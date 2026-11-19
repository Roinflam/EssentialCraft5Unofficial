package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.item.OrbitalRemoteItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 轨道打击遥控器的预览光柱（只画给拿着遥控器的本人）：落点上一道细的等离子光柱、地上一团柔光。
 * 落点每帧按玩家这一刻的位置和朝向找（{@link OrbitalRemoteItem#aimFor}，同右键时服务端用的那个函数，不插值），
 * 准星对着能锁定的生物时画在它脚下、按 partialTick 跟着走（{@link OrbitalRemoteItem#lockTargetFor}）。
 * 主手或副手拿着遥控器时由 {@link WeaponVfxClient} 放进特效列表，放下就淡掉移除。
 */
final class OrbitalPreviewFx implements VfxEffect {

    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    private static final double HEIGHT = 96D;
    private static OrbitalPreviewFx current;

    private final int seed = Vfx.random().nextInt(100000);
    private float shown;
    private boolean holding = true;
    private Vec3 last;
    private long seen;

    private OrbitalPreviewFx() {
    }

    /** 每刻调：拿着遥控器就保证有一个在画 */
    static void update(LocalPlayer player) {
        boolean hold = !player.isSpectator() && (player.getMainHandItem().getItem() instanceof OrbitalRemoteItem
                || player.getOffhandItem().getItem() instanceof OrbitalRemoteItem);
        long now = player.level().getGameTime();
        if (current != null && now - current.seen > 3L) {
            current = null;
        }
        if (hold && current == null) {
            current = new OrbitalPreviewFx();
            current.seen = now;
            VfxEffects.add(current);
        }
        if (current != null) {
            current.holding = hold;
        }
    }

    @Override
    public boolean tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            seen = mc.level.getGameTime();
        }
        shown = Mth.clamp(shown + (holding ? 0.25F : -0.34F), 0F, 1F);
        if (!holding && shown <= 0F) {
            if (current == this) {
                current = null;
            }
            return false;
        }
        return true;
    }

    @Override
    public void render(VfxDraw draw) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        long t0 = WeaponVfxStats.start();
        Vec3 foot = aim(player, draw.partialTick());
        if (foot != null) {
            last = foot;
        }
        if (last != null) {
            float time = draw.time();
            float a = shown * (0.85F + 0.15F * Mth.sin(time * 0.6F));
            VfxPlasma.beam(draw, last, last.add(0D, HEIGHT, 0D), 0.17F, 0.7F * a, true, seed, TINT);
            draw.flat(last.add(0D, 0.03D, 0D), 1.1F, time * 0.03F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.35F * a));
            draw.billboard(last.add(0D, 0.12D, 0D), 0.28F, time * 0.04F, VfxMode.FLARE, 900, Vfx.argb(TINT.core(), 0.6F * a));
            // 一颗光点顺着光柱慢慢往下滑，看得出这是「从天上来」的
            float f = 1F - ((time * 0.02F) % 1F);
            Vec3 bead = last.add(0D, 0.5D + 24D * f, 0D);
            draw.billboard(bead, 0.16F, 0F, VfxMode.DOT, 600, Vfx.argb(TINT.core(), 0.7F * a * Math.min(1F, f * 5F)));
        }
        WeaponVfxStats.stop(t0);
    }

    private static Vec3 aim(LocalPlayer player, float partialTick) {
        LivingEntity lock = OrbitalRemoteItem.lockTargetFor(player);
        if (lock != null) {
            return lock.getPosition(partialTick);
        }
        BlockHitResult hit = OrbitalRemoteItem.aimFor(player);
        if (hit == null) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D);
    }

    @Override
    public AABB bounds() {
        Vec3 c = last != null ? last : Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return new AABB(c, c.add(0D, HEIGHT, 0D)).inflate(2D);
    }
}
