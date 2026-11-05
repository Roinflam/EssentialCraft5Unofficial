package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.block.AnimalSeparatorBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.AnimalSeparatorMenu;
import com.flameforge.essentialcraft5.registry.ECSeparators;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 魔力动物分离器（幼年 / 成年共用这一个方块实体类型）。对应原作 TileAnimalSeparator（58 行）。
 * <p>
 * 玩家视角：0 号槽放绑定宝石接 MRU，给一下红石（从没有到有），把 {@code radius} 格内的幼年（或成年）动物传到自己头顶，
 * 自己 {@code ignore_radius} 格内的不动，每传一只扣 {@code mru_usage}。牧场分拣：幼崽挪去长大栏、成年的挪去屠宰间。
 *
 * <h2>一次脉冲（{@link #onRedstonePulse}，由 {@code block/RedstonePulse} 在上升沿后的计划刻里调）</h2>
 * <ol>
 *   <li>主人检查（policy §2.3）：没有主人不做；主人不在线（且 {@code require_owner_online} 开着）不做——这次脉冲丢弃、不排队。
 *       MRU 不够一只的也不做。</li>
 *   <li><b>只查一次</b> {@link Animal}（原作的筛选里每个候选再查一次 5 格范围，O(N²)），谓词见 {@link #isCandidate}：
 *       活着、不是 {@link Enemy}（疣猪兽是动物也是敌对生物）、幼年 / 成年对得上、不和忽略范围 {@link #ignoreBox}（至少包住机器正上方一格）相交、不骑乘也没被骑、
 *       过归属关 {@link ECEntityRules#belongsToOther}（有名字、拴绳的、玩家造的傀儡、认主悦灵不动；驯服的只处理主人就是机器主人的，D1 取 B）。
 *       村民、流浪商人不是 {@code Animal}，查不到。再滤掉落点放不下的：按这只动物<b>自己的</b>碰撞箱（骆驼 1.7 宽、2.375 高），
 *       落点固定，按宽高缓存，一种体型只查一次碰撞（{@link FitCache}）。按离机器的距离排序，整份名单留着（{@link #candidates}）。</li>
 *   <li>落点 (x+0.5, y+1.0, z+0.5)，站在机器顶上（原作 y+1.5，只是让它们摔 0.5 格）。目的格（机器正上方）等到第一只放得下的动物
 *       要探测它自己的格子时才 {@code checkAsOwner}（一只都放不下就不探：顶上压着方块时不以主人的名义对那块方块白发破坏事件）：
 *       拒绝就整次结束，探不出来（预算用完）下一刻再问。</li>
 *   <li>由近到远逐只：再过一遍条件和落点；它所在的格子 {@code checkAsOwner} 放行；
 *       扣 {@code mru_usage} → {@code teleportTo} → 停寻路、清摔落。MRU 不够下一只、做满 {@code max_per_pulse} 只、
 *       或者头顶已经挤了 {@code maxEntityCramming - 1} 只生物（见 {@link #crowdLimit}）就停——这次脉冲到此结束，要等下一次红石才接着传。</li>
 *   <li>一次脉冲要探测的格子（目的格 + 最多 16 只动物各自所在的格子）会超过每台机器每刻 16 格的探测预算：
 *       探不出来的（{@code UNKNOWN}）留在窗口 {@link #pending} 里，下一刻由 {@link #continuePulse} 接着做，最长到间隔结束（F4-8）。
 *       窗口最多 {@link #MAX_CANDIDATES} 只；放不下、被拒、不再是候选的丢掉，并从名单往后补一只，
 *       所以近处一大群不能传的（邻居领地里的、个子太高的）挤不掉远处能传的。「每次最多 N 只」数的是实际传了几只。</li>
 * </ol>
 *
 * <h2>和原作不同（报告里的 deviations）</h2>
 * 只传动物（原作 {@code EntityAgeable}，村民、流浪商人也被传走）；不传疣猪兽；按配置值判断 MRU 够不够（原作写死 ≥100、扣配置值）；
 * 落点放不下不传（原作研究书自己写了「没有空间也照传」）；归属关与领地；红石上升沿 + 20 刻（原作有信号期间每次邻居更新都传一批）；
 * 每次最多 16 只；头顶挤满不再传；半径上限 32；忽略范围至少包住机器正上方一格（原作 ignore 配 0 时顶上的动物每次被原地重传、照扣 MRU）。
 */
public class AnimalSeparatorBlockEntity extends MRUBlockEntity implements RedstonePulseReceiver {

    /** 服务端配置 {@code machines.animal_separator}（原作 tileentities.animalseparator 的四项 + 第四批新加的两项） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;
        public static ForgeConfigSpec.DoubleValue radius;
        public static ForgeConfigSpec.DoubleValue ignoreRadius;
        public static ForgeConfigSpec.IntValue maxPerPulse;
        public static ForgeConfigSpec.IntValue pulseInterval;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("animal_separator");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used for each animal moved (capped at max_mru).",
                            "每传送一只动物消耗的 MRU（超过 max_mru 时按 max_mru 算）")
                    .defineInRange("mru_usage", 100, 0, Integer.MAX_VALUE);
            radius = builder.comment("Animals within this many blocks (a cube around the separator) are moved on top of it. Capped at 32 so one pulse stays a cheap entity search.",
                            "把这么多格以内（以机器为中心的立方体）的动物传到它头顶。上限 32：一次脉冲只做一次实体查询，范围太大会卡服")
                    .defineInRange("radius", 24D, 1D, 32D);
            ignoreRadius = builder.comment("Animals within this many blocks of the separator are left alone (never more than radius).",
                            "离机器这么多格以内的动物不动（大于 radius 时按 radius 算）")
                    .defineInRange("ignore_radius", 5D, 0D, 32D);
            maxPerPulse = builder.comment("At most this many animals are moved per redstone pulse.",
                            "每次红石脉冲最多传送几只")
                    .defineInRange("max_per_pulse", 16, 1, 64);
            // 下限 10：一次脉冲查一次 49³（半径 32 时 65³）的实体，policy §1.2 规定 17³ 以上的查询至少隔 10 刻
            pulseInterval = builder.comment("Minimum ticks between two pulses; a pulse arriving earlier waits until then. At least 10: each pulse searches a large area for animals.",
                            "两次脉冲之间至少隔几刻；更早来的脉冲等到那时再执行。至少 10：每次脉冲都要在一大片范围里找动物")
                    .defineInRange("pulse_interval", 20, 10, 1200);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;
    /**
     * 窗口 {@link #pending} 最多同时攥着几只候选（探不出来的等下一刻再探）。名单本身不截断：
     * 窗口里丢掉一只（放不下、被拒、不再是候选）就从 {@link #candidates} 往后补一只（4a 审查 SEP-1：原来按距离截到 256 只，
     * 近处 256 只都不能传时，远处能传的永远轮不到）。
     */
    public static final int MAX_CANDIDATES = 256;

    private final PulseGate gate = new PulseGate(() -> ECConfig.get(Config.pulseInterval));

    // ---- 一次脉冲的进度（不存盘：区块卸载就算这次做完了，同 PulseGate 的续做） ----
    /** 这次脉冲的全部候选，由近到远（已滤掉落点放不下的）；{@link #nextCandidate} 之前的已经进过窗口 */
    private final List<Animal> candidates = new ArrayList<>();
    private int nextCandidate;
    /** 正在处理的窗口：探不出来（UNKNOWN）的留在这里等下一刻，最多 {@link #candidateWindow()} 只 */
    private final List<Animal> pending = new ArrayList<>();
    private boolean destinationUnknown;
    private int movedThisPulse;

    // ---- 只给 GameTest 用的本台覆盖值（不存盘；按台，不影响别的机器和并行的别的测试） ----
    @Nullable
    private AABB testSearchClip;
    private int testMRUUsage = -1;
    private double testIgnoreRadius = -1D;
    private int testCandidateWindow = -1;

    public AnimalSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(ECSeparators.ANIMAL_SEPARATOR_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 1);
    }

    /** 槽位规则（两端共用）：0 号只放绑定宝石（原作用射线塔那套界面） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 渲染用不到物品栏（F4-15）：物品栏只走菜单同步 */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    public PulseGate pulseGate() {
        return gate;
    }

    /** 幼年分离器返回 true，成年分离器返回 false（按方块区分，两种方块共用这个方块实体类型） */
    public boolean separatesChildren() {
        return getBlockState().getBlock() instanceof AnimalSeparatorBlock block && block.separatesChildren();
    }

    // ------------------------------------------------------------------
    // 数值（每次现取配置）
    // ------------------------------------------------------------------

    /** 每只消耗的 MRU（不超过 MRU 上限，否则永远攒不够） */
    public int mruUsage() {
        int usage = testMRUUsage >= 0 ? testMRUUsage : ECConfig.get(Config.mruUsage);
        return Math.max(0, Math.min(usage, mru.getMaxMRU()));
    }

    public double radius() {
        return ECConfig.get(Config.radius);
    }

    /** 忽略半径，不超过 {@link #radius()}（原作 setMaxValue(radius)） */
    public double ignoreRadius() {
        double ignore = testIgnoreRadius >= 0D ? testIgnoreRadius : ECConfig.get(Config.ignoreRadius);
        return Math.min(ignore, radius());
    }

    public int maxPerPulse() {
        return ECConfig.get(Config.maxPerPulse);
    }

    /** 窗口 {@link #pending} 的大小（{@link #MAX_CANDIDATES}，测试可按台压小） */
    private int candidateWindow() {
        return testCandidateWindow > 0 ? testCandidateWindow : MAX_CANDIDATES;
    }

    /** 落点：机器顶面正中（动物站在机器上） */
    public Vec3 destination() {
        return new Vec3(worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D);
    }

    /** 查动物的范围：以机器为中心的立方体（原作 AABB(pos).grow(radius)） */
    public AABB searchBox() {
        AABB box = new AABB(worldPosition).inflate(radius());
        return testSearchClip != null ? box.intersect(testSearchClip) : box;
    }

    /**
     * 不动的范围：和它相交的动物不传（原作 AABB(pos).grow(radiusIgnore)；传上来的动物就在这里面，不会被再传一次）。
     * <p>
     * 至少包住机器正上方那一格（4a 审查 SEP-2）：{@code ignore_radius} 配成 0 时框顶是 y+1，站在机器顶上的动物碰撞箱底也是 y+1，
     * {@link AABB#intersects} 用严格不等号，判成不相交——它们离得最近、排在最前，每次脉冲被原地「传」一遍、照扣 MRU、占掉每次的名额，
     * 头顶的数也被算两遍。站在落点上的动物碰撞箱一定盖住 (x+0.5, y+1 ~ y+1+高, z+0.5)，和这一格相交，骆驼这种宽的也一样。
     * 默认 {@code ignore_radius} 5 时这一格本来就在框里，行为不变。
     */
    public AABB ignoreBox() {
        return new AABB(worldPosition).inflate(ignoreRadius()).minmax(new AABB(worldPosition.above()));
    }

    // ------------------------------------------------------------------
    // 主人状态
    // ------------------------------------------------------------------

    /** 现在能不能干活：主人在线；或者主人不在线但关了 require_owner_online（那时 checkAsOwner 一律放行） */
    public boolean ownerReady() {
        return switch (ECProtection.ownerState(this)) {
            case ONLINE -> true;
            case OFFLINE -> !ECProtection.Config.requireOwnerOnline();
            case NONE -> false;
        };
    }

    /**
     * 界面显示用的主人状态：主人不在线但关了 require_owner_online（照常工作）时当成在线，界面不提示。
     * 菜单同步的是序号 + 1（0 = 还没同步到）。
     */
    public ECProtection.OwnerState displayedOwnerState() {
        ECProtection.OwnerState state = ECProtection.ownerState(this);
        if (state == ECProtection.OwnerState.OFFLINE && !ECProtection.Config.requireOwnerOnline()) {
            return ECProtection.OwnerState.ONLINE;
        }
        return state;
    }

    // ------------------------------------------------------------------
    // 红石脉冲
    // ------------------------------------------------------------------

    @Override
    public boolean onRedstonePulse(ServerLevel level, BlockState state) {
        clearPulse();
        if (!ownerReady()) {
            return false;
        }
        int usage = mruUsage();
        if (mru.getMRU() < usage) {
            return false;
        }
        boolean children = separatesChildren();
        AABB ignore = ignoreBox();
        UUID owner = ECProtection.actingOwner(this);
        List<Animal> found = level.getEntitiesOfClass(Animal.class, searchBox(), animal -> isCandidate(animal, children, ignore, owner));
        // 先滤掉落点放不下的（SEP-1）：一只都放不下时下面直接结束，连目的格也不探（SEP-3）
        FitCache fits = new FitCache(level, destination());
        found.removeIf(animal -> !fits.test(animal));
        if (found.isEmpty()) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(worldPosition);
        found.sort(Comparator.comparingDouble(animal -> animal.distanceToSqr(center)));
        candidates.addAll(found);
        destinationUnknown = true;
        return work(level, fits);
    }

    @Override
    public boolean continuePulse(ServerLevel level, BlockState state) {
        if (!hasPulseWork()) {
            clearPulse();
            return false;
        }
        if (!ownerReady()) {
            // 做到一半主人下线：剩下的算了
            clearPulse();
            return false;
        }
        return work(level, new FitCache(level, destination()));
    }

    /** 这次脉冲还有没处理完的候选（窗口里的，或名单里还没进窗口的） */
    private boolean hasPulseWork() {
        return !pending.isEmpty() || nextCandidate < candidates.size();
    }

    /**
     * 由近到远处理候选：放行的传走；拒绝的、放不下的、不再是候选的丢掉，并从名单往后补一只进窗口；
     * 探不出来的留在窗口 {@link #pending} 里等下一刻（窗口满了就不再往后补）。
     *
     * @param fits 这一刻的落点缓存（落点那一格下一刻可能就被放了方块，所以每刻新建）
     * @return 还有要下一刻再探的
     */
    private boolean work(ServerLevel level, FitCache fits) {
        int usage = mruUsage();
        int limit = maxPerPulse();
        int window = candidateWindow();
        Vec3 dest = destination();
        AABB search = searchBox();
        AABB ignore = ignoreBox();
        boolean children = separatesChildren();
        UUID owner = ECProtection.actingOwner(this);
        int crowdLimit = crowdLimit(level);
        int crowd = crowdLimit > 0 ? crowdAtDestination(level) : 0;
        int i = 0;
        while (true) {
            if (movedThisPulse >= limit || mru.getMRU() < usage || (crowdLimit > 0 && crowd >= crowdLimit)) {
                clearPulse();
                return false;
            }
            if (i >= pending.size()) {
                // 窗口里剩下的都在等下一刻：还有空位就从名单往后补一只
                if (nextCandidate >= candidates.size() || pending.size() >= window) {
                    break;
                }
                pending.add(candidates.get(nextCandidate++));
            }
            Animal animal = pending.get(i);
            // 续做时它可能已经死了、跑出范围、被人拴上、换了维度，落点也可能被堵上了：每次重新过一遍条件
            if (animal.level() != level || !search.intersects(animal.getBoundingBox()) || !isCandidate(animal, children, ignore, owner)
                    || !fits.test(animal)) {
                pending.remove(i);
                continue;
            }
            if (destinationUnknown) {
                // 目的格等到有一只放得下、要探它自己的格子时才探（SEP-3）：顶上压着方块、谁都放不下时，
                // 不以主人的名义对那块真方块白发破坏事件（porting_guide §8.3「只对真正要动的格子探测」）
                ECProtection.Verdict top = ECProtection.checkAsOwner(this, worldPosition.above());
                if (top == ECProtection.Verdict.DENY) {
                    clearPulse();
                    return false;
                }
                if (top == ECProtection.Verdict.UNKNOWN) {
                    return true;
                }
                destinationUnknown = false;
            }
            ECProtection.Verdict verdict = ECProtection.checkAsOwner(this, animal.blockPosition());
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                i++;
                continue;
            }
            pending.remove(i);
            if (verdict == ECProtection.Verdict.DENY) {
                continue;
            }
            if (usage > 0) {
                mru.extractMRU(usage, true);
            }
            animal.teleportTo(dest.x, dest.y, dest.z);
            animal.getNavigation().stop();
            animal.setDeltaMovement(Vec3.ZERO);
            animal.resetFallDistance();
            movedThisPulse++;
            crowd++;
        }
        if (!hasPulseWork()) {
            clearPulse();
            return false;
        }
        return true;
    }

    /**
     * 能不能传（纯内存判断，不探测）：见类注释第 2 步。
     *
     * @param owner 机器实际替谁干活（{@link ECProtection#actingOwner}，单人里没记主人时是房主）：宠物的主人是他的照常处理
     */
    public static boolean isCandidate(Animal animal, boolean children, AABB ignore, @Nullable UUID owner) {
        return animal.isAlive()
                && !(animal instanceof Enemy)
                && animal.isBaby() == children
                && !animal.isPassenger()
                && !animal.isVehicle()
                && !ignore.intersects(animal.getBoundingBox())
                && !ECEntityRules.belongsToOther(animal, owner);
    }

    /**
     * 头顶最多堆几只生物：{@code maxEntityCramming - 1}（默认 23）。原版 {@code LivingEntity.pushEntities} 要一只生物身边挤着
     * <b>多于</b> {@code maxEntityCramming - 1} 只别的生物才掉血，也就是一共 {@code maxEntityCramming + 1} 只（默认 25）；
     * 这里停在 23，再多两只才开始互相挤伤，留了余量。游戏规则关了挤压伤害时返回 0 = 不限。
     */
    private static int crowdLimit(Level level) {
        int cramming = level.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
        return cramming > 0 ? Math.max(1, cramming - 1) : 0;
    }

    /** 落点那一格（机器正上方，两格高）里已经有几只会互相挤的生物 */
    private int crowdAtDestination(Level level) {
        AABB box = new AABB(worldPosition.above()).expandTowards(0D, 1D, 0D);
        return level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity.isAlive() && entity.isPushable() && !entity.isPassenger()).size();
    }

    /**
     * 落点放不放得下（{@code level.noCollision}），按碰撞箱的宽高缓存（SEP-1）：落点固定，放不放得下只看宽高，
     * 一群同种同龄的动物只查一次碰撞，截断、探测之前就能把放不下的整批滤掉。只在同一刻里用（落点那一格下一刻可能就被放了方块）。
     * 落点的框按传过去以后的样子算（{@code EntityDimensions.makeBoundingBox}，和 {@code teleportTo} 之后的碰撞箱一模一样）。
     * 碰撞箱看实体自身状态的方块（脚手架看站得高低，细雪看穿没穿皮靴）按这种体型第一只算。
     */
    private static final class FitCache {
        private final Level level;
        private final Vec3 dest;
        private final Map<Long, Boolean> results = new HashMap<>();

        FitCache(Level level, Vec3 dest) {
            this.level = level;
            this.dest = dest;
        }

        boolean test(Animal animal) {
            float width = animal.getBbWidth();
            float height = animal.getBbHeight();
            long key = ((long) Float.floatToIntBits(width) << 32) | (Float.floatToIntBits(height) & 0xFFFFFFFFL);
            return results.computeIfAbsent(key, k -> level.noCollision(animal, EntityDimensions.scalable(width, height).makeBoundingBox(dest)));
        }
    }

    private void clearPulse() {
        candidates.clear();
        nextCandidate = 0;
        pending.clear();
        destinationUnknown = false;
        movedThisPulse = 0;
    }

    @Override
    protected void serverTick() {
        // 续做被框架停掉（到了间隔结束）以后，别一直攥着那些实体
        if (hasPulseWork() && !gate.isContinuing()) {
            clearPulse();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        clearPulse();
    }

    // ------------------------------------------------------------------
    // 存读档、界面
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        gate.save(tag, "pulse");
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        gate.load(tag, "pulse");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AnimalSeparatorMenu(id, inventory, this);
    }

    // ------------------------------------------------------------------
    // 只给 GameTest 用（按台、不存盘；GameTest 里别用 ConfigValue.set 改真配置）
    // ------------------------------------------------------------------

    /**
     * 查动物的范围再和这个框取交集（传测试结构的范围）：默认 24 格的范围会伸进旁边并行的测试里，把别人的动物传过来。
     * 传 null 取消。
     */
    public void limitSearchForTest(@Nullable AABB clip) {
        this.testSearchClip = clip;
    }

    /** 这台的每只消耗改成 usage（负数取消） */
    public void setMRUUsageForTest(int usage) {
        this.testMRUUsage = usage;
    }

    /** 这台的忽略半径改成 radius（负数取消；仍不超过 {@link #radius()}） */
    public void setIgnoreRadiusForTest(double radius) {
        this.testIgnoreRadius = radius;
    }

    /** 这台的候选窗口压成 size 只（默认 {@link #MAX_CANDIDATES}，测「从名单往后补」时用不着真摆 257 只动物；≤ 0 取消） */
    public void setCandidateWindowForTest(int size) {
        this.testCandidateWindow = size;
    }
}
