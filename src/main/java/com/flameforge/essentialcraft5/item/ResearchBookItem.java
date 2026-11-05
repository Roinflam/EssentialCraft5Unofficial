package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.client.guide.GuideClient;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
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
 * 研究手册（原作 ItemKnowledgeBook）：右键打开指南界面，潜行右键查阅准星对着的方块或地上的物品；界面和内容全在客户端（见 client/guide）。
 * 玩家第一次进服时会送一本（见 player/ResearchBookGift，服务端配置 general.give_research_book_on_first_join 可关）。
 * <p>
 * 原作按物品 NBT tier（0~4：基础 / MRU / 工程学 / 海纳尔 / 暗影）分等级，要一级级合成升级才能看到后面的分类。
 * 移植后只有一本书、没有等级：内容是一份按游戏进度排的流程教程，全部可见，做完的步骤按进度打勾（见 GuideUnlocks），
 * 不用再动物品。旧存档里带 tier 的书读进来照样能用（NBT 不再读取）。
 * <p>
 * 潜行右键对着方块时，原版会跳过方块自己的右键（机器不开界面）直接走到这里的 use，所以潜行右键机器查的是机器本身。
 * 服务端这边什么都不做，只返回成功（挥手）。
 */
public class ResearchBookItem extends Item {

    public ResearchBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            boolean sneaking = player.isShiftKeyDown();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GuideClient.useBook(sneaking));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.research_book.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.research_book.sneak").withStyle(ChatFormatting.DARK_GRAY));
        // 和 Screen.hasControlDown 一致：Mac 上是 Cmd（Util 是两端都有的类，不碰客户端的 Minecraft.ON_OSX）
        String ctrl = Util.getPlatform() == Util.OS.OSX ? "Cmd" : "Ctrl";
        tooltip.add(Component.translatable("tooltip.essentialcraft5.research_book.ctrl", ctrl).withStyle(ChatFormatting.DARK_GRAY));
        // 查阅键是客户端的按键绑定，默认不绑定；玩家自己绑了才写出来
        Component key = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> GuideClient::lookupKeyName);
        if (key != null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.research_book.lookup", key).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
