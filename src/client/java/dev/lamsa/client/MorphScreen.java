package dev.lamsa.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** قائمة التحول: كل الكائنات والبلوكات تُقرأ مباشرة من سجلات اللعبة (تدعم أي مود وأي إصدار). */
public class MorphScreen extends PanelScreen {
    private boolean blocksTab = false;
    private int srcIdx = 0;
    private final List<String> sources = new ArrayList<>();
    private final List<Object> items = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    public MorphScreen() {
        super("التحول إلى أي كائن أو بلوك");
        grid = true;
        cellMin = 40;
    }

    @Override
    protected void init() {
        build();
        super.init();
    }

    private void build() {
        items.clear();
        names.clear();
        Set<String> ns = new LinkedHashSet<>();
        List<Object> all = new ArrayList<>();
        List<Identifier> ids = new ArrayList<>();
        if (!blocksTab) {
            for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
                if (t == EntityType.PLAYER || !t.canSummon()) continue;
                all.add(t);
                ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(t));
            }
        } else {
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b.defaultBlockState().isAir()) continue;
                all.add(b);
                ids.add(BuiltInRegistries.BLOCK.getKey(b));
            }
        }
        for (Identifier id : ids) ns.add(id.getNamespace());
        sources.clear();
        sources.add("الكل");
        sources.addAll(ns);
        if (srcIdx >= sources.size()) srcIdx = 0;
        for (int i = 0; i < all.size(); i++) {
            if (srcIdx != 0 && !ids.get(i).getNamespace().equals(sources.get(srcIdx))) continue;
            items.add(all.get(i));
            Object o = all.get(i);
            names.add(o instanceof EntityType<?> t ? t.getDescription().getString() : ((Block) o).getName().getString());
        }
    }

    @Override protected int headerExtra() { return 52; }
    @Override protected int count() { return items.size(); }

    private float[] btn(int k) {
        // 0 كائنات، 1 بلوكات، 2 عودة، 3 الوضع، 4 المصدر
        float x = px + 6, w = pw - 12, y = py + 26;
        return switch (k) {
            case 0 -> new float[]{x, y, w * 0.30f - 2, 22};
            case 1 -> new float[]{x + w * 0.30f, y, w * 0.30f - 2, 22};
            case 2 -> new float[]{x + w * 0.60f, y, w * 0.40f, 22};
            case 3 -> new float[]{x, y + 25, w * 0.55f - 2, 22};
            default -> new float[]{x + w * 0.55f, y + 25, w * 0.45f, 22};
        };
    }

    @Override
    protected void drawHeader(GuiGraphics g, float x, float y, float w, int mx, int my) {
        String[] labels = {"الكائنات", "البلوكات", Morph.active() ? "عودة لشكلي" : "لا تحول حالياً",
            Cfg.I.morphFull ? "الوضع: تحول كامل" : "الوضع: قدرات فقط", "المصدر: " + sources.get(srcIdx)};
        for (int k = 0; k < 5; k++) {
            float[] r = btn(k);
            boolean green = (k == 0 && !blocksTab) || (k == 1 && blocksTab) || (k == 3 && Cfg.I.morphFull);
            Ui.button(g, r[0], r[1], r[2], r[3], false, false, green, 1f);
            float sc = Math.min(0.9f, (r[2] - 6) / Math.max(1, Ui.tw(labels[k])));
            Ui.text(g, labels[k], r[0] + r[2] / 2f, r[1] + 11 - 4 * sc, Ui.TEXT, sc, true);
        }
    }

    @Override
    protected boolean headerClick(double x, double y) {
        for (int k = 0; k < 5; k++) {
            float[] r = btn(k);
            if (Ui.in(x, y, r[0], r[1], r[2], r[3])) {
                switch (k) {
                    case 0 -> { blocksTab = false; srcIdx = 0; }
                    case 1 -> { blocksTab = true; srcIdx = 0; }
                    case 2 -> { Morph.clear(); Hud.toast("رجعت لشكلك الطبيعي"); }
                    case 3 -> { Cfg.I.morphFull = !Cfg.I.morphFull; Cfg.save(); }
                    default -> srcIdx = (srcIdx + 1) % sources.size();
                }
                scrollReset();
                build();
                relayout();
                return true;
            }
        }
        return false;
    }

    private void scrollReset() { scroll = 0; }

    static ItemStack iconFor(Object o) {
        try {
            if (o instanceof EntityType<?> t) {
                SpawnEggItem egg = SpawnEggItem.byId(t);
                return egg != null ? new ItemStack(egg) : ItemStack.EMPTY;
            }
            Item it = ((Block) o).asItem();
            return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    protected void drawItem(GuiGraphics g, int i, float x, float y, float w, float h, boolean hot) {
        Ui.button(g, x, y, w, h, false, hot, hot, 1f);
        ItemStack st = iconFor(items.get(i));
        if (!st.isEmpty()) {
            float s = (w * 0.62f) / 16f;
            g.pose().pushMatrix();
            g.pose().translate(x + w / 2f - 8 * s, y + h / 2f - 8 * s);
            g.pose().scale(s, s);
            g.renderItem(st, 0, 0);
            g.pose().popMatrix();
        } else {
            String nm = names.get(i);
            float sc = Math.min(0.8f, (w - 6) / Math.max(1, Ui.tw(nm)));
            Ui.text(g, nm, x + w / 2f, y + h / 2f - 4 * sc, Ui.TEXT, sc, true);
        }
    }

    @Override
    protected void activate(int i, double x, double y) {
        Object o = items.get(i);
        boolean ok;
        if (o instanceof EntityType<?> t) ok = Morph.setMob(t, Cfg.I.morphFull);
        else ok = Morph.setBlock((Block) o, Cfg.I.morphFull);
        if (ok) onClose();
    }

    @Override protected int footerH() { return 14; }

    @Override
    protected void drawFooter(GuiGraphics g, float x, float y, float w, int hot) {
        String s = hot >= 0 && hot < names.size() ? names.get(hot)
            : "اضغط للتحول • اضغط مطولاً ثم اسحب لاختيار دقيق (العنصر يكبر)";
        Ui.text(g, s, x + w / 2f, y + 2, hot >= 0 ? Ui.GOLD : Ui.GRAY,
            Math.min(0.85f, w / Math.max(1, Ui.tw(s))), true, false);
    }
}
