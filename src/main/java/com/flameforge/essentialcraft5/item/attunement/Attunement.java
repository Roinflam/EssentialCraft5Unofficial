package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 装备的同调数据（batch13.md §3.3.5）：物品 NBT 根上的 {@value #TAG}。
 * <pre>
 * essentialcraft5:attunement: {
 *   stage: 2b      同调阶（0 ~ 家族最高阶）
 *   points: 650    本阶已攒的同调度，攒满本阶门槛就停（等升阶）
 *   total: 1750    累计攒过的同调度（同调转移按它的一半算）
 *   rank: 5b       阶戳：穿戴者攒点时写进去的矩阵阶，只升不降；升阶配方只读它（魔力工作台查配方时没有玩家）
 *   v: 1b
 * }
 * </pre>
 * 没有这个键 = 0 阶 0 点（老存档、刚做出来的装备）。各家族的阶效果读 {@link #stage}，全套效果读 {@link #fullSetStage}。
 * <p>
 * 这里只改物品本身，不管谁拿着：在线攒点经 {@link AttunementGains} 节流写盘，升阶在魔力工作台（{@code recipe/AttunementAscensionRecipe}），
 * 指令改动走 {@code command/AttunementCommands}。阶变化时发 {@link AttunementStageEvent}（工作台升阶时没有玩家）。
 */
public final class Attunement {

    /** 物品 NBT 根上的键 */
    public static final String TAG = EssentialCraft5.MODID + ":attunement";
    public static final String KEY_STAGE = "stage";
    public static final String KEY_POINTS = "points";
    public static final String KEY_TOTAL = "total";
    public static final String KEY_RANK = "rank";
    public static final String KEY_VERSION = "v";
    public static final int VERSION = 1;

    /** 任何家族的阶都不超过这个数 */
    public static final int MAX_STAGE = 5;
    /** 几阶起物品带附魔光泽 */
    public static final int GLINT_STAGE = 3;

    private Attunement() {
    }

    @Nullable
    private static CompoundTag data(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(TAG, Tag.TAG_COMPOUND) ? root.getCompound(TAG) : null;
    }

    private static CompoundTag write(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.putByte(KEY_VERSION, (byte) VERSION);
        return tag;
    }

    /** 同调阶（0 起；没有同调键是 0） */
    public static int stage(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag == null ? 0 : Math.max(0, Math.min(MAX_STAGE, tag.getByte(KEY_STAGE)));
    }

    /** 本阶已攒的同调度 */
    public static int points(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag == null ? 0 : Math.max(0, tag.getInt(KEY_POINTS));
    }

    /** 累计攒过的同调度 */
    public static int total(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag == null ? 0 : Math.max(0, tag.getInt(KEY_TOTAL));
    }

    /** 阶戳：穿戴者攒点时的矩阵阶（只升不降） */
    public static int rankStamp(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag == null ? 0 : Math.max(0, tag.getByte(KEY_RANK));
    }

    /** 物品上有没有同调键 */
    public static boolean hasData(ItemStack stack) {
        return data(stack) != null;
    }

    /** 家族最高阶；不在任何家族里的物品是 0 */
    public static int maxStage(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        return family == null ? 0 : family.maxStage();
    }

    /** 本阶要攒满多少同调度；已经是家族最高阶（或不是同调装备）时为 0 */
    public static int need(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        return family == null ? 0 : need(family, stage(stack));
    }

    public static int need(AttunementFamily family, int stage) {
        return stage >= family.maxStage() ? 0 : AttunementConfig.threshold(stage);
    }

    /** 本阶攒满了、等着升阶（最高阶的不算） */
    public static boolean isFull(ItemStack stack) {
        int need = need(stack);
        return need > 0 && points(stack) >= need;
    }

    /** 已经是家族最高阶 */
    public static boolean isMaxed(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        return family != null && stage(stack) >= family.maxStage();
    }

    /** 几阶起带附魔光泽（物品类 isFoil 调它） */
    public static boolean hasGlint(ItemStack stack) {
        return stage(stack) >= GLINT_STAGE;
    }

    /**
     * 往本阶加同调度：加到本阶门槛为止，累计跟着加。返回实际加上的点数（满了、最高阶、不是同调装备、n ≤ 0 时为 0，不动 NBT）。
     */
    public static int addPoints(ItemStack stack, int n) {
        int need = need(stack);
        if (n <= 0 || need <= 0) {
            return 0;
        }
        int points = points(stack);
        int added = Math.min(n, need - points);
        if (added <= 0) {
            return 0;
        }
        CompoundTag tag = write(stack);
        tag.putInt(KEY_POINTS, points + added);
        tag.putInt(KEY_TOTAL, (int) Math.min(Integer.MAX_VALUE, (long) total(stack) + added));
        return added;
    }

    /** 把阶戳抬到 rank（只升不降）；抬了返回 true */
    public static boolean stampRank(ItemStack stack, int rank) {
        if (rank <= rankStamp(stack)) {
            return false;
        }
        write(stack).putByte(KEY_RANK, (byte) Math.min(Byte.MAX_VALUE, rank));
        return true;
    }

    /**
     * 直接设阶和本阶点数（指令、测试用）：阶夹到家族最高阶，点数夹到本阶门槛；累计至少是「前面各阶门槛之和 + 本阶点数」。
     */
    public static void set(ItemStack stack, int stage, int points) {
        AttunementFamily family = AttunementFamily.of(stack);
        int max = family == null ? MAX_STAGE : family.maxStage();
        int clampedStage = Math.max(0, Math.min(max, stage));
        int need = family == null ? AttunementConfig.threshold(clampedStage) : need(family, clampedStage);
        int clampedPoints = Math.max(0, Math.min(need, points));
        CompoundTag tag = write(stack);
        tag.putByte(KEY_STAGE, (byte) clampedStage);
        tag.putInt(KEY_POINTS, clampedPoints);
        long floor = (long) AttunementConfig.cumulative(clampedStage) + clampedPoints;
        tag.putInt(KEY_TOTAL, (int) Math.min(Integer.MAX_VALUE, Math.max(total(stack), floor)));
    }

    /**
     * 升一阶后的样子（不改 stack 本身）：复制整件（附魔、染色、模块、MRU、余烬……全部 NBT 照留），阶 + 1、本阶点数清零、累计和阶戳不变。
     */
    public static ItemStack ascended(ItemStack stack) {
        ItemStack out = stack.copyWithCount(1);
        CompoundTag tag = write(out);
        tag.putByte(KEY_STAGE, (byte) Math.min(MAX_STAGE, stage(stack) + 1));
        tag.putInt(KEY_POINTS, 0);
        return out;
    }

    /**
     * 同调转移 / 升格时新件拿到的同调度（D10 C）：旧件累计的一半，不超过新件 1 阶门槛的两倍；再按新件本阶还差多少夹（不跨阶）。
     */
    public static int transferAmount(ItemStack from, ItemStack to) {
        int half = total(from) / 2;
        int cap = 2 * AttunementConfig.threshold(0);
        int room = Math.max(0, need(to) - points(to));
        return Math.max(0, Math.min(Math.min(half, cap), room));
    }

    /** 同调转移后旧件的样子（不改 stack 本身）：清掉本阶点数和累计，阶、阶戳照留 */
    public static ItemStack cleared(ItemStack stack) {
        ItemStack out = stack.copy();
        CompoundTag root = out.getTag();
        if (root != null && root.contains(TAG, Tag.TAG_COMPOUND)) {
            CompoundTag tag = root.getCompound(TAG);
            tag.putInt(KEY_POINTS, 0);
            tag.putInt(KEY_TOTAL, 0);
        }
        return out;
    }

    /**
     * 升格成另一件物品（裁决之刃）时把旧件的 NBT 搬到新物品上：全部 NBT 照抄，同调从 0 阶起、拿旧件累计的一半（{@link #transferAmount}），阶戳照留。
     */
    public static ItemStack promoted(ItemStack from, ItemStack resultTemplate) {
        ItemStack out = resultTemplate.copy();
        CompoundTag source = from.getTag();
        if (source != null) {
            CompoundTag merged = source.copy();
            CompoundTag own = out.getTag();
            if (own != null) {
                merged.merge(own);
            }
            out.setTag(merged);
        }
        int rank = rankStamp(from);
        CompoundTag tag = write(out);
        tag.putByte(KEY_STAGE, (byte) 0);
        tag.putInt(KEY_POINTS, 0);
        tag.putInt(KEY_TOTAL, 0);
        tag.putByte(KEY_RANK, (byte) rank);
        int gained = transferAmount(from, out);
        tag.putInt(KEY_POINTS, gained);
        tag.putInt(KEY_TOTAL, gained);
        return out;
    }

    /**
     * 全套的阶：四个盔甲槽穿的都是这个家族时，取四件里最低的阶；没穿齐返回 -1。各套「全套 n 阶」效果读它。
     */
    public static int fullSetStage(LivingEntity entity, AttunementFamily family) {
        int min = Integer.MAX_VALUE;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.is(family.tag())) {
                return -1;
            }
            min = Math.min(min, stage(stack));
        }
        return min;
    }

    /** 四个盔甲槽（头、胸、腿、脚） */
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
}
