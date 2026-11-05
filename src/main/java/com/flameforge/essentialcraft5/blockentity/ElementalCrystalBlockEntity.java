package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.block.ElementalCrystalBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECCrystal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 元素水晶的方块实体。对应原作 TileElementalCrystal，实现框架接口 {@link IElementalCrystal}
 * （ECEssence 的水晶控制器、提取器经接口读写，不 import 本类）。
 * <p>
 * 大小没长满（&lt; 100）时每 tick 长 0.002 × GrowthModifier，1 → 100 要 49 500 tick（约 41 分钟）；
 * 同时每 tick 有 MutationChance（默认 0.1%）的几率突变：随机挑一个元素，变化 {@code nextInt(3) − nextInt(3)}（−2~+2，中间多）。
 * 长满以后既不长也不突变（原作突变写在生长的分支里）。
 * <p>
 * 数据照原作用 double 存，NBT 键也和原作一样：size、fire、water、earth、air（{@link #DATA_KEYS}）。
 * 挖下来经战利品表复制进物品的 BlockEntityTag，放回去时原版 BlockItem 自动 {@link #load} 回来。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li><b>突变越界夹到 [0, 100]</b>（batch2 D3）：原作加完越界就整次作废。</li>
 *   <li>大小长到 100 就停在 100（原作判断 size &lt; 100 再加一次，会停在 100.00x）。</li>
 *   <li>同步：原作每 60 tick 把整份 NBT 发给 32 格内所有人。这里只在大小跨过整数（渲染按整数缩放，原作如此）或元素变了时发原版更新包，
 *       约每 500 tick 一次。存盘照样每 tick 标记（只标记区块，不通知邻居）。</li>
 *   <li>支撑检查挪到方块的 canSurvive / updateShape（见 ElementalCrystalBlock），这里不再每 tick 查。</li>
 *   <li>生长粒子只在客户端冒（原作两端都调 spawnParticle）。</li>
 * </ul>
 */
public class ElementalCrystalBlockEntity extends BlockEntity implements IElementalCrystal {

    /** 原作 tileentities.elementalcrystal */
    public static final class Config {
        public static ForgeConfigSpec.DoubleValue mutationChance;
        public static ForgeConfigSpec.DoubleValue growthModifier;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("elemental_crystal");
            mutationChance = builder.comment("Chance per tick for a growing crystal to mutate: a random element changes by -2..+2 (clamped to 0..100).",
                            "生长中的水晶每 tick 突变的几率：随机一个元素变化 −2～+2（夹在 0～100）")
                    .defineInRange("mutation_chance", 0.001D, 0D, 1D);
            growthModifier = builder.comment("Size gained per tick = 0.002 × this (size 1 → 100 takes 49,500 ticks at 1.0).",
                            "每 tick 长大 0.002 × 这个值（1.0 时从 1 长到 100 要 49 500 tick）")
                    .defineInRange("growth_modifier", 1D, 0D, 1000D);
            builder.pop();
        }
    }

    /** 原作每 tick 的基础生长量 */
    public static final double BASE_GROWTH = 0.002D;
    public static final String TAG_SIZE = "size";
    /** 方块实体 NBT 里的全部数据键（掉落时原样复制进物品的 BlockEntityTag） */
    public static final List<String> DATA_KEYS = List.of(TAG_SIZE,
            Element.FIRE.getSerializedName(), Element.WATER.getSerializedName(),
            Element.EARTH.getSerializedName(), Element.AIR.getSerializedName());

    private double size;
    /** 按 {@link Element#ordinal()}：火、水、地、风 */
    private final double[] elements = new double[4];
    /** 读档（或放置）后第一次服务端 tick 核对一次亮度 */
    private boolean lightChecked;

    public ElementalCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ECCrystal.ELEMENTAL_CRYSTAL_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // IElementalCrystal
    // ------------------------------------------------------------------

    @Override
    public float size() {
        return (float) size;
    }

    @Override
    public float element(Element element) {
        return (float) elements[element.ordinal()];
    }

    /** 原始精度的读数（GameTest、物品数据用） */
    public double exactSize() {
        return size;
    }

    public double exactElement(Element element) {
        return elements[element.ordinal()];
    }

    /**
     * 夹到 [0, 100]，返回实际变化量（原作越界整次作废，D3 改成夹住）。只在服务端调：变了就标记存盘并通知客户端重画。
     * 在 double 上夹（别把 float 的变化量加回 double 字段，会差一点到不了 100）。
     */
    @Override
    public float mutate(Element element, float delta) {
        if (element == null || !Float.isFinite(delta)) {
            return 0F;
        }
        double change = applyMutation(element, delta);
        if (change != 0D) {
            sync();
        }
        return (float) change;
    }

    private double applyMutation(Element element, double delta) {
        int i = element.ordinal();
        double old = elements[i];
        double next = Mth.clamp(old + delta, MIN_ELEMENT, MAX_ELEMENT);
        if (next == old) {
            return 0D;
        }
        elements[i] = next;
        markSaved();
        return next - old;
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElementalCrystalBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ElementalCrystalBlockEntity be) {
        // 原作生长时每 tick 在方块顶上冒一个附魔台字符粒子
        if (be.size < MAX_SIZE) {
            RandomSource random = level.random;
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + random.nextFloat(), pos.getY() + 1D, pos.getZ() + random.nextFloat(), 0D, 0D, 0D);
        }
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        if (!lightChecked) {
            lightChecked = true;
            updateLight(level, pos, state);
        }
        if (size >= MAX_SIZE) {
            return;
        }
        boolean resync = false;
        double growth = BASE_GROWTH * ECConfig.get(Config.growthModifier);
        if (growth > 0D) {
            int before = (int) size;
            size = Math.min(MAX_SIZE, size + growth);
            markSaved();
            if ((int) size != before) {
                resync = true;
                updateLight(level, pos, state);
            }
        }
        RandomSource random = level.random;
        if (random.nextDouble() < ECConfig.get(Config.mutationChance)) {
            Element element = Element.byIndex(random.nextInt(4));
            int delta = random.nextInt(3) - random.nextInt(3);
            if (element != null && applyMutation(element, delta) != 0D) {
                resync = true;
            }
        }
        if (resync) {
            sync();
        }
    }

    /** 亮度 = 大小 / 100 × 15，向下取整（原作 getLightValue） */
    public static int lightFor(double size) {
        if (!(size > 0D)) {
            return 0;
        }
        return Mth.clamp((int) (size / MAX_SIZE * 15D), 0, 15);
    }

    /** 亮度放在方块状态里（光照引擎只在状态变化时重算）；只通知客户端，不做邻居更新 */
    private void updateLight(Level level, BlockPos pos, BlockState state) {
        int light = lightFor(size);
        if (state.hasProperty(ElementalCrystalBlock.LIGHT) && state.getValue(ElementalCrystalBlock.LIGHT) != light) {
            level.setBlock(pos, state.setValue(ElementalCrystalBlock.LIGHT, light), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** 只标记区块需要保存（不像 setChanged 那样通知比较器） */
    private void markSaved() {
        if (level != null && !level.isClientSide) {
            level.blockEntityChanged(worldPosition);
        }
    }

    /** 发原版方块实体更新包（同一 tick 里多次调用只发一次） */
    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    /** 只含数据键的一份 NBT（中键选取、GameTest 用；和物品 BlockEntityTag 里的内容一样） */
    public CompoundTag saveData() {
        CompoundTag tag = new CompoundTag();
        writeData(tag);
        return tag;
    }

    private void writeData(CompoundTag tag) {
        tag.putDouble(TAG_SIZE, size);
        for (Element element : Element.values()) {
            tag.putDouble(element.getSerializedName(), elements[element.ordinal()]);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeData(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        size = sanitize(tag.getDouble(TAG_SIZE), MAX_SIZE);
        for (Element element : Element.values()) {
            elements[element.ordinal()] = sanitize(tag.getDouble(element.getSerializedName()), MAX_ELEMENT);
        }
        lightChecked = false;
    }

    /** 夹在 [0, max]，NaN 按 0（手改 NBT、旧数据） */
    private static double sanitize(double value, double max) {
        return value > 0D ? Math.min(value, max) : 0D;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 水晶模型会伸进支撑方块、也会伸出本格，渲染包围盒往外扩一格 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1D);
    }
}
