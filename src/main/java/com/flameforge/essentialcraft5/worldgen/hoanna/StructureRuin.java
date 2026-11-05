package com.flameforge.essentialcraft5.worldgen.hoanna;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 海纳尔遗迹的「被毁」效果（第六批 F6b，batch6a §6.2 第 3 条、§4.2、D14 A）：确定性弹坑 {@link #carveCrater} 和按楼层缺损 {@link #decay}。
 * <p>
 * <b>为什么不用爆炸</b>：原作城镇每镇约 650~1 300 次 {@code ECExplosion}（每次 1 352 条射线），估约 1 秒 CPU / 镇，结果取决于区块生成顺序，
 * 还会炸进、并强行生成邻区块（别人建过东西的区块也照炸，P1）。这里两个方法都是「每一格自己决定挖不挖」：
 * <ul>
 *   <li>只看这一格（和它正上方、四周）的方块、这一格的世界坐标、坑的参数（中心、半径、种子）——用的随机是
 *       {@link HoannaStructurePiece#posHash(int, int, int, long)}，不是 postProcess 按区块播种的 random；</li>
 *   <li>只写传进来的 chunkBox（postProcess 的那个，就是当前区块），但判断时会读 chunkBox 外面（上方那一列、四周的流体），读不改。</li>
 * </ul>
 * 所以一个坑跨几个区块时，各区块分别挖出来拼在一起，和一次挖完完全一样（GameTest {@code craterIsChunkSeamless} 守着），也不会碰别的区块。
 * <p>
 * <b>挖哪些格</b>（两个方法共用）：不挖空气、带方块实体的（箱子、刷怪笼）、流体和含水方块、挖不动的（硬度 &lt; 0、{@code #features_cannot_replace}）；
 * 正上方或四周挨着流体的不挖（免得挖出口子让水 / 岩浆流进来）；正上方是没被挖掉的会掉的方块（沙、沙砾、混凝土粉末）时不挖（免得悬空的沙）。
 * 挖掉的最高一格上面剩下的东西（花草、火把、雪层）和范围里的栅栏 / 火把 / 梯子，交给 {@link HoannaStructurePiece#requestShapeUpdate} 按新邻居重算。
 * 写空气用 flag {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE}：世界生成里 flag 没有作用；在完整区块上（{@code /place}、GameTest）不连带更新邻居形状，
 * 形状统一由上面的后处理算，行为和生成时一致。
 */
public final class StructureRuin {

    /** 抗爆 &lt; {@value #SOFT_BELOW}（泥土、沙、草方块、混凝土、树叶、植物）：有效半径系数 */
    public static final float K_SOFT = 0.6F;
    /** 抗爆 &lt; {@value #STONE_BELOW}（石头、圆石、矿石、深板岩） */
    public static final float K_STONE = 0.35F;
    /** 抗爆 &lt; {@value #HARD_BELOW}（强化石 9、强化玻璃 9、护板） */
    public static final float K_HARD = 0.2F;
    public static final float SOFT_BELOW = 1.5F;
    public static final float STONE_BELOW = 7F;
    /** 抗爆 ≥ 它（虚空石 90、黑曜石、基岩）的不挖 */
    public static final float HARD_BELOW = 20F;
    /** 每格有效半径的抖动：× [{@value #JITTER_MIN}, {@value #JITTER_MIN} + {@value #JITTER_SPAN}) */
    public static final float JITTER_MIN = 0.85F;
    public static final float JITTER_SPAN = 0.3F;

    /** 弹坑、缺损各自异或进种子的常数（同一个种子两种用途互不相关） */
    public static final long CRATER_SALT = 0x6A09E667F3BCC909L;
    public static final long DECAY_SALT = 0xBB67AE8584CAA73BL;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private StructureRuin() {
    }

    /** 这一格的方块在 {@link #decay} 里被挖掉的概率；返回 0 表示不碰（按方块分档：碎石一个概率、墙和楼板另一个） */
    @FunctionalInterface
    public interface DecayChance {
        float chance(BlockState state);
    }

    /**
     * 弹坑里这一格方块的有效半径系数（默认 {@link #hardnessFactor}）；返回 ≤ 0 的不挖。只能按方块给（不看位置），
     * 超过 {@link #K_SOFT} 的按 K_SOFT 算——{@link #craterBounds} 按 K_SOFT 算范围，再大会挖到包围盒外面、坑在区块边上被截断。
     */
    @FunctionalInterface
    public interface CraterFactor {
        float factor(BlockState state);
    }

    // ---------------------------------------------------------------- 弹坑

    /**
     * 挖一个弹坑：以 center 为球心，每一格的有效半径 = radius × {@link #hardnessFactor}(这一格的方块) × 抖动（[0.85, 1.15)，按这一列的位置哈希），
     * 这一格到球心的距离不超过有效半径就挖（再按类注释的规则排除）。低于 seaLevel 的格子不挖（坑底碰不到海面以下，不会挖出悬空的水）。
     * 只遍历「球 ∩ chunkBox」这些列，每列从上往下判断。
     *
     * @param chunkBox postProcess 传进来的区块范围（只写这里面）
     * @param seed     坑自己的种子（拼件布局时从生成随机里取、存进 NBT）
     * @param seaLevel 这个高度以下不挖（城镇传 {@code chunkGenerator.getSeaLevel()}）
     * @return 这次在 chunkBox 里挖掉了几格（不算后处理顺带拆掉的花草）
     */
    public static int carveCrater(WorldGenLevel level, BoundingBox chunkBox, BlockPos center, float radius, long seed, int seaLevel) {
        return carveCrater(level, chunkBox, center, radius, seed, seaLevel, StructureRuin::hardnessFactor);
    }

    /**
     * 同 {@link #carveCrater(WorldGenLevel, BoundingBox, BlockPos, float, long, int)}，系数由 factor 按方块给（城镇房子的坑对强化石 / 强化玻璃另给系数），
     * floorY 以下不挖（调用方自己把海平面算进去：{@code max(海平面, 自己的下限)}）。floorY 和 factor 都是整个坑共用的参数、只看方块，分块挖照样 = 整块挖。
     */
    public static int carveCrater(WorldGenLevel level, BoundingBox chunkBox, BlockPos center, float radius, long seed, int floorY, CraterFactor factor) {
        if (!(radius > 0F)) {
            return 0;
        }
        float maxR = maxCraterRadius(radius);
        float maxR2 = maxR * maxR;
        int reach = craterReach(radius);
        float shell2 = (maxR + 1F) * (maxR + 1F);
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        int minX = Math.max(chunkBox.minX(), cx - reach);
        int maxX = Math.min(chunkBox.maxX(), cx + reach);
        int minZ = Math.max(chunkBox.minZ(), cz - reach);
        int maxZ = Math.min(chunkBox.maxZ(), cz + reach);
        int top = Math.min(cy + reach, level.getMaxBuildHeight() - 1);
        int bottom = Math.max(Math.max(cy - reach, floorY), level.getMinBuildHeight());
        if (minX > maxX || minZ > maxZ || bottom > top) {
            return 0;
        }
        long salted = seed ^ CRATER_SALT;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        Set<BlockPos> updates = new LinkedHashSet<>();
        int carved = 0;
        for (int x = minX; x <= maxX; x++) {
            int dx = x - cx;
            for (int z = minZ; z <= maxZ; z++) {
                int dz = z - cz;
                int horizontal2 = dx * dx + dz * dz;
                if (horizontal2 > shell2) {
                    continue;
                }
                BlockState above = level.getBlockState(pos.set(x, top + 1, z));
                boolean aboveCarved = false;
                int highestCarved = Integer.MIN_VALUE;
                // 抖动按列取（同一列上下用同一个系数）：同种方块越往下离球心越远越不挖，坑是碗形，不会挖出「上面一层草皮盖着、下面掏空」的夹层
                float jitter = JITTER_MIN + JITTER_SPAN * HoannaStructurePiece.unit(HoannaStructurePiece.posHash(x, 0, z, salted));
                for (int y = top; y >= bottom; y--) {
                    int dy = y - cy;
                    float d2 = horizontal2 + dy * dy;
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    boolean carve = false;
                    if (d2 <= maxR2 && isRuinable(level, pos, state)) {
                        float k = Math.min(K_SOFT, factor.factor(state));
                        if (k > 0F) {
                            float effective = radius * k * jitter;
                            carve = d2 <= effective * effective
                                    && !(above.getBlock() instanceof FallingBlock && !aboveCarved)
                                    && !touchesFluid(level, probe, x, y, z);
                        }
                    }
                    boolean inside = chunkBox.isInside(pos);
                    if (carve) {
                        if (inside) {
                            level.setBlock(pos, AIR, FLAGS);
                            carved++;
                        }
                        if (highestCarved == Integer.MIN_VALUE) {
                            highestCarved = y;
                        }
                    } else if (inside && d2 <= shell2 && HoannaStructurePiece.needsShapeUpdate(state)) {
                        updates.add(pos.immutable());
                    }
                    above = state;
                    aboveCarved = carve;
                }
                queueAboveHighest(level, chunkBox, pos, x, highestCarved, z, updates);
            }
        }
        for (BlockPos update : updates) {
            HoannaStructurePiece.requestShapeUpdate(level, update);
        }
        return carved;
    }

    /** 弹坑里最远能挖到的距离：radius × 0.6（最软的一档）× 1.15（抖动上限） */
    public static float maxCraterRadius(float radius) {
        return radius * K_SOFT * (JITTER_MIN + JITTER_SPAN);
    }

    /** 弹坑可能写到（挖掉或重算形状）的格子离球心的最远格数：ceil({@link #maxCraterRadius}) + 1 */
    public static int craterReach(float radius) {
        return Mth.ceil(maxCraterRadius(radius)) + 1;
    }

    /**
     * 弹坑可能写到的范围（球心 ± {@link #craterReach}，上下左右一样）。放弹坑的拼件（town_crater）<b>包围盒必须用它</b>：
     * 只有和包围盒相交的区块才会调这个拼件的 postProcess，包围盒小了，坑会在区块边上被齐刷刷截断。
     */
    public static BoundingBox craterBounds(BlockPos center, float radius) {
        int reach = craterReach(radius);
        return new BoundingBox(center.getX() - reach, center.getY() - reach, center.getZ() - reach,
                center.getX() + reach, center.getY() + reach, center.getZ() + reach);
    }

    /**
     * 有效半径系数 k（按方块的抗爆值分档，见常量）：&lt; 1.5 → 0.6、&lt; 7 → 0.35、&lt; 20 → 0.2、其余和流体 → 0（不挖）。
     * 用方块本身的抗爆值（{@code Block.getExplosionResistance()}），不看位置、不看爆炸源，结果只由方块决定。
     */
    public static float hardnessFactor(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return 0F;
        }
        float resistance = state.getBlock().getExplosionResistance();
        if (resistance < SOFT_BELOW) {
            return K_SOFT;
        }
        if (resistance < STONE_BELOW) {
            return K_STONE;
        }
        if (resistance < HARD_BELOW) {
            return K_HARD;
        }
        return 0F;
    }

    // ---------------------------------------------------------------- 按楼层缺损

    /**
     * 让 region 里匹配的方块按概率变成空气（房子二楼以上塌掉一部分，batch6a §5.5 town_house）。每一格挖不挖由
     * {@code posHash(x, y, z, seed ^ DECAY_SALT)} 决定（不用 postProcess 的 random：跨区块一致，也不受同一区块里别的拼件抽了几次随机影响）。
     * 挖的规则同类注释（带方块实体的、流体、挨着流体的、上面压着会掉的方块的都不挖）。只写 region ∩ chunkBox。
     *
     * @param chance 按方块给概率，返回 ≤ 0 的不碰（例：碎石 0.6 × i / F、墙和楼板 0.25 × i / F）
     * @return 这次在 chunkBox 里挖掉了几格
     */
    public static int decay(WorldGenLevel level, BoundingBox chunkBox, BoundingBox region, long seed, DecayChance chance) {
        int minX = Math.max(region.minX(), chunkBox.minX());
        int maxX = Math.min(region.maxX(), chunkBox.maxX());
        int minZ = Math.max(region.minZ(), chunkBox.minZ());
        int maxZ = Math.min(region.maxZ(), chunkBox.maxZ());
        int top = Math.min(region.maxY(), level.getMaxBuildHeight() - 1);
        int bottom = Math.max(region.minY(), level.getMinBuildHeight());
        if (minX > maxX || minZ > maxZ || bottom > top) {
            return 0;
        }
        long salted = seed ^ DECAY_SALT;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        Set<BlockPos> updates = new LinkedHashSet<>();
        int removed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockState above = level.getBlockState(pos.set(x, top + 1, z));
                boolean aboveRemoved = false;
                int highestRemoved = Integer.MIN_VALUE;
                for (int y = top; y >= bottom; y--) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    boolean remove = false;
                    if (isRuinable(level, pos, state)) {
                        float p = chance.chance(state);
                        remove = p > 0F
                                && HoannaStructurePiece.unit(HoannaStructurePiece.posHash(x, y, z, salted)) < p
                                && !(above.getBlock() instanceof FallingBlock && !aboveRemoved)
                                && !touchesFluid(level, probe, x, y, z);
                    }
                    boolean inside = chunkBox.isInside(pos);
                    if (remove) {
                        if (inside) {
                            level.setBlock(pos, AIR, FLAGS);
                            removed++;
                        }
                        if (highestRemoved == Integer.MIN_VALUE) {
                            highestRemoved = y;
                        }
                    } else if (inside && HoannaStructurePiece.needsShapeUpdate(state)) {
                        updates.add(pos.immutable());
                    }
                    above = state;
                    aboveRemoved = remove;
                }
                queueAboveHighest(level, chunkBox, pos, x, highestRemoved, z, updates);
            }
        }
        for (BlockPos update : updates) {
            HoannaStructurePiece.requestShapeUpdate(level, update);
        }
        return removed;
    }

    /** {@link #decay(WorldGenLevel, BoundingBox, BoundingBox, long, DecayChance)} 的简写：匹配 target 的方块统一按 chance 挖 */
    public static int decay(WorldGenLevel level, BoundingBox chunkBox, BoundingBox region, float chance, long seed, Predicate<BlockState> target) {
        return decay(level, chunkBox, region, seed, state -> target.test(state) ? chance : 0F);
    }

    // ---------------------------------------------------------------- 共用规则

    /**
     * 这一格能不能被「毁」：不是空气、没有方块实体、不是流体 / 含水方块、挖得动（硬度 ≥ 0）、不在 {@code #features_cannot_replace}（基岩、刷怪笼、箱子、末地门框……）。
     */
    public static boolean isRuinable(BlockGetter level, BlockPos pos, BlockState state) {
        return !state.isAir()
                && !state.hasBlockEntity()
                && state.getFluidState().isEmpty()
                && !state.is(BlockTags.FEATURES_CANNOT_REPLACE)
                && state.getDestroySpeed(level, pos) >= 0F;
    }

    /** 正上方或东南西北四格有流体（含含水方块）：挖了会让流体流进来 */
    private static boolean touchesFluid(BlockGetter level, BlockPos.MutableBlockPos probe, int x, int y, int z) {
        return !level.getFluidState(probe.set(x, y + 1, z)).isEmpty()
                || !level.getFluidState(probe.set(x + 1, y, z)).isEmpty()
                || !level.getFluidState(probe.set(x - 1, y, z)).isEmpty()
                || !level.getFluidState(probe.set(x, y, z + 1)).isEmpty()
                || !level.getFluidState(probe.set(x, y, z - 1)).isEmpty();
    }

    /** 一列里挖掉的最高一格上面、留下来的非空气方块（花草、火把、雪层）登记形状重算：没了依托的由后处理拆掉 */
    private static void queueAboveHighest(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int x, int highest, int z,
                                          Collection<BlockPos> updates) {
        if (highest == Integer.MIN_VALUE) {
            return;
        }
        pos.set(x, highest + 1, z);
        if (chunkBox.isInside(pos) && !level.getBlockState(pos).isAir()) {
            updates.add(pos.immutable());
        }
    }
}
