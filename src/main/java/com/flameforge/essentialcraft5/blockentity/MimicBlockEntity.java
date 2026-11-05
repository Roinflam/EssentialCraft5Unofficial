package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.block.camo.Camo;
import com.flameforge.essentialcraft5.registry.ECRedstoneTools;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 拟态方块的方块实体（原作 TileMimic）：只存一个伪装状态（被模仿的方块状态，null = 没伪装）。
 * <p>
 * 和原作的区别（batch4 §3.5）：
 * <ul>
 *   <li>不 tick。原作每 tick 递增一个没用的计数、每 60 tick 把整份 NBT 写两遍比对并发给 32 格内所有人（TileMimic L32~57）；
 *       这里只在伪装变了时 {@code setChanged} + 发一次原版方块实体更新包（{@link #setCamo}）。</li>
 *   <li>改伪装会存盘（原作 BlockMimic L96 / L105 不 markDirty，区块没被别的东西标脏时伪装重进世界就没了）。</li>
 *   <li>存档键 {@code camo}（{@link Camo#write}，方块状态 + 属性），不做原作 1.12 存档（{@code mimicState} 数字 id）的兼容：1.12 存档本来就不能直接升级到 1.20。</li>
 * </ul>
 * 渲染走框架的伪装模型（F4-11）：{@link #getModelData()} 交出伪装状态，客户端收到新伪装时 {@link Camo#clientChanged} 重画这一格。
 * 更新包里<b>总带一个 {@code has_camo} 键</b>：清掉伪装后更新标签要是空的，原版会把包里的标签当成 null，客户端就不会 load、一直画着旧伪装。
 */
public class MimicBlockEntity extends BlockEntity {

    /** 存档 / 更新包里伪装状态的键 */
    public static final String TAG_CAMO = "camo";
    /** 更新包里的「有没有伪装」标记（只为了让清掉伪装时的更新标签不是空的） */
    private static final String TAG_HAS_CAMO = "has_camo";

    @Nullable
    private BlockState camo;

    public MimicBlockEntity(BlockPos pos, BlockState state) {
        this(ECRedstoneTools.MIMIC_BE.get(), pos, state);
    }

    /** 给子类（第五批花纹拟态 {@code FancyMimicBlockEntity}）传自己的方块实体类型（F5-2）；存读档、同步、伪装规则全部继承 */
    protected MimicBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 当前伪装（没伪装为 null）。两端都能读；客户端的值来自更新包 */
    @Nullable
    public BlockState camo() {
        return camo;
    }

    /**
     * 设 / 清伪装（只在服务端调；调用方先用 {@code MimicBlock.acceptsCamo} 判断能不能当伪装）。
     * 变了才存盘并通知客户端，返回是否变了。
     */
    public boolean setCamo(@Nullable BlockState newCamo) {
        if (Objects.equals(camo, newCamo)) {
            return false;
        }
        camo = newCamo;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        return true;
    }

    @Override
    public @NotNull ModelData getModelData() {
        return Camo.modelData(camo);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        Camo.write(tag, TAG_CAMO, camo);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        camo = Camo.read(tag, TAG_CAMO);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        Camo.write(tag, TAG_CAMO, camo);
        tag.putBoolean(TAG_HAS_CAMO, camo != null);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        BlockState old = camo;
        CompoundTag tag = pkt.getTag();
        // 防御：标签为空的包（理论上不会有，见类注释）当成「没伪装」
        load(tag == null ? new CompoundTag() : tag);
        if (!Objects.equals(old, camo)) {
            Camo.clientChanged(this);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        BlockState old = camo;
        super.handleUpdateTag(tag);
        if (!Objects.equals(old, camo)) {
            Camo.clientChanged(this);
        }
    }
}
