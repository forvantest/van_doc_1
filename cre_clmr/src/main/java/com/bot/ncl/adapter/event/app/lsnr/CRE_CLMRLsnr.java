/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CRE_CLMR;
import com.bot.ncl.jpa.anq.entities.impl.CreClmr;
import com.bot.ncl.jpa.anq.svc.CreClmrService;
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
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CRE_CLMRLsnr")
@Scope("prototype")
public class CRE_CLMRLsnr extends BatchListenerCase<CRE_CLMR> {

    @Autowired private CreClmrService creClmrService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private DateUtil dateUtil;
    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String FILE_NAME = "CRE_CLMR";

    private static final String CHARSET = "Big5";

    private List<String> fileContents;

    private List<CreClmr> clmrBusList;
    private Map<String, String> textMap;
    private String batchDate;
    private String filePath;
    private String code;
    private String pbrno;
    private String vrcode;
    private String riddup;
    private String dupcyc;
    private String actno;
    private String msg1;
    private String puttime;
    private String subfg;
    private String chktype;
    private String chkamt;
    private String unit;
    private String amtcyc;
    private String amtfg;
    private String amt;
    private String cname;
    private String stop;
    private String holdcnt;
    private String holdcnt2;
    private String afcbv;
    private String netinfo;
    private String print;
    private String stdate;
    private String sttime;
    private String stopdate;
    private String stoptime;
    private String crdb;
    private String hcode;
    private String lkcode;
    private String atmcode;
    private String entpno;
    private String hentpno;
    private String scname;
    private String cdata;
    private String appdt;
    private String upddt;
    private String lputdt;
    private String llputdt;
    private String ulputdt;
    private String ullputdt;
    private String prtype;
    private String clsacno;
    private String clssbno;
    private String clsdtlno;
    private String ebtype;
    private String pwtype;
    private String feename;
    private String putsend;
    private String putform;
    private String putname;
    private String puttype;
    private String putaddr;
    private String put_encode;
    private String put_compress;
    private String oputtime;
    private String cyck1;
    private String cycno1;
    private String cyck2;
    private String cycno2;
    private String putdtfg;
    private String putdt;
    private String msg2;
    private String tputdt;
    private String usecnt;
    private String cfee1;
    private String cfee2;
    private String cfee3;
    private String cfee4;
    private String fkd;
    private String mfee;
    private String cfeeeb;

    @Override
    public void onApplicationEvent(CRE_CLMR event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRE_CLMRLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CRE_CLMR event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRE_CLMRLsnr run()");
        init(event);
        validateClmr();
        writeFile();
        createReportFile();
        batchResponse(event);
    }

    private void init(CRE_CLMR event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRE_CLMR init");
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        if (Objects.isNull(textMap)) {
            textMap = new HashMap<>();
        }
        batchDate = formatUtil.pad9(labelMap.get("BBSDY"), 8, 0); // 分行別

        filePath = fileDir + FILE_NAME;
        textFile.deleteFile(filePath);

        fileContents = new ArrayList<>();
        //  測試用日期
        if (batchDate == null) {
            batchDate = "01120704";
        }

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CRE_CLMR" + batchDate);
    }

    private void validateClmr() {
        clmrBusList = creClmrService.creClmr();
        if (Objects.isNull(clmrBusList) || clmrBusList.isEmpty()) {
            reportNoData();
        } else {
            queryClmr();
        }
    }

    private void reportNoData() {
        fileContents.add(" ");
    }

    private void queryClmr() {
        for (CreClmr clmrBus : clmrBusList) {
            code = clmrBus.getCode();
            pbrno = clmrBus.getPbrno();
            vrcode = clmrBus.getVrcode();
            riddup = clmrBus.getRiddup();
            dupcyc = clmrBus.getDupcyc();
            actno = clmrBus.getActno();
            msg1 = clmrBus.getMsg1();
            puttime = clmrBus.getPuttime();
            subfg = clmrBus.getSubfg();
            chktype = clmrBus.getChktype();
            chkamt = clmrBus.getChkamt();
            BigDecimal wkUnit = parse.string2BigDecimal(clmrBus.getUnit());
            unit = parse.decimal2String(wkUnit, 8, 2);
            amtcyc = clmrBus.getAmtcyc();
            amtfg = clmrBus.getAmtfg();
            BigDecimal wkAmt = parse.string2BigDecimal(clmrBus.getAmt());
            amt = parse.decimal2String(wkAmt, 15, 2);
            cname = clmrBus.getCname();
            stop = clmrBus.getStop();
            holdcnt = clmrBus.getHoldcnt();
            holdcnt2 = clmrBus.getHoldcnt2();
            afcbv = clmrBus.getAfcbv();
            netinfo = clmrBus.getNetinfo();
            print = clmrBus.getPrint();
            stdate = clmrBus.getStdate();
            sttime = clmrBus.getSttime();
            stopdate = clmrBus.getStopdate();
            stoptime = clmrBus.getStoptime();
            crdb = clmrBus.getCrdb();
            hcode = clmrBus.getHcode();
            lkcode = clmrBus.getLkcode();
            atmcode = clmrBus.getAtmcode();
            entpno = clmrBus.getEntpno();
            hentpno = clmrBus.getHentpno();
            scname = clmrBus.getScname();
            cdata = clmrBus.getCdata();
            appdt = clmrBus.getAppdt();
            upddt = clmrBus.getUpddt();
            lputdt = clmrBus.getLputdt();
            llputdt = clmrBus.getLlputdt();
            ulputdt = clmrBus.getUlputdt();
            ullputdt = clmrBus.getUllputdt();
            prtype = clmrBus.getPrtype();
            clsacno = clmrBus.getClsacno();
            clssbno = clmrBus.getClssbno();
            clsdtlno = clmrBus.getClsdtlno();
            ebtype = clmrBus.getEbtype();
            pwtype = clmrBus.getPwtype();
            feename = clmrBus.getFeename();
            putsend = clmrBus.getPutsend();
            putform = clmrBus.getPutform();
            putname = clmrBus.getPutname();
            puttype = clmrBus.getPuttype();
            putaddr = clmrBus.getPutaddr();
            put_encode = clmrBus.getPut_encode();
            put_compress = clmrBus.getPut_compress();
            oputtime = clmrBus.getOputtime();
            cyck1 = clmrBus.getCyck1();
            cycno1 = clmrBus.getCycno1();
            cyck2 = clmrBus.getCyck2();
            cycno2 = clmrBus.getCycno2();
            putdtfg = clmrBus.getPutdtfg();
            putdt = clmrBus.getPutdt();
            msg2 = clmrBus.getMsg2();
            tputdt = clmrBus.getTputdt();
            usecnt = clmrBus.getUsecnt();
            BigDecimal wkCfee1 = parse.string2BigDecimal(clmrBus.getCfee1());
            cfee1 = parse.decimal2String(wkCfee1, 4, 2);
            BigDecimal wkCfee2 = parse.string2BigDecimal(clmrBus.getCfee2());
            cfee2 = parse.decimal2String(wkCfee2, 3, 2);
            BigDecimal wkCfee3 = parse.string2BigDecimal(clmrBus.getCfee3());
            cfee3 = parse.decimal2String(wkCfee3, 4, 2);
            BigDecimal wkCfee4 = parse.string2BigDecimal(clmrBus.getCfee4());
            cfee4 = parse.decimal2String(wkCfee4, 10, 2);
            fkd = clmrBus.getFkd();
            BigDecimal wkMfee = parse.string2BigDecimal(clmrBus.getMfee());
            mfee = parse.decimal2String(wkMfee, 3, 2);
            BigDecimal wkCfeeeb = parse.string2BigDecimal(clmrBus.getCfeeeb());
            cfeeeb = parse.decimal2String(wkCfeeeb, 3, 2);
            reportFormat();
        }
    }

    private void writeFile() {
        try {
            textFile.writeFileContent(filePath, fileContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void reportFormat() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(formatUtil.padX(code, 6));
        stringBuilder.append(formatUtil.pad9(pbrno, 3));
        stringBuilder.append(formatUtil.pad9(vrcode, 4));
        stringBuilder.append(formatUtil.pad9(riddup, 1));
        stringBuilder.append(formatUtil.pad9(dupcyc, 3));
        stringBuilder.append(formatUtil.pad9(actno, 12));
        stringBuilder.append(formatUtil.pad9(msg1, 1));
        stringBuilder.append(formatUtil.pad9(puttime, 1));
        stringBuilder.append(formatUtil.pad9(subfg, 1));
        stringBuilder.append(formatUtil.padX(chktype, 2));
        stringBuilder.append(formatUtil.pad9(chkamt, 1));
        stringBuilder.append(formatUtil.pad9(unit, 11));
        stringBuilder.append(formatUtil.pad9(amtcyc, 1));
        stringBuilder.append(formatUtil.pad9(amtfg, 1));
        stringBuilder.append(formatUtil.pad9(amt, 18));
        stringBuilder.append(formatUtil.padX(cname, 40));
        stringBuilder.append(formatUtil.pad9(stop, 2));
        stringBuilder.append(formatUtil.pad9(holdcnt, 3));
        stringBuilder.append(formatUtil.pad9(holdcnt2, 3));
        stringBuilder.append(formatUtil.pad9(afcbv, 1));
        stringBuilder.append(formatUtil.padX(netinfo, 20));
        stringBuilder.append(formatUtil.pad9(print, 2));
        stringBuilder.append(formatUtil.pad9(stdate, 8));
        stringBuilder.append(formatUtil.pad9(sttime, 6));
        stringBuilder.append(formatUtil.pad9(stopdate, 8));
        stringBuilder.append(formatUtil.pad9(stoptime, 6));
        stringBuilder.append(formatUtil.pad9(crdb, 1));
        stringBuilder.append(formatUtil.pad9(hcode, 1));
        stringBuilder.append(formatUtil.padX(lkcode, 6));
        stringBuilder.append(formatUtil.pad9(atmcode, 3));
        stringBuilder.append(formatUtil.padX(entpno, 10));
        stringBuilder.append(formatUtil.pad9(hentpno, 8));
        stringBuilder.append(formatUtil.padX(scname, 10));
        stringBuilder.append(formatUtil.pad9(cdata, 1));
        stringBuilder.append(formatUtil.pad9(appdt, 8));
        stringBuilder.append(formatUtil.pad9(upddt, 8));
        stringBuilder.append(formatUtil.pad9(lputdt, 8));
        stringBuilder.append(formatUtil.pad9(llputdt, 8));
        stringBuilder.append(formatUtil.pad9(ulputdt, 8));
        stringBuilder.append(formatUtil.pad9(ullputdt, 8));
        stringBuilder.append(formatUtil.padX(prtype, 1));
        stringBuilder.append(formatUtil.padX(clsacno, 6));
        stringBuilder.append(formatUtil.padX(clssbno, 4));
        stringBuilder.append(formatUtil.padX(clsdtlno, 4));
        stringBuilder.append(formatUtil.padX(ebtype, 10));
        stringBuilder.append(formatUtil.padX(pwtype, 3));
        stringBuilder.append(formatUtil.padX(feename, 42));
        stringBuilder.append(formatUtil.padX(putsend, 1));
        stringBuilder.append(formatUtil.padX(putform, 1));
        stringBuilder.append(formatUtil.padX(putname, 6));
        stringBuilder.append(formatUtil.pad9(puttype, 2));
        stringBuilder.append(formatUtil.padX(putaddr, 100));
        stringBuilder.append(formatUtil.padX(put_encode, 1));
        stringBuilder.append(formatUtil.padX(put_compress, 1));
        stringBuilder.append(formatUtil.padX(oputtime, 1));
        stringBuilder.append(formatUtil.pad9(cyck1, 1));
        stringBuilder.append(formatUtil.pad9(cycno1, 2));
        stringBuilder.append(formatUtil.pad9(cyck2, 1));
        stringBuilder.append(formatUtil.pad9(cycno2, 2));
        stringBuilder.append(formatUtil.pad9(putdtfg, 1));
        stringBuilder.append(formatUtil.pad9(putdt, 8));
        stringBuilder.append(formatUtil.pad9(msg2, 1));
        stringBuilder.append(formatUtil.pad9(tputdt, 8));
        stringBuilder.append(formatUtil.pad9(usecnt, 4));
        stringBuilder.append(formatUtil.pad9(cfee1, 7));
        stringBuilder.append(formatUtil.pad9(cfee2, 6));
        stringBuilder.append(formatUtil.pad9(cfee3, 7));
        stringBuilder.append(formatUtil.pad9(cfee4, 13));
        stringBuilder.append(formatUtil.pad9(fkd, 1));
        stringBuilder.append(formatUtil.pad9(mfee, 6));
        stringBuilder.append(formatUtil.pad9(cfeeeb, 6));
        fileContents.add(formatUtil.padX(stringBuilder.toString(), 600));
    }

    private void createReportFile() {

        String reportFilePath = fileDir + "CRE_CLMR_" + batchDate.substring(1) + ".TXT";
        textFileUtil.deleteFile(reportFilePath);
        textFileUtil.writeFileContent(reportFilePath, fileContents, CHARSET);

        upload(reportFilePath);
    }

    private void upload(String filePath) {
        Path path = Paths.get(filePath);
        File file = path.toFile();
        String uploadPath = File.separator + batchDate + File.separator + "2FSAP";
        fsapSyncSftpService.uploadFile(file, uploadPath);
    }

    private void batchResponse(CRE_CLMR event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
