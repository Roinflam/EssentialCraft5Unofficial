package com.flameforge.essentialcraft5.client.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.entity.boss.seal.CalamitySeal;
import com.flameforge.essentialcraft5.network.S2CCalamitySealPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天灾封印的客户端（tools/plan/calamity_heart_v2.md §1.4）：只管本地玩家自己。
 * <ul>
 *   <li>状态：服务端发来的 {@link S2CCalamitySealPacket}（封着哪几处、在不在战斗里）；封着时服务端每秒重发，{@value #EXPIRE_TICKS} 刻没收到、
 *       下线、重生 / 换维度（{@code Clone}）、死了都当解开，丢包也不会卡在封着；</li>
 *   <li>本地的表：每刻按掩码把本地玩家的物品按位置放进 {@link CalamitySeal} 的客户端表（同服务端的分法），移动类附魔、挖掘速度、
 *       别的模组在客户端读的附魔和服务端一致；物品附魔光泽由 {@code mixin/seal/ItemStackFoilSealMixin} 按这张表熄掉；</li>
 *   <li>画面：物品提示加一行「天灾封印：打倒××灾柱解开」；血条下面一排四个相色小锁（封着是暗的锁、解开亮起），解开时动作栏说一句、放一声。</li>
 * </ul>
 * 只在客户端加载（模块包的处理器、Forge 总线）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class CalamitySealClient {

    /** 多少刻没收到状态就当解开（服务端封着时每 20 刻重发一次） */
    public static final int EXPIRE_TICKS = 60;
    /** 锁图标的大小、间距（GUI 像素） */
    private static final int LOCK_W = 11;
    private static final int LOCK_H = 12;
    private static final int LOCK_GAP = 6;
    /** 刚解开的锁亮一下多少刻 */
    private static final int UNLOCK_GLOW_TICKS = 30;
    /** 没有任何血条时锁图标离屏幕顶多远 */
    private static final int TOP_MARGIN = 4;

    private static volatile int mask;
    private static boolean engaged;
    private static int quiet;
    private static long clientTicks;
    private static final long[] unlockedAt = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    /** 这一帧血条画到哪（{@link CustomizeGuiOverlayEvent.BossEventProgress} 每画一条记一次） */
    private static int barBottom;
    private static final CalamitySeal.MaskSource SOURCE = () -> mask;

    private CalamitySealClient() {
    }

    /** 服务端发来的状态（模块包处理器，客户端主线程） */
    public static void onPacket(S2CCalamitySealPacket packet) {
        int before = engaged ? mask : 0;
        quiet = 0;
        if (!packet.engaged()) {
            reset();
            return;
        }
        engaged = true;
        mask = packet.mask();
        int freed = before & ~mask;
        if (freed != 0) {
            unlocked(freed);
        }
        rebuild();
    }

    /** 本地玩家在不在一场天灾之心的封印里、封着哪几处（GameTest 不用；展示厅、调试读） */
    public static boolean engaged() {
        return engaged;
    }

    public static int mask() {
        return engaged ? mask : 0;
    }

    private static void reset() {
        engaged = false;
        mask = 0;
        quiet = 0;
        CalamitySeal.publishClient(Map.of());
    }

    /** 按掩码重建本地玩家那张表 */
    private static void rebuild() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || mask == 0) {
            CalamitySeal.publishClient(Map.of());
            return;
        }
        Map<ItemStack, CalamitySeal.Seat> table = new IdentityHashMap<>();
        CalamitySeal.collect(player, SOURCE, table);
        CalamitySeal.publishClient(table);
    }

    /** 有几处刚解开：动作栏说一句、放一声，锁亮一下 */
    private static void unlocked(int freed) {
        Minecraft minecraft = Minecraft.getInstance();
        for (int i = 0; i < 4; i++) {
            int part = CalamitySeal.part(i);
            if ((freed & part) != 0) {
                unlockedAt[i] = clientTicks;
                TextColor color = TextColor.fromRgb(CalamityHeartOverlay.bright(CalamityHeartEntity.Aspect.byId(i).color()));
                minecraft.gui.setOverlayMessage(Component.translatable("message.essentialcraft5.calamity_seal.unsealed." + partKey(part))
                        .withStyle(style -> style.withColor(color)), false);
            }
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 1F));
    }

    /** 这一处的小写名（语言键用）：head_chest / other / main_hand / legs_feet */
    public static String partKey(int part) {
        return switch (part) {
            case CalamitySeal.HEAD_CHEST -> "head_chest";
            case CalamitySeal.MAIN_HAND -> "main_hand";
            case CalamitySeal.LEGS_FEET -> "legs_feet";
            default -> "other";
        };
    }

    // ------------------------------------------------------------------
    // 每刻
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void calamitySealClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clientTicks++;
        if (!engaged) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isDeadOrDying() || ++quiet > EXPIRE_TICKS) {
            reset();
            return;
        }
        rebuild();
    }

    @SubscribeEvent
    public static void calamitySealClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    /** 重生、换维度：本地玩家换了一个对象，服务端会按新状态重发 */
    @SubscribeEvent
    public static void calamitySealClientClone(ClientPlayerNetworkEvent.Clone event) {
        reset();
    }

    // ------------------------------------------------------------------
    // 物品提示
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void calamitySealTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!engaged || mask == 0 || event.getEntity() != Minecraft.getInstance().player || !stack.isEnchanted() || !CalamitySeal.isSealedStack(stack)) {
            return;
        }
        int part = CalamitySeal.partOf(stack);
        CalamityHeartEntity.Aspect aspect = CalamityHeartEntity.Aspect.byId(Integer.numberOfTrailingZeros(part));
        Component pillar = Component.translatable(aspect.event().nameKey());
        Component line = Component.translatable("tooltip.essentialcraft5.calamity_seal", pillar)
                .withStyle(style -> style.withColor(TextColor.fromRgb(CalamityHeartOverlay.bright(aspect.color()))));
        List<Component> tooltip = event.getToolTip();
        tooltip.add(Math.min(1, tooltip.size()), line);
        if (event.getFlags().isAdvanced()) {
            tooltip.add(Math.min(2, tooltip.size()), Component.translatable("tooltip.essentialcraft5.calamity_seal.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ------------------------------------------------------------------
    // 锁图标（血条下面）
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void calamitySealBarsPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())) {
            barBottom = 0;
        }
    }

    @SubscribeEvent
    public static void calamitySealBarProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        barBottom = Math.max(barBottom, event.getY() + event.getIncrement());
    }

    @SubscribeEvent
    public static void calamitySealBarsPost(RenderGuiOverlayEvent.Post event) {
        if (!engaged || !event.getOverlay().id().equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int total = 4 * LOCK_W + 3 * LOCK_GAP;
        int x = (width - total) / 2;
        int y = barBottom > 0 ? barBottom : TOP_MARGIN;
        float partial = event.getPartialTick();
        int current = mask;
        for (int i = 0; i < 4; i++) {
            boolean sealed = (current & CalamitySeal.part(i)) != 0;
            float glow = sealed ? 0F : 1F - Math.min(1F, (clientTicks - unlockedAt[i] + partial) / UNLOCK_GLOW_TICKS);
            drawLock(graphics, x + i * (LOCK_W + LOCK_GAP), y, CalamityHeartEntity.Aspect.byId(i).color(), sealed, glow);
        }
    }

    /**
     * 一把小锁（{@value #LOCK_W} × {@value #LOCK_H}）：封着时锁身是相色压暗、锁梁扣着、锁孔黑；解开时锁身是提亮的相色、锁梁弹起一侧，
     * 刚解开的那几刻外面描一圈亮边（淡出，不闪）。
     */
    static void drawLock(GuiGraphics graphics, int x, int y, int aspectColor, boolean sealed, float glow) {
        int body = sealed ? scale(aspectColor, 0.45F) : CalamityHeartOverlay.bright(aspectColor);
        int edge = sealed ? scale(aspectColor, 0.25F) : scale(CalamityHeartOverlay.bright(aspectColor), 0.7F);
        int shackle = sealed ? 0xFF7A7470 : 0xFFE8E2D6;
        if (glow > 0.02F) {
            int a = (int) (glow * 0x90) << 24;
            graphics.fill(x - 2, y - 2, x + LOCK_W + 2, y + LOCK_H + 2, a | (CalamityHeartOverlay.bright(aspectColor) & 0xFFFFFF));
        }
        if (sealed) {
            // 扣着的锁梁：上横、两侧落到锁身
            graphics.fill(x + 3, y, x + 8, y + 1, shackle);
            graphics.fill(x + 2, y + 1, x + 3, y + 5, shackle);
            graphics.fill(x + 8, y + 1, x + 9, y + 5, shackle);
        } else {
            // 弹起的锁梁：整根抬高两格，右侧离开锁身
            graphics.fill(x + 3, y - 2, x + 8, y - 1, shackle);
            graphics.fill(x + 2, y - 1, x + 3, y + 5, shackle);
            graphics.fill(x + 8, y - 1, x + 9, y + 2, shackle);
        }
        graphics.fill(x, y + 5, x + LOCK_W, y + LOCK_H, 0xFF000000 | edge);
        graphics.fill(x + 1, y + 6, x + LOCK_W - 1, y + LOCK_H - 1, 0xFF000000 | body);
        if (sealed) {
            graphics.fill(x + 5, y + 7, x + 6, y + 10, 0xFF100A08);
            graphics.fill(x + 4, y + 7, x + 7, y + 8, 0xFF100A08);
        }
    }

    private static int scale(int rgb, float factor) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }
}
