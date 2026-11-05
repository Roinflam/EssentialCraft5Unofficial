package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.blockentity.MagicianTableBlockEntity;
import com.flameforge.essentialcraft5.client.hoanna.HoannaClientEvents;
import com.flameforge.essentialcraft5.client.hoanna.HoannaEventHud;
import com.flameforge.essentialcraft5.client.hoanna.HoannaEventVisuals;
import com.flameforge.essentialcraft5.client.hoanna.HoannaPortalOverlay;
import com.flameforge.essentialcraft5.client.hoanna.HoannaSkyRenderer;
import com.flameforge.essentialcraft5.client.hoanna.RadiationOverlay;
import com.flameforge.essentialcraft5.entity.PoisonFumeEntity;
import com.flameforge.essentialcraft5.hoanna.HoannaClientConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaPortalPlacer;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaHazards;
import com.flameforge.essentialcraft5.registry.ECHoannaNature;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.town.HoannaTownPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.underground.ModernShaftPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.underground.OldCatacombsPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.underground.UndergroundStructures;
import com.mojang.datafixers.util.Pair;
import net.minecraft.BlockUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * 展示厅的第六批 6a 部分（{@link DevShowroom} 调，只在 -PecShowroom 时跑）。
 * <p>
 * <b>主世界场景</b>（04_scene_*，和前五批一样沿 X 排在方块排北边）：海纳尔植物、16 色虚空石 / 虚空玻璃、悬浮器与散射器的粒子、
 * 墓穴出口房间的剖面（拼件直接 postProcess，南边一排不放）；远处超平坦上放一座古代墓穴（y 6~32 悬在空中）和一座现代矿井，高空斜看；
 * 主世界的海纳尔之门（{@link HoannaPortalPlacer#place} 直接摆，等同石板换好的门）。
 * <p>
 * <b>海纳尔</b>（05_hoanna_* 与 04_scene_hoanna_town*）：展示厅是单人新建世界，数据包维度照常并进来，真海纳尔世界在（种子 0）。
 * 进世界时（{@link #build}）用群系源按噪声找 7 个群系的取景点（不生成区块），在取景点、两条群系边界、城镇、主取景点周围加区块票，
 * 让海纳尔在拍方块排和前面场景的几分钟里后台生成好；天灾的自然掷骰关掉（{@link HoannaHazardsConfig#setTestChanceMultiplier} 0），
 * 画面上的天灾全用客户端展示厅覆盖（{@link HoannaClientEvents#setShowroomOverride}）。时间改主世界的（海纳尔跟主世界同一个时间）。
 * 每张海纳尔场景的 prepare 先进海纳尔（不在就 {@code teleportTo}），等区块加载完了按真实地面算镜头再 {@link DevShowroom#aim}。
 * 最后三张是主取景点地下的现代矿井（04_scene_modern_shafts_hoanna_*：地道原样、插火把、挖开一侧的剖面，{@link #buildShafts}）。
 * 悬空墓穴 / 矿井和海纳尔矿井这几张除了等固定刻数，还等镜头周围的区块收齐、编译完（{@link ShowroomChunkWait}，审查 M6-1）。
 * 截图清单和要看什么见 batch6a.md §3.6（M3 的 {@code tools/gen/hoanna_shots.py} 量化其中几张）。
 */
final class DevShowroomHoanna {

    private DevShowroomHoanna() {
    }

    /** 预生成海纳尔区块用的票（值是区块坐标本身） */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("ec5_showroom_hoanna", Comparator.comparingLong(ChunkPos::toLong));
    /** 群系取景点搜索半径（格）与步长 */
    private static final int SEARCH_RADIUS = 4000;
    private static final int SEARCH_STEP = 32;
    /** 平视镜头离地（取景点四周 ±32 格估的最高地面）多高 */
    private static final int EYE_ABOVE = 16;
    /** 第一次到一个海纳尔取景点等多少刻（区块发过来、建网格） */
    static final int ARRIVE_WAIT = 220;

    private static final ResourceLocation TOWN = EssentialCraft5.id("hoanna_town");
    private static final ResourceLocation CATACOMBS = EssentialCraft5.id("old_catacombs");
    private static final ResourceLocation SHAFTS = EssentialCraft5.id("modern_shafts");
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;

    /** 群系 → 取景点（x、z 是群系里「四周 ±64 格都是它」最多的那一点，y 不用） */
    private static final Map<ResourceKey<Biome>, BlockPos> SITES = new LinkedHashMap<>();
    @Nullable
    private static BlockPos borderChaos;
    @Nullable
    private static BlockPos borderFrozen;
    @Nullable
    private static ChunkPos townChunk;
    @Nullable
    private static StructureStart townStart;
    @Nullable
    private static BlockPos fumeCenter;
    @Nullable
    private static BlockUtil.FoundRectangle arrivalPortal;
    /** 等区块的海纳尔场景：镜头和看向点四周这么多格（{@link ShowroomChunkWait}） */
    private static final int WAIT_MARGIN = 24;
    /** 矿井剖面沟的宽度（从走廊侧壁往外挖几格；镜头站在沟的另一头，离走廊中线约 12 格） */
    private static final int CUT_DEPTH = 11;
    /** 海纳尔现代矿井（M6，{@link #buildShafts}）：放在哪个区块、放下的起点、拍的那段走廊、沟挖过没有 */
    @Nullable
    private static ChunkPos shaftChunk;
    @Nullable
    private static StructureStart shaftStart;
    @Nullable
    private static ModernShaftPieces.Corridor shaftCorridor;
    private static boolean shaftCut;

    // ================================================================ 入口

    /**
     * 服务端线程（进世界时）：主世界的静态场景从 {@code sceneBase(firstIndex)} 开始摆；海纳尔取景点、预生成票、海纳尔场景排在最后。
     */
    static void build(ServerLevel overworld, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        buildFloraScene(overworld, sceneBase.apply(firstIndex));
        buildVoidDyeScene(overworld, sceneBase.apply(firstIndex + 1));
        buildTownDecorScene(overworld, sceneBase.apply(firstIndex + 2));
        buildPortalOverworldScene(overworld, sceneBase.apply(firstIndex + 3));
        buildCatacombExitScene(overworld, sceneBase.apply(firstIndex + 4));
        buildRadiationScreen();
        buildMagicianTableScreen(overworld, sceneBase.apply(firstIndex + 5));
        buildFloatingStructures(overworld);

        MinecraftServer server = overworld.getServer();
        ServerLevel hoanna = ECHoanna.level(server);
        if (hoanna == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 没有海纳尔世界（数据包删掉了？），跳过 05_hoanna_*");
            return;
        }
        // 天灾不自己发生（改时间跨过黎明会掷日阵）；画面上的天灾都用客户端覆盖
        HoannaHazardsConfig.setTestChanceMultiplier(0D);
        long started = System.nanoTime();
        locateSites(hoanna);
        EssentialCraft5.LOGGER.info("[showroom] 海纳尔取景点（{} ms）：{}；边界 魔力|混沌 {}、冰冻|魔力 {}；城镇区块 {}",
                (System.nanoTime() - started) / 1_000_000L, SITES, borderChaos, borderFrozen, townChunk);
        warmUp(hoanna);
        buildHoannaScenes(server, hoanna);
    }

    /** 服务端线程（场景全部拍完）：回主世界方块排前、时间天气复原、覆盖全撤掉 */
    static void finish(MinecraftServer server, ServerPlayer player, BlockPos origin) {
        HoannaHazardsConfig.clearTestOverrides();
        endSurvivalHud(player);
        player.removeEffect(MobEffects.BLINDNESS);
        ServerLevel overworld = server.overworld();
        overworld.setDayTime(6000);
        overworld.setWeatherParameters(6000, 0, false, false);
        if (player.level() != overworld) {
            player.teleportTo(overworld, origin.getX() + 0.5, origin.getY() + 2, origin.getZ() - 7.5, 0F, 15F);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        Minecraft.getInstance().execute(DevShowroomHoanna::resetClient);
    }

    /**
     * 只要海纳尔取景点、不要 6a 自己那些场景（-PecShowroom13d 用，{@link DevShowroomBatch13d}）：关掉天灾自然掷骰、找 7 个群系的取景点和城镇区块、加预生成票。
     * 没有海纳尔世界返回 null
     */
    @Nullable
    static ServerLevel prepareSitesOnly(ServerLevel overworld) {
        ServerLevel hoanna = ECHoanna.level(overworld.getServer());
        if (hoanna == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 没有海纳尔世界（数据包删掉了？），跳过海纳尔的镜头");
            return null;
        }
        HoannaHazardsConfig.setTestChanceMultiplier(0D);
        if (SITES.isEmpty()) {
            locateSites(hoanna);
            warmUp(hoanna);
        }
        return hoanna;
    }

    /** {@link #prepareSitesOnly} 或 {@link #build} 找到的群系取景点；没找到返回 null */
    @Nullable
    static BlockPos site(ResourceKey<Biome> biome) {
        return SITES.get(biome);
    }

    /** 主取景点附近挑的城镇区块；没挑到返回 null */
    @Nullable
    static ChunkPos townChunk() {
        return townChunk;
    }

    /** 客户端：撤掉所有海纳尔展示厅覆盖（每张海纳尔场景开拍前、全部拍完时） */
    static void resetClient() {
        Minecraft mc = Minecraft.getInstance();
        HoannaClientEvents.clearShowroomOverride();
        HoannaEventHud.clearShowroomTime();
        HoannaEventVisuals.clearShowroomShake();
        RadiationOverlay.setShowroomRadiation(null);
        HoannaClientConfig.setShowroomSkyExtras(null);
        HoannaPortalOverlay.setShowroomStrength(null);
        HoannaSkyRenderer.clearShowroomArc();
        if (mc.options.hideLightningFlash().get()) {
            mc.options.hideLightningFlash().set(false);
        }
    }

    // ================================================================ 主世界场景

    private static void add(String name, @Nullable Consumer<ServerPlayer> prepare, @Nullable Vec3 eye, @Nullable Vec3 target, int delay,
                            @Nullable Consumer<Minecraft> client) {
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, client));
    }

    /** 同上，等够 delay 以后还要等 ready（镜头周围的区块收齐、编译完，{@link ShowroomChunkWait}）才拍 */
    private static void add(String name, @Nullable Consumer<ServerPlayer> prepare, @Nullable Vec3 eye, @Nullable Vec3 target, int delay,
                            @Nullable Consumer<Minecraft> client, ShowroomChunkWait ready) {
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, client, ready));
    }

    /**
     * 海纳尔植物（M1）：草方块上两朵绿花、两丛海纳尔草丛；三块沙子上 1 / 2 / 3 格恐惧仙人掌（自下而上放，四周留空）；
     * 恐惧泥土、魔力果实块、根系、混凝土各一块。草丛在主世界是平原的草色（海纳尔配色在 05_hoanna_biome_* 里看）。
     */
    private static void buildFloraScene(ServerLevel level, BlockPos base) {
        level.setBlockAndUpdate(base, ECHoannaNature.GREEN_FLOWER.get().defaultBlockState());
        level.setBlockAndUpdate(base.east(1), ECHoannaNature.HOANNA_TALL_GRASS.get().defaultBlockState());
        level.setBlockAndUpdate(base.east(2), ECHoannaNature.GREEN_FLOWER.get().defaultBlockState());
        level.setBlockAndUpdate(base.east(1).north(), ECHoannaNature.HOANNA_TALL_GRASS.get().defaultBlockState());
        for (int i = 1; i <= 3; i++) {
            BlockPos sand = base.east(2 + i * 2).below();
            level.setBlockAndUpdate(sand, Blocks.SAND.defaultBlockState());
            for (int h = 0; h < i; h++) {
                level.setBlockAndUpdate(sand.above(1 + h), ECHoannaNature.DREAD_CACTUS.get().defaultBlockState());
            }
        }
        BlockState[] solids = {ECHoannaNature.DREAD_DIRT.get().defaultBlockState(), ECHoannaNature.MAGICAL_FRUIT_BLOCK.get().defaultBlockState(),
                ECHoannaNature.ROOT.get().defaultBlockState(), ECVoid.CONCRETE.get().defaultBlockState()};
        for (int i = 0; i < solids.length; i++) {
            level.setBlockAndUpdate(base.offset(11 + i, 0, -1), solids[i]);
        }
        // 只拍海纳尔时这是第一张场景图：从出生点一下传到东边约 190 格，要等区块发过来、建好网格（同 DevShowroom 第一张场景，审查 M1-7）
        add("hoanna_flora", null, DevShowroom.at(base, 7, 3.2, 8.5), DevShowroom.at(base, 7, 0.6, 0),
                DevShowroom.HOANNA_ONLY ? DevShowroom.FIRST_SCENE_WAIT : 30, null);
        add("hoanna_flora_close", null, DevShowroom.at(base, 5.5, 1.8, 3.5), DevShowroom.at(base, 5.5, 0.8, 0.5), 15, null);
    }

    /**
     * 虚空石 / 虚空玻璃（M7）：左边 16 色虚空石（8 × 2），右边 16 色虚空玻璃（8 × 2），再往右一块 2 × 2 同色玻璃（看同种面剔除）；
     * 玻璃后面两格一排金块和草方块，看半透明。最左边（虚空石左侧隔一格）一面 3 × 3 淡蓝色、石头和玻璃相间的墙，看纹路跨格、跨石头 / 玻璃接不接得上（void_tiling）。
     * void_stone_close 近看虚空石的纹路（染料只染纹路，底图不染色）。
     */
    private static void buildVoidDyeScene(ServerLevel level, BlockPos base) {
        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length; i++) {
            BlockPos stone = base.offset(i % 8, i / 8, 0);
            BlockPos glass = base.offset(9 + i % 8, i / 8, 0);
            level.setBlockAndUpdate(stone, ECVoid.VOID_STONE.get().defaultBlockState().setValue(DyeableBlock.COLOR, colors[i]));
            level.setBlockAndUpdate(glass, ECVoid.VOID_GLASS.get().defaultBlockState().setValue(DyeableBlock.COLOR, colors[i]));
        }
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                level.setBlockAndUpdate(base.offset(18 + dx, dy, 0), ECVoid.VOID_GLASS.get().defaultBlockState().setValue(DyeableBlock.COLOR, DyeColor.PURPLE));
            }
        }
        for (int x = 9; x <= 19; x++) {
            level.setBlockAndUpdate(base.offset(x, 0, -2), (x & 1) == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
        }
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                DyeableBlock block = ((dx + dy) & 1) == 0 ? ECVoid.VOID_STONE.get() : ECVoid.VOID_GLASS.get();
                level.setBlockAndUpdate(base.offset(-4 + dx, dy, 0), block.defaultBlockState().setValue(DyeableBlock.COLOR, DyeColor.LIGHT_BLUE));
            }
        }
        add("void_dye", null, DevShowroom.at(base, 10, 2.5, 11), DevShowroom.at(base, 10, 0.8, 0), 30, null);
        add("void_glass_close", null, DevShowroom.at(base, 14, 1.6, 3.5), DevShowroom.at(base, 14, 0.8, -1), 15, null);
        add("void_stone_close", null, DevShowroom.at(base, 4, 1.6, 3.5), DevShowroom.at(base, 4, 0.8, -1), 15, null);
        add("void_tiling", null, DevShowroom.at(base, -2.5, 1.5, 4.2), DevShowroom.at(base, -2.5, 1.5, 0.5), 15, null);
    }

    /**
     * 悬浮器 / 散射器粒子（M7）：4 个悬浮器摆成房屋地基的四角（露在地面上，城镇里它们埋在地下一格），中间一根 3 格强化石柱顶一个散射器；
     * 镜头约 8 格外。粒子是客户端随机刻冒的，隔一会儿再拍一张。
     */
    private static void buildTownDecorScene(ServerLevel level, BlockPos base) {
        for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, -6}, {6, -6}}) {
            level.setBlockAndUpdate(base.offset(c[0], 0, c[1]), ECVoid.LEVITATOR.get().defaultBlockState());
        }
        for (int y = 0; y < 3; y++) {
            level.setBlockAndUpdate(base.offset(3, y, -3), ECMaterials.FORTIFIED_STONE.get().defaultBlockState());
        }
        level.setBlockAndUpdate(base.offset(3, 3, -3), ECVoid.SPREADER.get().defaultBlockState());
        add("town_decor", null, DevShowroom.at(base, 3, 3.5, 5.5), DevShowroom.at(base, 3, 1.2, -3), 40, null);
        add("town_decor_b", null, DevShowroom.at(base, 3, 3.5, 5.5), DevShowroom.at(base, 3, 1.2, -3), 20, null);

        // 被挡住的三种（LevitatorBlock.openFaces）：左 = 顶上压强化石（只剩腰圈），中 = 四周围强化石（只剩竖线），右 = 顶上和四周都围上（一颗不冒）
        BlockState stone = ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
        BlockPos capped = base.offset(10, 0, -2);
        BlockPos walled = base.offset(13, 0, -2);
        BlockPos buried = base.offset(16, 0, -2);
        for (BlockPos p : new BlockPos[] {capped, walled, buried}) {
            level.setBlockAndUpdate(p, ECVoid.LEVITATOR.get().defaultBlockState());
        }
        level.setBlockAndUpdate(capped.above(), stone);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(walled.relative(side), stone);
            level.setBlockAndUpdate(buried.relative(side), stone);
        }
        level.setBlockAndUpdate(buried.above(), stone);
        add("town_decor_blocked", null, DevShowroom.at(base, 13, 3.5, 4.5), DevShowroom.at(base, 13, 0.8, -2), 40, null);
    }

    /** 主世界的海纳尔之门（M2）：4 × 5 黑曜石框 + 绿门（登记兴趣点，等同石板换好的门）；镜头在 6 格外，站不进门里 */
    private static void buildPortalOverworldScene(ServerLevel level, BlockPos base) {
        HoannaPortalPlacer.place(level, base.above(), Direction.Axis.X, false, true);
        add("05_hoanna_portal_overworld", null, DevShowroom.at(base, 1, 2.5, 7.5), DevShowroom.at(base, 1, 2.5, 0.5), 30, null);
    }

    /**
     * 墓穴出口房间剖面（M6）：一间「出口 + 长草」的房间悬在地面上 3 格，上面接烟囱（顶比房顶高 9 格）；
     * chunkBox 去掉最南边一排，从南边看得见地板到烟囱顶的梯子、正中虚空石上的箱子、地板下的根系。
     */
    private static void buildCatacombExitScene(ServerLevel level, BlockPos base) {
        BlockPos min = base.above(3);
        int s = OldCatacombsPieces.ROOM_SIZE - 1;
        BoundingBox box = new BoundingBox(min.getX(), min.getY(), min.getZ(), min.getX() + s, min.getY() + s, min.getZ() + s);
        OldCatacombsPieces.Room room = new OldCatacombsPieces.Room(0, 20260929L, box, Direction.EAST, false, true, true);
        int top = box.maxY() + 9;
        OldCatacombsPieces.Exit exit = new OldCatacombsPieces.Exit(room, top);
        BoundingBox cut = new BoundingBox(box.minX() - 2, box.minY() - 4, box.minZ() - 2, box.maxX() + 2, top + 2, box.maxZ() - 1);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (StructurePiece piece : List.of(room, exit)) {
            piece.postProcess(level, level.structureManager(), generator, level.getRandom(), cut, new ChunkPos(min), min);
        }
        add("catacomb_exit", null, DevShowroom.at(base, 2.5, 6, 11), DevShowroom.at(base, 2.5, 6, 2), 30, null);
    }

    /** 02_gui_magician_table_void：魔术师台中心放虚空石、MRU 加满，看它在做虚空板 */
    private static void buildMagicianTableScreen(ServerLevel level, BlockPos base) {
        BlockPos pos = base.immutable();
        level.setBlockAndUpdate(pos, ECMagicianTable.MAGICIAN_TABLE.get().defaultBlockState());
        DevShowroom.screens.add(new DevShowroom.ScreenShot("magician_table_void",
                p -> {
                    // 上一张（辐射效果）临时切成了生存模式
                    p.setGameMode(GameType.CREATIVE);
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    Radiation.set(p, 0);
                    Radiation.refresh(p);
                    p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
                    if (p.serverLevel().getBlockEntity(pos) instanceof MagicianTableBlockEntity table) {
                        table.getInventory().setStackInSlot(MagicianTableBlockEntity.SLOT_CENTER, new ItemStack(ECVoid.VOID_STONE.get()));
                        table.getMRUStorage().setMRU(table.getMRUStorage().getMaxMRU());
                    } else {
                        EssentialCraft5.LOGGER.warn("[showroom] {} 没有魔术师台方块实体", pos);
                    }
                },
                p -> {
                    if (p.serverLevel().getBlockEntity(pos) instanceof MagicianTableBlockEntity table) {
                        net.minecraftforge.network.NetworkHooks.openScreen(p, table, pos);
                    }
                }));
    }

    /**
     * 02_gui_radiation_effect：辐射 25 000（辐射 II），打开背包看效果栏两行文字（M4）。
     * 创造模式打开的背包会被原版换成创造物品栏（没有效果栏），这一张临时切生存模式，下一张（魔术师台）切回来。
     */
    private static void buildRadiationScreen() {
        DevShowroom.screens.add(new DevShowroom.ScreenShot("radiation_effect",
                p -> {
                    p.setGameMode(GameType.SURVIVAL);
                    Radiation.set(p, 25000);
                    Radiation.refresh(p);
                    EssentialCraft5.LOGGER.info("[showroom] 辐射 {}，等级 {}", Radiation.get(p), Radiation.level(Radiation.get(p)));
                },
                p -> Minecraft.getInstance().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        EssentialCraft5.LOGGER.info("[showroom] 客户端效果 {}", mc.player.getActiveEffects());
                        mc.setScreen(new InventoryScreen(mc.player));
                    }
                })));
    }

    /**
     * 远处超平坦上悬空放一座古代墓穴（区块 80, 0：y 6~32 浮在地面以上，走廊网一眼看全）和一座现代矿井（区块 80, 40），
     * 用 {@link UndergroundStructures#generateAt} + {@link UndergroundStructures#placeAround}（同 /place structure，超平坦区块生成很快）。
     */
    private static void buildFloatingStructures(ServerLevel level) {
        placeFloating(level, CATACOMBS, new ChunkPos(80, 0), 7, "old_catacombs", 150, 110);
        placeFloating(level, SHAFTS, new ChunkPos(80, 40), 6, "modern_shafts", 120, 40);
    }

    private static void placeFloating(ServerLevel level, ResourceLocation id, ChunkPos chunk, int radius, String name, double back, double up) {
        StructureStart start = StructureStart.INVALID_START;
        for (int i = 0; i < 12 && start.getPieces().size() < 8; i++) {
            start = UndergroundStructures.generateAt(level, id, chunk, 20260929L + i * SEED_STEP);
        }
        if (!start.isValid()) {
            EssentialCraft5.LOGGER.warn("[showroom] 放不了 {}", id);
            return;
        }
        int placed = UndergroundStructures.placeAround(level, start, chunk, radius);
        BoundingBox box = start.getBoundingBox();
        Vec3 center = new Vec3(box.getCenter().getX() + 0.5, box.getCenter().getY(), box.getCenter().getZ() + 0.5);
        EssentialCraft5.LOGGER.info("[showroom] {}：{} 件，放了 {} 个区块，包围盒 {}", id, start.getPieces().size(), placed, box);
        // 从上一个场景一下传到几百格外：光等固定刻数不够（集成时服务端落后 2.5 秒，矿井两张拍成了空天，审查 M6-1），
        // 等够刻数以后再等结构那一片的区块收齐、网格编译完
        Vec3 eye = center.add(0, up, back);
        Vec3 side = center.add(back * 0.8, up * 0.35, back * 0.3);
        add(name, null, eye, center, DevShowroom.FIRST_SCENE_WAIT, null, new ShowroomChunkWait(Level.OVERWORLD, eye, box));
        add(name + "_side", null, side, center, 60, null, new ShowroomChunkWait(Level.OVERWORLD, side, box));
    }

    // ================================================================ 海纳尔：找取景点、预生成

    private static Holder<Biome> biomeAt(ServerLevel hoanna, int x, int z) {
        return hoanna.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(80),
                QuartPos.fromBlock(z), hoanna.getChunkSource().randomState().sampler());
    }

    /** 噪声估的地表高度（含水面；不生成区块） */
    static int estimatedSurface(ServerLevel hoanna, int x, int z) {
        return hoanna.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, hoanna,
                hoanna.getChunkSource().randomState());
    }

    private static void locateSites(ServerLevel hoanna) {
        BlockPos from = new BlockPos(0, 80, 0);
        for (ResourceKey<Biome> biome : ECHoanna.BIOMES) {
            Pair<BlockPos, Holder<Biome>> found = hoanna.findClosestBiome3d(h -> h.is(biome), from, SEARCH_RADIUS, SEARCH_STEP, 1000);
            if (found == null) {
                EssentialCraft5.LOGGER.warn("[showroom] {} 格内找不到海纳尔群系 {}", SEARCH_RADIUS, biome.location());
                continue;
            }
            SITES.put(biome, refine(hoanna, biome, found.getFirst()));
        }
        BlockPos magic = SITES.get(ECHoanna.MAGIC_CORRUPTION);
        if (magic == null) {
            return;
        }
        borderChaos = nearest(hoanna, ECHoanna.CHAOS_CORRUPTION, magic);
        // 冰冻腐化之地可能离得很远：从冰冻取景点往回找最近的魔力腐化之地，边界镜头站在冰冻那一侧（名字仍按「冰冻 | 魔力」）
        BlockPos frozen = SITES.get(ECHoanna.FROZEN_CORRUPTION);
        borderFrozen = frozen == null ? null : nearest(hoanna, ECHoanna.MAGIC_CORRUPTION, frozen);
        // 现代矿井剖面（M6）：放在主取景点所在区块（陆地、主取景点的 12 圈票早就生成好了）；它排在所有海纳尔场景最后，挖沟不影响别的镜头
        shaftChunk = new ChunkPos(magic);
        // 城镇：离主取景点 200~600 格、八个方向里第一个不是海、估的地面不低于海平面的地方
        for (int dist = 200; dist <= 600 && townChunk == null; dist += 100) {
            for (int i = 0; i < 8 && townChunk == null; i++) {
                double angle = Math.PI / 4 * i;
                int x = magic.getX() + (int) Math.round(Math.cos(angle) * dist);
                int z = magic.getZ() + (int) Math.round(Math.sin(angle) * dist);
                if (!biomeAt(hoanna, x, z).is(ECHoanna.HOANNA_SEA) && estimatedSurface(hoanna, x, z) >= 63) {
                    townChunk = new ChunkPos(new BlockPos(x, 0, z));
                }
            }
        }
    }

    /** 在找到的点周围 ±128 格（步长 32）挑「四周 ±64 格 25 个采样点里这个群系最多」的一点，镜头放在群系中间而不是边上 */
    private static BlockPos refine(ServerLevel hoanna, ResourceKey<Biome> biome, BlockPos found) {
        BlockPos best = found;
        int bestScore = -1;
        for (int dx = -128; dx <= 128; dx += 32) {
            for (int dz = -128; dz <= 128; dz += 32) {
                int x = found.getX() + dx;
                int z = found.getZ() + dz;
                if (!biomeAt(hoanna, x, z).is(biome)) {
                    continue;
                }
                int score = 0;
                for (int ox = -64; ox <= 64; ox += 32) {
                    for (int oz = -64; oz <= 64; oz += 32) {
                        if (biomeAt(hoanna, x + ox, z + oz).is(biome)) {
                            score++;
                        }
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = new BlockPos(x, 0, z);
                }
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos nearest(ServerLevel hoanna, ResourceKey<Biome> biome, BlockPos from) {
        Pair<BlockPos, Holder<Biome>> found = hoanna.findClosestBiome3d(h -> h.is(biome), from.atY(80), 1500, 8, 1000);
        return found == null ? null : found.getFirst();
    }

    /** 加区块票：主取景点 12 圈（= 视距 12），其余取景点 10 圈，边界 8 圈，城镇 6 圈；在后台生成，拍到海纳尔时大多已经好了 */
    private static void warmUp(ServerLevel hoanna) {
        int tickets = 0;
        for (Map.Entry<ResourceKey<Biome>, BlockPos> site : SITES.entrySet()) {
            tickets += ticket(hoanna, site.getValue(), site.getKey().equals(ECHoanna.MAGIC_CORRUPTION) ? 12 : 10);
        }
        if (borderChaos != null) {
            tickets += ticket(hoanna, borderChaos, 8);
        }
        if (borderFrozen != null) {
            tickets += ticket(hoanna, borderFrozen, 8);
        }
        if (townChunk != null) {
            tickets += ticket(hoanna, townChunk.getWorldPosition(), 6);
        }
        if (shaftChunk != null) {
            // 矿井放进 ±7 区块（在主取景点那张 12 圈票里）；单独加一张 8 圈的，主取景点的票以后改小了也照样预生成
            tickets += ticket(hoanna, shaftChunk.getWorldPosition(), 8);
        }
        EssentialCraft5.LOGGER.info("[showroom] 海纳尔预生成票 {} 张", tickets);
    }

    static int ticket(ServerLevel hoanna, BlockPos pos, int radius) {
        ChunkPos chunk = new ChunkPos(pos);
        hoanna.getChunkSource().addRegionTicket(TICKET, chunk, radius, chunk);
        return 1;
    }

    // ================================================================ 海纳尔：场景

    /** 进海纳尔（不在就传过去）、改时间、天放晴（下雨那张在 extra 里再设成下雨） */
    private static void enter(ServerPlayer player, ServerLevel hoanna, BlockPos site, int dayTime) {
        endSurvivalHud(player);
        // 05_hoanna_fumes_blind 挂的失明
        player.removeEffect(MobEffects.BLINDNESS);
        MinecraftServer server = hoanna.getServer();
        server.overworld().setDayTime(dayTime);
        if (server.overworld().isRaining()) {
            server.overworld().setWeatherParameters(6000, 0, false, false);
        }
        if (player.level() != hoanna) {
            player.teleportTo(hoanna, site.getX() + 0.5, 200, site.getZ() + 0.5, player.getYRot(), player.getXRot());
            // 换维度时客户端新建的玩家不在飞（创造模式只给 mayfly），服务端却还记着在飞、aim 不会重发：不补这一句，镜头每张都往下掉
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
    }

    /** 生存 HUD 那张换下来的盔甲、副手、经验等级（{@link #endSurvivalHud} 放回去）；null = 没在拍那张 */
    @Nullable
    private static ItemStack[] survivalHudSaved;
    private static int survivalHudLevel;
    private static final EquipmentSlot[] SURVIVAL_HUD_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND};

    /**
     * 05_hoanna_event_hud_collapsed_survival：切生存模式，满血外加吸收 4 颗心（两行心）、一身铁甲（护甲条）、副手盾牌（副手槽）、30 级（经验条和等级数字），
     * 看提示条收起态和它们挨得多近。切生存会把飞行关掉，随后 aim 又打开（单人服允许飞），镜头不掉。下一张 {@link #enter} 时切回创造、东西放回去。
     */
    private static void beginSurvivalHud(ServerPlayer player) {
        if (survivalHudSaved == null) {
            survivalHudSaved = new ItemStack[SURVIVAL_HUD_SLOTS.length];
            for (int i = 0; i < SURVIVAL_HUD_SLOTS.length; i++) {
                survivalHudSaved[i] = player.getItemBySlot(SURVIVAL_HUD_SLOTS[i]).copy();
            }
            survivalHudLevel = player.experienceLevel;
        }
        player.setGameMode(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        player.setExperienceLevels(30);
        player.setHealth(player.getMaxHealth());
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1, false, false, false));
    }

    /** 生存 HUD 那张拍完：切回创造、飞起来，盔甲 / 副手 / 等级放回去，吸收和这几刻攒的辐射清掉；没拍过什么都不做 */
    private static void endSurvivalHud(ServerPlayer player) {
        if (survivalHudSaved == null) {
            return;
        }
        for (int i = 0; i < SURVIVAL_HUD_SLOTS.length; i++) {
            player.setItemSlot(SURVIVAL_HUD_SLOTS[i], survivalHudSaved[i]);
        }
        player.setExperienceLevels(survivalHudLevel);
        survivalHudSaved = null;
        player.removeEffect(MobEffects.ABSORPTION);
        player.setAbsorptionAmount(0F);
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        Radiation.set(player, 0);
        Radiation.refresh(player);
    }

    /** 真实地面（这一列的区块同步加载到完整）：不算树叶的最高方块上面一格 */
    static int ground(ServerLevel hoanna, int x, int z) {
        hoanna.getChunk(x >> 4, z >> 4);
        return hoanna.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** 平视镜头的眼睛：取景点四周 ±32 格（9 个点）估的最高地面（至少海平面）再高 {@value #EYE_ABOVE} 格 */
    static Vec3 eye(ServerLevel hoanna, BlockPos site) {
        int top = 63;
        for (int dx = -32; dx <= 32; dx += 32) {
            for (int dz = -32; dz <= 32; dz += 32) {
                top = Math.max(top, estimatedSurface(hoanna, site.getX() + dx, site.getZ() + dz));
            }
        }
        return new Vec3(site.getX() + 0.5, top + EYE_ABOVE, site.getZ() + 0.5);
    }

    /** 一张海纳尔场景：client 先撤掉所有覆盖再做自己的；prepare 进海纳尔、改时间、再做自己的；镜头由 look 按取景点算 */
    static void hoanna(String name, ServerLevel hoanna, BlockPos site, int dayTime, int delay, boolean hud,
                       @Nullable Consumer<Minecraft> client, @Nullable Consumer<ServerPlayer> extra, Look look) {
        hoanna(name, hoanna, site, dayTime, delay, hud, client, extra, look, null);
    }

    /**
     * 同上；wait 不为 null 时：站好位后告诉它镜头在哪，等镜头和看向点四周 {@value #WAIT_MARGIN} 格的区块收齐、编译完再拍（最少仍等 delay 刻）；
     * look 取景失败（返回 null）就不等
     */
    static void hoanna(String name, ServerLevel hoanna, BlockPos site, int dayTime, int delay, boolean hud,
                       @Nullable Consumer<Minecraft> client, @Nullable Consumer<ServerPlayer> extra, Look look,
                       @Nullable ShowroomChunkWait wait) {
        Consumer<ServerPlayer> prepare = player -> {
            enter(player, hoanna, site, dayTime);
            if (extra != null) {
                extra.accept(player);
            }
            Vec3[] cam = look.at(hoanna, site);
            if (cam != null) {
                DevShowroom.aim(player, cam[0], cam[1]);
                if (wait != null) {
                    wait.target(cam[0], ShowroomChunkWait.around(cam[0], cam[1], WAIT_MARGIN));
                }
            } else if (wait != null) {
                wait.skip();
            }
        };
        Consumer<Minecraft> reset = mc -> {
            resetClient();
            mc.getToasts().clear();
            mc.options.hideGui = !hud;
            if (client != null) {
                client.accept(mc);
            }
        };
        if (wait == null) {
            add(name, prepare, null, null, delay, reset);
        } else {
            add(name, prepare, null, null, delay, reset, wait);
        }
    }

    /** 镜头：返回 {眼睛, 看向}；null = 不动 */
    @FunctionalInterface
    interface Look {
        @Nullable
        Vec3[] at(ServerLevel hoanna, BlockPos site);
    }

    /** 从取景点上空朝一个方向看（dx、dz 是水平方向，dy 是看向点比眼睛高多少，距离 100 格） */
    static Look toward(double dx, double dy, double dz) {
        return (level, site) -> {
            Vec3 eye = eye(level, site);
            double len = Math.max(1E-3, Math.sqrt(dx * dx + dz * dz));
            return new Vec3[] {eye, eye.add(dx / len * 100, dy, dz / len * 100)};
        };
    }

    private static void buildHoannaScenes(MinecraftServer server, ServerLevel hoanna) {
        BlockPos main = SITES.get(ECHoanna.MAGIC_CORRUPTION);
        if (main == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 没找到魔力腐化之地，05_hoanna_* 只拍能找到的群系");
        } else {
            buildMainSite(server, hoanna, main);
        }
        for (Map.Entry<ResourceKey<Biome>, BlockPos> site : SITES.entrySet()) {
            String biome = site.getKey().location().getPath();
            hoanna("05_hoanna_biome_" + biome + "_noon", hoanna, site.getValue(), 6000, ARRIVE_WAIT, false, null, null, toward(1, -6, 0.15));
        }
        if (main != null) {
            border("05_hoanna_border_magic_chaos", hoanna, main, borderChaos);
        }
        BlockPos frozen = SITES.get(ECHoanna.FROZEN_CORRUPTION);
        if (frozen != null) {
            border("05_hoanna_border_frozen_magic", hoanna, frozen, borderFrozen);
        }
        if (townChunk != null) {
            buildTown(hoanna, townChunk);
        }
        if (shaftChunk != null) {
            buildShafts(hoanna, shaftChunk);
        }
    }

    /** 群系边界：站在 from 那个群系一侧、离边界约 50 格，看向另一边 */
    private static void border(String name, ServerLevel hoanna, BlockPos from, @Nullable BlockPos edge) {
        if (edge == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 没找到 {} 的边界", name);
            return;
        }
        double dx = edge.getX() - from.getX();
        double dz = edge.getZ() - from.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        BlockPos stand = edge.offset((int) Math.round(-dx / len * 50), 0, (int) Math.round(-dz / len * 50));
        hoanna(name, hoanna, stand, 6000, ARRIVE_WAIT, false, null, null, toward(dx, -8, dz));
    }

    private static void buildMainSite(MinecraftServer server, ServerLevel hoanna, BlockPos main) {
        Look east = toward(1, -6, 0);
        Look north = toward(0, 45, -1);
        // ---- 天空（M3 §3.3）
        // 只拍海纳尔（-PecShowroomHoanna）时没有方块排那几分钟给区块预生成，第一张多等一会儿
        hoanna("05_hoanna_noon_east", hoanna, main, 6000, DevShowroom.HOANNA_ONLY ? 900 : ARRIVE_WAIT, false, null, null, east);
        hoanna("05_hoanna_noon_up", hoanna, main, 6000, 25, false, null, null, (level, site) -> {
            Vec3 eye = eye(level, site);
            return new Vec3[] {eye, eye.add(12, 100, 0)};
        });
        hoanna("05_hoanna_sunset_west", hoanna, main, 12500, 40, false, null, null, toward(-1, -4, 0));
        hoanna("05_hoanna_magic_dusk", hoanna, main, 13000, 40, false, null, null, toward(-0.6, 8, 1));
        hoanna("05_hoanna_midnight_up", hoanna, main, 18000, 40, false, null, null, (level, site) -> {
            Vec3 eye = eye(level, site);
            return new Vec3[] {eye, eye.add(0, 100, -20)};
        });
        hoanna("05_hoanna_midnight_north", hoanna, main, 18000, 25, false, null, null, north);
        // 电弧要在镜头站好以后生成（它按当前朝向放在正前方）：和上一张同一个机位
        hoanna("05_hoanna_midnight_arc", hoanna, main, 18000, 6, false, mc -> HoannaSkyRenderer.showroomArc(true), null, north);
        hoanna("05_hoanna_extras_off", hoanna, main, 18000, 15, false, mc -> HoannaClientConfig.setShowroomSkyExtras(false), null, north);
        hoanna("05_hoanna_hide_flash", hoanna, main, 18000, 8, false, mc -> {
            mc.options.hideLightningFlash().set(true);
            HoannaSkyRenderer.showroomArc(true);
        }, null, north);
        // ---- 天灾画面（M3 §3.4；覆盖只改客户端画面，服务端不开天灾）
        hoanna("05_hoanna_darkness_noon", hoanna, main, 6000, 40, false,
                mc -> HoannaClientEvents.setShowroomOverride(HoannaEvent.DARKNESS, 1F), null, east);
        hoanna("05_hoanna_darkness_midnight", hoanna, main, 18000, 45, false,
                mc -> HoannaClientEvents.setShowroomOverride(HoannaEvent.DARKNESS, 1F), DevShowroomHoanna::placeTorches, DevShowroomHoanna::torchView);
        hoanna("05_hoanna_sun_array_noon", hoanna, main, 6000, 40, false,
                mc -> HoannaClientEvents.setShowroomOverride(HoannaEvent.SUN_ARRAY, 1F), null, toward(1, 60, 0));
        hoanna("05_hoanna_sun_array_hud", hoanna, main, 6000, 10, true, mc -> {
            HoannaClientEvents.setShowroomOverride(HoannaEvent.SUN_ARRAY, 1F);
            HoannaEventHud.setShowroomTime(9000, 12000, true);
        }, null, toward(1, 60, 0));
        hoanna("05_hoanna_event_hud_collapsed", hoanna, main, 6000, 10, true, mc -> {
            HoannaClientEvents.setShowroomOverride(HoannaEvent.DARKNESS, 1F);
            HoannaEventHud.setShowroomTime(35400, 72000, false);
        }, null, east);
        // 收起态在快捷栏左端的左上方：创造模式只画快捷栏，再拍一张生存模式的，看它碰没碰到两行心、护甲、经验条和副手槽
        hoanna("05_hoanna_event_hud_collapsed_survival", hoanna, main, 6000, 10, true, mc -> {
            HoannaClientEvents.setShowroomOverride(HoannaEvent.DARKNESS, 1F);
            HoannaEventHud.setShowroomTime(35400, 72000, false);
        }, DevShowroomHoanna::beginSurvivalHud, east);
        hoanna("05_hoanna_quake", hoanna, main, 6000, 20, false, mc -> {
            HoannaClientEvents.setShowroomOverride(HoannaEvent.EARTHQUAKE, 1F);
            HoannaEventVisuals.setShowroomShakeTime(0.115F);
        }, null, east);
        // ---- 辐射晕影（同一机位、开 HUD：晕影是 HUD 层）
        hoanna("05_hoanna_radiation_off", hoanna, main, 6000, 15, true, mc -> RadiationOverlay.setShowroomRadiation(0), null, east);
        hoanna("05_hoanna_radiation_10k", hoanna, main, 6000, 8, true, mc -> RadiationOverlay.setShowroomRadiation(10000), null, east);
        hoanna("05_hoanna_radiation_30k", hoanna, main, 6000, 8, true, mc -> RadiationOverlay.setShowroomRadiation(30000), null, east);
        // ---- 毒雾（M4 的实体 + M3 的雾团）：主取景点北边 16 格摆 8 只（不自然生成的在哪都活满 50 秒；NoAI 待在镜头里）
        hoanna("05_hoanna_fumes_outside", hoanna, main, 6000, 40, false,
                mc -> HoannaClientEvents.setShowroomOverride(HoannaEvent.FUMES, 1F), p -> spawnFumes(p, hoanna, main), (level, site) -> {
                    if (fumeCenter == null) {
                        return null;
                    }
                    Vec3 c = Vec3.atBottomCenterOf(fumeCenter);
                    return new Vec3[] {eye(level, site), c.add(0, 2, 0)};
                });
        hoanna("05_hoanna_fumes_inside", hoanna, main, 6000, 15, false,
                mc -> HoannaClientEvents.setShowroomOverride(HoannaEvent.FUMES, 1F), null, (level, site) -> {
                    if (fumeCenter == null) {
                        return null;
                    }
                    Vec3 c = Vec3.atBottomCenterOf(fumeCenter).add(0, 3, 0);
                    return new Vec3[] {c, c.add(10, -0.5, 0)};
                });
        // 失明时雾团跟着淡没：和 fumes_outside 同一个机位，应是一片黑雾、看不到亮着的黄绿雾团（失明在下一张 enter 时摘掉）
        hoanna("05_hoanna_fumes_blind", hoanna, main, 6000, 15, false,
                mc -> HoannaClientEvents.setShowroomOverride(HoannaEvent.FUMES, 1F),
                p -> p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 400, 0, false, false, false)), (level, site) -> {
                    if (fumeCenter == null) {
                        return null;
                    }
                    Vec3 c = Vec3.atBottomCenterOf(fumeCenter);
                    return new Vec3[] {eye(level, site), c.add(0, 2, 0)};
                });
        // ---- 传送门（M2）：在主取景点东南自动建一扇，再拍进门暗角一半
        hoanna("05_hoanna_portal_arrival", hoanna, main, 6000, 40, false, null, p -> buildArrivalPortal(p, hoanna, main), (level, site) -> {
            if (arrivalPortal == null) {
                return null;
            }
            BlockPos c = arrivalPortal.minCorner;
            return new Vec3[] {new Vec3(c.getX() + 1, c.getY() + 2.2, c.getZ() + 7.5), new Vec3(c.getX() + 1, c.getY() + 1.5, c.getZ() + 0.5)};
        });
        hoanna("05_hoanna_portal_overlay_half", hoanna, main, 6000, 10, true, mc -> HoannaPortalOverlay.setShowroomStrength(0.5F), null, (level, site) -> null);
        // ---- 下雨（雨量每刻只涨 0.01，要等 100 刻以上）：主取景点最后一张；下一张换取景点时 enter 把天放晴，那边本来就要等 220 刻
        hoanna("05_hoanna_rain", hoanna, main, 6000, 130, false, null,
                p -> p.server.overworld().setWeatherParameters(0, 6000, true, false), east);
    }

    /** 黑暗午夜：在主取景点东边地面插 3 根火把（地面按真实高度图） */
    private static void placeTorches(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos main = SITES.get(ECHoanna.MAGIC_CORRUPTION);
        if (main == null) {
            return;
        }
        for (int dz = -1; dz <= 1; dz++) {
            int x = main.getX() + 5;
            int z = main.getZ() + dz * 2;
            int y = ground(level, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                level.setBlockAndUpdate(pos, Blocks.TORCH.defaultBlockState());
            }
        }
    }

    private static Vec3[] torchView(ServerLevel level, BlockPos main) {
        int gy = ground(level, main.getX(), main.getZ());
        Vec3 eye = new Vec3(main.getX() + 0.5, gy + 1.7, main.getZ() + 0.5);
        int ty = ground(level, main.getX() + 5, main.getZ());
        return new Vec3[] {eye, new Vec3(main.getX() + 5.5, ty + 0.3, main.getZ() + 0.5)};
    }

    private static void spawnFumes(ServerPlayer player, ServerLevel hoanna, BlockPos main) {
        // 和平难度会马上删掉怪物类别的实体（毒雾照原作是怪物）：场景拍完 DevShowroom.finishScenes 调回和平，它们随之消失
        player.server.setDifficulty(Difficulty.EASY, true);
        // 摆在主镜头正前方（东边 14 格、低 9 格）的空中：NoAI 的实体不动也不下落，镜头不会卡进山坡；地面在哪都不影响雾团的样子
        Vec3 eye = eye(hoanna, main);
        BlockPos.MutableBlockPos center = BlockPos.containing(eye.x + 14, eye.y - 9, eye.z).mutable();
        while (!hoanna.getBlockState(center).isAir() && center.getY() < eye.y) {
            center.move(Direction.UP);
        }
        fumeCenter = center.immutable();
        int cx = fumeCenter.getX();
        int cz = fumeCenter.getZ();
        int spawned = 0;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 / 8 * i;
            double r = i % 2 == 0 ? 1.5 : 3.5;
            int x = Mth.floor(cx + Math.cos(angle) * r);
            int z = Mth.floor(cz + Math.sin(angle) * r);
            PoisonFumeEntity fume = ECHoannaHazards.POISON_FUME.get().create(hoanna);
            if (fume == null) {
                continue;
            }
            fume.moveTo(x + 0.5, fumeCenter.getY() + (i % 3) * 0.5, z + 0.5, 0F, 0F);
            //noinspection deprecation
            fume.finalizeSpawn(hoanna, hoanna.getCurrentDifficultyAt(fume.blockPosition()), MobSpawnType.COMMAND, null, null);
            fume.setNoAi(true);
            if (hoanna.addFreshEntity(fume)) {
                spawned++;
            }
        }
        EssentialCraft5.LOGGER.info("[showroom] 海纳尔摆了 {} 只毒雾（{}）", spawned, fumeCenter);
    }

    private static void buildArrivalPortal(ServerPlayer player, ServerLevel hoanna, BlockPos main) {
        int x = main.getX() + 10;
        int z = main.getZ() + 10;
        BlockPos target = new BlockPos(x, ground(hoanna, x, z), z);
        arrivalPortal = HoannaPortalPlacer.build(player, hoanna, target, Direction.Axis.X, 16).orElse(null);
        EssentialCraft5.LOGGER.info("[showroom] 海纳尔自动建门：目标 {} → {}", target, arrivalPortal == null ? "没建成" : arrivalPortal.minCorner);
    }

    // ================================================================ 海纳尔：城镇

    /**
     * 城镇（M5）：在城镇区块照 /place structure 放一座（挑有塔的种子），高空斜看整镇、塔顶箱子、最大的弹坑、最高那栋房子的上面几层。
     */
    private static void buildTown(ServerLevel hoanna, ChunkPos chunk) {
        BlockPos site = chunk.getMiddleBlockPosition(0);
        hoanna("hoanna_town", hoanna, site, 6000, 160, false, null, p -> placeTown(hoanna, chunk), (level, s) -> {
            if (townStart == null) {
                return null;
            }
            BoundingBox box = townStart.getBoundingBox();
            int cx = box.getCenter().getX();
            int cz = box.getCenter().getZ();
            int gy = ground(level, cx, cz);
            return new Vec3[] {new Vec3(cx + 0.5, gy + 80, cz + 75), new Vec3(cx + 0.5, gy, cz + 0.5)};
        });
        hoanna("hoanna_town_tower_top", hoanna, site, 6000, 40, false, null, null, (level, s) -> {
            HoannaTownPieces.Tower tower = townStart == null ? null : townStart.getPieces().stream()
                    .filter(HoannaTownPieces.Tower.class::isInstance).map(HoannaTownPieces.Tower.class::cast).findFirst().orElse(null);
            if (tower == null || !tower.hasGround()) {
                EssentialCraft5.LOGGER.warn("[showroom] 城镇里没有塔");
                return null;
            }
            BoundingBox box = tower.getBoundingBox();
            Vec3 top = new Vec3(box.getCenter().getX() + 0.5, tower.groundY() + 57, box.getCenter().getZ() + 0.5);
            return new Vec3[] {top.add(0, 4, 8), top};
        });
        hoanna("hoanna_town_crater", hoanna, site, 6000, 40, false, null, null, (level, s) -> {
            HoannaTownPieces.Crater crater = townStart == null ? null : townStart.getPieces().stream()
                    .filter(HoannaTownPieces.Crater.class::isInstance).map(HoannaTownPieces.Crater.class::cast)
                    .max(Comparator.comparingDouble(HoannaTownPieces.Crater::radius)).orElse(null);
            if (crater == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 城镇里没有弹坑");
                return null;
            }
            Vec3 c = Vec3.atCenterOf(crater.center());
            return new Vec3[] {c.add(0, 34, 26), c.add(0, -6, 0)};
        });
        // 最高的那栋房子的上面几层：看缺损的零散小洞和房子弹坑炸出的成团大洞，一楼应完好
        hoanna("hoanna_town_house_top", hoanna, site, 6000, 40, false, null, null, (level, s) -> {
            HoannaTownPieces.House house = tallestHouse();
            if (house == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 城镇里没有房子");
                return null;
            }
            BoundingBox box = house.getBoundingBox();
            int top = house.storeyY(house.floors());
            Vec3 target = new Vec3(box.getCenter().getX() + 0.5, Math.max(house.storeyY(1), top - 3), box.getCenter().getZ() + 0.5);
            double back = house.radius() * 2 + 10;
            EssentialCraft5.LOGGER.info("[showroom] 城镇房子 F = {}、半径 {}、包围盒 {}", house.floors(), house.radius(), box);
            return new Vec3[] {target.add(back * 0.6, 6, back), target};
        });
    }

    /**
     * 拍上面几层弹坑的房子：House3（半径 3、原作 1~9 层，城镇里最高的那种）里层数最多的；城镇里没有 House3 时退回层数最多的那栋。
     * 没放城镇或没有房子时 null。
     */
    @Nullable
    private static HoannaTownPieces.House tallestHouse() {
        if (townStart == null) {
            return null;
        }
        List<HoannaTownPieces.House> houses = houses(townStart);
        Comparator<HoannaTownPieces.House> byFloors = Comparator.comparingInt(HoannaTownPieces.House::floors);
        return houses.stream().filter(house -> house.radius() == 3).max(byFloors)
                .orElseGet(() -> houses.stream().max(byFloors).orElse(null));
    }

    private static List<HoannaTownPieces.House> houses(StructureStart start) {
        return start.getPieces().stream()
                .filter(HoannaTownPieces.House.class::isInstance).map(HoannaTownPieces.House.class::cast).toList();
    }

    /** 有 House3 至少 {@value #HOUSE3_MIN_FLOORS} 层（上面几层才有房子的弹坑可拍） */
    private static boolean hasTallHouse3(StructureStart start) {
        return houses(start).stream().anyMatch(house -> house.radius() == 3 && house.floors() >= HOUSE3_MIN_FLOORS);
    }

    private static final int HOUSE3_MIN_FLOORS = 4;

    private static void placeTown(ServerLevel hoanna, ChunkPos chunk) {
        if (townStart != null) {
            return;
        }
        // 挑有塔、又有一栋够高的 House3 的种子；20 个里都没有就用第一个有塔的
        StructureStart start = StructureStart.INVALID_START;
        StructureStart withTower = null;
        for (int i = 0; i < 20; i++) {
            StructureStart candidate = UndergroundStructures.generateAt(hoanna, TOWN, chunk, 20260929L + i * SEED_STEP);
            if (!candidate.isValid() || candidate.getPieces().stream().noneMatch(HoannaTownPieces.Tower.class::isInstance)) {
                start = withTower == null ? candidate : start;
                continue;
            }
            if (withTower == null) {
                withTower = candidate;
                start = candidate;
            }
            if (hasTallHouse3(candidate)) {
                start = candidate;
                break;
            }
        }
        if (!start.isValid()) {
            EssentialCraft5.LOGGER.warn("[showroom] 海纳尔城镇放不了（{}）", chunk);
            return;
        }
        int placed = UndergroundStructures.placeAround(hoanna, start, chunk, 5);
        townStart = start;
        List<String> kinds = new ArrayList<>();
        start.getPieces().forEach(piece -> kinds.add(piece.getClass().getSimpleName()));
        EssentialCraft5.LOGGER.info("[showroom] 海纳尔城镇 {}：{} 件（{}），放了 {} 个区块，包围盒 {}", chunk, kinds.size(), kinds, placed,
                start.getBoundingBox());
        List<String> houses = new ArrayList<>();
        houses(start).forEach(house -> houses.add("F" + house.floors() + "/r" + house.radius()));
        EssentialCraft5.LOGGER.info("[showroom] 城镇里的房子（层数 / 半径）：{}", houses);
    }

    // ================================================================ 海纳尔：现代矿井剖面（M6，审查 M6-1）

    /**
     * 现代矿井（batch6a §9「在海纳尔放一座，挖开一面拍剖面」）：在主取景点区块照 /place structure 放一座（16 个种子里挑最浅的，沟挖得少），
     * 挑一段走廊拍：有运输矿车的优先，其次铺铁轨、够长（≥ 3 组支架）、离地表近；洞穴蜘蛛走廊不挑（满地蜘蛛网挡镜头）。
     * <ol>
     *   <li>{@code modern_shafts_hoanna_tunnel}：站在走廊一头往里看，原样（只有结构自己的墙上火把，每组两侧各 5%）——没光的地道有多黑；</li>
     *   <li>{@code modern_shafts_hoanna_tunnel_torch}：同一机位，脚边插一根火把（玩家进矿井的样子）——虚空石横梁、虚空栅栏立柱在火把光下看不看得清；</li>
     *   <li>{@code modern_shafts_hoanna_cut}：走廊一侧挖开 {@value #CUT_DEPTH} 格宽、从地板下 2 格一直挖到地表的沟（天光照进来），站在沟里横着看：
     *       支架、墙上火把挂没挂住、铁轨方向、矿车、蜘蛛网、脚下补的虚空石。</li>
     * </ol>
     * 三张都等镜头周围的区块收齐、编译完再拍（{@link ShowroomChunkWait}）。排在所有海纳尔场景最后，挖沟不影响别的镜头。
     * 放进的是完整区块：拼件的栅栏、墙上火把当场按邻居重算形状（{@link HoannaStructurePiece#requestShapeUpdate}，原版名单里的墙上火把另打一行
     * 「Trying to mark a block for PostProcessing」警告，同 /place structure，无害）；挖沟后把走廊里的栅栏再重算一遍（原来连着被挖掉的岩壁）。
     */
    private static void buildShafts(ServerLevel hoanna, ChunkPos chunk) {
        BlockPos site = chunk.getMiddleBlockPosition(0);
        Look tunnel = (level, s) -> shaftCorridor == null ? null : tunnelView(shaftCorridor);
        hoanna("modern_shafts_hoanna_tunnel", hoanna, site, 6000, 100, false, null, p -> placeShafts(hoanna, chunk), tunnel,
                new ShowroomChunkWait(ECHoanna.LEVEL));
        hoanna("modern_shafts_hoanna_tunnel_torch", hoanna, site, 6000, 20, false, null, p -> placeTunnelTorch(hoanna), tunnel,
                new ShowroomChunkWait(ECHoanna.LEVEL));
        hoanna("modern_shafts_hoanna_cut", hoanna, site, 6000, 40, false, null, p -> carveCut(hoanna),
                (level, s) -> shaftCorridor == null ? null : cutView(shaftCorridor), new ShowroomChunkWait(ECHoanna.LEVEL));
    }

    private static void placeShafts(ServerLevel hoanna, ChunkPos chunk) {
        if (shaftStart != null) {
            return;
        }
        // 毒雾那张把难度调成了简单：别让附近洞穴蜘蛛走廊的刷怪笼往镜头里刷蜘蛛（场景拍完 DevShowroom.finishScenes 本来也调回和平）
        hoanna.getServer().setDifficulty(Difficulty.PEACEFUL, true);
        StructureStart best = StructureStart.INVALID_START;
        for (int i = 0; i < 16; i++) {
            StructureStart start = UndergroundStructures.generateAt(hoanna, SHAFTS, chunk, 20260929L + i * SEED_STEP);
            if (start.isValid() && start.getPieces().size() >= 30
                    && (!best.isValid() || start.getBoundingBox().maxY() > best.getBoundingBox().maxY())) {
                best = start;
            }
        }
        if (!best.isValid()) {
            EssentialCraft5.LOGGER.warn("[showroom] 海纳尔矿井放不了（{}）", chunk);
            return;
        }
        // 布局最远约 105 格（±7 区块），都在主取景点的预生成票里
        int placed = UndergroundStructures.placeAround(hoanna, best, chunk, 7);
        shaftStart = best;
        shaftCorridor = pickCorridor(hoanna, best);
        ModernShaftPieces.Corridor corridor = shaftCorridor;
        EssentialCraft5.LOGGER.info("[showroom] 海纳尔矿井 {}：{} 件，放了 {} 个区块，包围盒 {}；拍的走廊 {}", chunk, best.getPieces().size(), placed,
                best.getBoundingBox(), corridor == null ? "没有合适的" : corridor.getBoundingBox() + " 朝" + corridor.getOrientation()
                        + " " + corridor.numSections() + " 组、铁轨 " + corridor.hasRails());
    }

    /** 立柱齐全（碰到液体的区块里整段不生成，同原版）、不是洞穴蜘蛛走廊、≥ 3 组支架的走廊里挑分最高的 */
    @Nullable
    private static ModernShaftPieces.Corridor pickCorridor(ServerLevel level, StructureStart start) {
        Block fence = HoannaStructureBlocks.voidFence().getBlock();
        ModernShaftPieces.Corridor best = null;
        int bestScore = Integer.MIN_VALUE;
        int candidates = 0;
        int carts = 0;
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof ModernShaftPieces.Corridor corridor) || corridor.isSpiderCorridor() || corridor.numSections() < 3
                    || corridor.getOrientation() == null) {
                continue;
            }
            int posts = 0;
            for (int section = 0; section < corridor.numSections(); section++) {
                for (int x = 0; x <= 2; x += 2) {
                    for (int y = 0; y <= 1; y++) {
                        if (level.getBlockState(shaftPos(corridor, x, y, 2 + section * 5)).is(fence)) {
                            posts++;
                        }
                    }
                }
            }
            if (posts < corridor.numSections() * 4) {
                continue;
            }
            candidates++;
            BoundingBox box = corridor.getBoundingBox();
            boolean cart = !level.getEntitiesOfClass(MinecartChest.class, AABB.of(box).inflate(1)).isEmpty();
            if (cart) {
                carts++;
            }
            // 矿车 > 铁轨 > 长度 > 离地表近（maxY + 64：0~117）> 沿 X（镜头朝北看，南面受光 0.8，比东西面的 0.6 亮）
            int score = (cart ? 10000 : 0) + (corridor.hasRails() ? 1000 : 0) + Math.min(corridor.numSections(), 4) * 200 + box.maxY() + 64
                    + (corridor.getOrientation().getAxis() == Direction.Axis.X ? 5 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = corridor;
            }
        }
        EssentialCraft5.LOGGER.info("[showroom] 海纳尔矿井可拍的走廊 {} 段（其中有矿车的 {} 段）", candidates, carts);
        return best;
    }

    /** 站在走廊起点那一格中间（眼睛离地板 1.65 格），看向另一头 */
    private static Vec3[] tunnelView(ModernShaftPieces.Corridor corridor) {
        int last = corridor.numSections() * 5 - 1;
        Vec3 eye = Vec3.atCenterOf(shaftPos(corridor, 1, 1, 0)).add(0, 0.15, 0);
        Vec3 target = Vec3.atCenterOf(shaftPos(corridor, 1, 1, last)).add(0, -0.4, 0);
        return new Vec3[] {eye, target};
    }

    /** 镜头脚边（第一组立柱前一格、靠墙那一列）插一根火把 */
    private static void placeTunnelTorch(ServerLevel level) {
        ModernShaftPieces.Corridor corridor = shaftCorridor;
        if (corridor == null) {
            return;
        }
        for (int x = 0; x <= 2; x += 2) {
            BlockPos pos = shaftPos(corridor, x, 0, 1);
            BlockPos below = pos.below();
            if (level.getBlockState(pos).isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                level.setBlockAndUpdate(pos, Blocks.TORCH.defaultBlockState());
                EssentialCraft5.LOGGER.info("[showroom] 海纳尔矿井插了火把 {}", pos);
                return;
            }
        }
        EssentialCraft5.LOGGER.warn("[showroom] 海纳尔矿井走廊起点插不了火把（{}）", corridor.getBoundingBox());
    }

    /**
     * 剖面沟：沿 X 的走廊挖 +Z 一侧、沿 Z 的挖 +X 一侧；沿走向比走廊两头各多 3 格，横向从侧壁外一格起 {@value #CUT_DEPTH} 格，
     * 竖直从走廊地板下 2 格起（maxY 只给等区块用，实际一直挖到地表）
     */
    private static BoundingBox cutBox(ModernShaftPieces.Corridor corridor) {
        BoundingBox b = corridor.getBoundingBox();
        int y0 = b.minY() - 2;
        if (Objects.requireNonNull(corridor.getOrientation()).getAxis() == Direction.Axis.X) {
            return new BoundingBox(b.minX() - 3, y0, b.maxZ() + 1, b.maxX() + 3, b.maxY(), b.maxZ() + CUT_DEPTH);
        }
        return new BoundingBox(b.maxX() + 1, y0, b.minZ() - 3, b.maxX() + CUT_DEPTH, b.maxY(), b.maxZ() + 3);
    }

    /** 站在沟的最外一格（离走廊中线约 12 格）、比走廊地板高 3 格，稍微往下看走廊中间那一列 */
    private static Vec3[] cutView(ModernShaftPieces.Corridor corridor) {
        BoundingBox b = corridor.getBoundingBox();
        double y = b.minY();
        if (Objects.requireNonNull(corridor.getOrientation()).getAxis() == Direction.Axis.X) {
            double x = (b.minX() + b.maxX() + 1) / 2.0;
            return new Vec3[] {new Vec3(x, y + 3, b.maxZ() + CUT_DEPTH + 0.5), new Vec3(x, y + 0.8, b.minZ() + 1.5)};
        }
        double z = (b.minZ() + b.maxZ() + 1) / 2.0;
        return new Vec3[] {new Vec3(b.maxX() + CUT_DEPTH + 0.5, y + 3, z), new Vec3(b.minX() + 1.5, y + 0.8, z)};
    }

    /** 挖沟（只挖一次）：每列从沟底挖到这一列的地表（WORLD_SURFACE），写空气不连带更新邻居（沙子不落、水不流），再把走廊里的栅栏按邻居重算 */
    private static void carveCut(ServerLevel level) {
        ModernShaftPieces.Corridor corridor = shaftCorridor;
        if (corridor == null || shaftCut) {
            return;
        }
        shaftCut = true;
        BoundingBox cut = cutBox(corridor);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int carved = 0;
        int highest = cut.minY();
        for (int x = cut.minX(); x <= cut.maxX(); x++) {
            for (int z = cut.minZ(); z <= cut.maxZ(); z++) {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                highest = Math.max(highest, top);
                for (int y = cut.minY(); y < top; y++) {
                    if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                        carved++;
                    }
                }
            }
        }
        // 沟壁外一圈（四周 + 沟底下一层，一直到挖到的最高处）里的水 / 岩浆换成石头：海纳尔地下有含水层，
        // 不封的话沟挖开以后水灌进来，镜头泡在水里、整张是水下雾（6a 复验：存档里沟内 1 059 格水）
        BlockState seal = Blocks.STONE.defaultBlockState();
        int sealed = 0;
        for (int x = cut.minX() - 1; x <= cut.maxX() + 1; x++) {
            for (int z = cut.minZ() - 1; z <= cut.maxZ() + 1; z++) {
                boolean inside = x >= cut.minX() && x <= cut.maxX() && z >= cut.minZ() && z <= cut.maxZ();
                for (int y = cut.minY() - 1; y <= highest; y++) {
                    if (inside && y >= cut.minY()) {
                        continue;
                    }
                    if (!level.getFluidState(pos.set(x, y, z)).isEmpty()) {
                        level.setBlock(pos, seal, Block.UPDATE_CLIENTS);
                        sealed++;
                    }
                }
            }
        }
        BoundingBox b = corridor.getBoundingBox();
        int reshaped = 0;
        for (BlockPos p : BlockPos.betweenClosed(b.minX() - 1, b.minY() - 1, b.minZ() - 1, b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1)) {
            if (HoannaStructurePiece.needsShapeUpdate(level.getBlockState(p))) {
                HoannaStructurePiece.requestShapeUpdate(level, p);
                reshaped++;
            }
        }
        EssentialCraft5.LOGGER.info("[showroom] 海纳尔矿井挖沟 {}（挖到 y {}）：挖掉 {} 格，封住沟壁的水 {} 格，重算形状 {} 格", cut, highest, carved, sealed, reshaped);
    }

    /** 走廊局部坐标 → 世界坐标（同 StructurePiece.getWorldX / getWorldZ：局部 z 沿走向；和 HoannaUndergroundTests.worldPos 一样） */
    private static BlockPos shaftPos(StructurePiece piece, int x, int y, int z) {
        BoundingBox box = piece.getBoundingBox();
        Direction orientation = Objects.requireNonNull(piece.getOrientation());
        int wx = switch (orientation) {
            case WEST -> box.maxX() - z;
            case EAST -> box.minX() + z;
            default -> box.minX() + x;
        };
        int wz = switch (orientation) {
            case NORTH -> box.maxZ() - z;
            case SOUTH -> box.minZ() + z;
            default -> box.minZ() + x;
        };
        return new BlockPos(wx, box.minY() + y, wz);
    }
}
