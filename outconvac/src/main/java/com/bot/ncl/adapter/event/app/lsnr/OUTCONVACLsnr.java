/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTCONVAC;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("OUTCONVACLsnr")
@Scope("prototype")
public class OUTCONVACLsnr extends BatchListenerCase<OUTCONVAC> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;
    private OUTCONVAC event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CL022_FILE_PATH = "CL022\\003"; // 目錄
    private static final String FILE_NAME_ACTRDTL = "ACTRDTL."; // 產檔檔名
    private static final String FILE_NAME_UPDBAF = "UPDBAF."; // 讀檔檔名
    private String PATH_SEPARATOR = File.separator;
    private StringBuilder sb = new StringBuilder();
    private List<String> fileACTRDTLContents; //  檔案內容

    private Map<String, String> textMap;
    private int wkTaskDate;
    private int wkTaskRdate;
    // ----WK----
    private String wkAcdir; // 產檔路徑
    private String wkUpddir; // 讀檔路徑
    private int wkDate;
    private int wkUdate;
    private int wkAdate;
    // ----UPDBAF----
    private BigDecimal updbafAmt;
    private BigDecimal updbafIpal;
    private int updbafPbrno;
    // ----CN_ACTR----
    private static final String CN_ACTR_A190236 = "11A01020036000000";
    private static final String CN_ACTR_A190237 = "11A01020037000000";
    private static final String CN_ACTR_G0001 = "14199015000000000";

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTCONVAC event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF1Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTCONVAC event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF1Lsnr run");

        init(event);
        //// 若FD-UPDBAF檔案不存在，跳到0000-END-RTN 顯示訊息、結束程式
        // 005200     IF  ATTRIBUTE  RESIDENT   OF FD-UPDBAF  IS NOT = VALUE(TRUE)
        if (textFile.exists(wkUpddir)) {
            //// 執行0000-MAIN-RTN，讀FD-UPDBAF、寫FD-ACTRDTL會計入帳檔
            // 005500     PERFORM 0000-MAIN-RTN  THRU    0000-MAIN-EXIT  .
            main();
        }
        // 005600 0000-END-RTN.
        //// 顯示訊息、結束程式
        // 005700     DISPLAY "SYM/CL/BH/OUTING/CONVAC OK".
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/OUTING/CONVAC OK");
        // 006000     STOP RUN.
    }

    private Boolean init(OUTCONVAC event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF1Lsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkTaskDate = parse.string2Integer(textMap.get("WK_TASK_DATE"));
        wkTaskRdate = parse.string2Integer(textMap.get("RGDAY"));
        //// 搬接收參數-實際入帳日 給WK-DATE
        // 004700     MOVE    WK-TASK-DATE    TO      WK-DATE        .
        wkDate = wkTaskDate;

        //// 搬接收參數-預定入帳日 給WK-PUTDIR、WK-ACDIR檔名變數值
        // 004800     MOVE    WK-TASK-RDATE   TO      WK-UDATE        ,
        // 004900                                     WK-ADATE        ,
        wkUdate = wkTaskRdate;
        wkAdate = wkTaskRdate;

        //// 設定檔名
        ////  WK-ACDIR="DATA/GN/DWL/CL022/003/"+WK-ADATE 9(07)+"/ACTRDTL."
        ////  WK-PUTDIR="DATA/GN/DWL/CL022/003/"+WK-UDATE 9(07)+"/UPDBAF."
        // 005000     CHANGE ATTRIBUTE FILENAME OF FD-ACTRDTL TO WK-ACDIR .
        // 005100     CHANGE ATTRIBUTE FILENAME OF FD-UPDBAF  TO WK-UPDDIR.
        wkAcdir =
                fileDir
                        + CL022_FILE_PATH
                        + PATH_SEPARATOR
                        + wkAdate
                        + PATH_SEPARATOR
                        + FILE_NAME_ACTRDTL;
        wkUpddir =
                fileDir
                        + CL022_FILE_PATH
                        + PATH_SEPARATOR
                        + wkUdate
                        + PATH_SEPARATOR
                        + FILE_NAME_UPDBAF;

        fileACTRDTLContents = new ArrayList<>();
        return true;
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTCONVACLsnr main");
        // 006200 0000-MAIN-RTN.

        //// 開啟檔案
        // 006300     OPEN   INPUT   FD-UPDBAF.
        // 006400     OPEN   OUTPUT  FD-ACTRDTL.
        // 006500 0000-MAIN-LOOP.
        //
        //// 循序讀取FD-UPDBAF，直到檔尾，跳到0000-FLAST
        //
        // 006600     READ   FD-UPDBAF AT END GO TO 0000-MAIN-LAST.
        List<String> lines = textFile.readFileContent(wkUpddir, CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            cnt++;
            // 01 UPDBAF-REC TOTAL 60 BYTES
            // 03 UPDBAF-CODE	X(06)	代收類別 0-6
            // 03 UPDBAF-PBRNO	9(03)	主辦行 6-9
            // 03 UPDBAF-FEE	9(06)	總手續費 9-15
            // 03 UPDBAF-CNT	9(05)	總筆數 15-20
            // 03 UPDBAF-UPDDATE	9(08)	 20-28
            // 03 UPDBAF-TXTYPE	X(01)	帳務別 28-29
            // 03 UPDBAF-AMT	9(13)	總金額 29-42
            // 03 UPDBAF-FEECOST	9(06)	 42-48
            // 03 UPDBAF-R1	REDEFINES UPDBAF-FEECOST
            //  05 UPDBAF-IPAL	9(06)	總內部損益 42-48
            // 03 FILLER	X(12)	48-60
            updbafPbrno = parse.string2Integer(detail.substring(6, 9));
            updbafAmt = parse.string2BigDecimal(detail.substring(29, 42));
            updbafIpal = parse.string2BigDecimal(detail.substring(42, 48));
            //// 寫會計入帳檔
            // 006700* 本金從營業部撥入各主辦行的四邊帳   BY 聯往 36
            // 006800     PERFORM 2100-ACTRDTL-RTN THRU 2100-ACTRDTL-EXIT.
            actrdtl_2100();
            // 006900     PERFORM 2200-ACTRDTL-RTN THRU 2200-ACTRDTL-EXIT.
            actrdtl_2200();
            // 007000     PERFORM 2300-ACTRDTL-RTN THRU 2300-ACTRDTL-EXIT.
            actrdtl_2300();
            // 007100     PERFORM 2400-ACTRDTL-RTN THRU 2400-ACTRDTL-EXIT.
            actrdtl_2400();
            // 007200* 主辦行付給營業部的手續費用的四邊帳 BY 聯往 37
            // 007250     IF  UPDBAF-IPAL      >   0
            if (updbafIpal.compareTo(BigDecimal.ZERO) > 0) {
                // 007300       PERFORM 3100-ACTRDTL-RTN THRU 3100-ACTRDTL-EXIT
                actrdtl_3100();
                // 007400       PERFORM 3200-ACTRDTL-RTN THRU 3200-ACTRDTL-EXIT
                actrdtl_3200();
                // 007500       PERFORM 3300-ACTRDTL-RTN THRU 3300-ACTRDTL-EXIT
                actrdtl_3300();
                // 007600       PERFORM 3400-ACTRDTL-RTN THRU 3400-ACTRDTL-EXIT.
                actrdtl_3400();
            }
            // 007650
            //// LOOP讀下一筆FD-UPDBAF
            // 007700     GO TO 0000-MAIN-LOOP.

            if (cnt == lines.size()) {
                // 007800 0000-MAIN-LAST.
                //// 關閉檔案
                // 007900     CLOSE   FD-UPDBAF
                // 008000     CLOSE   FD-ACTRDTL WITH SAVE.
                try {
                    textFile.writeFileContent(wkAcdir, fileACTRDTLContents, CHARSET);
                } catch (LogicException e) {
                    moveErrorResponse(e);
                }
                // 008100 0000-MAIN-EXIT.
            }
        }
    }

    private void actrdtl_2100() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "actrdtl_2100 ....");
        // 008400 2100-ACTRDTL-RTN.
        // 008500* 本金　代收行的借方
        // 008600     MOVE    SPACES           TO   FD-ACTRDTL-REC.
        sb = new StringBuilder();
        // 008700     MOVE    WK-DATE          TO   FD-ACTRDTL-TXDATE.
        // 008800     MOVE    003              TO   FD-ACTRDTL-KINBR.
        // 008900     MOVE    003              TO   FD-ACTRDTL-ACBRNO.
        // 009000     MOVE    31               TO   FD-ACTRDTL-SECNO.
        // 009100     MOVE    "M"              TO   FD-ACTRDTL-ACTYPE.
        // 009200     MOVE    1                TO   FD-ACTRDTL-CRDB.
        // 009300     MOVE    1                TO   FD-ACTRDTL-DEPT.
        // 009350*IFRS-2 101/06/06 CSCHEN
        // 009400     MOVE    "BBBB00"         TO   FD-ACTRDTL-ACNO.
        // 009500     MOVE    "0001"           TO   FD-ACTRDTL-SBNO.
        // 009600     MOVE    "0031"           TO   FD-ACTRDTL-DTLNO.
        // 009650*IFRS-2 End
        // 009700     MOVE    00               TO   FD-ACTRDTL-CURCD.
        // 009800     MOVE    1                TO   FD-ACTRDTL-TXCNT.
        // 009900     MOVE    UPDBAF-AMT       TO   FD-ACTRDTL-TXAMT.
        // 010000     MOVE    SPACES           TO   FD-ACTRDTL-MRKEY.
        // 010100     MOVE    SPACES           TO   FD-ACTRDTL-COKEY.
        // 010200     MOVE    31               TO   FD-ACTRDTL-SUMNO.
        // 010300     MOVE    "CL-G61"         TO   FD-ACTRDTL-BATCHNO.
        // 010400     MOVE    0                TO   FD-ACTRDTL-UPDCD.
        // 010500     MOVE    "VY"             TO   FD-ACTRDTL-VTLRNO.

        int fdActrdtlTxdate = wkDate;
        int fdActrdtlKinbr = 3;
        int fdActrdtlAcbrno = 3;
        int fdActrdtlSecno = 31;
        String fdActrdtlActype = "M";
        int fdActrdtlCrdb = 1;
        int fdActrdtlDept = 1;
        String fdActrdtlAcno = "BBBB00";
        String fdActrdtlSbno = "0001";
        String fdActrdtlDtlno = "0031";
        int fdActrdtlCurcd = 00;
        int fdActrdtlTxcnt = 1;
        BigDecimal fdActrdtlTxamt = updbafAmt;
        String fdActrdtlMrkey = "";
        String fdActrdtlCokey = "";
        int fdActrdtlSumno = 31;
        String fdActrdtlBatchno = "CL-G61";
        int fdActrdtlUpdcd = 0;
        String fdActrdtlVtlrno = "VY";

        // 010600     WRITE   FD-ACTRDTL-REC.
        // 01 FD-ACTRDTL-REC TOTAL 249 BYTES
        // 03 FD-ACTRDTL-TXDATE	9(08)	交易日期	WK-DATE
        sb.append(formatUtil.pad9("" + fdActrdtlTxdate, 8));
        // 接收參數-實際入帳日
        // 03 FD-ACTRDTL-KINBR	9(03)	輸入行	003
        sb.append(formatUtil.pad9("" + fdActrdtlKinbr, 3));
        // 03 FD-ACTRDTL-ACBRNO	9(03)	掛帳行	003 or UPDBAF-PBRNO
        sb.append(formatUtil.pad9("" + fdActrdtlAcbrno, 3));
        // 03 FD-ACTRDTL-SECNO	9(02)	業務別（參考共同說明）	31
        sb.append(formatUtil.pad9("" + fdActrdtlSecno, 2));
        // 03 FD-ACTRDTL-ACTYPE	X(01)	帳務現轉別：Ｃ．現金　Ｍ．轉帳　Ｎ．交換（轉帳）	"M"
        sb.append(formatUtil.padX(fdActrdtlActype, 1));
        // 03 FD-ACTRDTL-CRDB	9(01)	借貸別：１   借　２   貸	1 or 2
        sb.append(formatUtil.pad9("" + fdActrdtlCrdb, 1));
        // 03 FD-ACTRDTL-ACTNO		會計帳號	詳程式
        //  05 FD-ACTRDTL-DEPT	9(01)	部別	1
        sb.append(formatUtil.pad9("" + fdActrdtlDept, 1));
        //  05 FD-ACTRDTL-ACNO	X(06)	科目
        sb.append(formatUtil.padX(fdActrdtlAcno, 6));
        //  05 FD-ACTRDTL-SBNO	X(04)	子目
        sb.append(formatUtil.padX(fdActrdtlSbno, 4));
        //  05 FD-ACTRDTL-DTLNO	X(04)	細目
        sb.append(formatUtil.padX(fdActrdtlDtlno, 4));
        // 03 FD-ACTRDTL-CURCD	9(02)	幣別	00
        sb.append(formatUtil.pad9("" + fdActrdtlCurcd, 2));
        // 03 FD-ACTRDTL-TXCNT	9(06)	記帳筆數	1
        sb.append(formatUtil.pad9("" + fdActrdtlTxcnt, 1));
        // 03 FD-ACTRDTL-TXAMT	9(14)V9(02)	記帳金額	UPDBAF-AMT or UPDBAF-IPAL
        sb.append(reportUtil.customFormat("" + fdActrdtlCurcd, "99999999999999.99"));
        // 03 FD-ACTRDTL-MRKEY	X(20)	帳號／交易編號／銷帳帳號	SPACES
        sb.append(formatUtil.padX(fdActrdtlMrkey, 20));
        // 03 FD-ACTRDTL-COKEY	X(20)	對方帳號／交易編號／銷帳編號	SPACES
        sb.append(formatUtil.padX(fdActrdtlCokey, 20));
        // 03 FD-ACTRDTL-SUMNO	9(02)	彙總別	31
        sb.append(formatUtil.pad9("" + fdActrdtlSumno, 2));
        // 03 FD-ACTRDTL-UPDCD	9(01)	需過帳記號	0
        sb.append(formatUtil.pad9("" + fdActrdtlUpdcd, 1));
        // 03 FD-ACTRDTL-VTLRNO	X(02)	指定結帳櫃員	"VY"
        sb.append(formatUtil.padX(fdActrdtlVtlrno, 2));
        // 03 FD-ACTRDTL-BAFGRP		業務科目資訊	SPACES
        //  05 FD-ACTRDTL-APNO	9(03)	科目
        sb.append(formatUtil.pad9("", 3));
        //  05 FD-ACTRDTL-CHARCD	9(02)	性質別
        sb.append(formatUtil.pad9("", 2));
        // 03 FD-ACTRDTL-OTRGRP		其他資訊
        //  05 FD-ACTRDTL-BATCHNO	X(12)	批號	"CL-G61"
        sb.append(formatUtil.padX(fdActrdtlBatchno, 12));
        //  05 FD-ACTRDTL-MEMO	X(80)	註記	SPACES
        sb.append(formatUtil.padX("", 80));
        //  05 FD-ACTRDTL-ENTACC	9(01)		SPACES
        sb.append(formatUtil.pad9("", 1));
        // 03 FD-ACTRDTL-MISDEP	X(06)		SPACES
        sb.append(formatUtil.padX("", 6));
        // 03 FD-ACTRDTL-FILLER	X(43)		SPACES
        sb.append(formatUtil.padX("", 43));

        fileACTRDTLContents.add(formatUtil.padX(sb.toString(), 249));
        // 010700 2100-ACTRDTL-EXIT.
    }

    private void actrdtl_2200() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "actrdtl_2200 ....");
        // 011000 2200-ACTRDTL-RTN.
        // 011100* 本金　代收行的貸方
        // 011200     MOVE    SPACES           TO   FD-ACTRDTL-REC.
        sb = new StringBuilder();
        // 011300     MOVE    WK-DATE          TO   FD-ACTRDTL-TXDATE.
        int fdActrdtlTxdate = wkDate;
        // 011400     MOVE    003              TO   FD-ACTRDTL-KINBR.
        int fdActrdtlKinbr = 3;
        // 011500     MOVE    003              TO   FD-ACTRDTL-ACBRNO.
        int fdActrdtlAcbrno = 3;
        // 011600     MOVE    31               TO   FD-ACTRDTL-SECNO.
        int fdActrdtlSecno = 31;
        // 011700     MOVE    "M"              TO   FD-ACTRDTL-ACTYPE.
        String fdActrdtlActype = "M";
        // 011800     MOVE    2                TO   FD-ACTRDTL-CRDB.
        int fdActrdtlCrdb = 2;
        // 011900     MOVE    1                TO   FD-ACTRDTL-DEPT.
        int fdActrdtlDept = 1;
        // 011920*IFRS-2 101/06/06 CSCHEN
        // 011940     MOVE    CN-ACTR-A190236  TO   WK-AC-ACTNO.
        // 012000     MOVE    "1A0102"         TO   FD-ACTRDTL-ACNO.
        String fdActrdtlAcno = "1A0102";
        // 012150     MOVE    "0036"           TO   FD-ACTRDTL-SBNO.
        String fdActrdtlSbno = "0036";
        // 012200     MOVE    "0000"           TO   FD-ACTRDTL-DTLNO.
        String fdActrdtlDtlno = "0000";
        // 012250*IFRS-2 End
        // 012300     MOVE    00               TO   FD-ACTRDTL-CURCD.
        int fdActrdtlCurcd = 00;
        // 012400     MOVE    1                TO   FD-ACTRDTL-TXCNT.
        int fdActrdtlTxcnt = 1;
        // 012500     MOVE    UPDBAF-AMT       TO   FD-ACTRDTL-TXAMT.
        BigDecimal fdActrdtlTxamt = updbafAmt;
        // 012600     MOVE    SPACES           TO   FD-ACTRDTL-MRKEY.
        String fdActrdtlMrkey = "";
        // 012700     MOVE    SPACES           TO   FD-ACTRDTL-COKEY.
        String fdActrdtlCokey = "";
        // 012800     MOVE    31               TO   FD-ACTRDTL-SUMNO.
        int fdActrdtlSumno = 31;
        // 012900     MOVE    "CL-G61"         TO   FD-ACTRDTL-BATCHNO.
        String fdActrdtlBatchno = "CL-G61";
        // 013000     MOVE    0                TO   FD-ACTRDTL-UPDCD.
        int fdActrdtlUpdcd = 0;
        // 013100     MOVE    "VY"             TO   FD-ACTRDTL-VTLRNO.
        String fdActrdtlVtlrno = "VY";

        // 013200     WRITE   FD-ACTRDTL-REC.
        // 01 FD-ACTRDTL-REC TOTAL 249 BYTES
        // 03 FD-ACTRDTL-TXDATE	9(08)	交易日期	WK-DATE
        sb.append(formatUtil.pad9("" + fdActrdtlTxdate, 8));
        // 接收參數-實際入帳日
        // 03 FD-ACTRDTL-KINBR	9(03)	輸入行	003
        sb.append(formatUtil.pad9("" + fdActrdtlKinbr, 3));
        // 03 FD-ACTRDTL-ACBRNO	9(03)	掛帳行	003 or UPDBAF-PBRNO
        sb.append(formatUtil.pad9("" + fdActrdtlAcbrno, 3));
        // 03 FD-ACTRDTL-SECNO	9(02)	業務別（參考共同說明）	31
        sb.append(formatUtil.pad9("" + fdActrdtlSecno, 2));
        // 03 FD-ACTRDTL-ACTYPE	X(01)	帳務現轉別：Ｃ．現金　Ｍ．轉帳　Ｎ．交換（轉帳）	"M"
        sb.append(formatUtil.padX(fdActrdtlActype, 1));
        // 03 FD-ACTRDTL-CRDB	9(01)	借貸別：１   借　２   貸	1 or 2
        sb.append(formatUtil.pad9("" + fdActrdtlCrdb, 1));
        // 03 FD-ACTRDTL-ACTNO		會計帳號	詳程式
        //  05 FD-ACTRDTL-DEPT	9(01)	部別	1
        sb.append(formatUtil.pad9("" + fdActrdtlDept, 1));
        //  05 FD-ACTRDTL-ACNO	X(06)	科目
        sb.append(formatUtil.padX(fdActrdtlAcno, 6));
        //  05 FD-ACTRDTL-SBNO	X(04)	子目
        sb.append(formatUtil.padX(fdActrdtlSbno, 4));
        //  05 FD-ACTRDTL-DTLNO	X(04)	細目
        sb.append(formatUtil.padX(fdActrdtlDtlno, 4));
        // 03 FD-ACTRDTL-CURCD	9(02)	幣別	00
        sb.append(formatUtil.pad9("" + fdActrdtlCurcd, 2));
        // 03 FD-ACTRDTL-TXCNT	9(06)	記帳筆數	1
        sb.append(formatUtil.pad9("" + fdActrdtlTxcnt, 1));
        // 03 FD-ACTRDTL-TXAMT	9(14)V9(02)	記帳金額	UPDBAF-AMT or UPDBAF-IPAL
        sb.append(reportUtil.customFormat("" + fdActrdtlCurcd, "99999999999999.99"));
        // 03 FD-ACTRDTL-MRKEY	X(20)	帳號／交易編號／銷帳帳號	SPACES
        sb.append(formatUtil.padX(fdActrdtlMrkey, 20));
        // 03 FD-ACTRDTL-COKEY	X(20)	對方帳號／交易編號／銷帳編號	SPACES
        sb.append(formatUtil.padX(fdActrdtlCokey, 20));
        // 03 FD-ACTRDTL-SUMNO	9(02)	彙總別	31
        sb.append(formatUtil.pad9("" + fdActrdtlSumno, 2));
        // 03 FD-ACTRDTL-UPDCD	9(01)	需過帳記號	0
        sb.append(formatUtil.pad9("" + fdActrdtlUpdcd, 1));
        // 03 FD-ACTRDTL-VTLRNO	X(02)	指定結帳櫃員	"VY"
        sb.append(formatUtil.padX(fdActrdtlVtlrno, 2));
        // 03 FD-ACTRDTL-BAFGRP		業務科目資訊	SPACES
        //  05 FD-ACTRDTL-APNO	9(03)	科目
        sb.append(formatUtil.pad9("", 3));
        //  05 FD-ACTRDTL-CHARCD	9(02)	性質別
        sb.append(formatUtil.pad9("", 2));
        // 03 FD-ACTRDTL-OTRGRP		其他資訊
        //  05 FD-ACTRDTL-BATCHNO	X(12)	批號	"CL-G61"
        sb.append(formatUtil.padX(fdActrdtlBatchno, 12));
        //  05 FD-ACTRDTL-MEMO	X(80)	註記	SPACES
        sb.append(formatUtil.padX("", 80));
        //  05 FD-ACTRDTL-ENTACC	9(01)		SPACES
        sb.append(formatUtil.pad9("", 1));
        // 03 FD-ACTRDTL-MISDEP	X(06)		SPACES
        sb.append(formatUtil.padX("", 6));
        // 03 FD-ACTRDTL-FILLER	X(43)		SPACES
        sb.append(formatUtil.padX("", 43));

        fileACTRDTLContents.add(formatUtil.padX(sb.toString(), 249));
        // 013300 2200-ACTRDTL-EXIT.
    }

    private void actrdtl_2300() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "actrdtl_2300 ....");
        // 013600 2300-ACTRDTL-RTN.
        // 013700* 本金　主辦行的借方
        // 013800     MOVE    SPACES           TO   FD-ACTRDTL-REC.
        sb = new StringBuilder();
        // 013900     MOVE    WK-DATE          TO   FD-ACTRDTL-TXDATE.
        int fdActrdtlTxdate = wkDate;
        // 014000     MOVE    003              TO   FD-ACTRDTL-KINBR.
        int fdActrdtlKinbr = 3;
        // 014100     MOVE    UPDBAF-PBRNO     TO   FD-ACTRDTL-ACBRNO.
        int fdActrdtlAcbrno = updbafPbrno;
        // 014200     MOVE    31               TO   FD-ACTRDTL-SECNO.
        int fdActrdtlSecno = 31;
        // 014300     MOVE    "M"              TO   FD-ACTRDTL-ACTYPE.
        String fdActrdtlActype = "M";
        // 014400     MOVE    "1"              TO   FD-ACTRDTL-CRDB.
        int fdActrdtlCrdb = 1;
        // 014500     MOVE    1                TO   FD-ACTRDTL-DEPT.
        int fdActrdtlDept = 1;
        // 014520*IFRS-2 101/06/06 CSCHEN
        // 014540     MOVE    CN-ACTR-A190236  TO   WK-AC-ACTNO.
        // 014600     MOVE    "1A0102"         TO   FD-ACTRDTL-ACNO.
        String fdActrdtlAcno = "1A0102";
        // 014750     MOVE    "0036"           TO   FD-ACTRDTL-SBNO.
        String fdActrdtlSbno = "0036";
        // 014800     MOVE    "0000"           TO   FD-ACTRDTL-DTLNO.
        String fdActrdtlDtlno = "0000";
        // 014850*IFRS-2 End
        // 014900     MOVE    00               TO   FD-ACTRDTL-CURCD.
        int fdActrdtlCurcd = 00;
        // 015000     MOVE    1                TO   FD-ACTRDTL-TXCNT.
        int fdActrdtlTxcnt = 1;
        // 015100     MOVE    UPDBAF-AMT       TO   FD-ACTRDTL-TXAMT.
        BigDecimal fdActrdtlTxamt = updbafAmt;
        // 015200     MOVE    SPACES           TO   FD-ACTRDTL-MRKEY.
        String fdActrdtlMrkey = "";
        // 015300     MOVE    SPACES           TO   FD-ACTRDTL-COKEY.
        String fdActrdtlCokey = "";
        // 015400     MOVE    31               TO   FD-ACTRDTL-SUMNO.
        int fdActrdtlSumno = 31;
        // 015500     MOVE    "CL-G61"         TO   FD-ACTRDTL-BATCHNO.
        String fdActrdtlBatchno = "CL-G61";
        // 015600     MOVE    0                TO   FD-ACTRDTL-UPDCD.
        int fdActrdtlUpdcd = 0;
        // 015700     MOVE    "VY"             TO   FD-ACTRDTL-VTLRNO.
        String fdActrdtlVtlrno = "VY";
        // 015800     WRITE   FD-ACTRDTL-REC.
        // 01 FD-ACTRDTL-REC TOTAL 249 BYTES
        // 03 FD-ACTRDTL-TXDATE	9(08)	交易日期	WK-DATE
        sb.append(formatUtil.pad9("" + fdActrdtlTxdate, 8));
        // 接收參數-實際入帳日
        // 03 FD-ACTRDTL-KINBR	9(03)	輸入行	003
        sb.append(formatUtil.pad9("" + fdActrdtlKinbr, 3));
        // 03 FD-ACTRDTL-ACBRNO	9(03)	掛帳行	003 or UPDBAF-PBRNO
        sb.append(formatUtil.pad9("" + fdActrdtlAcbrno, 3));
        // 03 FD-ACTRDTL-SECNO	9(02)	業務別（參考共同說明）	31
        sb.append(formatUtil.pad9("" + fdActrdtlSecno, 2));
        // 03 FD-ACTRDTL-ACTYPE	X(01)	帳務現轉別：Ｃ．現金　Ｍ．轉帳　Ｎ．交換（轉帳）	"M"
        sb.append(formatUtil.padX(fdActrdtlActype, 1));
        // 03 FD-ACTRDTL-CRDB	9(01)	借貸別：１   借　２   貸	1 or 2
        sb.append(formatUtil.pad9("" + fdActrdtlCrdb, 1));
        // 03 FD-ACTRDTL-ACTNO		會計帳號	詳程式
        //  05 FD-ACTRDTL-DEPT	9(01)	部別	1
        sb.append(formatUtil.pad9("" + fdActrdtlDept, 1));
        //  05 FD-ACTRDTL-ACNO	X(06)	科目
        sb.append(formatUtil.padX(fdActrdtlAcno, 6));
        //  05 FD-ACTRDTL-SBNO	X(04)	子目
        sb.append(formatUtil.padX(fdActrdtlSbno, 4));
        //  05 FD-ACTRDTL-DTLNO	X(04)	細目
        sb.append(formatUtil.padX(fdActrdtlDtlno, 4));
        // 03 FD-ACTRDTL-CURCD	9(02)	幣別	00
        sb.append(formatUtil.pad9("" + fdActrdtlCurcd, 2));
        // 03 FD-ACTRDTL-TXCNT	9(06)	記帳筆數	1
        sb.append(formatUtil.pad9("" + fdActrdtlTxcnt, 1));
        // 03 FD-ACTRDTL-TXAMT	9(14)V9(02)	記帳金額	UPDBAF-AMT or UPDBAF-IPAL
        sb.append(reportUtil.customFormat("" + fdActrdtlCurcd, "99999999999999.99"));
        // 03 FD-ACTRDTL-MRKEY	X(20)	帳號／交易編號／銷帳帳號	SPACES
        sb.append(formatUtil.padX(fdActrdtlMrkey, 20));
        // 03 FD-ACTRDTL-COKEY	X(20)	對方帳號／交易編號／銷帳編號	SPACES
        sb.append(formatUtil.padX(fdActrdtlCokey, 20));
        // 03 FD-ACTRDTL-SUMNO	9(02)	彙總別	31
        sb.append(formatUtil.pad9("" + fdActrdtlSumno, 2));
        // 03 FD-ACTRDTL-UPDCD	9(01)	需過帳記號	0
        sb.append(formatUtil.pad9("" + fdActrdtlUpdcd, 1));
        // 03 FD-ACTRDTL-VTLRNO	X(02)	指定結帳櫃員	"VY"
        sb.append(formatUtil.padX(fdActrdtlVtlrno, 2));
        // 03 FD-ACTRDTL-BAFGRP		業務科目資訊	SPACES
        //  05 FD-ACTRDTL-APNO	9(03)	科目
        sb.append(formatUtil.pad9("", 3));
        //  05 FD-ACTRDTL-CHARCD	9(02)	性質別
        sb.append(formatUtil.pad9("", 2));
        // 03 FD-ACTRDTL-OTRGRP		其他資訊
        //  05 FD-ACTRDTL-BATCHNO	X(12)	批號	"CL-G61"
        sb.append(formatUtil.padX(fdActrdtlBatchno, 12));
        //  05 FD-ACTRDTL-MEMO	X(80)	註記	SPACES
        sb.append(formatUtil.padX("", 80));
        //  05 FD-ACTRDTL-ENTACC	9(01)		SPACES
        sb.append(formatUtil.pad9("", 1));
        // 03 FD-ACTRDTL-MISDEP	X(06)		SPACES
        sb.append(formatUtil.padX("", 6));
        // 03 FD-ACTRDTL-FILLER	X(43)		SPACES
        sb.append(formatUtil.padX("", 43));

        fileACTRDTLContents.add(formatUtil.padX(sb.toString(), 249));

        // 015900 2300-ACTRDTL-EXIT.
    }

    private void actrdtl_2400() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "actrdtl_2300 ....");
        // 016200 2400-ACTRDTL-RTN.
        // 016300* 本金　主辦行的貸方
        // 016400     MOVE    SPACES           TO   FD-ACTRDTL-REC.
        sb = new StringBuilder();
        // 016500     MOVE    WK-DATE          TO   FD-ACTRDTL-TXDATE.
        int fdActrdtlTxdate = wkDate;
        // 016600     MOVE    003              TO   FD-ACTRDTL-KINBR.
        int fdActrdtlKinbr = 3;
        // 016700     MOVE    UPDBAF-PBRNO     TO   FD-ACTRDTL-ACBRNO.
        int fdActrdtlAcbrno = updbafPbrno;
        // 016800     MOVE    31               TO   FD-ACTRDTL-SECNO.
        int fdActrdtlSecno = 31;
        // 016900     MOVE    "M"              TO   FD-ACTRDTL-ACTYPE.
        String fdActrdtlActype = "M";
        // 017000     MOVE    2                TO   FD-ACTRDTL-CRDB.
        int fdActrdtlCrdb = 2;
        // 017100     MOVE    1                TO   FD-ACTRDTL-DEPT.
        int fdActrdtlDept = 1;
        // 017150*IFRS-2 101/06/06 CSCHEN
        // 017200     MOVE    "BBBB00"         TO   FD-ACTRDTL-ACNO.
        String fdActrdtlAcno = "BBBB00";
        // 017300     MOVE    "0001"           TO   FD-ACTRDTL-SBNO.
        String fdActrdtlSbno = "0001";
        // 017400     MOVE    "0031"           TO   FD-ACTRDTL-DTLNO.
        String fdActrdtlDtlno = "0031";
        // 017450*IFRS-2 End
        // 017500     MOVE    00               TO   FD-ACTRDTL-CURCD.
        int fdActrdtlCurcd = 00;
        // 017600     MOVE    1                TO   FD-ACTRDTL-TXCNT.
        int fdActrdtlTxcnt = 1;
        // 017700     MOVE    UPDBAF-AMT       TO   FD-ACTRDTL-TXAMT.
        BigDecimal fdActrdtlTxamt = updbafAmt;
        // 017800     MOVE    SPACES           TO   FD-ACTRDTL-MRKEY.
        String fdActrdtlMrkey = "";
        // 017900     MOVE    SPACES           TO   FD-ACTRDTL-COKEY.
        String fdActrdtlCokey = "";
        // 018000     MOVE    31               TO   FD-ACTRDTL-SUMNO.
        int fdActrdtlSumno = 31;
        // 018100     MOVE    "CL-G61"         TO   FD-ACTRDTL-BATCHNO.
        String fdActrdtlBatchno = "CL-G61";
        // 018200     MOVE    0                TO   FD-ACTRDTL-UPDCD.
        int fdActrdtlUpdcd = 0;
        // 018300     MOVE    "VY"             TO   FD-ACTRDTL-VTLRNO.
        String fdActrdtlVtlrno = "VY";

        // 018400     WRITE   FD-ACTRDTL-REC.
        // 01 FD-ACTRDTL-REC TOTAL 249 BYTES
        // 03 FD-ACTRDTL-TXDATE	9(08)	交易日期	WK-DATE
        sb.append(formatUtil.pad9("" + fdActrdtlTxdate, 8));
        // 接收參數-實際入帳日
        // 03 FD-ACTRDTL-KINBR	9(03)	輸入行	003
        sb.append(formatUtil.pad9("" + fdActrdtlKinbr, 3));
        // 03 FD-ACTRDTL-ACBRNO	9(03)	掛帳行	003 or UPDBAF-PBRNO
        sb.append(formatUtil.pad9("" + fdActrdtlAcbrno, 3));
        // 03 FD-ACTRDTL-SECNO	9(02)	業務別（參考共同說明）	31
        sb.append(formatUtil.pad9("" + fdActrdtlSecno, 2));
        // 03 FD-ACTRDTL-ACTYPE	X(01)	帳務現轉別：Ｃ．現金　Ｍ．轉帳　Ｎ．交換（轉帳）	"M"
        sb.append(formatUtil.padX(fdActrdtlActype, 1));
        // 03 FD-ACTRDTL-CRDB	9(01)	借貸別：１   借　２   貸	1 or 2
        sb.append(formatUtil.pad9("" + fdActrdtlCrdb, 1));
        // 03 FD-ACTRDTL-ACTNO		會計帳號	詳程式
        //  05 FD-ACTRDTL-DEPT	9(01)	部別	1
        sb.append(formatUtil.pad9("" + fdActrdtlDept, 1));
        //  05 FD-ACTRDTL-ACNO	X(06)	科目
        sb.append(formatUtil.padX(fdActrdtlAcno, 6));
        //  05 FD-ACTRDTL-SBNO	X(04)	子目
        sb.append(formatUtil.padX(fdActrdtlSbno, 4));
        //  05 FD-ACTRDTL-DTLNO	X(04)	細目
        sb.append(formatUtil.padX(fdActrdtlDtlno, 4));
        // 03 FD-ACTRDTL-CURCD	9(02)	幣別	00
        sb.append(formatUtil.pad9("" + fdActrdtlCurcd, 2));
        // 03 FD-ACTRDTL-TXCNT	9(06)	記帳筆數	1
        sb.append(formatUtil.pad9("" + fdActrdtlTxcnt, 1));
        // 03 FD-ACTRDTL-TXAMT	9(14)V9(02)	記帳金額	UPDBAF-AMT or UPDBAF-IPAL
        sb.append(reportUtil.customFormat("" + fdActrdtlCurcd, "99999999999999.99"));
        // 03 FD-ACTRDTL-MRKEY	X(20)	帳號／交易編號／銷帳帳號	SPACES
        sb.append(formatUtil.padX(fdActrdtlMrkey, 20));
        // 03 FD-ACTRDTL-COKEY	X(20)	對方帳號／交易編號／銷帳編號	SPACES
        sb.append(formatUtil.padX(fdActrdtlCokey, 20));
        // 03 FD-ACTRDTL-SUMNO	9(02)	彙總別	31
        sb.append(formatUtil.pad9("" + fdActrdtlSumno, 2));
        // 03 FD-ACTRDTL-UPDCD	9(01)	需過帳記號	0
        sb.append(formatUtil.pad9("" + fdActrdtlUpdcd, 1));
        // 03 FD-ACTRDTL-VTLRNO	X(02)	指定結帳櫃員	"VY"
        sb.append(formatUtil.padX(fdActrdtlVtlrno, 2));
        // 03 FD-ACTRDTL-BAFGRP		業務科目資訊	SPACES
        //  05 FD-ACTRDTL-APNO	9(03)	科目
        sb.append(formatUtil.pad9("", 3));
        //  05 FD-ACTRDTL-CHARCD	9(02)	性質別
        sb.append(formatUtil.pad9("", 2));
        // 03 FD-ACTRDTL-OTRGRP		其他資訊
        //  05 FD-ACTRDTL-BATCHNO	X(12)	批號	"CL-G61"
        sb.append(formatUtil.padX(fdActrdtlBatchno, 12));
        //  05 FD-ACTRDTL-MEMO	X(80)	註記	SPACES
        sb.append(formatUtil.padX("", 80));
        //  05 FD-ACTRDTL-ENTACC	9(01)		SPACES
        sb.append(formatUtil.pad9("", 1));
        // 03 FD-ACTRDTL-MISDEP	X(06)		SPACES
        sb.append(formatUtil.padX("", 6));
        // 03 FD-ACTRDTL-FILLER	X(43)		SPACES
        sb.append(formatUtil.padX("", 43));

        fileACTRDTLContents.add(formatUtil.padX(sb.toString(), 249));

        // 018500 2400-ACTRDTL-EXIT.
    }

    private void actrdtl_3100() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "actrdtl_3100 ....");
        // 018800 3100-ACTRDTL-RTN.
        // 018900* 營業部（代收行）的聯往借方
        // 019000     MOVE    SPACES           TO   FD-ACTRDTL-REC.
        sb = new StringBuilder();
        // 019100     MOVE    WK-DATE          TO   FD-ACTRDTL-TXDATE.
        int fdActrdtlTxdate = wkDate;
        // 019200     MOVE    003              TO   FD-ACTRDTL-KINBR.
        int fdActrdtlKinbr = 3;
        // 019300     MOVE    003              TO   FD-ACTRDTL-ACBRNO.
        int fdActrdtlAcbrno = 3;
        // 019400     MOVE    31               TO   FD-ACTRDTL-SECNO.
        int fdActrdtlSecno = 31;
        // 019500     MOVE    "M"              TO   FD-ACTRDTL-ACTYPE.
        String fdActrdtlActype = "M";
        // 019600     MOVE    1                TO   FD-ACTRDTL-CRDB.
        int fdActrdtlCrdb = 1;
        // 019700     MOVE    1                TO   FD-ACTRDTL-DEPT.
        int fdActrdtlDept = 1;
        // 019720*IFRS-2 101/06/06 CSCHEN
        // 019740     MOVE    CN-ACTR-A190237  TO   WK-AC-ACTNO.
        // 019800     MOVE    "1A0102"         TO   FD-ACTRDTL-ACNO.
        String fdActrdtlAcno = "1A0102";
        // 019900     MOVE    "0037"           TO   FD-ACTRDTL-SBNO.
        String fdActrdtlSbno = "0037";
        // 020000     MOVE    "0000"           TO   FD-ACTRDTL-DTLNO.
        String fdActrdtlDtlno = "0000";
        // 020050*IFRS-2 End
        // 020100     MOVE    00               TO   FD-ACTRDTL-CURCD.
        int fdActrdtlCurcd = 00;
        // 020200     MOVE    1                TO   FD-ACTRDTL-TXCNT.
        int fdActrdtlTxcnt = 1;
        // 020300     MOVE    UPDBAF-IPAL      TO   FD-ACTRDTL-TXAMT.
        BigDecimal fdActrdtlTxamt = updbafIpal;
        // 020400     MOVE    SPACES           TO   FD-ACTRDTL-MRKEY.
        String fdActrdtlMrkey = "";
        // 020500     MOVE    SPACES           TO   FD-ACTRDTL-COKEY.
        String fdActrdtlCokey = "";
        // 020600     MOVE    31               TO   FD-ACTRDTL-SUMNO.
        int fdActrdtlSumno = 31;
        // 020700     MOVE    "CL-G61"         TO   FD-ACTRDTL-BATCHNO.
        String fdActrdtlBatchno = "CL-G61";
        // 020800     MOVE    0                TO   FD-ACTRDTL-UPDCD.
        int fdActrdtlUpdcd = 0;
        // 020900     MOVE    "VY"             TO   FD-ACTRDTL-VTLRNO.
        String fdActrdtlVtlrno = "VY";

        // 021000     WRITE   FD-ACTRDTL-REC.
        // 01 FD-ACTRDTL-REC TOTAL 249 BYTES
        // 03 FD-ACTRDTL-TXDATE	9(08)	交易日期	WK-DATE
        sb.append(formatUtil.pad9("" + fdActrdtlTxdate, 8));
        // 接收參數-實際入帳日
        // 03 FD-ACTRDTL-KINBR	9(03)	輸入行	003
        sb.append(formatUtil.pad9("" + fdActrdtlKinbr, 3));
        // 03 FD-ACTRDTL-ACBRNO	9(03)	掛帳行	003 or UPDBAF-PBRNO
        sb.append(formatUtil.pad9("" + fdActrdtlAcbrno, 3));
        // 03 FD-ACTRDTL-SECNO	9(02)	業務別（參考共同說明）	31
        sb.append(formatUtil.pad9("" + fdActrdtlSecno, 2));
        // 03 FD-ACTRDTL-ACTYPE	X(01)	帳務現轉別：Ｃ．現金　Ｍ．轉帳　Ｎ．交換（轉帳）	"M"
        sb.append(formatUtil.padX(fdActrdtlActype, 1));
        // 03 FD-ACTRDTL-CRDB	9(01)	借貸別：１   借　２   貸	1 or 2
        sb.append(formatUtil.pad9("" + fdActrdtlCrdb, 1));
        // 03 FD-ACTRDTL-ACTNO		會計帳號	詳程式
        //  05 FD-ACTRDTL-DEPT	9(01)	部別	1
        sb.append(formatUtil.pad9("" + fdActrdtlDept, 1));
        //  05 FD-ACTRDTL-ACNO	X(06)	科目
        sb.append(formatUtil.padX(fdActrdtlAcno, 6));
        //  05 FD-ACTRDTL-SBNO	X(04)	子目
        sb.append(formatUtil.padX(fdActrdtlSbno, 4));
        //  05 FD-ACTRDTL-DTLNO	X(04)	細目
        sb.append(formatUtil.padX(fdActrdtlDtlno, 4));
        // 03 FD-ACTRDTL-CURCD	9(02)	幣別	00
        sb.append(formatUtil.pad9("" + fdActrdtlCurcd, 2));
        // 03 FD-ACTRDTL-TXCNT	9(06)	記帳筆數	1
        sb.append(formatUtil.pad9("" + fdActrdtlTxcnt, 1));
        // 03 FD-ACTRDTL-TXAMT	9(14)V9(02)	記帳金額	UPDBAF-AMT or UPDBAF-IPAL
        sb.append(reportUtil.customFormat("" + fdActrdtlCurcd, "99999999999999.99"));
        // 03 FD-ACTRDTL-MRKEY	X(20)	帳號／交易編號／銷帳帳號	SPACES
        sb.append(formatUtil.padX(fdActrdtlMrkey, 20));
        // 03 FD-ACTRDTL-COKEY	X(20)	對方帳號／交易編號／銷帳編號	SPACES
        sb.append(formatUtil.padX(fdActrdtlCokey, 20));
        // 03 FD-ACTRDTL-SUMNO	9(02)	彙總別	31
        sb.append(formatUtil.pad9("" + fdActrdtlSumno, 2));
        // 03 FD-ACTRDTL-UPDCD	9(01)	需過帳記號	0
        sb.append(formatUtil.pad9("" + fdActrdtlUpdcd, 1));
        // 03 FD-ACTRDTL-VTLRNO	X(02)	指定結帳櫃員	"VY"
        sb.append(formatUtil.padX(fdActrdtlVtlrno, 2));
        // 03 FD-ACTRDTL-BAFGRP		業務科目資訊	SPACES
        //  05 FD-ACTRDTL-APNO	9(03)	科目
        sb.append(formatUtil.pad9("", 3));
        //  05 FD-ACTRDTL-CHARCD	9(02)	性質別
        sb.append(formatUtil.pad9("", 2));
        // 03 FD-ACTRDTL-OTRGRP		其他資訊
        //  05 FD-ACTRDTL-BATCHNO	X(12)	批號	"CL-G61"
        sb.append(formatUtil.padX(fdActrdtlBatchno, 12));
        //  05 FD-ACTRDTL-MEMO	X(80)	註記	SPACES
        sb.append(formatUtil.padX("", 80));
        //  05 FD-ACTRDTL-ENTACC	9(01)		SPACES
        sb.append(formatUtil.pad9("", 1));
        // 03 FD-ACTRDTL-MISDEP	X(06)		SPACES
        sb.append(formatUtil.padX("", 6));
        // 03 FD-ACTRDTL-FILLER	X(43)		SPACES
        sb.append(formatUtil.padX("", 43));

        fileACTRDTLContents.add(formatUtil.padX(sb.toString(), 249));

        // 021100 3100-ACTRDTL-EXIT.
    }

    private void actrdtl_3200() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "actrdtl_3200 ....");
        // 021400 3200-ACTRDTL-RTN.
        // 021500* 營業部（代收行）的聯往貸方
        // 021600     MOVE    SPACES           TO   FD-ACTRDTL-REC.
        sb = new StringBuilder();
        // 021700     MOVE    WK-DATE          TO   FD-ACTRDTL-TXDATE.
        int fdActrdtlTxdate = wkDate;
        // 021800     MOVE    003              TO   FD-ACTRDTL-KINBR.
        int fdActrdtlKinbr = 3;
        // 021900     MOVE    003              TO   FD-ACTRDTL-ACBRNO.
        int fdActrdtlAcbrno = 3;
        // 022000     MOVE    31               TO   FD-ACTRDTL-SECNO.
        int fdActrdtlSecno = 31;
        // 022100     MOVE    "M"              TO   FD-ACTRDTL-ACTYPE.
        String fdActrdtlActype = "M";
        // 022200     MOVE    2                TO   FD-ACTRDTL-CRDB.
        int fdActrdtlCrdb = 2;
        // 022300     MOVE    1                TO   FD-ACTRDTL-DEPT.
        int fdActrdtlDept = 1;
        // 022320*IFRS-2 101/06/06 CSCHEN
        // 022340     MOVE    CN-ACTR-G0001    TO   WK-AC-ACTNO.
        // 022400     MOVE    "419901"         TO   FD-ACTRDTL-ACNO.
        String fdActrdtlAcno = "419901";
        // 022500     MOVE    "5000"           TO   FD-ACTRDTL-SBNO.
        String fdActrdtlSbno = "5000";
        // 022600     MOVE    "0000"           TO   FD-ACTRDTL-DTLNO.
        String fdActrdtlDtlno = "0000";
        // 022650*IFRS-2 End
        // 022700     MOVE    00               TO   FD-ACTRDTL-CURCD.
        int fdActrdtlCurcd = 00;
        // 022800     MOVE    1                TO   FD-ACTRDTL-TXCNT.
        int fdActrdtlTxcnt = 1;
        // 022900     MOVE    UPDBAF-IPAL      TO   FD-ACTRDTL-TXAMT.
        BigDecimal fdActrdtlTxamt = updbafIpal;
        // 023000     MOVE    SPACES           TO   FD-ACTRDTL-MRKEY.
        String fdActrdtlMrkey = "";
        // 023100     MOVE    SPACES           TO   FD-ACTRDTL-COKEY.
        String fdActrdtlCokey = "";
        // 023200     MOVE    31               TO   FD-ACTRDTL-SUMNO.
        int fdActrdtlSumno = 31;
        // 023300     MOVE    "CL-G61"         TO   FD-ACTRDTL-BATCHNO.
        String fdActrdtlBatchno = "CL-G61";
        // 023400     MOVE    0                TO   FD-ACTRDTL-UPDCD.
        int fdActrdtlUpdcd = 0;
        // 023500     MOVE    "VY"             TO   FD-ACTRDTL-VTLRNO.
        String fdActrdtlVtlrno = "VY";

        // 023600     WRITE   FD-ACTRDTL-REC.
        // 01 FD-ACTRDTL-REC TOTAL 249 BYTES
        // 03 FD-ACTRDTL-TXDATE	9(08)	交易日期	WK-DATE
        sb.append(formatUtil.pad9("" + fdActrdtlTxdate, 8));
        // 接收參數-實際入帳日
        // 03 FD-ACTRDTL-KINBR	9(03)	輸入行	003
        sb.append(formatUtil.pad9("" + fdActrdtlKinbr, 3));
        // 03 FD-ACTRDTL-ACBRNO	9(03)	掛帳行	003 or UPDBAF-PBRNO
        sb.append(formatUtil.pad9("" + fdActrdtlAcbrno, 3));
        // 03 FD-ACTRDTL-SECNO	9(02)	業務別（參考共同說明）	31
        sb.append(formatUtil.pad9("" + fdActrdtlSecno, 2));
        // 03 FD-ACTRDTL-ACTYPE	X(01)	帳務現轉別：Ｃ．現金　Ｍ．轉帳　Ｎ．交換（轉帳）	"M"
        sb.append(formatUtil.padX(fdActrdtlActype, 1));
        // 03 FD-ACTRDTL-CRDB	9(01)	借貸別：１   借　２   貸	1 or 2
        sb.append(formatUtil.pad9("" + fdActrdtlCrdb, 1));
        // 03 FD-ACTRDTL-ACTNO		會計帳號	詳程式
        //  05 FD-ACTRDTL-DEPT	9(01)	部別	1
        sb.append(formatUtil.pad9("" + fdActrdtlDept, 1));
        //  05 FD-ACTRDTL-ACNO	X(06)	科目
        sb.append(formatUtil.padX(fdActrdtlAcno, 6));
        //  05 FD-ACTRDTL-SBNO	X(04)	子目
        sb.append(formatUtil.padX(fdActrdtlSbno, 4));
        //  05 FD-ACTRDTL-DTLNO	X(04)	細目
        sb.append(formatUtil.padX(fdActrdtlDtlno, 4));
        // 03 FD-ACTRDTL-CURCD	9(02)	幣別	00
        sb.append(formatUtil.pad9("" + fdActrdtlCurcd, 2));
        // 03 FD-ACTRDTL-TXCNT	9(06)	記帳筆數	1
        sb.append(formatUtil.pad9("" + fdActrdtlTxcnt, 1));
        // 03 FD-ACTRDTL-TXAMT	9(14)V9(02)	記帳金額	UPDBAF-AMT or UPDBAF-IPAL
        sb.append(reportUtil.customFormat("" + fdActrdtlCurcd, "99999999999999.99"));
        // 03 FD-ACTRDTL-MRKEY	X(20)	帳號／交易編號／銷帳帳號	SPACES
        sb.append(formatUtil.padX(fdActrdtlMrkey, 20));
        // 03 FD-ACTRDTL-COKEY	X(20)	對方帳號／交易編號／銷帳編號	SPACES
        sb.append(formatUtil.padX(fdActrdtlCokey, 20));
        // 03 FD-ACTRDTL-SUMNO	9(02)	彙總別	31
        sb.append(formatUtil.pad9("" + fdActrdtlSumno, 2));
        // 03 FD-ACTRDTL-UPDCD	9(01)	需過帳記號	0
        sb.append(formatUtil.pad9("" + fdActrdtlUpdcd, 1));
        // 03 FD-ACTRDTL-VTLRNO	X(02)	指定結帳櫃員	"VY"
        sb.append(formatUtil.padX(fdActrdtlVtlrno, 2));
        // 03 FD-ACTRDTL-BAFGRP		業務科目資訊	SPACES
        //  05 FD-ACTRDTL-APNO	9(03)	科目
        sb.append(formatUtil.pad9("", 3));
        //  05 FD-ACTRDTL-CHARCD	9(02)	性質別
        sb.append(formatUtil.pad9("", 2));
        // 03 FD-ACTRDTL-OTRGRP		其他資訊
        //  05 FD-ACTRDTL-BATCHNO	X(12)	批號	"CL-G61"
        sb.append(formatUtil.padX(fdActrdtlBatchno, 12));
        //  05 FD-ACTRDTL-MEMO	X(80)	註記	SPACES
        sb.append(formatUtil.padX("", 80));
        //  05 FD-ACTRDTL-ENTACC	9(01)		SPACES
        sb.append(formatUtil.pad9("", 1));
        // 03 FD-ACTRDTL-MISDEP	X(06)		SPACES
        sb.append(formatUtil.padX("", 6));
        // 03 FD-ACTRDTL-FILLER	X(43)		SPACES
        sb.append(formatUtil.padX("", 43));

        fileACTRDTLContents.add(formatUtil.padX(sb.toString(), 249));
        // 023620 3200-ACTRDTL-EXIT.
    }

    private void actrdtl_3300() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "actrdtl_3300 ....");
        // 023680 3300-ACTRDTL-RTN.
        // 023700* 主辦行的聯往借方
        // 023800     MOVE    SPACES           TO   FD-ACTRDTL-REC.
        sb = new StringBuilder();
        // 023900     MOVE    WK-DATE          TO   FD-ACTRDTL-TXDATE.
        int fdActrdtlTxdate = wkDate;
        // 024000     MOVE    003              TO   FD-ACTRDTL-KINBR.
        int fdActrdtlKinbr = 3;
        // 024100     MOVE    UPDBAF-PBRNO     TO   FD-ACTRDTL-ACBRNO.
        int fdActrdtlAcbrno = updbafPbrno;
        // 024200     MOVE    31               TO   FD-ACTRDTL-SECNO.
        int fdActrdtlSecno = 31;
        // 024300     MOVE    "M"              TO   FD-ACTRDTL-ACTYPE.
        String fdActrdtlActype = "M";
        // 024400     MOVE    "1"              TO   FD-ACTRDTL-CRDB.
        int fdActrdtlCrdb = 1;
        // 024500     MOVE    1                TO   FD-ACTRDTL-DEPT.
        int fdActrdtlDept = 1;
        // 024520*IFRS-2 101/06/06 CSCHEN
        // 024540     MOVE    CN-ACTR-G0001    TO   WK-AC-ACTNO.
        // 024600     MOVE    "419901"         TO   FD-ACTRDTL-ACNO.
        String fdActrdtlAcno = "419901";
        // 024700     MOVE    "5000"           TO   FD-ACTRDTL-SBNO.
        String fdActrdtlSbno = "5000";
        // 024800     MOVE    "0000"           TO   FD-ACTRDTL-DTLNO.
        String fdActrdtlDtlno = "0000";
        // 024850*IFRS-2 End
        // 024900     MOVE    00               TO   FD-ACTRDTL-CURCD.
        int fdActrdtlCurcd = 00;
        // 025000     MOVE    1                TO   FD-ACTRDTL-TXCNT.
        int fdActrdtlTxcnt = 1;
        // 025100     MOVE    UPDBAF-IPAL      TO   FD-ACTRDTL-TXAMT.
        BigDecimal fdActrdtlTxamt = updbafIpal;
        // 025200     MOVE    SPACES           TO   FD-ACTRDTL-MRKEY.
        String fdActrdtlMrkey = "";
        // 025300     MOVE    SPACES           TO   FD-ACTRDTL-COKEY.
        String fdActrdtlCokey = "";
        // 025400     MOVE    31               TO   FD-ACTRDTL-SUMNO.
        int fdActrdtlSumno = 31;
        // 025500     MOVE    "CL-G61"         TO   FD-ACTRDTL-BATCHNO.
        String fdActrdtlBatchno = "CL-G61";
        // 025600     MOVE    0                TO   FD-ACTRDTL-UPDCD.
        int fdActrdtlUpdcd = 0;
        // 025700     MOVE    "VY"             TO   FD-ACTRDTL-VTLRNO.
        String fdActrdtlVtlrno = "VY";

        // 025800     WRITE   FD-ACTRDTL-REC.
        // 01 FD-ACTRDTL-REC TOTAL 249 BYTES
        // 03 FD-ACTRDTL-TXDATE	9(08)	交易日期	WK-DATE
        sb.append(formatUtil.pad9("" + fdActrdtlTxdate, 8));
        // 接收參數-實際入帳日
        // 03 FD-ACTRDTL-KINBR	9(03)	輸入行	003
        sb.append(formatUtil.pad9("" + fdActrdtlKinbr, 3));
        // 03 FD-ACTRDTL-ACBRNO	9(03)	掛帳行	003 or UPDBAF-PBRNO
        sb.append(formatUtil.pad9("" + fdActrdtlAcbrno, 3));
        // 03 FD-ACTRDTL-SECNO	9(02)	業務別（參考共同說明）	31
        sb.append(formatUtil.pad9("" + fdActrdtlSecno, 2));
        // 03 FD-ACTRDTL-ACTYPE	X(01)	帳務現轉別：Ｃ．現金　Ｍ．轉帳　Ｎ．交換（轉帳）	"M"
        sb.append(formatUtil.padX(fdActrdtlActype, 1));
        // 03 FD-ACTRDTL-CRDB	9(01)	借貸別：１   借　２   貸	1 or 2
        sb.append(formatUtil.pad9("" + fdActrdtlCrdb, 1));
        // 03 FD-ACTRDTL-ACTNO		會計帳號	詳程式
        //  05 FD-ACTRDTL-DEPT	9(01)	部別	1
        sb.append(formatUtil.pad9("" + fdActrdtlDept, 1));
        //  05 FD-ACTRDTL-ACNO	X(06)	科目
        sb.append(formatUtil.padX(fdActrdtlAcno, 6));
        //  05 FD-ACTRDTL-SBNO	X(04)	子目
        sb.append(formatUtil.padX(fdActrdtlSbno, 4));
        //  05 FD-ACTRDTL-DTLNO	X(04)	細目
        sb.append(formatUtil.padX(fdActrdtlDtlno, 4));
        // 03 FD-ACTRDTL-CURCD	9(02)	幣別	00
        sb.append(formatUtil.pad9("" + fdActrdtlCurcd, 2));
        // 03 FD-ACTRDTL-TXCNT	9(06)	記帳筆數	1
        sb.append(formatUtil.pad9("" + fdActrdtlTxcnt, 1));
        // 03 FD-ACTRDTL-TXAMT	9(14)V9(02)	記帳金額	UPDBAF-AMT or UPDBAF-IPAL
        sb.append(reportUtil.customFormat("" + fdActrdtlCurcd, "99999999999999.99"));
        // 03 FD-ACTRDTL-MRKEY	X(20)	帳號／交易編號／銷帳帳號	SPACES
        sb.append(formatUtil.padX(fdActrdtlMrkey, 20));
        // 03 FD-ACTRDTL-COKEY	X(20)	對方帳號／交易編號／銷帳編號	SPACES
        sb.append(formatUtil.padX(fdActrdtlCokey, 20));
        // 03 FD-ACTRDTL-SUMNO	9(02)	彙總別	31
        sb.append(formatUtil.pad9("" + fdActrdtlSumno, 2));
        // 03 FD-ACTRDTL-UPDCD	9(01)	需過帳記號	0
        sb.append(formatUtil.pad9("" + fdActrdtlUpdcd, 1));
        // 03 FD-ACTRDTL-VTLRNO	X(02)	指定結帳櫃員	"VY"
        sb.append(formatUtil.padX(fdActrdtlVtlrno, 2));
        // 03 FD-ACTRDTL-BAFGRP		業務科目資訊	SPACES
        //  05 FD-ACTRDTL-APNO	9(03)	科目
        sb.append(formatUtil.pad9("", 3));
        //  05 FD-ACTRDTL-CHARCD	9(02)	性質別
        sb.append(formatUtil.pad9("", 2));
        // 03 FD-ACTRDTL-OTRGRP		其他資訊
        //  05 FD-ACTRDTL-BATCHNO	X(12)	批號	"CL-G61"
        sb.append(formatUtil.padX(fdActrdtlBatchno, 12));
        //  05 FD-ACTRDTL-MEMO	X(80)	註記	SPACES
        sb.append(formatUtil.padX("", 80));
        //  05 FD-ACTRDTL-ENTACC	9(01)		SPACES
        sb.append(formatUtil.pad9("", 1));
        // 03 FD-ACTRDTL-MISDEP	X(06)		SPACES
        sb.append(formatUtil.padX("", 6));
        // 03 FD-ACTRDTL-FILLER	X(43)		SPACES
        sb.append(formatUtil.padX("", 43));

        fileACTRDTLContents.add(formatUtil.padX(sb.toString(), 249));

        // 025820 3300-ACTRDTL-EXIT.
    }

    private void actrdtl_3400() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "actrdtl_3400 ....");
        // 025880 3400-ACTRDTL-RTN.
        // 025900* 主辦行的聯往貸方
        // 026000     MOVE    SPACES           TO   FD-ACTRDTL-REC.
        sb = new StringBuilder();
        // 026100     MOVE    WK-DATE          TO   FD-ACTRDTL-TXDATE.
        int fdActrdtlTxdate = wkDate;
        // 026200     MOVE    003              TO   FD-ACTRDTL-KINBR.
        int fdActrdtlKinbr = 3;
        // 026300     MOVE    UPDBAF-PBRNO     TO   FD-ACTRDTL-ACBRNO.
        int fdActrdtlAcbrno = updbafPbrno;
        // 026400     MOVE    31               TO   FD-ACTRDTL-SECNO.
        int fdActrdtlSecno = 31;
        // 026500     MOVE    "M"              TO   FD-ACTRDTL-ACTYPE.
        String fdActrdtlActype = "M";
        // 026600     MOVE    2                TO   FD-ACTRDTL-CRDB.
        int fdActrdtlCrdb = 2;
        // 026700     MOVE    1                TO   FD-ACTRDTL-DEPT.
        int fdActrdtlDept = 1;
        // 026720*IFRS-2 101/06/06 CSCHEN
        // 026740     MOVE    CN-ACTR-A190237  TO   WK-AC-ACTNO.
        // 026800     MOVE    "1A0102"         TO   FD-ACTRDTL-ACNO.
        String fdActrdtlAcno = "1A0102";
        // 026900     MOVE    "0037"           TO   FD-ACTRDTL-SBNO.
        String fdActrdtlSbno = "0037";
        // 027000     MOVE    "0000"           TO   FD-ACTRDTL-DTLNO.
        String fdActrdtlDtlno = "0000";
        // 027050*IFRS-2 End
        // 027100     MOVE    00               TO   FD-ACTRDTL-CURCD.
        int fdActrdtlCurcd = 00;
        // 027200     MOVE    1                TO   FD-ACTRDTL-TXCNT.
        int fdActrdtlTxcnt = 1;
        // 027300     MOVE    UPDBAF-IPAL      TO   FD-ACTRDTL-TXAMT.
        BigDecimal fdActrdtlTxamt = updbafIpal;
        // 027400     MOVE    SPACES           TO   FD-ACTRDTL-MRKEY.
        String fdActrdtlMrkey = "";
        // 027500     MOVE    SPACES           TO   FD-ACTRDTL-COKEY.
        String fdActrdtlCokey = "";
        // 027600     MOVE    31               TO   FD-ACTRDTL-SUMNO.
        int fdActrdtlSumno = 31;
        // 027700     MOVE    "CL-G61"         TO   FD-ACTRDTL-BATCHNO.
        String fdActrdtlBatchno = "CL-G61";
        // 027800     MOVE    0                TO   FD-ACTRDTL-UPDCD.
        int fdActrdtlUpdcd = 0;
        // 027900     MOVE    "VY"             TO   FD-ACTRDTL-VTLRNO.
        String fdActrdtlVtlrno = "VY";

        // 028000     WRITE   FD-ACTRDTL-REC.
        // 01 FD-ACTRDTL-REC TOTAL 249 BYTES
        // 03 FD-ACTRDTL-TXDATE	9(08)	交易日期	WK-DATE
        sb.append(formatUtil.pad9("" + fdActrdtlTxdate, 8));
        // 接收參數-實際入帳日
        // 03 FD-ACTRDTL-KINBR	9(03)	輸入行	003
        sb.append(formatUtil.pad9("" + fdActrdtlKinbr, 3));
        // 03 FD-ACTRDTL-ACBRNO	9(03)	掛帳行	003 or UPDBAF-PBRNO
        sb.append(formatUtil.pad9("" + fdActrdtlAcbrno, 3));
        // 03 FD-ACTRDTL-SECNO	9(02)	業務別（參考共同說明）	31
        sb.append(formatUtil.pad9("" + fdActrdtlSecno, 2));
        // 03 FD-ACTRDTL-ACTYPE	X(01)	帳務現轉別：Ｃ．現金　Ｍ．轉帳　Ｎ．交換（轉帳）	"M"
        sb.append(formatUtil.padX(fdActrdtlActype, 1));
        // 03 FD-ACTRDTL-CRDB	9(01)	借貸別：１   借　２   貸	1 or 2
        sb.append(formatUtil.pad9("" + fdActrdtlCrdb, 1));
        // 03 FD-ACTRDTL-ACTNO		會計帳號	詳程式
        //  05 FD-ACTRDTL-DEPT	9(01)	部別	1
        sb.append(formatUtil.pad9("" + fdActrdtlDept, 1));
        //  05 FD-ACTRDTL-ACNO	X(06)	科目
        sb.append(formatUtil.padX(fdActrdtlAcno, 6));
        //  05 FD-ACTRDTL-SBNO	X(04)	子目
        sb.append(formatUtil.padX(fdActrdtlSbno, 4));
        //  05 FD-ACTRDTL-DTLNO	X(04)	細目
        sb.append(formatUtil.padX(fdActrdtlDtlno, 4));
        // 03 FD-ACTRDTL-CURCD	9(02)	幣別	00
        sb.append(formatUtil.pad9("" + fdActrdtlCurcd, 2));
        // 03 FD-ACTRDTL-TXCNT	9(06)	記帳筆數	1
        sb.append(formatUtil.pad9("" + fdActrdtlTxcnt, 1));
        // 03 FD-ACTRDTL-TXAMT	9(14)V9(02)	記帳金額	UPDBAF-AMT or UPDBAF-IPAL
        sb.append(reportUtil.customFormat("" + fdActrdtlCurcd, "99999999999999.99"));
        // 03 FD-ACTRDTL-MRKEY	X(20)	帳號／交易編號／銷帳帳號	SPACES
        sb.append(formatUtil.padX(fdActrdtlMrkey, 20));
        // 03 FD-ACTRDTL-COKEY	X(20)	對方帳號／交易編號／銷帳編號	SPACES
        sb.append(formatUtil.padX(fdActrdtlCokey, 20));
        // 03 FD-ACTRDTL-SUMNO	9(02)	彙總別	31
        sb.append(formatUtil.pad9("" + fdActrdtlSumno, 2));
        // 03 FD-ACTRDTL-UPDCD	9(01)	需過帳記號	0
        sb.append(formatUtil.pad9("" + fdActrdtlUpdcd, 1));
        // 03 FD-ACTRDTL-VTLRNO	X(02)	指定結帳櫃員	"VY"
        sb.append(formatUtil.padX(fdActrdtlVtlrno, 2));
        // 03 FD-ACTRDTL-BAFGRP		業務科目資訊	SPACES
        //  05 FD-ACTRDTL-APNO	9(03)	科目
        sb.append(formatUtil.pad9("", 3));
        //  05 FD-ACTRDTL-CHARCD	9(02)	性質別
        sb.append(formatUtil.pad9("", 2));
        // 03 FD-ACTRDTL-OTRGRP		其他資訊
        //  05 FD-ACTRDTL-BATCHNO	X(12)	批號	"CL-G61"
        sb.append(formatUtil.padX(fdActrdtlBatchno, 12));
        //  05 FD-ACTRDTL-MEMO	X(80)	註記	SPACES
        sb.append(formatUtil.padX("", 80));
        //  05 FD-ACTRDTL-ENTACC	9(01)		SPACES
        sb.append(formatUtil.pad9("", 1));
        // 03 FD-ACTRDTL-MISDEP	X(06)		SPACES
        sb.append(formatUtil.padX("", 6));
        // 03 FD-ACTRDTL-FILLER	X(43)		SPACES
        sb.append(formatUtil.padX("", 43));

        fileACTRDTLContents.add(formatUtil.padX(sb.toString(), 249));

        // 028100 3400-ACTRDTL-EXIT.
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }
}
