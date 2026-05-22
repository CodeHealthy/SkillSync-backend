package app.SkillSync.dto;

public class BillingSessionResponse {

    private String url;

    public BillingSessionResponse() {
    }

    public BillingSessionResponse(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
