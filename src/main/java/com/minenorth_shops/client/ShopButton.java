package com.minenorth_shops.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Bouton plat, même style que les écrans MineNorth Bank. */
@OnlyIn(Dist.CLIENT)
public class ShopButton extends AbstractButton {
    public static final int CYAN = 0xFF20AAEB;
    public static final int DARK = 0xFF4A3CB4;
    public static final int PINK = 0xFFC83CF0;
    public static final int GREEN = 0xFF22A86B;
    public static final int GHOST = 0;

    private final Runnable action;
    private final int color;
    private Component leftText, rightText;

    public ShopButton(int x, int y, int w, int h, Component label, int color, Runnable action) {
        super(x, y, w, h, label);
        this.color = color;
        this.action = action;
    }

    public ShopButton sides(Component left, Component right) {
        this.leftText = left;
        this.rightText = right;
        return this;
    }

    public ShopButton enabled(boolean on) {
        this.active = on;
        return this;
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 35);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 35);
        int b = Math.min(255, (c & 0xFF) + 35);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY();
        boolean hov = isHoveredOrFocused();
        int ty = y + (height - 8) / 2;

        if (color == GHOST) {
            int tc = !active ? 0xFF8FA8E0 : hov ? 0xFFFFFFFF : 0xFFCFE3FF;
            int tx = x + width - font.width(getMessage());
            g.drawString(font, getMessage(), tx, ty, tc, false);
            if (hov && active) g.fill(tx, ty + 9, x + width, ty + 10, tc);
            return;
        }

        int bg = !active ? 0xFF2A2468 : hov ? lighten(color) : color;
        g.fill(x, y, x + width, y + height, bg);
        int tc = active ? 0xFFFFFFFF : 0xFF8FA8E0;
        if (leftText != null) {
            g.drawString(font, leftText, x + 14, ty, tc, false);
            g.drawString(font, rightText, x + width - 14 - font.width(rightText), ty, tc, false);
        } else {
            g.drawCenteredString(font, getMessage(), x + width / 2, ty, tc);
        }
    }
}
