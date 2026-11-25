package dev.lamsa.client;

import dev.lamsa.client.Layout.Def;
import dev.lamsa.client.Layout.TrayItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** رسم واجهة اللمس أثناء اللعب. */
public final class Hud {
    private Hud() {}

    static String toastMsg;
    static long toastUntil;

    public static void toast(String s) {
        toastMsg = s;
        toastUntil = System.currentTimeMillis() + 2200;
    }

    public static void drawToast(GuiGraphics g, int w, int h) {
        if (toastMsg == null) return;
        long left = toastUntil - System.currentTimeMillis();
        if (left <= 0) { toastMsg = null; return; }
        float a = Math.min(1f, left / 300f);
        int tw = Ui.tw(toastMsg);
        float bw = tw + 16, bh = 16;
        float x = (w - bw) / 2f, y = h * 0.16f;
        Ui.bevel(g, x, y, bw, bh, Ui.PANEL, Ui.PANEL_L, Ui.PANEL_D, a);
        Ui.text(g, toastMsg, w / 2f, y + 4, Ui.mulA(Ui.TEXT, a), 1f, true);
    }

    // ───────── القائمة السريعة ─────────
    static List<TrayItem> trayItems() {
        List<TrayItem> l = new ArrayList<>();
        for (TrayItem t : Layout.TRAY) {
            if (t.id().equals("unmorph") && !Morph.active()) continue;
            l.add(t);
        }
        return l;
    }

    static final int COLS = 4;
    static float cell() { return 38f * Math.min(1.25f, Math.max(0.9f, Cfg.I.scale)); }

    /** مستطيل القائمة السريعة {x,y,w,h}. */
    static float[] trayRect(int w, int h) {
        Def td = Layout.byId("tray");
        float cx = Layout.cx(td, w), cy = Layout.cy(td, h), half = Layout.half(td);
        int n = trayItems().size();
        int rows = (n + COLS - 1) / COLS;
        float c = cell();
        float pw = COLS * c + 8, ph = rows * c + 8;
        float x = cx > w / 2f ? Math.min(w - 4, cx + half + 4) - pw : Math.max(4, cx - half - 4);
        float y = Math.min(h - ph - 4, cy + half + 4);
        return new float[]{x, y, pw, ph};
    }

    static String trayAt(float px, float py, int w, int h) {
        float[] r = trayRect(w, h);
        if (!Ui.in(px, py, r[0], r[1], r[2], r[3])) return null;
        List<TrayItem> items = trayItems();
        float c = cell();
        for (int i = 0; i < items.size(); i++) {
            float x = r[0] + 4 + (i % COLS) * c, y = r[1] + 4 + (i / COLS) * c;
            if (Ui.in(px, py, x, y, c, c)) return items.get(i).id();
        }
        return "";
    }

    // ───────── شريط تعديل الأزرار ─────────
    static final String[] EDIT = {"تم", "تكبير +", "تصغير -", "إخفاء", "إعادة ضبط"};

    static float[] editBtn(int i, int w, int h) {
        float bw = 52, bh = 22, gap = 3;
        float total = EDIT.length * bw + (EDIT.length - 1) * gap;
        return new float[]{(w - total) / 2f + i * (bw + gap), h * 0.30f, bw, bh};
    }

    static int editBtnAt(float x, float y, int w, int h) {
        for (int i = 0; i < EDIT.length; i++) {
            float[] r = editBtn(i, w, h);
            if (Ui.in(x, y, r[0], r[1], r[2], r[3])) return i;
        }
        return -1;
    }

    // ───────── الرسم الرئيسي ─────────
    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!Cfg.I.enabled || mc.screen != null || mc.player == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        boolean edit = Touch.editMode;
        float op = edit ? 1f : Cfg.I.opacity;

        if (edit) g.fill(0, 0, w, h, 0x66000000);

        // العصا
        if (!Cfg.I.hidden || edit) drawStick(g, w, h, op, edit);

        for (Def d : Layout.all()) {
            if (d.id().equals("stick")) continue;
            if (!Touch.isShown(d)) continue;
            drawButton(g, d, w, h, edit);
        }

        if (Touch.trayOpen && !edit && !Cfg.I.hidden) drawTray(g, w, h);
        if (edit) drawEditBar(g, w, h);
        drawToast(g, w, h);
    }

    static void drawStick(GuiGraphics g, int w, int h, float op, boolean edit) {
        Def sd = Layout.byId("stick");
        boolean act = Touch.stickPtr != Integer.MIN_VALUE;
        float r = Touch.stickRadius();
        float cx, cy;
        if (act) { cx = Touch.stickCx; cy = Touch.stickCy; }
        else { cx = Layout.cx(sd, w); cy = Layout.cy(sd, h); }
        float a = act ? op : (edit ? 0.9f : op * 0.45f);
        if (!Layout.pos(sd).vis && !edit) return;
        Ui.circle(g, cx, cy, r, Ui.al(0x000000, 0.28f * (act ? 1.3f : 1f) * (a > 0 ? 1 : 0)));
        Ui.ring(g, cx, cy, r, 2, Ui.al(0xE0E0E0, a * 0.8f));
        // اتجاهات
        Ui.icon(g, "up", cx, cy - r * 0.72f, r * 0.26f, Ui.al(0xFFFFFF, a * 0.55f));
        Ui.icon(g, "down", cx, cy + r * 0.72f, r * 0.26f, Ui.al(0xFFFFFF, a * 0.55f));
        float kx = cx + Touch.knobX * r, ky = cy + Touch.knobY * r;
        float kr = r * 0.46f;
        Ui.circle(g, kx, ky, kr + 1.5f, Ui.al(0x1E1E1F, a));
        Ui.circle(g, kx, ky, kr, Ui.al(act ? 0xB1B2B5 : 0x8D8E90, Math.min(1f, a + 0.1f)));
        Ui.circle(g, kx, ky - kr * 0.15f, kr * 0.65f, Ui.al(0xD0D1D4, a * 0.5f));
        if (edit && "stick".equals(Touch.selected)) Ui.ring(g, cx, cy, r + 3, 2, Ui.GOLD);
    }

    static void drawButton(GuiGraphics g, Def d, int w, int h, boolean edit) {
        float cx = Layout.cx(d, w), cy = Layout.cy(d, h), half = Layout.half(d);
        boolean pressed = Touch.isPressed(d);
        Cfg.Pos pos = Layout.pos(d);
        float a = edit ? (pos.vis ? 1f : 0.35f) : Cfg.I.opacity;
        boolean hide = d.id().equals("hide");
        if (hide && !edit) a = Math.min(a, Cfg.I.hidden ? 0.55f : 0.4f);
        boolean green = pressed && (d.kind() == Layout.Kind.TOGGLE);
        float bx = cx - half, by = cy - half, sz = half * 2;
        Ui.button(g, bx, by, sz, sz, pressed, false, green, a);
        String icon = d.icon();
        if (d.id().equals("hide") && Cfg.I.hidden) icon = "eye";
        Ui.icon(g, icon, cx, cy - (pressed ? 0 : 0.5f), sz * 0.52f, Ui.al(pressed ? 0xFFE866 : 0xFFFFFF, Math.min(1f, a + 0.2f)));
        boolean showLabel = (Cfg.I.labels && sz >= 30) || d.id().startsWith("c") || edit;
        if (showLabel && !hide && d.label() != null) {
            float sc = Math.min(0.62f, (sz + 14) / Math.max(1, Ui.tw(d.label())));
            Ui.text(g, d.label(), cx, by + sz + 1.5f, Ui.al(0xFFFFFF, Math.min(1f, a + 0.1f)), sc, true);
        }
        if (edit && d.id().equals(Touch.selected)) {
            float t = (System.currentTimeMillis() % 800) / 800f;
            int c = Ui.GOLD;
            Ui.rect(g, bx - 3, by - 3, sz + 6, 2, c);
            Ui.rect(g, bx - 3, by + sz + 1, sz + 6, 2, c);
            Ui.rect(g, bx - 3, by - 3, 2, sz + 6, c);
            Ui.rect(g, bx + sz + 1, by - 3, 2, sz + 6, c);
        }
    }

    static void drawTray(GuiGraphics g, int w, int h) {
        float[] r = trayRect(w, h);
        Ui.panel(g, r[0], r[1], r[2], r[3]);
        List<TrayItem> items = trayItems();
        float c = cell();
        for (int i = 0; i < items.size(); i++) {
            TrayItem t = items.get(i);
            float x = r[0] + 4 + (i % COLS) * c, y = r[1] + 4 + (i / COLS) * c;
            boolean danger = t.id().equals("stop");
            Ui.bevel(g, x + 1, y + 1, c - 2, c - 2, danger ? 0xFF8A2B2B : Ui.PANEL_L, 0xFF6A6B6D, Ui.PANEL_D, 1f);
            Ui.icon(g, t.icon(), x + c / 2f, y + c * 0.38f, c * 0.38f, Ui.TEXT);
            float sc = Math.min(0.7f, (c - 4) / Math.max(1, Ui.tw(t.label())));
            Ui.text(g, t.label(), x + c / 2f, y + c * 0.72f, Ui.GRAY, sc, true, false);
        }
    }

    static void drawEditBar(GuiGraphics g, int w, int h) {
        Ui.text(g, "وضع تحريك الأزرار — اسحب أي زر إلى مكانه، واضغط عليه لتحديده", w / 2f, h * 0.30f - 14, Ui.GOLD, 0.9f, true);
        for (int i = 0; i < EDIT.length; i++) {
            float[] r = editBtn(i, w, h);
            String label = EDIT[i];
            if (i == 3 && Touch.selected != null) {
                if (Touch.selected.startsWith("c")) label = "حذف";
                else label = Layout.pos(Layout.byId(Touch.selected)).vis ? "إخفاء" : "إظهار";
            }
            Ui.button(g, r[0], r[1], r[2], r[3], false, false, i == 0, 1f);
            float sc = Math.min(0.9f, (r[2] - 6) / Math.max(1, Ui.tw(label)));
            Ui.text(g, label, r[0] + r[2] / 2f, r[1] + 7, Ui.TEXT, sc, true);
        }
    }
}
