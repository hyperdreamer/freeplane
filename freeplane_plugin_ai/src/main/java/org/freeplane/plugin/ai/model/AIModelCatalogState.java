package org.freeplane.plugin.ai.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

public class AIModelCatalogState {
    static final long REFRESH_INTERVAL_MILLISECONDS = 30L * 60L * 1000L;
    private static final AIModelCatalogState SHARED = new AIModelCatalogState();

    private final Map<AIModelCatalogCacheKey, CacheEntry<?>> entries = new HashMap<>();
    private final LongSupplier currentTime;
    private final long refreshIntervalMilliseconds;

    public AIModelCatalogState() {
        this(System::currentTimeMillis, REFRESH_INTERVAL_MILLISECONDS);
    }

    AIModelCatalogState(LongSupplier currentTime, long refreshIntervalMilliseconds) {
        this.currentTime = currentTime;
        this.refreshIntervalMilliseconds = refreshIntervalMilliseconds;
    }

    public static AIModelCatalogState shared() {
        return SHARED;
    }

    public synchronized AIModelDiscoveryResult getFresh(AIModelCatalogCacheKey cacheKey) {
        return getFreshValue(cacheKey, AIModelDiscoveryResult.class);
    }

    public synchronized void recordSuccess(AIModelCatalogCacheKey cacheKey,
                                           List<DiscoveredAIModel> models) {
        recordValue(cacheKey, AIModelDiscoveryResult.success(models));
    }

    public synchronized void recordFailure(AIModelCatalogCacheKey cacheKey) {
        entries.remove(cacheKey);
    }

    synchronized <T> T getFreshValue(AIModelCatalogCacheKey cacheKey, Class<T> valueType) {
        CacheEntry<?> entry = entries.get(cacheKey);
        if (entry == null || currentTime.getAsLong() - entry.timestamp >= refreshIntervalMilliseconds) {
            return null;
        }
        return valueType.isInstance(entry.value) ? valueType.cast(entry.value) : null;
    }

    synchronized void recordValue(AIModelCatalogCacheKey cacheKey, Object value) {
        entries.put(cacheKey, new CacheEntry<>(currentTime.getAsLong(), value));
    }

    synchronized void clear() {
        entries.clear();
    }

    private static class CacheEntry<T> {
        private final long timestamp;
        private final T value;

        private CacheEntry(long timestamp, T value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}
