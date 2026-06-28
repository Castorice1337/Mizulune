package shit.zen.music.engine;

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import javax.sound.sampled.AudioFileFormat;
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
        LOGGER.info("[MizuluneMusic][engine:{}] schedule file={} track={} seekMs={} paused={}",
                runGeneration, file, track == null ? "unknown" : track.stableKey(), seekMs, startPaused);
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
        long startedAt = System.nanoTime();
        try (AudioInputStream source = this.openMp3(file)) {
            LOGGER.info("[MizuluneMusic][engine:{}] source format={} frameLength={} decoder={}",
                    runGeneration, source.getFormat(), source.getFrameLength(), codeSource(MpegAudioFileReader.class));
            AudioFormat decodedFormat = this.decodedFormat(source.getFormat());
            try (AudioInputStream decoded = this.decodeMp3(decodedFormat, source)) {
                this.durationMs = this.resolveDurationMs(file, source, decoded, decodedFormat);
                LOGGER.info("[MizuluneMusic][engine:{}] PCM format={} durationMs={}",
                        runGeneration, decodedFormat, this.durationMs);
                long skippedBytes = this.skipTo(decoded, decodedFormat, seekMs);
                bytesWritten = skippedBytes;
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                this.currentLine = line;
                line.open(decodedFormat);
                LOGGER.info("[MizuluneMusic][engine:{}] output line={} bufferBytes={}",
                        runGeneration, line.getLineInfo(), line.getBufferSize());
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
                LOGGER.warn("[MizuluneMusic][engine:{}] playback failed file={} elapsedMs={} decoder={} mixers={}",
                        runGeneration, file, (System.nanoTime() - startedAt) / 1_000_000L,
                        codeSource(MpegAudioFileReader.class), AudioSystem.getMixerInfo().length, exception);
                this.errorCallback.accept(this.isUnsupported(exception) ? "Unsupported audio format." : "Unable to play this track.");
            }
        } finally {
            if (runGeneration == this.generation) {
                this.playing = false;
                this.currentLine = null;
                if (finishedNaturally) {
                    LOGGER.info("[MizuluneMusic][engine:{}] finished naturally positionMs={} elapsedMs={}",
                            runGeneration, this.positionMs, (System.nanoTime() - startedAt) / 1_000_000L);
                    this.finishedCallback.run();
                }
            }
        }
    }

    private AudioInputStream openMp3(Path file) throws Exception {
        try {
            return new MpegAudioFileReader().getAudioInputStream(file.toFile());
        } catch (Exception directFailure) {
            LOGGER.warn("[MizuluneMusic][engine] direct MP3 reader failed; trying AudioSystem fallback file={}",
                    file, directFailure);
            BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file));
            try {
                return AudioSystem.getAudioInputStream(input);
            } catch (Exception fallbackFailure) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
                fallbackFailure.addSuppressed(directFailure);
                throw fallbackFailure;
            }
        }
    }

    private AudioInputStream decodeMp3(AudioFormat targetFormat, AudioInputStream source) throws Exception {
        MpegFormatConversionProvider provider = new MpegFormatConversionProvider();
        if (provider.isConversionSupported(targetFormat, source.getFormat())) {
            return provider.getAudioInputStream(targetFormat, source);
        }
        LOGGER.warn("[MizuluneMusic][engine] direct MP3 conversion unavailable source={} target={}; trying AudioSystem",
                source.getFormat(), targetFormat);
        return AudioSystem.getAudioInputStream(targetFormat, source);
    }

    private static String codeSource(Class<?> type) {
        try {
            return type.getProtectionDomain().getCodeSource().getLocation().toString();
        } catch (Exception ignored) {
            return "unknown";
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

    private long resolveDurationMs(Path file, AudioInputStream source, AudioInputStream decoded,
                                   AudioFormat decodedFormat) {
        long frameDuration = duration(decoded, decodedFormat);
        if (frameDuration > 0L) {
            return frameDuration;
        }
        try {
            AudioFileFormat fileFormat = new MpegAudioFileReader().getAudioFileFormat(file.toFile());
            long propertyDuration = durationPropertyMs(fileFormat.properties());
            if (propertyDuration > 0L) {
                return propertyDuration;
            }
            long bitrate = bitrateProperty(fileFormat.properties());
            if (bitrate > 0L) {
                return estimateDurationMs(Files.size(file), bitrate);
            }
        } catch (Exception exception) {
            LOGGER.debug("[MizuluneMusic][engine] unable to read MP3 duration properties file={}", file, exception);
        }
        long sourceDuration = durationPropertyMs(source.getFormat().properties());
        if (sourceDuration > 0L) {
            return sourceDuration;
        }
        long sourceBitrate = bitrateProperty(source.getFormat().properties());
        if (sourceBitrate > 0L) {
            try {
                return estimateDurationMs(Files.size(file), sourceBitrate);
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private static long durationPropertyMs(Map<String, Object> properties) {
        Object value = properties == null ? null : properties.get("duration");
        return value instanceof Number number ? Math.max(0L, number.longValue() / 1000L) : 0L;
    }

    private static long bitrateProperty(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return 0L;
        }
        for (String key : new String[] {"mp3.bitrate.nominal.bps", "bitrate", "audio.bitrate"}) {
            Object value = properties.get(key);
            if (value instanceof Number number && number.longValue() > 0L) {
                long bitrate = number.longValue();
                return bitrate < 10_000L ? bitrate * 1000L : bitrate;
            }
        }
        return 0L;
    }

    private static long estimateDurationMs(long bytes, long bitrateBitsPerSecond) {
        return bytes <= 0L || bitrateBitsPerSecond <= 0L
                ? 0L
                : Math.max(1L, bytes * 8000L / bitrateBitsPerSecond);
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
