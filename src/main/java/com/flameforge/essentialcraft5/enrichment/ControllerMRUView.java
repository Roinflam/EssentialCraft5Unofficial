package com.flameforge.essentialcraft5.enrichment;

import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.mru.MRUBalance;

/**
 * 部件对外暴露的 MRU（{@code ECCapabilities.MRU}）：控制器存储的一个视图。对应原作
 * ControllerMRUStorageInputOnlyWrapper（接收器）、ControllerMRUStorageOutputOnlyWrapper（输出器）。
 * <p>
 * 与原作不同：
 * <ul>
 *   <li>{@link #getBalance()} 报控制器的真实平衡值。原作三个包装类的 getBalance 都少写了 return，恒报 1.0，
 *       别的机器从输出器拉出来的 MRU 永远是纯净的，富集仓成了「洗平衡值」的机器。</li>
 *   <li>部件没连着有效的（已成形的）控制器时存量、上限都是 0，加不进也取不出。原作部件引用的控制器拆了也照样能抽。</li>
 *   <li>接收器（INPUT）对外的存量读数恒为 0（上限照报整座仓的）：它本来就取不出 MRU，
 *       机器的宝石误绑到接收器上时宝石状态是黄色「目标没有 MRU」，而不是绿色「工作中」却一点也拉不到。
 *       原作 InputOnlyWrapper 报整座仓的存量。接收器自己的界面走控制器的存储，不经过这里。</li>
 *   <li>setMRU 不透传：外面的东西不该绕过存储规则（上限、超出上限只出不进）直接改仓里的数。
 *       setBalance 透传给控制器（同原作）：平衡值没有容量规则可绕，调它的只有管理员工具平衡值设定器。</li>
 * </ul>
 */
public final class ControllerMRUView implements IMRUHandler {

    public enum Mode {
        /** 接收器：只进不出 */
        INPUT,
        /** 输出器：只出不进 */
        OUTPUT
    }

    private final EnrichmentLink link;
    private final Mode mode;

    public ControllerMRUView(EnrichmentLink link, Mode mode) {
        this.link = link;
        this.mode = mode;
    }

    @Override
    public int getMaxMRU() {
        EnrichmentControllerBlockEntity c = link.controller();
        return c == null ? 0 : c.storage().getMaxMRU();
    }

    /** 输出器报整座仓的存量；接收器恒为 0（取不出，见类注释） */
    @Override
    public int getMRU() {
        EnrichmentControllerBlockEntity c = mode == Mode.OUTPUT ? link.controller() : null;
        return c == null ? 0 : c.storage().getMRU();
    }

    @Override
    public void setMRU(int amount) {
    }

    @Override
    public int addMRU(int amount, boolean doAdd) {
        EnrichmentControllerBlockEntity c = mode == Mode.INPUT ? link.controller() : null;
        return c == null ? amount : c.storage().addMRU(amount, doAdd);
    }

    @Override
    public int extractMRU(int amount, boolean doExtract) {
        EnrichmentControllerBlockEntity c = mode == Mode.OUTPUT ? link.controller() : null;
        return c == null ? 0 : c.storage().extractMRU(amount, doExtract);
    }

    @Override
    public float getBalance() {
        EnrichmentControllerBlockEntity c = link.controller();
        return c == null ? MRUBalance.PURE : c.storage().getBalance();
    }

    /** 透传给控制器（平衡值设定器用）；存储自己会夹到合法范围、标记存盘 */
    @Override
    public void setBalance(float balance) {
        EnrichmentControllerBlockEntity c = link.controller();
        if (c != null) {
            c.storage().setBalance(balance);
        }
    }
}
