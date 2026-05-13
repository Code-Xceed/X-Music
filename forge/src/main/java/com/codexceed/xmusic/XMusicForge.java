package com.codexceed.xmusic;

import com.codexceed.xmusic.gui.screen.XMusicScreen;
import com.codexceed.xmusic.input.KeyBindings;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.platform.ForgePlatformHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge mod entry point.
 * Registers Forge-specific event bus subscribers and delegates to {@link XMusic}.
 */
@Mod(XMusic.MOD_ID)
public class XMusicForge {

    public XMusicForge() {
        // Common initialization
        XMusic.init(new ForgePlatformHelper());

        // Register mod event bus listeners
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onRegisterKeyMappings);

        // Register game event bus for tick and render events
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Client setup — initialize client systems.
     */
    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            XMusic.initClient();
        });
    }

    /**
     * Register key mappings with Forge.
     */
    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : KeyBindings.getAll()) {
            event.register(key);
        }
    }

    /**
     * Client tick — update audio engine and handle key bindings.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Tick the active playback backend.
        PlayerFacade.getInstance().tick();

        Minecraft client = Minecraft.getInstance();
        while (KeyBindings.OPEN_PLAYER.consumeClick()) {
            if (client.screen instanceof XMusicScreen) {
                client.setScreen(null);
            } else if (client.screen == null) {
                client.setScreen(new XMusicScreen());
            }
        }

        while (KeyBindings.PLAY_PAUSE.consumeClick()) {
            PlayerFacade.getInstance().togglePlayPause();
        }

        while (KeyBindings.NEXT_TRACK.consumeClick()) {
            PlayerFacade.getInstance().next();
        }

        while (KeyBindings.PREV_TRACK.consumeClick()) {
            PlayerFacade.getInstance().previous();
        }
    }

}
