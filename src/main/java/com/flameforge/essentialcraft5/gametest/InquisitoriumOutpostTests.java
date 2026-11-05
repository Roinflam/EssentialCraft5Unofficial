package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.GlitchyCoreDecoder;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.NestCoreBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RadiatingChamberBlockEntity;
import com.flameforge.essentialcraft5.item.GlitchyCoreItem;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECInquisitoriumOutpost;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.InquisitoriumOutpostStructure;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.OutpostCensus;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.OutpostCommands;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.OutpostPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.OutpostStructures;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.SpreadExcludingPlacement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 第十三批 13e-S3 审判庭前哨与解码器改指（batch13.md §6.4、§7.4；HS §2.4.3）的 GameTest，批 {@value #BATCH}：
 * <ul>
 *   <li>{@code outpostLayout200Seeds}：200 个种子的布局：8 根柱子 2~3 根断、高度范围、在圈上；营地 2 个、离中心 26~30、整个在一个区块里、在场地外、两边相对；整座 ≤ 34 格；</li>
 *   <li>{@code outpostPiecesRoundTripNbt}：4 种拼件（石柱带高度、断没断）存读档相同；</li>
 *   <li>{@code outpostArenaFlattens}：在起伏的地形上放场地：圆盘内 ≥ 95% 的列地面 = 场地面；有方块实体的格子、非天然方块、更高的山顶不动；地基最多补 8 格；圆外不动；缺损约 15%；</li>
 *   <li>{@code placeOutpostEndToEnd}：照 /place 在远处超平坦放一座：信标座 25 格、光源数 = 完好的柱子数、2 个箱子（chests/inquisitorium_outpost）、2 个巢穴核心（outpost_camp）、
 *       改动都在包围盒里、按区块正序倒序放结果相同；</li>
 *   <li>{@code outpostForecastMatchesGeneration}：固定种子的海纳尔上，预测的起点照真生成都能布出来、群系不对的候选都布不出来（预测 = 实际）；</li>
 *   <li>{@code outpostExclusionCensus}：4 096 × 4 096 区块里预测的前哨、宝库、城镇两两（切比雪夫）&gt; 8 区块；结构集 JSON 的 salt 和代码常量一致；</li>
 *   <li>{@code outpostNotInOverworld}：原版主世界（同种子手建）的结构放置状态里没有前哨的结构集；</li>
 *   <li>{@code outpostDecoderFindsNearest}：解码器找坐标先找前哨：起点附近有预测前哨时，结果 = 最近一座的中心（信标座）；</li>
 *   <li>{@code outpostDecoderFallsBack}：前哨一座都对不上（或配置关掉）时退回暗影腐化之地的群系点，结果和只找群系的一样；</li>
 *   <li>{@code outpostDecoderSplitsAcrossTicks}：辐射仓解码时全服预算每次只给几个点：分很多刻做完、每刻不超预算，最后坐标 = 一口气找完的结果；</li>
 *   <li>{@code outpostCensusRuns}：普查（小窗口）数的起点 = 直接预测的个数，归片的加没归片的 = 总数。</li>
 * </ul>
 * 端到端和场地测试在超平坦主世界远处的绝对坐标上放（不在测试结构里），放完还原。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class InquisitoriumOutpostTests {

    public static final String BATCH = "inquisitorium_outpost";
    private static final String EMPTY = "empty";
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final ChunkPos ARENA_CHUNK = new ChunkPos(-2611, 2603);
    private static final ChunkPos PLACE_CHUNK = new ChunkPos(2617, -2609);
    private static final BlockPos CHAMBER = new BlockPos(2, 1, 2);
    private static final ResourceKey<StructureSet> TOWN_SET = ResourceKey.create(Registries.STRUCTURE_SET, EssentialCraft5.id("hoanna_town"));
    private static final ResourceKey<StructureSet> VAULT_SET = ResourceKey.create(Registries.STRUCTURE_SET, EssentialCraft5.id("sealed_vault"));

    private InquisitoriumOutpostTests() {
    }

    // ================================================================== 开发指令

    /**
     * {@code /ec5 dev hoanna_census} 的点数上限：默认参数（2 048 / 4）正好到上限、能跑；边长 8 192、步长 1（6 700 万点）直接报错返回 0，
     * 不在主线程上采样（整条指令不到 1 秒）；权限不够的执行者连指令都找不到
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void censusCommandCapsPoints(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(OutpostCommands.points(OutpostCommands.DEFAULT_SIZE, OutpostCommands.DEFAULT_STEP) == OutpostCommands.MAX_POINTS,
                "默认参数正好是上限");
        helper.assertTrue(OutpostCommands.points(8192, 1) > OutpostCommands.MAX_POINTS && OutpostCommands.points(8192, 16) <= OutpostCommands.MAX_POINTS,
                "8192 / 1 超上限，8192 / 16 不超");
        List<String> said = new ArrayList<>();
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(message.toString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack console = server.createCommandSourceStack().withSource(sink);
        long started = System.nanoTime();
        int result = server.getCommands().performPrefixedCommand(console, "ec5 dev hoanna_census 1 8192 1");
        long millis = (System.nanoTime() - started) / 1_000_000L;
        helper.assertTrue(result == 0 && millis < 1000L, "超上限应直接报错：返回 " + result + "、用了 " + millis + " ms");
        helper.assertTrue(said.stream().anyMatch(text -> text.contains(OutpostCommands.KEY + "too_many_points")), "报的是点数太多：" + said);
        CommandSourceStack weak = server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        helper.assertTrue(server.getCommands().performPrefixedCommand(weak, "ec5 dev hoanna_census 1 64 64") == 0, "权限 2 不能用开发指令");
        helper.succeed();
    }

    // ================================================================== 布局与拼件

    @GameTest(template = EMPTY, batch = BATCH)
    public static void outpostLayout200Seeds(GameTestHelper helper) {
        ChunkPos chunk = new ChunkPos(37, -91);
        int cx = InquisitoriumOutpostStructure.centerX(chunk);
        int cz = InquisitoriumOutpostStructure.centerZ(chunk);
        int fallbacks = 0;
        for (long i = 1; i <= 200; i++) {
            long seed = i * SEED_STEP;
            InquisitoriumOutpostStructure.Plan plan = InquisitoriumOutpostStructure.plan(random(seed, chunk), chunk, 80);
            helper.assertTrue(plan.centerX() == chunk.getMiddleBlockX() && plan.centerZ() == chunk.getMiddleBlockZ() && plan.floorY() == 79,
                    "中心 = 起点区块中心、场地面 = 地面 − 1");
            helper.assertTrue(plan.pylons().size() == 8, "8 根柱子");
            int broken = 0;
            for (InquisitoriumOutpostStructure.PylonSpec pylon : plan.pylons()) {
                if (pylon.broken()) {
                    broken++;
                    helper.assertTrue(pylon.height() >= 2 && pylon.height() <= 4, "断柱高 2~4：" + pylon);
                } else {
                    helper.assertTrue(pylon.height() >= 6 && pylon.height() <= 12, "完好的柱子高 6~12：" + pylon);
                }
                double d = Math.hypot(pylon.x() + 1 - (cx + 0.5D), pylon.z() + 1 - (cz + 0.5D));
                helper.assertTrue(d >= 13.5D && d <= 16.5D, "柱子在半径 15 的圈上：" + pylon + " 离中心 " + d);
                for (int x = pylon.x(); x <= pylon.x() + 1; x++) {
                    for (int z = pylon.z(); z <= pylon.z() + 1; z++) {
                        helper.assertTrue((x - cx) * (x - cx) + (z - cz) * (z - cz) <= 18.5D * 18.5D, "柱子在场地圆盘里");
                        helper.assertTrue(Math.abs(x - cx) > 2 || Math.abs(z - cz) > 2, "柱子不压信标座");
                    }
                }
            }
            helper.assertTrue(broken == 2 || broken == 3, "2~3 根断的，实际 " + broken);
            for (int a = 0; a < 8; a++) {
                for (int b = a + 1; b < 8; b++) {
                    InquisitoriumOutpostStructure.PylonSpec p = plan.pylons().get(a);
                    InquisitoriumOutpostStructure.PylonSpec q = plan.pylons().get(b);
                    helper.assertTrue(Math.abs(p.x() - q.x()) >= 2 || Math.abs(p.z() - q.z()) >= 2, "柱子互不重叠");
                }
            }
            helper.assertTrue(plan.camps().size() == 2, "2 个营地");
            double[] angles = new double[2];
            for (int k = 0; k < 2; k++) {
                InquisitoriumOutpostStructure.CampSpec camp = plan.camps().get(k);
                double d = Math.hypot(camp.x() - cx, camp.z() - cz);
                helper.assertTrue(InquisitoriumOutpostStructure.campFitsChunk(camp.x(), camp.z()), "营地整个在一个区块里：" + camp);
                if (d < 26D || d > 30D) {
                    fallbacks++;
                }
                helper.assertTrue(d - 3D * Math.sqrt(2D) > 18.5D, "营地在场地外：离中心 " + d);
                helper.assertTrue(d + InquisitoriumOutpostStructure.CAMP_HALF <= InquisitoriumOutpostStructure.MAX_RADIUS, "营地在 34 格内：离中心 " + d);
                angles[k] = Math.atan2(camp.z() - cz, camp.x() - cx);
            }
            double gap = Math.abs(angles[0] - angles[1]);
            gap = Math.min(gap, 2D * Math.PI - gap);
            helper.assertTrue(gap >= Math.PI / 2D, "两个营地大致在相对的两侧，夹角 " + Math.toDegrees(gap));
            List<HoannaStructurePiece> pieces = InquisitoriumOutpostStructure.createPieces(plan);
            helper.assertTrue(pieces.size() == 12, "1 场地 + 1 信标座 + 8 柱子 + 2 营地");
            for (HoannaStructurePiece piece : pieces) {
                BoundingBox box = piece.getBoundingBox();
                int reach = Math.max(Math.max(Math.abs(box.minX() - cx), Math.abs(box.maxX() - cx)), Math.max(Math.abs(box.minZ() - cz), Math.abs(box.maxZ() - cz)));
                helper.assertTrue(reach <= InquisitoriumOutpostStructure.MAX_RADIUS, "件 " + piece + " 超出中心 34 格");
                helper.assertTrue(piece.groundY() == 80, "各件地面都是中心那一列");
            }
            helper.assertTrue(pieces.get(0) instanceof OutpostPieces.Arena && pieces.get(1) instanceof OutpostPieces.Dais, "先场地、再信标座");
        }
        helper.assertTrue(fallbacks <= 4, "营地挑不到 26~30 格又整块在区块里、要兜底挪的应很少，实际 " + fallbacks);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void outpostPiecesRoundTripNbt(GameTestHelper helper) {
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(helper.getLevel());
        ChunkPos chunk = new ChunkPos(5, 7);
        InquisitoriumOutpostStructure.Plan plan = InquisitoriumOutpostStructure.plan(random(3 * SEED_STEP, chunk), chunk, 70);
        List<HoannaStructurePiece> pieces = InquisitoriumOutpostStructure.createPieces(plan);
        for (HoannaStructurePiece piece : pieces) {
            CompoundTag tag = piece.createTag(context);
            StructurePieceType type = net.minecraft.core.registries.BuiltInRegistries.STRUCTURE_PIECE.get(net.minecraft.resources.ResourceLocation.tryParse(tag.getString("id")));
            helper.assertTrue(type != null, "拼件类型没注册：" + tag.getString("id"));
            StructurePiece copy = Objects.requireNonNull(type).load(context, tag);
            helper.assertTrue(copy.getClass() == piece.getClass() && copy.getBoundingBox().equals(piece.getBoundingBox()), "读回的类 / 包围盒不同：" + piece);
            helper.assertTrue(copy.createTag(context).equals(tag), "再存一次 NBT 应完全相同：" + piece);
            if (piece instanceof OutpostPieces.Pylon pylon) {
                OutpostPieces.Pylon read = (OutpostPieces.Pylon) copy;
                helper.assertTrue(read.height() == pylon.height() && read.broken() == pylon.broken(), "石柱的高度 / 断没断读回不同");
            }
        }
        helper.succeed();
    }

    // ================================================================== 场地削平

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void outpostArenaFlattens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int cx = ARENA_CHUNK.getMiddleBlockX();
        int cz = ARENA_CHUNK.getMiddleBlockZ();
        int floor = 100;
        BoundingBox region = new BoundingBox(cx - 22, floor - 16, cz - 22, cx + 22, floor + 24, cz + 22);
        loadChunks(level, region);
        Map<BlockPos, BlockState> before = snapshot(level, region);
        try {
            // 地形：场地面以下石头、面上草方块；东南一块山（高出 10）、一根高出 20 的石尖、一棵树、一个 12 深的坑、一小片水；
            // 另摆：埋在山里的箱子（方块实体）、地上的箱子、黑曜石和玻璃（非天然）
            fill(level, region, Blocks.AIR.defaultBlockState());
            fill(level, new BoundingBox(cx - 22, floor - 14, cz - 22, cx + 22, floor - 1, cz + 22), Blocks.STONE.defaultBlockState());
            fill(level, new BoundingBox(cx - 22, floor, cz - 22, cx + 22, floor, cz + 22), Blocks.GRASS_BLOCK.defaultBlockState());
            fill(level, new BoundingBox(cx + 5, floor + 1, cz + 5, cx + 22, floor + 10, cz + 22), Blocks.DIRT.defaultBlockState());
            fill(level, new BoundingBox(cx - 10, floor + 1, cz - 3, cx - 9, floor + 20, cz - 2), Blocks.STONE.defaultBlockState());
            fill(level, new BoundingBox(cx - 5, floor + 1, cz + 8, cx - 5, floor + 5, cz + 8), Blocks.OAK_LOG.defaultBlockState());
            fill(level, new BoundingBox(cx - 7, floor + 4, cz + 6, cx - 3, floor + 6, cz + 10), Blocks.OAK_LEAVES.defaultBlockState());
            fill(level, new BoundingBox(cx - 5, floor + 1, cz + 8, cx - 5, floor + 5, cz + 8), Blocks.OAK_LOG.defaultBlockState());
            fill(level, new BoundingBox(cx - 8, floor - 12, cz - 12, cx - 4, floor, cz - 8), Blocks.AIR.defaultBlockState());
            fill(level, new BoundingBox(cx + 10, floor - 1, cz - 10, cx + 12, floor, cz - 8), Blocks.WATER.defaultBlockState());
            BlockPos buried = new BlockPos(cx + 8, floor + 4, cz + 8);
            BlockPos onFloor = new BlockPos(cx + 6, floor + 1, cz + 2);
            BlockPos obsidian = new BlockPos(cx - 2, floor + 2, cz + 10);
            BlockPos glass = new BlockPos(cx + 12, floor + 1, cz - 3);
            level.setBlock(buried, Blocks.CHEST.defaultBlockState(), QUIET);
            level.setBlock(onFloor, Blocks.CHEST.defaultBlockState(), QUIET);
            level.setBlock(obsidian, Blocks.OBSIDIAN.defaultBlockState(), QUIET);
            level.setBlock(glass, Blocks.GLASS.defaultBlockState(), QUIET);

            OutpostPieces.Arena arena = new OutpostPieces.Arena(0, new BoundingBox(cx - 18, floor - 8, cz - 18, cx + 18, floor + 16, cz + 18), 0x51ED270BL);
            arena.setGroundY(floor + 1);
            BoundingBox chunkBox = new BoundingBox(region.minX(), level.getMinBuildHeight(), region.minZ(), region.maxX(), level.getMaxBuildHeight() - 1, region.maxZ());
            arena.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), chunkBox, ARENA_CHUNK, BlockPos.ZERO);

            int columns = 0;
            int flat = 0;
            int tiles = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = cx - 18; x <= cx + 18; x++) {
                for (int z = cz - 18; z <= cz + 18; z++) {
                    if (!arena.inDisc(x, z)) {
                        continue;
                    }
                    columns++;
                    boolean solid = !level.getBlockState(pos.set(x, floor, z)).isAir() && level.getFluidState(pos).isEmpty();
                    boolean clear = true;
                    for (int y = floor + 1; y <= floor + 16 && clear; y++) {
                        clear = level.getBlockState(pos.set(x, y, z)).isAir();
                    }
                    if (solid && clear) {
                        flat++;
                    }
                    BlockState tile = level.getBlockState(pos.set(x, floor, z));
                    if (tile.is(ECMaterials.FORTIFIED_STONE.get()) || tile.equals(HoannaStructureBlocks.voidStone())) {
                        tiles++;
                    }
                }
            }
            helper.assertTrue(flat >= columns * 0.95D, "圆盘内 ≥ 95% 的列地面 = 场地面：" + flat + " / " + columns);
            double tileShare = tiles / (double) columns;
            helper.assertTrue(tileShare >= 0.75D && tileShare <= 0.93D, "地板铺了约 85%（缺损约 15%），实际 " + tileShare);
            helper.assertTrue(level.getBlockState(buried).is(Blocks.CHEST) && level.getBlockEntity(buried) instanceof ChestBlockEntity, "埋在山里的箱子不动");
            helper.assertTrue(level.getBlockState(onFloor).is(Blocks.CHEST), "地上的箱子不动");
            helper.assertTrue(level.getBlockState(obsidian).is(Blocks.OBSIDIAN) && level.getBlockState(glass).is(Blocks.GLASS), "黑曜石、玻璃（非天然）不动");
            helper.assertTrue(level.getBlockState(new BlockPos(cx - 10, floor + 16, cz - 3)).isAir()
                    && level.getBlockState(new BlockPos(cx - 10, floor + 17, cz - 3)).is(Blocks.STONE), "石尖削到场地面上 16 格，更高的照留");
            helper.assertTrue(level.getBlockState(new BlockPos(cx - 5, floor + 3, cz + 8)).isAir() && level.getBlockState(new BlockPos(cx - 5, floor + 5, cz + 6)).isAir(),
                    "树（原木、树叶）削掉");
            for (int y = floor - 1; y >= floor - 8; y--) {
                helper.assertTrue(level.getBlockState(new BlockPos(cx - 6, y, cz - 10)).is(ECMaterials.FORTIFIED_STONE.get()), "坑里往下补强化石地基：y " + y);
            }
            helper.assertTrue(level.getBlockState(new BlockPos(cx - 6, floor - 9, cz - 10)).isAir(), "地基最多补 8 格");
            helper.assertTrue(level.getFluidState(new BlockPos(cx + 11, floor, cz - 9)).isEmpty() && level.getFluidState(new BlockPos(cx + 11, floor - 1, cz - 9)).isEmpty(),
                    "水面铺上地板、下面一格补地基");
            helper.assertTrue(level.getBlockState(new BlockPos(cx + 17, floor + 5, cz + 17)).is(Blocks.DIRT), "圆盘外（角上）的山不动");
            helper.assertTrue(level.getBlockState(new BlockPos(cx + 20, floor + 5, cz + 20)).is(Blocks.DIRT), "包围盒外的山不动");
        } finally {
            restore(level, before);
        }
        helper.succeed();
    }

    // ================================================================== 端到端

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 600)
    public static void placeOutpostEndToEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(OutpostStructures.STRUCTURE);
        helper.assertTrue(structure instanceof InquisitoriumOutpostStructure, "结构没加载或类型不对：" + structure);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        long seed = 11 * SEED_STEP;
        StructureStart start = Objects.requireNonNull(structure).generate(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), seed, PLACE_CHUNK, 0, level, biome -> true);
        helper.assertTrue(start.isValid(), "放置失败：结构起点无效");
        BoundingBox bounds = start.getBoundingBox();
        int cx = PLACE_CHUNK.getMiddleBlockX();
        int cz = PLACE_CHUNK.getMiddleBlockZ();
        helper.assertTrue(Math.max(Math.max(cx - bounds.minX(), bounds.maxX() - cx), Math.max(cz - bounds.minZ(), bounds.maxZ() - cz)) <= 34,
                "包围盒离中心不超过 34 格：" + bounds);
        int intact = 0;
        int ground = Integer.MIN_VALUE;
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof OutpostPieces.Pylon pylon && !pylon.broken()) {
                intact++;
            }
            if (piece instanceof OutpostPieces.Arena arena) {
                ground = arena.groundY();
            }
        }
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(bounds.minX()), SectionPos.blockToSectionCoord(bounds.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(bounds.maxX()), SectionPos.blockToSectionCoord(bounds.maxZ()));
        List<ChunkPos> chunks = new ArrayList<>();
        ChunkPos.rangeClosed(min, max).forEach(chunks::add);
        BoundingBox region = new BoundingBox(bounds.minX() - 1, Math.max(level.getMinBuildHeight(), bounds.minY()), bounds.minZ() - 1,
                bounds.maxX() + 1, Math.min(bounds.maxY() + 1, level.getMaxBuildHeight() - 1), bounds.maxZ() + 1);
        loadChunks(level, region);
        Map<BlockPos, BlockState> original = snapshot(level, region);
        try {
            for (ChunkPos chunk : chunks) {
                placeInChunk(level, generator, start, chunk);
            }
            int floor = ground - 1;
            int dais = 0;
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    if (level.getBlockState(new BlockPos(x, floor + 1, z)).is(ECInquisitoriumOutpost.INQUISITORIUM_DAIS.get())) {
                        dais++;
                    }
                }
            }
            helper.assertTrue(dais == 25, "中央 5 × 5 信标座，实际 " + dais);
            helper.assertTrue(level.getBlockState(new BlockPos(cx, floor + 2, cz)).isAir() && level.canSeeSky(new BlockPos(cx, floor + 2, cz)),
                    "信标座中心上面是空的、露天（召哨兵的条件）");
            int lights = 0;
            int chests = 0;
            int nests = 0;
            Map<BlockPos, BlockState> changes = changes(level, original);
            for (Map.Entry<BlockPos, BlockState> change : changes.entrySet()) {
                BlockPos pos = change.getKey();
                helper.assertTrue(bounds.isInside(pos.getX(), Math.max(bounds.minY(), Math.min(bounds.maxY(), pos.getY())), pos.getZ()),
                        "改动的 " + pos + " 在包围盒 " + bounds + " 外面");
                if (change.getValue().is(ECEssence.MAGIC_LIGHT.get())) {
                    lights++;
                }
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof ChestBlockEntity && OutpostPieces.CHEST_LOOT.toString().equals(be.saveWithoutMetadata().getString("LootTable"))) {
                    chests++;
                }
                if (be instanceof NestCoreBlockEntity core && OutpostPieces.CAMP_NEST.equals(core.typeId())) {
                    nests++;
                }
            }
            helper.assertTrue(lights == intact, "完好的柱子各一个魔力光源：完好 " + intact + "，光源 " + lights);
            helper.assertTrue(chests == 2, "两个营地各一个 chests/inquisitorium_outpost 箱子，实际 " + chests);
            helper.assertTrue(nests == 2, "两个营地各一个 outpost_camp 巢穴核心，实际 " + nests);
            Map<BlockPos, Block> forward = blocks(changes);
            restore(level, original);
            for (int i = chunks.size() - 1; i >= 0; i--) {
                placeInChunk(level, generator, start, chunks.get(i));
            }
            Map<BlockPos, Block> reverse = blocks(changes(level, original));
            helper.assertTrue(reverse.equals(forward), "按区块正序、倒序放的结果应逐格相同：正序 " + forward.size() + " 格，倒序 " + reverse.size() + " 格");
        } finally {
            restore(level, original);
        }
        helper.succeed();
    }

    // ================================================================== 预测与排除

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void outpostForecastMatchesGeneration(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        StructureForecast.World world = world(hoanna);
        StructureForecast.Target target = outpostTarget(server, world);
        Structure structure = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(OutpostStructures.STRUCTURE);
        DimensionType type = Objects.requireNonNull(hoanna.stem()).type().value();
        LevelHeightAccessor height = LevelHeightAccessor.create(type.minY(), type.height());
        List<ChunkPos> predicted = StructureForecast.predictAll(world, target, new ChunkPos(-700, -700), new ChunkPos(700, 700));
        helper.assertTrue(!predicted.isEmpty(), "固定种子的海纳尔 ±700 区块里应有预测的前哨");
        int checked = 0;
        for (ChunkPos chunk : predicted) {
            if (checked++ >= 10) {
                break;
            }
            StructureStart start = generate(server, hoanna, Objects.requireNonNull(structure), chunk, height);
            helper.assertTrue(start.isValid(), "预测的起点 " + chunk + " 照真生成应布得出来");
            helper.assertTrue(start.getPieces().get(0) instanceof OutpostPieces.Arena arena && arena.centerX() == chunk.getMiddleBlockX()
                    && arena.centerZ() == chunk.getMiddleBlockZ(), "场地中心 = 预测坐标（起点区块中心）");
        }
        int rejected = 0;
        int spacing = target.placement().spacing();
        for (int cellX = -20; cellX <= 20 && rejected < 10; cellX++) {
            for (int cellZ = -20; cellZ <= 20 && rejected < 10; cellZ++) {
                ChunkPos chunk = StructureForecast.candidate(world, target, cellX, cellZ);
                if (target.biomes().test(world.biomeAt(StructureForecast.sampleX(chunk), StructureForecast.sampleZ(chunk)))) {
                    continue;
                }
                rejected++;
                helper.assertFalse(generate(server, hoanna, structure, chunk, height).isValid(), "群系不对的候选 " + chunk + " 不该布出来");
            }
        }
        helper.assertTrue(rejected == 10, "测试前提：找到 10 个群系不对的候选（每格 " + spacing + " 区块）");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void outpostExclusionCensus(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server, 7 * SEED_STEP);
        StructureForecast.World world = world(hoanna);
        StructureForecast.Target outpost = outpostTarget(server, world);
        StructureForecast.Target vault = target(server, world, VAULT_SET);
        StructureForecast.Target town = target(server, world, TOWN_SET);
        ChunkPos min = new ChunkPos(-2048, -2048);
        ChunkPos max = new ChunkPos(2047, 2047);
        List<ChunkPos> outposts = StructureForecast.predictAll(world, outpost, min, max);
        List<ChunkPos> others = new ArrayList<>(StructureForecast.predictAll(world, vault, min, max));
        others.addAll(StructureForecast.predictAll(world, town, min, max));
        helper.assertTrue(!outposts.isEmpty(), "4 096² 区块里应有前哨");
        Map<Long, List<ChunkPos>> grid = new HashMap<>();
        for (ChunkPos chunk : others) {
            grid.computeIfAbsent(bucket(chunk), k -> new ArrayList<>()).add(chunk);
        }
        for (ChunkPos chunk : outposts) {
            grid.computeIfAbsent(bucket(chunk), k -> new ArrayList<>()).add(chunk);
        }
        for (ChunkPos chunk : outposts) {
            for (int bx = -1; bx <= 1; bx++) {
                for (int bz = -1; bz <= 1; bz++) {
                    for (ChunkPos other : grid.getOrDefault(ChunkPos.asLong(Math.floorDiv(chunk.x, 16) + bx, Math.floorDiv(chunk.z, 16) + bz), List.of())) {
                        if (other.equals(chunk)) {
                            continue;
                        }
                        int d = Math.max(Math.abs(other.x - chunk.x), Math.abs(other.z - chunk.z));
                        helper.assertTrue(d > 8, "前哨 " + chunk + " 和 " + other + " 只隔 " + d + " 区块");
                    }
                }
            }
        }
        JsonObject json = readJson(server, "worldgen/structure_set/inquisitorium_outpost.json").getAsJsonObject("placement");
        helper.assertTrue(json.get("salt").getAsInt() == OutpostStructures.SALT, "结构集 JSON 的 salt 应等于 OutpostStructures.SALT");
        helper.assertTrue(outpost.placement() instanceof SpreadExcludingPlacement p && p.spacing() == json.get("spacing").getAsInt()
                && p.separation() == json.get("separation").getAsInt(), "注册表里的放置和 JSON 一致");
        EssentialCraft5.LOGGER.info("[审判庭前哨] 排除普查：4096² 区块里前哨 {} 座、宝库 + 城镇 {} 座，两两 > 8 区块", outposts.size(), others.size());
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void outpostNotInOverworld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved overworld = HoannaTestWorld.vanillaOverworld(server, HoannaTestWorld.SEED);
        helper.assertFalse(overworld.structureState().possibleStructureSets().stream().anyMatch(set -> set.is(OutpostStructures.SET)),
                "原版主世界的结构放置状态里不该有前哨的结构集");
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        helper.assertTrue(hoanna.structureState().possibleStructureSets().stream().anyMatch(set -> set.is(OutpostStructures.SET)),
                "海纳尔里有前哨的结构集");
        helper.succeed();
    }

    // ================================================================== 解码器改指

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void outpostDecoderFindsNearest(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        StructureForecast.World world = world(hoanna);
        StructureForecast.Target target = outpostTarget(server, world);
        List<ChunkPos> near = StructureForecast.predictAll(world, target, new ChunkPos(-700, -700), new ChunkPos(700, 700));
        helper.assertTrue(!near.isEmpty(), "测试前提：±700 区块里有预测的前哨");
        GlitchyCoreDecoder.SiteSource source = GlitchyCoreDecoder.SiteSource.of(world, List.of(target));
        int tested = 0;
        for (int k = 0; k < Math.min(3, near.size()); k++) {
            ChunkPos anchor = near.get(k * near.size() / 3);
            int ox = anchor.getMiddleBlockX() + 700;
            int oz = anchor.getMiddleBlockZ() - 400;
            BlockPos expected = nearest(world, target, ox, oz, 3000);
            helper.assertTrue(expected != null, "测试前提：起点 3000 格内有前哨");
            GlitchyCoreDecoder.SiteSearch search = GlitchyCoreDecoder.SiteSearch.start(source, ox, oz, 3000, 64).finish();
            BlockPos found = search.found();
            helper.assertTrue(found != null && search.foundOutpost() && found.getX() == Objects.requireNonNull(expected).getX() && found.getZ() == expected.getZ(),
                    "坐标应是最近一座前哨的中心 " + expected + "，实际 " + found);
            helper.assertTrue(search.outpostSamples() == search.samples() && search.samples() <= 300,
                    "只走了找前哨那一步、≤ 300 个点，实际 " + search.samples());
            ChunkPos chunk = new ChunkPos(Objects.requireNonNull(found));
            helper.assertTrue(InquisitoriumOutpostStructure.centerX(chunk) == found.getX() && InquisitoriumOutpostStructure.centerZ(chunk) == found.getZ(),
                    "坐标 = 信标座中心");
            tested++;
        }
        helper.assertTrue(tested > 0, "至少测了一处");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void outpostDecoderFallsBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        StructureForecast.World world = world(hoanna);
        StructureForecast.Target real = outpostTarget(server, world);
        // 前哨一座都对不上（群系判定恒假）：找前哨那一步走完、没有，退回找暗影腐化之地
        StructureForecast.Target never = new StructureForecast.Target(real.set(), real.placement(), biome -> false);
        GlitchyCoreDecoder.SiteSource biomeOnly = new GlitchyCoreDecoder.SiteSource(world.biomes(), world.sampler());
        int ox = 1234;
        int oz = -5678;
        GlitchyCoreDecoder.SiteSearch plain = GlitchyCoreDecoder.SiteSearch.start(biomeOnly, ox, oz, 3000, 64).finish();
        GlitchyCoreDecoder.SiteSearch fallback = GlitchyCoreDecoder.SiteSearch.start(GlitchyCoreDecoder.SiteSource.of(world, List.of(never)), ox, oz, 3000, 64).finish();
        helper.assertTrue(!fallback.foundOutpost() && Objects.equals(fallback.found(), plain.found()), "退回群系点，和只找群系的一样：" + fallback.found() + " / " + plain.found());
        helper.assertTrue(fallback.outpostSamples() > 0 && fallback.samples() == fallback.outpostSamples() + plain.samples(),
                "两步的点数合起来算：" + fallback.samples() + " = " + fallback.outpostSamples() + " + " + plain.samples());
        GlitchyCoreDecoder.SiteSearch empty = GlitchyCoreDecoder.SiteSearch.start(GlitchyCoreDecoder.SiteSource.of(world, List.of()), ox, oz, 3000, 64).finish();
        helper.assertTrue(Objects.equals(empty.found(), plain.found()) && empty.outpostSamples() == 0, "结构集被删掉（空表）时直接找群系");

        // 辐射仓：配置关掉时不找前哨（起点附近有前哨也一样）
        List<ChunkPos> near = StructureForecast.predictAll(world, real, new ChunkPos(-700, -700), new ChunkPos(700, 700));
        helper.assertTrue(!near.isEmpty(), "测试前提：有预测的前哨");
        RadiatingChamberBlockEntity be = chamber(helper);
        BlockPos origin = be.getBlockPos();
        GlitchyCoreDecoder.SiteSource withOutposts = GlitchyCoreDecoder.SiteSource.of(world, List.of(real));
        try {
            ECInquisitorium.Rules.setTestOverrides(ECInquisitorium.Rules.DEFAULTS.withDecode(10, 1));
            GlitchyCoreDecoder.setTestCandidates(List.of());
            GlitchyCoreDecoder.setTestSiteSource(withOutposts);
            GlitchyCoreDecoder.setTestBudget(1024);
            ECInquisitoriumOutpost.Config.setTestDecoderTargetsOutpost(false);
            InquisitoriumCoordinatesItem.Target off = decode(be);
            BlockPos biomePoint = GlitchyCoreDecoder.SiteSearch.start(biomeOnly, origin.getX(), origin.getZ(), 3000, 64).finish().found();
            helper.assertTrue(off != null && (biomePoint == null || (off.x() == biomePoint.getX() && off.z() == biomePoint.getZ())),
                    "配置关掉：坐标 = 只找群系的结果 " + biomePoint + "，实际 " + off);
            ECInquisitoriumOutpost.Config.setTestDecoderTargetsOutpost(null);
            InquisitoriumCoordinatesItem.Target on = decode(be);
            GlitchyCoreDecoder.SiteSearch expected = GlitchyCoreDecoder.SiteSearch.start(withOutposts, origin.getX(), origin.getZ(), 3000, 64).finish();
            BlockPos want = expected.found();
            helper.assertTrue(on != null && (want == null || (on.x() == want.getX() && on.z() == want.getZ())),
                    "配置开着：坐标 = 先找前哨的结果 " + expected.found() + "，实际 " + on);
        } finally {
            reset(be);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void outpostDecoderSplitsAcrossTicks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        StructureForecast.World world = world(hoanna);
        GlitchyCoreDecoder.SiteSource source = GlitchyCoreDecoder.SiteSource.of(world, List.of(outpostTarget(server, world)));
        RadiatingChamberBlockEntity be = chamber(helper);
        BlockPos origin = be.getBlockPos();
        int perTick = 16;
        try {
            ECInquisitorium.Rules.setTestOverrides(ECInquisitorium.Rules.DEFAULTS.withDecode(10, 1));
            GlitchyCoreDecoder.setTestCandidates(List.of());
            GlitchyCoreDecoder.setTestSiteSource(source);
            be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core());
            int calls = 0;
            int last = 0;
            while (be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).isEmpty() && calls < 2000) {
                GlitchyCoreDecoder.resetBudgetForTest(server, perTick, Long.MAX_VALUE);
                tick(be);
                calls++;
                int now = be.getLocateSamples();
                if (be.getStatus() == RadiatingChamberBlockEntity.Status.LOCATING) {
                    helper.assertTrue(now - last <= perTick, "每刻不超过预算：这一刻看了 " + (now - last));
                    last = now;
                }
            }
            helper.assertTrue(calls > 2, "预算每刻只给 " + perTick + " 个点时应分好几刻做完，实际 " + calls);
            InquisitoriumCoordinatesItem.Target target = InquisitoriumCoordinatesItem.target(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT));
            GlitchyCoreDecoder.SiteSearch expected = GlitchyCoreDecoder.SiteSearch.start(source, origin.getX(), origin.getZ(), 3000, 64).finish();
            BlockPos found = expected.found();
            helper.assertTrue(target != null && (found == null || (target.x() == found.getX() && target.z() == found.getZ())),
                    "分刻做完的坐标 = 一口气找完的 " + found + "，实际 " + target);
        } finally {
            reset(be);
        }
        helper.succeed();
    }

    // ================================================================== 普查

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void outpostCensusRuns(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        StructureForecast.World world = world(hoanna);
        StructureForecast.Target target = outpostTarget(server, world);
        OutpostCensus.Grid grid = OutpostCensus.sample(world, target.biomes(), ChunkPos.ZERO, 1024, 8);
        OutpostCensus.Tally tally = OutpostCensus.tally(world, grid, target);
        helper.assertTrue(grid.patches() > 0 && grid.siteCells() > 0, "1 024² 区块里应有暗影腐化之地");
        int maxX = grid.minX() + grid.side() * grid.step() - 1;
        int maxZ = grid.minZ() + grid.side() * grid.step() - 1;
        int direct = StructureForecast.predictAll(world, target, new ChunkPos(grid.minX(), grid.minZ()), new ChunkPos(maxX, maxZ)).size();
        helper.assertTrue(tally.outposts() == direct, "普查数的起点 = 直接预测的 " + direct + "，实际 " + tally.outposts());
        int assigned = 0;
        int patches = 0;
        for (int i = 0; i < OutpostCensus.BUCKET_FLOORS.length; i++) {
            assigned += tally.bucketOutposts()[i];
            patches += tally.bucketPatches()[i];
        }
        helper.assertTrue(assigned + tally.unassigned() == tally.outposts() && patches == grid.patches(), "归片的 + 没归片的 = 总数，各档片数加起来 = 片数");
        OutpostCensus.Tally sparser = OutpostCensus.tally(world, grid, OutpostCensus.withPlacement(target, 48, 24));
        helper.assertTrue(sparser.candidates() < tally.candidates(), "spacing 加大候选变少");
        EssentialCraft5.LOGGER.info("[审判庭前哨] 小普查：{}", OutpostCensus.describe(grid, tally));
        helper.succeed();
    }

    // ================================================================== 工具

    static WorldgenRandom random(long seed, ChunkPos chunk) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunk.x, chunk.z);
        return random;
    }

    static StructureForecast.World world(HoannaTestWorld.Resolved hoanna) {
        return hoanna.forecast();
    }

    static StructureForecast.Target outpostTarget(MinecraftServer server, StructureForecast.World world) {
        return target(server, world, OutpostStructures.SET);
    }

    static StructureForecast.Target target(MinecraftServer server, StructureForecast.World world, ResourceKey<StructureSet> key) {
        Holder<StructureSet> set = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(key);
        List<StructureForecast.Target> targets = StructureForecast.targets(world, set);
        if (targets.isEmpty()) {
            throw new IllegalStateException("海纳尔里没有结构集 " + key.location());
        }
        return targets.get(0);
    }

    private static StructureStart generate(MinecraftServer server, HoannaTestWorld.Resolved hoanna, Structure structure, ChunkPos chunk,
                                           LevelHeightAccessor height) {
        return structure.generate(server.registryAccess(), hoanna.generator(), hoanna.biomeSource(), hoanna.randomState(),
                server.getStructureManager(), hoanna.seed(), chunk, 0, height, structure.biomes()::contains);
    }

    /** 最近一座预测前哨的中心（水平距离 ≤ radius；没有为 null），同 StructureForecast.Search 的口径 */
    private static BlockPos nearest(StructureForecast.World world, StructureForecast.Target target, int x, int z, int radius) {
        int r = radius / 16 + 2;
        ChunkPos center = new ChunkPos(new BlockPos(x, 0, z));
        BlockPos best = null;
        long bestSq = (long) radius * radius;
        for (ChunkPos chunk : StructureForecast.predictAll(world, target, new ChunkPos(center.x - r, center.z - r), new ChunkPos(center.x + r, center.z + r))) {
            long dx = chunk.getMiddleBlockX() - x;
            long dz = chunk.getMiddleBlockZ() - z;
            long sq = dx * dx + dz * dz;
            if (sq <= bestSq && (best == null || sq < bestSq)) {
                bestSq = sq;
                best = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
            }
        }
        return best;
    }

    private static long bucket(ChunkPos chunk) {
        return ChunkPos.asLong(Math.floorDiv(chunk.x, 16), Math.floorDiv(chunk.z, 16));
    }

    private static JsonObject readJson(MinecraftServer server, String path) {
        Resource resource = server.getResourceManager().getResource(EssentialCraft5.id(path))
                .orElseThrow(() -> new IllegalStateException("数据包里没有 " + path));
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("读 " + path + " 失败", e);
        }
    }

    private static ItemStack core() {
        ItemStack stack = new ItemStack(ECMaterials.GLITCHY_CORE.get());
        GlitchyCoreItem.setTargetBalance(stack, 0.73F);
        return stack;
    }

    private static RadiatingChamberBlockEntity chamber(GameTestHelper helper) {
        helper.setBlock(CHAMBER, ECRadiatingChamber.RADIATING_CHAMBER.get());
        RadiatingChamberBlockEntity be = (RadiatingChamberBlockEntity) helper.getBlockEntity(CHAMBER);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        be.getMRUStorage().setBalance(0.73F);
        return be;
    }

    private static void tick(RadiatingChamberBlockEntity be) {
        MRUBlockEntity.tickServer(be.getLevel(), be.getBlockPos(), be.getBlockState(), be);
    }

    /** 放一个核心解到底（测试口子给足预算），返回坐标并清掉输出槽 */
    private static InquisitoriumCoordinatesItem.Target decode(RadiatingChamberBlockEntity be) {
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core());
        for (int i = 0; i < 200 && be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT).isEmpty(); i++) {
            tick(be);
        }
        InquisitoriumCoordinatesItem.Target target = InquisitoriumCoordinatesItem.target(be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT));
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        return target;
    }

    private static void reset(RadiatingChamberBlockEntity be) {
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, ItemStack.EMPTY);
        be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        ECInquisitorium.Rules.setTestOverrides(null);
        ECInquisitoriumOutpost.Config.setTestDecoderTargetsOutpost(null);
        GlitchyCoreDecoder.setTestCandidates(null);
        GlitchyCoreDecoder.setTestSiteSource(null);
        GlitchyCoreDecoder.setTestSites(null);
        GlitchyCoreDecoder.setTestBudget(null);
    }

    private static void loadChunks(ServerLevel level, BoundingBox region) {
        for (int x = SectionPos.blockToSectionCoord(region.minX()); x <= SectionPos.blockToSectionCoord(region.maxX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(region.minZ()); z <= SectionPos.blockToSectionCoord(region.maxZ()); z++) {
                level.getChunk(x, z);
            }
        }
    }

    private static void fill(ServerLevel level, BoundingBox box, BlockState state) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (level.getBlockState(pos.set(x, y, z)) != state) {
                        level.setBlock(pos, state, QUIET);
                    }
                }
            }
        }
    }

    /** 区域里不是空气的格子 → 方块（还原用；空气不记） */
    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, BoundingBox region) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = region.minY(); y <= region.maxY(); y++) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir()) {
                        states.put(pos.immutable(), state);
                    }
                }
            }
        }
        states.put(new BlockPos(region.minX(), region.minY(), region.minZ()).immutable(), level.getBlockState(new BlockPos(region.minX(), region.minY(), region.minZ())));
        states.put(new BlockPos(region.maxX(), region.maxY(), region.maxZ()).immutable(), level.getBlockState(new BlockPos(region.maxX(), region.maxY(), region.maxZ())));
        return states;
    }

    /** 和快照不一样的格子 → 现在的方块（快照的包围盒是记下的两个角） */
    private static Map<BlockPos, BlockState> changes(ServerLevel level, Map<BlockPos, BlockState> original) {
        BoundingBox region = regionOf(original);
        Map<BlockPos, BlockState> changes = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = region.minY(); y <= region.maxY(); y++) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    BlockState now = level.getBlockState(pos.set(x, y, z));
                    if (now != original.getOrDefault(pos, air)) {
                        changes.put(pos.immutable(), now);
                    }
                }
            }
        }
        return changes;
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> original) {
        BoundingBox region = regionOf(original);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = region.minY(); y <= region.maxY(); y++) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    BlockState want = original.getOrDefault(pos.set(x, y, z), air);
                    if (level.getBlockState(pos) != want) {
                        // 箱子先清空（带战利品表的会先开出来再清掉），拆掉时不往地上掉东西
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be != null) {
                            Clearable.tryClear(be);
                        }
                        level.setBlock(pos, want, QUIET);
                    }
                }
            }
        }
    }

    private static BoundingBox regionOf(Map<BlockPos, BlockState> snapshot) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : snapshot.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Map<BlockPos, Block> blocks(Map<BlockPos, BlockState> states) {
        Map<BlockPos, Block> out = new HashMap<>();
        states.forEach((pos, state) -> out.put(pos, state.getBlock()));
        return out;
    }

    private static void placeInChunk(ServerLevel level, ChunkGenerator generator, StructureStart start, ChunkPos chunk) {
        start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
                new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxBuildHeight(),
                        chunk.getMaxBlockZ()), chunk);
    }
}
