package dev.lamsa.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * قاعدة كل القوائم: لوحة بنمط بيدروك، سحب للتمرير، وضغط مطوّل ثم انزلاق لاختيار
 * أي عنصر (العنصر يكبر قليلاً تحت الإصبع لاختيار دقيق).
 */
public abstract class PanelScreen extends Screen {
    protected float px, py, pw, ph;
    protected float cx0, cy0, cx1, cy1;
    protected float scroll, maxScroll;
    protected int cols = 1;
    protected float itemH = 24, gap = 3;
    protected boolean grid = false;
    protected float cellMin = 42;
    protected final String heading;

    private int mode; // 0 لا شيء، 1 معلّق، 2 تمرير، 3 اختيار بالانزلاق
    private long pressT;
    private double pressX, pressY, curX, curY;
    private int pressedItem = -1, hotItem = -1;
    private float cellW;

    protected PanelScreen(String heading) {
        super(Component.literal(heading));
        this.heading = heading;
    }

    protected abstract int count();
    protected abstract void drawItem(GuiGraphics g, int i, float x, float y, float w, float h, boolean hot);
    protected abstract void activate(int i, double x, double y);
    protected int headerExtra() { return 0; }
    protected void drawHeader(GuiGraphics g, float x, float y, float w, int mx, int my) {}
    protected boolean headerClick(double x, double y) { return false; }
    protected int footerH() { return 0; }
    protected void drawFooter(GuiGraphics g, float x, float y, float w, int hot) {}

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        pw = Math.min(width - 8, grid ? 440 : 380);
        ph = height - 10;
        px = (width - pw) / 2f;
        py = 5;
        cx0 = px + 6;
        cx1 = px + pw - 6;
        cy0 = py + 24 + headerExtra();
        cy1 = py + ph - 6 - footerH();
        if (grid) {
            float cw = cx1 - cx0 - 10;
            cols = Math.max(3, (int) (cw / (cellMin + gap)));
            cellW = (cw - (cols - 1) * gap) / cols;
            itemH = cellW;
        } else {
            cols = 1;
            cellW = cx1 - cx0 - 10;
        }
        clamp();
    }

    protected void relayout() { init(); }

    private int rows() { return (count() + cols - 1) / cols; }

    private void clamp() {
        float total = rows() * (itemH + gap);
        maxScroll = Math.max(0, total - (cy1 - cy0) + gap);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    protected float itemX(int i) { return cx0 + (i % cols) * (cellW + gap); }
    protected float itemY(int i) { return cy0 + (i / cols) * (itemH + gap) - scroll + gap; }
    protected float cellWidth() { return cellW; }

    protected int itemAt(double x, double y) {
        if (y < cy0 || y > cy1 || x < cx0 || x > cx0 + cols * (cellW + gap)) return -1;
        int col = (int) ((x - cx0) / (cellW + gap));
        int row = (int) Math.floor((y - cy0 + scroll - gap) / (itemH + gap));
        if (col < 0 || col >= cols || row < 0) return -1;
        int i = row * cols + col;
        return i < count() ? i : -1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xB0000000);
        Ui.panel(g, px, py, pw, ph);
        // العنوان
        Ui.bevel(g, px + 3, py + 3, pw - 6, 20, Ui.GREEN, Ui.GREEN_L, Ui.GREEN_D, 1f);
        Ui.text(g, heading, px + pw / 2f, py + 9, Ui.TEXT, 1f, true);
        // زر الإغلاق
        float cb = 18;
        Ui.button(g, px + pw - cb - 6, py + 4, cb, cb, false, false, false, 1f);
        Ui.icon(g, "x", px + pw - cb / 2f - 6, py + 4 + cb / 2f, 9, Ui.TEXT);

        drawHeader(g, px + 6, py + 26, pw - 12, mx, my);

        // تحويل الضغط المطوّل إلى وضع الاختيار
        long now = System.currentTimeMillis();
        if (mode == 1 && now - pressT > 260) {
            mode = 3;
            hotItem = pressedItem;
        }
        if (mode == 3) {
            hotItem = itemAt(curX, curY);
            if (curY < cy0 + 12) scroll -= 3;
            else if (curY > cy1 - 12) scroll += 3;
            clamp();
        }

        g.enableScissor((int) cx0, (int) cy0, (int) (cx1), (int) cy1);
        int n = count();
        int lastHot = -1;
        for (int i = 0; i < n; i++) {
            float y = itemY(i);
            if (y + itemH < cy0 - 2 || y > cy1 + 2) continue;
            boolean hot = (mode == 3 && i == hotItem) || (mode == 1 && i == pressedItem);
            if (mode == 3 && i == hotItem) { lastHot = i; continue; }
            drawItem(g, i, itemX(i), y, cellW, itemH, hot);
        }
        if (lastHot >= 0) {
            float x = itemX(lastHot), y = itemY(lastHot);
            float s = grid ? 1.25f : 1.06f;
            float cx = x + cellW / 2f, cy = y + itemH / 2f;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(s, s);
            g.pose().translate(-cx, -cy);
            drawItem(g, lastHot, x, y, cellW, itemH, true);
            g.pose().popMatrix();
        }
        g.disableScissor();

        // شريط التمرير
        if (maxScroll > 0) {
            float tx = cx1 - 6, th = cy1 - cy0;
            Ui.rect(g, tx, cy0, 5, th, 0xFF1E1E1F);
            float bh = Math.max(18, th * th / (th + maxScroll));
            float by = cy0 + (th - bh) * (scroll / maxScroll);
            Ui.rect(g, tx, by, 5, bh, Ui.LIGHT);
        }
        if (footerH() > 0) drawFooter(g, cx0, cy1 + 3, cx1 - cx0, mode == 3 ? hotItem : -1);
        Hud.drawToast(g, width, height);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        double x = ev.x(), y = ev.y();
        if (Ui.in(x, y, px + pw - 24, py + 4, 18, 18)) { onClose(); return true; }
        if (y < cy0 && headerClick(x, y)) return true;
        if (y >= cy0 && y <= cy1) {
            mode = 1;
            pressT = System.currentTimeMillis();
            pressX = curX = x;
            pressY = curY = y;
            pressedItem = itemAt(x, y);
            hotItem = -1;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent ev, double dx, double dy) {
        curX = ev.x();
        curY = ev.y();
        if (mode == 1 && Math.hypot(curX - pressX, curY - pressY) > 7) {
            mode = 2; // سحب سريع = تمرير
        }
        if (mode == 2) {
            scroll -= dy;
            clamp();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent ev) {
        double x = ev.x(), y = ev.y();
        int target = -1;
        if (mode == 1) target = pressedItem;
        else if (mode == 3) target = itemAt(x, y);
        mode = 0;
        hotItem = -1;
        pressedItem = -1;
        if (target >= 0) activate(target, x, y);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        scroll -= sy * 28;
        clamp();
        return true;
    }
}
