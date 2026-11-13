package dev.modsbyfox.jane.forge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SelectionScreenLayoutTest {
    @Test void splitSelectionStaysInRightPane() {
        SplitSyncLayout split = SplitSyncLayout.forSize(512, 276);
        SelectionScreenLayout selection = SelectionScreenLayout.forBounds(
                split.rightLeft(), split.rightRight(), 276);

        assertEquals(132, selection.left());
        assertEquals(494, selection.right());
        assertTrue(selection.left() > split.leftRight());
        assertEquals(split.rightRight(), selection.right() + 10);
        assertEquals(split.rightCenter() - 5, selection.centerX());
        assertFalse(selection.containsX(split.leftRight()));
        assertTrue(selection.containsX(split.rightRight() - 1));
        assertFalse(selection.containsX(split.rightRight()));
        assertTrue(selection.listBottom() < split.height());
    }

    @Test void narrowPaneUsesCompactHeadings() {
        SelectionScreenLayout selection = SelectionScreenLayout.forBounds(132, 360, 276);
        assertTrue(selection.compact());
        assertEquals(19, selection.declarationTop());
        assertEquals(43, selection.declarationBottom());
    }

    @Test void fixedDeclarationFitsThreeLinesWithoutShrinkingModList() {
        SelectionScreenLayout selection = SelectionScreenLayout.forBounds(132, 504, 276)
                .fitDeclaration(3);
        assertEquals(77, selection.declarationBottom());
        assertEquals(80, selection.listTop());
        assertTrue(selection.listHeight() > 100);
    }

    @Test void fixedDeclarationKeepsAModListOnNarrowScreens() {
        SelectionScreenLayout selection = SelectionScreenLayout.forSize(250, 190)
                .fitDeclaration(20);
        assertEquals(44, selection.listHeight());
    }
}
