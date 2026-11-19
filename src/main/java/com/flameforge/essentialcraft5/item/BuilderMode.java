package com.flameforge.essentialcraft5.item;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 创世神之杖的 5 种模式（原作 ItemMagicalBuilder 的 meta 0~4），每种模式一个物品（{@code registry/ECBuilder}）。
 * 工作台里单放一根换到 {@link #next()}，顺序同原作配方 magical_builder_0~4：填充 → 清除 → 清除选定方块 → 清除选定以外 → 全部替换 → 填充。
 * <p>
 * 每一格做什么由 {@link #decide} 决定（纯函数，GameTest 和手册都照它）：
 * <pre>
 *                 填充    清除    清除选定方块   清除选定以外   全部替换
 * 空气            放      —       —             —             放
 * 纯液体          —       —       —             —             放
 * 受保护的格子    —       —       —             —             —      （带方块实体、硬度 &lt; 0、builder_blacklist）
 * 同一种方块      —       拆      拆            —             —      （只比方块，不比状态）
 * 别的方块        —       拆      —             拆            换
 * </pre>
 * 原作 meta 2 / 3 叫「只替换选定方块 / 替换选定以外」，代码里只把格子清成空气、不放任何东西（L376~433），所以名字改成「清除」。
 * 原作清除时连液体格也发破坏事件、扣 250 MRU（拆不掉，白扣）；这里液体格在清除类模式里直接跳过，连事件都不发。
 */
public enum BuilderMode {
    /** meta 0「只填充空气」：原作 L323~349 */
    FILL("fill"),
    /** meta 1「清除为空气」：原作 L350~375；唯一不需要选定方块的模式（原作 L162、L284~291 只给 meta 1 放行） */
    CLEAR("clear"),
    /** meta 2：只清除和选定方块同种的格子（原作 L376~404） */
    CLEAR_MATCHING("clear_matching"),
    /** meta 3：清除和选定方块不同种的格子（原作 L405~433） */
    CLEAR_OTHERS("clear_others"),
    /** meta 4「全部替换」：空气放、别的方块换（原作 L434~486） */
    REPLACE_ALL("replace_all");

    /** 放一格的花费（原作 playerUseMRU(…, 25)，L329 / L440） */
    public static final int PLACE_COST = 25;
    /** 拆一格的花费（原作 L355 / L384 / L413） */
    public static final int BREAK_COST = 250;
    /** 换一格的花费（原作 L463）；拆成了、放被拒时只收 {@link #BREAK_COST} */
    public static final int REPLACE_COST = 300;

    private static final BuilderMode[] VALUES = values();

    private final String id;

    BuilderMode(String id) {
        this.id = id;
    }

    /** 注册名后缀和语言键里的名字：{@code magical_builder_<id>} */
    public String id() {
        return id;
    }

    /** 工作台里换到的下一个模式 */
    public BuilderMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** 开工前必须先选定一种方块：只有「清除」不用 */
    public boolean needsBlock() {
        return this != CLEAR;
    }

    /**
     * 按 y 从低到高做（会放方块的两种模式：下面的先放好，上面的才有地方立）；
     * 其余从高到低（先拆上面的，沙子、砂砾不会掉进下面已经清空的格子）。同一层里按 x、再按 z。
     */
    public boolean bottomUp() {
        return this == FILL || this == REPLACE_ALL;
    }

    /**
     * 作用于已有方块（清除三种、全部替换）：铺面、竖墙点中方块时从点中的那格本身起，沿面作用于连上的那层方块本身。
     * 只有填充是往空处放，从点中那一面外侧那格起、沿面铺在外侧那一层（{@link BuilderShapes#area}、{@link BuilderSurface.Scan#area(boolean)}）。
     */
    public boolean inPlace() {
        return this != FILL;
    }

    /** 这一格是什么情况 */
    public enum CellKind {
        /** 空气（含洞穴空气、虚空空气） */
        AIR,
        /** 纯液体格：水、岩浆（含流动的）、气泡柱；含水的台阶之类算 {@link #OTHER} */
        LIQUID,
        /** 带方块实体、硬度 &lt; 0（基岩、屏障）、在 {@code essentialcraft5:builder_blacklist} 里：哪种模式都不碰 */
        PROTECTED,
        /** 其余方块 */
        OTHER
    }

    /** 对一格要做的事 */
    public enum Action {
        SKIP,
        /** 放选定方块（花 {@link #PLACE_COST}） */
        PLACE,
        /** 拆掉、掉落进背包（花 {@link #BREAK_COST}） */
        BREAK,
        /** 先拆再放（花 {@link #REPLACE_COST}） */
        REPLACE
    }

    /**
     * 这一格怎么处理（见类注释的表）。
     *
     * @param sameBlock 格子里的方块和选定方块是同一种（只比方块，不比状态；没有选定方块时传 false）
     */
    public Action decide(CellKind kind, boolean sameBlock) {
        return switch (kind) {
            case AIR -> this == FILL || this == REPLACE_ALL ? Action.PLACE : Action.SKIP;
            case LIQUID -> this == REPLACE_ALL ? Action.PLACE : Action.SKIP;
            case PROTECTED -> Action.SKIP;
            case OTHER -> {
                if (sameBlock) {
                    yield this == CLEAR || this == CLEAR_MATCHING ? Action.BREAK : Action.SKIP;
                }
                yield switch (this) {
                    case CLEAR, CLEAR_OTHERS -> Action.BREAK;
                    case REPLACE_ALL -> Action.REPLACE;
                    default -> Action.SKIP;
                };
            }
        };
    }

    /** 这件物品是哪种模式的创世神之杖；不是创世神之杖返回 null */
    @Nullable
    public static BuilderMode of(ItemStack stack) {
        return stack.getItem() instanceof MagicalBuilderItem builder ? builder.mode() : null;
    }
}
