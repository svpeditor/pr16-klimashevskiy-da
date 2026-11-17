package ru.edu.pr13;

public class DocumentProcessor {
    // обработка условно занимает время: 10 мс + 1 мс на каждые 20 КБ
    public ProcessingResult process(Document d) {
        if (d.sizeKb() < 0) {
            throw new IllegalArgumentException("отрицательный размер " + d.sizeKb());
        }
        try {
            Thread.sleep(10 + d.sizeKb() / 20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("обработка прервана");
        }
        long x = (long) d.id().hashCode() * 31 + d.sizeKb();
        return new ProcessingResult(d.id(), x);
    }
}
