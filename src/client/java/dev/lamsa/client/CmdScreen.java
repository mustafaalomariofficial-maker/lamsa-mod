package dev.lamsa.client;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** أوامر جاهزة + كل أوامر اللعبة (تُقرأ من اللعبة نفسها) بدون الحاجة لكتابتها. */
public class CmdScreen extends PanelScreen {
    record Row(String label, String cmd, boolean direct, boolean header) {}
    private final List<Row> rows = new ArrayList<>();

    public CmdScreen() {
        super("الأوامر والدردشة");
        rows.add(new Row("مفضلة وسريعة", "", false, true));
        rows.add(new Row("فتح الدردشة للكتابة", "", false, false));
        add("وضع الإبداع", "gamemode creative");
        add("وضع البقاء", "gamemode survival");
        add("وضع المغامرة", "gamemode adventure");
        add("وضع المتفرج", "gamemode spectator");
        add("نهار", "time set day");
        add("ليل", "time set night");
        add("طقس صافي", "weather clear");
        add("مطر", "weather rain");
        add("رؤية ليلية دائمة", "effect give @s minecraft:night_vision infinite 0 true");
        add("شفاء كامل", "effect give @s minecraft:instant_health 1 10 true");
        add("إشباع الجوع", "effect give @s minecraft:saturation 1 10 true");
        add("قفزة 10 بلوكات للأعلى", "tp @s ~ ~10 ~");
        add("قتل نفسي", "kill @s");
        rows.add(new Row("كل أوامر اللعبة (تُقرأ تلقائياً)", "", false, true));
        try {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                List<CommandNode<?>> nodes = new ArrayList<>(conn.getCommands().getRoot().getChildren());
                nodes.sort(Comparator.comparing(CommandNode::getName));
                for (CommandNode<?> n : nodes) {
                    rows.add(new Row("/" + n.getName(), n.getName(), n.getCommand() != null, false));
                }
            }
        } catch (Throwable t) {
            // لا شيء
        }
    }

    private void add(String label, String cmd) { rows.add(new Row(label, cmd, true, false)); }

    @Override protected int count() { return rows.size(); }

    @Override
    protected void drawItem(GuiGraphics g, int i, float x, float y, float w, float h, boolean hot) {
        Row r = rows.get(i);
        if (r.header) {
            Ui.text(g, "◆ " + r.label, x + 6, y + h / 2f - 3, Ui.GOLD, 0.85f, false);
            return;
        }
        Ui.button(g, x, y, w, h, hot, hot, hot, 1f);
        float sc = Math.min(0.95f, (w - 90) / Math.max(1, Ui.tw(r.label)));
        Ui.text(g, r.label, x + 8, y + h / 2f - 4 * sc, Ui.TEXT, sc, false);
        if (!r.cmd.isEmpty() && !r.label.startsWith("/")) {
            String c = "/" + r.cmd;
            float s2 = Math.min(0.6f, 70f / Math.max(1, Ui.tw(c)));
            Ui.text(g, c, x + w - 14, y + h / 2f - 4 * s2, Ui.GRAY, s2, false, false);
        }
        Ui.icon(g, r.direct ? "arrow" : "chat", x + w - 14, y + h / 2f - (r.label.startsWith("/") ? 0 : -7), 8, r.direct ? Ui.GREEN_L : Ui.GOLD);
    }

    @Override
    protected void activate(int i, double x, double y) {
        Row r = rows.get(i);
        if (r.header) return;
        Minecraft mc = Minecraft.getInstance();
        if (r.label.startsWith("فتح الدردشة")) {
            mc.setScreen(new ChatScreen("", false));
        } else if (r.direct && mc.getConnection() != null) {
            mc.getConnection().sendCommand(r.cmd);
            onClose();
            Hud.toast("تم تنفيذ: /" + r.cmd);
        } else {
            mc.setScreen(new ChatScreen("/" + r.cmd + " ", false));
        }
    }
}
