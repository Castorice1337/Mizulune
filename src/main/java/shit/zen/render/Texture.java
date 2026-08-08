package shit.zen.render;

import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;

public final class Texture {
    private final int glId;
    private final int width;
    private final int height;
    private final ResourceLocation resourceLocation;
    private final Path imageFile;

    public Texture(int glId, int width, int height) {
        this.glId = glId;
        this.width = width;
        this.height = height;
        this.resourceLocation = null;
        this.imageFile = null;
    }

    public Texture(ResourceLocation resourceLocation, int width, int height) {
        this.glId = 0;
        this.width = width;
        this.height = height;
        this.resourceLocation = resourceLocation;
        this.imageFile = null;
    }

    public Texture(Path imageFile, int width, int height) {
        this.glId = 0;
        this.width = width;
        this.height = height;
        this.resourceLocation = null;
        this.imageFile = imageFile;
    }

    public Texture(int glId, ResourceLocation resourceLocation, int width, int height) {
        this.glId = glId;
        this.width = width;
        this.height = height;
        this.resourceLocation = resourceLocation;
        this.imageFile = null;
    }

    public int getGlId() {
        return this.glId;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public ResourceLocation getResourceLocation() {
        return this.resourceLocation;
    }

    public Path getImageFile() {
        return this.imageFile;
    }

    public boolean isFileTexture() {
        return this.imageFile != null;
    }
}
