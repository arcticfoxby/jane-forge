package dev.modsbyfox.jane.forge.client;

import dev.modsbyfox.jane.core.ClientInstallCategory;
import dev.modsbyfox.jane.core.Comparison;
import dev.modsbyfox.jane.core.JaneSyncSession;
import dev.modsbyfox.jane.core.SyncSelection;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Standalone fallback view for narrow layouts. */
final class SyncSelectionScreen extends Screen {
    private final SyncScreen parent;
    private final JaneSyncSession session;
    private final SyncSelection selection;
    private SyncSelectionPanel panel;

    SyncSelectionScreen(SyncScreen parent, JaneSyncSession session, SyncSelection selection) {
        super(Component.translatable("jane.select.screen_title"));
        this.parent = parent;
        this.session = session;
        this.selection = selection;
    }

    @Override protected void init() {
        if (panel == null) panel = new SyncSelectionPanel(parent, session, selection, font);
        panel.resetDrag();
        clearWidgets();
        int buttonWidth = Math.min(150, width - 24);
        addRenderableWidget(JaneUiText.button(Component.translatable("gui.back"), button -> onClose())
                .bounds((width - buttonWidth) / 2, height - 24, buttonWidth, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        panel.render(graphics, SelectionScreenLayout.forSize(width, height), mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return panel.mouseScrolled(mouseX, mouseY, amount, SelectionScreenLayout.forSize(width, height))
                || super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button)
                || panel.mouseClicked(mouseX, mouseY, button, SelectionScreenLayout.forSize(width, height));
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return panel.mouseDragged(mouseX, mouseY, button, dragX, dragY,
                SelectionScreenLayout.forSize(width, height))
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return panel.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}

/** Coordinates remain testable without constructing a Minecraft Screen. */
record SelectionScreenLayout(int left, int right, int declarationTop, int declarationBottom,
                             int listTop, int listBottom, boolean compact) {
    static SelectionScreenLayout forSize(int width, int height) {
        boolean compact = width < 300 || height <= 190;
        int left = Math.max(8, width / 2 - Math.min(270, (width - 26) / 2));
        int declarationTop = compact ? 19 : 34;
        int declarationBottom = compact ? 43 : 75;
        int listTop = declarationBottom + 3;
        return new SelectionScreenLayout(left, width - left - 8,
                declarationTop, declarationBottom, listTop, height - 29, compact);
    }
    static SelectionScreenLayout forBounds(int panelLeft, int panelRight, int height) {
        int left = Math.max(8, panelLeft);
        int right = Math.max(left + 1, panelRight - 10);
        boolean compact = right - left < 300 || height <= 190;
        int declarationTop = compact ? 19 : 34;
        int declarationBottom = compact ? 43 : 75;
        int listTop = declarationBottom + 3;
        return new SelectionScreenLayout(left, right, declarationTop, declarationBottom,
                listTop, height - 29, compact);
    }
    int centerX() { return (left + right) / 2; }
    int textWidth() { return Math.max(0, right - left - 10); }
    boolean containsX(double x) { return x >= left && x < right + 10; }
    int listHeight() { return listBottom - listTop; }

    /** Grow the fixed explanation to fit its wrapped text, while retaining a usable Mod list. */
    SelectionScreenLayout fitDeclaration(int lineCount) {
        int wantedBottom = declarationTop + 13 + Math.max(0, lineCount) * 10;
        int lastBottom = Math.max(declarationBottom, listBottom - 47);
        int bottom = Math.max(declarationBottom, Math.min(wantedBottom, lastBottom));
        return new SelectionScreenLayout(left, right, declarationTop, bottom,
                bottom + 3, listBottom, compact);
    }
}

/** Stable manifest order for the secondary selection groups. */
final class SelectionScreenRows {
    private SelectionScreenRows() { }
    static boolean adjustable(Comparison.Result result, ClientInstallCategory category) {
        return result.status() != Comparison.Status.OK
                && result.status() != Comparison.Status.FILE_ERROR
                && category == ClientInstallCategory.CLIENT_OPTIONAL;
    }
    static List<Comparison.Result> pending(List<Comparison.Result> comparisons,
                                           Map<String, ClientInstallCategory> categories,
                                           ClientInstallCategory category) {
        return comparisons.stream().filter(result -> result.status() != Comparison.Status.OK
                && categories.get(result.required().modId()) == category
                && (category != ClientInstallCategory.CLIENT_OPTIONAL
                || result.status() != Comparison.Status.FILE_ERROR)).toList();
    }
    static List<Comparison.Result> matched(List<Comparison.Result> comparisons) {
        return comparisons.stream().filter(result -> result.status() == Comparison.Status.OK).toList();
    }
}

