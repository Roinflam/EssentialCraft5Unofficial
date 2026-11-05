package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.ConverterTier;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.Mode;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.Status;
import com.flameforge.essentialcraft5.block.MRUConverterBlock;
import com.flameforge.essentialcraft5.registry.ECConverters;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * MRU 转换器界面（五台共用）。生存档一个绑定宝石格 (112, 23)；无尽档没有格子。
 * 切模式走 {@link #clickMenuButton}：按钮 0 = 能量、1 = 魔力（porting_guide §9：不带坐标，原版已查 containerId、stillValid、旁观者，
 * 这里只认服务端菜单、校验按钮号；目标模式出不了、和现在相同都返回 false）。
 * <p>
 * 额外数值（{@link MachineData#extra}）：状态、模式、标志位（1 = 植物魔法可用、2 = 能量能出、4 = 魔力能出），之后五个大数各拆两格（{@link MachineData#wide}）：
 * 近 1 秒送出去的合计、缓存、缓存上限、本档每刻最多送出去多少、本档每刻最多吃多少 MRU。都是服务端现取；植物魔法装没装用这里同步的标志，客户端不自己查。
 * <p>
 * 客户端菜单的档位从打开界面时的额外数据读（坐标后面一个字节，{@code MRUConverterBlock.use} 写的）；读不到（旧调用方只写了坐标）时按客户端世界里那一格的方块定。
 */
public class MRUConverterMenu extends MachineMenu {

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_MODE = 1;
    public static final int EXTRA_FLAGS = 2;
    public static final int EXTRA_RECENT = 3;
    public static final int EXTRA_BUFFER = 5;
    public static final int EXTRA_BUFFER_CAP = 7;
    public static final int EXTRA_QUOTA = 9;
    public static final int EXTRA_MRU_PER_TICK = 11;
    private static final int EXTRA_COUNT = 13;

    public static final int FLAG_BOTANIA = 1;
    public static final int FLAG_ENERGY = 2;
    public static final int FLAG_MANA = 4;

    /** 宝石格的位置（生存档） */
    public static final int GEM_X = 112;
    public static final int GEM_Y = 23;

    @Nullable
    private final MRUConverterBlockEntity converter;
    private final ConverterTier tier;

    public MRUConverterMenu(int id, Inventory playerInventory, MRUConverterBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.tier(), be.getInventory(), MachineData.server(be, extras(be)), be);
    }

    public MRUConverterMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), buf);
    }

    private MRUConverterMenu(int id, Inventory playerInventory, BlockPos pos, FriendlyByteBuf buf) {
        this(id, playerInventory, pos, clientTier(buf, playerInventory, pos), null, MachineData.client(EXTRA_COUNT), null);
    }

    private MRUConverterMenu(int id, Inventory playerInventory, BlockPos pos, ConverterTier tier, @Nullable IItemHandler handler,
                             MachineData data, @Nullable MRUConverterBlockEntity be) {
        super(ECConverters.MRU_CONVERTER_MENU.get(), id, playerInventory, pos, data, tier.block(), be);
        this.converter = be;
        this.tier = tier;
        if (tier.slots() > 0) {
            IItemHandler slots = handler != null ? handler : clientHandler(tier.slots(), MRUConverterBlockEntity::slotRule);
            addMachineSlot(FilteredSlot.boundGem(slots, MRUConverterBlockEntity.SLOT_GEM, GEM_X, GEM_Y));
        }
        addPlayerInventory(playerInventory);
    }

    private static ConverterTier clientTier(FriendlyByteBuf buf, Inventory inventory, BlockPos pos) {
        if (buf.isReadable()) {
            return ConverterTier.byId(buf.readByte());
        }
        return inventory.player.level().getBlockState(pos).getBlock() instanceof MRUConverterBlock block ? block.tier() : ConverterTier.BASIC;
    }

    private static IntSupplier[] extras(MRUConverterBlockEntity be) {
        List<IntSupplier> list = new ArrayList<>();
        list.add(() -> be.status().ordinal());
        list.add(() -> be.mode().ordinal());
        list.add(be::menuFlags);
        Collections.addAll(list, MachineData.wide(be::recentOutput));
        Collections.addAll(list, MachineData.wide(be::buffer));
        Collections.addAll(list, MachineData.wide(be::bufferCap));
        Collections.addAll(list, MachineData.wide(be::outputQuota));
        Collections.addAll(list, MachineData.wide(be::perTick));
        return list.toArray(IntSupplier[]::new);
    }

    /**
     * 按钮 0 = 能量、1 = 魔力。客户端菜单、范围外的按钮号、目标模式出不了（比率或每刻上限为 0、魔力而植物魔法没加载）、和现在相同，一律 false。
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (converter == null || !(player instanceof ServerPlayer)) {
            return false;
        }
        Mode mode = Mode.byId(id);
        return mode != null && converter.setMode(mode);
    }

    public ConverterTier tier() {
        return tier;
    }

    public Status status() {
        return Status.byId(data.extra(EXTRA_STATUS));
    }

    public Mode mode() {
        Mode mode = Mode.byId(data.extra(EXTRA_MODE));
        return mode == null ? Mode.ENERGY : mode;
    }

    public boolean botaniaAvailable() {
        return (data.extra(EXTRA_FLAGS) & FLAG_BOTANIA) != 0;
    }

    /** 这一种在这台机器上能不能选（比率和每刻上限都不是 0，魔力还要植物魔法） */
    public boolean modeUsable(Mode which) {
        int flags = data.extra(EXTRA_FLAGS);
        return which == Mode.ENERGY ? (flags & FLAG_ENERGY) != 0 : (flags & FLAG_MANA) != 0 && (flags & FLAG_BOTANIA) != 0;
    }

    /** 这一种的比率是不是被关成 0（区别于「没装植物魔法」） */
    public boolean modeOff(Mode which) {
        int flags = data.extra(EXTRA_FLAGS);
        return (flags & (which == Mode.ENERGY ? FLAG_ENERGY : FLAG_MANA)) == 0;
    }

    /** 近 1 秒送出去的合计 */
    public int recentOutput() {
        return data.extraWide(EXTRA_RECENT);
    }

    public int buffer() {
        return data.extraWide(EXTRA_BUFFER);
    }

    public int bufferCap() {
        return data.extraWide(EXTRA_BUFFER_CAP);
    }

    /** 本档每刻最多送出去多少 */
    public int quota() {
        return data.extraWide(EXTRA_QUOTA);
    }

    /** 本档每刻最多吃多少 MRU（无尽档：按多少折算） */
    public int mruPerTick() {
        return data.extraWide(EXTRA_MRU_PER_TICK);
    }
}
