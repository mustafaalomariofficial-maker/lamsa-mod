package dev.lamsa.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.UUID;

/**
 * التحول إلى أي كائن أو بلوك. كل شيء يُقرأ من سجلات اللعبة وكودها مباشرة:
 * النموذج، الحجم، والسمات (الصحة، السرعة، الهجوم، الارتفاع...) وقدرات مثل الطيران
 * والتنفس تحت الماء ومقاومة النار، وعوامل البلوكات (السرعة/القفز/مقاومة الانفجار).
 */
public final class Morph {
    private Morph() {}

    static EntityType<?> mobType;
    static BlockState blockState;
    static Entity dummy;
    static boolean full = true;
    public static boolean locked;
    static UUID uuid;
    static EntityRenderState markedState;
    static Object lastPlayer;
    static boolean addedWater, addedFire, addedRes;

    static final Identifier MOD_ID = Identifier.fromNamespaceAndPath("lamsa", "morph");
    @SuppressWarnings("unchecked")
    static final List<Holder<Attribute>> ATTRS = List.of(
        Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED, Attributes.ATTACK_DAMAGE,
        Attributes.STEP_HEIGHT, Attributes.JUMP_STRENGTH);

    static Minecraft mc() { return Minecraft.getInstance(); }

    public static boolean active() { return mobType != null || blockState != null; }

    public static boolean isMorphed(Player p) {
        return full && active() && uuid != null && p.getUUID().equals(uuid);
    }

    public static EntityDimensions dimensions() {
        if (blockState != null) return EntityDimensions.fixed(0.98f, 0.98f).withEyeHeight(0.5f);
        if (mobType != null) return mobType.getDimensions();
        return null;
    }

    // ───────────────────────── الاختيار ─────────────────────────
    public static boolean setMob(EntityType<?> type, boolean fullMode) {
        Minecraft m = mc();
        if (m.player == null || m.level == null) return false;
        Entity d = null;
        try { d = type.create(m.level, EntitySpawnReason.COMMAND); } catch (Throwable t) { /* تجاهل */ }
        if (d == null) { Hud.toast("لا يمكن التحول إلى هذا الكائن"); return false; }
        try {
            if (fullMode) m.getEntityRenderDispatcher().extractEntity(d, 1f);
        } catch (Throwable t) {
            Hud.toast("هذا الكائن لا يمكن عرضه كتحول");
            return false;
        }
        mobType = type;
        blockState = null;
        dummy = d;
        full = fullMode;
        locked = false;
        uuid = m.player.getUUID();
        afterChange(true, type.getDescription().getString());
        return true;
    }

    public static boolean setBlock(Block b, boolean fullMode) { return setBlockState(b.defaultBlockState(), fullMode); }

    public static boolean setBlockState(BlockState st, boolean fullMode) {
        Minecraft m = mc();
        if (m.player == null) return false;
        mobType = null;
        dummy = null;
        blockState = st;
        full = fullMode;
        locked = fullMode; // البلوك يبدأ مثبتاً حتى لا يتحرك بالخطأ
        uuid = m.player.getUUID();
        afterChange(true, st.getBlock().getName().getString());
        return true;
    }

    public static void clear() {
        mobType = null;
        blockState = null;
        dummy = null;
        locked = false;
        afterChange(false, null);
    }

    /** يُستدعى عند الخروج من العالم. */
    public static void reset() {
        mobType = null;
        blockState = null;
        dummy = null;
        locked = false;
        uuid = null;
        markedState = null;
    }

    static void afterChange(boolean on, String name) {
        Minecraft m = mc();
        if (m.player != null) {
            m.player.refreshDimensions();
            if (on && full) m.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        applyServer();
        if (on) Hud.toast((full ? "تحولت إلى: " : "أخذت قدرات: ") + name + (locked ? " (مثبّت - اضغط زر القفل للتحرك)" : ""));
    }

    public static void pickFromCrosshair() {
        Minecraft m = mc();
        HitResult hr = m.hitResult;
        if (hr == null || m.level == null) { Hud.toast("وجّه المؤشر إلى كائن أو بلوك"); return; }
        if (hr.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) hr).getEntity();
            if (e instanceof Player) { Hud.toast("لا يمكن التحول للاعب"); return; }
            setMob(e.getType(), Cfg.I.morphFull);
        } else if (hr.getType() == HitResult.Type.BLOCK) {
            BlockState st = m.level.getBlockState(((BlockHitResult) hr).getBlockPos());
            setBlockState(st, Cfg.I.morphFull);
        } else {
            Hud.toast("وجّه المؤشر إلى كائن أو بلوك");
        }
    }

    // ───────────────────────── الرسم ─────────────────────────
    public static Entity swapForRender(Entity entity) {
        Minecraft m = mc();
        if (entity == null || entity != m.player || !full || mobType == null || dummy == null) return entity;
        try {
            sync(m.player, dummy);
            return dummy;
        } catch (Throwable t) {
            return entity;
        }
    }

    public static void markState(Entity entity, EntityRenderState st) {
        Minecraft m = mc();
        if (full && blockState != null && entity != null && entity == m.player) markedState = st;
    }

    public static boolean renderBlock(EntityRenderState state, double x, double y, double z, PoseStack ps, SubmitNodeCollector c) {
        if (blockState == null || !full || state == null || state != markedState) return false;
        try {
            ps.pushPose();
            ps.translate(x - 0.5, y, z - 0.5);
            c.submitBlock(ps, blockState, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            ps.popPose();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static void sync(Player p, Entity d) {
        d.setPos(p.getX(), p.getY(), p.getZ());
        d.xo = p.xo; d.yo = p.yo; d.zo = p.zo;
        d.xOld = p.xOld; d.yOld = p.yOld; d.zOld = p.zOld;
        d.setYRot(p.getYRot()); d.yRotO = p.yRotO;
        d.setXRot(p.getXRot()); d.xRotO = p.xRotO;
        d.tickCount = p.tickCount;
        d.setOnGround(p.onGround());
        d.setShiftKeyDown(p.isShiftKeyDown());
        if (d instanceof LivingEntity le) {
            le.yBodyRot = p.yBodyRot; le.yBodyRotO = p.yBodyRotO;
            le.yHeadRot = p.yHeadRot; le.yHeadRotO = p.yHeadRotO;
            le.swinging = p.swinging;
            le.swingTime = p.swingTime;
            le.attackAnim = p.attackAnim;
            le.oAttackAnim = p.oAttackAnim;
            le.setSprinting(p.isSprinting());
            le.hurtTime = p.hurtTime;
            le.deathTime = p.deathTime;
        }
    }

    // ───────────────────────── التحديث ─────────────────────────
    public static void tick() {
        Minecraft m = mc();
        if (m.player == null || m.level == null) return;
        if (lastPlayer != m.player) {
            lastPlayer = m.player;
            if (active()) {
                uuid = m.player.getUUID();
                m.player.refreshDimensions();
                applyServer();
            }
        }
        if (!active() || mobType == null) return;
        if (dummy == null || dummy.level() != m.level) {
            try { dummy = mobType.create(m.level, EntitySpawnReason.COMMAND); } catch (Throwable t) { dummy = null; }
            if (dummy == null) return;
        }
        if (dummy instanceof LivingEntity le) {
            Player p = m.player;
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            float f = (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz) * 4.0);
            le.walkAnimation.update(f, 0.4f, 1.0f);
        }
    }

    // ───────────────────────── القدرات (في العالم المحلي/الخادم المدمج) ─────────────────────────
    static boolean canFly(Entity d, EntityType<?> t) {
        if (d instanceof FlyingAnimal) return true;
        if (d instanceof Mob mob && mob.getMoveControl() instanceof FlyingMoveControl) return true;
        return t == EntityType.BAT || t == EntityType.GHAST || t == EntityType.PHANTOM || t == EntityType.VEX
            || t == EntityType.BLAZE || t == EntityType.ENDER_DRAGON || t == EntityType.ALLAY
            || t == EntityType.BEE || t == EntityType.PARROT || t == EntityType.WITHER;
    }

    static void applyServer() {
        Minecraft m = mc();
        MinecraftServer srv = m.getSingleplayerServer();
        if (srv == null || m.player == null) return;
        UUID id = m.player.getUUID();
        EntityType<?> t = mobType;
        BlockState bs = blockState;
        Entity d = dummy;
        srv.execute(() -> {
            try {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp != null) applyTo(sp, t, bs, d);
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        });
    }

    static void applyTo(ServerPlayer sp, EntityType<?> t, BlockState bs, Entity d) {
        for (Holder<Attribute> a : ATTRS) {
            AttributeInstance in = sp.getAttribute(a);
            if (in != null) in.removeModifier(MOD_ID);
        }
        if (addedWater) { sp.removeEffect(MobEffects.WATER_BREATHING); addedWater = false; }
        if (addedFire) { sp.removeEffect(MobEffects.FIRE_RESISTANCE); addedFire = false; }
        if (addedRes) { sp.removeEffect(MobEffects.RESISTANCE); addedRes = false; }
        if (t == null && bs == null) {
            sp.gameMode.getGameModeForPlayer().updatePlayerAbilities(sp.getAbilities());
            sp.onUpdateAbilities();
            sp.refreshDimensions();
            return;
        }
        if (t != null && d instanceof LivingEntity le) {
            for (Holder<Attribute> a : ATTRS) {
                if (!le.getAttributes().hasAttribute(a)) continue;
                AttributeInstance in = sp.getAttribute(a);
                if (in == null) continue;
                double amount = le.getAttributeValue(a) - in.getBaseValue();
                if (Math.abs(amount) > 1e-6) {
                    in.addOrReplacePermanentModifier(new AttributeModifier(MOD_ID, amount, AttributeModifier.Operation.ADD_VALUE));
                }
            }
            if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
            if (le.canBreatheUnderwater()) {
                sp.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
                addedWater = true;
            }
        }
        if (t != null) {
            if (t.fireImmune()) {
                sp.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
                addedFire = true;
            }
            if (canFly(d, t)) {
                sp.getAbilities().mayfly = true;
                sp.onUpdateAbilities();
            }
        }
        if (bs != null) {
            Block b = bs.getBlock();
            float sf = b.getSpeedFactor(), jf = b.getJumpFactor();
            addMul(sp, Attributes.MOVEMENT_SPEED, sf - 1.0);
            addMul(sp, Attributes.JUMP_STRENGTH, jf - 1.0);
            if (b.getExplosionResistance() >= 1200f || b.defaultDestroyTime() < 0) {
                sp.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, true, false, false));
                addedRes = true;
            }
        }
        sp.refreshDimensions();
    }

    static void addMul(ServerPlayer sp, Holder<Attribute> a, double amount) {
        if (Math.abs(amount) < 1e-6) return;
        AttributeInstance in = sp.getAttribute(a);
        if (in != null) in.addOrReplacePermanentModifier(new AttributeModifier(MOD_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
