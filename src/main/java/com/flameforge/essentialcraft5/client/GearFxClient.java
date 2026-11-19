package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.client.vfx.gear.GearVfx;
import com.flameforge.essentialcraft5.item.attunement.AttunementLook;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * 装备成长特效事件的客户端分发（{@link S2CGearFxPacket}）：按 {@link GearFx} 种类交给登记的 {@link Renderer}。
 * <p>
 * <b>特效第 3 批（武器）、第 5 批（装备）的接口就是这里</b>：在自己的客户端类里对要换的种类 {@link #register} 一个正式画法，替掉这里的占位，服务端不用动。
 * <p>
 * 这一批的占位已经用 {@code client/vfx} 的等离子部件拼（爆点、冲击波、电晕、火花、余辉），不撒原版粒子，配色按那件装备的家族配色
 * （{@link AttunementLook#palette}，从装备贴图取的主色）或 MRU 紫：裁决之刃那几种用裁决之刃的品红紫，枪用 MRU 枪的玫粉，
 * 余烬剑的元素爆发按元素在源质色系里取（火偏玫红、水 MRU 蓝紫、地深紫、风薰衣草）。占位不加冲击帧和速度线（那两样只留给大招）。
 * 音效由服务端放。
 */
public final class GearFxClient {

    /** 一种事件的画法（客户端主线程调；entity 是包里的实体，客户端看不见时为 null） */
    @FunctionalInterface
    public interface Renderer {
        void play(ClientLevel level, @Nullable Entity entity, Vec3 pos, float param);
    }

    private static final Map<GearFx, Renderer> RENDERERS = new EnumMap<>(GearFx.class);

    /** 元素爆发的四种颜色（0 火 1 水 2 地 3 风）：都落在源质色系里，火往矩阵品红那边偏 */
    private static final int[] ELEMENT_RGB = {0xFF5A8C, VfxPalette.ORB_BLUE, VfxPalette.DEEP_VIOLET, VfxPalette.LAVENDER};

    static {
        register(GearFx.ASCEND, (level, entity, pos, param) -> {
            Vec3 at = pos.add(0D, 1D, 0D);
            VfxPlasma.embers(at, 0.8D, 18F, VfxPlasma.MRU);
            VfxEffects.add(new Pop(at, 1.4F, 0.9F, VfxPlasma.MRU, 14));
        });
        register(GearFx.SCAR_UP, (level, entity, pos, param) -> {
            Vec3 at = entity == null ? pos : entity.position().add(0D, entity.getBbHeight() * 0.6D, 0D);
            VfxPlasma.embers(at, 0.5D, 10F, VfxPlasma.MRU);
            VfxEffects.add(new Pop(at, 0.9F, 0.6F, VfxPlasma.MRU, 10));
        });
        register(GearFx.EXECUTE, (level, entity, pos, param) -> {
            VfxPlasma.Tint tint = familyTint(Family.VERDICT);
            VfxPlasma.sparks(pos, Vec3.ZERO, 1D, 10F, 0.15D, 0.35D, 5, 10, tint);
            VfxEffects.add(new Pop(pos, 0.8F, 0F, tint, 8));
        });
        register(GearFx.LIFESTEAL, (level, entity, pos, param) -> VfxPlasma.embers(pos, 0.3D, 4F, familyTint(Family.VERDICT)));
        register(GearFx.ARMOR_PEN, (level, entity, pos, param) -> VfxPlasma.sparks(pos, Vec3.ZERO, 1D, 5F, 0.1D, 0.25D, 4, 8, VfxPlasma.MRU));
        register(GearFx.VERDICT_SEAL, (level, entity, pos, param) -> {
            VfxPlasma.Tint tint = familyTint(Family.VERDICT);
            VfxPlasma.embers(pos, 0.3D, 6F, tint);
            VfxEffects.add(new Pop(pos, 0.6F, 0F, tint, 10));
        });
        register(GearFx.VERDICT_BURST, (level, entity, pos, param) -> {
            VfxPlasma.Tint tint = familyTint(Family.VERDICT);
            VfxPlasma.sparks(pos, Vec3.ZERO, 1D, 16F, 0.2D, 0.5D, 6, 14, tint);
            VfxEffects.add(new Pop(pos, 1.5F, 1.2F, tint, 12));
        });
        register(GearFx.ELEMENT_BURST, (level, entity, pos, param) -> {
            int element = Mth.clamp((int) param, 0, ELEMENT_RGB.length - 1);
            VfxPlasma.Tint tint = VfxPlasma.tint(ELEMENT_RGB[element]);
            VfxPlasma.embers(pos.add(0D, 0.4D, 0D), 1.2D, 12F, tint);
            VfxEffects.add(new Pop(pos.add(0D, 0.2D, 0D), 1.2F, 3F, tint, 14));
        });
        register(GearFx.OVERLOAD_READY, (level, entity, pos, param) -> {
            Vec3 at = entity == null ? pos : entity.position().add(0D, entity.getBbHeight() * 0.7D, 0D);
            VfxPlasma.sparks(at, Vec3.ZERO, 1D, 10F, 0.08D, 0.2D, 5, 10, familyTint(Family.GUN));
        });
        register(GearFx.OVERLOAD_FIRE, (level, entity, pos, param) -> {
            VfxPlasma.Tint tint = familyTint(Family.GUN);
            VfxPlasma.sparks(pos, Vec3.ZERO, 1D, 18F, 0.2D, 0.6D, 6, 14, tint);
            VfxEffects.add(new Pop(pos, 1.6F, Math.max(1F, param), tint, 14));
        });
        // 盔甲觉醒和装备动作（喷射、闪避、定罪、空中跳、封禁场穹顶）画在 client/vfx/gear/GearVfx
        GearVfx.registerAll();
    }

    private GearFxClient() {
    }

    /** 登记 / 换掉一种事件的画法（客户端初始化时调） */
    public static synchronized void register(GearFx type, Renderer renderer) {
        RENDERERS.put(type, renderer);
    }

    public static void handle(S2CGearFxPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        GearFx type = packet.kind();
        if (level == null || type == null) {
            return;
        }
        Renderer renderer;
        synchronized (GearFxClient.class) {
            renderer = RENDERERS.get(type);
        }
        if (renderer == null) {
            return;
        }
        Entity entity = packet.entity() >= 0 ? level.getEntity(packet.entity()) : null;
        renderer.play(level, entity, packet.pos(), packet.param());
    }

    private enum Family {
        VERDICT,
        GUN
    }

    /** 这件装备家族贴图主色配的一套等离子层次 */
    private static VfxPlasma.Tint familyTint(Family family) {
        int own = AttunementLook.palette(family == Family.GUN ? ECAttunement.MRU_GUN : ECAttunement.VERDICT_BLADE).own();
        return VfxPlasma.tint(own);
    }

    /**
     * 占位用的一次性命中：爆点（贴着镜头那一侧）+ 一圈电晕，ring 大于 0 时贴地再推一道没有亮边、越远越淡的柔波
     */
    private static final class Pop implements VfxEffect {

        private final Vec3 at;
        private final float size;
        private final float ring;
        private final VfxPlasma.Tint tint;
        private final int life;
        private final int seed = RandomSource.create().nextInt(1024);
        private int age;

        Pop(Vec3 at, float size, float ring, VfxPlasma.Tint tint, int life) {
            this.at = at;
            this.size = size;
            this.ring = ring;
            this.tint = tint;
            this.life = life;
        }

        @Override
        public boolean tick() {
            return ++age < life;
        }

        @Override
        public void render(VfxDraw draw) {
            float t = age + draw.partialTick();
            Vec3 p = draw.towardCamera(at, 0.4D);
            if (t < 1F) {
                draw.billboard(p, size, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.core(), 0.8F));
            }
            VfxPlasma.burst(draw, p, size, t, life * 0.7F, seed, tint);
            float k = 1F - Mth.clamp(t / (life * 0.6F), 0F, 1F);
            if (k > 0F && Vfx.tier() >= 1) {
                VfxPlasma.corona(draw, RandomSource.create(seed + (age >> 1) * 31L), at, size * 0.6F, 3, 0.025F, k, tint);
            }
            if (ring > 0F) {
                // 没有亮边的柔波：只表现「震开了」，地上不留范围的边（tools/plan/vfx_overhaul.md 第 6 节）
                VfxPlasma.wave(draw, at.subtract(0D, at.y - Math.floor(at.y) - 0.05D, 0D), ring, t, life * 0.45F, life, 0.8F, tint);
            }
        }

        @Override
        public AABB bounds() {
            return new AABB(at, at).inflate(Math.max(size, ring) + 1D);
        }
    }
}
