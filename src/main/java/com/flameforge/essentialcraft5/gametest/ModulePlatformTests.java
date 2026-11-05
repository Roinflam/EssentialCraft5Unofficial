package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.client.guide.GuideConditions;
import com.flameforge.essentialcraft5.config.GuideConfigSnapshot;
import com.flameforge.essentialcraft5.item.ComputerSealField;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.item.module.ModuleConfig;
import com.flameforge.essentialcraft5.item.module.ModuleCooldowns;
import com.flameforge.essentialcraft5.item.module.ModuleEvents;
import com.flameforge.essentialcraft5.item.module.ModuleTerminal;
import com.flameforge.essentialcraft5.item.module.ModuleTicker;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.network.C2SComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.ModulePanel;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.ModuleRow;
import com.flameforge.essentialcraft5.recipe.ArmorModuleRecipe;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECModules;
import com.flameforge.essentialcraft5.registry.ECSurvivalModules;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.Advancement;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 模块平台（第十批 10c-F，batch10.md §4.3 第 12 条）：装（配方）、拆（不复制）、开关与限频、要穿齐四件、{@code disabled}、快照作废、
 * 同种只算一块、超出格数、按秒耗电（先算后扣、缺电不生效、补电恢复）、终端包核对与模块段的编解码、冷却显示、手册打勾、协议号；
 * 审查后补的：真模块物品走配方、终端包核对模块注册名（名单过期不拆错）、等待扣电、停用的装不上、宅男盔甲不可修理。
 * <p>
 * 六块真模块由别的注册类登记，这里在 {@code @BeforeBatch} 用 {@link ArmorModules#registerForTest} 把几样原版物品临时当成模块（运行中造不了新物品），
 * {@code @AfterBatch} 撤掉；配置一律用 {@link ModuleConfig#setTestOverrides}，在同一段同步代码里设、测、置回。
 * 测试玩家不进世界、不走真 tick：换装后快照靠「盔甲槽的物品对象换了」自己重建，电费直接调 {@link ModuleTicker#pay}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ModulePlatformTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "module_platform";

    /** 头盔模块（不耗电） */
    private static final ModuleType T_HEAD = type(Items.IRON_NUGGET, 0, 0, true, ArmorItem.Type.HELMET);
    /** 第二种头盔模块 */
    private static final ModuleType T_HEAD2 = type(Items.FLINT, 0, 0, true, ArmorItem.Type.HELMET);
    /** 头盔或胸甲 */
    private static final ModuleType T_HEAD3 = type(Items.GOLD_NUGGET, 0, 0, true, ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE);
    /** 胸甲，每秒 300 */
    private static final ModuleType T_CHEST = type(Items.QUARTZ, 300, 0, true, ArmorItem.Type.CHESTPLATE);
    /** 护腿或靴子，每秒 200 */
    private static final ModuleType T_LEGS = type(Items.AMETHYST_SHARD, 200, 0, true, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS);
    /** 不能开关的头盔模块 */
    private static final ModuleType T_FIXED = type(Items.CLAY_BALL, 0, 0, false, ArmorItem.Type.HELMET);
    /** 每次触发 50 000 的头盔模块 */
    private static final ModuleType T_USE = type(Items.PRISMARINE_SHARD, 0, 50_000, true, ArmorItem.Type.HELMET);

    private static final Map<ModuleType, Item> TEST_TYPES = Map.of(T_HEAD, Items.IRON_NUGGET, T_HEAD2, Items.FLINT, T_HEAD3, Items.GOLD_NUGGET,
            T_CHEST, Items.QUARTZ, T_LEGS, Items.AMETHYST_SHARD, T_FIXED, Items.CLAY_BALL, T_USE, Items.PRISMARINE_SHARD);

    private ModulePlatformTests() {
    }

    private static ModuleType type(Item item, int perSecond, int perUse, boolean toggleable, ArmorItem.Type first, ArmorItem.Type... rest) {
        return ModuleType.builder(ForgeRegistries.ITEMS.getKey(item)).parts(first, rest).toggleable(toggleable)
                .mruPerSecond(() -> perSecond).mruPerUse(() -> perUse).build();
    }

    @BeforeBatch(batch = BATCH)
    public static void modulePlatformBefore(ServerLevel level) {
        TEST_TYPES.forEach(ArmorModules::registerForTest);
    }

    @AfterBatch(batch = BATCH)
    public static void modulePlatformAfter(ServerLevel level) {
        TEST_TYPES.keySet().forEach(ArmorModules::unregisterForTest);
        ModuleConfig.setTestOverrides(null);
    }

    // ------------------------------------------------------------------ 工具

    private static ItemStack piece(ArmorItem.Type type) {
        return new ItemStack(ECComputer.armor().get(type.ordinal()).get());
    }

    /** 一件宅男盔甲装上这些模块（按顺序） */
    private static ItemStack piece(ArmorItem.Type type, ModuleType... modules) {
        ItemStack stack = piece(type);
        for (ModuleType module : modules) {
            stack = ArmorModules.install(stack, module);
            if (stack.isEmpty()) {
                throw new IllegalStateException("装不上 " + module + " 到 " + type);
            }
        }
        return stack;
    }

    /** 穿齐四件的真玩家（胸甲自存 chestMru），头盔 / 胸甲 / 护腿 / 靴子上分别装 heads / chests / legs / boots */
    private static ServerPlayer suited(GameTestHelper helper, String name, int chestMru) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            player.setItemSlot(type.getSlot(), piece(type));
        }
        setMru(player.getItemBySlot(EquipmentSlot.CHEST), chestMru);
        return player;
    }

    private static void setMru(ItemStack stack, int mru) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
    }

    private static int mru(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    private static void wear(ServerPlayer player, ArmorItem.Type type, ModuleType... modules) {
        ItemStack old = player.getItemBySlot(type.getSlot());
        ItemStack stack = piece(type, modules);
        if (type == ArmorItem.Type.CHESTPLATE) {
            setMru(stack, mru(old));
        }
        player.setItemSlot(type.getSlot(), stack);
    }

    private static void cleanup(ServerPlayer player) {
        ComputerSealField.forgetForTest(player);
        ModuleTerminal.forgetForTest(player);
        ModuleTicker.forgetForTest(player);
        ModuleCache.invalidate(player);
        player.getAdvancements().stopListening();
    }

    /** 3 × 3 工作台里依次放这些物品（空的写 EMPTY） */
    private static CraftingContainer grid(ItemStack... stacks) {
        CraftingContainer container = new TransientCraftingContainer(new DummyMenu(), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            container.setItem(i, stacks[i]);
        }
        return container;
    }

    private static ArmorModuleRecipe recipe() {
        return new ArmorModuleRecipe(EssentialCraft5.id("terminal/armor_module_install"), CraftingBookCategory.EQUIPMENT);
    }

    private static boolean installs(GameTestHelper helper, CraftingContainer container) {
        ArmorModuleRecipe recipe = recipe();
        Level level = helper.getLevel();
        boolean matches = recipe.matches(container, level);
        boolean produces = !recipe.assemble(container, level.registryAccess()).isEmpty();
        if (matches != produces) {
            throw new IllegalStateException("matches 和 assemble 对不上：" + matches + " / " + produces);
        }
        return matches;
    }

    private static ModuleCache.Status statusAt(ServerPlayer player, ArmorItem.Type part, int index) {
        for (ModuleCache.Slot slot : ModuleCache.get(player).slots()) {
            if (slot.part() == part && slot.index() == index) {
                return slot.status();
            }
        }
        return null;
    }

    private static ModuleRow rowAt(ModulePanel panel, ArmorItem.Type part, int index) {
        for (ModuleRow row : panel.rows()) {
            if (row.part() == part.ordinal() && row.slot() == index) {
                return row;
            }
        }
        return null;
    }

    private static ModuleConfig.Values config(Set<ResourceLocation> disabled, int helmetSlots, boolean fullSet) {
        ModuleConfig.Values d = ModuleConfig.DEFAULTS;
        return new ModuleConfig.Values(disabled, helmetSlots, d.slotsChest(), d.slotsLegs(), d.slotsBoots(), fullSet);
    }

    // ------------------------------------------------------------------ 装

    /** 正常装上：产物是那件的拷贝（电量、名字、附魔都在）、末尾多一格开着的模块；原来那件不动 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleInstallKeepsNbt(GameTestHelper helper) {
        ItemStack helmet = piece(ArmorItem.Type.HELMET);
        setMru(helmet, 12_345);
        helmet.setHoverName(Component.literal("Mine"));
        CompoundTag before = helmet.getOrCreateTag().copy();
        CraftingContainer container = grid(ItemStack.EMPTY, helmet, ItemStack.EMPTY, new ItemStack(Items.IRON_NUGGET, 5));
        helper.assertTrue(installs(helper, container), "宅男头盔 + 头盔模块应能装");
        ItemStack out = recipe().assemble(container, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(ECComputer.COMPUTER_HELMET.get()) && out.getCount() == 1, "产物应是一件宅男头盔，实际 " + out);
        helper.assertTrue(mru(out) == 12_345, "电量应带过去，实际 " + mru(out));
        helper.assertTrue("Mine".equals(out.getHoverName().getString()), "名字应带过去");
        List<ArmorModules.Installed> installed = ArmorModules.installed(out);
        helper.assertTrue(installed.size() == 1 && installed.get(0).id().equals(T_HEAD.id()) && installed.get(0).on(),
                "应多一格开着的模块，实际 " + installed);
        helper.assertTrue(ArmorModules.installed(helmet).isEmpty() && helmet.getTag().equals(before), "原来那件不应被改");
        helper.assertTrue(recipe().isSpecial(), "特殊配方：配方书里不显示");
        helper.succeed();
    }

    /** 槽满不出产物：头盔默认 2 格，第三块不行；靴子 1 格，第二块不行 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleInstallRejectsFull(GameTestHelper helper) {
        ItemStack helmet = piece(ArmorItem.Type.HELMET, T_HEAD, T_HEAD2);
        helper.assertFalse(installs(helper, grid(helmet, new ItemStack(Items.GOLD_NUGGET))), "头盔两格满了，第三块不应装得上");
        helper.assertTrue(ArmorModules.check(helmet, T_HEAD3) == ArmorModules.InstallCheck.FULL, "原因应是槽满");
        ItemStack boots = piece(ArmorItem.Type.BOOTS, T_LEGS);
        helper.assertTrue(ArmorModules.slots(boots) == 1, "靴子默认 1 格");
        ModuleConfig.setTestOverrides(new ModuleConfig.Values(Set.of(), 2, 2, 1, 0, true));
        try {
            helper.assertTrue(ArmorModules.check(piece(ArmorItem.Type.BOOTS), T_LEGS) == ArmorModules.InstallCheck.FULL,
                    "靴子配成 0 格时一块都装不上");
        } finally {
            ModuleConfig.setTestOverrides(null);
        }
        helper.succeed();
    }

    /** 部位不对不出产物 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleInstallRejectsWrongPart(GameTestHelper helper) {
        helper.assertFalse(installs(helper, grid(piece(ArmorItem.Type.CHESTPLATE), new ItemStack(Items.IRON_NUGGET))), "头盔模块不应装到胸甲上");
        helper.assertFalse(installs(helper, grid(piece(ArmorItem.Type.HELMET), new ItemStack(Items.QUARTZ))), "胸甲模块不应装到头盔上");
        helper.assertTrue(installs(helper, grid(piece(ArmorItem.Type.CHESTPLATE), new ItemStack(Items.GOLD_NUGGET))), "头盔或胸甲的模块能装到胸甲上");
        helper.assertTrue(ArmorModules.check(piece(ArmorItem.Type.BOOTS), T_HEAD) == ArmorModules.InstallCheck.WRONG_PART, "原因应是部位不对");
        helper.succeed();
    }

    /** 同一件已有同种不出产物 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleInstallRejectsDuplicate(GameTestHelper helper) {
        ItemStack helmet = piece(ArmorItem.Type.HELMET, T_HEAD);
        helper.assertFalse(installs(helper, grid(helmet, new ItemStack(Items.IRON_NUGGET))), "同一件不应装两块同种模块");
        helper.assertTrue(ArmorModules.check(helmet, T_HEAD) == ArmorModules.InstallCheck.DUPLICATE, "原因应是重复");
        helper.assertTrue(installs(helper, grid(helmet, new ItemStack(Items.FLINT))), "换一种还能装（头盔两格）");
        helper.succeed();
    }

    /** 不是能装模块的盔甲、两块模块、多一样东西、只有模块，都不出产物 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleInstallRejectsNonModular(GameTestHelper helper) {
        ItemStack nugget = new ItemStack(Items.IRON_NUGGET);
        helper.assertFalse(installs(helper, grid(new ItemStack(Items.DIAMOND_HELMET), nugget)), "原版头盔不能装模块");
        helper.assertFalse(installs(helper, grid(new ItemStack(ECVoidArmor.VOID_HELMET.get()), nugget)), "虚空套装不开模块槽");
        helper.assertFalse(installs(helper, grid(piece(ArmorItem.Type.HELMET), nugget, new ItemStack(Items.FLINT))), "一次只能装一块");
        helper.assertFalse(installs(helper, grid(piece(ArmorItem.Type.HELMET), nugget, new ItemStack(Items.STICK))), "多放别的东西不行");
        helper.assertFalse(installs(helper, grid(piece(ArmorItem.Type.HELMET), piece(ArmorItem.Type.HELMET), nugget)), "两件盔甲不行");
        helper.assertFalse(installs(helper, grid(nugget)), "只有模块不行");
        helper.assertFalse(installs(helper, grid(new ItemStack(ECModules.BLANK_MODULE.get()), piece(ArmorItem.Type.HELMET))), "空白模块不是模块");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 拆

    /** 拆下：NBT 删掉、模块回到背包一块；同一格再拆是空格子，不会再给 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleRemoveGivesBackOnce(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-rm", 0);
        try {
            wear(player, ArmorItem.Type.HELMET, T_HEAD, T_HEAD2);
            ModuleTerminal.Result result = ModuleTerminal.remove(player, 0, 0);
            helper.assertTrue(result == ModuleTerminal.Result.OK, "拆下应成功，实际 " + result);
            List<ArmorModules.Installed> left = ArmorModules.installed(player.getItemBySlot(EquipmentSlot.HEAD));
            helper.assertTrue(left.size() == 1 && left.get(0).id().equals(T_HEAD2.id()) && left.get(0).slot() == 0,
                    "剩下的往前挪成第 0 格，实际 " + left);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_NUGGET) == 1, "背包里应正好多一块模块");
            ModuleTerminal.forgetForTest(player);
            helper.assertTrue(ModuleTerminal.remove(player, 0, 1) == ModuleTerminal.Result.EMPTY_SLOT, "第 1 格已经没有了");
            helper.assertTrue(player.getInventory().countItem(Items.IRON_NUGGET) == 1, "拆空格子不应再给");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 背包满了：模块掉在脚下（只一个），NBT 照样删掉 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleRemoveDropsWhenFull(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-full", 0);
        AABB box = new AABB(player.blockPosition()).inflate(3D);
        try {
            wear(player, ArmorItem.Type.HELMET, T_HEAD);
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
            }
            player.getInventory().offhand.set(0, new ItemStack(Items.STONE, 64));
            helper.assertTrue(ModuleTerminal.remove(player, 0, 0) == ModuleTerminal.Result.OK, "拆下应成功");
            helper.assertTrue(ArmorModules.count(player.getItemBySlot(EquipmentSlot.HEAD)) == 0, "NBT 应删掉");
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.IRON_NUGGET));
            int dropped = drops.stream().mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(dropped == 1, "背包满时应掉一块在脚下，实际 " + dropped);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_NUGGET) == 0, "背包里不应有");
        } finally {
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.IRON_NUGGET)).forEach(ItemEntity::discard);
            cleanup(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 开关

    /** 终端包走总入口开关一格：NBT 翻转、快照当场作废、马上回一份带模块段的终端数据；不能开关的模块拒绝 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleToggleThroughTerminal(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-tog", 0);
        List<S2CComputerTerminalPacket> sent = ComputerSealField.captureTerminalForTest(player.getUUID());
        try {
            wear(player, ArmorItem.Type.HELMET, T_HEAD, T_FIXED);
            helper.assertTrue(ModuleCache.active(player, T_HEAD), "装上就该生效");
            ComputerSealField.handleTerminalPacket(player, new C2SComputerTerminalPacket(C2SComputerTerminalPacket.MODULE_TOGGLE, 0, 0, T_HEAD.id().toString()));
            helper.assertFalse(ArmorModules.installed(player.getItemBySlot(EquipmentSlot.HEAD)).get(0).on(), "第 0 格应关掉");
            helper.assertFalse(ModuleCache.active(player, T_HEAD), "关掉以后快照应当场作废、不再生效");
            helper.assertTrue(statusAt(player, ArmorItem.Type.HELMET, 0) == ModuleCache.Status.OFF, "状态应是关着");
            helper.assertTrue(sent.size() == 1 && sent.get(0).detail(), "做成以后应马上回一份带名单的终端数据，实际 " + sent.size());
            ModuleRow row = rowAt(sent.get(0).modules(), ArmorItem.Type.HELMET, 0);
            helper.assertTrue(row != null && !row.on() && ModuleCache.Status.of(row.status()) == ModuleCache.Status.OFF, "回的数据里这一格应是关着，实际 " + row);
            ModuleTerminal.forgetForTest(player);
            helper.assertTrue(ModuleTerminal.toggle(player, 0, 1) == ModuleTerminal.Result.NOT_TOGGLEABLE, "不能开关的模块应拒绝");
            helper.assertTrue(ModuleCache.active(player, T_FIXED), "不能开关的模块一直开着");
            helper.assertTrue(ModuleTerminal.toggle(player, 0, 0) == ModuleTerminal.Result.OK && ModuleCache.active(player, T_HEAD), "再开回来生效");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 同一人 5 刻内只认一次：第 1 刻做成，第 5 刻（隔 4 刻）拒绝，第 6 刻（隔 5 刻）做成 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 40)
    public static void moduleToggleRateLimited(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-rate", 0);
        wear(player, ArmorItem.Type.HELMET, T_HEAD);
        helper.runAtTickTime(1, () -> helper.assertTrue(ModuleTerminal.toggle(player, 0, 0) == ModuleTerminal.Result.OK, "第一次应做成"));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(ModuleTerminal.toggle(player, 0, 0) == ModuleTerminal.Result.TOO_FAST, "隔 4 刻应拒绝");
            helper.assertTrue(ModuleTerminal.remove(player, 0, 0) == ModuleTerminal.Result.TOO_FAST, "拆也算在同一个间隔里");
            helper.assertFalse(ArmorModules.installed(player.getItemBySlot(EquipmentSlot.HEAD)).get(0).on(), "被拒的那次不应改状态");
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(ModuleTerminal.toggle(player, 0, 0) == ModuleTerminal.Result.OK, "隔 5 刻应做成");
            helper.assertTrue(ArmorModules.installed(player.getItemBySlot(EquipmentSlot.HEAD)).get(0).on(), "应开回来");
            cleanup(player);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ 生不生效

    /** 没穿齐四件不生效（终端标「没穿齐」）；require_full_set 关掉时穿着的那件照样生效 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleNeedsFullSet(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-set", 0);
        try {
            wear(player, ArmorItem.Type.HELMET, T_HEAD);
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            helper.assertFalse(ModuleCache.active(player, T_HEAD), "少一件不应生效");
            helper.assertTrue(statusAt(player, ArmorItem.Type.HELMET, 0) == ModuleCache.Status.INCOMPLETE, "状态应是没穿齐");
            ModulePanel panel = ModuleTerminal.panel(player);
            helper.assertTrue(!panel.fullSet() && panel.slots(3) == -1 && panel.slots(0) == 2, "模块段：没穿齐、靴子 -1、头盔 2 格");
            ModuleConfig.setTestOverrides(config(Set.of(), 2, false));
            try {
                helper.assertTrue(ModuleCache.active(player, T_HEAD), "不要求穿齐时穿着的头盔上的模块应生效");
            } finally {
                ModuleConfig.setTestOverrides(null);
            }
            helper.assertFalse(ModuleCache.active(player, T_HEAD), "置回以后又不生效");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** general.computer_modules.disabled 里的模块不生效、不耗电，终端标「停用」 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleDisabledByConfig(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-dis", 10_000);
        try {
            wear(player, ArmorItem.Type.HELMET, T_HEAD);
            wear(player, ArmorItem.Type.LEGGINGS, T_LEGS);
            ModuleConfig.setTestOverrides(config(Set.of(T_HEAD.id(), T_LEGS.id()), 2, true));
            try {
                helper.assertFalse(ModuleCache.active(player, T_HEAD), "被关掉的模块不应生效");
                helper.assertTrue(statusAt(player, ArmorItem.Type.HELMET, 0) == ModuleCache.Status.DISABLED, "状态应是停用");
                int paid = ModuleTicker.pay(player);
                helper.assertTrue(paid == 0 && mru(player.getItemBySlot(EquipmentSlot.CHEST)) == 10_000, "被关掉的按秒模块不应扣电，实际扣了 " + paid);
                ModuleRow row = rowAt(ModuleTerminal.panel(player), ArmorItem.Type.HELMET, 0);
                helper.assertTrue(row != null && ModuleCache.Status.of(row.status()) == ModuleCache.Status.DISABLED, "终端里也是停用");
            } finally {
                ModuleConfig.setTestOverrides(null);
            }
            helper.assertTrue(ModuleCache.active(player, T_HEAD), "置回以后生效");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 快照作废：脱下（物品对象换了）马上看得出；同一件就地改了 NBT 的，换维度、装备变化事件会让它作废 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleCacheInvalidates(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-cache", 0);
        try {
            wear(player, ArmorItem.Type.HELMET, T_HEAD);
            helper.assertTrue(ModuleCache.active(player, T_HEAD), "穿着应生效");
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            helper.assertFalse(ModuleCache.active(player, T_HEAD), "脱下以后应马上不生效");
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
            helper.assertTrue(ModuleCache.active(player, T_HEAD), "穿回来又生效");
            // 就地改 NBT（物品对象没换）：快照还是旧的，直到有事件让它作废
            ArmorModules.setOn(helmet, 0, false);
            helper.assertTrue(ModuleCache.active(player, T_HEAD), "就地改 NBT、没人通知时快照还没过期（最多 20 刻）");
            ModuleEvents.onModuleChangeDimension(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
            helper.assertFalse(ModuleCache.active(player, T_HEAD), "换维度以后快照作废、按新的 NBT 算");
            ItemStack before = helmet.copy();
            ArmorModules.setOn(helmet, 0, true);
            ModuleEvents.onModuleEquipmentChange(new LivingEquipmentChangeEvent(player, EquipmentSlot.HEAD, before, helmet));
            helper.assertTrue(ModuleCache.active(player, T_HEAD), "装备变化事件（NBT 变了原版也发）同样作废快照");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 同种模块全套只算一块：护腿、靴子各装一块按秒模块，生效一块、另一块写重复，电费只扣一份 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleSameKindCountsOnce(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-dup", 1_000);
        try {
            wear(player, ArmorItem.Type.LEGGINGS, T_LEGS);
            wear(player, ArmorItem.Type.BOOTS, T_LEGS);
            helper.assertTrue(statusAt(player, ArmorItem.Type.LEGGINGS, 0) == ModuleCache.Status.ACTIVE, "护腿上的先算");
            helper.assertTrue(statusAt(player, ArmorItem.Type.BOOTS, 0) == ModuleCache.Status.DUPLICATE, "靴子上的同种写重复");
            int paid = ModuleTicker.pay(player);
            helper.assertTrue(paid == 200 && mru(player.getItemBySlot(EquipmentSlot.CHEST)) == 800, "只扣一份 200，实际 " + paid);
            helper.assertTrue(ModuleCache.active(player, T_LEGS) && ModuleCache.activeCount(player) == 1, "生效一种");
            // 护腿上那块关掉以后，靴子上的接着算
            ModuleTerminal.toggle(player, 2, 0);
            helper.assertTrue(statusAt(player, ArmorItem.Type.BOOTS, 0) == ModuleCache.Status.ACTIVE, "护腿那块关了，靴子上的生效");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 格数改小：排在格数以外的写「超出格数」、不生效，前面的照常；能拆 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleOverflowWhenSlotsShrink(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-over", 0);
        try {
            wear(player, ArmorItem.Type.HELMET, T_HEAD, T_HEAD2);
            ModuleConfig.setTestOverrides(config(Set.of(), 1, true));
            try {
                helper.assertTrue(ModuleCache.active(player, T_HEAD), "第 0 格照常生效");
                helper.assertFalse(ModuleCache.active(player, T_HEAD2), "第 1 格超出格数、不生效");
                helper.assertTrue(statusAt(player, ArmorItem.Type.HELMET, 1) == ModuleCache.Status.OVERFLOW, "状态应是超出格数");
                helper.assertTrue(ArmorModules.check(player.getItemBySlot(EquipmentSlot.HEAD), T_HEAD3) == ArmorModules.InstallCheck.FULL, "满了不能再装");
                helper.assertTrue(ModuleTerminal.remove(player, 0, 1) == ModuleTerminal.Result.OK, "超出的那块能拆");
                helper.assertTrue(player.getInventory().countItem(Items.FLINT) == 1, "拆下来回到背包");
            } finally {
                ModuleConfig.setTestOverrides(null);
            }
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 电

    /** 按秒耗电：付不起不生效（终端标「缺电」）、一分不扣；补电以后下一次记账扣 200、恢复生效 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleNoPowerThenRecharged(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-pow", 150);
        try {
            wear(player, ArmorItem.Type.LEGGINGS, T_LEGS);
            helper.assertTrue(ModuleCache.eligible(player, T_LEGS), "格子状态是生效");
            helper.assertFalse(ModuleCache.active(player, T_LEGS), "还没记过账，按秒模块不生效");
            helper.assertTrue(ModuleTicker.pay(player) == 0, "付不起时一分不扣");
            helper.assertTrue(mru(player.getItemBySlot(EquipmentSlot.CHEST)) == 150, "胸甲电量不变");
            helper.assertFalse(ModuleCache.active(player, T_LEGS), "缺电不生效");
            ModuleRow row = rowAt(ModuleTerminal.panel(player), ArmorItem.Type.LEGGINGS, 0);
            helper.assertTrue(row != null && ModuleCache.Status.of(row.status()) == ModuleCache.Status.NO_POWER, "终端应标缺电，实际 " + row);
            setMru(player.getItemBySlot(EquipmentSlot.CHEST), 1_000);
            helper.assertTrue(ModuleTicker.pay(player) == 200, "补电以后扣 200");
            helper.assertTrue(ModuleCache.active(player, T_LEGS) && mru(player.getItemBySlot(EquipmentSlot.CHEST)) == 800, "恢复生效、剩 800");
            ModulePanel panel = ModuleTerminal.panel(player);
            helper.assertTrue(panel.mruPerSecond() == 200 && ModuleCache.Status.of(rowAt(panel, ArmorItem.Type.LEGGINGS, 0).status()) == ModuleCache.Status.ACTIVE,
                    "终端写每秒 200、生效中");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 先算后扣、一块块记：胸甲那块（300）付不起就跳过，护腿那块（200）付得起照扣；创造模式不扣也生效 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void modulePaysWhatItCan(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-greedy", 250);
        try {
            wear(player, ArmorItem.Type.CHESTPLATE, T_CHEST);
            wear(player, ArmorItem.Type.LEGGINGS, T_LEGS);
            int paid = ModuleTicker.pay(player);
            helper.assertTrue(paid == 200, "应只扣护腿那块的 200，实际 " + paid);
            helper.assertTrue(mru(player.getItemBySlot(EquipmentSlot.CHEST)) == 50, "剩 50");
            helper.assertFalse(ModuleCache.active(player, T_CHEST), "胸甲那块缺电");
            helper.assertTrue(ModuleCache.active(player, T_LEGS), "护腿那块生效");
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(ModuleTicker.pay(player) == 500 && mru(player.getItemBySlot(EquipmentSlot.CHEST)) == 50, "创造模式记 500、不扣电");
            helper.assertTrue(ModuleCache.active(player, T_CHEST) && ModuleCache.active(player, T_LEGS), "创造模式两块都生效");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 终端

    /** 终端包核对：部位越界、那件没穿、格子是空的、旁观者都拒，什么都不改 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleTerminalRejects(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-rej", 0);
        try {
            wear(player, ArmorItem.Type.HELMET, T_HEAD);
            helper.assertTrue(ModuleTerminal.toggle(player, 7, 0) == ModuleTerminal.Result.BAD_PART, "部位越界应拒绝");
            helper.assertTrue(ModuleTerminal.toggle(player, -1, 0) == ModuleTerminal.Result.BAD_PART, "部位负数应拒绝");
            helper.assertTrue(ModuleTerminal.remove(player, 0, 3) == ModuleTerminal.Result.EMPTY_SLOT, "空格子应拒绝");
            helper.assertTrue(ModuleTerminal.remove(player, 0, -2) == ModuleTerminal.Result.EMPTY_SLOT, "负的格子号应拒绝");
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            helper.assertTrue(ModuleTerminal.toggle(player, 1, 0) == ModuleTerminal.Result.NOT_WORN, "那件不是宅男盔甲应拒绝");
            // 模块在背包里的头盔上：没穿就拆不了
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.getInventory().setItem(0, helmet);
            helper.assertTrue(ModuleTerminal.remove(player, 0, 0) == ModuleTerminal.Result.NOT_WORN, "放在背包里的那件拆不了");
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
            player.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(ModuleTerminal.remove(player, 0, 0) == ModuleTerminal.Result.SPECTATOR, "旁观者应拒绝");
            helper.assertTrue(ArmorModules.count(helmet) == 1 && player.getInventory().countItem(Items.IRON_NUGGET) == 0, "被拒的都不应改东西");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 带名单的终端包里的模块段编解码一致；只带状态的包没有模块段；行数有上限 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleTerminalPacketRoundTrip(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-pkt", 0);
        try {
            wear(player, ArmorItem.Type.HELMET, T_HEAD, T_USE);
            wear(player, ArmorItem.Type.LEGGINGS, T_LEGS);
            ModuleCooldowns.start(player, T_USE, 600);
            S2CComputerTerminalPacket packet = ComputerSealField.packet(player, true);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            S2CComputerTerminalPacket.encode(packet, buf);
            S2CComputerTerminalPacket back = S2CComputerTerminalPacket.decode(buf);
            helper.assertTrue(buf.readableBytes() == 0, "应正好读完，剩 " + buf.readableBytes());
            ModulePanel a = packet.modules();
            ModulePanel b = back.modules();
            helper.assertTrue(a.rows().size() == 3 && a.rows().equals(b.rows()), "三格模块应原样读回，实际 " + a.rows() + " / " + b.rows());
            helper.assertTrue(b.fullSet() == a.fullSet() && b.requireFullSet() == a.requireFullSet() && b.mruPerSecond() == a.mruPerSecond(), "标志、电费应一致");
            for (int part = 0; part < 4; part++) {
                helper.assertTrue(a.slots(part) == b.slots(part), "第 " + part + " 件的格数应一致");
            }
            ModuleRow cooling = rowAt(b, ArmorItem.Type.HELMET, 1);
            helper.assertTrue(cooling != null && cooling.cooldown() > 590, "冷却剩余刻应带过去，实际 " + cooling);
            S2CComputerTerminalPacket state = ComputerSealField.packet(player, false);
            FriendlyByteBuf buf2 = new FriendlyByteBuf(Unpooled.buffer());
            S2CComputerTerminalPacket.encode(state, buf2);
            helper.assertTrue(S2CComputerTerminalPacket.decode(buf2).modules().rows().isEmpty() && buf2.readableBytes() == 0, "只带状态的包没有模块段");
            List<ModuleRow> many = new java.util.ArrayList<>();
            for (int i = 0; i < 40; i++) {
                many.add(new ModuleRow((byte) 0, (byte) i, T_HEAD.id(), (byte) 0, true, 0));
            }
            helper.assertTrue(new ModulePanel(true, true, new byte[]{4, 4, 4, 4}, 0, many).rows().size() == S2CComputerTerminalPacket.MAX_MODULES,
                    "模块段最多 " + S2CComputerTerminalPacket.MAX_MODULES + " 格");
        } finally {
            ModuleCooldowns.clear(player, T_USE);
            cleanup(player);
        }
        helper.succeed();
    }

    /** 冷却存在玩家持久数据里（终端写冷却中）；一次性花费付不起写缺电（模块照样算生效），付得起写生效中 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleCooldownAndUseCost(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-cd", 0);
        try {
            wear(player, ArmorItem.Type.HELMET, T_USE);
            ModuleCooldowns.start(player, T_USE, 6_000);
            helper.assertTrue(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ModuleCooldowns.TAG).contains(T_USE.id().toString()),
                    "冷却应存在 PlayerPersisted 下（退出重进、死亡复制都在）");
            helper.assertTrue(ModuleCooldowns.remaining(player, T_USE) == 6_000 && !ModuleCooldowns.ready(player, T_USE), "还剩 6000 刻");
            ModuleRow row = rowAt(ModuleTerminal.panel(player), ArmorItem.Type.HELMET, 0);
            helper.assertTrue(row != null && ModuleCache.Status.of(row.status()) == ModuleCache.Status.COOLDOWN && row.cooldown() == 6_000,
                    "终端应写冷却中，实际 " + row);
            helper.assertTrue(ModuleCache.active(player, T_USE), "冷却中的模块照样算生效（触发前由模块自己查冷却）");
            ModuleCooldowns.clear(player, T_USE);
            helper.assertTrue(ModuleCooldowns.ready(player, T_USE), "清掉以后好了");
            row = rowAt(ModuleTerminal.panel(player), ArmorItem.Type.HELMET, 0);
            helper.assertTrue(row != null && ModuleCache.Status.of(row.status()) == ModuleCache.Status.NO_POWER, "身上不够一次的 50 000，终端写缺电");
            setMru(player.getItemBySlot(EquipmentSlot.CHEST), 60_000);
            row = rowAt(ModuleTerminal.panel(player), ArmorItem.Type.HELMET, 0);
            helper.assertTrue(row != null && ModuleCache.Status.of(row.status()) == ModuleCache.Status.ACTIVE, "够一次以后写生效中");
        } finally {
            ModuleCooldowns.clear(player, T_USE);
            cleanup(player);
        }
        helper.succeed();
    }

    /** 手册「装上两块模块」：一块生效时不打勾，两种生效以后每秒的处理里打勾 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleGuideNeedsTwo(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-guide", 0);
        try {
            Advancement advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(ECModules.GUIDE_MODULES);
            helper.assertTrue(advancement != null, "应有进度 " + ECModules.GUIDE_MODULES);
            helper.assertTrue(helper.getLevel().getServer().getAdvancements().getAdvancement(ECModules.GUIDE_BLANK_MODULE) != null,
                    "应有进度 " + ECModules.GUIDE_BLANK_MODULE);
            wear(player, ArmorItem.Type.HELMET, T_HEAD);
            ModuleTicker.tick(player);
            helper.assertFalse(player.getAdvancements().getOrStartProgress(advancement).isDone(), "一块生效时不应打勾");
            wear(player, ArmorItem.Type.HELMET, T_HEAD, T_HEAD2);
            ModuleTerminal.toggle(player, 0, 1);
            ModuleTicker.tick(player);
            helper.assertFalse(player.getAdvancements().getOrStartProgress(advancement).isDone(), "第二块关着时不应打勾");
            ModuleTerminal.forgetForTest(player);
            ModuleTerminal.toggle(player, 0, 1);
            ModuleTicker.tick(player);
            helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(), "两种生效应打勾");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 手册条件 {@code module:<注册名>}：被 general.computer_modules.disabled 关掉时不成立、取反成立；id 写坏了两头都不成立；
     * 配置快照里有这个键（客户端读同步过来的服务端配置）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleGuideCondition(GameTestHelper helper) {
        helper.assertTrue(GuideConfigSnapshot.capture().containsKey("general.computer_modules.disabled"), "配置快照里应有 general.computer_modules.disabled");
        List<String> warnings = new java.util.ArrayList<>();
        GuideConditions conditions = new GuideConditions(Map.of("general.computer_modules.disabled", "[\"essentialcraft5:rewind_module\"]"),
                false, (key, message) -> warnings.add(message));
        helper.assertFalse(conditions.test("module:essentialcraft5:rewind_module"), "关掉的模块：条件不成立");
        helper.assertTrue(conditions.test("!module:essentialcraft5:rewind_module"), "关掉的模块：取反成立");
        helper.assertTrue(conditions.test("module:essentialcraft5:shred_module"), "没关的模块：条件成立");
        helper.assertTrue(conditions.test("module:essentialcraft5:rewind_module||module:essentialcraft5:shred_module"), "|| 任一成立");
        helper.assertFalse(conditions.test("module:Bad Id") || conditions.test("!module:Bad Id"), "id 写坏了：取不取反都不成立");
        helper.assertTrue(warnings.size() >= 1, "写坏的 id 应报警告");
        GuideConditions empty = new GuideConditions(Map.of("general.computer_modules.disabled", "[]"), false, (key, message) -> { });
        helper.assertTrue(empty.test("module:essentialcraft5:rewind_module"), "没关任何模块时都成立");
        helper.assertTrue(conditions.test("bauble:essentialcraft5:rewind_module"), "bauble: 看的是饰品那份配置，不受 module: 的影响");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 审查后补的

    /** 真的模块物品（不是测试登记的原版物品）走装模块配方：回溯模块 + 宅男头盔装得上；矩阵分流只认胸甲 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleRealItemInstalls(GameTestHelper helper) {
        ItemStack rewind = new ItemStack(ECSurvivalModules.REWIND_MODULE.get());
        helper.assertTrue(ArmorModules.moduleOf(rewind) == ECSurvivalModules.REWIND, "回溯模块物品应认得出是回溯");
        CraftingContainer container = grid(piece(ArmorItem.Type.HELMET), ItemStack.EMPTY, rewind);
        helper.assertTrue(installs(helper, container), "宅男头盔 + 回溯模块应能装");
        ItemStack out = recipe().assemble(container, helper.getLevel().registryAccess());
        List<ArmorModules.Installed> installed = ArmorModules.installed(out);
        helper.assertTrue(installed.size() == 1 && installed.get(0).id().equals(ECSurvivalModules.REWIND.id()), "产物上应装着回溯模块，实际 " + installed);
        ItemStack shunt = new ItemStack(ECSurvivalModules.MATRIX_SHUNT_MODULE.get());
        helper.assertFalse(installs(helper, grid(piece(ArmorItem.Type.HELMET), shunt)), "矩阵分流不能装在头盔上");
        helper.assertTrue(installs(helper, grid(piece(ArmorItem.Type.CHESTPLATE), shunt)), "矩阵分流能装在胸甲上");
        helper.succeed();
    }

    /**
     * 终端包带着界面上那一格的模块注册名：拆掉第 0 格的 A 以后，B 挪到第 0 格；再按旧名单对第 0 格发一次「拆 A」——判 STALE，B 还在、什么都不给、
     * 回一份新名单（限频），也不记操作间隔（对得上的马上能做）。编解码：模块的动作带注册名，封禁场的动作只有三个字节
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleTerminalChecksModuleId(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-stale", 0);
        List<S2CComputerTerminalPacket> sent = ComputerSealField.captureTerminalForTest(player.getUUID());
        try {
            C2SComputerTerminalPacket removeA = new C2SComputerTerminalPacket(C2SComputerTerminalPacket.MODULE_REMOVE, 0, 0, T_HEAD.id().toString());
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            C2SComputerTerminalPacket.encode(removeA, buf);
            C2SComputerTerminalPacket back = C2SComputerTerminalPacket.decode(buf);
            helper.assertTrue(back.equals(removeA) && buf.readableBytes() == 0, "模块的动作带注册名原样读回，实际 " + back);
            FriendlyByteBuf seal = new FriendlyByteBuf(Unpooled.buffer());
            C2SComputerTerminalPacket.encode(new C2SComputerTerminalPacket(C2SComputerTerminalPacket.REFRESH), seal);
            helper.assertTrue(seal.readableBytes() == 3, "封禁场的动作只有三个字节，实际 " + seal.readableBytes());

            wear(player, ArmorItem.Type.HELMET, T_HEAD, T_HEAD2);
            ComputerSealField.handleTerminalPacket(player, removeA);
            List<ArmorModules.Installed> left = ArmorModules.installed(player.getItemBySlot(EquipmentSlot.HEAD));
            helper.assertTrue(left.size() == 1 && left.get(0).id().equals(T_HEAD2.id()), "第一次拆掉 A，B 挪到第 0 格，实际 " + left);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_NUGGET) == 1, "A 回到背包");
            sent.clear();
            // 跳过 5 刻的操作间隔（等同于隔了 6 刻才点第二下）
            ModuleTerminal.forgetForTest(player);
            ModuleTerminal.Result stale = ModuleTerminal.handle(player, removeA);
            helper.assertTrue(stale == ModuleTerminal.Result.STALE, "第 0 格现在是 B，按旧名单拆 A 应判 STALE，实际 " + stale);
            left = ArmorModules.installed(player.getItemBySlot(EquipmentSlot.HEAD));
            helper.assertTrue(left.size() == 1 && left.get(0).id().equals(T_HEAD2.id()), "B 应还在");
            helper.assertTrue(player.getInventory().countItem(Items.FLINT) == 0 && player.getInventory().countItem(Items.IRON_NUGGET) == 1, "什么都不给");
            helper.assertTrue(sent.size() == 1 && sent.get(0).detail(), "应回一份新名单，实际 " + sent.size());
            helper.assertTrue(ModuleTerminal.toggle(player, 0, 0, T_HEAD.id().toString()) == ModuleTerminal.Result.STALE, "开关同样核对注册名");
            helper.assertTrue(sent.size() == 1, "5 刻内不再回名单");
            helper.assertTrue(ModuleTerminal.handle(player, new C2SComputerTerminalPacket(C2SComputerTerminalPacket.MODULE_REMOVE, (byte) 0, (byte) 0))
                    == ModuleTerminal.Result.STALE, "不带注册名的模块动作当名单过期");
            helper.assertTrue(ModuleTerminal.remove(player, 0, 0, T_HEAD2.id().toString()) == ModuleTerminal.Result.OK, "STALE 不记操作间隔：对得上的马上能拆");
            helper.assertTrue(player.getInventory().countItem(Items.FLINT) == 1, "B 回到背包");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 按秒耗电的模块刚装上、还没记过账：终端写「等待扣电」、还不生效；记账扣成以后写「生效中」 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void modulePendingUntilPaid(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "ec5-mod-pend", 1_000);
        try {
            wear(player, ArmorItem.Type.LEGGINGS, T_LEGS);
            ModuleRow row = rowAt(ModuleTerminal.panel(player), ArmorItem.Type.LEGGINGS, 0);
            helper.assertTrue(row != null && ModuleCache.Status.of(row.status()) == ModuleCache.Status.PENDING, "还没记过账应写等待扣电，实际 " + row);
            helper.assertFalse(ModuleCache.active(player, T_LEGS), "还没扣过电，不生效");
            helper.assertTrue(ModuleTicker.pay(player) == 200, "记账扣 200");
            row = rowAt(ModuleTerminal.panel(player), ArmorItem.Type.LEGGINGS, 0);
            helper.assertTrue(row != null && ModuleCache.Status.of(row.status()) == ModuleCache.Status.ACTIVE, "扣成以后写生效中，实际 " + row);
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 被 general.computer_modules.disabled 停用的模块装不上（别的照装）；已经装着的照样能拆、东西还回来 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleDisabledCannotInstall(GameTestHelper helper) {
        ItemStack helmet = piece(ArmorItem.Type.HELMET);
        ServerPlayer player = suited(helper, "ec5-mod-noinst", 0);
        try {
            wear(player, ArmorItem.Type.HELMET, T_HEAD);
            boolean disabledInstalls;
            boolean otherInstalls;
            ArmorModules.InstallCheck check;
            ModuleTerminal.Result removed;
            ModuleConfig.setTestOverrides(config(Set.of(T_HEAD.id()), 2, true));
            try {
                disabledInstalls = installs(helper, grid(helmet, new ItemStack(Items.IRON_NUGGET)));
                check = ArmorModules.check(helmet, T_HEAD);
                otherInstalls = installs(helper, grid(helmet, new ItemStack(Items.FLINT)));
                removed = ModuleTerminal.remove(player, 0, 0);
            } finally {
                ModuleConfig.setTestOverrides(null);
            }
            helper.assertFalse(disabledInstalls, "停用的模块不应装得上");
            helper.assertTrue(check == ArmorModules.InstallCheck.DISABLED, "原因应是停用，实际 " + check);
            helper.assertTrue(otherInstalls, "没停用的照装");
            helper.assertTrue(removed == ModuleTerminal.Result.OK, "已经装着的停用模块照样能拆，实际 " + removed);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_NUGGET) == 1, "拆下来的还回背包");
            helper.assertTrue(installs(helper, grid(helmet, new ItemStack(Items.IRON_NUGGET))), "置回以后又能装");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 宅男盔甲不可修理：两件各装一块模块的宅男头盔放进工作台不匹配任何配方（修理配方会新建一件、吞掉模块）；对照：两件铁头盔照常能修理 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleArmorNotRepairable(GameTestHelper helper) {
        ItemStack a = piece(ArmorItem.Type.HELMET, T_HEAD);
        ItemStack b = piece(ArmorItem.Type.HELMET, T_HEAD2);
        helper.assertFalse(a.isRepairable(), "宅男盔甲不可修理");
        Level level = helper.getLevel();
        Optional<CraftingRecipe> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid(a, b), level);
        helper.assertTrue(found.isEmpty(), "两件带模块的宅男头盔不该匹配任何配方，实际 " + found.map(CraftingRecipe::getId).orElse(null));
        Optional<CraftingRecipe> iron = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                grid(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_HELMET)), level);
        helper.assertTrue(iron.isPresent(), "对照：两件铁头盔照常能修理");
        helper.succeed();
    }

    /** 协议号：10c 给终端数据包加了模块段，升到 8；第十二批加了地脉探测仪的扫描包，升到 9（之后各批再升，精确值由各批自己的测试守着） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void moduleProtocolVersion(GameTestHelper helper) {
        helper.assertTrue(Integer.parseInt(ECNetwork.PROTOCOL) >= 9, "第十二批的协议号应至少为 9，实际 " + ECNetwork.PROTOCOL);
        helper.succeed();
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
