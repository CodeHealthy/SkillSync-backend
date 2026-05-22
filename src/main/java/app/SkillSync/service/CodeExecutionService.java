package app.SkillSync.service;

import app.SkillSync.dto.CodeExecutionResult;
import app.SkillSync.model.ProgrammingLanguage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class CodeExecutionService {

    @Value("${code.execution.timeout-seconds:8}")
    private long timeoutSeconds;

    @Value("${code.execution.enabled:true}")
    private boolean executionEnabled;

    @Value("${code.execution.max-source-size-chars:20000}")
    private int maxSourceSizeChars;

    @Value("${code.execution.max-output-size-chars:5000}")
    private int maxOutputSizeChars;

    @Value("${code.execution.memory-limit:128m}")
    private String memoryLimit;

    @Value("${code.execution.cpu-limit:0.5}")
    private String cpuLimit;

    @Value("${code.execution.pids-limit:64}")
    private String pidsLimit;

    private static final Set<ProgrammingLanguage> EXECUTABLE_LANGUAGES = EnumSet.of(
            ProgrammingLanguage.JAVA,
            ProgrammingLanguage.JAVASCRIPT,
            ProgrammingLanguage.PYTHON
    );

    public CodeExecutionResult executeCode(
            ProgrammingLanguage language,
            String sourceCode,
            String expectedOutput
    ) {
        return executeCode(language, sourceCode, "", expectedOutput);
    }

    public CodeExecutionResult executeCode(
            ProgrammingLanguage language,
            String sourceCode,
            String stdin,
            String expectedOutput
    ) {
        validateExecutionRequest(language, sourceCode);

        Path tempDir = null;
        ExecutorService executorService = null;

        try {
            tempDir = Files.createTempDirectory("skillsync-code-");

            DockerExecutionSpec spec = prepareExecutionSpec(language, sourceCode, tempDir);
            String containerName = "skillsync-" + UUID.randomUUID();

            List<String> command = buildDockerCommand(containerName, tempDir, spec);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            executorService = Executors.newFixedThreadPool(3);

            Future<String> stdoutFuture = executorService.submit(() ->
                    readLimited(process.getInputStream(), maxOutputSizeChars)
            );

            Future<String> stderrFuture = executorService.submit(() ->
                    readLimited(process.getErrorStream(), maxOutputSizeChars)
            );

            Future<?> stdinFuture = executorService.submit(() ->
                    writeStdin(process.getOutputStream(), stdin)
            );

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                forceRemoveContainer(containerName);
                process.destroyForcibly();

                String stdout = getFutureValue(stdoutFuture);
                String stderr = getFutureValue(stderrFuture);

                return new CodeExecutionResult(
                        language.name(),
                        stdout,
                        appendMessage(stderr, "Execution timed out after " + timeoutSeconds + " seconds."),
                        null,
                        true,
                        false
                );
            }

            waitForStdinWrite(stdinFuture);

            int exitCode = process.exitValue();

            String stdout = getFutureValue(stdoutFuture);
            String stderr = getFutureValue(stderrFuture);

            boolean matched = exitCode == 0 && outputsMatch(stdout, expectedOutput);

            return new CodeExecutionResult(
                    language.name(),
                    stdout,
                    stderr,
                    exitCode,
                    false,
                    matched
            );

        } catch (Exception exception) {
            String languageName = language == null ? "UNKNOWN" : language.name();

            return new CodeExecutionResult(
                    languageName,
                    "",
                    safeErrorMessage(exception),
                    null,
                    false,
                    false
            );
        } finally {
            if (executorService != null) {
                executorService.shutdownNow();
            }

            if (tempDir != null) {
                deleteDirectoryQuietly(tempDir);
            }
        }
    }

    private void validateExecutionRequest(
            ProgrammingLanguage language,
            String sourceCode
    ) {
        if (!executionEnabled) {
            throw new IllegalStateException("Code execution is disabled");
        }

        if (language == null) {
            throw new IllegalArgumentException("Programming language is required");
        }

        if (!EXECUTABLE_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("Unsupported executable language: " + language);
        }

        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Source code is required");
        }

        if (sourceCode.length() > maxSourceSizeChars) {
            throw new IllegalArgumentException(
                    "Source code exceeds maximum allowed size of " + maxSourceSizeChars + " characters"
            );
        }

        if (timeoutSeconds <= 0) {
            throw new IllegalStateException("Code execution timeout must be greater than zero");
        }

        if (maxOutputSizeChars <= 0) {
            throw new IllegalStateException("Maximum output size must be greater than zero");
        }
    }

    private DockerExecutionSpec prepareExecutionSpec(
            ProgrammingLanguage language,
            String sourceCode,
            Path tempDir
    ) throws IOException {
        return switch (language) {
            case JAVA -> {
                Files.writeString(
                        tempDir.resolve("Main.java"),
                        sourceCode,
                        StandardCharsets.UTF_8
                );

                yield new DockerExecutionSpec(
                        "eclipse-temurin:17-jdk",
                        "sh",
                        "-c",
                        "javac Main.java && java Main"
                );
            }

            case JAVASCRIPT -> {
                Files.writeString(
                        tempDir.resolve("main.js"),
                        sourceCode,
                        StandardCharsets.UTF_8
                );

                yield new DockerExecutionSpec(
                        "node:20-alpine",
                        "node",
                        "main.js"
                );
            }

            case PYTHON -> {
                Files.writeString(
                        tempDir.resolve("main.py"),
                        sourceCode,
                        StandardCharsets.UTF_8
                );

                yield new DockerExecutionSpec(
                        "python:3.12-alpine",
                        "python",
                        "main.py"
                );
            }

            case TEXT -> throw new IllegalArgumentException("TEXT submissions cannot be executed");
        };
    }

    private List<String> buildDockerCommand(
            String containerName,
            Path tempDir,
            DockerExecutionSpec spec
    ) {
        List<String> command = new ArrayList<>();

        command.add("docker");
        command.add("run");
        command.add("--rm");
        command.add("-i");
        command.add("--name");
        command.add(containerName);

        command.add("--network");
        command.add("none");

        command.add("--memory");
        command.add(memoryLimit);

        command.add("--memory-swap");
        command.add(memoryLimit);

        command.add("--cpus");
        command.add(cpuLimit);

        command.add("--pids-limit");
        command.add(pidsLimit);

        command.add("--cap-drop");
        command.add("ALL");

        command.add("--security-opt");
        command.add("no-new-privileges");

        command.add("--ulimit");
        command.add("nofile=64:64");

        command.add("-v");
        command.add(tempDir.toAbsolutePath() + ":/workspace");

        command.add("-w");
        command.add("/workspace");

        command.add(spec.image());

        command.add(spec.command());
        command.addAll(List.of(spec.args()));

        return command;
    }

    private void writeStdin(OutputStream outputStream, String stdin) {
        try (OutputStream stream = outputStream) {
            if (stdin != null && !stdin.isEmpty()) {
                stream.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            stream.flush();
        } catch (IOException ignored) {
        }
    }

    private void waitForStdinWrite(Future<?> stdinFuture) {
        try {
            stdinFuture.get(1, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private String readLimited(InputStream inputStream, int maxChars) throws IOException {
        int maxBytes = Math.max(maxChars * 4, 1024);
        byte[] buffer = new byte[1024];

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean truncated = false;

        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            if (outputStream.size() + bytesRead > maxBytes) {
                int allowedBytes = Math.max(maxBytes - outputStream.size(), 0);

                if (allowedBytes > 0) {
                    outputStream.write(buffer, 0, allowedBytes);
                }

                truncated = true;
                break;
            }

            outputStream.write(buffer, 0, bytesRead);
        }

        String output = outputStream.toString(StandardCharsets.UTF_8);

        if (output.length() > maxChars) {
            output = output.substring(0, maxChars);
            truncated = true;
        }

        if (truncated) {
            return output + "\n...output truncated...";
        }

        return output;
    }

    private void forceRemoveContainer(String containerName) {
        try {
            new ProcessBuilder("docker", "rm", "-f", containerName)
                    .start()
                    .waitFor(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private boolean outputsMatch(String actualOutput, String expectedOutput) {
        if (expectedOutput == null || expectedOutput.trim().isEmpty()) {
            return false;
        }

        String normalizedActual = normalizeOutput(actualOutput);
        String normalizedExpected = normalizeOutput(expectedOutput);

        return normalizedActual.equals(normalizedExpected);
    }

    private String normalizeOutput(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
    }

    private String getFutureValue(Future<String> future) {
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String appendMessage(String original, String message) {
        if (original == null || original.isBlank()) {
            return message;
        }

        return original + "\n" + message;
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return "Code execution failed unexpectedly.";
        }

        return message;
    }

    private void deleteDirectoryQuietly(Path directory) {
        try {
            if (!Files.exists(directory)) {
                return;
            }

            try (var paths = Files.walk(directory)) {
                paths.sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    private record DockerExecutionSpec(
            String image,
            String command,
            String... args
    ) {
    }
}
