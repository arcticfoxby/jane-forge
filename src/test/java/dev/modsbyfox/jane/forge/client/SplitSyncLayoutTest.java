package dev.modsbyfox.jane.forge.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SplitSyncLayoutTest {
    @Test void screenshotSizeUsesLeftRailAndRightListWithoutOverlap() {
        assertTrue(SplitSyncLayout.enabled(512, 276));
        SplitSyncLayout layout = SplitSyncLayout.forSize(512, 276);
        assertEquals(124, layout.dividerX());
        assertEquals(8, layout.leftX());
        assertEquals(116, layout.leftRight());
        assertEquals(132, layout.rightLeft());
        assertEquals(504, layout.rightRight());
        assertEquals(204, layout.actionTop(false));
        assertEquals(204, layout.primaryY());
        assertEquals(228, layout.detailsY());
        assertEquals(252, layout.cancelY());
        assertEquals(24, layout.detailsY() - layout.primaryY());
        assertEquals(24, layout.cancelY() - layout.detailsY());
        assertTrue(layout.noticeY(80, 2, false) >= 80);
        assertTrue(layout.noticeY(80, 2, false) + 11 + 9 < layout.actionTop(false));
        assertTrue(layout.quickButtonX(1) + layout.quickButtonWidth() <= layout.leftRight());
    }

    @Test void retryGetsItsOwnRowAndNoticeMovesAboveIt() {
        SplitSyncLayout layout = SplitSyncLayout.forSize(512, 276);
        assertEquals(180, layout.retryY());
        assertEquals(180, layout.actionTop(true));
        assertEquals(204, layout.primaryY());
        assertEquals(24, layout.primaryY() - layout.retryY());
        assertTrue(layout.noticeY(80, 2, true) >= 80);
        assertTrue(layout.noticeY(80, 2, true) + 11 + 9 < layout.actionTop(true));
    }

    @Test void widerAndSmallerScreensKeepBoundaries() {
        SplitSyncLayout wide = SplitSyncLayout.forSize(854, 480);
        assertEquals(180, wide.dividerX());
        assertTrue(wide.rightLeft() > wide.leftRight());
        assertTrue(wide.quickHintY(2) >= 0);
        assertTrue(SplitSyncLayout.enabled(480, 250));
        assertFalse(SplitSyncLayout.enabled(479, 276));
        assertFalse(SplitSyncLayout.enabled(512, 249));
        assertFalse(SplitSyncLayout.enabled(320, 180));
    }
}
