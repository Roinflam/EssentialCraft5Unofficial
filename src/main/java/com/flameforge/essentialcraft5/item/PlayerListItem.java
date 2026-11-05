package com.flameforge.essentialcraft5.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家白名单。对应原作 ItemPlayerList（74 行）。放进 MRU 特斯拉线圈的 1 号格，名单上的人不挨打、能打开线圈界面（只能看）。
 * <p>
 * 数据：NBT {@code players: [{uuid: int[4], name: "…"}]}，最多 {@link #MAX_ENTRIES} 人。判定只比 UUID（离线模式服务器的 UUID 由名字派生，改名就是另一个人，所以手册只说「按玩家本人认」，不说改名也认得）；
 * 名字只用来显示，加人时写入，不在客户端按 UUID 查名字（原作 {@code MiscUtils.getUsernameFromUUID} 只认得在线的人，不在线的显示不出来）。
 * <p>
 * 用法（原作只能「右键加自己」，加错了删不掉）：
 * <ul>
 *   <li>右键（对着空气或不响应右键的方块）：把自己加进去；已经在名单上就提示。</li>
 *   <li>对着另一个玩家右键：把他加进去。只会让他不挨打，对他没有坏处，所以不用他同意。</li>
 *   <li>潜行右键：把自己删掉。</li>
 *   <li>单独放进工作台：清空（无序配方 {@code tesla/player_list_clear}，产物不带 NBT）。</li>
 * </ul>
 * 安全靠线圈那边：界面只给主人和名单上的人开、名单格只有主人和创造模式的人能拿放、不收漏斗插入——别人加不进自己的名字。
 * <p>
 * 读 NBT 时最多读 {@link #MAX_READ} 条（创造模式能做出带任意 NBT 的物品），线圈按物品实例缓存读出来的 UUID 集合。
 */
public class PlayerListItem extends Item {

    /** 最多登记几个人 */
    public static final int MAX_ENTRIES = 64;
    /** 读 NBT 时最多读几条（手工改过的物品可能远超上限） */
    public static final int MAX_READ = 256;
    /** 名字最多保留几个字符（原版玩家名 16 个字符以内） */
    public static final int MAX_NAME_LENGTH = 32;
    /** 提示行最多列几个名字，其余写「……还有 N 人」 */
    public static final int TOOLTIP_NAMES = 10;

    public static final String TAG_PLAYERS = "players";
    public static final String TAG_UUID = "uuid";
    public static final String TAG_NAME = "name";

    /** 加人的结果 */
    public enum AddResult {
        ADDED, ALREADY_LISTED, FULL
    }

    public PlayerListItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    // 读写（静态，线圈和测试共用）
    // ------------------------------------------------------------------

    /** 名单上的 UUID（按登记顺序，去重）；不是白名单或没有数据返回空集合 */
    public static Set<UUID> uuids(ItemStack stack) {
        ListTag list = players(stack);
        if (list == null) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (int i = 0; i < list.size() && i < MAX_READ; i++) {
            UUID id = readUuid(list.getCompound(i));
            if (id != null) {
                result.add(id);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** 名单上的名字（显示用，和 UUID 同序） */
    public static List<String> names(ItemStack stack) {
        ListTag list = players(stack);
        if (list == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size() && i < MAX_READ; i++) {
            CompoundTag entry = list.getCompound(i);
            if (readUuid(entry) != null) {
                String name = entry.getString(TAG_NAME);
                result.add(name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name);
            }
        }
        return result;
    }

    public static boolean contains(ItemStack stack, UUID id) {
        return uuids(stack).contains(id);
    }

    public static int size(ItemStack stack) {
        return uuids(stack).size();
    }

    /** 把一个人加进名单（只改这一件物品的 NBT） */
    public static AddResult add(ItemStack stack, UUID id, String name) {
        if (!(stack.getItem() instanceof PlayerListItem)) {
            return AddResult.FULL;
        }
        Set<UUID> current = uuids(stack);
        if (current.contains(id)) {
            return AddResult.ALREADY_LISTED;
        }
        if (current.size() >= MAX_ENTRIES) {
            return AddResult.FULL;
        }
        // 在副本上改、整份换上去：线圈按物品实例 + NBT 实例缓存名单，换一个新的 CompoundTag 保证缓存一定失效
        CompoundTag tag = stack.getTag() == null ? new CompoundTag() : stack.getTag().copy();
        ListTag list = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        CompoundTag entry = new CompoundTag();
        entry.put(TAG_UUID, NbtUtils.createUUID(id));
        entry.putString(TAG_NAME, name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name);
        list.add(entry);
        tag.put(TAG_PLAYERS, list);
        stack.setTag(tag);
        return AddResult.ADDED;
    }

    /** 把一个人删掉；不在名单上返回 false。删到空时去掉整个 NBT（和新做的一样，能堆叠比较） */
    public static boolean remove(ItemStack stack, UUID id) {
        ListTag list = players(stack);
        if (list == null) {
            return false;
        }
        ListTag kept = new ListTag();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (id.equals(readUuid(entry))) {
                removed = true;
            } else {
                kept.add(entry.copy());
            }
        }
        if (!removed) {
            return false;
        }
        CompoundTag tag = stack.getOrCreateTag().copy();
        if (kept.isEmpty()) {
            tag.remove(TAG_PLAYERS);
        } else {
            tag.put(TAG_PLAYERS, kept);
        }
        stack.setTag(tag.isEmpty() ? null : tag);
        return true;
    }

    @Nullable
    private static ListTag players(ItemStack stack) {
        if (!(stack.getItem() instanceof PlayerListItem)) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_PLAYERS, Tag.TAG_LIST) ? tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND) : null;
    }

    @Nullable
    private static UUID readUuid(CompoundTag entry) {
        Tag raw = entry.get(TAG_UUID);
        if (raw == null || raw.getId() != Tag.TAG_INT_ARRAY || entry.getIntArray(TAG_UUID).length != 4) {
            return null;
        }
        return NbtUtils.loadUUID(raw);
    }

    // ------------------------------------------------------------------
    // 使用
    // ------------------------------------------------------------------

    /** 右键加自己、潜行右键删自己（只在服务端改，结果写动作栏） */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Component message;
            if (player.isShiftKeyDown()) {
                message = remove(stack, player.getUUID())
                        ? Component.translatable("message.essentialcraft5.player_list.removed_self").withStyle(ChatFormatting.YELLOW)
                        : Component.translatable("message.essentialcraft5.player_list.not_listed").withStyle(ChatFormatting.GRAY);
            } else {
                message = switch (add(stack, player.getUUID(), player.getGameProfile().getName())) {
                    case ADDED -> Component.translatable("message.essentialcraft5.player_list.added_self").withStyle(ChatFormatting.GREEN);
                    case ALREADY_LISTED -> Component.translatable("message.essentialcraft5.player_list.already_self").withStyle(ChatFormatting.GRAY);
                    case FULL -> Component.translatable("message.essentialcraft5.player_list.full", MAX_ENTRIES).withStyle(ChatFormatting.RED);
                };
            }
            player.displayClientMessage(message, true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 对着另一个玩家右键：把他加进去。
     * 改的是手上那一件（{@code player.getItemInHand}）：原版对实体右键时，创造模式传进来的是一份副本（{@code Player.interactOn}），改副本等于没改。
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player other)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof PlayerListItem)) {
            return InteractionResult.PASS;
        }
        String name = other.getGameProfile().getName();
        Component message = switch (add(held, other.getUUID(), name)) {
            case ADDED -> Component.translatable("message.essentialcraft5.player_list.added_other", name).withStyle(ChatFormatting.GREEN);
            case ALREADY_LISTED -> Component.translatable("message.essentialcraft5.player_list.already_other", name).withStyle(ChatFormatting.GRAY);
            case FULL -> Component.translatable("message.essentialcraft5.player_list.full", MAX_ENTRIES).withStyle(ChatFormatting.RED);
        };
        player.displayClientMessage(message, true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        List<String> names = names(stack);
        if (names.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.player_list.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.player_list.count", names.size()).withStyle(ChatFormatting.GRAY));
            for (int i = 0; i < names.size() && i < TOOLTIP_NAMES; i++) {
                tooltip.add(Component.translatable("tooltip.essentialcraft5.player_list.entry", names.get(i)).withStyle(ChatFormatting.DARK_AQUA));
            }
            if (names.size() > TOOLTIP_NAMES) {
                tooltip.add(Component.translatable("tooltip.essentialcraft5.player_list.more", names.size() - TOOLTIP_NAMES)
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        for (int i = 0; i < 4; i++) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.player_list.usage." + i).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
