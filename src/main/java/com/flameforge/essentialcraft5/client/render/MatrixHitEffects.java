package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.client.vfx.machine.CoilVfx;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 「矩阵受损」特效（ECGuns，GUNS-4 加的损坏特效）：射线打中玩家、加了矩阵损伤以后，服务端（{@code gun/GunMatrixFx}，已限频）给被打的人和
 * 看得见他的人发 {@code S2CGunMatrixHitPacket}，这里画：
 * <ul>
 *   <li><b>身上</b>（所有人看得见）：{@code 1 + 强度} 道短电弧从身体里窜出来（等离子画法，{@code CoilVfx.shortArc}），
 *       一团从粉白淡到紫罗兰的光点往外散，几颗余辉往上飘；
 *       本地放「MRU 矩阵受损」（{@link ECGuns#MATRIX_DAMAGE}，有字幕）。第一人称的被打者自己看不到身上这一套（镜头在身体里，电弧会糊脸）。</li>
 *   <li><b>被打的人自己的屏幕</b>：边缘泛一圈品红（{@link #renderVignette}，HUD 层，排在霜冻覆盖层之后、快捷栏之前，开狙击镜时也看得见）：
 *       {@value #RISE_TICKS} 刻升起、停 {@value #HOLD_TICKS} 刻、{@value #FALL_TICKS} 刻平滑淡出，最浓不透明度 0.30~0.40（按强度），
 *       从屏幕边缘往里 {@code 短边 × BAND} 线性淡到全透明，中间一大片不碰；两次叠在一起时取两者较浓的，不会先掉到 0 再亮起来。</li>
 * </ul>
 * <b>闪烁安全</b>：服务端同一目标至多 10 刻一次；这里同一个实体两次特效至少隔 {@value #MIN_GAP_TICKS} 刻（网络卡顿时几个包挤在一起也一样），
 * 所以任何位置每秒不超过 3 次（晕影每次都是「升起→淡出」一次亮度变化）；不做白闪、不整屏铺色。
 * 打开了原版「隐藏闪电闪烁」的，晕影只有三分之一浓、升起放慢到 {@value #REDUCED_RISE_TICKS} 刻。
 * 身上的特效另有全局上限：任意 20 刻里至多 {@value #MAX_BURSTS_PER_SECOND} 次（一排人同时挨打时）。
 * <p>
 * 展示厅直接调 {@link #play}（在任意实体身上放一次）和 {@link #flashSelf}（只亮本地晕影）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class MatrixHitEffects {

    /** 同一个实体两次特效至少隔多少刻（每秒不到 3 次） */
    public static final int MIN_GAP_TICKS = 7;
    /** 身上的特效：任意 20 刻里至多几次（全局） */
    public static final int MAX_BURSTS_PER_SECOND = 8;
    static final int RISE_TICKS = 2;
    static final int REDUCED_RISE_TICKS = 5;
    static final int HOLD_TICKS = 1;
    static final int FALL_TICKS = 10;
    /** 晕影最浓的不透明度（强度 1~3） */
    private static final float[] PEAK_ALPHA = {0.30F, 0.35F, 0.40F};
    /** 「隐藏闪电闪烁」打开时晕影浓度的倍数 */
    private static final float REDUCED_FACTOR = 1F / 3F;
    /** 晕影从边缘往里多宽（屏幕短边的比例） */
    private static final float BAND = 0.2F;
    /** 晕影颜色：研究手册的 MRU 品红（CodexTheme.MRU_MAGENTA #F050F0） */
    private static final float VIGNETTE_R = 0xF0 / 255F;
    private static final float VIGNETTE_G = 0x50 / 255F;
    private static final float VIGNETTE_B = 0xF0 / 255F;
    /** HUD 覆盖层的深度（原版暗角、霜冻覆盖层都画在 −90） */
    private static final float OVERLAY_Z = -90F;

    /** 客户端刻数（暂停时不走） */
    private static int ticks;
    private static final Map<Integer, Integer> LAST_BY_ENTITY = new HashMap<>();
    private static final Deque<Integer> RECENT_BURSTS = new ArrayDeque<>();
    /** 这一次和上一次晕影的开始刻、强度（两次叠着时取较浓的） */
    private static int flashStart = Integer.MIN_VALUE / 2;
    private static int flashIntensity = 1;
    private static int previousStart = Integer.MIN_VALUE / 2;
    private static int previousIntensity = 1;

    private MatrixHitEffects() {
    }

    /** 收到包：按实体 id 找到被打的人（找不到就算了） */
    public static void onPacket(int entityId, int intensity) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(entityId);
        if (entity != null) {
            play(entity, intensity);
        }
    }

    /** 在这个实体身上放一次「矩阵受损」（同一个实体 {@value #MIN_GAP_TICKS} 刻内只放一次）；是本地玩家自己时另亮晕影 */
    public static void play(Entity entity, int intensity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(entity.level() instanceof ClientLevel level)) {
            return;
        }
        int strength = Mth.clamp(intensity, 1, 3);
        Integer last = LAST_BY_ENTITY.get(entity.getId());
        if (last != null && ticks - last >= 0 && ticks - last < MIN_GAP_TICKS) {
            return;
        }
        if (LAST_BY_ENTITY.size() > 256) {
            LAST_BY_ENTITY.values().removeIf(at -> ticks - at < 0 || ticks - at >= MIN_GAP_TICKS);
        }
        LAST_BY_ENTITY.put(entity.getId(), ticks);
        LocalPlayer self = minecraft.player;
        boolean isSelf = entity == self;
        if (isSelf) {
            flash(strength);
        }
        boolean insideBody = isSelf && minecraft.options.getCameraType().isFirstPerson() && minecraft.getCameraEntity() == self;
        boolean shown = !insideBody && acquireBurst();
        if (shown) {
            burst(level, entity, strength);
        }
        if (!shown && !isSelf) {
            return;     // 全局上限挤掉的连声音也不放（一排人同时挨打时不刷屏）
        }
        AABB box = entity.getBoundingBox();
        level.playLocalSound(entity.getX(), box.minY + box.getYsize() * 0.6D, entity.getZ(), ECGuns.MATRIX_DAMAGE.get(), SoundSource.PLAYERS,
                isSelf ? 0.7F : 0.5F + 0.1F * strength, 1.2F + level.random.nextFloat() * 0.25F - 0.1F * (strength - 1), false);
    }

    /** 只亮本地晕影（展示厅用；不查间隔） */
    public static void flashSelf(int intensity) {
        flash(Mth.clamp(intensity, 1, 3));
    }

    private static void flash(int intensity) {
        previousStart = flashStart;
        previousIntensity = flashIntensity;
        flashStart = ticks;
        flashIntensity = intensity;
    }

    private static boolean acquireBurst() {
        while (!RECENT_BURSTS.isEmpty() && ticks - RECENT_BURSTS.peekFirst() >= 20) {
            RECENT_BURSTS.pollFirst();
        }
        if (RECENT_BURSTS.size() >= MAX_BURSTS_PER_SECOND) {
            return false;
        }
        RECENT_BURSTS.addLast(ticks);
        return true;
    }

    /** 身上的一套：电弧从身体里往外窜、尘粒子往外散、几颗女巫粒子往上飘 */
    private static void burst(ClientLevel level, Entity entity, int strength) {
        RandomSource random = level.random;
        AABB box = entity.getBoundingBox();
        double width = box.getXsize();
        double height = box.getYsize();
        double depth = box.getZsize();
        Vec3 center = box.getCenter();
        int arcs = 1 + strength;
        for (int i = 0; i < arcs; i++) {
            Vec3 start = new Vec3(center.x + (random.nextDouble() - 0.5D) * width * 0.6D,
                    box.minY + height * (0.3D + random.nextDouble() * 0.55D),
                    center.z + (random.nextDouble() - 0.5D) * depth * 0.6D);
            double angle = random.nextDouble() * Math.PI * 2D;
            double reach = 0.45D + random.nextDouble() * 0.35D + 0.1D * strength;
            Vec3 end = start.add(Math.cos(angle) * reach, (random.nextDouble() - 0.35D) * reach, Math.sin(angle) * reach);
            CoilVfx.shortArc(start, end, (i & 1) == 0 ? 10 : 8);
        }
        // 品红渐变到紫罗兰的光点往外散（原来是同色的尘粒子），几颗紫光慢慢往上飘
        int count = Vfx.count(6 + 4 * strength, center);
        for (int i = 0; i < count; i++) {
            double x = box.minX + random.nextDouble() * width;
            double y = box.minY + random.nextDouble() * height;
            double z = box.minZ + random.nextDouble() * depth;
            VfxParticles.spawn(x, y, z, (x - center.x) * 0.12D, 0.02D + random.nextDouble() * 0.04D, (z - center.z) * 0.12D,
                    10 + random.nextInt(8), 0.07F, 0.02F, Vfx.argb(VfxPalette.PINK, 0.95F), Vfx.argb(VfxPalette.VIOLET, 0F),
                    VfxMode.DOT, 600, 0.9F, 0F, 0F, 0F);
        }
        VfxPlasma.embers(new Vec3(center.x, box.minY + height * 0.5D, center.z), width * 0.5D, 1 + strength, VfxPlasma.MRU);
    }

    // ------------------------------------------------------------------
    // 晕影（HUD）
    // ------------------------------------------------------------------

    /** HUD 层（{@code client/GunsClient} 登记在原版霜冻覆盖层之后） */
    public static void renderVignette(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        boolean reduced = gui.getMinecraft().options.hideLightningFlash().get();
        float alpha = Math.max(vignetteAlpha(ticks - flashStart + partialTick, flashIntensity, reduced),
                vignetteAlpha(ticks - previousStart + partialTick, previousIntensity, reduced));
        if (alpha <= 0.004F) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        float t = Math.min(width, height) * BAND;
        float w = width;
        float h = height;
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        // 四条梯形带（外沿不透明度 alpha、内沿 0），在对角线上接缝；顶点顺序和 GuiGraphics.fill 同一个绕向
        band(buffer, pose, 0F, 0F, alpha, t, t, 0F, w - t, t, 0F, w, 0F, alpha);
        band(buffer, pose, 0F, 0F, alpha, 0F, h, alpha, t, h - t, 0F, t, t, 0F);
        band(buffer, pose, t, h - t, 0F, 0F, h, alpha, w, h, alpha, w - t, h - t, 0F);
        band(buffer, pose, w - t, t, 0F, w - t, h - t, 0F, w, h, alpha, w, 0F, alpha);
        graphics.flush();
    }

    /**
     * 一条梯形带。画在 z = {@value #OVERLAY_Z}（同原版暗角、南瓜头、霜冻覆盖层）：{@code RenderType.gui()} 会写深度，
     * 画在 0 的话之后在 −90 画的快捷栏、血条会被这几块四边形（连全透明的内沿也算）挡掉
     */
    private static void band(VertexConsumer buffer, Matrix4f pose, float x0, float y0, float a0, float x1, float y1, float a1,
                             float x2, float y2, float a2, float x3, float y3, float a3) {
        buffer.vertex(pose, x0, y0, OVERLAY_Z).color(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, a0).endVertex();
        buffer.vertex(pose, x1, y1, OVERLAY_Z).color(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, a1).endVertex();
        buffer.vertex(pose, x2, y2, OVERLAY_Z).color(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, a2).endVertex();
        buffer.vertex(pose, x3, y3, OVERLAY_Z).color(VIGNETTE_R, VIGNETTE_G, VIGNETTE_B, a3).endVertex();
    }

    /**
     * 晕影在开始后 t 刻的不透明度：升起（线性）→ 停住 → 淡出（二次，先快后慢），之外为 0。
     *
     * @param reduced 打开了「隐藏闪电闪烁」：浓度 ×{@code 1/3}、升起放慢
     */
    static float vignetteAlpha(float t, int intensity, boolean reduced) {
        int rise = reduced ? REDUCED_RISE_TICKS : RISE_TICKS;
        if (t < 0F || t >= rise + HOLD_TICKS + FALL_TICKS) {
            return 0F;
        }
        float peak = PEAK_ALPHA[Mth.clamp(intensity, 1, 3) - 1] * (reduced ? REDUCED_FACTOR : 1F);
        if (t < rise) {
            return peak * t / rise;
        }
        if (t < rise + HOLD_TICKS) {
            return peak;
        }
        float left = 1F - (t - rise - HOLD_TICKS) / FALL_TICKS;
        return peak * left * left;
    }

    // ------------------------------------------------------------------
    // 刻、清理
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onMatrixHitClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
            ticks++;
        }
    }

    @SubscribeEvent
    public static void onMatrixHitLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            reset();
        }
    }

    @SubscribeEvent
    public static void onMatrixHitLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        LAST_BY_ENTITY.clear();
        RECENT_BURSTS.clear();
        flashStart = Integer.MIN_VALUE / 2;
        previousStart = Integer.MIN_VALUE / 2;
    }
}
