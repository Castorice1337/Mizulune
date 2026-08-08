package shit.zen.hook;

/** Loader-neutral representation of whether an injected adapter owns the result. */
public record HookDecision<T>(boolean handled, T value) {
    private static final HookDecision<?> PASS = new HookDecision<>(false, null);

    @SuppressWarnings("unchecked")
    public static <T> HookDecision<T> pass() {
        return (HookDecision<T>) PASS;
    }

    public static <T> HookDecision<T> handled(T value) {
        return new HookDecision<>(true, value);
    }

    public static HookDecision<Void> cancel() {
        return handled(null);
    }
}
