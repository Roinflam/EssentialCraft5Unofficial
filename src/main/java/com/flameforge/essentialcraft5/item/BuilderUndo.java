package com.flameforge.essentialcraft5.item;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 创世神之杖的撤销记录（tools/plan/builder_upgrade.md §1.10）。只撤<b>放置</b>：放置类模式每放成一格记一条（位置、放下的状态、用掉的材料），
 * 任务第一次放成时才把记录压进这个人的栈（全被拒的任务不挤掉旧记录），栈深按做它的那根魔杖的档位（一、二阶 1，三阶 3，记在每条记录上）。清除类模式不产生记录：
 * 拆下来的东西已经进了背包，掉落和原方块不对等（石头掉圆石、玻璃什么都不掉），撤回去就是凭空造方块。
 * <p>
 * 撤的时候逐格（倒着做，后放的先撤）：现在的方块和记下的是同一个方块、整数属性（会自己变的除外）和台阶类型相同（{@link #unchanged}），
 * 才以玩家身份删掉（删的掉落丢掉），删成了退 1 个记下的材料；方块变了（被挖了、氧化了、作物长了、台阶补成双层）的不动也不退。
 * 每次真实删除最多退一次，所以同一格被两个人的记录同时认领时，先撤的删掉、后撤的看到方块没了不退，总量守恒。不退 MRU。
 * 只在内存里：下线、停服清掉；只在服务端线程上用。
 */
public final class BuilderUndo {

    /** 玩家 UUID → 撤销栈（栈顶是最近的） */
    private static final Map<UUID, Deque<Record>> STACKS = new HashMap<>();

    private BuilderUndo() {
    }

    /** 一次施工放下的格子 */
    public static final class Record {
        final ResourceKey<Level> dimension;
        /** 开工那一刻（服务器刻） */
        final long createdTick;
        long[] positions = new long[16];
        BlockState[] states = new BlockState[16];
        /** 用掉的材料；创造模式放的是 null（撤销时不退） */
        Item[] materials = new Item[16];
        int size;
        /** 栈深：第一次压栈时那根魔杖的档位给的（{@link #push}）；还没压过是 0 */
        int depth;
        /** 开工时魔杖的形状（面板「撤销上一次」那句写它）；不知道是 null */
        @Nullable
        BuilderShape shape;
        /**
         * 这条压在栈顶时结束过一单清除类（拆成过至少一格）：清除的结果不能撤，也不越过它撤这条（{@link BuilderJobs#clearedSince}）。
         * 记在记录上而不按时刻比：排队时建的记录比排在它前面的清除结束得早，按时刻比会把清除之后才放的这单也当成在清除之前；
         * 三阶多条时撤掉清除之后放的那条，下面这条照样标着，不会越过清除接着撤。
         */
        boolean clearedAfter;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        Record(ResourceKey<Level> dimension, long createdTick) {
            this.dimension = dimension;
            this.createdTick = createdTick;
        }

        void add(BlockPos pos, BlockState state, @Nullable Item material) {
            if (size == positions.length) {
                int grown = size * 2;
                positions = Arrays.copyOf(positions, grown);
                states = Arrays.copyOf(states, grown);
                materials = Arrays.copyOf(materials, grown);
            }
            positions[size] = pos.asLong();
            states[size] = state;
            materials[size] = material;
            size++;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        public int size() {
            return size;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public long createdTick() {
            return createdTick;
        }

        @Nullable
        public BuilderShape shape() {
            return shape;
        }

        /** 这条压在栈顶时结束过一单清除类（见字段注释） */
        public boolean clearedAfter() {
            return clearedAfter;
        }

        /** 放下的格子围成的外框；一格都没有返回 null */
        @Nullable
        public BoundingBox bounds() {
            return size == 0 ? null : new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        BlockPos pos(int index) {
            return BlockPos.of(positions[index]);
        }

        /** 只留前 count 格（撤到一半中断时，剩下没撤的那部分压回去） */
        void truncate(int count) {
            size = Math.max(0, Math.min(size, count));
        }
    }

    /** 新记录（还没压栈；第一次放成时 {@link #push}） */
    static Record create(ResourceKey<Level> dimension, long tick) {
        return new Record(dimension, tick);
    }

    /**
     * 压栈。栈是每人一条、不分魔杖，所以栈深记在每条记录上（第一次压栈时那根魔杖的档位），不按这一次用的魔杖整条截：
     * 从最早的一条看起，它上面压着的条数到了它自己的栈深就挤掉，挤到最早那条还留得住为止。
     * 三阶连做 3 单再拿铺设魔杖做 1 单，三阶那 3 条里只挤掉最早的 1 条，不会全截成 1 条；二阶做 1 单再接着用三阶，二阶那条照样被挤掉。
     * 只从最早一端挤，不从中间抽（撤销要按先后倒着来），所以低档的一条夹在两条三阶中间时会跟着留到下面的三阶那条被挤掉。
     */
    static void push(UUID player, Record record, int depth) {
        if (record.depth <= 0) {
            record.depth = Math.max(1, depth);
        }
        Deque<Record> stack = STACKS.computeIfAbsent(player, id -> new ArrayDeque<>());
        stack.remove(record);
        stack.push(record);
        while (stack.size() > stack.peekLast().depth) {
            stack.removeLast();
        }
    }

    /** 栈顶那条（不取出）；没有返回 null */
    @Nullable
    public static Record peek(UUID player) {
        Deque<Record> stack = STACKS.get(player);
        return stack == null ? null : stack.peek();
    }

    /** 取出栈顶那条来撤 */
    @Nullable
    static Record pop(UUID player) {
        Deque<Record> stack = STACKS.get(player);
        if (stack == null) {
            return null;
        }
        Record record = stack.poll();
        if (stack.isEmpty()) {
            STACKS.remove(player);
        }
        return record;
    }

    /** 撤到一半中断：没撤的那部分压回栈顶（时间、栈深不变，不按撤它的那根魔杖算），之后还能接着撤 */
    static void putBack(UUID player, Record record, int remaining, int depth) {
        record.truncate(remaining);
        if (record.size > 0) {
            push(player, record, depth);
        }
    }

    /** 栈里有几条 */
    public static int count(UUID player) {
        Deque<Record> stack = STACKS.get(player);
        return stack == null ? 0 : stack.size();
    }

    static void forget(UUID player) {
        STACKS.remove(player);
    }

    static void clearAll() {
        STACKS.clear();
    }

    /**
     * 放下以后会自己变、不代表格子里多了东西的整数属性，按名字认（模组方块常照原版起名）：红石线的信号强度（power），
     * 树叶、脚手架离支撑的距离（distance），耕地的湿度（moisture）。比了它们，红石线、树叶放下以后就撤不掉了。
     */
    private static final Set<String> DRIFTING = Set.of(
            BlockStateProperties.POWER.getName(),
            BlockStateProperties.DISTANCE.getName(),   // 脚手架的 STABILITY_DISTANCE 也叫 distance
            BlockStateProperties.MOISTURE.getName());

    /**
     * 这一格还是当初放下的样子：同一个方块，全部整数属性（海泡菜、蜡烛、雪层、海龟蛋的数量，作物年龄……）相同，台阶类型相同。
     * 方向、连接、含水这类属性不比（栅栏连上了邻居、楼梯拐了角不算变了），会自己变的整数属性（{@link #DRIFTING}）也不比。
     * 多面方块（发光地衣、幽匿脉络、藤蔓）的面是布尔属性，玩家往同一格又贴了几面时整格删掉只退 1 个，多贴的就没了：这类要整个状态都相同。
     */
    public static boolean unchanged(BlockState now, BlockState placed) {
        if (now.getBlock() != placed.getBlock()) {
            return false;
        }
        if (placed.getBlock() instanceof MultifaceBlock || placed.getBlock() instanceof VineBlock) {
            return now == placed;
        }
        for (Property<?> property : placed.getProperties()) {
            boolean compared = property instanceof IntegerProperty ? !DRIFTING.contains(property.getName()) : property == BlockStateProperties.SLAB_TYPE;
            if (compared && (!now.hasProperty(property) || !now.getValue(property).equals(placed.getValue(property)))) {
                return false;
            }
        }
        return true;
    }
}
