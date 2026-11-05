package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.api.IElementalCrystal.Element;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.ElementalDecomposerMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECDecomposer;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECMachineSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * 元素分解器（EC5 新机器，第十二批 batch12.md §4.8）。
 * <p>
 * 格子：0 绑定宝石；1~9 输入（只收在任一分解标签里的东西）；10~17 输出，按火普通、火罕见、水普通、水罕见、地普通、地罕见、风普通、风罕见排，
 * 每格只放对应那种精华。
 * <p>
 * 每 {@code ticks_per_item}（10）刻：从输入格 1 往后找第一件「元素定得下来、且这种元素的两个输出格都还能再放一颗」的东西 →
 * {@code mru_per_item}（30）MRU 扣得上 → 吃掉 1 件、这种元素点数 +1 → 点数到 {@code points_per_essence}（32）时减掉 32、
 * 这种元素的出货计数 +1，计数是 {@code uncommon_every}（16）的倍数就出 1 颗罕见精华，否则出 1 颗普通精华（确定性的，不随机）。
 * 某种元素的输出格满了只停这一种，别的元素照样分。
 * <p>
 * 元素怎么定：物品标签 {@code decomposer/fire}、{@code /water}、{@code /earth}、{@code /air}，同时在几个里时按火 → 水 → 地 → 风取第一个
 * （{@link #elementOf}）。点数和计数存在方块实体里，拆了机器就没了（掉落物不带 NBT，同其它机器）。
 * <p>
 * 只在自己的格子里进出，不碰世界、不碰实体；每 10 刻最多看 9 格。漏斗、管道只能往输入格塞标签里的东西，只能从输出格往外抽，任何一面都一样。
 */
public class ElementalDecomposerBlockEntity extends MRUBlockEntity {

    /** 配置小节 machines.elemental_decomposer（batch12.md §4.8.4） */
    public static final class Config {
        public static ForgeConfigSpec.BooleanValue enabled;
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue ticksPerItem;
        public static ForgeConfigSpec.IntValue mruPerItem;
        public static ForgeConfigSpec.IntValue pointsPerEssence;
        public static ForgeConfigSpec.IntValue uncommonEvery;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("elemental_decomposer");
            enabled = builder.comment("Whether elemental decomposers work at all.", "元素分解器是否工作")
                    .define("enabled", true);
            maxMRU = builder.comment("MRU capacity.", "MRU 容量")
                    .defineInRange("max_mru", 10_000, 1, 100_000_000);
            ticksPerItem = builder.comment("Ticks between two items (20 ticks = 1 second).", "每隔多少 tick 分解一件（20 tick = 1 秒）")
                    .defineInRange("ticks_per_item", 10, 1, 1200);
            mruPerItem = builder.comment("MRU used per item.", "每分解一件用多少 MRU")
                    .defineInRange("mru_per_item", 30, 0, 1_000_000);
            pointsPerEssence = builder.comment("Element points per essence (one item = one point).", "多少点元素出一颗精华（一件 = 一点）")
                    .defineInRange("points_per_essence", 32, 1, 4096);
            uncommonEvery = builder.comment("Every Nth essence of an element is uncommon instead of common. 0 = never.",
                            "每种元素每出这么多颗，其中最后一颗是罕见精华；0 = 不出罕见")
                    .defineInRange("uncommon_every", 16, 0, 4096);
            generatesCorruption = builder.comment("Whether the decomposer leaks MRU into nearby MRUCUs while working.", "工作时是否向周围泄漏 MRU")
                    .define("generate_corruption", true);
            maxCorruptionGen = builder.comment("Max MRU leaked per 20 working ticks (a random amount from 0 to this).",
                            "每工作 20 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 1, 0, 10_000);
            builder.pop();
        }
    }

    /** 界面上显示的状态（服务端算好，随菜单同步） */
    public enum Status {
        WORKING(0x00FF00),
        NO_INPUT(0xFFFFFF),
        NO_MRU(0xFFFF00),
        /** 有输入，但它们那种元素的输出格都满了 */
        OUTPUT_FULL(0xFFFF00),
        /** 有红石信号 */
        PAUSED(0xFF0000),
        /** enabled = false */
        DISABLED(0xFF0000);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.elemental_decomposer.status." + key());
        }

        public MutableComponent hint() {
            return Component.translatable("gui.essentialcraft5.elemental_decomposer.status." + key() + ".hint");
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NO_INPUT;
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int FIRST_INPUT = 1;
    public static final int INPUTS = 9;
    public static final int FIRST_OUTPUT = FIRST_INPUT + INPUTS;
    public static final int OUTPUTS = 8;
    public static final int SLOTS = FIRST_OUTPUT + OUTPUTS;

    /** 每工作这么多刻泄漏一次 MRU（照其它机器） */
    private static final int LEAK_INTERVAL = 20;

    private static final String TAG_POINTS = "points";
    private static final String TAG_MADE = "made";
    private static final String TAG_TIMER = "timer";
    private static final String TAG_LAST = "last_element";

    private static final Element[] ELEMENTS = Element.values();

    /** 四色尘粒（火红、水蓝、地棕绿、风淡黄），按最近分解的那件的元素挑一种，下标同 {@link Element#ordinal()} */
    private static final float[][] DUST_COLORS = {
            {1.0F, 0.35F, 0.2F},
            {0.3F, 0.55F, 1.0F},
            {0.55F, 0.75F, 0.3F},
            {0.95F, 0.95F, 0.6F}};

    /** 干活时的氛围：地下深处的低沉隆隆声 + 按元素着色、往上飘的尘粒（batch12.md §4.8.3：写在本类，不改 MachineAmbiences） */
    public static final MachineAmbience.Profile[] AMBIENCE = new MachineAmbience.Profile[ELEMENTS.length];

    static {
        for (Element element : ELEMENTS) {
            float[] c = DUST_COLORS[element.ordinal()];
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 0.7F);
            AMBIENCE[element.ordinal()] = MachineAmbience.Profile.builder()
                    .loop(ECMachineSounds.EARTH_RUMBLE, 0.25F)
                    .spark(2.5F, 1.05D, (level, x, y, z, random) -> level.addParticle(dust,
                            x + (random.nextDouble() - 0.5D) * 0.7D, y, z + (random.nextDouble() - 0.5D) * 0.7D, 0.0D, 0.02D, 0.0D))
                    .build();
        }
    }

    private final int[] points = new int[ELEMENTS.length];
    private final int[] made = new int[ELEMENTS.length];
    private int timer;
    private int leakCounter;
    private Status status = Status.NO_INPUT;
    /** 最近分解的那件的元素（尘粒颜色用，随更新包同步） */
    private int lastElement = Element.EARTH.ordinal();

    // ---- GameTest 钩子（−1 = 用配置） ----
    private int testTicksPerItem = -1;
    private int testMruPerItem = -1;

    public ElementalDecomposerBlockEntity(BlockPos pos, BlockState state) {
        super(ECDecomposer.ELEMENTAL_DECOMPOSER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    // ------------------------------------------------------------------
    // 元素与槽位
    // ------------------------------------------------------------------

    /**
     * 一件东西归哪种元素：按火 → 水 → 地 → 风的顺序问「在不在这个标签里」，取第一个；都不在返回 null。
     * 方块实体里传 {@code stack::is}；GameTest 传自己的判断，不临时改标签。
     */
    @Nullable
    public static Element elementOf(Predicate<TagKey<Item>> inTag) {
        for (Element element : ELEMENTS) {
            if (inTag.test(ECDecomposer.tag(element))) {
                return element;
            }
        }
        return null;
    }

    @Nullable
    public static Element elementOf(ItemStack stack) {
        return stack.isEmpty() ? null : elementOf(stack::is);
    }

    /** 某种元素的普通 / 罕见精华放在哪一格 */
    public static int outputSlot(Element element, boolean uncommon) {
        return FIRST_OUTPUT + element.ordinal() * 2 + (uncommon ? 1 : 0);
    }

    /** 槽位规则（两端共用）：0 绑定宝石，输入格只收标签里的东西，输出格什么都不能放 */
    public static boolean slotRule(int slot, ItemStack stack) {
        if (slot == SLOT_GEM) {
            return defaultSlotRule(slot, stack);
        }
        if (slot >= FIRST_INPUT && slot < FIRST_OUTPUT) {
            return elementOf(stack) != null;
        }
        return false;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 漏斗、管道只能往输入格塞（batch12.md §7），绑定宝石格不收 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        return slot >= FIRST_INPUT && slot < FIRST_OUTPUT && slotRule(slot, stack);
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot >= FIRST_OUTPUT && slot < SLOTS;
    }

    /** 没有方块实体渲染，物品栏、平衡值都不发给附近的客户端（干活时每 10 刻就变一次）；界面走菜单同步 */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    // ------------------------------------------------------------------
    // 公式（静态，GameTest 直接调）
    // ------------------------------------------------------------------

    /** 第 count 颗（从 1 数）是不是罕见精华：uncommonEvery 为 0 时永远不是 */
    public static boolean isUncommon(int count, int uncommonEvery) {
        return uncommonEvery > 0 && count > 0 && count % uncommonEvery == 0;
    }

    private int ticksPerItem() {
        return testTicksPerItem >= 0 ? Math.max(1, testTicksPerItem) : Math.max(1, ECConfig.get(Config.ticksPerItem));
    }

    private int mruPerItem() {
        return testMruPerItem >= 0 ? testMruPerItem : Math.max(0, ECConfig.get(Config.mruPerItem));
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (neighborPowered()) {
            status = Status.PAUSED;
            return;
        }
        if (!ECConfig.get(Config.enabled)) {
            status = Status.DISABLED;
            return;
        }
        if (status == Status.WORKING && ++leakCounter >= LEAK_INTERVAL) {
            leakCounter = 0;
            if (ECConfig.get(Config.generatesCorruption)) {
                ECCorruption.randomIncreaseAt(server, worldPosition, server.random, ECConfig.get(Config.maxCorruptionGen));
            }
        }
        if (++timer < ticksPerItem()) {
            return;
        }
        timer = 0;
        // 拿走一件、出一颗精华都会经物品栏标记存盘；空转时不标，免得闲着的机器每 10 刻弄脏一次区块
        processOne();
    }

    /** 分解一件：找第一件能分的，付 MRU，加点数，到数出一颗精华 */
    private void processOne() {
        boolean anyInput = false;
        for (int slot = FIRST_INPUT; slot < FIRST_OUTPUT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            Element element = elementOf(stack);
            if (element == null) {
                continue;
            }
            anyInput = true;
            if (!hasRoom(element)) {
                continue;
            }
            int cost = mruPerItem();
            if (cost > mru.getMRU()) {
                status = Status.NO_MRU;
                return;
            }
            if (cost > 0) {
                mru.extractMRU(cost, true);
            }
            inventory.extractItem(slot, 1, false);
            addPoint(element);
            status = Status.WORKING;
            if (lastElement != element.ordinal()) {
                lastElement = element.ordinal();
                markClientDirty();
            }
            return;
        }
        status = anyInput ? Status.OUTPUT_FULL : Status.NO_INPUT;
    }

    /** 这种元素的普通、罕见两格都还能再放一颗（格里只会是对应那种精华：玩家和漏斗都放不进输出格） */
    private boolean hasRoom(Element element) {
        return roomFor(outputSlot(element, false)) && roomFor(outputSlot(element, true));
    }

    private boolean roomFor(int slot) {
        ItemStack there = inventory.getStackInSlot(slot);
        return there.isEmpty() || there.getCount() < there.getMaxStackSize();
    }

    private void addPoint(Element element) {
        int e = element.ordinal();
        points[e]++;
        int per = Math.max(1, ECConfig.get(Config.pointsPerEssence));
        if (points[e] < per) {
            return;
        }
        points[e] -= per;
        made[e]++;
        boolean uncommon = isUncommon(made[e], ECConfig.get(Config.uncommonEvery));
        Item essence = ECEssence.essence(uncommon ? 1 : 0, element);
        int slot = outputSlot(element, uncommon);
        ItemStack there = inventory.getStackInSlot(slot);
        // 输出格的槽位规则不让任何东西放进来，所以直接改格子；hasRoom 已经保证放得下
        if (there.isEmpty()) {
            inventory.setStackInSlot(slot, new ItemStack(essence));
        } else {
            inventory.setStackInSlot(slot, there.copyWithCount(there.getCount() + 1));
        }
    }

    // ------------------------------------------------------------------
    // 访问（界面数值、GameTest）
    // ------------------------------------------------------------------

    public Status getStatus() {
        return status;
    }

    public int getPoints(Element element) {
        return points[element.ordinal()];
    }

    /** 这种元素已经出了几颗精华（普通 + 罕见） */
    public int getMade(Element element) {
        return made[element.ordinal()];
    }

    public static int pointsPerEssence() {
        return Math.max(1, ECConfig.get(Config.pointsPerEssence));
    }

    /** 只在 GameTest 用：改每件的间隔和价格（−1 = 用配置），测试不改真配置 */
    public void setTestOverrides(int ticksPerItem, int mruPerItem) {
        this.testTicksPerItem = ticksPerItem;
        this.testMruPerItem = mruPerItem;
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray(TAG_POINTS, points.clone());
        tag.putIntArray(TAG_MADE, made.clone());
        if (timer > 0) {
            tag.putInt(TAG_TIMER, timer);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] savedPoints = tag.getIntArray(TAG_POINTS);
        int[] savedMade = tag.getIntArray(TAG_MADE);
        for (int i = 0; i < ELEMENTS.length; i++) {
            points[i] = i < savedPoints.length ? Math.max(0, savedPoints[i]) : 0;
            made[i] = i < savedMade.length ? Math.max(0, savedMade[i]) : 0;
        }
        timer = Math.max(0, tag.getInt(TAG_TIMER));
        // 只有客户端收到的更新包里有这个键
        if (tag.contains(TAG_LAST)) {
            lastElement = Math.max(0, Math.min(ELEMENTS.length - 1, tag.getInt(TAG_LAST)));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        // 客户端用不到点数和计数（界面走菜单同步），只带尘粒颜色
        tag.remove(TAG_POINTS);
        tag.remove(TAG_MADE);
        tag.remove(TAG_TIMER);
        tag.putInt(TAG_LAST, lastElement);
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ElementalDecomposerMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return status == Status.WORKING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return AMBIENCE[lastElement];
    }
}
