package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.WeaponMakerBlockEntity;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.menu.WeaponMakerMenu;
import com.flameforge.essentialcraft5.registry.ECWeaponMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 武器工作台（原作 BlockWeaponMaker 的 meta 0~3，每种枪一个方块）。右键打开组装界面（潜行不开），挖掉掉出物品栏（{@link MachineBlock}）。
 * <p>
 * 和别的机器不同的两处：
 * <ul>
 *   <li><b>不 tick</b>（{@link #getTicker} 返回 null）：工作台没有 MRU、不扫描、只在玩家点「完成」时干活；
 *       方块实体也不把物品栏同步给附近的人（渲染用不到），界面里的物品走原版菜单同步。</li>
 *   <li>打开界面时除了坐标还写一个字节的枪种（{@link WeaponMakerMenu#writeOpenData}）：客户端菜单的槽数必须和服务端一样，
 *       不能只靠「客户端那一格现在是什么方块」（那一格刚被换掉时两边会对不上）。别的地方（开发展示厅）只写坐标打开时，客户端按那一格的方块认枪种。</li>
 * </ul>
 * 原作的 isOpaqueCube 是 false，但 setLightOpacity(15) 又让它完全挡光；模型是整格 cube_top，这里就当普通实心方块（挡光、剔除相邻面）。
 */
public class WeaponMakerBlock extends MachineBlock<WeaponMakerBlockEntity> {

    private final GunType gunType;

    public WeaponMakerBlock(Properties properties, GunType gunType) {
        super(properties, false);
        this.gunType = gunType;
    }

    /** 这台工作台造哪种枪 */
    public GunType gunType() {
        return gunType;
    }

    @Override
    protected BlockEntityType<WeaponMakerBlockEntity> blockEntityType() {
        return ECWeaponMaker.WEAPON_MAKER_BE.get();
    }

    /** 不 tick（见类注释） */
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof WeaponMakerBlockEntity be) {
            NetworkHooks.openScreen(serverPlayer, be, buf -> WeaponMakerMenu.writeOpenData(buf, pos, be.gunType()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
