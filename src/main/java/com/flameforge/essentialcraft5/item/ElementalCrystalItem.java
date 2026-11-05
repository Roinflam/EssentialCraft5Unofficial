package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.api.IElementalCrystal.Element;
import com.flameforge.essentialcraft5.blockentity.ElementalCrystalBlockEntity;
import com.flameforge.essentialcraft5.registry.ECCrystal;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 元素水晶的方块物品。对应原作 ItemBlockElementalCrystal（按元素比例染色）+ BlockElementalCrystal 里物品相关的部分
 * （按大小分 21 档图标、创造模式物品栏的变体）。
 * <p>
 * 数据放在 BlockEntityTag（原版方块物品的写法，放下时自动读进方块实体）：size、fire、water、earth、air，都是 double。
 * 原作把它们直接放在物品 NBT 顶层（float），1.20.1 不沿用。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>物品颜色：原作拼颜色时把绿、蓝两个通道写反了（{@code (r<<16)+(b<<8)+g}），水之水晶显示成绿色，这里按 RGB 拼。</li>
 *   <li>原作物品没有提示，玩家看不到大小和元素；这里加上（文字键 tooltip.essentialcraft5.elemental_crystal.*）。</li>
 * </ul>
 */
public class ElementalCrystalItem extends BlockItem implements CreativeVariants {

    /**
     * 物品属性：大小档位 0~20（⌊大小⌋ / 5，原作 MeshDefinitionElementalCrystal），模型按它切换 21 张图标。
     * 原版物品属性要夹在 0~1，所以属性值是 档位 / 20（{@link #sizeProperty}），模型里第 n 档的阈值是 {@link #stageThreshold}(n)，两边用同一个 float 算式。
     */
    public static final ResourceLocation SIZE_PREDICATE = EssentialCraft5.id("size");
    /** 最大档位 */
    public static final int STAGES = 20;

    /** 原作注释里的五种颜色：中性 D5F3F4、火 7D210B（实际用的是 130,33,11）、水 3298D4、地 114F1C、风 858D92 */
    private static final int[] NEUTRAL = {213, 243, 244};
    private static final int[][] ELEMENT_COLORS = {{130, 33, 11}, {50, 152, 212}, {17, 79, 28}, {133, 141, 146}};
    /** 原作颜色里「中性」的权重：元素全 0 时是中性色，元素越多越偏向元素色 */
    private static final double NEUTRAL_WEIGHT = 10D;
    private static final ChatFormatting[] ELEMENT_FORMATS = {ChatFormatting.RED, ChatFormatting.AQUA, ChatFormatting.DARK_GREEN, ChatFormatting.GRAY};

    public ElementalCrystalItem(Block block, Properties properties) {
        super(block, properties);
    }

    // ------------------------------------------------------------------
    // 数据读写
    // ------------------------------------------------------------------

    /** 物品里的大小（没有数据时 0，和原作一样） */
    public static double size(ItemStack stack) {
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        return tag == null ? 0D : clamp(tag.getDouble(ElementalCrystalBlockEntity.TAG_SIZE));
    }

    public static double element(ItemStack stack, Element element) {
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        return tag == null ? 0D : clamp(tag.getDouble(element.getSerializedName()));
    }

    private static double clamp(double value) {
        return value > 0D ? Math.min(value, IElementalCrystal.MAX_SIZE) : 0D;
    }

    /** 带数据的水晶（成型器产出、创造模式变体）。四元素按火、水、地、风的顺序 */
    public static ItemStack create(double size, double fire, double water, double earth, double air) {
        ItemStack stack = new ItemStack(ECCrystal.ELEMENTAL_CRYSTAL.get());
        CompoundTag tag = new CompoundTag();
        tag.putDouble(ElementalCrystalBlockEntity.TAG_SIZE, size);
        tag.putDouble(Element.FIRE.getSerializedName(), fire);
        tag.putDouble(Element.WATER.getSerializedName(), water);
        tag.putDouble(Element.EARTH.getSerializedName(), earth);
        tag.putDouble(Element.AIR.getSerializedName(), air);
        writeData(stack, tag);
        return stack;
    }

    /**
     * 把数据写进物品的 BlockEntityTag。不写方块实体类型 id（原版 setBlockEntityData 会加）：
     * 战利品表 copy_nbt 掉下来的水晶也没有 id，两边一致才能堆叠。放置时原版不看 id。
     */
    public static void writeData(ItemStack stack, CompoundTag data) {
        stack.addTagElement(BlockItem.BLOCK_ENTITY_TAG, data);
    }

    /** 图标档位 0~20 */
    public static int stage(ItemStack stack) {
        return Mth.clamp(Mth.floor(size(stack)) / 5, 0, STAGES);
    }

    /** 物品属性 {@link #SIZE_PREDICATE} 的值：档位 / 20 */
    public static float sizeProperty(ItemStack stack) {
        return stageThreshold(stage(stack));
    }

    /** 第 stage 档在物品模型 override 里的阈值（和 {@link #sizeProperty} 同一个算式，float 完全相等） */
    public static float stageThreshold(int stage) {
        return stage / (float) STAGES;
    }

    /**
     * 物品颜色（RGB）：五种颜色按「中性 10 + 四元素的值」加权平均（原作 ItemBlockElementalCrystal.getColorFromItemstack）。
     * 原作最后拼成 (r&lt;&lt;16)+(b&lt;&lt;8)+g，绿蓝写反；这里修正。
     */
    public static int color(ItemStack stack) {
        double[] weights = new double[4];
        double total = NEUTRAL_WEIGHT;
        for (Element element : Element.values()) {
            weights[element.ordinal()] = element(stack, element);
            total += weights[element.ordinal()];
        }
        int rgb = 0;
        for (int channel = 0; channel < 3; channel++) {
            double value = NEUTRAL[channel] * NEUTRAL_WEIGHT;
            for (int i = 0; i < 4; i++) {
                value += ELEMENT_COLORS[i][channel] * weights[i];
            }
            rgb = (rgb << 8) | Mth.clamp((int) (value / total), 0, 255);
        }
        return rgb;
    }

    // ------------------------------------------------------------------
    // 提示与创造模式变体
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        double size = size(stack);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.elemental_crystal.size", (int) size).withStyle(ChatFormatting.GRAY));
        for (Element element : Element.values()) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.elemental_crystal." + element.getSerializedName(),
                    (int) element(stack, element)).withStyle(ELEMENT_FORMATS[element.ordinal()]));
        }
        if (size >= IElementalCrystal.MAX_SIZE) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.elemental_crystal.grown").withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * 原作 getSubBlocks：一个不带数据的，再按大小 0 / 25 / 50 / 75 / 100 各放 6 个（四个单元素 100、四元素全 100、全 0）。
     * 注意：目前创造模式的「方块」页只放方块本身，不展开方块物品的变体（报告里提了框架需求）。
     */
    @Override
    public List<ItemStack> creativeVariants() {
        List<ItemStack> list = new ArrayList<>();
        list.add(new ItemStack(this));
        for (int i = 0; i < 5; i++) {
            double size = i * 25D;
            for (int j = 0; j < 4; j++) {
                double[] e = new double[4];
                e[j] = IElementalCrystal.MAX_ELEMENT;
                list.add(create(size, e[0], e[1], e[2], e[3]));
            }
            list.add(create(size, 100D, 100D, 100D, 100D));
            list.add(create(size, 0D, 0D, 0D, 0D));
        }
        return list;
    }
}
