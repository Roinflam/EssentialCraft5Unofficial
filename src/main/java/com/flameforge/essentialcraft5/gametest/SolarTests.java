package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.EnderGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.SolarPrismBlockEntity;
import com.flameforge.essentialcraft5.blockentity.SunRayAbsorberBlockEntity;
import com.flameforge.essentialcraft5.item.BalanceSetterItem;
import com.flameforge.essentialcraft5.registry.ECSolar;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 日光与末影模块的 GameTest：太阳棱镜的摆法（成形、缺支架、旁边被堵都会自毁）、光束只给一台吸收器、
 * 落点不是吸收器时给旁边最近的一台、重叠光束不叠加、红石停机、光束伤害只打敌对生物（第十批 A2；动物、掉落物、盔甲架不受影响，玩家看 burn_players）、默认不放火、正午窗口；
 * 末影产能器打中末影人 +500、红石停机、起了名字 / 拴着的末影人不碰（第十批 A7）；平衡值设定器非创造无效。
 * 第三批安全回补（policy §5）：光束不烧驯服、拴绳、起了名字的生物（P-7），审查 RB-3 补上村民、玩家造的铁傀儡和雪傀儡、认了主人的悦灵；
 * 两台末影产能器挨着时同一只末影人每刻最多被传送一次（P2）。
 * <p>
 * 大多数用模板 empty_large（11×8×11 空气，y=0 那层下面是超平坦地面）：棱镜放在 (5, 6, 5)，吸收器 / 落点在 (5, 1, 5)。
 * 棱镜头顶 (5, 7, 5) 放一块石头挡住天空，免得测试世界正好是正午时它自己随机出光束；光束一律由测试直接调 fireBeam 打出。
 * 末影人会被打得满世界瞬移，末影产能器的测试单独一批（batch），结束时不管在哪都把末影人清掉。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class SolarTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final String ENDER_BATCH = "ec5_solar_ender";
    /** 叠放测试数传送次数，隔壁测试里被打得乱瞬移的末影人会混进来，单独一批 */
    private static final String ENDER_STACK_BATCH = "ec5_solar_ender_stack";
    /** 起了名字的末影人那条看「没产 MRU」，隔壁测试里被打得乱瞬移的末影人会混进来，单独一批 */
    private static final String ENDER_NAMED_BATCH = "ec5_solar_ender_named";

    private static final BlockPos PRISM = new BlockPos(5, 6, 5);
    private static final BlockPos BELOW = new BlockPos(5, 1, 5);
    private static final int BEAM_MRU = 500 * SolarPrismBlockEntity.BEAM_DURATION;

    private SolarTests() {
    }

    /**
     * 搭棱镜：同层四个方向隔一格放石头（missing 那一面不放），头顶放一块挡天空，最后放棱镜。
     *
     * @param missing 故意缺的支架方向；null 表示四个都放
     */
    private static SolarPrismBlockEntity buildPrism(GameTestHelper helper, @Nullable Direction missing) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != missing) {
                helper.setBlock(PRISM.relative(direction, 2), Blocks.STONE);
            }
        }
        helper.setBlock(PRISM.above(), Blocks.STONE);
        helper.setBlock(PRISM, ECSolar.SOLAR_PRISM.get());
        return (SolarPrismBlockEntity) helper.getBlockEntity(PRISM);
    }

    private static SunRayAbsorberBlockEntity absorber(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECSolar.SUN_RAY_ABSORBER.get());
        return (SunRayAbsorberBlockEntity) helper.getBlockEntity(pos);
    }

    // ---------------- 摆法 ----------------

    /** 结构登记在 essentialcraft5:solar_prism：4 个支架 + 8 格空气 + 棱镜本身 */
    @GameTest(template = EMPTY)
    public static void prismStructureRegistered(GameTestHelper helper) {
        StructurePattern pattern = StructurePatterns.get(EssentialCraft5.id("solar_prism"));
        helper.assertTrue(pattern == ECSolar.PRISM_STRUCTURE, "essentialcraft5:solar_prism 应登记为 ECSolar.PRISM_STRUCTURE");
        helper.assertTrue(pattern.requiredCount() == 13, "摆法应有 13 格要求，实际 " + pattern.requiredCount());
        helper.succeed();
    }

    /** 摆法正确时棱镜一直在（跨过两次 20 刻的复查） */
    @GameTest(template = EMPTY_LARGE)
    public static void prismStaysWhenFormed(GameTestHelper helper) {
        SolarPrismBlockEntity prism = buildPrism(helper, null);
        helper.runAtTickTime(45, () -> {
            helper.assertBlockPresent(ECSolar.SOLAR_PRISM.get(), PRISM);
            helper.assertTrue(prism.structure().formed(), "摆法应判定为成形");
            helper.succeed();
        });
    }

    /** 少一个支架：第一次 tick 就自毁，掉出棱镜（D5） */
    @GameTest(template = EMPTY_LARGE)
    public static void prismBreaksWithoutSupport(GameTestHelper helper) {
        buildPrism(helper, Direction.WEST);
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(ECSolar.SOLAR_PRISM.get(), PRISM);
            helper.assertItemEntityPresent(ECSolar.SOLAR_PRISM.get().asItem(), PRISM, 3D);
        });
    }

    /** 成形之后在斜角放一块方块：20 刻内的复查发现后自毁 */
    @GameTest(template = EMPTY_LARGE)
    public static void prismBreaksWhenCrowded(GameTestHelper helper) {
        buildPrism(helper, null);
        helper.runAtTickTime(5, () -> {
            helper.assertBlockPresent(ECSolar.SOLAR_PRISM.get(), PRISM);
            helper.setBlock(PRISM.offset(1, 0, 1), Blocks.STONE);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertBlockNotPresent(ECSolar.SOLAR_PRISM.get(), PRISM);
            // 掉出来的棱镜这时已经往下落了一段（正下方是空的）
            helper.assertItemEntityPresent(ECSolar.SOLAR_PRISM.get().asItem(), PRISM, 6D);
            helper.succeed();
        });
    }

    // ---------------- 光束与吸收器 ----------------

    /** 落点就是吸收器：一道光束给它 20 刻 × 500 = 1 万 MRU；旁边另一台一点都不给（原作能喂到它） */
    @GameTest(template = EMPTY_LARGE)
    public static void beamFeedsOnlyOneAbsorber(GameTestHelper helper) {
        SolarPrismBlockEntity prism = buildPrism(helper, null);
        SunRayAbsorberBlockEntity target = absorber(helper, BELOW);
        SunRayAbsorberBlockEntity neighbour = absorber(helper, BELOW.east());
        helper.runAtTickTime(2, () -> {
            BlockPos landing = prism.fireBeam(helper.getLevel());
            helper.assertTrue(helper.absolutePos(BELOW).equals(landing), "落点应是正下方的吸收器，实际 " + landing);
            helper.assertTrue(target.getBlockPos().equals(prism.lastTarget()), "光束应给落点上的吸收器");
            helper.assertTrue(target.isLit(), "吸收器应被照亮");
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(target.getMRUStorage().getMRU() == BEAM_MRU, "一道光束应产出 1 万 MRU，实际 " + target.getMRUStorage().getMRU());
            helper.assertTrue(neighbour.getMRUStorage().getMRU() == 0, "旁边那台不应分到，实际 " + neighbour.getMRUStorage().getMRU());
            helper.assertTrue(target.getMRUStorage().getBalance() == 2F, "吸收器产出的应是平衡值 2.0 的混沌 MRU");
            helper.assertFalse(target.isLit(), "20 刻后应熄灭");
            helper.succeed();
        });
    }

    /** 落点不是吸收器：给落点旁边一格内最近的那台；更远的、斜上方的都不给 */
    @GameTest(template = EMPTY_LARGE)
    public static void beamFallsBackToNearestAbsorber(GameTestHelper helper) {
        SolarPrismBlockEntity prism = buildPrism(helper, null);
        helper.setBlock(BELOW, Blocks.STONE);
        SunRayAbsorberBlockEntity nearest = absorber(helper, BELOW.east());
        SunRayAbsorberBlockEntity diagonal = absorber(helper, BELOW.offset(-1, 1, -1));
        SunRayAbsorberBlockEntity tooFar = absorber(helper, BELOW.south(2));
        helper.runAtTickTime(2, () -> {
            BlockPos landing = prism.fireBeam(helper.getLevel());
            helper.assertTrue(helper.absolutePos(BELOW).equals(landing), "落点应是正下方的石头，实际 " + landing);
            helper.assertTrue(nearest.getBlockPos().equals(prism.lastTarget()), "应给最近的那台，实际 " + prism.lastTarget());
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(nearest.getMRUStorage().getMRU() == BEAM_MRU, "最近那台应得 1 万 MRU，实际 " + nearest.getMRUStorage().getMRU());
            helper.assertTrue(diagonal.getMRUStorage().getMRU() == 0, "斜上方那台不应分到");
            helper.assertTrue(tooFar.getMRUStorage().getMRU() == 0, "两格外那台不应分到");
            helper.succeed();
        });
    }

    /** 光束重叠不叠加：第二道在第一道还剩 10 刻时打下来，一共亮 30 刻 = 1.5 万 MRU */
    @GameTest(template = EMPTY_LARGE)
    public static void overlappingBeamsDoNotStack(GameTestHelper helper) {
        SolarPrismBlockEntity prism = buildPrism(helper, null);
        SunRayAbsorberBlockEntity target = absorber(helper, BELOW);
        helper.runAtTickTime(2, () -> prism.fireBeam(helper.getLevel()));
        helper.runAtTickTime(12, () -> prism.fireBeam(helper.getLevel()));
        helper.runAtTickTime(45, () -> {
            int expected = 500 * (10 + SolarPrismBlockEntity.BEAM_DURATION);
            helper.assertTrue(target.getMRUStorage().getMRU() == expected, "应为 " + expected + "，实际 " + target.getMRUStorage().getMRU());
            helper.succeed();
        });
    }

    /** 棱镜正下方紧挨着的格子不是空气：一道光束都打不出来（同原作） */
    @GameTest(template = EMPTY_LARGE)
    public static void noBeamWhenBlockedBelow(GameTestHelper helper) {
        SolarPrismBlockEntity prism = buildPrism(helper, null);
        SunRayAbsorberBlockEntity target = absorber(helper, PRISM.below());
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(prism.fireBeam(helper.getLevel()) == null, "正下方有方块时不应出光束");
            helper.assertFalse(target.isLit(), "吸收器不应被照亮");
            helper.succeed();
        });
    }

    /** 有红石信号时吸收器被照着也不产出 */
    @GameTest(template = EMPTY_LARGE)
    public static void absorberStopsWithRedstone(GameTestHelper helper) {
        SolarPrismBlockEntity prism = buildPrism(helper, null);
        SunRayAbsorberBlockEntity target = absorber(helper, BELOW);
        helper.setBlock(BELOW.west(), Blocks.REDSTONE_BLOCK);
        helper.runAtTickTime(2, () -> prism.fireBeam(helper.getLevel()));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(target.getMRUStorage().getMRU() == 0, "有红石信号时不应产出，实际 " + target.getMRUStorage().getMRU());
            helper.succeed();
        });
    }

    /**
     * 光束伤害落点附近的敌对生物并点燃它们；牛（动物）不烧（第十批 A2：只烧敌对生物）；掉落物不受影响（原作连掉落物一起烧）；
     * 盔甲架不受影响（原作几刻就把它烧毁、盔甲架物品不掉）；不放火（D4）。用蜘蛛当敌对生物（白天不会自己着火）
     */
    @GameTest(template = EMPTY_LARGE)
    public static void beamHurtsCreaturesOnly(GameTestHelper helper) {
        SolarPrismBlockEntity prism = buildPrism(helper, null);
        helper.setBlock(BELOW, Blocks.STONE);
        Spider spider = helper.spawnWithNoFreeWill(EntityType.SPIDER, new BlockPos(8, 1, 5));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(5, 1, 2));
        ItemEntity stick = helper.spawnItem(Items.STICK, new BlockPos(5, 2, 8));
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 5));
        helper.runAtTickTime(2, () -> prism.fireBeam(helper.getLevel()));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(spider.getHealth() < spider.getMaxHealth(), "蜘蛛应受伤");
            helper.assertTrue(spider.getRemainingFireTicks() > 0, "蜘蛛应着火");
            helper.assertTrue(cow.getHealth() == cow.getMaxHealth() && cow.getRemainingFireTicks() <= 0, "牛不应受伤、着火");
            helper.assertFalse(stand.getRemainingFireTicks() > 0, "盔甲架不应着火");
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(stick.isAlive(), "掉落物不应被光束烧掉");
            helper.assertTrue(stand.isAlive() && stand.getHealth() == stand.getMaxHealth(),
                    "盔甲架不应受损，实际生命 " + stand.getHealth() + "，" + (stand.isAlive() ? "还在" : "已毁"));
            for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 10, 7, 10)) {
                helper.assertBlockNotPresent(Blocks.FIRE, pos);
            }
            helper.succeed();
        });
    }

    /**
     * 第三批安全回补（P-7）：「谁都不许碰」的判定（{@code sparedFromBeam}）：起了名字的、被驯服的、拴着绳的、驯服的马（直接查判定）。
     * 审查 RB-3：没起名字的村民、玩家造的铁傀儡也算；村庄自己刷的铁傀儡不算，雪傀儡、认了主人的悦灵算（后三种直接查判定）。
     * 第十批 A2 起光束只烧敌对生物：野生的牛也不烧了；野生的蜘蛛照烧、起了名字的蜘蛛不烧
     */
    @GameTest(template = EMPTY_LARGE)
    public static void beamSparesOwnedCreatures(GameTestHelper helper) {
        SolarPrismBlockEntity prism = buildPrism(helper, null);
        helper.setBlock(BELOW, Blocks.STONE);
        Cow wild = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(8, 1, 5));
        Spider hostile = helper.spawnWithNoFreeWill(EntityType.SPIDER, new BlockPos(8, 1, 2));
        Spider namedSpider = helper.spawnWithNoFreeWill(EntityType.SPIDER, new BlockPos(2, 1, 8));
        namedSpider.setCustomName(Component.literal("Itsy"));
        Cow named = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 1, 5));
        named.setCustomName(Component.literal("Daisy"));
        Wolf tamed = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(5, 1, 8));
        tamed.setTame(true);
        tamed.setOwnerUUID(UUID.randomUUID());
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        IronGolem builtGolem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(8, 1, 8));
        builtGolem.setPlayerCreated(true);

        // 拴绳、驯服的马：不进世界，直接查判定
        Cow leashed = EntityType.COW.create(helper.getLevel());
        Horse horse = EntityType.HORSE.create(helper.getLevel());
        helper.assertTrue(leashed != null && horse != null, "造不出牛 / 马");
        helper.setBlock(new BlockPos(5, 1, 2), Blocks.OAK_FENCE);
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 2)));
        helper.assertFalse(SolarPrismBlockEntity.sparedFromBeam(leashed), "没拴的牛应照烧");
        leashed.setLeashedTo(knot, false);
        helper.assertTrue(SolarPrismBlockEntity.sparedFromBeam(leashed), "拴着绳的牛不应烧");
        helper.assertFalse(SolarPrismBlockEntity.sparedFromBeam(horse), "野马应照烧");
        horse.setTamed(true);
        helper.assertTrue(SolarPrismBlockEntity.sparedFromBeam(horse), "驯服的马不应烧");
        helper.assertTrue(SolarPrismBlockEntity.sparedFromBeam(named) && SolarPrismBlockEntity.sparedFromBeam(tamed), "命名的、驯服的不应烧");
        helper.assertFalse(SolarPrismBlockEntity.sparedFromBeam(wild), "野生的牛应照烧");

        // RB-3：村民、玩家造的傀儡、认了主人的悦灵
        helper.assertTrue(SolarPrismBlockEntity.sparedFromBeam(villager), "没起名字的村民也不应烧");
        helper.assertTrue(SolarPrismBlockEntity.sparedFromBeam(builtGolem), "玩家造的铁傀儡不应烧");
        IronGolem villageGolem = EntityType.IRON_GOLEM.create(helper.getLevel());
        SnowGolem snowGolem = EntityType.SNOW_GOLEM.create(helper.getLevel());
        Allay allay = EntityType.ALLAY.create(helper.getLevel());
        helper.assertTrue(villageGolem != null && snowGolem != null && allay != null, "造不出铁傀儡 / 雪傀儡 / 悦灵");
        helper.assertFalse(SolarPrismBlockEntity.sparedFromBeam(villageGolem), "村庄自己刷的铁傀儡应照烧");
        helper.assertTrue(SolarPrismBlockEntity.sparedFromBeam(snowGolem), "雪傀儡不应烧");
        helper.assertFalse(SolarPrismBlockEntity.sparedFromBeam(allay), "没认主人的悦灵应照烧");
        allay.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, UUID.randomUUID());
        helper.assertTrue(SolarPrismBlockEntity.sparedFromBeam(allay), "认了主人的悦灵不应烧");

        helper.assertFalse(SolarPrismBlockEntity.mayBurn(wild), "野生的牛不是敌对生物，不烧");
        helper.assertTrue(SolarPrismBlockEntity.mayBurn(hostile) && !SolarPrismBlockEntity.mayBurn(namedSpider), "野生的蜘蛛烧、起了名字的不烧");

        helper.runAtTickTime(2, () -> prism.fireBeam(helper.getLevel()));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(wild.getHealth() == wild.getMaxHealth() && wild.getRemainingFireTicks() <= 0, "野生的牛不应受伤、着火（只烧敌对生物）");
            helper.assertTrue(hostile.getHealth() < hostile.getMaxHealth() && hostile.getRemainingFireTicks() > 0, "野生的蜘蛛应受伤、着火");
            helper.assertTrue(namedSpider.getHealth() == namedSpider.getMaxHealth() && namedSpider.getRemainingFireTicks() <= 0,
                    "起了名字的蜘蛛不应受伤、着火");
            helper.assertTrue(named.getHealth() == named.getMaxHealth() && named.getRemainingFireTicks() <= 0, "起了名字的牛不应受伤、着火");
            helper.assertTrue(tamed.getHealth() == tamed.getMaxHealth() && tamed.getRemainingFireTicks() <= 0, "驯服的狼不应受伤、着火");
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth() && villager.getRemainingFireTicks() <= 0, "村民不应受伤、着火");
            helper.assertTrue(builtGolem.getHealth() == builtGolem.getMaxHealth() && builtGolem.getRemainingFireTicks() <= 0,
                    "玩家造的铁傀儡不应受伤、着火");
            knot.discard();
            helper.succeed();
        });
    }

    /**
     * 4a 审查 FW-7：装了鞍的骆驼算「谁都不许碰」（骆驼不用驯服就能骑、从不记主人，只能靠鞍看出有人在用），没装鞍的不算。
     * 第十批 A2 起光束只烧敌对生物，两只骆驼都不烧。两只都在落点 6 格内
     */
    @GameTest(template = EMPTY_LARGE)
    public static void beamSparesSaddledCamel(GameTestHelper helper) {
        SolarPrismBlockEntity prism = buildPrism(helper, null);
        helper.setBlock(BELOW, Blocks.STONE);
        Camel saddled = helper.spawnWithNoFreeWill(EntityType.CAMEL, new BlockPos(8, 1, 3));
        saddled.equipSaddle(null);
        Camel wild = helper.spawnWithNoFreeWill(EntityType.CAMEL, new BlockPos(2, 1, 7));
        helper.assertTrue(saddled.isSaddled() && !wild.isSaddled(), "骆驼的鞍没装上");
        helper.assertTrue(SolarPrismBlockEntity.sparedFromBeam(saddled), "装了鞍的骆驼不应烧");
        helper.assertFalse(SolarPrismBlockEntity.sparedFromBeam(wild), "没装鞍的骆驼应照烧");
        helper.runAtTickTime(2, () -> prism.fireBeam(helper.getLevel()));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(saddled.getHealth() == saddled.getMaxHealth() && saddled.getRemainingFireTicks() <= 0,
                    "装了鞍的骆驼不应受伤、着火，实际生命 " + saddled.getHealth());
            helper.assertTrue(wild.getHealth() == wild.getMaxHealth() && wild.getRemainingFireTicks() <= 0, "没装鞍的骆驼也不是敌对生物，不应受伤、着火");
            helper.succeed();
        });
    }

    /** 正午窗口：一天中的时间在 [5000, 7000]（含两端），按 24000 取模 */
    @GameTest(template = EMPTY)
    public static void solarWindow(GameTestHelper helper) {
        helper.assertTrue(SolarPrismBlockEntity.isSolarWindow(6000L), "6000 是正午");
        helper.assertTrue(SolarPrismBlockEntity.isSolarWindow(5000L) && SolarPrismBlockEntity.isSolarWindow(7000L), "窗口含两端");
        helper.assertFalse(SolarPrismBlockEntity.isSolarWindow(4999L) || SolarPrismBlockEntity.isSolarWindow(7001L), "窗口外不出光束");
        helper.assertTrue(SolarPrismBlockEntity.isSolarWindow(24000L * 3 + 6500L), "按 24000 取模");
        helper.assertFalse(SolarPrismBlockEntity.isSolarWindow(18000L), "午夜不出光束");
        helper.succeed();
    }

    /**
     * 第十批 A2：光束烧谁（判定本身）——敌对生物烧，玩家、动物、村民不烧；配置 burn_players 打开后生存玩家也烧，
     * 创造模式的玩家照旧不烧（测试覆盖值，不改真配置；同一段同步代码里设、测、还原）
     */
    @GameTest(template = EMPTY)
    public static void prismBurnRules(GameTestHelper helper) {
        Spider spider = EntityType.SPIDER.create(helper.getLevel());
        Cow cow = EntityType.COW.create(helper.getLevel());
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(spider != null && cow != null && villager != null, "造不出生物");
        Player survival = helper.makeMockSurvivalPlayer();
        Player creative = helper.makeMockPlayer();
        creative.getAbilities().invulnerable = true;
        helper.assertTrue(SolarPrismBlockEntity.mayBurn(spider), "敌对生物应烧");
        helper.assertFalse(SolarPrismBlockEntity.mayBurn(cow) || SolarPrismBlockEntity.mayBurn(villager), "动物、村民不烧");
        helper.assertFalse(SolarPrismBlockEntity.Config.burnPlayers(), "burn_players 默认关");
        helper.assertFalse(SolarPrismBlockEntity.mayBurn(survival), "默认不烧玩家");
        SolarPrismBlockEntity.Config.setBurnPlayersForTest(true);
        try {
            helper.assertTrue(SolarPrismBlockEntity.mayBurn(survival), "打开 burn_players 后生存玩家也烧");
            helper.assertFalse(SolarPrismBlockEntity.mayBurn(creative), "创造模式的玩家照旧不烧");
            helper.assertFalse(SolarPrismBlockEntity.mayBurn(cow), "打开 burn_players 动物也不烧");
        } finally {
            SolarPrismBlockEntity.Config.setBurnPlayersForTest(null);
        }
        helper.succeed();
    }

    // ---------------- 末影产能器 ----------------

    /**
     * 第十批 A7：起了名字、拴着绳的末影人不拉也不打（判定 {@code handles}）；在拉取范围里待 10 刻，起了名字的那只既没被拉到头顶、也没挨打，
     * 产能器不产 MRU。末影人设成无敌（不会被别的东西打得瞬移），只看位置
     */
    @GameTest(template = EMPTY_LARGE, batch = ENDER_NAMED_BATCH)
    public static void enderGeneratorSkipsNamedEndermen(GameTestHelper helper) {
        helper.setBlock(BELOW, ECSolar.ENDER_GENERATOR.get());
        EnderGeneratorBlockEntity generator = (EnderGeneratorBlockEntity) helper.getBlockEntity(BELOW);
        EnderMan named = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(5, 1, 8));
        named.setCustomName(Component.literal("Slender"));
        named.setInvulnerable(true);
        EnderMan plain = EntityType.ENDERMAN.create(helper.getLevel());
        EnderMan leashed = EntityType.ENDERMAN.create(helper.getLevel());
        helper.assertTrue(plain != null && leashed != null, "造不出末影人");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.OAK_FENCE);
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        leashed.setLeashedTo(knot, false);
        helper.assertTrue(generator.handles(plain), "普通末影人照拉照打");
        helper.assertFalse(generator.handles(named) || generator.handles(leashed), "起了名字、拴着绳的不碰");
        Vec3 before = named.position();
        helper.runAtTickTime(10, () -> {
            try {
                // 只比水平位置：刚生成的末影人这几刻里可能落到地面上（removeFreeWill 不关重力），被拉走的话会到产能器头顶（水平差 3 格）
                Vec3 now = named.position();
                double moved = Math.abs(now.x - before.x) + Math.abs(now.z - before.z);
                helper.assertTrue(moved < 1.0E-3D, "起了名字的末影人不应被拉走，生成时 " + before + "，实际 " + now);
                helper.assertTrue(generator.getMRUStorage().getMRU() == 0, "没打它就不产 MRU，实际 " + generator.getMRUStorage().getMRU());
            } finally {
                named.discard();
                knot.discard();
            }
            helper.succeed();
        });
    }

    /** 3 格外的末影人被拉到头顶打一下：+500 MRU（产量是 500 的整数倍） */
    @GameTest(template = EMPTY_LARGE, batch = ENDER_BATCH)
    public static void enderGeneratorHitsEnderman(GameTestHelper helper) {
        helper.setBlock(BELOW, ECSolar.ENDER_GENERATOR.get());
        EnderGeneratorBlockEntity generator = (EnderGeneratorBlockEntity) helper.getBlockEntity(BELOW);
        EnderMan enderman = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(5, 1, 8));
        helper.runAtTickTime(10, () -> {
            try {
                int mru = generator.getMRUStorage().getMRU();
                helper.assertTrue(mru >= 500 && mru % 500 == 0, "应至少打中一次（+500），实际 " + mru);
            } finally {
                enderman.discard();
            }
            helper.succeed();
        });
    }

    /** 有红石信号时不拉也不打 */
    @GameTest(template = EMPTY_LARGE, batch = ENDER_BATCH)
    public static void enderGeneratorStopsWithRedstone(GameTestHelper helper) {
        helper.setBlock(BELOW, ECSolar.ENDER_GENERATOR.get());
        helper.setBlock(BELOW.west(), Blocks.REDSTONE_BLOCK);
        EnderGeneratorBlockEntity generator = (EnderGeneratorBlockEntity) helper.getBlockEntity(BELOW);
        EnderMan enderman = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(5, 1, 8));
        helper.runAtTickTime(10, () -> {
            try {
                helper.assertTrue(generator.getMRUStorage().getMRU() == 0, "有红石信号时不应产出");
                Vec3 top = Vec3.atBottomCenterOf(generator.getBlockPos().above());
                helper.assertTrue(enderman.position().distanceToSqr(top) > 1D, "有红石信号时不应把末影人拉过来");
                helper.assertTrue(enderman.getHealth() == enderman.getMaxHealth(), "有红石信号时不应打末影人");
            } finally {
                enderman.discard();
            }
            helper.succeed();
        });
    }

    /**
     * 第三批安全回补（P2）：两台末影产能器挨着（相距 4 格，都在对方的 8 格拉取范围里）时，同一只末影人每刻最多被传送一次，
     * 而且会留在先认领它的那台头顶，不再每刻被两台来回拉（原作每刻两次）。末影人设成无敌，免得挨打后随机瞬移打乱计数。
     * 另外直接验证认领表：同一刻第二次认领失败，下一刻又能认领。
     */
    @GameTest(template = EMPTY_LARGE, batch = ENDER_STACK_BATCH, timeoutTicks = 100)
    public static void enderGeneratorsDoNotFightOverEnderman(GameTestHelper helper) {
        BlockPos west = new BlockPos(3, 1, 5);
        BlockPos east = new BlockPos(7, 1, 5);
        helper.setBlock(west, ECSolar.ENDER_GENERATOR.get());
        helper.setBlock(east, ECSolar.ENDER_GENERATOR.get());
        EnderGeneratorBlockEntity a = (EnderGeneratorBlockEntity) helper.getBlockEntity(west);
        EnderGeneratorBlockEntity b = (EnderGeneratorBlockEntity) helper.getBlockEntity(east);
        EnderMan enderman = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(5, 1, 8));
        enderman.setInvulnerable(true);

        EnderMan probe = EntityType.ENDERMAN.create(helper.getLevel());
        helper.assertTrue(probe != null, "造不出末影人");
        long t = helper.getLevel().getGameTime() + 1000L;
        helper.assertTrue(EnderGeneratorBlockEntity.claim(probe, t), "这一刻第一次认领应成功");
        helper.assertFalse(EnderGeneratorBlockEntity.claim(probe, t), "同一刻第二次认领应失败");
        helper.assertTrue(EnderGeneratorBlockEntity.claim(probe, t + 1), "下一刻应又能认领");

        helper.runAtTickTime(40, () -> {
            try {
                int teleports = a.getTeleportCount() + b.getTeleportCount();
                helper.assertTrue(teleports >= 1, "末影人应被拉过来");
                helper.assertTrue(teleports <= 2, "40 刻里两台一共应只传送一两次（原作每刻两次），实际 " + teleports);
                Vec3 topA = Vec3.atBottomCenterOf(a.getBlockPos().above());
                Vec3 topB = Vec3.atBottomCenterOf(b.getBlockPos().above());
                helper.assertTrue(enderman.position().distanceToSqr(topA) < 1.0E-3D || enderman.position().distanceToSqr(topB) < 1.0E-3D,
                        "末影人应站在其中一台头顶，实际 " + enderman.position());
            } finally {
                enderman.discard();
            }
            helper.succeed();
        });
    }

    // ---------------- 平衡值设定器 ----------------

    /** 生存模式（非管理员）潜行右键无效；创造模式设成模式值；右键切模式 2 → 0 */
    @GameTest(template = EMPTY)
    public static void balanceSetterNeedsCreative(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECSolar.ENDER_GENERATOR.get());
        EnderGeneratorBlockEntity generator = (EnderGeneratorBlockEntity) helper.getBlockEntity(pos);
        helper.runAtTickTime(2, () -> {
            float rolled = generator.getMRUStorage().getBalance();
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);

            Player survival = helper.makeMockSurvivalPlayer();
            ItemStack setter = new ItemStack(ECSolar.BALANCE_SETTER.get());
            BalanceSetterItem.setMode(setter, 2);
            survival.setItemInHand(InteractionHand.MAIN_HAND, setter);
            survival.setShiftKeyDown(true);
            BalanceSetterItem item = ECSolar.BALANCE_SETTER.get();
            InteractionResult denied = item.useOn(new UseOnContext(survival, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(denied == InteractionResult.FAIL, "生存模式应被拒绝，实际 " + denied);
            helper.assertTrue(generator.getMRUStorage().getBalance() == rolled, "生存模式不应改平衡值");

            Player creative = helper.makeMockPlayer();
            creative.setItemInHand(InteractionHand.MAIN_HAND, setter);
            creative.setShiftKeyDown(true);
            InteractionResult applied = item.useOn(new UseOnContext(creative, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(applied.consumesAction(), "创造模式应生效，实际 " + applied);
            helper.assertTrue(generator.getMRUStorage().getBalance() == 2F, "平衡值应设成 2，实际 " + generator.getMRUStorage().getBalance());

            creative.setShiftKeyDown(false);
            item.use(helper.getLevel(), creative, InteractionHand.MAIN_HAND);
            helper.assertTrue(BalanceSetterItem.mode(setter) == 0, "右键应从模式 2 切到 0，实际 " + BalanceSetterItem.mode(setter));
            helper.succeed();
        });
    }
}
