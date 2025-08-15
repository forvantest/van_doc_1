/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.KPUTH;
import com.bot.ncl.dto.entities.CldtlbyRangeEntdyOrderbyEntdyBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("KPUTHLsnr")
@Scope("prototype")
public class KPUTHLsnr extends BatchListenerCase<KPUTH> {

    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private CldtlService cldtlService;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Parse parse;
    private KPUTH event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;
    private static final String CHARSET = "UTF-8";
    private static final String FOLDER_OUTPUT_NAME = "ANALY"; // 產檔資料夾
    private static final String FILE_OUTPUT_NAME = "KPUTH."; // 產檔檔名
    private static final String PATH_SEPARATOR = File.separator;
    private static final String CONVF_DATA = "DATA";
    private String outputFilePath; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileKPUTHContents; //  檔案內容
    private String wkTaskYyymm;
    private String wkTempSyyymm;
    private String wkTempEyyymm;
    private String wkSdate;
    private String wkEdate;
    private HashMap<String, Integer> codePbrno = new HashMap<String, Integer>();
    private HashMap<String, String> codeEntpno = new HashMap<String, String>();

    private String kputh2UserData = "";
    private String kputh2Txtype = "";
    private String kputh2Rcptid = "";
    private String kputh2Code = "";
    private int kputh2Date = 0;
    private int kputh2Cllbr = 0;
    private BigDecimal kputh2Amt = BigDecimal.ZERO;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(KPUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "KPUTHLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(KPUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "KPUTHLsnr run");
        // 004100 0000-START-RTN.
        init(event);
        //// 執行0000-MAIN-RTN
        // 004900     PERFORM 0000-MAIN-RTN   THRU   0000-MAIN-EXIT.
        mainRtn();
    }

    private void init(KPUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "KPUTHLsnr init");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        String processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        ;
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 003900 PROCEDURE        DIVISION  USING  WK-TASK-YYYMM.

        // 004000 GENERATED-SECTION SECTION.
        wkTaskYyymm =
                getrocdate(
                        parse.string2Integer(textMap.get("WK_TASK_YYYMM"))); // TODO: 待確認BATCH參數名稱
        // 004100 0000-START-RTN.

        //// 開啟檔案
        // 004200     OPEN INQUIRY BOTSRDB.
        // 004300     OPEN OUTPUT  FD-KPUTH2.
        // 004400     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        fileKPUTHContents = new ArrayList<>();

        //// 設定日期
        // 004500     MOVE    WK-TASK-YYYMM   TO     WK-TEMP-SYYYMM,WK-TEMP-EYYYMM.
        wkTempSyyymm = wkTaskYyymm;
        wkTempEyyymm = wkTaskYyymm;
        // 004600     MOVE    WK-TEMP-SDATE   TO     WK-SDATE.
        wkSdate = wkTempSyyymm + "01";
        // 004700     MOVE    WK-TEMP-EDATE   TO     WK-EDATE.
        wkEdate = wkTempEyyymm + "31";

        //// 設定檔名
        // 004800     CHANGE  ATTRIBUTE FILENAME  OF FD-KPUTH2 TO WK-PUTDIR.
        // DATA/CL/BH/ANALY/KPUTH.
        outputFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FOLDER_OUTPUT_NAME
                        + PATH_SEPARATOR
                        + FILE_OUTPUT_NAME;
        textFile.deleteFile(outputFilePath);
    }

    private void mainRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "KPUTHLsnr mainRtn");
        // 005600 0000-MAIN-RTN.
        //// 將DB-CLDTL-IDX3指標移至開始

        // 005700     SET     DB-CLDTL-IDX3   TO    BEGINNING.
        // 005800 0000-MAIN-LOOP.
        //// 依 代收日(大於等於本月1日,小於等於本月31日) FIND NEXT 收付明細檔
        //// 正常，則執行下一步驟
        //// 若有誤,結束本段程式
        // 005900     FIND NEXT  DB-CLDTL-IDX3 AT DB-CLDTL-DATE  NOT <  WK-SDATE
        // 006000                             AND DB-CLDTL-DATE  NOT >  WK-EDATE
        List<CldtlbyRangeEntdyOrderbyEntdyBus> lClDtl =
                cldtlService.findbyRangeEntdyOrderbyEntdy(
                        parse.string2Integer(wkSdate),
                        parse.string2Integer(wkEdate),
                        0,
                        0,
                        Integer.MAX_VALUE);
        // 006100       ON EXCEPTION GO TO 0000-MAIN-EXIT.
        if (Objects.isNull(lClDtl)) {
            return;
        }
        for (CldtlbyRangeEntdyOrderbyEntdyBus tClDtl : lClDtl) {
            // 006200
            //
            //// 搬相關資料到KPUTH2-REC

            // 006300     MOVE       SPACES            TO      KPUTH2-REC     .
            // 006400     MOVE       DB-CLDTL-CODE     TO      KPUTH2-CODE    .
            kputh2Code = tClDtl.getCode();
            // 006500     MOVE       DB-CLDTL-DATE     TO      KPUTH2-DATE    .
            kputh2Date = tClDtl.getEntdy();
            // 006550     MOVE       DB-CLDTL-RCPTID   TO      KPUTH2-RCPTID  .
            kputh2Rcptid = tClDtl.getRcptid();
            // 006600     MOVE       DB-CLDTL-AMT      TO      KPUTH2-AMT     .
            kputh2Amt = tClDtl.getAmt();
            // 006610* 為避免與外部代收之全家相同，將連線代收之 N 改成 1
            // 006620     IF DB-CLDTL-TXTYPE = "N"
            kputh2Txtype = "";
            if ("N".equals(tClDtl.getTxtype())) {
                // 006640        MOVE    "1"               TO      KPUTH2-TXTYPE
                kputh2Txtype = "1";
            } else {
                // 006660     ELSE
                // 006700        MOVE    DB-CLDTL-TXTYPE   TO      KPUTH2-TXTYPE  .
                kputh2Txtype = tClDtl.getTxtype();
            }
            // 006900     MOVE       DB-CLDTL-USERDATA TO      KPUTH2-USERDATA.
            kputh2UserData = tClDtl.getUserdata();
            // 006950     MOVE       DB-CLDTL-CLLBR    TO      KPUTH2-CLLBR   .
            kputh2Cllbr = tClDtl.getCllbr();

            //// 搬主辦分行,統一編號
            // 007000     PERFORM    1000-CLMR-RTN     THRU    1000-CLMR-EXIT .
            clmr1000Rtn(kputh2Code);
            //// 寫檔FD-KPUTH2
            //
            // 007100     WRITE      KPUTH2-REC.
            // 01 KPUTH2-REC TOTAL 140 BYTES
            //  03  KPUTH2-PUTFILE
            //    05  KPUTH2-PUTTYPE	9(02)
            //    05  KPUTH2-PUTNAME	X(08)
            //  03  KPUTH2-PUTFILE-R1	REDEFINES KPUTH2-PUTFILE
            //    05  KPUTH2-ENTPNO	X(10)	統一編號 IF DB-CLMR-ENTPNO NOT = SPACES then DB-CLMR-ENTPNO
            // ELSE then DB-CLMR-HENTPNO
            //  03  KPUTH2-CODE	X(06)	代收類別	DB-CLDTL-CODE
            //  03  KPUTH2-RCPTID	X(16)	銷帳號碼	DB-CLDTL-RCPTID
            //  03  KPUTH2-DATE	9(07)	代收日	DB-CLDTL-DATE
            //  03  KPUTH2-TIME	9(06)
            //  03  KPUTH2-CLLBR	9(03)	代收行	DB-CLDTL-CLLBR
            //  03  KPUTH2-LMTDATE	9(06)
            //  03  KPUTH2-AMT	9(10)	繳費金額	DB-CLDTL-AMT
            //  03  KPUTH2-USERDATA	X(40)	備註資料	DB-CLDTL-USERDATA
            //  03  KPUTH2-USERDATE-R1	REDEFINES KPUTH2-USERDATA
            //    05 KPUTH2-SMSERNO	X(03)
            //    05 KPUTH2-RETAILNO	X(08)
            //    05 KPUTH2-BARCODE3	X(15)
            //    05 KPUTH2-FILLER	X(14)
            //  03  KPUTH2-SITDATE	9(07)
            //  03  KPUTH2-TXTYPE	X(01)	帳務別 IF DB-CLDTL-TXTYPE = "N"
            // Then "1"
            // ELSE then DB-CLDTL-TXTYPE
            //  03  KPUTH2-SERINO	9(06)
            //  03  KPUTH2-PBRNO	9(03)	主辦分行	DB-CLMR-PBRNO
            //  03  KPUTH2-UPLDATE	9(07)
            //  03  KPUTH2-FEETYPE	9(01)
            //  03  KPUTH2-FEEO2L	9(05)V99
            //  03  FILEER	X(02)
            //  03  FILLER	X(02)

            sb = new StringBuilder();
            sb.append(formatUtil.padX(codeEntpno.get(kputh2Code), 10));
            sb.append(formatUtil.padX(kputh2Code, 6));
            sb.append(formatUtil.padX(kputh2Rcptid, 16));
            sb.append(formatUtil.pad9("" + kputh2Date, 7));
            sb.append(formatUtil.pad9("", 6));
            sb.append(formatUtil.pad9("" + kputh2Cllbr, 3));
            sb.append(formatUtil.pad9("", 6));
            sb.append(formatUtil.pad9("" + kputh2Amt, 10));
            sb.append(formatUtil.padX(kputh2UserData, 40));
            sb.append(formatUtil.pad9("", 7));
            sb.append(formatUtil.padX(kputh2Txtype, 1));
            sb.append(formatUtil.pad9("", 6));
            sb.append(formatUtil.pad9("" + codePbrno.get(kputh2Code), 3));
            sb.append(formatUtil.pad9("", 7));
            sb.append(formatUtil.pad9("", 1));
            sb.append(formatUtil.pad9("", 7));
            sb.append(formatUtil.padX("", 2));
            sb.append(formatUtil.padX("", 2));
            fileKPUTHContents.add(formatUtil.padX(sb.toString(), 140));
            // 007200
            // 007300     GO TO      0000-MAIN-LOOP.
        }
        // 007400 0000-MAIN-EXIT.
        try {
            textFile.writeFileContent(outputFilePath, fileKPUTHContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void clmr1000Rtn(String code) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "KPUTHLsnr clmr1000Rtn");
        // 007700 1000-CLMR-RTN.
        //// 依 代收類別 FIND NEXT 事業單位交易設定檔
        //// 若有誤,DISPLAY "CODE= " DB-CLDTL-CODE

        // 007800     FIND   DB-CLMR-IDX1  AT  DB-CLMR-CODE = DB-CLDTL-CODE
        // 007900         ON EXCEPTION DISPLAY "CODE= " DB-CLDTL-CODE.
        if (!codePbrno.containsKey(code)) {
            ClmrBus tClmr = clmrService.findById(code);
            if (Objects.isNull(tClmr)) {
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CODE= {}", code);
                tClmr = new ClmrBus();
            }
            CltmrBus tCltmr = cltmrService.findById(code);
            if (Objects.isNull(tCltmr)) {
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CODE= {}", code);
                tCltmr = new CltmrBus();
            }
            //// 搬主辦分行,統一編號
            // 008000     MOVE       DB-CLMR-PBRNO     TO      KPUTH2-PBRNO      .
            codePbrno.put(code, tClmr.getPbrno());
            // 008050     IF         DB-CLMR-ENTPNO    NOT = SPACES
            codeEntpno.put(code, "");
            if (tCltmr.getEntpno() != null) {
                if (!tCltmr.getEntpno().isEmpty()) {
                    // 008100       MOVE     DB-CLMR-ENTPNO    TO      KPUTH2-ENTPNO
                    codeEntpno.put(code, tCltmr.getEntpno());
                } else {
                    // 008120     ELSE
                    // 008140       MOVE     DB-CLMR-HENTPNO   TO      KPUTH2-ENTPNO     .
                    codeEntpno.put(code, "" + tCltmr.getHentpno());
                }
            }
        }
        // 008200 1000-CLMR-EXIT.
    }

    private String getrocdate(int dateI) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate inputdate = {}", dateI);

        String date = "" + dateI;
        if (dateI > 19110101) {
            dateI = dateI - 19110000;
        }
        if (String.valueOf(dateI).length() < 7) {
            date = String.format("%07d", dateI);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate outputdate = {}", date);
        return date;
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }
}
