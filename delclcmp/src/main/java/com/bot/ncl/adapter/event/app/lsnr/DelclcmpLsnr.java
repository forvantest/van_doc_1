/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Delclcmp;
import com.bot.ncl.dto.entities.ClcmpBus;
import com.bot.ncl.dto.entities.ClcmpbyCodeBus;
import com.bot.ncl.jpa.entities.impl.ClcmpId;
import com.bot.ncl.jpa.svc.ClcmpService;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.dbs.DelNAException;
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
@Component("DelclcmpLsnr")
@Scope("prototype")
public class DelclcmpLsnr extends BatchListenerCase<Delclcmp> {

    @Autowired private Parse parse;

    @Autowired private ClcmpService clcmpService;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String wkCode;
    private int wkDate;

    private int index = 0;
    private int pageLimit = 100000;

    @Override
    public void onApplicationEvent(Delclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "DelclcmpLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Delclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "DelclcmpLsnr run()");
        init(event);
        mainRoutine();
    }

    private void init(Delclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        // 002500 PROCEDURE        DIVISION  USING WK-CODE,
        // 002600                                  WK-DATE.
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        wkDate = parse.string2Integer(textMap.get("WK_DATE")); // TODO: 待確認BATCH參數名稱
        wkCode = textMap.get("WK_CODE"); // TODO: 待確認BATCH參數名稱
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCode={}", wkCode);

        // 003620     MOVE    WK-DATE           TO      WK-DATE7.                  00/01/04
        // 003640     ADD     1000000           TO      WK-DATE7.                  00/01/04
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkDate={}", wkDate);
        if (wkDate < 1000000) {
            wkDate += 1000000;
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkDate={}", wkDate);
    }

    private void mainRoutine() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mainRoutine()");
        // 003700 0000-DEL-LOOP.                                                   93/02/06
        // 003800     MOVE    0                 TO      WK-RTN.
        // 003900     LOCK NEXT DB-CLCMP-IDX1   AT      DB-CLCMP-CODE=WK-CODE
        // 004000       ON EXCEPTION
        // 004100          GO TO 0000-MAIN-EXIT.
        List<ClcmpbyCodeBus> clcmpbyCodeBusList = clcmpService.findbyCode(wkCode, index, pageLimit);
        if (!Objects.isNull(clcmpbyCodeBusList) && !clcmpbyCodeBusList.isEmpty()) {
            for (ClcmpbyCodeBus clcmpbyCodeBus : clcmpbyCodeBusList) {
                // 004200     PERFORM 1000-FILER-RTN    THRU    1000-FILER-EXIT.
                // 004300     IF      WK-RTN            =       0
                // 004400       GO TO 0000-DEL-LOOP
                // 004500     ELSE
                // 004600       NEXT  SENTENCE.
                if (filter(clcmpbyCodeBus)) {
                    continue;
                }
                // 004700*                                                                 93/02/06
                // 004800     BEGIN-TRANSACTION NO-AUDIT RESTART-DST.
                // 004900       DELETE  DB-CLCMP-DDS.
                // 005000     END-TRANSACTION   NO-AUDIT RESTART-DST.
                ClcmpId clcmpId = new ClcmpId(clcmpbyCodeBus.getCode(), clcmpbyCodeBus.getRcptid());
                ClcmpBus clcmpBus = clcmpService.holdById(clcmpId);
                try {
                    clcmpService.delete(clcmpBus);
                } catch (DelNAException e) {
                    ApLogHelper.error(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "DelNAException={}",
                            e.getMessage());
                }
                // 005100*
                // 005200     GO TO 0000-DEL-LOOP.
            }
            if (clcmpbyCodeBusList.size() == pageLimit) {
                index++;
                mainRoutine();
            }
        }
    }

    private boolean filter(ClcmpbyCodeBus clcmpbyCodeBus) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filter()");
        // 005700**CODE=111961
        // 005800** 避免程式當掉後重 RUN 蓋掉已更新之資料
        // 005900** 若 DB-CLCMP-LDATE 為次次營業日時不刪除
        // 006000     IF      DB-CLCMP-LDATE    =       WK-DATE7                   00/01/04
        // 006100       MOVE  0                 TO      WK-RTN
        // 006200     ELSE
        // 006300       MOVE  1                 TO      WK-RTN.
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "clcmpbyCodeBus.getLdate()={}",
                clcmpbyCodeBus.getLdate());
        return clcmpbyCodeBus.getLdate() == wkDate;
    }
}
