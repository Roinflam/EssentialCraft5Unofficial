package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.gun.GunMaterials;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * MRU 矩阵投影（空白 / 混沌 / 冰冻 / 魔力 / 暗影）。对应原作 ItemMRUMatrixProjection 的 meta 0~4，1.20.1 拆成 5 个物品。
 * 空白投影由辐射仓用灵魂石做出，再在不同平衡值下辐射成另外三种；冰冻投影是冷凝器的材料。
 * <p>
 * 右键（原作 onItemRightClick，第二批 ECGear 接上「使用」）：
 * <ul>
 *   <li>还没有主人时（且没潜行）：记下右键的玩家为主人。</li>
 *   <li>主人本人右键：举着它 {@value #USE_DURATION} 刻（{@link UseAnim#BLOCK}）。期间每 40 刻响一次传送门音效；剩 {@value #NAUSEA_AT} 刻时反胃 10 秒；
 *       剩 {@value #BLINDNESS_AT} 刻起失明（原作最后 50 刻每刻重给 2000 刻的失明，用完还要瞎约 100 秒；这里只在第 50 刻给一次、时长补足到同一时刻结束，省掉 50 个同步包）。</li>
 *   <li>举满以后：凋零 I、虚弱 V、缓慢 V、挖掘疲劳 V、饥饿 I 各 {@value #AFTERMATH_TICKS} 刻（38 秒），聊天栏提示，
 *       玩家的矩阵类型改成 {@link #getMatrixType()}（空白投影改回中性），投影用掉（创造模式不消耗）。</li>
 *   <li>别人的投影：什么都不做，在动作栏提示一句（原作没有提示）。</li>
 * </ul>
 * 矩阵类型没有任何被动效果，只影响混沌之叉、冰冻重锤这类按矩阵触发特效的武器（研究手册写明了；暗渎三件已去掉）。
 * 用完触发原版 {@code minecraft:consume_item} 进度条件（同药水、牛奶），手册的「矩阵投影的使用」一步靠它打勾。
 * 第十三批 13e：用暗影投影变成暗影属性时另发第二十二章的一步 {@link #GUIDE_SHADE_ATTUNE}；矩阵阶开着、还不到暗影相性那一阶（八阶）时，
 * 聊天栏多一句「属性变了，但暗影相性要八阶才生效」（batch13.md §3.2：七阶以下照旧能变属性）。
 * <p>
 * NBT：owner（主人 UUID）、ownerName（主人名字，tooltip 用）。原作只存 UUID（键名叫 playerName），
 * tooltip 再按 UUID 反查名字，1.20.1 客户端查不到不在线的玩家，所以把名字一起存下来。
 */
public class MatrixProjectionItem extends Item {

    private static final String TAG_OWNER = "owner";

    /** 第二十二章「变成暗影属性」一步（13e，batch13.md §2.2），只由这里发 */
    public static final ResourceLocation GUIDE_SHADE_ATTUNE = EssentialCraft5.id("guide/shade/attune");
    public static final String GUIDE_SHADE_CRITERION = "attuned";
    private static final String TAG_OWNER_NAME = "ownerName";

    /** 举着的时长（原作 getMaxItemUseDuration） */
    public static final int USE_DURATION = 200;
    /** 每隔多少刻响一次传送门音效（按剩余刻数取模，同原作） */
    private static final int SOUND_INTERVAL = 40;
    /** 剩余刻数等于它时给反胃（原作 count == 100，反胃 200 刻） */
    public static final int NAUSEA_AT = 100;
    private static final int NAUSEA_TICKS = 200;
    /** 剩余刻数等于它时给失明（原作 count ≤ 50 时每刻给 2000 刻） */
    public static final int BLINDNESS_AT = 50;
    /** 原作最后一次（剩 1 刻时）给的 2000 刻在第 BLINDNESS_AT 刻看是 2000 + 49 刻 */
    private static final int BLINDNESS_TICKS = 2000 + BLINDNESS_AT - 1;
    /** 用完后几种负面效果的时长（原作 760 刻） */
    public static final int AFTERMATH_TICKS = 760;

    private final int matrixType;

    /**
     * @param matrixType 使用后玩家变成的矩阵类型（原作 meta，也是 IPlayerData#getMatrixType 的序号：0 中性、1 混沌、2 冰冻、3 魔力、4 暗影）
     */
    public MatrixProjectionItem(Properties properties, int matrixType) {
        super(properties);
        this.matrixType = matrixType;
    }

    public int getMatrixType() {
        return matrixType;
    }

    @Nullable
    public static UUID getOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    public static void setOwner(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_OWNER, player.getUUID());
        tag.putString(TAG_OWNER_NAME, player.getGameProfile().getName());
    }

    public static boolean isOwner(ItemStack stack, Player player) {
        return player.getUUID().equals(getOwner(stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        UUID owner = getOwner(stack);
        if (owner == null) {
            if (player.isShiftKeyDown()) {
                return InteractionResultHolder.pass(stack);
            }
            if (!level.isClientSide) {
                setOwner(stack, player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (owner.equals(player.getUUID())) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_projection.not_owner")
                    .withStyle(ChatFormatting.RED), true);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide) {
            return;
        }
        if (remainingUseDuration % SOUND_INTERVAL == 0) {
            playSound(level, user, SoundEvents.PORTAL_TRAVEL, 2F);
            playSound(level, user, SoundEvents.PORTAL_TRIGGER, 2F);
        }
        if (remainingUseDuration == NAUSEA_AT) {
            user.addEffect(effect(MobEffects.CONFUSION, NAUSEA_TICKS, 0));
        }
        if (remainingUseDuration == BLINDNESS_AT) {
            user.addEffect(effect(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0));
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!(user instanceof Player player) || !isOwner(stack, player)) {
            return stack;
        }
        // 原作音调 0.01，1.20 客户端把音调夹到 0.5~2
        playSound(level, player, SoundEvents.PORTAL_TRIGGER, 0.5F);
        if (!level.isClientSide) {
            player.addEffect(effect(MobEffects.WITHER, AFTERMATH_TICKS, 0));
            player.addEffect(effect(MobEffects.WEAKNESS, AFTERMATH_TICKS, 4));
            player.addEffect(effect(MobEffects.MOVEMENT_SLOWDOWN, AFTERMATH_TICKS, 4));
            player.addEffect(effect(MobEffects.DIG_SLOWDOWN, AFTERMATH_TICKS, 4));
            player.addEffect(effect(MobEffects.HUNGER, AFTERMATH_TICKS, 0));
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                data.setMatrixType(matrixType);
            }
            player.sendSystemMessage(Component.translatable("message.essentialcraft5.matrix_projection.used")
                    .withStyle(ChatFormatting.AQUA));
            if (matrixType == MatrixResonance.SHADE && data != null && MatrixRank.enabled()
                    && data.getMatrixRank() < MatrixResonance.SHADE_RANK) {
                player.sendSystemMessage(Component.translatable("message.essentialcraft5.matrix_projection.shade_locked",
                        MatrixRank.rankName(MatrixResonance.SHADE_RANK)).withStyle(ChatFormatting.GRAY));
            }
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
                if (matrixType == MatrixResonance.SHADE) {
                    PlayerPentacleBlockEntity.award(serverPlayer, GUIDE_SHADE_ATTUNE, GUIDE_SHADE_CRITERION);
                }
            }
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    /** 服务端放、所有人都听得到（原作 Entity.playSound） */
    private static void playSound(Level level, LivingEntity user, SoundEvent sound, float pitch) {
        if (!level.isClientSide) {
            level.playSound(null, user.getX(), user.getY(), user.getZ(), sound, SoundSource.PLAYERS, 0.3F, pitch);
        }
    }

    /** 原作 new PotionEffect(效果, 时长, 等级, ambient = true, showParticles = true) */
    private static MobEffectInstance effect(MobEffect effect, int ticks, int amplifier) {
        return new MobEffectInstance(effect, ticks, amplifier, true, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_OWNER_NAME, Tag.TAG_STRING)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_projection.owner",
                            Component.literal(tag.getString(TAG_OWNER_NAME)).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_projection.use").withStyle(ChatFormatting.GRAY));
        } else if (getOwner(stack) == null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_projection.bind").withStyle(ChatFormatting.GRAY));
        }
        // 四种有色投影也是枪的透镜：写上装进透镜格的命中效果（效果见 gun/LensEffects）
        GunMaterials.lensByItem(stack).ifPresent(lens -> tooltip.add(
                Component.translatable("tooltip.essentialcraft5.matrix_projection.lens." + lens.id()).withStyle(ChatFormatting.DARK_PURPLE)));
    }
}
