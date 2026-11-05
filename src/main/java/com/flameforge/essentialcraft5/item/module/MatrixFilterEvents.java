package com.flameforge.essentialcraft5.item.module;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.ComputerArmorEvents;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECSurvivalModules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 矩阵过滤模块（第十批 10c-M2，batch10.md §5.11.2）：有害的状态效果加不到穿戴者身上，改记矩阵损伤（「先欠后还」：攒多了凝成腐化效果，回家用五芒星治）。只在服务端。
 *
 * <h2>在哪拦</h2>
 * {@link MobEffectEvent.Applicable}（{@code LivingEntity.canBeAffected}，原版只在 {@code addEffect} 和 {@code forceAddEffect} 里调）<b>LOW</b>，
 * 结果还是 DEFAULT 时才管（别的模组已经判了免疫 / 强制生效的不动），拦下就 {@code setResult(DENY)}。只拦 {@link MobEffectCategory#HARMFUL}，
 * 跳过效果标签 {@code essentialcraft5:unfilterable}（辐射、MRU 腐化、心智悖论、锚定：免得「拦下记成矩阵损伤 → 矩阵损伤又带来这些」绕成圈）；
 * 腐化效果发作时留下的挖得慢 / 无力 / 走得慢（#0、#1、#3）是矩阵后遗症（corruption/Afflictions），不是药水效果，根本不经过这里；
 * 无限时长的（指令给的 infinite）记不下，不拦。
 *
 * <h2>套装本来就挡住的不拦（{@link Result#SUIT_NEGATED}）</h2>
 * 中毒、瞬间伤害（药水箭、指令这类经 {@code addEffect} 加上来的）只打 magic 伤害，凋零只打 wither 伤害，宅男胸甲对这两种整下免疫
 * （{@link ComputerArmorEvents#isImmune}）；饥饿只加消耗，宅男头盔每秒把饱食度补满。拦下它们只是白花 MRU、白攒矩阵损伤，
 * 效果照常上身、照样被套装挡掉，玩家拿到的保护一点不少。按现场判定（穿没穿胸甲 / 头盔），{@code require_full_set} 关掉、只戴头盔时中毒照拦。
 * <p>
 * 身上已经挂着同一个效果、级数不低于新来的、剩下的时间也不短于新来的（{@link Result#ALREADY}）：原版这一下本来就不改身上的效果
 * （{@code MobEffectInstance.update}），不拦、不收。新来的更长时，把身上那段当成已经付过钱的时间，只补记多出来的秒数（{@link Result#EXTENDED}），不收 MRU。
 *
 * <h2>花多少</h2>
 * 新拦下一个效果：矩阵损伤 + 持续秒数（向上取整，瞬间效果按 1 秒）×（等级 + 1）× {@code matrix_per_effect_second}，另扣 {@code mru_per_effect} MRU。
 * <b>先算后扣</b>：先看 {@link CorruptionEffects#matrixRoom}（记不下这一个的全部损伤就不拦，有害效果只能整个拦或不拦——不然满载以后等于白挡，§14.7），
 * 再看 {@link MRUDrain#canAfford}（付不起不拦），都过了才扣 MRU、加损伤（{@link CorruptionEffects#addMatrixDamage}）。
 *
 * <h2>同一个效果反复来（按代码改正：计划写的是「每拦一个」都收一遍）</h2>
 * 凋灵玫瑰每刻都给一次 2 秒凋零，河豚贴着时每刻一次中毒，药水云每秒一次：按「每拦一个」收，站在凋灵玫瑰上 10 秒就是 200 次、10 万 MRU。
 * 所以拦下以后按效果记一段「已经拦下」的时间（这一级、到哪一刻为止）：这段时间里同一个效果再来，等级不高于记着的、也不比记着的更晚结束 → 免费照拦；
 * 更晚结束 → 只把多出来的那几秒按记着的等级补记矩阵损伤、不再收 MRU（和原版「同一个效果再加一次只会延长 / 升级」一个意思，站着不动的凋零每秒 20 点）；
 * 等级更高 → 当成新拦下的一个，整笔重算。记录只在内存里，过了结束时刻就作废；下线、重生（换了玩家对象）时没了，下次再来照新拦下的收。
 * 别的模组只读地调 {@code canBeAffected}、紧接着又真的 {@code addEffect} 的，第二次落在已拦下的时间里，不会收两遍。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MatrixFilterEvents {

    /** 一次判定的结论（GameTest 断言用）。只往末尾加 */
    public enum Result {
        /** 新拦下一个：扣了 MRU、加了矩阵损伤 */
        FILTERED,
        /** 同一个效果更晚结束：只补记多出来的矩阵损伤 */
        EXTENDED,
        /** 落在已拦下的时间里：免费照拦 */
        COVERED,
        /** 不是有害效果 */
        NOT_HARMFUL,
        /** 在 unfilterable 标签里 */
        UNFILTERABLE,
        /** 矩阵过滤没在生效 */
        NOT_ACTIVE,
        /** 无限时长 */
        INFINITE,
        /** 矩阵损伤记不下这一个 */
        NO_ROOM,
        /** MRU 不够 */
        NO_POWER,
        /** 套装本来就挡住这个效果（穿着宅男胸甲时的中毒、瞬间伤害、凋零，戴着宅男头盔时的饥饿）：不拦、不收 */
        SUIT_NEGATED,
        /** 身上已经挂着不弱、不短的同一个效果：原版这一下什么都不改，不拦、不收 */
        ALREADY;

        /** 这个结论是拦下了 */
        public boolean blocks() {
            return this == FILTERED || this == EXTENDED || this == COVERED;
        }
    }

    /**
     * 一个效果已经拦下的那段时间。
     *
     * @param amplifier 拦下时的等级（0 = I 级）
     * @param start     从哪一刻起（gameTime）
     * @param end       到哪一刻为止
     * @param seconds   已经按多少秒记过矩阵损伤
     */
    private record Cover(int amplifier, long start, long end, int seconds) {
    }

    /** 每名玩家、每个效果已经拦下的时间（只在服务端线程读写） */
    private static final Map<Player, Map<MobEffect, Cover>> COVERS = new WeakHashMap<>();

    private MatrixFilterEvents() {
    }

    /** LOW；别的监听器已经给了结果的不管（见类注释） */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onMatrixFilterApplicable(MobEffectEvent.Applicable event) {
        if (event.getResult() != Event.Result.DEFAULT || !(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        if (filter(player, event.getEffectInstance()).blocks()) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * 判一个要加到玩家身上的效果：拦不拦，拦的话当场扣 MRU、加矩阵损伤（见类注释）。不拦时什么都不改。
     *
     * @return 结论（{@link Result#blocks()} 为真时调用方拒掉这个效果）
     */
    public static Result filter(ServerPlayer player, MobEffectInstance instance) {
        MobEffect effect = instance.getEffect();
        if (effect.getCategory() != MobEffectCategory.HARMFUL) {
            return Result.NOT_HARMFUL;
        }
        if (isUnfilterable(effect)) {
            return Result.UNFILTERABLE;
        }
        if (!ModuleCache.active(player, ECSurvivalModules.MATRIX_FILTER)) {
            return Result.NOT_ACTIVE;
        }
        if (suitNegates(player, effect)) {
            return Result.SUIT_NEGATED;
        }
        if (instance.isInfiniteDuration()) {
            return Result.INFINITE;
        }
        int amplifier = Math.max(0, instance.getAmplifier());
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && existing.getAmplifier() >= amplifier
                && (existing.isInfiniteDuration() || existing.getDuration() >= instance.getDuration())) {
            return Result.ALREADY;
        }
        SurvivalModuleConfig.Filter cfg = SurvivalModuleConfig.filter();
        long now = player.level().getGameTime();
        int ticks = effect.isInstantenous() ? 20 : Math.max(1, instance.getDuration());
        long end = now + ticks;
        Map<MobEffect, Cover> covers = COVERS.computeIfAbsent(player, p -> new HashMap<>());
        Cover cover = covers.get(effect);
        if (cover != null && cover.end() <= now) {
            covers.remove(effect);
            cover = null;
        }
        if (cover == null && existing != null && existing.getAmplifier() >= amplifier) {
            // 身上那段（不弱、但比新来的短）当成已经付过钱：下面按「更晚结束」只补记多出来的秒数，不收 MRU
            cover = new Cover(amplifier, now, now + existing.getDuration(), seconds(existing.getDuration()));
        }
        long matrix;
        int mru;
        Cover next;
        Result result;
        if (cover != null && amplifier <= cover.amplifier()) {
            if (end <= cover.end()) {
                return Result.COVERED;
            }
            int seconds = seconds(end - cover.start());
            matrix = (long) (seconds - cover.seconds()) * (cover.amplifier() + 1) * cfg.matrixPerEffectSecond();
            mru = 0;
            next = new Cover(cover.amplifier(), cover.start(), end, seconds);
            result = Result.EXTENDED;
        } else {
            int seconds = seconds(ticks);
            matrix = (long) seconds * (amplifier + 1) * cfg.matrixPerEffectSecond();
            mru = cfg.mruPerEffect();
            next = new Cover(amplifier, now, end, seconds);
            result = Result.FILTERED;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || matrix > CorruptionEffects.matrixRoom(data)) {
            return Result.NO_ROOM;
        }
        if (mru > 0) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!MRUDrain.canAfford(player, chest, mru) || !MRUDrain.playerUseMRU(player, chest, mru)) {
                return Result.NO_POWER;
            }
        }
        CorruptionEffects.addMatrixDamage(data, matrix);
        covers.put(effect, next);
        return result;
    }

    /**
     * 穿着的宅男套装本来就让这个效果没有害处（见类注释「套装本来就挡住的不拦」）：中毒、瞬间伤害打的是 magic，凋零打的是 wither，
     * 胸甲对这两种整下免疫；饥饿只加消耗，头盔每秒补满饱食度。按现在穿着的判，不看 {@code require_full_set}
     */
    public static boolean suitNegates(Player player, MobEffect effect) {
        if (effect == MobEffects.POISON || effect == MobEffects.HARM) {
            return ComputerArmorEvents.isImmune(player, player.damageSources().magic());
        }
        if (effect == MobEffects.WITHER) {
            return ComputerArmorEvents.isImmune(player, player.damageSources().wither());
        }
        return effect == MobEffects.HUNGER && ComputerArmorEvents.wears(player, ArmorItem.Type.HELMET);
    }

    /** 刻数换成秒，向上取整 */
    private static int seconds(long ticks) {
        return (int) Math.min(Integer.MAX_VALUE, (ticks + 19L) / 20L);
    }

    /** 在 {@code essentialcraft5:unfilterable} 效果标签里 */
    public static boolean isUnfilterable(MobEffect effect) {
        var tags = ForgeRegistries.MOB_EFFECTS.tags();
        return tags != null && tags.getTag(ECEffects.UNFILTERABLE).contains(effect);
    }

    @SubscribeEvent
    public static void onMatrixFilterServerStopped(ServerStoppedEvent event) {
        COVERS.clear();
    }

    /** GameTest 用：丢掉这名玩家已拦下的时间 */
    public static void forgetForTest(Player player) {
        COVERS.remove(player);
    }
}
