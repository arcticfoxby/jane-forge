package dev.modsbyfox.jane.forge.client;

import dev.modsbyfox.jane.core.ClientCompatibilityReport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Separate, explicit confirmation for extra JARs that may be needed by other Mods. */
final class ClientUniversalDisableConfirmScreen extends Screen {
    private static final int LINE_HEIGHT = 9;
    private record Line(FormattedCharSequence text, int color) { }

    private final CompatibilityReviewScreen parent;
    private final List<ClientCompatibilityReport.ExtraMod> chosen;
    private final List<ClientCompatibilityReport.ExtraMod> universal;
    private final JaneScrollbar scrollbar = new JaneScrollbar();
    private int scroll;
    private Button confirmButton;

    ClientUniversalDisableConfirmScreen(CompatibilityReviewScreen parent,
                                        List<ClientCompatibilityReport.ExtraMod> chosen) {
        super(Component.translatable("jane.compat.universal_confirm_title"));
        this.parent = parent;
        this.chosen = List.copyOf(chosen);
        this.universal = chosen.stream().filter(mod ->
                mod.kind() == ClientCompatibilityReport.Kind.OTHER_EXTRA).toList();
        if (universal.isEmpty()) throw new IllegalArgumentException("No universal Mod selected");
    }

    private ClientUniversalDisableLayout layout() { return ClientUniversalDisableLayout.forSize(width, height); }

    @Override protected void init() {
        scrollbar.reset();
        ClientUniversalDisableLayout layout = layout();
        int x = (width - layout.buttonWidth()) / 2;
        String confirmKey = width < 220 ? "jane.compat.universal_confirm_tiny"
                : width < 300 ? "jane.compat.universal_confirm_short" : "jane.compat.universal_confirm";
        confirmButton = addRenderableWidget(JaneUiText.button(Component.translatable(confirmKey), button -> {
            if (!parent.confirmUniversalDisable(chosen)) {
                minecraft.setScreen(parent);
                return;
            }
            minecraft.setScreen(parent);
            parent.disableSelected(chosen);
        }).bounds(x, layout.confirmY(), layout.buttonWidth(), 20).build());
        confirmButton.active = false;
        addRenderableWidget(JaneUiText.button(Component.translatable("jane.compat.universal_cancel"), button -> onClose())
                .bounds(x, layout.cancelY(), layout.buttonWidth(), 20).build());
    }

    private List<Line> lines() {
        List<Line> result = new ArrayList<>();
        addWrapped(result, Component.translatable("jane.compat.universal_scroll_hint"), 0xAAAAAA);
        result.add(new Line(null, 0));
        addWrapped(result, Component.translatable("jane.compat.universal_explain1"), 0xFFCC77);
        result.add(new Line(null, 0));
        addWrapped(result, Component.translatable("jane.compat.universal_explain2"), 0xCCCCCC);
        result.add(new Line(null, 0));
        for (var mod : universal) {
            addWrapped(result, Component.literal(mod.displayName()), 0xFFFFFF);
            addWrapped(result, Component.literal(mod.filename()), 0xAAAAAA);
            result.add(new Line(null, 0));
        }
        return result;
    }

    private void addWrapped(List<Line> result, Component text, int color) {
        for (FormattedCharSequence line : JaneUiText.split(font, text, Math.max(60, width - 40)))
            result.add(new Line(line, color));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        renderBackground(graphics);
        ClientUniversalDisableLayout layout = layout();
        JaneUiText.centered(graphics, font, Component.translatable(width < 300
                ? "jane.compat.universal_confirm_title_short" : "jane.compat.universal_confirm_title"),
                width / 2, 8, 0xFFFFFF);
        JaneUiText.centered(graphics, font, Component.translatable("jane.compat.universal_counts",
                universal.size(), chosen.size()), width / 2, width < 300 ? 20 : 26, 0xFFCC77);
        List<Line> rows = lines();
        int view = Math.max(1, layout.viewHeight());
        int contentHeight = rows.size() * LINE_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - view);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        confirmButton.active = layout.readable() && scroll >= maxScroll;
        if (layout.viewHeight() > 0) {
            graphics.enableScissor(12, layout.contentTop(), width - 12, layout.contentBottom());
            int y = layout.contentTop() - scroll;
            for (Line line : rows) {
                if (line.text() != null && y + LINE_HEIGHT > layout.contentTop() && y < layout.contentBottom())
                    JaneUiText.draw(graphics, font, line.text(), 16, y, line.color());
                y += LINE_HEIGHT;
            }
            graphics.disableScissor();
            JaneScrollbar.draw(graphics, width - 19, layout.contentTop(), layout.contentBottom(),
                    contentHeight, scroll);
        }
        if (!layout.readable())
            JaneUiText.centered(graphics, font, Component.translatable(width < 170
                            ? "jane.compat.enlarge_window_short" : "jane.compat.enlarge_window"),
                    width / 2, Math.max(26, layout.contentTop()), 0xFF7777);
        super.render(graphics, mouseX, mouseY, tick);
    }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        ClientUniversalDisableLayout layout = layout();
        if (y < layout.contentTop() || y >= layout.contentBottom()) return super.mouseScrolled(x, y, amount);
        scroll -= (int) Math.round(amount * 22);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines().size() * LINE_HEIGHT - layout.viewHeight())));
        return true;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        ClientUniversalDisableLayout layout = layout();
        int next = scrollbar.click(x, y, button, width - 19, layout.contentTop(), layout.contentBottom(),
                lines().size() * LINE_HEIGHT, scroll);
        if (next >= 0) {
            scroll = next;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && scrollbar.dragging()) {
            ClientUniversalDisableLayout layout = layout();
            scroll = scrollbar.drag(y, layout.contentTop(), layout.contentBottom(),
                    lines().size() * LINE_HEIGHT, scroll);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (scrollbar.release(button)) return true;
        return super.mouseReleased(x, y, button);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}

