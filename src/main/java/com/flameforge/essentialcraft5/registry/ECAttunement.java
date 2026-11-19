package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.command.AttunementCommands;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.GearCombatEvents;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorEvents;
import com.flameforge.essentialcraft5.item.MatrixTraits;
import com.flameforge.essentialcraft5.item.VerdictSeal;
import com.flameforge.essentialcraft5.item.VoidArmorEvents;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.recipe.AttunementTransferRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.RegistryObject;

/**
 * 同调框架（第十三批 13a，batch13.md §3.3）：装备在实战里长阶。家族用物品标签 {@code essentialcraft5:attunement/<家族>} 登记
 * （{@code tools/tags/attunement.json}），升阶走魔力工作台同一个配方类型、新的序列化器 {@code attunement_ascension}，
 * 换套时的同调转移是 {@code attunement_transfer}。
 * <p>
 * 这里登记：SERVER 配置 {@code general.attunement}（{@link AttunementConfig}）和 {@code general.gear_growth}（{@link GearGrowthConfig}，成长加强的词条、
 * 觉醒、战痕和总闸）、八个家族（最高阶、档位，§3.3.1；枪械同调 {@code mru_gun} 是成长加强加的）、两个序列化器、装备特效事件包、总闸池子的各来源、
 * 指令 {@code /ec5 attunement}。点数怎么来、写盘、预算、每阶通用加成在 {@code item/attunement/AttunementGains}、
 * {@code player/AttunementBudget}、{@code item/attunement/AttunementBonuses}（各自 {@code @EventBusSubscriber}）。
 * <p>
 * 家族档位（同调转移只许从低档转到高档）：魔力防护 1 → 风之 2 → 虚空 / 审判庭 3 → 宅男 4；武器余烬元素剑 1 → 裁决之刃 2；
 * 枪械同调 1（枪这条线和剑不互转，同家族之间可以转）。
 */
public final class ECAttunement {

    public static final RegistryObject<RecipeSerializer<AttunementAscensionRecipe>> ASCENSION_SERIALIZER =
            ECRecipes.serializer("attunement_ascension", AttunementAscensionRecipe.Serializer::new);
    public static final RegistryObject<RecipeSerializer<AttunementTransferRecipe>> TRANSFER_SERIALIZER =
            ECRecipes.serializer("attunement_transfer", AttunementTransferRecipe.Serializer::new);

    public static final AttunementFamily MHS_ARMOR = AttunementFamily.register("mhs_armor", AttunementFamily.Kind.ARMOR, 2, 1);
    public static final AttunementFamily WIND_ARMOR = AttunementFamily.register("wind_armor", AttunementFamily.Kind.ARMOR, 3, 2);
    public static final AttunementFamily VOID_ARMOR = AttunementFamily.register("void_armor", AttunementFamily.Kind.ARMOR, 4, 3);
    public static final AttunementFamily INQUISITORIUM_ARMOR = AttunementFamily.register("inquisitorium_armor", AttunementFamily.Kind.ARMOR, 4, 3);
    public static final AttunementFamily COMPUTER_ARMOR = AttunementFamily.register("computer_armor", AttunementFamily.Kind.ARMOR, 5, 4);
    public static final AttunementFamily EMBER_SWORD = AttunementFamily.register("ember_sword", AttunementFamily.Kind.WEAPON, 2, 1);
    public static final AttunementFamily VERDICT_BLADE = AttunementFamily.register("verdict_blade", AttunementFamily.Kind.WEAPON, 5, 2);
    /** 枪械同调（tools/plan/gear_growth.md §4.3）：四把 MRU 枪，枪这条线只在同家族之间转移 */
    public static final AttunementFamily MRU_GUN = AttunementFamily.register("mru_gun", AttunementFamily.Kind.WEAPON, 4, 1, AttunementFamily.Line.GUN);

    private ECAttunement() {
    }

    /** 由 ECRegistries.register() 调用（必须在 ECConfig.register 之前） */
    public static void init() {
        ECConfig.general(AttunementConfig::define);
        ECConfig.general(GearGrowthConfig::define);
        ECCommands.addSubcommand(AttunementCommands::build);
        ECGearSounds.init();
        ECNetwork.registerS2C(S2CGearFxPacket.class, S2CGearFxPacket::encode, S2CGearFxPacket::decode, S2CGearFxPacket::handleClient);
        // 总闸的池子：武器自己那几项、终审，加上以前各自连乘的几处（相性、矩阵特性、审判庭、虚空回击）
        GearCombatEvents.registerWeaponSources();
        VerdictSeal.registerGearSources();
        MatrixResonance.registerGearSources();
        MatrixTraits.registerGearSources();
        InquisitoriumArmorEvents.registerGearSources();
        VoidArmorEvents.registerGearSources();
        com.flameforge.essentialcraft5.item.attunement.ArmorGrowth.register();
        com.flameforge.essentialcraft5.item.attunement.ArmorAwakenings.registerGearSources();
    }
}
