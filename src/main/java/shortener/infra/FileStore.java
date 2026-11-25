package shortener.infra;

import shortener.core.Link;
import shortener.core.State;
import shortener.core.Store;
import shortener.core.Urls;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.UUID;

public final class FileStore implements Store {
    private final Path file;

    public FileStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    @Override
    public State load() throws IOException {
        if (Files.notExists(file)) {
            return State.empty();
        }
        Properties p = new Properties();
        try (BufferedReader input = Files.newBufferedReader(file)) {
            p.load(input);
        }
        try {
            if (!"1".equals(p.getProperty("version"))) {
                throw new IllegalArgumentException("Неизвестная версия хранилища");
            }
            HashSet<UUID> users = new HashSet<>();
            HashSet<String> issued = new HashSet<>();
            HashMap<String, Link> links = new HashMap<>();
            ArrayList<State.Notice> notices = new ArrayList<>();
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("user.")) {
                    users.add(UUID.fromString(key.substring(5)));
                } else if (key.startsWith("issued.")) {
                    issued.add(key.substring(7));
                } else if (key.startsWith("link.") && key.endsWith(".url")) {
                    String code = key.substring(5, key.length() - 4);
                    String base = "link." + code + ".";
                    Link link = new Link(code, Urls.parse(p.getProperty(key)),
                            UUID.fromString(p.getProperty(base + "owner")),
                            Instant.parse(p.getProperty(base + "expires")),
                            Integer.parseInt(p.getProperty(base + "limit")),
                            Integer.parseInt(p.getProperty(base + "clicks")));
                    if (link.getLimit() < 1 || link.getClicks() < 0 || link.getClicks() > link.getLimit()) {
                        throw new IllegalArgumentException("Некорректный счётчик");
                    }
                    links.put(code, link);
                }
            }
            int count = Integer.parseInt(p.getProperty("notice.count", "0"));
            if (count < 0) {
                throw new IllegalArgumentException("Некорректное число уведомлений");
            }
            for (int i = 0; i < count; i++) {
                String base = "notice." + i + ".";
                notices.add(new State.Notice(UUID.fromString(p.getProperty(base + "owner")),
                        java.util.Objects.requireNonNull(p.getProperty(base + "message"))));
            }
            for (Link link : links.values()) {
                if (!users.contains(link.getOwner()) || !issued.contains(link.getCode())) {
                    throw new IllegalArgumentException("Нарушена целостность ссылок");
                }
            }
            for (State.Notice notice : notices) {
                if (!users.contains(notice.getOwner())) {
                    throw new IllegalArgumentException("Неизвестный получатель уведомления");
                }
            }
            return new State(users, issued, links, notices);
        } catch (RuntimeException error) {
            throw new IOException("Повреждён файл данных: " + file, error);
        }
    }

    @Override
    public void save(State state) throws IOException {
        Properties p = new Properties();
        p.setProperty("version", "1");
        for (UUID id : state.getUsers()) {
            p.setProperty("user." + id, "true");
        }
        for (String code : state.getIssued()) {
            p.setProperty("issued." + code, "true");
        }
        for (Link link : state.getLinks().values()) {
            String base = "link." + link.getCode() + ".";
            p.setProperty(base + "url", link.getUrl().toString());
            p.setProperty(base + "owner", link.getOwner().toString());
            p.setProperty(base + "expires", link.getExpiresAt().toString());
            p.setProperty(base + "limit", Integer.toString(link.getLimit()));
            p.setProperty(base + "clicks", Integer.toString(link.getClicks()));
        }
        p.setProperty("notice.count", Integer.toString(state.getNotices().size()));
        for (int i = 0; i < state.getNotices().size(); i++) {
            State.Notice notice = state.getNotices().get(i);
            String base = "notice." + i + ".";
            p.setProperty(base + "owner", notice.getOwner().toString());
            p.setProperty(base + "message", notice.getMessage());
        }
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), ".state-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temp)) {
                p.store(writer, "Link shortener state");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
