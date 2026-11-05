package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECGuide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 第一次进服送一本研究手册。手册是流程教程，新玩家拿到就能照着走。
 * <p>
 * <ul>
 *   <li>时机：服务端的玩家登录事件（单人游戏同样走这里），FakePlayer 不送。</li>
 *   <li>「送过了」记在玩家持久数据 {@code getPersistentData()} 的 {@link Player#PERSISTED_NBT_TAG} 子标签里：
 *       Forge 在 ServerPlayer.restoreFrom 里把这个子标签复制给新的玩家对象，死亡重生、从末地回来都不丢；
 *       换维度（除了末地返回）本来就是同一个玩家对象。所以每个玩家只送一次，丢了、用掉了可以照配方再合成。</li>
 *   <li>用 {@link ItemHandlerHelper#giveItemToPlayer}：背包满了掉在脚下。不用 Inventory#add（创造模式塞不下时返回 true 却把物品销毁）。</li>
 *   <li><b>先给书、后写标记</b>，而且<b>死着不送</b>：玩家在死亡界面登录时（上次死了没点重生就退出了），这时的玩家对象马上要被
 *       重生换掉，书进的是旧对象的背包，跟着一起丢了，标记却已经写了 —— 这个人永远拿不到书。现在死着就跳过、不写标记，
 *       点重生后（{@code PlayerRespawnEvent}，新的玩家对象）再送一次（2026-09-28 修）；
 *       背包满掉脚下仍然照旧，那是 EC5 有意选的。</li>
 *   <li>服务端配置 {@code general.give_research_book_on_first_join}（默认开）。关着时不送也不记，之后再打开，还没收到过的玩家下次登录会收到。
 *       本功能上线前就玩过的老玩家，第一次用上这个版本登录时同样会收到一本。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ResearchBookGift {

    /** PERSISTED_NBT_TAG 里记「已经送过」的键 */
    public static final String TAG_GIVEN = EssentialCraft5.MODID + ":research_book_given";

    /** 服务端配置（general 小节），由 ECGuide.init 登记 */
    public static final class Config {
        public static ForgeConfigSpec.BooleanValue giveOnFirstJoin;

        public static void define(ForgeConfigSpec.Builder builder) {
            giveOnFirstJoin = builder.comment("Give every player a Research Book (a step-by-step tutorial) the first time they join.",
                            "玩家第一次进服时送一本研究手册（流程教程），每人只送一次")
                    .define("give_research_book_on_first_join", true);
        }
    }

    private ResearchBookGift() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            giveIfFirstTime(player);
        }
    }

    /** 死亡界面登录时跳过了，重生后补发（重生后的是新的玩家对象，持久数据已由 Forge 复制过来） */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            giveIfFirstTime(player);
        }
    }

    /**
     * 没送过就送一本并记下。死着（死亡界面登录）时不送也不记，等重生。
     *
     * @return 这次是否送了
     */
    public static boolean giveIfFirstTime(ServerPlayer player) {
        if (!ECConfig.get(Config.giveOnFirstJoin)) {
            return false;
        }
        if (player.isDeadOrDying()) {
            return false;
        }
        CompoundTag data = player.getPersistentData();
        // getCompound 在没有这个子标签时返回一个新的空标签，没挂在 data 上，改完要 put 回去
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(TAG_GIVEN)) {
            return false;
        }
        // 先给书、后写标记（见类注释）
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ECGuide.RESEARCH_BOOK.get()));
        persisted.putBoolean(TAG_GIVEN, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        return true;
    }
}
