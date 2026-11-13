package dev.modsbyfox.jane.forge.client;

import dev.modsbyfox.jane.core.ClientInstallCategory;
import dev.modsbyfox.jane.core.Comparison;
import dev.modsbyfox.jane.core.JaneSyncSession;
import dev.modsbyfox.jane.core.SyncSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Reusable selection content for the standalone view and the split sync view. */
final class SyncSelectionPanel {
    private final SyncScreen parent;
    private final JaneSyncSession session;
    private final SyncSelection selection;
    private final Font font;
    private final Component title = Component.translatable("jane.select.screen_title");
    private boolean requiredOpen;
    private boolean requiredInitialized;
    private boolean optionalOpen = true;
    private boolean matchedOpen;
    private boolean sourcesOpen;
    private int scroll;
    private final JaneScrollbar listBar = new JaneScrollbar();

    private enum RowKind { REQUIRED_HEADER, MATCHED_HEADER, OPTIONAL_HEADER,
        SOURCES_HEADER, TRUSTED_SOURCE, SERVER_SOURCE, NOTE, MOD }
    private record Row(RowKind kind, int height, Comparison.Result comparison,
                       FormattedCharSequence note, int count) { }


    SyncSelectionPanel(SyncScreen parent, JaneSyncSession session, SyncSelection selection, Font font) {
        this.parent = parent;
        this.session = session;
        this.selection = selection;
        this.font = font;
    }

    /** Show pending required mods when the embedded pane first appears. */
    void expandRequiredOnFirstShow() {
        if (requiredInitialized) return;
        requiredInitialized = true;
        requiredOpen = true;
    }

    /** Keep expanded groups and scroll offsets when the pane is temporarily hidden. */
    void resetDrag() {
        listBar.reset();
    }

    void render(GuiGraphics graphics, SelectionScreenLayout layout, int mouseX, int mouseY) {
        layout = fitDeclaration(layout);
        int centerX = layout.centerX();
        JaneUiText.centered(graphics, font, fit(title.getString(), layout.textWidth()), centerX,
                layout.compact() ? 5 : 9, 0xFFFFFF);
        if (!layout.compact())
            JaneUiText.centered(graphics, font, fit(Component.translatable("jane.select.screen_hint").getString(),
                    layout.textWidth()), centerX, 22, 0xBBBBBB);
        drawDeclaration(graphics, layout);
        List<Row> rows = rows(layout);
        clampScroll(rows, layout);
        JaneSyncSession.Snapshot snapshot = session.snapshot();
        graphics.enableScissor(layout.left(), layout.listTop(), layout.right() + 8, layout.listBottom());
        int y = layout.listTop() - scroll;
        for (Row row : rows) {
            if (y + row.height() > layout.listTop() && y < layout.listBottom())
                drawRow(graphics, snapshot, row, layout, y, mouseX, mouseY);
            y += row.height();
        }
        graphics.disableScissor();
        JaneScrollbar.draw(graphics, layout.right() + 3, layout.listTop(), layout.listBottom(),
                contentHeight(rows), scroll);
    }

    private List<FormattedCharSequence> declarationLines(SelectionScreenLayout layout) {
        int lineWidth = Math.max(20, layout.right() - layout.left() - 10);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(JaneUiText.split(font, Component.translatable("jane.select.declaration1"), lineWidth));
        lines.addAll(JaneUiText.split(font, Component.translatable("jane.select.declaration2"), lineWidth));
        lines.addAll(JaneUiText.split(font, Component.translatable("jane.select.declaration3"), lineWidth));
        return lines;
    }

    private SelectionScreenLayout fitDeclaration(SelectionScreenLayout layout) {
        return layout.fitDeclaration(declarationLines(layout).size());
    }

    private void drawDeclaration(GuiGraphics graphics, SelectionScreenLayout layout) {
        int left = layout.left();
        int right = layout.right();
        graphics.fill(left, layout.declarationTop(), right, layout.declarationBottom(), 0x88000000);
        JaneUiText.draw(graphics, font, fit(Component.translatable("jane.select.declaration_title").getString(),
                right - left - 10), left + 5, layout.declarationTop() + 2, 0xFFCC77);
        List<FormattedCharSequence> lines = declarationLines(layout);
        int textTop = layout.declarationTop() + 12;
        int textBottom = layout.declarationBottom() - 1;
        graphics.enableScissor(left, textTop, right + 8, textBottom);
        int y = textTop;
        for (FormattedCharSequence line : lines) {
            JaneUiText.draw(graphics, font, line, left + 5, y, 0xBBBBBB);
            y += 10;
        }
        graphics.disableScissor();
    }

    private List<Row> rows(SelectionScreenLayout layout) {
        List<Row> rows = new ArrayList<>();
        List<Comparison.Result> comparisons = session.context().results();
        Map<String, ClientInstallCategory> categories = selection.categories();
        List<Comparison.Result> required = SelectionScreenRows.pending(
                comparisons, categories, ClientInstallCategory.SERVER_REQUIRED);
        List<Comparison.Result> optional = SelectionScreenRows.pending(
                comparisons, categories, ClientInstallCategory.CLIENT_OPTIONAL);
        List<Comparison.Result> matched = SelectionScreenRows.matched(comparisons);
        rows.add(new Row(RowKind.REQUIRED_HEADER, 22, null, null, required.size()));
        if (requiredOpen)
            for (Comparison.Result result : required) rows.add(new Row(RowKind.MOD, 42, result, null, 0));
        rows.add(new Row(RowKind.MATCHED_HEADER, 22, null, null, matched.size()));
        if (matchedOpen)
            for (Comparison.Result result : matched) rows.add(new Row(RowKind.MOD, 42, result, null, 0));
        rows.add(new Row(RowKind.OPTIONAL_HEADER, 22, null, null, optional.size()));
        if (optionalOpen) {
            for (Comparison.Result result : optional) rows.add(new Row(RowKind.MOD, 42, result, null, 0));
            if (optional.isEmpty()) addNote(rows, "jane.select.optional_empty", layout);
        }
        rows.add(new Row(RowKind.SOURCES_HEADER, 22, null, null, 0));
        if (sourcesOpen) {
            rows.add(new Row(RowKind.TRUSTED_SOURCE, 22, null, null, 0));
            rows.add(new Row(RowKind.SERVER_SOURCE, 22, null, null, 0));
            if (!parent.serverSourceAvailable())
                addNote(rows, "jane.sync.server_source_unavailable", layout);
        }
        return rows;
    }

    private void addNote(List<Row> rows, String key, SelectionScreenLayout layout) {
        for (FormattedCharSequence line : JaneUiText.split(font, Component.translatable(key),
                Math.max(20, layout.right() - layout.left() - 12)))
            rows.add(new Row(RowKind.NOTE, 12, null, line, 0));
    }

    private int contentHeight(List<Row> rows) { return rows.stream().mapToInt(Row::height).sum(); }

    private void clampScroll(List<Row> rows, SelectionScreenLayout layout) {
        scroll = clamp(scroll, 0, Math.max(0, contentHeight(rows) - layout.listHeight()));
    }

    private void drawRow(GuiGraphics graphics, JaneSyncSession.Snapshot snapshot, Row row,
                         SelectionScreenLayout layout, int y, int mouseX, int mouseY) {
        int left = layout.left();
        int right = layout.right();
        if (row.kind() == RowKind.NOTE) {
            JaneUiText.draw(graphics, font, row.note(), left + 5, y + 1, 0xBBBBBB);
            return;
        }
        boolean hover = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + row.height();
        graphics.fill(left, y, right, y + row.height() - 2, hover ? 0xAA444444 : 0x88000000);
        if (row.kind() == RowKind.TRUSTED_SOURCE || row.kind() == RowKind.SERVER_SOURCE) {
            boolean server = row.kind() == RowKind.SERVER_SOURCE;
            boolean available = !server || parent.serverSourceAvailable();
            boolean enabled = server ? parent.serverSourceEnabled() : parent.trustedSourceEnabled();
            String name = Component.translatable(server ? "jane.sync.source_server"
                    : "jane.sync.source_trusted").getString();
            JaneUiText.draw(graphics, font, fit((enabled ? "☑ " : "☐ ") + name, right - left - 12),
                    left + 6, y + 6, available && parent.sourceSettingsEditable() ? 0xFFFFFF : 0x999999);
            return;
        }
        if (row.kind() != RowKind.MOD) {
            String key = switch (row.kind()) {
                case REQUIRED_HEADER -> "jane.select.required_group";
                case MATCHED_HEADER -> "jane.sync.matched_group";
                case OPTIONAL_HEADER -> "jane.select.optional_group";
                case SOURCES_HEADER -> "jane.sync.source_settings";
                default -> throw new IllegalStateException("Unknown row kind");
            };
            boolean open = switch (row.kind()) {
                case REQUIRED_HEADER -> requiredOpen;
                case MATCHED_HEADER -> matchedOpen;
                case OPTIONAL_HEADER -> optionalOpen;
                case SOURCES_HEADER -> sourcesOpen;
                default -> false;
            };
            String label = (open ? "▼ " : "▶ ") + Component.translatable(key).getString();
            if (row.kind() != RowKind.SOURCES_HEADER)
                label += Component.translatable(row.kind() == RowKind.REQUIRED_HEADER
                        ? "jane.sync.pending_count" : "jane.sync.group_count", row.count()).getString();
            JaneUiText.draw(graphics, font, fit(label, right - left - 10), left + 5, y + 6, 0xFFFFFF);
            return;
        }
        Comparison.Result result = row.comparison();
        String modId = result.required().modId();
        boolean matched = result.status() == Comparison.Status.OK;
        boolean selected = selection.isSelected(modId);
        boolean adjustable = SelectionScreenRows.adjustable(result, selection.category(modId));
        int textLeft = left + 25;
        JaneUiText.draw(graphics, font, matched ? "✓" : adjustable ? selected ? "☑" : "☐" : "•",
                left + 6, y + 13, matched ? 0xAAFFAA
                        : adjustable && parent.selectionEditable() ? 0xFFFFFF : 0x999999);
        JaneUiText.draw(graphics, font, fit(result.required().displayName(), right - textLeft - 8),
                textLeft, y + 3, 0xFFFFFF);
        boolean versionUpdate = result.status() == Comparison.Status.VERSION_MISMATCH
                && result.local() != null;
        String status = versionUpdate ? result.local().version() + " → " + result.required().version()
                : Component.translatable("jane.status."
                + result.status().name().toLowerCase(Locale.ROOT)).getString();
        int maxStatusWidth = versionUpdate ? Math.min(150, Math.max(95, (right - textLeft) * 2 / 3)) : 95;
        int statusWidth = Math.min(maxStatusWidth, JaneUiText.width(font, status) + 2);
        String identity = versionUpdate ? modId : modId + " · " + result.required().version();
        JaneUiText.draw(graphics, font, fit(identity,
                right - textLeft - statusWidth - 10), textLeft, y + 15, 0xBBBBBB);
        String statusLabel = versionUpdate ? versionTransition(result, statusWidth) : fit(status, statusWidth);
        JaneUiText.draw(graphics, font, statusLabel, right - statusWidth - 5,
                y + 15, matched ? 0xAAFFAA : 0xFFCC77);
        String size = fit(String.format(Locale.ROOT, "%.1f MiB", result.required().fileSize() / 1048576.0),
                Math.max(30, (right - textLeft) / 2));
        JaneUiText.draw(graphics, font, fit(sourceLabel(snapshot, result),
                right - textLeft - JaneUiText.width(font, size) - 12), textLeft, y + 28, 0xAAAAAA);
        JaneUiText.draw(graphics, font, size, right - 5 - JaneUiText.width(font, size), y + 28, 0xAAAAAA);
    }

    private String sourceLabel(JaneSyncSession.Snapshot snapshot, Comparison.Result result) {
        if (result.status() == Comparison.Status.OK)
            return Component.translatable("jane.details.matched").getString();
        if (snapshot.resolution() == null)
            return Component.translatable("jane.details.source_pending").getString();
        for (JaneSyncSession.ItemState item : snapshot.items()) {
            if (!item.item().comparison().required().modId().equals(result.required().modId())) continue;
            if (item.state() == JaneSyncSession.RuntimeState.DOWNLOADING)
                return Component.translatable("jane.runtime.downloading", (int) (100 * item.downloadedBytes()
                        / Math.max(1, result.required().fileSize()))).getString();
            if (item.state() != null && item.state() != JaneSyncSession.RuntimeState.WAITING)
                return Component.translatable("jane.runtime." + item.state().name().toLowerCase(Locale.ROOT))
                        .getString();
            return Component.translatable(switch (item.item().availability()) {
                case TRUSTED_AVAILABLE -> "jane.details.trusted_source";
                case SERVER_ONLY -> "jane.details.current_server";
                case ALREADY_PRESENT -> "jane.details.matched";
                case LOOKUP_FAILED -> "jane.sync.lookup_failed";
                case UNRESOLVED -> "jane.details.unknown_source";
                case LOCAL_ERROR -> "jane.sync.local_error";
            }).getString();
        }
        return Component.translatable("jane.details.source_pending").getString();
    }

    private String fit(String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        return JaneUiText.fit(font, value, maxWidth);
    }

    private String versionTransition(Comparison.Result result, int maxWidth) {
        String older = result.local().version();
        String newer = result.required().version();
        String arrow = " → ";
        int available = Math.max(0, maxWidth - JaneUiText.width(font, arrow) - 2);
        int olderWidth = JaneUiText.width(font, older);
        int newerWidth = JaneUiText.width(font, newer);
        int olderBudget = Math.min(olderWidth, Math.max(available / 2, available - newerWidth));
        return fit(older, olderBudget) + arrow + fit(newer, available - olderBudget);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(value, max)); }

    boolean mouseScrolled(double mouseX, double mouseY, double amount, SelectionScreenLayout layout) {
        layout = fitDeclaration(layout);
        if (!layout.containsX(mouseX)) return false;
        if (mouseY >= layout.listTop() && mouseY < layout.listBottom()) {
            List<Row> rows = rows(layout);
            scroll = clamp(scroll - (int) Math.round(amount * 24), 0,
                    Math.max(0, contentHeight(rows) - layout.listHeight()));
            return true;
        }
        return false;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button, SelectionScreenLayout layout) {
        layout = fitDeclaration(layout);
        if (button != 0 || !layout.containsX(mouseX)) return false;
        if (mouseY < layout.listTop() || mouseY >= layout.listBottom()) return false;
        List<Row> rows = rows(layout);
        int moved = listBar.click(mouseX, mouseY, button, layout.right() + 3,
                layout.listTop(), layout.listBottom(), contentHeight(rows), scroll);
        if (moved >= 0) {
            scroll = moved;
            return true;
        }
        if (mouseX < layout.left() || mouseX >= layout.right()) return false;
        int y = layout.listTop() - scroll;
        for (Row row : rows) {
            if (mouseY >= y && mouseY < y + row.height()) {
                switch (row.kind()) {
                    case REQUIRED_HEADER -> requiredOpen = !requiredOpen;
                    case MATCHED_HEADER -> matchedOpen = !matchedOpen;
                    case OPTIONAL_HEADER -> optionalOpen = !optionalOpen;
                    case SOURCES_HEADER -> sourcesOpen = !sourcesOpen;
                    case TRUSTED_SOURCE -> parent.toggleSource(false);
                    case SERVER_SOURCE -> parent.toggleSource(true);
                    case MOD -> {
                        if (SelectionScreenRows.adjustable(row.comparison(),
                                selection.category(row.comparison().required().modId())))
                            parent.toggleSelection(row.comparison());
                    }
                    case NOTE -> { return false; }
                }
                clampScroll(rows(layout), layout);
                return true;
            }
            y += row.height();
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY,
                         SelectionScreenLayout layout) {
        layout = fitDeclaration(layout);
        if (listBar.dragging() && button == 0) {
            scroll = listBar.drag(mouseY, layout.listTop(), layout.listBottom(),
                    contentHeight(rows(layout)), scroll);
            return true;
        }
        return false;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return listBar.release(button);
    }

}
