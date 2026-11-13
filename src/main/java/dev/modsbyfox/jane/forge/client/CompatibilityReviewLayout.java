package dev.modsbyfox.jane.forge.client;

/** Fixed declaration above a Mod list, with actions beside the list when space allows. */
record CompatibilityReviewLayout(int declarationTop, int declarationBottom, int declarationContentHeight,
                                 int listLeft, int listRight, int listTop, int listBottom,
                                 int buttonX, int buttonWidth, int buttonTop) {
    static CompatibilityReviewLayout forSize(int width, int height, int declarationHeight) {
        int margin = 8;
        int declarationTop = 8;
        int buttonTop = height - 76;
        boolean sideBySide = width >= 270;
        int buttonWidth = sideBySide ? Math.min(220, Math.max(84, (width - 32) / 3))
                : Math.max(40, Math.min(240, width - margin * 2));
        int buttonX = sideBySide ? margin : (width - buttonWidth) / 2;
        int listLeft = sideBySide ? buttonX + buttonWidth + 8 : margin;
        int listRight = width - margin - 8;
        int listBottom = sideBySide ? height - margin : buttonTop - 8;
        int maxDeclarationBottom = Math.min(Math.min(buttonTop - 8, listBottom - 30), height / 3);
        int declarationBottom = Math.min(declarationTop + Math.max(0, declarationHeight),
                Math.max(declarationTop + 9, maxDeclarationBottom));
        int listTop = declarationBottom + 8;
        return new CompatibilityReviewLayout(declarationTop, declarationBottom, declarationHeight,
                listLeft, listRight, listTop, listBottom, buttonX, buttonWidth, buttonTop);
    }

    int declarationScrollLimit() {
        return Math.max(0, declarationContentHeight - (declarationBottom - declarationTop));
    }

    int listRowY(int rowOffset, int scroll) { return listTop + rowOffset - scroll; }
    int listHeight() { return listBottom - listTop; }

    static int scrollForThumb(int mouseY, int trackTop, int trackBottom, int thumbHeight,
                              int contentHeight, int grabOffset) {
        int view = Math.max(0, trackBottom - trackTop);
        int maxScroll = Math.max(0, contentHeight - view);
        int travel = Math.max(0, view - Math.max(0, thumbHeight));
        if (maxScroll == 0 || travel == 0) return 0;
        int position = Math.max(0, Math.min(travel, mouseY - trackTop - grabOffset));
        return (int) Math.round(position * (double) maxScroll / travel);
    }
}

