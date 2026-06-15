package com.evanp.f1.api.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class SubprocessTrackMeshProcessRunner implements TrackMeshProcessRunner {

    private static final long PROCESS_TIMEOUT_MINUTES = 10L;

    @Override
    public ProcessResult run(Path trackMeshRoot, long sessionKey, String circuitSlug, Path outputPath)
            throws IOException {
        Path python = trackMeshRoot.resolve(".venv/bin/python");
        if (!Files.isExecutable(python)) {
            return new ProcessResult(1, "Python venv not found at " + python + "; run generate.sh once to create it.");
        }

        Files.createDirectories(outputPath.getParent());

        List<String> command = new ArrayList<>();
        command.add(python.toString());
        command.add("-m");
        command.add("track_mesh");
        command.add("--session-key");
        command.add(Long.toString(sessionKey));
        command.add("--circuit-slug");
        command.add(circuitSlug);
        command.add("--out");
        command.add(outputPath.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(trackMeshRoot.toFile());
        builder.environment().putIfAbsent("PYTHONPATH", trackMeshRoot.toString());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            if (!process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return new ProcessResult(1, "Track mesh generation timed out after " + PROCESS_TIMEOUT_MINUTES + " minutes");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return new ProcessResult(exitCode, truncate(output));
            }
            if (!Files.isRegularFile(outputPath) || Files.size(outputPath) == 0) {
                return new ProcessResult(1, "Generator exited 0 but output file is missing: " + outputPath);
            }
            return new ProcessResult(0, "");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new ProcessResult(1, "Track mesh generation interrupted");
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 2000) {
            return value == null ? "" : value;
        }
        return value.substring(0, 2000) + "\n...(truncated)";
    }
}
