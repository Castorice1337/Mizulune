package shit.zen.utils.rotation;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.CameraPitchEvent;
import shit.zen.event.impl.ChatEvent;
import shit.zen.event.impl.FallFlyingEvent;
import shit.zen.event.impl.RotationAnimationEvent