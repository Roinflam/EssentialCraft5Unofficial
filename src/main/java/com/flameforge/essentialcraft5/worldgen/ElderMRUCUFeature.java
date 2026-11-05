package com.flameforge.essentialcraft5.worldgen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemTrigger;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.common.DungeonHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * 上古 MRU 聚集体遗迹。对应原作 WorldGenElderMRUCU。
 * <p>
 * 原作：只在主世界，每区块尝试 1 次（配置 ElderMRUCUGenAttempts；移植后先过 1/16 的稀有度，见下），x、z 在区块内随机，y 在 [0, 128) 均匀，
 * 四种布局等概率。每种布局先检查底面，不满足就放弃这次尝试（不换位置重试）：
 * <ul>
 *   <li>布局 0（5×5）：石砖平台 + 圆石墙围栏，中心錾制石砖。</li>
 *   <li>布局 1（9×9）：随机石砖（普通 / 苔 / 裂纹）平台 + 圆石墙围栏，中心錾制石砖，四个角落内侧各一个地牢战利品箱。</li>
 *   <li>布局 2（9×9）：两层随机石砖 + 圆石墙，第二层里有一圈岩浆；聚集体在 (0, 0) 角上而不是中心（原作如此，保留）。</li>
 *   <li>布局 3（9×5×9）：埋在石头里的石砖笼子，四面是铁栏杆，四个角落内侧各一个战利品箱，中心四周各一个原版刷怪笼。</li>
 * </ul>
 * 布局 0~2 要求底面 9×9（或 5×5）全是完整方块、且每格上方不是完整方块（地表或洞穴地面）；布局 3 要求底面全是石头。
 * 每个遗迹在底面上方 1.5 格（錾制石砖的中心）放一个 5000~10000 MRU、平衡值 0~2 的聚集体。
 * <p>
 * 换算到 1.20.1（放置规则在 worldgen/placed_feature/elder_mrucu.json）：
 * <ul>
 *   <li><b>稀有度（全模组安全原则 P3，policy.md §6.1，P-1）</b>：placement 最前面加了 {@code rarity_filter} chance 16，
 *       每区块先掷一次 1/16 才去尝试。没加之前是原作的「每区块尝试 1 次」，读真实存档按 {@link #canGenerateAt} 的判定抽样实测：
 *       另一个测试档（5 863 区块）每 12~16 区块一座、EC5 开发档（529 区块，山地多）每 9~13 区块一座，比原作 1.12（6 个存档 3 740 区块实测每 27 区块一座）还密一倍——
 *       原作在 populate 之后判定（矿团已经嵌进石头），1.18 以后地形又高；99% 是布局 3（埋着的笼子），刷怪笼密度是同存档原版刷怪笼（0.031 个 / 区块）的 8~11 倍。
 *       加稀有度以后按同一批数据折算为每 190~260 区块一座（山地 147~203），约为原作的 1/7~1/10；刷怪笼 0.015~0.021 个 / 区块，是原版的 50%~68%（policy §1.3 要求不超过 3/4），
 *       玩家视野 17×17 区块里平均 1.1~1.6 座。四种布局和笼子里的 4 个刷怪笼都不变，只靠稀有度降密度；服主想调就用数据包覆盖这个 JSON。
 *       海纳尔维度（原作海纳尔走主世界生成分支）另建了一份 placed feature {@code elder_mrucu_hoanna}（第六批 6a M1，D12 A：目标每 25~35 区块一座，
 *       原作 1.12 实测每 27 区块；不带稀有度时 1.18 以后地形高、实际密一倍），由 {@code forge/biome_modifier/hoanna_elder_mrucu.json}
 *       挂在 #essentialcraft5:is_hoanna 上，和主世界这一份互不影响。{@code rarity_filter} chance 起初定 2，64 × 64 区块普查（种子 20260929）实测每 21.3 区块一座
 *       （1/2 × 落点约 9.4% 过 {@link #canGenerateAt}），审查后改 3，约每 32 区块一座（改 tools/gen/hoanna_biomes.py 的 ELDER_RARITY）。</li>
 *   <li>高度仍取绝对 y 0~127，和 ECWorldGen 的矿石一样按原作绝对高度搬过来：1.20 的 0 以上石头层与 1.12 体量相当，
 *       y &lt; 0 的深板岩层原作不存在。布局 3 的「石头」取 #minecraft:stone_ore_replaceables（石头、花岗岩、闪长岩、安山岩，
 *       正是 1.12 Blocks.STONE 的各个变种），所以不会嵌进深板岩。</li>
 *   <li>1.12 的 isBlockNormalCube（阻挡移动的完整方块）对应 1.20 的 {@link BlockState#isCollisionShapeFullBlock}。</li>
 *   <li>1.12 的墙和铁栏杆渲染时才按邻居算连接；1.20 的连接是方块状态，放完以后逐个按邻居更新一次形状，否则全是孤零零的柱子。</li>
 *   <li>生成步骤 underground_structures（和原版地牢同一步），所以原作的「矿石生成之后」变成了「矿石生成之前」：
 *       遗迹用的石砖不在矿石可替换的标签里，矿石不会盖掉遗迹。</li>
 *   <li>聚集体设为 alwaysStay（原作 false，但原作从不检查这个标记；这里 MRU 归零会消失，遗迹的聚集体应该一直在）。</li>
 * </ul>
 * 箱子（布局 1、3 各 4 个）用本模组的表 {@link #CHEST_LOOT}（第十二批，batch12.md §4.1.3）：整张引用原版地牢表、再加一池 35% 的源质残卷（序、卷一～卷四）。
 * 原版地牢表照常被 loot/DungeonLootInjection 注入特殊饰品，引用它的这张表跟着拿到。没有直接往原版地牢表里注入残卷，是因为那样原版地牢也会出，
 * 「先辈留下的书压在遗迹箱子里」就落不到遗迹上。已经生成、还没开过的箱子记的是旧表 id，照旧只出地牢的东西，不补。
 * <p>
 * 刷怪笼保留：它们是原版地牢式刷怪笼（怪物种类同原版地牢），不是本模组帮玩家刷怪的东西；「防刷怪塔」删的是刷怪笼收集器等搬运、
 * 收割刷怪笼产物的内容。而且原作的笼子内部不挖空，埋在石头里的刷怪笼周围没有生成空间，多半刷不出怪，照搬即可。
 */
public class ElderMRUCUFeature extends Feature<NoneFeatureConfiguration> {

    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final int MIN_MRU = 5000;
    private static final int MRU_RANGE = 5000;
    /** 遗迹箱子的掉落表：data/essentialcraft5/loot_tables/chests/elder_mrucu.json（原版地牢表 + 残卷池） */
    public static final ResourceLocation CHEST_LOOT = EssentialCraft5.id("chests/elder_mrucu");

    public ElderMRUCUFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        return switch (random.nextInt(4)) {
            case 0 -> placePlatform(level, random, origin);
            case 1 -> placeChestPlatform(level, random, origin);
            case 2 -> placeLavaPlatform(level, random, origin);
            default -> placeCage(level, random, origin);
        };
    }

    /** 布局 0：5×5 石砖平台 */
    private boolean placePlatform(WorldGenLevel level, RandomSource random, BlockPos origin) {
        if (!canGenerateAt(level, origin, 4, true)) {
            return false;
        }
        List<BlockPos> connecting = new ArrayList<>();
        for (int x = 0; x <= 4; ++x) {
            for (int z = 0; z <= 4; ++z) {
                level.setBlock(origin.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), FLAGS);
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    wall(level, origin.offset(x, 1, z), connecting);
                }
                if (x == 2 && z == 2) {
                    level.setBlock(origin.offset(x, 1, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), FLAGS);
                    spawnPresence(level, random, origin, x, z);
                }
            }
        }
        updateShapes(level, connecting);
        return true;
    }

    /** 布局 1：9×9 平台，四个箱子 */
    private boolean placeChestPlatform(WorldGenLevel level, RandomSource random, BlockPos origin) {
        if (!canGenerateAt(level, origin, 8, true)) {
            return false;
        }
        List<BlockPos> connecting = new ArrayList<>();
        for (int x = 0; x <= 8; ++x) {
            for (int z = 0; z <= 8; ++z) {
                level.setBlock(origin.offset(x, 0, z), randomBricks(random), FLAGS);
                if (x == 0 || x == 8 || z == 0 || z == 8) {
                    wall(level, origin.offset(x, 1, z), connecting);
                }
                if (x == 4 && z == 4) {
                    level.setBlock(origin.offset(x, 1, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), FLAGS);
                    spawnPresence(level, random, origin, x, z);
                }
                if (isInnerCorner(x, z)) {
                    // 原作 getStateFromMeta(4)：朝西
                    chest(level, random, origin.offset(x, 1, z), Direction.WEST);
                }
            }
        }
        updateShapes(level, connecting);
        return true;
    }

    /** 布局 2：两层平台，第二层一圈岩浆 */
    private boolean placeLavaPlatform(WorldGenLevel level, RandomSource random, BlockPos origin) {
        if (!canGenerateAt(level, origin, 8, true)) {
            return false;
        }
        List<BlockPos> connecting = new ArrayList<>();
        for (int x = 0; x <= 8; ++x) {
            for (int z = 0; z <= 8; ++z) {
                level.setBlock(origin.offset(x, 0, z), randomBricks(random), FLAGS);
                level.setBlock(origin.offset(x, 1, z), randomBricks(random), FLAGS);
                if (x == 0 || x == 8 || z == 0 || z == 8) {
                    wall(level, origin.offset(x, 2, z), connecting);
                }
                if (x == 0 && z == 0) {
                    level.setBlock(origin.offset(x, 2, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), FLAGS);
                    connecting.remove(origin.offset(x, 2, z));
                    spawnPresence(level, random, origin, x, z);
                }
                if (x != 7 && x != 1 && z != 7 && z != 1 && (x == 2 || x == 6 || z == 2 || z == 6)) {
                    wall(level, origin.offset(x, 2, z), connecting);
                }
                if (x != 8 && x != 0 && z != 8 && z != 0 && (x == 1 || x == 7 || z == 1 || z == 7)) {
                    level.setBlock(origin.offset(x, 1, z), Blocks.LAVA.defaultBlockState(), FLAGS);
                }
            }
        }
        updateShapes(level, connecting);
        return true;
    }

    /** 布局 3：埋在石头里的笼子，四个箱子、四个刷怪笼 */
    private boolean placeCage(WorldGenLevel level, RandomSource random, BlockPos origin) {
        if (!canGenerateAt(level, origin, 8, false)) {
            return false;
        }
        List<BlockPos> connecting = new ArrayList<>();
        for (int x = 0; x <= 8; ++x) {
            for (int z = 0; z <= 8; ++z) {
                level.setBlock(origin.offset(x, 0, z), randomBricks(random), FLAGS);
                level.setBlock(origin.offset(x, 4, z), randomBricks(random), FLAGS);
                boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
                boolean corner = (x == 0 || x == 8) && (z == 0 || z == 8);
                if (edge) {
                    level.setBlock(origin.offset(x, 2, z), randomBricks(random), FLAGS);
                    if (corner) {
                        level.setBlock(origin.offset(x, 1, z), randomBricks(random), FLAGS);
                        level.setBlock(origin.offset(x, 3, z), randomBricks(random), FLAGS);
                    } else {
                        bars(level, origin.offset(x, 1, z), connecting);
                        bars(level, origin.offset(x, 3, z), connecting);
                    }
                }
                if (x == 4 && z == 4) {
                    level.setBlock(origin.offset(x, 1, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), FLAGS);
                    spawnPresence(level, random, origin, x, z);
                }
                if (isInnerCorner(x, z)) {
                    // 原作 getStateFromMeta(rand.nextInt(4))，1.12 的箱子只认水平朝向，这里直接取随机水平朝向
                    chest(level, random, origin.offset(x, 1, z), Direction.Plane.HORIZONTAL.getRandomDirection(random));
                }
                if (x == 3 && z == 4 || x == 5 && z == 4 || x == 4 && z == 5 || x == 4 && z == 3) {
                    spawner(level, random, origin.offset(x, 1, z));
                }
            }
        }
        updateShapes(level, connecting);
        return true;
    }

    /**
     * 原作 canGenerateAt：底面每一格都必须是完整方块；needsOpenTop 时每格上方不能是完整方块，
     * 否则（布局 3）每格都必须是石头。
     */
    private static boolean canGenerateAt(WorldGenLevel level, BlockPos origin, int size, boolean needsOpenTop) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx <= size; ++dx) {
            for (int dz = 0; dz <= size; ++dz) {
                pos.setWithOffset(origin, dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !state.isCollisionShapeFullBlock(level, pos)) {
                    return false;
                }
                if (needsOpenTop) {
                    above.setWithOffset(pos, Direction.UP);
                    if (level.getBlockState(above).isCollisionShapeFullBlock(level, above)) {
                        return false;
                    }
                } else if (!state.is(BlockTags.STONE_ORE_REPLACEABLES)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isInnerCorner(int x, int z) {
        return (x == 1 || x == 7) && (z == 1 || z == 7);
    }

    /** 原作 STONEBRICK meta 0~2：普通、苔、裂纹石砖 */
    private static BlockState randomBricks(RandomSource random) {
        return switch (random.nextInt(3)) {
            case 0 -> Blocks.STONE_BRICKS.defaultBlockState();
            case 1 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            default -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        };
    }

    private static void wall(WorldGenLevel level, BlockPos pos, List<BlockPos> connecting) {
        level.setBlock(pos, Blocks.COBBLESTONE_WALL.defaultBlockState(), FLAGS);
        connecting.add(pos);
    }

    private static void bars(WorldGenLevel level, BlockPos pos, List<BlockPos> connecting) {
        level.setBlock(pos, Blocks.IRON_BARS.defaultBlockState(), FLAGS);
        connecting.add(pos);
    }

    /** 墙和铁栏杆放完以后按邻居算一次连接 */
    private static void updateShapes(WorldGenLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
            if (shaped != state) {
                level.setBlock(pos, shaped, FLAGS);
            }
        }
    }

    private static void chest(WorldGenLevel level, RandomSource random, BlockPos pos, Direction facing) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), FLAGS);
        RandomizableContainerBlockEntity.setLootTable(level, random, pos, CHEST_LOOT);
    }

    private static void spawner(WorldGenLevel level, RandomSource random, BlockPos pos) {
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), FLAGS);
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            // 和原版地牢一样从 Forge 的地牢怪物表里抽（骷髅、僵尸、蜘蛛……）
            spawner.setEntityId(DungeonHooks.getRandomDungeonMob(random), random);
        }
    }

    /** 在 (x, 1.5, z) 放一个 5000~10000 MRU、平衡值 0~2（1 + randomFloat，三角分布）的聚集体 */
    private static void spawnPresence(WorldGenLevel level, RandomSource random, BlockPos origin, int x, int z) {
        int mru = MIN_MRU + random.nextInt(MRU_RANGE);
        float balance = 1F + random.nextFloat() - random.nextFloat();
        MRUPresenceEntity presence = MRUPresenceEntity.create(level.getLevel(),
                origin.getX() + x + 0.5D, origin.getY() + 1.5D, origin.getZ() + z + 0.5D, mru, balance, false, true);
        // 新遗迹的聚集体记上聚能魔像守护和原位（batch13.md §5.3.1）；只读种子和维度，不动 random，遗迹长相不变
        presence.setGuardian(MRUGolemTrigger.ruinGuardian(level, presence.blockPosition()));
        level.addFreshEntity(presence);
    }
}
