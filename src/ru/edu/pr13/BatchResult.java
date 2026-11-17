package ru.edu.pr13;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

// агрегаты можно обновлять из разных потоков: AtomicInteger вместо count++
public class BatchResult {
    private final AtomicInteger success = new AtomicInteger();
    private final ConcurrentMap<String, String> errors = new ConcurrentHashMap<>();

    public void registerSuccess() {
        success.incrementAndGet();
    }

    public void registerError(String id, String msg) {
        errors.put(id, msg);
    }

    public int successCount() {
        return success.get();
    }

    public int errorCount() {
        return errors.size();
    }

    public ConcurrentMap<String, String> errors() {
        return errors;
    }
}
