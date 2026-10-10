package com.minenorth_shops.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Liste déroulante au style des boutons MineNorth : un clic ouvre la liste, un clic sur une ligne choisit. Le dessin de la liste
 * ({@link #renderList}) et ses clics ({@link #clickList}) sont pilotés par l'écran, pour qu'elle passe au-dessus des autres widgets.
 */
@OnlyIn(Dist.CLIENT)
public class ShopDropdown extends AbstractWidget {
    private static final int ROW_H = 14, MAX_ROWS = 7;
    private static final int PANEL = 0xFF0E0A34, HOVER = 0xFF2E2480, CYAN = ShopButton.CYAN;

    private final String title;
    private final List<String> options;
    private final int selected;
    private final int color;
    private final IntConsumer onSelect;
    private final Consumer<ShopDropdown> onToggle;
    private boolean open;
    private int scroll;

    /**
     * @param color  couleur du bouton (ShopButton.DARK = valeur par défaut, une couleur vive quand une valeur est choisie)
     * @param toggle prévenu quand la liste s'ouvre (this) ou se ferme (null)
     */
    public ShopDropdown(int x, int y, int w, int h, String title, List<String> options, int selected, int color,
                        IntConsumer onSelect, Consumer<ShopDropdown> toggle) {
        super(x, y, w, h, Component.literal(title));
        this.title = title;
        this.options = options;
        this.selected = Math.max(0, Math.min(options.size() - 1, selected));
        this.color = color;
        this.onSelect = onSelect;
        this.onToggle = toggle;
    }

    public boolean isOpen() { return open; }

    public void close() {
        open = false;
        onToggle.accept(null);
    }

    @Override
    public void onClick(double mx, double my) {
        open = !open;
        scroll = Math.max(0, Math.min(Math.max(0, options.size() - MAX_ROWS), selected - MAX_ROWS / 2));
        onToggle.accept(open ? this : null);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 35), g = Math.min(255, ((c >> 8) & 0xFF) + 35), b = Math.min(255, (c & 0xFF) + 35);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        boolean hov = isHoveredOrFocused();
        int bg = !active ? 0xFF2A2468 : hov || open ? lighten(color) : color;
        g.fill(getX(), getY(), getX() + width, getY() + height, bg);
        int tc = active ? 0xFFFFFFFF : 0xFF8FA8E0;
        String text = font.plainSubstrByWidth(title + " : " + options.get(selected), width - 16);
        g.drawString(font, text, getX() + 4, getY() + (height - 8) / 2, tc, false);
        if (active) g.drawString(font, open ? "^" : "v", getX() + width - 10, getY() + (height - 8) / 2, tc, false);
    }

    private int rows() { return Math.min(MAX_ROWS, options.size()); }

    private int listWidth() {
        Font font = Minecraft.getInstance().font;
        int w = width;
        for (String o : options) w = Math.max(w, font.width(o) + 12);
        return w;
    }

    private int listTop() { return getY() + height; }

    /** Liste ouverte, dessinée par l'écran après tous les autres widgets. */
    public void renderList(GuiGraphics g, int mx, int my) {
        if (!open) return;
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = listTop(), w = listWidth(), h = rows() * ROW_H;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, CYAN);
        g.fill(x, y, x + w, y + h, PANEL);
        for (int i = 0; i < rows(); i++) {
            int idx = scroll + i;
            int ry = y + i * ROW_H;
            boolean in = mx >= x && mx < x + w && my >= ry && my < ry + ROW_H;
            if (in) g.fill(x, ry, x + w, ry + ROW_H, HOVER);
            g.drawString(font, options.get(idx), x + 6, ry + 3, idx == selected ? CYAN : 0xFFFFFFFF, false);
        }
        if (options.size() > MAX_ROWS) {   // repère de défilement
            int bh = Math.max(6, h * MAX_ROWS / options.size());
            int by = y + (h - bh) * scroll / Math.max(1, options.size() - MAX_ROWS);
            g.fill(x + w - 3, by, x + w - 1, by + bh, 0xFF8FA8E0);
        }
        g.pose().popPose();
    }

    /** Clic quand la liste est ouverte : true si traité (ligne choisie, ou clic sur l'en-tête qui referme). */
    public boolean clickList(double mx, double my) {
        int x = getX(), y = listTop(), w = listWidth();
        if (mx >= x && mx < x + w && my >= y && my < y + rows() * ROW_H) {
            int idx = scroll + (int) ((my - y) / ROW_H);
            close();
            if (idx >= 0 && idx < options.size() && idx != selected) onSelect.accept(idx);
            return true;
        }
        if (isMouseOver(mx, my)) {
            close();
            return true;
        }
        return false;
    }

    public void scrollList(double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, options.size() - MAX_ROWS), scroll - (int) Math.signum(delta)));
    }
}
