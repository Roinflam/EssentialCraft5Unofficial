package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.client.WindClient;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaPortalPlacer;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaPortalShape;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.player.WindWishes;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 风之石板（原作 ItemWindTablet，ItemMRUGeneric 系：自带 5000 MRU，在充能仓里充，堆叠 1）。
 * 配方：4 风之焦点 + 4 强化板 + 受困之风。
 * <p>
 * 原作：风之好感到 3 500 以后，右键下界传送门花 500 MRU 把整片门换成海纳尔传送门；提示行按好感一个字一个字显出风的真名「Owethanna Else Hugaida」。
 * <p>
 * <b>换门</b>（第六批 6a M2，batch6a.md §5.2；D10 A；policy §4.2 / §7 裁定 11），只在服务端、只给真玩家：
 * <ul>
 *   <li><b>主世界</b>：点中点燃的下界传送门 → 门要是完整的（框里每格都是同一朝向的下界门，≤ 21 × 21 = 441 格，不完整按「没点燃」）→
 *       开关 {@code general.hoanna.portal_enabled} → 好感够（{@link #attuned}，不够说「没有回应」）→ {@code MRUDrain.canAfford}
 *       （{@code general.wind.tablet_portal_mru}，默认 500）→ 整片一次 {@code ECProtection.captureGuarded}（逐格查出生点保护 / 世界边界、发一次多格放置事件、
 *       朝向 = 点中的面），被拒整片还原、提示没有权限、<b>不扣</b>；放行后才扣 MRU、门中心放一声传送门触发音（音量 1.0、音高 0.6；
 *       原作音量 10、音高 0.01，160 格外都听得到）。<b>不能靠 Forge 放置捕获的回滚退 MRU</b>：{@code MRUDrain} 会先从身上别的储存物品扣，
 *       被拒时 Forge 只还原手上石板的 NBT。点中主世界里没点燃的空框：提示「先点燃」（好感还不够的人点空框什么都不说）。</li>
 *   <li><b>海纳尔</b>：点中搭好的空黑曜石框（点框的内侧面，框里是空气）→ 同样的判断顺序和 captureGuarded → 填成海纳尔之门（修回程门；
 *       1.20.1 只有主世界和下界能点燃下界门，海纳尔那侧的门被拆了只能这样修）。{@code portal_enabled} 关掉时这里照样放行：
 *       开关只挡主世界那头，已经在海纳尔的人还得能修门回家。</li>
 *   <li><b>其它维度</b>点下界门：提示「这里不行」，不扣。</li>
 * </ul>
 * 捕获期间放的是 {@code stable=false} 的门格，放行后再改成 {@code stable=true}（见 HoannaPortalBlock 的类注释），那一步暂停外层放置捕获，不会再发一次事件。
 * 原作的毛病都改了：先扣 500 MRU 再判断（拿石板右键任何方块都掉 500）、{@code setBlockState(flag 2)} 逐格换不发事件、不限维度、刺耳的音效。
 * <p>
 * 门槛 3 500 做成了配置 general.wind.tablet_points（E5）。原作 ItemWindTablet 里那 24 句 windMessages 没有任何地方用到，不移植。
 * <p>
 * <b>风之试炼</b>（第十批 10b-M1，D4 A）：石板回应（{@link #attuned}）除了好感够，还要完成够数的风之心愿（{@code general.wind_wish.required}，默认 7，
 * 0 = 不要；心愿功能关掉时也不要），两端都这样算（客户端读同步来的本人数据和配置）。提示行在真名下面多一行「风之印记 n / 7」；
 * 名字照旧只按好感显出。好感够了、心愿没够时右键门说「风还在等你完成心愿」，同样不改不扣。
 */
public class WindTabletItem extends Item implements CreativeVariants {

    /** 原作 ItemMRUGeneric.maxMRU 的默认值 */
    public static final int MAX_MRU = 5000;
    /** 原作换门的花费（配置 general.wind.tablet_portal_mru 的默认值） */
    public static final int PORTAL_COST = 500;
    /** 风之真名（原作写死，专有名词不翻译） */
    public static final String WIND_NAME = "Owethanna Else Hugaida";
    private static final String HIDDEN = "?".repeat(WIND_NAME.length());

    /** 石板右键的是哪种目标（{@link #useOn} 按它走不同的路） */
    public enum GateTarget {
        /** 没关系的方块：让给别的交互 */
        NONE,
        /** 主世界里点燃的完整下界门：换成海纳尔之门 */
        CONVERT,
        /** 海纳尔里搭好的空框：填成海纳尔之门（D10 A） */
        FILL,
        /** 主世界里没点燃的空框（或者缺了几格的下界门）：提示先点燃 */
        NOT_LIT,
        /** 别的维度（下界等）里的下界门：提示这里不行 */
        WRONG_DIMENSION
    }

    public WindTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        // 不是储存物品：不会被别的法杖当电池抽
        return new MRUItemStorage(stack, MAX_MRU);
    }

    /**
     * 显出几个字（原作 DummyCore MathUtils.pixelatedTextureSize(当前, 门槛, 22)：先夹到门槛，按 float 算百分比再乘长度、截断）。
     * 门槛 ≤ 0 时全部显出。
     */
    public static int revealed(int points, int threshold) {
        int length = WIND_NAME.length();
        if (threshold <= 0) {
            return length;
        }
        int current = Math.max(0, Math.min(points, threshold));
        float percent = (float) current / threshold * 100F;
        return Math.min(length, (int) (percent / 100F * length));
    }

    /** 真名显出来的样子：前几个字，其余是问号 */
    public static String revealedName(int points, int threshold) {
        int shown = revealed(points, threshold);
        return WIND_NAME.substring(0, shown) + HIDDEN.substring(shown);
    }

    /** 石板肯不肯回应：风之好感够（原作 revPos ≥ 22），而且完成过够数的风之心愿（第十批 10b） */
    public static boolean attuned(Player player) {
        return nameRevealed(player) && WindWishes.count(player) >= WindWishes.required();
    }

    /** 风之真名全显出来了（只看好感） */
    public static boolean nameRevealed(Player player) {
        return revealed(WindAffinity.points(player), ECWind.Config.tabletPoints()) >= WIND_NAME.length();
    }

    /**
     * 判断石板点中的是什么（两端都算：客户端据此决定吃不吃这次右键）；是门 / 框时 shapeOut[0] 带回要改的门框。
     * 主世界点空框、海纳尔补空框都要点<b>框的内侧面</b>（点中的面朝着框里的空气）。
     */
    public static GateTarget classify(Level level, BlockPos clicked, Direction face, HoannaPortalShape[] shapeOut) {
        BlockState state = level.getBlockState(clicked);
        boolean hoanna = ECHoanna.isHoanna(level);
        if (state.is(Blocks.NETHER_PORTAL)) {
            if (hoanna || level.dimension() != Level.OVERWORLD) {
                return GateTarget.WRONG_DIMENSION;
            }
            HoannaPortalShape shape = HoannaPortalShape.findNetherPortal(level, clicked, state.getValue(NetherPortalBlock.AXIS)).orElse(null);
            if (shape == null) {
                return GateTarget.NOT_LIT;
            }
            shapeOut[0] = shape;
            return GateTarget.CONVERT;
        }
        if (!hoanna && level.dimension() != Level.OVERWORLD) {
            return GateTarget.NONE;
        }
        BlockPos inside = clicked.relative(face);
        BlockState insideState = level.getBlockState(inside);
        if (!HoannaPortalShape.isEmpty(insideState) || HoannaPortalShape.isPortal(insideState)) {
            return GateTarget.NONE;
        }
        Direction.Axis preferred = face.getAxis().isHorizontal() ? face.getAxis() : Direction.Axis.X;
        HoannaPortalShape shape = HoannaPortalShape.findEmptyShape(level, inside, preferred).orElse(null);
        if (shape == null) {
            return GateTarget.NONE;
        }
        shapeOut[0] = shape;
        return hoanna ? GateTarget.FILL : GateTarget.NOT_LIT;
    }

    /**
     * 换门 / 补门（见类注释）。点别的方块返回 PASS（让给别的交互）；客户端对门 / 框直接吃掉这次右键，由服务端判断。
     * 失败一律 FAIL、什么都不改不扣，快捷栏上方一句提示；成功返回 CONSUME（触发原版 item_used_on_block 进度）。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        HoannaPortalShape[] shapeOut = new HoannaPortalShape[1];
        GateTarget target = classify(level, context.getClickedPos(), context.getClickedFace(), shapeOut);
        if (target == GateTarget.NONE) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || !ECProtection.isRealPlayer(player)) {
            return InteractionResult.FAIL;
        }
        if (target == GateTarget.WRONG_DIMENSION) {
            say(player, "message.essentialcraft5.wind_tablet.wrong_dimension");
            return InteractionResult.FAIL;
        }
        // 开关关掉只挡主世界换门；海纳尔里补空框照样放行（修回程门，已经在海纳尔的人不能被困住）
        if (!ECHoannaPortal.Config.portalEnabledIn(level) || ECHoanna.level(player.server) == null) {
            say(player, "message.essentialcraft5.hoanna.disabled");
            return InteractionResult.FAIL;
        }
        if (!attuned(player)) {
            if (target == GateTarget.NOT_LIT) {
                // 好感不够的人点一个空框：什么都不说（石板对他还只是一块石板）
                return InteractionResult.PASS;
            }
            if (nameRevealed(player)) {
                say(player, "message.essentialcraft5.wind_tablet.need_wishes", WindWishes.count(player), WindWishes.required());
            } else {
                say(player, "message.essentialcraft5.wind_tablet.no_response");
            }
            return InteractionResult.FAIL;
        }
        if (target == GateTarget.NOT_LIT) {
            say(player, "message.essentialcraft5.wind_tablet.not_lit");
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        int cost = ECWind.Config.tabletPortalMRU();
        if (!MRUDrain.canAfford(player, stack, cost)) {
            say(player, "message.essentialcraft5.wind_tablet.no_mru", cost);
            return InteractionResult.FAIL;
        }
        HoannaPortalShape shape = shapeOut[0];
        List<BlockPos> cells = shape.cells();
        BlockState portal = ECHoannaPortal.HOANNA_PORTAL.get().portalState(shape.axis(), false);
        ECProtection.CaptureResult result = ECProtection.captureGuarded(player, level, context.getClickedFace(), List.of(), () -> {
            for (BlockPos cell : cells) {
                level.setBlock(cell, portal, 18);
            }
            return true;
        });
        if (!result.is(ECProtection.CaptureResult.Outcome.ALLOWED)) {
            if (result.rejected()) {
                player.displayClientMessage(Component.translatable("message.essentialcraft5.no_permission").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        HoannaPortalPlacer.stabilize(level, cells);
        MRUDrain.playerUseMRU(player, stack, cost);
        level.playSound(null, cells.get(cells.size() / 2), SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.0F, 0.6F);
        say(player, target == GateTarget.FILL ? "message.essentialcraft5.wind_tablet.filled" : "message.essentialcraft5.wind_tablet.opened");
        return InteractionResult.CONSUME;
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler ->
                tooltip.add(Component.translatable("tooltip.essentialcraft5.mru", handler.getMRU(), handler.getMaxMRU())
                        .withStyle(ChatFormatting.GRAY)));
        // 客户端只有本人的玩家数据：看的是自己的好感（原作同样取 Minecraft.getMinecraft().player）
        Player viewer = level != null && level.isClientSide
                ? DistExecutor.<Player>unsafeCallWhenOn(Dist.CLIENT, () -> WindClient::localPlayer)
                : null;
        int points = viewer == null ? 0 : WindAffinity.points(viewer);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_tablet.name").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal(revealedName(points, ECWind.Config.tabletPoints())).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
        int required = WindWishes.required();
        if (required > 0) {
            int wishes = viewer == null ? 0 : WindWishes.count(viewer);
            tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_tablet.wishes", Math.min(wishes, required), required)
                    .withStyle(wishes >= required ? ChatFormatting.AQUA : ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_tablet.gate_hint", ECWind.Config.tabletPortalMRU())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 耐久（MRU）变化不触发重新举起的动画（原作 ItemMRUGeneric 只在换物品时触发） */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    /** 创造栏放空 / 满两份（原作 ItemMRUGeneric.getSubItems） */
    @Override
    public List<ItemStack> creativeVariants() {
        ItemStack empty = new ItemStack(this);
        ItemStack full = new ItemStack(this);
        full.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(MAX_MRU));
        return List.of(empty, full);
    }
}
