package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.WindWishes;
import com.flameforge.essentialcraft5.recipe.WindImbueRecipe;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.structure.StructureCache;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * 风之符文的方块实体（原作 TileWindRune）：判结构、注风、给客户端同步「祭坛是否完整」（BER 的符文图案和粒子要用）。
 *
 * <h2>注风（原作 action）</h2>
 * 手持注风原料（{@link WindImbueRecipe}）右键符文：结构完整、祭坛 8 根水晶柱（{@link ECWind#RUNE_PILLAR}）里的 ESPE 加起来够，
 * 就按柱子的顺序抽走配方要的 ESPE（{@link ESPETransfer}），主手那组少 1 个，产物优先放回主手（主手空了的话），否则进背包、放不下掉在脚下。
 * 创造模式不要 ESPE、也不扣 ESPE（原作 extractESPE(…, !creative)），原料照样扣 1 个（同原作）。
 * 「授予风之眷顾」的配方（灵魂石）不换物品：灵魂石原样留着，玩家受到风之眷顾，聊天栏收到「风在你身边盘旋……」。
 * 带 {@code requires_windbound} 的配方（风之号角，第十批 10b）只给已受眷顾的玩家注：没受眷顾的在判结构之后、扣 ESPE 之前拒绝，什么都不扣。
 *
 * <h2>风之心愿（第十批 10b，{@link WindWishes}）</h2>
 * 潜行、两手空着右键完整的符文：问风要一个心愿（不潜行照旧报祭坛状态）。原版潜行时只要任一只手拿着东西就不调方块的 use，到得了这里就是两手都空。
 * 手持风之药水潜行右键：换一个心愿、药水用掉一瓶（{@code WindWishEvents.windWishSwapClick} 在 RightClickBlock 里放行了方块交互，否则原版会去喝药水）。
 * 两种都要祭坛完整，不完整时说哪一格不对。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>只在服务端做（原作两端都执行，客户端也扣一遍 ESPE 和物品）。</li>
 *   <li>已受眷顾再注风灵魂石：原作照样抽走 4 万 ESPE，还把灵魂石换成配方产物（一块空白灵魂石，绑定的玩家丢了）；现在先判断、直接拒绝，什么都不扣（batch3.md §3.3 修复 2）。</li>
 *   <li>FakePlayer 不能受眷顾（原作 WindRelations 对 FakePlayer 一律不处理）；普通注风（钻石、水瓶）FakePlayer 也能做。</li>
 *   <li>右键时立即重判一次结构（原作用 40 tick 一次的缓存，拆了一块护板之后的 2 秒里还能注风）；平时 20 tick 一次（StructureCache）。</li>
 *   <li>ESPE 不够、结构不完整、已受眷顾都在快捷栏上方说一句（原作什么都不说）；空手右键报告祭坛状态（新增）。</li>
 *   <li>ESPE 够不够按抽得出来的量算（带面的 capability，{@code extractESPE} 模拟），浮点误差留 1e-6（原作 floor 以后比较，4 万整的配方抽到 39999.9999 会失败）。</li>
 *   <li>产物放不下时掉在脚下（ItemHandlerHelper.giveItemToPlayer；原版 Inventory#add 在创造模式下放不下也会把物品吞掉）。</li>
 * </ul>
 *
 * <h2>权限（policy §1.1）</h2>
 * 不改任何方块、不碰实体；只从祭坛自己的 8 根水晶柱里抽 ESPE。祭坛的 41 格都要摆对才算完整，跨着别人的领地边界搭不起来；
 * 右键别人的符文由领地插件的交互保护管（混合端转发 PlayerInteractEvent），所以不另做 canEdit 探测。
 */
public class WindRuneBlockEntity extends BlockEntity {

    /** 注风的结果（GameTest 断言用；方块只区分 PASS 和其余） */
    public enum Outcome {
        /** 手里不是注风原料：不处理，让给别的交互 */
        PASS,
        /** 空手：报告了祭坛状态 */
        STATUS,
        /** 结构不完整（或有一部分在未加载的区块里） */
        NOT_FORMED,
        /** 柱子里的 ESPE 不够 */
        NO_ESPE,
        /** 已经受到风之眷顾 */
        ALREADY_WINDBOUND,
        /** 这个玩家不能受眷顾（FakePlayer、没有玩家数据） */
        NOT_ALLOWED,
        /** 换出了产物 */
        IMBUED,
        /** 受到了风之眷顾 */
        WINDBOUND,
        /** 这条配方要已受风之眷顾（风之号角），玩家还没受：什么都不扣 */
        NEEDS_WINDBOUND,
        /** 问了 / 换了风之心愿（结果由 WindWishes 说） */
        WISH
    }

    /** 受眷顾时发的隐藏进度（手册「风之眷顾」一步打勾用） */
    public static final ResourceLocation WINDBOUND_ADVANCEMENT = EssentialCraft5.id("guide/wind/windbound");
    public static final String WINDBOUND_CRITERION = "windbound";

    /** 只在更新包里（存档里没有）：客户端据此画符文、出粒子 */
    private static final String TAG_FORMED = "formed";
    /** ESPE 比较的浮点容差 */
    private static final double EPS = 1.0E-6D;
    /** 原作 SmokeFX 的两种颜色：平时 (0.2, 1, 0.45)，注风成功 (0.7, 1, 0.85) */
    private static final DustParticleOptions AMBIENT = new DustParticleOptions(new Vector3f(0.2F, 1F, 0.45F), 1F);
    private static final DustParticleOptions BURST = new DustParticleOptions(new Vector3f(0.7F, 1F, 0.85F), 1.5F);
    /** 原作柱子处的 REDSTONE 粒子传 (-1, 1, 0)：红色为负被截成 0，实际是绿色 */
    private static final DustParticleOptions PILLAR = new DustParticleOptions(new Vector3f(0F, 0.8F, 0F), 1F);

    /** 8 根柱子相对符文的位置（结构四面对称，按北向；原作 coords），缓存一份免得每 tick 重建列表 */
    private static final List<BlockPos> PILLAR_OFFSETS = ECWind.RUNE_STRUCTURE.offsets(ECWind.RUNE_PILLAR);

    private final StructureCache structure = new StructureCache(ECWind.RUNE_STRUCTURE);
    /** 服务端：上次同步给客户端的结果；客户端：更新包里收到的结果 */
    private boolean formed;

    public WindRuneBlockEntity(BlockPos pos, BlockState state) {
        super(ECWind.WIND_RUNE_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    /** 结构按 StructureCache 的节流判（最多 20 tick 一次，原作 40 tick） */
    public static void serverTick(Level level, BlockPos pos, BlockState state, WindRuneBlockEntity be) {
        be.structure.check(level, pos, Direction.NORTH);
        be.syncFormed();
    }

    /**
     * 原作 update 的客户端部分：祭坛完整时符文上方飘一缕绿色的风，8 根柱子旁有绿色光点上下起伏（60 tick 一个来回）。
     * 原作每 tick 1 + 16 个粒子，这里柱子的上下两个隔 tick 轮流出（每 tick 1 + 8 个）。
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, WindRuneBlockEntity be) {
        if (!be.formed) {
            return;
        }
        RandomSource random = level.random;
        level.addParticle(AMBIENT, pos.getX() + 0.5D + spread(random) * 1.6D, pos.getY(), pos.getZ() + 0.5D + spread(random) * 1.6D,
                0D, random.nextDouble() * 0.3D, 0D);
        long time = level.getGameTime();
        int movement = (int) (time % 60L);
        if (movement > 30) {
            movement = 60 - movement;
        }
        double lift = movement / 30D + ((time & 1L) == 0L ? 0D : 2D);
        for (BlockPos offset : PILLAR_OFFSETS) {
            level.addParticle(PILLAR, pos.getX() + offset.getX() + 0.5D, pos.getY() + offset.getY() + lift, pos.getZ() + offset.getZ() + 0.5D,
                    0D, 0D, 0D);
        }
    }

    /** 原作 MathUtils.randomDouble：[-1, 1) */
    private static double spread(RandomSource random) {
        return random.nextDouble() * 2D - 1D;
    }

    private void syncFormed() {
        boolean now = structure.formed();
        if (now != formed && level != null && !level.isClientSide) {
            formed = now;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 祭坛是否完整（服务端：最近一次确定的判定；客户端：同步来的） */
    public boolean isFormed() {
        return formed;
    }

    public StructureCache structure() {
        return structure;
    }

    // ------------------------------------------------------------------
    // 注风
    // ------------------------------------------------------------------

    /** 客户端预判（方块 use 用）：空手、受眷顾的人潜行拿着风之药水（换心愿），或主手是注风原料时，这次右键归符文 */
    public boolean wouldHandle(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || isWishSwap(player, held)) {
            return true;
        }
        return level != null && WindImbueRecipe.find(level, held) != null;
    }

    /** 手持风之药水潜行右键、心愿功能开着、玩家受眷顾：换心愿（两端判断一致） */
    private static boolean isWishSwap(Player player, ItemStack held) {
        return player.isShiftKeyDown() && held.is(ECWind.AIR_POTION.get()) && WindWishes.swapApplies(player);
    }

    /** 服务端：玩家手持主手物品右键符文（见类注释） */
    public Outcome imbue(Player player) {
        Level level = this.level;
        if (level == null || level.isClientSide) {
            return Outcome.PASS;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            if (player.isShiftKeyDown() && WindWishes.Config.enabled() && player instanceof ServerPlayer serverPlayer
                    && ECProtection.isRealPlayer(serverPlayer)) {
                if (!checkNow()) {
                    player.displayClientMessage(structureProblem(), true);
                    return Outcome.NOT_FORMED;
                }
                WindWishes.ask(serverPlayer);
                return Outcome.WISH;
            }
            reportStatus(player);
            return Outcome.STATUS;
        }
        if (isWishSwap(player, held) && player instanceof ServerPlayer serverPlayer && ECProtection.isRealPlayer(serverPlayer)) {
            if (!checkNow()) {
                player.displayClientMessage(structureProblem(), true);
                return Outcome.NOT_FORMED;
            }
            WindWishes.swap(serverPlayer, held);
            return Outcome.WISH;
        }
        WindImbueRecipe recipe = WindImbueRecipe.find(level, held);
        if (recipe == null) {
            return Outcome.PASS;
        }
        if (!checkNow()) {
            player.displayClientMessage(structureProblem(), true);
            return Outcome.NOT_FORMED;
        }
        if (recipe.requiresWindbound() && !WindAffinity.isWindbound(player)) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.wind_rune.needs_windbound")
                    .withStyle(ChatFormatting.DARK_AQUA), true);
            return Outcome.NEEDS_WINDBOUND;
        }
        IPlayerData data = null;
        if (recipe.grantsWindbound()) {
            data = player instanceof FakePlayer ? null : PlayerDataEvents.get(player);
            if (data == null) {
                return Outcome.NOT_ALLOWED;
            }
            if (data.isWindbound()) {
                player.displayClientMessage(Component.translatable("message.essentialcraft5.wind_rune.already_windbound")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
                return Outcome.ALREADY_WINDBOUND;
            }
        }
        // 创造模式看服务端的能力位（原作 capabilities.isCreativeMode 同理）
        if (!player.getAbilities().instabuild) {
            double cost = recipe.getEspe();
            double available = availableESPE();
            if (available + EPS < cost) {
                player.displayClientMessage(Component.translatable("message.essentialcraft5.wind_rune.no_espe",
                        ESPEStorage.format(available), ESPEStorage.format(cost)).withStyle(ChatFormatting.RED), true);
                return Outcome.NO_ESPE;
            }
            if (!drain(cost)) {
                // 刚算过够，同一刻里不会不够；万一（柱子的实现不守规矩）已经原样还回去了
                return Outcome.NO_ESPE;
            }
        }
        burst();
        if (data != null) {
            data.setWindbound(true);
            player.sendSystemMessage(Component.translatable("message.essentialcraft5.wind_rune.windbound").withStyle(ChatFormatting.AQUA));
            awardWindbound(player);
            return Outcome.WINDBOUND;
        }
        ItemStack result = recipe.assemble(held);
        held.shrink(1);
        if (held.isEmpty()) {
            // 原作：这一格用完了就把产物直接放进这一格
            player.setItemInHand(InteractionHand.MAIN_HAND, result);
        } else {
            ItemHandlerHelper.giveItemToPlayer(player, result);
        }
        return Outcome.IMBUED;
    }

    /** 立即重判结构（右键用），并把结果同步给客户端 */
    private boolean checkNow() {
        StructureResult result = structure.checkNow(level, worldPosition, Direction.NORTH);
        syncFormed();
        return result == StructureResult.MATCH;
    }

    /** 结构不对时给玩家看的话：第一个不符的格子，或者「有一部分在未加载的区块里」 */
    private Component structureProblem() {
        if (structure.status() == StructureResult.UNKNOWN) {
            return Component.translatable("structure.essentialcraft5.unknown").withStyle(ChatFormatting.RED);
        }
        StructurePattern.Mismatch mismatch = structure.mismatch();
        return mismatch != null ? mismatch.message().copy().withStyle(ChatFormatting.RED)
                : Component.translatable("structure.essentialcraft5.incomplete").withStyle(ChatFormatting.RED);
    }

    /** 空手右键：祭坛完整就说柱子里一共有多少 ESPE，否则说哪一格不对 */
    private void reportStatus(Player player) {
        if (checkNow()) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.wind_rune.status",
                    ESPEStorage.format(availableESPE())).withStyle(ChatFormatting.DARK_AQUA), true);
        } else {
            player.displayClientMessage(structureProblem(), true);
        }
    }

    /** 8 根柱子的世界坐标 */
    private List<BlockPos> pillars() {
        return PILLAR_OFFSETS.stream().map(worldPosition::offset).toList();
    }

    /** 8 根柱子里现在抽得出来的 ESPE 总量（只读已加载的区块，原作 getEnderstarEnergy） */
    public double availableESPE() {
        if (level == null) {
            return 0D;
        }
        double total = 0D;
        for (BlockPos pillar : pillars()) {
            IESPEHandler handler = ESPETransfer.loadedHandler(level, pillar, ESPETransfer.sideFacing(pillar, worldPosition));
            if (handler != null) {
                double amount = handler.extractESPE(Double.MAX_VALUE, false);
                if (amount > 0) {
                    total += amount;
                }
            }
        }
        return total;
    }

    /**
     * 按柱子的顺序抽走 cost（原作 action 里的循环）：先抽进一个上限为 cost 的临时存储，抽满为止。
     * 没抽够就原样还回去、返回 false（调用前已经确认过够，正常不会发生）。
     */
    private boolean drain(double cost) {
        if (!(cost > 0)) {
            return true;
        }
        ESPEStorage buffer = new ESPEStorage(() -> cost, 0);
        ESPETransfer.pullFromAround(level, worldPosition, buffer, PILLAR_OFFSETS);
        double got = buffer.getESPE();
        if (got + EPS >= cost) {
            return true;
        }
        for (BlockPos pillar : pillars()) {
            if (!(got > 0)) {
                break;
            }
            IESPEHandler handler = ESPETransfer.loadedHandler(level, pillar, ESPETransfer.sideFacing(pillar, worldPosition));
            if (handler != null) {
                got = handler.addESPE(got, true);
            }
        }
        return false;
    }

    /** 注风成功的一团风（原作客户端 300 个 SmokeFX；这里服务端发一个粒子包，客户端按数量散开） */
    private void burst() {
        if (level instanceof ServerLevel server) {
            server.sendParticles(BURST, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.1D, worldPosition.getZ() + 0.5D,
                    80, 0.8D, 0.1D, 0.8D, 0.05D);
        }
    }

    private static void awardWindbound(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            Advancement advancement = serverPlayer.server.getAdvancements().getAdvancement(WINDBOUND_ADVANCEMENT);
            if (advancement != null) {
                serverPlayer.getAdvancements().award(advancement, WINDBOUND_CRITERION);
            }
        }
    }

    // ------------------------------------------------------------------
    // 存档与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        structure.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_FORMED)) {
            // 客户端收到的更新包
            formed = tag.getBoolean(TAG_FORMED);
        } else {
            // 存档：读档后缓存标脏，第一次判定之前沿用存档里的结果
            structure.load(tag);
            formed = structure.formed();
        }
    }

    /** 客户端只需要「完整没有」这一位 */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_FORMED, formed);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 原作 getRenderBoundingBox：地上的符文图案盖住周围 3×3 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-1, 0, -1), worldPosition.offset(2, 1, 2));
    }
}
