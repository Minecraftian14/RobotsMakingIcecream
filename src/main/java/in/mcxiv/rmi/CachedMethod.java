package in.mcxiv.rmi;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.stream.Stream;

class CachedMethod {
    final int id;
    private final Method reflection;
    final RMI rmi;
    /**
     * Actual classes of the arguments (Generic arguments are Object)
     */
    final Class<?>[] argClasses;
    /**
     * In case of local objects, we pass the object id, for that we store int class here instead.
     */
    final Class<?>[] serClasses;
    final Class<?>[] locClasses;
    final Class<?> resClass;
    final boolean isResLocal;

    final int[] localParamIndices;

    public CachedMethod() {
        // For serialized toString methods
        id = 0;
        reflection = null;
        rmi = null;
        argClasses = null;
        serClasses = null;
        locClasses = null;
        resClass = null;
        isResLocal = false;
        localParamIndices = null;
    }

    public CachedMethod(int id, Method method) {
        this.id = id;
        this.reflection = method;

        boolean isFunctionalInterface = method.getDeclaringClass().isAnnotationPresent(FunctionalInterface.class);

        this.rmi = RMI.Helper.getRMI(method);
        this.argClasses = method.getParameterTypes();
        this.serClasses = argClasses.clone();
        this.resClass = method.getReturnType();
        this.isResLocal = method.isAnnotationPresent(RMI.Closure.class)
                          || (isFunctionalInterface && resClass.isAnnotationPresent(FunctionalInterface.class));

        Parameter[] parameters = method.getParameters();

        if (isFunctionalInterface) {
            // TODO: Why did I add this? Is it even required?
            //  Future self: Okay, so I added this so that param classes can be like Function<Supplier<Int>>
            //  But that is far from complete due to lack of type info

            int localParamCount = 0;
            for (Parameter parameter : parameters)
                if (RMI.Helper.isLocal(parameter) || parameter.getType().isAnnotationPresent(FunctionalInterface.class))
                    localParamCount++;
            this.localParamIndices = new int[localParamCount];
            this.locClasses = new Class[localParamCount + (isResLocal ? 1 : 0)];
            localParamCount = 0;
            for (int i = 0; i < parameters.length; i++)
                if (RMI.Helper.isLocal(parameters[i]) || parameters[i].getType().isAnnotationPresent(FunctionalInterface.class)) {
                    localParamIndices[localParamCount] = i;
                    locClasses[localParamCount++] = parameters[i].getType();
                }

        } else {

            int localParamCount = 0;
            for (Parameter parameter : parameters)
                if (RMI.Helper.isLocal(parameter))
                    localParamCount++;
            this.localParamIndices = new int[localParamCount];
            this.locClasses = new Class[localParamCount + (isResLocal ? 1 : 0)];
            localParamCount = 0;
            for (int i = 0; i < parameters.length; i++)
                if (RMI.Helper.isLocal(parameters[i])) {
                    localParamIndices[localParamCount] = i;
                    locClasses[localParamCount++] = parameters[i].getType();
                }

        }

        if (isResLocal) this.locClasses[locClasses.length - 1] = resClass;

        // BOGUS TRICK...
        for (int paramIndex : localParamIndices)
            serClasses[paramIndex] = int.class;
    }

    @Override
    public String toString() {
        return "CachedMethod{" +
               "id=" + id +
               ", reflection=" + reflection +
               ", rmi=" + rmi +
               ", localParamIndices=" + Arrays.toString(localParamIndices) +
               '}';
    }

    public Object invokeMethod(Object object, InvocationEvent ie) {
        try {
            return reflection.invoke(object, ie.params);
        } catch (InvocationTargetException e) {
            if (rmi.transmitExceptions().equals(RMI.TransmitExceptions.Transmission.LOCAL_ONLY))
                throw new RuntimeException(e.getCause());
            else if (rmi.transmitExceptions().equals(RMI.TransmitExceptions.Transmission.TO_STRING)) {
                ie.objectId = ~ie.objectId; // ie is instantly closed where this function is called, and the negative value is consumed by ee
                e.printStackTrace();
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.getCause().printStackTrace(pw);
                return sw.toString();
            } else if (rmi.transmitExceptions().equals(RMI.TransmitExceptions.Transmission.GET_MESSAGE)) {
                ie.objectId = ~ie.objectId;
                e.printStackTrace();
                return e.getCause().getMessage();
            } else {
                throw new UnsupportedOperationException("TODO: How to serialize Throwable");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    Stream<Class<?>> getLocalClasses() {
        return Arrays.stream(locClasses);
    }
}
