package shit.zen.music.engine;

import java.nio.file.Path;
import shit.zen.music.model.MusicTrack;

public interface MusicPlayerEngine {
    void play(Path file, MusicTrack track);

    void pause();

    void resume();

    void stop();

    void seek(long positionMs);

    long getPositionMs();

    long getDurationMs();

    boolean isPlaying();

    void setVolume(float volume);
}
