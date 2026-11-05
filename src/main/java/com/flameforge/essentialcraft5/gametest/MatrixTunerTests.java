package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.authlib.GameProfile;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.MatrixTunerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MatrixTunerBlockEntity.Status;
import com.flameforge.essentialcraft5.menu.MatrixTunerMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECMatrixTuner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * 矩阵调律器（ECMatrixTuner，batch12.md §4.6.8）的 GameTest：
 * <ul>
 *   <li>每秒抹 60 点、每点 0.5 ESPE + 10 MRU；ESPE 不够时按 ESPE 算；损伤抹到 0 就停，只收这几点的钱；</li>
 *   <li>私人模式不给别人调、不扣钱；公开模式给别人调、主人优先；只有主人或 OP 能切换；</li>
 *   <li>身上已有的腐化效果和五芒星档位不动；假玩家、没站在顶上的人不算。</li>
 * </ul>
 * 测试玩家不在世界实体表里，用 {@link MatrixTunerBlockEntity#setTestOccupants} 指定「站在上面的人」（脚的位置照查，所以先挪到顶上）。
 * 调律每 20 刻一次、按位置错开，测试等 {@link MatrixTunerBlockEntity#cycles()} 走到下一次再看，不靠数刻。
 * 价格走真实配置的默认值（60 点 / 秒、0.5 ESPE、10 MRU），存量在开头直接设好，不摆柱子、不放绑定宝石。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MatrixTunerTests {

    private static final String EMPTY = "empty";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final double EPS = 1e-6;
    private static final UUID FAKE_ID = UUID.fromString("5cd89d0b-e9ba-0000-89f4-7c0a1e000012");

    private MatrixTunerTests() {
    }

    // ---------------- 工具 ----------------

    private static MatrixTunerBlockEntity place(GameTestHelper helper, int mru, double espe) {
        helper.setBlock(CENTER, ECMatrixTuner.MATRIX_TUNER.get());
        MatrixTunerBlockEntity be = (MatrixTunerBlockEntity) helper.getBlockEntity(CENTER);
        be.getMRUStorage().setMRU(mru);
        be.getESPEStorage().setESPE(espe);
        return be;
    }

    /** 真玩家（假连接），脚放在调律盘正中 */
    private static ServerPlayer onTop(GameTestHelper helper, UUID id, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, id, name);
        BlockPos abs = helper.absolutePos(CENTER);
        player.moveTo(abs.getX() + 0.5D, abs.getY() + MatrixTunerBlockEntity.TOP, abs.getZ() + 0.5D, 0F, 0F);
        return player;
    }

    private static void seat(GameTestHelper helper, ServerPlayer... players) {
        MatrixTunerBlockEntity.setTestOccupants(helper.getLevel(), helper.absolutePos(CENTER), List.of(players));
    }

    private static void unseat(GameTestHelper helper) {
        MatrixTunerBlockEntity.setTestOccupants(helper.getLevel(), helper.absolutePos(CENTER), null);
    }

    private static IPlayerData data(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家没有玩家数据");
        }
        return data;
    }

    /** 主人站在上面、损伤 damage 的一台（私人模式） */
    private static ServerPlayer owner(GameTestHelper helper, MatrixTunerBlockEntity be, int damage) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = onTop(helper, id, "ec5-tuner");
        be.setOwner(id, "ec5-tuner");
        data(player).setMatrixDamage(damage);
        seat(helper, player);
        return player;
    }

    // ---------------- 速度与价钱 ----------------

    /** 损伤 1000、ESPE / MRU 充足：一次调律后 940，两次之间不再动，下一次 880 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void matrixTunerDrains60PerSecond(GameTestHelper helper) {
        MatrixTunerBlockEntity be = place(helper, 50_000, 20_000D);
        ServerPlayer player = owner(helper, be, 1000);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 1, "等第一次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(data(player).getMatrixDamage() == 940 && be.cycles() == 1,
                            "一次调律应抹 60 点，实际 " + data(player).getMatrixDamage() + "（第 " + be.cycles() + " 次）");
                    helper.assertTrue(be.status() == Status.WORKING && be.remaining() == 940, "状态 WORKING、剩 940，实际 " + be.status());
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(data(player).getMatrixDamage() == 940, "两次调律之间不该再扣"))
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 2, "等第二次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(data(player).getMatrixDamage() == 880, "第二次后应是 880，实际 " + data(player).getMatrixDamage());
                    unseat(helper);
                })
                .thenSucceed();
    }

    /** 抹 60 点扣 30 ESPE、600 MRU */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void matrixTunerPaysEspeAndMru(GameTestHelper helper) {
        MatrixTunerBlockEntity be = place(helper, 10_000, 1000D);
        ServerPlayer player = owner(helper, be, 5000);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 1, "等第一次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(data(player).getMatrixDamage() == 4940, "应抹 60 点");
                    helper.assertTrue(Math.abs(be.getESPEStorage().getESPE() - 970D) < EPS,
                            "应扣 30 ESPE，剩 " + be.getESPEStorage().getESPE());
                    helper.assertTrue(be.getMRUStorage().getMRU() == 9400, "应扣 600 MRU，剩 " + be.getMRUStorage().getMRU());
                    unseat(helper);
                })
                .thenSucceed();
    }

    /** 只有 10 ESPE：只抹 20 点、ESPE 扣光；下一次 NO_ESPE、不动。另核对单次数量的公式 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void matrixTunerLimitedByEspe(GameTestHelper helper) {
        helper.assertTrue(MatrixTunerBlockEntity.amount(1000, 10D, 1_000_000, 60, 0.5D, 10) == 20, "10 ESPE 只够 20 点");
        helper.assertTrue(MatrixTunerBlockEntity.amount(1000, 1000D, 55, 60, 0.5D, 10) == 5, "55 MRU 只够 5 点");
        helper.assertTrue(MatrixTunerBlockEntity.amount(1000, 0D, 0, 60, 0D, 0) == 60, "价钱为 0 的那项不设限");
        helper.assertTrue(MatrixTunerBlockEntity.amount(30, 1000D, 100_000, 60, 0.5D, 10) == 30, "损伤只剩 30 就只抹 30");
        helper.assertTrue(MatrixTunerBlockEntity.amount(1000, 0.4D, 100_000, 60, 0.5D, 10) == 0, "不够一点就是 0");
        MatrixTunerBlockEntity be = place(helper, 50_000, 10D);
        ServerPlayer player = owner(helper, be, 1000);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 1, "等第一次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(data(player).getMatrixDamage() == 980, "10 ESPE 只够抹 20 点，实际剩 " + data(player).getMatrixDamage());
                    helper.assertTrue(be.getESPEStorage().getESPE() < EPS && be.getMRUStorage().getMRU() == 50_000 - 200,
                            "ESPE 扣光、MRU 扣 200，实际 " + be.getESPEStorage().getESPE() + " / " + be.getMRUStorage().getMRU());
                })
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 2, "等第二次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.NO_ESPE && data(player).getMatrixDamage() == 980,
                            "没 ESPE 了应是 NO_ESPE、损伤不动，实际 " + be.status() + " / " + data(player).getMatrixDamage());
                    unseat(helper);
                })
                .thenSucceed();
    }

    /** 损伤 30：抹到 0、只收 30 点的钱，状态 CLEAN；之后不再扣 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void matrixTunerStopsAtZero(GameTestHelper helper) {
        MatrixTunerBlockEntity be = place(helper, 10_000, 1000D);
        ServerPlayer player = owner(helper, be, 30);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 1, "等第一次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(data(player).getMatrixDamage() == 0, "应抹到 0");
                    helper.assertTrue(Math.abs(be.getESPEStorage().getESPE() - 985D) < EPS && be.getMRUStorage().getMRU() == 9700,
                            "只收 30 点：15 ESPE、300 MRU，实际剩 " + be.getESPEStorage().getESPE() + " / " + be.getMRUStorage().getMRU());
                    helper.assertTrue(be.status() == Status.CLEAN, "抹干净应是 CLEAN，实际 " + be.status());
                })
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 2, "等第二次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.CLEAN && be.getMRUStorage().getMRU() == 9700, "没损伤时什么都不扣");
                    unseat(helper);
                })
                .thenSucceed();
    }

    // ---------------- 私人 / 公开 ----------------

    /** 私人模式、站着的不是主人：NOT_ALLOWED，损伤和钱都不动 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void matrixTunerPrivateRejectsOthers(GameTestHelper helper) {
        MatrixTunerBlockEntity be = place(helper, 10_000, 1000D);
        be.setOwner(UUID.randomUUID(), "ec5-owner");
        ServerPlayer stranger = onTop(helper, UUID.randomUUID(), "ec5-stranger");
        data(stranger).setMatrixDamage(1000);
        seat(helper, stranger);
        helper.assertTrue(!be.isPublic(), "默认应是私人模式");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 1, "等第一次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.NOT_ALLOWED, "应是 NOT_ALLOWED，实际 " + be.status());
                    helper.assertTrue(data(stranger).getMatrixDamage() == 1000, "别人的损伤不该动");
                    helper.assertTrue(Math.abs(be.getESPEStorage().getESPE() - 1000D) < EPS && be.getMRUStorage().getMRU() == 10_000,
                            "不该扣钱");
                    unseat(helper);
                })
                .thenSucceed();
    }

    /** 公开模式：主人和别人都站着时先调主人；主人抹干净后轮到别人；主人不在时直接调别人 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void matrixTunerPublicServesOthers(GameTestHelper helper) {
        MatrixTunerBlockEntity be = place(helper, 50_000, 20_000D);
        UUID ownerId = UUID.randomUUID();
        be.setOwner(ownerId, "ec5-owner");
        be.setPublic(true);
        ServerPlayer owner = onTop(helper, ownerId, "ec5-owner");
        ServerPlayer guest = onTop(helper, UUID.randomUUID(), "ec5-guest");
        data(owner).setMatrixDamage(60);
        data(guest).setMatrixDamage(1000);
        seat(helper, guest, owner);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 1, "等第一次调律"))
                .thenExecute(() -> helper.assertTrue(data(owner).getMatrixDamage() == 0 && data(guest).getMatrixDamage() == 1000,
                        "主人优先：先抹主人的 60，客人不动；实际 " + data(owner).getMatrixDamage() + " / " + data(guest).getMatrixDamage()))
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 2, "等第二次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(data(guest).getMatrixDamage() == 940 && be.status() == Status.WORKING,
                            "主人干净了轮到客人，实际 " + data(guest).getMatrixDamage() + " / " + be.status());
                    seat(helper, guest);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 3, "等第三次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(data(guest).getMatrixDamage() == 880, "主人不在时直接调客人，实际 " + data(guest).getMatrixDamage());
                    unseat(helper);
                })
                .thenSucceed();
    }

    /** 切换：非主人非 OP 按按钮模式不变；主人按得动；别的按钮号不收；没记主人时只有 OP */
    @GameTest(template = EMPTY)
    public static void matrixTunerToggleOwnerOnly(GameTestHelper helper) {
        MatrixTunerBlockEntity be = place(helper, 0, 0D);
        UUID ownerId = UUID.randomUUID();
        be.setOwner(ownerId, "ec5-owner");
        ServerPlayer stranger = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-stranger");
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper, ownerId, "ec5-owner");
        helper.assertTrue(!stranger.hasPermissions(2), "测试玩家不该是 OP");

        MatrixTunerMenu strangerMenu = new MatrixTunerMenu(0, stranger.getInventory(), be);
        helper.assertTrue(!strangerMenu.clickMenuButton(stranger, MatrixTunerMenu.BUTTON_TOGGLE) && !be.isPublic(),
                "非主人非 OP 按了模式不该变");
        MatrixTunerMenu ownerMenu = new MatrixTunerMenu(0, owner.getInventory(), be);
        helper.assertTrue(!ownerMenu.clickMenuButton(owner, 1) && !be.isPublic(), "别的按钮号不收");
        helper.assertTrue(ownerMenu.clickMenuButton(owner, MatrixTunerMenu.BUTTON_TOGGLE) && be.isPublic(), "主人按了应切成公开");
        helper.assertTrue(ownerMenu.clickMenuButton(owner, MatrixTunerMenu.BUTTON_TOGGLE) && !be.isPublic(), "再按切回私人");

        UUID someone = UUID.randomUUID();
        helper.assertTrue(MatrixTunerBlockEntity.canToggle(null, someone, true), "没记主人时 OP 能切");
        helper.assertTrue(!MatrixTunerBlockEntity.canToggle(null, someone, false), "没记主人时普通玩家不能切");
        helper.assertTrue(MatrixTunerBlockEntity.canToggle(ownerId, someone, true), "OP 总能切");
        helper.succeed();
    }

    // ---------------- 不碰的东西 ----------------

    /** 身上有一个腐化效果：调律后效果还在，五芒星档位、抽取冷却都不动，只是损伤少了 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void matrixTunerNeverCuresEffects(GameTestHelper helper) {
        MatrixTunerBlockEntity be = place(helper, 50_000, 20_000D);
        ServerPlayer player = owner(helper, be, 1000);
        IPlayerData data = data(player);
        helper.assertTrue(data.addCorruptionEffect("body_0"), "应能加上效果 body_0");
        data.setPentacleTier(1);
        // 测试玩家不在世界里，腐化模块不会替它倒数冷却；调律后还是这个数，才说明调律器没碰它
        data.setCorruptionCooldown(12_345);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 1, "等第一次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(data(player).getMatrixDamage() == 940, "损伤照常抹");
                    helper.assertTrue(data(player).hasCorruptionEffect("body_0") && data(player).getCorruptionEffects().size() == 1,
                            "腐化效果不该被治掉");
                    helper.assertTrue(data(player).getPentacleTier() == 1, "五芒星档位不该变");
                    helper.assertTrue(data(player).getCorruptionCooldown() == 12_345,
                            "抽取冷却不该变，实际 " + data(player).getCorruptionCooldown());
                    unseat(helper);
                })
                .thenSucceed();
    }

    /** 只认站在顶上的真玩家：假玩家、站在旁边地上的人都不算（IDLE，什么都不扣） */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void matrixTunerIgnoresFakeAndBystanders(GameTestHelper helper) {
        MatrixTunerBlockEntity be = place(helper, 10_000, 1000D);
        // 单独一个假玩家身份：要把它挪到顶上，别动别的测试共用的那个。UUID 固定，FakePlayerFactory 按身份缓存，反复跑不会越攒越多
        FakePlayer fake = FakePlayerFactory.get(helper.getLevel(), new GameProfile(FAKE_ID, "[EC5]Tuner"));
        BlockPos abs = helper.absolutePos(CENTER);
        fake.moveTo(abs.getX() + 0.5D, abs.getY() + MatrixTunerBlockEntity.TOP, abs.getZ() + 0.5D, 0F, 0F);
        be.setOwner(fake.getUUID(), fake.getGameProfile().getName());
        be.setPublic(true);
        UUID id = UUID.randomUUID();
        ServerPlayer bystander = ProtectionFrameworkTests.realPlayer(helper, id, "ec5-bystander");
        bystander.moveTo(abs.getX() + 1.5D, abs.getY(), abs.getZ() + 0.5D, 0F, 0F);
        data(bystander).setMatrixDamage(1000);
        seat(helper, fake, bystander);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 1, "等第一次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.IDLE, "假玩家和旁边的人都不算，应是 IDLE，实际 " + be.status());
                    helper.assertTrue(data(bystander).getMatrixDamage() == 1000 && be.getMRUStorage().getMRU() == 10_000, "什么都不动");
                    // 同一个人站上去就算
                    bystander.moveTo(abs.getX() + 0.3D, abs.getY() + MatrixTunerBlockEntity.TOP + 0.2D, abs.getZ() + 0.7D, 0F, 0F);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.cycles() >= 2, "等第二次调律"))
                .thenExecute(() -> {
                    helper.assertTrue(data(bystander).getMatrixDamage() == 940, "站到顶上以后应开始调，实际 " + data(bystander).getMatrixDamage());
                    unseat(helper);
                })
                .thenSucceed();
    }
}
