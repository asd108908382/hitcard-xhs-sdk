package space.hitcard.xhs.sdk.oauth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized OAuth token snapshot persisted in a shared store.
 *
 * Expiration timestamps are stored as epoch milliseconds.
 */
public final class XhsOauthTokenRecord {

    private final String tokenKey;
    private final String accessToken;
    private final long accessTokenExpiresAt;
    private final String refreshToken;
    private final long refreshTokenExpiresAt;
    private final String sellerId;
    private final String sellerName;
    private final long version;
    private final long updatedAt;

    private XhsOauthTokenRecord(Builder builder) {
        this.tokenKey = requireNonBlank(builder.tokenKey, "tokenKey");
        this.accessToken = requireNonBlank(builder.accessToken, "accessToken");
        this.accessTokenExpiresAt = requirePositive(builder.accessTokenExpiresAt, "accessTokenExpiresAt");
        this.refreshToken = requireNonBlank(builder.refreshToken, "refreshToken");
        this.refreshTokenExpiresAt = requirePositive(builder.refreshTokenExpiresAt, "refreshTokenExpiresAt");
        this.sellerId = normalize(builder.sellerId);
        this.sellerName = normalize(builder.sellerName);
        this.version = requirePositive(builder.version, "version");
        this.updatedAt = requirePositive(builder.updatedAt, "updatedAt");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTokenKey() {
        return tokenKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public long getVersion() {
        return version;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean isAccessTokenExpired(long nowMillis, long refreshAheadMillis) {
        return accessTokenExpiresAt <= nowMillis + Math.max(refreshAheadMillis, 0L);
    }

    public boolean isRefreshTokenExpired(long nowMillis, long refreshAheadMillis) {
        return refreshTokenExpiresAt <= nowMillis + Math.max(refreshAheadMillis, 0L);
    }

    public Map<String, String> toMap() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("accessToken", accessToken);
        values.put("accessTokenExpiresAt", Long.toString(accessTokenExpiresAt));
        values.put("refreshToken", refreshToken);
        values.put("refreshTokenExpiresAt", Long.toString(refreshTokenExpiresAt));
        if (sellerId != null) {
            values.put("sellerId", sellerId);
        }
        if (sellerName != null) {
            values.put("sellerName", sellerName);
        }
        values.put("version", Long.toString(version));
        values.put("updatedAt", Long.toString(updatedAt));
        return values;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof XhsOauthTokenRecord)) {
            return false;
        }
        XhsOauthTokenRecord that = (XhsOauthTokenRecord) other;
        return accessTokenExpiresAt == that.accessTokenExpiresAt
                && refreshTokenExpiresAt == that.refreshTokenExpiresAt
                && version == that.version
                && updatedAt == that.updatedAt
                && Objects.equals(tokenKey, that.tokenKey)
                && Objects.equals(accessToken, that.accessToken)
                && Objects.equals(refreshToken, that.refreshToken)
                && Objects.equals(sellerId, that.sellerId)
                && Objects.equals(sellerName, that.sellerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenKey, accessToken, accessTokenExpiresAt, refreshToken,
                refreshTokenExpiresAt, sellerId, sellerName, version, updatedAt);
    }

    @Override
    public String toString() {
        return "XhsOauthTokenRecord{"
                + "tokenKey='" + tokenKey + '\''
                + ", accessToken='" + mask(accessToken) + '\''
                + ", accessTokenExpiresAt=" + accessTokenExpiresAt
                + ", refreshToken='" + mask(refreshToken) + '\''
                + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt
                + ", sellerId='" + sellerId + '\''
                + ", sellerName='" + sellerName + '\''
                + ", version=" + version
                + ", updatedAt=" + updatedAt
                + '}';
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static long requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
        return value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 6) {
            return "******";
        }
        return value.substring(0, 3) + "******" + value.substring(value.length() - 3);
    }

    public static final class Builder {
        private String tokenKey;
        private String accessToken;
        private long accessTokenExpiresAt;
        private String refreshToken;
        private long refreshTokenExpiresAt;
        private String sellerId;
        private String sellerName;
        private long version;
        private long updatedAt;

        private Builder() {
        }

        public Builder tokenKey(String tokenKey) {
            this.tokenKey = tokenKey;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder accessTokenExpiresAt(long accessTokenExpiresAt) {
            this.accessTokenExpiresAt = accessTokenExpiresAt;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder refreshTokenExpiresAt(long refreshTokenExpiresAt) {
            this.refreshTokenExpiresAt = refreshTokenExpiresAt;
            return this;
        }

        public Builder sellerId(String sellerId) {
            this.sellerId = sellerId;
            return this;
        }

        public Builder sellerName(String sellerName) {
            this.sellerName = sellerName;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Builder updatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public XhsOauthTokenRecord build() {
            return new XhsOauthTokenRecord(this);
        }
    }
}
