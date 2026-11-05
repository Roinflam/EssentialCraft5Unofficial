package com.flameforge.essentialcraft5.item.module;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 模块冷却（回溯这类「触发一次、等一阵」的模块用），存在玩家持久数据 PlayerPersisted 下的 {@value #TAG}：{@code {"<模块注册名>": 到期的 gameTime}}。
 * 退出重进不清、死亡复制玩家时 Forge 会带过去（同全息诱饵投影仪、θ 腰带的冷却：放在内存里的话下线再上就能刷新冷却）；
 * 要「死了就清」的模块自己在重生时调 {@link #clear}。gameTime 各维度共用一份，换维度不影响。
 * <p>
 * 只在服务端改；客户端看不到 PlayerPersisted，终端里的冷却由服务端算好随终端包发（{@link ModuleTerminal}）。
 */
public final class ModuleCooldowns {

    /** 玩家持久数据里的键（PlayerPersisted 下） */
    public static final String TAG = "essentialcraft5:module_cooldowns";

    private ModuleCooldowns() {
    }

    /** 开始冷却 ticks 刻（覆盖原来的；≤ 0 等于清掉） */
    public static void start(Player player, ModuleType type, int ticks) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag cooldowns = persisted.getCompound(TAG);
        if (ticks <= 0) {
            cooldowns.remove(type.id().toString());
        } else {
            cooldowns.putLong(type.id().toString(), player.level().getGameTime() + ticks);
        }
        if (cooldowns.isEmpty()) {
            persisted.remove(TAG);
        } else {
            persisted.put(TAG, cooldowns);
        }
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** 还剩几刻（没在冷却为 0） */
    public static int remaining(Player player, ModuleType type) {
        CompoundTag cooldowns = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(TAG);
        String key = type.id().toString();
        if (!cooldowns.contains(key)) {
            return 0;
        }
        long left = cooldowns.getLong(key) - player.level().getGameTime();
        return left <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, left);
    }

    /** 冷却好了 */
    public static boolean ready(Player player, ModuleType type) {
        return remaining(player, type) <= 0;
    }

    /** 清掉这块模块的冷却 */
    public static void clear(Player player, ModuleType type) {
        start(player, type, 0);
    }
}
