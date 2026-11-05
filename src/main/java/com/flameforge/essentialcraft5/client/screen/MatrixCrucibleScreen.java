package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.client.matrix.MatrixClient;
import com.flameforge.essentialcraft5.matrix.MatrixCodex;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.matrix.MatrixTrialChecks;
import com.flameforge.essentialcraft5.menu.MatrixCrucibleMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 矩阵淬炼台界面（第十三批 13a，batch13.md §3.1.2、§3.1.5）。256 × 246，面板照原版界面的边框画，不加新贴图：
 * <ul>
 *   <li>左边 (5, 16) 起：九阶表，每行 12 像素，阶徽缩到 10 像素；到过的绿、下一阶紫、没到的灰，悬停看这一阶要什么。</li>
 *   <li>右边 (97, 16) 起：下一阶标题，下面每行 9 像素逐条打勾（图谱、属性、站点、祭品、能量、本人），最多 8 行；悬停看细节。</li>
 *   <li>(97, 104) 绑定宝石格，(121, 104) 起四个祭品格（空着时淡淡画出这一阶要的祭品），(197, 103) 开始按钮（冷却、进行中时写在按钮上）。</li>
 *   <li>(5, 130) 起：图谱按组翻看，标题行左边切组、右边翻页，下面两行三列实体名。</li>
 *   <li>背包 (48, 164)，快捷栏 y = 222。</li>
 * </ul>
 * 下一阶的条件、站点、祭品、能量都由服务端算好随矩阵状态包发来（{@link MatrixClient#lastStatus()}），界面只解释，不自己判；
 * 图谱名单读客户端玩家数据（图谱包同步来的），分组用实体标签（客户端也有）。
 */
public class MatrixCrucibleScreen extends MachineScreen<MatrixCrucibleMenu> {

    private static final String KEY = "gui.essentialcraft5.matrix_crucible.";

    public static final int WIDTH = 256;
    public static final int HEIGHT = 246;

    private static final int TABLE_X = 5;
    private static final int TABLE_Y = 16;
    private static final int ROW_H = 12;
    private static final int RIGHT_X = 97;
    private static final int RIGHT_W = 154;
    private static final int COND_Y = 27;
    private static final int COND_H = 9;
    private static final int COND_MAX = 8;
    private static final int CODEX_Y = 130;
    private static final int CODEX_ROW_Y = 141;
    /** 图谱一行右边「‹ 1 / 2 ›」里页码的右端；翻页箭头画在它右边 3 像素，离图谱框的右边线留 3 像素（原来是 243，「›」贴着边线） */
    private static final int PAGE_RIGHT = 240;
    private static final int CODEX_COLUMNS = 3;
    private static final int CODEX_COLUMN_W = 82;
    private static final int CODEX_PER_PAGE = CODEX_COLUMNS * 2;
    private static final int BUTTON_X = 197;
    private static final int BUTTON_Y = 103;

    // 面板上的字（灰底）
    private static final int LABEL = 0x404040;
    // 深色内框里的字
    private static final int TEXT = 0xE0E0E0;
    private static final int DIM = 0x8A8A8A;
    private static final int OK = 0x7FE07F;
    private static final int BAD = 0xFF7A7A;
    private static final int NEXT = 0xD8A8FF;
    private static final int INSET = 0xFF262130;

    private static final MatrixCodex.Group[] GROUPS = MatrixCodex.Group.values();

    @Nullable
    private Button startButton;
    private int codexGroup;
    private int codexPage;
    /** 翻页「<」的位置（随页码宽度变，绘制时记下） */
    private int pagePrevX = 200;
    /** 图谱名字列表的缓存：同一组、同一份玩家数据、条数没变就不重排（图谱最多上千条，每帧排一遍不划算） */
    @Nullable
    private List<Component> codexNamesCache;
    @Nullable
    private MatrixCodex.Group codexCacheGroup;
    @Nullable
    private IPlayerData codexCacheData;
    private int codexCacheSize = -1;

    /** 右边一行：打勾、文字、悬停说明 */
    private record Line(boolean ok, Component text, List<Component> tooltip) {
    }

    public MatrixCrucibleScreen(MatrixCrucibleMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        startButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "button.start"), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MatrixCrucibleMenu.BUTTON_START);
            }
        }).bounds(leftPos + BUTTON_X, topPos + BUTTON_Y, 54, 18).build());
    }

    private static CompoundTag status() {
        return MatrixClient.lastStatus();
    }

    private static CompoundTag crucible() {
        return status().getCompound("crucible");
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (startButton != null) {
            CompoundTag c = crucible();
            startButton.setMessage(buttonText(c));
            // 台子锁着（自己或别人在试炼）时按不了
            startButton.active = !c.getBoolean("locked");
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        List<Component> tooltip = hoverTooltip(mouseX - leftPos, mouseY - topPos);
        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        panel(graphics, x, y, WIDTH, HEIGHT);
        inset(graphics, x + 4, y + 14, 90, 112);
        inset(graphics, x + 96, y + 14, 156, 87);
        inset(graphics, x + 4, y + 128, 248, 32);
        for (Slot slot : menu.slots) {
            graphics.blit(ECTextures.GENERIC_BACKGROUND, x + slot.x - 1, y + slot.y - 1, 7, 83, 18, 18);
        }
        renderGhostOfferings(graphics, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        CompoundTag status = status();
        graphics.drawString(font, title, 8, 4, LABEL, false);
        if (status.contains("reserve")) {
            Component reserve = Component.translatable(KEY + "reserve", status.getInt("reserve"));
            graphics.drawString(font, reserve, WIDTH - 8 - font.width(reserve), 4, LABEL, false);
        }
        renderRankTable(graphics, status);
        renderNext(graphics, status);
        renderCodex(graphics);
    }

    private void renderRankTable(GuiGraphics graphics, CompoundTag status) {
        int rank = status.getInt("rank");
        ListTag rows = status.getList("ranks", Tag.TAG_COMPOUND);
        for (int n = 1; n <= MatrixRank.MAX; n++) {
            int rowY = TABLE_Y + (n - 1) * ROW_H;
            boolean available = rows.size() < n || rows.getCompound(n - 1).getBoolean("available");
            int color = n <= rank ? OK : n == rank + 1 && available ? NEXT : DIM;
            if (n == rank + 1 && available) {
                graphics.fill(TABLE_X, rowY - 1, TABLE_X + 88, rowY + ROW_H - 1, 0x40B080FF);
            }
            float shade = n <= rank || n == rank + 1 ? 1F : 0.45F;
            graphics.setColor(shade, shade, shade, 1F);
            graphics.blit(badge(n), TABLE_X + 1, rowY, 10, 10, 0F, 0F, 16, 16, 16, 16);
            graphics.setColor(1F, 1F, 1F, 1F);
            Component text = Component.translatable(KEY + "rank_row", MatrixRank.rankName(n), MatrixRank.title(n));
            // 底框右边线在 TABLE_X + 88，字（含阴影）离它留 2 像素
            drawFit(graphics, text, TABLE_X + 14, rowY + 1, 72, color);
        }
    }

    private void renderNext(GuiGraphics graphics, CompoundTag status) {
        Component header;
        int color = TEXT;
        if (status.isEmpty()) {
            header = Component.translatable(KEY + "loading");
            color = DIM;
        } else if (!status.getBoolean("enabled")) {
            header = Component.translatable(KEY + "disabled");
            color = BAD;
        } else if (status.getInt("rank") >= MatrixRank.MAX) {
            header = Component.translatable(KEY + "max");
            color = OK;
        } else if (!status.contains("next")) {
            header = Component.translatable(KEY + "no_data");
            color = BAD;
        } else {
            int next = status.getCompound("next").getInt("rank");
            header = Component.translatable(KEY + "next", MatrixRank.rankName(next), MatrixRank.title(next));
            color = NEXT;
        }
        drawFit(graphics, header, RIGHT_X + 2, TABLE_Y, RIGHT_W - 4, color);
        List<Line> lines = lines(status);
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            int lineY = COND_Y + i * COND_H;
            graphics.drawString(font, line.ok() ? "✔" : "✘", RIGHT_X + 2, lineY, line.ok() ? OK : BAD, true);
            drawFit(graphics, line.text(), RIGHT_X + 12, lineY, RIGHT_W - 14, line.ok() ? TEXT : 0xFFD0D0);
        }
    }

    private void renderCodex(GuiGraphics graphics) {
        MatrixCodex.Group group = GROUPS[Math.floorMod(codexGroup, GROUPS.length)];
        List<Component> names = codexNames(group);
        int pages = Math.max(1, (names.size() + CODEX_PER_PAGE - 1) / CODEX_PER_PAGE);
        codexPage = Math.max(0, Math.min(codexPage, pages - 1));
        graphics.drawString(font, "<", TABLE_X + 1, CODEX_Y, NEXT, true);
        Component title = Component.translatable(KEY + "codex", Component.translatable(group.nameKey()), names.size());
        drawFit(graphics, title, TABLE_X + 10, CODEX_Y, 150, TEXT);
        graphics.drawString(font, ">", TABLE_X + 163, CODEX_Y, NEXT, true);
        Component page = Component.translatable(KEY + "codex.page", codexPage + 1, pages);
        graphics.drawString(font, page, PAGE_RIGHT - font.width(page), CODEX_Y, DIM, true);
        pagePrevX = PAGE_RIGHT - font.width(page) - 9;
        graphics.drawString(font, "<", pagePrevX, CODEX_Y, pages > 1 ? NEXT : DIM, true);
        graphics.drawString(font, ">", PAGE_RIGHT + 3, CODEX_Y, pages > 1 ? NEXT : DIM, true);
        if (names.isEmpty()) {
            graphics.drawString(font, Component.translatable(KEY + "codex.empty"), TABLE_X + 2, CODEX_ROW_Y, DIM, true);
            return;
        }
        for (int i = 0; i < CODEX_PER_PAGE; i++) {
            int index = codexPage * CODEX_PER_PAGE + i;
            if (index >= names.size()) {
                break;
            }
            int col = i % CODEX_COLUMNS;
            int row = i / CODEX_COLUMNS;
            drawFit(graphics, names.get(index), TABLE_X + 2 + col * CODEX_COLUMN_W, CODEX_ROW_Y + row * 9, CODEX_COLUMN_W - 4, TEXT);
        }
    }

    /** 空着的祭品格里淡淡画出这一阶要的祭品（同一项有多种候选时每秒换一种） */
    private void renderGhostOfferings(GuiGraphics graphics, int x, int y) {
        ListTag offerings = status().getCompound("next").getList("offerings", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(offerings.size(), 4); i++) {
            Slot slot = menu.slots.get(1 + i);
            if (slot.hasItem()) {
                continue;
            }
            ItemStack ghost = offeringStack(offerings.getCompound(i));
            if (ghost.isEmpty()) {
                continue;
            }
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.renderFakeItem(ghost, sx, sy);
            graphics.fill(RenderType.guiGhostRecipeOverlay(), sx, sy, sx + 16, sy + 16, 0x808B8B8B);
            graphics.renderItemDecorations(font, ghost, sx, sy);
        }
    }

    // ------------------------------------------------------------------ 右边逐条

    private List<Line> lines(CompoundTag status) {
        List<Line> out = new ArrayList<>();
        if (!status.contains("next")) {
            return out;
        }
        CompoundTag next = status.getCompound("next");
        CompoundTag c = status.getCompound("crucible");
        boolean peaceful = status.getBoolean("peaceful");
        List<Component> peacefulNote = peaceful ? List.of(Component.translatable("gui.essentialcraft5.matrix_rank.condition.peaceful")
                .withStyle(ChatFormatting.GRAY)) : List.of();
        for (Tag raw : next.getList("conditions", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            String kind = row.getString("kind");
            String key = row.getString("key");
            boolean ok = row.getBoolean("ok");
            Component text = switch (kind) {
                case "codex" -> {
                    MatrixCodex.Group group = MatrixCodex.Group.byKey(key);
                    Component name = group == null ? Component.literal(key) : Component.translatable(group.nameKey());
                    yield Component.translatable("gui.essentialcraft5.matrix_rank.condition.codex", name, row.getInt("have"), row.getInt("need"));
                }
                case "include" -> Component.translatable("gui.essentialcraft5.matrix_rank.condition.include", entityName(key));
                case "alignment" -> Component.translatable("gui.essentialcraft5.matrix_rank.condition.alignment");
                default -> Component.literal(key);
            };
            out.add(new Line(ok, text, "alignment".equals(kind) ? List.of(Component.translatable(KEY + "alignment.hint")
                    .withStyle(ChatFormatting.GRAY)) : peacefulNote));
        }
        CompoundTag site = c.getCompound("site");
        String state = site.getString("state");
        if (!state.isEmpty()) {
            Component text = Component.translatable(KEY + "site." + state, site.getInt("need_tier"), Math.max(0, site.getInt("found_tier")),
                    site.getInt("espe"), site.getInt("espe_need"));
            boolean ok = "ok".equals(state) || "not_needed".equals(state);
            out.add(new Line(ok, text, List.of(Component.translatable(KEY + "site.hint").withStyle(ChatFormatting.GRAY))));
        }
        ListTag offerings = next.getList("offerings", Tag.TAG_COMPOUND);
        if (!offerings.isEmpty()) {
            ListTag each = c.getList("offerings", Tag.TAG_BYTE);
            int matched = 0;
            List<Component> tooltip = new ArrayList<>();
            for (int i = 0; i < offerings.size(); i++) {
                boolean ok = i < each.size() && ((net.minecraft.nbt.ByteTag) each.get(i)).getAsByte() != 0;
                if (ok) {
                    matched++;
                }
                CompoundTag row = offerings.getCompound(i);
                ItemStack stack = offeringStack(row);
                tooltip.add(Component.literal(ok ? "✔ " : "✘ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .append(Component.translatable(KEY + "offering", row.getInt("count"), stack.getHoverName()).withStyle(ChatFormatting.WHITE)));
            }
            tooltip.add(Component.translatable(KEY + "offerings.hint").withStyle(ChatFormatting.GRAY));
            out.add(new Line(c.getBoolean("offerings_ok"), Component.translatable(KEY + "offerings", matched, offerings.size()), tooltip));
        }
        int ubmruNeed = c.getInt("ubmru_need");
        int reserve = c.getInt("reserve");
        int ubmru = Math.max(0, c.getInt("ubmru"));
        int mruNeed = c.getInt("mru_need");
        int mru = c.getInt("mru");
        int espeNeed = site.getInt("espe_need");
        int espe = site.getInt("espe");
        boolean ubmruOk = (long) reserve + ubmru >= ubmruNeed;
        boolean mruOk = mru >= mruNeed;
        boolean espeOk = espeNeed <= 0 || "ok".equals(state);
        MutableComponent energy = Component.translatable(KEY + "energy");
        List<Component> energyTip = new ArrayList<>();
        if (ubmruNeed > 0) {
            energy.append(part("UBMRU", ubmruOk));
            energyTip.add(Component.translatable(KEY + "energy.ubmru", ubmruNeed, reserve, ubmru));
            energyTip.add(Component.translatable(KEY + "energy.reserve_hint", c.getInt("reserve_cap")).withStyle(ChatFormatting.GRAY));
        }
        if (mruNeed > 0) {
            energy.append(part("MRU", mruOk));
            energyTip.add(Component.translatable(KEY + "energy.mru", mru, mruNeed));
            if (c.contains("mru_cap") && mruNeed > c.getInt("mru_cap")) {
                energyTip.add(Component.translatable(KEY + "blocker." + MatrixTrialChecks.Blocker.MRU_CAPACITY.key()).withStyle(ChatFormatting.RED));
            }
        }
        if (espeNeed > 0) {
            energy.append(part("ESPE", espeOk));
            energyTip.add(Component.translatable(KEY + "energy.espe", espe, espeNeed));
        }
        if (!energyTip.isEmpty()) {
            out.add(new Line(ubmruOk && mruOk && espeOk, energy, energyTip));
        }
        boolean hand = c.getBoolean("hand_ok");
        boolean near = c.getBoolean("near_ok");
        boolean health = c.getBoolean("health_ok");
        MutableComponent self = Component.translatable(KEY + "self");
        self.append(part(Component.translatable(KEY + "self.soul_stone"), hand));
        self.append(part(Component.translatable(KEY + "self.near"), near));
        self.append(part(Component.translatable(KEY + "self.health"), health));
        out.add(new Line(hand && near && health, self, List.of(
                check(hand, Component.translatable(KEY + "self.soul_stone.hint")),
                check(near, Component.translatable(KEY + "self.near.hint")),
                check(health, Component.translatable(KEY + "self.health.hint")))));
        if (out.size() < COND_MAX) {
            CompoundTag echoes = next.getCompound("echoes");
            if (c.getBoolean("channel")) {
                out.add(new Line(true, Component.translatable(KEY + "channel", c.getInt("channel_seconds")),
                        List.of(Component.translatable(KEY + "channel.hint").withStyle(ChatFormatting.GRAY))));
            } else {
                out.add(new Line(true, Component.translatable(KEY + "echoes", echoes.getInt("count"), echoes.getInt("time_seconds")),
                        List.of(Component.translatable(KEY + "echoes.hint", echoes.getInt("alive"), echoes.getInt("health"), echoes.getInt("damage"))
                                .withStyle(ChatFormatting.GRAY))));
            }
        }
        return out.size() > COND_MAX ? out.subList(0, COND_MAX) : out;
    }

    private static Component part(String label, boolean ok) {
        return part(Component.literal(label), ok);
    }

    private static Component part(Component label, boolean ok) {
        return Component.literal(" ").append(label.copy().withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static Component check(boolean ok, Component text) {
        return Component.literal(ok ? "✔ " : "✘ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(text.copy().withStyle(ChatFormatting.WHITE));
    }

    private Component buttonText(CompoundTag c) {
        if (c.getBoolean("running")) {
            return Component.translatable(KEY + "button.running");
        }
        int cooldown = c.getInt("cooldown");
        if (cooldown > 0) {
            return Component.translatable(KEY + "button.cooldown", (cooldown + 19) / 20);
        }
        if (c.getBoolean("locked")) {
            return Component.translatable(KEY + "button.busy");
        }
        return Component.translatable(KEY + "button.start");
    }

    // ------------------------------------------------------------------ 悬停

    private List<Component> hoverTooltip(int x, int y) {
        CompoundTag status = status();
        // 九阶表
        if (x >= TABLE_X && x < TABLE_X + 88 && y >= TABLE_Y - 1 && y < TABLE_Y + MatrixRank.MAX * ROW_H - 1) {
            int n = (y - TABLE_Y + 1) / ROW_H + 1;
            return rankTooltip(status, n);
        }
        // 右边逐条
        if (x >= RIGHT_X && x < RIGHT_X + RIGHT_W && y >= COND_Y && y < COND_Y + COND_MAX * COND_H) {
            List<Line> lines = lines(status);
            int index = (y - COND_Y) / COND_H;
            if (index < lines.size()) {
                return lines.get(index).tooltip();
            }
        }
        // 开始按钮：还差什么
        if (x >= BUTTON_X && x < BUTTON_X + 54 && y >= BUTTON_Y && y < BUTTON_Y + 18) {
            CompoundTag c = status.getCompound("crucible");
            if (c.getBoolean("ready") || c.getBoolean("running")) {
                return List.of(Component.translatable(KEY + "button.hint").withStyle(ChatFormatting.GRAY));
            }
            List<Component> out = new ArrayList<>();
            out.add(Component.translatable(KEY + "button.missing"));
            for (Tag raw : c.getList("blockers", Tag.TAG_STRING)) {
                out.add(Component.literal("✘ ").withStyle(ChatFormatting.RED)
                        .append(Component.translatable(KEY + "blocker." + raw.getAsString()).withStyle(ChatFormatting.WHITE)));
            }
            return out;
        }
        // 空着的祭品格
        // 按菜单里的序号认（机器槽排在最前面：0 宝石、1~4 祭品）；Slot.index 是各自容器里的序号，背包格也有 1~4
        int menuIndex = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menuIndex >= 1 && menuIndex <= 4) {
            ListTag offerings = status.getCompound("next").getList("offerings", Tag.TAG_COMPOUND);
            List<Component> out = new ArrayList<>();
            if (menu.isLocked()) {
                out.add(Component.translatable(KEY + "slot.locked").withStyle(ChatFormatting.YELLOW));
            }
            int i = menuIndex - 1;
            if (i < offerings.size()) {
                CompoundTag row = offerings.getCompound(i);
                out.add(Component.translatable(KEY + "offering", row.getInt("count"), offeringStack(row).getHoverName()));
            }
            out.add(Component.translatable(KEY + "offerings.hint").withStyle(ChatFormatting.GRAY));
            return out;
        }
        return List.of();
    }

    private List<Component> rankTooltip(CompoundTag status, int n) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(KEY + "rank_row", MatrixRank.rankName(n), MatrixRank.title(n)).withStyle(ChatFormatting.LIGHT_PURPLE));
        int rank = status.getInt("rank");
        ListTag rows = status.getList("ranks", Tag.TAG_COMPOUND);
        CompoundTag row = n <= rows.size() ? rows.getCompound(n - 1) : new CompoundTag();
        if (!row.getBoolean("available")) {
            out.add(Component.translatable(KEY + "rank.unavailable").withStyle(ChatFormatting.GRAY));
            return out;
        }
        out.add(Component.translatable(KEY + (n <= rank ? "rank.done" : n == rank + 1 ? "rank.next" : "rank.locked"))
                .withStyle(n <= rank ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        out.add(Component.translatable(KEY + "rank.codex", row.getInt("codex_total")).withStyle(ChatFormatting.GRAY));
        int tier = row.getInt("pentacle_tier");
        out.add((tier < 0 ? Component.translatable(KEY + "rank.site.none") : Component.translatable(KEY + "rank.site.tier", tier))
                .withStyle(ChatFormatting.GRAY));
        MutableComponent energy = Component.translatable(KEY + "rank.energy");
        if (row.getInt("ubmru") > 0) {
            energy.append(" UBMRU " + row.getInt("ubmru"));
        }
        if (row.getInt("mru") > 0) {
            energy.append(" MRU " + row.getInt("mru"));
        }
        if (row.getInt("espe") > 0) {
            energy.append(" ESPE " + row.getInt("espe"));
        }
        out.add(energy.withStyle(ChatFormatting.GRAY));
        return out;
    }

    // ------------------------------------------------------------------ 图谱翻页

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - topPos;
        if (button == 0 && y >= CODEX_Y - 2 && y < CODEX_Y + 10) {
            if (x >= TABLE_X - 1 && x < TABLE_X + 8) {
                codexGroup = Math.floorMod(codexGroup - 1, GROUPS.length);
                codexPage = 0;
                click();
                return true;
            }
            if (x >= TABLE_X + 161 && x < TABLE_X + 171) {
                codexGroup = Math.floorMod(codexGroup + 1, GROUPS.length);
                codexPage = 0;
                click();
                return true;
            }
            if (x >= pagePrevX - 2 && x < pagePrevX + 8) {
                codexPage = Math.max(0, codexPage - 1);
                click();
                return true;
            }
            if (x >= PAGE_RIGHT + 1 && x < PAGE_RIGHT + 11) {
                codexPage++;
                click();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
        }
    }

    /** 这一组里记过的实体名（按 id 排序；删了模组的只在总数里写 id）。图谱只增不改（清空时条数变 0），按组和条数缓存 */
    private List<Component> codexNames(MatrixCodex.Group group) {
        List<Component> out = new ArrayList<>();
        IPlayerData data = minecraft == null || minecraft.player == null ? null : PlayerDataEvents.get(minecraft.player);
        if (data == null) {
            return out;
        }
        Collection<String> ids = group == MatrixCodex.Group.HOANNA ? data.getMatrixCodexHoanna() : data.getMatrixCodex();
        if (codexNamesCache != null && codexCacheGroup == group && codexCacheData == data && codexCacheSize == ids.size()) {
            return codexNamesCache;
        }
        codexCacheGroup = group;
        codexCacheData = data;
        codexCacheSize = ids.size();
        codexNamesCache = out;
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(null);
        for (String id : sorted) {
            EntityType<?> type = MatrixCodex.type(id);
            if (type == null) {
                if (group == MatrixCodex.Group.TOTAL || group == MatrixCodex.Group.HOANNA) {
                    out.add(Component.literal(id));
                }
            } else if (group == MatrixCodex.Group.HOANNA || group.contains(type)) {
                out.add(type.getDescription());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 小工具

    private static Component entityName(String id) {
        EntityType<?> type = MatrixCodex.type(id);
        return type == null ? Component.literal(id) : type.getDescription();
    }

    /** 一项祭品要画成哪个物品（候选多时每秒换一种），数量照这一项 */
    private static ItemStack offeringStack(CompoundTag row) {
        ListTag items = row.getList("items", Tag.TAG_STRING);
        if (items.isEmpty()) {
            return new ItemStack(Items.BARRIER);
        }
        int index = (int) (Util.getMillis() / 1000L % items.size());
        ResourceLocation id = ResourceLocation.tryParse(items.getString(index));
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, row.getInt("count")));
    }

    private static ResourceLocation badge(int rank) {
        return EssentialCraft5.id("textures/gui/matrix_rank/rank_" + rank + ".png");
    }

    /** 写不下就横向压一压（最多压到 70%），再写不下截断 */
    private void drawFit(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        int width = font.width(text);
        if (width <= maxWidth) {
            graphics.drawString(font, text, x, y, color, color != LABEL);
            return;
        }
        float scale = Math.max(0.7F, maxWidth / (float) width);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0F);
        graphics.pose().scale(scale, 1F, 1F);
        if (width * scale <= maxWidth + 0.5F) {
            graphics.drawString(font, text, 0, 0, color, color != LABEL);
        } else {
            String plain = font.plainSubstrByWidth(text.getString(), (int) (maxWidth / scale) - font.width("…")) + "…";
            graphics.drawString(font, Component.literal(plain).withStyle(text.getStyle()), 0, 0, color, color != LABEL);
        }
        graphics.pose().popPose();
    }

    /** 原版界面那样的外框：黑边、白色高光、灰底、深灰阴影 */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y, x + w - 2, y + 1, 0xFF000000);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, 0xFF000000);
        g.fill(x, y + 2, x + 1, y + h - 2, 0xFF000000);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, 0xFF000000);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xFF000000);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, 0xFF000000);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, 0xFF000000);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 2, y + 1, x + w - 3, y + 3, 0xFFFFFFFF);
        g.fill(x + 1, y + 2, x + 3, y + h - 3, 0xFFFFFFFF);
        g.fill(x + 3, y + h - 3, x + w - 2, y + h - 1, 0xFF555555);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 2, 0xFF555555);
    }

    /** 凹进去的深色内框 */
    private static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h - 1, 0xFF373737);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, INSET);
    }
}
