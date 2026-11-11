import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/** Builds a Gradle application, then starts Java directly, opening a window for Canopy terminal projects. */
public class CanopyLaunch {
    private static final String WINDOW_RUNNER = """
        import java.nio.file.*;
        import java.nio.charset.StandardCharsets;
        import java.util.*;
        import java.util.concurrent.TimeUnit;
        class WindowRunner {
            public static void main(String[] args) throws Exception {
                var fields = Arrays.asList(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8).split("\\u0000", -1));
                Path result = Path.of(fields.get(1));
                Files.writeString(Path.of(fields.get(2)), Long.toString(ProcessHandle.current().pid()));
                Process child = null;
                int status = 1;
                try {
                    child = new ProcessBuilder(fields.subList(3, fields.size()))
                        .directory(Path.of(fields.get(0)).toFile()).inheritIO().start();
                    Process game = child;
                    Thread cleanup = new Thread(() -> {
                        game.destroy();
                        try {
                            if (!game.waitFor(5, TimeUnit.SECONDS)) game.destroyForcibly();
                            Files.writeString(result, "130");
                        } catch (Exception ignored) { }
                    }, "canopy-window-game-cleanup");
                    Runtime.getRuntime().addShutdownHook(cleanup);
                    try { status = child.waitFor(); }
                    finally { Runtime.getRuntime().removeShutdownHook(cleanup); }
                } finally {
                    if (child != null && child.isAlive()) child.destroy();
                    Files.writeString(result, Integer.toString(status));
                }
                System.exit(status);
            }
        }
        """;
    private static final String INIT = """
        import org.gradle.api.plugins.JavaApplication
        import org.gradle.api.tasks.JavaExec
        import org.gradle.api.tasks.Sync

        allprojects {
            pluginManager.withPlugin("application") {
                val app = extensions.getByType<JavaApplication>()
                val install = tasks.named<Sync>("installDist")
                tasks.register("canopyLaunchManifest") {
                    dependsOn(install)
                    doLast {
                        val run = tasks.named<JavaExec>("run").get()
                        val fields = listOf(
                            projectDir.absolutePath,
                            run.javaLauncher.get().executablePath.asFile.absolutePath,
                            install.get().destinationDir.resolve("lib").absolutePath,
                            app.mainClass.orNull ?: error("Set application.mainClass before launching"),
                            app.mainModule.orNull.orEmpty(),
                        ) + (app.applicationDefaultJvmArgs.toList() + run.jvmArgs.orEmpty()).distinct()
                        file(providers.gradleProperty("canopyLaunchManifest").get())
                            .writeText(fields.joinToString("\\u0000"), Charsets.UTF_8)
                    }
                }
            }
        }
        """;

    public static void main(String[] args) {
        int status;
        try {
            status = launch(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Launcher: " + e.getMessage());
            status = 2;
        } catch (IOException e) {
            System.err.println("Launcher: " + e.getMessage());
            status = 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = 130;
        }
        System.exit(status);
    }

    private static int launch(String[] args) throws IOException, InterruptedException {
        Path project = Path.of(".");
        String module = ":";
        List<String> gradleArgs = new ArrayList<>();
        List<String> gameArgs = List.of();
        Boolean window = null;
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (option.equals("--")) {
                gameArgs = Arrays.asList(args).subList(i + 1, args.length);
                break;
            }
            if (option.equals("--help") || option.equals("-h")) {
                System.out.println("Usage: java CanopyLaunch.java [-p PROJECT] [--module :game]"
                    + " [--window | --current-terminal] [--gradle-arg ARG] [-- GAME_ARGS...]");
                System.out.println("Terminal projects open their own window; --current-terminal uses the caller's console.");
                return 0;
            }
            if (option.equals("--window") || option.equals("--current-terminal")) {
                if (window != null) throw new IllegalArgumentException("Choose only one terminal mode");
                window = option.equals("--window");
                continue;
            }
            if (!List.of("-p", "--project", "--module", "--gradle-arg").contains(option)) {
                throw new IllegalArgumentException("Unknown option: " + option + "; put game arguments after --");
            }
            if (++i >= args.length) throw new IllegalArgumentException("Missing value for " + option);
            switch (option) {
                case "-p", "--project" -> project = Path.of(args[i]);
                case "--module" -> module = args[i];
                case "--gradle-arg" -> gradleArgs.add(args[i]);
                default -> throw new AssertionError(option);
            }
        }
        if (!module.equals(":") && !module.matches("(:[A-Za-z0-9_-]+)+")) {
            throw new IllegalArgumentException("Module must be : or a path such as :game");
        }
        project = project.toRealPath();
        Path wrapper = project.resolve("gradle/wrapper/gradle-wrapper.jar");
        if (!Files.isRegularFile(wrapper)) throw new IOException("No Gradle wrapper JAR in " + project);
        Path temp = Files.createTempDirectory("canopy-launch-");
        Path init = temp.resolve("launcher.gradle.kts");
        Path manifest = temp.resolve("launch.bin");
        List<String> fields;
        try {
            Files.writeString(init, INIT, StandardCharsets.UTF_8);
            List<String> build = new ArrayList<>(List.of(
                javaExecutable(Path.of(System.getProperty("java.home"))).toString(),
                "-Xmx64m", "-Xms64m", "-jar", wrapper.toString(), "--console=plain",
                "-I", init.toString(), "-PcanopyLaunchManifest=" + manifest
            ));
            build.addAll(gradleArgs);
            build.add(module.equals(":") ? ":canopyLaunchManifest" : module + ":canopyLaunchManifest");
            int result = run(build, project, false);
            if (result != 0) return result;
            fields = Arrays.asList(Files.readString(manifest, StandardCharsets.UTF_8).split("\u0000", -1));
            if (fields.size() < 5 || fields.subList(0, 4).stream().anyMatch(String::isBlank)) {
                throw new IOException("Invalid Gradle launcher manifest");
            }
        } finally {
            Files.deleteIfExists(manifest);
            Files.deleteIfExists(init);
            Files.deleteIfExists(temp);
        }
        List<String> game = new ArrayList<>();
        game.add(fields.get(1));
        game.addAll(fields.subList(5, fields.size()));
        if (fields.get(4).isEmpty()) {
            // The JVM expands classpath wildcards; Windows Path rejects '*' as a filename.
            game.addAll(List.of("-cp", fields.get(2) + java.io.File.separator + "*", fields.get(3)));
        } else {
            game.addAll(List.of("--module-path", fields.get(2), "--module", fields.get(4) + "/" + fields.get(3)));
        }
        game.addAll(gameArgs);
        if (window == null) window = terminalProject(Path.of(fields.get(2)));
        if (window) return runInWindow(game, Path.of(fields.get(0)));
        return run(game, Path.of(fields.get(0)), true);
    }

    private static boolean terminalProject(Path libraries) throws IOException {
        try (var files = Files.list(libraries)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (var jar = new JarFile(file.toFile())) {
                    if (jar.getEntry("io/canopy/platforms/terminal/app/TerminalApp.class") != null) return true;
                }
            }
        }
        return false;
    }

    private static int runInWindow(List<String> game, Path directory) throws IOException, InterruptedException {
        Path temp = Files.createTempDirectory("canopy-window-");
        Path runner = temp.resolve("WindowRunner.java");
        Path request = temp.resolve("request.bin");
        Path result = temp.resolve("result.txt");
        Path pid = temp.resolve("runner.pid");
        Path ownedWindow = temp.resolve("window.txt");
        List<Path> files = List.of(result, pid, request, runner, ownedWindow);
        Thread cleanup = new Thread(() -> {
            stopWindowRunner(pid);
            closeMacWindow(ownedWindow);
            deleteWindowFiles(temp, files);
        }, "canopy-window-cleanup");
        Runtime.getRuntime().addShutdownHook(cleanup);
        try {
            Files.writeString(runner, WINDOW_RUNNER, StandardCharsets.UTF_8);
            List<String> fields = new ArrayList<>(List.of(directory.toString(), result.toString(), pid.toString()));
            fields.addAll(game);
            Files.writeString(request, String.join("\u0000", fields), StandardCharsets.UTF_8);
            List<String> child = List.of(javaExecutable(Path.of(System.getProperty("java.home"))).toString(),
                "-Xmx64m", "-Xms16m", runner.toString(), request.toString());
            int windowStatus = run(windowCommand(child, result, pid, ownedWindow), directory, false);
            if (Files.exists(result)) return Integer.parseInt(Files.readString(result).trim());
            return windowStatus == 0 ? 130 : windowStatus;
        } finally {
            stopWindowRunner(pid);
            closeMacWindow(ownedWindow);
            try { Runtime.getRuntime().removeShutdownHook(cleanup); }
            catch (IllegalStateException ignored) { }
            deleteWindowFiles(temp, files);
        }
    }

    private static void deleteWindowFiles(Path temp, List<Path> files) {
        try {
            for (Path file : files) Files.deleteIfExists(file);
            Files.deleteIfExists(temp);
        } catch (IOException ignored) { }
    }

    private static void closeMacWindow(Path ownedWindow) {
        if (!System.getProperty("os.name").equals("Mac OS X")) return;
        try {
            if (!Files.exists(ownedWindow)) return;
            long id = Long.parseLong(Files.readString(ownedWindow).trim());
            Process close = new ProcessBuilder("osascript", "-e", "tell application \"Terminal\"\n"
                + "if exists window id " + id + " then close window id " + id + " saving no\nend tell").start();
            if (!close.waitFor(5, TimeUnit.SECONDS)) close.destroyForcibly();
        } catch (IOException | InterruptedException | NumberFormatException ignored) { }
    }

    private static void stopWindowRunner(Path pid) {
        try {
            if (!Files.exists(pid)) return;
            ProcessHandle.of(Long.parseLong(Files.readString(pid).trim())).ifPresent(child -> {
                child.descendants().forEach(ProcessHandle::destroy);
                child.destroy();
                try { child.onExit().get(6, TimeUnit.SECONDS); }
                catch (Exception ignored) { child.destroyForcibly(); }
            });
        } catch (IOException | NumberFormatException ignored) { }
    }

    private static List<String> windowCommand(List<String> child, Path result, Path pid, Path ownedWindow) throws IOException {
        String os = System.getProperty("os.name");
        if (os.startsWith("Windows")) {
            String arguments = child.subList(1, child.size()).stream()
                .map(s -> "\"" + s + "\"").collect(java.util.stream.Collectors.joining(" "));
            String script = "$ErrorActionPreference='Stop'; $p=Start-Process -FilePath "
                + powershellQuote(child.get(0)) + " -ArgumentList " + powershellQuote(arguments)
                + " -PassThru; [IO.File]::WriteAllText(" + powershellQuote(pid.toString())
                + ",$p.Id.ToString()); $p.WaitForExit(); exit $p.ExitCode";
            return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand",
                Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)));
        }
        if (os.equals("Mac OS X")) {
            String command = "printf '%s' $$ > " + shellQuote(pid.toString()) + "; "
                + child.stream().map(CanopyLaunch::shellQuote).collect(java.util.stream.Collectors.joining(" "))
                + "; code=$?; if [ ! -f " + shellQuote(result.toString()) + " ]; then printf '%s' \"$code\" > "
                + shellQuote(result.toString()) + "; fi; exit \"$code\"";
            String script = """
                use framework "Foundation"
                use scripting additions
                tell application "Terminal"
                    set ownedTab to do script %s
                    set ownedWindow to first window whose selected tab is ownedTab
                    set ownedId to id of ownedWindow
                    activate
                end tell
                do shell script ("printf '%%s' " & (ownedId as text) & " > " & %s)
                set manager to current application's NSFileManager's defaultManager()
                repeat
                    tell application "Terminal"
                        if not (exists ownedWindow) then return
                        set activeProcess to busy of ownedTab
                    end tell
                    if not activeProcess then
                        if (manager's fileExistsAtPath:%s) as boolean then exit repeat
                    end if
                    delay 0.1
                end repeat
                tell application "Terminal"
                    if exists ownedWindow then close ownedWindow saving no
                end tell
                """.formatted(applescriptQuote(command), applescriptQuote(shellQuote(ownedWindow.toString())),
                    applescriptQuote(result.toString()));
            return List.of("osascript", "-e", script);
        }
        if (os.equals("Linux")) {
            if (System.getenv("DISPLAY") == null && System.getenv("WAYLAND_DISPLAY") == null) {
                throw new IOException("A desktop display is required for a new terminal window; use --current-terminal here");
            }
            for (String terminal : List.of("kitty", "alacritty", "xterm")) {
                if (terminal.equals("xterm") && System.getenv("DISPLAY") == null) continue;
                Path executable = findExecutable(terminal);
                if (executable == null) continue;
                List<String> command = new ArrayList<>(List.of(executable.toString()));
                if (terminal.equals("kitty")) command.addAll(List.of("--override", "close_on_child_death=yes", "--title", "Canopy"));
                else if (terminal.equals("alacritty")) command.addAll(List.of("--title", "Canopy", "-e"));
                else command.addAll(List.of("-T", "Canopy", "-tn", "xterm-256color", "-e"));
                command.addAll(child);
                return command;
            }
            throw new IOException("Install kitty, alacritty or xterm to open the game window, or use --current-terminal");
        }
        throw new IOException("Terminal windows are not supported on " + os + "; use --current-terminal");
    }

    private static Path findExecutable(String name) {
        for (String directory : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
            Path file = Path.of(directory).resolve(name);
            if (Files.isRegularFile(file) && Files.isExecutable(file)) return file;
        }
        return null;
    }

    private static String shellQuote(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }
    private static String powershellQuote(String value) { return "'" + value.replace("'", "''") + "'"; }
    private static String applescriptQuote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static Path javaExecutable(Path home) {
        return home.resolve("bin").resolve(System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    }

    private static int run(List<String> command, Path directory, boolean consoleInput)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        if (consoleInput) builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        Process child = builder.start();
        Thread cleanup = new Thread(() -> {
            child.descendants().forEach(ProcessHandle::destroy);
            child.destroy();
        }, "canopy-launcher-child-cleanup");
        Runtime.getRuntime().addShutdownHook(cleanup);
        try {
            if (!consoleInput) child.getOutputStream().close();
            return child.waitFor();
        } finally {
            if (child.isAlive()) child.destroy();
            try {
                Runtime.getRuntime().removeShutdownHook(cleanup);
            } catch (IllegalStateException ignored) {
                // Shutdown is already running the hook (for example after Ctrl+C).
            }
        }
    }
}
