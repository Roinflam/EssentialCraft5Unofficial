package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * 本模组给玩家的飞行（第八批框架 8-Fb，batch8.md §3.2 第 7 条）：宅男反重力板给飞行，全息投影 Boss 登记压制器让参战者不能飞。
 * <pre>
 * ECFlight.grant(player, EssentialCraft5.id("computer_board"), 0.1F);   // 戴上（重复调没关系，速度变了会跟着改）
 * ECFlight.revoke(player, EssentialCraft5.id("computer_board"));        // 摘下
 * ECFlight.registerSuppressor(player -&gt; 是不是某场 Boss 战的参战者);     // 模组构造阶段登记一次
 * </pre>
 * <h2>规则</h2>
 * <ul>
 *   <li>按「来源」记账：几个来源同时给时取最快的速度；最后一个来源撤掉才收回飞行。只撤自己给的：
 *       第一次给飞行时玩家本来就能飞（别的模组给的），收回时不关 {@code mayfly}，只把飞行速度还原（速度还是本模组设的那个值才还原）。</li>
 *   <li>创造 / 旁观不碰：原版按游戏模式管它们的飞行；切到创造 / 旁观时只把本模组改过的飞行速度还原，切回生存 / 冒险时重新给上。</li>
 *   <li>压制器（{@link #registerSuppressor}）有一个返回真，就暂停本模组给的飞行（{@code mayfly}、{@code flying} 都关），来源照记，压制解除后重新给上。
 *       别的来源给的飞行不归这里管（Boss 自己每 10 刻关 {@code flying}）。压制器每刻对有授权的玩家调一次，要写得便宜。</li>
 *   <li>只在状态变化时写能力、发同步包（{@code onUpdateAbilities}），不每刻覆盖：别的模组、插件（禁飞区之类）在中途关掉飞行，这里不会抢回来。</li>
 *   <li>记账写在玩家的 Forge 持久数据里（{@value #TAG}，不在 PlayerPersisted 下面，死亡重生就没了，新玩家对象的能力本来就是默认的）。
 *       <b>重登不残留</b>：登录时把记下的来源都标成「待确认」，{@value #RELOGIN_GRACE_TICKS} 刻内给飞行的模块重新 {@link #grant} 过的留下，
 *       没人续的到点撤掉；宽限期里照旧能飞，免得带着反重力板在半空下线、上线就掉下去。给飞行的模块至少每 20 刻确认一次（或者在登录时确认）。</li>
 *   <li><b>原版会清掉的要重新给上</b>：原版登录时先读回存档里的能力，再按游戏模式重设一遍（{@code loadGameTypes}），生存 / 冒险下 {@code mayfly}、
 *       {@code flying} 都被清成假；生存 ↔ 冒险互切同样会清。这里在读档事件（{@code PlayerEvent.LoadFromFile}，早于原版重设）记下「要重新给上」和
 *       当时的 {@code flying}，登录当刻按它恢复；给飞行时记下游戏模式，模式变了（还在生存 / 冒险里）就重新打开 {@code mayfly}。
 *       速度不受影响（原版重设不碰速度，存档里就是本模组设的值）。</li>
 *   <li>纯 Forge 上跨维度的 {@code teleportTo}（指令 tp、别的模组的传送）不补发能力包，客户端换了新的本地玩家，能力是默认的：
 *       换维度事件里本模组给着飞行时补发一次（{@link #resync}）。</li>
 * </ul>
 * 只在服务端调；GameTest 里的玩家不进世界、不发 tick 事件，测试直接调 {@link #update}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ECFlight {

    /** 玩家持久数据里的键 */
    public static final String TAG = "essentialcraft5:flight";
    /** 重登后等给飞行的模块重新确认的刻数（按新玩家对象的 tickCount 算） */
    public static final int RELOGIN_GRACE_TICKS = 60;
    /** 原版默认飞行速度（{@code Abilities.flyingSpeed} 初值） */
    public static final float VANILLA_FLY_SPEED = 0.05F;

    private static final String SOURCES = "sources";
    private static final String STALE = "stale";
    private static final String APPLIED = "applied";
    private static final String OTHERS = "others";
    private static final String BASE_SPEED = "base_speed";
    private static final String SET_SPEED = "set_speed";
    /** 给上飞行时的游戏模式 id（生存 ↔ 冒险互切后原版清掉了 mayfly，按它认出来重新给上） */
    private static final String MODE = "mode";
    /** 读档时记下：原版登录流程马上要清掉 mayfly / flying，下一次 update 重新给上 */
    private static final String REASSERT = "reassert";
    /** 读档时存档里的 flying（半空下线的，上线照样在飞） */
    private static final String REASSERT_FLYING = "reassert_flying";

    private static final List<Predicate<ServerPlayer>> SUPPRESSORS = new CopyOnWriteArrayList<>();

    private ECFlight() {
    }

    // ------------------------------------------------------------------
    // 对外
    // ------------------------------------------------------------------

    /** 给 player 飞行（来源 source、飞行速度 flySpeed，原版默认 0.05）。重复调只更新速度；创造 / 旁观照记、不碰能力 */
    public static void grant(ServerPlayer player, ResourceLocation source, float flySpeed) {
        Objects.requireNonNull(source);
        CompoundTag data = data(player, true);
        CompoundTag sources = data.getCompound(SOURCES);
        sources.putFloat(source.toString(), Math.max(0F, flySpeed));
        data.put(SOURCES, sources);
        CompoundTag stale = data.getCompound(STALE);
        if (stale.contains(source.toString())) {
            stale.remove(source.toString());
            data.put(STALE, stale);
        }
        update(player);
    }

    /** 撤掉 source 给的飞行；最后一个来源撤掉时收回飞行（只撤自己给的，创造 / 旁观不碰） */
    public static void revoke(ServerPlayer player, ResourceLocation source) {
        CompoundTag data = data(player, false);
        if (data == null) {
            return;
        }
        CompoundTag sources = data.getCompound(SOURCES);
        if (!sources.contains(source.toString())) {
            return;
        }
        sources.remove(source.toString());
        data.put(SOURCES, sources);
        CompoundTag stale = data.getCompound(STALE);
        stale.remove(source.toString());
        data.put(STALE, stale);
        update(player);
    }

    /** 登记压制器（模组构造阶段登记一次）：有一个返回真，就暂停本模组给的飞行 */
    public static void registerSuppressor(Predicate<ServerPlayer> suppressed) {
        SUPPRESSORS.add(Objects.requireNonNull(suppressed));
    }

    /** 去掉登记过的压制器（GameTest 用完去掉自己登记的那个） */
    public static void unregisterSuppressor(Predicate<ServerPlayer> suppressed) {
        SUPPRESSORS.remove(suppressed);
    }

    /** source 现在给着 player 飞行（含重登后还没确认的） */
    public static boolean isGranted(ServerPlayer player, ResourceLocation source) {
        CompoundTag data = data(player, false);
        return data != null && data.getCompound(SOURCES).contains(source.toString());
    }

    /** 本模组现在真的给着飞行（{@code mayfly} 是本模组打开的、没被压制、不是创造 / 旁观） */
    public static boolean isApplied(ServerPlayer player) {
        CompoundTag data = data(player, false);
        return data != null && data.getBoolean(APPLIED);
    }

    /** 有压制器对 player 返回真 */
    public static boolean isSuppressed(ServerPlayer player) {
        for (Predicate<ServerPlayer> suppressor : SUPPRESSORS) {
            if (suppressor.test(player)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 状态推进
    // ------------------------------------------------------------------

    /**
     * 按现在的来源、压制、游戏模式把能力调到该有的样子，只在变化时写、发同步包。每刻对有授权的玩家调一次（{@link #onFlightPlayerTick}），
     * grant / revoke 时也立刻调。重登宽限期过了还没被确认的来源在这里撤掉。
     */
    public static void update(ServerPlayer player) {
        CompoundTag data = data(player, false);
        if (data == null) {
            return;
        }
        CompoundTag sources = data.getCompound(SOURCES);
        CompoundTag stale = data.getCompound(STALE);
        if (!stale.isEmpty() && player.tickCount > RELOGIN_GRACE_TICKS) {
            for (String key : stale.getAllKeys()) {
                sources.remove(key);
            }
            data.put(SOURCES, sources);
            data.remove(STALE);
        }
        boolean applied = data.getBoolean(APPLIED);
        Abilities abilities = player.getAbilities();
        boolean vanillaManaged = player.isCreative() || player.isSpectator();
        boolean want = !sources.isEmpty() && !vanillaManaged && !isSuppressed(player);
        boolean changed = false;
        if (want) {
            float speed = speedOf(sources);
            int mode = player.gameMode.getGameModeForPlayer().getId();
            if (!applied) {
                data.putBoolean(OTHERS, abilities.mayfly);
                data.putFloat(BASE_SPEED, abilities.getFlyingSpeed());
                abilities.mayfly = true;
                abilities.setFlyingSpeed(speed);
                data.putFloat(SET_SPEED, speed);
                data.putInt(MODE, mode);
                data.putBoolean(APPLIED, true);
                changed = true;
            } else {
                // 原版登录、生存 ↔ 冒险互切清掉了 mayfly（见类注释）：重新给上。APPLIED / OTHERS / 速度都不动——
                // 置回未接管会让上面的分支把本模组自己设的速度记成 BASE_SPEED。老存档的记账没有 MODE（读出 0 = 生存），冒险玩家多给一次，无害
                boolean relogin = data.getBoolean(REASSERT);
                if (relogin || data.getInt(MODE) != mode) {
                    abilities.mayfly = true;
                    if (relogin) {
                        abilities.flying = data.getBoolean(REASSERT_FLYING);
                    }
                    data.putInt(MODE, mode);
                    changed = true;
                }
                if (data.getFloat(SET_SPEED) != speed) {
                    if (abilities.getFlyingSpeed() == data.getFloat(SET_SPEED)) {
                        abilities.setFlyingSpeed(speed);
                        changed = true;
                    }
                    data.putFloat(SET_SPEED, speed);
                }
            }
        } else if (applied) {
            if (!vanillaManaged && !data.getBoolean(OTHERS)) {
                abilities.mayfly = false;
                abilities.flying = false;
            }
            if (abilities.getFlyingSpeed() == data.getFloat(SET_SPEED)) {
                abilities.setFlyingSpeed(data.contains(BASE_SPEED) ? data.getFloat(BASE_SPEED) : VANILLA_FLY_SPEED);
            }
            data.putBoolean(APPLIED, false);
            changed = true;
        }
        // 读档时记下的只管登录后的第一次 update：没在给（创造 / 旁观 / 被压制 / 没有来源）就作废
        data.remove(REASSERT);
        data.remove(REASSERT_FLYING);
        if (sources.isEmpty() && !data.getBoolean(APPLIED)) {
            player.getPersistentData().remove(TAG);
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    private static float speedOf(CompoundTag sources) {
        float speed = 0F;
        for (String key : sources.getAllKeys()) {
            speed = Math.max(speed, sources.getFloat(key));
        }
        return speed;
    }

    /** 玩家持久数据里本类的那一块；create 为假且没有时返回 null */
    private static CompoundTag data(ServerPlayer player, boolean create) {
        CompoundTag root = player.getPersistentData();
        if (root.contains(TAG, Tag.TAG_COMPOUND)) {
            return root.getCompound(TAG);
        }
        if (!create) {
            return null;
        }
        CompoundTag data = new CompoundTag();
        root.put(TAG, data);
        return data;
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onFlightPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !player.getPersistentData().contains(TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        update(player);
    }

    /**
     * 读档（两条读档路径都在 {@code player.load} 之后、原版 {@code loadGameTypes} 之前发）：这时能力还是存档里的，
     * 记下「要重新给上」和当时的 flying（见类注释）
     */
    @SubscribeEvent
    public static void onFlightLoadFromFile(PlayerEvent.LoadFromFile event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            onLoadedFromSave(player);
        }
    }

    /** 读档事件里调；GameTest 用它模拟读档 */
    public static void onLoadedFromSave(ServerPlayer player) {
        CompoundTag data = data(player, false);
        if (data == null || !data.getBoolean(APPLIED)) {
            return;
        }
        data.putBoolean(REASSERT, true);
        data.putBoolean(REASSERT_FLYING, player.getAbilities().flying);
    }

    /** 登录：记下的来源都标成待确认，宽限期内没人续就撤掉；当刻就把原版清掉的飞行重新给上（见类注释） */
    @SubscribeEvent
    public static void onFlightLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            loggedIn(player);
        }
    }

    /** 登录时调（{@link #markStale} + {@link #update}）；GameTest 用它模拟重登 */
    public static void loggedIn(ServerPlayer player) {
        markStale(player);
        update(player);
    }

    /** 换维度（含纯 Forge 上不补发能力包的跨维度 teleportTo）：本模组给着飞行时补发一次 */
    @SubscribeEvent
    public static void onFlightChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resync(player);
        }
    }

    /** 本模组给着飞行时把能力再发给客户端一次（不改服务端能力） */
    public static void resync(ServerPlayer player) {
        if (isApplied(player)) {
            player.onUpdateAbilities();
        }
    }

    /** 记下的来源都标成待确认（登录时由 {@link #loggedIn} 调） */
    public static void markStale(ServerPlayer player) {
        CompoundTag data = data(player, false);
        if (data == null) {
            return;
        }
        CompoundTag stale = new CompoundTag();
        for (String key : data.getCompound(SOURCES).getAllKeys()) {
            stale.putBoolean(key, true);
        }
        data.put(STALE, stale);
    }
}
