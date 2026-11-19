package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.ComputerClient;
import com.flameforge.essentialcraft5.client.CurioSealClient;
import com.flameforge.essentialcraft5.compat.curio.CurioSealClientConfig;
import com.flameforge.essentialcraft5.compat.curio.CurioSealClientState;
import com.flameforge.essentialcraft5.compat.curio.CurioSealConfig;
import com.flameforge.essentialcraft5.item.ComputerSealField;
import com.flameforge.essentialcraft5.network.C2SComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.datagen.ECItemModelProvider;
import com.flameforge.essentialcraft5.item.ComputerArmorEvents;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.ComputerBoardItem;
import com.flameforge.essentialcraft5.item.armor.ArmorBonus;
import com.flameforge.essentialcraft5.item.armor.ECArmorMaterial;
import com.flameforge.essentialcraft5.network.C2SComputerArmorPacket;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CBoardFlyingPacket;
import com.flameforge.essentialcraft5.network.S2CCurioSealPacket;
import com.flameforge.essentialcraft5.network.S2CCurioSealSlotPacket;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.loaders.ObjModelBuilder;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 电脑一系（第八批 M2「电脑一系」，计划 tools/plan/batch8.md §4.2）：宅男零件、电脑引擎、宅男套装、宅男反重力板。
 * <p>
 * 框架 8-Fb 先注册富神经元护板和四种数学核心：全息投影台的配方要富神经元护板，全息投影的四张掉落表要四种核心，
 * 电脑引擎也要它们，先注册好几个模块就不用互相等。其余 15 种零件、套装与反重力板接在后面。
 * <p>
 * 新旧注册名（总表见 tools/id_map_items.json，零件都是原作 ItemGenericEC 的子类型 genitem 56~75）：
 * <ul>
 *   <li>genitem 59 neuronicEnrichedPlating → neuronic_enriched_plating（富神经元护板）</li>
 *   <li>genitem 70 / 73 / 72 / 71 additionCore / substructionCore / multiplicationCore / divisionCore
 *       → addition_core / subtraction_core / multiplication_core / division_core（原作 substruction 拼错，改 subtraction）。
 *       原作全息投影四种攻击 0~3（加 = 玩家克隆体、减 = 护甲破坏者、乘 = 轨道打击、除 = 二分场）各掉一种，头顶浮着的核心也按这个对应（RenderHologram L56~78）。</li>
 *   <li>genitem 56~58、60~69、74、75：computerEngine → computer_engine、forceFieldGenerator → force_field_generator、
 *       forceFieldPlating → force_field_plating、pressureStabiliser → pressure_stabilizer、repulsionGenerator → repulsion_generator、
 *       thrusterEngine → thruster_engine、visionLense → vision_lens、speakerPlate → speaker_plate、forcefieldCore → force_field_core、
 *       particleCatcher → particle_catcher、particleEmitter → particle_emitter、forceEmitter → force_emitter、logicCore → logic_core、
 *       forceAbsorber → force_absorber、soundManager → sound_manager（贴图照原作的拼法）。</li>
 *   <li>computer_helmet / computer_chestplate / computer_leggings / computer_boots（宅男套装，原作 ItemComputerArmor）；
 *       computerBoard → computer_board（宅男反重力板，原作 ItemComputerBoard）。</li>
 * </ul>
 * 配方 21 条是静态 JSON（{@code data/essentialcraft5/recipes/computer/}）：形状照 1.10.2 的代码配方（原作 1.12 的 JSON 有三条抄错：
 * 视觉透镜照抄了力场核心的原料、胸甲的 F / S 对调、反重力板的 S 写成了力场护板），产量按 batch8 D4 = A：
 * 五种基础件（力场吸收器、力场发射器、粒子捕捉器、粒子发射器、力场核心）和富神经元护板每次出 4，
 * 五种中间件（力场发生器、力场护板、压力稳定器、斥力发生器、推进引擎）每次出 2，其余照原作。总账由 {@code tools/gen/computer_bom.py} 从配方展开。
 * 数学核心没有配方，只从全息投影掉；零件不进 {@code cores/magic}、{@code plates/magic} 这些代用标签，也没有拆回原料的配方。
 * <p>
 * 套装见 {@link ComputerArmorItem}（属性、挡伤害、耐久）和 {@link ComputerArmorEvents}（四件各自的效果、反伤、夜视与冲刺包）；
 * 反重力板见 {@link ComputerBoardItem}；客户端（按键、HUD、飞板模型）在 client/ComputerClient。
 * 文字 tools/lang/keys/eccomputer.json，标签 tools/tags/eccomputer.json，手册打勾进度 advancements/guide/hologram/neuronic_plating.json、
 * advancements/guide/computer/*.json，进度树终点 advancements/main/computer_armor.json。
 * <p>
 * 第十三批 13a：宅男套装的同调五阶（batch13.md §4.4）见 {@code item/ComputerAttunement}，数在 {@code general.computer_armor} 的
 * {@link Config#attuned()} 那几项；升阶配方 {@code data/essentialcraft5/recipes/attunement/computer_*.json}（§4.5）。
 */
public final class ECComputer {

    /** genitem 59：恶魔板 ×5 + 风之水晶 ×2 + 复合魔力合金 ×2，每次 4 个（原作 2 个） */
    public static final RegistryObject<Item> NEURONIC_ENRICHED_PLATING = ECItems.register("neuronic_enriched_plating",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/neuronicenrichedplating"));

    /** genitem 70：全息投影最后放完「加法」（玩家克隆体）时掉 */
    public static final RegistryObject<Item> ADDITION_CORE = ECItems.register("addition_core",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/additioncore"));

    /** genitem 73：「减法」（护甲破坏者）；原作贴图名照旧拼成 substructioncore */
    public static final RegistryObject<Item> SUBTRACTION_CORE = ECItems.register("subtraction_core",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/substructioncore"));

    /** genitem 72：「乘法」（轨道打击） */
    public static final RegistryObject<Item> MULTIPLICATION_CORE = ECItems.register("multiplication_core",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/multiplicationcore"));

    /** genitem 71：「除法」（二分场） */
    public static final RegistryObject<Item> DIVISION_CORE = ECItems.register("division_core",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/divisioncore"));

    // ------------------------------------------------------------------
    // 零件（按合成的先后排，创造栏也是这个顺序）
    // ------------------------------------------------------------------

    /** genitem 74：恶魔板 ×4 + 米斯莱水晶 ×4 + 风之水晶，每次 4 个 */
    public static final RegistryObject<Item> FORCE_ABSORBER = part("force_absorber", "forceabsorber");
    /** genitem 68：恶魔板 ×4 + 黯淡水晶 ×4 + 风之水晶，每次 4 个 */
    public static final RegistryObject<Item> FORCE_EMITTER = part("force_emitter", "forceemitter");
    /** genitem 66：恶魔板 ×4 + 黯淡水晶 ×4 + 力场吸收器，每次 4 个 */
    public static final RegistryObject<Item> PARTICLE_CATCHER = part("particle_catcher", "particlecatcher");
    /** genitem 67：恶魔板 ×4 + MRU 共鸣水晶 ×4 + 力场发射器，每次 4 个 */
    public static final RegistryObject<Item> PARTICLE_EMITTER = part("particle_emitter", "particleemitter");
    /** genitem 65：淡青板 ×4 + 恶魔核心 ×4 + MRU 反应核，每次 4 个 */
    public static final RegistryObject<Item> FORCE_FIELD_CORE = part("force_field_core", "forcefieldcore");
    /** genitem 69：恶魔板 ×4 + 红石 ×2 + 青金石 ×2 + 石英，每次 1 个 */
    public static final RegistryObject<Item> LOGIC_CORE = part("logic_core", "logiccore");
    /** genitem 57：力场发射器 ×4 + 力场核心 ×4 + 永恒物质，每次 2 个 */
    public static final RegistryObject<Item> FORCE_FIELD_GENERATOR = part("force_field_generator", "forcefieldgenerator");
    /** genitem 58：粒子捕捉器 ×4 + 粒子发射器 ×4 + 力场核心，每次 2 个 */
    public static final RegistryObject<Item> FORCE_FIELD_PLATING = part("force_field_plating", "forcefieldplating");
    /** genitem 60：力场吸收器 ×4 + 富神经元护板 ×5，每次 2 个（原作贴图拼成 stabiliser） */
    public static final RegistryObject<Item> PRESSURE_STABILIZER = part("pressure_stabilizer", "pressurestabiliser");
    /** genitem 61：粒子发射器 ×4 + 力场发射器 ×4 + 富神经元护板，每次 2 个 */
    public static final RegistryObject<Item> REPULSION_GENERATOR = part("repulsion_generator", "repulsiongenerator");
    /** genitem 62：力场发射器 ×4 + 粒子发射器 + 压力稳定器 ×2 + 富神经元护板 ×2，每次 2 个 */
    public static final RegistryObject<Item> THRUSTER_ENGINE = part("thruster_engine", "thrusterengine");
    /** genitem 75：恶魔板 ×4 + 恶魔核心 ×2 + 力场吸收器 ×2 + 压力稳定器，每次 1 个 */
    public static final RegistryObject<Item> SOUND_MANAGER = part("sound_manager", "soundmanager");
    /** genitem 64：力场吸收器 ×4 + 富神经元护板 ×4 + 声音管理器，每次 1 个 */
    public static final RegistryObject<Item> SPEAKER_PLATE = part("speaker_plate", "speakerplate");
    /** genitem 63：富神经元护板 ×4 + 力场吸收器 ×2 + 太阳灌注玻璃 ×2 + 魔力纯化玻璃合金，每次 1 个（原作贴图拼成 lense） */
    public static final RegistryObject<Item> VISION_LENS = part("vision_lens", "visionlense");
    /** genitem 56：富神经元护板 ×4 + 逻辑核心 + 加 / 减 / 乘 / 除四种数学核心，每次 1 个——研究书说的「控制恶魔板的电脑」 */
    public static final RegistryObject<Item> COMPUTER_ENGINE = part("computer_engine", "computerengine");

    // ------------------------------------------------------------------
    // 宅男套装与反重力板
    // ------------------------------------------------------------------

    /**
     * 原作 Computeric（ItemsCore L470：附魔 127，四件都 setMaxDamage 16384），没有修复材料。
     * 护甲值、韧性、击退抗性写的是 {@link Config} 的默认值：套装的属性由 {@link ComputerArmorItem} 按配置给，这三个数只给原版按材料取值的地方用。
     */
    public static final ECArmorMaterial MATERIAL = ECArmorMaterial.builder("computeric")
            .durability(16384, 16384, 16384, 16384)
            .defense(6, 16, 12, 6)
            .enchantability(127)
            .toughness(6F)
            .knockbackResistance(0.2F)
            .build();

    /** 靴子：登高 +0.6 到 1.2 格（原作 PlayerTickHandler L442~450 每刻写 stepHeight、摘下写回 0.6，会冲掉别的模组的登高） */
    public static final ArmorBonus BOOTS_BONUS = ArmorBonus.of()
            .add(ForgeMod.STEP_HEIGHT_ADDITION, 0.6D, AttributeModifier.Operation.ADDITION);

    public static final RegistryObject<ComputerArmorItem> COMPUTER_HELMET = armor("computer_helmet", ArmorItem.Type.HELMET,
            ArmorBonus.none(), Tags.Items.ARMORS_HELMETS);
    public static final RegistryObject<ComputerArmorItem> COMPUTER_CHESTPLATE = armor("computer_chestplate", ArmorItem.Type.CHESTPLATE,
            ArmorBonus.none(), Tags.Items.ARMORS_CHESTPLATES);
    public static final RegistryObject<ComputerArmorItem> COMPUTER_LEGGINGS = armor("computer_leggings", ArmorItem.Type.LEGGINGS,
            ArmorBonus.none(), Tags.Items.ARMORS_LEGGINGS);
    public static final RegistryObject<ComputerArmorItem> COMPUTER_BOOTS = armor("computer_boots", ArmorItem.Type.BOOTS,
            BOOTS_BONUS, Tags.Items.ARMORS_BOOTS);

    /** 飞行时脚下画的板子：models/item/computer_board_flying.json（OBJ board.obj，8-Fb 导入时 MTL 已补贴图），客户端登记成额外模型 */
    public static final ResourceLocation BOARD_MODEL = EssentialCraft5.id("item/computer_board_flying");

    /** 宅男反重力板：Curios 腰带槽，一组 1 个（原作 registerItemSimple(…, 1)） */
    public static final RegistryObject<ComputerBoardItem> COMPUTER_BOARD = ECItems.register("computer_board",
            () -> new ComputerBoardItem(new Item.Properties().stacksTo(1)),
            ItemSpec.of(ECComputer::boardModels).tags(ECTags.CURIOS_BELT));

    /** 进度树终点「全副武装的宅男」 */
    public static final ResourceLocation ADVANCEMENT_COMPUTER_ARMOR = EssentialCraft5.id("main/computer_armor");

    private ECComputer() {
    }

    /** 由 ECRegistries.register() 调用：物品在类加载时登记，这里登记服务端配置、宅男套装的夜视 / 冲刺包和反重力板的飞行状态包 */
    public static void init() {
        ECConfig.general(Config::define);
        ECNetwork.registerC2S(C2SComputerArmorPacket.class, C2SComputerArmorPacket::encode, C2SComputerArmorPacket::decode,
                (packet, player) -> ComputerArmorEvents.handlePacket(player, packet.action()));
        // 排在夜视 / 冲刺包后面；协议号仍是 6（第八批还没发布）。处理器只在客户端调（registerS2C 已套 DistExecutor），
        // lambda 体里的 ComputerClient 到执行时才加载，专用服碰不到
        ECNetwork.registerS2C(S2CBoardFlyingPacket.class, S2CBoardFlyingPacket::encode, S2CBoardFlyingPacket::decode,
                packet -> ComputerClient.onBoardFlyingPacket(packet));
        // 第十批：饰品封禁场。配置 general.computer_seal（压制核心与封禁场共用），压制状态包接在飞行状态包后面（协议 7）；
        // 宅男终端的两个包接在它后面
        ECConfig.general(CurioSealConfig::define);
        ECConfig.client(CurioSealClientConfig::define);
        ECNetwork.registerS2C(S2CCurioSealPacket.class, S2CCurioSealPacket::encode, S2CCurioSealPacket::decode,
                packet -> CurioSealClientState.Client.onPacket(packet));
        // 压制期间真物品变了的单格同步（SealSlotKeeper 发），接在压制状态包后面；第十批还没发布，协议号仍是 7
        ECNetwork.registerS2C(S2CCurioSealSlotPacket.class, S2CCurioSealSlotPacket::encode, S2CCurioSealSlotPacket::decode,
                packet -> CurioSealClientState.Client.onSlotPacket(packet));
        ECNetwork.registerC2S(C2SComputerTerminalPacket.class, C2SComputerTerminalPacket::encode, C2SComputerTerminalPacket::decode,
                (packet, player) -> ComputerSealField.handleTerminalPacket(player, packet));
        ECNetwork.registerS2C(S2CComputerTerminalPacket.class, S2CComputerTerminalPacket::encode, S2CComputerTerminalPacket::decode,
                packet -> CurioSealClient.onTerminalPacket(packet));
    }

    /** 四件，按头 / 胸 / 腿 / 靴 */
    public static List<RegistryObject<ComputerArmorItem>> armor() {
        return List.of(COMPUTER_HELMET, COMPUTER_CHESTPLATE, COMPUTER_LEGGINGS, COMPUTER_BOOTS);
    }

    /** 15 种零件（不含框架注册的富神经元护板和数学核心），按上面的声明顺序 */
    public static List<RegistryObject<Item>> parts() {
        return List.of(FORCE_ABSORBER, FORCE_EMITTER, PARTICLE_CATCHER, PARTICLE_EMITTER, FORCE_FIELD_CORE, LOGIC_CORE,
                FORCE_FIELD_GENERATOR, FORCE_FIELD_PLATING, PRESSURE_STABILIZER, REPULSION_GENERATOR, THRUSTER_ENGINE,
                SOUND_MANAGER, SPEAKER_PLATE, VISION_LENS, COMPUTER_ENGINE);
    }

    private static RegistryObject<Item> part(String name, String texture) {
        return ECItems.register(name, () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/" + texture));
    }

    private static RegistryObject<ComputerArmorItem> armor(String name, ArmorItem.Type type, ArmorBonus bonus, TagKey<Item> forgeTag) {
        return ECItems.register(name, () -> new ComputerArmorItem(type, new Item.Properties(), bonus),
                ItemSpec.generated("item/armor/" + name).tags(forgeTag));
    }

    /**
     * 反重力板的两个模型：物品图标（原作 item/antigravity_board），和飞行时画在脚下的 OBJ（{@code models/item/computer_board_flying.json}，
     * 只给客户端登记成额外模型用，不属于任何物品）。lambda 只在 datagen 时执行。
     */
    private static void boardModels(ECItemModelProvider provider, Item item) {
        provider.generated(item, "item/antigravity_board");
        provider.getBuilder(BOARD_MODEL.getPath())
                .parent(new ModelFile.UncheckedModelFile("minecraft:block/block"))
                .customLoader(ObjModelBuilder::begin)
                .modelLocation(provider.modLoc("models/item/board.obj"))
                .flipV(true)
                .end()
                .texture("particle", provider.tex("block/model/board"));
    }

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    /** 宅男套装有电时挡哪些伤害（{@code general.computer_armor.shield_scope}；带 {@code bypasses_armor} 的一律不挡，由 MRUShield 先排除） */
    public enum ShieldScope {
        /** 所有不无视护甲的伤害（原作 ISpecialArmor 的范围）：荆棘、轨道打击、模组的魔法都挡；原版魔法、冻伤本身无视护甲，照样挡不到 */
        ALL,
        /**
         * 物理和元素（近战、弹射物、火、爆炸、闪电、冰冻——原版冻伤无视护甲，实际挡不到，只有模组带 is_freezing 又不无视护甲的伤害挡得到）；
         * 魔法和既不是物理也不是元素的（轨道打击）不挡。默认（batch8 D5 = B）
         */
        PHYSICAL_ELEMENTAL,
        /** 只挡物理（同虚空套装） */
        PHYSICAL;

        /** 这一下在不在挡的范围里（不管 {@code bypasses_armor}，调用方已排除） */
        public boolean covers(DamageSource source) {
            return switch (this) {
                case ALL -> true;
                // 元素里再排除魔法：法术模组的火球这类带了 is_fire 标签的魔法伤害照样破防，和「魔法不挡」一致
                case PHYSICAL_ELEMENTAL -> ECDamageKinds.isPhysical(source)
                        || (ECDamageKinds.isElemental(source) && !ECDamageKinds.isMagic(source));
                case PHYSICAL -> ECDamageKinds.isPhysical(source);
            };
        }
    }

    /**
     * 服务端配置 {@code general.computer_armor}（套装）与 {@code general.computer_board}（反重力板）。SERVER 配置随进服同步给客户端，
     * 提示框、起跳加速、冲刺冷却圈用的数和服务端一致。改了属性以后，已经穿在身上的要换一次装才更新（原版只在装备变化时重算属性）；
     * 每件的 MRU 容量在物品建出来时取一次，改小以后存量按新容量截断。GameTest 用 {@link #setTestOverrides}，不碰真配置。
     */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param maxMru             每件存多少 MRU（原作 1 000 000）
         * @param mruPerDamage       挡下的伤害每点花多少 MRU（原作 250）
         * @param shieldScope        有电时挡哪些伤害
         * @param armorHelmet        头盔护甲值（胸甲、护腿、靴子同理）
         * @param toughness          每件盔甲韧性
         * @param knockbackResistance 每件击退抗性
         * @param maxHealth          头盔、护腿、靴子每件加的最大生命
         * @param chestplateMaxHealth 胸甲加的最大生命（原作只有胸甲 +40）
         * @param leggingsSpeed      护腿移速（加法，玩家基础 0.1）
         * @param leggingsJump       穿护腿起跳时额外的竖直速度
         * @param helmetSaturation   头盔是否也把饱和度顶满（原作每刻顶满；默认只保持饥饿值满）
         * @param thornsRatio        全套反伤比例
         * @param swimSpeed          全套游泳速度加成（加在 forge:swim_speed 上，基础 1）
         * @param dashStrength       冲刺速度（格 / 刻）
         * @param dashCooldown       冲刺冷却（刻）
         * @param dashMru            每次冲刺花多少 MRU（原作不花）
         * @param boardFlySpeed      反重力板给的飞行速度（原版创造飞行 0.05，原作 0.2）
         */
        public record Values(int maxMru, int mruPerDamage, ShieldScope shieldScope,
                             double armorHelmet, double armorChestplate, double armorLeggings, double armorBoots,
                             double toughness, double knockbackResistance, double maxHealth, double chestplateMaxHealth,
                             double leggingsSpeed, double leggingsJump, boolean helmetSaturation, double thornsRatio,
                             double swimSpeed, double dashStrength, int dashCooldown, int dashMru, double boardFlySpeed) {

            /** 这一件的护甲值 */
            public double armor(ArmorItem.Type type) {
                return switch (type) {
                    case HELMET -> armorHelmet;
                    case CHESTPLATE -> armorChestplate;
                    case LEGGINGS -> armorLeggings;
                    case BOOTS -> armorBoots;
                };
            }

            /** 这一件加的最大生命 */
            public double maxHealth(ArmorItem.Type type) {
                return type == ArmorItem.Type.CHESTPLATE ? chestplateMaxHealth : maxHealth;
            }

            /** 这一件的可配置移速（只有护腿有） */
            public double movementSpeed(ArmorItem.Type type) {
                return type == ArmorItem.Type.LEGGINGS ? leggingsSpeed : 0D;
            }

            /** 拿这一套改几项（GameTest 用） */
            public Builder toBuilder() {
                return new Builder(this);
            }
        }

        /** {@link Values} 的改写器（GameTest 只改要测的几项） */
        public static final class Builder {
            private int maxMru;
            private int mruPerDamage;
            private ShieldScope shieldScope;
            private double armorHelmet;
            private double armorChestplate;
            private double armorLeggings;
            private double armorBoots;
            private double toughness;
            private double knockbackResistance;
            private double maxHealth;
            private double chestplateMaxHealth;
            private double leggingsSpeed;
            private double leggingsJump;
            private boolean helmetSaturation;
            private double thornsRatio;
            private double swimSpeed;
            private double dashStrength;
            private int dashCooldown;
            private int dashMru;
            private double boardFlySpeed;

            private Builder(Values v) {
                maxMru = v.maxMru();
                mruPerDamage = v.mruPerDamage();
                shieldScope = v.shieldScope();
                armorHelmet = v.armorHelmet();
                armorChestplate = v.armorChestplate();
                armorLeggings = v.armorLeggings();
                armorBoots = v.armorBoots();
                toughness = v.toughness();
                knockbackResistance = v.knockbackResistance();
                maxHealth = v.maxHealth();
                chestplateMaxHealth = v.chestplateMaxHealth();
                leggingsSpeed = v.leggingsSpeed();
                leggingsJump = v.leggingsJump();
                helmetSaturation = v.helmetSaturation();
                thornsRatio = v.thornsRatio();
                swimSpeed = v.swimSpeed();
                dashStrength = v.dashStrength();
                dashCooldown = v.dashCooldown();
                dashMru = v.dashMru();
                boardFlySpeed = v.boardFlySpeed();
            }

            public Builder mruPerDamage(int value) {
                mruPerDamage = value;
                return this;
            }

            public Builder shieldScope(ShieldScope value) {
                shieldScope = value;
                return this;
            }

            /** 四件护甲值、韧性、击退抗性、生命一起改 */
            public Builder stats(double helmet, double chestplate, double leggings, double boots, double toughnessValue,
                                 double knockback, double health, double chestHealth, double speed) {
                armorHelmet = helmet;
                armorChestplate = chestplate;
                armorLeggings = leggings;
                armorBoots = boots;
                toughness = toughnessValue;
                knockbackResistance = knockback;
                maxHealth = health;
                chestplateMaxHealth = chestHealth;
                leggingsSpeed = speed;
                return this;
            }

            public Builder helmetSaturation(boolean value) {
                helmetSaturation = value;
                return this;
            }

            public Builder thornsRatio(double value) {
                thornsRatio = value;
                return this;
            }

            public Builder swimSpeed(double value) {
                swimSpeed = value;
                return this;
            }

            public Builder dash(double strength, int cooldown, int mru) {
                dashStrength = strength;
                dashCooldown = cooldown;
                dashMru = mru;
                return this;
            }

            public Builder boardFlySpeed(double value) {
                boardFlySpeed = value;
                return this;
            }

            public Values build() {
                return new Values(maxMru, mruPerDamage, shieldScope, armorHelmet, armorChestplate, armorLeggings, armorBoots,
                        toughness, knockbackResistance, maxHealth, chestplateMaxHealth, leggingsSpeed, leggingsJump, helmetSaturation,
                        thornsRatio, swimSpeed, dashStrength, dashCooldown, dashMru, boardFlySpeed);
            }
        }

        /**
         * 默认值（batch8 §6.5，D5 = B、D6 = A）：护甲同虚空套装（3 / 8 / 6 / 3 的两倍、每件韧性 6、击退抗性 0.2），
         * 每件 +20 生命、胸甲 +40；护腿移速 +0.075（原作 +0.15）、起跳 +0.3；头盔不加饱和度；反伤 50%；游泳 +2（约 3 倍）；
         * 冲刺 3 格 / 刻、冷却 20 刻、不花 MRU；反重力板飞行速度 0.1（原作 0.2）
         */
        public static final Values DEFAULTS = new Values(1_000_000, 250, ShieldScope.PHYSICAL_ELEMENTAL,
                6D, 16D, 12D, 6D, 6D, 0.2D, 20D, 40D, 0.075D, 0.3D, false, 0.5D, 2D, 3D, 20, 0, 0.1D);

        private static ForgeConfigSpec.IntValue maxMru;
        private static ForgeConfigSpec.IntValue mruPerDamage;
        private static ForgeConfigSpec.EnumValue<ShieldScope> shieldScope;
        private static ForgeConfigSpec.DoubleValue armorHelmet;
        private static ForgeConfigSpec.DoubleValue armorChestplate;
        private static ForgeConfigSpec.DoubleValue armorLeggings;
        private static ForgeConfigSpec.DoubleValue armorBoots;
        private static ForgeConfigSpec.DoubleValue toughness;
        private static ForgeConfigSpec.DoubleValue knockbackResistance;
        private static ForgeConfigSpec.DoubleValue maxHealth;
        private static ForgeConfigSpec.DoubleValue chestplateMaxHealth;
        private static ForgeConfigSpec.DoubleValue leggingsSpeed;
        private static ForgeConfigSpec.DoubleValue leggingsJump;
        private static ForgeConfigSpec.BooleanValue helmetSaturation;
        private static ForgeConfigSpec.DoubleValue thornsRatio;
        private static ForgeConfigSpec.DoubleValue swimSpeed;
        private static ForgeConfigSpec.DoubleValue dashStrength;
        private static ForgeConfigSpec.IntValue dashCooldown;
        private static ForgeConfigSpec.IntValue dashMru;
        private static ForgeConfigSpec.DoubleValue boardFlySpeed;
        @Nullable
        private static volatile Values testOverride;

        /**
         * 同调阶与护盾覆盖（第十三批 13a，batch13.md §4.4 宅男、D12；写在 {@code general.computer_armor} 里）。阶数门槛是代码常量（{@code item/ComputerAttunement}），
         * 这里只放数。
         *
         * @param stagePriceMultipliers 护盾每点单价按同调阶乘几（下标 = 阶 0~5，缺的按最后一项）：默认 1 / 0.9 / 0.9 / 0.9 / 0.8 / 0.8，即 250 → 225（1 阶）→ 200（4 阶）
         * @param magicPriceMultiplier  2 阶起挡魔法时单价再乘几（默认 2）
         * @param piercingShare         4 阶起挡无视护甲（非魔法）的伤害时每件挡自己份额的比例（默认 0.5）
         * @param piercingPriceMultiplier 同上单价再乘几（默认 3）
         * @param pvpShieldMaxShare     攻击者是别的玩家时每件挡自己份额的比例（四件合计就是这一下最多挡的比例，默认 0.5）
         * @param pvpShieldCostMultiplier 攻击者是别的玩家时单价再乘几（默认 2）
         * @param repairDelay           矩阵修复（3 阶全套）：多少刻没受伤以后开始回血
         * @param repairPercent         每秒回最大生命的比例（默认 0.01）
         * @param repairMruPerHealth    每回 1 点生命花多少 MRU
         * @param overloadThreshold     过载屏障（5 阶全套）：护盾一下挡下 ≥ 最大生命 × 这个比例时展开
         * @param overloadDuration      展开后免疫多少刻
         * @param overloadCooldown      冷却多少刻（存玩家数据，退出重进不清）
         */
        public record Attuned(List<Double> stagePriceMultipliers, double magicPriceMultiplier, double piercingShare, double piercingPriceMultiplier,
                              double pvpShieldMaxShare, double pvpShieldCostMultiplier, int repairDelay, double repairPercent, int repairMruPerHealth,
                              double overloadThreshold, int overloadDuration, int overloadCooldown) {

            public Attuned {
                stagePriceMultipliers = List.copyOf(stagePriceMultipliers);
            }

            /** n 阶的单价倍数（下标越界按最后一项；空表按 1） */
            public double priceMultiplier(int stage) {
                if (stagePriceMultipliers.isEmpty()) {
                    return 1D;
                }
                double value = stagePriceMultipliers.get(Math.max(0, Math.min(stagePriceMultipliers.size() - 1, stage)));
                return Double.isFinite(value) && value >= 0D ? value : 1D;
            }

            /** n 阶每点单价（0 阶原价 × 倍数，四舍五入） */
            public int price(int basePerPoint, int stage) {
                return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, Math.round(basePerPoint * priceMultiplier(stage))));
            }

            public Builder toBuilder() {
                return new Builder(this);
            }

            /** {@link Attuned} 的改写器（GameTest 只改要测的几项） */
            public static final class Builder {
                private List<Double> stagePriceMultipliers;
                private double magicPriceMultiplier;
                private double piercingShare;
                private double piercingPriceMultiplier;
                private double pvpShieldMaxShare;
                private double pvpShieldCostMultiplier;
                private int repairDelay;
                private double repairPercent;
                private int repairMruPerHealth;
                private double overloadThreshold;
                private int overloadDuration;
                private int overloadCooldown;

                private Builder(Attuned a) {
                    stagePriceMultipliers = a.stagePriceMultipliers();
                    magicPriceMultiplier = a.magicPriceMultiplier();
                    piercingShare = a.piercingShare();
                    piercingPriceMultiplier = a.piercingPriceMultiplier();
                    pvpShieldMaxShare = a.pvpShieldMaxShare();
                    pvpShieldCostMultiplier = a.pvpShieldCostMultiplier();
                    repairDelay = a.repairDelay();
                    repairPercent = a.repairPercent();
                    repairMruPerHealth = a.repairMruPerHealth();
                    overloadThreshold = a.overloadThreshold();
                    overloadDuration = a.overloadDuration();
                    overloadCooldown = a.overloadCooldown();
                }

                public Builder pvp(double maxShare, double costMultiplier) {
                    pvpShieldMaxShare = maxShare;
                    pvpShieldCostMultiplier = costMultiplier;
                    return this;
                }

                public Builder repair(int delay, double percent, int mruPerHealth) {
                    repairDelay = delay;
                    repairPercent = percent;
                    repairMruPerHealth = mruPerHealth;
                    return this;
                }

                public Builder overload(double threshold, int duration, int cooldown) {
                    overloadThreshold = threshold;
                    overloadDuration = duration;
                    overloadCooldown = cooldown;
                    return this;
                }

                public Attuned build() {
                    return new Attuned(stagePriceMultipliers, magicPriceMultiplier, piercingShare, piercingPriceMultiplier, pvpShieldMaxShare,
                            pvpShieldCostMultiplier, repairDelay, repairPercent, repairMruPerHealth, overloadThreshold, overloadDuration, overloadCooldown);
                }
            }
        }

        /**
         * 同调的默认值（batch13.md §4.4）：单价 250 → 225（1 阶）→ 200（4 阶）；魔法 ×2 价；无视护甲挡一半、×3 价；对玩家最多挡一半、×2 价（D12 A）；
         * 矩阵修复 120 刻没受伤后每秒 1%、每点生命 500 MRU；过载屏障 25%、20 刻、冷却 1 200 刻
         */
        public static final Attuned ATTUNED_DEFAULTS = new Attuned(List.of(1D, 0.9D, 0.9D, 0.9D, 0.8D, 0.8D), 2D, 0.5D, 3D, 0.5D, 2D,
                120, 0.01D, 500, 0.25D, 20, 1200);

        private static ForgeConfigSpec.ConfigValue<List<? extends Number>> stagePriceMultipliers;
        private static ForgeConfigSpec.DoubleValue magicPriceMultiplier;
        private static ForgeConfigSpec.DoubleValue piercingShare;
        private static ForgeConfigSpec.DoubleValue piercingPriceMultiplier;
        private static ForgeConfigSpec.DoubleValue pvpShieldMaxShare;
        private static ForgeConfigSpec.DoubleValue pvpShieldCostMultiplier;
        private static ForgeConfigSpec.IntValue repairDelay;
        private static ForgeConfigSpec.DoubleValue repairPercent;
        private static ForgeConfigSpec.IntValue repairMruPerHealth;
        private static ForgeConfigSpec.DoubleValue overloadThreshold;
        private static ForgeConfigSpec.IntValue overloadDuration;
        private static ForgeConfigSpec.IntValue overloadCooldown;
        @Nullable
        private static volatile Attuned attunedOverride;

        private Config() {
        }

        /** 同调那几项（在 {@code computer_armor} 小节里，{@link #define} 调） */
        private static void defineAttuned(ForgeConfigSpec.Builder builder) {
            stagePriceMultipliers = builder.comment("Attunement: multiplier on the per-point shield cost at each stage, stage 0 first (missing stages use the last entry).",
                            "同调：护盾每点单价按阶乘几，从 0 阶起（缺的阶按最后一项）")
                    .defineList("stage_price_multipliers", ATTUNED_DEFAULTS.stagePriceMultipliers(),
                            o -> o instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0D);
            magicPriceMultiplier = builder.comment("Attunement stage 2+: magic damage from a non-player attacker is blocked too, at this many times the cost per point.",
                            "同调 2 阶起：也挡非玩家攻击者打来的魔法伤害，每点单价乘这么多")
                    .defineInRange("magic_price_multiplier", ATTUNED_DEFAULTS.magicPriceMultiplier(), 0D, 1000D);
            piercingShare = builder.comment("Attunement stage 4+: armor-piercing damage (not magic) from a non-player attacker is blocked at this fraction of each piece's share.",
                            "同调 4 阶起：非玩家攻击者打来的无视护甲伤害（不是魔法）每件挡自己份额的比例（0.5 = 一半）")
                    .defineInRange("piercing_share", ATTUNED_DEFAULTS.piercingShare(), 0D, 1D);
            piercingPriceMultiplier = builder.comment("Attunement stage 4+: cost per point multiplier for armor-piercing damage.",
                            "同调 4 阶起：挡无视护甲伤害时每点单价乘这么多")
                    .defineInRange("piercing_price_multiplier", ATTUNED_DEFAULTS.piercingPriceMultiplier(), 0D, 1000D);
            pvpShieldMaxShare = builder.comment("When the attacker is another player, each piece blocks at most this fraction of its share (the whole suit blocks at most this fraction of the hit).",
                            "攻击者是别的玩家时，每件最多挡自己份额的比例（整套合计就是这一下最多挡的比例，0.5 = 一半）")
                    .defineInRange("pvp_shield_max_share", ATTUNED_DEFAULTS.pvpShieldMaxShare(), 0D, 1D);
            pvpShieldCostMultiplier = builder.comment("When the attacker is another player, cost per point multiplier.",
                            "攻击者是别的玩家时，每点单价乘这么多")
                    .defineInRange("pvp_shield_cost_multiplier", ATTUNED_DEFAULTS.pvpShieldCostMultiplier(), 0D, 1000D);
            repairDelay = builder.comment("Matrix Repair (full set at stage 3): ticks without taking damage before healing starts.",
                            "矩阵修复（全套 3 阶）：多少刻没受伤以后开始回血")
                    .defineInRange("repair_delay", ATTUNED_DEFAULTS.repairDelay(), 1, 72_000);
            repairPercent = builder.comment("No longer read: superseded by general.gear_growth.computer_armor.repair_percent (kept so old config files still load).", "已由 general.gear_growth.computer_armor.repair_percent 接管，不再读（留着是为了老配置文件照常加载）", "Matrix Repair: fraction of max health healed per second.", "矩阵修复：每秒回最大生命的比例（0.01 = 1%）")
                    .defineInRange("repair_percent", ATTUNED_DEFAULTS.repairPercent(), 0D, 1D);
            repairMruPerHealth = builder.comment("Matrix Repair: MRU spent per health point healed.", "矩阵修复：每回 1 点生命花多少 MRU")
                    .defineInRange("repair_mru_per_health", ATTUNED_DEFAULTS.repairMruPerHealth(), 0, 1_000_000);
            overloadThreshold = builder.comment("Overload Barrier (full set at stage 5): opens when the shield blocks at least this fraction of max health in one hit.",
                            "过载屏障（全套 5 阶）：护盾一下挡下的伤害至少是最大生命 × 这个比例时展开（0.25 = 四分之一）")
                    .defineInRange("overload_threshold", ATTUNED_DEFAULTS.overloadThreshold(), 0D, 100D);
            overloadDuration = builder.comment("Overload Barrier: ticks of immunity (anything that does not bypass invulnerability).",
                            "过载屏障：免疫多少刻（不无视无敌的伤害都免）")
                    .defineInRange("overload_duration", ATTUNED_DEFAULTS.overloadDuration(), 0, 1200);
            overloadCooldown = builder.comment("No longer read: superseded by general.gear_growth.computer_armor.overload_cooldown (kept so old config files still load).", "已由 general.gear_growth.computer_armor.overload_cooldown 接管，不再读（留着是为了老配置文件照常加载）", "Overload Barrier: cooldown in ticks (kept across relogging).", "过载屏障：冷却多少刻（退出重进不清）")
                    .defineInRange("overload_cooldown", ATTUNED_DEFAULTS.overloadCooldown(), 0, 72_000);
        }

        /** 同调那几项现在的数：测试覆盖值 → 配置（没加载时默认值） */
        public static Attuned attuned() {
            Attuned override = attunedOverride;
            if (override != null) {
                return override;
            }
            if (magicPriceMultiplier == null) {
                return ATTUNED_DEFAULTS;
            }
            return new Attuned(priceList(), ECConfig.get(magicPriceMultiplier), ECConfig.get(piercingShare), ECConfig.get(piercingPriceMultiplier),
                    ECConfig.get(pvpShieldMaxShare), ECConfig.get(pvpShieldCostMultiplier), ECConfig.get(repairDelay), ECConfig.get(repairPercent),
                    ECConfig.get(repairMruPerHealth), ECConfig.get(overloadThreshold), ECConfig.get(overloadDuration), ECConfig.get(overloadCooldown));
        }

        /** 列表按 Number 读（TOML 里写整数时会是 Long） */
        private static List<Double> priceList() {
            List<? extends Number> raw;
            try {
                raw = ECConfig.getValue(stagePriceMultipliers);
            } catch (IllegalStateException e) {
                return ATTUNED_DEFAULTS.stagePriceMultipliers();
            }
            List<Double> out = new ArrayList<>(raw.size());
            for (Number n : raw) {
                out.add(n.doubleValue());
            }
            return out;
        }

        /** GameTest 用：同调那几项换成给定的数，null = 回到配置 */
        public static void setAttunedTestOverrides(@Nullable Attuned values) {
            attunedOverride = values;
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Computer Guy's Suit: MRU, damage blocking and the effects of each piece. Attribute changes reach pieces already worn after taking them off and putting them on again.",
                    "宅男套装：MRU、挡伤害和各件的效果。改了属性以后，已经穿在身上的要脱下再穿一次才更新").push("computer_armor");
            maxMru = builder.comment("MRU stored by each piece.", "每件存多少 MRU")
                    .defineInRange("max_mru", DEFAULTS.maxMru(), 1, 100_000_000);
            mruPerDamage = builder.comment("MRU spent per point of damage a charged piece blocks (each piece counts at least 1 point).",
                            "有电的一件每挡下 1 点伤害花多少 MRU（每件至少按 1 点算）")
                    .defineInRange("mru_per_damage", DEFAULTS.mruPerDamage(), 0, 1_000_000);
            shieldScope = builder.comment("Which damage a charged piece blocks its share of: ALL (anything that does not bypass armor), PHYSICAL_ELEMENTAL (melee, projectiles, fire, explosions, lightning, freezing; vanilla freezing bypasses armor) or PHYSICAL (melee and projectiles).",
                            "有电时挡哪些伤害：ALL（所有不无视护甲的）、PHYSICAL_ELEMENTAL（近战、弹射物、火、爆炸、闪电、冰冻；原版冻伤无视护甲，实际挡不到）、PHYSICAL（近战和弹射物）")
                    .defineEnum("shield_scope", DEFAULTS.shieldScope());
            armorHelmet = builder.comment("Armor points of the helmet (vanilla caps a player's total armor at 30).",
                            "头盔的护甲值（原版玩家的总护甲最多算 30）")
                    .defineInRange("armor_helmet", DEFAULTS.armorHelmet(), 0D, 30D);
            armorChestplate = builder.comment("Armor points of the chestplate.", "胸甲的护甲值")
                    .defineInRange("armor_chestplate", DEFAULTS.armorChestplate(), 0D, 30D);
            armorLeggings = builder.comment("Armor points of the leggings.", "护腿的护甲值")
                    .defineInRange("armor_leggings", DEFAULTS.armorLeggings(), 0D, 30D);
            armorBoots = builder.comment("Armor points of the boots.", "靴子的护甲值")
                    .defineInRange("armor_boots", DEFAULTS.armorBoots(), 0D, 30D);
            toughness = builder.comment("Armor toughness of each piece (vanilla caps the total at 20).",
                            "每件的盔甲韧性（原版总韧性最多算 20）")
                    .defineInRange("toughness", DEFAULTS.toughness(), 0D, 20D);
            knockbackResistance = builder.comment("Knockback resistance of each piece (0 to 1; 1 = immune).",
                            "每件的击退抗性（0~1，1 = 完全不被击退）")
                    .defineInRange("knockback_resistance", DEFAULTS.knockbackResistance(), 0D, 1D);
            maxHealth = builder.comment("Extra max health of the helmet, the leggings and the boots, each, in health points (2 = one heart).",
                            "头盔、护腿、靴子每件加的最大生命（生命值，2 = 一颗心）")
                    .defineInRange("max_health", DEFAULTS.maxHealth(), 0D, 1024D);
            chestplateMaxHealth = builder.comment("Extra max health of the chestplate, in health points.", "胸甲加的最大生命（生命值）")
                    .defineInRange("chestplate_max_health", DEFAULTS.chestplateMaxHealth(), 0D, 1024D);
            leggingsSpeed = builder.comment("Movement speed added by the leggings (a player's base speed is 0.1).",
                            "护腿加的移速（玩家基础移速 0.1）")
                    .defineInRange("leggings_speed", DEFAULTS.leggingsSpeed(), 0D, 1D);
            leggingsJump = builder.comment("Extra upward speed when jumping with the leggings (a normal jump is 0.42).",
                            "穿护腿起跳时额外的竖直速度（普通起跳 0.42）")
                    .defineInRange("leggings_jump", DEFAULTS.leggingsJump(), 0D, 2D);
            helmetSaturation = builder.comment("Whether the helmet also keeps saturation full (fast regeneration), not only the hunger bar.",
                            "头盔是否也把饱和度顶满（回血很快），关掉时只保持饥饿值满")
                    .define("helmet_saturation", DEFAULTS.helmetSaturation());
            thornsRatio = builder.comment("Full set: share of the damage taken that is returned to the attacker (results under 0.5 are dropped).",
                            "全套：受到的伤害按这个比例反给攻击者（不到 0.5 点不反）")
                    .defineInRange("thorns_ratio", DEFAULTS.thornsRatio(), 0D, 10D);
            swimSpeed = builder.comment("Full set: swim speed added (forge:swim_speed, base 1).",
                            "全套：游泳速度加成（forge:swim_speed，基础 1）")
                    .defineInRange("swim_speed", DEFAULTS.swimSpeed(), 0D, 16D);
            dashStrength = builder.comment("Full set boost: speed in the looking direction, blocks per tick.",
                            "全套喷射推进：朝视线方向的速度（格 / 刻）")
                    .defineInRange("dash_strength", DEFAULTS.dashStrength(), 0D, 10D);
            dashCooldown = builder.comment("Full set boost: cooldown in ticks.", "全套喷射推进：冷却（刻）")
                    .defineInRange("dash_cooldown", DEFAULTS.dashCooldown(), 1, 1200);
            dashMru = builder.comment("Full set boost: MRU spent per boost (0 = free).", "全套喷射推进：每次花多少 MRU（0 = 不花）")
                    .defineInRange("dash_mru", DEFAULTS.dashMru(), 0, 1_000_000);
            defineAttuned(builder);
            builder.pop();
            builder.comment("Computer Guy's Antigravitational Board.", "宅男反重力板").push("computer_board");
            boardFlySpeed = builder.comment("Flying speed given by the board (vanilla creative flight is 0.05).",
                            "反重力板给的飞行速度（原版创造模式飞行 0.05）")
                    .defineInRange("fly_speed", DEFAULTS.boardFlySpeed(), 0D, 1D);
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (maxMru == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(maxMru), ECConfig.get(mruPerDamage), scope(),
                    ECConfig.get(armorHelmet), ECConfig.get(armorChestplate), ECConfig.get(armorLeggings), ECConfig.get(armorBoots),
                    ECConfig.get(toughness), ECConfig.get(knockbackResistance), ECConfig.get(maxHealth), ECConfig.get(chestplateMaxHealth),
                    ECConfig.get(leggingsSpeed), ECConfig.get(leggingsJump), ECConfig.get(helmetSaturation), ECConfig.get(thornsRatio),
                    ECConfig.get(swimSpeed), ECConfig.get(dashStrength), ECConfig.get(dashCooldown), ECConfig.get(dashMru),
                    ECConfig.get(boardFlySpeed));
        }

        private static ShieldScope scope() {
            try {
                return shieldScope.get();
            } catch (IllegalStateException e) {
                // 配置还没载入（客户端进服前、数据生成）
                return shieldScope.getDefault();
            }
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回（跨刻的测试在最后一步置回） */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
