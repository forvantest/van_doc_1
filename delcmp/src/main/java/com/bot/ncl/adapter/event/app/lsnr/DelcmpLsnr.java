/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static com.bot.txcontrol.mapper.MapperCase.formatUtil;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Delcmp;
import com.bot.ncl.dto.entities.ClcmpBus;
import com.bot.ncl.jpa.entities.impl.ClcmpId;
import com.bot.ncl.jpa.svc.ClcmpService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.dbs.DelNAException;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
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
@Component("DelcmpLsnr")
@Scope("prototype")
public class DelcmpLsnr extends BatchListenerCase<Delcmp> {

    @Autowired private Parse parse;

    @Autowired private DateUtil dateUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private ClcmpService clcmpService;

    private Map<String, String> labelMap;
    // WK-YYMMDD
    private String processDate;
    private int processDateInt = 0;
    private int nbsdy;

    private int clcmpIndex;

    private int clcmpLimit = 100000;

    @Override
    public void onApplicationEvent(Delcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "DelcmpLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Delcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "DelcmpLsnr run()");
        init(event);

        // 確定今日是否為本週最後營業日(WK-RTN:0.否 1.是)
        // 004100     PERFORM 022-WEEK-RTN    THRU    022-WEEK-EXIT.
        if (week()) {
            dataClean();
        }
        batchResponse(event);
    }

    private void init(Delcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        // 讀批次日期檔；若讀不到，顯示訊息，結束程式
        // 設定本營業日變數
        // 003800     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 003900          STOP RUN.
        // 004000     MOVE    FD-BHDATE-TBSDY TO      WK-YYMMDD.

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        processDateInt = parse.string2Integer(processDate);
        nbsdy = parse.string2Integer(labelMap.get("NBSDY"));

        clcmpIndex = 0;
    }

    private boolean week() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "week()");
        // 006600 022-WEEK-RTN.
        // WK-RTN預設為0
        // 006700     MOVE    0                   TO     WK-RTN.
        // 若本日為周六，WK-RTN設為1，結束本段落
        // 006800     IF      FD-BHDATE-WEEKDY    =      6
        // 006900       MOVE  1                   TO     WK-RTN
        // 007000       GO TO 022-WEEK-EXIT.
        // 執行029-POINTTBSDY-RTN，找到本營業日的日曆資料
        // 007100     PERFORM 029-POINTTBSDY-RTN  THRU   029-POINTTBSDY-EXIT.
        // 007200 022-LOOP.
        // 往下找
        // 007300     ADD     1                   TO     WK-CLNDR-KEY.
        // 007400     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" WK-CLNDR-STUS
        // 007500          MOVE  0                TO     WK-RTN
        // 007600          CLOSE FD-CLNDR
        // 007700          GO TO 022-WEEK-EXIT.
        // 非假日(今日非本周最後營業日)，WK-RTN設為0，關閉FD-CLNDR，結束本段落
        // 假日
        //   若周六，WK-RTN設為1，關閉FD-CLNDR，結束本段落
        //   其他，GO TO 022-LOOP
        // 007800     IF      FD-CLNDR-HOLIDY     NOT =  1
        // 007900       MOVE  0                   TO     WK-RTN
        // 008000       CLOSE FD-CLNDR
        // 008100       GO TO 022-WEEK-EXIT
        // 008200     ELSE
        // 008300      IF     FD-CLNDR-WEEKDY     =      6
        // 008400       MOVE  1                   TO     WK-RTN
        // 008500       CLOSE FD-CLNDR
        // 008600       GO TO 022-WEEK-EXIT
        // 008700      ELSE
        // 008800       GO TO 022-LOOP.
        // 008900 022-WEEK-EXIT.
        // 009000     EXIT.
        int weekdy = getWeekdy(processDateInt);
        if (weekdy == 6) {
            return true;
        }
        DateDto dateDto = new DateDto();
        dateDto.setDateS(processDate);
        dateDto.setDateE(nbsdy);
        dateUtil.dateDiff(dateDto);
        int days = dateDto.getDays();
        if (days >= 2) {
            for (int i = 1; i < days; i++) {
                dateDto.init();
                dateDto.setDateS(processDate);
                dateDto.setDays(i);
                dateUtil.getCalenderDay(dateDto);
                int tempDate = dateDto.getDateE2Integer(false);
                int tempWeekdy = getWeekdy(tempDate);
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tempDate = {}", tempDate);
                ApLogHelper.info(
                        log, false, LogType.NORMAL.getCode(), "tempWeekdy = {}", tempWeekdy);
                if (tempWeekdy == 6) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getWeekdy(int inputDate) {
        DateDto dateDto = new DateDto();
        dateDto.setDateS(inputDate);
        dateUtil.getCalenderDay(dateDto);
        return dateDto.getDayOfWeek();
    }

    private void dataClean() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dataClean()");
        // 005100 0000-MAIN-RTN.
        // 將DB-CLCMP-DDS指標移至開始
        // 005200     SET     DB-CLCMP-DDS    TO      BEGINNING.
        // 005250     MOVE    0               TO      WK-DELCNT.
        // 005300 0000-DEL-LOOP.
        // 循序LOCK DB-CLCMP-DDS收付比對檔
        // 005400     LOCK NEXT DB-CLCMP-DDS  ON EXCEPTION
        // 005410          IF DMSTATUS(NOTFOUND)
        // 005420             IF WK-DELCNT  >  0
        // 005430                GO TO 0000-ETR-RTN
        // 005440             ELSE
        // 005450                GO TO 0000-MAIN-EXIT
        // 005460          ELSE
        // 005470                GO TO 0000-MAIN-EXIT.
        List<ClcmpBus> clcmpList = clcmpService.findAll(clcmpIndex, clcmpLimit);
        if (Objects.isNull(clcmpList) || clcmpList.isEmpty()) {
            return;
        }

        for (ClcmpBus clcmp : clcmpList) {
            // 005490* 保留期限或繳費截止日期未到前，不刪除
            // 005500     IF  DB-CLCMP-KDATE  NOT <  WK-YYMMDD
            // 005600     OR (DB-CLCMP-LDATE  NOT <  WK-YYMMDD AND DB-CLCMP-LFLG = 1)
            // 005700       GO  TO  0000-DEL-LOOP.
            // 005702*  虛擬分戶以下永久保留
            // 005704     IF  DB-CLCMP-CODE  = "111981" OR = "111801" OR
            // 005706        (DB-CLCMP-CODE  > "118000" AND
            // 005707         DB-CLCMP-CODE  < "118999" )
            // 005708       GO  TO  0000-DEL-LOOP.
            if (clcmp.getKdate() >= processDateInt
                    || (clcmp.getLdate() >= processDateInt && clcmp.getLflg() == 1)) {
                continue;
            }
            String clcmpCode = clcmp.getCode();
            int clcmpCodeInt = parse.isNumeric(clcmpCode) ? parse.string2Integer(clcmpCode) : 0;
            if (clcmpCodeInt == 111981
                    || clcmpCodeInt == 111801
                    || (clcmpCodeInt > 118000 && clcmpCodeInt < 118999)) {
                continue;
            }

            // 005710*
            // 005712 0000-BTR-RTN.
            // 逐筆刪除，所以WK-DELCNT都會是0
            // BEGIN-TRANSACTION開始交易，若有誤，結束0000-MAIN-RTN
            // 005715      IF      WK-DELCNT     =     0
            // 005720         BEGIN-TRANSACTION NO-AUDIT RESTART-DST ON EXCEPTION
            // 005725              GO TO 0000-MAIN-EXIT.
            // DELETE DB-CLCMP-DDS收付比對檔，若有誤，結束0000-MAIN-RTN
            // 005730      DELETE  DB-CLCMP-DDS ON EXCEPTION GO TO 0000-MAIN-EXIT.
            // 刪檔筆數累加1
            // 005735      ADD     1         TO     WK-DELCNT.
            // 逐筆刪除後，WK-DELCNT刪檔筆數清為0，所以WK-DELCNT都會是1
            // 005740      IF    WK-DELCNT    <     1      GO  TO  0000-DEL-LOOP.
            // 005745 0000-ETR-RTN.
            // END-TRANSACTION確認交易，若有誤，結束0000-MAIN-RTN
            // WK-DELCNT刪檔筆數清為0
            // 005750      END-TRANSACTION   NO-AUDIT RESTART-DST ON EXCEPTION
            // 005755            GO TO 0000-MAIN-EXIT.
            // 005760      MOVE    0   TO    WK-DELCNT.
            // LOOP讀下一筆CLCMP
            // 005765      GO  TO  0000-DEL-LOOP.
            ClcmpId clcmpId = new ClcmpId();
            clcmpId.setCode(clcmp.getCode());
            clcmpId.setRcptid(clcmp.getRcptid());
            ClcmpBus holdClcmp = clcmpService.holdById(clcmpId);
            if (Objects.isNull(holdClcmp)) {
                ApLogHelper.error(
                        log, false, LogType.NORMAL.getCode(), "notfound CLCMPID = [{}]", clcmpId);
                return;
            }
            try {
                clcmpService.delete(holdClcmp);
            } catch (DelNAException e) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "delete clcmp ({}) , error = {}",
                        holdClcmp,
                        e.getMessage());
            }
            this.batchTransaction.commit();
        }

        if (clcmpList.size() == clcmpLimit) {
            clcmpIndex++;
            dataClean();
        }
        // 005800 0000-MAIN-EXIT.
        // 005900     EXIT.
    }

    private void batchResponse(Delcmp event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
