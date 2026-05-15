package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;

public class RunCodeRequest {

    @NotBlank(message = "Source code is required")
    private String sourceCode;

    public RunCodeRequest() {
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }
}