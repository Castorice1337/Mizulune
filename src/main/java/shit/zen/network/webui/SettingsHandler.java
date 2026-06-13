package shit.zen.network.webui;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import shit.zen.ZenClient;
import shit.zen.exception.ModuleNotFoundException;
import shit.zen.modules.Module;
import shit.zen.utils.render.TextureUtil;
import shit.zen.value.ModeValueGroup;
import shit.zen.value.ToggleValueGroup;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;
import shit.zen.value.ValueJsonCodec;

public class SettingsHandler extends AbstractHttpHandler {

    @Override
    public int handleRequest(InputStream in, OutputStream out, HttpExchange exchange) throws Throwable {
        Map<String, String> query = TextureUtil.parseQueryString(exchange.getRequestURI().getQuery());
        Map<String, Object> response = new HashMap<>();
        boolean success = false;
        String reason = null;
        if (query.containsKey("module")) {
            try {
                Module module = lookupModule(query.get("module"));
                if (module == null) {
                    reason = "module not found";
                } else {
                    List<Map<String, Object>> entries = new ArrayList<>();
                    for (Value<?> value : module.getValueTree().getChildren()) {
                        entries.add(toEntry(value));
                    }
                    response.put("result", entries);
                    success = true;
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                success = false;
                reason = throwable.toString();
            }
        } else {
            reason = "missing module";
        }
        response.put("success", success);
        response.put("reason", reason);
        out.write(new Gson().toJson(response).getBytes(StandardCharsets.UTF_8));
        return 200;
    }

    private static Module lookupModule(String name) {
        try {
            return ZenClient.getInstance().getModuleManager().getModule(name);
        } catch (ModuleNotFoundException e) {
            return null;
        }
    }

    private static Map<String, Object> toEntry(Value<?> value) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("id", value.getId());
        entry.put("path", value.getPath());
        entry.put("name", value.getPath());
        entry.put("displayName", value.getDisplayName());
        entry.put("description", value.getDescription());
        entry.put("type", ValueJsonCodec.typeName(value.getType()));
        entry.put("metadata", value.getMetadata());
        entry.put("visible", value.isVisible());
        if (value instanceof ToggleValueGroup toggleValueGroup) {
            entry.put("enabled", toggleValueGroup.isEnabled());
        } else if (value instanceof ModeValueGroup modeValueGroup) {
            entry.put("mode", modeValueGroup.getActiveModeId());
        } else if (!(value instanceof ValueGroup)) {
            entry.put("value", ValueJsonCodec.writeValue(value));
        }
        if (value instanceof ValueGroup group) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (Value<?> child : group.getChildren()) {
                children.add(toEntry(child));
            }
            entry.put("children", children);
        }
        return entry;
    }
}
