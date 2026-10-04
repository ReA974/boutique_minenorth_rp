package com.minenorth_shops.client;

import com.minenorth_eurobank.Money;
import com.minenorth_shops.MineNorthShops;
import com.minenorth_shops.Network;
import com.minenorth_shops.packet.ShopActionPacket;
import com.minenorth_shops.packet.ShopStatePacket;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopEntry;
import com.minenorth_shops.shop.ShopMode;
import com.minenorth_shops.shop.ShopService.Method;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

/** Écran de boutique côté joueur, dans le style des distributeurs MineNorth Bank. */
@OnlyIn(Dist.CLIENT)
public class ShopScreen extends Screen {
    private static final int W = 320, H = 224;
    private static final int LIST_X = 108, LIST_W = 180, LIST_Y = 40, ROWS = 6, ROW_H = 20;
    private static final ResourceLocation LOGO = new ResourceLocation(MineNorthShops.MODID, "textures/gui/logo.png");
    private static final int BLUE = 0xFF161048;
    private static final int PANEL = 0xFF0E0A34;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int DIM = 0xFF8FA8E0;
    private static final int WARN = 0xFFFFE066;
    private static final int CYAN = ShopButton.CYAN;

    private ShopStatePacket st;
    private String message;
    private int selected = -1;   // id de l'article
    private int lots = 1;
    private int offset;
    private int left, top;
    private boolean silent;

    public ShopScreen(ShopStatePacket st) {
        super(Component.literal(st.shop.name));
        this.st = st;
        this.message = st.message;
        if (!st.shop.entries.isEmpty()) selected = st.shop.entries.get(0).id;
    }

    public int shopId() {
        return st.shop.id;
    }

    /** Remplacé par une autre boutique : ne pas prévenir le serveur de la fermeture. */
    public void silent() {
        silent = true;
    }

    public void closeFromServer() {
        silent = true;
        minecraft.setScreen(null);
    }

    public void update(ShopStatePacket n) {
        this.st = n;
        if (!n.message.isEmpty()) this.message = n.message;
        if (selectedEntry() == null) {
            selected = n.shop.entries.isEmpty() ? -1 : n.shop.entries.get(0).id;
            lots = 1;
        }
        rebuildWidgets();
    }

    private Shop shop() {
        return st.shop;
    }

    @Nullable
    private ShopEntry selectedEntry() {
        return shop().entry(selected);
    }

    private int indexOf(ShopEntry e) {
        return shop().entries.indexOf(e);
    }

    private int have(ShopEntry e) {
        int i = indexOf(e);
        return i >= 0 && i < st.have.length ? st.have[i] : 0;
    }

    private long total() {
        ShopEntry e = selectedEntry();
        return e == null ? 0 : e.price * lots;
    }

    private int maxLots() {
        ShopEntry e = selectedEntry();
        if (e == null) return 1;
        long max;
        if (shop().mode == ShopMode.BUY) {
            max = have(e) / Math.max(1, e.quantity);
        } else if (e.price <= 0) {
            max = st.maxLots;
        } else {
            long money = Math.max(shop().allowCash ? st.cash : 0, shop().allowCard && st.cardIssue.isEmpty() ? st.balance : 0);
            max = money / e.price;
        }
        return (int) Math.max(1, Math.min(st.maxLots, max));
    }

    private void setLots(int n) {
        lots = Math.max(1, Math.min(st.maxLots, n));
        rebuildWidgets();
    }

    private void trade(Method method) {
        ShopEntry e = selectedEntry();
        if (e == null) return;
        Network.CHANNEL.sendToServer(new ShopActionPacket(ShopActionPacket.Action.TRADE, e.id, lots, e.price, method));
    }

    private ShopButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return addRenderableWidget(new ShopButton(left + x, top + y, w, h, Component.literal(label), color, r));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        Shop shop = shop();
        ShopEntry e = selectedEntry();
        boolean sel = e != null && st.licenceOk;   // sans la licence : consultation seulement
        long total = total();

        btn(W - 80, 10, 66, 16, "Quitter", ShopButton.GHOST, this::onClose);

        int maxOffset = Math.max(0, shop.entries.size() - ROWS);
        btn(LIST_X + LIST_W + 2, LIST_Y, 14, ROWS * ROW_H / 2 - 1, "^", ShopButton.DARK,
                () -> offset = Math.max(0, offset - 1)).enabled(shop.entries.size() > ROWS);
        btn(LIST_X + LIST_W + 2, LIST_Y + ROWS * ROW_H / 2 + 1, 14, ROWS * ROW_H / 2 - 1, "v", ShopButton.DARK,
                () -> offset = Math.min(maxOffset, offset + 1)).enabled(shop.entries.size() > ROWS);

        btn(LIST_X, 166, 18, 18, "-", ShopButton.DARK, () -> setLots(lots - 1)).enabled(sel && lots > 1);
        btn(LIST_X + 60, 166, 18, 18, "+", ShopButton.DARK, () -> setLots(lots + 1)).enabled(sel && lots < st.maxLots);
        btn(LIST_X + 82, 166, 34, 18, "Max", ShopButton.DARK, () -> setLots(maxLots())).enabled(sel);

        if (shop.mode == ShopMode.SELL) {
            boolean cashOk = sel && shop.allowCash && st.cash >= total;
            boolean cardOk = sel && shop.allowCard && st.cardIssue.isEmpty() && st.balance >= total;
            btn(LIST_X, 192, 96, 24, shop.allowCash ? "Payer en espèces" : "Espèces refusées", CYAN,
                    () -> trade(Method.CASH)).enabled(cashOk);
            btn(LIST_X + 100, 192, 96, 24, shop.allowCard ? "Payer par carte" : "Carte refusée", ShopButton.PINK,
                    () -> trade(Method.CARD)).enabled(cardOk);
        } else {
            boolean enough = sel && have(e) >= e.quantity * lots;
            btn(LIST_X, 192, 96, 24, shop.allowCash ? "Vendre (espèces)" : "Espèces refusées", ShopButton.GREEN,
                    () -> trade(Method.CASH)).enabled(enough && shop.allowCash);
            btn(LIST_X + 100, 192, 96, 24, shop.allowCard ? "Vendre (compte)" : "Virement refusé", ShopButton.PINK,
                    () -> trade(Method.CARD)).enabled(enough && shop.allowCard && st.hasAccount);
        }
    }

    @Override
    public void removed() {
        if (!silent) Network.CHANNEL.sendToServer(ShopActionPacket.close());
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, shop().entries.size() - ROWS);
        offset = Math.max(0, Math.min(max, offset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        List<ShopEntry> list = shop().entries;
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            int y = top + LIST_Y + i * ROW_H;
            if (idx < list.size() && mx >= left + LIST_X && mx < left + LIST_X + LIST_W && my >= y && my < y + ROW_H) {
                int id = list.get(idx).id;
                if (id != selected) {
                    selected = id;
                    lots = 1;
                    rebuildWidgets();
                }
                return true;
            }
        }
        return false;
    }

    private void drawScaled(GuiGraphics g, Component c, int x, int y, float maxScale, int maxWidth, int color) {
        float s = Math.min(maxScale, maxWidth / (float) Math.max(1, font.width(c)));
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        g.drawString(font, c, Math.round(x / s), Math.round(y / s), color, false);
        g.pose().popPose();
    }

    private static Component bold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
    }

    private int wrap(GuiGraphics g, String s, int x, int y, int w, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(s), w);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
        return n;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Shop shop = shop();
        boolean buy = shop.mode == ShopMode.BUY;
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF0E0E10);
        g.fill(left, top, left + W, top + H, BLUE);

        // en-tête
        RenderSystem.enableBlend();
        g.blit(LOGO, left + 10, top + 4, 28, 28, 0, 0, 96, 96, 96, 96);
        drawScaled(g, bold(shop.name.toUpperCase(java.util.Locale.ROOT)), left + 44, top + 9, 1.6f, 180, 0xFFFFFFFF);
        String sub = buy ? "Rachat d'objets" : "Boutique";
        g.drawString(font, sub, left + 44, top + 24, CYAN, false);
        if (!st.licenceName.isEmpty()) {
            // licence exigée : verte si le joueur l'a, rouge sinon
            int lx = left + 44 + font.width(sub) + 6;
            String lic = (st.licenceOk ? "✔ " : "✖ ") + st.licenceName;
            g.drawString(font, font.plainSubstrByWidth(lic, left + W - 84 - lx), lx, top + 24,
                    st.licenceOk ? 0xFF5FE0A0 : 0xFFFF5F6B, false);
        }

        // colonne de gauche : argent du joueur
        g.drawString(font, "Espèces", left + 16, top + 44, CYAN, false);
        drawScaled(g, bold(Money.format(st.cash)), left + 16, top + 55, 1.2f, 84, 0xFFFFFFFF);
        g.drawString(font, "Compte", left + 16, top + 70, CYAN, false);
        if (st.hasAccount) drawScaled(g, bold(Money.format(st.balance)), left + 16, top + 81, 1.2f, 84, 0xFFFFFFFF);
        else g.drawString(font, "aucun", left + 16, top + 81, DIM, false);
        if (shop.allowCard && !buy && !st.cardIssue.isEmpty()) wrap(g, st.cardIssue, left + 16, top + 94, 86, 2, WARN);

        ShopEntry e = selectedEntry();
        if (e != null) {
            g.drawString(font, "Sélection", left + 16, top + 118, CYAN, false);
            int n = wrap(g, e.item.getHoverName().getString(), left + 16, top + 129, 86, 2, 0xFFFFFFFF);
            g.drawString(font, Money.format(e.price) + (e.quantity > 1 ? " / " + e.quantity : ""), left + 16, top + 130 + n * 10, TEXT, false);
        }

        // liste des articles
        g.fill(left + LIST_X, top + LIST_Y - 1, left + LIST_X + LIST_W, top + LIST_Y + ROWS * ROW_H + 1, PANEL);
        List<ShopEntry> list = shop.entries;
        offset = Math.max(0, Math.min(offset, Math.max(0, list.size() - ROWS)));
        ShopEntry hovered = null;
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= list.size()) break;
            ShopEntry it = list.get(idx);
            int x = left + LIST_X, y = top + LIST_Y + i * ROW_H;
            boolean inRow = mx >= x && mx < x + LIST_W && my >= y && my < y + ROW_H;
            if (it.id == selected) g.fill(x, y, x + LIST_W, y + ROW_H, CYAN);
            else if (inRow) g.fill(x, y, x + LIST_W, y + ROW_H, 0xFF2E2480);
            g.renderItem(it.item, x + 2, y + 2);
            g.renderItemDecorations(font, it.item, x + 2, y + 2, it.quantity > 1 ? String.valueOf(it.quantity) : null);
            if (inRow && mx < x + 20) hovered = it;

            String price = Money.format(it.price);
            int pw = font.width(price);
            g.drawString(font, font.plainSubstrByWidth(it.item.getHoverName().getString(), LIST_W - 28 - pw), x + 22, y + 2, 0xFFFFFFFF, false);
            g.drawString(font, bold(price), x + LIST_W - 4 - font.width(bold(price)), y + 2, 0xFFFFFFFF, false);
            String sub = buy ? "Vous en avez : " + have(it) : it.quantity > 1 ? "Lot de " + it.quantity : "À l'unité";
            g.drawString(font, sub, x + 22, y + 11, it.id == selected ? 0xFFE8F4FF : DIM, false);
        }
        if (list.isEmpty()) g.drawString(font, "Cette boutique est vide.", left + LIST_X + 6, top + LIST_Y + 6, TEXT, false);

        // quantité et total
        if (e != null) {
            String q = "x" + lots;
            g.drawCenteredString(font, q, left + LIST_X + 39, top + 171, 0xFFFFFFFF);
            g.drawString(font, buy ? "Vous recevez" : "Total", left + LIST_X + 122, top + 163, CYAN, false);
            drawScaled(g, bold(Money.format(total())), left + LIST_X + 122, top + 174, 1.0f, 74, 0xFFFFFFFF);
        }

        if (message != null && !message.isEmpty()) wrap(g, message, left + 16, top + 162, 86, 5, WARN);

        super.render(g, mx, my, pt);
        if (hovered != null) g.renderTooltip(font, hovered.item, mx, my);
    }
}
