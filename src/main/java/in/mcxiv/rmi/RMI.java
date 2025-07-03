package in.mcxiv.rmi;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RMI {

    boolean local() default false;

    boolean useUdp() default false;

    boolean nonBlocking() default false;

    boolean noReturns() default false;

    int responseTimeout() default 3000;

    TransmitExceptions.Transmission transmitExceptions() default TransmitExceptions.Transmission.TO_STRING;

    boolean delegatedToString() default false;

    boolean delegatedHashCode() default false;

    boolean closed() default false;

    interface RMISupplier {
        default RMI getRMI(RMI defaultRmi) {
            return defaultRmi;
        }
    }

    class RMIImpl implements RMI {

        final boolean isLocal;
        final boolean isUdp;
        final boolean isNonBlocking;
        final boolean returnsNotRequired;
        final int responseTimeout;
        final TransmitExceptions.Transmission transmitExceptions;
        final boolean delegatedToString;
        final boolean delegatedHashCode;
        final boolean closed;
        final Class<? extends Annotation> annotationType;

        public RMIImpl(boolean isLocal, boolean isUdp, boolean isNonBlocking, boolean returnsNotRequired, int responseTimeout, TransmitExceptions.Transmission transmitExceptions, boolean delegatedToString, boolean delegatedHashCode, boolean closed, Class<? extends Annotation> annotationType) {
            this.isLocal = isLocal;
            this.isUdp = isUdp;
            this.isNonBlocking = isNonBlocking;
            this.returnsNotRequired = returnsNotRequired;
            this.responseTimeout = responseTimeout;
            this.transmitExceptions = transmitExceptions;
            this.delegatedToString = delegatedToString;
            this.delegatedHashCode = delegatedHashCode;
            this.closed = closed;
            this.annotationType = annotationType;
        }

        public boolean local() {
            return isLocal;
        }

        public boolean useUdp() {
            return isUdp;
        }

        public boolean nonBlocking() {
            return isNonBlocking;
        }

        @Override
        public boolean noReturns() {
            return returnsNotRequired;
        }

        @Override
        public int responseTimeout() {
            return responseTimeout;
        }

        @Override
        public TransmitExceptions.Transmission transmitExceptions() {
            return transmitExceptions;
        }

        @Override
        public boolean delegatedToString() {
            return delegatedToString;
        }

        @Override
        public boolean delegatedHashCode() {
            return delegatedHashCode;
        }

        @Override
        public boolean closed() {
            return closed;
        }

        public Class<? extends Annotation> annotationType() {
            return annotationType;
        }

        @Override
        public String toString() {
            return "RMIImpl{" + "isLocal=" + isLocal + ", isUdp=" + isUdp + ", isNonBlocking=" + isNonBlocking + ", returnsNotRequired=" + returnsNotRequired + ", responseTimeout=" + responseTimeout + ", transmitExceptions=" + transmitExceptions + ", remoteToString=" + delegatedToString + ", remoteHashCode=" + delegatedHashCode + ", closed=" + closed + ", annotationType=" + annotationType + '}';
        }
    }

    class Helper {

        static RMI getRMI(Method method) {
            boolean isLocal = false;
            boolean isUdp = false;
            boolean isNonBlocking = false;
            boolean returnsNotRequired = false;
            int responseTimeout = 3000;
            TransmitExceptions.Transmission transmitExceptions = TransmitExceptions.Transmission.TO_STRING;
            boolean delegatedToString = false;
            boolean delegatedHashCode = false;
            boolean closed = false;
            Class<? extends Annotation> annotationType = RMI.class;

            if (method.isAnnotationPresent(RMI.class)) {
                RMI rmi = method.getAnnotation(RMI.class);
                isLocal = rmi.local();
                isUdp = rmi.useUdp();
                isNonBlocking = rmi.nonBlocking();
                returnsNotRequired = rmi.noReturns();
                responseTimeout = rmi.responseTimeout();
                annotationType = rmi.annotationType();
                transmitExceptions = rmi.transmitExceptions();
                delegatedToString = rmi.delegatedToString();
                delegatedHashCode = rmi.delegatedHashCode();
                closed = rmi.closed();
            }

            isLocal |= method.isAnnotationPresent(Local.class);
            isUdp |= method.isAnnotationPresent(UDP.class);
            isNonBlocking |= method.isAnnotationPresent(NonBlocking.class);
            returnsNotRequired |= method.isAnnotationPresent(NoReturns.class);
            returnsNotRequired |= void.class.equals(method.getReturnType());
            if (method.isAnnotationPresent(TransmitExceptions.class)) transmitExceptions = method.getAnnotation(TransmitExceptions.class).value();
            delegatedToString |= method.isAnnotationPresent(DelegatedToString.class);
            delegatedHashCode |= method.isAnnotationPresent(DelegatedHashCode.class);
            if (method.isAnnotationPresent(ResponseTimeout.class)) responseTimeout = method.getAnnotation(ResponseTimeout.class).value();

            return new RMIImpl(isLocal, isUdp, isNonBlocking, returnsNotRequired, responseTimeout, transmitExceptions, delegatedToString, delegatedHashCode, closed, annotationType);
        }

        static boolean isLocal(Parameter parameter) {
            return parameter.isAnnotationPresent(Local.class)
                   || isValidClosure(parameter);
        }

        public static boolean isNotLocal(Method method) {
            return !method.isAnnotationPresent(Local.class)
                   && (!method.isAnnotationPresent(RMI.class) || !method.getAnnotation(RMI.class).local());
        }

        static boolean isValidClosure(Parameter parameter) {
            return parameter.getType().isInterface()
                   && parameter.getType().isAnnotationPresent(FunctionalInterface.class)
                   && parameter.isAnnotationPresent(Closure.class);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    @interface Local {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface UDP {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface NonBlocking {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface NoReturns {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ResponseTimeout {
        int value() default 3000;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    @interface Closure {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface TransmitExceptions {
        enum Transmission {
            LOCAL_ONLY, TO_STRING, GET_MESSAGE, GET_WHOLE
        }

        Transmission value() default Transmission.TO_STRING;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface DelegatedToString {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface DelegatedHashCode {
    }

}
