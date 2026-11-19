package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * 掉落物（{@link ItemEntity}）的归属读取（batch4 F4-12；policy §4.3 ④「掉落物指定了拾取人、又不是机器主人的，一律不碰」）。
 * 洗牌器、繁殖器、魔力漏斗挑掉落物，磁化法杖挑要拉的东西，都从这里读。
 *
 * <h2>为什么要反射</h2>
 * 1.20.1 的 {@code ItemEntity} 里丢出者 {@code thrower}、指定拾取人 {@code target}（NBT 的 {@code Owner}）、拾取延迟 {@code pickupDelay}
 * 都是 private、没有 getter（{@code getOwner()} 只在丢的人在同一世界里时才找得到实体，拿不到 UUID）。
 * 用 SRG 名反射（{@link ObfuscationReflectionHelper} 在开发环境自动换成官方名；混淆表 {@code mcp_config-1.20.1-obf_to_srg.tsrg} 的 {@code bvh}）：
 * {@code thrower = f_31988_}、{@code target = f_265881_}、{@code pickupDelay = f_31986_}。
 * 混合端的 {@code ItemEntity} 把这三个字段改成了 public（CraftBukkit 要用），名字和类型没变，反射照样找得到。
 * 找不到时（别的模组改了字段）退回读 NBT（{@code addAdditionalSaveData}，每次要建一个 compound，慢一些但结果一样），日志警告一次。
 * <p>
 * 原先这段在磁化法杖里（{@code MagnetizingStaffItem} 第三批），现在法杖改成调这里。
 * <p>
 * 另有 {@link #giveOrDrop}：交东西给可能已经死了的玩家（创世神之杖的掉落和退回的材料、{@code ECProtection} 发事件时拿开的主手物品）；
 * {@link #markForgeReleased} / {@link #forgeReleased}：余烬锻炉潜行放开的标记。
 */
public final class ECItemEntities {

    /** 原版「永远捡不起来」的拾取延迟（{@code ItemEntity.setNeverPickUp}，私有常量 INFINITE_PICKUP_DELAY） */
    public static final int NEVER_PICK_UP_DELAY = 32767;
    /** 余烬锻炉放开标记在 ForgeData 里的键（{@link #markForgeReleased}） */
    private static final String FORGE_RELEASE_KEY = "essentialcraft5_forge_release_until";

    @Nullable
    private static final Field THROWER = field("f_31988_", UUID.class);
    @Nullable
    private static final Field TARGET = field("f_265881_", UUID.class);
    @Nullable
    private static final Field PICKUP_DELAY = field("f_31986_", int.class);

    private ECItemEntities() {
    }

    /** 指定的拾取人（NBT {@code Owner}：/give 背包放不下掉在地上的、玩家死亡时被别的模组指定了拾取人的……）；没有返回 null */
    @Nullable
    public static UUID target(ItemEntity item) {
        return uuid(item, TARGET, "Owner");
    }

    /** 丢出它的人（NBT {@code Thrower}：玩家按 Q 丢的、死亡掉落不算）；没有返回 null */
    @Nullable
    public static UUID thrower(ItemEntity item) {
        return uuid(item, THROWER, "Thrower");
    }

    /**
     * 把东西交给玩家（{@code ItemHandlerHelper.giveItemToPlayer}：背包放不下的掉在脚下）；玩家已经死了、或者已被移除（下线）时，
     * 直接在他所在的位置生成掉落物。死亡掉落已经撒过一遍，这时再塞进背包，重生时（没开 keepInventory）这份背包不会带到新玩家身上，东西就没了；
     * 下线的玩家存档已经写过，塞进去的也会丢。代破坏、代放置的事件监听器可能在中途把玩家弄死，退回材料、交付掉落都走这里。
     */
    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.isAlive() || player.isRemoved() || player.hasDisconnected()) {
            ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
            entity.setDefaultPickUpDelay();
            player.level().addFreshEntity(entity);
            return;
        }
        ItemHandlerHelper.giveItemToPlayer(player, stack);
    }

    /** 当前拾取延迟（刻）；{@link #NEVER_PICK_UP_DELAY} 表示永远捡不起来 */
    public static int pickupDelay(ItemEntity item) {
        if (PICKUP_DELAY != null) {
            try {
                return PICKUP_DELAY.getInt(item);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                // 退回读 NBT
            }
        }
        return nbt(item).getShort("PickupDelay");
    }

    /**
     * 永远捡不起来（{@code setNeverPickUp}：展示用的掉落物、别的模组放的装饰）：漏斗、洗牌器不碰。
     * 和 {@link #pickableBy} 是两回事，机器两个都要查。
     */
    public static boolean neverPickUp(ItemEntity item) {
        return pickupDelay(item) == NEVER_PICK_UP_DELAY;
    }

    /**
     * 按「指定拾取人」这一条，player 能不能拿它：没指定拾取人，或者指定的就是 player。
     * 机器用主人的 UUID 问（没有主人传 null：只有没指定拾取人的才行）。不看丢出者（怪物、方块、玩家自己丢的照拿）、不看拾取延迟（见 {@link #neverPickUp}）。
     */
    public static boolean pickableBy(ItemEntity item, @Nullable UUID player) {
        UUID target = target(item);
        return target == null || target.equals(player);
    }

    /**
     * 余烬锻炉潜行放开时打的标记（实体 ForgeData 里的游戏时间）：到这个时间以前所有锻炉都不托它。
     * 相邻两台锻炉共用边上的格子时，只有被右键的那台记着「刚放开过」，另一台下一轮又会把共用格里的东西托住，玩家捡不回来。
     * 方块被拆时的放开不打标记（共用格子的另一台接着托是对的）。
     */
    public static void markForgeReleased(ItemEntity item, long until) {
        item.getPersistentData().putLong(FORGE_RELEASE_KEY, until);
    }

    /**
     * 这件是不是刚被哪台余烬锻炉潜行放开、标记还没到期（见 {@link #markForgeReleased}）；过期的标记顺手删掉。
     * {@code getPersistentData()} 对没有数据的实体会建一个空的 ForgeData（随实体存盘，无害），调用方应先用别的条件筛过再问。
     */
    public static boolean forgeReleased(ItemEntity item, long now) {
        CompoundTag data = item.getPersistentData();
        if (!data.contains(FORGE_RELEASE_KEY, Tag.TAG_LONG)) {
            return false;
        }
        if (data.getLong(FORGE_RELEASE_KEY) > now) {
            return true;
        }
        data.remove(FORGE_RELEASE_KEY);
        return false;
    }

    @Nullable
    private static UUID uuid(ItemEntity item, @Nullable Field field, String nbtKey) {
        if (field != null) {
            try {
                return (UUID) field.get(item);
            } catch (IllegalAccessException | IllegalArgumentException | ClassCastException e) {
                // 退回读 NBT
            }
        }
        CompoundTag tag = nbt(item);
        return tag.hasUUID(nbtKey) ? tag.getUUID(nbtKey) : null;
    }

    private static CompoundTag nbt(ItemEntity item) {
        CompoundTag tag = new CompoundTag();
        item.addAdditionalSaveData(tag);
        return tag;
    }

    @Nullable
    private static Field field(String srgName, Class<?> type) {
        try {
            Field field = ObfuscationReflectionHelper.findField(ItemEntity.class, srgName);
            if (field.getType() == type) {
                return field;
            }
            EssentialCraft5.LOGGER.warn("ItemEntity 的字段 {} 类型是 {}，不是 {}，改为读 NBT", srgName, field.getType(), type);
            return null;
        } catch (RuntimeException e) {
            EssentialCraft5.LOGGER.warn("找不到 ItemEntity 的字段 {}，改为读 NBT 判断掉落物的归属", srgName, e);
            return null;
        }
    }
}
