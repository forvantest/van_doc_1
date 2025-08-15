/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.STAT6;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.Bctl;
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
@Component("STAT6Lsnr")
@Scope("prototype")
public class STAT6Lsnr extends BatchListenerCase<STAT6> {
    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFileSTAT6;
    @Autowired private Parse parse;
    @Autowired private CldtlService cldtlService;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmrService clmrService;
    // 批次日期
    private String batchDate;
    int wkSDate = 0;
    int wkEDate = 0;
    private STAT6 event;

    private static final String CHARSET = "Big5";
    private String fileNameSTAT6 = "STAT6";
    private static final String FOLDER_OUTPUT_NAME = "ANALY"; // 產檔資料夾
    private String fileNameKPUTH = "KPUTH";
    private String fileNameTmp = "tmpKPUTH";
    @Autowired private ExternalSortUtil externalSortUtil;
    private String readFilePath = "";
    private StringBuilder sb = new StringBuilder();
    private List<String> fileContentsSTAT6;

    private List<String> lines = new ArrayList<String>();

    String wkSTAT6Code = "";
    String wkSTAT6CName = "";
    int wkSTAT6Pbrno = 0;
    int wkSTAT6Appdt = 0;
    String wkSTAT6PutFile = "";
    String wkSTAT6PutName = "";
    String wkSTAT6EntpNo = "";
    String wkSTAT6PbrName = "";
    String wkSTAT6Brno = "";
    int wkSTAT6_C_Cnt = 0;
    int wkSTAT6_M_Cnt = 0;
    BigDecimal wkSTAT6_C_Amt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_M_Amt = BigDecimal.ZERO;
    int wkSTAT6_R_Cnt = 0;
    int wkSTAT6_I_Cnt = 0;
    BigDecimal wkSTAT6_R_Amt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_I_Amt = BigDecimal.ZERO;
    int wkSTAT6_V_Cnt = 0;
    int wkSTAT6_A_Cnt = 0;
    BigDecimal wkSTAT6_V_Amt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_A_Amt = BigDecimal.ZERO;
    int wkSTAT6_E_Cnt = 0;
    int wkSTAT6_F_Cnt = 0;
    BigDecimal wkSTAT6_E_Amt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_F_Amt = BigDecimal.ZERO;
    int wkSTAT6_H_Cnt = 0;
    int wkSTAT6_J_Cnt = 0;
    BigDecimal wkSTAT6_H_Amt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_J_Amt = BigDecimal.ZERO;
    int wkSTAT6_K_Cnt = 0;
    int wkSTAT6_N_Cnt = 0;
    BigDecimal wkSTAT6_K_Amt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_N_Amt = BigDecimal.ZERO;
    int wkSTAT6_O_Cnt = 0;
    int wkSTAT6_L_Cnt = 0;
    BigDecimal wkSTAT6_O_Amt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_L_Amt = BigDecimal.ZERO;
    int wkSTAT6_U_Cnt = 0;
    int wkSTAT6_T_Cnt = 0;
    BigDecimal wkSTAT6_U_Amt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_T_Amt = BigDecimal.ZERO;
    int wkSTAT6_Q_Cnt = 0;
    int wkSTAT6_X_Cnt = 0;
    BigDecimal wkSTAT6_Q_Amt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_X_Amt = BigDecimal.ZERO;
    int wkSTAT6_Z_Cnt = 0;
    int wkSTAT6_2_Cnt = 0;
    BigDecimal wkSTAT6_Z_Amt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_2_Amt = BigDecimal.ZERO;
    int wkSTAT6_3_Cnt = 0;
    BigDecimal wkSTAT6_3_Amt = BigDecimal.ZERO;
    int wkSTAT6_4_Cnt = 0;
    BigDecimal wkSTAT6_4_Amt = BigDecimal.ZERO;
    int wkSTAT6_5_Cnt = 0;
    BigDecimal wkSTAT6_5_Amt = BigDecimal.ZERO;
    int wkSTAT6_6_Cnt = 0;
    BigDecimal wkSTAT6_6_Amt = BigDecimal.ZERO;
    int wkSTAT6_7_Cnt = 0;
    BigDecimal wkSTAT6_7_Amt = BigDecimal.ZERO;
    int wkSTAT6_8_Cnt = 0;
    BigDecimal wkSTAT6_8_Amt = BigDecimal.ZERO;
    int wkSTAT6_EntpNo = 0;

    int wkSTAT6_SubCnt = 0;
    BigDecimal wkSTAT6_SubAmt = BigDecimal.ZERO;
    String wkSTAT6_SmserNo = "";
    int wkSTAT6_C_TotCnt = 0;
    int wkSTAT6_M_TotCnt = 0;
    BigDecimal wkSTAT6_C_TotAmt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_M_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_R_TotCnt = 0;
    int wkSTAT6_I_TotCnt = 0;
    BigDecimal wkSTAT6_R_TotAmt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_I_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_V_TotCnt = 0;
    int wkSTAT6_A_TotCnt = 0;
    BigDecimal wkSTAT6_V_TotAmt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_A_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_E_TotCnt = 0;
    int wkSTAT6_F_TotCnt = 0;
    BigDecimal wkSTAT6_E_TotAmt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_F_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_H_TotCnt = 0;
    int wkSTAT6_J_TotCnt = 0;
    BigDecimal wkSTAT6_H_TotAmt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_J_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_K_TotCnt = 0;
    int wkSTAT6_N_TotCnt = 0;
    BigDecimal wkSTAT6_K_TotAmt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_N_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_O_TotCnt = 0;
    int wkSTAT6_L_TotCnt = 0;
    BigDecimal wkSTAT6_O_TotAmt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_L_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_U_TotCnt = 0;
    int wkSTAT6_T_TotCnt = 0;
    BigDecimal wkSTAT6_U_TotAmt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_T_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_Q_TotCnt = 0;
    int wkSTAT6_X_TotCnt = 0;
    BigDecimal wkSTAT6_Q_TotAmt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_X_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_Z_TotCnt = 0;
    int wkSTAT6_2_TotCnt = 0;
    BigDecimal wkSTAT6_Z_TotAmt = BigDecimal.ZERO;
    BigDecimal wkSTAT6_2_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_3_TotCnt = 0;
    BigDecimal wkSTAT6_3_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_4_TotCnt = 0;
    BigDecimal wkSTAT6_4_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_5_TotCnt = 0;
    BigDecimal wkSTAT6_5_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_6_TotCnt = 0;
    BigDecimal wkSTAT6_6_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_7_TotCnt = 0;
    BigDecimal wkSTAT6_7_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_8_TotCnt = 0;
    BigDecimal wkSTAT6_8_TotAmt = BigDecimal.ZERO;
    int wkSTAT6_TotCnt = 0;
    BigDecimal wkSTAT6_TotAmt = BigDecimal.ZERO;

    String wkRpt017Code = "";

    String wkRpt017PreCode = "";
    BigDecimal kputh1Amt = BigDecimal.ZERO;
    String kputh1Txtype = "";
    String kputh1SmserNo = "";

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(STAT6 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT6Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(STAT6 event) {
        this.event = event;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "STAT6Lsnr run()");
        readFilePath = fileDir + FOLDER_OUTPUT_NAME + "/" + fileNameKPUTH;

        fileNameSTAT6 = fileDir + FOLDER_OUTPUT_NAME + "/" + fileNameSTAT6;
        sortFile();
        lines = textFileSTAT6.readFileContent(fileDir + fileNameTmp, "UTF-8");

        if (Objects.isNull(lines)) {
            return;
        }

        fileContentsSTAT6 = new ArrayList<String>();
        _0000_MAIN(lines);

        writeFile();
    }

    private void sortFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC016File sortfile");
        File tmpFile = new File(readFilePath);
        File tmpFileOut = new File(fileDir + fileNameTmp);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(10, 6, SortBy.ASC));

        externalSortUtil.sortingFile(tmpFile, tmpFileOut, keyRanges, "UTF-8");
    }

    private void _0000_MAIN(List<String> lines) {

        for (String r : lines) {

            kputh1Amt = new BigDecimal(r.substring(54, 64));
            kputh1Txtype = r.substring(111, 112);
            kputh1SmserNo = r.substring(64, 67).trim().isEmpty() ? "" : r.substring(64, 67);

            // 025500     MOVE  KPUTH1-CODE           TO     WK-RPT017-CODE .
            wkRpt017Code = r.substring(10, 16);
            // 025600
            // 025610* 綜所稅退稅憑單與汽燃費退費憑單為公庫部代付業務，電金部不統計
            // 025620     IF    WK-RPT017-CODE        =      "121444"
            // 025630     OR    WK-RPT017-CODE        =      "121454"
            // 025640        GO TO   0000-MAIN-LOOP.
            if ("121444".equals(wkRpt017Code) || "121454".equals(wkRpt017Code)) {
                continue;
            }
            // 025650
            // 025700     IF    WK-RPT017-PRECODE     =      SPACES
            // 025800        PERFORM 1200-MRINIT-RTN  THRU   1200-MRINIT-EXIT
            // 025900        PERFORM 1000-MRDTL-RTN   THRU   1000-MRDTL-EXIT
            // 026000        PERFORM 2200-DTLCNT-RTN  THRU   2200-DTLCNT-EXIT
            // 026100        MOVE    KPUTH1-CODE      TO     WK-RPT017-PRECODE
            // 026200        GO TO   0000-MAIN-LOOP.
            if ("".equals(wkRpt017PreCode)) {
                _1200_MRINIT();
                _1000_MRDTL(wkRpt017Code);
                _2200_DTLCNT(kputh1Txtype, kputh1Amt, kputh1SmserNo);
                wkRpt017PreCode = wkRpt017Code;
            } else
            // 026300
            // 026400     IF    ( WK-RPT017-PRECODE   NOT =  KPUTH1-CODE  )
            // 026500        PERFORM 1100-MRWIT-RTN   THRU   1100-MRWIT-EXIT
            // 026600        WRITE   STAT6-REC        FROM   WK-STAT6-REC
            // 026700        PERFORM 1200-MRINIT-RTN  THRU   1200-MRINIT-EXIT
            // 026800        PERFORM 1000-MRDTL-RTN   THRU   1000-MRDTL-EXIT
            // 026900        PERFORM 2200-DTLCNT-RTN  THRU   2200-DTLCNT-EXIT
            // 027000        MOVE    KPUTH1-CODE      TO     WK-RPT017-PRECODE
            // 027100        GO TO   0000-MAIN-LOOP.
            if (!r.substring(10, 16).equals(wkRpt017PreCode)) {
                _1100_MRWIT();
                _WRITE_STAT6_REC();

                _1200_MRINIT();
                _1000_MRDTL(wkRpt017Code);
                _2200_DTLCNT(kputh1Txtype, kputh1Amt, kputh1SmserNo);
                wkRpt017PreCode = r.substring(10, 16);
            } else
            // 027200
            // 027300     IF    WK-RPT017-PRECODE     =      KPUTH1-CODE
            // 027400        PERFORM 2200-DTLCNT-RTN  THRU   2200-DTLCNT-EXIT
            // 027500        MOVE    KPUTH1-CODE      TO     WK-RPT017-PRECODE
            // 027600        GO TO   0000-MAIN-LOOP.
            if (r.substring(10, 16).equals(wkRpt017PreCode)) {
                _2200_DTLCNT(kputh1Txtype, kputh1Amt, kputh1SmserNo);
                wkRpt017PreCode = r.substring(10, 16);
            }
        }
        _1100_MRWIT();
        _WRITE_STAT6_REC();
        _WRITE_STAT6_TOTREC();
    }

    private void _1200_MRINIT() {

        // 044300 1200-MRINIT-RTN.
        //
        //// 清變數
        //
        // 044400     MOVE   LOW-VALUE TO  WK-STAT6-C-CNT ,WK-STAT6-M-CNT   ,
        // 044500                          WK-STAT6-C-AMT ,WK-STAT6-M-AMT   ,
        // 044600                          WK-STAT6-R-CNT ,WK-STAT6-I-CNT   ,
        // 044700                          WK-STAT6-R-AMT ,WK-STAT6-I-AMT   ,
        // 044800                          WK-STAT6-V-CNT ,WK-STAT6-A-CNT   ,
        // 044900                          WK-STAT6-V-AMT ,WK-STAT6-A-AMT   ,
        // 045000                          WK-STAT6-E-CNT ,WK-STAT6-F-CNT   ,
        // 045100                          WK-STAT6-E-AMT ,WK-STAT6-F-AMT   ,
        // 045200                          WK-STAT6-H-CNT ,WK-STAT6-J-CNT   ,
        // 045300                          WK-STAT6-H-AMT ,WK-STAT6-J-AMT   ,
        // 045400                          WK-STAT6-K-CNT ,WK-STAT6-N-CNT   ,
        // 045500                          WK-STAT6-K-AMT ,WK-STAT6-N-AMT   ,
        // 045600                          WK-STAT6-O-CNT ,WK-STAT6-L-CNT   ,
        // 045700                          WK-STAT6-O-AMT ,WK-STAT6-L-AMT   ,
        // 045800                          WK-STAT6-U-CNT ,WK-STAT6-T-CNT   ,
        // 045900                          WK-STAT6-U-AMT ,WK-STAT6-T-AMT   ,
        // 046000                          WK-STAT6-Q-CNT ,WK-STAT6-X-CNT   ,
        // 046100                          WK-STAT6-Q-AMT ,WK-STAT6-X-AMT   ,
        // 046120                          WK-STAT6-Z-CNT ,WK-STAT6-2-CNT   ,
        // 046150                          WK-STAT6-Z-AMT ,WK-STAT6-2-AMT   ,
        // 046170                          WK-STAT6-3-CNT ,WK-STAT6-3-AMT   ,
        // 046180                          WK-STAT6-4-CNT ,WK-STAT6-4-AMT   ,
        // 046182                          WK-STAT6-5-CNT ,WK-STAT6-5-AMT   ,
        // 046184                          WK-STAT6-6-CNT ,WK-STAT6-6-AMT   ,
        // 046190                          WK-STAT6-7-CNT ,WK-STAT6-7-AMT   ,
        // 046195                          WK-STAT6-8-CNT ,WK-STAT6-8-AMT   ,
        // 046200                          WK-STAT6-SUBCNT,WK-STAT6-ENTPNO  ,
        // 046300                          WK-STAT6-SUBAMT,WK-STAT6-SMSERNO .
        // 046400 1200-MRINIT-EXIT.

        wkSTAT6_C_Cnt = 0;
        wkSTAT6_M_Cnt = 0;
        wkSTAT6_C_Amt = BigDecimal.ZERO;
        wkSTAT6_M_Amt = BigDecimal.ZERO;
        wkSTAT6_R_Cnt = 0;
        wkSTAT6_I_Cnt = 0;
        wkSTAT6_R_Amt = BigDecimal.ZERO;
        wkSTAT6_I_Amt = BigDecimal.ZERO;
        wkSTAT6_V_Cnt = 0;
        wkSTAT6_A_Cnt = 0;
        wkSTAT6_V_Amt = BigDecimal.ZERO;
        wkSTAT6_A_Amt = BigDecimal.ZERO;
        wkSTAT6_E_Cnt = 0;
        wkSTAT6_F_Cnt = 0;
        wkSTAT6_E_Amt = BigDecimal.ZERO;
        wkSTAT6_F_Amt = BigDecimal.ZERO;
        wkSTAT6_H_Cnt = 0;
        wkSTAT6_J_Cnt = 0;
        wkSTAT6_H_Amt = BigDecimal.ZERO;
        wkSTAT6_J_Amt = BigDecimal.ZERO;
        wkSTAT6_K_Cnt = 0;
        wkSTAT6_N_Cnt = 0;
        wkSTAT6_K_Amt = BigDecimal.ZERO;
        wkSTAT6_N_Amt = BigDecimal.ZERO;
        wkSTAT6_O_Cnt = 0;
        wkSTAT6_L_Cnt = 0;
        wkSTAT6_O_Amt = BigDecimal.ZERO;
        wkSTAT6_L_Amt = BigDecimal.ZERO;
        wkSTAT6_U_Cnt = 0;
        wkSTAT6_T_Cnt = 0;
        wkSTAT6_U_Amt = BigDecimal.ZERO;
        wkSTAT6_T_Amt = BigDecimal.ZERO;
        wkSTAT6_Q_Cnt = 0;
        wkSTAT6_X_Cnt = 0;
        wkSTAT6_Q_Amt = BigDecimal.ZERO;
        wkSTAT6_X_Amt = BigDecimal.ZERO;
        wkSTAT6_Z_Cnt = 0;
        wkSTAT6_2_Cnt = 0;
        wkSTAT6_Z_Amt = BigDecimal.ZERO;
        wkSTAT6_2_Amt = BigDecimal.ZERO;
        wkSTAT6_3_Cnt = 0;
        wkSTAT6_3_Amt = BigDecimal.ZERO;
        wkSTAT6_4_Cnt = 0;
        wkSTAT6_4_Amt = BigDecimal.ZERO;
        wkSTAT6_5_Cnt = 0;
        wkSTAT6_5_Amt = BigDecimal.ZERO;
        wkSTAT6_6_Cnt = 0;
        wkSTAT6_6_Amt = BigDecimal.ZERO;
        wkSTAT6_7_Cnt = 0;
        wkSTAT6_7_Amt = BigDecimal.ZERO;
        wkSTAT6_8_Cnt = 0;
        wkSTAT6_8_Amt = BigDecimal.ZERO;
        wkSTAT6_EntpNo = 0;
        wkSTAT6_SubCnt = 0;
        wkSTAT6_SubAmt = BigDecimal.ZERO;
        wkSTAT6_SmserNo = "";
    }

    private void _1000_MRDTL(String code) {

        // 028200 1000-MRDTL-RTN.
        //
        //// 搬明細資料
        //
        // 028300
        //
        //// 將DB-CLMR-IDX1指標移至開始
        //
        // 028400     SET    DB-CLMR-IDX1         TO     BEGINNING        .
        //
        //// 依代收類別FIND DB-CLMR-DDS事業單位基本資料檔，
        //// 正常,下一步
        //// 若有誤，若NOTFOUND，搬空值到STAT6-REC，結束程式
        //
        //
        // 028500     FIND   DB-CLMR-IDX1 AT DB-CLMR-CODE = WK-RPT017-CODE
        // 028600     ON EXCEPTION
        // 028700     IF DMSTATUS(NOTFOUND)
        // 028800        MOVE   WK-RPT017-CODE    TO     WK-STAT6-CODE
        // 028900        MOVE   "N/A"             TO     WK-STAT6-CNAME
        // 029000        MOVE   "N/A"             TO     WK-STAT6-PBRNO
        // 029100        MOVE   "N/A"             TO     WK-STAT6-APPDT
        // 029200        MOVE   "N/A"             TO     WK-STAT6-PBRNAME
        // 029300        GO TO  1000-MRDTL-EXIT.
        ClmrBus lClmrBus = clmrService.findById(code);
        if (Objects.isNull(lClmrBus)) {
            return;
        }
        CltmrBus lCltmrBus = cltmrService.findById(code);
        if (Objects.isNull(lCltmrBus)) {
            return;
        }
        //// 搬相關資料到STAT6-REC
        //
        // 029400     MOVE   DB-CLMR-CODE         TO     WK-STAT6-CODE    .
        // 029500     MOVE   DB-CLMR-CNAME        TO     WK-STAT6-CNAME   .
        // 029600     MOVE   DB-CLMR-PBRNO        TO     WK-STAT6-PBRNO   .
        // 029700     MOVE   DB-CLMR-APPDT        TO     WK-STAT6-APPDT   .
        // 029720     MOVE   DB-CLMR-PUTTYPE      TO     WK-STAT6-PUTFILE(1:2).
        // 029740     MOVE   DB-CLMR-PUTNAME      TO     WK-STAT6-PUTFILE(3:8).
        // 029750     IF     DB-CLMR-ENTPNO       NOT =  SPACES
        // 029800       MOVE DB-CLMR-ENTPNO       TO     WK-STAT6-ENTPNO
        // 029820     ELSE
        // 029840       MOVE DB-CLMR-HENTPNO      TO     WK-STAT6-ENTPNO  .
        wkSTAT6Code = lClmrBus.getCode();
        wkSTAT6CName = lClmrBus.getCname();
        wkSTAT6Pbrno = lClmrBus.getPbrno();
        wkSTAT6Appdt = lCltmrBus.getAppdt();
        wkSTAT6PutFile = "";
        wkSTAT6PutName = lCltmrBus.getPutname();
        wkSTAT6EntpNo =
                lCltmrBus.getEntpno() == null || lCltmrBus.getEntpno().isEmpty()
                        ? lCltmrBus.getHentpno() + ""
                        : lCltmrBus.getEntpno();
        //// 依DB-CLMR-PBRNO主辦分行 FIND DB-BCTL-ACCESS，若有誤
        ////  若 找不到，搬空白到WK-STAT6-PBRNAME，結束本段
        ////  正常，搬DB-BCTL-CHNAM到WK-STAT6-PBRNAME
        //
        // 029900     FIND   DB-BCTL-ACCESS AT DB-BCTL-BRNO = DB-CLMR-PBRNO
        // 029920       ON EXCEPTION
        // 029940       IF DMSTATUS(NOTFOUND)
        // 029960          MOVE   SPACE           TO     WK-STAT6-PBRNAME
        // 029980          GO TO 1000-MRDTL-EXIT.
        // 030000     MOVE   DB-BCTL-CHNAM   TO     WK-STAT6-PBRNAME .
        // 030100
        // 030200 1000-MRDTL-EXIT.
        Bctl bctl = this.event.getAggregateBuffer().getMgGlobal().getBctl(wkSTAT6Pbrno);
        if (Objects.isNull(bctl)) {
            return;
        }
        wkSTAT6Brno = bctl.getBrno();
        wkSTAT6PbrName = getPbrName(parse.string2Integer(wkSTAT6Brno));
    }

    private void _2200_DTLCNT(String kputh1Txtype, BigDecimal kputh1Amt, String kputh1SmserNo) {
        // 030500 2200-DTLCNT-RTN.
        //
        //// 累加各類別金額筆數
        //
        // 030800     IF KPUTH1-TXTYPE            =        "C"
        // 030900       ADD    1                  TO       WK-STAT6-C-CNT
        // 031000       ADD    KPUTH1-AMT         TO       WK-STAT6-C-AMT
        // 031100       ADD    1                  TO       WK-STAT6-SUBCNT
        // 031200       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("C".equals(kputh1Txtype)) {

            wkSTAT6_C_Cnt = wkSTAT6_C_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_C_Amt = wkSTAT6_C_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 031300     IF KPUTH1-TXTYPE            =        "M"
        // 031400       ADD    1                  TO       WK-STAT6-M-CNT
        // 031500       ADD    KPUTH1-AMT         TO       WK-STAT6-M-AMT
        // 031600       ADD    1                  TO       WK-STAT6-SUBCNT
        // 031700       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("M".equals(kputh1Txtype)) {
            wkSTAT6_M_Cnt = wkSTAT6_M_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_M_Amt = wkSTAT6_M_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 031800     IF KPUTH1-TXTYPE            =        "R"
        // 031900       ADD    1                  TO       WK-STAT6-R-CNT
        // 032000       ADD    KPUTH1-AMT         TO       WK-STAT6-R-AMT
        // 032100       ADD    1                  TO       WK-STAT6-SUBCNT
        // 032200       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("R".equals(kputh1Txtype)) {
            wkSTAT6_R_Cnt = wkSTAT6_R_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_R_Amt = wkSTAT6_R_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 032300     IF KPUTH1-TXTYPE            =        "I" OR = "D"
        // 032400       ADD    1                  TO       WK-STAT6-I-CNT
        // 032500       ADD    KPUTH1-AMT         TO       WK-STAT6-I-AMT
        // 032600       ADD    1                  TO       WK-STAT6-SUBCNT
        // 032700       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("I".equals(kputh1Txtype)) {
            wkSTAT6_I_Cnt = wkSTAT6_I_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_I_Amt = wkSTAT6_I_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        if ("D".equals(kputh1Txtype)) {
            wkSTAT6_I_Cnt = wkSTAT6_I_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_I_Amt = wkSTAT6_I_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 032800     IF KPUTH1-TXTYPE            =        "V"
        // 032900       ADD    1                  TO       WK-STAT6-V-CNT
        // 033000       ADD    KPUTH1-AMT         TO       WK-STAT6-V-AMT
        // 033100       ADD    1                  TO       WK-STAT6-SUBCNT
        // 033200       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("V".equals(kputh1Txtype)) {
            wkSTAT6_V_Cnt = wkSTAT6_V_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_V_Amt = wkSTAT6_V_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 033300     IF KPUTH1-TXTYPE            =        "A" OR ="S"
        // 033400       ADD    1                  TO       WK-STAT6-A-CNT
        // 033500       ADD    KPUTH1-AMT         TO       WK-STAT6-A-AMT
        // 033600       ADD    1                  TO       WK-STAT6-SUBCNT
        // 033700       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("A".equals(kputh1Txtype)) {
            wkSTAT6_A_Cnt = wkSTAT6_A_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_A_Amt = wkSTAT6_A_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        if ("S".equals(kputh1Txtype)) {
            wkSTAT6_A_Cnt = wkSTAT6_A_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_A_Amt = wkSTAT6_A_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 033800     IF KPUTH1-TXTYPE            =        "E"
        // 033900       ADD    1                  TO       WK-STAT6-E-CNT
        // 034000       ADD    KPUTH1-AMT         TO       WK-STAT6-E-AMT
        // 034100       ADD    1                  TO       WK-STAT6-SUBCNT
        // 034200       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("E".equals(kputh1Txtype)) {
            wkSTAT6_E_Cnt = wkSTAT6_E_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_E_Amt = wkSTAT6_E_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 034300     IF KPUTH1-TXTYPE            =        "F" OR ="P"
        // 034400       ADD    1                  TO       WK-STAT6-F-CNT
        // 034500       ADD    KPUTH1-AMT         TO       WK-STAT6-F-AMT
        // 034600       ADD    1                  TO       WK-STAT6-SUBCNT
        // 034700       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("F".equals(kputh1Txtype)) {
            wkSTAT6_F_Cnt = wkSTAT6_F_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_F_Amt = wkSTAT6_F_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        if ("P".equals(kputh1Txtype)) {
            wkSTAT6_F_Cnt = wkSTAT6_F_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_F_Amt = wkSTAT6_F_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 034800     IF KPUTH1-TXTYPE            =        "H" OR ="G"
        // 034900       ADD    1                  TO       WK-STAT6-H-CNT
        // 035000       ADD    KPUTH1-AMT         TO       WK-STAT6-H-AMT
        // 035100       ADD    1                  TO       WK-STAT6-SUBCNT
        // 035200       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("H".equals(kputh1Txtype)) {
            wkSTAT6_H_Cnt = wkSTAT6_H_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_H_Amt = wkSTAT6_H_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        if ("G".equals(kputh1Txtype)) {
            wkSTAT6_H_Cnt = wkSTAT6_H_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_H_Amt = wkSTAT6_H_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 035300     IF KPUTH1-TXTYPE            =        "J"
        // 035400       ADD    1                  TO       WK-STAT6-J-CNT
        // 035500       ADD    KPUTH1-AMT         TO       WK-STAT6-J-AMT
        // 035600       ADD    1                  TO       WK-STAT6-SUBCNT
        // 035700       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("J".equals(kputh1Txtype)) {
            wkSTAT6_J_Cnt = wkSTAT6_J_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_J_Amt = wkSTAT6_J_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 035800     IF KPUTH1-TXTYPE            =        "K"
        // 035850       MOVE   KPUTH1-SMSERNO     TO       WK-STAT6-SMSERNO
        // 035900       ADD    1                  TO       WK-STAT6-K-CNT
        // 036000       ADD    KPUTH1-AMT         TO       WK-STAT6-K-AMT
        // 036100       ADD    1                  TO       WK-STAT6-SUBCNT
        // 036200       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("K".equals(kputh1Txtype)) {
            wkSTAT6_SmserNo = kputh1SmserNo;
            wkSTAT6_K_Cnt = wkSTAT6_K_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_K_Amt = wkSTAT6_K_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 036300     IF KPUTH1-TXTYPE            =        "L"
        // 036350       MOVE   KPUTH1-SMSERNO     TO       WK-STAT6-SMSERNO
        // 036400       ADD    1                  TO       WK-STAT6-L-CNT
        // 036500       ADD    KPUTH1-AMT         TO       WK-STAT6-L-AMT
        // 036600       ADD    1                  TO       WK-STAT6-SUBCNT
        // 036700       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("L".equals(kputh1Txtype)) {
            wkSTAT6_SmserNo = kputh1SmserNo;
            wkSTAT6_L_Cnt = wkSTAT6_L_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_L_Amt = wkSTAT6_L_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 036800     IF KPUTH1-TXTYPE            =        "N"
        // 036850       MOVE   KPUTH1-SMSERNO     TO       WK-STAT6-SMSERNO
        // 036900       ADD    1                  TO       WK-STAT6-N-CNT
        // 037000       ADD    KPUTH1-AMT         TO       WK-STAT6-N-AMT
        // 037100       ADD    1                  TO       WK-STAT6-SUBCNT
        // 037200       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("N".equals(kputh1Txtype)) {
            wkSTAT6_SmserNo = kputh1SmserNo;
            wkSTAT6_N_Cnt = wkSTAT6_N_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_N_Amt = wkSTAT6_N_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 037300     IF KPUTH1-TXTYPE            =        "O"
        // 037350       MOVE   KPUTH1-SMSERNO     TO       WK-STAT6-SMSERNO
        // 037400       ADD    1                  TO       WK-STAT6-O-CNT
        // 037500       ADD    KPUTH1-AMT         TO       WK-STAT6-O-AMT
        // 037600       ADD    1                  TO       WK-STAT6-SUBCNT
        // 037700       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("O".equals(kputh1Txtype)) {
            wkSTAT6_SmserNo = kputh1SmserNo;
            wkSTAT6_O_Cnt = wkSTAT6_O_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_O_Amt = wkSTAT6_O_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 037800     IF KPUTH1-TXTYPE            =        "Q"
        // 037900       ADD    1                  TO       WK-STAT6-Q-CNT
        // 038000       ADD    KPUTH1-AMT         TO       WK-STAT6-Q-AMT
        // 038100       ADD    1                  TO       WK-STAT6-SUBCNT
        // 038200       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("Q".equals(kputh1Txtype)) {
            wkSTAT6_Q_Cnt = wkSTAT6_Q_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_Q_Amt = wkSTAT6_Q_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 038300     IF KPUTH1-TXTYPE            =        "T"
        // 038400       ADD    1                  TO       WK-STAT6-T-CNT
        // 038500       ADD    KPUTH1-AMT         TO       WK-STAT6-T-AMT
        // 038600       ADD    1                  TO       WK-STAT6-SUBCNT
        // 038700       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("T".equals(kputh1Txtype)) {
            wkSTAT6_T_Cnt = wkSTAT6_T_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_T_Amt = wkSTAT6_T_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 038800     IF KPUTH1-TXTYPE            =        "X"
        // 038900       ADD    1                  TO       WK-STAT6-X-CNT
        // 039000       ADD    KPUTH1-AMT         TO       WK-STAT6-X-AMT
        // 039100       ADD    1                  TO       WK-STAT6-SUBCNT
        // 039200       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("X".equals(kputh1Txtype)) {
            wkSTAT6_X_Cnt = wkSTAT6_X_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_X_Amt = wkSTAT6_X_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 039205     IF KPUTH1-TXTYPE            =        "Z"
        // 039210       ADD    1                  TO       WK-STAT6-Z-CNT
        // 039215       ADD    KPUTH1-AMT         TO       WK-STAT6-Z-AMT
        // 039220       ADD    1                  TO       WK-STAT6-SUBCNT
        // 039225       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("Z".equals(kputh1Txtype)) {
            wkSTAT6_Z_Cnt = wkSTAT6_Z_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_Z_Amt = wkSTAT6_Z_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 039230     IF KPUTH1-TXTYPE            =        "2"
        // 039235       ADD    1                  TO       WK-STAT6-2-CNT
        // 039240       ADD    KPUTH1-AMT         TO       WK-STAT6-2-AMT
        // 039245       ADD    1                  TO       WK-STAT6-SUBCNT
        // 039250       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("2".equals(kputh1Txtype)) {
            wkSTAT6_2_Cnt = wkSTAT6_2_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_2_Amt = wkSTAT6_2_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 039300     IF KPUTH1-TXTYPE            =        "U"
        // 039400       ADD    1                  TO       WK-STAT6-U-CNT
        // 039500       ADD    KPUTH1-AMT         TO       WK-STAT6-U-AMT
        // 039600       ADD    1                  TO       WK-STAT6-SUBCNT
        // 039700       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("U".equals(kputh1Txtype)) {
            wkSTAT6_U_Cnt = wkSTAT6_U_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_U_Amt = wkSTAT6_U_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 039710     IF KPUTH1-TXTYPE            =        "3"
        // 039720       ADD    1                  TO       WK-STAT6-3-CNT
        // 039730       ADD    KPUTH1-AMT         TO       WK-STAT6-3-AMT
        // 039740       ADD    1                  TO       WK-STAT6-SUBCNT
        // 039750       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("3".equals(kputh1Txtype)) {
            wkSTAT6_3_Cnt = wkSTAT6_3_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_3_Amt = wkSTAT6_3_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 039752     IF KPUTH1-TXTYPE            =        "4"
        // 039754       ADD    1                  TO       WK-STAT6-4-CNT
        // 039756       ADD    KPUTH1-AMT         TO       WK-STAT6-4-AMT
        // 039758       ADD    1                  TO       WK-STAT6-SUBCNT
        // 039760       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("4".equals(kputh1Txtype)) {
            wkSTAT6_4_Cnt = wkSTAT6_4_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_4_Amt = wkSTAT6_4_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 039765     IF KPUTH1-TXTYPE            =        "5"
        // 039770       ADD    1                  TO       WK-STAT6-5-CNT
        // 039775       ADD    KPUTH1-AMT         TO       WK-STAT6-5-AMT
        // 039780       ADD    1                  TO       WK-STAT6-SUBCNT
        // 039785       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("5".equals(kputh1Txtype)) {
            wkSTAT6_5_Cnt = wkSTAT6_5_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_5_Amt = wkSTAT6_5_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 039786     IF KPUTH1-TXTYPE            =        "6"
        // 039787       ADD    1                  TO       WK-STAT6-6-CNT
        // 039788       ADD    KPUTH1-AMT         TO       WK-STAT6-6-AMT
        // 039789       ADD    1                  TO       WK-STAT6-SUBCNT
        // 039790       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("6".equals(kputh1Txtype)) {
            wkSTAT6_6_Cnt = wkSTAT6_6_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_6_Amt = wkSTAT6_6_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 039791     IF KPUTH1-TXTYPE            =        "7"
        // 039792       ADD    1                  TO       WK-STAT6-7-CNT
        // 039793       ADD    KPUTH1-AMT         TO       WK-STAT6-7-AMT
        // 039794       ADD    1                  TO       WK-STAT6-SUBCNT
        // 039795       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("7".equals(kputh1Txtype)) {
            wkSTAT6_7_Cnt = wkSTAT6_7_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_7_Amt = wkSTAT6_7_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 039800     IF KPUTH1-TXTYPE            =        "8"
        // 039810       ADD    1                  TO       WK-STAT6-8-CNT
        // 039820       ADD    KPUTH1-AMT         TO       WK-STAT6-8-AMT
        // 039830       ADD    1                  TO       WK-STAT6-SUBCNT
        // 039840       ADD    KPUTH1-AMT         TO       WK-STAT6-SUBAMT.
        if ("8".equals(kputh1Txtype)) {
            wkSTAT6_8_Cnt = wkSTAT6_8_Cnt + 1;
            wkSTAT6_SubCnt = wkSTAT6_SubCnt + 1;
            wkSTAT6_8_Amt = wkSTAT6_8_Amt.add(kputh1Amt);
            wkSTAT6_SubAmt = wkSTAT6_SubAmt.add(kputh1Amt);
        }
        // 039850
        // 039890 2200-DTLCNT-EXIT.
    }

    private void _1100_MRWIT() {
        // 040100 1100-MRWIT-RTN.
        //// 累加總金額總筆數
        // 040200     ADD    WK-STAT6-C-CNT       TO       WK-STAT6-C-TOTCNT.
        // 040300     ADD    WK-STAT6-C-AMT       TO       WK-STAT6-C-TOTAMT.
        // 040400     ADD    WK-STAT6-M-CNT       TO       WK-STAT6-M-TOTCNT.
        // 040500     ADD    WK-STAT6-M-AMT       TO       WK-STAT6-M-TOTAMT.
        // 040600     ADD    WK-STAT6-R-CNT       TO       WK-STAT6-R-TOTCNT.
        // 040700     ADD    WK-STAT6-R-AMT       TO       WK-STAT6-R-TOTAMT.
        // 040800     ADD    WK-STAT6-I-CNT       TO       WK-STAT6-I-TOTCNT.
        // 040900     ADD    WK-STAT6-I-AMT       TO       WK-STAT6-I-TOTAMT.
        // 041000     ADD    WK-STAT6-V-CNT       TO       WK-STAT6-V-TOTCNT.
        // 041100     ADD    WK-STAT6-V-AMT       TO       WK-STAT6-V-TOTAMT.
        // 041200     ADD    WK-STAT6-A-CNT       TO       WK-STAT6-A-TOTCNT.
        // 041300     ADD    WK-STAT6-A-AMT       TO       WK-STAT6-A-TOTAMT.
        // 041400     ADD    WK-STAT6-E-CNT       TO       WK-STAT6-E-TOTCNT.
        // 041500     ADD    WK-STAT6-E-AMT       TO       WK-STAT6-E-TOTAMT.
        // 041600     ADD    WK-STAT6-F-CNT       TO       WK-STAT6-F-TOTCNT.
        // 041700     ADD    WK-STAT6-F-AMT       TO       WK-STAT6-F-TOTAMT.
        // 041800     ADD    WK-STAT6-H-CNT       TO       WK-STAT6-H-TOTCNT.
        // 041900     ADD    WK-STAT6-H-AMT       TO       WK-STAT6-H-TOTAMT.
        // 042000     ADD    WK-STAT6-J-CNT       TO       WK-STAT6-W-TOTCNT.
        // 042100     ADD    WK-STAT6-J-AMT       TO       WK-STAT6-W-TOTAMT.
        // 042200     ADD    WK-STAT6-K-CNT       TO       WK-STAT6-K-TOTCNT.
        // 042300     ADD    WK-STAT6-K-AMT       TO       WK-STAT6-K-TOTAMT.
        // 042400     ADD    WK-STAT6-N-CNT       TO       WK-STAT6-N-TOTCNT.
        // 042500     ADD    WK-STAT6-N-AMT       TO       WK-STAT6-N-TOTAMT.
        // 042600     ADD    WK-STAT6-O-CNT       TO       WK-STAT6-O-TOTCNT.
        // 042700     ADD    WK-STAT6-O-AMT       TO       WK-STAT6-O-TOTAMT.
        // 042800     ADD    WK-STAT6-L-CNT       TO       WK-STAT6-L-TOTCNT.
        // 042900     ADD    WK-STAT6-L-AMT       TO       WK-STAT6-L-TOTAMT.
        // 043000     ADD    WK-STAT6-U-CNT       TO       WK-STAT6-U-TOTCNT.
        // 043100     ADD    WK-STAT6-U-AMT       TO       WK-STAT6-U-TOTAMT.
        // 043200     ADD    WK-STAT6-T-CNT       TO       WK-STAT6-T-TOTCNT.
        // 043300     ADD    WK-STAT6-T-AMT       TO       WK-STAT6-T-TOTAMT.
        // 043400     ADD    WK-STAT6-Q-CNT       TO       WK-STAT6-Q-TOTCNT.
        // 043500     ADD    WK-STAT6-Q-AMT       TO       WK-STAT6-Q-TOTAMT.
        // 043600     ADD    WK-STAT6-X-CNT       TO       WK-STAT6-X-TOTCNT.
        // 043700     ADD    WK-STAT6-X-AMT       TO       WK-STAT6-X-TOTAMT.
        // 043720     ADD    WK-STAT6-Z-CNT       TO       WK-STAT6-Z-TOTCNT.
        // 043740     ADD    WK-STAT6-Z-AMT       TO       WK-STAT6-Z-TOTAMT.
        // 043760     ADD    WK-STAT6-2-CNT       TO       WK-STAT6-2-TOTCNT.
        // 043780     ADD    WK-STAT6-2-AMT       TO       WK-STAT6-2-TOTAMT.
        // 043785     ADD    WK-STAT6-3-CNT       TO       WK-STAT6-3-TOTCNT.
        // 043790     ADD    WK-STAT6-3-AMT       TO       WK-STAT6-3-TOTAMT.
        // 043792     ADD    WK-STAT6-4-CNT       TO       WK-STAT6-4-TOTCNT.
        // 043795     ADD    WK-STAT6-4-AMT       TO       WK-STAT6-4-TOTAMT.
        // 043797     ADD    WK-STAT6-5-CNT       TO       WK-STAT6-5-TOTCNT.
        // 043799     ADD    WK-STAT6-5-AMT       TO       WK-STAT6-5-TOTAMT.
        // 043800     ADD    WK-STAT6-6-CNT       TO       WK-STAT6-6-TOTCNT.
        // 043820     ADD    WK-STAT6-6-AMT       TO       WK-STAT6-6-TOTAMT.
        // 043840     ADD    WK-STAT6-7-CNT       TO       WK-STAT6-7-TOTCNT.
        // 043860     ADD    WK-STAT6-7-AMT       TO       WK-STAT6-7-TOTAMT.
        // 043870     ADD    WK-STAT6-8-CNT       TO       WK-STAT6-8-TOTCNT.
        // 043880     ADD    WK-STAT6-8-AMT       TO       WK-STAT6-8-TOTAMT.
        // 043890     ADD    WK-STAT6-SUBCNT      TO       WK-STAT6-TOTCNT  .
        // 043900     ADD    WK-STAT6-SUBAMT      TO       WK-STAT6-TOTAMT  .
        // 044000 1100-MRWIT-EXIT.
        wkSTAT6_C_TotCnt = wkSTAT6_C_TotCnt + wkSTAT6_C_Cnt;
        wkSTAT6_M_TotCnt = wkSTAT6_M_TotCnt + wkSTAT6_M_Cnt;
        wkSTAT6_C_TotAmt = wkSTAT6_C_TotAmt.add(wkSTAT6_C_Amt);
        wkSTAT6_M_TotAmt = wkSTAT6_M_TotAmt.add(wkSTAT6_M_Amt);
        wkSTAT6_R_TotCnt = wkSTAT6_R_TotCnt + wkSTAT6_R_Cnt;
        wkSTAT6_I_TotCnt = wkSTAT6_I_TotCnt + wkSTAT6_I_Cnt;
        wkSTAT6_R_TotAmt = wkSTAT6_R_TotAmt.add(wkSTAT6_R_Amt);
        wkSTAT6_I_TotAmt = wkSTAT6_I_TotAmt.add(wkSTAT6_I_Amt);
        wkSTAT6_V_TotCnt = wkSTAT6_V_TotCnt + wkSTAT6_V_Cnt;
        wkSTAT6_A_TotCnt = wkSTAT6_A_TotCnt + wkSTAT6_A_Cnt;
        wkSTAT6_V_TotAmt = wkSTAT6_V_TotAmt.add(wkSTAT6_V_Amt);
        wkSTAT6_A_TotAmt = wkSTAT6_A_TotAmt.add(wkSTAT6_A_Amt);
        wkSTAT6_E_TotCnt = wkSTAT6_E_TotCnt + wkSTAT6_E_Cnt;
        wkSTAT6_F_TotCnt = wkSTAT6_F_TotCnt + wkSTAT6_F_Cnt;
        wkSTAT6_E_TotAmt = wkSTAT6_E_TotAmt.add(wkSTAT6_E_Amt);
        wkSTAT6_F_TotAmt = wkSTAT6_F_TotAmt.add(wkSTAT6_F_Amt);
        wkSTAT6_H_TotCnt = wkSTAT6_H_TotCnt + wkSTAT6_H_Cnt;
        wkSTAT6_J_TotCnt = wkSTAT6_J_TotCnt + wkSTAT6_J_Cnt;
        wkSTAT6_H_TotAmt = wkSTAT6_H_TotAmt.add(wkSTAT6_H_Amt);
        wkSTAT6_J_TotAmt = wkSTAT6_J_TotAmt.add(wkSTAT6_J_Amt);
        wkSTAT6_K_TotCnt = wkSTAT6_K_TotCnt + wkSTAT6_K_Cnt;
        wkSTAT6_N_TotCnt = wkSTAT6_N_TotCnt + wkSTAT6_N_Cnt;
        wkSTAT6_K_TotAmt = wkSTAT6_K_TotAmt.add(wkSTAT6_K_Amt);
        wkSTAT6_N_TotAmt = wkSTAT6_N_TotAmt.add(wkSTAT6_N_Amt);
        wkSTAT6_O_TotCnt = wkSTAT6_O_TotCnt + wkSTAT6_O_Cnt;
        wkSTAT6_L_TotCnt = wkSTAT6_L_TotCnt + wkSTAT6_L_Cnt;
        wkSTAT6_O_TotAmt = wkSTAT6_O_TotAmt.add(wkSTAT6_O_Amt);
        wkSTAT6_L_TotAmt = wkSTAT6_L_TotAmt.add(wkSTAT6_L_Amt);
        wkSTAT6_U_TotCnt = wkSTAT6_U_TotCnt + wkSTAT6_U_Cnt;
        wkSTAT6_T_TotCnt = wkSTAT6_T_TotCnt + wkSTAT6_T_Cnt;
        wkSTAT6_U_TotAmt = wkSTAT6_U_TotAmt.add(wkSTAT6_U_Amt);
        wkSTAT6_T_TotAmt = wkSTAT6_T_TotAmt.add(wkSTAT6_T_Amt);
        wkSTAT6_Q_TotCnt = wkSTAT6_Q_TotCnt + wkSTAT6_Q_Cnt;
        wkSTAT6_X_TotCnt = wkSTAT6_X_TotCnt + wkSTAT6_X_Cnt;
        wkSTAT6_Q_TotAmt = wkSTAT6_Q_TotAmt.add(wkSTAT6_Q_Amt);
        wkSTAT6_X_TotAmt = wkSTAT6_X_TotAmt.add(wkSTAT6_X_Amt);
        wkSTAT6_Z_TotCnt = wkSTAT6_Z_TotCnt + wkSTAT6_Z_Cnt;
        wkSTAT6_2_TotCnt = wkSTAT6_2_TotCnt + wkSTAT6_2_Cnt;
        wkSTAT6_Z_TotAmt = wkSTAT6_Z_TotAmt.add(wkSTAT6_Z_Amt);
        wkSTAT6_2_TotAmt = wkSTAT6_2_TotAmt.add(wkSTAT6_2_Amt);
        wkSTAT6_3_TotCnt = wkSTAT6_3_TotCnt + wkSTAT6_3_Cnt;
        wkSTAT6_3_TotAmt = wkSTAT6_3_TotAmt.add(wkSTAT6_3_Amt);
        wkSTAT6_4_TotCnt = wkSTAT6_4_TotCnt + wkSTAT6_4_Cnt;
        wkSTAT6_4_TotAmt = wkSTAT6_4_TotAmt.add(wkSTAT6_4_Amt);
        wkSTAT6_5_TotCnt = wkSTAT6_5_TotCnt + wkSTAT6_5_Cnt;
        wkSTAT6_5_TotAmt = wkSTAT6_5_TotAmt.add(wkSTAT6_5_Amt);
        wkSTAT6_6_TotCnt = wkSTAT6_6_TotCnt + wkSTAT6_6_Cnt;
        wkSTAT6_6_TotAmt = wkSTAT6_6_TotAmt.add(wkSTAT6_6_Amt);
        wkSTAT6_7_TotCnt = wkSTAT6_7_TotCnt + wkSTAT6_7_Cnt;
        wkSTAT6_7_TotAmt = wkSTAT6_7_TotAmt.add(wkSTAT6_7_Amt);
        wkSTAT6_8_TotCnt = wkSTAT6_8_TotCnt + wkSTAT6_8_Cnt;
        wkSTAT6_8_TotAmt = wkSTAT6_8_TotAmt.add(wkSTAT6_8_Amt);
        wkSTAT6_TotCnt = wkSTAT6_TotCnt + wkSTAT6_SubCnt;
        wkSTAT6_TotAmt = wkSTAT6_TotAmt.add(wkSTAT6_SubAmt);
    }

    private void _WRITE_STAT6_REC() {
        // 005900 01  WK-STAT6-REC.
        // 006000   03  WK-STAT6-CODE                      PIC X(06) .
        // 006100   03  FILLER                             PIC X(01) VALUE ",".
        // 006200   03  WK-STAT6-CNAME                     PIC X(40) .
        // 006300   03  FILLER                             PIC X(01) VALUE ",".
        // 006320   03  WK-STAT6-ENTPNO                    PIC X(10) .
        // 006340   03  FILLER                             PIC X(01) VALUE ",".
        // 006360   03  WK-STAT6-SMSERNO                   PIC X(03) .
        // 006380   03  FILLER                             PIC X(01) VALUE ",".
        // 006400   03  WK-STAT6-PBRNO                     PIC X(03) .
        // 006500   03  FILLER                             PIC X(01) VALUE ",".
        // 006600   03  WK-STAT6-PBRNAME                   PIC X(26).
        // 006700   03  FILLER                             PIC X(01) VALUE ",".
        // 006800   03  WK-STAT6-APPDT                     PIC X(08).
        // 006900   03  FILLER                             PIC X(01) VALUE ",".
        // 007000   03  WK-STAT6-C-CNT                     PIC 9(06).
        // 007100   03  FILLER                             PIC X(01) VALUE ",".
        // 007200   03  WK-STAT6-C-AMT                     PIC 9(13).
        // 007300   03  FILLER                             PIC X(01) VALUE ",".
        // 007400   03  WK-STAT6-M-CNT                     PIC 9(06).
        // 007500   03  FILLER                             PIC X(01) VALUE ",".
        // 007600   03  WK-STAT6-M-AMT                     PIC 9(13).
        // 007700   03  FILLER                             PIC X(01) VALUE ",".
        // 007800   03  WK-STAT6-R-CNT                     PIC 9(06).
        // 007900   03  FILLER                             PIC X(01) VALUE ",".
        // 008000   03  WK-STAT6-R-AMT                     PIC 9(13).
        // 008100   03  FILLER                             PIC X(01) VALUE ",".
        // 008200   03  WK-STAT6-I-CNT                     PIC 9(06).
        // 008300   03  FILLER                             PIC X(01) VALUE ",".
        // 008400   03  WK-STAT6-I-AMT                     PIC 9(13).
        // 008500   03  FILLER                             PIC X(01) VALUE ",".
        // 008600   03  WK-STAT6-V-CNT                     PIC 9(06).
        // 008700   03  FILLER                             PIC X(01) VALUE ",".
        // 008800   03  WK-STAT6-V-AMT                     PIC 9(13).
        // 008900   03  FILLER                             PIC X(01) VALUE ",".
        // 009000   03  WK-STAT6-A-CNT                     PIC 9(06).
        // 009100   03  FILLER                             PIC X(01) VALUE ",".
        // 009200   03  WK-STAT6-A-AMT                     PIC 9(13).
        // 009300   03  FILLER                             PIC X(01) VALUE ",".
        // 009400   03  WK-STAT6-E-CNT                     PIC 9(06).
        // 009500   03  FILLER                             PIC X(01) VALUE ",".
        // 009600   03  WK-STAT6-E-AMT                     PIC 9(13).
        // 009700   03  FILLER                             PIC X(01) VALUE ",".
        // 009800   03  WK-STAT6-F-CNT                     PIC 9(06).
        // 009900   03  FILLER                             PIC X(01) VALUE ",".
        // 010000   03  WK-STAT6-F-AMT                     PIC 9(13).
        // 010100   03  FILLER                             PIC X(01) VALUE ",".
        // 010200   03  WK-STAT6-H-CNT                     PIC 9(06).
        // 010300   03  FILLER                             PIC X(01) VALUE ",".
        // 010400   03  WK-STAT6-H-AMT                     PIC 9(13).
        // 010500   03  FILLER                             PIC X(01) VALUE ",".
        // 010600   03  WK-STAT6-J-CNT                     PIC 9(06).
        // 010700   03  FILLER                             PIC X(01) VALUE ",".
        // 010800   03  WK-STAT6-J-AMT                     PIC 9(13).
        // 010900   03  FILLER                             PIC X(01) VALUE ",".
        // 011000   03  WK-STAT6-K-CNT                     PIC 9(06).
        // 011100   03  FILLER                             PIC X(01) VALUE ",".
        // 011200   03  WK-STAT6-K-AMT                     PIC 9(13).
        // 011300   03  FILLER                             PIC X(01) VALUE ",".
        // 011400   03  WK-STAT6-N-CNT                     PIC 9(06).
        // 011500   03  FILLER                             PIC X(01) VALUE ",".
        // 011600   03  WK-STAT6-N-AMT                     PIC 9(13).
        // 011700   03  FILLER                             PIC X(01) VALUE ",".
        // 011800   03  WK-STAT6-O-CNT                     PIC 9(06).
        // 011900   03  FILLER                             PIC X(01) VALUE ",".
        // 012000   03  WK-STAT6-O-AMT                     PIC 9(13).
        // 012100   03  FILLER                             PIC X(01) VALUE ",".
        // 012200   03  WK-STAT6-L-CNT                     PIC 9(06).
        // 012300   03  FILLER                             PIC X(01) VALUE ",".
        // 012400   03  WK-STAT6-L-AMT                     PIC 9(13).
        // 012500   03  FILLER                             PIC X(01) VALUE ",".
        // 012600   03  WK-STAT6-U-CNT                     PIC 9(06).
        // 012700   03  FILLER                             PIC X(01) VALUE ",".
        // 012800   03  WK-STAT6-U-AMT                     PIC 9(13).
        // 012900   03  FILLER                             PIC X(01) VALUE ",".
        // 013000   03  WK-STAT6-T-CNT                     PIC 9(06).
        // 013100   03  FILLER                             PIC X(01) VALUE ",".
        // 013200   03  WK-STAT6-T-AMT                     PIC 9(13).
        // 013300   03  FILLER                             PIC X(01) VALUE ",".
        // 013400   03  WK-STAT6-Q-CNT                     PIC 9(06).
        // 013500   03  FILLER                             PIC X(01) VALUE ",".
        // 013600   03  WK-STAT6-Q-AMT                     PIC 9(13).
        // 013700   03  FILLER                             PIC X(01) VALUE ",".
        // 013800   03  WK-STAT6-X-CNT                     PIC 9(06).
        // 013900   03  FILLER                             PIC X(01) VALUE ",".
        // 014000   03  WK-STAT6-X-AMT                     PIC 9(13).
        // 014100   03  FILLER                             PIC X(01) VALUE ",".
        // 014110   03  WK-STAT6-Z-CNT                     PIC 9(06).
        // 014120   03  FILLER                             PIC X(01) VALUE ",".
        // 014130   03  WK-STAT6-Z-AMT                     PIC 9(13).
        // 014140   03  FILLER                             PIC X(01) VALUE ",".
        // 014150   03  WK-STAT6-2-CNT                     PIC 9(06).
        // 014160   03  FILLER                             PIC X(01) VALUE ",".
        // 014170   03  WK-STAT6-2-AMT                     PIC 9(13).
        // 014172   03  FILLER                             PIC X(01) VALUE ",".
        // 014174   03  WK-STAT6-3-CNT                     PIC 9(06).
        // 014176   03  FILLER                             PIC X(01) VALUE ",".
        // 014178   03  WK-STAT6-3-AMT                     PIC 9(13).
        // 014180   03  FILLER                             PIC X(01) VALUE ",".
        // 014182   03  WK-STAT6-4-CNT                     PIC 9(06).
        // 014184   03  FILLER                             PIC X(01) VALUE ",".
        // 014186   03  WK-STAT6-4-AMT                     PIC 9(13).
        // 014190   03  FILLER                             PIC X(01) VALUE ",".
        // 014192   03  WK-STAT6-5-CNT                     PIC 9(06).
        // 014194   03  FILLER                             PIC X(01) VALUE ",".
        // 014196   03  WK-STAT6-5-AMT                     PIC 9(13).
        // 014198   03  FILLER                             PIC X(01) VALUE ",".
        // 014200   03  WK-STAT6-6-CNT                     PIC 9(06).
        // 014210   03  FILLER                             PIC X(01) VALUE ",".
        // 014220   03  WK-STAT6-6-AMT                     PIC 9(13).
        // 014230   03  FILLER                             PIC X(01) VALUE ",".
        // 014240   03  WK-STAT6-7-CNT                     PIC 9(06).
        // 014250   03  FILLER                             PIC X(01) VALUE ",".
        // 014260   03  WK-STAT6-7-AMT                     PIC 9(13).
        // 014270   03  FILLER                             PIC X(01) VALUE ",".
        // 014272   03  WK-STAT6-8-CNT                     PIC 9(06).
        // 014274   03  FILLER                             PIC X(01) VALUE ",".
        // 014276   03  WK-STAT6-8-AMT                     PIC 9(13).
        // 014278   03  FILLER                             PIC X(01) VALUE ",".
        // 014290   03  WK-STAT6-SUBCNT                    PIC 9(07).
        // 014300   03  FILLER                             PIC X(01) VALUE ",".
        // 014400   03  WK-STAT6-SUBAMT                    PIC 9(13).
        // 014420   03  FILLER                             PIC X(01) VALUE ",".
        // 014440   03  WK-STAT6-PUTFILE                   PIC X(10).
        String filler = ",";
        sb = new StringBuilder();
        sb.append(formatUtil.padX(wkSTAT6Code, 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6CName, 40));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6EntpNo, 10));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_SmserNo + "", 3));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6Brno, 3));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6PbrName, 26));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6Appdt + "", 8));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_C_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_C_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_M_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_M_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_R_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_R_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_I_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_I_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_V_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_V_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_A_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_A_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_E_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_E_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_F_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_F_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_H_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_H_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_J_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_J_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_K_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_K_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_N_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_N_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_O_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_O_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_L_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_L_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_U_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_U_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_T_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_T_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_Q_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_Q_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_X_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_X_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_Z_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_Z_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_2_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_2_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_3_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_3_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_4_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_4_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_5_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_5_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_6_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_6_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_7_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_7_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_8_Cnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_8_Amt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_SubCnt + "", 7));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_SubAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6PutFile, 2));
        sb.append(formatUtil.padX(wkSTAT6PutFile, 8));
        fileContentsSTAT6.add(sb.toString());
    }

    private void _WRITE_STAT6_TOTREC() {
        String filler = ",";
        sb = new StringBuilder();
        sb.append(formatUtil.padX("999999", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(" 合計 ", 40));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX("" + "", 3));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX("999", 3));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX("", 26));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_C_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_C_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_M_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_M_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_R_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_R_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_I_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_I_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_V_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_V_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_A_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_A_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_E_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_E_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_F_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_F_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_H_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_H_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_J_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_J_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_K_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_K_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_N_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_N_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_O_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_O_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_L_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_L_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_U_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_U_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_T_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_T_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_Q_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_Q_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_X_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_X_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_Z_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_Z_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_2_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_2_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_3_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_3_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_4_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_4_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_5_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_5_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_6_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_6_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_7_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_7_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_8_TotCnt + "", 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_8_TotAmt + "", 13));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_TotCnt + "", 7));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(wkSTAT6_TotAmt + "", 13));
        fileContentsSTAT6.add(sb.toString());
        // 014500 01  WK-STAT6-TOTREC.
        // 014600   03  WK-STAT6-TOTCODE            PIC X(06) VALUE "999999".
        // 014700   03  FILLER                             PIC X(01) VALUE ",".
        // 014800   03  WK-STAT6-TOTCNAME           PIC X(40) VALUE " 合計 ".
        // 014900   03  FILLER                             PIC X(01) VALUE ",".
        // 014920   03  WK-STAT6-TOTENTPNO                 PIC X(10).
        // 014940   03  FILLER                             PIC X(01) VALUE ",".
        // 014960   03  WK-STAT6-TOTSMSERNO                PIC X(03).
        // 014980   03  FILLER                             PIC X(01) VALUE ",".
        // 015000   03  WK-STAT6-TOTPBRNO                  PIC 9(03) VALUE 999.
        // 015100   03  FILLER                             PIC X(01) VALUE ",".
        // 015200   03  WK-STAT6-TOTPBRNAME                PIC X(26).
        // 015300   03  FILLER                             PIC X(01) VALUE ",".
        // 015400   03  WK-STAT6-TOTAPPDT                  PIC 9(08).
        // 015500   03  FILLER                             PIC X(01) VALUE ",".
        // 015600   03  WK-STAT6-C-TOTCNT                  PIC 9(06).
        // 015700   03  FILLER                             PIC X(01) VALUE ",".
        // 015800   03  WK-STAT6-C-TOTAMT                  PIC 9(13).
        // 015900   03  FILLER                             PIC X(01) VALUE ",".
        // 016000   03  WK-STAT6-M-TOTCNT                  PIC 9(06).
        // 016100   03  FILLER                             PIC X(01) VALUE ",".
        // 016200   03  WK-STAT6-M-TOTAMT                  PIC 9(13).
        // 016300   03  FILLER                             PIC X(01) VALUE ",".
        // 016400   03  WK-STAT6-R-TOTCNT                  PIC 9(06).
        // 016500   03  FILLER                             PIC X(01) VALUE ",".
        // 016600   03  WK-STAT6-R-TOTAMT                  PIC 9(13).
        // 016700   03  FILLER                             PIC X(01) VALUE ",".
        // 016800   03  WK-STAT6-I-TOTCNT                  PIC 9(06).
        // 016900   03  FILLER                             PIC X(01) VALUE ",".
        // 017000   03  WK-STAT6-I-TOTAMT                  PIC 9(13).
        // 017100   03  FILLER                             PIC X(01) VALUE ",".
        // 017200   03  WK-STAT6-V-TOTCNT                  PIC 9(06).
        // 017300   03  FILLER                             PIC X(01) VALUE ",".
        // 017400   03  WK-STAT6-V-TOTAMT                  PIC 9(13).
        // 017500   03  FILLER                             PIC X(01) VALUE ",".
        // 017600   03  WK-STAT6-A-TOTCNT                  PIC 9(06).
        // 017700   03  FILLER                             PIC X(01) VALUE ",".
        // 017800   03  WK-STAT6-A-TOTAMT                  PIC 9(13).
        // 017900   03  FILLER                             PIC X(01) VALUE ",".
        // 018000   03  WK-STAT6-E-TOTCNT                  PIC 9(06).
        // 018100   03  FILLER                             PIC X(01) VALUE ",".
        // 018200   03  WK-STAT6-E-TOTAMT                  PIC 9(13).
        // 018300   03  FILLER                             PIC X(01) VALUE ",".
        // 018400   03  WK-STAT6-F-TOTCNT                  PIC 9(06).
        // 018500   03  FILLER                             PIC X(01) VALUE ",".
        // 018600   03  WK-STAT6-F-TOTAMT                  PIC 9(13).
        // 018700   03  FILLER                             PIC X(01) VALUE ",".
        // 018800   03  WK-STAT6-H-TOTCNT                  PIC 9(06).
        // 018900   03  FILLER                             PIC X(01) VALUE ",".
        // 019000   03  WK-STAT6-H-TOTAMT                  PIC 9(13).
        // 019100   03  FILLER                             PIC X(01) VALUE ",".
        // 019200   03  WK-STAT6-W-TOTCNT                  PIC 9(06).
        // 019300   03  FILLER                             PIC X(01) VALUE ",".
        // 019400   03  WK-STAT6-W-TOTAMT                  PIC 9(13).
        // 019500   03  FILLER                             PIC X(01) VALUE ",".
        // 019600   03  WK-STAT6-K-TOTCNT                  PIC 9(06).
        // 019700   03  FILLER                             PIC X(01) VALUE ",".
        // 019800   03  WK-STAT6-K-TOTAMT                  PIC 9(13).
        // 019900   03  FILLER                             PIC X(01) VALUE ",".
        // 020000   03  WK-STAT6-N-TOTCNT                  PIC 9(06).
        // 020100   03  FILLER                             PIC X(01) VALUE ",".
        // 020200   03  WK-STAT6-N-TOTAMT                  PIC 9(13).
        // 020300   03  FILLER                             PIC X(01) VALUE ",".
        // 020400   03  WK-STAT6-O-TOTCNT                  PIC 9(06).
        // 020500   03  FILLER                             PIC X(01) VALUE ",".
        // 020600   03  WK-STAT6-O-TOTAMT                  PIC 9(13).
        // 020700   03  FILLER                             PIC X(01) VALUE ",".
        // 020800   03  WK-STAT6-L-TOTCNT                  PIC 9(06).
        // 020900   03  FILLER                             PIC X(01) VALUE ",".
        // 021000   03  WK-STAT6-L-TOTAMT                  PIC 9(13).
        // 021100   03  FILLER                             PIC X(01) VALUE ",".
        // 021200   03  WK-STAT6-U-TOTCNT                  PIC 9(06).
        // 021300   03  FILLER                             PIC X(01) VALUE ",".
        // 021400   03  WK-STAT6-U-TOTAMT                  PIC 9(13).
        // 021500   03  FILLER                             PIC X(01) VALUE ",".
        // 021600   03  WK-STAT6-T-TOTCNT                  PIC 9(06).
        // 021700   03  FILLER                             PIC X(01) VALUE ",".
        // 021800   03  WK-STAT6-T-TOTAMT                  PIC 9(13).
        // 021900   03  FILLER                             PIC X(01) VALUE ",".
        // 022000   03  WK-STAT6-Q-TOTCNT                  PIC 9(06).
        // 022100   03  FILLER                             PIC X(01) VALUE ",".
        // 022200   03  WK-STAT6-Q-TOTAMT                  PIC 9(13).
        // 022300   03  FILLER                             PIC X(01) VALUE ",".
        // 022400   03  WK-STAT6-X-TOTCNT                  PIC 9(06).
        // 022500   03  FILLER                             PIC X(01) VALUE ",".
        // 022600   03  WK-STAT6-X-TOTAMT                  PIC 9(13).
        // 022700   03  FILLER                             PIC X(01) VALUE ",".
        // 022710   03  WK-STAT6-Z-TOTCNT                  PIC 9(06).
        // 022720   03  FILLER                             PIC X(01) VALUE ",".
        // 022730   03  WK-STAT6-Z-TOTAMT                  PIC 9(13).
        // 022740   03  FILLER                             PIC X(01) VALUE ",".
        // 022750   03  WK-STAT6-2-TOTCNT                  PIC 9(06).
        // 022760   03  FILLER                             PIC X(01) VALUE ",".
        // 022770   03  WK-STAT6-2-TOTAMT                  PIC 9(13).
        // 022772   03  FILLER                             PIC X(01) VALUE ",".
        // 022774   03  WK-STAT6-3-TOTCNT                  PIC 9(06).
        // 022776   03  FILLER                             PIC X(01) VALUE ",".
        // 022778   03  WK-STAT6-3-TOTAMT                  PIC 9(13).
        // 022780   03  FILLER                             PIC X(01) VALUE ",".
        // 022782   03  WK-STAT6-4-TOTCNT                  PIC 9(06).
        // 022784   03  FILLER                             PIC X(01) VALUE ",".
        // 022786   03  WK-STAT6-4-TOTAMT                  PIC 9(13).
        // 022790   03  FILLER                             PIC X(01) VALUE ",".
        // 022792   03  WK-STAT6-5-TOTCNT                  PIC 9(06).
        // 022794   03  FILLER                             PIC X(01) VALUE ",".
        // 022796   03  WK-STAT6-5-TOTAMT                  PIC 9(13).
        // 022798   03  FILLER                             PIC X(01) VALUE ",".
        // 022800   03  WK-STAT6-6-TOTCNT                  PIC 9(06).
        // 022810   03  FILLER                             PIC X(01) VALUE ",".
        // 022820   03  WK-STAT6-6-TOTAMT                  PIC 9(13).
        // 022830   03  FILLER                             PIC X(01) VALUE ",".
        // 022840   03  WK-STAT6-7-TOTCNT                  PIC 9(06).
        // 022850   03  FILLER                             PIC X(01) VALUE ",".
        // 022860   03  WK-STAT6-7-TOTAMT                  PIC 9(13).
        // 022870   03  FILLER                             PIC X(01) VALUE ",".
        // 022872   03  WK-STAT6-8-TOTCNT                  PIC 9(06).
        // 022874   03  FILLER                             PIC X(01) VALUE ",".
        // 022876   03  WK-STAT6-8-TOTAMT                  PIC 9(13).
        // 022878   03  FILLER                             PIC X(01) VALUE ",".
        // 022890   03  WK-STAT6-TOTCNT                    PIC 9(07).
        // 022900   03  FILLER                             PIC X(01) VALUE ",".
        // 023000   03  WK-STAT6-TOTAMT                    PIC 9(13).
    }

    private void writeFile() {

        textFileSTAT6.deleteFile(fileDir + fileNameTmp);
        textFileSTAT6.deleteFile(fileNameSTAT6);

        try {
            textFileSTAT6.writeFileContent(fileNameSTAT6, fileContentsSTAT6, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }

    private String getPbrName(int pbrno) {
        String pbrName = "";
        if (pbrno == 5) {
            //    IF      WK-PBRNO-1 = 5
            //      MOVE " 公庫部 "     TO SD-PBRNAME
            pbrName = " 公庫部 ";
        } else if (pbrno == 9) {
            //    ELSE IF WK-PBRNO-1 = 9
            //      MOVE " 臺南分行 "   TO SD-PBRNAME
            pbrName = " 臺南分行 ";
        } else if (pbrno == 10) {
            //    ELSE IF WK-PBRNO-1 = 10
            //      MOVE " 臺中分行 "   TO SD-PBRNAME
            pbrName = " 臺中分行 ";
        } else if (pbrno == 11) {
            //    ELSE IF WK-PBRNO-1 = 11
            //      MOVE " 高雄分行 "   TO SD-PBRNAME
            pbrName = " 高雄分行 ";
        } else if (pbrno == 12) {
            //    ELSE IF WK-PBRNO-1 = 12
            //      MOVE " 基隆分行 "   TO SD-PBRNAME
            pbrName = " 基隆分行 ";
        } else if (pbrno == 14) {
            //    ELSE IF WK-PBRNO-1 = 14
            //      MOVE " 嘉義分行 "   TO SD-PBRNAME
            pbrName = " 嘉義分行 ";
        } else if (pbrno == 15) {
            //    ELSE IF WK-PBRNO-1 = 15
            //      MOVE " 新竹分行 "   TO SD-PBRNAME
            pbrName = " 新竹分行 ";
        } else if (pbrno == 16) {
            //    ELSE IF WK-PBRNO-1 = 16
            //      MOVE " 彰化分行 "   TO SD-PBRNAME
            pbrName = " 彰化分行 ";
        } else if (pbrno == 17) {
            //    ELSE IF WK-PBRNO-1 = 17
            //      MOVE " 屏東分行 "   TO SD-PBRNAME
            pbrName = " 屏東分行 ";
        } else if (pbrno == 18) {
            //    ELSE IF WK-PBRNO-1 = 18
            //      MOVE " 花蓮分行 "   TO SD-PBRNAME
            pbrName = " 花蓮分行 ";
        } else if (pbrno == 22) {
            //    ELSE IF WK-PBRNO-1 = 22
            //      MOVE " 宜蘭分行 "   TO SD-PBRNAME
            pbrName = " 宜蘭分行 ";
        } else if (pbrno == 23) {
            //    ELSE IF WK-PBRNO-1 = 23
            //      MOVE " 臺東分行 "   TO SD-PBRNAME
            pbrName = " 臺東分行 ";
        } else if (pbrno == 24) {
            //    ELSE IF WK-PBRNO-1 = 24
            //      MOVE " 澎湖分行 "   TO SD-PBRNAME
            pbrName = " 澎湖分行 ";
        } else if (pbrno == 25) {
            //    ELSE IF WK-PBRNO-1 = 25
            //      MOVE " 鳳山分行 "   TO SD-PBRNAME
            pbrName = " 鳳山分行 ";
        } else if (pbrno == 26) {
            //    ELSE IF WK-PBRNO-1 = 26
            //      MOVE " 桃園分行 "   TO SD-PBRNAME
            pbrName = " 桃園分行 ";
        } else if (pbrno == 27) {
            //    ELSE IF WK-PBRNO-1 = 27
            //      MOVE " 板橋分行 "   TO SD-PBRNAME
            pbrName = " 板橋分行 ";
        } else if (pbrno == 28) {
            //    ELSE IF WK-PBRNO-1 = 28
            //      MOVE " 新營分行 "   TO SD-PBRNAME
            pbrName = " 新營分行 ";
        } else if (pbrno == 29) {
            //    ELSE IF WK-PBRNO-1 = 29
            //      MOVE " 苗栗分行 "   TO SD-PBRNAME
            pbrName = " 苗栗分行 ";
        } else if (pbrno == 30) {
            //    ELSE IF WK-PBRNO-1 = 30
            //      MOVE " 豐原分行 "   TO SD-PBRNAME
            pbrName = " 豐原分行 ";
        } else if (pbrno == 31) {
            //    ELSE IF WK-PBRNO-1 = 31
            //      MOVE " 斗六分行 "   TO SD-PBRNAME
            pbrName = " 斗六分行 ";
        } else if (pbrno == 32) {
            //    ELSE IF WK-PBRNO-1 = 32
            //      MOVE " 南投分行 "   TO SD-PBRNAME
            pbrName = " 南投分行 ";
        } else if (pbrno == 42) {
            //    ELSE IF WK-PBRNO-1 = 42
            //      MOVE " 三重分行 "   TO SD-PBRNAME
            pbrName = " 三重分行 ";
        } else if (pbrno == 67) {
            //    ELSE IF WK-PBRNO-1 = 67
            //      MOVE " 太保分行 "   TO SD-PBRNAME
            pbrName = " 太保分行 ";
        } else if (pbrno == 68) {
            //    ELSE IF WK-PBRNO-1 = 68
            //      MOVE " 竹北分行 "   TO SD-PBRNAME
            pbrName = " 竹北分行 ";
        } else if (pbrno == 88) {
            //    ELSE IF WK-PBRNO-1 = 88
            //      MOVE " 潮洲分行 "   TO SD-PBRNAME
            pbrName = " 潮洲分行 ";
        } else {
            //    ELSE
            //      MOVE  SPACES        TO SD-PBRNAME.
            pbrName = " ";
        }
        return pbrName;
    }

    private void log(String col, String text) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), col + "=" + text);
    }
}
