package dev.lamsa.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * يفحص إعدادات الأزرار (Controls) ويعرض كل مود في قائمة منسدلة؛ اضغط على المود
 * لتظهر كل أزراره، واضغط على أي زر لتنفيذه، أو ثبّته كزر عائم على الشاشة.
 */
public class ModsScreen extends PanelScreen {
    record Row(int type, String text, String sub, KeyMapping km, String ns) {}

    private final String only;
    private final Set<String> open = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private int totalKeys, totalMods;

    public ModsScreen(String onlyNamespace) {
        super(onlyNamespace == null ? "أزرار المودات (تم الفحص تلقائياً)" : "أزرار: " + modName(onlyNamespace));
        this.only = onlyNamespace;
        if (onlyNamespace != null) open.add(onlyNamespace);
    }

    static String modName(String ns) {
        if (ns.equals("minecraft")) return "ماين كرافت الأساسي";
        return FabricLoader.getInstance().getModContainer(ns)
            .map(c -> c.getMetadata().getName()).orElse(ns);
    }

    @Override
    protected void init() {
        rebuild();
        super.init();
    }

    private void rebuild() {
        rows.clear();
        Minecraft mc = Minecraft.getInstance();
        Map<String, List<KeyMapping>> byMod = new LinkedHashMap<>();
        totalKeys = 0;
        for (KeyMapping km : mc.options.keyMappings) {
            String ns;
            try { ns = km.getCategory().id().getNamespace(); } catch (Throwable t) { ns = "minecraft"; }
            byMod.computeIfAbsent(ns, k -> new ArrayList<>()).add(km);
            totalKeys++;
        }
        totalMods = byMod.size();
        List<String> names = new ArrayList<>(byMod.keySet());
        names.sort(Comparator.comparing((String n) -> n.equals("minecraft") ? 0 : 1).thenComparing(ModsScreen::modName, String.CASE_INSENSITIVE_ORDER));
        if (only == null) rows.add(new Row(3, "تم فحص " + totalKeys + " زر من " + totalMods + " مود. اضغط على أي مود لعرض أزراره.", "", null, ""));
        for (String ns : names) {
            if (only != null && !only.equals(ns)) continue;
            List<KeyMapping> l = byMod.get(ns);
            rows.add(new Row(0, modName(ns), String.valueOf(l.size()), null, ns));
            if (!open.contains(ns)) continue;
            l.sort(Comparator.comparing((KeyMapping k) -> k.getCategory().id().toString()).thenComparing(KeyMapping::getName));
            String lastCat = "";
            for (KeyMapping km : l) {
                String cat = km.getCategory().id().toString();
                if (!cat.equals(lastCat)) {
                    lastCat = cat;
                    rows.add(new Row(2, km.getCategory().label().getString(), "", null, ns));
                }
                String nm = Component.translatable(km.getName()).getString();
                String key = km.isUnbound() ? "—" : km.getTranslatedKeyMessage().getString();
                rows.add(new Row(1, nm, key, km, ns));
            }
        }
    }

    @Override protected int count() { return rows.size(); }

    @Override
    protected void drawItem(GuiGraphics g, int i, float x, float y, float w, float h, boolean hot) {
        Row r = rows.get(i);
        switch (r.type) {
            case 0 -> {
                boolean on = open.contains(r.ns);
                Ui.button(g, x, y, w, h, hot, hot, on, 1f);
                Ui.icon(g, on ? "down" : "arrow", x + 11, y + h / 2f, 9, Ui.TEXT);
                float sc = Math.min(1f, (w - 90) / Math.max(1, Ui.tw(r.text)));
                Ui.text(g, r.text, x + 24, y + h / 2f - 4 * sc, Ui.TEXT, sc, false);
                Ui.text(g, "(" + r.sub + ")", x + w - 62, y + h / 2f - 4, Ui.GOLD, 0.9f, true);
                Ui.icon(g, "folder", x + w - 24, y + h / 2f, 11, Ui.TEXT);
            }
            case 1 -> {
                Ui.bevel(g, x + 8, y, w - 8, h, hot ? 0xFF6A6B6D : Ui.PANEL_L, 0xFF6A6B6D, Ui.PANEL_D, 1f);
                float sc = Math.min(0.95f, (w - 120) / Math.max(1, Ui.tw(r.text)));
                Ui.text(g, r.text, x + 14, y + h / 2f - 4 * sc, Ui.TEXT, sc, false);
                float sc2 = Math.min(0.8f, 52f / Math.max(1, Ui.tw(r.sub)));
                Ui.text(g, r.sub, x + w - 62, y + h / 2f - 4 * sc2, Ui.GRAY, sc2, true, false);
                Ui.bevel(g, x + w - 26, y + 3, 20, h - 6, Ui.FACE, Ui.LIGHT, Ui.DARK, 1f);
                Ui.icon(g, "pin", x + w - 16, y + h / 2f, 10, Ui.TEXT);
            }
            case 2 -> Ui.text(g, "◆ " + r.text, x + 10, y + h / 2f - 3, Ui.GOLD, 0.8f, false);
            default -> {
                float sc = Math.min(0.9f, (w - 10) / Math.max(1, Ui.tw(r.text)));
                Ui.text(g, r.text, x + w / 2f, y + h / 2f - 4 * sc, Ui.GRAY, sc, true, false);
            }
        }
    }

    @Override
    protected void activate(int i, double x, double y) {
        Row r = rows.get(i);
        boolean pinZone = x > cx1 - 36;
        if (r.type == 0) {
            if (pinZone && only == null) {
                Layout.addCustom("folder", r.ns, r.text);
            } else if (only == null) {
                if (!open.remove(r.ns)) open.add(r.ns);
                rebuild();
                relayout();
            }
        } else if (r.type == 1) {
            if (pinZone) {
                Layout.addCustom("key", r.km.getName(), r.text);
            } else {
                KeyMapping km = r.km;
                if (Cfg.I.keepOpen) {
                    Touch.tap(km);
                } else {
                    onClose();
                    Touch.runLater(2, () -> Touch.tap(km));
                }
            }
        }
    }

    @Override protected int footerH() { return 14; }

    @Override
    protected void drawFooter(GuiGraphics g, float x, float y, float w, int hot) {
        Ui.text(g, "اضغط = تنفيذ • أيقونة الدبوس = زر عائم • أيقونة المجلد = زر يفتح كل أزرار المود", x + w / 2f, y + 2,
            Ui.GRAY, Math.min(0.75f, w / Math.max(1, Ui.tw("اضغط = تنفيذ • أيقونة الدبوس = زر عائم • أيقونة المجلد = زر يفتح كل أزرار المود"))), true, false);
    }
}
