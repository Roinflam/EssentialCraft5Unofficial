package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.demon.DemonTrades;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.hoanna.FrostbittenEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.HoannaScavengerEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.HoannaScavengerEntity.Weapon;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ManaPrismEntity;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.loot.FairKillCondition;
import com.flameforge.essentialcraft5.loot.KillFatigueCondition;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA.Config.FrostValues;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA.Config.PrismValues;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA.Config.ScavengerValues;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 第十三批 13d-N1「霜蚀者、浮游棱晶、遗民拾荒者」的 GameTest（batch13.md §5.2、§6.5）：
 * <ul>
 *   <li>霜蚀者：属性与精英（霜棺督卫）、读档不叠；近战打实才冻（皮革免冻、填 0 不冻）；冰息的方向在蓄力时定死、前方 90° 锥形、
 *       墙后 / 背后 / 创造模式不打、伤害 = 固定 + 最大生命 × 比例并加冻；锥形判定的纯函数；冰封只冻释放那一刻还在圈里的（走出去就躲掉）、
 *       不扣血、给缓慢 IV；冰盾挡正面弹射物、正面近战减 30%、背后照吃。</li>
 *   <li>浮游棱晶：属性、怕近战不怕箭；锁定方向定死、横移躲得掉、方块挡得住、伤害按护甲算过的 5 + 3%；棱晶集束扇出三束、
 *       同一目标只挨一下。</li>
 *   <li>遗民拾荒者：两种武器的攻击与存盘；撬棍 / 步枪打实加辐射（乘易伤）、别的伤害 / 创造 / 假玩家不加；打针的冷却存盘；
 *       遗民队长的信号弹（只一次、叫来的来源是召唤、不公平、不出精英、没经验、生成被取消不留东西）；收魂与交易池；
 *       白天地表变体的刷怪判定与附近数量上限（自己一批）。</li>
 *   <li>三张掉落表：关键掉落的组、几率、顺序；刷怪笼来的不掉关键物、精英多一颗异变之心。</li>
 * </ul>
 * 改配置一律用 {@code ECHoannaNativesA.Config.setTestFrost / setTestPrism / setTestScavenger}（同一段同步代码里设、测、置回 null）。
 * 要掉血的目标：精确伤害用尸壳（设成怪的当前目标，难度不缩放），范围技用放进世界的生存模式模拟玩家。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaNativesATests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    /** 刷怪判定那条自己一批：要往世界里放拾荒者、数 64 格内有几只 */
    private static final String SPAWN_BATCH = "ec5_hoanna_natives_a_spawn";
    private static final double EPS = 1.0E-3D;
    private static final FrostValues FROST = ECHoannaNativesA.Config.FROST_DEFAULTS;
    private static final PrismValues PRISM = ECHoannaNativesA.Config.PRISM_DEFAULTS;
    private static final ScavengerValues SCAV = ECHoannaNativesA.Config.SCAVENGER_DEFAULTS;

    private HoannaNativesATests() {
    }

    // ================================================================== 工具

    private static <T extends Mob> T make(GameTestHelper helper, EntityType<T> type, Vec3 relative) {
        T mob = type.create(helper.getLevel());
        if (mob == null) {
            throw new IllegalStateException(type + " 应能创建");
        }
        moveTo(helper, mob, relative);
        return mob;
    }

    private static FrostbittenEntity frost(GameTestHelper helper, Vec3 relative) {
        FrostbittenEntity frost = make(helper, ECHoannaNativesA.FROSTBITTEN.get(), relative);
        frost.applyConfiguredAttributes();
        return frost;
    }

    private static ManaPrismEntity prism(GameTestHelper helper, Vec3 relative) {
        ManaPrismEntity prism = make(helper, ECHoannaNativesA.MANA_PRISM.get(), relative);
        prism.applyConfiguredAttributes();
        return prism;
    }

    private static HoannaScavengerEntity scavenger(GameTestHelper helper, Vec3 relative, Weapon weapon) {
        HoannaScavengerEntity scavenger = make(helper, ECHoannaNativesA.HOANNA_SCAVENGER.get(), relative);
        scavenger.setWeapon(weapon);
        scavenger.applyConfiguredAttributes();
        return scavenger;
    }

    /** 把存盘里的某个冷却清零（各技能生成时都带一段起手冷却） */
    private static void ready(Entity entity, String key) {
        CompoundTag tag = entity.saveWithoutId(new CompoundTag());
        tag.putInt(key, 0);
        entity.load(tag);
    }

    /** 走 Forge 的生成事件初始化（精英几率钉成 eliteChance，同步代码里置回） */
    private static void finalizeSpawn(Mob mob, ServerLevel level, MobSpawnType type, double eliteChance) {
        ECMobFramework.Config.setTestEliteChance(eliteChance);
        try {
            ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), type, null, null);
        } finally {
            ECMobFramework.Config.setTestEliteChance(null);
        }
    }

    /** 真玩家（不在世界里）：过了出生无敌、生存模式、满血 */
    private static ServerPlayer survivor(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(GameType.SURVIVAL);
        moveTo(helper, player, relativeFeet);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** 放进世界的模拟玩家（范围技按碰撞箱在世界里找人）；creative 决定是不是创造模式 */
    private static Player bystander(GameTestHelper helper, Vec3 relativeFeet, boolean creative) {
        Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0F, new GameProfile(UUID.randomUUID(), "n1-bystander")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return creative;
            }
        };
        moveTo(helper, player, relativeFeet);
        player.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    private static Husk husk(GameTestHelper helper, Vec3 relative) {
        Husk husk = make(helper, EntityType.HUSK, relative);
        husk.setNoAi(true);
        helper.getLevel().addFreshEntity(husk);
        return husk;
    }

    private static void moveTo(GameTestHelper helper, Entity entity, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        entity.moveTo(at.x, at.y, at.z, 0F, 0F);
    }

    /** 测试结构里 y = 1 那一层铺满石头（脚踩在 y = 2） */
    private static void stoneFloor(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < EPS;
    }

    private static long eliteModifierCount(LivingEntity entity) {
        long count = 0;
        for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ARMOR, Attributes.KNOCKBACK_RESISTANCE)) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                count += instance.getModifiers().stream().filter(m -> m.getId().equals(ECMonster.ELITE_HEALTH_ID) || m.getId().equals(ECMonster.ELITE_ATTACK_ID)
                        || m.getId().equals(ECMonster.ELITE_ARMOR_ID) || m.getId().equals(ECMonster.ELITE_KNOCKBACK_ID)).count();
            }
        }
        return count;
    }

    /** 读档两遍：精英还在、四个精英修饰符各一个 */
    private static <T extends ECMonster> T reloadTwice(T mob) {
        @SuppressWarnings("unchecked")
        EntityType<T> type = (EntityType<T>) mob.getType();
        T first = type.create(mob.level());
        first.load(mob.saveWithoutId(new CompoundTag()));
        T second = type.create(mob.level());
        second.load(first.saveWithoutId(new CompoundTag()));
        return second;
    }

    private static FrostValues frostWith(int freezePerHit, int breathInterval) {
        return new FrostValues(FROST.maxHealth(), FROST.armor(), FROST.attackDamage(), freezePerHit, breathInterval, FROST.breathCharge(),
                FROST.breathRange(), FROST.breathDamage(), FROST.breathHealthPercent(), FROST.breathFreeze(), FROST.sealInterval(), FROST.sealWarning(),
                FROST.sealRadius(), FROST.sealTicks(), FROST.shieldMeleeReduction(), FROST.nearbyCap());
    }

    private static ScavengerValues scavWith(double rifleChance, double stimHealth, int eliteSummonCount, int surfaceMinY, int nearbyCap) {
        return new ScavengerValues(SCAV.maxHealth(), SCAV.armor(), SCAV.crowbarDamage(), SCAV.rifleDamage(), rifleChance, SCAV.rifleAim(),
                SCAV.rifleRange(), SCAV.rifleRest(), SCAV.crowbarDose(), SCAV.rifleDose(), stimHealth, SCAV.stimTicks(), SCAV.stimCooldown(),
                eliteSummonCount, SCAV.eliteFlareHealth(), surfaceMinY, nearbyCap);
    }

    // ================================================================== 霜蚀者

    /**
     * 生成：44 / 6 / 8、满血、自己不会冻、免冰冻伤害、怕火；改了配置只影响以后生成的；霜棺督卫（精英）生命 110、攻击 11.2，
     * 名字走 {@code .elite} 语言键，读档两遍不叠。霜蚀者不能收魂。
     */
    @GameTest(template = EMPTY)
    public static void frostbittenStatsAndElite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrostbittenEntity frost = make(helper, ECHoannaNativesA.FROSTBITTEN.get(), new Vec3(2.5D, 2D, 2.5D));
        finalizeSpawn(frost, level, MobSpawnType.NATURAL, 0D);
        helper.assertTrue(near(frost.getMaxHealth(), 44D) && near(frost.getHealth(), 44D), "生命 44，实际 " + frost.getMaxHealth());
        helper.assertTrue(near(frost.getAttributeValue(Attributes.ARMOR), 6D) && near(frost.getAttributeValue(Attributes.ATTACK_DAMAGE), 8D), "护甲 6、攻击 8");
        helper.assertTrue(SpawnOrigin.of(frost) == Origin.NATURAL && !frost.isElite(), "自然生成、几率 0 时不是精英");
        helper.assertTrue(!frost.canFreeze() && frost.isInvulnerableTo(level.damageSources().freeze()), "自己不会冻、免冰冻伤害");
        helper.assertTrue(near(frost.weaknessMultiplier(level.damageSources().onFire()), ECMobFramework.Config.weakness())
                && near(frost.weaknessMultiplier(level.damageSources().mobAttack(frost)), 1D), "怕火（火焰伤害 × weakness_multiplier）、近战照常");
        helper.assertFalse(frost.getType().is(DemonTrades.SOUL_CAPTURABLE), "霜蚀者不能收魂");

        FrostbittenEntity later = make(helper, ECHoannaNativesA.FROSTBITTEN.get(), new Vec3(1.5D, 2D, 1.5D));
        ECHoannaNativesA.Config.setTestFrost(new FrostValues(50D, 7D, 9D, FROST.freezePerHit(), FROST.breathInterval(), FROST.breathCharge(),
                FROST.breathRange(), FROST.breathDamage(), FROST.breathHealthPercent(), FROST.breathFreeze(), FROST.sealInterval(), FROST.sealWarning(),
                FROST.sealRadius(), FROST.sealTicks(), FROST.shieldMeleeReduction(), FROST.nearbyCap()));
        try {
            finalizeSpawn(later, level, MobSpawnType.NATURAL, 0D);
        } finally {
            ECHoannaNativesA.Config.setTestFrost(null);
        }
        helper.assertTrue(near(later.getMaxHealth(), 50D) && near(later.getAttributeValue(Attributes.ARMOR), 7D)
                && near(later.getAttributeValue(Attributes.ATTACK_DAMAGE), 9D), "改了配置以后生成的按新值");
        helper.assertTrue(near(frost.getMaxHealth(), 44D), "已经在世界里的不跟着改");

        FrostbittenEntity warden = make(helper, ECHoannaNativesA.FROSTBITTEN.get(), new Vec3(3.5D, 2D, 3.5D));
        finalizeSpawn(warden, level, MobSpawnType.NATURAL, 1D);
        helper.assertTrue(warden.isElite() && near(warden.getMaxHealth(), 110D) && near(warden.getAttributeValue(Attributes.ATTACK_DAMAGE), 11.2D),
                "霜棺督卫生命 110、攻击 11.2，实际 " + warden.getMaxHealth() + " / " + warden.getAttributeValue(Attributes.ATTACK_DAMAGE));
        helper.assertTrue(warden.getName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().endsWith("frostbitten.elite"), "精英的名字走 .elite 语言键：" + warden.getName());
        warden.setHealth(60F);
        FrostbittenEntity again = reloadTwice(warden);
        helper.assertTrue(again.isElite() && near(again.getMaxHealth(), 110D) && near(again.getHealth(), 60D) && eliteModifierCount(again) == 4,
                "读档两遍：还是精英、生命不叠、四个精英修饰符各一个，实际 " + again.getMaxHealth() + " / " + eliteModifierCount(again));
        helper.succeed();
    }

    /**
     * 近战冻结：打实一下目标加 60 冰冻刻；穿皮革靴子的不冻；{@code freeze_per_hit} 填 0 不冻；没打实（目标无敌）不冻；
     * 不是它亲手的伤害（冰息那种技能伤害走另一条）不在这里加。
     */
    @GameTest(template = EMPTY)
    public static void frostbittenMeleeFreezes(GameTestHelper helper) {
        FrostbittenEntity frost = frost(helper, new Vec3(2.5D, 2D, 2.5D));
        Husk bare = husk(helper, new Vec3(2.5D, 2D, 3.5D));
        Husk booted = husk(helper, new Vec3(3.5D, 2D, 2.5D));
        booted.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        Husk zero = husk(helper, new Vec3(1.5D, 2D, 2.5D));
        Husk shielded = husk(helper, new Vec3(2.5D, 2D, 1.5D));
        shielded.setInvulnerable(true);
        try {
            helper.assertTrue(frost.doHurtTarget(bare) && bare.getTicksFrozen() == FROST.freezePerHit(),
                    "打实一下加 " + FROST.freezePerHit() + " 冰冻刻，实际 " + bare.getTicksFrozen());
            helper.assertTrue(frost.doHurtTarget(booted) && booted.getTicksFrozen() == 0, "穿皮革靴子的不冻");
            ECHoannaNativesA.Config.setTestFrost(frostWith(0, FROST.breathInterval()));
            try {
                helper.assertTrue(frost.doHurtTarget(zero) && zero.getTicksFrozen() == 0, "freeze_per_hit 0：不冻");
            } finally {
                ECHoannaNativesA.Config.setTestFrost(null);
            }
            helper.assertTrue(!frost.doHurtTarget(shielded) && shielded.getTicksFrozen() == 0, "没打实：不冻");
            helper.assertTrue(FrostbittenEntity.isMeleeFrom(helper.getLevel().damageSources().mobAttack(frost), frost)
                    && !FrostbittenEntity.isMeleeFrom(ECDamageTypes.aberrantStrike(frost), frost), "只有它亲手的近战算");
        } finally {
            discard(bare, booted, zero, shielded);
        }
        helper.succeed();
    }

    /**
     * 冰息：冷却没好 / 关了不能起手；起手时蓄 20 刻、方向（水平）定在那一刻；喷出去打前方锥形里的技能目标——当前目标的尸壳挨 4 + 20 × 5% = 5 点
     * （aberrant_strike 无视护甲）并加 80 冰冻刻，生存模式的玩家也挨，背后的、墙后的、创造模式的不挨；蓄力时目标走出锥形就躲掉。
     */
    @GameTest(template = LARGE)
    public static void frostbittenBreathWarnsAndDamages(GameTestHelper helper) {
        FrostbittenEntity frost = frost(helper, new Vec3(3.5D, 2D, 5.5D));
        Husk husk = husk(helper, new Vec3(6.5D, 2D, 5.5D));
        frost.setTarget(husk);
        Player front = bystander(helper, new Vec3(6.0D, 2D, 4.5D), false);
        Player behind = bystander(helper, new Vec3(1.0D, 2D, 5.5D), false);
        Player walled = bystander(helper, new Vec3(6.5D, 2D, 7.5D), false);
        Player creative = bystander(helper, new Vec3(5.5D, 2D, 4.0D), true);
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(5, y, 6), Blocks.STONE);
        }
        try {
            helper.assertFalse(frost.canBreathAt(husk), "生成时带起手冷却：不能马上吐");
            ready(frost, "breath_cooldown");
            ECHoannaNativesA.Config.setTestFrost(frostWith(FROST.freezePerHit(), 0));
            try {
                helper.assertFalse(frost.canBreathAt(husk), "breath_interval 0：不吐");
            } finally {
                ECHoannaNativesA.Config.setTestFrost(null);
            }
            helper.assertTrue(frost.canBreathAt(husk), "冷却好了、5 格内看得见：能吐");
            frost.startBreath(husk);
            Vec3 dir = frost.breathDirection();
            helper.assertTrue(frost.breathTicks() == FROST.breathCharge() && dir != null && dir.x > 0.999D && Math.abs(dir.y) < EPS,
                    "蓄 " + FROST.breathCharge() + " 刻、方向水平朝着目标，实际 " + frost.breathTicks() + " / " + dir);
            helper.assertFalse(frost.canBreathAt(husk), "蓄力时不能再起手");
            List<LivingEntity> hit = frost.releaseBreath();
            helper.assertTrue(hit.contains(husk) && near(husk.getHealth(), 15D) && husk.getTicksFrozen() == FROST.breathFreeze(),
                    "尸壳挨 5 点、加 " + FROST.breathFreeze() + " 冰冻刻，实际 " + husk.getHealth() + " / " + husk.getTicksFrozen());
            helper.assertTrue(hit.contains(front) && front.getTicksFrozen() == FROST.breathFreeze(), "锥形里的生存模式玩家也挨、也冻");
            helper.assertTrue(!hit.contains(behind) && near(behind.getHealth(), behind.getMaxHealth()), "背后的不挨");
            helper.assertTrue(!hit.contains(walled) && near(walled.getHealth(), walled.getMaxHealth()), "墙后的不挨");
            helper.assertTrue(!hit.contains(creative) && near(creative.getHealth(), creative.getMaxHealth()), "创造模式的不挨");
            helper.assertTrue(frost.breathTicks() == 0 && frost.breathDirection() == null, "喷完回到没在蓄的状态");

            // 蓄力时目标横跑出锥形：方向不跟着转，躲掉
            husk.invulnerableTime = 0;
            frost.startBreath(husk);
            moveTo(helper, husk, new Vec3(4.5D, 2D, 9.5D));
            moveTo(helper, front, new Vec3(3.5D, 2D, 9.5D));
            List<LivingEntity> dodged = frost.releaseBreath();
            helper.assertTrue(dodged.isEmpty() && near(husk.getHealth(), 15D), "蓄力时走出锥形：躲掉，实际挨了 " + dodged.size());
            helper.assertTrue(frost.releaseBreath().isEmpty(), "没在蓄时喷：什么都不做");
        } finally {
            discard(husk, front, behind, walled, creative);
        }
        helper.succeed();
    }

    /** 冰息锥形的纯函数：前方 5 格、左右各 45°、上下 2.5 格以内；正上方水平 0.5 格以内也算；背后不算 */
    @GameTest(template = EMPTY)
    public static void frostbittenBreathCone(GameTestHelper helper) {
        Vec3 o = Vec3.ZERO;
        Vec3 east = new Vec3(1D, 0D, 0D);
        helper.assertTrue(FrostbittenEntity.inBreathCone(o, east, new Vec3(4D, 0D, 0D), 5D), "正前方 4 格：中");
        helper.assertFalse(FrostbittenEntity.inBreathCone(o, east, new Vec3(5.5D, 0D, 0D), 5D), "正前方 5.5 格：超出射程");
        double in = Math.toRadians(44D);
        double out = Math.toRadians(47D);
        helper.assertTrue(FrostbittenEntity.inBreathCone(o, east, new Vec3(Math.cos(in) * 3D, 0D, Math.sin(in) * 3D), 5D), "偏 44°：中");
        helper.assertFalse(FrostbittenEntity.inBreathCone(o, east, new Vec3(Math.cos(out) * 3D, 0D, Math.sin(out) * 3D), 5D), "偏 47°：不中");
        helper.assertTrue(FrostbittenEntity.inBreathCone(o, east, new Vec3(3D, 2.4D, 0D), 5D), "高 2.4 格：中");
        helper.assertFalse(FrostbittenEntity.inBreathCone(o, east, new Vec3(3D, 2.6D, 0D), 5D), "高 2.6 格：不中");
        helper.assertTrue(FrostbittenEntity.inBreathCone(o, east, new Vec3(0.2D, 1D, 0.2D), 5D), "几乎正上方：中");
        helper.assertFalse(FrostbittenEntity.inBreathCone(o, east, new Vec3(-2D, 0D, 0D), 5D), "背后：不中");
        helper.succeed();
    }

    /**
     * 冰封（霜棺督卫）：普通的不会；冷却好了、目标在圈边才起手；起手后 20 刻预警，释放那一刻还在 3 格圈里的生存模式玩家冻透（冰冻刻 ≥ 140 + 2 × 40）
     * 并给缓慢 IV 40 刻、不扣血；预警时走出圈就躲掉，走进圈的照冻；穿皮革的不冻。
     */
    @GameTest(template = LARGE)
    public static void frostWardenSeal(GameTestHelper helper) {
        FrostbittenEntity plain = frost(helper, new Vec3(1.5D, 2D, 1.5D));
        ready(plain, "seal_cooldown");
        FrostbittenEntity warden = frost(helper, new Vec3(5.5D, 2D, 5.5D));
        warden.setElite(true);
        Player leaver = bystander(helper, new Vec3(7.5D, 2D, 5.5D), false);
        Player comer = bystander(helper, new Vec3(5.5D, 2D, 9.8D), false);
        Player leather = bystander(helper, new Vec3(4.5D, 2D, 5.5D), false);
        leather.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        try {
            helper.assertFalse(plain.canSealNear(leaver), "普通霜蚀者不会冰封");
            helper.assertFalse(warden.canSealNear(leaver), "生成时带起手冷却");
            ready(warden, "seal_cooldown");
            helper.assertTrue(warden.canSealNear(leaver) && !warden.canSealNear(comer), "圈边 2 格的能起手、4.3 格外的不起手");
            warden.startSeal();
            helper.assertTrue(warden.sealTicks() == FROST.sealWarning() && !warden.canSealNear(leaver), "预警 " + FROST.sealWarning() + " 刻、预警时不再起手");
            moveTo(helper, leaver, new Vec3(9.0D, 2D, 5.5D));
            moveTo(helper, comer, new Vec3(5.5D, 2D, 7.0D));
            List<LivingEntity> sealed = warden.releaseSeal();
            int frozen = comer.getTicksRequiredToFreeze() + FROST.sealTicks() * 2;
            MobEffectInstance slow = comer.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(sealed.contains(comer) && comer.getTicksFrozen() >= frozen && slow != null
                    && slow.getAmplifier() == FrostbittenEntity.SEAL_SLOW_AMPLIFIER && slow.getDuration() == FROST.sealTicks(),
                    "走进圈的冻透、缓慢 IV " + FROST.sealTicks() + " 刻，实际 " + comer.getTicksFrozen() + " / " + slow);
            helper.assertTrue(near(comer.getHealth(), comer.getMaxHealth()), "冰封不扣血");
            helper.assertTrue(!sealed.contains(leaver) && leaver.getTicksFrozen() == 0 && !leaver.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "走出圈的躲掉");
            helper.assertTrue(!sealed.contains(leather) && leather.getTicksFrozen() == 0, "穿皮革的不冻");
            helper.assertTrue(warden.sealTicks() == 0 && !warden.canSealNear(comer), "放完回到冷却");
        } finally {
            discard(leaver, comer, leather);
        }
        helper.succeed();
    }

    /**
     * 冰盾（霜棺督卫）：正面来的弹射物（含没有直接实体的光束）整个挡掉；正面近战按 30% 减；背后的照吃全额；普通霜蚀者没有冰盾。
     */
    @GameTest(template = EMPTY)
    public static void frostWardenShield(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrostbittenEntity warden = frost(helper, new Vec3(2.5D, 2D, 2.5D));
        warden.setElite(true);
        warden.setYRot(0F);
        warden.setYBodyRot(0F);
        // 朝向 yRot 0 = +z
        Husk ahead = make(helper, EntityType.HUSK, new Vec3(2.5D, 2D, 4.5D));
        Husk rear = make(helper, EntityType.HUSK, new Vec3(2.5D, 2D, 0.5D));
        float full = warden.getMaxHealth();
        helper.assertFalse(warden.hurt(ECDamageTypes.mobBolt(ahead), 6F), "正面的光束：挡掉");
        helper.assertTrue(near(warden.getHealth(), full), "挡掉的不掉血");
        helper.assertTrue(warden.hurt(ECDamageTypes.mobBolt(rear), 6F) && warden.getHealth() < full, "背后的光束：照吃");

        double armor = warden.getArmorValue();
        double toughness = warden.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        warden.setHealth(full);
        warden.invulnerableTime = 0;
        warden.hurt(level.damageSources().mobAttack(ahead), 10F);
        float frontLoss = full - warden.getHealth();
        warden.setHealth(full);
        warden.invulnerableTime = 0;
        warden.hurt(level.damageSources().mobAttack(rear), 10F);
        float rearLoss = full - warden.getHealth();
        float expectFront = CombatRules.getDamageAfterAbsorb((float) (10D * (1D - FROST.shieldMeleeReduction())), (float) armor, (float) toughness);
        float expectRear = CombatRules.getDamageAfterAbsorb(10F, (float) armor, (float) toughness);
        helper.assertTrue(near(frontLoss, expectFront) && near(rearLoss, expectRear),
                "正面近战减 30%（" + expectFront + "）、背后全额（" + expectRear + "），实际 " + frontLoss + " / " + rearLoss);

        FrostbittenEntity plain = frost(helper, new Vec3(1.5D, 2D, 1.5D));
        plain.setYRot(0F);
        helper.assertTrue(plain.hurt(ECDamageTypes.mobBolt(ahead), 6F), "普通霜蚀者没有冰盾");
        helper.succeed();
    }

    // ================================================================== 浮游棱晶

    /** 生成：30 / 12、满血、会飞不受重力；怕近战（倍率 > 1）、不怕箭（倍率 < 1）；棱晶集束（精英）生命 75，读档两遍不叠；不能收魂 */
    @GameTest(template = EMPTY)
    public static void manaPrismStatsAndElite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ManaPrismEntity prism = make(helper, ECHoannaNativesA.MANA_PRISM.get(), new Vec3(2.5D, 2D, 2.5D));
        finalizeSpawn(prism, level, MobSpawnType.NATURAL, 0D);
        helper.assertTrue(near(prism.getMaxHealth(), 30D) && near(prism.getHealth(), 30D) && near(prism.getAttributeValue(Attributes.ARMOR), 12D),
                "生命 30、护甲 12，实际 " + prism.getMaxHealth() + " / " + prism.getAttributeValue(Attributes.ARMOR));
        helper.assertTrue(prism.isNoGravity() && !prism.isElite(), "会飞、几率 0 时不是精英");
        Husk hitter = make(helper, EntityType.HUSK, new Vec3(1.5D, 2D, 1.5D));
        float melee = prism.weaknessMultiplier(level.damageSources().mobAttack(hitter));
        float shot = prism.weaknessMultiplier(ECDamageTypes.mobBolt(hitter));
        helper.assertTrue(near(melee, ECMobFramework.Config.weakness()) && near(shot, ECMobFramework.Config.resistance()),
                "怕近战（默认 ×1.5）、不怕箭（默认 ×0.5），实际 " + melee + " / " + shot);
        helper.assertFalse(prism.getType().is(DemonTrades.SOUL_CAPTURABLE), "浮游棱晶不能收魂");

        ManaPrismEntity cluster = make(helper, ECHoannaNativesA.MANA_PRISM.get(), new Vec3(3.5D, 2D, 3.5D));
        finalizeSpawn(cluster, level, MobSpawnType.NATURAL, 1D);
        helper.assertTrue(cluster.isElite() && near(cluster.getMaxHealth(), 75D), "棱晶集束生命 75，实际 " + cluster.getMaxHealth());
        ManaPrismEntity again = reloadTwice(cluster);
        helper.assertTrue(again.isElite() && near(again.getMaxHealth(), 75D) && eliteModifierCount(again) == 4, "读档两遍不叠");
        helper.succeed();
    }

    /**
     * 锁定与开火：起手冷却好了、射程内看得见才锁；锁 30 刻、方向定在那一刻；锁定时横移出光路就躲掉；方块挡得住；
     * 打中当前目标的尸壳伤害按护甲算过的 5 + 20 × 3%（mob_bolt）；锁完挂 80 刻冷却。
     */
    @GameTest(template = LARGE)
    public static void manaPrismLockAndDodge(GameTestHelper helper) {
        ManaPrismEntity prism = prism(helper, new Vec3(1.5D, 3D, 5.5D));
        Player target = bystander(helper, new Vec3(9.5D, 2D, 5.5D), false);
        Husk husk = husk(helper, new Vec3(7.5D, 2D, 2.5D));
        try {
            helper.assertFalse(prism.canLockOn(target), "生成时带起手冷却");
            ready(prism, "lock_cooldown");
            helper.assertTrue(prism.canLockOn(target), "冷却好了、8 格外看得见：能锁");
            prism.startLock(target);
            Vec3 dir = prism.lockDirection();
            helper.assertTrue(prism.lockTicks() == PRISM.lockTicks() && dir != null && dir.x > 0.98D, "锁 " + PRISM.lockTicks() + " 刻、朝着目标：" + dir);
            helper.assertFalse(prism.canLockOn(target), "锁定时、冷却时不再锁");
            moveTo(helper, target, new Vec3(9.5D, 2D, 8.0D));
            List<LivingEntity> dodged = prism.fireBeams();
            helper.assertTrue(dodged.isEmpty() && near(target.getHealth(), target.getMaxHealth()), "锁定时横移出光路：躲掉");
            helper.assertTrue(prism.lockTicks() == 0 && prism.lockDirection() == null && prism.fireBeams().isEmpty(), "开完火回到没在锁、再开火什么都不做");

            moveTo(helper, target, new Vec3(9.5D, 2D, 5.5D));
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(5, y, 5), Blocks.STONE);
            }
            prism.startLock(target);
            List<LivingEntity> blocked = prism.fireBeams();
            helper.assertTrue(blocked.isEmpty() && near(target.getHealth(), target.getMaxHealth()), "方块挡住：打不着");
            ManaPrismEntity.BeamTrace trace = prism.trace(prism.beamOrigin(), new Vec3(1D, 0D, 0D), PRISM.beamRange());
            helper.assertTrue(trace.hit() == null && trace.end().x <= helper.absoluteVec(new Vec3(5D, 0D, 0D)).x + EPS, "光束停在墙面上");
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(5, y, 5), Blocks.AIR);
            }
            prism.startLock(target);
            helper.assertTrue(prism.fireBeams().contains(target) && target.getHealth() < target.getMaxHealth(), "光路通了：打中");

            prism.setTarget(husk);
            prism.startLock(husk);
            float before = husk.getHealth();
            List<LivingEntity> struck = prism.fireBeams();
            float expect = CombatRules.getDamageAfterAbsorb((float) (PRISM.beamDamage() + husk.getMaxHealth() * PRISM.beamHealthPercent()),
                    husk.getArmorValue(), (float) husk.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            helper.assertTrue(struck.contains(husk) && near(before - husk.getHealth(), expect),
                    "尸壳挨护甲算过的 5.6 点（" + expect + "），实际 " + (before - husk.getHealth()));
        } finally {
            discard(target, husk);
        }
        helper.succeed();
    }

    /** 棱晶集束：一次扇出三束（中线 ± 15°），三个方向上各站一人各挨一下；三束都穿过同一人时只挨一下；普通棱晶只一束 */
    @GameTest(template = LARGE)
    public static void manaPrismEliteFan(GameTestHelper helper) {
        ManaPrismEntity plain = prism(helper, new Vec3(1.5D, 3D, 1.5D));
        plain.startLock(new Vec3(1D, 0D, 0D));
        helper.assertTrue(plain.beamDirections().size() == 1, "普通棱晶只一束");
        plain.fireBeams();

        ManaPrismEntity cluster = prism(helper, new Vec3(1.5D, 3D, 5.5D));
        cluster.setElite(true);
        cluster.startLock(new Vec3(1D, 0D, 0D));
        List<Vec3> dirs = cluster.beamDirections();
        double spread = Math.cos(Math.toRadians(PRISM.eliteSpread()));
        helper.assertTrue(dirs.size() == PRISM.eliteBeams(), "集束 " + PRISM.eliteBeams() + " 束，实际 " + dirs.size());
        helper.assertTrue(near(dirs.get(1).x, 1D) && near(dirs.get(0).x, spread) && near(dirs.get(2).x, spread)
                && dirs.get(0).z * dirs.get(2).z < 0D, "中线一束、两侧各偏 " + PRISM.eliteSpread() + "°：" + dirs);

        Vec3 origin = cluster.beamOrigin();
        List<Player> line = new ArrayList<>();
        for (Vec3 d : dirs) {
            Vec3 center = origin.add(d.scale(7D));
            Player p = new Player(helper.getLevel(), BlockPos.ZERO, 0F, new GameProfile(UUID.randomUUID(), "n1-fan")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            p.moveTo(center.x, center.y - 0.9D, center.z, 0F, 0F);
            helper.getLevel().addFreshEntity(p);
            line.add(p);
        }
        Player close = null;
        try {
            List<LivingEntity> fan = cluster.fireBeams();
            helper.assertTrue(fan.size() == 3 && fan.containsAll(line), "三个方向上的人各挨一下，实际 " + fan.size());
            for (Player p : line) {
                p.discard();
            }
            Vec3 closeAt = origin.add(1.3D, 0D, 0D);
            close = bystander(helper, Vec3.ZERO, false);
            close.moveTo(closeAt.x, closeAt.y - 0.9D, closeAt.z, 0F, 0F);
            float before = close.getHealth();
            cluster.startLock(new Vec3(1D, 0D, 0D));
            List<LivingEntity> once = cluster.fireBeams();
            helper.assertTrue(once.size() == 1 && once.contains(close) && close.getHealth() < before, "三束都穿过同一人：只算一次，实际 " + once.size());
        } finally {
            for (Player p : line) {
                discard(p);
            }
            discard(close);
        }
        helper.succeed();
    }

    // ================================================================== 遗民拾荒者

    /**
     * 生成：36 / 8；拿步枪的攻击 6、拿撬棍的 9（rifle_chance 钉成 1 / 0）；武器存盘；遗民队长生命 90、撬棍 12.6，读档两遍不叠；
     * 换武器时攻击跟着换。拾荒者能收魂。
     */
    @GameTest(template = EMPTY)
    public static void scavengerStatsAndWeapon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HoannaScavengerEntity rifleman = make(helper, ECHoannaNativesA.HOANNA_SCAVENGER.get(), new Vec3(1.5D, 2D, 1.5D));
        HoannaScavengerEntity brawler = make(helper, ECHoannaNativesA.HOANNA_SCAVENGER.get(), new Vec3(3.5D, 2D, 1.5D));
        HoannaScavengerEntity captain = make(helper, ECHoannaNativesA.HOANNA_SCAVENGER.get(), new Vec3(2.5D, 2D, 3.5D));
        ECHoannaNativesA.Config.setTestScavenger(scavWith(1D, SCAV.stimHealth(), SCAV.eliteSummonCount(), SCAV.surfaceMinY(), SCAV.nearbyCap()));
        try {
            finalizeSpawn(rifleman, level, MobSpawnType.NATURAL, 0D);
        } finally {
            ECHoannaNativesA.Config.setTestScavenger(null);
        }
        ECHoannaNativesA.Config.setTestScavenger(scavWith(0D, SCAV.stimHealth(), SCAV.eliteSummonCount(), SCAV.surfaceMinY(), SCAV.nearbyCap()));
        try {
            finalizeSpawn(brawler, level, MobSpawnType.NATURAL, 0D);
            finalizeSpawn(captain, level, MobSpawnType.NATURAL, 1D);
        } finally {
            ECHoannaNativesA.Config.setTestScavenger(null);
        }
        helper.assertTrue(near(rifleman.getMaxHealth(), 36D) && near(rifleman.getAttributeValue(Attributes.ARMOR), 8D), "生命 36、护甲 8");
        helper.assertTrue(rifleman.weapon() == Weapon.RIFLE && near(rifleman.getAttributeValue(Attributes.ATTACK_DAMAGE), 6D), "拿步枪：攻击 6");
        helper.assertTrue(brawler.weapon() == Weapon.CROWBAR && near(brawler.getAttributeValue(Attributes.ATTACK_DAMAGE), 9D), "拿撬棍：攻击 9");
        HoannaScavengerEntity reloaded = ECHoannaNativesA.HOANNA_SCAVENGER.get().create(level);
        reloaded.load(rifleman.saveWithoutId(new CompoundTag()));
        helper.assertTrue(reloaded.weapon() == Weapon.RIFLE && near(reloaded.getAttributeValue(Attributes.ATTACK_DAMAGE), 6D), "武器和攻击存盘");
        helper.assertTrue(rifleman.getType().is(DemonTrades.SOUL_CAPTURABLE), "拾荒者能收魂（soul_capturable）");

        helper.assertTrue(captain.isElite() && near(captain.getMaxHealth(), 90D) && near(captain.getAttributeValue(Attributes.ATTACK_DAMAGE), 12.6D),
                "遗民队长生命 90、撬棍 12.6，实际 " + captain.getMaxHealth() + " / " + captain.getAttributeValue(Attributes.ATTACK_DAMAGE));
        HoannaScavengerEntity again = reloadTwice(captain);
        helper.assertTrue(again.isElite() && again.weapon() == Weapon.CROWBAR && near(again.getMaxHealth(), 90D) && eliteModifierCount(again) == 4,
                "读档两遍不叠、武器还在");
        again.setWeapon(Weapon.RIFLE);
        helper.assertTrue(near(again.getAttributeValue(Attributes.ATTACK_DAMAGE), 6D * 1.4D), "队长换成步枪：6 × 1.4，实际 " + again.getAttributeValue(Attributes.ATTACK_DAMAGE));
        helper.succeed();
    }

    /**
     * 辐射：撬棍打实 +150、步枪打实 +300，都乘玩家的辐射易伤（真打一下走 LivingDamageEvent，和直接调 irradiate 一致）；
     * 别的伤害（技能）不加；创造模式、假玩家不加；步枪被方块挡住不打、不加。
     */
    @GameTest(template = EMPTY)
    public static void scavengerHitIrradiates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper, "n1-scav-rad", new Vec3(2.5D, 2D, 3.5D));
        HoannaScavengerEntity brawler = scavenger(helper, new Vec3(2.5D, 2D, 2.5D), Weapon.CROWBAR);
        HoannaScavengerEntity rifleman = scavenger(helper, new Vec3(2.5D, 2D, 0.5D), Weapon.RIFLE);
        boolean on = HoannaHazardsConfig.radiationEnabled();
        float vulnerability = MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION);
        long crowbar = on ? Radiation.flooredAmount(SCAV.crowbarDose(), vulnerability, 0F) : 0L;
        long rifle = on ? Radiation.flooredAmount(SCAV.rifleDose(), vulnerability, 0F) : 0L;

        Radiation.set(player, 0);
        helper.assertTrue(brawler.irradiate(player, level.damageSources().mobAttack(brawler)) == crowbar, "撬棍：+" + crowbar);
        helper.assertTrue(rifleman.irradiate(player, ECDamageTypes.mobBolt(rifleman)) == crowbar + rifle, "步枪：再 +" + rifle);
        helper.assertTrue(brawler.irradiate(player, ECDamageTypes.aberrantStrike(brawler)) == crowbar + rifle, "别的伤害不加");

        Radiation.set(player, 0);
        player.invulnerableTime = 0;
        helper.assertTrue(brawler.doHurtTarget(player), "撬棍真打一下");
        helper.assertTrue(Radiation.get(player) == crowbar, "撬棍打实：+" + crowbar + "，实际 " + Radiation.get(player));
        player.invulnerableTime = 0;
        helper.assertTrue(rifleman.rifleCanHit(player) && rifleman.shootRifle(player), "步枪真打一枪");
        helper.assertTrue(Radiation.get(player) == crowbar + rifle, "步枪打实：再 +" + rifle + "，实际 " + Radiation.get(player));

        helper.setBlock(new BlockPos(2, 3, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
        player.invulnerableTime = 0;
        helper.assertTrue(!rifleman.rifleCanHit(player) && !rifleman.shootRifle(player) && Radiation.get(player) == crowbar + rifle, "被方块挡住：不打、不加");

        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(brawler.irradiate(player, level.damageSources().mobAttack(brawler)) == crowbar + rifle, "创造模式不加");
        ServerPlayer fake = FakePlayerFactory.getMinecraft(level);
        helper.assertTrue(brawler.irradiate(fake, level.damageSources().mobAttack(brawler)) == 0 && Radiation.get(fake) == 0, "假玩家不加");
        Radiation.set(player, 0);
        helper.succeed();
    }

    /** 打针：生命到 25% 以下才打，再生 II 60 刻、冷却 1200 刻（存盘）；冷却时不打；stim_health 填 0 不打 */
    @GameTest(template = EMPTY)
    public static void scavengerStimCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HoannaScavengerEntity scav = scavenger(helper, new Vec3(2.5D, 2D, 2.5D), Weapon.CROWBAR);
        scav.setHealth(10F);
        helper.assertFalse(scav.wantsStim(), "10 / 36 还没到 25%：不打");
        scav.setHealth(9F);
        helper.assertTrue(scav.wantsStim(), "9 / 36 = 25%：打");
        ECHoannaNativesA.Config.setTestScavenger(scavWith(SCAV.rifleChance(), 0D, SCAV.eliteSummonCount(), SCAV.surfaceMinY(), SCAV.nearbyCap()));
        try {
            helper.assertFalse(scav.wantsStim(), "stim_health 0：不打");
        } finally {
            ECHoannaNativesA.Config.setTestScavenger(null);
        }
        scav.stim();
        MobEffectInstance regen = scav.getEffect(MobEffects.REGENERATION);
        helper.assertTrue(regen != null && regen.getAmplifier() == HoannaScavengerEntity.STIM_AMPLIFIER && regen.getDuration() == SCAV.stimTicks(),
                "再生 II " + SCAV.stimTicks() + " 刻：" + regen);
        helper.assertTrue(scav.stimCooldown() == SCAV.stimCooldown() && !scav.wantsStim(), "打完挂 " + SCAV.stimCooldown() + " 刻冷却、冷却时不打");
        HoannaScavengerEntity reloaded = ECHoannaNativesA.HOANNA_SCAVENGER.get().create(level);
        reloaded.load(scav.saveWithoutId(new CompoundTag()));
        helper.assertTrue(reloaded.stimCooldown() == SCAV.stimCooldown() && !reloaded.wantsStim(), "冷却存盘：读档后还在冷却");
        helper.succeed();
    }

    /**
     * 遗民队长的信号弹：生命到 50% 以下才想打；叫来 2 名（来源召唤、不公平、不是精英、没经验、记着队长、盯着队长的目标）；只打一次（存盘）；
     * 普通拾荒者不打；elite_summon_count 填 0 不打；生成被取消一名都不进世界。
     */
    @GameTest(template = LARGE)
    public static void scavengerCaptainFlare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        stoneFloor(helper, 11);
        HoannaScavengerEntity plain = scavenger(helper, new Vec3(2.5D, 2D, 2.5D), Weapon.CROWBAR);
        HoannaScavengerEntity captain = scavenger(helper, new Vec3(5.5D, 2D, 5.5D), Weapon.RIFLE);
        captain.setElite(true);
        HoannaScavengerEntity rival = scavenger(helper, new Vec3(8.5D, 2D, 8.5D), Weapon.CROWBAR);
        rival.setElite(true);
        ServerPlayer player = survivor(helper, "n1-flare", new Vec3(5.5D, 2D, 9.5D));
        List<HoannaScavengerEntity> spawned = new ArrayList<>();
        Consumer<MobSpawnEvent.FinalizeSpawn> cancel = event -> {
            if (event.getEntity() instanceof HoannaScavengerEntity minion && rival.getUUID().equals(minion.captain())) {
                event.setSpawnCancelled(true);
            }
        };
        try {
            helper.assertTrue(plain.fireFlare(player) == 0, "普通拾荒者不打信号弹");
            helper.assertFalse(captain.wantsFlare(), "满血时不想打");
            captain.setHealth(captain.getMaxHealth() * 0.5F);
            helper.assertTrue(captain.wantsFlare(), "到 50%：想打");
            ECHoannaNativesA.Config.setTestScavenger(scavWith(SCAV.rifleChance(), SCAV.stimHealth(), 0, SCAV.surfaceMinY(), SCAV.nearbyCap()));
            try {
                helper.assertTrue(!captain.wantsFlare() && captain.fireFlare(player) == 0 && !captain.flareUsed(), "elite_summon_count 0：不打、也不算打过");
            } finally {
                ECHoannaNativesA.Config.setTestScavenger(null);
            }
            int count;
            ECMobFramework.Config.setTestEliteChance(1D);
            try {
                count = captain.fireFlare(player);
            } finally {
                ECMobFramework.Config.setTestEliteChance(null);
            }
            spawned.addAll(level.getEntitiesOfClass(HoannaScavengerEntity.class, captain.getBoundingBox().inflate(8D), e -> captain.getUUID().equals(e.captain())));
            helper.assertTrue(count == SCAV.eliteSummonCount() && spawned.size() == count && captain.minionCount() == count,
                    "叫来 " + SCAV.eliteSummonCount() + " 名，实际 " + count + " / " + spawned.size());
            for (HoannaScavengerEntity minion : spawned) {
                helper.assertTrue(SpawnOrigin.of(minion) == Origin.SUMMONED && !SpawnOrigin.isFair(minion), "来源是召唤、不公平");
                helper.assertTrue(!minion.isElite() && minion.getExperienceReward() == 0, "叫来的不是精英、不给经验");
                helper.assertTrue(minion.getTarget() == player, "叫来就盯着队长的目标");
                MobDamageShare.record(minion, player.getUUID(), 36F);
                helper.assertFalse(FairKillCondition.test(minion, player), "亲手打死叫来的也不算公平击杀");
            }
            helper.assertTrue(captain.flareUsed() && !captain.wantsFlare() && captain.fireFlare(player) == 0, "只打一次");
            HoannaScavengerEntity reloaded = ECHoannaNativesA.HOANNA_SCAVENGER.get().create(level);
            reloaded.load(captain.saveWithoutId(new CompoundTag()));
            helper.assertTrue(reloaded.flareUsed() && !reloaded.wantsFlare(), "打过的存盘：读档后不再打");

            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MobSpawnEvent.FinalizeSpawn.class, cancel);
            int canceled;
            try {
                canceled = rival.fireFlare(player);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(canceled == 0 && rival.minionCount() == 0, "生成被取消：一名都不进世界");
        } finally {
            for (HoannaScavengerEntity minion : spawned) {
                discard(minion);
            }
            for (HoannaScavengerEntity left : level.getEntitiesOfClass(HoannaScavengerEntity.class, captain.getBoundingBox().inflate(16D),
                    e -> e.captain() != null)) {
                discard(left);
            }
        }
        helper.succeed();
    }

    /**
     * 收魂与交易池：交易池里有一条「遗民灵魂 ×2」（池子总数由 DemonTests 守着）；灵魂刻刀对残血的野外拾荒者收得到，
     * 对残血的遗民队长、队长叫来的（来源召唤）收不到、也不补那一刀。
     */
    @GameTest(template = EMPTY)
    public static void scavengerSoulTrade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<DemonTrades.Entry> souls = DemonTrades.entries().stream()
                .filter(e -> e.isSoul() && EssentialCraft5.id("hoanna_scavenger").equals(e.soul())).toList();
        helper.assertTrue(souls.size() == 1 && souls.get(0).min() == 2 && souls.get(0).max() == 2, "交易池里有一条遗民灵魂 ×2，实际 " + souls.size());

        ServerPlayer player = survivor(helper, "n1-soul", new Vec3(1.5D, 2D, 1.5D));
        ItemStack scriber = new ItemStack(ECDemonic.SOUL_SCRIBER.get());
        HoannaScavengerEntity wild = scavenger(helper, new Vec3(1.5D, 2D, 3.5D), Weapon.CROWBAR);
        SpawnOrigin.set(wild, Origin.NATURAL);
        wild.setHealth(2F);
        HoannaScavengerEntity captain = scavenger(helper, new Vec3(3.5D, 2D, 1.5D), Weapon.CROWBAR);
        captain.setElite(true);
        captain.setHealth(2F);
        HoannaScavengerEntity minion = scavenger(helper, new Vec3(3.5D, 2D, 3.5D), Weapon.RIFLE);
        SpawnOrigin.set(minion, Origin.SUMMONED);
        minion.setHealth(2F);
        EntityType<HoannaScavengerEntity> type = ECHoannaNativesA.HOANNA_SCAVENGER.get();
        List<ItemEntity> drops = new ArrayList<>();
        try {
            scriber.getItem().hurtEnemy(scriber, wild, player);
            List<ItemEntity> wildDrops = level.getEntitiesOfClass(ItemEntity.class, wild.getBoundingBox().inflate(1D),
                    item -> CapturedSoulItem.matches(item.getItem(), type));
            drops.addAll(wildDrops);
            helper.assertTrue(wild.isDeadOrDying() && wildDrops.size() == 1, "野外的拾荒者：收到一个它的灵魂，实际 " + wildDrops.size());
            for (HoannaScavengerEntity refused : List.of(captain, minion)) {
                scriber.getItem().hurtEnemy(scriber, refused, player);
                List<ItemEntity> got = level.getEntitiesOfClass(ItemEntity.class, refused.getBoundingBox().inflate(1D),
                        item -> item.getItem().is(ECDemonic.CAPTURED_SOUL.get()) && !drops.contains(item));
                drops.addAll(got);
                helper.assertTrue(refused.isAlive() && near(refused.getHealth(), 2D) && got.isEmpty(),
                        (refused.isElite() ? "遗民队长" : "叫来的") + "收不了魂、也不补刀");
            }
        } finally {
            for (ItemEntity drop : drops) {
                discard(drop);
            }
        }
        helper.succeed();
    }

    // ================================================================== 掉落

    private static List<ItemStack> lootFor(ServerLevel level, LivingEntity victim, ServerPlayer killer, String table, long seed) {
        LootTable loot = level.getServer().getLootData().getLootTable(EssentialCraft5.id(table));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, killer.damageSources().playerAttack(killer))
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);
        return loot.getRandomItems(params, seed);
    }

    private static int countOf(List<ItemStack> drops, Item item) {
        int count = 0;
        for (ItemStack stack : drops) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(id));
        if (item == null || item == Items.AIR) {
            throw new IllegalStateException("物品 " + id + " 应已注册");
        }
        return item;
    }

    /** 读掉落表 JSON 里某件东西那一池的条件：[条件类型, 组, 几率] */
    private static List<String[]> poolConditions(ServerLevel level, String table, String itemId) {
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(EssentialCraft5.id("loot_tables/" + table + ".json"));
        if (resource.isEmpty()) {
            throw new IllegalStateException("掉落表 " + table + " 应存在");
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement pool : root.getAsJsonArray("pools")) {
                JsonObject p = pool.getAsJsonObject();
                boolean mine = false;
                for (JsonElement entry : p.getAsJsonArray("entries")) {
                    JsonObject e = entry.getAsJsonObject();
                    mine |= e.has("name") && e.get("name").getAsString().equals(itemId);
                }
                if (!mine) {
                    continue;
                }
                List<String[]> out = new ArrayList<>();
                JsonArray conditions = p.has("conditions") ? p.getAsJsonArray("conditions") : new JsonArray();
                for (JsonElement c : conditions) {
                    JsonObject o = c.getAsJsonObject();
                    out.add(new String[]{o.get("condition").getAsString(), o.has("group") ? o.get("group").getAsString() : "",
                            o.has("chance") ? o.get("chance").getAsString() : ""});
                }
                return out;
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return List.of();
    }

    /**
     * 掉落：关键掉落（异变残渣、魔法精华、钛石、异变之心）一律先 fair_kill 再 kill_fatigue（组和几率照 batch13.md §5.4）；
     * 野外的、玩家亲手打死的掉得出异变残渣、从不掉异变之心；刷怪笼来的关键物一样都不掉；精英掉得出异变之心。
     */
    @GameTest(template = EMPTY)
    public static void hoannaNativesALootTables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        record Key(String item, String group, String chance) {
        }
        record Case(String table, Supplier<? extends ECMonster> make, List<Key> keys) {
        }
        List<Case> cases = List.of(
                new Case("entities/frostbitten", () -> frost(helper, new Vec3(2.5D, 2D, 2.5D)),
                        List.of(new Key("essentialcraft5:aberrant_residue", "hoanna_common", "0.35"), new Key("essentialcraft5:aberrant_heart", "elite", "0.5"))),
                new Case("entities/mana_prism", () -> prism(helper, new Vec3(2.5D, 2D, 2.5D)),
                        List.of(new Key("essentialcraft5:aberrant_residue", "hoanna_common", "0.35"), new Key("essentialcraft5:magical_essence", "hoanna_common", "0.15"),
                                new Key("essentialcraft5:aberrant_heart", "elite", "0.5"))),
                new Case("entities/hoanna_scavenger", () -> scavenger(helper, new Vec3(2.5D, 2D, 2.5D), Weapon.CROWBAR),
                        List.of(new Key("essentialcraft5:titanite", "hoanna_titanite", "0.15"), new Key("essentialcraft5:aberrant_residue", "hoanna_common", "0.2"),
                                new Key("essentialcraft5:aberrant_heart", "elite", "0.5"))));
        Item residue = item("aberrant_residue");
        Item heart = item("aberrant_heart");
        long seed = 0L;
        for (Case c : cases) {
            List<Item> keyItems = new ArrayList<>();
            for (Key key : c.keys()) {
                List<String[]> conditions = poolConditions(level, c.table(), key.item());
                int fair = -1;
                int fatigue = -1;
                for (int i = 0; i < conditions.size(); i++) {
                    if (conditions.get(i)[0].equals("essentialcraft5:fair_kill")) {
                        fair = i;
                    } else if (conditions.get(i)[0].equals("essentialcraft5:kill_fatigue")) {
                        fatigue = i;
                        helper.assertTrue(conditions.get(i)[1].equals(key.group()) && near(Double.parseDouble(conditions.get(i)[2]), Double.parseDouble(key.chance())),
                                c.table() + " 的 " + key.item() + "：组 " + key.group() + "、几率 " + key.chance() + "，实际 " + conditions.get(i)[1] + " / " + conditions.get(i)[2]);
                    }
                }
                helper.assertTrue(fair >= 0 && fatigue > fair, c.table() + " 的 " + key.item() + " 要先 fair_kill 再 kill_fatigue");
                keyItems.add(ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse(key.item())));
            }

            // 同一只怪对同一名玩家每刻只记一次击杀（KillFatigueCondition），反复掷表不会自己把自己打折
            ECMonster wild = c.make().get();
            SpawnOrigin.set(wild, Origin.NATURAL);
            ServerPlayer killer = survivor(helper, "n1-loot-a", new Vec3(1.5D, 2D, 1.5D));
            MobDamageShare.record(wild, killer.getUUID(), 40F);
            // 对照：这一小时已经杀了 1000 只海纳尔普通怪的玩家，同样的种子掷同一只
            ECMonster farmed = c.make().get();
            SpawnOrigin.set(farmed, Origin.NATURAL);
            ServerPlayer grinder = survivor(helper, "n1-loot-f", new Vec3(1.5D, 2D, 1.5D));
            var grinderData = com.flameforge.essentialcraft5.player.PlayerDataEvents.get(grinder);
            helper.assertTrue(grinderData != null, "测试前提：玩家数据在");
            for (int i = 0; i < 1000; i++) {
                com.flameforge.essentialcraft5.player.MobKillLedger.addKill(grinderData, "hoanna_common");
            }
            MobDamageShare.record(farmed, grinder.getUUID(), 40F);
            int residues = 0;
            int hearts = 0;
            int farmedResidues = 0;
            for (int i = 0; i < 24; i++) {
                long s = (seed++) * 0x9E3779B97F4A7C15L;
                List<ItemStack> drops = lootFor(level, wild, killer, c.table(), s);
                residues += countOf(drops, residue);
                hearts += countOf(drops, heart);
                farmedResidues += countOf(lootFor(level, farmed, grinder, c.table(), s), residue);
            }
            helper.assertTrue(residues > 0 && hearts == 0, c.table() + "：野外的掉得出异变残渣、不掉异变之心，实际 " + residues + " / " + hearts);
            helper.assertTrue(farmedResidues < residues && farmedResidues <= 2,
                    c.table() + "：hoanna_common 递减生效（杀了 1000 只的掉得少得多），实际 " + farmedResidues + " / " + residues);

            ECMonster caged = c.make().get();
            SpawnOrigin.set(caged, Origin.SPAWNER);
            caged.setElite(true);
            ServerPlayer farmer = survivor(helper, "n1-loot-b", new Vec3(1.5D, 2D, 1.5D));
            MobDamageShare.record(caged, farmer.getUUID(), 100F);
            int cagedKeys = 0;
            for (int i = 0; i < 24; i++) {
                List<ItemStack> drops = lootFor(level, caged, farmer, c.table(), (seed++) * 0x9E3779B97F4A7C15L);
                for (Item key : keyItems) {
                    cagedKeys += countOf(drops, key);
                }
            }
            helper.assertTrue(cagedKeys == 0, c.table() + "：刷怪笼来的（哪怕是精英）关键物一样都不掉，实际 " + cagedKeys);

            ECMonster elite = c.make().get();
            elite.setElite(true);
            SpawnOrigin.set(elite, Origin.NATURAL);
            ServerPlayer hunter = survivor(helper, "n1-loot-c", new Vec3(1.5D, 2D, 1.5D));
            MobDamageShare.record(elite, hunter.getUUID(), 100F);
            int eliteHearts = 0;
            for (int i = 0; i < 16; i++) {
                eliteHearts += countOf(lootFor(level, elite, hunter, c.table(), (seed++) * 0x9E3779B97F4A7C15L), heart);
            }
            helper.assertTrue(eliteHearts > 0, c.table() + "：精英掉得出异变之心");
        }
        KillFatigueCondition.forgetCounted();
        helper.succeed();
    }

    // ================================================================== 刷怪判定（自己一批）

    /**
     * 拾荒者的白天地表变体：脚下 y ≥ surface_min_y、头顶露天的格子不看亮度（旁边亮着萤石也刷，原版怪物做不到）；
     * 盖了顶的格子照原版怪物（亮着就不刷）；64 格内已有 nearby_cap 只就不刷。纯函数 surfaceRule 另测。
     * surface_min_y 用测试覆盖值钉成测试区的高度（超平坦地表在 y ≈ -60）。天光要等光照更新，所以用 succeedWhen 等。
     */
    @GameTest(template = LARGE, batch = SPAWN_BATCH, timeoutTicks = 200)
    public static void scavengerSurfaceSpawnRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(HoannaScavengerEntity.surfaceRule(60, true, 60) && !HoannaScavengerEntity.surfaceRule(59, true, 60)
                && !HoannaScavengerEntity.surfaceRule(80, false, 60), "纯函数：露天且 y ≥ 下限");
        stoneFloor(helper, 11);
        // 露天格 (2, 2, 2)，旁边一块萤石照亮
        BlockPos openRel = new BlockPos(2, 2, 2);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.GLOWSTONE);
        // 盖顶格 (8, 2, 8)：头顶一块石头，旁边萤石
        BlockPos coveredRel = new BlockPos(8, 2, 8);
        helper.setBlock(new BlockPos(8, 4, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(7, 2, 8), Blocks.GLOWSTONE);
        BlockPos open = helper.absolutePos(openRel);
        BlockPos covered = helper.absolutePos(coveredRel);
        int minY = open.getY();
        EntityType<HoannaScavengerEntity> type = ECHoannaNativesA.HOANNA_SCAVENGER.get();
        List<HoannaScavengerEntity> crowd = new ArrayList<>();
        for (int i = 0; i < SCAV.nearbyCap(); i++) {
            HoannaScavengerEntity extra = scavenger(helper, new Vec3(1.5D + i * 2D, 2D, 9.5D), Weapon.CROWBAR);
            extra.setNoAi(true);
            level.addFreshEntity(extra);
            crowd.add(extra);
        }
        HoannaScavengerEntity probe = scavenger(helper, Vec3.atBottomCenterOf(openRel), Weapon.CROWBAR);
        HoannaScavengerEntity coveredProbe = scavenger(helper, Vec3.atBottomCenterOf(coveredRel), Weapon.CROWBAR);
        helper.succeedWhen(() -> {
            RandomSource random = RandomSource.create(1L);
            boolean peaceful = level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
            ECHoannaNativesA.Config.setTestScavenger(scavWith(SCAV.rifleChance(), SCAV.stimHealth(), SCAV.eliteSummonCount(), minY, SCAV.nearbyCap() + 1));
            try {
                helper.assertTrue(level.canSeeSky(open) && !level.canSeeSky(covered), "等光照：露天格看得见天、盖顶格看不见");
                helper.assertTrue(HoannaScavengerEntity.isSurfaceSpot(level, open) && !HoannaScavengerEntity.isSurfaceSpot(level, covered), "地表变体的位置判定");
                helper.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, covered) > 0, "等光照：盖顶格被萤石照亮");
                MobSpawnRules.clearCache();
                helper.assertTrue(peaceful != SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, open, random),
                        "露天地表：亮着也刷（和平时不刷）");
                MobSpawnRules.clearCache();
                helper.assertFalse(SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, covered, random), "盖了顶、亮着：不刷");
                helper.assertTrue(probe.checkSpawnRules(level, MobSpawnType.NATURAL) && !coveredProbe.checkSpawnRules(level, MobSpawnType.NATURAL),
                        "实体那一关：露天地表放行，盖顶亮处照原版拦下");
            } finally {
                ECHoannaNativesA.Config.setTestScavenger(null);
            }
            ECHoannaNativesA.Config.setTestScavenger(scavWith(SCAV.rifleChance(), SCAV.stimHealth(), SCAV.eliteSummonCount(), minY + 1, SCAV.nearbyCap() + 1));
            try {
                MobSpawnRules.clearCache();
                helper.assertFalse(HoannaScavengerEntity.isSurfaceSpot(level, open), "y 低于 surface_min_y：不算地表");
                helper.assertFalse(SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, open, random), "低于下限又亮着：照原版不刷");
            } finally {
                ECHoannaNativesA.Config.setTestScavenger(null);
            }
            ECHoannaNativesA.Config.setTestScavenger(scavWith(SCAV.rifleChance(), SCAV.stimHealth(), SCAV.eliteSummonCount(), minY, SCAV.nearbyCap()));
            try {
                MobSpawnRules.clearCache();
                helper.assertFalse(SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, open, random),
                        "64 格内已有 " + SCAV.nearbyCap() + " 只：不刷");
            } finally {
                ECHoannaNativesA.Config.setTestScavenger(null);
                MobSpawnRules.clearCache();
            }
            for (HoannaScavengerEntity extra : crowd) {
                discard(extra);
            }
        });
    }
}
