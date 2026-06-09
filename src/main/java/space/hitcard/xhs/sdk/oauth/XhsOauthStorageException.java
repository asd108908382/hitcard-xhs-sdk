package space.hitcard.xhs.sdk.oauth;

/**
 * Runtime exception for token store persistence failures.
 */
public final class XhsOauthStorageException extends RuntimeException {

    public XhsOauthStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
