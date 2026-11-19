package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.VoidArmorEvents;
import com.flameforge.essentialcraft5.item.VoidArmorItem;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.bauble.SpecialBaubleItem;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECCreative;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 虚空套装（第七批 M3，batch7.md §4.3；防护按 §9.1 D1）的 GameTest：
 * <ul>
 *   <li>材料、创造栏空 / 满两份、不是储存物品、护腿没有「穿过怪物」提示、头盔看得见 MRU、MRU 抗性与全套易伤。</li>
 *   <li>属性：护甲 6 / 16 / 12 / 6、每件韧性 6、击退抗性 0.2、生命 +20，空、满两份一样（不看电）；护甲修饰符用原版的 UUID；
 *       测试覆盖值改了属性跟着变，击退抗性、生命配成 0 时修饰符还在（数额 0），穿着时热改成 0 再脱下照样去掉；
 *       靴子移速、登高；全套生命 +80、护甲被原版夹到 30、韧性 20；脱下夹回生命，同一件 MRU 变了不算脱下。</li>
 *   <li>有电就挡物理伤害（第十三批起默认每点 400）：满电全套挨僵尸近战 8 点不掉血、不发 LivingDamageEvent、四件各扣 400 / 1 200 / 800 / 400、耐久不动、照常被击退；
 *       箭同样挡；先扣身上的储存物品；胸甲扣不起就不挡、MRU 不动，另外三件照挡，剩下 3.2 点照常挨；没电的那件不挡、不去抽储存物品；
 *       1 点伤害每件至少按 1 点算（1 600）；无尽 MRU 晶体付得起，一直挡；戴 η 挨 8 点按翻倍后的 16 点扣（挡伤害在 LOW，η 在 NORMAL）。</li>
 *   <li>物理判定的魔法命名空间与例外（配置 {@code general.damage_kinds}，用测试覆盖值）：默认表是 5 个法术模组和 5 条例外；命名空间里的算魔法、不挡，例外表里的照旧算物理。</li>
 *   <li>混合端（{@code RayHitGate.setHybridForTest} 模拟）：LivingHurtEvent 里只算不扣；走不到 LivingDamageEvent（插件拦下）就一点不扣，
 *       放行（发 LivingDamageEvent，整下挡掉时伤害是 0）才扣、只扣一次；部分挡下、身上储存物品不够几件分时，结论和纯 Forge 当场扣一样。</li>
 *   <li>不是物理伤害（岩浆、爆炸、魔法）不挡、不扣 MRU、按护甲照常减伤；带 bypasses_armor 的物理伤害（灵魂刻刀收魂）照全额；
 *       玩家穿着不掉耐久，别的生物穿着照常掉。</li>
 *   <li>胸甲闪避：掷 0 时这一下被取消、不扣 MRU、戴 σ 时攻击者不挨反弹；掷 0.5 照常挡；掉出世界不闪。靴子减摔 10 → 1、不扣 MRU。</li>
 *   <li>头盔饱食：真玩家打死僵尸 +2 饥饿、+4 饱和；假玩家、没戴、受害者是玩家、死亡被取消都不加。四个配方、魔力防护被消耗。</li>
 * </ul>
 * 挨打的一方是 mock 玩家（没有出生无敌、不用 tick）：它不 tick，穿上的盔甲属性不会自己加上，要看护甲减伤的测试按原版换装的写法手动加修饰符；
 * 其余测试不加，挨到的伤害就是挡剩下的数。僵尸近战按难度缩放，raw 由 {@link #rawFor} 反推。穿着胸甲的测试把闪避掷数设成 1（不闪），同步代码里设、测、置回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class VoidArmorTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 1.0E-4F;
    private static final ArmorItem.Type[] TYPES = ArmorItem.Type.values();

    private VoidArmorTests() {
    }

    // ================================================================ 工具

    private static VoidArmorItem item(ArmorItem.Type type) {
        return ECVoidArmor.all().get(type.ordinal()).get();
    }

    private static Player mock(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static ItemStack withMRU(ArmorItem.Type type, int mru) {
        ItemStack stack = new ItemStack(item(type));
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    /** 穿四件，每件的 MRU 按头 / 胸 / 腿 / 靴给；返回四件（同一个对象，挨打后读得到变化） */
    private static ItemStack[] wear(Player player, int helmet, int chest, int legs, int boots) {
        int[] mru = {helmet, chest, legs, boots};
        ItemStack[] stacks = new ItemStack[4];
        for (ArmorItem.Type type : TYPES) {
            stacks[type.ordinal()] = withMRU(type, mru[type.ordinal()]);
            player.setItemSlot(type.getSlot(), stacks[type.ordinal()]);
        }
        return stacks;
    }

    /** 原版换装时做的事（LivingEntity.collectEquipmentChanges）：把这几件的修饰符加到实体上 */
    private static void applyModifiers(Player player, ItemStack... stacks) {
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof ArmorItem armor) {
                player.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(armor.getEquipmentSlot()));
            }
        }
    }

    private static int mru(ItemStack stack) {
        return MRUStaffItem.storedMRU(stack);
    }

    private static double sum(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        return stack.getAttributeModifiers(slot).get(attribute).stream().mapToDouble(AttributeModifier::getAmount).sum();
    }

    /** Player.hurt 按难度缩放能缩放的伤害（简单 min(x/2+1, x)、困难 ×1.5）：要让事件里看到 wanted，传进去的数 */
    private static float rawFor(GameTestHelper helper, float wanted) {
        Difficulty difficulty = helper.getLevel().getDifficulty();
        helper.assertTrue(difficulty != Difficulty.PEACEFUL, "测试前提：不是和平难度（和平时怪物近战打玩家是 0）");
        return switch (difficulty) {
            case EASY -> wanted <= 2F ? wanted : (wanted - 1F) * 2F;
            case HARD -> wanted * 2F / 3F;
            default -> wanted;
        };
    }

    /** 数某个实体收到几次 LivingDamageEvent（这一下被挡完、事件取消时不会发） */
    private static final class DamageCounter implements Consumer<LivingDamageEvent> {
        private final Player target;
        int count;
        float last = -1F;

        DamageCounter(Player target) {
            this.target = target;
        }

        @Override
        public void accept(LivingDamageEvent event) {
            if (event.getEntity() == target) {
                count++;
                last = event.getAmount();
            }
        }
    }

    private static DamageCounter count(Player target) {
        DamageCounter counter = new DamageCounter(target);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDamageEvent.class, counter);
        return counter;
    }

    private static void runNoDodge(Runnable body) {
        VoidArmorEvents.setTestDodgeRoll(1F);
        try {
            body.run();
        } finally {
            VoidArmorEvents.setTestDodgeRoll(null);
        }
    }

    private static List<String> tooltipKeys(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, null, lines, TooltipFlag.NORMAL);
        List<String> keys = new ArrayList<>();
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents t) {
                keys.add(t.getKey());
            }
        }
        return keys;
    }

    // ================================================================ 材料、创造栏、提示、抗性

    /** 耐久照原作、附魔 40、没有修复材料；创造栏空 / 满两份；不是储存物品；护腿没有「穿过怪物」提示；头盔看得见 MRU；抗性与全套易伤 */
    @GameTest(template = EMPTY)
    public static void voidArmorMaterialAndTooltips(GameTestHelper helper) {
        int[] durability = {2048, 6144, 4096, 2048};
        for (ArmorItem.Type type : TYPES) {
            VoidArmorItem armor = item(type);
            ItemStack stack = new ItemStack(armor);
            helper.assertTrue(stack.getMaxDamage() == durability[type.ordinal()], type + " 耐久应是 " + durability[type.ordinal()] + "，实际 " + stack.getMaxDamage());
            helper.assertTrue(armor.getEnchantmentValue() == 40, type + " 附魔能力应是 40");
            helper.assertTrue(ECVoidArmor.MATERIAL.getRepairIngredient().isEmpty()
                    && !armor.isValidRepairItem(stack, new ItemStack(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("void_plating")))), "没有修复材料");
            List<ItemStack> variants = armor.creativeVariants();
            helper.assertTrue(variants.size() == 2 && mru(variants.get(0)) == 0 && mru(variants.get(1)) == ECVoidArmor.Config.shield().maxMru(), type + " 创造栏应是空、满两份");
            IMRUHandlerItem handler = stack.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
            helper.assertTrue(handler != null && handler.getMaxMRU() == 50_000 && !handler.isStorage(), type + " 默认存 5 万 MRU、不是储存物品");
            List<String> keys = tooltipKeys(variants.get(1));
            helper.assertTrue(keys.contains("tooltip.essentialcraft5.mru") && keys.contains("tooltip.essentialcraft5.void_armor.shield"), type + " 提示应有存量和挡伤害两行，实际 " + keys);
            helper.assertFalse(keys.contains("tooltip.essentialcraft5.void_leggings"), "护腿「穿过怪物」原作没有实现，不应有这行提示");
        }
        helper.assertTrue(VoidArmorItem.descriptionKey(ArmorItem.Type.LEGGINGS) == null, "护腿没有说明行");
        ItemStack helmet = new ItemStack(ECVoidArmor.VOID_HELMET.get());
        helper.assertTrue(helmet.is(ECTags.MRU_VISION) && MRUVision.grantsVision(helmet), "头盔应在 mru_vision 里（要先跑 runData）");
        helper.assertFalse(new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()).is(ECTags.MRU_VISION), "胸甲看不见 MRU");

        VoidArmorItem chest = item(ArmorItem.Type.CHESTPLATE);
        helper.assertTrue(chest.getResistance(helmet, MRUResistance.Type.MRU_CORRUPTION) == 1F
                && chest.getResistance(helmet, MRUResistance.Type.CORRUPTION_RADIATION) == 0.5F
                && chest.getResistance(helmet, MRUResistance.Type.HOANNA_RADIATION) == 0.75F, "每件抗性 1 / 0.5 / 0.75");
        Player player = mock(helper, 1.5D, 1.5D);
        wear(player, 0, 0, 0, 0);
        helper.assertTrue(MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION) == 0F, "全套 MRU 腐化易伤 0");
        helper.assertTrue(Math.abs(MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION) - 0.25F) < EPS, "全套海纳尔辐射易伤 0.25");
        helper.succeed();
    }

    // ================================================================ 属性

    /** 默认属性：护甲 6 / 16 / 12 / 6、韧性 6、击退抗性 0.2、生命 +20，空、满一样；只在自己的部位；护甲用原版 UUID；靴子移速 / 登高；覆盖值改了跟着变 */
    @GameTest(template = EMPTY)
    public static void voidArmorAttributesFollowConfig(GameTestHelper helper) {
        double[] armorValues = {6D, 16D, 12D, 6D};
        Attribute step = ForgeMod.STEP_HEIGHT_ADDITION.get();
        for (ArmorItem.Type type : TYPES) {
            EquipmentSlot slot = type.getSlot();
            for (ItemStack stack : List.of(withMRU(type, 0), withMRU(type, ECVoidArmor.Config.shield().maxMru()))) {
                String what = type + "（MRU " + mru(stack) + "）";
                helper.assertTrue(sum(stack, slot, Attributes.ARMOR) == armorValues[type.ordinal()], what + " 护甲应是 " + armorValues[type.ordinal()]);
                helper.assertTrue(sum(stack, slot, Attributes.ARMOR_TOUGHNESS) == 6D, what + " 韧性应是 6");
                helper.assertTrue(Math.abs(sum(stack, slot, Attributes.KNOCKBACK_RESISTANCE) - 0.2D) < 1e-9, what + " 击退抗性应是 0.2");
                helper.assertTrue(sum(stack, slot, Attributes.MAX_HEALTH) == 20D, what + " 生命应 +20");
                boolean boots = type == ArmorItem.Type.BOOTS;
                helper.assertTrue(Math.abs(sum(stack, slot, Attributes.MOVEMENT_SPEED) - (boots ? 0.075D : 0D)) < 1e-9, what + " 移速");
                helper.assertTrue(Math.abs(sum(stack, slot, step) - (boots ? 0.6D : 0D)) < 1e-9, what + " 登高");
                EquipmentSlot other = type == ArmorItem.Type.HELMET ? EquipmentSlot.CHEST : EquipmentSlot.HEAD;
                helper.assertTrue(stack.getAttributeModifiers(other).isEmpty(), what + " 不在自己的部位没有属性");
            }
        }
        UUID vanilla = Items.NETHERITE_HELMET.getDefaultAttributeModifiers(EquipmentSlot.HEAD).get(Attributes.ARMOR).iterator().next().getId();
        UUID ours = new ItemStack(ECVoidArmor.VOID_HELMET.get()).getAttributeModifiers(EquipmentSlot.HEAD).get(Attributes.ARMOR).iterator().next().getId();
        helper.assertTrue(vanilla.equals(ours), "头盔的护甲修饰符应用原版同部位的 UUID");

        ItemStack chest = withMRU(ArmorItem.Type.CHESTPLATE, 0);
        ECVoidArmor.Config.setTestOverrides(new ECVoidArmor.Config.Values(1D, 2D, 3D, 4D, 5D, 0.5D, 10D));
        try {
            helper.assertTrue(sum(chest, EquipmentSlot.CHEST, Attributes.ARMOR) == 2D && sum(chest, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS) == 5D
                    && sum(chest, EquipmentSlot.CHEST, Attributes.KNOCKBACK_RESISTANCE) == 0.5D && sum(chest, EquipmentSlot.CHEST, Attributes.MAX_HEALTH) == 10D,
                    "覆盖值改了属性应跟着变");
            ECVoidArmor.Config.setTestOverrides(new ECVoidArmor.Config.Values(1D, 2D, 3D, 4D, 5D, 0D, 0D));
            helper.assertTrue(chest.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.KNOCKBACK_RESISTANCE).size() == 1
                            && chest.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.MAX_HEALTH).size() == 1
                            && sum(chest, EquipmentSlot.CHEST, Attributes.KNOCKBACK_RESISTANCE) == 0D && sum(chest, EquipmentSlot.CHEST, Attributes.MAX_HEALTH) == 0D,
                    "击退抗性、生命为 0 时修饰符照样在、数额是 0（脱装时原版按表里的 UUID 去掉旧的）");
        } finally {
            ECVoidArmor.Config.setTestOverrides(null);
        }
        helper.assertTrue(sum(chest, EquipmentSlot.CHEST, Attributes.ARMOR) == 16D, "覆盖值去掉后回到默认");
        helper.succeed();
    }

    /** 全套：生命上限 +80、护甲夹到 30、韧性 20、击退抗性 0.8；脱下夹回生命；同一件只是 MRU 变了不算脱下 */
    @GameTest(template = EMPTY)
    public static void voidArmorFullSetHealthAndClamp(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(player, 0, 0, 0, 0);
        applyModifiers(player, stacks);
        helper.assertTrue(player.getMaxHealth() == 100F, "全套最大生命应是 20 + 80，实际 " + player.getMaxHealth());
        helper.assertTrue(player.getArmorValue() == 30, "全套护甲 40 被原版夹到 30，实际 " + player.getArmorValue());
        helper.assertTrue(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 20D, "全套韧性 24 被原版夹到 20");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.8D) < 1e-9, "全套击退抗性 0.8");
        player.setHealth(100F);

        // 同一件胸甲的 MRU 变了（原版也当成换装、发事件）：不算脱下，生命不动
        ItemStack charged = stacks[1].copy();
        charged.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(1000));
        MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.CHEST, stacks[1], charged));
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
        helper.assertTrue(player.getHealth() == 100F, "MRU 变了不算脱下，生命应还是 100，实际 " + player.getHealth());

        for (ArmorItem.Type type : TYPES) {
            MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, type.getSlot(), stacks[type.ordinal()], ItemStack.EMPTY));
            player.getAttributes().removeAttributeModifiers(stacks[type.ordinal()].getAttributeModifiers(type.getSlot()));
            player.setItemSlot(type.getSlot(), ItemStack.EMPTY);
        }
        helper.assertTrue(player.getMaxHealth() == 20F && player.getHealth() == 100F, "原版去掉修饰符时生命不跟着夹（测试前提）");
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
        helper.assertTrue(player.getHealth() == 20F, "脱下后下一刻生命应夹回 20，实际 " + player.getHealth());
        helper.succeed();
    }

    /**
     * 穿着胸甲时服主把击退抗性、生命热改成 0，再脱下（原版按旧物品「现在」算出的表去掉修饰符）：
     * 表里照样有这两条（数额 0），按 UUID 去掉穿上时加的那两条，最大生命回到 20、击退抗性回到 0，不留残余
     */
    @GameTest(template = EMPTY)
    public static void voidArmorConfigZeroRemovesModifiers(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack chest = withMRU(ArmorItem.Type.CHESTPLATE, 0);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        applyModifiers(player, chest);
        helper.assertTrue(player.getMaxHealth() == 40F && Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.2D) < 1e-9,
                "测试前提：穿着胸甲最大生命 40、击退抗性 0.2，实际 " + player.getMaxHealth() + " / " + player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        ECVoidArmor.Config.setTestOverrides(new ECVoidArmor.Config.Values(6D, 16D, 12D, 6D, 6D, 0D, 0D));
        try {
            player.getAttributes().removeAttributeModifiers(chest.getAttributeModifiers(EquipmentSlot.CHEST));
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            helper.assertTrue(player.getMaxHealth() == 20F, "配置改成 0 后脱下，最大生命应回到 20，实际 " + player.getMaxHealth());
            helper.assertTrue(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 0D,
                    "配置改成 0 后脱下，击退抗性应回到 0，实际 " + player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH).getModifier(VoidArmorItem.healthModifierId(ArmorItem.Type.CHESTPLATE)) == null,
                    "生命修饰符不应留在身上");
        } finally {
            ECVoidArmor.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    // ================================================================ 有电就挡物理伤害

    /** 满电全套挨僵尸近战 8 点：不掉血、不发 LivingDamageEvent、四件各扣 400 / 1 200 / 800 / 400、耐久不动、照常被击退 */
    @GameTest(template = EMPTY)
    public static void voidArmorBlocksMelee(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        ItemStack[] stacks = wear(victim, 5000, 5000, 5000, 5000);
        DamageCounter counter = count(victim);
        try {
            runNoDodge(() -> victim.hurt(victim.damageSources().mobAttack(zombie), rawFor(helper, 8F)));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        helper.assertTrue(victim.getHealth() == 20F, "满电全套挡下 8 点近战，不应掉血，实际 " + victim.getHealth());
        helper.assertTrue(counter.count == 0, "挡完了这次伤害被取消，不应发 LivingDamageEvent");
        int[] cost = {400, 1200, 800, 400};
        for (ArmorItem.Type type : TYPES) {
            ItemStack stack = stacks[type.ordinal()];
            helper.assertTrue(mru(stack) == 5000 - cost[type.ordinal()], type + " 应扣 " + cost[type.ordinal()] + "，剩 " + mru(stack));
            helper.assertTrue(stack.getDamageValue() == 0, type + " 耐久不应变");
        }
        helper.assertTrue(victim.getDeltaMovement().horizontalDistanceSqr() > 0D, "挡下伤害照样被击退");
        helper.succeed();
    }

    /** 箭（弹射物）同样挡：没有主人的箭 8 点，四件照样各扣一份 */
    @GameTest(template = EMPTY)
    public static void voidArmorBlocksArrow(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Arrow arrow = new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ());
        ItemStack[] stacks = wear(victim, 5000, 5000, 5000, 5000);
        runNoDodge(() -> victim.hurt(victim.damageSources().arrow(arrow, null), 8F));
        helper.assertTrue(victim.getHealth() == 20F, "箭也应被挡下，实际 " + victim.getHealth());
        helper.assertTrue(mru(stacks[0]) == 4600 && mru(stacks[1]) == 3800 && mru(stacks[2]) == 4200 && mru(stacks[3]) == 4600,
                "箭 8 点应扣 400 / 1200 / 800 / 400");
        helper.succeed();
    }

    /** 身上有储存物品时先扣它：红色灵魂物质付 2 800，四件自己的 MRU 不动 */
    @GameTest(template = EMPTY)
    public static void voidArmorPaysFromStorageFirst(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack storage = MRUStaffItem.full(ECItems.RED_SOUL_MATTER.get());
        victim.getInventory().setItem(9, storage);
        ItemStack[] stacks = wear(victim, 5000, 5000, 5000, 5000);
        runNoDodge(() -> victim.hurt(victim.damageSources().arrow(new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ()), null), 8F));
        helper.assertTrue(victim.getHealth() == 20F, "应被挡下");
        for (ItemStack stack : stacks) {
            helper.assertTrue(mru(stack) == 5000, "带着储存物品时四件自己的 MRU 不应动，实际 " + mru(stack));
        }
        helper.assertTrue(mru(storage) == 120000 - 2800, "储存物品应付 2800，剩 " + mru(storage));
        helper.succeed();
    }

    /** 胸甲只剩 1 000、身上没有储存物品，挨 8 点：胸甲扣不起（要 1 200）就不挡、MRU 仍是 1 000；另外三件照挡照扣，剩下 3.2 点照常挨，耐久都不动 */
    @GameTest(template = EMPTY)
    public static void voidArmorUnaffordablePieceDoesNotBlock(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 5000, 1000, 5000, 5000);
        DamageCounter counter = count(victim);
        try {
            runNoDodge(() -> victim.hurt(victim.damageSources().arrow(new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ()), null), 8F));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        helper.assertTrue(mru(stacks[1]) == 1000, "胸甲扣不起不应扣（也不清零），实际 " + mru(stacks[1]));
        helper.assertTrue(mru(stacks[0]) == 4600 && mru(stacks[2]) == 4200 && mru(stacks[3]) == 4600, "另外三件照挡照扣");
        helper.assertTrue(counter.count == 1 && Math.abs(counter.last - 3.2F) < EPS, "胸甲那 8/20 没挡，应挨 3.2 点，实际 " + counter.last);
        helper.assertTrue(Math.abs(victim.getHealth() - 16.8F) < EPS, "生命应剩 16.8，实际 " + victim.getHealth());
        for (ItemStack stack : stacks) {
            helper.assertTrue(stack.getDamageValue() == 0, "剩下的伤害走护甲时也不掉耐久");
        }
        helper.succeed();
    }

    /** 没电的那件不挡、也不去抽身上的储存物品；有电的三件由储存物品付 */
    @GameTest(template = EMPTY)
    public static void voidArmorEmptyPieceSkipsStorage(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack storage = MRUStaffItem.full(ECItems.RED_SOUL_MATTER.get());
        victim.getInventory().setItem(9, storage);
        ItemStack[] stacks = wear(victim, 5000, 0, 5000, 5000);
        runNoDodge(() -> victim.hurt(victim.damageSources().arrow(new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ()), null), 8F));
        helper.assertTrue(mru(storage) == 120000 - 1600, "只付有电的三件 400 + 800 + 400，剩 " + mru(storage));
        helper.assertTrue(mru(stacks[1]) == 0, "没电的胸甲还是 0");
        helper.assertTrue(Math.abs(victim.getHealth() - 16.8F) < EPS, "没电的胸甲那份照挨 3.2，实际 " + victim.getHealth());
        helper.succeed();
    }

    /** 1 点物理伤害每件也至少按 1 点算：全套扣 1 600 */
    @GameTest(template = EMPTY)
    public static void voidArmorMinimumCost(GameTestHelper helper) {
        helper.assertTrue(VoidArmorItem.blockCost(1F, ArmorItem.Type.CHESTPLATE) == 400 && VoidArmorItem.blockCost(20F, ArmorItem.Type.CHESTPLATE) == 3200
                && VoidArmorItem.blockCost(1.0E30F, ArmorItem.Type.CHESTPLATE) == Integer.MAX_VALUE, "花费 max(1, ⌊伤害 × 份额⌋) × 400，极大时夹到 int 上限");
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 5000, 5000, 5000, 5000);
        runNoDodge(() -> victim.hurt(victim.damageSources().arrow(new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ()), null), 1F));
        int total = 0;
        for (ItemStack stack : stacks) {
            total += 5000 - mru(stack);
        }
        helper.assertTrue(total == 1600 && victim.getHealth() == 20F, "1 点伤害全套应扣 1600，实际 " + total);
        helper.succeed();
    }

    /**
     * 带着无尽 MRU 晶体时扣电全由它付：四件不动、照样挡（两件东西叠出来的效果，batch7 §9.1 D1 附注；保持现状，
     * 手册的虚空套装 / 无尽道具两条和晶体的提示都写明「带着它时虚空套装的电不会耗尽」）
     */
    @GameTest(template = EMPTY)
    public static void voidArmorInfiniteCrystalPays(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        victim.getInventory().setItem(9, new ItemStack(ECCreative.INFINITE_MRU_CRYSTAL.get()));
        ItemStack[] stacks = wear(victim, 10, 10, 10, 10);
        runNoDodge(() -> victim.hurt(victim.damageSources().arrow(new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ()), null), 50F));
        helper.assertTrue(victim.getHealth() == 20F, "无尽晶体付得起，50 点也挡下");
        for (ItemStack stack : stacks) {
            helper.assertTrue(mru(stack) == 10, "四件自己的 MRU 不动");
        }
        helper.succeed();
    }

    /** 不是物理伤害（岩浆、爆炸、魔法）不挡、不扣 MRU，照护甲减伤；带 bypasses_armor 的物理伤害照全额；玩家穿着挨打不掉耐久 */
    @GameTest(template = EMPTY)
    public static void voidArmorNonPhysicalUsesArmor(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 5000, 5000, 5000, 5000);
        applyModifiers(victim, stacks);
        victim.setHealth(100F);
        DamageSources sources = victim.damageSources();
        float armor = victim.getArmorValue();
        float toughness = (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        runNoDodge(() -> {
            float before = victim.getHealth();
            victim.hurt(sources.lava(), 10F);
            float want = CombatRules.getDamageAfterAbsorb(10F, armor, toughness);
            helper.assertTrue(Math.abs(before - victim.getHealth() - want) < EPS, "岩浆 10 点应照护甲减到 " + want + "，实际掉 " + (before - victim.getHealth()));

            victim.invulnerableTime = 0;
            before = victim.getHealth();
            victim.hurt(sources.explosion(null, null), rawFor(helper, 5F));
            want = CombatRules.getDamageAfterAbsorb(5F, armor, toughness);
            helper.assertTrue(Math.abs(before - victim.getHealth() - want) < EPS, "爆炸 5 点应照护甲减到 " + want + "，实际掉 " + (before - victim.getHealth()));

            victim.invulnerableTime = 0;
            before = victim.getHealth();
            victim.hurt(sources.magic(), 4F);
            helper.assertTrue(Math.abs(before - victim.getHealth() - 4F) < EPS, "魔法无视护甲，照全额 4 点");

            victim.invulnerableTime = 0;
            before = victim.getHealth();
            Player other = mock(helper, 3.5D, 1.5D);
            DamageSource capture = ECDamageTypes.soulCapture(other);
            victim.hurt(capture, 3F);
            helper.assertTrue(Math.abs(before - victim.getHealth() - 3F) < EPS, "灵魂刻刀收魂（物理但 bypasses_armor）照全额 3 点");
        });
        for (ItemStack stack : stacks) {
            helper.assertTrue(mru(stack) == 5000, "不是物理伤害 / 无视护甲的都不应扣 MRU，实际 " + mru(stack));
            helper.assertTrue(stack.getDamageValue() == 0, "玩家穿着挨岩浆、爆炸不掉耐久");
        }
        helper.succeed();
    }

    /**
     * 戴 η（受到的伤害 ×2，NORMAL）挨僵尸 8 点：挡伤害在 LOW，翻倍后是 16 点，其中 η 多出来的 8 点护盾不吸收（batch13.md §4.4，第十三批起）——
     * 四件按另外 8 点各扣 400 / 1 200 / 800 / 400，η 那 8 点照常走护甲、扣血
     */
    @GameTest(template = EMPTY)
    public static void voidArmorCountsEtaDoubledDamage(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 5000, 5000, 5000, 5000);
        Optional<ICurioStacksHandler> necklaces = CuriosApi.getCuriosInventory(victim).resolve().map(inv -> inv.getCurios().get(CuriosCompat.NECKLACE));
        helper.assertTrue(necklaces.isPresent() && necklaces.get().getSlots() > 0, "测试前提：mock 玩家有 Curios 项链位");
        necklaces.get().getStacks().setStackInSlot(0, new ItemStack(ECBaubles.ETA_AMULET.get()));
        CuriosCompat.invalidate(victim);
        helper.assertTrue(CuriosCompat.has(victim, ECBaubles.ETA_AMULET.get()), "测试前提：戴上了 η");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        DamageCounter counter = count(victim);
        try {
            runNoDodge(() -> victim.hurt(victim.damageSources().mobAttack(zombie), rawFor(helper, 8F)));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
            necklaces.get().getStacks().setStackInSlot(0, ItemStack.EMPTY);
            CuriosCompat.invalidate(victim);
        }
        helper.assertTrue(counter.count == 1 && Math.abs(counter.last - 8F) < EPS,
                "η 多出来的 8 点不被吸收，照常落到护甲上，实际事件里 " + counter.last + "（" + counter.count + " 次）");
        helper.assertTrue(victim.getHealth() < 20F, "η 那一半照常扣血，实际 " + victim.getHealth());
        int[] cost = {400, 1200, 800, 400};
        for (ArmorItem.Type type : TYPES) {
            int left = mru(stacks[type.ordinal()]);
            helper.assertTrue(left == 5000 - cost[type.ordinal()], type + " 应按翻倍前的 8 点扣 " + cost[type.ordinal()] + "，剩 " + left);
        }
        helper.succeed();
    }

    /**
     * 物理判定的命名空间规则（配置 general.damage_kinds，用测试覆盖值）：默认表是 5 个法术模组、5 条例外；
     * 本模组的 demon 伤害默认算物理，把 essentialcraft5 放进魔法命名空间后算魔法、虚空套装不挡不扣，再放进例外表又算物理
     */
    @GameTest(template = EMPTY)
    public static void voidArmorMagicNamespaces(GameTestHelper helper) {
        helper.assertTrue(ECDamageKinds.Config.DEFAULT_MAGIC_NAMESPACES.equals(List.of("irons_spellbooks", "gtbcs_geomancy_plus", "goety", "ars_nouveau", "sweetmagic"))
                        && ECDamageKinds.Config.DEFAULT_MAGIC_EXCEPTIONS.equals(List.of("goety:summon", "goety:sword", "goety:loot_explode",
                        "goety:loot_explode_owned", "goety:dismissed")),
                "默认的魔法命名空间 / 例外表不对");
        helper.assertTrue(ECDamageKinds.isMagicById(ResourceLocation.fromNamespaceAndPath("goety", "direct_shock")),
                "服务端配置里的默认值：goety:direct_shock 算魔法");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        DamageSource demon = ECDamageTypes.demon(zombie);
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 5000, 5000, 5000, 5000);
        ECDamageKinds.Config.setTestOverrides(ECDamageKinds.Config.DEFAULT_MAGIC_NAMESPACES, ECDamageKinds.Config.DEFAULT_MAGIC_EXCEPTIONS);
        try {
            helper.assertTrue(ECDamageKinds.isMagicById(ResourceLocation.fromNamespaceAndPath("goety", "direct_shock"))
                    && ECDamageKinds.isMagicById(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fire_magic"))
                    && ECDamageKinds.isMagicById(ResourceLocation.fromNamespaceAndPath("ars_nouveau", "spell")), "法术模组命名空间里的算魔法");
            helper.assertFalse(ECDamageKinds.isMagicById(ResourceLocation.fromNamespaceAndPath("goety", "sword"))
                    || ECDamageKinds.isMagicById(ResourceLocation.fromNamespaceAndPath("goety", "summon"))
                    || ECDamageKinds.isMagicById(ResourceLocation.fromNamespaceAndPath("goety", "loot_explode")), "例外表里的不算魔法");
            helper.assertFalse(ECDamageKinds.isMagicById(ResourceLocation.withDefaultNamespace("mob_attack"))
                    || ECDamageKinds.isMagicById(EssentialCraft5.id("demon")), "别的命名空间不受影响");
            helper.assertTrue(ECDamageKinds.isPhysical(demon) && ECDamageKinds.isPhysical(victim.damageSources().mobAttack(zombie)), "默认表下 demon、怪物近战都算物理");

            ECDamageKinds.Config.setTestOverrides(List.of(EssentialCraft5.MODID), List.of());
            helper.assertTrue(ECDamageKinds.isMagic(demon) && !ECDamageKinds.isPhysical(demon), "essentialcraft5 放进魔法命名空间后 demon 算魔法、不算物理");
            helper.assertTrue(ECDamageKinds.isPhysical(victim.damageSources().mobAttack(zombie)), "原版近战不受影响");
            runNoDodge(() -> victim.hurt(demon, 4F));
            helper.assertTrue(Math.abs(victim.getHealth() - 16F) < EPS, "算魔法的伤害不挡，照挨 4 点，实际 " + victim.getHealth());
            for (ItemStack stack : stacks) {
                helper.assertTrue(mru(stack) == 5000, "算魔法的伤害不扣 MRU");
            }

            ECDamageKinds.Config.setTestOverrides(List.of(EssentialCraft5.MODID), List.of(EssentialCraft5.id("demon").toString()));
            helper.assertTrue(!ECDamageKinds.isMagic(demon) && ECDamageKinds.isPhysical(demon), "demon 进了例外表：不算魔法，照旧按近战算物理");
        } finally {
            ECDamageKinds.Config.setTestOverrides(null, null);
        }
        helper.succeed();
    }

    /**
     * 混合端（{@link RayHitGate#setHybridForTest} 模拟）：LivingHurtEvent 里只算哪几件挡、各花多少，照常把这一下取消，但不扣；
     * 纯 Forge 里取消以后不发 LivingDamageEvent，正好等于插件拦下——四件一点不扣，记录还在等。
     * 混合端上插件放行时哪怕伤害是 0 也发 LivingDamageEvent：别的伤害源对象认不出这一下；同一个伤害源对象来了才扣 400 / 1 200 / 800 / 400，只扣一次
     */
    @GameTest(template = EMPTY)
    public static void voidArmorHybridPaysOnlyWhenDamageApplies(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 5000, 5000, 5000, 5000);
        Arrow arrow = new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ());
        DamageSource source = victim.damageSources().arrow(arrow, null);
        DamageCounter counter = count(victim);
        RayHitGate.setHybridForTest(true);
        try {
            runNoDodge(() -> victim.hurt(source, 8F));
            helper.assertTrue(victim.getHealth() == 20F && counter.count == 0, "整下挡掉：不掉血，没发 LivingDamageEvent（等于插件拦下）");
            for (ItemStack stack : stacks) {
                helper.assertTrue(mru(stack) == 5000, "没到 LivingDamageEvent 时一点不扣，实际 " + mru(stack));
            }
            List<MRUShield.Charge> pending = VoidArmorEvents.pendingCharges(victim, source);
            helper.assertTrue(pending.equals(List.of(new MRUShield.Charge(ArmorItem.Type.HELMET, 400), new MRUShield.Charge(ArmorItem.Type.CHESTPLATE, 1200),
                    new MRUShield.Charge(ArmorItem.Type.LEGGINGS, 800), new MRUShield.Charge(ArmorItem.Type.BOOTS, 400))), "应记下四件的花费，实际 " + pending);

            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, victim.damageSources().arrow(arrow, null), 0F));
            for (ItemStack stack : stacks) {
                helper.assertTrue(mru(stack) == 5000, "别的伤害源对象的 LivingDamageEvent 不算这一下，实际 " + mru(stack));
            }

            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, source, 0F));
            int[] cost = {400, 1200, 800, 400};
            for (ArmorItem.Type type : TYPES) {
                int left = mru(stacks[type.ordinal()]);
                helper.assertTrue(left == 5000 - cost[type.ordinal()], type + " 放行后应扣 " + cost[type.ordinal()] + "，剩 " + left);
            }
            helper.assertTrue(VoidArmorEvents.pendingCharges(victim, source).isEmpty(), "扣过的记录应去掉");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, source, 0F));
            helper.assertTrue(mru(stacks[1]) == 3800, "同一下只扣一次，实际 " + mru(stacks[1]));
        } finally {
            RayHitGate.setHybridForTest(null);
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        helper.succeed();
    }

    /**
     * 同一个场面纯 Forge 和混合端各打一遍，结论要一样：身上储存物品只剩 500、靴子只剩 300，挨 8 点箭——
     * 头盔由储存物品付 400（剩 100），胸甲、护腿自己付，靴子要 400：储存物品只剩 100、自己只有 300，扣不起就不挡，挨 1.2 点。
     * 混合端只算不扣时储存物品按预算一笔笔减，不会让靴子也算成「储存物品付得起」（那样整下挡掉、伤害不生效，一点都不扣）
     */
    @GameTest(template = EMPTY)
    public static void voidArmorHybridMatchesForgeWhenAllowed(GameTestHelper helper) {
        String[] labels = {"纯 Forge", "混合端"};
        for (int round = 0; round < 2; round++) {
            Player victim = mock(helper, 1.5D + 2D * round, 1.5D);
            ItemStack storage = MRUStaffItem.full(ECItems.RED_SOUL_MATTER.get());
            storage.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(500));
            victim.getInventory().setItem(9, storage);
            ItemStack[] stacks = wear(victim, 5000, 5000, 5000, 300);
            RayHitGate.setHybridForTest(round == 1);
            try {
                runNoDodge(() -> victim.hurt(victim.damageSources().arrow(new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ()), null), 8F));
            } finally {
                RayHitGate.setHybridForTest(null);
            }
            String label = labels[round];
            helper.assertTrue(Math.abs(victim.getHealth() - 18.8F) < EPS, label + "：靴子那 3/20 没挡，应挨 1.2 点，实际生命 " + victim.getHealth());
            helper.assertTrue(mru(storage) == 100, label + "：储存物品只付头盔的 400，剩 " + mru(storage));
            helper.assertTrue(mru(stacks[0]) == 5000 && mru(stacks[1]) == 3800 && mru(stacks[2]) == 4200 && mru(stacks[3]) == 300,
                    label + "：头盔不动、胸甲 3800、护腿 4200、靴子 300 不动，实际 " + mru(stacks[0]) + " / " + mru(stacks[1]) + " / " + mru(stacks[2]) + " / " + mru(stacks[3]));
        }
        helper.succeed();
    }

    /** 耐久：玩家穿着时原版扣耐久的地方扣 0；别的生物穿着照常扣（1.20.1 原版生物挨打本来就不扣盔甲耐久，这里直接走 hurtAndBreak） */
    @GameTest(template = EMPTY)
    public static void voidArmorDurabilityOnlyForMobs(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack worn = withMRU(ArmorItem.Type.CHESTPLATE, 0);
        player.setItemSlot(EquipmentSlot.CHEST, worn);
        worn.hurtAndBreak(5, player, p -> { });
        helper.assertTrue(worn.getDamageValue() == 0, "玩家身上的不掉耐久，实际 " + worn.getDamageValue());
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        ItemStack mobWorn = withMRU(ArmorItem.Type.CHESTPLATE, 5000);
        zombie.setItemSlot(EquipmentSlot.CHEST, mobWorn);
        mobWorn.hurtAndBreak(5, zombie, z -> { });
        helper.assertTrue(mobWorn.getDamageValue() == 5, "生物身上的照常掉耐久，实际 " + mobWorn.getDamageValue());
        helper.succeed();
    }

    // ================================================================ 闪避、减摔

    /** 掷 0：这一下被取消、不扣 MRU，戴 σ 时攻击者不挨反弹；掷 0.5 照常挡；掉出世界不闪 */
    @GameTest(template = EMPTY)
    public static void voidArmorDodge(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 5000, 5000, 5000, 5000);
        Optional<ICurioStacksHandler> rings = CuriosApi.getCuriosInventory(victim).resolve().map(inv -> inv.getCurios().get(CuriosCompat.RING));
        helper.assertTrue(rings.isPresent() && rings.get().getSlots() > 0, "测试前提：mock 玩家有 Curios 指环位");
        rings.get().getStacks().setStackInSlot(0, new ItemStack(ECBaubles.SIGMA_RING.get()));
        CuriosCompat.invalidate(victim);
        helper.assertTrue(SpecialBaubleItem.isWearer(victim), "测试前提：戴上了 σ");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        float zombieHealth = zombie.getHealth();
        VoidArmorEvents.setTestDodgeRoll(0F);
        List<S2CGearFxPacket> sent = new ArrayList<>();
        try {
            // σ 每下 10% 反弹：60 下都没被取消的话，攻击者几乎一定挨到（0.9^60 ≈ 0.2%）
            S2CGearFxPacket.setTestSink(sent::add);
            try {
                for (int i = 0; i < 60; i++) {
                    victim.invulnerableTime = 0;
                    victim.hurt(victim.damageSources().mobAttack(zombie), rawFor(helper, 8F));
                }
            } finally {
                S2CGearFxPacket.setTestSink(null);
            }
            long dodges = sent.stream().filter(p -> p.kind() == GearFx.VOID_DODGE && p.entity() == victim.getId()).count();
            helper.assertTrue(dodges == 1, "同一刻闪避 60 下只发一个闪避事件（限流），实际 " + dodges);
            helper.assertTrue(victim.getHealth() == 20F, "闪避的那下不掉血");
            for (ItemStack stack : stacks) {
                helper.assertTrue(mru(stack) == 5000, "闪避不扣 MRU");
            }
            helper.assertTrue(zombie.getHealth() == zombieHealth, "闪避取消了事件，σ 收不到，攻击者不应挨反弹");

            victim.invulnerableTime = 0;
            victim.hurt(victim.damageSources().fellOutOfWorld(), 2F);
            helper.assertTrue(Math.abs(victim.getHealth() - 18F) < EPS, "掉出世界不闪，实际 " + victim.getHealth());

            VoidArmorEvents.setTestDodgeRoll(0.5F);
            victim.invulnerableTime = 0;
            victim.hurt(victim.damageSources().arrow(new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ()), null), 8F);
            helper.assertTrue(mru(stacks[1]) == 3800, "掷 0.5 不闪，照常挡、扣 MRU");
        } finally {
            VoidArmorEvents.setTestDodgeRoll(null);
        }
        helper.succeed();
    }

    /** 靴子：摔落 10 → 1，不扣 MRU；别的伤害不减；没穿靴子摔 10 照挨 10 */
    @GameTest(template = EMPTY)
    public static void voidArmorBootsReduceFall(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack boots = withMRU(ArmorItem.Type.BOOTS, 5000);
        victim.setItemSlot(EquipmentSlot.FEET, boots);
        victim.hurt(victim.damageSources().fall(), 10F);
        helper.assertTrue(Math.abs(victim.getHealth() - 19F) < EPS, "摔落 10 应只剩 1，实际掉 " + (20F - victim.getHealth()));
        helper.assertTrue(mru(boots) == 5000, "摔落不是物理伤害，不扣 MRU");
        victim.invulnerableTime = 0;
        victim.hurt(victim.damageSources().cactus(), 4F);
        helper.assertTrue(Math.abs(victim.getHealth() - 15F) < EPS, "仙人掌不减，实际 " + victim.getHealth());

        Player bare = mock(helper, 3.5D, 3.5D);
        bare.hurt(bare.damageSources().fall(), 10F);
        helper.assertTrue(Math.abs(bare.getHealth() - 10F) < EPS, "没穿靴子照挨 10");
        helper.succeed();
    }

    // ================================================================ 头盔饱食

    /** 真玩家打死僵尸：+2 饥饿、+4 饱和；假玩家、没戴、受害者是玩家、死亡被取消都不加 */
    @GameTest(template = EMPTY)
    public static void voidArmorHelmetFeeds(GameTestHelper helper) {
        ServerPlayer killer = ProtectionFrameworkTests.realPlayer(helper);
        killer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECVoidArmor.VOID_HELMET.get()));
        killer.getFoodData().setFoodLevel(10);
        killer.getFoodData().setSaturation(0F);
        Zombie first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 3));
        first.hurt(killer.damageSources().playerAttack(killer), 1000F);
        helper.assertTrue(first.isDeadOrDying(), "测试前提：僵尸被打死");
        helper.assertTrue(killer.getFoodData().getFoodLevel() == 12 && Math.abs(killer.getFoodData().getSaturationLevel() - 4F) < EPS,
                "最大生命 20 的僵尸：+2 饥饿、+4 饱和，实际 " + killer.getFoodData().getFoodLevel() + " / " + killer.getFoodData().getSaturationLevel());

        // 死亡被别的模组取消（救活）：不加
        Zombie saved = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        Consumer<LivingDeathEvent> revive = event -> {
            if (event.getEntity() == saved) {
                event.setCanceled(true);
                saved.setHealth(1F);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDeathEvent.class, revive);
        try {
            saved.hurt(killer.damageSources().playerAttack(killer), 1000F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(revive);
        }
        helper.assertTrue(saved.isAlive() && killer.getFoodData().getFoodLevel() == 12, "死亡被取消时不应加饱食");

        // 受害者是玩家：不加
        Player victim = mock(helper, 1.5D, 1.5D);
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(victim, killer.damageSources().playerAttack(killer)));
        helper.assertTrue(killer.getFoodData().getFoodLevel() == 12, "打死玩家不加饱食");

        // 假玩家戴着也不加
        ServerPlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        fake.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECVoidArmor.VOID_HELMET.get()));
        fake.getFoodData().setFoodLevel(10);
        Zombie byFake = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        byFake.hurt(fake.damageSources().playerAttack(fake), 1000F);
        helper.assertTrue(byFake.isDeadOrDying() && fake.getFoodData().getFoodLevel() == 10, "假玩家打死的不加");
        fake.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        fake.getFoodData().setFoodLevel(20);

        // 没戴：不加
        killer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        Zombie bare = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        bare.hurt(killer.damageSources().playerAttack(killer), 1000F);
        helper.assertTrue(bare.isDeadOrDying() && killer.getFoodData().getFoodLevel() == 12, "没戴头盔不加");
        helper.succeed();
    }

    // ================================================================ 配方

    /** 四个配方：第二行中间是同部位的魔力防护（被消耗），胸甲两个 MRU 反应核、其余两颗下界之星；手册打勾进度在 */
    @GameTest(template = EMPTY)
    public static void voidArmorRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item reactor = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("void_mru_reactor"));
        Item core = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("void_core"));
        Item plating = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("void_plating"));
        Item eternity = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("matter_of_eternity"));
        for (ArmorItem.Type type : TYPES) {
            String piece = type.getName();
            Item mhs = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("mhs_" + piece));
            boolean chest = type == ArmorItem.Type.CHESTPLATE;
            Item[] grid = {
                    chest ? reactor : Items.NETHER_STAR, chest ? Items.NETHER_STAR : reactor, chest ? reactor : Items.NETHER_STAR,
                    core, mhs, core,
                    plating, eternity, plating};
            CraftingContainer container = new TransientCraftingContainer(new DummyMenu(), 3, 3);
            for (int i = 0; i < 9; i++) {
                container.setItem(i, new ItemStack(grid[i]));
            }
            Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level);
            helper.assertTrue(recipe.isPresent() && recipe.get().getId().equals(EssentialCraft5.id("void_armor/void_" + piece)),
                    "虚空" + piece + " 应有配方，实际 " + recipe.map(r -> r.getId().toString()).orElse("没有"));
            ItemStack out = recipe.get().assemble(container, level.registryAccess());
            helper.assertTrue(out.is(item(type)) && out.getCount() == 1, piece + " 产物");
            NonNullList<ItemStack> remaining = recipe.get().getRemainingItems(container);
            helper.assertTrue(remaining.stream().allMatch(ItemStack::isEmpty), piece + "：魔力防护和别的原料都应被消耗");
        }
        ResourceLocation guide = EssentialCraft5.id("guide/gear/void_armor");
        helper.assertTrue(level.getServer().getAdvancements().getAdvancement(guide) != null, "应有手册打勾进度 " + guide);
        helper.succeed();
    }

    /** TransientCraftingContainer 要一个菜单通知格子变化，测试里用个空的 */
    private static final class DummyMenu extends AbstractContainerMenu {
        DummyMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
