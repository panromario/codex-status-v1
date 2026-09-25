package local.codexlimits;

import java.io.File;
import java.nio.file.*;
import java.util.*;

final class CliEnvironment {
    static String expandHome(String path) {
        return path.equals("~") ? System.getProperty("user.home")
            : path.startsWith("~/") ? System.getProperty("user.home") + path.substring(1) : path;
    }

    static ProcessBuilder builder(String executable, Map<String, String> environment, String home) {
        executable = expandHome(executable);
        Map<String, String> env = new HashMap<>(environment);
        // macOS GUI processes often lack the directory containing npm's node runtime.
        Set<String> dirs = new LinkedHashSet<>();
        String inherited = env.getOrDefault("PATH", "");
        for (String dir : inherited.split(java.util.regex.Pattern.quote(File.pathSeparator)))
            if (!dir.isBlank()) dirs.add(dir);
        Path exe = Path.of(executable);
        if (exe.isAbsolute()) dirs.add(exe.getParent().toString());
        if (File.separatorChar == '/') {
            dirs.add("/opt/homebrew/bin");
            dirs.add("/usr/local/bin");
            dirs.add("/usr/bin");
            dirs.add("/bin");
        }
        env.put("PATH", String.join(File.pathSeparator, dirs));
        if (!home.isBlank()) env.put("CODEX_HOME", expandHome(home));
        // ProcessBuilder does not resolve a bare executable against its child PATH.
        if (exe.getParent() == null) for (String dir : dirs) {
            Path candidate = Path.of(dir).resolve(executable);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                executable = candidate.toAbsolutePath().toString();
                break;
            }
        }
        ProcessBuilder builder = new ProcessBuilder(executable, "app-server");
        builder.environment().clear();
        builder.environment().putAll(env);
        builder.directory(new File(System.getProperty("user.home")));
        return builder;
    }
}
