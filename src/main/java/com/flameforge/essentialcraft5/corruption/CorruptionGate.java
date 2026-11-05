package com.flameforge.essentialcraft5.corruption;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.menu.PlayerPentacleMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.IntToLongFunction;

/**
 * 「开过五芒星才抽」（{@code general.corruption.draw_requires_cure_tier}）：玩家第一次打开一座成形的灵魂五芒星（0 档就行）以后，
 * 所有类别的腐化效果（身体、心智、矩阵）都可能被抽到；没打开过的玩家身上有再多矩阵损伤也不抽，已有的效果潜伏不发作。
 * <p>
 * 为什么留这一道：原作的机器泄漏在附近没有聚集体时因为空指针什么都不做（只有挨着上古遗迹的基地才会养大聚集体），移植修好以后基地里很早就有
 * 存量过万的聚集体，第四到第八章就开始攒损伤。还没搭过五芒星的新手不该背着效果玩；搭起来、打开过一次，就知道有这回事了，
 * 往后效果要不要怕、怎么治，是他的事（矩阵类要 2 档、最贵的三种要 3 档才治得了，这正是推着人去搭高档五芒星的压力）。
 * 门槛靠 {@code draw_threshold_multiplier} 拉高：损伤攒不够门槛，什么都不抽。
 * <p>
 * 另外留一条：按现在的柱子容量，哪一档攒满也付不起治它的成本的效果（{@link #cureTier} 是 {@link #NEVER}，柱子容量配得太小才会）不抽，
 * 免得抽到一个谁也治不了的东西。
 * <p>
 * 打开过的最高档记在玩家数据 {@link IPlayerData#getPentacleTier()}（−1 = 没打开过），打开成形五芒星的界面时由 {@link #recordPentacle} 写，只升不降；
 * 第一次记（−1 → 0 档起）时发一句话说明从此什么都可能抽到，并把抽取冷却拉满一次（刚打开、损伤已经攒了很多时，不会马上抽中）。
 * <p>
 * 什么时候记（{@link PentacleOpenEvents}）：界面<b>真的打开以后</b>（Forge 的 {@code PlayerContainerEvent.Open}）才记，
 * 不在 {@code createMenu} 里记——混合端先调 {@code createMenu}、再发插件的打开容器事件，插件拦下打开时界面没开、档位却已经记下了。
 * 只记自己的、或者自己能动的五芒星（{@code PlayerPentacleBlockEntity.onMenuOpened}，{@code respect_claims}）：
 * 档位只升不降、玩家自己降不回去，被人骗去打开一座别人的高档五芒星、之后又不让用了，就会提前抽到自己还治不了的效果。
 */
public final class CorruptionGate {

    /**
     * 治疗能力算到第几档：3 档。13e 的 4 档五芒星治疗能力同 3 档（batch13.md §7.5 末行），不跟着 {@link ECPentacle#MAX_TIER} 变——
     * 跟着变的话，柱子容量配得小、3 档攒满也付不起的效果，会因为多了 4 档（24 根柱子）变成「4 档治得了」而开始抽。
     */
    public static final int CURE_MAX_TIER = 3;

    /** 哪一档都治不了（柱子容量配得太小）：永远不抽、已有的永远潜伏。写死 4（= {@link #CURE_MAX_TIER} + 1），不随最高档变 */
    public static final int NEVER = CURE_MAX_TIER + 1;

    /** 第一次打开成形五芒星时的那句话：message.essentialcraft5.corruption.unlocked.first（矩阵类不抽时是 first_no_matrix） */
    public static final String UNLOCKED_KEY = "message." + EssentialCraft5.MODID + ".corruption.unlocked.";

    private CorruptionGate() {
    }

    /**
     * 治这个效果至少要几档五芒星：类别要求的档位起，第一个柱子攒满时付得起成本的档位，只算到 {@link #CURE_MAX_TIER}；
     * 都付不起返回 {@link #NEVER}（界面也拿它写「要几档才治得了」）
     */
    public static int cureTier(CorruptionEffects.Effect effect) {
        return cureTier(effect.category().minPentacleTier(), effect.cost(), PlayerPentacleBlockEntity::maxEffectiveESPE);
    }

    /** 同上的纯函数（GameTest 用来换柱子容量）：minTier 档起到 {@link #CURE_MAX_TIER}，第一个 capacity(档) ≥ cost 的档；都不够返回 {@link #NEVER} */
    public static int cureTier(int minTier, long cost, IntToLongFunction capacity) {
        for (int tier = Math.max(0, minTier); tier <= CURE_MAX_TIER; tier++) {
            if (capacity.applyAsLong(tier) >= cost) {
                return tier;
            }
        }
        return NEVER;
    }

    /**
     * 这名玩家现在能不能抽到它（闸关着时一律能）：打开过成形的五芒星，并且按现在的柱子容量总有一档治得了它。
     * 门槛、开关、身上有没有由抽取那边看
     */
    public static boolean drawable(CorruptionEffects.Effect effect, IPlayerData data) {
        return !CorruptionEffects.drawRequiresCureTier() || data.getPentacleTier() >= 0 && cureTier(effect) < NEVER;
    }

    /** 身上的这个效果会不会发作：闸开着时，没打开过五芒星的、哪一档都治不了的潜伏（和 {@link #drawable} 同一个判断） */
    public static boolean active(CorruptionEffects.Effect effect, IPlayerData data) {
        return drawable(effect, data);
    }

    /**
     * 玩家打开了一座 tier 档的成形五芒星（tier ≥ 0）：比记下的高就记下；是第一次打开（之前没记过）时再发一句话、把抽取冷却拉满一次。
     *
     * @return 档位升了没有
     */
    public static boolean recordPentacle(ServerPlayer player, int tier) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || tier < 0) {
            return false;
        }
        int clamped = Math.min(tier, ECPentacle.MAX_TIER);
        int before = data.getPentacleTier();
        if (clamped <= before) {
            return false;
        }
        data.setPentacleTier(clamped);
        if (before < 0 && CorruptionEffects.effectsEnabled() && CorruptionEffects.drawRequiresCureTier()) {
            data.setCorruptionCooldown(Math.max(data.getCorruptionCooldown(), CorruptionTicker.cooldownSlots()));
            player.sendSystemMessage(Component.translatable(unlockedKey()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return true;
    }

    /**
     * 第一次打开成形五芒星时说的那句话的键。矩阵类在这个世界里不抽（{@link CorruptionEffects#matrixEffectsEnabled}：配置关了，或者海纳尔之门关着）时，
     * 换成只讲身体类、心智类的那句（{@code unlocked.first_no_matrix}）。
     */
    public static String unlockedKey() {
        return UNLOCKED_KEY + (CorruptionEffects.matrixEffectsEnabled() ? "first" : "first_no_matrix");
    }

    /** 五芒星的界面真的打开以后才打勾、记档位（见类注释「什么时候记」） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class PentacleOpenEvents {

        private PentacleOpenEvents() {
        }

        @SubscribeEvent
        public static void onPentacleMenuOpen(PlayerContainerEvent.Open event) {
            if (event.getEntity() instanceof ServerPlayer player && event.getContainer() instanceof PlayerPentacleMenu menu) {
                PlayerPentacleBlockEntity pentacle = menu.pentacle();
                if (pentacle != null && !pentacle.isRemoved()) {
                    pentacle.onMenuOpened(player);
                }
            }
        }
    }
}
