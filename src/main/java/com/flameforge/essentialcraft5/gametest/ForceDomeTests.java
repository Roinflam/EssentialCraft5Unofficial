package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity.Status;
import com.flameforge.essentialcraft5.registry.ECForceDome;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.ForceDomeIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 力场穹顶（第十批 10c-M4，batch10.md §5.13）的 GameTest，17 条：
 * <ul>
 *   <li>范围内自然刷怪被拒（自然 / 生成区块 / 增援，整根柱子上下都算），范围外照常，不是怪物的照常；工作时每秒付满负荷的钱。</li>
 *   <li>刷怪笼、刷怪蛋、指令不受影响；首领（{@code #forge:bosses}）不受影响；{@code spawn_types} 只认自然 / 生成区块 / 巡逻 / 增援四种。</li>
 *   <li>MRU 不够不拦、一分不扣，补上就拦；连一块区块一秒都付不起时不探测。</li>
 *   <li>领地拒绝的区块不拦：以主人身份探测、被拒的区块全不拦时状态「没有你能罩的区块」、不扣钱；结论记在穹顶里，领地放开以后也不马上重问；
 *       被拒的结论记 6 倍长、有效期内一律不重问，全被拒时没电也不占名额。</li>
 *   <li>一部分区块被拒时按比例收费、只登记可拦的那几块。</li>
 *   <li>主人离线时用没过期的结论接着拦、不探测；过期了就不拦。主人在线时每次评估最多新问 4 块、先问自己所在的区块，能动的结论用到九成就重问。</li>
 *   <li>同主人第 5 台不工作（排队），拆掉更早的一台、或者给它通上红石就接上；别的主人不受影响。</li>
 *   <li>拆掉以后索引撤掉、名额表里也没有它；MRU / 红石来回跳时只停付费、索引快照不重建。</li>
 *   <li>幻翼（自然刷出、走 FinalizeSpawn）和巡逻队被取消；袭击不管。</li>
 *   <li>世界不动：工作 100 刻，没有放置事件、破坏事件全是领地探测，四周方块和箱子原样，没有掉落物；没有主人的不工作、不发事件。</li>
 * </ul>
 * 拦截走真的 Forge 事件（{@code ForgeEventFactory.checkSpawnPlacements} / {@code onFinalizeSpawn}，穹顶的监听器在里面），
 * 状态用 {@link ForceDomeBlockEntity#evaluateNow} 当场判。半径用实例钩子缩到 {@value #RADIUS}（测试结构隔 10 格左右一个，不互相罩住），
 * 主人每个测试一个新 UUID（名额表全服共用），都在本模块自己的 batch 里跑（用模拟领地拒格子的、半径放大的各自单独一个），测完把穹顶拆掉。
 * 主人在线用 {@code ECProtection.setOnlineOverride}，succeed 之前清掉。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ForceDomeTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "force_dome";
    /**
     * 用模拟领地的两条各自单独一个 batch：探测的是区块中心，相邻的测试结构可能落在同一块区块里，模拟领地按格子拒、不分是谁，
     * 和别的测试同时跑会把它们的探测也拒掉、也数进来
     */
    private static final String BATCH_CLAIMS = "force_dome_claims";
    private static final String BATCH_OFFLINE = "force_dome_offline";
    private static final String BATCH_DENIED = "force_dome_denied";
    /** 半径放大到 16 / 20 的两条各自单独一个 batch：会罩到相邻的测试结构 */
    private static final String BATCH_PARTIAL = "force_dome_partial";
    private static final String BATCH_PROBE_CAP = "force_dome_probe_cap";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final int RADIUS = 2;

    private ForceDomeTests() {
    }

    // ---------------- 工具 ----------------

    /** 放一台穹顶：主人（null = 没有主人）、放下时间、半径 {@value #RADIUS}、MRU 加满 */
    private static ForceDomeBlockEntity place(GameTestHelper helper, BlockPos rel, @Nullable UUID owner, long placedTick) {
        helper.setBlock(rel, ECForceDome.FORCE_DOME.get());
        ForceDomeBlockEntity be = (ForceDomeBlockEntity) helper.getBlockEntity(rel);
        if (owner != null) {
            be.setOwner(owner, "dome-test");
        }
        be.markPlaced(placedTick);
        be.setRadiusForTest(RADIUS);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        return be;
    }

    /** 主人当成在线（测完 {@code ECProtection.clearOnlineOverride}） */
    private static ServerPlayer online(GameTestHelper helper, UUID id) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, id, "dome-owner");
        ECProtection.setOnlineOverride(player);
        return player;
    }

    private static void remove(GameTestHelper helper, BlockPos... rels) {
        for (BlockPos rel : rels) {
            helper.setBlock(rel, Blocks.AIR);
        }
    }

    /** 这一格（绝对坐标）上这种生物按这种刷怪类型的放置检查过不过（原版结果当成「过」，只看事件有没有拒） */
    private static boolean placementAllowed(GameTestHelper helper, EntityType<?> type, MobSpawnType spawnType, BlockPos abs) {
        ServerLevel level = helper.getLevel();
        return ForgeEventFactory.checkSpawnPlacements(type, level, spawnType, abs, level.getRandom(), true);
    }

    /** 新建一只、站到这一格（绝对坐标）、走一遍 FinalizeSpawn：生成有没有被取消（实体不进世界） */
    private static boolean finalizeCancelled(GameTestHelper helper, EntityType<? extends Mob> type, MobSpawnType spawnType, BlockPos abs) {
        ServerLevel level = helper.getLevel();
        Mob mob = type.create(level);
        if (mob == null) {
            throw new IllegalStateException("建不出 " + type);
        }
        mob.moveTo(abs.getX() + 0.5D, abs.getY(), abs.getZ() + 0.5D, 0F, 0F);
        ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(abs), spawnType, null, null);
        boolean cancelled = mob.isSpawnCancelled();
        mob.discard();
        return cancelled;
    }

    private static boolean covered(GameTestHelper helper, BlockPos abs) {
        ServerLevel level = helper.getLevel();
        return ForceDomeIndex.covers(level.dimension(), level.getGameTime(), abs.getX() + 0.5D, abs.getZ() + 0.5D);
    }

    private static int mru(ForceDomeBlockEntity be) {
        return be.getMRUStorage().getMRU();
    }

    /** 穹顶正上方一格（一定在它自己所在的区块里、半径以内） */
    private static BlockPos inside(GameTestHelper helper) {
        return helper.absolutePos(CENTER).above();
    }

    /** 离穹顶 6 格（半径外；离相邻测试结构里的穹顶也在半径外） */
    private static BlockPos outside(GameTestHelper helper) {
        return helper.absolutePos(CENTER).offset(6, 1, 0);
    }

    /** 每块覆盖区块的探测格（绝对坐标，和 coveredChunkKeys 同序） */
    private static List<BlockPos> probeCells(ForceDomeBlockEntity be) {
        List<BlockPos> cells = new ArrayList<>();
        for (long chunk : be.coveredChunkKeys()) {
            cells.add(be.probePos(chunk));
        }
        return cells;
    }

    /** 这些格子合计收到几次这名主人的破坏事件（探测） */
    private static int probesBy(ProtectionFrameworkTests.Claims claims, List<BlockPos> cells, UUID owner) {
        int total = 0;
        for (BlockPos cell : cells) {
            total += claims.breaksByAt(cell, owner);
        }
        return total;
    }

    /** 这块区块里离穹顶最近的一格（穹顶上一层，绝对坐标）：区块算进覆盖就一定在半径以内 */
    private static BlockPos nearestIn(ForceDomeBlockEntity be, long chunk) {
        BlockPos dome = be.getBlockPos();
        int minX = ChunkPos.getX(chunk) * 16;
        int minZ = ChunkPos.getZ(chunk) * 16;
        int x = Math.max(minX, Math.min(minX + 15, dome.getX()));
        int z = Math.max(minZ, Math.min(minZ + 15, dome.getZ()));
        return new BlockPos(x, dome.getY() + 1, z);
    }

    // ---------------- 拦与不拦 ----------------

    /** 范围内自然 / 生成区块 / 增援刷怪被拒、整根柱子上下都算；范围外照常；牛照常；工作时付满负荷一秒的钱，索引登记了全部可拦区块 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeBlocksNaturalMonsters(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        online(helper, id);
        try {
            int before = mru(be);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING, "主人在线、MRU 满、没有领地，应在工作，实际 " + be.status());
            helper.assertTrue(be.blockableChunks() == be.coveredChunks() && be.coveredChunks() >= 1 && be.unconfirmedChunks() == 0,
                    "半径 2 碰到的区块（最多 4 块）一次就该问完、全部可拦：" + be.blockableChunks() + " / " + be.coveredChunks());
            helper.assertTrue(be.publishedChunks() == be.blockableChunks(), "可拦的区块都应登记进索引，实际 " + be.publishedChunks());
            int paid = before - mru(be);
            helper.assertTrue(paid == be.fullCostPerSecond() && paid == 20 * be.mruPerTick(), "全部可拦时付满负荷一秒 " + be.fullCostPerSecond() + "，实际 " + paid);
            BlockPos in = inside(helper);
            helper.assertFalse(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.NATURAL, in), "范围内自然刷出的僵尸应被拒");
            helper.assertFalse(placementAllowed(helper, EntityType.SKELETON, MobSpawnType.CHUNK_GENERATION, in), "范围内生成区块时的骷髅应被拒");
            helper.assertFalse(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.REINFORCEMENT, in), "范围内僵尸叫来的增援应被拒");
            helper.assertFalse(placementAllowed(helper, EntityType.CREEPER, MobSpawnType.NATURAL, in.above(80)), "整根柱子：高处也拦");
            helper.assertFalse(placementAllowed(helper, EntityType.CREEPER, MobSpawnType.NATURAL, in.below(40)), "整根柱子：低处也拦");
            helper.assertTrue(placementAllowed(helper, EntityType.COW, MobSpawnType.NATURAL, in), "牛不是怪物，照常");
            helper.assertTrue(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.NATURAL, outside(helper)), "范围外照常刷");
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    /** 刷怪笼、刷怪蛋、指令不受影响：放置检查放行、FinalizeSpawn 不取消，刷怪蛋生成的僵尸真的进了世界 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeSpawnersAndEggsUnaffected(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        online(helper, id);
        try {
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING, "应在工作，实际 " + be.status());
            BlockPos in = inside(helper);
            helper.assertTrue(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.SPAWNER, in), "刷怪笼的放置检查不拦");
            helper.assertFalse(finalizeCancelled(helper, EntityType.ZOMBIE, MobSpawnType.SPAWNER, in), "刷怪笼的生成不取消");
            helper.assertFalse(finalizeCancelled(helper, EntityType.ZOMBIE, MobSpawnType.SPAWN_EGG, in), "刷怪蛋的生成不取消");
            helper.assertFalse(finalizeCancelled(helper, EntityType.ZOMBIE, MobSpawnType.COMMAND, in), "指令召唤不取消");
            Entity egg = EntityType.ZOMBIE.spawn(helper.getLevel(), in, MobSpawnType.SPAWN_EGG);
            helper.assertTrue(egg != null && egg.isAddedToWorld() && !egg.isRemoved(), "刷怪蛋应照样生成僵尸");
            egg.discard();
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    /** 首领（#forge:bosses）不受影响：凋灵、末影龙的自然放置检查放行、FinalizeSpawn 不取消（逻辑上测，原版不会自然刷它们） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeIgnoresBosses(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        online(helper, id);
        try {
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING, "应在工作，实际 " + be.status());
            BlockPos in = inside(helper);
            helper.assertTrue(EntityType.WITHER.is(net.minecraftforge.common.Tags.EntityTypes.BOSSES), "凋灵应在 #forge:bosses 里");
            helper.assertTrue(placementAllowed(helper, EntityType.WITHER, MobSpawnType.NATURAL, in), "凋灵是首领，不拦");
            helper.assertTrue(placementAllowed(helper, EntityType.ENDER_DRAGON, MobSpawnType.NATURAL, in), "末影龙是首领，不拦");
            helper.assertFalse(finalizeCancelled(helper, EntityType.WITHER, MobSpawnType.NATURAL, in), "凋灵的 FinalizeSpawn 不取消");
            helper.assertFalse(placementAllowed(helper, EntityType.WITHER_SKELETON, MobSpawnType.NATURAL, in), "对照：凋灵骷髅不是首领，照拦");
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    /**
     * spawn_types 只认自然 / 生成区块 / 巡逻 / 增援：刷怪笼、袭击、结构这些写进配置也不认（校验不过、解析时丢掉）。
     * 袭击拦了会卡住袭击（灾厄村民先进波次名单、再被取消生成），刷怪笼、刷怪蛋是手册和配置注释承诺不拦的
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeSpawnTypesLimited(GameTestHelper helper) {
        helper.assertTrue(ForceDomeBlockEntity.Config.parse(List.of("SPAWNER", "EVENT", "NATURAL")).equals(EnumSet.of(MobSpawnType.NATURAL)),
                "名单里只有 NATURAL 该留下，实际 " + ForceDomeBlockEntity.Config.parse(List.of("SPAWNER", "EVENT", "NATURAL")));
        helper.assertTrue(ForceDomeBlockEntity.Config.parse(List.of("STRUCTURE", "SPAWN_EGG", "COMMAND", "BREEDING", "bogus")).isEmpty(),
                "四种以外的全丢掉");
        helper.assertTrue(ForceDomeBlockEntity.Config.parse(ForceDomeBlockEntity.Config.DEFAULT_SPAWN_TYPES).equals(ForceDomeBlockEntity.Config.ALLOWED_SPAWN_TYPES),
                "默认值就是允许的四种");
        for (String bad : List.of("SPAWNER", "EVENT", "STRUCTURE", "SPAWN_EGG", "COMMAND", "bogus")) {
            helper.assertFalse(ForceDomeBlockEntity.Config.validEntry(bad), bad + " 不该通过配置校验");
        }
        helper.assertFalse(ForceDomeBlockEntity.Config.validEntry(5), "不是字符串的不该通过配置校验");
        for (String good : List.of("NATURAL", " chunk_generation ", "PATROL", "reinforcement")) {
            helper.assertTrue(ForceDomeBlockEntity.Config.validEntry(good), good + " 应通过配置校验（大小写、首尾空格不计）");
        }
        helper.assertFalse(ForceDomeIndex.handles(EntityType.PILLAGER, MobSpawnType.EVENT), "袭击的灾厄村民不归穹顶管");
        helper.assertTrue(ForceDomeIndex.handles(EntityType.PILLAGER, MobSpawnType.PATROL), "对照：巡逻队的灾厄村民归穹顶管");
        helper.succeed();
    }

    /** MRU 不够：状态「MRU 不足」、不拦、一分不扣；补上以后下一次判定就拦 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeNeedsMru(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        online(helper, id);
        try {
            int need = be.fullCostPerSecond();
            be.getMRUStorage().setMRU(need - 1);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.NO_MRU, "差 1 MRU 付不起这一秒，应是 MRU 不足，实际 " + be.status());
            helper.assertTrue(mru(be) == need - 1 && be.payments() == 0, "付不起时一分不扣，剩 " + mru(be));
            helper.assertTrue(be.publishedChunks() == 0 && !covered(helper, inside(helper)), "缺电时索引里不该有它");
            helper.assertTrue(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.NATURAL, inside(helper)), "缺电时照常刷");
            be.getMRUStorage().setMRU(need);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING && mru(be) == 0, "补上正好一秒的钱：工作、扣光，实际 " + be.status() + " 剩 " + mru(be));
            helper.assertFalse(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.NATURAL, inside(helper)), "补上以后就拦");
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    /**
     * 连一块区块一秒的钱（⌈满负荷 ÷ 覆盖区块数⌉）都没有时不探测：主人在线、MRU 为 0 判一次，一次探测都没有、状态「MRU 不足」、全是未确认；
     * 差 1 MRU 照样不探测；补满以后才问、才工作（同饰品封禁场「付不起不探测」）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeNoMruNoProbe(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        be.getMRUStorage().setMRU(0);
        // 只用来按主人数探测次数，不拒任何格子（和同 batch 的测试互不影响）
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            online(helper, id);
            be.evaluateNow();
            List<BlockPos> cells = probeCells(be);
            helper.assertTrue(be.status() == Status.NO_MRU, "MRU 为 0、还没问过领地：应是 MRU 不足，实际 " + be.status());
            helper.assertTrue(probesBy(claims, cells, id) == 0, "MRU 为 0 时不该探测，实际 " + probesBy(claims, cells, id) + " 次");
            helper.assertTrue(be.unconfirmedChunks() == be.coveredChunks() && be.payments() == 0, "没问过：全是未确认、不付钱");
            int one = be.oneChunkCostPerSecond();
            int n = be.coveredChunks();
            helper.assertTrue(one == (be.fullCostPerSecond() + n - 1) / n && one > 1, "拦一块区块一秒 = ⌈满负荷 ÷ " + n + "⌉，实际 " + one);
            be.getMRUStorage().setMRU(one - 1);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.NO_MRU && probesBy(claims, cells, id) == 0, "差 1 MRU 照样不探测，实际 " + be.status() + " 探测 " + probesBy(claims, cells, id));
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING, "补满以后应问领地、开始工作，实际 " + be.status());
            for (BlockPos cell : cells) {
                helper.assertTrue(claims.breaksByAt(cell, id) == 1, cell.toShortString() + " 补满以后应正好探测一次，实际 " + claims.breaksByAt(cell, id));
            }
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    /**
     * MRU 在够 / 不够之间来回跳（进账比花费慢）、接着红石时钟：不工作的那一秒只停付费（当刻就不拦、publishedChunks 为 0），
     * 索引快照只在第一次工作时重建一次，之后来回 6 次加一轮红石都不再重建
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeFlappingKeepsIndex(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        BlockPos lever = CENTER.east();
        online(helper, id);
        try {
            int before = ForceDomeIndex.rebuildsForTest();
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING, "应在工作，实际 " + be.status());
            int afterFirst = ForceDomeIndex.rebuildsForTest();
            helper.assertTrue(afterFirst - before == 1, "第一次工作重建一次快照，实际 " + (afterFirst - before));
            int max = be.getMRUStorage().getMaxMRU();
            for (int i = 0; i < 6; i++) {
                be.getMRUStorage().setMRU(0);
                be.evaluateNow();
                helper.assertTrue(be.status() == Status.NO_MRU, "第 " + (i + 1) + " 次没电：应是 MRU 不足，实际 " + be.status());
                helper.assertTrue(be.publishedChunks() == 0 && !covered(helper, inside(helper)), "没电的那一秒当刻就不拦");
                helper.assertTrue(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.NATURAL, inside(helper)), "没电的那一秒照常刷");
                be.getMRUStorage().setMRU(max);
                be.evaluateNow();
                helper.assertTrue(be.status() == Status.WORKING && covered(helper, inside(helper)), "第 " + (i + 1) + " 次补上：接着拦，实际 " + be.status());
            }
            helper.setBlock(lever, Blocks.REDSTONE_BLOCK);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.POWERED && !covered(helper, inside(helper)) && be.publishedChunks() == 0, "红石暂停：当刻不拦，实际 " + be.status());
            helper.setBlock(lever, Blocks.AIR);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING && covered(helper, inside(helper)), "红石撤掉接着拦，实际 " + be.status());
            helper.assertTrue(ForceDomeIndex.rebuildsForTest() == afterFirst,
                    "来回跳不该再重建快照，实际多了 " + (ForceDomeIndex.rebuildsForTest() - afterFirst) + " 次");
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER, lever);
        }
        helper.succeed();
    }

    // ---------------- 领地 ----------------

    /**
     * 领地拒绝的区块不拦：先在主人离线时判一次（只算覆盖哪些区块、不探测），再把每块区块的探测格（区块中心、穹顶那一层）都设成别人的领地，
     * 主人上线判一次：每格正好一次以主人身份的探测、全被拒 → 「没有你能罩的区块」、不扣钱、不登记、照常刷。
     * 领地放开、清掉 ECProtection 自己的探测缓存以后再判：穹顶记着结论（1 小时），不重问、照旧不拦。
     */
    @GameTest(template = EMPTY, batch = BATCH_CLAIMS)
    public static void forceDomeClaimDeniedChunksNotBlocked(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.UNCONFIRMED && be.unconfirmedChunks() == be.coveredChunks(),
                    "主人不在线、从没问过：全是未确认，实际 " + be.status() + " " + be.unconfirmedChunks() + " / " + be.coveredChunks());
            long[] chunks = be.coveredChunkKeys();
            List<BlockPos> probes = new ArrayList<>();
            for (long chunk : chunks) {
                BlockPos at = be.probePos(chunk);
                probes.add(at);
                claims.denyAt(at);
                helper.assertTrue(claims.breaksByAt(at, id) == 0, "主人离线时不该探测 " + at.toShortString());
            }
            online(helper, id);
            int before = mru(be);
            be.evaluateNow();
            for (BlockPos at : probes) {
                helper.assertTrue(claims.breaksByAt(at, id) == 1,
                        at.toShortString() + " 应正好一次以主人身份的探测，实际 " + claims.breaksByAt(at, id));
            }
            helper.assertTrue(be.status() == Status.NO_CLAIM && be.deniedChunks() == chunks.length,
                    "覆盖的区块全被拒，应是没有你能罩的区块，实际 " + be.status() + " 拒 " + be.deniedChunks());
            helper.assertTrue(mru(be) == before && be.payments() == 0 && be.publishedChunks() == 0, "全被拒时不扣钱、不登记");
            helper.assertTrue(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.NATURAL, inside(helper)), "别人领地里的区块照常刷");
            for (BlockPos at : probes) {
                claims.allowAt(at);
            }
            ECProtection.forget(id);
            be.evaluateNow();
            for (BlockPos at : probes) {
                helper.assertTrue(claims.breaksByAt(at, id) == 1, "结论记在穹顶里，领地放开后也不马上重问 " + at.toShortString());
            }
            helper.assertTrue(be.status() == Status.NO_CLAIM && be.claimFor(chunks[0]) == Boolean.FALSE, "结论没过期，照旧不拦");
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    /**
     * 被拒的结论少问：全被拒以后主人一直在线，结论老化到 claim_cache 的 95%（能动的结论这时会重问）、再到超过 claim_cache（以前算过期、会重问），
     * 都不再探测，照旧「没有你能罩的区块」；全被拒时没电也是「没有你能罩的区块」（不占名额），不是「MRU 不足」；
     * 老化到 claim_cache × {@value ForceDomeBlockEntity#DENIED_CACHE_MULTIPLIER} 以后才重问一次
     */
    @GameTest(template = EMPTY, batch = BATCH_DENIED)
    public static void forceDomeDeniedChunksAskedRarely(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            be.evaluateNow();
            List<BlockPos> cells = probeCells(be);
            for (BlockPos cell : cells) {
                claims.denyAt(cell);
            }
            online(helper, id);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.NO_CLAIM && be.deniedChunks() == cells.size(), "全被拒：没有你能罩的区块，实际 " + be.status());
            for (BlockPos cell : cells) {
                helper.assertTrue(claims.breaksByAt(cell, id) == 1, cell.toShortString() + " 应探测一次，实际 " + claims.breaksByAt(cell, id));
            }
            int cache = be.claimCacheTicks();
            ECProtection.forget(id);
            be.ageClaimsForTest(cache - cache / 20);
            be.evaluateNow();
            helper.assertTrue(probesBy(claims, cells, id) == cells.size(), "被拒的结论用到 claim_cache 的 95% 也不重问，实际共探测 " + probesBy(claims, cells, id) + " 次");
            helper.assertTrue(be.status() == Status.NO_CLAIM && be.claimFor(be.coveredChunkKeys()[0]) == Boolean.FALSE, "照旧不可拦，实际 " + be.status());
            be.ageClaimsForTest(cache);
            be.evaluateNow();
            helper.assertTrue(probesBy(claims, cells, id) == cells.size(), "超过 claim_cache 以后被拒的结论仍有效、不重问，实际共探测 " + probesBy(claims, cells, id) + " 次");
            helper.assertTrue(be.status() == Status.NO_CLAIM && be.deniedChunks() == cells.size() && be.unconfirmedChunks() == 0,
                    "仍是全被拒，实际 " + be.status() + " 拒 " + be.deniedChunks() + " 未确认 " + be.unconfirmedChunks());
            be.getMRUStorage().setMRU(0);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.NO_CLAIM, "全被拒时没电也是没有你能罩的区块（不占名额），实际 " + be.status());
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            ECProtection.forget(id);
            be.ageClaimsForTest(be.deniedCacheTicks());
            be.evaluateNow();
            for (BlockPos cell : cells) {
                helper.assertTrue(claims.breaksByAt(cell, id) == 2, cell.toShortString() + " 被拒的结论过期以后应重问一次，实际共 " + claims.breaksByAt(cell, id) + " 次");
            }
            helper.assertTrue(be.status() == Status.NO_CLAIM, "领地没变，重问以后仍是没有你能罩的区块，实际 " + be.status());
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    /**
     * 一部分区块被拒：半径 16（至少 5 块区块），把离得远的一半区块的探测格设成别人的领地，主人在线问完（每次最多新问 4 块，判几次）；
     * 再判一次，付 ⌈满负荷 × 可拦 / 覆盖⌉，索引只登记可拦的那几块；可拦区块里离穹顶最近的一格不刷、被拒区块里照常刷
     */
    @GameTest(template = EMPTY, batch = BATCH_PARTIAL)
    public static void forceDomePartialClaimsPayShare(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        be.setRadiusForTest(16);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            be.evaluateNow();
            long[] chunks = be.coveredChunkKeys();
            int n = chunks.length;
            helper.assertTrue(n >= 4, "半径 16 至少碰到 4 块区块，实际 " + n);
            for (long chunk : chunks) {
                // 测试结构所在区块往外 1~3 块都被 GameTest 强制加载着；没加载的区块问不出结论，下面的计数就对不上
                helper.assertTrue(ECLevels.isFullyLoaded(helper.getLevel(), be.probePos(chunk)), "覆盖区块 " + new ChunkPos(chunk) + " 应已加载");
            }
            int denied = n / 2;
            int ok = n - denied;
            for (int i = ok; i < n; i++) {
                claims.denyAt(be.probePos(chunks[i]));
            }
            online(helper, id);
            for (int round = 0; round < 4 && (round == 0 || be.unconfirmedChunks() > 0); round++) {
                be.evaluateNow();
            }
            helper.assertTrue(be.unconfirmedChunks() == 0 && be.blockableChunks() == ok && be.deniedChunks() == denied,
                    "应问完：可拦 " + ok + " / 不可拦 " + denied + "，实际 " + be.blockableChunks() + " / " + be.deniedChunks() + "，未确认 " + be.unconfirmedChunks());
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            int before = mru(be);
            be.evaluateNow();
            int paid = before - mru(be);
            int expect = (int) (((long) be.fullCostPerSecond() * ok + n - 1) / n);
            helper.assertTrue(be.status() == Status.WORKING, "有可拦的区块，应在工作，实际 " + be.status());
            helper.assertTrue(paid == expect && be.costPerSecond() == expect && expect < be.fullCostPerSecond(),
                    "按比例付 ⌈" + be.fullCostPerSecond() + " × " + ok + " / " + n + "⌉ = " + expect + "，实际 " + paid);
            helper.assertTrue(be.publishedChunks() == ok, "索引只登记可拦的 " + ok + " 块，实际 " + be.publishedChunks());
            for (int i = 0; i < n; i++) {
                BlockPos near = nearestIn(be, chunks[i]);
                boolean allowed = placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.NATURAL, near);
                if (i < ok) {
                    helper.assertFalse(allowed, "可拦区块里 " + near.toShortString() + " 不该刷");
                } else {
                    helper.assertTrue(allowed, "被拒区块里 " + near.toShortString() + " 照常刷");
                }
            }
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    /**
     * 主人离线：接着用在线时问出的结论拦、不探测（用到九成也不重问）；全部过期以后「正在确认领地」、不拦、不扣钱、照样不探测。
     */
    @GameTest(template = EMPTY, batch = BATCH_OFFLINE)
    public static void forceDomeOfflineOwnerUsesCacheThenStops(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            online(helper, id);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING, "在线时应在工作，实际 " + be.status());
            BlockPos probe = be.probePos(be.coveredChunkKeys()[0]);
            helper.assertTrue(claims.breaksByAt(probe, id) == 1, "在线时探测一次");
            ECProtection.clearOnlineOverride(id);
            ECProtection.forget(id);
            int cache = be.claimCacheTicks();
            be.ageClaimsForTest(cache - cache / 20);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING, "离线、结论没过期：接着工作，实际 " + be.status());
            helper.assertFalse(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.NATURAL, inside(helper)), "离线时用没过期的结论接着拦");
            helper.assertTrue(claims.breaksByAt(probe, id) == 1, "离线时不探测（结论用到九成以上也不重问）");
            int before = mru(be);
            be.ageClaimsForTest(cache);
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.UNCONFIRMED && be.blockableChunks() == 0, "结论全过期：正在确认领地，实际 " + be.status());
            helper.assertTrue(mru(be) == before && be.publishedChunks() == 0, "过期以后不扣钱、不登记");
            helper.assertTrue(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.NATURAL, inside(helper)), "过期的区块不拦");
            helper.assertTrue(claims.breaksByAt(probe, id) == 1, "主人离线时过期了也不探测");
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    /**
     * 每次评估最多新问 {@value ForceDomeBlockEntity#MAX_NEW_PROBES} 块、近的先问：半径 20（多于 4 块区块），主人在线判一次，
     * 以主人身份的探测不超过 4 次、一定含自己所在区块的探测格（远处区块没加载时问不出结论，不计数）。
     * 主人一直在线：清掉 ECProtection 自己的探测缓存、结论老化到 91%，再判一次，问过的那几格各又探测一次（能动的结论用到九成就重问）
     */
    @GameTest(template = EMPTY, batch = BATCH_PROBE_CAP)
    public static void forceDomeProbeCapAndRefresh(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        be.setRadiusForTest(20);
        // 不拒任何格子，只按主人数探测次数
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            online(helper, id);
            be.evaluateNow();
            List<BlockPos> cells = probeCells(be);
            helper.assertTrue(cells.size() > ForceDomeBlockEntity.MAX_NEW_PROBES, "半径 20 应碰到 4 块以上的区块，实际 " + cells.size());
            int first = probesBy(claims, cells, id);
            helper.assertTrue(first >= 1 && first <= ForceDomeBlockEntity.MAX_NEW_PROBES, "一次评估最多新问 4 块，实际 " + first);
            helper.assertTrue(claims.breaksByAt(cells.get(0), id) == 1, "先问自己所在的区块");
            helper.assertTrue(be.status() == Status.WORKING, "问到的区块都能动，应在工作，实际 " + be.status());
            List<BlockPos> asked = new ArrayList<>();
            for (BlockPos cell : cells) {
                int count = claims.breaksByAt(cell, id);
                helper.assertTrue(count <= 1, cell.toShortString() + " 一次评估里最多问一次，实际 " + count);
                if (count == 1) {
                    asked.add(cell);
                }
            }
            ECProtection.forget(id);
            int cache = be.claimCacheTicks();
            be.ageClaimsForTest((long) cache * 91 / 100);
            be.evaluateNow();
            for (BlockPos cell : asked) {
                helper.assertTrue(claims.breaksByAt(cell, id) == 2, cell.toShortString() + " 结论用到 91%、主人在线，应重问一次，实际共 " + claims.breaksByAt(cell, id) + " 次");
            }
            int second = probesBy(claims, cells, id) - first;
            helper.assertTrue(second <= ForceDomeBlockEntity.MAX_NEW_PROBES, "重问也算进每次 4 块的上限，实际 " + second);
            helper.assertTrue(be.status() == Status.WORKING, "重问以后照旧工作，实际 " + be.status());
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    // ---------------- 台数、拆掉 ----------------

    /** 同主人只有最早放下的 4 台工作，第 5 台排队、不付钱；拆掉最早的一台，第 5 台接上；别的主人的穹顶不受影响 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeFifthDomeWaits(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        BlockPos[] spots = {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(1, 1, 3), new BlockPos(3, 1, 3), new BlockPos(2, 1, 2)};
        BlockPos strangerSpot = new BlockPos(2, 1, 1);
        ForceDomeBlockEntity[] domes = new ForceDomeBlockEntity[spots.length];
        online(helper, id);
        online(helper, stranger);
        try {
            for (int i = 0; i < spots.length; i++) {
                domes[i] = place(helper, spots[i], id, i);
                domes[i].setPerOwnerForTest(4);
            }
            ForceDomeBlockEntity other = place(helper, strangerSpot, stranger, 99L);
            other.setPerOwnerForTest(4);
            for (ForceDomeBlockEntity dome : domes) {
                dome.evaluateNow();
            }
            other.evaluateNow();
            for (int i = 0; i < 4; i++) {
                helper.assertTrue(domes[i].status() == Status.WORKING, "第 " + (i + 1) + " 台应在工作，实际 " + domes[i].status());
            }
            ForceDomeBlockEntity fifth = domes[4];
            helper.assertTrue(fifth.status() == Status.OWNER_LIMIT, "第 5 台应排队，实际 " + fifth.status());
            helper.assertTrue(fifth.payments() == 0 && fifth.publishedChunks() == 0, "排队的不付钱、不登记");
            helper.assertTrue(other.status() == Status.WORKING, "别的主人的穹顶不受影响，实际 " + other.status());
            remove(helper, spots[0]);
            fifth.evaluateNow();
            helper.assertTrue(fifth.status() == Status.WORKING, "拆掉最早的一台后第 5 台应接上，实际 " + fifth.status());
        } finally {
            ECProtection.clearOnlineOverride(id);
            ECProtection.clearOnlineOverride(stranger);
            remove(helper, spots);
            remove(helper, strangerSpot);
        }
        helper.succeed();
    }

    /**
     * 红石让位：同主人 4 台在工作、第 5 台排队；给最早的一台通上红石（红石块放在它正上方，只挨着它），判一次是「红石暂停」、不付钱、不拦，
     * 第 5 台再判一次就接上（暂停的不占名额）；红石撤掉，最早那台判一次拿回名额，第 5 台再判一次回到排队
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeRedstoneYieldsSlot(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        BlockPos[] spots = {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(1, 1, 3), new BlockPos(3, 1, 3), new BlockPos(2, 1, 2)};
        BlockPos redstone = spots[0].above();
        ForceDomeBlockEntity[] domes = new ForceDomeBlockEntity[spots.length];
        online(helper, id);
        try {
            for (int i = 0; i < spots.length; i++) {
                domes[i] = place(helper, spots[i], id, i);
                domes[i].setPerOwnerForTest(4);
            }
            for (ForceDomeBlockEntity dome : domes) {
                dome.evaluateNow();
            }
            ForceDomeBlockEntity earliest = domes[0];
            ForceDomeBlockEntity fifth = domes[4];
            helper.assertTrue(earliest.status() == Status.WORKING && fifth.status() == Status.OWNER_LIMIT,
                    "开始时最早那台工作、第 5 台排队，实际 " + earliest.status() + " / " + fifth.status());
            helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
            int paid = earliest.payments();
            earliest.evaluateNow();
            helper.assertTrue(earliest.status() == Status.POWERED, "通上红石应是红石暂停，实际 " + earliest.status());
            helper.assertTrue(earliest.payments() == paid && earliest.publishedChunks() == 0, "暂停时不付钱、不拦");
            fifth.evaluateNow();
            helper.assertTrue(fifth.status() == Status.WORKING, "暂停的不占名额，第 5 台应接上，实际 " + fifth.status());
            domes[1].evaluateNow();
            helper.assertTrue(domes[1].status() == Status.WORKING, "第 2 台没挨着红石块，照旧工作，实际 " + domes[1].status());
            helper.setBlock(redstone, Blocks.AIR);
            earliest.evaluateNow();
            helper.assertTrue(earliest.status() == Status.WORKING, "红石撤掉，最早那台拿回名额，实际 " + earliest.status());
            fifth.evaluateNow();
            helper.assertTrue(fifth.status() == Status.OWNER_LIMIT, "最早那台回来以后第 5 台回到排队，实际 " + fifth.status());
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, spots);
            remove(helper, redstone);
        }
        helper.succeed();
    }

    /** 拆掉：索引里撤掉、名额表里没有它，原来罩住的地方照常刷 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeRemovalWithdraws(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        online(helper, id);
        try {
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING && be.publishedChunks() >= 1 && be.isListed(), "拆之前在工作、登记着、在名额表里");
            helper.assertTrue(covered(helper, inside(helper)), "拆之前罩着正上方");
            remove(helper, CENTER);
            helper.assertTrue(be.publishedChunks() == 0 && !be.isListed(), "拆掉以后索引和名额表里都不该有它");
            helper.assertFalse(covered(helper, inside(helper)), "拆掉以后不再罩着");
            helper.assertTrue(placementAllowed(helper, EntityType.ZOMBIE, MobSpawnType.NATURAL, inside(helper)), "拆掉以后照常刷");
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    /** 幻翼（自然刷出，不经过放置检查）和巡逻队走 FinalizeSpawn 被取消；范围外不取消；袭击（EVENT）不管 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void forceDomeCancelsPhantomsAndPatrols(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        online(helper, id);
        try {
            be.evaluateNow();
            helper.assertTrue(be.status() == Status.WORKING, "应在工作，实际 " + be.status());
            BlockPos in = inside(helper).above(20);
            helper.assertTrue(finalizeCancelled(helper, EntityType.PHANTOM, MobSpawnType.NATURAL, in), "范围内的幻翼应被取消");
            helper.assertFalse(finalizeCancelled(helper, EntityType.PHANTOM, MobSpawnType.NATURAL, outside(helper).above(20)), "范围外的幻翼照常");
            helper.assertTrue(finalizeCancelled(helper, EntityType.PILLAGER, MobSpawnType.PATROL, inside(helper)), "范围内的巡逻队应被取消");
            helper.assertFalse(finalizeCancelled(helper, EntityType.PILLAGER, MobSpawnType.EVENT, inside(helper)), "袭击不管");
        } finally {
            ECProtection.clearOnlineOverride(id);
            remove(helper, CENTER);
        }
        helper.succeed();
    }

    // ---------------- 世界不动 ----------------

    /**
     * 四周铺石头、泥土、带东西的箱子，另放一台没有主人的；工作 100 刻：没有任何放置事件，破坏事件全是领地探测（没有主人的那台一个都不发），
     * 每格方块不变、箱子里的东西不少，没有掉落物；有主人的那台每秒付一次钱，没有主人的不工作。
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void forceDomeLeavesWorldAlone(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ForceDomeBlockEntity be = place(helper, CENTER, id, 0L);
        BlockPos orphanSpot = new BlockPos(4, 1, 4);
        ForceDomeBlockEntity orphan = place(helper, orphanSpot, null, 0L);
        Map<BlockPos, BlockState> around = new HashMap<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos rel = CENTER.relative(direction);
            BlockState state = direction.getAxis() == Direction.Axis.X ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            helper.setBlock(rel, state);
            around.put(rel, state);
        }
        BlockPos chestSpot = CENTER.above();
        helper.setBlock(chestSpot, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestSpot);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        online(helper, id);
        ServerLevel level = helper.getLevel();
        int[] places = new int[1];
        int[] realBreaks = new int[1];
        int[] probesTotal = new int[1];
        Consumer<BlockEvent.EntityPlaceEvent> onPlace = event -> {
            if (event.getLevel() == level) {
                places[0]++;
            }
        };
        // 同一 batch 里别的测试的穹顶也在探测（各用各的主人），探测次数只数这名主人的
        Consumer<BlockEvent.BreakEvent> onBreak = event -> {
            if (event.getLevel() == level) {
                if (!ECProtection.isProbing()) {
                    realBreaks[0]++;
                } else if (event.getPlayer() != null && id.equals(event.getPlayer().getUUID())) {
                    probesTotal[0]++;
                }
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, BlockEvent.EntityPlaceEvent.class, onPlace);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, BlockEvent.BreakEvent.class, onBreak);
        helper.runAfterDelay(100, () -> {
            try {
                helper.assertTrue(be.status() == Status.WORKING && be.payments() >= 4, "有主人的那台应在工作、每秒付一次，实际 " + be.status() + " 付 " + be.payments() + " 次");
                helper.assertTrue(orphan.status() == Status.NO_OWNER && orphan.payments() == 0 && orphan.publishedChunks() == 0, "没有主人的不工作，实际 " + orphan.status());
                helper.assertTrue(places[0] == 0, "不该有任何放置事件，实际 " + places[0]);
                helper.assertTrue(realBreaks[0] == 0, "破坏事件只能是领地探测，实际多出 " + realBreaks[0] + " 次");
                helper.assertTrue(probesTotal[0] <= be.coveredChunks() * 2, "探测次数应很少（每块区块一次），实际 " + probesTotal[0]);
                for (Map.Entry<BlockPos, BlockState> entry : around.entrySet()) {
                    helper.assertTrue(helper.getBlockState(entry.getKey()) == entry.getValue(), entry.getKey().toShortString() + " 被动过");
                }
                helper.assertTrue(helper.getBlockState(chestSpot).is(Blocks.CHEST) && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 5,
                        "箱子和里面的东西应原样");
                helper.assertTrue(helper.getBlockState(CENTER).is(ECForceDome.FORCE_DOME.get()) && helper.getBlockState(orphanSpot).is(ECForceDome.FORCE_DOME.get()),
                        "穹顶自己也还在");
                AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6D);
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, box).isEmpty(), "不该有掉落物");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(onPlace);
                MinecraftForge.EVENT_BUS.unregister(onBreak);
                ECProtection.clearOnlineOverride(id);
                remove(helper, CENTER, orphanSpot);
            }
            helper.succeed();
        });
    }
}
