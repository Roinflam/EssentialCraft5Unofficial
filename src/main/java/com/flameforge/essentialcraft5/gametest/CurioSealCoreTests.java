package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.event.CurioSealEvent;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import com.flameforge.essentialcraft5.compat.curio.CurioSealConfig;
import com.flameforge.essentialcraft5.compat.curio.CurioSealMixinStatus;
import com.flameforge.essentialcraft5.compat.curio.SealedCuriosView;
import com.flameforge.essentialcraft5.item.ComputerBoardItem;
import com.flameforge.essentialcraft5.item.bauble.MysticalAccessoryItem;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.network.S2CCurioSealSlotPacket;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.util.ECFlight;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.EnderManAngerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioEquipEvent;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 饰品压制核心（第十批 10a-F，tools/plan/batch10.md §4.1.11）的 GameTest。
 * <p>
 * 「别的模组的饰品」用 {@code CuriosApi.registerCurio(鹦鹉螺壳, 探针)} 造（只在测试里，不注册新物品）：探针按玩家数 curioTick、onEquip、onUnequip，
 * 给护甲 +4 和一个额外戒指槽（都用 Curios 传进来的槽位 UUID），猪灵中立、细雪行走、末影人面具，时运 3、抢夺 2。
 * 另两种探针：海洋之心（「同种只许戴一件」，canEquip 里查同种）、回响碎片（按 NBT 多给 1 或 2 个戒指槽，槽位 UUID 不变）。
 * 玩家用 {@link ProtectionFrameworkTests#realPlayer}（真 ServerPlayer，不进世界），Curios 的 tick 靠手动发 {@code LivingTickEvent}；
 * 压制直接调 {@code CurioSeal.hold} + {@code resolveForTest}（只结算这名玩家，并行的测试互不影响），收尾 {@code releaseForTest}。
 * 测试都在同一刻里做完。改了豁免覆盖的两条各自单独一个 batch。
 * 断言「压制当刻就生效」的地方不多走 Curios tick（钩子那条只拨 tickCount），免得测试代码替实现补上晚的那一刻。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CurioSealCoreTests {

    private static final String EMPTY = "empty";
    /** 测试玩家的起始 tickCount（见 {@link #player}） */
    private static final int ONLINE_TICKS = 100;
    private static final String BATCH = "curio_seal_core";
    private static final String BELT = "belt";
    private static final String RING = "ring";
    private static final String NECKLACE = "necklace";
    private static final long FAR = 1_000_000L;

    private static final ProbeCurio PROBE = new ProbeCurio();
    private static boolean probeRegistered;

    private CurioSealCoreTests() {
    }

    // ================================================================ 探针

    /** 「别的模组的饰品」：只在测试里登记到鹦鹉螺壳上 */
    private static final class ProbeCurio implements ICurioItem {
        final Map<UUID, int[]> counts = new ConcurrentHashMap<>();

        int[] of(LivingEntity entity) {
            return counts.computeIfAbsent(entity.getUUID(), id -> new int[3]);
        }

        int ticks(LivingEntity entity) {
            return of(entity)[0];
        }

        int equips(LivingEntity entity) {
            return of(entity)[1];
        }

        int unequips(LivingEntity entity) {
            return of(entity)[2];
        }

        @Override
        public void curioTick(SlotContext slotContext, ItemStack stack) {
            if (slotContext.entity() != null && !slotContext.entity().level().isClientSide) {
                of(slotContext.entity())[0]++;
            }
        }

        @Override
        public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
            if (slotContext.entity() != null && !slotContext.cosmetic()) {
                of(slotContext.entity())[1]++;
            }
        }

        @Override
        public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
            if (slotContext.entity() != null && !slotContext.cosmetic()) {
                of(slotContext.entity())[2]++;
            }
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
            Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
            map.put(Attributes.ARMOR, new AttributeModifier(uuid, "essentialcraft5:seal_probe", 4D, AttributeModifier.Operation.ADDITION));
            CuriosApi.addSlotModifier(map, RING, uuid, 1D, AttributeModifier.Operation.ADDITION);
            return map;
        }

        @Override
        public boolean makesPiglinsNeutral(SlotContext slotContext, ItemStack stack) {
            return true;
        }

        @Override
        public boolean canWalkOnPowderedSnow(SlotContext slotContext, ItemStack stack) {
            return true;
        }

        @Override
        public boolean isEnderMask(SlotContext slotContext, EnderMan enderMan, ItemStack stack) {
            return true;
        }

        @Override
        public int getFortuneLevel(SlotContext slotContext, LootContext lootContext, ItemStack stack) {
            return 3;
        }

        @Override
        public int getLootingLevel(SlotContext slotContext, DamageSource source, LivingEntity target, int baseLooting, ItemStack stack) {
            return 2;
        }
    }

    // ================================================================ 工具

    /** 「同种只许戴一件」的饰品（照常见写法：canEquip 里 findFirstCurio 查同种，查到的不是这一格就不许）：登记到海洋之心上 */
    private static final class SingletonProbe implements ICurioItem {
        @Override
        public boolean canEquip(SlotContext slotContext, ItemStack stack) {
            return CuriosApi.getCuriosInventory(slotContext.entity()).resolve()
                    .flatMap(inventory -> inventory.findFirstCurio(stack.getItem()))
                    .map(found -> found.slotContext().identifier().equals(slotContext.identifier())
                            && found.slotContext().index() == slotContext.index())
                    .orElse(true);
        }
    }

    /** 按 NBT 多给 1 或 2 个戒指槽的饰品（升级 / 降级会改数量，槽位 UUID 不变）：登记到回响碎片上 */
    private static final class SlotGiverProbe implements ICurioItem {
        static final String TAG_EXTRA = "ec5_extra_rings";

        @Override
        public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
            Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
            int extra = stack.getTag() != null && stack.getTag().getInt(TAG_EXTRA) == 2 ? 2 : 1;
            CuriosApi.addSlotModifier(map, RING, uuid, extra, AttributeModifier.Operation.ADDITION);
            return map;
        }
    }

    /**
     * 「套装」写法的饰品（照常见的套装件数算法：onEquip / onUnequip 里用 findCurios 数身上有几件同种）：登记到海晶碎片上，
     * 按玩家记下每次 onEquip / onUnequip 数到的件数
     */
    private static final class SetProbe implements ICurioItem {
        final Map<UUID, List<Integer>> equipCounts = new ConcurrentHashMap<>();
        final Map<UUID, List<Integer>> unequipCounts = new ConcurrentHashMap<>();

        private static int count(SlotContext context, ItemStack stack) {
            return CuriosApi.getCuriosInventory(context.entity()).map(inventory -> inventory.findCurios(stack.getItem()).size()).orElse(-1);
        }

        List<Integer> equips(LivingEntity entity) {
            return equipCounts.computeIfAbsent(entity.getUUID(), id -> Collections.synchronizedList(new ArrayList<>()));
        }

        List<Integer> unequips(LivingEntity entity) {
            return unequipCounts.computeIfAbsent(entity.getUUID(), id -> Collections.synchronizedList(new ArrayList<>()));
        }

        void clear(LivingEntity entity) {
            equips(entity).clear();
            unequips(entity).clear();
        }

        @Override
        public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
            if (slotContext.entity() != null && !slotContext.cosmetic() && !slotContext.entity().level().isClientSide) {
                equips(slotContext.entity()).add(count(slotContext, stack));
            }
        }

        @Override
        public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
            if (slotContext.entity() != null && !slotContext.cosmetic() && !slotContext.entity().level().isClientSide) {
                unequips(slotContext.entity()).add(count(slotContext, stack));
            }
        }
    }

    private static final SetProbe SET_PROBE = new SetProbe();

    private static synchronized void registerProbe() {
        if (!probeRegistered) {
            probeRegistered = true;
            CuriosApi.registerCurio(Items.NAUTILUS_SHELL, PROBE);
            CuriosApi.registerCurio(Items.HEART_OF_THE_SEA, new SingletonProbe());
            CuriosApi.registerCurio(Items.ECHO_SHARD, new SlotGiverProbe());
            CuriosApi.registerCurio(Items.PRISMARINE_SHARD, SET_PROBE);
        }
    }

    private static ItemStack slotGiver(int extraRings) {
        ItemStack stack = new ItemStack(Items.ECHO_SHARD);
        stack.getOrCreateTag().putInt(SlotGiverProbe.TAG_EXTRA, extraRings);
        return stack;
    }

    /** 发一次装备校验事件（Curios 的 isItemValid 先发它），返回结果 */
    private static Event.Result equipCheck(ItemStack stack, SlotContext context) {
        CurioEquipEvent event = new CurioEquipEvent(stack, context);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getResult();
    }

    private static ItemStack probe() {
        return new ItemStack(Items.NAUTILUS_SHELL);
    }

    private static void requireAvailable(GameTestHelper helper) {
        helper.assertTrue(CurioSeal.availability() == CurioSeal.Availability.AVAILABLE,
                "压制核心应可用，实际 " + CurioSeal.availability() + " " + CurioSealMixinStatus.problems());
    }

    /**
     * 测试玩家：真 ServerPlayer，不进世界。{@code tickCount} 先拨到 {@value #ONLINE_TICKS}（当成已经在线一会儿）：
     * 有的 Curios 5.14.x 构建按 {@code tickCount} 缓存原版钩子的结果、刚进世界 10 刻内不缩槽，测试玩家不走真的 tick，不拨的话这些缓存永远不过期。
     */
    private static ServerPlayer player(GameTestHelper helper) {
        registerProbe();
        requireAvailable(helper);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.tickCount = ONLINE_TICKS;
        return player;
    }

    private static ICuriosItemHandler inventory(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).resolve().orElseThrow(() -> new IllegalStateException("玩家应有 Curios 饰品栏"));
    }

    private static int slots(LivingEntity entity, String id) {
        return inventory(entity).getStacksHandler(id).map(ICurioStacksHandler::getSlots).orElse(0);
    }

    private static ItemStack realStack(LivingEntity entity, String id, int index) {
        return inventory(entity).getStacksHandler(id).map(handler -> handler.getStacks().getStackInSlot(index)).orElse(ItemStack.EMPTY);
    }

    private static void equip(ServerPlayer player, String id, int index, ItemStack stack) {
        inventory(player).setEquippedCurio(id, index, stack);
        CuriosCompat.invalidate(player);
    }

    /**
     * 一刻：Curios 的每刻处理（也会跑到本模组的 SealSlotKeeper），再把 {@code tickCount} 加一
     * （真游戏里实体每刻都加，见 {@code ServerLevel.tickNonPassenger}；测试玩家不进世界，只能手动加）
     */
    private static void tick(LivingEntity entity) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(entity));
        entity.tickCount++;
    }

    private static long now(GameTestHelper helper) {
        return CurioSeal.now(helper.getLevel().getServer());
    }

    private static void seal(GameTestHelper helper, ServerPlayer player) {
        long now = now(helper);
        CurioSeal.hold(player, player, now + FAR);
        CurioSeal.resolveForTest(now, player);
        helper.assertTrue(CurioSeal.isSealed(player), "hold + resolve 以后应被压制");
    }

    private static void unseal(GameTestHelper helper, ServerPlayer player) {
        CurioSeal.drop(player, player.getUUID());
        CurioSeal.resolveForTest(now(helper) + CurioSealConfig.values().minHoldTicks(), player);
        helper.assertFalse(CurioSeal.isSealed(player), "撤掉来源、过了最少保持时间应解除");
    }

    private static double armor(LivingEntity entity) {
        return entity.getAttributeValue(Attributes.ARMOR);
    }

    // ================================================================ 1 ~ 4：tick、属性、槽位数

    /** curioTick：压制前每刻 +1，压制后不再增加；onUnequip 正好 1 次；解除后 onEquip 再 1 次、tick 恢复；两次状态翻转各发一次 CurioSealEvent */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealStopsTicking(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        List<Boolean> events = new ArrayList<>();
        Consumer<CurioSealEvent> listener = event -> {
            if (event.getEntity() == player) {
                events.add(event.isSealed());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, CurioSealEvent.class, listener);
        try {
            equip(player, BELT, 0, probe());
            tick(player);
            tick(player);
            helper.assertTrue(PROBE.equips(player) == 1 && PROBE.ticks(player) == 2,
                    "压制前：戴上一次、每刻 tick，实际 equip " + PROBE.equips(player) + " tick " + PROBE.ticks(player));
            seal(helper, player);
            tick(player);
            tick(player);
            tick(player);
            helper.assertTrue(PROBE.ticks(player) == 2, "压制后不再 curioTick，实际 " + PROBE.ticks(player));
            helper.assertTrue(PROBE.unequips(player) == 1, "压制开始时 onUnequip 正好 1 次，实际 " + PROBE.unequips(player));
            unseal(helper, player);
            tick(player);
            helper.assertTrue(PROBE.equips(player) == 2, "解除后 onEquip 再 1 次，实际 " + PROBE.equips(player));
            helper.assertTrue(PROBE.ticks(player) == 3, "解除后 tick 恢复，实际 " + PROBE.ticks(player));
            helper.assertTrue(events.equals(List.of(true, false)), "压制 / 解除各发一次 CurioSealEvent，实际 " + events);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /** 属性：护甲 = 基础 + 4 → 压制后 = 基础 → 解除后回到基础 + 4 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealRemovesAttributes(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            double base = armor(player);
            equip(player, BELT, 0, probe());
            tick(player);
            helper.assertTrue(armor(player) == base + 4D, "戴上探针护甲 +4，实际 " + armor(player) + "（基础 " + base + "）");
            seal(helper, player);
            tick(player);
            helper.assertTrue(armor(player) == base, "压制后护甲回到基础，实际 " + armor(player));
            unseal(helper, player);
            tick(player);
            helper.assertTrue(armor(player) == base + 4D, "解除后护甲回到基础 + 4，实际 " + armor(player));
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /** 槽位数保留：探针在腰带槽时戒指槽 = 基础 + 1，额外那格放一件东西；压制后槽数不变、那件还在、背包和地上没多东西 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealKeepsExtraSlots(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            int base = slots(player, RING);
            equip(player, BELT, 0, probe());
            tick(player);
            helper.assertTrue(slots(player, RING) == base + 1, "探针给一个额外戒指槽，实际 " + slots(player, RING) + "（基础 " + base + "）");
            equip(player, RING, base, new ItemStack(Items.GOLD_NUGGET));
            tick(player);
            int itemsBefore = helper.getEntities(EntityType.ITEM, BlockPos.ZERO, 16D).size();
            seal(helper, player);
            tick(player);
            tick(player);
            helper.assertTrue(slots(player, RING) == base + 1, "压制期间额外槽位保留，实际 " + slots(player, RING));
            helper.assertTrue(realStack(player, RING, base).is(Items.GOLD_NUGGET), "额外那格的东西还在");
            helper.assertFalse(player.getInventory().items.stream().anyMatch(stack -> stack.is(Items.GOLD_NUGGET)), "没被挤进背包");
            helper.assertTrue(helper.getEntities(EntityType.ITEM, BlockPos.ZERO, 16D).size() == itemsBefore, "地上没多掉落物");
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /** 压制中摘掉探针：对账当刻去掉 +1；解除后不会多出槽位 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealReconcilesRemovedSlotGiver(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            int base = slots(player, RING);
            equip(player, BELT, 0, probe());
            tick(player);
            helper.assertTrue(slots(player, RING) == base + 1, "前提：探针给一个额外戒指槽");
            seal(helper, player);
            tick(player);
            equip(player, BELT, 0, ItemStack.EMPTY);
            tick(player);
            // 对账当刻就去掉修饰符（两种构建都不推迟这一步）
            UUID giverId = CuriosApi.getSlotUuid(new SlotContext(BELT, player, 0, false, true));
            helper.assertFalse(inventory(player).getStacksHandler(RING).map(handler -> handler.getModifiers().containsKey(giverId)).orElse(false),
                    "摘掉后的第一刻对账就去掉探针给的槽位修饰符");
            tick(player);
            // 有的 Curios 构建缩槽要隔一刻再确认（防同一刻里修饰符先去后加把东西挤出来），所以多走一刻再看槽数
            helper.assertTrue(slots(player, RING) == base, "压制中摘掉探针，对账后去掉额外槽位，实际 " + slots(player, RING));
            unseal(helper, player);
            tick(player);
            tick(player);
            helper.assertTrue(slots(player, RING) == base, "解除后不会多出槽位，实际 " + slots(player, RING));
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    // ================================================================ 5 ~ 8：查询、钩子、同步包

    /** 查询全过滤：isEquipped 两种、findFirstCurio 两种、findCurios 三种、findCurio、旧接口两种都查不到；时运 0、抢夺 0 */
    @GameTest(template = EMPTY, batch = BATCH)
    @SuppressWarnings({"deprecation", "removal"})
    public static void curioSealFiltersQueries(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            equip(player, BELT, 0, probe());
            ICuriosItemHandler inv = inventory(player);
            DamageSource source = player.damageSources().generic();
            helper.assertTrue(inv.isEquipped(Items.NAUTILUS_SHELL) && inv.getFortuneLevel(null) == 3
                    && inv.getLootingLevel(source, player, 0) == 2, "前提：没压制时查得到、时运 3、抢夺 2");
            seal(helper, player);
            helper.assertFalse(inv.isEquipped(Items.NAUTILUS_SHELL), "isEquipped(Item)");
            helper.assertFalse(inv.isEquipped(stack -> stack.is(Items.NAUTILUS_SHELL)), "isEquipped(Predicate)");
            helper.assertTrue(inv.findFirstCurio(Items.NAUTILUS_SHELL).isEmpty(), "findFirstCurio(Item)");
            helper.assertTrue(inv.findFirstCurio(stack -> stack.is(Items.NAUTILUS_SHELL)).isEmpty(), "findFirstCurio(Predicate)");
            helper.assertTrue(inv.findCurios(Items.NAUTILUS_SHELL).isEmpty(), "findCurios(Item)");
            helper.assertTrue(inv.findCurios(stack -> stack.is(Items.NAUTILUS_SHELL)).isEmpty(), "findCurios(Predicate)");
            helper.assertTrue(inv.findCurios(BELT).isEmpty(), "findCurios(String...)");
            helper.assertTrue(inv.findCurio(BELT, 0).isEmpty(), "findCurio(String, int)");
            helper.assertTrue(CuriosApi.getCuriosHelper().findEquippedCurio(Items.NAUTILUS_SHELL, player).isEmpty(), "findEquippedCurio(Item)");
            helper.assertTrue(CuriosApi.getCuriosHelper().findEquippedCurio(stack -> stack.is(Items.NAUTILUS_SHELL), player).isEmpty(),
                    "findEquippedCurio(Predicate)");
            helper.assertTrue(inv.getFortuneLevel(null) == 0, "时运应为 0，实际 " + inv.getFortuneLevel(null));
            helper.assertTrue(inv.getLootingLevel(source, player, 0) == 0, "抢夺应为 0，实际 " + inv.getLootingLevel(source, player, 0));
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /** 「背包里有没有」不过滤：Inventory.contains / hasAnyMatching 照样认得饰品栏里的探针 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealKeepsInventoryContains(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            equip(player, BELT, 0, probe());
            helper.assertTrue(player.getInventory().contains(probe()), "前提：Curios 让 Inventory.contains 认得饰品栏");
            seal(helper, player);
            helper.assertTrue(player.getInventory().contains(probe()), "压制中 Inventory.contains 照样为真");
            helper.assertTrue(player.getInventory().hasAnyMatching(stack -> stack.is(Items.NAUTILUS_SHELL)), "压制中 hasAnyMatching 照样为真");
            helper.assertTrue(inventory(player).findFirstCurio(Items.NAUTILUS_SHELL).isEmpty(), "钩子外的查询照样过滤（作用域没残留）");
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /** 原版钩子：猪灵中立、细雪行走、免冻、末影人面具在压制中都失效 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealMasksVanillaHooks(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            equip(player, BELT, 0, probe());
            equip(player, NECKLACE, 0, new ItemStack(Items.LEATHER_BOOTS));
            EnderMan enderMan = EntityType.ENDERMAN.create(helper.getLevel());
            helper.assertTrue(PiglinAi.isWearingGold(player) && PowderSnowBlock.canEntityWalkOnPowderSnow(player) && !player.canFreeze(),
                    "前提：探针让猪灵中立、能走细雪，饰品栏里的皮靴免冻");
            EnderManAngerEvent before = new EnderManAngerEvent(enderMan, player);
            MinecraftForge.EVENT_BUS.post(before);
            helper.assertTrue(before.isCanceled(), "前提：探针当末影人面具");
            seal(helper, player);
            // 只拨 tickCount、不跑 Curios 的 tick：让按 tickCount 缓存钩子结果的构建过期，又证明压制当刻就生效、不靠 Curios tick 留下的状态
            player.tickCount++;
            helper.assertFalse(PiglinAi.isWearingGold(player), "压制中猪灵不再中立");
            helper.assertFalse(PowderSnowBlock.canEntityWalkOnPowderSnow(player), "压制中不能走细雪");
            helper.assertTrue(player.canFreeze(), "压制中回到原版：会被冻");
            EnderManAngerEvent after = new EnderManAngerEvent(enderMan, player);
            MinecraftForge.EVENT_BUS.post(after);
            helper.assertFalse(after.isCanceled(), "压制中不再当末影人面具");
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /** 同步包：遮罩作用域里构造的 SPacketSyncStack 带真物品；作用域外、换了线程、装饰槽都原样 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealCorrectsSyncPacket(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper);
        Class<?> packetClass = Class.forName("top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack");
        Class<?> typeClass = Class.forName("top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack$HandlerType");
        Object equipment = typeClass.getEnumConstants()[0];
        Object cosmetic = typeClass.getEnumConstants()[1];
        Constructor<?> constructor = packetClass.getConstructor(int.class, String.class, int.class, ItemStack.class, typeClass, CompoundTag.class);
        Field stackField = packetClass.getDeclaredField("stack");
        stackField.setAccessible(true);
        ItemStack[] results = new ItemStack[3];
        Throwable[] failure = new Throwable[1];
        try {
            equip(player, BELT, 0, probe());
            seal(helper, player);
            CurioSeal.masking(handler -> {
                try {
                    results[0] = (ItemStack) stackField.get(constructor.newInstance(player.getId(), BELT, 0, ItemStack.EMPTY, equipment, new CompoundTag()));
                    results[1] = (ItemStack) stackField.get(constructor.newInstance(player.getId(), BELT, 0, ItemStack.EMPTY, cosmetic, new CompoundTag()));
                    Thread other = new Thread(() -> {
                        try {
                            results[2] = (ItemStack) stackField.get(
                                    constructor.newInstance(player.getId(), BELT, 0, ItemStack.EMPTY, equipment, new CompoundTag()));
                        } catch (ReflectiveOperationException e) {
                            failure[0] = e;
                        }
                    }, "ec5-seal-sync-test");
                    other.start();
                    other.join();
                } catch (ReflectiveOperationException | InterruptedException e) {
                    failure[0] = e;
                }
            }).accept(inventory(player));
            helper.assertTrue(failure[0] == null, "反射构造同步包出错：" + failure[0]);
            ItemStack outside = (ItemStack) stackField.get(constructor.newInstance(player.getId(), BELT, 0, ItemStack.EMPTY, equipment, new CompoundTag()));
            helper.assertTrue(results[0] != null && results[0].is(Items.NAUTILUS_SHELL), "作用域里的功能槽同步包应换回真物品，实际 " + results[0]);
            helper.assertTrue(results[0] != realStack(player, BELT, 0), "换进包里的是拷贝，不是真槽里那个对象");
            helper.assertTrue(results[1] != null && results[1].isEmpty(), "装饰槽的包不换，实际 " + results[1]);
            helper.assertTrue(results[2] != null && results[2].isEmpty(), "别的线程构造的不换，实际 " + results[2]);
            helper.assertTrue(outside.isEmpty(), "作用域外不换，实际 " + outside);
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    // ================================================================ 9 ~ 12：存储、掉落、死亡、豁免

    /**
     * 存储不动：压制中真槽里还是那件；writeTag 里有它；PlayerEvent.Clone 以后新玩家的饰品栏里有它。
     * 这条用本模组真的腰带（θ 腰带，在 {@code curios:belt} 标签里）：Curios 读档（readTag）时会按槽位规则重新校验，
     * 探针只登记了能力、不在槽位标签里，读回来会被当成放错槽的东西丢掉，测不出「压制有没有影响存档」。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealKeepsStorage(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ServerPlayer clone = ProtectionFrameworkTests.realPlayer(helper, player.getUUID(), "ec5-seal-clone");
        Item belt = ECBaubles.THETA_BELT.get();
        String beltId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(belt).toString();
        try {
            equip(player, BELT, 0, new ItemStack(belt));
            tick(player);
            seal(helper, player);
            tick(player);
            tick(player);
            helper.assertTrue(realStack(player, BELT, 0).is(belt), "压制中真槽里还是那条腰带");
            helper.assertTrue(inventory(player).writeTag().toString().contains(beltId), "存档里有那条腰带");
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(clone, player, false));
            helper.assertTrue(realStack(clone, BELT, 0).is(belt), "复制玩家（keepInventory、从末地回来）后新玩家的饰品栏里有那条腰带");
        } finally {
            CurioSeal.releaseForTest(player);
            CurioSeal.releaseForTest(clone);
        }
        helper.succeed();
    }

    /** 死亡掉落：不开 keepInventory 时被压制的玩家死亡，掉落里有探针 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealKeepsDeathDrops(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            helper.assertFalse(helper.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY), "前提：测试世界没开 keepInventory");
            equip(player, BELT, 0, probe());
            tick(player);
            seal(helper, player);
            tick(player);
            List<ItemEntity> drops = new ArrayList<>();
            MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(player, player.damageSources().generic(), drops, 0, false));
            helper.assertTrue(drops.stream().anyMatch(entity -> entity.getItem().is(Items.NAUTILUS_SHELL)), "被压制玩家死亡时探针照常掉落");
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /** 正在死亡的人照样过滤（新旧两种查询接口都是）；墓碑类读 getCurios / getEquippedCurios，不受影响 */
    @GameTest(template = EMPTY, batch = BATCH)
    @SuppressWarnings({"deprecation", "removal"})
    public static void curioSealFiltersWhileDying(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            equip(player, BELT, 0, probe());
            seal(helper, player);
            helper.assertFalse(inventory(player).isEquipped(Items.NAUTILUS_SHELL), "前提：活着时过滤");
            player.setHealth(0F);
            helper.assertTrue(player.isDeadOrDying(), "前提：血量 0 算正在死亡");
            helper.assertFalse(inventory(player).isEquipped(Items.NAUTILUS_SHELL), "正在死亡时照样过滤");
            helper.assertTrue(CuriosApi.getCuriosHelper().findEquippedCurio(Items.NAUTILUS_SHELL, player).isEmpty(),
                    "正在死亡时旧接口 findEquippedCurio 也照样过滤");
            helper.assertTrue(realStack(player, BELT, 0).is(Items.NAUTILUS_SHELL), "真槽里还是那件（墓碑类读原始槽位拿得到）");
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /**
     * 「戴着就免死」的饰品（在 LivingDeathEvent 里用旧接口查，查到就取消死亡）：没被压制时救得下，被压制时救不下。
     * 临时挂的监听器只认这名测试玩家。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    @SuppressWarnings({"deprecation", "removal"})
    public static void curioSealBlocksDeathSaveCurio(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Consumer<LivingDeathEvent> deathSave = event -> {
            if (event.getEntity() == player && CuriosApi.getCuriosHelper().findEquippedCurio(Items.NAUTILUS_SHELL, player).isPresent()) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDeathEvent.class, deathSave);
        try {
            equip(player, BELT, 0, probe());
            player.setHealth(0F);
            LivingDeathEvent unsealed = new LivingDeathEvent(player, player.damageSources().generic());
            MinecraftForge.EVENT_BUS.post(unsealed);
            helper.assertTrue(unsealed.isCanceled(), "对照：没被压制时免死饰品救得下");
            player.setHealth(player.getMaxHealth());
            seal(helper, player);
            player.setHealth(0F);
            LivingDeathEvent sealed = new LivingDeathEvent(player, player.damageSources().generic());
            MinecraftForge.EVENT_BUS.post(sealed);
            helper.assertFalse(sealed.isCanceled(), "被压制时免死饰品救不下");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deathSave);
            player.setHealth(player.getMaxHealth());
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /** 豁免标签：豁免的饰品 tick 照跑、属性在、查询查得到、格子不算被压制 */
    @GameTest(template = EMPTY, batch = "curio_seal_exempt")
    public static void curioSealHonorsExemptTag(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        CurioSeal.setExemptForTest(Items.NAUTILUS_SHELL);
        try {
            double base = armor(player);
            equip(player, BELT, 0, probe());
            tick(player);
            seal(helper, player);
            int ticks = PROBE.ticks(player);
            tick(player);
            helper.assertTrue(PROBE.ticks(player) == ticks + 1, "豁免的饰品照常 tick");
            helper.assertTrue(PROBE.unequips(player) == 0, "豁免的饰品不会被卸下");
            helper.assertTrue(armor(player) == base + 4D, "豁免的饰品属性在");
            helper.assertTrue(inventory(player).isEquipped(Items.NAUTILUS_SHELL), "豁免的饰品查得到");
            helper.assertFalse(CurioSeal.isSealedStack(player, realStack(player, BELT, 0)), "豁免的饰品格子不算被压制");
        } finally {
            CurioSeal.clearTestOverrides();
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    // ================================================================ 13 ~ 16：本模组饰品、反重力板、注入、写保护

    /** 本模组饰品：CuriosCompat.has 真 → 压制后假；forEachEquipped 跳过；饰品栏里的灵魂碎片不再算进 canAfford；神秘饰品的 MRU 抗性归零 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealHidesOwnBaubles(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            ItemStack alpha = new ItemStack(ECBaubles.ALPHA_RING.get());
            ItemStack shard = new ItemStack(ECItems.SOUL_SHARD.get());
            shard.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(400));
            ItemStack accessory = new ItemStack(ECVoid.MYSTICAL_ACCESSORY.get());
            accessory.getOrCreateTag().putInt(MysticalAccessoryItem.TAG_TYPE, 0);
            accessory.getOrCreateTag().putFloat(MysticalAccessoryItem.key(MRUResistance.Type.MRU_CORRUPTION), 0.1F);
            equip(player, BELT, 0, alpha);
            equip(player, RING, 0, shard);
            equip(player, NECKLACE, 0, accessory);
            int[] visited = {0};
            CuriosCompat.forEachEquipped(player, stack -> visited[0]++);
            helper.assertTrue(CuriosCompat.has(player, ECBaubles.ALPHA_RING.get()) && visited[0] == 3
                    && MRUDrain.canAfford(player, ItemStack.EMPTY, 300)
                    && MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION) < 1F,
                    "前提：戴着 α、灵魂碎片付得起 300、神秘饰品有 MRU 腐化抗性");
            seal(helper, player);
            visited[0] = 0;
            CuriosCompat.forEachEquipped(player, stack -> visited[0]++);
            helper.assertFalse(CuriosCompat.has(player, ECBaubles.ALPHA_RING.get()), "压制后 CuriosCompat.has 为假");
            helper.assertTrue(visited[0] == 0, "压制后 forEachEquipped 跳过被压制的，实际 " + visited[0]);
            helper.assertFalse(MRUDrain.canAfford(player, ItemStack.EMPTY, 300), "饰品栏里的灵魂碎片压制期间不付电");
            helper.assertTrue(MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION) == 1F, "神秘饰品的抗性压制期间归零");
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /** 反重力板：戴上给飞行 → 压制后撤掉 → 解除后再给 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealRevokesBoardFlight(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            equip(player, BELT, 0, new ItemStack(ECComputer.COMPUTER_BOARD.get()));
            tick(player);
            helper.assertTrue(ECFlight.isGranted(player, ComputerBoardItem.FLIGHT_SOURCE), "前提：戴上反重力板给飞行");
            seal(helper, player);
            tick(player);
            helper.assertFalse(ECFlight.isGranted(player, ComputerBoardItem.FLIGHT_SOURCE), "压制后撤掉反重力板的飞行");
            unseal(helper, player);
            tick(player);
            helper.assertTrue(ECFlight.isGranted(player, ComputerBoardItem.FLIGHT_SOURCE), "解除后重新给飞行");
        } finally {
            CurioSeal.releaseForTest(player);
            ECFlight.revoke(player, ComputerBoardItem.FLIGHT_SOURCE);
        }
        helper.succeed();
    }

    /** 注入到位：五个 mixin、每个目标方法都调到本模组的处理方法（开发环境里有一处没打上就挂） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealMixinsApplied(GameTestHelper helper) {
        helper.assertTrue(CurioSealMixinStatus.gate() == CurioSealMixinStatus.Gate.ACCEPTED,
                "Curios 版本门应放行（5.14.x），实际 " + CurioSealMixinStatus.gate() + " " + CurioSealMixinStatus.curiosVersion());
        helper.assertTrue(CurioSealMixinStatus.allApplied(), "注入点应全部到位：" + CurioSealMixinStatus.problems());
        helper.succeed();
    }

    /** 视图写保护：对被压制且非空的格子写空不生效、抽不出东西；读出来是空的 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealViewRejectsEmptyWrites(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            equip(player, BELT, 0, probe());
            seal(helper, player);
            SealedCuriosView view = new SealedCuriosView(inventory(player));
            IDynamicStackHandler stacks = view.getCurios().get(BELT).getStacks();
            helper.assertTrue(stacks.getStackInSlot(0).isEmpty(), "视图里被压制的格子读出来是空的");
            stacks.setStackInSlot(0, ItemStack.EMPTY);
            helper.assertTrue(realStack(player, BELT, 0).is(Items.NAUTILUS_SHELL), "写空被拒，真物品还在");
            helper.assertTrue(stacks.extractItem(0, 1, false).isEmpty(), "视图里抽不出东西");
            helper.assertTrue(realStack(player, BELT, 0).is(Items.NAUTILUS_SHELL), "抽取被拒，真物品还在");
            helper.assertTrue(view.getCurios().get(BELT).getCosmeticStacks() == inventory(player).getCurios().get(BELT).getCosmeticStacks(),
                    "装饰槽不遮，直接是真的");
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    // ================================================================ 装备校验、槽位数改值、单格同步

    /**
     * 装备校验看真物品：「同种只许戴一件」的饰品，被压制时查询被过滤成空，不拦的话能多戴一件。被压制时发装备校验事件应得 DENY；
     * 饰品栏里没有同种时照样 DEFAULT（被压制时照样戴得上新的）。直接看事件结果，不用 isItemValid（探针不在槽位标签里，那条本来就是假）。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealEquipCheckSeesRealItems(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            SlotContext second = new SlotContext(RING, player, 1, false, true);
            ItemStack heart = new ItemStack(Items.HEART_OF_THE_SEA);
            equip(player, RING, 0, new ItemStack(Items.HEART_OF_THE_SEA));
            helper.assertFalse(CuriosApi.getCurio(heart).map(curio -> curio.canEquip(second)).orElse(true),
                    "前提：没压制时同种第二件 canEquip 为假");
            helper.assertTrue(equipCheck(heart, second) == Event.Result.DEFAULT, "前提：没压制时不改事件结果（由 Curios 自己调 canEquip）");
            seal(helper, player);
            helper.assertTrue(inventory(player).findFirstCurio(Items.HEART_OF_THE_SEA).isEmpty(),
                    "前提：压制中查询被过滤成空（不另外校验的话 canEquip 会放行）");
            helper.assertTrue(equipCheck(heart, second) == Event.Result.DENY, "被压制时同种第二件被拒");
            helper.assertTrue(inventory(player).findFirstCurio(Items.HEART_OF_THE_SEA).isEmpty(), "校验完查询照样过滤（作用域没残留）");
            equip(player, RING, 0, ItemStack.EMPTY);
            helper.assertTrue(equipCheck(heart, second) == Event.Result.DEFAULT, "被压制、饰品栏里没有同种时不拦");
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /**
     * 压制中同一格的饰品改了给的槽位数（同一个槽位 UUID，数量从 2 变 1）：对账先去旧值再补新值，槽数当刻跟着变；
     * 解除后 Curios 再加一遍也还是新值（不改的话 Curios 按 UUID 判相等，旧数量一直留着）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealReconcilesChangedSlotCount(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            int base = slots(player, RING);
            equip(player, BELT, 0, slotGiver(2));
            tick(player);
            tick(player);
            helper.assertTrue(slots(player, RING) == base + 2, "前提：给 2 个额外戒指槽，实际 " + slots(player, RING) + "（基础 " + base + "）");
            seal(helper, player);
            tick(player);
            equip(player, BELT, 0, slotGiver(1));
            tick(player);
            tick(player);
            helper.assertTrue(slots(player, RING) == base + 1, "压制中降成 +1，对账后跟着少一格，实际 " + slots(player, RING));
            unseal(helper, player);
            tick(player);
            tick(player);
            helper.assertTrue(slots(player, RING) == base + 1, "解除后仍是 +1，实际 " + slots(player, RING));
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /**
     * 同上，换成豁免的饰品：Curios 看得到它的变化，但「先去旧值」经遮罩视图被丢掉、只剩「后加」，不靠对账同样卡在旧值。
     * 改了豁免覆盖，单独一个 batch。
     */
    @GameTest(template = EMPTY, batch = "curio_seal_exempt_slots")
    public static void curioSealReconcilesExemptSlotCount(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        CurioSeal.setExemptForTest(Items.ECHO_SHARD);
        try {
            int base = slots(player, RING);
            equip(player, BELT, 0, slotGiver(2));
            tick(player);
            tick(player);
            helper.assertTrue(slots(player, RING) == base + 2, "前提：给 2 个额外戒指槽，实际 " + slots(player, RING) + "（基础 " + base + "）");
            seal(helper, player);
            tick(player);
            equip(player, BELT, 0, slotGiver(1));
            tick(player);
            tick(player);
            helper.assertTrue(slots(player, RING) == base + 1, "压制中豁免饰品降成 +1，槽数跟着少一格，实际 " + slots(player, RING));
            unseal(helper, player);
            tick(player);
            tick(player);
            helper.assertTrue(slots(player, RING) == base + 1, "解除后仍是 +1，实际 " + slots(player, RING));
        } finally {
            CurioSeal.clearTestOverrides();
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /**
     * 单格同步：压制期间 Curios 看不见真物品的变化，摘下 / 换上由对账发本模组的单格包（给本人和旁人）；
     * 没被压制时、压制开始那一刻、没再变时都不发
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealSyncsChangedSlots(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        List<S2CCurioSealSlotPacket> sent = S2CCurioSealSlotPacket.captureForTest(player.getUUID());
        try {
            equip(player, BELT, 0, probe());
            tick(player);
            helper.assertTrue(sent.isEmpty(), "没被压制时由 Curios 自己同步，不发，实际 " + sent.size());
            seal(helper, player);
            tick(player);
            helper.assertTrue(sent.isEmpty(), "压制开始那一刻不发（Curios 的单格包已经换回真物品），实际 " + sent.size());
            equip(player, BELT, 0, ItemStack.EMPTY);
            tick(player);
            helper.assertTrue(sent.size() == 1, "摘下当刻发一个，实际 " + sent.size());
            S2CCurioSealSlotPacket cleared = sent.get(0);
            helper.assertTrue(cleared.entityId() == player.getId() && BELT.equals(cleared.curioId()) && cleared.slot() == 0
                    && cleared.stack().isEmpty(), "包里是这名玩家腰带第 0 格、空，实际 " + cleared);
            tick(player);
            helper.assertTrue(sent.size() == 1, "没再变就不重发，实际 " + sent.size());
            equip(player, BELT, 0, new ItemStack(Items.GOLD_NUGGET));
            tick(player);
            helper.assertTrue(sent.size() == 2 && sent.get(1).stack().is(Items.GOLD_NUGGET), "换上新东西当刻发一个，带新物品");
            helper.assertTrue(sent.get(1).stack() != realStack(player, BELT, 0), "包里的是拷贝");
        } finally {
            S2CCurioSealSlotPacket.stopCapture(player.getUUID());
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    // ================================================================ 第十一批：和别的饰品模组的写法对照

    /** 这名玩家身上（背包 + 盔甲 + 副手 + 饰品栏真槽）一共有几件这种物品 */
    private static int countEverywhere(ServerPlayer player, Item item) {
        int total = player.getInventory().countItem(item);
        var equipped = inventory(player).getEquippedCurios();
        for (int i = 0; i < equipped.getSlots(); i++) {
            ItemStack stack = equipped.getStackInSlot(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * 「戴着就免死」的另一种写法：在 LivingDeathEvent 里查 {@code Inventory.contains(new ItemStack(物品))}（Curios 让它顺带认饰品栏）。
     * 活着时「背包里有没有」照样认得被压制的饰品；正在死亡时这条也按压制过滤，被压制的人救不下；没被压制时救得下（对照）；
     * 同一件放在背包里照样算（只少算饰品栏里被压制的那件）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealFiltersContainsWhileDying(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Consumer<LivingDeathEvent> deathSave = event -> {
            if (event.getEntity() == player && player.getInventory().contains(probe())) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDeathEvent.class, deathSave);
        try {
            equip(player, BELT, 0, probe());
            player.setHealth(0F);
            LivingDeathEvent unsealed = new LivingDeathEvent(player, player.damageSources().generic());
            MinecraftForge.EVENT_BUS.post(unsealed);
            helper.assertTrue(unsealed.isCanceled(), "对照：没被压制时靠 Inventory.contains 查到的免死饰品救得下");
            player.setHealth(player.getMaxHealth());
            seal(helper, player);
            helper.assertTrue(player.getInventory().contains(probe()), "活着时「背包里有没有」照样认得被压制的饰品");
            player.setHealth(0F);
            helper.assertFalse(player.getInventory().contains(probe()), "正在死亡时 Inventory.contains 不再认被压制的饰品");
            helper.assertFalse(player.getInventory().hasAnyMatching(stack -> stack.is(Items.NAUTILUS_SHELL)),
                    "正在死亡时 hasAnyMatching 也不再认被压制的饰品");
            LivingDeathEvent sealed = new LivingDeathEvent(player, player.damageSources().generic());
            MinecraftForge.EVENT_BUS.post(sealed);
            helper.assertFalse(sealed.isCanceled(), "被压制时救不下");
            helper.assertTrue(realStack(player, BELT, 0).is(Items.NAUTILUS_SHELL), "真槽里还是那件（墓碑类读原始槽位拿得到）");
            player.getInventory().add(probe());
            helper.assertTrue(player.getInventory().contains(probe()), "同一件放在背包里照样算");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deathSave);
            player.setHealth(player.getMaxHealth());
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /**
     * 别的模组在压制期间直接改真槽（套装快捷切换、把饰品原地换成另一件这类写法：拿 {@code getStacksHandler(…).getStacks()} 直接
     * {@code setStackInSlot}，不经过遮罩视图）：东西不复制、不丢；压制期间换进来的不生效（不 onEquip、不加属性），换出去的也不再 onUnequip
     * （压制开始时已经撤过一次）；额外槽位按新那件保留；解除后只给新那件 onEquip 一次、属性只加一份
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealForeignSwapWhileSealed(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            double base = armor(player);
            int baseRings = slots(player, RING);
            equip(player, BELT, 0, probe());
            tick(player);
            seal(helper, player);
            tick(player);
            helper.assertTrue(PROBE.equips(player) == 1 && PROBE.unequips(player) == 1 && armor(player) == base,
                    "前提：戴上一次、压制开始撤过一次、属性已去掉，实际 equip " + PROBE.equips(player) + " unequip " + PROBE.unequips(player));
            IDynamicStackHandler stacks = inventory(player).getStacksHandler(BELT).orElseThrow().getStacks();
            ItemStack old = stacks.getStackInSlot(0);
            ItemStack swapped = probe();
            swapped.getOrCreateTag().putBoolean("ec5_swapped", true);
            stacks.setStackInSlot(0, swapped);
            player.getInventory().add(old);
            tick(player);
            tick(player);
            helper.assertTrue(PROBE.equips(player) == 1 && PROBE.unequips(player) == 1,
                    "压制期间换进来的不 onEquip、换出去的不再 onUnequip，实际 equip " + PROBE.equips(player) + " unequip " + PROBE.unequips(player));
            helper.assertTrue(armor(player) == base, "压制期间换进来的也不加属性，实际 " + armor(player));
            helper.assertTrue(slots(player, RING) == baseRings + 1, "压制期间额外槽位按新那件保留，实际 " + slots(player, RING));
            helper.assertTrue(countEverywhere(player, Items.NAUTILUS_SHELL) == 2,
                    "一件在饰品栏、一件在背包，不复制不丢，实际 " + countEverywhere(player, Items.NAUTILUS_SHELL));
            unseal(helper, player);
            tick(player);
            helper.assertTrue(PROBE.equips(player) == 2 && PROBE.unequips(player) == 1,
                    "解除后只给新那件 onEquip 一次，实际 equip " + PROBE.equips(player) + " unequip " + PROBE.unequips(player));
            helper.assertTrue(armor(player) == base + 4D, "解除后属性只加一份，实际 " + armor(player) + "（基础 " + base + "）");
            helper.assertTrue(slots(player, RING) == baseRings + 1, "解除后额外槽位还是一个，实际 " + slots(player, RING));
            ItemStack now = realStack(player, BELT, 0);
            helper.assertTrue(now.getTag() != null && now.getTag().getBoolean("ec5_swapped"), "饰品栏里是换进来的那件");
            helper.assertTrue(countEverywhere(player, Items.NAUTILUS_SHELL) == 2, "解除后还是两件");
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /**
     * 别的模组在压制期间往空的功能槽里直接塞东西（物品栏里出现就自动戴上、开局发装备直接戴上这类写法，绕过 Curios 的装备校验直接
     * {@code setStackInSlot}）：东西照样放进去、不丢；压制期间不生效；额外槽位按新东西对账给上；本人和旁人收到一个单格同步包
     * （Curios 比对的是遮罩视图，自己不发）；解除后 onEquip 一次、属性加上，额外槽位不重复
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealForeignInsertWhileSealed(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        List<S2CCurioSealSlotPacket> sent = S2CCurioSealSlotPacket.captureForTest(player.getUUID());
        try {
            double base = armor(player);
            int baseRings = slots(player, RING);
            seal(helper, player);
            tick(player);
            inventory(player).getStacksHandler(BELT).orElseThrow().getStacks().setStackInSlot(0, probe());
            tick(player);
            tick(player);
            helper.assertTrue(realStack(player, BELT, 0).is(Items.NAUTILUS_SHELL), "塞进去的东西在真槽里");
            helper.assertTrue(PROBE.equips(player) == 0 && PROBE.ticks(player) == 0 && armor(player) == base,
                    "压制期间塞进来的不生效，实际 equip " + PROBE.equips(player) + " tick " + PROBE.ticks(player) + " 护甲 " + armor(player));
            helper.assertTrue(slots(player, RING) == baseRings + 1, "额外槽位按新东西给上，实际 " + slots(player, RING));
            helper.assertTrue(sent.size() == 1 && sent.get(0).stack().is(Items.NAUTILUS_SHELL) && BELT.equals(sent.get(0).curioId()),
                    "塞进来那一刻发一个单格包（带新东西），实际 " + sent.size());
            unseal(helper, player);
            tick(player);
            helper.assertTrue(PROBE.equips(player) == 1, "解除后 onEquip 一次，实际 " + PROBE.equips(player));
            helper.assertTrue(armor(player) == base + 4D, "解除后属性加上，实际 " + armor(player) + "（基础 " + base + "）");
            helper.assertTrue(slots(player, RING) == baseRings + 1, "解除后额外槽位不重复，实际 " + slots(player, RING));
        } finally {
            S2CCurioSealSlotPacket.stopCapture(player.getUUID());
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }

    /**
     * 「套装」类饰品（onEquip / onUnequip 里用 findCurios 数同种件数）：压制开始时每件的 onUnequip 都数到 0（套装效果跟着撤）；
     * 解除时每件的 onEquip 都数到全部件数（解除那一刻玩家已经不在压制表里，查询不再过滤，套装件数一次算对，不会少算成半套）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void curioSealSetPiecesCountOnUnseal(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            equip(player, BELT, 0, new ItemStack(Items.PRISMARINE_SHARD));
            equip(player, NECKLACE, 0, new ItemStack(Items.PRISMARINE_SHARD));
            tick(player);
            helper.assertTrue(SET_PROBE.equips(player).size() == 2, "前提：两件各戴上一次，实际 " + SET_PROBE.equips(player));
            SET_PROBE.clear(player);
            seal(helper, player);
            tick(player);
            List<Integer> off = List.copyOf(SET_PROBE.unequips(player));
            helper.assertTrue(off.equals(List.of(0, 0)), "压制开始时两件的 onUnequip 都数到 0 件，实际 " + off);
            unseal(helper, player);
            tick(player);
            List<Integer> on = List.copyOf(SET_PROBE.equips(player));
            helper.assertTrue(on.equals(List.of(2, 2)), "解除时两件的 onEquip 都数到 2 件，实际 " + on);
        } finally {
            CurioSeal.releaseForTest(player);
        }
        helper.succeed();
    }
}
