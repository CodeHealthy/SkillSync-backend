package app.SkillSync.service;

import app.SkillSync.dto.CodeExecutionResult;
import app.SkillSync.model.ProgrammingLanguage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class CodeExecutionService {

    @Value("${code.execution.timeout-seconds:8}")
    private long timeoutSeconds;

    @Value("${code.execution.enabled:true}")
    private boolean executionEnabled;

    private static final int MAX_OUTPUT_LENGTH = 5000;

    public CodeExecutionResult executeCode(
            ProgrammingLanguage language,
            String sourceCode,
            String expectedOutput
    ) {
        if (!executionEnabled) {
            throw new IllegalStateException("Code execution is disabled");
        }

        if (language == null) {
            throw new IllegalArgumentException("Programming language is required");
        }

        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Source code is required");
        }

        Path tempDir = null;

        try {
            tempDir = Files.createTempDirectory("skillsync-code-");

            DockerExecutionSpec spec = prepareExecutionSpec(language, sourceCode, tempDir);

            String containerName = "skillsync-" + UUID.randomUUID();

            List<String> command = buildDockerCommand(containerName, tempDir, spec);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            ExecutorService executorService = Executors.newFixedThreadPool(2);

            Future<String> stdoutFuture = executorService.submit(() ->
                    readLimited(process.getInputStream().readAllBytes())
            );

            Future<String> stderrFuture = executorService.submit(() ->
                    readLimited(process.getErrorStream().readAllBytes())
            );

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                forceRemoveContainer(containerName);
                process.destroyForcibly();

                String stdout = getFutureValue(stdoutFuture);
                String stderr = getFutureValue(stderrFuture);

                executorService.shutdownNow();

                return new CodeExecutionResult(
                        language.name(),
                        stdout,
                        appendMessage(stderr, "Execution timed out after " + timeoutSeconds + " seconds."),
                        null,
                        true,
                        false
                );
            }

            int exitCode = process.exitValue();

            String stdout = getFutureValue(stdoutFuture);
            String stderr = getFutureValue(stderrFuture);

            executorService.shutdownNow();

            boolean matched = outputsMatch(stdout, expectedOutput);

            return new CodeExecutionResult(
                    language.name(),
                    stdout,
                    stderr,
                    exitCode,
                    false,
                    matched
            );

        } catch (Exception exception) {
            return new CodeExecutionResult(
                    language.name(),
                    "",
                    exception.getMessage(),
                    null,
                    false,
                    false
            );
        } finally {
            if (tempDir != null) {
                deleteDirectoryQuietly(tempDir);
            }
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
        command.add("--name");
        command.add(containerName);

        command.add("--network");
        command.add("none");

        command.add("--memory");
        command.add("128m");

        command.add("--cpus");
        command.add("0.5");

        command.add("--pids-limit");
        command.add("64");

        command.add("-v");
        command.add(tempDir.toAbsolutePath() + ":/workspace");

        command.add("-w");
        command.add("/workspace");

        command.add(spec.image());

        command.add(spec.command());
        command.addAll(List.of(spec.args()));

        return command;
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

    private String readLimited(byte[] bytes) {
        String output = new String(bytes, StandardCharsets.UTF_8);

        if (output.length() > MAX_OUTPUT_LENGTH) {
            return output.substring(0, MAX_OUTPUT_LENGTH) + "\n...output truncated...";
        }

        return output;
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

    private void deleteDirectoryQuietly(Path directory) {
        try {
            if (!Files.exists(directory)) {
                return;
            }

            Files.walk(directory)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
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