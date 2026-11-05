package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.SunkenAltarBlock;
import com.flameforge.essentialcraft5.blockentity.SunkenAltarBlockEntity;
import com.flameforge.essentialcraft5.config.CachedServerValues;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.datagen.ECBlockLoot;
import com.flameforge.essentialcraft5.entity.boss.AbyssTyrantEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 第十三批 13e-S2「沉没城区与渊潮领主」的注册类（batch13.md §5.3.4、§6.4、§9.5；HS §2.4.2、MS §2.4.6）：
 * <ul>
 *   <li>方块 {@link #SUNKEN_ALTAR} 沉城祭坛（方块实体 {@link #SUNKEN_ALTAR_BE}，记这一座上次召唤的游戏日）：不可破坏、活塞推不动、挖掉不掉东西；
 *       手持潮晶右键召渊潮领主（{@code block/SunkenAltarBlock}）；</li>
 *   <li>首领 {@link #ABYSS_TYRANT} 渊潮领主（{@code entity/boss/AbyssTyrantEntity}，Boss 档）与刷怪蛋（只放创造栏，点刷怪笼不改类型）；
 *       在 {@code #forge:bosses}、{@code divider_immune}、{@code mru_coil_immune} 里；</li>
 *   <li>音效（{@code tools/sounds/ecsunkendistrict.json}，都指原版声音改音高）。</li>
 * </ul>
 * 结构类型 {@code sunken_district} 与 3 种拼件在 {@code worldgen/hoanna/sunken/SunkenStructures}（{@code ECHoannaStructures.init()} 调它），
 * 结构 / 结构集 JSON、群系标签 {@code has_structure/sunken_district} 在数据包里。战利品表 {@code chests/sunken_district} 和
 * {@code entities/abyss_tyrant} 都由 {@code tools/gen/hoanna_loot_sunken.py} 出。
 * 文字在 {@code tools/lang/keys/ecsunkendistrict.json}，客户端 {@code client/SunkenDistrictClient}。服务端配置 {@code general.sunken_district}（{@link Config}）。
 * 测试 {@code gametest/SunkenDistrictTests}。
 */
public final class ECSunkenDistrict {

    /** 沉城祭坛：不可破坏（同宝库的谜题方块） */
    public static final RegistryObject<SunkenAltarBlock> SUNKEN_ALTAR = ECBlocks.register("sunken_altar",
            () -> new SunkenAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_NYLIUM).instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.STONE).strength(ECSealedVault.PUZZLE_HARDNESS, ECSealedVault.PUZZLE_RESISTANCE).pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> 8)),
            BlockSpec.of(BlockSpec.cubeAll("block/sunken_altar")).loot(ECBlockLoot::none)
                    // 挖不动的谜题方块：凋灵、末影龙也拆不了，后生成的地物不覆盖（同封印宝库的五种谜题方块）
                    .addBlockTags(BlockTags.WITHER_IMMUNE, BlockTags.DRAGON_IMMUNE, BlockTags.FEATURES_CANNOT_REPLACE));

    public static final RegistryObject<BlockEntityType<SunkenAltarBlockEntity>> SUNKEN_ALTAR_BE =
            ECBlockEntities.type("sunken_altar", SunkenAltarBlockEntity::new, SUNKEN_ALTAR);

    /** 渊潮领主：渊鳗（0.9 × 0.6）× 2.2 的碰撞箱 */
    public static final RegistryObject<EntityType<AbyssTyrantEntity>> ABYSS_TYRANT = ECEntities.register("abyss_tyrant",
            EntityType.Builder.<AbyssTyrantEntity>of(AbyssTyrantEntity::new, MobCategory.MONSTER)
                    .sized(2.0F, 1.3F).clientTrackingRange(10).updateInterval(2));

    public static final RegistryObject<ECEntities.SpawnEgg> ABYSS_TYRANT_SPAWN_EGG =
            ECEntities.spawnEgg("abyss_tyrant_spawn_egg", ABYSS_TYRANT, 0x0E2A3A, 0x3FE0D0);

    public static final RegistryObject<SoundEvent> SOUND_TYRANT_AMBIENT = ECSounds.register("sound.mob.abyss_tyrant.ambient");
    public static final RegistryObject<SoundEvent> SOUND_TYRANT_HURT = ECSounds.register("sound.mob.abyss_tyrant.hurt");
    public static final RegistryObject<SoundEvent> SOUND_TYRANT_DEATH = ECSounds.register("sound.mob.abyss_tyrant.death");
    public static final RegistryObject<SoundEvent> SOUND_TYRANT_BITE = ECSounds.register("sound.mob.abyss_tyrant.bite");
    /** 漩涡（远古守卫者诅咒 0.7，MS §2.4.6） */
    public static final RegistryObject<SoundEvent> SOUND_TYRANT_VORTEX = ECSounds.register("sound.mob.abyss_tyrant.vortex", 32F);
    public static final RegistryObject<SoundEvent> SOUND_ALTAR_SUMMON = ECSounds.register("sound.sunken_altar.summon", 32F);

    private ECSunkenDistrict() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；属性、配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECEntities.attributes(ABYSS_TYRANT, AbyssTyrantEntity::createAttributes);
    }

    /**
     * 服务端配置 {@code general.sunken_district}：召唤材料、领主的属性与招式、每人每日一份。默认值照 batch13.md §5.3.4。
     * 生命、护甲、咬的伤害在召出来时设成基础值（已经在场的不跟着变）；其余每次用时读。
     */
    public static final class Config {

        /**
         * 一组数值。
         *
         * @param tideCrystals   召唤要的潮晶（手里拿着一个，其余从背包里扣；0 = 不要，也不用拿在手里）
         * @param aberrantHearts 召唤要的异变之心（0 = 不要）
         * @param bossClearance  祭坛这么多格内有别的首领（{@code #forge:bosses}）就召不了（0 = 不查）
         * @param maxHealth      领主的最大生命
         * @param armor          护甲
         * @param biteDamage     咬一口的伤害（普通近战，吃护甲）
         * @param perHitCap      单次封顶（0 = 不封）
         * @param pullRange      离祭坛超过这么远的参战者被拽回穹顶（0 = 不拽）
         * @param escapeRange    离祭坛超过这么远脱战
         * @param vortexInterval 两次漩涡之间几刻（0 = 不放漩涡）
         * @param vortexTicks    一次漩涡持续几刻
         * @param vortexRadius   漩涡拉多远以内的参战者
         * @param vortexPull     漩涡每刻往它那里加的速度（格 / 刻）
         * @param dailyLimit     每名玩家每游戏日只领一份领主的奖励（不分哪座沉城）
         */
        public record Values(int tideCrystals, int aberrantHearts, int bossClearance, double maxHealth, double armor, double biteDamage,
                             double perHitCap, double pullRange, double escapeRange, int vortexInterval, int vortexTicks, double vortexRadius,
                             double vortexPull, boolean dailyLimit) {

            /** 同一份数值，换掉召唤材料（测试用） */
            public Values withCost(int crystals, int hearts) {
                return new Values(crystals, hearts, bossClearance, maxHealth, armor, biteDamage, perHitCap, pullRange, escapeRange, vortexInterval,
                        vortexTicks, vortexRadius, vortexPull, dailyLimit);
            }

            /** 同一份数值，换掉每日一份的开关（测试用） */
            public Values withDailyLimit(boolean limit) {
                return new Values(tideCrystals, aberrantHearts, bossClearance, maxHealth, armor, biteDamage, perHitCap, pullRange, escapeRange,
                        vortexInterval, vortexTicks, vortexRadius, vortexPull, limit);
            }
        }

        public static final Values DEFAULTS = new Values(4, 1, 64, 200D, 10D, 14D, AbyssTyrantEntity.PER_HIT_CAP, AbyssTyrantEntity.PULL_RANGE,
                AbyssTyrantEntity.ESCAPE_RANGE, 300, 80, 6D, 0.08D, true);

        private static ForgeConfigSpec.IntValue tideCrystals;
        private static ForgeConfigSpec.IntValue aberrantHearts;
        private static ForgeConfigSpec.IntValue bossClearance;
        private static ForgeConfigSpec.DoubleValue maxHealth;
        private static ForgeConfigSpec.DoubleValue armor;
        private static ForgeConfigSpec.DoubleValue biteDamage;
        private static ForgeConfigSpec.DoubleValue perHitCap;
        private static ForgeConfigSpec.DoubleValue pullRange;
        private static ForgeConfigSpec.DoubleValue escapeRange;
        private static ForgeConfigSpec.IntValue vortexInterval;
        private static ForgeConfigSpec.IntValue vortexTicks;
        private static ForgeConfigSpec.DoubleValue vortexRadius;
        private static ForgeConfigSpec.DoubleValue vortexPull;
        private static ForgeConfigSpec.BooleanValue dailyLimit;

        private static final CachedServerValues<Values> CACHE = new CachedServerValues<>();
        @Nullable
        private static volatile Values testValues;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            Values d = DEFAULTS;
            builder.comment("Sunken District: altar summon and the Abyssal Tidelord.", "沉没城区：沉城祭坛召唤与渊潮领主").push("sunken_district");
            tideCrystals = builder.comment("Tide Crystals a summon costs (hold one, the rest come from the inventory; 0 = none).",
                            "召一次要几个潮晶（手里拿着一个，其余从背包里扣；0 = 不要）")
                    .defineInRange("altar_tide_crystals", d.tideCrystals(), 0, 64);
            aberrantHearts = builder.comment("Aberrant Hearts a summon costs (0 = none).", "召一次要几个异变之心（0 = 不要）")
                    .defineInRange("altar_aberrant_hearts", d.aberrantHearts(), 0, 64);
            bossClearance = builder.comment("No summon while another boss (#forge:bosses) is within this many blocks of the altar (0 = do not check).",
                            "祭坛这么多格内有别的首领（#forge:bosses）时召不了（0 = 不查）")
                    .defineInRange("boss_clearance", d.bossClearance(), 0, 256);
            maxHealth = builder.comment("Tidelord max health (set when summoned).", "渊潮领主的最大生命（召出来时设好）")
                    .defineInRange("max_health", d.maxHealth(), 1D, 4096D);
            armor = builder.comment("Tidelord armor (set when summoned).", "护甲（召出来时设好）")
                    .defineInRange("armor", d.armor(), 0D, 30D);
            biteDamage = builder.comment("Damage of one bite (set when summoned; ordinary melee, armor applies).", "咬一口的伤害（召出来时设好；普通近战，吃护甲）")
                    .defineInRange("bite_damage", d.biteDamage(), 0D, 200D);
            perHitCap = builder.comment("Most damage it takes from one hit (0 = no cap).", "单次最多掉多少血（0 = 不封顶）")
                    .defineInRange("per_hit_cap", d.perHitCap(), 0D, 1024D);
            pullRange = builder.comment("Fighters farther than this from the altar are pulled back into the dome (0 = never pull).",
                            "参战者离祭坛超过这么远就被拽回穹顶（0 = 不拽）")
                    .defineInRange("pull_range", d.pullRange(), 0D, 128D);
            escapeRange = builder.comment("Fighters farther than this from the altar leave the fight.", "离祭坛超过这么远就算脱战")
                    .defineInRange("escape_range", d.escapeRange(), 1D, 256D);
            vortexInterval = builder.comment("Ticks between two vortices (0 = no vortex).", "两次漩涡之间几刻（0 = 不放漩涡）")
                    .defineInRange("vortex_interval", d.vortexInterval(), 0, 12_000);
            vortexTicks = builder.comment("How long one vortex lasts (ticks).", "一次漩涡持续几刻")
                    .defineInRange("vortex_ticks", d.vortexTicks(), 1, 1_200);
            vortexRadius = builder.comment("A vortex pulls fighters in the water within this many blocks.", "漩涡拉多远以内、在水里的参战者")
                    .defineInRange("vortex_radius", d.vortexRadius(), 0D, 32D);
            vortexPull = builder.comment("Speed a vortex adds towards the Tidelord every tick (blocks per tick).", "漩涡每刻往它那里加多少速度（格 / 刻）")
                    .defineInRange("vortex_pull", d.vortexPull(), 0D, 1D);
            dailyLimit = builder.comment("Each player collects the Tidelord's rewards once per game day (whichever sunken district).",
                            "每名玩家每游戏日只领一份渊潮领主的奖励（不分是哪座沉城）")
                    .define("daily_reward_limit", d.dailyLimit());
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testValues;
            if (override != null) {
                return override;
            }
            if (tideCrystals == null) {
                return DEFAULTS;
            }
            return CACHE.get(() -> new Values(ECConfig.get(tideCrystals), ECConfig.get(aberrantHearts), ECConfig.get(bossClearance),
                    ECConfig.get(maxHealth), ECConfig.get(armor), ECConfig.get(biteDamage), ECConfig.get(perHitCap), ECConfig.get(pullRange),
                    ECConfig.get(escapeRange), ECConfig.get(vortexInterval), ECConfig.get(vortexTicks), ECConfig.get(vortexRadius),
                    ECConfig.get(vortexPull), ECConfig.get(dailyLimit)));
        }

        /** 只给 GameTest：在同一段同步代码里设、测、置回 null（别用 ConfigValue.set，文件监视器会冲掉） */
        public static void setTestValues(@Nullable Values values) {
            testValues = values;
        }
    }
}
