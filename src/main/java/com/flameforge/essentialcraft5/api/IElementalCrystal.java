package com.flameforge.essentialcraft5.api;

import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 元素水晶。对应原作 TileElementalCrystal 的公开字段（size、fire、water、earth、air，都是 double）和 mutate。
 * ECCrystal 的 ElementalCrystalBlockEntity 实现它；水晶控制器、水晶提取器（ECEssence）只通过这个接口读写水晶，不 import 对方模块的类。
 * <p>
 * 数值范围（照原作）：
 * <ul>
 *   <li>大小 {@link #size()}：成型器做出来是 1，每 tick +0.002 × GrowthModifier，长到 {@link #MAX_SIZE}（100）停；
 *       原作判断是 {@code size < 100} 时再加一次，所以可能停在 100.00x，用的地方按 [0, 100] 夹一下再显示。</li>
 *   <li>四元素 {@link #element}：0~100，新水晶全是 0，只靠突变改变。原作 {@code mutate(int, int)} 越界（加完 &gt;100 或 &lt;0）时整次作废，
 *       这里改成夹到 [0, 100]（batch2 D3）：{@link #mutate} 返回实际变化量，调用方据此判断「有没有加上」。</li>
 * </ul>
 * 用法（ECEssence 的控制器）：
 * <pre>{@code
 * IElementalCrystal crystal = IElementalCrystal.adjacent(level, worldPosition); // 或 IElementalCrystal.at(level, pos)
 * if (crystal != null && crystal.isGrowing()) {
 *     float added = crystal.mutate(IElementalCrystal.Element.FIRE, level.random.nextInt(2 * (rarity + 1)));
 * }
 * }</pre>
 * 别跨 tick 持有返回的对象：水晶被挖掉后它就是个死方块实体。要缓存就缓存坐标，用的时候再 {@link #at} 一次（一次区块查找，很便宜）。
 * <p>
 * 实现方（ECCrystal）：内部可以照原作用 double 存（49 500 次 +0.002 累加，float 会有约 0.2 的误差），接口统一给 float。
 * {@link #mutate} 里夹值：用 double 存时直接 {@code next = Mth.clamp(old + delta, MIN_ELEMENT, MAX_ELEMENT)}、返回 {@code (float) (next - old)}
 * （别把 float 的变化量加回 double 字段，会差一点到不了 100）；用 float 存时可以用 {@link #clampedDelta}。变了就 setChanged 并按需同步客户端。
 */
public interface IElementalCrystal {

    /** 大小上限：原作 size &lt; 100 时生长 */
    float MAX_SIZE = 100F;
    /** 单个元素的下限、上限（原作 mutate 的判断） */
    float MIN_ELEMENT = 0F;
    float MAX_ELEMENT = 100F;

    /**
     * 四元素。序号 {@link #ordinal()} 就是原作 getElementByNum / 精华 meta % 4 的编号：0 火、1 水、2 地、3 风；
     * {@link #getSerializedName()} 可以当 NBT 键（原作键名 fire / water / earth / air 正好一致）和翻译键后缀（gui.essentialcraft5.crystal.fire 等）。
     */
    enum Element implements StringRepresentable {
        FIRE("fire"),
        WATER("water"),
        EARTH("earth"),
        AIR("air");

        private static final Element[] VALUES = values();

        private final String name;

        Element(String name) {
            this.name = name;
        }

        /** 原作编号 0~3 → 元素；越界返回 null（原作 getElementByNum 返回 -1） */
        @Nullable
        public static Element byIndex(int index) {
            return index >= 0 && index < VALUES.length ? VALUES[index] : null;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** 大小，0~100（见类注释） */
    float size();

    /** 某个元素的值，0~100 */
    float element(Element element);

    /** 是否还在生长（原作控制器、提取器看的是 size &lt; 100） */
    default boolean isGrowing() {
        return size() < MAX_SIZE;
    }

    /**
     * 给某个元素加 delta（可以为负），结果夹到 [{@link #MIN_ELEMENT}, {@link #MAX_ELEMENT}]。<b>只在服务端调。</b>
     *
     * @return 实际变化量（夹过以后的，可能是 0）
     */
    float mutate(Element element, float delta);

    /**
     * 实现 {@link #mutate} 用：当前值 current 加 delta 后夹到 [0, 100]，返回实际变化量。
     * 例：current 99、delta 5 → 1；current 1、delta −3 → −1。NaN / 无穷的 delta 当 0。
     */
    static float clampedDelta(float current, float delta) {
        if (!Float.isFinite(delta)) {
            return 0F;
        }
        return Mth.clamp(current + delta, MIN_ELEMENT, MAX_ELEMENT) - current;
    }

    /** pos 处的水晶（只看已完全加载的区块，不触发加载）；不是水晶返回 null */
    @Nullable
    static IElementalCrystal at(Level level, BlockPos pos) {
        return ECLevels.loadedBlockEntity(level, pos) instanceof IElementalCrystal crystal ? crystal : null;
    }

    /**
     * 紧挨着 pos 的水平四格里的水晶（原作水晶控制器 / 提取器的 getCrystal）。
     * 原作按东、西、南、北依次查，后找到的覆盖前面的，所以四面都有时取北边；这里按北、南、西、东的顺序取第一个，结果相同。
     */
    @Nullable
    static IElementalCrystal adjacent(Level level, BlockPos pos) {
        for (Direction direction : ADJACENT_ORDER) {
            IElementalCrystal crystal = at(level, pos.relative(direction));
            if (crystal != null) {
                return crystal;
            }
        }
        return null;
    }

    /** {@link #adjacent} 的查找顺序 */
    List<Direction> ADJACENT_ORDER = List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
}
