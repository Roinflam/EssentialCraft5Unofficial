package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.registry.ECBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 混铺模块（tools/plan/builder_upgrade.md §1.5、§2.2）：最多 4 种方块按整数权重 1～9 混着铺。两端共用（客户端预览、服务端确认和施工都调这里）。
 * <p>
 * NBT {@code builder_mix: {seed: int, entries: [{block: "minecraft:cobblestone", weight: 3}, …]}}。生效要同时满足：模块生效
 * （{@link BuilderModules#has} 混铺）、模式会放方块（填充、全部替换）、表里至少有一种还能放的方块。没有这个键、没装模块 = 老行为（只用选定的那一种）。
 * 读的时候再筛一遍：认不得的、已经不能选定的（{@link MagicalBuilderItem#recordFailure}，含 {@code builder_blacklist}）、和前面的用同一种物品当材料的丢掉，
 * 权重夹到 1～9，超过 {@code general.builder.modules.mix.max_kinds} 种的只取前几种。手改 NBT 改不出别的效果。
 * <p>
 * 每格选哪种：任务种子 = 表里的 seed 和这一单锚点的坐标打散（同一张表铺在不同地方花样不同，同一单在预览、确认、施工里一样），
 * 格子下标 = 这一格在外框里的位置（{@link #cellIndex}，和施工的先后顺序无关）；{@code mix64(任务种子 × 0x9E3779B97F4A7C15 + 下标)} 打散后
 * 对总权重取余落区间。挨着的小种子不能直接喂给 {@code LegacyRandomSource}（第一次 nextFloat 几乎一样），所以全程不用随机数对象。
 */
public final class BuilderMix {

    /** NBT 键 */
    public static final String TAG = "builder_mix";
    /** 一张表最多几种（配置 max_kinds 的上限） */
    public static final int HARD_MAX_KINDS = 4;
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 9;
    /** 打散用的大奇数（黄金分割） */
    public static final long GOLDEN = 0x9E3779B97F4A7C15L;
    /** 读 NBT 时最多看几项 */
    static final int SCAN_LIMIT = 16;

    private BuilderMix() {
    }

    /** 表里的一种：方块和权重 */
    public record Entry(Block block, int weight) {
    }

    /** 一张混铺表（已筛过、至少一种）和它的种子 */
    public record Table(List<Entry> entries, int seed) {

        public int size() {
            return entries.size();
        }

        public Block block(int index) {
            return entries.get(index).block();
        }

        public int totalWeight() {
            int sum = 0;
            for (Entry entry : entries) {
                sum += entry.weight();
            }
            return sum;
        }

        /** 这种方块在不在表里（只比方块，不比状态） */
        public boolean contains(BlockState state) {
            for (Entry entry : entries) {
                if (state.is(entry.block())) {
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 读写
    // ------------------------------------------------------------------

    /**
     * 这根魔杖这一单要不要混铺：模式会放方块、混铺模块生效、表里有能用的方块时返回表，否则 null（照老规矩用一种方块）。
     */
    @Nullable
    public static Table forWand(ItemStack wand, @Nullable BuilderMode mode) {
        if (mode == null || !mode.bottomUp() || !BuilderModules.has(wand, BuilderModules.Kind.MIX)) {
            return null;
        }
        return read(wand);
    }

    /** NBT 里的表，筛过（见类注释）；没有或筛完一种不剩返回 null。不看模块装没装 */
    @Nullable
    public static Table read(ItemStack wand) {
        CompoundTag tag = wand.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag mix = tag.getCompound(TAG);
        ListTag list = mix.getList("entries", Tag.TAG_COMPOUND);
        int max = ECBuilder.Config.mixMaxKinds();
        List<Entry> entries = new ArrayList<>(Math.min(list.size(), max));
        // 手改 NBT 塞了一长串没用的项时也只看前 SCAN_LIMIT 项（这里客户端每刻都会读）
        int scan = Math.min(list.size(), SCAN_LIMIT);
        for (int i = 0; i < scan && entries.size() < max; i++) {
            CompoundTag entry = list.getCompound(i);
            Block block = usableBlock(ResourceLocation.tryParse(entry.getString("block")));
            if (block == null || sameItem(entries, block)) {
                continue;
            }
            int weight = Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, entry.getInt("weight")));
            entries.add(new Entry(block, weight));
        }
        return entries.isEmpty() ? null : new Table(Collections.unmodifiableList(entries), mix.getInt("seed"));
    }

    /** 写一张表（就地改这根魔杖）；entries 为空时去掉整个键 */
    public static void write(ItemStack wand, List<Entry> entries, int seed) {
        if (entries.isEmpty()) {
            CompoundTag tag = wand.getTag();
            if (tag != null) {
                tag.remove(TAG);
            }
            return;
        }
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag one = new CompoundTag();
            one.putString("block", String.valueOf(ForgeRegistries.BLOCKS.getKey(entry.block())));
            one.putInt("weight", entry.weight());
            list.add(one);
        }
        CompoundTag mix = new CompoundTag();
        mix.putInt("seed", seed);
        mix.put("entries", list);
        wand.getOrCreateTag().put(TAG, mix);
    }

    /**
     * 能放进混铺表的方块：认得、不是空气、能选定（{@link MagicalBuilderItem#recordFailure}，含 builder_blacklist），
     * 而且它的物品放下去就是它自己（墙上的火把这类和别的方块共用一个物品的不收：两种用同一叠料，「持有」和确认会数两遍）；否则 null
     */
    @Nullable
    public static Block usableBlock(@Nullable ResourceLocation id) {
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id)) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null || block == Blocks.AIR || !(block.asItem() instanceof BlockItem item) || item.getBlock() != block) {
            return null;
        }
        return MagicalBuilderItem.recordFailure(block.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == null ? block : null;
    }

    /** 表里已经有用同一个物品当材料的方块了（含同一种方块） */
    public static boolean sameItem(List<Entry> entries, Block block) {
        for (Entry entry : entries) {
            if (entry.block() == block || entry.block().asItem() == block.asItem()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 混铺时料够不够（准星下的红字和开工前的二次确认同一个口径）：表里任一种持有少于这一单要放的那种格数就算缺，不比合计——
     * 一种用完会换别的几种顶上，铺出来的比例就和设定的不一样了。
     *
     * @param need 每种要放几格（按表的顺序）
     * @param held 每种持有多少；负数 = 不知道
     * @return 1 缺、0 够、-1 要放的那几种里有持有数不知道的（调用方退回比合计）
     */
    public static int shortByKind(int[] need, long[] held) {
        if (need.length != held.length) {
            return -1;
        }
        boolean shortage = false;
        for (int k = 0; k < need.length; k++) {
            if (need[k] <= 0) {
                continue;
            }
            if (held[k] < 0L) {
                return -1;
            }
            shortage |= held[k] < need[k];
        }
        return shortage ? 1 : 0;
    }

    // ------------------------------------------------------------------
    // 每格选哪种（纯函数）
    // ------------------------------------------------------------------

    /** 这一单的任务种子：表里的 seed 和锚点坐标打散 */
    public static long seedFor(Table table, BuilderArea area) {
        return seedFor(table, area.anchor());
    }

    /** 同上，直接给锚点（面板蓝图按预览里记下的锚点上色） */
    public static long seedFor(Table table, BlockPos anchor) {
        return mix64(((long) table.seed() << 32) ^ anchor.asLong());
    }

    /** 这一格在外框里的下标（x 最快、再 z、再 y），和施工先后无关 */
    public static int cellIndex(BoundingBox box, BlockPos pos) {
        int xs = box.getXSpan();
        int zs = box.getZSpan();
        return (pos.getX() - box.minX()) + xs * ((pos.getZ() - box.minZ()) + zs * (pos.getY() - box.minY()));
    }

    /** 下标为 index 的格子用表里第几种 */
    public static int pick(Table table, long seed, int index) {
        int n = table.size();
        if (n <= 1) {
            return 0;
        }
        long hashed = mix64(seed * GOLDEN + index);
        int roll = (int) Long.remainderUnsigned(hashed, table.totalWeight());
        for (int k = 0; k < n; k++) {
            roll -= table.entries().get(k).weight();
            if (roll < 0) {
                return k;
            }
        }
        return n - 1;
    }

    /** 外框 box 里 pos 这一格用第几种（预览、确认、施工都调它） */
    public static int kindAt(Table table, long seed, BoundingBox box, BlockPos pos) {
        return pick(table, seed, cellIndex(box, pos));
    }

    /** 同上，直接给方块 */
    public static Block blockAt(Table table, long seed, BoundingBox box, BlockPos pos) {
        return table.block(kindAt(table, seed, box, pos));
    }

    /** SplitMix64 的收尾一步：把相邻的输入打散到整个 64 位 */
    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
