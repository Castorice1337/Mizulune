package shit.zen.hook;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import shit.zen.ZenClient;
import shit.zen.event.impl.SprintDecisionEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.utils.game.DirectionalInput;

/** 26.2 immutable-input implementation of the shared movement hook contract. */
public final class KeyboardInputHookCallbacks {
    private KeyboardInputHookCallbacks() {
    }

    public static void onTick(KeyboardInput input) {
        onTick(input, false, 1.0F);
    }

    public static void onTick(KeyboardInput input, boolean slowDown, float sneakMultiplier) {
        Input keys = input.keyPresses;
        Vec2 movement = input.getMoveVector();
        StrafeEvent event = new StrafeEvent(movement.y, movement.x, keys.jump(), keys.shift());
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        AppliedInput applied = applyEvent(event, slowDown, sneakMultiplier);
        input.moveVector = new Vec2(applied.strafe(), applied.forward());

        Minecraft minecraft = Minecraft.getInstance();
        boolean sprinting = keys.sprint();
        if (ZenClient.isReady() && minecraft.options != null) {
            SprintDecisionEvent sprintEvent = new SprintDecisionEvent(
                DirectionalInput.fromImpulses(applied.forward(), applied.strafe()),
                minecraft.options.keySprint.isDown(),
                SprintDecisionEvent.Source.INPUT);
            ZenClient.getInstance().getEventBus().call(sprintEvent);
            sprinting = sprintEvent.isSprinting();
            KeyMapping.set(minecraft.options.keySprint.key, sprinting);
            minecraft.options.keySprint.setDown(sprinting);
        }
        input.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(),
            applied.jumping(), applied.sneaking(), sprinting);
    }

    public static AppliedInput applyEvent(StrafeEvent event, boolean slowDown, float sneakMultiplier) {
        float multiplier = slowDown || event.isSneaking() ? sneakMultiplier : 1.0F;
        return new AppliedInput(event.getForward() * multiplier, event.getStrafe() * multiplier,
            event.isJumping(), event.isSneaking());
    }

    public record AppliedInput(float forward, float strafe, boolean jumping, boolean sneaking) {
    }
}
