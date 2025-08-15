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
@Component("ADDF.S000.O")
@Scope("prototype")
public class ADDF_S000_O extends ResponseBase implements ResponseCase {
    private final String MSGID = "S000";

    public ADDF_S000_O() {
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
            schema = @Schema(description = "輸出檔名", maxLength = 10, type = "CHAR"),
            align = "LEFT",
            pad = "SPACE")
    @NotEmpty(message = "{PUTFILE}")
    @JsonProperty("PUTFILE")
    private String putfile;

    @CustomSchema(
            order = 38,
            schema = @Schema(description = "主辦分行", maxLength = 3, type = "CHAR"),
            align = "LEFT",
            pad = "SPACE")
    @NotEmpty(message = "{PBRNO}")
    @JsonProperty("PBRNO")
    private String pbrno;
}
