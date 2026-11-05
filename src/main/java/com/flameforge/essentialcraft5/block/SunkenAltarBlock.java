package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.SunkenAltarBlockEntity;
import com.flameforge.essentialcraft5.entity.boss.AbyssTyrantEntity;
import com.flameforge.essentialcraft5.entity.boss.BossDaily;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * 沉城祭坛（沉没城区穹顶中央，第十三批 13e-S2，batch13.md §5.3.4、§6.4）：手里拿着潮晶的真玩家右键，召出渊潮领主。
 * 框架照全息投影台（{@link HolopadBlock}）。右键只在服务端判，下面都满足才召唤，否则动作栏按原因说一句、什么都不扣（{@link #summon}，顺序即判定顺序）：
 * 真玩家（假玩家、机械手不行）、生存 / 冒险模式、不是和平难度、这一座今天（主世界的游戏日）还没召过、祭坛 {@code boss_clearance}（64）格内没有别的首领
 * （{@code #forge:bosses}，活着的）、{@code ECProtection.canEdit} 祭坛那格（领地不归你的召不了）、主手或副手拿着潮晶、
 * 背包里（含两只手）够潮晶 {@code altar_tide_crystals}（4）+ 异变之心 {@code altar_aberrant_hearts}（1）、穹顶外找得到放领主的水（{@link #spawnPoint}）。
 * <p>
 * <b>先生成后扣</b>：领主加进世界成功（别的模组、混合端插件没取消）才扣材料、记今天；祭坛本身不消耗、不可破坏（硬度 −1、抗爆 3 600 000，推不动）。
 * 召唤者进参战名单，生成来源记成首领（{@code SpawnOrigin.Origin.BOSS}），触发「召唤实体」进度条件。
 * 「每座每日一次」记在方块实体里（{@link SunkenAltarBlockEntity}）；领主的奖励另按「每人每日一份」算（{@code AbyssTyrantEntity}），
 * 两条互不替代：一个人一天打几座沉城也只领一份，一座沉城一天也只召一次。
 */
public class SunkenAltarBlock extends Block implements EntityBlock {

    /** 领主放在穹顶顶上：从祭坛往上至少隔这么多格（穹顶外壳顶在祭坛 + 8） */
    public static final int ABOVE_DOME = 9;
    /** 穹顶顶上往上找几格水 */
    public static final int ABOVE_SEARCH = 12;
    /** 顶上没水时，在穹顶侧面这么远的水里找（穹顶外壳半径 9） */
    public static final int SIDE_DISTANCE = 12;

    /** 召唤的结果（按判定顺序） */
    public enum Result {
        OK,
        NOT_REAL_PLAYER,
        GAME_MODE,
        PEACEFUL,
        USED_TODAY,
        OCCUPIED,
        DENIED,
        NOT_HOLDING,
        MISSING,
        BLOCKED,
        SPAWN_FAILED;

        /** 动作栏提示的键：message.essentialcraft5.sunken_altar.&lt;小写名&gt;（NOT_REAL_PLAYER 不提示） */
        public String messageKey() {
            return "message.essentialcraft5.sunken_altar." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** 召唤结论：result 是 OK 时 tyrant 是召出来的领主 */
    public record Outcome(Result result, @Nullable AbyssTyrantEntity tyrant) {
        public boolean ok() {
            return result == Result.OK;
        }
    }

    public SunkenAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SunkenAltarBlockEntity(pos, state);
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
        Outcome outcome = summon(serverPlayer, server, pos, BossDaily.now(server.getServer()));
        serverPlayer.displayClientMessage(message(outcome.result()), true);
        return InteractionResult.CONSUME;
    }

    /** 动作栏的那一句（成功也说一句：领主在穹顶外醒了） */
    public static Component message(Result result) {
        ECSunkenDistrict.Config.Values cfg = ECSunkenDistrict.Config.values();
        return switch (result) {
            case OK -> Component.translatable(result.messageKey()).withStyle(ChatFormatting.AQUA);
            case MISSING, NOT_HOLDING -> Component.translatable(result.messageKey(), cfg.tideCrystals(), cfg.aberrantHearts()).withStyle(ChatFormatting.RED);
            default -> Component.translatable(result.messageKey()).withStyle(ChatFormatting.RED);
        };
    }

    /**
     * 用 pos 上的祭坛召一只渊潮领主（见类注释）。只在服务端调；today 是现在这一刻的钟（{@code BossDaily.now}，测试直接给）。
     * pos 上不是祭坛时按生成失败算。
     */
    public static Outcome summon(ServerPlayer player, ServerLevel level, BlockPos pos, BossDaily.Stamp today) {
        if (!ECProtection.isRealPlayer(player)) {
            return fail(Result.NOT_REAL_PLAYER);
        }
        if (player.isCreative() || player.isSpectator()) {
            return fail(Result.GAME_MODE);
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return fail(Result.PEACEFUL);
        }
        if (!level.getBlockState(pos).is(ECSunkenDistrict.SUNKEN_ALTAR.get()) || !(level.getBlockEntity(pos) instanceof SunkenAltarBlockEntity altar)) {
            return fail(Result.SPAWN_FAILED);
        }
        if (altar.usedOn(today)) {
            return fail(Result.USED_TODAY);
        }
        ECSunkenDistrict.Config.Values cfg = ECSunkenDistrict.Config.values();
        if (bossNearby(level, Vec3.atCenterOf(pos), cfg.bossClearance())) {
            return fail(Result.OCCUPIED);
        }
        if (!ECProtection.canEdit(player, level, pos)) {
            return fail(Result.DENIED);
        }
        Item crystal = ECHoannaMaterials.TIDE_CRYSTAL.get();
        Item heart = ECMobDrops.ABERRANT_HEART.get();
        if (cfg.tideCrystals() > 0 && !player.getMainHandItem().is(crystal) && !player.getOffhandItem().is(crystal)) {
            return fail(Result.NOT_HOLDING);
        }
        if (count(player.getInventory(), crystal) < cfg.tideCrystals() || count(player.getInventory(), heart) < cfg.aberrantHearts()) {
            return fail(Result.MISSING);
        }
        EntityType<AbyssTyrantEntity> type = ECSunkenDistrict.ABYSS_TYRANT.get();
        Vec3 spawn = spawnPoint(level, pos, type);
        if (spawn == null) {
            return fail(Result.BLOCKED);
        }
        AbyssTyrantEntity tyrant = type.create(level);
        if (tyrant == null) {
            return fail(Result.SPAWN_FAILED);
        }
        float yaw = (float) (Mth.atan2(player.getZ() - spawn.z, player.getX() - spawn.x) * Mth.RAD_TO_DEG) - 90F;
        tyrant.moveTo(spawn.x, spawn.y, spawn.z, yaw, 0F);
        tyrant.setYHeadRot(yaw);
        tyrant.setYBodyRot(yaw);
        tyrant.applyConfiguredAttributes();
        tyrant.prepareSummon(pos, spawn);
        SpawnOrigin.set(tyrant, SpawnOrigin.Origin.BOSS);
        // 加入事件被取消时 addFreshEntity 返回 false；有的模组在加入事件里直接 discard 而不取消，返回值仍是 true，一并算失败
        if (!level.addFreshEntity(tyrant) || tyrant.isRemoved()) {
            return fail(Result.SPAWN_FAILED);
        }
        consume(player, crystal, cfg.tideCrystals());
        consume(player, heart, cfg.aberrantHearts());
        altar.markUsed(today);
        tyrant.bossRoster().add(player);
        level.sendParticles(ParticleTypes.NAUTILUS, pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D, 30, 0.4D, 0.4D, 0.4D, 0.6D);
        level.playSound(null, pos, ECSunkenDistrict.SOUND_ALTAR_SUMMON.get(), SoundSource.BLOCKS, 2F, 1F);
        CriteriaTriggers.SUMMONED_ENTITY.trigger(player, tyrant);
        return new Outcome(Result.OK, tyrant);
    }

    /** radius 格内有没有活着的首领（{@code #forge:bosses}；radius ≤ 0 不查） */
    public static boolean bossNearby(ServerLevel level, Vec3 center, double radius) {
        if (!(radius > 0D)) {
            return false;
        }
        double max = radius * radius;
        List<Entity> found = level.getEntities((Entity) null, new AABB(center, center).inflate(radius),
                entity -> entity.isAlive() && entity.getType().is(Tags.EntityTypes.BOSSES) && entity.distanceToSqr(center) <= max);
        return !found.isEmpty();
    }

    /**
     * 领主出在哪（祭坛在穹顶正中，穹顶外壳顶在祭坛 + 8）：先在祭坛正上方、穹顶顶上 {@value #ABOVE_DOME}~{@value #ABOVE_DOME} + {@value #ABOVE_SEARCH} 格里
     * 从下往上找第一个整个碰撞箱都泡在水里、不碰方块的位置；穹顶露出水面、顶上没水时，在穹顶外 {@value #SIDE_DISTANCE} 格的八个方向上、
     * 从祭坛 + 8 往下到祭坛那一层找（离水面近的先）。区块没加载的格子不看。都找不到返回 null。
     */
    @Nullable
    public static Vec3 spawnPoint(ServerLevel level, BlockPos altar, EntityType<?> type) {
        double cx = altar.getX() + 0.5D;
        double cz = altar.getZ() + 0.5D;
        for (int dy = ABOVE_DOME; dy <= ABOVE_DOME + ABOVE_SEARCH; dy++) {
            Vec3 at = new Vec3(cx, altar.getY() + dy, cz);
            if (submerged(level, type, at)) {
                return at;
            }
        }
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        for (int dy = ABOVE_DOME - 1; dy >= 0; dy--) {
            for (int[] dir : dirs) {
                double scale = dir[0] != 0 && dir[1] != 0 ? SIDE_DISTANCE / Math.sqrt(2D) : SIDE_DISTANCE;
                Vec3 at = new Vec3(cx + dir[0] * scale, altar.getY() + dy, cz + dir[1] * scale);
                if (submerged(level, type, at)) {
                    return at;
                }
            }
        }
        return null;
    }

    /** 脚底在 at 时整个碰撞箱都在水里、不碰方块（碰撞箱占到的格子都已加载） */
    public static boolean submerged(ServerLevel level, EntityType<?> type, Vec3 at) {
        AABB box = type.getDimensions().makeBoundingBox(at);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX - 1.0E-6D);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY - 1.0E-6D);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ - 1.0E-6D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!ECLevels.isFullyLoaded(level, cursor) || !level.getFluidState(cursor).is(FluidTags.WATER)) {
                        return false;
                    }
                }
            }
        }
        return level.noCollision(box);
    }

    /** 背包（含两只手、盔甲栏不算）里这种物品一共几个 */
    public static int count(Inventory inventory, Item item) {
        int total = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 扣 amount 个（先扣拿在手里的那一叠，再从背包里扣）。调用前已经数过够不够 */
    private static void consume(ServerPlayer player, Item item, int amount) {
        int left = amount;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (left > 0 && held.is(item)) {
                int take = Math.min(left, held.getCount());
                held.shrink(take);
                left -= take;
            }
        }
        Inventory inventory = player.getInventory();
        for (List<ItemStack> list : List.of(inventory.items, inventory.offhand)) {
            for (ItemStack stack : list) {
                if (left <= 0) {
                    break;
                }
                if (stack.is(item)) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        inventory.setChanged();
    }

    private static Outcome fail(Result result) {
        return new Outcome(result, null);
    }
}
