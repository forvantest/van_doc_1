/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CL003_SORT;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.parse.Parse;
import java.io.File;
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
@Component("CL003_SORTLsnr")
@Scope("prototype")
public class CL003_SORTLsnr extends BatchListenerCase<CL003_SORT> {

    @Autowired private Parse parse;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private TextFileUtil textFile;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;

    private static final String CHARSET = "UTF-8";
    private int wkBrno;
    private int inputRday;
    private String fdDbsctlDir;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CL003_SORT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CL003_SORTLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CL003_SORT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CL003_SORTLsnr run()");
        sortin(event);
        sortout();
    }

    private void sortin(CL003_SORT event) {

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkBrno = parse.string2Integer(textMap.get("BRNO")); // 分行別
        inputRday = parse.string2Integer(textMap.get("RDAY")); // 日期

        // 000600* 2. INPUT  DATA : FD-BSCTL = DATA/BS/DWL/"&RDAY&"/"&BRNO&"/"
        // 000700*                            &DROP(RDAY,3)&"891099/3"
        fdDbsctlDir = fileDir + "BS/DWL/";
        fdDbsctlDir += parse.decimal2String(inputRday, 7, 0) + "_";
        fdDbsctlDir += parse.decimal2String(wkBrno, 3, 0) + "_";
        fdDbsctlDir += parse.decimal2String(inputRday, 4, 0) + "891099_3";
    }

    private void sortout() {
        if (textFile.exists(fdDbsctlDir)) {
            File tmpFile = new File(fdDbsctlDir);
            List<KeyRange> keyRanges = new ArrayList<>();
            keyRanges.add(new KeyRange(1, 1, SortBy.ASC));
            keyRanges.add(new KeyRange(2, 6, SortBy.ASC));
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "keyRanges  = " + keyRanges);

            externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);
        }
    }
}
