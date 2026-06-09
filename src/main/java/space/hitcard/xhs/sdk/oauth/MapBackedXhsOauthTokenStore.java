package space.hitcard.xhs.sdk.oauth;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lightweight store for local development or single-node execution.
 */
public final class MapBackedXhsOauthTokenStore implements XhsOauthTokenStore {

    private final ConcurrentMap<String, XhsOauthTokenRecord> storage = new ConcurrentHashMap<String, XhsOauthTokenRecord>();

    @Override
    public Optional<XhsOauthTokenRecord> get(String tokenKey) {
        return Optional.ofNullable(storage.get(tokenKey));
    }

    @Override
    public boolean create(XhsOauthTokenRecord record) {
        return storage.putIfAbsent(record.getTokenKey(), record) == null;
    }

    @Override
    public boolean compareAndSet(String tokenKey, long expectedVersion, XhsOauthTokenRecord nextRecord) {
        XhsOauthTokenRecord current = storage.get(tokenKey);
        if (current == null || current.getVersion() != expectedVersion) {
            return false;
        }
        return storage.replace(tokenKey, current, nextRecord);
    }
}
