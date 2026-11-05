package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.corruption.Affliction;
import com.flameforge.essentialcraft5.corruption.Afflictions;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorEvents;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.VerdictBladeItem;
import com.flameforge.essentialcraft5.item.WindGearEvents;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData.Element;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 展示厅的第十三批 13b 部分（装备增强，只在 -PecShowroom13b 时跑；batch13.md §12.3）。截图名都以 {@code b13b_} 开头，存成 {@code ec5_b13b_*.png}。
 * <p>
 * <b>场景</b>（镜头从南边朝北拍）：
 * <ul>
 *   <li>{@code b13b_armor_gallery}：五个盔甲架，魔力防护、风之、虚空、审判庭四套各穿顶阶（看光泽），第五个手里拿 5 阶裁决之刃、副手 2 阶余烬元素剑；</li>
 *   <li>{@code b13b_charge_bar*}：开着 HUD、第一人称，穿 2 阶魔力防护套装，四件存量六成（紫字）、一成二（红字），最后再加一项后遗症看方框往左让一格；</li>
 *   <li>{@code b13b_wind_air_jump*}：第三人称从背后拍，穿 2 阶风之护甲（两次空中跳），临时切成生存模式（创造模式能飞，空中跳不认），
 *       从 8 格高处往下掉，客户端照按跳跃键那样跳两次（{@link WindGearEvents#airJump} + {@link WindGearEvents#airJumpEffects}），脚下各一团风；</li>
 *   <li>{@code b13b_conviction*}：穿 2 阶审判庭套装，对一只不带 AI 的尸壳记一次定罪（{@link InquisitoriumArmorEvents#convict}），拍那一下冒的紫色粒子；</li>
 *   <li>{@code b13b_verdict*}：第三人称斜着往下看，手拿 3 阶满电裁决之刃站在五只尸壳中间，放一次断罪（{@link VerdictBladeItem#judgment}），
 *       拍脚下那一圈附魔粒子和每只身上的横扫。尸壳血量调高，挨一下不会死。</li>
 * </ul>
 * <b>界面</b>：四套盔甲胸甲各阶的提示框（带「满电约挡」那一行的是魔力防护和虚空）、虚空靴子、裁决之刃 1 阶和 5 阶、2 阶余烬元素剑（能升格那一行）。
 * 手册页见 {@link DevShowroom} 的 {@code batch13bGuidePages}。
 */
public final class DevShowroomBatch13b {

    private DevShowroomBatch13b() {
    }

    /** 拍完删掉的实体（盔甲架、尸壳） */
    private static final List<Entity> SPAWNED = new ArrayList<>();
    /** 断罪那一场的尸壳（每场开拍时补齐） */
    private static final List<Husk> VERDICT_TARGETS = new ArrayList<>();
    @Nullable
    private static Husk convictionTarget;
    private static boolean built;

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        built = true;
        buildGalleryAndChargeBar(level, sceneBase.apply(firstIndex));
        buildAirJump(sceneBase.apply(firstIndex + 1));
        buildConviction(sceneBase.apply(firstIndex + 2));
        buildVerdict(sceneBase.apply(firstIndex + 3));
        buildScreens();
    }

    /** 服务端：场景拍完换回创造模式、脱掉盔甲、清后遗症和定罪表、删掉生成的实体（DevShowroom.finishScenes 调；没跑过 build 时什么都不做） */
    static void finish(ServerPlayer player) {
        if (!built) {
            return;
        }
        player.setGameMode(GameType.CREATIVE);
        Afflictions.clear(player);
        InquisitoriumArmorEvents.forgetForTest(player);
        undress(player);
        hold(player, ItemStack.EMPTY);
        for (Entity entity : SPAWNED) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        SPAWNED.clear();
        VERDICT_TARGETS.clear();
        convictionTarget = null;
    }

    /** 客户端：场景拍完切回第一人称（DevShowroom 场景收尾时调） */
    static void clientFinish(Minecraft mc) {
        if (built) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    // ================================================================ 小工具

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    private static void scene(String name, @Nullable Consumer<ServerPlayer> prepare, @Nullable Vec3 eye, @Nullable Vec3 target, int delay,
                              @Nullable Consumer<Minecraft> client) {
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, client, null));
    }

    private static void easy(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null && server.getWorldData().getDifficulty() != Difficulty.EASY) {
            server.setDifficulty(Difficulty.EASY, true);
        }
    }

    private static void hold(ServerPlayer player, ItemStack stack) {
        player.getInventory().setItem(player.getInventory().selected, stack);
    }

    /**
     * 满电、同调 stage 阶（大于这一族的顶阶时取顶阶）的一件。先设阶再充：虚空、魔力防护的容量随阶变大，
     * 先按 0 阶充满再升阶就只剩一半
     */
    private static ItemStack attuned(Item item, int stage) {
        ItemStack stack = new ItemStack(item);
        Attunement.set(stack, Math.min(stage, Attunement.maxStage(stack)), 0);
        return charged(stack, 1F);
    }

    /** 存量改成满电的 fraction（0~1） */
    private static ItemStack charged(ItemStack stack, float fraction) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(Math.round(handler.getMaxMRU() * fraction)));
        return stack;
    }

    /** 锻好的剑身数据：水 / 火、普通 + 燃烧余烬（同 VerdictBladeTests 那把） */
    private static ForgedSwordData forged() {
        List<ResourceLocation> embers = List.of(EmberKind.COMMON.id(), EmberKind.FLAME.id(), EmberKind.COMMON.id(), EmberKind.FLAME.id());
        List<ResourceLocation> foci = List.of(Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus());
        return new ForgedSwordData(embers, foci, Element.WATER, Element.FIRE);
    }

    private static ItemStack verdictBlade(int stage) {
        ItemStack stack = MRUStaffItem.full(ECEmberForge.VERDICT_BLADE.get());
        forged().writeTo(stack);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    private static ItemStack emberSword(int stage) {
        ItemStack stack = MRUStaffItem.full(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        forged().writeTo(stack);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    /** 一套四件（头、胸、腿、脚）都是 stage 阶、存量 fraction */
    private static List<ItemStack> set(List<? extends RegistryObject<? extends ArmorItem>> pieces, int stage, float fraction) {
        List<ItemStack> stacks = new ArrayList<>(pieces.size());
        for (RegistryObject<? extends ArmorItem> piece : pieces) {
            stacks.add(charged(attuned(piece.get(), stage), fraction));
        }
        return stacks;
    }

    private static void wear(ServerPlayer player, List<ItemStack> pieces) {
        for (ItemStack piece : pieces) {
            if (piece.getItem() instanceof ArmorItem armor) {
                player.setItemSlot(armor.getEquipmentSlot(), piece);
            }
        }
    }

    private static void undress(ServerPlayer player) {
        for (EquipmentSlot slot : Attunement.ARMOR_SLOTS) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static List<RegistryObject<? extends ArmorItem>> mhs() {
        return List.of(ECGear.MHS_HELMET, ECGear.MHS_CHESTPLATE, ECGear.MHS_LEGGINGS, ECGear.MHS_BOOTS);
    }

    private static List<RegistryObject<? extends ArmorItem>> wind() {
        return List.of(ECWindGear.WIND_HELMET, ECWindGear.WIND_CHESTPLATE, ECWindGear.WIND_LEGGINGS, ECWindGear.WIND_BOOTS);
    }

    private static List<RegistryObject<? extends ArmorItem>> voidSet() {
        return List.of(ECVoidArmor.VOID_HELMET, ECVoidArmor.VOID_CHESTPLATE, ECVoidArmor.VOID_LEGGINGS, ECVoidArmor.VOID_BOOTS);
    }

    private static List<RegistryObject<? extends ArmorItem>> inquisitorium() {
        return List.of(ECInquisitorium.INQUISITORIUM_HELMET, ECInquisitorium.INQUISITORIUM_CHESTPLATE, ECInquisitorium.INQUISITORIUM_LEGGINGS,
                ECInquisitorium.INQUISITORIUM_BOOTS);
    }

    private static ArmorStand armorStand(ServerLevel level, double x, double y, double z) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) {
            throw new IllegalStateException("生成不了盔甲架");
        }
        stand.moveTo(x, y, z, 0F, 0F);
        stand.setYHeadRot(0F);
        stand.setYBodyRot(0F);
        stand.setShowArms(true);
        stand.setNoBasePlate(true);
        return stand;
    }

    /** 不带 AI、不会消失、血量调到 200 的尸壳（白天不着火），脸朝南（朝镜头） */
    @Nullable
    private static Husk husk(ServerLevel level, Vec3 pos) {
        Husk husk = EntityType.HUSK.create(level);
        if (husk == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 生成不了尸壳");
            return null;
        }
        husk.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        husk.setYHeadRot(0F);
        husk.setYBodyRot(0F);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        AttributeInstance health = husk.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(200D);
        }
        husk.setHealth(husk.getMaxHealth());
        level.addFreshEntity(husk);
        SPAWNED.add(husk);
        return husk;
    }

    // ================================================================ 场景

    /** 盔甲架一排（四套顶阶 + 拿剑的一个），同一处开着 HUD 拍电量条 */
    private static void buildGalleryAndChargeBar(ServerLevel level, BlockPos base) {
        List<List<RegistryObject<? extends ArmorItem>>> sets = List.of(mhs(), wind(), voidSet(), inquisitorium());
        for (int i = 0; i < sets.size(); i++) {
            ArmorStand stand = armorStand(level, base.getX() - 3.5D + i * 2, base.getY(), base.getZ() - 3.5D);
            for (ItemStack piece : set(sets.get(i), Attunement.MAX_STAGE, 1F)) {
                if (piece.getItem() instanceof ArmorItem armor) {
                    stand.setItemSlot(armor.getEquipmentSlot(), piece);
                }
            }
            level.addFreshEntity(stand);
            SPAWNED.add(stand);
        }
        ArmorStand swords = armorStand(level, base.getX() + 4.5D, base.getY(), base.getZ() - 3.5D);
        swords.setItemSlot(EquipmentSlot.MAINHAND, verdictBlade(VerdictBladeItem.BISECT_STAGE));
        swords.setItemSlot(EquipmentSlot.OFFHAND, emberSword(2));
        level.addFreshEntity(swords);
        SPAWNED.add(swords);

        Vec3 eye = at(base, 0.5, 1.9, 3.0);
        Vec3 look = at(base, 0.5, 1.0, -3.5);
        scene("b13b_armor_gallery", null, eye, look, DevShowroom.FIRST_SCENE_WAIT, null);
        scene("b13b_armor_gallery_close", null, at(base, -0.5, 1.7, 0.2), at(base, -0.5, 1.1, -3.5), 15, null);

        // 电量条：穿 2 阶魔力防护四件、各存六成，开着 HUD 拍快捷栏左边那个方框
        Consumer<Minecraft> hud = mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
        };
        scene("b13b_charge_bar", p -> {
            Afflictions.clear(p);
            wear(p, set(mhs(), 2, 0.6F));
        }, eye, look, 20, hud);
        // 不到两成：百分比变红
        scene("b13b_charge_bar_low", p -> wear(p, set(mhs(), 2, 0.12F)), eye, look, 10, hud);
        // 再来一项后遗症：电量条占最靠快捷栏那一格，后遗症的方框往左让一格
        scene("b13b_charge_bar_affliction", p -> Afflictions.give(p, Affliction.HEAVY_HANDS, 1200, 0), eye, look, 15, hud);
        // 虚空套装也是护盾盔甲：同一个方框，图标换成虚空胸甲
        scene("b13b_charge_bar_void", p -> {
            Afflictions.clear(p);
            wear(p, set(voidSet(), 1, 0.45F));
        }, eye, look, 15, hud);
    }

    /**
     * 风之护甲空中跳：第一张切生存、穿 2 阶全套、传到 8 格高处往下掉（第三人称背后）；后两张客户端各跳一次。
     * 两张之间隔着「等 delay + 下一张开始前 5 刻」，掉得不多，第二跳时离地面还远
     */
    private static void buildAirJump(BlockPos base) {
        Consumer<ServerPlayer> lift = p -> {
            Afflictions.clear(p);
            undress(p);
            wear(p, set(wind(), 2, 1F));
            p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.resetFallDistance();
            p.connection.teleport(base.getX() + 0.5D, base.getY() + 9D, base.getZ() - 3.5D, 180F, 25F);
        };
        Consumer<Minecraft> thirdPerson = mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        scene("b13b_wind_air_jump_fall", lift, null, null, 6, thirdPerson);
        scene("b13b_wind_air_jump", null, null, null, 3, mc -> jumpOnce(mc, 0));
        scene("b13b_wind_air_jump_second", null, null, null, 3, mc -> jumpOnce(mc, 1));
    }

    /** 客户端：照按跳跃键那样空中跳一次（used = 这次离地以后已经跳过几次） */
    private static void jumpOnce(Minecraft mc, int used) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (WindGearEvents.airJump(player, used)) {
            WindGearEvents.airJumpEffects(player);
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] 空中跳没跳起来（第 {} 次；在地上 {}，能飞 {}）", used + 1, player.onGround(),
                    player.getAbilities().mayfly);
        }
    }

    /** 审判庭定罪：一只尸壳，穿 2 阶全套站在它南边 4 格，记一次定罪，拍那一下的紫色粒子 */
    private static void buildConviction(BlockPos base) {
        Vec3 eye = at(base, 0.5, 1.8, 0.5);
        Vec3 look = at(base, 0.5, 1.2, -3.5);
        Consumer<ServerPlayer> stand = p -> {
            // 上一张还在空中：先回创造模式（不摔），再脱掉风之护甲
            p.setGameMode(GameType.CREATIVE);
            undress(p);
            easy(p);
            wear(p, set(inquisitorium(), InquisitoriumArmorEvents.CONVICTION_STAGE, 1F));
            if (convictionTarget == null || !convictionTarget.isAlive()) {
                convictionTarget = husk(p.serverLevel(), at(base, 0.5, 0, -3.5));
            }
        };
        Consumer<Minecraft> firstPerson = mc -> mc.options.setCameraType(CameraType.FIRST_PERSON);
        scene("b13b_conviction_stand", stand, eye, look, 30, firstPerson);
        Consumer<ServerPlayer> convict = p -> {
            InquisitoriumArmorEvents.forgetForTest(p);
            if (convictionTarget == null || !InquisitoriumArmorEvents.convict(p, convictionTarget)) {
                EssentialCraft5.LOGGER.warn("[showroom] 定罪没记上（全套阶 {}）", InquisitoriumArmorEvents.setStage(p));
            }
        };
        scene("b13b_conviction", convict, eye, look, 3, null);
    }

    /**
     * 断罪：站在五只尸壳中间（都在半径内），第三人称斜着往下看（脚下那一圈半径 6 的附魔粒子大半在画面里），手拿 3 阶满电裁决之刃；
     * 第二张放一次断罪，三刻后拍
     */
    private static void buildVerdict(BlockPos base) {
        Vec3 eye = at(base, 0.5, 1.62, -3.5);
        Vec3 look = at(base, 0.5, 1.62 - 4.2, -8.5);
        Consumer<ServerPlayer> stance = p -> {
            undress(p);
            InquisitoriumArmorEvents.forgetForTest(p);
            easy(p);
            hold(p, verdictBlade(3));
            p.getCooldowns().removeCooldown(ECEmberForge.VERDICT_BLADE.get());
            VERDICT_TARGETS.removeIf(husk -> !husk.isAlive());
            if (VERDICT_TARGETS.isEmpty()) {
                double[][] spots = {{-2.5, -1.5}, {-1.0, -3.0}, {1.0, -3.0}, {2.5, -1.5}, {0.0, -4.5}};
                for (double[] spot : spots) {
                    Husk husk = husk(p.serverLevel(), at(base, 0.5 + spot[0], 0, -3.5 + spot[1]));
                    if (husk != null) {
                        VERDICT_TARGETS.add(husk);
                    }
                }
            }
        };
        Consumer<Minecraft> thirdPerson = mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        scene("b13b_verdict_stance", stance, eye, look, 30, thirdPerson);
        Consumer<ServerPlayer> judgment = p -> {
            VerdictBladeItem.Judgment result = VerdictBladeItem.judgment(p, p.getItemInHand(InteractionHand.MAIN_HAND));
            if (result.outcome() != VerdictBladeItem.Outcome.DONE) {
                EssentialCraft5.LOGGER.warn("[showroom] 断罪没放出来：{}", result.outcome());
            }
        };
        scene("b13b_verdict_judgment", judgment, null, null, 3, null);
        // 收尾：切回第一人称、手里清空（后面是界面截图）
        scene("b13b_verdict_after", p -> hold(p, ItemStack.EMPTY), at(base, 0.5, 2.4, 2.0), at(base, 0.5, 0.8, -6.0), 15,
                mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    // ================================================================ 界面

    private static void buildScreens() {
        tooltip("b13b_mhs_chestplate_stage0", () -> attuned(ECGear.MHS_CHESTPLATE.get(), 0));
        tooltip("b13b_mhs_chestplate_stage2", () -> charged(attuned(ECGear.MHS_CHESTPLATE.get(), 2), 0.7F));
        tooltip("b13b_wind_chestplate_stage0", () -> attuned(ECWindGear.WIND_CHESTPLATE.get(), 0));
        tooltip("b13b_wind_chestplate_stage3", () -> attuned(ECWindGear.WIND_CHESTPLATE.get(), 3));
        tooltip("b13b_void_chestplate_stage3", () -> attuned(ECVoidArmor.VOID_CHESTPLATE.get(), 3));
        tooltip("b13b_void_boots_stage2", () -> attuned(ECVoidArmor.VOID_BOOTS.get(), 2));
        tooltip("b13b_inquisitorium_chestplate_stage0", () -> attuned(ECInquisitorium.INQUISITORIUM_CHESTPLATE.get(), 0));
        tooltip("b13b_inquisitorium_chestplate_stage4", () -> attuned(ECInquisitorium.INQUISITORIUM_CHESTPLATE.get(), InquisitoriumArmorEvents.TOP_STAGE));
        tooltip("b13b_verdict_blade_stage1", () -> verdictBlade(1));
        tooltip("b13b_verdict_blade_stage5", () -> verdictBlade(VerdictBladeItem.BISECT_STAGE));
        tooltip("b13b_ember_sword_stage2", () -> emberSword(2));
    }

    /** 一张提示框：物品在服务端造好，5 刻后在客户端开 {@link TooltipShot} */
    private static void tooltip(String name, Supplier<ItemStack> stack) {
        DevShowroom.screens.add(new DevShowroom.ScreenShot(name, p -> { }, p -> {
            ItemStack shown = stack.get();
            Minecraft.getInstance().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.getToasts().clear();
                mc.setScreen(new TooltipShot(shown));
            });
        }));
    }

    /** 只画一个物品图标和它的提示框（游戏自己的提示内容和样式），截图用，不动鼠标 */
    private static final class TooltipShot extends Screen {
        private final ItemStack stack;

        TooltipShot(ItemStack stack) {
            super(Component.empty());
            this.stack = stack;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.renderItem(stack, 16, 16);
            graphics.renderTooltip(font, stack, 30, 30);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
