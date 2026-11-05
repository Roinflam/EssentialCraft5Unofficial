package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.MithrilineCrystalPillarBlock;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.menu.PlayerPentacleMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECCrystalPillars;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.flameforge.essentialcraft5.util.ECProtection;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 灵魂五芒星与收容模块（ECPentacle，batch3.md §3.7）的 GameTest：
 * <ul>
 *   <li>结构：0 档成形、缺一块护板 / 一根柱子不成形；加淡青块和淡青柱升 1 档，1 档的格子放米斯莱柱不算；结构的格数和柱子位置照原作。</li>
 *   <li>治疗：8 根满柱（有效 28 800）治一个 6 000 的身体类，效果消失、柱子一共被抽 16 666；ESPE 不够时不治、不抽。</li>
 *   <li>档位：0 档拒治心智类（不抽 ESPE），升到 1 档后能治。</li>
 *   <li>按钮校验：越界 / 伪造的按钮号、身上没有的效果、离太远、客户端菜单一律拒绝；生存玩家发「创造免费」的按钮号不生效，服务端判定的创造模式才免费。</li>
 *   <li>稳定器：放在富集仓外壳上被控制器数到，仓里 2 万 MRU 的聚集体不给旁边玩家加矩阵损伤；换掉后稳定期过了照常加。</li>
 *   <li>淡青块不透光；五芒星挖掉掉自己（原作一挖就抛异常）、挡液体（水冲不掉、岩浆烧不掉）；三条工作台配方加载。</li>
 *   <li>2、3 档（第七批）：真虚空柱、恶魔柱成形，柱位放低一档的柱子退一档；2 档 16 根满柱（有效 57 600）治得了 20 000 的矩阵类、
 *       治不了 72 000 的，3 档每点 ESPE 按 2 倍算（20 根满柱有效 144 000）能治；打开界面给手册 1 / 2 / 3 档打勾、3 档发进度「灵魂净化」。</li>
 * </ul>
 * 模板 empty_large（11×8×11 空气，y=0 是结构方块那层）：五芒星放在 (5, 2, 5)，护板在 y=1，柱子底座在 y=2（淡青柱在 y=3）。
 * 玩家用 ProtectionFrameworkTests.realPlayer（真 ServerPlayer，菜单的 clickMenuButton 只认服务端玩家）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class PentacleTests {

    private static final String LARGE = "empty_large";
    private static final BlockPos PENTACLE = new BlockPos(5, 2, 5);
    private static final double EPS = 1.0E-6D;
    private static final double FULL = 10000D;

    /** 富集仓：5×3×5 外壳，控制器在北墙正中、稳定器在西墙正中（同 SystemsFrameworkTests） */
    private static final BlockPos SHELL_MIN = new BlockPos(3, 1, 3);
    private static final BlockPos SHELL_MAX = new BlockPos(7, 3, 7);
    private static final BlockPos CONTROLLER = new BlockPos(5, 2, 3);
    private static final BlockPos WEST_WALL = new BlockPos(3, 2, 5);

    private PentacleTests() {
    }

    // ================================================================ 工具

    private static BlockPos at(BlockPos offset) {
        return PENTACLE.offset(offset);
    }

    /** 某一档新增的方块位置（北向偏移），从结构定义里取 */
    private static List<BlockPos> offsets(int tier, StructurePattern.Rule rule) {
        return ECPentacle.TIER_PATTERNS.get(tier).offsets(rule);
    }

    /** 搭 0 档：13 块米斯莱护板、8 根米斯莱柱，最后放五芒星 */
    private static PlayerPentacleBlockEntity buildTierZero(GameTestHelper helper) {
        for (BlockPos offset : offsets(0, ECPentacle.PLATING_RULE)) {
            helper.setBlock(at(offset), ECMithriline.MITHRILINE_PLATING.get());
        }
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(0)) {
            helper.setBlock(at(offset), ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        }
        helper.setBlock(PENTACLE, ECPentacle.PLAYER_PENTACLE.get());
        return (PlayerPentacleBlockEntity) helper.getBlockEntity(PENTACLE);
    }

    /** 在 0 档上加 1 档：4 块淡青块、上面 4 根淡青柱 */
    private static void addTierOne(GameTestHelper helper) {
        for (BlockPos offset : offsets(1, ECPentacle.PALE_RULE)) {
            helper.setBlock(at(offset), ECPentacle.PALE_BLOCK.get());
        }
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(1)) {
            helper.setBlock(at(offset), ECPentacle.PALE_CRYSTAL_PILLAR.get());
        }
    }

    /** 在 1 档上加 2 档：外圈四角两层 8 块虚空石、上面 4 根虚空柱 */
    private static void addTierTwo(GameTestHelper helper) {
        for (BlockPos offset : offsets(2, ECPentacle.VOID_STONE_RULE)) {
            helper.setBlock(at(offset), ECVoid.VOID_STONE.get());
        }
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(2)) {
            helper.setBlock(at(offset), ECCrystalPillars.VOID_CRYSTAL_PILLAR.get());
        }
    }

    /** 在 2 档上加 3 档：脚下一层四个正方向 4 块恶魔护板、高 3 格处 4 根恶魔柱 */
    private static void addTierThree(GameTestHelper helper) {
        for (BlockPos offset : offsets(3, ECPentacle.DEMONIC_RULE)) {
            helper.setBlock(at(offset), ECDemonic.DEMONIC_PLATING.get());
        }
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(3)) {
            helper.setBlock(at(offset), ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR.get());
        }
    }

    private static boolean advancementDone(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** 某一档的柱子（底座的方块实体） */
    private static List<MithrilineCrystalPillarBlockEntity> pillars(GameTestHelper helper, int tier) {
        return ECPentacle.PILLAR_OFFSETS.get(tier).stream()
                .map(offset -> (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(at(offset)))
                .toList();
    }

    private static void fill(GameTestHelper helper, int tier, double espe) {
        for (MithrilineCrystalPillarBlockEntity pillar : pillars(helper, tier)) {
            pillar.getESPEStorage().setESPE(espe);
        }
    }

    private static double total(GameTestHelper helper, int maxTier) {
        double sum = 0D;
        for (int t = 0; t <= maxTier; t++) {
            for (MithrilineCrystalPillarBlockEntity pillar : pillars(helper, t)) {
                sum += pillar.getESPEStorage().getESPE();
            }
        }
        return sum;
    }

    /** 真玩家站在五芒星南边 2 格（离方块中心 8 格以内，界面有效） */
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Vec3 stand = helper.absoluteVec(new Vec3(5.5D, 2D, 7.5D));
        player.moveTo(stand.x, stand.y, stand.z, 0F, 0F);
        return player;
    }

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("玩家应挂着玩家数据");
        }
        return data;
    }

    private static int button(String effectId) {
        CorruptionEffects.Effect effect = CorruptionEffects.byId(effectId);
        if (effect == null) {
            throw new IllegalStateException("效果表里没有 " + effectId);
        }
        return PlayerPentacleMenu.BUTTON_CURE + effect.index();
    }

    // ================================================================ 结构

    /** 结构格数照原作：0 档 1 + 13 + 8，每档再加 8 / 12 / 8；各档柱子位置就是原作 coords 的分段 */
    @GameTest(template = LARGE)
    public static void pentacleStructureMatchesOriginal(GameTestHelper helper) {
        int[] required = {22, 30, 42, 50};
        for (int t = 0; t < required.length; t++) {
            int count = ECPentacle.TIER_PATTERNS.get(t).requiredCount();
            helper.assertTrue(count == required[t], t + " 档结构应有 " + required[t] + " 格，实际 " + count);
        }
        StructurePattern full = ECPentacle.TIER_PATTERNS.get(3);
        for (int t = 0; t < 4; t++) {
            Set<BlockPos> inPattern = new HashSet<>(full.offsets(ECPentacle.PILLAR_RULES.get(t)));
            helper.assertTrue(inPattern.equals(new HashSet<>(ECPentacle.PILLAR_OFFSETS.get(t))), t + " 档的柱子位置应和抽 ESPE 的位置一致");
        }
        helper.assertTrue(ECPentacle.pillarOffsets(0).size() == 8 && ECPentacle.pillarOffsets(1).size() == 12
                && ECPentacle.pillarOffsets(3).size() == 20 && ECPentacle.pillarOffsets(-1).isEmpty(), "各档抽 8 / 12 / 20 根柱子");
        helper.assertTrue(offsets(0, ECPentacle.PLATING_RULE).size() == 13, "0 档 13 块米斯莱护板");
        helper.succeed();
    }

    /** 0 档成形；缺一块护板、缺一根柱子都不成形；加淡青块和淡青柱升 1 档；1 档的柱位放米斯莱柱不算 1 档 */
    @GameTest(template = LARGE)
    public static void pentacleTierForms(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerPentacleBlockEntity be = buildTierZero(helper);
        BlockPos abs = helper.absolutePos(PENTACLE);
        helper.assertTrue(be.recheckTier() == 0, "0 档结构应成形，实际档位 " + be.getTier());
        helper.assertTrue(ECPentacle.TIER_PATTERNS.get(0).match(level, abs, Direction.NORTH).result() == StructureResult.MATCH, "0 档结构判定 MATCH");

        BlockPos plating = at(new BlockPos(2, -1, 1));
        helper.setBlock(plating, Blocks.STONE);
        helper.assertTrue(be.recheckTier() == -1, "缺一块护板应不成形");
        helper.setBlock(plating, ECMithriline.MITHRILINE_PLATING.get());
        helper.assertTrue(be.recheckTier() == 0, "补上护板重新成形");

        BlockPos pillar = at(ECPentacle.PILLAR_OFFSETS.get(0).get(3));
        helper.setBlock(pillar, Blocks.AIR);
        helper.assertTrue(be.recheckTier() == -1, "缺一根柱子应不成形");
        helper.setBlock(pillar, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        helper.assertTrue(be.recheckTier() == 0, "补上柱子重新成形");

        addTierOne(helper);
        helper.assertTrue(be.recheckTier() == 1, "加 4 块淡青块、4 根淡青柱应升到 1 档，实际 " + be.getTier());
        BlockState pale = level.getBlockState(helper.absolutePos(at(ECPentacle.PILLAR_OFFSETS.get(1).get(0))));
        helper.assertTrue(MithrilineCrystalPillarBlock.isPillarBase(pale, 1), "淡青柱底座算 1 档柱子");

        BlockPos palePillar = at(ECPentacle.PILLAR_OFFSETS.get(1).get(2));
        helper.setBlock(palePillar, Blocks.AIR);
        helper.setBlock(palePillar, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        helper.assertTrue(be.recheckTier() == 0, "1 档的柱位放米斯莱柱不够档，退回 0 档");
        helper.assertTrue(PlayerPentacleBlockEntity.computeTier(level, abs, 5) == 0, "都加载着时不沿用上次的档位");
        helper.succeed();
    }

    // ================================================================ 治疗

    /** 8 根满柱：有效 28 800；治一个 6 000 的身体类，效果消失、柱子一共被抽 ⌊6000 ÷ 0.36⌋ = 16 666 */
    @GameTest(template = LARGE)
    public static void pentacleCuresBodyEffect(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = buildTierZero(helper);
        fill(helper, 0, FULL);
        be.recheckTier();
        helper.assertTrue(be.effectiveESPE() == 28800, "8 根满柱有效 ESPE 应为 28800，实际 " + be.effectiveESPE());

        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.addCorruptionEffect("body_0");
        PlayerPentacleMenu menu = new PlayerPentacleMenu(0, player.getInventory(), be);
        helper.assertTrue(PlayerPentacleBlockEntity.rawCost(6000) == 16666, "花 6000 有效 ESPE 要抽 16666");
        helper.assertTrue(menu.clickMenuButton(player, button("body_0")), "0 档满柱应能治身体类");
        helper.assertFalse(data.hasCorruptionEffect("body_0"), "治完效果应消失");
        double left = total(helper, 0);
        helper.assertTrue(Math.abs(left - (80000D - 16666D)) < EPS, "柱子应一共被抽 16666，剩 " + left);
        helper.assertTrue(be.effectiveESPE() == (int) Math.floor((80000D - 16666D) * 0.36D), "有效 ESPE 跟着减少，实际 " + be.effectiveESPE());
        helper.assertFalse(menu.clickMenuButton(player, button("body_0")), "身上已经没有的效果再点不做");
        helper.assertTrue(Math.abs(total(helper, 0) - left) < EPS, "没治成不抽 ESPE");
        helper.succeed();
    }

    /** ESPE 不够（8 根各 2000，有效 5760 &lt; 6000）：不治、一点都不抽 */
    @GameTest(template = LARGE)
    public static void pentacleNeedsEnoughEspe(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = buildTierZero(helper);
        fill(helper, 0, 2000D);
        be.recheckTier();
        helper.assertTrue(be.effectiveESPE() == 5760, "有效 ESPE 应为 5760，实际 " + be.effectiveESPE());
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.addCorruptionEffect("body_3");
        PlayerPentacleMenu menu = new PlayerPentacleMenu(0, player.getInventory(), be);
        helper.assertFalse(menu.clickMenuButton(player, button("body_3")), "ESPE 不够不应治");
        helper.assertTrue(data.hasCorruptionEffect("body_3"), "效果还在");
        helper.assertTrue(Math.abs(total(helper, 0) - 16000D) < EPS, "ESPE 不够时一点都不抽");
        helper.succeed();
    }

    /** 0 档拒治心智类（不抽 ESPE）；加上淡青块和淡青柱升到 1 档后能治 14 400 的心智类 */
    @GameTest(template = LARGE)
    public static void pentacleMindNeedsTierOne(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = buildTierZero(helper);
        fill(helper, 0, FULL);
        be.recheckTier();
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.addCorruptionEffect("mind_7");
        PlayerPentacleMenu menu = new PlayerPentacleMenu(0, player.getInventory(), be);
        helper.assertFalse(menu.clickMenuButton(player, button("mind_7")), "0 档不应能治心智类");
        helper.assertTrue(data.hasCorruptionEffect("mind_7") && Math.abs(total(helper, 0) - 80000D) < EPS, "拒绝时效果还在、不抽 ESPE");

        addTierOne(helper);
        helper.assertTrue(be.recheckTier() == 1, "应升到 1 档");
        helper.assertTrue(be.effectiveESPE() == 28800, "新淡青柱是空的，有效 ESPE 仍是 28800，实际 " + be.effectiveESPE());
        helper.assertTrue(menu.clickMenuButton(player, button("mind_7")), "1 档应能治心智类");
        helper.assertFalse(data.hasCorruptionEffect("mind_7"), "治完效果应消失");
        helper.assertTrue(Math.abs(total(helper, 1) - (80000D - PlayerPentacleBlockEntity.rawCost(14400))) < EPS,
                "应抽 ⌊14400 ÷ 0.36⌋ = " + PlayerPentacleBlockEntity.rawCost(14400));
        helper.succeed();
    }

    // ================================================================ 按钮校验

    /** 越界 / 伪造的按钮号、离太远、客户端菜单：一律拒绝，不动效果、不抽 ESPE */
    @GameTest(template = LARGE)
    public static void pentacleRejectsForgedButtons(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = buildTierZero(helper);
        fill(helper, 0, FULL);
        be.recheckTier();
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.addCorruptionEffect("body_1");
        PlayerPentacleMenu menu = new PlayerPentacleMenu(0, player.getInventory(), be);
        for (int id : new int[] {-1, PlayerPentacleMenu.MAX_EFFECTS, 31, PlayerPentacleMenu.BUTTON_CURE_FREE + PlayerPentacleMenu.MAX_EFFECTS, 100, 127}) {
            helper.assertFalse(PlayerPentacleMenu.isValidButton(id), "按钮号 " + id + " 不在范围里");
            helper.assertFalse(menu.clickMenuButton(player, id), "越界的按钮号 " + id + " 应被拒绝");
        }
        helper.assertFalse(menu.clickMenuButton(player, button("body_2")), "身上没有的效果不做");

        Vec3 far = helper.absoluteVec(new Vec3(5.5D, 2D, 30.5D));
        player.moveTo(far.x, far.y, far.z, 0F, 0F);
        helper.assertFalse(menu.clickMenuButton(player, button("body_1")), "离五芒星太远应被拒绝");

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(helper.absolutePos(PENTACLE));
        PlayerPentacleMenu clientMenu = new PlayerPentacleMenu(1, player.getInventory(), buf);
        player.moveTo(helper.absoluteVec(new Vec3(5.5D, 2D, 7.5D)));
        helper.assertFalse(clientMenu.clickMenuButton(player, button("body_1")), "没有方块实体的（客户端）菜单什么都不做");

        helper.assertTrue(data.hasCorruptionEffect("body_1") && Math.abs(total(helper, 0) - 80000D) < EPS, "以上都不动效果、不抽 ESPE");
        helper.assertTrue(menu.clickMenuButton(player, button("body_1")), "站回来以后正常的按钮号能治");
        helper.succeed();
    }

    /** 「创造免费」那段按钮号：生存玩家发了不生效（客户端自报创造没用）；服务端判定的创造模式才免费，而且不看档位、不抽 ESPE */
    @GameTest(template = LARGE)
    public static void pentacleCreativeIsServerSide(GameTestHelper helper) {
        helper.setBlock(PENTACLE, ECPentacle.PLAYER_PENTACLE.get());
        PlayerPentacleBlockEntity be = (PlayerPentacleBlockEntity) helper.getBlockEntity(PENTACLE);
        helper.assertTrue(be.recheckTier() == -1, "没有结构");
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.addCorruptionEffect("mind_5");
        CorruptionEffects.Effect effect = CorruptionEffects.byId("mind_5");
        int free = PlayerPentacleMenu.BUTTON_CURE_FREE + effect.index();
        PlayerPentacleMenu menu = new PlayerPentacleMenu(0, player.getInventory(), be);
        helper.assertFalse(player.isCreative(), "测试玩家是生存模式");
        helper.assertFalse(menu.clickMenuButton(player, free), "生存玩家发「创造免费」的按钮号应被拒绝");
        helper.assertTrue(data.hasCorruptionEffect("mind_5"), "效果还在");

        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(menu.clickMenuButton(player, free), "服务端判定的创造模式可以免费治（不看档位）");
        helper.assertFalse(data.hasCorruptionEffect("mind_5"), "效果应消失");
        data.addCorruptionEffect("mind_5");
        helper.assertFalse(menu.clickMenuButton(player, PlayerPentacleMenu.BUTTON_CURE + effect.index()),
                "创造模式走普通按钮号照样要档位和 ESPE");
        helper.succeed();
    }

    // ================================================================ 稳定器

    /**
     * 稳定器在富集仓外壳上：控制器数到它，仓里 2 万 MRU 的聚集体不给旁边玩家加矩阵损伤，而且控制器自己的 tick 会一直续；
     * 换成强化石后稳定期（40 tick）一过，照常加损伤（2 级、无防护 80）
     */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void balancerStabilizesChamber(GameTestHelper helper) {
        for (int x = SHELL_MIN.getX(); x <= SHELL_MAX.getX(); x++) {
            for (int y = SHELL_MIN.getY(); y <= SHELL_MAX.getY(); y++) {
                for (int z = SHELL_MIN.getZ(); z <= SHELL_MAX.getZ(); z++) {
                    if (x == SHELL_MIN.getX() || x == SHELL_MAX.getX() || y == SHELL_MIN.getY() || y == SHELL_MAX.getY()
                            || z == SHELL_MIN.getZ() || z == SHELL_MAX.getZ()) {
                        helper.setBlock(new BlockPos(x, y, z), ECMaterials.FORTIFIED_STONE.get());
                    }
                }
            }
        }
        helper.setBlock(WEST_WALL, ECPentacle.EC_BALANCER.get());
        helper.setBlock(CONTROLLER, ECEnrichment.EC_CONTROLLER.get());
        helper.assertTrue(ECPentacle.EC_BALANCER.get().defaultBlockState().is(ECEnrichment.CASING), "稳定器应在富集仓外壳标签里");
        EnrichmentControllerBlockEntity controller = (EnrichmentControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "带稳定器的外壳应成形");
        helper.assertTrue(controller.stabilizers() == 1, "应数到 1 个稳定器，实际 " + controller.stabilizers());

        Vec3 center = helper.absoluteVec(new Vec3(5.5D, 2.5D, 5.5D));
        MRUPresenceEntity presence = MRUPresenceEntity.create(helper.getLevel(), center.x, center.y, center.z, 20000, 1F, false, true);
        helper.getLevel().addFreshEntity(presence);
        Player player = helper.makeMockSurvivalPlayer();
        Vec3 stand = helper.absoluteVec(new Vec3(4.5D, 2D, 5.5D));
        player.moveTo(stand.x, stand.y, stand.z, 0F, 0F);
        IPlayerData data = data(player);
        int perCycle = MRUPresenceEntity.matrixDamage(2, 1F);

        helper.assertTrue(controller.stabilizeContents() == 1, "仓里有 1 个聚集体");
        helper.assertTrue(presence.isStabilized(), "被稳定");
        presence.runCycle(List.of(player));
        helper.assertTrue(data.getMatrixDamage() == 0, "稳定期间不加矩阵损伤，实际 " + data.getMatrixDamage());

        helper.startSequence()
                .thenExecuteAfter(EnrichmentControllerBlockEntity.STABILIZE_TICKS + EnrichmentControllerBlockEntity.STABILIZE_INTERVAL + 5, () -> {
                    helper.assertTrue(presence.isStabilized(), "控制器自己的 tick 应一直续稳定期");
                    presence.runCycle(List.of(player));
                    helper.assertTrue(data.getMatrixDamage() == 0, "仍不加矩阵损伤");
                    helper.setBlock(WEST_WALL, ECMaterials.FORTIFIED_STONE.get());
                    helper.assertTrue(controller.checkNow() == StructureResult.MATCH && controller.stabilizers() == 0, "换掉后外壳照样成形、没有稳定器");
                })
                .thenExecuteAfter(EnrichmentControllerBlockEntity.STABILIZE_TICKS + 2, () -> {
                    helper.assertFalse(presence.isStabilized(), "稳定期过了");
                    presence.runCycle(List.of(player));
                    helper.assertTrue(data.getMatrixDamage() == perCycle, "没有稳定器时一轮应加 " + perCycle + "，实际 " + data.getMatrixDamage());
                    presence.discard();
                })
                .thenSucceed();
    }

    // ================================================================ 杂项

    /** 淡青块不透光（原作不透光度 0） */
    @GameTest(template = LARGE)
    public static void paleBlockIsOpaque(GameTestHelper helper) {
        BlockPos pos = new BlockPos(5, 1, 5);
        helper.setBlock(pos, ECPentacle.PALE_BLOCK.get());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = level.getBlockState(abs);
        helper.assertTrue(state.canOcclude(), "淡青块应遮挡");
        helper.assertTrue(state.getLightBlock(level, abs) == 15, "淡青块应完全挡光，实际 " + state.getLightBlock(level, abs));
        helper.assertFalse(state.propagatesSkylightDown(level, abs), "淡青块不透天空光");
        helper.succeed();
    }

    /** 五芒星挖掉掉自己，不抛异常（原作 breakBlock 把方块实体强转 IInventory） */
    @GameTest(template = LARGE)
    public static void pentacleDropsItself(GameTestHelper helper) {
        helper.setBlock(PENTACLE, ECPentacle.PLAYER_PENTACLE.get());
        // GameTestHelper.destroyBlock 是 destroyBlock(pos, false)，不掉落；要测掉落得自己带 true
        helper.getLevel().destroyBlock(helper.absolutePos(PENTACLE), true);
        helper.assertBlockPresent(Blocks.AIR, PENTACLE);
        helper.assertItemEntityPresent(ECPentacle.PLAYER_PENTACLE.get().asItem(), PENTACLE, 2D);
        helper.killAllEntities();
        helper.succeed();
    }

    /**
     * 五芒星挡液体（原作 Material.ROCK；ECPentacle.PLAYER_PENTACLE 的 forceSolidOn，同 WindTests.windRuneBlocksFluids）：1/16 高的碰撞箱太薄，
     * 不加的话按形状算不是 isSolid，会被水冲掉、被岩浆烧掉。两个五芒星各用玻璃围一圈，正上方分别放水源、岩浆源（只能往下流），
     * 等过岩浆的流动间隔（主世界 30 刻）两轮，五芒星都还在、格子里没有液体、没冲出掉落物；canBeReplaced(液体) 也是 false，碰撞箱不变。
     */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void pentacleBlocksFluids(GameTestHelper helper) {
        BlockState state = ECPentacle.PLAYER_PENTACLE.get().defaultBlockState();
        BlockPos waterPentacle = new BlockPos(2, 2, 2);
        BlockPos lavaPentacle = new BlockPos(4, 2, 4);
        helper.assertTrue(state.blocksMotion(), "五芒星应挡液体（blocksMotion）");
        helper.assertTrue(!state.canBeReplaced(Fluids.WATER) && !state.canBeReplaced(Fluids.LAVA), "水桶、发射器倒液体不应能直接替换五芒星");
        double height = state.getCollisionShape(helper.getLevel(), helper.absolutePos(waterPentacle)).max(Direction.Axis.Y);
        helper.assertTrue(Math.abs(height - 1D / 16D) < EPS, "碰撞箱仍是 1/16 高，实际 " + height);

        // 两个 3×3 的小池（石头底、两层玻璃墙），共用 (3, y, 3) 那根角柱
        for (BlockPos pentacle : List.of(waterPentacle, lavaPentacle)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.setBlock(pentacle.offset(dx, -1, dz), Blocks.STONE);
                    if (dx != 0 || dz != 0) {
                        helper.setBlock(pentacle.offset(dx, 0, dz), Blocks.GLASS);
                        helper.setBlock(pentacle.offset(dx, 1, dz), Blocks.GLASS);
                    }
                }
            }
            helper.setBlock(pentacle, ECPentacle.PLAYER_PENTACLE.get());
        }
        helper.setBlock(waterPentacle.above(), Blocks.WATER);
        helper.setBlock(lavaPentacle.above(), Blocks.LAVA);

        helper.runAfterDelay(70, () -> {
            for (BlockPos pentacle : List.of(waterPentacle, lavaPentacle)) {
                helper.assertBlockPresent(ECPentacle.PLAYER_PENTACLE.get(), pentacle);
                helper.assertTrue(helper.getBlockState(pentacle).getFluidState().isEmpty(), pentacle + " 的五芒星格里不应有液体");
            }
            helper.assertItemEntityNotPresent(ECPentacle.PLAYER_PENTACLE.get().asItem(), waterPentacle, 3D);
            // 液体源收掉，别在测试区留岩浆
            helper.setBlock(waterPentacle.above(), Blocks.AIR);
            helper.setBlock(lavaPentacle.above(), Blocks.AIR);
            helper.succeed();
        });
    }

    /** 本模块的三条工作台配方都加载了，产物对得上 */
    @GameTest(template = LARGE)
    public static void pentacleRecipesLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String[][] recipes = {{"player_pentacle", "player_pentacle"}, {"pale_crystal_pillar", "pale_crystal_pillar"}, {"ec_balancer", "ec_balancer"}};
        for (String[] entry : recipes) {
            ResourceLocation id = EssentialCraft5.id("pentacle/" + entry[0]);
            Optional<? extends Recipe<?>> recipe = level.getRecipeManager().byKey(id);
            helper.assertTrue(recipe.isPresent(), "配方 " + id + " 应已加载");
            ItemStack result = recipe.get().getResultItem(level.registryAccess());
            Block expected = entry[1].equals("player_pentacle") ? ECPentacle.PLAYER_PENTACLE.get()
                    : entry[1].equals("pale_crystal_pillar") ? ECPentacle.PALE_CRYSTAL_PILLAR.get() : ECPentacle.EC_BALANCER.get();
            helper.assertTrue(result.is(expected.asItem()), "配方 " + id + " 的产物不对：" + result);
        }
        helper.succeed();
    }

    // ================================================================ 2、3 档（第七批）

    /** 2 档用真虚空柱成形，柱位放淡青柱退回 1 档；3 档用恶魔护板和恶魔柱成形，柱位放虚空柱退回 2 档 */
    @GameTest(template = LARGE)
    public static void pentacleTierTwoAndThreeForm(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = buildTierZero(helper);
        addTierOne(helper);
        addTierTwo(helper);
        helper.assertTrue(be.recheckTier() == 2, "加 8 块虚空石、4 根虚空柱应升到 2 档，实际 " + be.getTier());
        BlockPos voidPillar = at(ECPentacle.PILLAR_OFFSETS.get(2).get(1));
        helper.setBlock(voidPillar, Blocks.AIR);
        helper.setBlock(voidPillar, ECPentacle.PALE_CRYSTAL_PILLAR.get());
        helper.assertTrue(be.recheckTier() == 1, "2 档的柱位放淡青柱不够档，退回 1 档，实际 " + be.getTier());
        helper.setBlock(voidPillar, Blocks.AIR);
        helper.setBlock(voidPillar, ECCrystalPillars.VOID_CRYSTAL_PILLAR.get());
        helper.assertTrue(be.recheckTier() == 2, "换回虚空柱重新是 2 档");

        addTierThree(helper);
        helper.assertTrue(be.recheckTier() == 3, "加 4 块恶魔护板、4 根恶魔柱应升到 3 档，实际 " + be.getTier());
        BlockPos demonicPillar = at(ECPentacle.PILLAR_OFFSETS.get(3).get(0));
        helper.setBlock(demonicPillar, Blocks.AIR);
        helper.setBlock(demonicPillar, ECCrystalPillars.VOID_CRYSTAL_PILLAR.get());
        helper.assertTrue(be.recheckTier() == 2, "3 档的柱位放虚空柱不够档，退回 2 档，实际 " + be.getTier());
        helper.succeed();
    }

    /** 2 档 16 根满柱：有效 57 600；治不了 72 000 的 matrix_12（不抽），治得了 20 000 的 matrix_11，抽 ⌊20000 ÷ 0.36⌋ */
    @GameTest(template = LARGE)
    public static void pentacleTierTwoCuresMatrix(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = buildTierZero(helper);
        addTierOne(helper);
        addTierTwo(helper);
        for (int t = 0; t <= 2; t++) {
            fill(helper, t, FULL);
        }
        helper.assertTrue(be.recheckTier() == 2 && be.effectiveESPE() == 57600, "2 档 16 根满柱有效 ESPE 应为 57600，实际 " + be.effectiveESPE());
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.addCorruptionEffect("matrix_12");
        data.addCorruptionEffect("matrix_11");
        PlayerPentacleMenu menu = new PlayerPentacleMenu(0, player.getInventory(), be);
        helper.assertFalse(menu.clickMenuButton(player, button("matrix_12")), "72000 的矩阵类 2 档攒满也治不了");
        helper.assertTrue(data.hasCorruptionEffect("matrix_12") && Math.abs(total(helper, 2) - 160000D) < EPS, "治不了时效果还在、不抽 ESPE");
        helper.assertTrue(menu.clickMenuButton(player, button("matrix_11")), "20000 的矩阵类 2 档能治");
        helper.assertFalse(data.hasCorruptionEffect("matrix_11"), "治完效果应消失");
        helper.assertTrue(Math.abs(total(helper, 2) - (160000D - PlayerPentacleBlockEntity.rawCost(20000))) < EPS,
                "应抽 ⌊20000 ÷ 0.36⌋ = " + PlayerPentacleBlockEntity.rawCost(20000));
        helper.succeed();
    }

    /** 3 档 20 根满柱：每点 ESPE 按 2 倍算，有效 144 000；治 72 000 的 matrix_12，柱子实际只少 ⌊72000 ÷ 0.36⌋ ÷ 2 = 100 000 */
    @GameTest(template = LARGE)
    public static void pentacleTierThreeDoublesEspe(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = buildTierZero(helper);
        addTierOne(helper);
        addTierTwo(helper);
        addTierThree(helper);
        for (int t = 0; t <= 3; t++) {
            fill(helper, t, FULL);
        }
        helper.assertTrue(be.recheckTier() == 3 && be.effectiveESPE() == 144000, "3 档 20 根满柱有效 ESPE 应为 144000，实际 " + be.effectiveESPE());
        ServerPlayer player = player(helper);
        IPlayerData data = data(player);
        data.addCorruptionEffect("matrix_12");
        PlayerPentacleMenu menu = new PlayerPentacleMenu(0, player.getInventory(), be);
        helper.assertTrue(menu.clickMenuButton(player, button("matrix_12")), "3 档能治 72000 的矩阵类");
        helper.assertFalse(data.hasCorruptionEffect("matrix_12"), "治完效果应消失");
        double drawn = 200000D - total(helper, 3);
        helper.assertTrue(Math.abs(drawn - PlayerPentacleBlockEntity.rawCost(72000) / 2D) < EPS, "柱子应一共少 100000，实际 " + drawn);
        helper.succeed();
    }

    /**
     * 3 档打开界面：手册 1 / 2 / 3 档都打勾，进度页上的「灵魂净化」（main/player_pentacle）也发。
     * 打勾、记档位要等界面真的打开（PlayerContainerEvent.Open）：只调 createMenu（混合端插件拦下打开时就停在这一步）什么都不记。
     */
    @GameTest(template = LARGE)
    public static void pentacleTierAdvancements(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = buildTierZero(helper);
        addTierOne(helper);
        addTierTwo(helper);
        addTierThree(helper);
        ServerPlayer player = player(helper);
        AbstractContainerMenu menu = be.createMenu(0, player.getInventory(), player);
        helper.assertTrue(be.getTier() == 3, "createMenu 当场重判档位，实际 " + be.getTier());
        helper.assertFalse(advancementDone(player, PlayerPentacleBlockEntity.GUIDE_TIER1), "界面还没真打开：不打勾");
        helper.assertTrue(data(player).getPentacleTier() == -1, "界面还没真打开：不记档位");
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, menu));
        helper.assertTrue(advancementDone(player, PlayerPentacleBlockEntity.GUIDE_TIER1), "手册 1 档应打勾");
        helper.assertTrue(advancementDone(player, PlayerPentacleBlockEntity.GUIDE_TIER2), "手册 2 档应打勾");
        helper.assertTrue(advancementDone(player, PlayerPentacleBlockEntity.GUIDE_TIER3), "手册 3 档应打勾");
        helper.assertTrue(advancementDone(player, PlayerPentacleBlockEntity.MAIN_PLAYER_PENTACLE),
                "3 档应发进度「灵魂净化」（父进度 main/demon_trade 要在）");
        helper.assertTrue(data(player).getPentacleTier() == 3,
                "打开 3 档的界面应把玩家「打开过的最高档」记成 3（腐化效果只抽这一档治得了的），实际 " + data(player).getPentacleTier());
        helper.succeed();
    }

    /**
     * 只给自己的、或者自己能动的五芒星记档位（respect_claims）：五芒星那一格是别人的领地时，打开界面照样能用，但档位不升；
     * 主人打开自己的不用探测；记下来也不会升的不探测；闸关着照旧记
     */
    @GameTest(template = LARGE)
    public static void pentacleTierOnlyOwnOrEditable(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = buildTierZero(helper);
        addTierOne(helper);
        BlockPos pentacleAbs = helper.absolutePos(PENTACLE);
        ServerPlayer stranger = player(helper);
        UUID ownerId = UUID.randomUUID();
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper, ownerId, "ec5-pentacle");
        Vec3 stand = helper.absoluteVec(new Vec3(5.5D, 2D, 7.5D));
        owner.moveTo(stand.x, stand.y, stand.z, 0F, 0F);
        be.setOwner(ownerId, "ec5-pentacle");
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyAt(pentacleAbs)) {
            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, true);
            ECProtection.setOnlineOverride(owner);
            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(stranger, be.createMenu(0, stranger.getInventory(), stranger)));
            helper.assertTrue(data(stranger).getPentacleTier() == -1, "别人领地里的五芒星：不记档位，实际 " + data(stranger).getPentacleTier());
            helper.assertTrue(claims.breaksBy(PENTACLE, stranger.getUUID()) == 1, "以打开的人的身份问了一次五芒星那一格");
            helper.assertTrue(advancementDone(stranger, PlayerPentacleBlockEntity.GUIDE_TIER1), "手册打勾不受影响（界面照样能用）");

            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(owner, be.createMenu(0, owner.getInventory(), owner)));
            helper.assertTrue(data(owner).getPentacleTier() == 1, "主人打开自己的：记 1 档，实际 " + data(owner).getPentacleTier());
            helper.assertTrue(claims.breaksBy(PENTACLE, ownerId) == 0, "主人打开自己的不用探测");

            data(stranger).setPentacleTier(3);
            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(stranger, be.createMenu(0, stranger.getInventory(), stranger)));
            helper.assertTrue(claims.breaksBy(PENTACLE, stranger.getUUID()) == 1, "记下来也不会升（已经 3 档）：不再探测");
            data(stranger).setPentacleTier(-1);

            claims.allow(PENTACLE);
            ECProtection.forget(stranger.getUUID());
            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(stranger, be.createMenu(0, stranger.getInventory(), stranger)));
            helper.assertTrue(data(stranger).getPentacleTier() == 1, "能动的（被信任、野外）照样记，实际 " + data(stranger).getPentacleTier());

            ServerPlayer other = player(helper);
            claims.denyAt(pentacleAbs);
            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, false);
            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(other, be.createMenu(0, other.getInventory(), other)));
            helper.assertTrue(data(other).getPentacleTier() == 1, "respect_claims 关着：照旧记，实际 " + data(other).getPentacleTier());
            helper.assertTrue(claims.breaksBy(PENTACLE, other.getUUID()) == 0, "关着时不探测");
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
            ECProtection.clearOnlineOverride(ownerId);
            ECProtection.forget(stranger.getUUID());
        }
        helper.succeed();
    }
}
