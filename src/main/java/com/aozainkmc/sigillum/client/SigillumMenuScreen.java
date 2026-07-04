package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.glyph.GlyphCodex;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SigillumMenuScreen extends Screen {

    private static final int OVERLAY = 0x66000000;
    private static final int VOID = 0xF21A120B;
    private static final int VOID_DK = 0xFF120E09;
    private static final int VOID_LT = 0xFF241B12;
    private static final int EDGE = 0xFF5B3E22;
    private static final int EDGE_SOFT = 0x885B3E22;
    private static final int GRID = 0x664D3824;
    private static final int PAPER = 0xFF9D7B45;
    private static final int PAPER_DK = 0xFF74623F;
    private static final int PAPER_TX = 0xFFD7C19A;
    private static final int INK = 0xFF1C140C;
    private static final int MUTED = 0xFF93836A;
    private static final int MUTED_DK = 0xFF6F604C;
    private static final int RED = 0xFF5A2114;
    private static final int RED_LT = 0xFF8F2E1F;
    private static final int RED_TX = 0xFFB43C2B;
    private static final int HOT_TX = 0xFFE2CFAB;

    private static final String[] TAB_LABELS = {"快速吟唱", "黄符字典"};
    private static final int SLOT_COUNT = 9;

    private final Map<Integer, String> bindings;
    private int panelX;
    private int panelY;
    private int activeTab = 0;
    private int bindingScroll = 0;
    private int codexScroll = 0;

    public SigillumMenuScreen(Map<Integer, String> bindings) {
        super(Component.literal("快速吟唱设置"));
        this.bindings = new LinkedHashMap<>(bindings);
    }

    @Override
    protected void init() {
        int margin = Math.max(8, Math.min(this.width, this.height) / 28);
        this.panelX = margin;
        this.panelY = margin;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, this.width, this.height, OVERLAY);
        drawFrame(g);
        drawTitle(g);
        drawTabs(g, mx, my);

        if (activeTab == 0) {
            renderBindings(g);
        } else {
            renderCodex(g);
        }

        drawBottomButtons(g, mx, my);
    }

    private void renderBindings(GuiGraphics g) {
        int x = contentX();
        int y = contentY();
        int w = contentW();
        int h = contentH();
        int headerH = 20;
        int visibleRows = visibleBindingRows();
        int rowH = bindingRowH(visibleRows);
        bindingScroll = clamp(bindingScroll, 0, SLOT_COUNT - visibleRows);
        int slotX = x + w * 8 / 100;
        int glyphX = x + w * 28 / 100;
        int effectX = x + w * 56 / 100;

        drawTable(g, x, y, w, headerH + visibleRows * rowH, headerH, rowH, visibleRows);
        g.drawString(this.font, "槽位", slotX, y + 6, INK, false);
        g.drawString(this.font, "绑定字", glyphX, y + 6, INK, false);
        g.drawString(this.font, "效果", effectX, y + 6, INK, false);

        drawVLine(g, x + w * 16 / 100, y, headerH + visibleRows * rowH, GRID);
        drawVLine(g, x + w * 48 / 100, y, headerH + visibleRows * rowH, GRID);

        for (int row = 0; row < visibleRows; row++) {
            int slot = bindingScroll + row + 1;
            int rowY = y + headerH + row * rowH;
            int textY = rowY + Math.max(3, (rowH - 8) / 2);
            String glyph = bindings.get(slot);
            boolean bound = glyph != null && !glyph.isEmpty();
            g.drawString(this.font, String.valueOf(slot), slotX + 2, textY, PAPER_TX, false);

            if (bound) {
                drawGlyphTag(g, glyph, glyphX + 4, rowY, rowH);
                g.drawString(this.font, GlyphCodex.describe(glyph, SigillumClientConfig.detailed()), effectX, textY, PAPER_TX, false);
                int[] cb = clearRect(rowY, rowH);
                g.fill(cb[0], cb[1], cb[0] + cb[2], cb[1] + cb[3], 0x66302118);
                drawBorder(g, cb[0], cb[1], cb[2], cb[3], MUTED_DK);
                drawCenteredInBox(g, "清除", cb[0], cb[1], cb[2], cb[3], PAPER_TX, 1.0f);
            } else {
                g.drawString(this.font, "未绑定", glyphX, textY, MUTED_DK, false);
                g.drawString(this.font, "未绑定", effectX, textY, MUTED_DK, false);
            }
        }
        drawScrollBar(g, x, y + headerH, w, visibleRows * rowH, SLOT_COUNT, visibleRows, bindingScroll);
    }

    private void renderCodex(GuiGraphics g) {
        int x = contentX();
        int y = contentY();
        int w = contentW();
        int h = contentH();
        List<String> glyphs = GlyphCodex.glyphs();
        int totalRows = (glyphs.size() + 1) / 2;
        int visibleRows = visibleCodexRows(totalRows);
        int rowH = codexRowH(visibleRows);
        int listY = y + 24;
        int panelH = 24 + visibleRows * rowH;
        drawDarkPanel(g, x, y, w, panelH);
        g.fill(x + 1, y + 1, x + w - 1, y + 22, PAPER_DK);
        g.drawString(this.font, "十二字 · 含义（改绑请书写指定符）", x + 16, y + 7, INK, false);

        int colW = (w - 50) / 2;
        codexScroll = clamp(codexScroll, 0, totalRows - visibleRows);
        for (int row = 0; row < visibleRows; row++) {
            int sourceRow = codexScroll + row;
            for (int col = 0; col < 2; col++) {
                int idx = col * totalRows + sourceRow;
                if (idx >= glyphs.size()) {
                    continue;
                }
            int rowX = x + 18 + col * colW;
            int rowY = listY + row * rowH;
            String glyph = glyphs.get(idx);
            drawScaledText(g, glyph, rowX, rowY + 2, RED_TX, 1.25f);
            g.drawString(this.font, GlyphCodex.describe(glyph, SigillumClientConfig.detailed()), rowX + 32, rowY + 6, PAPER_TX, false);
            }
        }
        drawScrollBar(g, x, listY, w, visibleRows * rowH, totalRows, visibleRows, codexScroll);
    }

    private void drawFrame(GuiGraphics g) {
        int w = panelW();
        int h = panelH();
        g.fill(panelX - 2, panelY - 2, panelX + w + 2, panelY + h + 2, EDGE);
        g.fill(panelX, panelY, panelX + w, panelY + h, VOID);
        g.fill(panelX + 4, panelY + 4, panelX + w - 4, panelY + h - 4, VOID_DK);
        g.fill(panelX + 5, panelY + 5, panelX + w - 5, panelY + h - 5, VOID);
        drawHLine(g, panelX + 4, panelY + topH(), w - 8, RED);
        drawHLine(g, panelX + 4, panelY + topH() + 1, w - 8, EDGE_SOFT);
    }

    private void drawTitle(GuiGraphics g) {
        int center = panelX + panelW() / 2;
        int y = panelY + Math.max(10, topH() / 4);
        drawScaledCenteredString(g, "快速吟唱设置", center, y, HOT_TX, 1.55f);
        int sealX = center + Math.round(this.font.width("快速吟唱设置") * 1.55f / 2.0f) + 8;
        int sealY = y - 1;
        g.fill(sealX, sealY, sealX + 14, sealY + 22, 0x882A120C);
        drawBorder(g, sealX, sealY, 14, 22, RED);
        g.drawString(this.font, "符", sealX + 4, sealY + 3, RED_LT, false);
        g.drawString(this.font, "咒", sealX + 4, sealY + 12, RED_LT, false);
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < TAB_LABELS.length; i++) {
            int[] r = tabRect(i);
            boolean selected = activeTab == i;
            boolean hover = hit(mx, my, r);
            int bg = selected ? PAPER : (hover ? VOID_LT : 0x66201810);
            int tx = selected ? INK : PAPER_TX;
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], bg);
            if (selected) {
                g.fill(r[0], r[1], r[0] + 5, r[1] + r[3], RED);
                drawBorder(g, r[0], r[1], r[2], r[3], EDGE);
            } else {
                drawBorder(g, r[0], r[1], r[2], r[3], 0x552F2518);
            }
            drawScaledCenteredString(g, TAB_LABELS[i], r[0] + r[2] / 2, r[1] + (r[3] - 9) / 2, tx, 1.15f);
        }
    }

    private void drawBottomButtons(GuiGraphics g, int mx, int my) {
        drawButton(g, clearAllRect(), "全部清空", PAPER_DK, mx, my);
        drawButton(g, toggleDetailRect(), SigillumClientConfig.detailed() ? "详细信息" : "简略信息", PAPER_DK, mx, my);
        drawButton(g, backRect(), "返回", PAPER_DK, mx, my);
    }

    private void drawButton(GuiGraphics g, int[] r, String label, int base, int mx, int my) {
        boolean hover = hit(mx, my, r);
        int bg = hover ? (base == RED ? RED_LT : PAPER) : base;
        int tx = base == RED ? HOT_TX : INK;
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], bg);
        drawBorder(g, r[0], r[1], r[2], r[3], EDGE);
        drawBorder(g, r[0] + 2, r[1] + 2, r[2] - 4, r[3] - 4, 0x66472F1B);
        drawScaledCenteredString(g, label, r[0] + r[2] / 2, r[1] + (r[3] - 9) / 2, tx, 1.15f);
    }

    private void drawScrollBar(GuiGraphics g, int x, int y, int w, int h, int totalRows, int visibleRows, int offset) {
        if (visibleRows >= totalRows) {
            return;
        }
        int trackX = x + w - 8;
        int trackY = y + 3;
        int trackH = Math.max(12, h - 6);
        int thumbH = Math.max(10, trackH * visibleRows / totalRows);
        int maxOffset = Math.max(1, totalRows - visibleRows);
        int thumbY = trackY + (trackH - thumbH) * offset / maxOffset;
        g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x55201810);
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, PAPER_DK);
    }

    private void drawTable(GuiGraphics g, int x, int y, int w, int h, int headerH, int rowH, int rows) {
        drawDarkPanel(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + headerH, PAPER_DK);
        drawHLine(g, x, y + headerH, w, GRID);
        for (int i = 1; i <= rows; i++) {
            drawHLine(g, x, y + headerH + i * rowH, w, GRID);
        }
    }

    private void drawDarkPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, EDGE_SOFT);
        g.fill(x, y, x + w, y + h, 0xEE1E1A12);
        drawBorder(g, x, y, w, h, EDGE);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x66120E09);
    }

    private void drawGlyphTag(GuiGraphics g, String glyph, int x, int rowY, int rowH) {
        int h = Math.max(15, Math.min(20, rowH - 8));
        int w = Math.max(16, Math.min(21, h + 1));
        int y = rowY + (rowH - h) / 2;
        g.fill(x, y, x + w, y + h, PAPER);
        drawBorder(g, x, y, w, h, EDGE);
        int color = "火裂心".contains(glyph) ? RED_TX : INK;
        drawCenteredInBox(g, glyph, x, y, w, h, color, 1.1f);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        drawHLine(g, x, y, w, color);
        drawHLine(g, x, y + h - 1, w, color);
        drawVLine(g, x, y, h, color);
        drawVLine(g, x + w - 1, y, h, color);
    }

    private void drawHLine(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    private void drawVLine(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y, x + 1, y + h, color);
    }

    private void drawScaledCenteredString(GuiGraphics g, String text, int centerX, int y, int color, float scale) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(centerX - this.font.width(text) * scale / 2.0f, y, 0.0f);
        pose.scale(scale, scale, 1.0f);
        g.drawString(this.font, text, 0, 0, color, false);
        pose.popPose();
    }

    private void drawCenteredInBox(GuiGraphics g, String text, int x, int y, int w, int h, int color, float scale) {
        int textX = Math.round(x + (w - this.font.width(text) * scale) / 2.0f);
        int textY = Math.round(y + (h - this.font.lineHeight * scale) / 2.0f);
        drawScaledText(g, text, textX, textY, color, scale);
    }

    private void drawScaledText(GuiGraphics g, String text, int x, int y, int color, float scale) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0f);
        pose.scale(scale, scale, 1.0f);
        g.drawString(this.font, text, 0, 0, color, false);
        pose.popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < TAB_LABELS.length; i++) {
                if (hit(mx, my, tabRect(i))) {
                    activeTab = i;
                    return true;
                }
            }
            if (hit(mx, my, backRect())) {
                onClose();
                return true;
            }
            if (hit(mx, my, clearAllRect())) {
                for (int slot = 1; slot <= SLOT_COUNT; slot++) {
                    if (bindings.containsKey(slot)) {
                        SigillumClientHooks.clearBinding(slot);
                    }
                }
                bindings.clear();
                return true;
            }
            if (hit(mx, my, toggleDetailRect())) {
                SigillumClientConfig.toggleDetailed();
                return true;
            }
            if (activeTab == 0) {
                int visibleRows = visibleBindingRows();
                int rowH = bindingRowH(visibleRows);
                bindingScroll = clamp(bindingScroll, 0, SLOT_COUNT - visibleRows);
                for (int row = 0; row < visibleRows; row++) {
                    int slot = bindingScroll + row + 1;
                    String glyph = bindings.get(slot);
                    int rowY = contentY() + 20 + row * rowH;
                    if (glyph != null && !glyph.isEmpty() && hit(mx, my, clearRect(rowY, rowH))) {
                        SigillumClientHooks.clearBinding(slot);
                        bindings.remove(slot);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!hit(mx, my, new int[] {contentX(), contentY(), contentW(), contentH()})) {
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }
        int delta = scrollY < 0.0 ? 1 : -1;
        if (activeTab == 0) {
            int visibleRows = visibleBindingRows();
            int next = clamp(bindingScroll + delta, 0, SLOT_COUNT - visibleRows);
            if (next != bindingScroll) {
                bindingScroll = next;
                return true;
            }
        } else {
            int totalRows = (GlyphCodex.glyphs().size() + 1) / 2;
            int visibleRows = visibleCodexRows(totalRows);
            int next = clamp(codexScroll + delta, 0, totalRows - visibleRows);
            if (next != codexScroll) {
                codexScroll = next;
                return true;
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, this.width, this.height, OVERLAY);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelW() {
        return this.width - panelX * 2;
    }

    private int panelH() {
        return this.height - panelY * 2;
    }

    private int topH() {
        return Math.max(42, panelH() / 8);
    }

    private int sidebarW() {
        return Math.max(84, panelW() / 6);
    }

    private int contentX() {
        return panelX + sidebarW() + Math.max(16, panelW() / 38);
    }

    private int contentY() {
        return panelY + topH() + Math.max(12, panelH() / 38);
    }

    private int contentW() {
        return panelX + panelW() - contentX() - Math.max(14, panelW() / 42);
    }

    private int contentH() {
        return bottomY() - contentY() - Math.max(14, panelH() / 40);
    }

    private int bottomY() {
        return panelY + panelH() - Math.max(34, panelH() / 12);
    }

    private int[] tabRect(int i) {
        int tabX = panelX + Math.max(12, panelW() / 60);
        int tabY = contentY() + i * (tabH() + 5);
        return new int[] {tabX, tabY, sidebarW() - Math.max(24, panelW() / 30), tabH()};
    }

    private int tabH() {
        return Math.max(24, Math.min(42, panelH() / 13));
    }

    private int buttonW() {
        return Math.max(64, Math.min(112, panelW() / 7));
    }

    private int buttonH() {
        return Math.max(20, Math.min(34, panelH() / 17));
    }

    private int bindingRowH(int visibleRows) {
        int available = Math.max(20, contentH() - 20);
        return Math.max(20, Math.min(24, available / Math.max(1, visibleRows)));
    }

    private int visibleBindingRows() {
        int available = Math.max(20, contentH() - 20);
        return Math.max(1, Math.min(SLOT_COUNT, available / 20));
    }

    private int codexRowH(int visibleRows) {
        int available = Math.max(24, contentH() - 30);
        return Math.max(24, Math.min(30, available / Math.max(1, visibleRows)));
    }

    private int visibleCodexRows(int totalRows) {
        int available = Math.max(24, contentH() - 30);
        return Math.max(1, Math.min(totalRows, available / 24));
    }

    private int[] clearAllRect() {
        return new int[] {contentX(), bottomY(), buttonW() + 18, buttonH()};
    }

    private int[] toggleDetailRect() {
        int tw = Math.max(70, buttonW() + 4);
        int centerX = contentX() + contentW() / 2;
        return new int[] {centerX - tw / 2, bottomY(), tw, buttonH()};
    }

    private int[] backRect() {
        return new int[] {contentX() + contentW() - buttonW(), bottomY(), buttonW(), buttonH()};
    }

    private int[] clearRect(int rowY, int rowH) {
        int h = Math.max(12, Math.min(18, rowH - 8));
        return new int[] {contentX() + contentW() - 54, rowY + (rowH - h) / 2, 42, h};
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static boolean hit(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }
}
