package dev.lamsa.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/** أدوات الرسم بنمط ماين كرافت بيدروك: أزرار رمادية مشطوفة، ألوان داكنة، أيقونات بكسلية. */
public final class Ui {
    private Ui() {}

    // ألوان بيدروك
    public static final int BORDER = 0xFF1E1E1F;
    public static final int FACE = 0xFF707172;
    public static final int FACE_HOT = 0xFF8D8E90;
    public static final int FACE_DOWN = 0xFF4A4B4C;
    public static final int LIGHT = 0xFFB1B2B5;
    public static final int DARK = 0xFF3A3B3C;
    public static final int GREEN = 0xFF3C8527;
    public static final int GREEN_L = 0xFF6CC349;
    public static final int GREEN_D = 0xFF2A641C;
    public static final int PANEL = 0xFF313233;
    public static final int PANEL_L = 0xFF48494A;
    public static final int PANEL_D = 0xFF1E1E1F;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int GRAY = 0xFFB1B2B5;
    public static final int GOLD = 0xFFFFE866;

    public static int al(int rgb, float a) {
        int alpha = Math.max(0, Math.min(255, (int) (a * 255)));
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static int mulA(int argb, float m) {
        int a = (int) (((argb >>> 24) & 0xFF) * m);
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
    }

    public static Font font() { return Minecraft.getInstance().font; }

    public static Component t(String s) { return Component.literal(s); }

    public static int tw(String s) { return font().width(t(s)); }

    /** نص عربي/إنجليزي (عبر Component ليعمل التشكيل واتجاه الكتابة). */
    public static void text(GuiGraphics g, String s, float x, float y, int color, float scale, boolean center, boolean shadow) {
        float w = tw(s) * scale;
        g.pose().pushMatrix();
        g.pose().translate(center ? x - w / 2f : x, y);
        g.pose().scale(scale, scale);
        g.drawString(font(), t(s), 0, 0, color, shadow);
        g.pose().popMatrix();
    }

    public static void text(GuiGraphics g, String s, float x, float y, int color, float scale, boolean center) {
        text(g, s, x, y, color, scale, center, true);
    }

    public static void rect(GuiGraphics g, float x, float y, float w, float h, int c) {
        g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), c);
    }

    /** زر مشطوف بنمط بيدروك. */
    public static void bevel(GuiGraphics g, float x, float y, float w, float h, int face, int light, int dark, float alpha) {
        int X = Math.round(x), Y = Math.round(y), W = Math.round(w), H = Math.round(h);
        g.fill(X, Y, X + W, Y + H, mulA(BORDER, alpha));
        g.fill(X + 1, Y + 1, X + W - 1, Y + H - 1, mulA(dark, alpha));
        g.fill(X + 1, Y + 1, X + W - 2, Y + H - 2, mulA(light, alpha));
        g.fill(X + 2, Y + 2, X + W - 2, Y + H - 2, mulA(face, alpha));
    }

    public static void button(GuiGraphics g, float x, float y, float w, float h, boolean down, boolean hot, boolean green, float alpha) {
        if (green) {
            bevel(g, x, y, w, h, down ? GREEN_D : GREEN, down ? GREEN : GREEN_L, GREEN_D, alpha);
        } else if (down) {
            bevel(g, x, y, w, h, FACE_DOWN, DARK, LIGHT, alpha);
        } else {
            bevel(g, x, y, w, h, hot ? FACE_HOT : FACE, LIGHT, DARK, alpha);
        }
    }

    /** مربع داكن للوحات. */
    public static void panel(GuiGraphics g, float x, float y, float w, float h) {
        bevel(g, x, y, w, h, PANEL, PANEL_L, PANEL_D, 1f);
    }

    public static void circle(GuiGraphics g, float cx, float cy, float r, int color) {
        int R = Math.max(1, Math.round(r));
        int X = Math.round(cx), Y = Math.round(cy);
        for (int dy = -R; dy <= R; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) R * R - (double) dy * dy));
            g.fill(X - dx, Y + dy, X + dx, Y + dy + 1, color);
        }
    }

    public static void ring(GuiGraphics g, float cx, float cy, float r, float thick, int color) {
        int R = Math.max(1, Math.round(r));
        int X = Math.round(cx), Y = Math.round(cy);
        int ri = Math.max(0, Math.round(r - thick));
        for (int dy = -R; dy <= R; dy++) {
            int dxo = (int) Math.round(Math.sqrt((double) R * R - (double) dy * dy));
            int dxi = Math.abs(dy) < ri ? (int) Math.round(Math.sqrt((double) ri * ri - (double) dy * dy)) : 0;
            if (dxi == 0) {
                g.fill(X - dxo, Y + dy, X + dxo, Y + dy + 1, color);
            } else {
                g.fill(X - dxo, Y + dy, X - dxi, Y + dy + 1, color);
                g.fill(X + dxi, Y + dy, X + dxo, Y + dy + 1, color);
            }
        }
    }

    /** أيقونة بكسلية مرسومة بحجم عام (بكسلات الواجهة). */
    public static void icon(GuiGraphics g, String name, float cx, float cy, float size, int color) {
        String[] rows = ICONS.get(name);
        if (rows == null) rows = ICONS.get("star");
        int h = rows.length, w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        float s = size / Math.max(w, h);
        g.pose().pushMatrix();
        g.pose().translate(cx - w * s / 2f, cy - h * s / 2f);
        g.pose().scale(s, s);
        for (int y = 0; y < h; y++) {
            String r = rows[y];
            int x = 0;
            while (x < r.length()) {
                if (r.charAt(x) == '#') {
                    int x2 = x;
                    while (x2 < r.length() && r.charAt(x2) == '#') x2++;
                    g.fill(x, y, x2, y + 1, color);
                    x = x2;
                } else x++;
            }
        }
        g.pose().popMatrix();
    }

    public static boolean in(double x, double y, float rx, float ry, float rw, float rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private static final Map<String, String[]> ICONS = new HashMap<>();
    private static void ic(String n, String... r) { ICONS.put(n, r); }

    static {
        ic("up", "....#....", "...###...", "..#####..", ".#######.", "#########", "...###...", "...###...", "...###...");
        ic("down", "...###...", "...###...", "...###...", "#########", ".#######.", "..#####..", "...###...", "....#....");
        ic("sword", "......###", ".....####", "....###..", "...###...", "#.###....", ".###.....", "..#......", ".###.....", "##.#.....");
        ic("hand", "..#.#.#..", "..#.#.#..", ".#######.", "#########", "#########", ".#######.", "..#####..", "...###...");
        ic("chest", ".#######.", "#########", "#.......#", "#########", "#...#...#", "#...#...#", "#.......#", "#########");
        ic("pause", "..##.##..", "..##.##..", "..##.##..", "..##.##..", "..##.##..", "..##.##..", "..##.##..");
        ic("chat", "#########", "#.......#", "#.#.#.#.#", "#.......#", "#########", "..##.....", "...#.....");
        ic("camera", "..###....", "#########", "#.......#", "#..###..#", "#.#...#.#", "#..###..#", "#.......#", "#########");
        ic("eye", "..#####..", ".#######.", "##..#..##", "##.###.##", "##..#..##", ".#######.", "..#####..");
        ic("menu", "#########", "#########", ".........", "#########", "#########", ".........", "#########", "#########");
        ic("gear", "...#.#...", ".#######.", "..##.##..", "###...###", "..##.##..", ".#######.", "...#.#...");
        ic("plus", "...###...", "...###...", "...###...", "#########", "#########", "#########", "...###...", "...###...", "...###...");
        ic("minus", ".........", ".........", "#########", "#########", "#########", ".........");
        ic("x", "##.....##", "###...###", ".###.###.", "..#####..", "...###...", "..#####..", ".###.###.", "###...###", "##.....##");
        ic("check", "........#", ".......##", "......##.", "#....##..", "##..##...", ".####....", "..##.....");
        ic("drop", "...###...", "...###...", "...###...", "#########", ".#######.", "..#####..", "...###...", "....#....", "#########");
        ic("swap", "..#......", ".###..###", "#####.###", "..#......", "......#..", "###.#####", "###..###.", "......#..");
        ic("expand", "###...###", "#.......#", "#.......#", ".........", ".........", ".........", "#.......#", "#.......#", "###...###");
        ic("power", "....#....", "..#.#.#..", ".#..#..#.", "#...#...#", "#.......#", "#.......#", ".#.....#.", "..#####..");
        ic("puzzle", "..###....", "..#.#....", "#######..", "#.....#..", "#.....###", "#.....#.#", "#.....###", "#######..");
        ic("paw", "#..#.#..#", "#..#.#..#", ".........", "...###...", "..#####..", ".#######.", ".##...##.", "..#...#..");
        ic("lock", "..#####..", ".#.....#.", ".#.....#.", "#########", "#########", "####.####", "####.####", "#########");
        ic("move", "....#....", "...###...", "..#.#.#..", "...#.#...", "###.#.###", "...#.#...", "..#.#.#..", "...###...", "....#....");
        ic("wing", "#.......#", "##.....##", "####.####", "#########", ".#######.", "..#####..", "...###...");
        ic("run", "##..##...", ".##..##..", "..##..##.", "...##..##", "..##..##.", ".##..##..", "##..##...");
        ic("term", "#########", "#.......#", "#.#.....#", "#..#....#", "#.#..###.", "#.......#", "#########");
        ic("folder", ".###.....", "#########", "#.......#", "#.......#", "#.......#", "#.......#", "#########");
        ic("star", "....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..##.##..", ".##...##.");
        ic("pick", "......###", ".....####", "....####.", "...###...", "..###....", ".###.....", "###......", "##.......");
        ic("block", "...###...", "..#####..", ".###.###.", "####.####", "#.#####.#", "#..###..#", "#...#...#", ".#.....#.");
        ic("pin", "..#####..", "...###...", "...###...", "..#####..", "....#....", "....#....", "....#....");
        ic("arrow", "..#......", "..##.....", "..###....", "..####...", "..###....", "..##.....", "..#......");
    }
}
