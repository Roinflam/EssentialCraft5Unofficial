package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.EnchantUpgraderBlockEntity;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Tier;
import com.flameforge.essentialcraft5.registry.ECEnchantUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * 附魔增幅台 / 升华台界面（两台共用，按档位取菜单类型和方块）。槽位：附魔书 (30, 44)、材料一 (62, 44)、材料二 (80, 44)、
 * 输出 (122, 44)、绑定宝石 (152, 44)。目标等级用四个按钮调：{@link #clickMenuButton} 的 0 / 1 / 2 / 3 = −5 / −1 / +1 / +5
 * （porting_guide §9：不带坐标，只认服务端菜单、校验按钮号）。
 * <p>
 * 额外数值：状态序号、正在做的一步的目标等级（0 = 没在做）、目标等级（short 范围）；
 * 进度、这一步总刻数（各占两格，{@link MachineData#wide}）；之后是 ESPE 那 4 格（{@link MachineData#serverWithESPE}）。
 */
public class EnchantUpgraderMenu extends MachineMenu {

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_STEP = 1;
    public static final int EXTRA_TARGET = 2;
    public static final int EXTRA_PROGRESS = 3;
    public static final int EXTRA_REQUIRED = 5;
    private static final int EXTRA_COUNT = 7;

    /** 四个目标等级按钮的增量 */
    public static final int[] TARGET_DELTAS = {-5, -1, 1, 5};

    private final Tier tier;
    @Nullable
    private final EnchantUpgraderBlockEntity upgrader;

    public EnchantUpgraderMenu(int id, Inventory playerInventory, EnchantUpgraderBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.serverWithESPE(be, extras(be)), be.tier(), be);
    }

    public EnchantUpgraderMenu(int id, Inventory playerInventory, FriendlyByteBuf buf, Tier tier) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(EnchantUpgraderBlockEntity.SLOTS, EnchantUpgraderBlockEntity::slotRule),
                MachineData.clientWithESPE(EXTRA_COUNT), tier, null);
    }

    private static IntSupplier[] extras(EnchantUpgraderBlockEntity be) {
        IntSupplier[] extras = new IntSupplier[EXTRA_COUNT];
        extras[EXTRA_STATUS] = () -> be.status().ordinal();
        extras[EXTRA_STEP] = be::stepLevel;
        extras[EXTRA_TARGET] = be::targetLevel;
        System.arraycopy(MachineData.wide(be::progress), 0, extras, EXTRA_PROGRESS, 2);
        System.arraycopy(MachineData.wide(be::required), 0, extras, EXTRA_REQUIRED, 2);
        return extras;
    }

    private EnchantUpgraderMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data, Tier tier,
                                @Nullable EnchantUpgraderBlockEntity be) {
        super(ECEnchantUpgrade.menu(tier).get(), id, playerInventory, pos, data, ECEnchantUpgrade.block(tier).get(), be);
        this.tier = tier;
        this.upgrader = be;
        addMachineSlot(new SlotItemHandler(handler, EnchantUpgraderBlockEntity.SLOT_BOOK, 30, 44));
        addMachineSlot(new SlotItemHandler(handler, EnchantUpgraderBlockEntity.SLOT_A, 62, 44));
        addMachineSlot(new SlotItemHandler(handler, EnchantUpgraderBlockEntity.SLOT_B, 80, 44));
        addMachineSlot(new OutputSlot(handler, EnchantUpgraderBlockEntity.SLOT_OUTPUT, 122, 44));
        addMachineSlot(FilteredSlot.boundGem(handler, EnchantUpgraderBlockEntity.SLOT_GEM, 152, 44));
        addPlayerInventory(playerInventory);
    }

    public Tier tier() {
        return tier;
    }

    /** 0 / 1 / 2 / 3：目标等级 −5 / −1 / +1 / +5。客户端、客户端菜单、范围外的按钮号一律 false */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (upgrader == null || !(player instanceof ServerPlayer) || id < 0 || id >= TARGET_DELTAS.length) {
            return false;
        }
        upgrader.setTargetLevel(upgrader.targetLevel() + TARGET_DELTAS[id]);
        return true;
    }

    public EnchantUpgraderBlockEntity.Status status() {
        return EnchantUpgraderBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    /** 正在做的这一步的目标等级；没在做为 0 */
    public int stepLevel() {
        return data.extra(EXTRA_STEP);
    }

    public int targetLevel() {
        return data.extra(EXTRA_TARGET);
    }

    public int progress() {
        return data.extraWide(EXTRA_PROGRESS);
    }

    public int required() {
        return data.extraWide(EXTRA_REQUIRED);
    }
}
