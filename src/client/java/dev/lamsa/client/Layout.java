package dev.lamsa.client;

import java.util.ArrayList;
import java.util.List;

/** تعريف الأزرار وأماكنها الافتراضية (نسب من الشاشة، لذلك تناسب أي دقة). */
public final class Layout {
    public enum Kind { HOLD, TAP, TOGGLE, SPECIAL }

    public record Def(String id, String icon, String label, float fx, float fy, float size, Kind kind, String target) {}

    public record TrayItem(String id, String icon, String label) {}

    private static final List<Def> BASE = List.of(
        new Def("stick",   "move",   "عصا الحركة", 0.14f, 0.74f, 30, Kind.SPECIAL, null),
        new Def("jump",    "up",     "قفز",        0.91f, 0.70f, 34, Kind.HOLD, null),
        new Def("sneak",   "down",   "تسلل",       0.91f, 0.89f, 26, Kind.TOGGLE, null),
        new Def("attack",  "sword",  "هجوم / كسر", 0.80f, 0.74f, 32, Kind.HOLD, null),
        new Def("use",     "hand",   "استخدام / وضع", 0.74f, 0.88f, 28, Kind.HOLD, null),
        new Def("sprint",  "run",    "ركض",        0.27f, 0.58f, 22, Kind.TOGGLE, null),
        new Def("fly",     "wing",   "طيران",      0.91f, 0.50f, 22, Kind.TOGGLE, null),
        new Def("lock",    "lock",   "تثبيت",      0.82f, 0.50f, 22, Kind.TOGGLE, null),
        new Def("ability", "star",   "قدرة الكائن", 0.66f, 0.62f, 26, Kind.TAP, null),
        new Def("swap",    "swap",   "F تبديل اليد", 0.235f, 0.91f, 20, Kind.TAP, null),
        new Def("drop",    "drop",   "رمي",        0.285f, 0.91f, 20, Kind.TAP, null),
        new Def("slot1",   "block",  "1",          0.335f, 0.91f, 20, Kind.TAP, null),
        new Def("f3",      "term",   "F3",         0.385f, 0.91f, 20, Kind.TAP, null),
        new Def("f4",      "star",   "F4",         0.435f, 0.91f, 20, Kind.TAP, null),
        new Def("f11",     "expand", "F11",        0.485f, 0.91f, 20, Kind.TAP, null),
        new Def("inv",     "chest",  "الحقيبة",    0.70f, 0.07f, 20, Kind.TAP, null),
        new Def("chat",    "chat",   "الدردشة",    0.76f, 0.07f, 20, Kind.TAP, null),
        new Def("persp",   "camera", "الكاميرا",   0.82f, 0.07f, 20, Kind.TAP, null),
        new Def("pause",   "pause",  "إيقاف مؤقت", 0.88f, 0.07f, 20, Kind.TAP, null),
        new Def("tray",    "menu",   "المزيد",     0.94f, 0.07f, 20, Kind.TOGGLE, null),
        new Def("hide",    "eye",    "إخفاء",      0.985f, 0.07f, 12, Kind.SPECIAL, null)
    );

    public static final List<TrayItem> TRAY = List.of(
        new TrayItem("mods",     "puzzle",  "أزرار المودات"),
        new TrayItem("morph",    "paw",     "التحول"),
        new TrayItem("pickmorph","pick",    "التقاط ما أنظر إليه"),
        new TrayItem("unmorph",  "x",       "العودة لطبيعتي"),
        new TrayItem("cmds",     "term",    "الأوامر"),
        new TrayItem("settings", "gear",    "الإعدادات"),
        new TrayItem("edit",     "move",    "تحريك الأزرار"),
        new TrayItem("full",     "expand",  "ملء الشاشة"),
        new TrayItem("hand",     "swap",    "يمين / يسار"),
        new TrayItem("shot",     "camera",  "لقطة شاشة"),
        new TrayItem("stop",     "power",   "إيقاف المود")
    );

    public static List<Def> all() {
        List<Def> l = new ArrayList<>(BASE);
        for (Cfg.Custom c : Cfg.I.custom) {
            boolean folder = "folder".equals(c.kind);
            l.add(new Def(c.id, folder ? "folder" : "star", c.label, 0.5f, 0.3f, 24,
                folder ? Kind.TAP : Kind.HOLD, c.target));
        }
        return l;
    }

    public static Def byId(String id) {
        for (Def d : all()) if (d.id().equals(id)) return d;
        return null;
    }

    public static Cfg.Pos pos(Def d) {
        return Cfg.I.pos.computeIfAbsent(d.id(), k -> new Cfg.Pos(d.fx(), d.fy()));
    }

    public static float cx(Def d, int w) {
        float fx = pos(d).x;
        if (Cfg.I.leftHanded) fx = 1f - fx;
        return fx * w;
    }

    public static float cy(Def d, int h) {
        return pos(d).y * h;
    }

    /** نصف حجم الزر بإحداثيات الواجهة. */
    public static float half(Def d) {
        float base = d.size() * Cfg.I.scale * pos(d).s;
        return base / 2f;
    }

    public static void resetPositions() {
        Cfg.I.pos.clear();
        Cfg.save();
    }

    public static void addCustom(String kind, String target, String label) {
        for (Cfg.Custom c : Cfg.I.custom) if (c.kind.equals(kind) && c.target.equals(target)) { Hud.toast("الزر موجود مسبقاً"); return; }
        Cfg.Custom c = new Cfg.Custom();
        c.id = "c" + System.currentTimeMillis();
        c.kind = kind;
        c.target = target;
        c.label = label;
        Cfg.I.custom.add(c);
        float off = (Cfg.I.custom.size() % 6) * 0.045f;
        Cfg.I.pos.put(c.id, new Cfg.Pos(0.40f + off, 0.30f + off));
        Cfg.save();
        Hud.toast("تمت إضافة زر: " + label);
    }

    public static void removeCustom(String id) {
        Cfg.I.custom.removeIf(c -> c.id.equals(id));
        Cfg.I.pos.remove(id);
        Cfg.save();
    }
}
