package in.mcxiv.rmi;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.*;
import com.esotericsoftware.kryonet.Listener.TypeListener;
import com.esotericsoftware.kryonet.rmi.RemoteObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static in.mcxiv.rmi.RMI.TransmitExceptions.Transmission.GET_MESSAGE;
import static org.junit.jupiter.api.Assertions.*;

class RemoteSpaceTest {

    static class TestHelper {
        Server server;

        public TestHelper(Server server) {
            this.server = server;
        }

        void send(String message) {
            server.sendToAllTCP(message);
        }
    }

    public static class Holder {
        public long value;

        public Holder() {
        }

        public Holder(long value) {
            this.value = value;
        }
    }

    interface SimpleFunctions {
        void runnable();

        void consumer(int object);

        double supplier();

        String function(Holder object);
    }

    static class SimpleFunctionsImpl extends TestHelper implements SimpleFunctions {

        public SimpleFunctionsImpl(Server server) {
            super(server);
        }

        @Override
        public void runnable() {
            send("runnable");
        }

        @Override
        public void consumer(int object) {
            send("consumer" + object);
        }

        @Override
        public double supplier() {
            send("supplier");
            return Math.PI;
        }

        @Override
        public String function(Holder object) {
            send("function" + object.value);
            return "Server";
        }
    }

    interface ClosureTest {
        void forwardConsumer(@RMI.Closure IntConsumer consumer);

        void forwardSupplier(@RMI.Closure IntSupplier supplier);

        @RMI.Closure
        IntConsumer backwardConsumer();

        @RMI.Closure
        IntSupplier backwardSupplier();
    }

    static class ClosureTestImpl extends TestHelper implements ClosureTest {

        public ClosureTestImpl(Server server) {
            super(server);
        }

        @Override
        public void forwardConsumer(IntConsumer consumer) {
            send("forwardConsumer");
            consumer.accept(1114_1);
        }

        @Override
        public void forwardSupplier(IntSupplier supplier) {
            int asInt = supplier.getAsInt();
            send("forwardSupplier" + asInt);
            assertEquals(1114_2, asInt);
        }

        @Override
        public IntConsumer backwardConsumer() {
            return integer -> {
                send("backwardConsumer" + integer);
                assertEquals(1114_3, integer);
            };
        }

        @Override
        public IntSupplier backwardSupplier() {
            send("backwardSupplier");
            return () -> 1114_4;
        }
    }

    interface A {
        void a();
    }

    interface B {
        void b();
    }

    interface C {
        void c();
    }

    interface D {
        void d();
    }

    static class AImpl extends TestHelper implements A {
        public AImpl(Server server) {
            super(server);
        }

        @Override
        public void a() {
            send("a");
        }
    }

    static class BImpl extends TestHelper implements B {
        public BImpl(Server server) {
            super(server);
        }

        @Override
        public void b() {
            send("b");
        }
    }

    static class CImpl extends TestHelper implements C {
        public CImpl(Server server) {
            super(server);
        }

        @Override
        public void c() {
            send("c");
        }
    }

    static class DImpl implements D {
        Connection connection;

        public DImpl(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void d() {
            connection.sendTCP("d");
        }
    }

    interface Annotations {

        default int process1(int a) {
            return a | 0b1;
        }

        @RMI.Local
        default int process2(int a) {
            return a | 0b100;
        }

        @RMI.UDP
        default int process4(int a) {
            return process2(a);
        }

        @RMI.NonBlocking
        default int process8(int a) {
            return a | 0b1000;
        }

        @RMI.NoReturns
        default int process16(int a) {
            return a | 0b10000;
        }

        @RMI.ResponseTimeout(10_000)
        default int process32(int a) {
            return a | 0b100000;
        }

        @RMI.ResponseTimeout(100_000)
        @RMI.TransmitExceptions(GET_MESSAGE)
        default int process64(int a) {
            throw new RuntimeException("" + (a | 0b1000000));
//            return a | 0b1000000;
        }

        default int process128(int a) {
            return a | 0b10000000;
        }

        default int process256(int a) {
            return a | 0b100000000;
        }

        @RMI(local = true)
        default int process512(int a) {
            return a | 0b10000000000;
        }

        @RMI(useUdp = true, nonBlocking = true)
        default int process1024(int a) {
            return process512(a);
        }

        @RMI(noReturns = true)
        default int process2048(int a) {
            return a | 0b100000000000;
        }

        @RMI(responseTimeout = 10000)
        default int process4096(int a) {
            return a | 0b1000000000000;
        }

        @RMI(delegatedToString = true, delegatedHashCode = true)
        default int process8192(int a) {
            return a | 0b10000000000000;
        }

        @RMI(closed = true)
        default int process16384(int a) {
            return a | 0b100000000000000;
        }

    }

    class AnnotationsImpl implements Annotations {
        @Override
        public int process32(int a) {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Annotations.super.process32(a);
        }

        @Override
        public int process4096(int a) {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Annotations.super.process4096(a);
        }
    }

    @RMI.DelegatedToString
    @RMI.DelegatedHashCode
    static class AImpl2 extends TestHelper implements A {
        public AImpl2(Server server) {
            super(server);
        }

        @Override
        public void a() {
            send("a2");
        }
    }

    interface Delegate {
        void delegatedA();
    }

    static class ADelegate implements RMI.RMISupplier, Delegate {
        @Override
        public void delegatedA() {
        }
    }

    interface GenericsInClosures {
        @RMI.ResponseTimeout(60_000)
        void getSomeData(@RMI.Closure Function<Integer, String> callback);
    }

    static class GenericsInClosuresImpl extends TestHelper implements GenericsInClosures {
        public GenericsInClosuresImpl(Server server) {
            super(server);
        }

        @Override
        public void getSomeData(Function<Integer, String> callback) {
            send(callback.apply(1045) + " Data");
        }
    }

    public void register(Kryo kryo, RemoteSpace registry) {
        kryo.register(Object.class);
        kryo.register(Throwable.class);
        kryo.register(UnsupportedOperationException.class);
        kryo.register(StackTraceElement[].class);
        kryo.register(StackTraceElement.class);
        kryo.register(Collections.EMPTY_LIST.getClass());
        kryo.setReferences(true);

        registry.registerEvents(kryo);
        registry.registerRemotable(Object.class);
        registry.registerRemotable(SimpleFunctions.class);
        registry.registerRemotable(ClosureTest.class);
        registry.registerRemotable(A.class);
        registry.registerRemotable(B.class);
        registry.registerRemotable(C.class);
        registry.registerRemotable(D.class);
        registry.registerRemotable(Annotations.class);
        registry.registerRemotable(GenericsInClosures.class);

        kryo.register(Holder.class);
    }

    String host = "localhost";
    int tcpPort = 54555, udpPort = 54777;
    ArrayList<EndPoint> endPoints = new ArrayList<>();
    ArrayList<Thread> threads = new ArrayList<>();
    Timer timer = new Timer();
    boolean fail;

    public void startEndPoint(EndPoint endPoint) {
        endPoints.add(endPoint);
        Thread thread = new Thread(endPoint, endPoint.getClass().getSimpleName());
        threads.add(thread);
        thread.start();
    }

    public void stopEndPoints() {
        stopEndPoints(0);
    }

    public void stopEndPoints(int stopAfterMillis) {
        timer.schedule(new TimerTask() {
            public void run() {
                for (EndPoint endPoint : endPoints)
                    endPoint.stop();
                endPoints.clear();
            }
        }, stopAfterMillis);
    }

    public void waitForThreads() {
        fail = false;
        TimerTask failTask = new TimerTask() {
            public void run() {
                stopEndPoints();
                fail = true;
            }
        };
        timer.schedule(failTask, 13000);
        while (true) {
            threads.removeIf(thread -> !thread.isAlive());
            if (threads.isEmpty())
                break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        failTask.cancel();
        if (fail)
            fail("Test did not complete in a timely manner.");
        // Give sockets a chance to close before starting the next test.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
    }

    @Test
    void testSimpleFunctions() throws IOException {
        List<String> receivedEvents = new ArrayList<>(4);

        Server server = new Server();
        RemoteSpace serverRegistry = new RemoteSpace();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, new SimpleFunctionsImpl(server));

        Client client = new Client();
        RemoteSpace clientRegistry = new RemoteSpace();
        register(client.getKryo(), clientRegistry);
        TypeListener listener = new TypeListener();
        listener.addTypeHandler(String.class, (connection, string) -> receivedEvents.add(string));
        client.addListener(listener);
        SimpleFunctions functions = clientRegistry.createRemote(client, SimpleFunctions.class);
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        functions.runnable();
        functions.consumer(1234554321);
        assertEquals(Math.PI, functions.supplier());
        assertEquals("Server", functions.function(new Holder(1234567890987654321L)));

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();

        assertEquals("runnable", receivedEvents.get(0));
        assertEquals("consumer1234554321", receivedEvents.get(1));
        assertEquals("supplier", receivedEvents.get(2));
        assertEquals("function1234567890987654321", receivedEvents.get(3));
    }

    @Test
    void testClosures() throws IOException {
        List<String> receivedEvents = new ArrayList<>(4);
        List<String> receivedClosures = new ArrayList<>(4);

        Server server = new Server();
        RemoteSpace serverRegistry = new RemoteSpace();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, new ClosureTestImpl(server));

        Client client = new Client();
        RemoteSpace clientRegistry = new RemoteSpace();
        register(client.getKryo(), clientRegistry);
        TypeListener listener = new TypeListener();
        listener.addTypeHandler(String.class, (connection, string) -> receivedEvents.add(string));
        client.addListener(listener);
        ClosureTest functions = clientRegistry.createRemote(client, ClosureTest.class);
        clientRegistry.nextProxyId = 10;
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        functions.forwardConsumer(integer -> {
            receivedClosures.add("forwardConsumer" + integer);
            assertEquals(1114_1, integer);
        });
        functions.forwardSupplier(() -> {
            receivedClosures.add("forwardSupplier");
            return 1114_2;
        });
        functions.backwardConsumer()
                .andThen(i -> receivedClosures.add("backwardConsumer"))
                .accept(1114_3);
//        receivedClosures.add("backwardConsumer");
        int asInt = functions.backwardSupplier().getAsInt();
        assertEquals(1114_4, asInt);
        receivedClosures.add("backwardSupplier" + asInt);

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();

        assertEquals("forwardConsumer", receivedEvents.get(0));
        assertEquals("forwardConsumer11141", receivedClosures.get(0));
        assertEquals("forwardSupplier11142", receivedEvents.get(1));
        assertEquals("forwardSupplier", receivedClosures.get(1));
        assertEquals("backwardConsumer11143", receivedEvents.get(2));
        assertEquals("backwardConsumer", receivedClosures.get(2));
        assertEquals("backwardSupplier", receivedEvents.get(3));
        assertEquals("backwardSupplier11144", receivedClosures.get(3));
    }

    @Test
    void testJumble() throws IOException, InterruptedException {
        List<String> receivedEvents = new ArrayList<>(4);

        Server server = new Server();
        RemoteSpace serverRegistry = new RemoteSpace();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, 3, new CImpl(server));
        serverRegistry.hostObject(server, 2, new BImpl(server));
        serverRegistry.hostObject(server, 1, new AImpl(server));
        D[] d = new D[1];
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                d[0] = serverRegistry.createRemote(connection, 4, D.class);
                TypeListener listener = new TypeListener();
                listener.addTypeHandler(String.class, (__, string) -> receivedEvents.add(string));
                connection.addListener(listener);
                ;
            }
        });
        Client client = new Client();
        RemoteSpace clientRegistry = new RemoteSpace();
        register(client.getKryo(), clientRegistry);
        TypeListener listener = new TypeListener();
        listener.addTypeHandler(String.class, (connection, string) -> receivedEvents.add(string));
        client.addListener(listener);
        ;
        C c = clientRegistry.createRemote(client, 3, C.class);
        clientRegistry.hostObject(client, 4, new DImpl(client));
        A a = clientRegistry.createRemote(client, 1, A.class);
        B b = clientRegistry.createRemote(client, 2, B.class);
        clientRegistry.nextProxyId = 10;
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        Thread.sleep(1000);

        a.a();
        b.b();
        c.c();
        d[0].d();

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();

        assertTrue(receivedEvents.remove("d"));
        assertEquals("a", receivedEvents.get(0));
        assertEquals("b", receivedEvents.get(1));
        assertEquals("c", receivedEvents.get(2));
    }

    @Test
    void testAnnotations() throws IOException {
        Server server = new Server();
        RemoteSpace serverRegistry = new RemoteSpace();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, new AnnotationsImpl());

        Client client = new Client();
        RemoteSpace clientRegistry = new RemoteSpace();
        register(client.getKryo(), clientRegistry);
        Annotations a = clientRegistry.createRemote(client, Annotations.class);
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        int x = 0;
        x = a.process1(x);
//      x = a.process2(x); // Local only
        x = a.process4(x);
        a.process8(x); // Non Blocking
        x = (int) clientRegistry.getLastResult();
        a.process16(x); // No Returns
        x = a.process32(x);
        try {
            x = a.process64(x);
        } catch (Exception e) {
            x = Integer.parseInt(e.getMessage());
        }
        x = a.process128(x);
        x = a.process256(x);
//      x = a.process512(x); // Local only
        a.process1024(x); // Non Blocking
        x = (int) clientRegistry.getLastResult();
        a.process2048(x); // No Returns
        x = a.process4096(x);
        x = a.process8192(x);
        a.process16384(x); // Closed
        assertEquals(0b011010111101101, x);

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();
    }

    @Test
    void testTypeAnnotations() throws IOException {
        Server server = new Server();
        RemoteSpace serverRegistry = new RemoteSpace();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);

        AImpl2 host = new AImpl2(server);
        serverRegistry.hostObject(server, 1, host);

        Client client = new Client();
        RemoteSpace clientRegistry = new RemoteSpace();
        register(client.getKryo(), clientRegistry);
        ADelegate delegate = new ADelegate();
        Annotations delegatedRemote = clientRegistry.createRemote(client, Annotations.class, delegate, Delegate.class);
        Annotations plainRemote = clientRegistry.createRemote(client, Annotations.class);
        startEndPoint(client);
        client.connect(5000, this.host, tcpPort, udpPort);

        assertEquals(delegate.toString(), delegatedRemote.toString());
        assertEquals(delegate.hashCode(), delegatedRemote.hashCode());
        assertNotEquals(delegate.toString(), plainRemote.toString());
        assertNotEquals(delegate.hashCode(), plainRemote.hashCode());

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();
    }

    @Test
    void testDelegate() throws IOException {
        Server server = new Server();
        RemoteSpace serverRegistry = new RemoteSpace();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, new SimpleFunctionsImpl(server));

        Client client = new Client();
        RemoteSpace clientRegistry = new RemoteSpace();
        register(client.getKryo(), clientRegistry);
        SimpleFunctions s = clientRegistry.createRemote(client, SimpleFunctions.class, new DelegateObject(clientRegistry, client), RemoteObject.class);
        RemoteObject r = (RemoteObject) s;
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        assertEquals(Math.PI, s.supplier());
        r.setNonBlocking(true);
        assertEquals(0.0, s.supplier());
        assertEquals(Math.PI, r.waitForLastResponse());

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();
    }

    @Test
    @Disabled("No bright ideas, regarding how to implement this.")
    void testGenericsInCallbacks() throws IOException {
        // TODO:
        //  Auto register closure remotables during the normal registration
        //  If, for generic entities children specify a solid type, like
        //      class MyClass extends Supplier<Integer>
        //  Then use reflection to retrieve the actual types of returns and parameters instead of generics (which gives plain Object).
        //  In case of
        //      class MyClass<T> extends Supplier<T>
        //  Try to find a use case, else throw an exception: Invalid entity, Classes w/o generics can not be hosted due to the lack of type information necessary for serialization.
        //  Finally, do the same thing for parameter types.
        //  .
        //  Basically, you need to refactor the caching mechanism to instead take two main entities, the class and the generic containing type
        //  For normal entities provided for registration, we can derive the generic type
        //  For parameters, we supply the types
        List<String> receivedEvents = new ArrayList<>(4);

        Server server = new Server();
        RemoteSpace serverRegistry = new RemoteSpace();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, 3, new GenericsInClosuresImpl(server));

        Client client = new Client();
        RemoteSpace clientRegistry = new RemoteSpace();
        register(client.getKryo(), clientRegistry);
        TypeListener listener = new TypeListener();
        listener.addTypeHandler(String.class, (connection, string) -> receivedEvents.add(string));
        client.addListener(listener);
        ;
        GenericsInClosures gic = clientRegistry.createRemote(client, 3, GenericsInClosures.class);
        clientRegistry.nextProxyId = 10;
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        gic.getSomeData(i -> "Hoisted " + i);

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();

        assertEquals("Hoisted 1045 Data", receivedEvents.get(0));
    }

    interface CCallable extends Callable<Object> {
    }

    @Test
    void testDifferenceInHandles() throws Exception {
        Set<Integer> receivedEvents = Collections.synchronizedSet(new HashSet<>(3));
        ExecutorService executor = Executors.newCachedThreadPool();
        List<CCallable> callables = new ArrayList<>();

        Server server = new Server();
        RemoteSpace serverRegistry = new RemoteSpace();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, new SimpleFunctionsImpl(server));

        Callable<RemoteObject> clientSActivity = () -> {
            Client client = new Client();
            client.addListener(new Listener() {
                @Override
                public void connected(Connection connection) {
                    receivedEvents.add(connection.getID());
                }
            });
            RemoteSpace clientRegistry = new RemoteSpace();
            register(client.getKryo(), clientRegistry);
            SimpleFunctions s = clientRegistry.createRemote(client, SimpleFunctions.class, new DelegateObject(clientRegistry, client), RemoteObject.class);
            RemoteObject r = (RemoteObject) s;
            startEndPoint(client);
            client.connect(5000, host, tcpPort, udpPort);

            callables.add(() -> {
                assertEquals(Math.PI, s.supplier());
                r.setNonBlocking(true);
                assertEquals(0.0, s.supplier());
                assertEquals(Math.PI, r.waitForLastResponse());
                return null;
            });

            return r;
        };

        RemoteObject r1 = clientSActivity.call();
        executor.invokeAll(callables, 3L, TimeUnit.SECONDS);
        callables.clear();
        r1.close();

        RemoteObject r2 = clientSActivity.call();
        RemoteObject r3 = clientSActivity.call();
        executor.invokeAll(callables, 3L, TimeUnit.SECONDS);
        callables.clear();
        r2.close();
        r3.close();

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();

        assertTrue(receivedEvents.contains(1), "contains(1)");
        assertTrue(receivedEvents.contains(2), "contains(2)");
        assertTrue(receivedEvents.contains(3), "contains(3)");
    }
}