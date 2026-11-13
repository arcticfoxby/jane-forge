package dev.modsbyfox.jane.forge.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Jane screen text at 70% of the native font size, without changing widget hitboxes. */
final class JaneUiText {
    static final float SCALE = 0.7F;

    private JaneUiText() { }

    static Builder button(Component label, Button.OnPress onPress) {
        return new Builder(label, onPress);
    }

    static void draw(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        graphics.drawString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    static void draw(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        graphics.drawString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    static void draw(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        graphics.drawString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    static void centered(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        graphics.drawCenteredString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    static void centered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        graphics.drawCenteredString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    static void centered(GuiGraphics graphics, Font font, FormattedCharSequence text,
                         int centerX, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        graphics.drawCenteredString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    static int width(Font font, String text) {
        return Math.round(font.width(text) * SCALE);
    }

    static int width(Font font, Component text) {
        return Math.round(font.width(text) * SCALE);
    }

    static List<FormattedCharSequence> split(Font font, Component text, int pixelWidth) {
        return font.split(text, Math.max(1, (int) (pixelWidth / SCALE)));
    }

    static String fit(Font font, String text, int pixelWidth) {
        int unscaledWidth = Math.max(0, (int) (pixelWidth / SCALE));
        if (font.width(text) <= unscaledWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, unscaledWidth - font.width("…"))) + "…";
    }

    static void tooltip(GuiGraphics graphics, Font font, Component text, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = split(font, text, Math.max(40, Math.min(240, graphics.guiWidth() - 24)));
        int textWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        int boxWidth = Math.round(textWidth * SCALE) + 8;
        int boxHeight = lines.size() * 9 + 7;
        int x = mouseX + 12;
        if (x + boxWidth + 4 > graphics.guiWidth()) x = mouseX - boxWidth - 12;
        x = Math.max(4, Math.min(x, graphics.guiWidth() - boxWidth - 4));
        int y = mouseY + 10;
        if (y + boxHeight + 4 > graphics.guiHeight()) y = mouseY - boxHeight - 10;
        y = Math.max(4, Math.min(y, graphics.guiHeight() - boxHeight - 4));
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, 0xFF5050A0);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF0100010);
        for (int i = 0; i < lines.size(); i++)
            draw(graphics, font, lines.get(i), x + 4, y + 3 + i * 9, 0xFFFFFF);
        graphics.pose().popPose();
    }

    static final class Builder {
        private final Component label;
        private final Button.OnPress onPress;
        private int x;
        private int y;
        private int width;
        private int height;

        private Builder(Component label, Button.OnPress onPress) {
            this.label = label;
            this.onPress = onPress;
        }

        Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        Button build() {
            return new ScaledButton(x, y, width, height, label, onPress);
        }
    }

    private static final class ScaledButton extends Button {
        private ScaledButton(int x, int y, int width, int height, Component label, OnPress onPress) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        }

        @Override public void renderString(GuiGraphics graphics, Font font, int color) {
            Component label = getMessage();
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + getWidth() / 2.0F,
                    getY() + (getHeight() - font.lineHeight * SCALE) / 2.0F, 0);
            graphics.pose().scale(SCALE, SCALE, 1);
            if (width(font, label) > getWidth() - 8)
                graphics.drawCenteredString(font, fit(font, label.getString(), getWidth() - 8), 0, 0, color);
            else graphics.drawCenteredString(font, label, 0, 0, color);
            graphics.pose().popPose();
        }
    }
}

