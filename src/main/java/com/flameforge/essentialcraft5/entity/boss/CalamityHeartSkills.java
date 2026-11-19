package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.entity.PoisonFumeEntity;
import com.flameforge.essentialcraft5.hoanna.HoannaHazards;
import com.flameforge.essentialcraft5.network.S2CBossFxPacket;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECHoannaHazards;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 天灾之心的四相招式（batch13.md §5.3.5；MS §2.6.2 的招式表）。两招之间隔 {@code skill_interval} 刻（失衡期 × {@code unbalanced_cooldown}），能用哪几相由
 * {@link CalamityHeartEntity#activeAspects} 给（护盾期：灾柱还在的那几相；失衡期：当前这一对），几相轮着来、每相两招交替；崩解时一招都不放。
 * 一律只打名单里在线的生存 / 冒险参战者（{@link CalamityHeartEntity#isSkillTarget}）：宠物、村民、路过的玩家都不碰；不破坏方块、不放方块。
 * 有预警的招 = 固定值 + 目标最大生命 × 比例，日阵用 {@code calamity_solar}、其余 {@code calamity}（都带 {@code bypasses_armor}、{@code undodgeable}）；
 * 没有预警的暗袭、落石吃护甲（{@code mobAttack}）。
 * <table>
 *   <tr><th>相</th><th>招</th><th>做法</th></tr>
 *   <tr><td rowspan="3">日阵</td><td>天光棱线</td><td>{@code prism_lines} 条横贯场地的线，地面金线预警 {@code prism_warning_ticks}，线两侧 {@value #PRISM_WIDTH} 格内挨伤害并着火</td></tr>
 *   <tr><td>烈日标记</td><td>标一名参战者，头顶光柱 {@code mark_warning_ticks}；落下那一刻他头顶 +2 格还看得见天就挨（钻到方块下面就躲开）</td></tr>
 *   <tr><td>残响 · 正午</td><td>预警 {@code noon_warning_ticks} 后，所有露天的参战者挨</td></tr>
 *   <tr><td rowspan="3">毒雾</td><td>瘴云</td><td>在参战者附近放 {@code fumes_per_cast} 团毒雾（复用毒雾实体，只毒参战者，同时 ≤ {@code max_fumes}）</td></tr>
 *   <tr><td>腐蚀吐息</td><td>对一名参战者方向的锥形（{@code breath_range} 格、半角约 40°），预警后挨伤害 + 腐蚀</td></tr>
 *   <tr><td>残响 · 弥漫</td><td>另放一份同样多的毒雾，活 {@code echo_fume_ticks}</td></tr>
 *   <tr><td rowspan="2">黑暗</td><td>熄光</td><td>预警后熄光 {@code blackout_ticks}（参战者屏幕边缘压暗、它只剩眼睛），期间 {@code blackout_strikes} 次暗袭：
 *       从它身上朝一名参战者起手时的位置扑过去，{@code strike_warning_ticks} 后还站在那里（{@value #STRIKE_RADIUS} 格内）就挨；
 *       主手或副手拿光源的人看得见本体和轨迹</td></tr>
 *   <tr><td>残响 · 长夜</td><td>熄光 {@code long_night_ticks}、{@code long_night_strikes} 次暗袭</td></tr>
 *   <tr><td rowspan="3">地震</td><td>震波</td><td>{@code quake_rings} 圈从它脚下往外扩的地面波，每圈预警 {@code ring_warning_ticks}；圈扫过时脚踩地面的人挨伤害并被抛起（跳起来就躲开）</td></tr>
 *   <tr><td>落石</td><td>{@code rubble_count} 块（同时 ≤ {@code max_rubble}），落点先有影子，砸中挨（落地即消失、不放方块）</td></tr>
 *   <tr><td>残响 · 断层</td><td>{@code fault_rings} 圈震波接连扩开</td></tr>
 * </table>
 * 加强那一轮（tools/plan/calamity_heart_v2.md §2.2）另有几招不进轮换，由本体在各自的时机调，伤害数值在 {@code ECCalamityHeart.Config.Fury}：
 * <table>
 *   <tr><th>招</th><th>时机</th><th>做法</th></tr>
 *   <tr><td>灾柱反噬</td><td>每根灾柱第一次掉到一半</td><td>灾柱脚下 {@code backlash_radius} 格红圈预警 {@code backlash_warning_ticks}，圈里的人挨伤害、被往外推</td></tr>
 *   <tr><td>四灾归心</td><td>四根都倒、进失衡期</td><td>以它为中心 {@code converge_radius} 格的安全圈，预警 {@code converge_warning_ticks}，圈外的人挨；预警期间它停在原地</td></tr>
 *   <tr><td>合相技</td><td>失衡期换到一对相后 {@code combo_delay_ticks}</td><td>蚀日（日阵 + 毒雾）：{@value #COMBO_LINES} 条过它身体的棱线 + {@value #COMBO_FUMES} 团毒雾；
 *       坠夜（黑暗 + 地震）：熄光，期间两批落石；瘴影（毒雾 + 黑暗）：熄光，{@value #COMBO_STRIKES} 次暗袭、落点留毒雾；熔裂（地震 + 日阵）：震波和 {@value #COMBO_LINES} 条棱线同时来。
 *       过它身体的棱线每放一次整体转 22.5°（上一次的空当这次正好压在线上）</td></tr>
 *   <tr><td>垂死反扑</td><td>崩解里每 {@code collapse_echo_interval}</td><td>交替：断层（{@code fault_rings} 圈震波）/ 棱线齐射（{@code volley_lines} 条过参战者的棱线）</td></tr>
 * </table>
 * 合相技、反扑沿用原招的预警、判定和伤害配置；过它身体的棱线、齐射不受 {@code max_prisms} 限制（一次放完，次数由时机限住）。
 * 只在服务端用；不存盘（读档后预警中的招都没了，毒雾、落石本来就不存盘）。
 */
public final class CalamityHeartSkills {

    public static final String KIND_FUME = "calamity_fume";
    public static final String KIND_ECHO_FUME = "calamity_echo_fume";
    public static final String KIND_RUBBLE = "calamity_rubble";
    /** 熄光晕影的颜色 */
    public static final int BLACKOUT_COLOR = 0x14051E;
    /** 天光棱线两侧多宽算打中 */
    public static final double PRISM_WIDTH = 1.5D;
    /** 暗袭：落下时离起手那刻的位置这么近算打中 */
    public static final double STRIKE_RADIUS = 1.8D;
    /** 烈日标记的预警光柱多高（格） */
    public static final double MARK_COLUMN = 8D;
    /** 腐蚀吐息锥形的半角余弦（约 40°） */
    public static final double BREATH_COS = 0.766D;
    /** 震波每刻往外扩多少格 */
    public static final double RING_SPEED = 0.6D;
    /** 震波圈前沿多厚算扫到 */
    public static final double RING_BAND = 0.9D;
    /** 震波上抛的速度 */
    public static final double RING_LAUNCH = 0.55D;
    /** 招式够得着场地外多远的人 */
    public static final double REACH_SLACK = 8D;
    /** 合相技里过它身体的棱线条数 */
    public static final int COMBO_LINES = 4;
    /** 合相技 · 蚀日放几团毒雾 */
    public static final int COMBO_FUMES = 3;
    /** 合相技 · 瘴影几次暗袭 */
    public static final int COMBO_STRIKES = 4;
    /** 灾柱反噬往外推的水平速度、上抛速度 */
    public static final double BACKLASH_PUSH = 0.9D;
    public static final double BACKLASH_LIFT = 0.35D;
    /** 灾柱反噬：和灾柱脚下高差超过这么多不挨 */
    public static final double BACKLASH_HEIGHT = 4D;
    /** 灾柱反噬炸开的火红（预警圈仍用危险红，读招要紧）、归心安全圈的颜色 */
    public static final int BACKLASH_COLOR = 0xD0281E;
    public static final int CONVERGE_COLOR = 0xFFD27A;

    /** 合相技每一对的两相（顺序同 {@link #combo} 的 pair：蚀日、坠夜、瘴影、熔裂），起手画面取这两种颜色 */
    private static final CalamityHeartEntity.Aspect[][] COMBO_ASPECTS = {
            {CalamityHeartEntity.Aspect.SUN_ARRAY, CalamityHeartEntity.Aspect.FUMES},
            {CalamityHeartEntity.Aspect.DARKNESS, CalamityHeartEntity.Aspect.EARTHQUAKE},
            {CalamityHeartEntity.Aspect.DARKNESS, CalamityHeartEntity.Aspect.FUMES},
            {CalamityHeartEntity.Aspect.EARTHQUAKE, CalamityHeartEntity.Aspect.SUN_ARRAY},
    };

    /** 招式的种类：轮着放的普通招、灾柱倒下时的残响、别的时机放的特殊招（不进轮换） */
    public enum Kind {
        NORMAL, ECHO, SPECIAL
    }

    /** 招式：所属的相（特殊招没有）、种类 */
    public enum Skill {
        PRISM(CalamityHeartEntity.Aspect.SUN_ARRAY, Kind.NORMAL),
        MARK(CalamityHeartEntity.Aspect.SUN_ARRAY, Kind.NORMAL),
        MIASMA(CalamityHeartEntity.Aspect.FUMES, Kind.NORMAL),
        BREATH(CalamityHeartEntity.Aspect.FUMES, Kind.NORMAL),
        BLACKOUT(CalamityHeartEntity.Aspect.DARKNESS, Kind.NORMAL),
        SHOCKWAVE(CalamityHeartEntity.Aspect.EARTHQUAKE, Kind.NORMAL),
        RUBBLE(CalamityHeartEntity.Aspect.EARTHQUAKE, Kind.NORMAL),
        NOON(CalamityHeartEntity.Aspect.SUN_ARRAY, Kind.ECHO),
        PERVADE(CalamityHeartEntity.Aspect.FUMES, Kind.ECHO),
        LONG_NIGHT(CalamityHeartEntity.Aspect.DARKNESS, Kind.ECHO),
        FAULT(CalamityHeartEntity.Aspect.EARTHQUAKE, Kind.ECHO),
        /** 灾柱反噬（每根第一次掉到一半） */
        BACKLASH(null, Kind.SPECIAL),
        /** 四灾归心（最后一根灾柱倒下） */
        CONVERGE(null, Kind.SPECIAL),
        /** 合相技里延后落下的一批落石 */
        VOLLEY(null, Kind.SPECIAL);

        @Nullable
        private final CalamityHeartEntity.Aspect aspect;
        private final Kind kind;

        Skill(@Nullable CalamityHeartEntity.Aspect aspect, Kind kind) {
            this.aspect = aspect;
            this.kind = kind;
        }

        @Nullable
        public CalamityHeartEntity.Aspect aspect() {
            return aspect;
        }

        public boolean echo() {
            return kind == Kind.ECHO;
        }

        public Kind kind() {
            return kind;
        }

        /** 这一相的残响 */
        public static Skill echoOf(CalamityHeartEntity.Aspect aspect) {
            return switch (aspect) {
                case SUN_ARRAY -> NOON;
                case FUMES -> PERVADE;
                case DARKNESS -> LONG_NIGHT;
                case EARTHQUAKE -> FAULT;
            };
        }
    }

    private final CalamityHeartEntity heart;
    private final List<Effect> effects = new ArrayList<>();
    private final Map<CalamityHeartEntity.Aspect, Integer> alternate = new EnumMap<>(CalamityHeartEntity.Aspect.class);
    private int rotation;
    private int cooldown;
    /** 熄光：还剩多少刻（−1 = 没在熄光）、预警还剩多少刻、这一次一共几刻、几次暗袭、放了几次 */
    private int blackoutLeft = -1;
    private int blackoutWarn = -1;
    private int blackoutTotal;
    private int blackoutStrikes;
    private int strikesDone;
    /** 这一次熄光的暗袭落点留一团毒雾（合相技 · 瘴影） */
    private boolean blackoutFumes;
    /** 合相技 · 蚀日 / 熔裂的十字棱线每次转多少（45° 的倍数） */
    private int crossTurn;

    CalamityHeartSkills(CalamityHeartEntity heart) {
        this.heart = heart;
    }

    private static ECCalamityHeart.Config.Skills s() {
        return ECCalamityHeart.Config.values().skills();
    }

    private static ECCalamityHeart.Config.Fury u() {
        return ECCalamityHeart.Config.values().fury();
    }

    // ------------------------------------------------------------------
    // 读数（GameTest 用）
    // ------------------------------------------------------------------

    public int cooldown() {
        return cooldown;
    }

    /** 正在预警 / 进行中的招有几个（不含已经放出去的实体） */
    public int pending() {
        return effects.size();
    }

    /** 某一种还在预警的有几个 */
    public int pending(Skill skill) {
        int count = 0;
        for (Effect effect : effects) {
            if (effect.skill == skill) {
                count++;
            }
        }
        return count;
    }

    /** 正在熄光（含预警） */
    public boolean blackoutActive() {
        return blackoutLeft >= 0 || blackoutWarn >= 0;
    }

    /** 四灾归心还在预警（它停在原地） */
    public boolean converging() {
        return pending(Skill.CONVERGE) > 0;
    }

    /** 名单里在线、range 格内的参战者（range 从它自己量） */
    public List<ServerPlayer> targets(double range) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : heart.bossRoster().online()) {
            if (heart.isSkillTarget(player) && player.distanceTo(heart) <= range) {
                out.add(player);
            }
        }
        return out;
    }

    private double reach() {
        return ECCalamityHeart.Config.values().fight().arenaRadius() + REACH_SLACK;
    }

    // ------------------------------------------------------------------
    // 每刻
    // ------------------------------------------------------------------

    /** 每刻（{@link CalamityHeartEntity#serverBossStep}，崩解、散去时不调）：推进预警中的招，冷却到了按相轮着放下一招 */
    void tick(ServerLevel server) {
        tickEffects(server);
        if (heart.collapsing() || heart.dissipating()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        List<ServerPlayer> targets = targets(reach());
        List<CalamityHeartEntity.Aspect> aspects = heart.activeAspects();
        if (targets.isEmpty() || aspects.isEmpty()) {
            return;
        }
        for (int i = 0; i < aspects.size(); i++) {
            CalamityHeartEntity.Aspect aspect = aspects.get(Math.floorMod(rotation + i, aspects.size()));
            Skill skill = pick(aspect);
            if (skill != null) {
                rotation = Math.floorMod(rotation + i + 1, aspects.size());
                start(skill, targets.get(heart.getRandom().nextInt(targets.size())));
                return;
            }
        }
    }

    /** 这一相轮到的招（两招交替，放不了的跳过；都放不了时 null） */
    @Nullable
    private Skill pick(CalamityHeartEntity.Aspect aspect) {
        List<Skill> pool = new ArrayList<>(2);
        for (Skill skill : Skill.values()) {
            if (skill.aspect == aspect && skill.kind == Kind.NORMAL) {
                pool.add(skill);
            }
        }
        int start = alternate.getOrDefault(aspect, 0);
        for (int i = 0; i < pool.size(); i++) {
            Skill skill = pool.get(Math.floorMod(start + i, pool.size()));
            if (available(skill)) {
                alternate.put(aspect, Math.floorMod(start + i + 1, pool.size()));
                return skill;
            }
        }
        return null;
    }

    private boolean available(Skill skill) {
        ECCalamityHeart.Config.Skills k = s();
        return switch (skill) {
            case PRISM -> k.prismLines() > 0 && pending(Skill.PRISM) < k.maxPrisms();
            case MARK -> pending(Skill.MARK) == 0;
            case MIASMA -> k.fumesPerCast() > 0 && heart.skills().hasRoom(KIND_FUME, k.maxFumes());
            case BLACKOUT -> !blackoutActive();
            case SHOCKWAVE -> k.quakeRings() > 0;
            case RUBBLE -> k.rubbleCount() > 0 && heart.skills().hasRoom(KIND_RUBBLE, k.maxRubble());
            default -> true;
        };
    }

    /**
     * 推进预警中的招和熄光（GameTest 直接调）。遍历的是快照：结算时把召唤者打死，会在同一刻同步走契约失败 → 散去 → {@link #cancelAll}，
     * 把 {@code effects} 清空；直接用迭代器会在下一步抛 ConcurrentModificationException（实体 tick 里抛出就是崩服）。
     * 已经被清掉的招跳过，不在散去后接着结算。
     */
    public void tickEffects(ServerLevel server) {
        tickBlackout(server);
        if (effects.isEmpty()) {
            return;
        }
        for (Effect effect : List.copyOf(effects)) {
            if (!effects.contains(effect)) {
                continue;
            }
            if (effect.tick(server)) {
                effects.remove(effect);
            }
        }
    }

    /** 放一招（GameTest 直接调；残响也走这里）：起手、上冷却 */
    public void start(Skill skill, ServerPlayer target) {
        if (!(heart.level() instanceof ServerLevel server)) {
            return;
        }
        cooldown = (int) Math.round(ECCalamityHeart.Config.values().fight().skillInterval() * heart.cooldownMultiplier());
        heart.getLookControl().setLookAt(target, 30F, 30F);
        ECCalamityHeart.Config.Skills k = s();
        switch (skill) {
            case PRISM -> prisms(server, k);
            case MARK -> {
                add(new Mark(target.getUUID(), k.markWarnTicks()));
                target.displayClientMessage(Component.translatable("message.essentialcraft5.calamity_heart.mark"), true);
                server.playSound(null, target.getX(), target.getY(), target.getZ(), ECCalamityHeart.SOUND_MARK.get(), SoundSource.HOSTILE, 1.5F, 1.2F);
            }
            case MIASMA -> fumes(server, KIND_FUME, k.maxFumes(), k.fumesPerCast(), k.fumeTicks());
            case BREATH -> {
                Vec3 dir = new Vec3(target.getX() - heart.getX(), 0D, target.getZ() - heart.getZ());
                add(new Breath(dir.lengthSqr() < 1.0E-6D ? new Vec3(1D, 0D, 0D) : dir.normalize(), k.breathWarnTicks()));
            }
            case BLACKOUT -> beginBlackout(k.blackoutWarnTicks(), k.blackoutTicks(), k.blackoutStrikes(), false);
            case SHOCKWAVE -> rings(k.quakeRings(), k.ringWarnTicks());
            case RUBBLE -> rubble(server, k);
            case NOON -> {
                add(new Noon(k.noonWarnTicks()));
                heart.tell("message.essentialcraft5.calamity_heart.echo.sun_array");
            }
            case PERVADE -> {
                fumes(server, KIND_ECHO_FUME, k.maxFumes(), k.maxFumes(), k.echoFumeTicks());
                heart.tell("message.essentialcraft5.calamity_heart.echo.fumes");
            }
            case LONG_NIGHT -> {
                beginBlackout(k.blackoutWarnTicks(), k.longNightTicks(), k.longNightStrikes(), false);
                heart.tell("message.essentialcraft5.calamity_heart.echo.darkness");
            }
            case FAULT -> {
                rings(k.faultRings(), k.ringWarnTicks());
                heart.tell("message.essentialcraft5.calamity_heart.echo.earthquake");
            }
            default -> {
                // 特殊招有自己的入口（backlash / converge / combo / deathThroe），不经这里
            }
        }
        if (skill.echo()) {
            server.playSound(null, heart.getX(), heart.getY(), heart.getZ(), ECCalamityHeart.SOUND_ECHO.get(), SoundSource.HOSTILE, 3F, 1F);
        }
    }

    /** 一根灾柱倒了：放那一相的残响（不等冷却；名单里没人时不放） */
    void echo(ServerLevel server, CalamityHeartEntity.Aspect aspect) {
        List<ServerPlayer> targets = targets(reach());
        if (!targets.isEmpty()) {
            start(Skill.echoOf(aspect), targets.get(heart.getRandom().nextInt(targets.size())));
        }
    }

    /** 打断还在预警的招（失衡开始）；客户端的预警一起撤掉 */
    public void cancelCast() {
        effects.clear();
        BossWarnings.cancel(heart);
    }

    /** 打断一切：预警中的招、熄光（崩解、散去、死亡、移出世界） */
    public void cancelAll(ServerLevel server) {
        effects.clear();
        BossWarnings.cancel(heart);
        if (blackoutActive()) {
            endBlackout();
        }
    }

    /** 加一个预警中的招，同时把预警发给附近的客户端（{@link BossWarnings}，客户端自己画完，预警期不再逐刻发粒子） */
    private void add(Effect effect) {
        effects.add(effect);
        effect.announce();
    }

    // ------------------------------------------------------------------
    // 日阵
    // ------------------------------------------------------------------

    private void prisms(ServerLevel server, ECCalamityHeart.Config.Skills k) {
        prismsThroughTargets(server, Math.min(k.prismLines(), Math.max(0, k.maxPrisms() - pending(Skill.PRISM))));
    }

    /** count 条随机朝向、过随机一名参战者（没人时过场地中心）的棱线 */
    private void prismsThroughTargets(ServerLevel server, int count) {
        Vec3 center = heart.arenaCenter();
        double radius = ECCalamityHeart.Config.values().fight().arenaRadius();
        List<ServerPlayer> targets = targets(reach());
        for (int i = 0; i < count; i++) {
            Vec3 through = targets.isEmpty() ? center : targets.get(heart.getRandom().nextInt(targets.size())).position();
            double angle = heart.getRandom().nextDouble() * Math.PI;
            Vec3 dir = new Vec3(Math.cos(angle), 0D, Math.sin(angle));
            add(new Prism(new Vec3(through.x, heart.floorTop(), through.z), dir, radius, s().prismWarnTicks()));
        }
        server.playSound(null, heart.getX(), heart.getY(), heart.getZ(), ECCalamityHeart.SOUND_PRISM.get(), SoundSource.HOSTILE, 1.5F, 1.4F);
    }

    /** lines 条过它身体、均分半圈的棱线；整体角度每放一次转 22.5°（{@link #crossTurn}） */
    private void cross(ServerLevel server, int lines) {
        if (lines <= 0) {
            return;
        }
        double radius = ECCalamityHeart.Config.values().fight().arenaRadius();
        Vec3 through = new Vec3(heart.getX(), heart.floorTop(), heart.getZ());
        double base = (crossTurn++ & 7) * Math.PI / 8D;
        for (int i = 0; i < lines; i++) {
            double angle = base + Math.PI * i / lines;
            add(new Prism(through, new Vec3(Math.cos(angle), 0D, Math.sin(angle)), radius, s().prismWarnTicks()));
        }
        server.playSound(null, heart.getX(), heart.getY(), heart.getZ(), ECCalamityHeart.SOUND_PRISM.get(), SoundSource.HOSTILE, 2F, 1.1F);
    }

    /** 这一点在不在这条线的打击范围里（水平距离 ≤ {@value #PRISM_WIDTH}、在场地里） */
    public static boolean onPrism(Vec3 through, Vec3 dir, Vec3 center, double radius, Vec3 at) {
        double px = at.x - through.x;
        double pz = at.z - through.z;
        double cross = Math.abs(px * dir.z - pz * dir.x);
        return cross <= PRISM_WIDTH && Math.hypot(at.x - center.x, at.z - center.z) <= radius + PRISM_WIDTH;
    }

    // ------------------------------------------------------------------
    // 毒雾
    // ------------------------------------------------------------------

    /** 在参战者附近放毒雾（只毒参战者）；返回放了几团 */
    public int fumes(ServerLevel server, String kind, int max, int count, int lifetime) {
        List<ServerPlayer> targets = targets(reach());
        if (targets.isEmpty()) {
            return 0;
        }
        int placed = 0;
        for (int i = 0; i < count && heart.skills().hasRoom(kind, max); i++) {
            ServerPlayer near = targets.get(heart.getRandom().nextInt(targets.size()));
            double angle = heart.getRandom().nextDouble() * Math.PI * 2D;
            double distance = 3D + heart.getRandom().nextDouble() * 3D;
            PoisonFumeEntity fume = ECHoannaHazards.POISON_FUME.get().create(server);
            if (fume == null) {
                break;
            }
            fume.moveTo(near.getX() + Math.cos(angle) * distance, near.getY() + 1D, near.getZ() + Math.sin(angle) * distance, 0F, 0F);
            fume.bindBoss(heart, lifetime);
            if (server.addFreshEntity(fume) && !fume.isRemoved()) {
                heart.skills().track(kind, fume);
                placed++;
            }
        }
        return placed;
    }

    /** 在这一点放一团毒雾（只毒参战者；满了就不放）；放成了返回 true */
    public boolean fumeAt(ServerLevel server, Vec3 at, String kind, int max, int lifetime) {
        if (!heart.skills().hasRoom(kind, max)) {
            return false;
        }
        PoisonFumeEntity fume = ECHoannaHazards.POISON_FUME.get().create(server);
        if (fume == null) {
            return false;
        }
        fume.moveTo(at.x, at.y + 1D, at.z, 0F, 0F);
        fume.bindBoss(heart, lifetime);
        if (server.addFreshEntity(fume) && !fume.isRemoved()) {
            heart.skills().track(kind, fume);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 黑暗
    // ------------------------------------------------------------------

    private void beginBlackout(int warn, int length, int strikes, boolean fumes) {
        if (blackoutLeft >= 0) {
            // 正在熄光：残响把它拉长
            blackoutLeft = Math.max(blackoutLeft, length);
            blackoutTotal = Math.max(blackoutTotal, length);
            blackoutStrikes += strikes;
            blackoutFumes |= fumes;
            return;
        }
        blackoutWarn = Math.max(1, warn);
        blackoutTotal = Math.max(1, length);
        blackoutStrikes = Math.max(0, strikes);
        blackoutFumes = fumes;
        strikesDone = 0;
        heart.tell("message.essentialcraft5.calamity_heart.blackout_warning");
    }

    private void tickBlackout(ServerLevel server) {
        if (blackoutWarn >= 0) {
            if (heart.tickCount % 3 == 0) {
                server.sendParticles(ParticleTypes.SQUID_INK, heart.getX(), heart.getY() + 1.5D, heart.getZ(), 6, 1D, 1D, 1D, 0.02D);
            }
            if (--blackoutWarn < 0) {
                blackoutLeft = blackoutTotal;
                heart.setBlackout(true);
                S2CBossFxPacket.send(heart.bossRoster().online(), S2CBossFxPacket.of(S2CBossFxPacket.BLACKOUT, heart, 1F, (float) BLACKOUT_COLOR));
                server.playSound(null, heart.getX(), heart.getY(), heart.getZ(), ECCalamityHeart.SOUND_BLACKOUT.get(), SoundSource.HOSTILE, 2F, 0.6F);
                heart.tell("message.essentialcraft5.calamity_heart.blackout");
            }
            return;
        }
        if (blackoutLeft < 0) {
            return;
        }
        int elapsed = blackoutTotal - blackoutLeft;
        if (strikesDone < blackoutStrikes && elapsed >= (long) blackoutTotal * (strikesDone + 1) / (blackoutStrikes + 1)) {
            strikesDone++;
            List<ServerPlayer> targets = targets(reach());
            if (!targets.isEmpty()) {
                ServerPlayer target = targets.get(heart.getRandom().nextInt(targets.size()));
                add(new Strike(target.getUUID(), target.position(), heart.position().add(0D, heart.getBbHeight() * 0.5D, 0D),
                        s().strikeWarnTicks(), blackoutFumes));
            }
        }
        if (--blackoutLeft < 0) {
            endBlackout();
        }
    }

    private void endBlackout() {
        blackoutLeft = -1;
        blackoutWarn = -1;
        blackoutFumes = false;
        heart.setBlackout(false);
        S2CBossFxPacket.send(heart.bossRoster().online(), S2CBossFxPacket.of(S2CBossFxPacket.BLACKOUT, heart, 0F, (float) BLACKOUT_COLOR));
    }

    // ------------------------------------------------------------------
    // 地震
    // ------------------------------------------------------------------

    private void rings(int count, int warn) {
        Vec3 origin = new Vec3(heart.getX(), heart.floorTop(), heart.getZ());
        for (int i = 0; i < count; i++) {
            add(new Ring(origin, Math.max(1, warn) * (i + 1)));
        }
    }

    private void rubble(ServerLevel server, ECCalamityHeart.Config.Skills k) {
        rubble(server, k, k.rubbleCount());
    }

    private void rubble(ServerLevel server, ECCalamityHeart.Config.Skills k, int count) {
        List<ServerPlayer> targets = targets(reach());
        if (targets.isEmpty()) {
            return;
        }
        for (int i = 0; i < count && heart.skills().hasRoom(KIND_RUBBLE, k.maxRubble()); i++) {
            ServerPlayer near = targets.get(heart.getRandom().nextInt(targets.size()));
            double ox = i == 0 ? 0D : (heart.getRandom().nextDouble() * 2D - 1D) * 2.5D;
            double oz = i == 0 ? 0D : (heart.getRandom().nextDouble() * 2D - 1D) * 2.5D;
            CalamityRubbleEntity rock = ECCalamityHeart.CALAMITY_RUBBLE.get().create(server);
            if (rock == null) {
                break;
            }
            rock.bind(heart, new Vec3(near.getX() + ox, near.getY(), near.getZ() + oz), k.rubbleWarnTicks());
            if (server.addFreshEntity(rock) && !rock.isRemoved()) {
                heart.skills().track(KIND_RUBBLE, rock);
                rock.announce();
            }
        }
    }

    // ------------------------------------------------------------------
    // 特殊招（不进轮换，本体在各自的时机调）
    // ------------------------------------------------------------------

    /** 灾柱反噬（灾柱第一次掉到一半时本体调）：base 是灾柱脚下；伤害和比例都是 0 时不放。放了返回 true */
    public boolean backlash(ServerLevel server, Vec3 base) {
        ECCalamityHeart.Config.Fury u = u();
        if (!(u.backlashDamage() > 0D) && !(u.backlashPercent() > 0D)) {
            return false;
        }
        add(new Backlash(base, u.backlashRadius(), u.backlashWarnTicks()));
        server.playSound(null, base.x, base.y, base.z, ECCalamityHeart.SOUND_PULSE.get(), SoundSource.HOSTILE, 2F, 1.4F);
        return true;
    }

    /** 四灾归心（进失衡期那一刻本体调，在打断预警中的招之后）：伤害和比例都是 0 时不放。放了返回 true */
    public boolean converge(ServerLevel server) {
        ECCalamityHeart.Config.Fury u = u();
        if (!(u.convergeDamage() > 0D) && !(u.convergePercent() > 0D)) {
            return false;
        }
        add(new Converge(new Vec3(heart.getX(), heart.floorTop(), heart.getZ()), u.convergeRadius(), u.convergeWarnTicks()));
        server.playSound(null, heart.getX(), heart.getY(), heart.getZ(), ECCalamityHeart.SOUND_ECHO.get(), SoundSource.HOSTILE, 3F, 0.6F);
        return true;
    }

    /**
     * 合相技（失衡期换到一对相后 {@code combo_delay_ticks} 刻本体调）：pair 是这一对在本体轮换表里的下标——
     * 0 蚀日（日阵 + 毒雾）、1 坠夜（黑暗 + 地震）、2 瘴影（毒雾 + 黑暗）、3 熔裂（地震 + 日阵）。放完把冷却顶到至少一个间隔，免得紧接着再来一招。
     */
    public void combo(ServerLevel server, int pair) {
        ECCalamityHeart.Config.Skills k = s();
        int which = Math.floorMod(pair, 4);
        // 起手：这一对的两种相色在它身上合成一股炸开（客户端画，FX_COMBO）；之后各招的预警、结算照常
        CalamityHeartEntity.Aspect[] two = COMBO_ASPECTS[which];
        double lift = heart.getY() + heart.getBbHeight() * 0.5D - heart.floorTop();
        BossWarnings.resolve(heart, S2CBossWarningPacket.FX_COMBO, heart.position().add(0D, heart.getBbHeight() * 0.5D, 0D), heart.getBbWidth() * 0.5D,
                0D, new Vec3(0D, lift, 0D), 0, two[0].color(), two[1].color());
        switch (which) {
            case 0 -> {
                cross(server, COMBO_LINES);
                fumes(server, KIND_FUME, k.maxFumes(), COMBO_FUMES, k.fumeTicks());
            }
            case 1 -> {
                int warn = Math.max(1, k.blackoutWarnTicks());
                int length = Math.max(1, k.blackoutTicks());
                beginBlackout(warn, length, 0, false);
                add(new Volley(warn + length / 3, k.rubbleCount()));
                add(new Volley(warn + length * 2 / 3, k.rubbleCount()));
            }
            case 2 -> beginBlackout(k.blackoutWarnTicks(), k.blackoutTicks(), COMBO_STRIKES, true);
            default -> {
                rings(k.quakeRings(), k.ringWarnTicks());
                cross(server, COMBO_LINES);
            }
        }
        server.playSound(null, heart.getX(), heart.getY(), heart.getZ(), ECCalamityHeart.SOUND_ECHO.get(), SoundSource.HOSTILE, 3F, 1.2F);
        int interval = (int) Math.round(ECCalamityHeart.Config.values().fight().skillInterval() * heart.cooldownMultiplier());
        cooldown = Math.max(cooldown, interval);
    }

    /** 垂死反扑（崩解里每 {@code collapse_echo_interval} 刻本体调）：n 是这一轮崩解里的第几次（从 0 起），双数断层、单数棱线齐射 */
    public void deathThroe(ServerLevel server, int n) {
        // 每一次反扑先从它身上炸出一圈（客户端画，FX_THROE）
        double lift = heart.getY() + heart.getBbHeight() * 0.5D - heart.floorTop();
        BossWarnings.resolve(heart, S2CBossWarningPacket.FX_THROE, heart.position().add(0D, heart.getBbHeight() * 0.5D, 0D),
                Math.min(12D, ECCalamityHeart.Config.values().fight().arenaRadius()), 0D, new Vec3(0D, lift, 0D), 0,
                CalamityHeartEntity.COLLAPSE_COLOR, 0);
        if ((n & 1) == 0) {
            rings(s().faultRings(), s().ringWarnTicks());
            server.playSound(null, heart.getX(), heart.getY(), heart.getZ(), ECCalamityHeart.SOUND_QUAKE.get(), SoundSource.HOSTILE, 2.5F, 0.5F);
        } else {
            prismsThroughTargets(server, Math.max(0, u().volleyLines()));
        }
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /** 固定值 + 目标最大生命 × 比例；打中了返回 true */
    public static boolean hit(LivingEntity target, DamageSource source, double flat, double percent) {
        float amount = MatrixShadeSkills.damageFor(target, flat, percent);
        if (!(amount > 0F)) {
            return false;
        }
        target.invulnerableTime = 0;
        return target.hurt(source, amount);
    }

    static void ring(ServerLevel server, Vec3 center, double radius, ParticleOptions particle, int points) {
        MatrixShadeSkills.ring(server, center, radius, particle, points);
    }

    // ------------------------------------------------------------------
    // 预警中的招
    // ------------------------------------------------------------------

    private abstract class Effect {
        final Skill skill;
        int ticks;

        Effect(Skill skill, int ticks) {
            this.skill = skill;
            this.ticks = Math.max(1, ticks);
        }

        /** 推进一刻；做完了返回 true */
        boolean tick(ServerLevel server) {
            warn(server);
            if (--ticks > 0) {
                return false;
            }
            resolve(server);
            return true;
        }

        /** 起手时调一次：把预警发给客户端（{@link BossWarnings}）；没有预警画面的招不覆盖 */
        void announce() {
        }

        /** 预警期每刻（多数招的预警画面都交给客户端了，这里留给要逐刻判断的） */
        void warn(ServerLevel server) {
        }

        abstract void resolve(ServerLevel server);
    }

    /** 天光棱线 */
    private final class Prism extends Effect {
        final Vec3 through;
        final Vec3 dir;
        final double radius;

        Prism(Vec3 through, Vec3 dir, double radius, int ticks) {
            super(Skill.PRISM, ticks);
            this.through = through;
            this.dir = dir;
            this.radius = radius;
        }

        @Override
        void announce() {
            BossWarnings.line(heart, through.subtract(dir.scale(radius)), through.add(dir.scale(radius)), PRISM_WIDTH, ticks, BossWarnings.SOLAR);
        }

        /** 落下：沿线立起一道光墙（客户端画，{@code FX_PRISM}） */
        @Override
        void resolve(ServerLevel server) {
            Vec3 center = heart.arenaCenter();
            BossWarnings.resolve(heart, S2CBossWarningPacket.FX_PRISM, through, radius, PRISM_WIDTH, dir.scale(radius), 0, BossWarnings.SOLAR, 0);
            ECCalamityHeart.Config.Skills k = s();
            for (ServerPlayer player : targets(Double.MAX_VALUE)) {
                if (onPrism(through, dir, center, radius, player.position())
                        && hit(player, ECDamageTypes.calamitySolar(heart), k.prismDamage(), k.prismPercent()) && k.prismFireSeconds() > 0) {
                    player.setSecondsOnFire(k.prismFireSeconds());
                }
            }
        }
    }

    /** 烈日标记 */
    private final class Mark extends Effect {
        final UUID target;

        Mark(UUID target, int ticks) {
            super(Skill.MARK, ticks);
            this.target = target;
        }

        /** 被标记的人头顶一根跟着人走的光柱（高 {@value #MARK_COLUMN} 格） */
        @Override
        void announce() {
            if (heart.level() instanceof ServerLevel server) {
                ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), target);
                if (player != null && player.level() == server) {
                    BossWarnings.column(heart, player, player.position(), MARK_COLUMN, 1D, ticks, BossWarnings.SOLAR);
                }
            }
        }

        @Override
        void resolve(ServerLevel server) {
            ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), target);
            if (player == null || !heart.isSkillTarget(player)) {
                return;
            }
            server.playSound(null, player.getX(), player.getY(), player.getZ(), ECCalamityHeart.SOUND_MARK.get(), SoundSource.HOSTILE, 2F, 0.6F);
            boolean exposed = HoannaHazards.exposedToSky(server, player);
            // 光柱砸下来（客户端画）；有遮挡时 size2 = 0，光打在遮挡上散掉
            BossWarnings.resolve(heart, S2CBossWarningPacket.FX_SUNFALL, player.position(), MARK_COLUMN, exposed ? 1D : 0D, Vec3.ZERO, 0,
                    BossWarnings.SOLAR, 0);
            if (exposed) {
                hit(player, ECDamageTypes.calamitySolar(heart), 0D, s().markPercent());
            }
        }
    }

    /** 残响 · 正午 */
    private final class Noon extends Effect {
        Noon(int ticks) {
            super(Skill.NOON, ticks);
        }

        @Override
        void announce() {
            BossWarnings.circle(heart, null, heart.arenaCenter(), Math.min(12D, ECCalamityHeart.Config.values().fight().arenaRadius()), ticks,
                    BossWarnings.SOLAR);
        }

        @Override
        void resolve(ServerLevel server) {
            BossWarnings.resolve(heart, S2CBossWarningPacket.FX_NOON, heart.arenaCenter(), Math.min(12D, ECCalamityHeart.Config.values().fight().arenaRadius()),
                    BossWarnings.SOLAR);
            for (ServerPlayer player : targets(Double.MAX_VALUE)) {
                if (HoannaHazards.exposedToSky(server, player)) {
                    hit(player, ECDamageTypes.calamitySolar(heart), 0D, s().noonPercent());
                }
            }
        }
    }

    /** 腐蚀吐息 */
    private final class Breath extends Effect {
        final Vec3 dir;

        Breath(Vec3 dir, int ticks) {
            super(Skill.BREATH, ticks);
            this.dir = dir;
        }

        /** 扇形预警跟着它的水平位置走（结算按结算那一刻它在哪算） */
        @Override
        void announce() {
            BossWarnings.sector(heart, heart, new Vec3(heart.getX(), heart.floorTop(), heart.getZ()), s().breathRange(), dir, Math.acos(BREATH_COS),
                    ticks, BossWarnings.CORROSION);
        }

        @Override
        void resolve(ServerLevel server) {
            ECCalamityHeart.Config.Skills k = s();
            server.playSound(null, heart.getX(), heart.getY(), heart.getZ(), ECCalamityHeart.SOUND_BREATH.get(), SoundSource.HOSTILE, 2F, 0.7F);
            for (ServerPlayer player : targets(Double.MAX_VALUE)) {
                if (inBreath(player.position(), k.breathRange()) && hit(player, ECDamageTypes.calamity(heart), k.breathDamage(), k.breathPercent())
                        && k.corrosionTicks() > 0) {
                    player.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), k.corrosionTicks(), 0), heart);
                }
            }
        }

        boolean inBreath(Vec3 at, double range) {
            Vec3 to = new Vec3(at.x - heart.getX(), 0D, at.z - heart.getZ());
            double distance = to.length();
            if (distance > range || Math.abs(at.y - heart.floorTop()) > 10D) {
                return false;
            }
            return distance < 1.0E-3D || to.scale(1D / distance).dot(dir) >= BREATH_COS;
        }
    }

    /** 暗袭 */
    private final class Strike extends Effect {
        final UUID target;
        final Vec3 aim;
        final Vec3 from;
        final boolean fume;

        Strike(UUID target, Vec3 aim, Vec3 from, int ticks, boolean fume) {
            super(Skill.BLACKOUT, ticks);
            this.target = target;
            this.aim = aim;
            this.from = from;
            this.fume = fume;
        }

        /** 已经发过轨迹的人（熄光中途才拿起光源的，下次检查时补发剩下的时长） */
        final Set<UUID> told = new HashSet<>();

        @Override
        void announce() {
            warn(null);
        }

        /** 轨迹（空中瞄准线 + 落点圈）只发给拿着光源的参战者（熄光时只有他们看得见）；每 2 刻看一次有没有新拿起光源的人 */
        @Override
        void warn(@Nullable ServerLevel server) {
            if (server != null && ticks % 2 != 0) {
                return;
            }
            List<ServerPlayer> viewers = new ArrayList<>();
            for (ServerPlayer viewer : targets(Double.MAX_VALUE)) {
                if (CalamityHeartEntity.holdsLight(viewer) && told.add(viewer.getUUID())) {
                    viewers.add(viewer);
                }
            }
            if (!viewers.isEmpty()) {
                BossWarnings.tether(heart, from, aim.add(0D, 1D, 0D), ticks, BossWarnings.SHADOW, viewers);
                BossWarnings.circleFor(heart, aim, STRIKE_RADIUS, ticks, BossWarnings.SHADOW, viewers);
            }
        }

        /** 落下：影刃从出手点扑到落点（客户端画，{@code FX_STRIKE}；落点画面谁都看得见，轨迹预警只给拿光源的人） */
        @Override
        void resolve(ServerLevel server) {
            BossWarnings.resolve(heart, S2CBossWarningPacket.FX_STRIKE, aim, STRIKE_RADIUS, 0D, from.subtract(aim), 0, BossWarnings.SHADOW, 0);
            if (fume) {
                fumeAt(server, aim, KIND_FUME, s().maxFumes(), s().fumeTicks());
            }
            ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), target);
            if (player == null || !heart.isSkillTarget(player)) {
                return;
            }
            if (Math.hypot(player.getX() - aim.x, player.getZ() - aim.z) <= STRIKE_RADIUS && Math.abs(player.getY() - aim.y) <= 3D) {
                double damage = s().strikeDamage();
                player.invulnerableTime = 0;
                if (damage > 0D && player.hurt(heart.damageSources().mobAttack(heart), (float) damage)) {
                    server.playSound(null, player.getX(), player.getY(), player.getZ(), ECCalamityHeart.SOUND_STRIKE.get(), SoundSource.HOSTILE, 1.5F, 0.8F);
                }
            }
        }
    }

    /** 一圈震波 */
    private final class Ring extends Effect {
        final Vec3 origin;
        final Set<UUID> struck = new HashSet<>();
        double radius;
        int delay;

        Ring(Vec3 origin, int delay) {
            super(Skill.SHOCKWAVE, 1);
            this.origin = origin;
            this.delay = delay;
        }

        /** 蓄力 delay 刻、再每刻往外扩 {@value #RING_SPEED} 格直到场地外，整条时间轴一次发给客户端 */
        @Override
        void announce() {
            BossWarnings.ring(heart, null, origin, 0D, ECCalamityHeart.Config.values().fight().arenaRadius() + REACH_SLACK, RING_SPEED, RING_BAND,
                    delay, BossWarnings.QUAKE);
        }

        @Override
        boolean tick(ServerLevel server) {
            if (delay > 0) {
                if (--delay == 0) {
                    server.playSound(null, origin.x, origin.y, origin.z, ECCalamityHeart.SOUND_QUAKE.get(), SoundSource.HOSTILE, 2F, 0.6F);
                }
                return false;
            }
            radius += RING_SPEED;
            ECCalamityHeart.Config.Skills k = s();
            for (ServerPlayer player : targets(Double.MAX_VALUE)) {
                if (struck.contains(player.getUUID()) || !player.onGround() || Math.abs(player.getY() - origin.y) > 3D) {
                    continue;
                }
                double distance = Math.hypot(player.getX() - origin.x, player.getZ() - origin.z);
                if (Math.abs(distance - radius) <= RING_BAND) {
                    struck.add(player.getUUID());
                    if (hit(player, ECDamageTypes.calamity(heart), k.quakeDamage(), k.quakePercent())) {
                        player.push(0D, RING_LAUNCH, 0D);
                        player.hurtMarked = true;
                    }
                }
            }
            return radius > ECCalamityHeart.Config.values().fight().arenaRadius() + REACH_SLACK;
        }

        @Override
        void resolve(ServerLevel server) {
        }
    }

    /** 合相技 · 坠夜里延后落下的一批落石 */
    private final class Volley extends Effect {
        final int count;

        Volley(int ticks, int count) {
            super(Skill.VOLLEY, ticks);
            this.count = count;
        }

        @Override
        void resolve(ServerLevel server) {
            rubble(server, s(), count);
        }
    }

    /** 灾柱反噬 */
    private final class Backlash extends Effect {
        final Vec3 base;
        final double radius;

        Backlash(Vec3 base, double radius, int ticks) {
            super(Skill.BACKLASH, ticks);
            this.base = base;
            this.radius = radius;
        }

        /** 灾柱脚下的预警圈，柱顶聚能（柱高取灾柱实体的高度） */
        @Override
        void announce() {
            BossWarnings.pillarCircle(heart, base, radius, ECCalamityHeart.CALAMITY_PILLAR.get().getHeight(), ticks, BossWarnings.DANGER);
        }

        @Override
        void resolve(ServerLevel server) {
            ECCalamityHeart.Config.Fury u = u();
            // 火柱冲天 + 冲击波（客户端画，FX_BACKLASH）
            BossWarnings.resolve(heart, S2CBossWarningPacket.FX_BACKLASH, base, radius, ECCalamityHeart.CALAMITY_PILLAR.get().getHeight(), Vec3.ZERO, 0,
                    BACKLASH_COLOR, 0);
            server.playSound(null, base.x, base.y, base.z, ECCalamityHeart.SOUND_PILLAR_BREAK.get(), SoundSource.HOSTILE, 2.5F, 1.3F);
            for (ServerPlayer player : targets(Double.MAX_VALUE)) {
                double dx = player.getX() - base.x;
                double dz = player.getZ() - base.z;
                double distance = Math.hypot(dx, dz);
                if (distance > radius || Math.abs(player.getY() - base.y) > BACKLASH_HEIGHT) {
                    continue;
                }
                if (hit(player, ECDamageTypes.calamity(heart), u.backlashDamage(), u.backlashPercent())) {
                    Vec3 away = distance < 1.0E-3D ? new Vec3(1D, 0D, 0D) : new Vec3(dx / distance, 0D, dz / distance);
                    player.push(away.x * BACKLASH_PUSH, BACKLASH_LIFT, away.z * BACKLASH_PUSH);
                    player.hurtMarked = true;
                }
            }
        }
    }

    /** 四灾归心：安全圈是起手那刻它脚下（预警期间它停在原地，见 {@link #converging}） */
    private final class Converge extends Effect {
        final Vec3 origin;
        final double radius;
        final int total;

        Converge(Vec3 origin, double radius, int ticks) {
            super(Skill.CONVERGE, ticks);
            this.origin = origin;
            this.radius = radius;
            this.total = Math.max(1, ticks);
        }

        /** 安全圈常亮；圈外是危险区，一道危险红的环从场地边上往里收，收到安全圈那一刻落下 */
        @Override
        void announce() {
            double arena = Math.max(radius, ECCalamityHeart.Config.values().fight().arenaRadius());
            BossWarnings.safeZone(heart, origin, radius, arena, total, CONVERGE_COLOR, BossWarnings.DANGER);
        }

        @Override
        void resolve(ServerLevel server) {
            ECCalamityHeart.Config.Fury u = u();
            server.playSound(null, origin.x, origin.y, origin.z, ECCalamityHeart.SOUND_COLLAPSE.get(), SoundSource.HOSTILE, 3F, 1.2F);
            // 圈外整片炸开、安全圈亮一下（客户端画，FX_CONVERGE）
            double arena = Math.max(radius, ECCalamityHeart.Config.values().fight().arenaRadius());
            BossWarnings.resolve(heart, S2CBossWarningPacket.FX_CONVERGE, origin, radius, arena, Vec3.ZERO, 0, CONVERGE_COLOR, heart.mainAspect().color());
            for (ServerPlayer player : targets(Double.MAX_VALUE)) {
                if (Math.hypot(player.getX() - origin.x, player.getZ() - origin.z) > radius) {
                    hit(player, ECDamageTypes.calamity(heart), u.convergeDamage(), u.convergePercent());
                }
            }
            heart.shake(CalamityHeartEntity.SHAKE_MEDIUM, CalamityHeartEntity.SHAKE_TICKS);
        }
    }

    /** 测试用：这一圈震波现在扩到多大（第 n 个还在的震波） */
    public double ringRadius(int n) {
        int i = 0;
        for (Effect effect : effects) {
            if (effect instanceof Ring ring && i++ == n) {
                return ring.delay > 0 ? -ring.delay : ring.radius;
            }
        }
        return Double.NaN;
    }
}
