/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C004_RPT;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.Bctl;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C004_RPTLsnr")
@Scope("prototype")
public class C004_RPTLsnr extends BatchListenerCase<C004_RPT> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private ClmrService clmrService;
    @Autowired private Parse parse;
    private C004_RPT event;
    @Autowired private DateUtil dateUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private static final String FILE_NAME = "CL-BH-RPT-C004";
    private List<String> fileRptC004Contents;
    private StringBuilder sb = new StringBuilder();
    private String wkC004dir;
    private String fdDbsctlDir;
    private int wkBrno;
    private int inputRday;
    private int wkRdcnt;
    private int kkk;
    private int wkRptBrno;
    private String wkRptBrnm;
    private String wkCode;
    private Map<String, String> textMap;
    // ----BSCTL----
    private String bsctlAfiller;
    private String bsctlRday1;
    private String bsctlRemark1;
    private String bsctlTxamt;
    private String bsctlUsrdata;
    private String bsctlActno;

    private int rday1;
    private int rptRday1;
    private String rptYYYMMDD;
    private String rptHHMM;
    private int rptCnt;
    private BigDecimal rptTxamt;
    private String rptCode;
    private String rptActno;
    private String rptActname;
    private String rptCname;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C004_RPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C004_RPTLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C004_RPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C004_RPTLsnr run()");
        // 015600 0000-START-RTN.
        init(event);

        // 016400   PERFORM 9999-GETSYSDT-RTN THRU 9999-GETSYSDT-EXIT.
        // 016500   PERFORM ACCOM-GETBCTL-RTN THRU ACCOM-GETBCTL-EXIT.
        // 016600   MOVE 0 TO WK-RDCNT.
        //        _9999_Getsysdt();
        //        accom_Getbctl();
        wkRdcnt = 0;

        // 016700   PERFORM 1000-RDFD-BSCTL-RTN THRU 1000-RDFD-BSCTL-EXIT.
        _1000_Rdfd_Bsctl();

        // 016800 0000-END-RTN.
        // 016900   CLOSE FD-BHDATE.
        // 017000   CLOSE FD-BSCTL.
        // 017100   CLOSE REPORTFL WITH SAVE.
        // 017200   CLOSE BOTSRDB.
        try {
            textFile.writeFileContent(wkC004dir, fileRptC004Contents, CHARSET_BIG5);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 017300   STOP RUN.
    }

    private void init(C004_RPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C004_RPTLsnr init... ");
        // 014500 PROCEDURE               DIVISION  USING WK-BRNO.
        // 015700   OPEN INQUIRY BOTSRDB.
        // 015800   OPEN INPUT   FD-BHDATE.
        // 016000   OPEN INPUT   FD-BSCTL.
        // 016100   OPEN OUTPUT  REPORTFL.
        // 016200   CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        // 016300   READ FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR" STOP RUN.

        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkBrno = parse.string2Integer(textMap.get("KINBR"));
        inputRday = parse.string2Integer(textMap.get("RDAY"));

        // 000600* 2. INPUT  DATA : FD-BSCTL = DATA/BS/DWL/"&RDAY&"/"&BRNO&"/"
        // 000700*                            &DROP(RDAY,3)&"891099/3"
        fdDbsctlDir = fileDir + "BS/DWL/";
        fdDbsctlDir += parse.decimal2String(inputRday, 7, 0) + "_";
        fdDbsctlDir += parse.decimal2String(wkBrno, 3, 0) + "_";
        fdDbsctlDir += parse.decimal2String(inputRday, 4, 0) + "891099_3";

        //        002900  FD REPORTFL
        //        003000     VALUE OF TITLE IS "BD/CL/BH/RPT/C004"
        //        003100     VALUE OF USERBACKUPNAME IS TRUE
        //        003200     SAVEPRINTFILE IS TRUE
        //        003300     SECURITYTYPE IS PUBLIC.
        wkC004dir = fileDir + FILE_NAME;
        fileRptC004Contents = new ArrayList<>();
        textFile.deleteFile(wkC004dir);
    }

    private void getBsctl(String detail) {

        // FD-BSCTL-AFILLER
        // FD-BSCTL-DATA 1-200
        // 02  FD-BSCTL-DETAIL	REDEFINES FD-BSCTL-DATA
        //  03 FD-BSCTL-SEQNO	9(06)	1-7
        //  03 FD-BSCTL-FILLER1	X(04)	7-11
        //  03 FD-BSCTL-ACTNO1	9(16)	11-27
        //  03 FD-BSCTL-ACTNO1R	REDEFINES FD-BSCTL-ACTNO1
        //   05 FD-BSCTL-ACTNO	9(12)	11-23
        //   05 FD-BSCTL-AFILLER	9(04)	23-27
        //   05 FD-BSCTL-AFILLERR	REDEFINES FD-BSCTL-AFILLER
        //    07 FD-BSCTL-AFLR1	X(02) 23-25
        //    07 FD-BSCTL-AFLR2	X(02) 25-27
        //  03 FD-BSCTL-ACTNO2R	REDEFINES FD-BSCTL-ACTNO1
        //   05 FD-BSCTL-ACTNO2	9(14) 11-25
        //   05 FD-BSCTL-ACTNO2V	REDEFINES FD-BSCTL-ACTNO2
        //    07 FD-BSCTL-ACTNO2-4 	9(04) 11-15
        //    07 FD-BSCTL-ACTNO2-10	9(10) 15-25
        //   05 FD-BSCTL-BFILLER    	9(02) 25-27
        //  03 FD-BSCTL-ACTNO3R	REDEFINES FD-BSCTL-ACTNO1
        //   05 FD-BSCTL-ACTNO3	X(16) 11-27
        //  03 FD-BSCTL-ACTNO4R	REDEFINES FD-BSCTL-ACTNO1
        //   05 FD-BSCTL-ACTNO4-1	9(01) 11-12
        //   05 FD-BSCTL-ACTNO4-3	9(03) 12-15
        //   05 FD-BSCTL-ACTNO4-12	9(12) 15-27
        //  03 FD-BSCTL-CRDB	9(01) 27-28
        //  03 FD-BSCTL-TXAMT	9(11)V99 28-41
        //  03 FD-BSCTL-RDAY1	9(07) 41-48
        //  03 FD-BSCTL-ERDAY	9(07) 48-55
        //  03 FD-BSCTL-REMARK1	X(26) 55-81
        //  03 FD-BSCTL-ID	X(10) 81-91
        //  03 FD-BSCTL-STCD2	X(02) 91-93
        //  03 FD-BSCTL-PNO1	X(20) 93-113
        //  03 FD-BSCTL-PNO2	X(20) 113-133
        //  03 FD-BSCTL-USRDATA	X(60) 133-193
        //  03 FD-BSCTL-CHKFG1	9(01) 193-194
        //  03 FD-BSCTL-CHKID	X(01) 194-195
        //  03 FD-BSCTL-FILLER2	X(05) 195-200
        bsctlAfiller = detail.substring(23, 27);
        // FD-BSCTL-RDAY1
        bsctlRday1 = detail.substring(41, 48);
        // FD-BSCTL-REMARK1
        bsctlRemark1 = detail.substring(55, 81);
        // FD-BSCTL-TXAMT
        bsctlTxamt = detail.substring(28, 39) + "." + detail.substring(39, 41);
        // FD-BSCTL-USRDATA
        bsctlUsrdata = detail.substring(133, 193);
        // FD-BSCTL-ACTNO
        bsctlActno = detail.substring(11, 23);
    }

    private void _1000_Rdfd_Bsctl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C004_RPTLsnr _1000_Rdfd_Bsctl... ");
        // 017500 1000-RDFD-BSCTL-RTN.
        // 017600   READ FD-BSCTL AT END GO TO 1000-RDFD-BSCTL-EXIT.
        List<String> lines = textFile.readFileContent(fdDbsctlDir, CHARSET);
        for (String detail : lines) {
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "C004_RPTLsnr detail = {}", detail);

            // 017700   ADD 1 TO WK-RDCNT.
            wkRdcnt++;
            getBsctl(detail);
            // 017800* 第一筆為首筆
            // 017900   IF WK-RDCNT = 1
            if (wkRdcnt == 1) {
                // 018000      GO TO 1000-RDFD-BSCTL-RTN.
                continue;
            }
            // 018100* 若不為會計科目才會出報表
            // 018200   IF FD-BSCTL-AFILLER = SPACES
            if (bsctlAfiller.trim().isEmpty()) {
                // 018300      MOVE SPACES TO REPORTFL-REC
                // 018400* 搬分行
                // 018500      MOVE WK-BRNO      TO    WK-RPT-BRNO
                // 018600      MOVE WK-BRNO      TO    KKK
                // 018700      MOVE LIB-BCTL-CHNAM(KKK) TO  WK-RPT-BRNM
                wkRptBrno = wkBrno;
                kkk = wkBrno;
                Bctl bctl;
                bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(kkk);
                if (bctl == null) {
                    wkRptBrnm = bctl.getChnam();
                }

                // 018800* 搬列印時間
                // 018900      MOVE WK-CHST-YYYY TO RPT-YYY
                // 019000      MOVE WK-CHST-MM   TO RPT-MM
                // 019100      MOVE WK-CHST-DD   TO RPT-DD
                // 019200      MOVE WK-CHST-HR   TO RPT-HR
                // 019300      MOVE WK-CHST-MIN  TO RPT-MIN

                rptYYYMMDD = dateUtil.getNowStringRoc();
                rptHHMM = dateUtil.getNowStringTime(false).substring(0, 4);

                // 019400* 搬入帳日期
                // 019500      MOVE 0 TO WK-RDAY1
                // 019600      MOVE FD-BSCTL-RDAY1 TO WK-RDAY1
                // 019700      MOVE WK-RDAY1-YYY TO RPT-RDAY1-YYY
                // 019800      MOVE WK-RDAY1-MM TO RPT-RDAY1-MM
                // 019900      MOVE WK-RDAY1-DD TO RPT-RDAY1-DD
                rday1 = 0;
                rday1 = parse.string2Integer(bsctlRday1);
                rptRday1 = rday1;

                // 020000* 筆數靠左對齊
                // 020100      MOVE FD-BSCTL-REMARK1(11:6) TO WK-CNT
                // 020200      MOVE 0 TO COUNTER WK-A WK-B
                // 020300      INSPECT WK-CNT TALLYING COUNTER FOR LEADING SPACE
                // 020400      COMPUTE WK-A = COUNTER + 1
                // 020500      COMPUTE WK-B = 7 - COUNTER
                // 020600      MOVE WK-CNT(WK-A:WK-B) TO RPT-CNT
                rptCnt = parse.string2Integer(bsctlRemark1.substring(10, 16));

                // 020700* 金額靠左對齊
                // 020800      MOVE FD-BSCTL-TXAMT TO WK-TXAMT
                // 020900      MOVE 0 TO COUNTER WK-A WK-B
                // 021000      INSPECT WK-TXAMT TALLYING COUNTER FOR LEADING SPACE
                // 021100      COMPUTE WK-A = COUNTER + 1
                // 021200      COMPUTE WK-B = 16 - COUNTER
                // 021300      MOVE WK-TXAMT(WK-A:WK-B) TO RPT-TXAMT
                rptTxamt = parse.string2BigDecimal(bsctlTxamt);

                // 021400* 搬代收類別及入帳帳號
                // 021500      MOVE FD-BSCTL-USRDATA(8:6) TO WK-CODE RPT-CODE              05/10/24
                // 021600      MOVE FD-BSCTL-ACTNO TO RPT-ACTNO
                wkCode = bsctlUsrdata.substring(7, 13);
                rptCode = bsctlUsrdata.substring(7, 13);
                rptActno = bsctlActno;
                // 021700      PERFORM 2000-FNDB-CIFACT-RTN THRU 2000-FNDB-CIFACT-EXIT
                _2000_Fndb_Cifact();
                // 021800      PERFORM 3000-FNDB-CLMR-RTN THRU 3000-FNDB-CLMR-EXIT
                _3000_Fndb_Clmr();
                // 021900      PERFORM 4000-RPT-RTN THRU 4000-RPT-EXIT
                _4000_Rpt();
                // 022000   END-IF.
            }
            // 022100   GO TO 1000-RDFD-BSCTL-RTN.
        }
        // 022200 1000-RDFD-BSCTL-EXIT.
    }

    private void _2000_Fndb_Cifact() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "C004_RPTLsnr _2000_Fndb_Cifact... ");
        // 022500 2000-FNDB-CIFACT-RTN.
        Boolean notfound = true;
        // 022600   SET DB-CIFACT-IDX1 OF DB-CIFACT-DDS TO BEGINNING.
        // 022700   FIND      DB-CIFACT-IDX1 OF DB-CIFACT-DDS
        // 022800          AT DB-CIFACT-ACTNO = FD-BSCTL-ACTNO
        // 022900          ON EXCEPTION
        // 023000          IF DMSTATUS(NOTFOUND)
        if (notfound) {

            // 023100             MOVE " 此帳號未存在歸戶檔裡，故找不到戶名 " TO
            // 023200                                              RPT-ACTNAME
            // 023300             GO TO 2000-FNDB-CIFACT-EXIT
            //            rptActname = " 此帳號未存在歸戶檔裡，故找不到戶名 ";
            rptActname = " "; // 2024-08-30 Wei 修改 from 思樺&展嘉: 找不到時擺空白
            return;
        } else {
            // 023400          ELSE
            // 023500             MOVE SPACES             TO WC-ERRMSG-REC
            // 023600             MOVE "MNTCLMR"          TO WC-ERRMSG-MAIN
            // 023700             MOVE "FNCIFKEY"         TO WC-ERRMSG-SUB
            // 023800             MOVE 2                  TO WC-ERRMSG-TXRSUT
            // 023900             PERFORM DB99-DMERROR-RTN THRU DB99-DMERROR-EXIT
            // 024000             GO TO 2000-FNDB-CIFACT-EXIT
            // 024100          END-IF.
        }
        // 024200   MOVE DB-CIFACT-CIFKEY TO WK-CIFKEY.
        // 024300   IF WK-CIFKEY(1:3) = "OBU"
        // 024400      PERFORM 2300-FNDB-FCIF-RTN THRU 2300-FNDB-FCIF-EXIT
        // 024500   ELSE IF DB-CIFACT-FLAG = 1
        // 024600      PERFORM 2100-FNDB-CIFCMP-RTN THRU 2100-FNDB-FNCIFCMP-EXIT
        // 024700   ELSE
        // 024800      PERFORM 2200-FNDB-CIFPSN-RTN THRU 2200-FNDB-CIFPSN-EXIT
        // 024900   END-IF.
        // 025000 2000-FNDB-CIFACT-EXIT.
    }

    private void _3000_Fndb_Clmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C004_RPTLsnr _3000_Fndb_Clmr... ");
        // 031900 3000-FNDB-CLMR-RTN.
        // 032000   SET DB-CLMR-IDX1 OF DB-CLMR-DDS TO BEGINNING.
        // 032100   FIND      DB-CLMR-IDX1 OF DB-CLMR-DDS
        // 032200          AT DB-CLMR-CODE = WK-CODE
        ClmrBus tClmr = clmrService.findById(wkCode);
        // 032300          ON EXCEPTION
        // 032400          IF DMSTATUS(NOTFOUND)
        if (Objects.isNull(tClmr)) {
            // 032500             MOVE " 代收主檔找不到此代收類別： " TO RPT-CNAME
            // 032600             GO TO 3000-FNDB-CLMR-EXIT
            rptCname = " 代收主檔找不到此代收類別： ";
            // 032700          ELSE
            // 032800             MOVE SPACES             TO WC-ERRMSG-REC
            // 032900             MOVE "MNTCLMR"          TO WC-ERRMSG-MAIN
            // 033000             MOVE "FNCLMR"           TO WC-ERRMSG-SUB
            // 033100             MOVE 2                  TO WC-ERRMSG-TXRSUT
            // 033200             PERFORM DB99-DMERROR-RTN THRU DB99-DMERROR-EXIT
            // 033300             GO TO 3000-FNDB-CLMR-EXIT
            // 033400          END-IF.
        }
        // 033500   MOVE DB-CLMR-CNAME TO RPT-CNAME.
        rptCname = tClmr.getCname();
        // 033600 3000-FNDB-CLMR-EXIT.
    }

    private void _4000_Rpt() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C004_RPTLsnr _4000_Rpt... ");
        // 033900 4000-RPT-RTN.

        // 034000     WRITE REPORTFL-REC   FROM RPT-1-REC AFTER PAGE.
        rpt1Rec();

        // 034100     WRITE REPORTFL-REC   FROM RPT-2-REC AFTER 2.
        rpt2Rec();

        // 034200     WRITE REPORTFL-REC   FROM RPT-3-REC AFTER 2.
        rpt3Rec();

        // 034300     WRITE REPORTFL-REC   FROM RPT-4-REC AFTER 2.
        rpt4Rec();

        // 034500     WRITE REPORTFL-REC   FROM RPT-6-REC AFTER 2.
        rpt6Rec();

        // 034600     WRITE REPORTFL-REC   FROM RPT-7-REC AFTER 2.
        rpt7Rec();

        // 034700     WRITE REPORTFL-REC   FROM RPT-8-REC AFTER 2.
        rpt8Rec();

        // 034800     WRITE REPORTFL-REC   FROM RPT-9-REC AFTER 2.
        rpt9Rec();

        // 034900     WRITE REPORTFL-REC   FROM RPT-10-REC AFTER 2.
        rpt10Rec();

        // 035000     WRITE REPORTFL-REC   FROM RPT-11-REC AFTER 3.
        rpt11Rec();

        // 035100 4000-RPT-EXIT.
    }

    private void rpt1Rec() {
        //        fileRptC004Contents.add(PAGE_SEPARATOR);
        // 007800 01 RPT-1-REC.
        // 007900    03 FILLER            PIC X(12) VALUE SPACES.
        // 008000    03 RPT-HEAD1-TITLE   PIC X(40) VALUE
        // 008100        " 臺　灣　銀　行　彙　總　入　帳　通　知 ".
        sb = new StringBuilder();
        sb.append(PAGE_SEPARATOR);
        sb.append(formatUtil.padX("", 12));
        sb.append(formatUtil.padX(" 臺　灣　銀　行　彙　總　入　帳　通　知 ", 40));
        fileRptC004Contents.add(sb.toString());
    }

    private void rpt2Rec() {
        fileRptC004Contents.add("");
        // 008200 01 RPT-2-REC.
        // 008250    03 FILLER            PIC X(40) VALUE SPACES.
        // 008300    03 FILLER            PIC X(12) VALUE " 分行別　： ".
        // 008400    03  WK-RPT-ITEM3     PIC X(13).
        // 008500    03  WK-RPT-ITEM3R    REDEFINES  WK-RPT-ITEM3.
        // 008600        05  WK-RPT-BRNO  PIC 9(03).
        // 008700        05  WK-RPT-BRNM  PIC X(10).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 40));
        sb.append(formatUtil.padX(" 分行別　： ", 12));
        sb.append(formatUtil.pad9("" + wkRptBrno, 3));
        sb.append(formatUtil.padX(wkRptBrnm, 10));
        fileRptC004Contents.add(sb.toString());
    }

    private void rpt3Rec() {
        fileRptC004Contents.add("");
        // 008800 01 RPT-3-REC.
        // 008850    03 FILLER            PIC X(40) VALUE SPACES.
        // 008900    03 FILLER            PIC X(12) VALUE " 列印時間： ".
        // 009000    03 RPT-YYY           PIC ZZ9.
        // 009100    03 FILLER            PIC X(01) VALUE "/".
        // 009200    03 RPT-MM            PIC 99.
        // 009300    03 FILLER            PIC X(01) VALUE "/".
        // 009400    03 RPT-DD            PIC 99.
        // 009500    03 FILLER            PIC X(01) VALUE SPACE.
        // 009600    03 RPT-HR            PIC 9(02).
        // 009700    03 FILLER            PIC X(01) VALUE ":".
        // 009800    03 RPT-MIN           PIC 9(02).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 40));
        sb.append(formatUtil.padX(" 列印時間： ", 12));
        sb.append(reportUtil.customFormat(rptYYYMMDD, "ZZ9/99/99"));
        sb.append(formatUtil.padX("", 1));
        sb.append(reportUtil.customFormat(rptHHMM, "99:99"));
        fileRptC004Contents.add(sb.toString());
    }

    private void rpt4Rec() {
        fileRptC004Contents.add("");
        // 009900 01 RPT-4-REC.
        // 010000    03 FILLER            PIC X(08) VALUE SPACES.
        // 010100    03 FILLER            PIC X(18) VALUE " 代　收　類　別： ".
        // 010200    03 FILLER            PIC X(01) VALUE SPACES.
        // 010300    03 RPT-CODE          PIC X(06).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 代　收　類　別： ", 18));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(rptCode, 6));
        fileRptC004Contents.add(sb.toString());
    }

    private void rpt6Rec() {
        fileRptC004Contents.add("");
        // 010800 01 RPT-6-REC.
        // 010900    03 FILLER            PIC X(08) VALUE SPACES.
        // 011000    03 FILLER            PIC X(18) VALUE " 入　帳　日　期： ".
        // 011100    03 FILLER            PIC X(01) VALUE SPACES.
        // 011200    03 RPT-RDAY1-YYY     PIC ZZ9.
        // 011300    03 FILLER            PIC X(01) VALUE "/".
        // 011400    03 RPT-RDAY1-MM      PIC 99.
        // 011500    03 FILLER            PIC X(01) VALUE "/".
        // 011600    03 RPT-RDAY1-DD      PIC 99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 入　帳　日　期： ", 18));
        sb.append(formatUtil.padX("", 1));
        sb.append(reportUtil.customFormat("" + rptRday1, "Z99/99/99"));
        fileRptC004Contents.add(sb.toString());
    }

    private void rpt7Rec() {
        fileRptC004Contents.add("");
        // 011700 01 RPT-7-REC.
        // 011800    03 FILLER            PIC X(08) VALUE SPACES.
        // 011900    03 FILLER            PIC X(18) VALUE " 入　帳　帳　號： ".
        // 012000    03 FILLER            PIC X(01) VALUE SPACES.
        // 012100    03 RPT-ACTNO         PIC 9(12).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 入　帳　帳　號： ", 18));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.pad9(rptActno, 12));
        fileRptC004Contents.add(sb.toString());
    }

    private void rpt8Rec() {
        fileRptC004Contents.add("");
        // 012200 01 RPT-8-REC.
        // 012300    03 FILLER            PIC X(08) VALUE SPACES.
        // 012400    03 FILLER            PIC X(18) VALUE " 入　帳　戶　名： ".
        // 012500    03 RPT-ACTNAME       PIC X(54).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 入　帳　戶　名： ", 18));
        sb.append(formatUtil.padX(rptActname, 54));
        fileRptC004Contents.add(sb.toString());
    }

    private void rpt9Rec() {
        fileRptC004Contents.add("");
        // 012600 01 RPT-9-REC.
        // 012700    03 FILLER            PIC X(08) VALUE SPACES.
        // 012800    03 FILLER            PIC X(18) VALUE " 總　　筆　　數： ".
        // 012900    03 FILLER            PIC X(01) VALUE SPACES.
        // 013000    03 RPT-CNT           PIC X(07).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 總　　筆　　數： ", 18));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("" + rptCnt, 7));
        fileRptC004Contents.add(sb.toString());
    }

    private void rpt10Rec() {
        fileRptC004Contents.add("");
        // 013100 01 RPT-10-REC.
        // 013200    03 FILLER            PIC X(08) VALUE SPACES.
        // 013300    03 FILLER            PIC X(18) VALUE " 總　　金　　額： ".
        // 013400    03 FILLER            PIC X(01) VALUE SPACES.
        // 013500    03 RPT-TXAMT         PIC X(16).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 總　　金　　額： ", 18));
        sb.append(formatUtil.padX("", 1));
        sb.append(
                formatUtil.padX(
                        "$" + reportUtil.customFormat("" + rptTxamt, "Z,ZZZ,ZZZ,ZZ9.99").trim(),
                        17));
        fileRptC004Contents.add(sb.toString());
    }

    private void rpt11Rec() {
        fileRptC004Contents.add("");
        fileRptC004Contents.add("");
        // 013600 01 RPT-11-REC.
        // 013700    03 FILLER            PIC X(08) VALUE SPACES.
        // 013800    03 FILLER            PIC X(06) VALUE " 經辦 ".                10/02/09
        // 013900    03 FILLER            PIC X(12) VALUE SPACES.
        // 014000    03 FILLER            PIC X(06) VALUE " 主辦 ".                10/02/09
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 經辦 ", 6));
        sb.append(formatUtil.padX("", 12));
        sb.append(formatUtil.padX(" 主辦 ", 6));
        fileRptC004Contents.add(sb.toString());
    }

    private void moveErrorResponse(LogicException e) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "moveErrorResponse... ");

        // this.event.setPeripheryRequest();
    }
}
