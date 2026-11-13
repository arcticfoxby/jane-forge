package dev.modsbyfox.jane.forge.client;

import dev.modsbyfox.jane.core.ClientCompatibilityReport;
import dev.modsbyfox.jane.core.ClientDisableBatch;
import dev.modsbyfox.jane.core.ClientDisablePlan;
import dev.modsbyfox.jane.core.SyncDecisionAudit;


import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Review appears only after the server-required exact baseline has passed. */
final class CompatibilityReviewScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("jane");
    private static final float TEXT_SCALE = 0.7F;
    private static final int TEXT_LINE_HEIGHT = 8;
    private enum RowType { TEXT, EXPLICIT_HEADER, OTHER_HEADER, EXPLICIT, OTHER }
    private enum Scrollbar { NONE, DECLARATION, MODS }
    private record Row(RowType type, int height, FormattedCharSequence text,
                       ClientCompatibilityReport.ExtraMod mod) { }

    private final Screen parent;
    private final PendingClientAction.CompatibilityReview action;
    private final CompatibilitySelection selected;
    private boolean explicitOpen = true;
    private boolean otherOpen = true;
    private boolean busy;
    private List<ClientCompatibilityReport.ExtraMod> confirmedUniversalSelection;
    private int scroll;
    private int declarationScroll;
    private Scrollbar dragging = Scrollbar.NONE;
    private int dragOffset;
    private Component notice;
    private Button disableButton;
    private Button directButton;
    private Button backButton;

    CompatibilityReviewScreen(Screen parent, PendingClientAction.CompatibilityReview action) {
        super(Component.translatable("jane.compat.title"));
        this.parent = parent;
        this.action = action;
        this.selected = new CompatibilitySelection(action.report());
    }

    @Override protected void init() {
        dragging = Scrollbar.NONE;
        CompatibilityReviewLayout layout = layout();
        int x = layout.buttonX();
        int buttonWidth = layout.buttonWidth();
        disableButton = addRenderableWidget(new SmallTextButton(x, layout.buttonTop(), buttonWidth,
                Component.translatable("jane.compat.disable_count", 0), button -> confirmSelection()));
        directButton = addRenderableWidget(new SmallTextButton(x, layout.buttonTop() + 24, buttonWidth,
                Component.translatable("jane.compat.direct_join"), button -> directJoin()));
        backButton = addRenderableWidget(new SmallTextButton(x, layout.buttonTop() + 48, buttonWidth,
                Component.translatable("gui.back"), button -> onClose()));
    }

    private int declarationTextWidth() { return Math.max(40, (int) ((width - 35) / TEXT_SCALE)); }
    private int listTextWidth() {
        CompatibilityReviewLayout layout = layout();
        return Math.max(40, (int) ((layout.listRight() - layout.listLeft() - 12) / TEXT_SCALE));
    }
    private CompatibilityReviewLayout layout() {
        return CompatibilityReviewLayout.forSize(width, height, contentHeight(declarationRows()));
    }
    private int top() { return layout().listTop(); }
    private int bottom() { return layout().listBottom(); }

    /** Fixed technical boundary: its coordinates never depend on the Mod-list scroll offset. */
    private List<Row> declarationRows() {
        List<Row> result = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            for (FormattedCharSequence line : font.split(Component.translatable("jane.compat.boundary" + i), declarationTextWidth()))
                result.add(new Row(RowType.TEXT, TEXT_LINE_HEIGHT, line, null));
            if (i < 4) result.add(new Row(RowType.TEXT, 2, null, null));
        }
        return result;
    }

    private List<Row> rows() {
        List<Row> result = new ArrayList<>();
        result.add(new Row(RowType.EXPLICIT_HEADER, 18, null, null));
        if (explicitOpen)
            for (var mod : action.report().explicitClientMods()) result.add(new Row(RowType.EXPLICIT, 24, null, mod));
        result.add(new Row(RowType.OTHER_HEADER, 18, null, null));
        if (otherOpen) {
            addText(result, "jane.compat.other_warning");
            for (var mod : action.report().otherExtraMods()) result.add(new Row(RowType.OTHER, 31, null, mod));
        }
        result.add(new Row(RowType.TEXT, 6, null, null));
        addText(result, "jane.compat.disable_hint");
        addText(result, "jane.compat.dependency_warning");
        return result;
    }

    private void addText(List<Row> rows, String key) {
        for (FormattedCharSequence line : font.split(Component.translatable(key), listTextWidth()))
            rows.add(new Row(RowType.TEXT, TEXT_LINE_HEIGHT + 1, line, null));
    }

    private int contentHeight(List<Row> rows) { return rows.stream().mapToInt(Row::height).sum(); }
    private void clamp(List<Row> rows) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight(rows) - (bottom() - top()))));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        renderBackground(graphics);
        disableButton.active = !busy && selected.size() > 0;
        disableButton.setMessage(Component.translatable("jane.compat.disable_count", selected.size()));
        directButton.active = !busy;
        backButton.active = !busy;
        CompatibilityReviewLayout layout = layout();
        declarationScroll = Math.max(0, Math.min(declarationScroll, layout.declarationScrollLimit()));
        int declarationY = layout.declarationTop() - declarationScroll;
        int declarationLeft = 12;
        int declarationRight = width - 20;
        graphics.enableScissor(declarationLeft, layout.declarationTop(), width - 10, layout.declarationBottom());
        for (Row line : declarationRows()) {
            if (line.text() != null && declarationY + line.height() > layout.declarationTop()
                    && declarationY < layout.declarationBottom())
                drawSmall(graphics, line.text(), declarationLeft, declarationY, 0xBBBBBB);
            declarationY += line.height();
        }
        graphics.disableScissor();
        if (layout.declarationScrollLimit() > 0) {
            int view = layout.declarationBottom() - layout.declarationTop();
            int thumb = thumbHeight(view, layout.declarationContentHeight());
            int thumbY = thumbTop(layout.declarationTop(), view, thumb, declarationScroll,
                    layout.declarationScrollLimit());
            graphics.fill(declarationRight + 3, layout.declarationTop(), declarationRight + 10,
                    layout.declarationBottom(), 0xFF444444);
            graphics.fill(declarationRight + 3, thumbY, declarationRight + 10, thumbY + thumb, 0xFFAAAAAA);
        }
        graphics.fill(12, layout.declarationBottom() + 2, width - 12,
                layout.declarationBottom() + 3, 0xFF666666);
        List<Row> rows = rows();
        clamp(rows);
        int left = layout.listLeft();
        int right = layout.listRight();
        if (top() < bottom()) {
            graphics.enableScissor(left, top(), right + 8, bottom());
            int y = top() - scroll;
            for (Row row : rows) {
                if (y + row.height() > top() && y < bottom()) drawRow(graphics, row, left, right, y, mouseX, mouseY);
                y += row.height();
            }
            graphics.disableScissor();
        }
        int total = contentHeight(rows);
        int view = bottom() - top();
        if (view > 0 && total > view) {
            int thumb = thumbHeight(view, total);
            int thumbY = thumbTop(top(), view, thumb, scroll, total - view);
            graphics.fill(right + 3, top(), right + 10, bottom(), 0xFF444444);
            graphics.fill(right + 3, thumbY, right + 10, thumbY + thumb, 0xFFAAAAAA);
        }
        if (notice != null) {
            if (layout.buttonX() + layout.buttonWidth() >= layout.listLeft()) {
                drawSmall(graphics, fit(notice.getString(), layout.listRight() - layout.listLeft()),
                        layout.listLeft(), layout.listBottom() + 1, 0xFF7777);
            } else if (layout.buttonTop() - 3 > layout.declarationBottom() + 5) {
                List<FormattedCharSequence> lines = font.split(notice,
                        Math.max(40, (int) (layout.buttonWidth() / TEXT_SCALE)));
                int y = Math.max(layout.declarationBottom() + 6,
                        layout.buttonTop() - 5 - lines.size() * TEXT_LINE_HEIGHT);
                graphics.enableScissor(layout.buttonX(), layout.declarationBottom() + 5,
                        layout.buttonX() + layout.buttonWidth(), layout.buttonTop() - 3);
                for (FormattedCharSequence line : lines) {
                    drawSmall(graphics, line, layout.buttonX(), y, 0xFF7777);
                    y += TEXT_LINE_HEIGHT;
                }
                graphics.disableScissor();
            }
        }
        super.render(graphics, mouseX, mouseY, tick);
    }

    private void drawRow(GuiGraphics graphics, Row row, int left, int right, int y, int mouseX, int mouseY) {
        if (row.type() == RowType.TEXT) {
            if (row.text() != null) drawSmall(graphics, row.text(), left + 5, y + 1, 0xBBBBBB);
            return;
        }
        graphics.fill(left, y, right, y + row.height() - 2,
                mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + row.height()
                        ? 0xAA444444 : 0x88000000);
        if (row.type() == RowType.EXPLICIT_HEADER || row.type() == RowType.OTHER_HEADER) {
            boolean explicit = row.type() == RowType.EXPLICIT_HEADER;
            String text = ((explicit ? explicitOpen : otherOpen) ? "▼ " : "▶ ")
                    + Component.translatable(explicit ? "jane.compat.explicit" : "jane.compat.other").getString();
            drawSmall(graphics, fit(text, right - left - 48), left + 5, y + 5, 0xFFFFFF);
            int total = explicit ? action.report().explicitClientMods().size()
                    : action.report().otherExtraMods().size();
            int picked = explicit ? selected.explicitCount() : selected.otherCount();
            String count = picked + "/" + total;
            drawSmall(graphics, count, right - (int) (font.width(count) * TEXT_SCALE) - 7,
                    y + 5, 0xFFFFFF);
            return;
        }
        var mod = row.mod();
        boolean selectable = selected.selectable(mod);
        int x = left + (selectable ? 18 : 6);
        if (selectable) drawSmall(graphics, selected.contains(mod) ? "☑" : "☐",
                left + 6, y + 8, 0xFFFFFF);
        drawSmall(graphics, fit(mod.displayName(), right - x - 6), x, y + 2, 0xFFFFFF);
        String detail = mod.version() + "  ·  " + mod.filename();
        if (row.type() == RowType.EXPLICIT && mod.sha512() == null)
            detail += "  ·  " + Component.translatable("jane.direct.extra_unverified").getString();
        drawSmall(graphics, fit(detail, right - x - 6), x, y + 12,
                row.type() == RowType.EXPLICIT && mod.sha512() == null ? 0xFF7777 : 0xCCCCCC);
        if (row.type() == RowType.OTHER)
            drawSmall(graphics, fit(Component.translatable(selectable
                    ? "jane.compat.universal_kind" : rowErrorKey(mod)).getString(),
                    right - x - 6), x, y + 22, selectable ? 0xFFCC77 : 0xFF7777);
    }

    private String rowErrorKey(ClientCompatibilityReport.ExtraMod mod) {
        try {
            dev.modsbyfox.jane.core.PathSafety.safeExistingJarName(mod.filename());
        } catch (IllegalArgumentException exception) {
            return "jane.compat.unsafe_file";
        }
        return "jane.compat.local_identity_error";
    }

    private String fit(String text, int maxWidth) {
        int unscaledWidth = Math.max(0, (int) (maxWidth / TEXT_SCALE));
        if (font.width(text) <= unscaledWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, unscaledWidth - font.width("…"))) + "…";
    }

    private void drawSmall(GuiGraphics graphics, FormattedCharSequence text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
        graphics.drawString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    private void drawSmall(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
        graphics.drawString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    private static int thumbHeight(int view, int content) {
        return Math.min(view, Math.max(12, view * view / content));
    }

    private static int thumbTop(int top, int view, int thumb, int scroll, int limit) {
        return top + (limit == 0 ? 0 : scroll * (view - thumb) / limit);
    }

    private static boolean overTrack(double x, double y, int trackX, int top, int bottom) {
        return x >= trackX && x < trackX + 10 && y >= top && y < bottom;
    }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        CompatibilityReviewLayout layout = layout();
        if (x >= 12 && x < width - 10 && y >= layout.declarationTop() && y < layout.declarationBottom()) {
            declarationScroll -= (int) Math.round(amount * 18);
            declarationScroll = Math.max(0, Math.min(declarationScroll, layout.declarationScrollLimit()));
            return true;
        }
        if (x < layout.listLeft() || x >= layout.listRight() + 10
                || y < top() || y >= bottom()) return super.mouseScrolled(x, y, amount);
        scroll -= (int) Math.round(amount * 24);
        clamp(rows());
        return true;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        CompatibilityReviewLayout layout = layout();
        if (button == 0 && layout.declarationScrollLimit() > 0
                && overTrack(x, y, width - 17, layout.declarationTop(), layout.declarationBottom())) {
            int view = layout.declarationBottom() - layout.declarationTop();
            int thumb = thumbHeight(view, layout.declarationContentHeight());
            int thumbY = thumbTop(layout.declarationTop(), view, thumb, declarationScroll,
                    layout.declarationScrollLimit());
            dragOffset = y >= thumbY && y < thumbY + thumb ? (int) y - thumbY : thumb / 2;
            dragging = Scrollbar.DECLARATION;
            declarationScroll = CompatibilityReviewLayout.scrollForThumb((int) y, layout.declarationTop(),
                    layout.declarationBottom(), thumb, layout.declarationContentHeight(), dragOffset);
            return true;
        }
        int total = contentHeight(rows());
        int view = layout.listHeight();
        if (button == 0 && view > 0 && total > view
                && overTrack(x, y, layout.listRight() + 3, layout.listTop(), layout.listBottom())) {
            int thumb = thumbHeight(view, total);
            int thumbY = thumbTop(layout.listTop(), view, thumb, scroll, total - view);
            dragOffset = y >= thumbY && y < thumbY + thumb ? (int) y - thumbY : thumb / 2;
            dragging = Scrollbar.MODS;
            scroll = CompatibilityReviewLayout.scrollForThumb((int) y, layout.listTop(),
                    layout.listBottom(), thumb, total, dragOffset);
            return true;
        }
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0 || y < top() || y >= bottom() || busy) return false;
        int left = layout.listLeft();
        int right = layout.listRight();
        if (x < left || x >= right) return false;
        int at = top() - scroll;
        for (Row row : rows()) {
            if (y >= at && y < at + row.height()) {
                switch (row.type()) {
                    case EXPLICIT_HEADER -> explicitOpen = !explicitOpen;
                    case OTHER_HEADER -> otherOpen = !otherOpen;
                    case EXPLICIT, OTHER -> {
                        if (!selected.selectable(row.mod())) {
                            notice = Component.translatable("jane.compat.local_file_error");
                            return true;
                        }
                        selected.toggle(row.mod());
                        notice = null;
                    }
                    default -> { return false; }
                }
                clamp(rows());
                return true;
            }
            at += row.height();
        }
        return false;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging != Scrollbar.NONE) {
            CompatibilityReviewLayout layout = layout();
            if (dragging == Scrollbar.DECLARATION) {
                int view = layout.declarationBottom() - layout.declarationTop();
                declarationScroll = CompatibilityReviewLayout.scrollForThumb((int) y, layout.declarationTop(),
                        layout.declarationBottom(), thumbHeight(view, layout.declarationContentHeight()),
                        layout.declarationContentHeight(), dragOffset);
            } else {
                int total = contentHeight(rows());
                scroll = CompatibilityReviewLayout.scrollForThumb((int) y, layout.listTop(),
                        layout.listBottom(), thumbHeight(layout.listHeight(), total), total, dragOffset);
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging != Scrollbar.NONE) {
            dragging = Scrollbar.NONE;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private static final class SmallTextButton extends Button {
        private SmallTextButton(int x, int y, int width, Component label, OnPress onPress) {
            super(x, y, width, 20, label, onPress, DEFAULT_NARRATION);
        }

        @Override public void renderString(GuiGraphics graphics, Font font, int color) {
            String label = getMessage().getString();
            int maxWidth = Math.max(1, (int) ((getWidth() - 8) / TEXT_SCALE));
            if (font.width(label) > maxWidth)
                label = font.plainSubstrByWidth(label, Math.max(0, maxWidth - font.width("…"))) + "…";
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + getWidth() / 2.0F,
                    getY() + (getHeight() - font.lineHeight * TEXT_SCALE) / 2.0F, 0);
            graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
            graphics.drawCenteredString(font, label, 0, 0, color);
            graphics.pose().popPose();
        }
    }

    private void confirmSelection() {
        List<ClientCompatibilityReport.ExtraMod> chosen = selected.selected();
        if (busy || chosen.isEmpty()) return;
        confirmedUniversalSelection = null;
        if (!auditDisable("USER_SELECT_EXTRA_DISABLE", counts(chosen))) {
            notice = Component.translatable("jane.compat.audit_failed");
            return;
        }
        minecraft.setScreen(selected.needsUniversalConfirmation()
                ? new ClientUniversalDisableConfirmScreen(this, chosen)
                : new ClientDisableConfirmScreen(this, chosen));
    }

    boolean confirmUniversalDisable(List<ClientCompatibilityReport.ExtraMod> chosen) {
        if (busy || chosen.isEmpty() || !selected.selected().equals(chosen)
                || chosen.stream().noneMatch(mod -> mod.kind() == ClientCompatibilityReport.Kind.OTHER_EXTRA)) {
            notice = Component.translatable("jane.compat.audit_failed");
            return false;
        }
        boolean saved = auditDisable("USER_CONFIRM_UNIVERSAL_DISABLE", counts(chosen));
        if (saved) confirmedUniversalSelection = List.copyOf(chosen);
        if (!saved) notice = Component.translatable("jane.compat.audit_failed");
        return saved;
    }

    private static Map<String, String> counts(List<ClientCompatibilityReport.ExtraMod> chosen) {
        long explicit = chosen.stream().filter(mod ->
                mod.kind() == ClientCompatibilityReport.Kind.EXPLICIT_CLIENT).count();
        return Map.of("selectedExplicit", Long.toString(explicit),
                "selectedOther", Long.toString(chosen.size() - explicit),
                "selectedCount", Integer.toString(chosen.size()));
    }

    private boolean auditDisable(String event, Map<String, String> fields) {
        Map<String, String> values = new HashMap<>(fields);
        values.put("serverId", action.context().serverId());
        try {
            SyncDecisionAudit.record(ClientPaths.gameDir(), event, values);
            LOGGER.info("{}event={} selectedExplicit={} selectedOther={} modId={} kind={} fileHash={} backupPoint={}",
                    JaneLog.client(action.context().serverId()), event,
                    values.getOrDefault("selectedExplicit", "-"), values.getOrDefault("selectedOther", "-"),
                    values.getOrDefault("modId", "-"), values.getOrDefault("kind", "-"),
                    values.getOrDefault("fileHash", "-"), values.getOrDefault("selectionTimestamp", "-"));
            return true;
        } catch (IOException | SecurityException exception) {
            LOGGER.error("{}event={} auditWriteFailed cause={}", JaneLog.client(action.context().serverId()),
                    event, exception.getClass().getSimpleName());
            return false;
        }
    }

    private static final class DisableFailure extends RuntimeException {
        private final String reason;
        DisableFailure(String reason, Throwable cause) {
            super(reason, cause);
            this.reason = reason;
        }
    }

    void disableSelected(List<ClientCompatibilityReport.ExtraMod> chosen) {
        if (busy || chosen.isEmpty() || !selected.selected().equals(chosen)) return;
        if (chosen.stream().anyMatch(mod -> mod.kind() == ClientCompatibilityReport.Kind.OTHER_EXTRA)
                && !chosen.equals(confirmedUniversalSelection)) {
            notice = Component.translatable("jane.compat.universal_confirmation_required");
            return;
        }
        confirmedUniversalSelection = null;
        busy = true;
        notice = Component.translatable("jane.compat.preparing_disable");
        Path gameDir = ClientPaths.gameDir();
        List<ClientCompatibilityReport.ExtraMod> snapshot = List.copyOf(chosen);
        CompletableFuture.supplyAsync(() -> {
            String stage = "VALIDATION_FAILED";
            try {
                List<ClientPhysicalDiscovery.PhysicalClientMod> physical = ClientPhysicalDiscovery.scan(gameDir).all();
                ClientDisablePlan plan = ClientDisablePlan.prepare(gameDir, action.context().serverId(),
                        action.context().manifest(), action.report(), snapshot, (expected, jar) ->
                                physical.stream().anyMatch(found -> found.jar().equals(jar)
                                        && found.modId().equals(expected.modId())
                                        && found.displayName().equals(expected.displayName())
                                        && found.version().equals(expected.version())
                                        && found.environment() == (expected.kind()
                                        == ClientCompatibilityReport.Kind.EXPLICIT_CLIENT
                                        ? ModEnvironment.CLIENT : ModEnvironment.UNIVERSAL)));
                stage = "AUDIT_FAILED";
                for (int i = 0; i < plan.entries().size(); i++) {
                    var entry = plan.entries().get(i);
                    var selectedMod = snapshot.get(i);
                    if (!auditDisable("EXTRA_DISABLE_FILE_VERIFIED", Map.of(
                            "modId", entry.modId(), "kind", selectedMod.kind().name(),
                            "fileHash", entry.sha512().substring(0, 12),
                            "selectionTimestamp", plan.timestamp())))
                        throw new IOException("Disable audit unavailable");
                }
                Map<String, String> planFields = new HashMap<>(counts(snapshot));
                planFields.put("selectionTimestamp", plan.timestamp());
                if (!auditDisable("EXTRA_DISABLE_PLAN_CREATED", planFields))
                    throw new IOException("Disable audit unavailable");
                stage = "BATCH_WRITE_FAILED";
                ClientDisableBatch.write(gameDir, plan, ProcessHandle.current().pid());
                return plan;
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(new DisableFailure(stage, exception));
            }
        }).whenComplete((plan, error) -> minecraft.execute(() -> {
            busy = false;
            if (error != null) {
                DisableFailure failure = error.getCause() instanceof DisableFailure value ? value
                        : new DisableFailure("VALIDATION_FAILED", error);
                LOGGER.error("{}event=EXTRA_DISABLE_FAILED reason={} cause={}",
                        JaneLog.client(action.context().serverId()), failure.reason,
                        failure.getCause().getClass().getSimpleName());
                auditDisable("EXTRA_DISABLE_FAILED", Map.of("reason", failure.reason, "result", "FAILED"));
                notice = failureNotice(failure);
                return;
            }
            if (!auditDisable("EXTRA_DISABLE_HELPER_LAUNCH_REQUESTED", Map.of(
                    "selectionTimestamp", plan.timestamp(), "selectedCount", Integer.toString(plan.entries().size())))) {
                notice = Component.translatable("jane.compat.audit_failed");
                return;
            }
            try {
                WindowsHelperLauncher.launch(minecraft, gameDir, "Jane client disable", "disable.bat", () -> {
                    if (!auditDisable("EXTRA_DISABLE_HELPER_LAUNCHED", Map.of(
                            "selectionTimestamp", plan.timestamp(), "selectedCount", Integer.toString(plan.entries().size()))))
                        LOGGER.error("{}event=EXTRA_DISABLE_HELPER_LAUNCHED auditWriteFailed; CMD launch was accepted",
                                JaneLog.client(action.context().serverId()));
                }, "disabled", plan.serverId(), plan.timestamp());
            } catch (Exception exception) {
                LOGGER.error("{}event=EXTRA_DISABLE_FAILED reason=HELPER_LAUNCH_FAILED cause={}",
                        JaneLog.client(action.context().serverId()), exception.getClass().getSimpleName());
                auditDisable("EXTRA_DISABLE_FAILED", Map.of("reason", "HELPER_LAUNCH_FAILED", "result", "FAILED"));
                notice = Component.translatable("jane.compat.launch_failed");
            }
        }));
    }

    private Component failureNotice(DisableFailure failure) {
        if ("AUDIT_FAILED".equals(failure.reason)) return Component.translatable("jane.compat.audit_failed");
        String message = failure.getCause().getMessage();
        if (message != null && message.contains("metadata changed"))
            return Component.translatable("jane.compat.metadata_changed");
        if (message != null && message.contains("changed"))
            return Component.translatable("jane.compat.file_changed");
        if (message != null && (message.contains("Required mod") || message.contains("Jane cannot")))
            return Component.translatable("jane.compat.protected_file");
        if (message != null && (message.contains("not an ordinary file") || message.contains("not a direct JAR")
                || message.contains("Unsafe") || message.contains("escaped")))
            return Component.translatable("jane.compat.unsafe_file");
        return Component.translatable("jane.compat.local_file_error");
    }

    private void directJoin() {
        if (busy) return;
        boolean saved = JaneClient.recordEnvironmentAction("USER_SELECT_DIRECT_JOIN",
                action.context(), action.report());
        minecraft.setScreen(new DirectJoinConfirmScreen(this, new PendingClientAction.EnvironmentDecision(
                action.context(), action.report(), action.target(), !saved)));
    }

    @Override public void onClose() {
        // Validation and helper launch may still be in flight after the confirmation screen closes.
        if (!busy) minecraft.setScreen(parent);
    }
}

