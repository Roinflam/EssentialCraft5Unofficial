package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorItem;
import com.flameforge.essentialcraft5.item.armor.ConfiguredArmorStats;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.io.Reader;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 审判庭套装（ECInquisitorium，batch8.md §6.6）的 GameTest：默认属性（护甲 10、韧性 5、击退抗性 0.2，胸甲 +30 生命、靴子 +0.1 移速）、
 * 配置覆盖跟着变、热改成 0 后脱下不留残余；MRU 抗性全 1（四件穿齐全免）；头盔在 mru_vision；第十批 D22 起每件一条配方、各吃一个审判庭芯片，
 * 仍不在任何掉落表里；
 * 耐久照原作，第十三批 13b 起穿在玩家身上不掉（D14，别的生物穿着照常掉，{@code keep_durability} 关掉时照原作掉）；稀有度史诗、附魔能力 60、没有修复材料。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class InquisitoriumArmorTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1.0E-9D;
    private static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    private InquisitoriumArmorTests() {
    }

    private static ItemStack stack(ArmorItem.Type type) {
        return new ItemStack(ECInquisitorium.all().get(type.ordinal()).get());
    }

    private static double sum(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        return stack.getAttributeModifiers(slot).get(attribute).stream().mapToDouble(AttributeModifier::getAmount).sum();
    }

    private static int count(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        return stack.getAttributeModifiers(slot).get(attribute).size();
    }

    /** 原版换装时做的事：把这件的修饰符加到实体上 */
    private static void wear(Player player, ItemStack stack) {
        ArmorItem armor = (ArmorItem) stack.getItem();
        player.setItemSlot(armor.getEquipmentSlot(), stack);
        player.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(armor.getEquipmentSlot()));
    }

    /** 默认属性：每件护甲 10、韧性 5、击退抗性 0.2；生命只有胸甲 +30、移速只有靴子 +0.1（别的部位也放数额 0 的修饰符）；护甲用原版同部位 UUID；只在自己的部位 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumDefaultAttributes(GameTestHelper helper) {
        for (ArmorItem.Type type : TYPES) {
            ItemStack stack = stack(type);
            EquipmentSlot slot = type.getSlot();
            String what = type.toString();
            helper.assertTrue(sum(stack, slot, Attributes.ARMOR) == 10D, what + " 护甲应是 10");
            helper.assertTrue(sum(stack, slot, Attributes.ARMOR_TOUGHNESS) == 5D, what + " 韧性应是 5");
            helper.assertTrue(Math.abs(sum(stack, slot, Attributes.KNOCKBACK_RESISTANCE) - 0.2D) < EPS, what + " 击退抗性应是 0.2");
            double health = type == ArmorItem.Type.CHESTPLATE ? 30D : 0D;
            double speed = type == ArmorItem.Type.BOOTS ? 0.1D : 0D;
            helper.assertTrue(count(stack, slot, Attributes.MAX_HEALTH) == 1 && sum(stack, slot, Attributes.MAX_HEALTH) == health, what + " 生命应是 " + health);
            helper.assertTrue(count(stack, slot, Attributes.MOVEMENT_SPEED) == 1 && Math.abs(sum(stack, slot, Attributes.MOVEMENT_SPEED) - speed) < EPS,
                    what + " 移速应是 " + speed);
            EquipmentSlot other = type == ArmorItem.Type.HELMET ? EquipmentSlot.CHEST : EquipmentSlot.HEAD;
            helper.assertTrue(stack.getAttributeModifiers(other).isEmpty(), what + " 不在自己的部位没有属性");
            UUID vanilla = ConfiguredArmorStats.vanillaArmorModifierId(type);
            helper.assertTrue(stack.getAttributeModifiers(slot).get(Attributes.ARMOR).iterator().next().getId().equals(vanilla), what + " 护甲修饰符用原版 UUID");
        }
        helper.succeed();
    }

    /** 配置覆盖了属性跟着变；穿着时把胸甲生命热改成 0 再脱下，最大生命回到 20、修饰符不留在身上 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumConfigAndHotReload(GameTestHelper helper) {
        ItemStack boots = stack(ArmorItem.Type.BOOTS);
        ECInquisitorium.Config.setTestOverrides(new ECInquisitorium.Config.Values(4D, 2D, 0.1D, 12D, 0.05D));
        try {
            helper.assertTrue(sum(boots, EquipmentSlot.FEET, Attributes.ARMOR) == 4D && sum(boots, EquipmentSlot.FEET, Attributes.ARMOR_TOUGHNESS) == 2D
                    && Math.abs(sum(boots, EquipmentSlot.FEET, Attributes.MOVEMENT_SPEED) - 0.05D) < EPS, "覆盖值改了属性应跟着变");
        } finally {
            ECInquisitorium.Config.setTestOverrides(null);
        }

        Player player = helper.makeMockPlayer();
        ItemStack chest = stack(ArmorItem.Type.CHESTPLATE);
        wear(player, chest);
        helper.assertTrue(player.getMaxHealth() == 50F, "测试前提：穿着胸甲最大生命 50，实际 " + player.getMaxHealth());
        ECInquisitorium.Config.setTestOverrides(new ECInquisitorium.Config.Values(10D, 5D, 0.2D, 0D, 0.1D));
        try {
            player.getAttributes().removeAttributeModifiers(chest.getAttributeModifiers(EquipmentSlot.CHEST));
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            helper.assertTrue(player.getMaxHealth() == 20F, "热改成 0 后脱下，最大生命应回到 20，实际 " + player.getMaxHealth());
            helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH)
                    .getModifier(ConfiguredArmorStats.healthModifierId(ArmorItem.Type.CHESTPLATE, InquisitoriumArmorItem.STATS_KEY)) == null, "生命修饰符不应留在身上");
        } finally {
            ECInquisitorium.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    /** MRU 抗性每件 1 / 1 / 1：四件穿齐，MRU 腐化、腐化辐射、海纳尔辐射的易伤都是 0；只穿一件是 0.75 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumMruResistance(GameTestHelper helper) {
        Player one = helper.makeMockPlayer();
        one.setItemSlot(EquipmentSlot.HEAD, stack(ArmorItem.Type.HELMET));
        Player full = helper.makeMockPlayer();
        for (ArmorItem.Type type : TYPES) {
            ItemStack stack = stack(type);
            full.setItemSlot(type.getSlot(), stack);
            for (MRUResistance.Type kind : MRUResistance.Type.values()) {
                helper.assertTrue(((InquisitoriumArmorItem) stack.getItem()).getResistance(stack, kind) == 1F, type + " 对 " + kind + " 的抗性应是 1");
            }
        }
        for (MRUResistance.Type kind : MRUResistance.Type.values()) {
            helper.assertTrue(MRUResistance.vulnerability(full, kind) == 0F, "四件穿齐对 " + kind + " 应全免，实际 " + MRUResistance.vulnerability(full, kind));
            helper.assertTrue(Math.abs(MRUResistance.vulnerability(one, kind) - 0.75F) < 1.0E-6F, "只戴头盔对 " + kind + " 应是 0.75");
        }
        helper.succeed();
    }

    /** 头盔在 mru_vision 里（看得见 MRU），另外三件不在 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumVisionTag(GameTestHelper helper) {
        for (ArmorItem.Type type : TYPES) {
            ItemStack stack = stack(type);
            boolean helmet = type == ArmorItem.Type.HELMET;
            helper.assertTrue(stack.is(ECTags.MRU_VISION) == helmet, type + (helmet ? " 应" : " 不应") + "在 mru_vision 里");
            helper.assertTrue(MRUVision.grantsVision(stack) == helmet, type + " 看不看得见 MRU");
        }
        helper.succeed();
    }

    /**
     * 生存可得（第十批 D22 A）：每件正好一条产出它的配方，各吃 1 个审判庭芯片（打倒审判庭哨兵才有）；全息投影、遗迹箱子的表里都没有它
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumRecipesUseChip(GameTestHelper helper) {
        for (RegistryObject<InquisitoriumArmorItem> piece : ECInquisitorium.all()) {
            int recipes = 0;
            for (Recipe<?> recipe : helper.getLevel().getRecipeManager().getRecipes()) {
                if (!recipe.getResultItem(helper.getLevel().registryAccess()).is(piece.get())) {
                    continue;
                }
                recipes++;
                long chips = recipe.getIngredients().stream()
                        .filter(ingredient -> ingredient.test(new ItemStack(ECInquisitorium.INQUISITORIUM_CHIP.get()))).count();
                helper.assertTrue(chips == 1, piece.getId() + " 的配方应正好吃 1 个审判庭芯片，实际 " + chips + "（" + recipe.getId() + "）");
            }
            helper.assertTrue(recipes == 1, piece.getId() + " 应正好有一条配方，实际 " + recipes);
        }
        List<String> tables = List.of("entities/hologram_addition", "entities/hologram_subtraction", "entities/hologram_multiplication",
                "entities/hologram_division", "chests/town_tower", "chests/catacombs");
        for (String table : tables) {
            ResourceLocation file = EssentialCraft5.id("loot_tables/" + table + ".json");
            Optional<Resource> resource = helper.getLevel().getServer().getResourceManager().getResource(file);
            helper.assertTrue(resource.isPresent(), "找不到 " + file);
            if (resource.isEmpty()) {
                return;
            }
            try (Reader reader = resource.get().openAsReader()) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[4096];
                for (int read; (read = reader.read(buffer)) >= 0; ) {
                    text.append(buffer, 0, read);
                }
                helper.assertFalse(text.toString().contains("inquisitorium"), table + " 里不应有审判庭套装");
            } catch (Exception e) {
                helper.fail(file + " 读不出来：" + e);
            }
        }
        helper.succeed();
    }

    /**
     * 耐久照原作 5 120 / 15 360 / 10 240 / 5 120；穿在玩家身上不掉（D14），僵尸穿着照常掉，{@code keep_durability} 关掉时玩家也照常掉；
     * 史诗稀有度、附魔能力 60、没有修复材料
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumDurabilityAndWear(GameTestHelper helper) {
        int[] durability = {5120, 15360, 10240, 5120};
        for (ArmorItem.Type type : TYPES) {
            ItemStack stack = stack(type);
            helper.assertTrue(stack.getMaxDamage() == durability[type.ordinal()], type + " 耐久应是 " + durability[type.ordinal()] + "，实际 " + stack.getMaxDamage());
            helper.assertTrue(stack.getRarity() == Rarity.EPIC, type + " 应是史诗稀有度");
            helper.assertTrue(stack.getEnchantmentValue() == 60, type + " 附魔能力应是 60");
            helper.assertFalse(stack.getItem().isValidRepairItem(stack, new ItemStack(Items.DIAMOND))
                    || stack.getItem().isValidRepairItem(stack, new ItemStack(Items.NETHERITE_INGOT)), type + " 没有修复材料");
        }
        Player player = helper.makeMockPlayer();
        ItemStack helmet = stack(ArmorItem.Type.HELMET);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        helmet.hurtAndBreak(3, player, p -> p.broadcastBreakEvent(EquipmentSlot.HEAD));
        helper.assertTrue(helmet.getDamageValue() == 0, "穿在玩家身上不掉耐久，实际掉了 " + helmet.getDamageValue());

        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        ItemStack worn = stack(ArmorItem.Type.CHESTPLATE);
        zombie.setItemSlot(EquipmentSlot.CHEST, worn);
        worn.hurtAndBreak(3, zombie, z -> z.broadcastBreakEvent(EquipmentSlot.CHEST));
        helper.assertTrue(worn.getDamageValue() == 3, "僵尸穿着照常掉耐久，实际 " + worn.getDamageValue());

        ECInquisitorium.Config.Law law = ECInquisitorium.Config.LAW_DEFAULTS;
        ECInquisitorium.Config.setLawTestOverrides(new ECInquisitorium.Config.Law(false, law.magicResistance(), law.bossResistance(),
                law.edictReduction(), law.convictionBonus(), law.convictionTicks(), law.convictionMaxTargets()));
        try {
            helmet.hurtAndBreak(3, player, p -> p.broadcastBreakEvent(EquipmentSlot.HEAD));
            helper.assertTrue(helmet.getDamageValue() == 3, "keep_durability 关掉时玩家也照常掉，实际 " + helmet.getDamageValue());
        } finally {
            ECInquisitorium.Config.setLawTestOverrides(null);
        }
        helper.succeed();
    }
}
