package dev.lamsa.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** يسمح بتسجيل "نقرة" على أي زر (حتى لو كان بلا مفتاح) ليعمل مع كل المودات. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int lamsa$getClickCount();

    @Accessor("clickCount")
    void lamsa$setClickCount(int value);
}
