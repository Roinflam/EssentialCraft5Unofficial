package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.item.ElementalTier;
import com.flameforge.essentialcraft5.item.MRUMoverItem;
import com.flameforge.essentialcraft5.item.MRUPresenceSpawnEggItem;
import com.flameforge.essentialcraft5.item.MatrixProjectionItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECMRUCU;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 装备与防护模块（ECGear）的 GameTest：魔力防护套装的 MRU 易伤与护甲值、MRU 可见性、单片镜戴头上、矩阵投影的使用、
 * 聚集体刷怪蛋、元素工具的挖掘等级与斧的攻速修正、二阶魔杖。
 * <p>
 * 本模块自己的物品直接引用 ECGear；一批的物品（矩阵投影）引用 ECMaterials。二批其它模块的物品这里用不到。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GearTests {

    private static final String EMPTY = "empty";
    private static final float EPSILON = 1.0E-5F;

    private GearTests() {
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    private static void wearFullMHS(Player player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECGear.MHS_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ECGear.MHS_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ECGear.MHS_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ECGear.MHS_BOOTS.get()));
    }

    // ---------------------------------------------------------------- 魔力防护套装

    /** 穿齐四件：MRU 腐化易伤 0.75、腐化辐射 0.875、海纳尔辐射 0.9；少一件时按公式少减；矩阵损伤 1 级 1 点、2 级 3 点 */
    @GameTest(template = EMPTY)
    public static void mhsFullSetVulnerability(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        wearFullMHS(player);
        float corruption = MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION);
        float radiation = MRUResistance.vulnerability(player, MRUResistance.Type.CORRUPTION_RADIATION);
        float hoanna = MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION);
        helper.assertTrue(near(corruption, 0.75F), "全套 MRU 腐化易伤应为 0.75，实际 " + corruption);
        helper.assertTrue(near(radiation, 0.875F), "全套腐化辐射易伤应为 0.875，实际 " + radiation);
        helper.assertTrue(near(hoanna, 0.9F), "全套海纳尔辐射易伤应为 0.9，实际 " + hoanna);
        helper.assertTrue(MRUPresenceEntity.matrixDamage(1, corruption) == 1 && MRUPresenceEntity.matrixDamage(2, corruption) == 3,
                "全套时聚集体矩阵损伤应为 1 级 1 点、2 级 3 点（手册写的数）");

        // 手册里「穿 1 / 2 / 3 件」那几行
        int[][] expected = {{6, 12}, {3, 6}, {2, 4}};
        EquipmentSlot[] order = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST};
        for (int removed = 0; removed < 3; removed++) {
            player.setItemSlot(order[removed], ItemStack.EMPTY);
            int pieces = 3 - removed;
            float vulnerability = MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION);
            helper.assertTrue(near(vulnerability, 1F - 0.25F * pieces / 4F), pieces + " 件时易伤应为 " + (1F - 0.25F * pieces / 4F) + "，实际 " + vulnerability);
            int[] row = expected[pieces - 1];
            helper.assertTrue(MRUPresenceEntity.matrixDamage(1, vulnerability) == row[0] && MRUPresenceEntity.matrixDamage(2, vulnerability) == row[1],
                    pieces + " 件时矩阵损伤应为 " + row[0] + " / " + row[1]);
        }
        helper.succeed();
    }

    /**
     * 护甲值照原版钻石：头盔 3、胸甲 8、护腿 6、靴子 3（D13，原作胸甲护腿写反）；耐久 512 / 1536 / 1024 / 512；护腿用 _1 贴图。
     * 第十三批起穿在身上的属性按配置给（默认每件韧性 2、+2 生命，batch13.md §4.3），细节在 MHSArmorTests
     */
    @GameTest(template = EMPTY)
    public static void mhsArmorValues(GameTestHelper helper) {
        ArmorItem[] pieces = {ECGear.MHS_HELMET.get(), ECGear.MHS_CHESTPLATE.get(), ECGear.MHS_LEGGINGS.get(), ECGear.MHS_BOOTS.get()};
        int[] defense = {3, 8, 6, 3};
        int[] durability = {512, 1536, 1024, 512};
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(pieces[i].getDefense() == defense[i], pieces[i] + " 护甲值应为 " + defense[i] + "，实际 " + pieces[i].getDefense());
            helper.assertTrue(new ItemStack(pieces[i]).getMaxDamage() == durability[i],
                    pieces[i] + " 耐久应为 " + durability[i]);
            double toughness = new ItemStack(pieces[i]).getAttributeModifiers(pieces[i].getEquipmentSlot()).get(Attributes.ARMOR_TOUGHNESS).stream()
                    .mapToDouble(AttributeModifier::getAmount).sum();
            helper.assertTrue(toughness == 2D, "魔力防护每件韧性 2，实际 " + toughness);
        }
        helper.assertTrue(pieces[0].getEnchantmentValue() == 25, "附魔能力应为 25");
        String legs = pieces[2].getArmorTexture(new ItemStack(pieces[2]), null, EquipmentSlot.LEGS, null);
        String chest = pieces[1].getArmorTexture(new ItemStack(pieces[1]), null, EquipmentSlot.CHEST, null);
        helper.assertTrue(legs != null && legs.endsWith("armorec3magic_1.png"), "护腿应用 _1 贴图，实际 " + legs);
        helper.assertTrue(chest != null && chest.endsWith("armorec3magic_0.png"), "胸甲应用 _0 贴图，实际 " + chest);
        helper.succeed();
    }

    // ---------------------------------------------------------------- MRU 可见性、单片镜

    /** 单片镜（主手 / 副手 / 头盔槽）、二阶魔杖（手里）、防护头盔（头盔槽）都让 MRUVision.canSee 为真；一阶魔杖、防护胸甲不行 */
    @GameTest(template = EMPTY)
    public static void mruVisionItems(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        helper.assertFalse(MRUVision.canSee(player), "空手不应看见 MRU");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.MAGIC_MONOCLE.get()));
        helper.assertTrue(MRUVision.canSee(player), "主手拿单片镜应能看见 MRU（mru_vision 标签，需先跑 runData）");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ECGear.MRU_MOVER_T2.get()));
        helper.assertTrue(MRUVision.canSee(player), "副手拿二阶魔杖应能看见 MRU");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECGear.MHS_HELMET.get()));
        helper.assertTrue(MRUVision.canSee(player), "戴魔力防护头盔应能看见 MRU");

        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECGear.MAGIC_MONOCLE.get()));
        helper.assertTrue(MRUVision.canSee(player), "单片镜戴在头盔槽应能看见 MRU（D12）");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.MHS_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ECGear.MHS_CHESTPLATE.get()));
        helper.assertFalse(MRUVision.canSee(player), "防护胸甲不应让人看见 MRU");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECMRUCU.MRU_MOVER.get()));
        helper.assertFalse(MRUVision.canSee(player), "一阶魔杖原作不在可见列表里");
        helper.succeed();
    }

    /** 单片镜：原版 Equipable 的头部槽，右键戴上；没有耐久 */
    @GameTest(template = EMPTY)
    public static void monocleEquipsToHead(GameTestHelper helper) {
        ItemStack monocle = new ItemStack(ECGear.MAGIC_MONOCLE.get());
        helper.assertTrue(Mob.getEquipmentSlotForItem(monocle) == EquipmentSlot.HEAD, "单片镜应归头盔槽");
        helper.assertFalse(monocle.isDamageableItem(), "单片镜不应有耐久（D12）");

        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, monocle);
        InteractionResultHolder<ItemStack> result = monocle.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, result.getObject());
        helper.assertTrue(result.getResult().consumesAction(), "右键应戴上单片镜，实际 " + result.getResult());
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(ECGear.MAGIC_MONOCLE.get()), "单片镜应到了头盔槽");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "手里应空了");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 矩阵投影

    private static void useFully(ServerLevel level, Player player, ItemStack stack) {
        for (int remaining = MatrixProjectionItem.USE_DURATION; remaining > 0; remaining--) {
            stack.getItem().onUseTick(level, player, stack, remaining);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, stack.getItem().finishUsingItem(stack, level, player));
    }

    /** 第一次右键记主人；主人再右键开始举 200 刻；举满改矩阵类型、挂负面效果、投影用掉；空白投影改回中性 */
    @GameTest(template = EMPTY)
    public static void projectionChangesMatrixType(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        IPlayerData data = PlayerDataEvents.get(player);
        helper.assertTrue(data != null, "模拟玩家应挂着玩家数据");
        data.setMatrixType(0);

        ItemStack chaos = new ItemStack(ECMaterials.CHAOS_MATRIX_PROJECTION.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, chaos);
        chaos.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(MatrixProjectionItem.isOwner(chaos, player), "第一次右键应记下主人");
        helper.assertFalse(player.isUsingItem(), "记主人那一下不应开始使用");

        InteractionResultHolder<ItemStack> start = chaos.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(start.getResult() == InteractionResult.CONSUME && player.isUsingItem(), "主人右键应开始使用");
        helper.assertTrue(chaos.getUseDuration() == 200, "应举 200 刻");
        player.stopUsingItem();

        useFully(level, player, chaos);
        helper.assertTrue(data.getMatrixType() == 1, "用完混沌投影矩阵类型应为 1（混沌），实际 " + data.getMatrixType());
        helper.assertTrue(player.getMainHandItem().isEmpty(), "投影应被用掉");
        helper.assertTrue(player.hasEffect(MobEffects.WITHER) && player.hasEffect(MobEffects.WEAKNESS)
                && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && player.hasEffect(MobEffects.DIG_SLOWDOWN)
                && player.hasEffect(MobEffects.HUNGER), "用完应挂上凋零、虚弱、缓慢、挖掘疲劳、饥饿");
        helper.assertTrue(player.getEffect(MobEffects.WEAKNESS).getAmplifier() == 4
                        && player.getEffect(MobEffects.WITHER).getDuration() == MatrixProjectionItem.AFTERMATH_TICKS,
                "虚弱应为 V 级、凋零 760 刻");
        helper.assertTrue(player.hasEffect(MobEffects.CONFUSION) && player.hasEffect(MobEffects.BLINDNESS), "使用过程中应给反胃和失明");
        helper.assertTrue(player.getEffect(MobEffects.BLINDNESS).getDuration() > 1900, "失明应持续到用完后约 100 秒");

        ItemStack empty = new ItemStack(ECMaterials.EMPTY_MATRIX_PROJECTION.get());
        MatrixProjectionItem.setOwner(empty, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, empty);
        useFully(level, player, empty);
        helper.assertTrue(data.getMatrixType() == 0, "空白投影应把矩阵类型改回 0（中性），实际 " + data.getMatrixType());
        helper.succeed();
    }

    /** 别人的投影：右键失败、不开始使用；就算直接调 finishUsingItem 也不改矩阵类型、不消耗 */
    @GameTest(template = EMPTY)
    public static void projectionRejectsOthers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockSurvivalPlayer();
        Player other = helper.makeMockSurvivalPlayer();
        IPlayerData data = PlayerDataEvents.get(other);
        helper.assertTrue(data != null, "模拟玩家应挂着玩家数据");
        data.setMatrixType(0);

        ItemStack frozen = new ItemStack(ECMaterials.FROZEN_MATRIX_PROJECTION.get());
        MatrixProjectionItem.setOwner(frozen, owner);
        other.setItemInHand(InteractionHand.MAIN_HAND, frozen);
        InteractionResultHolder<ItemStack> result = frozen.use(level, other, InteractionHand.MAIN_HAND);
        helper.assertFalse(result.getResult().consumesAction(), "别人的投影右键不应成功");
        helper.assertFalse(other.isUsingItem(), "别人的投影不应开始使用");

        ItemStack after = frozen.getItem().finishUsingItem(frozen, level, other);
        helper.assertTrue(after.getCount() == 1 && data.getMatrixType() == 0, "非主人不应改矩阵类型、不应消耗投影");
        helper.assertTrue(MatrixProjectionItem.isOwner(frozen, owner), "主人不应被改掉");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 刷怪蛋

    /** 右键方块顶面：上面那格正中放出 500 MRU、平衡值 1、MRU 归零也不消失的聚集体；生存消耗、创造不消耗 */
    @GameTest(template = EMPTY)
    public static void spawnEggReleases500(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos absFloor = helper.absolutePos(floor);

        Player player = helper.makeMockSurvivalPlayer();
        ItemStack eggs = new ItemStack(ECGear.MRU_PRESENCE_SPAWN_EGG.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, eggs);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absFloor).add(0D, 0.5D, 0D), Direction.UP, absFloor, false);
        InteractionResult result = eggs.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(result.consumesAction(), "对方块使用刷怪蛋应成功，实际 " + result);

        Vec3 expected = Vec3.atCenterOf(absFloor.above());
        List<MRUPresenceEntity> found = level.getEntitiesOfClass(MRUPresenceEntity.class, new AABB(absFloor.above()).inflate(1D), Entity::isAlive);
        try {
            helper.assertTrue(found.size() == 1, "应放出 1 个聚集体，实际 " + found.size());
            MRUPresenceEntity presence = found.get(0);
            helper.assertTrue(presence.getMRU() == MRUPresenceSpawnEggItem.MRU, "聚集体应有 500 MRU（原作是 0），实际 " + presence.getMRU());
            helper.assertTrue(near(presence.getBalance(), 1F), "平衡值应为 1，实际 " + presence.getBalance());
            helper.assertTrue(presence.isAlwaysStay() && !presence.getFlag(), "应 alwaysStay=true、flag=false");
            helper.assertTrue(presence.position().distanceTo(expected) < 0.01D, "应放在方块上面那格的正中，实际 " + presence.position());
            helper.assertTrue(eggs.getCount() == 1, "生存模式应消耗一个");

            player.getAbilities().instabuild = true;
            eggs.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(eggs.getCount() == 1, "创造模式不应消耗");
        } finally {
            level.getEntitiesOfClass(MRUPresenceEntity.class, new AABB(absFloor.above()).inflate(1D), Entity::isAlive).forEach(Entity::discard);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- 元素工具

    /** 元素镐能挖黑曜石（效率 15），排在下界合金之后；弱化元素排在钻石与下界合金之间，也能挖黑曜石；铁镐不行 */
    @GameTest(template = EMPTY)
    public static void elementalPickaxeMinesObsidian(GameTestHelper helper) {
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        ItemStack elemental = new ItemStack(ECGear.ELEMENTAL_PICKAXE.get());
        ItemStack weak = new ItemStack(ECGear.WEAK_ELEMENTAL_PICKAXE.get());
        helper.assertTrue(elemental.isCorrectToolForDrops(obsidian), "元素镐应能挖黑曜石");
        helper.assertTrue(near(elemental.getDestroySpeed(obsidian), 15F), "元素镐挖掘速度应为 15，实际 " + elemental.getDestroySpeed(obsidian));
        helper.assertTrue(weak.isCorrectToolForDrops(obsidian), "弱化元素镐（钻石级）应能挖黑曜石");
        helper.assertTrue(near(weak.getDestroySpeed(obsidian), 7F), "弱化元素镐挖掘速度应为 7");
        helper.assertFalse(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(obsidian), "铁镐不应能挖黑曜石（对照）");
        helper.assertTrue(elemental.getMaxDamage() == 3568 && weak.getMaxDamage() == 754, "耐久应为 3568 / 754");

        List<Tier> sorted = TierSortingRegistry.getSortedTiers();
        int diamond = sorted.indexOf(Tiers.DIAMOND);
        int netherite = sorted.indexOf(Tiers.NETHERITE);
        int weakIndex = sorted.indexOf(ElementalTier.WEAKENED);
        int elementalIndex = sorted.indexOf(ElementalTier.ELEMENTAL);
        helper.assertTrue(elementalIndex > netherite && netherite >= 0, "元素材料应排在下界合金之后，排序 " + sorted);
        helper.assertTrue(weakIndex > diamond && weakIndex < netherite, "弱化元素材料应排在钻石与下界合金之间，排序 " + sorted);
        helper.assertTrue(ECGear.NEEDS_ELEMENTAL_TOOL != null && ElementalTier.WEAKENED.getTag() == null,
                "元素材料带 needs_elemental_tool 标签，弱化元素不带");
        helper.succeed();
    }

    /** 斧修了原作把挖掘效率当攻速传进去的 bug：攻速修正 −3（不是 +15），攻击 +5 + 材料加成 */
    @GameTest(template = EMPTY)
    public static void elementalAxeAttackFixed(GameTestHelper helper) {
        checkAxe(helper, ECGear.ELEMENTAL_AXE.get(), 10D);
        checkAxe(helper, ECGear.WEAK_ELEMENTAL_AXE.get(), 8D);
        helper.succeed();
    }

    private static void checkAxe(GameTestHelper helper, Item axe, double damage) {
        ItemStack stack = new ItemStack(axe);
        double speed = stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED).stream()
                .mapToDouble(AttributeModifier::getAmount).sum();
        double attack = stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream()
                .mapToDouble(AttributeModifier::getAmount).sum();
        helper.assertTrue(near(speed, -3D), axe + " 攻速修正应为 -3，实际 " + speed);
        helper.assertTrue(near(attack, damage), axe + " 攻击修正应为 " + damage + "，实际 " + attack);
    }

    // ---------------------------------------------------------------- 二阶魔杖

    /** 二阶魔杖：耐久 1536；和一阶一样把 4 格外的聚集体拉近 0.1 格；拉动时每 20 刻掉 1 点耐久 */
    @GameTest(template = EMPTY)
    public static void moverT2PullsAndWears(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 target = helper.absoluteVec(new Vec3(2.5D, 1.5D, 4.5D));
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, target.x, target.y, target.z, 500, 1F, false, true);
        level.addFreshEntity(presence);
        try {
            // 第三批安全回补起魔杖只听真玩家的（MRUMoverItem.mayMove：假玩家、模拟玩家都挪不动），这里用生存模式的真玩家
            Player player = ProtectionFrameworkTests.realPlayer(helper);
            Vec3 feet = helper.absoluteVec(new Vec3(2.5D, 1.5D, 0.5D));
            player.moveTo(feet.x, feet.y, feet.z, 0F, 0F);

            ItemStack wand = new ItemStack(ECGear.MRU_MOVER_T2.get());
            helper.assertTrue(wand.getMaxDamage() == 1536, "二阶魔杖耐久应为 1536");
            helper.assertTrue(wand.getItem() instanceof MRUMoverItem, "二阶魔杖应复用一阶的类");
            double before = presence.getZ();
            wand.getItem().onUseTick(level, player, wand, 72000);
            helper.assertTrue(near(presence.getZ() - before, -0.1D), "不潜行时应往玩家这边拉 0.1 格，实际 " + (presence.getZ() - before));
            helper.assertTrue(wand.getDamageValue() == 1, "拉动时第 72000 刻（20 的倍数）应掉 1 点耐久，实际 " + wand.getDamageValue());
            wand.getItem().onUseTick(level, player, wand, 71999);
            helper.assertTrue(wand.getDamageValue() == 1, "不是 20 的倍数的刻不掉耐久");
        } finally {
            presence.discard();
        }
        helper.succeed();
    }
}
