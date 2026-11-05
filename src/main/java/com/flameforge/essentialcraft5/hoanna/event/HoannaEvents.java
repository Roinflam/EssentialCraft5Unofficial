package com.flameforge.essentialcraft5.hoanna.event;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.network.S2CHoannaEventPacket;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaStorm;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Predicate;

/**
 * 海纳尔四种天灾的服务端逻辑（原作 {@code ECEventHandler.worldTick} L920~943、{@code WorldEventRegistry.selectRandomEvent}、
 * {@code CommandHoannaEvent}；第六批 6a M4，batch6a.md §5.4）。状态存在海纳尔世界的 {@link HoannaEventData}，客户端靠
 * {@code S2CHoannaEventPacket} 同步（只在开始 / 结束 / 指令、登录 / 换维度 / 重生时发，原作每人每 220 tick 把整份标签发给所有维度的人）。
 * <p>
 * <b>计时与掷骰</b>（{@link #tick}，海纳尔世界 {@code LevelTickEvent} END）：
 * <ul>
 *   <li><b>海纳尔里没人就不计时、不掷骰</b>（= 原作维度卸载时的实际效果）；有人时每 tick 剩余 −1，到 0 结束。</li>
 *   <li>没有事件时，按 {@code gameTime % 20 == 0}（原作按 worldTime，关了日夜循环就变成每 tick 掷或永远不掷）常规掷一次：
 *       {@code nextInt(4)} 挑一种、再 {@code nextFloat() ≤ 概率 × chance_multiplier}；日阵在常规掷骰里永远不成立。</li>
 *   <li><b>黎明</b>：日序号（dayTime / 24000）变了、而且上一 tick 也有人在（连续看到的）时，单独掷一次日阵（同样先 nextInt(4) 挑中日阵、再 ≤ 0.3）。
 *       正常日出、主世界的人睡觉跳天、/time set 都算；没人时错过的日出不补。</li>
 *   <li>四项合计约每 6 个游戏日一次：日阵 7.5%、毒雾约 5.1%、黑暗约 1.5%、地震约 3.0% / 日（{@link HoannaEvent} 类注释）。</li>
 *   <li><b>日阵只在白天</b>（6a 审查 M4-1）：开始时时长截到当天黄昏（{@link #daylightLeft}；已经天黑就不开始）；<b>每 tick、没人时也查</b>，
 *       天黑（一天的第 {@value #SUN_ARRAY_DUSK} 刻起）就结束，没到黄昏但剩余时间超过离黄昏的时间（没人时停住、/time 往后拨）就把剩余截短。
 *       以前只看倒计时：中途离开、夜里回来，日阵会接着烧一整夜，而天色和余烬都随白天淡掉，画面上看不出来（原作同样不看昼夜，但原作没有画面）。
 *       截短不重发状态包（客户端同一事件起止刻变化会重新淡入）；进海纳尔时 {@link #syncTo} 发的已经是截短后的剩余。</li>
 *   <li><b>地脉风暴</b>（第十三批 13d，{@link LeyStorm}）：不进常规掷骰；日内刻跨过黄昏（{@link LeyStorm#duskIndex} 变了、上一 tick 也有人在）、
 *       没有别的天灾时单独掷一次（{@link #duskRoll}：只在大潮夜）。开始时时长截到下一个黎明并记下那个黎明（{@code HoannaEventData.stormDawn}），
 *       <b>每 tick、没人时也查</b>：到了那个黎明就结束，没到但剩余超过离黎明的时间就截短（同日阵的写法）。开始 / 结束各给在场的人放一声。</li>
 * </ul>
 * 开始 / 结束时给海纳尔里的人发红 / 绿字（原作文字，键在 tools/lang/keys/echoanna.json）并广播状态包；第一次遇上天灾的人授予手册步骤
 * {@link #GUIDE_EVENT}（{@code minecraft:impossible} 进度，代码发）。事件本身对玩家的影响（日阵灼烧、地震推人）在 {@code HoannaHazards}，
 * 毒雾的生成在 {@code FumeSpawns}，黑暗只有客户端画面。天灾一律不改方块。
 * <p>
 * API：{@link #current}、{@link #isActive}、{@link #start}、{@link #stop}、{@link #syncTo}；纯函数掷骰 {@link #rollRegular} / {@link #rollDawn}（GameTest 测概率）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class HoannaEvents {

    /** 常规掷骰间隔（tick，按 gameTime） */
    public static final int ROLL_INTERVAL = 20;
    /** 运行中多久置一次 dirty */
    public static final int SAVE_INTERVAL = 20;
    /** 一个游戏日的 tick 数 */
    public static final long DAY_TICKS = 24_000L;
    /** 一天里的第几刻（dayTime mod 24000）算天黑：日阵到这里结束（原版 12000 = 日落，天光从这里开始变暗） */
    public static final int SUN_ARRAY_DUSK = 12_000;
    /** 手册步骤「遇上一次天灾」（没有 display，代码授予） */
    public static final ResourceLocation GUIDE_EVENT = EssentialCraft5.id("guide/hoanna/event");
    /** 进海纳尔时正在事件中，动作栏提示一次 */
    public static final String ONGOING_KEY = "message.essentialcraft5.hoanna_event.ongoing";

    /** 常规掷骰先 nextInt(4) 挑的四种（HoannaEvent.rollable() 每次返回新数组，这里存一份） */
    private static final HoannaEvent[] ROLLABLE = HoannaEvent.rollable();

    private HoannaEvents() {
    }

    // ------------------------------------------------------------------ 查询

    /** 这个世界现在的天灾（不是海纳尔、没有事件都是 NONE） */
    public static HoannaEvent current(ServerLevel level) {
        return ECHoanna.isHoanna(level) ? HoannaEventData.get(level).event() : HoannaEvent.NONE;
    }

    /** 这个世界是海纳尔、而且正在发生这种天灾 */
    public static boolean isActive(ServerLevel level, HoannaEvent event) {
        return !event.isNone() && current(level) == event;
    }

    // ------------------------------------------------------------------ 开始 / 停止

    /**
     * 开始一种天灾（指令、掷骰都走这里）：正在发生别的天灾就先照常结束它（绿字、停止包）；然后红字、广播状态包、给在场的人打手册勾。
     * 调用方保证 level 是海纳尔。日阵的时长截到当天黄昏，已经天黑时不开始（什么都不改，正在发生的天灾也不停）。
     *
     * @param ticks 持续多少 tick（≥ 1；≤ 0 时用这种天灾的默认时长）
     * @return 开始了没有（只有夜里开日阵、NONE 返回 false）
     */
    public static boolean start(ServerLevel level, HoannaEvent event, int ticks) {
        if (event.isNone()) {
            stop(level);
            return false;
        }
        int duration = ticks > 0 ? ticks : event.duration(level.random);
        if (event == HoannaEvent.SUN_ARRAY) {
            duration = Math.min(duration, daylightLeft(level.getDayTime()));
            if (duration <= 0) {
                return false;
            }
        } else if (event == HoannaEvent.LEY_STORM) {
            duration = Math.min(duration, LeyStorm.ticksToDawn(level.getDayTime()));
        }
        HoannaEventData data = HoannaEventData.get(level);
        if (data.active()) {
            finish(level, data);
        }
        data.begin(event, duration);
        if (event == HoannaEvent.LEY_STORM) {
            data.setStormDawn(LeyStorm.nextDawn(level.getDayTime()));
        }
        for (ServerPlayer player : level.players()) {
            if (!(player instanceof FakePlayer)) {
                player.sendSystemMessage(Component.translatable(event.startKey()).withStyle(ChatFormatting.RED));
                awardGuideStep(player);
                if (event == HoannaEvent.LEY_STORM) {
                    notify(player, ECHoannaStorm.LEY_STORM_START.get());
                }
            }
        }
        S2CHoannaEventPacket.broadcast(level, new S2CHoannaEventPacket(event, duration, duration));
        return true;
    }

    /**
     * 离当天黄昏（一天的第 {@value #SUN_ARRAY_DUSK} 刻）还有多少 tick；已经天黑（12000~23999）返回 ≤ 0。纯函数，指令和 GameTest 也用。
     */
    public static int daylightLeft(long dayTime) {
        return SUN_ARRAY_DUSK - (int) Math.floorMod(dayTime, DAY_TICKS);
    }

    /**
     * 停止现在的天灾（绿字、停止包，客户端从当时的强度线性淡出）。没有天灾时什么都不做、返回 NONE（原作 /hoannaevent stop 在这里空指针）。
     *
     * @return 被停掉的天灾
     */
    public static HoannaEvent stop(ServerLevel level) {
        HoannaEventData data = HoannaEventData.get(level);
        if (!data.active()) {
            return HoannaEvent.NONE;
        }
        return finish(level, data);
    }

    private static HoannaEvent finish(ServerLevel level, HoannaEventData data) {
        HoannaEvent ended = data.event();
        data.clear();
        for (ServerPlayer player : level.players()) {
            if (!(player instanceof FakePlayer)) {
                player.sendSystemMessage(Component.translatable(ended.endKey()).withStyle(ChatFormatting.GREEN));
                if (ended == HoannaEvent.LEY_STORM) {
                    notify(player, ECHoannaStorm.LEY_STORM_END.get());
                }
            }
        }
        S2CHoannaEventPacket.broadcast(level, S2CHoannaEventPacket.none());
        return ended;
    }

    /** 只给这个人放一声（环境类，跟着他走） */
    private static void notify(ServerPlayer player, SoundEvent sound) {
        player.playNotifySound(sound, SoundSource.AMBIENT, 1F, 1F);
    }

    // ------------------------------------------------------------------ 计时与掷骰

    @SubscribeEvent
    public static void onHoannaLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side == LogicalSide.SERVER
                && event.level instanceof ServerLevel level && ECHoanna.isHoanna(level)) {
            tick(level, !level.players().isEmpty());
        }
    }

    /**
     * 海纳尔世界的一 tick（服务端，{@code LevelTickEvent} END；GameTest 直接调，occupied 由测试给）。
     * <p>
     * 日阵的黄昏检查放在「没人就返回」<b>之前</b>：停住的日阵在没人时也到黄昏就散。要是放在后面，玩家夜里进海纳尔时
     * {@code PlayerChangedDimensionEvent → syncTo} 会先发「日阵进行中」、打手册勾，下一 tick 又马上绿字说结束。
     * 服务端每 tick 先跑完所有世界的 {@code LevelTickEvent}、再处理玩家（{@code PlayerTickEvent}），所以灼烧那边看不到一个该在黄昏结束的日阵。
     * 多出的开销是每 tick 一次 {@code DimensionDataStorage} 的 HashMap 查找。
     *
     * @param occupied 海纳尔里有没有玩家；没有就只做日阵的黄昏检查
     */
    public static void tick(ServerLevel level, boolean occupied) {
        HoannaEventData data = HoannaEventData.get(level);
        boolean endedAtDusk = false;
        if (data.event() == HoannaEvent.SUN_ARRAY) {
            int daylight = daylightLeft(level.getDayTime());
            if (daylight <= 0) {
                finish(level, data);
                endedAtDusk = true;
            } else {
                data.limitRemaining(daylight);
            }
        } else if (data.event() == HoannaEvent.LEY_STORM) {
            long dayTime = level.getDayTime();
            long dawn = data.stormDawn();
            if (dawn == HoannaEventData.UNSET_DAY) {
                // 旧存档没记截止的黎明：按现在的下一个黎明补上
                dawn = LeyStorm.nextDawn(dayTime);
                data.setStormDawn(dawn);
            }
            if (dayTime >= dawn) {
                // 到黎明结束；不挡这一刻的掷骰：黎明那一次日阵只有这一刻能掷
                finish(level, data);
            } else {
                data.limitRemaining((int) Math.min(Integer.MAX_VALUE, dawn - dayTime));
            }
        }
        if (!occupied) {
            return;
        }
        long now = level.getGameTime();
        boolean continuous = data.lastSeenTick == now - 1;
        data.lastSeenTick = now;
        long day = Math.floorDiv(level.getDayTime(), DAY_TICKS);
        boolean dawn = false;
        if (day != data.lastDay()) {
            dawn = continuous && data.lastDay() != HoannaEventData.UNSET_DAY;
            data.setLastDay(day);
        }
        long duskIndex = LeyStorm.duskIndex(level.getDayTime());
        boolean dusk = false;
        if (duskIndex != data.lastDusk) {
            dusk = continuous && data.lastDusk != HoannaEventData.UNSET_DAY;
            data.lastDusk = duskIndex;
        }
        if (endedAtDusk) {
            return;   // 同倒计时结束的那一 tick：刚结束就不掷骰
        }

        if (data.active()) {
            if (data.countDown() <= 0) {
                finish(level, data);
            } else if (now % SAVE_INTERVAL == 0) {
                data.setDirty();
            }
            return;
        }

        double multiplier = HoannaHazardsConfig.chanceMultiplier();
        if (multiplier <= 0D) {
            return;
        }
        RandomSource random = level.random;
        if (dusk && duskRoll(level, random)) {
            return;
        }
        Predicate<HoannaEvent> enabled = HoannaHazardsConfig::eventEnabled;
        HoannaEvent rolled = HoannaEvent.NONE;
        if (dawn) {
            rolled = rollDawn(random, multiplier, enabled);
        }
        if (rolled.isNone() && now % ROLL_INTERVAL == 0) {
            rolled = rollRegular(random, multiplier, enabled);
        }
        if (!rolled.isNone()) {
            start(level, rolled, rolled.duration(random));
        }
    }

    /**
     * 常规掷骰（每 20 tick）：{@code nextInt(4)} 挑一种、{@code nextFloat() ≤ 概率 × multiplier} 且这种开着才发生；日阵永远不成立
     * （原作 possibleToApply 要求 worldTime % 24000 == 0）。随机数用量固定（一次 nextInt、一次 nextFloat）。
     */
    public static HoannaEvent rollRegular(RandomSource random, double multiplier, Predicate<HoannaEvent> enabled) {
        HoannaEvent candidate = ROLLABLE[random.nextInt(ROLLABLE.length)];
        float roll = random.nextFloat();
        if (candidate.dawnOnly() || !enabled.test(candidate)) {
            return HoannaEvent.NONE;
        }
        return roll <= candidate.chance() * multiplier ? candidate : HoannaEvent.NONE;
    }

    /**
     * 黄昏那一次地脉风暴（{@link #tick} 在日内刻跨过黄昏、连续有人时调；GameTest 直接调）：已经有天灾就不掷；
     * 否则按 {@link LeyStorm#rollDusk}（大潮、{@code ley_storm_enabled}、{@code ley_storm_chance × chance_multiplier}）掷，掷中就开始（时长截到黎明）。
     *
     * @return 开始了没有
     */
    public static boolean duskRoll(ServerLevel level, RandomSource random) {
        if (HoannaEventData.get(level).active()) {
            return false;
        }
        HoannaEvent rolled = LeyStorm.rollDusk(random, HoannaHazardsConfig.chanceMultiplier(), HoannaTide.isSpringTide(level),
                HoannaHazardsConfig.eventEnabled(HoannaEvent.LEY_STORM), HoannaHazardsConfig.leyStormChance());
        return !rolled.isNone() && start(level, rolled, rolled.duration(random));
    }

    /** 黎明那一次：同样先 nextInt(4)，挑中日阵、再 {@code nextFloat() ≤ 0.3 × multiplier} 才发生（每日约 7.5%） */
    public static HoannaEvent rollDawn(RandomSource random, double multiplier, Predicate<HoannaEvent> enabled) {
        HoannaEvent candidate = ROLLABLE[random.nextInt(ROLLABLE.length)];
        float roll = random.nextFloat();
        if (!candidate.dawnOnly() || !enabled.test(candidate)) {
            return HoannaEvent.NONE;
        }
        return roll <= candidate.chance() * multiplier ? candidate : HoannaEvent.NONE;
    }

    // ------------------------------------------------------------------ 同步给新来的人

    @SubscribeEvent
    public static void onHoannaEventLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player, true);
        }
    }

    @SubscribeEvent
    public static void onHoannaEventRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player, !event.isEndConquered());
        }
    }

    @SubscribeEvent
    public static void onHoannaEventDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player, true);
        }
    }

    /**
     * 把这个人所在世界的天灾状态发给他（不在海纳尔发 NONE）。必须在登录 / 换维度 / 重生<b>之后</b>调：客户端每 tick 发现不在海纳尔就清零，
     * 先发会被清掉。在海纳尔、正在事件中：打手册勾，announce 时动作栏提示一次。
     */
    public static void syncTo(ServerPlayer player, boolean announce) {
        if (player instanceof FakePlayer) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!ECHoanna.isHoanna(level)) {
            S2CHoannaEventPacket.send(player, S2CHoannaEventPacket.none());
            return;
        }
        HoannaEventData data = HoannaEventData.get(level);
        if (!data.active()) {
            S2CHoannaEventPacket.send(player, S2CHoannaEventPacket.none());
            return;
        }
        S2CHoannaEventPacket.send(player, new S2CHoannaEventPacket(data.event(), data.remaining(), data.total()));
        awardGuideStep(player);
        if (announce) {
            player.displayClientMessage(Component.translatable(ONGOING_KEY, Component.translatable(data.event().nameKey()))
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    /** 手册步骤「遇上一次天灾」（进度不存在或已完成时什么都不做） */
    public static void awardGuideStep(ServerPlayer player) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(GUIDE_EVENT);
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}
