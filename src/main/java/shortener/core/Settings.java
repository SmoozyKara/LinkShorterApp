package shortener.core;

import java.net.URI;
import java.time.Duration;

public final class Settings {
    private final Duration ttl;
    private final int defaultLimit;
    private final int maxLimit;
    private final String prefix;
    private final int codeLength;
    private final long cleanupMillis;

    public Settings(Duration ttl, int defaultLimit, int maxLimit, String prefix, int codeLength, long cleanupMillis) {
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofDays(3650)) > 0
                || defaultLimit < 1 || defaultLimit > maxLimit || cleanupMillis < 1
                || codeLength < 4 || codeLength > 32) {
            throw new IllegalArgumentException("Некорректные параметры конфигурации");
        }
        URI uri = Urls.parse(prefix);
        if (!prefix.endsWith("/") || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Префикс должен завершаться / и не содержать query/fragment");
        }
        this.ttl = ttl;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
        this.prefix = prefix;
        this.codeLength = codeLength;
        this.cleanupMillis = cleanupMillis;
    }

    public Duration getTtl() {
        return ttl;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public String getPrefix() {
        return prefix;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public long getCleanupMillis() {
        return cleanupMillis;
    }
}
