package com.codexceed.xmusic.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Screen.class)
public interface ScreenAccessor {
    @Accessor("title")
    Component getTitle();

    @Accessor("title")
    void setTitle(Component title);
}
