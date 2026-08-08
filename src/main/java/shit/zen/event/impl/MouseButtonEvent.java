package shit.zen.event.impl;

import shit.zen.event.EventMarker;

public record MouseButtonEvent(int button, int action) implements EventMarker {
}
