package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.IItemFilter;
import com.flameforge.essentialcraft5.api.ItemFilters;
import com.flameforge.essentialcraft5.client.LogisticsClient;
import com.flameforge.essentialcraft5.menu.FilterMenu;
import com.flameforge.essentialcraft5.menu.HeldItemMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 物品过滤器（白名单 / 黑名单；原作 {@code ItemFilter} meta 0 / 2，原名「魔力仓库节点过滤器」，魔力仓库已去掉，改名「物品过滤器」）。
 * 一张 3×3 的样本表：拿在手上右键打开（{@link FilterMenu}，手持物品菜单 + 幽灵格），装进魔力漏斗（以后还有别的机器）决定处理哪些物品。
 * 数据格式与判定全在框架的 {@link ItemFilters}（别的模块只认 {@link IItemFilter} 和标签 {@code essentialcraft5:filters}，不 import 这个类）。
 * 堆叠 1（原作 {@code ItemsCore} L199）。高级版（原作 meta 1 / 3）多三个开关：忽略耐久、忽略 NBT、按标签（每格选一个标签，见 {@link ItemFilters}），
 * 由普通过滤器加 4 块虚空板升级（保留 NBT 的有序配方，样本和开关原样带过来；原作产物不带 NBT，升级就丢样本）。
 * <p>
 * 修掉的原作问题：格子里放真物品、只在「过滤器是选中物品」时每刻写回 → 用鼠标挪走过滤器再取东西就能刷物品（F-1）；
 * 从副手打开读写的是主手（F-2，吞物品）；能把自己 Shift 进自己的格子（F-3）；每刻写 NBT（F-8）；能装 9 组真物品、无限套娃（F-9）。
 * 现在样本是幽灵格（不消耗、拿不出来），改了立刻写回打开时手上的那一件，界面随物品离手关闭（{@link HeldItemMenu}）。
 */
public class FilterItem extends Item implements IItemFilter {

    private final boolean blacklist;
    private final boolean advanced;

    public FilterItem(Properties properties, boolean blacklist) {
        this(properties, blacklist, false);
    }

    public FilterItem(Properties properties, boolean blacklist, boolean advanced) {
        super(properties);
        this.blacklist = blacklist;
        this.advanced = advanced;
    }

    @Override
    public boolean isBlacklist() {
        return blacklist;
    }

    @Override
    public boolean isAdvanced() {
        return advanced;
    }

    /** 右键打开样本界面（假玩家、手上空着不开）。对方块右键时方块自己的界面优先（魔力漏斗、箱子），要打开过滤器就潜行右键或对着空气 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            HeldItemMenu.open(serverPlayer, hand, stack.getHoverName(), FilterMenu::new);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(blacklist ? "tooltip.essentialcraft5.filter.blacklist" : "tooltip.essentialcraft5.filter.whitelist")
                .withStyle(ChatFormatting.GRAY));
        if (advanced) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.filter.advanced", switchesOn(stack)).withStyle(ChatFormatting.GOLD));
        }
        boolean byTag = advanced && ItemFilters.flag(stack, ItemFilters.Flag.MATCH_TAGS);
        List<ItemStack> all = ItemFilters.samples(stack);
        List<ItemStack> samples = all.stream().filter(sample -> !sample.isEmpty()).toList();
        if (samples.isEmpty()) {
            tooltip.add(Component.translatable(blacklist ? "tooltip.essentialcraft5.filter.empty.blacklist" : "tooltip.essentialcraft5.filter.empty.whitelist")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.filter.samples", samples.size(), ItemFilters.SLOTS)
                    .withStyle(ChatFormatting.GRAY));
            // 按住 Shift 列出样本名字（按键状态只在客户端有；提示框本来也只在客户端画）
            Boolean shift = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> LogisticsClient::hasShiftDown);
            if (Boolean.TRUE.equals(shift)) {
                for (int i = 0; i < all.size(); i++) {
                    ItemStack sample = all.get(i);
                    if (sample.isEmpty()) {
                        continue;
                    }
                    Component name = sample.getHoverName();
                    TagKey<Item> tag = byTag ? ItemFilters.effectiveTag(stack, i) : null;
                    Component line;
                    if (ItemFilters.isFilter(sample)) {
                        line = Component.translatable("tooltip.essentialcraft5.filter.sample.nested", name);
                    } else if (tag != null) {
                        line = Component.translatable("tooltip.essentialcraft5.filter.sample.tag", name, tag.location().toString());
                    } else {
                        line = Component.translatable("tooltip.essentialcraft5.filter.sample", name);
                    }
                    tooltip.add(line.copy().withStyle(ChatFormatting.DARK_AQUA));
                }
            } else {
                tooltip.add(Component.translatable("tooltip.essentialcraft5.filter.shift").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.filter.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 高级过滤器开着的开关，按界面按钮的顺序用顿号连起来；都关着时写「开关都关着」 */
    private static Component switchesOn(ItemStack stack) {
        MutableComponent out = null;
        for (ItemFilters.Flag flag : ItemFilters.Flag.values()) {
            if (!ItemFilters.flag(stack, flag)) {
                continue;
            }
            Component name = Component.translatable("tooltip.essentialcraft5.filter.flag." + flag.key());
            out = out == null ? name.copy() : out.append(Component.translatable("tooltip.essentialcraft5.filter.flag.separator")).append(name);
        }
        return out == null ? Component.translatable("tooltip.essentialcraft5.filter.flag.none") : out;
    }
}
