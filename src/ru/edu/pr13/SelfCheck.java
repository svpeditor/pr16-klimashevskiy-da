package ru.edu.pr13;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SelfCheck {
    private static final BatchProcessor BATCH = new BatchProcessor(new DocumentProcessor());
    private static int passed = 0;
    private static int count = 0;

    public static void main(String[] args) throws InterruptedException {
        checkPoolSize();
        checkErrors();
        checkThreadSafety();
        checkShutdown();
        checkVariant();
        System.out.println("Unit: " + passed + "/" + count + " passed");
        compareTime();
        // свой файл и размер пула: java -cp out ru.edu.pr13.SelfCheck data/my.csv 4
        int pool = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        runFile(args.length > 0 ? args[0] : "data/sample.csv", pool);
    }

    private static void checkPoolSize() {
        List<Document> docs = sample();
        check("pool 0", !tryPool(docs, 0));
        check("pool 1", tryPool(docs, 1));
        check("pool negative", !tryPool(docs, -3));
        check("pool max", tryPool(docs, BatchProcessor.MAX_POOL_SIZE));
        check("pool max + 1", !tryPool(docs, BatchProcessor.MAX_POOL_SIZE + 1));
    }

    private static void checkErrors() {
        List<Document> docs = new ArrayList<>(sample());
        docs.add(1, new Document("BAD", -10, "PDF"));
        BatchResult r = BATCH.processAll(docs, 3);
        // ошибка BAD не отменила остальные 4 документа
        check("error localized", r.successCount() == 4 && r.errorCount() == 1 && r.errors().containsKey("BAD"));
        check("error message", r.errors().get("BAD").contains("-10"));
        check("empty list", BATCH.processAll(List.of(), 2).successCount() == 0);
        check("duplicate id", !tryPool(List.of(new Document("D1", 1, "PDF"), new Document("D1", 2, "TXT")), 2));
        // результат один и тот же при разном порядке завершения задач
        boolean same = true;
        for (int pool = 1; pool <= 8; pool++) {
            BatchResult x = BATCH.processAll(docs, pool);
            same = same && x.successCount() == 4 && x.errorCount() == 1;
        }
        check("same counts for pools 1..8", same);
        BatchResult seq = BATCH.processSequential(docs);
        check("sequential same counts", seq.successCount() == 4 && seq.errorCount() == 1);
    }

    // 8 потоков по 10000 раз увеличивают общий счетчик
    private static void checkThreadSafety() throws InterruptedException {
        BatchResult r = new BatchResult();
        ExecutorService ex = Executors.newFixedThreadPool(8);
        for (int t = 0; t < 8; t++) {
            ex.submit(() -> {
                for (int i = 0; i < 10000; i++) {
                    r.registerSuccess();
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(10, TimeUnit.SECONDS);
        check("atomic counter 80000", r.successCount() == 80000);
    }

    // после processAll потоков пула не остается
    private static void checkShutdown() throws InterruptedException {
        BATCH.processAll(sample(), 4);
        Thread.sleep(200);
        int alive = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("pool-") && t.isAlive()) {
                alive++;
            }
        }
        check("pool threads finished", alive == 0);
    }

    private static void checkVariant() {
        // вариант 4: пул до 25 потоков
        check("variant max pool 25", BatchProcessor.MAX_POOL_SIZE == 25);
        check("variant pool 9", tryPool(sample(), 9));
        check("variant pool 25", BATCH.processAll(sample(), 25).successCount() == 4);
        check("variant pool 26", !tryPool(sample(), 26));
    }

    // время зависит от компьютера, ускорение не гарантия
    private static void compareTime() {
        List<Document> docs = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            docs.add(new Document("T" + i, i * 20, "PDF"));
        }
        long t0 = System.currentTimeMillis();
        BatchResult seq = BATCH.processSequential(docs);
        long t1 = System.currentTimeMillis();
        BatchResult p4 = BATCH.processAll(docs, 4);
        long t2 = System.currentTimeMillis();
        BatchResult p25 = BATCH.processAll(docs, 25);
        long t3 = System.currentTimeMillis();
        System.out.println();
        System.out.println("Время на 40 документах:");
        System.out.println("  последовательно: " + (t1 - t0) + " мс, успешно " + seq.successCount());
        System.out.println("  пул 4:  " + (t2 - t1) + " мс, успешно " + p4.successCount());
        System.out.println("  пул 25: " + (t3 - t2) + " мс, успешно " + p25.successCount());
    }

    // ---------- вспомогательные методы ----------

    private static List<Document> sample() {
        return List.of(new Document("D1", 120, "PDF"), new Document("D2", 400, "DOCX"),
                new Document("D3", 50, "TXT"), new Document("D4", 800, "PDF"));
    }

    private static boolean tryPool(List<Document> docs, int pool) {
        try {
            BATCH.processAll(docs, pool);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void check(String name, boolean ok) {
        count++;
        if (ok) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            System.out.println("FAIL " + name);
        }
    }

    // ---------- документы из файла id;sizeKb;type ----------

    private static void runFile(String fileName, int pool) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(fileName));
        } catch (IOException e) {
            System.out.println("Не удалось прочитать " + fileName);
            return;
        }
        System.out.println();
        System.out.println("Документы из файла " + fileName + ", пул " + pool + ":");
        List<Document> docs = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {   // первая строка - заголовок
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.split(";", -1);
            try {
                if (p.length != 3 || p[0].isBlank()) {
                    throw new IllegalArgumentException("нужно 3 значения через ; и непустой id");
                }
                docs.add(new Document(p[0].strip(), Integer.parseInt(p[1].strip()), p[2].strip()));
            } catch (NumberFormatException e) {
                System.out.println("строка " + (i + 1) + " пропущена: размер не целое число");
            } catch (IllegalArgumentException e) {
                System.out.println("строка " + (i + 1) + " пропущена: " + e.getMessage());
            }
        }
        BatchResult r;
        try {
            r = BATCH.processAll(docs, pool);
        } catch (IllegalArgumentException e) {
            System.out.println("пакет не обработан: " + e.getMessage());
            return;
        }
        System.out.println("успешно " + r.successCount() + ", с ошибкой " + r.errorCount());
        Map<String, String> sorted = new TreeMap<>(r.errors());
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            System.out.println("  " + e.getKey() + ": " + e.getValue());
        }
    }
}
