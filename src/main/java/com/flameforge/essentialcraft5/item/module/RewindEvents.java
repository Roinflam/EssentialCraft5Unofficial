package com.flameforge.essentialcraft5.item.module;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECSurvivalModules;
import com.flameforge.essentialcraft5.teleport.ECTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 回溯模块（第十批 10c-M2，batch10.md §5.11.1）：受到致命一击时把穿戴者送回约 {@code seconds_back} 秒前站过的地方，生命回到当时的数（至少 1）。只在服务端。
 *
 * <h2>快照</h2>
 * 回溯生效（{@link ModuleCache#active}）的玩家每 {@value #INTERVAL} 刻记一条（{@link TickEvent.PlayerTickEvent} 末尾，和模块电费一样按
 * {@code (tickCount + 实体 id) % 20} 错开）：维度、脚的位置、朝向、生命、吸收、着没着地、脚下那格（{@code getOnPos}，站在栅栏上也对）。
 * 留最近 {@code seconds_back × 2} 条，只在内存里、不存盘；换维度、下线、模块不生效时清空。
 *
 * <h2>救</h2>
 * {@link LivingDeathEvent} <b>LOW</b>、不收已取消的：原版图腾在 {@code checkTotemDeathProtection} 里、发死亡事件之前就救了，别的模组的免死多在
 * HIGH / NORMAL，让它们先救，回溯兜底。按顺序，第一条不满足就不救、什么都不扣：
 * <ol>
 *   <li>伤害类型是 {@code minecraft:generic_kill}（/kill）不救；{@code minecraft:out_of_world}（掉进虚空）看 {@code rescue_void}（D11 A 默认救）；</li>
 *   <li>冷却好了（{@link ModuleCooldowns}，存在玩家持久数据里，退出重进、死了都不清，所以不能靠重生刷新）；</li>
 *   <li>身上付得起 {@code mru_cost}（{@link MRUDrain#canAfford}：先身上储存物品、再胸甲自存）；</li>
 *   <li>挑快照（{@link #candidates}）：同维度、记的时候着地、至少 {@code min_age_ticks} 刻以前、还在留存窗口里的；先挑 {@code seconds_back} 秒前及更早的
 *       （越接近越先），都不行再挑 {@code min_age_ticks} 到 {@code seconds_back} 秒之间的（越早越先）。先按这个顺序只查落点安全
 *       （{@link ECTeleport#checkSafe}，不探测），落点相同的只算一个，取前 {@value #MAX_CLAIM_PROBES} 个；再对它们用 {@link ECTeleport#check}
 *       问领地三态（给不出结论的当不合格），第一个合格的就是它，两个都不行就不救。领地探测压到每次死亡最多 {@value #MAX_CLAIM_PROBES} 个落点
 *       （最多 4 个破坏事件，常见 1~2 个）：在不让建筑的地方死掉时每个候选都会被拒，逐条问会一口气连发十几个，混合端上就是十几句「没有权限」。
 *       出生点保护范围里（{@code level.mayInteract}）的落点同样不合格；</li>
 *   <li>{@link ECTeleport#teleport} 过去（发 Forge 的 {@code EntityTeleportEvent}，混合端插件在 {@code PlayerTeleportEvent} 里也能取消）：
 *       没传成就不救、不扣（不再试别的快照）。</li>
 * </ol>
 * 救成以后：取消死亡事件，生命设回快照的数（至少 1），身上有伤害吸收效果时把吸收补回快照的数（不超过这个效果本身给的量），火熄掉、摔落距离和速度清零；
 * 再扣 MRU、加 {@code matrix_damage} 点矩阵损伤（走 {@link CorruptionEffects#addMatrixDamage}，超出上限的不记，batch10.md §14.7）、进冷却，
 * 动作栏一句「回溯：退回 n 秒前」，两头各一声紫颂果传送的声音。
 * <p>
 * 混合端（Mohist 系，batch10.md §5.11.1）：{@code ServerPlayer.die} 先发 Forge 的 {@code LivingDeathEvent}、被取消就返回，Bukkit 的
 * {@code PlayerDeathEvent} 在后面，所以救下的这次插件看不到死亡（混合端自检 C41 守着）；在它之前只有 {@code gameEvent(ENTITY_DIE)}，纯 Forge 也一样。
 * 只动自己：PvP 里等于 5 分钟多一条命（D11 A），服主可以用 {@code general.computer_modules.disabled} 关掉这块模块。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class RewindEvents {

    /** 记快照的间隔（刻） */
    public static final int INTERVAL = 20;
    /** 一次救援最多对几个不同的落点问领地（每个落点探测落脚格和脚下那格，最多 2 个破坏事件） */
    public static final int MAX_CLAIM_PROBES = 2;
    /** 动作栏：回溯成功（参数：退回了几秒） */
    public static final String KEY_REWOUND = "message.essentialcraft5.rewind_module.rewound";

    /** 一次死亡时的结论（GameTest 断言用）。只往末尾加 */
    public enum Result {
        /** 救下了 */
        REWOUND,
        /** 回溯没在生效（没装、关着、没穿齐、被停用……） */
        NOT_ACTIVE,
        /** /kill */
        KILL_COMMAND,
        /** 掉进虚空，而 {@code rescue_void} 关着 */
        VOID,
        /** 冷却中 */
        COOLDOWN,
        /** MRU 不够 */
        NO_POWER,
        /** 没有合格的快照（没记过、都太新 / 太旧、别的维度、没着地、落点不安全或没有权限） */
        NO_SNAPSHOT,
        /** 传送被取消（{@code EntityTeleportEvent} 或插件的传送事件） */
        TELEPORT_FAILED
    }

    /**
     * 一条快照。
     *
     * @param dimension  记的时候在哪个维度
     * @param pos        脚的位置
     * @param yRot       朝向
     * @param xRot       俯仰
     * @param health     生命
     * @param absorption 伤害吸收
     * @param onGround   着没着地
     * @param floor      脚下那格（落点检查按它的顶面算）
     * @param at         记于哪一刻（gameTime，各维度共用一份）
     */
    public record Snapshot(ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot, float health, float absorption, boolean onGround,
                           BlockPos floor, long at) {
    }

    /** 每名玩家的快照，旧的在前（只在服务端线程读写） */
    private static final Map<Player, ArrayDeque<Snapshot>> HISTORY = new WeakHashMap<>();

    private RewindEvents() {
    }

    // ------------------------------------------------------------------ 快照

    @SubscribeEvent
    public static void onRewindPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        if (Math.floorMod(player.tickCount + player.getId(), INTERVAL) != 0) {
            return;
        }
        if (!ModuleCache.active(player, ECSurvivalModules.REWIND)) {
            HISTORY.remove(player);
            return;
        }
        record(player, player.level().getGameTime());
    }

    /** 给这名玩家记一条快照，记在 at 这一刻（每秒的处理用现在；GameTest 传更早的刻数造「几秒前」的快照）。死了、旁观时不记 */
    public static void record(ServerPlayer player, long at) {
        if (player.isDeadOrDying() || player.isSpectator()) {
            return;
        }
        add(player, new Snapshot(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.getHealth(),
                player.getAbsorptionAmount(), player.onGround(), player.getOnPos(), at));
    }

    private static void add(Player player, Snapshot snapshot) {
        ArrayDeque<Snapshot> history = HISTORY.computeIfAbsent(player, p -> new ArrayDeque<>());
        Snapshot last = history.peekLast();
        if (last != null && last.dimension() != snapshot.dimension()) {
            history.clear();
        }
        history.addLast(snapshot);
        int keep = keep(SurvivalModuleConfig.rewind());
        while (history.size() > keep) {
            history.removeFirst();
        }
    }

    /** 留几条 */
    private static int keep(SurvivalModuleConfig.Rewind cfg) {
        return Math.max(2, cfg.secondsBack() * 2);
    }

    /**
     * 按挑选顺序排好的候选快照（见类注释第 4 条；只看维度、着地和年龄，不查落点）。
     *
     * @param now 现在（gameTime）
     */
    public static List<Snapshot> candidates(ServerPlayer player, long now) {
        ArrayDeque<Snapshot> history = HISTORY.get(player);
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        SurvivalModuleConfig.Rewind cfg = SurvivalModuleConfig.rewind();
        long target = cfg.secondsBack() * 20L;
        long maxAge = (long) (keep(cfg) + 1) * INTERVAL;
        ResourceKey<Level> dimension = player.level().dimension();
        List<Snapshot> older = new ArrayList<>();
        List<Snapshot> newer = new ArrayList<>();
        for (Snapshot snapshot : history) {
            long age = now - snapshot.at();
            if (snapshot.dimension() != dimension || !snapshot.onGround() || age < cfg.minAgeTicks() || age > maxAge) {
                continue;
            }
            (age >= target ? older : newer).add(snapshot);
        }
        // 「seconds_back 秒前及更早」按年龄从小到大；不到 seconds_back 秒的按年龄从大到小，接在后面
        older.sort(Comparator.comparingLong(snapshot -> now - snapshot.at()));
        newer.sort(Comparator.comparingLong((Snapshot snapshot) -> now - snapshot.at()).reversed());
        older.addAll(newer);
        return older;
    }

    // ------------------------------------------------------------------ 救

    /** LOW、不收已取消的（见类注释） */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRewindDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && attempt(player, event.getSource()) == Result.REWOUND) {
            event.setCanceled(true);
        }
    }

    /**
     * 试着救一次（死亡事件里调；救成了由调用方取消事件）。不满足的条件按类注释的顺序报出第一条，什么都不改。
     *
     * @param source 致命的那一下
     */
    public static Result attempt(ServerPlayer player, DamageSource source) {
        if (!ModuleCache.active(player, ECSurvivalModules.REWIND)) {
            return Result.NOT_ACTIVE;
        }
        if (source.is(DamageTypes.GENERIC_KILL)) {
            return Result.KILL_COMMAND;
        }
        SurvivalModuleConfig.Rewind cfg = SurvivalModuleConfig.rewind();
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) && !cfg.rescueVoid()) {
            return Result.VOID;
        }
        if (!ModuleCooldowns.ready(player, ECSurvivalModules.REWIND)) {
            return Result.COOLDOWN;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!MRUDrain.canAfford(player, chest, cfg.mruCost())) {
            return Result.NO_POWER;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        for (Snapshot snapshot : safeCandidates(player, level, now)) {
            ECTeleport.Check check = ECTeleport.check(player, level, snapshot.floor(), null);
            if (!check.ok() || check.landing() == null) {
                continue;
            }
            Vec3 from = player.position();
            if (!ECTeleport.teleport(player, level, check.landing(), snapshot.yRot(), snapshot.xRot())) {
                return Result.TELEPORT_FAILED;
            }
            restore(player, snapshot);
            MRUDrain.playerUseMRU(player, chest, cfg.mruCost());
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                CorruptionEffects.addMatrixDamage(data, cfg.matrixDamage());
            }
            ModuleCooldowns.start(player, ECSurvivalModules.REWIND, cfg.cooldownTicks());
            // 退回去以后旧的位置都作废：下一次（冷却好以后）从新记的开始
            HISTORY.remove(player);
            long seconds = Math.max(1L, Math.round((now - snapshot.at()) / 20.0D));
            player.displayClientMessage(Component.translatable(KEY_REWOUND, seconds), true);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1F, 1F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1F, 1F);
            return Result.REWOUND;
        }
        return Result.NO_SNAPSHOT;
    }

    /**
     * 要问领地的候选（见类注释第 4 条）：按挑选顺序只做落点安全检查（{@link ECTeleport#checkSafe}，不探测），落点相同的只留第一条，
     * 最多留 {@value #MAX_CLAIM_PROBES} 条。在不让建筑的地方死掉时，每个候选都会被领地拒绝，不限条数就是一次死亡连发十几个破坏事件
     */
    private static List<Snapshot> safeCandidates(ServerPlayer player, ServerLevel level, long now) {
        List<Snapshot> out = new ArrayList<>(MAX_CLAIM_PROBES);
        Set<Vec3> landings = new HashSet<>();
        for (Snapshot snapshot : candidates(player, now)) {
            ECTeleport.Check safe = ECTeleport.checkSafe(player, level, snapshot.floor());
            if (!safe.ok() || safe.landing() == null || !landings.add(safe.landing())) {
                continue;
            }
            out.add(snapshot);
            if (out.size() >= MAX_CLAIM_PROBES) {
                break;
            }
        }
        return out;
    }

    /** 传过去以后：生命、吸收、火、摔落、速度 */
    private static void restore(ServerPlayer player, Snapshot snapshot) {
        player.setHealth(Math.max(1F, snapshot.health()));
        MobEffectInstance absorption = player.getEffect(MobEffects.ABSORPTION);
        if (absorption != null) {
            // 只补回这个效果本身给得起的量（每级 4 点），效果过了期照常消失，不会凭空多出一层吸收
            float cap = 4F * (absorption.getAmplifier() + 1);
            player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), Math.min(snapshot.absorption(), cap)));
        }
        player.clearFire();
        player.fallDistance = 0F;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
    }

    // ------------------------------------------------------------------ 清理

    @SubscribeEvent
    public static void onRewindChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        HISTORY.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void onRewindLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HISTORY.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void onRewindServerStopped(ServerStoppedEvent event) {
        HISTORY.clear();
    }

    // ------------------------------------------------------------------ 给测试看的

    /** GameTest / 混合端自检用：直接塞一条快照（造别的维度、没着地的快照） */
    public static void injectForTest(Player player, Snapshot snapshot) {
        add(player, snapshot);
    }

    /** GameTest 用：这名玩家现在留着几条快照 */
    public static int historySizeForTest(Player player) {
        ArrayDeque<Snapshot> history = HISTORY.get(player);
        return history == null ? 0 : history.size();
    }

    /** GameTest / 混合端自检用：丢掉这名玩家的快照 */
    public static void forgetForTest(Player player) {
        HISTORY.remove(player);
    }
}
