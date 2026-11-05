package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.AnimalSeparatorBlockEntity;
import com.flameforge.essentialcraft5.menu.AnimalSeparatorMenu;
import com.flameforge.essentialcraft5.menu.MachineData;
import com.flameforge.essentialcraft5.registry.ECSeparators;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 动物分离器（ECSeparators，batch4.md §3.2）的 GameTest。
 * <p>
 * 站位（模板 {@code empty_large}，11×8×11）：机器在 (1, 1, 5)，落点是它顶面正中 (1.5, 2.0, 5.5)；
 * 默认忽略半径 5 的范围是 x ∈ [-4, 7]，所以要被传的动物放在 x ≥ 8，「身边不动」的放在 (4, 1, 5)。
 * 查动物的范围用 {@link AnimalSeparatorBlockEntity#limitSearchForTest} 裁到本测试结构里：默认 24 格会伸进旁边并行的测试，把别人的动物传过来。
 * <p>
 * 主人 = {@link ProtectionFrameworkTests#realPlayer(GameTestHelper, UUID, String)}（每个测试自己的随机 UUID）+ {@code ECProtection.setOnlineOverride}，
 * finally 里 {@code clearOnlineOverride}；{@code ECProtection.Config.setRequireOwnerOnlineForTest} 只在同一段同步代码里设、测、清。
 * 大多数测试直接调 {@code onRedstonePulse}（同步，动物来不及动）；{@link #separatorsCapsAt16AndContinues} 走真的红石（上升沿 → 计划刻 → 续做）。
 * 生成的动物都关了 AI，测完 {@code discard}（不 kill：kill 会掉肉和皮，留在地上给别的测试捡）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class SeparatorsTests {

    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos MACHINE = new BlockPos(1, 1, 5);
    private static final BlockPos TOP = MACHINE.above();
    private static final Vec3 DEST = new Vec3(1.5D, 2.0D, 5.5D);

    private SeparatorsTests() {
    }

    // ------------------------------------------------------------------ 工具

    /** 放一台分离器：主人（可为 null = 没有主人）、MRU、查找范围裁到本测试结构 */
    private static AnimalSeparatorBlockEntity place(GameTestHelper helper, boolean children, ServerPlayer owner, int mru) {
        helper.setBlock(MACHINE, (children ? ECSeparators.CHILD_SEPARATOR : ECSeparators.ADULT_SEPARATOR).get().defaultBlockState());
        if (!(helper.getBlockEntity(MACHINE) instanceof AnimalSeparatorBlockEntity be)) {
            throw new IllegalStateException("分离器没有方块实体");
        }
        if (owner != null) {
            be.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        }
        be.getMRUStorage().setMRU(mru);
        be.limitSearchForTest(bounds(helper));
        return be;
    }

    /** 新的真玩家当主人并设成在线（调用方 finally 里 clearOnlineOverride） */
    private static ServerPlayer onlineOwner(GameTestHelper helper) {
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-sep");
        ECProtection.setOnlineOverride(owner);
        return owner;
    }

    /** 测试结构的范围（绝对坐标，整格） */
    private static AABB bounds(GameTestHelper helper) {
        return AABB.of(BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(10, 7, 10))));
    }

    private static boolean pulse(GameTestHelper helper, AnimalSeparatorBlockEntity be) {
        return be.onRedstonePulse(helper.getLevel(), be.getBlockState());
    }

    /** 生成一只关了 AI 的动物；baby = 幼年 */
    private static <T extends Animal> T animal(GameTestHelper helper, EntityType<T> type, BlockPos relative, boolean baby) {
        T animal = helper.spawn(type, relative);
        animal.setNoAi(true);
        animal.setAge(baby ? -24000 : 0);
        return animal;
    }

    private static boolean onTop(GameTestHelper helper, Entity entity) {
        return entity.position().distanceToSqr(helper.absoluteVec(DEST)) < 1.0E-4D;
    }

    private static int mru(AnimalSeparatorBlockEntity be) {
        return be.getMRUStorage().getMRU();
    }

    /** 测完清掉本结构里的实体（discard：不掉落） */
    private static void cleanup(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(Entity.class, bounds(helper).inflate(1D), e -> !(e instanceof Player)).forEach(Entity::discard);
    }

    // ------------------------------------------------------------------ 基本功能

    /** 没有任何领地（纯 Forge、单人的常态）：幼年牛传到顶上、扣 100；成年牛不动；传完这次脉冲就结束（不续做） */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsMovesBabyWithoutClaims(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        try {
            AnimalSeparatorBlockEntity be = place(helper, true, owner, 1000);
            Cow baby = animal(helper, EntityType.COW, new BlockPos(8, 1, 5), true);
            Cow adult = animal(helper, EntityType.COW, new BlockPos(8, 1, 3), false);
            boolean more = pulse(helper, be);
            helper.assertFalse(more, "一只动物、一个落点，一刻内就应做完");
            helper.assertTrue(onTop(helper, baby), "幼年牛应被传到机器顶上");
            helper.assertFalse(onTop(helper, adult), "幼年分离器不应传成年牛");
            helper.assertTrue(mru(be) == 900, "应扣 100 MRU，实际剩 " + mru(be));
            helper.assertTrue(baby.fallDistance == 0F, "传送后应清掉摔落距离");
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    /** 成年分离器只传成年的 */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsAdultSkipsBabies(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        try {
            AnimalSeparatorBlockEntity be = place(helper, false, owner, 1000);
            Cow baby = animal(helper, EntityType.COW, new BlockPos(8, 1, 5), true);
            Cow adult = animal(helper, EntityType.COW, new BlockPos(8, 1, 3), false);
            pulse(helper, be);
            helper.assertTrue(onTop(helper, adult), "成年牛应被传到机器顶上");
            helper.assertFalse(onTop(helper, baby), "成年分离器不应传幼年牛");
            helper.assertTrue(mru(be) == 900, "应扣 100 MRU，实际剩 " + mru(be));
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    /**
     * 归属关与类型关：有名字的、拴着绳的、别人驯服的小马、疣猪兽幼崽、幼年村民、身边 5 格内的都不传；
     * 主人自己驯服的小马照传（D1 取 B）。只扣一只的钱。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsSkipsProtectedAnimals(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        try {
            AnimalSeparatorBlockEntity be = place(helper, true, owner, 1000);
            Cow named = animal(helper, EntityType.COW, new BlockPos(8, 1, 1), true);
            named.setCustomName(Component.literal("Bessie"));
            Cow leashed = animal(helper, EntityType.COW, new BlockPos(8, 1, 3), true);
            leashed.setLeashedTo(owner, false);
            Horse othersFoal = animal(helper, EntityType.HORSE, new BlockPos(8, 1, 7), true);
            othersFoal.setTamed(true);
            othersFoal.setOwnerUUID(UUID.randomUUID());
            Hoglin hoglet = animal(helper, EntityType.HOGLIN, new BlockPos(8, 1, 9), true);
            hoglet.setImmuneToZombification(true);
            Villager kid = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 1, 1));
            kid.setNoAi(true);
            kid.setAge(-24000);
            Cow nearby = animal(helper, EntityType.COW, new BlockPos(4, 1, 5), true);
            Horse ownFoal = animal(helper, EntityType.HORSE, new BlockPos(10, 1, 5), true);
            ownFoal.setTamed(true);
            ownFoal.setOwnerUUID(owner.getUUID());

            pulse(helper, be);
            helper.assertTrue(onTop(helper, ownFoal), "主人自己驯服的小马应照传");
            for (Entity untouched : List.of(named, leashed, othersFoal, hoglet, kid, nearby)) {
                helper.assertFalse(onTop(helper, untouched), "不该传的被传了：" + untouched);
            }
            helper.assertTrue(mru(be) == 900, "只应扣一只的 100 MRU，实际剩 " + mru(be));
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    /**
     * 装了鞍的骆驼不传（ECEntityRules 把它算作谁的机器都不碰的，和起了名字的一样，不看鞍是谁装的）；没装鞍的野骆驼照传。
     * 骆驼 1.7 宽、2.375 高，放在 x = 9（碰撞箱 x ∈ [8.65, 10.35]），不碰忽略范围 x ≤ 7。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsSkipsSaddledCamel(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        try {
            AnimalSeparatorBlockEntity be = place(helper, false, owner, 1000);
            Camel saddled = animal(helper, EntityType.CAMEL, new BlockPos(9, 1, 2), false);
            saddled.equipSaddle(null);
            helper.assertTrue(saddled.isSaddled(), "测试前提：骆驼应已装上鞍");
            Camel wild = animal(helper, EntityType.CAMEL, new BlockPos(9, 1, 8), false);
            pulse(helper, be);
            helper.assertFalse(onTop(helper, saddled), "装了鞍的骆驼不应传");
            helper.assertTrue(onTop(helper, wild), "没装鞍的野骆驼应照传");
            helper.assertTrue(mru(be) == 900, "只应扣一只的 100 MRU，实际剩 " + mru(be));
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    /** MRU 按「每只消耗」判断（原作写死 ≥100）：每只 500、只有 300 时不传、不扣；补到 600 后传一只、剩 100 */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsNeedsEnoughMRU(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        try {
            AnimalSeparatorBlockEntity be = place(helper, true, owner, 300);
            be.setMRUUsageForTest(500);
            Cow baby = animal(helper, EntityType.COW, new BlockPos(8, 1, 5), true);
            pulse(helper, be);
            helper.assertFalse(onTop(helper, baby), "MRU 不够一只时不应传");
            helper.assertTrue(mru(be) == 300, "MRU 不够时不应扣，实际剩 " + mru(be));
            be.getMRUStorage().setMRU(600);
            pulse(helper, be);
            helper.assertTrue(onTop(helper, baby), "MRU 够了应传");
            helper.assertTrue(mru(be) == 100, "应扣 500，实际剩 " + mru(be));
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 落点

    /**
     * 顶上是实心方块：一只都不传、不扣；也不探测目的格（4a 审查 SEP-3：一只都放不下时，
     * 不该以主人的名义对顶上那块真方块发破坏事件，日志插件会记成「主人挖了石头」）
     */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsBlockedTopMovesNothing(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            AnimalSeparatorBlockEntity be = place(helper, true, owner, 1000);
            helper.setBlock(TOP, Blocks.STONE);
            Cow a = animal(helper, EntityType.COW, new BlockPos(8, 1, 5), true);
            Cow b = animal(helper, EntityType.COW, new BlockPos(8, 1, 3), true);
            helper.assertFalse(pulse(helper, be), "没有一只放得下，这次脉冲应直接结束");
            helper.assertFalse(onTop(helper, a) || onTop(helper, b), "顶上放不下时不应传");
            helper.assertTrue(mru(be) == 1000, "没传就不应扣，实际剩 " + mru(be));
            helper.assertTrue(claims.probes(TOP) == 0, "没有一只放得下时不应探测目的格，实际探了 " + claims.probes(TOP) + " 次");
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    /**
     * 按每只动物自己的碰撞箱判断：顶上只有一格高的空间时，成年牛（1.4 高）不传，成年鸡（0.7 高）照传；
     * 换成幼年分离器，小牛（0.45×0.7）也放得下、照传（手册「只留一格高时，鸡、小牛照传，成年牛就不传了」）
     */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsChecksEachAnimalsSize(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        try {
            AnimalSeparatorBlockEntity be = place(helper, false, owner, 1000);
            helper.setBlock(TOP.above(), Blocks.STONE);
            Cow cow = animal(helper, EntityType.COW, new BlockPos(8, 1, 5), false);
            Chicken chicken = animal(helper, EntityType.CHICKEN, new BlockPos(10, 1, 5), false);
            pulse(helper, be);
            helper.assertFalse(onTop(helper, cow), "牛放不下，不应传");
            helper.assertTrue(onTop(helper, chicken), "鸡放得下，应传（离得远也轮得到）");
            helper.assertTrue(mru(be) == 900, "只应扣一只，实际剩 " + mru(be));

            AnimalSeparatorBlockEntity childBe = place(helper, true, owner, 1000);
            Cow calf = animal(helper, EntityType.COW, new BlockPos(8, 1, 7), true);
            pulse(helper, childBe);
            helper.assertTrue(onTop(helper, calf), "一格高放得下小牛，幼年分离器应照传");
            helper.assertFalse(onTop(helper, cow), "成年牛仍不应被传");
            helper.assertTrue(mru(childBe) == 900, "幼年分离器应扣一只，实际剩 " + mru(childBe));
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    /** 头顶已经挤了 maxEntityCramming - 1 只生物：不再传（再传就开始挤伤）；挪走一只后照传 */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsStopsWhenTopIsCrowded(GameTestHelper helper) {
        int cramming = helper.getLevel().getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
        if (cramming <= 1) {
            helper.succeed();   // 游戏规则关了挤压伤害：没有这条限制
            return;
        }
        ServerPlayer owner = onlineOwner(helper);
        try {
            AnimalSeparatorBlockEntity be = place(helper, true, owner, 1000);
            List<Chicken> crowd = new ArrayList<>();
            for (int i = 0; i < cramming - 1; i++) {
                Chicken chicken = helper.spawn(EntityType.CHICKEN, DEST);
                chicken.setNoAi(true);
                crowd.add(chicken);
            }
            Cow baby = animal(helper, EntityType.COW, new BlockPos(8, 1, 5), true);
            pulse(helper, be);
            helper.assertFalse(onTop(helper, baby), "头顶挤满时不应再传");
            helper.assertTrue(mru(be) == 1000, "没传就不应扣，实际剩 " + mru(be));
            crowd.get(0).discard();
            pulse(helper, be);
            helper.assertTrue(onTop(helper, baby), "空出位置后应传");
            helper.assertTrue(mru(be) == 900, "应扣 100，实际剩 " + mru(be));
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    /**
     * 忽略半径是 0 时，已经站在机器顶上的也不再被原地「传」一遍（4a 审查 SEP-2）：忽略范围至少包住机器正上方一格。
     * 顶上 3 只成年鸡、远处 1 头成年牛：只传牛、只扣一只的钱（原来鸡离得最近、排在最前，每只都被原地重传、照扣 100）。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsZeroIgnoreRadiusLeavesTopAlone(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        try {
            AnimalSeparatorBlockEntity be = place(helper, false, owner, 1000);
            be.setIgnoreRadiusForTest(0D);
            for (int i = 0; i < 3; i++) {
                Chicken chicken = helper.spawn(EntityType.CHICKEN, DEST);
                chicken.setNoAi(true);
            }
            Cow cow = animal(helper, EntityType.COW, new BlockPos(8, 1, 5), false);
            pulse(helper, be);
            helper.assertTrue(onTop(helper, cow), "远处的牛应被传上来");
            helper.assertTrue(mru(be) == 900, "顶上的鸡不应被原地重传、再扣 MRU，实际剩 " + mru(be));
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 近处挤不掉远处（候选窗口压到 1 只，代替真摆 257 只）

    /**
     * 顶上只留一格高，两头更近的成年牛放不下、一只更远的鸡放得下：放不下的在截断之前就被滤掉，鸡照传（4a 审查 SEP-1 ①）。
     * 原来按距离截断后才看放不放得下，近处的全放不下时远处的永远轮不到。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsUnfitNearbyDoNotStarveFar(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        try {
            AnimalSeparatorBlockEntity be = place(helper, false, owner, 1000);
            be.setCandidateWindowForTest(1);
            helper.setBlock(TOP.above(), Blocks.STONE);
            Cow near = animal(helper, EntityType.COW, new BlockPos(8, 1, 5), false);
            Cow nearToo = animal(helper, EntityType.COW, new BlockPos(8, 1, 4), false);
            Chicken far = animal(helper, EntityType.CHICKEN, new BlockPos(10, 1, 9), false);
            pulse(helper, be);
            helper.assertTrue(onTop(helper, far), "远处放得下的鸡应照传");
            helper.assertFalse(onTop(helper, near) || onTop(helper, nearToo), "放不下的牛不应传");
            helper.assertTrue(mru(be) == 900, "只应扣一只，实际剩 " + mru(be));
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    /**
     * 两只更近的幼年牛所在格被拒、一只更远的在能动的格子里：被拒的丢掉后从名单往后补，远处的照传（4a 审查 SEP-1 ②）。
     * 被拒的格子各只探测一次。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsDeniedNearbyDoNotStarveFar(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        BlockPos deniedA = new BlockPos(8, 1, 5);
        BlockPos deniedB = new BlockPos(8, 1, 4);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(deniedA).deny(deniedB)) {
            AnimalSeparatorBlockEntity be = place(helper, true, owner, 1000);
            be.setCandidateWindowForTest(1);
            Cow a = animal(helper, EntityType.COW, deniedA, true);
            Cow b = animal(helper, EntityType.COW, deniedB, true);
            Cow far = animal(helper, EntityType.COW, new BlockPos(10, 1, 9), true);
            helper.assertFalse(pulse(helper, be), "探测预算够用，一刻内就应做完");
            helper.assertTrue(onTop(helper, far), "远处能动的格子里的牛应照传");
            helper.assertFalse(onTop(helper, a) || onTop(helper, b), "被拒的格子里的牛不应传");
            helper.assertTrue(mru(be) == 900, "只应扣一只，实际剩 " + mru(be));
            helper.assertTrue(claims.probes(deniedA) == 1 && claims.probes(deniedB) == 1,
                    "被拒的格子应各探测一次，实际 " + claims.probes(deniedA) + " / " + claims.probes(deniedB));
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 权限与主人

    /** 动物所在格被拒：那只不动、不扣；另一只照传。被拒的格子只探测一次 */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsDeniedCellUntouched(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        BlockPos deniedCell = new BlockPos(8, 1, 5);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(deniedCell)) {
            AnimalSeparatorBlockEntity be = place(helper, true, owner, 1000);
            Cow denied = animal(helper, EntityType.COW, deniedCell, true);
            Cow allowed = animal(helper, EntityType.COW, new BlockPos(8, 1, 3), true);
            pulse(helper, be);
            helper.assertFalse(onTop(helper, denied), "被拒的格子里的动物不应传");
            helper.assertTrue(helper.absolutePos(deniedCell).equals(denied.blockPosition()), "被拒的动物应留在原地");
            helper.assertTrue(onTop(helper, allowed), "能动的格子里的动物应照传");
            helper.assertTrue(mru(be) == 900, "只应扣一只，实际剩 " + mru(be));
            helper.assertTrue(claims.probes(deniedCell) == 1, "被拒的格子应只探测一次，实际 " + claims.probes(deniedCell));
            helper.assertTrue(claims.breaksBy(deniedCell, owner.getUUID()) == 1, "应以主人的身份探测");
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    /** 目的格（机器正上方）被拒：整次不传、不扣，也不再去探测动物的格子 */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsDeniedDestinationMovesNothing(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        BlockPos cowCell = new BlockPos(8, 1, 5);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(TOP)) {
            AnimalSeparatorBlockEntity be = place(helper, true, owner, 1000);
            Cow baby = animal(helper, EntityType.COW, cowCell, true);
            boolean more = pulse(helper, be);
            helper.assertFalse(more, "目的格被拒应整次结束");
            helper.assertFalse(onTop(helper, baby), "目的格被拒时不应传");
            helper.assertTrue(mru(be) == 1000, "没传就不应扣，实际剩 " + mru(be));
            helper.assertTrue(claims.probes(TOP) == 1, "目的格应探测一次");
            helper.assertTrue(claims.breaks(cowCell) == 0, "目的格被拒后不应再探测动物的格子");
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
            cleanup(helper);
        }
        helper.succeed();
    }

    /**
     * 主人不在线：不动、不扣（脉冲丢弃）；关掉 require_owner_online 后照常工作（不探测）；
     * 没有主人（/setblock 放的）：不动（GameTest 服是专用服，没有「单人里算房主的」）
     */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsOwnerRules(GameTestHelper helper) {
        ServerPlayer offline = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-away");
        try {
            AnimalSeparatorBlockEntity be = place(helper, true, offline, 1000);
            Cow first = animal(helper, EntityType.COW, new BlockPos(8, 1, 5), true);
            helper.assertFalse(pulse(helper, be), "主人不在线时脉冲应直接丢弃");
            helper.assertFalse(onTop(helper, first), "主人不在线时不应传");
            helper.assertTrue(mru(be) == 1000, "主人不在线时不应扣，实际剩 " + mru(be));

            ECProtection.Config.setRequireOwnerOnlineForTest(false);
            try {
                helper.assertTrue(be.displayedOwnerState() == ECProtection.OwnerState.ONLINE, "关了「主人在线」时界面不该提示主人不在线");
                pulse(helper, be);
                helper.assertTrue(onTop(helper, first), "关了「主人在线」后主人不在线也应照常工作");
                helper.assertTrue(mru(be) == 900, "应扣 100，实际剩 " + mru(be));
            } finally {
                ECProtection.Config.setRequireOwnerOnlineForTest(null);
            }

            be.setOwner(null, "");
            Cow second = animal(helper, EntityType.COW, new BlockPos(8, 1, 3), true);
            helper.assertFalse(pulse(helper, be), "没有主人时脉冲应直接丢弃");
            helper.assertFalse(onTop(helper, second), "没有主人的分离器不应传");
            helper.assertTrue(mru(be) == 900, "没有主人时不应扣，实际剩 " + mru(be));
        } finally {
            cleanup(helper);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 上限、续做（走真的红石）

    /**
     * 17 只幼年猪分在 17 格：给一下红石，第一刻探测预算（每台每刻 16 格）只够目的格 + 15 只，下一刻续做传第 16 只，然后停在上限 16；
     * 第 17 只不动，共扣 1 600。走真的红石：上升沿 → 计划刻 → onRedstonePulse → continuePulse。
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 100)
    public static void separatorsCapsAt16AndContinues(GameTestHelper helper) {
        ServerPlayer owner = onlineOwner(helper);
        AnimalSeparatorBlockEntity be = place(helper, true, owner, 5000);
        List<Pig> pigs = new ArrayList<>();
        outer:
        for (int x = 8; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                pigs.add(animal(helper, EntityType.PIG, new BlockPos(x, 1, z), true));
                if (pigs.size() == 17) {
                    break outer;
                }
            }
        }
        helper.setBlock(MACHINE.north(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(8, () -> {
            try {
                int moved = (int) pigs.stream().filter(pig -> onTop(helper, pig)).count();
                helper.assertTrue(moved == 16, "一次脉冲应正好传 16 只，实际 " + moved);
                helper.assertTrue(mru(be) == 5000 - 16 * 100, "应扣 1600，实际剩 " + mru(be));
                helper.assertTrue(be.pulseGate().lastFire() != Long.MIN_VALUE, "应由红石触发过");
            } finally {
                ECProtection.clearOnlineOverride(owner.getUUID());
                cleanup(helper);
            }
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 配方、界面

    /** 两张工作台配方都在，产物对得上 */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsRecipes(GameTestHelper helper) {
        for (String name : List.of("child_separator", "adult_separator")) {
            ResourceLocation id = EssentialCraft5.id("separators/" + name);
            Optional<? extends Recipe<?>> recipe = helper.getLevel().getRecipeManager().byKey(id);
            helper.assertTrue(recipe.isPresent(), "缺配方 " + id);
            ItemStack result = recipe.get().getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(result.is((name.equals("child_separator") ? ECSeparators.CHILD_SEPARATOR : ECSeparators.ADULT_SEPARATOR).get().asItem()),
                    id + " 的产物不对：" + result);
        }
        helper.succeed();
    }

    /** 界面：0 号格只收绑定宝石；主人状态同步（没有主人 / 不在线 / 在线） */
    @GameTest(template = EMPTY_LARGE)
    public static void separatorsMenuOwnerState(GameTestHelper helper) {
        ServerPlayer viewer = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-sep");
        try {
            AnimalSeparatorBlockEntity be = place(helper, false, null, 0);
            AnimalSeparatorMenu menu = new AnimalSeparatorMenu(0, viewer.getInventory(), be);
            helper.assertFalse(menu.getSlot(AnimalSeparatorBlockEntity.SLOT_GEM).mayPlace(new ItemStack(Items.DIRT)), "宝石格不应收泥土");
            int index = MachineData.BASE_COUNT + AnimalSeparatorMenu.EXTRA_OWNER;
            helper.assertTrue(menu.getData().get(index) == ECProtection.OwnerState.NONE.ordinal() + 1, "没有主人时应同步 NONE");
            be.setOwner(owner.getUUID(), "ec5-sep");
            helper.assertTrue(menu.getData().get(index) == ECProtection.OwnerState.OFFLINE.ordinal() + 1, "主人不在线时应同步 OFFLINE");
            ECProtection.setOnlineOverride(owner);
            helper.assertTrue(menu.getData().get(index) == ECProtection.OwnerState.ONLINE.ordinal() + 1, "主人在线时应同步 ONLINE");
        } finally {
            ECProtection.clearOnlineOverride(owner.getUUID());
        }
        helper.succeed();
    }
}
