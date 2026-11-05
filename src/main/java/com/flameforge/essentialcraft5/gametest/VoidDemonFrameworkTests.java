package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.block.MithrilineCrystalPillarBlock;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.registry.ECCrystalPillars;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 第七批框架 7-F「虚空与恶魔」的 GameTest（batch7 §3.1 第 7 条）：
 * safeExplode 的过滤重载；矩阵类腐化效果默认开、海纳尔之门关着时不抽不发作；三个伤害类型与标签；物理伤害判定（虚空套装用，batch7 D1）；
 * 三种水晶柱的档位、附魔加成、标签、产出、暗渎柱没有配方；被俘灵魂的 of / matches / 显示名 / 创造栏。
 * 配置一律用测试覆盖值（CorruptionEffects.setTestOverrides、ECHoannaPortal.Config.setEnabledForTest），同一段同步代码里设、测、置回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class VoidDemonFrameworkTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1.0E-6D;

    private VoidDemonFrameworkTests() {
    }

    // ------------------------------------------------------------------ safeExplode 过滤

    /**
     * 过滤重载：过滤掉的生物不受伤、不被击退；放行的照原版爆炸受伤、被推开；方块照旧不坏。
     * 过滤按实例认（别的测试结构离得近，用「是不是这一只」免得伤到隔壁的生物）
     */
    @GameTest(template = EMPTY)
    public static void voidDemonSafeExplodeFilter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 1, 2));
        Vec3 center = helper.absoluteVec(new Vec3(2.5D, 1.5D, 2.5D));
        // 不带实体的爆炸伤害源：带攻击者的话 safeExplode 拿它当爆炸本体，原版爆炸伤不到本体
        DamageSource damage = level.damageSources().explosion(null, null);

        ECProtection.safeExplode(level, null, damage, center, 2F, living -> living == zombie);
        helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "放行的僵尸应受伤，实际 " + zombie.getHealth());
        helper.assertTrue(zombie.getDeltaMovement().horizontalDistanceSqr() > EPS, "放行的僵尸应被推开");
        helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "过滤掉的村民不应受伤，实际 " + villager.getHealth());
        helper.assertTrue(villager.getDeltaMovement().lengthSqr() < EPS, "过滤掉的村民不应被推开，实际 " + villager.getDeltaMovement());

        // 同一只村民换成放行：受伤，说明上面没受伤是过滤的结果，不是离得远
        ECProtection.safeExplode(level, null, damage, center, 2F, living -> living == villager);
        helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), "放行后村民应受伤，实际 " + villager.getHealth());
        helper.succeed();
    }

    // ------------------------------------------------------------------ 矩阵类腐化效果

    /** 矩阵类默认开（E2）；海纳尔之门关着时（门的测试覆盖值）不抽、已有的不发作（发作也走 isEnabled）；disabled_effects 照常生效 */
    @GameTest(template = EMPTY)
    public static void voidDemonMatrixGate(GameTestHelper helper) {
        helper.assertTrue(CorruptionEffects.matrixEffectsEnabled(), "矩阵类默认应开着");
        try {
            CorruptionEffects.setTestOverrides(true, List.of());
            ECHoannaPortal.Config.setEnabledForTest(false);
            helper.assertFalse(CorruptionEffects.matrixEffectsEnabled(), "海纳尔之门关着时矩阵类应关");
            helper.assertTrue(CorruptionEffects.drawCandidates(100000, List.of()).stream()
                    .noneMatch(e -> e.category() == CorruptionEffects.Category.MATRIX), "门关着时一个矩阵类都不抽");
            helper.assertFalse(CorruptionEffects.byId("matrix_16").isEnabled(), "门关着时身上已有的矩阵类不发作");
            helper.assertTrue(CorruptionEffects.byId("body_0").isEnabled() && CorruptionEffects.byId("mind_5").isEnabled(),
                    "门只管矩阵类，身体、心智类不受影响");

            ECHoannaPortal.Config.setEnabledForTest(true);
            helper.assertTrue(CorruptionEffects.byId("matrix_16").isEnabled(), "门开着时矩阵类照常");
            CorruptionEffects.setTestOverrides(true, List.of("matrix_16"));
            helper.assertFalse(CorruptionEffects.byId("matrix_16").isEnabled(), "disabled_effects 里的矩阵类应关着");
            helper.assertTrue(CorruptionEffects.byId("matrix_14").isEnabled(), "没列进 disabled_effects 的矩阵类照常");
            CorruptionEffects.setTestOverrides(false, List.of());
            helper.assertFalse(CorruptionEffects.matrixEffectsEnabled(), "开关关着时门开着也不行");
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
            ECHoannaPortal.Config.setEnabledForTest(null);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 伤害类型

    /** 三个伤害类型都在注册表里；demon、soul_capture 进不掉落标签；soul_capture 带五个 bypass；ember_backlash 不进任何标签、不带攻击者 */
    @GameTest(template = EMPTY)
    public static void voidDemonDamageTypes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        DamageSource demon = ECDamageTypes.demon(zombie);
        DamageSource soul = ECDamageTypes.soulCapture(zombie);
        DamageSource backlash = ECDamageTypes.emberBacklash(level);

        helper.assertTrue(demon.is(ECDamageTypes.DEMON) && demon.getEntity() == zombie && demon.getDirectEntity() == zombie, "恶魔伤害源带攻击者");
        helper.assertTrue(demon.is(ECDamageTypes.NO_DEATH_LOOT) && !demon.is(DamageTypeTags.BYPASSES_ARMOR), "demon：不掉落、不无视护甲");
        helper.assertTrue(soul.is(ECDamageTypes.NO_DEATH_LOOT) && soul.getEntity() == zombie, "soul_capture：不掉落、带攻击者");
        helper.assertTrue(soul.is(DamageTypeTags.BYPASSES_ARMOR) && soul.is(DamageTypeTags.BYPASSES_COOLDOWN)
                        && soul.is(DamageTypeTags.BYPASSES_EFFECTS) && soul.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                        && soul.is(DamageTypeTags.BYPASSES_RESISTANCE),
                "soul_capture 应带 bypasses_armor / cooldown / effects / enchantments / resistance");
        helper.assertTrue(backlash.getEntity() == null && backlash.getDirectEntity() == null && backlash.sourcePositionRaw() == null,
                "反噬不带攻击者、不带位置");
        helper.assertFalse(backlash.is(ECDamageTypes.NO_DEATH_LOOT) || backlash.is(DamageTypeTags.BYPASSES_ARMOR)
                || backlash.is(DamageTypeTags.WITCH_RESISTANT_TO), "反噬不进任何标签");
        helper.assertTrue(ECDamageTypes.mruDischarge(level).is(ECDamageTypes.NO_DEATH_LOOT), "加新成员后 mru_discharge 仍在不掉落标签里");
        helper.assertTrue(demon.getMsgId().equals("essentialcraft5.demon") && soul.getMsgId().equals("essentialcraft5.soul_capture")
                && backlash.getMsgId().equals("essentialcraft5.ember_backlash"), "message_id");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 物理伤害判定

    /**
     * 物理伤害（ECDamageKinds，虚空套装用）：近战、箭、弹射物算；魔法、荆棘、火、爆炸、摔落、/kill 不算；
     * 召唤物替主人挥砍按近战算（魔法类型也算）；本模组的 demon 算、mru_ray / mru_discharge / ember_backlash 不算
     */
    @GameTest(template = EMPTY)
    public static void voidDemonPhysicalDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DamageSources sources = level.damageSources();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie owner = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        Arrow arrow = new Arrow(level, zombie.getX(), zombie.getY(), zombie.getZ());
        LargeFireball fireball = new LargeFireball(EntityType.FIREBALL, level);

        helper.assertTrue(ECDamageKinds.isPhysical(sources.mobAttack(zombie)), "怪物近战算物理");
        helper.assertTrue(ECDamageKinds.isPhysical(sources.arrow(arrow, zombie)) && ECDamageKinds.isPhysical(sources.arrow(arrow, null)),
                "箭算物理（发射器射的、没有主人的也算）");
        helper.assertTrue(ECDamageKinds.isPhysical(sources.mobProjectile(arrow, zombie)), "弹射物算物理");
        helper.assertTrue(ECDamageKinds.isPhysical(new DamageSource(sources.magic().typeHolder(), zombie, owner)),
                "召唤物替主人挥砍（直接实体是活物、攻击者是别人）按近战算，类型是魔法也算");

        helper.assertFalse(ECDamageKinds.isPhysical(sources.magic()), "魔法不算");
        helper.assertFalse(ECDamageKinds.isPhysical(sources.indirectMagic(zombie, zombie)), "间接魔法（message_id 含 magic）不算");
        helper.assertFalse(ECDamageKinds.isPhysical(sources.thorns(zombie)), "荆棘（witch_resistant_to）不算");
        helper.assertFalse(ECDamageKinds.isPhysical(sources.fireball(fireball, zombie)), "火球（is_fire）不算");
        helper.assertFalse(ECDamageKinds.isPhysical(sources.explosion(zombie, zombie)), "爆炸（直接实体是活物也）不算");
        helper.assertFalse(ECDamageKinds.isPhysical(sources.lava()) || ECDamageKinds.isPhysical(sources.lightningBolt())
                || ECDamageKinds.isPhysical(sources.freeze()), "岩浆、闪电、冰冻不算");
        helper.assertFalse(ECDamageKinds.isPhysical(sources.fall()) || ECDamageKinds.isPhysical(sources.cactus())
                || ECDamageKinds.isPhysical(sources.drown()), "没有攻击者的不算");
        helper.assertFalse(ECDamageKinds.isPhysical(sources.genericKill()), "/kill（bypasses_invulnerability）不算");

        helper.assertTrue(ECDamageKinds.isPhysical(ECDamageTypes.demon(zombie)), "恶魔清怪算物理（直接实体是恶魔）");
        helper.assertFalse(ECDamageKinds.isPhysical(new DamageSource(ECDamageTypes.holder(level, ECGuns.MRU_RAY), null, zombie)),
                "枪械射线不算（没有直接实体、不是弹射物）");
        helper.assertFalse(ECDamageKinds.isPhysical(ECDamageTypes.mruDischarge(level)) || ECDamageKinds.isPhysical(ECDamageTypes.emberBacklash(level)),
                "MRU 电弧、余烬反噬不算");
        helper.assertTrue(ECDamageKinds.isMagic(sources.magic()) && !ECDamageKinds.isMagic(sources.mobAttack(zombie)), "isMagic");
        helper.assertTrue(sources.genericKill().is(DamageTypes.GENERIC_KILL), "自检：genericKill 的类型");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 三种水晶柱

    /** 虚空 / 恶魔 / 暗渎柱：档位 2 / 3 / 4、附魔加成 30 / 60 / 120 只在底座、在 espe_pillars 里、露天照常产 ESPE；暗渎柱史诗、没有配方 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void voidDemonCrystalPillars(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<MithrilineCrystalPillarBlock> blocks = ECCrystalPillars.all().stream().map(r -> r.get()).toList();
        BlockPos[] bases = {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(1, 1, 3)};
        int[] tiers = {2, 3, 4};
        float[] power = {30F, 60F, 120F};
        for (int i = 0; i < 3; i++) {
            helper.setBlock(bases[i], blocks.get(i));
        }
        for (int i = 0; i < 3; i++) {
            BlockPos base = bases[i];
            BlockState bottom = level.getBlockState(helper.absolutePos(base));
            BlockState middle = level.getBlockState(helper.absolutePos(base.above()));
            helper.assertTrue(bottom.is(blocks.get(i)) && middle.is(blocks.get(i))
                    && level.getBlockState(helper.absolutePos(base.above(2))).is(blocks.get(i)), "放下一格应补成三格高：" + blocks.get(i));
            helper.assertTrue(MithrilineCrystalPillarBlock.tierOf(bottom) == tiers[i], "档位应为 " + tiers[i]);
            helper.assertTrue(bottom.is(MithrilineCrystalPillarBlock.ESPE_PILLARS), "应在 espe_pillars 里");
            helper.assertTrue(MithrilineCrystalPillarBlock.isPillarBase(bottom, 2) && !MithrilineCrystalPillarBlock.isPillarBase(middle, 0),
                    "底座算 2 档以上的柱子，中段不算");
            helper.assertTrue(Math.abs(bottom.getEnchantPowerBonus(level, helper.absolutePos(base)) - power[i]) < EPS
                            && middle.getEnchantPowerBonus(level, helper.absolutePos(base.above())) == 0F,
                    "附魔加成应只在底座、为 " + power[i]);
            MithrilineCrystalPillarBlockEntity be = (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(base);
            helper.assertTrue(be != null && be.getTier() == tiers[i] && be.getESPEStorage().getTier() == tiers[i], "方块实体档位应为 " + tiers[i]);
        }
        helper.assertFalse(MithrilineCrystalPillarBlock.isPillarBase(level.getBlockState(helper.absolutePos(bases[0])), 3), "虚空柱不算 3 档");

        ItemStack shade = new ItemStack(ECCrystalPillars.SHADE_CRYSTAL_PILLAR.get());
        helper.assertTrue(shade.getRarity() == Rarity.EPIC, "暗渎柱是史诗物品");
        // 13e 起暗渎柱有生存配方（魔力工作台，第二十二章）：只认这一条
        int recipes = 0;
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            if (recipe.getResultItem(level.registryAccess()).is(shade.getItem())) {
                recipes++;
                helper.assertTrue(recipe.getId().equals(EssentialCraft5.id("crystal_pillars/shade_crystal_pillar")), "暗渎柱只该有魔力工作台那一条配方：" + recipe.getId());
            }
        }
        helper.assertTrue(recipes == 1, "暗渎柱应有 1 条配方，实际 " + recipes);

        MithrilineCrystalPillarBlockEntity voidPillar = (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(bases[0]);
        helper.runAfterDelay(45, () -> {
            double espe = voidPillar.getESPEStorage().getESPE();
            helper.assertTrue(espe > 0.5D, "露天 45 tick 虚空柱应攒到 ESPE，实际 " + espe);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 被俘灵魂

    /** of / entityId / matches（只比 entity，改过名照认）、显示名「被俘灵魂（村民）」、实体没注册时显示注册名、创造栏三份 */
    @GameTest(template = EMPTY)
    public static void voidDemonCapturedSoul(GameTestHelper helper) {
        ItemStack villager = CapturedSoulItem.of(EntityType.VILLAGER, 3);
        helper.assertTrue(villager.is(ECDemonic.CAPTURED_SOUL.get()) && villager.getCount() == 3, "of 应造出 3 个被俘灵魂");
        helper.assertTrue(ResourceLocation.withDefaultNamespace("villager").equals(CapturedSoulItem.entityId(villager)), "entity 应是村民");
        helper.assertTrue(CapturedSoulItem.matches(villager, EntityType.VILLAGER) && !CapturedSoulItem.matches(villager, EntityType.ENDERMAN),
                "村民的灵魂只认村民");
        ItemStack renamed = villager.copy();
        renamed.setHoverName(Component.literal("某个村民"));
        renamed.getOrCreateTag().putInt("extra", 1);
        helper.assertTrue(CapturedSoulItem.matches(renamed, EntityType.VILLAGER), "改过名、带别的 NBT 照样认");
        helper.assertFalse(CapturedSoulItem.matches(new ItemStack(ECDemonic.CAPTURED_SOUL.get()), EntityType.VILLAGER), "没有 entity 的不认");
        helper.assertTrue(CapturedSoulItem.entityId(new ItemStack(ECDemonic.ACKRONITE_INGOT.get())) == null, "别的物品没有 entity");

        Component name = villager.getHoverName();
        helper.assertTrue(name.getContents() instanceof TranslatableContents t && t.getKey().equals(CapturedSoulItem.NAME_WITH_ENTITY)
                        && t.getArgs().length == 1 && t.getArgs()[0] instanceof Component arg
                        && arg.getContents() instanceof TranslatableContents e && e.getKey().equals(EntityType.VILLAGER.getDescriptionId()),
                "显示名应是「被俘灵魂（村民）」，实际 " + name);
        ItemStack unknown = CapturedSoulItem.of(EssentialCraft5.id("no_such_entity"), 1);
        Component unknownName = unknown.getHoverName();
        helper.assertTrue(unknownName.getContents() instanceof TranslatableContents t2 && t2.getArgs().length == 1
                        && t2.getArgs()[0] instanceof Component arg2 && arg2.getContents() instanceof LiteralContents literal
                        && literal.text().equals("essentialcraft5:no_such_entity"),
                "实体没注册时括号里写注册名，实际 " + unknownName);
        helper.assertTrue(ECDemonic.CAPTURED_SOUL.get().getMaxStackSize(villager) == 64, "一组 64");

        List<ItemStack> variants = ECDemonic.CAPTURED_SOUL.get().creativeVariants();
        helper.assertTrue(variants.size() == 3 && CapturedSoulItem.matches(variants.get(0), EntityType.VILLAGER)
                        && CapturedSoulItem.matches(variants.get(1), EntityType.ENDERMAN)
                        && EssentialCraft5.id("wind_mage").equals(CapturedSoulItem.entityId(variants.get(2))),
                "创造栏应是村民、末影人、风法师三份，实际 " + variants);
        helper.succeed();
    }
}
