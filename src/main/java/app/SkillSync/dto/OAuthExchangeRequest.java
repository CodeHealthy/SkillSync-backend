package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;

public class OAuthExchangeRequest {

    @NotBlank(message = "OAuth exchange code is required")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
