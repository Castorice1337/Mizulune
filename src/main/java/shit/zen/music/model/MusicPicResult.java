package shit.zen.music.model;

public class MusicPicResult {
    private String url = "";
    private String from = "";

    public String getUrl() {
        return this.url == null ? "" : this.url;
    }

    public String getFrom() {
        return this.from == null ? "" : this.from;
    }
}
