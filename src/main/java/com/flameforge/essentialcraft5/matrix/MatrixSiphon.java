package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.item.MRUMoverItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 矩阵虹吸（第十三批 13a，四阶，batch13.md §3.1.4，限制见 policy §4.16）：潜行、主手拿着绑定自己的灵魂石，对着 {@value #REACH} 格内的聚集体
 * 按住右键 {@value #USE_TICKS} 刻，从它身上抽最多 {@code siphon.amount}（2 000）MRU 进矩阵容量；魔力相性把上限再放大
 * （{@link MatrixResonance#siphonMultiplier}，I / II / III 为 ×1.25 / ×1.5 / ×2）。抽多少还受容量剩下的空间、聚集体自己的存量限制。
 * <ul>
 *   <li><b>代价</b>：本人矩阵损伤 +{@code siphon.matrix_damage}（100）× MRU 腐化易伤（{@link MRUResistance#vulnerability}，魔力防护等能减），
 *       走本批新来源的入口（{@code CorruptionEffects.addMatrixDamage(…, Source.BATCH13)}，吃耐受、稳固和损伤上限）。</li>
 *   <li><b>冷却</b> {@code siphon.cooldown_ticks}（200 刻）：用原版物品冷却挂在灵魂石上（物品栏里的灵魂石有冷却遮罩，期间右键灵魂石什么都不做；
 *       不存盘，下线重进就没了，10 秒的东西不值得存）。只有抽成了才冷却。</li>
 *   <li><b>权限</b>同收容瓶：聚集体所在那一格要是你能动的（{@link MRUMoverItem#mayMove}，只有真玩家能用）。在别人领地、仓里那格不能动的抽不了；
 *       收在瓶子里的不是世界里的实体，本来就对不上；被富集仓稳定器压着的（{@link MRUPresenceEntity#isStabilized}）不抽。</li>
 *   <li>找目标同移动魔杖、收容瓶（{@link MRUMoverItem#findTarget}，视线前方逐段找），再限 {@value #REACH} 格。</li>
 * </ul>
 * 只抽 MRU，不改聚集体的平衡值；抽空了的聚集体照原规则在它下一轮结算时消失（「归零后保留」的遗迹聚集体不消失）。
 */
public final class MatrixSiphon {

    /** 几阶起能虹吸 */
    public static final int RANK = 4;
    /** 最远多少格 */
    public static final double REACH = 5D;
    /** 按住多少刻 */
    public static final int USE_TICKS = 20;

    /** 一次虹吸的结果（测试看；除 {@link #OK} 外都没抽、没扣、没冷却） */
    public enum Result {
        OK, RANK, COOLDOWN, NO_TARGET, STABILIZED, DENIED, POOL_FULL, EMPTY, NO_DATA
    }

    private MatrixSiphon() {
    }

    /** 这名玩家一次最多抽多少（配置 × 魔力相性） */
    public static int cap(Player player) {
        double cap = MatrixResonanceConfig.siphonAmount() * MatrixResonance.siphonMultiplier(player);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, Math.round(cap)));
    }

    /** 视线前方 {@value #REACH} 格内的聚集体（同收容瓶的找法），没有返回 null */
    @Nullable
    public static MRUPresenceEntity target(Level level, Player player) {
        MRUPresenceEntity found = MRUMoverItem.findTarget(level, player);
        return found != null && found.isAlive() && found.distanceTo(player) <= REACH ? found : null;
    }

    /** 能不能开始按住（两端都调，结论要一致：只看阶和面前有没有聚集体；权限、稳定、冷却等到抽的那一刻在服务端判） */
    public static boolean canStart(Level level, Player player) {
        return MatrixRank.atLeast(player, RANK) && target(level, player) != null;
    }

    /**
     * 抽一次（服务端；按住用完时由灵魂石调，GameTest 直接调）。成功时动作栏写抽到多少、容量多少，不成时写原因。
     *
     * @param stoneItem 灵魂石（冷却挂在它上面）
     */
    public static Result siphon(Player player, Item stoneItem) {
        Level level = player.level();
        Result result = attempt(player, stoneItem);
        if (result != Result.OK && result != Result.NO_DATA && !level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_siphon." + result.name().toLowerCase(Locale.ROOT),
                    MatrixRank.rankName(RANK), (int) REACH).withStyle(ChatFormatting.GRAY), true);
        }
        return result;
    }

    private static Result attempt(Player player, Item stoneItem) {
        Level level = player.level();
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || level.isClientSide) {
            return Result.NO_DATA;
        }
        if (!MatrixRank.atLeast(player, RANK)) {
            return Result.RANK;
        }
        if (player.getCooldowns().isOnCooldown(stoneItem)) {
            return Result.COOLDOWN;
        }
        MRUPresenceEntity presence = target(level, player);
        if (presence == null) {
            return Result.NO_TARGET;
        }
        if (presence.isStabilized()) {
            return Result.STABILIZED;
        }
        Vec3 at = presence.position();
        if (!MRUMoverItem.mayMove(level, player, at, at)) {
            return Result.DENIED;
        }
        int pool = data.getMatrixPool();
        int room = MatrixPool.capacity(data) - pool;
        if (room <= 0) {
            return Result.POOL_FULL;
        }
        int want = Math.min(cap(player), room);
        int drawn = want <= 0 ? 0 : presence.getStorage().extractMRU(Math.min(want, presence.getMRU()), true);
        if (drawn <= 0) {
            return Result.EMPTY;
        }
        data.setMatrixPool(pool + drawn, true);
        long damage = Math.round(MatrixResonanceConfig.siphonMatrixDamage() * MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION));
        if (damage > 0L) {
            CorruptionEffects.addMatrixDamage(data, damage, CorruptionEffects.Source.BATCH13);
        }
        int cooldown = MatrixResonanceConfig.siphonCooldownTicks();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(stoneItem, cooldown);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.6F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 16, 0.3D, 0.3D, 0.3D, 0.02D);
        }
        // 数字直接传整数：这句在服务端拼，专用服的语言是英文，按语言分组的话中文玩家会看到「2,000」
        player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_siphon.done", drawn,
                        data.getMatrixPool(), MatrixPool.capacity(data))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return Result.OK;
    }
}
