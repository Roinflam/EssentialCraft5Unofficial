package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.HoannaClientConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaVisuals;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * 海纳尔天灾的画面、粒子与环境音（第六批 6a M3，batch6a.md §3.4；游戏总线、只在客户端）。事件状态、淡入值读 F6a 的 {@link HoannaClientEvents}，
 * 数值全在 {@link HoannaVisuals}。
 * <ul>
 *   <li><b>雾色</b>（{@code ViewportEvent.ComputeFogColor}）：{@link HoannaVisuals#tint}，和天穹同一个函数（地平线无缝）。只在维度效果是 {@link HoannaEffects} 时；
 *       镜头在水里只做黑暗那一项，在岩浆 / 细雪里、镜头实体有失明 / 黑暗效果时不动（原版此时把雾色压黑，往日阵 / 毒雾色混会把黑雾提亮）。
 *       只改颜色，平时不改雾距离（和别的模组改雾距离叠加不冲突）。</li>
 *   <li><b>毒雾近处拉近地形雾</b>（{@code ViewportEvent.RenderFog}，FOG_TERRAIN、镜头不在液体里、附近强度 p > 0.01）：终点、起点随 p 连续拉近
 *       （{@link HoannaVisuals#fumeFogEnd} / {@link HoannaVisuals#fumeFogStart}），<b>只拉近、不推远</b>：原来的终点 / 起点更近就保留，镜头实体有失明 / 黑暗效果时整个不管
 *       （6a 审查 M3-4：以前会把失明的 5 格、黑暗的 15 格推到 24 格）。尽力而为：终点不是有限的小数（别的模组把地形雾关了、比如装了远景模组）就不动，
 *       装了关雾模组时只剩雾团和雾色。</li>
 *   <li><b>地震镜头晃动</b>（{@code ViewportEvent.ComputeCameraAngles}）：只加 roll / pitch 偏移、不改真实朝向和准星；{@link HoannaVisuals#shake}
 *       乘地震淡入值 × 原版「屏幕扭曲效果」× 客户端配置 {@code camera_shake}；创造 / 旁观不晃。强震由服务端决定（{@code S2CHoannaJoltPacket}），
 *       收到时另放 {@code sound.deep_noise}（AMBIENT、只给本地，音量 0.5~0.7）。</li>
 *   <li><b>日阵余烬</b>：白天、淡入值 > 0.2、粒子设置不是「最少」时每 tick 1 个（「少量」每 3 tick 1 个），镜头 20 格内、镜头上方 6~14 格、该列头顶露天，
 *       原版 {@code DustColorTransitionOptions}（不注册新粒子）；按粒子最长寿命记账，同时 ≤ {@link HoannaVisuals#MAX_EMBERS}。</li>
 *   <li><b>地震落尘</b>：镜头附近一列 6 格内头顶有结实的方块就在它底面放它的 {@code FALLING_DUST}，否则地面上放一粒 {@code BLOCK} 碎屑；每 tick 至多 1 个、
 *       概率 = 淡入值，同时 ≤ {@link HoannaVisuals#MAX_DUST}。</li>
 *   <li><b>黑暗环境音</b>：洞穴音每 tick 0.25%（原作 1%）、远处怪物音每 tick 0.1%，都乘黑暗淡入值、都是 AMBIENT 类别（原作怪物音是 HOSTILE，会让人以为真有怪）。</li>
 *   <li><b>远空电弧排期</b>：{@link HoannaVisuals.ArcLimiter}（全局 ≥ 240 tick 一道，隐藏天空闪烁效果时 ≥ 480），出现时交给 {@link HoannaSkyRenderer#spawnArc}。</li>
 * </ul>
 * 展示厅：{@link #setShowroomShakeTime}（镜头停在一次满强度强震之后第 t 秒，0.115 秒左右是峰值）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class HoannaEventVisuals {

    /** 余烬、落尘的最长寿命（tick，按原版粒子的算法取上界）：DustParticleBase ≤ 40 × 1.2，FallingDustParticle ≤ 160 × 0.9、TerrainParticle ≤ 40 */
    private static final int EMBER_MAX_LIFE = 48;
    private static final int DUST_MAX_LIFE = 160;
    private static final float EMBER_RADIUS = 20F;
    private static final float DUST_RADIUS = 4F;
    private static final int DUST_CEILING = 6;
    private static final float EMBER_MIN_DAYLIGHT = 0.3F;
    /** 远处怪物音（原作 PlayerTickHandler L250 的 9 种，音高按原作 0.01 被引擎夹到 0.5） */
    private static final SoundEvent[] MONSTER_SOUNDS = {SoundEvents.ZOMBIE_DEATH, SoundEvents.ZOMBIE_AMBIENT, SoundEvents.BLAZE_DEATH,
            SoundEvents.SKELETON_STEP, SoundEvents.ENDERMAN_STARE, SoundEvents.SPIDER_STEP, SoundEvents.SPIDER_DEATH, SoundEvents.SPIDER_AMBIENT,
            SoundEvents.CREEPER_DEATH};

    private static final HoannaVisuals.ArcLimiter ARC_LIMITER = new HoannaVisuals.ArcLimiter();
    private static final HoannaVisuals.JoltTrack JOLTS = new HoannaVisuals.JoltTrack();
    private static final HoannaVisuals.JoltTrack SHOWROOM_JOLTS = new HoannaVisuals.JoltTrack();
    private static final float[] SHAKE = new float[2];
    private static final float[] TINT = new float[3];
    private static final DustColorTransitionOptions EMBER = new DustColorTransitionOptions(color(HoannaVisuals.EMBER_FROM),
            color(HoannaVisuals.EMBER_TO), HoannaVisuals.EMBER_SCALE);
    /** 每个槽位记一个活着的粒子的到期刻（客户端刻数）；找不到到期的槽位就不放 */
    private static final int[] EMBER_SLOTS = new int[HoannaVisuals.MAX_EMBERS];
    private static final int[] DUST_SLOTS = new int[HoannaVisuals.MAX_DUST];
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    /** 客户端刻数（暂停时不走） */
    private static int ticks;
    private static boolean wasInHoanna;
    /** 展示厅：镜头停在「一次满强度强震之后第几秒」；NaN = 不用 */
    private static float showroomShake = Float.NaN;

    static {
        SHOWROOM_JOLTS.add(0D, 1F);
    }

    private HoannaEventVisuals() {
    }

    private static Vector3f color(int rgb) {
        return new Vector3f(HoannaVisuals.red(rgb), HoannaVisuals.green(rgb), HoannaVisuals.blue(rgb));
    }

    // ==================================================================
    // 每刻：强震、电弧、余烬、落尘、环境音
    // ==================================================================

    @SubscribeEvent
    public static void onHoannaVisualsClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused()) {
            return;
        }
        ticks++;
        if (!HoannaClientEvents.inHoanna()) {
            if (wasInHoanna) {
                leaveHoanna();
            }
            return;
        }
        wasInHoanna = true;
        RandomSource random = level.random;
        for (HoannaClientEvents.Jolt jolt : HoannaClientEvents.consumeJolts()) {
            JOLTS.add(jolt.gameTime() / 20D, jolt.strength());
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), ECHoanna.DEEP_NOISE.get(), SoundSource.AMBIENT,
                    HoannaVisuals.joltVolume(jolt.strength()), 1F, false);
        }
        float darkness = HoannaClientEvents.fade(HoannaEvent.DARKNESS, 0F);
        float timeOfDay = level.getTimeOfDay(0F);
        if (HoannaClientConfig.skyExtras()) {
            boolean reduced = minecraft.options.hideLightningFlash().get();
            if (ARC_LIMITER.tryFire(level.getGameTime(), HoannaVisuals.night(timeOfDay), darkness, reduced, random)) {
                HoannaSkyRenderer.spawnArc(level.getGameTime());
            }
        }
        ParticleStatus particles = minecraft.options.particles().get();
        boolean particleTick = particles == ParticleStatus.ALL || (particles == ParticleStatus.DECREASED && ticks % 3 == 0);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float sunArray = HoannaClientEvents.fade(HoannaEvent.SUN_ARRAY, 0F);
        if (particleTick && sunArray > HoannaVisuals.EMBER_MIN_FADE && HoannaVisuals.daylight(timeOfDay) > EMBER_MIN_DAYLIGHT) {
            spawnEmber(level, camera, random);
        }
        float quake = HoannaClientEvents.fade(HoannaEvent.EARTHQUAKE, 0F);
        if (particleTick && quake > 0F && random.nextFloat() < quake) {
            spawnDust(level, camera, random);
        }
        if (darkness > 0F) {
            darknessSounds(level, player, random, darkness);
        }
    }

    private static void leaveHoanna() {
        wasInHoanna = false;
        ARC_LIMITER.reset();
        JOLTS.clear();
        HoannaSkyRenderer.resetArc();
    }

    /** 余烬：镜头 20 格内、上方 6~14 格、那一列头顶露天 */
    private static void spawnEmber(ClientLevel level, Vec3 camera, RandomSource random) {
        int slot = freeSlot(EMBER_SLOTS);
        if (slot < 0) {
            return;
        }
        double x = camera.x + (random.nextDouble() * 2D - 1D) * EMBER_RADIUS;
        double z = camera.z + (random.nextDouble() * 2D - 1D) * EMBER_RADIUS;
        double y = camera.y + 6D + random.nextDouble() * 8D;
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > Mth.floor(y)) {
            return;
        }
        EMBER_SLOTS[slot] = ticks + EMBER_MAX_LIFE;
        level.addParticle(EMBER, x, y, z, (random.nextDouble() * 2D - 1D) * 0.02D, -0.05D, (random.nextDouble() * 2D - 1D) * 0.02D);
    }

    /** 地震落尘：随机一列，头顶 6 格内有结实的方块就从它底面落尘，否则地面上崩一粒碎屑 */
    private static void spawnDust(ClientLevel level, Vec3 camera, RandomSource random) {
        int slot = freeSlot(DUST_SLOTS);
        if (slot < 0) {
            return;
        }
        double x = camera.x + (random.nextDouble() * 2D - 1D) * DUST_RADIUS;
        double z = camera.z + (random.nextDouble() * 2D - 1D) * DUST_RADIUS;
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int eye = Mth.floor(camera.y);
        for (int dy = 1; dy <= DUST_CEILING; dy++) {
            POS.set(bx, eye + dy, bz);
            BlockState state = level.getBlockState(POS);
            if (!state.isAir() && state.getRenderShape() != RenderShape.INVISIBLE && state.isFaceSturdy(level, POS, Direction.DOWN)) {
                DUST_SLOTS[slot] = ticks + DUST_MAX_LIFE;
                level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), x, eye + dy - 0.05D, z, 0D, 0D, 0D);
                return;
            }
        }
        for (int dy = 0; dy >= -3; dy--) {
            POS.set(bx, eye - 1 + dy, bz);
            BlockState state = level.getBlockState(POS);
            if (!state.isAir() && state.getRenderShape() != RenderShape.INVISIBLE && state.isFaceSturdy(level, POS, Direction.UP)) {
                DUST_SLOTS[slot] = ticks + DUST_MAX_LIFE;
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, POS.getY() + 1.02D, z,
                        0D, 0.05D + random.nextDouble() * 0.1D, 0D);
                return;
            }
        }
    }

    /** 找一个已经到期的槽位（没有就返回 −1：同时活着的粒子到上限了） */
    private static int freeSlot(int[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] - ticks <= 0) {
                return i;
            }
        }
        return -1;
    }

    /** 黑暗：洞穴音、远处怪物音（都是 AMBIENT，只给本地玩家） */
    private static void darknessSounds(ClientLevel level, LocalPlayer player, RandomSource random, float darkness) {
        if (random.nextFloat() < HoannaVisuals.DARK_CAVE_CHANCE * darkness) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT,
                    1F, 0.5F + random.nextFloat(), false);
        }
        if (random.nextFloat() < HoannaVisuals.DARK_MONSTER_CHANCE * darkness) {
            SoundEvent sound = MONSTER_SOUNDS[random.nextInt(MONSTER_SOUNDS.length)];
            level.playLocalSound(player.getX() + (random.nextDouble() * 2D - 1D) * 16D, player.getY(), player.getZ() + (random.nextDouble() * 2D - 1D) * 16D,
                    sound, SoundSource.AMBIENT, 1F, 0.5F, false);
        }
    }

    // ==================================================================
    // 每帧：雾色、毒雾拉近、镜头晃动
    // ==================================================================

    @SubscribeEvent
    public static void onHoannaFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !(level.effects() instanceof HoannaEffects)) {
            return;
        }
        FogType fluid = event.getCamera().getFluidInCamera();
        if (fluid == FogType.LAVA || fluid == FogType.POWDER_SNOW || HoannaSkyRenderer.effectBlocksSky(event.getCamera().getEntity())) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        float darkness = HoannaClientEvents.fade(HoannaEvent.DARKNESS, partialTick);
        float sunArray = 0F;
        float quake = 0F;
        float fume = 0F;
        float storm = 0F;
        if (fluid == FogType.NONE) {
            sunArray = HoannaClientEvents.fade(HoannaEvent.SUN_ARRAY, partialTick);
            quake = HoannaClientEvents.fade(HoannaEvent.EARTHQUAKE, partialTick);
            fume = FumeHazeRenderer.nearby(partialTick);
            storm = HoannaClientEvents.fade(HoannaEvent.LEY_STORM, partialTick);
        }
        if (darkness <= 0F && sunArray <= 0F && quake <= 0F && fume <= 0F && storm <= 0F) {
            return;
        }
        HoannaVisuals.tint(event.getRed(), event.getGreen(), event.getBlue(), darkness, sunArray,
                HoannaVisuals.daylight(level.getTimeOfDay(partialTick)), quake, fume, storm, TINT);
        event.setRed(TINT[0]);
        event.setGreen(TINT[1]);
        event.setBlue(TINT[2]);
    }

    @SubscribeEvent
    public static void onHoannaRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !(level.effects() instanceof HoannaEffects) || HoannaSkyRenderer.effectBlocksSky(event.getCamera().getEntity())) {
            return;
        }
        float p = FumeHazeRenderer.nearby((float) event.getPartialTick());
        float far = event.getFarPlaneDistance();
        if (p <= HoannaVisuals.FUME_FOG_THRESHOLD || !Float.isFinite(far) || far > 4096F) {
            return;
        }
        float near = event.getNearPlaneDistance();
        // 只拉近：fumeFogEnd 本身已经 ≤ 原终点，这里再夹一次，起点同理（别的模组设的更近的雾保留）
        float end = Math.min(far, HoannaVisuals.fumeFogEnd(p, far));
        float start = Math.min(near, HoannaVisuals.fumeFogStart(p, near, end));
        if (end >= far && start >= near) {
            return;
        }
        event.setFarPlaneDistance(end);
        event.setNearPlaneDistance(start);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHoannaCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        boolean showroom = !Float.isNaN(showroomShake);
        float quake = showroom ? 1F : HoannaClientEvents.fade(HoannaEvent.EARTHQUAKE, partialTick);
        if (quake <= 0F) {
            return;
        }
        LocalPlayer player = minecraft.player;
        boolean exempt = !showroom && (player == null || player.isCreative() || player.isSpectator());
        double seconds = showroom ? showroomShake : (level.getGameTime() + (double) partialTick) / 20D;
        float screen = minecraft.options.screenEffectScale().get().floatValue();
        HoannaVisuals.shake(seconds, quake, showroom ? SHOWROOM_JOLTS : JOLTS, screen, HoannaClientConfig.cameraShake(), exempt, SHAKE);
        if (SHAKE[0] != 0F || SHAKE[1] != 0F) {
            event.setRoll(event.getRoll() + SHAKE[0]);
            event.setPitch(event.getPitch() + SHAKE[1]);
        }
    }

    // ==================================================================
    // 展示厅、清理
    // ==================================================================

    /**
     * 展示厅：镜头停在「一次满强度强震之后第 {@code secondsIntoJolt} 秒」（持续晃动同一时刻、地震强度按 1 算、创造 / 旁观也晃）；
     * 约 0.115 秒是冲击峰值。传 NaN 或调 {@link #clearShowroomShake} 撤掉。仍乘「屏幕扭曲效果」和 camera_shake。
     */
    public static void setShowroomShakeTime(float secondsIntoJolt) {
        showroomShake = secondsIntoJolt;
    }

    public static void clearShowroomShake() {
        showroomShake = Float.NaN;
    }

    @SubscribeEvent
    public static void onHoannaVisualsLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        leaveHoanna();
        clearShowroomShake();
        Arrays.fill(EMBER_SLOTS, 0);
        Arrays.fill(DUST_SLOTS, 0);
        HoannaSkyRenderer.release();
    }
}
