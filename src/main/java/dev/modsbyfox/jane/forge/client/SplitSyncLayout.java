package dev.modsbyfox.jane.forge.client;

/** Bounds for the wide sync view; positions are Minecraft GUI pixels, not window pixels. */
record SplitSyncLayout(int width, int height, int dividerX) {
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    static boolean enabled(int width, int height) {
        return width >= 480 && height >= 250;
    }

    static SplitSyncLayout forSize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid screen size");
        int divider = Math.max(124, Math.min(180, Math.round(width * 0.24F)));
        return new SplitSyncLayout(width, height, divider);
    }

    int leftX() { return 8; }
    int leftRight() { return dividerX - 8; }
    int leftWidth() { return leftRight() - leftX(); }
    int leftCenter() { return (leftX() + leftRight()) / 2; }
    int rightLeft() { return dividerX + 8; }
    int rightRight() { return width - 8; }
    int rightWidth() { return rightRight() - rightLeft(); }
    int rightCenter() { return (rightLeft() + rightRight()) / 2; }

    int retryY() { return height - 96; }
    int primaryY() { return height - 72; }
    int detailsY() { return height - 48; }
    int cancelY() { return height - 24; }
    int actionTop(boolean retryVisible) { return retryVisible ? retryY() : primaryY(); }

    /** Position complete notice lines before the first action, or report insufficient space. */
    int noticeY(int minimumY, int lines, boolean retryVisible) {
        if (lines <= 0) return -1;
        int lastAllowed = actionTop(retryVisible) - 12 - 9 - (lines - 1) * 11;
        if (minimumY > lastAllowed) return -1;
        int preferred = height - 116 - (lines - 1) * 11;
        return Math.max(minimumY, Math.min(preferred, lastAllowed));
    }

    int barWidth() { return Math.min(90, leftWidth() - 12); }
    int barX(int barWidth) { return leftCenter() - Math.min(barWidth, leftWidth()) / 2; }

    int quickButtonWidth() { return (leftWidth() - BUTTON_GAP) / 2; }
    int quickButtonX(int slot) {
        if (slot < 0 || slot > 1) throw new IllegalArgumentException("Invalid quick route slot");
        return leftX() + slot * (quickButtonWidth() + BUTTON_GAP);
    }
    int quickButtonsY() { return Math.max(107, actionTop(false) - 60); }
    int quickHeaderY() { return quickButtonsY() - 14; }
    int quickHintY(int lines) {
        if (lines <= 0) return -1;
        int belowButtons = quickButtonsY() + BUTTON_HEIGHT + 5;
        return belowButtons + (lines - 1) * 11 + 9 <= actionTop(false) - 2 ? belowButtons : -1;
    }
}
