/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTDELBAF;
import com.bot.ncl.dto.entities.ClbafBus;
import com.bot.ncl.dto.entities.ClbafbyEntdyTxtypeBus;
import com.bot.ncl.jpa.entities.impl.ClbafId;
import com.bot.ncl.jpa.svc.ClbafService;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("OUTDELBAFLsnr")
@Scope("prototype")
public class OUTDELBAFLsnr extends BatchListenerCase<OUTDELBAF> {

    @Autowired private Parse parse;
    @Autowired private ClbafService clbafService;
    private OUTDELBAF event;
    private ClbafBus dClbaf = new ClbafBus();

    private static final String CLBAF_TXTYPE_K = "K";
    private Map<String, String> textMap;
    // ----tita----
    private int wkTaskDate;
    // ----wk----
    private int wkYYYYMMDD;
    // ----CLBAF----
    private int cllbr;
    private int entdy;
    private String code;
    private int pbrno;
    private int crdb;
    private String txtype;
    private int curcd;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTDELBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTDELBAFLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTDELBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTDELBAFLsnr run");
        init(event);
        // 003000     PERFORM 0000-MAIN-RTN   THRU    0000-MAIN-EXIT
        main();
        // 003100     DISPLAY "SYM/CL/BH/OUTING/DELBAF MAINTAIN OK".
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/OUTING/DELBAF MAINTAIN OK");
    }

    private void init(OUTDELBAF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTDELBAFLsnr init");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkTaskDate = parse.string2Integer(textMap.get("WK_TASK_DATE"));
        wkYYYYMMDD = wkTaskDate;
        if (wkYYYYMMDD > 19110000) {
            wkYYYYMMDD = wkYYYYMMDD - 19110000;
        }
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTDELBAFLsnr main");
        // 003600 0000-MAIN-RTN.
        // 003700 0000-DEL-LOOP.
        // 003800     SET DB-CLBAF-IDX1  TO    BEGINNING.
        // 003900     LOCK NEXT DB-CLBAF-IDX1  AT DB-CLBAF-DATE   = WK-YYYMMDD
        // 004000                             AND DB-CLBAF-TXTYPE = "K"
        List<ClbafbyEntdyTxtypeBus> lClbaf =
                clbafService.findbyEntdyTxtype(wkYYYYMMDD, CLBAF_TXTYPE_K, 0, Integer.MAX_VALUE);
        // 004100     ON EXCEPTION GO TO 0000-MAIN-EXIT.
        if (Objects.isNull(lClbaf)) {
            return;
        }

        for (ClbafbyEntdyTxtypeBus tClbaf : lClbaf) {
            cllbr = tClbaf.getCllbr();
            entdy = tClbaf.getEntdy();
            code = tClbaf.getCode();
            pbrno = tClbaf.getPbrno();
            crdb = tClbaf.getCrdb();
            txtype = tClbaf.getTxtype();
            curcd = tClbaf.getCurcd();
            dClbaf =
                    clbafService.holdById(
                            new ClbafId(cllbr, entdy, code, pbrno, crdb, txtype, curcd));
            if (Objects.isNull(dClbaf)) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "鎖定資料錯誤 {} {} {} {} {} {} {}",
                        cllbr,
                        entdy,
                        code,
                        pbrno,
                        crdb,
                        txtype,
                        curcd);
            }
            // 004200
            // 004300     BEGIN-TRANSACTION NO-AUDIT RESTART-DST .
            // 004400     DELETE  DB-CLBAF-DDS                   .
            try {
                clbafService.delete(dClbaf);
            } catch (Exception e) {
                throw new LogicException("", "刪除收付累計檔");
            }
            // 004500     END-TRANSACTION   NO-AUDIT RESTART-DST .
            // 004600
            // 004700     GO TO 0000-DEL-LOOP.
        }
        // 004800 0000-MAIN-EXIT.
    }
}
