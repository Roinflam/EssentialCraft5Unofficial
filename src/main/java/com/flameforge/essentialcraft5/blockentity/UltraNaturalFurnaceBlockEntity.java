package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.UltraNaturalFurnaceMenu;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECUltraGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * 终极自然熔炉。对应原作 TileUltraFlowerBurner（方块 BlockUltraFlowerBurner，注册名 ultraFlowerBurner）。
 * <p>
 * 烧放进它唯一那个格子里的花、树叶、草、原木<b>物品</b>：物品形态的方块在四个方块标签之一里就能放进去（整合包可以改标签），
 * 没在烧、MRU 没满时拿走一件点燃，按它那一类烧一段时间、每刻产出固定的 MRU，烧完什么也不留。默认数值照原作：
 * <table>
 *   <tr><th>类别（标签）</th><th>燃烧刻数</th><th>每刻 MRU</th><th>一件合计</th></tr>
 *   <tr><td>花 {@link #FLOWERS}（默认 #minecraft:small_flowers）</td><td>600</td><td>60</td><td>3.6 万</td></tr>
 *   <tr><td>树叶 {@link #LEAVES}（默认 #minecraft:leaves）</td><td>300</td><td>70</td><td>2.1 万</td></tr>
 *   <tr><td>草 {@link #GRASS}（默认普通版的草标签 + #minecraft:tall_flowers）</td><td>100</td><td>20</td><td>2000</td></tr>
 *   <tr><td>原木 {@link #LOGS}（默认 #minecraft:logs_that_burn）</td><td>2400</td><td>100</td><td>24 万</td></tr>
 * </table>
 * 同时属于几类的，按原作的判断顺序以最后一类为准（花 → 树叶 → 草 → 原木，见 {@link #resolve}；高花在原作就落在草档）。
 * 每刻产量在点燃时记下（中途改配置不影响正在烧的那件）。满了就不再点新的，正在烧的那件照常烧完；有红石信号时整台停住（燃烧时间也不减）。
 * 漏斗、管道能从任意一面往格子里塞（只收这四类），不能往外抽。
 * <p>
 * 不读不写世界里的方块，不碰实体和掉落物，所以不用主人规则，谁放的、怎么放的都照常工作。
 * <p>
 * 与原作不同之处（removed_redesign D3 = A：机器不自动挖方块、放方块）：
 * <ul>
 *   <li>原作在水平 ±16、往上 0~32 格里随机挑一格，把那里的花、树叶、草或原木（不发事件）烧掉；每 80 刻还把附近的树苗掉落物直接种下。
 *       现在只烧放进格子里的物品，不再碰周围的林子、不种树苗。原作「选定以后不再检查植物还在不在」「烧完删掉那格当时的任何方块」
 *       这些问题随之消失。</li>
 *   <li>原作四类数值写死在代码里、配置读了不用；这里做成真生效的配置，燃烧刻数或每刻产量配成 0 时那一类放不进来。</li>
 *   <li>容量 1 万（原作注册时 setupConfig 把静态的 10 万覆盖成通用产能器容量）；平衡值放下时随机一次并存盘（见 {@link RandomBalanceBlockEntity}）。</li>
 *   <li>界面火苗按这一件的总燃烧时间画（原作按 600 刻画，原木会溢出）。</li>
 * </ul>
 */
public class UltraNaturalFurnaceBlockEntity extends RandomBalanceBlockEntity {

    public static final TagKey<Block> FLOWERS = ECTags.modBlock("ultra_natural_furnace_flowers");
    public static final TagKey<Block> LEAVES = ECTags.modBlock("ultra_natural_furnace_leaves");
    public static final TagKey<Block> GRASS = ECTags.modBlock("ultra_natural_furnace_grass");
    public static final TagKey<Block> LOGS = ECTags.modBlock("ultra_natural_furnace_logs");

    /** 能烧的四类，声明顺序 = 原作的判断顺序（后面的覆盖前面的） */
    public enum Category {
        FLOWER,
        LEAVES,
        GRASS,
        LOG;

        public TagKey<Block> tag() {
            return switch (this) {
                case FLOWER -> FLOWERS;
                case LEAVES -> UltraNaturalFurnaceBlockEntity.LEAVES;
                case GRASS -> UltraNaturalFurnaceBlockEntity.GRASS;
                case LOG -> LOGS;
            };
        }

        /** 一件烧多少刻 */
        public int ticks() {
            int[] override = TEST_OVERRIDES.get(this);
            if (override != null) {
                return override[0];
            }
            return ECConfig.get(switch (this) {
                case FLOWER -> Config.flowerTicks;
                case LEAVES -> Config.leavesTicks;
                case GRASS -> Config.grassTicks;
                case LOG -> Config.logTicks;
            });
        }

        /** 燃烧时每刻产出多少 MRU */
        public int mruPerTick() {
            int[] override = TEST_OVERRIDES.get(this);
            if (override != null) {
                return override[1];
            }
            return ECConfig.get(switch (this) {
                case FLOWER -> Config.flowerMRU;
                case LEAVES -> Config.leavesMRU;
                case GRASS -> Config.grassMRU;
                case LOG -> Config.logMRU;
            });
        }

        /** 燃烧刻数和每刻产量都大于 0 才收这一类 */
        public boolean enabled() {
            return ticks() > 0 && mruPerTick() > 0;
        }
    }

    /** 原作 tileentities.ultranaturalfurnace */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue balance;
        public static ForgeConfigSpec.IntValue flowerTicks;
        public static ForgeConfigSpec.IntValue flowerMRU;
        public static ForgeConfigSpec.IntValue leavesTicks;
        public static ForgeConfigSpec.IntValue leavesMRU;
        public static ForgeConfigSpec.IntValue grassTicks;
        public static ForgeConfigSpec.IntValue grassMRU;
        public static ForgeConfigSpec.IntValue logTicks;
        public static ForgeConfigSpec.IntValue logMRU;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("ultra_natural_furnace");
            maxMRU = builder.defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU, 1, Integer.MAX_VALUE);
            balance = builder.comment("Balance of the MRU produced. -1 = random in [0, 2), rolled once when the furnace is placed.",
                            "产出 MRU 的平衡值。-1 表示放下时在 [0, 2) 里随机一次（冰冻、混沌 MRU 的来源，别轻易改）")
                    .defineInRange("balance", RANDOM, -1D, 2D);
            flowerTicks = ticks(builder, "flower", "flower", "一朵花", 600);
            flowerMRU = mru(builder, "flower", "a flower", "花", 60);
            leavesTicks = ticks(builder, "leaves", "leaf block", "一块树叶", 300);
            leavesMRU = mru(builder, "leaves", "leaves", "树叶", 70);
            grassTicks = ticks(builder, "grass", "grass (or tall flower)", "一丛草（高花算草）", 100);
            grassMRU = mru(builder, "grass", "grass", "草", 20);
            logTicks = ticks(builder, "log", "log", "一根原木", 2400);
            logMRU = mru(builder, "log", "a log", "原木", 100);
            builder.pop();
        }

        private static ForgeConfigSpec.IntValue ticks(ForgeConfigSpec.Builder builder, String key, String en, String zh, int value) {
            return builder.comment("Ticks one " + en + " burns. 0 = not accepted.", zh + "烧多少 tick；0 表示不收这一类")
                    .defineInRange("ticks_" + key, value, 0, 1_000_000);
        }

        private static ForgeConfigSpec.IntValue mru(ForgeConfigSpec.Builder builder, String key, String en, String zh, int value) {
            return builder.comment("MRU per tick while burning " + en + ". 0 = not accepted.", "烧" + zh + "时每 tick 的产量；0 表示不收这一类")
                    .defineInRange("mru_" + key, value, 0, 1_000_000);
        }
    }

    /** GameTest 用的数值覆盖（{燃烧刻数, 每刻产量}）：只许在同一段同步代码里设、测、清掉，免得影响同时在跑的别的测试 */
    private static final Map<Category, int[]> TEST_OVERRIDES = new EnumMap<>(Category.class);

    /** 唯一的格子：要烧的东西 */
    public static final int SLOT_PLANT = 0;

    private static final String TAG_BURN = "burn";
    private static final String TAG_BURN_MAX = "burn_max";
    /** 正在烧的那件每刻产多少（点燃时记下） */
    private static final String TAG_BURN_MRU = "burn_mru";
    /** 正在烧的那件的物品 id（界面提示用） */
    private static final String TAG_BURN_ITEM = "burn_item";

    private int burnTime;
    private int maxBurnTime;
    private int burnMRU;
    /** 正在烧的那件（界面提示里写它的名字）；没在烧是空气 */
    private Item burningItem = Items.AIR;
    /** 上次告诉客户端的「是否在烧」（客户端靠它决定冒不冒烧东西的火） */
    private boolean syncedBurning;

    public UltraNaturalFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ECUltraGenerators.ULTRA_NATURAL_FURNACE_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 1);
    }

    /** 把某一类的燃烧刻数、每刻产量临时改成给定值（GameTest 用，见 {@link #TEST_OVERRIDES}） */
    public static void setTestOverride(Category category, int ticks, int mruPerTick) {
        TEST_OVERRIDES.put(category, new int[]{ticks, mruPerTick});
    }

    public static void clearTestOverrides() {
        TEST_OVERRIDES.clear();
    }

    /**
     * 原作的判断顺序（TileUltraFlowerBurner L117~136）：花、树叶、草、原木依次判断，后面的覆盖前面的。
     *
     * @return 最后一个成立的类别；都不成立返回 null
     */
    public static @Nullable Category resolve(boolean flower, boolean leaves, boolean grass, boolean log) {
        Category category = null;
        if (flower) {
            category = Category.FLOWER;
        }
        if (leaves) {
            category = Category.LEAVES;
        }
        if (grass) {
            category = Category.GRASS;
        }
        if (log) {
            category = Category.LOG;
        }
        return category;
    }

    /** 这件物品属于哪一类；不是方块物品、不在四个标签里、那一类配置成 0 时返回 null */
    public static @Nullable Category categoryOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Block block = Block.byItem(stack.getItem());
        if (block == Blocks.AIR) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        Category category = resolve(state.is(FLOWERS), state.is(LEAVES), state.is(GRASS), state.is(LOGS));
        return category != null && category.enabled() ? category : null;
    }

    /** 槽位规则（两端共用）：唯一的格子只收四类能烧的东西 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_PLANT && categoryOf(stack) != null;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 唯一的格子放要烧的东西，不是绑定宝石槽（产能器不拉 MRU） */
    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    /** 渲染用不到格子里的东西：物品栏只走菜单同步给开着界面的人，燃烧状态单独发（同自然熔炉） */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected void serverTick() {
        applyBalance(ECConfig.get(Config.balance));
        if (level == null) {
            return;
        }
        if (!neighborPowered()) {
            if (burnTime > 0) {
                burnTime--;
                mru.addMRU(burnMRU, true);
                if (burnTime == 0) {
                    burningItem = Items.AIR;
                    burnMRU = 0;
                }
                level.blockEntityChanged(worldPosition);
            }
            if (burnTime == 0 && mru.getMRU() < mru.getMaxMRU()) {
                ItemStack plant = inventory.getStackInSlot(SLOT_PLANT);
                Category category = categoryOf(plant);
                if (category != null) {
                    burningItem = plant.getItem();
                    burnTime = category.ticks();
                    maxBurnTime = burnTime;
                    burnMRU = category.mruPerTick();
                    // 一次一件，不留副产物（extractItem 会标记物品栏变了、存盘）
                    inventory.extractItem(SLOT_PLANT, 1, false);
                    level.blockEntityChanged(worldPosition);
                }
            }
        }
        boolean burning = burnTime > 0;
        if (burning != syncedBurning) {
            syncedBurning = burning;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- 界面数值 ----

    public int getBurnTime() {
        return burnTime;
    }

    public int getMaxBurnTime() {
        return maxBurnTime;
    }

    /** 正在烧的那件每刻产多少；没在烧是 0 */
    public int getBurnMRU() {
        return burnMRU;
    }

    /** 正在烧的那件的物品 id（Item.getId，界面提示用；没在烧是空气 0） */
    public int getBurningItemId() {
        return Item.getId(burningItem);
    }

    /** 正在烧的那件；没在烧是空气（GameTest 用） */
    public Item getBurningItem() {
        return burningItem;
    }

    // ---- 客户端 ----

    /**
     * 机器自身一直冒火冒烟（原作如此）；正在烧东西时（有红石信号时不算）机器顶上多冒一团火和烟。
     * 原作每刻机器上两团火十团烟、被烧的那格一团火，烧完一株放 600 团烟（EC 自己的粒子），这里换成原版粒子并大幅减量，
     * 东西现在就在机器里烧，所以那团火挪到机器上（同自然熔炉）。
     */
    @Override
    protected void clientTick() {
        if (level == null) {
            return;
        }
        RandomSource random = level.random;
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY();
        double z = worldPosition.getZ() + 0.5;
        level.addParticle(ParticleTypes.FLAME, x + (random.nextFloat() - random.nextFloat()) * 0.4, y + 0.1,
                z + (random.nextFloat() - random.nextFloat()) * 0.4, 0, 0.01, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x + (random.nextFloat() - random.nextFloat()) * 0.1, y + 0.6,
                    z + (random.nextFloat() - random.nextFloat()) * 0.1, 0, 0, 0);
        }
        if (burnTime > 0 && !neighborPowered()) {
            level.addParticle(ParticleTypes.FLAME, x + (random.nextFloat() - random.nextFloat()) * 0.2,
                    y + 0.4 + random.nextFloat() * 0.3, z + (random.nextFloat() - random.nextFloat()) * 0.2, 0, 0.01, 0);
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SMOKE, x + (random.nextFloat() - random.nextFloat()) * 0.2,
                        y + 0.7, z + (random.nextFloat() - random.nextFloat()) * 0.2, 0, 0.02, 0);
            }
        }
    }

    // ---- 存盘 ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_BURN, burnTime);
        tag.putInt(TAG_BURN_MAX, maxBurnTime);
        tag.putInt(TAG_BURN_MRU, burnMRU);
        if (burningItem != Items.AIR) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(burningItem);
            if (id != null) {
                tag.putString(TAG_BURN_ITEM, id.toString());
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        burnTime = Math.max(0, tag.getInt(TAG_BURN));
        maxBurnTime = Math.max(0, tag.getInt(TAG_BURN_MAX));
        burnMRU = burnTime > 0 ? Math.max(0, tag.getInt(TAG_BURN_MRU)) : 0;
        Item item = Items.AIR;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_BURN_ITEM));
        if (burnTime > 0 && id != null) {
            Item found = ForgeRegistries.ITEMS.getValue(id);
            if (found != null) {
                item = found;
            }
        }
        burningItem = item;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new UltraNaturalFurnaceMenu(id, inventory, this);
    }
}
