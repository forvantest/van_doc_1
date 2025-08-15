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
@Component("ADDF.I")
@Scope("prototype")
public class ADDF__I extends RequestBaseSvc implements RequestSvcCase {
    private RequestLabel label;

    @CustomSchema(
            order = 68,
            schema = @Schema(description = "代收類別", maxLength = 6, type = "CHAR"),
            align = "LEFT",
            pad = "SPACE")
    @NotEmpty(message = "{CODE}")
    @JsonProperty("CODE")
    private String code;
}
