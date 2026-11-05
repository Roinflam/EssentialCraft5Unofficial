package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.corruption.CorruptionGate;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.menu.PlayerPentacleMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECPentacle;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 灵魂五芒星的方块实体（原作 TilePlayerPentacle）：每 {@link #CHECK_INTERVAL} tick 判一次结构档位（{@link ECPentacle#TIER_PATTERNS}），
 * 治腐化效果时从结构里的水晶柱抽 ESPE。界面（menu/PlayerPentacleMenu）通过 {@link #cure} 治疗，服务端校验一切。
 * <p>
 * 继承 {@link MRUBlockEntity} 只是为了用机器那一套（右键开界面、菜单同步数值、展示厅自动打开界面）：MRU 上限 0、没有物品栏。
 *
 * <h2>ESPE（原作 getEnderstarEnergy / consumeEnderstarEnergy）</h2>
 * 能用的 ESPE（「有效 ESPE」）= ⌊结构里 8 / 12 / 16 / 20 / 24 根柱子（按档位，{@link ECPentacle#pillarOffsets}）的 ESPE 之和 × 0.36⌋，3 档每根柱子按 2 倍算，
 * 4 档（13e）同 3 档也是 2 倍（{@link #multiplier(int)}）：4 档多的是 4 根柱子和暗渎柱 ×4 的产能，不再翻倍。
 * 治一个效果花它的成本（{@link CorruptionEffects.Effect#cost()}）那么多有效 ESPE，也就是从柱子里抽 ⌊成本 ÷ 0.36⌋（3 档再减半）原始 ESPE：
 * 先算够不够，够了再按原作的顺序一根一根抽（抽空一根再抽下一根）。8 根满柱（8 万）是 28 800 有效 ESPE，治一个身体类（6 000）抽 16 666。
 * 柱子只读已加载的（{@link ESPETransfer#loadedHandler}，不触发区块加载）。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>原作按钮包带着客户端自报的「创造模式」就免费清效果，不查坐标、下标、档位，客户端自己还先扣一次 ESPE（batch3 §3.7 修 1）：
 *       这里只接受界面按钮（clickMenuButton），档位、ESPE、创造模式全在服务端判，见 {@link PlayerPentacleMenu}。</li>
 *   <li>按钮按「效果本身」编号（腐化效果表的序号），不按「身上第几个效果」：同步有延迟时不会治错。</li>
 *   <li>档位存盘、随更新包发给客户端（BER 的颜色和旋转、客户端粒子要用），读档后第一个 tick 重判；结构有一部分在没加载的区块里时沿用上次的档位。</li>
 *   <li>界面的「有效 ESPE」由服务端算好同步（原作客户端自己读柱子，1.20 的柱子不同步 ESPE）。</li>
 *   <li>粒子每 2 tick 出一次（原作每 tick），只在客户端。</li>
 * </ul>
 */
public class PlayerPentacleBlockEntity extends MRUBlockEntity {

    /** 原作 sCheckTick = 40 */
    public static final int CHECK_INTERVAL = 40;
    /** 原作 consumeModifier：柱子里 1 点 ESPE 只顶 0.36 点 */
    public static final double CONSUME_MODIFIER = 0.36D;
    /** 客户端粒子间隔（tick） */
    private static final int PARTICLE_INTERVAL = 2;
    private static final double EPS = 1.0E-6D;
    private static final String TAG_TIER = "tier";

    /** 手册步骤「治一个效果」「1 档」「2 档」「3 档」的进度（没有 display，只给手册打勾） */
    public static final ResourceLocation GUIDE_CURE = EssentialCraft5.id("guide/pentacle/cure");
    public static final String GUIDE_CURE_CRITERION = "cured";
    public static final ResourceLocation GUIDE_TIER1 = EssentialCraft5.id("guide/pentacle/tier1");
    public static final String GUIDE_TIER1_CRITERION = "formed";
    public static final ResourceLocation GUIDE_TIER2 = EssentialCraft5.id("guide/pentacle/tier2");
    public static final ResourceLocation GUIDE_TIER3 = EssentialCraft5.id("guide/pentacle/tier3");
    /** 第二十二章「4 档五芒星」一步（13e，batch13.md §2.2），判据名同 1 ~ 3 档 */
    public static final ResourceLocation GUIDE_TIER4 = EssentialCraft5.id("guide/pentacle/tier4");
    /** 进度页上的「灵魂净化」（原作只有描述「用上最高档的灵魂五芒星」，标题是占位文字），3 档时和手册的 3 档一起发；判据名同上 */
    public static final ResourceLocation MAIN_PLAYER_PENTACLE = EssentialCraft5.id("main/player_pentacle");

    /**
     * 各档的颜色（原作 RenderPlayerPentacle / GuiPlayerPentacle 的 GlStateManager.color），下标 = 档位 + 1。
     * 最后一个是 13e 的 4 档：暗渎紫，比 2 档的紫暗、偏蓝，和 3 档的红分得开。
     */
    private static final float[][] TIER_COLORS = {
            {0.2F, 0.2F, 0.2F}, {0F, 1F, 0F}, {0F, 0F, 1F}, {0.5F, 0F, 0.5F}, {1F, 0F, 0F}, {0.35F, 0.12F, 0.55F}};

    /**
     * 界面上档位那行字的颜色（下标 = 档位 + 1）：灰、绿、蓝、紫、红、暗渎紫（4 档），比符文颜色亮一些，深色背景上看得清
     * （4 档的 #A070E0 对黑底约 5.3:1，和 2 档偏品红的紫分得开）。和 {@link #TIER_COLORS} 一样长，加档要一起加
     */
    private static final int[] TIER_TEXT = {0xAAAAAA, 0x55FF55, 0x5599FF, 0xCC66CC, 0xFF5555, 0xA070E0};

    /** −1 = 结构不完整，0 ~ {@link ECPentacle#MAX_TIER} */
    private int tier = -1;
    private int checkTimer;
    /** 界面每 tick 要读两次（拆成两格同步），同一 tick 里只算一次 */
    private long espeCacheTime = Long.MIN_VALUE;
    private int espeCache;

    public PlayerPentacleBlockEntity(BlockPos pos, BlockState state) {
        super(ECPentacle.PLAYER_PENTACLE_BE.get(), pos, state, () -> 0, 0);
    }

    // ------------------------------------------------------------------
    // 档位
    // ------------------------------------------------------------------

    /** −1 = 结构不完整；客户端上是服务端同步来的值 */
    public int getTier() {
        return tier;
    }

    /** 界面上档位文字的颜色（0xRRGGBB） */
    public static int tierTextColor(int tier) {
        return TIER_TEXT[Math.max(-1, Math.min(ECPentacle.MAX_TIER, tier)) + 1];
    }

    /** 档位颜色（RGB 0~1），BER、界面共用 */
    public static float[] tierColor(int tier) {
        return TIER_COLORS[Math.max(-1, Math.min(ECPentacle.MAX_TIER, tier)) + 1];
    }

    @Override
    protected void serverTick() {
        if (--checkTimer <= 0) {
            checkTimer = CHECK_INTERVAL;
            recheckTier();
        }
    }

    /** 立即重判一次档位（结构有一部分没加载时沿用上次的），变了就存盘并发更新包；返回新档位。只在服务端调 */
    public int recheckTier() {
        if (level == null || level.isClientSide) {
            return tier;
        }
        int now = computeTier(level, worldPosition, tier);
        if (now != tier) {
            tier = now;
            espeCacheTime = Long.MIN_VALUE;
            level.blockEntityChanged(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return tier;
    }

    /**
     * 原作 checkStructureAndTier：从 0 档起逐档判，满足到第几档就是几档。
     * 有一档的结果是「部分没加载」（UNKNOWN）时给不出结论，返回 previous。
     */
    public static int computeTier(Level level, BlockPos pos, int previous) {
        int result = -1;
        for (int t = 0; t < ECPentacle.TIER_PATTERNS.size(); t++) {
            StructurePattern pattern = ECPentacle.TIER_PATTERNS.get(t);
            StructureResult match = pattern.match(level, pos, Direction.NORTH).result();
            if (match == StructureResult.UNKNOWN) {
                return previous;
            }
            if (match == StructureResult.MISMATCH) {
                break;
            }
            result = t;
        }
        return result;
    }

    // ------------------------------------------------------------------
    // ESPE
    // ------------------------------------------------------------------

    /** 结构里（按当前档位）的柱子；只取已加载的、带 ESPE 的 */
    private List<IESPEHandler> pillars() {
        List<IESPEHandler> out = new ArrayList<>();
        if (level == null) {
            return out;
        }
        for (BlockPos offset : ECPentacle.pillarOffsets(tier)) {
            BlockPos at = worldPosition.offset(offset);
            IESPEHandler handler = ESPETransfer.loadedHandler(level, at, ESPETransfer.sideFacing(at, worldPosition));
            if (handler != null) {
                out.add(handler);
            }
        }
        return out;
    }

    /** 3 档的柱子每点 ESPE 按 2 倍算（原作） */
    private int multiplier() {
        return multiplier(tier);
    }

    /** tier 档的柱子每点 ESPE 算几倍：3 档起 2 倍（原作），4 档同 3 档，不再往上翻（batch13.md §7.5 末行） */
    public static int multiplier(int tier) {
        return tier >= 3 ? 2 : 1;
    }

    /**
     * tier 档的柱子全部攒满时的有效 ESPE（⌊柱子数 × 柱子上限 × 倍率 × 0.36⌋；tier &lt; 0 是 0）。默认柱子上限 1 万：0 档 28800、1 档 43200、
     * 2 档 57600、3 档 144000、4 档 172800。「这一档治不治得了某个效果」按它判（{@code corruption/CorruptionGate}），两端都能调（柱子上限是 SERVER 配置，会同步）。
     */
    public static long maxEffectiveESPE(int tier) {
        if (tier < 0) {
            return 0L;
        }
        int clamped = Math.min(tier, ECPentacle.MAX_TIER);
        int pillars = 0;
        for (int t = 0; t <= clamped; t++) {
            pillars += ECPentacle.PILLAR_OFFSETS.get(t).size();   // 每次发作都要判，不建 pillarOffsets 那张新表
        }
        double perPillar = ECConfig.get(MithrilineCrystalPillarBlockEntity.Config.maxESPE);
        double sum = pillars * perPillar * multiplier(clamped);
        return (long) Math.min(Long.MAX_VALUE, Math.floor(sum * CONSUME_MODIFIER));
    }

    /** 有效 ESPE（原作 getEnderstarEnergy）：⌊柱子之和 × 倍率 × 0.36⌋；结构不完整时 0。只在服务端有意义 */
    public int effectiveESPE() {
        if (level == null || level.isClientSide || tier < 0) {
            return 0;
        }
        long now = level.getGameTime();
        if (espeCacheTime != now) {
            double sum = 0D;
            for (IESPEHandler handler : pillars()) {
                sum += handler.getESPE() * multiplier();
            }
            espeCacheTime = now;
            espeCache = (int) Math.min(Integer.MAX_VALUE, Math.floor(sum * CONSUME_MODIFIER));
        }
        return espeCache;
    }

    /** 花 cost 有效 ESPE 要从柱子里抽多少原始 ESPE（原作 {@code consumed /= consumeModifier}，int 截断） */
    public static int rawCost(int cost) {
        return (int) (cost / CONSUME_MODIFIER);
    }

    /**
     * 原作 consumeEnderstarEnergy：先算够不够，够了再一根一根抽。不够时什么都不抽，返回 false。
     * 只在服务端调；档位由调用方先判好。
     */
    public boolean consumeESPE(int cost) {
        if (level == null || level.isClientSide || tier < 0 || cost < 0) {
            return false;
        }
        int mult = multiplier();
        double needed = rawCost(cost);
        List<IESPEHandler> handlers = pillars();
        double available = 0D;
        for (IESPEHandler handler : handlers) {
            available += handler.getESPE() * mult;
        }
        if (available + EPS < needed) {
            return false;
        }
        double remaining = needed;
        for (IESPEHandler handler : handlers) {
            if (remaining <= EPS) {
                break;
            }
            remaining -= handler.extractESPE(remaining / mult, true) * mult;
        }
        espeCacheTime = Long.MIN_VALUE;
        return true;
    }

    // ------------------------------------------------------------------
    // 治疗
    // ------------------------------------------------------------------

    /**
     * 治掉 player 身上的 effect。只在服务端调（菜单的 clickMenuButton 校验过按钮号、距离）。
     * <ul>
     *   <li>免费（free = true，只给服务端判定的创造模式玩家）：不看档位、不花 ESPE（原作创造模式按住 Shift 点击）。</li>
     *   <li>否则当场重判一次结构：档位 ≥ 这一类要求的档位（身体 0、心智 1、矩阵 2），而且柱子里的 ESPE 够，才扣 ESPE 并去掉效果。</li>
     * </ul>
     * 身上没有这个效果时什么都不做。成功返回 true。
     */
    public boolean cure(ServerPlayer player, @Nullable CorruptionEffects.Effect effect, boolean free) {
        if (effect == null || level == null || level.isClientSide) {
            return false;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || !data.hasCorruptionEffect(effect.id())) {
            return false;
        }
        if (!free) {
            int now = recheckTier();
            if (now < effect.category().minPentacleTier() || !consumeESPE(effect.cost())) {
                return false;
            }
        }
        data.removeCorruptionEffect(effect.id());
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.2F);
        player.displayClientMessage(Component.translatable("message.essentialcraft5.player_pentacle.cured",
                Component.translatable(effect.nameKey())).withStyle(ChatFormatting.AQUA), true);
        award(player, GUIDE_CURE, GUIDE_CURE_CRITERION);
        return true;
    }

    /** 发进度（进度文件不存在时什么都不做，整合包删了它也不报错） */
    public static void award(ServerPlayer player, ResourceLocation id, String criterion) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    // ------------------------------------------------------------------
    // 界面
    // ------------------------------------------------------------------

    /**
     * 打开界面时当场重判一次档位（刚搭完不用等 2 秒）。打勾、记档位不在这里做，等界面真的打开（{@link #onMenuOpened}）：
     * 混合端先调这里、再发插件的打开容器事件，插件拦下打开时界面没开。
     */
    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        if (player instanceof ServerPlayer) {
            recheckTier();
        }
        return new PlayerPentacleMenu(id, inventory, this);
    }

    /**
     * 界面真的打开了（{@code PlayerContainerEvent.Open}，见 corruption/CorruptionGate.PentacleOpenEvents）：给手册的「1 / 2 / 3 / 4 档」步骤打勾，
     * 3 档同时发进度「灵魂净化」；成形的（0 档起）把档位记进玩家数据——打开过一次成形的五芒星，腐化效果才会抽到这名玩家（{@link CorruptionGate#recordPentacle}），
     * 但只记自己的、或者自己能动的五芒星（{@link #mayRecordTier}）。
     */
    public void onMenuOpened(ServerPlayer player) {
        int now = tier;
        if (now >= 1) {
            award(player, GUIDE_TIER1, GUIDE_TIER1_CRITERION);
        }
        if (now >= 2) {
            award(player, GUIDE_TIER2, GUIDE_TIER1_CRITERION);
        }
        if (now >= 3) {
            award(player, GUIDE_TIER3, GUIDE_TIER1_CRITERION);
            award(player, MAIN_PLAYER_PENTACLE, GUIDE_TIER1_CRITERION);
        }
        if (now >= 4) {
            award(player, GUIDE_TIER4, GUIDE_TIER1_CRITERION);
        }
        if (now >= 0 && mayRecordTier(player, now)) {
            CorruptionGate.recordPentacle(player, now);
        }
    }

    /**
     * 这座五芒星能不能给 player 记档位（{@code general.corruption.respect_claims} 关着时都能）：档位只升不降、玩家自己降不回去，
     * 别人的高档五芒星（被骗去打开、之后不让用了）会让人提前抽到自己还治不了的效果。所以只认：
     * <ul>
     *   <li>记下来也不会升的：直接不记，什么都不问；</li>
     *   <li>这座五芒星的主人（{@link ECProtection#onlineOwner}，单人里没记主人的算房主）：不问；</li>
     *   <li>别人：以他的身份问一次五芒星那一格能不能动（{@link ECProtection#check}，有缓存），能动才记——自己的地里、被信任的、野外的都算。
     *       只在档位真会升时才问，每人最多问几次。</li>
     * </ul>
     * 不记档位时界面照样能用来治。
     */
    public boolean mayRecordTier(ServerPlayer player, int now) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || level == null || Math.min(now, ECPentacle.MAX_TIER) <= data.getPentacleTier()) {
            return false;
        }
        if (!CorruptionEffects.respectClaims()) {
            return true;
        }
        ServerPlayer owner = ECProtection.onlineOwner(this);
        if (owner != null && owner.getUUID().equals(player.getUUID())) {
            return true;
        }
        return ECProtection.check(player, level, worldPosition, this) == ECProtection.Verdict.ALLOW;
    }

    // ------------------------------------------------------------------
    // 客户端粒子（原作 update 里 world.isRemote 那段）
    // ------------------------------------------------------------------

    @Override
    protected void clientTick() {
        if (level == null || tier < 0 || innerRotation % PARTICLE_INTERVAL != 0) {
            return;
        }
        // 原作 movement：60 tick 一个来回的三角波，粒子在柱子底座、+2 两处上下走
        int movement = (int) (level.getGameTime() % 60);
        if (movement > 30) {
            movement = 60 - movement;
        }
        for (int t = 0; t <= Math.min(tier, ECPentacle.MAX_TIER); t++) {
            float[] color = tierColor(t);
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(color[0], color[1], color[2]), 1F);
            for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(t)) {
                BlockPos at = worldPosition.offset(offset);
                if (!(level.getBlockEntity(at) instanceof MithrilineCrystalPillarBlockEntity)) {
                    continue;
                }
                double x = at.getX() + 0.5D;
                double z = at.getZ() + 0.5D;
                level.addParticle(dust, x, at.getY() + movement / 30D, z, 0D, 0D, 0D);
                level.addParticle(dust, x, at.getY() + 2D + movement / 30D, z, 0D, 0D, 0D);
            }
        }
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putByte(TAG_TIER, (byte) tier);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 旧数据没有这个键时是 −1；读档后第一个服务端 tick 就重判
        tier = tag.contains(TAG_TIER) ? Math.max(-1, Math.min(ECPentacle.MAX_TIER, tag.getByte(TAG_TIER))) : -1;
        checkTimer = 0;
    }

    /** 符文是 3×3 的方形斜放、转着画（对角 4.24 格），比方块本身大 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-2, 0, -2), worldPosition.offset(3, 1, 3));
    }
}
