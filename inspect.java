import java.lang.reflect.Method;
public class inspect {
    public static void main(String[] args) throws Exception {
        Class<?> c1 = Class.forName("net.minecraft.client.gui.GuiGraphics");
        System.out.println("GuiGraphics methods:");
        for (Method m : c1.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("color") || m.getName().toLowerCase().contains("pose")) {
                System.out.println("  " + m.toString());
            }
        }
        Class<?> c2 = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
        System.out.println("RenderSystem methods:");
        for (Method m : c2.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("color")) {
                System.out.println("  " + m.toString());
            }
        }
        Class<?> c3 = Class.forName("net.minecraft.client.renderer.RenderType");
        System.out.println("RenderType methods:");
        for (Method m : c3.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("gui")) {
                System.out.println("  " + m.toString());
            }
        }
    }
}
