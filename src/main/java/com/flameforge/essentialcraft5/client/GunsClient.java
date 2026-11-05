package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.GunModel;
import com.flameforge.essentialcraft5.client.render.BeamEffects;
import com.flameforge.essentialcraft5.client.render.MatrixHitEffects;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.item.GunItem;
import com.flameforge.essentialcraft5.network.S2CGunBeamPacket;
import com.flameforge.essentialcraft5.network.S2CGunMatrixHitPacket;
import com.flameforge.essentialcraft5.registry.ECGuns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * 枪与射线模块的客户端注册（模组总线，ECGuns）：
 * <ul>
 *   <li>物品模型 loader {@code essentialcraft5:gun}（{@link GunModel}）。</li>
 *   <li>物品格右侧的<b>热量条</b>（原作 {@code ItemOverlayGun}，像素照抄）：黑底 2×14、灰槽 1×13，红色 = 这一匣已射 / 弹量。</li>
 *   <li>狙击镜覆盖层（HUD，排在原版望远镜后面）：主手是装了狙击镜的狙击枪、潜行、第一人称时画 {@code hud/sniper_scope.png}，
 *       正方形边长 = 屏幕高、<b>十字线交点对准屏幕正中</b>（贴图的十字线本身偏右上，见 {@link #SCOPE_RETICLE_X}）、四周补黑（原作左边缘写死 w/4，宽屏上偏左）。
 *       准星同时藏掉（{@link GunsClientEvents}）。</li>
 *   <li>光束包的处理（{@link #onBeamPacket}，ECGuns 登记）：光束的起点让开镜头（审查 GUNS-3），开狙击镜时从镜子正下方出来。</li>
 *   <li>矩阵受损特效包的处理（{@link #onMatrixHitPacket}）和被打者屏幕边缘的品红晕影（HUD，{@code MatrixHitEffects.renderVignette}）。</li>
 * </ul>
 * {@link #showroomForceScope}：展示厅拍开镜画面用，打开后不用潜行也算开镜（只影响本客户端的画面）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GunsClient {

    /** 狙击镜贴图（512×512，中间透明） */
    public static final ResourceLocation SNIPER_SCOPE = EssentialCraft5.id("textures/hud/sniper_scope.png");
    /**
     * 狙击镜贴图里十字线交点的位置（占边长的比例）：原作贴图的圆心在正中（255.5, 255.5），十字线却画在 (263.5, 253.5)——
     * 竖线偏右 8 像素、横线偏上 2 像素。画的时候整张挪一下，让十字线交点落在屏幕正中（= 射线方向），
     * 不然开镜瞄准点和子弹落点差 1.6%（720p 上约 11 像素；复验时开镜光束落在竖线左边才看出来）
     */
    static final float SCOPE_RETICLE_X = 263.5F / 512F;
    static final float SCOPE_RETICLE_Y = 253.5F / 512F;

    /** 展示厅用：打开后不用潜行也按开镜画（缩放、覆盖层、藏第一人称的枪） */
    public static volatile boolean showroomForceScope;

    private GunsClient() {
    }

    @SubscribeEvent
    public static void registerGunGeometry(ModelEvent.RegisterGeometryLoaders event) {
        event.register(GunModel.LOADER_NAME, GunModel.Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void registerGunDecorations(RegisterItemDecorationsEvent event) {
        event.register(ECGuns.PISTOL.get(), HeatBar.INSTANCE);
        event.register(ECGuns.RIFLE.get(), HeatBar.INSTANCE);
        event.register(ECGuns.SNIPER.get(), HeatBar.INSTANCE);
        event.register(ECGuns.GATLING.get(), HeatBar.INSTANCE);
    }

    @SubscribeEvent
    public static void registerGunOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.SPYGLASS.id(), "sniper_scope", GunsClient::renderSniperScope);
        // 被射线加了矩阵损伤时屏幕边缘的品红晕影：在狙击镜、南瓜头、霜冻之后画（开着镜也看得见），快捷栏之前
        event.registerAbove(VanillaGuiOverlay.FROSTBITE.id(), "matrix_damage", MatrixHitEffects::renderVignette);
    }

    // ------------------------------------------------------------------
    // 光束包（ECGuns.init 登记的客户端处理器调；registerS2C 已套 DistExecutor，专用服不会加载这个类）
    // ------------------------------------------------------------------

    /** 自己的光束从枪口画：眼睛往前多少格 */
    static final double MUZZLE_FORWARD = 0.75D;
    /**
     * 自己的光束从枪口画：往拿枪那只手那边偏多少格（视野 70 时）。和下面的 {@link #MUZZLE_DOWN} 一起按展示厅 04_scene_gun_hand 量的：
     * 第一人称手枪的枪口（透镜那一格）在 1280×720 画面的 (942, 452)，反推回眼睛前 0.75 格处的偏移（复验时量，原来 0.3 / 0.22 落在枪口左下方约 110 像素）
     */
    static final double MUZZLE_SIDE = 0.44D;
    /** 自己的光束从枪口画：往下多少格（镜头的「下」，视野 70 时） */
    static final double MUZZLE_DOWN = 0.13D;
    /** 原版画手持物品固定用的视野（{@code GameRenderer.renderItemInHand} 调 {@code getFov(…, false)}，不吃视野设置） */
    private static final double HAND_FOV = 70D;
    /** 自己的光束开头渐显多少格 */
    static final float OWN_FADE_IN = 0.5F;
    /** 别人的光束从眼睛往前让开多少格（不从射手脑袋里长出来） */
    static final double OTHER_START = 0.5D;
    /** 别人的光束开头渐显多少格 */
    static final float OTHER_FADE_IN = 1F;
    /** 让开以后光束至少还要这么长才画（贴脸打墙就不画了） */
    static final double MIN_VISIBLE = 0.25D;
    /** 开狙击镜时自己的光束起点比镜子的圆边再往下多少（倍）：起点刚好藏在镜子下沿外面 */
    static final double SCOPE_EDGE = 1.15D;
    /** 开狙击镜时光束在镜里的角半径下限、上限（占半个视野角的比例）：太细会闪、太粗会糊住镜子 */
    static final double SCOPED_TAPER_MIN = 0.008D;
    static final double SCOPED_TAPER_MAX = 0.06D;
    /** 开狙击镜时光束在镜里的粗细 = 终点处本来粗细的多少倍（整条等宽；看展示厅 04_scene_sniper_fire_scoped 调） */
    static final double SCOPED_WIDTH = 0.6D;

    /**
     * 收到光束包：包里的起点是射手的眼睛（射线判定从这里算），画的时候让开镜头——
     * 原样从眼睛画的话，开枪的人镜头就在管口里，管子两面都画、加色混合，整屏会被染成透镜色（审查 GUNS-3）。
     * <ul>
     *   <li>自己打的（包里的射手 id 是本地玩家）：从现在的枪口位置（眼睛往前 {@value #MUZZLE_FORWARD}、往拿枪的手那边 {@value #MUZZLE_SIDE}、
     *       往下 {@value #MUZZLE_DOWN}）画到终点，开头 {@value #OWN_FADE_IN} 格渐显；看出去是一道从右下角收向准星的光。</li>
     *   <li>自己开着狙击镜打的（{@link #sniperScoped}，枪不画）：从<b>瞄准镜正下方</b>画（{@link #scopedBeam}，像枪管在镜子下面），
     *       不再从右边斜着挡住准星右侧（2026-09-29 改）。</li>
     *   <li>别人打的：从眼睛往前让开 {@value #OTHER_START} 格起画，开头 {@value #OTHER_FADE_IN} 格渐显。</li>
     *   <li>镜头贴着光束（被射线穿过脑袋、走进还没散的光束）时由 {@link BeamEffects} 按距离变淡。</li>
     * </ul>
     */
    public static void onBeamPacket(S2CGunBeamPacket packet) {
        Vec3 from = packet.from();
        Vec3 to = packet.to();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && packet.shooter() == player.getId()) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1F);
            if (to.subtract(eye).dot(look) <= MUZZLE_FORWARD + MIN_VISIBLE) {
                return;
            }
            if (sniperScoped()) {
                scopedBeam(player, eye, look, to, packet.balance());
                return;
            }
            BeamEffects.spawn(muzzle(player, eye, look), to, packet.balance(), OWN_FADE_IN);
            return;
        }
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length <= OTHER_START + MIN_VISIBLE) {
            return;
        }
        BeamEffects.spawn(from.add(axis.scale(OTHER_START / length)), to, packet.balance(), OTHER_FADE_IN);
    }

    /**
     * 本地玩家的枪口位置：拿枪的手是主手就在主手那边（默认右边），只有副手拿枪时在另一边。
     * 手持物品固定按视野 {@value #HAND_FOV} 画、世界按视野设置画，所以侧偏和下偏按 tan(视野设置 / 2) / tan(35°) 缩放，
     * 光束在屏幕上才从枪口出来（只看视野设置，不看疾跑、狙击镜这类临时倍率：开镜时枪不画，缩了反而会让管口贴着镜头中心）。
     */
    static Vec3 muzzle(LocalPlayer player, Vec3 eye, Vec3 look) {
        HumanoidArm arm = player.getMainArm();
        if (!(player.getMainHandItem().getItem() instanceof GunItem) && player.getOffhandItem().getItem() instanceof GunItem) {
            arm = arm.getOpposite();
        }
        double fovScale = Math.tan(Math.toRadians(Minecraft.getInstance().options.fov().get()) / 2D) / Math.tan(Math.toRadians(HAND_FOV) / 2D);
        double side = (arm == HumanoidArm.RIGHT ? MUZZLE_SIDE : -MUZZLE_SIDE) * fovScale;
        Vec3 right = cameraRight(player);
        Vec3 up = right.cross(look);
        return eye.add(look.scale(MUZZLE_FORWARD)).add(right.scale(side)).add(up.scale(-MUZZLE_DOWN * fovScale));
    }

    /** 镜头的「右」（水平）：朝 +z（yaw 0）时右 = −x；「上」= 右 × 视线（低头时跟着往前倾） */
    private static Vec3 cameraRight(LocalPlayer player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(yaw), 0D, -Mth.sin(yaw));
    }

    /**
     * 开着狙击镜时自己的光束（2026-09-29 改成从瞄准镜正下方射出，像枪管在镜子下面）：
     * <ul>
     *   <li>起点在眼睛正前方 {@value #MUZZLE_FORWARD} 格、<b>正下方</b>（不往两边偏），往下的量刚好让起点落在镜子圆边外面一点
     *       （角度 = 半个视野角 × {@value #SCOPE_EDGE}，视野角按视野设置 ÷ 瞄具倍率算，同 {@link GunsClientEvents} 的缩放）：
     *       镜里看是一道从镜子下沿中间升上来、收进准星的光。</li>
     *   <li>镜头一放大，贴着镜头的那一截照常粗细会糊满镜子下半边，所以起点一段收成锥形（{@link BeamEffects#spawn(Vec3, Vec3, int, float, float, float)}）：
     *       锥尖在镜头处，角半径取「光束在终点处本来的角半径」× {@value #SCOPED_WIDTH}（夹在半个视野角的 {@value #SCOPED_TAPER_MIN}~{@value #SCOPED_TAPER_MAX} 倍之间），
     *       整道光束在镜里是一条等宽的细线（只是射手自己看到的样子，别人看到的照常）。</li>
     *   <li>光束还没散就关了镜，看到的是一根从准星正下方伸出去的细线（整条都收细了），不会凭空悬在半空。</li>
     * </ul>
     */
    static void scopedBeam(LocalPlayer player, Vec3 eye, Vec3 look, Vec3 to, int balance) {
        double zoom = Math.max(1D, activeZoom(player));
        double halfFov = Math.toRadians(Minecraft.getInstance().options.fov().get() / zoom / 2D);
        Vec3 up = cameraRight(player).cross(look);
        Vec3 from = eye.add(look.scale(MUZZLE_FORWARD)).subtract(up.scale(MUZZLE_FORWARD * Math.tan(halfFov) * SCOPE_EDGE));
        double distance = Math.max(1.0E-3D, to.distanceTo(eye));
        double slope = Mth.clamp(SCOPED_WIDTH * BeamEffects.BASE_RADIUS / distance, SCOPED_TAPER_MIN * halfFov, SCOPED_TAPER_MAX * halfFov);
        BeamEffects.spawn(from, to, balance, OWN_FADE_IN, (float) slope, (float) from.distanceTo(eye));
    }

    // ------------------------------------------------------------------
    // 矩阵受损特效包（ECGuns.init 登记；GUNS-4）
    // ------------------------------------------------------------------

    /** 收到「这名玩家被射线加了矩阵损伤」：身上的电弧、粒子、音效，是自己时另亮屏幕边缘的晕影（{@link MatrixHitEffects}） */
    public static void onMatrixHitPacket(S2CGunMatrixHitPacket packet) {
        MatrixHitEffects.onPacket(packet.entity(), packet.intensity());
    }

    /** 展示厅：拍枪的物品提示时当作按着 Shift（展开属性），不动真键盘 */
    private static volatile boolean showroomShift;

    /** 物品提示里「按住 Shift 看属性」用（按键状态只在客户端有） */
    public static Boolean hasShiftDown() {
        return showroomShift || Screen.hasShiftDown();
    }

    /** 展示厅：true = 枪的物品提示按「按着 Shift」画 */
    public static void setShowroomShift(boolean shift) {
        showroomShift = shift;
    }

    // ------------------------------------------------------------------
    // 开镜判断（GunsClientEvents 也用）
    // ------------------------------------------------------------------

    /** 主手枪的瞄具倍率（没有枪、没组装、没装瞄具为 0） */
    public static float zoomOf(ItemStack stack) {
        if (GunType.of(stack).isEmpty()) {
            return 0F;
        }
        return GunStats.of(stack).map(GunStats::zoom).orElse(0F);
    }

    /** 正在用瞄具（潜行 + 主手的枪装了瞄具；展示厅开关也算） */
    public static float activeZoom(Player player) {
        if (!player.isShiftKeyDown() && !showroomForceScope) {
            return 0F;
        }
        return zoomOf(player.getMainHandItem());
    }

    /** 正在用狙击镜（画覆盖层、藏第一人称的枪）：主手狙击枪、开镜、第一人称 */
    public static boolean sniperScoped() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !minecraft.options.getCameraType().isFirstPerson()) {
            return false;
        }
        ItemStack main = player.getMainHandItem();
        return GunType.of(main).filter(type -> type == GunType.SNIPER).isPresent() && activeZoom(player) > 0F;
    }

    private static void renderSniperScope(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (gui.getMinecraft().options.hideGui || !sniperScoped()) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        int size = height;
        // 十字线交点对准屏幕正中（SCOPE_RETICLE_X / Y）：整张往左挪约 1.6%、往下挪约 0.4%，挪出来的空当补黑（贴图外圈本来就是不透明黑，补的黑和它重叠也看不出来）
        float x = (width - size) / 2F + size * (0.5F - SCOPE_RETICLE_X);
        float y = size * (0.5F - SCOPE_RETICLE_Y);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0F);
        graphics.blit(SNIPER_SCOPE, 0, 0, -90, 0F, 0F, size, size, size, size);
        graphics.pose().popPose();
        int left = Mth.ceil(x);
        int right = Mth.floor(x + size);
        int top = Mth.ceil(y);
        if (left > 0) {
            graphics.fill(RenderType.guiOverlay(), 0, 0, left, height, -90, 0xFF000000);
        }
        if (right < width) {
            graphics.fill(RenderType.guiOverlay(), right, 0, width, height, -90, 0xFF000000);
        }
        if (top > 0) {
            graphics.fill(RenderType.guiOverlay(), 0, 0, width, top, -90, 0xFF000000);
        }
    }

    /** 热量条：物品格右侧 2 像素宽，红色 = 这一匣已射 / 弹量（原作 ItemOverlayGun L614~657） */
    private static final class HeatBar implements IItemDecorator {

        static final HeatBar INSTANCE = new HeatBar();

        @Override
        public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
            Optional<GunStats> stats = GunStats.of(stack);
            if (stats.isEmpty()) {
                return false;
            }
            int magazine = stats.get().magazine();
            double heat = Math.min(GunParts.fired(stack), magazine) / (double) magazine;
            int top = (int) Math.round(13D - heat * 13D);
            graphics.fill(RenderType.guiOverlay(), x + 14, y, x + 16, y + 14, 0xFF000000);
            graphics.fill(RenderType.guiOverlay(), x + 14, y, x + 15, y + 13, 0xFF333333);
            if (top < 13) {
                graphics.fill(RenderType.guiOverlay(), x + 14, y + top, x + 15, y + 13, 0xFFBA0000);
            }
            return false;
        }
    }
}
