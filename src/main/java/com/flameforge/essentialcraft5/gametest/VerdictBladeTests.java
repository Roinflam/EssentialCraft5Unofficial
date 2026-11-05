package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.ForgedElementalSwordItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.MatrixTraits;
import com.flameforge.essentialcraft5.item.VerdictBladeItem;
import com.flameforge.essentialcraft5.item.VerdictBladeItem.Judgment;
import com.flameforge.essentialcraft5.item.VerdictBladeItem.Outcome;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.ember.EmberEffect;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData.Element;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13b：余烬元素剑的两阶、裁决之刃（升格、属性、裁断、断罪）（batch13.md §4.4、§4.5、§12.2 13b 行）。
 * <p>
 * 裁断的结算直接调 {@link VerdictBladeItem#flush()}（本刻末那一次也会调，测试里先调掉，结论不跨刻）；
 * 数值覆盖（{@code setTestOverrides}、{@code setTestBossOverride}、{@code setTestStageValues}）都在同一段同步代码里设、测、置回。
 * 混合端的顺序照 {@code CombatModuleTests} 的做法手动发事件：LivingHurtEvent 之后插件取消就不会再有 LivingDamageEvent。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class VerdictBladeTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final float EPS = 1e-3F;

    private VerdictBladeTests() {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 能挨打的真玩家（出生无敌跑掉、满血），站在 relativeFeet */
    private static ServerPlayer fighter(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** 主手拿上这把剑，并把它的属性挂到玩家身上（测试玩家不走 LivingEntity.tick，装备属性不会自己刷新） */
    private static void wield(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getAttributes().addTransientAttributeModifiers(stack.getAttributeModifiers(EquipmentSlot.MAINHAND));
    }

    private static ForgedSwordData data(Element primary, Element secondary, EmberKind a, EmberKind b) {
        List<ResourceLocation> embers = List.of(a.id(), b.id(), a.id(), b.id());
        List<ResourceLocation> foci = List.of(Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus());
        return new ForgedSwordData(embers, foci, primary, secondary);
    }

    /** 满电、锻好、stage 阶的余烬元素剑 */
    private static ItemStack emberSword(ForgedSwordData data, int stage) {
        ItemStack stack = MRUStaffItem.full(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        data.writeTo(stack);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    /** 满电（50 000）、锻好（水 / 火、普通 + 燃烧余烬，攻击 +11）、stage 阶的裁决之刃 */
    private static ItemStack blade(int stage) {
        ItemStack stack = MRUStaffItem.full(ECEmberForge.VERDICT_BLADE.get());
        data(Element.WATER, Element.FIRE, EmberKind.COMMON, EmberKind.FLAME).writeTo(stack);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    private static void setMRU(ItemStack stack, int mru) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
    }

    private static int mru(ItemStack stack) {
        return MRUStaffItem.storedMRU(stack);
    }

    /** 不会动、不穿甲、maxHealth 生命的生物 */
    private static <T extends Mob> T dummy(GameTestHelper helper, EntityType<T> type, BlockPos relative, double maxHealth) {
        T mob = helper.spawnWithNoFreeWill(type, relative);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        mob.setHealth((float) maxHealth);
        if (mob.getAttribute(Attributes.ARMOR) != null) {
            mob.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        }
        return mob;
    }

    private static int duration(LivingEntity entity, MobEffect effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -1 : instance.getDuration();
    }

    private static <E extends Event> Runnable listen(EventPriority priority, Class<E> type, Consumer<E> listener) {
        MinecraftForge.EVENT_BUS.addListener(priority, false, type, listener);
        return () -> MinecraftForge.EVENT_BUS.unregister(listener);
    }

    private static VerdictBladeItem.Config.Values values(double percent, double percentStage, double cap, int verdictMru, double pvpPercent) {
        VerdictBladeItem.Config.Values d = VerdictBladeItem.Config.DEFAULTS;
        return new VerdictBladeItem.Config.Values(d.traitMultiplier(), percent, percentStage, cap, d.verdictCapStage(),
                verdictMru, pvpPercent, d.chargeTicks(), d.radius(), d.maxTargets(), d.damageMultiplier(), d.judgmentMru(), d.cooldown(),
                d.cooldownStage(), d.bisectTicks());
    }

    private static VerdictBladeItem.Config.Values judgmentValues(double radius, int maxTargets) {
        return judgmentValues(radius, maxTargets, VerdictBladeItem.Config.DEFAULTS.damageMultiplier());
    }

    private static VerdictBladeItem.Config.Values judgmentValues(double radius, int maxTargets, double damageMultiplier) {
        VerdictBladeItem.Config.Values d = VerdictBladeItem.Config.DEFAULTS;
        return new VerdictBladeItem.Config.Values(d.traitMultiplier(), d.verdictPercent(), d.verdictPercentStage(),
                d.verdictCap(), d.verdictCapStage(), d.verdictMru(), d.verdictPvpPercent(), d.chargeTicks(), radius, maxTargets, damageMultiplier,
                d.judgmentMru(), d.cooldown(), d.cooldownStage(), d.bisectTicks());
    }

    /** 攻击蓄力计时（{@code LivingEntity.attackStrengthTicker}，SRG f_20922_）：测试玩家不走 Player.tick，不会自己涨 */
    private static void setAttackTicker(Player player, int ticks) {
        ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, player, ticks, "f_20922_");
    }

    /** 蓄满挥一下：发 AttackEntityEvent（Player.attack 开头那个），代替真的挥剑；伤害再由测试自己 hurt */
    private static void swing(Player player, LivingEntity target) {
        setAttackTicker(player, 100);
        MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, target));
    }

    private static void reset() {
        VerdictBladeItem.flush();
        VerdictBladeItem.Config.setTestOverrides(null);
        VerdictBladeItem.setTestBossOverride(null);
        ForgedElementalSwordItem.Config.setTestStageValues(null);
    }

    private static void discard(LivingEntity... entities) {
        for (LivingEntity entity : entities) {
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : component.getString();
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
        if (item == null) {
            throw new IllegalStateException("没有物品 " + path);
        }
        return item;
    }

    // ================================================================ 余烬元素剑的两阶

    /**
     * 1 阶：主元素命中（地：失明、挖掘疲劳 40 → 80 刻）、副元素右键（水：生命恢复 50 → 100 刻）时长 ×2；挂在玩家身上只多给一半（40 → 60）；
     * 余烬（普通 + 腐化：虚弱）1 阶还不变（60 刻、50 MRU）。0 阶照旧。
     */
    @GameTest(template = EMPTY)
    public static void emberSwordStage1DoublesDurations(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-st1", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer victim = fighter(helper, "e5-st1-v", new Vec3(3.5D, 1D, 3.5D));
        Husk husk0 = dummy(helper, EntityType.HUSK, new BlockPos(2, 1, 0), 20D);
        Husk husk1 = dummy(helper, EntityType.HUSK, new BlockPos(2, 1, 2), 20D);
        try {
            ForgedSwordData forged = data(Element.EARTH, Element.WATER, EmberKind.COMMON, EmberKind.CORRUPTED);
            ItemStack stage0 = emberSword(forged, 0);
            ForgedElementalSwordItem sword = (ForgedElementalSwordItem) stage0.getItem();
            sword.hurtEnemy(stage0, husk0, player);
            helper.assertTrue(duration(husk0, MobEffects.BLINDNESS) == 40 && duration(husk0, MobEffects.WEAKNESS) == 60,
                    "0 阶：失明 40、虚弱 60，实际 " + duration(husk0, MobEffects.BLINDNESS) + " / " + duration(husk0, MobEffects.WEAKNESS));

            ItemStack stage1 = emberSword(forged, 1);
            int full = mru(stage1);
            sword.hurtEnemy(stage1, husk1, player);
            helper.assertTrue(duration(husk1, MobEffects.BLINDNESS) == 80 && duration(husk1, MobEffects.DIG_SLOWDOWN) == 80,
                    "1 阶：主元素时长 ×2 = 80，实际 " + duration(husk1, MobEffects.BLINDNESS));
            helper.assertTrue(duration(husk1, MobEffects.WEAKNESS) == 60, "1 阶：余烬时长不变 60，实际 " + duration(husk1, MobEffects.WEAKNESS));
            helper.assertTrue(mru(stage1) == full - 2 * ForgedElementalSwordItem.COST, "1 阶：主元素 50 + 余烬 50，剩 " + mru(stage1));

            sword.hurtEnemy(stage1, victim, player);
            helper.assertTrue(duration(victim, MobEffects.BLINDNESS) == 60,
                    "挂在玩家身上：多出来的那份按 pvp_effect_scale（0.5），40 × 1.5 = 60，实际 " + duration(victim, MobEffects.BLINDNESS));

            helper.assertTrue(ForgedElementalSwordItem.applySecondary(stage1, Element.WATER, player)
                    && duration(player, MobEffects.REGENERATION) == 2 * ForgedElementalSwordItem.USE_REGENERATION_TICKS, "1 阶：副元素右键时长 ×2");
            helper.assertTrue(Math.abs(AttunementConfig.pvpEffectScale() - 0.5D) < 1e-9D, "前提：pvp_effect_scale 默认 0.5");
        } finally {
            discard(husk0, husk1);
            reset();
        }
        helper.succeed();
    }

    /**
     * 2 阶：余烬命中只花一半（50 → 25）、时长 ×1.5（60 → 90；挂在玩家身上 75）；主元素照 1 阶 ×2。
     * 余烬嵌片走的公共入口不吃剑的阶（60）。配置填 0：余烬不花 MRU、效果照给。
     */
    @GameTest(template = EMPTY)
    public static void emberSwordStage2HalvesEmberCost(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-st2", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer victim = fighter(helper, "e5-st2-v", new Vec3(3.5D, 1D, 3.5D));
        Husk husk = dummy(helper, EntityType.HUSK, new BlockPos(2, 1, 0), 20D);
        Husk inlay = dummy(helper, EntityType.HUSK, new BlockPos(2, 1, 2), 20D);
        Husk free = dummy(helper, EntityType.HUSK, new BlockPos(0, 1, 2), 20D);
        try {
            ItemStack stack = emberSword(data(Element.EARTH, Element.WATER, EmberKind.COMMON, EmberKind.CORRUPTED), 2);
            ForgedElementalSwordItem sword = (ForgedElementalSwordItem) stack.getItem();
            helper.assertTrue(sword.emberCost(stack) == 25, "2 阶余烬 25 MRU，实际 " + sword.emberCost(stack));
            int full = mru(stack);
            sword.hurtEnemy(stack, husk, player);
            helper.assertTrue(duration(husk, MobEffects.WEAKNESS) == 90 && duration(husk, MobEffects.BLINDNESS) == 80,
                    "2 阶：余烬 90、主元素 80，实际 " + duration(husk, MobEffects.WEAKNESS) + " / " + duration(husk, MobEffects.BLINDNESS));
            helper.assertTrue(mru(stack) == full - ForgedElementalSwordItem.COST - 25, "主元素 50 + 余烬 25，剩 " + mru(stack));

            sword.hurtEnemy(stack, victim, player);
            helper.assertTrue(duration(victim, MobEffects.WEAKNESS) == 75, "挂在玩家身上 60 × (1 + 0.5 × 0.5) = 75，实际 " + duration(victim, MobEffects.WEAKNESS));

            ForgedElementalSwordItem.applyEmber(EmberEffect.WEAKNESS, inlay, player);
            helper.assertTrue(duration(inlay, MobEffects.WEAKNESS) == EmberEffect.DURATION, "嵌片的入口不吃剑的阶：60");

            ForgedElementalSwordItem.Config.setTestStageValues(new ForgedElementalSwordItem.Config.StageValues(2D, 0D, 1.5D));
            ItemStack zero = emberSword(data(Element.FIRE, Element.WATER, EmberKind.COMMON, EmberKind.CORRUPTED), 2);
            helper.assertTrue(sword.emberCost(zero) == 0, "花费倍数填 0：余烬不花");
            int before = mru(zero);
            sword.hurtEnemy(zero, free, player);
            helper.assertTrue(mru(zero) == before - ForgedElementalSwordItem.COST && duration(free, MobEffects.WEAKNESS) == 90,
                    "花费 0：只扣主元素 50、余烬照给，实际剩 " + mru(zero) + "、虚弱 " + duration(free, MobEffects.WEAKNESS));
        } finally {
            discard(husk, inlay, free);
            reset();
        }
        helper.succeed();
    }

    // ================================================================ 裁决之刃：物品与配方

    /** 攻击 = 余烬剑原值 + 6、攻速同余烬剑、自带 50 000 MRU、没耐久、能附魔；三个标签；矩阵特性 ×1.5；余烬剑 2 阶的倍数一直有 */
    @GameTest(template = EMPTY)
    public static void verdictBladeAttributes(GameTestHelper helper) {
        ItemStack stack = blade(0);
        ForgedSwordData forged = ForgedSwordData.read(stack).orElseThrow();
        double attack = 0D;
        double speed = 0D;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                attack += modifier.getAmount();
            }
        }
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED)) {
            speed += modifier.getAmount();
        }
        int ember = ForgedElementalSwordItem.attackBonus(forged);
        helper.assertTrue(Math.abs(attack - (ember + VerdictBladeItem.ATTACK_BONUS)) < EPS, "攻击 = 余烬剑 " + ember + " + 6，实际 " + attack);
        helper.assertTrue(Math.abs(speed - ForgedElementalSwordItem.ATTACK_SPEED) < EPS, "攻速同余烬剑 -2.4，实际 " + speed);
        helper.assertTrue(VerdictBladeItem.attackBonus(new ItemStack(ECEmberForge.VERDICT_BLADE.get())) == VerdictBladeItem.ATTACK_BONUS, "没锻造的 +6");
        helper.assertTrue(mru(stack) == VerdictBladeItem.MAX_MRU && VerdictBladeItem.MAX_MRU == 50_000, "自带 50 000 MRU，实际 " + mru(stack));
        helper.assertTrue(!stack.isDamageableItem() && stack.isEnchantable(), "没耐久、能附魔");
        helper.assertTrue(stack.is(ItemTags.SWORDS) && stack.is(ECTags.MATRIX_ATTUNED) && AttunementFamily.of(stack) == ECAttunement.VERDICT_BLADE,
                "剑、矩阵特性、同调家族 verdict_blade");
        helper.assertTrue(ECAttunement.VERDICT_BLADE.maxStage() == 5, "最高 5 阶");
        helper.assertTrue(Math.abs(MatrixTraits.weaponMultiplier(stack) - 1.5D) < 1e-9D, "矩阵特性 ×1.5，实际 " + MatrixTraits.weaponMultiplier(stack));
        VerdictBladeItem item = (VerdictBladeItem) stack.getItem();
        helper.assertTrue(item.emberCost(stack) == 25 && Math.abs(item.primaryDurationMultiplier(stack) - 2D) < 1e-9D
                        && Math.abs(item.emberDurationMultiplier(stack) - 1.5D) < 1e-9D,
                "0 阶的裁决之刃也按余烬剑 2 阶算：余烬 25、主元素 ×2、余烬 ×1.5");
        helper.succeed();
    }

    /**
     * 升格配方（JSON）：中心是攒满阶戳的 2 阶余烬元素剑；1 阶的、阶戳不够的不行。产物保留锻造数据、附魔、剑里的 MRU，0 阶、带累计的一半（夹到 300）。
     */
    @GameTest(template = EMPTY)
    public static void verdictPromotionKeepsForging(GameTestHelper helper) {
        Recipe<?> found = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id("attunement/verdict_promotion")).orElse(null);
        helper.assertTrue(found instanceof AttunementAscensionRecipe, "升格配方应能加载");
        AttunementAscensionRecipe recipe = (AttunementAscensionRecipe) found;
        helper.assertTrue(recipe.family().equals(ECAttunement.EMBER_SWORD.id()) && recipe.fromStage() == 2
                        && recipe.promoteTo().is(ECEmberForge.VERDICT_BLADE.get()) && recipe.getMRU() == 200_000,
                "余烬剑 2 阶 → 裁决之刃、进度 200 000");
        ForgedSwordData forged = data(Element.EARTH, Element.AIR, EmberKind.CHAOS, EmberKind.DIVINE);
        ItemStack sword = emberSword(forged, 2);
        setMRU(sword, 4_321);
        sword.enchant(Enchantments.SHARPNESS, 3);
        sword.getOrCreateTagElement(Attunement.TAG).putInt(Attunement.KEY_TOTAL, 1_100);
        int rank = recipe.requiredRank();
        helper.assertTrue(rank == AttunementConfig.requiredRank(3), "升格的阶戳门槛按 3 阶算");
        Attunement.stampRank(sword, rank);
        ItemStack chip = new ItemStack(item("inquisitorium_chip"));
        ItemStack core = new ItemStack(item("demonic_core"));
        ItemStack eternity = new ItemStack(item("matter_of_eternity"));
        // 第四角 13c 起是矩阵结晶 ×2（以前兜底用审判庭芯片）
        ItemStack crystals = new ItemStack(item("matrix_crystal"), 2);
        helper.assertFalse(recipe.matches(new SimpleContainer(sword, chip, core, eternity, chip.copy()), helper.getLevel()), "第四角放审判庭芯片不再匹配");
        helper.assertFalse(recipe.matches(new SimpleContainer(sword, chip, core, eternity, new ItemStack(item("matrix_crystal"))), helper.getLevel()),
                "矩阵结晶只有 1 个不匹配");
        SimpleContainer inputs = new SimpleContainer(sword, chip, core, eternity, crystals);
        helper.assertTrue(recipe.matches(inputs, helper.getLevel()), "攒满阶戳的 2 阶余烬剑应匹配");

        ItemStack out = recipe.assemble(inputs, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ECEmberForge.VERDICT_BLADE.get()) && Attunement.stage(out) == 0, "产物是 0 阶裁决之刃");
        helper.assertTrue(forged.equals(ForgedSwordData.read(out).orElse(null)), "元素、余烬照搬");
        helper.assertTrue(out.getEnchantmentLevel(Enchantments.SHARPNESS) == 3 && mru(out) == 4_321, "附魔、MRU 照搬，实际 MRU " + mru(out));
        helper.assertTrue(Attunement.points(out) == 300 && Attunement.rankStamp(out) == rank, "累计 1 100 的一半夹到 300，实际 " + Attunement.points(out));

        if (rank > 0) {
            ItemStack low = emberSword(forged, 2);
            Attunement.stampRank(low, rank - 1);
            helper.assertFalse(recipe.matches(new SimpleContainer(low, chip, core, eternity, crystals.copy()), helper.getLevel()), "阶戳不够不匹配");
        }
        ItemStack stage1 = emberSword(forged, 1);
        Attunement.stampRank(stage1, 9);
        helper.assertFalse(recipe.matches(new SimpleContainer(stage1, chip, core, eternity, crystals.copy()), helper.getLevel()), "1 阶的不匹配");
        ItemStack bladeCenter = blade(2);
        Attunement.stampRank(bladeCenter, 9);
        helper.assertFalse(recipe.matches(new SimpleContainer(bladeCenter, chip, core, eternity, crystals.copy()), helper.getLevel()), "裁决之刃本身不能再升格");
        helper.succeed();
    }

    /** 七条升阶 / 升格配方都在：余烬剑 0→1、1→2，升格，裁决之刃 0→1 … 4→5；进度照 §4.5；「任意余烬」标签 8 种、×2 */
    @GameTest(template = EMPTY)
    public static void verdictAscensionRecipesComplete(GameTestHelper helper) {
        String[] ids = {"ember_sword_1", "ember_sword_2", "verdict_1", "verdict_2", "verdict_3", "verdict_4", "verdict_5"};
        int[] mru = {40_000, 60_000, 60_000, 80_000, 100_000, 120_000, 150_000};
        for (int i = 0; i < ids.length; i++) {
            Recipe<?> found = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id("attunement/" + ids[i])).orElse(null);
            helper.assertTrue(found instanceof AttunementAscensionRecipe, ids[i] + " 应能加载");
            AttunementAscensionRecipe recipe = (AttunementAscensionRecipe) found;
            boolean sword = i < 2;
            int from = sword ? i : i - 2;
            helper.assertTrue(recipe.family().equals((sword ? ECAttunement.EMBER_SWORD : ECAttunement.VERDICT_BLADE).id())
                    && recipe.fromStage() == from && recipe.promoteTo().isEmpty(), ids[i] + "：家族 / 起始阶不对");
            helper.assertTrue(recipe.getMRU() == mru[i], ids[i] + " 进度应为 " + mru[i] + "，实际 " + recipe.getMRU());
        }
        AttunementAscensionRecipe first = (AttunementAscensionRecipe) helper.getLevel().getRecipeManager()
                .byKey(EssentialCraft5.id("attunement/ember_sword_1")).orElseThrow();
        helper.assertTrue(first.countAt(1) == 2, "余烬剑 0→1：左上任意余烬 ×2");
        for (EmberKind kind : EmberKind.values()) {
            Item ember = ForgeRegistries.ITEMS.getValue(kind.id());
            helper.assertTrue(ember != null && first.acceptsAt(1, new ItemStack(ember, 2)), kind + " 应算任意余烬");
        }
        ItemStack center = emberSword(data(Element.FIRE, Element.FIRE, EmberKind.COMMON, EmberKind.FLAME), 0);
        Attunement.set(center, 0, AttunementConfig.threshold(0));
        Attunement.stampRank(center, AttunementConfig.requiredRank(1));
        SimpleContainer inputs = new SimpleContainer(center, new ItemStack(item("acidic_ember"), 2), new ItemStack(item("void_plating")),
                new ItemStack(item("demonic_plate")), ItemStack.EMPTY);
        helper.assertTrue(first.matches(inputs, helper.getLevel()), "攒满的 0 阶余烬剑 + 两个余烬 + 虚空护板 + 恶魔板应匹配");
        ItemStack up = first.assemble(inputs, helper.getLevel().registryAccess());
        helper.assertTrue(up.is(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()) && Attunement.stage(up) == 1, "升到 1 阶，还是余烬元素剑");
        helper.succeed();
    }

    // ================================================================ 裁断

    /**
     * 普通目标：蓄满打实后本刻末补 最大生命 × 3%（2 阶 4%），无视护甲、攻击者是持剑人、不带直接实体、不再击退；扣 200。
     * 同一刻同一名玩家对同一个目标只补一下；补完目标的无敌帧放回原来那一下的。
     */
    @GameTest(template = EMPTY)
    public static void verdictStrikesAfterHit(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-verdict", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 0), 200D);
        Zombie later = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 2), 200D);
        try {
            zombie.getAttribute(Attributes.ARMOR).setBaseValue(20D);
            ItemStack stack = blade(0);
            wield(player, stack);
            swing(player, zombie);
            zombie.hurt(player.damageSources().playerAttack(player), 5F);
            zombie.invulnerableTime = 0;
            zombie.hurt(player.damageSources().playerAttack(player), 6F);
            helper.assertTrue(VerdictBladeItem.pendingCount() == 2, "两下都记着，实际 " + VerdictBladeItem.pendingCount());
            float before = zombie.getHealth();
            Vec3 motion = zombie.getDeltaMovement();
            int dealt = VerdictBladeItem.flush();
            helper.assertTrue(dealt == 1, "同一刻同一个目标只补一下，实际 " + dealt);
            helper.assertTrue(Math.abs(before - zombie.getHealth() - 6F) < EPS, "200 × 3% = 6，护甲 20 照样全吃，实际 " + (before - zombie.getHealth()));
            DamageSource last = zombie.getLastDamageSource();
            helper.assertTrue(last != null && last.is(VerdictBladeItem.VERDICT) && last.is(DamageTypeTags.BYPASSES_ARMOR)
                    && last.getEntity() == player && last.getDirectEntity() == null, "伤害类型 verdict、攻击者是持剑人：" + last);
            helper.assertTrue(zombie.getDeltaMovement().equals(motion), "补的那一下不再击退");
            helper.assertTrue(zombie.invulnerableTime == 20, "补完无敌帧放回原来那一下的 20，实际 " + zombie.invulnerableTime);
            helper.assertTrue(mru(stack) == VerdictBladeItem.MAX_MRU - 200, "扣 200，剩 " + mru(stack));

            ItemStack stage2 = blade(2);
            wield(player, stage2);
            swing(player, later);
            later.hurt(player.damageSources().playerAttack(player), 1F);
            float mid = later.getHealth();
            VerdictBladeItem.flush();
            helper.assertTrue(Math.abs(mid - later.getHealth() - 8F) < EPS, "2 阶 4%：8，实际 " + (mid - later.getHealth()));
        } finally {
            discard(zombie, later);
            reset();
        }
        helper.succeed();
    }

    /**
     * 只认蓄满的一下：不走 Player.attack 直接 hurt 的、蓄力没满的不补不扣；真的 Player.attack 蓄满了补 6、扣 200，
     * 补完无敌窗口放回，紧接着再挥一下（蓄力刚归零，0.2 倍伤害）被原版无敌窗口挡掉，也不补。
     */
    @GameTest(template = EMPTY)
    public static void verdictNeedsFullCharge(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-charge", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 0), 200D);
        try {
            ItemStack stack = blade(0);
            wield(player, stack);
            zombie.hurt(player.damageSources().playerAttack(player), 5F);
            helper.assertTrue(VerdictBladeItem.pendingCount() == 0, "没挥剑（不走 Player.attack）直接 hurt：不记");
            VerdictBladeItem.flush();

            setAttackTicker(player, 2);
            zombie.invulnerableTime = 0;
            MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, zombie));
            zombie.hurt(player.damageSources().playerAttack(player), 2F);
            helper.assertTrue(VerdictBladeItem.pendingCount() == 0, "蓄力 " + player.getAttackStrengthScale(0.5F) + " 没满：不记");
            float before = zombie.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 0 && zombie.getHealth() == before && mru(stack) == VerdictBladeItem.MAX_MRU, "没蓄满：不补、不扣");

            zombie.invulnerableTime = 0;
            setAttackTicker(player, 100);
            float start = zombie.getHealth();
            player.attack(zombie);
            helper.assertTrue(zombie.getHealth() < start && VerdictBladeItem.pendingCount() == 1,
                    "蓄满的 Player.attack 打实、记下，实际掉 " + (start - zombie.getHealth()) + "、记了 " + VerdictBladeItem.pendingCount());
            int mruBefore = mru(stack);
            float hit = zombie.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 1 && Math.abs(hit - zombie.getHealth() - 6F) < EPS && mru(stack) == mruBefore - 200,
                    "补 6、扣 200，实际补 " + (hit - zombie.getHealth()) + "、扣 " + (mruBefore - mru(stack)));
            helper.assertTrue(zombie.invulnerableTime > 10, "补完无敌帧放回，实际 " + zombie.invulnerableTime);

            float after = zombie.getHealth();
            player.attack(zombie);
            helper.assertTrue(zombie.getHealth() == after && VerdictBladeItem.pendingCount() == 0,
                    "紧接着没蓄满的一下打不进无敌窗口、不记，实际掉 " + (after - zombie.getHealth()));
            helper.assertTrue(VerdictBladeItem.flush() == 0 && zombie.getHealth() == after, "不补");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    /**
     * 补完把目标的无敌窗口原样放回：无敌帧还是原来那一下的 20、lastHurt 还是 5（窗口里 5 点打不进，5.5 只打进差额 0.5）；
     * 补的那下被插件 / 领地拦下（LivingAttackEvent 取消）时不扣，原来那一下的无敌帧也不丢。
     */
    @GameTest(template = EMPTY)
    public static void verdictRestoresHurtWindow(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-window", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 0), 200D);
        Zombie guarded = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 2), 200D);
        Runnable unlisten = listen(EventPriority.NORMAL, LivingAttackEvent.class, event -> {
            if (event.getEntity() == guarded && event.getSource().is(VerdictBladeItem.VERDICT)) {
                event.setCanceled(true);
            }
        });
        try {
            ItemStack stack = blade(0);
            wield(player, stack);
            swing(player, zombie);
            zombie.hurt(player.damageSources().playerAttack(player), 5F);
            float before = zombie.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 1 && Math.abs(before - zombie.getHealth() - 6F) < EPS, "补 6");
            helper.assertTrue(zombie.invulnerableTime == 20, "无敌帧放回原来那一下的 20，实际 " + zombie.invulnerableTime);
            DamageSource generic = helper.getLevel().damageSources().generic();
            helper.assertFalse(zombie.hurt(generic, 5F), "lastHurt 放回 5：窗口里 5 点打不进");
            float mid = zombie.getHealth();
            helper.assertTrue(zombie.hurt(generic, 5.5F) && Math.abs(mid - zombie.getHealth() - 0.5F) < EPS,
                    "窗口里 5.5 只打进差额 0.5，实际 " + (mid - zombie.getHealth()));

            swing(player, guarded);
            guarded.hurt(player.damageSources().playerAttack(player), 5F);
            float guardedBefore = guarded.getHealth();
            int mruBefore = mru(stack);
            helper.assertTrue(VerdictBladeItem.flush() == 0 && guarded.getHealth() == guardedBefore && mru(stack) == mruBefore,
                    "补的那下被拦下：不补、不扣");
            helper.assertTrue(guarded.invulnerableTime == 20, "原来那一下的无敌帧不丢，实际 " + guarded.invulnerableTime);
        } finally {
            unlisten.run();
            discard(zombie, guarded);
            reset();
        }
        helper.succeed();
    }

    /** 封顶、配置填 0：percent / cap 为 0 不补也不扣；mru 为 0 照补不扣；阶加百分比和封顶 */
    @GameTest(template = EMPTY)
    public static void verdictCapAndZeroConfig(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-cap", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 0), 200D);
        try {
            ItemStack stage0 = blade(0);
            ItemStack stage3 = blade(3);
            helper.assertTrue(Math.abs(VerdictBladeItem.verdictAmount(stage3, zombie) - 9F) < EPS, "3 阶 4.5% × 200 = 9");
            VerdictBladeItem.Config.setTestOverrides(values(3D, 0.5D, 4D, 200, 0D));
            helper.assertTrue(Math.abs(VerdictBladeItem.verdictAmount(stage0, zombie) - 4F) < EPS, "封顶 4：6 → 4");
            helper.assertTrue(Math.abs(VerdictBladeItem.verdictAmount(stage3, zombie) - 9F) < EPS, "3 阶封顶 4 + 30 = 34：9 不夹");

            wield(player, stage0);
            VerdictBladeItem.Config.setTestOverrides(values(0D, 0D, 40D, 200, 0D));
            swing(player, zombie);
            zombie.hurt(player.damageSources().playerAttack(player), 1F);
            float before = zombie.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 0 && zombie.getHealth() == before && mru(stage0) == VerdictBladeItem.MAX_MRU, "percent 0：不补、不扣");

            VerdictBladeItem.Config.setTestOverrides(values(3D, 0.5D, 0D, 200, 0D));
            zombie.invulnerableTime = 0;
            swing(player, zombie);
            zombie.hurt(player.damageSources().playerAttack(player), 1F);
            before = zombie.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 0 && zombie.getHealth() == before && mru(stage0) == VerdictBladeItem.MAX_MRU, "cap 0：不补、不扣");

            VerdictBladeItem.Config.setTestOverrides(values(3D, 0.5D, 40D, 0, 0D));
            zombie.invulnerableTime = 0;
            swing(player, zombie);
            zombie.hurt(player.damageSources().playerAttack(player), 1F);
            before = zombie.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 1 && Math.abs(before - zombie.getHealth() - 6F) < EPS && mru(stage0) == VerdictBladeItem.MAX_MRU,
                    "mru 0：照补 6、不扣");
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    /**
     * 首领（#forge:bosses）不补裁断：蓄满打实只掉这一下的血，不记、不扣，无敌帧照原版；{@code verdictAmount} 对首领是 0。
     * 别的模组的首领多把单次封顶写在 hurt() 覆写里，在伤害事件里加数会越过封顶（batch13.md §4.4）。
     */
    @GameTest(template = EMPTY)
    public static void verdictSkipsBosses(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-boss", new Vec3(0.5D, 1D, 0.5D));
        Zombie boss = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 0), 200D);
        try {
            VerdictBladeItem.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            ItemStack stack = blade(5);
            wield(player, stack);
            helper.assertTrue(VerdictBladeItem.verdictAmount(stack, boss) == 0F, "首领的裁断是 0");
            swing(player, boss);
            boss.hurt(player.damageSources().playerAttack(player), 10F);
            helper.assertTrue(Math.abs(200F - boss.getHealth() - 10F) < EPS, "只掉这一下的 10，实际 " + (200F - boss.getHealth()));
            helper.assertTrue(VerdictBladeItem.pendingCount() == 0, "首领不记");
            float before = boss.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 0 && boss.getHealth() == before && boss.invulnerableTime == 20,
                    "结算不碰首领的血和无敌帧");
            helper.assertTrue(mru(stack) == VerdictBladeItem.MAX_MRU, "不扣，剩 " + mru(stack));
        } finally {
            discard(boss);
            reset();
        }
        helper.succeed();
    }

    /**
     * 混合端先算后扣：普通目标的 LivingDamageEvent 记下以后又被取消（插件排在后面拦下）——不补、不扣；没被取消的照补照扣。
     */
    @GameTest(template = EMPTY)
    public static void verdictMixedServerChargesAfterLanding(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-mixed", new Vec3(0.5D, 1D, 0.5D));
        Zombie normal = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 2), 200D);
        try {
            ItemStack stack = blade(0);
            wield(player, stack);
            DamageSource source = player.damageSources().playerAttack(player);
            swing(player, normal);
            LivingDamageEvent canceled = new LivingDamageEvent(normal, source, 5F);
            MinecraftForge.EVENT_BUS.post(canceled);
            helper.assertTrue(VerdictBladeItem.pendingCount() == 1, "普通目标记下了");
            canceled.setCanceled(true);
            float before = normal.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 0 && normal.getHealth() == before && mru(stack) == VerdictBladeItem.MAX_MRU,
                    "记下以后又被取消：不补、不扣");

            swing(player, normal);
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(normal, source, 5F));
            before = normal.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 1 && Math.abs(before - normal.getHealth() - 6F) < EPS
                    && mru(stack) == VerdictBladeItem.MAX_MRU - 200, "没被取消：补 6、扣 200，剩 " + mru(stack));
        } finally {
            discard(normal);
            reset();
        }
        helper.succeed();
    }

    /**
     * 打玩家：默认 pvp_percent 0，不补不扣；改成 10：20 × 10% = 2，不随阶涨；被 PvP / 领地拦下（LivingAttackEvent 取消）不扣。
     * 封顶对玩家也不随阶涨：pvp_percent 50、对方 200 生命、5 阶剑补的是 cap 40，不是 40 + 5 × 10 = 90。
     */
    @GameTest(template = EMPTY)
    public static void verdictAgainstPlayers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        ServerPlayer attacker = fighter(helper, "e5-pvp-a", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer victim = fighter(helper, "e5-pvp-v", new Vec3(2.5D, 1D, 0.5D));
        boolean[] deny = {false};
        Runnable unlisten = listen(EventPriority.NORMAL, LivingAttackEvent.class, event -> {
            if (deny[0] && event.getEntity() == victim && event.getSource().is(VerdictBladeItem.VERDICT)) {
                event.setCanceled(true);
            }
        });
        try {
            server.setPvpAllowed(true);
            ItemStack stack = blade(3);
            wield(attacker, stack);
            swing(attacker, victim);
            helper.assertTrue(victim.hurt(attacker.damageSources().playerAttack(attacker), 2F), "前提：PvP 开着能打");
            float before = victim.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 0 && victim.getHealth() == before && mru(stack) == VerdictBladeItem.MAX_MRU, "默认不打玩家");

            VerdictBladeItem.Config.setTestOverrides(values(3D, 0.5D, 40D, 200, 10D));
            victim.invulnerableTime = 0;
            swing(attacker, victim);
            victim.hurt(attacker.damageSources().playerAttack(attacker), 2F);
            before = victim.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 1 && Math.abs(before - victim.getHealth() - 2F) < EPS,
                    "pvp_percent 10：20 × 10% = 2，3 阶也不涨，实际 " + (before - victim.getHealth()));
            helper.assertTrue(mru(stack) == VerdictBladeItem.MAX_MRU - 200, "打成了扣 200");

            deny[0] = true;
            victim.invulnerableTime = 0;
            swing(attacker, victim);
            victim.hurt(attacker.damageSources().playerAttack(attacker), 2F);
            before = victim.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 0 && victim.getHealth() == before && mru(stack) == VerdictBladeItem.MAX_MRU - 200,
                    "补的那一下被拦下：不扣");

            VerdictBladeItem.Config.setTestOverrides(values(50D, 0.5D, 40D, 200, 50D));
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
            helper.assertTrue(Math.abs(VerdictBladeItem.verdictAmount(blade(5), victim) - 40F) < EPS,
                    "200 生命、pvp_percent 50、5 阶：封顶只用 cap 40，实际 " + VerdictBladeItem.verdictAmount(blade(5), victim));
            Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(4, 1, 0), 200D);
            try {
                helper.assertTrue(Math.abs(VerdictBladeItem.verdictAmount(blade(5), zombie) - 90F) < EPS,
                        "对照：打怪 52.5% × 200 = 105，封顶照随阶涨到 90，实际 " + VerdictBladeItem.verdictAmount(blade(5), zombie));
            } finally {
                discard(zombie);
            }
        } finally {
            unlisten.run();
            server.setPvpAllowed(pvp);
            reset();
        }
        helper.succeed();
    }

    /** 不算的：付不起（主刀照打）、副手拿着、荆棘 / 魔法伤害、假玩家 */
    @GameTest(template = EMPTY)
    public static void verdictOnlyRealMainHandHits(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-only", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 0), 200D);
        try {
            ItemStack poor = blade(0);
            setMRU(poor, 100);
            wield(player, poor);
            swing(player, zombie);
            helper.assertTrue(zombie.hurt(player.damageSources().playerAttack(player), 3F), "主刀照打");
            float before = zombie.getHealth();
            helper.assertTrue(VerdictBladeItem.flush() == 0 && zombie.getHealth() == before && mru(poor) == 100, "付不起 200：不补、不扣");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, blade(0));
            zombie.invulnerableTime = 0;
            swing(player, zombie);
            zombie.hurt(player.damageSources().playerAttack(player), 1F);
            helper.assertTrue(VerdictBladeItem.pendingCount() == 0, "副手拿着不算");

            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, blade(0));
            swing(player, zombie);
            zombie.invulnerableTime = 0;
            zombie.hurt(player.damageSources().thorns(player), 1F);
            zombie.invulnerableTime = 0;
            zombie.hurt(player.damageSources().indirectMagic(player, player), 1F);
            helper.assertTrue(VerdictBladeItem.pendingCount() == 0, "荆棘、魔法伤害不算");

            ServerPlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
            ItemStack previous = fake.getMainHandItem();
            fake.setItemInHand(InteractionHand.MAIN_HAND, blade(0));
            try {
                swing(fake, zombie);
                zombie.invulnerableTime = 0;
                zombie.hurt(fake.damageSources().playerAttack(fake), 1F);
                helper.assertTrue(VerdictBladeItem.pendingCount() == 0, "假玩家（机械手）不算");
            } finally {
                fake.setItemInHand(InteractionHand.MAIN_HAND, previous);
            }
        } finally {
            discard(zombie);
            reset();
        }
        helper.succeed();
    }

    // ================================================================ 断罪

    /** 0~1 阶：没解锁（LOCKED）、右键照余烬剑格挡；2 阶起：蓄力动作、不再当盾 */
    @GameTest(template = EMPTY)
    public static void judgmentLockedBelowStage2(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-lock", new Vec3(0.5D, 1D, 0.5D));
        ItemStack stage1 = blade(1);
        wield(player, stage1);
        helper.assertTrue(VerdictBladeItem.judgment(player, stage1).outcome() == Outcome.LOCKED, "1 阶：没解锁");
        helper.assertTrue(stage1.getUseAnimation() == UseAnim.BLOCK && stage1.canPerformAction(ToolActions.SHIELD_BLOCK), "1 阶右键格挡");
        ItemStack stage2 = blade(2);
        helper.assertTrue(stage2.getUseAnimation() == UseAnim.SPEAR && !stage2.canPerformAction(ToolActions.SHIELD_BLOCK)
                && stage2.canPerformAction(ToolActions.SWORD_SWEEP), "2 阶：蓄力、不当盾、照样横扫");
        helper.assertTrue(VerdictBladeItem.judgmentCooldown(2, VerdictBladeItem.Config.DEFAULTS) == 260
                && VerdictBladeItem.judgmentCooldown(5, VerdictBladeItem.Config.DEFAULTS) == 200, "冷却 300 − 20 / 阶");
        reset();
        helper.succeed();
    }

    /**
     * 冷却用原版物品冷却、不存盘（重登、死亡清零），两项配置的上限压在 1 200 刻；首领倍数 verdict.boss_multiplier 已经没有了（首领不补裁断）。
     */
    @GameTest(template = EMPTY)
    public static void judgmentCooldownConfigCapped(GameTestHelper helper) {
        for (String path : List.of("general.verdict_blade.judgment.cooldown", "general.verdict_blade.judgment.cooldown_per_stage")) {
            Object raw = ECConfig.spec().getSpec().get(path);
            helper.assertTrue(raw instanceof ForgeConfigSpec.ValueSpec, "配置项不在：" + path);
            ForgeConfigSpec.ValueSpec spec = (ForgeConfigSpec.ValueSpec) raw;
            helper.assertTrue(spec.test(VerdictBladeItem.MAX_COOLDOWN_TICKS) && !spec.test(VerdictBladeItem.MAX_COOLDOWN_TICKS + 1),
                    path + " 的上限应是 " + VerdictBladeItem.MAX_COOLDOWN_TICKS + "，实际 " + spec.getRange());
        }
        helper.assertTrue(VerdictBladeItem.MAX_COOLDOWN_TICKS == 1200, "上限 1 200 刻（60 秒）");
        helper.assertTrue(ECConfig.spec().getSpec().get("general.verdict_blade.verdict.boss_multiplier") == null, "没有首领倍数这一项");
        helper.succeed();
    }

    /** judgment.damage_multiplier 填 0：断罪不开（DISABLED），不打、不扣、不冷却；提示框写「断罪没有开启」 */
    @GameTest(template = EMPTY)
    public static void judgmentDisabledWhenNoDamage(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-off", new Vec3(2.5D, 1D, 2.5D));
        Husk husk = dummy(helper, EntityType.HUSK, new BlockPos(4, 1, 2), 200D);
        try {
            VerdictBladeItem.Config.setTestOverrides(judgmentValues(3D, 16, 0D));
            ItemStack stack = blade(2);
            wield(player, stack);
            Judgment result = VerdictBladeItem.judgment(player, stack);
            helper.assertTrue(result.outcome() == Outcome.DISABLED, "伤害倍数 0：断罪不开，实际 " + result);
            helper.assertTrue(husk.getHealth() == 200F && mru(stack) == VerdictBladeItem.MAX_MRU && !player.getCooldowns().isOnCooldown(stack.getItem()),
                    "不打、不扣、不冷却");
            List<String> keys = tooltipKeys(helper, stack);
            helper.assertTrue(keys.contains("tooltip.essentialcraft5.verdict_blade.judgment_off")
                    && !keys.contains("tooltip.essentialcraft5.verdict_blade.judgment"), "提示框写没有开启：" + keys);
        } finally {
            discard(husk);
            reset();
        }
        helper.succeed();
    }

    /**
     * 只打半径 6 内的敌对生物：起了名字的、村民、驯服的狼、7 格外的不打；每个挨当前攻击力；扣 20 000、冷却 260 刻（259 刻时还在）；
     * 冷却中再放是 COOLDOWN；打到的普通目标本刻末各补一下裁断。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void judgmentHitsOnlyEnemies(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-judge", new Vec3(5.5D, 1D, 5.5D));
        Zombie near = dummy(helper, EntityType.ZOMBIE, new BlockPos(5, 1, 7), 200D);
        Husk husk = dummy(helper, EntityType.HUSK, new BlockPos(8, 1, 5), 200D);
        Husk diagonal = dummy(helper, EntityType.HUSK, new BlockPos(1, 1, 9), 200D);
        Zombie named = dummy(helper, EntityType.ZOMBIE, new BlockPos(3, 1, 5), 200D);
        named.setCustomName(Component.literal("Bob"));
        Villager villager = dummy(helper, EntityType.VILLAGER, new BlockPos(5, 1, 3), 20D);
        Wolf wolf = dummy(helper, EntityType.WOLF, new BlockPos(7, 1, 7), 20D);
        wolf.tame(player);
        Zombie far = dummy(helper, EntityType.ZOMBIE, new BlockPos(0, 1, 0), 200D);
        try {
            ItemStack stack = blade(2);
            wield(player, stack);
            float attack = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            Judgment result = VerdictBladeItem.judgment(player, stack);
            helper.assertTrue(result.outcome() == Outcome.DONE && result.landed() == 3, "应打中 3 个，实际 " + result);
            for (LivingEntity hit : List.of(near, husk, diagonal)) {
                helper.assertTrue(Math.abs(200F - hit.getHealth() - attack) < EPS, hit.getType() + " 应挨 " + attack + "，实际 " + (200F - hit.getHealth()));
                DamageSource last = hit.getLastDamageSource();
                helper.assertTrue(last != null && last.getEntity() == player && last.getDirectEntity() == player, "按玩家攻击算：" + last);
            }
            helper.assertTrue(named.getHealth() == 200F && villager.getHealth() == 20F && wolf.getHealth() == 20F && far.getHealth() == 200F,
                    "起了名字的、村民、驯服的狼、7 格外的不打");
            helper.assertTrue(mru(stack) == VerdictBladeItem.MAX_MRU - 20_000, "扣 20 000，剩 " + mru(stack));
            helper.assertTrue(VerdictBladeItem.judgment(player, stack).outcome() == Outcome.COOLDOWN, "冷却中");

            int dealt = VerdictBladeItem.flush();
            helper.assertTrue(dealt == 3 && Math.abs(200F - near.getHealth() - attack - 8F) < EPS, "三个各补 8（2 阶 4%），实际 " + dealt + " 下");
            helper.assertTrue(mru(stack) == VerdictBladeItem.MAX_MRU - 20_000 - 600, "裁断 3 × 200，剩 " + mru(stack));

            for (int i = 0; i < 259; i++) {
                player.getCooldowns().tick();
            }
            helper.assertTrue(player.getCooldowns().isOnCooldown(stack.getItem()), "259 刻时还在冷却");
            player.getCooldowns().tick();
            helper.assertFalse(player.getCooldowns().isOnCooldown(stack.getItem()), "260 刻冷却完");
        } finally {
            discard(near, husk, diagonal, named, villager, wolf, far);
            reset();
        }
        helper.succeed();
    }

    /** 最多打 max_targets 个（近的先打）；范围里没有目标、MRU 不够时不打、不扣、不冷却 */
    @GameTest(template = EMPTY_LARGE)
    public static void judgmentLimitsAndRefusals(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-limit", new Vec3(5.5D, 1D, 5.5D));
        List<Husk> husks = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            husks.add(dummy(helper, EntityType.HUSK, new BlockPos(5 + i, 1, 5), 200D));
        }
        try {
            VerdictBladeItem.Config.setTestOverrides(judgmentValues(6D, 2));
            ItemStack stack = blade(2);
            wield(player, stack);
            Judgment result = VerdictBladeItem.judgment(player, stack);
            helper.assertTrue(result.outcome() == Outcome.DONE && result.landed() == 2, "最多 2 个，实际 " + result);
            helper.assertTrue(husks.get(0).getHealth() < 200F && husks.get(1).getHealth() < 200F
                    && husks.get(2).getHealth() == 200F && husks.get(3).getHealth() == 200F, "打最近的两个");
            VerdictBladeItem.flush();

            ServerPlayer lonely = fighter(helper, "e5-lonely", new Vec3(1.5D, 1D, 9.5D));
            VerdictBladeItem.Config.setTestOverrides(judgmentValues(2D, 16));
            ItemStack idle = blade(2);
            wield(lonely, idle);
            helper.assertTrue(VerdictBladeItem.judgment(lonely, idle).outcome() == Outcome.NO_TARGET, "范围里没有目标");
            helper.assertTrue(mru(idle) == VerdictBladeItem.MAX_MRU && !lonely.getCooldowns().isOnCooldown(idle.getItem()), "没目标：不扣、不冷却");

            VerdictBladeItem.Config.setTestOverrides(null);
            ServerPlayer poor = fighter(helper, "e5-poor", new Vec3(5.5D, 1D, 5.5D));
            ItemStack empty = blade(2);
            setMRU(empty, 19_999);
            wield(poor, empty);
            float before = husks.get(2).getHealth();
            helper.assertTrue(VerdictBladeItem.judgment(poor, empty).outcome() == Outcome.NO_MRU, "MRU 不够");
            helper.assertTrue(mru(empty) == 19_999 && husks.get(2).getHealth() == before && !poor.getCooldowns().isOnCooldown(empty.getItem()),
                    "MRU 不够：不打、不扣、不冷却");
        } finally {
            discard(husks.toArray(new LivingEntity[0]));
            reset();
        }
        helper.succeed();
    }

    /** 全被拦下（LivingAttackEvent 取消，领地 / 插件）：BLOCKED，不扣、不冷却 */
    @GameTest(template = EMPTY)
    public static void judgmentAllBlockedCostsNothing(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-blocked", new Vec3(2.5D, 1D, 2.5D));
        Husk a = dummy(helper, EntityType.HUSK, new BlockPos(0, 1, 2), 200D);
        Husk b = dummy(helper, EntityType.HUSK, new BlockPos(4, 1, 2), 200D);
        Runnable unlisten = listen(EventPriority.NORMAL, LivingAttackEvent.class, event -> {
            if (event.getEntity() == a || event.getEntity() == b) {
                event.setCanceled(true);
            }
        });
        try {
            VerdictBladeItem.Config.setTestOverrides(judgmentValues(3D, 16));
            ItemStack stack = blade(2);
            wield(player, stack);
            Judgment result = VerdictBladeItem.judgment(player, stack);
            helper.assertTrue(result.outcome() == Outcome.BLOCKED, "全被拦下，实际 " + result);
            helper.assertTrue(mru(stack) == VerdictBladeItem.MAX_MRU && !player.getCooldowns().isOnCooldown(stack.getItem()), "不扣、不冷却");
            helper.assertTrue(a.getHealth() == 200F && b.getHealth() == 200F && VerdictBladeItem.pendingCount() == 0, "没挨打、没有待补的");
        } finally {
            unlisten.run();
            discard(a, b);
            reset();
        }
        helper.succeed();
    }

    /**
     * 首领：断罪照打、不补裁断（只掉攻击力那么多，本刻末只扣断罪的 20 000）；5 阶起挂二分 40 刻（4 阶不挂）；
     * 首领的无敌帧不清（还在无敌里的打不动 → BLOCKED）；起了名字的首领照打；divider_immune 里的（全息投影）挂不上二分。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void judgmentBossesAndBisect(GameTestHelper helper) {
        Zombie boss4 = dummy(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1), 200D);
        Zombie boss5 = dummy(helper, EntityType.ZOMBIE, new BlockPos(9, 1, 1), 200D);
        Zombie guarded = dummy(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 9), 200D);
        Zombie named = dummy(helper, EntityType.ZOMBIE, new BlockPos(9, 1, 9), 200D);
        named.setCustomName(Component.literal("Bob"));
        try {
            VerdictBladeItem.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            VerdictBladeItem.Config.setTestOverrides(judgmentValues(2D, 16));

            ServerPlayer p4 = fighter(helper, "e5-boss4", new Vec3(2.5D, 1D, 1.5D));
            ItemStack stage4 = blade(4);
            wield(p4, stage4);
            float attack4 = (float) p4.getAttributeValue(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(VerdictBladeItem.judgment(p4, stage4).outcome() == Outcome.DONE, "4 阶打首领");
            helper.assertTrue(Math.abs(200F - boss4.getHealth() - attack4) < EPS,
                    "只掉攻击力那么多、不补裁断，实际 " + (200F - boss4.getHealth()) + "（攻击 " + attack4 + "）");
            helper.assertTrue(!boss4.hasEffect(ECEffects.BISECTED.get()), "4 阶不挂二分");
            helper.assertTrue(VerdictBladeItem.pendingCount() == 0 && VerdictBladeItem.flush() == 0, "首领没有待补的");
            helper.assertTrue(mru(stage4) == VerdictBladeItem.MAX_MRU - 20_000, "只扣断罪 20 000，剩 " + mru(stage4));

            ServerPlayer p5 = fighter(helper, "e5-boss5", new Vec3(8.5D, 1D, 1.5D));
            ItemStack stage5 = blade(5);
            wield(p5, stage5);
            helper.assertTrue(VerdictBladeItem.judgment(p5, stage5).outcome() == Outcome.DONE, "5 阶打首领");
            helper.assertTrue(duration(boss5, ECEffects.BISECTED.get()) == 40, "5 阶挂二分 40 刻，实际 " + duration(boss5, ECEffects.BISECTED.get()));
            VerdictBladeItem.flush();

            ServerPlayer pg = fighter(helper, "e5-guard", new Vec3(2.5D, 1D, 9.5D));
            guarded.hurt(helper.getLevel().damageSources().generic(), 100F);
            helper.assertTrue(guarded.invulnerableTime == 20, "前提：首领刚挨过 100");
            ItemStack stageG = blade(2);
            wield(pg, stageG);
            helper.assertTrue(VerdictBladeItem.judgment(pg, stageG).outcome() == Outcome.BLOCKED && mru(stageG) == VerdictBladeItem.MAX_MRU,
                    "首领的无敌帧不清：打不动、不扣");

            ServerPlayer pn = fighter(helper, "e5-named", new Vec3(8.5D, 1D, 9.5D));
            ItemStack stageN = blade(2);
            wield(pn, stageN);
            helper.assertTrue(!VerdictBladeItem.judgmentSpared(named) && VerdictBladeItem.judgment(pn, stageN).outcome() == Outcome.DONE
                    && named.getHealth() < 200F, "起了名字的首领照打");
            VerdictBladeItem.setTestBossOverride(null);
            helper.assertTrue(VerdictBladeItem.judgmentSpared(named), "对照：不是首领的、起了名字的不打");

            helper.assertTrue(VerdictBladeItem.bisectImmune(ECHologram.HOLOGRAM.get()) && !VerdictBladeItem.bisectImmune(EntityType.ZOMBIE),
                    "全息投影在 divider_immune 里，挂不上二分");
        } finally {
            discard(boss4, boss5, guarded, named);
            reset();
        }
        helper.succeed();
    }

    /**
     * 原版使用流程：按住不到 20 刻松开什么都不做；中途剑的 NBT 变了（扣电、盖阶戳）照样算同一次使用；够 20 刻松开放出；副手拿着不放。
     */
    @GameTest(template = EMPTY)
    public static void judgmentReleaseNeedsCharge(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "e5-release", new Vec3(2.5D, 1D, 2.5D));
        Husk husk = dummy(helper, EntityType.HUSK, new BlockPos(4, 1, 2), 200D);
        try {
            VerdictBladeItem.Config.setTestOverrides(judgmentValues(3D, 16));
            ItemStack stack = blade(2);
            wield(player, stack);
            player.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND, "开始蓄力");

            ItemStack changed = stack.copy();
            setMRU(changed, 45_000);
            Attunement.stampRank(changed, 5);
            player.setItemInHand(InteractionHand.MAIN_HAND, changed);
            helper.assertTrue(ForgeHooks.canContinueUsing(player.getUseItem(), changed), "NBT 变了照样是同一次使用");
            helper.assertFalse(ForgeHooks.canContinueUsing(player.getUseItem(), emberSword(data(Element.FIRE, Element.FIRE, EmberKind.COMMON, EmberKind.FLAME), 2)),
                    "换成别的物品才算中断");

            int duration = changed.getUseDuration();
            changed.releaseUsing(helper.getLevel(), player, duration - 19);
            helper.assertTrue(husk.getHealth() == 200F && mru(changed) == 45_000, "19 刻松开：不放");
            changed.releaseUsing(helper.getLevel(), player, duration - 20);
            helper.assertTrue(husk.getHealth() < 200F && mru(changed) == 25_000 && player.getCooldowns().isOnCooldown(changed.getItem()),
                    "20 刻松开：放出、扣 20 000、进冷却，实际剩 " + mru(changed));
            player.stopUsingItem();
            VerdictBladeItem.flush();

            ServerPlayer left = fighter(helper, "e5-left", new Vec3(2.5D, 1D, 2.5D));
            ItemStack off = blade(2);
            left.setItemInHand(InteractionHand.OFF_HAND, off);
            left.startUsingItem(InteractionHand.OFF_HAND);
            float before = husk.getHealth();
            off.releaseUsing(helper.getLevel(), left, off.getUseDuration() - 40);
            helper.assertTrue(husk.getHealth() == before && mru(off) == VerdictBladeItem.MAX_MRU, "副手拿着不放");
            left.stopUsingItem();
        } finally {
            discard(husk);
            reset();
        }
        helper.succeed();
    }

    /** 提示框：0~1 阶写「2 阶解锁」和格挡；2 阶写断罪；5 阶加二分；裁断写不打玩家。2 阶攒满的余烬剑提示能升格、要几阶矩阵 */
    @GameTest(template = EMPTY)
    public static void verdictBladeTooltip(GameTestHelper helper) {
        List<String> stage1 = tooltipKeys(helper, blade(1));
        helper.assertTrue(stage1.contains("tooltip.essentialcraft5.verdict_blade.attack") && stage1.contains("tooltip.essentialcraft5.verdict_blade.verdict")
                && stage1.contains("tooltip.essentialcraft5.verdict_blade.verdict_no_pvp")
                && stage1.contains("tooltip.essentialcraft5.verdict_blade.judgment_locked")
                && stage1.contains("tooltip.essentialcraft5.forged_elemental_sword.block"), "1 阶的提示：" + stage1);
        List<String> stage2 = tooltipKeys(helper, blade(2));
        helper.assertTrue(stage2.contains("tooltip.essentialcraft5.verdict_blade.judgment") && !stage2.contains("tooltip.essentialcraft5.verdict_blade.judgment_bisect")
                && !stage2.contains("tooltip.essentialcraft5.forged_elemental_sword.block"), "2 阶的提示：" + stage2);
        List<String> stage5 = tooltipKeys(helper, blade(5));
        helper.assertTrue(stage5.contains("tooltip.essentialcraft5.verdict_blade.judgment_bisect"), "5 阶的提示：" + stage5);

        ItemStack sword = emberSword(data(Element.FIRE, Element.FIRE, EmberKind.COMMON, EmberKind.FLAME), 2);
        List<String> swordKeys = tooltipKeys(helper, sword);
        String expected = AttunementConfig.requiredRank(3) > 0 ? "tooltip.essentialcraft5.forged_elemental_sword.promotable_rank"
                : "tooltip.essentialcraft5.forged_elemental_sword.promotable";
        helper.assertTrue(swordKeys.contains(expected), "2 阶余烬剑应提示能升格：" + swordKeys);
        helper.assertFalse(tooltipKeys(helper, emberSword(data(Element.FIRE, Element.FIRE, EmberKind.COMMON, EmberKind.FLAME), 1)).contains(expected),
                "1 阶不提示升格");
        AttunementConfig.setTestOverrides(null, List.of(ECAttunement.EMBER_SWORD.id()));
        try {
            List<String> off = tooltipKeys(helper, sword);
            helper.assertTrue(!off.contains("tooltip.essentialcraft5.forged_elemental_sword.promotable_rank")
                    && !off.contains("tooltip.essentialcraft5.forged_elemental_sword.promotable"), "余烬剑家族关掉时升格配方不认，也不提示：" + off);
        } finally {
            AttunementConfig.clearTestOverrides();
        }
        List<String> verdict = tooltipKeys(helper, blade(2));
        helper.assertTrue(verdict.contains("tooltip.essentialcraft5.verdict_blade.verdict"), "裁断那一行：" + verdict);
        helper.succeed();
    }

    private static List<String> tooltipKeys(GameTestHelper helper, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, helper.getLevel(), lines, TooltipFlag.NORMAL);
        List<String> keys = new ArrayList<>();
        for (Component line : lines) {
            keys.add(key(line));
        }
        return keys;
    }
}
