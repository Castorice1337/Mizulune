package shit.zen.value;

@FunctionalInterface
public interface ValueListener<T> {
    void onChanged(Value<T> value, T oldValue, T newValue);
}
