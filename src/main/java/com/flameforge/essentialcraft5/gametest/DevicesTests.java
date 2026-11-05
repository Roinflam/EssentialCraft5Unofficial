package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicalEnchanterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalJukeboxBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalRepairerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PotionSpreaderBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 魔力器械（修复器、附魔台、药水扩散器、唱片机）的自动化测试（./gradlew runGameTestServer）。
 * MRU 直接充满或设成指定值，不接绑定宝石（拉 MRU 在框架测试里测过）。
 * 扩散器的测试用 11×8×11 的大模板、机器放正中：作用范围 8 格，不会碰到相邻测试里的生物。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class DevicesTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";

    private DevicesTests() {
    }

    private static IItemHandler automation(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("机器应提供物品能力"));
    }

    // ------------------------------------------------------------------
    // 魔力修复器
    // ------------------------------------------------------------------

    private static MagicalRepairerBlockEntity placeRepairer(GameTestHelper helper, BlockPos pos, int mru) {
        helper.setBlock(pos, ECDevices.MAGICAL_REPAIRER.get());
        MagicalRepairerBlockEntity be = (MagicalRepairerBlockEntity) helper.getBlockEntity(pos);
        be.getMRUStorage().setMRU(mru);
        return be;
    }

    /** 每 tick 修 1 点、每点 500 MRU：5000 MRU 正好修 10 点，然后停在「MRU 不足」 */
    @GameTest(template = EMPTY)
    public static void repairerUses500MRUPerPoint(GameTestHelper helper) {
        MagicalRepairerBlockEntity be = placeRepairer(helper, new BlockPos(2, 1, 2), 5000);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.setDamageValue(100);
        be.getInventory().setStackInSlot(MagicalRepairerBlockEntity.SLOT_ITEM, pickaxe);

        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    ItemStack stack = be.getInventory().getStackInSlot(MagicalRepairerBlockEntity.SLOT_ITEM);
                    int repaired = 100 - stack.getDamageValue();
                    int used = 5000 - be.getMRUStorage().getMRU();
                    helper.assertTrue(repaired >= 1 && repaired <= 3, "3 tick 内应修 1~3 点，实际 " + repaired);
                    helper.assertTrue(used == 500 * repaired, "每点应耗 500 MRU：修了 " + repaired + " 点，耗了 " + used);
                })
                .thenExecuteAfter(20, () -> {
                    ItemStack stack = be.getInventory().getStackInSlot(MagicalRepairerBlockEntity.SLOT_ITEM);
                    helper.assertTrue(stack.getDamageValue() == 90, "5000 MRU 应正好修 10 点，剩余损耗 " + stack.getDamageValue());
                    helper.assertTrue(be.getMRUStorage().getMRU() == 0, "MRU 应用光，实际 " + be.getMRUStorage().getMRU());
                    helper.assertTrue(be.statusCode() == MagicalRepairerBlockEntity.STATUS_NO_MRU, "应显示 MRU 不足，状态 " + be.statusCode());
                })
                .thenSucceed();
    }

    /** 漏斗拿不走没修好的工具；修好的、修不了的能拿走；只往里送要修的东西 */
    @GameTest(template = EMPTY)
    public static void repairerHopperTakesOnlyFinished(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MagicalRepairerBlockEntity be = placeRepairer(helper, pos, 0);
        IItemHandler handler = automation(helper, pos);
        int slot = MagicalRepairerBlockEntity.SLOT_ITEM;

        ItemStack sound = new ItemStack(Items.IRON_PICKAXE);
        helper.assertTrue(handler.insertItem(slot, sound, true).getCount() == 1, "没有损耗的镐不应被送进来");
        ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(5);
        helper.assertTrue(handler.insertItem(slot, damaged, false).isEmpty(), "有损耗的镐应能送进来");
        helper.assertTrue(handler.extractItem(slot, 1, false).isEmpty(), "没修好的镐不应被抽走");

        be.getInventory().getStackInSlot(slot).setDamageValue(0);
        helper.assertTrue(handler.extractItem(slot, 1, false).is(Items.IRON_PICKAXE), "修好的镐应能被抽走");

        be.getInventory().setStackInSlot(slot, new ItemStack(Items.DIRT));
        helper.assertTrue(handler.extractItem(slot, 1, false).is(Items.DIRT), "修不了的东西应能被抽走");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 魔力附魔台
    // ------------------------------------------------------------------

    /** 在 (2,1,2) 放附魔台、(0,1,2) 放一个书架：等级 1，附一件 10 tick、1000 MRU */
    private static MagicalEnchanterBlockEntity placeEnchanter(GameTestHelper helper, int mru) {
        helper.setBlock(new BlockPos(0, 1, 2), Blocks.BOOKSHELF);
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECDevices.MAGICAL_ENCHANTER.get());
        MagicalEnchanterBlockEntity be = (MagicalEnchanterBlockEntity) helper.getBlockEntity(pos);
        be.getMRUStorage().setMRU(mru);
        return be;
    }

    private static Map<Enchantment, Integer> asMap(List<EnchantmentInstance> list) {
        Map<Enchantment, Integer> map = new HashMap<>();
        list.forEach(instance -> map.put(instance.enchantment, instance.level));
        return map;
    }

    /** 一叠书：出正常的附魔书（StoredEnchantments），每次只用一本；等级 1 正好耗 1000 MRU */
    @GameTest(template = EMPTY)
    public static void enchanterMakesStoredEnchantmentBooks(GameTestHelper helper) {
        MagicalEnchanterBlockEntity be = placeEnchanter(helper, 5000);
        be.getInventory().setStackInSlot(MagicalEnchanterBlockEntity.SLOT_INPUT, new ItemStack(Items.BOOK, 3));

        helper.succeedWhen(() -> {
            ItemStack output = be.getInventory().getStackInSlot(MagicalEnchanterBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(output.is(Items.ENCHANTED_BOOK), "输出应是附魔书，实际 " + output);
            helper.assertTrue(!EnchantedBookItem.getEnchantments(output).isEmpty(), "附魔应写进 StoredEnchantments");
            CompoundTag tag = output.getTag();
            helper.assertTrue(tag != null && !tag.contains(ItemStack.TAG_ENCH), "附魔书不应带 Enchantments（铁砧认不出）");
            helper.assertTrue(be.getInventory().getStackInSlot(MagicalEnchanterBlockEntity.SLOT_INPUT).getCount() == 2,
                    "一叠 3 本书应只用掉 1 本");
            int used = 5000 - be.getMRUStorage().getMRU();
            helper.assertTrue(used == 1000, "等级 1 应正好耗 1000 MRU，实际 " + used);
        });
    }

    /** MRU 断供时暂停：进度和抽到的附魔都留着，来了 MRU 接着做，出来的正是开工时抽到的那些 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void enchanterPausesWithoutMRU(GameTestHelper helper) {
        MagicalEnchanterBlockEntity be = placeEnchanter(helper, 300);
        be.getInventory().setStackInSlot(MagicalEnchanterBlockEntity.SLOT_INPUT, new ItemStack(Items.BOOK));
        Map<Enchantment, Integer> rolled = new HashMap<>();

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(be.getProgress() == 3, "300 MRU 应只够做 3 tick，进度 " + be.getProgress());
                    helper.assertTrue(be.statusCode() == MagicalEnchanterBlockEntity.STATUS_NO_MRU, "应停在 MRU 不足，状态 " + be.statusCode());
                    helper.assertTrue(!be.rolledEnchantments().isEmpty(), "断供时抽到的附魔应留着");
                    helper.assertTrue(be.getInventory().getStackInSlot(MagicalEnchanterBlockEntity.SLOT_OUTPUT).isEmpty(), "还不应出货");
                    // 抽到的附魔和进度只存盘，不进发给客户端的更新包（改过的客户端不能先看结果再重抽）
                    CompoundTag update = be.getUpdateTag();
                    helper.assertTrue(!update.contains("rolled_enchantments") && !update.contains("rolled_for")
                                    && !update.contains("progress") && !update.contains("level"),
                            "更新包不应带抽到的附魔和进度：" + update.getAllKeys());
                    helper.assertTrue(be.saveWithoutMetadata().contains("rolled_enchantments"), "存盘应照写抽到的附魔");
                    rolled.putAll(asMap(be.rolledEnchantments()));
                    be.getMRUStorage().setMRU(5000);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        be.getInventory().getStackInSlot(MagicalEnchanterBlockEntity.SLOT_OUTPUT).is(Items.ENCHANTED_BOOK), "应做出附魔书"))
                .thenExecute(() -> {
                    Map<Enchantment, Integer> result = EnchantmentHelper.getEnchantments(
                            be.getInventory().getStackInSlot(MagicalEnchanterBlockEntity.SLOT_OUTPUT));
                    helper.assertTrue(result.equals(rolled), "出来的附魔应就是开工时抽到的：" + result + " 对 " + rolled);
                    int used = 5000 - be.getMRUStorage().getMRU();
                    helper.assertTrue(used == 700, "补上 MRU 后应只再耗剩下 7 tick 的 700，实际 " + used);
                })
                .thenSucceed();
    }

    /**
     * 小数附魔能力（别的模组的方块）：照 scanPower 的写法把 float 逐格加进 double，再换算成等级，不能少 1 级。
     * 0.7F × 10 在 double 里是 6.99999988，没有取整前的小量就成了 6 级。
     */
    @GameTest(template = EMPTY)
    public static void enchanterFractionalPowerNotShortByOne(GameTestHelper helper) {
        float[] values = {0.1F, 0.2F, 0.3F, 0.7F, 1F};
        int[] counts = {30, 25, 10, 10, 74};
        int[] expected = {3, 5, 3, 7, 74};
        for (int i = 0; i < values.length; i++) {
            double sum = 0D;
            for (int n = 0; n < counts[i]; n++) {
                sum += values[i];
            }
            int level = MagicalEnchanterBlockEntity.powerToLevel(sum);
            helper.assertTrue(level == expected[i], counts[i] + " 格 " + values[i] + " 应是 " + expected[i] + " 级，实际 " + level);
        }
        helper.assertTrue(MagicalEnchanterBlockEntity.powerToLevel(2.5D) == 2, "2.5 应向下取整成 2 级");
        helper.assertTrue(MagicalEnchanterBlockEntity.powerToLevel(0D) == 0, "没有书架应是 0 级");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 药水扩散器
    // ------------------------------------------------------------------

    private static final BlockPos SPREADER = new BlockPos(5, 1, 5);

    private static PotionSpreaderBlockEntity placeSpreader(GameTestHelper helper, int mru) {
        helper.setBlock(SPREADER, ECDevices.POTION_SPREADER.get());
        PotionSpreaderBlockEntity be = (PotionSpreaderBlockEntity) helper.getBlockEntity(SPREADER);
        be.getMRUStorage().setMRU(mru);
        return be;
    }

    /** D9：瞬间伤害、凋零（默认配置）、没有效果的药水放不进去；治疗、缓慢、喷溅药水能放 */
    @GameTest(template = EMPTY)
    public static void spreaderRejectsForbiddenPotions(GameTestHelper helper) {
        int slot = PotionSpreaderBlockEntity.FIRST_POTION;
        helper.assertFalse(PotionSpreaderBlockEntity.slotRule(slot, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HARMING)),
                "瞬间伤害药水不应能放进去");
        helper.assertFalse(PotionSpreaderBlockEntity.slotRule(slot, PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.STRONG_HARMING)),
                "瞬间伤害 II 喷溅药水不应能放进去");
        helper.assertFalse(PotionSpreaderBlockEntity.slotRule(slot, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER)),
                "水瓶没有效果，不应能放进去");
        ItemStack wither = PotionUtils.setCustomEffects(new ItemStack(Items.POTION), List.of(new MobEffectInstance(MobEffects.WITHER, 200)));
        helper.assertFalse(PotionSpreaderBlockEntity.slotRule(slot, wither), "凋零药水（默认配置禁用）不应能放进去");
        helper.assertTrue(PotionSpreaderBlockEntity.slotRule(slot, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING)),
                "瞬间治疗药水应能放进去");
        helper.assertTrue(PotionSpreaderBlockEntity.slotRule(slot, PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.SLOWNESS)),
                "缓慢喷溅药水应能放进去");
        helper.assertFalse(PotionSpreaderBlockEntity.slotRule(slot, new ItemStack(Items.DIRT)), "不是药水不应能放进去");

        Player player = helper.makeMockPlayer();
        MobEffectInstance slowness = new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600);
        MobEffectInstance speed = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600);
        helper.assertFalse(PotionSpreaderBlockEntity.shouldApply(player, slowness), "有害效果不应作用于玩家");
        helper.assertTrue(PotionSpreaderBlockEntity.shouldApply(player, speed), "有益效果应作用于玩家");
        helper.succeed();
    }

    /**
     * 第十批 A3：有害效果只给敌对生物——僵尸给；猪、村民、驯服的狼、起了名字的僵尸、玩家不给；有益效果照旧谁都给
     */
    @GameTest(template = EMPTY)
    public static void spreaderHarmfulOnlyForHostiles(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        Zombie named = EntityType.ZOMBIE.create(helper.getLevel());
        Pig pig = EntityType.PIG.create(helper.getLevel());
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        helper.assertTrue(zombie != null && named != null && pig != null && villager != null && wolf != null, "造不出生物");
        named.setCustomName(Component.literal("Bob"));
        wolf.setTame(true);
        wolf.setOwnerUUID(UUID.randomUUID());
        Player player = helper.makeMockSurvivalPlayer();
        MobEffectInstance slowness = new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600);
        MobEffectInstance poison = new MobEffectInstance(MobEffects.POISON, 600);
        MobEffectInstance speed = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600);
        helper.assertTrue(PotionSpreaderBlockEntity.shouldApply(zombie, slowness) && PotionSpreaderBlockEntity.mayReceiveHarmful(zombie),
                "敌对生物应得到有害效果");
        for (net.minecraft.world.entity.LivingEntity spared : List.of(named, pig, villager, wolf, player)) {
            helper.assertFalse(PotionSpreaderBlockEntity.shouldApply(spared, slowness) || PotionSpreaderBlockEntity.shouldApply(spared, poison),
                    spared.getType() + " 不应得到有害效果");
            helper.assertTrue(PotionSpreaderBlockEntity.shouldApply(spared, speed), spared.getType() + " 应照常得到有益效果");
        }
        helper.succeed();
    }

    /** 多效果全用（每种各 250 MRU）；有害效果不给猪（第十批 A3）；一次加上就用掉一次 */
    @GameTest(template = EMPTY_LARGE)
    public static void spreaderAppliesAllEffects(GameTestHelper helper) {
        PotionSpreaderBlockEntity be = placeSpreader(helper, 5000);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 5));
        ItemStack potion = PotionUtils.setCustomEffects(new ItemStack(Items.POTION), List.of(
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200),
                new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200),
                new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200)));
        be.getInventory().setStackInSlot(PotionSpreaderBlockEntity.FIRST_POTION, potion);

        helper.succeedWhen(() -> {
            helper.assertTrue(pig.hasEffect(MobEffects.MOVEMENT_SPEED), "猪应得到速度");
            helper.assertTrue(pig.hasEffect(MobEffects.DAMAGE_BOOST), "猪应得到力量（第二个效果也要用上）");
            helper.assertFalse(pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "猪不应得到缓慢（有害效果只给敌对生物）");
            int used = 5000 - be.getMRUStorage().getMRU();
            helper.assertTrue(used == 500, "猪只加上两种有益效果、各 250，应耗 500 MRU，实际 " + used);
            helper.assertTrue(be.usesLeft() == be.maxUses() - 1, "加上一次应用掉一次，剩 " + be.usesLeft() + "/" + be.maxUses());
            helper.assertTrue(be.getInventory().getStackInSlot(PotionSpreaderBlockEntity.FIRST_POTION).isEmpty(), "药水应被装进机器");
        });
    }

    /**
     * 多效果药水加到一半 MRU 用完：400 MRU 只够给尸壳（敌对生物、白天不着火）加神龟药水的第一个效果（缓慢，250），检查第二个（抗性）时不够了。
     * 已经加上的那个照样算这次查询用掉一次（修之前直接返回 false，不计次，一瓶药能用远超 16 次）。
     * 第十批 A3 起有害效果只给敌对生物，这里从猪换成尸壳（猪拿不到缓慢，测不出「加到一半」）。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void spreaderCountsUseWhenMRURunsOutMidTarget(GameTestHelper helper) {
        PotionSpreaderBlockEntity be = placeSpreader(helper, 400);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 1, 5));
        be.getInventory().setStackInSlot(PotionSpreaderBlockEntity.FIRST_POTION,
                PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.TURTLE_MASTER));

        helper.succeedWhen(() -> {
            helper.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "尸壳应得到缓慢（神龟药水的第一个效果）");
            helper.assertFalse(husk.hasEffect(MobEffects.DAMAGE_RESISTANCE), "剩下的 150 MRU 不够再加抗性");
            int used = 400 - be.getMRUStorage().getMRU();
            helper.assertTrue(used == 250, "只应加上一种效果、耗 250 MRU，实际 " + used);
            helper.assertTrue(be.usesLeft() == be.maxUses() - 1,
                    "加到一半 MRU 用完，这次查询也应算用掉一次，剩 " + be.usesLeft() + "/" + be.maxUses());
        });
    }

    /** 瞬间治疗：给少血的猪治，不给亡灵（骷髅马）治（对亡灵它是伤害，D9） */
    @GameTest(template = EMPTY_LARGE)
    public static void spreaderHealingSkipsUndead(GameTestHelper helper) {
        PotionSpreaderBlockEntity be = placeSpreader(helper, 5000);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 5));
        pig.setHealth(5F);
        SkeletonHorse horse = helper.spawnWithNoFreeWill(EntityType.SKELETON_HORSE, new BlockPos(7, 1, 5));
        horse.setHealth(5F);
        be.getInventory().setStackInSlot(PotionSpreaderBlockEntity.FIRST_POTION,
                PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pig.getHealth() >= 9F, "猪应被治疗 4 点，现在 " + pig.getHealth()))
                .thenIdle(25)
                .thenExecute(() -> {
                    // 瞬间治疗对亡灵是伤害：只断言没掉血。原版的马每 tick 有 1/900 的几率自己回 1 点血（AbstractHorse.aiStep），不能要求正好是 5
                    helper.assertTrue(horse.isAlive() && horse.getHealth() >= 5F, "骷髅马（亡灵）不应受瞬间治疗影响（不该掉血），现在 " + horse.getHealth());
                    int used = 5000 - be.getMRUStorage().getMRU();
                    // 猪治到 9 以后再治会溢出（9 + 4 > 10），不再治
                    helper.assertTrue(used == 250, "只应治猪一次、耗 250 MRU，实际 " + used);
                })
                .thenSucceed();
    }

    /** 空闲时存读档不出错（原作对空的药水 id 调 toString）；装着药水时效果和次数原样读回 */
    @GameTest(template = EMPTY)
    public static void spreaderSavesIdleAndLoaded(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECDevices.POTION_SPREADER.get());
        PotionSpreaderBlockEntity be = (PotionSpreaderBlockEntity) helper.getBlockEntity(pos);
        PotionSpreaderBlockEntity idleCopy = new PotionSpreaderBlockEntity(be.getBlockPos(), be.getBlockState());
        idleCopy.load(be.saveWithoutMetadata());
        helper.assertTrue(idleCopy.loadedEffects().isEmpty() && idleCopy.usesLeft() == 0, "空闲的扩散器读回来应什么都没装");

        be.getInventory().setStackInSlot(PotionSpreaderBlockEntity.FIRST_POTION,
                PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.LONG_SWIFTNESS));
        helper.succeedWhen(() -> {
            helper.assertTrue(!be.loadedEffects().isEmpty(), "药水应已装上");
            PotionSpreaderBlockEntity copy = new PotionSpreaderBlockEntity(be.getBlockPos(), be.getBlockState());
            copy.load(be.saveWithoutMetadata());
            helper.assertTrue(copy.loadedEffects().size() == 1
                            && copy.loadedEffects().get(0).getEffect() == MobEffects.MOVEMENT_SPEED
                            && copy.loadedEffects().get(0).getDuration() == be.loadedEffects().get(0).getDuration(),
                    "读回来的效果应一样：" + copy.loadedEffects());
            helper.assertTrue(copy.usesLeft() == be.usesLeft() && copy.maxUses() == be.maxUses(), "读回来的次数应一样");
            helper.assertTrue(copy.potionColor() == be.potionColor(), "读回来的颜色应一样");
        });
    }

    // ------------------------------------------------------------------
    // 魔力唱片机
    // ------------------------------------------------------------------

    /** 红石常亮只放一次（原作每 5 秒重放、每次 500 MRU）；再给一个上升沿才重放；放着的时候漏斗抽不走唱片 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void jukeboxReplaysOnlyOnRisingEdge(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos redstone = new BlockPos(2, 1, 3);
        helper.setBlock(pos, ECDevices.MAGICAL_JUKEBOX.get());
        MagicalJukeboxBlockEntity be = (MagicalJukeboxBlockEntity) helper.getBlockEntity(pos);
        be.getMRUStorage().setMRU(5000);
        be.getInventory().setStackInSlot(MagicalJukeboxBlockEntity.SLOT_RECORD, new ItemStack(Items.MUSIC_DISC_13));
        helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
        IItemHandler handler = automation(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.getMRUStorage().getMRU() == 4500, "放进唱片应自动放一遍、耗 500 MRU，剩 " + be.getMRUStorage().getMRU());
                    helper.assertTrue(be.isPlaying(), "应在放");
                    helper.assertTrue(handler.extractItem(MagicalJukeboxBlockEntity.SLOT_RECORD, 1, true).isEmpty(), "放着的时候漏斗不应抽走唱片");
                })
                .thenExecuteAfter(225, () -> {
                    helper.assertTrue(be.getMRUStorage().getMRU() == 4500, "红石常亮不应重放，剩 " + be.getMRUStorage().getMRU());
                    helper.setBlock(redstone, Blocks.AIR);
                })
                .thenExecuteAfter(2, () -> helper.setBlock(redstone, Blocks.REDSTONE_BLOCK))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.getMRUStorage().getMRU() == 4000, "上升沿应重放一次、再耗 500，剩 " + be.getMRUStorage().getMRU());
                    be.stopPlaying();
                    helper.assertTrue(!be.isPlaying(), "停下以后不应在放");
                    helper.assertTrue(handler.extractItem(MagicalJukeboxBlockEntity.SLOT_RECORD, 1, true).is(Items.MUSIC_DISC_13),
                            "停下以后漏斗应能抽走唱片");
                })
                .thenSucceed();
    }

    /** 换一张唱片（漏斗同一 tick 抽走旧的、送进新的）也算新唱片，自动放 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void jukeboxPlaysSwappedRecord(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECDevices.MAGICAL_JUKEBOX.get());
        MagicalJukeboxBlockEntity be = (MagicalJukeboxBlockEntity) helper.getBlockEntity(pos);
        be.getMRUStorage().setMRU(5000);
        be.getInventory().setStackInSlot(MagicalJukeboxBlockEntity.SLOT_RECORD, new ItemStack(Items.MUSIC_DISC_CAT));

        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.getMRUStorage().getMRU() == 4500, "第一张应放一遍");
                    be.stopPlaying();
                    // 冷却（100 tick）过后换唱片，模拟漏斗同一 tick 里抽走、送进
                })
                .thenExecuteAfter(MagicalJukeboxBlockEntity.RESTART_COOLDOWN + 5, () ->
                        be.getInventory().setStackInSlot(MagicalJukeboxBlockEntity.SLOT_RECORD, new ItemStack(Items.MUSIC_DISC_BLOCKS)))
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.getMRUStorage().getMRU() == 4000, "换上的新唱片应自动放一遍，剩 " + be.getMRUStorage().getMRU());
                    helper.assertTrue(be.isPlaying(), "新唱片应在放");
                })
                .thenSucceed();
    }

    /**
     * 还没放过的唱片漏斗抽不走（否则下面的漏斗会把歌单整张冲走）：MRU 不够没开放的、同一 tick 刚换进来本机还没 tick 到的、
     * 冷却中等着放的都抽不走；放完的能抽走。
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void jukeboxHopperKeepsUnplayedRecord(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        int slot = MagicalJukeboxBlockEntity.SLOT_RECORD;
        helper.setBlock(pos, ECDevices.MAGICAL_JUKEBOX.get());
        MagicalJukeboxBlockEntity be = (MagicalJukeboxBlockEntity) helper.getBlockEntity(pos);
        be.getMRUStorage().setMRU(0);
        be.getInventory().setStackInSlot(slot, new ItemStack(Items.MUSIC_DISC_13));
        IItemHandler handler = automation(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(!be.hasPlayed() && !be.isPlaying(), "MRU 不够不应开放");
                    helper.assertTrue(handler.extractItem(slot, 1, true).isEmpty(), "MRU 不够、还没放过的唱片不应被漏斗抽走");
                    be.getMRUStorage().setMRU(5000);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(be.isPlaying() && be.getMRUStorage().getMRU() == 4500, "来了 MRU 应开放、耗 500，剩 " + be.getMRUStorage().getMRU());
                    // 当作这一遍放完了（停下，唱片留着）
                    be.stopPlaying();
                    helper.assertTrue(handler.extractItem(slot, 1, false).is(Items.MUSIC_DISC_13), "放完的唱片应能被抽走");
                    // 模拟漏斗同一 tick 里送进下一张，本机还没 tick 到
                    helper.assertTrue(handler.insertItem(slot, new ItemStack(Items.MUSIC_DISC_CAT), false).isEmpty(), "应能送进新唱片");
                    helper.assertTrue(handler.extractItem(slot, 1, true).isEmpty(), "刚送进来、本机还没 tick 到的新唱片不应被抽走");
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(!be.hasPlayed() && !be.isPlaying(), "冷却中新唱片应等着，不开放");
                    helper.assertTrue(be.getMRUStorage().getMRU() == 4500, "冷却中不应扣 MRU，剩 " + be.getMRUStorage().getMRU());
                    helper.assertTrue(handler.extractItem(slot, 1, true).isEmpty(), "冷却中等着放的唱片不应被抽走");
                })
                .thenWaitUntil(() -> helper.assertTrue(be.isPlaying(), "冷却过后新唱片应自动放"))
                .thenExecute(() -> helper.assertTrue(be.getMRUStorage().getMRU() == 4000,
                        "新唱片应放一遍、再耗 500，剩 " + be.getMRUStorage().getMRU()))
                .thenSucceed();
    }
}
