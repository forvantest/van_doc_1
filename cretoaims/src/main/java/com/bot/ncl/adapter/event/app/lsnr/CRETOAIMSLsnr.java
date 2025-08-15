/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CRETOAIMS;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
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
@Component("CRETOAIMSLsnr")
@Scope("prototype")
public class CRETOAIMSLsnr extends BatchListenerCase<CRETOAIMS> {
    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFilePUTCLMR;
    @Autowired private TextFileUtil textFileAIMSHDR;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private DateUtil dateUtil;
    @Autowired private Parse parse;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmrService clmrService;

    private CltmrBus cltmrBus;

    // 作業日期
    private String processDate;
    private String tbsdy;
    private String crmhdr;
    private CRETOAIMS event;

    private static final String CHARSET = "Big5";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private String filePath = "";
    private String fileNamePUTCLMR = "PUTCLMR";
    private String fileNameAIMSHDR = "AIMSHDR";
    private StringBuilder sb = new StringBuilder();
    private List<String> fileContentsPUTCLMR;
    private List<String> fileContentsAIMSHDR;

    // init
    String clmrCode = "";
    String filler = ",";
    int clmrVRCode = 0;
    int clmrPbrno = 0;
    long clmrActno = 0L;
    String clmrEntpNo = "";

    // *working sortage*/
    int wkNotFound = 0;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CRETOAIMS event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRETOAIMSLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CRETOAIMS event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRETOAIMSLsnr run()");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 002600 FD  FD-BHDATE     COPY "SYM/DP/FD/BHDATE
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        ApLogHelper.info(log, false, LogType.BATCH.getCode(), processDate);
        tbsdy = labelMap.get("PROCESS_DATE");
        fileNamePUTCLMR =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + fileNamePUTCLMR;
        fileNameAIMSHDR =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + fileNameAIMSHDR;

        fileContentsPUTCLMR = new ArrayList<String>();
        fileContentsAIMSHDR = new ArrayList<String>();

        // 002800 FD  FD-CRMHDR     COPY "SYM/DP/FD/CRMHDR."
        // 系統時間 待補
        crmhdr = "";

        List<ClmrBus> tClmr = clmrService.findAll(0, Integer.MAX_VALUE);

        if (tClmr == null) {
            return;
        }
        ApLogHelper.info(log, false, LogType.BATCH.getCode(), "tClmr.size = " + tClmr.size());

        List<ClmrBus> lClmr = new ArrayList<ClmrBus>();

        String tmpCode = "";
        boolean isNotNumber = false;
        for (ClmrBus r : tClmr) {

            tmpCode = r.getCode();

            // 字串中 是否皆為數字
            if (tmpCode.matches("[+-]?\\d*(\\.\\d+)?")) {
                isNotNumber = true;
            } else {
                isNotNumber = false;
            }

            //// 代收類別 小於 "110044" 或 代收類別第一碼 大於 "7"，不處理
            // 019700      IF  DB-CLMR-CODE      < "110044" OR
            // 019800          DB-CLMR-CODE(1:1) > "7"
            // 019900          GO  TO  3000-FNCLMR-RTN.
            if (isNotNumber) {
                int dbCode = parse.string2Integer(tmpCode);
                //                if (dbCode == 0) {
                //                    continue;
                //                }
                if (dbCode < 110044) {
                    continue;
                }

                if (parse.string2Integer((dbCode + "").substring(0, 1)) > 7) {
                    continue;
                }
                lClmr.add(r);
            }
        }
        // code排序
        lClmr.sort(
                (c1, c2) -> {
                    if (parse.string2Integer(c1.getCode()) - parse.string2Integer(c2.getCode())
                            != 0) {
                        return parse.string2Integer(c1.getCode())
                                - parse.string2Integer(c2.getCode());
                    } else {
                        return 0;
                    }
                });
        ApLogHelper.info(log, false, LogType.BATCH.getCode(), "lClmr.size = " + lClmr.size());
        // 初始化
        initClmr();

        int wkCnt = 0;
        // 3000_FNCLMR(){
        // 018000     MOVE 0                        TO   WK-NOTFOUND.
        // 018400     FIND NEXT DB-CLMR-IDX1 OF DB-CLMR-DDS
        // 018500          ON EXCEPTION
        // 018600          IF DMSTATUS(NOTFOUND)
        // 018700             MOVE 1                TO   WK-NOTFOUND
        // 018800             GO TO 3000-EXIT
        // 018900          ELSE
        // 019000             MOVE  SPACES          TO   WC-ERRMSG-REC
        // 019100             MOVE  2               TO   WC-FIND-FLAG
        // 019200             MOVE  2               TO   WC-ERRMSG-TXRSUT
        // 019300             MOVE  "3000MR"        TO   WC-ERRMSG-SUB
        // 019400             MOVE  "CRECLMR"       TO   WC-ERRMSG-MAIN
        // 019500             PERFORM DB99-DMERROR-RTN   THRU DB99-DMERROR-EXIT.
        // 019600
        //
        //// 代收類別 小於 "110044" 或 代收類別第一碼 大於 "7"，不處理
        //
        // 019700      IF  DB-CLMR-CODE      < "110044" OR
        // 019800          DB-CLMR-CODE(1:1) > "7"
        // 019900          GO  TO  3000-FNCLMR-RTN.
        // 020000
        //
        //// WK-IN-CNT累加1(沒用，多餘???)
        //// 執行1000-PUT-RTN，寫檔FD-CLMR(收付類別資料檔)
        //
        // 020100      ADD  1  TO  WK-IN-CNT.
        // 020200      PERFORM 1000-PUT-RTN   THRU  1000-EXIT.
        for (ClmrBus r : lClmr) {
            // 計算筆數
            wkCnt++;
            // 列印CLMR資料
            _FD_CLMR(r);
        }
        // 列印時間
        _CRMHDR(wkCnt);
        ApLogHelper.info(log, false, LogType.BATCH.getCode(), "wkCnt.size = " + wkCnt);
        // 產出檔案
        writeFile();
        batchResponse();
    }

    private void initClmr() {
        // 011600     MOVE SPACES   TO  FD-CLMR-REC.
        // 011700     MOVE ","      TO  FILLER01, FILLER02,
        // 011800                       FILLER03, FILLER04.
        clmrCode = "";
        filler = ",";
        clmrVRCode = 0;
        clmrPbrno = 0;
        clmrActno = 0L;
        clmrEntpNo = "";
    }

    private void _FD_CLMR(ClmrBus r) {
        // 013900 1000-PUT-RTN.
        //// 搬DB-CLMR-...到FD-CLMR-...
        // 014100     MOVE    DB-CLMR-CODE          TO   FD-CLMR-CODE      .
        // 014200     MOVE    DB-CLMR-VRCODE        TO   FD-CLMR-VRCODE    .
        // 014300     MOVE    DB-CLMR-PBRNO         TO   FD-CLMR-PBRNO     .
        // 014400     MOVE    DB-CLMR-ACTNO         TO   FD-CLMR-ACTNO     .
        // 014500     MOVE    DB-CLMR-ENTPNO        TO   FD-CLMR-ENTPNO    .

        clmrCode = r.getCode();
        clmrVRCode = r.getVrcode();
        clmrPbrno = r.getPbrno();
        clmrActno = r.getActno();
        cltmrBus = cltmrService.findById(clmrCode);
        if (Objects.isNull(cltmrBus)) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cltmr is null.");
        } else {
            clmrEntpNo = cltmrBus.getEntpno();
            // 等確定0跟 00000000要調整成一樣， 先都照元DB資料輸出
            //            if ("0".equals(clmrEntpNo.trim())) {
            //                clmrEntpNo = "";
            //            }
        }
        sb = new StringBuilder();
        sb.append(formatUtil.padX(clmrCode, 6));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(clmrVRCode + "", 4));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(clmrPbrno + "", 3));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.pad9(clmrActno + "", 12));
        sb.append(formatUtil.padX(filler, 1));
        sb.append(formatUtil.padX(clmrEntpNo, 10));
        fileContentsPUTCLMR.add(sb.toString());
    }

    private void _CRMHDR(int totCnt) {
        // 01 FD-CRMHDR-REC TOTAL 30 BYTES
        // 03 FD-CRMHDR-SDAY       	9(08)	資料起日	WK-BHDATE-TBSDY8
        // 本營業日(西元日期)
        // 03 FD-CRMHDR-EDAY        9(08)	資料迄日	WK-BHDATE-TBSDY8
        // 本營業日(西元日期)
        // 03 FD-CRMHDR-FLNM      	X(10)	檔名	"DCLMR001.D"
        // 03 FD-CRMHDR-SYSDY    	9(08)	寫FD-CRMHDR時系統日期	WK-SYS-DATE
        // 03 FD-CRMHDR-SYSTM     	9(06)	寫FD-CRMHDR時系統時間	SYS-HHMMSS
        // 03 FD-CRMHDR-TOTCNT  	9(10)	資料明細檔總筆數	WK-CLMR-CNT

        //// 搬相關資料到FD-CRMHDR-...
        //// 每營業日產生者起迄日放西元本營業日期 TBSDY8
        //
        // 015500     MOVE WK-BHDATE-TBSDY8       TO  FD-CRMHDR-SDAY.
        // 015600     MOVE WK-BHDATE-TBSDY8       TO  FD-CRMHDR-EDAY.
        // 015700     MOVE "DCLMR001.D"           TO  FD-CRMHDR-FLNM.
        // 015800**   取得目前系統日期及時間
        // 015900     PERFORM 2100-GET-SYSDTTM-RTN  THRU  2100-EXIT.
        // 016000     MOVE WK-SYS-DATE            TO  FD-CRMHDR-SYSDY.
        // 016100     MOVE SYS-HHMMSS             TO  FD-CRMHDR-SYSTM.
        // 016200     MOVE WK-CLMR-CNT            TO  FD-CRMHDR-TOTCNT.
        // 016300     WRITE FD-CRMHDR-REC.
        // 016400     ADD  1  TO  WK-CRMHDR-CNT.
        String chmhdr_sDay = (parse.string2Integer(processDate) + 19110000) + "";
        String chmhdr_eDay = (parse.string2Integer(processDate) + 19110000) + "";
        String chmhdr_Flmn = "DCLMR001.D";

        //        int t1 = dateUtil.getNowIntegerTime(false);
        //        int t2 = dateUtil.getNowIntegerTime(true);
        //        ApLogHelper.info(log, false, LogType.APLOG.getCode(), "t1: [{}], t2: [{}]", t1,
        // t2);
        String chmhdr_SYSDY = dateUtil.getNowStringBc();
        int chmhdr_SYSTM = dateUtil.getNowIntegerTime(false);

        sb = new StringBuilder();
        sb.append(formatUtil.pad9(chmhdr_sDay, 8));
        sb.append(formatUtil.pad9(chmhdr_eDay, 8));
        sb.append(formatUtil.padX(chmhdr_Flmn, 10));
        sb.append(formatUtil.pad9(chmhdr_SYSDY, 8));
        sb.append(formatUtil.pad9(chmhdr_SYSTM + "", 6));
        sb.append(formatUtil.pad9(totCnt + "", 10));
        fileContentsAIMSHDR.add(sb.toString());
    }

    private void writeFile() {

        textFilePUTCLMR.deleteFile(fileNamePUTCLMR);
        textFileAIMSHDR.deleteFile(fileNameAIMSHDR);

        try {
            textFilePUTCLMR.writeFileContent(fileNamePUTCLMR, fileContentsPUTCLMR, CHARSET);
            upload(fileNamePUTCLMR, "DATA", "");
            textFileAIMSHDR.writeFileContent(fileNameAIMSHDR, fileContentsAIMSHDR, CHARSET);
            upload(fileNameAIMSHDR, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
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

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
