package com.flameforge.essentialcraft5.worldgen.hoanna.placement;

import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * 放置修饰 {@code essentialcraft5:radiation_hotspot} {min_base}（第十三批 13d-HF，batch13.md §6.1、§6.2「辐射热点」；HS §2.1.5）：
 * 所在区块的辐射底数 {@link Radiation#chunkBase} ≥ min_base 才放。底数只由区块坐标决定（0~5 均匀），所以「最热的区块」在世界里看得见
 * （焦痕、辐晶簇），和辐射的数值对得上。纯函数，每区块一次整数哈希，不看世界。
 * <pre>{"type": "essentialcraft5:radiation_hotspot", "min_base": 4}</pre>
 */
public final class RadiationHotspot extends PlacementFilter {

    public static final Codec<RadiationHotspot> CODEC = Codec.intRange(0, 64).fieldOf("min_base")
            .xmap(RadiationHotspot::new, RadiationHotspot::minBase).codec();

    private final int minBase;

    public RadiationHotspot(int minBase) {
        this.minBase = minBase;
    }

    public int minBase() {
        return minBase;
    }

    /** 这个区块的底数够不够 */
    public boolean test(int chunkX, int chunkZ) {
        return Radiation.chunkBase(chunkX, chunkZ) >= minBase;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return test(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Override
    public PlacementModifierType<?> type() {
        return ECHoannaFramework.RADIATION_HOTSPOT.get();
    }
}
