package com.codexceed.xmusic;
import net.minecraft.client.gui.GuiGraphics;
import java.lang.reflect.Method;
public class TestBlit {
    public static void main(String[] args) {
        for (Method m : GuiGraphics.class.getMethods()) {
            if (m.getName().equals("blit")) {
                System.out.println("blit" + java.util.Arrays.toString(m.getParameterTypes()));
            }
        }
    }
}
