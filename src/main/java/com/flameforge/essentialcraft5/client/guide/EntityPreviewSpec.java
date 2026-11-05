package com.flameforge.essentialcraft5.client.guide;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 手册 entity 块的两个可选字段（第十三批 13c-Fb）：{@code nbt}（SNBT 字符串，预览实体建好后合并进去，用来摆精英变体、颜色、体型）
 * 和 {@code scale}（预览缩放，大首领填 0.5 之类，框不变、模型缩小）。写法见 {@code tools/guide/FORMAT.md} 的 entity 一行。
 * <p>
 * 纯解析、夹范围，不碰任何客户端类（GameTest 在专用服上直接测）；加载器 {@link GuideContentLoader} 解析时调，
 * {@link GuideMedia} 建实体时调 {@link #merge}（GameTest 查「框下名字对不对得上」时也走它，和手册里建出来的是同一只）。
 */
public final class EntityPreviewSpec {

    public static final float MIN_SCALE = 0.25F;
    public static final float MAX_SCALE = 2F;
    /**
     * 合并前拿掉的键：位置、速度、朝向、UUID 这些会把预览实体挪走或撞上已有实体；乘客、坐骑预览里不建。
     * 其余照写（精英标记、颜色、生命、装备都能摆）。
     */
    static final Set<String> BLOCKED_KEYS = Set.of("Pos", "Motion", "Rotation", "UUID", "Passengers", "Leash", "Dimension");

    private EntityPreviewSpec() {
    }

    /** 缩放夹到 [{@value #MIN_SCALE}, {@value #MAX_SCALE}]；不是有限数按 1 */
    public static float clampScale(double scale) {
        if (!Double.isFinite(scale)) {
            return 1F;
        }
        return (float) Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    /** SNBT 解析成复合标签；空串、写错返回 null（调用方记一条警告，预览照常建、不合并） */
    public static @Nullable CompoundTag parseNbt(@Nullable String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return null;
        }
        try {
            return TagParser.parseTag(snbt);
        } catch (CommandSyntaxException | RuntimeException e) {
            return null;
        }
    }

    /** 能合并进预览实体的那部分（拿掉 {@link #BLOCKED_KEYS}，返回副本） */
    public static CompoundTag sanitized(CompoundTag nbt) {
        CompoundTag copy = nbt.copy();
        for (String key : BLOCKED_KEYS) {
            copy.remove(key);
        }
        return copy;
    }

    /** 先存一份完整数据、把 nbt（拿掉 {@link #BLOCKED_KEYS}）并进去再读回来：只写一部分键时别的字段保持原样 */
    public static void merge(Entity entity, CompoundTag nbt) {
        CompoundTag merged = entity.saveWithoutId(new CompoundTag());
        merged.merge(sanitized(nbt));
        entity.load(merged);
    }

    /** 预览缓存的键：同一个实体类型、不同 nbt 是不同的预览 */
    public static String cacheKey(String id, @Nullable String snbt) {
        return snbt == null || snbt.isBlank() ? id : id + "|" + snbt;
    }
}
