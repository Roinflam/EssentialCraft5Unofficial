package com.flameforge.essentialcraft5.ambience;

import com.flameforge.essentialcraft5.ambience.MachineAmbience.Fx;
import com.flameforge.essentialcraft5.ambience.MachineAmbience.Profile;
import com.flameforge.essentialcraft5.registry.ECMachineSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import org.joml.Vector3f;

/**
 * 各类机器的氛围一览（声音 + 粒子）。方块实体覆盖 {@code ambience()} 返回这里的某一份，
 * 再覆盖 {@code ambientWorking()} 说清楚什么叫「在干活」就行。调音量、频率只改这一个文件。
 * <ul>
 *   <li>{@link #ENRICHMENT} 富集仓控制器：反应堆的低频嗡鸣 + 盖革计数器 + 偶尔的泄压蒸汽声，冷蓝的光点往上飘。</li>
 *   <li>{@link #RADIATING} 辐射仓：高一点的辐射嗡嗡声 + 稀疏的盖革声，黄绿色光点。</li>
 *   <li>{@link #MAGICIAN} 魔力工作台：细碎的魔法微光声 + 偶尔的风铃，紫色和白色的光点。</li>
 *   <li>{@link #LEY} 地脉凝聚器：地下深处的低沉隆隆声 + 水晶生长的叮叮声。</li>
 *   <li>{@link #CONVERTER} MRU 转换器：电流嗡鸣 + 噼啪火花，电火花粒子。</li>
 *   <li>{@link #ENCHANTER} 魔力附魔台、{@link #AMPLIFIER} 附魔增幅台、{@link #ASCENDER} 附魔升华台：旋转的气流声 + 风铃，附魔粒子汇向书本。</li>
 * </ul>
 * 第二批（原作本来就有粒子的机器只补声音，不重复冒粒子）：
 * <ul>
 *   <li>{@link #MITHRILINE} 米斯莱熔炉、{@link #CRYSTAL_FORMER} 水晶成型器：水晶共鸣 + 叮叮声。</li>
 *   <li>{@link #CRYSTAL_EXTRACTOR} 水晶提取器：低沉的研磨声 + 低一点的叮叮声。</li>
 *   <li>{@link #MAGICAL_FURNACE} 魔力熔炉：炉火声 + 原版熔炉的噼啪声，顶上冒火苗和烟。</li>
 *   <li>{@link #MAGMATIC_FURNACE} 岩浆熔炉（四档）：炉火声 + 噼啪声。{@link #MAGMATIC_SMELTERY} 岩浆冶炼炉：岩浆冒泡 + 原版岩浆的啵声，偶尔溅出岩浆滴。</li>
 *   <li>{@link #EMBER_FORGE} 余烬锻炉：锻造时的炉火声（仪式本身的火焰和音效照旧）。</li>
 *   <li>{@link #REPAIRER} 魔力修复器：细碎的魔法微光声 + 偶尔一下锻造台的敲击。</li>
 *   <li>{@link #POTION_SPREADER} 药水扩散器：装着药水时偶尔咕嘟一下。</li>
 *   <li>{@link #CHARGER} 充能仓：电流嗡鸣 + 噼啪火花，电火花粒子。</li>
 *   <li>{@link #COLD_DISTILLATOR} 冷凝器：霜气嘶声，雪花和冷蓝光点。</li>
 *   <li>{@link #MATRIX_ABSORBER} 矩阵换能器：虚空吸引的低鸣（粒子是服务端发的那几个，不另加）。</li>
 *   <li>{@link #SUN_RAY} 日光吸收器：只冒金色光点（产能器到处都是，不加持续的声音）。{@link #MOON_WELL} 月亮井：银白光点慢慢升起，偶尔一声很轻的风铃。</li>
 * </ul>
 */
public final class MachineAmbiences {

    private MachineAmbiences() {
    }

    public static final Profile ENRICHMENT = Profile.builder()
            .loop(ECMachineSounds.REACTOR_HUM, 0.55F)
            .accent(ECMachineSounds.GEIGER, 7.0F, 0.45F, 0.9F, 1.15F)
            .accent(ECMachineSounds.REACTOR_VENT, 0.05F, 0.7F, 0.95F, 1.05F)
            .spark(4.0F, 1.2D, dust(0.45F, 0.85F, 1.0F, 0.8F, 0.02D, 0.5D))
            .spark(1.5F, 1.1D, plain(ParticleTypes.ELECTRIC_SPARK, 0.6D, 0.0D))
            .build();

    public static final Profile RADIATING = Profile.builder()
            .loop(ECMachineSounds.RADIATION_HUM, 0.4F)
            .accent(ECMachineSounds.GEIGER, 2.5F, 0.35F, 0.9F, 1.1F)
            .spark(2.5F, 1.1D, dust(0.7F, 1.0F, 0.3F, 0.7F, 0.015D, 0.5D))
            .build();

    public static final Profile MAGICIAN = Profile.builder()
            .loop(ECMachineSounds.ARCANE_SHIMMER, 0.35F)
            .accent(ECMachineSounds.ARCANE_CHIME, 0.5F, 0.4F, 0.9F, 1.2F)
            .spark(3.0F, 1.1D, plain(ParticleTypes.WITCH, 0.6D, 0.02D))
            .spark(1.0F, 1.1D, plain(ParticleTypes.END_ROD, 0.5D, 0.01D))
            .build();

    public static final Profile LEY = Profile.builder()
            .loop(ECMachineSounds.EARTH_RUMBLE, 0.5F)
            .accent(ECMachineSounds.CRYSTAL_TINK, 0.5F, 0.3F, 0.8F, 1.3F)
            .build();

    public static final Profile CONVERTER = Profile.builder()
            .loop(ECMachineSounds.ELECTRIC_HUM, 0.35F)
            .accent(ECMachineSounds.SPARK_CRACKLE, 1.2F, 0.4F, 0.9F, 1.15F)
            .spark(2.0F, 1.05D, plain(ParticleTypes.ELECTRIC_SPARK, 0.7D, 0.0D))
            .build();

    public static final Profile ENCHANTER = Profile.builder()
            .loop(ECMachineSounds.ENCHANT_SWIRL, 0.3F)
            .accent(ECMachineSounds.ARCANE_CHIME, 0.25F, 0.25F, 1.0F, 1.3F)
            .spark(5.0F, 1.0D, enchantConverge(1.2D))
            .build();

    public static final Profile AMPLIFIER = Profile.builder()
            .loop(ECMachineSounds.ENCHANT_SWIRL, 0.32F)
            .accent(ECMachineSounds.ARCANE_CHIME, 0.3F, 0.28F, 1.0F, 1.3F)
            .spark(6.0F, 1.0D, enchantConverge(1.3D))
            .build();

    public static final Profile ASCENDER = Profile.builder()
            .loop(ECMachineSounds.ENCHANT_SWIRL, 0.4F)
            .accent(ECMachineSounds.CRYSTAL_TINK, 0.8F, 0.3F, 0.7F, 1.0F)
            .accent(ECMachineSounds.ARCANE_CHIME, 0.2F, 0.3F, 0.7F, 0.9F)
            .spark(8.0F, 1.0D, enchantConverge(1.6D))
            .spark(2.0F, 0.9D, plain(ParticleTypes.END_ROD, 0.35D, 0.04D))
            .build();

    // ---------------- 第二批 ----------------

    public static final Profile MITHRILINE = Profile.builder()
            .loop(ECMachineSounds.CRYSTAL_RESONANCE, 0.3F)
            .accent(ECMachineSounds.CRYSTAL_TINK, 0.6F, 0.3F, 0.9F, 1.2F)
            .build();

    public static final Profile CRYSTAL_FORMER = Profile.builder()
            .loop(ECMachineSounds.CRYSTAL_RESONANCE, 0.25F)
            .accent(ECMachineSounds.CRYSTAL_TINK, 0.9F, 0.3F, 1.0F, 1.3F)
            .build();

    public static final Profile CRYSTAL_EXTRACTOR = Profile.builder()
            .loop(ECMachineSounds.EARTH_RUMBLE, 0.3F)
            .accent(ECMachineSounds.CRYSTAL_TINK, 0.5F, 0.25F, 0.6F, 0.85F)
            .build();

    public static final Profile MAGICAL_FURNACE = Profile.builder()
            .loop(ECMachineSounds.FIRE_ROAR, 0.35F)
            .accent(() -> SoundEvents.FURNACE_FIRE_CRACKLE, 0.6F, 0.5F, 0.9F, 1.1F)
            .spark(3.0F, 1.05D, plain(ParticleTypes.FLAME, 0.5D, 0.01D))
            .spark(1.5F, 1.1D, plain(ParticleTypes.SMOKE, 0.4D, 0.03D))
            .build();

    public static final Profile MAGMATIC_FURNACE = Profile.builder()
            .loop(ECMachineSounds.FIRE_ROAR, 0.3F)
            .accent(() -> SoundEvents.FURNACE_FIRE_CRACKLE, 0.5F, 0.45F, 0.85F, 1.05F)
            .build();

    public static final Profile MAGMATIC_SMELTERY = Profile.builder()
            .loop(ECMachineSounds.MAGMA_BUBBLE, 0.45F)
            .accent(() -> SoundEvents.LAVA_POP, 0.4F, 0.4F, 0.8F, 1.1F)
            .spark(0.3F, 1.0D, plain(ParticleTypes.LAVA, 0.5D, 0.0D))
            .build();

    public static final Profile EMBER_FORGE = Profile.builder()
            .loop(ECMachineSounds.FIRE_ROAR, 0.4F)
            .build();

    public static final Profile REPAIRER = Profile.builder()
            .loop(ECMachineSounds.ARCANE_SHIMMER, 0.25F)
            .accent(() -> SoundEvents.SMITHING_TABLE_USE, 0.15F, 0.15F, 1.1F, 1.3F)
            .build();

    public static final Profile POTION_SPREADER = Profile.builder()
            .accent(() -> SoundEvents.BREWING_STAND_BREW, 0.12F, 0.25F, 0.9F, 1.1F)
            .accent(() -> SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.4F, 0.3F, 0.8F, 1.2F)
            .build();

    public static final Profile CHARGER = Profile.builder()
            .loop(ECMachineSounds.ELECTRIC_HUM, 0.3F)
            .accent(ECMachineSounds.SPARK_CRACKLE, 0.8F, 0.35F, 0.9F, 1.2F)
            .spark(2.0F, 1.0D, plain(ParticleTypes.ELECTRIC_SPARK, 0.8D, 0.0D))
            .build();

    public static final Profile COLD_DISTILLATOR = Profile.builder()
            .loop(ECMachineSounds.FROST_HISS, 0.3F)
            .spark(2.0F, 1.1D, plain(ParticleTypes.SNOWFLAKE, 0.8D, -0.01D))
            .spark(1.5F, 1.0D, dust(0.6F, 0.85F, 1.0F, 0.7F, 0.01D, 0.7D))
            .build();

    public static final Profile MATRIX_ABSORBER = Profile.builder()
            .loop(ECMachineSounds.VOID_DRAW, 0.35F)
            .build();

    public static final Profile SUN_RAY = Profile.builder()
            .spark(2.0F, 1.0D, dust(1.0F, 0.85F, 0.35F, 0.8F, 0.02D, 0.6D))
            .build();

    public static final Profile MOON_WELL = Profile.builder()
            .accent(ECMachineSounds.ARCANE_CHIME, 0.06F, 0.18F, 1.2F, 1.4F)
            .spark(1.0F, 1.0D, plain(ParticleTypes.END_ROD, 0.7D, 0.02D))
            .build();

    /** 带颜色的尘粒，往上慢慢飘。scale 是粒子大小，rise 是每刻上升速度，spread 是水平散开的范围（格） */
    private static Fx dust(float r, float g, float b, float scale, double rise, double spread) {
        DustParticleOptions options = new DustParticleOptions(new Vector3f(r, g, b), scale);
        return (level, x, y, z, random) -> AmbienceParticles.add(level, options,
                x + (random.nextDouble() - 0.5D) * spread, y + (random.nextDouble() - 0.5D) * 0.3D, z + (random.nextDouble() - 0.5D) * spread,
                0.0D, rise, 0.0D);
    }

    /** 原版粒子，在 spread 范围内随机出现，竖直速度 rise */
    private static Fx plain(ParticleOptions type, double spread, double rise) {
        return (level, x, y, z, random) -> AmbienceParticles.add(level, type,
                x + (random.nextDouble() - 0.5D) * spread, y + (random.nextDouble() - 0.5D) * 0.3D, z + (random.nextDouble() - 0.5D) * spread,
                0.0D, rise, 0.0D);
    }

    /**
     * 附魔粒子：原版附魔台的粒子从「出生点 + 速度向量」飞向出生点（速度向量就是起点到终点的偏移），
     * 这里让它们从机器四周 radius 格的范围里汇向机器上方的书。
     */
    private static Fx enchantConverge(double radius) {
        return converge(ParticleTypes.ENCHANT, radius);
    }

    /** 从四周 radius 格汇向基准点的粒子；只适用于原版附魔、传送门这类「速度向量 = 起点偏移」的粒子 */
    private static Fx converge(ParticleOptions type, double radius) {
        return (level, x, y, z, random) -> {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = radius * (0.6D + random.nextDouble() * 0.4D);
            AmbienceParticles.add(level, type, x, y, z,
                    Math.cos(angle) * distance, random.nextDouble() * 0.8D, Math.sin(angle) * distance);
        };
    }
}
