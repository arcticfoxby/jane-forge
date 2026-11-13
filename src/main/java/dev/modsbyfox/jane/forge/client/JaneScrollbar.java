package dev.modsbyfox.jane.forge.client;

import net.minecraft.client.gui.GuiGraphics;

/** Mouse and wheel independent scrollbar state for Jane's custom lists. */
final class JaneScrollbar {
    private static final int WIDTH = 7;
    private boolean dragging;
    private int grabOffset;

    static void draw(GuiGraphics graphics, int x, int top, int bottom, int contentHeight, int scroll) {
        int view = bottom - top;
        if (view <= 0 || contentHeight <= view) return;
        int thumb = thumbHeight(view, contentHeight);
        int thumbY = thumbTop(top, view, contentHeight, scroll);
        graphics.fill(x, top, x + WIDTH, bottom, 0xFF444444);
        graphics.fill(x, thumbY, x + WIDTH, thumbY + thumb, 0xFFAAAAAA);
    }

    int click(double mouseX, double mouseY, int button, int x, int top, int bottom,
              int contentHeight, int scroll) {
        int view = bottom - top;
        if (button != 0 || view <= 0 || contentHeight <= view
                || mouseX < x - 2 || mouseX >= x + WIDTH + 3 || mouseY < top || mouseY >= bottom)
            return -1;
        int thumb = thumbHeight(view, contentHeight);
        int thumbY = thumbTop(top, view, contentHeight, scroll);
        grabOffset = mouseY >= thumbY && mouseY < thumbY + thumb ? (int) mouseY - thumbY : thumb / 2;
        dragging = true;
        return scrollForPointer((int) mouseY, top, bottom, contentHeight, grabOffset);
    }

    int drag(double mouseY, int top, int bottom, int contentHeight, int currentScroll) {
        if (!dragging) return currentScroll;
        int view = bottom - top;
        if (view <= 0 || contentHeight <= view) {
            dragging = false;
            return 0;
        }
        return scrollForPointer((int) mouseY, top, bottom, contentHeight, grabOffset);
    }

    boolean dragging() { return dragging; }

    boolean release(int button) {
        if (button != 0 || !dragging) return false;
        dragging = false;
        return true;
    }

    void reset() { dragging = false; }

    static int scrollForPointer(int mouseY, int top, int bottom, int contentHeight, int grabOffset) {
        int view = Math.max(0, bottom - top);
        int maxScroll = Math.max(0, contentHeight - view);
        int travel = view - thumbHeight(view, Math.max(view, contentHeight));
        if (maxScroll == 0 || travel <= 0) return 0;
        int position = Math.max(0, Math.min(travel, mouseY - top - grabOffset));
        return (int) Math.round(position * (double) maxScroll / travel);
    }

    private static int thumbHeight(int view, int contentHeight) {
        if (view <= 0) return 0;
        return Math.min(view, Math.max(12, view * view / Math.max(1, contentHeight)));
    }

    private static int thumbTop(int top, int view, int contentHeight, int scroll) {
        int maxScroll = contentHeight - view;
        int thumb = thumbHeight(view, contentHeight);
        int clamped = Math.max(0, Math.min(scroll, maxScroll));
        return top + clamped * (view - thumb) / maxScroll;
    }
}

