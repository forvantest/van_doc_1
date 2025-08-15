/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.STAT3;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.dto.entities.CltmrClmrIdx1Bus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("STAT3Lsnr")
@Scope("prototype")
public class STAT3Lsnr extends BatchListenerCase<STAT3> {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFileSTAT3;
    @Autowired private Parse parse;
    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    // 批次日期
    private String wkTaskYYYMM;
    private STAT3 event;
    List<CltmrClmrIdx1Bus> lCltmr = new ArrayList<CltmrClmrIdx1Bus>();
    List<CltmrBus> tmplCltmr = new ArrayList<CltmrBus>();
    private static final String CHARSET = "Big5";
    private String fileNameSTAT3 = "STAT3";

    private StringBuilder sb = new StringBuilder();
    private List<String> fileContentsSTAT3;
    private Map<String, String> textMap;
    int wkSDate = 0;
    int wkEDate = 0;

    // stat3-rec col
    String stat3Code = "";
    String stat3CodeName = "";
    int stat3Pbrno = 0;
    int stat3Appdt = 0;
    String stat3Entpno = "";
    int stat3Stop = 0;
    String filller = "";

    //// 功能：夜間批次 –製作所有代收類別的資料檔
    //// 讀DB-CLMR-DDS 事業單位交易設定檔,寫檔
    //// 寫FD-STAT3("DATA/CL/BH/ANALY/STAT3")

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(STAT3 event) {
        this.event = event;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT3Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(STAT3 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT3Lsnr run()");

        fileNameSTAT3 = fileDir + fileNameSTAT3;
        fileContentsSTAT3 = new ArrayList<String>();

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();

        wkTaskYYYMM = textMap.get("WK_TASK_YYYMM"); // TODO: 待確認BATCH參數名稱
        ApLogHelper.info(log, false, LogType.BATCH.getCode(), wkTaskYYYMM);

        // 005600 0000-START-RTN.
        //
        //// 開啟檔案
        //
        // 005700     OPEN INQUIRY BOTSRDB.
        // 005800     OPEN OUTPUT  FD-STAT3.
        // 005900     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        // 006000     MOVE    "N"             TO     WK-FLAG.
        // 006100*    MOVE    "Y"             TO     WK-PBRNAME.
        //
        //// 設定日期
        //
        // 006200     MOVE    WK-TASK-YYYMM   TO     WK-TASK-YYYMM-N.
        // 006300     MOVE    WK-TASK-YYYMM-N TO     WK-TEMP-SYYYMM,WK-TEMP-EYYYMM.
        // 006400     MOVE    WK-TEMP-SDATE   TO     WK-SDATE.
        // 006500     MOVE    WK-TEMP-EDATE   TO     WK-EDATE.
        //
        //// 執行0000-MAIN-RTN
        //
        // 006600     PERFORM 0000-MAIN-RTN   THRU   0000-MAIN-EXIT.
        // 006700 0000-END-RTN.

        // 沒用到??
        //        int thisMonth = parse.string2Integer(batchDate) / 100;

        //        wkSDate = thisMonth * 100 + 1;
        //        wkEDate = thisMonth * 100 + 31;

        _0000_MAIN();
        writeFile();
    }

    private void _0000_MAIN() {
        // 007700 0000-MAIN-RTN.
        //
        //// 將DB-CLMR-IDX1指標移至開始
        //
        // 007800     SET     DB-CLMR-IDX1    TO    BEGINNING.
        // 007900 0000-MAIN-LOOP.
        //
        //// FIND NEXT 事業單位交易設定檔
        //// 若有誤,跳到0000-MAIN-EXIT
        //
        // 008000     FIND  NEXT  DB-CLMR-IDX1
        // 008100           ON EXCEPTION
        // 008200           GO  TO   0000-MAIN-EXIT.
        // 008300*    IF    DB-CLMR-APPDT   NOT <  WK-SDATE
        // 008400*      AND DB-CLMR-APPDT   NOT >  WK-EDATE
        // 008500     PERFORM  1000-FILE-RTN   THRU  1000-FILE-EXIT.
        // 008600*    ELSE
        // 008700*          NEXT SENTENCE.
        // 008800     GO TO      0000-MAIN-LOOP.(就是跑完一遍再跑一遍)
        // 008900 0000-MAIN-EXIT.

        lCltmr = cltmrService.findClmrIdx1(0, 99999999, 0, Integer.MAX_VALUE);

        if (Objects.isNull(lCltmr)) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Cltmr is null");
            // 006800     IF      WK-FLAG         =      "N"
            // 006900       MOVE  SPACES          TO     STAT3-REC
            // 007000       MOVE  " 本月無新增的代收類別 "  TO    STAT3-CODENAME
            // 007100       WRITE STAT3-REC.
            _1000_FILE(null);

        } else {

            for (CltmrClmrIdx1Bus r : lCltmr) {

                // 有問題就跳過
                if (!_1000_FILE(r)) {
                    continue;
                }
            }
        }
    }

    private boolean _1000_FILE(CltmrClmrIdx1Bus r) {
        // 006800     IF      WK-FLAG         =      "N"
        // 006900       MOVE  SPACES          TO     STAT3-REC
        // 007000       MOVE  " 本月無新增的代收類別 "  TO    STAT3-CODENAME
        // 007100       WRITE STAT3-REC.
        if (Objects.isNull(r)) {
            init_STAT3_REC();
            stat3CodeName = " 本月無新增的代收類別 ";
            write_STAT3_REC();
            return false;
        }

        // 009200 1000-FILE-RTN.
        // 009300     MOVE       DB-CLMR-CODE      TO      WK-CODE.
        //
        //// IF WK-CODE1 = "0",跳到1000-FILE-EXIT
        //// IF WK-CODE-F2 = "37" OR = "39",跳到1000-FILE-EXIT
        //
        // 009400     IF         WK-CODE1           =      "0"
        // 009500       GO  TO   1000-FILE-EXIT.
        // 009520     IF         WK-CODE-F2         =  "37"  OR  =  "39"
        // 009540       GO  TO   1000-FILE-EXIT.

        // 先確認是否有值
        if (r.getCode().trim().isEmpty()) {
            return false;
        }
        // 有值再賦值
        String wkCode = r.getCode().trim();
        String wkCode2 = wkCode.substring(0, 2);

        if ("37".equals(wkCode2) || "39".equals(wkCode2)) {
            return false;
        }
        //// 搬相關資料到STAT3-REC
        //
        // 009600     MOVE       "Y"               TO      WK-FLAG.
        // 009700     MOVE       SPACES            TO      STAT3-REC.
        // 009800     MOVE       DB-CLMR-CODE      TO      STAT3-CODE.
        // 009900     MOVE       DB-CLMR-CNAME     TO      STAT3-CODENAME.
        // 010000     MOVE       DB-CLMR-PBRNO     TO      STAT3-PBRNO.

        // 010800     MOVE       DB-CLMR-APPDT     TO      STAT3-APPDT.
        // 010805     IF         DB-CLMR-ENTPNO    =       SPACES
        // 010810       MOVE     DB-CLMR-HENTPNO   TO      STAT3-ENTPNO
        // 010815     ELSE
        // 010820       MOVE     DB-CLMR-ENTPNO    TO      STAT3-ENTPNO.
        // 010840     MOVE       DB-CLMR-STOP      TO      STAT3-STOP.

        init_STAT3_REC();
        stat3Code = r.getCode().isEmpty() ? "" : r.getCode().trim();
        ClmrBus clmr = clmrService.findById(stat3Code);
        if (Objects.isNull(clmr)) {
            return false;
        } else {
            stat3CodeName = clmr.getCname();
            stat3Pbrno = clmr.getPbrno();
            stat3Appdt = r.getAppdt();
            stat3Entpno = r.getEntpno();
            if ("".equals(stat3Entpno)) {
                stat3Entpno = r.getHentpno() + "";
            } else {
                stat3Entpno = r.getEntpno();
            }
            stat3Stop = clmr.getStop();
        }

        //// 寫檔FD-STAT3
        //
        // 010900     WRITE      STAT3-REC.

        // 011100 1000-FILE-EXIT.
        write_STAT3_REC();

        return true;
    }

    private void init_STAT3_REC() {
        // 002200  01  STAT3-REC.
        // 002300      03  STAT3-CODE                       PIC X(06).
        // 002400      03  STAT3-CODENAME                   PIC X(40).
        // 002500      03  STAT3-PBRNO                      PIC 9(03).
        // 002700      03  STAT3-APPDT                      PIC 9(07).
        // 002720      03  STAT3-ENTPNO                     PIC X(08).
        // 002740      03  STAT3-STOP                       PIC 9(01).
        // 002800      03  FILLER                           PIC X(15).

        stat3Code = "";
        stat3CodeName = "";
        stat3Pbrno = 0;
        stat3Appdt = 0;
        stat3Entpno = "";
        stat3Stop = 0;
        filller = "";
    }

    private void write_STAT3_REC() {

        sb = new StringBuilder();
        sb.append(formatUtil.padX(stat3Code, 6));
        sb.append(formatUtil.padX(stat3CodeName, 40));
        sb.append(formatUtil.pad9(stat3Pbrno + "", 3));
        sb.append(formatUtil.pad9(stat3Appdt + "", 7));
        sb.append(formatUtil.padX(stat3Entpno, 8));
        sb.append(formatUtil.padX(stat3Stop + "", 1));
        sb.append(formatUtil.padX(filller, 15));
        fileContentsSTAT3.add(sb.toString());
    }

    private void writeFile() {

        textFileSTAT3.deleteFile(fileNameSTAT3);

        try {
            textFileSTAT3.writeFileContent(fileNameSTAT3, fileContentsSTAT3, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }
}
