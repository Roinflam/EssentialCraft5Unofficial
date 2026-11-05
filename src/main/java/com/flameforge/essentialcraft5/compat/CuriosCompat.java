package com.flameforge.essentialcraft5.compat;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Curios 饰品栏。原作用 Baubles（固定 2 戒指、1 项链、1 腰带），这里全部改成 Curios。
 * Curios 在 mods.toml 里是必需依赖，所以不做「没装」的判断。
 * <p>
 * <b>槽位</b>：本模组只在 {@code data/essentialcraft5/curios/entities/essentialcraft5.json} 里给玩家开启槽位类型
 * {@value #RING}、{@value #NECKLACE}、{@value #BELT}（原作 Baubles 的 RING / AMULET / BELT）。
 * 戒指槽另由 {@code data/essentialcraft5/curios/slots/ring.json} 给到 2 格（原作 Baubles 固定 2 个戒指位；Curios 自己的 ring 不写 size，默认 1 格）。
 * Curios 合并各数据包的槽位定义时 {@code SET} 取最大值（{@code SlotType.Builder.size}），别的模组 / 整合包给得更多就照它们的，不会被压回 2；
 * 写了 {@code replace} 的数据包或 Curios 自己的配置照样能改。项链、腰带不改（Curios 默认各 1 格，和原作一样）。
 * 物品进哪种槽靠物品标签：{@code ItemSpec…tags(ECTags.CURIOS_NECKLACE)}（curios:ring / necklace / belt）。
 * <p>
 * <b>查「戴着没有」（第三批 G3）</b>：热事件（受伤、击杀、挖方块、每 tick）里一律用下面这几个，走每玩家缓存，不遍历 Curios：
 * <pre>
 * if (CuriosCompat.has(player, ECBaubles.OMEGA_RING.get())) { … }          // 同种只算一件
 * ItemStack wings = CuriosCompat.find(player, ECCharms.MAGICAL_WINGS.get()); // 要改它的 NBT / 扣它的 MRU 时拿栈本身
 * boolean resistant = CuriosCompat.has(player, WIND_RESISTANT_TAG);          // 按物品标签
 * </pre>
 * 缓存按玩家对象存（服务端、客户端各一份），在 {@link CurioChangeEvent}（Curios 每 tick 比对槽位，任何变化含 NBT 都会发）、登录、重生、换维度、登出时失效，
 * 另外服务端最长 {@value #MAX_CACHE_AGE} tick 强制重建一次（防别的模组绕过 Curios 改槽位）；客户端没有变化事件，缓存只在同一 tick 内有效。
 * 缓存里<b>不含</b>被配置禁用的物品（见下），也不含装饰槽里的物品。非玩家实体不缓存（每次现查）。
 * {@link #forEachEquipped} 现查、含被禁用的物品，给 MRU 扣费、UBMRU 修正、MRU 抗性这些一、二批的老调用用。
 * <p>
 * <b>饰品封禁场</b>（第十批，{@link CurioSeal}）：被压制的人饰品栏里的东西（豁免标签 {@code essentialcraft5:curio_seal_exempt} 以外）
 * 缓存和 {@link #forEachEquipped} 都当不存在；压制状态翻转时 CurioSeal 让两端的缓存作废。存取规则 {@link #onlyOneEquipped} 照读原始槽位。
 * <p>
 * <b>同种只生效一件</b>（policy §4.2）：饰品类实现 Curios 的 {@code ICurioItem}，在 canEquip 里拒第二件：
 * <pre>
 * &#64;Override
 * public boolean canEquip(SlotContext slotContext, ItemStack stack) {
 *     return CuriosCompat.onlyOneEquipped(slotContext, stack);
 * }
 * </pre>
 * 查询本身也只认一件（{@link #has} 是布尔），所以就算用指令塞进两件也不会叠加。
 * <p>
 * <b>槽位属性修饰符</b>（魔力之翼移速 +0.1 这类，不要每 tick 加属性）：覆盖 {@code ICurioItem#getAttributeModifiers(SlotContext, UUID, ItemStack)}，
 * 用 Curios 传进来的 uuid（按槽位固定，戴上时 Curios 自动加、摘下自动去，提示行也自动显示）：
 * <pre>
 * &#64;Override
 * public Multimap&lt;Attribute, AttributeModifier&gt; getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
 *     // 原作 MiscUtils.applyPlayerModifier(MOVEMENT_SPEED, "EC300", 0.1, …) 每 tick 调一次
 *     return ImmutableMultimap.of(Attributes.MOVEMENT_SPEED,
 *             new AttributeModifier(uuid, "essentialcraft5:magical_wings", 0.1D, AttributeModifier.Operation.ADDITION));
 * }
 * </pre>
 * 不要每次 {@code UUID.randomUUID()}（原作 ξ 每次命中 new 一个随机 UUID 的修饰符，会越叠越多）。
 * <p>
 * <b>配置 {@code general.baubles.disabled}</b>（SERVER，注册名列表，默认空）：列进去的饰品戴着不生效（{@link #has} / {@link #find} 当它不存在）、
 * 也不进地牢池（战利品条件 {@code essentialcraft5:not_disabled}，见 loot/NotDisabledCondition）。PvP 服可以关 ζ、σ。
 * 不走缓存的效果（物品自己的 curioTick、{@code IUBMRUGainModifyHandler}）要自己查 {@link #isDisabled(ItemStack)}。
 * 提示行可以加 {@link #disabledTooltip()}（服务端配置会同步到客户端）。
 */
public final class CuriosCompat {

    public static final String RING = "ring";
    public static final String NECKLACE = "necklace";
    public static final String BELT = "belt";

    /** 服务端缓存最长保留多少 tick（CurioChangeEvent 漏掉的变化最多晚这么久生效） */
    public static final int MAX_CACHE_AGE = 20;

    public static final String DISABLED_TOOLTIP = "tooltip.essentialcraft5.curios.disabled";
    public static final String ONLY_ONE_TOOLTIP = "tooltip.essentialcraft5.curios.only_one";

    /** 一个玩家某一刻戴着的（生效的）饰品。stacks 是 Curios 槽里的栈本身（不是拷贝），改它就是改槽里的物品 */
    private record Snapshot(long builtAt, int generation, List<ItemStack> stacks, Map<Item, ItemStack> byItem) {
    }

    /** 服务端线程和客户端线程各用一份，不共享（单人游戏两边同时在跑） */
    private static final Map<Player, Snapshot> SERVER_CACHE = new WeakHashMap<>();
    private static final Map<Player, Snapshot> CLIENT_CACHE = new WeakHashMap<>();

    /** 配置重载时加一，旧缓存随之作废（配置事件可能在别的线程发，所以只动这个计数） */
    private static volatile int configGeneration;
    private static boolean initialized;

    private CuriosCompat() {
    }

    /** 登记配置（ECRegistries.register 调，必须在 ECConfig.register 之前） */
    public static void init() {
        if (!initialized) {
            initialized = true;
            ECConfig.general(Config::define);
        }
    }

    // ---------------------------------------------------------------- 现查（老接口）

    /**
     * 现查身上所有饰品槽（不含装饰槽、含被禁用的；不含被饰品封禁场压制的，见 {@link CurioSeal#isSealedStack}）。
     * 热事件里不要用它查「戴没戴某件」，用 {@link #has}
     */
    public static void forEachEquipped(LivingEntity entity, Consumer<ItemStack> action) {
        CuriosApi.getCuriosInventory(entity).ifPresent(inventory -> {
            IItemHandlerModifiable equipped = inventory.getEquippedCurios();
            for (int i = 0; i < equipped.getSlots(); i++) {
                ItemStack stack = equipped.getStackInSlot(i);
                // 被压制的饰品当不存在：储存物品不付电、MRU 抗性 / UBMRU 修正不算
                if (!stack.isEmpty() && !CurioSeal.isSealedStack(entity, stack)) {
                    action.accept(stack);
                }
            }
        });
    }

    // ---------------------------------------------------------------- 缓存查询

    /** 生效的饰品（不含被禁用的、装饰槽的），只读；玩家走缓存 */
    public static List<ItemStack> equipped(LivingEntity entity) {
        return snapshot(entity).stacks();
    }

    /** 戴着这种物品（且没被禁用） */
    public static boolean has(LivingEntity entity, Item item) {
        return !find(entity, item).isEmpty();
    }

    /** 戴着的第一件这种物品（Curios 槽里的栈本身），没有返回空栈 */
    public static ItemStack find(LivingEntity entity, Item item) {
        ItemStack stack = snapshot(entity).byItem().get(item);
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    /** 戴着带这个标签的物品 */
    public static boolean has(LivingEntity entity, TagKey<Item> tag) {
        return !find(entity, tag).isEmpty();
    }

    /** 戴着的第一件带这个标签的物品，没有返回空栈 */
    public static ItemStack find(LivingEntity entity, TagKey<Item> tag) {
        for (ItemStack stack : snapshot(entity).stacks()) {
            if (!stack.isEmpty() && stack.is(tag)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 让这个玩家的缓存作废（本类已经监听了换装、登录、重生、换维度；别的地方直接改了 Curios 槽可以手动调） */
    public static void invalidate(Player player) {
        (player.level().isClientSide ? CLIENT_CACHE : SERVER_CACHE).remove(player);
    }

    private static Snapshot snapshot(LivingEntity entity) {
        long now = entity.level().getGameTime();
        if (!(entity instanceof Player player)) {
            return build(entity, now);
        }
        boolean client = player.level().isClientSide;
        Map<Player, Snapshot> cache = client ? CLIENT_CACHE : SERVER_CACHE;
        Snapshot snapshot = cache.get(player);
        if (snapshot == null || !fresh(snapshot, now, client)) {
            snapshot = build(player, now);
            cache.put(player, snapshot);
        }
        return snapshot;
    }

    private static boolean fresh(Snapshot snapshot, long now, boolean client) {
        long age = now - snapshot.builtAt();
        return snapshot.generation() == configGeneration && age >= 0 && age < (client ? 1 : MAX_CACHE_AGE);
    }

    private static Snapshot build(LivingEntity entity, long now) {
        int generation = configGeneration;
        List<ItemStack> stacks = new ArrayList<>();
        Map<Item, ItemStack> byItem = new IdentityHashMap<>();
        forEachEquipped(entity, stack -> {
            if (!isDisabled(stack.getItem())) {
                stacks.add(stack);
                byItem.putIfAbsent(stack.getItem(), stack);
            }
        });
        return new Snapshot(now, generation, Collections.unmodifiableList(stacks), Collections.unmodifiableMap(byItem));
    }

    // ---------------------------------------------------------------- 同种只生效一件

    /**
     * 给 {@code ICurioItem#canEquip} 用：除了 slotContext 这一格，身上别的饰品槽里已经有同一种物品就返回 false。
     * 装饰槽（cosmetic）不生效，总是放行；也不看装饰槽里的。
     */
    public static boolean onlyOneEquipped(SlotContext slotContext, ItemStack stack) {
        Item item = stack.getItem();
        return onlyOneEquipped(slotContext, other -> other.is(item));
    }

    /** 同上，「同一种」由 sameKind 判断（比如一组互斥的饰品共用一个标签：{@code other -> other.is(TAG)}） */
    public static boolean onlyOneEquipped(SlotContext slotContext, Predicate<ItemStack> sameKind) {
        LivingEntity entity = slotContext.entity();
        if (slotContext.cosmetic() || entity == null) {
            return true;
        }
        return CuriosApi.getCuriosInventory(entity).map(inventory -> {
            for (Map.Entry<String, ICurioStacksHandler> entry : inventory.getCurios().entrySet()) {
                IDynamicStackHandler stacks = entry.getValue().getStacks();
                boolean sameType = entry.getKey().equals(slotContext.identifier());
                for (int i = 0; i < stacks.getSlots(); i++) {
                    if (sameType && i == slotContext.index()) {
                        // 要放进去的这一格（换同种的另一件、Curios 读档时逐格校验）不算
                        continue;
                    }
                    ItemStack other = stacks.getStackInSlot(i);
                    if (!other.isEmpty() && sameKind.test(other)) {
                        return false;
                    }
                }
            }
            return true;
        }).orElse(true);
    }

    // ---------------------------------------------------------------- 配置：禁用的饰品

    public static boolean isDisabled(ItemStack stack) {
        return !stack.isEmpty() && isDisabled(stack.getItem());
    }

    public static boolean isDisabled(Item item) {
        Set<ResourceLocation> disabled = disabledIds();
        if (disabled.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null && disabled.contains(id);
    }

    public static boolean isDisabled(ResourceLocation itemId) {
        return disabledIds().contains(itemId);
    }

    /** 红色「已被服务器禁用」提示行 */
    public static Component disabledTooltip() {
        return Component.translatable(DISABLED_TOOLTIP).withStyle(ChatFormatting.RED);
    }

    /** 灰色「同种只有一件生效」提示行 */
    public static Component onlyOneTooltip() {
        return Component.translatable(ONLY_ONE_TOOLTIP).withStyle(ChatFormatting.GRAY);
    }

    @Nullable
    private static List<? extends String> cachedList;
    private static Set<ResourceLocation> cachedSet = Set.of();

    /** 配置里禁用的注册名（配置没加载时用默认值；列表对象换了才重新解析） */
    static synchronized Set<ResourceLocation> disabledIds() {
        if (Config.testDisabledOverride != null) {
            return Config.testDisabledOverride;
        }
        if (Config.disabled == null) {
            return Set.of();
        }
        List<? extends String> list;
        try {
            list = Config.disabled.get();
        } catch (IllegalStateException e) {
            list = Config.disabled.getDefault();
        }
        if (list != cachedList) {
            Set<ResourceLocation> set = new HashSet<>();
            for (String s : list) {
                ResourceLocation id = ResourceLocation.tryParse(s);
                if (id != null) {
                    set.add(id);
                }
            }
            cachedList = list;
            cachedSet = Set.copyOf(set);
        }
        return cachedSet;
    }

    /** SERVER 配置 general.baubles 小节 */
    public static final class Config {

        @Nullable
        static ForgeConfigSpec.ConfigValue<List<? extends String>> disabled;

        private Config() {
        }

        static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Special baubles (Curios items of this mod).", "特殊饰品（本模组的 Curios 饰品）").push("baubles");
            disabled = builder.comment(
                            "Item ids listed here have no effect when worn and are not generated in dungeon chests.",
                            "Useful on PvP servers, e.g. [\"essentialcraft5:zeta_amulet\", \"essentialcraft5:sigma_ring\"].",
                            "列在这里的饰品（注册名）戴着不生效，也不会出现在地牢箱子里。PvP 服可以关 ζ、σ")
                    .defineListAllowEmpty("disabled", List.of(), o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
            builder.pop();
        }

        /*
         * GameTest 用的内存覆盖值（null = 按配置）。不用 ConfigValue.set：服务端配置会自动存盘，
         * Forge 的文件监视器随后在后台重读整份文件，可能把测试刚设的值冲掉（见 CorruptionEffects.setTestOverrides）。
         */
        @Nullable
        static volatile Set<ResourceLocation> testDisabledOverride;

        /** GameTest 用：临时覆盖禁用列表（传 null 恢复按配置），测完必须在 finally 里恢复 */
        public static void setDisabledForTest(@Nullable List<String> ids) {
            if (ids == null) {
                testDisabledOverride = null;
            } else {
                Set<ResourceLocation> set = new HashSet<>();
                for (String s : ids) {
                    ResourceLocation id = ResourceLocation.tryParse(s);
                    if (id != null) {
                        set.add(id);
                    }
                }
                testDisabledOverride = Set.copyOf(set);
            }
            configGeneration++;
        }
    }

    // ---------------------------------------------------------------- 缓存失效

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class CacheEvents {

        private CacheEvents() {
        }

        /** Curios 只在服务端发（每 tick 比对槽位，换了物品或 NBT 变了都会发） */
        @SubscribeEvent
        public static void onCurioChange(CurioChangeEvent event) {
            if (event.getEntity() instanceof Player player) {
                invalidate(player);
            }
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            invalidate(event.getEntity());
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            invalidate(event.getEntity());
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
            invalidate(event.getEntity());
        }

        @SubscribeEvent
        public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            invalidate(event.getEntity());
        }
    }

    /** 配置加载 / 重载（模组总线；可能在文件监视线程上发，只改计数） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ConfigEvents {

        private ConfigEvents() {
        }

        @SubscribeEvent
        public static void onLoad(ModConfigEvent.Loading event) {
            if (EssentialCraft5.MODID.equals(event.getConfig().getModId())) {
                configGeneration++;
            }
        }

        @SubscribeEvent
        public static void onReload(ModConfigEvent.Reloading event) {
            if (EssentialCraft5.MODID.equals(event.getConfig().getModId())) {
                configGeneration++;
            }
        }
    }
}
