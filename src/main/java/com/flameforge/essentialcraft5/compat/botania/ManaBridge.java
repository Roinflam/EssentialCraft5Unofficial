package com.flameforge.essentialcraft5.compat.botania;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 植物魔法（Botania）魔力的桥：MRU 转换器往旁边推魔力只经过这里。
 * <p>
 * <b>这一层只用 EC5、原版、Forge 的类型</b>：植物魔法是可选依赖，没装的时候任何会被加载的类都不能在签名、字段、常量池里出现
 * 植物魔法自己的类型，否则一加载就 {@code NoClassDefFoundError}。真正碰植物魔法的只有 {@link BotaniaManaBridge}（全工程唯一引用
 * 植物魔法 api 的类），它只在 {@link ModList} 里有 {@value #MODID} 时才用反射按类名加载、实例化（同 Forge {@code DistExecutor} 的思路，
 * 这里不直接 {@code new}），否则用 {@link #NONE}。调用方拿到的接收方句柄是不透明的 {@link Object}，类型只有实现自己认得。
 * <p>
 * 规则（policy §4.14）：只推给 {@code canReceiveManaFromBursts() && !isFull()} 的接收方（植物魔法自己「现在收不收」的约定：
 * 符文祭坛没满、泰拉凝聚板摆着配方、魔力附魔台要魔力时才收；魔力池、发射器、通量场一直收）；魔力池和魔力发射器的
 * {@code receiveMana} 超出上限的部分直接丢，所以按剩余空间推；别的接收方按推完后存量的增量记账，存量不动的（魔力分配器、魔力销毁器）
 * 推过一次就标 {@link #BLIND}，不再推。不挂火花、不进植物魔法的魔力网络。
 */
public abstract class ManaBridge {

    /** 植物魔法的模组 id */
    public static final String MODID = "botania";
    /**
     * {@link #accept} 返回值里的标记位：推进去以后接收方的存量没动（魔力分配器、魔力销毁器这类收了马上分走或吞掉的），收进去多少认不出来。
     * 这一次按推出去的量记账（宁可算它收了，也不让魔力凭空多出来），调用方此后别再往这一面推，等这一面的邻居变了再说。
     * 实收的量不超过每刻额度（{@code Integer.MAX_VALUE / 2} 以内），用不到这一位
     */
    public static final int BLIND = 1 << 30;
    private static final String IMPL = "com.flameforge.essentialcraft5.compat.botania.BotaniaManaBridge";

    /** 没装植物魔法（或实现加载失败）时用的空实现：什么都找不到、什么都推不进去 */
    public static final ManaBridge NONE = new ManaBridge() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public @Nullable Object find(BlockEntity blockEntity, Direction side) {
            return null;
        }

        @Override
        public boolean isPresent(Object handle) {
            return false;
        }

        @Override
        public int accept(Object handle, int amount) {
            return 0;
        }

        @Override
        public void onInvalidate(Object handle, Runnable listener) {
        }

        @Override
        public int currentMana(Object handle) {
            return 0;
        }

        @Override
        public int maxMana(Object handle) {
            return -1;
        }

        @Override
        public boolean receiving(Object handle) {
            return false;
        }
    };

    private static volatile ManaBridge instance;

    protected ManaBridge() {
    }

    /** 当前环境用的桥：第一次调用时按模组表选定并缓存 */
    public static ManaBridge get() {
        ManaBridge bridge = instance;
        if (bridge == null) {
            synchronized (ManaBridge.class) {
                bridge = instance;
                if (bridge == null) {
                    bridge = create();
                    instance = bridge;
                }
            }
        }
        return bridge;
    }

    private static ManaBridge create() {
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded(MODID)) {
            return NONE;
        }
        try {
            return (ManaBridge) Class.forName(IMPL).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            // 植物魔法的版本和编译时的 api 对不上：魔力模式当成「没装」，能量模式照常
            EssentialCraft5.LOGGER.error("[MRU 转换器] 植物魔法已加载，但魔力桥初始化失败，魔力模式不可用", e);
            return NONE;
        }
    }

    /** 植物魔法装了、桥能用 */
    public abstract boolean available();

    /**
     * 查方块实体某一面的魔力接收能力（{@code side} 是从它那边看过来的面）。返回不透明句柄，没有就 null。
     * 同一个方块实体一般每次返回同一个句柄（能力的 LazyOptional 本身），调用方可以按引用比较、只挂一次作废监听。
     */
    @Nullable
    public abstract Object find(BlockEntity blockEntity, Direction side);

    /** 句柄还有效（邻居没被拆、能力没作废） */
    public abstract boolean isPresent(Object handle);

    /**
     * 往这个接收方推至多 {@code amount} 魔力，按上面的规则（不收的不推，魔力池 / 发射器按剩余空间推），返回实收的量（0 ≤ {@link #taken} ≤ amount），
     * 认不出实收时带 {@link #BLIND}。
     */
    public abstract int accept(Object handle, int amount);

    /** {@link #accept} 返回值里实收的量 */
    public static int taken(int result) {
        return result & ~BLIND;
    }

    /** {@link #accept} 返回值带不带 {@link #BLIND} */
    public static boolean blind(int result) {
        return (result & BLIND) != 0;
    }

    /**
     * 句柄作废时回调（邻居被拆、能力作废）。回调里只该置标记；监听会一直挂在邻居的能力上，所以 listener 别直接抓着方块实体（用弱引用）。
     */
    public abstract void onInvalidate(Object handle, Runnable listener);

    /** 接收方现在存着多少魔力（GameTest、排查用）；无效句柄返回 0 */
    public abstract int currentMana(Object handle);

    /** 接收方的魔力上限：魔力池 / 发射器才有，别的接收方返回 −1 */
    public abstract int maxMana(Object handle);

    /** 接收方现在收不收（{@code canReceiveManaFromBursts() && !isFull()}） */
    public abstract boolean receiving(Object handle);
}
