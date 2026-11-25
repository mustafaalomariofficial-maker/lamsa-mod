package dev.lamsa.client.mixin;

import dev.lamsa.client.Morph;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    /** حجم اللاعب (والصندوق التصادمي) يصبح حجم الكائن/البلوك المتحول إليه. */
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void lamsa$dims(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object) this instanceof Player p && Morph.isMorphed(p)) {
            EntityDimensions d = Morph.dimensions();
            if (d != null) cir.setReturnValue(d);
        }
    }
}
