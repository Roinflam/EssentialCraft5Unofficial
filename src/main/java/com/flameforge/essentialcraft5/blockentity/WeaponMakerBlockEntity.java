package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.block.WeaponMakerBlock;
import com.flameforge.essentialcraft5.gun.GunAssembly;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.menu.WeaponMakerMenu;
import com.flameforge.essentialcraft5.registry.ECWeaponMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 武器工作台的方块实体（原作 TileWeaponMaker，继承 TileMRUGeneric 但 MRU 上限是 0）。
 * 槽数 = 这台工作台枪种的布局（{@link GunType#layout()}：手枪 10、步枪 13、狙击 14、加特林 19），0 号是输出格；没有绑定宝石格、不存 MRU。
 * <ul>
 *   <li>槽位规则（{@link #slotRule}，两端共用）：各格只收自己那一类已开放的材料，输出格谁都放不进（{@link GunAssembly#accepts}）。
 *       原作 isItemValidForSlot 只看下标，任何东西都能塞进任何格。漏斗照原作能往里塞（同一份规则），只能从 0 号格往外抽。</li>
 *   <li>组装（{@link #assemble}，只由菜单按钮在服务端调）：{@link GunAssembly#assemble} 算出新枪和用到的格，<b>只扣这些格各 1 个</b>，新枪放进 0 号格。</li>
 *   <li>不 tick（方块的 getTicker 为 null）、不存预览（原作把 previewStack 写进 NBT，界面自己算）、物品栏不同步给附近的人（渲染用不到）。</li>
 * </ul>
 * 工作台不碰世界、不远程读写，没有主人规则要守（主人照框架记下，不用）。
 */
public class WeaponMakerBlockEntity extends MRUBlockEntity {

    /** 构造时从方块取（super 构造器里还没赋值，{@link #gunType()} 兜底） */
    @Nullable
    private final GunType gunType;

    public WeaponMakerBlockEntity(BlockPos pos, BlockState state) {
        super(ECWeaponMaker.WEAPON_MAKER_BE.get(), pos, state, () -> 0, typeOf(state).layout().size());
        this.gunType = typeOf(state);
    }

    /** 方块状态是哪种枪的工作台；不是工作台（不该发生）按手枪 */
    public static GunType typeOf(BlockState state) {
        return state.getBlock() instanceof WeaponMakerBlock maker ? maker.gunType() : GunType.PISTOL;
    }

    public GunType gunType() {
        return gunType != null ? gunType : typeOf(getBlockState());
    }

    /** 槽位规则（服务端物品栏、漏斗、客户端菜单共用） */
    public static boolean slotRule(GunType type, int slot, ItemStack stack) {
        return GunAssembly.accepts(type, slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(gunType(), slot, stack);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == GunType.OUTPUT_SLOT;
    }

    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    /**
     * 组装一把枪：条件见 {@link GunAssembly}。成功时扣掉用到的格各 1 个（灵魂格物品连同里面的 MRU 一起用掉，MRU 已经转进新枪），
     * 新枪放进 0 号格，返回 true；条件不满足什么都不动，返回 false。只在服务端调。
     */
    public boolean assemble() {
        if (level == null || level.isClientSide) {
            return false;
        }
        Optional<GunAssembly.Result> result = GunAssembly.assemble(gunType(), inventory);
        if (result.isEmpty()) {
            return false;
        }
        for (int slot : result.get().consumeSlots()) {
            inventory.extractItem(slot, 1, false);
        }
        inventory.setStackInSlot(GunType.OUTPUT_SLOT, result.get().gun());
        return true;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new WeaponMakerMenu(id, inventory, this);
    }

    /**
     * 开发展示厅用（02_gui 截图看预览）：按这台的枪种放满一套能组装的材料——魔力锭枪托、淡青锭握把、米斯莱锭机栓，
     * 带混沌透镜和瞄具（狙击：米斯莱狙击镜；加特林：日光玻璃透镜座），灵魂球里存 2500 MRU。材料按注册名取，没注册的格子留空。
     */
    public void fillShowroomDemo() {
        for (GunType.Slot slot : gunType().layout().slots()) {
            ItemStack stack = switch (slot.role()) {
                case OUTPUT -> ItemStack.EMPTY;
                case CORE -> demoItem("elemental_core");
                case SOUL -> demoItem("soul_sphere");
                case LENS_OPT, LENS -> demoItem("chaos_matrix_projection");
                case LENS_HOUSING -> demoItem("sun_imbued_glass");
                case SCOPE_OPT, SCOPE_HOUSING -> demoItem("magic_purified_glass_alloy");
                case SCOPE, DEVICE -> demoItem("mithriline_ingot");
                case BASE -> demoItem("magical_ingot");
                case HANDLE -> demoItem("pale_ingot");
            };
            if (slot.role() == GunType.SlotRole.SOUL) {
                stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(2500));
            }
            inventory.setStackInSlot(slot.index(), stack);
        }
    }

    private static ItemStack demoItem(String path) {
        ResourceLocation id = EssentialCraft5.id(path);
        return ForgeRegistries.ITEMS.containsKey(id) ? new ItemStack(ForgeRegistries.ITEMS.getValue(id)) : ItemStack.EMPTY;
    }
}
