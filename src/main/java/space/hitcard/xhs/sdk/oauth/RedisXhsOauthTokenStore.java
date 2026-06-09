package space.hitcard.xhs.sdk.oauth;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Redis-backed token store for clustered deployments.
 */
public final class RedisXhsOauthTokenStore implements XhsOauthTokenStore {

    public static final String DEFAULT_KEY_PREFIX = "xhs:oauth:token";

    private static final String FIELD_ACCESS_TOKEN = "accessToken";
    private static final String FIELD_ACCESS_TOKEN_EXPIRES_AT = "accessTokenExpiresAt";
    private static final String FIELD_REFRESH_TOKEN = "refreshToken";
    private static final String FIELD_REFRESH_TOKEN_EXPIRES_AT = "refreshTokenExpiresAt";
    private static final String FIELD_SELLER_ID = "sellerId";
    private static final String FIELD_SELLER_NAME = "sellerName";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String CREATE_SCRIPT =
            "if redis.call('EXISTS', KEYS[1]) == 1 then "
                    + "return 0 "
                    + "end "
                    + "redis.call('HMSET', KEYS[1], "
                    + "'" + FIELD_ACCESS_TOKEN + "', ARGV[1], "
                    + "'" + FIELD_ACCESS_TOKEN_EXPIRES_AT + "', ARGV[2], "
                    + "'" + FIELD_REFRESH_TOKEN + "', ARGV[3], "
                    + "'" + FIELD_REFRESH_TOKEN_EXPIRES_AT + "', ARGV[4], "
                    + "'" + FIELD_SELLER_ID + "', ARGV[5], "
                    + "'" + FIELD_SELLER_NAME + "', ARGV[6], "
                    + "'" + FIELD_VERSION + "', ARGV[7], "
                    + "'" + FIELD_UPDATED_AT + "', ARGV[8]) "
                    + "if tonumber(ARGV[4]) > 0 then "
                    + "redis.call('PEXPIREAT', KEYS[1], tonumber(ARGV[4])) "
                    + "end "
                    + "return 1";
    private static final String COMPARE_AND_SET_SCRIPT =
            "local currentVersion = redis.call('HGET', KEYS[1], '" + FIELD_VERSION + "') "
                    + "if not currentVersion then "
                    + "return 0 "
                    + "end "
                    + "if currentVersion ~= ARGV[1] then "
                    + "return 0 "
                    + "end "
                    + "redis.call('HMSET', KEYS[1], "
                    + "'" + FIELD_ACCESS_TOKEN + "', ARGV[2], "
                    + "'" + FIELD_ACCESS_TOKEN_EXPIRES_AT + "', ARGV[3], "
                    + "'" + FIELD_REFRESH_TOKEN + "', ARGV[4], "
                    + "'" + FIELD_REFRESH_TOKEN_EXPIRES_AT + "', ARGV[5], "
                    + "'" + FIELD_SELLER_ID + "', ARGV[6], "
                    + "'" + FIELD_SELLER_NAME + "', ARGV[7], "
                    + "'" + FIELD_VERSION + "', ARGV[8], "
                    + "'" + FIELD_UPDATED_AT + "', ARGV[9]) "
                    + "if tonumber(ARGV[5]) > 0 then "
                    + "redis.call('PEXPIREAT', KEYS[1], tonumber(ARGV[5])) "
                    + "end "
                    + "return 1";

    private final JedisPool jedisPool;
    private final String keyNamespace;

    public RedisXhsOauthTokenStore(JedisPool jedisPool) {
        this(jedisPool, DEFAULT_KEY_PREFIX);
    }

    public RedisXhsOauthTokenStore(JedisPool jedisPool, String keyPrefix) {
        this(jedisPool, keyPrefix, null);
    }

    public RedisXhsOauthTokenStore(JedisPool jedisPool, String keyPrefix, String appId) {
        if (jedisPool == null) {
            throw new IllegalArgumentException("jedisPool must not be null");
        }
        if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        this.jedisPool = jedisPool;
        this.keyNamespace = buildKeyNamespace(keyPrefix, appId);
    }

    @Override
    public Optional<XhsOauthTokenRecord> get(String tokenKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            String redisKey = redisKey(tokenKey);
            if (!jedis.exists(redisKey)) {
                return Optional.empty();
            }
            return Optional.of(map(redisKey, jedis));
        } catch (RuntimeException exception) {
            throw new XhsOauthStorageException("Failed to query token from redis for key " + tokenKey, exception);
        }
    }

    @Override
    public boolean create(XhsOauthTokenRecord record) {
        try (Jedis jedis = jedisPool.getResource()) {
            String redisKey = redisKey(record.getTokenKey());
            Object result = jedis.eval(CREATE_SCRIPT,
                    Collections.singletonList(redisKey),
                    createArgs(record));
            return asLong(result) == 1L;
        } catch (RuntimeException exception) {
            throw new XhsOauthStorageException("Failed to create token in redis for key " + record.getTokenKey(), exception);
        }
    }

    @Override
    public boolean compareAndSet(String tokenKey, long expectedVersion, XhsOauthTokenRecord nextRecord) {
        String redisKey = redisKey(tokenKey);
        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(COMPARE_AND_SET_SCRIPT,
                    Collections.singletonList(redisKey),
                    compareAndSetArgs(expectedVersion, nextRecord));
            return asLong(result) == 1L;
        } catch (RuntimeException exception) {
            throw new XhsOauthStorageException("Failed to compare-and-set token in redis for key " + tokenKey, exception);
        }
    }

    private XhsOauthTokenRecord map(String redisKey, Jedis jedis) {
        java.util.Map<String, String> values = jedis.hgetAll(redisKey);
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("Missing redis hash for key " + redisKey);
        }
        return XhsOauthTokenRecord.builder()
                .tokenKey(stripPrefix(redisKey))
                .accessToken(required(values.get(FIELD_ACCESS_TOKEN), FIELD_ACCESS_TOKEN))
                .accessTokenExpiresAt(parseLong(required(values.get(FIELD_ACCESS_TOKEN_EXPIRES_AT), FIELD_ACCESS_TOKEN_EXPIRES_AT)))
                .refreshToken(required(values.get(FIELD_REFRESH_TOKEN), FIELD_REFRESH_TOKEN))
                .refreshTokenExpiresAt(parseLong(required(values.get(FIELD_REFRESH_TOKEN_EXPIRES_AT), FIELD_REFRESH_TOKEN_EXPIRES_AT)))
                .sellerId(values.get(FIELD_SELLER_ID))
                .sellerName(values.get(FIELD_SELLER_NAME))
                .version(parseLong(required(values.get(FIELD_VERSION), FIELD_VERSION)))
                .updatedAt(parseLong(required(values.get(FIELD_UPDATED_AT), FIELD_UPDATED_AT)))
                .build();
    }

    private static java.util.List<String> createArgs(XhsOauthTokenRecord record) {
        return Arrays.asList(
                record.getAccessToken(),
                Long.toString(record.getAccessTokenExpiresAt()),
                record.getRefreshToken(),
                Long.toString(record.getRefreshTokenExpiresAt()),
                nullToEmpty(record.getSellerId()),
                nullToEmpty(record.getSellerName()),
                Long.toString(record.getVersion()),
                Long.toString(record.getUpdatedAt())
        );
    }

    private static java.util.List<String> compareAndSetArgs(long expectedVersion, XhsOauthTokenRecord record) {
        return Arrays.asList(
                Long.toString(expectedVersion),
                record.getAccessToken(),
                Long.toString(record.getAccessTokenExpiresAt()),
                record.getRefreshToken(),
                Long.toString(record.getRefreshTokenExpiresAt()),
                nullToEmpty(record.getSellerId()),
                nullToEmpty(record.getSellerName()),
                Long.toString(record.getVersion()),
                Long.toString(record.getUpdatedAt())
        );
    }

    private String redisKey(String tokenKey) {
        if (tokenKey == null || tokenKey.trim().isEmpty()) {
            throw new IllegalArgumentException("tokenKey must not be blank");
        }
        return keyNamespace + tokenKey.trim();
    }

    private String stripPrefix(String redisKey) {
        if (redisKey.startsWith(keyNamespace)) {
            return redisKey.substring(keyNamespace.length());
        }
        return redisKey;
    }

    public String getKeyNamespace() {
        return keyNamespace;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing redis field " + fieldName);
        }
        return value;
    }

    private static long parseLong(String value) {
        return Long.parseLong(value);
    }

    private static long asLong(Object value) {
        if (value instanceof Long) {
            return ((Long) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String buildKeyNamespace(String keyPrefix, String appId) {
        StringBuilder builder = new StringBuilder(trimColon(keyPrefix));
        builder.append(':');
        if (appId != null && !appId.trim().isEmpty()) {
            builder.append(appId.trim()).append(':');
        }
        return builder.toString();
    }

    private static String trimColon(String value) {
        String normalized = value.trim();
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
