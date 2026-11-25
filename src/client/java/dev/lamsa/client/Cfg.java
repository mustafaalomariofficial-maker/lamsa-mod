package dev.lamsa.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** إعدادات المود (تُحفظ في config/lamsa.json). */
public final class Cfg {
    public static Cfg I = new Cfg();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public boolean leftHanded = false;
    public boolean hidden = false;
    public boolean floatingStick = true;
    public boolean nativeTouch = true;
    /** أُلغي افتراضيًا: كان يقفل العصا عند أقصى دفع ويسبب التصاق الحركة. */
    public boolean autoSprint = false;
    public boolean sneakToggle = true;
    public boolean keepOpen = false;
    public boolean labels = true;
    public float scale = 1.0f;
    public float opacity = 0.8f;
    public float look = 1.0f;
    /** تكبير نصوص قوائم المود لتناسب البث على الجوال. */
    public float uiText = 1.15f;
    /** 0 = لا تغيّر حجم واجهة اللعبة. */
    public int guiScale = 0;
    public int prevGuiScale = -1;
    public boolean morphFull = true;

    public Map<String, Pos> pos = new LinkedHashMap<>();
    public List<Custom> custom = new ArrayList<>();

    public static class Pos {
        public float x, y, s = 1f;
        public boolean vis = true;
        public Pos() {}
        public Pos(float x, float y) { this.x = x; this.y = y; }
    }

    public static class Custom {
        public String id, kind, target, label;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("lamsa.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f)) {
                    Cfg c = GSON.fromJson(r, Cfg.class);
                    if (c != null) I = c;
                }
            }
        } catch (Throwable t) {
            System.err.println("[Lamsa] config load failed: " + t);
        }
        if (I.pos == null) I.pos = new LinkedHashMap<>();
        if (I.custom == null) I.custom = new ArrayList<>();
        if (I.uiText < 1.0f) I.uiText = 1.15f;
    }

    public static void save() {
        try {
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(I, w);
            }
        } catch (Throwable t) {
            System.err.println("[Lamsa] config save failed: " + t);
        }
    }
}
