package com.codexceed.xmusic.mixin;

import com.codexceed.xmusic.audio.PlaybackMode;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.gui.screen.XMusicScreen;
import com.codexceed.xmusic.gui.screen.HudEditorScreen;
import com.codexceed.xmusic.hud.HudRenderer;
import com.codexceed.xmusic.input.KeyBindings;
import com.codexceed.xmusic.player.PlayerFacade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept screen rendering and keypress events to render the HUD 
 * and process keybindings globally, even when screens are open.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

#if MC_RENDER_MATRIX_2D
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
#else
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
#endif
        if (Minecraft.getInstance().gui.overlay() != null) {
            return;
        }

        XMusicConfig cfg = ConfigManager.get();
        boolean inGame = (Minecraft.getInstance().level != null);
        if (!inGame && !cfg.playInMainMenu) {
            return;
        }

        Screen thisScreen = (Object) this instanceof Screen ? (Screen) (Object) this : null;
        if (thisScreen == null) {
            return;
        }
        if (thisScreen instanceof com.codexceed.xmusic.gui.screen.XMusicScreen 
                || thisScreen instanceof com.codexceed.xmusic.gui.screen.HudEditorScreen 
                || thisScreen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            return;
        }

        net.minecraft.client.gui.screens.Screen activeScreen = Minecraft.getInstance().gui.screen();
        if (activeScreen instanceof com.codexceed.xmusic.gui.screen.XMusicScreen 
                || activeScreen instanceof com.codexceed.xmusic.gui.screen.HudEditorScreen 
                || activeScreen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            return;
        }

        if (cfg.hudEnabled) {
            HudRenderer.getInstance().getMiniPlayer().render(g, partialTick);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        Screen thisScreen = (Screen) (Object) this;
        int keyCode = event.key(); int scanCode = event.scancode(); int modifiers = event.modifiers();

        // Skip if typing in a text field
        if (thisScreen.getFocused() instanceof EditBox) {
            return;
        }

        // Skip if screen is a known text-editing screen
        String clsName = thisScreen.getClass().getSimpleName();
        if (thisScreen instanceof ChatScreen
                || thisScreen instanceof BookEditScreen
                || thisScreen instanceof SignEditScreen
                || thisScreen instanceof CommandBlockEditScreen
                || clsName.contains("Sign")
                || clsName.contains("Book")
                || clsName.contains("Chat")
                || clsName.contains("Anvil")) {
            return;
        }

        if (KeyBindings.OPEN_PLAYER.matches(event)) {
            Minecraft client = Minecraft.getInstance();
            if (client.gui.screen() instanceof XMusicScreen) {
                ((XMusicScreen) client.gui.screen()).closeAnimated();
            } else {
                client.setScreenAndShow(new XMusicScreen(client.gui.screen()));
            }
            cir.setReturnValue(true);
            return;
        }

        if (KeyBindings.PLAY_PAUSE.matches(event)) {
            PlayerFacade.getInstance().togglePlayPause();
            cir.setReturnValue(true);
            return;
        }

        if (KeyBindings.NEXT_TRACK.matches(event)) {
            PlayerFacade.getInstance().next();
            cir.setReturnValue(true);
            return;
        }

        if (KeyBindings.PREV_TRACK.matches(event)) {
            PlayerFacade.getInstance().previous();
            cir.setReturnValue(true);
            return;
        }

        if (KeyBindings.VOLUME_UP.matches(event)) {
            PlayerFacade.getInstance().adjustVolume(ConfigManager.get().volumeStep);
            cir.setReturnValue(true);
            return;
        }

        if (KeyBindings.VOLUME_DOWN.matches(event)) {
            PlayerFacade.getInstance().adjustVolume(-ConfigManager.get().volumeStep);
            cir.setReturnValue(true);
            return;
        }

        if (KeyBindings.TOGGLE_SHUFFLE.matches(event)) {
            PlayerFacade facade = PlayerFacade.getInstance();
            if (facade.getPlaybackMode() == PlaybackMode.SHUFFLE) {
                facade.setPlaybackMode(PlaybackMode.SEQUENTIAL);
            } else {
                facade.setPlaybackMode(PlaybackMode.SHUFFLE);
            }
            cir.setReturnValue(true);
            return;
        }

        if (KeyBindings.CYCLE_LOOP.matches(event)) {
            PlayerFacade.getInstance().cycleLoopMode();
            cir.setReturnValue(true);
            return;
        }

        if (KeyBindings.CYCLE_PLAYBACK_MODE.matches(event)) {
            PlayerFacade.getInstance().cyclePlaybackMode();
            cir.setReturnValue(true);
            return;
        }
    }
}


