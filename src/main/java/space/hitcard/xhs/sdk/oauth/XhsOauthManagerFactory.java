package space.hitcard.xhs.sdk.oauth;

import com.xiaohongshu.fls.opensdk.client.OauthClient;
import redis.clients.jedis.JedisPool;

/**
 * Factory for building OAuth managers and shared Redis-backed token stores.
 */
public final class XhsOauthManagerFactory {

    private XhsOauthManagerFactory() {
    }

    public static XhsOauthTokenManager create(OauthClient oauthClient, XhsOauthTokenStore tokenStore) {
        return new XhsOauthTokenManager(oauthClient, tokenStore);
    }

    public static XhsOauthTokenManager create(OauthClient oauthClient, XhsOauthTokenStore tokenStore,
                                              long refreshAheadMillis) {
        return new XhsOauthTokenManager(oauthClient, tokenStore, refreshAheadMillis);
    }

    public static RedisXhsOauthTokenStore redisStore(JedisPool jedisPool, String appId) {
        return new RedisXhsOauthTokenStore(jedisPool, RedisXhsOauthTokenStore.DEFAULT_KEY_PREFIX, appId);
    }

    public static RedisXhsOauthTokenStore redisStore(JedisPool jedisPool, String keyPrefix, String appId) {
        return new RedisXhsOauthTokenStore(jedisPool, keyPrefix, appId);
    }

    public static XhsOauthTokenManager redis(OauthClient oauthClient, JedisPool jedisPool, String appId) {
        return create(oauthClient, redisStore(jedisPool, appId));
    }

    public static XhsOauthTokenManager redis(OauthClient oauthClient, JedisPool jedisPool,
                                             String keyPrefix, String appId) {
        return create(oauthClient, redisStore(jedisPool, keyPrefix, appId));
    }

    public static XhsOauthTokenManager redis(OauthClient oauthClient, JedisPool jedisPool,
                                             String keyPrefix, String appId, long refreshAheadMillis) {
        return create(oauthClient, redisStore(jedisPool, keyPrefix, appId), refreshAheadMillis);
    }
}
