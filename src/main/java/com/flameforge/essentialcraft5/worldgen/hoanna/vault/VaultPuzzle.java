package com.flameforge.essentialcraft5.worldgen.hoanna.vault;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.vault.VaultElement;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.Arrays;

/**
 * 一座封印宝库的谜题（batch13.md §6.4；HS §2.4.1），纯数据，由结构生成时的谜题种子定：
 * <ul>
 *   <li>{@link #pedestals()}：前厅四座祭座各刻哪种元素，下标是祭座在锁孔哪一侧的 {@link Direction#get2DDataValue()}（南 0、西 1、北 2、东 3）；</li>
 *   <li>{@link #order()}：正确顺序，第 k 个要放的元素（{@link VaultElement#ordinal()}）；</li>
 *   <li>{@link #riddle()}：碑文四行的排列，第 i 行写的是顺序里的第 riddle[i] 个（元素 order[riddle[i]] + 次序词 riddle[i]）。</li>
 * </ul>
 * 三份排列各 4! 种，彼此独立。碑文四行合起来能唯一还原出顺序：每行给出一种元素和它的次序，四行覆盖四个次序。
 * 宝库核心的方块实体把这三份数组原样存进 NBT（不只存种子：以后改了这里的抽法，已经生成的宝库照旧）。
 */
public record VaultPuzzle(int[] pedestals, int[] order, int[] riddle) {

    /** 四个次序词的键名，下标 = 次序（0 = 先） */
    public static final String[] ORDINALS = {"first", "second", "third", "last"};

    public VaultPuzzle {
        pedestals = check(pedestals);
        order = check(order);
        riddle = check(riddle);
    }

    /** 按谜题种子抽三份排列（Fisher–Yates，种子先过一遍混合，挨着的种子也不相关） */
    public static VaultPuzzle of(long seed) {
        RandomSource random = RandomSource.create(mix(seed));
        return new VaultPuzzle(shuffle(random), shuffle(random), shuffle(random));
    }

    /** 读档：三份都得是 0~3 的排列，坏了的整份按种子 0 重抽（返回值不会是 null） */
    public static VaultPuzzle read(int[] pedestals, int[] order, int[] riddle) {
        if (isPermutation(pedestals) && isPermutation(order) && isPermutation(riddle)) {
            return new VaultPuzzle(pedestals, order, riddle);
        }
        EssentialCraft5.LOGGER.warn("[封印宝库] 核心里的谜题数据坏了，按默认重抽");
        return of(0L);
    }

    /** 刻在 side 那一侧的祭座的元素 */
    public VaultElement elementAt(Direction side) {
        return VaultElement.byId(pedestals[side.get2DDataValue()]);
    }

    /** 第 step 个要放的元素（0~3） */
    public VaultElement expected(int step) {
        return VaultElement.byId(order[Math.max(0, Math.min(3, step))]);
    }

    /** 碑文第 line 行（0~3）对应的次序 */
    public int lineOrdinal(int line) {
        return riddle[line];
    }

    /** 碑文第 line 行的元素 */
    public VaultElement lineElement(int line) {
        return VaultElement.byId(order[riddle[line]]);
    }

    /** 碑文第 line 行的翻译键：{@code message.essentialcraft5.vault.riddle.<元素>.<次序>} */
    public String riddleKey(int line) {
        return riddleKey(lineElement(line), lineOrdinal(line));
    }

    public static String riddleKey(VaultElement element, int ordinal) {
        return "message.essentialcraft5.vault.riddle." + element.getSerializedName() + "." + ORDINALS[ordinal];
    }

    /** 只看碑文四行（元素 + 次序）还原顺序；四行的次序不是 0~3 各一个时返回 null */
    public static int[] solveFromRiddle(VaultElement[] lineElements, int[] lineOrdinals) {
        int[] order = new int[4];
        Arrays.fill(order, -1);
        for (int i = 0; i < 4; i++) {
            int k = lineOrdinals[i];
            if (k < 0 || k > 3 || order[k] >= 0) {
                return null;
            }
            order[k] = lineElements[i].ordinal();
        }
        return order;
    }

    private static int[] shuffle(RandomSource random) {
        int[] values = {0, 1, 2, 3};
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = values[i];
            values[i] = values[j];
            values[j] = t;
        }
        return values;
    }

    public static boolean isPermutation(int[] values) {
        if (values == null || values.length != 4) {
            return false;
        }
        boolean[] seen = new boolean[4];
        for (int v : values) {
            if (v < 0 || v > 3 || seen[v]) {
                return false;
            }
            seen[v] = true;
        }
        return true;
    }

    private static int[] check(int[] values) {
        if (!isPermutation(values)) {
            throw new IllegalArgumentException("宝库谜题要 0~3 的排列：" + Arrays.toString(values));
        }
        return values.clone();
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VaultPuzzle that && Arrays.equals(pedestals, that.pedestals) && Arrays.equals(order, that.order)
                && Arrays.equals(riddle, that.riddle);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(pedestals) + Arrays.hashCode(order)) + Arrays.hashCode(riddle);
    }

    @Override
    public String toString() {
        return "VaultPuzzle[pedestals=" + Arrays.toString(pedestals) + ", order=" + Arrays.toString(order) + ", riddle=" + Arrays.toString(riddle) + "]";
    }
}
