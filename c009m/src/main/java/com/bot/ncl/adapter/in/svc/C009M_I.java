/* (C) 2023 */
package com.bot.ncl.adapter.in.svc;

import com.bot.txcontrol.adapter.RequestSvcCase;
import com.bot.txcontrol.adapter.in.RequestBaseSvc;
import com.bot.txcontrol.adapter.in.RequestLabel;
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
@Component("C009M_I")
@Scope("prototype")
public class C009M_I extends RequestBaseSvc implements RequestSvcCase {
    private RequestLabel label;

    @CustomSchema(
            order = 68,
            schema = @Schema(description = "日期", maxLength = 8, type = "NUMBER"),
            decimal = 0,
            align = "RIGHT",
            pad = "ZERO")
    @NotEmpty(message = "{BATCH_TBSDY}")
    @JsonProperty("BATCH_TBSDY")
    private String batchTbsdy;

    @CustomSchema(
            order = 69,
            schema = @Schema(description = "指定啟動第幾步驟", maxLength = 3, type = "NUMBER"),
            decimal = 0,
            align = "RIGHT",
            pad = "ZERO")
    @NotEmpty(message = "{STEP_START_AT}")
    @JsonProperty("STEP_START_AT")
    private String stepStartAt;
}
