/* (C) 2023 */
package com.bot.ncl.adapter.out.svc;

import com.bot.txcontrol.adapter.ResponseCase;
import com.bot.txcontrol.adapter.ResponseLabelCase;
import com.bot.txcontrol.adapter.out.ResponseBase;
import com.bot.txcontrol.adapter.out.api.ResponseLabelFas;
import com.bot.txcontrol.config.CustomSchema;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Component(" STAT1_S000_O")
@Scope("prototype")
public class STAT1_S000_O extends ResponseBase implements ResponseCase {
    private final String MSGID = "S000";

    public STAT1_S000_O() {
        this.label.setMType(MSGID.substring(0, 1));
        this.label.setMsgNo(MSGID.substring(1, 4));
    }

    @CustomSchema(
            order = 0,
            schema = @Schema(name = "RES_LABEL_FAS", description = "RES_LABEL_FAS"))
    @JsonProperty("RES_LABEL_FAS")
    private ResponseLabelCase label = new ResponseLabelFas();

    @CustomSchema(
            order = 37,
            schema = @Schema(description = "批次結果", maxLength = 1, type = "NUMBER"),
            decimal = 0,
            align = "RIGHT",
            pad = "ZERO")
    @NotEmpty(message = "{BATCH_RESULT}")
    @JsonProperty("BATCH_RESULT")
    private String batchResult;

    @CustomSchema(
            order = 38,
            schema = @Schema(description = "目前第幾步驟", maxLength = 3, type = "NUMBER"),
            decimal = 0,
            align = "RIGHT",
            pad = "ZERO")
    @NotEmpty(message = "{STEP_AT}")
    @JsonProperty("STEP_AT")
    private String stepAt;

    @CustomSchema(
            order = 38,
            schema = @Schema(description = "批次訊息", maxLength = 500, type = "CHAR"),
            align = "LEFT",
            pad = "SPACE")
    @NotEmpty(message = "{BATCH_MESSAGE}")
    @JsonProperty("BATCH_MESSAGE")
    private String batchMessage;
}
