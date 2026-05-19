package com.codexceed.xmusic.gui.layout;

public final class GuiFrame {
    public static final int MIN_WIDTH = 520;
    public static final int MIN_HEIGHT = 320;
    public static final int TOP_BAR_HEIGHT = 22;
    public static final int PLAYER_BAR_HEIGHT = 74;
    public static final int SIDEBAR_WIDTH = 96;
    public static final int SIDEBAR_COMPACT_WIDTH = 60;
    public static final int MARGIN = 6;
    public static final int GAP = 4;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final boolean compact;

    private GuiFrame(int x, int y, int width, int height, boolean compact) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.compact = compact;
    }

    public static GuiFrame calculate(int screenWidth, int screenHeight) {
        int width = Math.max(MIN_WIDTH, Math.min(screenWidth - 12, (int) (screenWidth * 0.86f)));
        int height = Math.max(MIN_HEIGHT, Math.min(screenHeight - 12, (int) (screenHeight * 0.78f)));
        width = Math.min(width, screenWidth - 6);
        height = Math.min(height, screenHeight - 6);
        return new GuiFrame((screenWidth - width) / 2, (screenHeight - height) / 2, width, height, width < 560);
    }

    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean compact() { return compact; }

    public int topBarX() { return x + MARGIN; }
    public int topBarY() { return y + MARGIN; }
    public int topBarWidth() { return width - MARGIN * 2; }
    public int topBarHeight() { return TOP_BAR_HEIGHT; }

    public int sidebarX() { return x + MARGIN; }
    public int sidebarY() { return topBarY() + TOP_BAR_HEIGHT + GAP; }
    public int sidebarWidth() { return compact ? SIDEBAR_COMPACT_WIDTH : SIDEBAR_WIDTH; }
    public int sidebarHeight() { return height - TOP_BAR_HEIGHT - PLAYER_BAR_HEIGHT - MARGIN * 2 - GAP * 2; }

    public int contentX() { return sidebarX() + sidebarWidth() + GAP; }
    public int contentY() { return sidebarY(); }
    public int contentWidth() { return x + width - MARGIN - contentX(); }
    public int contentHeight() { return sidebarHeight(); }

    public int playerX() { return x + MARGIN; }
    public int playerY() { return y + height - MARGIN - PLAYER_BAR_HEIGHT; }
    public int playerWidth() { return width - MARGIN * 2; }
    public int playerHeight() { return PLAYER_BAR_HEIGHT; }
}
