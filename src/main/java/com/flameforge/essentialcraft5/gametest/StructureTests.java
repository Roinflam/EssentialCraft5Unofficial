package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.structure.StructureCache;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import com.flameforge.essentialcraft5.structure.StructureResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 框架「多方块结构判定」（structure/）的 GameTest：用原版方块搭米斯莱熔炉、太阳棱镜同样形状的结构
 * （金块当护板、磁石当熔炉、紫水晶块当水晶柱、玻璃当棱镜），核对形状和原作一致、旋转、第一个不符的位置、
 * 空气与任意方块规则、标签与谓词规则、缓存的节流和「区块没加载保留上次结果」、登记表。
 * 大结构用模板 empty_large（11×8×11 空气），其余用 empty。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class StructureTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 在 11×8×11 模板里放米斯莱熔炉形状的原点：下面留一层（y=-1 在模板的 y=1），上面留得下 y=+2 */
    private static final BlockPos FURNACE_ORIGIN = new BlockPos(5, 2, 5);
    private static final BlockPos PRISM_ORIGIN = new BlockPos(2, 1, 2);

    /** 米斯莱熔炉的水晶柱位置（可选） */
    private static final StructurePattern.Rule PILLAR = StructurePattern.Rule.block(() -> Blocks.AMETHYST_BLOCK).optional();
    private static final StructurePattern.Rule PLATING = StructurePattern.Rule.block(() -> Blocks.GOLD_BLOCK);

    /** 和 batch2 §3.6 / 原作 TileMithrilineFurnace 同形状：24 块护板 + 13 个水晶柱位置 */
    static final StructurePattern FURNACE_LIKE = StructurePattern.builder()
            .where('P', PLATING)
            .where('C', PILLAR)
            .where('F', StructurePattern.Rule.block(() -> Blocks.LODESTONE))
            .anchor('F')
            .layer("  P  ", " PPP ", "PP PP", " PPP ", "  P  ")
            .layer("P P P", " C C ", "P F P", " C C ", "P P P")
            .layer("P C P", "     ", "C C C", "     ", "P C P")
            .layer("C   C", "     ", "     ", "     ", "C   C")
            .build();

    /** 太阳棱镜（原作 TileSolarPrism）：同层四个方向隔一格有方块、紧挨着的 8 格是空气 */
    static final StructurePattern PRISM_LIKE = StructurePattern.builder()
            .where('B', StructurePattern.Rule.anyBlock())
            .where('A', StructurePattern.Rule.air())
            .where('S', StructurePattern.Rule.block(() -> Blocks.GLASS))
            .anchor('S')
            .layer("  B  ", " AAA ", "BASAB", " AAA ", "  B  ")
            .build();

    private StructureTests() {
    }

    /** 按结构把必需的格子摆出来（每条规则放代表方块；空气规则放空气） */
    private static void build(GameTestHelper helper, StructurePattern pattern, BlockPos origin, Direction facing) {
        BlockPos worldOrigin = helper.absolutePos(origin);
        for (Map.Entry<BlockPos, StructurePattern.Rule> entry : pattern.rules().entrySet()) {
            if (entry.getValue().isOptional()) {
                continue;
            }
            BlockPos pos = StructurePattern.toWorld(worldOrigin, entry.getKey(), facing);
            helper.getLevel().setBlockAndUpdate(pos, entry.getValue().displayState());
        }
    }

    // ================================================================ 形状

    /** 熔炉形状和原作逐格一致：下层 12、同层 8、上一层四角 4 块护板；13 个水晶柱位置 = 原作 possiblePowerSources；分成 4 层 */
    @GameTest(template = EMPTY)
    public static void furnaceShapeMatchesOriginal(GameTestHelper helper) {
        Set<BlockPos> plating = new HashSet<>(FURNACE_LIKE.offsets(PLATING));
        Set<BlockPos> original = Set.of(
                new BlockPos(-1, -1, 0), new BlockPos(1, -1, 0), new BlockPos(0, -1, -1), new BlockPos(0, -1, 1),
                new BlockPos(1, -1, 1), new BlockPos(1, -1, -1), new BlockPos(-1, -1, 1), new BlockPos(-1, -1, -1),
                new BlockPos(-2, -1, 0), new BlockPos(2, -1, 0), new BlockPos(0, -1, -2), new BlockPos(0, -1, 2),
                new BlockPos(-2, 0, 0), new BlockPos(2, 0, 0), new BlockPos(0, 0, -2), new BlockPos(0, 0, 2),
                new BlockPos(-2, 0, 2), new BlockPos(2, 0, -2), new BlockPos(-2, 0, -2), new BlockPos(2, 0, 2),
                new BlockPos(-2, 1, 2), new BlockPos(2, 1, -2), new BlockPos(-2, 1, -2), new BlockPos(2, 1, 2));
        helper.assertTrue(plating.equals(original), "护板位置应和原作 isStructureCorrect 一致，实际 " + plating);
        Set<BlockPos> pillars = new HashSet<>(FURNACE_LIKE.offsets(PILLAR));
        Set<BlockPos> sources = Set.of(
                new BlockPos(0, 1, 0),
                new BlockPos(1, 0, 1), new BlockPos(-1, 0, 1), new BlockPos(1, 0, -1), new BlockPos(-1, 0, -1),
                new BlockPos(2, 1, 0), new BlockPos(-2, 1, 0), new BlockPos(0, 1, 2), new BlockPos(0, 1, -2),
                new BlockPos(2, 2, 2), new BlockPos(-2, 2, 2), new BlockPos(2, 2, -2), new BlockPos(-2, 2, -2));
        helper.assertTrue(pillars.equals(sources), "水晶柱位置应和原作 possiblePowerSources 一致，实际 " + pillars);
        helper.assertTrue(FURNACE_LIKE.requiredCount() == 25, "参与判定的应是 24 块护板 + 熔炉自己，实际 " + FURNACE_LIKE.requiredCount());
        helper.assertTrue(FURNACE_LIKE.size() == 38, "规则总数应为 38（含 13 个可选），实际 " + FURNACE_LIKE.size());

        List<StructurePattern.Layer> layers = FURNACE_LIKE.layers();
        helper.assertTrue(layers.size() == 4, "应分 4 层（y = -1 ~ 2），实际 " + layers.size());
        int[] expected = {12, 8, 4, 0};
        for (int i = 0; i < layers.size(); i++) {
            StructurePattern.Layer layer = layers.get(i);
            helper.assertTrue(layer.y() == i - 1, "第 " + i + " 层的 y 应为 " + (i - 1));
            helper.assertTrue(layer.width() == 5 && layer.depth() == 5, "每层都应是 5×5");
            int count = 0;
            for (StructurePattern.Rule rule : layer.cells()) {
                if (rule == PLATING) {
                    count++;
                }
            }
            helper.assertTrue(count == expected[i], "y = " + layer.y() + " 的护板应有 " + expected[i] + " 块，实际 " + count);
        }
        StructurePattern.Layer anchorLayer = layers.get(1);
        helper.assertTrue(anchorLayer.anchorColumn() == 2 && anchorLayer.anchorRow() == 2, "机器应在网格正中");
        helper.assertTrue(anchorLayer.at(2, 2) != null && anchorLayer.at(2, 2).displayState().is(Blocks.LODESTONE), "y = 0 正中应是机器");
        helper.assertTrue(layers.get(0).at(2, 2) == null, "机器正下方原作不要求");
        helper.assertTrue(anchorLayer.distinctRules().size() == 3, "y = 0 层的图例应是护板、水晶柱、机器三种");
        helper.succeed();
    }

    /** 按世界坐标列出的水晶柱位置随朝向旋转；北向 (0, 1, -2) 朝东时在 (2, 1, 0) */
    @GameTest(template = EMPTY)
    public static void positionsFollowFacing(GameTestHelper helper) {
        BlockPos origin = new BlockPos(100, 64, 100);
        List<BlockPos> north = FURNACE_LIKE.positions(PILLAR, origin, Direction.NORTH);
        helper.assertTrue(north.size() == 13, "应列出 13 个水晶柱位置，实际 " + north.size());
        helper.assertTrue(north.contains(origin.above()), "熔炉正上方应是一个水晶柱位置");
        helper.assertTrue(StructurePattern.toWorld(origin, new BlockPos(0, 1, -2), Direction.EAST).equals(origin.offset(2, 1, 0)),
                "北向的 (0, 1, -2) 朝东应转到 (2, 1, 0)");
        helper.assertTrue(StructurePattern.toWorld(origin, new BlockPos(0, 0, -1), Direction.SOUTH).equals(origin.south()),
                "北向正前方朝南时应在南边");
        helper.assertTrue(StructurePattern.toWorld(origin, new BlockPos(0, 0, -1), Direction.WEST).equals(origin.west()),
                "北向正前方朝西时应在西边");
        helper.assertTrue(StructurePattern.toWorld(origin, new BlockPos(0, 0, -1), Direction.UP).equals(origin.north()),
                "朝上按北处理");
        helper.succeed();
    }

    // ================================================================ 判定

    /** 摆齐 24 块护板 → MATCH（水晶柱可选，不摆也行）；拆掉一块 → MISMATCH，并指出那一格 */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceMatchAndMismatch(GameTestHelper helper) {
        build(helper, FURNACE_LIKE, FURNACE_ORIGIN, Direction.NORTH);
        BlockPos origin = helper.absolutePos(FURNACE_ORIGIN);
        StructurePattern.Match match = FURNACE_LIKE.match(helper.getLevel(), origin, Direction.NORTH);
        helper.assertTrue(match.result() == StructureResult.MATCH, "摆齐后应 MATCH，实际 " + match.result() + " " + match.mismatch());

        BlockPos removed = origin.offset(2, 1, 2);
        helper.getLevel().setBlockAndUpdate(removed, Blocks.AIR.defaultBlockState());
        match = FURNACE_LIKE.match(helper.getLevel(), origin, Direction.NORTH);
        helper.assertTrue(match.result() == StructureResult.MISMATCH, "拆掉一块后应 MISMATCH，实际 " + match.result());
        StructurePattern.Mismatch mismatch = match.mismatch();
        helper.assertTrue(mismatch != null && mismatch.pos().equals(removed), "不符的应是被拆的那格 " + removed + "，实际 " + mismatch);
        helper.assertTrue(mismatch.offset().equals(new BlockPos(2, 1, 2)) && mismatch.rule() == PLATING && mismatch.found().isAir(),
                "不符信息应带北向偏移、规则和实际方块，实际 " + mismatch);
        helper.assertTrue(mismatch.message().getString().length() > 0, "应能生成提示文字");

        // 再拆一块更靠下的：第一个不符按 y、z、x 顺序，应报下层那块
        BlockPos lower = origin.offset(0, -1, -2);
        helper.getLevel().setBlockAndUpdate(lower, Blocks.DIRT.defaultBlockState());
        match = FURNACE_LIKE.match(helper.getLevel(), origin, Direction.NORTH);
        helper.assertTrue(match.mismatch() != null && match.mismatch().pos().equals(lower), "应先报最下层的不符，实际 " + match.mismatch());
        helper.assertTrue(match.mismatch().found().is(Blocks.DIRT), "实际方块应是泥土");
        helper.succeed();
    }

    /** 不对称的结构：朝东摆好，按东判定 MATCH，按北判定 MISMATCH */
    @GameTest(template = EMPTY)
    public static void rotatedStructure(GameTestHelper helper) {
        StructurePattern arrow = StructurePattern.builder()
                .where('M', StructurePattern.Rule.block(() -> Blocks.LODESTONE))
                .where('G', StructurePattern.Rule.block(() -> Blocks.GOLD_BLOCK))
                .where('I', StructurePattern.Rule.block(() -> Blocks.IRON_BLOCK))
                .anchor('M')
                .layer("G", "M", "I")
                .build();
        BlockPos origin = new BlockPos(2, 1, 2);
        build(helper, arrow, origin, Direction.EAST);
        BlockPos world = helper.absolutePos(origin);
        helper.assertTrue(helper.getLevel().getBlockState(world.east()).is(Blocks.GOLD_BLOCK), "朝东时北向的「前方」应摆在东边");
        helper.assertTrue(arrow.match(helper.getLevel(), world, Direction.EAST).matches(), "按东判定应 MATCH");
        helper.assertTrue(arrow.match(helper.getLevel(), world, Direction.NORTH).result() == StructureResult.MISMATCH, "按北判定应 MISMATCH");
        helper.succeed();
    }

    /** 太阳棱镜：隔一格的任意方块 + 紧挨着 8 格空气；斜角放了方块就不行，隔一格换成别的方块照样行 */
    @GameTest(template = EMPTY)
    public static void prismAirAndAnyBlock(GameTestHelper helper) {
        build(helper, PRISM_LIKE, PRISM_ORIGIN, Direction.NORTH);
        BlockPos origin = helper.absolutePos(PRISM_ORIGIN);
        helper.assertTrue(PRISM_LIKE.match(helper.getLevel(), origin, Direction.NORTH).matches(), "摆好后应 MATCH");

        helper.setBlock(PRISM_ORIGIN.offset(2, 0, 0), Blocks.COBBLESTONE);
        helper.setBlock(PRISM_ORIGIN.offset(0, 0, 2), Blocks.OAK_PLANKS);
        helper.assertTrue(PRISM_LIKE.match(helper.getLevel(), origin, Direction.NORTH).matches(), "隔一格换成任意方块都应 MATCH");

        helper.setBlock(PRISM_ORIGIN.offset(1, 0, 1), Blocks.DIRT);
        StructurePattern.Match match = PRISM_LIKE.match(helper.getLevel(), origin, Direction.NORTH);
        helper.assertTrue(match.result() == StructureResult.MISMATCH && match.mismatch() != null && match.mismatch().rule().isAir(),
                "紧挨着的斜角放了方块应 MISMATCH（空气规则），实际 " + match);

        helper.setBlock(PRISM_ORIGIN.offset(1, 0, 1), Blocks.AIR);
        helper.setBlock(PRISM_ORIGIN.offset(-2, 0, 0), Blocks.AIR);
        match = PRISM_LIKE.match(helper.getLevel(), origin, Direction.NORTH);
        helper.assertTrue(match.result() == StructureResult.MISMATCH && match.mismatch() != null && !match.mismatch().rule().isAir(),
                "隔一格缺了方块应 MISMATCH（任意方块规则），实际 " + match);
        helper.succeed();
    }

    /** 标签规则认标签里的任意方块；谓词规则看方块状态（炼药锅要满 3 格水） */
    @GameTest(template = EMPTY)
    public static void tagAndPredicateRules(GameTestHelper helper) {
        StructurePattern pattern = StructurePattern.builder()
                .at(0, 0, 0, StructurePattern.Rule.block(() -> Blocks.LODESTONE))
                .at(1, 0, 0, StructurePattern.Rule.tag(BlockTags.LOGS, Blocks.OAK_LOG::defaultBlockState, "structure.essentialcraft5.rule.any_block"))
                .at(-1, 0, 0, StructurePattern.Rule.predicate(
                        (level, pos, state) -> state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) == 3,
                        () -> Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3),
                        "structure.essentialcraft5.rule.any_block"))
                .build();
        BlockPos origin = new BlockPos(2, 1, 2);
        helper.setBlock(origin, Blocks.LODESTONE);
        helper.setBlock(origin.east(), Blocks.BIRCH_LOG);
        BlockState oneLevel = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1);
        helper.setBlock(origin.west(), oneLevel);
        BlockPos world = helper.absolutePos(origin);
        StructurePattern.Match match = pattern.match(helper.getLevel(), world, Direction.NORTH);
        helper.assertTrue(match.result() == StructureResult.MISMATCH && match.mismatch() != null && match.mismatch().pos().equals(world.west()),
                "炼药锅只有 1 格水应不符，实际 " + match);
        helper.setBlock(origin.west(), oneLevel.setValue(LayeredCauldronBlock.LEVEL, 3));
        helper.assertTrue(pattern.match(helper.getLevel(), world, Direction.NORTH).matches(), "白桦原木算原木、满水炼药锅应 MATCH");
        helper.assertTrue(pattern.layers().size() == 1 && pattern.layers().get(0).width() == 3, "只有一层、宽 3");
        helper.succeed();
    }

    /** 有格子落在没加载的区块里：已加载的都对 → UNKNOWN；已加载的有不符 → MISMATCH（不因为别处没加载就不报） */
    @GameTest(template = EMPTY)
    public static void unloadedChunkIsUnknown(GameTestHelper helper) {
        StructurePattern far = StructurePattern.builder()
                .at(0, 0, 0, StructurePattern.Rule.block(() -> Blocks.GOLD_BLOCK))
                .at(200000, 0, 0, StructurePattern.Rule.block(() -> Blocks.GOLD_BLOCK))
                .build();
        BlockPos origin = new BlockPos(2, 1, 2);
        BlockPos world = helper.absolutePos(origin);
        helper.assertTrue(far.match(helper.getLevel(), world, Direction.NORTH).result() == StructureResult.MISMATCH,
                "原点是空气时应 MISMATCH");
        helper.setBlock(origin, Blocks.GOLD_BLOCK);
        helper.assertTrue(far.match(helper.getLevel(), world, Direction.NORTH).result() == StructureResult.UNKNOWN,
                "20 万格外的区块没加载，应 UNKNOWN");
        helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk((world.getX() + 200000) >> 4, world.getZ() >> 4),
                "判定不应把远处的区块加载起来");
        helper.succeed();
    }

    // ================================================================ 缓存

    /** 标脏后 20 tick 内不重算；到点后重算；没标脏、复查间隔很长的缓存不重算，标脏后才重算 */
    @GameTest(template = EMPTY_LARGE)
    public static void cacheThrottles(GameTestHelper helper) {
        build(helper, FURNACE_LIKE, FURNACE_ORIGIN, Direction.NORTH);
        BlockPos origin = helper.absolutePos(FURNACE_ORIGIN);
        StructureCache periodic = new StructureCache(FURNACE_LIKE);
        StructureCache lazy = new StructureCache(FURNACE_LIKE, Integer.MAX_VALUE);
        helper.assertTrue(periodic.check(helper.getLevel(), origin, Direction.NORTH) == StructureResult.MATCH, "第一次判定应立即算出 MATCH");
        helper.assertTrue(lazy.check(helper.getLevel(), origin, Direction.NORTH) == StructureResult.MATCH, "第一次判定应立即算出 MATCH");
        helper.assertTrue(periodic.formed() && lazy.formed(), "MATCH 后 formed 为真");

        helper.setBlock(FURNACE_ORIGIN.offset(-2, -1, 0), Blocks.AIR);
        periodic.markDirty();
        helper.assertTrue(periodic.check(helper.getLevel(), origin, Direction.NORTH) == StructureResult.MATCH,
                "标脏后 20 tick 内应仍返回缓存的 MATCH");

        helper.runAtTickTime(25, () -> {
            helper.assertTrue(periodic.check(helper.getLevel(), origin, Direction.NORTH) == StructureResult.MISMATCH, "过了 20 tick 应重算出 MISMATCH");
            helper.assertTrue(!periodic.formed() && periodic.mismatch() != null
                    && periodic.mismatch().pos().equals(helper.absolutePos(FURNACE_ORIGIN.offset(-2, -1, 0))), "应记下不符的那格");
            helper.assertTrue(lazy.check(helper.getLevel(), origin, Direction.NORTH) == StructureResult.MATCH, "没标脏的长间隔缓存不应重算");
            lazy.markDirty();
            helper.assertTrue(lazy.check(helper.getLevel(), origin, Direction.NORTH) == StructureResult.MISMATCH, "标脏且过了 20 tick 应重算");
            helper.assertTrue(periodic.checkNow(helper.getLevel(), origin, Direction.NORTH) == StructureResult.MISMATCH, "checkNow 不受节流");
            helper.succeed();
        });
    }

    /** 读档得到的 MATCH：区块没加载（UNKNOWN）时 formed 仍为真；存盘只存确定的结果 */
    @GameTest(template = EMPTY)
    public static void cacheKeepsLastKnownWhenUnloaded(GameTestHelper helper) {
        StructureCache cache = new StructureCache(PRISM_LIKE);
        helper.assertTrue(cache.status() == StructureResult.UNKNOWN && !cache.formed(), "没判定过时是 UNKNOWN、未成形");
        CompoundTag saved = new CompoundTag();
        cache.save(saved);
        helper.assertTrue(!saved.contains(StructureCache.NBT_KEY), "没确定过的结果不存盘");

        saved.putByte(StructureCache.NBT_KEY, (byte) StructureResult.MATCH.ordinal());
        cache.load(saved);
        helper.assertTrue(cache.formed(), "读档后应沿用存下的 MATCH");
        BlockPos unloaded = helper.absolutePos(PRISM_ORIGIN).offset(200000, 0, 0);
        helper.assertTrue(cache.check(helper.getLevel(), unloaded, Direction.NORTH) == StructureResult.UNKNOWN, "区块没加载应 UNKNOWN");
        helper.assertTrue(cache.formed() && cache.lastKnown() == StructureResult.MATCH, "UNKNOWN 不应改掉上次确定的结果");

        CompoundTag again = new CompoundTag();
        cache.save(again);
        helper.assertTrue(again.getByte(StructureCache.NBT_KEY) == StructureResult.MATCH.ordinal(), "存盘应仍是 MATCH");

        // 在已加载的地方判定出 MISMATCH 后，formed 跟着变
        cache.checkNow(helper.getLevel(), helper.absolutePos(PRISM_ORIGIN), Direction.NORTH);
        helper.assertTrue(!cache.formed() && cache.lastKnown() == StructureResult.MISMATCH, "空地上应判定为 MISMATCH");
        helper.succeed();
    }

    // ================================================================ 登记表与构造检查

    /** 按 id 登记、查找、反查；同 id 登记另一个结构抛异常，同一个对象重复登记不抛（GameTest 可能重跑） */
    @GameTest(template = EMPTY)
    public static void registryLookup(GameTestHelper helper) {
        ResourceLocation id = EssentialCraft5.id("test/furnace_like");
        helper.assertTrue(StructurePatterns.register(id, FURNACE_LIKE) == FURNACE_LIKE, "登记应原样返回");
        StructurePatterns.register(id, FURNACE_LIKE);
        helper.assertTrue(StructurePatterns.get(id) == FURNACE_LIKE, "应能按 id 查到");
        helper.assertTrue(id.equals(StructurePatterns.idOf(FURNACE_LIKE)), "应能反查 id");
        helper.assertTrue(StructurePatterns.ids().contains(id), "ids 应包含它");
        boolean threw = false;
        try {
            StructurePatterns.register(id, PRISM_LIKE);
        } catch (IllegalStateException e) {
            threw = true;
        }
        helper.assertTrue(threw, "同一个 id 登记另一个结构应抛异常");
        helper.assertTrue(StructurePatterns.get(EssentialCraft5.id("test/nothing")) == null, "没登记的 id 查不到");
        helper.succeed();
    }

    /** 写错的结构在 build() 时就抛异常：字符没定义、原点缺失或重复、层大小不一 */
    @GameTest(template = EMPTY)
    public static void builderRejectsBadPatterns(GameTestHelper helper) {
        assertThrows(helper, "字符没定义", () -> StructurePattern.builder()
                .where('M', StructurePattern.Rule.air()).anchor('M').layer("MX").build());
        assertThrows(helper, "没有原点", () -> StructurePattern.builder()
                .where('A', StructurePattern.Rule.air()).layer("A").build());
        assertThrows(helper, "原点重复", () -> StructurePattern.builder()
                .where('A', StructurePattern.Rule.air()).anchor('M').layer("MAM").build());
        assertThrows(helper, "层大小不一", () -> StructurePattern.builder()
                .where('A', StructurePattern.Rule.air()).anchor('M').layer("AMA").layer("AA").build());
        assertThrows(helper, "同一格写两次", () -> StructurePattern.builder()
                .where('A', StructurePattern.Rule.air()).anchor('M').layer("AM").at(-1, 0, 0, StructurePattern.Rule.anyBlock()).build());
        assertThrows(helper, "一格都没有", () -> StructurePattern.builder().build());
        helper.succeed();
    }

    private static void assertThrows(GameTestHelper helper, String what, Runnable build) {
        boolean threw = false;
        try {
            build.run();
        } catch (IllegalStateException | IllegalArgumentException e) {
            threw = true;
        }
        helper.assertTrue(threw, what + "时应抛异常");
    }

    /** 大模板 empty_large 是 11×8×11 的空气（给富集仓、米斯莱熔炉、太阳棱镜的测试用） */
    @GameTest(template = EMPTY_LARGE)
    public static void largeTemplateIsEmpty(GameTestHelper helper) {
        // 相对坐标 (0,0,0) 是 GameTest 放的结构方块（StructureUtils.spawnStructure），模板从它上方一格起（StructureBlockEntity 默认偏移 0,1,0）
        for (BlockPos pos : List.of(new BlockPos(0, 1, 0), new BlockPos(10, 7, 10), new BlockPos(5, 3, 5), new BlockPos(10, 1, 0))) {
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(pos)).isAir(), pos + " 应是空气");
        }
        helper.succeed();
    }
}
