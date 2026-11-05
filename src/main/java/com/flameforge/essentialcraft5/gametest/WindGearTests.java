package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.item.WindArmorItem;
import com.flameforge.essentialcraft5.item.WindGearEvents;
import com.flameforge.essentialcraft5.item.WindTier;
import com.flameforge.essentialcraft5.item.WindToolForms;
import com.flameforge.essentialcraft5.item.WindToolForms.Form;
import com.flameforge.essentialcraft5.item.armor.DyeableECArmorItem;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 风之装备（ECWindGear）的 GameTest（batch3.md §3.4 的测试点）：
 * 风之工具按方块换形态（石头 → 镐、原木 → 斧……）、经 Forge 的 LeftClickBlock 两端都会换、左键被取消也照样换、
 * 够不着 / 区块没加载完 / 创造模式挖方块不换；附魔和铁砧惩罚按形态分开、耐久和名字共用；打中生物变剑、不扣耐久（创造模式也变）；
 * 任何形态右键锄地、被领地拒绝的格子不改也不扣耐久；
 * 风之护甲的护甲值、MRU 抗性之和、头盔看见 MRU、靴子登高 +0.6（脱下恢复）、胸甲移速、护腿跳跃；染色配方；风之水晶配方（ECWind 接线后才查）。
 * <p>
 * 本模块自己的物品直接引用 ECWindGear；风之水晶（ECWind）按注册名取，拿不到就跳过配方断言。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class WindGearTests {

    private static final String EMPTY = "empty";
    private static final double EPSILON = 1.0E-5D;

    private WindGearTests() {
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    private static ItemStack tool(Form form) {
        return new ItemStack(form.item());
    }

    private static double modifierSum(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        return stack.getAttributeModifiers(slot).get(attribute).stream().mapToDouble(AttributeModifier::getAmount).sum();
    }

    // ---------------------------------------------------------------- 选形态

    /** 选形态：石头 → 镐、原木 → 斧、泥土 → 锹、树叶 → 锄、蜘蛛网 → 剑；已经最合适不换；一碰就碎（蒲公英）、挖不动（基岩）不换 */
    @GameTest(template = EMPTY)
    public static void windGearFormForBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ItemStack sword = tool(Form.SWORD);
        ItemStack pickaxe = tool(Form.PICKAXE);
        checkForm(helper, sword, Blocks.STONE.defaultBlockState(), Form.PICKAXE, level, pos);
        checkForm(helper, sword, Blocks.DIAMOND_ORE.defaultBlockState(), Form.PICKAXE, level, pos);
        checkForm(helper, sword, Blocks.OAK_LOG.defaultBlockState(), Form.AXE, level, pos);
        checkForm(helper, sword, Blocks.DIRT.defaultBlockState(), Form.SHOVEL, level, pos);
        checkForm(helper, sword, Blocks.SAND.defaultBlockState(), Form.SHOVEL, level, pos);
        checkForm(helper, sword, Blocks.OAK_LEAVES.defaultBlockState(), Form.HOE, level, pos);
        checkForm(helper, sword, Blocks.HAY_BLOCK.defaultBlockState(), Form.HOE, level, pos);
        checkForm(helper, pickaxe, Blocks.COBWEB.defaultBlockState(), Form.SWORD, level, pos);
        checkForm(helper, pickaxe, Blocks.STONE.defaultBlockState(), null, level, pos);
        checkForm(helper, pickaxe, Blocks.DANDELION.defaultBlockState(), null, level, pos);
        checkForm(helper, pickaxe, Blocks.BEDROCK.defaultBlockState(), null, level, pos);
        checkForm(helper, sword, Blocks.AIR.defaultBlockState(), null, level, pos);
        helper.assertTrue(WindToolForms.bestFormFor(new ItemStack(Items.DIAMOND_PICKAXE), Blocks.DIRT.defaultBlockState(), level, pos) == null,
                "不是风之工具不应给形态");
        helper.succeed();
    }

    private static void checkForm(GameTestHelper helper, ItemStack held, BlockState state, Form expected, ServerLevel level, BlockPos pos) {
        Form actual = WindToolForms.bestFormFor(held, state, level, pos);
        helper.assertTrue(actual == expected, held.getItem() + " 挖 " + state + " 应换成 " + expected + "，实际 " + actual);
    }

    /**
     * 经 Forge 的开始挖方块事件（START）换形态；STOP 不换；事件被别的监听器取消也照样换（两端一致，客户端不会来回闪）；
     * 够不着、区块有票据但还没加载完时不换、也不读方块（不让服务端主线程同步等区块加载）；创造模式不换
     */
    @GameTest(template = EMPTY)
    public static void windGearSwitchesOnLeftClick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = new BlockPos(1, 1, 1);
        BlockPos log = new BlockPos(2, 1, 1);
        BlockPos dirt = new BlockPos(3, 1, 1);
        BlockPos hay = new BlockPos(3, 1, 2);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(log, Blocks.OAK_LOG);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(hay, Blocks.HAY_BLOCK);

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool(Form.SWORD));
        ForgeHooks.onLeftClickBlock(player, helper.absolutePos(stone), Direction.UP, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        helper.assertTrue(player.getMainHandItem().is(ECWindGear.WIND_ELEMENTAL_PICKAXE.get()), "剑开始挖石头应变成镐，实际 " + player.getMainHandItem());

        ForgeHooks.onLeftClickBlock(player, helper.absolutePos(log), Direction.UP, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);
        helper.assertTrue(player.getMainHandItem().is(ECWindGear.WIND_ELEMENTAL_PICKAXE.get()), "STOP 不应换形态");
        ForgeHooks.onLeftClickBlock(player, helper.absolutePos(log), Direction.UP, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        helper.assertTrue(player.getMainHandItem().is(ECWindGear.WIND_ELEMENTAL_AXE.get()), "开始挖原木应变成斧，实际 " + player.getMainHandItem());

        // 事件被别的监听器（模拟只在服务端拦截的领地模组）取消：照样换，两端才一致
        Consumer<PlayerInteractEvent.LeftClickBlock> claim = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.LeftClickBlock.class, claim);
        try {
            PlayerInteractEvent.LeftClickBlock event = ForgeHooks.onLeftClickBlock(player, helper.absolutePos(dirt), Direction.UP,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            helper.assertTrue(event.isCanceled(), "模拟领地应取消这次左键");
            helper.assertTrue(player.getMainHandItem().is(ECWindGear.WIND_ELEMENTAL_SHOVEL.get()),
                    "左键被别的监听器取消也应照样换成锹，实际 " + player.getMainHandItem());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(claim);
        }

        // 够不着（人离开 30 格）：不换
        Vec3 away = helper.absoluteVec(new Vec3(0.5D, 1D, 30.5D));
        player.moveTo(away.x, away.y, away.z, 0F, 0F);
        helper.assertFalse(WindToolForms.onStartMining(player, level, helper.absolutePos(hay)), "够不着的方块不应换形态");
        helper.assertTrue(player.getMainHandItem().is(ECWindGear.WIND_ELEMENTAL_SHOVEL.get()), "够不着时手里应还是锹");
        Vec3 near = helper.absoluteVec(new Vec3(0.5D, 1D, 0.5D));
        player.moveTo(near.x, near.y, near.z, 0F, 0F);
        helper.assertTrue(WindToolForms.onStartMining(player, level, helper.absolutePos(hay)), "回到近处应能换");
        helper.assertTrue(player.getMainHandItem().is(ECWindGear.WIND_ELEMENTAL_HOE.get()), "开始挖干草块应变成锄");

        // 区块有票据（level.isLoaded 已经是 true）但还没加载完：不换、不读方块。
        // 票据刚加上时区块的读盘 / 生成还没开始跑完（完成那一步要回到主线程，测试正占着主线程），读方块就会同步等它
        BlockPos unloaded = helper.absolutePos(stone).offset(100_000, 0, 0);
        ChunkPos chunk = new ChunkPos(unloaded);
        ServerChunkCache chunks = level.getChunkSource();
        chunks.addRegionTicket(TicketType.UNKNOWN, chunk, 0, chunk);   // 票据等级 33（FULL），UNKNOWN 自己 1 tick 后过期
        try {
            chunks.pollTask();   // 跑一次票据更新：建出区块持有者，isLoaded 变 true
            helper.assertTrue(level.isLoaded(unloaded) && !ECLevels.isFullyLoaded(level, unloaded),
                    "前提：区块应有票据但还没加载完（isLoaded=" + level.isLoaded(unloaded) + "）");
            player.moveTo(unloaded.getX() + 0.5D, unloaded.getY(), unloaded.getZ() - 0.5D, 0F, 0F);
            helper.assertFalse(WindToolForms.onStartMining(player, level, unloaded), "区块没加载完不应换形态");
            helper.assertTrue(chunks.getChunkNow(chunk.x, chunk.z) == null, "不应为了读方块同步把区块加载完");
            helper.assertTrue(player.getMainHandItem().is(ECWindGear.WIND_ELEMENTAL_HOE.get()), "区块没加载完时手里应还是锄");
        } finally {
            chunks.removeRegionTicket(TicketType.UNKNOWN, chunk, 0, chunk);
            player.moveTo(near.x, near.y, near.z, 0F, 0F);
        }

        // 创造模式挖方块不换（原作 BreakSpeed 在创造模式不触发）；打中生物照样变剑，见 windGearHitTurnsIntoSword
        Player creative = helper.makeMockPlayer();
        creative.moveTo(near.x, near.y, near.z, 0F, 0F);
        creative.setItemInHand(InteractionHand.MAIN_HAND, tool(Form.SWORD));
        helper.assertFalse(WindToolForms.onStartMining(creative, level, helper.absolutePos(stone)), "创造模式挖方块不应换形态");
        helper.assertTrue(creative.getMainHandItem().is(ECWindGear.WIND_ELEMENTAL_SWORD.get()), "创造模式手里应还是剑");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 附魔、耐久

    /** 附魔和铁砧惩罚按形态分开；耐久、名字共用；收起来的形态不嵌套；transform 不改原栈 */
    @GameTest(template = EMPTY)
    public static void windGearKeepsEnchantmentsPerForm(GameTestHelper helper) {
        ItemStack pickaxe = tool(Form.PICKAXE);
        pickaxe.enchant(Enchantments.BLOCK_FORTUNE, 3);
        pickaxe.setRepairCost(3);
        pickaxe.setDamageValue(100);
        pickaxe.setHoverName(Component.literal("Breeze"));

        ItemStack axe = WindToolForms.transform(pickaxe, Form.AXE);
        helper.assertTrue(axe.is(ECWindGear.WIND_ELEMENTAL_AXE.get()), "应变成斧");
        helper.assertTrue(EnchantmentHelper.getEnchantments(axe).isEmpty(), "斧形态不应带着镐的附魔，实际 " + EnchantmentHelper.getEnchantments(axe));
        helper.assertTrue(axe.getBaseRepairCost() == 0, "斧形态的铁砧惩罚应从 0 算");
        helper.assertTrue(axe.getDamageValue() == 100, "耐久应共用（100），实际 " + axe.getDamageValue());
        helper.assertTrue("Breeze".equals(axe.getHoverName().getString()), "名字应共用");
        helper.assertTrue(WindToolForms.storedEnchantments(axe, Form.PICKAXE).getOrDefault(Enchantments.BLOCK_FORTUNE, 0) == 3,
                "镐的时运 III 应收起来");
        helper.assertTrue(WindToolForms.storedRepairCost(axe, Form.PICKAXE) == 3, "镐的铁砧惩罚应收起来");
        helper.assertTrue(pickaxe.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE) == 3, "transform 不应改原来的栈");

        axe.enchant(Enchantments.SILK_TOUCH, 1);
        ItemStack sword = WindToolForms.transform(axe, Form.SWORD);
        helper.assertTrue(EnchantmentHelper.getEnchantments(sword).isEmpty(), "剑形态没有附魔");
        ItemStack back = WindToolForms.transform(sword, Form.PICKAXE);
        Map<net.minecraft.world.item.enchantment.Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(back);
        helper.assertTrue(enchantments.size() == 1 && enchantments.getOrDefault(Enchantments.BLOCK_FORTUNE, 0) == 3,
                "变回镐应取回时运 III，实际 " + enchantments);
        helper.assertTrue(back.getBaseRepairCost() == 3, "变回镐应取回铁砧惩罚 3");
        helper.assertTrue(WindToolForms.storedEnchantments(back, Form.AXE).getOrDefault(Enchantments.SILK_TOUCH, 0) == 1, "斧的精准采集应收着");
        CompoundTag forms = back.getOrCreateTag().getCompound(WindToolForms.FORMS_KEY);
        helper.assertTrue(forms.getAllKeys().equals(Set.of("axe")), "只应收着有附魔的斧，实际 " + forms.getAllKeys());
        helper.assertTrue(!forms.getCompound("axe").contains(WindToolForms.FORMS_KEY) && forms.getCompound("axe").getAllKeys().size() == 1,
                "收起来的形态只存附魔 / 铁砧惩罚，不嵌套");
        helper.assertTrue(back.getDamageValue() == 100 && back.getMaxDamage() == 15684, "耐久一直共用，上限 15684");
        helper.assertTrue(WindToolForms.transform(back, Form.PICKAXE) == back, "同形态应原样返回");
        helper.assertFalse(pickaxe.getItem().shouldCauseReequipAnimation(pickaxe, axe, false), "同一格里换形态不做换手动画");
        helper.assertTrue(pickaxe.getItem().shouldCauseReequipAnimation(pickaxe, axe, true), "换快捷栏格子照常做换手动画");
        helper.assertTrue(pickaxe.getItem().shouldCauseReequipAnimation(pickaxe, new ItemStack(Items.STONE), false), "换成别的东西照常做换手动画");
        helper.succeed();
    }

    /**
     * 镐 / 斧 / 锹 / 锄打中生物后主手变成剑、不扣耐久（同原作：hitEntity 不调父类）；剑不变、照原版每下扣 1；不在主手的不变；
     * 创造模式打中生物也变剑（同原作，只有挖方块在创造模式不变形）
     */
    @GameTest(template = EMPTY)
    public static void windGearHitTurnsIntoSword(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        ArmorStand target = new ArmorStand(level, at.x, at.y, at.z);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        for (Form form : new Form[]{Form.PICKAXE, Form.AXE, Form.SHOVEL, Form.HOE}) {
            ItemStack stack = tool(form);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            stack.hurtEnemy(target, player);
            ItemStack held = player.getMainHandItem();
            helper.assertTrue(held.is(ECWindGear.WIND_ELEMENTAL_SWORD.get()), form + " 打中生物后应变成剑，实际 " + held);
            helper.assertTrue(held.getDamageValue() == 0, form + " 打中生物不应扣耐久（原作 hitEntity 不调父类），实际 " + held.getDamageValue());
        }
        ItemStack sword = player.getMainHandItem();
        sword.hurtEnemy(target, player);
        helper.assertTrue(player.getMainHandItem() == sword, "剑打中生物不应再变");
        helper.assertTrue(sword.getDamageValue() == 1, "剑形态照原版每下扣 1 耐久，实际 " + sword.getDamageValue());

        ItemStack offhand = tool(Form.PICKAXE);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        offhand.hurtEnemy(target, player);
        helper.assertTrue(player.getOffhandItem() == offhand && player.getMainHandItem() == sword, "不在主手的不应变形");

        Player creative = helper.makeMockPlayer();
        ItemStack creativeTool = tool(Form.PICKAXE);
        creative.setItemInHand(InteractionHand.MAIN_HAND, creativeTool);
        creativeTool.hurtEnemy(target, creative);
        helper.assertTrue(creative.getMainHandItem().is(ECWindGear.WIND_ELEMENTAL_SWORD.get()),
                "创造模式打中生物也应变成剑（同原作），实际 " + creative.getMainHandItem());
        helper.succeed();
    }

    // ---------------------------------------------------------------- 右键

    /** 任何形态右键泥土都锄成耕地、扣 1 耐久；被模拟领地拒绝的格子不改、不扣耐久；斧形态对原木照原版剥皮 */
    @GameTest(template = EMPTY)
    public static void windGearTillsAndRespectsClaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos allowed = new BlockPos(1, 1, 1);
        BlockPos denied = new BlockPos(3, 1, 1);
        BlockPos log = new BlockPos(1, 1, 3);
        helper.setBlock(allowed, Blocks.DIRT);
        helper.setBlock(denied, Blocks.DIRT);
        helper.setBlock(log, Blocks.OAK_LOG);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);

        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(denied)) {
            ItemStack pickaxe = tool(Form.PICKAXE);
            player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
            InteractionResult result = pickaxe.useOn(context(player, helper.absolutePos(allowed)));
            helper.assertTrue(result.consumesAction(), "镐形态右键泥土应锄地，实际 " + result);
            helper.assertTrue(level.getBlockState(helper.absolutePos(allowed)).is(Blocks.FARMLAND), "应锄成耕地");
            helper.assertTrue(pickaxe.getDamageValue() == 1, "锄地应扣 1 耐久，实际 " + pickaxe.getDamageValue());

            InteractionResult rejected = pickaxe.useOn(context(player, helper.absolutePos(denied)));
            helper.assertFalse(rejected.consumesAction(), "被拒的格子不应成功，实际 " + rejected);
            helper.assertTrue(level.getBlockState(helper.absolutePos(denied)).is(Blocks.DIRT), "被拒的格子应还是泥土");
            helper.assertTrue(pickaxe.getDamageValue() == 1, "被拒时不应扣耐久，实际 " + pickaxe.getDamageValue());
            helper.assertTrue(claims.places(denied) == 1, "被拒的格子应收到一次放置事件，实际 " + claims.places(denied));

            ItemStack axe = tool(Form.AXE);
            player.setItemInHand(InteractionHand.MAIN_HAND, axe);
            InteractionResult stripped = axe.useOn(context(player, helper.absolutePos(log)));
            helper.assertTrue(stripped.consumesAction() && level.getBlockState(helper.absolutePos(log)).is(Blocks.STRIPPED_OAK_LOG),
                    "斧形态对锄不了的原木应照原版剥皮，实际 " + level.getBlockState(helper.absolutePos(log)));
        }
        helper.succeed();
    }

    private static UseOnContext context(Player player, BlockPos absolute) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).add(0D, 0.5D, 0D), Direction.UP, absolute, false));
    }

    // ---------------------------------------------------------------- 数值

    /** 风之 Tier：镐能挖黑曜石、速度 15、耐久 15684、附魔 42；攻击修正照 ECGear 的换算（剑 9、斧 11、镐 7、锹 7.5、锄 0，锄攻速 +3） */
    @GameTest(template = EMPTY)
    public static void windGearToolNumbers(GameTestHelper helper) {
        ItemStack pickaxe = tool(Form.PICKAXE);
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        helper.assertTrue(pickaxe.isCorrectToolForDrops(obsidian) && near(pickaxe.getDestroySpeed(obsidian), 15D), "风之镐应能挖黑曜石、速度 15");
        helper.assertTrue(pickaxe.getMaxDamage() == 15684 && pickaxe.getEnchantmentValue() == 42, "耐久 15684、附魔 42");
        helper.assertTrue(WindTier.WIND.getAttackDamageBonus() == 6F, "材料攻击加成 6");
        double[][] expected = {{7D, -2.8D}, {11D, -3D}, {7.5D, -3D}, {0D, 3D}, {9D, -2.4D}};
        Form[] forms = {Form.PICKAXE, Form.AXE, Form.SHOVEL, Form.HOE, Form.SWORD};
        for (int i = 0; i < forms.length; i++) {
            ItemStack stack = tool(forms[i]);
            double attack = modifierSum(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE);
            double speed = modifierSum(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED);
            helper.assertTrue(near(attack, expected[i][0]) && near(speed, expected[i][1]),
                    forms[i] + " 攻击 / 攻速修正应为 " + expected[i][0] + " / " + expected[i][1] + "，实际 " + attack + " / " + speed);
        }
        helper.succeed();
    }

    /** 护甲值 4 / 10 / 7 / 4（E4）、耐久 624 / 1248 / 936 / 624、无韧性、附魔 40；护腿用 wind_layer_2；全套 MRU 易伤 0.5 / 0.25 / 0.5；头盔看见 MRU */
    @GameTest(template = EMPTY)
    public static void windArmorValuesAndResistance(GameTestHelper helper) {
        ArmorItem[] pieces = {ECWindGear.WIND_HELMET.get(), ECWindGear.WIND_CHESTPLATE.get(), ECWindGear.WIND_LEGGINGS.get(), ECWindGear.WIND_BOOTS.get()};
        int[] defense = {4, 10, 7, 4};
        int[] durability = {624, 1248, 936, 624};
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(pieces[i].getDefense() == defense[i], pieces[i] + " 护甲值应为 " + defense[i] + "，实际 " + pieces[i].getDefense());
            helper.assertTrue(new ItemStack(pieces[i]).getMaxDamage() == durability[i], pieces[i] + " 耐久应为 " + durability[i]);
            helper.assertTrue(pieces[i].getToughness() == ECWindGear.ARMOR_TOUGHNESS && pieces[i].getEnchantmentValue() == 40, "韧性 2.5（第十三批）、附魔 40");
            helper.assertTrue(pieces[i] instanceof DyeableLeatherItem, "风之护甲应可染色");
        }
        String legs = pieces[2].getArmorTexture(new ItemStack(pieces[2]), null, EquipmentSlot.LEGS, null);
        String chest = pieces[1].getArmorTexture(new ItemStack(pieces[1]), null, EquipmentSlot.CHEST, null);
        String overlay = pieces[1].getArmorTexture(new ItemStack(pieces[1]), null, EquipmentSlot.CHEST, "overlay");
        helper.assertTrue(legs != null && legs.endsWith("wind_layer_2.png"), "护腿应用 wind_layer_2，实际 " + legs);
        helper.assertTrue(chest != null && chest.endsWith("wind_layer_1.png"), "胸甲应用 wind_layer_1，实际 " + chest);
        helper.assertTrue(DyeableECArmorItem.NO_OVERLAY.toString().equals(overlay), "overlay 层应是透明贴图");

        Player player = helper.makeMockPlayer();
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(pieces[0]));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(pieces[1]));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(pieces[2]));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(pieces[3]));
        helper.assertTrue(near(MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION), 0.5D), "全套 MRU 腐化易伤应为 0.5");
        helper.assertTrue(near(MRUResistance.vulnerability(player, MRUResistance.Type.CORRUPTION_RADIATION), 0.25D), "全套腐化辐射易伤应为 0.25");
        helper.assertTrue(near(MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION), 0.5D), "全套海纳尔辐射易伤应为 0.5");
        helper.assertTrue(((WindArmorItem) pieces[1]).getResistance(new ItemStack(pieces[1]), MRUResistance.Type.MRU_CORRUPTION) == 0.5F, "每件 MRU 腐化抗性 0.5");
        helper.assertTrue(MRUVision.canSee(player), "戴风之头盔应能看见 MRU（mru_vision 标签，需先跑 runData）");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        helper.assertFalse(MRUVision.canSee(player), "胸甲、护腿、靴子不应让人看见 MRU");
        helper.succeed();
    }

    /** 靴子登高 +0.6（合计 1.2，脱下回到 0.6）；胸甲移速 +0.075；护腿起跳 +0.2 竖直速度，没穿不加 */
    @GameTest(template = EMPTY)
    public static void windArmorBonuses(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack boots = new ItemStack(ECWindGear.WIND_BOOTS.get());
        helper.assertTrue(near(modifierSum(boots, EquipmentSlot.FEET, ForgeMod.STEP_HEIGHT_ADDITION.get()), 0.6D), "靴子应给登高 +0.6");
        helper.assertTrue(near(player.getStepHeight(), 0.6D), "没穿时登高 0.6，实际 " + player.getStepHeight());
        player.getAttributes().addTransientAttributeModifiers(boots.getAttributeModifiers(EquipmentSlot.FEET));
        helper.assertTrue(near(player.getStepHeight(), 1.2D), "穿上靴子登高应为 1.2，实际 " + player.getStepHeight());
        player.getAttributes().removeAttributeModifiers(new ItemStack(ECWindGear.WIND_BOOTS.get()).getAttributeModifiers(EquipmentSlot.FEET));
        helper.assertTrue(near(player.getStepHeight(), 0.6D), "脱下（换一个新栈的修饰符去掉，UUID 固定）应回到 0.6，实际 " + player.getStepHeight());
        helper.assertTrue(near(modifierSum(boots, EquipmentSlot.FEET, Attributes.ARMOR), 4D), "靴子仍有护甲值 4（额外属性不顶掉护甲）");

        ItemStack chest = new ItemStack(ECWindGear.WIND_CHESTPLATE.get());
        helper.assertTrue(near(modifierSum(chest, EquipmentSlot.CHEST, Attributes.MOVEMENT_SPEED), 0.075D), "胸甲应给移速 +0.075");
        helper.assertTrue(near(modifierSum(chest, EquipmentSlot.LEGS, Attributes.MOVEMENT_SPEED), 0D), "胸甲放在别的部位不给移速");
        helper.assertTrue(near(modifierSum(new ItemStack(ECWindGear.WIND_HELMET.get()), EquipmentSlot.HEAD, Attributes.MOVEMENT_SPEED), 0D), "头盔不给移速");

        player.setDeltaMovement(0D, 0.42D, 0D);
        ForgeHooks.onLivingJump(player);
        helper.assertTrue(near(player.getDeltaMovement().y, 0.42D), "没穿护腿起跳不加速度，实际 " + player.getDeltaMovement().y);
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ECWindGear.WIND_LEGGINGS.get()));
        player.setDeltaMovement(0D, 0.42D, 0D);
        ForgeHooks.onLivingJump(player);
        helper.assertTrue(near(player.getDeltaMovement().y, 0.42D + WindGearEvents.LEGGINGS_JUMP_BOOST),
                "穿风之护腿起跳应 +0.2，实际 " + player.getDeltaMovement().y);
        helper.succeed();
    }

    /** 原版 armor_dye 配方能染风之护甲 */
    @GameTest(template = EMPTY)
    public static void windArmorDyeable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        items.set(0, new ItemStack(ECWindGear.WIND_HELMET.get()));
        items.set(1, new ItemStack(Items.RED_DYE));
        TransientCraftingContainer grid = new TransientCraftingContainer(null, 3, 3, items);
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        helper.assertTrue(recipe.isPresent(), "风之头盔 + 红色染料应匹配原版染色配方");
        ItemStack dyed = recipe.get().assemble(grid, level.registryAccess());
        helper.assertTrue(dyed.is(ECWindGear.WIND_HELMET.get()), "产物应还是风之头盔");
        helper.assertTrue(DyeableECArmorItem.colorOf(dyed) != DyeableECArmorItem.DEFAULT_COLOR, "染过色后颜色应变");
        helper.assertTrue(DyeableECArmorItem.colorOf(new ItemStack(ECWindGear.WIND_HELMET.get())) == 0xFFFFFF, "没染过是白色（贴图原色）");
        helper.succeed();
    }

    /** 风之水晶（ECWind）注册了才查：九个配方都在，镐的摆法能做出风之镐 */
    @GameTest(template = EMPTY)
    public static void windGearRecipes(GameTestHelper helper) {
        ResourceLocation gemId = EssentialCraft5.id("wind_gem");
        if (!ForgeRegistries.ITEMS.containsKey(gemId)) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        for (String name : new String[]{"wind_elemental_pickaxe", "wind_elemental_axe", "wind_elemental_shovel", "wind_elemental_hoe",
                "wind_elemental_sword", "wind_helmet", "wind_chestplate", "wind_leggings", "wind_boots"}) {
            helper.assertTrue(level.getRecipeManager().byKey(EssentialCraft5.id("windgear/" + name)).isPresent(), "应有配方 windgear/" + name);
        }
        Item gem = ForgeRegistries.ITEMS.getValue(gemId);
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        items.set(0, new ItemStack(gem));
        items.set(1, new ItemStack(gem));
        items.set(2, new ItemStack(gem));
        items.set(4, new ItemStack(Items.STICK));
        items.set(7, new ItemStack(Items.STICK));
        TransientCraftingContainer grid = new TransientCraftingContainer(null, 3, 3, items);
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        helper.assertTrue(recipe.isPresent() && recipe.get().assemble(grid, level.registryAccess()).is(ECWindGear.WIND_ELEMENTAL_PICKAXE.get()),
                "三颗风之水晶 + 两根木棍应做出风之镐");
        helper.succeed();
    }
}
