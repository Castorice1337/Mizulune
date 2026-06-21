package shit.zen.music.engine;

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.music.model.MusicTrack;

public class JavaSoundMp3PlayerEngine implements MusicPlayerEngine {
    private static final Logger LOGGER = LogManager.getLogger(JavaSoundMp3PlayerEngine.class);
    private final Object lock = new Object();
    private volatile Path currentFile;
    private volatile MusicTrack currentTrack;
    private volatile SourceDataLine currentLine;
    private volatile boolean stopRequested;
    private volatile boolean paused;
    private volatile boolean playing;
    private volatile long positionMs;
    private volatile long durationMs;
    private volatile float volume = 0.8f;
    private volatile int generation;
    private Runnable finishedCallback = () -> {
    };
    private Consumer<String> errorCallback = message -> {
    };

    public void setFinishedCallback(Runnable finishedCallback) {
        this.finishedCallback = finishedCallback == null ? () -> {
        } : finishedCallback;
    }

    public void setErrorCallback(Consumer<String> errorCallback) {
        this.errorCallback = errorCallback == null ? message -> {
        } : errorCallback;
    }

    @Override
    public void play(Path file, MusicTrack track) {
        this.start(file, track, 0L, false);
    }

    @Override
    public void pause() {
        this.paused = true;
        SourceDataLine line = this.currentLine;
        if (line != null) {
            line.stop();
        }
    }

    @Override
    public void resume() {
        synchronized (this.lock) {
            this.paused = false;
            SourceDataLine line = this.currentLine;
            if (line != null) {
                line.start();
            }
            this.lock.notifyAll();
        }
    }

    @Override
    public void stop() {
        this.stopInternal();
    }

    @Override
    public void seek(long positionMs) {
        Path file = this.currentFile;
        MusicTrack track = this.currentTrack;
        if (file == null || track == null) {
            return;
        }
        this.start(file, track, Math.max(0L, positionMs), this.paused);
    }

    @Override
    public long getPositionMs() {
        return this.positionMs;
    }

    @Override
    public long getDurationMs() {
        return this.durationMs;
    }

    @Override
    public boolean isPlaying() {
        return this.playing && !this.paused;
    }

    @Override
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        this.applyVolume(this.currentLine);
    }

    private void start(Path file, MusicTrack track, long seekMs, boolean startPaused) {
        this.stopInternal();
        if (file == null || !Files.isRegularFile(file)) {
            this.errorCallback.accept("Unable to play this track.");
            return;
        }
        int runGeneration = ++this.generation;
        this.currentFile = file;
        this.currentTrack = track;
        this.positionMs = Math.max(0L, seekMs);
        this.paused = startPaused;
        this.stopRequested = false;
        Thread thread = new Thread(() -> this.runPlayback(runGeneration, file, seekMs), "Mizulune-MusicPlayer");
        thread.setDaemon(true);
        thread.start();
    }

    private void stopInternal() {
        this.stopRequested = true;
        this.playing = false;
        this.generation++;
        SourceDataLine line = this.currentLine;
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {
            }
        }
        synchronized (this.lock) {
            this.paused = false;
            this.lock.notifyAll();
        }
    }

    private void runPlayback(int runGeneration, Path file, long seekMs) {
        boolean finishedNaturally = false;
        long bytesWritten = 0L;
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file));
             AudioInputStream source = AudioSystem.getAudioInputStream(input)) {
            AudioFormat decodedFormat = this.decodedFormat(source.getFormat());
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(decodedFormat, source)) {
                this.durationMs = duration(decoded, decodedFormat);
                long skippedBytes = this.skipTo(decoded, decodedFormat, seekMs);
                bytesWritten = skippedBytes;
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                this.currentLine = line;
                line.open(decodedFormat);
                this.applyVolume(line);
                if (!this.paused) {
                    line.start();
                }
                this.playing = true;
                byte[] buffer = new byte[8192];
                int read;
                while (runGeneration == this.generation && !this.stopRequested && (read = decoded.read(buffer, 0, buffer.length)) != -1) {
                    synchronized (this.lock) {
                        while (this.paused && runGeneration == this.generation && !this.stopRequested) {
                            this.lock.wait(250L);
                        }
                    }
                    if (runGeneration != this.generation || this.stopRequested) {
                        break;
                    }
                    int offset = 0;
                    while (offset < read && runGeneration == this.generation && !this.stopRequested) {
                        offset += line.write(buffer, offset, read - offset);
                    }
                    bytesWritten += read;
                    this.positionMs = bytesToMillis(bytesWritten, decodedFormat);
                }
                if (runGeneration == this.generation && !this.stopRequested) {
                    line.drain();
                    finishedNaturally = true;
                }
                line.stop();
                line.close();
            }
        } catch (Exception exception) {
            if (runGeneration == this.generation && !this.stopRequested) {
                LOGGER.warn("Music playback failed for {}", file, exception);
                this.errorCallback.accept(this.isUnsupported(exception) ? "Unsupported audio format." : "Unable to play this track.");
            }
        } finally {
            if (runGeneration == this.generation) {
                this.playing = false;
                this.currentLine = null;
                if (finishedNaturally) {
                    this.finishedCallback.run();
                }
            }
        }
    }

    private AudioFormat decodedFormat(AudioFormat sourceFormat) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                16,
                sourceFormat.getChannels(),
                Math.max(1, sourceFormat.getChannels()) * 2,
                sourceFormat.getSampleRate(),
                false);
    }

    private long duration(AudioInputStream decoded, AudioFormat format) {
        long frames = decoded.getFrameLength();
        if (frames <= 0L || format.getFrameRate() <= 0.0f) {
            return 0L;
        }
        return (long) (frames * 1000.0 / format.getFrameRate());
    }

    private long skipTo(AudioInputStream decoded, AudioFormat format, long seekMs) throws Exception {
        long frameSize = Math.max(1, format.getFrameSize());
        long targetBytes = seekMs <= 0L ? 0L : (long) (seekMs * format.getFrameRate() * frameSize / 1000.0);
        targetBytes -= targetBytes % frameSize;
        long skipped = 0L;
        while (skipped < targetBytes) {
            long count = decoded.skip(targetBytes - skipped);
            if (count <= 0L) {
                break;
            }
            skipped += count;
        }
        return skipped;
    }

    private static long bytesToMillis(long bytes, AudioFormat format) {
        long bytesPerSecond = (long) (format.getFrameRate() * Math.max(1, format.getFrameSize()));
        return bytesPerSecond <= 0L ? 0L : bytes * 1000L / bytesPerSecond;
    }

    private void applyVolume(SourceDataLine line) {
        if (line == null || !line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        float value = this.volume <= 0.0001f
                ? control.getMinimum()
                : (float) (20.0 * Math.log10(this.volume));
        control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), value)));
    }

    private boolean isUnsupported(Exception exception) {
        String name = exception.getClass().getSimpleName().toLowerCase();
        return name.contains("unsupported") || name.contains("lineunavailable");
    }
}
