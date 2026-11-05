package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.event.ProtectionProbeEvent;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 三批框架 G0「权限工具与主人」的 GameTest（{@code util/ECProtection}、{@code MachineBlock.setPlacedBy} 记主人）：
 * canEdit 被模拟领地拒绝、探测缓存与每 tick 预算；placeGuarded 被拒还原、嵌套在外层捕获里、不许换掉容器；breakGuarded；
 * safeExplode 不改方块、不伤展示框 / 盔甲架 / 掉落物 / 经验球、照伤生物且记攻击者，别的爆炸不受影响；主人存读档、不同步。
 * 四批框架 F4-1 ~ F4-3：在线主人覆盖、每主人探测预算；先删后掉、harvestGuarded；
 * captureGuarded（被拒全部还原、嵌套、不许的实体、异常、方块实体换回使用前、被打断）。
 *
 * <h2>给各模块测试用的两个工具</h2>
 * <ul>
 *   <li>{@link #realPlayer(GameTestHelper)}：真 {@link ServerPlayer}（不是 FakePlayer，权限工具认它）。
 *       policy §2.5 写的 {@code makeMockServerPlayerInLevel} 在 Forge 1.20.1 会崩（placeNewPlayer 要网络通道，见 BootstrapTests 的类注释），别用。</li>
 *   <li>{@link Claims}：模拟领地插件，按坐标拒绝破坏 / 放置事件（含探测），并数每格收到几次事件（{@code breaksBy / placesBy} 按玩家分开数）；
 *       {@code denyPlace} 是「只拒放置、放行破坏」的格子；第六批 6a 起能指定世界、按绝对坐标拒一整块（{@code open(helper, level).denyBox(box)}）。</li>
 *   <li>主人「在线」：{@code ECProtection.setOnlineOverride(realPlayer(helper, id, name))}，测完 {@code clearOnlineOverride(id)}；
 *       全局开关 {@code ECProtection.Config.setRequireOwnerOnlineForTest} 只许在同一段同步代码里设、测、置回 null。</li>
 * </ul>
 * <pre>{@code
 * ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
 * try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(new BlockPos(3, 1, 2))) {
 *     ...调被测的物品 / 机器...
 *     helper.assertTrue(claims.breaks(new BlockPos(3, 1, 2)) == 1, "被拒的格子应探测一次");
 * }
 * helper.succeed();
 * }</pre>
 * 跨 tick 的测试（runAtTickTime / succeedWhen）在最后一步手动 {@link Claims#close()}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionFrameworkTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";

    private ProtectionFrameworkTests() {
    }

    // ------------------------------------------------------------------
    // 测试工具（各模块的测试可以直接用）
    // ------------------------------------------------------------------

    /**
     * 测试用的「真玩家」：真 {@link ServerPlayer}（不是 FakePlayer，混合端会转发它的事件、ECProtection 认它），站在测试区域的 (0, 1, 0)。
     * 不进玩家列表、不进世界，所以 {@code ECProtection.onlineOwner} 找不到它（= 主人不在线）；
     * 要测「主人在线」：直接传给带 ServerPlayer 参数的方法（{@code check}、{@code placeGuarded}），
     * 或者 {@code ECProtection.setOnlineOverride(player)} 把它当成在线（机器的 {@code onlineOwner / checkAsOwner} 都认），
     * 用自己的 UUID（{@link #realPlayer(GameTestHelper, UUID, String)}），{@code succeed} 之前 {@code clearOnlineOverride(id)}。
     * 连接是假的：发给它的包只进队列，displayClientMessage 之类可以照常调。游戏模式是生存。每次调用是随机的新 UUID。
     */
    public static ServerPlayer realPlayer(GameTestHelper helper) {
        return realPlayer(helper, UUID.randomUUID(), "ec5-test");
    }

    /**
     * 同 {@link #realPlayer(GameTestHelper)}，身份由调用方给（在线覆盖、按玩家分开计数 {@link Claims#placesBy} 时用；混合端自检用固定 UUID）。
     * 名字照原版上限不超过 16 个字符。同一个 UUID 可以建多次（每次是新对象，探测缓存按 UUID 共用）。
     */
    public static ServerPlayer realPlayer(GameTestHelper helper, UUID id, String name) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(id, name));
        // 构造器把自己挂到 player.connection 上；Connection 没有网络通道，send 只进队列不会崩
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 1D, 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 假玩家（机械手、自动化）：权限工具一律当它没有权限 */
    public static FakePlayer fakePlayer(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString("5cd89d0b-e9ba-0000-89f4-badbb05964ff"), "[EC5]Test"));
    }

    /**
     * 模拟领地插件：挂一个破坏事件和一个放置事件（含多格放置）监听器，事件落在 {@link #deny} 过的格子上就取消，
     * 另按格子记收到几次事件、探测事件（{@code ECProtection.isProbing()}）有几次、每名玩家各几次（{@link #breaksBy} / {@link #placesBy}，
     * 用来断言「事件是以主人身份发的」）。只管一个世界（{@link #open(GameTestHelper)} 是测试所在的世界）。用完 {@link #close()} 摘掉监听器（try-with-resources）。
     * 带 {@code relative} 参数的方法坐标都是相对测试结构的。
     * <p>
     * {@link #denyPlace}：<b>只拒放置、放行破坏</b>的格子（Residence 的 place 和 destroy 是两个独立标志，给某人 destroy=true、place=false 是真实存在的组合）：
     * 探测（破坏事件）放行、真改（放置事件）被拒——先探测后真改的代码最容易在这里复制（policy §2.1 ⑨）。
     * <p>
     * 第六批 6a（F6-D3）：{@link #open(GameTestHelper, ServerLevel)} 指定别的世界（例：被 {@code ECHoanna.setTestHoannaOverride} 当成海纳尔的世界，
     * 或者跨维度建门的目标世界），{@link #denyBox} / {@link #denyAt} 用<b>绝对坐标</b>拒一整块 / 一格（传送门在别处建门、找点时相对坐标不好用），
     * {@link #breaksAt} / {@link #placesAt} 按绝对坐标数，{@link #placeEvents()} 数放置事件本身有几次（一次多格放置算一次）。
     * <p>
     * 监听器是 NORMAL 优先级、不收已取消的：更高优先级的监听器取消了的事件这里看不到。
     */
    public static final class Claims implements AutoCloseable {
        private final GameTestHelper helper;
        private final Level level;
        private final Set<BlockPos> denied = new HashSet<>();
        private final Set<BlockPos> placeDenied = new HashSet<>();
        private final List<BoundingBox> deniedBoxes = new ArrayList<>();
        private final Map<BlockPos, Integer> breaks = new HashMap<>();
        private final Map<BlockPos, Integer> places = new HashMap<>();
        private final Map<BlockPos, Integer> probes = new HashMap<>();
        private final Map<BlockPos, Map<UUID, Integer>> breaksByPlayer = new HashMap<>();
        private final Map<BlockPos, Map<UUID, Integer>> placesByPlayer = new HashMap<>();
        private int placeEvents;
        private final Consumer<BlockEvent.BreakEvent> onBreak;
        private final Consumer<BlockEvent.EntityPlaceEvent> onPlace;

        private Claims(GameTestHelper helper, Level level) {
            this.helper = helper;
            this.level = level;
            this.onBreak = event -> {
                if (event.getLevel() != level) {
                    return;
                }
                BlockPos pos = event.getPos().immutable();
                breaks.merge(pos, 1, Integer::sum);
                if (event.getPlayer() != null) {
                    breaksByPlayer.computeIfAbsent(pos, p -> new HashMap<>()).merge(event.getPlayer().getUUID(), 1, Integer::sum);
                }
                if (ECProtection.isProbing()) {
                    probes.merge(pos, 1, Integer::sum);
                }
                if (isDenied(pos)) {
                    event.setCanceled(true);
                }
            };
            this.onPlace = event -> {
                if (event.getLevel() != level) {
                    return;
                }
                placeEvents++;
                List<BlockPos> positions = new ArrayList<>();
                if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
                    for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                        positions.add(snapshot.getPos().immutable());
                    }
                } else {
                    positions.add(event.getPos().immutable());
                }
                UUID by = event.getEntity() == null ? null : event.getEntity().getUUID();
                for (BlockPos pos : positions) {
                    places.merge(pos, 1, Integer::sum);
                    if (by != null) {
                        placesByPlayer.computeIfAbsent(pos, p -> new HashMap<>()).merge(by, 1, Integer::sum);
                    }
                    if (isDenied(pos) || placeDenied.contains(pos)) {
                        event.setCanceled(true);
                    }
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.BreakEvent.class, onBreak);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, onPlace);
        }

        private boolean isDenied(BlockPos pos) {
            if (denied.contains(pos)) {
                return true;
            }
            for (BoundingBox box : deniedBoxes) {
                if (box.isInside(pos)) {
                    return true;
                }
            }
            return false;
        }

        /** 管测试所在的世界 */
        public static Claims open(GameTestHelper helper) {
            return new Claims(helper, helper.getLevel());
        }

        /** 管指定的世界（第六批 6a：跨维度建门的目标世界、被当成海纳尔的测试世界） */
        public static Claims open(GameTestHelper helper, ServerLevel level) {
            return new Claims(helper, level);
        }

        /** 这一格算别人的领地：破坏 / 放置事件都拒绝 */
        public Claims deny(BlockPos relative) {
            denied.add(helper.absolutePos(relative));
            return this;
        }

        /** 同 {@link #deny}，绝对坐标 */
        public Claims denyAt(BlockPos absolute) {
            denied.add(absolute.immutable());
            return this;
        }

        /** 这一整块（绝对坐标，含边界）算别人的领地：破坏 / 放置事件都拒绝 */
        public Claims denyBox(BoundingBox absolute) {
            deniedBoxes.add(absolute);
            return this;
        }

        /** 撤掉所有 {@link #denyBox} */
        public Claims clearBoxes() {
            deniedBoxes.clear();
            return this;
        }

        /** 这一格只拒放置、放行破坏（探测放行、真改被拒，见类注释） */
        public Claims denyPlace(BlockPos relative) {
            placeDenied.add(helper.absolutePos(relative));
            return this;
        }

        /** 这一格又能动了（{@link #deny} 和 {@link #denyPlace} 都撤掉；{@link #denyBox} 的块不受影响） */
        public Claims allow(BlockPos relative) {
            BlockPos pos = helper.absolutePos(relative);
            denied.remove(pos);
            placeDenied.remove(pos);
            return this;
        }

        /** 同 {@link #allow}，绝对坐标 */
        public Claims allowAt(BlockPos absolute) {
            denied.remove(absolute);
            placeDenied.remove(absolute);
            return this;
        }

        /** 这一格收到了几次破坏事件（含探测） */
        public int breaks(BlockPos relative) {
            return breaksAt(helper.absolutePos(relative));
        }

        /** 同 {@link #breaks}，绝对坐标 */
        public int breaksAt(BlockPos absolute) {
            return breaks.getOrDefault(absolute, 0);
        }

        /** 其中几次是 ECProtection 的探测 */
        public int probes(BlockPos relative) {
            return probes.getOrDefault(helper.absolutePos(relative), 0);
        }

        /** 这一格收到了几次放置事件（多格放置里每格各算一次） */
        public int places(BlockPos relative) {
            return placesAt(helper.absolutePos(relative));
        }

        /** 同 {@link #places}，绝对坐标 */
        public int placesAt(BlockPos absolute) {
            return places.getOrDefault(absolute, 0);
        }

        /** 这个世界一共收到几次放置事件（一次多格放置算一次，不管被没被拒） */
        public int placeEvents() {
            return placeEvents;
        }

        /** 这一格收到了几次<b>这名玩家</b>（事件实体的 UUID）的破坏事件（含探测） */
        public int breaksBy(BlockPos relative, UUID player) {
            return breaksByAt(helper.absolutePos(relative), player);
        }

        /**
         * 同 {@link #breaksBy}，绝对坐标。测试结构外面的格子（例：力场穹顶探测的区块中心）用这个：
         * 1.20.1 的 {@code GameTestHelper.relativePos} 和 {@code absolutePos} 互不为逆（没转向的结构也按转了 180° 换算），换算一圈会落到别的格子
         */
        public int breaksByAt(BlockPos absolute, UUID player) {
            return breaksByPlayer.getOrDefault(absolute.immutable(), Map.of()).getOrDefault(player, 0);
        }

        /** 这一格收到了几次<b>这名玩家</b>（事件实体的 UUID）的放置事件（多格放置里每格各算一次） */
        public int placesBy(BlockPos relative, UUID player) {
            return placesByPlayer.getOrDefault(helper.absolutePos(relative), Map.of()).getOrDefault(player, 0);
        }

        /** 管的是哪个世界 */
        public Level level() {
            return level;
        }

        @Override
        public void close() {
            MinecraftForge.EVENT_BUS.unregister(onBreak);
            MinecraftForge.EVENT_BUS.unregister(onPlace);
        }
    }

    // ------------------------------------------------------------------
    // canEdit：探测、缓存、预算
    // ------------------------------------------------------------------

    /** 空地能动、被模拟领地拒绝的格子不能动；每格发一次探测用的破坏事件、方块不变；假玩家一律不能动、不发事件 */
    @GameTest(template = EMPTY)
    public static void protectionCanEditRespectsClaims(GameTestHelper helper) {
        BlockPos free = new BlockPos(1, 1, 2);
        BlockPos claimed = new BlockPos(3, 1, 2);
        helper.setBlock(claimed, Blocks.DIRT);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = realPlayer(helper);
        try (Claims claims = Claims.open(helper).deny(claimed)) {
            helper.assertTrue(ECProtection.canEdit(player, level, helper.absolutePos(free)), "空地应能动");
            helper.assertFalse(ECProtection.canEdit(player, level, helper.absolutePos(claimed)), "模拟领地里的格子不应能动");
            helper.assertTrue(ECProtection.check(player, level, helper.absolutePos(claimed), player) == ECProtection.Verdict.DENY,
                    "check 应给出 DENY");
            helper.assertTrue(claims.breaks(free) == 1 && claims.breaks(claimed) == 1,
                    "每格应只探测一次（第二次问走缓存），实际 " + claims.breaks(free) + " / " + claims.breaks(claimed));
            helper.assertTrue(claims.probes(claimed) == 1, "探测时 ECProtection.isProbing() 应为 true");
            helper.assertBlockPresent(Blocks.DIRT, claimed);
            helper.assertFalse(ECProtection.isProbing(), "探测完应复位");

            FakePlayer fake = fakePlayer(helper);
            BlockPos other = new BlockPos(2, 1, 3);
            helper.assertFalse(ECProtection.canEdit(fake, level, helper.absolutePos(other)), "假玩家一律不能动");
            helper.assertFalse(ECProtection.canEdit(null, level, helper.absolutePos(other)), "没有玩家一律不能动");
            helper.assertTrue(claims.breaks(other) == 0, "假玩家不应发探测事件");
        }
        helper.succeed();
    }

    /** 结果按（玩家、维度、格子）缓存：缓存期内领地变了也沿用；forget 以后重新探测；拒绝也缓存；别的玩家不共用 */
    @GameTest(template = EMPTY)
    public static void protectionProbeCache(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos abs = helper.absolutePos(pos);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = realPlayer(helper);
        try (Claims claims = Claims.open(helper)) {
            helper.assertTrue(ECProtection.canEdit(player, level, abs), "一开始应能动");
            claims.deny(pos);
            helper.assertTrue(ECProtection.canEdit(player, level, abs), "缓存期内应沿用上次的结果");
            helper.assertTrue(claims.breaks(pos) == 1, "缓存命中不应再发事件，实际 " + claims.breaks(pos));

            ECProtection.forget(player.getUUID());
            helper.assertFalse(ECProtection.canEdit(player, level, abs), "forget 以后应重新探测、被拒");
            claims.allow(pos);
            helper.assertFalse(ECProtection.canEdit(player, level, abs), "拒绝也应缓存");
            helper.assertTrue(claims.breaks(pos) == 2, "应共探测 2 次，实际 " + claims.breaks(pos));

            ServerPlayer other = realPlayer(helper);
            helper.assertTrue(ECProtection.canEdit(other, level, abs), "别的玩家不共用缓存，应重新探测");
            helper.assertTrue(claims.breaks(pos) == 3, "别的玩家应另探测一次，实际 " + claims.breaks(pos));
        }
        helper.succeed();
    }

    /** 每个调用方每 tick 最多新探测 max_probes_per_tick 格，多出来的给 UNKNOWN；缓存命中不花预算；别的调用方不受影响 */
    @GameTest(template = EMPTY)
    public static void protectionProbeBudget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = realPlayer(helper);
        Object caller = new Object();
        int max = ECProtection.Config.maxProbesPerTick();
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i <= max + 1; i++) {
            cells.add(helper.absolutePos(new BlockPos(i % 5, 1 + i / 25, (i / 5) % 5)));
        }
        for (int i = 0; i < max; i++) {
            ECProtection.Verdict verdict = ECProtection.check(player, level, cells.get(i), caller);
            helper.assertTrue(verdict == ECProtection.Verdict.ALLOW, "第 " + (i + 1) + " 格应在预算内，实际 " + verdict);
        }
        helper.assertTrue(ECProtection.check(player, level, cells.get(max), caller) == ECProtection.Verdict.UNKNOWN,
                "超出本 tick 预算应给 UNKNOWN");
        helper.assertTrue(ECProtection.check(player, level, cells.get(0), caller) == ECProtection.Verdict.ALLOW,
                "缓存命中不花预算");
        helper.assertTrue(ECProtection.check(player, level, cells.get(max + 1), new Object()) == ECProtection.Verdict.ALLOW,
                "别的调用方有自己的预算");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // placeGuarded / breakGuarded
    // ------------------------------------------------------------------

    /**
     * 空地放得下；被拒的格子原样还原、只发一次放置事件；在外层捕获里调用时外层的开关和列表原样放回、这一格不被外层打包；
     * 不许把带方块实体的另一种方块（箱子）换掉（防复制），箱子和里面的东西都在；假玩家放不了
     */
    @GameTest(template = EMPTY)
    public static void protectionPlaceGuardedRestoresWhenDenied(GameTestHelper helper) {
        BlockPos free = new BlockPos(1, 1, 1);
        BlockPos claimed = new BlockPos(3, 1, 1);
        BlockPos nested = new BlockPos(1, 1, 3);
        BlockPos chest = new BlockPos(3, 1, 3);
        BlockPos byFake = new BlockPos(2, 1, 2);
        helper.setBlock(claimed, Blocks.DIRT);
        helper.setBlock(chest, Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 3));
        ServerLevel level = helper.getLevel();
        ServerPlayer player = realPlayer(helper);
        BlockState stone = Blocks.STONE.defaultBlockState();
        try (Claims claims = Claims.open(helper).deny(claimed)) {
            helper.assertTrue(ECProtection.placeGuarded(player, level, helper.absolutePos(free), stone, Direction.UP), "空地应放得下");
            helper.assertBlockPresent(Blocks.STONE, free);
            helper.assertFalse(ECProtection.placeGuarded(player, level, helper.absolutePos(claimed), stone, Direction.UP), "被拒应返回 false");
            helper.assertBlockPresent(Blocks.DIRT, claimed);
            helper.assertTrue(claims.places(free) == 1 && claims.places(claimed) == 1,
                    "每格应只发一次放置事件，实际 " + claims.places(free) + " / " + claims.places(claimed));
            helper.assertTrue(claims.breaks(claimed) == 0, "放置不应顺带发破坏事件");

            // 模拟在 useOn 里：ForgeHooks 开着捕获
            List<BlockSnapshot> outerBefore = new ArrayList<>(level.capturedBlockSnapshots);
            level.captureBlockSnapshots = true;
            try {
                helper.assertTrue(ECProtection.placeGuarded(player, level, helper.absolutePos(nested), Blocks.GLASS.defaultBlockState(), Direction.UP),
                        "外层捕获里也应放得下");
                helper.assertTrue(level.captureBlockSnapshots, "外层捕获的开关应原样放回");
                helper.assertTrue(level.capturedBlockSnapshots.size() == outerBefore.size(), "这一格不应再被外层捕获打包");
            } finally {
                level.captureBlockSnapshots = false;
                level.capturedBlockSnapshots.clear();
                level.capturedBlockSnapshots.addAll(outerBefore);
            }
            helper.assertBlockPresent(Blocks.GLASS, nested);

            helper.assertFalse(ECProtection.placeGuarded(player, level, helper.absolutePos(chest), stone, Direction.UP), "不许换掉容器");
            helper.assertBlockPresent(Blocks.CHEST, chest);
            helper.assertTrue(((ChestBlockEntity) helper.getBlockEntity(chest)).getItem(0).getCount() == 3, "箱子里的东西应还在");
            helper.assertItemEntityNotPresent(Items.DIAMOND, chest, 2D);
            helper.assertTrue(claims.places(chest) == 0, "不许换的格子不应发事件");

            helper.assertFalse(ECProtection.placeGuarded(fakePlayer(helper), level, helper.absolutePos(byFake), stone, Direction.UP), "假玩家放不了");
            helper.assertBlockPresent(Blocks.AIR, byFake);
            helper.assertTrue(claims.places(byFake) == 0, "假玩家不应发事件");
        }
        helper.succeed();
    }

    /** 放行才挖、按要求掉落；被拒的不挖不掉、顺手更新 canEdit 缓存；硬度 &lt; 0 的不挖、不发事件；真破坏不算探测 */
    @GameTest(template = EMPTY)
    public static void protectionBreakGuarded(GameTestHelper helper) {
        BlockPos free = new BlockPos(1, 1, 1);
        BlockPos claimed = new BlockPos(3, 1, 3);
        BlockPos bedrock = new BlockPos(1, 1, 3);
        helper.setBlock(free, Blocks.DIRT);
        helper.setBlock(claimed, Blocks.DIRT);
        helper.setBlock(bedrock, Blocks.BEDROCK);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = realPlayer(helper);
        try (Claims claims = Claims.open(helper).deny(claimed)) {
            helper.assertTrue(ECProtection.breakGuarded(player, level, helper.absolutePos(free), ItemStack.EMPTY, true), "空地应挖得动");
            helper.assertBlockPresent(Blocks.AIR, free);
            helper.assertItemEntityPresent(Items.DIRT, free, 1.5D);

            helper.assertFalse(ECProtection.breakGuarded(player, level, helper.absolutePos(claimed), ItemStack.EMPTY, true), "被拒应返回 false");
            helper.assertBlockPresent(Blocks.DIRT, claimed);
            helper.assertItemEntityNotPresent(Items.DIRT, claimed, 1D);
            helper.assertTrue(claims.probes(free) == 0 && claims.probes(claimed) == 0, "真破坏不应算探测");

            helper.assertFalse(ECProtection.canEdit(player, level, helper.absolutePos(claimed)), "被拒的结果应进缓存");
            helper.assertTrue(claims.breaks(claimed) == 1, "再问被拒的格子应走缓存，实际 " + claims.breaks(claimed));

            helper.assertFalse(ECProtection.breakGuarded(player, level, helper.absolutePos(bedrock), ItemStack.EMPTY, true), "基岩不挖");
            helper.assertBlockPresent(Blocks.BEDROCK, bedrock);
            helper.assertTrue(claims.breaks(bedrock) == 0, "不可破坏的方块不应发事件");
        }
        helper.succeed();
    }

    /**
     * 探测和代破坏发 BreakEvent 时主手是空的（别的模组按主手工具掉卡、连锁挖，见 ECProtection 类注释「副作用」），
     * 事后主手放回原来那个对象；代破坏期间 isGuardedBreak 为 true，探测期间为 false
     */
    @GameTest(template = EMPTY)
    public static void protectionHidesMainHandDuringEvents(GameTestHelper helper) {
        BlockPos probed = new BlockPos(1, 1, 1);
        BlockPos broken = new BlockPos(3, 1, 3);
        helper.setBlock(probed, Blocks.DIRT);
        helper.setBlock(broken, Blocks.DIRT);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = realPlayer(helper);
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        player.getInventory().selected = 0;
        player.getInventory().items.set(0, pickaxe);

        List<String> seen = new ArrayList<>();
        Consumer<BlockEvent.BreakEvent> spy = event -> {
            if (event.getLevel() == level) {
                seen.add(event.getPlayer().getMainHandItem().isEmpty() + "/" + ECProtection.isGuardedBreak());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, spy);
        try {
            ECProtection.clearCache();
            helper.assertTrue(ECProtection.canEdit(player, level, helper.absolutePos(probed)), "空地应能动");
            helper.assertTrue(ECProtection.breakGuarded(player, level, helper.absolutePos(broken), ItemStack.EMPTY, false), "空地应挖得动");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(spy);
        }
        helper.assertTrue(seen.equals(List.of("true/false", "true/true")), "探测、代破坏时主手应为空，实际 " + seen);
        helper.assertTrue(player.getInventory().items.get(0) == pickaxe, "事后主手应放回原来那个物品");
        helper.assertFalse(ECProtection.isGuardedBreak(), "代破坏结束后标记应复位");
        helper.succeed();
    }

    /**
     * 要问的格子是牌子时探测改问下面那一格（下面也是牌子就问上面）：牌子那格一次破坏事件都不收到，结论跟着下面那格走；
     * 有的领地插件收到牌子的破坏事件、判定放行时会把它当成出售 / 出租牌撤掉登记
     */
    @GameTest(template = EMPTY)
    public static void protectionProbeAvoidsSigns(GameTestHelper helper) {
        BlockPos sign = new BlockPos(1, 2, 1);
        BlockPos below = sign.below();
        BlockPos stacked = new BlockPos(3, 2, 3);
        helper.setBlock(below, Blocks.DIRT);
        helper.setBlock(sign, Blocks.OAK_SIGN);
        helper.setBlock(stacked.below(), Blocks.OAK_SIGN);
        helper.setBlock(stacked, Blocks.OAK_SIGN);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = realPlayer(helper);
        ECProtection.forget(player.getUUID());
        try (Claims claims = Claims.open(helper).deny(below)) {
            ECProtection.Verdict verdict = ECProtection.check(player, level, helper.absolutePos(sign), player);
            helper.assertTrue(claims.breaks(sign) == 0, "牌子那格不该收到探测，实际 " + claims.breaks(sign));
            helper.assertTrue(claims.breaks(below) == 1, "应改问牌子下面那一格，实际 " + claims.breaks(below));
            helper.assertTrue(verdict == ECProtection.Verdict.DENY, "结论跟着下面那格（被拒），实际 " + verdict);
            ECProtection.check(player, level, helper.absolutePos(stacked), player);
            helper.assertTrue(claims.breaks(stacked) == 0 && claims.breaks(stacked.below()) == 0 && claims.breaks(stacked.above()) == 1,
                    "上下两格牌子时应问上面那一格，实际 " + claims.breaks(stacked.below()) + " / " + claims.breaks(stacked) + " / " + claims.breaks(stacked.above()));
        } finally {
            ECProtection.forget(player.getUUID());
        }
        helper.assertBlockPresent(Blocks.OAK_SIGN, sign);
        helper.succeed();
    }

    /**
     * 探测发的是 {@link ProtectionProbeEvent}（{@code BreakEvent} 的子类）：只认 {@code BreakEvent} 的监听器（模拟领地、旁听的监听器）照样收到，
     * 取消照样算「不能动」；直接监听子类的只收到探测；事件里的格子、方块、玩家都对；方块不动。
     * 类名以 ProbeEvent 结尾是给混合端按名字认探测用的约定，这里守着别被改名。
     */
    @GameTest(template = EMPTY)
    public static void protectionProbeIsProbeEvent(GameTestHelper helper) {
        BlockPos free = new BlockPos(1, 1, 1);
        BlockPos claimed = new BlockPos(3, 1, 3);
        helper.setBlock(free, Blocks.DIRT);
        helper.setBlock(claimed, Blocks.DIRT);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = realPlayer(helper);
        BlockPos freeAbs = helper.absolutePos(free);
        BlockPos claimedAbs = helper.absolutePos(claimed);

        List<String> seen = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        Consumer<BlockEvent.BreakEvent> spy = event -> {
            if (event.getLevel() != level) {
                return;
            }
            String where = event.getPos().equals(freeAbs) ? "free" : event.getPos().equals(claimedAbs) ? "claimed" : event.getPos().toShortString();
            seen.add(event.getClass().getSimpleName() + "/" + where + "/" + event.isCanceled());
            if (event.getPlayer() != player || !event.getState().is(Blocks.DIRT)) {
                wrong.add(where + "：玩家 " + event.getPlayer() + " 方块 " + event.getState());
            }
        };
        List<BlockPos> probesOnly = new ArrayList<>();
        Consumer<ProtectionProbeEvent> probeSpy = event -> {
            if (event.getLevel() == level) {
                probesOnly.add(event.getPos().immutable());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockEvent.BreakEvent.class, spy);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProtectionProbeEvent.class, probeSpy);
        try (Claims claims = Claims.open(helper).deny(claimed)) {
            helper.assertTrue(ECProtection.check(player, level, freeAbs, player) == ECProtection.Verdict.ALLOW, "空地应能动");
            helper.assertTrue(ECProtection.check(player, level, claimedAbs, player) == ECProtection.Verdict.DENY,
                    "只认 BreakEvent 的模拟领地取消了探测，应给出 DENY");
            helper.assertTrue(claims.breaks(free) == 1 && claims.breaks(claimed) == 1,
                    "模拟领地（监听 BreakEvent）应各收到一次探测，实际 " + claims.breaks(free) + " / " + claims.breaks(claimed));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(spy);
            MinecraftForge.EVENT_BUS.unregister(probeSpy);
        }
        helper.assertTrue(seen.equals(List.of("ProtectionProbeEvent/free/false", "ProtectionProbeEvent/claimed/true")),
                "BreakEvent 监听器应收到两次 ProtectionProbeEvent（被拒的那次已取消），实际 " + seen);
        helper.assertTrue(wrong.isEmpty(), "探测事件里的玩家、方块不对：" + wrong);
        helper.assertTrue(probesOnly.equals(List.of(freeAbs, claimedAbs)), "直接监听 ProtectionProbeEvent 的应收到这两次探测，实际 " + probesOnly);
        helper.assertBlockPresent(Blocks.DIRT, free);
        helper.assertBlockPresent(Blocks.DIRT, claimed);
        helper.assertTrue(BlockEvent.BreakEvent.class.isAssignableFrom(ProtectionProbeEvent.class), "ProtectionProbeEvent 应是 BreakEvent 的子类");
        helper.assertTrue(ProtectionProbeEvent.class.getSimpleName().endsWith("ProbeEvent"),
                "类名应以 ProbeEvent 结尾（混合端按这个约定认探测），实际 " + ProtectionProbeEvent.class.getSimpleName());
        helper.assertTrue(new ProtectionProbeEvent(level, freeAbs, Blocks.DIRT.defaultBlockState(), player).isCancelable(), "探测事件应可取消");
        helper.succeed();
    }

    /**
     * 真的代破坏（breakGuarded、harvestGuarded、批量 harvestGuarded）发的仍是普通 {@code BreakEvent}，不是探测事件：
     * 按破坏发奖励、记日志的监听器照常把它们当成挖了一格；被拒的那次同样是普通事件、方块不动
     */
    @GameTest(template = EMPTY)
    public static void protectionRealBreaksArePlainBreakEvents(GameTestHelper helper) {
        BlockPos broken = new BlockPos(1, 1, 1);
        BlockPos harvested = new BlockPos(3, 1, 1);
        BlockPos batch = new BlockPos(1, 1, 3);
        BlockPos claimed = new BlockPos(3, 1, 3);
        for (BlockPos pos : List.of(broken, harvested, batch, claimed)) {
            helper.setBlock(pos, Blocks.DIRT);
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer player = realPlayer(helper);
        List<String> seen = new ArrayList<>();
        int[] probeEvents = new int[1];
        Consumer<BlockEvent.BreakEvent> spy = event -> {
            if (event.getLevel() == level) {
                seen.add((event.getClass() == BlockEvent.BreakEvent.class ? "plain" : event.getClass().getSimpleName()) + "/" + event.isCanceled());
            }
        };
        Consumer<ProtectionProbeEvent> probeSpy = event -> {
            if (event.getLevel() == level) {
                probeEvents[0]++;
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockEvent.BreakEvent.class, spy);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProtectionProbeEvent.class, probeSpy);
        try (Claims claims = Claims.open(helper).deny(claimed)) {
            helper.assertTrue(ECProtection.breakGuarded(player, level, helper.absolutePos(broken), ItemStack.EMPTY, true), "空地应挖得动");
            helper.assertTrue(ECProtection.harvestGuarded(player, level, helper.absolutePos(harvested), ItemStack.EMPTY) != null, "空地应收得了");
            helper.assertTrue(ECProtection.harvestGuarded(player, level, helper.absolutePos(batch), ItemStack.EMPTY, true) != null, "批量也应收得了");
            helper.assertFalse(ECProtection.breakGuarded(player, level, helper.absolutePos(claimed), ItemStack.EMPTY, true), "被拒的不挖");
            helper.assertTrue(claims.probes(broken) + claims.probes(harvested) + claims.probes(batch) + claims.probes(claimed) == 0,
                    "代破坏期间 isProbing 应为 false");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(spy);
            MinecraftForge.EVENT_BUS.unregister(probeSpy);
        }
        helper.assertTrue(seen.equals(List.of("plain/false", "plain/false", "plain/false", "plain/true")),
                "四次代破坏都应是普通 BreakEvent，实际 " + seen);
        helper.assertTrue(probeEvents[0] == 0, "代破坏不应发 ProtectionProbeEvent，实际 " + probeEvents[0] + " 次");
        helper.assertBlockPresent(Blocks.AIR, broken);
        helper.assertBlockPresent(Blocks.AIR, harvested);
        helper.assertBlockPresent(Blocks.AIR, batch);
        helper.assertBlockPresent(Blocks.DIRT, claimed);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // safeExplode
    // ------------------------------------------------------------------

    /**
     * 爆炸后周围方块不变、不起火；盔甲架、展示框（连里面的物品）、掉落物、经验球都不受损；旁边的牛受伤，伤害记在攻击者头上
     */
    @GameTest(template = EMPTY_LARGE)
    public static void protectionSafeExplodeKeepsScenery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos glass = new BlockPos(4, 2, 4);
        BlockPos dirt = new BlockPos(7, 2, 4);
        BlockPos planks = new BlockPos(4, 1, 7);
        BlockPos wall = new BlockPos(5, 2, 8);
        helper.setBlock(glass, Blocks.GLASS);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(planks, Blocks.OAK_PLANKS);
        helper.setBlock(wall, Blocks.STONE);

        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(4, 1, 5));
        ItemFrame frame = new ItemFrame(level, helper.absolutePos(wall.north()), Direction.NORTH);
        frame.setItem(new ItemStack(Items.DIAMOND));
        level.addFreshEntity(frame);
        ItemEntity stick = helper.spawnItem(Items.STICK, 6.5F, 1.2F, 5.5F);
        Vec3 orbAt = helper.absoluteVec(new Vec3(4.5D, 1.2D, 6.5D));
        ExperienceOrb orb = new ExperienceOrb(level, orbAt.x, orbAt.y, orbAt.z, 5);
        level.addFreshEntity(orb);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(8, 1, 5));
        ServerPlayer attacker = realPlayer(helper);

        Explosion explosion = ECProtection.safeExplode(level, null, attacker, helper.absoluteVec(new Vec3(5.5D, 2D, 5.5D)), 2F);
        helper.assertTrue(explosion != null, "服务端应返回这次爆炸");
        helper.assertFalse(ECProtection.isSafeExplosion(explosion), "爆炸结束后标记应撤掉");

        helper.assertBlockPresent(Blocks.GLASS, glass);
        helper.assertBlockPresent(Blocks.DIRT, dirt);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, planks);
        helper.assertBlockPresent(Blocks.STONE, wall);
        for (BlockPos pos : BlockPos.betweenClosed(0, 1, 0, 10, 7, 10)) {
            helper.assertBlockNotPresent(Blocks.FIRE, pos);
        }
        helper.assertTrue(stand.isAlive() && stand.getHealth() == stand.getMaxHealth(), "盔甲架不应受损");
        helper.assertTrue(frame.isAlive() && frame.getItem().is(Items.DIAMOND), "展示框和里面的物品不应受损");
        helper.assertTrue(stick.isAlive(), "掉落物不应被炸掉");
        helper.assertTrue(orb.isAlive(), "经验球不应被炸掉");
        helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "旁边的牛应受伤，实际生命 " + cow.getHealth());
        helper.assertTrue(cow.getLastDamageSource() != null && cow.getLastDamageSource().getEntity() == attacker,
                "伤害应记在攻击者头上");
        helper.succeed();
    }

    /** 标记只认 safeExplode 发起的爆炸：同样不破坏方块的普通爆炸照原版炸掉盔甲架 */
    @GameTest(template = EMPTY)
    public static void protectionOtherExplosionsUnaffected(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
        Vec3 center = helper.absoluteVec(new Vec3(2.5D, 1.5D, 3.5D));
        helper.getLevel().explode(null, center.x, center.y, center.z, 2F, Level.ExplosionInteraction.NONE);
        helper.assertFalse(stand.isAlive(), "普通爆炸应照原版炸掉盔甲架");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 主人
    // ------------------------------------------------------------------

    /**
     * 真玩家放下机器记主人（UUID + 名字），存盘读档不丢、更新包里没有；旧存档读成没有主人；
     * 没有主人 → NONE、checkAsOwner 拒绝；主人不在线 → OFFLINE、默认配置下 UNKNOWN；假玩家放下 → 清空主人
     */
    @GameTest(template = EMPTY)
    public static void protectionOwnerSavedNotSynced(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos abs = helper.absolutePos(pos);
        ServerLevel level = helper.getLevel();
        helper.setBlock(pos, ECBlocks.CHARGING_CHAMBER.get());
        MRUBlockEntity be = (MRUBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be.getOwner() == null && ECProtection.ownerState(be) == ECProtection.OwnerState.NONE, "直接 setBlock 的机器没有主人");
        helper.assertTrue(ECProtection.checkAsOwner(be, abs) == ECProtection.Verdict.DENY, "没有主人的机器不动世界");

        ServerPlayer player = realPlayer(helper);
        BlockState state = helper.getBlockState(pos);
        Block block = state.getBlock();
        block.setPlacedBy(level, abs, state, player, new ItemStack(block));
        helper.assertTrue(player.getUUID().equals(be.getOwner()) && player.getGameProfile().getName().equals(be.getOwnerName()),
                "真玩家放下应记成主人");
        helper.assertTrue(player.getUUID().equals(ECProtection.owner(be)), "ECProtection.owner 应读到主人");

        CompoundTag saved = be.saveWithFullMetadata();
        helper.assertTrue(saved.hasUUID(MRUBlockEntity.OWNER_KEY) && saved.getString(MRUBlockEntity.OWNER_NAME_KEY).equals(be.getOwnerName()),
                "存盘应写主人");
        CompoundTag update = be.getUpdateTag();
        helper.assertTrue(!update.contains(MRUBlockEntity.OWNER_KEY) && !update.contains(MRUBlockEntity.OWNER_NAME_KEY), "更新包里不应有主人");
        helper.assertTrue(update.contains("mru_storage"), "更新包里别的照旧");

        BlockEntity copy = BlockEntity.loadStatic(abs, state, saved);
        helper.assertTrue(copy instanceof MRUBlockEntity loaded && player.getUUID().equals(loaded.getOwner())
                && be.getOwnerName().equals(loaded.getOwnerName()), "读档应读回主人");
        CompoundTag old = saved.copy();
        old.remove(MRUBlockEntity.OWNER_KEY);
        old.remove(MRUBlockEntity.OWNER_NAME_KEY);
        BlockEntity legacy = BlockEntity.loadStatic(abs, state, old);
        helper.assertTrue(legacy instanceof MRUBlockEntity loaded && loaded.getOwner() == null && loaded.getOwnerName().isEmpty(),
                "旧存档应读成没有主人");

        // 测试玩家不在玩家列表里 = 主人不在线
        helper.assertTrue(ECProtection.onlineOwner(be) == null && ECProtection.ownerState(be) == ECProtection.OwnerState.OFFLINE,
                "主人不在线");
        ECProtection.Verdict offline = ECProtection.checkAsOwner(be, abs);
        helper.assertTrue(offline == (ECProtection.Config.requireOwnerOnline() ? ECProtection.Verdict.UNKNOWN : ECProtection.Verdict.ALLOW),
                "主人不在线时应停工（默认配置），实际 " + offline);

        block.setPlacedBy(level, abs, state, fakePlayer(helper), new ItemStack(block));
        helper.assertTrue(be.getOwner() == null && be.getOwnerName().isEmpty(), "假玩家放下应清空主人");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 第四批 F4-1：在线主人覆盖、每主人探测预算
    // ------------------------------------------------------------------

    /**
     * 在线覆盖：主人 = realPlayer，没覆盖时 OFFLINE / UNKNOWN，覆盖后 ONLINE / 以主人真身探测（ALLOW，被拒的格子 DENY），清掉回 OFFLINE；
     * 服主关了在线要求（测试覆盖值）时离线也 ALLOW；假玩家不收进覆盖表
     */
    @GameTest(template = EMPTY)
    public static void protectionOnlineOverride(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos machinePos = new BlockPos(2, 1, 2);
        BlockPos freeRel = new BlockPos(1, 1, 1);
        BlockPos claimedRel = new BlockPos(3, 1, 3);
        helper.setBlock(machinePos, ECBlocks.CHARGING_CHAMBER.get());
        helper.setBlock(claimedRel, Blocks.DIRT);
        MRUBlockEntity be = (MRUBlockEntity) helper.getBlockEntity(machinePos);
        UUID id = UUID.randomUUID();
        ServerPlayer owner = realPlayer(helper, id, "ec5-online");
        ECProtection.recordOwner(be, owner);
        BlockPos free = helper.absolutePos(freeRel);
        BlockPos claimed = helper.absolutePos(claimedRel);
        try (Claims claims = Claims.open(helper).deny(claimedRel)) {
            ECProtection.Config.setRequireOwnerOnlineForTest(true);
            try {
                helper.assertTrue(ECProtection.onlinePlayer(level.getServer(), id) == null && ECProtection.onlineOwner(be) == null,
                        "没覆盖时测试玩家不在线");
                helper.assertTrue(ECProtection.ownerState(be) == ECProtection.OwnerState.OFFLINE, "没覆盖时应为 OFFLINE");
                helper.assertTrue(ECProtection.checkAsOwner(be, free) == ECProtection.Verdict.UNKNOWN, "主人离线应 UNKNOWN");
                helper.assertTrue(claims.breaks(freeRel) == 0, "主人离线不应发探测事件");

                ECProtection.setOnlineOverride(owner);
                helper.assertTrue(ECProtection.onlinePlayer(level.getServer(), id) == owner && ECProtection.onlineOwner(be) == owner,
                        "覆盖后应认成在线");
                helper.assertTrue(ECProtection.ownerState(be) == ECProtection.OwnerState.ONLINE, "覆盖后应为 ONLINE");
                helper.assertTrue(ECProtection.checkAsOwner(be, free) == ECProtection.Verdict.ALLOW, "覆盖后空地应 ALLOW");
                helper.assertTrue(ECProtection.checkAsOwner(be, claimed) == ECProtection.Verdict.DENY, "覆盖后被拒的格子应 DENY");
                helper.assertTrue(claims.breaksBy(freeRel, id) == 1 && claims.breaksBy(claimedRel, id) == 1, "探测应以主人身份发");

                ECProtection.clearOnlineOverride(id);
                helper.assertTrue(ECProtection.ownerState(be) == ECProtection.OwnerState.OFFLINE
                        && ECProtection.checkAsOwner(be, free) == ECProtection.Verdict.UNKNOWN, "清掉覆盖后应回到离线");

                ECProtection.Config.setRequireOwnerOnlineForTest(false);
                helper.assertTrue(ECProtection.checkAsOwner(be, claimed) == ECProtection.Verdict.ALLOW, "关了在线要求时离线主人不做检查");
            } finally {
                ECProtection.Config.setRequireOwnerOnlineForTest(null);
                ECProtection.clearOnlineOverride(id);
            }
        }
        FakePlayer fake = fakePlayer(helper);
        ECProtection.setOnlineOverride(fake);
        helper.assertTrue(ECProtection.onlinePlayer(level.getServer(), fake.getUUID()) == null, "假玩家不应收进覆盖表");
        ECProtection.clearOnlineOverride(fake.getUUID());
        helper.succeed();
    }

    /**
     * 每主人探测预算：同一主人 10 个调用方在同一 tick 各要新探测若干格，合计放行不超过 max_probes_per_player_tick（测试覆盖成 64），其余 UNKNOWN；
     * 缓存命中不花预算；别的玩家不受影响；下一 tick 预算恢复
     */
    @GameTest(template = EMPTY_LARGE)
    public static void protectionProbeBudgetPerPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = realPlayer(helper);
        int perCaller = Math.min(ECProtection.Config.maxProbesPerTick(), 16);
        int callers = 10;
        int total = perCaller * callers;
        int budget = 64;
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i <= total; i++) {
            cells.add(new BlockPos(i % 11, 1 + i / 121, (i / 11) % 11));
        }
        List<BlockPos> unknown = new ArrayList<>();
        try (Claims claims = Claims.open(helper)) {
            ECProtection.Config.setMaxProbesPerPlayerTickForTest(budget);
            try {
                int allowed = 0;
                for (int c = 0; c < callers; c++) {
                    Object caller = new Object();
                    for (int k = 0; k < perCaller; k++) {
                        BlockPos rel = cells.get(c * perCaller + k);
                        ECProtection.Verdict verdict = ECProtection.check(owner, level, helper.absolutePos(rel), caller);
                        if (verdict == ECProtection.Verdict.ALLOW) {
                            allowed++;
                        } else if (verdict == ECProtection.Verdict.UNKNOWN) {
                            unknown.add(rel);
                        } else {
                            helper.fail("没有领地时不应 DENY：" + rel);
                        }
                    }
                }
                int expected = Math.min(budget, total);
                helper.assertTrue(allowed == expected && unknown.size() == total - expected,
                        "同一主人一 tick 合计应只放行 " + expected + " 格，实际放行 " + allowed + "、UNKNOWN " + unknown.size());
                int events = 0;
                for (int i = 0; i < total; i++) {
                    events += claims.breaks(cells.get(i));
                }
                helper.assertTrue(events == expected, "只有放行的才真的发了探测事件，实际 " + events);
                helper.assertTrue(ECProtection.check(owner, level, helper.absolutePos(cells.get(0)), new Object()) == ECProtection.Verdict.ALLOW,
                        "缓存命中不花预算");
                helper.assertTrue(ECProtection.check(realPlayer(helper), level, helper.absolutePos(cells.get(total)), new Object())
                        == ECProtection.Verdict.ALLOW, "别的玩家有自己的预算");
            } finally {
                ECProtection.Config.setMaxProbesPerPlayerTickForTest(null);
            }
        }
        if (unknown.isEmpty()) {
            helper.succeed();
            return;
        }
        BlockPos later = unknown.get(unknown.size() - 1);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(ECProtection.check(owner, level, helper.absolutePos(later), new Object()) == ECProtection.Verdict.ALLOW,
                    "下一 tick 预算应恢复，这次没探到的格子继续探");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------
    // 第四批 F4-2：先删后掉、harvestGuarded
    // ------------------------------------------------------------------

    /**
     * breakGuarded 先删、删成功才掉落：破坏事件放行后那格被换掉（模拟 Mohist 的 destroyBlock 被取消 / 方块没了）→ 返回 false、不生成掉落物；
     * harvestGuarded 挖石头 → 圆石 ×1、方块没了、地上没有掉落物；被拒 → null、方块在
     */
    @GameTest(template = EMPTY)
    public static void protectionBreakDeletesBeforeDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos vanishingRel = new BlockPos(1, 1, 1);
        BlockPos stoneRel = new BlockPos(3, 1, 3);
        BlockPos claimedRel = new BlockPos(1, 1, 3);
        helper.setBlock(vanishingRel, Blocks.DIRT);
        helper.setBlock(stoneRel, Blocks.STONE);
        helper.setBlock(claimedRel, Blocks.STONE);
        BlockPos vanishing = helper.absolutePos(vanishingRel);
        ServerPlayer player = realPlayer(helper);
        // 放行以后（LOWEST）把那格换成空气：destroyBlock 对空气返回 false。旧顺序（先掉落后删）这里会白掉一份泥土
        Consumer<BlockEvent.BreakEvent> thief = event -> {
            if (event.getLevel() == level && event.getPos().equals(vanishing) && !event.isCanceled()) {
                level.setBlock(vanishing, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, thief);
        try {
            helper.assertFalse(ECProtection.breakGuarded(player, level, vanishing, ItemStack.EMPTY, true), "删不掉就应返回 false");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(thief);
        }
        helper.assertItemEntityNotPresent(Items.DIRT, vanishingRel, 2D);
        helper.assertFalse(ECProtection.isGuardedBreak(), "代破坏结束后标记应复位");

        try (Claims claims = Claims.open(helper).deny(claimedRel)) {
            List<ItemStack> drops = ECProtection.harvestGuarded(player, level, helper.absolutePos(stoneRel), new ItemStack(Items.IRON_PICKAXE));
            helper.assertTrue(drops != null && drops.size() == 1 && drops.get(0).is(Items.COBBLESTONE) && drops.get(0).getCount() == 1,
                    "挖石头应交回圆石 ×1，实际 " + drops);
            helper.assertBlockPresent(Blocks.AIR, stoneRel);
            helper.assertItemEntityNotPresent(Items.COBBLESTONE, stoneRel, 2D);
            helper.assertItemEntityNotPresent(Items.STONE, stoneRel, 2D);
            helper.assertTrue(claims.breaksBy(stoneRel, player.getUUID()) == 1 && claims.probes(stoneRel) == 0, "应以玩家身份发一次真破坏事件");

            helper.assertTrue(ECProtection.harvestGuarded(player, level, helper.absolutePos(claimedRel), ItemStack.EMPTY) == null, "被拒应返回 null");
            helper.assertBlockPresent(Blocks.STONE, claimedRel);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 第四批 F4-3：captureGuarded
    // ------------------------------------------------------------------

    /** 改 3 格、其中 1 格被拒 → 3 格全部还原、期间生成的掉落物被删；放置事件只有一次、实体是主人 */
    @GameTest(template = EMPTY)
    public static void protectionCaptureDeniedRestoresAll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID id = UUID.randomUUID();
        ServerPlayer owner = realPlayer(helper, id, "ec5-capture");
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(2, 1, 1);
        BlockPos c = new BlockPos(3, 1, 1);
        helper.setBlock(c, Blocks.DIRT);
        ItemEntity[] dropped = new ItemEntity[1];
        int sharedBefore = level.capturedBlockSnapshots.size();
        try (Claims claims = Claims.open(helper).deny(b)) {
            ECProtection.CaptureResult result = ECProtection.captureGuarded(owner, level, Direction.UP, List.of(), () -> {
                for (BlockPos pos : List.of(a, b, c)) {
                    level.setBlock(helper.absolutePos(pos), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
                }
                Vec3 at = helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D));
                dropped[0] = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.DIAMOND));
                level.addFreshEntity(dropped[0]);
                return true;
            });
            helper.assertTrue(result.is(ECProtection.CaptureResult.Outcome.DENIED) && result.rejected() && result.refundSafe() && result.acted(),
                    "应被拒且可以退回，实际 " + result);
            helper.assertBlockPresent(Blocks.AIR, a);
            helper.assertBlockPresent(Blocks.AIR, b);
            helper.assertBlockPresent(Blocks.DIRT, c);
            helper.assertTrue(dropped[0].isRemoved() && result.spawned().isEmpty(), "期间生成的掉落物应被删掉");
            helper.assertTrue(claims.placesBy(a, id) == 1 && claims.placesBy(b, id) == 1 && claims.placesBy(c, id) == 1,
                    "应以主人身份发一次（多格）放置事件");
            helper.assertFalse(level.captureBlockSnapshots, "捕获开关应放回");
            helper.assertTrue(level.capturedBlockSnapshots.size() == sharedBefore, "共享快照列表应原样放回");
        }
        helper.succeed();
    }

    /**
     * 全放行 → 改动保留、生成的实体留着；嵌套在外层 useOn 捕获里调用时外层的开关和列表不被清掉；
     * 生成了不许的实体（箭）→ 整次被拒；action 抛异常 → 已改的还原、异常原样抛出；主人为 null 时看在线要求
     */
    @GameTest(template = EMPTY)
    public static void protectionCaptureAllowedNestedAndEdgeCases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID id = UUID.randomUUID();
        ServerPlayer owner = realPlayer(helper, id, "ec5-capture2");
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(2, 1, 1);
        BlockPos nested = new BlockPos(1, 1, 3);
        BlockPos arrowCell = new BlockPos(3, 1, 3);
        BlockPos throwing = new BlockPos(3, 2, 3);
        BlockPos nullOwner = new BlockPos(1, 2, 1);
        BlockState glass = Blocks.GLASS.defaultBlockState();
        try (Claims claims = Claims.open(helper)) {
            ItemEntity[] dropped = new ItemEntity[1];
            ECProtection.CaptureResult allowed = ECProtection.captureGuarded(owner, level, Direction.UP, List.of(), () -> {
                level.setBlock(helper.absolutePos(a), glass, Block.UPDATE_ALL);
                level.setBlock(helper.absolutePos(b), glass, Block.UPDATE_ALL);
                Vec3 at = helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D));
                dropped[0] = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STICK));
                level.addFreshEntity(dropped[0]);
                return false;
            });
            helper.assertTrue(allowed.is(ECProtection.CaptureResult.Outcome.ALLOWED) && !allowed.rejected() && !allowed.acted(),
                    "全放行应 ALLOWED，实际 " + allowed);
            helper.assertBlockPresent(Blocks.GLASS, a);
            helper.assertBlockPresent(Blocks.GLASS, b);
            helper.assertTrue(!dropped[0].isRemoved() && allowed.spawned().contains(dropped[0]), "放行时生成的实体应留着");
            helper.assertTrue(claims.placesBy(a, id) == 1 && claims.placesBy(b, id) == 1, "放置事件应记在主人名下");

            ECProtection.CaptureResult nothing = ECProtection.captureGuarded(owner, level, Direction.UP, List.of(), () -> true);
            helper.assertTrue(nothing.is(ECProtection.CaptureResult.Outcome.NO_CHANGE), "什么都没改应 NO_CHANGE，实际 " + nothing);

            // 模拟在别的 useOn 里：ForgeHooks 开着捕获、列表里已经有一条
            List<BlockSnapshot> outerBefore = new ArrayList<>(level.capturedBlockSnapshots);
            BlockSnapshot outerSnapshot = BlockSnapshot.create(level.dimension(), level, helper.absolutePos(new BlockPos(4, 1, 4)));
            level.capturedBlockSnapshots.add(outerSnapshot);
            level.captureBlockSnapshots = true;
            try {
                ECProtection.CaptureResult inner = ECProtection.captureGuarded(owner, level, Direction.UP, List.of(), () -> {
                    level.setBlock(helper.absolutePos(nested), glass, Block.UPDATE_ALL);
                    return true;
                });
                helper.assertTrue(inner.is(ECProtection.CaptureResult.Outcome.ALLOWED), "嵌套时也应放行，实际 " + inner);
                helper.assertTrue(level.captureBlockSnapshots, "外层捕获的开关应原样放回");
                helper.assertTrue(level.capturedBlockSnapshots.size() == outerBefore.size() + 1
                        && level.capturedBlockSnapshots.get(level.capturedBlockSnapshots.size() - 1) == outerSnapshot, "外层已捕获的列表应原样放回");
            } finally {
                level.captureBlockSnapshots = false;
                level.capturedBlockSnapshots.clear();
                level.capturedBlockSnapshots.addAll(outerBefore);
            }
            helper.assertBlockPresent(Blocks.GLASS, nested);

            Arrow[] arrow = new Arrow[1];
            ECProtection.CaptureResult shot = ECProtection.captureGuarded(owner, level, Direction.UP, List.of(), () -> {
                level.setBlock(helper.absolutePos(arrowCell), glass, Block.UPDATE_ALL);
                Vec3 at = helper.absoluteVec(Vec3.atCenterOf(throwing));
                arrow[0] = new Arrow(level, at.x, at.y, at.z);
                level.addFreshEntity(arrow[0]);
                return true;
            }, entity -> entity instanceof Projectile);
            helper.assertTrue(shot.is(ECProtection.CaptureResult.Outcome.DENIED) && shot.refundSafe(), "生成了不许的实体应整次被拒，实际 " + shot);
            helper.assertTrue(arrow[0].isRemoved(), "不许的实体应被删掉");
            helper.assertBlockPresent(Blocks.AIR, arrowCell);

            boolean thrown = false;
            try {
                ECProtection.captureGuarded(owner, level, Direction.UP, List.of(), () -> {
                    level.setBlock(helper.absolutePos(arrowCell), glass, Block.UPDATE_ALL);
                    throw new IllegalStateException("测试异常");
                });
            } catch (IllegalStateException e) {
                thrown = true;
            }
            helper.assertTrue(thrown, "action 的异常应原样抛出");
            helper.assertBlockPresent(Blocks.AIR, arrowCell);
            helper.assertFalse(level.captureBlockSnapshots, "出异常后捕获开关也应放回");

            boolean[] ran = new boolean[1];
            ECProtection.Config.setRequireOwnerOnlineForTest(true);
            try {
                ECProtection.CaptureResult strict = ECProtection.captureGuarded(null, level, Direction.UP, List.of(), () -> {
                    ran[0] = true;
                    return true;
                });
                helper.assertTrue(strict.is(ECProtection.CaptureResult.Outcome.DENIED) && !ran[0], "主人离线（要求在线）不应执行");
                ECProtection.Config.setRequireOwnerOnlineForTest(false);
                ECProtection.CaptureResult lax = ECProtection.captureGuarded(null, level, Direction.UP, List.of(), () -> {
                    level.setBlock(helper.absolutePos(nullOwner), glass, Block.UPDATE_ALL);
                    return true;
                });
                helper.assertTrue(lax.is(ECProtection.CaptureResult.Outcome.ALLOWED), "关了在线要求应直接放行，实际 " + lax);
                helper.assertTrue(claims.places(nullOwner) == 0, "离线直接做不应发事件");
            } finally {
                ECProtection.Config.setRequireOwnerOnlineForTest(null);
            }
            helper.assertBlockPresent(Blocks.GLASS, nullOwner);
        }
        helper.succeed();
    }

    /**
     * 「只拒放置、放行破坏」：探测放行，但 action 往雕纹书架塞附魔书、往唱片机放唱片（先改方块实体再 setBlock）被拒 →
     * 书架、唱片机都空、状态没变，可以退回（唱片机的 load 不清空唱片，这里验证换回的是新建的方块实体）；
     * 对照：书架不在 beGuard 里 → 不可退回
     */
    @GameTest(template = EMPTY)
    public static void protectionCaptureRestoresBlockEntities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID id = UUID.randomUUID();
        ServerPlayer owner = realPlayer(helper, id, "ec5-capture3");
        BlockPos shelfRel = new BlockPos(1, 1, 1);
        BlockPos jukeboxRel = new BlockPos(3, 1, 1);
        BlockPos unguardedRel = new BlockPos(1, 1, 3);
        helper.setBlock(shelfRel, Blocks.CHISELED_BOOKSHELF);
        helper.setBlock(jukeboxRel, Blocks.JUKEBOX);
        helper.setBlock(unguardedRel, Blocks.CHISELED_BOOKSHELF);
        BlockPos shelf = helper.absolutePos(shelfRel);
        BlockPos jukebox = helper.absolutePos(jukeboxRel);
        try (Claims claims = Claims.open(helper).denyPlace(shelfRel).denyPlace(jukeboxRel).denyPlace(unguardedRel)) {
            helper.assertTrue(ECProtection.canEdit(owner, level, shelf), "只拒放置的格子探测应放行");

            ECProtection.CaptureResult book = ECProtection.captureGuarded(owner, level, Direction.UP, List.of(shelf), () -> {
                ((ChiseledBookShelfBlockEntity) level.getBlockEntity(shelf)).setItem(0, new ItemStack(Items.ENCHANTED_BOOK));
                return true;
            });
            helper.assertTrue(book.is(ECProtection.CaptureResult.Outcome.DENIED) && book.refundSafe(), "书架应被拒且可以退回，实际 " + book);
            ChiseledBookShelfBlockEntity shelfBe = (ChiseledBookShelfBlockEntity) level.getBlockEntity(shelf);
            helper.assertTrue(shelfBe != null && shelfBe.count() == 0, "被拒后书架里不应有书");
            helper.assertFalse(level.getBlockState(shelf).getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(0)), "书架状态应还原");
            helper.assertTrue(claims.placesBy(shelfRel, id) == 1, "应以主人身份发一次放置事件");

            ECProtection.CaptureResult disc = ECProtection.captureGuarded(owner, level, Direction.UP, List.of(jukebox), () -> {
                ((JukeboxBlockEntity) level.getBlockEntity(jukebox)).setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
                return true;
            });
            helper.assertTrue(disc.is(ECProtection.CaptureResult.Outcome.DENIED) && disc.refundSafe(), "唱片机应被拒且可以退回，实际 " + disc);
            JukeboxBlockEntity jukeboxBe = (JukeboxBlockEntity) level.getBlockEntity(jukebox);
            helper.assertTrue(jukeboxBe != null && jukeboxBe.getFirstItem().isEmpty(), "被拒后唱片机里不应有唱片");
            helper.assertFalse(level.getBlockState(jukebox).getValue(JukeboxBlock.HAS_RECORD), "唱片机状态应还原");

            BlockPos unguarded = helper.absolutePos(unguardedRel);
            ECProtection.CaptureResult unsafe = ECProtection.captureGuarded(owner, level, Direction.UP, List.of(), () -> {
                ((ChiseledBookShelfBlockEntity) level.getBlockEntity(unguarded)).setItem(1, new ItemStack(Items.BOOK));
                return true;
            });
            helper.assertTrue(unsafe.is(ECProtection.CaptureResult.Outcome.DENIED) && !unsafe.refundSafe(),
                    "beGuard 以外带方块实体数据的格子被还原时不应可以退回，实际 " + unsafe);
        }
        helper.succeed();
    }

    /**
     * action 里又调了 ItemStack.useOn（Forge 的放置捕获调完把开关置 false、清空共享列表）、再改一格 → INTERRUPTED、不可退回；
     * 里面那次放置照 Forge 自己的规则以使用者身份问过了，后面那格没被捕获、还原不了（所以不许退物品）
     */
    @GameTest(template = EMPTY)
    public static void protectionCaptureInterruptedByNestedUseOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID id = UUID.randomUUID();
        ServerPlayer owner = realPlayer(helper, id, "ec5-capture4");
        BlockPos floorRel = new BlockPos(2, 1, 2);
        BlockPos farRel = new BlockPos(4, 1, 4);
        helper.setBlock(floorRel, Blocks.STONE);
        BlockPos floor = helper.absolutePos(floorRel);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 4));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0D, 0.5D, 0D), Direction.UP, floor, false);
        try (Claims claims = Claims.open(helper)) {
            ECProtection.CaptureResult result = ECProtection.captureGuarded(owner, level, Direction.UP, List.of(floor.above()), () -> {
                InteractionResult used = owner.getMainHandItem().useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit));
                level.setBlock(helper.absolutePos(farRel), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
                return used.consumesAction();
            });
            helper.assertTrue(result.is(ECProtection.CaptureResult.Outcome.INTERRUPTED) && !result.refundSafe() && result.acted(),
                    "嵌套 useOn 后应 INTERRUPTED 且不可退回，实际 " + result);
            helper.assertBlockPresent(Blocks.DIRT, floorRel.above());
            helper.assertTrue(claims.placesBy(floorRel.above(), id) == 1, "里面那次放置应由 Forge 以使用者身份问过");
            helper.assertBlockPresent(Blocks.GLASS, farRel);
            helper.assertFalse(level.captureBlockSnapshots, "捕获开关应放回");
        }
        helper.succeed();
    }
}
