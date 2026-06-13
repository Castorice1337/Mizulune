package shit.zen.network.webui;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import shit.zen.ZenClient;
import shit.zen.exception.ModuleNotFoundException;
import shit.zen.manager.ConfigManager;
import shit.zen.modules.Module;
import shit.zen.modules.impl.world.WebUI;
import shit.zen.settings.Setting;
import shit.zen.settings.impl.BooleanSetting;
import shit.zen.settings.impl.ModeSetting;
import shit.zen.settings.impl.NumberSetting;
import shit.zen.utils.render.TextureUtil;
import shit.zen.value.Value;
import shit.zen.value.ValueJsonCodec;

public class SetSettingHandler extends AbstractHttpHandler {

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public int handleRequest(InputStream in, OutputStream out, HttpExchange exchange) throws Throwable {
        Map<String, String> query = TextureUtil.parseQueryString(exchange.getRequestURI().getQuery());
        Map<String, Object> response = new HashMap<>();
        boolean success = false;
        String reason = null;
        Object result = null;
        if (query.containsKey("module") && (query.containsKey("path") || query.containsKey("name")) && query.containsKey("value")) {
            try {
                Module module = lookupModule(query.get("module"));
                if (module == null) {
                    reason = "module not found";
                } else if (module instanceof WebUI) {
                    reason = "webui module settings are locked";
                } else {
                    String key = query.containsKey("path") ? query.get("path") : query.get("name");
                    Value<?> value = module.findValue(key);
                    if (value != null && ValueJsonCodec.readStringInto(value, query.get("value"), ConfigManager.LOGGER, value.getPath())) {
                        success = true;
                        result = ValueJsonCodec.writeValue(value);
                    } else {
                        Optional<Setting<?>> match = module.getSettings().stream()
                                .filter(setting -> setting.getName().equals(key))
                                .findFirst();
                        if (match.isEmpty()) {
                            reason = "setting not found";
                        } else {
                            Setting setting = match.get();
                            String raw = query.get("value");
                            if (setting instanceof NumberSetting) {
                                try {
                                    setting.setValue(Double.valueOf(raw));
                                    success = true;
                                } catch (NumberFormatException ignored) {
                                }
                            } else if (setting instanceof BooleanSetting) {
                                setting.setValue(Boolean.valueOf(raw));
                                success = true;
                            } else if (setting instanceof ModeSetting modeSetting) {
                                String matchedMode = Stream.of(modeSetting.getModes())
                                        .filter(mode -> mode.equals(raw))
                                        .findFirst()
                                        .orElse(null);
                                if (matchedMode != null) {
                                    modeSetting.setValue(matchedMode);
                                    success = true;
                                }
                            }
                            if (success) {
                                result = setting.getValue();
                            } else {
                                reason = "invalid value";
                            }
                        }
                    }
                    if (success) {
                        ConfigManager.saveAllIfReady();
                    }
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                success = false;
                reason = throwable.toString();
            }
        } else {
            result = false;
            reason = "missing module/path/value";
        }
        response.put("success", success);
        response.put("reason", reason);
        response.put("result", result);
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
}
