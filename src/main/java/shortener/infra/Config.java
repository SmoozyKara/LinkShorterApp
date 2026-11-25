package shortener.infra;

import shortener.core.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public final class Config {
    private final Settings settings;
    private final Path dataFile;

    private Config(Settings settings, Path dataFile) {
        this.settings = settings;
        this.dataFile = dataFile;
    }

    public Settings getSettings() {
        return settings;
    }

    public Path getDataFile() {
        return dataFile;
    }

    public static Config load(Path file) throws IOException {
        Properties p = new Properties();
        try (BufferedReader input = Files.newBufferedReader(file)) {
            p.load(input);
        }
        try {
            Settings settings = new Settings(Duration.ofSeconds(Long.parseLong(required(p, "ttl.seconds"))),
                    Integer.parseInt(required(p, "limit.default")), Integer.parseInt(required(p, "limit.max")),
                    required(p, "short.prefix"), Integer.parseInt(required(p, "code.length")),
                    Long.parseLong(required(p, "cleanup.millis")));
            Path path = Path.of(required(p, "data.file"));
            return new Config(settings, file.toAbsolutePath().getParent().resolve(path).normalize());
        } catch (RuntimeException error) {
            throw new IOException("Ошибка конфигурации: " + error.getMessage(), error);
        }
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Нет параметра " + key);
        }
        return value.trim();
    }
}
