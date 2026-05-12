package app.SkillSync.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class CreateCandidateRequest {

    @NotBlank(message = "Candidate name is required")
    private String name;

    @Email(message = "Candidate email must be valid")
    @NotBlank(message = "Candidate email is required")
    private String email;

    public CreateCandidateRequest() {
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}