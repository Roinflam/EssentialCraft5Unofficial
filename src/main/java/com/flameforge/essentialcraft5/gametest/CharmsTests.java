package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.item.CharmItem;
import com.flameforge.essentialcraft5.item.CharmsMRU;
import com.flameforge.essentialcraft5.item.MRUConsumableItem;
import com.flameforge.essentialcraft5.item.MagicalWingsItem;
import com.flameforge.essentialcraft5.network.WingsFlight;
import com.flameforge.essentialcraft5.registry.ECCharms;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 护符与随身物品模块（ECCharms）的 GameTest（batch3.md §3.1 的测试点）：
 * 护符的条件与扣费（火、水、地、风、蒸汽、熔岩、生命、沙尘；先扣身上的灵魂储存；没 MRU 不给；被配置关掉不生效；同种只能戴一件）、
 * 三件随身物品（绿宝石之心 +10 生命、猪排、水瓶清效果、MRU 不够时右键不开始吃）、
 * 魔力之翼（没戴时「开」被忽略、开着时按秒预扣、扣不动就叫停、缓降清摔落距离、潜行不缓降、手持飞行、手里那对没电时戴着的照常缓降、
 * 背包里潜行缓降、移速修饰符与中途禁用对账）、十四条配方都在。
 * <p>
 * 模拟玩家（{@code makeMockSurvivalPlayer}）不在世界里 tick，所以直接调物品的 {@code tickWorn / tickWornServer / consume / inventoryTick}；
 * 雷电、雨水护符要改世界天气，会影响同时跑的别的测试，不测（逻辑和别的护符是同一个写法）。
 * 本模块自己的物品直接引用 ECCharms；一批的灵魂球引用 ECItems。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CharmsTests {

    private static final String EMPTY = "empty";
    private static final double EPSILON = 1.0E-5D;

    private CharmsTests() {
    }

    // ---------------------------------------------------------------- 工具

    private static ItemStack charged(Item item, int mru) {
        ItemStack stack = new ItemStack(item);
        CharmsMRU.setStoredMRU(stack, mru);
        return stack;
    }

    /** 生存模式的模拟玩家，放在测试区域的相对坐标 rel（脚底） */
    private static Player survivor(GameTestHelper helper, Vec3 rel) {
        Player player = helper.makeMockSurvivalPlayer();
        Vec3 at = helper.absoluteVec(rel);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static Player survivor(GameTestHelper helper) {
        return survivor(helper, new Vec3(2.5D, 1D, 2.5D));
    }

    /** 放进某种饰品槽的第 0 格，并让 CuriosCompat 的缓存作废 */
    private static void wear(GameTestHelper helper, Player player, String slot, ItemStack stack) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (inventory == null) {
            helper.fail("模拟玩家没有 Curios 饰品栏");
            return;
        }
        ICurioStacksHandler handler = inventory.getCurios().get(slot);
        if (handler == null || handler.getSlots() < 1) {
            helper.fail("玩家没有 Curios 槽位 " + slot);
            return;
        }
        handler.getStacks().setStackInSlot(0, stack);
        CuriosCompat.invalidate(player);
    }

    private static CharmItem charm(CharmItem.Kind kind) {
        return switch (kind) {
            case FIRE -> ECCharms.FIRE_CHARM.get();
            case WATER -> ECCharms.WATER_CHARM.get();
            case EARTH -> ECCharms.EARTH_CHARM.get();
            case AIR -> ECCharms.AIR_CHARM.get();
            case STEAM -> ECCharms.STEAM_CHARM.get();
            case MAGMA -> ECCharms.MAGMA_CHARM.get();
            case LIGHTNING -> ECCharms.LIGHTNING_CHARM.get();
            case LIFE -> ECCharms.LIFE_CHARM.get();
            case RAIN -> ECCharms.RAIN_CHARM.get();
            case DUST -> ECCharms.DUST_CHARM.get();
        };
    }

    private static int amplifier(Player player, net.minecraft.world.effect.MobEffect effect) {
        MobEffectInstance instance = player.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    // ---------------------------------------------------------------- 护符

    /** 火之护符：着火时灭火、扣 50；有抗火时不管、不扣；自己没 MRU 也没带储存时不灭 */
    @GameTest(template = EMPTY)
    public static void fireCharmPutsOutFire(GameTestHelper helper) {
        CharmItem fire = ECCharms.FIRE_CHARM.get();
        Player player = survivor(helper);
        ItemStack stack = charged(fire, 5000);

        player.setRemainingFireTicks(100);
        helper.assertTrue(fire.tickWorn(player, stack), "着火时应触发");
        helper.assertFalse(player.isOnFire(), "应已灭火");
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 5000 - CharmItem.FIRE_COST, "应扣 50 MRU，剩 " + CharmsMRU.storedMRU(stack));

        player.setRemainingFireTicks(100);
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100));
        helper.assertFalse(fire.tickWorn(player, stack), "有抗火时不应触发");
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 5000 - CharmItem.FIRE_COST, "有抗火时不应扣 MRU");

        Player broke = survivor(helper);
        ItemStack empty = charged(fire, 0);
        broke.setRemainingFireTicks(100);
        helper.assertFalse(fire.tickWorn(broke, empty), "没 MRU 时不应触发");
        helper.assertTrue(broke.isOnFire(), "没 MRU 时不应灭火");
        helper.succeed();
    }

    /** 水之护符：在水里、氧气低于 10 时补回 100、扣 100；氧气还多时不管 */
    @GameTest(template = EMPTY)
    public static void waterCharmRefillsAir(GameTestHelper helper) {
        helper.setBlock(2, 1, 2, Blocks.WATER);
        helper.setBlock(2, 2, 2, Blocks.WATER);
        CharmItem water = ECCharms.WATER_CHARM.get();
        Player player = survivor(helper);
        ItemStack stack = charged(water, 5000);

        player.setAirSupply(50);
        helper.assertFalse(water.tickWorn(player, stack), "氧气还有 50 时不应触发");
        player.setAirSupply(5);
        helper.assertTrue(water.tickWorn(player, stack), "氧气低于 10 时应触发");
        helper.assertTrue(player.getAirSupply() == CharmItem.WATER_AIR_REFILL, "氧气应补回 100，实际 " + player.getAirSupply());
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 5000 - CharmItem.WATER_COST, "应扣 100 MRU");

        Player dry = survivor(helper, new Vec3(4.5D, 1D, 4.5D));
        dry.setAirSupply(5);
        helper.assertFalse(water.tickWorn(dry, charged(water, 5000)), "不在水里时不应触发");
        helper.succeed();
    }

    /** 地、风、蒸汽、生命护符的条件、效果等级和扣费 */
    @GameTest(template = EMPTY)
    public static void effectCharms(GameTestHelper helper) {
        Player hurt = survivor(helper);
        ItemStack earth = charged(charm(CharmItem.Kind.EARTH), 5000);
        helper.assertFalse(charm(CharmItem.Kind.EARTH).tickWorn(hurt, earth), "没受伤时地之护符不应触发");
        hurt.hurtTime = 5;
        helper.assertTrue(charm(CharmItem.Kind.EARTH).tickWorn(hurt, earth), "受伤时地之护符应触发");
        helper.assertTrue(amplifier(hurt, MobEffects.DAMAGE_RESISTANCE) == 0, "应给抗性提升 I");
        helper.assertTrue(CharmsMRU.storedMRU(earth) == 5000 - CharmItem.EARTH_COST, "地之护符应扣 200");

        Player runner = survivor(helper);
        ItemStack air = charged(charm(CharmItem.Kind.AIR), 5000);
        runner.setSprinting(true);
        helper.assertTrue(charm(CharmItem.Kind.AIR).tickWorn(runner, air), "疾跑时风之护符应触发");
        helper.assertTrue(amplifier(runner, MobEffects.MOVEMENT_SPEED) == 1, "应给速度 II");
        helper.assertFalse(charm(CharmItem.Kind.AIR).tickWorn(runner, air), "已有速度时不应再扣");
        helper.assertTrue(CharmsMRU.storedMRU(air) == 5000 - CharmItem.AIR_COST, "风之护符只应扣一次 10");

        Player dying = survivor(helper);
        ItemStack steam = charged(charm(CharmItem.Kind.STEAM), 5000);
        dying.setHealth(3F);
        helper.assertTrue(charm(CharmItem.Kind.STEAM).tickWorn(dying, steam), "生命低于 5 时蒸汽护符应触发");
        helper.assertTrue(amplifier(dying, MobEffects.MOVEMENT_SPEED) == 5, "应给速度 VI");

        Player low = survivor(helper);
        ItemStack life = charged(charm(CharmItem.Kind.LIFE), 5000);
        low.setHealth(4F);
        helper.assertTrue(charm(CharmItem.Kind.LIFE).tickWorn(low, life), "生命低于 5 时生命护符应触发");
        helper.assertTrue(amplifier(low, MobEffects.REGENERATION) == 1, "生命低于 5 时应给生命恢复 II");
        helper.assertTrue(CharmsMRU.storedMRU(life) == 5000 - CharmItem.LIFE_CRITICAL_COST, "重伤档应只扣 200（第二段看到已有生命恢复不再扣）");
        low.removeAllEffects();
        low.setHealth(10F);
        helper.assertTrue(charm(CharmItem.Kind.LIFE).tickWorn(low, life), "生命低于 20 时生命护符应触发");
        helper.assertTrue(amplifier(low, MobEffects.REGENERATION) == 0, "生命低于 20 时应给生命恢复 I");
        helper.assertTrue(CharmsMRU.storedMRU(life) == 5000 - CharmItem.LIFE_CRITICAL_COST - CharmItem.LIFE_COST, "轻伤档应扣 50");
        helper.succeed();
    }

    /** 熔岩护符：脚所在格是岩浆时给抗火、扣 100（原作坐标算错一格，这里看玩家自己的格子） */
    @GameTest(template = EMPTY)
    public static void magmaCharmInLava(GameTestHelper helper) {
        helper.setBlock(2, 0, 2, Blocks.STONE);
        helper.setBlock(2, 1, 2, Blocks.LAVA);
        CharmItem magma = ECCharms.MAGMA_CHARM.get();
        Player player = survivor(helper);
        ItemStack stack = charged(magma, 5000);
        helper.assertTrue(magma.tickWorn(player, stack), "脚下是岩浆时应触发");
        helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE), "应给抗火");
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 5000 - CharmItem.MAGMA_COST, "应扣 100");

        Player away = survivor(helper, new Vec3(4.5D, 1D, 0.5D));
        helper.assertFalse(magma.tickWorn(away, charged(magma, 5000)), "离岩浆远时不应触发");
        helper.succeed();
    }

    /** 沙尘护符：站在红沙、沙砾上触发（原作 Material.SAND 的成员），站在石头上不触发 */
    @GameTest(template = EMPTY)
    public static void dustCharmOnSandLikeGround(GameTestHelper helper) {
        CharmItem dust = ECCharms.DUST_CHARM.get();
        BlockState[] triggers = {Blocks.RED_SAND.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState()};
        for (int i = 0; i < triggers.length; i++) {
            int x = 1 + i;
            helper.setBlock(x, 0, 3, Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, 3), triggers[i]);
            Player player = survivor(helper, new Vec3(x + 0.5D, 2D, 3.5D));
            ItemStack stack = charged(dust, 5000);
            helper.assertTrue(player.getBlockStateOn().is(triggers[i].getBlock()), "玩家应站在 " + triggers[i] + " 上，实际 " + player.getBlockStateOn());
            helper.assertTrue(dust.tickWorn(player, stack), "站在 " + triggers[i].getBlock() + " 上应触发");
            helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_RESISTANCE) && player.hasEffect(MobEffects.REGENERATION), "应给抗性提升和生命恢复");
            helper.assertTrue(CharmsMRU.storedMRU(stack) == 5000 - CharmItem.DUST_COST, "应扣 100");
        }
        helper.setBlock(1, 1, 1, Blocks.STONE);
        Player onStone = survivor(helper, new Vec3(1.5D, 2D, 1.5D));
        helper.assertFalse(dust.tickWorn(onStone, charged(dust, 5000)), "站在石头上不应触发");
        helper.succeed();
    }

    /** 扣费先扣身上的灵魂储存物品（MRUDrain 的顺序），护符自己的不动 */
    @GameTest(template = EMPTY)
    public static void charmDrainsSoulStorageFirst(GameTestHelper helper) {
        CharmItem fire = ECCharms.FIRE_CHARM.get();
        Player player = survivor(helper);
        ItemStack sphere = charged(ECItems.SOUL_SPHERE.get(), 1000);
        player.getInventory().setItem(9, sphere);
        ItemStack stack = charged(fire, 5000);
        player.setRemainingFireTicks(100);
        helper.assertTrue(fire.tickWorn(player, stack), "应触发");
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 5000, "护符自己的 MRU 不应动，实际 " + CharmsMRU.storedMRU(stack));
        helper.assertTrue(CharmsMRU.storedMRU(sphere) == 1000 - CharmItem.FIRE_COST, "应从灵魂球扣 50，实际 " + CharmsMRU.storedMRU(sphere));
        helper.succeed();
    }

    /** 被 general.baubles.disabled 关掉的护符戴着不生效（用测试覆盖值，不改真配置） */
    @GameTest(template = EMPTY)
    public static void disabledCharmDoesNothing(GameTestHelper helper) {
        CharmItem fire = ECCharms.FIRE_CHARM.get();
        Player player = survivor(helper);
        ItemStack stack = charged(fire, 5000);
        player.setRemainingFireTicks(100);
        try {
            CuriosCompat.Config.setDisabledForTest(List.of(EssentialCraft5.id("fire_charm").toString()));
            helper.assertFalse(fire.tickWorn(player, stack), "被禁用的护符不应触发");
            helper.assertTrue(player.isOnFire() && CharmsMRU.storedMRU(stack) == 5000, "被禁用时不应灭火、不应扣 MRU");
        } finally {
            CuriosCompat.Config.setDisabledForTest(null);
        }
        helper.assertTrue(fire.tickWorn(player, stack), "解除禁用后应触发");
        helper.succeed();
    }

    /** 同种护符只能戴一件，不同种的不拦；护符进项链槽 */
    @GameTest(template = EMPTY)
    public static void charmOnlyOneOfAKind(GameTestHelper helper) {
        Player player = survivor(helper);
        wear(helper, player, CuriosCompat.NECKLACE, charged(ECCharms.FIRE_CHARM.get(), 0));
        SlotContext another = new SlotContext(CuriosCompat.RING, player, 0, false, true);
        helper.assertFalse(ECCharms.FIRE_CHARM.get().canEquip(another, new ItemStack(ECCharms.FIRE_CHARM.get())), "已经戴着火之护符时不应再戴第二件");
        helper.assertTrue(ECCharms.WATER_CHARM.get().canEquip(another, new ItemStack(ECCharms.WATER_CHARM.get())), "不同种的护符不应被拦");
        helper.assertTrue(new ItemStack(ECCharms.FIRE_CHARM.get()).is(ECCharms.CHARMS), "护符应在 essentialcraft5:charms 标签里（先跑 runData）");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 随身物品

    /** 绿宝石之心：+10 生命、扣 500，物品不消耗；没 MRU 时不回血，右键直接失败（不开始吃） */
    @GameTest(template = EMPTY)
    public static void emeraldHeartHeals(GameTestHelper helper) {
        MRUConsumableItem heart = ECCharms.EMERALD_HEART.get();
        Player player = survivor(helper);
        player.setHealth(5F);
        ItemStack stack = charged(heart, 5000);
        helper.assertTrue(heart.consume(player, stack), "有 MRU 时应生效");
        helper.assertTrue(Math.abs(player.getHealth() - 15F) < EPSILON, "应回到 15 生命，实际 " + player.getHealth());
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 5000 - MRUConsumableItem.HEART_COST, "应扣 500");
        helper.assertTrue(heart.finishUsingItem(stack, helper.getLevel(), player) == stack && !stack.isEmpty(), "用完物品应还在");

        Player broke = survivor(helper);
        broke.setHealth(5F);
        ItemStack empty = charged(heart, 0);
        helper.assertFalse(heart.consume(broke, empty), "没 MRU 时不应生效");
        helper.assertTrue(Math.abs(broke.getHealth() - 5F) < EPSILON, "没 MRU 时不应回血");
        broke.setItemInHand(InteractionHand.MAIN_HAND, empty);
        InteractionResult result = heart.use(helper.getLevel(), broke, InteractionHand.MAIN_HAND).getResult();
        helper.assertTrue(result == InteractionResult.FAIL, "没 MRU 时右键应失败，实际 " + result);
        helper.assertFalse(broke.isUsingItem(), "没 MRU 时不应开始吃");
        helper.succeed();
    }

    /** 魔力猪排：饥饿 +5、饱和度 +30 但不超过饥饿值（FoodData.eat(5, 3)），扣 1500 */
    @GameTest(template = EMPTY)
    public static void magicalPorkchopFeeds(GameTestHelper helper) {
        MRUConsumableItem pork = ECCharms.MAGICAL_PORKCHOP.get();
        Player player = survivor(helper);
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0F);
        ItemStack stack = charged(pork, 5000);
        helper.assertTrue(pork.consume(player, stack), "应生效");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 15, "饥饿值应为 15，实际 " + player.getFoodData().getFoodLevel());
        helper.assertTrue(Math.abs(player.getFoodData().getSaturationLevel() - 15F) < EPSILON,
                "饱和度应补到和饥饿值一样（15），实际 " + player.getFoodData().getSaturationLevel());
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 5000 - MRUConsumableItem.PORKCHOP_COST, "应扣 1500");
        helper.succeed();
    }

    /** 魔力水瓶：好的坏的效果全清，扣 500 */
    @GameTest(template = EMPTY)
    public static void magicWaterBottleClearsEffects(GameTestHelper helper) {
        MRUConsumableItem bottle = ECCharms.MAGIC_WATER_BOTTLE.get();
        Player player = survivor(helper);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200));
        ItemStack stack = charged(bottle, 5000);
        helper.assertTrue(bottle.consume(player, stack), "应生效");
        helper.assertTrue(player.getActiveEffects().isEmpty(), "效果应全部清掉，还剩 " + player.getActiveEffects());
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 5000 - MRUConsumableItem.BOTTLE_COST, "应扣 500");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 魔力之翼

    /**
     * 没戴翅膀时「开」被忽略；戴着时记下，每刻结算：第 1 刻预扣 20、之后 19 刻不扣、第 21 刻再扣 20（平均每刻 1）；
     * MRU 用完、预付也用完时叫停。
     */
    @GameTest(template = EMPTY)
    public static void wingsFlightChargesAndStops(GameTestHelper helper) {
        MagicalWingsItem wings = ECCharms.MAGICAL_WINGS.get();
        Player player = survivor(helper);
        WingsFlight.handleToggle(player, true);
        helper.assertFalse(WingsFlight.isActive(player), "没戴翅膀时「开」应被忽略");

        ItemStack stack = charged(wings, 100);
        wear(helper, player, CuriosCompat.BELT, stack);
        helper.assertTrue(WingsFlight.isWearingWings(player), "腰带槽里的魔力之翼应算戴着");
        WingsFlight.handleToggle(player, true);
        helper.assertTrue(WingsFlight.isActive(player), "戴着翅膀时「开」应记下");

        helper.assertTrue(MagicalWingsItem.tickWornServer(player, stack), "开着时应结算");
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 100 - MagicalWingsItem.PREPAY_TICKS, "第 1 刻应预扣 20，剩 " + CharmsMRU.storedMRU(stack));
        helper.assertTrue(MagicalWingsItem.prepaidTicks(player) == MagicalWingsItem.PREPAY_TICKS - 1,
                "应还预付着 19 刻，实际 " + MagicalWingsItem.prepaidTicks(player));
        for (int i = 1; i < MagicalWingsItem.PREPAY_TICKS; i++) {
            MagicalWingsItem.tickWornServer(player, stack);
        }
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 80, "预付的 20 刻里不应再扣，剩 " + CharmsMRU.storedMRU(stack));
        MagicalWingsItem.tickWornServer(player, stack);
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 60, "第 21 刻应再扣 20，剩 " + CharmsMRU.storedMRU(stack));

        CharmsMRU.setStoredMRU(stack, 0);
        for (int i = 1; i < MagicalWingsItem.PREPAY_TICKS; i++) {
            helper.assertTrue(MagicalWingsItem.tickWornServer(player, stack), "预付还没用完时应照常飞");
        }
        helper.assertTrue(WingsFlight.isActive(player), "预付用完之前不应叫停");
        helper.assertFalse(MagicalWingsItem.tickWornServer(player, stack), "MRU 用完应扣不动");
        helper.assertFalse(WingsFlight.isActive(player), "扣不动时应叫停");
        helper.succeed();
    }

    /** 最后不到 20 MRU 时退回按刻扣 1，用得完 */
    @GameTest(template = EMPTY)
    public static void wingsUseLastFewMRU(GameTestHelper helper) {
        Player player = survivor(helper);
        ItemStack stack = charged(ECCharms.MAGICAL_WINGS.get(), 3);
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(MagicalWingsItem.payTick(player, stack, MagicalWingsItem.Use.HELD), "剩 " + (3 - i) + " MRU 时应还能付");
        }
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 0, "应扣光");
        helper.assertFalse(MagicalWingsItem.payTick(player, stack, MagicalWingsItem.Use.HELD), "扣光以后应付不起");
        // 付不起以后 20 刻内不再翻物品栏：这时补上 MRU 也还是付不起（到时间才重试）
        CharmsMRU.setStoredMRU(stack, 100);
        helper.assertFalse(MagicalWingsItem.payTick(player, stack, MagicalWingsItem.Use.HELD), "付不起以后 20 刻内应直接返回 false");
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 100, "等待重试期间不应扣");
        helper.succeed();
    }

    /** 缓降：没在飞、不在地上、摔落距离到阈值 → 扣费并清零；潜行时、摔落距离很小时不管 */
    @GameTest(template = EMPTY)
    public static void wingsSlowFallResetsFallDistance(GameTestHelper helper) {
        Player player = survivor(helper);
        ItemStack stack = charged(ECCharms.MAGICAL_WINGS.get(), 100);
        wear(helper, player, CuriosCompat.BELT, stack);
        player.setOnGround(false);

        player.fallDistance = 0.1F;
        helper.assertFalse(MagicalWingsItem.tickWornServer(player, stack), "摔落距离很小时不应扣");
        player.fallDistance = 2.5F;
        player.setShiftKeyDown(true);
        helper.assertFalse(MagicalWingsItem.tickWornServer(player, stack), "潜行时不应缓降");
        helper.assertTrue(player.fallDistance == 2.5F, "潜行时摔落距离不应清零");
        player.setShiftKeyDown(false);
        helper.assertTrue(MagicalWingsItem.tickWornServer(player, stack), "下落时应缓降");
        helper.assertTrue(player.fallDistance == 0F, "缓降应清掉摔落距离");
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 80, "应预扣 20，剩 " + CharmsMRU.storedMRU(stack));
        helper.assertFalse(WingsFlight.isActive(player), "缓降不应打开往上飞");
        helper.succeed();
    }

    /** 手持：主手那对每刻结算并清摔落距离；副手另一对不重复扣；手里拿着能用的翅膀时戴着的不缓降 */
    @GameTest(template = EMPTY)
    public static void wingsHeldFlight(GameTestHelper helper) {
        MagicalWingsItem wings = ECCharms.MAGICAL_WINGS.get();
        ServerLevel level = helper.getLevel();
        Player player = survivor(helper);
        ItemStack main = charged(wings, 100);
        ItemStack off = charged(wings, 100);
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        player.fallDistance = 3F;
        wings.inventoryTick(main, level, player, 0, true);
        wings.inventoryTick(off, level, player, 40, false);
        helper.assertTrue(CharmsMRU.storedMRU(main) == 80, "主手那对应预扣 20，剩 " + CharmsMRU.storedMRU(main));
        helper.assertTrue(CharmsMRU.storedMRU(off) == 100, "副手那对不应重复扣");
        helper.assertTrue(player.fallDistance == 0F, "手持飞行应清摔落距离");

        ItemStack worn = charged(wings, 100);
        wear(helper, player, CuriosCompat.BELT, worn);
        player.setOnGround(false);
        player.fallDistance = 3F;
        helper.assertFalse(MagicalWingsItem.tickWornServer(player, worn), "手里拿着能用的翅膀时戴着的不应再缓降");
        helper.assertTrue(CharmsMRU.storedMRU(worn) == 100, "戴着的那对不应扣");

        // 饰品槽里的栈也会被 Curios 调 inventoryTick（slot = -1），不是手里那一个，不算手持
        Player wearer = survivor(helper);
        ItemStack belt = charged(wings, 100);
        wear(helper, wearer, CuriosCompat.BELT, belt);
        wings.inventoryTick(belt, level, wearer, -1, false);
        helper.assertTrue(CharmsMRU.storedMRU(belt) == 100, "饰品槽里的翅膀不应按手持扣");
        helper.succeed();
    }

    /** 手里拿着没电的翅膀时，戴着的那对照常缓降；手里那对付不起引起的重试等待不连带戴着的（按手持 / 戴着分开记） */
    @GameTest(template = EMPTY)
    public static void wingsEmptyHeldDoesNotBlockWorn(GameTestHelper helper) {
        MagicalWingsItem wings = ECCharms.MAGICAL_WINGS.get();
        ServerLevel level = helper.getLevel();
        Player player = survivor(helper);
        ItemStack held = charged(wings, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        ItemStack worn = charged(wings, 100);
        wear(helper, player, CuriosCompat.BELT, worn);
        player.setOnGround(false);
        player.fallDistance = 3F;

        wings.inventoryTick(held, level, player, player.getInventory().selected, true);
        helper.assertTrue(player.fallDistance == 3F, "手里那对没电，不应清摔落距离");
        helper.assertFalse(MagicalWingsItem.heldWingsUsable(player), "手里那对没电时不应算能用");
        helper.assertTrue(MagicalWingsItem.tickWornServer(player, worn), "手里那对没电时戴着的应照常缓降（不被手持的重试等待挡住）");
        helper.assertTrue(player.fallDistance == 0F, "戴着的缓降应清掉摔落距离");
        helper.assertTrue(CharmsMRU.storedMRU(worn) == 80, "戴着的那对应预扣 20，剩 " + CharmsMRU.storedMRU(worn));
        helper.assertTrue(CharmsMRU.storedMRU(held) == 0, "手里那对不应变化");
        helper.succeed();
    }

    /**
     * 背包里的翅膀（不拿不戴，原作 onUpdate 第二个分支）：潜行下落时缓降、清摔落距离；放了几对只扣一对；前面没电的那对不挡后面有电的；
     * 不潜行时、饰品槽那一份（slot = -1）不管；手里拿着能用的翅膀时让给手持，手里那对没电时照常。
     */
    @GameTest(template = EMPTY)
    public static void wingsInPackSlowFallWhenSneaking(GameTestHelper helper) {
        MagicalWingsItem wings = ECCharms.MAGICAL_WINGS.get();
        ServerLevel level = helper.getLevel();
        Player player = survivor(helper);
        ItemStack dead = charged(wings, 0);
        ItemStack first = charged(wings, 100);
        ItemStack second = charged(wings, 100);
        player.getInventory().setItem(9, dead);
        player.getInventory().setItem(10, first);
        player.getInventory().setItem(11, second);
        player.setOnGround(false);
        player.fallDistance = 2.5F;

        wings.inventoryTick(first, level, player, 10, false);
        helper.assertTrue(player.fallDistance == 2.5F && CharmsMRU.storedMRU(first) == 100, "不潜行时背包里的翅膀不应缓降、不应扣");
        player.setShiftKeyDown(true);
        wings.inventoryTick(first, level, player, -1, false);
        helper.assertTrue(player.fallDistance == 2.5F && CharmsMRU.storedMRU(first) == 100, "饰品槽那一份（slot = -1）不应按背包算");

        wings.inventoryTick(dead, level, player, 9, false);
        helper.assertTrue(player.fallDistance == 2.5F, "没电的那对不应缓降");
        wings.inventoryTick(first, level, player, 10, false);
        wings.inventoryTick(second, level, player, 11, false);
        helper.assertTrue(player.fallDistance == 0F, "潜行下落时背包里的翅膀应缓降、清摔落距离");
        helper.assertTrue(CharmsMRU.storedMRU(first) == 80, "前面没电的那对不应挡住，有电的第一对应预扣 20，剩 " + CharmsMRU.storedMRU(first));
        helper.assertTrue(CharmsMRU.storedMRU(second) == 100, "后面那对不应重复扣，剩 " + CharmsMRU.storedMRU(second));

        // 手里拿着能用的翅膀：让给手持
        Player holder = survivor(helper);
        ItemStack inHand = charged(wings, 100);
        ItemStack bag = charged(wings, 100);
        holder.setItemInHand(InteractionHand.MAIN_HAND, inHand);
        holder.getInventory().setItem(9, bag);
        holder.setOnGround(false);
        holder.setShiftKeyDown(true);
        holder.fallDistance = 2.5F;
        wings.inventoryTick(bag, level, holder, 9, false);
        helper.assertTrue(holder.fallDistance == 2.5F && CharmsMRU.storedMRU(bag) == 100, "手里拿着能用的翅膀时背包里的不应结算");

        // 手里那对没电：背包里的照常
        CharmsMRU.setStoredMRU(inHand, 0);
        wings.inventoryTick(bag, level, holder, 9, false);
        helper.assertTrue(holder.fallDistance == 0F && CharmsMRU.storedMRU(bag) == 80, "手里那对没电时背包里的应照常缓降，剩 " + CharmsMRU.storedMRU(bag));
        helper.succeed();
    }

    /** 摘下（换成别的）叫停；扣 MRU 引起的「换成同一种」不叫停；移速 +0.1 用 Curios 给的 UUID，被禁用时给 0 */
    @GameTest(template = EMPTY)
    public static void wingsUnequipAndSpeed(GameTestHelper helper) {
        MagicalWingsItem wings = ECCharms.MAGICAL_WINGS.get();
        Player player = survivor(helper);
        ItemStack stack = charged(wings, 100);
        wear(helper, player, CuriosCompat.BELT, stack);
        WingsFlight.handleToggle(player, true);
        SlotContext belt = new SlotContext(CuriosCompat.BELT, player, 0, false, true);
        wings.onUnequip(belt, charged(wings, 99), stack);
        helper.assertTrue(WingsFlight.isActive(player), "NBT 变化（还是翅膀）不应叫停");
        wings.onUnequip(belt, ItemStack.EMPTY, stack);
        helper.assertFalse(WingsFlight.isActive(player), "摘下时应叫停");

        UUID uuid = UUID.randomUUID();
        Multimap<Attribute, AttributeModifier> modifiers = wings.getAttributeModifiers(belt, uuid, stack);
        Collection<AttributeModifier> speed = modifiers.get(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(speed.size() == 1, "应有一条移速修饰符，实际 " + modifiers);
        AttributeModifier modifier = speed.iterator().next();
        helper.assertTrue(modifier.getId().equals(uuid), "应使用 Curios 给的 UUID");
        helper.assertTrue(Math.abs(modifier.getAmount() - MagicalWingsItem.SPEED_BONUS) < EPSILON
                && modifier.getOperation() == AttributeModifier.Operation.ADDITION, "应为 +0.1（加法）");
        try {
            CuriosCompat.Config.setDisabledForTest(List.of(WingsFlight.WINGS_ID.toString()));
            AttributeModifier off = wings.getAttributeModifiers(belt, uuid, stack).get(Attributes.MOVEMENT_SPEED).iterator().next();
            helper.assertTrue(off.getId().equals(uuid) && off.getAmount() == 0D, "被禁用时应给同一 UUID 的 0（能把旧的顶掉）");
        } finally {
            CuriosCompat.Config.setDisabledForTest(null);
        }
        helper.succeed();
    }

    /**
     * 运行中改禁用配置：Curios 只在物品变了时重算槽位修饰符，curioTick 把它已经加上的移速修饰符对到现在应有的值（禁用 0，否则 +0.1）；
     * Curios 还没加过的不自己补，同一 UUID 下别的物品的修饰符（名字不同）不动。
     */
    @GameTest(template = EMPTY)
    public static void wingsSpeedFollowsDisabledConfig(GameTestHelper helper) {
        MagicalWingsItem wings = ECCharms.MAGICAL_WINGS.get();
        Player player = survivor(helper);
        ItemStack stack = charged(wings, 100);
        wear(helper, player, CuriosCompat.BELT, stack);
        SlotContext belt = new SlotContext(CuriosCompat.BELT, player, 0, false, true);
        UUID uuid = CuriosApi.getSlotUuid(belt);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            helper.fail("玩家没有移速属性");
            return;
        }
        speed.removeModifier(uuid);
        wings.curioTick(belt, stack);
        helper.assertTrue(speed.getModifier(uuid) == null, "Curios 没加过的修饰符不应自己补上");

        // 模拟 Curios 戴上时加好的 +0.1
        speed.addTransientModifier(new AttributeModifier(uuid, MagicalWingsItem.SPEED_MODIFIER_NAME, MagicalWingsItem.SPEED_BONUS,
                AttributeModifier.Operation.ADDITION));
        try {
            CuriosCompat.Config.setDisabledForTest(List.of(WingsFlight.WINGS_ID.toString()));
            wings.curioTick(belt, stack);
            AttributeModifier off = speed.getModifier(uuid);
            helper.assertTrue(off != null && off.getAmount() == 0D, "中途禁用后移速加成应变成 0，实际 " + off);
        } finally {
            CuriosCompat.Config.setDisabledForTest(null);
        }
        wings.curioTick(belt, stack);
        AttributeModifier on = speed.getModifier(uuid);
        helper.assertTrue(on != null && Math.abs(on.getAmount() - MagicalWingsItem.SPEED_BONUS) < EPSILON
                && on.getOperation() == AttributeModifier.Operation.ADDITION, "解禁后应恢复 +0.1，实际 " + on);
        helper.assertTrue(CharmsMRU.storedMRU(stack) == 100, "对账不应扣 MRU");

        // 同一个 UUID 下不是本物品的修饰符（名字不同）不动
        speed.removeModifier(uuid);
        speed.addTransientModifier(new AttributeModifier(uuid, "other", 0.5D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        wings.curioTick(belt, stack);
        AttributeModifier other = speed.getModifier(uuid);
        helper.assertTrue(other != null && other.getAmount() == 0.5D, "别的物品留下的修饰符不应被改，实际 " + other);
        speed.removeModifier(uuid);
        helper.succeed();
    }

    // ---------------------------------------------------------------- 配方与标签

    /** 14 件都有工作台配方（配方 JSON 写错时 RecipeManager 里就没有）；沙尘护符的地面标签有沙砾、灵魂沙、混凝土粉末、没有石头 */
    @GameTest(template = EMPTY)
    public static void recipesAndTags(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<CraftingRecipe> recipes = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        String[] ids = {"fire_charm", "water_charm", "earth_charm", "air_charm", "steam_charm", "magma_charm", "lightning_charm",
                "life_charm", "rain_charm", "dust_charm", "emerald_heart", "magical_porkchop", "magic_water_bottle", "magical_wings"};
        for (String id : ids) {
            ResourceLocation rl = EssentialCraft5.id(id);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            boolean found = recipes.stream().anyMatch(r -> r.getResultItem(level.registryAccess()).is(item));
            helper.assertTrue(found, "缺少 " + rl + " 的工作台配方");
        }
        helper.assertTrue(Blocks.GRAVEL.defaultBlockState().is(ECCharms.DUST_CHARM_GROUND)
                        && Blocks.SOUL_SAND.defaultBlockState().is(ECCharms.DUST_CHARM_GROUND)
                        && Blocks.RED_SAND.defaultBlockState().is(ECCharms.DUST_CHARM_GROUND)
                        && Blocks.LIME_CONCRETE_POWDER.defaultBlockState().is(ECCharms.DUST_CHARM_GROUND),
                "沙尘护符的地面标签应含红沙、沙砾、灵魂沙、混凝土粉末（先跑 runData）");
        helper.assertFalse(Blocks.STONE.defaultBlockState().is(ECCharms.DUST_CHARM_GROUND), "石头不应在沙尘护符的地面标签里");
        helper.succeed();
    }
}
