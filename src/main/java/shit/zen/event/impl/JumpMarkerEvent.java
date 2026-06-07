package shit.zen.event.impl;

import lombok.AllArgsConstructor;
import lombok.Data;
import shit.zen.event.EventMarker;

@Data
@AllArgsConstructor
public class JumpMarkerEvent
implements EventMarker {
    private float yaw;
}