package local.codexlimits;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class CliEnvironmentTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("codex cli test ");
        try {
            Path runtime = dir.resolve("test-node");
            Path cli = dir.resolve("codex");
            Files.writeString(runtime, "#!/bin/sh\nprintf 'runtime-ok'\n");
            Files.writeString(cli, "#!/usr/bin/env test-node\n");
            assert runtime.toFile().setExecutable(true);
            assert cli.toFile().setExecutable(true);
            ProcessBuilder builder = CliEnvironment.builder(cli.toString(), Map.of("PATH", "/usr/bin:/bin", "CODEX_HOME", "/existing"), "");
            assert builder.environment().get("CODEX_HOME").equals("/existing");
            Process p = builder.start();
            try {
                assert p.waitFor(5, TimeUnit.SECONDS) : "CLI did not exit";
                assert p.exitValue() == 0 : "GUI PATH failed to find runtime";
                assert new String(p.getInputStream().readAllBytes()).equals("runtime-ok");
            } finally { p.destroyForcibly(); }
            builder = CliEnvironment.builder("codex", Map.of("PATH", dir + ":/usr/bin:/bin"), "~/custom-codex");
            assert builder.command().get(0).equals(cli.toString());
            assert builder.environment().get("CODEX_HOME").equals(System.getProperty("user.home") + "/custom-codex");
            assert builder.environment().get("PATH").startsWith(dir + ":");
            assert CliEnvironment.expandHome("~/bin/codex").equals(System.getProperty("user.home") + "/bin/codex");
            assert CliEnvironment.expandHome("/bin/codex").equals("/bin/codex");
        } finally {
            Files.deleteIfExists(dir.resolve("codex"));
            Files.deleteIfExists(dir.resolve("test-node"));
            Files.deleteIfExists(dir);
        }
        System.out.println("CLI environment tests passed");
    }
}
