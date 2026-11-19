package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideClient;
import com.flameforge.essentialcraft5.client.screen.BuilderPanelScreen;
import com.flameforge.essentialcraft5.item.BuilderAim;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderShapes;
import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 创世神之杖的魔杖键（默认左 Alt，按键设置里能改，见 tools/plan/builder_upgrade.md §1.4）。只在手里拿着魔杖时起作用（主手优先，主手不是魔杖看副手），
 * 所有操作都经 {@link C2SBuilderActionPacket} 交给服务端判定：
 * <ul>
 *   <li>单按（按下到松开之间没滚轮、没按别的键和鼠标键，且不超过 {@value #TAP_MAX_TICKS} 刻）：铺开面板（{@link BuilderPanelScreen}，切形状挪进了面板）；</li>
 *   <li>按住 + 滚轮：铺面长宽一起 ±1（取长宽里大的那个，变成正方形），直线只调长；再按住潜行：铺面只调长，直线只调宽。
 *       按住时滚轮事件取消，不切快捷栏；</li>
 *   <li>按住 + 右键：撤销上一次放置。右键在客户端拦下（不走原版的使用），按住右键的连发只算一次。</li>
 *   <li>不按魔杖键、铺面 / 竖墙 / 沿面对着原版够不着的远处方块右键：拦下原版的「对空气用物品」，改发远距离开工包（{@link #onBuilderInputUse}）。</li>
 * </ul>
 * 尺寸先改本地显示（虚影、提示即时变），本地记一份待确认的尺寸，最多每 {@value #SEND_GAP_TICKS} 刻发一次最新值
 * （服务端每 20 刻只收 10 个操作包，连着滚也不会被丢）；收到服务端写回的 NBT 和它一致、或发出后 {@value #PENDING_TIMEOUT} 刻还没对上，就作废、改看 NBT。
 * 进服时和改了客户端配置的确认阈值时报一次 {@code CLIENT_PREFS}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class BuilderInput {

    public static final String KEY = "key.essentialcraft5.builder_wand";
    public static final KeyMapping WAND_KEY = new KeyMapping(KEY, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT, GuideClient.KEY_CATEGORY);

    /** 按下到松开超过这么多刻（0.5 秒）就不算单按：按住魔杖键想调尺寸、又改了主意的不会误切形状 */
    static final int TAP_MAX_TICKS = 10;
    /** 待确认的尺寸最多每这么多刻发一次 */
    static final int SEND_GAP_TICKS = 4;
    /** 发出后这么多刻还没在 NBT 里看到，就不再用本地的值 */
    static final int PENDING_TIMEOUT = 40;

    /** 本地的客户端刻（只用来算间隔，不跟世界时间走） */
    private static long ticks;
    private static boolean keyWasDown;
    private static long keyDownAt;
    /** 这次按住期间滚过轮、按过别的键或鼠标键 */
    private static boolean keyUsed;
    /** 右键松开过：下一次按住魔杖键右键才再发撤销 */
    private static boolean undoArmed = true;
    /** 这次按住期间已经给哪种形状提示过「不用调尺寸」（两点、沿面各提示一次） */
    @Nullable
    private static BuilderShape noSizeHintShown;
    private static int selectedSlot = -1;

    @Nullable
    private static Pending pending;
    private static long lastSizeSent = Long.MIN_VALUE / 2;

    /** 本地待确认的尺寸 */
    private record Pending(InteractionHand hand, BuilderShape shape, int width, int length, boolean sent, long sentAt) {
    }

    private BuilderInput() {
    }

    /** 模组总线：登记按键 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class BuilderInputRegistration {
        private BuilderInputRegistration() {
        }

        @SubscribeEvent
        public static void registerBuilderKeys(RegisterKeyMappingsEvent event) {
            event.register(WAND_KEY);
        }
    }

    // ------------------------------------------------------------------
    // 给别处读
    // ------------------------------------------------------------------

    /** 拿着魔杖的那只手（主手优先）；都不是返回 null */
    @Nullable
    public static InteractionHand wandHand(Player player) {
        if (BuilderMode.of(player.getMainHandItem()) != null) {
            return InteractionHand.MAIN_HAND;
        }
        return BuilderMode.of(player.getOffhandItem()) != null ? InteractionHand.OFF_HAND : null;
    }

    /** 魔杖键现在按着（游戏里、没开界面） */
    public static boolean keyHeld() {
        return Minecraft.getInstance().screen == null && WAND_KEY.isDown();
    }

    /**
     * 这只手里的魔杖这个形状现在该显示的尺寸（宽、长；竖墙是宽、高，已按服务端配置夹过）：有本地待确认的用它，否则用 NBT 里的
     */
    public static int[] displaySize(InteractionHand hand, ItemStack wand, BuilderShape shape) {
        Pending local = pending;
        int[] raw = local != null && local.hand == hand && local.shape == shape
                ? new int[] {local.width, local.length} : BuilderSelection.size(wand, shape);
        BuilderTier.Stats stats = BuilderTier.stats(wand);
        BuilderShapes.Size size = BuilderShapes.clamp(raw[0], raw[1], stats.maxSide(), stats.maxVolume());
        return new int[] {size.width(), size.length()};
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onBuilderInputTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getConnection() == null) {
            keyWasDown = false;
            pending = null;
            return;
        }
        if (BuilderClientConfig.takePrefsDirty()) {
            send(C2SBuilderActionPacket.CLIENT_PREFS, InteractionHand.MAIN_HAND, BuilderClientConfig.confirmThreshold(), 0);
        }
        if (!minecraft.options.keyUse.isDown()) {
            undoArmed = true;
        }
        int slot = player.getInventory().selected;
        if (slot != selectedSlot) {
            selectedSlot = slot;
            pending = null;
        }
        InteractionHand hand = wandHand(player);
        boolean down = hand != null && keyHeld();
        if (down && !keyWasDown) {
            keyDownAt = ticks;
            keyUsed = false;
            noSizeHintShown = null;
        } else if (!down && keyWasDown) {
            // 松开：中间什么都没做、按得不久，又还拿着魔杖、没开界面，才算单按
            if (!keyUsed && hand != null && minecraft.screen == null && ticks - keyDownAt <= TAP_MAX_TICKS) {
                openPanel(minecraft, player, hand);
            }
        }
        if (down && (minecraft.screen != null || !minecraft.isWindowActive())) {
            // 按着魔杖键切走了窗口（Alt+Tab）或开了界面：这次不算单按
            keyUsed = true;
        }
        keyWasDown = down;
        tickPending(player);
    }

    /** 按住魔杖键时按了别的键：这次不算单按 */
    @SubscribeEvent
    public static void onBuilderInputKey(InputEvent.Key event) {
        if (!keyWasDown || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (!WAND_KEY.getKey().equals(InputConstants.getKey(event.getKey(), event.getScanCode()))) {
            keyUsed = true;
        }
    }

    /** 按住魔杖键时按了鼠标键：这次不算单按（魔杖键自己绑在鼠标键上时除外） */
    @SubscribeEvent
    public static void onBuilderInputMouse(InputEvent.MouseButton.Pre event) {
        if (!keyWasDown || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (!WAND_KEY.getKey().equals(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) {
            keyUsed = true;
        }
    }

    /** 按住魔杖键 + 滚轮：调尺寸，滚轮不切快捷栏 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBuilderInputScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator() || !keyHeld()) {
            return;
        }
        InteractionHand hand = wandHand(player);
        if (hand == null || event.getScrollDelta() == 0D) {
            return;
        }
        event.setCanceled(true);
        keyUsed = true;
        resize(player, hand, event.getScrollDelta() > 0D ? 1 : -1, player.isShiftKeyDown());
    }

    /**
     * 按住魔杖键 + 右键：撤销，不走原版的使用。
     * 不按魔杖键时，形状用远距离瞄准、原版准星什么都没点中、远距离射线点中了方块（{@link #farHit}）：这一下原版会走「对空气用物品」
     * （按脚下开工），在这里拦下，改发 {@code AIM_START}（按远处点中的格子和面开工，服务端复核）。潜行右键照旧走原版（停下 / 提示撤销）。
     * 按住右键的连发每一下都照发（同原版连发的用物品包）：服务端在射线之前就按 6 刻间隔把它忽略掉、同时刷新间隔的起点，
     * 所以按住右键扫视角、准星在远处方块和天空 / 近处方块之间来回时，原版那几下也一直被当成连发，不会自动确认、也不会多排一单。
     * 魔杖在副手时主手那一轮不拦，同原版先问主手。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBuilderInputUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        InteractionHand hand = wandHand(player);
        if (hand == null) {
            return;
        }
        if (keyHeld()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            keyUsed = true;
            if (undoArmed) {
                undoArmed = false;
                send(C2SBuilderActionPacket.UNDO, hand, 0, 0);
            }
            return;
        }
        if (event.getHand() != hand || player.isSpectator() || player.isSecondaryUseActive()) {
            return;
        }
        BuilderShapes.Hit far = farHit(minecraft, player, player.getItemInHand(hand));
        if (far == null) {
            return;
        }
        event.setCanceled(true);
        sendAim(minecraft, player, hand, far);
    }

    /**
     * 远距离瞄准点中的格子和面：只在这根魔杖用远距离瞄准（{@link BuilderAim#applies}）、原版准星什么都没点中时才算
     * （原版点中了方块照旧走原版的右键方块，点中了实体照旧和实体交互）；瞄准距离内没点中、视线经过没加载的区块返回 null。
     * 按这一刻的位置和朝向算，和右键前补发给服务端的一致。
     */
    @Nullable
    public static BuilderShapes.Hit farHit(Minecraft minecraft, Player player, ItemStack wand) {
        HitResult result = minecraft.hitResult;
        if (result != null && result.getType() != HitResult.Type.MISS || !BuilderAim.applies(wand)) {
            return null;
        }
        return BuilderAim.trace(player, wand).hit();
    }

    /** 发远距离开工：同原版「用物品」，先把这一刻的位置和朝向发过去，服务端复核时用的视线就和这边算的是同一条 */
    private static void sendAim(Minecraft minecraft, LocalPlayer player, InteractionHand hand, BuilderShapes.Hit hit) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return;
        }
        connection.send(new ServerboundMovePlayerPacket.PosRot(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.onGround()));
        ECNetwork.sendToServer(C2SBuilderActionPacket.aim(hand == InteractionHand.OFF_HAND ? 1 : 0, hit.pos(), hit.face()));
    }

    @SubscribeEvent
    public static void onBuilderInputLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        BuilderClientConfig.markPrefsDirty();
        BuilderClientStatus.clear();
        pending = null;
    }

    @SubscribeEvent
    public static void onBuilderInputLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BuilderClientStatus.clear();
        pending = null;
        keyWasDown = false;
    }

    // ------------------------------------------------------------------
    // 操作
    // ------------------------------------------------------------------

    /** 单按魔杖键：铺开面板（死了、旁观时不开） */
    private static void openPanel(Minecraft minecraft, LocalPlayer player, InteractionHand hand) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        // 刚滚过还没发出去的尺寸（pending）留着照常发：开面板不改形状
        minecraft.setScreen(new BuilderPanelScreen(hand));
    }

    /** 滚一格：dir 是 +1 / −1。面板里的尺寸加减号、按住魔杖键滚轮也走这里 */
    public static void resize(LocalPlayer player, InteractionHand hand, int dir, boolean sneak) {
        ItemStack wand = player.getItemInHand(hand);
        BuilderShape shape = BuilderSelection.shape(wand);
        if (BuilderClientStatus.working()) {
            // 施工中服务端不收尺寸（改设置一律要先停下），本地也不先改显示
            player.displayClientMessage(Component.translatable("message.essentialcraft5.builder.panel_busy").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!shape.resizable()) {
            if (noSizeHintShown != shape) {
                noSizeHintShown = shape;
                String key = shape == BuilderShape.FACE ? "message.essentialcraft5.builder.face_no_size" : "message.essentialcraft5.builder.points_no_size";
                player.displayClientMessage(Component.translatable(key, Component.keybind(KEY)).withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            return;
        }
        int[] current = displaySize(hand, wand, shape);
        int width = current[0];
        int length = current[1];
        if (shape == BuilderShape.PLANE) {
            if (sneak) {
                length += dir;
            } else {
                int side = Math.max(width, length) + dir;
                width = side;
                length = side;
            }
        } else if (shape == BuilderShape.WALL) {
            // 竖墙：滚轮调宽，加潜行调高（第二个数是高）
            if (sneak) {
                length += dir;
            } else {
                width += dir;
            }
        } else if (sneak) {
            width += dir;
        } else {
            length += dir;
        }
        BuilderTier.Stats stats = BuilderTier.stats(wand);
        BuilderShapes.Size size = BuilderShapes.clamp(width, length, stats.maxSide(), stats.maxVolume());
        if (size.width() == current[0] && size.length() == current[1]) {
            return;
        }
        pending = new Pending(hand, shape, size.width(), size.length(), false, 0L);
    }

    private static void cycleShape(InteractionHand hand) {
        pending = null;
        send(C2SBuilderActionPacket.CYCLE_SHAPE, hand, 0, 0);
    }

    /** 发待确认的尺寸；NBT 对上了或发出太久就作废 */
    private static void tickPending(LocalPlayer player) {
        Pending local = pending;
        if (local == null) {
            return;
        }
        ItemStack wand = player.getItemInHand(local.hand);
        if (BuilderMode.of(wand) == null || BuilderSelection.shape(wand) != local.shape) {
            pending = null;
            return;
        }
        if (!local.sent) {
            if (ticks - lastSizeSent >= SEND_GAP_TICKS) {
                send(C2SBuilderActionPacket.SET_SIZE, local.hand, local.width, local.length);
                lastSizeSent = ticks;
                pending = new Pending(local.hand, local.shape, local.width, local.length, true, ticks);
            }
            return;
        }
        int[] stored = BuilderSelection.size(wand, local.shape);
        if (stored[0] == local.width && stored[1] == local.length || ticks - local.sentAt > PENDING_TIMEOUT) {
            pending = null;
        }
    }

    /** 面板发操作包用 */
    public static void send(int action, InteractionHand hand, int a, int b) {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        ECNetwork.sendToServer(new C2SBuilderActionPacket(action, hand == InteractionHand.OFF_HAND ? 1 : 0, a, b));
    }

    // ------------------------------------------------------------------
    // 展示厅用：不发按键，直接走同一段处理
    // ------------------------------------------------------------------

    /** 等于按住魔杖键（sneak 时加潜行）滚一格 */
    public static void showroomScroll(int dir, boolean sneak) {
        LocalPlayer player = Minecraft.getInstance().player;
        InteractionHand hand = player == null ? null : wandHand(player);
        if (hand != null) {
            resize(player, hand, dir, sneak);
        }
    }

    /** 切到下一个形状（第 1 批单按魔杖键的老操作；现在单按铺开面板，展示厅的老机位照旧用它切形状） */
    public static void showroomTap() {
        LocalPlayer player = Minecraft.getInstance().player;
        InteractionHand hand = player == null ? null : wandHand(player);
        if (hand != null) {
            cycleShape(hand);
        }
    }

    /** 等于按住魔杖键右键 */
    public static void showroomUndo() {
        LocalPlayer player = Minecraft.getInstance().player;
        InteractionHand hand = player == null ? null : wandHand(player);
        if (hand != null) {
            send(C2SBuilderActionPacket.UNDO, hand, 0, 0);
        }
    }
}
