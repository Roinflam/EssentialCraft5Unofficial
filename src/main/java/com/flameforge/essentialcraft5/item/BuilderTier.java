package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.registry.ECBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 创世神之杖的三档（tools/plan/builder_upgrade.md §2.1）和每档的数值表。
 * <pre>
 *                     一阶 铺设魔杖     二阶 创世神之杖         三阶 创世神之杖·升格
 * 怎么认              物品本身          NBT 没有 builder_tier    NBT builder_tier = 3
 * 模式                只有填充          5 种                    5 种
 * 形状                两点、铺面、直线   全部                    全部
 * 最大格数            256              4096（max_volume）       16384
 * 铺面 / 直线边长      16               48（max_side）           96
 * 两点最远            16               48                      96
 * 施工距离            32               64                      96
 * 每刻最多            8                64                      128
 * 每格 MRU 放/拆/换    25 / — / —       25 / 250 / 300          ×0.8 向下取整：20 / 200 / 240
 * 自带 MRU            5000             5000                    20000
 * 撤销栈深            1                1                       3
 * 模块槽              2                3                       4
 * </pre>
 * 二阶的数就是 SERVER 配置 {@code general.builder} 里原有的那几个键（默认值没动，老魔杖行为不变）；一阶、三阶各在
 * {@code general.builder.paver.*} / {@code general.builder.ascended.*}。最大格数最后再夹一道 {@code hard_max_volume}（模块加成也越不过）。
 * <p>
 * 读数一律走 {@link #stats(ItemStack)}：施工（{@link BuilderJobs}）、选点、尺寸（{@link BuilderActions}）、客户端的准星下提示和虚影都从这里取，
 * 不再直接读 {@code ECBuilder.Config.maxVolume()} 这类。SERVER 配置在登录时同步到客户端，两端算出来的一样；
 * 运行中改 toml 触发的重载不会再发给客户端，客户端的尺寸夹取、虚影、「太大」提示要重进才跟上（服务端照新值夹，以服务端为准）。
 * GameTest 的覆盖值（{@code ECBuilder.Config.setTestOverrides} 的离选区距离、每刻格数）对三档都生效，盖掉档位自己的数。
 */
public enum BuilderTier {
    PAVER(1, 5000, 1, 2),
    BUILDER(2, 5000, 1, 3),
    ASCENDED(3, 20000, 3, 4);

    /** NBT 键：二 / 三阶魔杖的档位（只认 3；没有或别的值都是二阶）。铺设魔杖是单独的物品，不看这个键 */
    public static final String TAG_TIER = "builder_tier";
    /** 一阶能用的形状（竖墙、沿面一阶不给） */
    private static final Set<BuilderShape> PAVER_SHAPES = EnumSet.of(BuilderShape.POINTS, BuilderShape.PLANE, BuilderShape.LINE);

    private final int level;
    private final int mruCapacity;
    private final int undoDepth;
    private final int moduleSlots;

    BuilderTier(int level, int mruCapacity, int undoDepth, int moduleSlots) {
        this.level = level;
        this.mruCapacity = mruCapacity;
        this.undoDepth = undoDepth;
        this.moduleSlots = moduleSlots;
    }

    /** 第几阶（1 ~ 3），也是物品属性 {@code essentialcraft5:builder_tier} 的值 */
    public int level() {
        return level;
    }

    /** 物品自带的 MRU 容量 */
    public int mruCapacity() {
        return mruCapacity;
    }

    /** 撤销栈深 */
    public int undoDepth() {
        return undoDepth;
    }

    /** 模块槽数（{@link BuilderModules}） */
    public int moduleSlots() {
        return moduleSlots;
    }

    /** 这档能不能用这个模式：一阶只有填充 */
    public boolean allows(BuilderMode mode) {
        return this != PAVER || mode == BuilderMode.FILL;
    }

    /** 这档能不能用这个形状：一阶只有两点、铺面、直线 */
    public boolean allows(BuilderShape shape) {
        return this != PAVER || PAVER_SHAPES.contains(shape);
    }

    /** NBT 里没存形状时用的形状：一阶铺面，二 / 三阶两点（老魔杖） */
    public BuilderShape defaultShape() {
        return this == PAVER ? BuilderShape.PLANE : BuilderShape.POINTS;
    }

    /** 单按魔杖键切到的下一个形状（跳过这档不能用的） */
    public BuilderShape nextShape(BuilderShape current) {
        BuilderShape next = current.next();
        for (int i = 0; i < BuilderShape.values().length && !allows(next); i++) {
            next = next.next();
        }
        return allows(next) ? next : defaultShape();
    }

    /** 这件物品是哪一档；不是创世神之杖的按二阶算（调用方应先用 {@link BuilderMode#of} 判断是不是魔杖） */
    public static BuilderTier of(ItemStack stack) {
        if (stack.getItem() instanceof PavingWandItem) {
            return PAVER;
        }
        CompoundTag tag = stack.getTag();
        if (stack.getItem() instanceof MagicalBuilderItem && tag != null && tag.contains(TAG_TIER, Tag.TAG_ANY_NUMERIC)
                && tag.getInt(TAG_TIER) == ASCENDED.level) {
            return ASCENDED;
        }
        return BUILDER;
    }

    /** 这件魔杖现在的数值（配置值 + 模块修正 + 硬上限） */
    public static Stats stats(ItemStack stack) {
        BuilderTier tier = of(stack);
        return withModules(tier.base(), stack).capped();
    }

    /** 这档不装模块时的数值（已经夹过硬上限） */
    public Stats baseStats() {
        return base().capped();
    }

    /** 按配置取这档的基础数 */
    private Stats base() {
        ECBuilder.Config.TierValues values = ECBuilder.Config.tierValues(this);
        double multiplier = values.costMultiplier();
        return new Stats(this, values.maxVolume(), values.maxSide(), values.maxPointDistance(),
                ECBuilder.Config.reachOverride(values.maxReach()), ECBuilder.Config.cellsPerTickOverride(values.cellsPerTick()),
                scaled(BuilderMode.PLACE_COST, multiplier), scaled(BuilderMode.BREAK_COST, multiplier), scaled(BuilderMode.REPLACE_COST, multiplier),
                mruCapacity, undoDepth, moduleSlots);
    }

    /**
     * 模块修正（{@link BuilderModules#active}，倍率在 {@code general.builder.modules.*}）叠到基础数上：
     * 扩域乘格数和边长、疾工乘每刻格数、节能乘每格 MRU（放 / 拆 / 换三样）、远域加施工距离，各自向下取整；
     * 顺序是「档位配置值 → GameTest 覆盖值（距离、每刻格数）→ 模块 → {@link Stats#capped} 夹 {@code hard_max_volume} 和边长 256」，
     * 所以扩域越不过硬上限。三阶的花费是先 ×0.8 取整、再 ×0.6 取整（放置 25 × 0.8 = 20，再 × 0.6 = 12）。没装模块原样返回。混铺、回收不改数值。
     */
    private static Stats withModules(Stats base, ItemStack stack) {
        List<BuilderModules.Kind> modules = BuilderModules.active(stack);
        if (modules.isEmpty()) {
            return base;
        }
        ECBuilder.Config.ModuleValues values = ECBuilder.Config.moduleValues();
        int maxVolume = base.maxVolume();
        int maxSide = base.maxSide();
        int maxReach = base.maxReach();
        int cellsPerTick = base.cellsPerTick();
        int place = base.placeCost();
        int breaks = base.breakCost();
        int replace = base.replaceCost();
        if (modules.contains(BuilderModules.Kind.RANGE)) {
            maxVolume = scaledUp(maxVolume, values.rangeVolume());
            maxSide = scaledUp(maxSide, values.rangeSide());
        }
        if (modules.contains(BuilderModules.Kind.HASTE)) {
            cellsPerTick = scaledUp(cellsPerTick, values.hasteCells());
        }
        if (modules.contains(BuilderModules.Kind.EFFICIENCY)) {
            place = scaled(place, values.efficiencyCost());
            breaks = scaled(breaks, values.efficiencyCost());
            replace = scaled(replace, values.efficiencyCost());
        }
        if (modules.contains(BuilderModules.Kind.REACH)) {
            maxReach = (int) Math.min(Integer.MAX_VALUE, (long) maxReach + Math.max(0, values.reachBonus()));
        }
        return new Stats(base.tier(), maxVolume, maxSide, base.maxPointDistance(), maxReach, cellsPerTick,
                place, breaks, replace, base.mruCapacity(), base.undoDepth(), base.moduleSlots());
    }

    /** 乘倍率（≥ 1 的那种）、向下取整，不超过 int 上限 */
    private static int scaledUp(int value, double multiplier) {
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(value * multiplier + 1.0E-9D));
    }

    /** 花费乘倍率、向下取整（加一点余量防 0.6 × 25 这类乘出 14.999…） */
    public static int scaled(int cost, double multiplier) {
        return Math.max(0, (int) Math.floor(cost * multiplier + 1.0E-9D));
    }

    /**
     * 一件魔杖的数值。花费是物品本身的价（还没算玩家身上的减免，扣费时照旧经 {@code MRUDrain.discounted}）。
     */
    public record Stats(BuilderTier tier, int maxVolume, int maxSide, int maxPointDistance, int maxReach, int cellsPerTick,
                        int placeCost, int breakCost, int replaceCost, int mruCapacity, int undoDepth, int moduleSlots) {

        /** 夹硬上限：格数不超过 {@code hard_max_volume}，其余各项至少 1 */
        Stats capped() {
            int hard = ECBuilder.Config.hardMaxVolume();
            return new Stats(tier, Math.max(1, Math.min(maxVolume, hard)), Math.max(1, Math.min(maxSide, ECBuilder.Config.HARD_MAX_SIDE)),
                    Math.max(1, maxPointDistance), Math.max(1, maxReach), Math.max(1, cellsPerTick),
                    placeCost, breakCost, replaceCost, mruCapacity, undoDepth, moduleSlots);
        }

        /** 这一格要做的事花多少（物品本身的价） */
        public int cost(BuilderMode.Action action) {
            return switch (action) {
                case PLACE -> placeCost;
                case BREAK -> breakCost;
                case REPLACE -> replaceCost;
                default -> 0;
            };
        }

        /** 这件魔杖的模式物品自己那一行写的价：填充写放置价，清除类写拆除价 */
        public int modeCost(BuilderMode mode) {
            return mode == BuilderMode.FILL ? placeCost : breakCost;
        }
    }
}
