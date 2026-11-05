package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECGuide;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 研究手册的客户端接线（模组总线）：查阅键和提示框里「按住 Ctrl 查看」那一行的绘制；另外放几个打开手册的入口。
 * 游戏内事件在 {@link GuideEvents}（查阅键、配方同步后的检查）和 {@link GuideLookup}（按住 Ctrl 查阅），
 * 资源重载（读手册 JSON）在 {@link GuideReloadListener}。这几个类都自带订阅注解，不用在别处接线。
 * <p>
 * 查阅都走全局共用的 {@link GuideView#current()}（物品 → 条目 + 块的索引），打开时走短开场、直接翻到那一块。
 * <p>
 * 查阅的三种方式（都要求身上带着研究手册）：
 * <ol>
 *   <li>界面里指着物品按住 Ctrl（Mac 上 Cmd），见 {@link GuideLookup}；</li>
 *   <li>手持研究手册潜行右键：对着方块查方块，对着地上的物品查物品（{@link #useBook}）；</li>
 *   <li>查阅键，<b>默认不绑定</b>，想用的玩家自己去按键设置里绑。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuideClient {

    public static final String KEY_CATEGORY = "key.categories." + EssentialCraft5.MODID;

    /**
     * 查阅键：手持研究手册对着方块或地上的物品按，或在物品栏里指着物品按，直接翻到介绍它的那一页。
     * <p>
     * <b>默认不绑定</b>（2026-09-24 定）。原作研究书用 R，可 R 在常见整合包里已经有换弹、配方查看等 8 个绑定，
     * 换成哪个键都难保不撞；日常查阅改由「界面里按住 Ctrl」（{@link GuideLookup}）和「手持手册潜行右键」（{@link #useBook}）承担，
     * 这两种都不占按键。键位本身留着，习惯用键的玩家自己绑一个。冲突上下文用 UNIVERSAL，界面里也能用。
     */
    public static final KeyMapping LOOKUP = new KeyMapping("key.essentialcraft5.guide_lookup", KeyConflictContext.UNIVERSAL,
            InputConstants.UNKNOWN, KEY_CATEGORY);

    /** 查阅时往视线前方找多远（格） */
    private static final double REACH = 4.5D;

    private GuideClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(LOOKUP);
    }

    @SubscribeEvent
    public static void registerTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(GuideLookup.Hint.class, GuideLookup.HintRenderer::new);
    }

    // ================================================================ 入口

    /** 打开手册，回到上次看的位置（完整开场） */
    public static void openBook() {
        GuideScreenOpener.open(null);
    }

    /**
     * 研究手册右键（ResearchBookItem#use 在客户端调）：平时打开指南；潜行时查阅准星对着的方块或地上的物品，什么都没对着就打开指南。
     * 潜行右键方块时，原版会跳过方块自己的右键（机器不会开界面），直接走到物品的使用，所以对着机器也能查。
     */
    public static void useBook(boolean sneaking) {
        Minecraft minecraft = Minecraft.getInstance();
        if (sneaking && minecraft.player != null && minecraft.level != null) {
            lookupInWorld(minecraft);
        } else {
            openBook();
        }
    }

    /** 研究手册提示里显示的查阅键名；没绑定时返回 null（提示里就不写这一行） */
    @Nullable
    public static Component lookupKeyName() {
        return LOOKUP.isUnbound() ? null : LOOKUP.getTranslatedKeyMessage();
    }

    /**
     * 查阅：打开介绍这个物品的条目。
     *
     * @param parent 关掉指南后回到的界面（从物品栏查阅时就是物品栏），可为 null
     * @return 是否找到并打开了
     */
    public static boolean lookup(ItemStack stack, @Nullable Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (stack.isEmpty()) {
            return false;
        }
        GuideView view = GuideView.current();
        GuideView.Target target = view.find(stack.getItem());
        GuideView.EntryView entry = target == null ? null : view.entries.get(target.entryKey());
        if (entry == null) {
            return false;
        }
        ResourceLocation locked = view.lockedBy(entry);
        if (locked != null) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(view.lockHint(entry, locked).copy().withStyle(ChatFormatting.GRAY), true);
            }
            return true;
        }
        GuideScreenOpener.openAt(parent, target.entryKey(), target.block());
        return true;
    }

    /** 身上（背包、快捷栏、副手）有没有研究手册 */
    static boolean hasBook(LocalPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ECGuide.RESEARCH_BOOK.get())) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ECGuide.RESEARCH_BOOK.get())) {
                return true;
            }
        }
        return false;
    }

    static boolean holdsBook(LocalPlayer player) {
        return player.getMainHandItem().is(ECGuide.RESEARCH_BOOK.get()) || player.getOffhandItem().is(ECGuide.RESEARCH_BOOK.get());
    }

    /**
     * 查阅世界里的东西（手持研究手册潜行右键、或按查阅键）：查视线里最近的东西——地上的物品或方块
     * （原作 PlayerTickHandler.client_manageBook）。什么都没对着就直接打开书。
     */
    static void lookupInWorld(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !holdsBook(player)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1F).scale(REACH));

        double blockDistance = Double.MAX_VALUE;
        ItemStack blockStack = ItemStack.EMPTY;
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
            // 用「选取方块」拿到的物品：多格方块（射线塔上半格）也能对上正确的物品
            blockStack = state.getCloneItemStack(blockHit, minecraft.level, blockHit.getBlockPos(), player);
            blockDistance = eye.distanceToSqr(hit.getLocation());
        }

        // 地上的物品实体不参与准星判定，自己沿视线找；比方块近才算
        ItemStack itemStack = ItemStack.EMPTY;
        double itemDistance = Double.MAX_VALUE;
        AABB area = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1D);
        for (ItemEntity item : minecraft.level.getEntitiesOfClass(ItemEntity.class, area)) {
            Optional<Vec3> clip = item.getBoundingBox().inflate(0.25D).clip(eye, end);
            if (clip.isPresent()) {
                double distance = eye.distanceToSqr(clip.get());
                if (distance < itemDistance) {
                    itemDistance = distance;
                    itemStack = item.getItem();
                }
            }
        }

        if (!itemStack.isEmpty() && itemDistance < blockDistance && lookup(itemStack, null)) {
            return;
        }
        if (!blockStack.isEmpty() && lookup(blockStack, null)) {
            return;
        }
        if (!itemStack.isEmpty() || !blockStack.isEmpty()) {
            ItemStack shown = !itemStack.isEmpty() && itemDistance < blockDistance ? itemStack : blockStack.isEmpty() ? itemStack : blockStack;
            player.displayClientMessage(Component.translatable("guide.essentialcraft5.lookup.none", shown.getHoverName())
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // 准星什么都没对着：直接打开手册（潜行右键 / 查阅键打开的，同样走短开场）
        GuideScreenOpener.openQuick(null);
    }
}
