package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.SunkenAltarBlock;
import com.flameforge.essentialcraft5.blockentity.NestCoreBlockEntity;
import com.flameforge.essentialcraft5.blockentity.SunkenAltarBlockEntity;
import com.flameforge.essentialcraft5.entity.boss.AbyssTyrantEntity;
import com.flameforge.essentialcraft5.entity.boss.BossDaily;
import com.flameforge.essentialcraft5.entity.boss.BossLoot;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestType;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestTypes;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.sunken.SunkenDistrictStructure;
import com.flameforge.essentialcraft5.worldgen.hoanna.sunken.SunkenPieces;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13e-S2「沉没城区与渊潮领主」（batch13.md §5.3.4、§6.4；HS §2.4.2）。
 * <ul>
 *   <li>结构：{@code sunkenLayoutSeeds}（2 000 个种子：3~5 栋沉房 + 1~2 段塔身、整座 ≤ 40 格、件与件隔开、定高 ≤ 6 列）、
 *       {@code sunkenDomeShellWatertight}（穹顶外壳逐格完整：里面每一格六面都挨着里面或外壳）、{@code sunkenPiecesRoundTripNbt}（三种拼件、整座结构起点存读档）、
 *       {@code sunkenNotInOverworld}（结构集只在海纳尔）、{@code placeSunkenEndToEnd}（batch {@value #PLACE_BATCH}：在远处造一片海、放一整座，
 *       改动都在各件包围盒里、穹顶里的东西都在；跑 100 刻后穹顶里仍是空气，对照的漏洞格子确实灌进了水）；</li>
 *   <li>祭坛（{@code SunkenAltarBlock.summon}）：缺材料 / 没拿在手里不召、被取消不扣、附近有首领不召、和平 / 创造 / 假玩家不召、领地不许不召、
 *       每座每日一次（拨日子过一天又能召、往回拨不多召）；</li>
 *   <li>渊潮领主：漩涡只拉水里的参战者（路过的、穹顶里的不动；溺水速度 ×2、水下呼吸不扣）、每人每日一份（同一人一天两次只领一次、两个人各领一份、
 *       不到一成的人不给、异兽录只给真分到的人）、掉落表与箱子表的内容、沉城卫兵巢穴的类型。</li>
 * </ul>
 * 所有召唤、击杀都在同一段同步代码里做完并清掉召出来的领主（同一批里的测试一起跑，留着会让别的测试判「附近已有首领」）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class SunkenDistrictTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    public static final String BATCH = "sunken_district";
    public static final String PLACE_BATCH = "sunken_place";
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;

    private static final ResourceLocation SUNKEN = EssentialCraft5.id("sunken_district");
    private static final ResourceKey<StructureSet> SUNKEN_SET = ResourceKey.create(Registries.STRUCTURE_SET, SUNKEN);

    /** 祭坛测试：祭坛在 (5, 1, 5)，领主出的那一小片水在它上方 9~10 格（模板外，测完清掉） */
    private static final BlockPos ALTAR = new BlockPos(5, 1, 5);
    /** 祭坛测试用的固定主世界 gameTime（只看日序号那条规则的用例都用它） */
    private static final long RIG_GAME_TIME = 1_000_000L;

    private SunkenDistrictTests() {
    }

    // ================================================================== 结构：布局、形状、存档

    /** 2 000 个种子：件数、范围、间隔、定高列数、箱子约一半 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sunkenLayoutSeeds(GameTestHelper helper) {
        int chests = 0;
        int houses = 0;
        for (int i = 1; i <= 2000; i++) {
            RandomSource random = RandomSource.create(i * SEED_STEP);
            int cx = (i % 37) * 16 - 300;
            int cz = (i % 53) * 16 + 100;
            SunkenDistrictStructure.Layout layout = SunkenDistrictStructure.layout(random, cx, cz);
            int h = layout.count(SunkenDistrictStructure.Kind.HOUSE);
            int t = layout.count(SunkenDistrictStructure.Kind.TOWER);
            helper.assertTrue(layout.pieces().get(0).kind() == SunkenDistrictStructure.Kind.DOME && layout.count(SunkenDistrictStructure.Kind.DOME) == 1,
                    "种子 " + i + "：第一件是穹顶、只有一座");
            helper.assertTrue(h >= SunkenDistrictStructure.MIN_HOUSES && h <= SunkenDistrictStructure.MAX_HOUSES, "种子 " + i + "：沉房 " + h + " 栋");
            helper.assertTrue(t >= SunkenDistrictStructure.MIN_TOWERS && t <= SunkenDistrictStructure.MAX_TOWERS, "种子 " + i + "：塔身 " + t + " 段");
            helper.assertTrue(layout.radius() <= SunkenDistrictStructure.MAX_RADIUS, "种子 " + i + "：半径 " + layout.radius() + " 超过 40");
            List<SunkenDistrictStructure.Planned> pieces = layout.pieces();
            for (int a = 0; a < pieces.size(); a++) {
                for (int b = a + 1; b < pieces.size(); b++) {
                    helper.assertFalse(pieces.get(a).footprint().inflatedBy(SunkenDistrictStructure.GAP).intersects(pieces.get(b).footprint()),
                            "种子 " + i + "：第 " + a + "、" + b + " 件没隔开 " + SunkenDistrictStructure.GAP + " 格");
                }
            }
            for (SunkenDistrictStructure.Planned piece : pieces) {
                if (piece.kind() == SunkenDistrictStructure.Kind.HOUSE) {
                    houses++;
                    chests += piece.chest() ? 1 : 0;
                }
            }
            int[] columns = {0};
            List<HoannaStructurePiece> built = SunkenDistrictStructure.createPieces(layout, 45, (x, z) -> {
                columns[0]++;
                return 40 + Math.floorMod(x + z, 9);
            });
            helper.assertTrue(columns[0] == h && 1 + columns[0] <= SunkenDistrictStructure.MAX_COLUMNS,
                    "种子 " + i + "：定高应只取每栋沉房中心 1 列（+ 穹顶 1 列 ≤ 6），实际 " + columns[0]);
            helper.assertTrue(built.size() == pieces.size(), "种子 " + i + "：拼件数对不上");
            for (HoannaStructurePiece piece : built) {
                BoundingBox box = piece.getBoundingBox();
                helper.assertTrue(Math.max(Math.max(Math.abs(box.minX() - cx), Math.abs(box.maxX() - cx)),
                        Math.max(Math.abs(box.minZ() - cz), Math.abs(box.maxZ() - cz))) <= SunkenDistrictStructure.MAX_RADIUS, "种子 " + i + "：拼件出了 40 格");
            }
        }
        double share = chests / (double) houses;
        helper.assertTrue(share > 0.4D && share < 0.6D, "沉房约一半有箱子，实际 " + share);
        helper.succeed();
    }

    /** 穹顶外壳：里面每一格六个邻格都是里面或外壳（水进不来）；外壳不超过半径 9；地板就是 dy 0 的外壳；祭坛、箱子、巢穴核心都在里面 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sunkenDomeShellWatertight(GameTestHelper helper) {
        int r = SunkenPieces.DOME_RADIUS;
        int interior = 0;
        int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dy = -2; dy <= r + 2; dy++) {
                for (int dz = -r - 2; dz <= r + 2; dz++) {
                    boolean in = SunkenPieces.domeInterior(dx, dy, dz);
                    boolean shell = SunkenPieces.domeShell(dx, dy, dz);
                    helper.assertFalse(in && shell, "一格不能既是里面又是外壳：" + dx + "," + dy + "," + dz);
                    if (in || shell) {
                        helper.assertTrue(Math.abs(dx) <= r && Math.abs(dz) <= r && dy >= 0 && dy <= r, "超出半径 9：" + dx + "," + dy + "," + dz);
                    }
                    if (!in) {
                        continue;
                    }
                    interior++;
                    for (int[] s : sides) {
                        int x = dx + s[0];
                        int y = dy + s[1];
                        int z = dz + s[2];
                        helper.assertTrue(SunkenPieces.domeInterior(x, y, z) || SunkenPieces.domeShell(x, y, z),
                                "里面的 " + dx + "," + dy + "," + dz + " 挨着外面的 " + x + "," + y + "," + z);
                    }
                    helper.assertTrue(SunkenPieces.domeShell(dx, 0, dz), "里面这一列底下要有地板：" + dx + "," + dz);
                }
            }
        }
        helper.assertTrue(interior > 1000, "穹顶里应有一千多格空气，实际 " + interior);
        helper.assertTrue(SunkenPieces.domeShell(0, r, 0) && !SunkenPieces.domeShell(0, r + 1, 0), "顶在地板上 9 格");
        int o = SunkenPieces.DOME_FIXTURE_OFFSET;
        for (int[] at : new int[][]{{0, 1, 0}, {o, 1, 0}, {-o, 1, 0}, {0, 1, o}, {0, 1, -o}}) {
            helper.assertTrue(SunkenPieces.domeInterior(at[0], at[1], at[2]), "祭坛 / 箱子 / 巢穴核心要在里面：" + at[0] + "," + at[2]);
        }
        int l = SunkenPieces.DOME_LAMP_OFFSET;
        helper.assertTrue(SunkenPieces.domeShell(l, 0, l) && SunkenPieces.domeShellBlock(l, 0, l).is(Blocks.SEA_LANTERN), "地板嵌着海晶灯");
        helper.assertTrue(SunkenPieces.domeShellBlock(3, 4, 5).is(ECVoid.VOID_GLASS.get()) && SunkenPieces.domeShellBlock(0, 4, 5).is(ECMaterials.FORTIFIED_STONE.get()),
                "外壳是虚空玻璃，两条经线是强化石");
        helper.succeed();
    }

    /** 三种拼件（带字段）、整座结构起点存档再读回完全相同 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sunkenPiecesRoundTripNbt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
        List<HoannaStructurePiece> pieces = List.of(
                new SunkenPieces.Dome(0, new BoundingBox(100, 30, 200, 118, 45, 218), 51L),
                new SunkenPieces.House(1, new BoundingBox(130, 36, 200, 138, 50, 208), 52L, 2, 4, true, 3),
                new SunkenPieces.Tower(1, new BoundingBox(100, 36, 230, 104, 43, 245), 53L, false));
        Set<String> types = new HashSet<>();
        for (HoannaStructurePiece piece : pieces) {
            piece.setGroundY(41);
            CompoundTag tag = piece.createTag(context);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            helper.assertTrue(id != null && BuiltInRegistries.STRUCTURE_PIECE.containsKey(id), "拼件类型没注册：" + tag.getString("id"));
            types.add(id.getPath());
            StructurePieceType type = BuiltInRegistries.STRUCTURE_PIECE.get(id);
            StructurePiece copy = type.load(context, tag);
            helper.assertTrue(copy.getClass() == piece.getClass() && copy.getBoundingBox().equals(piece.getBoundingBox()), id + " 读回的类 / 包围盒不对");
            helper.assertTrue(((HoannaStructurePiece) copy).seed() == piece.seed() && ((HoannaStructurePiece) copy).groundY() == 41, id + " 种子 / 地面读回不同");
            helper.assertTrue(copy.createTag(context).equals(tag), id + " 再存一次应完全相同：\n" + tag + "\n" + copy.createTag(context));
            if (copy instanceof SunkenPieces.House house) {
                helper.assertTrue(house.floors() == 2 && house.radius() == 4 && house.hasChest() && house.door() == 3, "沉房的字段读回不同");
            }
            if (copy instanceof SunkenPieces.Tower tower) {
                helper.assertTrue(!tower.alongX() && tower.length() == 16, "塔身的朝向 / 长度读回不同");
            }
        }
        helper.assertTrue(types.equals(Set.of("sunken_dome", "sunken_house", "sunken_tower")), "三种拼件都测到：" + types);

        Structure structure = sunken(level);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        ChunkPos chunk = new ChunkPos(-1700, 900);
        long seed = 777L * SEED_STEP;
        StructureStart start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), seed, chunk, 0, level, biome -> true);
        helper.assertTrue(start.isValid(), "群系放行时应能布出一座沉没城区");
        helper.assertTrue(start.getPieces().get(0) instanceof SunkenPieces.Dome, "第一件是穹顶");
        CompoundTag startTag = start.createTag(context, chunk);
        StructureStart loaded = StructureStart.loadStaticStart(context, startTag, seed);
        helper.assertTrue(loaded != null && loaded.getPieces().size() == start.getPieces().size(), "结构起点读回来的件数不对");
        for (int i = 0; i < start.getPieces().size(); i++) {
            helper.assertTrue(loaded.getPieces().get(i).createTag(context).equals(start.getPieces().get(i).createTag(context)), "第 " + i + " 件读回不同");
        }
        helper.succeed();
    }

    /** 结构集只出现在海纳尔（原版主世界的结构放置状态里没有它），结构只挂海纳尔之海 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sunkenNotInOverworld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Holder<StructureSet>> hoanna = HoannaTestWorld.resolve(server).structureState().possibleStructureSets();
        List<Holder<StructureSet>> overworld = HoannaTestWorld.vanillaOverworld(server, HoannaTestWorld.SEED).structureState().possibleStructureSets();
        helper.assertTrue(hoanna.stream().anyMatch(set -> set.is(SUNKEN_SET)), "海纳尔的结构放置里要有沉没城区");
        helper.assertFalse(overworld.stream().anyMatch(set -> set.is(SUNKEN_SET)), "主世界不该有沉没城区");
        Structure structure = sunken(helper.getLevel());
        var seaBiome = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(ECHoanna.HOANNA_SEA);
        helper.assertTrue(structure.biomes().contains(seaBiome) && structure.biomes().size() == 1, "只挂海纳尔之海，实际 " + structure.biomes().size() + " 个群系");
        helper.succeed();
    }

    // ================================================================== 结构：端到端

    /** 远处造一片海（10 层海底石头 + 12 层水，四周玻璃围住），放一整座；改动都在各件包围盒里；100 刻后穹顶里仍是空气、对照的漏洞灌进了水 */
    @GameTest(template = EMPTY, batch = PLACE_BATCH, timeoutTicks = 600)
    public static void placeSunkenEndToEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos centerChunk = new ChunkPos(-2100, 2300);
        int cx = centerChunk.getMiddleBlockX();
        int cz = centerChunk.getMiddleBlockZ();
        SunkenDistrictStructure.Layout layout = null;
        for (int i = 1; i <= 200 && layout == null; i++) {
            SunkenDistrictStructure.Layout candidate = SunkenDistrictStructure.layout(RandomSource.create(i * SEED_STEP), cx, cz);
            if (candidate.count(SunkenDistrictStructure.Kind.HOUSE) >= 4
                    && candidate.pieces().stream().anyMatch(p -> p.kind() == SunkenDistrictStructure.Kind.HOUSE && p.chest() && p.floors() == 2)) {
                layout = candidate;
            }
        }
        helper.assertTrue(layout != null, "200 个种子里没有 4 栋以上、有两层带箱子沉房的布局");
        int seabed = level.getMinBuildHeight() + 4;
        int ground = seabed + 10;
        int waterTop = ground + 11;
        List<HoannaStructurePiece> pieces = SunkenDistrictStructure.createPieces(layout, ground, (x, z) -> ground);
        BoundingBox union = pieces.get(0).getBoundingBox();
        for (HoannaStructurePiece piece : pieces) {
            union = BoundingBox.encapsulatingBoxes(List.of(union, piece.getBoundingBox())).orElseThrow();
        }
        BoundingBox region = new BoundingBox(union.minX() - 3, seabed, union.minZ() - 3, union.maxX() + 3, Math.max(union.maxY(), waterTop) + 1, union.maxZ() + 3);
        List<ChunkPos> chunks = new ArrayList<>();
        ChunkPos.rangeClosed(new ChunkPos(SectionPos.blockToSectionCoord(region.minX()), SectionPos.blockToSectionCoord(region.minZ())),
                new ChunkPos(SectionPos.blockToSectionCoord(region.maxX()), SectionPos.blockToSectionCoord(region.maxZ()))).forEach(chunks::add);
        for (ChunkPos chunk : chunks) {
            level.setChunkForced(chunk.x, chunk.z, true);
            level.getChunk(chunk.x, chunk.z);
        }
        int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                boolean wall = x == region.minX() || x == region.maxX() || z == region.minZ() || z == region.maxZ();
                for (int y = region.minY(); y <= region.maxY(); y++) {
                    BlockState state = y < ground ? Blocks.STONE.defaultBlockState() : y > waterTop ? Blocks.AIR.defaultBlockState()
                            : wall ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState();
                    level.setBlock(pos.set(x, y, z), state, quiet);
                }
            }
        }
        BlockState[] before = snapshot(level, region);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (ChunkPos chunk : chunks) {
            BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                    level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
            for (HoannaStructurePiece piece : pieces) {
                if (piece.getBoundingBox().intersects(chunkBox)) {
                    piece.postProcess(level, level.structureManager(), generator, RandomSource.create(chunk.toLong()), chunkBox, chunk,
                            new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ()));
                }
            }
        }
        // 改动都在某一件的包围盒里
        int changed = 0;
        int i = 0;
        for (int y = region.minY(); y <= region.maxY(); y++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    BlockState now = level.getBlockState(pos.set(x, y, z));
                    if (now != before[i++]) {
                        changed++;
                        BlockPos at = pos.immutable();
                        helper.assertTrue(pieces.stream().anyMatch(p -> p.getBoundingBox().isInside(at)), "改动的 " + at + " 不在任何一件的包围盒里");
                    }
                }
            }
        }
        helper.assertTrue(changed > 2000, "一整座应改动不少方块，实际 " + changed);
        SunkenPieces.Dome dome = (SunkenPieces.Dome) pieces.get(0);
        helper.assertTrue(level.getBlockState(dome.altarPos()).is(ECSunkenDistrict.SUNKEN_ALTAR.get())
                && level.getBlockEntity(dome.altarPos()) instanceof SunkenAltarBlockEntity, "穹顶中央是沉城祭坛（带方块实体）");
        for (BlockPos chest : dome.chestPositions()) {
            helper.assertTrue(chestTable(level, chest), "穹顶里的箱子 " + chest + " 用 chests/sunken_district");
        }
        for (BlockPos nest : dome.nestPositions()) {
            helper.assertTrue(level.getBlockEntity(nest) instanceof NestCoreBlockEntity core && SunkenPieces.NEST_TYPE.equals(core.typeId()),
                    "穹顶两侧的巢穴核心类型是 sunken_guard：" + nest);
        }
        int houseChests = 0;
        for (HoannaStructurePiece piece : pieces) {
            if (piece instanceof SunkenPieces.House house && house.hasChest()) {
                helper.assertTrue(chestTable(level, house.chestPos()), "沉房的箱子 " + house.chestPos());
                helper.assertTrue(level.getBlockState(house.chestPos()).getFluidState().is(FluidTags.WATER), "沉房里的箱子是含水的");
                houseChests++;
            }
            if (piece instanceof SunkenPieces.House house) {
                BlockPos inside = new BlockPos(house.getBoundingBox().minX() + 1, house.slabY() + 3, house.getBoundingBox().minZ() + 2);
                BlockState cell = level.getBlockState(inside);
                helper.assertTrue(cell.getFluidState().is(FluidTags.WATER) || cell.is(Blocks.WATER) || !cell.isAir(), "沉房一楼里面是水（或碎石），不是空气：" + cell);
            }
        }
        helper.assertTrue(houseChests >= 1, "挑的布局里至少一栋沉房有箱子");
        checkDome(helper, level, dome, "放完");
        // 对照：远离各件的一格挖成空气（带邻格更新），100 刻后应被水灌满——证明这片区块的流体确实在走
        BlockPos control = new BlockPos(region.minX() + 1, waterTop - 1, region.minZ() + 1);
        helper.assertTrue(pieces.stream().noneMatch(p -> p.getBoundingBox().isInside(control)), "对照格不该在拼件里");
        level.setBlock(control, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        BoundingBox cleanup = region;
        helper.runAfterDelay(100, () -> {
            try {
                helper.assertTrue(level.getFluidState(control).is(FluidTags.WATER), "对照格 100 刻后应灌进水（流体没在走的话这条测试没意义）");
                checkDome(helper, level, dome, "100 刻后");
                checkNestSpots(helper, level, dome);
            } finally {
                clear(level, cleanup);
                for (ChunkPos chunk : chunks) {
                    level.setChunkForced(chunk.x, chunk.z, false);
                }
            }
            helper.succeed();
        });
    }

    /**
     * 穹顶两侧的巢穴照真规则（{@code NestCoreBlockEntity.findSpot}、巢穴类型数据里的 spawn_radius）找落点，每个核心试 300 次：
     * 找到的落点（脚下那一格、身体那一格）都在穹顶里面，不会落到穹顶外的海底或外壳顶上；也不会和核心同一列
     */
    private static void checkNestSpots(GameTestHelper helper, ServerLevel level, SunkenPieces.Dome dome) {
        HoannaNestType type = HoannaNestTypes.get(SunkenPieces.NEST_TYPE);
        helper.assertTrue(type != null && type.spawnRadius() <= 2, "沉城卫兵的落点半径应 ≤ 2（核心离穹顶中心 6 格、穹顶内半径约 8.4）：" + type);
        RandomSource random = RandomSource.create(0x5EA6E57L);
        int found = 0;
        for (BlockPos nest : dome.nestPositions()) {
            for (int i = 0; i < 300; i++) {
                Vec3 spot = NestCoreBlockEntity.findSpot(level, nest, ECHoannaNativesC.CATACOMB_REVENANT.get(), type.spawnRadius(), random);
                if (spot == null) {
                    continue;
                }
                found++;
                BlockPos feet = BlockPos.containing(spot);
                int dx = feet.getX() - dome.centerX();
                int dy = feet.getY() - dome.floorY();
                int dz = feet.getZ() - dome.centerZ();
                helper.assertTrue(SunkenPieces.domeInterior(dx, dy, dz) && SunkenPieces.domeInterior(dx, dy + 1, dz),
                        "巢穴 " + nest + " 的落点 " + feet + " 不在穹顶里（相对中心 " + dx + ", " + dy + ", " + dz + "）");
                helper.assertFalse(feet.getX() == nest.getX() && feet.getZ() == nest.getZ(), "落点不和核心同一列");
            }
        }
        helper.assertTrue(found > 300, "两个核心试 600 次应大多找得到落点，实际 " + found);
    }

    /** 穹顶里逐格是空气（祭坛、箱子、巢穴核心那几格除外）、外壳逐格是对的方块 */
    private static void checkDome(GameTestHelper helper, ServerLevel level, SunkenPieces.Dome dome, String when) {
        int r = SunkenPieces.DOME_RADIUS;
        Set<BlockPos> fixtures = new HashSet<>();
        fixtures.add(dome.altarPos());
        fixtures.addAll(List.of(dome.chestPositions()));
        fixtures.addAll(List.of(dome.nestPositions()));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = 0; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(dome.centerX() + dx, dome.floorY() + dy, dome.centerZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (SunkenPieces.domeInterior(dx, dy, dz)) {
                        if (!fixtures.contains(pos)) {
                            helper.assertTrue(state.isAir(), when + "：穹顶里 " + pos + " 应是空气，实际 " + state);
                        }
                        helper.assertTrue(state.getFluidState().isEmpty(), when + "：穹顶里 " + pos + " 有水");
                    } else if (SunkenPieces.domeShell(dx, dy, dz)) {
                        helper.assertTrue(state == SunkenPieces.domeShellBlock(dx, dy, dz), when + "：外壳 " + pos + " 应是 " + SunkenPieces.domeShellBlock(dx, dy, dz)
                                + "，实际 " + state);
                    }
                }
            }
        }
    }

    // ================================================================== 祭坛

    /** 没拿着潮晶、材料不够：都不召，什么都不扣，祭坛不记今天 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void altarNeedsMaterials(GameTestHelper helper) {
        AltarRig rig = AltarRig.build(helper);
        ServerPlayer player = player(helper, "s2-mat", GameType.SURVIVAL);
        try {
            // 主手先占上别的东西，不然 add 会把潮晶放进当前选中的那一格
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STICK));
            player.getInventory().add(new ItemStack(ECHoannaMaterials.TIDE_CRYSTAL.get(), 6));
            player.getInventory().add(new ItemStack(ECMobDrops.ABERRANT_HEART.get(), 1));
            SunkenAltarBlock.Result unheld = rig.summon(player, 10L).result();
            helper.assertTrue(unheld == SunkenAltarBlock.Result.NOT_HOLDING, "背包里有、手里没拿潮晶：不召，实际 " + unheld);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ECHoannaMaterials.TIDE_CRYSTAL.get(), 3));
            player.getInventory().add(new ItemStack(ECMobDrops.ABERRANT_HEART.get(), 1));
            helper.assertTrue(rig.summon(player, 10L).result() == SunkenAltarBlock.Result.MISSING, "副手拿着、只有 3 个潮晶：缺材料");
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECHoannaMaterials.TIDE_CRYSTAL.get(), 4));
            helper.assertTrue(rig.summon(player, 10L).result() == SunkenAltarBlock.Result.MISSING, "4 个潮晶、没有异变之心：缺材料");
            helper.assertTrue(player.getMainHandItem().getCount() == 4 && rig.tyrants().isEmpty() && rig.altar().lastDay() == SunkenAltarBlockEntity.NEVER,
                    "什么都不扣、没召出来、祭坛不记今天");
        } finally {
            rig.close(player);
        }
        helper.succeed();
    }

    /** 召出来的领主在加入世界时被拦下：报生成失败，材料、祭坛的「今天」都不动 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void altarSpawnCancelledKeepsMaterials(GameTestHelper helper) {
        AltarRig rig = AltarRig.build(helper);
        ServerPlayer player = player(helper, "s2-cxl", GameType.SURVIVAL);
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof AbyssTyrantEntity) {
                event.setCanceled(true);
            }
        };
        try {
            stock(player, 4, 1);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, cancel);
            SunkenAltarBlock.Outcome outcome;
            try {
                outcome = rig.summon(player, 10L);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(outcome.result() == SunkenAltarBlock.Result.SPAWN_FAILED, "生成被拦，实际 " + outcome.result());
            helper.assertTrue(crystals(player) == 4 && hearts(player) == 1, "被取消不扣材料");
            helper.assertTrue(rig.altar().lastDay() == SunkenAltarBlockEntity.NEVER && rig.tyrants().isEmpty(), "祭坛不记今天、世界里没有领主");
        } finally {
            rig.close(player);
        }
        helper.succeed();
    }

    /** 祭坛 64 格内有别的首领（#forge:bosses）不召；首领没了就能召 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void altarRefusesWithBossNearby(GameTestHelper helper) {
        AltarRig rig = AltarRig.build(helper);
        ServerPlayer player = player(helper, "s2-occ", GameType.SURVIVAL);
        AbyssTyrantEntity other = ECSunkenDistrict.ABYSS_TYRANT.get().create(helper.getLevel());
        try {
            stock(player, 4, 1);
            Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D)).add(40D, 0D, 0D);
            other.moveTo(at.x, at.y, at.z);
            other.setNoAi(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(other), "测试前提：另一只首领加进了世界");
            helper.assertTrue(rig.summon(player, 10L).result() == SunkenAltarBlock.Result.OCCUPIED, "40 格外有一只首领：不召");
            helper.assertTrue(crystals(player) == 4 && hearts(player) == 1, "不扣材料");
            other.discard();
            SunkenAltarBlock.Outcome ok = rig.summon(player, 10L);
            helper.assertTrue(ok.ok(), "首领没了以后能召，实际 " + ok.result());
        } finally {
            other.discard();
            rig.close(player);
        }
        helper.succeed();
    }

    /** 和平难度、创造模式、假玩家都不召 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void altarRefusesPeacefulCreativeFake(GameTestHelper helper) {
        AltarRig rig = AltarRig.build(helper);
        ServerPlayer player = player(helper, "s2-pea", GameType.SURVIVAL);
        MinecraftServer server = helper.getLevel().getServer();
        try {
            stock(player, 4, 1);
            Difficulty old = server.getWorldData().getDifficulty();
            server.setDifficulty(Difficulty.PEACEFUL, true);
            SunkenAltarBlock.Result peaceful;
            try {
                peaceful = rig.summon(player, 10L).result();
            } finally {
                server.setDifficulty(old, true);
            }
            helper.assertTrue(peaceful == SunkenAltarBlock.Result.PEACEFUL, "和平难度不召，实际 " + peaceful);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(rig.summon(player, 10L).result() == SunkenAltarBlock.Result.GAME_MODE, "创造模式不召");
            player.setGameMode(GameType.SURVIVAL);
            FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
            helper.assertTrue(SunkenAltarBlock.summon(fake, helper.getLevel(), rig.pos(), new BossDaily.Stamp(10L, RIG_GAME_TIME)).result() == SunkenAltarBlock.Result.NOT_REAL_PLAYER, "假玩家不召");
            helper.assertTrue(crystals(player) == 4 && hearts(player) == 1 && rig.tyrants().isEmpty(), "都不扣、都没召出来");
        } finally {
            rig.close(player);
        }
        helper.succeed();
    }

    /** 祭坛那一格领地不许（以召唤者身份探测）：不召、不扣 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void altarRefusesClaimDenied(GameTestHelper helper) {
        AltarRig rig = AltarRig.build(helper);
        ServerPlayer player = player(helper, "s2-claim", GameType.SURVIVAL);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(ALTAR)) {
            stock(player, 4, 1);
            helper.assertTrue(rig.summon(player, 10L).result() == SunkenAltarBlock.Result.DENIED, "领地不许时不召");
            helper.assertTrue(claims.breaks(ALTAR) >= 1, "应以召唤者的身份探测祭坛那一格");
            helper.assertTrue(crystals(player) == 4 && hearts(player) == 1 && rig.tyrants().isEmpty(), "不扣、没召出来");
        } finally {
            rig.close(player);
        }
        helper.succeed();
    }

    /**
     * 召成：领主在穹顶顶上的水里、来源是首领、召唤者进名单、生命按配置、场地是祭坛；扣潮晶 4、异变之心 1（先扣手里的）；祭坛记今天。
     * 同一天再召不行；过一天能召；把日子往回拨也不多召。
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void altarOncePerDay(GameTestHelper helper) {
        AltarRig rig = AltarRig.build(helper);
        ServerPlayer player = player(helper, "s2-day", GameType.SURVIVAL);
        try {
            stock(player, 9, 3);
            SunkenAltarBlock.Outcome first = rig.summon(player, 20L);
            helper.assertTrue(first.ok() && first.tyrant() != null, "第一次召成，实际 " + first.result());
            AbyssTyrantEntity tyrant = first.tyrant();
            helper.assertTrue(tyrant.isInWater() || helper.getLevel().getFluidState(tyrant.blockPosition()).is(FluidTags.WATER), "领主出在水里");
            helper.assertTrue(tyrant.getY() >= rig.pos().getY() + SunkenAltarBlock.ABOVE_DOME, "领主出在穹顶顶上（祭坛 + 9 格以上）");
            helper.assertTrue(SpawnOrigin.of(tyrant) == SpawnOrigin.Origin.BOSS, "来源是首领");
            helper.assertTrue(tyrant.bossRoster().contains(player.getUUID()), "召唤者进名单");
            helper.assertTrue(Math.abs(tyrant.getMaxHealth() - ECSunkenDistrict.Config.DEFAULTS.maxHealth()) < 1.0E-3D && tyrant.getHealth() == tyrant.getMaxHealth(),
                    "生命按配置、满血");
            helper.assertTrue(rig.pos().equals(tyrant.arenaFloor()) && rig.pos().equals(tyrant.altar()), "场地是祭坛");
            helper.assertTrue(crystals(player) == 5 && hearts(player) == 2, "扣潮晶 4、异变之心 1，实际剩 " + crystals(player) + " / " + hearts(player));
            helper.assertTrue(rig.altar().lastDay() == 20L && helper.getLevel().getBlockState(rig.pos()).is(ECSunkenDistrict.SUNKEN_ALTAR.get()),
                    "祭坛记今天、本身不消耗");
            tyrant.discard();
            helper.assertTrue(rig.summon(player, 20L).result() == SunkenAltarBlock.Result.USED_TODAY, "同一天再召不行");
            SunkenAltarBlock.Outcome next = rig.summon(player, 21L);
            helper.assertTrue(next.ok(), "过一天能召，实际 " + next.result());
            next.tyrant().discard();
            helper.assertTrue(rig.summon(player, 19L).result() == SunkenAltarBlock.Result.USED_TODAY, "日子往回拨不多召");
            helper.assertTrue(crystals(player) == 1 && hearts(player) == 1, "两次各扣一份");
        } finally {
            rig.close(player);
        }
        helper.succeed();
    }

    /**
     * 祭坛每日一次走 BossDaily 的钟：锁了日夜循环（日序号不动）满 24 000 刻 gameTime 能再召、差一刻不行；
     * /time set 往回拨（日序号变小）也要满 24 000 刻 gameTime；记下的日序号不往回拨；存档往返两项都在
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void altarDailyLockedClock(GameTestHelper helper) {
        AltarRig rig = AltarRig.build(helper);
        ServerPlayer player = player(helper, "s2-clock", GameType.SURVIVAL);
        long g = RIG_GAME_TIME;
        try {
            stock(player, 20, 5);
            SunkenAltarBlock.Outcome first = rig.summon(player, 30L, g);
            helper.assertTrue(first.ok(), "第一次召成，实际 " + first.result());
            first.tyrant().discard();
            helper.assertTrue(rig.summon(player, 30L, g + BossDaily.DAY_TICKS - 1L).result() == SunkenAltarBlock.Result.USED_TODAY, "日子没动、差一刻满一天：不行");
            SunkenAltarBlock.Outcome locked = rig.summon(player, 30L, g + BossDaily.DAY_TICKS);
            helper.assertTrue(locked.ok(), "锁了日夜循环：满 24 000 刻 gameTime 能再召，实际 " + locked.result());
            locked.tyrant().discard();
            long t2 = g + BossDaily.DAY_TICKS;
            helper.assertTrue(rig.summon(player, 0L, t2 + 100L).result() == SunkenAltarBlock.Result.USED_TODAY, "往回拨到第 0 天：不多召");
            SunkenAltarBlock.Outcome later = rig.summon(player, 0L, t2 + BossDaily.DAY_TICKS);
            helper.assertTrue(later.ok(), "往回拨以后满一天 gameTime 照样能召，实际 " + later.result());
            later.tyrant().discard();
            helper.assertTrue(rig.altar().lastDay() == 30L && rig.altar().lastGameTime() == t2 + BossDaily.DAY_TICKS, "记下的日序号不往回拨、gameTime 记这一刻");
            CompoundTag saved = rig.altar().saveWithoutMetadata();
            SunkenAltarBlockEntity copy = new SunkenAltarBlockEntity(rig.pos(), rig.altar().getBlockState());
            copy.load(saved);
            helper.assertTrue(copy.lastDay() == 30L && copy.lastGameTime() == t2 + BossDaily.DAY_TICKS, "存档往返两项都在");
        } finally {
            rig.close(player);
        }
        helper.succeed();
    }

    /** 渊潮领主每人每日一份走同一个钟：锁日夜循环满 24 000 刻 gameTime 又能领；往回拨日子不满一天不多给 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void tyrantDailyLockedClock(GameTestHelper helper) {
        ServerPlayer b = player(helper, "s2-dclock", GameType.SURVIVAL);
        long g = 3_000_000L;
        try {
            helper.assertTrue(AbyssTyrantEntity.claimDaily(List.of(b), new BossDaily.Stamp(5L, g)).size() == 1, "第一次领");
            helper.assertTrue(AbyssTyrantEntity.claimDaily(List.of(b), new BossDaily.Stamp(5L, g + 23_999L)).isEmpty(), "同一天不再给");
            helper.assertTrue(AbyssTyrantEntity.claimDaily(List.of(b), new BossDaily.Stamp(5L, g + 24_000L)).size() == 1, "锁了日夜循环：满一天 gameTime 又能领");
            helper.assertTrue(AbyssTyrantEntity.claimDaily(List.of(b), new BossDaily.Stamp(0L, g + 30_000L)).isEmpty(), "往回拨：不满一天不多给");
            helper.assertTrue(AbyssTyrantEntity.claimDaily(List.of(b), new BossDaily.Stamp(0L, g + 48_000L)).size() == 1, "往回拨以后满一天照样能领");
            helper.assertTrue(data(b).getBossDaily().getLong(AbyssTyrantEntity.DAILY_KEY) == 5L, "记下的日序号不往回拨");
        } finally {
            ECProtection.clearOnlineOverride(b.getUUID());
        }
        helper.succeed();
    }

    /**
     * 领主的活动范围按场地中心算：默认 pull_range 下活动范围（pull_range − 6）加上它咬得到的距离仍在拉回距离以内，贴着它打的人不会被拽回穹顶；
     * 两种出生点（穹顶正上方最高处、穹顶外侧最远处）都在活动范围里；离祭坛超过活动范围才算出界；pull_range 填 0 时按 escape_range 算，不小于穹顶外 3 格
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void tyrantStaysInsidePullRange(GameTestHelper helper) {
        ECSunkenDistrict.Config.Values cfg = ECSunkenDistrict.Config.DEFAULTS;
        double leash = AbyssTyrantEntity.leashRange(cfg.pullRange(), cfg.escapeRange());
        AbyssTyrantEntity tyrant = ECSunkenDistrict.ABYSS_TYRANT.get().create(helper.getLevel());
        helper.assertTrue(tyrant != null, "建得出领主");
        double reach = tyrant.getBbWidth() * 2D + 1D;
        helper.assertTrue(leash + reach <= cfg.pullRange(), "活动范围 " + leash + " + 咬的距离 " + reach + " 应在拉回距离 " + cfg.pullRange() + " 以内");
        // 祭坛顶面中心 = 祭坛 + 1；出生点最高在祭坛 + ABOVE_DOME + ABOVE_SEARCH，侧面在 SIDE_DISTANCE 外、最高祭坛 + ABOVE_DOME − 1
        double above = SunkenAltarBlock.ABOVE_DOME + SunkenAltarBlock.ABOVE_SEARCH - 1D;
        double side = Math.sqrt(SunkenAltarBlock.SIDE_DISTANCE * (double) SunkenAltarBlock.SIDE_DISTANCE
                + (SunkenAltarBlock.ABOVE_DOME - 2D) * (SunkenAltarBlock.ABOVE_DOME - 2D));
        helper.assertTrue(above <= leash && side <= leash, "两种出生点都在活动范围里：" + above + " / " + side + " ≤ " + leash);
        BlockPos altar = helper.absolutePos(ALTAR);
        Vec3 center = Vec3.atBottomCenterOf(altar.above());
        tyrant.prepareSummon(altar, center.add(0D, SunkenAltarBlock.ABOVE_DOME, 0D));
        tyrant.setPos(center.x + leash - 1D, center.y, center.z);
        helper.assertFalse(tyrant.outsideLeash(), "活动范围以内不算出界");
        tyrant.setPos(center.x + leash + 1D, center.y, center.z);
        helper.assertTrue(tyrant.outsideLeash(), "超过活动范围算出界");
        helper.assertTrue(AbyssTyrantEntity.leashRange(0D, 40D) == 34D && AbyssTyrantEntity.leashRange(10D, 48D) == AbyssTyrantEntity.MIN_LEASH,
                "pull_range 填 0 按 escape_range；太小时不小于下限");
        tyrant.discard();
        helper.succeed();
    }

    // ================================================================== 渊潮领主

    /** 漩涡：只拉名单里、在水里、6 格内的人（速度往它那里、空气多扣一点）；路过的不在名单里、穹顶里（空气）的、太远的不动；水下呼吸不扣空气 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void vortexOnlyPullsFightersInWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 7×4×7 的水池（玻璃围住），东南角一个 1×2 的空气小间（玻璃围住，当穹顶里）
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                for (int y = 1; y <= 6; y++) {
                    boolean edge = x == 1 || x == 9 || z == 1 || z == 9 || y == 1;
                    helper.setBlock(new BlockPos(x, y, z), edge ? Blocks.GLASS : y <= 5 ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        for (int x = 6; x <= 8; x++) {
            for (int z = 6; z <= 8; z++) {
                for (int y = 2; y <= 5; y++) {
                    boolean room = x == 7 && z == 7 && (y == 3 || y == 4);
                    helper.setBlock(new BlockPos(x, y, z), room ? Blocks.AIR : Blocks.GLASS);
                }
            }
        }
        AbyssTyrantEntity tyrant = ECSunkenDistrict.ABYSS_TYRANT.get().create(level);
        ServerPlayer fighter = player(helper, "s2-vx-a", GameType.SURVIVAL);
        ServerPlayer bystander = player(helper, "s2-vx-b", GameType.SURVIVAL);
        ServerPlayer sheltered = player(helper, "s2-vx-c", GameType.SURVIVAL);
        try {
            moveTo(helper, tyrant, new Vec3(4.5D, 2.2D, 4.5D));
            moveTo(helper, fighter, new Vec3(2.5D, 3D, 4.5D));
            moveTo(helper, bystander, new Vec3(4.5D, 3D, 2.5D));
            moveTo(helper, sheltered, new Vec3(7.5D, 3D, 7.5D));
            tyrant.bossRoster().add(fighter);
            tyrant.bossRoster().add(sheltered);
            for (ServerPlayer player : List.of(fighter, bystander, sheltered)) {
                player.baseTick();
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = false;
                player.setAirSupply(200);
            }
            helper.assertTrue(fighter.isInWater() && bystander.isInWater() && !sheltered.isInWater(), "测试前提：两人在水里、一人在空气小间里");
            ECSunkenDistrict.Config.Values cfg = ECSunkenDistrict.Config.DEFAULTS;
            helper.assertTrue(tyrant.applyVortex(level, cfg) == 1, "只拉到一个人");
            Vec3 toward = tyrant.position().subtract(fighter.position());
            helper.assertTrue(fighter.getDeltaMovement().dot(toward) > 0D && fighter.hurtMarked, "参战者被往领主那里拉、速度发给客户端");
            helper.assertTrue(fighter.getAirSupply() == 199, "参战者空气多扣一点（溺水 ×2），实际 " + fighter.getAirSupply());
            helper.assertTrue(bystander.getDeltaMovement().equals(Vec3.ZERO) && bystander.getAirSupply() == 200, "不在名单里的人不动");
            helper.assertTrue(sheltered.getDeltaMovement().equals(Vec3.ZERO) && sheltered.getAirSupply() == 200, "穹顶里（空气）的参战者不动");
            helper.assertFalse(tyrant.vortexAffects(fighter, 1D), "1 格范围就够不着");
            fighter.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200));
            helper.assertFalse(AbyssTyrantEntity.drainAir(fighter), "水下呼吸时不扣空气");
            fighter.removeAllEffects();
            fighter.setAirSupply(AbyssTyrantEntity.AIR_FLOOR);
            helper.assertFalse(AbyssTyrantEntity.drainAir(fighter), "空气扣到 −19 就停（原版在正好 −20 时结算溺水伤害）");
            ECSunkenDistrict.Config.Values off = new ECSunkenDistrict.Config.Values(4, 1, 64, 200D, 10D, 14D, 25D, 16D, 48D, 300, 80, 0D, 0.08D, true);
            helper.assertTrue(tyrant.applyVortex(level, off) == 0, "半径填 0：谁都不拉");
        } finally {
            tyrant.discard();
            for (ServerPlayer player : List.of(fighter, bystander, sheltered)) {
                ECProtection.clearOnlineOverride(player.getUUID());
            }
        }
        helper.succeed();
    }

    /**
     * 每人每日一份：A 今天领过（只在聊天栏说一句、不给、不记首杀和异兽录），B 分到一份（异变之心 ×2、上古晶核 ×1、遗物）并记上今天，
     * C 只打了不到一成不给；同一天 B 再打一只不给，第二天又给；{@code daily_reward_limit} 关掉时照给。
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void tyrantRewardOncePerPlayerPerDay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer a = player(helper, "s2-dl-a", GameType.SURVIVAL);
        ServerPlayer b = player(helper, "s2-dl-b", GameType.SURVIVAL);
        ServerPlayer c = player(helper, "s2-dl-c", GameType.SURVIVAL);
        long today = BossDaily.dayIndex(level.getServer());
        List<AbyssTyrantEntity> spawned = new ArrayList<>();
        try {
            data(a).getBossDaily().putLong(AbyssTyrantEntity.DAILY_KEY, today);
            AbyssTyrantEntity first = kill(helper, spawned, a, 60F, b, 50F, c, 5F);
            helper.assertTrue(first.defeated() && first.defeatedByPlayers(), "测试前提：被玩家打倒");
            helper.assertTrue(hearts(a) == 0 && cores(a) == 0, "A 今天领过：不给");
            helper.assertTrue(hearts(b) == 2 && cores(b) == 1, "B 分到异变之心 ×2、上古晶核 ×1，实际 " + hearts(b) + " / " + cores(b));
            helper.assertTrue(hearts(c) == 0 && cores(c) == 0, "C 不到一成：不给");
            helper.assertTrue(data(b).getBossDaily().getLong(AbyssTyrantEntity.DAILY_KEY) == today, "B 记上今天");
            helper.assertTrue(data(b).getBossFirstKills().contains("essentialcraft5:abyss_tyrant")
                    && !data(a).getBossFirstKills().contains("essentialcraft5:abyss_tyrant"), "首杀只记给真分到的人");
            ResourceLocation bestiary = BossLoot.bestiaryAdvancement(ECSunkenDistrict.ABYSS_TYRANT.get());
            if (bestiary != null && level.getServer().getAdvancements().getAdvancement(bestiary) != null) {
                helper.assertTrue(done(b, bestiary) && !done(a, bestiary), "异兽录只发给真分到的人");
            }

            kill(helper, spawned, b, 80F, null, 0F, null, 0F);
            helper.assertTrue(hearts(b) == 2, "B 同一天再打一只：不给");
            helper.assertTrue(AbyssTyrantEntity.claimDaily(List.of(b), new BossDaily.Stamp(today + 1, BossDaily.gameTime(level.getServer()))).size() == 1 && data(b).getBossDaily().getLong(AbyssTyrantEntity.DAILY_KEY) == today + 1,
                    "第二天又能领、记上新的一天");
            helper.assertTrue(AbyssTyrantEntity.claimDaily(List.of(b), new BossDaily.Stamp(today, BossDaily.gameTime(level.getServer()))).isEmpty(), "日子往回拨不多给");

            ECSunkenDistrict.Config.setTestValues(ECSunkenDistrict.Config.DEFAULTS.withDailyLimit(false));
            try {
                kill(helper, spawned, a, 80F, null, 0F, null, 0F);
            } finally {
                ECSunkenDistrict.Config.setTestValues(null);
            }
            helper.assertTrue(hearts(a) == 2, "关掉每日一份时照给");
        } finally {
            ECSunkenDistrict.Config.setTestValues(null);
            for (AbyssTyrantEntity tyrant : spawned) {
                tyrant.discard();
            }
            for (ServerPlayer player : List.of(a, b, c)) {
                ECProtection.clearOnlineOverride(player.getUUID());
            }
        }
        helper.succeed();
    }

    /** 掉落表：每个池都要被玩家打倒；异变之心 ×2、上古晶核 ×1、遗物 6 抽。箱子表：渊盐、遗物 6~17 抽、残破勘测图 25%。沉城卫兵巢穴照 §6.4 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sunkenLootAndNestData(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        JsonObject tyrant = json(server, EssentialCraft5.id("loot_tables/entities/abyss_tyrant.json"));
        JsonArray pools = tyrant.getAsJsonArray("pools");
        helper.assertTrue(pools.size() == 3, "领主表 3 个池");
        for (JsonElement pool : pools) {
            helper.assertTrue(pool.toString().contains("minecraft:killed_by_player"), "每个池都带 killed_by_player：" + pool);
        }
        helper.assertTrue(pools.get(0).toString().contains("essentialcraft5:aberrant_heart") && pools.get(0).toString().contains("\"count\":2.0"), "异变之心 ×2");
        helper.assertTrue(pools.get(1).toString().contains("essentialcraft5:ancient_core") && !pools.get(1).toString().contains("set_count"), "上古晶核 ×1");
        JsonObject relicRolls = pools.get(2).getAsJsonObject().getAsJsonObject("rolls");
        helper.assertTrue(relicRolls.get("min").getAsDouble() == 6D && relicRolls.get("max").getAsDouble() == 6D, "遗物 6 抽");

        JsonObject chest = json(server, EssentialCraft5.id("loot_tables/chests/sunken_district.json"));
        String chestText = chest.toString();
        helper.assertTrue(chestText.contains("essentialcraft5:abyssal_salt") && chestText.contains("essentialcraft5:torn_survey_map")
                && chestText.contains("\"chance\":0.25") && !chestText.contains("killed_by_player"), "箱子表：渊盐、残破勘测图 25%，不带击杀条件");
        JsonObject relics = chest.getAsJsonArray("pools").get(1).getAsJsonObject().getAsJsonObject("rolls");
        helper.assertTrue(relics.get("min").getAsDouble() == 6D && relics.get("max").getAsDouble() == 17D, "遗物池 1 次（6~17 抽）");

        JsonObject nest = json(server, EssentialCraft5.id("hoanna_nests/sunken_guard.json"));
        JsonArray entities = nest.getAsJsonArray("entities");
        int plain = 0;
        int leaders = 0;
        for (JsonElement element : entities) {
            JsonObject row = element.getAsJsonObject();
            helper.assertTrue("essentialcraft5:catacomb_revenant".equals(row.get("type").getAsString()), "沉城卫兵是墓穴守灵");
            if (row.has("leader") && row.get("leader").getAsBoolean()) {
                leaders += row.get("count").getAsInt();
            } else {
                plain += row.get("count").getAsInt();
            }
        }
        helper.assertTrue(plain == 3 && leaders == 1, "墓穴守灵 ×3 + 墓穴守灵长，实际 " + plain + " + " + leaders);
        JsonObject nestLoot = json(server, EssentialCraft5.id("loot_tables/hoanna_nests/sunken_guard.json"));
        helper.assertTrue(nestLoot.toString().contains("essentialcraft5:abyssal_salt") && nestLoot.toString().contains("\"min\":4.0")
                && nestLoot.toString().contains("\"max\":8.0"), "巢穴奖励另加渊盐 4~8");
        helper.assertTrue(SunkenPieces.NEST_TYPE.equals(EssentialCraft5.id("sunken_guard")), "穹顶放的巢穴类型是 sunken_guard");
        helper.succeed();
    }

    // ================================================================== 工具

    /** 祭坛测试的场地：祭坛 + 头顶 9~10 格的一小片水（玻璃围住，在模板外，测完清掉） */
    private record AltarRig(GameTestHelper helper, BlockPos pos, BoundingBox pocket) {

        static AltarRig build(GameTestHelper helper) {
            helper.setBlock(ALTAR, ECSunkenDistrict.SUNKEN_ALTAR.get());
            BlockPos altar = helper.absolutePos(ALTAR);
            ServerLevel level = helper.getLevel();
            BoundingBox pocket = new BoundingBox(altar.getX() - 2, altar.getY() + SunkenAltarBlock.ABOVE_DOME - 1, altar.getZ() - 2,
                    altar.getX() + 2, altar.getY() + SunkenAltarBlock.ABOVE_DOME + 2, altar.getZ() + 2);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = pocket.minX(); x <= pocket.maxX(); x++) {
                for (int y = pocket.minY(); y <= pocket.maxY(); y++) {
                    for (int z = pocket.minZ(); z <= pocket.maxZ(); z++) {
                        boolean shell = x == pocket.minX() || x == pocket.maxX() || z == pocket.minZ() || z == pocket.maxZ() || y == pocket.minY() || y == pocket.maxY();
                        level.setBlock(pos.set(x, y, z), shell ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            return new AltarRig(helper, altar, pocket);
        }

        SunkenAltarBlockEntity altar() {
            if (helper.getLevel().getBlockEntity(pos) instanceof SunkenAltarBlockEntity be) {
                return be;
            }
            throw new IllegalStateException("祭坛应有方块实体");
        }

        /** 按日序号 today 召（gameTime 固定在 {@link #RIG_GAME_TIME}，只看日序号那条规则） */
        SunkenAltarBlock.Outcome summon(ServerPlayer player, long today) {
            return summon(player, today, RIG_GAME_TIME);
        }

        SunkenAltarBlock.Outcome summon(ServerPlayer player, long today, long gameTime) {
            return SunkenAltarBlock.summon(player, helper.getLevel(), pos, new BossDaily.Stamp(today, gameTime));
        }

        /** 祭坛附近世界里的渊潮领主 */
        List<AbyssTyrantEntity> tyrants() {
            return helper.getLevel().getEntitiesOfClass(AbyssTyrantEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(32D), Entity::isAlive);
        }

        void close(ServerPlayer player) {
            for (AbyssTyrantEntity tyrant : tyrants()) {
                tyrant.discard();
            }
            ServerLevel level = helper.getLevel();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int pass = 0; pass < 2; pass++) {
                for (int x = pocket.minX(); x <= pocket.maxX(); x++) {
                    for (int y = pocket.minY(); y <= pocket.maxY(); y++) {
                        for (int z = pocket.minZ(); z <= pocket.maxZ(); z++) {
                            boolean water = !level.getFluidState(at.set(x, y, z)).isEmpty();
                            if (pass == 0 ? water : !level.getBlockState(at).isAir()) {
                                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                            }
                        }
                    }
                }
            }
            ECProtection.clearOnlineOverride(player.getUUID());
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name, GameType mode) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(mode);
        moveTo(helper, player, new Vec3(5.5D, 2D, 2.5D));
        player.setHealth(player.getMaxHealth());
        ECProtection.setOnlineOverride(player);
        return player;
    }

    private static void moveTo(GameTestHelper helper, Entity entity, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        entity.moveTo(at.x, at.y, at.z, 0F, 0F);
    }

    /** 主手拿着潮晶（最多一叠），其余潮晶和异变之心放背包 */
    private static void stock(ServerPlayer player, int crystals, int hearts) {
        player.getInventory().clearContent();
        int held = Math.min(crystals, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECHoannaMaterials.TIDE_CRYSTAL.get(), held));
        if (crystals > held) {
            player.getInventory().add(new ItemStack(ECHoannaMaterials.TIDE_CRYSTAL.get(), crystals - held));
        }
        if (hearts > 0) {
            player.getInventory().add(new ItemStack(ECMobDrops.ABERRANT_HEART.get(), hearts));
        }
    }

    private static int crystals(Player player) {
        return SunkenAltarBlock.count(player.getInventory(), ECHoannaMaterials.TIDE_CRYSTAL.get());
    }

    private static int hearts(Player player) {
        return SunkenAltarBlock.count(player.getInventory(), ECMobDrops.ABERRANT_HEART.get());
    }

    private static int cores(Player player) {
        return SunkenAltarBlock.count(player.getInventory(), ECMobDrops.ANCIENT_CORE.get());
    }

    private static IPlayerData data(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("玩家应挂着玩家数据");
        }
        return data;
    }

    private static boolean done(ServerPlayer player, ResourceLocation id) {
        var advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /**
     * 造一只领主（不进世界、不动），按给的伤害记账（每人一下打实的有效伤害），由第一个人打出最后一下：走真正的死亡结算（分掉落、记首杀）。
     * 没分到 / 不要的人传 null。
     */
    private static AbyssTyrantEntity kill(GameTestHelper helper, List<AbyssTyrantEntity> spawned, ServerPlayer killer, float killerDamage,
                                          ServerPlayer second, float secondDamage, ServerPlayer third, float thirdDamage) {
        AbyssTyrantEntity tyrant = ECSunkenDistrict.ABYSS_TYRANT.get().create(helper.getLevel());
        spawned.add(tyrant);
        moveTo(helper, tyrant, new Vec3(5.5D, 2D, 5.5D));
        tyrant.setNoAi(true);
        tyrant.bossRoster().noteDamage(tyrant.damageSources().playerAttack(killer), killerDamage);
        if (second != null) {
            tyrant.bossRoster().noteDamage(tyrant.damageSources().playerAttack(second), secondDamage);
        }
        if (third != null) {
            tyrant.bossRoster().noteDamage(tyrant.damageSources().playerAttack(third), thirdDamage);
        }
        // setLastHurtByPlayer 把计时设成 tickCount，刚造出来的是 0（= 没被玩家打过），先让它「活过」一会
        tyrant.tickCount = 200;
        tyrant.setLastHurtByPlayer(killer);
        tyrant.setHealth(0F);
        tyrant.die(tyrant.damageSources().playerAttack(killer));
        return tyrant;
    }

    private static boolean chestTable(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof ChestBlockEntity && SunkenPieces.CHEST_TABLE.toString().equals(be.saveWithoutMetadata().getString("LootTable"));
    }

    private static Structure sunken(ServerLevel level) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(SUNKEN);
        if (!(structure instanceof SunkenDistrictStructure)) {
            throw new IllegalStateException("结构 essentialcraft5:sunken_district 没加载或类型不对：" + structure);
        }
        return structure;
    }

    private static BlockState[] snapshot(ServerLevel level, BoundingBox region) {
        BlockState[] states = new BlockState[region.getXSpan() * region.getYSpan() * region.getZSpan()];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = region.minY(); y <= region.maxY(); y++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    states[i++] = level.getBlockState(pos.set(x, y, z));
                }
            }
        }
        return states;
    }

    /** 整片清成空气（先抽水再清别的，不让水流出去；不更新邻居形状） */
    private static void clear(ServerLevel level, BoundingBox region) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int pass = 0; pass < 2; pass++) {
            for (int y = region.maxY(); y >= region.minY(); y--) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        BlockState state = level.getBlockState(pos.set(x, y, z));
                        boolean water = !state.getFluidState().isEmpty();
                        if (pass == 0 ? water : !state.isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
                        }
                    }
                }
            }
        }
    }

    private static JsonObject json(MinecraftServer server, ResourceLocation file) {
        Optional<Resource> resource = server.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            throw new IllegalStateException("找不到数据文件 " + file + "（先跑 tools/gen/hoanna_loot.py？）");
        }
        try (Reader reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("读不了 " + file, e);
        }
    }
}
