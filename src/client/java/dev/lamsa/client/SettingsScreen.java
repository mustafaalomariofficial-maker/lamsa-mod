package dev.lamsa.client;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** إعدادات المود: كلها أزرار كبيرة مناسبة للمس. */
public class SettingsScreen extends PanelScreen {
    abstract static class Row {
        final String label;
        Row(String l) { label = l; }
    }
    static final class Toggle extends Row {
        final BooleanSupplier get; final Consumer<Boolean> set;
        Toggle(String l, BooleanSupplier g, Consumer<Boolean> s) { super(l); get = g; set = s; }
    }
    static final class Step extends Row {
        final Supplier<String> val; final Runnable dec, inc;
        Step(String l, Supplier<String> v, Runnable d, Runnable i) { super(l); val = v; dec = d; inc = i; }
    }
    static final class Act extends Row {
        final Runnable run; final boolean danger;
        Act(String l, Runnable r, boolean d) { super(l); run = r; danger = d; }
    }
    static final class Info extends Row {
        final Supplier<String> txt;
        Info(Supplier<String> t) { super(""); txt = t; }
    }

    private final List<Row> rows = new ArrayList<>();

    private static float clampF(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, Math.round(v * 10f) / 10f)); }

    public SettingsScreen() {
        super("إعدادات لمسة");
        Cfg c = Cfg.I;
        rows.add(new Info(() -> NativeTouch.status()));
        rows.add(new Step("حجم الأزرار", () -> (int) (c.scale * 100) + "%", () -> { c.scale = clampF(c.scale - 0.1f, 0.6f, 1.8f); Cfg.save(); }, () -> { c.scale = clampF(c.scale + 0.1f, 0.6f, 1.8f); Cfg.save(); }));
        rows.add(new Step("شفافية الأزرار", () -> (int) (c.opacity * 100) + "%", () -> { c.opacity = clampF(c.opacity - 0.1f, 0.2f, 1f); Cfg.save(); }, () -> { c.opacity = clampF(c.opacity + 0.1f, 0.2f, 1f); Cfg.save(); }));
        rows.add(new Step("حساسية تحريك الكاميرا", () -> String.format("%.1f", c.look), () -> { c.look = clampF(c.look - 0.1f, 0.2f, 4f); Cfg.save(); }, () -> { c.look = clampF(c.look + 0.1f, 0.2f, 4f); Cfg.save(); }));
        rows.add(new Step("حجم واجهة اللعبة (تكبير الأيقونات)", () -> c.guiScale == 0 ? "تلقائي" : String.valueOf(c.guiScale),
            () -> { c.guiScale = Math.max(0, c.guiScale - 1); Cfg.save(); Touch.applyGuiScale(); },
            () -> { c.guiScale = Math.min(6, c.guiScale + 1); Cfg.save(); Touch.applyGuiScale(); }));
        rows.add(new Toggle("وضع اليد اليسرى", () -> c.leftHanded, v -> { c.leftHanded = v; Cfg.save(); }));
        rows.add(new Toggle("عصا حركة عائمة (تظهر تحت إصبعك)", () -> c.floatingStick, v -> { c.floatingStick = v; Cfg.save(); }));
        rows.add(new Toggle("ركض تلقائي عند دفع العصا للنهاية", () -> c.autoSprint, v -> { c.autoSprint = v; Cfg.save(); }));
        rows.add(new Toggle("التسلل بالتبديل (ضغطة تشغّل وضغطة توقف)", () -> c.sneakToggle, v -> { c.sneakToggle = v; Cfg.save(); }));
        rows.add(new Toggle("لمس متعدد الأصابع الأصلي (ويندوز)", () -> c.nativeTouch, v -> { c.nativeTouch = v; Cfg.save(); NativeTouch.setActive(c.enabled && v); }));
        rows.add(new Toggle("إبقاء قائمة المودات مفتوحة بعد التنفيذ", () -> c.keepOpen, v -> { c.keepOpen = v; Cfg.save(); }));
        rows.add(new Toggle("إظهار أسماء الأزرار", () -> c.labels, v -> { c.labels = v; Cfg.save(); }));
        rows.add(new Toggle("التحول الكامل (الشكل والحجم) بدل القدرات فقط", () -> c.morphFull, v -> { c.morphFull = v; Cfg.save(); }));
        rows.add(new Act("تحريك وتعديل أماكن الأزرار", () -> { onClose(); Touch.editMode = true; Touch.selected = null; }, false));
        rows.add(new Act("إعادة الأزرار لأماكنها الافتراضية", () -> { Layout.resetPositions(); Hud.toast("تمت إعادة ضبط الأزرار"); }, false));
        rows.add(new Act("حذف كل الأزرار التي أضفتها", () -> { new ArrayList<>(c.custom).forEach(x -> Layout.removeCustom(x.id)); Hud.toast("تم حذف الأزرار المضافة"); }, false));
        rows.add(new Act("إيقاف المود وإرجاع اللعبة طبيعية", () -> { onClose(); Touch.setEnabled(false); }, true));
    }

    @Override protected int count() { return rows.size(); }

    @Override
    protected void drawItem(GuiGraphics g, int i, float x, float y, float w, float h, boolean hot) {
        Row r = rows.get(i);
        if (r instanceof Info in) {
            Ui.text(g, in.txt.get(), x + w / 2f, y + h / 2f - 3, Ui.GOLD, Math.min(0.85f, (w - 8) / Math.max(1, Ui.tw(in.txt.get()))), true, false);
            return;
        }
        boolean act = r instanceof Act;
        Ui.button(g, x, y, w, h, hot, hot, act && !((Act) r).danger && hot, 1f);
        if (act && ((Act) r).danger) Ui.bevel(g, x, y, w, h, hot ? 0xFFB03A3A : 0xFF8A2B2B, 0xFFC45050, 0xFF4A1515, 1f);
        float labelW = r instanceof Act ? w - 12 : w - 104;
        float sc = Math.min(0.95f, labelW / Math.max(1, Ui.tw(r.label)));
        Ui.text(g, r.label, x + 7, y + h / 2f - 4 * sc, Ui.TEXT, sc, false);
        if (r instanceof Toggle t) {
            boolean on = t.get.getAsBoolean();
            Ui.bevel(g, x + w - 52, y + 3, 46, h - 6, on ? Ui.GREEN : Ui.PANEL_D, on ? Ui.GREEN_L : Ui.PANEL_L, on ? Ui.GREEN_D : 0xFF111111, 1f);
            Ui.text(g, on ? "مفعّل" : "معطّل", x + w - 29, y + h / 2f - 3, Ui.TEXT, 0.8f, true);
        } else if (r instanceof Step s) {
            Ui.button(g, x + w - 92, y + 3, 20, h - 6, false, false, false, 1f);
            Ui.icon(g, "minus", x + w - 82, y + h / 2f, 8, Ui.TEXT);
            Ui.text(g, s.val.get(), x + w - 51, y + h / 2f - 3, Ui.GOLD, 0.85f, true);
            Ui.button(g, x + w - 26, y + 3, 20, h - 6, false, false, false, 1f);
            Ui.icon(g, "plus", x + w - 16, y + h / 2f, 8, Ui.TEXT);
        }
    }

    @Override
    protected void activate(int i, double x, double y) {
        Row r = rows.get(i);
        if (r instanceof Toggle t) t.set.accept(!t.get.getAsBoolean());
        else if (r instanceof Step s) {
            if (x > cx1 - 56) s.inc.run();
            else if (x > cx1 - 100) s.dec.run();
        } else if (r instanceof Act a) a.run.run();
    }
}
