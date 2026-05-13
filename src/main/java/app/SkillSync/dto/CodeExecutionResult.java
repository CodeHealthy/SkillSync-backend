package app.SkillSync.dto;

public class CodeExecutionResult {

    private String language;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private boolean timedOut;
    private boolean outputMatched;

    public CodeExecutionResult() {
    }

    public CodeExecutionResult(
            String language,
            String stdout,
            String stderr,
            Integer exitCode,
            boolean timedOut,
            boolean outputMatched
    ) {
        this.language = language;
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
        this.timedOut = timedOut;
        this.outputMatched = outputMatched;
    }

    public String getLanguage() {
        return language;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public boolean isOutputMatched() {
        return outputMatched;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public void setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
    }

    public void setOutputMatched(boolean outputMatched) {
        this.outputMatched = outputMatched;
    }
}