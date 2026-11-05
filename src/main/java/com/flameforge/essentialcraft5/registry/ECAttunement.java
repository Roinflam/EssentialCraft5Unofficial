package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.command.AttunementCommands;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.recipe.AttunementTransferRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.RegistryObject;

/**
 * 同调框架（第十三批 13a，batch13.md §3.3）：装备在实战里长阶。家族用物品标签 {@code essentialcraft5:attunement/<家族>} 登记
 * （{@code tools/tags/attunement.json}），升阶走魔力工作台同一个配方类型、新的序列化器 {@code attunement_ascension}，
 * 换套时的同调转移是 {@code attunement_transfer}。
 * <p>
 * 这里登记：SERVER 配置 {@code general.attunement}（{@link AttunementConfig}）、七个家族（最高阶、档位，§3.3.1）、两个序列化器、
 * 指令 {@code /ec5 attunement}。点数怎么来、写盘、预算、每阶通用加成在 {@code item/attunement/AttunementGains}、
 * {@code player/AttunementBudget}、{@code item/attunement/AttunementBonuses}（各自 {@code @EventBusSubscriber}）。
 * <p>
 * 家族档位（同调转移只许从低档转到高档）：魔力防护 1 → 风之 2 → 虚空 / 审判庭 3 → 宅男 4；武器余烬元素剑 1 → 裁决之刃 2。
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

    private ECAttunement() {
    }

    /** 由 ECRegistries.register() 调用（必须在 ECConfig.register 之前） */
    public static void init() {
        ECConfig.general(AttunementConfig::define);
        ECCommands.addSubcommand(AttunementCommands::build);
    }
}
