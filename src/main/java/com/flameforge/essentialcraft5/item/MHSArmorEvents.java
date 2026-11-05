package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.armor.MRUShieldAbsorbedEvent;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECGear;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 魔力防护套装挨打以后的事（第十三批 13b，batch13.md §4.4；物品本身见 {@link MHSArmorItem}）。只在服务端。
 *
 * <h2>余流（四件都到 1 阶，D35 A）</h2>
 * 矩阵缓冲挡下一下（{@link MRUShieldAbsorbedEvent}：护盾扣过电才发，混合端等插件放行、伤害真的生效以后才发）后给 {@code afterflow_ticks}（40）刻急迫 I，
 * 两次之间至少隔 {@code afterflow_cooldown}（200）刻。魔力防护四件合计最多挡 45%，永远挡不下整下，所以「挡下」按挡下了一部分算（护盾吃掉的 &gt; 0）。
 * 看全套最低的那一件（{@link Attunement#fullSetStage}），混穿别的套装时不给。
 * 攻击者是别的玩家时时长 × {@code general.attunement.pvp_effect_scale}（全批「对玩家减半」都读它）；乘出来不到 1 刻（或配置填 0）就不给，也不记冷却，
 * 接下来怪物打来的那一下照样给。冷却按服务器刻（各世界的 gameTime 不一样，换维度会错）记在内存里，按玩家对象弱引用，重登清零：只有 10 秒，不进存档。
 * 已经有更强或更长的急迫时原版 {@code addEffect} 自己取舍。
 *
 * <h2>脱下夹回生命</h2>
 * 每件 +2 最大生命（{@code general.mhs_armor.max_health}）加同调每阶的那份，脱下以后下一刻夹回上限：和风之护甲、审判庭、宅男套装一起由
 * {@link com.flameforge.essentialcraft5.item.armor.ArmorTakeoffClamp} 管（换成同种低阶的一件也算脱下）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MHSArmorEvents {

    /** 余流的急迫等级（急迫 I） */
    public static final int AFTERFLOW_AMPLIFIER = 0;

    /** 上次给余流的服务器刻（按玩家对象弱引用；只在服务端线程读写） */
    private static final Map<Player, Long> AFTERFLOW_AT = new WeakHashMap<>();

    private MHSArmorEvents() {
    }

    // ------------------------------------------------------------------
    // 余流
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onMHSShieldAbsorbed(MRUShieldAbsorbedEvent event) {
        tryAfterflow(event.getPlayer(), event.getSource().getEntity(), event.getAbsorbed());
    }

    /**
     * 护盾刚挡下 absorbed 点：全套 1 阶、冷却走完就给急迫。
     *
     * @param attacker 这一下记在谁头上（别的玩家时时长按 pvp_effect_scale 缩）
     * @return 给了
     */
    public static boolean tryAfterflow(Player player, @Nullable Entity attacker, float absorbed) {
        if (player.level().isClientSide || !(absorbed > 0F) || Attunement.fullSetStage(player, ECAttunement.MHS_ARMOR) < MHSArmorItem.AFTERFLOW_STAGE) {
            return false;
        }
        ECGear.MHSConfig.Values values = ECGear.MHSConfig.values();
        int ticks = values.afterflowTicks();
        if (attacker instanceof Player && attacker != player) {
            ticks = (int) Math.round(ticks * AttunementConfig.pvpEffectScale());
        }
        if (ticks <= 0) {
            return false;
        }
        long now = now(player);
        Long last = AFTERFLOW_AT.get(player);
        if (last != null && now - last < values.afterflowCooldown()) {
            return false;
        }
        AFTERFLOW_AT.put(player, now);
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ticks, AFTERFLOW_AMPLIFIER, false, true, true));
        return true;
    }

    private static long now(Player player) {
        MinecraftServer server = player.level().getServer();
        return server != null ? server.getTickCount() : player.level().getGameTime();
    }

    // ------------------------------------------------------------------
    // 收尾、测试
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onMHSArmorServerStopped(ServerStoppedEvent event) {
        AFTERFLOW_AT.clear();
    }

    /** GameTest 用：把上次给余流挪到 ticksAgo 刻以前（null = 从没给过） */
    public static void setAfterflowForTest(Player player, @Nullable Integer ticksAgo) {
        if (ticksAgo == null) {
            AFTERFLOW_AT.remove(player);
        } else {
            AFTERFLOW_AT.put(player, now(player) - ticksAgo);
        }
    }
}
