package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.item.bauble.SpecialBaubleItem;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.item.module.ModuleTicker;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.item.module.combat.AccumulatorEvents;
import com.flameforge.essentialcraft5.item.module.combat.PhaseAnchorEvents;
import com.flameforge.essentialcraft5.item.module.combat.ShredEvents;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECCombatModules;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.util.ECCombatTags;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 三块战斗模块（第十批 10c-M3，batch10.md §5.12）：累加器 11 条、削减 9 条、相位锚 10 条（10c 审查后补了反伤、盾牌、受击无敌门槛和本刻末自动结算）。
 * <p>
 * 攻击者都是穿齐四件宅男盔甲、装着真模块的测试玩家（{@link ProtectionFrameworkTests#realPlayer}，不进世界、不走真 tick，先空跑 61 刻去掉出生无敌）。
 * 本刻末的结算（{@code AccumulatorEvents.flush} / {@code ShredEvents.flush}）一般在 hurt 之后直接调；正式环境只靠本刻末自动结算，
 * 这条路由 {@link #accumulatorAccruesAtTickEnd}、{@link #shredStacksAtTickEnd}、{@link #anchorRunsFromPlayerTick} 三条不手动调的守着。同一刻里「过了几刻」靠手动减目标的受击无敌模拟
 * （原版每刻减 1，{@code invulnerableTime > 10} 时同样大小的一下整下吞掉）。配置一律用各自的测试覆盖值，在同一段同步代码里设、测、置回；
 * 跨刻的两条（累加器作废、破甲到期）不改配置，按当时的配置值等。目标用把最大生命调到 1000、没有护甲的猪，掉血就是打进去的数。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CombatModuleTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    /** 末影人随机瞬移 {@code EnderMan.teleport()} 的 SRG 名（继承自原版的方法，运行期是 SRG） */
    private static final String ENDERMAN_RANDOM_TELEPORT = "m_32529_";
    private static final float DUMMY_HEALTH = 1000F;

    private CombatModuleTests() {
    }

    // ================================================================ 工具

    /** 穿齐四件宅男盔甲的真玩家：每块模块装在它能装的第一个部位上，胸甲自存 chestMru；过掉出生无敌、满血 */
    private static ServerPlayer suited(GameTestHelper helper, String name, Vec3 relative, int chestMru, ModuleType... modules) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            ItemStack stack = new ItemStack(ECComputer.armor().get(type.ordinal()).get());
            for (ModuleType module : modules) {
                if (module.parts().iterator().next() == type) {
                    stack = ArmorModules.install(stack, module);
                    if (stack.isEmpty()) {
                        throw new IllegalStateException("装不上 " + module + " 到 " + type);
                    }
                }
            }
            player.setItemSlot(type.getSlot(), stack);
        }
        setChestMru(player, chestMru);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** 没穿宅男盔甲的真玩家（过掉出生无敌、满血） */
    private static ServerPlayer plain(GameTestHelper helper, String name, Vec3 relative) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void setChestMru(ServerPlayer player, int mru) {
        player.getItemBySlot(EquipmentSlot.CHEST).getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
    }

    /** 四件宅男盔甲都充到 mru（满电的全套挡得住整下伤害） */
    private static void chargeSuit(ServerPlayer player, int mru) {
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            player.getItemBySlot(type.getSlot()).getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        }
    }

    /** 举着盾、面朝 yaw 的真玩家：blocking[0] 为真时算在格挡（只把这个状态写死，格挡本身走原版 hurt）；去掉出生无敌、满血 */
    private static ServerPlayer shieldBearer(GameTestHelper helper, String name, Vec3 relative, float yaw, boolean[] blocking) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public boolean isBlocking() {
                return blocking[0];
            }
        };
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, yaw, 0F);
        player.setYHeadRot(yaw);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** 数 victim 身上落下（到了 LivingDamageEvent）的、记在 from 头上的荆棘类反伤 */
    private static Consumer<LivingDamageEvent> countThorns(LivingEntity victim, LivingEntity from, int[] counter) {
        return event -> {
            if (event.getEntity() == victim && event.getSource().is(DamageTypes.THORNS) && event.getSource().getEntity() == from) {
                counter[0]++;
            }
        };
    }

    private static int chestMru(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    private static void cleanup(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            AccumulatorEvents.forgetForTest(player);
            ModuleTicker.forgetForTest(player);
            ModuleCache.invalidate(player);
            ECCombatTags.forget(player.getUUID());
            player.getAdvancements().stopListening();
        }
    }

    /** 最大生命 1000、满血、没有护甲的猪（不会叫增援、不怕太阳） */
    private static Pig dummy(GameTestHelper helper, BlockPos pos) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, pos);
        AttributeInstance health = pig.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(DUMMY_HEALTH);
        }
        pig.setHealth(DUMMY_HEALTH);
        return pig;
    }

    private static float lost(LivingEntity entity) {
        return DUMMY_HEALTH - entity.getHealth();
    }

    /** 本模组枪的射线（每次新建一个伤害源实例，同 RayShot） */
    private static DamageSource ray(ServerPlayer shooter) {
        return new DamageSource(ECDamageTypes.holder(shooter.level(), ECGuns.MRU_RAY), null, shooter);
    }

    private static DamageSource melee(ServerPlayer player) {
        return player.damageSources().playerAttack(player);
    }

    /** 不在受击无敌里的一下（先清掉受击无敌） */
    private static boolean land(LivingEntity target, DamageSource source, float amount) {
        target.invulnerableTime = 0;
        return target.hurt(source, amount);
    }

    private static boolean hasShred(LivingEntity entity) {
        return entity.hasEffect(ECEffects.ARMOR_SHRED.get());
    }

    private static int shredLevel(LivingEntity entity) {
        MobEffectInstance effect = entity.getEffect(ECEffects.ARMOR_SHRED.get());
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    private static boolean anchored(LivingEntity entity) {
        return entity.hasEffect(ECEffects.ANCHORED.get());
    }

    private static void setArmor(LivingEntity entity, double armor, double toughness) {
        AttributeInstance a = entity.getAttribute(Attributes.ARMOR);
        AttributeInstance t = entity.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (a != null) {
            a.setBaseValue(armor);
        }
        if (t != null) {
            t.setBaseValue(toughness);
        }
    }

    // ================================================================ 累加器

    /**
     * 加特林式的速射（每刻一发 2 点射线，打同一只目标 100 刻）：没开累加器时每 10 刻才有一发算数（10 发 = 20 点）；
     * 开着时其余被受击无敌整下吞掉的 9 发记账，补进下一发（最多翻一倍）——第一发 2 点，后面 9 发各 4 点，共 38 点，约翻倍
     */
    @GameTest(template = EMPTY)
    public static void accumulatorDoublesRapidFire(GameTestHelper helper) {
        ServerPlayer gunner = suited(helper, "acc-gat-a", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.ACCUMULATOR);
        ServerPlayer control = suited(helper, "acc-gat-b", new Vec3(4.5D, 1D, 0.5D), 0);
        Pig boosted = dummy(helper, new BlockPos(1, 1, 3));
        Pig normal = dummy(helper, new BlockPos(3, 1, 3));
        try {
            AccumulatorEvents.flush();
            for (int t = 0; t < 100; t++) {
                boosted.hurt(ray(gunner), 2F);
                normal.hurt(ray(control), 2F);
                AccumulatorEvents.flush();
                boosted.invulnerableTime = Math.max(0, boosted.invulnerableTime - 1);
                normal.invulnerableTime = Math.max(0, normal.invulnerableTime - 1);
            }
            helper.assertTrue(lost(normal) == 20F, "对照：没开累加器 100 刻只有 10 发算数，应掉 20，实际 " + lost(normal));
            helper.assertTrue(lost(boosted) == 38F, "开着累加器：第一发 2 + 后面 9 发各 4 = 38，实际 " + lost(boosted));
            float ratio = lost(boosted) / lost(normal);
            helper.assertTrue(ratio >= 1.8F && ratio <= 2.0F, "有效伤害应约翻倍，实际 ×" + ratio);
        } finally {
            cleanup(gunner, control);
        }
        helper.succeed();
    }

    /** 结算最多补这一下的 max_ratio 倍：攒了 12，这一下 4 点 → 默认 1.0 补 4（共 8）；max_ratio 0.5 时只补 2（共 6），多的作废 */
    @GameTest(template = EMPTY)
    public static void accumulatorCapsAtMaxRatio(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "acc-cap", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.ACCUMULATOR);
        Pig full = dummy(helper, new BlockPos(1, 1, 3));
        Pig half = dummy(helper, new BlockPos(3, 1, 3));
        try {
            for (Pig pig : List.of(full, half)) {
                land(pig, melee(player), 4F);
                for (int i = 0; i < 3; i++) {
                    pig.hurt(melee(player), 4F);
                    AccumulatorEvents.flush();
                }
                helper.assertTrue(AccumulatorEvents.balance(player, pig) == 12F, "前提：被吞掉的三下记了 12，实际 " + AccumulatorEvents.balance(player, pig));
            }
            float before = lost(full);
            land(full, melee(player), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(lost(full) - before == 8F, "max_ratio 1.0：4 + min(12, 4) = 8，实际 " + (lost(full) - before));
            helper.assertTrue(AccumulatorEvents.balance(player, full) == 0F, "结算以后这笔账清掉（多的作废）");

            AccumulatorEvents.Config.setTestOverrides(new AccumulatorEvents.Config.Values(0.5D, 40, 32));
            try {
                before = lost(half);
                land(half, melee(player), 4F);
                AccumulatorEvents.flush();
                helper.assertTrue(lost(half) - before == 6F, "max_ratio 0.5：4 + min(12, 2) = 6，实际 " + (lost(half) - before));
            } finally {
                AccumulatorEvents.Config.setTestOverrides(null);
            }
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** forget_ticks 刻没再记账就作废：到期前还在，过了期这一下只打出它自己的数 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void accumulatorForgetsAfterIdle(GameTestHelper helper) {
        int forget = AccumulatorEvents.Config.values().forgetTicks();
        if (forget + 20 > 400) {
            helper.fail("forget_ticks 是 " + forget + "，这条测试只等得了 380 刻（默认 40）");
            return;
        }
        ServerPlayer player = suited(helper, "acc-forget", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.ACCUMULATOR);
        Pig pig = dummy(helper, new BlockPos(2, 1, 3));
        land(pig, melee(player), 4F);
        pig.hurt(melee(player), 4F);
        AccumulatorEvents.flush();
        if (AccumulatorEvents.balance(player, pig) != 4F) {
            cleanup(player);
            helper.fail("前提：被吞掉的一下记了 4，实际 " + AccumulatorEvents.balance(player, pig));
            return;
        }
        helper.runAfterDelay(Math.max(1, forget - 5), () -> {
            if (AccumulatorEvents.balance(player, pig) != 4F) {
                cleanup(player);
                helper.fail("到期前（" + (forget - 5) + " 刻）账还应在，实际 " + AccumulatorEvents.balance(player, pig));
            }
        });
        helper.runAfterDelay(forget + 2, () -> {
            try {
                helper.assertTrue(AccumulatorEvents.balance(player, pig) == 0F, "过了 " + forget + " 刻账应作废，实际 " + AccumulatorEvents.balance(player, pig));
                float before = pig.getHealth();
                land(pig, melee(player), 4F);
                AccumulatorEvents.flush();
                helper.assertTrue(before - pig.getHealth() == 4F, "作废以后这一下只打 4，实际 " + (before - pig.getHealth()));
            } finally {
                cleanup(player);
            }
            helper.succeed();
        });
    }

    /** 戴着 ζ：打实的一下当场清掉目标的受击无敌，紧跟的一下照样打实、没有被吞掉的，累加器不记账；摘掉 ζ 后紧跟的一下被吞掉、记账 */
    @GameTest(template = EMPTY)
    public static void accumulatorIdleWithZeta(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "acc-zeta", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.ACCUMULATOR);
        Pig withZeta = dummy(helper, new BlockPos(1, 1, 3));
        Pig withoutZeta = dummy(helper, new BlockPos(3, 1, 3));
        SpecialBaubleItem zeta = ECBaubles.ZETA_AMULET.get();
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            cleanup(player);
            helper.fail("玩家没有 EC5 玩家数据");
            return;
        }
        try {
            CuriosApi.getCuriosInventory(player).resolve().orElseThrow().setEquippedCurio(zeta.slot(), 0, new ItemStack(zeta));
            CuriosCompat.invalidate(player);
            data.setUBMRU(10_000);
            land(withZeta, melee(player), 4F);
            withZeta.hurt(melee(player), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(lost(withZeta) == 8F, "前提：戴着 ζ 两下都打实（8），实际 " + lost(withZeta));
            helper.assertTrue(AccumulatorEvents.balance(player, withZeta) == 0F, "戴着 ζ 没有被吞掉的，不记账，实际 " + AccumulatorEvents.balance(player, withZeta));

            CuriosApi.getCuriosInventory(player).resolve().orElseThrow().setEquippedCurio(zeta.slot(), 0, ItemStack.EMPTY);
            CuriosCompat.invalidate(player);
            land(withoutZeta, melee(player), 4F);
            withoutZeta.hurt(melee(player), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(lost(withoutZeta) == 4F, "对照：摘掉 ζ 后紧跟的一下被受击无敌吞掉，实际掉 " + lost(withoutZeta));
            helper.assertTrue(AccumulatorEvents.balance(player, withoutZeta) == 4F, "对照：被吞掉的那一下记账 4，实际 " + AccumulatorEvents.balance(player, withoutZeta));
        } finally {
            CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> handler.setEquippedCurio(zeta.slot(), 0, ItemStack.EMPTY));
            CuriosCompat.invalidate(player);
            cleanup(player);
        }
        helper.succeed();
    }

    /** 全息投影的单次封顶照样生效：攒了 30、这一下 30，并进去是 60，最终只掉封顶那么多；账照样清掉 */
    @GameTest(template = EMPTY)
    public static void accumulatorRespectsHologramCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        double cap = ECHologram.Config.values().maxDamagePerHit();
        if (!(cap > 0D)) {
            helper.fail("前提：全息投影默认有单次封顶，实际 " + cap);
            return;
        }
        HologramEntity boss = ECHologram.HOLOGRAM.get().create(level);
        if (boss == null) {
            helper.fail("建不出全息投影");
            return;
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        boss.moveTo(at.x, at.y, at.z, 0F, 0F);
        boss.setNoAi(true);
        boss.applyConfiguredHealth();
        level.addFreshEntity(boss);
        ServerPlayer player = suited(helper, "acc-holo", new Vec3(4.5D, 1D, 4.5D), 0, ECCombatModules.ACCUMULATOR);
        float hit = (float) (cap * 0.75D);
        try {
            boss.setTestState(HologramEntity.REST, 0, 100, null);
            float full = boss.getHealth();
            land(boss, melee(player), hit);
            helper.assertTrue(full - boss.getHealth() == hit, "前提：第一下打实 " + hit + "，实际 " + (full - boss.getHealth()));
            boss.hurt(melee(player), hit);
            AccumulatorEvents.flush();
            helper.assertTrue(AccumulatorEvents.balance(player, boss) == hit, "前提：被吞掉的一下记账 " + hit + "，实际 " + AccumulatorEvents.balance(player, boss));
            float before = boss.getHealth();
            land(boss, melee(player), hit);
            AccumulatorEvents.flush();
            helper.assertTrue(before - boss.getHealth() == (float) cap,
                    "并进去 " + (hit * 2) + "，最终应只掉单次封顶 " + cap + "，实际 " + (before - boss.getHealth()));
            helper.assertTrue(AccumulatorEvents.balance(player, boss) == 0F, "打实以后账清掉");
        } finally {
            boss.discard();
            cleanup(player);
        }
        helper.succeed();
    }

    /** 带 bypasses_cooldown 的伤害不受受击无敌限制、不会被整下吞掉，不记账；同一刻普通的一下被吞掉照记 */
    @GameTest(template = EMPTY)
    public static void accumulatorSkipsBypassCooldown(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "acc-bypass", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.ACCUMULATOR);
        Pig pig = dummy(helper, new BlockPos(2, 1, 3));
        // 原版没有伤害类型在 bypasses_cooldown 标签里（数据包才加），这里让一个近战伤害源自己认这个标签
        DamageSource bypass = new DamageSource(player.damageSources().playerAttack(player).typeHolder(), player, player) {
            @Override
            public boolean is(TagKey<DamageType> tag) {
                return tag == DamageTypeTags.BYPASSES_COOLDOWN || super.is(tag);
            }
        };
        try {
            land(pig, melee(player), 4F);
            float before = lost(pig);
            pig.hurt(bypass, 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(lost(pig) - before == 4F, "前提：bypasses_cooldown 的一下在受击无敌里照样打实，实际 " + (lost(pig) - before));
            helper.assertTrue(AccumulatorEvents.balance(player, pig) == 0F, "bypasses_cooldown 的不记账，实际 " + AccumulatorEvents.balance(player, pig));
            pig.hurt(melee(player), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(AccumulatorEvents.balance(player, pig) == 4F, "对照：普通的一下被吞掉照记 4，实际 " + AccumulatorEvents.balance(player, pig));
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 近战按这一下自己的输入值算：攒了 3、这一下 5 → 补 3（共 8）；攒了 6、这一下 2 → 只补 2（共 4，最多翻一倍）；
     * 只打出差额的那种（受击无敌里比上一下重）不记账
     */
    @GameTest(template = EMPTY)
    public static void accumulatorMeleeUsesHitInput(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "acc-melee", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.ACCUMULATOR);
        Pig big = dummy(helper, new BlockPos(1, 1, 3));
        Pig small = dummy(helper, new BlockPos(3, 1, 3));
        try {
            land(big, melee(player), 5F);
            big.hurt(melee(player), 3F);
            AccumulatorEvents.flush();
            helper.assertTrue(AccumulatorEvents.balance(player, big) == 3F, "前提：被吞掉的 3 记账，实际 " + AccumulatorEvents.balance(player, big));
            big.hurt(melee(player), 7F);
            AccumulatorEvents.flush();
            helper.assertTrue(lost(big) == 7F && AccumulatorEvents.balance(player, big) == 3F,
                    "受击无敌里比上一下重的只打出差额（共 7），不记也不结算，实际掉 " + lost(big) + "、账 " + AccumulatorEvents.balance(player, big));
            float before = lost(big);
            land(big, melee(player), 5F);
            AccumulatorEvents.flush();
            helper.assertTrue(lost(big) - before == 8F, "攒了 3、这一下 5：5 + 3 = 8，实际 " + (lost(big) - before));

            land(small, melee(player), 2F);
            for (int i = 0; i < 3; i++) {
                small.hurt(melee(player), 2F);
            }
            AccumulatorEvents.flush();
            before = lost(small);
            land(small, melee(player), 2F);
            AccumulatorEvents.flush();
            helper.assertTrue(lost(small) - before == 4F, "攒了 6、这一下 2：2 + min(6, 2) = 4，实际 " + (lost(small) - before));
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 被 LivingAttackEvent 拦下的不结算、账留着，放行以后的下一下照常结算；受击无敌里被拦下的那一下（排在累加器后面的监听器才拦）也不记账
     */
    @GameTest(template = EMPTY)
    public static void accumulatorBlockedAttackKeepsBalance(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "acc-block", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.ACCUMULATOR);
        Pig pig = dummy(helper, new BlockPos(2, 1, 3));
        boolean[] blocking = {false};
        Consumer<LivingAttackEvent> lateBlock = event -> {
            if (blocking[0] && event.getEntity() == pig) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingAttackEvent.class, lateBlock);
        try {
            land(pig, melee(player), 4F);
            pig.hurt(melee(player), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(AccumulatorEvents.balance(player, pig) == 4F, "前提：记账 4，实际 " + AccumulatorEvents.balance(player, pig));

            blocking[0] = true;
            pig.invulnerableTime = 20;
            pig.hurt(melee(player), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(AccumulatorEvents.balance(player, pig) == 4F, "受击无敌里被拦下的那一下不记账，实际 " + AccumulatorEvents.balance(player, pig));
            float before = lost(pig);
            land(pig, melee(player), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(lost(pig) == before, "前提：被拦下的一下没掉血");
            helper.assertTrue(AccumulatorEvents.balance(player, pig) == 4F, "被拦下的不结算，账留着，实际 " + AccumulatorEvents.balance(player, pig));

            blocking[0] = false;
            land(pig, melee(player), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(lost(pig) - before == 8F, "放行以后的下一下照常结算：4 + 4 = 8，实际 " + (lost(pig) - before));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(lateBlock);
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 攻击事件时还在受击无敌里（按原版门槛判成「整下吞掉」），后面却打实了的一下：Mohist 系混合端的门槛是受击无敌时长的一半、插件能改，
     * 这里用排在累加器后面的监听器清掉无敌帧模拟。这一下走到了 LivingHurtEvent，待入账撤掉——只打出自己的数，不记账也不结算，已有的账留给下一下
     */
    @GameTest(template = EMPTY)
    public static void accumulatorDropsAccrualThatLanded(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "acc-landed", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.ACCUMULATOR);
        Pig pig = dummy(helper, new BlockPos(2, 1, 3));
        boolean[] clearing = {false};
        Consumer<LivingAttackEvent> clearCooldown = event -> {
            if (clearing[0] && event.getEntity() == pig) {
                pig.invulnerableTime = 0;
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingAttackEvent.class, clearCooldown);
        try {
            land(pig, melee(player), 4F);
            pig.hurt(melee(player), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(AccumulatorEvents.balance(player, pig) == 4F, "前提：被吞掉的一下记账 4，实际 " + AccumulatorEvents.balance(player, pig));

            float before = lost(pig);
            clearing[0] = true;
            pig.invulnerableTime = 12;
            pig.hurt(melee(player), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(lost(pig) - before == 4F, "前提：受击无敌在累加器之后被清掉，这一下打实了 4，实际 " + (lost(pig) - before));
            helper.assertTrue(AccumulatorEvents.balance(player, pig) == 4F,
                    "打实了的那一下不记账（账没变成 8）、也不结算（账没被清掉），实际 " + AccumulatorEvents.balance(player, pig));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(clearCooldown);
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 举盾挡得住的一下不登记结算、账留着，两种环境一样：纯 Forge 真走一遍 hurt（盾牌整下挡掉、没掉血）；
     * 混合端在 LivingHurtEvent 之后才格挡，按它的顺序手动发 LivingAttackEvent → LivingHurtEvent → ShieldBlockEvent → LivingDamageEvent(0)：
     * 补伤不并进这一下（盾牌不多掉耐久），盾牌整下挡掉以后的 LivingDamageEvent(0) 也不清账
     */
    @GameTest(template = EMPTY)
    public static void accumulatorSkipsShieldBlock(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer attacker = suited(helper, "acc-shield-a", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.ACCUMULATOR);
        boolean[] blocking = {false};
        // 站在攻击者东边、面朝西（yaw 90 朝 -x）对着攻击者
        ServerPlayer bearer = shieldBearer(helper, "acc-shield-b", new Vec3(2.5D, 1D, 0.5D), 90F, blocking);
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            land(bearer, melee(attacker), 4F);
            bearer.hurt(melee(attacker), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(AccumulatorEvents.balance(attacker, bearer) == 4F, "前提：没举盾时被吞掉的一下记账 4，实际 " + AccumulatorEvents.balance(attacker, bearer));

            blocking[0] = true;
            bearer.setHealth(bearer.getMaxHealth());
            land(bearer, melee(attacker), 4F);
            AccumulatorEvents.flush();
            helper.assertTrue(bearer.getHealth() == bearer.getMaxHealth(), "前提：纯 Forge 上盾牌整下挡掉、没掉血，实际 " + bearer.getHealth());
            helper.assertTrue(AccumulatorEvents.balance(attacker, bearer) == 4F, "纯 Forge：举盾挡掉的一下不结算，账留着，实际 " + AccumulatorEvents.balance(attacker, bearer));

            DamageSource shielded = melee(attacker);
            bearer.invulnerableTime = 0;
            MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(bearer, shielded, 4F));
            LivingHurtEvent hurt = new LivingHurtEvent(bearer, shielded, 4F);
            MinecraftForge.EVENT_BUS.post(hurt);
            MinecraftForge.EVENT_BUS.post(new ShieldBlockEvent(bearer, shielded, hurt.getAmount()));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(bearer, shielded, 0F));
            AccumulatorEvents.flush();
            helper.assertTrue(hurt.getAmount() == 4F, "混合端的顺序：举盾挡得住的一下不并进补伤，实际 " + hurt.getAmount());
            helper.assertTrue(AccumulatorEvents.balance(attacker, bearer) == 4F,
                    "混合端的顺序：盾牌整下挡掉以后的 LivingDamageEvent(0) 不清账，实际 " + AccumulatorEvents.balance(attacker, bearer));
        } finally {
            server.setPvpAllowed(pvp);
            cleanup(attacker, bearer);
        }
        helper.succeed();
    }

    /** 正式环境只靠本刻末自动入账（{@code ServerTickEvent} END 的 {@code onAccumulatorServerTick}）：被吞掉的一下当场还不在账上，不手动 flush，下一刻已经入账 */
    @GameTest(template = EMPTY)
    public static void accumulatorAccruesAtTickEnd(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "acc-tick", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.ACCUMULATOR);
        Pig pig = dummy(helper, new BlockPos(2, 1, 3));
        land(pig, melee(player), 4F);
        pig.invulnerableTime = 15;
        pig.hurt(melee(player), 4F);
        if (lost(pig) != 4F || AccumulatorEvents.balance(player, pig) != 0F) {
            cleanup(player);
            helper.fail("前提：第二下被吞掉、本刻末之前还没入账，实际掉 " + lost(pig) + "、账 " + AccumulatorEvents.balance(player, pig));
            return;
        }
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(AccumulatorEvents.balance(player, pig) == 4F, "本刻末自动入账 4，实际 " + AccumulatorEvents.balance(player, pig));
            } finally {
                cleanup(player);
            }
            helper.succeed();
        });
    }

    // ================================================================ 削减

    /** 打实三下：破甲 III，护甲 10 → 4、韧性 4 → 1（每层 −2 / −1） */
    @GameTest(template = EMPTY)
    public static void shredThreeHitsCutArmor(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "shred-three", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.SHRED);
        Pig pig = dummy(helper, new BlockPos(2, 1, 3));
        setArmor(pig, 10D, 4D);
        try {
            ShredEvents.flush();
            for (int i = 0; i < 3; i++) {
                land(pig, melee(player), 1F);
                ShredEvents.flush();
            }
            helper.assertTrue(shredLevel(pig) == 3, "三下应是破甲 III，实际 " + shredLevel(pig));
            helper.assertTrue(pig.getAttributeValue(Attributes.ARMOR) == 4D && pig.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 1D,
                    "护甲应 10 → 4、韧性 4 → 1，实际 " + pig.getAttributeValue(Attributes.ARMOR) + " / " + pig.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 封顶 max_level 层：打实七下停在 V 级（护甲 −10）；max_level 改成 3 时停在 III；同一刻的几下只叠一层 */
    @GameTest(template = EMPTY)
    public static void shredCapsAtMaxLevel(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "shred-cap", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.SHRED);
        Pig five = dummy(helper, new BlockPos(1, 1, 3));
        Pig three = dummy(helper, new BlockPos(3, 1, 3));
        Pig sameTick = dummy(helper, new BlockPos(2, 1, 1));
        setArmor(five, 20D, 0D);
        try {
            ShredEvents.flush();
            for (int i = 0; i < 7; i++) {
                land(five, melee(player), 1F);
                ShredEvents.flush();
            }
            helper.assertTrue(shredLevel(five) == 5 && five.getAttributeValue(Attributes.ARMOR) == 10D,
                    "七下应封顶 V 级、护甲 20 → 10，实际 " + shredLevel(five) + " 级 / " + five.getAttributeValue(Attributes.ARMOR));

            ShredEvents.Config.Values d = ShredEvents.Config.DEFAULTS;
            ShredEvents.Config.setTestOverrides(new ShredEvents.Config.Values(d.armorPerLevel(), d.toughnessPerLevel(), 3, d.durationTicks()));
            try {
                for (int i = 0; i < 5; i++) {
                    land(three, melee(player), 1F);
                    ShredEvents.flush();
                }
            } finally {
                ShredEvents.Config.setTestOverrides(null);
            }
            helper.assertTrue(shredLevel(three) == 3, "max_level 3 时应停在 III，实际 " + shredLevel(three));

            for (int i = 0; i < 3; i++) {
                land(sameTick, melee(player), 1F);
            }
            ShredEvents.flush();
            helper.assertTrue(shredLevel(sameTick) == 1, "同一刻对同一只的几下只叠一层，实际 " + shredLevel(sameTick));
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** duration_ticks 刻内没再叠就全掉（一个效果实例，几层一起到期），护甲复原 */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void shredWearsOff(GameTestHelper helper) {
        int duration = ShredEvents.Config.values().durationTicks();
        if (duration + 20 > 300) {
            helper.fail("duration_ticks 是 " + duration + "，这条测试只等得了 280 刻（默认 100）");
            return;
        }
        ServerPlayer player = suited(helper, "shred-off", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.SHRED);
        Pig pig = dummy(helper, new BlockPos(2, 1, 3));
        setArmor(pig, 10D, 0D);
        ShredEvents.flush();
        land(pig, melee(player), 1F);
        ShredEvents.flush();
        land(pig, melee(player), 1F);
        ShredEvents.flush();
        cleanup(player);
        if (shredLevel(pig) != 2) {
            helper.fail("前提：两下叠到破甲 II，实际 " + shredLevel(pig));
            return;
        }
        helper.runAfterDelay(Math.max(1, duration - 10), () -> helper.assertTrue(shredLevel(pig) == 2,
                "到期前（" + (duration - 10) + " 刻）还应是破甲 II，实际 " + shredLevel(pig)));
        helper.runAfterDelay(duration + 5, () -> {
            helper.assertFalse(hasShred(pig), duration + " 刻没再叠应全部消失");
            helper.assertTrue(pig.getAttributeValue(Attributes.ARMOR) == 10D, "护甲应复原到 10，实际 " + pig.getAttributeValue(Attributes.ARMOR));
            helper.succeed();
        });
    }

    /**
     * 只在打实以后叠：攻击被拦、受伤事件被取消、伤害事件被后面的监听器取消、混合端按 0 发来的 LivingDamageEvent 都不叠；
     * 伤害是 0 但吃掉了伤害吸收（黄心）的算打实；照常打中的叠
     */
    @GameTest(template = LARGE)
    public static void shredOnlyLandedHits(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "shred-land", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.SHRED);
        Pig attackBlocked = dummy(helper, new BlockPos(2, 1, 2));
        Pig hurtCanceled = dummy(helper, new BlockPos(4, 1, 2));
        Pig damageCanceled = dummy(helper, new BlockPos(6, 1, 2));
        Pig zeroDamage = dummy(helper, new BlockPos(8, 1, 2));
        Pig absorbed = dummy(helper, new BlockPos(2, 1, 6));
        Pig normal = dummy(helper, new BlockPos(4, 1, 6));
        Consumer<LivingAttackEvent> blockAttack = event -> {
            if (event.getEntity() == attackBlocked) {
                event.setCanceled(true);
            }
        };
        Consumer<LivingHurtEvent> cancelHurt = event -> {
            if (event.getEntity() == hurtCanceled) {
                event.setCanceled(true);
            }
        };
        Consumer<LivingDamageEvent> lateCancelDamage = event -> {
            if (event.getEntity() == damageCanceled) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, blockAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, cancelHurt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, lateCancelDamage);
        try {
            ShredEvents.flush();
            land(attackBlocked, melee(player), 2F);
            land(hurtCanceled, melee(player), 2F);
            land(damageCanceled, melee(player), 2F);
            helper.assertTrue(ShredEvents.pendingCount() == 1, "前提：只有伤害事件到了的那一下在等本刻末结算，实际 " + ShredEvents.pendingCount());
            // 混合端：别的模组取消原伤害、举盾整下挡掉时插件放行后照发 LivingDamageEvent(0)
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(zeroDamage, melee(player), 0F));
            absorbed.setAbsorptionAmount(10F);
            land(absorbed, melee(player), 4F);
            helper.assertTrue(absorbed.getAbsorptionAmount() == 6F && lost(absorbed) == 0F, "前提：黄心整下吃掉了这一下");
            land(normal, melee(player), 2F);
            ShredEvents.flush();
            helper.assertFalse(hasShred(attackBlocked), "攻击被拦的不叠");
            helper.assertFalse(hasShred(hurtCanceled), "受伤事件被取消的不叠");
            helper.assertFalse(hasShred(damageCanceled), "伤害事件被排在后面的监听器取消的不叠");
            helper.assertFalse(hasShred(zeroDamage), "混合端按 0 发来的 LivingDamageEvent 不叠");
            helper.assertTrue(hasShred(absorbed), "吃掉了伤害吸收的算打实，叠一层");
            helper.assertTrue(hasShred(normal), "照常打中的叠一层");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(blockAttack);
            MinecraftForge.EVENT_BUS.unregister(cancelHurt);
            MinecraftForge.EVENT_BUS.unregister(lateCancelDamage);
            cleanup(player);
        }
        helper.succeed();
    }

    /** 对状态效果免疫的（MobEffectEvent.Applicable 拒绝）不吃破甲、护甲不变；旁边没免疫的照吃；没装模块的玩家打不出破甲 */
    @GameTest(template = EMPTY)
    public static void shredSkipsImmune(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "shred-immune", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.SHRED);
        ServerPlayer without = suited(helper, "shred-none", new Vec3(4.5D, 1D, 0.5D), 0);
        Pig immune = dummy(helper, new BlockPos(1, 1, 3));
        Pig open = dummy(helper, new BlockPos(3, 1, 3));
        Pig untouched = dummy(helper, new BlockPos(2, 1, 1));
        setArmor(immune, 10D, 0D);
        Consumer<MobEffectEvent.Applicable> deny = event -> {
            if (event.getEntity() == immune && event.getEffectInstance().getEffect() == ECEffects.ARMOR_SHRED.get()) {
                event.setResult(Event.Result.DENY);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MobEffectEvent.Applicable.class, deny);
        try {
            ShredEvents.flush();
            land(immune, melee(player), 1F);
            land(open, melee(player), 1F);
            land(untouched, melee(without), 1F);
            ShredEvents.flush();
            helper.assertFalse(hasShred(immune), "免疫的不吃破甲");
            helper.assertTrue(immune.getAttributeValue(Attributes.ARMOR) == 10D, "免疫的护甲不变，实际 " + immune.getAttributeValue(Attributes.ARMOR));
            helper.assertTrue(hasShred(open), "对照：没免疫的吃破甲");
            helper.assertFalse(hasShred(untouched), "没装削减模块的玩家打不出破甲");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deny);
            cleanup(player, without);
        }
        helper.succeed();
    }

    /** 正式环境只靠本刻末自动结算（{@code ServerTickEvent} END 的 {@code onShredServerTick}）：打实一下当场还没叠，不手动 flush，下一刻是破甲 I */
    @GameTest(template = EMPTY)
    public static void shredStacksAtTickEnd(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "shred-tick", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.SHRED);
        Pig pig = dummy(helper, new BlockPos(2, 1, 3));
        land(pig, melee(player), 1F);
        if (lost(pig) != 1F || hasShred(pig)) {
            cleanup(player);
            helper.fail("前提：打实了一下、本刻末之前还没叠，实际掉 " + lost(pig) + "、破甲 " + shredLevel(pig));
            return;
        }
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(shredLevel(pig) == 1, "本刻末自动叠上破甲 I，实际 " + shredLevel(pig));
            } finally {
                cleanup(player);
            }
            helper.succeed();
        });
    }

    /**
     * 反伤不叠破甲（batch10 §5.12.2「你打实的每一下」）：开着削减的人被僵尸打，宅男全套以他的名义反伤僵尸；
     * 胸甲上的荆棘 III 也打回去（照 Mob.doHurtTarget 打中以后调 doPostHurtEffects，荆棘按几率触发，重复到触发为止）。
     * 这两种都是别人打你、自动打回去的一下，僵尸都不叠；对照：自己打实一下照叠
     */
    @GameTest(template = EMPTY)
    public static void shredSkipsThorns(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "shred-thorns", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.SHRED);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
        player.getItemBySlot(EquipmentSlot.CHEST).enchant(Enchantments.THORNS, 3);
        try {
            helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getTag(ECDamageTypes.REFLECTED).isPresent(),
                    "伤害类型标签 essentialcraft5:reflected 应已加载");
            ShredEvents.flush();
            float before = zombie.getHealth();
            player.hurt(zombie.damageSources().mobAttack(zombie), 4F);
            helper.assertTrue(zombie.getHealth() < before, "前提：宅男全套的反伤打到了僵尸");
            ShredEvents.flush();
            helper.assertFalse(hasShred(zombie), "宅男全套的反伤不叠破甲");

            boolean triggered = false;
            for (int i = 0; i < 64 && !triggered; i++) {
                zombie.invulnerableTime = 0;
                float hp = zombie.getHealth();
                EnchantmentHelper.doPostHurtEffects(player, zombie);
                triggered = zombie.getHealth() < hp;
            }
            helper.assertTrue(triggered, "前提：荆棘打回了僵尸");
            ShredEvents.flush();
            helper.assertFalse(hasShred(zombie), "荆棘打回去的伤害不叠破甲");

            land(zombie, melee(player), 1F);
            ShredEvents.flush();
            helper.assertTrue(hasShred(zombie), "对照：自己打实的一下照叠");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** σ 的反弹（和普通近战同一个伤害类型，靠饰品反伤作用域认）不叠破甲：σ 必中，僵尸那一下被免成 0，结算时原样反给僵尸，僵尸不叠 */
    @GameTest(template = EMPTY)
    public static void shredSkipsSigmaReflect(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "shred-sigma", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.SHRED);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
        SpecialBaubleItem sigma = ECBaubles.SIGMA_RING.get();
        try {
            CuriosApi.getCuriosInventory(player).resolve().orElseThrow().setEquippedCurio(sigma.slot(), 0, new ItemStack(sigma));
            CuriosCompat.invalidate(player);
            BaubleCombatEvents.setSigmaRollForTest(0F);
            BaubleCombatEvents.flushSigma();
            ShredEvents.flush();
            player.hurt(zombie.damageSources().mobAttack(zombie), 4F);
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "前提：σ 必中，自己免伤");
            ShredEvents.flush();
            helper.assertFalse(hasShred(zombie), "前提：σ 结算之前僵尸没有破甲");
            zombie.invulnerableTime = 0;
            float before = zombie.getHealth();
            helper.assertTrue(BaubleCombatEvents.flushSigma() == 1 && zombie.getHealth() < before, "前提：σ 在结算时把这一下反给了僵尸");
            ShredEvents.flush();
            helper.assertFalse(hasShred(zombie), "σ 的反弹不叠破甲");
        } finally {
            BaubleCombatEvents.setSigmaRollForTest(null);
            CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> handler.setEquippedCurio(sigma.slot(), 0, ItemStack.EMPTY));
            CuriosCompat.invalidate(player);
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 饰品打出、但是你这一下引出来的伤害照叠：ν 的连锁照 {@code BaubleEffects.nuChain} 的写法，在饰品伤害作用域里以玩家攻击打 {@value BaubleEffects#NU_DAMAGE} 点
     * （不直接调 nuChain：它会打到隔壁测试结构里的怪）
     */
    @GameTest(template = EMPTY)
    public static void shredCountsNuChain(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "shred-nu", new Vec3(0.5D, 1D, 0.5D), 0, ECCombatModules.SHRED);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
        try {
            ShredEvents.flush();
            zombie.invulnerableTime = 0;
            boolean hit = BaubleEffects.asBaubleDamage(() -> zombie.hurt(player.damageSources().playerAttack(player), BaubleEffects.NU_DAMAGE));
            helper.assertTrue(hit, "前提：ν 的连锁打中了僵尸");
            ShredEvents.flush();
            helper.assertTrue(hasShred(zombie), "ν 的连锁照叠一层");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ================================================================ 相位锚

    /** 先付这一秒的电费（相位锚每秒耗电，付成了才生效） */
    private static ServerPlayer anchorOwner(GameTestHelper helper, String name, Vec3 relative, int chestMru) {
        ServerPlayer owner = suited(helper, name, relative, chestMru, ECCombatModules.PHASE_ANCHOR);
        ModuleTicker.pay(owner);
        return owner;
    }

    /**
     * 半径改成 radius、effect_ticks 改成 effectTicks（0 = 不改）跑一次每秒的处理：测试只扫自己结构里的东西
     * （默认 16 格会扫到隔壁测试的生物）
     */
    private static int tickWithin(ServerPlayer owner, int radius, int effectTicks) {
        PhaseAnchorEvents.Config.Values d = PhaseAnchorEvents.Config.values();
        PhaseAnchorEvents.Config.setTestOverrides(new PhaseAnchorEvents.Config.Values(radius, d.mruPerSecond(), d.affectPlayers(), d.combatTicks(),
                effectTicks > 0 ? effectTicks : d.effectTicks()));
        try {
            return PhaseAnchorEvents.tick(owner);
        } finally {
            PhaseAnchorEvents.Config.setTestOverrides(null);
        }
    }

    /** 范围内的末影人挂上锚定，随机瞬移每次都传不走；范围外的不挂 */
    @GameTest(template = LARGE)
    public static void anchorStopsEndermanTeleport(GameTestHelper helper) {
        ServerPlayer owner = anchorOwner(helper, "anc-ender", new Vec3(3.5D, 1D, 3.5D), 100_000);
        EnderMan near = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(4, 1, 4));
        EnderMan far = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(9, 1, 9));
        try {
            int count = tickWithin(owner, 2, 0);
            helper.assertTrue(count == 1 && anchored(near) && !anchored(far), "半径 2：只锚住近的那只，实际 " + count + " 只、近 " + anchored(near) + "、远 " + anchored(far));
            Method teleport = ObfuscationReflectionHelper.findMethod(EnderMan.class, ENDERMAN_RANDOM_TELEPORT);
            Vec3 before = near.position();
            for (int i = 0; i < 32; i++) {
                teleport.invoke(near);
            }
            helper.assertTrue(near.position().equals(before), "锚住的末影人瞬移不走，实际 " + before + " → " + near.position());
        } catch (ReflectiveOperationException e) {
            helper.fail("调不到末影人的随机瞬移：" + e);
            return;
        } finally {
            cleanup(owner);
        }
        helper.succeed();
    }

    /** 潜影贝换位置（EntityTeleportEvent.EnderEntity）被拦；锚定挂的是 effect_ticks 刻、带图标；没在范围里的照常 */
    @GameTest(template = LARGE)
    public static void anchorStopsShulkerTeleport(GameTestHelper helper) {
        ServerPlayer owner = anchorOwner(helper, "anc-shulker", new Vec3(3.5D, 1D, 3.5D), 100_000);
        Shulker near = helper.spawnWithNoFreeWill(EntityType.SHULKER, new BlockPos(4, 1, 5));
        Shulker far = helper.spawnWithNoFreeWill(EntityType.SHULKER, new BlockPos(9, 1, 9));
        try {
            tickWithin(owner, 3, 60);
            MobEffectInstance effect = near.getEffect(ECEffects.ANCHORED.get());
            helper.assertTrue(effect != null && effect.getDuration() == 60 && effect.showIcon(), "近的潜影贝挂 60 刻带图标的锚定，实际 " + effect);
            Vec3 to = near.position().add(0D, 3D, 0D);
            helper.assertTrue(ForgeEventFactory.onEnderTeleport(near, to.x, to.y, to.z).isCanceled(), "锚住的潜影贝换位置被拦");
            helper.assertFalse(anchored(far), "范围外的潜影贝不锚");
            helper.assertFalse(ForgeEventFactory.onEnderTeleport(far, to.x, to.y, to.z).isCanceled(), "范围外的潜影贝照常换位置");
        } finally {
            cleanup(owner);
        }
        helper.succeed();
    }

    /** anchor_immune 里的类型、起了名字的（isSpared）、不是敌对生物的都不锚；标签本身在 */
    @GameTest(template = EMPTY)
    public static void anchorSkipsImmuneAndSpared(GameTestHelper helper) {
        ServerPlayer owner = anchorOwner(helper, "anc-immune", new Vec3(2.5D, 1D, 2.5D), 100_000);
        Zombie immune = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 3));
        EnderMan named = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(3, 1, 3));
        named.setCustomName(Component.literal("Ender"));
        EnderMan plainEnder = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(3, 1, 1));
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 1));
        try {
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getTag(ECCombatModules.ANCHOR_IMMUNE).isPresent(), "实体标签 anchor_immune 应已加载");
            PhaseAnchorEvents.setImmuneForTest(Set.of(EntityType.ZOMBIE));
            try {
                tickWithin(owner, 2, 0);
            } finally {
                PhaseAnchorEvents.setImmuneForTest(Set.of());
            }
            helper.assertFalse(anchored(immune), "anchor_immune 里的不锚");
            helper.assertFalse(anchored(named), "起了名字的不锚（isSpared）");
            helper.assertFalse(anchored(pig), "不是敌对生物的不锚");
            helper.assertTrue(anchored(plainEnder), "对照：普通的末影人锚住");
        } finally {
            cleanup(owner);
        }
        helper.succeed();
    }

    /** owner 范围里扔珍珠的 other：EntityTeleportEvent.EnderPearl 有没有被取消 */
    private static boolean pearlBlocked(GameTestHelper helper, ServerPlayer other) {
        ThrownEnderpearl pearl = new ThrownEnderpearl(helper.getLevel(), other);
        Vec3 to = other.position().add(2D, 0D, 0D);
        BlockHitResult hit = new BlockHitResult(to, Direction.UP, BlockPos.containing(to), false);
        boolean canceled = ForgeEventFactory.onEnderPearlLand(other, to.x, to.y, to.z, pearl, 5F, hit).isCanceled();
        pearl.discard();
        return canceled;
    }

    /** 没交过手、只有一方动过手的玩家都不锚，珍珠照常 */
    @GameTest(template = EMPTY)
    public static void anchorSkipsPlayersWithoutMutualCombat(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = anchorOwner(helper, "anc-pv-own", new Vec3(0.5D, 1D, 0.5D), 100_000);
        ServerPlayer other = plain(helper, "anc-pv-oth", new Vec3(2.5D, 1D, 2.5D));
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            int now = server.getTickCount();
            helper.assertTrue(PhaseAnchorEvents.anchor(owner, List.of(other), now) == 0 && !anchored(other), "没交过手的玩家不锚");
            helper.assertFalse(pearlBlocked(helper, other), "没交过手的玩家扔珍珠照常");
            land(other, melee(owner), 1F);
            helper.assertTrue(ECCombatTags.lastLanded(owner.getUUID(), other.getUUID()) >= 0, "前提：主人打过对方一下");
            helper.assertTrue(PhaseAnchorEvents.anchor(owner, List.of(other), now) == 0 && !anchored(other), "只有主人动过手不算互相交过手，不锚");
            helper.assertFalse(pearlBlocked(helper, other), "只有一方动过手，珍珠照常");
        } finally {
            server.setPvpAllowed(pvp);
            cleanup(owner, other);
        }
        helper.succeed();
    }

    /**
     * 互相交过手以后（combat_ticks 内）对方挂上锚定、珍珠被拦；过了窗口不再锚、珍珠放开；PvP 关掉或 affect_players 关掉时也不锚
     */
    @GameTest(template = EMPTY)
    public static void anchorPlayersAfterMutualCombat(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = anchorOwner(helper, "anc-mu-own", new Vec3(0.5D, 1D, 0.5D), 100_000);
        ServerPlayer other = plain(helper, "anc-mu-oth", new Vec3(2.5D, 1D, 2.5D));
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            land(other, melee(owner), 1F);
            land(owner, melee(other), 1F);
            ECCombatTags.flushPending();
            int now = server.getTickCount();
            helper.assertTrue(ECCombatTags.inMutualCombat(owner, other, now, PhaseAnchorEvents.Config.values().combatTicks()), "前提：双方都打过对方");

            PhaseAnchorEvents.Config.Values d = PhaseAnchorEvents.Config.DEFAULTS;
            PhaseAnchorEvents.Config.setTestOverrides(new PhaseAnchorEvents.Config.Values(d.radius(), d.mruPerSecond(), false, d.combatTicks(), d.effectTicks()));
            try {
                helper.assertTrue(PhaseAnchorEvents.anchor(owner, List.of(other), now) == 0, "affect_players 关掉时不锚玩家");
            } finally {
                PhaseAnchorEvents.Config.setTestOverrides(null);
            }
            server.setPvpAllowed(false);
            helper.assertTrue(PhaseAnchorEvents.anchor(owner, List.of(other), now) == 0 && !anchored(other), "PvP 关掉（打不了对方）时不锚");
            server.setPvpAllowed(true);

            helper.assertTrue(PhaseAnchorEvents.anchor(owner, List.of(other), now) == 1 && anchored(other), "互相交过手：对方挂上锚定");
            helper.assertTrue(pearlBlocked(helper, other), "锚住的玩家珍珠被拦");

            int window = PhaseAnchorEvents.Config.values().combatTicks();
            ECCombatTags.forget(owner.getUUID());
            ECCombatTags.record(owner.getUUID(), other.getUUID(), now - window - 1);
            ECCombatTags.record(other.getUUID(), owner.getUUID(), now - window - 1);
            other.removeEffect(ECEffects.ANCHORED.get());
            helper.assertTrue(PhaseAnchorEvents.anchor(owner, List.of(other), now) == 0 && !anchored(other), "过了 " + window + " 刻不再锚");
            helper.assertFalse(pearlBlocked(helper, other), "过了窗口珍珠放开");
        } finally {
            server.setPvpAllowed(pvp);
            cleanup(owner, other);
        }
        helper.succeed();
    }

    /**
     * 主人近战打中穿满电宅男全套的人、对方一下都不还手：对方的套装以他的名义反伤主人一下，反伤不算对方动过手——
     * 不算互相交过手、不锚、珍珠照常（主人那一下被 MRU 盔甲整下挡掉照样算主人打过对方）
     */
    @GameTest(template = EMPTY)
    public static void anchorIgnoresSuitReflect(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = anchorOwner(helper, "anc-refl-own", new Vec3(0.5D, 1D, 0.5D), 100_000);
        ServerPlayer other = suited(helper, "anc-refl-oth", new Vec3(2.5D, 1D, 2.5D), 0);
        chargeSuit(other, 1_000_000);
        int[] reflected = {0};
        Consumer<LivingDamageEvent> watch = countThorns(owner, other, reflected);
        boolean pvp = server.isPvpAllowed();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDamageEvent.class, watch);
        try {
            server.setPvpAllowed(true);
            land(other, melee(owner), 4F);
            ECCombatTags.flushPending();
            helper.assertTrue(reflected[0] > 0, "前提：对方的套装以他的名义反伤了主人");
            int now = server.getTickCount();
            helper.assertTrue(ECCombatTags.lastLanded(owner.getUUID(), other.getUUID()) >= 0, "前提：主人那一下算打过对方");
            helper.assertTrue(ECCombatTags.lastLanded(other.getUUID(), owner.getUUID()) < 0, "反伤不算对方打过主人");
            helper.assertFalse(ECCombatTags.inMutualCombat(owner, other, now, PhaseAnchorEvents.Config.values().combatTicks()), "不算互相交过手");
            helper.assertTrue(PhaseAnchorEvents.anchor(owner, List.of(other), now) == 0 && !anchored(other), "反伤凑不满「互相交过手」，不锚");
            helper.assertFalse(pearlBlocked(helper, other), "珍珠照常");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watch);
            server.setPvpAllowed(pvp);
            cleanup(owner, other);
        }
        helper.succeed();
    }

    /**
     * 同上，对方穿带荆棘 III 的胸甲：荆棘打回主人的不算对方动过手（荆棘按几率触发：照 Player.attack 打中以后调一次 doPostHurtEffects，重复到触发为止）
     */
    @GameTest(template = EMPTY)
    public static void anchorIgnoresThorns(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = anchorOwner(helper, "anc-thorn-own", new Vec3(0.5D, 1D, 0.5D), 100_000);
        ServerPlayer other = plain(helper, "anc-thorn-oth", new Vec3(2.5D, 1D, 2.5D));
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        chest.enchant(Enchantments.THORNS, 3);
        other.setItemSlot(EquipmentSlot.CHEST, chest);
        int[] thorns = {0};
        Consumer<LivingDamageEvent> watch = countThorns(owner, other, thorns);
        boolean pvp = server.isPvpAllowed();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDamageEvent.class, watch);
        try {
            server.setPvpAllowed(true);
            for (int i = 0; i < 64 && thorns[0] == 0; i++) {
                other.setHealth(other.getMaxHealth());
                owner.setHealth(owner.getMaxHealth());
                owner.invulnerableTime = 0;
                if (land(other, melee(owner), 1F)) {
                    EnchantmentHelper.doPostHurtEffects(other, owner);
                }
            }
            ECCombatTags.flushPending();
            helper.assertTrue(thorns[0] > 0, "前提：荆棘以对方的名义打回了主人");
            int now = server.getTickCount();
            helper.assertTrue(ECCombatTags.lastLanded(owner.getUUID(), other.getUUID()) >= 0, "前提：主人打过对方");
            helper.assertTrue(ECCombatTags.lastLanded(other.getUUID(), owner.getUUID()) < 0, "荆棘不算对方打过主人");
            helper.assertFalse(ECCombatTags.inMutualCombat(owner, other, now, PhaseAnchorEvents.Config.values().combatTicks()), "不算互相交过手");
            helper.assertTrue(PhaseAnchorEvents.anchor(owner, List.of(other), now) == 0 && !anchored(other), "不锚");
            helper.assertFalse(pearlBlocked(helper, other), "珍珠照常");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watch);
            server.setPvpAllowed(pvp);
            cleanup(owner, other);
        }
        helper.succeed();
    }

    /**
     * 正式环境由玩家刻末尾的 {@code onPhaseAnchorPlayerTick} 每秒扫一次（按 (tickCount + 实体 id) % 20 错开）。测试玩家不进世界、不走真 tick，
     * 这里把 tickCount 摆到相位上、手动往事件总线发 PlayerTickEvent：不在相位上的刻、刻开头（START）都不扫，在相位上的刻末尾扫一次，范围内的末影人挂上锚定
     */
    @GameTest(template = EMPTY)
    public static void anchorRunsFromPlayerTick(GameTestHelper helper) {
        ServerPlayer owner = anchorOwner(helper, "anc-tick", new Vec3(2.5D, 1D, 2.5D), 100_000);
        EnderMan ender = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(2, 1, 3));
        int base = PhaseAnchorEvents.INTERVAL * 10;
        PhaseAnchorEvents.Config.Values d = PhaseAnchorEvents.Config.values();
        try {
            PhaseAnchorEvents.Config.setTestOverrides(new PhaseAnchorEvents.Config.Values(2, d.mruPerSecond(), d.affectPlayers(), d.combatTicks(), d.effectTicks()));
            try {
                owner.tickCount = base + Math.floorMod(PhaseAnchorEvents.PHASE + 1 - owner.getId(), PhaseAnchorEvents.INTERVAL);
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
                helper.assertFalse(anchored(ender), "不在相位上的那一刻不扫");
                owner.tickCount = base + Math.floorMod(PhaseAnchorEvents.PHASE - owner.getId(), PhaseAnchorEvents.INTERVAL);
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, owner));
                helper.assertFalse(anchored(ender), "刻开头（START）不扫");
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
            } finally {
                PhaseAnchorEvents.Config.setTestOverrides(null);
            }
            helper.assertTrue(anchored(ender), "在相位上的刻末尾扫一次，范围内的末影人挂上锚定");
        } finally {
            cleanup(owner);
        }
        helper.succeed();
    }

    /** 缺电时不锚、不扣电；补上电、这一秒付了 mru_per_second 以后照锚 */
    @GameTest(template = EMPTY)
    public static void anchorNeedsPower(GameTestHelper helper) {
        ServerPlayer owner = suited(helper, "anc-power", new Vec3(2.5D, 1D, 2.5D), 0, ECCombatModules.PHASE_ANCHOR);
        EnderMan ender = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(2, 1, 3));
        try {
            helper.assertTrue(ModuleTicker.pay(owner) == 0, "前提：没电付不起");
            helper.assertTrue(tickWithin(owner, 2, 0) == 0 && !anchored(ender), "缺电时不锚");
            int cost = PhaseAnchorEvents.Config.values().mruPerSecond();
            setChestMru(owner, 10_000);
            helper.assertTrue(ModuleTicker.pay(owner) == cost && chestMru(owner) == 10_000 - cost, "补上电：这一秒扣 " + cost + "，实际剩 " + chestMru(owner));
            helper.assertTrue(tickWithin(owner, 2, 0) == 1 && anchored(ender), "付了电费照锚");
        } finally {
            cleanup(owner);
        }
        helper.succeed();
    }

    /** 终端里关掉模块：不锚、不扣电；没穿齐四件同样不锚 */
    @GameTest(template = EMPTY)
    public static void anchorOffWhenSwitchedOff(GameTestHelper helper) {
        ServerPlayer owner = suited(helper, "anc-off", new Vec3(2.5D, 1D, 2.5D), 10_000, ECCombatModules.PHASE_ANCHOR);
        EnderMan ender = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(2, 1, 3));
        try {
            ItemStack legs = owner.getItemBySlot(EquipmentSlot.LEGS);
            helper.assertTrue(ArmorModules.setOn(legs, 0, false), "前提：关掉护腿上的相位锚");
            ModuleCache.invalidate(owner);
            helper.assertTrue(ModuleTicker.pay(owner) == 0 && chestMru(owner) == 10_000, "关着的模块不扣电");
            helper.assertTrue(tickWithin(owner, 2, 0) == 0 && !anchored(ender), "关着的模块不锚");

            ArmorModules.setOn(legs, 0, true);
            owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            ModuleCache.invalidate(owner);
            ModuleTicker.pay(owner);
            helper.assertTrue(tickWithin(owner, 2, 0) == 0 && !anchored(ender), "没穿齐四件不锚");
        } finally {
            cleanup(owner);
        }
        helper.succeed();
    }
}
