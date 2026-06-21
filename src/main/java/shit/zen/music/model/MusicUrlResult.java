package shit.zen.music.model;

public class MusicUrlResult {
    private String url = "";
    private int br;
    private long size;
    private String from = "";

    public String getUrl() {
        return this.url == null ? "" : this.url;
    }

    public int getBr() {
        return this.br;
    }

    public long getSize() {
        return this.size;
    }

    public String getFrom() {
        return this.from == null ? "" : this.from;
    }
}
