package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.espe.ESPEAccess;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.menu.MachineData;
import com.flameforge.essentialcraft5.menu.MatrixTunerMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECMachineSounds;
import com.flameforge.essentialcraft5.registry.ECMatrixTuner;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 矩阵调律器（EC5 新机器，第十二批，batch12.md §4.6）：站在它上面的玩家，矩阵损伤每秒被抹掉一点，按点收 ESPE 和 MRU。
 *
 * <h2>每 {@value #INTERVAL} 刻一次（各台按位置错开）</h2>
 * <ol>
 *   <li>自己的 ESPE 没满时，从周围八个位置（{@link #SOURCES}，同附魔升华台、地脉凝聚器：同一高度隔一格一圈）抽 ESPE，只读已加载的方块实体；</li>
 *   <li>找站在顶上的人：方块顶面上 1 × 0.5 × 1 的框里查一次 {@link ServerPlayer}，再按脚的位置筛（脚在这一格的水平范围里、
 *       高度在顶面到顶面 + 0.5 之间），活着、不是旁观、是真玩家（{@link ECProtection#isRealPlayer}，假玩家不算）；</li>
 *   <li>挑一个人：私人模式只认主人；公开模式主人优先，其余按 UUID 排，取第一个还有损伤的。一次只给一个人调；</li>
 *   <li>这次抹掉 n = min(每秒上限, 他的损伤, ⌊ESPE ÷ 每点 ESPE⌋, ⌊MRU ÷ 每点 MRU⌋) 点（{@link #amount}），扣 n 份的 ESPE 和 MRU。
 *       n = 0 时按缺什么给状态，什么都不扣。</li>
 * </ol>
 * 主人照 {@link ECProtection#actingOwner}：记下的主人；没记主人时单人里算房主，专用服上没有主人（私人模式谁都不认，OP 切成公开就能用）。
 *
 * <h2>不做的事</h2>
 * 不碰已经抽中的腐化效果、不碰抽取冷却、不碰五芒星档位、不给生命；只改站在上面那一个人的矩阵损伤，并且只往下减。
 * 不读写世界方块、不加载区块、不发 Forge 事件。对外的 ESPE 只进不出（{@link ESPEAccess#INSERT}），物品格只有手放的绑定宝石，漏斗插不进。
 *
 * <h2>数值（batch12.md §4.6.2，配置 {@code machines.matrix_tuner}）</h2>
 * 每秒最多 60 点：满条 28.8 万要 80 分钟，升华一级攒下的 8 000～5.6 万点要 2.2～15.5 分钟。
 * 每点 0.5 ESPE：灵魂五芒星治一个成本 C 的效果要 C ÷ 0.36 原始 ESPE（3 档减半），而这个效果抽中时扣掉的门槛是 3C 点损伤，
 * 折下来每点约 0.93 ESPE、3 档约 0.46，调律器不比它便宜。每点 10 MRU：60 点 / 秒 = 30 MRU / 刻。
 * 实际速度多半被 ESPE 卡住：8 根恶魔水晶柱每秒约 12 ESPE，稳态约 24 点 / 秒。
 */
public class MatrixTunerBlockEntity extends MRUBlockEntity {

    public static final int SLOT_GEM = 0;
    public static final int SLOTS = 1;

    /** 两次调律之间的刻数（每秒一次） */
    public static final int INTERVAL = 20;
    /** 顶面高度（13/16 格，和方块外形一致） */
    public static final double TOP = 13D / 16D;
    /** 找人的框从顶面往上多高 */
    public static final double REACH = 0.5D;
    private static final double EPS = 1e-6D;

    /** ESPE 源的位置：同一高度、隔一格一圈（四正四斜，同附魔升华台 / 地脉凝聚器） */
    public static final List<Vec3i> SOURCES = List.of(
            new Vec3i(2, 0, 0), new Vec3i(-2, 0, 0), new Vec3i(0, 0, 2), new Vec3i(0, 0, -2),
            new Vec3i(2, 0, 2), new Vec3i(-2, 0, 2), new Vec3i(2, 0, -2), new Vec3i(-2, 0, -2));

    private static final String TAG_PUBLIC = "public";

    /**
     * 干活时的声音和粒子：虚空吸引的低鸣（{@code VOID_DRAW} 0.3），加上从四周汇向调律盘的传送门粒子。
     * 写在本类里、不进 MachineAmbiences（batch12.md §10.2）。
     */
    public static final MachineAmbience.Profile AMBIENCE = MachineAmbience.Profile.builder()
            .loop(ECMachineSounds.VOID_DRAW, 0.3F)
            .spark(5.0F, 1.0D, (level, x, y, z, random) -> {
                // 传送门粒子从「出生点 + 速度向量」飞回出生点：速度向量给成四周的偏移，粒子就汇向调律盘上方
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = 1.2D * (0.6D + random.nextDouble() * 0.4D);
                level.addParticle(ParticleTypes.PORTAL, x, y, z,
                        Math.cos(angle) * distance, random.nextDouble() * 0.6D, Math.sin(angle) * distance);
            })
            .build();

    /** 界面状态。顺序只许在末尾追加：界面按序号读 */
    public enum Status {
        /** 上面没人 */
        IDLE(0xFFFF00),
        /** 这一秒抹掉了一些 */
        WORKING(0x00FF00),
        /** 站着的人没有矩阵损伤（或者刚好抹干净） */
        CLEAN(0x55FFFF),
        /** ESPE 不够抹一点 */
        NO_ESPE(0xFFAA00),
        /** MRU 不够抹一点 */
        NO_MRU(0xFFAA00),
        /** 私人模式，站着的不是主人 */
        NOT_ALLOWED(0xFF0000),
        /** 整台机器被关掉了 */
        DISABLED(0xFF0000);

        public static final Status[] VALUES = values();
        public final int color;

        Status(int color) {
            this.color = color;
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
        }
    }

    public static final class Config {
        public static ForgeConfigSpec.BooleanValue enabled;
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue maxESPE;
        public static ForgeConfigSpec.IntValue damagePerSecond;
        public static ForgeConfigSpec.DoubleValue espePerDamage;
        public static ForgeConfigSpec.IntValue mruPerDamage;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("matrix_tuner");
            enabled = builder.comment("Whether matrix tuners work at all.", "矩阵调律器是否工作")
                    .define("enabled", true);
            maxMRU = builder.comment("MRU capacity.", "MRU 容量")
                    .defineInRange("max_mru", 50_000, 1, 100_000_000);
            maxESPE = builder.comment("ESPE capacity (pulled from the 8 crystal pillar spots around it).",
                            "ESPE 容量（从周围 8 个水晶柱位置抽）")
                    .defineInRange("max_espe", 20_000D, 1D, MachineData.MAX_SYNCED_ESPE);
            damagePerSecond = builder.comment("Most matrix damage removed per second from the player standing on it.",
                            "每秒最多抹掉站在上面的人多少点矩阵损伤")
                    .defineInRange("damage_per_second", 60, 1, 10_000);
            espePerDamage = builder.comment("ESPE paid per point of matrix damage removed.", "每抹掉 1 点矩阵损伤付多少 ESPE")
                    .defineInRange("espe_per_damage", 0.5D, 0D, 100D);
            mruPerDamage = builder.comment("MRU paid per point of matrix damage removed.", "每抹掉 1 点矩阵损伤付多少 MRU")
                    .defineInRange("mru_per_damage", 10, 0, 10_000);
            generatesCorruption = builder.comment("Whether the tuner leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向周围泄漏 MRU")
                    .define("generate_corruption", true);
            maxCorruptionGen = builder.comment("Max MRU leaked per working second (a random amount from 0 to this).",
                            "每工作 1 秒最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 2, 0, 10_000);
            builder.pop();
        }
    }

    /** 公开模式（谁站上去都调）；默认私人（只给主人调） */
    private boolean publicMode;
    private Status status = Status.IDLE;
    /** 正在调的人现在还剩多少损伤（给界面看）；没人在调为 −1 */
    private int remaining = -1;
    /** 上一次调律有没有真的抹掉东西（给声音粒子用） */
    private boolean workedLastCycle;
    /** 调律跑过几次（只在内存里，GameTest 用来对准刻） */
    private int cycles;

    public MatrixTunerBlockEntity(BlockPos pos, BlockState state) {
        super(ECMatrixTuner.MATRIX_TUNER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS,
                () -> ECConfig.get(Config.maxESPE), 0);
    }

    // ------------------------------------------------------------------
    // 槽位
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用）：只有 0 号格，放绑定宝石 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 绑定宝石只许手放（batch12.md §7）：漏斗、管道一格都插不进 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    /** 方块是静态模型、没有渲染器，宝石格只走菜单同步给开着界面的人，不用每次换宝石都给附近所有人发整份 NBT */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected ESPEAccess espeAccess(@Nullable Direction side) {
        return ESPEAccess.INSERT;
    }

    private ESPEStorage espeStorage() {
        ESPEStorage storage = getESPEStorage();
        if (storage == null) {
            throw new IllegalStateException("矩阵调律器没有 ESPE 存储");
        }
        return storage;
    }

    // ------------------------------------------------------------------
    // 工作
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // 各台按位置错开，一片调律器不会挤在同一刻里查实体
        if (Math.floorMod(server.getGameTime() + worldPosition.hashCode(), INTERVAL) != 0) {
            return;
        }
        cycles++;
        workedLastCycle = false;
        if (!ECConfig.get(Config.enabled)) {
            show(Status.DISABLED, -1);
            return;
        }
        ESPEStorage storage = espeStorage();
        if (storage.getESPE() < storage.getMaxESPE()) {
            ESPETransfer.pullFromAround(server, worldPosition, storage, SOURCES);
        }
        tune(server, storage);
    }

    private void tune(ServerLevel server, ESPEStorage storage) {
        List<ServerPlayer> present = occupants(server);
        if (present.isEmpty()) {
            show(Status.IDLE, -1);
            return;
        }
        List<ServerPlayer> allowed = allowed(present, ECProtection.actingOwner(this), publicMode);
        if (allowed.isEmpty()) {
            show(Status.NOT_ALLOWED, -1);
            return;
        }
        ServerPlayer target = null;
        IPlayerData data = null;
        for (ServerPlayer candidate : allowed) {
            IPlayerData candidateData = PlayerDataEvents.get(candidate);
            if (candidateData == null) {
                continue;
            }
            if (data == null) {
                target = candidate;
                data = candidateData;
            }
            if (candidateData.getMatrixDamage() > 0) {
                target = candidate;
                data = candidateData;
                break;
            }
        }
        if (target == null) {
            show(Status.IDLE, -1);
            return;
        }
        int damage = data.getMatrixDamage();
        if (damage <= 0) {
            show(Status.CLEAN, 0);
            return;
        }
        double espePer = ECConfig.get(Config.espePerDamage);
        int mruPer = ECConfig.get(Config.mruPerDamage);
        int n = amount(damage, storage.getESPE(), mru.getMRU(), ECConfig.get(Config.damagePerSecond), espePer, mruPer);
        if (n <= 0) {
            show(espePer > 0D && storage.getESPE() + EPS < espePer ? Status.NO_ESPE : Status.NO_MRU, damage);
            return;
        }
        data.setMatrixDamage(damage - n);
        if (espePer > 0D) {
            storage.extractESPE(n * espePer, true);
        }
        if (mruPer > 0) {
            mru.extractMRU(n * mruPer, true);
        }
        workedLastCycle = true;
        show(damage - n > 0 ? Status.WORKING : Status.CLEAN, damage - n);
        if (ECConfig.get(Config.generatesCorruption)) {
            ECCorruption.randomIncreaseAt(server, worldPosition, server.random, ECConfig.get(Config.maxCorruptionGen));
        }
    }

    /**
     * 这一秒能抹掉几点：min(每秒上限, 损伤, ⌊ESPE ÷ 每点 ESPE⌋, ⌊MRU ÷ 每点 MRU⌋)。每点的价钱是 0 的那一项不设限。
     * 纯函数，GameTest 直接测。
     */
    public static int amount(int damage, double espe, int mruStored, int perSecond, double espePerDamage, int mruPerDamage) {
        long n = Math.min((long) perSecond, (long) damage);
        if (espePerDamage > 0D) {
            // 补一个极小量：存量是 0.5 的倍数时浮点除法偶尔差一点点，别少算一点
            n = Math.min(n, (long) Math.floor(espe / espePerDamage + EPS));
        }
        if (mruPerDamage > 0) {
            n = Math.min(n, mruStored / mruPerDamage);
        }
        return (int) Math.max(0L, n);
    }

    /**
     * 能给谁调，排好先后：私人模式只有主人（主人不在上面就是空表）；公开模式主人在最前，其余按 UUID 排。
     * owner 为 null（专用服上没记主人）时私人模式谁都不认。纯函数。
     */
    public static List<ServerPlayer> allowed(List<ServerPlayer> present, @Nullable UUID owner, boolean publicMode) {
        List<ServerPlayer> result = new ArrayList<>(present.size());
        for (ServerPlayer player : present) {
            if (owner != null && owner.equals(player.getUUID())) {
                result.add(player);
            }
        }
        if (!publicMode) {
            return result;
        }
        List<ServerPlayer> others = new ArrayList<>(present.size());
        for (ServerPlayer player : present) {
            if (owner == null || !owner.equals(player.getUUID())) {
                others.add(player);
            }
        }
        others.sort(Comparator.comparing(ServerPlayer::getUUID));
        result.addAll(others);
        return result;
    }

    /** 站在顶上的真玩家（每秒一次 1 × 0.5 × 1 的实体查询，再按脚的位置筛） */
    private List<ServerPlayer> occupants(ServerLevel server) {
        List<ServerPlayer> candidates;
        List<ServerPlayer> seated = TEST_OCCUPANTS.isEmpty() ? null : TEST_OCCUPANTS.get(GlobalPos.of(server.dimension(), worldPosition));
        if (seated != null) {
            candidates = seated;
        } else {
            double top = worldPosition.getY() + TOP;
            candidates = server.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition.getX(), top - 0.01D, worldPosition.getZ(),
                    worldPosition.getX() + 1D, top + REACH, worldPosition.getZ() + 1D));
        }
        List<ServerPlayer> result = new ArrayList<>(candidates.size());
        for (ServerPlayer player : candidates) {
            if (ECProtection.isRealPlayer(player) && player.level() == server && player.isAlive() && !player.isSpectator()
                    && standsOn(player.getX(), player.getY(), player.getZ())) {
                result.add(player);
            }
        }
        return result;
    }

    /** 脚在这一格的水平范围里、高度在顶面到顶面 + {@value #REACH} 之间（跳起来的那一下也算还站着） */
    private boolean standsOn(double x, double y, double z) {
        double top = worldPosition.getY() + TOP;
        return Math.floor(x) == worldPosition.getX() && Math.floor(z) == worldPosition.getZ()
                && y >= top - 0.01D && y <= top + REACH;
    }

    private void show(Status next, int remainingDamage) {
        status = next;
        remaining = remainingDamage;
    }

    // ------------------------------------------------------------------
    // 私人 / 公开
    // ------------------------------------------------------------------

    public boolean isPublic() {
        return publicMode;
    }

    public void setPublic(boolean value) {
        if (publicMode != value) {
            publicMode = value;
            if (level != null) {
                level.blockEntityChanged(worldPosition);
            }
        }
    }

    /** 谁能切换私人 / 公开：主人（{@link ECProtection#actingOwner}）或 OP（权限 2） */
    public boolean canToggle(Player player) {
        return canToggle(ECProtection.actingOwner(this), player.getUUID(), player.hasPermissions(2));
    }

    /** 同上的纯函数：owner 为 null（专用服上没记主人）时只有 OP 能按 */
    public static boolean canToggle(@Nullable UUID owner, UUID player, boolean op) {
        return op || (owner != null && owner.equals(player));
    }

    // ------------------------------------------------------------------
    // 界面数值（服务端菜单现取）
    // ------------------------------------------------------------------

    public Status status() {
        return status;
    }

    /** 正在调的人还剩多少损伤；没人在调为 −1 */
    public int remaining() {
        return remaining;
    }

    /** 调律跑过几次（只在内存里） */
    public int cycles() {
        return cycles;
    }

    @Override
    protected boolean ambientWorking() {
        return workedLastCycle;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return AMBIENCE;
    }

    // ------------------------------------------------------------------
    // 存盘
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (publicMode) {
            tag.putBoolean(TAG_PUBLIC, true);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        publicMode = tag.getBoolean(TAG_PUBLIC);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MatrixTunerMenu(id, inventory, this);
    }

    // ------------------------------------------------------------------
    // 给测试的
    // ------------------------------------------------------------------

    /** 测试座位：按（维度, 调律器坐标）登记「站在上面的玩家」。GameTest 的测试玩家不在世界实体表里，实体查询找不到它 */
    private static final Map<GlobalPos, List<ServerPlayer>> TEST_OCCUPANTS = new HashMap<>();

    /**
     * 只给 GameTest：这台调律器的实体查询换成「只看这几名玩家」（脚的位置、旁观、真玩家这些规则照查，所以玩家要先挪到顶上）。
     * 传空表或 null 时摘掉。用完一定要摘（失败也只影响这一个坐标）。
     */
    public static void setTestOccupants(ServerLevel level, BlockPos pos, @Nullable List<ServerPlayer> players) {
        GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
        if (players == null || players.isEmpty()) {
            TEST_OCCUPANTS.remove(key);
        } else {
            TEST_OCCUPANTS.put(key, List.copyOf(players));
        }
    }
}
