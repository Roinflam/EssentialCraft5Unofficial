package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.advancement.StructureFormedTrigger;
import com.flameforge.essentialcraft5.block.ReactorBlock;
import com.flameforge.essentialcraft5.block.ReactorSupportBlock;
import com.flameforge.essentialcraft5.client.render.ArcEffect;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECReactor;
import com.flameforge.essentialcraft5.registry.ECSounds;
import com.flameforge.essentialcraft5.structure.StructureCache;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * MRU 特斯拉反应堆核心。对应原作 TileMRUReactor（209 行）。
 *
 * <h2>行为</h2>
 * <ul>
 *   <li><b>结构</b>（{@link ECReactor#REACTOR_STRUCTURE}）：先试上次成形的那个轴（沿 X / 沿 Z），不成再试另一个（原作先 X 后 Z，L43~134）。
 *       两个轴各一个 {@link StructureCache}：最快 20 刻重算一次、邻格变化标脏、区块没加载时保留上次结论。成形与否翻转时才改方块状态
 *       {@link ReactorBlock#FORMED}；从没成形翻到成形那一次给 16 格内的玩家触发 {@code essentialcraft5:structure_formed}（手册完成条件）。</li>
 *   <li><b>产能</b>：成形就每刻 +50 MRU（上限 10 000），不要燃料、不看红石（原作 L28、L155）。</li>
 *   <li><b>平衡值</b>：每刻 ±0.01，在 0~2 之间来回摆（原作 L156~169，cfgBalance = -1）；配置 {@code balance} 填 0~2 就固定成那个值。
 *       平衡值每刻都在变、又没有界面，所以不同步给客户端（{@link #syncBalanceToClient}）。</li>
 *   <li><b>电怪</b>：每 {@code damage_interval}（10）刻查一次 7×7×7（原作 AABB (x−3, y−3, z−3)~(x+4, y+4, z+4)），
 *       对<b>敌对生物</b>造成 {@code damage}（5）点 {@code essentialcraft5:mru_discharge}（不带攻击者、不带位置）。
 *       原作每刻打 5 点，但受伤无敌帧让它实际每 10 刻才生效一次，秒伤一样（policy §4.3）。被它打死的不掉物品和经验，怪从地上捡走的
 *       东西照掉：装备格里掉落率 &gt;1 的（捡起时被设成必掉）、猪灵背包里的（4b 审查 REA-1）；框架 {@code DeathLootEvents} 按伤害类型标签
 *       {@code no_death_loot} 自动处理。原作没有爆炸，这里也没有。</li>
 * </ul>
 *
 * <h2>打谁（{@link #canDamage}）</h2>
 * 只打 {@link Enemy}（定稿改，改玩法）；另跳过 {@link ECEntityRules#isSpared}（起了名字的、拴着绳的、驯服的等，同太阳棱镜 / 冷凝器 / 线圈）和
 * {@code #forge:bosses}。玩家、被动生物、村民、宠物、盔甲架都不打。<b>不豁免持久生物</b>（和线圈不同）：捡了东西的怪照打，
 * 它捡的东西由框架保留下来掉出（上一节），不会被吞；也不因为背包里有东西就不打（那样捡过一颗金粒的猪灵会永远不挨电）。
 * 不探测领地：伤害不带攻击者，原作打 7³ 内所有生物，贴着领地边建的反应堆会电死邻居栏里没起名的牛羊、领地的动物保护管不到（policy §1.1 第 3、4 条）；只打怪以后没有这个问题，也省掉探测。
 * 每次最多打 {@code max_targets}（32）只（新加的上限：刷怪塔把几百只怪挤进 3 格内时，一次放电不处理几百次死亡）。
 *
 * <h2>和原作不同（除上面两条）</h2>
 * <ul>
 *   <li>原作继承 TileMRUGeneric 的 1 格物品栏（{@code slot0IsBoundGem = false}，漏斗能塞任何东西，方块又不掉物品栏 → 挖掉就吞）：这里 0 格，
 *       不暴露物品 capability，漏斗、管道插不进。</li>
 *   <li>原作两端都跑结构检查和伤害，客户端也「电」生物；这里只在服务端，客户端只冒电弧、放声音（{@link #clientTick}）。</li>
 *   <li>原作每 tick 查实体 → 每 10 刻（P2）。</li>
 * </ul>
 * 没有主人规则（policy §2.3 管「动世界」的机器；反应堆只对敌对生物造成伤害，不挪东西、不改方块）。
 */
public class ReactorBlockEntity extends MRUBlockEntity {

    /** 服务端配置 machines.reactor（原作 tileentities.mrureactor） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruGenerated;
        public static ForgeConfigSpec.DoubleValue balance;
        public static ForgeConfigSpec.BooleanValue damageMobs;
        public static ForgeConfigSpec.DoubleValue damage;
        public static ForgeConfigSpec.IntValue damageInterval;
        public static ForgeConfigSpec.IntValue maxTargets;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("reactor");
            maxMRU = builder.defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU, 1, Integer.MAX_VALUE);
            mruGenerated = builder.comment("MRU generated per tick while the structure is complete.", "结构完整时每刻产出的 MRU")
                    .defineInRange("mru_generated", 50, 0, 1_000_000);
            balance = builder.comment("Balance of the generated MRU; -1 cycles between 0 and 2 by 0.01 per tick.",
                            "产出 MRU 的平衡值；-1 表示每刻变 0.01、在 0 和 2 之间来回摆")
                    .defineInRange("balance", -1D, -1D, 2D);
            damageMobs = builder.comment("Zap hostile mobs within 3 blocks.", "电 3 格内的敌对生物")
                    .define("damage_mobs", true);
            damage = builder.comment("Damage per zap (MRU discharge: ignores armor points, mobs killed by it drop nothing).",
                            "每次电击的伤害（MRU 电弧：无视护甲值，被它打死的生物不掉落）")
                    .defineInRange("damage", 5D, 0D, 1000D);
            damageInterval = builder.comment("Ticks between zaps (the original hit every tick, but invulnerability frames limited it to one hit per 10 ticks).",
                            "两次电击之间隔几刻（原作每刻都打，但受伤无敌帧让它实际 10 刻才生效一次）")
                    .defineInRange("damage_interval", 10, 1, 200);
            maxTargets = builder.comment("Maximum mobs hurt by one zap.", "一次电击最多打几只")
                    .defineInRange("max_targets", 32, 1, 256);
            builder.pop();
        }
    }

    /** 电击范围：以核心为中心各方向 3 格（原作 AABB x−3 ~ x+4） */
    public static final int RANGE = 3;
    /** 平衡值一个来回的半程步数（0.01 一步，0 → 2） */
    public static final int BALANCE_STEPS = 200;
    /** 客户端同时最多几道电弧（原作 {@code size() <= 20} 才补一道 → 最多 21 道） */
    public static final int MAX_ARCS = 21;
    /** 客户端每刻放一次电流声的概率（原作 L174） */
    public static final float SOUND_CHANCE = 0.05F;
    /** 电弧起点：核心顶面中心（相对方块角，原作 {@code new Coord3D(0.5, 1, 0.5)}） */
    private static final Vec3 ARC_START = new Vec3(0.5D, 1D, 0.5D);

    private final StructureCache xAxis = new StructureCache(ECReactor.REACTOR_STRUCTURE);
    private final StructureCache zAxis = new StructureCache(ECReactor.REACTOR_STRUCTURE);
    /** 上次成形的是沿 Z 轴摆的（先查它） */
    private boolean lastAxisZ;
    /** 平衡值正在往上走（原作 cycle） */
    private boolean balanceRising = true;
    /** 距下一次电击还有几刻（只在服务端、不存盘；刚成形时为 0 = 立刻电一次） */
    private int zapTimer;

    /** 客户端：正在画的电弧（相对方块角） */
    private final List<ArcEffect> arcs = new ArrayList<>();

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ECReactor.REACTOR_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 0);
        // 原作构造器 setBalance(0)：新放下的反应堆从 0 开始往上摆
        mru.setBalance(0F);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    /** 平衡值每刻都在变、没有界面也不画出来（F4-15） */
    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    /** 没有界面 */
    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!updateStructure(server, false)) {
            zapTimer = 0;
            return;
        }
        mru.addMRU(ECConfig.get(Config.mruGenerated), true);
        double fixed = ECConfig.get(Config.balance);
        if (fixed < 0D) {
            stepBalance();
        } else {
            mru.setBalance((float) fixed);
        }
        if (ECConfig.get(Config.damageMobs) && --zapTimer <= 0) {
            zapTimer = ECConfig.get(Config.damageInterval);
            zap(server);
        }
    }

    /** 客户端：成形时每刻补一道电弧（最多 {@link #MAX_ARCS} 道，各活 21 刻），5% 概率放一声电流声（原作 L174~187） */
    @Override
    protected void clientTick() {
        ArcEffect.tickAll(arcs);
        if (level == null || !ReactorBlock.isFormed(getBlockState())) {
            arcs.clear();
            return;
        }
        RandomSource random = level.random;
        if (random.nextFloat() < SOUND_CHANCE) {
            level.playLocalSound(worldPosition.getX() + 0.5D, worldPosition.getY() + 1D, worldPosition.getZ() + 0.5D,
                    ECSounds.GEN_ELECTRICITY.get(), SoundSource.BLOCKS, 1F, 1F, false);
        }
        if (arcs.size() < MAX_ARCS) {
            // 原作终点 = 起点 + 三个轴各 MathUtils.randomFloat（两个 [0,1) 相减，±1）
            Vec3 end = ARC_START.add(random.nextFloat() - random.nextFloat(), random.nextFloat() - random.nextFloat(),
                    random.nextFloat() - random.nextFloat());
            arcs.add(new ArcEffect(random, ARC_START, end, ArcEffect.Style.REACTOR));
        }
    }

    /** 客户端正在画的电弧（渲染器用，坐标相对方块角） */
    public List<ArcEffect> arcs() {
        return arcs;
    }

    // ------------------------------------------------------------------
    // 结构
    // ------------------------------------------------------------------

    /** 服务端：结构是否成形（最近一次确定的结论） */
    public boolean formed() {
        return (lastAxisZ ? zAxis : xAxis).formed();
    }

    /** 邻格变化时调：两个轴的缓存都标脏 */
    public void markStructureDirty() {
        xAxis.markDirty();
        zAxis.markDirty();
    }

    /**
     * 按节流规则（{@code force} 为 true 时立即）判定结构，成形与否变了就改方块状态；刚成形时触发手册进度。
     *
     * @return 现在是否成形
     */
    private boolean updateStructure(ServerLevel server, boolean force) {
        boolean formed = checkStructure(server, force);
        BlockState state = getBlockState();
        if (state.hasProperty(ReactorBlock.FORMED) && state.getValue(ReactorBlock.FORMED) != formed) {
            // UPDATE_ALL：两侧的支架（updateShape）跟着改电栏，客户端拿到新状态后换浮球位置、开始 / 停止冒电弧
            server.setBlock(worldPosition, state.setValue(ReactorBlock.FORMED, formed), Block.UPDATE_ALL);
            if (formed) {
                StructureFormedTrigger.triggerNearby(server, worldPosition, ECReactor.REACTOR_STRUCTURE_ID, 0);
            }
        }
        return formed;
    }

    /** 先试上次成形的轴，不成再试另一个；两个轴不可能同时成形（一个要支架的格子另一个要空气） */
    private boolean checkStructure(ServerLevel server, boolean force) {
        check(lastAxisZ ? zAxis : xAxis, server, lastAxisZ ? ECReactor.Z_AXIS : ECReactor.X_AXIS, force);
        if ((lastAxisZ ? zAxis : xAxis).formed()) {
            return true;
        }
        StructureCache other = lastAxisZ ? xAxis : zAxis;
        check(other, server, lastAxisZ ? ECReactor.X_AXIS : ECReactor.Z_AXIS, force);
        if (other.formed()) {
            lastAxisZ = !lastAxisZ;
            return true;
        }
        return false;
    }

    private StructureResult check(StructureCache cache, ServerLevel server, Direction facing, boolean force) {
        return force ? cache.checkNow(server, worldPosition, facing) : cache.check(server, worldPosition, facing);
    }

    /**
     * 空手右键时动作栏写的状态（服务端调）：先立即重判一次结构（右键是玩家手动刷新，不走 20 刻节流），
     * 成形写存量和平衡值；不成形写第一个不对的格子（按支架摆的方向挑轴：东西两侧有支架看 X 轴，南北两侧有看 Z 轴）。
     */
    public Component statusMessage() {
        if (level instanceof ServerLevel server && updateStructure(server, true)) {
            return Component.translatable("message.essentialcraft5.reactor.formed", mru.getMRU(), mru.getMaxMRU(),
                    MRUBalance.describe(mru.getBalance()));
        }
        StructureCache shown = preferZAxis() ? zAxis : xAxis;
        StructurePattern.Mismatch mismatch = shown.mismatch();
        if (mismatch != null) {
            return mismatch.message().copy().withStyle(ChatFormatting.RED);
        }
        String key = shown.status() == StructureResult.UNKNOWN ? "structure.essentialcraft5.unknown" : "structure.essentialcraft5.incomplete";
        return Component.translatable(key).withStyle(ChatFormatting.RED);
    }

    /** 核心东西两侧没有支架、南北两侧有：玩家多半在沿 Z 轴摆 */
    private boolean preferZAxis() {
        if (level == null) {
            return lastAxisZ;
        }
        boolean xSupport = isSupport(worldPosition.east()) || isSupport(worldPosition.west());
        boolean zSupport = isSupport(worldPosition.north()) || isSupport(worldPosition.south());
        return zSupport && !xSupport;
    }

    private boolean isSupport(BlockPos pos) {
        return level != null && level.getBlockState(pos).getBlock() instanceof ReactorSupportBlock;
    }

    // ------------------------------------------------------------------
    // 平衡值
    // ------------------------------------------------------------------

    /** 平衡值走一步（±0.01，到 2 往回、到 0 往上；原作 L156~169）。每刻由 serverTick 调，公开给测试 */
    public void stepBalance() {
        int step = Mth.clamp(Math.round(mru.getBalance() * 100F), 0, BALANCE_STEPS);
        if (balanceRising) {
            step = Math.min(BALANCE_STEPS, step + 1);
            if (step >= BALANCE_STEPS) {
                balanceRising = false;
            }
        } else {
            step = Math.max(0, step - 1);
            if (step <= 0) {
                balanceRising = true;
            }
        }
        mru.setBalance(step / 100F);
    }

    public boolean isBalanceRising() {
        return balanceRising;
    }

    /** 测试用：设定平衡值的走向 */
    public void setBalanceRising(boolean rising) {
        balanceRising = rising;
    }

    // ------------------------------------------------------------------
    // 电怪
    // ------------------------------------------------------------------

    /**
     * 放一次电：7×7×7 里 {@link #canDamage} 的生物各挨 {@code damage} 点 MRU 电弧，最多 {@code max_targets} 只（按实体查询的顺序）。
     * 一次实体查询（{@code Mob} 按类别过滤），不探测领地。每 {@code damage_interval} 刻由 serverTick 调，公开给测试。
     *
     * @return 这次真的受了伤的生物数
     */
    public int zap(ServerLevel server) {
        List<Mob> targets = server.getEntitiesOfClass(Mob.class, new AABB(worldPosition).inflate(RANGE), ReactorBlockEntity::canDamage);
        if (targets.isEmpty()) {
            return 0;
        }
        DamageSource source = ECDamageTypes.mruDischarge(server);
        float damage = (float) ECConfig.get(Config.damage);
        int limit = ECConfig.get(Config.maxTargets);
        int hit = 0;
        for (Mob mob : targets) {
            if (hit >= limit) {
                break;
            }
            if (mob.hurt(source, damage)) {
                hit++;
            }
        }
        return hit;
    }

    /**
     * 反应堆打不打这只生物：活着的敌对生物（{@link Enemy}），不是受保护的生物（{@link ECEntityRules#isSpared}：起名、拴绳、驯服……），
     * 不是 {@code #forge:bosses}（凋灵被补刀会丢下界之星）。玩家一律不打（D21）。持久生物照打（捡走的东西由框架保留，见类注释）。
     */
    public static boolean canDamage(LivingEntity entity) {
        return entity instanceof Enemy
                && !(entity instanceof Player)
                && entity.isAlive()
                && !ECEntityRules.isSpared(entity)
                && !entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    // ------------------------------------------------------------------
    // 存盘与渲染
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        xAxis.save(tag, "structure_x");
        zAxis.save(tag, "structure_z");
        tag.putBoolean("axis_z", lastAxisZ);
        tag.putBoolean("balance_rising", balanceRising);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        xAxis.load(tag, "structure_x");
        zAxis.load(tag, "structure_z");
        lastAxisZ = tag.getBoolean("axis_z");
        balanceRising = !tag.contains("balance_rising") || tag.getBoolean("balance_rising");
    }

    /** 包住浮球（成形时顶到 y+1.6）和电弧（起点核心顶面中心，终点各轴 ±1 再加分叉），别用无限渲染盒 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5D).expandTowards(0D, 1D, 0D);
    }
}
