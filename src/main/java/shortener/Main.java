package shortener;

import shortener.core.LinkService;
import shortener.infra.CodeGenerator;
import shortener.infra.Config;
import shortener.infra.DesktopBrowser;
import shortener.infra.FileStore;
import shortener.ui.Console;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        try {
            if (args.length > 1) {
                throw new IllegalArgumentException("Использование: java -jar out/link-shortener.jar [config.properties]");
            }
            String configFile = args.length == 0 ? "config.properties" : args[0];
            Config config = Config.load(Path.of(configFile));
            LinkService service = new LinkService(new FileStore(config.getDataFile()),
                    config.getSettings(), Clock.systemUTC(),
                    new CodeGenerator(config.getSettings().getCodeLength()), new DesktopBrowser());
            Console console = new Console(service, new PrintWriter(System.out, true, StandardCharsets.UTF_8));

            // Один фоновый поток проверяет TTL, пока консоль ждёт ввода.
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            try {
                long interval = config.getSettings().getCleanupMillis();
                scheduler.scheduleWithFixedDelay(console::tick, interval, interval, TimeUnit.MILLISECONDS);
                console.run(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            } finally {
                scheduler.shutdown();
            }
        } catch (IOException | RuntimeException error) {
            System.err.println("Ошибка запуска приложения: " + error.getMessage());
            System.exit(1);
        }
    }
}
