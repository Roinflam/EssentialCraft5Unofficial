package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.gun.GunAssembly;
import com.flameforge.essentialcraft5.gun.GunMaterials;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.menu.WeaponMakerMenu;
import com.flameforge.essentialcraft5.registry.ECWeaponMaker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 武器工作台界面（原作 GuiWeaponBench）。背景是原作四张整幅贴图 {@code textures/gui/<枪>_maker.png}（槽位框、材料小图和 B / H / M / S 字母都画在图上），
 * 所以不用 {@link MachineScreen} 的发射器底图。原作的元素位置照搬：标题 (60, 5)、预览 (153, 5)、「完成」按钮 (145, 20, 28×12)。
 * <ul>
 *   <li>标题：原作用的翻译键 {@code item.ec3.gun.*.name} 不存在（显示成键名），这里画枪的物品名；枪还没注册时画工作台的名字。颜色用原版界面标题的深灰。</li>
 *   <li>预览（原作 previewWeapon）：能组装时是新枪，悬停列出部件和属性（{@link GunStats}，和枪的提示同一套算法）、从灵魂格转来的 MRU；
 *       核心和灵魂格放好、别的没齐时画一把空枪，悬停列出还差什么。原作把预览写进方块实体 NBT，这里每 tick 按同步来的槽位现算（不每帧算）。</li>
 *   <li>「完成」：用原版的 {@code gui.done}；客户端判定不能组装时禁用（悬停写原因），点击发 {@code handleInventoryButtonClick(containerId, 0)}，
 *       服务端再按同一套规则判一次（{@link WeaponMakerMenu#clickMenuButton}）。</li>
 *   <li>新加：空格子悬停写这格放什么（材料表按已开放的现列）；灵魂格悬停加一行「会被用掉，里面的 MRU 转进新枪」。</li>
 * </ul>
 * 坐标没进游戏核对过，按原作数字写的。
 */
public class WeaponMakerScreen extends MachineScreen<WeaponMakerMenu> {

    private static final String KEY = "gui.essentialcraft5.weapon_maker.";
    private static final int TITLE_X = 60;
    private static final int TITLE_Y = 5;
    private static final int TITLE_COLOR = 0x404040;
    private static final int PREVIEW_X = 153;
    private static final int PREVIEW_Y = 5;
    private static final int BUTTON_X = 145;
    private static final int BUTTON_Y = 20;
    private static final int BUTTON_WIDTH = 28;
    /** 高 12 时字（含阴影）上下只离按钮边 1 像素；14 上下各 2 像素，下沿离输出格的框（y 36）还有 1 像素 */
    private static final int BUTTON_HEIGHT = 14;
    /**
     * 手枪、狙击装填完后另有的冷却（ECGuns 的 GunItem 用同一个常量，计划 batch5.md §3.1；原作 cool = 60 只拦得住这两种），预览提示里写出来
     */
    private static final int SINGLE_SHOT_RELOAD_COOLDOWN_TICKS = GunStats.RELOAD_COOLDOWN_TICKS;

    private final GunType type;
    private final ResourceLocation background;
    private Button done;
    private ItemStack preview = ItemStack.EMPTY;
    private Set<GunAssembly.Problem> problems = Set.of();

    public WeaponMakerScreen(WeaponMakerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.type = menu.gunType();
        this.background = EssentialCraft5.id("textures/gui/" + type.id() + "_maker.png");
        refresh();
    }

    @Override
    protected void init() {
        super.init();
        done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> assemble())
                .bounds(leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        done.active = problems.isEmpty();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
        if (done != null) {
            done.active = problems.isEmpty();
        }
    }

    /** 按同步来的槽位重算预览和原因（每 tick 一次） */
    private void refresh() {
        problems = GunAssembly.problems(type, menu.handler());
        preview = GunAssembly.preview(type, menu.handler());
    }

    private void assemble() {
        if (problems.isEmpty() && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WeaponMakerMenu.BUTTON_ASSEMBLE);
        }
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(background, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Item gun = GunAssembly.gunItem(type);
        graphics.drawString(font, gun != null ? gun.getDescription() : title, TITLE_X, TITLE_Y, TITLE_COLOR, false);
        if (!preview.isEmpty()) {
            graphics.renderItem(preview, PREVIEW_X, PREVIEW_Y);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        if (!preview.isEmpty() && isHovering(PREVIEW_X, PREVIEW_Y, 16, 16, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, previewTooltip(), mouseX, mouseY);
        } else if (done != null && done.isHovered()) {
            graphics.renderComponentTooltip(font, buttonTooltip(), mouseX, mouseY);
        } else if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < type.layout().size()) {
            GunType.SlotRole role = type.layout().role(hoveredSlot.index);
            if (role != null) {
                graphics.renderComponentTooltip(font, slotHint(role), mouseX, mouseY);
            }
        }
    }

    /** 放着东西的灵魂格：物品提示末尾加一行「组装时用掉，MRU 转进新枪」 */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (hoveredSlot != null && hoveredSlot.index < type.layout().size()
                && type.layout().role(hoveredSlot.index) == GunType.SlotRole.SOUL) {
            lines = new ArrayList<>(lines);
            lines.add(Component.translatable(KEY + "slot.soul.used", GunAssembly.MAX_SOUL_TRANSFER).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // 提示
    // ------------------------------------------------------------------

    private List<Component> buttonTooltip() {
        List<Component> lines = new ArrayList<>();
        if (problems.isEmpty()) {
            lines.add(Component.translatable(KEY + "button.ready"));
        } else {
            lines.add(Component.translatable(KEY + "preview.missing").withStyle(ChatFormatting.RED));
            problems.forEach(problem -> lines.add(problemLine(problem)));
        }
        return lines;
    }

    private Component problemLine(GunAssembly.Problem problem) {
        return Component.literal("- ").append(Component.translatable(problem.translationKey())).withStyle(ChatFormatting.GRAY);
    }

    private List<Component> previewTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(preview.getHoverName());
        GunParts parts = GunParts.read(preview).orElse(null);
        if (!problems.isEmpty() || parts == null) {
            lines.add(Component.translatable(KEY + "preview.missing").withStyle(ChatFormatting.RED));
            problems.forEach(problem -> lines.add(problemLine(problem)));
            return lines;
        }
        GunStats stats = GunStats.of(type, parts);
        lines.add(gray("part.base", bodyName(parts.base())));
        lines.add(gray("part.handle", bodyName(parts.handle())));
        lines.add(gray("part.device", bodyName(parts.device())));
        if (parts.lens() != null) {
            GunMaterials.lens(parts.lens()).ifPresent(lens -> lines.add(gray("part.lens", itemName(lens.item()))));
        }
        if (parts.scope() != null) {
            GunMaterials.scope(type, parts.scope()).ifPresent(scope -> lines.add(gray(
                    type.scopeKind() == GunType.ScopeKind.SNIPER_REQUIRED ? "part.sniper_scope" : "part.scope",
                    itemName(scope.item()), number(scope.zoom(), 1))));
        }
        lines.add(stat("stat.damage", number(stats.damage(), 1)));
        lines.add(stat("stat.mru", type.mruPerShot(stats)));
        lines.add(stat("stat.magazine", stats.magazine()));
        if (type.spinUpTicks() > 0) {
            lines.add(stat("stat.spin_up", number(type.spinUpTicks() / 20F, 2), type.autoInterval()));
        } else if (type.automatic()) {
            lines.add(stat("stat.auto", type.autoInterval()));
        } else {
            lines.add(stat("stat.interval", stats.cooldownTicks()));
        }
        String reload = number(stats.reloadTicks() / 20F, 2);
        lines.add(type.automatic() ? stat("stat.reload", reload)
                : stat("stat.reload_cooldown", reload, number(SINGLE_SHOT_RELOAD_COOLDOWN_TICKS / 20F, 2)));
        lines.add(stat("stat.spread", number(stats.spread(), 2)));
        if (type.hasRecoil()) {
            lines.add(stat("stat.recoil", number(stats.recoil(), 1)));
        }
        lines.add(stat("stat.durability", stats.durabilityInt()));
        int mru = preview.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandler::getMRU).orElse(0);
        lines.add(Component.translatable(KEY + "stat.soul_mru", mru, GunAssembly.MAX_SOUL_TRANSFER).withStyle(ChatFormatting.GOLD));
        return lines;
    }

    /** 空格子：这格放什么 */
    private List<Component> slotHint(GunType.SlotRole role) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "slot." + role.name().toLowerCase(Locale.ROOT)));
        switch (role) {
            case OUTPUT -> lines.add(gray("slot.output.hint"));
            case CORE -> {
                lines.add(gray("slot.any_of", tagNames(ECWeaponMaker.MAGIC_CORES)));
                lines.add(gray("slot.core.used"));
            }
            case SOUL -> {
                lines.add(gray("slot.any_of", tagNames(ECWeaponMaker.GUN_SOULS)));
                lines.add(Component.translatable(KEY + "slot.soul.used", GunAssembly.MAX_SOUL_TRANSFER).withStyle(ChatFormatting.GOLD));
            }
            case LENS_OPT -> {
                lines.add(gray("slot.any_of", join(GunMaterials.availableLenses().stream().map(lens -> itemName(lens.item())).toList())));
                lines.add(gray("slot.lens.effect"));
            }
            case LENS -> {
                lines.add(gray("slot.same_material", join(GunMaterials.availableLenses().stream().map(lens -> itemName(lens.item())).toList())));
                lines.add(gray("slot.lens.effect"));
            }
            case LENS_HOUSING -> lines.add(gray("slot.any_of", itemName(GunMaterials.GATLING_LENS_HOUSING)));
            case SCOPE_HOUSING -> lines.add(gray("slot.any_of", itemName(GunMaterials.SNIPER_SCOPE_HOUSING)));
            case SCOPE_OPT -> {
                lines.add(gray("slot.any_of", scopeList()));
                lines.add(gray("slot.scope.effect"));
            }
            case SCOPE -> {
                lines.add(gray("slot.same_material", scopeList()));
                lines.add(gray("slot.scope.effect"));
            }
            case BASE, DEVICE, HANDLE -> {
                lines.add(gray("slot.same_material", join(GunMaterials.availableBodies().stream().map(body -> itemName(body.item())).toList())));
                lines.add(gray("slot." + role.name().toLowerCase(Locale.ROOT) + ".effect"));
            }
        }
        return lines;
    }

    private Component scopeList() {
        return join(GunMaterials.availableScopes(type).stream()
                .map(scope -> (Component) Component.translatable(KEY + "zoom", itemName(scope.item()), number(scope.zoom(), 1)))
                .toList());
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    private static MutableComponent gray(String key, Object... args) {
        return Component.translatable(KEY + key, args).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent stat(String key, Object... args) {
        return Component.translatable(KEY + key, args).withStyle(ChatFormatting.DARK_AQUA);
    }

    private static Component bodyName(String id) {
        return GunMaterials.body(id).map(body -> itemName(body.item())).orElse(Component.literal(id));
    }

    private static Component itemName(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        return item != null ? item.getDescription() : Component.literal(id.toString());
    }

    private static Component tagNames(TagKey<Item> tag) {
        List<Component> names = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            names.add(holder.value().getDescription());
        }
        return join(names);
    }

    /** 用「、」（英文「, 」）连起来 */
    private static Component join(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(Component.translatable(KEY + "list_sep"));
            }
            out.append(parts.get(i));
        }
        return out;
    }

    /** 保留至多 digits 位小数、去掉末尾的 0 */
    private static String number(float value, int digits) {
        return new BigDecimal(Float.toString(value)).setScale(digits, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
