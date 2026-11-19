package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECGearSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 战痕（tools/plan/gear_growth.md 第 6 节）：装备到了家族最高阶以后，同调度不再白白溢出，接着攒进 NBT 的 {@code scar}，
 * 每 {@code scars.points_per_level}（2000）点一级，最多 {@code scars.max_level}（10）级；封顶以后不拿点、不占预算（{@link AttunementGains} 的 room）。
 * <ul>
 *   <li>武器：每级增伤池 +{@code scars.weapon_per_level}（1%，{@link GearStatSheet#scarDamage}，池来源 {@code battle_scar}）；</li>
 *   <li>盔甲：每级减伤 +{@code scars.armor_per_level}（1%，按全套四件里最低的那件算，G2 接进减伤池）。</li>
 * </ul>
 * 另记两样战绩：击杀过的首领数（{@code bosses}，当场写）、累计打实的伤害（{@code dealt}，先记在内存、跟着 {@link AttunementGains#flush} 写盘）。
 * 升一级时动作栏写「名字 战痕 ✦n」、发 {@link GearFx#SCAR_UP} 事件、放音效。
 * 同调转移、升格不带战痕（{@link Attunement#cleared} / {@link Attunement#promoted}）。
 */
public final class BattleScars {

    /** 主手待写的累计伤害（只认记下时的那个物品对象，同 {@code AttunementGains} 的待写点数） */
    private static final class Dealt {
        final ItemStack stack;
        double amount;

        Dealt(ItemStack stack) {
            this.stack = stack;
        }
    }

    private static final Map<Player, Dealt> DEALT = new WeakHashMap<>();

    private BattleScars() {
    }

    /** 战痕等级：min(最高级, scar / 每级点数) */
    public static int level(ItemStack stack) {
        int max = GearGrowthConfig.scarMaxLevel();
        if (max <= 0) {
            return 0;
        }
        return Math.min(max, Attunement.scar(stack) / GearGrowthConfig.scarPointsPerLevel());
    }

    /** 记一下主手这件打实的伤害（只在服务端；跟着同调写盘一起写进物品） */
    public static void noteDealt(Player player, ItemStack stack, float amount) {
        if (!(amount > 0F) || !Float.isFinite(amount) || stack.isEmpty()) {
            return;
        }
        Dealt dealt = DEALT.get(player);
        if (dealt == null || dealt.stack != stack) {
            dealt = new Dealt(stack);
            DEALT.put(player, dealt);
        }
        dealt.amount += amount;
    }

    /** 把待写的累计伤害写进主手那件（还是记下时那件才写；小数部分留着） */
    static void flushDealt(Player player, ItemStack current) {
        Dealt dealt = DEALT.get(player);
        if (dealt == null) {
            return;
        }
        if (dealt.stack != current || current.isEmpty()) {
            DEALT.remove(player);
            return;
        }
        long whole = (long) dealt.amount;
        if (whole > 0L) {
            Attunement.addDealt(current, whole);
            dealt.amount -= whole;
        }
    }

    /** 还没写进物品的累计伤害（测试看） */
    public static double pendingDealt(Player player) {
        Dealt dealt = DEALT.get(player);
        return dealt != null && dealt.stack == player.getMainHandItem() ? dealt.amount : 0D;
    }

    /** 测试：忘掉这名玩家待写的累计伤害 */
    public static void forget(Player player) {
        DEALT.remove(player);
    }

    /** 战痕升到 level 级：动作栏、事件包、音效 */
    static void onLevelUp(ServerPlayer player, ItemStack stack, int level) {
        player.displayClientMessage(Component.translatable("message.essentialcraft5.gear.scar_up",
                stack.getHoverName(), AttunementLook.scarMark(level)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        S2CGearFxPacket.sendAround(player.serverLevel(), GearFx.SCAR_UP, player, player.position().add(0D, 1D, 0D), level);
        ECGearSounds.play(player.level(), player.position(), ECGearSounds.SCAR_UP, SoundSource.PLAYERS, 0.8F, 1F);
    }
}
