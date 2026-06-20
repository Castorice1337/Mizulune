package shit.zen.utils.render;

public final class ScissorStack {
    public static void push(int x, int y, int width, int height) {
        RenderUtil.pushScissor(x, y, width, height);
    }

    public static void pop() {
        RenderUtil.popScissor();
    }

    private ScissorStack() {
    }
}
