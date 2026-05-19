package com.codexceed.xmusic.gui;

public enum GuiRoute {
    HOME("Home"),
    SEARCH("Search"),
    LIBRARY("Library"),
    DOWNLOADS("Downloads"),
    SETTINGS("Settings");

    private final String label;

    GuiRoute(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
