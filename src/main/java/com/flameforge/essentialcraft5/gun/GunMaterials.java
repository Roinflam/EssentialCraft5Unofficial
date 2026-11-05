package com.flameforge.essentialcraft5.gun;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 枪械材料表（第五批框架 F5-5），照原作 {@code GunInitialization}（L17~228：枪身材料 L18~151、瞄具 L154~186、透镜 L189~227）写成代码数据。
 * <ul>
 *   <li><b>枪身材料</b>（{@link #BODIES}）：枪托、机栓、握把三种部件共用，七项属性由 {@link GunStats#of} 按部件主导项算（原作 calculateGunStats）。</li>
 *   <li><b>普通瞄具</b>（{@link #SCOPES}，手枪 / 步枪可选）：只有 zoom。</li>
 *   <li><b>狙击镜</b>（{@link #SNIPER_SCOPES}，狙击必填，两格同种）：只有 zoom；镜座固定是 {@link #SNIPER_SCOPE_HOUSING}（原作镜座认的就是普通瞄具那张表）。</li>
 *   <li><b>透镜</b>（{@link #LENSES}）：只有 balance（光束颜色；四种另有命中效果，见 {@link LensEffects}；原作暗影透镜的「暗影伤害」{@code ShadeUtils} 是空方法，这里补成吸取）；
 *       加特林的透镜座固定是 {@link #GATLING_LENS_HOUSING}（原作 genitem 32）。</li>
 * </ul>
 * <b>透镜只有 balance、瞄具只有 zoom，别的属性一项都没有。</b>
 * <p>
 * <b>「已开放」</b>：物品按注册名认，材料物品没注册（{@code void_plating} 第六批、{@code ackronite_ingot} 第七批）时这种材料在工作台里不认、
 * 随机组装也不抽（{@link #bodyByItem} 等只返回已开放的）；数据照样在表里，注册以后自动能用。NBT 里已经写着的材料 id 按 id 查（{@link #body(String)} 等），
 * 不管开没开放，老枪的属性照算。
 */
public final class GunMaterials {

    /** 枪身材料：七项基础值（原作 appendData 的 durability、damage、reload、knockback、spread、speed、shots） */
    public record Body(String id, ResourceLocation item, float durability, float damage, float reload, float knockback,
                       float spread, float speed, float shots) {
        /** 材料物品注册了没有 */
        public boolean available() {
            return ForgeRegistries.ITEMS.containsKey(item);
        }
    }

    /** 瞄具（普通或狙击镜）：只有放大倍率 */
    public record Scope(String id, ResourceLocation item, float zoom) {
        public boolean available() {
            return ForgeRegistries.ITEMS.containsKey(item);
        }
    }

    /**
     * 透镜：只有 balance（{@code 1} 混沌、{@code 2} 冰冻、{@code 3} 魔力（原作 id「pure」）、{@code 4} 暗影；没装透镜是 0 = 中性）。
     * 物品是四种矩阵投影（原作 matrixProj meta 1~4）。
     */
    public record Lens(String id, ResourceLocation item, int balance) {
        public boolean available() {
            return ForgeRegistries.ITEMS.containsKey(item);
        }
    }

    /** 枪身材料（原作顺序）；void、demonic 的物品在第六、七批注册 */
    public static final List<Body> BODIES = List.of(
            new Body("mru", id("magical_ingot"), 120, 8, 5, 6, 3, 3, 2),
            new Body("pale", id("pale_ingot"), 160, 11, 8, 2, 6, 1, 2),
            new Body("mithriline", id("mithriline_ingot"), 300, 14, 12, 12, 1, 6, 12),
            new Body("void", id("void_plating"), 1200, 18, 4, 2, 6, 2, 8),
            new Body("demonic", id("ackronite_ingot"), 3000, 26, 2, 0, 1, 1, 20));

    /** 普通瞄具（手枪、步枪；原作 ScopeMaterial("mru", false)，材料是魔力纯化玻璃合金） */
    public static final List<Scope> SCOPES = List.of(
            new Scope("mru", id("magic_purified_glass_alloy"), 1.2F));

    /** 狙击镜（原作 ScopeMaterial(id, true)，材料同枪身五种；虚空 12×、恶魔 16× 原作实际被原版 FOV 下限压成 10×，客户端修） */
    public static final List<Scope> SNIPER_SCOPES = List.of(
            new Scope("mru", id("magical_ingot"), 3F),
            new Scope("pale", id("pale_ingot"), 5F),
            new Scope("mithriline", id("mithriline_ingot"), 7F),
            new Scope("void", id("void_plating"), 12F),
            new Scope("demonic", id("ackronite_ingot"), 16F));

    /** 透镜（id 沿用原作与贴图目录名 {@code item/guns/<枪>/lense/<id>}） */
    public static final List<Lens> LENSES = List.of(
            new Lens("chaos", id("chaos_matrix_projection"), 1),
            new Lens("frozen", id("frozen_matrix_projection"), 2),
            new Lens("pure", id("magic_matrix_projection"), 3),
            new Lens("shade", id("shade_matrix_projection"), 4));

    /** 狙击的镜座（{@link GunType.SlotRole#SCOPE_HOUSING}）：魔力纯化玻璃合金（原作按普通瞄具表认） */
    public static final ResourceLocation SNIPER_SCOPE_HOUSING = id("magic_purified_glass_alloy");
    /** 加特林的透镜座（{@link GunType.SlotRole#LENS_HOUSING}）：日光玻璃（原作 genitem 32） */
    public static final ResourceLocation GATLING_LENS_HOUSING = id("sun_imbued_glass");

    private GunMaterials() {
    }

    private static ResourceLocation id(String path) {
        return EssentialCraft5.id(path);
    }

    // ------------------------------------------------------------------
    // 按 id 查（读 NBT 用，不管开没开放）
    // ------------------------------------------------------------------

    public static Optional<Body> body(String id) {
        return BODIES.stream().filter(body -> body.id().equals(id)).findFirst();
    }

    /** 这种枪的瞄具表里按 id 查：狙击查 {@link #SNIPER_SCOPES}，手枪 / 步枪查 {@link #SCOPES}，加特林没有 */
    public static Optional<Scope> scope(GunType type, String id) {
        return scopesFor(type).stream().filter(scope -> scope.id().equals(id)).findFirst();
    }

    public static Optional<Lens> lens(String id) {
        return LENSES.stream().filter(lens -> lens.id().equals(id)).findFirst();
    }

    /** 这种枪用哪张瞄具表 */
    public static List<Scope> scopesFor(GunType type) {
        return switch (type.scopeKind()) {
            case NONE -> List.of();
            case NORMAL_OPTIONAL -> SCOPES;
            case SNIPER_REQUIRED -> SNIPER_SCOPES;
        };
    }

    // ------------------------------------------------------------------
    // 按物品查（工作台认材料用，只认已开放的）
    // ------------------------------------------------------------------

    /** 这个物品是哪种已开放的枪身材料（只比物品，不比 NBT、数量） */
    public static Optional<Body> bodyByItem(ItemStack stack) {
        ResourceLocation key = keyOf(stack);
        return key == null ? Optional.empty()
                : BODIES.stream().filter(body -> body.item().equals(key) && body.available()).findFirst();
    }

    /** 这个物品是这种枪的哪种已开放瞄具（狙击 = 狙击镜材；手枪 / 步枪 = 普通瞄具；加特林恒空） */
    public static Optional<Scope> scopeByItem(GunType type, ItemStack stack) {
        ResourceLocation key = keyOf(stack);
        return key == null ? Optional.empty()
                : scopesFor(type).stream().filter(scope -> scope.item().equals(key) && scope.available()).findFirst();
    }

    public static Optional<Lens> lensByItem(ItemStack stack) {
        ResourceLocation key = keyOf(stack);
        return key == null ? Optional.empty()
                : LENSES.stream().filter(lens -> lens.item().equals(key) && lens.available()).findFirst();
    }

    /** 是不是狙击镜座 */
    public static boolean isSniperScopeHousing(ItemStack stack) {
        return SNIPER_SCOPE_HOUSING.equals(keyOf(stack));
    }

    /** 是不是加特林透镜座 */
    public static boolean isGatlingLensHousing(ItemStack stack) {
        return GATLING_LENS_HOUSING.equals(keyOf(stack));
    }

    // ------------------------------------------------------------------
    // 已开放的（随机组装、界面提示用）
    // ------------------------------------------------------------------

    public static List<Body> availableBodies() {
        return BODIES.stream().filter(Body::available).toList();
    }

    public static List<Scope> availableScopes(GunType type) {
        return scopesFor(type).stream().filter(Scope::available).toList();
    }

    public static List<Lens> availableLenses() {
        return LENSES.stream().filter(Lens::available).toList();
    }

    @Nullable
    private static ResourceLocation keyOf(ItemStack stack) {
        return stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
    }
}
