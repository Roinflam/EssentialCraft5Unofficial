package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorEvents;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorItem;
import com.flameforge.essentialcraft5.item.VerdictBladeItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.item.ember.InlayEvents;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 审判庭套装的「审判庭之律」与同调阶（第十三批 13b，batch13.md §4.3、§4.4、D14、D35 A；实现 {@code item/InquisitoriumArmorEvents}）的 GameTest：
 * <ul>
 *   <li>抗魔：有攻击者的魔法 ×0.65（带不带 bypasses_armor 都算），没有攻击者、攻击者是自己、无视无敌、不是魔法的都不减；1 阶 ×0.6；混穿不算；填 0 不减。</li>
 *   <li>首领减伤 0 阶 15%，和抗魔相加进减伤池；4 阶无视护甲（非魔法）×0.7，魔法不叠这一条。抗魔、首领减伤、律令按阶的数读
 *       {@code general.gear_growth.inquisitorium_armor.*}，填 0 那几条用 {@link #withGrowth} 盖。</li>
 *   <li>倍率顺序：减伤在护甲前，壁垒在护甲后、按减过的数算门槛以上那段。</li>
 *   <li>壁垒：3 阶超过 30% 的部分减半、4 阶 25% 起只受四成；没有攻击者的不管；填 0 不减；按扣黄心之前的整下算，减掉的扣不完退回黄心。</li>
 *   <li>律令：每 20 刻削 13 刻（约 ×0.6），有益、无限时长、unfilterable 的不动；没穿齐、填 0 不削；真玩家每刻走一遍也是 20 刻削一次；
 *       被同种更强效果盖住的那一层不缩。</li>
 *   <li>定罪：伤害真的落下才记（只有 LivingHurtEvent、被取消、伤害 0 都不记，反伤不记；黄心整下吃掉的算落下）；200 刻到期；每人 8 个、满了挤掉最快到期的；
 *       +20%、2 阶 +30%、首领 +40%，只加亲手打的（反伤、饰品、尖刺圆盾、裁断不加）；对玩家 × pvp_effect_scale（填 0 就不加）；时长 / 上限 / 增伤填 0 都不起作用。</li>
 *   <li>赦罪：1 阶亲手打死被定罪的目标清掉一个有害效果（有限时长的先清、最后才清无限时长的，unfilterable 不清），冷却 200 刻、没清掉不进冷却；
 *       0 阶、没定罪、饰品 / 反伤打死、死亡事件被取消都不清。</li>
 *   <li>装备 NBT 变化（同调写盘）触发的换装事件不算脱装：生命不夹、定罪表还在、全套还认。</li>
 *   <li>四条升阶配方（§4.5，霜髓 13d 前兜底蓝冰 ×4）与提示行。</li>
 * </ul>
 * 挨打的一方多用 mock 玩家（不 tick、穿上的盔甲属性不自动加，最大生命 20）；换装那条用 {@link ProtectionFrameworkTests#realPlayer}。
 * 怪物打来的伤害按难度缩放，{@link #rawFor} 反推。时间用 {@link InquisitoriumArmorEvents#setTestNow} 钉住，设、测、置回都在同一段同步代码里。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class InquisitoriumLawTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1.0E-4D;
    private static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
    private static final long T0 = 1_000_000L;

    private InquisitoriumLawTests() {
    }

    // ================================================================ 工具

    private static ItemStack piece(ArmorItem.Type type, int stage) {
        ItemStack stack = new ItemStack(ECInquisitorium.all().get(type.ordinal()).get());
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        return stack;
    }

    /** 穿四件 stage 阶（只放进槽，不加属性） */
    private static ItemStack[] suit(LivingEntity entity, int stage) {
        ItemStack[] stacks = new ItemStack[4];
        for (ArmorItem.Type type : TYPES) {
            stacks[type.ordinal()] = piece(type, stage);
            entity.setItemSlot(type.getSlot(), stacks[type.ordinal()]);
        }
        return stacks;
    }

    private static Player mock(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static Zombie zombie(GameTestHelper helper, int x, int z) {
        return helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(x, 1, z));
    }

    private static DamageSource source(GameTestHelper helper, ResourceKey<DamageType> type, Entity attacker) {
        return new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
    }

    /** Player.hurt 按难度缩放怪物打来的伤害：要让事件里看到 wanted，传进去的数 */
    private static float rawFor(GameTestHelper helper, float wanted) {
        Difficulty difficulty = helper.getLevel().getDifficulty();
        helper.assertTrue(difficulty != Difficulty.PEACEFUL, "测试前提：不是和平难度（和平时怪物打玩家是 0）");
        return switch (difficulty) {
            case EASY -> wanted <= 2F ? wanted : (wanted - 1F) * 2F;
            case HARD -> wanted * 2F / 3F;
            default -> wanted;
        };
    }

    private static void close(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPS, what + "：应 " + expected + "，实际 " + actual);
    }

    private static ECInquisitorium.Config.Law law(boolean keep, double magic, double boss, double edict, double bonus, int ticks, int max) {
        return new ECInquisitorium.Config.Law(keep, magic, boss, edict, bonus, ticks, max);
    }

    private static void withLaw(ECInquisitorium.Config.Law law, Runnable body) {
        ECInquisitorium.Config.setLawTestOverrides(law);
        try {
            body.run();
        } finally {
            ECInquisitorium.Config.setLawTestOverrides(null);
        }
    }

    /**
     * 抗魔 / 首领减伤 / 律令改由 {@code general.gear_growth.inquisitorium_armor.*} 按阶的列表管（{@code law.*} 的旧键不再读）：
     * 给了数的那几项盖成「全阶都是这个数」，null 的不动；设、测、清都在同一段同步代码里
     */
    private static void withGrowth(Double magic, Double boss, Double edict, Runnable body) {
        if (magic != null) {
            GearGrowthConfig.setTestOverride("inquisitorium_armor.magic_resistance", List.of(magic));
        }
        if (boss != null) {
            GearGrowthConfig.setTestOverride("inquisitorium_armor.boss_resistance", List.of(boss));
        }
        if (edict != null) {
            GearGrowthConfig.setTestOverride("inquisitorium_armor.edict_reduction", List.of(edict));
        }
        try {
            body.run();
        } finally {
            GearGrowthConfig.clearTestOverrides();
        }
    }

    private static void withNow(long tick, Runnable body) {
        InquisitoriumArmorEvents.setTestNow(tick);
        try {
            body.run();
        } finally {
            InquisitoriumArmorEvents.setTestNow(null);
        }
    }

    /** 某个实体最后一次 LivingDamageEvent 的数（LOWEST、收已取消的：所有人改完以后） */
    private static final class FinalDamage implements Consumer<LivingDamageEvent> {
        private final LivingEntity target;
        float last = -1F;

        FinalDamage(LivingEntity target) {
            this.target = target;
        }

        @Override
        public void accept(LivingDamageEvent event) {
            if (event.getEntity() == target) {
                last = event.getAmount();
            }
        }
    }

    /** 某个实体最后一次 LivingHurtEvent 的数（LOWEST） */
    private static final class FinalHurt implements Consumer<LivingHurtEvent> {
        private final LivingEntity target;
        float last = -1F;

        FinalHurt(LivingEntity target) {
            this.target = target;
        }

        @Override
        public void accept(LivingHurtEvent event) {
            if (event.getEntity() == target) {
                last = event.getAmount();
            }
        }
    }

    private static float hurtAndRead(LivingEntity victim, DamageSource source, float raw) {
        FinalDamage probe = new FinalDamage(victim);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDamageEvent.class, probe);
        try {
            victim.invulnerableTime = 0;
            victim.hurt(source, raw);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        return probe.last;
    }

    private static int duration(LivingEntity entity, MobEffect effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -2 : instance.getDuration();
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
        if (item == null) {
            throw new IllegalStateException("没有物品 " + path);
        }
        return item;
    }

    // ================================================================ 默认值

    /** 配置默认值照 batch13.md §4.3 / §4.4；家族最高 4 阶；律令每 20 刻削 13 刻 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumLawDefaults(GameTestHelper helper) {
        ECInquisitorium.Config.Law law = ECInquisitorium.Config.LAW_DEFAULTS;
        helper.assertTrue(law.keepDurability() && law.magicResistance() == 0.35D && law.bossResistance() == 0.15D && law.edictReduction() == 0.4D
                && law.convictionBonus() == 0.2D && law.convictionTicks() == 200 && law.convictionMaxTargets() == 8, "0 阶默认值不对：" + law);
        ECInquisitorium.Config.Attuned a = ECInquisitorium.Config.ATTUNED_DEFAULTS;
        helper.assertTrue(a.magicResistance() == 0.45D && a.absolutionCooldown() == 200 && a.convictionBonus() == 0.3D && a.convictionBossBonus() == 0.1D
                && a.bulwarkThreshold() == 0.3D && a.bulwarkReduction() == 0.5D && a.piercingResistance() == 0.3D && a.bulwarkThresholdTop() == 0.25D
                && a.bulwarkReductionTop() == 0.6D, "同调阶默认值不对：" + a);
        helper.assertTrue(ECAttunement.INQUISITORIUM_ARMOR.maxStage() == 4, "审判庭家族最高 4 阶");
        helper.assertTrue(InquisitoriumArmorEvents.edictCut(0.4D) == 13 && InquisitoriumArmorEvents.edictCut(0D) == 0
                && InquisitoriumArmorEvents.edictCut(0.5D) == 20 && InquisitoriumArmorEvents.edictCut(-1D) == 0, "律令每次削的刻数");
        close(helper, 20D / (20D + InquisitoriumArmorEvents.edictCut(0.4D)), 20D / 33D, "默认律令的实际时长倍数约 0.606");
        helper.succeed();
    }

    // ================================================================ 抗魔、首领、无视护甲

    /** 抗魔：有攻击者的魔法 ×0.65（带 bypasses_armor 的间接魔法、不带的荆棘都算）；没有攻击者、自己、无视无敌、物理不减；1 阶 ×0.6；混穿、填 0 不减 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumMagicResistance(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = zombie(helper, 3, 1);
        suit(victim, 0);
        DamageSource spell = source(helper, DamageTypes.INDIRECT_MAGIC, zombie);
        DamageSource thorns = source(helper, DamageTypes.THORNS, zombie);
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, spell), 0.65D, "间接魔法（无视护甲）");
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, thorns), 0.65D, "荆棘（魔法、不无视护甲）");
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, victim.damageSources().magic()), 1D, "没有攻击者的 magic()（腐化自伤）不减");
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, source(helper, DamageTypes.INDIRECT_MAGIC, victim)), 1D, "攻击者是自己不减");
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, victim.damageSources().mobAttack(zombie)), 1D, "物理不减");
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, source(helper, DamageTypes.GENERIC_KILL, zombie)), 1D, "无视无敌的不减");

        float before = victim.getHealth();
        hurtAndRead(victim, spell, rawFor(helper, 10F));
        close(helper, before - victim.getHealth(), 6.5D, "真挨一下 10 点间接魔法只掉 6.5");

        suit(victim, 1);
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, spell), 0.55D, "四件 1 阶抗魔 45%");
        victim.setItemSlot(EquipmentSlot.FEET, piece(ArmorItem.Type.BOOTS, 0));
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, spell), 0.65D, "按最低一件算：有一件 0 阶就是 35%");
        victim.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ECComputer.armor().get(1).get()));
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, spell), 1D, "混穿宅男胸甲没有全套效果");
        suit(victim, 0);
        withGrowth(0D, 0D, null,
                () -> close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, spell), 1D, "magic_resistance 填 0 不减"));
        helper.succeed();
    }

    /** 首领减伤 0 阶 15%，和抗魔相加进减伤池：首领的魔法 1 − (35% + 15%) = 0.5；首领填 0 只剩抗魔 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumBossReductionMultiplies(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        suit(victim, 0);
        WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
        helper.assertTrue(wither != null, "测试前提：造得出凋灵");
        if (wither == null) {
            return;
        }
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, victim.damageSources().mobAttack(wither)), 0.85D, "首领近战");
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, source(helper, DamageTypes.INDIRECT_MAGIC, wither)), 0.5D,
                "首领的魔法：两条相加（35% + 15%，进总闸的减伤池）");
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, victim.damageSources().mobAttack(zombie(helper, 3, 1))), 1D, "普通怪近战不减");
        withGrowth(null, 0D, null,
                () -> close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, source(helper, DamageTypes.INDIRECT_MAGIC, wither)), 0.65D,
                        "boss_resistance 填 0 只剩抗魔"));
        helper.succeed();
    }

    /** 4 阶：有攻击者的无视护甲、非魔法 ×0.7；3 阶不减、没有攻击者不减；魔法走抗魔、不再叠这一条 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumPiercingStageFour(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = zombie(helper, 3, 1);
        DamageSource crush = source(helper, DamageTypes.CRAMMING, zombie);
        suit(victim, 3);
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, crush), 1D, "3 阶不减无视护甲");
        suit(victim, 4);
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, crush), 0.7D, "4 阶无视护甲 ×0.7");
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, victim.damageSources().cramming()), 1D, "没有攻击者的挤压不减");
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, source(helper, DamageTypes.INDIRECT_MAGIC, zombie)), 0.45D,
                "魔法走抗魔（4 阶 55%），不再乘 0.7");
        close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, victim.damageSources().mobAttack(zombie)), 1D, "普通近战不减");
        helper.succeed();
    }

    /** 壁垒：3 阶护甲后超过最大生命 30% 的部分减半；4 阶 25% 起只受四成；门槛以下、2 阶、没有攻击者、填 0 都照原样 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumBulwark(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = zombie(helper, 3, 1);
        DamageSource melee = victim.damageSources().mobAttack(zombie);
        helper.assertTrue(victim.getMaxHealth() == 20F, "测试前提：最大生命 20");
        suit(victim, 2);
        close(helper, InquisitoriumArmorEvents.bulwark(victim, melee, 10F), 10D, "2 阶没有壁垒");
        suit(victim, 3);
        close(helper, InquisitoriumArmorEvents.bulwark(victim, melee, 10F), 8D, "3 阶：6 + 4 × 0.5");
        close(helper, InquisitoriumArmorEvents.bulwark(victim, melee, 5F), 5D, "门槛以下不动");
        close(helper, InquisitoriumArmorEvents.bulwark(victim, victim.damageSources().fall(), 10F), 10D, "没有攻击者的（摔落）不管");
        close(helper, InquisitoriumArmorEvents.bulwark(victim, source(helper, DamageTypes.GENERIC_KILL, zombie), 10F), 10D, "无视无敌的不管");
        suit(victim, 4);
        close(helper, InquisitoriumArmorEvents.bulwark(victim, melee, 10F), 7D, "4 阶：5 + 5 × 0.4");
        ECInquisitorium.Config.Attuned a = ECInquisitorium.Config.ATTUNED_DEFAULTS;
        ECInquisitorium.Config.setAttunedTestOverrides(new ECInquisitorium.Config.Attuned(a.magicResistance(), a.absolutionCooldown(), a.convictionBonus(),
                a.convictionBossBonus(), a.bulwarkThreshold(), a.bulwarkReduction(), a.piercingResistance(), 0D, 0D));
        try {
            close(helper, InquisitoriumArmorEvents.bulwark(victim, melee, 10F), 10D, "超出部分减掉的比例填 0：不减");
        } finally {
            ECInquisitorium.Config.setAttunedTestOverrides(null);
        }
        helper.succeed();
    }

    /**
     * 倍率顺序：首领的间接魔法 40（护甲前）在 LivingHurtEvent 进减伤池，4 阶抗魔 55% + 首领减伤 25% 相加 = 80%，夹到有攻击者时的池上限 60% → 16，
     * 无视护甲，到 LivingDamageEvent 时 4 阶壁垒按 16 算：5 + 11 × 0.4 = 9.4。近战走护甲：3 阶、护甲属性加上以后，最终伤害 = 壁垒(护甲公式(105))。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumMultiplierOrder(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        suit(victim, 4);
        WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
        helper.assertTrue(wither != null, "测试前提：造得出凋灵");
        if (wither == null) {
            return;
        }
        float dealt = hurtAndRead(victim, source(helper, DamageTypes.INDIRECT_MAGIC, wither), rawFor(helper, 40F));
        close(helper, dealt, 5D + (40D * (1D - Math.min(0.55D + 0.25D, 0.6D)) - 5D) * 0.4D, "减伤池在护甲前、壁垒在后");

        Player armored = mock(helper, 3.5D, 3.5D);
        ItemStack[] stacks = suit(armored, 3);
        for (ArmorItem.Type type : TYPES) {
            armored.getAttributes().addTransientAttributeModifiers(stacks[type.ordinal()].getAttributeModifiers(type.getSlot()));
        }
        armored.setHealth(armored.getMaxHealth());
        Zombie zombie = zombie(helper, 0, 4);
        float armor = armored.getArmorValue();
        float toughness = (float) armored.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float afterArmor = CombatRules.getDamageAfterAbsorb(105F, armor, toughness);
        double limit = armored.getMaxHealth() * 0.3D;
        helper.assertTrue(afterArmor > limit, "测试前提：护甲后 " + afterArmor + " 要超过门槛 " + limit);
        float melee = hurtAndRead(armored, armored.damageSources().mobAttack(zombie), rawFor(helper, 105F));
        close(helper, melee, limit + (afterArmor - limit) * 0.5D, "近战：先过护甲，再按护甲后的数算壁垒");
        helper.succeed();
    }

    /**
     * 壁垒按扣黄心之前的整下算（batch13.md §4.4、手册的例子）：100 生命、3 阶、护甲后挨 60。
     * 没有黄心掉 45；20 点黄心时黄心先扣 20、红心再掉 25，合计还是 45（不是黄心扣满再只管落到红心的 40）；
     * 50 点黄心时红心不掉、减掉的 15 有 5 退回黄心（剩 5）。2 阶没有壁垒，黄心照原样扣。纯函数 applyBulwark 同样核一遍。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumBulwarkCountsAbsorption(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = zombie(helper, 3, 1);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        suit(victim, 3);
        helper.assertTrue(victim.getArmorValue() == 0, "测试前提：mock 玩家穿上的盔甲不加护甲属性，护甲后 = 护甲前");
        try {
            float[] pure = InquisitoriumArmorEvents.applyBulwark(victim, victim.damageSources().mobAttack(zombie), 40F, 20F);
            close(helper, pure[0], 25D, "纯函数：事件里 40 + 黄心吃掉 20 = 整下 60 → 共受 45，红心 25");
            close(helper, pure[1], 0D, "纯函数：红心够扣，不退黄心");
            pure = InquisitoriumArmorEvents.applyBulwark(victim, victim.damageSources().mobAttack(zombie), 10F, 50F);
            close(helper, pure[0], 0D, "纯函数：整下 60 减 15，红心那 10 全免");
            close(helper, pure[1], 5D, "纯函数：剩下的 5 退回黄心");

            Object[][] cases = {{0F, 55F, 0F}, {20F, 75F, 0F}, {50F, 100F, 5F}};
            for (Object[] row : cases) {
                float absorption = (float) row[0];
                victim.setHealth(100F);
                victim.setAbsorptionAmount(absorption);
                hurtAndRead(victim, victim.damageSources().mobAttack(zombie), rawFor(helper, 60F));
                close(helper, victim.getHealth(), (float) row[1], "黄心 " + absorption + " 挨 60：生命");
                close(helper, victim.getAbsorptionAmount(), (float) row[2], "黄心 " + absorption + " 挨 60：剩下的黄心");
                close(helper, (100F + absorption) - (victim.getHealth() + victim.getAbsorptionAmount()), 45D, "黄心 " + absorption + "：合计受 45");
            }

            suit(victim, 2);
            victim.setHealth(100F);
            victim.setAbsorptionAmount(20F);
            hurtAndRead(victim, victim.damageSources().mobAttack(zombie), rawFor(helper, 60F));
            close(helper, victim.getHealth(), 60D, "2 阶没有壁垒：黄心 20 扣完、红心掉 40");
            close(helper, victim.getAbsorptionAmount(), 0D, "2 阶黄心不退");
        } finally {
            InquisitoriumArmorEvents.forgetForTest(victim);
        }
        helper.succeed();
    }

    /**
     * 定罪认黄心：0 阶、身上 4 点黄心，僵尸打 3（事件里是 0）照样定罪；壁垒退回黄心后（50 点黄心挨 60）也算打实。
     * 只发 LivingDamageEvent(0) 没有 LivingHurtEvent 记录的（盾牌挡掉之类）照旧不记。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumConvictionCountsAbsorption(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        Zombie zombie = zombie(helper, 3, 1);
        Zombie other = zombie(helper, 3, 3);
        suit(player, 0);
        try {
            player.setAbsorptionAmount(4F);
            float dealt = hurtAndRead(player, player.damageSources().mobAttack(zombie), rawFor(helper, 3F));
            close(helper, dealt, 0D, "测试前提：黄心整下吃掉，LivingDamageEvent 里是 0");
            close(helper, player.getAbsorptionAmount(), 1D, "黄心 4 → 1");
            helper.assertTrue(InquisitoriumArmorEvents.isConvicted(player, zombie), "黄心整下吃掉的一下也算打实，定罪");

            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, player.damageSources().mobAttack(other), 0F));
            helper.assertFalse(InquisitoriumArmorEvents.isConvicted(player, other), "没有伤害吸收记录、伤害 0 的不记");

            InquisitoriumArmorEvents.forgetForTest(player);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            player.setHealth(100F);
            suit(player, 3);
            player.setAbsorptionAmount(50F);
            hurtAndRead(player, player.damageSources().mobAttack(other), rawFor(helper, 60F));
            close(helper, player.getAbsorptionAmount(), 5D, "测试前提：壁垒退回 5 点黄心");
            helper.assertTrue(InquisitoriumArmorEvents.isConvicted(player, other), "壁垒退黄心以后红心没掉，黄心少了 45，照样定罪");
        } finally {
            InquisitoriumArmorEvents.forgetForTest(player);
        }
        helper.succeed();
    }

    // ================================================================ 律令

    /** 律令：穿全套时有害效果每次削 13 刻；有益、无限时长、unfilterable（锚定）不动；没穿齐、edict_reduction 填 0 不削 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumEdict(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        suit(player, 0);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 5));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, MobEffectInstance.INFINITE_DURATION));
        player.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), 200));
        helper.assertTrue(InquisitoriumArmorEvents.edict(player) == 2, "削了中毒和凋零两个");
        helper.assertTrue(duration(player, MobEffects.POISON) == 187, "中毒 200 → 187，实际 " + duration(player, MobEffects.POISON));
        helper.assertTrue(duration(player, MobEffects.WITHER) == 1, "只剩 5 刻的凋零留 1 刻让它自己到期，实际 " + duration(player, MobEffects.WITHER));
        helper.assertTrue(duration(player, MobEffects.MOVEMENT_SPEED) == 200, "有益效果不动");
        helper.assertTrue(player.getEffect(MobEffects.WEAKNESS).isInfiniteDuration(), "无限时长不动");
        helper.assertTrue(duration(player, ECEffects.ANCHORED.get()) == 200, "unfilterable 里的锚定不动");

        withGrowth(null, null, 0D,
                () -> helper.assertTrue(InquisitoriumArmorEvents.edict(player) == 0 && duration(player, MobEffects.POISON) == 187,
                        "edict_reduction 填 0 不削"));
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        helper.assertTrue(InquisitoriumArmorEvents.edict(player) == 0 && duration(player, MobEffects.POISON) == 187, "没穿齐不削");
        helper.succeed();
    }

    // ================================================================ 定罪

    /** 打实你才记、200 刻到期、你打它 +20%（真打一下看得到）、没定罪的不加、反伤不加；8 个上限挤掉最快到期的；再挨一下刷新到期 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumConviction(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        suit(player, 0);
        List<Zombie> zombies = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            zombies.add(zombie(helper, i % 5, i < 5 ? 0 : 4));
        }
        Zombie first = zombies.get(0);
        try {
            withNow(T0, () -> {
                MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, player.damageSources().mobAttack(first), 3F));
                helper.assertTrue(InquisitoriumArmorEvents.isConvicted(player, first), "打实一下就定罪");
                DamageSource hit = player.damageSources().playerAttack(player);
                close(helper, InquisitoriumArmorEvents.convictionBonus(player, first, hit), 0.2D, "打被定罪的 +20%");
                close(helper, InquisitoriumArmorEvents.convictionBonus(player, zombies.get(1), player.damageSources().playerAttack(player)), 0D,
                        "没定罪的不加");
                close(helper, InquisitoriumArmorEvents.convictionBonus(player, first, source(helper, DamageTypes.THORNS, player)), 0D, "反伤不加");

                FinalHurt probe = new FinalHurt(first);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingHurtEvent.class, probe);
                try {
                    first.invulnerableTime = 0;
                    first.hurt(hit, 5F);
                } finally {
                    MinecraftForge.EVENT_BUS.unregister(probe);
                }
                close(helper, probe.last, 6D, "真打一下 5 点到护甲前是 6");
            });
            withNow(T0 + 199, () -> helper.assertTrue(InquisitoriumArmorEvents.isConvicted(player, first), "第 199 刻还在"));
            withNow(T0 + 200, () -> helper.assertFalse(InquisitoriumArmorEvents.isConvicted(player, first), "200 刻到期"));

            InquisitoriumArmorEvents.forgetForTest(player);
            for (int i = 0; i < 9; i++) {
                Zombie zombie = zombies.get(i);
                withNow(T0 + i, () -> helper.assertTrue(InquisitoriumArmorEvents.convict(player, zombie), "新记上的返回 true"));
            }
            withNow(T0 + 9, () -> {
                helper.assertTrue(InquisitoriumArmorEvents.convictedCount(player) == 8, "每人最多 8 个，实际 " + InquisitoriumArmorEvents.convictedCount(player));
                helper.assertFalse(InquisitoriumArmorEvents.isConvicted(player, first), "满了挤掉最快到期的（第一个）");
                helper.assertTrue(InquisitoriumArmorEvents.isConvicted(player, zombies.get(8)), "最新的在");
                helper.assertFalse(InquisitoriumArmorEvents.convict(player, zombies.get(8)), "已经在表里的只刷新");
            });
            withNow(T0 + 205, () -> {
                helper.assertTrue(InquisitoriumArmorEvents.isConvicted(player, zombies.get(8)), "刷新以后从第 9 刻重新算 200 刻");
                helper.assertFalse(InquisitoriumArmorEvents.isConvicted(player, zombies.get(1)), "没刷新的到期了");
            });
        } finally {
            InquisitoriumArmorEvents.forgetForTest(player);
        }
        helper.succeed();
    }

    /** 混合端先算后扣：只有 LivingHurtEvent（插件在两个事件之间拦下）、LivingDamageEvent 被取消或伤害 0、反伤、没穿齐都不记；真落下才记 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumConvictionOnlyWhenLanded(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        suit(player, 0);
        Zombie zombie = zombie(helper, 3, 1);
        DamageSource melee = player.damageSources().mobAttack(zombie);
        try {
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(player, melee, 4F));
            helper.assertFalse(InquisitoriumArmorEvents.isConvicted(player, zombie), "只到 LivingHurtEvent（插件拦下）不记");
            LivingDamageEvent canceled = new LivingDamageEvent(player, melee, 4F);
            canceled.setCanceled(true);
            MinecraftForge.EVENT_BUS.post(canceled);
            helper.assertFalse(InquisitoriumArmorEvents.isConvicted(player, zombie), "LivingDamageEvent 被取消不记");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, melee, 0F));
            helper.assertFalse(InquisitoriumArmorEvents.isConvicted(player, zombie), "放行但伤害 0（盾牌挡掉）不记");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, source(helper, DamageTypes.THORNS, zombie), 2F));
            helper.assertFalse(InquisitoriumArmorEvents.isConvicted(player, zombie), "反伤不算它动手");
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, melee, 4F));
            helper.assertFalse(InquisitoriumArmorEvents.isConvicted(player, zombie), "没穿齐不记");
            player.setItemSlot(EquipmentSlot.LEGS, piece(ArmorItem.Type.LEGGINGS, 0));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, melee, 4F));
            helper.assertTrue(InquisitoriumArmorEvents.isConvicted(player, zombie), "真落下才记");
        } finally {
            InquisitoriumArmorEvents.forgetForTest(player);
        }
        helper.succeed();
    }

    /** 对玩家 × pvp_effect_scale（0.5 → +10%，填 0 → 不加）；2 阶 +30%、首领 +40%；时长、上限、增伤填 0 都不起作用 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumConvictionScalingAndZero(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        Player rival = mock(helper, 3.5D, 1.5D);
        Zombie zombie = zombie(helper, 3, 3);
        suit(player, 0);
        DamageSource hit = player.damageSources().playerAttack(player);
        try {
            InquisitoriumArmorEvents.convict(player, rival);
            InquisitoriumArmorEvents.convict(player, zombie);
            close(helper, InquisitoriumArmorEvents.convictionBonus(player, rival, hit), 0.2D * AttunementConfig.pvpEffectScale(), "对玩家乘 pvp_effect_scale");
            AttunementConfig.setTestPvpEffectScale(0.5D);
            try {
                close(helper, InquisitoriumArmorEvents.convictionBonus(player, rival, hit), 0.1D, "对玩家 +10%");
                AttunementConfig.setTestPvpEffectScale(0D);
                close(helper, InquisitoriumArmorEvents.convictionBonus(player, rival, hit), 0D, "pvp_effect_scale 填 0 对玩家不加");
                close(helper, InquisitoriumArmorEvents.convictionBonus(player, zombie, hit), 0.2D, "对怪物照加");
            } finally {
                AttunementConfig.setTestPvpEffectScale(null);
            }

            suit(player, 2);
            close(helper, InquisitoriumArmorEvents.convictionBonus(player, zombie, hit), 0.3D, "2 阶 +30%");
            WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
            helper.assertTrue(wither != null, "测试前提：造得出凋灵");
            if (wither != null) {
                // 没放进世界的凋灵：活着、没被移除，按类型认首领就够了
                InquisitoriumArmorEvents.convict(player, wither);
                close(helper, InquisitoriumArmorEvents.convictionBonus(player, wither, hit), 0.4D, "2 阶打首领 +40%");
            }

            suit(player, 0);
            ECInquisitorium.Config.Law d = ECInquisitorium.Config.LAW_DEFAULTS;
            InquisitoriumArmorEvents.forgetForTest(player);
            withLaw(law(true, d.magicResistance(), d.bossResistance(), d.edictReduction(), d.convictionBonus(), 0, 8),
                    () -> helper.assertFalse(InquisitoriumArmorEvents.convict(player, zombie), "conviction_ticks 填 0 不记"));
            withLaw(law(true, d.magicResistance(), d.bossResistance(), d.edictReduction(), d.convictionBonus(), 200, 0),
                    () -> helper.assertFalse(InquisitoriumArmorEvents.convict(player, zombie), "conviction_max_targets 填 0 不记"));
            InquisitoriumArmorEvents.convict(player, zombie);
            withLaw(law(true, d.magicResistance(), d.bossResistance(), d.edictReduction(), 0D, 200, 8),
                    () -> close(helper, InquisitoriumArmorEvents.convictionBonus(player, zombie, hit), 0D, "conviction_bonus 填 0 不加"));
        } finally {
            InquisitoriumArmorEvents.forgetForTest(player);
        }
        helper.succeed();
    }

    /**
     * 定罪增伤只算亲手（同虚空回击、相性）：普通近战、自己射的箭照加；饰品打出的伤害、尖刺圆盾的范围伤害、反伤、裁决之刃的裁断不加。
     * 饰品作用域里真打一下，到护甲前还是原数。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumConvictionOwnHitsOnly(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        Zombie zombie = zombie(helper, 3, 1);
        suit(player, 0);
        try {
            InquisitoriumArmorEvents.convict(player, zombie);
            DamageSource hit = player.damageSources().playerAttack(player);
            close(helper, InquisitoriumArmorEvents.convictionBonus(player, zombie, hit), 0.2D, "亲手近战 +20%");
            Arrow arrow = EntityType.ARROW.create(helper.getLevel());
            helper.assertTrue(arrow != null, "测试前提：造得出箭");
            if (arrow != null) {
                arrow.setOwner(player);
                close(helper, InquisitoriumArmorEvents.convictionBonus(player, zombie, player.damageSources().arrow(arrow, player)), 0.2D, "自己射的箭照加");
            }
            double[] scoped = new double[2];
            BaubleEffects.asBaubleDamage(() -> {
                scoped[0] = InquisitoriumArmorEvents.convictionBonus(player, zombie, player.damageSources().playerAttack(player));
                return true;
            });
            InlayEvents.notWeaponHit(() -> {
                scoped[1] = InquisitoriumArmorEvents.convictionBonus(player, zombie, player.damageSources().playerAttack(player));
                return true;
            });
            close(helper, scoped[0], 0D, "饰品打出的伤害不加");
            close(helper, scoped[1], 0D, "尖刺圆盾的范围伤害不加");
            close(helper, InquisitoriumArmorEvents.convictionBonus(player, zombie, source(helper, DamageTypes.THORNS, player)), 0D, "反伤不加");
            close(helper, InquisitoriumArmorEvents.convictionBonus(player, zombie, VerdictBladeItem.verdictSource(player)), 0D, "裁决之刃的裁断不加");

            FinalHurt probe = new FinalHurt(zombie);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingHurtEvent.class, probe);
            try {
                BaubleEffects.asBaubleDamage(() -> {
                    zombie.invulnerableTime = 0;
                    return zombie.hurt(player.damageSources().playerAttack(player), 5F);
                });
            } finally {
                MinecraftForge.EVENT_BUS.unregister(probe);
            }
            close(helper, probe.last, 5D, "饰品作用域里真打一下 5 点，到护甲前还是 5");
        } finally {
            InquisitoriumArmorEvents.forgetForTest(player);
        }
        helper.succeed();
    }

    // ================================================================ 赦罪

    /**
     * 1 阶亲手打死被定罪的目标：有限时长的先清、清剩余最长的（无限时长的虚弱留着），冷却 200 刻、冷却里不清；
     * 有限的清完了才清无限时长的；0 阶、没定罪、只有 unfilterable 的不清、不进冷却
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumAbsolution(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        suit(player, 1);
        Zombie a = zombie(helper, 3, 1);
        Zombie b = zombie(helper, 3, 3);
        Zombie c = zombie(helper, 1, 3);
        try {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, MobEffectInstance.INFINITE_DURATION));
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400));
            player.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), 1000));
            withNow(T0, () -> {
                InquisitoriumArmorEvents.convict(player, a);
                InquisitoriumArmorEvents.convict(player, b);
                MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(a, player.damageSources().playerAttack(player)));
                helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "打死被定罪的：先清有限时长里剩余最长的缓慢");
                helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "还有有限时长的有害效果时，无限时长的虚弱留着");
                helper.assertTrue(player.hasEffect(MobEffects.POISON), "一次只清一个");
                helper.assertTrue(InquisitoriumArmorEvents.absolve(player, b) == null && player.hasEffect(MobEffects.POISON), "冷却里不清");
            });
            withNow(T0 + 199, () -> helper.assertTrue(InquisitoriumArmorEvents.absolve(player, b) == null, "199 刻还在冷却"));
            withNow(T0 + 200, () -> {
                helper.assertTrue(InquisitoriumArmorEvents.absolve(player, c) == null, "没定罪的不清");
                InquisitoriumArmorEvents.convict(player, b);
                MobEffect cleared = InquisitoriumArmorEvents.absolve(player, b);
                helper.assertTrue(cleared == MobEffects.POISON, "冷却完清剩下的有限时长的中毒，实际 " + cleared);
                helper.assertTrue(player.hasEffect(ECEffects.ANCHORED.get()), "unfilterable 里的锚定不清");
            });
            withNow(T0 + 400, () -> {
                InquisitoriumArmorEvents.convict(player, c);
                MobEffect cleared = InquisitoriumArmorEvents.absolve(player, c);
                helper.assertTrue(cleared == MobEffects.WEAKNESS, "一个有限时长的都没有了，才清无限时长的虚弱，实际 " + cleared);
            });

            withNow(T0 + 600, () -> {
                InquisitoriumArmorEvents.convict(player, c);
                helper.assertTrue(InquisitoriumArmorEvents.absolve(player, c) == null, "只剩 unfilterable 的：没清");
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
                InquisitoriumArmorEvents.convict(player, c);
                helper.assertTrue(InquisitoriumArmorEvents.absolve(player, c) == MobEffects.POISON, "上一下没清不算一次，不进冷却");
            });

            suit(player, 0);
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100));
            withNow(T0 + 1000, () -> {
                InquisitoriumArmorEvents.convict(player, b);
                helper.assertTrue(InquisitoriumArmorEvents.absolve(player, b) == null && player.hasEffect(MobEffects.HUNGER), "0 阶没有赦罪");
            });
        } finally {
            InquisitoriumArmorEvents.forgetForTest(player);
        }
        helper.succeed();
    }

    /**
     * 赦罪只认亲手打死：饰品作用域里的玩家攻击、荆棘反伤打死的不清、不进冷却；被别的模组取消的死亡事件（图腾救下）收不到；
     * 之后亲手打死照样清（证明前面几下都没记冷却）。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumAbsolutionOwnKillsOnly(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        suit(player, 1);
        Zombie zombie = zombie(helper, 3, 1);
        try {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            withNow(T0, () -> {
                InquisitoriumArmorEvents.convict(player, zombie);
                BaubleEffects.asBaubleDamage(() -> {
                    MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(zombie, player.damageSources().playerAttack(player)));
                    return true;
                });
                helper.assertTrue(player.hasEffect(MobEffects.POISON), "饰品打死的不算亲手，不清");
                MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(zombie, source(helper, DamageTypes.THORNS, player)));
                helper.assertTrue(player.hasEffect(MobEffects.POISON), "荆棘反伤打死的不算亲手，不清");
                LivingDeathEvent saved = new LivingDeathEvent(zombie, player.damageSources().playerAttack(player));
                saved.setCanceled(true);
                MinecraftForge.EVENT_BUS.post(saved);
                helper.assertTrue(player.hasEffect(MobEffects.POISON), "死亡事件被取消（图腾救下）不清");
                helper.assertTrue(InquisitoriumArmorEvents.isConvicted(player, zombie), "没清的那几下不把它从定罪表里拿掉");
                MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(zombie, player.damageSources().playerAttack(player)));
                helper.assertFalse(player.hasEffect(MobEffects.POISON), "亲手打死才清，前面几下没进冷却");
            });
        } finally {
            InquisitoriumArmorEvents.forgetForTest(player);
        }
        helper.succeed();
    }

    // ================================================================ 律令：真玩家走刻、被盖住的那一层

    /**
     * 律令走真的 PlayerTickEvent：真玩家穿全套挂 200 刻缓慢，走 20 刻（自然少 20、律令在 tickCount 是 20 的倍数那一刻削 13）剩 167。
     * 真玩家不在世界里，tickCount 由服务端世界每刻加一，这里照样手动加。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumEdictOnPlayerTicks(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-inq-edict");
        try {
            suit(player, 0);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
            player.tickCount = 1;
            for (int i = 0; i < 20; i++) {
                player.tickCount++;
                player.doTick();
            }
            helper.assertTrue(duration(player, MobEffects.MOVEMENT_SLOWDOWN) == 167, "200 − 20 − 13 = 167，实际 " + duration(player, MobEffects.MOVEMENT_SLOWDOWN));
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            for (int i = 0; i < 20; i++) {
                player.tickCount++;
                player.doTick();
            }
            helper.assertTrue(duration(player, MobEffects.MOVEMENT_SLOWDOWN) == 147, "没穿齐只按自然流逝少 20，实际 " + duration(player, MobEffects.MOVEMENT_SLOWDOWN));
        } finally {
            player.getAdvancements().stopListening();
        }
        helper.succeed();
    }

    /**
     * 律令只缩表层（同医师腰带）：先挂中毒 I 600、再挂中毒 II 100，II 盖住 I；削一次后表层 100 → 87，被盖住的那层还是 600，等它露出来以后再缩。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumEdictLeavesHiddenLayer(GameTestHelper helper) {
        Player player = mock(helper, 1.5D, 1.5D);
        suit(player, 0);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
        MobEffectInstance top = player.getEffect(MobEffects.POISON);
        MobEffectInstance hidden = hiddenLayer(top);
        helper.assertTrue(top != null && top.getAmplifier() == 1 && hidden != null && hidden.getDuration() == 600,
                "测试前提：中毒 II 100 盖住中毒 I 600");
        helper.assertTrue(InquisitoriumArmorEvents.edict(player) == 1, "削了一个效果");
        helper.assertTrue(top.getDuration() == 87, "表层 100 → 87，实际 " + top.getDuration());
        helper.assertTrue(hiddenLayer(top) == hidden && hidden.getDuration() == 600, "被盖住的那层不缩，实际 " + hidden.getDuration());
        helper.succeed();
    }

    /** MobEffectInstance 里被盖住的那一层（私有字段，按类型找，不依赖字段名） */
    private static MobEffectInstance hiddenLayer(MobEffectInstance instance) {
        if (instance == null) {
            return null;
        }
        for (Field field : MobEffectInstance.class.getDeclaredFields()) {
            if (field.getType() == MobEffectInstance.class) {
                try {
                    field.setAccessible(true);
                    return (MobEffectInstance) field.get(instance);
                } catch (IllegalAccessException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    // ================================================================ 换装

    /**
     * 同调写盘只改了身上那件的 NBT：原版照样发一次换装事件、重换属性修饰符。生命不被夹、全套照认、定罪表还在、减伤不变。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumNbtChangeIsNotUnequip(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-inq-nbt");
        Zombie zombie = zombie(helper, 3, 1);
        int[] changes = {0};
        Consumer<LivingEquipmentChangeEvent> counter = event -> {
            if (event.getEntity() == player && event.getSlot() == EquipmentSlot.CHEST) {
                changes[0]++;
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingEquipmentChangeEvent.class, counter);
        try {
            ItemStack[] stacks = suit(player, 1);
            player.doTick();
            float max = player.getMaxHealth();
            helper.assertTrue(max > 20F, "测试前提：穿上以后最大生命大于 20（胸甲 +30、每阶每件 +1），实际 " + max);
            player.setHealth(max);
            InquisitoriumArmorEvents.convict(player, zombie);
            DamageSource spell = source(helper, DamageTypes.INDIRECT_MAGIC, zombie);
            double before = InquisitoriumArmorEvents.incomingMultiplier(player, spell);
            int seen = changes[0];

            ItemStack chest = stacks[1];
            Attunement.addPoints(chest, 5);
            player.doTick();
            helper.assertTrue(changes[0] > seen, "测试前提：只改 NBT 原版也发了换装事件");
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST) == chest, "还是同一件");
            helper.assertTrue(player.getMaxHealth() == max && player.getHealth() == max, "生命不被夹：" + player.getHealth() + " / " + player.getMaxHealth());
            helper.assertTrue(InquisitoriumArmorEvents.setStage(player) == 1, "全套照认");
            helper.assertTrue(InquisitoriumArmorEvents.isConvicted(player, zombie), "定罪表还在");
            close(helper, InquisitoriumArmorEvents.incomingMultiplier(player, spell), before, "减伤不变");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
            InquisitoriumArmorEvents.forgetForTest(player);
            player.getAdvancements().stopListening();
        }
        helper.succeed();
    }

    // ================================================================ 升阶配方、提示

    /** 四条升阶配方照 §4.5：起始阶、MRU、四角与数量（2 阶 13d 起用霜髓 ×4，3 阶 13d 起用异变之心 + 异变残质 ×8，4 阶 13c 起用矩阵结晶）；1 → 2 霜髓不够 4 个不匹配 */
    @GameTest(template = EMPTY)
    public static void inquisitoriumAscensionRecipes(GameTestHelper helper) {
        Object[][] table = {
                {"inquisitorium_1", 0, 40_000, new String[]{"essentialcraft5:demonic_core", "essentialcraft5:ackronite_ingot", "essentialcraft5:twinkling_titanite", null},
                        new int[]{1, 2, 1, 1}},
                {"inquisitorium_2", 1, 60_000, new String[]{"essentialcraft5:addition_core", "essentialcraft5:demonic_plating", "essentialcraft5:ackronite_ingot",
                        "essentialcraft5:frost_marrow"}, new int[]{1, 1, 2, 4}},
                {"inquisitorium_3", 2, 80_000, new String[]{"essentialcraft5:aberrant_heart", "essentialcraft5:aberrant_residue", "essentialcraft5:neuronic_enriched_plating",
                        "essentialcraft5:demonic_core"}, new int[]{1, 8, 1, 1}},
                {"inquisitorium_4", 3, 100_000, new String[]{"essentialcraft5:inquisitorium_chip", "essentialcraft5:matrix_crystal", "essentialcraft5:matter_of_eternity",
                        "essentialcraft5:demonic_core"}, new int[]{1, 1, 1, 1}},
        };
        for (Object[] row : table) {
            ResourceLocation id = EssentialCraft5.id("attunement/" + row[0]);
            Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
            helper.assertTrue(recipe instanceof AttunementAscensionRecipe, id + " 应是升阶配方，实际 " + recipe);
            if (!(recipe instanceof AttunementAscensionRecipe ascension)) {
                return;
            }
            helper.assertTrue(ascension.family().equals(ECAttunement.INQUISITORIUM_ARMOR.id()) && ascension.fromStage() == (int) row[1]
                    && ascension.parts().isEmpty() && ascension.getMRU() == (int) row[2] && ascension.promoteTo().isEmpty(), id + " 的家族 / 起始阶 / 部位 / MRU 不对");
            String[] corners = (String[]) row[3];
            int[] counts = (int[]) row[4];
            for (int i = 0; i < 4; i++) {
                if (corners[i] == null) {
                    helper.assertTrue(ascension.getIngredients().get(i + 1).isEmpty(), id + " 第 " + (i + 1) + " 角应是空的");
                    continue;
                }
                Item want = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(corners[i]));
                helper.assertTrue(want != null && ascension.getIngredients().get(i + 1).test(new ItemStack(want)), id + " 第 " + (i + 1) + " 角应收 " + corners[i]);
                helper.assertTrue(ascension.countAt(i + 1) == counts[i], id + " 第 " + (i + 1) + " 角数量应是 " + counts[i] + "，实际 " + ascension.countAt(i + 1));
            }
        }

        AttunementAscensionRecipe second = (AttunementAscensionRecipe) helper.getLevel().getRecipeManager()
                .byKey(EssentialCraft5.id("attunement/inquisitorium_2")).orElseThrow();
        ItemStack helmet = piece(ArmorItem.Type.HELMET, 1);
        Attunement.set(helmet, 1, Attunement.need(ECAttunement.INQUISITORIUM_ARMOR, 1));
        Attunement.stampRank(helmet, AttunementConfig.requiredRank(2));
        ItemStack core = new ItemStack(item("division_core"));
        ItemStack plating = new ItemStack(item("demonic_plating"));
        ItemStack ingots = new ItemStack(item("ackronite_ingot"), 2);
        helper.assertTrue(second.matches(new SimpleContainer(helmet, core, plating, ingots, new ItemStack(item("frost_marrow"), 4)), helper.getLevel()),
                "1 → 2：任一数学核心 + 恶魔护板 + 阿克罗尼特锭 ×2 + 霜髓 ×4");
        helper.assertFalse(second.matches(new SimpleContainer(helmet, core, plating, ingots, new ItemStack(item("frost_marrow"), 3)), helper.getLevel()),
                "霜髓只有 3 个不匹配");
        helper.assertFalse(second.matches(new SimpleContainer(helmet, core, plating, ingots, new ItemStack(Items.BLUE_ICE, 4)), helper.getLevel()),
                "13d 起不再收蓝冰");
        ItemStack out = second.assemble(new SimpleContainer(helmet, core, plating, ingots, new ItemStack(item("frost_marrow"), 4)), helper.getLevel().registryAccess());
        helper.assertTrue(out.getItem() instanceof InquisitoriumArmorItem && Attunement.stage(out) == 2, "出来 2 阶头盔");
        helper.succeed();
    }

    /**
     * 提示行：不掉耐久、全套四条（数字读配置）；对应的配置填 0 时那一行不写。定罪的秒数带一位小数（30 刻 = 1.5 秒）；
     * 对玩家的增伤另起一行（× pvp_effect_scale，填 0 写「不多」，填 1 不写）；增伤填 0 但还在定罪时只写「被定罪」那半句（1、2 阶靠它）。
     */
    @GameTest(template = EMPTY)
    public static void inquisitoriumTooltipLines(GameTestHelper helper) {
        String prefix = "tooltip.essentialcraft5.inquisitorium_armor.";
        AttunementConfig.setTestPvpEffectScale(0.5D);
        try {
            List<Component> lines = new ArrayList<>();
            InquisitoriumArmorItem.lawTooltip(lines);
            for (String key : List.of("keep_durability", "fullset", "fullset.magic", "fullset.boss", "fullset.edict", "fullset.conviction",
                    "fullset.conviction.pvp")) {
                helper.assertTrue(hasKey(lines, prefix + key), "默认应有 " + key);
            }
            TranslatableContents conviction = contents(lines, prefix + "fullset.conviction");
            helper.assertTrue(conviction != null && "10".equals(String.valueOf(conviction.getArgs()[0])) && "8".equals(String.valueOf(conviction.getArgs()[1]))
                    && "20%".equals(String.valueOf(conviction.getArgs()[2])), "定罪一行：10 秒、8 个、20%");
            TranslatableContents pvp = contents(lines, prefix + "fullset.conviction.pvp");
            helper.assertTrue(pvp != null && "10%".equals(String.valueOf(pvp.getArgs()[0])), "对玩家一行：20% × 0.5 = 10%");

            ECInquisitorium.Config.Law d = ECInquisitorium.Config.LAW_DEFAULTS;
            withLaw(law(true, d.magicResistance(), d.bossResistance(), d.edictReduction(), d.convictionBonus(), 30, 8), () -> {
                List<Component> odd = new ArrayList<>();
                InquisitoriumArmorItem.lawTooltip(odd);
                TranslatableContents line = contents(odd, prefix + "fullset.conviction");
                helper.assertTrue(line != null && "1.5".equals(String.valueOf(line.getArgs()[0])), "30 刻写 1.5 秒（不是整数除出来的 1 秒），实际 "
                        + (line == null ? null : line.getArgs()[0]));
            });

            AttunementConfig.setTestPvpEffectScale(0D);
            List<Component> noPvp = new ArrayList<>();
            InquisitoriumArmorItem.lawTooltip(noPvp);
            helper.assertTrue(hasKey(noPvp, prefix + "fullset.conviction.pvp_none") && !hasKey(noPvp, prefix + "fullset.conviction.pvp"),
                    "pvp_effect_scale 填 0：写打玩家不多");
            AttunementConfig.setTestPvpEffectScale(1D);
            List<Component> full = new ArrayList<>();
            InquisitoriumArmorItem.lawTooltip(full);
            helper.assertFalse(hasKey(full, prefix + "fullset.conviction.pvp") || hasKey(full, prefix + "fullset.conviction.pvp_none"),
                    "pvp_effect_scale 填 1：对玩家和对怪一样，不另写");
        } finally {
            AttunementConfig.setTestPvpEffectScale(null);
        }

        withLaw(law(false, 0D, 0D, 0D, 0D, 200, 8), () -> withGrowth(0D, 0D, 0D, () -> {
            List<Component> off = new ArrayList<>();
            InquisitoriumArmorItem.lawTooltip(off);
            for (String key : List.of("keep_durability", "fullset.magic", "fullset.boss", "fullset.edict", "fullset.conviction", "fullset.conviction.pvp")) {
                helper.assertFalse(hasKey(off, prefix + key), "填 0 / 关掉时不写 " + key);
            }
            TranslatableContents mark = contents(off, prefix + "fullset.conviction.mark_only");
            helper.assertTrue(mark != null && "10".equals(String.valueOf(mark.getArgs()[0])) && "8".equals(String.valueOf(mark.getArgs()[1])),
                    "增伤填 0 但还在定罪（赦罪、2 阶要用）：只写被定罪 10 秒、8 个");
        }));
        withLaw(law(true, 0D, 0D, 0D, 0.2D, 0, 8), () -> {
            List<Component> off = new ArrayList<>();
            InquisitoriumArmorItem.lawTooltip(off);
            helper.assertFalse(hasKey(off, prefix + "fullset.conviction") || hasKey(off, prefix + "fullset.conviction.mark_only"),
                    "conviction_ticks 填 0（不定罪）：定罪那行不写");
        });
        helper.succeed();
    }

    private static boolean hasKey(List<Component> lines, String key) {
        return contents(lines, key) != null;
    }

    private static TranslatableContents contents(List<Component> lines, String key) {
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents translatable && key.equals(translatable.getKey())) {
                return translatable;
            }
        }
        return null;
    }
}
