package dev.lamsa.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** شريط صغير فوق كل القوائم: تشغيل/إيقاف المود، إغلاق، تمرير، Shift، ونقر يمين. */
public final class Toolbar {
    private Toolbar() {}

    public static boolean shift, right;
    static final float SZ = 22, GAP = 3, X0 = 3, Y0 = 3;

    static String[] ids(Screen s) {
        if (s instanceof PanelScreen) return new String[0];
        if (!Cfg.I.enabled) return new String[]{"power"};
        return s.shouldCloseOnEsc()
            ? new String[]{"power", "close", "up", "down", "shift", "right"}
            : new String[]{"power", "up", "down", "shift", "right"};
    }

    static String icon(String id) {
        return switch (id) {
            case "power" -> "power";
            case "close" -> "x";
            case "up" -> "up";
            case "down" -> "down";
            case "shift" -> "up";
            default -> "hand";
        };
    }

    static String label(String id) {
        return switch (id) {
            case "power" -> Cfg.I.enabled ? "إيقاف" : "تشغيل اللمس";
            case "close" -> "إغلاق";
            case "up" -> "للأعلى";
            case "down" -> "للأسفل";
            case "shift" -> "Shift";
            default -> "نقر يمين";
        };
    }

    public static void render(GuiGraphics g, Screen s) {
        String[] ids = ids(s);
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            float x = X0 + i * (SZ + GAP + 6);
            boolean on = (id.equals("shift") && shift) || (id.equals("right") && right);
            boolean off = id.equals("power") && !Cfg.I.enabled;
            float a = Cfg.I.enabled ? 0.92f : 0.7f;
            Ui.button(g, x, Y0, SZ + 6, SZ, false, false, on || off, a);
            if (id.equals("shift")) {
                Ui.text(g, "Shift", x + (SZ + 6) / 2f, Y0 + 7, Ui.TEXT, 0.75f, true);
            } else {
                Ui.icon(g, icon(id), x + (SZ + 6) / 2f, Y0 + SZ / 2f, SZ * 0.5f, Ui.TEXT);
            }
            if (!id.equals("shift")) {
                String lb = label(id);
                float sc = Math.min(0.6f, (SZ + 6) / Math.max(1, Ui.tw(lb)));
                Ui.text(g, lb, x + (SZ + 6) / 2f, Y0 + SZ + 1, Ui.al(0xFFFFFF, 0.85f), sc, true);
            }
        }
        Hud.drawToast(g, g.guiWidth(), g.guiHeight());
    }

    /** يعيد true إذا استُهلكت اللمسة. */
    public static boolean click(double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        if (s == null) return false;
        String[] ids = ids(s);
        for (int i = 0; i < ids.length; i++) {
            float bx = X0 + i * (SZ + GAP + 6);
            if (Ui.in(x, y, bx, Y0, SZ + 6, SZ + 8)) {
                run(ids[i], s);
                return true;
            }
        }
        return false;
    }

    static void run(String id, Screen s) {
        switch (id) {
            case "power" -> Touch.setEnabled(!Cfg.I.enabled);
            case "close" -> s.onClose();
            case "up", "down" -> {
                double dir = id.equals("up") ? 1 : -1;
                double x = Touch.lastScreenX >= 0 ? Touch.lastScreenX : s.width / 2.0;
                double y = Touch.lastScreenY >= 0 ? Touch.lastScreenY : s.height / 2.0;
                s.mouseScrolled(x, y, 0, dir);
            }
            case "shift" -> { shift = !shift; Hud.toast(shift ? "Shift مفعّل: النقر ينقل العناصر بسرعة" : "تم إلغاء Shift"); }
            case "right" -> { right = !right; Hud.toast(right ? "النقر الآن = نقر يمين (تقسيم/وضع واحد)" : "النقر الآن = نقر يسار"); }
            default -> {}
        }
    }
}
