package shit.zen.event.impl;

import lombok.*;
import shit.zen.event.EventMarker;

@Data
@AllArgsConstructor
public class FallFlyingEvent
implements EventMarker {
    @Getter @Setter
    private float pitch;
}