package dev.lamsa.client;

import com.mojang.blaze3d.platform.Window;
import dev.lamsa.client.Layout.Def;
import dev.lamsa.client.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * مدير اللمس المركزي. يقبل عدداً غير محدود من الأصابع (عبر WM_TOUCH على ويندوز)
 * ويرجع تلقائياً للماوس (إصبع واحد) إذا لم يكن اللمس المتعدد متاحاً.
 * كل الإدخال يمر عبر KeyMapping العادية فيتوافق مع أي مود آخر.
 */
public final class Touch {
    private Touch() {}

    static final class Ptr {
        final int id;
        float x, y, sx, sy, lx, ly;
        long t0 = System.currentTimeMillis();
        String owner = "none";
        Def def;
        boolean moved, mining;
        Ptr(int id, float x, float y) { this.id = id; this.x = x; this.y = y; sx = x; sy = y; lx = x; ly = y; }
    }

    static final Map<Integer, Ptr> P = new LinkedHashMap<>();
    static final Set<KeyMapping> held = new HashSet<>();

    // حالة العصا
    public static int stickPtr = Integer.MIN_VALUE;
    public static float stickCx, stickCy, knobX, knobY;
    // حالات عامة
    public static boolean sneakOn, sprintOn, editMode, trayOpen, nativeSeen;
    public static String selected;
    static double mouseX, mouseY;
    static int screenPrimary = Integer.MIN_VALUE;
    static float lastScreenX = -1, lastScreenY = -1;
    static boolean wasScreen;

    static final class Rel { KeyMapping km; int ticks; Rel(KeyMapping k, int t) { km = k; ticks = t; } }
    static final List<Rel> rels = new ArrayList<>();
    static final List<Object[]> later = new ArrayList<>();

    static Minecraft mc() { return Minecraft.getInstance(); }

    // ───────────────────────── تحويل الإحداثيات ─────────────────────────
    static float gx(double px) {
        Window w = mc().getWindow();
        return (float) (px * w.getGuiScaledWidth() / (double) Math.max(1, w.getScreenWidth()));
    }

    static float gy(double py) {
        Window w = mc().getWindow();
        return (float) (py * w.getGuiScaledHeight() / (double) Math.max(1, w.getScreenHeight()));
    }

    static float guiToPx() {
        Window w = mc().getWindow();
        return w.getScreenWidth() / (float) Math.max(1, w.getGuiScaledWidth());
    }

    // ───────────────────────── أدوات المفاتيح ─────────────────────────
    public static void hold(KeyMapping km, boolean down) {
        if (km == null) return;
        if (down) {
            held.add(km);
            km.setDown(true);
        } else if (held.remove(km)) {
            km.setDown(false);
        }
    }

    /** نقرة واحدة على أي KeyMapping (تعمل مع أزرار المودات). */
    public static void tap(KeyMapping km) {
        if (km == null) return;
        KeyMappingAccessor a = (KeyMappingAccessor) (Object) km;
        a.lamsa$setClickCount(a.lamsa$getClickCount() + 1);
        km.setDown(true);
        rels.add(new Rel(km, 2));
    }

    public static void runLater(int ticks, Runnable r) {
        later.add(new Object[]{ticks, r});
    }

    // ───────────────────────── التفعيل / الإيقاف ─────────────────────────
    public static void setEnabled(boolean on) {
        Cfg.I.enabled = on;
        cancelAll();
        sneakOn = false;
        sprintOn = false;
        editMode = false;
        trayOpen = false;
        Cfg.save();
        NativeTouch.setActive(on && Cfg.I.nativeTouch);
        applyGuiScale();
        Minecraft m = mc();
        if (!on) {
            for (KeyMapping k : new ArrayList<>(held)) hold(k, false);
            if (m.screen == null && m.player != null && !m.mouseHandler.isMouseGrabbed()) m.mouseHandler.grabMouse();
            if (m.getWindow() != null && m.screen != null) {
                GLFW.glfwSetInputMode(m.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        }
        Hud.toast(on ? "تم تشغيل وضع اللمس" : "تم إيقاف المود - اللعبة رجعت طبيعية");
    }

    public static void applyGuiScale() {
        Minecraft m = mc();
        if (m.options == null) return;
        OptionInstance<Integer> o = m.options.guiScale();
        if (Cfg.I.enabled && Cfg.I.guiScale > 0) {
            if (Cfg.I.prevGuiScale < 0) Cfg.I.prevGuiScale = o.get();
            if (!o.get().equals(Cfg.I.guiScale)) o.set(Cfg.I.guiScale);
        } else if (Cfg.I.prevGuiScale >= 0) {
            o.set(Cfg.I.prevGuiScale);
            Cfg.I.prevGuiScale = -1;
        }
    }

    public static boolean wantFreeCursor() {
        Minecraft m = mc();
        return Cfg.I.enabled && m.screen == null;
    }

    public static void cancelAll() {
        for (Ptr p : new ArrayList<>(P.values())) {
            if (p.owner.equals("btn") && p.def != null) release(p.def);
            if (p.mining) hold(mc().options.keyAttack, false);
        }
        P.clear();
        stickPtr = Integer.MIN_VALUE;
        knobX = knobY = 0;
        screenPrimary = Integer.MIN_VALUE;
    }

    // ───────────────────────── مدخل الماوس (الاحتياطي) ─────────────────────────
    public static boolean mouseButton(boolean down) {
        Minecraft m = mc();
        if (!Cfg.I.enabled || m.screen != null || m.player == null || m.level == null) return false;
        if (down) {
            mouseX = m.mouseHandler.xpos();
            mouseY = m.mouseHandler.ypos();
            pointer(-1, 0, mouseX, mouseY);
            return true; // نمنع اللعبة من اعتبارها نقرة هجوم عادية
        }
        if (P.containsKey(-1)) pointer(-1, 2, mouseX, mouseY);
        return false; // حدث الرفع يمر للعبة دائماً حتى لا تبقى حالة الماوس معلّقة
    }

    public static void mouseMove(double x, double y) {
        mouseX = x;
        mouseY = y;
        if (P.containsKey(-1) && mc().screen == null) pointer(-1, 1, x, y);
    }

    // ───────────────────────── مدخل المؤشرات ─────────────────────────
    /** phase: 0 = ضغط، 1 = حركة، 2 = رفع. الإحداثيات بالبكسل الحقيقي لنافذة اللعبة. */
    public static void pointer(int id, int phase, double px, double py) {
        if (!Cfg.I.enabled) return;
        if (id >= 0) nativeSeen = true;
        float x = gx(px), y = gy(py);
        try {
            if (mc().screen != null) {
                screenPointer(id, phase, x, y, px, py);
                return;
            }
            if (mc().player == null) return;
            switch (phase) {
                case 0 -> down(id, x, y);
                case 1 -> move(id, x, y);
                default -> up(id, x, y);
            }
        } catch (Throwable t) {
            System.err.println("[Lamsa] pointer error: " + t);
            t.printStackTrace();
        }
    }

    // ───────────────────────── اللعب (بدون شاشة) ─────────────────────────
    static Def hitButton(float x, float y, int w, int h) {
        List<Def> all = Layout.all();
        for (int i = all.size() - 1; i >= 0; i--) {
            Def d = all.get(i);
            if (!isShown(d)) continue;
            if (d.id().equals("stick")) {
                if (!editMode) continue;
                float r = stickRadius();
                if (Math.hypot(x - Layout.cx(d, w), y - Layout.cy(d, h)) <= r) return d;
                continue;
            }
            float cx = Layout.cx(d, w), cy = Layout.cy(d, h);
            float hf = Layout.half(d) + 3f;
            if (Math.abs(x - cx) <= hf && Math.abs(y - cy) <= hf) return d;
        }
        return null;
    }

    public static boolean isShown(Def d) {
        Cfg.Pos p = Layout.pos(d);
        if (editMode) return true;
        if (Cfg.I.hidden) return d.id().equals("hide");
        if (!p.vis) return false;
        Minecraft m = mc();
        if (d.id().equals("fly")) return m.player != null && m.player.getAbilities().mayfly;
        if (d.id().equals("lock")) return Morph.active();
        return true;
    }

    public static float stickRadius() { return 34f * Cfg.I.scale * Layout.pos(Layout.byId("stick")).s; }

    static void down(int id, float x, float y) {
        Ptr p = new Ptr(id, x, y);
        P.put(id, p);
        Window win = mc().getWindow();
        int w = win.getGuiScaledWidth(), h = win.getGuiScaledHeight();

        if (editMode) {
            int i = Hud.editBtnAt(x, y, w, h);
            if (i >= 0) { editAction(i); return; }
            Def d = hitButton(x, y, w, h);
            if (d != null) { selected = d.id(); p.owner = "drag"; p.def = d; }
            return;
        }

        if (trayOpen) {
            String it = Hud.trayAt(x, y, w, h);
            if (it != null) { trayAction(it); return; }
            Def d0 = hitButton(x, y, w, h);
            if (d0 == null || !d0.id().equals("tray")) trayOpen = false;
        }

        Def d = hitButton(x, y, w, h);
        if (d != null) {
            p.owner = "btn";
            p.def = d;
            press(d);
            return;
        }
        if (Cfg.I.hidden) return;

        // الشريط السريع (Hotbar)
        float hx0 = w / 2f - 95, hy0 = h - 30;
        if (x >= hx0 && x <= hx0 + 190 && y >= hy0) {
            p.owner = "hotbar";
            selectSlot(x, w);
            return;
        }

        // منطقة عصا الحركة
        boolean inZone;
        Def sd = Layout.byId("stick");
        if (Cfg.I.floatingStick) {
            boolean leftSide = !Cfg.I.leftHanded;
            inZone = y > h * 0.28f && (leftSide ? x < w * 0.42f : x > w * 0.58f);
        } else {
            inZone = Math.hypot(x - Layout.cx(sd, w), y - Layout.cy(sd, h)) <= stickRadius() * 1.7f;
        }
        if (inZone && stickPtr == Integer.MIN_VALUE) {
            p.owner = "stick";
            stickPtr = id;
            if (Cfg.I.floatingStick) {
                float r = stickRadius();
                stickCx = Math.max(r + 4, Math.min(w - r - 4, x));
                stickCy = Math.max(r + 4, Math.min(h - r - 4, y));
            } else {
                stickCx = Layout.cx(sd, w);
                stickCy = Layout.cy(sd, h);
            }
            updateStick(x, y);
            return;
        }
        p.owner = "look";
    }

    static void updateStick(float x, float y) {
        float r = stickRadius();
        float dx = x - stickCx, dy = y - stickCy;
        float dist = (float) Math.hypot(dx, dy);
        if (dist > r) {
            if (Cfg.I.floatingStick) {
                // القاعدة تتبع الإصبع ليبقى الإبهام مرتاحاً
                float k = (dist - r) / dist;
                stickCx += dx * k;
                stickCy += dy * k;
                dx = x - stickCx;
                dy = y - stickCy;
            } else {
                dx = dx / dist * r;
                dy = dy / dist * r;
            }
        }
        knobX = dx / r;
        knobY = dy / r;
    }

    static void move(int id, float x, float y) {
        Ptr p = P.get(id);
        if (p == null) return;
        float ddx = x - p.lx, ddy = y - p.ly;
        p.lx = x; p.ly = y; p.x = x; p.y = y;
        if (Math.hypot(x - p.sx, y - p.sy) > 5f) p.moved = true;
        switch (p.owner) {
            case "stick" -> updateStick(x, y);
            case "look" -> look(ddx, ddy);
            case "hotbar" -> selectSlot(x, mc().getWindow().getGuiScaledWidth());
            case "drag" -> {
                if (p.def != null && editMode) {
                    Window win = mc().getWindow();
                    int w = win.getGuiScaledWidth(), h = win.getGuiScaledHeight();
                    Cfg.Pos ps = Layout.pos(p.def);
                    float fx = Math.max(0.02f, Math.min(0.98f, x / w));
                    ps.x = Cfg.I.leftHanded ? 1f - fx : fx;
                    ps.y = Math.max(0.03f, Math.min(0.97f, y / h));
                }
            }
            default -> {}
        }
    }

    static void up(int id, float x, float y) {
        Ptr p = P.remove(id);
        if (p == null) return;
        switch (p.owner) {
            case "btn" -> release(p.def);
            case "stick" -> {
                stickPtr = Integer.MIN_VALUE;
                knobX = knobY = 0;
            }
            case "drag" -> Cfg.save();
            case "look" -> {
                long dt = System.currentTimeMillis() - p.t0;
                if (p.mining) {
                    hold(mc().options.keyAttack, false);
                } else if (!p.moved && dt < 300) {
                    HitResult hr = mc().hitResult;
                    if (hr != null && hr.getType() == HitResult.Type.ENTITY) tap(mc().options.keyAttack);
                    else tap(mc().options.keyUse);
                }
            }
            default -> {}
        }
    }

    static void look(float dx, float dy) {
        if (mc().player == null) return;
        float k = guiToPx() * 1.25f * Cfg.I.look;
        mc().player.turn(dx * k, dy * k);
    }

    static void selectSlot(float x, int w) {
        float hx0 = w / 2f - 91;
        int slot = (int) Math.floor((x - (hx0 + 1)) / 20f);
        slot = Math.max(0, Math.min(8, slot));
        if (mc().player != null && mc().player.getInventory().getSelectedSlot() != slot) {
            mc().player.getInventory().setSelectedSlot(slot);
        }
    }

    // ───────────────────────── الأزرار ─────────────────────────
    public static boolean isPressed(Def d) {
        switch (d.id()) {
            case "sneak": return sneakOn;
            case "sprint": return sprintOn;
            case "tray": return trayOpen;
            case "lock": return Morph.locked;
            case "fly": return mc().player != null && mc().player.getAbilities().flying;
            default: break;
        }
        for (Ptr p : P.values()) if (p.def != null && p.def.id().equals(d.id()) && p.owner.equals("btn")) return true;
        return false;
    }

    static KeyMapping customKey(Def d) {
        if (d.target() == null) return null;
        try { return KeyMapping.get(d.target()); } catch (Throwable t) { return null; }
    }

    static boolean isFolder(Def d) { return "folder".equals(d.icon()); }

    static void press(Def d) {
        var o = mc().options;
        switch (d.id()) {
            case "jump" -> { if (!(Morph.active() && Morph.locked)) hold(o.keyJump, true); }
            case "attack" -> { tap(o.keyAttack); hold(o.keyAttack, true); }
            case "use" -> { tap(o.keyUse); hold(o.keyUse, true); }
            case "sneak" -> sneakOn = Cfg.I.sneakToggle ? !sneakOn : true;
            case "sprint" -> sprintOn = !sprintOn;
            case "inv" -> tap(o.keyInventory);
            case "chat" -> tap(o.keyChat);
            case "drop" -> tap(o.keyDrop);
            case "swap" -> tap(o.keySwapOffhand);
            case "persp" -> tap(o.keyTogglePerspective);
            case "pause" -> mc().setScreen(new PauseScreen(true));
            case "tray" -> trayOpen = !trayOpen;
            case "hide" -> {
                Cfg.I.hidden = !Cfg.I.hidden;
                trayOpen = false;
                Cfg.save();
            }
            case "fly" -> {
                var pl = mc().player;
                if (pl != null && pl.getAbilities().mayfly) {
                    pl.getAbilities().flying = !pl.getAbilities().flying;
                    pl.onUpdateAbilities();
                }
            }
            case "lock" -> Morph.locked = !Morph.locked;
            default -> {
                if (d.id().startsWith("c")) {
                    if (isFolder(d)) {
                        mc().setScreen(new ModsScreen(d.target()));
                    } else {
                        KeyMapping km = customKey(d);
                        if (km != null) { tap(km); hold(km, true); }
                    }
                }
            }
        }
    }

    static void release(Def d) {
        if (d == null) return;
        var o = mc().options;
        switch (d.id()) {
            case "jump" -> hold(o.keyJump, false);
            case "attack" -> hold(o.keyAttack, false);
            case "use" -> hold(o.keyUse, false);
            case "sneak" -> { if (!Cfg.I.sneakToggle) sneakOn = false; }
            default -> {
                if (d.id().startsWith("c") && !isFolder(d)) {
                    KeyMapping km = customKey(d);
                    if (km != null) hold(km, false);
                }
            }
        }
    }

    // ───────────────────────── القائمة السريعة ─────────────────────────
    static void trayAction(String id) {
        Minecraft m = mc();
        switch (id) {
            case "mods" -> { trayOpen = false; m.setScreen(new ModsScreen(null)); }
            case "morph" -> { trayOpen = false; m.setScreen(new MorphScreen()); }
            case "pickmorph" -> { trayOpen = false; Morph.pickFromCrosshair(); }
            case "unmorph" -> { trayOpen = false; Morph.clear(); Hud.toast("رجعت لشكلك الطبيعي"); }
            case "cmds" -> { trayOpen = false; m.setScreen(new CmdScreen()); }
            case "settings" -> { trayOpen = false; m.setScreen(new SettingsScreen()); }
            case "edit" -> { trayOpen = false; editMode = true; selected = null; Hud.toast("اسحب أي زر لتحريكه"); }
            case "full" -> {
                trayOpen = false;
                m.getWindow().toggleFullScreen();
                m.options.fullscreen().set(m.getWindow().isFullscreen());
            }
            case "hand" -> {
                Cfg.I.leftHanded = !Cfg.I.leftHanded;
                Cfg.save();
                trayOpen = false;
                Hud.toast(Cfg.I.leftHanded ? "وضع اليد اليسرى" : "وضع اليد اليمنى");
            }
            case "shot" -> { trayOpen = false; runLater(3, () -> tap(m.options.keyScreenshot)); }
            case "stop" -> setEnabled(false);
            default -> {}
        }
    }

    static void editAction(int i) {
        switch (i) {
            case 0 -> { editMode = false; selected = null; Cfg.save(); Hud.toast("تم حفظ مكان الأزرار"); }
            case 1 -> { if (selected != null) { Cfg.Pos p = Layout.pos(Layout.byId(selected)); p.s = Math.min(2.5f, p.s + 0.1f); Cfg.save(); } }
            case 2 -> { if (selected != null) { Cfg.Pos p = Layout.pos(Layout.byId(selected)); p.s = Math.max(0.4f, p.s - 0.1f); Cfg.save(); } }
            case 3 -> {
                if (selected == null) return;
                if (selected.startsWith("c")) { Layout.removeCustom(selected); selected = null; return; }
                if (selected.equals("tray") || selected.equals("hide")) { Hud.toast("هذا الزر ضروري ولا يمكن إخفاؤه"); return; }
                Cfg.Pos p = Layout.pos(Layout.byId(selected));
                p.vis = !p.vis;
                Cfg.save();
            }
            case 4 -> { Layout.resetPositions(); selected = null; Hud.toast("تمت إعادة الأزرار لأماكنها"); }
            default -> {}
        }
    }

    // ───────────────────────── القوائم (شاشات) ─────────────────────────
    static void screenPointer(int id, int phase, float x, float y, double px, double py) {
        Screen s = mc().screen;
        if (s == null) return;
        int mods = Toolbar.shift ? GLFW.GLFW_MOD_SHIFT : 0;
        int btn = Toolbar.right ? 1 : 0;
        if (phase == 0) {
            lastScreenX = x; lastScreenY = y;
            if (Toolbar.click(x, y)) return;
            P.put(id, new Ptr(id, x, y));
            if (screenPrimary != Integer.MIN_VALUE) return;
            screenPrimary = id;
            P.get(id).owner = "screen";
            GLFW.glfwSetCursorPos(mc().getWindow().handle(), px, py);
            s.mouseMoved(x, y);
            s.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(btn, mods)), false);
        } else if (phase == 1) {
            Ptr p = P.get(id);
            if (p == null) return;
            float dx = x - p.lx, dy = y - p.ly;
            p.lx = x; p.ly = y;
            lastScreenX = x; lastScreenY = y;
            if (id == screenPrimary) {
                GLFW.glfwSetCursorPos(mc().getWindow().handle(), px, py);
                s.mouseMoved(x, y);
                s.mouseDragged(new MouseButtonEvent(x, y, new MouseButtonInfo(btn, mods)), dx, dy);
            }
        } else {
            Ptr p = P.remove(id);
            if (p == null) return;
            if (id == screenPrimary) {
                screenPrimary = Integer.MIN_VALUE;
                s.mouseReleased(new MouseButtonEvent(x, y, new MouseButtonInfo(btn, mods)));
            }
        }
    }

    // ───────────────────────── التحديث كل Tick ─────────────────────────
    public static void tick() {
        Minecraft m = mc();
        if (m.options == null) return;

        Iterator<Rel> it = rels.iterator();
        while (it.hasNext()) {
            Rel r = it.next();
            if (--r.ticks <= 0) {
                if (!held.contains(r.km)) r.km.setDown(false);
                it.remove();
            }
        }
        Iterator<Object[]> lt = later.iterator();
        List<Runnable> fire = new ArrayList<>();
        while (lt.hasNext()) {
            Object[] o = lt.next();
            int t = (Integer) o[0] - 1;
            if (t <= 0) { fire.add((Runnable) o[1]); lt.remove(); } else o[0] = t;
        }
        for (Runnable r : fire) { try { r.run(); } catch (Throwable t) { t.printStackTrace(); } }

        if (!Cfg.I.enabled) return;

        boolean inScreen = m.screen != null;
        if (inScreen != wasScreen) {
            wasScreen = inScreen;
            if (inScreen) {
                // فتحت شاشة: أفلت كل الأزرار المضغوطة
                for (Ptr p : new ArrayList<>(P.values())) {
                    if (p.owner.equals("btn") && p.def != null) release(p.def);
                    if (p.mining) hold(m.options.keyAttack, false);
                }
                P.clear();
                stickPtr = Integer.MIN_VALUE;
                knobX = knobY = 0;
                screenPrimary = Integer.MIN_VALUE;
                for (KeyMapping k : new ArrayList<>(held)) hold(k, false);
                trayOpen = false;
            }
        }
        if (inScreen) return;
        if (m.player == null) return;

        // المؤشر حر دائماً أثناء اللعب باللمس
        if (m.mouseHandler.isMouseGrabbed()) m.mouseHandler.releaseMouse();
        if (nativeSeen && m.getWindow() != null) {
            GLFW.glfwSetInputMode(m.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        }

        boolean locked = Morph.active() && Morph.locked;
        boolean act = stickPtr != Integer.MIN_VALUE && !locked;
        float nx = act ? knobX : 0, ny = act ? knobY : 0;
        var o = m.options;
        hold(o.keyUp, ny < -0.35f);
        hold(o.keyDown, ny > 0.35f);
        hold(o.keyLeft, nx < -0.35f);
        hold(o.keyRight, nx > 0.35f);
        boolean run = (sprintOn && act && ny < -0.35f) || (Cfg.I.autoSprint && act && ny < -0.9f);
        hold(o.keySprint, run);
        hold(o.keyShift, sneakOn);

        // الضغط المطوّل على الشاشة = كسر مستمر
        long now = System.currentTimeMillis();
        for (Ptr p : P.values()) {
            if (p.owner.equals("look") && !p.moved && !p.mining && now - p.t0 > 300) {
                p.mining = true;
                tap(o.keyAttack);
                hold(o.keyAttack, true);
            }
        }
        if (locked) {
            var dm = m.player.getDeltaMovement();
            m.player.setDeltaMovement(0, dm.y, 0);
        }
    }
}
