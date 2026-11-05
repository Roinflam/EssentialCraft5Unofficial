package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.vault.ElderSteleBlock;
import com.flameforge.essentialcraft5.block.vault.VaultCoreBlock;
import com.flameforge.essentialcraft5.block.vault.VaultElement;
import com.flameforge.essentialcraft5.block.vault.VaultKeyholeBlock;
import com.flameforge.essentialcraft5.block.vault.VaultPedestalBlock;
import com.flameforge.essentialcraft5.block.vault.VaultSealBlock;
import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.datagen.ECBlockLoot;
import com.flameforge.essentialcraft5.datagen.ECBlockStateProvider;
import com.flameforge.essentialcraft5.item.MobMaterialItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第十三批 13e-S1「封印宝库」的注册类（batch13.md §6.4、§9.5；HS §2.4.1）：
 * <ul>
 *   <li>四种谜题方块 {@link #ELDER_STELE} 先辈碑、{@link #VAULT_PEDESTAL} 宝库祭座（状态 {@code element}、{@code filled}）、
 *       {@link #VAULT_KEYHOLE} 宝库锁孔（状态 {@code keyed}）、{@link #VAULT_SEAL} 封印石（状态 {@code open}），和 {@link #VAULT_CORE} 宝库核心
 *       （方块实体 {@link #VAULT_CORE_BE}，整座宝库的谜题、名单都在它里面）：一律不可破坏（硬度 −1、抗爆 3 600 000，同末地传送门框架）、
 *       活塞推不动、挖掉不掉东西，另进 {@code wither_immune}、{@code dragon_immune}、{@code features_cannot_replace}（凋灵、末影龙、后面的地物都动不了）；</li>
 *   <li>物品 {@link #ELDER_REMNANT} 先辈残核（宅男 / 裁决之刃 3 阶的料，宝库核心每人每座必给 1 个）。</li>
 * </ul>
 * 结构类型 {@code sealed_vault} 与 4 种拼件在 {@code worldgen/hoanna/vault/VaultStructures}，布局 {@code VaultLayout}、谜题 {@code VaultPuzzle}；
 * 战利品表 {@code chests/sealed_vault_reward} 由 {@code tools/gen/hoanna_loot_vault.py} 出。文字在 {@code tools/lang/keys/ecsealedvault.json}
 * （碑文 16 句 {@code message.essentialcraft5.vault.riddle.<元素>.<次序>}），音效 {@code tools/sounds/ecsealedvault.json}，
 * 客户端 {@code client/SealedVaultClient}。服务端配置 {@code general.sealed_vault}（{@link Config}）。
 */
public final class ECSealedVault {

    /** 谜题方块的硬度：−1 = 生存模式挖不动（同末地传送门框架） */
    public static final float PUZZLE_HARDNESS = -1F;
    /** 谜题方块的抗爆（同末地传送门框架） */
    public static final float PUZZLE_RESISTANCE = 3_600_000F;

    /** 先辈碑：地面平台上的碑（1 × 1 × 5 由拼件叠放），右键读碑文 */
    public static final RegistryObject<ElderSteleBlock> ELDER_STELE = ECBlocks.register("elder_stele",
            () -> new ElderSteleBlock(puzzle(MapColor.TERRACOTTA_YELLOW).lightLevel(state -> 6)),
            puzzleSpec(BlockSpec.cubeAll("block/vault/elder_stele")));

    /** 宝库祭座：前厅四座，各刻一种元素；放满了亮起来 */
    public static final RegistryObject<VaultPedestalBlock> VAULT_PEDESTAL = ECBlocks.register("vault_pedestal",
            () -> new VaultPedestalBlock(puzzle(MapColor.COLOR_PURPLE).lightLevel(VaultPedestalBlock::lightLevel)),
            puzzleSpec(ECSealedVault::pedestalModel));

    /** 宝库锁孔：前厅中央，每次尝试先放 1 个地脉结晶 */
    public static final RegistryObject<VaultKeyholeBlock> VAULT_KEYHOLE = ECBlocks.register("vault_keyhole",
            () -> new VaultKeyholeBlock(puzzle(MapColor.COLOR_PURPLE).lightLevel(VaultKeyholeBlock::lightLevel)),
            puzzleSpec(ECSealedVault::keyholeModel));

    /** 封印石：封印门的九块，答对时切到 open（没有碰撞） */
    public static final RegistryObject<VaultSealBlock> VAULT_SEAL = ECBlocks.register("vault_seal",
            () -> new VaultSealBlock(puzzle(MapColor.COLOR_BLACK).noOcclusion()),
            puzzleSpec(ECSealedVault::sealModel));

    /** 宝库核心：宝库室中央，名单上的人右键领奖 */
    public static final RegistryObject<VaultCoreBlock> VAULT_CORE = ECBlocks.register("vault_core",
            () -> new VaultCoreBlock(puzzle(MapColor.COLOR_MAGENTA).lightLevel(state -> 10)),
            puzzleSpec(BlockSpec.cubeAll("block/vault/vault_core")));

    public static final RegistryObject<BlockEntityType<VaultCoreBlockEntity>> VAULT_CORE_BE =
            ECBlockEntities.type("vault_core", VaultCoreBlockEntity::new, VAULT_CORE);

    /** 先辈残核：一组 64 */
    public static final RegistryObject<MobMaterialItem> ELDER_REMNANT = ECItems.register("elder_remnant",
            () -> new MobMaterialItem(new Item.Properties().rarity(Rarity.RARE), "elder_remnant"), ItemSpec.generated("item/elder_remnant"));

    /** 往锁孔 / 祭座放东西 */
    public static final RegistryObject<SoundEvent> INSERT = ECSounds.register("sound.sealed_vault.insert");
    /** 封印反噬 */
    public static final RegistryObject<SoundEvent> BACKLASH = ECSounds.register("sound.sealed_vault.backlash");
    /** 答对、封印解开 */
    public static final RegistryObject<SoundEvent> UNLOCK = ECSounds.register("sound.sealed_vault.unlock");
    /** 封印门打开 */
    public static final RegistryObject<SoundEvent> DOOR = ECSounds.register("sound.sealed_vault.door");
    /** 宝库核心给东西 */
    public static final RegistryObject<SoundEvent> CLAIM = ECSounds.register("sound.sealed_vault.claim");

    private ECSealedVault() {
    }

    /** 谜题方块共用的属性：挖不动、炸不掉、推不动、强化石的声音 */
    private static BlockBehaviour.Properties puzzle(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE)
                .strength(PUZZLE_HARDNESS, PUZZLE_RESISTANCE).pushReaction(PushReaction.BLOCK);
    }

    /** 谜题方块的规格：挖掉不掉东西；凋灵、末影龙破坏不了，后面的地物也不覆盖它 */
    private static BlockSpec puzzleSpec(BlockSpec.ModelGen model) {
        return BlockSpec.of(model).loot(ECBlockLoot::none)
                .addBlockTags(BlockTags.WITHER_IMMUNE, BlockTags.DRAGON_IMMUNE, BlockTags.FEATURES_CANNOT_REPLACE);
    }

    /** 祭座：四种元素 × 放没放满，侧面共用一张，顶面按元素（放满了换亮的那张） */
    private static void pedestalModel(ECBlockStateProvider provider, Block block) {
        String name = provider.name(block);
        Map<String, ModelFile> models = new java.util.HashMap<>();
        for (VaultElement element : VaultElement.values()) {
            for (boolean lit : new boolean[]{false, true}) {
                String suffix = "_" + element.getSerializedName() + (lit ? "_lit" : "");
                models.put(suffix, provider.models().cubeBottomTop(name + suffix, provider.tex("block/vault/vault_pedestal"),
                        provider.tex("block/vault/vault_pedestal"), provider.tex("block/vault/vault_pedestal_top" + suffix)));
            }
        }
        provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(models.get("_" + state.getValue(VaultPedestalBlock.ELEMENT).getSerializedName()
                        + (state.getValue(VaultPedestalBlock.FILLED) ? "_lit" : "")))
                .build());
        provider.simpleBlockItem(block, models.get("_fire"));
    }

    /** 锁孔：空的 / 放了结晶 */
    private static void keyholeModel(ECBlockStateProvider provider, Block block) {
        String name = provider.name(block);
        ModelFile empty = provider.models().cubeAll(name, provider.tex("block/vault/vault_keyhole"));
        ModelFile keyed = provider.models().cubeAll(name + "_keyed", provider.tex("block/vault/vault_keyhole_keyed"));
        provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(state.getValue(VaultKeyholeBlock.KEYED) ? keyed : empty).build());
        provider.simpleBlockItem(block, empty);
    }

    /** 封印石：关着是实心的，开着只剩透光的边框（cutout） */
    private static void sealModel(ECBlockStateProvider provider, Block block) {
        String name = provider.name(block);
        ModelFile closed = provider.models().cubeAll(name, provider.tex("block/vault/vault_seal"));
        ModelFile open = provider.models().cubeAll(name + "_open", provider.tex("block/vault/vault_seal_open")).renderType("cutout");
        provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(state.getValue(VaultSealBlock.OPEN) ? open : closed).build());
        provider.simpleBlockItem(block, closed);
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
    }

    /**
     * 服务端配置 {@code general.sealed_vault}：每座祭座放几个、反噬比例、守卫只数与冷却、连错几次封锁、封锁多久、门开多久。
     * GameTest 用 {@link #setTestOverride} 改（别用 ConfigValue.set）。
     */
    public static final class Config {

        public static final int DEFAULT_CRYSTALS_PER_PEDESTAL = 1;
        public static final int MAX_CRYSTALS_PER_PEDESTAL = 16;
        public static final double DEFAULT_BACKLASH_FRACTION = 0.2D;
        public static final int DEFAULT_GUARD_COUNT = 2;
        public static final int MAX_GUARD_COUNT = 8;
        public static final int DEFAULT_GUARD_COOLDOWN_TICKS = 1200;
        public static final int DEFAULT_LOCKOUT_FAILURES = 3;
        public static final int DEFAULT_LOCKOUT_TICKS = 24_000;
        public static final int DEFAULT_DOOR_OPEN_TICKS = 600;

        /** 测试覆盖用的键（配置里的完整路径是 {@code general.sealed_vault.} 加上它） */
        public static final String CRYSTALS_PER_PEDESTAL = "crystals_per_pedestal";
        public static final String BACKLASH_FRACTION = "backlash_fraction";
        public static final String GUARD_COUNT = "guard_count";
        public static final String GUARD_COOLDOWN_TICKS = "guard_cooldown_ticks";
        public static final String LOCKOUT_FAILURES = "lockout_failures";
        public static final String LOCKOUT_TICKS = "lockout_ticks";
        public static final String DOOR_OPEN_TICKS = "door_open_ticks";

        private static final Map<String, Number> TEST = new ConcurrentHashMap<>();

        @Nullable
        private static ForgeConfigSpec.IntValue crystalsPerPedestal;
        @Nullable
        private static ForgeConfigSpec.DoubleValue backlashFraction;
        @Nullable
        private static ForgeConfigSpec.IntValue guardCount;
        @Nullable
        private static ForgeConfigSpec.IntValue guardCooldownTicks;
        @Nullable
        private static ForgeConfigSpec.IntValue lockoutFailures;
        @Nullable
        private static ForgeConfigSpec.IntValue lockoutTicks;
        @Nullable
        private static ForgeConfigSpec.IntValue doorOpenTicks;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Sealed vaults in Hoanna: the riddle puzzle, its backlash and the vault core reward.",
                    "海纳尔的封印宝库：碑文谜题、答错的反噬、宝库核心的奖励").push("sealed_vault");
            crystalsPerPedestal = builder.comment("Elemental crystals each pedestal takes before it counts as done.",
                            "每座祭座要放几个元素魔晶才算放好")
                    .defineInRange(CRYSTALS_PER_PEDESTAL, DEFAULT_CRYSTALS_PER_PEDESTAL, 1, MAX_CRYSTALS_PER_PEDESTAL);
            backlashFraction = builder.comment("Backlash dealt to the player who placed a crystal out of order, as a fraction of their max health. 0 = no damage.",
                            "放错的人受到的封印反噬，按他最大生命的比例；0 = 不受伤")
                    .defineInRange(BACKLASH_FRACTION, DEFAULT_BACKLASH_FRACTION, 0D, 1D);
            guardCount = builder.comment("Catacomb revenants that rise next to the pedestals on a wrong answer. 0 = none.",
                            "答错时从祭座旁召出几只墓穴守灵；0 = 不召")
                    .defineInRange(GUARD_COUNT, DEFAULT_GUARD_COUNT, 0, MAX_GUARD_COUNT);
            guardCooldownTicks = builder.comment("After guards rise, further wrong answers at the same vault only cause backlash for this many ticks.",
                            "召过一次守卫后，这么多刻内同一座宝库再答错只反噬、不召守卫")
                    .defineInRange(GUARD_COOLDOWN_TICKS, DEFAULT_GUARD_COOLDOWN_TICKS, 0, 72_000);
            lockoutFailures = builder.comment("Wrong answers in a row after which the vault refuses that player for a while. 0 = never.",
                            "同一人连错几次后这座宝库暂时不认他；0 = 不封锁")
                    .defineInRange(LOCKOUT_FAILURES, DEFAULT_LOCKOUT_FAILURES, 0, 100);
            lockoutTicks = builder.comment("How long the lockout lasts, in ticks of the Overworld time of day (24000 = one game day).",
                            "封锁多久，按主世界的日内时间算（24000 = 一个游戏日）")
                    .defineInRange(LOCKOUT_TICKS, DEFAULT_LOCKOUT_TICKS, 0, 240_000);
            doorOpenTicks = builder.comment("How long the seal door stays open after the riddle is solved, in ticks.",
                            "答对后封印门开多久（刻）")
                    .defineInRange(DOOR_OPEN_TICKS, DEFAULT_DOOR_OPEN_TICKS, 20, 12_000);
            builder.pop();
        }

        public static int crystalsPerPedestal() {
            return Math.max(1, Math.min(MAX_CRYSTALS_PER_PEDESTAL, i(CRYSTALS_PER_PEDESTAL, crystalsPerPedestal, DEFAULT_CRYSTALS_PER_PEDESTAL)));
        }

        public static double backlashFraction() {
            return Math.max(0D, Math.min(1D, d(BACKLASH_FRACTION, backlashFraction, DEFAULT_BACKLASH_FRACTION)));
        }

        public static int guardCount() {
            return Math.max(0, Math.min(MAX_GUARD_COUNT, i(GUARD_COUNT, guardCount, DEFAULT_GUARD_COUNT)));
        }

        public static int guardCooldownTicks() {
            return Math.max(0, i(GUARD_COOLDOWN_TICKS, guardCooldownTicks, DEFAULT_GUARD_COOLDOWN_TICKS));
        }

        public static int lockoutFailures() {
            return Math.max(0, i(LOCKOUT_FAILURES, lockoutFailures, DEFAULT_LOCKOUT_FAILURES));
        }

        public static int lockoutTicks() {
            return Math.max(0, i(LOCKOUT_TICKS, lockoutTicks, DEFAULT_LOCKOUT_TICKS));
        }

        public static int doorOpenTicks() {
            return Math.max(20, i(DOOR_OPEN_TICKS, doorOpenTicks, DEFAULT_DOOR_OPEN_TICKS));
        }

        /** 测试用：覆盖一个键（上面的常量；null 还原这一个） */
        public static void setTestOverride(String key, @Nullable Number value) {
            if (value == null) {
                TEST.remove(key);
            } else {
                TEST.put(key, value);
            }
        }

        public static void clearTestOverrides() {
            TEST.clear();
        }

        private static double d(String key, @Nullable ForgeConfigSpec.DoubleValue value, double fallback) {
            Number test = TEST.get(key);
            if (test != null) {
                return test.doubleValue();
            }
            return value == null ? fallback : ECConfig.get(value);
        }

        private static int i(String key, @Nullable ForgeConfigSpec.IntValue value, int fallback) {
            Number test = TEST.get(key);
            if (test != null) {
                return test.intValue();
            }
            return value == null ? fallback : ECConfig.get(value);
        }
    }
}
