package com.flameforge.essentialcraft5.corruption;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.creative.CreativeWardItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 腐化效果的服务端节奏（原作 PlayerTickHandler L528~578）：玩家 tick 每 {@value CorruptionEffects#TICK_INTERVAL} tick 往下走一次，
 * 其中每 {@value CorruptionEffects#DRAW_INTERVAL} tick 抽一次效果（{@link #draw}），然后把身上开着的效果各跑一遍（{@link #runEffects}）。
 * <p>
 * 开销：每名玩家每秒最多 25 次掷骰加几次 HashMap 查询（手册打勾），和原作一样可以忽略；不扫方块、不查实体（#13 的爆炸除外，那是原版爆炸自己的开销，约 28 分钟一次）。
 * 假玩家（FakePlayer）不参与；旁观者和死人不抽取、也不发作（原作两处都不查：旁观模式的管理员身上有损伤会被抽到效果，
 * 旁观者的物品栏被扔、被打乱也没有意义）；创造模式照原作照常抽取、发作（伤害本来就伤不到）。
 * 配置总开关 {@code general.corruption.effects_enabled} 关着时不抽、不发作。
 * <p>
 * 顺带给研究手册「腐化效果」一章打勾（{@link #updateGuideProgress}）：这几步没有物品可拿，进度用 {@code minecraft:impossible} 条件，由这里发。
 *
 * <h2>第十批的闸（配置见 api/CorruptionEffects 的 general.corruption）</h2>
 * <ul>
 *   <li>每秒先把超过 {@code max_matrix_damage} 的损伤夹回上限（旧存档、指令设的值）。</li>
 *   <li>抽取（{@link #drawSlot}）：冷却没走完就只扣一次冷却；抽中后冷却拉满（{@link #cooldownSlots}）。候选只留
 *       {@link CorruptionGate#drawable} 放行的（打开过成形的五芒星）。</li>
 *   <li>发作：{@link CorruptionGate#active} 不放行的效果潜伏，留在列表里不发作。</li>
 *   <li>提示：第一次有矩阵损伤时聊天栏一句说明（跟手册打勾同一刻，腐化效果总开关关着时不发）；之后聚集体每次给你加损伤，动作栏最多每
 *       {@value #NOTICE_INTERVAL} 刻提醒一次（{@link #noticePresence}）；抽中时红字之后再加一行灰字，指去五芒星。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class CorruptionTicker {

    /** 抽到效果时发给本人的话（原作 essentialcraft.txt.corruption，红字）；不说是哪个效果，照原作 */
    public static final String GAINED_KEY = "message." + EssentialCraft5.MODID + ".corruption.gained";
    /** 抽到效果时红字下面那一行灰字：去五芒星看是哪一个（只在「治得了才抽」开着时发，这时一定治得了） */
    public static final String GAINED_HINT_KEY = "message." + EssentialCraft5.MODID + ".corruption.gained_hint";
    /** 第一次有矩阵损伤时的说明 */
    public static final String FIRST_DAMAGE_KEY = "message." + EssentialCraft5.MODID + ".corruption.first_damage";
    /** 聚集体正在给你加矩阵损伤（动作栏） */
    public static final String PRESENCE_NOTICE_KEY = "message." + EssentialCraft5.MODID + ".corruption.presence_notice";
    /** 动作栏提醒的最短间隔（刻）：5 分钟 */
    public static final int NOTICE_INTERVAL = 6000;

    /** 上次动作栏提醒的游戏时间（按玩家对象记，重生 / 重新登录后从头算；只在服务端线程读写） */
    private static final Map<ServerPlayer, Long> LAST_NOTICE = new WeakHashMap<>();

    /** 手册步骤的进度（没有 display，data/essentialcraft5/advancements/guide/corruption/*.json） */
    public static final ResourceLocation STEP_MATRIX_DAMAGE = EssentialCraft5.id("guide/corruption/matrix_damage");
    public static final ResourceLocation STEP_BODY = EssentialCraft5.id("guide/corruption/body");
    public static final ResourceLocation STEP_MIND = EssentialCraft5.id("guide/corruption/mind");
    /** 手册「心智悖论」一步：陷入过一次（{@link CorruptionBehaviors} 给上时发；原来靠原版 effects_changed，心智悖论不再是药水效果） */
    public static final ResourceLocation STEP_PARADOX = EssentialCraft5.id("guide/corruption/paradox");
    public static final ResourceLocation STEP_MATRIX = EssentialCraft5.id("guide/corruption/matrix");

    private CorruptionTicker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER
                || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer
                || player.tickCount % CorruptionEffects.TICK_INTERVAL != 0) {
            return;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return;
        }
        // 带着无尽护佑护符（只放创造栏，item/creative/CreativeWardItem）时矩阵损伤保持 0，也就抽不到新效果；已有的效果照常发作
        if (data.getMatrixDamage() != 0 && CreativeWardItem.protects(player)) {
            data.setMatrixDamage(0);
        }
        // 超过上限的（旧存档里攒了几百万的、指令设的）夹回上限
        int cap = CorruptionEffects.maxMatrixDamage();
        if (data.getMatrixDamage() > cap) {
            data.setMatrixDamage(cap);
        }
        updateGuideProgress(player, data);
        if (!player.isAlive() || player.isSpectator()) {
            return;   // 不抽也不发作（runEffects 里另有同样的检查，GameTest 会直接调它）
        }
        if (!CorruptionEffects.effectsEnabled()) {
            return;
        }
        RandomSource random = player.getRandom();
        if (player.tickCount % CorruptionEffects.DRAW_INTERVAL == 0) {
            drawSlot(player, data, random);
        }
        runEffects(player, data, random);
    }

    /** 抽中一个以后要跳过几次抽取：每分钟一次，跳过 {@code draw_cooldown_minutes} − 1 次，下一次能抽中正好在 N 分钟后（0、1 = 不跳） */
    public static int cooldownSlots() {
        return Math.max(0, CorruptionEffects.drawCooldownMinutes() - 1);
    }

    /**
     * 一次抽取的机会（每 {@value CorruptionEffects#DRAW_INTERVAL} 刻在线时间一次）：冷却没走完就扣一次冷却、不抽；
     * 否则照 {@link #draw} 抽，抽中了把冷却拉满。
     *
     * @return 抽到的效果；冷却中、没抽中时 null
     */
    @Nullable
    public static CorruptionEffects.Effect drawSlot(ServerPlayer player, IPlayerData data, RandomSource random) {
        int cooldown = data.getCorruptionCooldown();
        if (cooldown > 0) {
            data.setCorruptionCooldown(cooldown - 1);
            return null;
        }
        CorruptionEffects.Effect effect = draw(player, data, random);
        if (effect != null) {
            data.setCorruptionCooldown(cooldownSlots());
        }
        return effect;
    }

    /**
     * 抽一次（原作 L538~574）：以 min(1, (损伤 + 1) / 36000) 的概率抽；候选是门槛（成本 × {@code draw_threshold_multiplier}）≤ 损伤、身上还没有、开着的效果，身体类权重 ×3，
     * 再只留这名玩家抽得到的（{@link CorruptionGate#drawable}：打开过成形的五芒星，「治得了才抽」关着时不筛）；
     * 抽中就扣掉它的门槛、加进玩家的效果列表、发一句红字（闸开着时再加一行灰字指去五芒星）。损伤不会自然消退，只在抽中时减少。
     * 不看冷却（冷却在 {@link #drawSlot}）。
     *
     * @return 抽到的效果；没抽中、没有候选时 null
     */
    @Nullable
    public static CorruptionEffects.Effect draw(ServerPlayer player, IPlayerData data, RandomSource random) {
        int damage = data.getMatrixDamage();
        if (!CorruptionEffects.rollDraw(damage, random)) {
            return null;
        }
        CorruptionEffects.Effect effect = CorruptionEffects.draw(damage, data.getCorruptionEffects(), random,
                candidate -> CorruptionGate.drawable(candidate, data));
        if (effect == null || !data.addCorruptionEffect(effect.id())) {
            return null;
        }
        data.setMatrixDamage(Math.max(0, damage - CorruptionEffects.drawThreshold(effect)));
        player.sendSystemMessage(Component.translatable(GAINED_KEY).withStyle(ChatFormatting.RED));
        if (CorruptionEffects.drawRequiresCureTier()) {
            player.sendSystemMessage(Component.translatable(GAINED_HINT_KEY).withStyle(ChatFormatting.GRAY));
        }
        return effect;
    }

    /**
     * 把身上的效果各跑一遍（每秒一次）：认不出的 id、被配置关掉的（总开关、矩阵类开关、disabled_effects）跳过，留在列表里、重新打开后恢复；
     * 打开过的五芒星治不了的潜伏（{@link CorruptionGate#active}），同样留着、打开够档的五芒星以后才发作。
     *
     * @return 这一轮发作了几个
     */
    public static int runEffects(ServerPlayer player, IPlayerData data, RandomSource random) {
        if (!player.isAlive() || player.isSpectator()) {
            return 0;
        }
        int fired = 0;
        // 复制一份：发作的后果（扔东西触发的事件、死亡）理论上可能回头改列表
        for (String id : List.copyOf(data.getCorruptionEffects())) {
            CorruptionEffects.Effect effect = CorruptionEffects.byId(id);
            if (effect == null || !effect.isEnabled() || !CorruptionGate.active(effect, data)) {
                continue;
            }
            if (!player.isAlive()) {
                break;   // 前一个效果把人打死了
            }
            if (CorruptionBehaviors.tick(player, data, effect, random)) {
                fired++;
            }
        }
        return fired;
    }

    /**
     * 手册「腐化效果」一章的打勾：有过矩阵损伤（或身上已经有效果）→「矩阵损伤」；身上有身体 / 心智 / 矩阵类效果 →对应那一步。
     * 心智悖论那一步用原版 {@code effects_changed} 条件，不在这里。每名玩家每秒最多几次查表，已完成的直接跳过。
     * 「矩阵损伤」那一步第一次打勾、而且是真有损伤时，聊天栏发一句说明（损伤从哪来、怎么防、手册哪一章）；
     * 那句话说「攒多了会凝成腐化效果」，腐化效果总开关关着的世界里不成立，所以关着时不发（那时矩阵损伤没有别的后果）。
     *
     * @return 这次发没发那句说明
     */
    public static boolean updateGuideProgress(ServerPlayer player, IPlayerData data) {
        List<String> effects = data.getCorruptionEffects();
        if (data.getMatrixDamage() <= 0 && effects.isEmpty()) {
            return false;
        }
        boolean noticed = false;
        if (award(player, STEP_MATRIX_DAMAGE) && data.getMatrixDamage() > 0 && CorruptionEffects.effectsEnabled()) {
            player.sendSystemMessage(Component.translatable(FIRST_DAMAGE_KEY).withStyle(ChatFormatting.LIGHT_PURPLE));
            noticed = true;
        }
        boolean body = false;
        boolean mind = false;
        boolean matrix = false;
        for (String id : effects) {
            CorruptionEffects.Effect effect = CorruptionEffects.byId(id);
            if (effect != null) {
                switch (effect.category()) {
                    case BODY -> body = true;
                    case MIND -> mind = true;
                    case MATRIX -> matrix = true;
                }
            }
        }
        if (body) {
            award(player, STEP_BODY);
        }
        if (mind) {
            award(player, STEP_MIND);
        }
        if (matrix) {
            award(player, STEP_MATRIX);
        }
        return noticed;
    }

    /**
     * 聚集体刚给这名玩家加了矩阵损伤：动作栏提醒一句，同一个玩家对象最多每 {@value #NOTICE_INTERVAL} 刻一次
     * （常驻基地的人不被刷屏；重生、重新登录后的第一次照样提醒）。
     *
     * @return 这次提醒了没有
     */
    public static boolean noticePresence(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = LAST_NOTICE.get(player);
        if (last != null && now >= last && now - last < NOTICE_INTERVAL) {
            return false;
        }
        LAST_NOTICE.put(player, now);
        player.displayClientMessage(Component.translatable(PRESENCE_NOTICE_KEY).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    /** 发一个只靠代码完成的进度（条件全部补齐）；进度不存在（数据包删了）或已完成返回 false */
    public static boolean award(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return false;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
        return true;
    }
}
