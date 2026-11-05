package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 聚集体收容瓶（EC5 新增，原作没有）：把一整团 MRU 聚集体收进瓶里带走，再放到别处。
 * <p>
 * 原作的聚集体继承 EntityLivingBase，整合包里抓生物的道具能把它抓走搬家；移植后它是普通实体（不受重力、不能被攻击，
 * 见 {@link MRUPresenceEntity}），那些道具认不出来，只剩移动魔杖一格一格地拉。这个瓶子补上「整团搬走」。
 * <ul>
 *   <li><b>收</b>：空瓶对着视线前方 {@value #REACH} 格内的聚集体按住右键 {@value #USE_TICKS} 刻（找目标同移动魔杖 {@link MRUMoverItem#findTarget}），
 *       松手前一直对着它才算。要付这团聚集体存量 {@code general.mrucu_container.capture_cost_percent}%（默认 10%，向上取整）的 MRU，
 *       从身上的 MRU 储存物品里扣（{@link MRUDrain}，创造模式不扣）。收走以后瓶里记下 MRU、平衡值和「归零后是否保留」，世界里那团消失。</li>
 *   <li><b>放</b>：装着的瓶子右键方块，聚集体出现在点中那一面外侧那格的正中，瓶子变回空的。不花 MRU。
 *       放出来的聚集体第一轮结算照常跳过（{@link MRUPresenceEntity#create} 的 flag），刚放下不会马上伤人。</li>
 *   <li><b>权限</b>同移动魔杖（{@link MRUMoverItem#mayMove}、{@link MRUMoverItem#canMoveTo}）：只有真玩家能用；收的时候聚集体那一格要是你能动的，
 *       放的时候目的格要是你能动的、在世界边界内、不往出生点保护范围里放（管理员除外）。原作能把聚集体推进别人家，这里不行。</li>
 *   <li><b>倒掉</b>：装着的瓶子对着空处（不是方块）按住右键 {@value #DISPERSE_TICKS} 刻，瓶里那团散掉，瓶子变回空的，
 *       已付的收取费不退。是消掉一团用不着、又不想留在领地里的聚集体的办法（别的办法是富集仓里坍缩，见手册）。</li>
 *   <li>瓶子装着的时候聚集体是封住的，不伤人、不吸泄漏的 MRU、不合并。瓶子不怕火（扔进岩浆不会连带把聚集体烧掉）。</li>
 * </ul>
 * 一瓶只装一团；装着的时候不能再收。
 */
public class MRUCUContainerItem extends Item {

    /** 物品 NBT：装着的聚集体（没有这个键 = 空瓶） */
    public static final String TAG = "mrucu";
    private static final String TAG_MRU = "mru";
    private static final String TAG_BALANCE = "balance";
    private static final String TAG_STAY = "stay";
    /** 模型切换「装着」的物品属性 */
    public static final ResourceLocation FILLED_PREDICATE = EssentialCraft5.id("filled");
    /** 能收多远的聚集体（格） */
    public static final double REACH = 6D;
    /** 收一团要按住多少刻 */
    public static final int USE_TICKS = 40;
    /** 倒掉一瓶要按住多少刻 */
    public static final int DISPERSE_TICKS = 60;

    /** SERVER 配置 general.mrucu_container */
    public static final class Config {
        public static final int DEFAULT_COST_PERCENT = 10;
        public static ForgeConfigSpec.IntValue costPercent;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("MRUCU Containment Flask: picks up a whole MRUCU and puts it down elsewhere.",
                    "聚集体收容瓶：把一整团聚集体收进瓶里、放到别处").push("mrucu_container");
            costPercent = builder.comment("MRU paid to capture an MRUCU, as a percent of the MRU it holds (rounded up; taken from the player's MRU storages).",
                            "收一团聚集体要付它存量的百分之几的 MRU（向上取整，从身上的 MRU 储存物品里扣）")
                    .defineInRange("capture_cost_percent", DEFAULT_COST_PERCENT, 0, 100);
            builder.pop();
        }

        public static int costPercent() {
            return costPercent == null ? DEFAULT_COST_PERCENT : ECConfig.get(costPercent);
        }
    }

    /** 瓶里装着的那一团 */
    public record Contents(int mru, float balance, boolean alwaysStay) {
    }

    public MRUCUContainerItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ 数据

    @Nullable
    public static Contents contents(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag data = tag.getCompound(TAG);
        return new Contents(Math.max(0, data.getInt(TAG_MRU)), data.getFloat(TAG_BALANCE), data.getBoolean(TAG_STAY));
    }

    public static boolean isFilled(ItemStack stack) {
        return contents(stack) != null;
    }

    public static void setContents(ItemStack stack, @Nullable Contents contents) {
        if (contents == null) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(TAG);
                if (tag.isEmpty()) {
                    stack.setTag(null);
                }
            }
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt(TAG_MRU, contents.mru());
        data.putFloat(TAG_BALANCE, contents.balance());
        data.putBoolean(TAG_STAY, contents.alwaysStay());
        stack.getOrCreateTag().put(TAG, data);
    }

    /** 收这团聚集体要付多少 MRU：存量 × 百分比，向上取整 */
    public static int captureCost(int mru) {
        return (int) Math.ceil(mru * Config.costPercent() / 100D);
    }

    // ------------------------------------------------------------------ 收

    /** 视线前方 {@value #REACH} 格内最近的聚集体（同移动魔杖的找法，再限距离），没有返回 null */
    @Nullable
    public static MRUPresenceEntity target(Level level, Player player) {
        MRUPresenceEntity found = MRUMoverItem.findTarget(level, player);
        return found != null && found.isAlive() && found.distanceTo(player) <= REACH ? found : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isFilled(stack)) {
            // 对着空处按住：倒掉；对着方块的右键由 useOn 放出来
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (target(level, player) == null) {
            notice(player, Component.translatable("message.essentialcraft5.mrucu_container.no_target", (int) REACH));
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return isFilled(stack) ? DISPERSE_TICKS : USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /** 收的过程中：客户端在聚集体和瓶口之间冒点粒子；对不准了就停 */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (isFilled(stack)) {
            if (level.isClientSide && remaining % 3 == 0) {
                Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(0.6D)).subtract(0D, 0.3D, 0D);
                level.addParticle(ParticleTypes.WITCH, mouth.x, mouth.y, mouth.z,
                        (level.random.nextDouble() - 0.5D) * 0.1D, -0.05D, (level.random.nextDouble() - 0.5D) * 0.1D);
            }
            return;
        }
        MRUPresenceEntity target = target(level, player);
        if (target == null) {
            player.stopUsingItem();
            return;
        }
        if (level.isClientSide && remaining % 3 == 0) {
            Vec3 from = target.position();
            Vec3 to = player.getEyePosition().add(player.getLookAngle().scale(0.6D)).subtract(0D, 0.3D, 0D);
            double t = level.random.nextDouble();
            Vec3 at = from.lerp(to, t);
            level.addParticle(ParticleTypes.WITCH, at.x, at.y, at.z, 0D, 0D, 0D);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return stack;
        }
        if (isFilled(stack)) {
            disperse(stack, level, player);
        } else {
            capture(stack, level, player);
        }
        return stack;
    }

    /**
     * 倒掉瓶里的聚集体（服务端；按住用完时调，GameTest 直接调）。里面的 MRU 就此散掉，不退收取费。
     *
     * @return 倒掉了（瓶子本来是空的返回 false）
     */
    public static boolean disperse(ItemStack stack, Level level, Player player) {
        Contents contents = contents(stack);
        if (contents == null) {
            return false;
        }
        setContents(stack, null);
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.6D)).subtract(0D, 0.3D, 0D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 24, 0.3D, 0.3D, 0.3D, 0.05D);
        }
        notice(player, Component.translatable("message.essentialcraft5.mrucu_container.dispersed", contents.mru()));
        return true;
    }

    /**
     * 收走玩家面前那团聚集体（服务端；按住用完时调，GameTest 直接调）。
     *
     * @return 收成了
     */
    public static boolean capture(ItemStack stack, Level level, Player player) {
        if (isFilled(stack)) {
            notice(player, Component.translatable("message.essentialcraft5.mrucu_container.full"));
            return false;
        }
        MRUPresenceEntity target = target(level, player);
        if (target == null) {
            notice(player, Component.translatable("message.essentialcraft5.mrucu_container.no_target", (int) REACH));
            return false;
        }
        Vec3 at = target.position();
        if (!MRUMoverItem.mayMove(level, player, at, at)) {
            notice(player, Component.translatable("message.essentialcraft5.mrucu_container.denied"));
            return false;
        }
        int mru = target.getMRU();
        int cost = captureCost(mru);
        if (!MRUDrain.playerUseMRU(player, stack, cost)) {
            notice(player, Component.translatable("message.essentialcraft5.mrucu_container.no_mru", cost));
            return false;
        }
        setContents(stack, new Contents(mru, target.getBalance(), target.isAlwaysStay()));
        target.discard();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.PLAYERS, 1F, 0.8F);
        notice(player, Component.translatable("message.essentialcraft5.mrucu_container.captured", mru));
        return true;
    }

    // ------------------------------------------------------------------ 放

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!isFilled(stack)) {
            // 空瓶点方块：交给 use()，对着聚集体就开始收
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        return release(stack, level, player, Vec3.atCenterOf(pos)) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /**
     * 把瓶里的聚集体放到 at（服务端；GameTest 直接调）。
     *
     * @return 放成了
     */
    public static boolean release(ItemStack stack, Level level, Player player, Vec3 at) {
        Contents contents = contents(stack);
        if (contents == null) {
            return false;
        }
        if (!MRUMoverItem.canMoveTo(level, player, player.position(), at) || !MRUMoverItem.mayMove(level, player, at, at)) {
            notice(player, Component.translatable("message.essentialcraft5.mrucu_container.cannot_release"));
            return false;
        }
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, at.x, at.y, at.z, contents.mru(), contents.balance(), true, contents.alwaysStay());
        if (!level.addFreshEntity(presence)) {
            notice(player, Component.translatable("message.essentialcraft5.mrucu_container.cannot_release"));
            return false;
        }
        setContents(stack, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1F, 0.8F);
        notice(player, Component.translatable("message.essentialcraft5.mrucu_container.released", contents.mru()));
        return true;
    }

    // ------------------------------------------------------------------ 外观

    private static void notice(Player player, Component message) {
        if (!player.level().isClientSide) {
            player.displayClientMessage(message, true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Contents contents = contents(stack);
        if (contents == null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mrucu_container.empty", (int) REACH, USE_TICKS / 20,
                    Config.costPercent()).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.mrucu_container.filled", contents.mru(),
                String.format(java.util.Locale.ROOT, "%.2f", Mth.clamp(contents.balance(), 0F, 2F))).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.mrucu_container.release").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.mrucu_container.disperse", DISPERSE_TICKS / 20).withStyle(ChatFormatting.DARK_GRAY));
    }
}
