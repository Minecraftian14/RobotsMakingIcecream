package in.mcxiv.rmi;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;

import static in.mcxiv.rmi.RMI.TransmitExceptions.Transmission.GET_WHOLE;

class ExecutionEvent implements FrameworkMessage, AutoCloseable {

    int transactionId;
    int objectId;
    CachedMethod method;
    Object result;

    private static final Pool<ExecutionEvent> POOL = new Pool<ExecutionEvent>(false, false) {
        @Override
        protected ExecutionEvent create() {
            return new ExecutionEvent();
        }
    };

    private static ExecutionEvent obtain() {
        synchronized (POOL) {
            return POOL.obtain();
        }
    }

    private static void free(ExecutionEvent ie) {
        synchronized (POOL) {
            POOL.free(ie);
        }
    }

    public static ExecutionEvent obtainEE(int transactionId, int objectId, CachedMethod method, Object result) {
        ExecutionEvent ee = obtain();
        ee.transactionId = transactionId;
        ee.objectId = objectId;
        ee.method = method;
        ee.result = RemoteSpace.primitize(result, method.resClass);
        return ee;
    }

    public Object use() {
        Object object = result;
        close();
        if (objectId < 0)
            if (method.rmi.transmitExceptions().equals(GET_WHOLE))
                throw new RuntimeException((Throwable) object);
            else throw new RuntimeException((String) object);

        return object;
    }

    @Override
    public String toString() {
        return "ExecutionEvent{" +
               "transactionId=" + transactionId +
               ", objectId=" + objectId +
               ", method=" + method +
               ", result=" + result +
               '}';
    }

    @Override
    public void close() {
        free(this);
    }

    static final class Handler extends Serializer<ExecutionEvent> implements Listener {

        final RemoteSpace registry;

        Handler(RemoteSpace registry) {
            this.registry = registry;
        }

        @Override
        public void write(Kryo kryo, Output output, ExecutionEvent ee) {
            output.writeVarInt(ee.transactionId, true);
            output.writeVarInt(ee.objectId, false);
            output.writeVarInt(ee.method.id, true);
            if (ee.method.isResLocal) output.writeVarInt((Integer) ee.result, true);
            else if (ee.objectId < 0)
                if (ee.method.rmi.transmitExceptions().equals(GET_WHOLE))
                    kryo.writeObjectOrNull(output, ee.result, Throwable.class);
                else kryo.writeObjectOrNull(output, ee.result, String.class);
            else kryo.writeObjectOrNull(output, ee.result, ee.method.resClass);
            ee.close();
        }

        @Override
        public ExecutionEvent read(Kryo kryo, Input input, Class<? extends ExecutionEvent> aClass) {
            ExecutionEvent ee = obtain();
            ee.transactionId = input.readVarInt(true);
            Log.debug("Reading Execution Event: transactionId=" + ee.transactionId);
            ee.objectId = input.readVarInt(false);
            ee.method = registry.midToCMet.get(input.readVarInt(true));
            if (ee.method.isResLocal) input.readVarInt(true);
            else if (ee.objectId < 0)
                if (ee.method.rmi.transmitExceptions().equals(GET_WHOLE))
                    ee.result = kryo.readObjectOrNull(input, Throwable.class);
                else ee.result = kryo.readObjectOrNull(input, String.class);
            else ee.result = RemoteSpace.primitize(kryo.readObjectOrNull(input, ee.method.resClass), ee.method.resClass);
            return ee;
        }

        @Override
        public void received(Connection connection, Object event) {
            if (!(event instanceof ExecutionEvent)) return;
            Log.debug("Received ExecutionEvent: " + registry.logHelper.apply(event));
            ExecutionEvent ee = (ExecutionEvent) event;
            registry.executionLock.write(ee.transactionId, ee);
        }
    }
}
