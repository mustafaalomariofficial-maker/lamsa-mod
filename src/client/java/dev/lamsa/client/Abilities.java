package dev.lamsa.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * قدرات التحول. كل القدرات تُطلب من الخادم المدمج (عالم لاعب واحد) لأن اللعبة
 * تقرر الضرر والانفجارات على الخادم، لا على العميل. على السيرفرات البعيدة
 * لا يستطيع المود وحده إطلاق كرات نار أو إحداث ضرر عن بعد.
 */
public final class Abilities {

    private Abilities() {}

    private static final double RANGE = 24.0;

    public static boolean ready() {
        return Morph.active() && Morph.mobType != null;
    }

    public static void fire() {
        Minecraft m = Minecraft.getInstance();
        if (m.player == null) return;

        if (Morph.blockState != null) {
            Hud.toast("البلوك ما عنده قدرة — تحول لكائن أولاً");
            return;
        }

        final EntityType<?> type = Morph.mobType;
        if (type == null) {
            Hud.toast("تحول أولاً إلى كائن");
            return;
        }

        MinecraftServer srv = m.getSingleplayerServer();
        if (srv == null) {
            Hud.toast("القدرات الحقيقية تحتاج عالم لاعب واحد (الخادم المدمج)");
            return;
        }

        HitResult hr = m.hitResult;
        final Entity target = hr instanceof EntityHitResult ehr ? ehr.getEntity() : null;
        final java.util.UUID id = m.player.getUUID();

        srv.execute(() -> {
            try {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) return;

                if (type == EntityType.GHAST || type == EntityType.BLAZE || type == EntityType.WITHER) {
                    Vec3 look = sp.getLookAngle();
                    LargeFireball fb = new LargeFireball(sp.level(), sp, look.scale(1.2), 1);
                    fb.setPos(sp.getX() + look.x * 1.2, sp.getEyeY() + look.y * 0.4, sp.getZ() + look.z * 1.2);
                    sp.level().addFreshEntity(fb);
                    return;
                }

                if (target != null && !target.isRemoved() && target.distanceTo(sp) <= RANGE) {
                    sp.attack(target);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        Hud.toast("قدرة: " + type.getDescription().getString());
    }
}
