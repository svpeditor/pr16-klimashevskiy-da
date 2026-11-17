package ru.edu.pr13;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BatchProcessor {
    public static final int MIN_POOL_SIZE = 1;
    public static final int MAX_POOL_SIZE = 8;

    private final DocumentProcessor processor;

    public BatchProcessor(DocumentProcessor p) {
        processor = p;
    }

    public BatchResult processAll(List<Document> docs, int poolSize) {
        if (poolSize < MIN_POOL_SIZE || poolSize > MAX_POOL_SIZE) {
            throw new IllegalArgumentException("Размер пула должен быть от " + MIN_POOL_SIZE + " до " + MAX_POOL_SIZE + ": " + poolSize);
        }
        checkIds(docs);
        BatchResult result = new BatchResult();
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            // один документ - одна задача Callable
            List<Future<ProcessingResult>> futures = new ArrayList<>();
            for (Document d : docs) {
                Callable<ProcessingResult> task = () -> processor.process(d);
                futures.add(executor.submit(task));
            }
            // ошибка одной задачи остается в ее Future и не отменяет остальные
            for (int i = 0; i < futures.size(); i++) {
                String id = docs.get(i).id();
                try {
                    futures.get(i).get();
                    result.registerSuccess();
                } catch (ExecutionException e) {
                    result.registerError(id, e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    result.registerError(id, "ожидание прервано");
                }
            }
        } finally {
            executor.shutdown();
        }
        return result;
    }

    // последовательная версия для сравнения времени
    public BatchResult processSequential(List<Document> docs) {
        checkIds(docs);
        BatchResult result = new BatchResult();
        for (Document d : docs) {
            try {
                processor.process(d);
                result.registerSuccess();
            } catch (RuntimeException e) {
                result.registerError(d.id(), e.getMessage());
            }
        }
        return result;
    }

    // ошибки хранятся по id, поэтому одинаковые id в пакете не допускаются
    private void checkIds(List<Document> docs) {
        Set<String> ids = new HashSet<>();
        for (Document d : docs) {
            if (!ids.add(d.id())) {
                throw new IllegalArgumentException("Повторный id документа " + d.id());
            }
        }
    }
}
