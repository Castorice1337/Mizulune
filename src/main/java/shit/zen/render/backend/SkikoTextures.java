package shit.zen.render.backend;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.skia.Image;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class SkikoTextures {
    private final Map<String, Image> imageCache = new HashMap<>();
    private final Set<String> missingResources = new HashSet<>();

    Image getResourceImage(ResourceLocation resourceLocation) {
        if (resourceLocation == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        String key = this.resourceKey(mc, resourceLocation);
        if (this.missingResources.contains(key)) {
            return null;
        }
        Image cached = this.imageCache.get(key);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = mc.getResourceManager().open(resourceLocation)) {
            Image image = Image.Companion.makeFromEncoded(stream.readAllBytes());
            if (image != null) {
                this.imageCache.put(key, image);
                return image;
            }
        } catch (Exception ignored) {
            // Some Minecraft textures, especially downloaded skins, only exist as GL textures.
        }
        this.missingResources.add(key);
        return null;
    }

    int getCachedImageCount() {
        return this.imageCache.size();
    }

    int getMissingResourceCount() {
        return this.missingResources.size();
    }

    private String resourceKey(Minecraft mc, ResourceLocation resourceLocation) {
        return System.identityHashCode(mc.getResourceManager()) + ":" + resourceLocation;
    }
}
