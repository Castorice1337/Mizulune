package shit.zen.hook;

/** Original invocation supplied by either Patchify ASM or a Fabric Mixin adapter. */
@FunctionalInterface
public interface OriginalCall<T> {
    T call() throws Exception;
}
