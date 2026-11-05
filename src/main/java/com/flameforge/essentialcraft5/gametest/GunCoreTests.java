package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.gun.GunConfig;
import com.flameforge.essentialcraft5.gun.GunMaterials;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.gun.GunType.SlotRole;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CGunBeamPacket;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECGuns;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 第五批框架 F5-5「枪械共享核心」的测试：属性计算对原作、材料表的「已开放」、工作台布局、NBT、伤害类型、配置、光束包与音效、协议号。
 * 纯数据 / 纯函数为主，不放方块、不开火（开火、射线、铁砧是 ECGuns 模块的 GunsTests，组装是 ECWeaponMaker 的 WeaponMakerTests）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GunCoreTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 0.001F;

    private GunCoreTests() {
    }

    /** 期望值（照原作 calculateGunStats 的 float 算法独立算出；耐久、伤害、装填、后坐、speed、散布、shots、弹量） */
    private record Expected(GunType type, String material, float durability, float damage, float reload, float recoil,
                            float speed, float spread, float shots, int magazine) {
    }

    private static final List<Expected> ORIGINAL = List.of(
            new Expected(GunType.PISTOL, "mru", 200F, 13.3333F, 4.1667F, 10F, 5F, 5F, 3.3333F, 3),
            new Expected(GunType.PISTOL, "pale", 266.6667F, 18.3333F, 6.6667F, 3.3333F, 1.6667F, 10F, 3.3333F, 3),
            new Expected(GunType.PISTOL, "mithriline", 500F, 23.3333F, 10F, 20F, 10F, 1.6667F, 20F, 20),
            new Expected(GunType.PISTOL, "void", 2000F, 30F, 3.3333F, 3.3333F, 3.3333F, 10F, 13.3333F, 13),
            new Expected(GunType.PISTOL, "demonic", 5000F, 43.3333F, 1.6667F, 0F, 1.6667F, 1.6667F, 33.3333F, 33),
            new Expected(GunType.RIFLE, "mru", 200F, 13.3333F, 8.3333F, 10F, 0F, 6.5F, 20F, 20),
            new Expected(GunType.RIFLE, "pale", 266.6667F, 18.3333F, 13.3333F, 3.3333F, 0F, 13F, 20F, 20),
            new Expected(GunType.RIFLE, "mithriline", 500F, 23.3333F, 20F, 20F, 0F, 2.1667F, 120F, 120),
            new Expected(GunType.RIFLE, "void", 2000F, 30F, 6.6667F, 3.3333F, 0F, 13F, 80F, 80),
            new Expected(GunType.RIFLE, "demonic", 5000F, 43.3333F, 3.3333F, 0F, 0F, 2.1667F, 200F, 200),
            new Expected(GunType.SNIPER, "mru", 200F, 26.6667F, 8.3333F, 10F, 45F, 0.5F, 3.3333F, 3),
            new Expected(GunType.SNIPER, "pale", 266.6667F, 36.6667F, 13.3333F, 3.3333F, 15F, 1F, 3.3333F, 3),
            new Expected(GunType.SNIPER, "mithriline", 500F, 46.6667F, 20F, 20F, 90F, 0.1667F, 20F, 20),
            new Expected(GunType.SNIPER, "void", 2000F, 60F, 6.6667F, 3.3333F, 30F, 1F, 13.3333F, 13),
            new Expected(GunType.SNIPER, "demonic", 5000F, 86.6667F, 3.3333F, 0F, 15F, 0.1667F, 33.3333F, 33),
            new Expected(GunType.GATLING, "mru", 200F, 13.3333F, 8.3333F, 10F, 0F, 22.5F, 66.6667F, 66),
            new Expected(GunType.GATLING, "pale", 266.6667F, 18.3333F, 13.3333F, 3.3333F, 0F, 45F, 66.6667F, 66),
            new Expected(GunType.GATLING, "mithriline", 500F, 23.3333F, 20F, 20F, 0F, 7.5F, 400F, 400),
            new Expected(GunType.GATLING, "void", 2000F, 30F, 6.6667F, 3.3333F, 0F, 45F, 266.6667F, 266),
            new Expected(GunType.GATLING, "demonic", 5000F, 43.3333F, 3.3333F, 0F, 0F, 7.5F, 666.6666F, 666));

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) <= EPS;
    }

    private static void check(GameTestHelper helper, String what, float actual, float expected) {
        helper.assertTrue(near(actual, expected), what + "：期望 " + expected + "，实际 " + actual);
    }

    // ------------------------------------------------------------------
    // 属性计算
    // ------------------------------------------------------------------

    /** 4 种枪 × 5 种材料（三个部件同材料）逐项对原作；外加混搭部件、透镜 / 瞄具只贡献 balance / zoom、每发 MRU、冷却、装填 tick */
    @GameTest(template = EMPTY)
    public static void gunStatsMatchOriginal(GameTestHelper helper) {
        for (Expected e : ORIGINAL) {
            GunStats s = GunStats.of(e.type(), GunParts.uniform(e.material()));
            String tag = e.type().id() + "/" + e.material();
            check(helper, tag + " 耐久", s.durability(), e.durability());
            check(helper, tag + " 伤害", s.damage(), e.damage());
            check(helper, tag + " 装填", s.reload(), e.reload());
            check(helper, tag + " 后坐", s.recoil(), e.recoil());
            check(helper, tag + " speed", s.speed(), e.speed());
            check(helper, tag + " 散布", s.spread(), e.spread());
            check(helper, tag + " shots", s.shots(), e.shots());
            helper.assertTrue(s.magazine() == e.magazine(), tag + " 弹量：期望 " + e.magazine() + "，实际 " + s.magazine());
            helper.assertTrue(s.zoom() == 0F && s.balance() == 0, tag + " 没装透镜 / 瞄具时 zoom、balance 应为 0");
        }

        // 混搭：枪托魔力锭、握把米斯莱、机栓淡青（各部件主导的项取全值、其余 ÷3）
        GunStats mixed = GunStats.of(GunType.PISTOL, new GunParts("mru", "mithriline", "pale", null, null));
        check(helper, "混搭 耐久", mixed.durability(), 393.3333F);
        check(helper, "混搭 伤害", mixed.damage(), 18.3333F);
        check(helper, "混搭 装填", mixed.reload(), 5.8333F);
        check(helper, "混搭 后坐", mixed.recoil(), 14.6667F);
        check(helper, "混搭 散布", mixed.spread(), 7.3333F);
        check(helper, "混搭 speed", mixed.speed(), 5.3333F);
        check(helper, "混搭 shots", mixed.shots(), 6.6667F);
        helper.assertTrue(mixed.magazine() == 6, "混搭弹量应为 6，实际 " + mixed.magazine());

        // 透镜只有 balance、瞄具只有 zoom，别的属性一项不变
        GunStats plain = GunStats.of(GunType.PISTOL, GunParts.uniform("mru"));
        GunStats dressed = GunStats.of(GunType.PISTOL, GunParts.uniform("mru").withLens("chaos").withScope("mru"));
        helper.assertTrue(dressed.balance() == 1 && near(dressed.zoom(), 1.2F), "混沌透镜 balance 1、普通瞄具 1.2 倍");
        helper.assertTrue(dressed.damage() == plain.damage() && dressed.durability() == plain.durability() && dressed.reload() == plain.reload()
                && dressed.recoil() == plain.recoil() && dressed.speed() == plain.speed() && dressed.spread() == plain.spread()
                && dressed.shots() == plain.shots(), "透镜、瞄具不应改变别的属性");
        helper.assertTrue(GunStats.of(GunType.PISTOL, GunParts.uniform("mru").withLens("frozen")).balance() == 2
                && GunStats.of(GunType.PISTOL, GunParts.uniform("mru").withLens("pure")).balance() == 3
                && GunStats.of(GunType.PISTOL, GunParts.uniform("mru").withLens("shade")).balance() == 4, "透镜 balance 应为 冰冻 2、魔力 3、暗影 4");
        helper.assertTrue(near(GunStats.of(GunType.SNIPER, GunParts.uniform("mru").withScope("mru")).zoom(), 3F)
                && near(GunStats.of(GunType.SNIPER, GunParts.uniform("mru").withScope("pale")).zoom(), 5F)
                && near(GunStats.of(GunType.SNIPER, GunParts.uniform("mru").withScope("mithriline")).zoom(), 7F)
                && near(GunStats.of(GunType.SNIPER, GunParts.uniform("mru").withScope("void")).zoom(), 12F)
                && near(GunStats.of(GunType.SNIPER, GunParts.uniform("mru").withScope("demonic")).zoom(), 16F), "狙击镜倍率 3 / 5 / 7 / 12 / 16");
        helper.assertTrue(GunStats.of(GunType.GATLING, GunParts.uniform("mru").withScope("mru")).zoom() == 0F, "加特林没有瞄具表，写了也不算");
        helper.assertTrue(GunStats.of(GunType.PISTOL, GunParts.uniform("mru").withScope("pale")).zoom() == 0F, "手枪的瞄具表里没有 pale");
        GunStats unknown = GunStats.of(GunType.PISTOL, new GunParts("no_such", "mru", "mru", "no_such", "no_such"));
        helper.assertTrue(unknown.balance() == 0 && unknown.zoom() == 0F && unknown.reload() > 0F, "认不出的部件当不存在，不抛异常");

        // 每发 MRU（伤害 × 10 / 3 / 10 / 2 向零取整）、冷却 ceil(speed × 2)、装填 floor(reload × 20)
        helper.assertTrue(GunType.PISTOL.mruPerShot(plain) == 133, "魔力锭手枪每发 133 MRU，实际 " + GunType.PISTOL.mruPerShot(plain));
        GunStats rifle = GunStats.of(GunType.RIFLE, GunParts.uniform("mru"));
        GunStats gatling = GunStats.of(GunType.GATLING, GunParts.uniform("mru"));
        helper.assertTrue(GunType.RIFLE.mruPerShot(rifle) == 40, "魔力锭步枪每发 40 MRU，实际 " + GunType.RIFLE.mruPerShot(rifle));
        helper.assertTrue(GunType.GATLING.mruPerShot(gatling) == 26, "魔力锭加特林每发 26 MRU，实际 " + GunType.GATLING.mruPerShot(gatling));
        helper.assertTrue(plain.cooldownTicks() == 10, "魔力锭手枪射击间隔 10 tick，实际 " + plain.cooldownTicks());
        helper.assertTrue(GunStats.of(GunType.SNIPER, GunParts.uniform("mithriline")).cooldownTicks() == 180, "米斯莱狙击间隔 180 tick");
        helper.assertTrue(GunStats.of(GunType.PISTOL, GunParts.uniform("pale")).cooldownTicks() == 4, "淡青手枪 speed 1.667 → 间隔 4 tick");
        helper.assertTrue(rifle.cooldownTicks() == 0 && gatling.cooldownTicks() == 0, "步枪、加特林 speed 为 0，没有单发冷却");
        helper.assertTrue(plain.reloadTicks() == 83, "魔力锭手枪装填 floor(4.1667 × 20) = 83 tick，实际 " + plain.reloadTicks());
        helper.assertTrue(plain.durabilityInt() == 200, "耐久取整 200");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 材料表：已开放
    // ------------------------------------------------------------------

    /** 材料物品没注册时这种材料在工作台里不认、随机组装不抽；注册了（第六、七批）就自动开放 */
    @GameTest(template = EMPTY)
    public static void unregisteredMaterialIgnored(GameTestHelper helper) {
        for (GunMaterials.Body body : GunMaterials.BODIES) {
            boolean registered = ForgeRegistries.ITEMS.containsKey(body.item());
            helper.assertTrue(body.available() == registered, body.id() + " 的开放状态应等于物品注册了没有");
            helper.assertTrue(GunMaterials.availableBodies().contains(body) == registered, body.id() + " 在已开放列表里的状态不对");
            if (registered) {
                ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(body.item()));
                helper.assertTrue(GunMaterials.bodyByItem(stack).orElse(null) == body, body.id() + " 的物品应认成这种材料");
            }
        }
        for (String id : List.of("mru", "pale", "mithriline")) {
            helper.assertTrue(GunMaterials.body(id).map(GunMaterials.Body::available).orElse(false), id + " 的材料在前几批已注册，应开放");
        }
        // demonic 的 ackronite_ingot 第七批才注册：没注册时按 id 仍查得到（老枪属性照算），但不开放。
        // void 的 void_plating 第六批 6a 已注册（ECVoid），由上面「注册了就开放」那段查；这里的循环对它不再断言（留着，以后拆模块时仍有用）
        for (String id : List.of("void", "demonic")) {
            GunMaterials.Body body = GunMaterials.body(id).orElseThrow();
            if (!ForgeRegistries.ITEMS.containsKey(body.item())) {
                helper.assertFalse(body.available(), id + " 没注册，不应开放");
                helper.assertTrue(GunMaterials.availableScopes(GunType.SNIPER).stream().noneMatch(s -> s.id().equals(id)), id + " 狙击镜不应开放");
            }
        }
        helper.assertTrue(GunMaterials.bodyByItem(new ItemStack(Items.DIRT)).isEmpty(), "泥土不是枪身材料");
        helper.assertTrue(GunMaterials.bodyByItem(ItemStack.EMPTY).isEmpty(), "空物品不是材料");

        Item ingot = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("magical_ingot"));
        Item alloy = ForgeRegistries.ITEMS.getValue(GunMaterials.SNIPER_SCOPE_HOUSING);
        Item sunGlass = ForgeRegistries.ITEMS.getValue(GunMaterials.GATLING_LENS_HOUSING);
        Item chaos = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("chaos_matrix_projection"));
        helper.assertTrue(GunMaterials.scopeByItem(GunType.SNIPER, new ItemStack(ingot)).map(GunMaterials.Scope::zoom).orElse(0F) == 3F,
                "魔力锭是 3 倍狙击镜材");
        helper.assertTrue(GunMaterials.scopeByItem(GunType.PISTOL, new ItemStack(ingot)).isEmpty(), "魔力锭不是手枪的普通瞄具");
        helper.assertTrue(GunMaterials.scopeByItem(GunType.PISTOL, new ItemStack(alloy)).map(GunMaterials.Scope::zoom).orElse(0F) == 1.2F,
                "魔力纯化玻璃合金是手枪的普通瞄具（1.2 倍）");
        helper.assertTrue(GunMaterials.scopeByItem(GunType.GATLING, new ItemStack(alloy)).isEmpty(), "加特林没有瞄具");
        helper.assertTrue(GunMaterials.scopeByItem(GunType.SNIPER, new ItemStack(alloy)).isEmpty(), "玻璃合金是狙击的镜座，不是镜材");
        helper.assertTrue(GunMaterials.isSniperScopeHousing(new ItemStack(alloy)) && !GunMaterials.isSniperScopeHousing(new ItemStack(ingot)), "狙击镜座认错");
        helper.assertTrue(GunMaterials.isGatlingLensHousing(new ItemStack(sunGlass)) && !GunMaterials.isGatlingLensHousing(new ItemStack(alloy)), "加特林透镜座认错");
        helper.assertTrue(GunMaterials.lensByItem(new ItemStack(chaos)).map(GunMaterials.Lens::balance).orElse(0) == 1, "混沌矩阵投影是混沌透镜");
        helper.assertTrue(GunMaterials.lensByItem(new ItemStack(ingot)).isEmpty(), "魔力锭不是透镜");
        helper.assertTrue(GunMaterials.availableLenses().size() == 4, "四种矩阵投影都已注册，透镜应全开放");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 工作台布局
    // ------------------------------------------------------------------

    /** 照原作 ContainerWeaponBench：格数、0 号输出格、每种部件的格数、必填的镜座 / 透镜座、坐标不重叠 */
    @GameTest(template = EMPTY)
    public static void gunLayoutsMatchOriginal(GameTestHelper helper) {
        int[] sizes = {10, 13, 14, 19};
        int[][] partCounts = {{3, 1, 1}, {4, 1, 3}, {3, 2, 2}, {7, 2, 4}};   // 枪托、机栓、握把（= 全套同材料要几个锭）
        for (GunType type : GunType.values()) {
            GunType.Layout layout = type.layout();
            int i = type.ordinal();
            helper.assertTrue(layout.size() == sizes[i], type.id() + " 格数应为 " + sizes[i] + "，实际 " + layout.size());
            GunType.Slot out = layout.slot(GunType.OUTPUT_SLOT);
            helper.assertTrue(out.role() == SlotRole.OUTPUT && out.x() == 152 && out.y() == 37, type.id() + " 0 号应是 (152, 37) 的输出格");
            helper.assertTrue(layout.indicesOf(SlotRole.OUTPUT).length == 1, type.id() + " 只能有一个输出格");
            helper.assertTrue(layout.indicesOf(SlotRole.CORE).length == 1 && layout.indicesOf(SlotRole.SOUL).length == 1, type.id() + " 核心、灵魂各一格");
            helper.assertTrue(layout.indicesOf(SlotRole.CORE)[0] == 1 && layout.indicesOf(SlotRole.SOUL)[0] == 2, type.id() + " 核心 1 号、灵魂 2 号");
            helper.assertTrue(layout.indicesOf(SlotRole.BASE).length == partCounts[i][0]
                    && layout.indicesOf(SlotRole.DEVICE).length == partCounts[i][1]
                    && layout.indicesOf(SlotRole.HANDLE).length == partCounts[i][2], type.id() + " 枪托 / 机栓 / 握把格数不对");
            Set<Long> coords = new HashSet<>();
            for (int slot = 0; slot < layout.size(); slot++) {
                helper.assertTrue(layout.slot(slot).index() == slot, type.id() + " 格子序号应连续");
                helper.assertTrue(coords.add(((long) layout.slot(slot).x() << 16) | layout.slot(slot).y()), type.id() + " 有两格坐标重叠");
            }
            helper.assertTrue(layout.role(-1) == null && layout.role(layout.size()) == null, "越界下标应返回 null");
        }
        GunType.Layout pistol = GunType.PISTOL.layout();
        helper.assertTrue(Arrays.equals(pistol.indicesOf(SlotRole.LENS_OPT), new int[]{3}) && Arrays.equals(pistol.indicesOf(SlotRole.SCOPE_OPT), new int[]{4}),
                "手枪 3 号可选透镜、4 号可选瞄具");
        helper.assertTrue(pistol.slot(3).x() == 20 && pistol.slot(3).y() == 17 && pistol.slot(9).x() == 80 && pistol.slot(9).y() == 57, "手枪坐标不对");
        GunType.Layout sniper = GunType.SNIPER.layout();
        helper.assertTrue(Arrays.equals(sniper.indicesOf(SlotRole.SCOPE_HOUSING), new int[]{4}) && Arrays.equals(sniper.indicesOf(SlotRole.SCOPE), new int[]{5, 6})
                && !sniper.has(SlotRole.SCOPE_OPT), "狙击 4 号镜座、5 / 6 号镜材");
        helper.assertTrue(sniper.slot(4).x() == 60 && sniper.slot(4).y() == 17, "狙击镜座在 (60, 17)");
        GunType.Layout gatling = GunType.GATLING.layout();
        helper.assertTrue(Arrays.equals(gatling.indicesOf(SlotRole.LENS_HOUSING), new int[]{3}) && Arrays.equals(gatling.indicesOf(SlotRole.LENS), new int[]{4, 5})
                && !gatling.has(SlotRole.SCOPE) && !gatling.has(SlotRole.SCOPE_OPT) && !gatling.has(SlotRole.LENS_OPT), "加特林 3 号透镜座、4 / 5 号透镜、没有瞄具");
        helper.assertTrue(gatling.slot(18).x() == 120 && gatling.slot(18).y() == 57, "加特林最后一格握把在 (120, 57)");
        helper.succeed();
    }

    /** 枪种的固定数据：注册名、开火方式、后坐、瞄具种类 */
    @GameTest(template = EMPTY)
    public static void gunTypeData(GameTestHelper helper) {
        helper.assertTrue(GunType.PISTOL.itemId().equals(EssentialCraft5.id("pistol"))
                && GunType.GATLING.makerBlockId().equals(EssentialCraft5.id("gatling_weapon_maker")), "注册名不对");
        helper.assertTrue(GunType.byId("sniper") == GunType.SNIPER && GunType.byId("cannon") == null, "按 id 查不对");
        helper.assertTrue(GunType.ofMaker(EssentialCraft5.id("rifle_weapon_maker")).orElse(null) == GunType.RIFLE, "按工作台注册名查不对");
        helper.assertTrue(!GunType.PISTOL.automatic() && GunType.RIFLE.automatic() && GunType.RIFLE.autoInterval() == 3
                && GunType.GATLING.autoInterval() == 1 && GunType.GATLING.spinUpTicks() == 60 && !GunType.SNIPER.automatic(), "开火方式不对");
        helper.assertTrue(GunType.PISTOL.hasRecoil() && GunType.SNIPER.hasRecoil() && !GunType.RIFLE.hasRecoil() && !GunType.GATLING.hasRecoil(),
                "只有手枪、狙击有后坐");
        helper.assertTrue(GunType.PISTOL.mruFactor() == 10 && GunType.RIFLE.mruFactor() == 3 && GunType.SNIPER.mruFactor() == 10
                && GunType.GATLING.mruFactor() == 2, "每发 MRU 系数 10 / 3 / 10 / 2");
        helper.assertTrue(GunType.GATLING.scopeKind() == GunType.ScopeKind.NONE && GunType.SNIPER.scopeKind() == GunType.ScopeKind.SNIPER_REQUIRED
                && GunType.RIFLE.scopeKind() == GunType.ScopeKind.NORMAL_OPTIONAL, "瞄具种类不对");
        helper.assertTrue(GunType.of(new ItemStack(Items.BOW)).isEmpty() && GunStats.of(new ItemStack(Items.BOW)).isEmpty(), "弓不是枪");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    /** 部件写进 gun 子标签、读回一样；fired / wear 独立；透镜清掉删键；缺部件不算组装好；不碰顶层的 mru 键 */
    @GameTest(template = EMPTY)
    public static void gunPartsNbt(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STICK);
        helper.assertFalse(GunParts.isAssembled(stack), "没有子标签不算组装好");
        helper.assertTrue(GunParts.fired(stack) == 0 && GunParts.wear(stack) == 0, "没有子标签时 fired / wear 为 0");
        stack.getOrCreateTag().putInt("mru", 1234);
        GunParts parts = new GunParts("mru", "pale", "mithriline", "chaos", "mru");
        parts.writeTo(stack);
        GunParts.setFired(stack, 2);
        GunParts.setWear(stack, 7);
        helper.assertTrue(GunParts.read(stack).orElse(null) != null && GunParts.read(stack).get().equals(parts), "读回的部件应一样");
        helper.assertTrue(GunParts.fired(stack) == 2 && GunParts.wear(stack) == 7, "fired / wear 存读不对");
        parts.withLens(null).writeTo(stack);
        helper.assertFalse(stack.getTagElement(GunParts.TAG).contains("lens"), "透镜为 null 时应删掉 lens 键");
        helper.assertTrue(GunParts.fired(stack) == 2 && GunParts.wear(stack) == 7, "重写部件不应清掉 fired / wear");
        helper.assertTrue(stack.getTag().getInt("mru") == 1234, "不应碰顶层的 mru 键");
        GunParts.setFired(stack, -5);
        helper.assertTrue(GunParts.fired(stack) == 0, "fired 不能为负");
        stack.getTagElement(GunParts.TAG).remove("device");
        helper.assertFalse(GunParts.isAssembled(stack), "缺机栓不算组装好");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 伤害类型、配置、光束包、音效
    // ------------------------------------------------------------------

    /** mru_ray（D1 A）：不带 is_projectile，直接实体为空、causing = 射手、没有位置；不在 bypasses_armor / no_death_loot 里 */
    @GameTest(template = EMPTY)
    public static void mruRayDamageType(GameTestHelper helper) {
        Holder<DamageType> holder = ECDamageTypes.holder(helper.getLevel(), ECGuns.MRU_RAY);
        DamageType type = holder.value();
        helper.assertTrue("essentialcraft5.mru_ray".equals(type.msgId()), "message_id 不对：" + type.msgId());
        helper.assertTrue(type.scaling() == DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, "scaling 应为 when_caused_by_living_non_player");
        helper.assertTrue(Math.abs(type.exhaustion() - 0.1F) < 1e-6F, "exhaustion 应为 0.1");
        helper.assertFalse(holder.is(DamageTypeTags.IS_PROJECTILE), "D1 A：不应带 minecraft:is_projectile（末影人会瞬移躲开）");
        helper.assertFalse(holder.is(DamageTypeTags.BYPASSES_ARMOR), "护甲应照算");
        helper.assertFalse(holder.is(ECDamageTypes.NO_DEATH_LOOT), "打死的怪应照常掉落");

        ServerPlayer shooter = ProtectionFrameworkTests.realPlayer(helper);
        DamageSource source = new DamageSource(holder, null, shooter);
        helper.assertTrue(source.getEntity() == shooter && source.getDirectEntity() == null, "causing = 射手、直接实体为空");
        helper.assertTrue(source.getSourcePosition() == null, "没有位置（盾挡不住，同原作）");
        helper.assertTrue(source.is(ECGuns.MRU_RAY), "伤害类型应是 mru_ray");
        helper.succeed();
    }

    /** general.guns 三项：读的是配置（默认 100 / 1.0 / 32），测试覆盖值优先、置回 null 恢复 */
    @GameTest(template = EMPTY)
    public static void gunConfigOverrides(GameTestHelper helper) {
        helper.assertTrue(GunConfig.matrixDamagePerDamage != null && GunConfig.matrixDamagePerDamage.getDefault() == 100
                && GunConfig.playerDamageMultiplier.getDefault() == 1.0D && GunConfig.maxTargetsPerShot.getDefault() == 32, "默认值应为 100 / 1.0 / 32");
        try {
            GunConfig.setTestOverrides(5, 0.5D, 3);
            helper.assertTrue(GunConfig.matrixDamagePerDamage() == 5 && GunConfig.playerDamageMultiplier() == 0.5D
                    && GunConfig.maxTargetsPerShot() == 3, "测试覆盖值应优先");
        } finally {
            GunConfig.setTestOverrides(null, null, null);
        }
        helper.assertTrue(GunConfig.matrixDamagePerDamage() == ECConfig.get(GunConfig.matrixDamagePerDamage)
                && GunConfig.playerDamageMultiplier() == ECConfig.get(GunConfig.playerDamageMultiplier)
                && GunConfig.maxTargetsPerShot() == ECConfig.get(GunConfig.maxTargetsPerShot), "置回 null 后应按配置");
        helper.succeed();
    }

    /** 光束包：协议号至少 4（第六批 6a 升到 5，见 HoannaFrameworkTests）；射手实体 id 原样带过去；远离原点的起点不丢精度、终点偏移 float 足够；balance 夹到 0~4；音效 sound.beam 已注册 */
    @GameTest(template = EMPTY)
    public static void gunBeamPacketAndSound(GameTestHelper helper) {
        helper.assertTrue(Integer.parseInt(ECNetwork.PROTOCOL) >= 4, "第五批加了光束包，协议号应至少为 4，实际 " + ECNetwork.PROTOCOL);
        Vec3 from = new Vec3(1_234_567.25D, 70.62D, -2_345_678.5D);
        Vec3 to = from.add(40.5D, -3.25D, 50.125D);
        S2CGunBeamPacket back = roundTrip(new S2CGunBeamPacket(123_456, from, to, 3));
        helper.assertTrue(back.shooter() == 123_456 && back.from().distanceTo(from) < 1e-9 && back.to().distanceTo(to) < 1e-3 && back.balance() == 3,
                "存读后光束不一样：" + back);
        helper.assertTrue(roundTrip(new S2CGunBeamPacket(1, from, to, 9)).balance() == 4
                && roundTrip(new S2CGunBeamPacket(1, from, to, -2)).balance() == 0, "balance 应夹到 0~4");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        S2CGunBeamPacket.encode(new S2CGunBeamPacket(-1, from, to, 0), buf);
        helper.assertTrue(buf.readableBytes() == 41, "光束包应为 41 字节，实际 " + buf.readableBytes());
        buf.release();

        ResourceLocation beam = EssentialCraft5.id("sound.beam");
        helper.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(beam) && ECGuns.BEAM.get().getLocation().equals(beam), "sound.beam 应已注册");
        helper.succeed();
    }

    private static S2CGunBeamPacket roundTrip(S2CGunBeamPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CGunBeamPacket.encode(packet, buf);
            return S2CGunBeamPacket.decode(buf);
        } finally {
            buf.release();
        }
    }
}
