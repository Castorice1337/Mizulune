package shit.zen.value;

@FunctionalInterface
public interface ValueCondition {
    ValueCondition ALWAYS = () -> true;

    boolean visible();
}
