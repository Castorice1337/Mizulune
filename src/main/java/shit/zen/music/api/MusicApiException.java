package shit.zen.music.api;

public class MusicApiException extends RuntimeException {
    public MusicApiException(String message) {
        super(message);
    }

    public MusicApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
