package in.mcxiv.rmi;

import com.esotericsoftware.kryo.util.ObjectMap;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MultiResultLock<Type> {

    private final ObjectMap<Integer, CompletableFuture<Type>> transactions = new ObjectMap<>();

    public boolean containsKey(int lastTransactionId) {
        synchronized (transactions) {
            return transactions.containsKey(lastTransactionId);
        }
    }

    public boolean containsValue(int lastTransactionId) {
        synchronized (transactions) {
            return transactions.containsKey(lastTransactionId)
                   && transactions.get(lastTransactionId).isDone();
        }
    }

    public void write(int transactionId, Type object) {
        synchronized (transactions) {
            if (transactions.containsKey(transactionId))
                transactions.get(transactionId).complete(object);
            else transactions.put(transactionId, CompletableFuture.completedFuture(object));
        }
    }

    public Type read(int transactionId, long millis) {
        CompletableFuture<Type> future;
        synchronized (transactions) {
            if (transactions.containsKey(transactionId))
                future = transactions.get(transactionId);
            else transactions.put(transactionId, future = new CompletableFuture<>());
        }
        try {
            return future.get(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            synchronized (transactions) {
                transactions.remove(transactionId);
            }
            throw new RuntimeException("Transaction ID " + transactionId, e);
        }
    }

}
