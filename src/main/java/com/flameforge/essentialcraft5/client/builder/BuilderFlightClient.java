package com.flameforge.essentialcraft5.client.builder;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.BuilderClientConfig;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.item.MagicalBuilderItem;
import com.flameforge.essentialcraft5.network.S2CBuilderFlightPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 创世神之杖飞行特效的客户端入口：收 {@link S2CBuilderFlightPacket}，交给 {@link BuilderFlights} 排飞行体；另管杖尖位置、起飞 / 落位 / 碎掉的光效和声音限频。
 * <ul>
 *   <li>飞出（kind 0）：每格一个飞行体，从杖尖飞到格子；落位时刻 = 收包时刻 + {@code leadTicks} + i / n（同一包里按顺序错开一刻内的小数）。
 *   服务端在发包之后 {@code leadTicks} 刻才真放，耗时预算不够时会再晚几刻、不另发包（飞行体停在落点上等，最多 10 刻）；
 *   这一单的放置声由客户端放，服务端不放，所以关了飞行画面时落位声照放。</li>
 *   <li>飞回（kind 1）：撤销时真方块已经删掉，从格子飞回杖尖。这些格子服务端不再放原版的破坏碎屑（和飞回的方块叠在一起）；
 *   关了飞行画面的人由这里按原版的样子补放碎屑和破碎声（{@link #undoFallback}）。</li>
 *   <li>收尾（kind 2）：这一单不会再放任何已经发过的格子。格子里已经有方块的照常落位；没有的先等 2 刻
 *   （最后一批真放和收尾包同一刻发出，方块变化下一刻才同步过来），还没有就碎掉。</li>
 * </ul>
 * 服务端不按「包里缺了哪些格」传任何意思（被完整方块包住的格子本来就不发），这里也不推断。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class BuilderFlightClient {

    /** 飞行特效的配色：芯偏粉白，辉光薰衣草，外晕取模组的紫 */
    public static final VfxPlasma.Tint TINT = new VfxPlasma.Tint(0xFFE6FA, VfxPalette.LAVENDER, VfxPalette.VIOLET, VfxPalette.DEEP_VIOLET);

    /** 起飞星芒和起飞声：每个施法者每几刻最多一次 */
    private static final int LAUNCH_FX_GAP = 4;
    /** 落位声：每几刻最多一次 */
    private static final int LAND_SOUND_GAP = 3;
    private static final float LAND_VOLUME = 0.6F;
    private static final float LAUNCH_VOLUME = 0.5F;
    /** 杖尖在物品贴图里的位置（模型坐标 0~1，贴图右上角那颗晶体） */
    private static final float TIP_X = 0.9F;
    private static final float TIP_Y = 0.9F;
    private static final float TIP_Z = 0.5F;
    /** 第一人称手持物品按固定 70 度视角画（不随视角设置和冲刺缩放），换算到世界视角时要按这个比例拉开 */
    private static final double HAND_FOV = 70D;

    private static final Int2IntOpenHashMap LAST_LAUNCH_FX = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap LAST_RETURN_FX = new Int2IntOpenHashMap();
    private static int lastLandSound = Integer.MIN_VALUE / 2;
    private static int lastBreakSound = Integer.MIN_VALUE / 2;
    /** 碎掉时迸出的碎块：每块方块最多几颗（再按画质和距离减） */
    private static final float BREAK_FRAGMENTS = 10F;
    /** 关了飞行画面时撤销补放的原版破坏效果：每个包最多几格（同服务端拆方块时每任务每刻的上限） */
    private static final int UNDO_FALLBACK_EFFECTS = 4;
    private static double worldFov = HAND_FOV;

    private BuilderFlightClient() {
    }

    // ------------------------------------------------------------------ 收包

    /** 客户端主线程调 */
    public static void handle(S2CBuilderFlightPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        if (packet.kind() == S2CBuilderFlightPacket.KIND_END) {
            BuilderFlights.end(level, packet.casterId(), packet.jobId());
            return;
        }
        boolean visuals = BuilderClientConfig.flights();
        boolean undo = packet.kind() == S2CBuilderFlightPacket.KIND_UNDO;
        if (undo && !visuals) {
            undoFallback(level, packet);
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double now = BuilderFlights.clock();
        int n = packet.count();
        int lead = packet.leadTicks();
        for (int i = 0; i < n; i++) {
            BlockPos pos = packet.pos(i);
            int range = BuilderFlightMath.range(camera.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
            if (range == BuilderFlightMath.RANGE_NONE) {
                continue;
            }
            if (undo) {
                double launch = now + (double) i / n;
                BuilderFlights.addBack(packet.casterId(), packet.jobId(), pos, packet.stateId(i), launch,
                        launch + BuilderFlightMath.backTicks(lead), range == BuilderFlightMath.RANGE_FLASH);
            } else {
                double land = BuilderFlightMath.landAt(now, lead, i, n);
                byte mode;
                if (!visuals) {
                    mode = BuilderFlights.SOUND;
                } else if (range == BuilderFlightMath.RANGE_FLASH) {
                    mode = BuilderFlights.FLASH;
                } else {
                    mode = BuilderFlights.PENDING;
                }
                BuilderFlights.addOut(packet.casterId(), packet.jobId(), pos, packet.stateId(i),
                        BuilderFlightMath.launchAt(now, land), land, mode);
            }
        }
    }

    /**
     * 关了飞行画面时的撤销：服务端对进了飞回包的格子不再放破坏效果，这里照原版 {@code levelEvent 2001} 的样子补
     * （碎屑粒子 + 破碎声），每个包均匀挑最多 {@value #UNDO_FALLBACK_EFFECTS} 格，和服务端拆方块时的份额一样。
     */
    private static void undoFallback(ClientLevel level, S2CBuilderFlightPacket packet) {
        int n = packet.count();
        if (n <= 0) {
            return;
        }
        int stride = Math.max(1, (n + UNDO_FALLBACK_EFFECTS - 1) / UNDO_FALLBACK_EFFECTS);
        for (int i = 0; i < n; i += stride) {
            BlockPos pos = packet.pos(i);
            BlockState state = net.minecraft.world.level.block.Block.stateById(packet.stateId(i));
            if (state.isAir()) {
                continue;
            }
            level.addDestroyBlockEffect(pos, state);
            SoundType sound = state.getSoundType();
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound.getBreakSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.8F, false);
        }
    }

    // ------------------------------------------------------------------ 杖尖

    /** 施法者此刻的杖尖：本人第一人称按手持物品的变换算，其余按身体朝向加手的偏移 */
    public static Vec3 wandTip(Player player, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && mc.getCameraEntity() == player && mc.options.getCameraType().isFirstPerson()) {
            try {
                return firstPersonTip(mc, mc.player, partial);
            } catch (RuntimeException e) {
                // 拿不到手持模型的变换时退回按身体算，不影响别的
                return bodyTip(player, partial);
            }
        }
        return bodyTip(player, partial);
    }

    private static InteractionHand wandHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof MagicalBuilderItem) {
            return InteractionHand.MAIN_HAND;
        }
        return player.getOffhandItem().getItem() instanceof MagicalBuilderItem ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static HumanoidArm armOf(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    /**
     * 照 {@code ItemInHandRenderer} 画手持物品的那一串变换（手随视角的晃动、挥手、手臂位置、物品模型的第一人称变换）算出杖尖在视野里的位置，
     * 再按镜头朝向换回世界坐标。拿起物品的那段下沉动画和视角晃动不算（差得很少）。
     */
    private static Vec3 firstPersonTip(Minecraft mc, LocalPlayer player, float partial) {
        InteractionHand hand = wandHand(player);
        boolean right = armOf(player, hand) == HumanoidArm.RIGHT;
        int side = right ? 1 : -1;
        ItemStack stack = player.getItemInHand(hand);
        PoseStack pose = new PoseStack();
        float xBob = Mth.lerp(partial, player.xBobO, player.xBob);
        float yBob = Mth.lerp(partial, player.yBobO, player.yBob);
        pose.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(partial) - xBob) * 0.1F));
        pose.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partial) - yBob) * 0.1F));
        InteractionHand swinging = player.swingingArm == null ? InteractionHand.MAIN_HAND : player.swingingArm;
        float swing = swinging == hand ? player.getAttackAnim(partial) : 0F;
        float root = Mth.sqrt(swing);
        pose.translate(side * -0.4F * Mth.sin(root * Mth.PI), 0.2F * Mth.sin(root * Mth.TWO_PI), -0.2F * Mth.sin(swing * Mth.PI));
        pose.translate(side * 0.56F, -0.52F, -0.72F);
        float f = Mth.sin(swing * swing * Mth.PI);
        pose.mulPose(Axis.YP.rotationDegrees(side * (45F + f * -20F)));
        float f1 = Mth.sin(root * Mth.PI);
        pose.mulPose(Axis.ZP.rotationDegrees(side * f1 * -20F));
        pose.mulPose(Axis.XP.rotationDegrees(f1 * -80F));
        pose.mulPose(Axis.YP.rotationDegrees(side * -45F));
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, player, player.getId());
        model.applyTransform(right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, pose, !right);
        pose.translate(-0.5F, -0.5F, -0.5F);
        Vector4f v = new Vector4f(TIP_X, TIP_Y, TIP_Z, 1F).mul(pose.last().pose());
        double k = Math.tan(Math.toRadians(worldFov) * 0.5D) / Math.tan(Math.toRadians(HAND_FOV) * 0.5D);
        Camera camera = mc.gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Vector3f look = camera.getLookVector();
        // 视野坐标：x 向右、y 向上、-z 向前；镜头的 left 向量指向左边
        double x = v.x() * k;
        double y = v.y() * k;
        double z = -v.z();
        Vec3 eye = camera.getPosition();
        return new Vec3(eye.x - left.x() * x + up.x() * y + look.x() * z,
                eye.y - left.y() * x + up.y() * y + look.y() * z,
                eye.z - left.z() * x + up.z() * y + look.z() * z);
    }

    /** 第三人称、别的玩家：身体朝向往前 0.75 格、往持杖那只手的一侧 0.4 格，高度按身高折算（潜行时跟着矮） */
    private static Vec3 bodyTip(Player player, float partial) {
        float yaw = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        double side = armOf(player, wandHand(player)) == HumanoidArm.RIGHT ? 1D : -1D;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double rx = -Mth.cos(yaw) * side;
        double rz = -Mth.sin(yaw) * side;
        Vec3 feet = player.getPosition(partial);
        double height = 1.55D * player.getBbHeight() / 1.8D;
        return new Vec3(feet.x + fx * 0.75D + rx * 0.4D, feet.y + height, feet.z + fz * 0.75D + rz * 0.4D);
    }

    @SubscribeEvent
    public static void onBuilderFlightFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) {
            worldFov = event.getFOV();
        }
    }

    // ------------------------------------------------------------------ 光效和声音

    /** 起飞：杖尖小星芒 + 紫水晶轻响，每个施法者每 {@value #LAUNCH_FX_GAP} 刻最多一次 */
    static void onLaunch(ClientLevel level, int casterId, Vec3 tip, int clock) {
        if (clock - LAST_LAUNCH_FX.getOrDefault(casterId, Integer.MIN_VALUE / 2) < LAUNCH_FX_GAP) {
            return;
        }
        LAST_LAUNCH_FX.put(casterId, clock);
        VfxParticles.spawn(tip.x, tip.y, tip.z, 0D, 0D, 0D, 4, 0.16F, 0.05F, Vfx.argb(TINT.core(), 0.9F),
                Vfx.argb(TINT.glow(), 0F), VfxMode.FLARE, 600, 1F, 0F, 0F, 0.1F);
        if (BuilderClientConfig.flightSounds()) {
            RandomSource random = Vfx.random();
            level.playLocalSound(tip.x, tip.y, tip.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, LAUNCH_VOLUME,
                    1.2F + random.nextFloat() * 0.4F, false);
        }
    }

    /** 飞回到杖尖：一点小亮光，每个施法者每 {@value #LAUNCH_FX_GAP} 刻最多一次 */
    static void onReturn(int casterId, Vec3 tip, int clock) {
        if (clock - LAST_RETURN_FX.getOrDefault(casterId, Integer.MIN_VALUE / 2) < LAUNCH_FX_GAP) {
            return;
        }
        LAST_RETURN_FX.put(casterId, clock);
        VfxParticles.spawn(tip.x, tip.y, tip.z, 0D, 0D, 0D, 3, 0.12F, 0.03F, Vfx.argb(TINT.core(), 0.8F),
                Vfx.argb(TINT.glow(), 0F), VfxMode.DOT, 600, 1F, 0F, 0F, 0F);
    }

    /**
     * 落位：格子中心一团小柔光（3～4 刻淡掉）+ 两三颗往上飘的余辉，不画贴地的圈；只闪不飞的格子亮度减半。落位声另由 {@link #landSound} 放。
     *
     * @param half 只闪不飞（同屏满了、太远、来不及起飞）
     */
    static void onLand(BlockPos cell, boolean half) {
        Vec3 center = Vec3.atCenterOf(cell);
        if (!Vfx.visibleAt(center)) {
            return;
        }
        RandomSource random = Vfx.random();
        float bright = half ? 0.5F : 1F;
        Vec3 face = towardCamera(center, 0.62D);
        VfxParticles.spawn(face.x, face.y, face.z, 0D, 0D, 0D, 3 + random.nextInt(2), 0.62F, 0.38F, Vfx.argb(TINT.inner(), 0.8F * bright),
                Vfx.argb(TINT.glow(), 0F), VfxMode.DOT, 0, 1F, 0F, 0F, 0F);
        int n = Vfx.count(2.5F * bright, center);
        for (int i = 0; i < n; i++) {
            double x = cell.getX() + 0.15D + random.nextDouble() * 0.7D;
            double z = cell.getZ() + 0.15D + random.nextDouble() * 0.7D;
            VfxParticles.spawn(x, cell.getY() + 1.04D, z, (random.nextDouble() - 0.5D) * 0.01D, 0.025D + random.nextDouble() * 0.015D,
                    (random.nextDouble() - 0.5D) * 0.01D, 16 + random.nextInt(8), 0.1F, 0.03F, Vfx.argb(TINT.core(), 0.9F * bright),
                    Vfx.argb(TINT.glow(), 0F), VfxMode.DOT, 600, 0.94F, -0.0008F, 0F, 0F);
        }
    }

    /** 落位声：方块自己的放置声，音量 {@value #LAND_VOLUME}，每 {@value #LAND_SOUND_GAP} 刻最多一次；关了飞行声不放 */
    static void landSound(ClientLevel level, BlockPos cell, BlockState state, int clock) {
        if (!BuilderClientConfig.flightSounds() || clock - lastLandSound < LAND_SOUND_GAP) {
            return;
        }
        lastLandSound = clock;
        SoundType sound = state.getSoundType();
        float pitch = sound.getPitch() * 0.8F * (0.9F + Vfx.random().nextFloat() * 0.2F);
        level.playLocalSound(cell.getX() + 0.5D, cell.getY() + 0.5D, cell.getZ() + 0.5D, sound.getPlaceSound(), SoundSource.BLOCKS,
                LAND_VOLUME, pitch, false);
    }

    /** 等不到真方块（被拒、没料、立不住）或收尾时还没放：碎成几颗光点往外散开、淡掉 */
    static void onShatter(Vec3 at) {
        if (!Vfx.visibleAt(at)) {
            return;
        }
        RandomSource random = Vfx.random();
        int n = Vfx.count(6F, at);
        for (int i = 0; i < n; i++) {
            Vec3 dir = VfxPlasma.randomDir(random);
            double speed = 0.03D + random.nextDouble() * 0.04D;
            VfxParticles.spawn(at.x + dir.x * 0.25D, at.y + dir.y * 0.25D, at.z + dir.z * 0.25D, dir.x * speed, dir.y * speed + 0.01D,
                    dir.z * speed, 8 + random.nextInt(5), 0.09F, 0.02F, Vfx.argb(TINT.inner(), 0.9F), Vfx.argb(TINT.glow(), 0F),
                    VfxMode.DOT, 600, 0.9F, 0F, 0F, 0F);
        }
        Vec3 face = towardCamera(at, 0.4D);
        VfxParticles.spawn(face.x, face.y, face.z, 0D, 0D, 0D, 3, 0.35F, 0.15F, Vfx.argb(TINT.glow(), 0.45F),
                Vfx.argb(TINT.outer(), 0F), VfxMode.DOT, 0, 1F, 0F, 0F, 0F);
    }

    /**
     * 飞行方块没等到真方块、原地碎掉的那一下：从方块所在的范围里迸出这种方块的碎块（同原版挖方块的碎屑，带重力），
     * 再加 {@link #onShatter} 的光点；放一声轻的破碎声（每 {@value #LAND_SOUND_GAP} 刻最多一次，关了飞行声不放）。
     *
     * @param scale 方块此刻画的大小（碎块从这么大的范围里出来，数量也按它减）
     */
    static void onBreak(ClientLevel level, Vec3 at, BlockState state, BlockPos cell, float scale, int clock) {
        if (Vfx.visibleAt(at)) {
            RandomSource random = Vfx.random();
            int n = Vfx.count(BREAK_FRAGMENTS * Math.max(0.4F, scale), at);
            double half = 0.4D * scale;
            for (int i = 0; i < n; i++) {
                Vec3 dir = VfxPlasma.randomDir(random);
                double speed = 0.08D + random.nextDouble() * 0.08D;
                TerrainParticle particle = new TerrainParticle(level, at.x + dir.x * half, at.y + dir.y * half, at.z + dir.z * half,
                        dir.x * speed, Math.abs(dir.y) * speed + 0.06D, dir.z * speed, state, cell);
                Minecraft.getInstance().particleEngine.add(particle.scale(0.6F + 0.4F * scale));
            }
        }
        onShatter(at);
        if (BuilderClientConfig.flightSounds() && clock - lastBreakSound >= LAND_SOUND_GAP) {
            lastBreakSound = clock;
            SoundType sound = state.getSoundType();
            level.playLocalSound(at.x, at.y, at.z, sound.getBreakSound(), SoundSource.BLOCKS, 0.5F,
                    sound.getPitch() * (1.1F + Vfx.random().nextFloat() * 0.2F), false);
        }
    }

    /** 往镜头方向挪 d 格（光点画在方块中心会被方块本身挡住） */
    static Vec3 towardCamera(Vec3 center, double d) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 to = camera.subtract(center);
        double len = to.length();
        return len < 1.0E-4D ? center : center.add(to.scale(Math.min(d, len * 0.5D) / len));
    }

    /** 换世界、退出时清掉限频记录 */
    static void reset() {
        LAST_LAUNCH_FX.clear();
        LAST_RETURN_FX.clear();
        lastLandSound = Integer.MIN_VALUE / 2;
        lastBreakSound = Integer.MIN_VALUE / 2;
    }
}
