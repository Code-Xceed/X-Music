package com.codexceed.xmusic.mixin;

import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.source.TrackRef;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
        )
    )
    private void redirectTitleRender(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        if ((Object) this instanceof PauseScreen) {
            PlayerState state = PlayerFacade.getInstance().snapshot();
            TrackRef track = state.getCurrentTrack();
            boolean widgetActive = (track != null || state.isPlaying() || state.isPaused());
            if (widgetActive) {
                // Shift the title down below our player widget (widget bottom is at y=46)
                g.drawCenteredString(font, text, x, 52, color);
                return;
            }
        }
        // Draw normally on all other screens or if music player is inactive
        g.drawCenteredString(font, text, x, y, color);
    }
}
