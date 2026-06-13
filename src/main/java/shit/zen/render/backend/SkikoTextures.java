package shit.zen.render.backend;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.skia.Image;
import shit.zen.utils.misc.Assets;

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
        try (InputStream stream = Assets.open(this.toClasspath(resourceLocation))) {
            if (stream != null) {
                Image image = Image.Companion.makeFromEncoded(stream.readAllBytes());
                if (image != null) {
                    this.imageCache.put(key, image);
                    return image;
                }
            }
        } catch (Exception ignored) {
            // DLL injection exposes jar resources through Assets instead of Minecraft's resource manager.
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
        String resourceDir = System.getProperty("mizulune.resources", "");
        return System.identityHashCode(mc.getResourceManager()) + ":" + resourceDir + ":" + resourceLocation;
    }

    private String toClasspath(ResourceLocation resourceLocation) {
        return "/assets/" + resourceLocation.getNamespace() + "/" + resourceLocation.getPath();
    }
}
