package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.WeaponMakerBlock;
import com.flameforge.essentialcraft5.blockentity.WeaponMakerBlockEntity;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.menu.WeaponMakerMenu;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 武器工作台模块（第五批，计划 tools/plan/batch5.md §3.2）：四台工作台（方块、方块实体、菜单、界面），在工作台里把核心、灵魂格物品、
 * 枪身材料组装成枪。枪物品本身、射线、铁砧修枪归 ECGuns 模块；共享核心在 {@code gun/} 包（枪种与槽位布局 {@link GunType}、
 * 材料表 {@code GunMaterials}、部件 NBT {@code GunParts}、属性 {@code GunStats}），组装规则在 {@code gun/GunAssembly}。
 * 工作台按 {@link GunType#itemId()} 从注册表取枪物品，不 import ECGuns 的类（枪没注册时工作台不出枪）。
 * <p>
 * 新旧注册名：原作一个方块 {@code weaponMaker} 的 meta 0~3（BlocksCore L156：{@code registerBlockSimple(…, 1, 1, 15)}，先抗爆后硬度、不透光，
 * Material.ROCK）拆成 {@code pistol_weapon_maker}、{@code rifle_weapon_maker}、{@code sniper_weapon_maker}、{@code gatling_weapon_maker}；
 * 方块实体、菜单共用一种 {@code weapon_maker}（槽数按方块的枪种：10 / 13 / 14 / 19）。外观照原作 blockstate：{@code cube_top}，
 * 顶面 {@code block/weaponbench}、侧面和底面 {@code block/fortifiedstone}。
 * <p>
 * 配方在 data/essentialcraft5/recipes/weaponmaker/：手枪工作台 SIS/SCS/SSS（魔力强化石、重质测重压力板、工作台），
 * 单独放进工作台按 手枪 → 步枪 → 狙击 → 加特林 → 手枪 循环（原作 weapon_maker_0~3.json）。
 * 文字在 tools/lang/keys/ecweaponmaker.json，标签在 tools/tags/ecweaponmaker.json（{@link #GUN_SOULS}）。
 * 手册支线「魔力枪械」（{@code guns}，order 95）归本模块：assets/essentialcraft5/guide/{zh_cn,en_us}/guns.json，
 * 进度 data/essentialcraft5/advancements/guide/guns/。客户端部分（界面注册）在 client/WeaponMakerClient。
 * <p>
 * 没有配置、没有网络包：造枪按钮走原版 {@code clickMenuButton}（{@link WeaponMakerMenu#BUTTON_ASSEMBLE}），不带坐标。
 */
public final class ECWeaponMaker {

    /**
     * 灵魂格收的物品（原作「MRU 储存物品 meta ≥ 1」）：灵魂球、暗黑灵魂物质、鲜红灵魂物质、永恒物质（灵魂碎片不收）。
     * 成员在 tools/tags/ecweaponmaker.json；以后别的模块想让自己的储存物品也能当灵魂格物品，在自己的标签文件里追加。
     */
    public static final TagKey<Item> GUN_SOULS = ECTags.modItem("gun_souls");
    /** 核心格收的物品（原作矿辞 coreMagic：元素核心、淡青元素核心，以后的恶魔核心由第七批加进标签） */
    public static final TagKey<Item> MAGIC_CORES = ECTags.modItem("cores/magic");

    public static final RegistryObject<WeaponMakerBlock> PISTOL_WEAPON_MAKER = registerMaker(GunType.PISTOL);
    public static final RegistryObject<WeaponMakerBlock> RIFLE_WEAPON_MAKER = registerMaker(GunType.RIFLE);
    public static final RegistryObject<WeaponMakerBlock> SNIPER_WEAPON_MAKER = registerMaker(GunType.SNIPER);
    public static final RegistryObject<WeaponMakerBlock> GATLING_WEAPON_MAKER = registerMaker(GunType.GATLING);

    public static final RegistryObject<BlockEntityType<WeaponMakerBlockEntity>> WEAPON_MAKER_BE =
            ECBlockEntities.type("weapon_maker", WeaponMakerBlockEntity::new,
                    PISTOL_WEAPON_MAKER, RIFLE_WEAPON_MAKER, SNIPER_WEAPON_MAKER, GATLING_WEAPON_MAKER);

    public static final RegistryObject<MenuType<WeaponMakerMenu>> WEAPON_MAKER_MENU =
            ECMenus.register("weapon_maker", WeaponMakerMenu::new);

    private ECWeaponMaker() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister；本模块没有配置和网络包 */
    public static void init() {
    }

    /** 这种枪的工作台 */
    public static RegistryObject<WeaponMakerBlock> maker(GunType type) {
        return switch (type) {
            case PISTOL -> PISTOL_WEAPON_MAKER;
            case RIFLE -> RIFLE_WEAPON_MAKER;
            case SNIPER -> SNIPER_WEAPON_MAKER;
            case GATLING -> GATLING_WEAPON_MAKER;
        };
    }

    /**
     * 原作：硬度 1、抗爆 1（先抗爆后硬度 → 1.20.1 抗爆 1.0），Material.ROCK（地图色石头、要镐），不透光（完整实心方块，天空光不穿透）。
     * 注册名 = {@link GunType#makerBlockId()}（{@code <枪>_weapon_maker}）。
     */
    private static RegistryObject<WeaponMakerBlock> registerMaker(GunType type) {
        return ECBlocks.register(type.makerBlockId().getPath(),
                () -> new WeaponMakerBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, true)), type),
                BlockSpec.of((provider, block) -> provider.simpleBlockWithItem(block,
                        provider.models().cubeTop(provider.name(block), provider.tex("block/fortifiedstone"), provider.tex("block/weaponbench")))));
    }
}
