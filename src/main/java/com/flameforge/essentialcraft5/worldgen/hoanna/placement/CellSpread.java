package com.flameforge.essentialcraft5.worldgen.hoanna.placement;

import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * 放置修饰 {@code essentialcraft5:cell_spread} {spacing, separation, salt}（第十三批 13d-HF，batch13.md §6.1；HS §2.1.4）。
 * <p>
 * 中景观、里程碑在玩家眼里是「一处地方」，只靠 rarity 会扎堆（相邻区块各掷各的）。这里把世界切成 spacing × spacing 区块的格子，
 * 每格用和原版 {@code RandomSpreadStructurePlacement.getPotentialStructureChunk}（LINEAR）<b>同一套哈希</b>挑一个区块，只有挑中的那块放：
 * 格内偏移是 [0, spacing − separation) 里的均匀整数，所以挑中的区块两两（切比雪夫距离）至少隔 separation + 1 个区块；
 * 任意一个 w × w 区块窗口在每个轴上最多碰到「窗口宽度 ÷ 格宽」附近的两三格。每区块一次 64 位哈希（新建一个 {@link WorldgenRandom}、取两次 nextInt），不看世界。
 * <p>
 * 用法：placed feature 的放置列表第一项（后面再接 in_square、heightmap、biome 这些）：
 * <pre>{"type": "essentialcraft5:cell_spread", "spacing": 14, "separation": 7, "salt": 202610261}</pre>
 * 位置取进来的那一格所在区块（放置的起点是区块的最小角，放在 in_square 之后也一样落在同一区块）；种子是世界种子（{@code WorldGenLevel.getSeed()}）。
 * codec 校验 spacing &gt; separation ≥ 0、spacing ≤ 4096（和原版 random_spread 同一限制）。
 * <p>
 * 纯函数 {@link #pick}、{@link #isCellChunk} 给普查、勘测图、GameTest 直接调（同一个种子、同一组参数，和世界生成时的判定一模一样）。
 */
public final class CellSpread extends PlacementFilter {

    /** 先在 MapCodec 上校验再转 Codec（同原版 random_spread）：这样 type 分派时字段和 type 平铺在一层，不会要一个 value 包着 */
    public static final Codec<CellSpread> CODEC = RecordCodecBuilder.<CellSpread>mapCodec(instance -> instance.group(
                    ExtraCodecs.POSITIVE_INT.fieldOf("spacing").forGetter(CellSpread::spacing),
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("separation").forGetter(CellSpread::separation),
                    Codec.INT.fieldOf("salt").forGetter(CellSpread::salt))
            .apply(instance, CellSpread::new))
            .flatXmap(CellSpread::validate, CellSpread::validate).codec();

    /** 和原版 random_spread 的 spacing 上限一样 */
    public static final int MAX_SPACING = 4096;

    private final int spacing;
    private final int separation;
    private final int salt;

    public CellSpread(int spacing, int separation, int salt) {
        this.spacing = spacing;
        this.separation = separation;
        this.salt = salt;
    }

    private static DataResult<CellSpread> validate(CellSpread spread) {
        if (spread.spacing > MAX_SPACING) {
            return DataResult.error(() -> "cell_spread 的 spacing 不能超过 " + MAX_SPACING + "：" + spread.spacing);
        }
        if (spread.separation < 0 || spread.spacing <= spread.separation) {
            return DataResult.error(() -> "cell_spread 要 spacing > separation ≥ 0，实际 spacing " + spread.spacing + "、separation " + spread.separation);
        }
        return DataResult.success(spread);
    }

    public int spacing() {
        return spacing;
    }

    public int separation() {
        return separation;
    }

    public int salt() {
        return salt;
    }

    // ------------------------------------------------------------------ 纯函数

    /**
     * 第 (cellX, cellZ) 格挑中的区块：和原版 {@code RandomSpreadStructurePlacement.getPotentialStructureChunk}（LINEAR、同一个 salt）逐格一致。
     * 调用方保证 spacing &gt; separation ≥ 0。
     */
    public static ChunkPos pick(long worldSeed, int cellX, int cellZ, int spacing, int separation, int salt) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(worldSeed, cellX, cellZ, salt);
        int range = spacing - separation;
        int dx = random.nextInt(range);
        int dz = random.nextInt(range);
        return new ChunkPos(cellX * spacing + dx, cellZ * spacing + dz);
    }

    /** 这个区块是不是它那一格挑中的（格子按 floorDiv 算，负坐标也对） */
    public static boolean isCellChunk(long worldSeed, int chunkX, int chunkZ, int spacing, int separation, int salt) {
        ChunkPos picked = pick(worldSeed, Math.floorDiv(chunkX, spacing), Math.floorDiv(chunkZ, spacing), spacing, separation, salt);
        return picked.x == chunkX && picked.z == chunkZ;
    }

    /** 按这一组参数判一个区块（同 {@link #isCellChunk}） */
    public boolean test(long worldSeed, int chunkX, int chunkZ) {
        return isCellChunk(worldSeed, chunkX, chunkZ, spacing, separation, salt);
    }

    // ------------------------------------------------------------------ 放置修饰

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return test(context.getLevel().getSeed(), SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Override
    public PlacementModifierType<?> type() {
        return ECHoannaFramework.CELL_SPREAD.get();
    }
}
