package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import shit.zen.ZenClient;
import shit.zen.event.impl.SprintDecisionEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.utils.game.DirectionalInput;

@Patch(KeyboardInput.class)
public class KeyboardInputPatch extends Input {
    @Inject(method = "tick", desc = "(ZF)V", at = @At(At.Type.TAIL))
    public static void onTick(
            KeyboardInput input,
            boolean slowDown,
            float sneakMultiplier,
            CallbackInfo callbackInfo) {
        input.forwardImpulse = input.up == input.down ? 0.0f : (input.up ? 1.0f : -1.0f);
        input.leftImpulse = input.left == input.right ? 0.0f : (input.left ? 1.0f : -1.0f);
        StrafeEvent event = new StrafeEvent(
                input.forwardImpulse,
                input.leftImpulse,
                input.jumping,
                input.shiftKeyDown);
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        AppliedInput applied = applyEvent(event, slowDown, sneakMultiplier);
        input.forwardImpulse = applied.forward();
        input.leftImpulse = applied.strafe();
        input.jumping = applied.jumping();
        input.shiftKeyDown = applied.sneaking();
        Minecraft minecraft = Minecraft.getInstance();
        if (ZenClient.isReady() && minecraft.options != null) {
            SprintDecisionEvent sprintEvent = new SprintDecisionEvent(
                    DirectionalInput.fromImpulses(input.forwardImpulse, input.leftImpulse),
                    minecraft.options.keySprint.isDown(),
                    SprintDecisionEvent.Source.INPUT);
            ZenClient.getInstance().getEventBus().call(sprintEvent);
            KeyMapping.set(minecraft.options.keySprint.getKey(), sprintEvent.isSprinting());
            minecraft.options.keySprint.setDown(sprintEvent.isSprinting());
        }
    }

    static AppliedInput applyEvent(StrafeEvent event, boolean slowDown, float sneakMultiplier) {
        float multiplier = slowDown || event.isSneaking() ? sneakMultiplier : 1.0f;
        return new AppliedInput(
                event.getForward() * multiplier,
                event.getStrafe() * multiplier,
                event.isJumping(),
                event.isSneaking());
    }

    record AppliedInput(float forward, float strafe, boolean jumping, boolean sneaking) {
    }
}
