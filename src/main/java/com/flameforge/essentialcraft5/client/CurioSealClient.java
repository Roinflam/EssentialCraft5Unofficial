package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.event.CurioSealEvent;
import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import com.flameforge.essentialcraft5.compat.curio.CurioSealClientConfig;
import com.flameforge.essentialcraft5.compat.curio.CurioSealClientState;
import com.flameforge.essentialcraft5.compat.curio.CurioSealConfig;
import com.flameforge.essentialcraft5.item.SealNotifier;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.FieldState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 饰品封禁场的客户端（tools/plan/batch10.md §5.1.6、§5.1.7）：被封的一方看得到的东西，和穿戴者终端的数据。
 * <ul>
 *   <li><b>格子禁用图标</b>：任何容器界面里，本地玩家被压制时，功能槽（{@code ICurioStacksHandler.getStacks()} 背后的那几格）蒙一层暗紫、
 *       压一个禁用图标（空格也罩：放进去也不会生效；豁免标签里的物品那格不罩；装饰槽背后是 {@code getCosmeticStacks()}，不罩）。
 *       不认 Curios 的格子类（在它的内部包里），按格子背后的物品栏对象认（{@code SlotItemHandler.getItemHandler()}，Curios 的界面就是拿
 *       {@code getStacks()} 建的格子）。画在 {@code ContainerScreenEvent.Render.Foreground}：格子画完、标签画完、手上拿着的物品还没画。
 *       罩和图标要抬到 z = {@value #VEIL_Z} / {@value #ICON_Z}（1.20.1 的数，javap 核过）：{@code renderSlot} 先把 pose 抬高 100，
 *       {@code GuiGraphics.renderItem} 再抬 150，格子里的物品中心在 z 250（方块物品斜着放，前后约 ±9）；数量字在 {@code renderItemDecorations}
 *       里再抬 200（z 300，耐久条、冷却罩用不做深度测试的 guiOverlay，只看先后）；手上拿着的物品在 {@code renderFloatingItem} 里抬 232
 *       （物品 z 382，方块物品下沿约 373），提示框 z 400。暗紫罩走 {@code RenderType.gui()}，做深度测试，z 低于物品就会被物品挡住
 *       （以前 205 / 210 就是这样画到了物品底下）；现在压在物品和数量字上面，手上物品和提示框照样盖得住它。不 mixin 任何界面类。</li>
 *   <li><b>Curios 按钮角标</b>：背包、创造背包、Curios 界面里 Curios 的按钮（按类名认）右上角缩成 8 × 8 画同一张图标，和格子一起画在
 *       {@code Render.Foreground}（按钮在那之前已经画完），同样 z {@value #ICON_Z}：手上拿着的物品和提示框画在后面、盖得住它
 *       （画在 {@code ScreenEvent.Render.Post} 的话提示框已经画完，角标会压在提示框上）。</li>
 *   <li><b>物品提示</b>：悬停的正是功能槽里那个物品对象（{@code ==}）、不在豁免标签里，末尾加两行「被封了、来自谁」。</li>
 *   <li><b>快捷栏图标</b>：客户端配置 {@code [curio_seal] hud_icon}；快捷栏左边（副手格在左边且有东西时再让开 29 像素）画 16 × 16 图标。
 *       用压制包里的状态，不注册状态效果（牛奶清不掉，也不该清掉）。</li>
 *   <li><b>被压制的提示</b>：收到本人的 {@link CurioSealEvent} 后按 {@link SealNotifier.VictimDebounce} 出——每次登录第一次在聊天栏说清是谁的场、
 *       离开多远恢复；之后每次开始被压制动作栏一条 + 一声 {@code block.beacon.deactivate}（音量 0.4、音高 1.4，只有自己听到）；解除后
 *       {@value SealNotifier#REENTER_QUIET_TICKS} 刻内又被压制不出字、不响，只靠图标。只被自己的场压着（穿戴者）时什么都不出，
 *       物品提示的来源写「你自己的饰品封禁场」。</li>
 *   <li><b>终端数据</b>：{@link S2CComputerTerminalPacket} 收到就记下（只带状态的那种用来画屏幕顶上的紫色小圆，带名单的给终端）。</li>
 * </ul>
 * 单人游戏里服务端和客户端在同一个 JVM，Forge 总线上服务端那份 {@link CurioSealEvent} 也会到这里，所以只认客户端世界里的本地玩家。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class CurioSealClient {

    /** 禁用图标（16 × 16：品红圆环 + 斜杠，外侧深色描边） */
    public static final ResourceLocation ICON = EssentialCraft5.id("textures/gui/curio_sealed.png");
    /** 格子上的暗紫罩（GuideTheme.BAND #261A38，60% 不透明） */
    private static final int VEIL = 0x99261A38;
    /** 暗紫罩的 z：高过格子里的物品（250 ± 9）和数量字（300），低过手上拿着的物品（下沿约 373）和提示框（400），见类注释 */
    private static final float VEIL_Z = 310F;
    /** 禁用图标和按钮角标的 z（罩上面一层） */
    private static final float ICON_Z = 320F;
    /** Curios 背包按钮的类（在它的客户端内部包里，按名字认） */
    private static final String CURIOS_BUTTON = "top.theillusivec4.curios.client.gui.CuriosButton";
    /** 提示行颜色：品红（CodexTheme.FOCUS） */
    private static final int SEALED_COLOR = 0xF040E8;
    private static final String MSG = "message.essentialcraft5.curio_seal.";

    private static final SealNotifier.VictimDebounce DEBOUNCE = new SealNotifier.VictimDebounce();
    /** 客户端自己数的刻（被压制提示的去抖用；只在主线程读写） */
    private static long clientTicks;

    /** 最近收到的终端数据（只带状态的和带名单的都记；只在主线程写） */
    @Nullable
    private static volatile S2CComputerTerminalPacket terminal;
    /** 最近一份带名单的 */
    @Nullable
    private static volatile S2CComputerTerminalPacket terminalDetail;
    /** 展示厅：终端和紫色小圆按这份画（不发包、不影响服务端），null = 照常 */
    @Nullable
    private static volatile S2CComputerTerminalPacket showroomTerminal;

    private CurioSealClient() {
    }

    // ------------------------------------------------------------------ 状态

    /** 本地玩家现在被压制（收到的压制包，或展示厅覆盖） */
    public static boolean localSealed() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && CurioSeal.isSealed(player);
    }

    /** 终端数据包（ECComputer 登记，客户端主线程调） */
    public static void onTerminalPacket(S2CComputerTerminalPacket packet) {
        terminal = packet;
        if (packet.detail()) {
            terminalDetail = packet;
        }
    }

    /** 最近一份终端数据（展示厅覆盖优先）；还没收到过返回 null */
    @Nullable
    public static S2CComputerTerminalPacket terminalData() {
        S2CComputerTerminalPacket override = showroomTerminal;
        return override != null ? override : terminal;
    }

    /** 最近一份带名单的终端数据（展示厅覆盖优先）；还没收到过返回 null */
    @Nullable
    public static S2CComputerTerminalPacket terminalDetail() {
        S2CComputerTerminalPacket override = showroomTerminal;
        return override != null ? override : terminalDetail;
    }

    /** 屏幕顶上紫色小圆要不要画、画亮的还是暗的：开着（待机 / 运转 / 电量不足）才画；没收到过数据 = 关 */
    public static FieldState fieldState() {
        S2CComputerTerminalPacket data = terminalData();
        return data == null ? FieldState.OFF : data.state();
    }

    /** 展示厅：终端和紫色小圆按这份数据画（null 撤销），只改本客户端画面 */
    public static void setShowroomTerminal(@Nullable S2CComputerTerminalPacket packet) {
        showroomTerminal = packet;
    }

    /** 展示厅：本客户端把本人画成被压制（格子图标、提示行、快捷栏图标都出来），不发包、不影响服务端 */
    public static void setShowroomSealed(boolean sealed, List<String> sources) {
        CurioSealClientState.setShowroomSealed(sealed, sources);
    }

    /** 「A、B、C 等 N 人」（名字至多 3 个 + 余下人数）；一个名字都没有时写「附近的人」 */
    public static Component sourcesText(List<String> names, int more) {
        if (names.isEmpty()) {
            return Component.translatable(MSG + "someone");
        }
        Component head = SealNotifier.names(names);
        return more > 0 ? Component.translatable("message.essentialcraft5.computer_seal.more", head, names.size() + more) : head;
    }

    // ------------------------------------------------------------------ 格子

    /** 本地玩家的功能槽背后的物品栏对象（按对象认，装饰槽不在里面） */
    private static Set<Object> functionalHandlers(Player player) {
        Set<Object> handlers = Collections.newSetFromMap(new IdentityHashMap<>());
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            for (ICurioStacksHandler handler : inventory.getCurios().values()) {
                handlers.add(handler.getStacks());
            }
        });
        return handlers;
    }

    /** 这件物品正放在本地玩家的某个功能槽里（按对象比） */
    private static boolean inFunctionalSlot(Player player, ItemStack stack) {
        boolean[] found = {false};
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            for (ICurioStacksHandler handler : inventory.getCurios().values()) {
                for (int i = 0; i < handler.getSlots() && !found[0]; i++) {
                    found[0] = handler.getStacks().getStackInSlot(i) == stack;
                }
            }
        });
        return found[0];
    }

    private static void blitIcon(GuiGraphics graphics, int x, int y, int size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(ICON, x, y, size, size, 0F, 0F, 16, 16, 16, 16);
        RenderSystem.disableBlend();
    }

    /**
     * 格子禁用图标和 Curios 按钮角标。这一层的坐标原点已平移到界面左上角（{@code leftPos, topPos}），z 见类注释。
     */
    @SubscribeEvent
    public static void onCurioSealSlotsForeground(ContainerScreenEvent.Render.Foreground event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !CurioSeal.isSealed(player)) {
            return;
        }
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        GuiGraphics graphics = event.getGuiGraphics();
        Set<Object> handlers = functionalHandlers(player);
        if (!handlers.isEmpty()) {
            for (Slot slot : screen.getMenu().slots) {
                if (!slot.isActive() || !(slot instanceof SlotItemHandler handlerSlot) || !handlers.contains(handlerSlot.getItemHandler())) {
                    continue;
                }
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty() && CurioSeal.isExempt(stack)) {
                    continue;
                }
                graphics.pose().pushPose();
                graphics.pose().translate(0F, 0F, VEIL_Z);
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, VEIL);
                graphics.pose().translate(0F, 0F, ICON_Z - VEIL_Z);
                blitIcon(graphics, slot.x, slot.y, 16);
                graphics.pose().popPose();
            }
        }
        renderButtonBadges(screen, graphics);
    }

    /**
     * Curios 背包按钮右上角的小角标（背包、创造背包、Curios 自己的界面里都是这一个按钮类；按钮在 {@code Render.Foreground} 之前已经画完）。
     * 创造背包里不在「物品栏」那一页时，Curios 把按钮设成不可用、不画（{@code visible} 仍是 true），角标也跟着不画。
     */
    private static void renderButtonBadges(AbstractContainerScreen<?> screen, GuiGraphics graphics) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.active && CURIOS_BUTTON.equals(child.getClass().getName())) {
                // 按钮坐标是屏幕坐标，这一层的原点在界面左上角
                int x = widget.getX() - screen.getGuiLeft() + widget.getWidth() - 5;
                int y = widget.getY() - screen.getGuiTop() - 3;
                graphics.pose().pushPose();
                graphics.pose().translate(0F, 0F, ICON_Z);
                blitIcon(graphics, x, y, 8);
                graphics.pose().popPose();
            }
        }
    }

    // ------------------------------------------------------------------ 物品提示

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCurioSealTooltip(ItemTooltipEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack stack = event.getItemStack();
        if (player == null || stack.isEmpty() || !CurioSeal.isSealed(player) || CurioSeal.isExempt(stack) || !inFunctionalSlot(player, stack)) {
            return;
        }
        event.getToolTip().add(Component.translatable("tooltip.essentialcraft5.curio_seal.sealed").withStyle(style -> style.withColor(SEALED_COLOR)));
        List<String> names = CurioSealClientState.localSources();
        int more = CurioSealClientState.localMoreSources();
        if (!names.isEmpty() || more > 0) {
            event.getToolTip().add(Component.translatable("tooltip.essentialcraft5.curio_seal.sources",
                    sourcesText(names, more)).withStyle(ChatFormatting.GRAY));
        } else if (fieldState() == FieldState.RUNNING) {
            // 名单不含本人：空着又在运转，就是自己的场在压自己
            event.getToolTip().add(Component.translatable("tooltip.essentialcraft5.curio_seal.sources.self").withStyle(ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ 快捷栏图标

    private static void renderHotbarIcon(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !showsHotbarIcon(player)) {
            return;
        }
        // 快捷栏左边缘在 width / 2 - 91；副手格在左边（主手在右）且有东西时占了左边 29 像素
        int x = width / 2 - 91 - 4 - 16;
        if (!player.getOffhandItem().isEmpty() && player.getMainArm() == HumanoidArm.RIGHT) {
            x -= 29;
        }
        blitIcon(graphics, x, height - 19, 16);
    }

    /** 快捷栏左边这会儿画着被压制的图标（矩阵后遗症的图标排在它左边，AfflictionHud） */
    static boolean showsHotbarIcon(LocalPlayer player) {
        return !player.isSpectator() && CurioSealClientConfig.hudIcon() && CurioSeal.isSealed(player);
    }

    /** 模组总线：登记快捷栏图标那一层 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class CurioSealClientRegistration {

        private CurioSealClientRegistration() {
        }

        @SubscribeEvent
        public static void registerCurioSealOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "curio_seal", CurioSealClient::renderHotbarIcon);
        }
    }

    // ------------------------------------------------------------------ 被压制的提示

    /**
     * 本人的压制状态翻转。名单是服务端去掉本人以后的（穿戴者的场运转时也压他自己）：名单为空、余数为 0 = 只有自己的场在压自己，
     * 什么都不出（屏幕顶上的紫圆已经亮着），解除时也不出「恢复生效」（{@link SealNotifier.VictimDebounce.Kind#SELF}）
     */
    @SubscribeEvent
    public static void onCurioSealNotice(CurioSealEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || event.getEntity() != player || !event.getEntity().level().isClientSide) {
            return;
        }
        if (event.isSealed()) {
            boolean selfOnly = event.getSourceNames().isEmpty() && event.getMoreSources() == 0;
            notifySealed(player, DEBOUNCE.onSealed(clientTicks, selfOnly), event.getSourceNames(), event.getMoreSources());
        } else if (DEBOUNCE.onReleased(clientTicks)) {
            player.displayClientMessage(Component.translatable(MSG + "released").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void notifySealed(LocalPlayer player, SealNotifier.VictimDebounce.Kind kind, List<String> names, int more) {
        Component who = sourcesText(names, more);
        switch (kind) {
            case FIRST -> player.displayClientMessage(Component.translatable(MSG + "first", who,
                    CurioSealConfig.values().radius() + 2).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            case SEALED -> {
                player.displayClientMessage(Component.translatable(MSG + "sealed", who).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_DEACTIVATE, 1.4F, 0.4F));
            }
            case QUIET, SELF -> {
                // 解除后不久又被压制、或只被自己的场压着：只靠图标
            }
        }
    }

    @SubscribeEvent
    public static void onCurioSealClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level != null) {
            clientTicks++;
            if (DEBOUNCE.selfOnly()) {
                // 只被自己的场压着时别人的场也罩上来：状态没翻转、不发事件，只有名单由空变成有人（服务端补发的名字包）
                LocalPlayer player = Minecraft.getInstance().player;
                List<String> names = CurioSealClientState.localSources();
                if (player != null && CurioSeal.isSealed(player) && (!names.isEmpty() || CurioSealClientState.localMoreSources() > 0)) {
                    SealNotifier.VictimDebounce.Kind kind = DEBOUNCE.onOthersJoined(clientTicks);
                    if (kind != null) {
                        notifySealed(player, kind, names, CurioSealClientState.localMoreSources());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCurioSealLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        DEBOUNCE.reset();
        terminal = null;
        terminalDetail = null;
    }

    @SubscribeEvent
    public static void onCurioSealLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DEBOUNCE.reset();
        terminal = null;
        terminalDetail = null;
    }
}
