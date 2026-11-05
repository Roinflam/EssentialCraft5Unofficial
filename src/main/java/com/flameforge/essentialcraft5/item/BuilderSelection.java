package com.flameforge.essentialcraft5.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 建筑魔杖的选区和选定方块：存在物品 NBT 里的读写，加上选区的几何计算。纯函数、两端共用（选区框、提示行在客户端用，任务在服务端用）。
 * <p>
 * NBT（和 MRU 的 {@code mru} 键并列，工作台换模式时整份带过去）：
 * <ul>
 *   <li>{@value #TAG_FIRST} / {@value #TAG_SECOND}：两个点，int[3]；</li>
 *   <li>{@value #TAG_DIMENSION}：点所在的维度 id（原作不存维度，到下界照同样的坐标改）；</li>
 *   <li>{@value #TAG_BLOCK}：选定方块的注册名（只存方块，不存状态，放的时候按物品自己的放置规则算状态）。</li>
 * </ul>
 * 选区是两个点围成的长方体（含两端），格子顺序见 {@link #cellAt}。
 */
public final class BuilderSelection {

    public static final String TAG_FIRST = "point1";
    public static final String TAG_SECOND = "point2";
    public static final String TAG_DIMENSION = "dimension";
    public static final String TAG_BLOCK = "stored_block";

    private BuilderSelection() {
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    /** 第一个点；没设返回 null */
    @Nullable
    public static BlockPos first(ItemStack stack) {
        return point(stack, TAG_FIRST);
    }

    /** 第二个点；没设返回 null */
    @Nullable
    public static BlockPos second(ItemStack stack) {
        return point(stack, TAG_SECOND);
    }

    @Nullable
    private static BlockPos point(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key, Tag.TAG_INT_ARRAY)) {
            return null;
        }
        int[] xyz = tag.getIntArray(key);
        return xyz.length == 3 ? new BlockPos(xyz[0], xyz[1], xyz[2]) : null;
    }

    /** 两个点所在的维度；没设返回 null */
    @Nullable
    public static ResourceLocation dimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_DIMENSION, Tag.TAG_STRING) ? null : ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
    }

    /** 两个点都设了、维度也有 */
    public static boolean hasBothPoints(ItemStack stack) {
        return first(stack) != null && second(stack) != null && dimension(stack) != null;
    }

    /** 点是不是在这个维度里设的 */
    public static boolean inDimension(ItemStack stack, ResourceKey<Level> dimension) {
        return dimension.location().equals(dimension(stack));
    }

    /** 设第一个点（换了一次选区：第二个点清掉，维度记成 dimension） */
    public static void setFirst(ItemStack stack, BlockPos pos, ResourceKey<Level> dimension) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putIntArray(TAG_FIRST, new int[] {pos.getX(), pos.getY(), pos.getZ()});
        tag.remove(TAG_SECOND);
        tag.putString(TAG_DIMENSION, dimension.location().toString());
    }

    /** 设第二个点（维度沿用第一个点的） */
    public static void setSecond(ItemStack stack, BlockPos pos) {
        stack.getOrCreateTag().putIntArray(TAG_SECOND, new int[] {pos.getX(), pos.getY(), pos.getZ()});
    }

    /** 清掉两个点和维度（选定方块留着，同原作 resetPoints）；本来就没有点时返回 false */
    public static boolean clearPoints(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_FIRST) && !tag.contains(TAG_SECOND)) {
            return false;
        }
        tag.remove(TAG_FIRST);
        tag.remove(TAG_SECOND);
        tag.remove(TAG_DIMENSION);
        return true;
    }

    /** 选定方块的注册名；没选返回 null */
    @Nullable
    public static ResourceLocation storedBlock(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_BLOCK, Tag.TAG_STRING) ? null : ResourceLocation.tryParse(tag.getString(TAG_BLOCK));
    }

    public static void setStoredBlock(ItemStack stack, ResourceLocation block) {
        stack.getOrCreateTag().putString(TAG_BLOCK, block.toString());
    }

    // ------------------------------------------------------------------
    // 几何
    // ------------------------------------------------------------------

    /** 两点围成的长方体（含两端） */
    public static BoundingBox box(BlockPos a, BlockPos b) {
        return BoundingBox.fromCorners(a, b);
    }

    /** 两点围成的格数（long：坐标差很大时 int 会溢出） */
    public static long volume(BlockPos a, BlockPos b) {
        return (long) (Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1) * (Math.abs(a.getZ() - b.getZ()) + 1);
    }

    /** 两点的直线距离（按方块坐标算，同原作 DummyDistance：一格到隔壁一格是 1） */
    public static double distance(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }

    /** 选区的格数 */
    public static int cellCount(BoundingBox box) {
        return box.getXSpan() * box.getYSpan() * box.getZSpan();
    }

    /**
     * 第 index 格（0 起，小于 {@link #cellCount}）。一层一层做：bottomUp 为 true 时从最低一层起，否则从最高一层起；
     * 同一层里 x 从小到大，同一个 x 里 z 从小到大。
     */
    public static BlockPos cellAt(BoundingBox box, int index, boolean bottomUp) {
        int sizeX = box.getXSpan();
        int sizeZ = box.getZSpan();
        int perLayer = sizeX * sizeZ;
        int layer = index / perLayer;
        int rest = index % perLayer;
        int y = bottomUp ? box.minY() + layer : box.maxY() - layer;
        return new BlockPos(box.minX() + rest / sizeZ, y, box.minZ() + rest % sizeZ);
    }

    /** 点到选区（每格占满一整个方块）的最近距离的平方；在选区里为 0 */
    public static double distanceSqToBox(Vec3 point, BoundingBox box) {
        double dx = Math.max(Math.max(box.minX() - point.x, 0D), point.x - (box.maxX() + 1D));
        double dy = Math.max(Math.max(box.minY() - point.y, 0D), point.y - (box.maxY() + 1D));
        double dz = Math.max(Math.max(box.minZ() - point.z, 0D), point.z - (box.maxZ() + 1D));
        return dx * dx + dy * dy + dz * dz;
    }

    /** 选区框（世界坐标）：只有第一个点时框那一格，两点都有时框整个选区 */
    public static AABB outline(BlockPos first, @Nullable BlockPos second) {
        BlockPos other = second == null ? first : second;
        return new AABB(Math.min(first.getX(), other.getX()), Math.min(first.getY(), other.getY()), Math.min(first.getZ(), other.getZ()),
                Math.max(first.getX(), other.getX()) + 1, Math.max(first.getY(), other.getY()) + 1, Math.max(first.getZ(), other.getZ()) + 1);
    }
}
