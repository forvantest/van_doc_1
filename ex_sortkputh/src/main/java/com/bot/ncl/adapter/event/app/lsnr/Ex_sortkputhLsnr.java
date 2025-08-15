/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static com.bot.txcontrol.mapper.MapperCase.formatUtil;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Ex_sortkputh;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.nio.file.Paths;
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
@Component("Ex_sortkputhLsnr")
@Scope("prototype")
public class Ex_sortkputhLsnr extends BatchListenerCase<Ex_sortkputh> {

    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private Parse parse;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String PUTH_FILE_NAME = "PUTH";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private String tbsdy;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Ex_sortkputh event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Ex_sortkputhLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Ex_sortkputh event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Ex_sortkputhLsnr run()");

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、檔名日期變數值
        String processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        // SYM/CL/BH/OUTING/SORT/KPUTH1
        // 00000100DISKSORT
        // 00000200FILE IN  (TITLE = "DATA/CL/BH/OUTING/SPUTH")
        // 00000300FILE OUT (TITLE = "DATA/CL/BH/OUTING/SPUTH")
        // 00000320KEY (   1 38 A )
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 38, SortBy.ASC));

        // WFL/CL/1530/CLONB
        // RUN  OBJ/CL/BH/OUTING/SORT/KPUTH1[T];
        // FILE IN  (TITLE=DATA/CL/BH/PUTH );
        // FILE OUT (TITLE=DATA/CL/BH/PUTH );
        String puthFilePath =
                this.fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + PUTH_FILE_NAME;
        File file = Paths.get(puthFilePath).toFile();
        externalSortUtil.sortingFile(file, file, keyRanges);
        upload(file);
        batchResponse(event);
    }

    private void batchResponse(Ex_sortkputh event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }

    private void upload(File file) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV145952 upload()");

            String uploadPath =
                    File.separator + tbsdy + File.separator + "2FSAP" + File.separator + "DATA";
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }
}
