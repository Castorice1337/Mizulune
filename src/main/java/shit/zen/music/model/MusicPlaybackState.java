package shit.zen.music.model;

public class MusicPlaybackState {
    private final MusicTrack currentTrack;
    private final boolean playing;
    private final boolean loading;
    private final long positionMs;
    private final long durationMs;
    private final float volume;
    private final PlayMode playMode;
    private final boolean cached;
    private final String status;
    private final String error;

    public MusicPlaybackState(MusicTrack currentTrack, boolean playing, boolean loading, long positionMs,
                              long durationMs, float volume, PlayMode playMode, boolean cached,
                              String status, String error) {
        this.currentTrack = currentTrack;
        this.playing = playing;
        this.loading = loading;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.volume = volume;
        this.playMode = playMode == null ? PlayMode.ORDER : playMode;
        this.cached = cached;
        this.status = status == null ? "" : status;
        this.error = error == null ? "" : error;
    }

    public static MusicPlaybackState empty(float volume, PlayMode playMode) {
        return new MusicPlaybackState(null, false, false, 0L, 0L, volume, playMode,
                false, "No track playing", "");
    }

    public MusicTrack getCurrentTrack() {
        return this.currentTrack;
    }

    public boolean isPlaying() {
        return this.playing;
    }

    public boolean isLoading() {
        return this.loading;
    }

    public long getPositionMs() {
        return this.positionMs;
    }

    public long getDurationMs() {
        return this.durationMs;
    }

    public float getVolume() {
        return this.volume;
    }

    public PlayMode getPlayMode() {
        return this.playMode;
    }

    public boolean isCached() {
        return this.cached;
    }

    public String getStatus() {
        return this.status;
    }

    public String getError() {
        return this.error;
    }
}
