package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.gun.LensEffects;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.gun.RayShot;
import com.flameforge.essentialcraft5.item.DividerGunItem;
import com.flameforge.essentialcraft5.item.EmberInlayItem;
import com.flameforge.essentialcraft5.item.OrbitalRemoteItem;
import com.flameforge.essentialcraft5.item.SpikyShieldItem;
import com.flameforge.essentialcraft5.item.bauble.SpecialBaubleItem;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.InlayEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.registry.ECItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十批 10b「武器机制」的 GameTest（batch10.md §5.7，31 条）：
 * <ul>
 *   <li><b>透镜</b>（8）：混沌腐蚀、冰冻冻结、魔力驱散各打一只僵尸生效；攻击在 {@code LivingAttackEvent} 被拦下时不加；
 *       混合端模拟（{@code RayHitGate.setHybridForTest}）里别的模组取消 {@code LivingHurtEvent}、混合端照样按 0 发 {@code LivingDamageEvent} 时不加；
 *       冻结对 {@code freeze_immune} 实体、穿皮革靴子的无效；驱散先剥无限时长的、再剥剩余最长的、不碰有害的；腐蚀期间 {@code heal} 无效。</li>
 *   <li><b>余烬嵌刻</b>（15）：14 条配方都在、结果 NBT 对；铁砧对剑 / 斧 / 弓给出嵌好的拷贝（经验、材料数、改名、取走时发手册进度）；
 *       不是武器、叠着的、空白嵌片、功能关着都不给；余烬元素剑被拒；黑名单被拒；嵌刻武器打僵尸触发效果、扣 50、伤害不变；扣不起不触发；
 *       攻击被拦下不记、伤害事件后来被取消的结算时不算；再嵌换掉旧的、同一对不给；弓射出的箭同样触发、主手不是弓弩时不算。
 *       审查后补的：ν 护身符的连锁伤害、尖刺圆盾的范围伤害不算武器打的；同一刻对同一个生物的几段伤害只结算一次（前一段没打实时后一段顶上）；
 *       被伤害吸收整下吃掉的一下算打实，原伤害的 {@code LivingHurtEvent} 被取消、混合端照发 {@code LivingDamageEvent(0)} 的不算。</li>
 *   <li><b>二分枪对 Boss</b>（5）：首领挨二分场后挂二分、生命不减半（时长 0 时不挂）；带二分的首领打玩家伤害减半；探测被拦时不挂（这三条用
 *       {@code DividerGunItem.setTestBossOverride} 把僵尸当首领：原版首领挂不上效果或探测不过去，用它们测会空转通过）；
 *       原版凋灵对状态效果免疫、探测放行也不挂；全息投影不受影响。</li>
 *   <li><b>轨道锁定</b>（3）：打击点跟着移动的僵尸、落下前 10 刻定住；目标被移除时停在最后位置；遥控器右键对准僵尸时锁定、没对准生物时照旧打方块。</li>
 * </ul>
 * 测试玩家是 {@code ProtectionFrameworkTests.realPlayer}（真 ServerPlayer、不在世界的实体列表里）。配置一律用各类的测试覆盖值，
 * 在同一段同步代码里设、测、置回；凋灵在同一刻里删掉（它会 tick 的话会放凋灵骷髅头打旁边的测试）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class WeaponMechanicsTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 不会在测试里自己到点的延迟 */
    private static final int LONG_DELAY = 1000;
    private static final float EPS = 1.0E-3F;
    private static final RayShot.MatrixFxSink NO_FX = (target, intensity) -> {
    };

    private WeaponMechanicsTests() {
    }

    // ================================================================== 透镜

    /** 混沌透镜打实一下：僵尸挂上 60 刻腐蚀（无透镜的对照不挂） */
    @GameTest(template = EMPTY)
    public static void lensChaosCorrodes(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-lc", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie plain = sturdyZombie(helper, new Vec3(1.5D, 1D, 3.5D));
        try {
            LensEffects.Config.setTestOverrides(60, 15, true);
            RayShot.Hit hit = RayShot.strike(shooter, zombie, lens("chaos"), NO_FX);
            RayShot.strike(shooter, plain, lens(null), NO_FX);
            helper.assertTrue(hit.damaged(), "这一下应打实");
            MobEffectInstance corrosion = zombie.getEffect(ECEffects.CORROSION.get());
            helper.assertTrue(corrosion != null && corrosion.getDuration() == 60, "混沌透镜应挂 60 刻腐蚀，实际 " + corrosion);
            helper.assertFalse(plain.hasEffect(ECEffects.CORROSION.get()), "没装透镜的不该挂腐蚀");
        } finally {
            LensEffects.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /** 冰冻透镜：一下 +15 冰冻刻；冻得再深也只到「冻僵所需 + 40」 */
    @GameTest(template = EMPTY)
    public static void lensFrozenFreezes(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-lf", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        try {
            LensEffects.Config.setTestOverrides(60, 15, true);
            RayShot.strike(shooter, zombie, lens("frozen"), NO_FX);
            helper.assertTrue(zombie.getTicksFrozen() == 15, "一下应加 15 冰冻刻，实际 " + zombie.getTicksFrozen());
            int cap = zombie.getTicksRequiredToFreeze() + LensEffects.FREEZE_OVERFLOW;
            zombie.setTicksFrozen(cap - 5);
            zombie.invulnerableTime = 0;
            RayShot.strike(shooter, zombie, lens("frozen"), NO_FX);
            helper.assertTrue(zombie.getTicksFrozen() == cap, "冻得再深也只到 " + cap + "，实际 " + zombie.getTicksFrozen());
            helper.assertTrue(zombie.isFullyFrozen(), "冻满了应算冻僵");
        } finally {
            LensEffects.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /** 魔力透镜：剥掉一个有益效果（这里是剩余最长的迅捷），力量还在 */
    @GameTest(template = EMPTY)
    public static void lensMagicDispels(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-lm", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
        zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0));
        try {
            LensEffects.Config.setTestOverrides(60, 15, true);
            RayShot.strike(shooter, zombie, lens("pure"), NO_FX);
            helper.assertFalse(zombie.hasEffect(MobEffects.MOVEMENT_SPEED), "剩余最长的迅捷应被剥掉");
            helper.assertTrue(zombie.hasEffect(MobEffects.DAMAGE_BOOST), "一下只剥一个，力量应还在");
            LensEffects.Config.setTestOverrides(60, 15, false);
            zombie.invulnerableTime = 0;
            RayShot.strike(shooter, zombie, lens("pure"), NO_FX);
            helper.assertTrue(zombie.hasEffect(MobEffects.DAMAGE_BOOST), "驱散关掉以后不剥");
        } finally {
            LensEffects.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /**
     * 暗影透镜：射手回「目标实际掉的血 × 吸取百分比」；打死目标的那一下也回；吸取关掉、射手挂着腐蚀时不回；
     * 没装透镜的对照不回
     */
    @GameTest(template = EMPTY)
    public static void lensShadeDrains(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-ls", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie weak = sturdyZombie(helper, new Vec3(1.5D, 1D, 3.5D));
        try {
            LensEffects.Config.setTestOverrides(60, 15, true, 50D);
            shooter.setHealth(4F);
            RayShot.strike(shooter, zombie, lens(null), NO_FX);
            helper.assertTrue(shooter.getHealth() == 4F, "没装透镜不该回血，实际 " + shooter.getHealth());

            zombie.invulnerableTime = 0;
            float before = zombie.getHealth();
            RayShot.Hit hit = RayShot.strike(shooter, zombie, lens("shade"), NO_FX);
            float dealt = before - zombie.getHealth();
            helper.assertTrue(hit.damaged() && dealt > 0F, "这一下应打实");
            helper.assertTrue(Math.abs(shooter.getHealth() - (4F + dealt * 0.5F)) < EPS,
                    "应回 " + dealt + " × 50%，实际血量 " + shooter.getHealth());

            weak.setHealth(1F);
            float beforeKill = shooter.getHealth();
            RayShot.strike(shooter, weak, lens("shade"), NO_FX);
            helper.assertTrue(!weak.isAlive() && shooter.getHealth() > beforeKill, "打死目标的那一下也该回血");

            LensEffects.Config.setTestOverrides(60, 15, true, 0D);
            helper.assertFalse(LensEffects.active(LensEffects.SHADE), "吸取 0% 时暗影透镜应算没效果");
            float beforeOff = shooter.getHealth();
            zombie.invulnerableTime = 0;
            RayShot.strike(shooter, zombie, lens("shade"), NO_FX);
            helper.assertTrue(shooter.getHealth() == beforeOff, "吸取关掉不该回血");

            LensEffects.Config.setTestOverrides(60, 15, true, 50D);
            LensEffects.corrode(shooter, null, 100);
            float beforeCorroded = shooter.getHealth();
            zombie.invulnerableTime = 0;
            RayShot.strike(shooter, zombie, lens("shade"), NO_FX);
            helper.assertTrue(shooter.getHealth() == beforeCorroded, "射手挂着腐蚀回不了血，实际 " + shooter.getHealth());
        } finally {
            LensEffects.Config.setTestOverrides(null, null, null, null);
        }
        helper.succeed();
    }

    /** 攻击在 LivingAttackEvent 被拦下（Forge 端领地模组的做法）：不掉血、不加腐蚀；同一时刻没被拦的照加 */
    @GameTest(template = EMPTY)
    public static void lensBlockedByAttackEvent(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-la", new Vec3(0.5D, 1D, 0.5D));
        Zombie guarded = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie open = sturdyZombie(helper, new Vec3(1.5D, 1D, 3.5D));
        Runnable off = listen(EventPriority.NORMAL, LivingAttackEvent.class, event -> {
            if (event.getEntity() == guarded) {
                event.setCanceled(true);
            }
        });
        try {
            LensEffects.Config.setTestOverrides(60, 15, true);
            RayShot.strike(shooter, guarded, lens("chaos"), NO_FX);
            RayShot.strike(shooter, open, lens("chaos"), NO_FX);
        } finally {
            off.run();
            LensEffects.Config.setTestOverrides(null, null, null);
        }
        helper.assertTrue(full(guarded) && !guarded.hasEffect(ECEffects.CORROSION.get()), "被拦下的不该掉血、不该挂腐蚀");
        helper.assertTrue(open.hasEffect(ECEffects.CORROSION.get()), "没被拦的应挂腐蚀");
        helper.succeed();
    }

    /**
     * 混合端模拟：别的模组在 LivingHurtEvent 里取消原伤害，Mohist 系混合端照样按 0 发 LivingDamageEvent（这里在取消的同时手动发一个 0 伤害的
     * LivingDamageEvent 模拟）——不该当成打实，不加腐蚀；同样当成混合端、没人拦的对照照加
     */
    @GameTest(template = EMPTY)
    public static void lensHybridCanceledHurt(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-lh", new Vec3(0.5D, 1D, 0.5D));
        Zombie canceled = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie control = sturdyZombie(helper, new Vec3(1.5D, 1D, 3.5D));
        int[] fakeDamageEvents = {0};
        Runnable off = listen(EventPriority.NORMAL, LivingHurtEvent.class, event -> {
            if (event.getEntity() == canceled) {
                event.setCanceled(true);
                fakeDamageEvents[0]++;
                MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(canceled, event.getSource(), 0F));
            }
        });
        try {
            RayHitGate.setHybridForTest(true);
            LensEffects.Config.setTestOverrides(60, 15, true);
            RayShot.strike(shooter, canceled, lens("chaos"), NO_FX);
            RayShot.strike(shooter, control, lens("chaos"), NO_FX);
        } finally {
            off.run();
            RayHitGate.setHybridForTest(null);
            LensEffects.Config.setTestOverrides(null, null, null);
        }
        helper.assertTrue(fakeDamageEvents[0] == 1, "模拟的 LivingDamageEvent(0) 应发过一次，实际 " + fakeDamageEvents[0]);
        helper.assertFalse(canceled.hasEffect(ECEffects.CORROSION.get()), "LivingHurtEvent 被取消（混合端按 0 照发 LivingDamageEvent）时不该挂腐蚀");
        helper.assertTrue(control.hasEffect(ECEffects.CORROSION.get()), "混合端上没人拦的应照常挂腐蚀");
        helper.succeed();
    }

    /** 冻结：流浪者（freeze_immune_entity_types）、穿皮革靴子的僵尸冻不上；光着脚的僵尸冻上 */
    @GameTest(template = EMPTY)
    public static void lensFreezeImmune(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-li", new Vec3(0.5D, 1D, 0.5D));
        Stray stray = helper.spawnWithNoFreeWill(EntityType.STRAY, new Vec3(2.5D, 1D, 2.5D));
        Zombie booted = sturdyZombie(helper, new Vec3(1.5D, 1D, 3.5D));
        booted.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        Zombie bare = sturdyZombie(helper, new Vec3(3.5D, 1D, 1.5D));
        try {
            LensEffects.Config.setTestOverrides(60, 15, true);
            RayShot.strike(shooter, stray, lens("frozen"), NO_FX);
            RayShot.strike(shooter, booted, lens("frozen"), NO_FX);
            RayShot.strike(shooter, bare, lens("frozen"), NO_FX);
        } finally {
            LensEffects.Config.setTestOverrides(null, null, null);
        }
        helper.assertTrue(stray.getTicksFrozen() == 0, "流浪者冻不上，实际 " + stray.getTicksFrozen());
        helper.assertTrue(booted.getTicksFrozen() == 0, "穿皮革靴子的冻不上，实际 " + booted.getTicksFrozen());
        helper.assertTrue(bare.getTicksFrozen() == 15, "光脚的应冻上 15 刻，实际 " + bare.getTicksFrozen());
        helper.succeed();
    }

    /** 驱散的顺序：无限时长的抗火先剥；再剥剩余最长的迅捷；再剥力量；有害的缓慢一直不碰 */
    @GameTest(template = EMPTY)
    public static void lensDispelOrder(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-lo", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0));
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0));
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2000, 0));
        try {
            LensEffects.Config.setTestOverrides(60, 15, true);
            RayShot.strike(shooter, zombie, lens("pure"), NO_FX);
            helper.assertTrue(!zombie.hasEffect(MobEffects.FIRE_RESISTANCE) && zombie.hasEffect(MobEffects.MOVEMENT_SPEED)
                    && zombie.hasEffect(MobEffects.DAMAGE_BOOST), "第一下应剥无限时长的抗火");
            zombie.invulnerableTime = 0;
            RayShot.strike(shooter, zombie, lens("pure"), NO_FX);
            helper.assertTrue(!zombie.hasEffect(MobEffects.MOVEMENT_SPEED) && zombie.hasEffect(MobEffects.DAMAGE_BOOST), "第二下应剥剩余最长的迅捷");
            zombie.invulnerableTime = 0;
            RayShot.strike(shooter, zombie, lens("pure"), NO_FX);
            helper.assertFalse(zombie.hasEffect(MobEffects.DAMAGE_BOOST), "第三下剥力量");
            zombie.invulnerableTime = 0;
            RayShot.strike(shooter, zombie, lens("pure"), NO_FX);
            helper.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "有害的缓慢一直不碰");
        } finally {
            LensEffects.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /** 腐蚀期间 heal 无效；没腐蚀的对照照常回血 */
    @GameTest(template = EMPTY)
    public static void lensCorrosionBlocksHeal(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-lr", new Vec3(0.5D, 1D, 0.5D));
        Zombie corroded = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie control = sturdyZombie(helper, new Vec3(1.5D, 1D, 3.5D));
        try {
            LensEffects.Config.setTestOverrides(60, 15, true);
            RayShot.strike(shooter, corroded, lens("chaos"), NO_FX);
            RayShot.strike(shooter, control, lens(null), NO_FX);
        } finally {
            LensEffects.Config.setTestOverrides(null, null, null);
        }
        float corrodedBefore = corroded.getHealth();
        float controlBefore = control.getHealth();
        helper.assertTrue(corrodedBefore < corroded.getMaxHealth() && controlBefore < control.getMaxHealth(), "两只都应先掉了血");
        corroded.heal(10F);
        control.heal(10F);
        helper.assertTrue(corroded.getHealth() == corrodedBefore, "腐蚀期间不该回血，实际 " + corrodedBefore + " → " + corroded.getHealth());
        helper.assertTrue(Math.abs(control.getHealth() - (controlBefore + 10F)) < EPS, "没腐蚀的应回 10，实际 " + controlBefore + " → " + control.getHealth());
        helper.succeed();
    }

    // ================================================================== 余烬嵌刻

    /** 14 条配方：每条的产物是一片记着那一对的嵌片、原料是两个余烬 + 淡青宝石 + 注魔合金；补上的两对在、只加伤害 / 带反噬的不在 */
    @GameTest(template = EMPTY)
    public static void inlayRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<EmberInlayItem.Pair> pairs = EmberInlayItem.craftablePairs();
        helper.assertTrue(pairs.size() == 14, "应有 14 对能做，实际 " + pairs.size());
        long count = level.getRecipeManager().getRecipeIds()
                .filter(id -> id.getNamespace().equals(EssentialCraft5.MODID) && id.getPath().startsWith("ember_inlay/")).count();
        helper.assertTrue(count == 14, "ember_inlay/ 下应有 14 条配方，实际 " + count);
        ItemStack paleGem = new ItemStack(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("pale_gem")));
        ItemStack alloy = new ItemStack(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("combined_magical_alloys")));
        for (EmberInlayItem.Pair pair : pairs) {
            ResourceLocation id = recipeId(pair);
            Optional<? extends Recipe<?>> recipe = level.getRecipeManager().byKey(id);
            helper.assertTrue(recipe.isPresent() && recipe.get().getType() == RecipeType.CRAFTING, "缺工作台配方 " + id);
            ItemStack result = recipe.get().getResultItem(level.registryAccess());
            helper.assertTrue(result.is(ECEmberForge.EMBER_INLAY.get()) && result.getCount() == 1, id + " 的产物应是 1 片余烬嵌片");
            helper.assertTrue(EmberInlayItem.embers(result).equals(Optional.of(pair)), id + " 的产物应记着 " + pair + "，实际 " + result.getTag());
            NonNullList<Ingredient> ingredients = recipe.get().getIngredients();
            helper.assertTrue(ingredients.size() == 4, id + " 应有 4 个原料");
            for (ItemStack needed : List.of(new ItemStack(pair.first().item()), new ItemStack(pair.second().item()), paleGem, alloy)) {
                helper.assertTrue(ingredients.stream().anyMatch(ingredient -> ingredient.test(needed)), id + " 缺原料 " + needed);
            }
        }
        helper.assertTrue(pairs.contains(EmberInlayItem.Pair.of(EmberKind.DIVINE, EmberKind.MAGICAL))
                && pairs.contains(EmberInlayItem.Pair.of(EmberKind.DIVINE, EmberKind.FLAME)), "神圣 + 魔力 / 燃烧两对应能做");
        helper.assertFalse(pairs.contains(EmberInlayItem.Pair.of(EmberKind.CHAOS, EmberKind.COMMON)), "混沌 + 阴燃（伤害 + 反噬）不该能做");
        helper.assertFalse(pairs.contains(EmberInlayItem.Pair.of(EmberKind.ACIDIC, EmberKind.CHAOS)), "只加伤害的不该能做");
        helper.succeed();
    }

    /** 铁砧：剑、斧、弓都给出嵌好的拷贝（花 10 级、1 片），锹、锄、盾牌、钓鱼竿也能嵌，改名照原版；取走时发手册进度 */
    @GameTest(template = EMPTY)
    public static void inlayAnvilWeapons(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-ia", new Vec3(0.5D, 1D, 0.5D));
        EmberInlayItem.Pair pair = EmberInlayItem.Pair.of(EmberKind.COMMON, EmberKind.CORRUPTED);
        ItemStack inlay = inlay(pair);
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            for (ItemStack weapon : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE), new ItemStack(Items.BOW))) {
                AnvilUpdateEvent event = anvil(weapon, inlay, null, player);
                ItemStack out = event.getOutput();
                helper.assertTrue(out.is(weapon.getItem()) && EmberInlayItem.inlayOf(out).equals(Optional.of(pair)),
                        weapon.getItem() + " 应给出嵌好的拷贝，实际 " + out + " " + out.getTag());
                helper.assertTrue(event.getCost() == 10 && event.getMaterialCost() == 1, weapon.getItem() + " 应花 10 级、1 片，实际 "
                        + event.getCost() + " / " + event.getMaterialCost());
                helper.assertTrue(EmberInlayItem.inlayOf(weapon).isEmpty(), "原来那件不该被改");
            }
            // 手册「哪些武器能嵌」照 #forge:tools 写：锹、锄、盾牌、钓鱼竿也在里面
            for (ItemStack tool : List.of(new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_HOE), new ItemStack(Items.SHIELD),
                    new ItemStack(Items.FISHING_ROD))) {
                helper.assertTrue(EmberInlayItem.isInlayable(tool) && !anvil(tool, inlay, null, player).getOutput().isEmpty(),
                        tool.getItem() + " 应能嵌（#forge:tools）");
            }
            ItemStack renamed = anvil(new ItemStack(Items.IRON_SWORD), inlay, "ec5", player).getOutput();
            helper.assertTrue(renamed.hasCustomHoverName() && renamed.getHoverName().getString().equals("ec5"), "改名照原版一起生效");

            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            ItemStack out = anvil(sword, inlay, null, player).getOutput();
            MinecraftForge.EVENT_BUS.post(new AnvilRepairEvent(player, sword, inlay, out));
            Advancement step = player.server.getAdvancements().getAdvancement(ECEmberForge.ADVANCEMENT_EMBER_INLAY);
            helper.assertTrue(step != null && player.getAdvancements().getOrStartProgress(step).isDone(), "取走嵌好的武器应发 guide/void_machines/ember_inlay");
        } finally {
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /** 铁砧不给：钻石（不是武器）、叠着的两把剑、空白嵌片、功能关着 */
    @GameTest(template = EMPTY)
    public static void inlayAnvilRejectsNonWeapons(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-in", new Vec3(0.5D, 1D, 0.5D));
        ItemStack inlay = inlay(EmberInlayItem.Pair.of(EmberKind.COMMON, EmberKind.CORRUPTED));
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            helper.assertTrue(anvil(new ItemStack(Items.DIAMOND), inlay, null, player).getOutput().isEmpty(), "钻石不是武器，不该给");
            helper.assertTrue(anvil(new ItemStack(Items.IRON_SWORD, 2), inlay, null, player).getOutput().isEmpty(), "叠着的不该给（会整叠吃掉）");
            helper.assertTrue(anvil(new ItemStack(Items.IRON_SWORD), new ItemStack(ECEmberForge.EMBER_INLAY.get()), null, player).getOutput().isEmpty(),
                    "空白嵌片不该给");
            EmberInlayItem.Config.setTestOverrides(false, 50, 10);
            helper.assertTrue(anvil(new ItemStack(Items.IRON_SWORD), inlay, null, player).getOutput().isEmpty(), "功能关着不该给");
        } finally {
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /** 余烬元素剑本身不能嵌（锻好的、没锻的都不行） */
    @GameTest(template = EMPTY)
    public static void inlayAnvilRejectsForgedSword(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-if", new Vec3(0.5D, 1D, 0.5D));
        ItemStack inlay = inlay(EmberInlayItem.Pair.of(EmberKind.DIVINE, EmberKind.MAGICAL));
        ResourceLocation fire = ForgedSwordData.Element.FIRE.focus();
        ForgedSwordData data = new ForgedSwordData(List.of(EmberKind.ACIDIC.id(), EmberKind.CHAOS.id(), EmberKind.ACIDIC.id(), EmberKind.CHAOS.id()),
                List.of(fire, fire, fire, fire), ForgedSwordData.Element.FIRE, ForgedSwordData.Element.FIRE);
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            helper.assertTrue(anvil(ECEmberForge.FORGED_ELEMENTAL_SWORD.get().forged(data), inlay, null, player).getOutput().isEmpty(), "锻好的余烬元素剑不该给");
            helper.assertTrue(anvil(new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()), inlay, null, player).getOutput().isEmpty(), "没锻的余烬元素剑也不该给");
            helper.assertFalse(EmberInlayItem.isInlayable(new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get())), "余烬元素剑不算能嵌");
        } finally {
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /** 黑名单：两个标签都登记了（默认空）；当成在黑名单里的铁剑不给，对照的金剑照给 */
    @GameTest(template = EMPTY)
    public static void inlayAnvilRejectsBlacklist(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-ib", new Vec3(0.5D, 1D, 0.5D));
        ItemStack inlay = inlay(EmberInlayItem.Pair.of(EmberKind.ACIDIC, EmberKind.CORRUPTED));
        helper.assertTrue(ForgeRegistries.ITEMS.tags().isKnownTagName(EmberInlayItem.INLAY_BLACKLIST)
                && ForgeRegistries.ITEMS.tags().isKnownTagName(EmberInlayItem.INLAYABLE), "inlay_blacklist / inlayable 两个标签应已登记");
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            EmberInlayItem.setTestBlacklist(stack -> stack.is(Items.IRON_SWORD));
            helper.assertTrue(anvil(new ItemStack(Items.IRON_SWORD), inlay, null, player).getOutput().isEmpty(), "黑名单里的不该给");
            helper.assertFalse(anvil(new ItemStack(Items.GOLDEN_SWORD), inlay, null, player).getOutput().isEmpty(), "不在黑名单里的照给");
        } finally {
            EmberInlayItem.setTestBlacklist(null);
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /**
     * 嵌刻武器（阴燃 + 腐化 = 虚弱）近战打僵尸：结算后僵尸虚弱、身上的储存扣 50；掉的血和拿没嵌刻的剑打另一只一样（只给效果、不加伤害）
     */
    @GameTest(template = EMPTY)
    public static void inlayTriggersOnHit(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-it", new Vec3(0.5D, 1D, 0.5D));
        ItemStack sword = inlaid(Items.IRON_SWORD, EmberKind.COMMON, EmberKind.CORRUPTED);
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        Zombie zombie = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie control = sturdyZombie(helper, new Vec3(1.5D, 1D, 3.5D));
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            control.hurt(player.damageSources().playerAttack(player), 2F);
            float controlLoss = control.getMaxHealth() - control.getHealth();
            helper.assertTrue(InlayEvents.pendingCount() == 0 && controlLoss > 0F, "没嵌刻的剑打一下：掉血、不记");
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            float before = zombie.getHealth();
            boolean hurt = zombie.hurt(player.damageSources().playerAttack(player), 2F);
            helper.assertTrue(hurt && InlayEvents.pendingCount() >= 1, "这一下应打实、记下待结算");
            helper.assertFalse(zombie.hasEffect(MobEffects.WEAKNESS), "刻末结算之前还没有效果");
            InlayEvents.flush();
            helper.assertTrue(zombie.hasEffect(MobEffects.WEAKNESS), "结算后僵尸应虚弱");
            helper.assertTrue(mru(storage) == 950, "身上的储存应扣 50，剩 " + mru(storage));
            helper.assertTrue(Math.abs(before - zombie.getHealth() - controlLoss) < EPS,
                    "只给效果、不加伤害：应和对照一样掉 " + controlLoss + "，实际 " + (before - zombie.getHealth()));
            helper.assertFalse(control.hasEffect(MobEffects.WEAKNESS), "对照不该虚弱");
        } finally {
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /** 身上没有储存、武器自己也没 MRU：扣不起就什么都不触发 */
    @GameTest(template = EMPTY)
    public static void inlayNeedsMru(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-im", new Vec3(0.5D, 1D, 0.5D));
        player.setItemInHand(InteractionHand.MAIN_HAND, inlaid(Items.IRON_SWORD, EmberKind.COMMON, EmberKind.CORRUPTED));
        Zombie zombie = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            zombie.hurt(player.damageSources().playerAttack(player), 2F);
            InlayEvents.flush();
            helper.assertFalse(zombie.hasEffect(MobEffects.WEAKNESS), "扣不起 MRU 不该触发");
        } finally {
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /**
     * 攻击在 LivingAttackEvent 被拦下：不记、不触发、不扣；伤害事件到了以后又被排在后面的监听器取消（LOWEST）：记下了，但结算时不算、不扣
     */
    @GameTest(template = EMPTY)
    public static void inlayBlockedHits(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-ix", new Vec3(0.5D, 1D, 0.5D));
        player.setItemInHand(InteractionHand.MAIN_HAND, inlaid(Items.IRON_SWORD, EmberKind.COMMON, EmberKind.CORRUPTED));
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        Zombie guarded = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie late = sturdyZombie(helper, new Vec3(1.5D, 1D, 3.5D));
        Runnable offAttack = listen(EventPriority.NORMAL, LivingAttackEvent.class, event -> {
            if (event.getEntity() == guarded) {
                event.setCanceled(true);
            }
        });
        Runnable offDamage = listen(EventPriority.LOWEST, LivingDamageEvent.class, event -> {
            if (event.getEntity() == late) {
                event.setCanceled(true);
            }
        });
        int pendingAfterGuarded;
        int pendingAfterLate;
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            InlayEvents.flush();
            guarded.hurt(player.damageSources().playerAttack(player), 2F);
            pendingAfterGuarded = InlayEvents.pendingCount();
            late.hurt(player.damageSources().playerAttack(player), 2F);
            pendingAfterLate = InlayEvents.pendingCount();
            InlayEvents.flush();
        } finally {
            offAttack.run();
            offDamage.run();
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.assertTrue(pendingAfterGuarded == 0, "攻击被拦下不该记，实际 " + pendingAfterGuarded);
        helper.assertTrue(pendingAfterLate == 1, "伤害事件到了应先记下，实际 " + pendingAfterLate);
        helper.assertFalse(guarded.hasEffect(MobEffects.WEAKNESS) || late.hasEffect(MobEffects.WEAKNESS), "两只都不该虚弱");
        helper.assertTrue(mru(storage) == 1000, "两下都不该扣 MRU，剩 " + mru(storage));
        helper.succeed();
    }

    /** 再嵌：换成新的一对（旧的不退）；嵌着同一对时不给（免得白花经验） */
    @GameTest(template = EMPTY)
    public static void inlayReplaces(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-ir", new Vec3(0.5D, 1D, 0.5D));
        EmberInlayItem.Pair first = EmberInlayItem.Pair.of(EmberKind.COMMON, EmberKind.CORRUPTED);
        EmberInlayItem.Pair second = EmberInlayItem.Pair.of(EmberKind.DIVINE, EmberKind.FLAME);
        ItemStack sword = inlaid(Items.IRON_SWORD, EmberKind.COMMON, EmberKind.CORRUPTED);
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            ItemStack out = anvil(sword, inlay(second), null, player).getOutput();
            helper.assertTrue(EmberInlayItem.inlayOf(out).equals(Optional.of(second)), "应换成神圣 + 燃烧，实际 " + out.getTag());
            helper.assertTrue(anvil(sword, inlay(first), null, player).getOutput().isEmpty(), "嵌着同一对不该给");
        } finally {
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.succeed();
    }

    /** 弓：主手是嵌刻的弓、箭是自己射的 → 结算后触发；主手换成嵌刻的剑时，箭打中不算（只认弓弩） */
    @GameTest(template = EMPTY)
    public static void inlayBowArrows(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-iw", new Vec3(0.5D, 1D, 0.5D));
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        Zombie hit = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie other = sturdyZombie(helper, new Vec3(1.5D, 1D, 3.5D));
        Arrow arrow = new Arrow(helper.getLevel(), player);
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            player.setItemInHand(InteractionHand.MAIN_HAND, inlaid(Items.BOW, EmberKind.COMMON, EmberKind.CORRUPTED));
            hit.hurt(player.damageSources().arrow(arrow, player), 2F);
            InlayEvents.flush();
            player.setItemInHand(InteractionHand.MAIN_HAND, inlaid(Items.IRON_SWORD, EmberKind.COMMON, EmberKind.CORRUPTED));
            other.hurt(player.damageSources().arrow(arrow, player), 2F);
            InlayEvents.flush();
        } finally {
            arrow.discard();
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.assertTrue(hit.hasEffect(MobEffects.WEAKNESS), "嵌刻的弓射出的箭打实应触发");
        helper.assertFalse(other.hasEffect(MobEffects.WEAKNESS), "主手不是弓弩时箭打中不算");
        helper.assertTrue(mru(storage) == 950, "只扣一次 50，剩 " + mru(storage));
        helper.succeed();
    }

    /**
     * ν 护身符的连锁伤害不算武器打的（它在 {@code BaubleEffects.asBaubleDamage} 里打出）：戴 ν、主手嵌刻剑打死一只僵尸，
     * 连锁打到旁边两只；只记打死的那一下、只结算一次、扣一次 50，连锁打到的不虚弱
     */
    @GameTest(template = EMPTY_LARGE)
    public static void inlayIgnoresNuChain(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-nu", new Vec3(5.5D, 1D, 5.5D));
        wearBauble(player, ECBaubles.NU_AMULET.get());
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        player.setItemInHand(InteractionHand.MAIN_HAND, inlaid(Items.IRON_SWORD, EmberKind.COMMON, EmberKind.CORRUPTED));
        Zombie victim = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(6.5D, 1D, 5.5D));
        victim.setHealth(1F);
        Zombie left = sturdyZombie(helper, new Vec3(3.5D, 1D, 5.5D));
        Zombie right = sturdyZombie(helper, new Vec3(5.5D, 1D, 7.5D));
        int pending;
        int fired;
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            InlayEvents.flush();
            victim.hurt(player.damageSources().playerAttack(player), 2F);
            pending = InlayEvents.pendingCount();
            fired = InlayEvents.flush();
        } finally {
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.assertFalse(victim.isAlive(), "前提：这一下应打死它");
        helper.assertTrue(left.getHealth() < 200F && right.getHealth() < 200F,
                "前提：ν 的连锁应打到旁边两只，实际 " + left.getHealth() + " / " + right.getHealth());
        helper.assertTrue(pending == 1 && fired == 1, "只该记、只该结算打死的那一下，实际记 " + pending + "、结算 " + fired);
        helper.assertTrue(mru(storage) == 950, "只扣一次 50，剩 " + mru(storage));
        helper.assertFalse(left.hasEffect(MobEffects.WEAKNESS) || right.hasEffect(MobEffects.WEAKNESS), "连锁打到的不该虚弱");
        helper.succeed();
    }

    /**
     * 尖刺圆盾举满时的范围伤害不算武器打的（{@code InlayEvents.notWeaponHit}）：主手是嵌刻剑，spike 打中两只僵尸，不记、不扣、不虚弱；
     * 对照：同一把剑直接打其中一只照常触发
     */
    @GameTest(template = EMPTY_LARGE)
    public static void inlayIgnoresSpikyShield(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-ip", new Vec3(5.5D, 1D, 5.5D));
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        player.setItemInHand(InteractionHand.MAIN_HAND, inlaid(Items.IRON_SWORD, EmberKind.COMMON, EmberKind.CORRUPTED));
        Zombie first = sturdyZombie(helper, new Vec3(3.5D, 1D, 5.5D));
        Zombie second = sturdyZombie(helper, new Vec3(7.5D, 1D, 5.5D));
        int hit;
        int pendingAfterSpike;
        int mruAfterSpike;
        boolean weakAfterSpike;
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            InlayEvents.flush();
            hit = SpikyShieldItem.spike(helper.getLevel(), player);
            pendingAfterSpike = InlayEvents.pendingCount();
            InlayEvents.flush();
            mruAfterSpike = mru(storage);
            weakAfterSpike = first.hasEffect(MobEffects.WEAKNESS) || second.hasEffect(MobEffects.WEAKNESS);

            first.invulnerableTime = 0;
            first.hurt(player.damageSources().playerAttack(player), 2F);
            InlayEvents.flush();
        } finally {
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.assertTrue(hit >= 2 && first.getHealth() < 200F && second.getHealth() < 200F, "前提：尖刺应打中两只，实际 " + hit);
        helper.assertTrue(pendingAfterSpike == 0, "尖刺圆盾的伤害不该记，实际 " + pendingAfterSpike);
        helper.assertTrue(mruAfterSpike == 1000 && !weakAfterSpike, "尖刺圆盾的伤害不该扣 MRU、不该触发，剩 " + mruAfterSpike);
        helper.assertTrue(first.hasEffect(MobEffects.WEAKNESS) && mru(storage) == 950, "对照：剑直接打照常触发、扣 50，剩 " + mru(storage));
        helper.succeed();
    }

    /**
     * 同一刻对同一个生物的几段伤害只结算一次：一只僵尸挨两下（中间清掉无敌帧）→ 记两条、只结算一次；
     * 另一只的第一下在 LivingDamageEvent 被取消（没打实）、第二下打实 → 第二下顶上，同样结算一次。合计结算 2 次、扣 100
     */
    @GameTest(template = EMPTY)
    public static void inlayOncePerTargetPerTick(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-io", new Vec3(0.5D, 1D, 0.5D));
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        player.setItemInHand(InteractionHand.MAIN_HAND, inlaid(Items.IRON_SWORD, EmberKind.COMMON, EmberKind.CORRUPTED));
        Zombie twice = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie retry = sturdyZombie(helper, new Vec3(1.5D, 1D, 3.5D));
        DamageSource blocked = player.damageSources().playerAttack(player);
        Runnable off = listen(EventPriority.LOWEST, LivingDamageEvent.class, event -> {
            if (event.getSource() == blocked) {
                event.setCanceled(true);
            }
        });
        int pending;
        int fired;
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            InlayEvents.flush();
            twice.hurt(player.damageSources().playerAttack(player), 2F);
            twice.invulnerableTime = 0;
            twice.hurt(player.damageSources().playerAttack(player), 2F);
            retry.hurt(blocked, 2F);
            retry.invulnerableTime = 0;
            retry.hurt(player.damageSources().playerAttack(player), 2F);
            pending = InlayEvents.pendingCount();
            fired = InlayEvents.flush();
        } finally {
            off.run();
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.assertTrue(pending == 4, "四下伤害事件都该先记下，实际 " + pending);
        helper.assertTrue(fired == 2, "每只只该结算一次，实际 " + fired);
        helper.assertTrue(mru(storage) == 900, "两只各扣一次 50，剩 " + mru(storage));
        helper.assertTrue(twice.hasEffect(MobEffects.WEAKNESS) && retry.hasEffect(MobEffects.WEAKNESS), "两只都该虚弱（第二只靠第二下顶上）");
        helper.succeed();
    }

    /**
     * 伤害吸收整下吃掉的一下算打实：僵尸有 20 点伤害吸收，嵌刻剑打 2 点 → LivingDamageEvent 里是 0、生命不掉、伤害吸收少了，
     * 结算后照样虚弱、扣 50
     */
    @GameTest(template = EMPTY)
    public static void inlayAbsorbedHitCounts(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-ib", new Vec3(0.5D, 1D, 0.5D));
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        player.setItemInHand(InteractionHand.MAIN_HAND, inlaid(Items.IRON_SWORD, EmberKind.COMMON, EmberKind.CORRUPTED));
        Zombie zombie = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        zombie.setAbsorptionAmount(20F);
        float[] damage = {-1F};
        Runnable off = listen(EventPriority.LOWEST, LivingDamageEvent.class, event -> {
            if (event.getEntity() == zombie) {
                damage[0] = event.getAmount();
            }
        });
        int pending;
        int fired;
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            InlayEvents.flush();
            zombie.hurt(player.damageSources().playerAttack(player), 2F);
            pending = InlayEvents.pendingCount();
            fired = InlayEvents.flush();
        } finally {
            off.run();
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.assertTrue(damage[0] == 0F, "前提：LivingDamageEvent 里的伤害应是 0（被伤害吸收吃掉），实际 " + damage[0]);
        helper.assertTrue(zombie.getHealth() == 200F && zombie.getAbsorptionAmount() < 20F,
                "前提：生命不掉、伤害吸收少了，实际生命 " + zombie.getHealth() + "、吸收 " + zombie.getAbsorptionAmount());
        helper.assertTrue(pending == 1 && fired == 1, "被伤害吸收吃掉的一下应记下、结算，实际记 " + pending + "、结算 " + fired);
        helper.assertTrue(zombie.hasEffect(MobEffects.WEAKNESS) && mru(storage) == 950, "应虚弱、扣 50，剩 " + mru(storage));
        helper.succeed();
    }

    /**
     * 对照：僵尸同样有伤害吸收，原伤害的 LivingHurtEvent 被排在后面的监听器取消（伤害吸收一点没少），再按混合端的顺序手动发
     * {@code LivingDamageEvent(0)}：不记、不触发、不扣（不能把「事后照发的 0」当成被伤害吸收吃掉）
     */
    @GameTest(template = EMPTY)
    public static void inlayCanceledHurtNotAbsorbed(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-wm-ic", new Vec3(0.5D, 1D, 0.5D));
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        player.setItemInHand(InteractionHand.MAIN_HAND, inlaid(Items.IRON_SWORD, EmberKind.COMMON, EmberKind.CORRUPTED));
        Zombie zombie = sturdyZombie(helper, new Vec3(2.5D, 1D, 2.5D));
        zombie.setAbsorptionAmount(20F);
        DamageSource source = player.damageSources().playerAttack(player);
        Runnable off = listen(EventPriority.LOWEST, LivingHurtEvent.class, event -> {
            if (event.getSource() == source) {
                event.setCanceled(true);
            }
        });
        int pending;
        try {
            EmberInlayItem.Config.setTestOverrides(true, 50, 10);
            InlayEvents.flush();
            zombie.hurt(source, 2F);
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(zombie, source, 0F));
            pending = InlayEvents.pendingCount();
            InlayEvents.flush();
        } finally {
            off.run();
            EmberInlayItem.Config.setTestOverrides(null, null, null);
        }
        helper.assertTrue(zombie.getAbsorptionAmount() == 20F, "前提：取消以后伤害吸收不该少，实际 " + zombie.getAbsorptionAmount());
        helper.assertTrue(pending == 0, "LivingHurtEvent 被取消的一下不该记，实际 " + pending);
        helper.assertFalse(zombie.hasEffect(MobEffects.WEAKNESS), "不该虚弱");
        helper.assertTrue(mru(storage) == 1000, "不该扣 MRU，剩 " + mru(storage));
        helper.succeed();
    }

    // ================================================================== 二分枪对 Boss

    /**
     * 首领模式（测试里把僵尸当首领：原版首领挂不上状态效果或探测不过去）：时长 0 时不受影响；默认时长 200 时挂 200 刻二分、生命不减半、没有虚弱
     */
    @GameTest(template = EMPTY_LARGE)
    public static void dividerBossModeBisects(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-db", new Vec3(1.5D, 1D, 1.5D));
        Zombie boss = sturdyZombie(helper, new Vec3(5.5D, 1D, 5.5D));
        try {
            DividerGunItem.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            ECHoloWeapons.Config.setTestOverrides(withBossMode(0));
            helper.assertFalse(DividerGunItem.isBossModeTarget(boss) || DividerGunItem.isTarget(boss), "时长 0：当成首领的僵尸哪种目标都不是");
            gunField(helper, shooter).detonate();
            helper.assertTrue(full(boss) && !boss.hasEffect(ECEffects.BISECTED.get()), "时长 0：首领不受影响");

            ECHoloWeapons.Config.setTestOverrides(withBossMode(200));
            helper.assertTrue(DividerGunItem.isBossModeTarget(boss) && !DividerGunItem.isTarget(boss), "默认：首领走首领模式、不算减半目标");
            gunField(helper, shooter).detonate();
            MobEffectInstance bisected = boss.getEffect(ECEffects.BISECTED.get());
            helper.assertTrue(bisected != null && bisected.getDuration() == 200, "应挂 200 刻二分，实际 " + bisected);
            helper.assertTrue(full(boss), "生命不减半，实际 " + boss.getHealth());
            helper.assertFalse(boss.hasEffect(MobEffects.WEAKNESS) || boss.hasEffect(MobEffects.BLINDNESS), "首领模式不挂减半那几种效果");
        } finally {
            ECHoloWeapons.Config.setTestOverrides(null);
            DividerGunItem.setTestBossOverride(null);
        }
        helper.succeed();
    }

    /** 带二分的首领（当成首领的僵尸）打玩家：伤害是挂二分之前那一下的一半 */
    @GameTest(template = EMPTY_LARGE)
    public static void dividerBisectedHalvesDamage(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-dh", new Vec3(1.5D, 1D, 1.5D));
        ServerPlayer before = player(helper, "ec5-wm-dh1", new Vec3(2.5D, 1D, 1.5D));
        ServerPlayer after = player(helper, "ec5-wm-dh2", new Vec3(3.5D, 1D, 1.5D));
        Zombie boss = sturdyZombie(helper, new Vec3(5.5D, 1D, 5.5D));
        float plainLoss;
        float halvedLoss;
        try {
            DividerGunItem.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            ECHoloWeapons.Config.setTestOverrides(withBossMode(200));
            before.hurt(before.damageSources().mobAttack(boss), 8F);
            plainLoss = before.getMaxHealth() - before.getHealth();
            gunField(helper, shooter).detonate();
            helper.assertTrue(boss.hasEffect(ECEffects.BISECTED.get()), "首领应先挂上二分");
            after.hurt(after.damageSources().mobAttack(boss), 8F);
            halvedLoss = after.getMaxHealth() - after.getHealth();
        } finally {
            ECHoloWeapons.Config.setTestOverrides(null);
            DividerGunItem.setTestBossOverride(null);
        }
        helper.assertTrue(plainLoss > 0F, "对照那一下应掉血（难度不是和平），实际 " + plainLoss);
        helper.assertTrue(Math.abs(halvedLoss - plainLoss / 2F) < EPS, "带二分时伤害应减半：" + plainLoss + " → " + halvedLoss);
        helper.succeed();
    }

    /** 0 伤害探测在 LivingAttackEvent 被拦下：不挂二分（当成首领的僵尸挂得上效果，所以这条不会空转通过） */
    @GameTest(template = EMPTY_LARGE)
    public static void dividerBossModeProbeDenied(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-dp", new Vec3(1.5D, 1D, 1.5D));
        Zombie boss = sturdyZombie(helper, new Vec3(5.5D, 1D, 5.5D));
        Runnable off = listen(EventPriority.NORMAL, LivingAttackEvent.class, event -> {
            if (event.getEntity() == boss) {
                event.setCanceled(true);
            }
        });
        try {
            DividerGunItem.setTestBossOverride(type -> type == EntityType.ZOMBIE);
            ECHoloWeapons.Config.setTestOverrides(withBossMode(200));
            helper.assertTrue(DividerGunItem.isBossModeTarget(boss), "前提：僵尸被当成首领模式的对象");
            gunField(helper, shooter).detonate();
            helper.assertFalse(boss.hasEffect(ECEffects.BISECTED.get()), "探测被拦下时不该挂二分");
        } finally {
            off.run();
            ECHoloWeapons.Config.setTestOverrides(null);
            DividerGunItem.setTestBossOverride(null);
        }
        helper.succeed();
    }

    /**
     * 原版凋灵对状态效果免疫（{@code WitherBoss.addEffect} 一律返回 false）：首领模式只走 addEffect，照它的设计不吃——
     * 它是首领模式的对象、0 伤害探测也放行了（LivingHurtEvent 到了），但不挂二分、生命不变
     */
    @GameTest(template = EMPTY_LARGE)
    public static void dividerBossModeWitherImmune(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-dw", new Vec3(1.5D, 1D, 1.5D));
        WitherBoss wither = helper.spawnWithNoFreeWill(EntityType.WITHER, new Vec3(5.5D, 1D, 5.5D));
        int[] probed = new int[1];
        Runnable off = listen(EventPriority.LOWEST, LivingHurtEvent.class, event -> {
            if (event.getEntity() == wither) {
                probed[0]++;
            }
        });
        try {
            ECHoloWeapons.Config.setTestOverrides(withBossMode(200));
            helper.assertTrue(DividerGunItem.isBossModeTarget(wither) && !DividerGunItem.isTarget(wither), "前提：凋灵是首领模式的对象");
            gunField(helper, shooter).detonate();
            helper.assertTrue(probed[0] == 1, "前提：探测应放行（LivingHurtEvent 到了一次），实际 " + probed[0]);
            helper.assertFalse(wither.hasEffect(ECEffects.BISECTED.get()), "凋灵对状态效果免疫，不该挂上二分");
            helper.assertTrue(full(wither) && wither.getActiveEffects().isEmpty(), "凋灵不该受影响，生命 " + wither.getHealth() + "、效果 " + wither.getActiveEffects());
        } finally {
            off.run();
            ECHoloWeapons.Config.setTestOverrides(null);
            wither.discard();
        }
        helper.succeed();
    }

    /** 全息投影在 divider_immune 里：首领模式也不碰它（不挂二分、生命不变） */
    @GameTest(template = EMPTY_LARGE)
    public static void dividerHologramImmune(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-wm-dg", new Vec3(1.5D, 1D, 1.5D));
        HologramEntity hologram = ECHologram.HOLOGRAM.get().create(helper.getLevel());
        if (hologram == null) {
            throw new IllegalStateException("全息投影没造出来");
        }
        Vec3 center = helper.absoluteVec(new Vec3(5.5D, 1D, 5.5D));
        hologram.moveTo(center.x, center.y, center.z, 0F, 0F);
        try {
            ECHoloWeapons.Config.setTestOverrides(withBossMode(200));
            helper.assertTrue(hologram.getType().is(ECHoloWeapons.DIVIDER_IMMUNE), "全息投影应在 divider_immune 里");
            helper.assertFalse(DividerGunItem.isBossModeTarget(hologram) || DividerGunItem.isTarget(hologram), "全息投影不是任何一种目标");
            float health = hologram.getHealth();
            gunField(helper, shooter).detonate(List.of(hologram));
            helper.assertTrue(hologram.getHealth() == health && hologram.getActiveEffects().isEmpty(), "全息投影不该受影响");
        } finally {
            ECHoloWeapons.Config.setTestOverrides(null);
            hologram.discard();
        }
        helper.succeed();
    }

    // ================================================================== 轨道锁定

    /**
     * 跟随：打击点跟着僵尸水平移动（高度不变）；落下前 10 刻（延迟 40，第 30 刻起）定住，之后僵尸再走也不跟
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 100)
    public static void orbitalLockFollows(GameTestHelper helper) {
        ServerPlayer user = player(helper, "ec5-wm-of", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = sturdyZombie(helper, new Vec3(3.5D, 1D, 3.5D));
        zombie.setNoAi(true);
        OrbitalStrikeEntity strike = OrbitalStrikeEntity.spawn(helper.getLevel(), zombie.position(), 0F, 0F, 40, user, living -> false, zombie);
        if (strike == null) {
            throw new IllegalStateException("打击没放下");
        }
        double y = strike.getY();
        double[] frozenAt = new double[1];
        helper.runAfterDelay(5, () -> zombie.setPos(zombie.getX() + 1D, zombie.getY(), zombie.getZ()));
        helper.runAfterDelay(10, () -> helper.assertTrue(Math.abs(strike.getX() - zombie.getX()) < EPS && Math.abs(strike.getZ() - zombie.getZ()) < EPS,
                "打击点应跟到僵尸脚下：" + strike.position() + " / " + zombie.position()));
        helper.runAfterDelay(15, () -> zombie.setPos(zombie.getX(), zombie.getY(), zombie.getZ() + 1D));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(Math.abs(strike.getZ() - zombie.getZ()) < EPS && strike.follow() == zombie, "第二次挪动也应跟上");
            helper.assertTrue(strike.getY() == y, "高度不变");
            frozenAt[0] = strike.getX();
        });
        helper.runAfterDelay(32, () -> zombie.setPos(zombie.getX() + 2D, zombie.getY(), zombie.getZ()));
        helper.runAfterDelay(36, () -> {
            helper.assertTrue(strike.follow() == null, "落下前 10 刻应定住、不再跟");
            helper.assertTrue(strike.getX() == frozenAt[0] && zombie.getX() - strike.getX() > 1.5D,
                    "定住以后僵尸再走不跟：打击 " + strike.position() + " / 僵尸 " + zombie.position());
            strike.discard();
            zombie.discard();
            helper.succeed();
        });
    }

    /** 目标被移除：打击停在最后跟到的位置 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 100)
    public static void orbitalLockTargetRemoved(GameTestHelper helper) {
        ServerPlayer user = player(helper, "ec5-wm-or", new Vec3(0.5D, 1D, 0.5D));
        Zombie zombie = sturdyZombie(helper, new Vec3(3.5D, 1D, 3.5D));
        zombie.setNoAi(true);
        OrbitalStrikeEntity strike = OrbitalStrikeEntity.spawn(helper.getLevel(), zombie.position(), 0F, 0F, 40, user, living -> false, zombie);
        if (strike == null) {
            throw new IllegalStateException("打击没放下");
        }
        Vec3[] last = new Vec3[1];
        helper.runAfterDelay(5, () -> zombie.setPos(zombie.getX() + 1.5D, zombie.getY(), zombie.getZ() + 0.5D));
        helper.runAfterDelay(9, () -> {
            last[0] = zombie.position();
            zombie.discard();
        });
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(strike.follow() == null, "目标没了应放开");
            helper.assertTrue(Math.abs(strike.getX() - last[0].x) < EPS && Math.abs(strike.getZ() - last[0].z) < EPS,
                    "应停在最后跟到的位置 " + last[0] + "，实际 " + strike.position());
            helper.assertFalse(strike.isRemoved(), "打击照常倒数（还没到点）");
            strike.discard();
            helper.succeed();
        });
    }

    /** 遥控器：准星对着僵尸时打击放在它脚下并跟着它；僵尸没了、对着石头时照旧打那一格顶上、不跟；锁定距离 0 时对着僵尸也打方块 */
    @GameTest(template = EMPTY_LARGE)
    public static void orbitalRemoteLockOrGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(5, 2, 8), Blocks.STONE);
        ServerPlayer user = player(helper, "ec5-wm-ol", new Vec3(5.5D, 1D, 1.5D));
        user.setYRot(0F);
        user.setXRot(0F);
        user.setYHeadRot(0F);
        Zombie zombie = sturdyZombie(helper, new Vec3(5.5D, 1D, 4.5D));
        zombie.setNoAi(true);
        ItemStack remote = new ItemStack(ECHoloWeapons.ORBITAL_REMOTE.get());
        remote.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(OrbitalRemoteItem.CAPACITY));
        user.setItemInHand(InteractionHand.MAIN_HAND, remote);
        AABB box = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(11D, 6D, 11D))).inflate(2D);
        try {
            // 每发 0 MRU、没有冷却：连放三发
            ECHoloWeapons.Config.setTestOverrides(new ECHoloWeapons.Config.Values(0, 128D, 1D, 0, 5000, false,
                    ECHoloWeapons.Config.DEFAULT_BOSS_MODE_TICKS, 32D));
            helper.assertTrue(OrbitalRemoteItem.lockTargetFor(user) == zombie, "准星对着僵尸应锁定它");
            helper.assertTrue(remote.use(level, user, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "锁定时应放下打击");
            OrbitalStrikeEntity locked = only(helper, level, box, user, null);
            helper.assertTrue(locked.follow() == zombie && locked.position().distanceToSqr(zombie.position()) < EPS, "应放在僵尸脚下、跟着它");

            ECHoloWeapons.Config.setTestOverrides(new ECHoloWeapons.Config.Values(0, 128D, 1D, 0, 5000, false,
                    ECHoloWeapons.Config.DEFAULT_BOSS_MODE_TICKS, 0D));
            helper.assertTrue(OrbitalRemoteItem.lockTargetFor(user) == null, "锁定距离 0：不锁定");
            helper.assertTrue(remote.use(level, user, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "不锁定时照旧打方块");
            OrbitalStrikeEntity ground = only(helper, level, box, user, locked);
            helper.assertTrue(ground.follow() == null, "不锁定时不跟");

            ECHoloWeapons.Config.setTestOverrides(new ECHoloWeapons.Config.Values(0, 128D, 1D, 0, 5000, false,
                    ECHoloWeapons.Config.DEFAULT_BOSS_MODE_TICKS, 32D));
            zombie.discard();
            helper.assertTrue(OrbitalRemoteItem.lockTargetFor(user) == null, "没对准生物时不锁定");
            helper.assertTrue(remote.use(level, user, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "对着石头应放下打击");
            Set<OrbitalStrikeEntity> seen = new HashSet<>(List.of(locked, ground));
            OrbitalStrikeEntity onStone = level.getEntitiesOfClass(OrbitalStrikeEntity.class, box, strike -> strike.attacker() == user && !seen.contains(strike))
                    .stream().findFirst().orElseThrow(() -> new IllegalStateException("对着石头没放下打击"));
            Vec3 top = helper.absoluteVec(new Vec3(5.5D, 3D, 8.5D));
            helper.assertTrue(onStone.follow() == null && onStone.position().distanceToSqr(top) < EPS, "应落在石头顶面正中、不跟，实际 " + onStone.position());
        } finally {
            ECHoloWeapons.Config.setTestOverrides(null);
            level.getEntitiesOfClass(OrbitalStrikeEntity.class, box, strike -> strike.attacker() == user).forEach(OrbitalStrikeEntity::discard);
            zombie.discard();
            helper.setBlock(new BlockPos(5, 2, 8), Blocks.AIR);
        }
        helper.succeed();
    }

    // ================================================================== 工具

    /** 真玩家（不在世界的实体列表里），过掉出生无敌，生存模式、满血 */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setYHeadRot(0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** 200 血的僵尸（几下射线打不死），不会动 */
    private static Zombie sturdyZombie(GameTestHelper helper, Vec3 relative) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, relative);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        zombie.setHealth(200F);
        return zombie;
    }

    /** 魔力锭手枪（伤害约 13.3），透镜 id 为 null 时不装 */
    private static GunStats lens(String lensId) {
        return GunStats.of(GunType.PISTOL, GunParts.uniform("mru").withLens(lensId));
    }

    private static ItemStack inlay(EmberInlayItem.Pair pair) {
        return ECEmberForge.EMBER_INLAY.get().create(pair);
    }

    private static ItemStack inlaid(net.minecraft.world.item.Item item, EmberKind a, EmberKind b) {
        ItemStack stack = new ItemStack(item);
        EmberInlayItem.setInlay(stack, EmberInlayItem.Pair.of(a, b));
        return stack;
    }

    private static ResourceLocation recipeId(EmberInlayItem.Pair pair) {
        String a = pair.first().id().getPath().replace("_ember", "");
        String b = pair.second().id().getPath().replace("_ember", "");
        return EssentialCraft5.id("ember_inlay/" + a + "_" + b);
    }

    /** 发一次铁砧事件（走本模组的监听器），返回事件看产物 */
    private static AnvilUpdateEvent anvil(ItemStack left, ItemStack right, String name, ServerPlayer player) {
        AnvilUpdateEvent event = new AnvilUpdateEvent(left, right, name, 0, player);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    /** 把饰品直接放进它那种槽的第 0 格（不走 canEquip，模拟指令塞进去），并让缓存作废 */
    private static void wearBauble(ServerPlayer player, SpecialBaubleItem bauble) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).resolve()
                .orElseThrow(() -> new IllegalStateException("玩家没有 Curios 饰品栏"));
        ICurioStacksHandler handler = inventory.getCurios().get(bauble.slot());
        if (handler == null || handler.getSlots() < 1) {
            throw new IllegalStateException("玩家没有 Curios 槽位 " + bauble.slot());
        }
        handler.getStacks().setStackInSlot(0, new ItemStack(bauble));
        CuriosCompat.invalidate(player);
    }

    /** 一件存着 mru 的暗之灵魂物质（储存物品，嵌刻先从它扣） */
    private static ItemStack storage(int mru) {
        ItemStack stack = new ItemStack(ECItems.DARK_SOUL_MATTER.get());
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    private static int mru(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    /** 默认配置换掉首领模式的时长 */
    private static ECHoloWeapons.Config.Values withBossMode(int ticks) {
        ECHoloWeapons.Config.Values d = ECHoloWeapons.Config.DEFAULTS;
        return new ECHoloWeapons.Config.Values(d.mruPerStrike(), d.damage(), d.playerDamageMultiplier(), d.cooldownTicks(), d.mruPerShot(),
                false, ticks, d.lockRange());
    }

    /** 测试结构正中的一个玩家射出的二分场（枪的规则 + 首领模式，要探测），延迟长到不会自己张开 */
    private static DividerFieldEntity gunField(GameTestHelper helper, LivingEntity shooter) {
        DividerFieldEntity field = DividerGunItem.spawnField(helper.getLevel(), helper.absoluteVec(new Vec3(5.5D, 1D, 5.5D)), LONG_DELAY, shooter);
        if (field == null) {
            throw new IllegalStateException("二分场没生成出来");
        }
        return field;
    }

    /** 这名使用者放下的、除了 except 以外唯一的一记打击 */
    private static OrbitalStrikeEntity only(GameTestHelper helper, ServerLevel level, AABB box, ServerPlayer user, OrbitalStrikeEntity except) {
        List<OrbitalStrikeEntity> found = level.getEntitiesOfClass(OrbitalStrikeEntity.class, box, strike -> strike.attacker() == user && strike != except);
        helper.assertTrue(found.size() == 1, "应新放下 1 记打击，实际 " + found.size());
        return found.get(0);
    }

    private static boolean full(LivingEntity living) {
        return living.getHealth() == living.getMaxHealth();
    }

    /** 挂一个只在测试期间有效的事件监听器（不收已取消的），返回摘掉它的动作 */
    private static <E extends Event> Runnable listen(EventPriority priority, Class<E> type, Consumer<E> listener) {
        MinecraftForge.EVENT_BUS.addListener(priority, false, type, listener);
        return () -> MinecraftForge.EVENT_BUS.unregister(listener);
    }
}
