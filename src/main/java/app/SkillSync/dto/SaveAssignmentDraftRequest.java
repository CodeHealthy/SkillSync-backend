package app.SkillSync.dto;

import java.util.Map;

public class SaveAssignmentDraftRequest {

    private String draftCode;
    private Map<String, String> draftAnswers;

    public SaveAssignmentDraftRequest() {
    }

    public String getDraftCode() {
        return draftCode;
    }

    public void setDraftCode(String draftCode) {
        this.draftCode = draftCode;
    }

    public Map<String, String> getDraftAnswers() {
        return draftAnswers;
    }

    public void setDraftAnswers(Map<String, String> draftAnswers) {
        this.draftAnswers = draftAnswers;
    }
}
