package shortener.core;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class LinkService {
    private final Store store;
    private final Settings settings;
    private final Clock clock;
    private final Supplier<String> codes;
    private final Browser browser;
    private final State state;
    private boolean storageFailed;

    public LinkService(Store store, Settings settings, Clock clock, Supplier<String> codes,
                       Browser browser) throws IOException {
        this.store = store;
        this.settings = settings;
        this.clock = clock;
        this.codes = codes;
        this.browser = browser;
        this.state = store.load();
        cleanup();
    }

    // Если UUID ещё нет, создаём его вместе с первой ссылкой.
    public synchronized Link create(UUID owner, String url, Integer requestedLimit) throws IOException {
        URI uri = Urls.parse(url);
        int limit = requestedLimit == null ? settings.getDefaultLimit() : requestedLimit;
        checkLimit(limit);
        cleanup();
        if (owner == null) {
            do {
                owner = UUID.randomUUID();
            } while (state.getUsers().contains(owner));
        } else {
            requireUser(owner);
        }
        String code = uniqueCode();
        Instant expiresAt = clock.instant().plus(settings.getTtl());
        Link link = new Link(code, uri, owner, expiresAt, limit, 0);
        state.getUsers().add(owner);
        state.getIssued().add(code);
        state.getLinks().put(code, link);
        save();
        return link;
    }

    private String uniqueCode() {
        for (int i = 0; i < 1000; i++) {
            String code = codes.get();
            if (code != null && code.matches("[A-Za-z0-9]{4,32}") && !state.getIssued().contains(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Не удалось выделить уникальный код");
    }

    public synchronized Link open(String shortLink) throws IOException {
        cleanup();
        Link link = find(shortLink);
        if (link.isBlocked()) {
            throw new IllegalStateException("Лимит переходов исчерпан. Попросите владельца создать новую ссылку.");
        }
        // Учитываем переход до открытия браузера. При ошибке записи браузер не откроется.
        link.incrementClicks();
        State.Notice notice = null;
        if (link.isBlocked()) {
            notice = limitNotice(link);
            state.getNotices().add(notice);
        }
        save();
        try {
            browser.open(link.getUrl());
        } catch (IOException | RuntimeException error) {
            // Если браузер не открылся, отменяем только этот переход и его уведомление.
            link.decrementClicks();
            if (notice != null) {
                state.getNotices().remove(notice);
            }
            save();
            throw error;
        }
        return link;
    }

    public synchronized Link editLimit(UUID owner, String shortLink, int limit) throws IOException {
        checkLimit(limit);
        cleanup();
        Link link = owned(owner, shortLink);
        if (limit < link.getClicks()) {
            throw new IllegalArgumentException("Лимит не может быть меньше числа совершённых переходов");
        }
        boolean wasBlocked = link.isBlocked();
        link.setLimit(limit);
        if (!wasBlocked && link.isBlocked()) {
            state.getNotices().add(limitNotice(link));
        }
        save();
        return link;
    }

    public synchronized Link editUrl(UUID owner, String shortLink, String url) throws IOException {
        URI uri = Urls.parse(url);
        cleanup();
        Link link = owned(owner, shortLink);
        link.setUrl(uri);
        save();
        return link;
    }

    public synchronized void delete(UUID owner, String shortLink) throws IOException {
        cleanup();
        Link link = owned(owner, shortLink);
        state.getLinks().remove(link.getCode());
        save();
    }

    public synchronized List<Link> list(UUID owner) throws IOException {
        cleanup();
        requireUser(owner);
        List<Link> result = new ArrayList<>();
        for (Link link : state.getLinks().values()) {
            if (link.getOwner().equals(owner)) {
                result.add(link);
            }
        }
        result.sort(Comparator.comparing(Link::getCode));
        return result;
    }

    public synchronized void requireUser(UUID owner) {
        if (owner == null || !state.getUsers().contains(owner)) {
            throw new IllegalArgumentException("Нет такого пользователя. Создайте ссылку или войдите по своему UUID.");
        }
    }

    public synchronized List<String> takeNotices(UUID owner) throws IOException {
        cleanup();
        requireUser(owner);
        List<String> result = new ArrayList<>();
        Iterator<State.Notice> iterator = state.getNotices().iterator();
        while (iterator.hasNext()) {
            State.Notice notice = iterator.next();
            if (notice.getOwner().equals(owner)) {
                result.add(notice.getMessage());
                iterator.remove();
            }
        }
        if (!result.isEmpty()) {
            save();
        }
        return result;
    }

    public synchronized int cleanup() throws IOException {
        if (storageFailed) {
            throw new IOException("Хранилище недоступно. Проверьте файл данных и перезапустите приложение.");
        }
        Instant now = clock.instant();
        int count = 0;
        Iterator<Link> iterator = state.getLinks().values().iterator();
        while (iterator.hasNext()) {
            Link link = iterator.next();
            if (!now.isBefore(link.getExpiresAt())) {
                iterator.remove();
                state.getNotices().add(new State.Notice(link.getOwner(), shortUrl(link.getCode())
                        + ": срок действия истёк, ссылка удалена. Создайте новую ссылку."));
                count++;
            }
        }
        if (count > 0) {
            save();
        }
        return count;
    }

    public String shortUrl(String code) {
        return settings.getPrefix() + code;
    }

    private Link find(String input) {
        String code = input;
        if (input.startsWith(settings.getPrefix())) {
            code = input.substring(settings.getPrefix().length());
        }
        Link link = state.getLinks().get(code);
        if (link == null) {
            if (state.getIssued().contains(code)) {
                throw new IllegalArgumentException("Ссылка удалена или просрочена");
            }
            throw new IllegalArgumentException("Короткая ссылка не найдена");
        }
        return link;
    }

    private Link owned(UUID owner, String input) {
        requireUser(owner);
        Link link = find(input);
        if (!owner.equals(link.getOwner())) {
            throw new IllegalArgumentException("Нет прав: операция доступна только владельцу ссылки");
        }
        return link;
    }

    private void checkLimit(int limit) {
        if (limit < 1 || limit > settings.getMaxLimit()) {
            throw new IllegalArgumentException("Лимит должен быть от 1 до " + settings.getMaxLimit());
        }
    }

    private State.Notice limitNotice(Link link) {
        return new State.Notice(link.getOwner(), shortUrl(link.getCode())
                + ": лимит переходов исчерпан. Создайте новую ссылку или увеличьте лимит.");
    }

    // При сбое записи прекращаем операции со ссылками до перезапуска,
    // чтобы не продолжать работу с несохранёнными изменениями.
    private void save() throws IOException {
        try {
            store.save(state);
        } catch (IOException | RuntimeException error) {
            storageFailed = true;
            throw new IOException("Не удалось сохранить данные. Перезапустите приложение.", error);
        }
    }
}
