package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 矩阵之影的招式（batch13.md §5.3.2）。每相三招轮着放，两招之间隔 {@code skill_interval} 刻（打满 5 分钟后 ×0.7），蓄招时不出拳、不走动；
 * 只打名单里在线的生存 / 冒险参战者（{@link ECBossMonster#isSkillTarget}）。有预警的大招伤害 = 固定值 + 目标最大生命 × 比例，带 {@code bypasses_armor}
 * 的伤害类型（能躲才穿甲，batch13.md §5.1「能躲的才穿甲」）。
 * <table>
 *   <tr><th>相</th><th>招</th><th>做法</th></tr>
 *   <tr><td rowspan="2">身</td><td>迟滞之握</td><td>蓄 0.5 秒，抓住一名参战者 {@code grip_ticks} 刻：移动速度 −85%；他打实它一下就挣开</td></tr>
 *   <tr><td>坠身</td><td>在目标脚下画 1 秒阴影圈（半径 3），落下来砸圈里的人 {@code slam_damage} + {@code slam_health_percent}（{@code aberrant_strike}）</td></tr>
 *   <tr><td rowspan="3">心</td><td>镜影</td><td>放出最多 {@code max_mirrors} 个镜影，自己和其中一个换位置；真身胸口的矩阵格是亮的，打碎镜影的人被反噬</td></tr>
 *   <tr><td>心智钉</td><td>在场地里立一根（最多 {@code max_spikes}），立着时它挨的伤害 ×{@code spike_damage_taken}；12 点生命，打掉就好</td></tr>
 *   <tr><td>低语</td><td>对一名参战者低语 2 秒（只有他听得见），结束时他还看着它就挨 {@code whisper_damage} + {@code whisper_health_percent}</td></tr>
 *   <tr><td rowspan="3">矩阵</td><td>矩阵电弧</td><td>蓄 1 秒打一名参战者，再跳到他 {@code arc_chain_range} 格内的下一个人（最多 {@code arc_max_targets} 人）：分散站就只挨一下</td></tr>
 *   <tr><td>失序之柱</td><td>在每名参战者脚下放一根（最多 {@code max_pillars}），1.5 秒预警圈后炸开 {@code pillar_damage} + {@code pillar_health_percent}（{@code matrix_rend}）</td></tr>
 *   <tr><td>收束</td><td>2 秒把场地里的参战者往自己身边拉（潜行减半），结束时 5 格内没举盾的挨 {@code converge_damage} + {@code converge_health_percent} 并被弹开</td></tr>
 * </table>
 * 只在服务端用；技能实体不存盘，读档后这份状态是空的（抓着的人在卸载时就放开了，见 {@link #cancelAll}）。
 */
public final class MatrixShadeSkills {

    public static final String KIND_MIRROR = "shade_mirror";
    public static final String KIND_SPIKE = "mind_spike";
    public static final String KIND_PILLAR = "disorder_pillar";

    /** 迟滞之握的移动速度修饰（临时修饰，不存盘） */
    public static final UUID GRIP_MODIFIER_ID = UUID.fromString("5b1e8c07-4d7a-4f2e-b0f3-13c4d0a1f001");
    public static final double GRIP_SLOW = -0.85D;
    /** 坠身的阴影圈半径 */
    public static final double SLAM_RADIUS = 3D;
    /** 收束结束时冲击的半径 */
    public static final double CONVERGE_SHOCK_RADIUS = 5D;
    /** 矩阵电弧画面上每一跳晚几刻（只是画面，伤害同一刻结算） */
    public static final int ARC_HOP_DELAY = 2;
    /** 收束预警里往里收的环从场地边收到圆心要几刻 */
    public static final double CONVERGE_RING_TICKS = 40D;
    /** 收束每刻往它身边拉的速度（潜行减半） */
    public static final double CONVERGE_PULL = 0.09D;
    /** 招式够得着的距离（离它多远以内的参战者会被选中） */
    public static final double SKILL_RANGE = 24D;
    /** 镜影离它的距离 */
    public static final double MIRROR_MIN = 4D;
    public static final double MIRROR_MAX = 7D;
    /** 打碎镜影的反噬 */
    public static final float MIRROR_BACKLASH = 4F;
    /** 低语时视线和它的夹角余弦超过这个算「看着它」 */
    public static final double WHISPER_LOOK = 0.5D;

    /** 招式：所属的相、蓄招刻数 */
    public enum Skill {
        GRIP(MatrixShadeEntity.Phase.BODY, 10, MatrixShadeEntity.ANIM_GRIP),
        SLAM(MatrixShadeEntity.Phase.BODY, 20, MatrixShadeEntity.ANIM_SLAM),
        MIRRORS(MatrixShadeEntity.Phase.MIND, 20, MatrixShadeEntity.ANIM_CAST),
        SPIKE(MatrixShadeEntity.Phase.MIND, 15, MatrixShadeEntity.ANIM_CAST),
        WHISPER(MatrixShadeEntity.Phase.MIND, 40, MatrixShadeEntity.ANIM_WHISPER),
        ARC(MatrixShadeEntity.Phase.MATRIX, 20, MatrixShadeEntity.ANIM_ARC),
        PILLARS(MatrixShadeEntity.Phase.MATRIX, 10, MatrixShadeEntity.ANIM_CAST),
        CONVERGE(MatrixShadeEntity.Phase.MATRIX, 40, MatrixShadeEntity.ANIM_CONVERGE);

        private final MatrixShadeEntity.Phase phase;
        private final int windup;
        private final int anim;

        Skill(MatrixShadeEntity.Phase phase, int windup, int anim) {
            this.phase = phase;
            this.windup = windup;
            this.anim = anim;
        }

        public MatrixShadeEntity.Phase phase() {
            return phase;
        }

        public int windup() {
            return windup;
        }
    }

    private final MatrixShadeEntity shade;
    private final Map<MatrixShadeEntity.Phase, Integer> rotation = new EnumMap<>(MatrixShadeEntity.Phase.class);
    /** 被抓住的人 → 放开的刻（按它的 tickCount） */
    private final Map<UUID, Integer> grips = new HashMap<>();
    @Nullable
    private Skill casting;
    private int castTicks;
    @Nullable
    private UUID castTarget;
    @Nullable
    private Vec3 castPos;
    private int cooldown;

    MatrixShadeSkills(MatrixShadeEntity shade) {
        this.shade = shade;
    }

    // ------------------------------------------------------------------
    // 读数（GameTest 用）
    // ------------------------------------------------------------------

    /** 正在蓄的招（没有时 null） */
    @Nullable
    public Skill current() {
        return casting;
    }

    public boolean casting() {
        return casting != null;
    }

    /** 这一招已经蓄了几刻 */
    public int castTicks() {
        return castTicks;
    }

    @Nullable
    public Vec3 castPos() {
        return castPos;
    }

    public int cooldown() {
        return cooldown;
    }

    public boolean gripping(UUID player) {
        return grips.containsKey(player);
    }

    // ------------------------------------------------------------------
    // 每刻
    // ------------------------------------------------------------------

    /** 每刻（{@link MatrixShadeEntity#serverBossStep}；换相、散去途中不调或什么都不放） */
    void tick(ServerLevel server) {
        tickGrips(server);
        if (casting != null) {
            tickCast(server);
            return;
        }
        if (shade.phaseShifting()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        List<ServerPlayer> targets = targets();
        if (targets.isEmpty()) {
            return;
        }
        Skill next = pick(shade.phase());
        if (next == null) {
            return;
        }
        ServerPlayer target = shade.getTarget() instanceof ServerPlayer current && targets.contains(current)
                ? current : targets.get(shade.getRandom().nextInt(targets.size()));
        start(next, target);
    }

    /** 这一相轮到的下一招（放不了的跳过；都放不了时 null） */
    @Nullable
    private Skill pick(MatrixShadeEntity.Phase phase) {
        List<Skill> pool = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            if (skill.phase == phase) {
                pool.add(skill);
            }
        }
        int start = rotation.getOrDefault(phase, shade.getRandom().nextInt(pool.size()));
        for (int i = 0; i < pool.size(); i++) {
            Skill skill = pool.get((start + i) % pool.size());
            if (available(skill)) {
                rotation.put(phase, (start + i + 1) % pool.size());
                return skill;
            }
        }
        return null;
    }

    private boolean available(Skill skill) {
        ECMatrixShade.Config.Skills s = ECMatrixShade.Config.values().skills();
        return switch (skill) {
            case GRIP -> s.gripTicks() > 0;
            case MIRRORS -> shade.skills().hasRoom(KIND_MIRROR, s.maxMirrors());
            case SPIKE -> shade.skills().hasRoom(KIND_SPIKE, s.maxSpikes());
            case PILLARS -> shade.skills().hasRoom(KIND_PILLAR, s.maxPillars());
            default -> true;
        };
    }

    /** 名单里在线、在招式距离内的参战者 */
    public List<ServerPlayer> targets() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : shade.bossRoster().online()) {
            if (shade.isSkillTarget(player) && player.distanceTo(shade) <= SKILL_RANGE) {
                out.add(player);
            }
        }
        return out;
    }

    /** 开始蓄一招（GameTest 直接调）；正在蓄别的招时先打断 */
    public void start(Skill skill, ServerPlayer target) {
        cancelCast();
        casting = skill;
        castTicks = 0;
        castTarget = target.getUUID();
        castPos = skill == Skill.SLAM ? groundUnder(target) : target.position();
        shade.getNavigation().stop();
        shade.getLookControl().setLookAt(target, 30F, 30F);
        shade.anims.play(shade, skill.anim);
        cooldown = (int) Math.round(ECMatrixShade.Config.values().fight().skillInterval() * shade.cooldownMultiplier());
        if (shade.level() instanceof ServerLevel server) {
            onStart(server, skill, target);
        }
        announce(skill);
    }

    /**
     * 起手时把这一招的预警发给客户端（{@link BossWarnings}，客户端自己画完）：坠身是落点的圈；收束是它身边的冲击圈（跟着它）
     * 加一道从场地边往里收的环（拉人的那 40 刻）。别的招没有地面预警。
     */
    private void announce(Skill skill) {
        switch (skill) {
            case SLAM -> {
                if (castPos != null) {
                    BossWarnings.circle(shade, null, castPos, SLAM_RADIUS, skill.windup, BossWarnings.DANGER);
                }
            }
            case CONVERGE -> {
                double radius = ECMatrixShade.Config.values().fight().arenaRadius();
                Vec3 at = groundUnder(shade);
                BossWarnings.circle(shade, shade, at, CONVERGE_SHOCK_RADIUS, skill.windup, BossWarnings.DANGER);
                BossWarnings.ring(shade, shade, at, radius, 2D, -radius / CONVERGE_RING_TICKS, 0.6D, 0, BossWarnings.MATRIX);
            }
            default -> {
            }
        }
    }

    /** 打断正在蓄的招（换相、散去、被删掉）；已经放出去的技能实体不收。蓄到一半被打断时客户端的预警一起撤掉 */
    public void cancelCast() {
        if (casting != null && castTicks < casting.windup) {
            BossWarnings.cancel(shade);
        }
        casting = null;
        castTicks = 0;
        castTarget = null;
        castPos = null;
    }

    /** 打断正在蓄的招并放开所有被抓住的人 */
    public void cancelAll() {
        cancelCast();
        if (shade.level() instanceof ServerLevel server) {
            for (UUID id : List.copyOf(grips.keySet())) {
                release(server, id);
            }
        }
        grips.clear();
    }

    private void tickCast(ServerLevel server) {
        Skill skill = casting;
        if (skill == null) {
            return;
        }
        castTicks++;
        ServerPlayer target = castTarget == null ? null : ECProtection.onlinePlayer(server.getServer(), castTarget);
        if (target != null && target.level() == server) {
            shade.getLookControl().setLookAt(target, 30F, 30F);
        }
        whileCasting(server, skill, target);
        if (castTicks >= skill.windup) {
            Skill done = skill;
            Vec3 pos = castPos;
            cancelCast();
            resolve(server, done, target, pos);
        }
    }

    // ------------------------------------------------------------------
    // 各招
    // ------------------------------------------------------------------

    private void onStart(ServerLevel server, Skill skill, ServerPlayer target) {
        switch (skill) {
            case WHISPER -> {
                target.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_shade.whisper"), true);
                target.playNotifySound(ECMatrixShade.SOUND_WHISPER.get(), SoundSource.HOSTILE, 1F, 1F);
            }
            case CONVERGE -> {
                for (ServerPlayer player : targets()) {
                    player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_shade.converge"), true);
                }
                server.playSound(null, shade.getX(), shade.getY(), shade.getZ(), ECMatrixShade.SOUND_CONVERGE.get(), SoundSource.HOSTILE, 2F, 1F);
            }
            case ARC -> server.playSound(null, shade.getX(), shade.getY(), shade.getZ(), ECMatrixShade.SOUND_ARC.get(), SoundSource.HOSTILE, 1F, 0.6F);
            default -> {
            }
        }
    }

    private void whileCasting(ServerLevel server, Skill skill, @Nullable ServerPlayer target) {
        switch (skill) {
            case CONVERGE -> pull(server);
            case ARC -> {
                if (castTicks % 3 == 0) {
                    server.sendParticles(ParticleTypes.ELECTRIC_SPARK, shade.getX(), shade.getY() + shade.getBbHeight() * 0.6D, shade.getZ(),
                            6, 0.5D, 0.5D, 0.5D, 0.05D);
                }
            }
            default -> {
            }
        }
    }

    private void resolve(ServerLevel server, Skill skill, @Nullable ServerPlayer target, @Nullable Vec3 pos) {
        switch (skill) {
            case GRIP -> {
                if (target != null && shade.isSkillTarget(target) && target.distanceTo(shade) <= SKILL_RANGE) {
                    grip(server, target);
                }
            }
            case SLAM -> slam(server, pos);
            case MIRRORS -> mirrors(server);
            case SPIKE -> spike(server);
            case WHISPER -> {
                if (target != null && shade.isSkillTarget(target) && looksAt(target)) {
                    hit(target, ECDamageTypes.aberrantStrike(shade), s().whisperDamage(), s().whisperPercent());
                }
            }
            case ARC -> {
                if (target != null) {
                    arc(server, target);
                }
            }
            case PILLARS -> pillars(server);
            case CONVERGE -> converge(server);
        }
    }

    /** 迟滞之握：移动速度 −85%，{@code grip_ticks} 刻或者他打它一下（{@link #breakGrip}）就放开 */
    public void grip(ServerLevel server, ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || grips.containsKey(player.getUUID())) {
            return;
        }
        if (speed.getModifier(GRIP_MODIFIER_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(GRIP_MODIFIER_ID, "Matrix specter grip", GRIP_SLOW, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        grips.put(player.getUUID(), shade.tickCount + Math.max(1, s().gripTicks()));
        player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_shade.grip"), true);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), ECMatrixShade.SOUND_GRIP.get(), SoundSource.HOSTILE, 1F, 1F);
    }

    /** 被抓住的人打实它一下：放开 */
    void breakGrip(ServerPlayer player) {
        if (grips.containsKey(player.getUUID()) && shade.level() instanceof ServerLevel server) {
            release(server, player.getUUID());
        }
    }

    private void tickGrips(ServerLevel server) {
        if (grips.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Integer> entry : List.copyOf(grips.entrySet())) {
            ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), entry.getKey());
            if (player == null || player.level() != server || !player.isAlive() || shade.tickCount >= entry.getValue()) {
                release(server, entry.getKey());
            } else if (shade.tickCount % 5 == 0) {
                server.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.1F, 0.17F), 1F),
                        player.getX(), player.getY() + 0.3D, player.getZ(), 4, 0.3D, 0.2D, 0.3D, 0D);
            }
        }
    }

    private void release(ServerLevel server, UUID id) {
        grips.remove(id);
        ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), id);
        if (player != null) {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(GRIP_MODIFIER_ID);
            }
        }
    }

    /** 坠身：落到阴影圈正中（站得下、脚下有地才落，否则原地砸：目标悬在虚空上时不把自己送到半空），砸圈里的人 */
    private void slam(ServerLevel server, @Nullable Vec3 pos) {
        if (pos == null) {
            return;
        }
        Vec3 center = shade.arenaCenter();
        double radius = ECMatrixShade.Config.values().fight().arenaRadius();
        BlockPos below = BlockPos.containing(pos.x, pos.y - 0.01D, pos.z);
        boolean floor = !server.getBlockState(below).getCollisionShape(server, below).isEmpty();
        if (floor && pos.distanceTo(center) <= radius) {
            Vec3 old = shade.position();
            shade.moveTo(pos.x, pos.y, pos.z, shade.getYRot(), shade.getXRot());
            if (!server.noCollision(shade)) {
                shade.moveTo(old.x, old.y, old.z, shade.getYRot(), shade.getXRot());
            }
        }
        // 落地的冲击由客户端画（FX_SLAM）
        BossWarnings.resolve(shade, S2CBossWarningPacket.FX_SLAM, pos, SLAM_RADIUS, BossWarnings.DANGER);
        server.playSound(null, pos.x, pos.y, pos.z, ECMatrixShade.SOUND_SLAM.get(), SoundSource.HOSTILE, 2F, 0.8F);
        for (ServerPlayer player : targets()) {
            if (horizontal(player.position(), pos) <= SLAM_RADIUS && Math.abs(player.getY() - pos.y) <= 2.5D) {
                hit(player, ECDamageTypes.aberrantStrike(shade), s().slamDamage(), s().slamPercent());
            }
        }
    }

    /** 镜影：放出来以后自己和其中一个换位置 */
    private void mirrors(ServerLevel server) {
        int max = s().maxMirrors();
        List<MatrixShadeMirror> spawned = new ArrayList<>();
        int attempts = 0;
        while (shade.skills().hasRoom(KIND_MIRROR, max) && attempts++ < max * 4) {
            Vec3 at = ringSpot(server, MIRROR_MIN, MIRROR_MAX);
            if (at == null) {
                continue;
            }
            MatrixShadeMirror mirror = ECMatrixShade.SHADE_MIRROR.get().create(server);
            if (mirror == null) {
                break;
            }
            mirror.moveTo(at.x, at.y, at.z, shade.getYRot(), 0F);
            mirror.bind(shade);
            if (!server.noCollision(mirror) || !server.addFreshEntity(mirror) || mirror.isRemoved()) {
                continue;
            }
            shade.skills().track(KIND_MIRROR, mirror);
            spawned.add(mirror);
        }
        if (spawned.isEmpty()) {
            return;
        }
        // 每个镜影现身的地方、它自己原来站的地方都闪一下（客户端画，FX_MIRROR）：换位以后分不清哪个是真身
        for (MatrixShadeMirror mirror : spawned) {
            BossWarnings.resolve(mirror, S2CBossWarningPacket.FX_MIRROR, mirror.position(), mirror.getBbHeight(), BossWarnings.MATRIX);
        }
        BossWarnings.resolve(shade, S2CBossWarningPacket.FX_MIRROR, shade.position(), shade.getBbHeight(), BossWarnings.MATRIX);
        server.playSound(null, shade.getX(), shade.getY(), shade.getZ(), ECMatrixShade.SOUND_MIRROR.get(), SoundSource.HOSTILE, 1.5F, 1F);
        MatrixShadeMirror swap = spawned.get(shade.getRandom().nextInt(spawned.size()));
        Vec3 mine = shade.position();
        Vec3 theirs = swap.position();
        shade.moveTo(theirs.x, theirs.y, theirs.z, shade.getYRot(), shade.getXRot());
        swap.moveTo(mine.x, mine.y, mine.z, swap.getYRot(), swap.getXRot());
        for (ServerPlayer player : targets()) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_shade.mirrors"), true);
        }
    }

    /** 心智钉：立在场地里离它 6~9 格的地方 */
    private void spike(ServerLevel server) {
        if (!shade.skills().hasRoom(KIND_SPIKE, s().maxSpikes())) {
            return;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            Vec3 at = ringSpot(server, 6D, 9D);
            if (at == null) {
                continue;
            }
            MatrixShadeSpike spike = ECMatrixShade.MIND_SPIKE.get().create(server);
            if (spike == null) {
                return;
            }
            spike.moveTo(at.x, at.y + 0.5D, at.z, shade.getRandom().nextFloat() * 360F, 0F);
            spike.bind(shade);
            if (!server.noCollision(spike) || !server.addFreshEntity(spike) || spike.isRemoved()) {
                continue;
            }
            shade.skills().track(KIND_SPIKE, spike);
            // 钉子到它身上的连线（客户端画，FX_LINK，跟着两头走；钉子碎了 / 没了就断）
            BossWarnings.attach(spike, shade, S2CBossWarningPacket.FX_LINK, spike.position().add(0D, spike.getBbHeight() * 0.5D, 0D), 0D,
                    MatrixShadeSpike.LIFE, MatrixShadeSpike.LINK_COLOR);
            server.playSound(null, at.x, at.y, at.z, ECMatrixShade.SOUND_SPIKE.get(), SoundSource.HOSTILE, 1.5F, 1F);
            for (ServerPlayer player : targets()) {
                player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_shade.spike"), true);
            }
            return;
        }
    }

    /** 矩阵电弧：打第一个人，再跳到上一个人 {@code arc_chain_range} 格内最近的、还没挨过的人 */
    public List<ServerPlayer> arc(ServerLevel server, ServerPlayer first) {
        List<ServerPlayer> hit = new ArrayList<>();
        if (!shade.isSkillTarget(first) || first.distanceTo(shade) > SKILL_RANGE) {
            return hit;
        }
        ECMatrixShade.Config.Skills s = s();
        Set<UUID> done = new HashSet<>();
        Vec3 from = shade.position().add(0D, shade.getBbHeight() * 0.6D, 0D);
        ServerPlayer next = first;
        int max = Math.max(1, s.arcMaxTargets());
        while (next != null && hit.size() < max) {
            Vec3 to = next.position().add(0D, next.getBbHeight() * 0.5D, 0D);
            // 每一跳一道电弧（客户端画，FX_ARC）；伤害照旧同一刻结算，画面上一跳晚 2 刻，看得出是从谁跳到谁
            BossWarnings.resolve(shade, S2CBossWarningPacket.FX_ARC, from, 0D, 0D, to.subtract(from), hit.size() * ARC_HOP_DELAY, BossWarnings.MATRIX, 0);
            hit(next, ECDamageTypes.mruDischarge(shade), s.arcDamage(), s.arcPercent());
            hit.add(next);
            done.add(next.getUUID());
            from = to;
            ServerPlayer last = next;
            next = null;
            double best = s.arcChainRange();
            for (ServerPlayer other : targets()) {
                if (done.contains(other.getUUID())) {
                    continue;
                }
                double distance = other.distanceTo(last);
                if (distance <= best) {
                    best = distance;
                    next = other;
                }
            }
        }
        server.playSound(null, shade.getX(), shade.getY(), shade.getZ(), ECMatrixShade.SOUND_ARC.get(), SoundSource.HOSTILE, 1.5F, 1.4F);
        return hit;
    }

    /** 失序之柱：每名参战者脚下一根（总数不超过 {@code max_pillars}） */
    private void pillars(ServerLevel server) {
        int max = s().maxPillars();
        for (ServerPlayer player : targets()) {
            if (!shade.skills().hasRoom(KIND_PILLAR, max)) {
                break;
            }
            Vec3 at = groundUnder(player);
            MatrixShadePillar pillar = ECMatrixShade.DISORDER_PILLAR.get().create(server);
            if (pillar == null) {
                return;
            }
            pillar.moveTo(at.x, at.y, at.z, 0F, 0F);
            pillar.bind(shade);
            if (server.addFreshEntity(pillar) && !pillar.isRemoved()) {
                shade.skills().track(KIND_PILLAR, pillar);
                BossWarnings.circle(pillar, null, at, MatrixShadePillar.RADIUS, MatrixShadePillar.WARN, BossWarnings.MATRIX);
            }
        }
    }

    /** 收束蓄招时每刻：把场地里的参战者往它身边拉（潜行减半） */
    private void pull(ServerLevel server) {
        double radius = ECMatrixShade.Config.values().fight().arenaRadius();
        Vec3 to = shade.position();
        for (ServerPlayer player : targets()) {
            Vec3 delta = to.subtract(player.position());
            double distance = delta.horizontalDistance();
            if (distance < 1.5D || distance > radius) {
                continue;
            }
            double strength = CONVERGE_PULL * (player.isShiftKeyDown() ? 0.5D : 1D);
            Vec3 push = new Vec3(delta.x / distance * strength, 0D, delta.z / distance * strength);
            player.push(push.x, push.y, push.z);
            player.hurtMarked = true;
        }
    }

    /** 收束结束：5 格内没举盾的挨冲击并被弹开 */
    private void converge(ServerLevel server) {
        // 冲击由客户端画（FX_SHOCK）
        BossWarnings.resolve(shade, S2CBossWarningPacket.FX_SHOCK, groundUnder(shade), CONVERGE_SHOCK_RADIUS, BossWarnings.MATRIX);
        for (ServerPlayer player : targets()) {
            if (player.distanceTo(shade) > CONVERGE_SHOCK_RADIUS || player.isBlocking()) {
                continue;
            }
            if (hit(player, ECDamageTypes.aberrantStrike(shade), s().convergeDamage(), s().convergePercent())) {
                Vec3 away = player.position().subtract(shade.position());
                double h = Math.max(0.1D, away.horizontalDistance());
                player.push(away.x / h * 1.2D, 0.4D, away.z / h * 1.2D);
                player.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /** 固定值 + 目标最大生命 × 比例 */
    public static float damageFor(LivingEntity target, double flat, double percent) {
        return (float) Math.max(0D, flat + target.getMaxHealth() * Math.max(0D, percent));
    }

    private boolean hit(LivingEntity target, DamageSource source, double flat, double percent) {
        float amount = damageFor(target, flat, percent);
        return amount > 0F && target.hurt(source, amount);
    }

    private static ECMatrixShade.Config.Skills s() {
        return ECMatrixShade.Config.values().skills();
    }

    /** 他的视线和指向它的方向夹角够小、中间没有方块挡着 */
    public boolean looksAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 chest = shade.position().add(0D, shade.getBbHeight() * 0.6D, 0D);
        Vec3 dir = chest.subtract(eye);
        double length = dir.length();
        if (length < 1.0E-3D) {
            return true;
        }
        if (player.getViewVector(1F).dot(dir.scale(1D / length)) <= WHISPER_LOOK) {
            return false;
        }
        return player.level().clip(new ClipContext(eye, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    /** 他脚下的地面（往下找 6 格内第一个有碰撞的方块顶，找不到就是他脚下） */
    private static Vec3 groundUnder(LivingEntity entity) {
        BlockPos pos = entity.blockPosition();
        for (int dy = 0; dy <= 6; dy++) {
            BlockPos below = pos.below(dy + 1);
            if (!entity.level().getBlockState(below).getCollisionShape(entity.level(), below).isEmpty()) {
                return new Vec3(entity.getX(), below.getY() + 1D, entity.getZ());
            }
        }
        return entity.position();
    }

    /** 离它 min~max 格、在场地里、脚下有地面的一点（找 8 次找不到给 null） */
    @Nullable
    private Vec3 ringSpot(ServerLevel server, double min, double max) {
        Vec3 center = shade.arenaCenter();
        double radius = ECMatrixShade.Config.values().fight().arenaRadius();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = shade.getRandom().nextDouble() * Math.PI * 2D;
            double distance = min + shade.getRandom().nextDouble() * Math.max(0D, max - min);
            double x = shade.getX() + Math.cos(angle) * distance;
            double z = shade.getZ() + Math.sin(angle) * distance;
            if (Math.hypot(x - center.x, z - center.z) > radius) {
                continue;
            }
            BlockPos column = BlockPos.containing(x, shade.getY() + 3D, z);
            for (int dy = 0; dy <= 8; dy++) {
                BlockPos below = column.below(dy + 1);
                if (!server.getBlockState(below).getCollisionShape(server, below).isEmpty()) {
                    BlockPos feet = below.above();
                    if (server.getBlockState(feet).getCollisionShape(server, feet).isEmpty()) {
                        return new Vec3(x, below.getY() + 1D, z);
                    }
                    break;
                }
            }
        }
        return null;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    static void ring(ServerLevel server, Vec3 center, double radius, ParticleOptions particle, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2D * i / points;
            server.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + 0.1D, center.z + Math.sin(angle) * radius,
                    1, 0D, 0D, 0D, 0D);
        }
    }

    /** 打碎镜影的人被反噬：缓慢 3 秒 + 4 点（攻击者是矩阵之影） */
    static void mirrorBacklash(MatrixShadeEntity shade, LivingEntity attacker) {
        attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), shade);
        attacker.hurt(shade.damageSources().mobAttack(shade), MIRROR_BACKLASH);
    }
}
