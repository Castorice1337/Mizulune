package shit.zen.modules.impl.misc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import shit.zen.ZenClient;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;

/**
 * WindowTitle — replaces the Minecraft window title with a customisable format:
 *   [poem / custom text] | Mizulune | [display name] | [date-time]
 * Each segment can be toggled independently through the ClickGUI (Misc category).
 */
public class WindowTitle extends Module {

    /* ───────── 40 句古代 / 近现代中文小情诗 ───────── */
    private static final String[] POEMS = {
        // 古诗词
        "两情若是久长时，又岂在朝朝暮暮",
        "曾经沧海难为水，除却巫山不是云",
        "在天愿作比翼鸟，在地愿为连理枝",
        "身无彩凤双飞翼，心有灵犀一点通",
        "春蚕到死丝方尽，蜡炬成灰泪始干",
        "玲珑骰子安红豆，入骨相思知不知",
        "愿得一心人，白首不相离",
        "山有木兮木有枝，心悦君兮君不知",
        "死生契阔，与子成说；执子之手，与子偕老",
        "一日不见兮，思之如狂",
        "人生若只如初见，何事秋风悲画扇",
        "天涯地角有穷时，只有相思无尽处",
        "相思相见知何日，此时此夜难为情",
        "入我相思门，知我相思苦",
        "衣带渐宽终不悔，为伊消得人憔悴",
        "花自飘零水自流，一种相思，两处闲愁",
        "问世间情为何物，直教生死相许",
        "只愿君心似我心，定不负相思意",
        "月上柳梢头，人约黄昏后",
        "十年生死两茫茫，不思量，自难忘",
        "此情可待成追忆，只是当时已惘然",
        "一生一代一双人，争教两处销魂",
        "思君如满月，夜夜减清辉",
        "直道相思了无益，未妨惆怅是清狂",
        "若似月轮终皎洁，不辞冰雪为卿热",
        "取次花丛懒回顾，半缘修道半缘君",
        "落花人独立，微雨燕双飞",
        "酒入愁肠，化作相思泪",
        "平生不会相思，才会相思，便害相思",
        "一寸相思千万绪，人间没个安排处",
        // 近现代情诗
        "你是人间四月天",
        "我行过许多地方的桥，看过许多次数的云",
        "醉过才知酒浓，爱过才知情重",
        "从前的日色变得慢，一生只够爱一个人",
        "你来人间一趟，你要看看太阳",
        "答案很长，我准备用一生的时间来回答",
        "我明白你会来，所以我等",
        "我是天空里的一片云，偶尔投影在你的波心",
        "你一定要走吗？等一等，我也去",
        "今晚月色真美",
    };

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /* ───────── 设置项 ───────── */
    private Value<String>  customText;
    private Value<Boolean> showPoem;
    private Value<Boolean> showClientName;
    private Value<Boolean> showDisplayName;
    private Value<Boolean> showDateTime;

    /* ───────── 运行时 ───────── */
    private String currentPoem;
    private String lastAppliedTitle;
    private int tickCounter;
    private int displayNameRefreshCounter;

    public WindowTitle() {
        super("WindowTitle", Category.MISC);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup display = root.group("display", "Display");
        this.customText     = display.text("custom_text", "Custom Text", "");
        this.showPoem       = display.bool("show_poem", "Show Poem", true);
        this.showClientName = display.bool("show_client_name", "Show Client Name", true);
        this.showDisplayName= display.bool("show_display_name", "Show Display Name", true);
        this.showDateTime   = display.bool("show_date_time", "Show DateTime", true);
    }

    @Override
    protected boolean defaultHiddenInModuleList() {
        return true;
    }

    @Override
    protected void onEnable() {
        ZenClient.refreshDisplayName();
        this.currentPoem = pickRandomPoem();
        this.lastAppliedTitle = null;
        this.tickCounter = 0;
        this.displayNameRefreshCounter = 0;
    }

    @Override
    protected void onDisable() {
        // Let the active loader rebuild its own title; a Forge literal is wrong on Fabric and
        // also becomes stale when the loader/version profile changes.
        if (mc != null) mc.updateTitle();
        this.lastAppliedTitle = null;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc == null || mc.getWindow() == null) {
            return;
        }

        // Refresh the poem every ~2 minutes (2400 ticks) to keep it interesting
        this.tickCounter++;
        if (this.tickCounter >= 2400) {
            this.tickCounter = 0;
            this.currentPoem = pickRandomPoem();
        }

        // The launcher and the client share ~/.mizulune/loader-profile.properties.
        // Poll it once per second so changing the launcher display name updates
        // the title without requiring reinjection or a client restart.
        this.displayNameRefreshCounter++;
        if (this.displayNameRefreshCounter >= 20) {
            this.displayNameRefreshCounter = 0;
            ZenClient.refreshDisplayName();
        }

        String title = buildTitle();
        if (!title.equals(this.lastAppliedTitle)) {
            mc.getWindow().setTitle(title);
            this.lastAppliedTitle = title;
        }
    }

    /* ───────── 标题拼装 ───────── */

    private String buildTitle() {
        StringBuilder sb = new StringBuilder();

        // 1. 诗句 / 自定义文本
        String custom = this.customText != null ? this.customText.getValue() : "";
        if (custom != null && !custom.trim().isEmpty()) {
            sb.append(custom.trim());
        } else if (Boolean.TRUE.equals(this.showPoem != null ? this.showPoem.getValue() : true)) {
            sb.append(this.currentPoem != null ? this.currentPoem : pickRandomPoem());
        }

        // 2. Mizulune
        if (Boolean.TRUE.equals(this.showClientName != null ? this.showClientName.getValue() : true)) {
            appendSeparator(sb);
            sb.append(ZenClient.CLIENT_SHORT_NAME);
        }

        // 3. 显示名称
        if (Boolean.TRUE.equals(this.showDisplayName != null ? this.showDisplayName.getValue() : true)) {
            String name = ZenClient.username;
            if (name != null && !name.isEmpty()) {
                appendSeparator(sb);
                sb.append(name);
            }
        }

        // 4. 日期时间
        if (Boolean.TRUE.equals(this.showDateTime != null ? this.showDateTime.getValue() : true)) {
            appendSeparator(sb);
            sb.append(LocalDateTime.now().format(TIME_FMT));
        }

        // Fallback: never return a blank title
        if (sb.isEmpty()) {
            sb.append(ZenClient.CLIENT_NAME);
        }

        return sb.toString();
    }

    private static void appendSeparator(StringBuilder sb) {
        if (!sb.isEmpty()) {
            sb.append(" | ");
        }
    }

    private static String pickRandomPoem() {
        return POEMS[new Random().nextInt(POEMS.length)];
    }
}
