package com.flameforge.essentialcraft5.api;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.item.bauble.MysticalAccessoryItem;
import com.flameforge.essentialcraft5.lore.ScrollVolumes;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 玩家的 MRU 易伤系数。对应原作 {@code ECUtils.getGenResistance(index, player)}（L393-435）：
 * <pre>
 * 易伤 = max(0, 1 − 盔甲四件的抗性之和 / 4 − 其它饰品的抗性之和 − min(神秘饰品的抗性之和, 0.4))
 * </pre>
 * 1 = 无防护，0 = 完全免疫。抗性由物品实现 {@link IMRUResistanceItem} 给出；盔甲只看四个盔甲槽，饰品走 Curios（原作 Baubles），
 * 装着的饰品里实现了接口的才算（{@link CuriosCompat#forEachEquipped}，不含装饰槽）。
 * <p>
 * 三种抗性（原作 List 下标 0 / 1 / 2，ApiCore.setItemResistances 的参数名 cResist / rResist / cAffect）：
 * <ul>
 *   <li>{@link Type#MRU_CORRUPTION}（0）：MRU 腐化效果的伤害（原作 PotionMRUCorruption）、MRU 聚集体给的矩阵损伤（EntityMRUPresence）。</li>
 *   <li>{@link Type#CORRUPTION_RADIATION}（1）：站在腐化方块里时叠腐化效果的层数（原作 TileCorruption，每次 5 × 易伤）。
 *       腐化方块不移植，现在没有地方用，留给以后的腐化系统。</li>
 *   <li>{@link Type#HOANNA_RADIATION}（2）：海纳尔维度每 20 tick 增加的辐射（原作 RadiationManager，随机 0~5 + 群系加成，× 易伤）。等海纳尔批次。</li>
 * </ul>
 * 原作提示文字把 1、2 写成「MRU 辐射抗性」「腐化影响」，和实际用途对不上；这里提示按用途写（腐化辐射抗性、海纳尔辐射抗性）。
 * <p>
 * 用法：{@code damage *= MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION);}（伤害、矩阵损伤都在服务端结算，客户端不用调）。
 * 物品一侧实现 {@link IMRUResistanceItem} 即可，提示行由本类的 {@link ResistanceTooltips} 自动加。
 * <p>
 * 和原作的差别：原作同一件物品既在登记表里、又实现接口时会算两次，这里只有接口一种来源；物品返回 NaN / 无穷按 0 算（原作会把 NaN 传进伤害）。
 * <b>神秘饰品</b>（{@link MysticalAccessoryItem}）单独累加、合计夹到 {@link MysticalAccessoryItem#MAX_TOTAL}：原作神秘饰品只抽项链、腰带、
 * 戒指三种形态（ItemBaublesResistance L41 nextInt(3)），Baubles 里能戴它们的只有 4 格（1 项链 + 1 腰带 + 2 戒指），每件每项不到 0.1，
 * 合计最多约 0.4；Curios 的槽位能被别的模组、数据包或指令加多，不设上限的话槽位加到十几个、戴满就接近免疫。
 * 其它饰品（包括别的模组实现本接口的）照旧直接相加。
 * <p>
 * 第十二批（batch12.md §4.1.4）：读完全部源质残卷的玩家，{@link Type#MRU_CORRUPTION} 和 {@link Type#HOANNA_RADIATION} 两项各多
 * {@code general.ancient_scrolls.set_resistance}（默认 0.1），算进「其它饰品」那一项（{@link ScrollVolumes#setResistance}）。
 * 只认服务端玩家：集齐是服务端的进度，这个函数也只在服务端结算时调；{@link Type#CORRUPTION_RADIATION} 现在没有用处，不加。
 * <p>
 * 第十三批 13a（batch13.md §3.1.4）：矩阵五阶起 {@link Type#HOANNA_RADIATION} 多 {@value MatrixRank#RADIATION_RESISTANCE}，同样算进「其它饰品」那一项。
 * <p>
 * 第十三批 13b（batch13.md §4.4）：公式算完以后再乘各个登记的倍率（{@link #registerMultiplier}；虚空套装全套 2 阶起把海纳尔辐射再 ×0.5）。
 * 「再减一半」写成倍率而不是往抗性里加：加法里别的来源一叠就会夹到 0，乘在最后时，原本不是 0 的易伤乘完也不是 0。
 * 只对玩家那个重载生效；按数算的两个静态公式不乘。
 */
public final class MRUResistance {

    public enum Type {
        MRU_CORRUPTION("mru_corruption"),
        CORRUPTION_RADIATION("corruption_radiation"),
        HOANNA_RADIATION("hoanna_radiation");

        private final String name;

        Type(String name) {
            this.name = name;
        }

        /** 小写名：提示翻译键 tooltip.essentialcraft5.resistance.&lt;名&gt; 的后缀 */
        public String getName() {
            return name;
        }

        public String tooltipKey() {
            return "tooltip.essentialcraft5.resistance." + name;
        }
    }

    /** 一个易伤倍率来源：这名玩家对这种易伤要再乘几（不管就返回 1；负数、NaN、无穷按 1） */
    @FunctionalInterface
    public interface Multiplier {
        float multiplier(Player player, Type type);
    }

    private static final List<Multiplier> MULTIPLIERS = new CopyOnWriteArrayList<>();

    private MRUResistance() {
    }

    /** 登记一个易伤倍率来源（mod 构造期调；同一个对象登记两次只算一次） */
    public static void registerMultiplier(Multiplier multiplier) {
        if (multiplier != null && !MULTIPLIERS.contains(multiplier)) {
            MULTIPLIERS.add(multiplier);
        }
    }

    /** 玩家对 type 的易伤系数，[0, +∞)（有负抗性的物品时可以大于 1）。 */
    public static float vulnerability(Player player, Type type) {
        float value = additiveVulnerability(player, type);
        for (Multiplier multiplier : MULTIPLIERS) {
            float factor = multiplier.multiplier(player, type);
            if (Float.isFinite(factor) && factor >= 0F) {
                value *= factor;
            }
        }
        return value;
    }

    /** 不乘倍率时的易伤：盔甲、饰品、古卷、矩阵阶按原作公式相加 */
    private static float additiveVulnerability(Player player, Type type) {
        float armor = 0F;
        for (ItemStack stack : player.getArmorSlots()) {
            armor += resistance(stack, type);
        }
        // [0] 其它饰品，[1] 神秘饰品（合计有上限，分开加）
        float[] curios = {0F, 0F};
        CuriosCompat.forEachEquipped(player, stack -> curios[stack.getItem() instanceof MysticalAccessoryItem ? 1 : 0] += resistance(stack, type));
        if (type != Type.CORRUPTION_RADIATION && player instanceof ServerPlayer server) {
            curios[0] += ScrollVolumes.setResistance(server);
        }
        if (type == Type.HOANNA_RADIATION && MatrixRanks.get(player) >= MatrixRank.RADIATION_RANK) {
            curios[0] += MatrixRank.RADIATION_RESISTANCE;
        }
        return vulnerability(armor, curios[0], curios[1]);
    }

    /** 公式本身：max(0, 1 − 盔甲抗性之和 / 4 − 饰品抗性之和)。拆出来方便测试和手册核对数值 */
    public static float vulnerability(float armorSum, float curioSum) {
        float value = 1F - armorSum / 4F - curioSum;
        return Float.isNaN(value) ? 1F : Math.max(0F, value);
    }

    /** 带神秘饰品的公式：神秘饰品的抗性之和先夹到 {@link MysticalAccessoryItem#MAX_TOTAL}，再和其它饰品相加 */
    public static float vulnerability(float armorSum, float curioSum, float mysticalSum) {
        return vulnerability(armorSum, curioSum + Math.min(mysticalSum, MysticalAccessoryItem.MAX_TOTAL));
    }

    /** 一件物品对 type 的抗性：没实现 {@link IMRUResistanceItem} 的是 0 */
    public static float resistance(ItemStack stack, Type type) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IMRUResistanceItem item)) {
            return 0F;
        }
        float value = item.getResistance(stack, type);
        return Float.isFinite(value) ? value : 0F;
    }

    /**
     * 物品提示里的抗性行（原作 PlayerTracker.onDescrAdded：金色数值 + 深紫色说明，三行都显示）。
     * 这里只显示不为 0 的项；百分数保留一位小数（0.125 → 12.5%，原作取整成 12%）。
     */
    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        for (Type type : Type.values()) {
            float value = resistance(stack, type);
            if (value != 0F) {
                String percent = formatPercent(value);
                tooltip.add(Component.translatable(type.tooltipKey(),
                        Component.literal(percent).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
    }

    /** 0.125 → "+12.5"，−0.1 → "-10"（翻译文本里自己带 %） */
    public static String formatPercent(float value) {
        float percent = Math.round(value * 1000F) / 10F;
        String text = percent == (int) percent ? Integer.toString((int) percent) : Float.toString(percent);
        return percent >= 0F ? "+" + text : text;
    }

    /** 给所有实现了 {@link IMRUResistanceItem} 的物品自动加提示行（只在客户端注册；事件类本身双端都有，不引用客户端类） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class ResistanceTooltips {

        private ResistanceTooltips() {
        }

        @SubscribeEvent
        public static void onTooltip(ItemTooltipEvent event) {
            // 同调装备没按住 Shift 时，抗性行和物品说明一起收起
            if (event.getItemStack().getItem() instanceof IMRUResistanceItem
                    && !com.flameforge.essentialcraft5.client.AttunementTooltips.holdsBackNotes(event)) {
                appendTooltip(event.getItemStack(), event.getToolTip());
            }
        }
    }
}
