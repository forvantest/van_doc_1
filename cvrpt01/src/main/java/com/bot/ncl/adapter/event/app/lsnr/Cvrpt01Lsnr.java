/* (C) 2025 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Cvrpt01;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.text.astart.AstarUtils;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.integration.file.remote.FileInfo;
import org.springframework.stereotype.Component;

@Slf4j
@Component("Cvrpt01Lsnr")
@Scope("prototype")
public class Cvrpt01Lsnr extends BatchListenerCase<Cvrpt01> {
    private Cvrpt01 event;
    // 滿天星轉碼
    @Autowired private AstarUtils astarUtils;

    // 寫檔相關工具及變數
    @Value("${localFile.ncl.batch.directory}")
    private String fileDir; // 讀檔共用路徑

    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private TextFileUtil textFile;

    private List<FileInfo> fileInfoList;
    private String batchDate = "";
    private String localFlag = "";
    private String BeforeDir;
    private String fileCovPath;
    private String fileLocalDir;
    private String outputPath;
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CONFIG_ENCODING = "MS950";
    private static final String BATNO = "001";

    // 文字檔內容
    List<String> covTXT = new ArrayList<>();

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Cvrpt01 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Cvrpt01Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Cvrpt01 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in Cvrpt01Lsnr");
        this.event = event;
        initParam();
        if (localFlag.equals("Y")) {
            localCov();
        } else {
            // 下載到本地的BD/BEFORECONV裡
            // 寫到本地
            // 組fsap共用檔案路徑
            BeforeDir = fileDir + "BEFORECONV/" + batchDate;
            textFile.deleteDir(BeforeDir);
            setPathAndCov();
        }

        batchResponse();
    }

    private void initParam() {
        // 設定讀檔路徑
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "initParams()");
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        Map<String, String> textMap = arrayMap.get("textMap").getMapAttrMap();
        batchDate = labelMap.get("PROCESS_DATE"); // 批次營業日
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "textMap:{}", textMap);
            localFlag = textMap.getOrDefault("LOCALFLAG", "N"); // 本機記號
        } catch (Exception e) {
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "localFlag error={}", e.getMessage());
            localFlag = "N";
        }
    }

    private void localCov() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in localCov.....");
        // 修改這裡的路徑為你要讀取的本地資料夾
        Path folderPath = Paths.get(fileDir);
        // 定義輸出資料夾（新建一個資料夾）
        Path outputFolder = folderPath.resolve("convertedFiles");

        // 如果輸出資料夾不存在，就建立它
        try {
            if (!Files.exists(outputFolder)) {
                Files.createDirectories(outputFolder);
            }
        } catch (IOException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "unexpected error={}", e.getMessage());
            return;
        }

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(folderPath)) {
            for (Path filePath : directoryStream) {
                // 只處理常規檔案，排除資料夾
                if (Files.isRegularFile(filePath)) {
                    String originalFilename = filePath.getFileName().toString();
                    int dotIndex = originalFilename.lastIndexOf('.');

                    String baseFilename;
                    if (dotIndex == -1) {
                        // 沒有副檔名的情況
                        baseFilename = originalFilename;
                    } else if (dotIndex == originalFilename.length() - 1) {
                        // 檔名以點結尾，移除最後的點
                        baseFilename = originalFilename.substring(0, originalFilename.length() - 1);
                    } else {
                        // 有副檔名，取最後一個點之前的部分作為基底檔名
                        baseFilename = originalFilename.substring(0, dotIndex);
                    }

                    // 組合成新的檔名，例如：myfile_converted.txt
                    String newFilename = baseFilename + "_converted.txt";
                    fileCovPath = folderPath.resolve(originalFilename).toString();
                    fileLocalDir = fileCovPath;
                    outputPath = outputFolder.resolve(newFilename).toString();
                    ApLogHelper.info(
                            log, false, LogType.NORMAL.getCode(), "原始檔案路徑:{}", fileCovPath);
                    ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "產出檔案路徑:{}", outputPath);
                    // 這裡可以進行檔案轉碼或複製操作
                    getTxt();
                    writeTxt();
                } else {
                    ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "此檔案為資料夾不處理!");
                }
            }
        } catch (IOException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "unexpected error={}", e.getMessage());
        }
    }

    private void setPathAndCov() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in setPathAndCov.....");
        listFTP("NCL/" + batchDate + "/BEFORECONV");
        //        listLocalFiles(BeforeDir);
        // 假設所有檔案都在同一個資料夾，取得第一個檔案的資料夾作為基底
        // 或者你也可以直接設定一個路徑
        if (fileInfoList.isEmpty()) {
            return;
        }
        String remoteDirectory = fileInfoList.get(0).getRemoteDirectory();
        // 定義新資料夾的路徑
        Path outputFolder = Paths.get(remoteDirectory, "AFTERCONV");
        // 若資料夾不存在就建立
        if (!Files.exists(outputFolder)) {
            try {
                Files.createDirectories(outputFolder);
            } catch (IOException e) {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "建立輸出資料夾失敗", e);
                return;
            }
        }

        for (int i = 0; i < fileInfoList.size(); i++) {
            FileInfo fileinfo = fileInfoList.get(i);
            // 原始檔案路徑
            fileCovPath =
                    Paths.get(fileinfo.getRemoteDirectory(), fileinfo.getFilename()).toString();
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "資料夾中第{}筆原始檔案路徑:{}", i + 1, fileCovPath);
            fileLocalDir = BeforeDir + File.separator + fileinfo.getFilename();
            String sourceFtpPath =
                    "NCL"
                            + File.separator
                            + batchDate
                            + File.separator
                            + "BEFORECONV"
                            + File.separator
                            + fileinfo.getFilename();
            File sFile = downloadFromSftp(sourceFtpPath, BeforeDir);
            if (sFile != null) {
                fileLocalDir = getLocalPath(sFile);
            }

            String originalFilename = fileinfo.getFilename();
            int dotIndex = originalFilename.lastIndexOf('.');

            String baseName;
            if (dotIndex == -1) {
                // 沒有任何點
                baseName = originalFilename;
            } else if (dotIndex == originalFilename.length() - 1) {
                // 檔名以點結尾，移除最後的點
                baseName = originalFilename.substring(0, originalFilename.length() - 1);
            } else {
                // 有副檔名：取最後一個點之前的部分
                baseName = originalFilename.substring(0, dotIndex);
            }

            // 組合成新檔案名稱：將原始檔名改為 "baseName_converted.txt"
            String outputname = baseName;
            // 將新檔案放到剛建立的資料夾中
            outputPath = Paths.get(outputFolder.toString(), outputname).toString();

            // 進行檔案轉碼或處理
            getTxt();
            // 檔案存在自己的本地的RPT目錄底下並上傳
            outputPath =
                    fileDir
                            + "AFTERCONV"
                            + File.separator
                            + batchDate
                            + File.separator
                            + outputname;
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "資料夾中第{}筆產出檔案路徑:{}", i + 1, outputPath);
            writeTxt();
        }
    }

    private void listFTP(String dir) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in listFTP = {}", dir);
        fileInfoList = new ArrayList<>();
        try {
            fileInfoList = fsapSyncSftpService.listFile(dir);
        } catch (Exception e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "listFile ERROR = {}", e.getMessage());
            throw new LogicException("GE999", "目錄不存在(" + dir + ")");
        }

        if (!Objects.isNull(fileInfoList) && !fileInfoList.isEmpty()) {
            for (FileInfo fileInfo : fileInfoList) {
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileInfo = {}", fileInfo);
            }
        }
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }

    private void getTxt() {
        try {
            covTXT = new ArrayList<>();
            String string = mainCov();

            List<String> reportList = Arrays.asList(string.split("\r\n"));
            StringBuilder sb = null;
            int k = 0;
            for (int i = 0; i < reportList.size(); i++) {
                //				String[] arr = reportList.get(i).split("");
                if (reportList.size() > 0) {
                    byte[] dataByte = reportList.get(i).getBytes();
                    if (dataByte.length > 0) {
                        // 換頁條件 0x0C
                        if (dataByte[0] == 0x0C) {
                            sb = new StringBuilder();
                            // 換頁邏輯
                            for (int j = k; j < i; j++) {
                                ApLogHelper.info(
                                        log,
                                        false,
                                        LogType.NORMAL.getCode(),
                                        reportList.get(j) + "\r\n");
                                sb.append(reportList.get(j) + "\r\n");
                            }
                            covTXT.add(sb.toString());
                            k = i;
                        }
                    }
                }
            }
        } catch (Exception e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "unexpected error={}", e.getMessage());
        }
    }

    private String mainCov() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileLocalDir = {}", fileLocalDir);

        StringBuffer sb = new StringBuffer();

        try {
            int iFilter = 1;
            Path sPath = Paths.get(fileLocalDir);
            String desPath = "";
            int cbFlag = 0;
            int iFirstPage = 0;
            int i0d0a = 1;
            int iTot = 0;
            int iTraceInfo = 0;
            int iLineNo = 0;
            int flag = 0;
            if (Files.exists(sPath)) {
                byte[] fileByte = Files.readAllBytes(sPath);
                // 檔案內容
                char[] data = new char[fileByte.length];
                for (int i = 0; i < fileByte.length; i++) {
                    data[i] = (char) fileByte[i];
                }
                // 處理中的
                char[] proData = new char[1800];
                // 剩下的資料
                char[] temp = null;
                // 每次處理1800個
                while (data.length != 0) {
                    if (data.length > 1800) {
                        System.arraycopy(data, 0, proData, 0, 1800);
                        temp = new char[data.length - 1800];
                        // 剩下的資料
                        System.arraycopy(data, 1800, temp, 0, temp.length);
                        data = new char[temp.length];
                        System.arraycopy(temp, 0, data, 0, temp.length);
                    } else {
                        System.arraycopy(data, 0, proData, 0, data.length);
                        temp = new char[data.length];
                        data = new char[0];
                    }
                    // 開始處理
                    // 每次處理1800個
                    int ioffset = 0;
                    int cwA, cwB, cwC, cwD, cwE, cwF;
                    int value = 0;
                    int iLineNum = 0;
                    int iTotLine = 0;
                    int iFix = 0;
                    while (ioffset < proData.length - (4 * 6)) {
                        cwA = byteToInt(proData[ioffset], 3, 1);
                        cwB = byteToInt(proData[ioffset], 0, 1);
                        cwC = byteToInt(proData[ioffset + 1], 0, 4);
                        cwD = byteToInt(proData[ioffset + 2], 6, 2);
                        cwE = byteToInt(proData[ioffset + 3], 1, 3);
                        int i1, i2, i3;
                        i1 = byteToInt(proData[ioffset + 3], 0, 1) * 16 * 16 * 16 * 16;
                        i2 = byteToInt(proData[ioffset + 4], 0, 8) * 16 * 16;
                        i3 = byteToInt(proData[ioffset + 5], 0, 8);
                        cwF = i1 + i2 + i3;
                        if (cwA == 0 && cwB == 0 && cwC == 0 && cwD == 0 && cwE == 0 && cwF == 0)
                            break;
                        cwF *= 6;
                        ioffset += 6;
                        if ((ioffset + cwF + cwE) > 1800) {
                            break;
                        }
                        if (cwA == 0 && cwB == 0) {
                            int len;
                            if (iTotLine < iLineNum || iLineNum == 0 || iFix == 0) {
                                char[] conv = new char[proData.length - ioffset];
                                System.arraycopy(proData, ioffset, conv, 0, conv.length);
                                byte[] conData = con2Asc(conv, cwF + cwE, iFilter);
                                filterStarASCII(conData, conData.length);
                                sb.append(new String(conData, CONFIG_ENCODING));
                                iTot += conData.length;
                                iFirstPage = 1;
                            }
                        } else {
                            cbFlag++;
                        }
                        if (cwE > 0) {
                            ioffset += cwF + 6;
                        } else {
                            ioffset += cwF;
                        }
                        if (cwC > 0) {
                            if (iFirstPage == 0) continue;
                            for (int index = 0; index < cwC; index++) {
                                if (iLineNum > 0) {
                                    for (int j = 0, index1 = iTotLine;
                                            index1 < iLineNum;
                                            index1++, j++) {
                                        if (i0d0a != 0) {
                                            sb.append("\r\n");
                                            iTot += 2;
                                        } else {
                                            if (iTraceInfo != 0) {
                                                if (j == 0 && (cwA != 0 || cwB != 0)) {
                                                    sb.append(StringUtils.leftPad("", 100));
                                                }
                                                if (j > 0) {
                                                    sb.append(StringUtils.leftPad("", 100));
                                                }
                                            }
                                            sb.append("\n");
                                            iTot += 1;
                                        }
                                    }
                                    iTotLine = 0;
                                } else {
                                    // Bot換頁符號
                                    sb.append("\r\n");
                                    sb.append((char) 0x0c);
                                    sb.append("\r\n");
                                    iTot += 1;
                                }
                            }
                            if (iTraceInfo != 0) {
                                sb.append(StringUtils.leftPad("", 100));
                                iLineNo = 0;
                            }
                        }
                        if (cwD > 0) {
                            for (int i = 0; i < cwD; i++) {
                                if (iTotLine >= iLineNum && iLineNum > 0 && iFix == 1) {
                                    iTotLine++;
                                    continue;
                                }
                                if (i0d0a != 0) {
                                    sb.append("\r\n");
                                    iTot += 2;
                                } else {
                                    if (iTraceInfo != 0) {
                                        if (i == 0 && (cwA != 0 || cwB != 0)) {
                                            sb.append(StringUtils.leftPad("", 100));
                                        }
                                        if (i > 0) {
                                            sb.append(StringUtils.leftPad("", 100));
                                        }
                                    }
                                    sb.append("\n");
                                    iTot += 1;
                                }
                                iTotLine++;
                            }
                        }
                    }
                }
                //                if (StringUtils.isNotBlank(outputPath)) {
                //                    try {
                //                        textFile.writeFileContent(outputPath, sb.toString(),
                // CHARSET_BIG5);
                //                    } catch (LogicException e) {
                //                        moveErrorResponse(e);
                //                    }
                //                    FileUtils.writeStringToFile(new File(desPath), sb.toString(),
                // CONFIG_ENCODING);
                //                }
            } else {
                //                throw new LogicException(E032, "無此檔案");
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "無此檔案");
            }

        } catch (Exception e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "unexpected error={}", e.getMessage());
            //            throw new LogicException(E032, "讀檔錯誤!");
        }
        return sb.toString();
    }

    private void writeTxt() {
        try {
            textFile.writeFileContent(outputPath, covTXT, CHARSET_BIG5);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "unexpected error={}", e.getMessage());
        }
        // 上傳fsap共用空間
        upload(outputPath);
    }

    // 以下為工具類

    /**
     * @param cc char
     * @param ii 起始位置由lsb開始(lsb由0開始)
     * @param nn 取幾個 Ex: char = "C", ii = 3, nn = 2 -> 16進制 = 0x59 -> 二進制 (msb)01011001(lsb) -> 取到
     *     010'11'001 -> 換成最後 00000011 -> 3
     * @return
     */
    public static int byteToInt(char cc, int ii, int nn) {
        int[] iBit = new int[8];
        int i, j;
        int iRet = 0;

        for (i = 0; i < 8; i++) {
            iBit[i] = (int) (cc / Math.pow((float) 2, i)) % 2;
        }
        for (i = ii, j = 0; j < nn && i < 8; i++, j++) {
            iRet += (iBit[i] * Math.pow((float) 2, j));
        }
        return (iRet);
    }

    public byte[] con2Asc(char[] pStr, int len, int flag) {
        char[] p2;
        int len2;

        p2 = new char[len];
        // 20080829 , [0x1A 0x46 0x89] [0x1A 0x46 0x8C] filter to null.
        for (int i = 0, j = 0; i < len; i++) {
            if (i <= (len - 3)) {
                if (pStr[i] == 0x1A && pStr[i + 1] == 0x46) {
                    if (pStr[i + 2] == 0x89 || pStr[i + 2] == 0x8C) {
                        i += 2; // Total jump 3 position.
                    }
                } else p2[j++] = pStr[i];
            } else p2[j++] = pStr[i];
        }
        len2 = p2.length;
        byte[] data = new byte[p2.length];
        for (int i = 0; i < p2.length; i++) {
            data[i] = (byte) p2[i];
        }

        byte[] resultsplit = astarUtils.bur2Big5(data);
        // char[] resultsplit = new String(result).toCharArray();
        if (flag != 0) {
            for (int i = 0; i < resultsplit.length; i++) {
                if (resultsplit[i] == (byte) 0x04 || resultsplit[i] == (byte) 0x07)
                    resultsplit[i] = (byte) 0x20;
                if (resultsplit[i] == (byte) 0x00) resultsplit[i] = (byte) 0x20;
            }
        }

        return resultsplit;
    }

    public void filterStarASCII(byte[] src, int len) {
        for (int i = 0; i < len; i++) {
            if (src[i] > 0x80) {
                if (src[i] == 0xA1 && (src[i + 1] == 0xB8 || src[i + 1] == 0xB9)) src[i + 1] = 0x40;
                i++;
            }
        }
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

    private void upload(String filePath) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "upload = {}", filePath);
        Path path = Paths.get(filePath);
        File file = path.toFile();
        String uploadPath = File.separator + batchDate + File.separator + "FILETOBIG5";
        fsapSyncSftpService.uploadFile(file, uploadPath);
    }

    private void batchResponse() {
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
