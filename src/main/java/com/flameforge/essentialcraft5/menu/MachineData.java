package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import com.flameforge.essentialcraft5.mru.MRULink;
import net.minecraft.world.inventory.ContainerData;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * 机器界面同步的数值。服务端从方块实体现取，客户端存在数组里。
 * <p>
 * 原版菜单数据包按 short 传值（ClientboundContainerSetDataPacket.writeShort），超过 32767 会被截断，
 * 而 MRU 上限动辄上万到百万，所以 MRU 和上限各拆成低 16 位、高 16 位两格。
 * 机器自己的额外数值用 {@link #extra}，大数同样要拆（{@link #wide}），小数用 ×100 定点再拆（{@link #wideFixed}）。
 * <p>
 * 布局：0-1 MRU，2-3 上限，4-5 平衡值（float 的原始位，原样还原，冰冻/纯净/混沌的判断和服务端一致），
 * 6 绑定宝石状态，7 起是机器的额外数值。
 * 带 ESPE 的菜单（{@link #serverWithESPE} / {@link #clientWithESPE}）在额外数值<b>之后</b>再追加 4 格：
 * ESPE ×100 的低、高位，上限 ×100 的低、高位。追加在后面，额外数值的序号不受影响；不带 ESPE 的菜单格数和以前一样。
 * ×100 定点放在一个 int 里，最多只能表示约 2147 万（超出的夹到这个值，界面上的数字和 ESPE 球都会算错）：
 * 带 ESPE 界面的机器，{@code max_espe} 配置的上限不能超过 {@link #MAX_SYNCED_ESPE}（米斯莱熔炉已按它限）。
 * <p>
 * 两端必须用成对的工厂方法、额外数值个数一致：格数对不上时，客户端收到多出来的那格会直接抛越界异常断线。
 * <pre>{@code
 * // 服务端
 * MachineData.serverWithESPE(be, MachineData.wideFixed(be::getProgress));   // 额外数值 2 格
 * // 客户端
 * MachineData.clientWithESPE(2);
 * // 界面里读
 * data.espe(); data.maxESPE(); data.extraFixed(0);
 * }</pre>
 */
public class MachineData implements ContainerData {

    private static final int MRU = 0;
    private static final int MAX_MRU = 2;
    private static final int BALANCE = 4;
    private static final int LINK = 6;
    public static final int BASE_COUNT = 7;
    /** 带 ESPE 时追加的格数：ESPE 两格、上限两格 */
    public static final int ESPE_COUNT = 4;
    /** 定点数的倍率：ESPE 等 double 按 0.01 精度同步 */
    public static final double FIXED_SCALE = 100D;
    /**
     * 带 ESPE 的菜单能原样同步的 ESPE 上限：×100 以后要放进 int（最大约 21474836.47），取整到 2100 万。
     * 有 ESPE 界面的机器把 {@code max_espe} 配置的上限设成它（{@code defineInRange(…, 1D, MachineData.MAX_SYNCED_ESPE)}）。
     */
    public static final double MAX_SYNCED_ESPE = 21_000_000D;

    @Nullable
    private final MRUBlockEntity blockEntity;
    private final IntSupplier[] extras;
    private final int extraCount;
    private final boolean espe;
    private final int[] clientValues;

    private MachineData(@Nullable MRUBlockEntity blockEntity, IntSupplier[] extras, int extraCount, boolean espe) {
        this.blockEntity = blockEntity;
        this.extras = extras;
        this.extraCount = extraCount;
        this.espe = espe;
        this.clientValues = blockEntity == null ? new int[BASE_COUNT + extraCount + (espe ? ESPE_COUNT : 0)] : null;
    }

    /** 服务端：extras 每个占一格（short 范围）；大数用 {@link #wide} 拆成两个 */
    public static MachineData server(MRUBlockEntity blockEntity, IntSupplier... extras) {
        return new MachineData(blockEntity, extras, extras.length, false);
    }

    /**
     * 服务端，额外数值之后再同步方块实体的 ESPE 与上限（客户端用 {@link #clientWithESPE}）。
     * 方块实体没开 ESPE 时这 4 格恒为 0（不抛异常，界面显示空条）。
     */
    public static MachineData serverWithESPE(MRUBlockEntity blockEntity, IntSupplier... extras) {
        return new MachineData(blockEntity, extras, extras.length, true);
    }

    /** 客户端：只需要知道格数，和服务端的 extras 个数一致 */
    public static MachineData client(int extraCount) {
        return new MachineData(null, new IntSupplier[0], extraCount, false);
    }

    /** 客户端，配 {@link #serverWithESPE}；extraCount 同样是服务端 extras 的个数（不含 ESPE 那 4 格） */
    public static MachineData clientWithESPE(int extraCount) {
        return new MachineData(null, new IntSupplier[0], extraCount, true);
    }

    /** 把一个 int 拆成低、高 16 位两格，客户端用 {@link #extraWide} 取回 */
    public static IntSupplier[] wide(IntSupplier value) {
        return new IntSupplier[] {() -> low(value.getAsInt()), () -> high(value.getAsInt())};
    }

    /**
     * 把一个非负 double 按 ×100 定点拆成两格（精度 0.01，上限约 2147 万），客户端用 {@link #extraFixed} 取回。
     * 给 ESPE 这类小数用（例：米斯莱熔炉的进度按 ESPE 计）。负数、NaN 同步成 0。
     */
    public static IntSupplier[] wideFixed(DoubleSupplier value) {
        return wide(() -> toFixed(value.getAsDouble()));
    }

    @Override
    public int get(int index) {
        if (blockEntity == null) {
            return clientValues[index];
        }
        return switch (index) {
            case MRU -> low(blockEntity.getMRUStorage().getMRU());
            case MRU + 1 -> high(blockEntity.getMRUStorage().getMRU());
            case MAX_MRU -> low(blockEntity.getMRUStorage().getMaxMRU());
            case MAX_MRU + 1 -> high(blockEntity.getMRUStorage().getMaxMRU());
            case BALANCE -> low(Float.floatToRawIntBits(blockEntity.getMRUStorage().getBalance()));
            case BALANCE + 1 -> high(Float.floatToRawIntBits(blockEntity.getMRUStorage().getBalance()));
            case LINK -> blockEntity.getLinkStatus().ordinal();
            default -> index < espeIndex() ? extras[index - BASE_COUNT].getAsInt() : serverESPE(index - espeIndex());
        };
    }

    /** ESPE 那 4 格（只在服务端、带 ESPE 时走到） */
    private int serverESPE(int slot) {
        ESPEStorage storage = blockEntity.getESPEStorage();
        if (storage == null) {
            return 0;
        }
        int value = toFixed(slot < 2 ? storage.getESPE() : storage.getMaxESPE());
        return (slot & 1) == 0 ? low(value) : high(value);
    }

    @Override
    public void set(int index, int value) {
        if (clientValues != null) {
            clientValues[index] = value;
        }
    }

    @Override
    public int getCount() {
        return BASE_COUNT + extraCount + (espe ? ESPE_COUNT : 0);
    }

    public int mru() {
        return join(get(MRU), get(MRU + 1));
    }

    public int maxMRU() {
        return join(get(MAX_MRU), get(MAX_MRU + 1));
    }

    public float balance() {
        return MRUBalance.clamp(Float.intBitsToFloat(join(get(BALANCE), get(BALANCE + 1))));
    }

    public MRULink.Status linkStatus() {
        return MRULink.Status.byId(get(LINK));
    }

    /** 是否带 ESPE 那 4 格（用 serverWithESPE / clientWithESPE 建的） */
    public boolean hasESPE() {
        return espe;
    }

    /** 同步来的 ESPE 存量（精度 0.01）；不带 ESPE 的菜单返回 0 */
    public double espe() {
        return espe ? fromFixed(join(get(espeIndex()), get(espeIndex() + 1))) : 0D;
    }

    /** 同步来的 ESPE 上限（精度 0.01）；不带 ESPE 的菜单返回 0 */
    public double maxESPE() {
        return espe ? fromFixed(join(get(espeIndex() + 2), get(espeIndex() + 3))) : 0D;
    }

    /** 机器的第 i 个额外数值（short 范围，客户端收到的是有符号 short） */
    public int extra(int i) {
        return (short) get(BASE_COUNT + i);
    }

    /** 用 {@link #wide} 拆过的额外数值，i 是低位那格的序号 */
    public int extraWide(int i) {
        return join(get(BASE_COUNT + i), get(BASE_COUNT + i + 1));
    }

    /** 用 {@link #wideFixed} 拆过的额外数值，i 是低位那格的序号 */
    public double extraFixed(int i) {
        return fromFixed(extraWide(i));
    }

    private int espeIndex() {
        return BASE_COUNT + extraCount;
    }

    /** 四舍五入到 0.01 再转定点；夹在 [0, Integer.MAX_VALUE]（约 2147 万，见 {@link #MAX_SYNCED_ESPE}），NaN 按 0 */
    private static int toFixed(double value) {
        if (!(value > 0)) {
            return 0;
        }
        double scaled = value * FIXED_SCALE;
        return scaled >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round(scaled);
    }

    private static double fromFixed(int fixed) {
        // 定点值总是非负；按无符号读，防止哪天夹到上限以上时变成负数
        return Integer.toUnsignedLong(fixed) / FIXED_SCALE;
    }

    private static int low(int value) {
        return value & 0xFFFF;
    }

    private static int high(int value) {
        return (value >>> 16) & 0xFFFF;
    }

    private static int join(int low, int high) {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }
}
