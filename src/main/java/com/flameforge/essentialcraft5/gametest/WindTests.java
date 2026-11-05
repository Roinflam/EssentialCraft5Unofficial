package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.WindRuneBlockEntity;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.command.WindCommands;
import com.flameforge.essentialcraft5.effect.ParanormalLightnessEffect;
import com.flameforge.essentialcraft5.item.WindTabletItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.WindRelations;
import com.flameforge.essentialcraft5.recipe.WindImbueRecipe;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 风模块的 GameTest（batch3.md §3.3 的测试点）：
 * 祭坛的形状（13 / 12 / 8 / 8）、缺一块不成形、报第一个不符的格子；符文挡水和岩浆；8 根柱注风钻石得风之水晶、按量扣 ESPE、产物回到主手；ESPE 不够 / 结构不全什么都不扣；
 * 创造模式不花 ESPE；灵魂石授予风之眷顾（灵魂石不变）、已受眷顾再注风被拒且不扣 ESPE、FakePlayer 不能受眷顾；三条注风配方与水瓶只认水；
 * 轻盈叠喝封顶 III / 60 分钟、跳跃与摔落、移速修饰符；风之药水 +10 好感；瓶中之风三件 1 / 2 / 10、FakePlayer 不处理；
 * 被动事件、β 四选一、推一把、托起来（没受眷顾的不托）；捕风挂坠给受困之风；风之石板显名、好感不够时右键点燃的下界门不换不扣（换门本身见 HoannaPortalTests）；/ec5 player … wind。
 * 大结构用模板 empty_large（11×8×11，(0,0,0) 是结构方块，东西从 y=1 起放），符文放在 (5,2,5)（下一层护板在 y=1，最上面的柱子到 y=6）。
 * 权限：风模块不改任何方块、不碰实体，没有「被拒的格子」可测；FakePlayer 的限制测了。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class WindTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos RUNE = new BlockPos(5, 2, 5);
    private static final double EPS = 1.0E-6D;

    private WindTests() {
    }

    // ================================================================ 工具

    /** 摆出整座祭坛（护板用默认颜色，水晶柱用米斯莱柱，放底座会自动补齐三段），返回符文的方块实体 */
    private static WindRuneBlockEntity buildAltar(GameTestHelper helper) {
        for (Map.Entry<BlockPos, StructurePattern.Rule> entry : ECWind.RUNE_STRUCTURE.rules().entrySet()) {
            helper.setBlock(RUNE.offset(entry.getKey()), entry.getValue().displayState());
        }
        if (!(helper.getBlockEntity(RUNE) instanceof WindRuneBlockEntity rune)) {
            throw new IllegalStateException("符文那一格应有方块实体");
        }
        return rune;
    }

    /** 8 根柱子的底座（相对坐标） */
    private static List<BlockPos> pillars() {
        return ECWind.RUNE_STRUCTURE.offsets(ECWind.RUNE_PILLAR).stream().map(RUNE::offset).toList();
    }

    private static void fillPillars(GameTestHelper helper, double each) {
        for (BlockPos pos : pillars()) {
            pillar(helper, pos).getESPEStorage().setESPE(each);
        }
    }

    private static MithrilineCrystalPillarBlockEntity pillar(GameTestHelper helper, BlockPos pos) {
        if (!(helper.getBlockEntity(pos) instanceof MithrilineCrystalPillarBlockEntity pillar)) {
            throw new IllegalStateException(pos + " 应是水晶柱底座");
        }
        return pillar;
    }

    private static double totalESPE(GameTestHelper helper) {
        double total = 0D;
        for (BlockPos pos : pillars()) {
            total += pillar(helper, pos).getESPEStorage().getESPE();
        }
        return total;
    }

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("模拟玩家应挂着玩家数据");
        }
        return data;
    }

    private static int count(Player player, net.minecraft.world.item.Item item) {
        return player.getInventory().countItem(item);
    }

    private static CommandSourceStack source(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .withPermission(ECCommands.PERMISSION_LEVEL)
                .withSuppressedOutput();
    }

    // ================================================================ 祭坛

    /** 原作 tier0Checker：米斯莱护板 13、淡青护板 12、魔力护板 8、水晶柱 8，四面对称；整座摆好成形，拿掉一块淡青护板不成形、报的就是那一格 */
    @GameTest(template = EMPTY_LARGE)
    public static void windRuneStructure(GameTestHelper helper) {
        Map<BlockPos, StructurePattern.Rule> rules = ECWind.RUNE_STRUCTURE.rules();
        long mithriline = rules.values().stream().filter(rule -> rule.displayState().is(com.flameforge.essentialcraft5.registry.ECMithriline.MITHRILINE_PLATING.get())).count();
        long pale = rules.values().stream().filter(rule -> rule.displayState().is(com.flameforge.essentialcraft5.registry.ECAlloys.PALE_PLATING.get())).count();
        long magic = rules.values().stream().filter(rule -> rule.displayState().is(com.flameforge.essentialcraft5.registry.ECAlloys.MAGIC_PLATING.get())).count();
        long pillars = rules.values().stream().filter(rule -> rule == ECWind.RUNE_PILLAR).count();
        helper.assertTrue(mithriline == 13 && pale == 12 && magic == 8 && pillars == 8,
                "应是 13 / 12 / 8 / 8，实际 " + mithriline + " / " + pale + " / " + magic + " / " + pillars);
        // 原作的 8 个取 ESPE 的位置（相对符文）
        List<BlockPos> expected = List.of(new BlockPos(2, 0, 2), new BlockPos(2, 0, -2), new BlockPos(-2, 0, 2), new BlockPos(-2, 0, -2),
                new BlockPos(1, 2, 1), new BlockPos(-1, 2, 1), new BlockPos(1, 2, -1), new BlockPos(-1, 2, -1));
        helper.assertTrue(ECWind.RUNE_STRUCTURE.offsets(ECWind.RUNE_PILLAR).containsAll(expected), "柱子位置应和原作 coords 一致");

        WindRuneBlockEntity rune = buildAltar(helper);
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(RUNE);
        helper.assertTrue(rune.structure().checkNow(level, origin, Direction.NORTH) == StructureResult.MATCH, "整座摆好应成形");
        WindRuneBlockEntity.serverTick(level, origin, level.getBlockState(origin), rune);
        helper.assertTrue(rune.isFormed(), "成形后要同步给客户端（formed = true）");

        BlockPos removed = new BlockPos(1, -1, 1);
        helper.setBlock(RUNE.offset(removed), Blocks.STONE);
        helper.assertTrue(rune.structure().checkNow(level, origin, Direction.NORTH) == StructureResult.MISMATCH, "缺一块应不成形");
        StructurePattern.Mismatch mismatch = rune.structure().mismatch();
        helper.assertTrue(mismatch != null && mismatch.offset().equals(removed), "应报拿掉的那一格，实际 " + (mismatch == null ? null : mismatch.offset()));
        helper.succeed();
    }

    /**
     * 符文挡液体（原作 Material.ROCK；ECWind.WIND_RUNE 的 forceSolidOn）：两个符文各用玻璃围一圈，正上方分别放水源、岩浆源（只能往下流），
     * 等过岩浆的流动间隔（主世界 30 刻）两轮，符文都还在、格子里没有液体、没冲出掉落物；发射器 / 水桶走的 canBeReplaced(液体) 也是 false，
     * 碰撞箱照旧是空的。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void windRuneBlocksFluids(GameTestHelper helper) {
        BlockState state = ECWind.WIND_RUNE.get().defaultBlockState();
        BlockPos waterRune = new BlockPos(1, 2, 1);
        BlockPos lavaRune = new BlockPos(3, 2, 3);
        helper.assertTrue(state.blocksMotion(), "符文应挡液体（blocksMotion）");
        helper.assertTrue(!state.canBeReplaced(Fluids.WATER) && !state.canBeReplaced(Fluids.LAVA), "水桶、发射器倒液体不应能直接替换符文");
        helper.assertTrue(state.getCollisionShape(helper.getLevel(), helper.absolutePos(waterRune)).isEmpty(), "符文仍然没有碰撞箱");

        // 两个 3×3 的小池（石头底、两层玻璃墙），共用 (2, y, 2) 那根角柱
        for (BlockPos rune : List.of(waterRune, lavaRune)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.setBlock(rune.offset(dx, -1, dz), Blocks.STONE);
                    if (dx != 0 || dz != 0) {
                        helper.setBlock(rune.offset(dx, 0, dz), Blocks.GLASS);
                        helper.setBlock(rune.offset(dx, 1, dz), Blocks.GLASS);
                    }
                }
            }
            helper.setBlock(rune, ECWind.WIND_RUNE.get());
        }
        helper.setBlock(waterRune.above(), Blocks.WATER);
        helper.setBlock(lavaRune.above(), Blocks.LAVA);

        helper.runAfterDelay(70, () -> {
            for (BlockPos rune : List.of(waterRune, lavaRune)) {
                helper.assertBlockPresent(ECWind.WIND_RUNE.get(), rune);
                helper.assertTrue(helper.getBlockState(rune).getFluidState().isEmpty(), rune + " 的符文格里不应有液体");
            }
            helper.assertItemEntityNotPresent(ECWind.WIND_RUNE.get().asItem(), waterRune, 3D);
            // 液体源收掉，别在测试区留岩浆
            helper.setBlock(waterRune.above(), Blocks.AIR);
            helper.setBlock(lavaRune.above(), Blocks.AIR);
            helper.succeed();
        });
    }

    /** 8 根柱各 2000：注风一颗钻石扣 10000、剩 6000；还有钻石时产物进背包，最后一颗换完产物直接放回主手 */
    @GameTest(template = EMPTY_LARGE)
    public static void windRuneImbuesDiamond(GameTestHelper helper) {
        WindRuneBlockEntity rune = buildAltar(helper);
        fillPillars(helper, 2000D);
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));

        helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.IMBUED, "应注风成功");
        helper.assertTrue(player.getMainHandItem().is(Items.DIAMOND) && player.getMainHandItem().getCount() == 1, "主手应剩 1 颗钻石");
        helper.assertTrue(count(player, ECWind.WIND_GEM.get()) == 1, "背包里应有 1 颗风之水晶");
        helper.assertTrue(Math.abs(totalESPE(helper) - 6000D) < EPS, "应扣 10000 ESPE，剩 " + totalESPE(helper));

        fillPillars(helper, 1250D);
        helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.IMBUED, "正好 10000 也应成功");
        helper.assertTrue(player.getMainHandItem().is(ECWind.WIND_GEM.get()), "最后一颗换完，产物应放回主手");
        helper.assertTrue(totalESPE(helper) < EPS, "柱子应被抽空，剩 " + totalESPE(helper));
        helper.succeed();
    }

    /** ESPE 差一点、结构缺一块、手里不是原料：什么都不扣 */
    @GameTest(template = EMPTY_LARGE)
    public static void windRuneRefusesWithoutESPEOrStructure(GameTestHelper helper) {
        WindRuneBlockEntity rune = buildAltar(helper);
        fillPillars(helper, 1249.99D);
        double before = totalESPE(helper);
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));

        helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.NO_ESPE, "9999.92 ESPE 不够 10000");
        helper.assertTrue(player.getMainHandItem().is(Items.DIAMOND) && player.getMainHandItem().getCount() == 1, "钻石不应被扣");
        helper.assertTrue(Math.abs(totalESPE(helper) - before) < EPS, "ESPE 不应被扣");

        fillPillars(helper, 5000D);
        helper.setBlock(RUNE.offset(new BlockPos(2, 0, 0)), Blocks.AIR);   // 拿掉一块魔力护板
        helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.NOT_FORMED, "结构不全应拒绝");
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && Math.abs(totalESPE(helper) - 40000D) < EPS, "结构不全时什么都不扣");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.PASS, "不是注风原料应让给别的交互");
        helper.succeed();
    }

    /** 创造模式（服务端能力位）不需要也不扣 ESPE，原料照原作扣 */
    @GameTest(template = EMPTY_LARGE)
    public static void windRuneCreativeFree(GameTestHelper helper) {
        WindRuneBlockEntity rune = buildAltar(helper);
        Player player = helper.makeMockSurvivalPlayer();
        player.getAbilities().instabuild = true;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.IMBUED, "创造模式柱子空着也能注风");
        helper.assertTrue(player.getMainHandItem().is(ECWind.WIND_GEM.get()), "应换成风之水晶");
        helper.assertTrue(totalESPE(helper) == 0D, "创造模式不扣 ESPE");
        helper.succeed();
    }

    /**
     * 灵魂石：授予风之眷顾、灵魂石原样留着、抽走 20000（配置 windbound_espe，原作 40000）；已受眷顾再注风被拒、不扣 ESPE、灵魂石不变（修原作白扣 4 万还换成空白灵魂石）；
     * FakePlayer 不能受眷顾。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void windRuneWindbound(GameTestHelper helper) {
        WindRuneBlockEntity rune = buildAltar(helper);
        fillPillars(helper, 2500D);
        Player player = helper.makeMockSurvivalPlayer();
        IPlayerData data = data(player);
        ItemStack stone = new ItemStack(ECBootstrap.SOUL_STONE.get());
        stone.getOrCreateTag().putString("marker", "keep-me");
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);

        helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.WINDBOUND, "应受到风之眷顾");
        helper.assertTrue(data.isWindbound(), "玩家数据应记下眷顾");
        helper.assertTrue(player.getMainHandItem() == stone && stone.getCount() == 1 && "keep-me".equals(stone.getTag().getString("marker")),
                "灵魂石应原样留在手里");
        helper.assertTrue(totalESPE(helper) < EPS, "应抽走 20000，剩 " + totalESPE(helper));

        fillPillars(helper, 5000D);
        helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.ALREADY_WINDBOUND, "已受眷顾应被拒");
        helper.assertTrue(Math.abs(totalESPE(helper) - 40000D) < EPS, "被拒时不扣 ESPE");
        helper.assertTrue(player.getMainHandItem() == stone && stone.getCount() == 1, "被拒时灵魂石不变");

        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        ItemStack before = fake.getMainHandItem().copy();
        try {
            fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECBootstrap.SOUL_STONE.get()));
            helper.assertTrue(rune.imbue(fake) == WindRuneBlockEntity.Outcome.NOT_ALLOWED, "FakePlayer 不能受眷顾");
            helper.assertTrue(Math.abs(totalESPE(helper) - 40000D) < EPS, "FakePlayer 被拒时不扣 ESPE");
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, before);
        }
        helper.succeed();
    }

    /** 三条注风配方（RecipesCore L205~209）：钻石 10000（读配置 gem_espe）、水瓶 250（别的药水不行）、灵魂石 20000（读配置 windbound_espe，原作 40000）授予眷顾 */
    @GameTest(template = EMPTY)
    public static void windImbueRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WindImbueRecipe gem = WindImbueRecipe.find(level, new ItemStack(Items.DIAMOND));
        helper.assertTrue(gem != null && gem.getResultItem(level.registryAccess()).is(ECWind.WIND_GEM.get())
                && gem.getEspe() == ECWind.Config.gemESPE() && gem.getEspe() == 10000D
                && WindImbueRecipe.CONFIG_GEM_ESPE.equals(gem.getEspeConfig()) && !gem.grantsWindbound(), "钻石 → 风之水晶 10000");
        WindImbueRecipe potion = WindImbueRecipe.find(level, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
        helper.assertTrue(potion != null && potion.getResultItem(level.registryAccess()).is(ECWind.AIR_POTION.get()) && potion.getEspe() == 250D,
                "水瓶 → 风之药水 250");
        helper.assertTrue(WindImbueRecipe.find(level, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.AWKWARD)) == null, "粗制的药水不能注风");
        WindImbueRecipe boon = WindImbueRecipe.find(level, new ItemStack(ECBootstrap.SOUL_STONE.get()));
        helper.assertTrue(boon != null && boon.grantsWindbound() && boon.getEspe() == ECWind.Config.windboundESPE() && boon.getEspe() == 20000D
                && WindImbueRecipe.CONFIG_WINDBOUND_ESPE.equals(boon.getEspeConfig()), "灵魂石 → 风之眷顾 20000");
        helper.assertTrue(level.getRecipeManager().getAllRecipesFor(ECWind.WIND_IMBUE.get()).size() >= 3, "至少三条注风配方");
        helper.succeed();
    }

    // ================================================================ 轻盈与风之药水

    /** 叠喝：I 级 2 分钟起，每瓶 +2 分钟、每 8 分钟一级；封顶 III 级、60 分钟（原作无上限）；无限时长的不动 */
    @GameTest(template = EMPTY)
    public static void windLightnessStackingCapped(GameTestHelper helper) {
        MobEffectInstance first = ParanormalLightnessEffect.next(null, 3, 72000);
        helper.assertTrue(first != null && first.getAmplifier() == 0 && first.getDuration() == 2400, "第一瓶 I 级 2400 tick");
        MobEffectInstance second = ParanormalLightnessEffect.next(lightness(2400, 0), 3, 72000);
        helper.assertTrue(second != null && second.getAmplifier() == 0 && second.getDuration() == 4800, "剩 2 分钟时仍 I 级、4800 tick");
        MobEffectInstance eight = ParanormalLightnessEffect.next(lightness(9600, 0), 3, 72000);
        helper.assertTrue(eight != null && eight.getAmplifier() == 1 && eight.getDuration() == 12000, "剩 8 分钟时 II 级");
        MobEffectInstance forty = ParanormalLightnessEffect.next(lightness(48000, 2), 3, 72000);
        helper.assertTrue(forty != null && forty.getAmplifier() == 2 && forty.getDuration() == 50400, "剩 40 分钟时封顶 III 级（原作是 V 级）");
        MobEffectInstance full = ParanormalLightnessEffect.next(lightness(71000, 2), 3, 72000);
        helper.assertTrue(full != null && full.getAmplifier() == 2 && full.getDuration() == 72000, "时长封顶 60 分钟");
        helper.assertTrue(ParanormalLightnessEffect.next(lightness(MobEffectInstance.INFINITE_DURATION, 4), 3, 72000) == null, "无限时长的不动");

        Player player = helper.makeMockSurvivalPlayer();
        for (int i = 0; i < 40; i++) {
            ParanormalLightnessEffect.drink(player);
        }
        MobEffectInstance after = player.getEffect(ECWind.PARANORMAL_LIGHTNESS.get());
        helper.assertTrue(after != null && after.getAmplifier() == 2 && after.getDuration() == 72000,
                "连喝 40 瓶应是 III 级 60 分钟，实际 " + after);
        helper.succeed();
    }

    private static MobEffectInstance lightness(int duration, int amplifier) {
        return new MobEffectInstance(ECWind.PARANORMAL_LIGHTNESS.get(), duration, amplifier, true, true);
    }

    /** III 级：起跳竖直速度 +0.3、摔落少算 3 格、移速修饰符 +120%（MULTIPLY_TOTAL）；没有效果时都不变 */
    @GameTest(template = EMPTY)
    public static void windLightnessJumpFallSpeed(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.setDeltaMovement(0D, 0.42D, 0D);
        ParanormalLightnessEffect.applyJump(player);
        helper.assertTrue(Math.abs(player.getDeltaMovement().y - 0.42D) < EPS, "没有轻盈时不加速度");
        helper.assertTrue(ParanormalLightnessEffect.reduceFall(player, 5F) == 5F, "没有轻盈时摔落不变");

        player.addEffect(lightness(2400, 2));
        player.setDeltaMovement(0D, 0.42D, 0D);
        ParanormalLightnessEffect.applyJump(player);
        helper.assertTrue(Math.abs(player.getDeltaMovement().y - 0.72D) < EPS, "III 级起跳 +0.3，实际 " + player.getDeltaMovement().y);
        helper.assertTrue(ParanormalLightnessEffect.reduceFall(player, 5F) == 2F && ParanormalLightnessEffect.reduceFall(player, 2F) == 0F,
                "III 级摔落少算 3 格");
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier modifier = speed == null ? null : speed.getModifier(UUID.fromString(ParanormalLightnessEffect.SPEED_MODIFIER_UUID));
        helper.assertTrue(modifier != null && Math.abs(modifier.getAmount() - 1.2D) < EPS
                && modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL, "移速修饰符应是 +1.2 MULTIPLY_TOTAL，实际 " + modifier);
        helper.succeed();
    }

    /** 喝一瓶风之药水：扣正在喝的那一组、好感 +10（系数 0.1）、I 级轻盈 2 分钟、风之触 */
    @GameTest(template = EMPTY)
    public static void windAirPotionDrink(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        IPlayerData data = data(player);
        ItemStack potions = new ItemStack(ECWind.AIR_POTION.get(), 2);
        player.setItemInHand(InteractionHand.OFF_HAND, potions);
        ItemStack left = potions.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(left.getCount() == 1 && player.getOffhandItem().getCount() == 1, "应扣正在喝的那一组（副手）");
        helper.assertTrue(data.getWindPoints() == 10, "系数 0.1 时 +100 实得 10，实际 " + data.getWindPoints());
        MobEffectInstance lightness = player.getEffect(ECWind.PARANORMAL_LIGHTNESS.get());
        helper.assertTrue(lightness != null && lightness.getAmplifier() == 0 && lightness.getDuration() == 2400, "应得到 I 级轻盈 2 分钟");
        helper.assertTrue(player.hasEffect(ECWind.WIND_TOUCH.get()), "加好感应给风之触");
        helper.succeed();
    }

    // ================================================================ 瓶中之风、被动事件

    /** 瓶中之风 / 禁锢之风 / 受困之风：+1 / +2 / +10（系数 0.1），打碎就少一个；FakePlayer 不处理、不扣 */
    @GameTest(template = EMPTY)
    public static void windBottledWinds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        IPlayerData data = data(player);
        int[] expected = {1, 3, 13};
        List<ItemStack> bottles = List.of(new ItemStack(ECWind.BOTTLED_WIND.get()), new ItemStack(ECWind.IMPRISONED_WIND.get()),
                new ItemStack(ECWind.WIND_KEEPER.get()));
        for (int i = 0; i < bottles.size(); i++) {
            ItemStack bottle = bottles.get(i);
            player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
            bottle.getItem().use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(bottle.isEmpty(), bottle + " 应被打碎");
            helper.assertTrue(data.getWindPoints() == expected[i], "累计好感应是 " + expected[i] + "，实际 " + data.getWindPoints());
        }
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        ItemStack before = fake.getMainHandItem().copy();
        try {
            ItemStack keeper = new ItemStack(ECWind.WIND_KEEPER.get());
            fake.setItemInHand(InteractionHand.MAIN_HAND, keeper);
            helper.assertTrue(!keeper.getItem().use(level, fake, InteractionHand.MAIN_HAND).getResult().consumesAction() && keeper.getCount() == 1,
                    "FakePlayer 右键不处理、不扣");
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, before);
        }
        helper.succeed();
    }

    /** 被动事件 +100、推一把（速度 XXXII 1 秒）、托起来（跳跃提升 XIII 5 秒，没受眷顾的不托）；β 四选一各给一种（修 rndID == 2 写两遍）；没受眷顾时每秒判定什么都不做 */
    @GameTest(template = EMPTY)
    public static void windPassiveEvents(GameTestHelper helper) {
        RandomSource random = RandomSource.create(1L);
        Player player = helper.makeMockSurvivalPlayer();
        IPlayerData data = data(player);
        for (int i = 0; i < 200; i++) {
            WindRelations.secondTick(player, random);
        }
        WindRelations.jumpEvent(player, random);
        helper.assertTrue(data.getWindPoints() == 0 && !player.hasEffect(MobEffects.JUMP), "没受眷顾时什么都不发生");

        data.setWindbound(true);
        WindRelations.passiveEvent(player, random);
        helper.assertTrue(data.getWindPoints() == 100, "被动事件 1000 → 实得 100，实际 " + data.getWindPoints());
        WindRelations.sprintEvent(player, random);
        MobEffectInstance push = player.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(data.getWindPoints() == 200 && push != null && push.getAmplifier() == WindRelations.PUSH_AMPLIFIER
                && push.getDuration() == WindRelations.PUSH_TICKS, "推一把：+100、速度 XXXII 1 秒");
        WindRelations.jumpEvent(player, random);
        MobEffectInstance lift = player.getEffect(MobEffects.JUMP);
        helper.assertTrue(data.getWindPoints() == 300 && lift != null && lift.getAmplifier() == WindRelations.LIFT_AMPLIFIER
                && lift.getDuration() == WindRelations.LIFT_TICKS, "托起来：+100、跳跃提升 XIII 5 秒");

        Player boon = helper.makeMockSurvivalPlayer();
        WindRelations.applyBoon(boon, 0);
        WindRelations.applyBoon(boon, 1);
        WindRelations.applyBoon(boon, 2);
        WindRelations.applyBoon(boon, 3);
        helper.assertTrue(amplifier(boon, MobEffects.DAMAGE_BOOST) == 1 && amplifier(boon, MobEffects.REGENERATION) == 0
                && amplifier(boon, MobEffects.SATURATION) == 0 && amplifier(boon, MobEffects.DIG_SPEED) == 1, "β 四选一：力量 II、生命恢复、饱和、急迫 II");
        helper.assertTrue(boon.getEffect(MobEffects.DIG_SPEED).getDuration() == WindRelations.BOON_TICKS, "各 6 分钟");

        helper.assertTrue(WindRelations.lightnessFactor(player) == 1, "没有轻盈时系数 1");
        player.addEffect(lightness(2400, 2));
        helper.assertTrue(WindRelations.lightnessFactor(player) == 3, "III 级轻盈时几率 ×3");
        helper.succeed();
    }

    private static int amplifier(Player player, net.minecraft.world.effect.MobEffect effect) {
        MobEffectInstance instance = player.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    /** 捕风挂坠：塞一个受困之风；没戴时 grant 只加好感不给东西 */
    @GameTest(template = EMPTY)
    public static void windCatcherGivesTrappedWind(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        WindRelations.catchWind(player);
        helper.assertTrue(count(player, ECWind.WIND_KEEPER.get()) == 1, "应塞一个受困之风");
        RandomSource random = RandomSource.create(7L);
        for (int i = 0; i < 50; i++) {
            WindRelations.grant(player, 10, random);
        }
        helper.assertTrue(count(player, ECWind.WIND_KEEPER.get()) == 1 && WindAffinity.points(player) == 50, "没戴捕风挂坠时只加好感");
        helper.succeed();
    }

    // ================================================================ 风之石板、指令

    /** 真名：0 → 0 个字、1750 → 11、3499 → 21、3500 → 22（原作 pixelatedTextureSize）；门槛 0 全显 */
    @GameTest(template = EMPTY)
    public static void windTabletReveal(GameTestHelper helper) {
        helper.assertTrue(WindTabletItem.revealed(0, 3500) == 0 && WindTabletItem.revealed(1750, 3500) == 11
                && WindTabletItem.revealed(3499, 3500) == 21 && WindTabletItem.revealed(3500, 3500) == 22
                && WindTabletItem.revealed(999999, 3500) == 22 && WindTabletItem.revealed(5, 0) == 22, "显名的字数");
        String half = WindTabletItem.revealedName(1750, 3500);
        helper.assertTrue(half.length() == 22 && half.startsWith("Owethanna E") && half.endsWith("?"), "显一半：" + half);
        helper.assertTrue(WindTabletItem.revealedName(3500, 3500).equals(WindTabletItem.WIND_NAME), "够了全部显出");
        helper.succeed();
    }

    /**
     * 好感不够时右键点燃的下界门（第六批 6a M2 改写，原 windTabletPortalNoResponse）：只说「没有回应」、返回 FAIL，门一格不变、石板 MRU 仍 5000；
     * 点别的方块让给别的交互。好感够了以后的换门、被拒、补门见 HoannaPortalTests。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void windTabletNotAttunedNoCost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 4 × 5 黑曜石框，内框 2 × 3（x 4~5、y 2~4、z 5），照原版点燃
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                if (dx == -1 || dx == 2 || dy == -1 || dy == 3) {
                    helper.setBlock(new BlockPos(4 + dx, 2 + dy, 5), Blocks.OBSIDIAN);
                }
            }
        }
        BlockPos interior = helper.absolutePos(new BlockPos(4, 2, 5));
        boolean lit = PortalShape.findEmptyPortalShape(level, interior, Direction.Axis.X).map(shape -> {
            shape.createPortalBlocks();
            return true;
        }).orElse(false);
        helper.assertTrue(lit, "点燃下界门");
        helper.setBlock(new BlockPos(8, 1, 8), Blocks.STONE);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack tablet = new ItemStack(ECWind.WIND_TABLET.get());
        tablet.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(WindTabletItem.MAX_MRU));
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);

        InteractionResult onPortal = tablet.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(interior), Direction.NORTH, interior, false)));
        BlockPos stone = helper.absolutePos(new BlockPos(8, 1, 8));
        InteractionResult onStone = tablet.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false)));
        int mru = tablet.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
        int portals = 0;
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                if (level.getBlockState(interior.offset(dx, dy, 0)).is(Blocks.NETHER_PORTAL)) {
                    portals++;
                }
            }
        }
        helper.assertTrue(!WindTabletItem.attuned(player), "新玩家好感为 0");
        helper.assertTrue(onPortal == InteractionResult.FAIL && onStone == InteractionResult.PASS,
                "好感不够时点门返回 FAIL、别的方块让出去：" + onPortal + " / " + onStone);
        helper.assertTrue(mru == WindTabletItem.MAX_MRU, "不扣 MRU，实际 " + mru);
        helper.assertTrue(portals == 6, "下界门 6 格都不变，实际 " + portals);
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                level.setBlock(interior.offset(dx, dy, 0), Blocks.AIR.defaultBlockState(), 18);
            }
        }
        helper.succeed();
    }

    /** /ec5 player … wind points / windbound：设了就改玩家数据，get 返回值（execute store 用） */
    @GameTest(template = EMPTY)
    public static void windCommands(GameTestHelper helper) throws CommandSyntaxException {
        CommandSourceStack source = source(helper);
        Player player = helper.makeMockSurvivalPlayer();
        IPlayerData data = data(player);
        helper.assertTrue(WindCommands.setPoints(source, List.of(player), 777) == 1 && data.getWindPoints() == 777, "points set");
        helper.assertTrue(WindCommands.getPoints(source, List.of(player)) == 777, "points get 返回数值");
        helper.assertTrue(WindCommands.setWindbound(source, List.of(player), true) == 1 && data.isWindbound(), "windbound set true");
        helper.assertTrue(WindCommands.getWindbound(source, List.of(player)) == 1, "windbound get 返回 1");
        helper.assertTrue(WindCommands.setWindbound(source, List.of(player), false) == 1 && !data.isWindbound(), "windbound set false");
        helper.succeed();
    }
}
