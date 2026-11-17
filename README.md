# Практическая работа №16. Параллельное выполнение задач и безопасная агрегация результатов

Климашевский Даниил Александрович, группа ЭФБО-07-25, вариант 4.

Обработка пакета документов в ограниченном пуле потоков: одна задача Callable на документ, результаты через Future, ошибка документа не отменяет остальные, счетчики в AtomicInteger и ConcurrentHashMap, пул закрывается в finally. По варианту 4 размер пула от 1 до 25.

## Сборка и запуск

Нужна Java 21 или новее.

```bash
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -cp out ru.edu.pr13.SelfCheck
```

Или скриптом: `./run_selfcheck.sh` (macOS/Linux), `run_selfcheck.bat` (Windows).

Сначала выполняются проверки, потом обрабатывается `data/sample.csv` (формат `id;sizeKb;type`, первая строка - заголовок).
Свой файл: `java -cp out ru.edu.pr13.SelfCheck data/boundaries.csv`. Вторым аргументом передается размер пула: `java -cp out ru.edu.pr13.SelfCheck data/boundaries.csv 25`.

Пакет называется `ru.edu.pr13`, как в исходном проекте.

## Что где лежит

- `src/ru/edu/pr13/BatchProcessor.java` - пул потоков, Callable, Future, последовательный режим
- `src/ru/edu/pr13/BatchResult.java` - потокобезопасные агрегаты
- `src/ru/edu/pr13/DocumentProcessor.java` - обработка одного документа
- `src/ru/edu/pr13/Document.java` (и ProcessingResult.java) - документ и результат
- `src/ru/edu/pr13/SelfCheck.java` - проверки, сравнение времени, документы из файла
- `data/sample.csv` - открытый набор из задания, `data/boundaries.csv` - мои граничные значения
- `diagrams/` - activity-диаграмма и диаграмма классов (dot и png)
- `DECISIONS.md` - вариант, два решения, граничные проверки, ответы на вопросы задания
- `REPORT.docx` - отчет
