package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.VoidArmorItem;
import com.flameforge.essentialcraft5.item.armor.ArmorBonus;
import com.flameforge.essentialcraft5.item.armor.ArmorHealthRestore;
import com.flameforge.essentialcraft5.item.armor.ConfiguredArmorStats;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.util.ECFlight;
import com.google.common.collect.Multimap;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.FrameType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 第八批框架 8-Fb「共用注册名、资源与装备框架」的 GameTest（batch8.md §3.2 第 10 条）：
 * <ul>
 *   <li>富神经元护板与四种数学核心已注册、是普通物品、核心没有配方、不在代用标签里；全息唱片（RecordItem、长度、比较器、音效、标签）；Boss 的 4 个音效与唱片音效；
 *       进度 {@code main/hologram}（父进度、goal 框、impossible 条件）；协议号 6。音效定义与 .ogg 由 RegistryFrameworkTests 统一查。</li>
 *   <li>{@link ConfiguredArmorStats}：数额 0 也放修饰符、同一套数复用缓存、护甲 UUID 同原版、生命 / 移速 UUID 按部位和套装分开；
 *       穿着时热改成 0 再按新表脱下，属性上不留修饰符；{@link ArmorHealthRestore} 重登 / 末地回来后恢复超出基础上限的生命。</li>
 *   <li>{@link MRUShield}：花费公式；四件的份额与单价；没电的不挡、扣不起的不挡、{@code bypasses_armor} 和不在挡的范围内的不挡；
 *       虚空 / 宅男混穿按同一下伤害算份额、混合端预算跨两套扣；
 *       混合端待付（只认同一个伤害源对象、只付一次、onPaid 在付款后调一次、跨了刻的丢掉不扣）。</li>
 *   <li>{@link ECFlight}：给、叠来源取最快、只撤自己的（本来就能飞的不关）、创造模式不碰、压制器暂停与恢复、重登后宽限期内没人续就撤；
 *       原版登录 / 生存 ↔ 冒险互切清掉的飞行重新给上、换维度补发不改能力。</li>
 * </ul>
 * 盔甲挡伤害用虚空套装当载体（框架阶段还没有别的 {@code IMRUShieldArmor}；混穿那条再加宅男护腿 / 靴子），挨打的是 mock 玩家；飞行要 {@link ServerPlayer}，用
 * {@link ProtectionFrameworkTests#realPlayer}（不进世界、不发 tick 事件，直接调 {@link ECFlight#update}）。全局开关（混合端模拟）同一段同步代码里设、测、置回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoloComputerFrameworkTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 1.0E-4F;
    private static final ArmorItem.Type[] TYPES = ArmorItem.Type.values();
    private static final ResourceLocation SOURCE_A = EssentialCraft5.id("test_flight_a");
    private static final ResourceLocation SOURCE_B = EssentialCraft5.id("test_flight_b");

    private HoloComputerFrameworkTests() {
    }

    // ================================================================ 共用注册名

    /** 五个共用物品已注册、是普通物品；四种核心没有任何配方产出；都不在 cores/magic、plates/magic 里 */
    @GameTest(template = EMPTY)
    public static void holoComputerSharedItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Item> cores = List.of(ECComputer.ADDITION_CORE.get(), ECComputer.SUBTRACTION_CORE.get(),
                ECComputer.MULTIPLICATION_CORE.get(), ECComputer.DIVISION_CORE.get());
        List<Item> all = new ArrayList<>(cores);
        all.add(0, ECComputer.NEURONIC_ENRICHED_PLATING.get());
        String[] names = {"neuronic_enriched_plating", "addition_core", "subtraction_core", "multiplication_core", "division_core"};
        for (int i = 0; i < names.length; i++) {
            Item item = all.get(i);
            helper.assertTrue(EssentialCraft5.id(names[i]).equals(ForgeRegistries.ITEMS.getKey(item)), names[i] + " 应已注册，实际 " + ForgeRegistries.ITEMS.getKey(item));
            helper.assertTrue(item.getClass() == Item.class, names[i] + " 应是普通物品");
            ItemStack stack = new ItemStack(item);
            helper.assertFalse(stack.is(ECTags.modItem("cores/magic")) || stack.is(ECTags.modItem("plates/magic")),
                    names[i] + " 不进 cores/magic、plates/magic 代用标签");
        }
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            helper.assertFalse(cores.contains(result.getItem()), "数学核心只从全息投影掉，不该有配方产出：" + recipe.getId());
        }
        helper.assertTrue(Integer.parseInt(ECNetwork.PROTOCOL) >= 6, "第八批加了宅男套装的 C2S 包，协议号应至少为 6，实际 " + ECNetwork.PROTOCOL);
        helper.succeed();
    }

    /** 全息唱片：RecordItem、107.04 秒 = 2 141 刻、比较器 15、一组 1 个、音效 records.hologram；在 music_discs，不在 creeper_drop_music_discs */
    @GameTest(template = EMPTY)
    public static void holoComputerHologramDisc(GameTestHelper helper) {
        Item item = ECHologram.MUSIC_DISC_ROPOCALYPSE.get();
        helper.assertTrue(EssentialCraft5.id("music_disc_ropocalypse").equals(ForgeRegistries.ITEMS.getKey(item)), "唱片注册名");
        helper.assertTrue(item instanceof RecordItem, "全息唱片应是 RecordItem（魔力唱片机按它收）");
        RecordItem disc = (RecordItem) item;
        helper.assertTrue(disc.getLengthInTicks() == 2141, "长度 2 141 刻，实际 " + disc.getLengthInTicks());
        helper.assertTrue(disc.getAnalogOutput() == 15, "比较器 15，实际 " + disc.getAnalogOutput());
        helper.assertTrue(disc.getSound() == ECHologram.RECORD.get(), "唱片放 records.hologram");
        helper.assertTrue(new ItemStack(item).getMaxStackSize() == 1, "一组 1 个");
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.is(ItemTags.MUSIC_DISCS), "应在 minecraft:music_discs 里");
        helper.assertFalse(stack.is(ItemTags.CREEPER_DROP_MUSIC_DISCS), "不进 creeper_drop_music_discs（原作苦力怕不掉它）");
        helper.succeed();
    }

    /** Boss 的 4 个音效、唱片音效已注册（定义和 .ogg 由 RegistryFrameworkTests 查） */
    @GameTest(template = EMPTY)
    public static void holoComputerHologramSounds(GameTestHelper helper) {
        String[] names = {"sound.mob.hologram.stop", "sound.mob.hologram.damage.melee", "sound.mob.hologram.damage.projectile",
                "sound.mob.hologram.shutdown", "records.hologram"};
        for (String name : names) {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(EssentialCraft5.id(name));
            helper.assertTrue(sound != null && EssentialCraft5.id(name).equals(sound.getLocation()), name + " 应已注册");
        }
        helper.assertFalse(ForgeRegistries.SOUND_EVENTS.containsKey(EssentialCraft5.id("sound.mob.hologram.start")),
                "原作从没播放过的 sound.mob.hologram.start 不注册");
        helper.succeed();
    }

    /** 进度 main/hologram：父 main/demon_trade、goal 框、只有一个 impossible 条件（由代码发） */
    @GameTest(template = EMPTY)
    public static void holoComputerHologramAdvancement(GameTestHelper helper) {
        Advancement advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(ECHologram.ADVANCEMENT_HOLOGRAM);
        helper.assertTrue(advancement != null, "进度 main/hologram 应能加载");
        helper.assertTrue(advancement.getParent() != null && EssentialCraft5.id("main/demon_trade").equals(advancement.getParent().getId()),
                "父进度应是 main/demon_trade");
        helper.assertTrue(advancement.getDisplay() != null && advancement.getDisplay().getFrame() == FrameType.GOAL, "goal 框");
        helper.assertTrue(advancement.getCriteria().size() == 1, "只有一个条件");
        Criterion criterion = advancement.getCriteria().values().iterator().next();
        helper.assertTrue(criterion.getTrigger() != null && ResourceLocation.withDefaultNamespace("impossible").equals(criterion.getTrigger().getCriterion()),
                "条件是 minecraft:impossible（Boss 死时由代码发）");
        helper.succeed();
    }

    // ================================================================ ConfiguredArmorStats

    /**
     * 数额 0 也照放护甲 / 韧性 / 击退抗性 / 生命修饰符；同一套数复用同一张表；护甲 UUID 和原版同部位一样；生命、移速 UUID 按部位、按套装分开；
     * 没说有可配置移速的不放移速，说了的四件都放（没有移速的放 0）；ArmorBonus 接在后面
     */
    @GameTest(template = EMPTY)
    public static void holoComputerArmorStatsModifiers(GameTestHelper helper) {
        ConfiguredArmorStats chest = new ConfiguredArmorStats(ArmorItem.Type.CHESTPLATE, "test_suit", false, ArmorBonus.none());
        ConfiguredArmorStats.Piece zero = ConfiguredArmorStats.Piece.of(0D, 0D, 0D, 0D);
        Multimap<Attribute, AttributeModifier> zeroMap = chest.modifiers(zero);
        for (Attribute attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE, Attributes.MAX_HEALTH)) {
            helper.assertTrue(zeroMap.get(attribute).size() == 1 && zeroMap.get(attribute).iterator().next().getAmount() == 0D,
                    attribute.getDescriptionId() + " 数额 0 也应放一条修饰符");
        }
        helper.assertTrue(zeroMap.get(Attributes.MOVEMENT_SPEED).isEmpty(), "没有可配置移速的不放移速修饰符");
        helper.assertTrue(chest.modifiers(ConfiguredArmorStats.Piece.of(0D, 0D, 0D, 0D)) == zeroMap, "同一套数复用缓存的表");
        Multimap<Attribute, AttributeModifier> changed = chest.modifiers(ConfiguredArmorStats.Piece.of(16D, 6D, 0.2D, 40D));
        helper.assertTrue(changed != zeroMap && changed.get(Attributes.ARMOR).iterator().next().getAmount() == 16D
                && changed.get(Attributes.MAX_HEALTH).iterator().next().getAmount() == 40D, "配置变了重建，数额跟着变");

        UUID vanillaChest = Items.IRON_CHESTPLATE.getDefaultAttributeModifiers(ArmorItem.Type.CHESTPLATE.getSlot()).get(Attributes.ARMOR)
                .iterator().next().getId();
        helper.assertTrue(changed.get(Attributes.ARMOR).iterator().next().getId().equals(vanillaChest)
                && ConfiguredArmorStats.vanillaArmorModifierId(ArmorItem.Type.CHESTPLATE).equals(vanillaChest), "护甲修饰符 UUID 应和原版同部位一样");
        helper.assertTrue(changed.get(Attributes.MAX_HEALTH).iterator().next().getId().equals(ConfiguredArmorStats.healthModifierId(ArmorItem.Type.CHESTPLATE, "test_suit")),
                "生命修饰符用按套装、部位固定的 UUID");
        helper.assertFalse(ConfiguredArmorStats.healthModifierId(ArmorItem.Type.CHESTPLATE, "test_suit")
                .equals(ConfiguredArmorStats.healthModifierId(ArmorItem.Type.HELMET, "test_suit")), "不同部位的生命 UUID 不同（四件能叠加）");
        helper.assertFalse(ConfiguredArmorStats.healthModifierId(ArmorItem.Type.CHESTPLATE, "test_suit")
                .equals(ConfiguredArmorStats.healthModifierId(ArmorItem.Type.CHESTPLATE, "other_suit")), "不同套装的生命 UUID 不同");
        helper.assertTrue(VoidArmorItem.healthModifierId(ArmorItem.Type.CHESTPLATE)
                        .equals(ArmorBonus.uuid(ArmorItem.Type.CHESTPLATE, "essentialcraft5:void_armor/max_health")),
                "虚空套装的生命 UUID 和第七批一样（老存档的修饰符换装时照样去得掉）");

        ArmorBonus step = ArmorBonus.of().add(() -> Attributes.ARMOR_TOUGHNESS, 1D, AttributeModifier.Operation.ADDITION);
        ConfiguredArmorStats legs = new ConfiguredArmorStats(ArmorItem.Type.LEGGINGS, "test_suit", true, step);
        Multimap<Attribute, AttributeModifier> legMap = legs.modifiers(new ConfiguredArmorStats.Piece(12D, 6D, 0.2D, 20D, 0.075D));
        helper.assertTrue(legMap.get(Attributes.MOVEMENT_SPEED).size() == 1
                && Math.abs(legMap.get(Attributes.MOVEMENT_SPEED).iterator().next().getAmount() - 0.075D) < 1.0E-9
                && legMap.get(Attributes.MOVEMENT_SPEED).iterator().next().getId().equals(ConfiguredArmorStats.speedModifierId(ArmorItem.Type.LEGGINGS, "test_suit")),
                "有可配置移速的放一条移速修饰符");
        helper.assertTrue(legs.modifiers(ConfiguredArmorStats.Piece.of(12D, 6D, 0.2D, 20D)).get(Attributes.MOVEMENT_SPEED).iterator().next().getAmount() == 0D,
                "移速 0 也照放");
        helper.assertTrue(legMap.get(Attributes.ARMOR_TOUGHNESS).size() == 2, "ArmorBonus 的修饰符接在配置给的后面");
        helper.succeed();
    }

    /**
     * 热改不残留：穿着时按旧配置加了生命、击退抗性，服主把配置改成 0，原版脱装时按「现在」算出的表去修饰符——表里有数额 0 的同 UUID 修饰符，照样去得掉。
     * 用虚空胸甲走真的配置覆盖值（ECVoidArmor.Config.setTestOverrides）
     */
    @GameTest(template = EMPTY)
    public static void holoComputerArmorStatsHotReload(GameTestHelper helper) {
        Player player = mock(helper);
        ItemStack chest = new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get());
        ECVoidArmor.Config.setTestOverrides(new ECVoidArmor.Config.Values(6D, 16D, 12D, 6D, 6D, 0.2D, 20D));
        try {
            player.getAttributes().addTransientAttributeModifiers(chest.getAttributeModifiers(ArmorItem.Type.CHESTPLATE.getSlot()));
            helper.assertTrue(Math.abs(player.getMaxHealth() - 40F) < EPS, "穿上 +20 生命，实际 " + player.getMaxHealth());
            ECVoidArmor.Config.setTestOverrides(new ECVoidArmor.Config.Values(6D, 16D, 12D, 6D, 6D, 0D, 0D));
            player.getAttributes().removeAttributeModifiers(chest.getAttributeModifiers(ArmorItem.Type.CHESTPLATE.getSlot()));
            UUID health = VoidArmorItem.healthModifierId(ArmorItem.Type.CHESTPLATE);
            helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH).getModifier(health) == null, "热改成 0 再脱下，生命修饰符应去掉");
            helper.assertTrue(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifiers().isEmpty(), "击退抗性修饰符应去掉");
            helper.assertTrue(Math.abs(player.getMaxHealth() - 20F) < EPS, "生命上限回到 20，实际 " + player.getMaxHealth());
        } finally {
            ECVoidArmor.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    /**
     * 盔甲的生命修饰符不进存档：读档 / 从末地回来时先按不含盔甲的上限夹当前生命，修饰符第一刻才加回来。
     * {@link ArmorHealthRestore}：超出基础上限的生命先记下，修饰符回来以后恢复；存的比现在少不改；不超过基础上限不记；
     * 末地回来记在新玩家对象上，恢复时按现在的上限夹
     */
    @GameTest(template = EMPTY)
    public static void holoComputerArmorHealthRestore(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ECComputer.Config.setTestOverrides(ECComputer.Config.DEFAULTS);
        try {
            Multimap<Attribute, AttributeModifier> chest = new ItemStack(ECComputer.COMPUTER_CHESTPLATE.get())
                    .getAttributeModifiers(ArmorItem.Type.CHESTPLATE.getSlot());
            player.getAttributes().addTransientAttributeModifiers(chest);
            helper.assertTrue(Math.abs(player.getMaxHealth() - 60F) < EPS, "宅男胸甲 +40，上限 60，实际 " + player.getMaxHealth());
            player.setHealth(50F);
            ArmorHealthRestore.save(player);
            helper.assertTrue(player.getPersistentData().getFloat(ArmorHealthRestore.KEY) == 50F, "超出基础上限的生命记下来");
            // 模拟读档：临时修饰符不在，setHealth 被夹到 20
            player.getAttributes().removeAttributeModifiers(chest);
            player.setHealth(50F);
            helper.assertTrue(Math.abs(player.getHealth() - 20F) < EPS, "自检：没有盔甲修饰符时生命被夹到 20，实际 " + player.getHealth());
            // 第一刻换装检测把修饰符加回来以后
            player.getAttributes().addTransientAttributeModifiers(chest);
            ArmorHealthRestore.restore(player);
            helper.assertTrue(Math.abs(player.getHealth() - 50F) < EPS, "修饰符回来以后恢复到 50，实际 " + player.getHealth());
            helper.assertFalse(player.getPersistentData().contains(ArmorHealthRestore.KEY), "恢复后记录删掉");

            player.getPersistentData().putFloat(ArmorHealthRestore.KEY, 30F);
            ArmorHealthRestore.restore(player);
            helper.assertTrue(Math.abs(player.getHealth() - 50F) < EPS && !player.getPersistentData().contains(ArmorHealthRestore.KEY),
                    "存的比现在少：不改生命，记录照样删掉");
            player.setHealth(18F);
            ArmorHealthRestore.save(player);
            helper.assertFalse(player.getPersistentData().contains(ArmorHealthRestore.KEY), "不超过基础上限的不记");

            // 从末地回来：旧对象的生命记到新对象上；新对象只穿着 +20 的头盔，恢复时按现在的上限 40 夹
            ServerPlayer copy = ProtectionFrameworkTests.realPlayer(helper);
            player.setHealth(55F);
            ArmorHealthRestore.save(player, copy);
            helper.assertTrue(copy.getPersistentData().getFloat(ArmorHealthRestore.KEY) == 55F
                    && !player.getPersistentData().contains(ArmorHealthRestore.KEY), "末地回来：记在新玩家对象上");
            copy.setHealth(55F);
            copy.getAttributes().addTransientAttributeModifiers(new ItemStack(ECComputer.COMPUTER_HELMET.get())
                    .getAttributeModifiers(ArmorItem.Type.HELMET.getSlot()));
            ArmorHealthRestore.restore(copy);
            helper.assertTrue(Math.abs(copy.getHealth() - 40F) < EPS, "按现在的上限 40 夹，实际 " + copy.getHealth());
        } finally {
            ECComputer.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    // ================================================================ MRUShield

    /** 花费 max(1, ⌊伤害 × 份额 / 20⌋) × 单价：8 点胸甲 3 份 × 250、1 点也按 1 点、极大夹到 int 上限；虚空套装的接口实现 */
    @GameTest(template = EMPTY)
    public static void holoComputerShieldCost(GameTestHelper helper) {
        helper.assertTrue(MRUShield.cost(8F, 3, 250) == 250 && MRUShield.cost(8F, 8, 250) == 750 && MRUShield.cost(8F, 6, 250) == 500,
                "8 点：头 1 点、胸 3 点、腿 2 点（宅男套装合计 7 点 × 250 = 1 750）");
        helper.assertTrue(MRUShield.cost(0.1F, 3, 250) == 250, "不到 1 点按 1 点算");
        helper.assertTrue(MRUShield.cost(1.0E30F, 8, 800) == Integer.MAX_VALUE, "极大夹到 int 上限");
        VoidArmorItem chest = ECVoidArmor.VOID_CHESTPLATE.get();
        ItemStack stack = new ItemStack(chest);
        helper.assertTrue(chest.shieldShare(ArmorItem.Type.CHESTPLATE) == 8 && chest.mruPerDamage(stack) == 400, "虚空胸甲 8 份、每点 400（第十三批起的默认）");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        helper.assertTrue(chest.shields(stack, helper.getLevel().damageSources().mobAttack(zombie))
                && !chest.shields(stack, helper.getLevel().damageSources().magic()), "虚空套装只挡物理（近战挡、魔法不挡）");
        helper.succeed();
    }

    /**
     * absorb：满电全套挨 8 点近战全挡（剩 0），各扣 400 / 1 200 / 800 / 400；胸甲没电时它那份不挡、也不去抽储存物品；
     * 不在挡的范围（魔法）、带 bypasses_armor（/kill）的整下不挡不扣
     */
    @GameTest(template = EMPTY)
    public static void holoComputerShieldAbsorb(GameTestHelper helper) {
        Player player = mock(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        DamageSource melee = player.damageSources().mobAttack(zombie);
        ItemStack[] full = wear(player, 5000, 5000, 5000, 5000);
        helper.assertTrue(MRUShield.absorb(player, melee, 8F) == 0F, "满电全套挡满 8 点");
        int[] cost = {400, 1200, 800, 400};
        for (ArmorItem.Type type : TYPES) {
            helper.assertTrue(mru(full[type.ordinal()]) == 5000 - cost[type.ordinal()], type + " 应扣 " + cost[type.ordinal()] + "，剩 " + mru(full[type.ordinal()]));
        }

        ItemStack[] noChest = wear(player, 5000, 0, 5000, 5000);
        float left = MRUShield.absorb(player, melee, 8F);
        helper.assertTrue(Math.abs(left - 8F * 8 / 20) < EPS, "胸甲没电，它那 8 份不挡，剩 3.2，实际 " + left);
        helper.assertTrue(mru(noChest[1]) == 0, "没电的胸甲不扣");

        ItemStack[] again = wear(player, 5000, 5000, 5000, 5000);
        helper.assertTrue(MRUShield.absorb(player, player.damageSources().magic(), 8F) == 8F, "魔法不在虚空套装挡的范围内，整下不挡");
        helper.assertTrue(MRUShield.absorb(player, player.damageSources().fellOutOfWorld(), 8F) == 8F, "带 bypasses_armor 的不挡");
        for (ItemStack stack : again) {
            helper.assertTrue(mru(stack) == 5000, "不挡就不扣");
        }

        // 扣不起：胸甲只剩 100（不够 1 200），身上没有储存物品：这件不挡、MRU 不动，另三件照挡
        ItemStack[] poor = wear(player, 5000, 100, 5000, 5000);
        left = MRUShield.absorb(player, melee, 8F);
        helper.assertTrue(Math.abs(left - 3.2F) < EPS && mru(poor[1]) == 100, "扣不起的胸甲不挡、MRU 不动，实际剩 " + left + "、胸甲 " + mru(poor[1]));
        helper.succeed();
    }

    /**
     * 混合端待付：plan 只算不扣；defer 记下以后，别的伤害源对象的 LivingDamageEvent 不认；同一个伤害源对象来了才扣、onPaid 在付款后调一次，
     * 再来一次不重复；只挂 onPaid（空花费）的记录也照样认
     */
    @GameTest(template = EMPTY)
    public static void holoComputerShieldDeferred(GameTestHelper helper) {
        Player player = mock(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        DamageSource source = player.damageSources().mobAttack(zombie);
        ItemStack[] stacks = wear(player, 5000, 5000, 5000, 5000);
        int[] paid = {0};
        int[] chestAtPay = {-1};
        RayHitGate.setHybridForTest(true);
        try {
            List<MRUShield.Charge> charges = new ArrayList<>();
            helper.assertTrue(MRUShield.plan(player, source, 8F, charges) == 0F && charges.size() == 4, "plan 算出四件都挡");
            for (ItemStack stack : stacks) {
                helper.assertTrue(mru(stack) == 5000, "plan 不扣");
            }
            MRUShield.defer(player, source, charges, () -> {
                paid[0]++;
                chestAtPay[0] = mru(stacks[1]);
            });
            helper.assertTrue(MRUShield.pendingCharges(player, source).equals(charges), "待付记录里是算好的花费");

            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, player.damageSources().mobAttack(zombie), 0F));
            helper.assertTrue(paid[0] == 0 && mru(stacks[1]) == 5000, "别的伤害源对象不认");

            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, source, 0F));
            helper.assertTrue(mru(stacks[1]) == 3800 && paid[0] == 1, "同一下到了才扣、onPaid 调一次，胸甲剩 " + mru(stacks[1]));
            helper.assertTrue(chestAtPay[0] == 3800, "onPaid 在扣完以后调");
            helper.assertFalse(MRUShield.hasPending(player, source), "付过的记录去掉");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, source, 0F));
            helper.assertTrue(mru(stacks[1]) == 3800 && paid[0] == 1, "同一下不重复扣、不重复调");

            DamageSource second = player.damageSources().mobAttack(zombie);
            int[] thorns = {0};
            MRUShield.defer(player, second, List.of(), () -> thorns[0]++);
            helper.assertTrue(MRUShield.hasPending(player, second) && MRUShield.pendingCharges(player, second).isEmpty(), "只挂 onPaid 的记录");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, second, 0F));
            helper.assertTrue(thorns[0] == 1 && mru(stacks[1]) == 3800, "空花费的记录只调 onPaid");
        } finally {
            RayHitGate.setHybridForTest(null);
        }
        helper.succeed();
    }

    /**
     * 混穿：头 / 胸虚空、腿 / 靴宅男，四件按同一下伤害各算各的份额、合计 20 份才挡满。
     * 8 点近战全挡（虚空每点 400：400 / 1 200；宅男每点 250：500 / 250）；8 点火（inFire：带 is_fire、不带 bypasses_armor——
     * onFire 在原版 bypasses_armor 里，整下不挡）只有宅男两件挡（9 份，剩 8 × 11 / 20 = 4.4），虚空两件不扣。
     * 混合端 plan 拿身上储存物品的合计当预算跨两套一笔笔减（1 500：头盔 400 由它付，胸甲 1 200 凑不够改由胸甲自己付，腿 / 靴再由它付），
     * 同一下到了再扣的结果和纯 Forge 依次扣一样
     */
    @GameTest(template = EMPTY)
    public static void holoComputerShieldMixedSuit(GameTestHelper helper) {
        Player player = mock(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        DamageSource melee = player.damageSources().mobAttack(zombie);
        ECComputer.Config.setTestOverrides(ECComputer.Config.DEFAULTS);
        try {
            helper.assertTrue(ECComputer.Config.values().mruPerDamage() == 250
                    && ECComputer.Config.values().shieldScope() == ECComputer.ShieldScope.PHYSICAL_ELEMENTAL, "自检：宅男每点 250、挡物理和元素");
            ItemStack[] mixed = wearMixed(player, 5000);
            helper.assertTrue(MRUShield.absorb(player, melee, 8F) == 0F, "混穿满电挡满 8 点近战");
            assertMru(helper, mixed, new int[]{4600, 3800, 4500, 4750}, "近战：虚空 400 / 1 200、宅男 500 / 250");

            ItemStack[] fire = wearMixed(player, 5000);
            float left = MRUShield.absorb(player, player.damageSources().inFire(), 8F);
            helper.assertTrue(Math.abs(left - 8F * 11 / 20) < EPS, "火只有宅男两件挡 9 份，剩 4.4，实际 " + left);
            assertMru(helper, fire, new int[]{5000, 5000, 4500, 4750}, "火：虚空两件不扣、宅男 500 / 250");

            List<MRUShield.Charge> expected = List.of(new MRUShield.Charge(ArmorItem.Type.HELMET, 400),
                    new MRUShield.Charge(ArmorItem.Type.CHESTPLATE, 1200), new MRUShield.Charge(ArmorItem.Type.LEGGINGS, 500),
                    new MRUShield.Charge(ArmorItem.Type.BOOTS, 250));
            int[] paidTo = {5000, 3800, 5000, 5000};
            RayHitGate.setHybridForTest(true);
            try {
                ItemStack[] hybrid = wearMixed(player, 5000);
                ItemStack storage = storage(player, 1500);
                List<MRUShield.Charge> charges = new ArrayList<>();
                helper.assertTrue(MRUShield.plan(player, melee, 8F, charges) == 0F && charges.equals(expected),
                        "混合端 plan 算出四件都挡、花费同纯 Forge，实际 " + charges);
                assertMru(helper, hybrid, new int[]{5000, 5000, 5000, 5000}, "plan 不扣");
                helper.assertTrue(mru(storage) == 1500, "plan 不扣储存物品");
                MRUShield.defer(player, melee, charges, null);
                MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, melee, 0F));
                assertMru(helper, hybrid, paidTo, "混合端付款：胸甲 1 200 由自己付，其余由储存物品付");
                helper.assertTrue(mru(storage) == 1500 - 400 - 500 - 250, "储存物品付 400 + 500 + 250，剩 " + mru(storage));
            } finally {
                RayHitGate.setHybridForTest(null);
            }
            ItemStack[] forge = wearMixed(player, 5000);
            ItemStack storage = storage(player, 1500);
            helper.assertTrue(MRUShield.absorb(player, melee, 8F) == 0F, "纯 Forge 同样的身家也挡满");
            assertMru(helper, forge, paidTo, "纯 Forge 依次扣，结果和混合端一样");
            helper.assertTrue(mru(storage) == 350, "纯 Forge 储存物品同样剩 350，实际 " + mru(storage));
        } finally {
            ECComputer.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    /**
     * 混合端待付跨了刻：下一刻才来的同一个伤害源对象的 LivingDamageEvent 不扣、不调 onPaid，记录也清掉
     * （插件拦下的那一下走不到 LivingDamageEvent，不会拖到以后被别的事件认领）。defer / 付款本身不看是不是混合端，不用全局覆盖
     */
    @GameTest(template = EMPTY)
    public static void holoComputerShieldDeferredStale(GameTestHelper helper) {
        Player player = mock(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        DamageSource source = player.damageSources().mobAttack(zombie);
        ItemStack[] stacks = wear(player, 5000, 5000, 5000, 5000);
        List<MRUShield.Charge> charges = new ArrayList<>();
        helper.assertTrue(MRUShield.plan(player, source, 8F, charges) == 0F && charges.size() == 4, "自检：plan 算出四件都挡");
        int[] paid = {0};
        MRUShield.defer(player, source, charges, () -> paid[0]++);
        helper.assertTrue(MRUShield.hasPending(player, source), "自检：记下了待付");
        helper.runAfterDelay(1, () -> {
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, source, 0F));
            assertMru(helper, stacks, new int[]{5000, 5000, 5000, 5000}, "跨了刻的待付不扣");
            helper.assertTrue(paid[0] == 0, "跨了刻的不调 onPaid");
            helper.assertFalse(MRUShield.hasPending(player, source), "跨了刻的记录清掉");
            helper.succeed();
        });
    }

    // ================================================================ ECFlight

    /**
     * 给飞行：mayfly 打开、速度按来源；两个来源取最快；撤一个回到另一个的速度；都撤掉关飞行、速度还原 0.05、记账清掉。
     * 本来就能飞（别的模组给的）的，撤掉时不关 mayfly，只还原速度
     */
    @GameTest(template = EMPTY)
    public static void holoComputerFlightGrantRevoke(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ECFlight.grant(player, SOURCE_A, 0.1F);
        helper.assertTrue(player.getAbilities().mayfly && Math.abs(player.getAbilities().getFlyingSpeed() - 0.1F) < EPS && ECFlight.isApplied(player),
                "给了以后能飞、速度 0.1");
        ECFlight.grant(player, SOURCE_B, 0.2F);
        helper.assertTrue(Math.abs(player.getAbilities().getFlyingSpeed() - 0.2F) < EPS, "两个来源取最快的 0.2");
        ECFlight.revoke(player, SOURCE_B);
        helper.assertTrue(player.getAbilities().mayfly && Math.abs(player.getAbilities().getFlyingSpeed() - 0.1F) < EPS, "撤掉一个，回到另一个的 0.1");
        player.getAbilities().flying = true;
        ECFlight.revoke(player, SOURCE_A);
        helper.assertFalse(player.getAbilities().mayfly || player.getAbilities().flying, "都撤掉：不能飞、正在飞的也停");
        helper.assertTrue(Math.abs(player.getAbilities().getFlyingSpeed() - ECFlight.VANILLA_FLY_SPEED) < EPS, "速度还原 0.05");
        helper.assertFalse(player.getPersistentData().contains(ECFlight.TAG), "没有来源以后记账清掉");
        ECFlight.revoke(player, SOURCE_A);
        helper.assertFalse(player.getAbilities().mayfly, "没给过的来源撤掉什么都不做");

        ServerPlayer other = ProtectionFrameworkTests.realPlayer(helper);
        other.getAbilities().mayfly = true;
        ECFlight.grant(other, SOURCE_A, 0.1F);
        ECFlight.revoke(other, SOURCE_A);
        helper.assertTrue(other.getAbilities().mayfly, "本来就能飞的（别的来源给的）不关");
        helper.assertTrue(Math.abs(other.getAbilities().getFlyingSpeed() - ECFlight.VANILLA_FLY_SPEED) < EPS, "只还原本模组改的速度");
        helper.succeed();
    }

    /** 创造模式不碰：照记来源、不改能力；切回生存时重新给上；在生存里给过再切创造，速度还原 */
    @GameTest(template = EMPTY)
    public static void holoComputerFlightCreative(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        ECFlight.grant(player, SOURCE_A, 0.1F);
        helper.assertTrue(ECFlight.isGranted(player, SOURCE_A) && !ECFlight.isApplied(player), "创造模式照记来源、不接管");
        helper.assertTrue(Math.abs(player.getAbilities().getFlyingSpeed() - ECFlight.VANILLA_FLY_SPEED) < EPS, "创造模式的飞行速度不动");
        player.setGameMode(GameType.SURVIVAL);
        helper.assertFalse(player.getAbilities().mayfly, "切回生存，原版先把飞行关掉");
        ECFlight.update(player);
        helper.assertTrue(player.getAbilities().mayfly && ECFlight.isApplied(player)
                && Math.abs(player.getAbilities().getFlyingSpeed() - 0.1F) < EPS, "下一刻重新给上");
        player.setGameMode(GameType.CREATIVE);
        ECFlight.update(player);
        helper.assertTrue(!ECFlight.isApplied(player) && player.getAbilities().mayfly
                && Math.abs(player.getAbilities().getFlyingSpeed() - ECFlight.VANILLA_FLY_SPEED) < EPS, "切到创造：交还原版管、速度还原");
        ECFlight.revoke(player, SOURCE_A);
        helper.assertTrue(player.getAbilities().mayfly, "创造模式撤来源不关飞行");
        player.setGameMode(GameType.SURVIVAL);
        helper.succeed();
    }

    /** 压制器：有一个返回真就暂停（来源照记），去掉以后恢复；飞行中被压制时 flying 也关 */
    @GameTest(template = EMPTY)
    public static void holoComputerFlightSuppressor(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Predicate<ServerPlayer> suppressor = p -> p == player;
        ECFlight.registerSuppressor(suppressor);
        try {
            ECFlight.grant(player, SOURCE_A, 0.1F);
            helper.assertTrue(ECFlight.isGranted(player, SOURCE_A) && !player.getAbilities().mayfly && ECFlight.isSuppressed(player),
                    "压制中：来源照记、不能飞");
            ECFlight.unregisterSuppressor(suppressor);
            ECFlight.update(player);
            helper.assertTrue(player.getAbilities().mayfly && ECFlight.isApplied(player), "压制解除后重新给上");
            player.getAbilities().flying = true;
            ECFlight.registerSuppressor(suppressor);
            ECFlight.update(player);
            helper.assertFalse(player.getAbilities().mayfly || player.getAbilities().flying, "飞着被压制：mayfly、flying 都关");
            helper.assertTrue(ECFlight.isGranted(player, SOURCE_A), "来源还在");
            ServerPlayer bystander = ProtectionFrameworkTests.realPlayer(helper);
            ECFlight.grant(bystander, SOURCE_A, 0.1F);
            helper.assertTrue(bystander.getAbilities().mayfly, "压制器只对它返回真的玩家生效");
        } finally {
            ECFlight.unregisterSuppressor(suppressor);
        }
        ECFlight.revoke(player, SOURCE_A);
        helper.succeed();
    }

    /**
     * 重登：原版登录先读回存档里的能力、再按游戏模式重设（生存下 mayfly / flying 清成假），登录当刻按读档时记下的重新给上，
     * 半空下线的上线照样在飞；只给这一次，之后别的插件关掉飞行不抢回来。记下的来源标成待确认，宽限期内照旧能飞；
     * 宽限期内被重新确认的留下，没人续的到点撤掉
     */
    @GameTest(template = EMPTY)
    public static void holoComputerFlightRelogin(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ECFlight.grant(player, SOURCE_A, 0.1F);
        ECFlight.grant(player, SOURCE_B, 0.2F);
        player.getAbilities().flying = true;
        // 模拟重登：读档事件（能力还是存档里的）→ 原版 loadGameTypes 按生存重设能力 → 登录事件；新玩家对象的 tickCount 从 0 开始
        player.tickCount = 0;
        ECFlight.onLoadedFromSave(player);
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        helper.assertFalse(player.getAbilities().mayfly || player.getAbilities().flying, "自检：原版重设把 mayfly / flying 清掉了");
        ECFlight.loggedIn(player);
        helper.assertTrue(player.getAbilities().mayfly && player.getAbilities().flying && ECFlight.isApplied(player)
                && Math.abs(player.getAbilities().getFlyingSpeed() - 0.2F) < EPS, "登录当刻重新给上：能飞、照旧在飞、速度还是 0.2");
        ECFlight.update(player);
        helper.assertTrue(player.getAbilities().mayfly && ECFlight.isGranted(player, SOURCE_B), "宽限期内照旧能飞");
        ECFlight.grant(player, SOURCE_A, 0.1F);
        player.tickCount = ECFlight.RELOGIN_GRACE_TICKS + 1;
        ECFlight.update(player);
        helper.assertTrue(ECFlight.isGranted(player, SOURCE_A) && !ECFlight.isGranted(player, SOURCE_B), "续上的留下，没人续的撤掉");
        helper.assertTrue(player.getAbilities().mayfly && Math.abs(player.getAbilities().getFlyingSpeed() - 0.1F) < EPS, "速度回到留下的来源的 0.1");
        player.getAbilities().mayfly = false;
        ECFlight.update(player);
        helper.assertFalse(player.getAbilities().mayfly, "重新给上只在登录后做一次：之后别的插件关掉飞行不抢回来");

        ServerPlayer grounded = ProtectionFrameworkTests.realPlayer(helper);
        ECFlight.grant(grounded, SOURCE_A, 0.1F);
        grounded.tickCount = 0;
        ECFlight.onLoadedFromSave(grounded);
        GameType.SURVIVAL.updatePlayerAbilities(grounded.getAbilities());
        ECFlight.loggedIn(grounded);
        helper.assertTrue(grounded.getAbilities().mayfly && !grounded.getAbilities().flying, "在地上下线的：上线能飞，但不在飞");

        ServerPlayer gone = ProtectionFrameworkTests.realPlayer(helper);
        ECFlight.grant(gone, SOURCE_A, 0.1F);
        gone.tickCount = 0;
        ECFlight.markStale(gone);
        gone.tickCount = ECFlight.RELOGIN_GRACE_TICKS + 1;
        ECFlight.update(gone);
        helper.assertFalse(gone.getAbilities().mayfly || ECFlight.isGranted(gone, SOURCE_A), "下线时摘了反重力板：重登后没人续，飞行收回");
        helper.assertFalse(gone.getPersistentData().contains(ECFlight.TAG), "记账清掉");
        helper.succeed();
    }

    /**
     * 生存 ↔ 冒险互切：原版把 mayfly 清掉，下一次 update 认出游戏模式变了重新给上；模式没变时别的插件关掉飞行不抢回来；
     * 换维度时的补发（resync）不改服务端能力
     */
    @GameTest(template = EMPTY)
    public static void holoComputerFlightAdventureSwitch(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        ECFlight.grant(player, SOURCE_A, 0.1F);
        helper.assertTrue(player.getAbilities().mayfly, "自检：生存里给上了");
        player.setGameMode(GameType.ADVENTURE);
        helper.assertFalse(player.getAbilities().mayfly, "自检：切到冒险，原版先把飞行关掉");
        ECFlight.update(player);
        helper.assertTrue(player.getAbilities().mayfly && ECFlight.isApplied(player)
                && Math.abs(player.getAbilities().getFlyingSpeed() - 0.1F) < EPS, "冒险里下一刻重新给上");
        player.getAbilities().mayfly = false;
        ECFlight.update(player);
        helper.assertFalse(player.getAbilities().mayfly, "模式没变：别的插件中途关掉飞行不抢回来");
        player.setGameMode(GameType.SURVIVAL);
        ECFlight.update(player);
        helper.assertTrue(player.getAbilities().mayfly && ECFlight.isApplied(player), "切回生存同样重新给上");
        ECFlight.resync(player);
        helper.assertTrue(player.getAbilities().mayfly && !player.getAbilities().flying
                && Math.abs(player.getAbilities().getFlyingSpeed() - 0.1F) < EPS, "换维度补发能力包，不改服务端能力");
        ECFlight.revoke(player, SOURCE_A);
        helper.assertFalse(player.getAbilities().mayfly || player.getPersistentData().contains(ECFlight.TAG), "撤掉以后照常收回、记账清掉");
        helper.succeed();
    }

    // ================================================================ 工具

    private static Player mock(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 1D, 1.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static ItemStack[] wear(Player player, int helmet, int chest, int legs, int boots) {
        int[] mru = {helmet, chest, legs, boots};
        ItemStack[] stacks = new ItemStack[4];
        for (ArmorItem.Type type : TYPES) {
            ItemStack stack = new ItemStack(ECVoidArmor.all().get(type.ordinal()).get());
            int amount = mru[type.ordinal()];
            stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(amount));
            player.setItemSlot(type.getSlot(), stack);
            stacks[type.ordinal()] = stack;
        }
        return stacks;
    }

    /** 头 / 胸穿虚空、腿 / 靴穿宅男，各存 mru；按头 / 胸 / 腿 / 靴返回 */
    private static ItemStack[] wearMixed(Player player, int mru) {
        ItemStack[] stacks = {new ItemStack(ECVoidArmor.VOID_HELMET.get()), new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()),
                new ItemStack(ECComputer.COMPUTER_LEGGINGS.get()), new ItemStack(ECComputer.COMPUTER_BOOTS.get())};
        for (ArmorItem.Type type : TYPES) {
            ItemStack stack = stacks[type.ordinal()];
            stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
            player.setItemSlot(type.getSlot(), stack);
        }
        return stacks;
    }

    /** 背包第 10 格放一个存了 mru 的储存物品（红魂物质），替掉原来那格 */
    private static ItemStack storage(Player player, int mru) {
        ItemStack storage = MRUStaffItem.full(ECItems.RED_SOUL_MATTER.get());
        storage.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        player.getInventory().setItem(9, storage);
        return storage;
    }

    private static void assertMru(GameTestHelper helper, ItemStack[] stacks, int[] expected, String what) {
        for (ArmorItem.Type type : TYPES) {
            int actual = mru(stacks[type.ordinal()]);
            helper.assertTrue(actual == expected[type.ordinal()], what + "：" + type + " 应剩 " + expected[type.ordinal()] + "，实际 " + actual);
        }
    }

    private static int mru(ItemStack stack) {
        return MRUStaffItem.storedMRU(stack);
    }
}
