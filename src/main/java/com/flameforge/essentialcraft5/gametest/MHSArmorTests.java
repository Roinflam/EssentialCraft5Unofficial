package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.entity.ArmorDestroyerEntity;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.MHSArmorEvents;
import com.flameforge.essentialcraft5.item.MHSArmorItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.VoidArmorItem;
import com.flameforge.essentialcraft5.item.armor.ArmorCharge;
import com.flameforge.essentialcraft5.item.armor.ArmorTakeoffClamp;
import com.flameforge.essentialcraft5.item.armor.ConfiguredArmorStats;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECCreative;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 魔力防护第十三批 13b 的 GameTest（batch13.md §4.3、§4.4，§12.2 13b 行）：属性按配置、自带 MRU 随阶、矩阵缓冲的份额与扣费、先挡后过护甲、
 * 2 阶挡带攻击者的 MRU 放电、余流（全套、冷却、对玩家缩放、配置填 0、混合端放行后才给）、NBT 变了不算脱下、升阶配方、提示与默认配置；
 * 脱下夹生命（同种低阶换件、风之 / 审判庭 / 宅男套装、真 tick 的换装）、护甲破坏者按电量扣电或扣耐久、容量配成空列表、修理时阶留不留。
 * <p>
 * 测试用的模拟玩家不 tick，原版换装时加修饰符那一步用 {@link #applyModifiers} 手动做；伤害经 {@code Player.hurt} 走真的事件链。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MHSArmorTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 1.0E-3F;
    private static final ArmorItem.Type[] TYPES = ArmorItem.Type.values();

    private MHSArmorTests() {
    }

    // ================================================================ 工具

    private static MHSArmorItem item(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> ECGear.MHS_HELMET.get();
            case CHESTPLATE -> ECGear.MHS_CHESTPLATE.get();
            case LEGGINGS -> ECGear.MHS_LEGGINGS.get();
            case BOOTS -> ECGear.MHS_BOOTS.get();
        };
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
        if (item == null) {
            throw new IllegalStateException("没有物品 " + path);
        }
        return item;
    }

    private static Player mock(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 一件 stage 阶、存 mru 的（存不下的按容量截） */
    private static ItemStack piece(ArmorItem.Type type, int stage, int mru) {
        ItemStack stack = new ItemStack(item(type));
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    /** 穿四件 stage 阶、各存满（按这一阶的容量）；返回四件 */
    private static ItemStack[] wear(Player player, int stage) {
        ItemStack[] stacks = new ItemStack[4];
        for (ArmorItem.Type type : TYPES) {
            stacks[type.ordinal()] = piece(type, stage, Integer.MAX_VALUE);
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

    private static int max(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandlerItem::getMaxMRU).orElse(-1);
    }

    private static double sum(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        return stack.getAttributeModifiers(slot).get(attribute).stream().mapToDouble(AttributeModifier::getAmount).sum();
    }

    /** Player.hurt 按难度缩放怪物打来的伤害（简单 min(x/2+1, x)、困难 ×1.5）：要让事件里看到 wanted，传进去的数 */
    private static float rawFor(GameTestHelper helper, float wanted) {
        Difficulty difficulty = helper.getLevel().getDifficulty();
        helper.assertTrue(difficulty != Difficulty.PEACEFUL, "测试前提：不是和平难度（和平时怪物打玩家是 0）");
        return switch (difficulty) {
            case EASY -> wanted <= 2F ? wanted : (wanted - 1F) * 2F;
            case HARD -> wanted * 2F / 3F;
            default -> wanted;
        };
    }

    private static DamageSource source(GameTestHelper helper, ResourceKey<DamageType> type, Entity attacker) {
        return new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
    }

    private static Zombie zombie(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
    }

    /** 回满血再打（模拟玩家没有护甲修饰符时几下就会打死，死了以后 hurt 直接返回） */
    private static void hit(Player victim, DamageSource source, float amount) {
        victim.setHealth(victim.getMaxHealth());
        victim.invulnerableTime = 0;
        victim.hurt(source, amount);
    }

    /** 模拟混合端插件拦下这名玩家挨的打：护盾（LOW）算完以后在 LivingHurtEvent 最后取消，LivingDamageEvent 不会来 */
    private static final class PluginGate implements Consumer<LivingHurtEvent> {
        private final Player target;

        PluginGate(Player target) {
            this.target = target;
        }

        @Override
        public void accept(LivingHurtEvent event) {
            if (event.getEntity() == target) {
                event.setCanceled(true);
            }
        }
    }

    private static void hitBlockedByPlugin(Player victim, DamageSource source, float amount) {
        PluginGate gate = new PluginGate(victim);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, gate);
        try {
            hit(victim, source, amount);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(gate);
        }
    }

    /** 记某个实体最后一次 LivingDamageEvent 的数（护盾和原版护甲都算完以后） */
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

    /** 打一下并返回 LivingDamageEvent 看到的数（没发就是 -1） */
    private static float damageSeen(Player victim, DamageSource source, float amount) {
        DamageCounter counter = new DamageCounter(victim);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDamageEvent.class, counter);
        try {
            hit(victim, source, amount);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        return counter.last;
    }

    private static void runConfig(ECGear.MHSConfig.Values values, Runnable body) {
        ECGear.MHSConfig.setTestOverrides(values);
        try {
            body.run();
        } finally {
            ECGear.MHSConfig.setTestOverrides(null);
        }
    }

    private static boolean hasKey(List<Component> lines, String key) {
        return argsOf(lines, key) != null;
    }

    private static Object[] argsOf(List<Component> lines, String key) {
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents translatable && key.equals(translatable.getKey())) {
                return translatable.getArgs();
            }
        }
        return null;
    }

    private static int haste(Player player) {
        MobEffectInstance effect = player.getEffect(MobEffects.DIG_SPEED);
        return effect == null ? 0 : effect.getDuration();
    }

    // ================================================================ 属性、容量、抗性

    /**
     * 属性按配置：护甲 3 / 8 / 6 / 3、每件韧性 2、+2 生命，护甲 / 韧性用原版同部位的 UUID（老存档登录时替换掉材料给的那条），生命 UUID 和虚空、宅男不重；
     * 配置改成 0 时修饰符照样在、数额 0（热改后脱装不留残余）；全套穿上最大生命 28、护甲 20、韧性 8
     */
    @GameTest(template = EMPTY)
    public static void mhsAttributesFromConfig(GameTestHelper helper) {
        double[] armor = {3D, 8D, 6D, 3D};
        for (ArmorItem.Type type : TYPES) {
            ItemStack stack = new ItemStack(item(type));
            EquipmentSlot slot = type.getSlot();
            helper.assertTrue(sum(stack, slot, Attributes.ARMOR) == armor[type.ordinal()] && sum(stack, slot, Attributes.ARMOR_TOUGHNESS) == 2D
                            && sum(stack, slot, Attributes.MAX_HEALTH) == 2D && sum(stack, slot, Attributes.KNOCKBACK_RESISTANCE) == 0D,
                    type + " 的属性应是护甲 " + armor[type.ordinal()] + "、韧性 2、生命 2");
            ItemStack diamond = new ItemStack(switch (type) {
                case HELMET -> Items.DIAMOND_HELMET;
                case CHESTPLATE -> Items.DIAMOND_CHESTPLATE;
                case LEGGINGS -> Items.DIAMOND_LEGGINGS;
                case BOOTS -> Items.DIAMOND_BOOTS;
            });
            AttributeModifier ours = stack.getAttributeModifiers(slot).get(Attributes.ARMOR).iterator().next();
            AttributeModifier vanilla = diamond.getAttributeModifiers(slot).get(Attributes.ARMOR).iterator().next();
            helper.assertTrue(ours.getId().equals(vanilla.getId()), type + " 护甲修饰符应用原版同部位的 UUID");
            helper.assertTrue(stack.getAttributeModifiers(slot).get(Attributes.MAX_HEALTH).iterator().next().getId().equals(MHSArmorItem.healthModifierId(type))
                    && !MHSArmorItem.healthModifierId(type).equals(VoidArmorItem.healthModifierId(type))
                    && !MHSArmorItem.healthModifierId(type).equals(ComputerArmorItem.healthModifierId(type))
                    && MHSArmorItem.healthModifierId(type).equals(ConfiguredArmorStats.healthModifierId(type, MHSArmorItem.STATS_KEY)),
                    type + " 生命修饰符 UUID 按套装名固定、和别的套装不重");
        }
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(player, 0);
        applyModifiers(player, stacks);
        helper.assertTrue(player.getMaxHealth() == 28F && player.getArmorValue() == 20
                && player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 8D, "全套：生命 28、护甲 20、韧性 8，实际 "
                + player.getMaxHealth() + " / " + player.getArmorValue() + " / " + player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));

        ItemStack chest = new ItemStack(item(ArmorItem.Type.CHESTPLATE));
        runConfig(ECGear.MHSConfig.DEFAULTS.toBuilder().stats(1D, 9D, 4D, 1D, 0D, 0D, 0D).build(), () -> {
            helper.assertTrue(sum(chest, EquipmentSlot.CHEST, Attributes.ARMOR) == 9D, "配置改了护甲跟着变");
            helper.assertTrue(chest.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR_TOUGHNESS).size() == 1
                            && chest.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.MAX_HEALTH).size() == 1
                            && sum(chest, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS) == 0D && sum(chest, EquipmentSlot.CHEST, Attributes.MAX_HEALTH) == 0D,
                    "韧性、生命配成 0 时修饰符照样在、数额 0");
        });
        helper.assertTrue(sum(chest, EquipmentSlot.CHEST, Attributes.ARMOR) == 8D, "覆盖值去掉后回到默认");
        helper.succeed();
    }

    /**
     * 自带 MRU：老存档 / 新做的一件 0 MRU、容量 2 000、不是储存物品；容量按物品上现在的阶现算（同一个物品对象改阶立刻变）；
     * 升阶出来的那件电留着、容量 8 000；创造栏满的一份是 2 000；配置 max_mru 填 0 时存不进电、护盾不挡；列表比阶短时按最后一项
     */
    @GameTest(template = EMPTY)
    public static void mhsCapacityFollowsStage(GameTestHelper helper) {
        ItemStack fresh = new ItemStack(item(ArmorItem.Type.HELMET));
        IMRUHandlerItem handler = fresh.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        helper.assertTrue(handler != null && handler.getMRU() == 0 && handler.getMaxMRU() == 2000 && !handler.isStorage(),
                "新的一件 0 / 2000 MRU、不是储存物品");
        handler.setMRU(5000);
        helper.assertTrue(handler.getMRU() == 2000, "存不下的按容量截，实际 " + handler.getMRU());
        helper.assertTrue(handler.addMRU(500, true) == 500 && handler.getMRU() == 2000, "满了以后再加全退回来");
        Attunement.set(fresh, 1, 0);
        helper.assertTrue(handler.getMaxMRU() == 4000 && handler.addMRU(3000, true) == 1000 && handler.getMRU() == 4000, "改成 1 阶，容量立刻 4 000");
        Attunement.set(fresh, 1, Attunement.need(ECAttunement.MHS_ARMOR, 1));
        ItemStack ascended = Attunement.ascended(fresh);
        helper.assertTrue(Attunement.stage(ascended) == 2 && max(ascended) == 8000 && mru(ascended) == 4000, "升到 2 阶：容量 8 000、电留着，实际 "
                + mru(ascended) + " / " + max(ascended));
        helper.assertTrue(handler.extractMRU(1500, true) == 1500 && handler.getMRU() == 2500, "取电照常");

        ItemStack full = MRUStaffItem.full(item(ArmorItem.Type.CHESTPLATE));
        helper.assertTrue(mru(full) == 2000 && ((MHSArmorItem) full.getItem()).creativeVariants().size() == 2, "创造栏空、满两份，满的是 2 000");

        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(player, 0);
        Zombie zombie = zombie(helper);
        runConfig(ECGear.MHSConfig.DEFAULTS.toBuilder().maxMru(List.of(0)).build(), () -> {
            helper.assertTrue(max(stacks[1]) == 0 && mru(stacks[1]) == 0, "max_mru 填 0：容量 0、读出来 0");
            float seen = damageSeen(player, player.damageSources().mobAttack(zombie), rawFor(helper, 20F));
            helper.assertTrue(Math.abs(seen - 20F) < EPS, "没电的四件不挡，实际 " + seen);
        });
        runConfig(ECGear.MHSConfig.DEFAULTS.toBuilder().maxMru(List.of(500)).build(), () ->
                helper.assertTrue(max(ascended) == 500 && ECGear.MHSConfig.maxMru(5) == 500, "列表只有一项时各阶都按它"));
        helper.assertTrue(mru(stacks[1]) == 2000, "覆盖值去掉后存量回来（NBT 里没被截）");
        helper.succeed();
    }

    /** MRU 腐化抗性 0 / 1 阶每件 0.25、2 阶 0.5（全套 2 阶易伤 0.5）；腐化辐射、海纳尔辐射不随阶变 */
    @GameTest(template = EMPTY)
    public static void mhsResistanceByStage(GameTestHelper helper) {
        for (int stage = 0; stage <= 2; stage++) {
            ItemStack stack = piece(ArmorItem.Type.LEGGINGS, stage, 0);
            float want = stage >= MHSArmorItem.DISCHARGE_STAGE ? 0.5F : 0.25F;
            helper.assertTrue(MRUResistance.resistance(stack, MRUResistance.Type.MRU_CORRUPTION) == want
                            && MRUResistance.resistance(stack, MRUResistance.Type.CORRUPTION_RADIATION) == 0.125F
                            && MRUResistance.resistance(stack, MRUResistance.Type.HOANNA_RADIATION) == 0.10F,
                    stage + " 阶抗性不对");
        }
        Player player = mock(helper, 1.5D, 1.5D);
        wear(player, 2);
        helper.assertTrue(Math.abs(MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION) - 0.5F) < EPS, "全套 2 阶 MRU 腐化易伤 0.5");
        wear(player, 0);
        helper.assertTrue(Math.abs(MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION) - 0.75F) < EPS, "全套 0 阶照旧 0.75");
        helper.succeed();
    }

    // ================================================================ 矩阵缓冲

    /**
     * 0 阶全套有电：怪物近战 20 点挡 25%（剩 15），头 / 胸 / 腿 / 靴扣 100 / 200 / 100 / 100；火（岩浆）照挡；
     * 怪物放的魔法不挡、不扣；没电的不挡；只有胸甲有电时只挡胸甲那 2 份
     */
    @GameTest(template = EMPTY)
    public static void mhsShieldStageZero(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(player, 0);
        float seen = damageSeen(player, player.damageSources().mobAttack(zombie), rawFor(helper, 20F));
        helper.assertTrue(Math.abs(seen - 15F) < EPS, "挡 5/20，应剩 15，实际 " + seen);
        int[] cost = {100, 200, 100, 100};
        for (ArmorItem.Type type : TYPES) {
            helper.assertTrue(mru(stacks[type.ordinal()]) == 2000 - cost[type.ordinal()], type + " 应扣 " + cost[type.ordinal()] + "，实际 "
                    + (2000 - mru(stacks[type.ordinal()])));
        }
        float lava = damageSeen(player, player.damageSources().lava(), 10F);
        helper.assertTrue(Math.abs(lava - 7.5F) < EPS, "岩浆（元素）也挡 25%，实际 " + lava);
        int chestBefore = mru(stacks[1]);
        float spell = damageSeen(player, source(helper, DamageTypes.INDIRECT_MAGIC, zombie), rawFor(helper, 8F));
        helper.assertTrue(Math.abs(spell - 8F) < EPS && mru(stacks[1]) == chestBefore, "魔法不挡、不扣，实际 " + spell);
        IMRUShieldArmor.Coverage melee = item(ArmorItem.Type.CHESTPLATE).coverage(stacks[1], player.damageSources().mobAttack(zombie));
        helper.assertTrue(melee != null && melee.share() == 2D && melee.priceMultiplier() == 1D && !melee.extended(), "胸甲 0 阶：2 份、原价，实际 " + melee);

        Player bare = mock(helper, 3.5D, 1.5D);
        for (ArmorItem.Type type : TYPES) {
            bare.setItemSlot(type.getSlot(), piece(type, 0, type == ArmorItem.Type.CHESTPLATE ? 2000 : 0));
        }
        float partial = damageSeen(bare, bare.damageSources().mobAttack(zombie), rawFor(helper, 20F));
        helper.assertTrue(Math.abs(partial - 18F) < EPS, "只有胸甲有电：挡 2/20，应剩 18，实际 " + partial);
        for (ArmorItem.Type type : TYPES) {
            bare.setItemSlot(type.getSlot(), piece(type, 0, 0));
        }
        float none = damageSeen(bare, bare.damageSources().mobAttack(zombie), rawFor(helper, 20F));
        helper.assertTrue(Math.abs(none - 20F) < EPS, "都没电不挡，实际 " + none);
        helper.succeed();
    }

    /**
     * 倍率顺序：护盾在 LivingHurtEvent LOW 先拿走份额，剩下的才过原版护甲和韧性（§4.1 表：带电 D = 20 实伤 0 阶 5.2、2 阶 3.4）。
     * 0 阶全套剩 15 → 护甲 20 / 韧性 8 后 5.25；2 阶全套剩 11 → 护甲 28 / 韧性 16（基础属性随阶涨）后护甲顶到减 80%，2.2，四件共扣 900 × 0.9 = 810（1 阶起单价 −10%，gear_growth.mhs_armor.shield_discount）
     */
    @GameTest(template = EMPTY)
    public static void mhsShieldBeforeArmor(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        float[] want = {5.25F, 2.2F};
        int[] stages = {0, 2};
        for (int i = 0; i < 2; i++) {
            Player player = mock(helper, 1.5D + 2D * i, 1.5D);
            ItemStack[] stacks = wear(player, stages[i]);
            applyModifiers(player, stacks);
            player.setHealth(player.getMaxHealth());
            float before = player.getHealth();
            int[] mruBefore = new int[4];
            for (int k = 0; k < 4; k++) {
                mruBefore[k] = mru(stacks[k]);
            }
            hit(player, player.damageSources().mobAttack(zombie), rawFor(helper, 20F));
            float lost = before - player.getHealth();
            helper.assertTrue(Math.abs(lost - want[i]) < 0.01F, stages[i] + " 阶：实伤应是 " + want[i] + "，实际 " + lost);
            if (stages[i] == 2) {
                int spent = 0;
                for (int k = 0; k < 4; k++) {
                    spent += mruBefore[k] - mru(stacks[k]);
                }
                helper.assertTrue(spent == 810, "2 阶 20 点一下共扣 900 × 0.9 = 810，实际 " + spent);
            }
        }
        helper.succeed();
    }

    /**
     * 2 阶挡带攻击者的 MRU 放电（新覆盖面）：1 阶不挡；2 阶挡 45%、单价 ×0.9（阶的护盾折扣，四件 180 / 360 / 180 / 90）；没有攻击者的放电（聚集体、特斯拉线圈）、
     * 玩家打来的放电不挡；身上带无尽 MRU 晶体时近战由晶体付，放电从盔甲自己扣
     */
    @GameTest(template = EMPTY)
    public static void mhsDischargeFromStageTwo(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player first = mock(helper, 1.5D, 1.5D);
        ItemStack[] one = wear(first, 1);
        float seenOne = damageSeen(first, source(helper, ECDamageTypes.MRU_DISCHARGE, zombie), 20F);
        helper.assertTrue(Math.abs(seenOne - 20F) < EPS && mru(one[1]) == 4000, "1 阶不挡放电，实际 " + seenOne);

        Player second = mock(helper, 3.5D, 1.5D);
        ItemStack[] two = wear(second, 2);
        IMRUShieldArmor.Coverage coverage = item(ArmorItem.Type.CHESTPLATE).coverage(two[1], source(helper, ECDamageTypes.MRU_DISCHARGE, zombie));
        helper.assertTrue(coverage != null && coverage.share() == 4D && Math.abs(coverage.priceMultiplier() - 0.9D) < EPS && coverage.extended(),
                "2 阶胸甲挡放电：4 份、单价 ×0.9、新覆盖面，实际 " + coverage);
        float seenTwo = damageSeen(second, source(helper, ECDamageTypes.MRU_DISCHARGE, zombie), 20F);
        helper.assertTrue(Math.abs(seenTwo - 11F) < EPS, "2 阶挡 45%，应剩 11，实际 " + seenTwo);
        int[] cost = {180, 360, 180, 90};
        for (ArmorItem.Type type : TYPES) {
            helper.assertTrue(mru(two[type.ordinal()]) == 8000 - cost[type.ordinal()], type + " 放电应扣 " + cost[type.ordinal()] + "，实际 "
                    + (8000 - mru(two[type.ordinal()])));
        }
        float lone = damageSeen(second, ECDamageTypes.mruDischarge(helper.getLevel()), 4F);
        helper.assertTrue(Math.abs(lone - 4F) < EPS, "没有攻击者的放电不挡，实际 " + lone);
        Player other = mock(helper, 5.5D, 1.5D);
        float pvp = damageSeen(second, source(helper, ECDamageTypes.MRU_DISCHARGE, other), 4F);
        helper.assertTrue(Math.abs(pvp - 4F) < EPS && MRUShield.coverageFor(second, two[1], source(helper, ECDamageTypes.MRU_DISCHARGE, other)) == null,
                "玩家打来的放电不挡，实际 " + pvp);

        Player rich = mock(helper, 1.5D, 3.5D);
        rich.getInventory().setItem(9, new ItemStack(ECCreative.INFINITE_MRU_CRYSTAL.get()));
        ItemStack[] crystal = wear(rich, 2);
        damageSeen(rich, rich.damageSources().mobAttack(zombie), rawFor(helper, 20F));
        helper.assertTrue(mru(crystal[1]) == 8000, "近战由无尽晶体付，盔甲不动");
        damageSeen(rich, source(helper, ECDamageTypes.MRU_DISCHARGE, zombie), 20F);
        helper.assertTrue(mru(crystal[1]) == 8000 - 360, "放电从胸甲自己扣 360，实际 " + (8000 - mru(crystal[1])));
        runConfig(ECGear.MHSConfig.DEFAULTS.toBuilder().dischargePriceMultiplier(2.5D).build(), () -> {
            IMRUShieldArmor.Coverage scaled = item(ArmorItem.Type.CHESTPLATE).coverage(two[1], source(helper, ECDamageTypes.MRU_DISCHARGE, zombie));
            helper.assertTrue(scaled != null && Math.abs(scaled.priceMultiplier() - 2.25D) < EPS, "放电单价倍数读配置（2.5 × 阶折扣 0.9），实际 " + scaled);
        });
        helper.succeed();
    }

    // ================================================================ 余流

    /**
     * 全套 1 阶：护盾挡下一下给 40 刻急迫 I；200 刻内再挨打不再给；冷却走完再给。全套 0 阶、混穿（胸甲换成虚空）不给；护盾没挡（没电）不给
     */
    @GameTest(template = EMPTY)
    public static void mhsAfterflowFullSetStageOne(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player player = mock(helper, 1.5D, 1.5D);
        try {
            wear(player, 1);
            hit(player, player.damageSources().mobAttack(zombie), rawFor(helper, 6F));
            MobEffectInstance effect = player.getEffect(MobEffects.DIG_SPEED);
            helper.assertTrue(effect != null && effect.getDuration() == 40 && effect.getAmplifier() == MHSArmorEvents.AFTERFLOW_AMPLIFIER,
                    "挡下一下给 40 刻急迫 I，实际 " + effect);
            player.removeEffect(MobEffects.DIG_SPEED);
            hit(player, player.damageSources().mobAttack(zombie), rawFor(helper, 6F));
            helper.assertTrue(haste(player) == 0, "冷却里不再给");
            MHSArmorEvents.setAfterflowForTest(player, 200);
            hit(player, player.damageSources().mobAttack(zombie), rawFor(helper, 6F));
            helper.assertTrue(haste(player) == 40, "冷却走完再给，实际 " + haste(player));
        } finally {
            MHSArmorEvents.setAfterflowForTest(player, null);
        }

        Player stageZero = mock(helper, 3.5D, 1.5D);
        wear(stageZero, 0);
        hit(stageZero, stageZero.damageSources().mobAttack(zombie), rawFor(helper, 6F));
        helper.assertTrue(haste(stageZero) == 0, "全套 0 阶没有余流");

        Player mixed = mock(helper, 5.5D, 1.5D);
        wear(mixed, 1);
        ItemStack voidChest = new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get());
        voidChest.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(handler.getMaxMRU()));
        mixed.setItemSlot(EquipmentSlot.CHEST, voidChest);
        hit(mixed, mixed.damageSources().mobAttack(zombie), rawFor(helper, 6F));
        helper.assertTrue(haste(mixed) == 0, "混穿不给");

        Player empty = mock(helper, 1.5D, 3.5D);
        for (ArmorItem.Type type : TYPES) {
            empty.setItemSlot(type.getSlot(), piece(type, 1, 0));
        }
        hit(empty, empty.damageSources().mobAttack(zombie), rawFor(helper, 6F));
        helper.assertTrue(haste(empty) == 0, "没电护盾没挡，不给");
        helper.succeed();
    }

    /**
     * 对玩家缩放：别的玩家打来的一下时长 × pvp_effect_scale（默认 0.5 → 20 刻）；倍数填 0 时不给、也不记冷却（接下来怪物那一下照给 40 刻）；
     * afterflow_ticks 填 0 时不给
     */
    @GameTest(template = EMPTY)
    public static void mhsAfterflowPvpAndZero(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player victim = mock(helper, 1.5D, 1.5D);
        Player other = mock(helper, 3.5D, 1.5D);
        try {
            wear(victim, 1);
            hit(victim, victim.damageSources().playerAttack(other), 6F);
            helper.assertTrue(haste(victim) == 20, "玩家打来的：40 × 0.5 = 20 刻，实际 " + haste(victim));

            victim.removeEffect(MobEffects.DIG_SPEED);
            MHSArmorEvents.setAfterflowForTest(victim, null);
            AttunementConfig.setTestPvpEffectScale(0D);
            try {
                hit(victim, victim.damageSources().playerAttack(other), 6F);
                helper.assertTrue(haste(victim) == 0, "对玩家倍数 0：不给");
            } finally {
                AttunementConfig.clearTestOverrides();
            }
            hit(victim, victim.damageSources().mobAttack(zombie), rawFor(helper, 6F));
            helper.assertTrue(haste(victim) == 40, "倍数 0 那下没记冷却，怪物这一下照给 40 刻，实际 " + haste(victim));

            victim.removeEffect(MobEffects.DIG_SPEED);
            MHSArmorEvents.setAfterflowForTest(victim, null);
            runConfig(ECGear.MHSConfig.DEFAULTS.toBuilder().afterflow(0, 200).build(), () -> {
                hit(victim, victim.damageSources().mobAttack(zombie), rawFor(helper, 6F));
                helper.assertTrue(haste(victim) == 0, "afterflow_ticks 填 0：不给");
            });
            hit(victim, victim.damageSources().mobAttack(zombie), rawFor(helper, 6F));
            helper.assertTrue(haste(victim) == 40, "填 0 那下也没记冷却");
        } finally {
            MHSArmorEvents.setAfterflowForTest(victim, null);
        }
        helper.succeed();
    }

    /**
     * 混合端先算后扣：LivingHurtEvent 里只算、不扣电、不给余流；LivingDamageEvent 到了（插件放行）才扣电、给余流；插件拦下的什么都不动。
     * 没被拦的那一下在同一次 hurt 里就走到 LivingDamageEvent，当场扣、当场给
     */
    @GameTest(template = EMPTY)
    public static void mhsAfterflowHybridWaitsForDamage(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        Player victim = mock(helper, 1.5D, 1.5D);
        Player blocked = mock(helper, 3.5D, 1.5D);
        Player passed = mock(helper, 5.5D, 1.5D);
        ItemStack[] stacks = wear(victim, 1);
        ItemStack[] kept = wear(blocked, 1);
        ItemStack[] paid = wear(passed, 1);
        RayHitGate.setHybridForTest(true);
        try {
            // 插件还没表态：先在 LivingHurtEvent 最后拦住，再手动发 LivingDamageEvent 当作放行
            DamageSource melee = victim.damageSources().mobAttack(zombie);
            hitBlockedByPlugin(victim, melee, rawFor(helper, 20F));
            List<MRUShield.Charge> charges = MRUShield.pendingCharges(victim, melee);
            helper.assertTrue(charges.size() == 4 && mru(stacks[1]) == 4000 && haste(victim) == 0, "放行前：记了四笔、没扣、没给余流，实际 " + charges);
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, melee, 0F));
            // 1 阶单价 −10%：胸甲 300 → 270
            helper.assertTrue(mru(stacks[1]) == 4000 - 270 && haste(victim) == 40, "放行后胸甲扣 270、给余流，实际 "
                    + (4000 - mru(stacks[1])) + " / " + haste(victim));
            helper.assertTrue(MRUShield.pendingCharges(victim, melee).isEmpty(), "放行后记录清掉");

            // 插件拦下：LivingDamageEvent 不来，不扣电、不给余流（记录等下一次记账时作废）
            DamageSource stopped = blocked.damageSources().mobAttack(zombie);
            hitBlockedByPlugin(blocked, stopped, rawFor(helper, 20F));
            helper.assertTrue(MRUShield.hasPending(blocked, stopped) && mru(kept[1]) == 4000 && haste(blocked) == 0, "拦下时不扣电、不给余流");

            // 没被拦：同一次 hurt 里就扣了、给了
            DamageSource through = passed.damageSources().mobAttack(zombie);
            hit(passed, through, rawFor(helper, 20F));
            helper.assertTrue(mru(paid[1]) == 4000 - 270 && haste(passed) == 40 && !MRUShield.hasPending(passed, through), "放行的那一下当场扣 270、给余流");
        } finally {
            RayHitGate.setHybridForTest(null);
            MHSArmorEvents.setAfterflowForTest(victim, null);
            MHSArmorEvents.setAfterflowForTest(passed, null);
        }
        helper.succeed();
    }

    // ================================================================ 换装

    /** 同一件只是 NBT 变了（扣电、写同调点）不算脱下，生命不动；真的脱下后下一刻夹回上限 */
    @GameTest(template = EMPTY)
    public static void mhsNbtChangeIsNotTakeoff(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        ItemStack[] stacks = wear(player, 0);
        applyModifiers(player, stacks);
        player.setHealth(28F);
        helper.assertTrue(player.getHealth() == 28F, "测试前提：全套生命 28");

        ItemStack changed = stacks[1].copy();
        changed.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(100));
        Attunement.set(changed, 0, 50);
        MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.CHEST, stacks[1], changed));
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
        helper.assertTrue(player.getHealth() == 28F, "NBT 变了不算脱下，生命还是 28，实际 " + player.getHealth());

        for (ArmorItem.Type type : TYPES) {
            MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, type.getSlot(), stacks[type.ordinal()], ItemStack.EMPTY));
            player.getAttributes().removeAttributeModifiers(stacks[type.ordinal()].getAttributeModifiers(type.getSlot()));
            player.setItemSlot(type.getSlot(), ItemStack.EMPTY);
        }
        helper.assertTrue(player.getMaxHealth() == 20F && player.getHealth() == 28F, "原版去掉修饰符时生命不跟着夹（测试前提）");
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
        helper.assertTrue(player.getHealth() == 20F, "脱下后下一刻夹回 20，实际 " + player.getHealth());
        helper.succeed();
    }

    // ================================================================ 升阶配方

    /** 攒满 from 阶、阶戳够的一件 */
    private static ItemStack ready(ArmorItem.Type type, int from) {
        ItemStack stack = new ItemStack(item(type));
        Attunement.set(stack, from, Attunement.need(ECAttunement.MHS_ARMOR, from));
        Attunement.stampRank(stack, AttunementConfig.requiredRank(from + 1));
        return stack;
    }

    /**
     * 两条升阶配方（§4.5）：0 → 1 淡青宝石、魔力板 ×2、水晶粉，1 万；1 → 2 元素核心、米斯莱板 ×2、上古晶核（13c 起换掉兜底的 MRU 共鸣水晶），2 万；
     * 四件通用；升出来的那件保留附魔和电、容量跟着阶；阶不对、点数没满的不收
     */
    @GameTest(template = EMPTY)
    public static void mhsAscensionRecipes(GameTestHelper helper) {
        Object[][] table = {
                {"mhs_1", 0, 10_000, new String[]{"pale_gem", "magic_plate", "crystal_dust"}},
                {"mhs_2", 1, 20_000, new String[]{"elemental_core", "mithriline_plate", "ancient_core"}},
        };
        for (Object[] row : table) {
            ResourceLocation id = EssentialCraft5.id("attunement/" + row[0]);
            Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
            helper.assertTrue(recipe instanceof AttunementAscensionRecipe, id + " 应是升阶配方，实际 " + recipe);
            AttunementAscensionRecipe ascension = (AttunementAscensionRecipe) recipe;
            int from = (int) row[1];
            helper.assertTrue(ascension.family().equals(EssentialCraft5.id("mhs_armor")) && ascension.fromStage() == from && ascension.parts().isEmpty()
                            && ascension.getMRU() == (int) row[2] && ascension.promoteTo().isEmpty() && ascension.countAt(2) == 2 && ascension.countAt(1) == 1,
                    id + " 的家族 / 起始阶 / 部位 / MRU / 数量不对");
            String[] corners = (String[]) row[3];
            ItemStack a = new ItemStack(item(corners[0]));
            ItemStack b = new ItemStack(item(corners[1]), 2);
            ItemStack c = new ItemStack(item(corners[2]));
            for (ArmorItem.Type type : TYPES) {
                ItemStack center = ready(type, from);
                center.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 2);
                center.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(1500));
                SimpleContainer container = new SimpleContainer(center, a, b, c, ItemStack.EMPTY);
                helper.assertTrue(ascension.matches(container, helper.getLevel()), id + " 应收 " + type);
                ItemStack out = ascension.assemble(container, helper.getLevel().registryAccess());
                helper.assertTrue(out.is(item(type)) && Attunement.stage(out) == from + 1 && mru(out) == 1500
                                && out.getEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION) == 2 && max(out) == ECGear.MHSConfig.maxMru(from + 1),
                        id + " 产物：升一阶、电和附魔留着、容量跟着阶");
            }
            helper.assertFalse(ascension.matches(new SimpleContainer(ready(ArmorItem.Type.HELMET, from), a, new ItemStack(item(corners[1]), 1), c, ItemStack.EMPTY),
                    helper.getLevel()), id + " 第二格只放 1 个不够");
            ItemStack notFull = new ItemStack(item(ArmorItem.Type.HELMET));
            Attunement.set(notFull, from, 0);
            Attunement.stampRank(notFull, 9);
            helper.assertFalse(ascension.matches(new SimpleContainer(notFull, a, b, c, ItemStack.EMPTY), helper.getLevel()), id + " 点数没攒满不收");
        }
        helper.assertFalse(((AttunementAscensionRecipe) helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id("attunement/mhs_1")).orElseThrow())
                        .matches(new SimpleContainer(ready(ArmorItem.Type.HELMET, 1), new ItemStack(item("pale_gem")), new ItemStack(item("magic_plate"), 2),
                                new ItemStack(item("crystal_dust")), ItemStack.EMPTY), helper.getLevel()),
                "0 → 1 那条不收 1 阶的");
        helper.succeed();
    }

    // ================================================================ 提示、配置

    /** 提示行：缓冲那一行按这件这一阶的百分比（胸甲 10% / 15% / 20%）、每点 100；2 阶起多「也挡放电」；配置默认值照 §4.3 / §4.4 */
    @GameTest(template = EMPTY)
    public static void mhsTooltipAndDefaults(GameTestHelper helper) {
        String shield = "tooltip.essentialcraft5.mhs_armor.shield";
        String discharge = "tooltip.essentialcraft5.mhs_armor.shield.discharge";
        String[] percents = {"10%", "15%", "20%"};
        for (int stage = 0; stage <= 2; stage++) {
            ItemStack stack = piece(ArmorItem.Type.CHESTPLATE, stage, 0);
            List<Component> lines = new ArrayList<>();
            item(ArmorItem.Type.CHESTPLATE).appendHoverText(stack, helper.getLevel(), lines, TooltipFlag.NORMAL);
            Object[] args = argsOf(lines, shield);
            helper.assertTrue(args != null && args.length == 2 && percents[stage].equals(String.valueOf(args[0])) && Integer.valueOf(100).equals(args[1]),
                    stage + " 阶缓冲行应是 " + percents[stage] + " / 100");
            helper.assertTrue(hasKey(lines, discharge) == (stage >= MHSArmorItem.DISCHARGE_STAGE), stage + " 阶「也挡放电」行");
        }
        ECGear.MHSConfig.Values values = ECGear.MHSConfig.values();
        helper.assertTrue(values.equals(ECGear.MHSConfig.DEFAULTS), "没改配置时就是默认值：" + values);
        helper.assertTrue(values.totalShare(0) == 5 && values.totalShare(1) == 7 && values.totalShare(2) == 9 && values.totalShare(7) == 9,
                "四件合计 25% / 35% / 45%，超出的阶按最后一项");
        helper.assertTrue(values.maxMru(0) == 2000 && values.maxMru(1) == 4000 && values.maxMru(2) == 8000 && values.mruPerDamage() == 100
                && values.afterflowTicks() == 40 && values.afterflowCooldown() == 200 && values.dischargePriceMultiplier() == 1D,
                "容量 2 000 / 4 000 / 8 000、每点 100、余流 40 / 200、放电原价");
        helper.assertTrue(item(ArmorItem.Type.HELMET).mruPerDamage(new ItemStack(item(ArmorItem.Type.HELMET))) == 100
                && item(ArmorItem.Type.CHESTPLATE).shieldShare(ArmorItem.Type.CHESTPLATE) == 2, "接口给的 0 阶单价、份额（护甲破坏者按它扣）");
        runConfig(values.toBuilder().shares(List.of(0), List.of(0), List.of(0), List.of(0)).build(), () -> {
            Player player = mock(helper, 1.5D, 1.5D);
            ItemStack[] stacks = wear(player, 2);
            Zombie zombie = zombie(helper);
            float seen = damageSeen(player, player.damageSources().mobAttack(zombie), rawFor(helper, 20F));
            helper.assertTrue(Math.abs(seen - 20F) < EPS && mru(stacks[1]) == 8000 && haste(player) == 0, "份额全填 0：不挡、不扣、不触发余流");
        });
        helper.succeed();
    }

    // ================================================================ 脱下夹生命（各套共用 ArmorTakeoffClamp）

    /** 一件 stage 阶的盔甲（阶 0 时不写同调数据） */
    private static ItemStack staged(Item armor, int stage) {
        ItemStack stack = new ItemStack(armor);
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        return stack;
    }

    /**
     * 换成同种低阶的一件也算脱下（同调每阶每件 +1 生命跟着少了），下一刻夹到新上限；换成高阶的不夹。宅男套装同理；
     * 风之护甲、审判庭胸甲以前脱下不夹，现在一样夹。照原版换装的顺序（先发事件、再按旧物品去修饰符、按新物品加）做，再发一刻 LivingTickEvent
     */
    @GameTest(template = EMPTY)
    public static void mhsLowerStageSwapClamps(GameTestHelper helper) {
        record Swap(String name, EquipmentSlot slot, ItemStack from, ItemStack to, boolean clamps) {
        }
        Item computerChest = ECComputer.armor().get(ArmorItem.Type.CHESTPLATE.ordinal()).get();
        List<Swap> swaps = List.of(
                new Swap("魔力防护胸甲 2 阶 → 0 阶", EquipmentSlot.CHEST, piece(ArmorItem.Type.CHESTPLATE, 2, 0), piece(ArmorItem.Type.CHESTPLATE, 0, 0), true),
                new Swap("魔力防护靴子 1 阶 → 0 阶", EquipmentSlot.FEET, piece(ArmorItem.Type.BOOTS, 1, 0), piece(ArmorItem.Type.BOOTS, 0, 0), true),
                new Swap("魔力防护胸甲 0 阶 → 1 阶", EquipmentSlot.CHEST, piece(ArmorItem.Type.CHESTPLATE, 0, 0), piece(ArmorItem.Type.CHESTPLATE, 1, 0), false),
                new Swap("宅男胸甲 3 阶 → 1 阶", EquipmentSlot.CHEST, staged(computerChest, 3), staged(computerChest, 1), true),
                new Swap("风之胸甲 2 阶 → 空手", EquipmentSlot.CHEST, staged(ECWindGear.WIND_CHESTPLATE.get(), 2), ItemStack.EMPTY, true),
                new Swap("审判庭胸甲 1 阶 → 铁胸甲", EquipmentSlot.CHEST, staged(ECInquisitorium.INQUISITORIUM_CHESTPLATE.get(), 1),
                        new ItemStack(Items.IRON_CHESTPLATE), true));
        for (Swap swap : swaps) {
            Player player = mock(helper, 1.5D, 1.5D);
            player.setItemSlot(swap.slot(), swap.from());
            applyModifiers(player, swap.from());
            float before = player.getMaxHealth();
            player.setHealth(before);
            MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, swap.slot(), swap.from(), swap.to()));
            player.getAttributes().removeAttributeModifiers(swap.from().getAttributeModifiers(swap.slot()));
            if (!swap.to().isEmpty()) {
                player.getAttributes().addTransientAttributeModifiers(swap.to().getAttributeModifiers(swap.slot()));
            }
            player.setItemSlot(swap.slot(), swap.to());
            float after = player.getMaxHealth();
            helper.assertTrue(swap.clamps() ? after < before : after >= before, swap.name() + "：上限应" + (swap.clamps() ? "变小" : "不变小") + "（前提），实际 "
                    + before + " → " + after);
            helper.assertTrue(ArmorTakeoffClamp.pendingForTest(player) == swap.clamps(), swap.name() + "：" + (swap.clamps() ? "应" : "不该") + "记下要夹生命");
            MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
            float want = swap.clamps() ? after : before;
            helper.assertTrue(player.getHealth() == want, swap.name() + "：下一刻生命应是 " + want + "，实际 " + player.getHealth());
        }
        helper.succeed();
    }

    /**
     * 真的换装（不手动发事件）：尸壳穿着 2 阶魔力防护胸甲、风之胸甲，世界里照常 tick（原版 detectEquipmentUpdates 发事件、换修饰符）。
     * 同一个物品对象只改 NBT（充能仓充电就是这样）不夹；换成 0 阶的、脱掉风之胸甲以后两刻内夹到新上限
     */
    @GameTest(template = EMPTY)
    public static void mhsTakeoffClampEndToEnd(GameTestHelper helper) {
        Husk mhs = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(1, 1, 1));
        Husk wind = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 1, 3));
        ItemStack chest = piece(ArmorItem.Type.CHESTPLATE, 2, 0);
        mhs.setItemSlot(EquipmentSlot.CHEST, chest);
        wind.setItemSlot(EquipmentSlot.CHEST, staged(ECWindGear.WIND_CHESTPLATE.get(), 2));
        double health = AttunementConfig.healthPerStage();
        float mhsBase = (float) mhs.getAttributeBaseValue(Attributes.MAX_HEALTH);
        float windBase = (float) wind.getAttributeBaseValue(Attributes.MAX_HEALTH);
        float mhsFull = mhsBase + (float) (ECGear.MHSConfig.values().maxHealth() + 2 * health);
        float mhsStageZero = mhsBase + (float) ECGear.MHSConfig.values().maxHealth();
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(mhs.getMaxHealth() == mhsFull && wind.getMaxHealth() == windBase + (float) (2 * health),
                            "穿上后的上限（前提），实际 " + mhs.getMaxHealth() + " / " + wind.getMaxHealth());
                    mhs.setHealth(mhs.getMaxHealth());
                    wind.setHealth(wind.getMaxHealth());
                    chest.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(800));
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(mhs.getItemBySlot(EquipmentSlot.CHEST) == chest && mhs.getHealth() == mhsFull,
                            "同一件只改了 NBT，不算脱下，生命应还是 " + mhsFull + "，实际 " + mhs.getHealth());
                    mhs.setItemSlot(EquipmentSlot.CHEST, piece(ArmorItem.Type.CHESTPLATE, 0, 0));
                    wind.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(mhs.getMaxHealth() == mhsStageZero && mhs.getHealth() == mhsStageZero,
                            "换成 0 阶的：夹到 " + mhsStageZero + "，实际 " + mhs.getHealth() + " / " + mhs.getMaxHealth());
                    helper.assertTrue(wind.getMaxHealth() == windBase && wind.getHealth() == windBase,
                            "脱掉风之胸甲：夹到 " + windBase + "，实际 " + wind.getHealth() + " / " + wind.getMaxHealth());
                })
                .thenSucceed();
    }

    // ================================================================ 护甲破坏者、空容量、修理

    /**
     * 全息投影的护甲破坏者：有电的魔力防护扣 5 × 100 MRU、不掉耐久；没电的照常掉 5 点耐久（以前什么都不扣，没电反倒比有电划算）；
     * 没电的虚空靴子穿在玩家身上照旧不掉耐久
     */
    @GameTest(template = EMPTY)
    public static void mhsArmorDestroyerWearsByCharge(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack chest = piece(ArmorItem.Type.CHESTPLATE, 0, 0);
        ItemStack legs = piece(ArmorItem.Type.LEGGINGS, 0, 1000);
        ItemStack boots = new ItemStack(ECVoidArmor.VOID_BOOTS.get());
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        ArmorDestroyerEntity.wear(player);
        helper.assertTrue(chest.getDamageValue() == ArmorDestroyerEntity.WEAR && mru(chest) == 0,
                "没电的魔力防护胸甲照常掉 5 点耐久，实际耐久损耗 " + chest.getDamageValue());
        helper.assertTrue(mru(legs) == 1000 - ArmorDestroyerEntity.WEAR * 100 && legs.getDamageValue() == 0,
                "有电的魔力防护护腿扣 500 MRU、不掉耐久，实际 " + mru(legs) + " / " + legs.getDamageValue());
        helper.assertTrue(boots.getDamageValue() == 0 && mru(boots) == 0, "没电的虚空靴子穿在玩家身上照旧不掉耐久");
        helper.succeed();
    }

    /** max_mru 配成空列表：各阶容量 0，存不进电、不挡，「满电约挡」当不挡 */
    @GameTest(template = EMPTY)
    public static void mhsEmptyCapacityList(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        Zombie zombie = zombie(helper);
        runConfig(ECGear.MHSConfig.DEFAULTS.toBuilder().maxMru(List.of()).build(), () -> {
            helper.assertTrue(ECGear.MHSConfig.maxMru(0) == 0 && ECGear.MHSConfig.maxMru(2) == 0, "空列表各阶容量 0");
            ItemStack[] stacks = wear(player, 2);
            for (ItemStack stack : stacks) {
                helper.assertTrue(max(stack) == 0 && mru(stack) == 0, "空列表：容量 0、存不进电，实际 " + mru(stack) + " / " + max(stack));
            }
            float seen = damageSeen(player, player.damageSources().mobAttack(zombie), rawFor(helper, 20F));
            helper.assertTrue(Math.abs(seen - 20F) < EPS, "没电不挡，实际 " + seen);
            DamageSource sample = ArmorCharge.samplePhysical(helper.getLevel());
            helper.assertTrue(sample != null && ArmorCharge.fullChargeHits(stacks[1], sample) == ArmorCharge.NOT_SHIELDED, "满电约挡：当不挡");
        });
        helper.succeed();
    }

    /**
     * 修理和同调阶（手册同调章的修理提示、装备章魔力防护的修理段照这个写）：能同调的盔甲不可修理，工作台修理配方、砂轮都合不了，
     * 铁砧合两件留左边那件的阶（全套不可修理见 AttunedArmorRepairTests）；两把剑没有耐久，工作台修理配方也不收
     */
    @GameTest(template = EMPTY)
    public static void mhsRepairKeepsStageOnlyOnAnvil(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack attuned = piece(ArmorItem.Type.CHESTPLATE, 2, 0);
        attuned.setDamageValue(600);
        ItemStack plain = piece(ArmorItem.Type.CHESTPLATE, 0, 0);
        plain.setDamageValue(300);

        helper.assertTrue(repairRecipe(helper, attuned, plain).isEmpty(), "魔力防护胸甲不进工作台修理配方");
        GrindstoneMenu armorStone = grindstone(player, attuned, plain);
        helper.assertTrue(armorStone.getSlot(2).getItem().isEmpty(), "魔力防护胸甲砂轮合不了");
        ItemStack anvilledArmor = anvil(player, attuned, plain);
        helper.assertTrue(Attunement.stage(anvilledArmor) == 2 && anvilledArmor.getDamageValue() < 600, "铁砧：魔力防护留左边那件的阶、修了耐久，实际阶 "
                + Attunement.stage(anvilledArmor) + "、耐久损耗 " + anvilledArmor.getDamageValue());

        // 余烬元素剑、裁决之刃没有耐久，工作台修理配方本来就不收；同调物品没有哪件会被工作台合修丢阶
        ItemStack sword = new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        Attunement.set(sword, 2, 0);
        ItemStack plainSword = new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        helper.assertTrue(!sword.isDamageableItem() && repairRecipe(helper, sword, plainSword).isEmpty(), "余烬元素剑不进工作台修理配方");
        ItemStack blade = new ItemStack(ECEmberForge.VERDICT_BLADE.get());
        Attunement.set(blade, 2, 0);
        helper.assertTrue(repairRecipe(helper, blade, new ItemStack(ECEmberForge.VERDICT_BLADE.get())).isEmpty(), "裁决之刃不进工作台修理配方");
        helper.succeed();
    }

    private static CraftingContainer grid(ItemStack first, ItemStack second) {
        CraftingContainer grid = new TransientCraftingContainer(new DummyMenu(), 3, 3);
        grid.setItem(0, first.copy());
        grid.setItem(1, second.copy());
        return grid;
    }

    private static Optional<CraftingRecipe> repairRecipe(GameTestHelper helper, ItemStack first, ItemStack second) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid(first, second), helper.getLevel());
    }

    private static GrindstoneMenu grindstone(Player player, ItemStack top, ItemStack bottom) {
        GrindstoneMenu menu = new GrindstoneMenu(0, player.getInventory());
        menu.getSlot(0).set(top.copy());
        menu.getSlot(1).set(bottom.copy());
        return menu;
    }

    private static ItemStack anvil(Player player, ItemStack left, ItemStack right) {
        AnvilMenu menu = new AnvilMenu(0, player.getInventory());
        menu.getSlot(0).set(left.copy());
        menu.getSlot(1).set(right.copy());
        menu.createResult();
        return menu.getSlot(2).getItem();
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
