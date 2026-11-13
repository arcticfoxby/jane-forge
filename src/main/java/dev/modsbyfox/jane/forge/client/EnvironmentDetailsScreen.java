package dev.modsbyfox.jane.forge.client;

import dev.modsbyfox.jane.core.ClientCompatibilityReport;
import dev.modsbyfox.jane.core.Comparison;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Scrollable local comparison details, deliberately independent of source resolution. */
final class EnvironmentDetailsScreen extends Screen {
    private enum Group { UNMET, MATCHED, EXTRAS }
    private record Row(Group group, Comparison.Result result, ClientCompatibilityReport.ExtraMod extra, int height) { }

    private final Screen parent;
    private final PendingClientAction.EnvironmentDecision action;
    private boolean unmetOpen = true;
    private boolean matchedOpen;
    private boolean extrasOpen = true;
    private int scroll;
    private final JaneScrollbar scrollbar = new JaneScrollbar();

    EnvironmentDetailsScreen(Screen parent, PendingClientAction.EnvironmentDecision action) {
        super(Component.translatable("jane.environment.details_title"));
        this.parent = parent;
        this.action = action;
    }

    @Override protected void init() {
        scrollbar.reset();
        int buttonWidth = Math.min(160, width - 24);
        addRenderableWidget(JaneUiText.button(Component.translatable("gui.back"), button -> onClose())
                .bounds((width - buttonWidth) / 2, height - 27, buttonWidth, 20).build());
    }

    private int left() { return Math.max(10, width / 2 - Math.min(270, (width - 28) / 2)); }
    private int right() { return width - left() - 10; }
    private int top() { return 31; }
    private int bottom() { return Math.max(top() + 1, height - 34); }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Group.UNMET, null, null, 22));
        if (unmetOpen) for (Comparison.Result result : action.context().results())
            if (result.status() != Comparison.Status.OK) rows.add(new Row(Group.UNMET, result, null, 34));
        rows.add(new Row(Group.MATCHED, null, null, 22));
        if (matchedOpen) for (Comparison.Result result : action.context().results())
            if (result.status() == Comparison.Status.OK) rows.add(new Row(Group.MATCHED, result, null, 30));
        rows.add(new Row(Group.EXTRAS, null, null, 22));
        if (extrasOpen) {
            for (ClientCompatibilityReport.ExtraMod extra : action.report().explicitClientMods())
                rows.add(new Row(Group.EXTRAS, null, extra, 30));
            for (ClientCompatibilityReport.ExtraMod extra : action.report().otherExtraMods())
                rows.add(new Row(Group.EXTRAS, null, extra, 30));
        }
        return rows;
    }

    private int count(Group group) {
        return switch (group) {
            case UNMET -> (int) action.context().results().stream()
                    .filter(result -> result.status() != Comparison.Status.OK).count();
            case MATCHED -> (int) action.context().results().stream()
                    .filter(result -> result.status() == Comparison.Status.OK).count();
            case EXTRAS -> action.report().explicitClientMods().size() + action.report().otherExtraMods().size();
        };
    }

    private boolean open(Group group) {
        return switch (group) { case UNMET -> unmetOpen; case MATCHED -> matchedOpen; case EXTRAS -> extrasOpen; };
    }

    private void toggle(Group group) {
        switch (group) {
            case UNMET -> unmetOpen = !unmetOpen;
            case MATCHED -> matchedOpen = !matchedOpen;
            case EXTRAS -> extrasOpen = !extrasOpen;
        }
    }

    private int contentHeight(List<Row> rows) { return rows.stream().mapToInt(Row::height).sum(); }
    private void clamp(List<Row> rows) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight(rows) - (bottom() - top()))));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        JaneUiText.centered(graphics, font, title, width / 2, 9, 0xFFFFFF);
        List<Row> rows = rows();
        clamp(rows);
        int y = top() - scroll;
        graphics.enableScissor(left(), top(), right() + 8, bottom());
        for (Row row : rows) {
            if (y + row.height() > top() && y < bottom()) drawRow(graphics, row, y, mouseX, mouseY);
            y += row.height();
        }
        graphics.disableScissor();
        JaneScrollbar.draw(graphics, right() + 3, top(), bottom(), contentHeight(rows), scroll);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphics graphics, Row row, int y, int mouseX, int mouseY) {
        int left = left();
        int right = right();
        if (row.result() == null && row.extra() == null) {
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + row.height();
            graphics.fill(left, y, right, y + row.height() - 2, hovered ? 0xFF555555 : 0xFF333333);
            String key = switch (row.group()) {
                case UNMET -> "jane.environment.unmet_group";
                case MATCHED -> "jane.environment.matched_group";
                case EXTRAS -> "jane.environment.extra_group";
            };
            JaneUiText.draw(graphics, font, fit((open(row.group()) ? "▼ " : "▶ ")
                    + Component.translatable(key).getString(), right - left - 55), left + 5, y + 6, 0xFFFFFF);
            JaneUiText.draw(graphics, font, Integer.toString(count(row.group())), right - 24, y + 6, 0xFFFFFF);
            return;
        }
        graphics.fill(left, y, right, y + row.height() - 2, 0x88000000);
        if (row.result() != null) {
            var result = row.result();
            JaneUiText.draw(graphics, font, fit(result.required().displayName(), right - left - 12), left + 6, y + 3, 0xFFFFFF);
            String status = !result.retainedCandidates().isEmpty() && result.status() != Comparison.Status.OK
                    ? Component.translatable("jane.environment.unsafe_duplicate").getString()
                    : Component.translatable("jane.status."
                    + result.status().name().toLowerCase(Locale.ROOT)).getString();
            String detail = result.required().modId() + " · " + result.required().version() + " · " + status;
            JaneUiText.draw(graphics, font, fit(detail, right - left - 12), left + 6, y + 17,
                    result.status() == Comparison.Status.FILE_ERROR ? 0xFF7777 : 0xFFCC77);
        } else {
            var extra = row.extra();
            JaneUiText.draw(graphics, font, fit(extra.displayName(), right - left - 12), left + 6, y + 3, 0xFFFFFF);
            String kind = Component.translatable(extra.kind() == ClientCompatibilityReport.Kind.EXPLICIT_CLIENT
                    ? "jane.compat.explicit" : "jane.compat.other").getString();
            JaneUiText.draw(graphics, font, fit(extra.modId() + " · " + extra.version() + " · " + kind,
                    right - left - 12), left + 6, y + 16, 0xCCCCCC);
        }
    }

    private String fit(String value, int maxWidth) {
        return JaneUiText.fit(font, value, maxWidth);
    }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (y < top() || y >= bottom()) return super.mouseScrolled(x, y, amount);
        scroll -= (int) Math.round(amount * 24);
        clamp(rows());
        return true;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        int moved = scrollbar.click(x, y, button, right() + 3, top(), bottom(),
                contentHeight(rows()), scroll);
        if (moved >= 0) {
            scroll = moved;
            return true;
        }
        if (button != 0 || x < left() || x >= right() || y < top() || y >= bottom()) return false;
        int at = top() - scroll;
        for (Row row : rows()) {
            if (y >= at && y < at + row.height()) {
                if (row.result() == null && row.extra() == null) {
                    toggle(row.group());
                    clamp(rows());
                    return true;
                }
                return false;
            }
            at += row.height();
        }
        return false;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (button == 0 && scrollbar.dragging()) {
            scroll = scrollbar.drag(y, top(), bottom(), contentHeight(rows()), scroll);
            return true;
        }
        return super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (scrollbar.release(button)) return true;
        return super.mouseReleased(x, y, button);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}

