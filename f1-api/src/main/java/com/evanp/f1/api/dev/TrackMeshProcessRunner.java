package com.evanp.f1.api.dev;

import java.io.IOException;
import java.nio.file.Path;

public interface TrackMeshProcessRunner {

    ProcessResult run(Path trackMeshRoot, long sessionKey, String circuitSlug, Path outputPath)
            throws IOException;

    record ProcessResult(int exitCode, String stderr) {}
}
