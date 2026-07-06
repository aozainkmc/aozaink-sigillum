package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.glyph.GlyphCodex;
import com.aozainkmc.sigillum.network.OpenMenuPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
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
    private static final int MUTED_DK = 0xFF6F604C;
    private static final int RED = 0xFF5A2114;
    private static final int RED_LT = 0xFF8F2E1F;
    private static final int RED_TX = 0xFFB43C2B;
    private static final int HOT_TX = 0xFFE2CFAB;
    private static final String[] TAB_LABELS = {"快速吟唱", "黄符字典", "刻印", "合成"};
    private static final int SLOT_COUNT = 9;
    private static final float BRIEF_TEXT_SCALE = 1.15f;
    private static final float DETAIL_TEXT_SCALE = 0.82f;

    private final Map<Integer, String> bindings;
    private final List<OpenMenuPayload.InscriptionEntry> inscriptions;
    private int panelX;
    private int panelY;
    private int activeTab = 0;
    private int bindingScroll = 0;
    private int codexScroll = 0;
    private int inscriptionScroll = 0;
    private int recipePage = 0;

    public SigillumMenuScreen(Map<Integer, String> bindings) {
        this(bindings, List.of());
    }

    public SigillumMenuScreen(Map<Integer, String> bindings, List<OpenMenuPayload.InscriptionEntry> inscriptions) {
        super(Component.literal("符咒簿"));
        this.bindings = new LinkedHashMap<>(bindings);
        this.inscriptions = List.copyOf(inscriptions);
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

        switch (activeTab) {
            case 0 -> renderBindings(g);
            case 1 -> renderCodex(g);
            case 2 -> renderInscriptions(g);
            case 3 -> renderRecipes(g);
            default -> renderBindings(g);
        }

        drawBottomButtons(g, mx, my);
    }

    private void renderBindings(GuiGraphics g) {
        int x = contentX();
        int y = contentY();
        int w = contentW();
        int headerH = 20;
        int visibleRows = visibleBindingRows();
        int rowH = bindingRowH(visibleRows);
        bindingScroll = clamp(bindingScroll, 0, SLOT_COUNT - visibleRows);
        int slotColX = x;
        int slotColW = w * 16 / 100;
        int glyphColX = x + slotColW;
        int glyphColW = w * 32 / 100;
        int effectColX = x + w * 48 / 100;
        int actionColX = x + w - 76;
        int effectColW = Math.max(24, actionColX - effectColX);
        int actionColW = Math.max(44, x + w - 8 - actionColX);
        boolean detailed = SigillumClientConfig.detailed();

        drawTable(g, x, y, w, headerH + visibleRows * rowH, headerH, rowH, visibleRows);
        drawCenteredInBox(g, "槽位", slotColX, y, slotColW, headerH, INK, 1.0f);
        drawCenteredInBox(g, "绑定字", glyphColX, y, glyphColW, headerH, INK, 1.0f);
        drawCenteredInBox(g, "效果", effectColX, y, effectColW, headerH, INK, 1.0f);
        drawCenteredInBox(g, "操作", actionColX, y, actionColW, headerH, INK, 1.0f);

        drawVLine(g, glyphColX, y, headerH + visibleRows * rowH, GRID);
        drawVLine(g, effectColX, y, headerH + visibleRows * rowH, GRID);
        drawVLine(g, actionColX, y, headerH + visibleRows * rowH, GRID);

        for (int row = 0; row < visibleRows; row++) {
            int slot = bindingScroll + row + 1;
            int rowY = y + headerH + row * rowH;
            String glyph = bindings.get(slot);
            boolean bound = glyph != null && !glyph.isEmpty();
            drawCenteredInBox(g, String.valueOf(slot), slotColX, rowY, slotColW, rowH, PAPER_TX, BRIEF_TEXT_SCALE);

            if (bound) {
                drawGlyphTagCentered(g, glyph, glyphColX, rowY, glyphColW, rowH);
                String effect = GlyphCodex.describe(glyph, detailed);
                if (detailed) {
                    drawWrappedCenteredInBox(g, effect, effectColX + 5, rowY, effectColW - 10, rowH, 2, PAPER_TX, DETAIL_TEXT_SCALE);
                } else {
                    drawCenteredInBox(g, effect, effectColX, rowY, effectColW, rowH, PAPER_TX, BRIEF_TEXT_SCALE);
                }
                int[] cb = clearRect(rowY, rowH);
                g.fill(cb[0], cb[1], cb[0] + cb[2], cb[1] + cb[3], 0x66302118);
                drawBorder(g, cb[0], cb[1], cb[2], cb[3], MUTED_DK);
                drawCenteredInBox(g, "清除", cb[0], cb[1], cb[2], cb[3], PAPER_TX, 1.0f);
            } else {
                drawCenteredInBox(g, "未绑定", glyphColX, rowY, glyphColW, rowH, MUTED_DK, BRIEF_TEXT_SCALE);
                drawCenteredInBox(g, "未绑定", effectColX, rowY, effectColW, rowH, MUTED_DK, BRIEF_TEXT_SCALE);
            }
        }
        drawScrollBar(g, x, y + headerH, w, visibleRows * rowH, SLOT_COUNT, visibleRows, bindingScroll);
    }

    private void renderCodex(GuiGraphics g) {
        int x = contentX();
        int y = contentY();
        int w = contentW();
        List<String> glyphs = GlyphCodex.basicGlyphs();
        int totalRows = (glyphs.size() + 1) / 2;
        int visibleRows = visibleCodexRows(totalRows);
        int rowH = codexRowH(visibleRows);
        int listY = y + 24;
        int panelH = 24 + visibleRows * rowH;
        drawDarkPanel(g, x, y, w, panelH);
        g.fill(x + 1, y + 1, x + w - 1, y + 22, PAPER_DK);
        drawMenuString(g, "基础十二字 · 写符时会实时显示组合效果", x + 16, y + 7, INK);

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
                drawScaledText(g, glyph, rowX, rowY + 1, RED_TX, 1.25f);
                drawWrappedString(g, GlyphCodex.describe(glyph, SigillumClientConfig.detailed()),
                    rowX + 32, rowY + 4, colW - 42, 2, PAPER_TX, 0.72f);
            }
        }
        drawScrollBar(g, x, listY, w, visibleRows * rowH, totalRows, visibleRows, codexScroll);
    }

    private void renderInscriptions(GuiGraphics g) {
        int x = contentX();
        int y = contentY();
        int w = contentW();
        int headerH = 20;
        int totalRows = Math.max(1, inscriptions.size());
        int visibleRows = visibleInscriptionRows(totalRows);
        int rowH = inscriptionRowH(visibleRows);
        inscriptionScroll = clamp(inscriptionScroll, 0, totalRows - visibleRows);
        int tableH = headerH + visibleRows * rowH;
        int posX = x + 12;
        int nameX = x + w * 34 / 100;
        int statusX = x + w * 54 / 100;
        int effectX = x + w * 70 / 100;

        drawTable(g, x, y, w, tableH, headerH, rowH, visibleRows);
        drawMenuString(g, "位置", posX, y + 6, INK);
        drawMenuString(g, "刻印", nameX, y + 6, INK);
        drawMenuString(g, "余势", statusX, y + 6, INK);
        drawMenuString(g, "范围 / 效果", effectX, y + 6, INK);
        drawVLine(g, x + w * 32 / 100, y, tableH, GRID);
        drawVLine(g, x + w * 52 / 100, y, tableH, GRID);
        drawVLine(g, x + w * 68 / 100, y, tableH, GRID);

        if (inscriptions.isEmpty()) {
            int textY = y + headerH + Math.max(4, (rowH - 8) / 2);
            drawMenuString(g, "暂无自己的刻印", posX, textY, MUTED_DK);
            return;
        }

        for (int row = 0; row < visibleRows; row++) {
            int idx = inscriptionScroll + row;
            if (idx >= inscriptions.size()) {
                break;
            }
            OpenMenuPayload.InscriptionEntry entry = inscriptions.get(idx);
            BlockPos pos = BlockPos.of(entry.pos());
            int rowY = y + headerH + row * rowH;
            int textY = rowY + Math.max(3, (rowH - 14) / 2);
            String place = SigillumClientConfig.detailed()
                ? shortDimension(entry.dimension()) + " " + formatPos(pos)
                : formatPos(pos);
            String status = Math.round(clamp01(entry.progress()) * 100.0f) + "%";
            String effect = SigillumClientConfig.detailed()
                ? "半径" + formatRadius(entry.radius()) + (entry.strong() ? " · 强" : "")
                : firstEffect(entry.name());
            drawWrappedString(g, place, posX, textY, nameX - posX - 10, 2, PAPER_TX, 0.72f);
            drawWrappedString(g, entry.name(), nameX, textY, statusX - nameX - 10, 2, PAPER_TX, 0.8f);
            drawWrappedString(g, status, statusX, textY, effectX - statusX - 10, 1, PAPER_TX, 0.9f);
            drawWrappedString(g, effect, effectX, textY, x + w - effectX - 12, 2, PAPER_TX, 0.72f);
        }
        drawScrollBar(g, x, y + headerH, w, visibleRows * rowH, totalRows, visibleRows, inscriptionScroll);
    }

    private void renderRecipes(GuiGraphics g) {
        int x = contentX();
        int y = contentY();
        int w = contentW();
        int h = contentH();
        drawDarkPanel(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + 22, PAPER_DK);
        drawMenuString(g, recipePage == 0 ? "空白黄符" : "拓印", x + 16, y + 7, INK);
        drawMenuString(g, (recipePage + 1) + "/2", x + w - 32, y + 7, INK);

        if (recipePage == 0) {
            renderBlankTalismanRecipe(g, x, y, w);
        } else {
            renderCopyRecipe(g, x, y, w);
        }
    }

    private void renderBlankTalismanRecipe(GuiGraphics g, int x, int y, int w) {
        int gridX = x + 36;
        int gridY = y + 48;
        int s = recipeSlotSize();
        drawRecipeSlot(g, gridX, gridY, s, "纸", PAPER_TX);
        drawRecipeSlot(g, gridX + s + 5, gridY, s, "纸", PAPER_TX);
        drawRecipeSlot(g, gridX, gridY + s + 5, s, "纸", PAPER_TX);
        drawRecipeSlot(g, gridX + s + 5, gridY + s + 5, s, "黄", PAPER_TX);
        drawScaledCenteredString(g, ">", gridX + s * 2 + 32, gridY + s - 4, PAPER_TX, 1.4f);
        drawRecipeSlot(g, gridX + s * 2 + 58, gridY + (s + 5) / 2, s, "黄符x6", PAPER_TX);

        int textX = x + Math.max(210, w * 42 / 100);
        int textY = y + 50;
        drawInfoLines(g, textX, textY, x + w - textX - 20, List.of(
            "无序合成：3纸 + 1黄色染料。",
            "产出6张空白黄符。",
            "空白黄符只能放在工作台上书写。"
        ));
    }

    private void renderCopyRecipe(GuiGraphics g, int x, int y, int w) {
        int gridX = x + 26;
        int gridY = y + 40;
        int s = recipeSlotSize();
        String[][] cells = {
            {"空符", "空符", "空符"},
            {"空符", "成符", "空符"},
            {"空符", "朱砂", "空符"}
        };
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawRecipeSlot(g, gridX + col * (s + 5), gridY + row * (s + 5), s, cells[row][col], PAPER_TX);
            }
        }
        drawScaledCenteredString(g, ">", gridX + s * 3 + 30, gridY + s + 4, PAPER_TX, 1.4f);
        drawRecipeSlot(g, gridX + s * 3 + 56, gridY + s + 5, s, "成符x2", PAPER_TX);

        int textX = x + Math.max(236, w * 46 / 100);
        int textY = y + 44;
        drawInfoLines(g, textX, textY, x + w - textX - 20, List.of(
            "有序3x3拓印。",
            "中心放已写黄符，下中放红色染料。",
            "其余7格放空白黄符。",
            "产出2张相同成符。"
        ));
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
        String title = titleText();
        drawScaledCenteredString(g, title, center, y, HOT_TX, 2.0f);
        int sealX = center + menuWidth(title) + 8;
        int sealY = y - 1;
        g.fill(sealX, sealY, sealX + 14, sealY + 22, 0x882A120C);
        drawBorder(g, sealX, sealY, 14, 22, RED);
        drawMenuString(g, "符", sealX + 4, sealY + 3, RED_LT);
        drawMenuString(g, "咒", sealX + 4, sealY + 12, RED_LT);
    }

    private String titleText() {
        return switch (activeTab) {
            case 0 -> "快速吟唱设置";
            case 1 -> "黄符字典";
            case 2 -> "刻印录";
            case 3 -> "合成";
            default -> "符咒簿";
        };
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
        if (activeTab == 0) {
            drawButton(g, clearAllRect(), "全部清空", PAPER_DK, mx, my);
        } else if (activeTab == 3) {
            drawButton(g, clearAllRect(), recipePage == 0 ? "拓印配方" : "空符配方", PAPER_DK, mx, my);
        }
        drawButton(g, toggleDetailRect(), SigillumClientConfig.detailed() ? "简略信息" : "详细信息", PAPER_DK, mx, my);
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
        int h = glyphTagHeight(rowH);
        int w = glyphTagWidth(h);
        int y = rowY + (rowH - h) / 2;
        g.fill(x, y, x + w, y + h, PAPER);
        drawBorder(g, x, y, w, h, EDGE);
        int color = "火裂心".contains(glyph) ? RED_TX : INK;
        drawCenteredInBox(g, glyph, x, y, w, h, color, 1.1f);
    }

    private void drawGlyphTagCentered(GuiGraphics g, String glyph, int x, int rowY, int colW, int rowH) {
        int w = glyphTagWidth(glyphTagHeight(rowH));
        drawGlyphTag(g, glyph, x + (colW - w) / 2, rowY, rowH);
    }

    private int glyphTagHeight(int rowH) {
        return Math.max(15, Math.min(20, rowH - 8));
    }

    private int glyphTagWidth(int h) {
        return Math.max(16, Math.min(21, h + 1));
    }

    private void drawRecipeSlot(GuiGraphics g, int x, int y, int size, String label, int color) {
        g.fill(x, y, x + size, y + size, 0x66302118);
        drawBorder(g, x, y, size, size, EDGE);
        drawBorder(g, x + 2, y + 2, size - 4, size - 4, 0x553B2D1B);
        float scale = label.length() > 2 ? 0.75f : 1.0f;
        drawCenteredInBox(g, label, x, y, size, size, color, scale);
    }

    private void drawInfoLines(GuiGraphics g, int x, int y, int maxW, List<String> lines) {
        int yy = y;
        for (String line : lines) {
            yy += drawWrappedString(g, line, x, yy, maxW, 3, PAPER_TX, 0.82f) + 6;
        }
    }

    private int drawWrappedString(GuiGraphics g, String text, int x, int y, int maxW, int maxLines, int color, float scale) {
        if (maxW <= 0 || maxLines <= 0 || text == null || text.isEmpty()) {
            return 0;
        }
        int available = Math.max(1, (int)(maxW / scale));
        int lineH = Math.max(6, Math.round(this.font.lineHeight * scale));
        String remaining = text;
        int used = 0;
        for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
            boolean lastLine = line == maxLines - 1;
            String value = this.font.plainSubstrByWidth(remaining, available);
            if (value.isEmpty()) {
                break;
            }
            remaining = remaining.substring(value.length());
            if (lastLine && !remaining.isEmpty()) {
                String ellipsis = "...";
                int clippedWidth = Math.max(1, available - menuWidth(ellipsis));
                value = this.font.plainSubstrByWidth(value, clippedWidth) + ellipsis;
                remaining = "";
            }
            drawScaledText(g, value, x, y + used, color, scale);
            used += lineH;
        }
        return used;
    }

    private void drawWrappedCenteredInBox(GuiGraphics g, String text, int x, int y, int w, int h, int maxLines, int color, float scale) {
        if (w <= 0 || h <= 0 || maxLines <= 0 || text == null || text.isEmpty()) {
            return;
        }
        int available = Math.max(1, (int)(w / scale));
        int lineH = Math.max(6, Math.round(this.font.lineHeight * scale));
        String[] lines = new String[maxLines];
        int count = 0;
        String remaining = text;
        while (count < maxLines && !remaining.isEmpty()) {
            boolean lastLine = count == maxLines - 1;
            String value = this.font.plainSubstrByWidth(remaining, available);
            if (value.isEmpty()) {
                break;
            }
            remaining = remaining.substring(value.length());
            if (lastLine && !remaining.isEmpty()) {
                String ellipsis = "...";
                int clippedWidth = Math.max(1, available - menuWidth(ellipsis));
                value = this.font.plainSubstrByWidth(value, clippedWidth) + ellipsis;
                remaining = "";
            }
            lines[count++] = value;
        }
        int totalH = count * lineH;
        int textY = y + Math.max(0, (h - totalH) / 2);
        for (int i = 0; i < count; i++) {
            String line = lines[i];
            int textX = Math.round(x + (w - menuWidth(line) * scale) / 2.0f);
            drawScaledText(g, line, textX, textY + i * lineH, color, scale);
        }
    }

    private void drawTrimmedString(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        if (maxW <= 0 || text == null || text.isEmpty()) {
            return;
        }
        String value = text;
        if (menuWidth(value) > maxW) {
            String ellipsis = "...";
            int ellipsisW = menuWidth(ellipsis);
            if (maxW <= ellipsisW) {
                return;
            }
            value = this.font.plainSubstrByWidth(value, maxW - ellipsisW) + ellipsis;
        }
        drawMenuString(g, value, x, y, color);
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
        pose.translate(centerX - menuWidth(text) * scale / 2.0f, y, 0.0f);
        pose.scale(scale, scale, 1.0f);
        drawMenuString(g, text, 0, 0, color);
        pose.popPose();
    }

    private void drawCenteredInBox(GuiGraphics g, String text, int x, int y, int w, int h, int color, float scale) {
        int textX = Math.round(x + (w - menuWidth(text) * scale) / 2.0f);
        int textY = Math.round(y + (h - this.font.lineHeight * scale) / 2.0f);
        drawScaledText(g, text, textX, textY, color, scale);
    }

    private void drawScaledText(GuiGraphics g, String text, int x, int y, int color, float scale) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0f);
        pose.scale(scale, scale, 1.0f);
        drawMenuString(g, text, 0, 0, color);
        pose.popPose();
    }

    private void drawMenuString(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(this.font, text, x, y, color, false);
    }

    private int menuWidth(String text) {
        return this.font.width(text);
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
                if (activeTab == 0) {
                    for (int slot = 1; slot <= SLOT_COUNT; slot++) {
                        if (bindings.containsKey(slot)) {
                            SigillumClientHooks.clearBinding(slot);
                        }
                    }
                    bindings.clear();
                    return true;
                }
                if (activeTab == 3) {
                    recipePage = 1 - recipePage;
                    return true;
                }
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
        } else if (activeTab == 1) {
            int totalRows = (GlyphCodex.basicGlyphs().size() + 1) / 2;
            int visibleRows = visibleCodexRows(totalRows);
            int next = clamp(codexScroll + delta, 0, totalRows - visibleRows);
            if (next != codexScroll) {
                codexScroll = next;
                return true;
            }
        } else if (activeTab == 2) {
            int totalRows = Math.max(1, inscriptions.size());
            int visibleRows = visibleInscriptionRows(totalRows);
            int next = clamp(inscriptionScroll + delta, 0, totalRows - visibleRows);
            if (next != inscriptionScroll) {
                inscriptionScroll = next;
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

    private int inscriptionRowH(int visibleRows) {
        int available = Math.max(22, contentH() - 20);
        return Math.max(22, Math.min(28, available / Math.max(1, visibleRows)));
    }

    private int visibleInscriptionRows(int totalRows) {
        int available = Math.max(22, contentH() - 20);
        return Math.max(1, Math.min(totalRows, available / 22));
    }

    private int recipeSlotSize() {
        return Math.max(24, Math.min(34, panelH() / 14));
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
        return new int[] {contentX() + contentW() - 56, rowY + (rowH - h) / 2, 44, h};
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String shortDimension(String dimension) {
        if ("minecraft:overworld".equals(dimension)) return "主世界";
        if ("minecraft:the_nether".equals(dimension)) return "下界";
        if ("minecraft:the_end".equals(dimension)) return "末地";
        int idx = dimension.indexOf(':');
        return idx >= 0 ? dimension.substring(idx + 1) : dimension;
    }

    private static String formatRadius(float radius) {
        if (Math.abs(radius - Math.rint(radius)) < 0.001) {
            return (int)Math.rint(radius) + "格";
        }
        return String.format(java.util.Locale.ROOT, "%.1f格", radius);
    }

    private static String firstEffect(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String first = name.split("\\+")[0];
        return GlyphCodex.describe(first, false);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
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
