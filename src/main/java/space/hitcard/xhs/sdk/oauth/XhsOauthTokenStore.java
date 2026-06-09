package space.hitcard.xhs.sdk.oauth;

import java.util.Optional;

/**
 * Shared token persistence SPI for multi-node OAuth visibility.
 */
public interface XhsOauthTokenStore {

    Optional<XhsOauthTokenRecord> get(String tokenKey);

    boolean create(XhsOauthTokenRecord record);

    boolean compareAndSet(String tokenKey, long expectedVersion, XhsOauthTokenRecord nextRecord);
}
