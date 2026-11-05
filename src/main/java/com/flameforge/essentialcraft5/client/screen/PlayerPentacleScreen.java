package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.corruption.CorruptionGate;
import com.flameforge.essentialcraft5.menu.PlayerPentacleMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 灵魂五芒星界面（原作 GuiPlayerPentacle）。坐标照抄原作：k、l 是 176×166 的界面左上角（这里的 leftPos、topPos），
 * 15 个效果按钮（20×20）按原作 initGui 的位置围成一圈，符文 256×256 画在 (k − 42, l − 42)，玩家模型脚在 (k + 88, l + 102)，
 * 损伤条在模型脚下、ESPE 在 +22 的位置。
 * <p>
 * 画法照原作：先铺一层染成墨绿的末地天空（跟着鼠标错动）、再叠一层 2 倍大的末地传送门星空（慢慢流动），然后是按档位上色的符文；
 * 刚打开时整体淡入，淡入完才画玩家模型、损伤条和按钮（原作 100 tick，这里 20 tick，免得每次开都干等 5 秒）。
 * 当前档位治不了的效果，原作用银河字母（附魔台那种字体）写名字、图标换成传送门星空；银河字母没有汉字，这里改成乱码字（§k），
 * 图标照样换成星空，悬停时另写一行「需要几档」。
 * <p>
 * 和原作的区别：
 * <ul>
 *   <li>界面上下位置按按钮的范围居中（原作按 176×166 居中，界面缩放大时最上面一个按钮会出屏幕）。</li>
 *   <li>点按钮只发按钮号（{@link PlayerPentacleMenu} 的两段编号），服务端判档位、ESPE、创造模式；这里的判断只决定按钮能不能点、提示怎么写。</li>
 *   <li>效果超过 15 个时下面多一对翻页按钮（原作只显示前 15 个；矩阵类打开以后身上最多 25 个）。</li>
 *   <li>有效 ESPE 是服务端同步来的（原作客户端自己读柱子）；档位写成一行字（原作只看符文颜色）。</li>
 * </ul>
 */
public class PlayerPentacleScreen extends AbstractContainerScreen<PlayerPentacleMenu> {

    private static final ResourceLocation END_SKY = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    private static final ResourceLocation END_PORTAL = ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");
    private static final ResourceLocation RUNE = EssentialCraft5.id("textures/block/model/pentacle.png");

    /** 原作 initGui 的 15 个按钮位置（相对 k、l） */
    private static final int[][] SLOTS = {
            {76, -40}, {-36, 40}, {186, 40}, {76, 16}, {30, 10}, {126, 10}, {20, 57}, {132, 57},
            {0, 101}, {150, 101}, {42, 126}, {110, 126}, {76, 156}, {0, 174}, {150, 174}};
    private static final int BUTTON_SIZE = 20;
    /** 按钮占的高度：从 l − 40 到 l + 194 */
    private static final int LAYOUT_TOP = 40;
    private static final int LAYOUT_HEIGHT = 234;
    /** 原作 uv / 2.9、uu / 2.5（uv = uu = 256） */
    private static final int MODEL_X = 88;
    private static final int MODEL_Y = 102;
    /** 淡入要多少 tick（原作 100） */
    private static final float FADE_TICKS = 20F;

    private long openedAt = Long.MIN_VALUE;
    private int page;
    @Nullable
    private Button previous;
    @Nullable
    private Button next;
    private final List<EffectButton> buttons = new ArrayList<>();

    public PlayerPentacleScreen(PlayerPentacleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        topPos = (height - LAYOUT_HEIGHT) / 2 + LAYOUT_TOP;
        if (openedAt == Long.MIN_VALUE && minecraft != null && minecraft.level != null) {
            openedAt = minecraft.level.getGameTime();
        }
        buttons.clear();
        for (int i = 0; i < SLOTS.length; i++) {
            buttons.add(addRenderableWidget(new EffectButton(leftPos + SLOTS[i][0], topPos + SLOTS[i][1], i)));
        }
        // 翻页：夹在第 10、11 号按钮（下沿 l + 146）和第 12 号按钮（上沿 l + 156）之间，页码写在两个箭头中间
        int cx = leftPos + MODEL_X;
        int y = topPos + MODEL_Y + 44;
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> page--).bounds(cx - 26, y, 10, 10).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> page++).bounds(cx + 16, y, 10, 10).build());
    }

    // ------------------------------------------------------------------
    // 数据
    // ------------------------------------------------------------------

    /** 身上的腐化效果（玩家数据同步来的），认不出的 id 跳过 */
    private List<CorruptionEffects.Effect> effects() {
        List<CorruptionEffects.Effect> out = new ArrayList<>();
        if (minecraft == null || minecraft.player == null) {
            return out;
        }
        IPlayerData data = PlayerDataEvents.get(minecraft.player);
        if (data == null) {
            return out;
        }
        for (String id : data.getCorruptionEffects()) {
            CorruptionEffects.Effect effect = CorruptionEffects.byId(id);
            if (effect != null) {
                out.add(effect);
            }
        }
        return out;
    }

    private int pages(int count) {
        return Math.max(1, (count + SLOTS.length - 1) / SLOTS.length);
    }

    @Nullable
    private CorruptionEffects.Effect effectAt(int slot) {
        List<CorruptionEffects.Effect> list = effects();
        int index = page * SLOTS.length + slot;
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    private float opacity(float partialTick) {
        if (minecraft == null || minecraft.level == null || openedAt == Long.MIN_VALUE) {
            return 1F;
        }
        return Math.min(1F, Math.max(0F, (minecraft.level.getGameTime() - openedAt + partialTick) / FADE_TICKS));
    }

    private boolean creativeFree() {
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative() && hasShiftDown();
    }

    /** 当前档位能不能治（能不能看懂）这一类 */
    private boolean tierAllows(CorruptionEffects.Effect effect) {
        return menu.tier() >= effect.category().minPentacleTier();
    }

    private boolean canCure(CorruptionEffects.Effect effect) {
        return creativeFree() || tierAllows(effect) && effect.cost() <= menu.effectiveESPE();
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int count = effects().size();
        int pages = pages(count);
        page = Math.max(0, Math.min(page, pages - 1));
        boolean shown = opacity(partialTick) >= 1F;
        for (EffectButton button : buttons) {
            CorruptionEffects.Effect effect = effectAt(button.slot);
            button.visible = shown && effect != null;
            button.active = effect != null && canCure(effect);
        }
        if (previous != null && next != null) {
            previous.visible = next.visible = shown && pages > 1;
            previous.active = page > 0;
            next.active = page < pages - 1;
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (shown) {
            renderHoverTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float opacity = opacity(partialTick);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 末地天空，染成墨绿，跟着鼠标错动（原作 drawTexturedModalRect(0, 0, mouseX, mouseY, …)，贴图按 256 算、平铺）
        g.setColor(0F, 0.2F, 0.05F, opacity);
        g.blit(END_SKY, 0, 0, 0, mouseX, mouseY, width, height, 256, 256);
        // 2 倍大的传送门星空，按玩家存在时间慢慢流动
        float drift = minecraft != null && minecraft.player != null ? minecraft.player.tickCount % 24000 : 0;
        g.setColor(0F, 0.7F, 1F, 0.6F * opacity);
        g.pose().pushPose();
        g.pose().scale(2F, 2F, 1F);
        g.blit(END_PORTAL, 0, 0, 0, drift, drift, width / 2 + 1, height / 2 + 1, 256, 256);
        g.pose().popPose();
        // 符文：按档位上色
        float[] color = PlayerPentacleBlockEntity.tierColor(menu.tier());
        g.setColor(color[0], color[1], color[2], opacity);
        g.blit(RUNE, leftPos - 42, topPos - 42, 256, 256, 0F, 0F, 512, 512, 512, 512);
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();

        if (opacity < 1F || minecraft == null || minecraft.player == null) {
            return;
        }
        int cx = leftPos + MODEL_X;
        int cy = topPos + MODEL_Y;
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, cy, 20, 0F, 0F, minecraft.player);
        // 损伤条（原作白块染色：底 64×8、槽 61×5、红色按 damageBarMax 满格，默认 288000）
        g.fill(cx - 32, cy + 2, cx + 32, cy + 10, 0xFF0F0F0F);
        g.fill(cx - 32, cy + 2, cx + 29, cy + 7, 0xFF33331A);
        int filled = (int) (61L * damage() / CorruptionEffects.damageBarMax());
        if (filled > 0) {
            g.fill(cx - 32, cy + 2, cx - 32 + filled, cy + 7, 0xFFBA0000);
        }
        g.drawCenteredString(font, Component.translatable("gui.essentialcraft5.espe", menu.effectiveESPE()), cx, cy + 22, 0xFFFFFF);
        int tier = menu.tier();
        Component tierLine = tier >= 0
                ? Component.translatable("gui.essentialcraft5.player_pentacle.tier", tier)
                : Component.translatable("structure.essentialcraft5.incomplete");
        g.drawCenteredString(font, tierLine, cx, cy + 32, PlayerPentacleBlockEntity.tierTextColor(tier));
        int count = effects().size();
        if (count == 0) {
            g.drawCenteredString(font, Component.translatable("gui.essentialcraft5.player_pentacle.no_effects"), cx, cy + 45, 0xAAAAAA);
        } else if (pages(count) > 1) {
            g.drawCenteredString(font, Component.translatable("gui.essentialcraft5.player_pentacle.page", page + 1, pages(count)),
                    cx, cy + 45, 0xAAAAAA);
        }
    }

    /** 标题、「物品栏」字样都不画（原作没有） */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    private int damage() {
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }
        IPlayerData data = PlayerDataEvents.get(minecraft.player);
        return data == null ? 0 : Math.max(0, Math.min(CorruptionEffects.damageBarMax(), data.getMatrixDamage()));
    }

    private void renderHoverTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (EffectButton button : buttons) {
            CorruptionEffects.Effect effect = effectAt(button.slot);
            if (button.visible && effect != null && button.isHovered()) {
                g.renderComponentTooltip(font, tooltip(effect), mouseX, mouseY);
                return;
            }
        }
        int cx = leftPos + MODEL_X;
        int cy = topPos + MODEL_Y;
        if (mouseX >= cx - 32 && mouseX < cx + 32 && mouseY >= cy + 2 && mouseY < cy + 10 && minecraft != null && minecraft.player != null) {
            IPlayerData data = PlayerDataEvents.get(minecraft.player);
            int damage = data == null ? 0 : data.getMatrixDamage();
            g.renderTooltip(font, Component.translatable("gui.essentialcraft5.player_pentacle.damage", damage, CorruptionEffects.damageBarMax()),
                    mouseX, mouseY);
        }
    }

    private List<Component> tooltip(CorruptionEffects.Effect effect) {
        List<Component> lines = new ArrayList<>();
        boolean free = creativeFree();
        boolean readable = free || tierAllows(effect);
        MutableComponent name = Component.translatable(effect.nameKey());
        lines.add(readable ? name.withStyle(ChatFormatting.WHITE) : name.withStyle(ChatFormatting.OBFUSCATED));
        lines.add(Component.translatable("gui.essentialcraft5.player_pentacle.category",
                Component.translatable(effect.category().nameKey())).withStyle(ChatFormatting.GRAY));
        // 潜伏：哪一档都治不了它（柱子容量配得太小），或者这名玩家还没打开过成形的五芒星（档位存在玩家数据里，同步到客户端）
        IPlayerData playerData = minecraft == null || minecraft.player == null ? null : PlayerDataEvents.get(minecraft.player);
        if (playerData != null && !CorruptionGate.active(effect, playerData)) {
            lines.add((CorruptionGate.cureTier(effect) >= CorruptionGate.NEVER
                    ? Component.translatable("gui.essentialcraft5.player_pentacle.dormant_never")
                    : Component.translatable("gui.essentialcraft5.player_pentacle.dormant")).withStyle(ChatFormatting.DARK_AQUA));
        }
        // 触发时那句话（ECCorruption 写的翻译；没有就不写）当作说明，原作 getLocalizedDesc
        if (readable && I18n.exists(effect.messageKey())) {
            lines.add(Component.translatable(effect.messageKey()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        boolean enough = effect.cost() <= menu.effectiveESPE();
        MutableComponent cost = Component.translatable("gui.essentialcraft5.espe", effect.cost());
        if (free) {
            lines.add(Component.translatable("gui.essentialcraft5.player_pentacle.creative", cost).withStyle(ChatFormatting.DARK_GREEN));
            lines.add(Component.translatable("gui.essentialcraft5.player_pentacle.click").withStyle(ChatFormatting.GREEN));
            return lines;
        }
        lines.add(cost.withStyle(tierAllows(effect) && enough ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
        int cureTier = CorruptionGate.cureTier(effect);
        if (!tierAllows(effect)) {
            lines.add(Component.translatable("gui.essentialcraft5.player_pentacle.needs_tier", effect.category().minPentacleTier())
                    .withStyle(ChatFormatting.RED));
        } else if (!enough && menu.tier() < cureTier && cureTier < CorruptionGate.NEVER) {
            // 这一档的柱子攒满也付不起（默认是 72000 的三种矩阵类在 2 档）：直接说要几档，别让人干等柱子
            lines.add(Component.translatable("gui.essentialcraft5.player_pentacle.needs_tier", cureTier).withStyle(ChatFormatting.RED));
        } else if (!enough) {
            lines.add(Component.translatable("gui.essentialcraft5.player_pentacle.not_enough").withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable("gui.essentialcraft5.player_pentacle.click").withStyle(ChatFormatting.GREEN));
        }
        if (minecraft != null && minecraft.player != null && minecraft.player.isCreative()) {
            lines.add(Component.translatable("gui.essentialcraft5.player_pentacle.creative_hint").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** 点了第 slot 个按钮：能治就把按钮号发给服务端（服务端再判一遍） */
    private void effectClicked(int slot) {
        CorruptionEffects.Effect effect = effectAt(slot);
        if (effect == null || minecraft == null || minecraft.gameMode == null) {
            return;
        }
        int id;
        if (creativeFree()) {
            id = PlayerPentacleMenu.BUTTON_CURE_FREE + effect.index();
        } else if (tierAllows(effect) && effect.cost() <= menu.effectiveESPE()) {
            id = PlayerPentacleMenu.BUTTON_CURE + effect.index();
        } else {
            return;
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    /** 一个效果按钮（原作 EffectButton）：按钮底 + 16×16 图标，文字由悬停提示写 */
    private final class EffectButton extends AbstractButton {

        private final int slot;

        EffectButton(int x, int y, int slot) {
            super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.empty());
            this.slot = slot;
        }

        @Override
        public void onPress() {
            effectClicked(slot);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            CorruptionEffects.Effect effect = effectAt(slot);
            if (effect == null) {
                return;
            }
            super.renderWidget(g, mouseX, mouseY, partialTick);
            boolean readable = creativeFree() || tierAllows(effect);
            RenderSystem.enableBlend();
            g.blit(readable ? effect.icon() : END_PORTAL, getX() + 2, getY() + 2, 0, 0F, 0F, 16, 16, 16, 16);
            RenderSystem.disableBlend();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            CorruptionEffects.Effect effect = effectAt(slot);
            if (effect != null) {
                output.add(NarratedElementType.TITLE, Component.translatable(effect.nameKey()));
            }
        }
    }
}
