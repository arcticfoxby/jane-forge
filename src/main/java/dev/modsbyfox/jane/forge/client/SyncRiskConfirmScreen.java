package dev.modsbyfox.jane.forge.client;

import dev.modsbyfox.jane.core.Comparison;
import dev.modsbyfox.jane.core.PendingSyncContext;
import dev.modsbyfox.jane.core.SyncSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** An explicit, one-time confirmation for unsatisfied manifest entries the player chose to skip. */
final class SyncRiskConfirmScreen extends Screen {
    private record Row(FormattedCharSequence text, Comparison.Result skipped, int height) { }

    private final SyncScreen parent;
    private final PendingSyncContext context;
    private final SyncSelection selection;
    private final JaneScrollbar scrollbar = new JaneScrollbar();
    private int scroll;
    private boolean audited;
    private boolean warningShown;
    private Component notice;
    private Button confirmButton;

    SyncRiskConfirmScreen(SyncScreen parent, PendingSyncContext context, SyncSelection selection) {
        super(Component.translatable("jane.risk.title"));
        this.parent = parent;
        this.context = context;
        this.selection = selection;
    }

    @Override protected void init() {
        scrollbar.reset();
        boolean compact = height <= 140;
        int buttonWidth = Math.min(compact ? 190 : 250, width - 24);
        int x = (width - buttonWidth) / 2;
        confirmButton = addRenderableWidget(JaneUiText.button(Component.translatable("jane.risk.confirm"),
                button -> confirm()).bounds(x, height - (compact ? 22 : 52), buttonWidth, 20).build());
        Button back = addRenderableWidget(JaneUiText.button(Component.translatable("jane.risk.back"),
                button -> onClose()).bounds(compact ? 7 : x, compact ? 3 : height - 28,
                        compact ? 22 : buttonWidth, compact ? 18 : 20).build());
        if (compact) back.setMessage(Component.translatable("jane.select.back_short"));
    }

    private int top() { return height <= 140 ? 32 : Math.min(44, height - 83); }
    private int bottom() { return height <= 140 ? height - 28 : height - 63; }
    private int left() { return Math.max(10, width / 2 - Math.min(270, (width - 28) / 2)); }
    private int right() { return width - left() - 10; }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        addText(rows, "jane.risk.explain1");
        addText(rows, "jane.risk.explain2");
        addText(rows, "jane.risk.explain3");
        rows.add(new Row(null, null, 8));
        for (Comparison.Result result : context.results())
            if (result.status() != Comparison.Status.OK && !selection.isSelected(result.required().modId()))
                rows.add(new Row(null, result, 32));
        return rows;
    }

    private void addText(List<Row> rows, String key) {
        for (FormattedCharSequence line : JaneUiText.split(font, Component.translatable(key),
                Math.max(90, right() - left() - 12)))
            rows.add(new Row(line, null, 12));
        rows.add(new Row(null, null, 5));
    }

    private int contentHeight(List<Row> rows) { return rows.stream().mapToInt(Row::height).sum(); }
    private void clamp(List<Row> rows) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight(rows) - (bottom() - top()))));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        confirmButton.active = parent.canConfirmOverride();
        JaneUiText.centered(graphics, font, title, width / 2, 7, 0xFFCC77);
        String summary = Component.translatable("jane.risk.summary", skippedCount()).getString();
        if (height <= 140 && (notice != null || parent.auditIncomplete()))
            summary = (notice != null ? notice : Component.translatable("jane.select.audit_incomplete")).getString();
        JaneUiText.centered(graphics, font, fit(summary, height <= 140 ? width - 66 : width - 20),
                width / 2, height <= 140 ? 19 : 25, 0xFFFFFF);
        List<Row> rows = rows();
        clamp(rows);
        graphics.enableScissor(left(), top(), right() + 8, bottom());
        int y = top() - scroll;
        for (Row row : rows) {
            if (y + row.height() > top() && y < bottom()) drawRow(graphics, row, y);
            y += row.height();
        }
        graphics.disableScissor();
        JaneScrollbar.draw(graphics, right() + 3, top(), bottom(), contentHeight(rows), scroll);
        if (height > 140 && (notice != null || parent.auditIncomplete())) {
            Component message = notice != null ? notice : Component.translatable("jane.select.audit_incomplete");
            JaneUiText.centered(graphics, font, fit(message.getString(), width - 20), width / 2,
                    bottom() + 3, 0xFF7777);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int skippedCount() {
        return (int) context.results().stream().filter(result -> result.status() != Comparison.Status.OK
                && !selection.isSelected(result.required().modId())).count();
    }

    private void drawRow(GuiGraphics graphics, Row row, int y) {
        if (row.skipped() == null) {
            if (row.text() != null) JaneUiText.draw(graphics, font, row.text(), left() + 5, y + 1, 0xCCCCCC);
            return;
        }
        Comparison.Result result = row.skipped();
        graphics.fill(left(), y, right(), y + row.height() - 2, 0x88333333);
        JaneUiText.draw(graphics, font, fit(result.required().modId(), right() - left() - 12),
                left() + 6, y + 3, 0xFFFFFF);
        String detail = Component.translatable("jane.risk.item", result.required().version(),
                Component.translatable("jane.status." + result.status().name().toLowerCase(Locale.ROOT))).getString();
        JaneUiText.draw(graphics, font, fit(detail, right() - left() - 12), left() + 6, y + 17, 0xFFCC77);
    }

    private String fit(String value, int maxWidth) {
        return JaneUiText.fit(font, value, maxWidth);
    }

    private void confirm() {
        if (!parent.canConfirmOverride()) {
            notice = Component.translatable("jane.risk.selection_changed");
            return;
        }
        if (!audited) {
            audited = true;
            if (!parent.recordOverrideDecision()) {
                warningShown = true;
                notice = Component.translatable("jane.risk.audit_continue");
                return;
            }
        }
        if (parent.auditIncomplete() && !warningShown) {
            warningShown = true;
            notice = Component.translatable("jane.risk.audit_continue");
            return;
        }
        if (!parent.requestOverrideJoin()) notice = Component.translatable("jane.select.reconnect_failed");
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseY < top() || mouseY >= bottom()) return super.mouseScrolled(mouseX, mouseY, amount);
        scroll -= (int) Math.round(amount * 24);
        clamp(rows());
        return true;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        int next = scrollbar.click(x, y, button, right() + 3, top(), bottom(),
                contentHeight(rows()), scroll);
        if (next >= 0) {
            scroll = next;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && scrollbar.dragging()) {
            scroll = scrollbar.drag(y, top(), bottom(), contentHeight(rows()), scroll);
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

