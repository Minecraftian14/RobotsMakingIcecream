package in.mcxiv.rmi;

import com.esotericsoftware.kryo.util.Pool;
import com.esotericsoftware.kryonet.Connection;

public class AsyncExecution implements AutoCloseable {
    Connection connection;
    long responseTimeout;

    public static AsyncExecution obtainAE(Connection connection, long responseTimeout) {
        AsyncExecution ae = POOL.obtain();
        ae.connection = connection;
        ae.responseTimeout = responseTimeout;
        return ae;
    }

    private static final Pool<AsyncExecution> POOL = new Pool<AsyncExecution>(false, false) {
        @Override
        protected AsyncExecution create() {
            return new AsyncExecution();
        }
    };

    @Override
    public void close() {
        POOL.free(this);
    }
}
