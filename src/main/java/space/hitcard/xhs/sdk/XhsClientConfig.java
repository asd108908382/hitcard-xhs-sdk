package space.hitcard.xhs.sdk;

import java.util.Objects;

/**
 * Immutable configuration for the wrapped XHS OpenSDK clients.
 */
public final class XhsClientConfig {

    public static final String DEFAULT_VERSION = "1.0";

    private final String url;
    private final String appId;
    private final String appSecret;
    private final String version;

    private XhsClientConfig(Builder builder) {
        this.url = requireNonBlank(builder.url, "url");
        this.appId = requireNonBlank(builder.appId, "appId");
        this.appSecret = requireNonBlank(builder.appSecret, "appSecret");
        this.version = normalize(builder.version);
    }

    public static XhsClientConfig of(String url, String appId, String appSecret) {
        return builder()
                .url(url)
                .appId(appId)
                .appSecret(appSecret)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUrl() {
        return url;
    }

    public String getAppId() {
        return appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof XhsClientConfig)) {
            return false;
        }
        XhsClientConfig that = (XhsClientConfig) other;
        return Objects.equals(url, that.url)
                && Objects.equals(appId, that.appId)
                && Objects.equals(appSecret, that.appSecret)
                && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, appId, appSecret, version);
    }

    @Override
    public String toString() {
        return "XhsClientConfig{"
                + "url='" + url + '\''
                + ", appId='" + appId + '\''
                + ", appSecret='" + mask(appSecret) + '\''
                + ", version='" + version + '\''
                + '}';
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_VERSION;
        }
        return value.trim();
    }

    private static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    public static final class Builder {
        private String url;
        private String appId;
        private String appSecret;
        private String version = DEFAULT_VERSION;

        private Builder() {
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder appId(String appId) {
            this.appId = appId;
            return this;
        }

        public Builder appSecret(String appSecret) {
            this.appSecret = appSecret;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public XhsClientConfig build() {
            return new XhsClientConfig(this);
        }
    }
}
