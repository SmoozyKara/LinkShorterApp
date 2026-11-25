package shortener.core;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public final class Link {
    private final String code;
    private URI url;
    private final UUID owner;
    private final Instant expiresAt;
    private int limit;
    private int clicks;

    public Link(String code, URI url, UUID owner, Instant expiresAt, int limit, int clicks) {
        this.code = code;
        this.url = url;
        this.owner = owner;
        this.expiresAt = expiresAt;
        this.limit = limit;
        this.clicks = clicks;
    }

    public String getCode() {
        return code;
    }

    public URI getUrl() {
        return url;
    }

    public UUID getOwner() {
        return owner;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getLimit() {
        return limit;
    }

    public int getClicks() {
        return clicks;
    }

    public boolean isBlocked() {
        return clicks >= limit;
    }

    void incrementClicks() {
        clicks++;
    }

    void decrementClicks() {
        clicks--;
    }

    void setLimit(int limit) {
        this.limit = limit;
    }

    void setUrl(URI url) {
        this.url = url;
    }
}
