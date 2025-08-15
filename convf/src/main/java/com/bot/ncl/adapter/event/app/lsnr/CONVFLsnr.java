/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONVF;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
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
import java.text.DecimalFormat;
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
@Component("CONVFLsnr")
@Scope("prototype")
public class CONVFLsnr extends BatchListenerCase<CONVF> {

    @Autowired private TextFileUtil textFile;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePutf;
    @Autowired private FileSumPUTF fileSumPUTF;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private static final String CHARSET = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "PUTF"; // 讀檔檔名
    private static final String FILE_NAME = "CONVF"; // 檔名
    private static final String CONVF_DATA = "DATA";
    private String inputFilePath; // 讀檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileCONVF07Z4220003Contents = new ArrayList<>(); // 07Z4220003 檔案內容
    private List<String> fileCONVF07ZC442002Contents = new ArrayList<>(); // 07ZC442002 檔案內容
    private List<String> fileCONVF07Z0742004Contents = new ArrayList<>(); // 07Z0742004 檔案內容
    private List<String> fileCONVF02C8041011Contents = new ArrayList<>(); // 02C8041011 檔案內容
    private List<String> fileCONVF02C5741019Contents = new ArrayList<>(); // 02C5741019 檔案內容
    private String PATH_SEPARATOR = File.separator;
    private CONVF event;
    private String processDate = "";
    private String wkYYMMDD = "";
    private String tbsdy;
    private String wkFdate = "";
    private String wkCdate = "";
    private int wk420045Seqno = 0;
    private int wk420045Idx = 0;
    private String wkPutfile;
    private String wkConvfile;
    private BigDecimal wk420045Sum = BigDecimal.ZERO;
    private BigDecimal wk420045Totsum = BigDecimal.ZERO;
    private BigDecimal wk420045S = BigDecimal.ZERO;
    private BigDecimal wk420045Chk = BigDecimal.ZERO;
    private String wk420045Totchk = "0";
    private String putfCtl = "";
    private String putfCode = "";
    private String putfRcptid = "";
    private String putfUserdata = "";
    private String putfCllbr = "";
    private String putfDate = "";
    private String putfAmt = "";
    private String putfBdate = "";
    private String putfEdate = "";
    private String putfTotcnt = "";
    private String putfTotamt = "";
    private DecimalFormat decimalFormat3 = new DecimalFormat(".00");
    private DecimalFormat decimalFormat16 = new DecimalFormat("0000000000000.00");
    private String convf200037Rc2;
    private String convf200037Bdate;
    private String convf200037Edate;
    private String convf200037Totcnt;
    private String convf200037Totamt;
    private String convf200037Rc1;
    private String convf200037Rcptid;
    private String convf200037Cllbr;
    private String convf200037Date;
    private String convf200037Amt;
    private String convf200037Rc3;
    private String convf200037Code;
    private String convf07ZC442002004T;
    private String convf07ZC442002Date;
    private String convf07ZC442002004;
    private String convf07ZC442002Cllbr;
    private String convf07ZC4420026105;
    private String convf07ZC442002Idx;
    private String convf07ZC442002Sign;
    private String convf07ZC442002Amt;
    private String convf07ZC442002Userdata;
    private String convf07ZC442002004B;
    private String convf07ZC442002Bdate;
    private String convf07ZC442002Edate;
    private String convf07ZC442002Totcnt;
    private String convf07ZC442002Signb;
    private String convf07ZC442002Totamt;
    private String convf420045Flnm;
    private String convf420045Date1;
    private String wk420045Time;
    private String wk420045HH;
    private String wk420045MM;
    private String wk420045SS;
    private String convf420045HH;
    private String convf420045MM;
    private String convf420045SS;
    private String convf420045D1;
    private String convf420045D2;
    private String convf420045Bhno;
    private String convf420045Issu;
    private String convf420045Bkno1;
    private String convf420045Comm1;
    private String convf420045Comm2;
    private String convf420045Comm3;
    private String convf420045Comm4;
    private String convf420045Comm5;
    private String convf420045Comm6;
    private String convf420045Comm7;
    private String convf420045Comm8;
    private String convf420045Invno;
    private int convf420045Seqno;
    private String convf420045Amt;
    private String convf420045Bkno2;
    private String convf420045Cllbr;
    private String convf420045Date2;
    private String convf420045Dot1;
    private String convf420045002;
    private String convf420045Comm9;
    private String convf420045Comm10;
    private String convf420045Comm11;
    private String convf420045Comm12;
    private String convf420045Comm13;
    private String convf420045Comm14;
    private String convf420045Comm15;
    private String convf420045Comm16;
    private BigDecimal convf420045Condig = BigDecimal.ZERO;
    private String convf420045Totcnt;
    private String convf420045Totamt;
    private String convf420045Dot2;
    private String convf420045003;
    private String convf420045Comm17;
    private String convf420045Comm18;
    private String convf420045Comm19;
    private String convf420045Totcondig;
    private String convf410113Stockid;
    private String wk410113UserData;
    private String wk410113Id;
    private String wk410113No;
    private String wk410113Type;
    private String convf410113Id;
    private String convf410113No;
    private String convf410113Type;
    private String convf410113Amt;
    private int convf410113Actstock;
    private String convf410113Date;
    private String convf410113Cllbr;
    private String convf410113Idx;
    private String convf410193Conp;
    private String wk410193Userdata;
    private String wk410193Id;
    private String wk410193No;
    private String convf410193Idno;
    private String convf410193Acno;
    private String convf410193Each;
    private String convf410193Amta;
    private int convf410193Acty;
    private String convf410193Date;
    private String convf410193Acct;
    private int convf410193Idx;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉

    @Override
    public void onApplicationEvent(CONVF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVFLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONVF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVFLsnr run");
        init(event);
        // 030210*200037
        //// WK-PUTDIR <-"DATA/CL/BH/PUTF/" +WK-FDATE 9(06)+"/"+WK-PUTFILE  X(10)+"."
        //// WK-CONVDIR<-"DATA/CL/BH/CONVF/"+WK-CDATE 9(06)+"/"+WK-CONVFILE X(10)+"."
        //// 設定FD-PUTF、FD-CONVF檔名
        //// FD-PUTF檔案存在，
        ////  A.依代收類別找事業單位基本資料檔	<-多餘，未HANDLE，也沒後續欄位的使用
        ////  B.執行200037-RTN a.開檔、b.讀FD-PUTF、寫FD-CONVF、c.關檔
        // 030220     MOVE        "07Z1200037"        TO   WK-PUTFILE,WK-CONVFILE.
        // 030230     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 030240     CHANGE  ATTRIBUTE FILENAME OF FD-CONVF-07Z4220003
        // 030250                                TO WK-CONVDIR.
        // 030260     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        // 030270       FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "200037"
        // 030280       PERFORM    200037-RTN  THRU  200037-EXIT.
        wkPutfile = "07Z1200037";
        wkConvfile = "07Z1200037";
        String convDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate;
        inputFilePath = convDir + PATH_SEPARATOR + wkPutfile;
        textFile.deleteFile(inputFilePath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, convDir);
        if (sourceFile != null) {
            inputFilePath = getLocalPath(sourceFile);
        }

        if (textFile.exists(inputFilePath)) {
            _200037_rtn();
        }
        // 030282*200047
        //// 設定FD-PUTF、FD-CONVF檔名
        //// FD-PUTF檔案存在，
        ////  A.依代收類別找事業單位基本資料檔	<-多餘，未HANDLE，也沒後續欄位的使用
        ////  B.執行200037-RTN a.開檔、b.讀FD-PUTF、寫FD-CONVF、c.關檔
        //
        // 030284     MOVE        "07Z1200047"        TO   WK-PUTFILE,WK-CONVFILE.
        // 030286     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 030288     CHANGE  ATTRIBUTE FILENAME OF FD-CONVF-07Z4220003
        // 030290                                TO WK-CONVDIR.
        // 030292     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        // 030294       FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "200047"
        // 030296       PERFORM    200037-RTN  THRU  200037-EXIT.
        wkPutfile = "07Z1200047";
        wkConvfile = "07Z1200047";
        convDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate;
        inputFilePath = convDir + PATH_SEPARATOR + wkPutfile;
        textFile.deleteFile(inputFilePath);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, convDir);
        if (sourceFile != null) {
            inputFilePath = getLocalPath(sourceFile);
        }
        if (textFile.exists(inputFilePath)) {
            _200037_rtn();
        }
        // 030298*200057
        //// 設定FD-PUTF、FD-CONVF檔名
        //// FD-PUTF檔案存在，
        ////  A.依代收類別找事業單位基本資料檔	<-多餘，未HANDLE，也沒後續欄位的使用
        ////  B.執行200037-RTN a.開檔、b.讀FD-PUTF、寫FD-CONVF、c.關檔
        //
        // 030300     MOVE        "07Z1200057"        TO   WK-PUTFILE,WK-CONVFILE.
        // 030302     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 030304     CHANGE  ATTRIBUTE FILENAME OF FD-CONVF-07Z4220003
        // 030306                                TO WK-CONVDIR.
        // 030308     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        // 030310       FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "200057"
        // 030312       PERFORM    200037-RTN  THRU  200037-EXIT.
        wkPutfile = "07Z1200057";
        wkConvfile = "07Z1200057";
        convDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate;
        inputFilePath = convDir + PATH_SEPARATOR + wkPutfile;

        textFile.deleteFile(inputFilePath);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, convDir);
        if (sourceFile != null) {
            inputFilePath = getLocalPath(sourceFile);
        }
        if (textFile.exists(inputFilePath)) {
            _200037_rtn();
        }
        // 030314*420025
        //
        //// 設定FD-PUTF、FD-CONVF檔名
        //// FD-PUTF檔案存在，
        ////  A.依代收類別找事業單位基本資料檔	<-多餘，未HANDLE，也沒後續欄位的使用
        ////  B.執行07ZC442002-RTN a.開檔、b.讀FD-PUTF、寫FD-CONVF、c.關檔
        //
        // 030316     MOVE        "07Z1420025"        TO   WK-PUTFILE,WK-CONVFILE.
        // 030318     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 030320     CHANGE  ATTRIBUTE FILENAME OF FD-CONVF-07ZC442002
        // 030322                                TO WK-CONVDIR.
        // 030324     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        // 030326       FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "420025"
        // 030328       PERFORM    07ZC442002-RTN  THRU  07ZC442002-EXIT.
        wkPutfile = "07Z1420025";
        wkConvfile = "07Z1420025";
        convDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate;
        inputFilePath = convDir + PATH_SEPARATOR + wkPutfile;

        textFile.deleteFile(inputFilePath);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, convDir);
        if (sourceFile != null) {
            inputFilePath = getLocalPath(sourceFile);
        }
        if (textFile.exists(inputFilePath)) {
            _07ZC442002_rtn();
        }
        // 030330*420185
        //// 設定FD-PUTF、FD-CONVF檔名
        //// FD-PUTF檔案存在，
        ////  A.依代收類別找事業單位基本資料檔	<-多餘，未HANDLE，也沒後續欄位的使用
        ////  B.執行07ZC442002-RTN a.開檔、b.讀FD-PUTF、寫FD-CONVF、c.關檔
        // 030332     MOVE        "07Z1420185"        TO   WK-PUTFILE,WK-CONVFILE.
        // 030334     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 030336     CHANGE  ATTRIBUTE FILENAME OF FD-CONVF-07ZC442002
        // 030338                                TO WK-CONVDIR.
        // 030340     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        // 030342       FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "420185"
        // 030344       PERFORM    07ZC442002-RTN  THRU  07ZC442002-EXIT.
        wkPutfile = "07Z1420185";
        wkConvfile = "07Z1420185";
        convDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate;
        inputFilePath = convDir + PATH_SEPARATOR + wkPutfile;

        textFile.deleteFile(inputFilePath);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, convDir);
        if (sourceFile != null) {
            inputFilePath = getLocalPath(sourceFile);
        }
        if (textFile.exists(inputFilePath)) {
            _07ZC442002_rtn();
        }
        // 030346*420045
        //
        //// 設定FD-PUTF、FD-CONVF檔名
        //// FD-PUTF檔案存在，
        ////  A.依代收類別找事業單位基本資料檔	<-多餘，未HANDLE，也沒後續欄位的使用
        ////  B.執行420045-RTN a.開檔、b.讀FD-PUTF、寫FD-CONVF、c.關檔
        //
        // 030348     MOVE        "07Z1420045"        TO   WK-PUTFILE,WK-CONVFILE.
        // 030350     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 030352     CHANGE  ATTRIBUTE FILENAME OF FD-CONVF-07Z0742004
        // 030354                                TO WK-CONVDIR.
        // 030356     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        // 030360       FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "420048"
        // 030370       PERFORM    420045-RTN      THRU  420045-EXIT.
        wkPutfile = "07Z1420045";
        wkConvfile = "07Z1420045";
        convDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate;
        inputFilePath = convDir + PATH_SEPARATOR + wkPutfile;

        textFile.deleteFile(inputFilePath);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, convDir);
        if (sourceFile != null) {
            inputFilePath = getLocalPath(sourceFile);
        }
        if (textFile.exists(inputFilePath)) {
            _420045_rtn();
        }
        // 030372*420158
        //// 設定FD-PUTF、FD-CONVF檔名
        //// FD-PUTF檔案存在，
        ////  A.依代收類別找事業單位基本資料檔	<-多餘，未HANDLE，也沒後續欄位的使用
        ////  B.執行420045-RTN a.開檔、b.讀FD-PUTF、寫FD-CONVF、c.關檔
        // 030374     MOVE        "07Z1420158"        TO   WK-PUTFILE,WK-CONVFILE.
        // 030376     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 030378     CHANGE  ATTRIBUTE FILENAME OF FD-CONVF-07Z0742004
        // 030380                                TO WK-CONVDIR.
        // 030382     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        // 030384       FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "420158"
        // 030386       PERFORM    420045-RTN      THRU  420045-EXIT.
        wkPutfile = "07Z1420158";
        wkConvfile = "07Z1420158";
        convDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate;
        inputFilePath = convDir + PATH_SEPARATOR + wkPutfile;

        textFile.deleteFile(inputFilePath);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, convDir);
        if (sourceFile != null) {
            inputFilePath = getLocalPath(sourceFile);
        }
        if (textFile.exists(inputFilePath)) {
            _420045_rtn();
        }
        // 030388*410113
        //
        //// 設定FD-PUTF、FD-CONVF檔名
        //// FD-PUTF檔案存在，
        ////  A.依代收類別找事業單位基本資料檔	<-多餘，未HANDLE，也沒後續欄位的使用
        ////  B.執行410113-RTN a.開檔、b.讀FD-PUTF、寫FD-CONVF、c.關檔 ???
        //
        // 030390     MOVE        "02C1410113"        TO   WK-PUTFILE,WK-CONVFILE.
        // 030392     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 030394     CHANGE  ATTRIBUTE FILENAME OF FD-CONVF-02C8041011
        // 030396                                TO WK-CONVDIR.
        // 030398     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        // 030400       FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "410113"
        // 030402       PERFORM    410113-RTN      THRU  410113-EXIT.
        wkPutfile = "02C1410113";
        wkConvfile = "02C1410113";
        convDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate;
        inputFilePath = convDir + PATH_SEPARATOR + wkPutfile;

        textFile.deleteFile(inputFilePath);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, convDir);
        if (sourceFile != null) {
            inputFilePath = getLocalPath(sourceFile);
        }
        if (textFile.exists(inputFilePath)) {
            _410113_rtn();
        }
        // 030404*410193
        //
        //// 設定FD-PUTF、FD-CONVF檔名
        //// FD-PUTF檔案存在，
        ////  A.依代收類別找事業單位基本資料檔	<-多餘，未HANDLE，也沒後續欄位的使用
        ////  B.執行410193-RTN a.開檔、b.讀FD-PUTF、寫FD-CONVF、c.關檔 ???
        //
        // 030406     MOVE        "02C1410193"        TO   WK-PUTFILE,WK-CONVFILE.
        // 030410     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 030420     CHANGE  ATTRIBUTE FILENAME OF FD-CONVF-02C5741019
        // 030430                                TO WK-CONVDIR.
        // 030440     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        // 030450*      FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = "410193"
        // 030460       PERFORM    410193-RTN      THRU  410193-EXIT.
        wkPutfile = "02C1410193";
        wkConvfile = "02C1410193";
        convDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME
                        + PATH_SEPARATOR
                        + wkFdate;
        inputFilePath = convDir + PATH_SEPARATOR + wkPutfile;

        textFile.deleteFile(inputFilePath);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, convDir);
        if (sourceFile != null) {
            inputFilePath = getLocalPath(sourceFile);
        }
        if (textFile.exists(inputFilePath)) {
            _410193_rtn();
        }

        batchResponse();
    }

    private void init(CONVF event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVFLsnr init ...");
        this.event = event;
        // 抓作業日
        //// 讀作業日期檔，設定本營業日變數值；若讀不到，顯示訊息，結束程式
        //
        // 028200     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 028300          STOP RUN.
        // 028400     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD.

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        tbsdy = labelMap.get("PROCESS_DATE");
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        wkYYMMDD = processDate;
        // 設定檔名日期變數值
        // WK-FDATE PIC 9(06) <-WK-PUTDIR'S變數
        // WK-CDATE PIC 9(06) <-WK-CONVDIR'S變數
        // 028500     MOVE        WK-YYMMDD           TO   WK-FDATE,WK-CDATE.
        wkFdate = formatUtil.pad9(wkYYMMDD, 7).substring(1, 7);
        wkCdate = formatUtil.pad9(wkYYMMDD, 7).substring(1, 7);
    }

    private void _200037_rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVFLsnr _200037_rtn ...");
        // 050000 200037-RTN.
        //// 開啟檔案
        //// WK-PUTDIR <-"DATA/CL/BH/PUTF/" +WK-FDATE 9(06)+"/"+WK-PUTFILE  X(10)+"."
        //// WK-CONVDIR<-"DATA/CL/BH/CONVF/"+WK-CDATE 9(06)+"/"+WK-CONVFILE X(10)+"."
        // 052000     OPEN      OUTPUT    FD-CONVF-07Z4220003.
        // 054000     OPEN      INPUT     FD-PUTF.
        // 056000***  DETAIL  RECORD  *****
        // 058000 200037-NEXT.

        //// 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，跳到200037-CLOSE 關檔
        // 060000     READ   FD-PUTF    AT  END  GO TO  200037-CLOSE.

        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            text2VoFormatter.format(detail, fileSumPUTF);
            putfCtl = filePutf.getCtl();
            putfCode = filePutf.getCode();
            putfRcptid = filePutf.getRcptid();
            putfCllbr = filePutf.getCllbr();
            putfDate = filePutf.getEntdy();
            putfBdate = fileSumPUTF.getBdate();
            putfEdate = fileSumPUTF.getEdate();
            putfTotcnt = fileSumPUTF.getTotcnt();
            putfTotamt = fileSumPUTF.getTotamt();
            //// 不是明細資料，跳到200037-LAST寫總數
            // 062000     IF        PUTF-CTL       NOT =      11  AND NOT = 21
            if (!"11".equals(putfCtl) && !"21".equals(putfCtl)) {
                // 064000       GO TO   200037-LAST.
            } else {
                //// 搬PUTF-...到CONVF-...(RC=1)
                // 066000     MOVE      SPACES            TO      CONVF-200037-REC.
                // 070000     MOVE      1                 TO      CONVF-200037-RC-1.
                // 076000     MOVE      PUTF-RCPTID       TO      CONVF-200037-RCPTID.
                // 078000     MOVE      PUTF-CLLBR        TO      CONVF-200037-CLLBR.
                // 080000     MOVE      PUTF-DATE         TO      CONVF-200037-DATE.
                // 082000     MOVE      PUTF-AMT          TO      CONVF-200037-AMT.
                convf200037Rc1 = "1";
                convf200037Rcptid = putfRcptid;
                convf200037Cllbr = putfCllbr;
                convf200037Date = putfDate;
                convf200037Amt = putfAmt;
                //// 寫檔FD-CONVF(明細)
                // 084000     WRITE     CONVF-200037-REC.
                fileCONVF07Z4220003Contents.add(convf_200037_rec(convf200037Rc1));
                //// LOOP讀下一筆FD-PUTF
                // 086000     GO TO     200037-NEXT.
                continue;
            }
            // 088000***  LAST    RECORD  *****
            // 090000 200037-LAST.

            //// 搬PUTF-...到CONVF-...(RC=2)
            // 092000     MOVE      SPACES               TO   CONVF-200037-REC.
            // 096000     MOVE      2                    TO   CONVF-200037-RC-2.
            // 102000     MOVE      PUTF-BDATE           TO   CONVF-200037-BDATE.
            // 104000     MOVE      PUTF-EDATE           TO   CONVF-200037-EDATE.
            // 106000     MOVE      PUTF-TOTCNT          TO   CONVF-200037-TOTCNT.
            // 108000     MOVE      PUTF-TOTAMT          TO   CONVF-200037-TOTAMT.
            convf200037Rc2 = "2";
            convf200037Bdate = putfBdate;
            convf200037Edate = putfEdate;
            convf200037Totcnt = putfTotcnt;
            convf200037Totamt = putfTotamt;
            //// 寫檔FD-CONVF(總數)
            // 110000     WRITE     CONVF-200037-REC.
            fileCONVF07Z4220003Contents.add(convf_200037_rec(convf200037Rc2));
            //// PUTF-CTL=12，LOOP讀下一筆FD-PUTF；否則往下執行
            // 112000     IF        PUTF-CTL              =   12
            if (!"12".equals(putfCtl)) {
                break;
            }
            // 114000       GO TO   200037-NEXT.
        }
        // 116000 200037-CLOSE.
        //// 搬PUTF-...到CONVF-...(RC=3)
        // 116500     MOVE      SPACES               TO   CONVF-200037-REC.
        // 117000     MOVE      3                    TO   CONVF-200037-RC-3.
        // 117500     MOVE      PUTF-CODE            TO   CONVF-200037-CODE.
        convf200037Rc3 = "3";
        convf200037Code = putfCode;
        //// 寫檔FD-CONVF(CODE)
        // 117600     WRITE     CONVF-200037-REC.
        fileCONVF07Z4220003Contents.add(convf_200037_rec(convf200037Rc3));
        ////  關檔
        // 118000     CLOSE     FD-PUTF              WITH  SAVE.
        // 120000     CHANGE ATTRIBUTE FILENAME OF FD-CONVF-07Z4220003
        // 122000                               TO WK-PUTDIR.
        // 124000     CLOSE     FD-CONVF-07Z4220003  WITH  SAVE.
        textFile.deleteFile(inputFilePath);
        try {
            textFile.writeFileContent(inputFilePath, fileCONVF07Z4220003Contents, CHARSET);
            upload(inputFilePath, "DATA", "PUTF");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 126000 200037-EXIT.
    }

    private void _07ZC442002_rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVFLsnr _07ZC442002_rtn ...");
        // 132000 07ZC442002-RTN.
        //
        //// 開啟檔案
        //// WK-PUTDIR <-"DATA/CL/BH/PUTF/" +WK-FDATE 9(06)+"/"+WK-PUTFILE  X(10)+"."
        //// WK-CONVDIR<-"DATA/CL/BH/CONVF/"+WK-CDATE 9(06)+"/"+WK-CONVFILE X(10)+"."
        //
        // 134000     OPEN      OUTPUT    FD-CONVF-07ZC442002.
        // 136000     OPEN      INPUT     FD-PUTF.
        // 138000     MOVE      0                 TO      WK-420025-IDX.
        int wk420025Idx = 0;
        // 140000**** DETAIL  RECORD  *****
        // 142000 07ZC442002-NEXT.
        //
        //// 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，跳到7ZC442002-CLOSE 關檔
        // 144000     READ   FD-PUTF    AT  END  GO TO  07ZC442002-CLOSE.
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            text2VoFormatter.format(detail, fileSumPUTF);
            putfCtl = filePutf.getCtl();
            putfCode = filePutf.getCode();
            putfRcptid = filePutf.getRcptid();
            putfCllbr = filePutf.getCllbr();
            putfDate = filePutf.getEntdy();
            putfUserdata = filePutf.getUserdata();
            putfAmt = filePutf.getAmt();
            putfBdate = fileSumPUTF.getBdate();
            putfEdate = fileSumPUTF.getEdate();
            putfTotcnt = fileSumPUTF.getTotcnt();
            putfTotamt = fileSumPUTF.getTotamt();
            //// 不是明細資料，，跳到07ZC442002-LAST寫總數
            // 146000     IF        PUTF-CTL       NOT =      11  AND NOT = 21
            if (!"11".equals(putfCtl) && !"21".equals(putfCtl)) {
                // 148000       GO TO   07ZC442002-LAST.
            } else {
                //// 搬PUTF-...到CONVF-...(明細)
                // 150000     MOVE      SPACES            TO      CONVF-07ZC442002-REC.
                // 160000     ADD       1                 TO      WK-420025-IDX.
                // 162000     MOVE      00400001          TO      CONVF-07ZC442002-004T.
                // 164000     MOVE      PUTF-DATE         TO      CONVF-07ZC442002-DATE.
                // 166000     MOVE      004               TO      CONVF-07ZC442002-004.
                // 168000     MOVE      PUTF-CLLBR        TO      CONVF-07ZC442002-CLLBR.
                // 170000     MOVE      "6105"            TO      CONVF-07ZC442002-6105.
                // 172000     MOVE      WK-420025-IDX     TO      CONVF-07ZC442002-IDX.
                // 174000     MOVE      "+"               TO      CONVF-07ZC442002-SIGN.
                // 176000     MOVE      PUTF-AMT          TO      CONVF-07ZC442002-AMT.
                // 178000     MOVE      PUTF-USERDATA     TO     CONVF-07ZC442002-USERDATA.
                wk420025Idx = wk420025Idx + 1;
                convf07ZC442002004T = "00400001";
                convf07ZC442002Date = putfDate;
                convf07ZC442002004 = "004";
                convf07ZC442002Cllbr = putfCllbr;
                convf07ZC4420026105 = "6105";
                convf07ZC442002Idx = "" + wk420025Idx;
                convf07ZC442002Sign = "+";
                convf07ZC442002Amt = putfAmt;
                convf07ZC442002Userdata = putfUserdata;
                //// 寫檔FD-CONVF(明細)
                // 180000     WRITE     CONVF-07ZC442002-REC.
                fileCONVF07ZC442002Contents.add(convf_07ZC442002_rec("1"));

                //// LOOP讀下一筆FD-PUTF
                // 182000     GO TO     07ZC442002-NEXT.
                continue;
            }
            // 184000***  LAST    RECORD  *****
            // 186000 07ZC442002-LAST.
            //// 搬PUTF-...到CONVF-...(總數)

            // 188000     MOVE      SPACES               TO   CONVF-07ZC442002-REC.
            // 198000     MOVE      00400001             TO   CONVF-07ZC442002-004B.
            // 199000     MOVE      PUTF-BDATE           TO   CONVF-07ZC442002-BDATE.
            // 200000     MOVE      PUTF-EDATE           TO   CONVF-07ZC442002-EDATE.
            // 202000     MOVE      PUTF-TOTCNT          TO   CONVF-07ZC442002-TOTCNT.
            // 203000     MOVE      "+"                  TO   CONVF-07ZC442002-SIGNB.
            // 204000     MOVE      PUTF-TOTAMT          TO   CONVF-07ZC442002-TOTAMT.
            convf07ZC442002004B = "00400001";
            convf07ZC442002Bdate = putfBdate;
            convf07ZC442002Edate = putfEdate;
            convf07ZC442002Totcnt = putfTotcnt;
            convf07ZC442002Signb = "+";
            convf07ZC442002Totamt = putfTotamt;

            //// 寫檔FD-CONVF(總數)
            // 206000     WRITE     CONVF-07ZC442002-REC.
            fileCONVF07ZC442002Contents.add(convf_07ZC442002_rec("2"));
            //// PUTF-CTL=12 LOOP讀下一筆FD-PUTF；否則往下執行
            //
            // 208000     IF        PUTF-CTL              =   12
            if (!"12".equals(putfCtl)) {
                break;
            }
            // 210000       GO TO   07ZC442002-NEXT.
        }
        // 212000 07ZC442002-CLOSE.
        ////  關檔
        // 214000     CLOSE     FD-PUTF              WITH  SAVE.
        // 216000     CHANGE ATTRIBUTE FILENAME OF FD-CONVF-07ZC442002
        // 218000                               TO WK-PUTDIR.
        // 220000     CLOSE     FD-CONVF-07ZC442002  WITH  SAVE.
        textFile.deleteFile(inputFilePath);
        try {
            textFile.writeFileContent(inputFilePath, fileCONVF07ZC442002Contents, CHARSET);
            upload(inputFilePath, "DATA", "PUTF");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 222000 07ZC442002-EXIT.
    }

    private void _420045_rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVFLsnr _420045_rtn ...");
        // 228000 420045-RTN.
        //
        //// 開啟檔案
        //// WK-PUTDIR <-"DATA/CL/BH/PUTF/" +WK-FDATE 9(06)+"/"+WK-PUTFILE  X(10)+"."
        //// WK-CONVDIR<-"DATA/CL/BH/CONVF/"+WK-CDATE 9(06)+"/"+WK-CONVFILE X(10)+"."
        //
        // 230000     OPEN      OUTPUT    FD-CONVF-07Z0742004.
        // 232000     OPEN      INPUT     FD-PUTF.
        // 234000**** FIRST   RECORD  *****
        // 236000 420045-FIRST.
        // 238000     MOVE      0                 TO      WK-420045-SEQNO,
        // 240000                                         WK-420045-IDX,
        // 242000                                         WK-420045-SUM,
        // 244000                                         WK-420045-TOTSUM.
        wk420045Seqno = 0;
        wk420045Idx = 0;
        wk420045Sum = BigDecimal.ZERO;
        wk420045Totsum = new BigDecimal(0);

        //// 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，跳到420045-CLOSE 關檔
        //
        // 246000     READ   FD-PUTF    AT  END  GO TO  420045-CLOSE.

        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);

        Boolean isFirstDetail = true;
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            text2VoFormatter.format(detail, fileSumPUTF);
            putfCtl = filePutf.getCtl();
            putfDate = filePutf.getEntdy();
            putfUserdata = filePutf.getUserdata();
            putfAmt = filePutf.getAmt();
            putfCllbr = filePutf.getCllbr();
            putfTotcnt = fileSumPUTF.getTotcnt();
            putfTotamt = fileSumPUTF.getTotamt();
            //// 不是明細資料，，跳到420045-LAST寫總數
            // 248000     IF        PUTF-CTL       NOT =      11  AND NOT = 21
            if (!"11".equals(putfCtl) && !"21".equals(putfCtl)) {
                // 250000       GO TO   420045-LAST.
            } else {
                //// 搬相關資料到CONVF-...(FIRST RECORD)
                if (isFirstDetail) {
                    // 252000     MOVE      SPACES            TO      CONVF-420045-REC.
                    // 254000     MOVE      "C0742004.TXT"    TO      CONVF-420045-FLNM.
                    // 256000     MOVE      PUTF-DATE         TO      CONVF-420045-DATE-1.
                    // 258000     ACCEPT    WK-420045-TIME    FROM    TIME.
                    // 258200     MOVE      WK-420045-HH      TO      CONVF-420045-HH.
                    // 258400     MOVE      WK-420045-MM      TO      CONVF-420045-MM.
                    // 258600     MOVE      WK-420045-SS      TO      CONVF-420045-SS.
                    // 258800     MOVE      ":"           TO  CONVF-420045-D1,CONVF-420045-D2.
                    // 260000     MOVE      1                 TO      CONVF-420045-BHNO.
                    // 262000     MOVE      "BANK OF TW"      TO      CONVF-420045-ISSU.
                    // 264000     MOVE      "004"             TO      CONVF-420045-BKNO-1.
                    // 266000     MOVE      ","   TO   CONVF-420045-COMM1, CONVF-420045-COMM2,
                    // 268000                          CONVF-420045-COMM3, CONVF-420045-COMM4,
                    // 270000                          CONVF-420045-COMM5, CONVF-420045-COMM6,
                    // 271000                          CONVF-420045-COMM7, CONVF-420045-COMM8.
                    convf420045Flnm = "C0742004.TXT";
                    convf420045Date1 = putfDate;
                    wk420045Time = dateUtil.getNowStringTime(false);
                    wk420045HH = wk420045Time.substring(0, 2);
                    wk420045MM = wk420045Time.substring(2, 4);
                    wk420045SS = wk420045Time.substring(4, 6);
                    convf420045HH = wk420045HH;
                    convf420045MM = wk420045MM;
                    convf420045SS = wk420045SS;
                    convf420045D1 = ":";
                    convf420045D2 = ":";
                    convf420045Bhno = "1";
                    convf420045Issu = "BANK OF TW";
                    convf420045Bkno1 = "004";
                    convf420045Comm1 = ",";
                    convf420045Comm2 = ",";
                    convf420045Comm3 = ",";
                    convf420045Comm4 = ",";
                    convf420045Comm5 = ",";
                    convf420045Comm6 = ",";
                    convf420045Comm7 = ",";
                    convf420045Comm8 = ",";
                    //// 寫檔FD-CONVF(FIRST RECORD)
                    // 272000     WRITE     CONVF-420045-REC.
                    fileCONVF07Z0742004Contents.add(convf_420045_rec("1"));
                }
                //// 搬PUTF-...到CONVF-...(明細)
                // 276000     MOVE      SPACES            TO      CONVF-420045-REC.
                // 278000     ADD       1                 TO      WK-420045-SEQNO.
                // 280000     MOVE      0                 TO  WK-420045-SUM,WK-420045-IDX.
                // 282000     MOVE      PUTF-USERDATA     TO      CONVF-420045-INVNO.
                // 284000     MOVE      WK-420045-SEQNO   TO      CONVF-420045-SEQNO.
                // 286000     MOVE      PUTF-AMT          TO      CONVF-420045-AMT.
                // 288000     MOVE      004               TO      CONVF-420045-BKNO-2.
                // 290000     MOVE      PUTF-CLLBR        TO      CONVF-420045-CLLBR.
                // 292000     MOVE      PUTF-DATE         TO      CONVF-420045-DATE-2.
                // 294000     MOVE      "."               TO      CONVF-420045-DOT1.
                // 296000     MOVE      0                 TO      CONVF-420045-00-2.
                // 300000     MOVE      ","   TO   CONVF-420045-COMM9, CONVF-420045-COMM10,
                // 302000                          CONVF-420045-COMM11,CONVF-420045-COMM12,
                // 304000                          CONVF-420045-COMM13,CONVF-420045-COMM14,
                // 305000                          CONVF-420045-COMM15,CONVF-420045-COMM16.
                wk420045Sum = BigDecimal.ZERO;
                wk420045Idx = 0;
                wk420045Seqno = wk420045Seqno + 1;
                convf420045Invno = putfUserdata;
                convf420045Seqno = wk420045Seqno;
                convf420045Amt = putfAmt;
                convf420045Bkno2 = "004";
                convf420045Cllbr = putfCllbr;
                convf420045Date2 = putfDate;
                convf420045Dot1 = ".";
                convf420045002 = "0";
                convf420045Comm9 = ",";
                convf420045Comm10 = ",";
                convf420045Comm11 = ",";
                convf420045Comm12 = ",";
                convf420045Comm13 = ",";
                convf420045Comm14 = ",";
                convf420045Comm15 = ",";
                convf420045Comm16 = ",";

                // 306000 420045-LOOP1.
                for (int i = 1; i <= sb.toString().length(); i++) {
                    //// 累加CONVF-420045-REC前101位數值資料至WK-420045-SUM
                    // 308000     ADD       1                 TO      WK-420045-IDX.
                    String text = sb.toString().substring(i - 1, i);
                    // 310000     IF        CONVF-420045-C9(WK-420045-IDX) NUMERIC
                    if (parse.isNumeric(text)) {
                        // 312000       ADD     CONVF-420045-C9(WK-420045-IDX) TO WK-420045-SUM.
                        wk420045Sum = wk420045Sum.add(parse.string2BigDecimal(text));
                    }
                    // 314000     IF        WK-420045-IDX     NOT =   101
                    // 316000       GO TO   420045-LOOP1.
                    if (i == 101) {
                        break;
                    }
                }
                //// WK-420045-SUM除以11，商數放WK-420045-S，餘數放WK-420045-CHK
                // 318000     DIVIDE    WK-420045-SUM  BY  11      GIVING    WK-420045-S
                // 320000                                          REMAINDER WK-420045-CHK.
                BigDecimal[] result = wk420045Sum.divideAndRemainder(new BigDecimal(11));
                wk420045S = result[0];
                wk420045Chk = result[1];
                //// 搬WK-420045-CHK餘數到CONVF-420045-CONDIG
                // 322000     MOVE      WK-420045-CHK     TO       CONVF-420045-CONDIG.
                convf420045Condig = wk420045Chk;
                //// 累加WK-420045-CHK餘數到WK-420045-TOTSUM餘數合計
                // 324000     ADD       WK-420045-CHK     TO       WK-420045-TOTSUM.
                wk420045Totsum = wk420045Totsum.add(wk420045Chk);

                //// 寫檔FD-CONVF(明細)
                // 326000     WRITE     CONVF-420045-REC.
                fileCONVF07Z0742004Contents.add(convf_420045_rec("2"));
                isFirstDetail = false;
                continue;
            }

            // 400000***  LAST    RECORD  *****
            // 402000 420045-LAST.
            //// 搬PUTF-...到CONVF-...(總數)
            // 404000     MOVE      SPACES               TO   CONVF-420045-REC.
            // 414000     MOVE      PUTF-TOTCNT          TO   CONVF-420045-TOTCNT.
            // 416000     MOVE      PUTF-TOTAMT          TO   CONVF-420045-TOTAMT.
            // 418000     MOVE      "."               TO      CONVF-420045-DOT2.
            // 420000     MOVE      0                 TO      CONVF-420045-00-3.
            // 422000     MOVE      ","   TO   CONVF-420045-COMM17,CONVF-420045-COMM18,
            // 424000                          CONVF-420045-COMM19.
            convf420045Totcnt = putfTotcnt;
            convf420045Totamt = putfTotamt;
            convf420045Dot2 = ".";
            convf420045003 = "0";
            convf420045Comm17 = ",";
            convf420045Comm18 = ",";
            convf420045Comm19 = ",";

            //// WK-420045-TOTSUM除以11，商數放WK-420045-S，餘數放WK-420045-TOTCHK
            // 426000     DIVIDE    WK-420045-TOTSUM  BY  11   GIVING WK-420045-S
            // 428000                                       REMAINDER WK-420045-TOTCHK.
            BigDecimal[] result = wk420045Totsum.divideAndRemainder(new BigDecimal(11));
            wk420045S = result[0];
            wk420045Totchk = "" + result[1];

            //// 搬WK-420045-TOTCHK餘數到CONVF-420045-TOTCONDIG
            // 430000     MOVE      WK-420045-TOTCHK  TO       CONVF-420045-TOTCONDIG.
            convf420045Totcondig = wk420045Totchk;

            //// 寫檔FD-CONVF(總數)
            // 432000     WRITE     CONVF-420045-REC.
            fileCONVF07Z0742004Contents.add(convf_420045_rec("3"));
            //// PUTF-CTL=12 LOOP讀下一筆FD-PUTF；否則往下執行
            //
            // 434000     IF        PUTF-CTL              =   12
            if (!"12".equals(putfCtl)) {
                break;
            }
            isFirstDetail = true;
            // 436000       GO TO   420045-FIRST.
        }
        // 438000 420045-CLOSE.
        ////  關檔
        // 440000     CLOSE     FD-PUTF              WITH  SAVE.
        // 442000     CHANGE ATTRIBUTE FILENAME OF FD-CONVF-07Z0742004
        // 444000                               TO WK-PUTDIR.
        // 446000     CLOSE     FD-CONVF-07Z0742004  WITH  SAVE.
        textFile.deleteFile(inputFilePath);
        try {
            textFile.writeFileContent(inputFilePath, fileCONVF07Z0742004Contents, CHARSET);
            upload(inputFilePath, "DATA", "PUTF");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 448000 420045-EXIT.
    }

    private void _410113_rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVFLsnr _410113_rtn ...");
        // 454000 410113-RTN.
        //
        //// 開啟檔案
        //// WK-PUTDIR <-"DATA/CL/BH/PUTF/" +WK-FDATE 9(06)+"/"+WK-PUTFILE  X(10)+"."
        //// WK-CONVDIR<-"DATA/CL/BH/CONVF/"+WK-CDATE 9(06)+"/"+WK-CONVFILE X(10)+"."
        //
        // 456000     OPEN      OUTPUT    FD-CONVF-02C8041011.
        // 458000     OPEN      INPUT     FD-PUTF.
        // 460000     MOVE      0                 TO      WK-410113-IDX.
        // 462000**** DETAIL  RECORD  *****
        // 464000 410113-NEXT.
        //
        //// 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，跳到410113-CLOSE 關檔
        //
        // 466000     READ   FD-PUTF    AT  END  GO TO  410113-CLOSE.

        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        int cnt = 0;
        int wk410113Idx = 0;
        for (String detail : lines) {
            cnt++;
            text2VoFormatter.format(detail, filePutf);
            putfCtl = filePutf.getCtl();
            putfCllbr = filePutf.getCllbr();
            putfDate = filePutf.getEntdy();
            putfAmt = filePutf.getAmt();
            putfUserdata = filePutf.getUserdata();
            //// 不是明細資料，，跳到410113-LAST寫總數
            //
            // 468000     IF        PUTF-CTL       NOT =      11  AND NOT = 21
            if (!"11".equals(putfCtl) && !"21".equals(putfCtl)) {
                // 470000       GO TO   410113-LAST.
            } else {
                //// 搬PUTF-...到CONVF-...(明細)
                // 472000     MOVE      SPACES            TO      CONVF-410113-REC.
                // 474000     ADD       1                 TO      WK-410113-IDX.
                // 476000     MOVE      "6002"            TO      CONVF-410113-STOCKID.
                // 478000     MOVE      PUTF-USERDATA     TO      WK-410113-USERDATA.
                // 480000     MOVE      WK-410113-ID      TO      CONVF-410113-ID.
                // 482000     MOVE      WK-410113-NO      TO      CONVF-410113-NO.
                // 484000     MOVE      WK-410113-TYPE    TO      CONVF-410113-TYPE.
                // 486000     MOVE      PUTF-AMT          TO      CONVF-410113-AMT.
                // 488000     COMPUTE   CONVF-410113-ACTSTOCK = PUTF-AMT / 21.
                // 490000     MOVE      PUTF-DATE         TO      CONVF-410113-DATE.
                // 492000     MOVE      PUTF-CLLBR        TO      CONVF-410113-CLLBR.
                // 494000     MOVE      WK-410113-IDX     TO      CONVF-410113-IDX.
                wk410113Idx = wk410113Idx + 1;
                convf410113Stockid = "6002";
                wk410113UserData = putfUserdata;
                wk410113Id = putfUserdata.substring(0, 10);
                wk410113No = putfUserdata.substring(11, 18);
                wk410113Type = putfUserdata.substring(18, 19);
                convf410113Id = wk410113Id;
                convf410113No = wk410113No;
                convf410113Type = wk410113Type;
                convf410113Amt = putfAmt;
                convf410113Actstock =
                        parse.string2Integer(putfAmt.trim().isEmpty() ? "0" : putfAmt) / 21;
                convf410113Date = putfDate;
                convf410113Cllbr = putfCllbr;
                convf410113Idx = "" + wk410113Idx;
                //// 寫檔FD-CONVF(明細)
                // 496000     WRITE     CONVF-410113-REC.
                fileCONVF02C8041011Contents.add(convf_410113_rec());
                //// LOOP讀下一筆FD-PUTF
                // 498000     GO TO     410113-NEXT.
                continue;
            }
            // 500000 410113-LAST.
            //// PUTF-CTL=12 LOOP讀下一筆FD-PUTF；否則往下執行
            // 502000     IF        PUTF-CTL          =       12
            if (!"12".equals(putfCtl)) {
                break;
            }
            // 504000       MOVE    0                 TO      WK-410113-IDX
            wk410113Idx = 0;
            // 506000       GO TO   410113-NEXT.
        }
        // 508000 410113-CLOSE.
        ////  關檔
        // 510000     CLOSE     FD-PUTF              WITH  SAVE.
        // 512000     CHANGE ATTRIBUTE FILENAME OF FD-CONVF-02C8041011
        // 514000                               TO WK-PUTDIR.
        // 516000     CLOSE     FD-CONVF-02C8041011  WITH  SAVE.
        textFile.deleteFile(inputFilePath);
        try {
            textFile.writeFileContent(inputFilePath, fileCONVF02C8041011Contents, CHARSET);
            upload(inputFilePath, "DATA", "PUTF");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 518000 410113-EXIT.
    }

    private void _410193_rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONVFLsnr _410193_rtn ...");
        // 520100 410193-RTN.
        //
        //// 開啟檔案
        //// WK-PUTDIR <-"DATA/CL/BH/PUTF/" +WK-FDATE 9(06)+"/"+WK-PUTFILE  X(10)+"."
        //// WK-CONVDIR<-"DATA/CL/BH/CONVF/"+WK-CDATE 9(06)+"/"+WK-CONVFILE X(10)+"."
        //
        // 520150     OPEN      OUTPUT    FD-CONVF-02C5741019.
        // 520200     OPEN      INPUT     FD-PUTF.
        // 520250     MOVE      0                 TO      WK-410193-IDX.
        // 520300**** DETAIL  RECORD  *****
        // 520350 410193-NEXT.
        //
        //// 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，跳到410193-CLOSE 關檔
        //
        // 520400     READ   FD-PUTF    AT  END  GO TO  410193-CLOSE.
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        int cnt = 0;
        int wk410193Idx = 0;
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            cnt++;
            putfCtl = filePutf.getCtl();
            putfUserdata = filePutf.getUserdata();
            putfAmt = filePutf.getAmt();
            putfDate = filePutf.getEntdy();
            putfCllbr = filePutf.getCllbr();
            //// 不是明細資料，，跳到410113-LAST寫總數
            // 520450     IF        PUTF-CTL       NOT =      11  AND NOT = 21
            if (!"11".equals(putfCtl) && !"21".equals(putfCtl)) {
                // 520500       GO TO   410193-LAST.
            } else {
                //// 搬PUTF-...到CONVF-...(明細)
                // 520550     MOVE      SPACES            TO      CONVF-410193-REC.
                // 520600     ADD       1                 TO      WK-410193-IDX.
                // 520650     MOVE      "4402"            TO      CONVF-410193-CONP.
                // 520700     MOVE      PUTF-USERDATA     TO      WK-410193-USERDATA.
                // 520750     MOVE      WK-410193-ID      TO      CONVF-410193-IDNO.
                // 520800     MOVE      WK-410193-NO      TO      CONVF-410193-ACNO.
                // 520850     MOVE      15                TO      CONVF-410193-EACH.
                // 520900     MOVE      PUTF-AMT          TO      CONVF-410193-AMTA.
                // 520950     COMPUTE   CONVF-410193-ACTY     = PUTF-AMT / 15.
                // 521000     MOVE      PUTF-DATE         TO      CONVF-410193-DATE.
                // 521050     MOVE      PUTF-CLLBR        TO      CONVF-410193-ACCT.
                // 521100     MOVE      WK-410193-IDX     TO      CONVF-410193-IDX.
                wk410193Idx = wk410193Idx + 1;
                convf410193Conp = "4402";
                wk410193Userdata = putfUserdata;
                wk410193Id = wk410193Userdata.substring(0, 10);
                wk410193No = wk410193Userdata.substring(11, 18);
                convf410193Idno = wk410193Id;
                convf410193Acno = wk410193No;
                convf410193Each = "15";
                convf410193Amta = putfAmt;
                convf410193Acty =
                        parse.string2Integer(putfAmt.trim().isEmpty() ? "0" : putfAmt) / 15;
                convf410193Date = putfDate;
                convf410193Acct = putfCllbr;
                convf410193Idx = wk410193Idx;

                //// 寫檔FD-CONVF(明細)
                // 521150     WRITE     CONVF-410193-REC.
                fileCONVF02C5741019Contents.add(convf_410193_rec());

                //// LOOP讀下一筆FD-PUTF
                // 521200     GO TO     410193-NEXT.
                continue;
            }
            // 521250 410193-LAST.
            //// PUTF-CTL=12 LOOP讀下一筆FD-PUTF；否則往下執行
            // 521300     IF        PUTF-CTL          =       12
            if (!"12".equals(putfCtl)) {
                break;
            }
            // 521350       MOVE    0                 TO      WK-410193-IDX
            wk410193Idx = 0;
            // 521400       GO TO   410193-NEXT.
        }

        // 521450 410193-CLOSE.
        ////  關檔
        // 521500     CLOSE     FD-PUTF              WITH  SAVE.
        // 521550     CHANGE ATTRIBUTE FILENAME OF FD-CONVF-02C5741019
        // 521600                               TO WK-PUTDIR.
        // 521650     CLOSE     FD-CONVF-02C5741019  WITH  SAVE.
        textFile.deleteFile(inputFilePath);
        try {
            textFile.writeFileContent(inputFilePath, fileCONVF02C5741019Contents, CHARSET);
            upload(inputFilePath, "DATA", "PUTF");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 521700 410193-EXIT.
    }

    private void upload(String filePath, String directory1, String directory2) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "upload = {}", filePath);
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath =
                    File.separator + tbsdy + File.separator + "2FSAP" + File.separator + directory1;
            if (!directory2.isEmpty()) {
                uploadPath += File.separator + directory2;
            }
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private String convf_200037_rec(String rcfg) {
        sb = new StringBuilder();
        switch (rcfg) {
            case "1":
                sb.append(formatUtil.padX("" + convf200037Rc1, 1));
                sb.append(formatUtil.padX(convf200037Rcptid, 7));
                sb.append(formatUtil.pad9(convf200037Date, 6));
                sb.append(formatUtil.pad9(convf200037Cllbr, 3));
                sb.append(formatUtil.pad9(convf200037Amt, 6));
                break;
            case "2":
                sb.append(formatUtil.padX(convf200037Rc2, 1));
                sb.append(formatUtil.pad9(convf200037Bdate, 4));
                sb.append(formatUtil.pad9(convf200037Edate, 4));
                sb.append(formatUtil.pad9(convf200037Totcnt, 5));
                sb.append(formatUtil.pad9(convf200037Totamt, 9));
                break;
            case "3":
                sb.append(formatUtil.padX(convf200037Rc3, 1));
                sb.append(formatUtil.padX(convf200037Code, 6));
                sb.append(formatUtil.padX("", 16));
        }
        return sb.toString();
    }

    private String convf_07ZC442002_rec(String rcfg) {
        sb = new StringBuilder();
        switch (rcfg) {
            case "1":
                sb.append(formatUtil.pad9(convf07ZC442002004T, 8));
                sb.append(formatUtil.pad9(convf07ZC442002Date, 6));
                sb.append(formatUtil.pad9(convf07ZC442002004, 3));
                sb.append(formatUtil.pad9(convf07ZC442002Cllbr, 3));
                sb.append(formatUtil.padX(convf07ZC4420026105, 4));
                sb.append(formatUtil.pad9(convf07ZC442002Idx, 7));
                sb.append(formatUtil.padX(convf07ZC442002Sign, 1));
                sb.append(formatUtil.pad9(convf07ZC442002Amt, 11));
                sb.append(formatUtil.padX(convf07ZC442002Userdata, 24));
                sb.append(formatUtil.padX("", 13));
                break;
            case "2":
                sb.append(formatUtil.pad9(convf07ZC442002004B, 8));
                sb.append(formatUtil.pad9(convf07ZC442002Bdate, 6));
                sb.append(formatUtil.pad9(convf07ZC442002Edate, 6));
                sb.append(formatUtil.padX("", 4));
                sb.append(formatUtil.pad9(convf07ZC442002Totcnt, 7));
                sb.append(formatUtil.pad9(convf07ZC442002Signb, 1));
                sb.append(formatUtil.padX("", 11));
                sb.append(formatUtil.pad9(convf07ZC442002Totamt, 9));
                sb.append(formatUtil.padX("", 28));
                break;
        }
        return sb.toString();
    }

    private String convf_420045_rec(String rcfg) {
        sb = new StringBuilder();
        switch (rcfg) {
            case "1":
                sb.append(formatUtil.padX(convf420045Flnm, 12));
                sb.append(formatUtil.padX(convf420045Comm1, 1));
                sb.append(formatUtil.pad9(convf420045Date1, 8));
                sb.append(formatUtil.padX(convf420045Comm2, 1));
                sb.append(formatUtil.pad9(convf420045HH, 2));
                sb.append(formatUtil.padX(convf420045D1, 1));
                sb.append(formatUtil.pad9(convf420045MM, 2));
                sb.append(formatUtil.padX(convf420045D2, 1));
                sb.append(formatUtil.pad9(convf420045SS, 2));
                sb.append(formatUtil.padX(convf420045Comm3, 1));
                sb.append(formatUtil.pad9(convf420045Bhno, 10));
                sb.append(formatUtil.padX(convf420045Comm4, 1));
                sb.append(formatUtil.padX(convf420045Issu, 10));
                sb.append(formatUtil.padX(convf420045Comm5, 1));
                sb.append(formatUtil.pad9(convf420045Bkno1, 10));
                sb.append(formatUtil.padX(convf420045Comm6, 1));
                sb.append(formatUtil.padX("", 20));
                sb.append(formatUtil.padX(convf420045Comm7, 1));
                sb.append(formatUtil.padX("", 20));
                sb.append(formatUtil.padX(convf420045Comm8, 1));
                break;
            case "2":
                // 011418     03  CONVF-420045-2  REDEFINES   CONVF-420045-1.
                sb.append(formatUtil.pad9("" + convf420045Seqno, 8));
                sb.append(formatUtil.padX(convf420045Comm9, 1));
                sb.append(formatUtil.padX(convf420045Invno, 14));
                sb.append(formatUtil.padX(convf420045Comm10, 1));
                sb.append(formatUtil.pad9(convf420045Amt, 12));
                sb.append(formatUtil.padX(convf420045Dot1, 1));
                sb.append(formatUtil.pad9(convf420045002, 2));
                sb.append(formatUtil.padX(convf420045Comm11, 1));
                sb.append(formatUtil.padX(convf420045Bkno2, 3));
                sb.append(formatUtil.padX(convf420045Cllbr, 5));
                sb.append(formatUtil.padX(convf420045Comm12, 1));
                sb.append(formatUtil.pad9(convf420045Date2, 8));
                sb.append(formatUtil.padX(convf420045Comm13, 1));
                sb.append(formatUtil.padX("", 20));
                sb.append(formatUtil.padX(convf420045Comm14, 1));
                sb.append(formatUtil.padX("", 20));
                sb.append(formatUtil.padX(convf420045Comm15, 1));
                sb.append(formatUtil.pad9("" + convf420045Condig, 1));
                sb.append(formatUtil.padX(convf420045Comm16, 1));
                break;
            case "3":
                sb.append(formatUtil.pad9(convf420045Totcnt, 10));
                sb.append(formatUtil.padX(convf420045Comm17, 1));
                sb.append(formatUtil.pad9(convf420045Totamt, 12));
                sb.append(formatUtil.padX(convf420045Dot2, 1));
                sb.append(formatUtil.pad9(convf420045003, 2));
                sb.append(formatUtil.padX(convf420045Comm18, 1));
                sb.append(formatUtil.padX(convf420045Totcondig, 1));
                sb.append(formatUtil.padX(convf420045Comm19, 1));
                sb.append(formatUtil.padX("", 77));
                break;
        }
        return sb.toString();
    }

    private String convf_410113_rec() {

        sb = new StringBuilder();
        sb.append(formatUtil.padX(convf410113Idx, 6));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(convf410113Stockid, 6));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(convf410113Id, 10));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(convf410113No, 7));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(convf410113Type, 1));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.pad9(convf410113Amt, 8));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.pad9("" + convf410113Actstock, 10));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(convf410113Date, 6));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(convf410113Cllbr, 3));
        return sb.toString();
    }

    private String convf_410193_rec() {

        sb = new StringBuilder();
        sb.append(formatUtil.padX(convf410193Conp, 5));
        sb.append(formatUtil.padX(convf410193Acno, 8));
        sb.append(formatUtil.padX(convf410193Idno, 10));

        sb.append(decimalFormat3.format(convf410193Each)); // TODO:V99
        sb.append(decimalFormat16.format(0)); // TODO:V99
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.pad9(convf410193Date, 6));
        sb.append(formatUtil.pad9(convf410193Acct, 3));
        sb.append(formatUtil.pad9("" + convf410193Acty, 10));
        sb.append(decimalFormat16.format(convf410193Amta)); // TODO:V99
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.pad9("" + convf410193Idx, 5));
        sb.append(formatUtil.padX("", 16));
        return sb.toString();
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
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
