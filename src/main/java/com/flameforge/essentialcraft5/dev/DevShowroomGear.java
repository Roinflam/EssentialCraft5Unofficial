package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.client.AttunementTooltips;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData.Element;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 只拍武器防具成长加强（-PecShowroomGear）：物品格左上的阶位小菱形（余烬剑、裁决之刃、MRU 枪、五套盔甲胸甲各阶，枪带过载充能条）、
 * 名字颜色与满阶渐变、战痕后缀，以及裁决之刃 / 余烬元素剑几个阶、五套盔甲胸甲 2 阶和满阶（带战痕）的提示框「同调词条」一块。每张在界面缩放 2、3、4 下各拍一次
 * （截图名 {@code 02_gui_gear_*_s<n>}）；满阶虚空胸甲和满阶裁决之刃另在缩放 3 下按背包头盔格的位置各拍一张（{@code *_slot_s3}，折行最窄）。交付前对着截图核像素：菱形不出格、不压耐久条 / 热量条 / 数量，提示框不出屏。
 * 物品说明收进 Shift 以后，最高的四件（满阶裁决之刃、审判庭 / 宅男 / 虚空胸甲）另拍不按 / 按住 Shift 两种（{@code 02_gui_gear_shift_*}，按住 Shift 由
 * {@link AttunementTooltips#setPreview} 代替）。
 * 不发按键、不动鼠标；拍完把界面缩放放回 3。
 */
final class DevShowroomGear {

    private static final int[] SCALES = {2, 3, 4};

    private DevShowroomGear() {
    }

    static void build(ServerPlayer player) {
        if (Boolean.getBoolean("essentialcraft5.showroom.baseStats")) {
            buildBaseStats();
            return;
        }
        if (Boolean.getBoolean("essentialcraft5.showroom.enchanted")) {
            buildEnchanted();
            return;
        }
        for (int scale : SCALES) {
            int s = scale;
            shot("gear_slots_s" + s, s, null, DevShowroomGear::slotRow);
            shot("gear_tip_blade5_s" + s, s, () -> scarred(blade(5), 6500), null);
            shot("gear_tip_blade3_s" + s, s, () -> blade(3), null);
            shot("gear_tip_ember2_s" + s, s, () -> ember(2), null);
            shot("gear_tip_ember1_s" + s, s, () -> ember(1), null);
            for (Supplier<Item> chest : chestplates()) {
                String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(chest.get()).getPath();
                shot("gear_tip_" + id + "_max_s" + s, s, () -> scarred(maxed(chest.get()), 6500), null);
                shot("gear_tip_" + id + "_2_s" + s, s, () -> staged(chest.get(), 2), null);
            }
        }
        // 鼠标停在生存背包头盔格上时，提示框右边能用的宽度最窄（缩放 3 约 270），长行在这里最先折行
        slotShot("gear_tip_void_chestplate_max_slot_s3", () -> scarred(maxed(ECVoidArmor.VOID_CHESTPLATE.get()), 6500));
        slotShot("gear_tip_blade5_slot_s3", () -> scarred(blade(5), 6500));
        // 物品说明收进 Shift：最高的四件在缩放 3 下各拍不按 / 按住 Shift，画在左上角和头盔格两个位置；缩放 2、4 各抽一件
        for (int i = 0; i < 2; i++) {
            boolean shift = i == 1;
            String state = shift ? "on" : "off";
            for (Supplier<ItemStack> tall : tallest()) {
                String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(tall.get().getItem()).getPath();
                shot("gear_shift_" + id + "_" + state + "_s3", 3, tall, null, false, shift);
                shot("gear_shift_" + id + "_" + state + "_slot_s3", 3, tall, null, true, shift);
            }
            shot("gear_shift_computer_chestplate_" + state + "_s2", 2, () -> scarred(maxed(ECComputer.COMPUTER_CHESTPLATE.get()), 6500), null, true, shift);
            shot("gear_shift_verdict_blade_" + state + "_s4", 4, () -> scarred(blade(5), 6500), null, true, shift);
        }
        DevShowroom.screens.add(new DevShowroom.ScreenShot("gear_restore_scale", p -> { }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            mc.setScreen(null);
        })));
    }

    /** 一张：先把界面缩放调到 scale；tooltip 不为 null 时拍它的提示框，否则拍 row 那一排物品格 */
    private static void shot(String name, int scale, Supplier<ItemStack> tooltip, Supplier<List<ItemStack>> row) {
        shot(name, scale, tooltip, row, false);
    }

    /** 缩放 3 下把提示框画在生存背包头盔格的位置 */
    private static void slotShot(String name, Supplier<ItemStack> tooltip) {
        shot(name, 3, tooltip, null, true);
    }

    private static void shot(String name, int scale, Supplier<ItemStack> tooltip, Supplier<List<ItemStack>> row, boolean armorSlot) {
        shot(name, scale, tooltip, row, armorSlot, null);
    }

    /** shift 不为 null 时，画提示框那一下假定 Shift 按没按住（不发按键） */
    private static void shot(String name, int scale, Supplier<ItemStack> tooltip, Supplier<List<ItemStack>> row, boolean armorSlot, Boolean shift) {
        DevShowroom.screens.add(new DevShowroom.ScreenShot(name, p -> { }, p -> {
            ItemStack shown = tooltip == null ? ItemStack.EMPTY : tooltip.get();
            List<ItemStack> items = row == null ? List.of() : row.get();
            Minecraft.getInstance().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.getToasts().clear();
                if (mc.options.guiScale().get() != scale) {
                    mc.options.guiScale().set(scale);
                    mc.resizeDisplay();
                }
                mc.setScreen(new GearShot(name, shown, items, armorSlot, shift));
            });
        }));
    }

    /**
     * 只拍升阶后基础属性（-PecShowroomBase）：满余烬的裁决之刃、余烬元素剑和五套胸甲，0 阶和满阶各一张（缩放 3，左上角），
     * 满阶另拍按住 Shift、画在头盔格位置的一张（最高、右边最窄，看放不放得下）。
     */
    private static void buildBaseStats() {
        List<Supplier<Item>> items = new ArrayList<>(List.of(() -> ECEmberForge.VERDICT_BLADE.get(), () -> ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        items.addAll(chestplates());
        for (Supplier<Item> item : items) {
            String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.get()).getPath();
            shot("base_" + id + "_0_s3", 3, () -> fullEmber(item.get(), 0), null, false, false);
            shot("base_" + id + "_max_s3", 3, () -> fullEmber(item.get(), -1), null, false, false);
            shot("base_" + id + "_max_on_slot_s3", 3, () -> fullEmber(item.get(), -1), null, true, true);
        }
        DevShowroom.screens.add(new DevShowroom.ScreenShot("gear_restore_scale", p -> { }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            mc.setScreen(null);
        })));
    }

    /**
     * 只拍带附魔的同调装备（-PecShowroomEnch）：满余烬满阶裁决之刃、余烬元素剑（带战痕）、满阶步枪、满阶审判庭胸甲，各带一组偏多的原版附魔，
     * 和同一件不带附魔的对照（{@code ench_<件>_e_*} / {@code ench_<件>_p_*}）；不按 / 按住 Shift，缩放 2、3，画在头盔格位置（顶得最高），
     * 缩放 3 另画一张左上角的。看附魔行有没有算进高度、同调块和属性行有没有重复。枪本身不能在附魔台 / 铁砧上附魔，这里按指令能塞进去的写。
     */
    private static void buildEnchanted() {
        record Piece(String key, Supplier<ItemStack> stack, Object[] enchants) { }
        Object[] sword = {Enchantments.SHARPNESS, 5, Enchantments.MOB_LOOTING, 3, Enchantments.SWEEPING_EDGE, 3, Enchantments.UNBREAKING, 3,
                Enchantments.MENDING, 1, Enchantments.FIRE_ASPECT, 2};
        Object[] gun = {Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1, Enchantments.VANISHING_CURSE, 1};
        Object[] chest = {Enchantments.ALL_DAMAGE_PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.THORNS, 3, Enchantments.MENDING, 1};
        List<Piece> pieces = List.of(
                new Piece("verdict_blade", () -> scarred(fullEmber(ECEmberForge.VERDICT_BLADE.get(), -1), 6500), sword),
                new Piece("ember_sword", () -> scarred(fullEmber(ECEmberForge.FORGED_ELEMENTAL_SWORD.get(), -1), 6500), sword),
                new Piece("rifle", () -> scarred(maxed(ECGuns.RIFLE.get()), 6500), gun),
                new Piece("inquisitorium_chestplate", () -> scarred(maxed(ECInquisitorium.INQUISITORIUM_CHESTPLATE.get()), 6500), chest));
        for (int scale : new int[] {2, 3}) {
            for (int i = 0; i < 2; i++) {
                boolean shift = i == 1;
                String state = shift ? "on" : "off";
                for (Piece piece : pieces) {
                    Supplier<ItemStack> enchanted = () -> enchant(piece.stack().get(), piece.enchants());
                    shot("ench_" + piece.key() + "_e_" + state + "_slot_s" + scale, scale, enchanted, null, true, shift);
                    shot("ench_" + piece.key() + "_p_" + state + "_slot_s" + scale, scale, piece.stack(), null, true, shift);
                    if (scale == 3) {
                        shot("ench_" + piece.key() + "_e_" + state + "_tl_s3", 3, enchanted, null, false, shift);
                    }
                }
            }
        }
        DevShowroom.screens.add(new DevShowroom.ScreenShot("gear_restore_scale", p -> { }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            mc.setScreen(null);
        })));
    }

    /** 按「附魔, 等级, 附魔, 等级…」直接写进物品（不管能不能附） */
    private static ItemStack enchant(ItemStack stack, Object[] enchants) {
        for (int i = 0; i < enchants.length; i += 2) {
            stack.enchant((Enchantment) enchants[i], (Integer) enchants[i + 1]);
        }
        return stack;
    }

    /** stage 为 −1 时取满阶；剑用地元素主元素 + 余烬加成最高的组合（方案里「满余烬」那把） */
    private static ItemStack fullEmber(Item item, int stage) {
        ItemStack stack = item instanceof com.flameforge.essentialcraft5.item.ForgedElementalSwordItem ? MRUStaffItem.full(item) : new ItemStack(item);
        if (item instanceof com.flameforge.essentialcraft5.item.ForgedElementalSwordItem) {
            bestEmbers().writeTo(stack);
        }
        Attunement.set(stack, stage < 0 ? Attunement.maxStage(stack) : stage, 0);
        return stack;
    }

    private static ForgedSwordData bestEmbers() {
        List<ResourceLocation> foci = List.of(Element.EARTH.focus(), Element.EARTH.focus(), Element.EARTH.focus(), Element.EARTH.focus());
        EmberKind[] kinds = EmberKind.values();
        ForgedSwordData best = null;
        for (int a = 0; a < kinds.length; a++) {
            for (int b = 0; b < kinds.length; b++) {
                for (int c = 0; c < kinds.length; c++) {
                    for (int d = 0; d < kinds.length; d++) {
                        ForgedSwordData data = new ForgedSwordData(List.of(kinds[a].id(), kinds[b].id(), kinds[c].id(), kinds[d].id()), foci, Element.EARTH, Element.FIRE);
                        if (best == null || data.emberDamage() > best.emberDamage()) {
                            best = data;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static ForgedSwordData sword(Element primary, Element secondary) {
        List<ResourceLocation> embers = List.of(EmberKind.COMMON.id(), EmberKind.FLAME.id(), EmberKind.COMMON.id(), EmberKind.FLAME.id());
        List<ResourceLocation> foci = List.of(Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus());
        return new ForgedSwordData(embers, foci, primary, secondary);
    }

    private static ItemStack blade(int stage) {
        ItemStack stack = MRUStaffItem.full(ECEmberForge.VERDICT_BLADE.get());
        sword(Element.WATER, Element.FIRE).writeTo(stack);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    private static ItemStack ember(int stage) {
        ItemStack stack = MRUStaffItem.full(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        sword(Element.FIRE, Element.WATER).writeTo(stack);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    private static ItemStack scarred(ItemStack stack, int scar) {
        Attunement.addPoints(stack, scar);
        return stack;
    }

    private static ItemStack staged(Item item, int stage) {
        ItemStack stack = MRUStaffItem.full(item);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    /** 五套盔甲的胸甲（宅男、魔力防护、风之、虚空、审判庭） */
    private static List<Supplier<Item>> chestplates() {
        return List.of(() -> ECComputer.COMPUTER_CHESTPLATE.get(), () -> ECGear.MHS_CHESTPLATE.get(), () -> ECWindGear.WIND_CHESTPLATE.get(),
                () -> ECVoidArmor.VOID_CHESTPLATE.get(), () -> ECInquisitorium.INQUISITORIUM_CHESTPLATE.get());
    }

    /** 满阶时提示框最高的四件：裁决之刃（带战痕）、审判庭 / 宅男 / 虚空胸甲 */
    private static List<Supplier<ItemStack>> tallest() {
        return List.of(() -> scarred(blade(5), 6500), () -> scarred(maxed(ECInquisitorium.INQUISITORIUM_CHESTPLATE.get()), 6500),
                () -> scarred(maxed(ECComputer.COMPUTER_CHESTPLATE.get()), 6500), () -> scarred(maxed(ECVoidArmor.VOID_CHESTPLATE.get()), 6500));
    }

    /** 这件所在家族的满阶 */
    private static ItemStack maxed(Item item) {
        ItemStack stack = MRUStaffItem.full(item);
        Attunement.set(stack, Attunement.maxStage(stack), 0);
        return stack;
    }

    /** 一排：余烬剑 0~2、裁决之刃 1~5（5 阶带 10 级战痕）、宅男胸甲 1~5（3 阶磨损一半）、魔力防护胸甲 2、四把枪 1~4 阶 */
    private static List<ItemStack> slotRow() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i <= 2; i++) {
            out.add(ember(i));
        }
        for (int i = 1; i <= 5; i++) {
            out.add(i == 5 ? scarred(blade(5), 20_000) : blade(i));
        }
        for (int i = 1; i <= 5; i++) {
            ItemStack chest = staged(ECComputer.COMPUTER_CHESTPLATE.get(), i);
            if (i == 3 && chest.isDamageableItem()) {
                chest.setDamageValue(chest.getMaxDamage() / 2);
            }
            out.add(chest);
        }
        out.add(staged(ECGear.MHS_CHESTPLATE.get(), 2));
        for (Supplier<Item> chest : chestplates().subList(1, 5)) {
            for (int i = 1; i <= Attunement.maxStage(new ItemStack(chest.get())); i++) {
                out.add(i == Attunement.maxStage(new ItemStack(chest.get())) ? scarred(maxed(chest.get()), 20_000) : staged(chest.get(), i));
            }
        }
        List<Item> guns = List.of(ECGuns.PISTOL.get(), ECGuns.RIFLE.get(), ECGuns.SNIPER.get(), ECGuns.GATLING.get());
        for (int i = 0; i < guns.size(); i++) {
            out.add(staged(guns.get(i), i + 1));
        }
        return out;
    }

    /** 画一排物品（带物品格装饰：耐久条、热量条、阶位菱形）或一个物品的提示框，截图用 */
    private static final class GearShot extends Screen {
        private final ItemStack stack;
        private final List<ItemStack> row;
        private final boolean armorSlot;
        private final Boolean shift;

        private final String name;
        private boolean logged;

        GearShot(String name, ItemStack stack, List<ItemStack> row, boolean armorSlot, Boolean shift) {
            super(Component.empty());
            this.name = name;
            this.stack = stack;
            this.row = row;
            this.armorSlot = armorSlot;
            this.shift = shift;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            if (!row.isEmpty()) {
                int perRow = 8;
                for (int i = 0; i < row.size(); i++) {
                    int x = 16 + (i % perRow) * 20;
                    int y = 16 + (i / perRow) * 20;
                    graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
                    graphics.fill(x, y, x + 16, y + 16, 0xFF373737);
                    graphics.renderItem(row.get(i), x, y);
                    graphics.renderItemDecorations(font, row.get(i), x, y);
                }
                // 名字（颜色 / 渐变 / 战痕后缀）列在右边
                // 名字太多时按屏幕高度分几列，每列 140 宽
                int textX = 16 + 8 * 20 + 8;
                int perColumn = Math.max(1, (height - 16) / 11);
                for (int i = 0; i < row.size(); i++) {
                    graphics.drawString(font, row.get(i).getHoverName(), textX + (i / perColumn) * 140, 16 + (i % perColumn) * 11, 0xFFFFFF);
                }
                return;
            }
            // 生存背包 176 × 166 居中，头盔格左上角在 (8, 8)，鼠标按停在格子正中算
            int slotX = armorSlot ? (width - 176) / 2 + 8 : 16;
            int slotY = armorSlot ? (height - 166) / 2 + 8 : 16;
            graphics.renderItem(stack, slotX, slotY);
            graphics.renderItemDecorations(font, stack, slotX, slotY);
            int tipX = armorSlot ? slotX + 8 : 30;
            // 按住 Shift 那张：代替键盘；同时告诉同调提示框鼠标在哪一列，估折行和高度时用
            AttunementTooltips.setPreview(shift, tipX);
            try {
                if (!logged) {
                    // 整份提示框的文字行写进日志（「[showroom-tip]」），对照顺序、重复行和数字时不用逐张看图
                    logged = true;
                    StringBuilder text = new StringBuilder();
                    List<Component> lines = getTooltipFromItem(Minecraft.getInstance(), stack);
                    for (Component line : lines) {
                        text.append(" ¦ ").append(line.getString());
                    }
                    // 按 Forge 折行后的行数和原版算的内容高度（不含上下各 4 的边框）；内容高 + 7 超过屏高的部分顶到屏幕外
                    var parts = net.minecraftforge.client.ForgeHooksClient.gatherTooltipComponents(stack, lines, stack.getTooltipImage(), tipX, width, height, font);
                    int contentHeight = parts.size() == 1 ? -2 : 0;
                    for (var part : parts) {
                        contentHeight += part.getHeight();
                    }
                    com.flameforge.essentialcraft5.EssentialCraft5.LOGGER.info("[showroom-tip] {} {}×{} rows={} h={}{}", name, width, height, parts.size(), contentHeight, text);
                }
                graphics.renderTooltip(font, stack, tipX, armorSlot ? slotY + 8 : 30);
            } finally {
                AttunementTooltips.setPreview(null, -1);
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
