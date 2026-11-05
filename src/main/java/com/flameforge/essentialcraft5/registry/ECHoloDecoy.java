package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.HoloDecoyEntity;
import com.flameforge.essentialcraft5.item.HoloDecoyProjectorItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 第十批 10b「全息诱饵」的注册类（tools/plan/batch10.md §5.8.2，原作没有这件东西）：
 * <ul>
 *   <li>物品 {@link #HOLO_DECOY_PROJECTOR}「全息诱饵投影仪」（{@link HoloDecoyProjectorItem}）：自带 20 000 MRU，右键在准星处投出使用者的全息分身，
 *       配方 {@code data/essentialcraft5/recipes/hologram/holo_decoy_projector.json}（加法核心 + 视觉透镜 + 富神经元护板 ×2 + 鲜红灵魂物质），
 *       加法核心只有打倒全息投影才有，所以是第十八章打完 Boss 以后的可选步骤。</li>
 *   <li>实体 {@link #HOLO_DECOY}（{@link HoloDecoyEntity}）：不存盘、不能 /summon；渲染在 client/render/HoloDecoyRenderer（客户端类 client/HoloDecoyClient）。</li>
 *   <li>实体标签 {@link #DECOY_IMMUNE}（{@code data/essentialcraft5/tags/entity_types/decoy_immune.json}）：骗不了的敌对生物，默认只有监守者（它是瞎的，
 *       靠气味和振动找人，看不见投影）；整合包作者可以往里加。</li>
 *   <li>进度 {@link #ADVANCEMENT_DECOY}（{@code guide/hologram/decoy}）：第一次投出诱饵时由代码发。</li>
 *   <li>服务端配置 {@link Config}（{@code general.holo_decoy}）。</li>
 * </ul>
 * 新名字（命名表 tools/id_map_items.json：{@code holo_decoy_projector}，{@code old_id} 为 null；实体不在命名表里，名字键在 tools/lang/keys/echolodecoy.json）。
 * 测试 gametest/HoloDecoyTests。
 */
public final class ECHoloDecoy {

    /**
     * 诱饵实体：和玩家一样大（0.6 × 1.8）、不怕火；不存盘（下线、区块卸载就没了，每次只活几秒）、不能 /summon（没有主人的诱饵什么都不做）。
     * 追踪 4 区块够用（只骗 16 格内的怪）；同步间隔照原版盔甲架这一类不动的活体。
     */
    public static final RegistryObject<EntityType<HoloDecoyEntity>> HOLO_DECOY = ECEntities.register("holo_decoy",
            EntityType.Builder.<HoloDecoyEntity>of(HoloDecoyEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).fireImmune().noSave().noSummon().clientTrackingRange(4).updateInterval(3));

    /** 全息诱饵投影仪：叠 1、手持模型 */
    public static final RegistryObject<HoloDecoyProjectorItem> HOLO_DECOY_PROJECTOR = ECItems.register("holo_decoy_projector",
            () -> new HoloDecoyProjectorItem(new Item.Properties().stacksTo(1)), ItemSpec.handheld("item/tools/holo_decoy_projector"));

    /** 诱饵骗不了的敌对生物（默认监守者） */
    public static final TagKey<EntityType<?>> DECOY_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("decoy_immune"));

    /** 第一次投出诱饵时发的手册进度（{@code data/essentialcraft5/advancements/guide/hologram/decoy.json}，条件 impossible、由代码发） */
    public static final ResourceLocation ADVANCEMENT_DECOY = EssentialCraft5.id("guide/hologram/decoy");
    public static final String ADVANCEMENT_DECOY_CRITERION = "awarded_by_code";

    private ECHoloDecoy() {
    }

    public static void init() {
        ECEntities.attributes(HOLO_DECOY, HoloDecoyEntity::createAttributes);
        ECConfig.general(Config::define);
    }

    /**
     * 服务端配置 {@code general.holo_decoy}（batch10 §5.8.2）。读值走 {@link #values()}（没加载时是默认值）；GameTest 用 {@link #setTestOverrides}
     * 整套换掉，<b>不许用 ConfigValue.set</b>（服务端配置自动存盘、文件监视器后台重读会冲掉刚设的值），只在同一段同步代码里设、测、置回 null。
     */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param mruPerUse     每投一次花的 MRU
         * @param cooldownTicks 投完一次的冷却（刻，0 = 没有冷却）
         * @param lifetimeTicks 诱饵活多少刻
         * @param radius        骗多远以内的怪（格，按到诱饵的距离）
         * @param maxRange      最远投到准星前几格
         * @param bossTicks     Boss（{@code #forge:bosses}）最多被骗几刻，所有诱饵共用、每 max(冷却, 它) 刻一次（0 = Boss 不上当）
         */
        public record Values(int mruPerUse, int cooldownTicks, int lifetimeTicks, int radius, int maxRange, int bossTicks) {
        }

        /** 默认值：每次 5 000 MRU、冷却 30 秒、活 8 秒、骗 16 格内的怪、最远 8 格、Boss 只骗 3 秒 */
        public static final Values DEFAULTS = new Values(5000, 600, 160, 16, 8, 60);

        private static ForgeConfigSpec.IntValue mruPerUse;
        private static ForgeConfigSpec.IntValue cooldownTicks;
        private static ForgeConfigSpec.IntValue lifetimeTicks;
        private static ForgeConfigSpec.IntValue radius;
        private static ForgeConfigSpec.IntValue maxRange;
        private static ForgeConfigSpec.IntValue bossTicks;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Holographic Decoy Projector.", "全息诱饵投影仪").push("holo_decoy");
            mruPerUse = builder.comment("MRU spent per decoy (taken from MRU storages you carry first, then from the projector itself; "
                                    + "the projector holds 20000). Spent only when the decoy appears.",
                            "每投一个诱饵花多少 MRU（先扣身上的储存物品，凑不够再扣投影仪自己存的；投影仪最多存 20000）。诱饵出来了才扣")
                    .defineInRange("mru_per_use", DEFAULTS.mruPerUse(), 0, 1_000_000);
            cooldownTicks = builder.comment("Cooldown after each decoy, in ticks (20 = 1 second), shared by all projectors and kept across relogs. 0 = no cooldown.",
                            "每投一次的冷却（刻，20 = 1 秒），所有投影仪共用，退出重进不清；0 = 没有冷却")
                    .defineInRange("cooldown_ticks", DEFAULTS.cooldownTicks(), 0, 72000);
            lifetimeTicks = builder.comment("How long a decoy lasts, in ticks.", "诱饵存在多少刻")
                    .defineInRange("lifetime_ticks", DEFAULTS.lifetimeTicks(), 20, 6000);
            radius = builder.comment("Hostile mobs within this many blocks of the decoy that are after you (or a teammate) turn on the decoy.",
                            "诱饵周围多少格内、正盯着你（或同队玩家）的敌对生物会改去打诱饵")
                    .defineInRange("radius", DEFAULTS.radius(), 1, 32);
            maxRange = builder.comment("Farthest distance, in blocks, the decoy can be projected in front of you.", "诱饵最远投到准星前多少格")
                    .defineInRange("max_range", DEFAULTS.maxRange(), 1, 16);
            bossTicks = builder.comment("Bosses (#forge:bosses) only fall for decoys for this many ticks; then no one's decoy fools that boss again until max(cooldown_ticks, boss_ticks) ticks after it was first fooled. 0 = bosses ignore decoys.",
                            "Boss（#forge:bosses）最多被骗多少刻；从它上当算起 max(cooldown_ticks, boss_ticks) 刻内谁的诱饵都不再骗它；0 = Boss 不上当")
                    .defineInRange("boss_ticks", DEFAULTS.bossTicks(), 0, 6000);
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (mruPerUse == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(mruPerUse), ECConfig.get(cooldownTicks), ECConfig.get(lifetimeTicks), ECConfig.get(radius),
                    ECConfig.get(maxRange), ECConfig.get(bossTicks));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
