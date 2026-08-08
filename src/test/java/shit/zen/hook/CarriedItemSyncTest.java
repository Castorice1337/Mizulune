package shit.zen.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

final class CarriedItemSyncTest {
    @Test
    void reconcilesVanillaCarriedIndexAfterDirectSlotPackets() throws Exception {
        MultiPlayerGameMode gameMode = allocateWithoutMinecraftBootstrap();
        assertEquals(0, CarriedItemSync.vanillaCachedSlot(gameMode));

        CarriedItemSync.reconcileVanillaCache(gameMode, 6);

        assertEquals(6, CarriedItemSync.vanillaCachedSlot(gameMode));
    }

    @Test
    void onlyClientboundReceivingConnectionsEnterClientHooks() {
        assertTrue(ConnectionHookCallbacks.isClientConnection(
                new Connection(PacketFlow.CLIENTBOUND)));
        assertFalse(ConnectionHookCallbacks.isClientConnection(
                new Connection(PacketFlow.SERVERBOUND)));
    }

    private static MultiPlayerGameMode allocateWithoutMinecraftBootstrap() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        return (MultiPlayerGameMode) unsafe.allocateInstance(MultiPlayerGameMode.class);
    }
}
