package shit.zen.utils.animation;

import lombok.Getter;
import lombok.Setter;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easing;
import shit.zen.utils.math.Easings;
import shit.zen.utils.render.Argb;

public class AnimatedColor {
    @Getter @Setter
    private int color;
    @Getter @Setter
    private SmoothAnimationTimer rTimer = new SmoothAnimationTimer();
    @Getter @Setter
    private SmoothAnimationTimer gTimer = new SmoothAnimationTimer();
    @Getter @Setter
    private SmoothAnimationTimer bTimer = new SmoothAnimationTimer();
    @Getter @Setter
    private SmoothAnimationTimer aTimer = new SmoothAnimationTimer();

    public AnimatedColor(int color) {
        this.color = color;
    }

    public void animateTo(int targetColor, float speed) {
        this.animateTo(targetColor, speed, Easings.EASE_OUT_QUAD);
    }

    public void animateTo(int targetColor, float speed, Easing easing) {
        this.rTimer.animate(Argb.red(targetColor), 0.2 / (double)speed, easing);
        this.gTimer.animate(Argb.green(targetColor), 0.2 / (double)speed, easing);
        this.bTimer.animate(Argb.blue(targetColor), 0.2 / (double)speed, easing);
        this.aTimer.animate(Argb.alpha(targetColor), 0.2 / (double)speed, easing);
        this.rTimer.tick();
        this.gTimer.tick();
        this.bTimer.tick();
        this.aTimer.tick();
        this.color = Argb.fromRgbaComponents(this.rTimer.getValueI(), this.gTimer.getValueI(), this.bTimer.getValueI(), this.aTimer.getValueI());
    }

    }