package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.Shapes;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.item.ComputerArmorEvents;
import com.flameforge.essentialcraft5.network.C2SComputerArmorPacket;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CBoardFlyingPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.client.screen.ComputerTerminalScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * 电脑一系的客户端（模组总线，ECComputer）：宅男套装的三个按键、HUD 上的小圆、飞行时脚下的反重力板。
 * <ul>
 *   <li>按键（原作 ClientProxy L536~539）：喷射推进默认 Z、夜视默认 X。X 和原版创造模式的「加载快捷栏」同键，只在创造模式里会一起触发，可以改键。
 *       按下只发 {@link C2SComputerArmorPacket}（一个字节的意图），能不能冲、开不开夜视由服务端决定；本地先看一眼穿没穿、冷却过没过，
 *       不满足就不发包（冷却圈也只在发出去以后才转）。</li>
 *   <li>HUD（原作 RenderHandlerEC L788~925，原作画在 CROSSHAIRS 那一层，坐标在屏幕顶上、Boss 血条两边）：戴着头盔时左边一个深蓝小圆 + 头盔图标，
 *       夜视开着时亮、关着时暗；穿全套时右边一个青色小圆 + 烟花火箭图标，冷却时按剩下的冷却缺一块、冷却完是整圆。
 *       原作两个圆的透明度一直在 0.1~0.8 之间来回跳，这里固定不闪。夜视开没开看本地玩家身上有没有隐藏图标的夜视（服务端给的那份）。
 *       穿全套、饰品封禁场开着时，夜视圆左边再画一个紫圆 + 禁用图标：运转时亮、待机和电量不足时暗（第十批，状态来自
 *       {@link CurioSealClient#fieldState()}，服务端只在状态变化时推）。</li>
 *   <li>宅男终端（第十批）：默认 Alt+C，戴着头盔就能打开 {@link ComputerTerminalScreen}。</li>
 *   <li>反重力板（原作 RenderHandlerEC L228~252）：飞着时在脚下画 {@link ECComputer#BOARD_MODEL}（board.obj，贴图 block/model/board），
 *       长边顺着身体朝向，缩放 0.8 照原作。原作没有平移到玩家的位置（画在镜头原点上）；这里画在脚下。
 *       本地玩家看 {@code abilities.flying}；别的玩家看服务端发来的 {@link S2CBoardFlyingPacket}（状态变了、或者开始看到这名玩家时才发），
 *       记在 {@link #BOARD_FLYERS} 里，退出世界时清空。以前按「不在地上、没在往下掉」估，起跳、爬梯子、缓降时都会闪出板子。旁观者不画。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ComputerClient {

    public static final KeyMapping KEY_BOOST = new KeyMapping(ComputerArmorEvents.KEY_BOOST, KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories." + EssentialCraft5.MODID);
    public static final KeyMapping KEY_NIGHT_VISION = new KeyMapping(ComputerArmorEvents.KEY_NIGHT_VISION, KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories." + EssentialCraft5.MODID);
    /**
     * 宅男终端（第十批）：默认 Alt+C。原版只把不带修饰键的 C 给创造模式的「保存快捷栏」；本模组已有 Z / X，Curios 是 G；
     * 一份 170 多个模组的整合包里单字母 C 有十几个绑定、Alt+C 没人用（Alt+Z、Alt+X 已被占）
     */
    public static final KeyMapping KEY_TERMINAL = new KeyMapping(ComputerTerminalScreen.KEY, KeyConflictContext.IN_GAME, KeyModifier.ALT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories." + EssentialCraft5.MODID);

    /** HUD 小圆的半径与位置（原作 r = 10、y = 11、x = 屏幕中线 −108 / +105） */
    private static final float HUD_RADIUS = 10F;
    private static final float HUD_Y = 11F;
    private static final float HUD_NIGHT_VISION_X = -108F;
    private static final float HUD_BOOST_X = 105F;
    private static final int HUD_SEGMENTS = 48;
    /** 夜视圆：开着（原作颜色 (0, 0, 1)）、关着（(0, 0, 0.2)），透明度固定 0.8 */
    private static final int NIGHT_VISION_ON = 0xCC0000FF;
    private static final int NIGHT_VISION_OFF = 0xCC000033;
    /** 冲刺圆：冷却好的部分青色（原作 (0, 1, 1)），冷却中缺掉的部分是暗底 */
    private static final int BOOST_READY = 0xCC00FFFF;
    private static final int BOOST_TRACK = 0x66003333;
    /** 封禁场圆（在夜视圆左边）：运转时亮品红、待机 / 电量不足时暗紫 */
    private static final float HUD_SEAL_X = -132F;
    private static final int SEAL_RUNNING = 0xCCF040E8;
    private static final int SEAL_IDLE = 0xCC2A1238;

    /** 反重力板：缩放（原作 0.8）、中心在脚下多深（板厚 0.2 × 0.8，顶面贴着脚底） */
    private static final float BOARD_SCALE = 0.8F;
    private static final double BOARD_DROP = 0.1D;
    /** 服务端说「正踩着反重力板在飞」的别的玩家（实体 id；只在客户端主线程读写） */
    private static final IntSet BOARD_FLYERS = new IntOpenHashSet();

    private static final RandomSource RANDOM = RandomSource.create();

    /** 本地上次发出冲刺包时的 tickCount（冷却圈用；重生后 tickCount 从 0 起，比它小时当作冷却完了） */
    private static int lastDashTick = Integer.MIN_VALUE;
    /** 展示厅：冲刺圈固定画成这个就绪比例（0~1），null = 照常按冷却算 */
    @Nullable
    private static Float showroomBoostReady;

    private ComputerClient() {
    }

    @SubscribeEvent
    public static void registerComputerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_BOOST);
        event.register(KEY_NIGHT_VISION);
        event.register(KEY_TERMINAL);
    }

    @SubscribeEvent
    public static void registerComputerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "computer_armor", ComputerClient::renderHud);
    }

    /** 板子的 OBJ 不属于任何物品状态，要单独登记才会被烘焙 */
    @SubscribeEvent
    public static void registerComputerModels(ModelEvent.RegisterAdditional event) {
        event.register(ECComputer.BOARD_MODEL);
    }

    // ------------------------------------------------------------------
    // 按键
    // ------------------------------------------------------------------

    private static void handleKeys(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            while (KEY_NIGHT_VISION.consumeClick()) {
                // 没进世界时的按键丢掉
            }
            while (KEY_BOOST.consumeClick()) {
                // 同上
            }
            while (KEY_TERMINAL.consumeClick()) {
                // 同上
            }
            return;
        }
        while (KEY_NIGHT_VISION.consumeClick()) {
            if (ComputerArmorEvents.wears(player, ArmorItem.Type.HELMET) && !player.isSpectator()) {
                ECNetwork.sendToServer(new C2SComputerArmorPacket(C2SComputerArmorPacket.NIGHT_VISION));
            }
        }
        while (KEY_BOOST.consumeClick()) {
            if (canDash(player) && boostCooldownLeft(player, 0F) <= 0F) {
                ECNetwork.sendToServer(new C2SComputerArmorPacket(C2SComputerArmorPacket.DASH));
                lastDashTick = player.tickCount;
            }
        }
        while (KEY_TERMINAL.consumeClick()) {
            // 戴着头盔就能打开（夜视开关在里面）；能不能开封禁场由服务端决定
            if (minecraft.screen == null && ComputerArmorEvents.wears(player, ArmorItem.Type.HELMET) && !player.isSpectator()) {
                minecraft.setScreen(new ComputerTerminalScreen());
            }
        }
    }

    /** 冲刺还剩多少刻冷却（终端里显示，不带插值） */
    public static int boostCooldownTicks(Player player) {
        return Math.round(boostCooldownLeft(player, 0F));
    }

    /** 本地先看一眼（服务端照样再查一遍） */
    private static boolean canDash(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isSleeping() && ComputerArmorEvents.hasFullSet(player);
    }

    /** 还剩多少刻冷却（带插值，HUD 转得顺） */
    private static float boostCooldownLeft(Player player, float partialTick) {
        if (lastDashTick == Integer.MIN_VALUE || player.tickCount < lastDashTick) {
            return 0F;
        }
        float elapsed = player.tickCount - lastDashTick + partialTick;
        return Math.max(0F, ECComputer.Config.values().dashCooldown() - elapsed);
    }

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------

    private static void renderHud(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        boolean helmet = ComputerArmorEvents.wears(player, ArmorItem.Type.HELMET);
        boolean fullSet = helmet && ComputerArmorEvents.hasFullSet(player);
        if (!helmet) {
            return;
        }
        float center = width / 2F;
        float nightX = center + HUD_NIGHT_VISION_X;
        float boostX = center + HUD_BOOST_X;
        MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
        boolean nightOn = nightVision != null && !nightVision.showIcon();

        float sealX = center + HUD_SEAL_X;
        S2CComputerTerminalPacket.FieldState seal = CurioSealClient.fieldState();
        boolean sealShown = fullSet && (seal == S2CComputerTerminalPacket.FieldState.STANDBY || seal == S2CComputerTerminalPacket.FieldState.RUNNING
                || seal == S2CComputerTerminalPacket.FieldState.NO_POWER);

        BufferBuilder buffer = Shapes.begin(graphics);
        Matrix4f matrix = graphics.pose().last().pose();
        Shapes.polygon(buffer, matrix, sector(nightX, HUD_Y, 1F), nightOn ? NIGHT_VISION_ON : NIGHT_VISION_OFF);
        if (sealShown) {
            Shapes.polygon(buffer, matrix, sector(sealX, HUD_Y, 1F), seal == S2CComputerTerminalPacket.FieldState.RUNNING ? SEAL_RUNNING : SEAL_IDLE);
        }
        if (fullSet) {
            int cooldown = ECComputer.Config.values().dashCooldown();
            Float frozen = showroomBoostReady;
            float ready = frozen != null ? frozen : cooldown <= 0 ? 1F : 1F - boostCooldownLeft(player, partialTick) / cooldown;
            Shapes.polygon(buffer, matrix, sector(boostX, HUD_Y, 1F), BOOST_TRACK);
            if (ready > 0F) {
                Shapes.polygon(buffer, matrix, sector(boostX, HUD_Y, Mth.clamp(ready, 0F, 1F)), BOOST_READY);
            }
        }
        Shapes.end();

        graphics.renderItem(new ItemStack(ECComputer.COMPUTER_HELMET.get()), Math.round(nightX) - 8, Math.round(HUD_Y) - 8);
        if (fullSet) {
            graphics.renderItem(new ItemStack(Items.FIREWORK_ROCKET), Math.round(boostX) - 8, Math.round(HUD_Y) - 8);
        }
        if (sealShown) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(CurioSealClient.ICON, Math.round(sealX) - 8, Math.round(HUD_Y) - 8, 0F, 0F, 16, 16, 16, 16);
            RenderSystem.disableBlend();
        }
    }

    /** 展示厅拍 HUD 用：冲刺圈固定画成 ready（0~1）的就绪比例，null 恢复照常（只影响本客户端的画面） */
    public static void setShowroomBoostReady(@Nullable Float ready) {
        showroomBoostReady = ready;
    }

    /** 从正上方顺时针扫过 fraction 圈的扇形（第一个点是圆心，Shapes.polygon 按扇形三角化，扇形对圆心是星形的，照样画得对） */
    private static float[][] sector(float cx, float cy, float fraction) {
        int segments = Math.max(1, Math.round(HUD_SEGMENTS * fraction));
        float[][] points = new float[segments + 2][2];
        points[0][0] = cx;
        points[0][1] = cy;
        double start = -Math.PI / 2D;
        double step = Math.PI * 2D * fraction / segments;
        for (int i = 0; i <= segments; i++) {
            double angle = start + step * i;
            points[i + 1][0] = cx + (float) Math.cos(angle) * HUD_RADIUS;
            points[i + 1][1] = cy + (float) Math.sin(angle) * HUD_RADIUS;
        }
        return points;
    }

    // ------------------------------------------------------------------
    // 反重力板
    // ------------------------------------------------------------------

    private static void renderBoard(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        if (!isBoardFlying(player) || !CuriosCompat.has(player, ECComputer.COMPUTER_BOARD.get())) {
            return;
        }
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(ECComputer.BOARD_MODEL);
        PoseStack pose = event.getPoseStack();
        float yaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        pose.pushPose();
        pose.translate(0D, -BOARD_DROP, 0D);
        // 模型的长边是 x 轴；转到身体朝向（朝向 0 = 南 +z）
        pose.mulPose(Axis.YP.rotationDegrees(90F - yaw));
        pose.scale(BOARD_SCALE, BOARD_SCALE, BOARD_SCALE);
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(Sheets.solidBlockSheet());
        PoseStack.Pose last = pose.last();
        int light = event.getPackedLight();
        for (Direction side : Direction.values()) {
            drawQuads(model, side, last, consumer, light);
        }
        drawQuads(model, null, last, consumer, light);
        pose.popPose();
    }

    private static void drawQuads(BakedModel model, @Nullable Direction side, PoseStack.Pose pose, VertexConsumer consumer, int light) {
        RANDOM.setSeed(42L);
        for (BakedQuad quad : model.getQuads(null, side, RANDOM, ModelData.EMPTY, null)) {
            consumer.putBulkData(pose, quad, 1F, 1F, 1F, light, OverlayTexture.NO_OVERLAY);
        }
    }

    /** 在飞：本地玩家看能力；别的玩家查服务端发来的表（见类注释）；旁观者不算 */
    private static boolean isBoardFlying(Player player) {
        if (player.isSpectator()) {
            return false;
        }
        if (player instanceof LocalPlayer) {
            return player.getAbilities().flying;
        }
        return BOARD_FLYERS.contains(player.getId());
    }

    /** 飞行状态包（ECComputer 登记的客户端处理器调） */
    public static void onBoardFlyingPacket(S2CBoardFlyingPacket packet) {
        if (packet.flying()) {
            BOARD_FLYERS.add(packet.entityId());
        } else {
            BOARD_FLYERS.remove(packet.entityId());
        }
    }

    /** Forge 总线上的客户端事件（按键、画板子） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class ComputerClientEvents {

        private ComputerClientEvents() {
        }

        @SubscribeEvent
        public static void onComputerClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                handleKeys(Minecraft.getInstance());
            }
        }

        @SubscribeEvent
        public static void onComputerRenderPlayer(RenderPlayerEvent.Post event) {
            renderBoard(event);
        }

        /** 退出世界：这些实体 id 只在这次连接里作数 */
        @SubscribeEvent
        public static void onComputerClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            BOARD_FLYERS.clear();
        }
    }
}
