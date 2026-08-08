package shit.zen.protocol.heypixel;

import java.util.Objects;

/** Platform-neutral ID1 input produced from one official environment snapshot. */
public record Id1BuildInput(
    Id1PacketBuilder.Id1Subtype subtype,
    Id1PacketBuilder.Context context,
    Object subtypePayload
) {
    public Id1BuildInput {
        Objects.requireNonNull(subtype, "subtype");
        Objects.requireNonNull(context, "context");
    }
}
