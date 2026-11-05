package com.flameforge.essentialcraft5.compat.curio;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.event.CurioSealEvent;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.network.S2CCurioSealPacket;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.common.util.NonNullFunction;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.event.CurioEquipEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 饰品压制核心（tools/plan/batch10.md §4.1）：让一名玩家饰品栏里的东西「相当于不存在」，存储一格不动。
 * <p>
 * <b>怎么压</b>（三层，注入点见 {@code mixin/curios/}，只对 Curios 5.14.x 打补丁）：
 * <ol>
 *   <li>Curios 让饰品生效的那几处（每刻 tick、末影人面具、经验修补、挖矿经验、猪灵 / 细雪 / 免冻钩子）拿到的是遮罩视图
 *       {@link SealedCuriosView}：功能槽读成空。Curios 每刻照常比对，看到「有 → 空」就自己去属性修饰符、调 onUnequip、
 *       发 CurioChangeEvent，之后不再 curioTick；解除时反过来走一遍「戴上」。</li>
 *   <li>查询接口（findFirstCurio / findCurios / findCurio / isEquipped / 时运 / 抢夺 / 旧接口 findEquippedCurio）在返回时过滤
 *       （{@link #filterFirst} 等）。只在「背包里有没有」那三处钩子里（{@link #unfilteredFn}，持有者正在死亡时除外）和装备校验
 *       （{@link CurioSealEvents#onCurioSealEquipCheck}）里不过滤；持有者死亡时照样过滤（「戴着就免死」的饰品在 LivingDeathEvent 里查，
 *       被压制的人不能被它救下；墓碑类读 {@code getCurios()} / {@code getEquippedCurios()}，不受影响）。</li>
 *   <li>单格同步包：压制开始那一刻 Curios 会把空栈同步出去，在包构造完时换回真物品（{@link #realStackForSync}），客户端的饰品栏不会被刷空。</li>
 * </ol>
 * 本模组自己的饰品走 {@link CuriosCompat} 的缓存，那里另外跳过 {@link #isSealedStack} 的。饰品给的额外槽位压制期间保留，
 * 由 {@link SealSlotKeeper} 按真物品对账（压制期间真物品变了的格子也由它发单格包给旁人）。绝不拦：死亡掉落、keepInventory、
 * 界面里挪物品、右键装备、存档、整包同步、{@code getEquippedCurios()} / {@code getCurios()}；装备时的 {@code canEquip}
 * （「同种只许戴一件」这类存取规则）看真物品，被压制时也不能借过滤多戴一件。
 * <p>
 * <b>压制表</b>（服务端，只在服务端线程读写）：每个被压制的人一张「来源 UUID → 到期刻」，任一来源没到期就算被压制；
 * 被压制后最少保持 {@code general.computer_seal.min_hold_ticks}；时钟是 {@link #now}（{@code server.getTickCount()}，不受 /time 影响）。
 * 状态只在 {@code ServerTickEvent} 末尾结算（{@link #resolve}）时翻转：翻转时给本人和旁观者发 {@link S2CCurioSealPacket}、
 * 发 {@link CurioSealEvent}、让 CuriosCompat 的缓存作废。下线时作为目标、作为来源的记录都删掉；换维度当刻解除；
 * 死亡期间不解除（免得死亡动画里饰品重新生效一次），重生是新对象，等下一次评估。
 * <p>
 * <b>热路径</b>：没人被压制时 mixin 里读一个 volatile 集合就返回，不分配；有人被压制时每个活体每刻多一次集合查找。
 * <p>
 * 用法（封禁场）：每次评估对要压制的人 {@code CurioSeal.hold(目标, 穿戴者, CurioSeal.now(server) + 30)}；关场、下线 {@link #drop}；
 * 先看 {@link #availability()} 是不是 AVAILABLE。客户端判断用 {@link #isSealed} / {@link #isSealedStack}，状态来自 {@link CurioSealClientState}。
 */
public final class CurioSeal {

    /** 封禁场现在能不能用 */
    public enum Availability {
        /** 能用 */
        AVAILABLE,
        /** 配置 {@code general.computer_seal.enabled} 关了 */
        DISABLED,
        /** Curios 不是 5.14.x，没打补丁 */
        CURIOS_VERSION,
        /** 注入点没全部到位（这份 Curios 的实现和核对过的 5.14.1 对不上，或者本模组的混入配置整个没加载） */
        MIXIN_MISSING
    }

    /**
     * 豁免标签：里面的物品戴着照常生效、格子不算被压制。默认带一枚戴着就带诅咒的戒指（被封等于解除诅咒），以及别的模组里能增加饰品栏位的物品（腰带、手套、披风之类，
     * 被遮罩成空会触发它们自己的卸下逻辑，有的会连带收回槽位），都是可选条目；整合包可以用数据包追加
     */
    public static final TagKey<Item> EXEMPT = ECTags.modItem("curio_seal_exempt");

    private static final Logger LOGGER = LoggerFactory.getLogger("essentialcraft5/curio_seal");

    /** 一个被压制（或登记了来源、正等结算）的人 */
    private static final class Entry {
        final ServerPlayer player;
        /** 来源 → 到期刻（含） */
        final Map<UUID, Long> sources = new LinkedHashMap<>();
        /** 来源 → 名字（提示用） */
        final Map<UUID, String> names = new LinkedHashMap<>();
        boolean sealed;
        long sealedAt;
        boolean namesDirty;
        /** 上次发给本人的名字和余数（去掉自己以后的；没变就不再发） */
        List<String> sentNames = List.of();
        int sentMore;

        Entry(ServerPlayer player) {
            this.player = player;
        }
    }

    /** 服务端压制表（只在服务端线程读写） */
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    /**
     * 服务端正被压制的玩家对象（写时复制，按对象比）。Entity 的 equals / hashCode 按实体 id，重生后的新对象和旧的 id 相同，
     * 普通 HashSet 会把新对象也当成被压制，所以用 IdentityHashMap 撑的集合
     */
    private static volatile Set<Player> sealedServer = Set.of();

    /** mixin 的遮罩作用域（只在服务端线程设）：同步包修正认它 */
    private record Scope(Thread thread, int entityId, ICuriosItemHandler real, @Nullable Scope previous) {
    }

    @Nullable
    private static Scope scope;
    /** 「本次查询不过滤」的嵌套深度（每个线程一份） */
    private static final ThreadLocal<int[]> UNFILTERED = ThreadLocal.withInitial(() -> new int[1]);

    @Nullable
    private static volatile Set<Item> testExempt;

    private CurioSeal() {
    }

    // ================================================================== 状态查询（两端）

    /** 配置开关 + 版本门 + 注入核对 */
    public static Availability availability() {
        if (!CurioSealConfig.values().enabled()) {
            return Availability.DISABLED;
        }
        CurioSealMixinStatus.Gate gate = CurioSealMixinStatus.gate();
        if (gate == CurioSealMixinStatus.Gate.UNKNOWN) {
            // 版本门一次都没判过 = 混入配置整个没加载（插件没跑），不是版本问题。不另加枚举值：序号会进终端包、客户端按序号查原因
            return Availability.MIXIN_MISSING;
        }
        if (gate != CurioSealMixinStatus.Gate.ACCEPTED) {
            return Availability.CURIOS_VERSION;
        }
        if (!CurioSealMixinStatus.allApplied()) {
            return Availability.MIXIN_MISSING;
        }
        return Availability.AVAILABLE;
    }

    /** 两端都没人被压制（热路径先看这个） */
    public static boolean noOneSealed() {
        return sealedServer.isEmpty() && CurioSealClientState.isEmpty();
    }

    /** 这个实体的饰品现在被压制。服务端看压制表，客户端看收到的状态包；不是玩家一律 false */
    public static boolean isSealed(@Nullable LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (player.level().isClientSide) {
            return !CurioSealClientState.isEmpty() && CurioSealClientState.isSealed(player.getId());
        }
        Set<Player> sealed = sealedServer;
        return !sealed.isEmpty() && sealed.contains(player);
    }

    /** 这件物品在豁免标签里（或测试覆盖里） */
    public static boolean isExempt(ItemStack stack) {
        Set<Item> override = testExempt;
        return override != null && override.contains(stack.getItem()) || stack.is(EXEMPT);
    }

    /** 这件饰品被压制：非空、持有者被压制、不在豁免标签里 */
    public static boolean isSealedStack(@Nullable LivingEntity entity, ItemStack stack) {
        return !stack.isEmpty() && !noOneSealed() && isSealed(entity) && !isExempt(stack);
    }

    /** 压制表的时钟（服务器开机以来的刻数，不受 /time 影响）；{@link #hold} 的到期刻按它算 */
    public static long now(MinecraftServer server) {
        return server.getTickCount();
    }

    // ================================================================== 压制表（服务端）

    /**
     * 登记 / 续期一个来源：source 到 untilTick（含）为止压制 target，同一来源再调就覆盖到期刻。
     * 状态在这一刻末尾的 {@link #resolve} 里才翻转。功能不可用、target 是假玩家时什么都不做。
     */
    public static void hold(ServerPlayer target, ServerPlayer source, long untilTick) {
        if (target instanceof FakePlayer || availability() != Availability.AVAILABLE) {
            return;
        }
        Entry entry = ENTRIES.get(target.getUUID());
        if (entry != null && entry.player != target) {
            // 重生 / 重登后的新对象：旧记录作废，新对象等这次结算
            releaseEntry(entry, null);
            entry = null;
        }
        if (entry == null) {
            entry = new Entry(target);
            ENTRIES.put(target.getUUID(), entry);
        }
        entry.sources.put(source.getUUID(), untilTick);
        String name = source.getGameProfile().getName();
        if (!name.equals(entry.names.put(source.getUUID(), name))) {
            entry.namesDirty = true;
        }
    }

    /** 来源主动撤（关场、下线、换维度）：仍受最少保持时间限制 */
    public static void drop(ServerPlayer target, UUID source) {
        Entry entry = ENTRIES.get(target.getUUID());
        if (entry != null && entry.player == target) {
            entry.sources.remove(source);
            if (entry.names.remove(source) != null) {
                entry.namesDirty = true;
            }
        }
    }

    /** 这个来源从所有人身上撤掉（穿戴者下线、死亡、换维度时封禁场调；仍受最少保持时间限制） */
    public static void dropEverywhere(UUID source) {
        for (Entry entry : ENTRIES.values()) {
            entry.sources.remove(source);
            if (entry.names.remove(source) != null) {
                entry.namesDirty = true;
            }
        }
    }

    /** {@code ServerTickEvent} 末尾调（本类已监听）：只在表非空时遍历；到期、最少保持、翻转 */
    public static void resolve(MinecraftServer server, long now) {
        resolveInternal(now, null);
    }

    /** GameTest 用：只结算这几名玩家（并行的测试互不影响） */
    public static void resolveForTest(long now, ServerPlayer... targets) {
        Set<UUID> only = new HashSet<>();
        for (ServerPlayer target : targets) {
            only.add(target.getUUID());
        }
        resolveInternal(now, only);
    }

    private static void resolveInternal(long now, @Nullable Set<UUID> only) {
        if (ENTRIES.isEmpty()) {
            return;
        }
        boolean available = availability() == Availability.AVAILABLE;
        int minHold = CurioSealConfig.values().minHoldTicks();
        for (Entry entry : new ArrayList<>(ENTRIES.values())) {
            if (only != null && !only.contains(entry.player.getUUID())) {
                continue;
            }
            entry.sources.entrySet().removeIf(source -> {
                if (source.getValue() < now) {
                    entry.names.remove(source.getKey());
                    entry.namesDirty = true;
                    return true;
                }
                return false;
            });
            boolean want = available && !entry.sources.isEmpty();
            if (want && !entry.sealed) {
                seal(entry, now);
            } else if (!want && entry.sealed) {
                if (!available || (now - entry.sealedAt >= minHold && !entry.player.isDeadOrDying())) {
                    unseal(entry, entry.player);
                }
            } else if (entry.sealed && entry.namesDirty) {
                // 穿戴者自己那份每秒随场的运转来去，去掉自己以后名单没变就不发
                List<String> names = displayNames(entry);
                int more = moreNames(entry, names);
                if (!names.equals(entry.sentNames) || more != entry.sentMore) {
                    entry.sentNames = names;
                    entry.sentMore = more;
                    S2CCurioSealPacket.sendNames(entry.player, names, more);
                }
            }
            entry.namesDirty = false;
            if (!entry.sealed && entry.sources.isEmpty()) {
                ENTRIES.remove(entry.player.getUUID(), entry);
            }
        }
    }

    private static void seal(Entry entry, long now) {
        entry.sealed = true;
        entry.sealedAt = now;
        updateSealedSet(entry.player, true);
        CuriosCompat.invalidate(entry.player);
        List<String> names = displayNames(entry);
        int more = moreNames(entry, names);
        entry.sentNames = names;
        entry.sentMore = more;
        S2CCurioSealPacket.sendState(entry.player, true, names, more);
        MinecraftForge.EVENT_BUS.post(new CurioSealEvent(entry.player, true, List.copyOf(entry.sources.keySet()), names, more));
    }

    /** 解除：notify 是要通知的玩家对象（重生后的新对象和旧的实体 id 相同），null = 不发包不发事件（下线） */
    private static void unseal(Entry entry, @Nullable ServerPlayer notify) {
        entry.sealed = false;
        updateSealedSet(entry.player, false);
        CuriosCompat.invalidate(entry.player);
        SealSlotKeeper.forget(entry.player);
        // 压制期间 Curios 看不到真物品的变化，有的构建按格缓存的 ICurio 清不到：解除这一刻全清（见 CurioSlotCaches）
        CurioSlotCaches.resetAll(entry.player);
        if (notify != null) {
            S2CCurioSealPacket.sendState(notify, false, List.of(), 0);
            MinecraftForge.EVENT_BUS.post(new CurioSealEvent(notify, false, List.of(), List.of(), 0));
        }
    }

    /** 整条记录作废（不看最少保持）：被压制的先解除 */
    private static void releaseEntry(Entry entry, @Nullable ServerPlayer notify) {
        if (entry.sealed) {
            unseal(entry, notify);
        }
        ENTRIES.remove(entry.player.getUUID(), entry);
    }

    private static void updateSealedSet(Player player, boolean sealed) {
        Set<Player> current = sealedServer;
        if (current.contains(player) == sealed) {
            return;
        }
        Set<Player> next = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        next.addAll(current);
        if (sealed) {
            next.add(player);
        } else {
            next.remove(player);
        }
        sealedServer = next.isEmpty() ? Set.of() : Collections.unmodifiableSet(next);
    }

    /**
     * 给本人看的来源名（至多 {@value S2CCurioSealPacket#MAX_NAMES} 个）：去掉他自己——穿戴者的场运转时也压他自己（{@code affects_self}），
     * 「你的饰品被你自己的场压制了」没有意义，名单只剩自己时为空，客户端就不出被封的提示。服务端的 {@link CurioSealEvent#getSourceIds()} 照旧是完整的
     */
    private static List<String> displayNames(Entry entry) {
        UUID self = entry.player.getUUID();
        List<String> names = new ArrayList<>(entry.names.size());
        for (Map.Entry<UUID, String> source : entry.names.entrySet()) {
            if (!source.getKey().equals(self)) {
                names.add(source.getValue());
            }
        }
        return names.size() > S2CCurioSealPacket.MAX_NAMES ? List.copyOf(names.subList(0, S2CCurioSealPacket.MAX_NAMES)) : List.copyOf(names);
    }

    /** 去掉本人以后，shown（{@link #displayNames} 的结果）没列出来的来源数 */
    private static int moreNames(Entry entry, List<String> shown) {
        int others = entry.names.size() - (entry.names.containsKey(entry.player.getUUID()) ? 1 : 0);
        return Math.max(0, others - shown.size());
    }

    /** 压制 target 的来源名字（至多 3 个，提示用；不含他自己的场）；没被压制返回空 */
    public static List<String> sourceNames(ServerPlayer target) {
        Entry entry = ENTRIES.get(target.getUUID());
        return entry == null || entry.player != target || !entry.sealed ? List.of() : displayNames(entry);
    }

    /** 压制 target 的来源里（不含他自己的场），{@link #sourceNames} 没列出来的还有几个 */
    public static int moreSources(ServerPlayer target) {
        Entry entry = ENTRIES.get(target.getUUID());
        return entry == null || entry.player != target || !entry.sealed ? 0 : moreNames(entry, displayNames(entry));
    }

    /** 登记在 target 身上、还没到期的全部来源（UUID → 名字），没有返回空表（终端里「也被 X 压制」用） */
    public static Map<UUID, String> sourcesOf(ServerPlayer target) {
        Entry entry = ENTRIES.get(target.getUUID());
        return entry == null || entry.player != target ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(entry.names));
    }

    // ================================================================== mixin 用

    /** 包 {@code CuriosEventHandler} 四个方法里交给 {@code ifPresent} 的处理函数：被压制的人交遮罩视图 */
    public static NonNullConsumer<ICuriosItemHandler> masking(NonNullConsumer<ICuriosItemHandler> original) {
        if (noOneSealed()) {
            return original;
        }
        return handler -> {
            LivingEntity wearer = handler.getWearer();
            if (!isSealed(wearer)) {
                original.accept(handler);
                return;
            }
            SealedCuriosView view = new SealedCuriosView(handler);
            if (wearer.level().isClientSide) {
                original.accept(view);
                return;
            }
            Scope previous = scope;
            scope = new Scope(Thread.currentThread(), wearer.getId(), handler, previous);
            try {
                original.accept(view);
            } finally {
                scope = previous;
            }
        };
    }

    /** 包猪灵中立、细雪行走、免冻三处钩子交给 {@code map} 的函数：被压制的人交遮罩视图 */
    public static <R> NonNullFunction<ICuriosItemHandler, R> maskingFn(NonNullFunction<ICuriosItemHandler, R> original) {
        if (noOneSealed()) {
            return original;
        }
        return handler -> isSealed(handler.getWearer()) ? original.apply(new SealedCuriosView(handler)) : original.apply(handler);
    }

    /**
     * 包「背包里有没有」三处钩子交给 {@code map} 的函数：执行期间查询接口不过滤（任务、钥匙这类「有没有」的判断照常）。
     * 例外是持有者正在死亡（血量 ≤ 0）：「戴着就免死」的饰品有的不查饰品栏，而是查 {@code Inventory.contains(new ItemStack(物品))}，
     * Curios 让它顺带认饰品栏——这时也放开的话，被压制的人照样被饰品栏里那件救下，和「死亡时照样过滤」对不上。
     * 所以死亡判定期间（{@code checkTotemDeathProtection}、{@code LivingDeathEvent} 都在血量归零之后）这三处也按压制过滤，
     * 只少算被压制的饰品，背包、副手、盔甲位里的照常算。
     */
    public static <R> NonNullFunction<ICuriosItemHandler, R> unfilteredFn(NonNullFunction<ICuriosItemHandler, R> original) {
        if (noOneSealed()) {
            return original;
        }
        return handler -> {
            LivingEntity wearer = handler.getWearer();
            if (wearer != null && wearer.isDeadOrDying()) {
                return original.apply(handler);
            }
            int[] depth = UNFILTERED.get();
            depth[0]++;
            try {
                return original.apply(handler);
            } finally {
                depth[0]--;
            }
        };
    }

    /**
     * 查询接口该不该过滤：持有者被压制、不在「本次查询不过滤」里。死亡时也过滤：LivingDeathEvent 发出时血量已经 ≤ 0，
     * 「戴着就免死」的饰品正是在这时查，放开就等于被压制的人照样被它救下；本模组自己的饰品（{@link CuriosCompat}）死亡时同样过滤，
     * 前后一致。墓碑类存取物品读 {@code getCurios()} / {@code getEquippedCurios()}，不经过这里；Curios 的死亡掉落读真槽位。
     */
    private static boolean shouldFilter(@Nullable LivingEntity wearer) {
        return !noOneSealed() && isSealed(wearer) && UNFILTERED.get()[0] == 0;
    }

    /** findFirstCurio(Item) 的返回值过滤；不用改返回 null */
    @Nullable
    public static Optional<SlotResult> filterFirst(ICuriosItemHandler handler, @Nullable Optional<SlotResult> result, Item item) {
        if (result == null || result.isEmpty() || !shouldFilter(handler.getWearer()) || isExempt(result.get().stack())) {
            return null;
        }
        return firstExempt(handler, stack -> stack.getItem() == item);
    }

    /** findFirstCurio(Predicate) 的返回值过滤：结果被压制就接着找下一个没被压制的；不用改返回 null */
    @Nullable
    public static Optional<SlotResult> filterFirst(ICuriosItemHandler handler, @Nullable Optional<SlotResult> result, Predicate<ItemStack> filter) {
        if (result == null || result.isEmpty() || !shouldFilter(handler.getWearer()) || isExempt(result.get().stack())) {
            return null;
        }
        return firstExempt(handler, filter);
    }

    /** 按真槽位表找第一个豁免、且满足条件的饰品（被压制的人只剩豁免的东西算数） */
    private static Optional<SlotResult> firstExempt(ICuriosItemHandler handler, Predicate<ItemStack> match) {
        LivingEntity wearer = handler.getWearer();
        for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
            IDynamicStackHandler stacks = entry.getValue().getStacks();
            NonNullList<Boolean> renders = entry.getValue().getRenders();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty() && isExempt(stack) && match.test(stack)) {
                    return Optional.of(new SlotResult(new SlotContext(entry.getKey(), wearer, i, false,
                            renders.size() > i && renders.get(i)), stack));
                }
            }
        }
        return Optional.empty();
    }

    /** findCurios 三种的返回值过滤：去掉被压制的；不用改返回 null */
    @Nullable
    public static List<SlotResult> filterAll(ICuriosItemHandler handler, @Nullable List<SlotResult> result) {
        if (result == null || result.isEmpty() || !shouldFilter(handler.getWearer())) {
            return null;
        }
        List<SlotResult> kept = new ArrayList<>(result.size());
        for (SlotResult slot : result) {
            if (isExempt(slot.stack())) {
                kept.add(slot);
            }
        }
        return kept.size() == result.size() ? null : kept;
    }

    /** findCurio(槽位, 序号) 的返回值过滤；不用改返回 null */
    @Nullable
    public static Optional<SlotResult> filterSlot(ICuriosItemHandler handler, @Nullable Optional<SlotResult> result) {
        if (result == null || result.isEmpty() || !shouldFilter(handler.getWearer()) || isExempt(result.get().stack())) {
            return null;
        }
        return Optional.empty();
    }

    /** 时运：被压制时只算豁免的那几件；不用改返回 null */
    @Nullable
    public static Integer filterFortune(ICuriosItemHandler handler, @Nullable LootContext lootContext) {
        if (!shouldFilter(handler.getWearer())) {
            return null;
        }
        int level = 0;
        for (SlotResult slot : exemptSlots(handler)) {
            level += CuriosApi.getCurio(slot.stack()).map(curio -> curio.getFortuneLevel(slot.slotContext(), lootContext)).orElse(0);
        }
        return level;
    }

    /** 抢夺：被压制时只算豁免的那几件；不用改返回 null */
    @Nullable
    public static Integer filterLooting(ICuriosItemHandler handler, DamageSource source, LivingEntity target, int baseLooting) {
        if (!shouldFilter(handler.getWearer())) {
            return null;
        }
        int level = 0;
        for (SlotResult slot : exemptSlots(handler)) {
            level += CuriosApi.getCurio(slot.stack())
                    .map(curio -> curio.getLootingLevel(slot.slotContext(), source, target, baseLooting)).orElse(0);
        }
        return level;
    }

    private static List<SlotResult> exemptSlots(ICuriosItemHandler handler) {
        LivingEntity wearer = handler.getWearer();
        List<SlotResult> out = new ArrayList<>();
        for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
            IDynamicStackHandler stacks = entry.getValue().getStacks();
            NonNullList<Boolean> renders = entry.getValue().getRenders();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty() && isExempt(stack)) {
                    out.add(new SlotResult(new SlotContext(entry.getKey(), wearer, i, false, renders.size() > i && renders.get(i)), stack));
                }
            }
        }
        return out;
    }

    /** 旧接口 findEquippedCurio(Predicate, 实体) 的返回值过滤；不用改返回 null */
    @Nullable
    public static Optional<ImmutableTriple<String, Integer, ItemStack>> filterLegacy(
            LivingEntity entity, @Nullable Optional<ImmutableTriple<String, Integer, ItemStack>> result, Predicate<ItemStack> filter) {
        if (result == null || result.isEmpty() || !shouldFilter(entity) || isExempt(result.get().getRight())) {
            return null;
        }
        return CuriosApi.getCuriosInventory(entity).resolve()
                .flatMap(handler -> firstExempt(handler, filter))
                .map(slot -> ImmutableTriple.of(slot.slotContext().identifier(), slot.slotContext().index(), slot.stack()));
    }

    /** 旧接口 findEquippedCurio(Item, 实体) 的返回值过滤；不用改返回 null */
    @Nullable
    public static Optional<ImmutableTriple<String, Integer, ItemStack>> filterLegacy(
            LivingEntity entity, @Nullable Optional<ImmutableTriple<String, Integer, ItemStack>> result, Item item) {
        return filterLegacy(entity, result, stack -> stack.getItem() == item);
    }

    /**
     * 同步包修正：只在「遮罩作用域在当前线程、实体对得上、这一格的真物品被压制」时返回真物品的拷贝，否则 null（包原样）。
     * 单人游戏里客户端网络线程解码时也会构造这个包，线程对不上就不碰服务端对象。
     */
    @Nullable
    public static ItemStack realStackForSync(int entityId, String curioId, int slot) {
        Scope current = scope;
        if (current == null || current.thread() != Thread.currentThread() || current.entityId() != entityId || curioId == null) {
            return null;
        }
        ICurioStacksHandler handler = current.real().getCurios().get(curioId);
        if (handler == null) {
            return null;
        }
        IDynamicStackHandler stacks = handler.getStacks();
        if (slot < 0 || slot >= stacks.getSlots()) {
            return null;
        }
        ItemStack real = stacks.getStackInSlot(slot);
        return real.isEmpty() || isExempt(real) ? null : real.copy();
    }

    // ================================================================== 测试

    /** GameTest 用：把这些物品当成在豁免标签里（不改真标签） */
    public static void setExemptForTest(Item... items) {
        testExempt = Set.copyOf(Arrays.asList(items));
    }

    /** GameTest 用：去掉豁免覆盖 */
    public static void clearTestOverrides() {
        testExempt = null;
    }

    /** GameTest 用：这名玩家的记录整条作废（被压制的直接解除，不发包不发事件），测试收尾时调 */
    public static void releaseForTest(ServerPlayer player) {
        Entry entry = ENTRIES.get(player.getUUID());
        if (entry != null) {
            releaseEntry(entry, null);
        }
        SealSlotKeeper.forget(player);
    }

    // ================================================================== 事件

    /** 服务端：结算、下线、重生、换维度、开始追踪、关服；两端：装备校验看真物品 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class CurioSealEvents {

        private CurioSealEvents() {
        }

        @SubscribeEvent
        public static void onCurioSealServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && !ENTRIES.isEmpty()) {
                resolve(event.getServer(), now(event.getServer()));
            }
        }

        @SubscribeEvent
        public static void onCurioSealLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                Entry entry = ENTRIES.get(player.getUUID());
                if (entry != null) {
                    releaseEntry(entry, null);
                }
                dropEverywhere(player.getUUID());
                SealSlotKeeper.forget(player);
            }
        }

        @SubscribeEvent
        public static void onCurioSealRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                Entry entry = ENTRIES.get(player.getUUID());
                if (entry != null && entry.player != player) {
                    // 重生是新对象（实体 id 不变）：旧对象的压制作废，客户端那条也撤掉，等下一次评估
                    releaseEntry(entry, entry.sealed ? player : null);
                }
            }
        }

        @SubscribeEvent
        public static void onCurioSealChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                Entry entry = ENTRIES.get(player.getUUID());
                if (entry != null) {
                    // 换维度当刻撤掉他身上所有来源（场伸不过维度），不等最少保持
                    releaseEntry(entry, entry.sealed ? player : null);
                }
            }
        }

        @SubscribeEvent
        public static void onCurioSealStartTracking(PlayerEvent.StartTracking event) {
            if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer tracker && isSealed(target)) {
                S2CCurioSealPacket.sendTo(tracker, target);
            }
        }

        /**
         * 装备校验看真物品（两端都发：界面放物品、右键装备都经 Curios 的 {@code isItemValid}，它先发这个事件、结果是 DEFAULT 才调
         * {@code ICurio.canEquip}）。别的模组常在 canEquip 里用 findFirstCurio 做「同种只许戴一件」，被压制时查询被过滤成空，
         * 就能多戴一件，解除后两件一起生效。所以持有者被压制时，先在「不过滤」作用域里把 canEquip 算一遍，不许就 DENY；
         * 许的话留着 DEFAULT，「被压制时照样戴得上新的」不变。
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onCurioSealEquipCheck(CurioEquipEvent event) {
            if (noOneSealed() || event.getResult() != Event.Result.DEFAULT) {
                return;
            }
            SlotContext context = event.getSlotContext();
            if (!isSealed(context.entity())) {
                return;
            }
            int[] depth = UNFILTERED.get();
            depth[0]++;
            boolean allowed;
            try {
                allowed = CuriosApi.getCurio(event.getStack()).map(curio -> curio.canEquip(context)).orElse(true);
            } finally {
                depth[0]--;
            }
            if (!allowed) {
                event.setResult(Event.Result.DENY);
            }
        }

        @SubscribeEvent
        public static void onCurioSealServerStopped(ServerStoppedEvent event) {
            // 单人游戏退出世界再进另一个，同一个 JVM 里不能留上一个世界的记录
            ENTRIES.clear();
            sealedServer = Set.of();
            scope = null;
            SealSlotKeeper.clear();
        }
    }

    /** 模组总线：加载完以后强制加载五个注入目标，核对注入是否全部到位 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class CurioSealModEvents {

        private CurioSealModEvents() {
        }

        @SubscribeEvent
        public static void onCurioSealLoadComplete(FMLLoadCompleteEvent event) {
            event.enqueueWork(CurioSeal::verifyMixins);
        }
    }

    /**
     * 核对：postApply 只在目标类被加载时才跑，而 {@code CuriosEventHandler} 等由 Curios 自己的通用设置加载（各模组并行分发），
     * {@code CurioInventoryWrapper} 要等第一个实体挂能力、{@code CuriosUtilMixinHooks} 要等第一次调钩子——所以先各
     * {@code Class.forName(…, false, …)} 一次（只加载、不初始化），再看结果。不全就整个功能不可用，不崩游戏。
     */
    static void verifyMixins() {
        if (CurioSealMixinStatus.gate() == CurioSealMixinStatus.Gate.ACCEPTED) {
            ClassLoader loader = CurioSeal.class.getClassLoader();
            for (String target : CurioSealMixinStatus.TARGETS.values()) {
                try {
                    Class.forName(target, false, loader);
                } catch (ClassNotFoundException | LinkageError e) {
                    LOGGER.warn("Curio Seal Field: target class {} could not be loaded", target, e);
                }
            }
        }
        CurioSealMixinStatus.verified = true;
        List<String> problems = CurioSealMixinStatus.problems();
        if (problems.isEmpty()) {
            LOGGER.info("Curio Seal Field: all Curios hooks are in place (Curios {}).", CurioSealMixinStatus.curiosVersion());
        } else {
            LOGGER.warn("Curio Seal Field is unavailable: {}", problems);
        }
    }
}
