package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.NaturalFurnaceMenu;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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

/**
 * 自然熔炉。对应原作 TileFlowerBurner（方块 BlockFlowerBurner，注册名 naturalFurnace）。
 * <p>
 * <b>第四批改造（2026-09-28 起机器不自动挖方块、放方块，policy §1.1、batch4.md §0.0）</b>：
 * 不再到周围去找植物烧，改成烧放进它唯一那个格子里的植物<b>物品</b>。
 * 物品形态的方块在三个方块标签之一里就能放进去（整合包可以改标签）：
 * {@link #FLOWERS}（默认小花 + 高花）、{@link #SAPLINGS}（默认原版树苗标签）、{@link #GRASS}（草、蕨、高草丛、大型蕨）。
 * 同时属于几类的，按原作的判断顺序以最后一类为准（草 &gt; 树苗 &gt; 花）。
 * <p>
 * 没在烧、MRU 没满、格子里有植物时拿走一株点燃：花烧 600 刻、树苗 900 刻、草 150 刻，燃烧期间每刻产出固定的 10 MRU（数值、配置名同原作），
 * 一次只烧一株，烧完什么也不留。满了就不再点新的，正在烧的那株照常烧完；有红石信号时整台停住（燃烧时间也不减）。
 * 漏斗、管道能从任意一面往格子里塞植物（只收植物），不能往外抽。
 * <p>
 * 因为不再碰世界里的任何方块，第三批回补加的「机器主人」一套全部拿掉：没有主人（/setblock、结构、别的模组放的）照常工作，
 * 不探测、不发破坏 / 放置事件，也没有授权表；旧存档里的授权表（NBT 键 {@code grants}）读档时忽略，下次存盘自然消失。
 * 旧存档里「正在烧场景里某株植物」的记录（NBT 键 {@code burning}）读档时作废：那株植物从来没被消耗，还在原地。
 * <p>
 * 与原作不同之处：
 * <ul>
 *   <li>烧的是放进来的物品，不是周围 ±7 格、同一高度的植物；烧完不留枯萎的灌木，也不把底下的草方块变泥土（配置 leave_dead_bush 删了）。</li>
 *   <li>平衡值：放下时随机一次并存盘（见 {@link RandomBalanceBlockEntity}）。</li>
 *   <li>1.12 的「花」「树苗」「草丛」是按方块类加矿辞名判断的，这里改成方块标签。原作界面的燃烧进度按 600 刻画，这里按这一株的总时间画。</li>
 *   <li>某一类的燃烧时间配置成 0 时，那一类放不进来（原作 0 刻 = 烧 1 刻就完，没有意义）。</li>
 * </ul>
 */
public class NaturalFurnaceBlockEntity extends RandomBalanceBlockEntity {

    /** 原作 tileentities.naturalfurnace */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue balance;
        public static ForgeConfigSpec.IntValue mruGenerated;
        public static ForgeConfigSpec.IntValue flowerBurnTime;
        public static ForgeConfigSpec.IntValue saplingBurnTime;
        public static ForgeConfigSpec.IntValue grassBurnTime;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("natural_furnace");
            maxMRU = builder.defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU, 1, Integer.MAX_VALUE);
            balance = builder.comment("Balance of the MRU produced. -1 = random in [0, 2), rolled once when the furnace is placed.",
                            "产出 MRU 的平衡值。-1 表示放下时在 [0, 2) 里随机一次（冰冻、混沌 MRU 的来源，别轻易改）")
                    .defineInRange("balance", RANDOM, -1D, 2D);
            mruGenerated = builder.comment("MRU per tick while burning a plant.", "烧植物时每 tick 的产量")
                    .defineInRange("mru_generated", 10, 0, 1_000_000);
            flowerBurnTime = builder.comment("Ticks one flower burns. 0 = flowers are not accepted.",
                            "一朵花烧多少 tick；0 表示不收花")
                    .defineInRange("ticks_flower", 600, 0, 1_000_000);
            saplingBurnTime = builder.comment("Ticks one sapling burns. 0 = saplings are not accepted.",
                            "一株树苗烧多少 tick；0 表示不收树苗")
                    .defineInRange("ticks_sapling", 900, 0, 1_000_000);
            grassBurnTime = builder.comment("Ticks one grass/fern burns. 0 = grass is not accepted.",
                            "一丛草（蕨）烧多少 tick；0 表示不收草")
                    .defineInRange("ticks_grass", 150, 0, 1_000_000);
            builder.pop();
        }
    }

    public static final TagKey<Block> FLOWERS = ECTags.modBlock("natural_furnace_flowers");
    public static final TagKey<Block> SAPLINGS = ECTags.modBlock("natural_furnace_saplings");
    public static final TagKey<Block> GRASS = ECTags.modBlock("natural_furnace_grass");

    /** 唯一的格子：要烧的植物 */
    public static final int SLOT_PLANT = 0;

    private static final String TAG_BURN = "burn";
    private static final String TAG_BURN_MAX = "burn_max";
    /** 正在烧的那株的物品 id（界面提示用） */
    private static final String TAG_BURN_ITEM = "burn_item";
    /** 旧存档（第四批以前）里「正在烧的场景植物坐标」，读档时认出来作废 */
    public static final String LEGACY_TAG_BURNING = "burning";
    /** 旧存档（第三批 ~ 第四批以前）里的授权表，读档时忽略 */
    public static final String LEGACY_TAG_GRANTS = "grants";

    private int burnTime;
    private int maxBurnTime;
    /** 正在烧的那株植物（界面提示里写它的名字）；没在烧是空气 */
    private Item burningItem = Items.AIR;
    /** 上次告诉客户端的「是否在烧」（客户端靠它决定冒不冒烧植物的火） */
    private boolean syncedBurning;

    public NaturalFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ECGenerators.NATURAL_FURNACE_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 1);
    }

    /** 槽位规则（两端共用）：唯一的格子只收能烧的植物 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_PLANT && burnTicks(stack) > 0;
    }

    /**
     * 这件物品能烧多久；不能烧（不是方块物品、方块不在三个标签里、那一类配置成 0）返回 0。
     * 判断顺序同原作（花、树苗、草依次判断，后面的覆盖前面的）。
     */
    public static int burnTicks(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Block block = Block.byItem(stack.getItem());
        if (block == Blocks.AIR) {
            return 0;
        }
        BlockState state = block.defaultBlockState();
        int ticks = 0;
        if (state.is(FLOWERS)) {
            ticks = ECConfig.get(Config.flowerBurnTime);
        }
        if (state.is(SAPLINGS)) {
            ticks = ECConfig.get(Config.saplingBurnTime);
        }
        if (state.is(GRASS)) {
            ticks = ECConfig.get(Config.grassBurnTime);
        }
        return Math.max(0, ticks);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 唯一的格子放植物，不是绑定宝石槽（产能器不拉 MRU） */
    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    /** 渲染用不到格子里的植物：物品栏只走菜单同步给开着界面的人（第四批框架 F4-15），燃烧状态单独发 */
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
                mru.addMRU(ECConfig.get(Config.mruGenerated), true);
                if (burnTime == 0) {
                    burningItem = Items.AIR;
                }
                level.blockEntityChanged(worldPosition);
            }
            if (burnTime == 0 && mru.getMRU() < mru.getMaxMRU()) {
                ItemStack plant = inventory.getStackInSlot(SLOT_PLANT);
                int ticks = burnTicks(plant);
                if (ticks > 0) {
                    burningItem = plant.getItem();
                    burnTime = ticks;
                    maxBurnTime = ticks;
                    // 一次一株，不留副产物（extractItem 会标记物品栏变了、存盘）
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

    /** 正在烧的植物的物品 id（Item.getId，界面提示用；没在烧是空气 0） */
    public int getBurningItemId() {
        return Item.getId(burningItem);
    }

    /** 正在烧的植物；没在烧是空气（GameTest 用） */
    public Item getBurningItem() {
        return burningItem;
    }

    // ---- 客户端 ----

    /**
     * 机器自身一直冒火冒烟（原作如此）；正在烧植物时（有红石信号时不算）机器顶上多冒一团火和烟。
     * 原作每 tick 机器上两团火十团烟、被烧的植物处一团火两百团烟（EC 自己的粒子），这里换成原版粒子并大幅减量；
     * 植物现在就在机器里烧，所以那团火挪到机器上。
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
        // 授权表（LEGACY_TAG_GRANTS）不再读：存盘时整份重写，旧键自然消失
        if (tag.contains(LEGACY_TAG_BURNING, Tag.TAG_COMPOUND)) {
            // 旧存档正烧着场景里的某株植物：那株植物没被消耗、还在原地，这次燃烧作废（不白给剩下的 MRU）
            burnTime = 0;
            maxBurnTime = 0;
            burningItem = Items.AIR;
            return;
        }
        burnTime = Math.max(0, tag.getInt(TAG_BURN));
        maxBurnTime = Math.max(0, tag.getInt(TAG_BURN_MAX));
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
        return new NaturalFurnaceMenu(id, inventory, this);
    }
}
