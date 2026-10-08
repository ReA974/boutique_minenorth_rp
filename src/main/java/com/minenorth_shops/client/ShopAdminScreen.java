package com.minenorth_shops.client;

import com.minenorth_eurobank.Money;
import com.minenorth_shops.MineNorthShops;
import com.minenorth_shops.Network;
import com.minenorth_shops.packet.AdminEditPacket;
import com.minenorth_shops.packet.AdminEditPacket.Op;
import com.minenorth_shops.packet.AdminSyncPacket;
import com.minenorth_shops.PermisCompat.LicenceInfo;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopEntry;
import com.minenorth_shops.shop.ShopMode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

/** Interface d'administration (ops) : création et configuration des boutiques. */
@OnlyIn(Dist.CLIENT)
public class ShopAdminScreen extends Screen {
    private static final int W = 320, H = 240;
    private static final ResourceLocation LOGO = new ResourceLocation(MineNorthShops.MODID, "textures/gui/logo.png");
    private static final int BLUE = 0xFF161048;
    private static final int PANEL = 0xFF0E0A34;
    private static final int HOVER = 0xFF2E2480;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int DIM = 0xFF8FA8E0;
    private static final int WARN = 0xFFFFE066;
    private static final int CYAN = ShopButton.CYAN;

    // page liste
    private static final int L_Y = 64, L_ROWS = 7, L_ROW_H = 16;
    // page édition
    private static final int E_Y = 100, E_ROWS = 4, E_ROW_H = 18;
    private static final int LIST_X = 16, LIST_W = 272;

    private List<Shop> shops;
    private boolean permis;
    private List<LicenceInfo> licences;
    private String message;
    private int selectedShop = -1;   // sélection sur la page liste
    private int editing = -1;        // boutique en cours d'édition (-1 = page liste)
    private int selectedEntry = -1;
    private int offset;
    private boolean confirmDelete;

    private String createText = "", renameText = "", priceText = "", qtyText = "1", catText = "";
    private EditBox createBox, renameBox, priceBox, qtyBox, catBox;

    // sélecteur d'objet dans l'inventaire de l'admin
    private static final int PICK_X = 79, PICK_Y = 70, CELL = 18;
    private boolean picking;
    private int pickedSlot = -1;   // -1 = objet en main
    private int left, top;

    public ShopAdminScreen(AdminSyncPacket m) {
        super(Component.literal("Boutiques"));
        this.shops = m.shops;
        this.permis = m.permis;
        this.licences = m.licences;
        this.message = m.message;
        if (m.focus >= 0 && shop(m.focus) != null) startEdit(m.focus);
    }

    // ---------- données ----------

    public void update(AdminSyncPacket m) {
        EditBox focused = getFocused() instanceof EditBox b ? b : null;
        String focusName = focused == null ? null : focused == createBox ? "create" : focused == renameBox ? "rename"
                : focused == priceBox ? "price" : focused == qtyBox ? "qty" : focused == catBox ? "cat" : null;
        saveTexts();
        this.shops = m.shops;
        this.permis = m.permis;
        this.licences = m.licences;
        if (!m.message.isEmpty()) this.message = m.message;
        if (m.focus >= 0 && shop(m.focus) != null) {
            startEdit(m.focus);
            createText = "";
            focusName = null;
        }
        if (editing >= 0 && shop(editing) == null) editing = -1;
        if (selectedShop >= 0 && shop(selectedShop) == null) selectedShop = -1;
        Shop s = shop(editing);
        if (s != null && s.entry(selectedEntry) == null) selectedEntry = -1;
        rebuildWidgets();
        EditBox target = focusName == null ? null : switch (focusName) {
            case "create" -> createBox;
            case "rename" -> renameBox;
            case "price" -> priceBox;
            case "cat" -> catBox;
            default -> qtyBox;
        };
        if (target != null) setFocused(target);
    }

    @Nullable
    private Shop shop(int id) {
        if (id < 0) return null;
        for (Shop s : shops) if (s.id == id) return s;
        return null;
    }

    /** Nom lisible d'une licence, ou son id si le serveur ne la connaît pas. */
    private String licenceName(String id) {
        for (LicenceInfo l : licences) if (l.id().equals(id)) return l.name();
        return id;
    }

    /** Licence suivante dans la liste (aucune -> 1re -> 2e ... -> dernière -> aucune). */
    private String nextLicence(String current) {
        if (licences.isEmpty()) return "";
        int i = -1;
        for (int k = 0; k < licences.size(); k++) if (licences.get(k).id().equals(current)) i = k;
        return i + 1 >= licences.size() ? "" : licences.get(i + 1).id();
    }

    private void startEdit(int id) {
        Shop s = shop(id);
        if (s == null) return;
        editing = id;
        selectedShop = id;
        selectedEntry = -1;
        offset = 0;
        renameText = s.name;
        priceText = "";
        qtyText = "1";
        catText = "";
        pickedSlot = -1;
        picking = false;
    }

    private void saveTexts() {
        if (createBox != null) createText = createBox.getValue();
        if (renameBox != null) renameText = renameBox.getValue();
        if (priceBox != null) priceText = priceBox.getValue();
        if (qtyBox != null) qtyText = qtyBox.getValue();
        if (catBox != null) catText = catBox.getValue();
    }

    private void send(Op op, int shopId, int entryId, String text, long price, int qty) {
        Network.CHANNEL.sendToServer(new AdminEditPacket(op, shopId, entryId, text, price, qty));
    }

    private void send(Op op, int shopId) {
        send(op, shopId, 0, "", 0, 0);
    }

    private static String euros(long cents) {
        String s = (cents / 100) + "," + String.format("%02d", cents % 100);
        return s.endsWith(",00") ? s.substring(0, s.length() - 3) : s;
    }

    private long priceInput() {
        String v = priceBox.getValue().trim();
        if (v.equals("0") || v.equals("0,00") || v.equals("0.00")) return 0;
        return Money.parseEuros(v);
    }

    private int qtyInput() {
        try {
            return Integer.parseInt(qtyBox.getValue().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void entryAction(Op op) {
        long price = priceInput();
        int qty = qtyInput();
        if ((op == Op.ADD_ENTRY || op == Op.UPDATE_ENTRY) && price < 0) {
            message = "Prix invalide (ex. 12,50).";
            return;
        }
        if ((op == Op.ADD_ENTRY || op == Op.UPDATE_ENTRY) && qty < 1) {
            message = "Quantité invalide.";
            return;
        }
                // ADD_ENTRY : entryId = emplacement d'inventaire choisi (lu côté serveur, NBT complet)
        // text = rubrique de l'article (vide = sans rubrique)
        send(op, editing, op == Op.ADD_ENTRY ? effectiveSlot() : selectedEntry, catBox.getValue(), price, qty);
    }

    // ---------- widgets ----------

    private ShopButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return addRenderableWidget(new ShopButton(left + x, top + y, w, h, Component.literal(label), color, r));
    }

    private EditBox box(int x, int y, int w, String hint, String value, int max) {
        EditBox b = new EditBox(font, left + x, top + y, w, 16, Component.literal(hint));
        b.setMaxLength(max);
        b.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        b.setValue(value);
        return addRenderableWidget(b);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        createBox = renameBox = priceBox = qtyBox = catBox = null;
        if (shop(editing) == null) picking = false;
        if (picking) initPicker();
        else if (shop(editing) != null) initEdit(shop(editing));
        else initList();
    }

    private void initList() {
        editing = -1;
        boolean sel = shop(selectedShop) != null;
        btn(W - 80, 10, 66, 16, "Fermer", ShopButton.GHOST, this::onClose);

        createBox = box(16, 40, 200, "Nom de la nouvelle boutique", createText, Shop.MAX_NAME);
        btn(220, 39, 84, 18, "Créer", CYAN, () -> send(Op.CREATE, -1, 0, createBox.getValue(), 0, 0));

        int maxOffset = Math.max(0, shops.size() - L_ROWS);
        btn(LIST_X + LIST_W + 2, L_Y, 14, L_ROWS * L_ROW_H / 2 - 1, "^", ShopButton.DARK, () -> offset = Math.max(0, offset - 1))
                .enabled(shops.size() > L_ROWS);
        btn(LIST_X + LIST_W + 2, L_Y + L_ROWS * L_ROW_H / 2 + 1, 14, L_ROWS * L_ROW_H / 2 - 1, "v", ShopButton.DARK,
                () -> offset = Math.min(maxOffset, offset + 1)).enabled(shops.size() > L_ROWS);

        btn(16, 184, 68, 20, "Modifier", CYAN, () -> {
            saveTexts();
            startEdit(selectedShop);
            rebuildWidgets();
        }).enabled(sel);
        btn(88, 184, 64, 20, "Aperçu", ShopButton.DARK, () -> send(Op.PREVIEW, selectedShop)).enabled(sel);
        btn(156, 184, 84, 20, "Outil de liaison", ShopButton.DARK, () -> send(Op.LINKER, selectedShop)).enabled(sel);
        btn(244, 184, 60, 20, confirmDelete ? "Confirmer ?" : "Supprimer", ShopButton.PINK, () -> {
            saveTexts();
            if (confirmDelete) {
                send(Op.DELETE, selectedShop);
                confirmDelete = false;
            } else {
                confirmDelete = true;
            }
            rebuildWidgets();
        }).enabled(sel);
    }

    private void initEdit(Shop s) {
        boolean sel = s.entry(selectedEntry) != null;
        btn(W - 80, 8, 66, 12, "Retour", ShopButton.GHOST, () -> {
            saveTexts();
            editing = -1;
            offset = 0;
            confirmDelete = false;
            rebuildWidgets();
        });
        btn(W - 190, 24, 86, 12, "Outil de liaison", ShopButton.GHOST, () -> send(Op.LINKER, s.id));
        btn(W - 90, 24, 76, 12, "Aperçu", ShopButton.GHOST, () -> send(Op.PREVIEW, s.id));

        renameBox = box(16, 38, 196, "Nom de la boutique", renameText, Shop.MAX_NAME);
        btn(216, 37, 88, 18, "Renommer", ShopButton.DARK, () -> send(Op.RENAME, s.id, 0, renameBox.getValue(), 0, 0));

        btn(16, 58, 110, 18, s.mode.adminLabel, s.mode == ShopMode.SELL ? CYAN : ShopButton.GREEN,
                () -> send(Op.TOGGLE_MODE, s.id));
        btn(130, 58, 85, 18, "Espèces : " + (s.allowCash ? "oui" : "non"), s.allowCash ? CYAN : ShopButton.DARK,
                () -> send(Op.TOGGLE_CASH, s.id));
        btn(219, 58, 85, 18, (s.mode == ShopMode.SELL ? "Carte : " : "Compte : ") + (s.allowCard ? "oui" : "non"),
                s.allowCard ? ShopButton.PINK : ShopButton.DARK, () -> send(Op.TOGGLE_CARD, s.id));

        // licence exigée (mod minenorth_permis) : clic = licence suivante, « Aucune » = retirer
        String licLabel;
        if (s.requiresLicence()) licLabel = "Licence requise : " + licenceName(s.licence);
        else if (!permis) licLabel = "Licence : mod permis absent";
        else licLabel = "Licence requise : aucune";
        btn(16, 78, 150, 18, font.plainSubstrByWidth(licLabel, 144), s.requiresLicence() ? ShopButton.GREEN : ShopButton.DARK,
                () -> send(Op.SET_LICENCE, s.id, 0, nextLicence(s.licence), 0, 0))
                .enabled(permis && !licences.isEmpty());
        btn(170, 78, 50, 18, "Aucune", ShopButton.GHOST, () -> send(Op.SET_LICENCE, s.id, 0, "", 0, 0))
                .enabled(s.requiresLicence());
        // réservée à la police (mod minenorthpolice) : clic = non -> tout policier (oui) -> officier et + -> commissaire -> non
        String[] police = {"Police : comm.", "Police : off.+", "Police : oui"};
        btn(224, 78, 80, 18, s.policeOnly() ? police[Math.max(0, Math.min(2, s.policeGrade))] : "Police : non",
                s.policeOnly() ? ShopButton.PINK : ShopButton.DARK, () -> send(Op.CYCLE_POLICE, s.id));

        int maxOffset = Math.max(0, s.entries.size() - E_ROWS);
        btn(LIST_X + LIST_W + 2, E_Y, 14, E_ROWS * E_ROW_H / 2 - 1, "^", ShopButton.DARK, () -> offset = Math.max(0, offset - 1))
                .enabled(s.entries.size() > E_ROWS);
        btn(LIST_X + LIST_W + 2, E_Y + E_ROWS * E_ROW_H / 2 + 1, 14, E_ROWS * E_ROW_H / 2 - 1, "v", ShopButton.DARK,
                () -> offset = Math.min(maxOffset, offset + 1)).enabled(s.entries.size() > E_ROWS);

        priceBox = box(16, 178, 64, "Prix/lot €", priceText, 12);
        qtyBox = box(84, 178, 40, "Qté", qtyText, 4);
        // aperçu de l'objet choisi (dessiné dans renderEdit) + bouton du sélecteur
        catBox = box(226, 178, 78, "Rubrique", catText, 20);
        btn(150, 177, 72, 18, pickedSlot < 0 ? "Inventaire" : "Changer", ShopButton.DARK, () -> {
            saveTexts();
            picking = true;
            rebuildWidgets();
        });

        btn(16, 198, 70, 18, "Ajouter", CYAN, () -> entryAction(Op.ADD_ENTRY));
        btn(90, 198, 70, 18, "Appliquer", ShopButton.DARK, () -> entryAction(Op.UPDATE_ENTRY)).enabled(sel);
        btn(164, 198, 60, 18, "Monter", ShopButton.DARK, () -> send(Op.MOVE_UP, s.id, selectedEntry, "", 0, 0)).enabled(sel);
        btn(228, 198, 76, 18, "Retirer", ShopButton.PINK, () -> send(Op.REMOVE_ENTRY, s.id, selectedEntry, "", 0, 0)).enabled(sel);
    }

    // ---------- sélecteur d'inventaire ----------

    /** Inventaire du joueur côté client (synchronisé par le jeu) : sert seulement à l'affichage et au choix. */
    private Inventory inv() {
        return minecraft.player.getInventory();
    }

    private int effectiveSlot() {
        return pickedSlot >= 0 ? pickedSlot : inv().selected;
    }

    private ItemStack pickedStack() {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        return inv().getItem(effectiveSlot());
    }

    /** Position (relative à l'écran) de chaque emplacement : 3 rangées d'inventaire, la barre rapide, la main secondaire. */
    private int[] slotPos(int slot) {
        if (slot == 40) return new int[]{PICK_X - 26, PICK_Y + 3 * CELL + 6};
        if (slot < 9) return new int[]{PICK_X + slot * CELL, PICK_Y + 3 * CELL + 6};
        int r = slot / 9 - 1, c = slot % 9;
        return new int[]{PICK_X + c * CELL, PICK_Y + r * CELL};
    }

    private static final int[] PICK_SLOTS;
    static {
        PICK_SLOTS = new int[37];
        for (int i = 0; i < 36; i++) PICK_SLOTS[i] = i;
        PICK_SLOTS[36] = 40;
    }

    private int slotAt(double mx, double my) {
        for (int slot : PICK_SLOTS) {
            int[] p = slotPos(slot);
            int x = left + p[0], y = top + p[1];
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) return slot;
        }
        return -1;
    }

    private void initPicker() {
        btn(W - 80, 8, 66, 12, "Annuler", ShopButton.GHOST, () -> {
            picking = false;
            rebuildWidgets();
        });
        btn(PICK_X, PICK_Y + 4 * CELL + 16, 9 * CELL, 18, "Prendre l'objet en main", ShopButton.DARK, () -> {
            pickedSlot = -1;
            picking = false;
            rebuildWidgets();
        });
    }

    private void pick(int slot) {
        ItemStack st = inv().getItem(slot);
        if (st.isEmpty()) return;
        pickedSlot = slot;
        picking = false;
        // pratique : un tas de 16 pains choisi = lot de 16 proposé
        if (qtyText.isBlank() || qtyText.trim().equals("1")) qtyText = String.valueOf(st.getCount());
        rebuildWidgets();
    }

    @Nullable
    private ItemStack renderPicker(GuiGraphics g, int mx, int my) {
        drawScaled(g, bold("CHOISIR UN OBJET"), left + 44, top + 9, 1.6f, 170, 0xFFFFFFFF);
        g.drawString(font, "Cliquez sur un objet de votre inventaire", left + 44, top + 25, CYAN, false);
        g.drawString(font, "Inventaire", left + PICK_X, top + PICK_Y - 11, TEXT, false);
        g.drawString(font, "Main 2", left + PICK_X - 30, top + PICK_Y + 3 * CELL - 4, DIM, false);
        ItemStack hovered = null;
        for (int slot : PICK_SLOTS) {
            int[] p = slotPos(slot);
            int x = left + p[0], y = top + p[1];
            ItemStack st = inv().getItem(slot);
            boolean in = mx >= x && mx < x + CELL && my >= y && my < y + CELL;
            g.fill(x, y, x + CELL - 1, y + CELL - 1, slot == effectiveSlot() ? CYAN : PANEL);
            if (in && !st.isEmpty()) g.fill(x, y, x + CELL - 1, y + CELL - 1, HOVER);
            if (!st.isEmpty()) {
                g.renderItem(st, x + 1, y + 1);
                g.renderItemDecorations(font, st, x + 1, y + 1);
                if (in) hovered = st;
            }
        }
        return hovered;
    }

    @Override
    public void removed() {
        Network.CHANNEL.sendToServer(AdminEditPacket.of(Op.CLOSE, -1));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------- souris ----------

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        Shop s = shop(editing);
        int size = s != null ? s.entries.size() : shops.size();
        int rows = s != null ? E_ROWS : L_ROWS;
        offset = Math.max(0, Math.min(Math.max(0, size - rows), offset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (picking) {
            int slot = slotAt(mx, my);
            if (slot >= 0) pick(slot);
            return true;
        }
        Shop s = shop(editing);
        int rows = s != null ? E_ROWS : L_ROWS, rowH = s != null ? E_ROW_H : L_ROW_H, y0 = s != null ? E_Y : L_Y;
        int size = s != null ? s.entries.size() : shops.size();
        for (int i = 0; i < rows; i++) {
            int idx = offset + i;
            int y = top + y0 + i * rowH;
            if (idx >= size || mx < left + LIST_X || mx >= left + LIST_X + LIST_W || my < y || my >= y + rowH) continue;
            saveTexts();
            if (s != null) {
                ShopEntry e = s.entries.get(idx);
                selectedEntry = e.id;
                priceText = euros(e.price);
                qtyText = String.valueOf(e.quantity);
                catText = e.category;
            } else {
                int id = shops.get(idx).id;
                if (id == selectedShop && button == 0 && confirmDelete == false && lastClickId == id
                        && System.currentTimeMillis() - lastClickMs < 350) {
                    startEdit(id);   // double-clic = modifier
                } else {
                    selectedShop = id;
                }
                lastClickId = id;
                lastClickMs = System.currentTimeMillis();
                confirmDelete = false;
            }
            rebuildWidgets();
            return true;
        }
        return false;
    }

    private int lastClickId = -1;
    private long lastClickMs;

    // ---------- rendu ----------

    private void drawScaled(GuiGraphics g, Component c, int x, int y, float maxScale, int maxWidth, int color) {
        float sc = Math.min(maxScale, maxWidth / (float) Math.max(1, font.width(c)));
        g.pose().pushPose();
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, c, Math.round(x / sc), Math.round(y / sc), color, false);
        g.pose().popPose();
    }

    private static Component bold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF0E0E10);
        g.fill(left, top, left + W, top + H, BLUE);
        RenderSystem.enableBlend();
        g.blit(LOGO, left + 10, top + 4, 28, 28, 0, 0, 96, 96, 96, 96);

        Shop s = shop(editing);
        ItemStack pickHover = null;
        ShopEntry hovered = null;
        if (picking) pickHover = renderPicker(g, mx, my);
        else if (s != null) hovered = renderEdit(g, s, mx, my);
        else renderList(g, mx, my);

        if (message != null && !message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, W - 32), left + 16, top + 224, WARN, false);
        }
        super.render(g, mx, my, pt);
        if (hovered != null) g.renderTooltip(font, hovered.item, mx, my);
        if (pickHover != null) g.renderTooltip(font, pickHover, mx, my);
        else if (!picking && s != null && mx >= left + 128 && mx < left + 146 && my >= top + 177 && my < top + 195
                && !pickedStack().isEmpty()) g.renderTooltip(font, pickedStack(), mx, my);
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        drawScaled(g, bold("BOUTIQUES"), left + 44, top + 9, 1.8f, 160, 0xFFFFFFFF);
        g.drawString(font, "Administration — " + shops.size() + " boutique(s)", left + 44, top + 25, CYAN, false);

        g.fill(left + LIST_X, top + L_Y - 1, left + LIST_X + LIST_W, top + L_Y + L_ROWS * L_ROW_H + 1, PANEL);
        offset = Math.max(0, Math.min(offset, Math.max(0, shops.size() - L_ROWS)));
        for (int i = 0; i < L_ROWS; i++) {
            int idx = offset + i;
            if (idx >= shops.size()) break;
            Shop sh = shops.get(idx);
            int x = left + LIST_X, y = top + L_Y + i * L_ROW_H;
            boolean sel = sh.id == selectedShop;
            if (sel) g.fill(x, y, x + LIST_W, y + L_ROW_H, CYAN);
            else if (mx >= x && mx < x + LIST_W && my >= y && my < y + L_ROW_H) g.fill(x, y, x + LIST_W, y + L_ROW_H, HOVER);
            String right = (sh.policeOnly() ? "Police · " : "") + (sh.requiresLicence() ? "Licence · " : "") + sh.mode.label + " · " + sh.entries.size() + " art.";
            int rw = font.width(right);
            g.drawString(font, "#" + sh.id, x + 4, y + 4, sel ? 0xFFFFFFFF : DIM, false);
            g.drawString(font, font.plainSubstrByWidth(sh.name, LIST_W - rw - 40), x + 30, y + 4, 0xFFFFFFFF, false);
            g.drawString(font, right, x + LIST_W - 4 - rw, y + 4,
                    sel ? 0xFFFFFFFF : sh.mode == ShopMode.SELL ? CYAN : 0xFF5FE0A0, false);
        }
        if (shops.isEmpty()) g.drawString(font, "Aucune boutique : créez-en une ci-dessus.", left + LIST_X + 6, top + L_Y + 4, TEXT, false);

        g.drawString(font, confirmDelete ? "Cliquez encore sur « Confirmer ? » pour supprimer."
                        : "Double-clic = modifier. Assigner : outil ou /shops assign.",
                left + 16, top + 210, confirmDelete ? WARN : DIM, false);
    }

    @Nullable
    private ShopEntry renderEdit(GuiGraphics g, Shop s, int mx, int my) {
        drawScaled(g, bold(s.name.toUpperCase(java.util.Locale.ROOT)), left + 44, top + 8, 1.6f, 140, 0xFFFFFFFF);
        g.drawString(font, "Boutique #" + s.id, left + 44, top + 25, CYAN, false);

        g.fill(left + LIST_X, top + E_Y - 1, left + LIST_X + LIST_W, top + E_Y + E_ROWS * E_ROW_H + 1, PANEL);
        offset = Math.max(0, Math.min(offset, Math.max(0, s.entries.size() - E_ROWS)));
        ShopEntry hovered = null;
        for (int i = 0; i < E_ROWS; i++) {
            int idx = offset + i;
            if (idx >= s.entries.size()) break;
            ShopEntry e = s.entries.get(idx);
            int x = left + LIST_X, y = top + E_Y + i * E_ROW_H;
            boolean inRow = mx >= x && mx < x + LIST_W && my >= y && my < y + E_ROW_H;
            if (e.id == selectedEntry) g.fill(x, y, x + LIST_W, y + E_ROW_H, CYAN);
            else if (inRow) g.fill(x, y, x + LIST_W, y + E_ROW_H, HOVER);
            g.renderItem(e.item, x + 1, y + 1);
            if (inRow && mx < x + 18) hovered = e;
            String right = "x" + e.quantity + "   " + Money.format(e.price);
            int rw = font.width(right);
            String name = (e.category.isEmpty() ? "" : "[" + e.category + "] ") + e.item.getHoverName().getString() + (e.item.hasTag() ? " *" : "");
            g.drawString(font, font.plainSubstrByWidth(name, LIST_W - rw - 30), x + 20, y + 5, 0xFFFFFFFF, false);
            g.drawString(font, right, x + LIST_W - 4 - rw, y + 5, 0xFFFFFFFF, false);
        }
        if (s.entries.isEmpty()) {
            g.drawString(font, "Aucun article. Choisissez un objet de votre inventaire,", left + LIST_X + 6, top + E_Y + 4, TEXT, false);
            g.drawString(font, "indiquez prix et quantité, puis « Ajouter ».", left + LIST_X + 6, top + E_Y + 14, TEXT, false);
        }
        // rubriques déjà utilisées (pour réécrire exactement le même nom)
        List<String> cats = s.categories();
        if (!cats.isEmpty())
            g.drawString(font, font.plainSubstrByWidth("Rubriques : " + String.join(", ", cats), LIST_W), left + LIST_X, top + 216, DIM, false);
        // objet qui sera ajouté
        int px = left + 128, py = top + 177;
        g.fill(px, py, px + 18, py + 18, PANEL);
        ItemStack picked = pickedStack();
        if (!picked.isEmpty()) g.renderItem(picked, px + 1, py + 1);
        else g.drawCenteredString(font, "?", px + 9, py + 5, DIM);
        return hovered;
    }
}
