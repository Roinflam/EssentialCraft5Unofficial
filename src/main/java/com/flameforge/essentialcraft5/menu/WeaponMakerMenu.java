package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.block.WeaponMakerBlock;
import com.flameforge.essentialcraft5.blockentity.WeaponMakerBlockEntity;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.registry.ECWeaponMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 武器工作台界面（原作 ContainerWeaponBench + GuiWeaponBench 的按钮）。槽位坐标、用途按枪种取 {@link GunType#layout()}（照原作），
 * 0 号输出格 (152, 37) 只能拿；背包在 (8, 84)。没有额外同步数值：预览和「完成」按钮在客户端按同步来的槽位内容现算。
 *
 * <h2>按钮（porting_guide §9）</h2>
 * 只有一个：{@link #BUTTON_ASSEMBLE}「完成」。界面调 {@code gameMode.handleInventoryButtonClick(containerId, 0)}，服务端原版先查过
 * 「打开的就是这个菜单、不是旁观者、stillValid（方块实体还是这一个、8 格内）」，这里再查一遍，然后照 {@code GunAssembly} 的条件组装。
 * 原作的按钮包（DummyCore handleButtonPress）带着客户端给的坐标，服务端直接取那个坐标的方块实体强转，不查距离、不查是不是打开着这个界面；
 * 这里不收坐标。客户端菜单上的 {@code clickMenuButton} 直接返回 false，不做预测。
 *
 * <h2>打开时的额外数据</h2>
 * 坐标 + 一个字节的枪种（{@link #writeOpenData}）。只有坐标时（开发展示厅用 {@code NetworkHooks.openScreen(p, be, pos)} 打开）按客户端那一格的方块认枪种。
 *
 * <h2>Shift 点击（审查 MAKER-1 取 A）</h2>
 * 从背包 Shift 进工作台时不走 {@link MachineMenu} 的「按格子序号先合并再放空格」：同一种锭能进枪托、机栓、握把、狙击镜好几类格子，
 * 按序号放的话狙击台一组魔力锭会整组塞进排在前面的镜材格，四样部件一样都没齐。这里按用途排先后（{@link #QUICK_MOVE_ORDER}：
 * 核心、灵魂 → 枪托 → 机栓 → 握把 → 镜座 / 镜材 / 透镜座 / 透镜 → 可选的透镜、瞄具），分两步：
 * <ol>
 *   <li>先给每个肯收它的<b>空</b>格各放 1 个（数量不够时排在前面的用途先拿到）；</li>
 *   <li>剩下的往已有的同种堆里补，一轮每格补 1 个、轮流补到放不下为止——组装时每格各扣 1，几格数量齐平才能连着做几把。</li>
 * </ol>
 * 每一格照旧先问 {@code mayPlace}（槽位规则），输出格不收任何东西。工作台 → 背包的方向、别的机器的 Shift 点击都不变（{@link MachineMenu#quickMoveStack}）。
 */
public class WeaponMakerMenu extends MachineMenu {

    /** 「完成」：组装一把枪 */
    public static final int BUTTON_ASSEMBLE = 0;

    /** Shift 点击往工作台里放时各用途的先后（同一组里按格子序号）；没列出的（输出格）不放 */
    private static final List<List<GunType.SlotRole>> QUICK_MOVE_ORDER = List.of(
            List.of(GunType.SlotRole.CORE, GunType.SlotRole.SOUL),
            List.of(GunType.SlotRole.BASE),
            List.of(GunType.SlotRole.DEVICE),
            List.of(GunType.SlotRole.HANDLE),
            List.of(GunType.SlotRole.SCOPE_HOUSING, GunType.SlotRole.SCOPE, GunType.SlotRole.LENS_HOUSING, GunType.SlotRole.LENS),
            List.of(GunType.SlotRole.LENS_OPT, GunType.SlotRole.SCOPE_OPT));

    private final GunType gunType;
    private final IItemHandler handler;
    /** 服务端才有 */
    @Nullable
    private final WeaponMakerBlockEntity maker;
    /** 工作台各格（菜单下标 = 物品栏下标）按 {@link #QUICK_MOVE_ORDER} 排好的顺序 */
    private final int[] quickMoveOrder;

    /** 服务端 */
    public WeaponMakerMenu(int id, Inventory playerInventory, WeaponMakerBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.gunType(), be.getInventory(), MachineData.server(be), be);
    }

    /** 客户端（菜单类型的工厂） */
    public WeaponMakerMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, OpenData.read(playerInventory, buf));
    }

    private WeaponMakerMenu(int id, Inventory playerInventory, OpenData open) {
        this(id, playerInventory, open.pos(), open.type(),
                clientHandler(open.type().layout().size(), (slot, stack) -> WeaponMakerBlockEntity.slotRule(open.type(), slot, stack)),
                MachineData.client(0), null);
    }

    private WeaponMakerMenu(int id, Inventory playerInventory, BlockPos pos, GunType type, IItemHandler handler, MachineData data,
                            @Nullable WeaponMakerBlockEntity be) {
        super(ECWeaponMaker.WEAPON_MAKER_MENU.get(), id, playerInventory, pos, data, ECWeaponMaker.maker(type).get(), be);
        this.gunType = type;
        this.handler = handler;
        this.maker = be;
        for (GunType.Slot slot : type.layout().slots()) {
            addMachineSlot(slot.role() == GunType.SlotRole.OUTPUT
                    ? new OutputSlot(handler, slot.index(), slot.x(), slot.y())
                    : new SlotItemHandler(handler, slot.index(), slot.x(), slot.y()));
        }
        addPlayerInventory(playerInventory);
        this.quickMoveOrder = quickMoveOrder(type);
    }

    /** 按用途排好的格子序号（布局里的格子是按序号加进菜单的，菜单下标 = 物品栏下标） */
    private static int[] quickMoveOrder(GunType type) {
        List<Integer> order = new ArrayList<>();
        for (List<GunType.SlotRole> group : QUICK_MOVE_ORDER) {
            for (GunType.Slot slot : type.layout().slots()) {
                if (group.contains(slot.role())) {
                    order.add(slot.index());
                }
            }
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 打开界面时写给客户端的数据：坐标 + 枪种（{@code WeaponMakerBlock.use}） */
    public static void writeOpenData(FriendlyByteBuf buf, BlockPos pos, GunType type) {
        buf.writeBlockPos(pos);
        buf.writeByte(type.ordinal());
    }

    public GunType gunType() {
        return gunType;
    }

    /** 工作台的物品栏（客户端是原版菜单同步来的副本），界面拿来算预览 */
    public IItemHandler handler() {
        return handler;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        // 客户端（或客户端菜单，没有方块实体）：什么都不做，只由服务端判
        if (!(player instanceof ServerPlayer serverPlayer) || maker == null || maker.isRemoved()) {
            return false;
        }
        // 原版 handleContainerButtonClick 已经查过这些，这里再查一遍（直接调用时也安全）
        if (id != BUTTON_ASSEMBLE || serverPlayer.isSpectator() || !stillValid(serverPlayer)) {
            return false;
        }
        return maker.assemble();
    }

    /**
     * Shift 点击。工作台里的格子 → 背包照 {@link MachineMenu}；背包 → 工作台按用途分格（见类注释「Shift 点击」）。
     * 写回背包那一格的做法和 {@link MachineMenu#quickMoveStack} 一样（原版 doClick 会接着再调一次，直到返回空或那一格换了东西）。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < gunType.layout().size()) {
            return super.quickMoveStack(player, index);
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveIntoBench(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.set(stack);
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /** 按用途分格放进工作台（原地改 {@code stack} 的数量）；放进去至少 1 个返回 true */
    private boolean moveIntoBench(ItemStack stack) {
        boolean moved = false;
        // 第一步：每个肯收它的空格各放 1 个
        for (int index : quickMoveOrder) {
            if (stack.isEmpty()) {
                return true;
            }
            Slot target = slots.get(index);
            if (!target.hasItem() && target.mayPlace(stack) && target.getMaxStackSize(stack) > 0) {
                target.setByPlayer(stack.split(1));
                moved = true;
            }
        }
        // 第二步：往已有的同种堆里补（含第一步刚放的），一轮每格 1 个，轮流补；先算好数量，每格只写一次
        List<Slot> targets = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<Integer> limits = new ArrayList<>();
        for (int index : quickMoveOrder) {
            Slot target = slots.get(index);
            ItemStack existing = target.getItem();
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, stack) || !target.mayPlace(stack)) {
                continue;
            }
            int limit = Math.min(target.getMaxStackSize(stack), stack.getMaxStackSize());
            if (existing.getCount() < limit) {
                targets.add(target);
                counts.add(existing.getCount());
                limits.add(limit);
            }
        }
        int left = stack.getCount();
        boolean progress = true;
        while (left > 0 && progress) {
            progress = false;
            for (int i = 0; i < targets.size() && left > 0; i++) {
                if (counts.get(i) < limits.get(i)) {
                    counts.set(i, counts.get(i) + 1);
                    left--;
                    progress = true;
                }
            }
        }
        for (int i = 0; i < targets.size(); i++) {
            Slot target = targets.get(i);
            if (counts.get(i) != target.getItem().getCount()) {
                // set 而不是 setChanged：SlotItemHandler 的 setChanged 不通知物品栏（同 MachineMenu）
                target.set(target.getItem().copyWithCount(counts.get(i)));
                moved = true;
            }
        }
        stack.setCount(left);
        return moved;
    }

    /** 客户端打开数据：坐标 + 枪种 */
    private record OpenData(BlockPos pos, GunType type) {

        static OpenData read(Inventory playerInventory, FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            GunType type = null;
            if (buf.readableBytes() > 0) {
                int ordinal = buf.readByte();
                if (ordinal >= 0 && ordinal < GunType.values().length) {
                    type = GunType.values()[ordinal];
                }
            }
            if (type == null) {
                type = playerInventory.player.level().getBlockState(pos).getBlock() instanceof WeaponMakerBlock block
                        ? block.gunType() : GunType.PISTOL;
            }
            return new OpenData(pos, type);
        }
    }
}
