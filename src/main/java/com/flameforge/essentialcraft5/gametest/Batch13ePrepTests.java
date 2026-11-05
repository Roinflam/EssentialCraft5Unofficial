package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.BossTier;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.entity.boss.ECBossMonster;
import com.flameforge.essentialcraft5.item.ComputerAttunement;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorEvents;
import com.flameforge.essentialcraft5.item.VoidArmorEvents;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEntities;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaStructures;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECInquisitoriumOutpost;
import com.flameforge.essentialcraft5.registry.ECSealedVault;
import com.flameforge.essentialcraft5.registry.ECShade;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECTownSentry;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.HoannaMidscape;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.OutpostPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.sunken.SunkenPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.VaultPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.SpreadExcludingPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 第十三批 13e 预备的共用件（batch13.md §9.5；细则见 13e 预备说明）：
 * <ul>
 *   <li>{@code damageTypes13e}：两种新伤害类型的注册、缩放、标签、构造出来的伤害源；</li>
 *   <li>{@code damageTypes13eShieldTable}：宅男 0~4 阶、审判庭 0 / 1 / 3 / 4 阶对两种新伤害的口径和 {@code aberrant_strike} 一模一样；</li>
 *   <li>{@code damageTypes13eEndToEnd}：虚空必中闪避也闪不掉两种新伤害；</li>
 *   <li>{@code blocksAndItems13e}：七个方块、五件物品、三个刷怪蛋都注册了，谜题方块挖不动推不动，封印石默认关着，两个方块实体挂在对的方块上；</li>
 *   <li>{@code entities13e}：六个实体的类别、属性默认值、首领档位 / 封顶 / 名单，技能实体不存盘；</li>
 *   <li>{@code entityTags13e}：三只首领在 {@code forge:bosses}、{@code divider_immune}、{@code mru_coil_immune}，三种技能实体在 {@code growth_excluded}；</li>
 *   <li>{@code structures13eRegistered}：三个结构类型、11 个拼件类型注册了且齐，结构的群系只在海纳尔，主世界 / 下界 / 末地都没有这三个结构集；</li>
 *   <li>{@code structureSets13e}：三个结构集的放置参数、多集排除指着对的集；</li>
 *   <li>{@code mutualExclusionTerminates}：宝库、前哨互相排除不递归，挨得近的一对两边都不放、离得远的照常放；</li>
 *   <li>{@code structureTags13e}：{@code blocks_midscape}、{@code has_structure/*}、{@code shade_core_sites}；</li>
 *   <li>{@code pieces13eRoundTripNbt}：11 种拼件存读档相同。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch13ePrepTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1.0E-4D;
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;

    private static final ResourceKey<StructureSet> TOWN_SET = set("hoanna_town");
    private static final ResourceKey<StructureSet> VAULT_SET = set("sealed_vault");
    private static final ResourceKey<StructureSet> SUNKEN_SET = set("sunken_district");
    private static final ResourceKey<StructureSet> OUTPOST_SET = set("inquisitorium_outpost");
    private static final List<ResourceKey<StructureSet>> NEW_SETS = List.of(VAULT_SET, SUNKEN_SET, OUTPOST_SET);

    private Batch13ePrepTests() {
    }

    // ================================================================== 伤害类型

    /** 两种新伤害：注册表里有、按难度缩放、标签（无视护甲、闪不掉，日阵另算火）、不是魔法、盾挡不住、两种构造的攻击者 / 直接实体 */
    @GameTest(template = EMPTY)
    public static void damageTypes13e(GameTestHelper helper) {
        Registry<DamageType> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (ResourceKey<DamageType> key : List.of(ECDamageTypes.CALAMITY, ECDamageTypes.CALAMITY_SOLAR)) {
            DamageType type = registry.get(key);
            helper.assertTrue(type != null, "数据包里要有伤害类型 " + key.location());
            helper.assertTrue(type.scaling() == DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, key.location() + " 的 scaling 应是 when_caused_by_living_non_player");
            helper.assertTrue(type.msgId().equals("essentialcraft5." + key.location().getPath()), key.location() + " 的 message_id");
        }
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Arrow arrow = new Arrow(helper.getLevel(), zombie);
        DamageSource calamity = ECDamageTypes.calamity(zombie);
        DamageSource calamityVia = ECDamageTypes.calamity(arrow, zombie);
        DamageSource solar = ECDamageTypes.calamitySolar(zombie);
        DamageSource solarVia = ECDamageTypes.calamitySolar(arrow, zombie);
        helper.assertTrue(calamity.getEntity() == zombie && calamity.getDirectEntity() == zombie, "calamity(攻击者)");
        helper.assertTrue(calamityVia.getEntity() == zombie && calamityVia.getDirectEntity() == arrow, "calamity(直接实体, 攻击者)");
        helper.assertTrue(solar.getEntity() == zombie && solarVia.getDirectEntity() == arrow && solarVia.getEntity() == zombie, "calamitySolar 两种写法");
        for (DamageSource source : List.of(calamity, solar)) {
            helper.assertTrue(source.is(DamageTypeTags.BYPASSES_ARMOR) && source.is(ECDamageTypes.UNDODGEABLE), source.getMsgId() + "：无视护甲、闪不掉");
            helper.assertTrue(source.is(DamageTypeTags.BYPASSES_SHIELD), source.getMsgId() + "：盾牌挡不住（bypasses_shield 含 #bypasses_armor）");
            helper.assertFalse(ECDamageKinds.isMagic(source) || source.is(ECDamageKinds.FORGE_IS_MAGIC), source.getMsgId() + " 不是魔法");
            helper.assertFalse(VoidArmorEvents.dodgeable(source), source.getMsgId() + "：虚空的闪避 / 相位不作用");
        }
        helper.assertTrue(solar.is(DamageTypeTags.IS_FIRE) && !calamity.is(DamageTypeTags.IS_FIRE), "只有 calamity_solar 算火");
        helper.succeed();
    }

    /** 宅男 0~4 阶、审判庭 0 / 1 / 3 / 4 阶：两种新伤害和 aberrant_strike 的挡法逐阶相同（4 阶起宅男挡一半、审判庭 ×0.7） */
    @GameTest(template = EMPTY)
    public static void damageTypes13eShieldTable(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        DamageSource strike = ECDamageTypes.aberrantStrike(zombie);
        List<DamageSource> news = List.of(ECDamageTypes.calamity(zombie), ECDamageTypes.calamitySolar(zombie));
        ArmorItem.Type chestType = ArmorItem.Type.CHESTPLATE;
        for (int stage = 0; stage <= 4; stage++) {
            ItemStack chest = attuned(new ItemStack(ECComputer.armor().get(chestType.ordinal()).get()), stage);
            IMRUShieldArmor.Coverage expected = ComputerAttunement.coverage(chest, chestType, strike);
            for (DamageSource source : news) {
                IMRUShieldArmor.Coverage actual = ComputerAttunement.coverage(chest, chestType, source);
                boolean same = expected == null ? actual == null : actual != null && actual.extended() == expected.extended()
                        && Math.abs(actual.share() - expected.share()) < EPS && Math.abs(actual.priceMultiplier() - expected.priceMultiplier()) < EPS;
                helper.assertTrue(same, "宅男 " + stage + " 阶挡 " + source.getMsgId() + " 应和 aberrant_strike 一样：" + expected + " / " + actual);
            }
            if (stage >= ComputerAttunement.PIERCING_STAGE) {
                helper.assertTrue(expected != null, "对照组：宅男 4 阶挡 aberrant_strike");
            }
        }
        int[] stages = {0, InquisitoriumArmorEvents.ABSOLUTION_STAGE, InquisitoriumArmorEvents.BULWARK_STAGE, InquisitoriumArmorEvents.TOP_STAGE};
        try {
            for (int stage : stages) {
                inquisitoriumSuit(victim, stage);
                double expected = InquisitoriumArmorEvents.incomingMultiplier(victim, strike);
                for (DamageSource source : news) {
                    close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, source), expected, "审判庭 " + stage + " 阶对 " + source.getMsgId());
                }
                if (stage >= InquisitoriumArmorEvents.TOP_STAGE) {
                    close(helper, expected, 1D - ECInquisitorium.Config.attuned().piercingResistance(), "对照组：审判庭 4 阶对 aberrant_strike");
                }
            }
        } finally {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                victim.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        helper.succeed();
    }

    /** 真挨一下：虚空胸甲闪避掷骰钉成必中，两种新伤害照样掉血 */
    @GameTest(template = EMPTY)
    public static void damageTypes13eEndToEnd(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        victim.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()));
        VoidArmorEvents.setTestDodgeRoll(0F);
        try {
            helper.assertTrue(lost(victim, ECDamageTypes.mobBolt(zombie), 4F) == 0F, "对照组：必中闪避闪掉 mob_bolt");
            helper.assertTrue(lost(victim, ECDamageTypes.calamity(zombie), 4F) > 0F, "必中闪避也闪不掉 calamity");
            helper.assertTrue(lost(victim, ECDamageTypes.calamitySolar(zombie), 4F) > 0F, "必中闪避也闪不掉 calamity_solar");
        } finally {
            VoidArmorEvents.setTestDodgeRoll(null);
            victim.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    // ================================================================== 方块、物品、实体

    /** 七个方块、五件物品、三个刷怪蛋：注册名；谜题方块和沉城祭坛挖不动、炸不掉、推不动；封印石默认关着；两个方块实体挂在对的方块上 */
    @GameTest(template = EMPTY)
    public static void blocksAndItems13e(GameTestHelper helper) {
        Map<String, Supplier<? extends Block>> blocks = Map.of(
                "elder_stele", ECSealedVault.ELDER_STELE, "vault_pedestal", ECSealedVault.VAULT_PEDESTAL, "vault_keyhole", ECSealedVault.VAULT_KEYHOLE,
                "vault_seal", ECSealedVault.VAULT_SEAL, "vault_core", ECSealedVault.VAULT_CORE, "sunken_altar", ECSunkenDistrict.SUNKEN_ALTAR,
                "inquisitorium_dais", ECInquisitoriumOutpost.INQUISITORIUM_DAIS);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        for (Map.Entry<String, Supplier<? extends Block>> e : blocks.entrySet()) {
            Block block = e.getValue().get();
            helper.assertTrue(EssentialCraft5.id(e.getKey()).equals(BuiltInRegistries.BLOCK.getKey(block)), "方块注册名 " + e.getKey());
            helper.assertTrue(block.asItem() != net.minecraft.world.item.Items.AIR, e.getKey() + " 应带方块物品");
            BlockState state = block.defaultBlockState();
            boolean puzzle = !e.getKey().equals("inquisitorium_dais");
            if (puzzle) {
                helper.assertTrue(state.getDestroySpeed(helper.getLevel(), at) < 0F, e.getKey() + " 应挖不动（硬度 −1）");
                helper.assertTrue(block.getExplosionResistance() >= ECSealedVault.PUZZLE_RESISTANCE, e.getKey() + " 应炸不掉");
                helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, e.getKey() + " 应推不动");
            } else {
                helper.assertTrue(state.getDestroySpeed(helper.getLevel(), at) > 0F, "信标座挖得动");
            }
        }
        helper.assertFalse(ECSealedVault.VAULT_SEAL.get().defaultBlockState().getValue(com.flameforge.essentialcraft5.block.vault.VaultSealBlock.OPEN),
                "封印石默认关着");
        helper.assertTrue(ECSealedVault.VAULT_CORE_BE.get().isValid(ECSealedVault.VAULT_CORE.get().defaultBlockState())
                && ECSunkenDistrict.SUNKEN_ALTAR_BE.get().isValid(ECSunkenDistrict.SUNKEN_ALTAR.get().defaultBlockState()), "两个方块实体挂在对的方块上");
        helper.setBlock(new BlockPos(1, 2, 1), ECSealedVault.VAULT_CORE.get());
        helper.setBlock(new BlockPos(3, 2, 1), ECSunkenDistrict.SUNKEN_ALTAR.get());
        helper.assertTrue(helper.getBlockEntity(new BlockPos(1, 2, 1)) instanceof com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity
                && helper.getBlockEntity(new BlockPos(3, 2, 1)) instanceof com.flameforge.essentialcraft5.blockentity.SunkenAltarBlockEntity, "放下去有方块实体");

        Map<String, Supplier<? extends net.minecraft.world.item.Item>> items = Map.of(
                "elder_remnant", ECSealedVault.ELDER_REMNANT, "calamity_beacon", ECCalamityHeart.CALAMITY_BEACON, "calamity_remnant", ECCalamityHeart.CALAMITY_REMNANT,
                "shade_core", ECShade.SHADE_CORE, "demon_oath", ECShade.DEMON_OATH);
        for (Map.Entry<String, Supplier<? extends net.minecraft.world.item.Item>> e : items.entrySet()) {
            helper.assertTrue(EssentialCraft5.id(e.getKey()).equals(BuiltInRegistries.ITEM.getKey(e.getValue().get())), "物品注册名 " + e.getKey());
        }
        helper.assertTrue(new ItemStack(ECCalamityHeart.CALAMITY_BEACON.get()).getMaxStackSize() == 1, "天灾信标一组 1");
        for (RegistryObject<ECEntities.SpawnEgg> egg : List.of(ECTownSentry.TOWN_SENTRY_SPAWN_EGG, ECSunkenDistrict.ABYSS_TYRANT_SPAWN_EGG,
                ECCalamityHeart.CALAMITY_HEART_SPAWN_EGG)) {
            helper.assertFalse(egg.get().changesSpawner(), egg.getId() + " 点刷怪笼不改类型（同全息投影 / 魔像）");
        }
        helper.succeed();
    }

    /** 六个实体：类别、属性默认值（batch13.md §5.3）、首领档位 / 封顶 / 名单；技能实体不存盘 */
    @GameTest(template = EMPTY)
    public static void entities13e(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(ECTownSentry.TOWN_SENTRY.get().getCategory() == MobCategory.MONSTER
                && ECSunkenDistrict.ABYSS_TYRANT.get().getCategory() == MobCategory.MONSTER
                && ECCalamityHeart.CALAMITY_HEART.get().getCategory() == MobCategory.MONSTER, "三只首领类别 MONSTER");
        helper.assertTrue(ECCalamityHeart.CALAMITY_PILLAR.get().getCategory() == MobCategory.MISC
                && ECCalamityHeart.CALAMITY_RUBBLE.get().getCategory() == MobCategory.MISC, "灾柱、落石类别 MISC（不占刷怪上限）");

        record Boss(EntityType<? extends ECBossMonster> type, double health, double armor, double toughness, BossTier tier, float perHit, float perSecond) {
        }
        for (Boss b : List.of(
                new Boss(ECTownSentry.TOWN_SENTRY.get(), 260D, 14D, 4D, BossTier.MINI, 30F, 0F),
                new Boss(ECSunkenDistrict.ABYSS_TYRANT.get(), 200D, 10D, 0D, BossTier.BOSS, 25F, 0F),
                new Boss(ECCalamityHeart.CALAMITY_HEART.get(), 2400D, 16D, 8D, BossTier.BOSS, 40F, 30F))) {
            ECBossMonster boss = b.type().create(level);
            helper.assertTrue(boss != null, "建不出 " + b.type());
            String name = BuiltInRegistries.ENTITY_TYPE.getKey(b.type()).getPath();
            close(helper, boss.getAttributeBaseValue(Attributes.MAX_HEALTH), b.health(), name + " 生命");
            close(helper, boss.getAttributeBaseValue(Attributes.ARMOR), b.armor(), name + " 护甲");
            close(helper, boss.getAttributeBaseValue(Attributes.ARMOR_TOUGHNESS), b.toughness(), name + " 韧性");
            close(helper, boss.getAttributeBaseValue(Attributes.KNOCKBACK_RESISTANCE), 1D, name + " 击退抗性");
            helper.assertTrue(boss.bossTier() == b.tier(), name + " 档位应是 " + b.tier());
            helper.assertTrue(Math.abs(boss.bossLimits().perHit() - b.perHit()) < EPS && Math.abs(boss.bossLimits().perSecond() - b.perSecond()) < EPS,
                    name + " 封顶：" + boss.bossLimits());
            helper.assertFalse(boss.hasEliteVariant(), name + " 没有精英");
            boss.discard();
        }
        CalamityHeartEntity heart = ECCalamityHeart.CALAMITY_HEART.get().create(level);
        helper.assertTrue(heart != null && heart.rosterSettings().abandons() && heart.rosterSettings().suppressFlight(), "天灾之心：召唤出来的场地首领（名单空了散去、压飞行）");
        helper.assertFalse(ECTownSentry.TOWN_SENTRY.get().create(level).rosterSettings().abandons(), "守望者：守着一个地方，名单空了不散去");
        heart.discard();

        Mob turret = ECTownSentry.SENTRY_TURRET.get().create(level);
        Mob pillar = ECCalamityHeart.CALAMITY_PILLAR.get().create(level);
        Entity rubble = ECCalamityHeart.CALAMITY_RUBBLE.get().create(level);
        helper.assertTrue(turret != null && pillar != null && rubble != null, "三种技能实体建得出");
        close(helper, turret.getAttributeBaseValue(Attributes.MAX_HEALTH), 30D, "炮台 30 血");
        close(helper, pillar.getAttributeBaseValue(Attributes.MAX_HEALTH), 240D, "灾柱 240 血");
        helper.assertFalse(turret.shouldBeSaved() || pillar.shouldBeSaved() || rubble.shouldBeSaved(), "技能实体不存盘");
        helper.assertTrue(pillar.isNoAi() && !(rubble instanceof LivingEntity), "灾柱没有 AI；落石不是活物");
        turret.discard();
        pillar.discard();
        rubble.discard();
        helper.succeed();
    }

    /** 三只首领进三个首领标签，三种技能实体进 growth_excluded；13c 的首领还在 */
    @GameTest(template = EMPTY)
    public static void entityTags13e(GameTestHelper helper) {
        List<EntityType<?>> bosses = List.of(ECTownSentry.TOWN_SENTRY.get(), ECSunkenDistrict.ABYSS_TYRANT.get(), ECCalamityHeart.CALAMITY_HEART.get());
        TagKey<EntityType<?>> divider = TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("divider_immune"));
        TagKey<EntityType<?>> coil = TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("mru_coil_immune"));
        for (EntityType<?> type : bosses) {
            helper.assertTrue(type.is(Tags.EntityTypes.BOSSES) && type.is(divider) && type.is(coil), type + " 应在 forge:bosses、divider_immune、mru_coil_immune 里");
            helper.assertFalse(type.is(ECTags.GROWTH_EXCLUDED), type + " 不在 growth_excluded（首领点数走同调预算）");
        }
        for (EntityType<?> type : List.of(ECTownSentry.SENTRY_TURRET.get(), ECCalamityHeart.CALAMITY_PILLAR.get(), ECCalamityHeart.CALAMITY_RUBBLE.get())) {
            helper.assertTrue(type.is(ECTags.GROWTH_EXCLUDED), type + " 应在 growth_excluded 里");
            helper.assertFalse(type.is(Tags.EntityTypes.BOSSES), type + " 不是首领");
        }
        EntityType<?> golem = BuiltInRegistries.ENTITY_TYPE.get(EssentialCraft5.id("mru_golem"));
        EntityType<?> shade = BuiltInRegistries.ENTITY_TYPE.get(EssentialCraft5.id("matrix_shade"));
        helper.assertTrue(golem.is(Tags.EntityTypes.BOSSES) && shade.is(Tags.EntityTypes.BOSSES) && golem.is(coil) && shade.is(divider), "13c 的两只首领还在");
        helper.succeed();
    }

    // ================================================================== 结构

    /** 三个结构类型、11 个拼件类型注册了且在计划表里；结构的群系只在海纳尔；只有海纳尔的结构状态里有这三个结构集 */
    @GameTest(template = EMPTY)
    public static void structures13eRegistered(GameTestHelper helper) {
        for (String name : List.of("sealed_vault", "sunken_district", "inquisitorium_outpost")) {
            helper.assertTrue(BuiltInRegistries.STRUCTURE_TYPE.containsKey(EssentialCraft5.id(name)), "结构类型没注册：" + name);
            helper.assertTrue(ECHoannaStructures.structureTypeIds().contains(EssentialCraft5.id(name)), "结构类型没登记：" + name);
        }
        Set<String> pieces = new HashSet<>();
        pieces.addAll(ECHoannaStructures.PLANNED_VAULT_PIECES);
        pieces.addAll(ECHoannaStructures.PLANNED_SUNKEN_PIECES);
        pieces.addAll(ECHoannaStructures.PLANNED_OUTPOST_PIECES);
        helper.assertTrue(pieces.size() == 11, "13e 计划 11 种拼件，实际 " + pieces.size());
        for (String name : pieces) {
            helper.assertTrue(BuiltInRegistries.STRUCTURE_PIECE.containsKey(EssentialCraft5.id(name)), "拼件类型没注册：" + name);
        }

        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        Map<String, List<ResourceKey<Biome>>> expected = Map.of(
                "sealed_vault", List.of(ECHoanna.HOANNA_DESERT, ECHoanna.DREADLANDS),
                "sunken_district", List.of(ECHoanna.HOANNA_SEA),
                "inquisitorium_outpost", List.of(ECHoanna.SHADOW_CORRUPTION));
        List<ResourceKey<Biome>> hoanna = List.of(ECHoanna.FROZEN_CORRUPTION, ECHoanna.MAGIC_CORRUPTION, ECHoanna.CHAOS_CORRUPTION, ECHoanna.DREADLANDS,
                ECHoanna.HOANNA_DESERT, ECHoanna.SHADOW_CORRUPTION, ECHoanna.HOANNA_SEA);
        for (Map.Entry<String, List<ResourceKey<Biome>>> e : expected.entrySet()) {
            Structure structure = structures.get(EssentialCraft5.id(e.getKey()));
            helper.assertTrue(structure != null, "数据包里要有结构 " + e.getKey());
            helper.assertTrue(BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type()).equals(EssentialCraft5.id(e.getKey())), e.getKey() + " 的类型");
            for (ResourceKey<Biome> key : hoanna) {
                boolean in = structure.biomes().contains(biomes.getHolderOrThrow(key));
                helper.assertTrue(in == e.getValue().contains(key), e.getKey() + (in ? " 不该" : " 应") + "在 " + key.location() + " 生成");
            }
            for (ResourceKey<Biome> vanilla : List.of(Biomes.PLAINS, Biomes.DESERT, Biomes.OCEAN, Biomes.NETHER_WASTES, Biomes.THE_END, Biomes.END_HIGHLANDS)) {
                helper.assertFalse(structure.biomes().contains(biomes.getHolderOrThrow(vanilla)), e.getKey() + " 不该在原版群系 " + vanilla.location());
            }
        }

        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved vanilla = HoannaTestWorld.vanillaOverworld(server, HoannaTestWorld.SEED);
        helper.assertTrue(newSets(vanilla.structureState().possibleStructureSets()).isEmpty(), "原版主世界的结构集里不该有 13e 的三个");
        helper.assertTrue(newSets(HoannaTestWorld.resolve(server).structureState().possibleStructureSets()).size() == 3, "海纳尔的结构集里应有 13e 的三个");
        for (ServerLevel level : server.getAllLevels()) {
            int count = newSets(level.getChunkSource().getGeneratorState().possibleStructureSets()).size();
            boolean hoannaLevel = level.dimension().equals(ECHoanna.LEVEL);
            helper.assertTrue(count == (hoannaLevel ? 3 : 0), level.dimension().location() + " 的 13e 结构集应有 " + (hoannaLevel ? 3 : 0) + " 个，实际 " + count);
        }
        helper.succeed();
    }

    /** 三个结构集的放置：宝库 spread_excluding 32 / 16 排城镇和前哨各 8，前哨 24 / 14 排城镇和宝库各 8，沉城 spread_excluding 40 / 20 排城镇、宝库、前哨各 8；salt */
    @GameTest(template = EMPTY)
    public static void structureSets13e(GameTestHelper helper) {
        Registry<StructureSet> sets = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        StructurePlacement vault = sets.getOrThrow(VAULT_SET).placement();
        StructurePlacement outpost = sets.getOrThrow(OUTPOST_SET).placement();
        StructurePlacement sunken = sets.getOrThrow(SUNKEN_SET).placement();
        helper.assertTrue(vault instanceof SpreadExcludingPlacement v && v.spacing() == 32 && v.separation() == 16 && salt(v) == 202610265
                && zonesAre(v, TOWN_SET, OUTPOST_SET), "宝库的放置：" + describe(vault));
        helper.assertTrue(outpost instanceof SpreadExcludingPlacement o && o.spacing() == 24 && o.separation() == 14 && salt(o) == 202610267
                && zonesAre(o, TOWN_SET, VAULT_SET), "前哨的放置：" + describe(outpost));
        helper.assertTrue(sunken instanceof SpreadExcludingPlacement s && s.spacing() == 40 && s.separation() == 20 && salt(s) == 202610266
                && s.exclusionZones().size() == 3 && s.exclusionZones().get(0).otherSet().is(TOWN_SET) && s.exclusionZones().get(1).otherSet().is(VAULT_SET)
                && s.exclusionZones().get(2).otherSet().is(OUTPOST_SET) && s.exclusionZones().stream().allMatch(z -> z.chunkCount() == 8),
                "沉城的放置：" + describe(sunken));
        for (ResourceKey<StructureSet> key : NEW_SETS) {
            StructureSet set = sets.getOrThrow(key);
            helper.assertTrue(set.structures().size() == 1 && set.structures().get(0).structure().is(key.location()), key.location() + " 只放同名的那一座");
        }
        helper.succeed();
    }

    /**
     * 宝库、前哨互相排除（原版的查法会无限递归）：固定种子海纳尔的结构状态上，宝库 61 × 61 格、前哨 81 × 81 格逐格调 isStructureChunk，
     * 不递归；放不放 = 「±8 区块里没有群系对的城镇格、没有群系对的对方格（只看对方挑中的区块，群系按对方的采样点）」；
     * 挨得近、群系都对的一对两边都不放，这种对要出现；旁边只有群系不对的格子、照样放的也要出现。
     */
    @GameTest(template = EMPTY)
    public static void mutualExclusionTerminates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server, 5 * SEED_STEP);
        Registry<StructureSet> sets = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        SpreadExcludingPlacement vault = (SpreadExcludingPlacement) sets.getOrThrow(VAULT_SET).placement();
        SpreadExcludingPlacement outpost = (SpreadExcludingPlacement) sets.getOrThrow(OUTPOST_SET).placement();
        RandomSpreadStructurePlacement town = (RandomSpreadStructurePlacement) sets.getOrThrow(TOWN_SET).placement();
        long seed = hoanna.seed();
        Set<ChunkPos> towns = inBiome(hoanna, sets.getHolderOrThrow(TOWN_SET), cells(town, seed, 1100));
        Set<ChunkPos> vaults = inBiome(hoanna, sets.getHolderOrThrow(VAULT_SET), cells(vault, seed, 1100));
        Set<ChunkPos> outposts = inBiome(hoanna, sets.getHolderOrThrow(OUTPOST_SET), cells(outpost, seed, 1100));
        Set<ChunkPos> allOther = cells(outpost, seed, 1100);
        allOther.addAll(cells(town, seed, 1100));
        int[] counts = new int[3];
        try {
            if (!checkSide(helper, hoanna, vault, seed, 30, towns, outposts, "宝库", counts)
                    || !checkSide(helper, hoanna, outpost, seed, 40, towns, vaults, "前哨", counts)) {
                return;
            }
        } catch (StackOverflowError error) {
            helper.fail("宝库 / 前哨互相排除时递归没停下来");
            return;
        }
        helper.assertTrue(counts[0] > 0 && counts[1] > 0, "放的、拒的都应出现：放 " + counts[0] + "、拒 " + counts[1]);
        helper.assertTrue(counts[2] > 0, "宝库和前哨挨得近（两边都不放）的对应至少出现一次，实际 " + counts[2]);
        // 旁边只有群系不对的城镇 / 前哨格：照样放（原版的 exclusion_zone 会挡）
        int placedNearWrongBiome = 0;
        for (int cx = -30; cx <= 30; cx++) {
            for (int cz = -30; cz <= 30; cz++) {
                ChunkPos chunk = vault.getPotentialStructureChunk(seed, cx * vault.spacing(), cz * vault.spacing());
                if (near(allOther, chunk) && vault.isStructureChunk(hoanna.structureState(), chunk.x, chunk.z)) {
                    placedNearWrongBiome++;
                }
            }
        }
        helper.assertTrue(placedNearWrongBiome > 0, "旁边只有群系不对的格子、照样放的宝库应出现");
        helper.succeed();
    }

    /** 只留群系对的（按那个集的群系判定，和排除用的同一个） */
    private static Set<ChunkPos> inBiome(HoannaTestWorld.Resolved hoanna, Holder<StructureSet> set, Set<ChunkPos> cells) {
        Set<ChunkPos> out = new HashSet<>();
        for (ChunkPos cell : cells) {
            if (SpreadExcludingPlacement.otherBiomeAllows(hoanna.structureState(), set, cell.x, cell.z)) {
                out.add(cell);
            }
        }
        return out;
    }

    /** blocks_midscape = 城镇 + 三座；三个 has_structure 群系标签；shade_core_sites 默认 = 审判庭地点（暗影腐化之地） */
    @GameTest(template = EMPTY)
    public static void structureTags13e(GameTestHelper helper) {
        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String name : List.of("hoanna_town", "sealed_vault", "sunken_district", "inquisitorium_outpost")) {
            Holder<Structure> holder = structures.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, EssentialCraft5.id(name)));
            helper.assertTrue(holder.is(HoannaMidscape.BLOCKS_MIDSCAPE), name + " 应在 blocks_midscape 里");
        }
        helper.assertFalse(structures.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, EssentialCraft5.id("old_catacombs"))).is(HoannaMidscape.BLOCKS_MIDSCAPE),
                "墓穴（地下）不在 blocks_midscape 里");
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        TagKey<Biome> shadeSites = TagKey.create(Registries.BIOME, EssentialCraft5.id("shade_core_sites"));
        helper.assertTrue(biomes.getHolderOrThrow(ECHoanna.SHADOW_CORRUPTION).is(shadeSites), "shade_core_sites 默认有暗影腐化之地");
        helper.assertFalse(biomes.getHolderOrThrow(ECHoanna.HOANNA_DESERT).is(shadeSites), "shade_core_sites 默认没有沙漠");
        TagKey<Biome> vaultBiomes = TagKey.create(Registries.BIOME, EssentialCraft5.id("has_structure/sealed_vault"));
        helper.assertTrue(biomes.getHolderOrThrow(ECHoanna.HOANNA_DESERT).is(vaultBiomes) && biomes.getHolderOrThrow(ECHoanna.DREADLANDS).is(vaultBiomes)
                && !biomes.getHolderOrThrow(ECHoanna.SHADOW_CORRUPTION).is(vaultBiomes), "has_structure/sealed_vault = 沙漠、恐惧之地");
        helper.succeed();
    }

    /** 11 种拼件 createTag 再按注册的拼件类型读回：类、包围盒、深度、种子、地面相同，再存一次 NBT 完全相同 */
    @GameTest(template = EMPTY)
    public static void pieces13eRoundTripNbt(GameTestHelper helper) {
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(helper.getLevel());
        BoundingBox box = new BoundingBox(100, 40, 200, 120, 80, 220);
        List<HoannaStructurePiece> pieces = List.of(
                new VaultPieces.Stele(0, box, 21L), new VaultPieces.Shaft(1, box, 22L), new VaultPieces.Antechamber(1, box, 23L),
                new VaultPieces.Chamber(2, box, 24L),
                new SunkenPieces.Dome(0, box, 31L), new SunkenPieces.House(1, box, 32L), new SunkenPieces.Tower(1, box, 33L),
                new OutpostPieces.Arena(0, box, 41L), new OutpostPieces.Pylon(1, box, 42L), new OutpostPieces.Dais(1, box, 43L),
                new OutpostPieces.Camp(1, box, -44L));
        Set<String> types = new HashSet<>();
        int ground = 50;
        for (HoannaStructurePiece piece : pieces) {
            piece.setGroundY(ground++);
            CompoundTag tag = piece.createTag(context);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            helper.assertTrue(id != null && BuiltInRegistries.STRUCTURE_PIECE.containsKey(id), "拼件类型没注册：" + tag.getString("id"));
            types.add(id.getPath());
            StructurePieceType type = BuiltInRegistries.STRUCTURE_PIECE.get(id);
            StructurePiece copy = type.load(context, tag);
            helper.assertTrue(copy.getClass() == piece.getClass(), id + " 读回来的类不对：" + copy.getClass());
            helper.assertTrue(copy.getBoundingBox().equals(piece.getBoundingBox()) && copy.getGenDepth() == piece.getGenDepth(), id + " 的包围盒 / 深度读回不同");
            HoannaStructurePiece hoannaCopy = (HoannaStructurePiece) copy;
            helper.assertTrue(hoannaCopy.seed() == piece.seed() && hoannaCopy.groundY() == piece.groundY(), id + " 的种子 / 地面读回不同");
            helper.assertTrue(copy.createTag(context).equals(tag), id + " 再存一次 NBT 应完全相同");
        }
        Set<String> planned = new HashSet<>(ECHoannaStructures.PLANNED_VAULT_PIECES);
        planned.addAll(ECHoannaStructures.PLANNED_SUNKEN_PIECES);
        planned.addAll(ECHoannaStructures.PLANNED_OUTPOST_PIECES);
        helper.assertTrue(types.equals(planned), "11 种拼件都过了一遍：" + types);
        helper.succeed();
    }

    // ================================================================== 工具

    /** 一侧的格子逐个对：放不放 = ±8 区块里既没有城镇格、也没有对方的格。counts：放、拒、和对方挨得近的个数 */
    private static boolean checkSide(GameTestHelper helper, HoannaTestWorld.Resolved hoanna, SpreadExcludingPlacement placement, long seed, int cells,
                                     Set<ChunkPos> towns, Set<ChunkPos> others, String what, int[] counts) {
        int spacing = placement.spacing();
        for (int cx = -cells; cx <= cells; cx++) {
            for (int cz = -cells; cz <= cells; cz++) {
                ChunkPos chunk = placement.getPotentialStructureChunk(seed, cx * spacing, cz * spacing);
                boolean nearTown = near(towns, chunk);
                boolean nearOther = near(others, chunk);
                boolean placed = placement.isStructureChunk(hoanna.structureState(), chunk.x, chunk.z);
                if (placed == (nearTown || nearOther)) {
                    helper.fail(what + "格 " + chunk + "：城镇" + (nearTown ? "在" : "不在") + " 8 区块内、对方" + (nearOther ? "在" : "不在") + "，却" + (placed ? "放了" : "没放"));
                    return false;
                }
                counts[placed ? 0 : 1]++;
                if (nearOther) {
                    counts[2]++;
                }
            }
        }
        return true;
    }

    /** 以原点为中心、半边 range 区块内这个放置挑中的全部区块（每格一个） */
    private static Set<ChunkPos> cells(RandomSpreadStructurePlacement placement, long seed, int range) {
        Set<ChunkPos> all = new HashSet<>();
        int spacing = placement.spacing();
        int n = range / spacing + 1;
        for (int cx = -n; cx <= n; cx++) {
            for (int cz = -n; cz <= n; cz++) {
                all.add(placement.getPotentialStructureChunk(seed, cx * spacing, cz * spacing));
            }
        }
        return all;
    }

    private static boolean near(Set<ChunkPos> cells, ChunkPos chunk) {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (cells.contains(new ChunkPos(chunk.x + dx, chunk.z + dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean zonesAre(SpreadExcludingPlacement placement, ResourceKey<StructureSet> first, ResourceKey<StructureSet> second) {
        List<StructurePlacement.ExclusionZone> zones = placement.exclusionZones();
        return zones.size() == 2 && zones.get(0).otherSet().is(first) && zones.get(1).otherSet().is(second)
                && zones.get(0).chunkCount() == 8 && zones.get(1).chunkCount() == 8 && !encode(placement).has("exclusion_zone");
    }

    private static String describe(StructurePlacement placement) {
        if (placement instanceof SpreadExcludingPlacement s) {
            List<String> zones = new ArrayList<>();
            s.exclusionZones().forEach(z -> zones.add(z.otherSet().unwrapKey().map(k -> k.location().toString()).orElse("?") + " " + z.chunkCount()));
            return "spread_excluding " + s.spacing() + " / " + s.separation() + " salt " + salt(s) + " " + zones;
        }
        if (placement instanceof RandomSpreadStructurePlacement r) {
            return "random_spread " + r.spacing() + " / " + r.separation() + " salt " + salt(r);
        }
        return String.valueOf(placement);
    }

    /**
     * salt、exclusion_zone 在原版是 protected，从编码出来的 JSON 里读。要带注册表编码：不带时排除区的结构集按内容整个展开，
     * 宝库、前哨互相排除会无限展开下去
     */
    private static com.google.gson.JsonObject encode(StructurePlacement placement) {
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess());
        return StructurePlacement.CODEC.encodeStart(ops, placement).getOrThrow(false, e -> {
        }).getAsJsonObject();
    }

    private static int salt(StructurePlacement placement) {
        return encode(placement).get("salt").getAsInt();
    }

    private static List<Holder<StructureSet>> newSets(List<Holder<StructureSet>> sets) {
        return sets.stream().filter(set -> NEW_SETS.stream().anyMatch(set::is)).toList();
    }

    private static ResourceKey<StructureSet> set(String name) {
        return ResourceKey.create(Registries.STRUCTURE_SET, EssentialCraft5.id(name));
    }

    private static Player mock(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 满血挨一下，返回掉了多少血（之后补满） */
    private static float lost(Player victim, DamageSource source, float amount) {
        victim.setHealth(victim.getMaxHealth());
        victim.invulnerableTime = 0;
        victim.hurt(source, amount);
        float lost = victim.getMaxHealth() - victim.getHealth();
        victim.setHealth(victim.getMaxHealth());
        return lost;
    }

    private static ItemStack attuned(ItemStack stack, int stage) {
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        return stack;
    }

    /** 穿四件 stage 阶的审判庭（只放进槽，不加属性） */
    private static void inquisitoriumSuit(LivingEntity entity, int stage) {
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            entity.setItemSlot(type.getSlot(), attuned(new ItemStack(ECInquisitorium.all().get(type.ordinal()).get()), stage));
        }
    }

    private static void close(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPS, what + "：应 " + expected + "，实际 " + actual);
    }
}
