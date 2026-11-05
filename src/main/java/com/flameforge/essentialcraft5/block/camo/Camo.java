package com.flameforge.essentialcraft5.block.camo;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 伪装（第四批框架 F4-11）的<b>公共</b>一半：规则、方块标签、模型属性、NBT 与同步帮手。拟态方块和第五批的 16 种花纹拟态共用（花纹拟态另在伪装上叠一层花纹，F5-1）。
 * <p>
 * <b>这个类两端都会加载</b>（拟态方块 {@code use} 的服务端分支调它），所以<b>不许引用任何客户端类、不许加 {@code @OnlyIn}</b>，
 * 否则专用服（含 {@code runGameTestServer}）一加载拟态方块就崩。{@link ModelProperty} / {@link ModelData} 虽然在
 * {@code net.minecraftforge.client.model.data} 包里，但没有 {@code @OnlyIn}，原版 {@code IForgeBlockEntity.getModelData()} 在服务端也引用它们，两端都能加载。
 * 渲染在客户端那一半 {@code client/camo/}（{@code CamoBakedModel} + {@code CamoClient}）。
 *
 * <h2>模块怎么用</h2>
 * <ol>
 *   <li><b>登记要包一层伪装模型的方块</b>：在模块注册类的 {@code init()} 里（模组构造阶段，模型烘焙和方块颜色注册之前）
 *       {@code Camo.registerCamoBlock(ECRedstoneTools.MIMIC);}。客户端在 {@code ModelEvent.ModifyBakingResult} 里把这些方块的所有状态包一层：
 *       没伪装时原样画本来的模型，伪装了就画被模仿方块的模型（按它的渲染类型分层，草、树叶的颜色也转发原版 {@code BlockColors}）。
 *       登记过的方块<b>不要再自己注册 BlockColor</b>（会覆盖掉转发）。</li>
 *   <li><b>方块实体提供 ModelData</b>：存一个 {@code @Nullable BlockState camo}，
 *       <pre>{@code
 * @Override public ModelData getModelData() { return Camo.modelData(camo); }
 * // saveAdditional / load：Camo.write(tag, "camo", camo);  camo = Camo.read(tag, "camo");
 * // getUpdateTag 里也写 camo（MRUBlockEntity 的机器关了物品栏同步时，伪装要单独放一个字段）
 * @Override public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
 *     BlockState old = camo;
 *     super.onDataPacket(net, pkt);                       // 调 load
 *     if (!Objects.equals(old, camo)) Camo.clientChanged(this);   // 刷新模型数据、重画这一格
 * }
 * @Override public void handleUpdateTag(CompoundTag tag) {   // 区块刚发来时；同上（onLoad 也会请求一次模型数据，这里保险）
 *     BlockState old = camo;
 *     super.handleUpdateTag(tag);
 *     if (!Objects.equals(old, camo)) Camo.clientChanged(this);
 * }
 * }</pre></li>
 *   <li>改伪装（服务端）：先 {@link #isValid} / {@link #fromItem} 判断，改完 {@code setChanged()}，再通知客户端——普通方块实体（拟态方块）
 *       {@code level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)}；{@code MRUBlockEntity} 的机器（目前没有带伪装的）调
 *       {@code markClientDirty()}（节流，关了物品栏同步也照发）。只在变化时发。</li>
 *   <li>往 {@link #MIMIC_BLACKLIST}（{@code essentialcraft5:mimic_blacklist}）里加不许被模仿的方块：模块在自己的 {@code tools/tags/<模块>.json} 里写
 *       （拟态方块自己……）；框架不建空标签文件（引用不存在的标签 = 空标签）。</li>
 *   <li><b>伪装上叠一层花纹</b>（第五批框架 F5-1，花纹拟态用）：登记时多传一个方块模型 id，
 *       {@code Camo.registerCamoBlock(ECDecorations.FANCY_MIMIC_BRICK, EssentialCraft5.id("block/fancy_overlay/brick"))}。
 *       客户端把这个模型登记成额外模型（{@code ModelEvent.RegisterAdditional}），伪装时在被模仿方块的面之后追加它的面，
 *       画在镂空（{@code cutout_mipped}）层；没伪装时照常画方块本来的模型（花纹拟态自己的模型里已经带花纹）。
 *       花纹模型要自己提供（JSON 方块模型，整格、六面带 cullface、{@code shade: false}、面不带 {@code tintindex}——被模仿方块的草色不会染到花纹上）；
 *       模型文件缺失时客户端记一条警告、按没有花纹画。被模仿方块不是整格时（半砖、楼梯……），客户端把花纹各面沿法线往外推
 *       {@link #OVERLAY_PUSH}（见 {@link #overlayPushedOut}），模型文件本身照旧贴着格子边界写。</li>
 * </ol>
 */
public final class Camo {

    /** 不许被模仿的方块（ECRedstoneTools 放拟态方块，以后花纹拟态） */
    public static final TagKey<Block> MIMIC_BLACKLIST = TagKey.create(Registries.BLOCK, EssentialCraft5.id("mimic_blacklist"));

    /**
     * 模型属性：被模仿的方块状态。不带校验谓词：{@code ModelData.Builder.with} 校验不过会抛异常，
     * 而黑名单是数据包标签，重载后可能变——在区块渲染线程上抛异常不值得，校验在服务端改伪装时做。
     */
    public static final ModelProperty<BlockState> CAMO_STATE = new ModelProperty<>();

    /** 要包一层伪装模型的方块与它们的花纹模型（模组构造阶段登记，客户端烘焙模型、注册方块颜色时读） */
    private static final List<Entry> CAMO_BLOCKS = new ArrayList<>();

    /** 登记项：方块，和伪装时叠在上面的花纹模型（没有是 null） */
    private record Entry(Supplier<? extends Block> block, @Nullable ResourceLocation overlay) {
    }

    private Camo() {
    }

    // ------------------------------------------------------------------
    // 规则
    // ------------------------------------------------------------------

    /**
     * 这个方块状态能不能当伪装：普通模型渲染（{@link RenderShape#MODEL}，排除液体、屏障、光源方块这类不可见的和箱子这类方块实体渲染的）、
     * 不是空气、没有方块实体（它的模型数据、渲染器都拿不到）、不在 {@link #MIMIC_BLACKLIST} 里。两端结果一样（标签随数据包同步到客户端）。
     */
    public static boolean isValid(@Nullable BlockState state) {
        return isValid(state, s -> s.is(MIMIC_BLACKLIST));
    }

    /** 同 {@link #isValid(BlockState)}，黑名单换成给定的判断（GameTest 不依赖还没人加成员的标签） */
    public static boolean isValid(@Nullable BlockState state, Predicate<BlockState> blacklisted) {
        return state != null
                && !state.isAir()
                && state.getRenderShape() == RenderShape.MODEL
                && !state.hasBlockEntity()
                && !blacklisted.test(state);
    }

    /** 物品对应的伪装状态（方块物品的默认状态），不能当伪装返回 null */
    @Nullable
    public static BlockState fromItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return isValid(state) ? state : null;
    }

    /** 物品能不能放进伪装槽（给带伪装槽的机器当槽位规则；原本是执行器 10 号格的，执行器已去掉，目前没有调用方） */
    public static boolean isValidItem(ItemStack stack) {
        return fromItem(stack) != null;
    }

    // ------------------------------------------------------------------
    // 模型数据、存盘、同步
    // ------------------------------------------------------------------

    /** 方块实体 {@code getModelData()} 的返回值；没伪装返回 {@link ModelData#EMPTY}（画本来的模型） */
    public static ModelData modelData(@Nullable BlockState camo) {
        return camo == null ? ModelData.EMPTY : ModelData.builder().with(CAMO_STATE, camo).build();
    }

    /** 某格方块实体当前的伪装（两端都能调；没有方块实体、没伪装返回 null） */
    @Nullable
    public static BlockState camoAt(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.getModelData().get(CAMO_STATE);
    }

    /** 写进 NBT；null 时删掉这个键 */
    public static void write(CompoundTag tag, String key, @Nullable BlockState camo) {
        if (camo == null) {
            tag.remove(key);
        } else {
            tag.put(key, NbtUtils.writeBlockState(camo));
        }
    }

    /**
     * 从 NBT 读；没有这个键、方块已经不存在（读成空气）返回 null。
     * 用内置方块注册表查（{@code load} 被调用时方块实体可能还没有 level），不做 {@link #isValid} 校验：
     * 数据包改了黑名单不会让已经伪装好的方块突然变样，只挡新的伪装。
     */
    @Nullable
    @SuppressWarnings("deprecation") // Forge 标了原版注册表过时，但 NbtUtils 要的就是 HolderGetter，原版方块实体读档也这么取（同 StructurePattern）
    public static BlockState read(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(key));
        return state.isAir() ? null : state;
    }

    /**
     * 客户端收到新的伪装以后调：请求刷新这一格的模型数据并重画所在区块段。服务端调用什么都不做。
     * （原版 {@code ClientLevel.sendBlockUpdated} 会把这一格标成要重建；标志 8 = 立刻在主线程重建，免得闪一下旧样子。）
     */
    public static void clientChanged(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null || !level.isClientSide) {
            return;
        }
        be.requestModelDataUpdate();
        BlockState state = be.getBlockState();
        level.sendBlockUpdated(be.getBlockPos(), state, state, Block.UPDATE_IMMEDIATE);
    }

    // ------------------------------------------------------------------
    // 要包伪装模型的方块
    // ------------------------------------------------------------------

    /**
     * 登记一个要包伪装模型的方块（传 {@code RegistryObject}）。在模块注册类的 {@code init()} 里调（模组构造阶段），
     * 客户端烘焙模型、注册方块颜色时统一取出来；重复登记同一个会被忽略。只存 Supplier，不碰客户端类，两端都能调。
     */
    public static void registerCamoBlock(Supplier<? extends Block> block) {
        registerCamoBlock(block, null);
    }

    /**
     * 同 {@link #registerCamoBlock(Supplier)}，另给一个伪装时叠在上面的花纹模型（方块模型 id，如 {@code essentialcraft5:block/fancy_overlay/brick}；
     * null = 不叠）。同一个方块重复登记时，给了花纹模型的那次覆盖之前的。
     */
    public static synchronized void registerCamoBlock(Supplier<? extends Block> block, @Nullable ResourceLocation overlayModel) {
        Objects.requireNonNull(block, "block");
        for (int i = 0; i < CAMO_BLOCKS.size(); i++) {
            if (CAMO_BLOCKS.get(i).block().equals(block)) {
                if (overlayModel != null) {
                    CAMO_BLOCKS.set(i, new Entry(block, overlayModel));
                }
                return;
            }
        }
        CAMO_BLOCKS.add(new Entry(block, overlayModel));
    }

    /** 登记过的方块（给客户端的 {@code client/camo/CamoClient} 用） */
    public static synchronized List<Supplier<? extends Block>> camoBlocks() {
        List<Supplier<? extends Block>> blocks = new ArrayList<>(CAMO_BLOCKS.size());
        for (Entry entry : CAMO_BLOCKS) {
            blocks.add(entry.block());
        }
        return Collections.unmodifiableList(blocks);
    }

    /**
     * 这个方块登记的花纹模型；没登记、没有花纹返回 null。按方块对象比（Supplier 取不到的跳过），
     * 在方块注册完以后调（客户端烘焙模型时）。
     */
    @Nullable
    public static synchronized ResourceLocation overlayOf(Block block) {
        for (Entry entry : CAMO_BLOCKS) {
            if (entry.overlay() == null) {
                continue;
            }
            try {
                if (entry.block().get() == block) {
                    return entry.overlay();
                }
            } catch (RuntimeException ignored) {
                // 那个模块没接线（RegistryObject 取不到），当作没登记
            }
        }
        return null;
    }

    /**
     * 花纹面往外推的距离（方块单位，= 0.05 像素），只在 {@link #overlayPushedOut} 为真时用（审查 DECOR-2）。
     * <p>
     * 花纹模型是整格。伪装成半砖、楼梯这类时，花纹的侧面和伪装的侧面在同一平面上、顶点却不同，两组三角形插值出的深度有舍入差，
     * 深度测试（LEQUAL）时有的像素花纹被丢掉，斜看花纹上出现一条条细竖线。整格伪装两组面的顶点完全一样
     * （原版烘焙把同一朝向的面统一成同样的顶点顺序），深度逐像素相同、不闪，所以不推——推了反而在远处（推出的距离小于深度精度时）可能闪。
     * <p>
     * 只沿各面自己的法线推、面内不放大：墙上相邻两格同一平面的花纹面仍然刚好对接、不重叠（整体放大会在接缝处重叠而闪）；
     * 代价是凸角处两个面之间留一条同样宽（1/320 格）的缝，1080p 下三格外不到一个像素。
     * 24 位深度缓冲、近平面 0.05 时，这个距离在约 30 格内仍有深度舍入误差（约一个深度单位）的三倍以上；更远处才可能偶尔闪（没推时是任何距离都闪）。
     * 原作 {@code offsetoverlay.json} 外扩 0.01 像素，这里取大一些，给远处留余量。
     */
    public static final float OVERLAY_PUSH = 0.05F / 16F;

    /**
     * 伪装时叠的花纹要不要往外推 {@link #OVERLAY_PUSH}：被模仿方块的碰撞箱不是整格就推。
     * 用碰撞箱是因为方块状态缓存里有现成的结果，区块渲染线程上每个面问一次也不花时间；
     * 碰撞箱比外观小的（灵魂沙、蜂蜜块这类）也会推，无害。别的模组的方块在空世界里算形状抛异常时按要推处理。两端都能调。
     */
    public static boolean overlayPushedOut(BlockState camo) {
        try {
            return !camo.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** 所有登记过的花纹模型（去重；客户端在 {@code ModelEvent.RegisterAdditional} 里登记成额外模型） */
    public static synchronized Set<ResourceLocation> overlayModels() {
        Set<ResourceLocation> models = new LinkedHashSet<>();
        for (Entry entry : CAMO_BLOCKS) {
            if (entry.overlay() != null) {
                models.add(entry.overlay());
            }
        }
        return Collections.unmodifiableSet(models);
    }
}
