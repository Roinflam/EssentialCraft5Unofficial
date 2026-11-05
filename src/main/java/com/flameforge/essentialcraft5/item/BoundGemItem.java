package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.IBoundGemTarget;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 绑定宝石：右键方块记下坐标，放进消费端机器的 0 号槽，机器就从那个坐标的 MRU 源头拉 MRU（见 MRULink）。
 * 对应原作 ItemBoundGem。
 * <p>
 * NBT：pos（int[3]，同原作）、dim（维度 id 字符串，原作是 int 维度号）、created。
 * created 的用法照搬原作：普通右键绑定时为 true；潜行时右键绑定为 false。
 * 潜行右键空气：created 为 true 就解除绑定，否则把它置为 true（所以潜行绑定的宝石要潜行右键两次才解绑，防手滑）。
 */
public class BoundGemItem extends Item {

    /** 物品模型切换用的属性（原作 MeshDefinitionBoundGem 的 active=true/false），客户端在 ECClient 注册 */
    public static final ResourceLocation BOUND_PREDICATE = ResourceLocation.fromNamespaceAndPath("essentialcraft5", "bound");

    private static final String TAG_POS = "pos";
    private static final String TAG_DIM = "dim";
    private static final String TAG_CREATED = "created";

    public BoundGemItem(Properties properties) {
        super(properties);
    }

    public static boolean isBound(ItemStack stack) {
        return getTarget(stack) != null;
    }

    @Nullable
    public static GlobalPos getTarget(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_POS, Tag.TAG_INT_ARRAY) || !tag.contains(TAG_DIM, Tag.TAG_STRING)) {
            return null;
        }
        int[] pos = tag.getIntArray(TAG_POS);
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString(TAG_DIM));
        if (pos.length != 3 || dim == null) {
            return null;
        }
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), new BlockPos(pos[0], pos[1], pos[2]));
    }

    public static void bind(ItemStack stack, GlobalPos target, boolean created) {
        CompoundTag tag = stack.getOrCreateTag();
        BlockPos pos = target.pos();
        tag.putIntArray(TAG_POS, new int[] {pos.getX(), pos.getY(), pos.getZ()});
        tag.putString(TAG_DIM, target.dimension().location().toString());
        tag.putBoolean(TAG_CREATED, created);
    }

    private static void unbind(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            // 只删自己的键，不像原作那样整个 NBT 清空（会把改名之类的一起清掉）
            tag.remove(TAG_POS);
            tag.remove(TAG_DIM);
            tag.remove(TAG_CREATED);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
    }

    /**
     * 右键方块绑定。第三批安全回补（policy §5，P1）：原作谁的产能器都能绑，隔 16 格一直抽别人的 MRU；
     * 现在服务端先用使用者的身份问一次领地插件（{@link ECProtection#canEdit}，按换算后存 MRU 的那一格），不能动那一格就不绑、
     * 动作栏提示 {@code message.essentialcraft5.no_permission}；假玩家、没有玩家（发射器之类）一律不绑。已经绑好的宝石不追溯。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (isBound(stack)) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        Player player = context.getPlayer();
        // 原作：点在射线塔上半格时绑到下半格。多格方块实现 IBoundGemTarget 给出存 MRU 的那一格
        BlockPos pos = context.getClickedPos();
        BlockState clicked = level.getBlockState(pos);
        if (clicked.getBlock() instanceof IBoundGemTarget target) {
            pos = target.boundGemTarget(level, pos, clicked);
        }
        if (level.isClientSide) {
            // 客户端不知道领地的结论，照常挥手；服务端被拒时宝石不变，提示走动作栏
            return InteractionResult.SUCCESS;
        }
        if (!ECProtection.isRealPlayer(player)) {
            return InteractionResult.FAIL;
        }
        if (!ECProtection.canEdit(player, level, pos)) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.no_permission").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        ItemStack bound = stack.copyWithCount(1);
        bind(bound, GlobalPos.of(level.dimension(), pos), !player.isShiftKeyDown());
        stack.shrink(1);
        // 不用 Inventory#add：创造模式下背包满时它返回 true 却把物品销毁
        ItemHandlerHelper.giveItemToPlayer(player, bound);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1F, 2F);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player.isShiftKeyDown() && isBound(stack)) {
            CompoundTag tag = stack.getOrCreateTag();
            if (tag.getBoolean(TAG_CREATED)) {
                unbind(stack);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1F, 0.5F);
            } else {
                tag.putBoolean(TAG_CREATED, true);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return isBound(stack) ? Rarity.EPIC : Rarity.COMMON;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos target = getTarget(stack);
        if (target != null) {
            BlockPos pos = target.pos();
            tooltip.add(Component.translatable("tooltip.essentialcraft5.bound_gem.bound").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("x: " + pos.getX()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("y: " + pos.getY()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("z: " + pos.getZ()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.essentialcraft5.dimension",
                    target.dimension().location().toString()).withStyle(ChatFormatting.GRAY));
        }
    }
}
