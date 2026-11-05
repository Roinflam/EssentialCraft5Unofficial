package com.flameforge.essentialcraft5.lore;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.flameforge.essentialcraft5.registry.ECScrolls;
import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.function.IntPredicate;

/**
 * 源质残卷的卷号、进度和集齐判定（batch12.md §4.1.4；卷数照 §10.1 D1 的定稿：序 + 卷一～卷六共 7 卷）。
 * <p>
 * 卷号就是物品 NBT {@code Volume}：0 = 序，1～6 = 卷一～卷六。主世界的上古遗迹出 0～4（主世界、下界、末地的普通箱子也出 0～4，见 {@link ScrollChestModifier}），海纳尔的城塔和墓穴另出卷五，卷六只在审判庭哨兵身上。
 * 读过哪一卷记在代码发的隐藏进度里（{@code guide/scrolls/preface}、{@code book_1}～{@code book_6}，条件 {@value #CRITERION}）：
 * 进度按玩家存盘、原版自动同步给本人的客户端，手册按它锁条目、打勾，不用另加玩家数据和网络包。
 * <p>
 * 集齐要几卷看海纳尔之门开没开：开着 {@value #COUNT} 卷，关着 {@value #OVERWORLD} 卷（卷五、卷六在海纳尔里拿）。
 * 每次读完一卷、每次登录各判一次（登录这次是给改过开关的老存档补发）；发过就不收回，之后把门打开也不撤。
 * <p>
 * 另记一份「哪几卷已经掉给过这名玩家」（{@link #markGiven}），放在玩家持久数据 {@link Player#PERSISTED_NBT_TAG} 的 {@value #TAG_GIVEN}
 * 里，按位存（第 v 位 = 第 v 卷），死亡重生、从末地回来由 Forge 复制过去。掉落时读过的和掉给过的都算「有了」（{@link #has}）：
 * 原版箱子（{@link ScrollChestModifier}）只出没有的，遗迹 / 海纳尔的表优先出没有的。
 */
public final class ScrollVolumes {

    /** 卷数：序 + 卷一～卷六 */
    public static final int COUNT = 7;
    /** 海纳尔之门关着时集齐要的卷数：序 + 卷一～卷四（都在主世界的上古遗迹里出） */
    public static final int OVERWORLD = 5;
    /** 物品 NBT 里的卷号 */
    public static final String TAG = "Volume";
    /** 进度 JSON 里唯一的条件名（minecraft:impossible，只由代码发） */
    public static final String CRITERION = "awarded_by_code";
    /** 集齐的进度 */
    public static final ResourceLocation COMPLETE = EssentialCraft5.id("guide/scrolls/complete");
    /** PERSISTED_NBT_TAG 里记「掉给过哪几卷」的键（int，按位） */
    public static final String TAG_GIVEN = EssentialCraft5.MODID + ":scrolls_given";

    /** 语言键：卷的短名（「序」「卷一」…），物品名、读完的提示都拼它 */
    private static final String LABEL_KEY = "lore.essentialcraft5.scroll.v%d.label";
    /** 语言键：卷名（提示框第一行） */
    private static final String TITLE_KEY = "lore.essentialcraft5.scroll.v%d.title";
    /** 聊天：集齐（门开着 / 门关着两种说法，卷数不同） */
    public static final String MSG_COMPLETE_ALL = "message.essentialcraft5.ancient_scroll.complete_all";
    public static final String MSG_COMPLETE_OVERWORLD = "message.essentialcraft5.ancient_scroll.complete_overworld";
    /**
     * 同上两句的「不提手册」版：general.ancient_scrolls.enabled 关掉时「古卷」整章藏掉，手里剩下的残卷照样能读、照样能集齐，
     * 这时再说「手册里最后一篇打上了勾」就是假话
     */
    public static final String MSG_COMPLETE_ALL_PLAIN = "message.essentialcraft5.ancient_scroll.complete_all_plain";
    public static final String MSG_COMPLETE_OVERWORLD_PLAIN = "message.essentialcraft5.ancient_scroll.complete_overworld_plain";

    private static final ResourceLocation[] ADVANCEMENTS = new ResourceLocation[COUNT];

    static {
        ADVANCEMENTS[0] = EssentialCraft5.id("guide/scrolls/preface");
        for (int v = 1; v < COUNT; v++) {
            ADVANCEMENTS[v] = EssentialCraft5.id("guide/scrolls/book_" + v);
        }
    }

    private ScrollVolumes() {
    }

    /** 是不是有效的卷号（0～6） */
    public static boolean valid(int volume) {
        return volume >= 0 && volume < COUNT;
    }

    /** 这一卷读过以后发的进度：0 → guide/scrolls/preface，n → guide/scrolls/book_n */
    public static ResourceLocation advancement(int volume) {
        if (!valid(volume)) {
            throw new IllegalArgumentException("没有这一卷：" + volume);
        }
        return ADVANCEMENTS[volume];
    }

    public static String labelKey(int volume) {
        return String.format(LABEL_KEY, volume);
    }

    public static String titleKey(int volume) {
        return String.format(TITLE_KEY, volume);
    }

    /** 卷的短名（「序」「卷三」），物品名和读完的提示用 */
    public static Component label(int volume) {
        return Component.translatable(labelKey(volume));
    }

    /** 集齐要几卷：门开着 7（序 + 卷一～卷六），关着 5（序 + 卷一～卷四） */
    public static int requiredCount(boolean portalEnabled) {
        return portalEnabled ? COUNT : OVERWORLD;
    }

    /** 纯函数：卷 0～requiredCount−1 都读过了就算集齐 */
    public static boolean isSetComplete(IntPredicate read, boolean portalEnabled) {
        int need = requiredCount(portalEnabled);
        for (int v = 0; v < need; v++) {
            if (!read.test(v)) {
                return false;
            }
        }
        return true;
    }

    /** 这名玩家读过这一卷没有（服务端；卷号无效、进度没加载时按没读过算） */
    public static boolean isRead(ServerPlayer player, int volume) {
        return valid(volume) && done(player, ADVANCEMENTS[volume]);
    }

    /** 这一卷掉给过这名玩家没有（箱子开出来、怪物掉出来都算，不管后来读没读、还在不在手里） */
    public static boolean isGiven(ServerPlayer player, int volume) {
        return valid(volume) && (givenBits(player) & (1 << volume)) != 0;
    }

    /** 读过或掉给过：掉落时当作「已经有了」 */
    public static boolean has(ServerPlayer player, int volume) {
        return isRead(player, volume) || isGiven(player, volume);
    }

    /** 记下「这一卷掉给过这名玩家」 */
    public static void markGiven(ServerPlayer player, int volume) {
        if (!valid(volume)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        // getCompound 在没有这个子标签时返回一个新的空标签，没挂在 data 上，改完要 put 回去
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putInt(TAG_GIVEN, persisted.getInt(TAG_GIVEN) | (1 << volume));
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static int givenBits(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(TAG_GIVEN);
    }

    /** 这名玩家拿到集齐的进度没有 */
    public static boolean complete(ServerPlayer player) {
        return done(player, COMPLETE);
    }

    /**
     * 记下「读过这一卷」。
     *
     * @return 这次新记下的为 true；以前读过、卷号无效、进度文件不在（数据包删了它）为 false
     */
    public static boolean award(ServerPlayer player, int volume) {
        if (!valid(volume) || isRead(player, volume)) {
            return false;
        }
        return award(player, ADVANCEMENTS[volume]);
    }

    /**
     * 读完一卷、登录时调：该读的卷都读过、还没拿过集齐的进度就发它，给本人一条聊天消息和一声挑战完成的提示音
     * （{@link ServerPlayer#playNotifySound} 只发给本人）。门开没开按现在的开关算。
     *
     * @return 这次新发了集齐的进度
     */
    public static boolean tryAwardComplete(ServerPlayer player) {
        if (complete(player)) {
            return false;
        }
        boolean portal = ECHoannaPortal.Config.portalEnabled();
        if (!isSetComplete(v -> isRead(player, v), portal) || !award(player, COMPLETE)) {
            return false;
        }
        player.sendSystemMessage(Component.translatable(completeMessageKey(portal, ECScrolls.Config.values().enabled())));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1F, 1F);
        return true;
    }

    /** 集齐的聊天用哪句：门开没开定卷数，手册「古卷」章显示着（general.ancient_scrolls.enabled）才提手册 */
    public static String completeMessageKey(boolean portalEnabled, boolean chapterShown) {
        if (portalEnabled) {
            return chapterShown ? MSG_COMPLETE_ALL : MSG_COMPLETE_ALL_PLAIN;
        }
        return chapterShown ? MSG_COMPLETE_OVERWORLD : MSG_COMPLETE_OVERWORLD_PLAIN;
    }

    /**
     * 集齐给的抗性（{@code general.ancient_scrolls.set_resistance}）：拿到集齐进度的玩家才有，否则 0。
     * 由 {@code api/MRUResistance.vulnerability} 加进「其它饰品」那一项，只对 MRU 腐化和海纳尔辐射两种（那个函数只在服务端结算时调）。
     */
    public static float setResistance(ServerPlayer player) {
        double value = ECScrolls.Config.values().setResistance();
        if (value <= 0D || !complete(player)) {
            return 0F;
        }
        return (float) value;
    }

    private static boolean done(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static boolean award(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().award(advancement, CRITERION);
    }
}
