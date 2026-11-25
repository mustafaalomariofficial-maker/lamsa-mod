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

/**
 * قائمة التحول: تقرأ سجل الكائنات وسجل البلوكات كاملاً من اللعبة (تدعم أي مود وأي إصدار).
 * تُعرض كل الكائنات حتى التي ليس لها بيضة، ويظهر لكل عنصر اسمه ومساره (المعرف).
 */
public class MorphScreen extends PanelScreen {
    private boolean blocksTab = false;
    private int srcIdx = 0;
    private final List<String> sources = new ArrayList<>();
    private final List<Object> items = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();

    public MorphScreen() {
        super("التحول إلى أي كائن أو بلوك");
        grid = true;
        cellMin = 46;
    }

    @Override
    protected void init() {
        build();
        super.init();
    }

    private void build() {
        items.clear();
        names.clear();
        ids.clear();
        Set<String> ns = new LinkedHashSet<>();
        List<Object> all = new ArrayList<>();
        List<Identifier> keys = new ArrayList<>();
        if (!blocksTab) {
            for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
                if (t == EntityType.PLAYER) continue;
                all.add(t);
                keys.add(BuiltInRegistries.ENTITY_TYPE.getKey(t));
            }
        } else {
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b.defaultBlockState().isAir()) continue;
                all.add(b);
                keys.add(BuiltInRegistries.BLOCK.getKey(b));
            }
        }
        for (Identifier id : keys) if (id != null) ns.add(id.getNamespace());
        sources.clear();
        sources.add("الكل");
        sources.addAll(ns);
        if (srcIdx >= sources.size()) srcIdx = 0;
        for (int i = 0; i < all.size(); i++) {
            Identifier id = keys.get(i);
            if (id == null) continue;
            if (srcIdx != 0 && !id.getNamespace().equals(sources.get(srcIdx))) continue;
            Object o = all.get(i);
            items.add(o);
            names.add(o instanceof EntityType<?> t ? t.getDescription().getString() : ((Block) o).getName().getString());
            ids.add(id.toString());
        }
    }

    @Override protected int headerExtra() { return 52; }
    @Override protected int count() { return items.size(); }

    private float[] btn(int k) {
        // 0 كائنات، 1 بلوكات، 2 عودة، 3 الوضع، 4 المصدر
        float x = px + 6, w = pw - 12, y = py + 26;
        return switch (k) {
            case 0 -> new float[]{x, y, w * 0.3f - 2, 22};
            case 1 -> new float[]{x + w * 0.3f, y, w * 0.3f - 2, 22};
            case 2 -> new float[]{x + w * 0.6f, y, w * 0.4f, 22};
            case 3 -> new float[]{x, y + 25, w * 0.55f - 2, 22};
            default -> new float[]{x + w * 0.55f, y + 25, w * 0.45f, 22};
        };
    }

    @Override
    protected void drawHeader(GuiGraphics g, float x, float y, float w, int mx, int my) {
        String[] labels = {"الكائنات (", "البلوكات", Morph.active() ? "عودة لشكلي" : "لا تحول حالياً",
            Cfg.I.morphFull ? "الوضع: تحول كامل" : "الوضع: قدرات فقط", "المصدر: " + sources.get(srcIdx)};
        labels[0] = "الكائنات " + (blocksTab ? "" : String.valueOf(items.size())) + "";
        if (blocksTab) labels[1] = "البلوكات " + items.size();
        for (int k = 0; k < 5; k++) {
            float[] r = btn(k);
            boolean green = (k == 0 && !blocksTab) || (k == 1 && blocksTab) || (k == 3 && Cfg.I.morphFull);
            Ui.button(g, r[0], r[1], r[2], r[3], false, false, green, 1f);
            float sc = Math.min(0.95f, (r[2] - 6) / Math.max(1, Ui.tw(labels[k])));
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
                scroll = 0;
                build();
                relayout();
                return true;
            }
        }
        return false;
    }

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
            float s = (w * 0.52f) / 16f;
            g.pose().pushMatrix();
            g.pose().translate(x + w / 2f - 8 * s, y + h * 0.34f - 8 * s);
            g.pose().scale(s, s);
            g.renderItem(st, 0, 0);
            g.pose().popMatrix();
        } else {
            // لا توجد بيضة/أيقونة: أيقونة بديلة واضحة بدل خانة فارغة
            Ui.icon(g, items.get(i) instanceof EntityType<?> ? "paw" : "block",
                x + w / 2f, y + h * 0.34f, w * 0.30f, Ui.al(0xB1B2B5, 1f));
        }
        String nm = names.get(i);
        float sc = Math.min(0.72f * Math.max(1f, Cfg.I.uiText), (w - 3) / Math.max(1, Ui.tw(nm)));
        Ui.text(g, nm, x + w / 2f, y + h * 0.72f, Ui.TEXT, sc, true, false);
    }

    @Override
    protected void activate(int i, double x, double y) {
        Object o = items.get(i);
        boolean ok;
        if (o instanceof EntityType<?> t) ok = Morph.setMob(t, Cfg.I.morphFull);
        else ok = Morph.setBlock((Block) o, Cfg.I.morphFull);
        if (ok) onClose();
    }

    @Override protected int footerH() { return 16; }

    @Override
    protected void drawFooter(GuiGraphics g, float x, float y, float w, int hot) {
        String s = hot >= 0 && hot < names.size()
            ? names.get(hot) + "  •  " + ids.get(hot)
            : "ضغطة واحدة للتحول • اسحب للتمرير • عدد العناصر: " + items.size();
        Ui.text(g, s, x + w / 2f, y + 1, hot >= 0 ? Ui.GOLD : Ui.GRAY,
            Math.min(0.8f * Math.max(1f, Cfg.I.uiText), w / Math.max(1, Ui.tw(s))), true, false);
    }
}
