/* (C) 2024 */
package com.bot.ncl.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum BatchLabel {
    BATCH_NO("BATCHNO"),
    AP_KIND("APKIND"),
    AP_ITEM("APITEM"),
    SRC_FILE_NAME("FILENAME"),
    TAR_FILE_NAME("TAR_FILENAME"),
    SYNC_FG("SYNC_FG"),
    PR_FILE_TYPE("PRFILETYPE"),
    FTPACT("FTPACT"),
    FTPID("FTPID"),
    BIZID("BIZ_ID"),
    PRDID("PRDID"),
    NTFCLSID("NTFCLSID"),
    DLVCHNL("DLVCHNL"),
    CNTADDR("CNTADDR"),
    NOTIFYTITLE("NOTIFYTITLE"),
    CONTENT("CONTENT"),
    STATUS("STATUS"),
    MSG("MSG"),
    WFL("WFL");

    @Getter private final String key;

    public static BatchLabel fromString(String name) {
        for (BatchLabel batchLabel : BatchLabel.values()) {
            if (batchLabel.getKey().equalsIgnoreCase(name)) {
                return batchLabel;
            }
        }
        throw new IllegalArgumentException(
                "No enum constant " + BatchLabel.class.getCanonicalName() + " for name " + name);
    }
}
