package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.InquisitoriumBeaconBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.InquisitoriumSentinelEntity;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.item.WindHornItem;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * 审判庭信标（{@code inquisitorium_beacon}，第十批 10c，batch10.md §5.10.4）：半格高的台子，记着做它时用的审判庭坐标（{@link InquisitoriumBeaconBlockEntity}）。
 * 放在坐标附近右键，台子消失，头顶 5 格出现审判庭哨兵（照全息投影台 {@link HolopadBlock#summon} 的写法）。
 * <p>
 * 右键只在服务端判，下面都满足才召唤，否则快捷栏写原因、什么都不扣（{@link #summon}，顺序即判定顺序）：
 * 真玩家、生存 / 冒险模式、不是和平难度、维度在 {@code general.inquisitorium.summon_dimensions} 里（默认只有海纳尔）、信标记着坐标、
 * 信标和坐标在同一个维度且水平距离 ≤ {@code arrive_radius}、台子上方露天（{@link HolopadBlock#openSky}）、夜里或海纳尔的黑暗天灾中、
 * 生成点 (x + 0.5, y + 5, z + 0.5) 站得下哨兵、64 格内没有别的全息投影或哨兵（{@link HologramEntity#anyNearby}，两种 Boss 不叠在一起）、
 * {@code ECProtection.canEdit}（召唤 = 拿掉这块方块）。<b>先生成后扣</b>：哨兵加进世界成功才删方块（不掉落），然后触发「召唤实体」进度条件
 * （{@code guide/inquisitorium/beacon} 认的就是它），并给召唤者补上 {@code guide/inquisitorium/decoded} 和 {@code arrived}（先做好信标再来的人没拿着坐标走过来）。
 * 所有参战者离开、到了放弃时间、切到和平难度时，哨兵消失，信标（带着坐标）掉回原处。
 * <p>
 * 「夜里」两端同风之号角（{@link WindHornItem#night}：按时间和天气现算，雷雨天的白天也算）；海纳尔正在黑暗天灾时白天也行。
 * GameTest 用 {@link #setTestNight} 固定昼夜（GameTest 服的时间是所有测试共用的，不能拨）。
 */
public class InquisitoriumBeaconBlock extends Block implements EntityBlock {

    /** 亮度（微微发光，夜里找得到放在哪） */
    public static final int LIGHT = 5;
    /** 同全息投影台：半格高 */
    private static final VoxelShape SHAPE = Block.box(0D, 0D, 0D, 16D, 8D, 16D);

    @Nullable
    private static Boolean testNight;

    /** 召唤的结果（按判定顺序） */
    public enum Result {
        OK,
        NOT_REAL_PLAYER,
        GAME_MODE,
        PEACEFUL,
        DIMENSION,
        NO_COORDINATES,
        TOO_FAR,
        NO_SKY,
        NOT_NIGHT,
        BLOCKED,
        OCCUPIED,
        DENIED,
        SPAWN_FAILED;

        /** 快捷栏提示的键：message.essentialcraft5.inquisitorium_beacon.&lt;小写名&gt;（OK、NOT_REAL_PLAYER 不提示） */
        public String messageKey() {
            return "message.essentialcraft5.inquisitorium_beacon." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** 召唤结论：result 是 OK 时 sentinel 是召出来的哨兵；TOO_FAR 时 distance 是信标到坐标的水平距离 */
    public record Outcome(Result result, @Nullable InquisitoriumSentinelEntity sentinel, double distance) {
        public boolean ok() {
            return result == Result.OK;
        }
    }

    public InquisitoriumBeaconBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InquisitoriumBeaconBlockEntity(pos, state);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /** 物品根标签里的坐标（合成时从审判庭坐标带过来的）写进方块实体 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof InquisitoriumBeaconBlockEntity beacon) {
            beacon.setTarget(InquisitoriumCoordinatesItem.target(stack));
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer) || !ECProtection.isRealPlayer(serverPlayer)) {
            return InteractionResult.PASS;
        }
        Outcome outcome = summon(serverPlayer, server, pos);
        if (!outcome.ok()) {
            serverPlayer.displayClientMessage(message(outcome).withStyle(ChatFormatting.RED), true);
        }
        return InteractionResult.CONSUME;
    }

    /** 快捷栏那一句（离坐标太远时带上要多近、现在多远） */
    public static MutableComponent message(Outcome outcome) {
        if (outcome.result() == Result.TOO_FAR) {
            return Component.translatable(outcome.result().messageKey(), ECInquisitorium.Rules.values().arriveRadius(),
                    Double.isFinite(outcome.distance()) ? String.valueOf(Mth.floor(outcome.distance())) : "?");
        }
        return Component.translatable(outcome.result().messageKey());
    }

    /**
     * 用 pos 上的审判庭信标召唤一只审判庭哨兵（见类注释）。只在服务端调；pos 上不是信标时按生成失败算。
     */
    public static Outcome summon(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!ECProtection.isRealPlayer(player)) {
            return fail(Result.NOT_REAL_PLAYER);
        }
        if (player.isCreative() || player.isSpectator()) {
            return fail(Result.GAME_MODE);
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return fail(Result.PEACEFUL);
        }
        if (!dimensionAllowed(level)) {
            return fail(Result.DIMENSION);
        }
        if (!(level.getBlockEntity(pos) instanceof InquisitoriumBeaconBlockEntity beacon) || !level.getBlockState(pos).is(ECInquisitorium.INQUISITORIUM_BEACON.get())) {
            return fail(Result.SPAWN_FAILED);
        }
        InquisitoriumCoordinatesItem.Target target = beacon.target();
        if (target == null) {
            return fail(Result.NO_COORDINATES);
        }
        double distance = target.isIn(level) ? target.horizontalDistance(pos.getX() + 0.5D, pos.getZ() + 0.5D) : Double.POSITIVE_INFINITY;
        if (distance > ECInquisitorium.Rules.values().arriveRadius()) {
            return new Outcome(Result.TOO_FAR, null, distance);
        }
        if (!HolopadBlock.openSky(level, pos)) {
            return fail(Result.NO_SKY);
        }
        if (!night(level)) {
            return fail(Result.NOT_NIGHT);
        }
        Vec3 spawn = HolopadBlock.spawnPoint(pos);
        AABB box = ECInquisitorium.INQUISITORIUM_SENTINEL.get().getDimensions().makeBoundingBox(spawn);
        if (!level.noCollision(box)) {
            return fail(Result.BLOCKED);
        }
        if (HologramEntity.anyNearby(level, Vec3.atCenterOf(pos))) {
            return fail(Result.OCCUPIED);
        }
        if (!ECProtection.canEdit(player, level, pos)) {
            return fail(Result.DENIED);
        }
        InquisitoriumSentinelEntity sentinel = ECInquisitorium.INQUISITORIUM_SENTINEL.get().create(level);
        if (sentinel == null) {
            return fail(Result.SPAWN_FAILED);
        }
        // 面朝召唤者
        float yaw = (float) (Mth.atan2(player.getZ() - spawn.z, player.getX() - spawn.x) * Mth.RAD_TO_DEG) - 90F;
        sentinel.moveTo(spawn.x, spawn.y, spawn.z, yaw, 0F);
        sentinel.setYHeadRot(yaw);
        sentinel.setYBodyRot(yaw);
        sentinel.prepareSummon(player, pos);
        sentinel.setBeaconTarget(target);
        // 加入事件被取消时 addFreshEntity 返回 false；有的模组在加入事件里直接 discard 而不取消，返回值仍是 true，一并算失败
        if (!level.addFreshEntity(sentinel) || sentinel.isRemoved()) {
            return fail(Result.SPAWN_FAILED);
        }
        level.removeBlock(pos, false);
        CriteriaTriggers.SUMMONED_ENTITY.trigger(player, sentinel);
        // 召唤要求信标在坐标 arrive_radius 格内、人又点得到信标，就是到了：先做好信标再出发的人坐标已经做进信标、没拿着走过来，在这里补上「到达」；
        // 信标只能用解出来的坐标做，「解码」一并补上（坐标被漏斗直接送进箱子、没进过背包的也接得上）
        PlayerPentacleBlockEntity.award(player, ECInquisitorium.ADVANCEMENT_DECODED, ECInquisitorium.ADVANCEMENT_CRITERION);
        PlayerPentacleBlockEntity.award(player, ECInquisitorium.ADVANCEMENT_ARRIVED, ECInquisitorium.ADVANCEMENT_CRITERION);
        return new Outcome(Result.OK, sentinel, distance);
    }

    /** 这个世界能不能召（维度在 summon_dimensions 里；写了海纳尔的也认 GameTest 当成海纳尔的世界） */
    public static boolean dimensionAllowed(Level level) {
        ResourceLocation here = level.dimension().location();
        for (String entry : ECInquisitorium.Rules.values().summonDimensions()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id != null && (id.equals(here) || (id.equals(ECHoanna.ID) && ECHoanna.isHoanna(level)))) {
                return true;
            }
        }
        return false;
    }

    /** 夜里（同风之号角），或者海纳尔正在黑暗天灾；测试覆盖值优先 */
    public static boolean night(ServerLevel level) {
        Boolean override = testNight;
        if (override != null) {
            return override;
        }
        return WindHornItem.night(level) || HoannaEvents.isActive(level, HoannaEvent.DARKNESS);
    }

    /** GameTest / 混合端自检用：固定「是不是夜里」，null = 照实算。同一段同步代码里设、测、置回 */
    public static void setTestNight(@Nullable Boolean night) {
        testNight = night;
    }

    private static Outcome fail(Result result) {
        return new Outcome(result, null, 0D);
    }

    /** 信标的物品：提示行写记着的坐标（没有时写「空白」）；一组 1 个 */
    public static class BeaconItem extends BlockItem {

        public BeaconItem(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            InquisitoriumCoordinatesItem.Target target = InquisitoriumCoordinatesItem.target(stack);
            if (target == null) {
                tooltip.add(Component.translatable("tooltip.essentialcraft5.inquisitorium_beacon.blank").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            tooltip.add(InquisitoriumCoordinatesItem.where(target).withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("tooltip.essentialcraft5.inquisitorium_beacon.usage").withStyle(ChatFormatting.GRAY));
        }
    }
}
