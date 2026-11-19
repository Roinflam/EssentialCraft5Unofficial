package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.ComputerArmorEvents;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.VoidArmorItem;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 宅男套装（第八批 M2，batch8.md §4.2；挡伤害 D5 = B、生命 / 饱食 / 移速 D6 = A）的 GameTest：
 * <ul>
 *   <li>材料与提示：耐久 16 384、附魔 127、没有修复材料、创造栏空 / 满两份（满 = 1 000 000）、不是储存物品、MRU 抗性 1 / 1 / 1、头盔看得见 MRU。</li>
 *   <li>属性：护甲 6 / 16 / 12 / 6、韧性 6、击退抗性 0.2、生命 20 / 40 / 20 / 20、护腿移速 0.075、靴子登高 0.6，空、满一样；全套最大生命 120、
 *       护甲夹到 30、韧性 20；脱下夹回生命；热改成 0 再脱下不留修饰符。</li>
 *   <li>有电挡伤害（框架 MRUShield + 本模块的 IMRUShieldArmor）：近战 8 点四件各扣 250 / 750 / 500 / 250、不掉血、不发 LivingDamageEvent；
 *       岩浆、爆炸（元素）也挡；扣不起的那件不挡、MRU 不动；没电的那件不挡；无视护甲的不挡；轨道打击既不是物理也不是元素，照样破防；
 *       挡哪些按 shield_scope 换；护甲破坏者按接口扣 5 × 250。</li>
 *   <li>胸甲免疫 magic / wither / starve、不免疫 indirect_magic；靴子免摔（含落到滴水石锥上）；护腿起跳 +0.3、着火给抗火；头盔补氧气、饥饿值，饱和度按配置。</li>
 *   <li>挡哪些：带火标签的魔法伤害默认不挡、ALL 照挡（把原版命名空间临时列成魔法命名空间来造）。</li>
 *   <li>反伤：攻击者挨 50% 荆棘伤害（攻击者 = 穿戴者），整下挡掉也照反；荆棘、饰品伤害不反；不反给自己；不到 0.5 点不反；不是全套不反；
 *       没有直接实体的（轨道打击、射线枪）不反、箭照反；攻击在 LivingAttackEvent 被拦下不反；混合端（{@code RayHitGate.setHybridForTest}）等 LivingDamageEvent 到了才反、才扣电，被盾牌整下挡掉的不反不扣。</li>
 *   <li>穿着不掉耐久（别的生物照掉）；全套游泳加成随换装加上 / 去掉。</li>
 *   <li>夜视包：要戴头盔；给的是隐藏图标的那份，剩不到 300 刻才续（剩 280 续），喝的药水不碰；摘头盔、关掉时只删自己那份；同一刻连按只认一次。
 *       没给过的玩家身上长得一样的隐藏夜视（命令给的）不删；无限时长的、放大等级更高的不改图标、不删。</li>
 *   <li>冲刺包：要全套；速度设成视线 × 3；冷却 20 刻内再按无效、过了冷却又能冲；骑乘、旁观时不能冲；配置要花 MRU 时扣不起不冲。</li>
 * </ul>
 * 挨打的一方用 mock 玩家（没有出生无敌、不用 tick）；夜视、冲刺、每 20 刻的检查要 ServerPlayer，用 {@link ProtectionFrameworkTests#realPlayer}。
 * mock 玩家不 tick，穿上的盔甲属性不会自己加上，要看属性的测试按原版换装的写法手动加修饰符。僵尸近战按难度缩放，raw 由 {@link #rawFor} 反推。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ComputerArmorTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 1.0E-4F;
    private static final ArmorItem.Type[] TYPES = ArmorItem.Type.values();
    private static final int FULL = 1_000_000;

    private ComputerArmorTests() {
    }

    // ================================================================ 工具

    private static ComputerArmorItem item(ArmorItem.Type type) {
        return ECComputer.armor().get(type.ordinal()).get();
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
    private static ItemStack[] wear(LivingEntity entity, int helmet, int chest, int legs, int boots) {
        int[] mru = {helmet, chest, legs, boots};
        ItemStack[] stacks = new ItemStack[4];
        for (ArmorItem.Type type : TYPES) {
            stacks[type.ordinal()] = withMRU(type, mru[type.ordinal()]);
            entity.setItemSlot(type.getSlot(), stacks[type.ordinal()]);
        }
        return stacks;
    }

    /** 原版换装时做的事：把这几件的修饰符加到实体上 */
    private static void applyModifiers(LivingEntity entity, ItemStack... stacks) {
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof ArmorItem armor) {
                entity.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(armor.getEquipmentSlot()));
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
        private final LivingEntity target;
        int count;
        float last = -1F;

        DamageCounter(LivingEntity target) {
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

    private static DamageCounter count(LivingEntity target) {
        DamageCounter counter = new DamageCounter(target);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDamageEvent.class, counter);
        return counter;
    }

    /** 记下某个实体挨的荆棘伤害（LivingHurtEvent HIGHEST，护甲之前的数）：几次、最后一次的数和攻击者 */
    private static final class ThornsProbe implements Consumer<LivingHurtEvent> {
        private final LivingEntity target;
        int count;
        float amount = -1F;
        Object attacker;

        ThornsProbe(LivingEntity target) {
            this.target = target;
        }

        @Override
        public void accept(LivingHurtEvent event) {
            if (event.getEntity() == target && event.getSource().is(DamageTypes.THORNS)) {
                count++;
                amount = event.getAmount();
                attacker = event.getSource().getEntity();
            }
        }
    }

    private static ThornsProbe thorns(LivingEntity target) {
        ThornsProbe probe = new ThornsProbe(target);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingHurtEvent.class, probe);
        return probe;
    }

    private static void runWith(ECComputer.Config.Values values, Runnable body) {
        ECComputer.Config.setTestOverrides(values);
        try {
            body.run();
        } finally {
            ECComputer.Config.setTestOverrides(null);
        }
    }

    private static Arrow arrow(GameTestHelper helper, Player victim) {
        return new Arrow(helper.getLevel(), victim.getX(), victim.getY() + 3D, victim.getZ());
    }

    // ================================================================ 材料、提示、抗性

    /** 耐久 16 384、附魔 127、没有修复材料；创造栏空 / 满两份；存 1 000 000、不是储存物品；抗性全 1；头盔看得见 MRU */
    @GameTest(template = EMPTY)
    public static void computerArmorMaterial(GameTestHelper helper) {
        for (ArmorItem.Type type : TYPES) {
            ComputerArmorItem armor = item(type);
            ItemStack stack = new ItemStack(armor);
            helper.assertTrue(stack.getMaxDamage() == 16384, type + " 耐久应是 16384，实际 " + stack.getMaxDamage());
            helper.assertTrue(armor.getEnchantmentValue() == 127, type + " 附魔能力应是 127");
            helper.assertTrue(ECComputer.MATERIAL.getRepairIngredient().isEmpty(), "没有修复材料");
            List<ItemStack> variants = armor.creativeVariants();
            helper.assertTrue(variants.size() == 2 && mru(variants.get(0)) == 0 && mru(variants.get(1)) == FULL, type + " 创造栏应是空、满两份");
            IMRUHandlerItem handler = stack.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
            helper.assertTrue(handler != null && handler.getMaxMRU() == FULL && !handler.isStorage(), type + " 存 1 000 000 MRU、不是储存物品");
            for (MRUResistance.Type resistance : MRUResistance.Type.values()) {
                helper.assertTrue(armor.getResistance(stack, resistance) == 1F, type + " 的 " + resistance + " 抗性应是 1");
            }
            helper.assertTrue(armor.shieldShare(type) == new int[]{3, 8, 6, 3}[type.ordinal()], type + " 的份额");
        }
        ItemStack helmet = new ItemStack(ECComputer.COMPUTER_HELMET.get());
        helper.assertTrue(helmet.is(ECTags.MRU_VISION) && MRUVision.grantsVision(helmet), "头盔应在 mru_vision 里（要先跑 runData）");
        helper.assertFalse(new ItemStack(ECComputer.COMPUTER_CHESTPLATE.get()).is(ECTags.MRU_VISION), "胸甲看不见 MRU");
        Player player = mock(helper, 1.5D, 1.5D);
        wear(player, 0, 0, 0, 0);
        helper.assertTrue(MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION) == 0F
                && MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION) == 0F, "全套对腐化和辐射全免");
        helper.succeed();
    }

    // ================================================================ 属性

    /** 默认属性（空、满一样、只在自己的部位）；护甲用原版 UUID；生命 / 移速 UUID 和虚空套装不重；覆盖值改了跟着变 */
    @GameTest(template = EMPTY)
    public static void computerArmorAttributes(GameTestHelper helper) {
        double[] armorValues = {6D, 16D, 12D, 6D};
        double[] health = {20D, 40D, 20D, 20D};
        Attribute step = ForgeMod.STEP_HEIGHT_ADDITION.get();
        for (ArmorItem.Type type : TYPES) {
            EquipmentSlot slot = type.getSlot();
            for (ItemStack stack : List.of(withMRU(type, 0), withMRU(type, FULL))) {
                String what = type + "（MRU " + mru(stack) + "）";
                helper.assertTrue(sum(stack, slot, Attributes.ARMOR) == armorValues[type.ordinal()], what + " 护甲应是 " + armorValues[type.ordinal()]);
                helper.assertTrue(sum(stack, slot, Attributes.ARMOR_TOUGHNESS) == 6D, what + " 韧性应是 6");
                helper.assertTrue(Math.abs(sum(stack, slot, Attributes.KNOCKBACK_RESISTANCE) - 0.2D) < 1e-9, what + " 击退抗性应是 0.2");
                helper.assertTrue(sum(stack, slot, Attributes.MAX_HEALTH) == health[type.ordinal()], what + " 生命应 +" + health[type.ordinal()]);
                boolean legs = type == ArmorItem.Type.LEGGINGS;
                helper.assertTrue(stack.getAttributeModifiers(slot).get(Attributes.MOVEMENT_SPEED).size() == 1
                        && Math.abs(sum(stack, slot, Attributes.MOVEMENT_SPEED) - (legs ? 0.075D : 0D)) < 1e-9, what + " 移速（四件都放修饰符，只有护腿不是 0）");
                helper.assertTrue(Math.abs(sum(stack, slot, step) - (type == ArmorItem.Type.BOOTS ? 0.6D : 0D)) < 1e-9, what + " 登高");
                EquipmentSlot other = type == ArmorItem.Type.HELMET ? EquipmentSlot.CHEST : EquipmentSlot.HEAD;
                helper.assertTrue(stack.getAttributeModifiers(other).isEmpty(), what + " 不在自己的部位没有属性");
            }
            helper.assertFalse(ComputerArmorItem.healthModifierId(type).equals(VoidArmorItem.healthModifierId(type)),
                    type + " 的生命修饰符 UUID 不应和虚空套装一样");
        }
        UUID vanilla = new ItemStack(Items.NETHERITE_CHESTPLATE).getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR).iterator().next().getId();
        UUID ours = new ItemStack(ECComputer.COMPUTER_CHESTPLATE.get()).getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR).iterator().next().getId();
        helper.assertTrue(vanilla.equals(ours), "护甲修饰符应用原版同部位的 UUID");

        ItemStack legs = withMRU(ArmorItem.Type.LEGGINGS, 0);
        runWith(ECComputer.Config.DEFAULTS.toBuilder().stats(1D, 2D, 3D, 4D, 5D, 0.5D, 7D, 9D, 0.2D).build(), () ->
                helper.assertTrue(sum(legs, EquipmentSlot.LEGS, Attributes.ARMOR) == 3D && sum(legs, EquipmentSlot.LEGS, Attributes.ARMOR_TOUGHNESS) == 5D
                        && sum(legs, EquipmentSlot.LEGS, Attributes.MAX_HEALTH) == 7D
                        && Math.abs(sum(legs, EquipmentSlot.LEGS, Attributes.MOVEMENT_SPEED) - 0.2D) < 1e-9, "覆盖值改了属性应跟着变"));
        helper.assertTrue(sum(legs, EquipmentSlot.LEGS, Attributes.ARMOR) == 12D, "覆盖值去掉后回到默认");
        helper.succeed();
    }

    /** 全套：最大生命 120、护甲夹到 30、韧性 20；脱下下一刻夹回生命；同一件只是 MRU 变了不算脱下 */
    @GameTest(template = EMPTY)
    public static void computerArmorFullSetHealthAndClamp(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(player, 0, 0, 0, 0);
        applyModifiers(player, stacks);
        helper.assertTrue(player.getMaxHealth() == 120F, "全套最大生命应是 20 + 100，实际 " + player.getMaxHealth());
        helper.assertTrue(player.getArmorValue() == 30, "全套护甲 40 被原版夹到 30，实际 " + player.getArmorValue());
        helper.assertTrue(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 20D, "全套韧性 24 被原版夹到 20");
        player.setHealth(120F);

        ItemStack charged = stacks[1].copy();
        charged.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(1000));
        MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.CHEST, stacks[1], charged));
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
        helper.assertTrue(player.getHealth() == 120F, "MRU 变了不算脱下，生命应还是 120，实际 " + player.getHealth());

        for (ArmorItem.Type type : TYPES) {
            MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, type.getSlot(), stacks[type.ordinal()], ItemStack.EMPTY));
            player.getAttributes().removeAttributeModifiers(stacks[type.ordinal()].getAttributeModifiers(type.getSlot()));
            player.setItemSlot(type.getSlot(), ItemStack.EMPTY);
        }
        helper.assertTrue(player.getMaxHealth() == 20F && player.getHealth() == 120F, "原版去掉修饰符时生命不跟着夹（测试前提）");
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
        helper.assertTrue(player.getHealth() == 20F, "脱下后下一刻生命应夹回 20，实际 " + player.getHealth());
        helper.succeed();
    }

    /** 穿着胸甲时生命热改成 0 再脱下：表里照样有这条（数额 0），按 UUID 去掉穿上时加的那条，最大生命回到 20 */
    @GameTest(template = EMPTY)
    public static void computerArmorConfigZeroRemovesModifiers(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack chest = withMRU(ArmorItem.Type.CHESTPLATE, 0);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        applyModifiers(player, chest);
        helper.assertTrue(player.getMaxHealth() == 60F, "测试前提：穿着胸甲最大生命 60，实际 " + player.getMaxHealth());
        runWith(ECComputer.Config.DEFAULTS.toBuilder().stats(6D, 16D, 12D, 6D, 6D, 0D, 0D, 0D, 0D).build(), () -> {
            helper.assertTrue(chest.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.MAX_HEALTH).size() == 1,
                    "生命为 0 时修饰符照样在（数额 0）");
            player.getAttributes().removeAttributeModifiers(chest.getAttributeModifiers(EquipmentSlot.CHEST));
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            helper.assertTrue(player.getMaxHealth() == 20F, "配置改成 0 后脱下，最大生命应回到 20，实际 " + player.getMaxHealth());
            helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH).getModifier(ComputerArmorItem.healthModifierId(ArmorItem.Type.CHESTPLATE)) == null,
                    "生命修饰符不应留在身上");
        });
        helper.succeed();
    }

    // ================================================================ 有电挡伤害

    /** 满电全套挨僵尸近战 8 点：不掉血、不发 LivingDamageEvent、四件各扣 250 / 750 / 500 / 250、耐久不动 */
    @GameTest(template = EMPTY)
    public static void computerArmorBlocksMelee(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        ItemStack[] stacks = wear(victim, FULL, FULL, FULL, FULL);
        DamageCounter counter = count(victim);
        try {
            victim.hurt(victim.damageSources().mobAttack(zombie), rawFor(helper, 8F));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        helper.assertTrue(victim.getHealth() == 20F, "满电全套挡下 8 点近战，不应掉血，实际 " + victim.getHealth());
        helper.assertTrue(counter.count == 0, "挡完了这次伤害被取消，不应发 LivingDamageEvent");
        int[] cost = {250, 750, 500, 250};
        for (ArmorItem.Type type : TYPES) {
            ItemStack stack = stacks[type.ordinal()];
            helper.assertTrue(mru(stack) == FULL - cost[type.ordinal()], type + " 应扣 " + cost[type.ordinal()] + "，剩 " + mru(stack));
            helper.assertTrue(stack.getDamageValue() == 0, type + " 耐久不应变");
        }
        helper.succeed();
    }

    /** 元素伤害也挡（D5 = B）：岩浆 8 点、爆炸 8 点都整下挡掉；箭（弹射物）也挡 */
    @GameTest(template = EMPTY)
    public static void computerArmorBlocksElementalAndProjectile(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, FULL, FULL, FULL, FULL);
        // 爆炸的伤害类型按难度缩放（scaling always），传反推的数；岩浆、没有主人的箭不缩放
        List<DamageSource> sources = List.of(victim.damageSources().lava(), victim.damageSources().explosion(null, null),
                victim.damageSources().arrow(arrow(helper, victim), null));
        int round = 0;
        for (DamageSource source : sources) {
            round++;
            victim.invulnerableTime = 0;
            victim.hurt(source, source.is(DamageTypeTags.IS_EXPLOSION) ? rawFor(helper, 8F) : 8F);
            helper.assertTrue(victim.getHealth() == 20F, source.getMsgId() + " 应整下挡掉，实际生命 " + victim.getHealth());
            helper.assertTrue(mru(stacks[1]) == FULL - 750 * round, source.getMsgId() + " 胸甲应再扣 750，剩 " + mru(stacks[1]));
        }
        helper.succeed();
    }

    /** 胸甲只剩 500（要 750）：胸甲不挡、MRU 不动；另外三件照挡，剩下 8 × 8/20 = 3.2 点照常挨；没电的靴子不挡 */
    @GameTest(template = EMPTY)
    public static void computerArmorUnaffordablePieceDoesNotBlock(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(victim, FULL, 500, FULL, FULL);
        DamageCounter counter = count(victim);
        try {
            victim.hurt(victim.damageSources().arrow(arrow(helper, victim), null), 8F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        helper.assertTrue(mru(stacks[1]) == 500, "胸甲扣不起不应扣，实际 " + mru(stacks[1]));
        helper.assertTrue(mru(stacks[0]) == FULL - 250 && mru(stacks[2]) == FULL - 500 && mru(stacks[3]) == FULL - 250, "另外三件照挡照扣");
        helper.assertTrue(counter.count == 1 && Math.abs(counter.last - 3.2F) < EPS, "胸甲那 8/20 没挡，应挨 3.2 点（护甲之后更少），实际事件里 " + counter.last);

        Player second = mock(helper, 3.5D, 1.5D);
        ItemStack[] empty = wear(second, FULL, FULL, FULL, 0);
        DamageCounter secondCounter = count(second);
        try {
            second.hurt(second.damageSources().arrow(arrow(helper, second), null), 8F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(secondCounter);
        }
        helper.assertTrue(mru(empty[3]) == 0 && secondCounter.count == 1 && Math.abs(secondCounter.last - 1.2F) < EPS,
                "没电的靴子不挡，3/20 照常挨 1.2 点，实际 " + secondCounter.last);
        helper.succeed();
    }

    /**
     * 不在范围里的不挡、不扣：无视护甲的（挤压）、轨道打击（既不是物理也不是元素，照样破防）；shield_scope 换成 ALL 时轨道打击和荆棘也挡，PHYSICAL 时岩浆不挡；
     * 带火标签的魔法伤害默认不挡、ALL 照挡
     */
    @GameTest(template = EMPTY)
    public static void computerArmorShieldScope(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        ItemStack[] stacks = wear(victim, FULL, FULL, FULL, FULL);
        DamageSource cramming = victim.damageSources().cramming();
        helper.assertTrue(cramming.is(DamageTypeTags.BYPASSES_ARMOR), "测试前提：挤压无视护甲");
        victim.hurt(cramming, 4F);
        helper.assertTrue(victim.getHealth() == 16F && mru(stacks[1]) == FULL, "无视护甲的不挡不扣，实际生命 " + victim.getHealth());

        DamageSource orbital = ECDamageTypes.orbitalStrike(helper.getLevel(), zombie);
        ComputerArmorItem chest = item(ArmorItem.Type.CHESTPLATE);
        helper.assertFalse(chest.shields(stacks[1], orbital), "轨道打击既不是物理也不是元素，默认不挡");
        victim.invulnerableTime = 0;
        victim.hurt(orbital, 4F);
        helper.assertTrue(victim.getHealth() < 16F && mru(stacks[1]) == FULL, "轨道打击照样破防、不扣电，实际生命 " + victim.getHealth());

        DamageSource thorns = victim.damageSources().thorns(zombie);
        DamageSource lava = victim.damageSources().lava();
        helper.assertFalse(chest.shields(stacks[1], thorns), "荆棘算魔法，默认不挡");
        runWith(ECComputer.Config.DEFAULTS.toBuilder().shieldScope(ECComputer.ShieldScope.ALL).build(), () -> {
            helper.assertTrue(chest.shields(stacks[1], orbital) && chest.shields(stacks[1], thorns), "ALL：轨道打击、荆棘也挡");
            float before = victim.getHealth();
            victim.invulnerableTime = 0;
            victim.hurt(orbital, 4F);
            helper.assertTrue(victim.getHealth() == before && mru(stacks[1]) == FULL - 250, "ALL：轨道打击整下挡掉，胸甲扣 1 点 × 250");
        });
        runWith(ECComputer.Config.DEFAULTS.toBuilder().shieldScope(ECComputer.ShieldScope.PHYSICAL).build(), () ->
                helper.assertFalse(chest.shields(stacks[1], lava), "PHYSICAL：岩浆不挡"));
        helper.assertTrue(chest.shields(stacks[1], lava), "默认（物理 + 元素）挡岩浆");

        // 带火标签的魔法伤害（法术模组的火球这类）：把原版命名空间临时列成魔法命名空间，岩浆就成了「火 + 魔法」。设、测、置回在同一段同步代码里，不跨刻
        try {
            ECDamageKinds.Config.setTestOverrides(List.of("minecraft"), null);
            helper.assertTrue(ECDamageKinds.isMagic(lava) && ECDamageKinds.isElemental(lava), "测试前提：岩浆成了火 + 魔法");
            helper.assertFalse(chest.shields(stacks[1], lava), "默认：带火标签的魔法伤害不挡");
            runWith(ECComputer.Config.DEFAULTS.toBuilder().shieldScope(ECComputer.ShieldScope.ALL).build(), () ->
                    helper.assertTrue(chest.shields(stacks[1], lava), "ALL：带火标签的魔法伤害照挡"));
        } finally {
            ECDamageKinds.Config.setTestOverrides(null, null);
        }
        helper.succeed();
    }

    /** 护甲破坏者认的接口：宅男盔甲每件扣 5 × 250 = 1 250 */
    @GameTest(template = EMPTY)
    public static void computerArmorDestroyerCost(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        wear(player, FULL, FULL, FULL, FULL);
        for (ArmorItem.Type type : TYPES) {
            ItemStack worn = MRUShield.wornPiece(player, type);
            helper.assertTrue(worn != null && worn.getItem() instanceof IMRUShieldArmor shield && 5 * shield.mruPerDamage(worn) == 1250,
                    type + " 应被认成 MRU 盔甲、扣 1250");
        }
        helper.succeed();
    }

    // ================================================================ 四件各自的效果

    /** 胸甲免疫 magic / wither / starve（整下取消、不掉血），不免疫别人扔的 indirect_magic；脱了胸甲照常挨 magic */
    @GameTest(template = EMPTY)
    public static void computerArmorChestImmunities(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        player.setItemSlot(EquipmentSlot.CHEST, withMRU(ArmorItem.Type.CHESTPLATE, 0));
        for (DamageSource source : List.of(player.damageSources().magic(), player.damageSources().wither(), player.damageSources().starve())) {
            helper.assertFalse(player.hurt(source, 4F), source.getMsgId() + " 应被免疫");
        }
        helper.assertTrue(player.getHealth() == 20F, "免疫的伤害不掉血");
        helper.assertTrue(player.hurt(player.damageSources().indirectMagic(zombie, zombie), rawFor(helper, 4F)) && Math.abs(player.getHealth() - 16F) < EPS,
                "别人扔的喷溅伤害药水（indirect_magic）照样打得到，实际生命 " + player.getHealth());
        Player bare = mock(helper, 3.5D, 1.5D);
        helper.assertTrue(bare.hurt(bare.damageSources().magic(), 4F), "没穿胸甲照常挨 magic");
        helper.succeed();
    }

    /** 靴子免摔（整下取消，摔落和落到滴水石锥上都算）；护腿起跳竖直速度 +0.3；没穿的不加 */
    @GameTest(template = EMPTY)
    public static void computerArmorBootsAndLeggings(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        player.setItemSlot(EquipmentSlot.FEET, withMRU(ArmorItem.Type.BOOTS, 0));
        helper.assertFalse(player.hurt(player.damageSources().fall(), 10F), "穿靴子免摔");
        helper.assertTrue(player.getHealth() == 20F, "免摔不掉血");
        DamageSource stalagmite = player.damageSources().stalagmite();
        helper.assertTrue(stalagmite.is(DamageTypeTags.IS_FALL) && ComputerArmorEvents.isImmune(player, stalagmite), "落到滴水石锥上（#is_fall）也免疫");
        helper.assertFalse(player.hurt(stalagmite, 4F), "落到滴水石锥上整下取消");
        helper.assertTrue(player.getHealth() == 20F, "落到滴水石锥上不掉血，实际 " + player.getHealth());
        Player bare = mock(helper, 3.5D, 1.5D);
        helper.assertTrue(bare.hurt(bare.damageSources().fall(), 4F), "没穿靴子照常摔伤");

        player.setItemSlot(EquipmentSlot.LEGS, withMRU(ArmorItem.Type.LEGGINGS, 0));
        player.setDeltaMovement(0D, 0.42D, 0D);
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(player));
        helper.assertTrue(Math.abs(player.getDeltaMovement().y - 0.72D) < 1e-9, "护腿起跳 +0.3，实际 " + player.getDeltaMovement().y);
        bare.setDeltaMovement(0D, 0.42D, 0D);
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(bare));
        helper.assertTrue(Math.abs(bare.getDeltaMovement().y - 0.42D) < 1e-9, "没穿护腿不加");
        helper.succeed();
    }

    /** 每 20 刻：头盔补满氧气和饥饿值，默认不加饱和度（配置开了才顶满）；护腿着火时给 5 秒抗火 */
    @GameTest(template = EMPTY)
    public static void computerArmorHelmetAndFire(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemSlot(EquipmentSlot.HEAD, withMRU(ArmorItem.Type.HELMET, 0));
        player.setAirSupply(10);
        player.getFoodData().setFoodLevel(5);
        player.getFoodData().setSaturation(0F);
        ComputerArmorEvents.periodic(player);
        helper.assertTrue(player.getAirSupply() == player.getMaxAirSupply(), "氧气应补满，实际 " + player.getAirSupply());
        helper.assertTrue(player.getFoodData().getFoodLevel() == 20, "饥饿值应补满");
        helper.assertTrue(player.getFoodData().getSaturationLevel() == 0F, "默认不加饱和度，实际 " + player.getFoodData().getSaturationLevel());
        runWith(ECComputer.Config.DEFAULTS.toBuilder().helmetSaturation(true).build(), () -> ComputerArmorEvents.periodic(player));
        helper.assertTrue(player.getFoodData().getSaturationLevel() == 20F, "配置开了饱和度也顶满");

        player.setItemSlot(EquipmentSlot.LEGS, withMRU(ArmorItem.Type.LEGGINGS, 0));
        ComputerArmorEvents.periodic(player);
        helper.assertTrue(player.getEffect(MobEffects.FIRE_RESISTANCE) == null, "没着火不给抗火");
        player.setSecondsOnFire(5);
        ComputerArmorEvents.periodic(player);
        MobEffectInstance fire = player.getEffect(MobEffects.FIRE_RESISTANCE);
        helper.assertTrue(fire != null && fire.getDuration() == ComputerArmorEvents.FIRE_RESISTANCE_TICKS, "着火时给 100 刻抗火");
        helper.succeed();
    }

    /** 穿在玩家身上不掉耐久，别的生物穿着照常掉 */
    @GameTest(template = EMPTY)
    public static void computerArmorDurability(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack worn = withMRU(ArmorItem.Type.CHESTPLATE, 0);
        player.setItemSlot(EquipmentSlot.CHEST, worn);
        worn.hurtAndBreak(5, player, p -> { });
        helper.assertTrue(worn.getDamageValue() == 0, "玩家身上的不掉耐久，实际 " + worn.getDamageValue());
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        ItemStack mobWorn = withMRU(ArmorItem.Type.CHESTPLATE, FULL);
        zombie.setItemSlot(EquipmentSlot.CHEST, mobWorn);
        mobWorn.hurtAndBreak(5, zombie, z -> { });
        helper.assertTrue(mobWorn.getDamageValue() == 5, "生物身上的照常掉耐久，实际 " + mobWorn.getDamageValue());
        helper.succeed();
    }

    /** 全套游泳加成：四件穿齐换装时加上 2.0，脱一件就去掉 */
    @GameTest(template = EMPTY)
    public static void computerArmorSwimSpeed(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(player, 0, 0, 0, 0);
        MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.FEET, ItemStack.EMPTY, stacks[3]));
        AttributeModifier modifier = player.getAttribute(ForgeMod.SWIM_SPEED.get()).getModifier(ComputerArmorEvents.SWIM_MODIFIER_ID);
        helper.assertTrue(modifier != null && modifier.getAmount() == 2D, "全套应有游泳 +2");
        helper.assertTrue(Math.abs(player.getAttributeValue(ForgeMod.SWIM_SPEED.get()) - 3D) < 1e-9, "游泳速度应是 3 倍");
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.FEET, stacks[3], ItemStack.EMPTY));
        helper.assertTrue(player.getAttribute(ForgeMod.SWIM_SPEED.get()).getModifier(ComputerArmorEvents.SWIM_MODIFIER_ID) == null, "脱一件就去掉");
        helper.succeed();
    }

    // ================================================================ 反伤

    /** 满电全套挨僵尸 8 点：整下挡掉，僵尸照样挨 4 点荆棘伤害（攻击者 = 穿戴者）；半套不反 */
    @GameTest(template = EMPTY)
    public static void computerArmorThornsReflects(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        wear(victim, FULL, FULL, FULL, FULL);
        ThornsProbe probe = thorns(zombie);
        try {
            victim.hurt(victim.damageSources().mobAttack(zombie), rawFor(helper, 8F));
            helper.assertTrue(victim.getHealth() == 20F, "测试前提：整下挡掉");
            helper.assertTrue(probe.count == 1 && Math.abs(probe.amount - 4F) < EPS && probe.attacker == victim,
                    "僵尸应挨 4 点荆棘伤害、攻击者是穿戴者，实际 " + probe.count + " 次 / " + probe.amount);
            helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "僵尸应掉血");

            Player half = mock(helper, 3.5D, 1.5D);
            wear(half, FULL, FULL, FULL, FULL);
            half.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            zombie.invulnerableTime = 0;
            half.hurt(half.damageSources().mobAttack(zombie), rawFor(helper, 8F));
            helper.assertTrue(probe.count == 1, "不是全套不反");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        helper.succeed();
    }

    /** 不反的情况：荆棘伤害、饰品伤害（σ 等打出的）、自己打自己、算出来不到 0.5 点（0.8 × 0.5 = 0.4） */
    @GameTest(template = EMPTY)
    public static void computerArmorThornsSkips(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        wear(victim, FULL, FULL, FULL, FULL);
        ThornsProbe probe = thorns(zombie);
        ThornsProbe self = thorns(victim);
        try {
            victim.hurt(victim.damageSources().thorns(zombie), 8F);
            helper.assertTrue(probe.count == 0, "荆棘伤害不再反（两个穿全套的人不会来回弹）");
            victim.invulnerableTime = 0;
            BaubleEffects.asBaubleDamage(() -> victim.hurt(victim.damageSources().mobAttack(zombie), rawFor(helper, 8F)));
            helper.assertTrue(probe.count == 0, "饰品 / 反伤自己打出的伤害不再反");
            victim.invulnerableTime = 0;
            // 第一下荆棘伤害本身就打在 victim 身上，self 已经数过一次；只数自己打自己这一下之后有没有反回来
            self.count = 0;
            victim.hurt(victim.damageSources().playerAttack(victim), 8F);
            helper.assertTrue(self.count == 0, "自己打自己不反给自己");
            victim.invulnerableTime = 0;
            victim.hurt(victim.damageSources().arrow(arrow(helper, victim), zombie), rawFor(helper, 0.8F));
            helper.assertTrue(probe.count == 0, "0.8 × 0.5 = 0.4 不到 0.5，不反");
            helper.assertTrue(ComputerArmorEvents.thornsAmount(0.8F, 0.5D) == 0F && ComputerArmorEvents.thornsAmount(1F, 0.5D) == 0.5F,
                    "反伤数额：不到 0.5 算 0");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            MinecraftForge.EVENT_BUS.unregister(self);
        }
        helper.succeed();
    }

    /**
     * 没有直接实体的伤害不反：轨道打击（攻击者 = 僵尸）、射线枪的 mru_ray（直接实体为空、攻击者 = 僵尸）都打进来了（默认不挡、掉血），僵尸不挨荆棘伤害；
     * 同一只僵尸射的箭（直接实体是箭）照反
     */
    @GameTest(template = EMPTY)
    public static void computerArmorThornsNeedsDirectEntity(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        wear(victim, FULL, FULL, FULL, FULL);
        DamageSource orbital = ECDamageTypes.orbitalStrike(helper.getLevel(), zombie);
        DamageSource ray = new DamageSource(ECDamageTypes.holder(helper.getLevel(), ECGuns.MRU_RAY), null, zombie);
        helper.assertTrue(orbital.getDirectEntity() == null && orbital.getEntity() == zombie && ray.getDirectEntity() == null && ray.getEntity() == zombie,
                "测试前提：两种伤害只有攻击者、没有直接实体");
        helper.assertFalse(ComputerArmorEvents.reflects(orbital) || ComputerArmorEvents.reflects(ray), "没有直接实体的不反");
        helper.assertTrue(ComputerArmorEvents.reflects(victim.damageSources().mobAttack(zombie)), "近战照反");
        ThornsProbe probe = thorns(zombie);
        try {
            victim.hurt(orbital, 8F);
            float afterOrbital = victim.getHealth();
            helper.assertTrue(afterOrbital < 20F, "测试前提：轨道打击打进来了，实际生命 " + afterOrbital);
            helper.assertTrue(probe.count == 0, "轨道打击不反伤");
            victim.invulnerableTime = 0;
            victim.hurt(ray, 8F);
            helper.assertTrue(victim.getHealth() < afterOrbital, "测试前提：射线打进来了，实际生命 " + victim.getHealth());
            helper.assertTrue(probe.count == 0, "射线枪不反伤");
            helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "僵尸没掉血");

            victim.invulnerableTime = 0;
            victim.hurt(victim.damageSources().arrow(arrow(helper, victim), zombie), rawFor(helper, 8F));
            helper.assertTrue(probe.count == 1 && Math.abs(probe.amount - 4F) < EPS, "箭（直接实体是箭）照反 4 点，实际 " + probe.count + " 次 / " + probe.amount);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        helper.succeed();
    }

    /** 攻击在 LivingAttackEvent 被拦下（纯 Forge 的领地模组在这里拦）：不掉血、不扣电、不反伤 */
    @GameTest(template = EMPTY)
    public static void computerArmorThornsNotWhenAttackDenied(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        ItemStack[] stacks = wear(victim, FULL, FULL, FULL, FULL);
        ThornsProbe probe = thorns(zombie);
        Consumer<LivingAttackEvent> claim = event -> {
            if (event.getEntity() == victim) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, claim);
        try {
            helper.assertFalse(victim.hurt(victim.damageSources().mobAttack(zombie), rawFor(helper, 8F)), "攻击被拦下");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(claim);
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        helper.assertTrue(probe.count == 0, "攻击被拦下不反伤");
        helper.assertTrue(mru(stacks[1]) == FULL, "攻击被拦下不扣电");
        helper.succeed();
    }

    /**
     * 混合端（{@link RayHitGate#setHybridForTest} 模拟）：LivingHurtEvent 里只记待付，整下挡掉时纯 Forge 不发 LivingDamageEvent，等于插件拦下——
     * 不反伤、不扣电；同一个伤害源的 LivingDamageEvent 到了（插件放行）才反伤、才扣电，各一次
     */
    @GameTest(template = EMPTY)
    public static void computerArmorThornsHybridWaitsForDamage(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        ItemStack[] stacks = wear(victim, FULL, FULL, FULL, FULL);
        DamageSource source = victim.damageSources().mobAttack(zombie);
        ThornsProbe probe = thorns(zombie);
        RayHitGate.setHybridForTest(true);
        try {
            victim.hurt(source, rawFor(helper, 8F));
            helper.assertTrue(probe.count == 0 && mru(stacks[1]) == FULL, "插件还没放行：不反伤、不扣电");
            helper.assertTrue(MRUShield.hasPending(victim, source), "应记下待付");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, victim.damageSources().mobAttack(zombie), 0F));
            helper.assertTrue(probe.count == 0, "别的伤害源对象不算这一下");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, source, 0F));
            helper.assertTrue(probe.count == 1 && Math.abs(probe.amount - 4F) < EPS, "放行后反 4 点，实际 " + probe.count + " / " + probe.amount);
            helper.assertTrue(mru(stacks[1]) == FULL - 750, "放行后胸甲扣 750，剩 " + mru(stacks[1]));
            zombie.invulnerableTime = 0;
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, source, 0F));
            helper.assertTrue(probe.count == 1 && mru(stacks[1]) == FULL - 750, "同一下只反一次、只扣一次");
        } finally {
            RayHitGate.setHybridForTest(null);
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        helper.succeed();
    }

    /**
     * 混合端举盾整下挡掉（审查补充，和饰品那几条同一个根因）：混合端在 LivingHurtEvent 之后才算举盾，挡满照样发 {@code LivingDamageEvent(0)}。
     * 按混合端的顺序：hurt（LivingHurtEvent 里记待付）→ {@code ShieldBlockEvent} → {@code LivingDamageEvent(0)}：不扣电、不反伤，记录删掉。
     * 纯 Forge 举盾在 LivingHurtEvent 之前就把伤害扣成 0，盔甲本来就不挡不扣、不反伤，两边一样
     */
    @GameTest(template = EMPTY)
    public static void computerArmorHybridShieldBlockedPaysNothing(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        ItemStack[] stacks = wear(victim, FULL, FULL, FULL, FULL);
        DamageSource source = victim.damageSources().mobAttack(zombie);
        ThornsProbe probe = thorns(zombie);
        RayHitGate.setHybridForTest(true);
        try {
            victim.hurt(source, rawFor(helper, 8F));
            helper.assertTrue(MRUShield.hasPending(victim, source) && !MRUShield.pendingCharges(victim, source).isEmpty(), "测试前提：记下了待付的花费");
            MinecraftForge.EVENT_BUS.post(new ShieldBlockEvent(victim, source, 0F));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, source, 0F));
            helper.assertTrue(probe.count == 0, "被盾牌整下挡掉的一下不反伤，实际反了 " + probe.count + " 次");
            for (ItemStack stack : stacks) {
                helper.assertTrue(mru(stack) == FULL, "被盾牌整下挡掉的一下不扣电，实际剩 " + mru(stack));
            }
            helper.assertFalse(MRUShield.hasPending(victim, source), "这一下的待付记录应删掉");
        } finally {
            RayHitGate.setHybridForTest(null);
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        helper.succeed();
    }

    // ================================================================ 夜视包

    /**
     * 夜视：没戴头盔按不动；戴着按一下给隐藏图标的那份、同一刻再按无效；剩不到 300 刻才续（剩 280 续）；喝的药水不碰；摘头盔只删自己那份；
     * 5 刻后再按就关掉、删掉
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void computerArmorNightVision(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        helper.assertFalse(ComputerArmorEvents.toggleNightVision(player), "没戴头盔按不动");
        ItemStack helmet = withMRU(ArmorItem.Type.HELMET, 0);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        helper.assertTrue(ComputerArmorEvents.toggleNightVision(player), "戴着头盔能切换");
        MobEffectInstance effect = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(ComputerArmorEvents.isOwnNightVision(effect) && effect.getDuration() == ComputerArmorEvents.NIGHT_VISION_TICKS,
                "应给 400 刻、隐藏图标的夜视");
        helper.assertFalse(ComputerArmorEvents.toggleNightVision(player), "同一刻再按无效（限频）");

        player.removeEffect(MobEffects.NIGHT_VISION);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false));
        ComputerArmorEvents.periodic(player);
        helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION).getDuration() == 300, "剩 300 刻（不到 300 才续）不续");
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 280, 0, false, false, false));
        ComputerArmorEvents.periodic(player);
        helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION).getDuration() == ComputerArmorEvents.NIGHT_VISION_TICKS,
                "剩 280 刻（400 起每 20 刻查一次，第一次不到 300）应续到 400");
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0, false, false, false));
        ComputerArmorEvents.periodic(player);
        helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION).getDuration() == ComputerArmorEvents.NIGHT_VISION_TICKS, "剩 200 刻应续到 400");

        player.removeEffect(MobEffects.NIGHT_VISION);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600));
        ComputerArmorEvents.periodic(player);
        MobEffectInstance potion = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(potion.getDuration() == 3600 && potion.showIcon(), "喝的夜视药水不碰（不改成隐藏图标）");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.HEAD, helmet, ItemStack.EMPTY));
        helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION) != null, "摘头盔时药水的夜视不删");

        player.removeEffect(MobEffects.NIGHT_VISION);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        ComputerArmorEvents.periodic(player);
        helper.assertTrue(ComputerArmorEvents.isOwnNightVision(player.getEffect(MobEffects.NIGHT_VISION)), "开关开着、戴回头盔下一次检查给上");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.HEAD, helmet, ItemStack.EMPTY));
        helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION) == null, "摘头盔删掉自己给的那份");

        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        ComputerArmorEvents.periodic(player);
        helper.startSequence()
                .thenExecuteAfter(ComputerArmorEvents.TOGGLE_MIN_INTERVAL + 1, () -> {
                    helper.assertTrue(ComputerArmorEvents.toggleNightVision(player), "过了限频能再按");
                    helper.assertFalse(ComputerArmorEvents.nightVisionOn(player), "这一下应关掉");
                    helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION) == null, "关掉时删掉自己给的那份");
                })
                .thenSucceed();
    }

    /**
     * 夜视只删自己给的：
     * <ol>
     *   <li>从没给过的玩家（没戴头盔；戴着头盔但开关关着）身上命令给的隐藏夜视（300 刻，和自己给的长得一样）每 20 刻检查后还在；</li>
     *   <li>给过一次、摘头盔收回（标记清掉）以后，再有命令给的隐藏夜视也不删；</li>
     *   <li>隐藏图标的无限时长夜视：标记还在时摘头盔也不删；</li>
     *   <li>显示图标的无限时长夜视：开关开着时检查不把它改成隐藏图标、不改时长，摘头盔也不删；</li>
     *   <li>放大等级 1、剩得不多的夜视：开关开着时检查不改图标、不改时长和等级。</li>
     * </ol>
     */
    @GameTest(template = EMPTY)
    public static void computerArmorNightVisionLeavesOthers(GameTestHelper helper) {
        ServerPlayer bare = ProtectionFrameworkTests.realPlayer(helper);
        bare.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false));
        helper.assertTrue(ComputerArmorEvents.isOwnNightVision(bare.getEffect(MobEffects.NIGHT_VISION)), "测试前提：命令给的隐藏夜视和自己给的长得一样");
        ComputerArmorEvents.periodic(bare);
        MobEffectInstance kept = bare.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(kept != null && kept.getDuration() == 300, "没给过、没戴头盔：命令给的隐藏夜视不删");

        ServerPlayer switchedOff = ProtectionFrameworkTests.realPlayer(helper);
        switchedOff.setItemSlot(EquipmentSlot.HEAD, withMRU(ArmorItem.Type.HELMET, 0));
        helper.assertFalse(ComputerArmorEvents.nightVisionOn(switchedOff), "测试前提：开关默认关着");
        switchedOff.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false));
        ComputerArmorEvents.periodic(switchedOff);
        kept = switchedOff.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(kept != null && kept.getDuration() == 300, "戴着头盔、开关关着：命令给的隐藏夜视不删");

        ServerPlayer once = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack onceHelmet = withMRU(ArmorItem.Type.HELMET, 0);
        once.setItemSlot(EquipmentSlot.HEAD, onceHelmet);
        helper.assertTrue(ComputerArmorEvents.toggleNightVision(once) && ComputerArmorEvents.nightVisionGiven(once), "打开后给上、记下标记");
        once.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(once, EquipmentSlot.HEAD, onceHelmet, ItemStack.EMPTY));
        helper.assertTrue(once.getEffect(MobEffects.NIGHT_VISION) == null && !ComputerArmorEvents.nightVisionGiven(once), "摘头盔收回自己那份、清掉标记");
        once.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false));
        ComputerArmorEvents.periodic(once);
        kept = once.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(kept != null && kept.getDuration() == 300, "标记清掉以后，命令给的隐藏夜视不删");

        ServerPlayer hiddenInfinite = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack hiddenHelmet = withMRU(ArmorItem.Type.HELMET, 0);
        hiddenInfinite.setItemSlot(EquipmentSlot.HEAD, hiddenHelmet);
        helper.assertTrue(ComputerArmorEvents.toggleNightVision(hiddenInfinite), "测试前提：打开、记下标记");
        hiddenInfinite.removeEffect(MobEffects.NIGHT_VISION);
        hiddenInfinite.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        helper.assertFalse(ComputerArmorEvents.isOwnNightVision(hiddenInfinite.getEffect(MobEffects.NIGHT_VISION)), "无限时长的不像自己给的");
        hiddenInfinite.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(hiddenInfinite, EquipmentSlot.HEAD, hiddenHelmet, ItemStack.EMPTY));
        kept = hiddenInfinite.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(kept != null && kept.isInfiniteDuration(), "隐藏图标的无限时长夜视：标记在、摘头盔也不删");

        ServerPlayer potion = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack potionHelmet = withMRU(ArmorItem.Type.HELMET, 0);
        potion.setItemSlot(EquipmentSlot.HEAD, potionHelmet);
        helper.assertTrue(ComputerArmorEvents.toggleNightVision(potion), "测试前提：打开");
        potion.removeEffect(MobEffects.NIGHT_VISION);
        potion.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0));
        ComputerArmorEvents.periodic(potion);
        kept = potion.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(kept != null && kept.isInfiniteDuration() && kept.showIcon() && kept.isVisible(),
                "显示图标的无限时长夜视：开关开着也不改成隐藏图标、不改时长");
        potion.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(potion, EquipmentSlot.HEAD, potionHelmet, ItemStack.EMPTY));
        kept = potion.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(kept != null && kept.isInfiniteDuration() && kept.showIcon(), "显示图标的无限时长夜视：摘头盔不删");

        ServerPlayer strong = ProtectionFrameworkTests.realPlayer(helper);
        strong.setItemSlot(EquipmentSlot.HEAD, withMRU(ArmorItem.Type.HELMET, 0));
        helper.assertTrue(ComputerArmorEvents.toggleNightVision(strong), "测试前提：打开");
        strong.removeEffect(MobEffects.NIGHT_VISION);
        strong.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100, 1));
        ComputerArmorEvents.periodic(strong);
        kept = strong.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(kept != null && kept.getAmplifier() == 1 && kept.getDuration() == 100 && kept.showIcon(),
                "放大等级 1、剩 100 刻的夜视：不改图标、不改时长和等级");
        helper.succeed();
    }

    // ================================================================ 冲刺包

    /** 冲刺：不是全套、旁观、骑乘时不能冲；全套冲一下速度 = 视线 × 3、标记同步；冷却内再按无效，过了冷却又能冲 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void computerArmorDash(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        helper.assertFalse(ComputerArmorEvents.tryDash(player), "没穿全套不能冲");
        wear(player, 0, 0, 0, 0);
        player.setXRot(-30F);
        player.setYRot(45F);
        Vec3 look = player.getLookAngle();
        player.hurtMarked = false;
        List<S2CGearFxPacket> sent = new ArrayList<>();
        S2CGearFxPacket.setTestSink(sent::add);
        try {
            helper.assertTrue(ComputerArmorEvents.tryDash(player), "全套能冲");
            helper.assertTrue(player.getDeltaMovement().distanceTo(look.scale(3D)) < 1e-6 && player.hurtMarked,
                    "速度应设成视线 × 3 并标记同步，实际 " + player.getDeltaMovement());
            helper.assertFalse(ComputerArmorEvents.tryDash(player), "冷却内再按无效");
        } finally {
            S2CGearFxPacket.setTestSink(null);
        }
        long dashes = sent.stream().filter(p -> p.kind() == GearFx.JET_DASH && p.entity() == player.getId()).count();
        helper.assertTrue(dashes == 1 && sent.stream().anyMatch(p -> p.kind() == GearFx.JET_DASH && Math.abs(p.param() - 3F) < 1e-4),
                "冲出去发一个喷射事件（param = 速度 3），冷却内按的不发，实际 " + dashes);

        ServerPlayer spectator = ProtectionFrameworkTests.realPlayer(helper);
        wear(spectator, 0, 0, 0, 0);
        spectator.setGameMode(GameType.SPECTATOR);
        helper.assertFalse(ComputerArmorEvents.tryDash(spectator), "旁观者不能冲");
        spectator.setGameMode(GameType.SURVIVAL);

        ServerPlayer rider = ProtectionFrameworkTests.realPlayer(helper);
        wear(rider, 0, 0, 0, 0);
        Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(3, 1, 3));
        helper.assertTrue(rider.startRiding(boat, true) && rider.isPassenger(), "测试前提：坐上船");
        helper.assertFalse(ComputerArmorEvents.tryDash(rider), "骑乘时不能冲");
        rider.stopRiding();
        boat.discard();

        int cooldown = ECComputer.Config.values().dashCooldown();
        helper.startSequence()
                .thenExecuteAfter(cooldown + 1, () -> helper.assertTrue(ComputerArmorEvents.tryDash(player), "过了冷却又能冲"))
                .thenSucceed();
    }

    /** 配置要每次花 1 000 MRU：身上没有储存物品、胸甲没电时不冲；胸甲有电时从胸甲扣；带着储存物品时先扣储存物品 */
    @GameTest(template = EMPTY)
    public static void computerArmorDashCost(GameTestHelper helper) {
        runWith(ECComputer.Config.DEFAULTS.toBuilder().dash(3D, 20, 1000).build(), () -> {
            ServerPlayer broke = ProtectionFrameworkTests.realPlayer(helper);
            wear(broke, 0, 0, 0, 0);
            helper.assertFalse(ComputerArmorEvents.tryDash(broke), "扣不起不冲");
            helper.assertTrue(broke.getDeltaMovement().lengthSqr() == 0D, "没冲出去");

            ServerPlayer charged = ProtectionFrameworkTests.realPlayer(helper);
            ItemStack[] stacks = wear(charged, 0, 5000, 0, 0);
            helper.assertTrue(ComputerArmorEvents.tryDash(charged) && mru(stacks[1]) == 4000, "从胸甲扣 1000，剩 " + mru(stacks[1]));

            ServerPlayer withStorage = ProtectionFrameworkTests.realPlayer(helper);
            ItemStack[] own = wear(withStorage, 0, 5000, 0, 0);
            ItemStack storage = MRUStaffItem.full(ECItems.RED_SOUL_MATTER.get());
            withStorage.getInventory().setItem(9, storage);
            helper.assertTrue(ComputerArmorEvents.tryDash(withStorage) && mru(own[1]) == 5000 && mru(storage) == 120000 - 1000,
                    "带着储存物品先扣它");
        });
        helper.succeed();
    }
}
