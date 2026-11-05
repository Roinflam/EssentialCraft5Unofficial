package com.flameforge.essentialcraft5.item.armor;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 盔甲加的最大生命（{@link ConfiguredArmorStats}：虚空、宅男、审判庭套装）在重登、从末地回来时不把当前生命夹掉。
 * <p>
 * 原版读档时先读属性、再 {@code setHealth}（按最大生命夹），而换装修饰符是临时的、不进存档，盔甲物品本身也比生命读得晚，
 * 要到第一刻的换装检测才加回来：满套宅男 120 点生命下线，上线只剩 20。从末地回来（{@code restoreFrom} 保留一切）同样先在新玩家对象上
 * {@code setHealth}，那时还没有盔甲修饰符。
 * <p>
 * 做法：下线（{@code PlayerLoggedOutEvent}，原版先发事件再存档，写进去的随这次存档落盘）、从末地回来（{@code PlayerEvent.Clone} 且不是死亡）时，
 * 当前生命超过最大生命的<b>基础值</b>（别的模组、指令可能改过基础值，不写死 20）就记在持久数据根下 {@value #KEY}；
 * 之后第一个 {@code PlayerTickEvent} END（{@code LivingEntity.tick} 里的换装检测已经跑过，最大生命里有盔甲修饰符了）恢复：
 * 存的比现在多才改，按现在的最大生命夹，然后删掉记录。不放在 PlayerPersisted 下面：死亡重生不带过去。
 * <p>
 * 不管的情况：服务端崩溃（没走下线流程，没有记录）；死亡后开着 keepInventory 重生也是从满血起算——那是原版「重生满血」的口径，
 * 新玩家对象构造时按不含盔甲的上限 {@code setHealth(getMaxHealth())}，这里不改。
 * 不只认本模组的盔甲：别的模组给的、同样只在换装时加的最大生命也一起受益。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ArmorHealthRestore {

    /** 玩家持久数据根下的键：下线 / 从末地回来时超出基础上限的生命 */
    public static final String KEY = "essentialcraft5:saved_health";

    private ArmorHealthRestore() {
    }

    /** 记下 player 自己现在的生命（下线时；只在超过最大生命基础值时记） */
    public static void save(Player player) {
        save(player, player);
    }

    /** 把 from 现在的生命记到 to 的持久数据里（从末地回来时 from 是旧玩家对象、to 是新的；只在超过 from 的最大生命基础值时记） */
    public static void save(Player from, Player to) {
        float health = from.getHealth();
        AttributeInstance max = from.getAttribute(Attributes.MAX_HEALTH);
        double base = max != null ? max.getBaseValue() : 20D;
        if (Float.isFinite(health) && health > base) {
            to.getPersistentData().putFloat(KEY, health);
        }
    }

    /** 有记录就恢复（存的比现在多才改，按现在的最大生命夹），然后删掉记录；死了的不恢复 */
    public static void restore(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(KEY, Tag.TAG_FLOAT)) {
            return;
        }
        float saved = root.getFloat(KEY);
        root.remove(KEY);
        if (player.isDeadOrDying() || !(saved > player.getHealth())) {
            return;
        }
        player.setHealth(Math.min(saved, player.getMaxHealth()));
    }

    @SubscribeEvent
    public static void onArmorHealthLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            save(player);
        }
    }

    @SubscribeEvent
    public static void onArmorHealthClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            save(event.getOriginal(), player);
        }
    }

    @SubscribeEvent
    public static void onArmorHealthTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !player.getPersistentData().contains(KEY, Tag.TAG_FLOAT)) {
            return;
        }
        restore(player);
    }
}
