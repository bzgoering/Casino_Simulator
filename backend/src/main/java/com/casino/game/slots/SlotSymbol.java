package com.casino.game.slots;

/** Symbols on the reel strip, in classic three-reel order. */
public enum SlotSymbol {
    CHERRY("Cherry"),
    ORANGE("Orange"),
    PLUM("Plum"),
    BELL("Bell"),
    BAR1("Bar"),
    BAR2("Double Bar"),
    BAR3("Triple Bar"),
    SEVEN("Seven");

    private final String displayName;

    SlotSymbol(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isBar() {
        return this == BAR1 || this == BAR2 || this == BAR3;
    }
}
