package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.AnimalSeparatorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECSeparators;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 魔力动物分离器（幼年 {@code child_separator} / 成年 {@code adult_separator}）。原作是 {@code essentialcraft:device} 的 meta 6 / 7
 * （{@code BlockRedstoneDeviceNotSided}：Material.ROCK、硬度 1、抗爆 1、整块不透明、六面同贴图）。
 * <p>
 * 红石上升沿（{@link PulseMachineBlock}，原作是「有信号期间每次邻居更新都传一批」）；方块实体要 tick（从绑定宝石拉 MRU），{@code ticking = true}。
 * 右键打开界面（原作借用射线塔的界面：绑定宝石槽、MRU、平衡值），界面上另有主人状态。两种方块共用一个方块实体类型和一个菜单类型。
 */
public class AnimalSeparatorBlock extends PulseMachineBlock<AnimalSeparatorBlockEntity> {

    private final boolean children;

    /** @param children true = 幼年分离器（传幼崽），false = 成年分离器 */
    public AnimalSeparatorBlock(Properties properties, boolean children) {
        super(properties, false, true);
        this.children = children;
    }

    public boolean separatesChildren() {
        return children;
    }

    @Override
    protected BlockEntityType<AnimalSeparatorBlockEntity> blockEntityType() {
        return ECSeparators.ANIMAL_SEPARATOR_BE.get();
    }
}
