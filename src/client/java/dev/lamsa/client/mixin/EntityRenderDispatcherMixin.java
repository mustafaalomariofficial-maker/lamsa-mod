package dev.lamsa.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lamsa.client.Morph;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    /** نستبدل اللاعب بكائن وهمي من نوع الموب المختار ليُرسم بنموذج اللعبة نفسه. */
    @ModifyVariable(method = "extractEntity", at = @At("HEAD"), argsOnly = true)
    private Entity lamsa$swap(Entity entity) {
        return Morph.swapForRender(entity);
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void lamsa$mark(Entity entity, float partial, CallbackInfoReturnable<EntityRenderState> cir) {
        Morph.markState(entity, cir.getReturnValue());
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void lamsa$submit(EntityRenderState state, CameraRenderState cam, double x, double y, double z,
                              PoseStack ps, SubmitNodeCollector collector, CallbackInfo ci) {
        if (Morph.renderBlock(state, x, y, z, ps, collector)) ci.cancel();
    }
}
