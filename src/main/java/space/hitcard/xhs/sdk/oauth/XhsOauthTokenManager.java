package space.hitcard.xhs.sdk.oauth;

import com.xiaohongshu.fls.opensdk.client.OauthClient;
import com.xiaohongshu.fls.opensdk.entity.BaseResponse;
import com.xiaohongshu.fls.opensdk.entity.oauth.request.GetAccessTokenRequest;
import com.xiaohongshu.fls.opensdk.entity.oauth.request.RefreshTokenRequest;
import com.xiaohongshu.fls.opensdk.entity.oauth.response.GetAccessTokenResponse;
import com.xiaohongshu.fls.opensdk.entity.oauth.response.RefreshTokenResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared OAuth token manager that keeps token state in a cluster-visible store.
 */
public final class XhsOauthTokenManager {

    public static final long DEFAULT_REFRESH_AHEAD_MILLIS = 5L * 60L * 1000L;

    private final OauthClient oauthClient;
    private final XhsOauthTokenStore tokenStore;
    private final long refreshAheadMillis;

    public XhsOauthTokenManager(OauthClient oauthClient, XhsOauthTokenStore tokenStore) {
        this(oauthClient, tokenStore, DEFAULT_REFRESH_AHEAD_MILLIS);
    }

    public XhsOauthTokenManager(OauthClient oauthClient, XhsOauthTokenStore tokenStore, long refreshAheadMillis) {
        this.oauthClient = Objects.requireNonNull(oauthClient, "oauthClient");
        this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore");
        if (refreshAheadMillis < 0L) {
            throw new IllegalArgumentException("refreshAheadMillis must be >= 0");
        }
        this.refreshAheadMillis = refreshAheadMillis;
    }

    public Optional<XhsOauthTokenRecord> find(String tokenKey) {
        return tokenStore.get(requireNonBlank(tokenKey, "tokenKey"));
    }

    public XhsOauthTokenRecord exchangeCode(String tokenKey, String code) throws IOException {
        String normalizedTokenKey = requireNonBlank(tokenKey, "tokenKey");
        String normalizedCode = requireNonBlank(code, "code");

        GetAccessTokenRequest request = new GetAccessTokenRequest();
        request.setCode(normalizedCode);
        BaseResponse<GetAccessTokenResponse> response = oauthClient.execute(request);
        GetAccessTokenResponse data = requireSuccess(response, "oauth.getAccessToken");

        return persistLatest(normalizedTokenKey, data.getAccessToken(), data.getAccessTokenExpiresAt(),
                data.getRefreshToken(), data.getRefreshTokenExpiresAt(), data.getSellerId(), data.getSellerName());
    }

    public String requireAccessToken(String tokenKey) throws IOException {
        return requireToken(tokenKey).getAccessToken();
    }

    public XhsOauthTokenRecord requireToken(String tokenKey) throws IOException {
        String normalizedTokenKey = requireNonBlank(tokenKey, "tokenKey");
        XhsOauthTokenRecord current = tokenStore.get(normalizedTokenKey)
                .orElseThrow(functionalErrorSupplier("Missing OAuth token for key " + normalizedTokenKey));
        if (isAccessTokenUsable(current)) {
            return current;
        }
        return refresh(normalizedTokenKey, current);
    }

    public XhsOauthTokenRecord refresh(String tokenKey) throws IOException {
        String normalizedTokenKey = requireNonBlank(tokenKey, "tokenKey");
        XhsOauthTokenRecord current = tokenStore.get(normalizedTokenKey)
                .orElseThrow(functionalErrorSupplier("Missing OAuth token for key " + normalizedTokenKey));
        return refresh(normalizedTokenKey, current);
    }

    public long getRefreshAheadMillis() {
        return refreshAheadMillis;
    }

    private XhsOauthTokenRecord refresh(String tokenKey, XhsOauthTokenRecord current) throws IOException {
        if (current.isRefreshTokenExpired(nowMillis(), refreshAheadMillis)) {
            throw functionalError("Refresh token expired for key " + tokenKey);
        }

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(current.getRefreshToken());

        try {
            BaseResponse<RefreshTokenResponse> response = oauthClient.execute(request);
            RefreshTokenResponse data = requireSuccess(response, "oauth.refreshToken");
            XhsOauthTokenRecord next = XhsOauthTokenRecord.builder()
                    .tokenKey(tokenKey)
                    .accessToken(requireNonBlank(data.getAccessToken(), "accessToken"))
                    .accessTokenExpiresAt(toEpochMillis(data.getAccessTokenExpiresAt()))
                    .refreshToken(requireNonBlank(data.getRefreshToken(), "refreshToken"))
                    .refreshTokenExpiresAt(toEpochMillis(data.getRefreshTokenExpiresAt()))
                    .sellerId(emptyToNull(data.getSellerId()))
                    .sellerName(emptyToNull(data.getSellerName()))
                    .version(current.getVersion() + 1L)
                    .updatedAt(nowMillis())
                    .build();
            if (tokenStore.compareAndSet(tokenKey, current.getVersion(), next)) {
                return next;
            }
        } catch (IOException exception) {
            XhsOauthTokenRecord latest = tokenStore.get(tokenKey).orElse(null);
            if (latest != null && latest.getVersion() > current.getVersion() && isAccessTokenUsable(latest)) {
                return latest;
            }
            throw exception;
        } catch (RuntimeException exception) {
            XhsOauthTokenRecord latest = tokenStore.get(tokenKey).orElse(null);
            if (latest != null && latest.getVersion() > current.getVersion() && isAccessTokenUsable(latest)) {
                return latest;
            }
            throw exception;
        }

        XhsOauthTokenRecord latest = tokenStore.get(tokenKey)
                .orElseThrow(functionalErrorSupplier("Token lost update for key " + tokenKey));
        if (isAccessTokenUsable(latest)) {
            return latest;
        }
        return latest;
    }

    private XhsOauthTokenRecord persistLatest(String tokenKey, String accessToken, long accessTokenExpiresAt,
                                              String refreshToken, long refreshTokenExpiresAt,
                                              String sellerId, String sellerName) {
        while (true) {
            Optional<XhsOauthTokenRecord> current = tokenStore.get(tokenKey);
            long nextVersion = current.isPresent() ? current.get().getVersion() + 1L : 1L;
            XhsOauthTokenRecord candidate = XhsOauthTokenRecord.builder()
                    .tokenKey(tokenKey)
                    .accessToken(requireNonBlank(accessToken, "accessToken"))
                    .accessTokenExpiresAt(toEpochMillis(accessTokenExpiresAt))
                    .refreshToken(requireNonBlank(refreshToken, "refreshToken"))
                    .refreshTokenExpiresAt(toEpochMillis(refreshTokenExpiresAt))
                    .sellerId(emptyToNull(sellerId))
                    .sellerName(emptyToNull(sellerName))
                    .version(nextVersion)
                    .updatedAt(nowMillis())
                    .build();
            if (!current.isPresent()) {
                if (tokenStore.create(candidate)) {
                    return candidate;
                }
                continue;
            }
            if (tokenStore.compareAndSet(tokenKey, current.get().getVersion(), candidate)) {
                return candidate;
            }
        }
    }

    private boolean isAccessTokenUsable(XhsOauthTokenRecord record) {
        return !record.isAccessTokenExpired(nowMillis(), refreshAheadMillis);
    }

    private static <T> T requireSuccess(BaseResponse<T> response, String method) {
        if (response == null) {
            throw functionalError("Null response from " + method);
        }
        if (!response.isSuccess() || response.getData() == null) {
            throw functionalError("Call " + method + " failed, code=" + response.getCode() + ", msg=" + response.getMsg());
        }
        return response.getData();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static IllegalStateException functionalError(String message) {
        return new IllegalStateException(message);
    }

    private static java.util.function.Supplier<IllegalStateException> functionalErrorSupplier(final String message) {
        return new java.util.function.Supplier<IllegalStateException>() {
            @Override
            public IllegalStateException get() {
                return new IllegalStateException(message);
            }
        };
    }

    private static long toEpochMillis(long value) {
        if (value <= 0L) {
            return value;
        }
        if (value < 1_000_000_000_000L) {
            return value * 1000L;
        }
        return value;
    }

    private static long nowMillis() {
        return System.currentTimeMillis();
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
