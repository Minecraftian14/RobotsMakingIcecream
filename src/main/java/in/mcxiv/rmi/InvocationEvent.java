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

import java.util.Arrays;

class InvocationEvent implements FrameworkMessage, AutoCloseable {

    int transactionId;
    int objectId;
    CachedMethod method;
    Object[] params;

    private static final Pool<InvocationEvent> POOL = new Pool<InvocationEvent>(false, false) {
        @Override
        protected InvocationEvent create() {
            return new InvocationEvent();
        }
    };

    private static InvocationEvent obtain() {
        synchronized (POOL) {
            return POOL.obtain();
        }
    }

    private static void free(InvocationEvent ie) {
        synchronized (POOL) {
            POOL.free(ie);
        }
    }

    static InvocationEvent obtainIE(int transactionId, int objectId, CachedMethod method, Object[] args) {
        InvocationEvent ie = obtain();
        ie.transactionId = transactionId;
        ie.objectId = objectId;
        ie.method = method;
        ie.params = args != null ? args : new Object[0];
        return ie;
    }

    @Override
    public String toString() {
        return "InvocationEvent{" +
               "transactionId=" + transactionId +
               ", objectId=" + objectId +
               ", method=" + method +
               ", params=" + Arrays.toString(params) +
               '}';
    }

    @Override
    public void close() {
        free(this);
    }

    static class Handler extends Serializer<InvocationEvent> implements Listener {

        final RemoteSpace registry;

        Handler(RemoteSpace registry) {
            this.registry = registry;
        }

        @Override
        public void write(Kryo kryo, Output output, InvocationEvent ie) {
            output.writeVarInt(ie.transactionId, true);
            output.writeVarInt(ie.objectId, true);
            output.writeVarInt(ie.method.id, true);
            output.writeVarInt(ie.params.length, true);
            for (int i = 0; i < ie.params.length; i++)
                kryo.writeObjectOrNull(output, ie.params[i], ie.method.serClasses[i]);
            ie.close();
        }

        @Override
        public InvocationEvent read(Kryo kryo, Input input, Class<? extends InvocationEvent> aClass) {
            InvocationEvent ie = obtain();
            ie.transactionId = input.readVarInt(true);
            Log.debug("Reading Invocation Event: transactionId=" + ie.transactionId);
            ie.objectId = input.readVarInt(true);
            ie.method = registry.midToCMet.get(input.readVarInt(true));
            ie.params = new Object[input.readVarInt(true)];
            Log.debug("params.length=" + ie.params.length);
            Log.debug("ir.method.serClasses.length=" + ie.method.argClasses.length);
            Log.debug("ir.method.serClasses.length=" + ie.method.serClasses.length);
            for (int i = 0; i < ie.params.length; i++)
                ie.params[i] = kryo.readObjectOrNull(input, ie.method.serClasses[i]);
            return ie;
        }

        @Override
        public void received(Connection connection, Object event) {
            if (!(event instanceof InvocationEvent)) return;
            Log.debug("Received InvocationEvent: " + registry.logHelper.apply(event));
            InvocationEvent ie = (InvocationEvent) event;
            registry.invokeMethod(connection, ie);
        }
    }
}
