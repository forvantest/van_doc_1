/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV145952;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTFN;
import com.bot.ncl.util.fileVo.FileSumPUTFN;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
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
@Component("CONV145952Lsnr")
@Scope("prototype")
public class CONV145952Lsnr extends BatchListenerCase<CONV145952> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV145952 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFN filePutfn;
    @Autowired private FileSumPUTFN fileSumPutfn;
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private static final String REPORT_NAME = "CL-BH-019";
    private static final String FILE_NAME_ATM = "CONVF-ATM";
    private static final String FILE_NAME_NET = "CONVF-NET";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";

    private StringBuilder sb = new StringBuilder();
    private String wkPutdir;
    private String filePath_Atm;
    private String filePath_Net;
    private String reportFilePath;
    private List<String> fileContents_Atm = new ArrayList<>(); //  檔案內容
    private List<String> fileContents_Net = new ArrayList<>(); //  檔案內容
    private List<String> reportContents = new ArrayList<>(); //  報表內容

    private Map<String, String> labelMap;
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private int nbsdy;

    private String wkYYYMMDD;
    private String wkYYYMM_1;
    private String wkYYMMDD;
    private String wkFiledate;
    private String wkPutfile;

    private int wkAtmTotcnt;
    private BigDecimal wkAtmTotamt = BigDecimal.ZERO;
    private int wkNetTotcnt;
    private BigDecimal wkNetTotamt = BigDecimal.ZERO;
    private int wkTotcnt;
    private BigDecimal wkTotamt = BigDecimal.ZERO;
    private String wkTotcntRpt;
    private String wkTotamtRpt;
    private String wkItemRpt;
    private String wkSubcntRpt;
    private String wkSubamtRpt;
    private String wkTxtype;
    private String wkPbrno;
    private String wkPdate;

    private String _145952_AtmRc;
    private String _145952_NetRc;
    private String _145952_AtmType;
    private String _145952_NetType;
    private String _145952_AtmBkno;
    private String _145952_NetBkno;
    private String _145952_AtmFilename;
    private String _145952_NetFilename;
    private String _145952_AtmActno;
    private String _145952_NetActno;
    private String _145952_AtmBdate;
    private String _145952_AtmEdate;
    private String _145952_NetBdate;
    private String _145952_NetEdate;
    private String _145952_AtmTdate;
    private String _145952_NetTdate;
    private String _145952_NetSupmatBkno;
    private String _145952_AtmTotamt;
    private String _145952_NetTotamt;
    private String _145952_AtmTotcnt;
    private String _145952_NetTotcnt;
    private String _145952_AtmFtotcnt;
    private String _145952_NetFtotcnt;
    private String _145952_AtmFtotamt;
    private String _145952_NetFtotamt;

    private String _145952_AtmFdate;
    private String _145952_AtmRcptid;
    private String _145952_AtmYYYMM;
    private String _145952_AtmChkno;
    private String _145952_AtmAmt;
    private String _145952_AtmFamt;
    private String _145952_AtmSdate;
    private String _145952_AtmDate;
    private String _145952_AtmBkno2;
    private String _145952_AtmPbrno;
    private String _145952_AtmQrcode = "";
    private String _145952_NetFdate;
    private String _145952_NetRcptid;
    private String _145952_NetYYYMM;
    private String _145952_NetChkno;
    private String _145952_NetAmt;
    private String _145952_NetFamt;
    private String _145952_NetSdate;
    private String _145952_NetDate;
    private String _145952_NetBkno2;
    private String _145952_NetPbrno;
    private String _145952_NetQrcode;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV145952 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV145952Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV145952 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV145952Lsnr run()");
        init(event);
        //// 若FD-PUTFN檔案存在,執行145952-RTN
        // 019300     IF  ATTRIBUTE  RESIDENT  OF FD-PUTFN     IS = VALUE(TRUE)
        if (textFile.exists(wkPutdir)) {
            // 019400       PERFORM  145952-RTN       THRU   145952-EXIT.
            _145952();
        }

        checkPath();

        batchResponse();
    }

    private void init(CONV145952 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV145952Lsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        nbsdy = parse.string2Integer(labelMap.get("NBSDY")); // 待中菲APPLE提供正確名稱

        //// 設定本營業日、檔名日期變數值
        // 018500     MOVE    FD-BHDATE-TBSDY     TO     WK-YYYMMDD,
        // 018600                                        WK-YYMMDD,
        // 018700                                        WK-FILEDATE.
        wkYYYMMDD = formatUtil.pad9(processDate, 7);
        wkYYYMM_1 = wkYYYMMDD.substring(0, 5);
        wkYYMMDD = wkYYYMMDD.substring(1, 7);
        wkFiledate = wkYYMMDD;
        // 019000* 代收類別 145952

        //// 設定檔名變數值
        // 019100     MOVE       "02C4145952"     TO     WK-PUTFILE.
        wkPutfile = "02C4145952";
        // 019150     DISPLAY "PUTFFILE="WK-PUTDIR.

        //// 設定檔名
        //// WK-PUTDIR="DATA/CL/BH/PUTFN/"+WK-FILEDATE+"/02C4145952."
        // 019200     CHANGE  ATTRIBUTE FILENAME  OF FD-PUTFN  TO WK-PUTDIR.
        String putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "PUTFN"
                        + PATH_SEPARATOR
                        + wkFiledate;
        wkPutdir = putDir + PATH_SEPARATOR + wkPutfile;
        textFile.deleteFile(wkPutdir);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTFN"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        reportFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + REPORT_NAME;
        filePath_Atm =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_NAME_ATM;
        filePath_Net =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_NAME_NET;
    }

    private void _145952() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV145952Lsnr _145952");
        // 020000 145952-RTN.

        //// 開啟檔案
        // 020100     OPEN       INPUT             FD-PUTFN.
        // 020200     OPEN       OUTPUT            FD-145952-ATM.
        // 020300     OPEN       OUTPUT            FD-145952-NET.
        // 020400     OPEN       OUTPUT            REPORTFL.

        //// 搬相關資料到145952-ATM-REC...、145952-NET-REC...
        // 020500     MOVE       SPACES            TO      145952-ATM-REC,
        // 020600                                          145952-NET-REC.
        // 020700     MOVE       "1"               TO      145952-ATM-RC,
        // 020800                                          145952-NET-RC.
        // 020900     MOVE       "401"             TO      145952-ATM-TYPE,
        // 021000                                          145952-NET-TYPE.
        // 021100     MOVE       "004"             TO      145952-ATM-BKNO,
        // 021200                                          145952-NET-BKNO.
        // 021300     MOVE       "MPREMIUM"        TO      145952-ATM-FILENAME.
        // 021400     MOVE       "WPREMIUM"        TO      145952-NET-FILENAME.
        // 021500     MOVE       "00054004052428"  TO      145952-ATM-ACTNO,
        // 021600                                          145952-NET-ACTNO.
        // 021700     MOVE       WK-YYYMMDD        TO      145952-ATM-BDATE,
        // 021800                                          145952-ATM-EDATE,
        // 021900                                          145952-NET-BDATE,
        // 022000                                          145952-NET-EDATE.
        // 022100     MOVE       FD-BHDATE-NBSDY   TO      145952-ATM-TDATE,
        // 022200                                          145952-NET-TDATE.
        // 022300     MOVE       SPACE             TO      145952-NET-SUPMAT-BKNO.
        _145952_AtmRc = "1";
        _145952_NetRc = "1";
        _145952_AtmType = "401";
        _145952_NetType = "401";
        _145952_AtmBkno = "004";
        _145952_NetBkno = "004";
        _145952_AtmFilename = "MPREMIUM";
        _145952_NetFilename = "WPREMIUM";
        _145952_AtmActno = "00054004052428";
        _145952_NetActno = "00054004052428";
        _145952_AtmBdate = wkYYYMMDD;
        _145952_AtmEdate = wkYYYMMDD;
        _145952_NetBdate = wkYYYMMDD;
        _145952_NetEdate = wkYYYMMDD;
        _145952_AtmTdate = formatUtil.pad9("" + nbsdy, 7);
        _145952_NetTdate = formatUtil.pad9("" + nbsdy, 7);
        _145952_NetSupmatBkno = "";

        //// 清變數值
        // 022400     MOVE       0                 TO      WK-ATM-TOTAMT.
        // 022500     MOVE       0                 TO      WK-ATM-TOTCNT.
        // 022600     MOVE       0                 TO      WK-NET-TOTAMT.
        // 022700     MOVE       0                 TO      WK-NET-TOTCNT.
        wkAtmTotamt = BigDecimal.ZERO;
        wkAtmTotcnt = 0;
        wkNetTotamt = BigDecimal.ZERO;
        wkNetTotcnt = 0;

        //// 寫檔FD-145952-ATM、FD-145952-NET(FIRST RECORD)

        // 022800     WRITE      145952-ATM-REC.
        fileContents_Atm.add(_145952_Atm_Rec(_145952_AtmRc));
        // 022900     WRITE      145952-NET-REC.
        fileContents_Net.add(_145952_Net_Rec(_145952_NetRc));

        // 023000 145952-FNEXT.
        //// 循序讀取FD-PUTFN，直到檔尾，跳到145952-FLAST
        // 023100     READ   FD-PUTFN    AT  END  GO TO  145952-FLAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutfn);
            text2VoFormatter.format(detail, fileSumPutfn);
            //// 挑 PUTFN-CTL=11明細資料
            ////  A.執行DATA-INPUT-RTN，INITIAL 145952-ATM-REC、145952-NET-REC
            ////  B.執行FILE-DTL-RTN，搬欄位、寫檔FD-145952-ATM(DETAIL) & FD-145952-NET(DETAIL)
            // 023200     IF  PUTFN-CTL                = 11
            if ("11".equals(filePutfn.getCtl())) {
                // 023300         PERFORM  DATA-INPUT-RTN  THRU DATA-INPUT-RTN
                data_Input();
                // 023400         PERFORM  FILE-DTL-RTN    THRU FILE-DTL-EXIT.
                file_Dtl();
            }
            //// LOOP讀下一筆FD-PUTFN
            // 023900     GO TO      145952-FNEXT.
        }
        // 024000 145952-FLAST.
        //// 執行DATA-INPUT-RTN，INITIAL 145952-ATM-REC、145952-NET-REC
        //// 執行FILE-LAST-RTN，寫檔FD-145952-ATM(LAST RECORD) & FD-145952-NET(LAST RECORD)
        //// 執行RPT-SUM-RTN，寫REPORTFL報表

        // 024020     PERFORM  DATA-INPUT-RTN  THRU DATA-INPUT-RTN
        data_Input();
        // 024040     PERFORM  FILE-LAST-RTN   THRU FILE-LAST-EXIT
        file_Last();
        // 024060     PERFORM  RPT-SUM-RTN     THRU RPT-SUM-EXIT.
        rpt_Sum();

        //// 關檔
        // 024100     CLOSE    FD-PUTFN.
        // 024200     CLOSE    FD-145952-ATM       WITH SAVE.
        //        try {
        //            textFile.writeFileContent(filePath_Atm, fileContents_Atm, CHARSET);
        //        } catch (LogicException e) {
        //            moveErrorResponse(e);
        //        }

        // 024300     CLOSE    FD-145952-NET       WITH SAVE.
        //        try {
        //            textFile.writeFileContent(filePath_Net, fileContents_Net, CHARSET);
        //        } catch (LogicException e) {
        //            moveErrorResponse(e);
        //        }

        // 024400     CLOSE    REPORTFL            WITH SAVE.
        try {
            textFile.writeFileContent(reportFilePath, reportContents, CHARSET_BIG5);
            upload(reportFilePath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 024500 145952-EXIT.
    }

    private void data_Input() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV145952Lsnr data_Input");
        // 026200 DATA-INPUT-RTN.
        // 設定RC="2" 、搬PUTFN-TXTYPE到WK-TXTYPE

        // 026300     MOVE       SPACES          TO      145952-ATM-REC,
        // 026400                                        145952-NET-REC.
        // 026500     MOVE       "2"             TO      145952-ATM-RC,
        // 026600                                        145952-NET-RC.
        // 026700     MOVE       PUTFN-TXTYPE    TO      WK-TXTYPE.
        _145952_AtmRc = "2";
        _145952_NetRc = "2";
        wkTxtype = filePutfn.getTxtype();
        // 026800 DATA-INPUT-EXIT.
    }

    private void file_Last() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV145952Lsnr file_Last");
        // 024800 FILE-LAST-RTN.
        //// 搬ATM、NET代收總筆數、金額到145952-ATM-REC... & 145952-NET-REC...

        // 024900     MOVE     SPACES          TO      145952-ATM-REC,
        // 025000                                      145952-NET-REC.
        // 025100     MOVE     "9"             TO      145952-ATM-RC,
        // 025200                                      145952-NET-RC.
        // 025300     MOVE     WK-ATM-TOTAMT   TO      145952-ATM-TOTAMT.
        // 025400     MOVE     WK-NET-TOTAMT   TO      145952-NET-TOTAMT.
        // 025500     MOVE     WK-ATM-TOTCNT   TO      145952-ATM-TOTCNT.
        // 025600     MOVE     WK-NET-TOTCNT   TO      145952-NET-TOTCNT.
        // 025620     MOVE     "00000000"      TO      145952-ATM-FTOTCNT,
        // 025640                                      145952-NET-FTOTCNT.
        // 025660     MOVE     "0000000000"    TO      145952-ATM-FTOTAMT,
        // 025680                                      145952-NET-FTOTAMT.
        _145952_AtmRc = "9";
        _145952_NetRc = "9";
        _145952_AtmTotamt = "" + wkAtmTotamt;
        _145952_NetTotamt = "" + wkNetTotamt;
        _145952_AtmTotcnt = "" + wkAtmTotcnt;
        _145952_NetTotcnt = "" + wkNetTotcnt;
        _145952_AtmFtotcnt = "00000000";
        _145952_NetFtotcnt = "00000000";
        _145952_AtmFtotamt = "0000000000";
        _145952_NetFtotamt = "0000000000";

        //// 寫檔FD-145952-ATM,FD-145952-NET(LAST RECORD)
        // 025700     WRITE    145952-ATM-REC..
        fileContents_Atm.add(_145952_Atm_Rec(_145952_AtmRc));
        // 025800     WRITE    145952-NET-REC
        fileContents_Net.add(_145952_Net_Rec(_145952_NetRc));
        // 025900 FILE-LAST-EXIT.
    }

    private void rpt_Sum() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV145952Lsnr rpt_Sum");
        // 030700 RPT-SUM-RTN.
        //// 寫REPORTFL報表

        // 030800     MOVE       SPACES            TO      REPORT-LINE.
        // 030900     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE1.
        reportContents.add(wk_Title_Line_1());
        // 031000     MOVE       SPACES            TO      REPORT-LINE.
        // 031100     WRITE      REPORT-LINE       AFTER   1.
        reportContents.add("");

        // 031200     MOVE       054               TO      WK-PBRNO.
        // 031300     MOVE       WK-YYMMDD         TO      WK-PDATE.
        wkPbrno = "054";
        wkPdate = wkYYMMDD;
        // 031400     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE2.
        reportContents.add(wk_Title_Line_2());

        // 031500     MOVE       SPACES            TO      REPORT-LINE.
        // 031600     WRITE      REPORT-LINE       AFTER   1.
        // 031700     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE3.
        reportContents.add("");
        reportContents.add(wk_Title_Line_3());

        // 031800     MOVE       SPACES            TO      REPORT-LINE.
        // 031900     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        reportContents.add(wk_Title_Line_4());

        // 032000     MOVE       SPACES            TO      REPORT-LINE.
        // 032100     WRITE      REPORT-LINE       FROM    WK-GATE-LINE.
        reportContents.add(wk_Gate_Line());

        // 032200     MOVE       SPACES            TO      REPORT-LINE.
        // 032300     PERFORM    RPT-ATM-RTN       THRU    RPT-ATM-EXIT.
        rpt_Atm_Rtn();

        // 032400     WRITE      REPORT-LINE       FROM    WK-DETAIL-LINE.
        reportContents.add(wk_Detail_Line());

        // 032500     MOVE       SPACES            TO      REPORT-LINE.
        // 032600     PERFORM    RPT-NET-RTN       THRU    RPT-NET-EXIT.
        rpt_Net_Rtn();

        // 032700     WRITE      REPORT-LINE       FROM    WK-DETAIL-LINE.
        reportContents.add(wk_Detail_Line());

        // 032800     MOVE       SPACES            TO      REPORT-LINE.
        // 032900     WRITE      REPORT-LINE       FROM    WK-GATE-LINE.
        reportContents.add(wk_Gate_Line());

        // 033000     ADD        WK-ATM-TOTAMT     TO      WK-TOTAMT.
        // 033100     ADD        WK-NET-TOTAMT     TO      WK-TOTAMT.
        // 033200     ADD        WK-ATM-TOTCNT     TO      WK-TOTCNT.
        // 033300     ADD        WK-NET-TOTCNT     TO      WK-TOTCNT.
        // 033400     MOVE       WK-TOTCNT         TO      WK-TOTCNT-RPT.
        // 033500     MOVE       WK-TOTAMT         TO      WK-TOTAMT-RPT.
        wkTotamt = wkTotamt.add(wkAtmTotamt).add(wkNetTotamt);
        wkTotcnt = wkTotcnt + wkAtmTotcnt + wkNetTotcnt;
        wkTotcntRpt = "" + wkTotcnt;
        wkTotamtRpt = "" + wkTotamt;

        // 033600     WRITE      REPORT-LINE       FROM    WK-TOTAL-LINE.
        reportContents.add(wk_Total_Line());

        // 033700 RPT-SUM-EXIT.
    }

    private void rpt_Atm_Rtn() {
        // 034000 RPT-ATM-RTN.
        //// 搬ATM代收總筆數、金額到WK-DETAIL-LINE

        // 034100     MOVE       " ＡＴＭ "        TO      WK-ITEM-RPT.
        // 034200     MOVE       WK-ATM-TOTCNT     TO      WK-SUBCNT-RPT.
        // 034300     MOVE       WK-ATM-TOTAMT     TO      WK-SUBAMT-RPT.
        wkItemRpt = " ＡＴＭ ";
        wkSubcntRpt = "" + wkAtmTotcnt;
        wkSubamtRpt = "" + wkAtmTotamt;
        // 034400 RPT-ATM-EXIT.
    }

    private void rpt_Net_Rtn() {
        // 034700 RPT-NET-RTN.
        //
        //// 搬NET代收總筆數、金額到WK-DETAIL-LINE
        //
        // 034800     MOVE       " 網際網路 "      TO      WK-ITEM-RPT.
        // 034900     MOVE       WK-NET-TOTCNT     TO      WK-SUBCNT-RPT.
        // 035000     MOVE       WK-NET-TOTAMT     TO      WK-SUBAMT-RPT.
        wkItemRpt = " 網際網路 ";
        wkSubcntRpt = "" + wkNetTotcnt;
        wkSubamtRpt = "" + wkNetTotamt;
        // 035100 RPT-NET-EXIT.
    }

    private void file_Dtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV145952Lsnr file_Dtl");
        // 027100 FILE-DTL-RTN.
        // 027200**************************************************************
        // 027300*      若非電子銀行管道則視同為 ATM 繳款
        // 027400*      因有時主辦行或清算針對個別交易須以人工方式處理
        // 027500**************************************************************

        //// 若WK-TXTYPE = "A" OR ( PUTFN-CLLBR = 054 AND (WK-TXTYPE  = "C" OR "M" ))
        //// 寫ATM媒體檔明細
        //// 否則寫NET媒體檔明細

        //// 搬PUTFN-REC...到145952-ATM-REC or 145952-NET-REC
        // 027600     IF         WK-TXTYPE       =       "A"
        // 027650     OR ( PUTFN-CLLBR = 054 AND (WK-TXTYPE  = "C" OR "M" ))
        if ("A".equals(wkTxtype)
                || ("054".equals(filePutfn.getCllbr())
                        && ("C".equals(wkTxtype) || "M".equals(wkTxtype)))) {
            // 027700        ADD     1               TO      WK-ATM-TOTCNT
            // 027800        ADD     PUTFN-AMT       TO      WK-ATM-TOTAMT
            // 027900        MOVE    0000000         TO      145952-ATM-FDATE
            // 028000        MOVE    PUTFN-RCPTID    TO      145952-ATM-RCPTID
            // 028100        MOVE    WK-YYYMM-1      TO      145952-ATM-YYYMM
            // 028200        MOVE    SPACE           TO      145952-ATM-CHKNO
            // 028300        MOVE    PUTFN-AMT       TO      145952-ATM-AMT
            // 028400        MOVE    "0000000"       TO      145952-ATM-FAMT
            // 028500        MOVE    PUTFN-SITDATE   TO      145952-ATM-SDATE
            // 028600        MOVE    PUTFN-DATE      TO      145952-ATM-DATE
            // 028700        MOVE    "004"           TO      145952-ATM-BKNO2
            // 028800        MOVE    SPACE           TO      145952-ATM-PBRNO
            wkAtmTotcnt = wkAtmTotcnt + 1;
            wkAtmTotamt = wkAtmTotamt.add(new BigDecimal(filePutfn.getAmt()));
            _145952_AtmFdate = "0000000";
            _145952_AtmRcptid = filePutfn.getRcptid();
            _145952_AtmYYYMM = wkYYYMM_1;
            _145952_AtmChkno = "";
            _145952_AtmAmt = filePutfn.getAmt();
            _145952_AtmFamt = "0000000";
            _145952_AtmSdate = filePutfn.getSitdate();
            _145952_AtmDate = filePutfn.getEntdy();
            _145952_AtmBkno2 = "004";
            _145952_AtmPbrno = "";

            // 028900        WRITE   145952-ATM-REC
            fileContents_Atm.add(_145952_Atm_Rec(_145952_AtmRc));
        } else {
            // 029000     ELSE
            // 029100        ADD     1               TO      WK-NET-TOTCNT
            // 029200        ADD     PUTFN-AMT       TO      WK-NET-TOTAMT
            // 029300        MOVE    0000000         TO      145952-NET-FDATE
            // 029400        MOVE    PUTFN-RCPTID    TO      145952-NET-RCPTID
            // 029500        MOVE    WK-YYYMM-1      TO      145952-NET-YYYMM
            // 029600        MOVE    SPACE           TO      145952-NET-CHKNO
            // 029700        MOVE    PUTFN-AMT       TO      145952-NET-AMT
            // 029800        MOVE    "0000000"       TO      145952-NET-FAMT
            // 029900        MOVE    PUTFN-SITDATE   TO      145952-NET-SDATE
            // 030000        MOVE    PUTFN-DATE      TO      145952-NET-DATE
            // 030100        MOVE    "004"           TO      145952-NET-BKNO2
            wkNetTotcnt = wkNetTotcnt + 1;
            wkNetTotamt = wkNetTotamt.add(new BigDecimal(filePutfn.getAmt()));
            _145952_NetFdate = "0000000";
            _145952_NetRcptid = filePutfn.getRcptid();
            _145952_NetYYYMM = wkYYYMM_1;
            _145952_NetChkno = "";
            _145952_NetAmt = filePutfn.getAmt();
            _145952_NetFamt = "0000000";
            _145952_NetSdate = filePutfn.getSitdate();
            _145952_NetDate = filePutfn.getEntdy();
            _145952_NetBkno2 = "004";

            // 030120        IF      WK-TXTYPE = "J" OR "F"
            if ("J".equals(wkTxtype) || "F".equals(wkTxtype)) {
                // 030140            MOVE  WK-TXTYPE     TO      145952-NET-QRCODE
                _145952_NetQrcode = wkTxtype;
                // 030160        END-IF
            }
            // 030200        MOVE    SPACE           TO      145952-NET-PBRNO
            _145952_NetPbrno = "";
            // 030300        WRITE   145952-NET-REC.
            fileContents_Net.add(_145952_Net_Rec(_145952_NetRc));
        }
        // 030400 FILE-DTL-EXIT.
    }

    private String wk_Title_Line_1() {
        // 013300 01 WK-TITLE-LINE1.
        // 013400    02 FILLER                       PIC X(11) VALUE SPACE.
        // 013500    02 TITLE-LABEL                  PIC X(46)
        // 013600       VALUE " ATM 及網際網路代收全民健康保險費之款項統計表 ".
        // 013700    02 FILLER                       PIC X(11) VALUE SPACE.
        // 013800    02 FILLER                       PIC X(12)
        // 013900                              VALUE "FORM : C019 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 11));
        sb.append(formatUtil.padX(" ATM 及網際網路代收全民健康保險費之款項統計表 ", 46));
        sb.append(formatUtil.padX("", 11));
        sb.append(formatUtil.padX("FORM : C019 ", 12));
        return sb.toString();
    }

    private String wk_Title_Line_2() {
        // 014000 01 WK-TITLE-LINE2.
        // 014100    02 FILLER                       PIC X(10)
        // 014200                              VALUE " 分行別： ".
        // 014300    02 WK-PBRNO                     PIC 9(03).
        // 014400    02 FILLER                       PIC X(05) VALUE SPACE.
        // 014500    02 FILLER                       PIC X(13)
        // 014600                              VALUE "  印表日期： ".
        // 014700    02 WK-PDATE                     PIC 99/99/99.
        // 014800    02 FILLER                       PIC X(41) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別： ", 10));
        sb.append(formatUtil.pad9(wkPbrno, 3));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX("  印表日期： ", 13));
        sb.append(reportUtil.customFormat(wkPdate, "99/99/99"));
        sb.append(formatUtil.padX("", 41));
        return sb.toString();
    }

    private String wk_Title_Line_3() {
        // 014900 01 WK-TITLE-LINE3.
        // 015000    02 FILLER                       PIC X(08) VALUE SPACE.
        // 015100    02 FILLER                       PIC X(08) VALUE " 項　目 ".
        // 015200    02 FILLER                       PIC X(20) VALUE SPACE.
        // 015300    02 FILLER                       PIC X(06) VALUE " 筆數 ".
        // 015400    02 FILLER                       PIC X(22) VALUE SPACE.
        // 015500    02 FILLER                       PIC X(06) VALUE " 金額 ".
        // 015600    02 FILLER                       PIC X(07) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 項　目 ", 8));
        sb.append(formatUtil.padX("", 20));
        sb.append(formatUtil.padX(" 筆數 ", 6));
        sb.append(formatUtil.padX("", 22));
        sb.append(formatUtil.padX(" 金額 ", 6));
        sb.append(formatUtil.padX("", 7));
        return sb.toString();
    }

    private String wk_Title_Line_4() {
        // 015700 01 WK-TITLE-LINE4.
        // 015800    02 FILLER                       PIC X(80) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 80));
        return sb.toString();
    }

    private String wk_Gate_Line() {
        // 016700 01 WK-GATE-LINE.
        // 016800    02 FILLER                       PIC X(03) VALUE SPACE.
        // 016900    02 FILLER                       PIC X(77) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 3));
        sb.append(reportUtil.makeGate("-", 77));
        return sb.toString();
    }

    private String wk_Detail_Line() {
        // 015900 01 WK-DETAIL-LINE.
        // 016000    02 FILLER                       PIC X(08) VALUE SPACE.
        // 016100    02 WK-ITEM-RPT                  PIC X(10).
        // 016200    02 FILLER                       PIC X(18) VALUE SPACE.
        // 016300    02 WK-SUBCNT-RPT                PIC 9(08).
        // 016400    02 FILLER                       PIC X(15) VALUE SPACE.
        // 016500    02 WK-SUBAMT-RPT                PIC ZZZ,ZZZ,ZZ9.
        // 016600    02 FILLER                       PIC X(10) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(wkItemRpt, 10));
        sb.append(formatUtil.padX("", 18));
        sb.append(formatUtil.padX(wkSubcntRpt, 8));
        sb.append(formatUtil.padX("", 15));
        sb.append(reportUtil.customFormat(wkSubamtRpt, "ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        return sb.toString();
    }

    private String _145952_Atm_Rec(String fg) {
        // 004100  01 145952-ATM-REC.
        // 004200     03 145952-ATM-RC               PIC X(01).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(_145952_AtmRc, 1));
        switch (fg) {
            case "1":
                // 004300     03 145952-ATM-1.
                // 004400        05 145952-ATM-TYPE          PIC X(03).
                // 004500        05 145952-ATM-BKNO          PIC X(03).
                // 004600        05 145952-ATM-FILENAME      PIC X(08).
                // 004700        05 145952-ATM-ACTNO         PIC X(14).
                // 004800        05 145952-ATM-BDATE         PIC 9(07).
                // 004900        05 145952-ATM-EDATE         PIC 9(07).
                // 005000        05 145952-ATM-TDATE         PIC 9(07).
                // 005100        05 145952-ATM-SUPMAT-BKNO   PIC X(03).
                // 005200        05 FILLER                   PIC X(37).
                sb.append(formatUtil.padX(_145952_AtmType, 3));
                sb.append(formatUtil.padX(_145952_AtmBkno, 3));
                sb.append(formatUtil.padX(_145952_AtmFilename, 8));
                sb.append(formatUtil.padX(_145952_AtmActno, 14));
                sb.append(formatUtil.pad9(_145952_AtmBdate, 7));
                sb.append(formatUtil.pad9(_145952_AtmEdate, 7));
                sb.append(formatUtil.pad9(_145952_AtmTdate, 7));
                sb.append(formatUtil.padX(_145952_AtmBkno, 3));
                sb.append(formatUtil.padX("", 37));
                break;
            case "2":
                // 005300     03 145952-ATM-2  REDEFINES  145952-ATM-1.
                // 005400        05 145952-ATM-FDATE         PIC 9(07).
                // 005500        05 145952-ATM-RCPTID        PIC X(16).
                // 005600        05 145952-ATM-YYYMM         PIC 9(05).
                // 005700        05 145952-ATM-CHKNO         PIC X(02).
                // 005800        05 145952-ATM-AMT           PIC 9(11).
                // 005900        05 145952-ATM-FAMT          PIC X(07).
                // 006000        05 145952-ATM-SDATE         PIC 9(07).
                // 006100        05 145952-ATM-DATE          PIC 9(07).
                // 006200        05 145952-ATM-BKNO2         PIC X(03).
                // 006300        05 145952-ATM-PBRNO         PIC X(04).
                // 006320        05 FILLER                   PIC X(09).
                // 006340        05 145952-ATM-QRCODE        PIC X(01).
                // 006400        05 FILLER                   PIC X(10).
                sb.append(formatUtil.pad9(_145952_AtmFdate, 7));
                sb.append(formatUtil.padX(_145952_AtmRcptid, 16));
                sb.append(formatUtil.pad9(_145952_AtmYYYMM, 5));
                sb.append(formatUtil.padX(_145952_AtmChkno, 2));
                sb.append(formatUtil.pad9(_145952_AtmAmt, 11));
                sb.append(formatUtil.padX(_145952_AtmFamt, 7));
                sb.append(formatUtil.pad9(_145952_AtmSdate, 7));
                sb.append(formatUtil.pad9(_145952_AtmDate, 7));
                sb.append(formatUtil.padX(_145952_AtmBkno2, 3));
                sb.append(formatUtil.padX(_145952_AtmPbrno, 4));
                sb.append(formatUtil.padX("", 9));
                sb.append(formatUtil.padX(_145952_AtmQrcode, 1));
                sb.append(formatUtil.padX("", 10));
                break;
            case "9":
                // 006500     03 145952-ATM-3  REDEFINES  145952-ATM-1.
                // 006600        05 145952-ATM-TOTCNT        PIC 9(08).
                // 006700        05 145952-ATM-TOTAMT        PIC 9(14).
                // 006800        05 145952-ATM-FTOTCNT       PIC X(08).
                // 006900        05 145952-ATM-FTOTAMT       PIC X(10).
                // 007000        05 FILLER                   PIC X(49).
                sb.append(formatUtil.pad9(_145952_AtmTotcnt, 8));
                sb.append(formatUtil.pad9(_145952_AtmTotamt, 14));
                sb.append(formatUtil.padX(_145952_AtmFtotcnt, 8));
                sb.append(formatUtil.padX(_145952_AtmFtotamt, 10));
                sb.append(formatUtil.padX("", 49));
                break;
        }
        return sb.toString();
    }

    private String _145952_Net_Rec(String fg) {
        // 007700  01 145952-NET-REC.
        // 007800     03 145952-NET-RC               PIC X(01).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(_145952_AtmRc, 1));
        switch (fg) {
            case "1":
                // 007900     03 145952-NET-1.
                // 008000        05 145952-NET-TYPE          PIC X(03).
                // 008100        05 145952-NET-BKNO          PIC X(03).
                // 008200        05 145952-NET-FILENAME      PIC X(08).
                // 008300        05 145952-NET-ACTNO         PIC X(14).
                // 008400        05 145952-NET-BDATE         PIC 9(07).
                // 008500        05 145952-NET-EDATE         PIC 9(07).
                // 008600        05 145952-NET-TDATE         PIC 9(07).
                // 008700        05 145952-NET-SUPMAT-BKNO   PIC X(03).
                // 008800        05 FILLER                   PIC X(37).
                sb.append(formatUtil.padX(_145952_NetType, 3));
                sb.append(formatUtil.padX(_145952_NetBkno, 3));
                sb.append(formatUtil.padX(_145952_NetFilename, 8));
                sb.append(formatUtil.padX(_145952_NetActno, 14));
                sb.append(formatUtil.pad9(_145952_NetBdate, 7));
                sb.append(formatUtil.pad9(_145952_NetEdate, 7));
                sb.append(formatUtil.pad9(_145952_NetTdate, 7));
                sb.append(formatUtil.padX(_145952_NetSupmatBkno, 3));
                sb.append(formatUtil.padX("", 37));
                break;
            case "2":
                // 008900     03 145952-NET-2  REDEFINES  145952-NET-1.
                // 009000        05 145952-NET-FDATE         PIC 9(07).
                // 009100        05 145952-NET-RCPTID        PIC X(16).
                // 009200        05 145952-NET-YYYMM         PIC 9(05).
                // 009300        05 145952-NET-CHKNO         PIC X(02).
                // 009400        05 145952-NET-AMT           PIC 9(11).
                // 009500        05 145952-NET-FAMT          PIC X(07).
                // 009600        05 145952-NET-SDATE         PIC 9(07).
                // 009700        05 145952-NET-DATE          PIC 9(07).
                // 009800        05 145952-NET-BKNO2         PIC X(03).
                // 009900        05 145952-NET-PBRNO         PIC X(04).
                // 010000        05 FILLER                   PIC X(09).
                // 010020        05 145952-NET-QRCODE        PIC X(01).
                // 010040        05 FILLER                   PIC X(10).
                sb.append(formatUtil.pad9(_145952_NetFdate, 7));
                sb.append(formatUtil.padX(_145952_NetRcptid, 16));
                sb.append(formatUtil.pad9(_145952_NetYYYMM, 5));
                sb.append(formatUtil.padX(_145952_NetChkno, 2));
                sb.append(formatUtil.pad9(_145952_NetAmt, 11));
                sb.append(formatUtil.padX(_145952_NetFamt, 7));
                sb.append(formatUtil.pad9(_145952_NetSdate, 7));
                sb.append(formatUtil.pad9(_145952_NetDate, 7));
                sb.append(formatUtil.padX(_145952_NetBkno2, 3));
                sb.append(formatUtil.padX(_145952_NetPbrno, 4));
                sb.append(formatUtil.padX("", 9));
                sb.append(formatUtil.padX(_145952_NetQrcode, 1));
                sb.append(formatUtil.padX("", 10));
                break;
            case "9":
                // 010100     03 145952-NET-3  REDEFINES  145952-NET-1.
                // 010200        05 145952-NET-TOTCNT        PIC 9(08).
                // 010300        05 145952-NET-TOTAMT        PIC 9(14).
                // 010400        05 145952-NET-FTOTCNT       PIC X(08).
                // 010500        05 145952-NET-FTOTAMT       PIC X(10).
                // 010600        05 FILLER                   PIC X(49).
                sb.append(formatUtil.pad9(_145952_NetTotcnt, 8));
                sb.append(formatUtil.pad9(_145952_NetTotamt, 14));
                sb.append(formatUtil.padX(_145952_NetFtotcnt, 8));
                sb.append(formatUtil.padX(_145952_NetFtotamt, 10));
                sb.append(formatUtil.padX("", 49));
                break;
        }
        return sb.toString();
    }

    private String wk_Total_Line() {
        // 017000 01 WK-TOTAL-LINE.
        // 017100    02 FILLER                       PIC X(08) VALUE SPACE.
        // 017200    02 FILLER                       PIC X(08) VALUE " 總　計 ".
        // 017300    02 FILLER                       PIC X(20) VALUE SPACE.
        // 017400    02 WK-TOTCNT-RPT                PIC 9(08).
        // 017500    02 FILLER                       PIC X(13) VALUE SPACE.
        // 017600    02 WK-TOTAMT-RPT                PIC Z,ZZZ,ZZZ,ZZ9.
        // 017700    02 FILLER                       PIC X(10) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 總　計 ", 8));
        sb.append(formatUtil.padX("", 20));
        sb.append(formatUtil.pad9(wkTotcntRpt, 8));
        sb.append(formatUtil.padX("", 13));
        sb.append(reportUtil.customFormat(wkTotamtRpt, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        return sb.toString();
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void upload(String filePath, String directory1, String directory2) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "upload = {}", filePath);
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + tbsdy + File.separator + "2FSAP";
            if (!directory1.isEmpty()) {
                uploadPath += File.separator + directory1;
            }
            if (!directory2.isEmpty()) {
                uploadPath += File.separator + directory2;
            }
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void checkPath() {
        if (textFile.exists(wkPutdir)) {
            upload(wkPutdir, "", "");
            forFsapATM();
            forFsapNET();
        }
    }

    private void forFsapATM() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        "02C4145952", // 來源檔案名稱(20碼長)
                        "CONVF-ATM", // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV145952", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void forFsapNET() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        "02C4145952", // 來源檔案名稱(20碼長)
                        "CONVF-NET", // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV145952_NET", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private File downloadFromSftp(String fileFtpPath, String tarDir) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "downloadFromSftp fileFtpPath = {}",
                fileFtpPath);
        File file;
        try {
            file = fsapSyncSftpService.downloadFiles(fileFtpPath, tarDir);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "downloadFromSftp error = {}",
                    e.getMessage());
            return null;
        }
        return file;
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
