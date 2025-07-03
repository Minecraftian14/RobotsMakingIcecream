package in.mcxiv.rmi;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.IntMap;
import com.esotericsoftware.kryo.util.ObjectMap;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.util.ObjectIntMap;
import com.esotericsoftware.minlog.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static in.mcxiv.rmi.ExecutionEvent.obtainEE;

public class RemoteSpace {

    protected int nextObjectId = 0;
    protected int nextMethodId = 0;
    protected int nextProxyId = 0;

    final ObjectIntMap<Class<?>> clsReg = new ObjectIntMap<>();

    final IntMap<CachedMethod> midToCMet = new IntMap<>();
    final ObjectIntMap<Method> metToMid = new ObjectIntMap<>();

    // Hosts
    final IntMap<Object> oidToObj = new IntMap<>();
    final ObjectIntMap<Object> objToOid = new ObjectIntMap<>();

    // Proxies
    final ObjectMap<Connection, IntMap<Object>> proxies = new ObjectMap<>();

    final ExecutorService executor;
    Function<Object, Object> logHelper = o -> o;

    final InvocationEvent.Handler invocationHandler = new InvocationEvent.Handler(this);
    final ExecutionEvent.Handler executionHandler = new ExecutionEvent.Handler(this);
    final IntMap<AsyncExecution> asyncExecutions = new IntMap<>();
    protected int lastTransactionId = -1;

    final AtomicInteger transactionIdSupplier = new AtomicInteger();
    final MultiResultLock<ExecutionEvent> executionLock = new MultiResultLock<>();

    final Listener activeServerListener = new Listener() {
        @Override
        public void connected(Connection connection) {
            connection.addListener(invocationHandler);
        }
    };

    public RemoteSpace() {
        this(Executors.newSingleThreadExecutor());
    }

    public RemoteSpace(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor);
    }

    // Class Initialization

    public RemoteSpace registerRemotable(Class<?> clazz) {
        if (clsReg.containsKey(clazz)) throw new IllegalArgumentException("Class " + clazz + " is already registered.");
        Log.debug("Registering remotable " + clazz);
        clsReg.put(clazz, 0);
        registerMethods(clazz);
        return this;
    }

    public boolean isNotRegistered(Class<?> aClass) {
        return !clsReg.containsKey(aClass);
    }

    void registerMethods(Class<?> clazz) {
        Log.debug("Registering methods for " + clazz);
        Arrays.stream(clazz.getMethods())
                .filter(RemoteSpace::isMethodRemotable)
                .filter(RMI.Helper::isNotLocal)
                .sorted(RemoteSpace::compareMethods)
                .map(this::registerMethod)
                .flatMap(CachedMethod::getLocalClasses)
                .filter(this::isNotRegistered)
                .distinct()
                .forEach(this::registerRemotable);
        for (Class<?> iSuper : clazz.getInterfaces()) registerMethods(iSuper);
    }

    CachedMethod registerMethod(Method method) {
        int methodId = nextMethodId++;
        Log.debug("Registering remotable method " + methodId + " to " + method);
        CachedMethod cMethod = new CachedMethod(methodId, method);
        midToCMet.put(methodId, cMethod);
        metToMid.put(method, methodId);
        return cMethod;
    }

    public static boolean isMethodRemotable(Method method) {
        int modifiers = method.getModifiers();
        // TODO: Do more research into what other methods can not be remoted.
        return Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers);
    }

    public static int compareMethods(Method m1, Method m2) {
        // Methods are sorted so they can be represented as an index.
        // MORE IMPORTANTLY, the order in which methods are received using getMethods is not guaranteed!
        int diff = m1.getName().compareTo(m2.getName());
        if (diff != 0) return diff;
        Class<?>[] p1s = m1.getParameterTypes();
        Class<?>[] p2s = m2.getParameterTypes();
        if (p1s.length > p2s.length) return 1;
        if (p1s.length < p2s.length) return -1;
        for (int i = 0; i < p1s.length; i++) {
            diff = p1s[i].getName().compareTo(p2s[i].getName());
            if (diff != 0) return diff;
        }
        throw new RuntimeException("Two methods with same signature!"); // Impossible.
    }

    public RemoteSpace registerEvents(Kryo kryo) {
        kryo.register(InvocationEvent.class, invocationHandler);
        kryo.register(ExecutionEvent.class, executionHandler);
        return this;
    }

    // Host Management

    int saveObject(int objectId, Object object) {
        // TODO: Make Connections a list instead, host once (not evey connection) and add that connection to the host
        //  Also, connections is a property of registry not the object
        if (oidToObj.containsKey(objectId))
            throw new IllegalArgumentException("Object id " + objectId + " already configured for object " + oidToObj.get(objectId) + ". Error saving object " + object);
        nextObjectId = objectId + 1;
        objToOid.put(object, objectId);
        oidToObj.put(objectId, object);
        return objectId;
    }

    public void hookConnection(Connection connection) {
        connection.addListener(invocationHandler);
    }

    public int hostObject(int objectId, Object object) {
        // Class explicitly not required, since we only invoke the methods
        return saveObject(objectId, object);
    }

    public int hostObject(Object object) {
        return saveObject(nextObjectId++, object);
    }

    public void hostObject(Connection connection, int objectId, Object object) {
        // Class explicitly not required, since we only invoke the methods
        saveObject(objectId, object);
        connection.addListener(invocationHandler);
    }

    public void hostObject(Connection connection, Object object) {
        hostObject(connection, nextObjectId, object);
    }

    public void hostObject(Server server, int objectId, Object object) {
        saveObject(objectId, object);
        server.addListener(activeServerListener);
    }

    public void hostObject(Server server, Object object) {
        hostObject(server, nextObjectId, object);
    }

    // Remote Management

    @SuppressWarnings("unchecked")
    public <T> T createRemote(
            final Connection connection,
            final int objectId, final Class<T> clazz,
            final RMI.RMISupplier delegate,
            final Class<?> delegationClass) {

        assert (delegate == null) == (delegationClass == null);
        assert (delegate == null) || (delegationClass.isInstance(delegate));

        // TODO: Review and remove this if it is not needed.
        //  I think, the only valid case is reconnection, but then connection is always new.
        //  So this is completely unnecessary :: except when createRemote can be called multiple times for the same connection.
        if (!proxies.containsKey(connection)) proxies.put(connection, new IntMap<>());
        IntMap<Object> cache = proxies.get(connection);
        if (cache.containsKey(objectId)) return (T) cache.get(objectId);

        Object remote = Proxy.newProxyInstance(clazz.getClassLoader(), delegate == null ? new Class[]{clazz} : new Class[]{delegationClass, clazz},
                (proxy, method, args) -> invokeMethod(connection, objectId, delegate, method, args));

        cache.put(objectId, remote);

        connection.addListener(executionHandler);
        return (T) remote;
    }

    public <T> T createRemote(Connection connection, int objectId, Class<T> clazz) {
        return createRemote(connection, objectId, clazz, null, null);
    }

    public <T> T createRemote(Connection connection, Class<T> clazz, RMI.RMISupplier delegate, Class<?> delegationClass) {
        return createRemote(connection, nextProxyId++, clazz, delegate, delegationClass);
    }

    public <T> T createRemote(Connection connection, Class<T> clazz) {
        return createRemote(connection, clazz, null, null);
    }

    // Invocation Management

    void send(CachedMethod method, Connection connection, Object object) {
        if (method.rmi.useUdp()) connection.sendUDP(object);
        else connection.sendTCP(object);
    }

    static Object primitize(Object object, Class<?> resClass) {
        if (object != null) return object;
        if (!resClass.isPrimitive()) return null;
        if (resClass == boolean.class) return Boolean.FALSE;
        if (resClass == byte.class) return (byte) 0;
        if (resClass == char.class) return (char) 0;
        if (resClass == short.class) return (short) 0;
        if (resClass == int.class) return 0;
        if (resClass == long.class) return 0L;
        if (resClass == float.class) return 0f;
        if (resClass == double.class) return 0d;
        return null;
    }

    boolean delegationRequired(RMI.RMISupplier remote, Method method) {
        if (remote == null) return false;
        if (method.getDeclaringClass().isAssignableFrom(remote.getClass())) return true;
        if (!method.getDeclaringClass().isAssignableFrom(Object.class)) return false;
        RMI rmi = remote.getRMI(null);
        if (rmi == null) return false;
        if (rmi.delegatedToString() && method.getName().equals("toString")) return true;
        if (rmi.delegatedHashCode() && method.getName().equals("hashCode")) return true;
        return false;
    }

    Object invokeUsingReflection(Object object, Method method, Object[] params) {
        try {
            return method.invoke(object, params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    Object invokeMethod(Connection connection, int objectId, RMI.RMISupplier delegate, Method method, Object[] params) {
        Log.debug("Remote Invocation: connection=" + connection + ", objectId=" + objectId + ", delegate=" + delegate + ", method=" + method + ", params=" + Arrays.toString(params));
        if (delegationRequired(delegate, method))
            return invokeUsingReflection(delegate, method, params);
        int methodId = metToMid.get(method, -1);
        CachedMethod cMethod = midToCMet.get(methodId);
        RMI rmi = delegate != null ? delegate.getRMI(cMethod.rmi) : cMethod.rmi;
        if (rmi.closed()) return primitize(null, cMethod.resClass);
        int transactionId = lastTransactionId = transactionIdSupplier.getAndIncrement();

        Log.debug("Remote Invocation: transactionId=" + transactionId + ", objectId=" + objectId + ", cMethod=" + cMethod);
        send(cMethod, connection, hostParams(connection, InvocationEvent.obtainIE(transactionId, objectId, cMethod, params)));
        if (rmi.noReturns()) return primitize(null, cMethod.resClass);
        if (rmi.nonBlocking()) {
            asyncExecutions.put(transactionId, AsyncExecution.obtainAE(connection, rmi.responseTimeout()));
            return primitize(null, cMethod.resClass);
        }
        Log.debug("Waiting for ExecutionEvent: transactionId=" + transactionId + ", thread=" + Thread.currentThread().getName());
        // Wait for the ExecutionEvent. which contains the result.
        // If the return type is supposed to be a remote object as well, the result must be an object id.
        // Replace the id with the actual remote object.
        // Finally, retrieve the result and free the ExecutionEvent.
        return createRemoteResult(connection, executionLock.read(transactionId, rmi.responseTimeout())).use();
    }

    InvocationEvent hostParams(Connection connection, InvocationEvent ie) {
        for (int index : ie.method.localParamIndices) {
            Object[] params = ie.params;
            Object param = params[index];
            params[index] = -1;
            if (param == null) continue;
            if (!objToOid.containsKey(param)) hostObject(connection, param);
            params[index] = objToOid.get(param, -1);
        }
        return ie;
    }

    ExecutionEvent createRemoteResult(Connection connection, ExecutionEvent ee) {
        if (!ee.method.isResLocal || ee.objectId < 0) return ee;
        int objectId = (int) ee.result;
        ee.result = null;
        if (objectId == -1) return ee;
        ee.result = createRemote(connection, objectId, ee.method.resClass);
        return ee;
    }

    void invokeMethod(Connection connection, InvocationEvent ie) {
        Log.debug("Local Invocation: connection=" + connection + ", objectId=" + ie.objectId + ", method=" + ie.method);
        Object object = oidToObj.get(ie.objectId);

        executor.submit(() -> {
            Object result = ie.method.invokeMethod(object, createRemoteParams(connection, ie));
            if (ie.method.rmi.noReturns()) return;
            send(ie.method, connection, hostResult(connection, obtainEE(ie.transactionId, ie.objectId, ie.method, result)));
            ie.close();
        });
    }

    InvocationEvent createRemoteParams(Connection connection, InvocationEvent ie) {
        for (int index : ie.method.localParamIndices) {
            Object[] params = ie.params;
            int objectId = (int) params[index];
            params[index] = null;
            if (objectId == -1) continue;
            params[index] = createRemote(connection, objectId, ie.method.argClasses[index]);
        }
        return ie;
    }

    ExecutionEvent hostResult(Connection connection, ExecutionEvent ee) {
        if (!ee.method.isResLocal || ee.objectId < 0) return ee;
        Object param = ee.result;
        ee.result = -1;
        if (param == null) return ee;
        if (!objToOid.containsKey(param)) hostObject(connection, param);
        ee.result = objToOid.get(param, -1);
        return ee;
    }

    // Result Management

    public boolean hasAnyTransaction() {
        return hasTransaction(lastTransactionId);
    }

    public boolean hasTransaction(int transactionId) {
        return executionLock.containsKey(transactionId);
    }

    public boolean hasLastResult() {
        return hasResult(lastTransactionId);
    }

    public boolean hasResult(int transactionId) {
        return executionLock.containsValue(transactionId);
    }

    public int getLastTransactionId() {
        return lastTransactionId;
    }

    public Object getLastResult() {
        return getLastResult(-1);
    }

    public Object getLastResult(long responseTimeout) {
        return getResult(lastTransactionId, responseTimeout);
    }

    public Object getResult(int transactionId) {
        return getResult(transactionId, -1);
    }

    public Object getResult(int transactionId, long responseTimeout) {
        AsyncExecution ae = asyncExecutions.get(transactionId);
        Connection connection = ae.connection;
        responseTimeout = Math.max(responseTimeout, ae.responseTimeout);
        ae.close();
        return createRemoteResult(connection, executionLock.read(transactionId, responseTimeout)).use();
    }

    // Utility Methods

    public void shutDownExecutor() {
        if (executor.isShutdown()) return;
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS))
                Log.info(String.format("Executor did not terminate in the specified time, %d tasks did not finish.", executor.shutdownNow().size()));
        } catch (InterruptedException e) {
            Log.error(String.format("Interrupted while waiting for executor to shut down, %d tasks did not finish.", executor.shutdownNow().size()), e);
            executor.shutdownNow();
        }
    }

    public Function<Object, Object> getLogHelper() {
        return logHelper;
    }

    public void setLogHelper(Function<Object, Object> logHelper) {
        this.logHelper = logHelper;
    }
}
