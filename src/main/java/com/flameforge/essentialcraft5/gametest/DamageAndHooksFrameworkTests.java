package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.advancement.StructureFormedTrigger;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.camo.Camo;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MimicBlockEntity;
import com.flameforge.essentialcraft5.client.render.ArcEffect;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.UBMRUEvents;
import com.flameforge.essentialcraft5.registry.ECBlockEntities;
import com.flameforge.essentialcraft5.registry.BlockSpec;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECCreativeTabs;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECRedstoneTools;
import com.flameforge.essentialcraft5.registry.ECSounds;
import com.flameforge.essentialcraft5.registry.ItemSpec;
import com.flameforge.essentialcraft5.util.DeathLootEvents;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 第四批框架 D「伤害、渲染与钩子」的 GameTest（batch4.md §4.11、§4.13~§4.15）：
 * <ul>
 *   <li>F4-13 伤害类型 {@code mru_discharge}：没有攻击者、没有位置，标签、死亡消息；被它打死的怪不掉物品不掉经验、捡来的必掉装备和猪灵背包照掉；
 *       玩家、Boss 不拦；PvP 关了照样伤得到玩家；玩家被它打死没人拿 UBMRU。</li>
 *   <li>F4-11 伪装的公共一半 {@code Camo}：规则、物品、模型数据、NBT（专用服跑得过即证明公共包没引用客户端类）。</li>
 *   <li>F4-14 电弧折线 {@code ArcEffect}（纯几何，客户端包里但不引用客户端类）：段数、分叉数、端点、寿命；两个音效已注册。</li>
 *   <li>F4-15 {@code MRUBlockEntity} 的同步开关与 {@code markClientDirty}、{@code MRUDrain.canAfford} 与 {@code playerUseMRU} 对拍、
 *       进度触发器 {@code structure_formed}（{@code realPlayer} 不在玩家列表里，直接调 {@code trigger}）。</li>
 * </ul>
 * 真玩家用 {@link ProtectionFrameworkTests#realPlayer}。改全局状态的（PvP 开关）只在同一段同步代码里设、测、还原。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class DamageAndHooksFrameworkTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";

    private DamageAndHooksFrameworkTests() {
    }

    // ------------------------------------------------------------------
    // 帮手
    // ------------------------------------------------------------------

    /** 过掉 60 tick 出生无敌、生命上限调高的真玩家（同 CorruptionTests.victim） */
    private static ServerPlayer victim(GameTestHelper helper, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40D);
        player.setHealth(40F);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /**
     * 放一只僵尸并让玩家先打它一下：原版 {@code setLastHurtByPlayer} 把「最近被玩家打过」的计时设成 {@code tickCount}（刚生成的怪是 0 = 没被打过），
     * 所以用真的玩家攻击（计时 100，照样有经验、战利品按玩家击杀算），再清掉受伤无敌帧。
     */
    private static Zombie hitZombie(GameTestHelper helper, BlockPos relative, ServerPlayer player) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, relative);
        zombie.setNoAi(true);
        zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1F);
        zombie.invulnerableTime = 0;
        return zombie;
    }

    private static List<ItemEntity> itemsNear(GameTestHelper helper, Vec3 at, double radius) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(radius), Entity::isAlive);
    }

    private static List<ExperienceOrb> orbsNear(GameTestHelper helper, Vec3 at, double radius) {
        return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(radius), Entity::isAlive);
    }

    private static String translationKey(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "<不是可翻译文本>";
    }

    // ------------------------------------------------------------------
    // F4-13 伤害类型
    // ------------------------------------------------------------------

    /** mru_discharge：没有攻击者、没有位置；数据包定义和标签都在；死亡消息键按有没有「最后打它的生物」带不带 .player */
    @GameTest(template = EMPTY)
    public static void damageMruDischargeHasNoAttackerAndTags(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DamageSource source = ECDamageTypes.mruDischarge(level);
        helper.assertTrue(source.getEntity() == null && source.getDirectEntity() == null, "MRU 电弧不应带攻击者");
        helper.assertTrue(source.getSourcePosition() == null, "MRU 电弧不应带位置（盾挡不住、没有击退）");
        helper.assertTrue(source.is(ECDamageTypes.MRU_DISCHARGE), "伤害类型应是 mru_discharge");
        helper.assertTrue("essentialcraft5.mru_discharge".equals(source.type().msgId()), "message_id 不对：" + source.type().msgId());
        helper.assertTrue(source.type().scaling() == DamageScaling.NEVER && source.type().exhaustion() == 0F, "scaling 应为 never、exhaustion 应为 0");
        helper.assertTrue(source.is(DamageTypeTags.BYPASSES_ARMOR), "应在 minecraft:bypasses_armor 里（无视护甲点数）");
        helper.assertTrue(source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS), "应在 minecraft:avoids_guardian_thorns 里");
        helper.assertTrue(source.is(DamageTypeTags.ALWAYS_TRIGGERS_SILVERFISH), "应在 minecraft:always_triggers_silverfish 里");
        helper.assertFalse(source.is(DamageTypeTags.WITCH_RESISTANT_TO), "不应在 minecraft:witch_resistant_to 里（裁定 6）");
        helper.assertTrue(source.is(ECDamageTypes.NO_DEATH_LOOT), "应在 essentialcraft5:no_death_loot 里");
        helper.assertTrue(source.is(DamageTypeTags.BYPASSES_SHIELD), "原版 bypasses_shield 包含 #bypasses_armor，盾挡不住");
        helper.assertTrue(ECDamageTypes.holder(level, ECDamageTypes.MRU_DISCHARGE).is(ECDamageTypes.NO_DEATH_LOOT), "holder 应带标签");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        helper.assertTrue("death.attack.essentialcraft5.mru_discharge".equals(translationKey(source.getLocalizedDeathMessage(zombie))),
                "没人打过它时死亡消息键不对：" + translationKey(source.getLocalizedDeathMessage(zombie)));
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        zombie.hurt(level.damageSources().playerAttack(player), 1F);
        helper.assertTrue("death.attack.essentialcraft5.mru_discharge.player".equals(translationKey(source.getLocalizedDeathMessage(zombie))),
                "被玩家打过时死亡消息键应带 .player：" + translationKey(source.getLocalizedDeathMessage(zombie)));
        zombie.discard();
        helper.succeed();
    }

    /** 判定：只拦非玩家、非 Boss、伤害类型在 no_death_loot 里的 */
    @GameTest(template = EMPTY)
    public static void damageSuppressesLootRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DamageSource discharge = ECDamageTypes.mruDischarge(level);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        WitherBoss wither = EntityType.WITHER.create(level);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        helper.assertTrue(zombie != null && wither != null, "应能建出僵尸和凋灵");
        helper.assertTrue(DeathLootEvents.suppressesLoot(zombie, discharge), "僵尸被 MRU 电弧打死应不掉落");
        helper.assertFalse(DeathLootEvents.suppressesLoot(zombie, level.damageSources().generic()), "普通伤害打死的僵尸照常掉落");
        helper.assertFalse(DeathLootEvents.suppressesLoot(wither, discharge), "#forge:bosses（凋灵）不拦");
        helper.assertFalse(DeathLootEvents.suppressesLoot(player, discharge), "玩家受害者不碰");
        helper.succeed();
    }

    /** 被 MRU 电弧打死的僵尸（被玩家打过）：没有掉落物、没有经验球；对照组 generic 打死的有经验球 */
    @GameTest(template = EMPTY_LARGE)
    public static void damageDischargeKillDropsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);

        Zombie zapped = hitZombie(helper, new BlockPos(2, 1, 2), player);
        zapped.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));       // 默认掉落率 0.085
        zapped.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zapped.setDropChance(EquipmentSlot.HEAD, 1.0F);                                     // 被玩家打过就一定掉，但不是「必掉」（要 >1）
        Vec3 zappedAt = zapped.position();
        zapped.hurt(ECDamageTypes.mruDischarge(level), 1000F);
        helper.assertTrue(zapped.isDeadOrDying(), "僵尸应被打死");

        Zombie control = hitZombie(helper, new BlockPos(8, 1, 8), player);
        Vec3 controlAt = control.position();
        control.hurt(level.damageSources().generic(), 1000F);
        helper.assertTrue(control.isDeadOrDying(), "对照僵尸应被打死");

        List<ItemEntity> items = itemsNear(helper, zappedAt, 2D);
        helper.assertTrue(items.isEmpty(), "MRU 电弧打死的僵尸不应掉东西，实际 " + items.stream().map(e -> e.getItem().toString()).toList());
        helper.assertTrue(orbsNear(helper, zappedAt, 2D).isEmpty(), "MRU 电弧打死的僵尸不应掉经验");
        helper.assertFalse(orbsNear(helper, controlAt, 2D).isEmpty(), "对照：被玩家打过、普通伤害打死的僵尸应掉经验");
        helper.succeed();
    }

    /**
     * 别的模组在默认优先级往掉落里加东西（实测：甜蜜魔法给打死的怪加蛋袋）：电死的僵尸照样什么都不掉；
     * 对照 generic 打死的能拿到加的那件（证明模拟的监听器真的生效了）
     */
    @GameTest(template = EMPTY_LARGE)
    public static void damageDischargeStripsDropsAddedByOtherMods(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Zombie zapped = hitZombie(helper, new BlockPos(2, 1, 2), player);
        Zombie control = hitZombie(helper, new BlockPos(8, 1, 8), player);
        Consumer<LivingDropsEvent> otherMod = event -> {
            LivingEntity victim = event.getEntity();
            if (victim == zapped || victim == control) {
                event.getDrops().add(new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), new ItemStack(Items.EGG, 2)));
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDropsEvent.class, otherMod);
        Vec3 zappedAt = zapped.position();
        Vec3 controlAt = control.position();
        try {
            zapped.hurt(ECDamageTypes.mruDischarge(level), 1000F);
            control.hurt(level.damageSources().generic(), 1000F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(otherMod);
        }
        helper.assertTrue(zapped.isDeadOrDying() && control.isDeadOrDying(), "两只僵尸都应被打死");
        List<ItemEntity> items = itemsNear(helper, zappedAt, 2D);
        helper.assertTrue(items.isEmpty(), "别的模组加的掉落也应被删，实际 " + items.stream().map(e -> e.getItem().toString()).toList());
        helper.assertTrue(itemsNear(helper, controlAt, 2D).stream().anyMatch(e -> e.getItem().is(Items.EGG)), "对照：普通伤害打死的应拿到模拟模组加的鸡蛋");
        helper.succeed();
    }

    /** 捡了钻石剑（setGuaranteedDrop，掉落率 2.0）的僵尸被电死：只掉钻石剑，头盔（掉落率 1.0）、战利品、经验都没有 */
    @GameTest(template = EMPTY_LARGE)
    public static void damageDischargeKeepsGuaranteedEquipment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Zombie zombie = hitZombie(helper, new BlockPos(5, 1, 5), player);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setHoverName(Component.literal("玩家的剑"));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
        zombie.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setDropChance(EquipmentSlot.HEAD, 1.0F);
        helper.assertTrue(DeathLootEvents.guaranteedEquipment(zombie).size() == 1, "必掉装备应只有钻石剑，实际 " + DeathLootEvents.guaranteedEquipment(zombie));

        Vec3 at = zombie.position();
        zombie.hurt(ECDamageTypes.mruDischarge(level), 1000F);
        helper.assertTrue(zombie.isDeadOrDying(), "僵尸应被打死");
        List<ItemEntity> items = itemsNear(helper, at, 2D);
        helper.assertTrue(items.size() == 1 && ItemStack.matches(items.get(0).getItem(), sword),
                "应只掉那把钻石剑，实际 " + items.stream().map(e -> e.getItem().toString()).toList());
        helper.assertTrue(orbsNear(helper, at, 2D).isEmpty(), "不应掉经验");

        // 没有记录的受害者（没死过的、不是怪）：全删
        List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.DIRT))));
        Zombie fresh = EntityType.ZOMBIE.create(level);
        DeathLootEvents.keepGuaranteedEquipment(fresh, drops);
        helper.assertTrue(drops.isEmpty(), "没有必掉装备记录时应全删");
        helper.succeed();
    }

    /**
     * 猪灵背包（4b 审查 REA-1）：收进背包的金块、附魔金剑（{@code Piglin.dropCustomDeathLoot} 死时把背包全部掉出来）被电死照掉；
     * 手里那把弩（掉落率 1.0：被玩家打过就一定掉，但不算必掉）照删，也不掉经验
     */
    @GameTest(template = EMPTY_LARGE)
    public static void damageDischargeKeepsCarriedInventory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Piglin piglin = helper.spawn(EntityType.PIGLIN, new BlockPos(5, 1, 5));
        piglin.setNoAi(true);
        piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        piglin.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        ItemStack goldBlocks = new ItemStack(Items.GOLD_BLOCK, 3);
        ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 3);
        piglin.getInventory().addItem(goldBlocks.copy());
        piglin.getInventory().addItem(sword.copy());
        // 先让玩家打一下：弩（掉落率 1.0）和经验照原版都会掉，用来证明它们是被删掉的
        piglin.hurt(level.damageSources().playerAttack(player), 1F);
        piglin.invulnerableTime = 0;
        List<ItemStack> kept = DeathLootEvents.guaranteedEquipment(piglin);
        helper.assertTrue(kept.size() == 2, "保留表应只有背包里的两样（弩不是必掉），实际 " + kept);

        Vec3 at = piglin.position();
        piglin.hurt(ECDamageTypes.mruDischarge(level), 1000F);
        helper.assertTrue(piglin.isDeadOrDying(), "猪灵应被打死");
        List<ItemEntity> items = itemsNear(helper, at, 2D);
        boolean blocksDropped = items.stream().anyMatch(e -> ItemStack.matches(e.getItem(), goldBlocks));
        boolean swordDropped = items.stream().anyMatch(e -> ItemStack.matches(e.getItem(), sword));
        helper.assertTrue(items.size() == 2 && blocksDropped && swordDropped,
                "应只掉背包里的 3 个金块和附魔金剑，实际 " + items.stream().map(e -> e.getItem().toString()).toList());
        helper.assertTrue(orbsNear(helper, at, 2D).isEmpty(), "不应掉经验");
        helper.succeed();
    }

    /** PvP 关了：MRU 电弧伤得到玩家，玩家攻击伤不到（同一段同步代码里设、测、还原 PvP 开关） */
    @GameTest(template = EMPTY)
    public static void damageDischargeIgnoresPvpOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer target = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer attacker = ProtectionFrameworkTests.realPlayer(helper);
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(false);
            float before = target.getHealth();
            boolean byPlayer = target.hurt(level.damageSources().playerAttack(attacker), 4F);
            helper.assertTrue(!byPlayer && target.getHealth() == before, "PvP 关了，玩家攻击不应伤到玩家");
            boolean byArc = target.hurt(ECDamageTypes.mruDischarge(level), 4F);
            helper.assertTrue(byArc && target.getHealth() < before, "PvP 关了，MRU 电弧应照样伤到玩家，实际 " + before + " → " + target.getHealth());

            // 对照：PvP 开着时玩家攻击伤得到（证明上面不是被别的原因挡掉的）
            server.setPvpAllowed(true);
            target.invulnerableTime = 0;
            float mid = target.getHealth();
            helper.assertTrue(target.hurt(level.damageSources().playerAttack(attacker), 4F) && target.getHealth() < mid,
                    "PvP 开着时玩家攻击应伤得到");
        } finally {
            server.setPvpAllowed(pvp);
        }
        helper.succeed();
    }

    /** 玩家被 MRU 电弧打死：没有击杀者，谁都拿不到 UBMRU，受害者照旧清零 */
    @GameTest(template = EMPTY)
    public static void damageDischargeDeathGivesNoUbmru(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer bystander = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData targetData = PlayerDataEvents.get(target);
        IPlayerData bystanderData = PlayerDataEvents.get(bystander);
        helper.assertTrue(targetData != null && bystanderData != null, "真玩家应挂着玩家数据");
        targetData.setUBMRU(1000);
        bystanderData.setUBMRU(50);
        // UBMRU 只认伤害源的攻击者（source.getEntity()），MRU 电弧没有攻击者
        UBMRUEvents.handleDeath(target, ECDamageTypes.mruDischarge(level));
        helper.assertTrue(targetData.getUBMRU() == 0, "受害者应清零，实际 " + targetData.getUBMRU());
        helper.assertTrue(bystanderData.getUBMRU() == 50, "旁人不应拿到 UBMRU，实际 " + bystanderData.getUBMRU());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // F4-11 伪装（公共一半）
    // ------------------------------------------------------------------

    /** Camo.isValid：石头、草方块真；箱子（有方块实体）、空气、水、屏障（不是普通模型）假；黑名单里的假 */
    @GameTest(template = EMPTY)
    public static void camoValidityRules(GameTestHelper helper) {
        helper.assertTrue(Camo.isValid(Blocks.STONE.defaultBlockState()), "石头应能当伪装");
        helper.assertTrue(Camo.isValid(Blocks.GRASS_BLOCK.defaultBlockState()), "草方块应能当伪装");
        helper.assertTrue(Camo.isValid(Blocks.GLASS.defaultBlockState()), "玻璃应能当伪装");
        helper.assertFalse(Camo.isValid(Blocks.CHEST.defaultBlockState()), "箱子有方块实体，不能当伪装");
        helper.assertFalse(Camo.isValid(Blocks.AIR.defaultBlockState()), "空气不能当伪装");
        helper.assertFalse(Camo.isValid(Blocks.WATER.defaultBlockState()), "水不是普通模型渲染，不能当伪装");
        helper.assertFalse(Camo.isValid(Blocks.BARRIER.defaultBlockState()), "屏障不可见，不能当伪装");
        helper.assertFalse(Camo.isValid(null), "null 不能当伪装");
        helper.assertFalse(Camo.isValid(Blocks.STONE.defaultBlockState(), state -> state.is(Blocks.STONE)), "黑名单里的方块不能当伪装");
        helper.assertTrue(Camo.MIMIC_BLACKLIST.location().equals(EssentialCraft5.id("mimic_blacklist")), "黑名单标签名不对");

        helper.assertTrue(Camo.fromItem(new ItemStack(Items.STONE)) == Blocks.STONE.defaultBlockState(), "石头物品应对应石头");
        helper.assertTrue(Camo.fromItem(new ItemStack(Items.CHEST)) == null, "箱子物品不能当伪装");
        helper.assertTrue(Camo.fromItem(new ItemStack(Items.DIAMOND)) == null, "不是方块物品不能当伪装");
        helper.assertTrue(Camo.isValidItem(new ItemStack(Items.OAK_LOG)) && !Camo.isValidItem(ItemStack.EMPTY), "伪装槽规则不对");

        // 模块接好以后：拟态方块自己不能被模仿（有方块实体，且模块会把它加进黑名单）；没接线就跳过这条
        ResourceLocation mimicId = EssentialCraft5.id("mimic");
        if (ForgeRegistries.BLOCKS.containsKey(mimicId)) {
            helper.assertFalse(Camo.isValid(ForgeRegistries.BLOCKS.getValue(mimicId).defaultBlockState()), "拟态方块不能模仿拟态方块");
        }
        helper.succeed();
    }

    /** 模型数据、NBT 存读（带方块状态属性）、不存在的方块读成 null；普通方块实体上没有伪装 */
    @GameTest(template = EMPTY)
    public static void camoModelDataAndNbt(GameTestHelper helper) {
        helper.assertTrue(Camo.modelData(null) == ModelData.EMPTY, "没伪装应返回空模型数据");
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        helper.assertTrue(Camo.modelData(log).get(Camo.CAMO_STATE) == log, "模型数据里应是伪装状态");

        CompoundTag tag = new CompoundTag();
        Camo.write(tag, "camo", log);
        helper.assertTrue(Camo.read(tag, "camo") == log, "存读后应还是横放的橡木原木，实际 " + Camo.read(tag, "camo"));
        Camo.write(tag, "camo", null);
        helper.assertFalse(tag.contains("camo"), "写 null 应删掉这个键");
        helper.assertTrue(Camo.read(tag, "camo") == null, "没有键应读成 null");
        CompoundTag missing = new CompoundTag();
        missing.putString("Name", "essentialcraft5:no_such_block");
        tag.put("camo", missing);
        helper.assertTrue(Camo.read(tag, "camo") == null, "方块不存在应读成 null");

        BlockPos chest = new BlockPos(2, 1, 2);
        helper.setBlock(chest, Blocks.CHEST);
        helper.assertTrue(Camo.camoAt(helper.getLevel(), helper.absolutePos(chest)) == null, "箱子没有伪装");
        helper.assertTrue(Camo.camoAt(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))) == null, "空气格没有伪装");
        helper.succeed();
    }

    /**
     * 第五批 F5-1 / F5-2：花纹叠层的登记表（拟态方块登记时不带花纹，叠层表里没有 null）；拟态方块实体的类型仍是 mimic
     * （新加的带类型构造器给花纹拟态用，旧构造器行为不变）。客户端包模型、叠花纹要进游戏看（展示厅 04_scene_fancy_mimic）。
     */
    @GameTest(template = EMPTY)
    public static void camoOverlayRegistry(GameTestHelper helper) {
        helper.assertTrue(Camo.overlayOf(Blocks.STONE) == null, "没登记的方块没有花纹");
        helper.assertFalse(Camo.overlayModels().contains(null), "花纹模型表里不应有 null");
        ResourceLocation mimicId = EssentialCraft5.id("mimic");
        if (ForgeRegistries.BLOCKS.containsKey(mimicId)) {
            helper.assertTrue(Camo.camoBlocks().stream().anyMatch(block -> block.get() == ForgeRegistries.BLOCKS.getValue(mimicId)),
                    "拟态方块应已登记伪装模型");
            helper.assertTrue(Camo.overlayOf(ForgeRegistries.BLOCKS.getValue(mimicId)) == null, "拟态方块登记时不带花纹");
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            MimicBlockEntity be = new MimicBlockEntity(pos, ECRedstoneTools.MIMIC.get().defaultBlockState());
            helper.assertTrue(be.getType() == ECRedstoneTools.MIMIC_BE.get(), "拟态方块实体的类型应仍是 mimic");
        }
        helper.succeed();
    }

    /** 第五批 F5-3：规格上的「装饰」标记（默认不是）；创造栏第三页「装饰」已登记、排在物品页后面 */
    @GameTest(template = EMPTY)
    public static void decorationSpecAndTab(GameTestHelper helper) {
        helper.assertFalse(BlockSpec.of(BlockSpec.cubeAll("block/fortifiedstone")).isDecoration(), "方块规格默认不是装饰");
        helper.assertTrue(BlockSpec.of(BlockSpec.cubeAll("block/fortifiedstone")).decoration().isDecoration(), "decoration() 以后应是装饰");
        helper.assertFalse(ItemSpec.generated("item/gem_bound").isDecoration(), "物品规格默认不是装饰");
        helper.assertTrue(ItemSpec.generated("item/gem_bound").decoration().isDecoration(), "decoration() 以后应是装饰");
        helper.assertTrue(ECCreativeTabs.DECORATIONS.getId().equals(EssentialCraft5.id("decorations")), "装饰页注册名不对");
        helper.assertTrue(ECCreativeTabs.DECORATIONS.isPresent(), "装饰页应已注册");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // F4-14 电弧与音效
    // ------------------------------------------------------------------

    /** 主干 8 段首尾相接、两端钉在起点终点；支流 4 段从主干第 3 点到终点；分叉 12 条各 1~2 段、从主干 / 支流的段起点出发；抖动不超过 curve */
    @GameTest(template = EMPTY)
    public static void arcGeometry(GameTestHelper helper) {
        Vec3 start = new Vec3(0.5D, 0.8D, 0.5D);
        Vec3 end = new Vec3(3.5D, 4.8D, -2.5D);
        for (long seed = 0; seed < 20; seed++) {
            ArcEffect.Style style = ArcEffect.Style.COIL_STRIKE;
            ArcEffect arc = new ArcEffect(RandomSource.create(seed), start, end, style);
            List<ArcEffect.Segment> trunk = arc.segments().stream().filter(s -> s.kind() == ArcEffect.Kind.TRUNK).toList();
            List<ArcEffect.Segment> fork = arc.segments().stream().filter(s -> s.kind() == ArcEffect.Kind.FORK).toList();
            int branchSegments = arc.count(ArcEffect.Kind.BRANCH);
            helper.assertTrue(trunk.size() == ArcEffect.TRUNK_SEGMENTS, "主干应 8 段，实际 " + trunk.size());
            helper.assertTrue(fork.size() == ArcEffect.FORK_SEGMENTS, "支流应 4 段，实际 " + fork.size());
            helper.assertTrue(branchSegments >= ArcEffect.BRANCHES && branchSegments <= ArcEffect.BRANCHES * 2,
                    "12 条分叉各 1~2 段，实际共 " + branchSegments + " 段");
            helper.assertTrue(trunk.get(0).from().equals(start) && trunk.get(trunk.size() - 1).to().equals(end), "主干两端应在起点、终点上");
            for (int i = 0; i + 1 < trunk.size(); i++) {
                helper.assertTrue(trunk.get(i).to().equals(trunk.get(i + 1).from()), "主干应首尾相接（第 " + i + " 段）");
                Vec3 expected = start.lerp(end, (i + 1) / (double) ArcEffect.TRUNK_SEGMENTS);
                Vec3 p = trunk.get(i).to();
                helper.assertTrue(Math.abs(p.x - expected.x) <= style.curve() + 1e-6 && Math.abs(p.y - expected.y) <= style.curve() + 1e-6
                        && Math.abs(p.z - expected.z) <= style.curve() + 1e-6, "主干第 " + (i + 1) + " 点抖动超过 curve：" + p + " vs " + expected);
            }
            helper.assertTrue(fork.get(0).from().equals(trunk.get(ArcEffect.FORK_VERTEX).from()) && fork.get(fork.size() - 1).to().equals(end),
                    "支流应从主干第 3 点出发、到终点");
            List<Vec3> roots = new ArrayList<>();
            trunk.forEach(s -> roots.add(s.from()));
            fork.forEach(s -> roots.add(s.from()));
            // 分叉按生成顺序连续排列：一段的起点不是上一段分叉的终点，就是新的一条
            int branchStarts = 0;
            ArcEffect.Kind previous = null;
            Vec3 previousEnd = null;
            for (ArcEffect.Segment segment : arc.segments()) {
                boolean newBranch = segment.kind() == ArcEffect.Kind.BRANCH
                        && (previous != ArcEffect.Kind.BRANCH || !segment.from().equals(previousEnd));
                if (newBranch) {
                    helper.assertTrue(roots.contains(segment.from()), "分叉应从主干或支流的段起点出发");
                    branchStarts++;
                }
                previous = segment.kind();
                previousEnd = segment.to();
            }
            helper.assertTrue(branchStarts == ArcEffect.BRANCHES, "应有 12 条分叉，实际 " + branchStarts);
            helper.assertTrue(arc.bounds().contains(start) && arc.bounds().contains(end), "包围盒应包住两端");
        }
        helper.succeed();
    }

    /**
     * 平面版（研究手册的 2D 电弧）和 3D 版同一套形状：主干 8 段首尾相接、两端钉住、抖动不超过 curve；支流 4 段从主干第 3 点到终点；
     * 分叉 12 条各 1~2 段、从主干 / 支流的段起点出发、伸出不超过 reach + curve / 2；从偏移处写起，不碰前面的段。
     * 少长分叉的版本（短火花用，这里测 5 条和 0 条）：根在 12 个候选根里等距取（第 i 条取第 i × 12 / 条数 个），其余同上
     */
    @GameTest(template = EMPTY)
    public static void arcPlanarGeometry(GameTestHelper helper) {
        float x0 = 10F, y0 = 40F, x1 = 130F, y1 = 70F, curve = 6F, reach = 14F;
        float[] out = new float[ArcEffect.MAX_SEGMENTS * 2 * 4];
        byte[] kinds = new byte[ArcEffect.MAX_SEGMENTS * 2];
        int first = ArcEffect.MAX_SEGMENTS;
        int trunkKind = ArcEffect.Kind.TRUNK.ordinal();
        int forkKind = ArcEffect.Kind.FORK.ordinal();
        int branchKind = ArcEffect.Kind.BRANCH.ordinal();
        for (int branches : new int[] {ArcEffect.BRANCHES, 5, 0})
        for (long seed = 0; seed < 20; seed++) {
            java.util.Arrays.fill(out, -999F);
            int n = branches == ArcEffect.BRANCHES
                    ? ArcEffect.planar(RandomSource.create(seed), x0, y0, x1, y1, curve, reach, out, kinds, first)
                    : ArcEffect.planar(RandomSource.create(seed), x0, y0, x1, y1, curve, reach, branches, out, kinds, first);
            int minN = ArcEffect.TRUNK_SEGMENTS + ArcEffect.FORK_SEGMENTS + branches;
            int maxN = ArcEffect.TRUNK_SEGMENTS + ArcEffect.FORK_SEGMENTS + branches * 2;
            helper.assertTrue(n >= minN && n <= maxN, "平面电弧（" + branches + " 条分叉）段数应在 " + minN + "~" + maxN + "，实际 " + n);
            helper.assertTrue(out[0] == -999F && out[first * 4 - 1] == -999F, "不应写到偏移之前");
            for (int i = 0; i < ArcEffect.TRUNK_SEGMENTS; i++) {
                int o = (first + i) * 4;
                helper.assertTrue(kinds[first + i] == trunkKind, "前 8 段应是主干");
                if (i > 0) {
                    helper.assertTrue(out[o] == out[o - 2] && out[o + 1] == out[o - 1], "主干应首尾相接（第 " + i + " 段）");
                }
                if (i < ArcEffect.TRUNK_SEGMENTS - 1) {
                    float t = (i + 1) / (float) ArcEffect.TRUNK_SEGMENTS;
                    helper.assertTrue(Math.abs(out[o + 2] - (x0 + (x1 - x0) * t)) <= curve + 1e-4F
                            && Math.abs(out[o + 3] - (y0 + (y1 - y0) * t)) <= curve + 1e-4F, "主干第 " + (i + 1) + " 点抖动超过 curve");
                }
            }
            int lastTrunk = (first + ArcEffect.TRUNK_SEGMENTS - 1) * 4;
            helper.assertTrue(out[first * 4] == x0 && out[first * 4 + 1] == y0 && out[lastTrunk + 2] == x1 && out[lastTrunk + 3] == y1,
                    "主干两端应在起点、终点上");
            int forkFirst = (first + ArcEffect.TRUNK_SEGMENTS) * 4;
            int forkLast = (first + ArcEffect.TRUNK_SEGMENTS + ArcEffect.FORK_SEGMENTS - 1) * 4;
            int vertex = (first + ArcEffect.FORK_VERTEX) * 4;
            helper.assertTrue(kinds[first + ArcEffect.TRUNK_SEGMENTS] == forkKind && out[forkFirst] == out[vertex] && out[forkFirst + 1] == out[vertex + 1]
                    && out[forkLast + 2] == x1 && out[forkLast + 3] == y1, "支流应从主干第 3 点出发、到终点");
            int branchStarts = 0;
            float px = Float.NaN, py = Float.NaN;
            for (int s = ArcEffect.TRUNK_SEGMENTS + ArcEffect.FORK_SEGMENTS; s < n; s++) {
                int o = (first + s) * 4;
                helper.assertTrue(kinds[first + s] == branchKind, "主干、支流之后应全是分叉");
                if (out[o] != px || out[o + 1] != py) {
                    // 新的一条分叉：根是第 branchStarts × 12 / 条数 段的起点，伸出不超过 reach（中间点另有 curve / 2 的抖动）
                    int rootSeg = branchStarts * ArcEffect.BRANCHES / branches;
                    int root = (first + rootSeg) * 4;
                    helper.assertTrue(out[o] == out[root] && out[o + 1] == out[root + 1], "第 " + branchStarts + " 条分叉应从第 " + rootSeg + " 段的起点出发");
                    branchStarts++;
                }
                int root = (first + (branchStarts - 1) * ArcEffect.BRANCHES / branches) * 4;
                helper.assertTrue(Math.abs(out[o + 2] - out[root]) <= reach + curve / 2F + 1e-4F
                        && Math.abs(out[o + 3] - out[root + 1]) <= reach + curve / 2F + 1e-4F, "分叉伸出超过 reach");
                px = out[o + 2];
                py = out[o + 3];
            }
            helper.assertTrue(branchStarts == branches, "应有 " + branches + " 条分叉，实际 " + branchStarts);
            helper.assertTrue((first + n) * 4 >= out.length || out[(first + n) * 4] == -999F, "不应写到第 n 段之后");
        }
        helper.succeed();
    }

    /** 寿命按 tick 走：20 tick 的第 20 次 tick 到期；最后四分之一淡出；tickAll 删掉到期的；寿命 0 的样式不合法；两个音效已注册 */
    @GameTest(template = EMPTY)
    public static void arcLifetimeAndSounds(GameTestHelper helper) {
        ArcEffect arc = new ArcEffect(RandomSource.create(7L), Vec3.ZERO, new Vec3(0D, 1D, 0D), ArcEffect.Style.COIL_IDLE);
        helper.assertTrue(arc.lifetime() == ArcEffect.DEFAULT_LIFETIME && arc.alpha(0F) == 1F, "新电弧应不透明、寿命 20");
        for (int i = 1; i < 20; i++) {
            helper.assertFalse(arc.tick(), "第 " + i + " 次 tick 不应到期");
        }
        helper.assertTrue(arc.alpha(0F) > 0F && arc.alpha(0F) < 1F, "最后几 tick 应在淡出，实际 " + arc.alpha(0F));
        helper.assertTrue(arc.tick() && arc.isExpired() && arc.alpha(0F) == 0F, "第 20 次 tick 应到期、完全透明");

        List<ArcEffect> arcs = new ArrayList<>();
        arcs.add(arc);
        ArcEffect fresh = new ArcEffect(RandomSource.create(8L), Vec3.ZERO, new Vec3(1D, 0D, 0D), ArcEffect.Style.COIL_HARDENER);
        arcs.add(fresh);
        ArcEffect.tickAll(arcs);
        helper.assertTrue(arcs.size() == 1 && arcs.get(0) == fresh && fresh.age() == 1, "tickAll 应删掉到期的、推进其余的");
        helper.assertTrue(fresh.lifetime() == 40, "基座火花寿命应为 40（原作 TileMRUCoilHardener L40）");

        boolean rejected = false;
        try {
            ArcEffect.Style.REACTOR.withLifetime(0);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        helper.assertTrue(rejected, "寿命 0 的样式应被拒");

        helper.assertTrue(ECSounds.LIGHTNING_HIT.getId().equals(EssentialCraft5.id("sound.lightning_hit"))
                && ForgeRegistries.SOUND_EVENTS.containsKey(EssentialCraft5.id("sound.lightning_hit"))
                && ForgeRegistries.SOUND_EVENTS.containsKey(EssentialCraft5.id("sound.gen_electricity")), "电弧两个音效应已注册");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // F4-15 MRUBlockEntity 同步开关
    // ------------------------------------------------------------------

    /** 测试用机器：借充能仓的方块实体类型，同步开关由参数决定（不放进世界） */
    private static MRUBlockEntity testMachine(GameTestHelper helper, BlockPos relative, boolean syncInventory, boolean syncBalance) {
        BlockState state = ECBlocks.CHARGING_CHAMBER.get().defaultBlockState();
        MRUBlockEntity be = new MRUBlockEntity(ECBlockEntities.CHARGING_CHAMBER.get(), helper.absolutePos(relative), state, () -> 1000, 3) {
            @Override
            protected boolean syncInventoryToClient() {
                return syncInventory;
            }

            @Override
            protected boolean syncBalanceToClient() {
                return syncBalance;
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
                return null;
            }
        };
        be.setLevel(helper.getLevel());
        return be;
    }

    private static int syncCooldown(MRUBlockEntity be) {
        try {
            Field field = MRUBlockEntity.class.getDeclaredField("syncCooldown");
            field.setAccessible(true);
            return field.getInt(be);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void tick(MRUBlockEntity be) {
        MRUBlockEntity.tickServer(be.getLevel(), be.getBlockPos(), be.getBlockState(), be);
    }

    /** 默认开关（一~三批的机器）：更新包带物品栏和平衡值、平衡值变化照发；关掉后都不带、不触发，存盘不受影响 */
    @GameTest(template = EMPTY)
    public static void hooksSyncSwitches(GameTestHelper helper) {
        MRUBlockEntity loud = testMachine(helper, new BlockPos(1, 1, 1), true, true);
        MRUBlockEntity quiet = testMachine(helper, new BlockPos(3, 1, 3), false, false);
        for (MRUBlockEntity be : List.of(loud, quiet)) {
            be.getInventory().setStackInSlot(1, new ItemStack(Items.DIAMOND));
            be.getMRUStorage().setBalance(1.5F);
            be.getMRUStorage().setMRU(300);
        }
        CompoundTag loudTag = loud.getUpdateTag();
        helper.assertTrue(loudTag.contains("inventory") && loudTag.getCompound("mru_storage").contains("balance"), "默认更新包应带物品栏和平衡值");
        CompoundTag quietTag = quiet.getUpdateTag();
        helper.assertFalse(quietTag.contains("inventory"), "关了物品栏同步，更新包不应带物品栏");
        helper.assertFalse(quietTag.getCompound("mru_storage").contains("balance"), "关了平衡值同步，更新包不应带平衡值");
        helper.assertTrue(quietTag.getCompound("mru_storage").getInt("mru") == 300, "MRU 存量照旧带着");
        CompoundTag saved = quiet.saveWithoutMetadata();
        helper.assertTrue(saved.contains("inventory") && saved.getCompound("mru_storage").contains("balance"), "存盘不受同步开关影响");

        // 默认开关：第一次 tick 就因为平衡值发包（进入 10 tick 冷却），和以前一样
        tick(loud);
        helper.assertTrue(syncCooldown(loud) == MRUBlockEntity.SYNC_INTERVAL, "默认机器第一次 tick 应发更新包");
        // 关掉的：物品栏、平衡值怎么变都不发
        tick(quiet);
        helper.assertTrue(syncCooldown(quiet) == 0, "关了同步的机器，物品栏 / 平衡值变化不应发包");
        quiet.getInventory().setStackInSlot(2, new ItemStack(Items.EMERALD));
        quiet.getMRUStorage().setBalance(0.5F);
        tick(quiet);
        helper.assertTrue(syncCooldown(quiet) == 0, "再变一次也不应发包");
        // markClientDirty：下一次 tick 发一次，同样节流
        quiet.markClientDirty();
        tick(quiet);
        helper.assertTrue(syncCooldown(quiet) == MRUBlockEntity.SYNC_INTERVAL, "markClientDirty 后下一次 tick 应发更新包");
        quiet.markClientDirty();
        tick(quiet);
        helper.assertTrue(syncCooldown(quiet) == MRUBlockEntity.SYNC_INTERVAL - 1, "冷却中不应再发（节流）");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // F4-15 MRUDrain.canAfford
    // ------------------------------------------------------------------

    private static void expectSame(GameTestHelper helper, Player player, ItemStack own, int amount, boolean expected, String what) {
        boolean afford = MRUDrain.canAfford(player, own, amount);
        helper.assertTrue(afford == expected, what + "：canAfford 应为 " + expected);
        boolean used = MRUDrain.playerUseMRU(player, own, amount);
        helper.assertTrue(used == afford, what + "：canAfford（" + afford + "）应与 playerUseMRU（" + used + "）一致");
    }

    private static IMRUHandlerItem handler(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).orElseThrow(IllegalStateException::new);
    }

    /** 与 playerUseMRU 对拍：创造模式、只有储存物品、只有自存、都不够；canAfford 本身不扣 */
    @GameTest(template = EMPTY)
    public static void hooksCanAffordMatchesPlayerUseMRU(GameTestHelper helper) {
        // 只有储存物品
        Player player = helper.makeMockPlayer();
        ItemStack shard = new ItemStack(ECItems.SOUL_SHARD.get());
        handler(shard).setMRU(300);
        player.getInventory().setItem(0, shard);
        helper.assertTrue(MRUDrain.canAfford(player, ItemStack.EMPTY, 200) && handler(shard).getMRU() == 300, "canAfford 不应扣");
        expectSame(helper, player, ItemStack.EMPTY, 500, false, "储存物品 300 不够 500");
        expectSame(helper, player, ItemStack.EMPTY, 200, true, "储存物品 300 够 200");
        helper.assertTrue(handler(shard).getMRU() == 100, "playerUseMRU 应扣到 100，实际 " + handler(shard).getMRU());

        // 只有自存（魔力灯笼不是储存物品）
        Player own = helper.makeMockPlayer();
        ItemStack lantern = new ItemStack(ECEssence.MAGICAL_LANTERN.get());
        handler(lantern).setMRU(300);
        helper.assertFalse(handler(lantern).isStorage(), "魔力灯笼不应是储存物品（本测试的前提）");
        expectSame(helper, own, lantern, 200, true, "自存 300 够 200");
        expectSame(helper, own, lantern, 200, false, "自存剩 100 不够 200");
        expectSame(helper, own, lantern, 0, true, "0 MRU 恒够");

        // 都不够：储存物品 100 + 自存 100，要 150（两边不合并，同 playerUseMRU）
        Player both = helper.makeMockPlayer();
        ItemStack shard2 = new ItemStack(ECItems.SOUL_SHARD.get());
        handler(shard2).setMRU(100);
        both.getInventory().setItem(0, shard2);
        ItemStack lantern2 = new ItemStack(ECEssence.MAGICAL_LANTERN.get());
        handler(lantern2).setMRU(100);
        expectSame(helper, both, lantern2, 150, false, "储存 100 + 自存 100 不够 150");
        helper.assertTrue(handler(shard2).getMRU() == 100 && handler(lantern2).getMRU() == 100, "不够时两边都不应扣");

        // 创造模式
        Player creative = helper.makeMockPlayer();
        creative.getAbilities().instabuild = true;
        expectSame(helper, creative, ItemStack.EMPTY, 1_000_000, true, "创造模式");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // F4-15 进度触发器 structure_formed
    // ------------------------------------------------------------------

    /** 已登记；JSON 读写；条件匹配（结构 id、最小半径）；非法 id 报错 */
    @GameTest(template = EMPTY)
    public static void hooksStructureTriggerConditions(GameTestHelper helper) {
        helper.assertTrue((Object) CriteriaTriggers.getCriterion(StructureFormedTrigger.ID) == StructureFormedTrigger.INSTANCE, "触发器应已登记");
        ResourceLocation coil = EssentialCraft5.id("mru_coil");
        ResourceLocation reactor = EssentialCraft5.id("reactor");
        DeserializationContext context = new DeserializationContext(EssentialCraft5.id("test/structure_formed"), helper.getLevel().getServer().getLootData());

        JsonObject json = new JsonObject();
        json.addProperty("structure", coil.toString());
        json.addProperty("min_radius", 7);
        StructureFormedTrigger.Instance instance = StructureFormedTrigger.INSTANCE.createInstance(json, context);
        helper.assertTrue(coil.equals(instance.structure()) && instance.minRadius() == 7, "JSON 读出的条件不对");
        helper.assertTrue(instance.matches(coil, 7) && instance.matches(coil, 32), "线圈半径 ≥7 应匹配");
        helper.assertFalse(instance.matches(coil, 6), "半径 6 不应匹配");
        helper.assertFalse(instance.matches(reactor, 99), "别的结构不应匹配");
        JsonObject written = instance.serializeToJson(SerializationContext.INSTANCE);
        helper.assertTrue(coil.toString().equals(written.get("structure").getAsString()) && written.get("min_radius").getAsInt() == 7, "写回的 JSON 不对：" + written);

        StructureFormedTrigger.Instance any = StructureFormedTrigger.INSTANCE.createInstance(new JsonObject(), context);
        helper.assertTrue(any.structure() == null && any.matches(reactor, 0), "不写条件应匹配任何结构");

        JsonObject bad = new JsonObject();
        bad.addProperty("structure", "Not A Valid:Id");
        boolean rejected = false;
        try {
            StructureFormedTrigger.INSTANCE.createInstance(bad, context);
        } catch (JsonParseException e) {
            rejected = true;
        }
        helper.assertTrue(rejected, "非法的结构 id 应报错");
        helper.succeed();
    }

    /** 直接对 realPlayer 调 trigger：不匹配不给；匹配给一次；再调不重复；16 格内的才算「附近」 */
    @GameTest(template = EMPTY)
    public static void hooksStructureTriggerAwardsOnce(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ResourceLocation coil = EssentialCraft5.id("mru_coil");
        StructureFormedTrigger.Instance instance = StructureFormedTrigger.Instance.of(coil, 5);
        Advancement advancement = new Advancement(EssentialCraft5.id("test/structure_formed"), null, null, AdvancementRewards.EMPTY,
                Map.of("formed", new Criterion(instance)), new String[][]{{"formed"}}, false);
        int[] earned = {0};
        Consumer<AdvancementEvent.AdvancementEarnEvent> onEarn = event -> {
            if (event.getAdvancement() == advancement) {
                earned[0]++;
            }
        };
        MinecraftForge.EVENT_BUS.addListener(onEarn);
        try {
            StructureFormedTrigger.INSTANCE.addPlayerListener(player.getAdvancements(), new CriterionTrigger.Listener<>(instance, advancement, "formed"));
            StructureFormedTrigger.INSTANCE.trigger(player, EssentialCraft5.id("reactor"), 9);
            StructureFormedTrigger.INSTANCE.trigger(player, coil, 3);
            helper.assertFalse(player.getAdvancements().getOrStartProgress(advancement).isDone(), "结构不对或半径不够时不应完成");
            StructureFormedTrigger.INSTANCE.trigger(player, coil, 7);
            helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(), "匹配时应完成");
            StructureFormedTrigger.INSTANCE.trigger(player, coil, 9);
            helper.assertTrue(earned[0] == 1, "只应给一次，实际 " + earned[0]);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(onEarn);
            player.getAdvancements().stopListening();
        }

        // 「附近」：到方块中心 16 格以内
        BlockPos center = new BlockPos(2, 1, 2);
        Vec3 c = Vec3.atCenterOf(helper.absolutePos(center));
        ServerPlayer near = ProtectionFrameworkTests.realPlayer(helper);
        near.moveTo(c.x + 10D, c.y, c.z, 0F, 0F);
        ServerPlayer far = ProtectionFrameworkTests.realPlayer(helper);
        far.moveTo(c.x + 17D, c.y, c.z, 0F, 0F);
        List<ServerPlayer> within = StructureFormedTrigger.playersWithin(List.of(near, far), c, StructureFormedTrigger.TRIGGER_RANGE);
        helper.assertTrue(within.size() == 1 && within.get(0) == near, "只应找到 16 格内的那名玩家");
        helper.succeed();
    }
}
