package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.MagnetizingStaffItem;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.item.module.ModuleTicker;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.item.module.UtilityModuleConfig;
import com.flameforge.essentialcraft5.item.module.UtilityModuleEvents;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECModules;
import com.flameforge.essentialcraft5.registry.ECUtilityModules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 四块生活模块（第十二批，batch12.md §4.7.9）：注册与配方 1 条、吸附 5 条、法杖老行为 1 条、攀行 1 条、稳手 1 条、隐匿 1 条、槽位 1 条。
 * <p>
 * 玩家都是穿齐四件宅男盔甲、装着真模块的测试玩家（{@link ProtectionFrameworkTests#realPlayer}，不进世界、不走真 tick，写法同 {@code CombatModuleTests}）；
 * 按秒耗电的先 {@code ModuleTicker.pay} 再测。吸附的范围用测试覆盖值缩到 3 格（默认 6 格的框会伸出模板、推到隔壁测试的掉落物），
 * 近的放在 2.5 格、远的放在 4.5 格，和设计里 6 格范围下「5 格内的推、8 格外的不推」是同一件事；默认值另在注册那条里断言。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class UtilityModuleTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    /** 测试里吸附的水平范围（见类注释） */
    private static final int TEST_RADIUS = 3;
    /** 玩家站在 11×11 模板正中 */
    private static final Vec3 CENTER = new Vec3(5.5D, 1D, 5.5D);
    /** 宅男靴子自带的步高加成（ECComputer.BOOTS_BONUS） */
    private static final double BOOTS_STEP = 0.6D;
    /** 客户端玩家的原版步高（LivingEntity 构造器的 setMaxUpStep(0.6)） */
    private static final double CLIENT_BASE_STEP = 0.6D;

    private UtilityModuleTests() {
    }

    // ================================================================ 工具

    /** 穿齐四件宅男盔甲的真玩家：每块模块装在它能装、还有空槽的第一个部位上，胸甲自存 chestMru；过掉出生无敌 */
    private static ServerPlayer suited(GameTestHelper helper, String name, Vec3 relative, int chestMru, ModuleType... modules) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        ItemStack[] pieces = new ItemStack[4];
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            pieces[type.ordinal()] = new ItemStack(ECComputer.armor().get(type.ordinal()).get());
        }
        for (ModuleType module : modules) {
            boolean done = false;
            for (ArmorItem.Type type : module.parts()) {
                ItemStack next = ArmorModules.install(pieces[type.ordinal()], module);
                if (!next.isEmpty()) {
                    pieces[type.ordinal()] = next;
                    done = true;
                    break;
                }
            }
            if (!done) {
                throw new IllegalStateException("装不上 " + module);
            }
        }
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            player.setItemSlot(type.getSlot(), pieces[type.ordinal()]);
        }
        setChestMru(player, chestMru);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void setChestMru(ServerPlayer player, int mru) {
        player.getItemBySlot(EquipmentSlot.CHEST).getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
    }

    private static int chestMru(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    private static void cleanup(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            UtilityModuleEvents.removeStride(player);
            ModuleTicker.forgetForTest(player);
            ModuleCache.invalidate(player);
            player.getAdvancements().stopListening();
        }
    }

    /** 吸附范围换成 TEST_RADIUS 推一次（电费、节奏照默认） */
    private static MagnetizingStaffItem.Pulse attractWithin(ServerPlayer player) {
        UtilityModuleConfig.Attraction d = UtilityModuleConfig.attraction();
        UtilityModuleConfig.setTestAttraction(new UtilityModuleConfig.Attraction(d.mruPerSecond(), TEST_RADIUS, d.intervalTicks()));
        try {
            return UtilityModuleEvents.attract(player);
        } finally {
            UtilityModuleConfig.setTestAttraction(null);
        }
    }

    /** 速度为 0 的掉落物（相对坐标） */
    private static ItemEntity drop(GameTestHelper helper, double x, double z) {
        return helper.spawnItem(Items.COBBLESTONE, (float) x, 1.2F, (float) z);
    }

    private static boolean moved(Entity entity) {
        return entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6D;
    }

    private static double horizontal(Entity entity, ServerPlayer player) {
        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0E-3D;
    }

    // ================================================================ 注册

    /** 四块都登记了、物品对得上；部位、每秒电费、能开关照设计；配方都吃一块空白模块；配置默认值 */
    @GameTest(template = EMPTY)
    public static void utilityModulesRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<ModuleType, Item> items = Map.of(ECUtilityModules.ATTRACTION, ECUtilityModules.ATTRACTION_MODULE.get(),
                ECUtilityModules.STRIDE, ECUtilityModules.STRIDE_MODULE.get(), ECUtilityModules.STEADY_HAND, ECUtilityModules.STEADY_HAND_MODULE.get(),
                ECUtilityModules.VEIL, ECUtilityModules.VEIL_MODULE.get());
        for (Map.Entry<ModuleType, Item> entry : items.entrySet()) {
            ModuleType type = entry.getKey();
            helper.assertTrue(ArmorModules.get(type.id()) == type, type + " 应已登记");
            helper.assertTrue(type.item() == entry.getValue(), type + " 的物品应是 " + entry.getValue());
            helper.assertTrue(type.toggleable(), type + " 应能在终端里开关");
            Optional<? extends Recipe<?>> recipe = level.getRecipeManager().byKey(EssentialCraft5.id("terminal/" + type.id().getPath()));
            helper.assertTrue(recipe.isPresent(), type + " 应有配方 terminal/" + type.id().getPath());
            int blanks = 0;
            for (Ingredient ingredient : recipe.get().getIngredients()) {
                if (ingredient.test(new ItemStack(ECModules.BLANK_MODULE.get()))) {
                    blanks++;
                }
            }
            helper.assertTrue(blanks == 1, type + " 的配方应吃一块空白模块，实际 " + blanks);
            helper.assertTrue(recipe.get().getResultItem(level.registryAccess()).is(entry.getValue()), type + " 的配方产物不对");
        }
        helper.assertTrue(ECUtilityModules.ATTRACTION.parts().equals(java.util.EnumSet.of(ArmorItem.Type.LEGGINGS)), "吸附只装护腿");
        helper.assertTrue(ECUtilityModules.STRIDE.parts().equals(java.util.EnumSet.of(ArmorItem.Type.BOOTS)), "攀行只装靴子");
        helper.assertTrue(ECUtilityModules.STEADY_HAND.parts().equals(java.util.EnumSet.of(ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)), "稳手装护腿或靴子");
        helper.assertTrue(ECUtilityModules.VEIL.parts().equals(java.util.EnumSet.of(ArmorItem.Type.HELMET)), "隐匿只装头盔");
        helper.assertTrue(ECUtilityModules.ATTRACTION.mruPerSecond() == UtilityModuleConfig.attraction().mruPerSecond()
                && ECUtilityModules.STRIDE.mruPerSecond() == UtilityModuleConfig.stride().mruPerSecond()
                && ECUtilityModules.VEIL.mruPerSecond() == UtilityModuleConfig.veil().mruPerSecond(), "每秒电费读配置");
        helper.assertTrue(ECUtilityModules.STEADY_HAND.mruPerSecond() == 0 && ECUtilityModules.STEADY_HAND.mruPerUse() == 0, "稳手不耗电");
        helper.assertTrue(UtilityModuleConfig.ATTRACTION_DEFAULTS.equals(new UtilityModuleConfig.Attraction(20, 6, 10)), "吸附默认 20 MRU / 秒、6 格、10 刻");
        helper.assertTrue(UtilityModuleConfig.STRIDE_DEFAULTS.equals(new UtilityModuleConfig.Stride(10, 0.4D)), "攀行默认 10 MRU / 秒、+0.4");
        helper.assertTrue(UtilityModuleConfig.VEIL_DEFAULTS.equals(new UtilityModuleConfig.Veil(10, 0.7D, 0.25D)), "隐匿默认 10 MRU / 秒、× 0.7、潜行 × 0.25");
        helper.assertTrue(UtilityModuleEvents.STEADY_HAND_FACTOR == 5F, "稳手抵掉原版的 ÷ 5");
        helper.succeed();
    }

    // ================================================================ 吸附

    /** 范围里没人扔的掉落物被推向玩家，20 刻后离玩家更近；范围外的不动 */
    @GameTest(template = LARGE)
    public static void attractionPullsNatural(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "attr-pull", CENTER, 10_000, ECUtilityModules.ATTRACTION);
        ItemEntity close = drop(helper, CENTER.x - 2.5D, 5.0D);
        ItemEntity far = drop(helper, CENTER.x - (TEST_RADIUS + 1.5D), 5.0D);
        double closeBefore = horizontal(close, player);
        Vec3 farBefore = far.position();
        int cost = UtilityModuleConfig.attraction().mruPerSecond();
        try {
            helper.assertTrue(ModuleTicker.pay(player) == cost && chestMru(player) == 10_000 - cost, "这一秒扣 " + cost + " MRU，实际剩 " + chestMru(player));
            MagnetizingStaffItem.Pulse pulse = attractWithin(player);
            helper.assertTrue(pulse != null && pulse.items() == 1, "应只推范围里的 1 个，实际 " + pulse);
            helper.assertTrue(moved(close) && !moved(far), "近的被推、远的不动");
        } catch (RuntimeException e) {
            cleanup(player);
            throw e;
        }
        helper.runAfterDelay(20, () -> {
            try {
                double closeAfter = horizontal(close, player);
                helper.assertTrue(closeAfter < closeBefore - 0.05D, "20 刻后近的离玩家更近：" + closeBefore + " → " + closeAfter);
                helper.assertTrue(near(far.getX(), farBefore.x) && near(far.getZ(), farBefore.z), "远的没动：" + farBefore + " → " + far.position());
            } finally {
                close.discard();
                far.discard();
                cleanup(player);
            }
            helper.succeed();
        });
    }

    /** 自己扔的、别人扔的都不推，只推没人扔的；扔出来的不占件数名额 */
    @GameTest(template = LARGE)
    public static void attractionSkipsThrown(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "attr-thrown", CENTER, 10_000, ECUtilityModules.ATTRACTION);
        ItemEntity natural = drop(helper, CENTER.x - 2D, CENTER.z);
        ItemEntity own = drop(helper, CENTER.x + 2D, CENTER.z);
        own.setThrower(player.getUUID());
        ItemEntity others = drop(helper, CENTER.x, CENTER.z + 2D);
        others.setThrower(UUID.randomUUID());
        try {
            ModuleTicker.pay(player);
            MagnetizingStaffItem.Pulse pulse = attractWithin(player);
            helper.assertTrue(pulse != null && pulse.items() == 1, "只推 1 个，实际 " + pulse);
            helper.assertTrue(moved(natural), "没人扔的被推");
            helper.assertFalse(moved(own), "自己扔的不推");
            helper.assertFalse(moved(others), "别人扔的不推");
            helper.assertTrue(UtilityModuleEvents.unthrown(natural) && !UtilityModuleEvents.unthrown(own), "unthrown 只认没有丢出者的");
        } finally {
            natural.discard();
            own.discard();
            others.discard();
            cleanup(player);
        }
        helper.succeed();
    }

    /** 潜行时不吸；站起来照吸 */
    @GameTest(template = LARGE)
    public static void attractionSneakOff(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "attr-sneak", CENTER, 10_000, ECUtilityModules.ATTRACTION);
        ItemEntity item = drop(helper, CENTER.x - 2D, CENTER.z);
        try {
            ModuleTicker.pay(player);
            player.setShiftKeyDown(true);
            helper.assertTrue(attractWithin(player) == null && !moved(item), "潜行时不吸");
            player.setShiftKeyDown(false);
            helper.assertTrue(attractWithin(player) != null && moved(item), "站起来照吸");
        } finally {
            item.discard();
            cleanup(player);
        }
        helper.succeed();
    }

    /** 缺电、终端里关掉、没穿齐四件都不吸；关着不扣电 */
    @GameTest(template = LARGE)
    public static void attractionNeedsPower(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "attr-power", CENTER, 0, ECUtilityModules.ATTRACTION);
        ItemEntity item = drop(helper, CENTER.x - 2D, CENTER.z);
        try {
            helper.assertTrue(ModuleTicker.pay(player) == 0, "前提：没电付不起");
            helper.assertTrue(attractWithin(player) == null && !moved(item), "缺电时不吸");

            setChestMru(player, 10_000);
            ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
            helper.assertTrue(ArmorModules.setOn(legs, 0, false), "前提：关掉护腿上的吸附");
            ModuleCache.invalidate(player);
            helper.assertTrue(ModuleTicker.pay(player) == 0 && chestMru(player) == 10_000, "关着的不扣电");
            helper.assertTrue(attractWithin(player) == null && !moved(item), "关着的不吸");

            ArmorModules.setOn(legs, 0, true);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            ModuleCache.invalidate(player);
            ModuleTicker.pay(player);
            helper.assertTrue(attractWithin(player) == null && !moved(item), "没穿齐四件不吸");
        } finally {
            item.discard();
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 正式环境由玩家刻末尾的 {@code onUtilityModulePlayerTick} 每 interval_ticks 刻推一次（按 (tickCount + 实体 id) 错开）。测试玩家不进世界，
     * 这里把 tickCount 摆好、手动发 PlayerTickEvent：不在节拍上的刻、刻开头（START）不推，在节拍上的刻末尾推
     */
    @GameTest(template = LARGE)
    public static void attractionRunsFromPlayerTick(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "attr-tick", CENTER, 10_000, ECUtilityModules.ATTRACTION);
        ItemEntity item = drop(helper, CENTER.x - 2D, CENTER.z);
        UtilityModuleConfig.Attraction d = UtilityModuleConfig.attraction();
        int interval = new UtilityModuleConfig.Attraction(d.mruPerSecond(), TEST_RADIUS, d.intervalTicks()).effectiveIntervalTicks();
        int base = interval * 40;
        try {
            ModuleTicker.pay(player);
            UtilityModuleConfig.setTestAttraction(new UtilityModuleConfig.Attraction(d.mruPerSecond(), TEST_RADIUS, interval));
            try {
                player.tickCount = base + Math.floorMod(1 - player.getId(), interval);
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                helper.assertFalse(moved(item), "不在节拍上的那一刻不推");
                player.tickCount = base + Math.floorMod(-player.getId(), interval);
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
                helper.assertFalse(moved(item), "刻开头（START）不推");
                MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            } finally {
                UtilityModuleConfig.setTestAttraction(null);
            }
            helper.assertTrue(moved(item), "在节拍上的刻末尾推一次");
        } finally {
            item.discard();
            cleanup(player);
        }
        helper.succeed();
    }

    /** 吸附节拍：间隔不低于 4 刻；半径超过 8 格时至少 10 刻（policy P2 的两档） */
    @GameTest(template = EMPTY)
    public static void attractionIntervalFloor(GameTestHelper helper) {
        helper.assertTrue(new UtilityModuleConfig.Attraction(20, 6, 10).effectiveIntervalTicks() == 10, "默认 6 格、10 刻照旧");
        helper.assertTrue(new UtilityModuleConfig.Attraction(20, 8, 4).effectiveIntervalTicks() == 4, "8 格以内可以 4 刻一次");
        helper.assertTrue(new UtilityModuleConfig.Attraction(20, 8, 2).effectiveIntervalTicks() == 4, "低于 4 刻按 4 刻");
        helper.assertTrue(new UtilityModuleConfig.Attraction(20, 9, 4).effectiveIntervalTicks() == 10, "超过 8 格至少 10 刻");
        helper.assertTrue(new UtilityModuleConfig.Attraction(20, 12, 30).effectiveIntervalTicks() == 30, "配得更慢的照配置");
        helper.succeed();
    }

    /** 磁化法杖照旧推玩家自己扔的东西（加重载没改老行为）；同一件吸附不推 */
    @GameTest(template = LARGE)
    public static void staffUnchanged(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "attr-staff", CENTER, 10_000, ECUtilityModules.ATTRACTION);
        ItemEntity own = drop(helper, CENTER.x - 2D, CENTER.z);
        own.setThrower(player.getUUID());
        ServerLevel level = helper.getLevel();
        try {
            MagnetizingStaffItem.Pulse staff = MagnetizingStaffItem.pulse(level, player, TEST_RADIUS);
            helper.assertTrue(staff.items() == 1 && moved(own), "法杖推自己扔的，实际 " + staff);
            own.setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(MagnetizingStaffItem.pulse(level, player, TEST_RADIUS, item -> true).items() == 1 && moved(own),
                    "额外条件恒真的重载和老的一样");
            own.setDeltaMovement(Vec3.ZERO);
            ModuleTicker.pay(player);
            MagnetizingStaffItem.Pulse module = attractWithin(player);
            helper.assertTrue(module != null && module.items() == 0 && !moved(own), "吸附不推自己扔的，实际 " + module);
        } finally {
            own.discard();
            cleanup(player);
        }
        helper.succeed();
    }

    // ================================================================ 攀行

    /**
     * 步高：宅男靴子自带 +0.6（客户端玩家原版 0.6 → 1.2），攀行开着再加 step_bonus 到 1.6；关掉、缺电、拆下、下线都摘回只剩靴子的 +0.6。
     * 断言看 Forge 的步高加成属性本身：服务端的 ServerPlayer 构造时把自己的 maxUpStep 设成 1.0（原版给服务端移动校验留的余量），
     * getStepHeight() 在这里会比客户端多 0.4，玩家真正走路用的是客户端那份（LocalPlayer 0.6 + 同一个属性，属性会同步过去）。
     * 测试玩家不走真 tick，盔甲的属性修饰符不会自己挂上，这里照原版换装那一步手动挂上靴子的
     */
    @GameTest(template = EMPTY)
    public static void strideModifier(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "stride", new Vec3(2.5D, 1D, 2.5D), 10_000, ECUtilityModules.STRIDE);
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        player.getAttributes().addTransientAttributeModifiers(boots.getAttributeModifiers(EquipmentSlot.FEET));
        AttributeInstance step = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        double bonus = UtilityModuleConfig.stride().stepBonus();
        double serverBase = player.maxUpStep();
        try {
            helper.assertTrue(step != null, "玩家应有步高属性");
            helper.assertTrue(near(step.getValue(), BOOTS_STEP), "前提：靴子自带 +0.6，实际 " + step.getValue());
            helper.assertTrue(near(player.getStepHeight(), serverBase + BOOTS_STEP), "getStepHeight = maxUpStep + 属性，实际 " + player.getStepHeight());
            UtilityModuleEvents.updateStride(player);
            helper.assertTrue(step.getModifier(UtilityModuleEvents.STRIDE_MODIFIER) == null, "这一秒的电还没付：不加");

            int cost = UtilityModuleConfig.stride().mruPerSecond();
            helper.assertTrue(ModuleTicker.pay(player) == cost, "这一秒扣 " + cost);
            UtilityModuleEvents.updateStride(player);
            AttributeModifier modifier = step.getModifier(UtilityModuleEvents.STRIDE_MODIFIER);
            helper.assertTrue(modifier != null && modifier.getAmount() == bonus, "开着：挂 +" + bonus);
            helper.assertTrue(near(step.getValue(), BOOTS_STEP + bonus), "开着：加成 " + (BOOTS_STEP + bonus) + "，实际 " + step.getValue());
            helper.assertTrue(near(CLIENT_BASE_STEP + BOOTS_STEP + UtilityModuleConfig.STRIDE_DEFAULTS.stepBonus(), 1.6D), "默认在客户端合计 1.6 格");
            UtilityModuleEvents.updateStride(player);
            helper.assertTrue(near(step.getValue(), BOOTS_STEP + bonus), "再调一次不叠加");

            helper.assertTrue(ArmorModules.setOn(boots, 0, false), "前提：关掉");
            ModuleCache.invalidate(player);
            UtilityModuleEvents.updateStride(player);
            helper.assertTrue(near(step.getValue(), BOOTS_STEP), "关掉：摘回靴子的 +0.6，实际 " + step.getValue());

            ArmorModules.setOn(boots, 0, true);
            ModuleCache.invalidate(player);
            ModuleTicker.pay(player);
            UtilityModuleEvents.updateStride(player);
            helper.assertTrue(near(step.getValue(), BOOTS_STEP + bonus), "再打开：加回来");

            setChestMru(player, 0);
            helper.assertTrue(ModuleTicker.pay(player) == 0, "前提：没电付不起");
            UtilityModuleEvents.updateStride(player);
            helper.assertTrue(near(step.getValue(), BOOTS_STEP), "缺电：摘掉，实际 " + step.getValue());

            setChestMru(player, 10_000);
            ModuleTicker.pay(player);
            UtilityModuleEvents.updateStride(player);
            helper.assertTrue(near(step.getValue(), BOOTS_STEP + bonus), "补上电：加回来");
            UtilityModuleEvents.removeStride(player);
            helper.assertTrue(near(step.getValue(), BOOTS_STEP), "下线时摘掉");
            UtilityModuleEvents.updateStride(player);

            helper.assertTrue(ArmorModules.remove(boots, 0) != null, "前提：拆下");
            ModuleCache.invalidate(player);
            UtilityModuleEvents.updateStride(player);
            helper.assertTrue(near(step.getValue(), BOOTS_STEP), "拆下：摘掉，实际 " + step.getValue());
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ================================================================ 稳手

    /** 悬空时同一方块的挖掘速度：开着 = 关着 × 5；站在地上不变；不耗电、身上没电也生效 */
    @GameTest(template = EMPTY)
    public static void steadyHandAir(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "steady", new Vec3(2.5D, 1D, 2.5D), 0, ECUtilityModules.STEADY_HAND);
        BlockState stone = Blocks.STONE.defaultBlockState();
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        try {
            helper.assertTrue(ModuleTicker.pay(player) == 0 && ModuleCache.active(player, ECUtilityModules.STEADY_HAND), "不耗电、没电照样生效");
            player.setOnGround(false);
            float on = player.getDigSpeed(stone, null);
            helper.assertTrue(UtilityModuleEvents.steadyHandFactor(player) == 5F, "悬空：倍数 5");

            helper.assertTrue(ArmorModules.setOn(legs, 0, false), "前提：关掉");
            ModuleCache.invalidate(player);
            float off = player.getDigSpeed(stone, null);
            helper.assertTrue(Math.abs(on - off * 5F) < 1.0E-4F, "悬空：开着 = 关着 × 5，实际 " + on + " / " + off);

            ArmorModules.setOn(legs, 0, true);
            ModuleCache.invalidate(player);
            player.setOnGround(true);
            float groundOn = player.getDigSpeed(stone, null);
            helper.assertTrue(Math.abs(groundOn - off * 5F) < 1.0E-4F && UtilityModuleEvents.steadyHandFactor(player) == 1F,
                    "站在地上：和不减速时一样，倍数 1，实际 " + groundOn);
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ================================================================ 隐匿

    /** 怪物看你：开着 × 0.7、潜行 × 0.8 × 0.25；另一名玩家看你不变；关掉回到 1 */
    @GameTest(template = EMPTY)
    public static void veilMobOnly(GameTestHelper helper) {
        ServerPlayer player = suited(helper, "veil", new Vec3(1.5D, 1D, 1.5D), 10_000, ECUtilityModules.VEIL);
        ServerPlayer other = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "veil-other");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        UtilityModuleConfig.Veil cfg = UtilityModuleConfig.veil();
        try {
            helper.assertTrue(near(player.getVisibilityPercent(zombie), 1D), "这一秒的电还没付：不生效");
            ModuleTicker.pay(player);
            helper.assertTrue(near(player.getVisibilityPercent(zombie), cfg.multiplier()), "怪物看：× " + cfg.multiplier() + "，实际 "
                    + player.getVisibilityPercent(zombie));
            helper.assertTrue(near(player.getVisibilityPercent(other), 1D), "玩家看：不变，实际 " + player.getVisibilityPercent(other));
            helper.assertTrue(near(player.getVisibilityPercent(null), 1D), "没有看的人：不变");
            player.setShiftKeyDown(true);
            helper.assertTrue(near(player.getVisibilityPercent(zombie), 0.8D * cfg.sneakMultiplier()),
                    "潜行：原版 × 0.8 再 × " + cfg.sneakMultiplier() + "，实际 " + player.getVisibilityPercent(zombie));
            helper.assertTrue(near(player.getVisibilityPercent(other), 0.8D), "潜行时玩家看：只有原版的 × 0.8");
            player.setShiftKeyDown(false);

            helper.assertTrue(ArmorModules.setOn(player.getItemBySlot(EquipmentSlot.HEAD), 0, false), "前提：关掉");
            ModuleCache.invalidate(player);
            helper.assertTrue(near(player.getVisibilityPercent(zombie), 1D), "关掉：回到 1");
        } finally {
            zombie.discard();
            cleanup(player, other);
        }
        helper.succeed();
    }

    // ================================================================ 槽位

    /** 每块只能装在设计的部位上：攀行装不进胸甲、隐匿装不进靴子，等等 */
    @GameTest(template = EMPTY)
    public static void slotsRespected(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        for (ModuleType type : List.of(ECUtilityModules.ATTRACTION, ECUtilityModules.STRIDE, ECUtilityModules.STEADY_HAND, ECUtilityModules.VEIL)) {
            for (ArmorItem.Type part : ArmorItem.Type.values()) {
                ItemStack piece = new ItemStack(ECComputer.armor().get(part.ordinal()).get());
                ArmorModules.InstallCheck check = ArmorModules.check(piece, type);
                ArmorModules.InstallCheck expected = type.fits(part) ? ArmorModules.InstallCheck.OK : ArmorModules.InstallCheck.WRONG_PART;
                if (check != expected) {
                    wrong.add(type.id().getPath() + " → " + part + "：" + check + "（应 " + expected + "）");
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "装不装得上不对：" + wrong);
        ItemStack chest = new ItemStack(ECComputer.armor().get(ArmorItem.Type.CHESTPLATE.ordinal()).get());
        helper.assertTrue(ArmorModules.install(chest, ECUtilityModules.STRIDE).isEmpty(), "攀行装不进胸甲");
        ItemStack boots = new ItemStack(ECComputer.armor().get(ArmorItem.Type.BOOTS.ordinal()).get());
        helper.assertTrue(ArmorModules.install(boots, ECUtilityModules.VEIL).isEmpty(), "隐匿装不进靴子");
        helper.assertFalse(ArmorModules.install(boots, ECUtilityModules.STEADY_HAND).isEmpty(), "稳手装得进靴子");
        helper.succeed();
    }
}
