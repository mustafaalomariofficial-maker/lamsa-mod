package dev.lamsa.client.mixin;

import dev.lamsa.client.Touch;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    /** في وضع اللمس لا نحبس المؤشر حتى يمكن الضغط على الأزرار بالماوس أيضاً. */
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void lamsa$noGrab(CallbackInfo ci) {
        if (Touch.wantFreeCursor()) ci.cancel();
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void lamsa$button(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (info.button() == 0 && Touch.mouseButton(action != 0)) ci.cancel();
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void lamsa$move(long window, double x, double y, CallbackInfo ci) {
        Touch.mouseMove(x, y);
    }
}
