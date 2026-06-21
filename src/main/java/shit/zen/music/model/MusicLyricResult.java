package shit.zen.music.model;

public class MusicLyricResult {
    private String lyric = "";
    private String tlyric = "";
    private String from = "";

    public String getLyric() {
        return this.lyric == null ? "" : this.lyric;
    }

    public String getTranslatedLyric() {
        return this.tlyric == null ? "" : this.tlyric;
    }

    public String getFrom() {
        return this.from == null ? "" : this.from;
    }
}
