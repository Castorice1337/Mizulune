package shit.zen.music.model;

public enum PlayMode {
    ORDER,
    SHUFFLE,
    SINGLE;

    public PlayMode next() {
        PlayMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
