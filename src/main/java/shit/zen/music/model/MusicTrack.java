package shit.zen.music.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MusicTrack {
    private String id = "";
    private String name = "";
    private List<String> artist = new ArrayList<>();
    private String album = "";
    @SerializedName(value = "picId", alternate = {"pic_id", "pic"})
    private String picId = "";
    @SerializedName(value = "urlId", alternate = {"url_id"})
    private String urlId = "";
    @SerializedName(value = "lyricId", alternate = {"lyric_id"})
    private String lyricId = "";
    private String source = "";
    private String from = "";
    @SerializedName(value = "durationMs", alternate = {"duration", "interval"})
    private long durationMs = -1L;

    public MusicTrack() {
    }

    public String getId() {
        return safe(this.id);
    }

    public String getName() {
        return safe(this.name);
    }

    public List<String> getArtist() {
        if (this.artist == null || this.artist.isEmpty()) {
            return List.of();
        }
        return this.artist.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    public String getAlbum() {
        return safe(this.album);
    }

    public String getPicId() {
        return safe(this.picId);
    }

    public String getUrlId() {
        String value = safe(this.urlId);
        return value.isEmpty() ? this.getId() : value;
    }

    public String getLyricId() {
        String value = safe(this.lyricId);
        return value.isEmpty() ? this.getId() : value;
    }

    public String getSource() {
        return safe(this.source);
    }

    public String getFrom() {
        return safe(this.from);
    }

    public long getDurationMs() {
        return this.durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String artistsText() {
        List<String> artists = this.getArtist();
        if (artists.isEmpty()) {
            return "Unknown Artist";
        }
        return String.join(" / ", artists);
    }

    public String stableKey() {
        return this.getSource() + ":" + this.getId();
    }

    public boolean hasCover() {
        return !this.getPicId().isBlank();
    }

    public boolean hasLyric() {
        return !this.getLyricId().isBlank();
    }

    public MusicTrack copy() {
        MusicTrack copy = new MusicTrack();
        copy.id = this.getId();
        copy.name = this.getName();
        copy.artist = new ArrayList<>(this.getArtist());
        copy.album = this.getAlbum();
        copy.picId = this.getPicId();
        copy.urlId = this.getUrlId();
        copy.lyricId = this.getLyricId();
        copy.source = this.getSource();
        copy.from = this.getFrom();
        copy.durationMs = this.durationMs;
        return copy;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MusicTrack track)) {
            return false;
        }
        return Objects.equals(this.stableKey(), track.stableKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stableKey());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
