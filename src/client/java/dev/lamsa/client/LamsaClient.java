package dev.lamsa.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class LamsaClient implements ClientModInitializer {
    public static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        Cfg.load();

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lamsa", "main"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lamsa.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, cat));
        KeyMapping editKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lamsa.edit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, cat));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            NativeTouch.setActive(Cfg.I.enabled && Cfg.I.nativeTouch);
            Touch.applyGuiScale();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) Touch.setEnabled(!Cfg.I.enabled);
            while (editKey.consumeClick()) {
                if (Cfg.I.enabled && client.screen == null) { Touch.editMode = !Touch.editMode; Touch.selected = null; }
            }
            Touch.tick();
            Morph.tick();
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("lamsa", "touch_hud"), Hud::render);

        // شريط الأدوات فوق كل الشاشات + دعم الماوس لشريط الأدوات
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            ScreenMouseEvents.allowMouseClick(screen).register((s, ev) -> !Toolbar.click(ev.x(), ev.y()));
            ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> Toolbar.render(g, s));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Morph.reset();
            Touch.cancelAll();
        });
    }
}
