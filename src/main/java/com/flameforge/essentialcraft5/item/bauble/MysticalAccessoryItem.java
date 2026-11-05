package com.flameforge.essentialcraft5.item.bauble;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IMRUResistanceItem;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 神秘饰品（原作 ItemBaublesResistance，注册名 bauble → mystical_accessory；第六批 6a M7）：海纳尔遗迹箱子里开出来的随机饰品，
 * 形态（项链 / 腰带 / 戒指）、外观（底 6 种 × 面 6 种）和三项 MRU 抗性都是随机的。
 * <p>
 * NBT（键名照原作）：{@value #TAG_TYPE} 形态 0~2（0 项链、1 腰带、2 戒指，原作 AMULET / BELT / RING）、{@value #TAG_BOTTOM} / {@value #TAG_TOP} 外观 0~5、
 * {@value #TAG_MRU_CORRUPTION} / {@value #TAG_CORRUPTION_RADIATION} / {@value #TAG_HOANNA_RADIATION} 三项抗性，各是 [0, 0.1) 的小数（原作 {@code nextFloat() / 10}）。
 * 三项按用途接到 {@link MRUResistance.Type}：MRU 腐化抗性、腐化辐射抗性（腐化方块没移植，现在没有用处）、<b>海纳尔辐射抗性</b>。
 * 原作提示把后两项写成「MRU 辐射抗性」「腐化影响」，和原作代码里的实际用途（ECUtils.getGenResistance 下标 1 / 2）对不上；提示行由框架
 * {@link MRUResistance.ResistanceTooltips} 按用途自动加，这里不写。
 * <p>
 * 和原作的差别：
 * <ul>
 *   <li><b>开箱时就定属性</b>（已定，batch6a §11）：战利品函数 {@code essentialcraft5:randomize_accessory}（{@code loot/RandomizeAccessoryFunction}）在箱子生成物品时就抽。
 *       原作第一次右键才抽，从箱子里拿出来直接放进饰品栏的是没有形态、没有抗性的空饰品（原作 getBaubleType 退回 TRINKET，1.12 Baubles 的任意槽）。
 *       还没抽过的（创造栏、指令给的）放进背包的下一刻就在服务端抽（{@link #inventoryTick}），右键也行。</li>
 *   <li>只能戴进形态对应的那种 Curios 槽（{@link #canEquip}）。物品进了 curios:necklace / belt / ring 三个标签（Curios 要靠标签决定槽位界面放不放行），
 *       没抽过形态的哪都戴不上。</li>
 *   <li>抗性读出来夹在 [0, {@value #MAX_RESISTANCE}]：原作按 NBT 原样加，指令改 NBT 能写出免疫或负数。</li>
 *   <li>不受「同种只生效一件」限制（照原作：几件神秘饰品的抗性相加；默认 1 项链 + 1 腰带 + 2 戒指（戒指的 2 格是本模组 data/essentialcraft5/curios/slots/ring.json 给的），最多约 +40%，和原作一样：原作只抽这三种形态（ItemBaublesResistance L41 nextInt(3)），Baubles 里能戴它们的也是这 4 格）。
 *       每项合计夹到 {@value #MAX_TOTAL}（{@link MRUResistance#vulnerability(Player, MRUResistance.Type)} 里单独累加）：Curios 的槽位能被别的模组、
 *       数据包或指令加多，原作能戴神秘饰品的只有 4 格，不设上限的话槽位加到十几个、戴满就接近免疫海纳尔辐射。
 *       受 {@code general.baubles.disabled} 管：列进去以后抗性全算 0、提示加红字、不从遗迹箱子里开出来（表里带 not_disabled 条件）；不进 {@code ECBaubles.all()}。</li>
 * </ul>
 * 模型：物品属性 {@link #MODEL_PREDICATE} = 形态 × 36 + 底 × 6 + 面（客户端 {@code client/VoidClient} 登记，不夹到 0~1），
 * 108 个两层模型由 ECVoid 的规格生成（照原作物品方块状态 blockstates/item/baubles{amulet,belt,ring}.json）。
 */
public class MysticalAccessoryItem extends Item implements ICurioItem, IMRUResistanceItem {

    public static final String TAG_TYPE = "type";
    public static final String TAG_BOTTOM = "b";
    public static final String TAG_TOP = "t";
    public static final String TAG_MRU_CORRUPTION = "mrucr";
    public static final String TAG_CORRUPTION_RADIATION = "mrurr";
    public static final String TAG_HOANNA_RADIATION = "car";

    /** 形态数（项链、腰带、戒指） */
    public static final int TYPES = 3;
    /** 底、面各几种外观 */
    public static final int STYLES = 6;
    /** 每项抗性的上限（原作 nextFloat() / 10，取不到 0.1） */
    public static final float MAX_RESISTANCE = 0.1F;
    /** 戴着的所有神秘饰品每项抗性合计的上限（原作能戴神秘饰品的 4 格 × {@value #MAX_RESISTANCE}） */
    public static final float MAX_TOTAL = 0.4F;
    /** 形态 → 贴图里的名字（item/baubles/bauble_&lt;名&gt;_&lt;底&gt;.png） */
    public static final String[] KINDS = {"amulet", "belt", "ring"};
    /** 形态 → Curios 槽位 id */
    private static final String[] SLOTS = {CuriosCompat.NECKLACE, CuriosCompat.BELT, CuriosCompat.RING};

    /** 物品模型属性：形态 × 36 + 底 × 6 + 面（0~107），没抽过形态的是 0 */
    public static final ResourceLocation MODEL_PREDICATE = EssentialCraft5.id("accessory");

    public static final String UNIDENTIFIED_TOOLTIP = "tooltip.essentialcraft5.mystical_accessory.unidentified";

    public MysticalAccessoryItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- 属性

    /** 抽过形态了 */
    public static boolean isIdentified(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_TYPE, Tag.TAG_ANY_NUMERIC) && validType(tag.getInt(TAG_TYPE));
    }

    /** 形态 0~2；没抽过返回 -1 */
    public static int type(ItemStack stack) {
        return isIdentified(stack) ? stack.getTag().getInt(TAG_TYPE) : -1;
    }

    /** 能戴进的 Curios 槽位 id；没抽过返回 null */
    @Nullable
    public static String slot(ItemStack stack) {
        int type = type(stack);
        return type < 0 ? null : SLOTS[type];
    }

    /** 底 / 面外观 0~5（越界的按 0） */
    public static int style(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        int value = tag == null ? 0 : tag.getInt(key);
        return value >= 0 && value < STYLES ? value : 0;
    }

    /** 某一项抗性（读 NBT，夹到 [0, MAX_RESISTANCE]；没抽过是 0） */
    public static float storedResistance(ItemStack stack, MRUResistance.Type type) {
        if (!isIdentified(stack)) {
            return 0F;
        }
        float value = stack.getTag().getFloat(key(type));
        return Float.isFinite(value) ? Mth.clamp(value, 0F, MAX_RESISTANCE) : 0F;
    }

    public static String key(MRUResistance.Type type) {
        return switch (type) {
            case MRU_CORRUPTION -> TAG_MRU_CORRUPTION;
            case CORRUPTION_RADIATION -> TAG_CORRUPTION_RADIATION;
            case HOANNA_RADIATION -> TAG_HOANNA_RADIATION;
        };
    }

    /**
     * 抽属性（原作 initRandomTag 的顺序与分布：形态 nextInt(3)、底 nextInt(6)、面 nextInt(6)、三项抗性各 nextFloat() / 10）。
     * 已经抽过的也会重抽——调用方自己判断（战利品函数只对刚生成的新物品调；{@link #inventoryTick} / {@link #use} 只对没抽过的调）。
     */
    public static void roll(ItemStack stack, RandomSource random) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_TYPE, random.nextInt(TYPES));
        tag.putInt(TAG_BOTTOM, random.nextInt(STYLES));
        tag.putInt(TAG_TOP, random.nextInt(STYLES));
        tag.putFloat(TAG_MRU_CORRUPTION, random.nextFloat() / 10F);
        tag.putFloat(TAG_CORRUPTION_RADIATION, random.nextFloat() / 10F);
        tag.putFloat(TAG_HOANNA_RADIATION, random.nextFloat() / 10F);
    }

    /** 模型属性值：形态 × 36 + 底 × 6 + 面（没抽过的按项链 0 / 0） */
    public static float modelIndex(ItemStack stack) {
        int type = type(stack);
        return type < 0 ? 0F : modelIndex(type, style(stack, TAG_BOTTOM), style(stack, TAG_TOP));
    }

    public static float modelIndex(int type, int bottom, int top) {
        return type * STYLES * STYLES + bottom * STYLES + top;
    }

    private static boolean validType(int type) {
        return type >= 0 && type < TYPES;
    }

    // ---------------------------------------------------------------- 没抽过的：进背包就抽、右键也抽

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && !isIdentified(stack)) {
            roll(stack, level.random);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isIdentified(stack)) {
            if (!level.isClientSide) {
                roll(stack, level.random);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    // ---------------------------------------------------------------- Curios

    /** 只能戴进形态对应的那种槽；没抽过的哪都戴不上 */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        String slot = slot(stack);
        return slot != null && slot.equals(slotContext.identifier());
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return isIdentified(stack);
    }

    /** Curios 按标签会列出三种槽；抽过形态的只写它那一种（格式同 Curios 自己的「槽位：」行） */
    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, ItemStack stack) {
        String slot = slot(stack);
        if (slot == null) {
            return tooltips;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("curios.tooltip.slot").append(" ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("curios.identifier." + slot).withStyle(ChatFormatting.YELLOW)));
        return lines;
    }

    // ---------------------------------------------------------------- MRU 抗性

    /** 被 {@code general.baubles.disabled} 禁用时全算 0（{@code MRUResistance.vulnerability} 走的是不看禁用的 forEachEquipped） */
    @Override
    public float getResistance(ItemStack stack, MRUResistance.Type type) {
        return CuriosCompat.isDisabled(stack) ? 0F : storedResistance(stack, type);
    }

    // ---------------------------------------------------------------- 提示

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (!isIdentified(stack)) {
            tooltip.add(Component.translatable(UNIDENTIFIED_TOOLTIP).withStyle(ChatFormatting.GRAY));
        }
        if (CuriosCompat.isDisabled(stack)) {
            tooltip.add(CuriosCompat.disabledTooltip());
        }
    }
}
