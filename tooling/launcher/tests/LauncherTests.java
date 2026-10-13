import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

/** Same dependency-free integration suite on Linux, Windows and macOS; requires JDK 17+. */
public class LauncherTests {
    private static Path launcher;
    private static Path project;
    private static Path scratch;
    private static volatile Process activeLaunch;
    private static final String APPLICATION = """
        plugins { application }
        java { toolchain { languageVersion.set(JavaLanguageVersion.of(%d)) } }
        application {
            mainClass.set("demo.Game")
            applicationName = "custom-game-name"
            applicationDefaultJvmArgs = listOf("-Dlauncher.flag=two words")
        }
        tasks.named<JavaExec>("run") { jvmArgs("-Dlauncher.run=run option") }
        tasks.named<Sync>("installDist") { into(layout.buildDirectory.dir("custom distribution")) }
        """;
    private static final String GAME = """
        package demo;
        import java.util.Base64;
        import java.nio.charset.StandardCharsets;
        public class Game {
            public static void main(String[] args) throws Exception {
                if (args.length > 1 && args[0].equals("--window-fixture")) {
                    var record = new StringBuilder("CONSOLE=" + (System.console() != null) + "\\n");
                    record.append("FLAG=").append(System.getProperty("launcher.flag")).append("\\n");
                    record.append("DIR=").append(System.getProperty("user.dir")).append("\\n");
                    record.append("PID=").append(ProcessHandle.current().pid()).append("\\n");
                    for (String arg : args) record.append("ARG=").append(Base64.getEncoder()
                        .encodeToString(arg.getBytes(StandardCharsets.UTF_8))).append("\\n");
                    record.append("READY\\n");
                    var marker = java.nio.file.Path.of(args[1]);
                    java.nio.file.Files.writeString(marker, record);
                    System.out.print("\\u001b[38;2;120;240;120mCanopy window fixture\\u001b[0m\\n");
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                    while (!java.nio.file.Files.exists(marker.resolveSibling("window-close"))) {
                        if (System.nanoTime() >= deadline) throw new AssertionError("Window close signal missing");
                        Thread.sleep(20);
                    }
                    System.exit(7);
                }
                System.out.println("FLAG=" + System.getProperty("launcher.flag"));
                System.out.println("RUN=" + System.getProperty("launcher.run"));
                System.out.println("JAVA=" + System.getProperty("java.home"));
                System.out.println("DIR=" + System.getProperty("user.dir"));
                System.out.println("CONSOLE=" + (System.console() != null));
                for (String arg : args) {
                    System.out.println("ARG=" + Base64.getEncoder().encodeToString(arg.getBytes(StandardCharsets.UTF_8)));
                }
                System.out.println("INPUT=" + new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine());
                System.exit(7);
            }
        }
        """;

    public static void main(String[] args) throws Exception {
        Path repo = Path.of(args.length == 0 ? "." : args[0]).toRealPath();
        launcher = repo.resolve("tooling/launcher/CanopyLaunch.java");
        scratch = Files.createTempDirectory("canopy launcher tests ' ");
        project = scratch.resolve("game project & spaces");
        try {
            Files.createDirectories(project.resolve("gradle/wrapper"));
            for (String file : List.of("gradle-wrapper.jar", "gradle-wrapper.properties")) {
                Files.copy(repo.resolve("gradle/wrapper/" + file), project.resolve("gradle/wrapper/" + file));
            }
            Files.createDirectories(scratch.resolve("manifests"));
            Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"launcher-test\"\ninclude(\":game\")\n");
            Path source = project.resolve("game/src/main/java/demo/Game.java");
            Files.createDirectories(source.getParent());
            Files.writeString(source, GAME);
            Files.writeString(project.resolve("game/build.gradle.kts"), APPLICATION.formatted(Runtime.version().feature()));

            Result invalid = launch("", "--module", ":game;echo bad");
            require(invalid.code == 2, "Invalid module must fail before Gradle", invalid);
            Result missing = launch("", "--module");
            require(missing.code == 2, "Missing option value", missing);
            Result modes = launch("", "--window", "--current-terminal");
            require(modes.code == 2, "Conflicting terminal modes must fail before Gradle", modes);
            System.out.println("PASS: option and module validation");

            List<String> literal = List.of("two words", "& literal", "--project", "", "héllo", "$(echo nope)");
            List<String> options = new ArrayList<>(List.of("--module", ":game", "--"));
            options.addAll(literal);
            Result classpath = launch("keyboard\n", options.toArray(String[]::new));
            verifyGame(classpath, literal, project.resolve("game"));
            System.out.println("PASS: real Gradle module, custom distribution, JVM flags, literal arguments, stdin and exit code");

            Files.writeString(project.resolve("game/src/main/java/module-info.java"), "module launcher.game { }\n");
            Files.writeString(project.resolve("game/build.gradle.kts"),
                APPLICATION.formatted(Runtime.version().feature()) + "\napplication { mainModule.set(\"launcher.game\") }\n");
            Result modular = launch("keyboard\n", "--module", ":game", "--", "module argument");
            verifyGame(modular, List.of("module argument"), project.resolve("game"));
            System.out.println("PASS: JPMS application");

            Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"root-game\"\n");
            Files.createDirectories(project.resolve("src/main/java/demo"));
            Files.writeString(project.resolve("src/main/java/demo/Game.java"), GAME);
            Files.writeString(project.resolve("build.gradle.kts"), APPLICATION.formatted(Runtime.version().feature()));
            Result root = launch("keyboard\n", "--", "root argument");
            verifyGame(root, List.of("root argument"), project);
            System.out.println("PASS: root application");

            if (Boolean.getBoolean("canopy.tests.window")) {
                verifyWindow(literal, false);
                verifyWindow(literal, true);
            }
            if (System.getProperty("os.name").equals("Mac OS X")) verifyMacScript();

            Files.writeString(project.resolve("build.gradle.kts"), "error(\"deliberate build failure\")\n");
            Result failed = launch("keyboard\n");
            require(failed.code != 0 && !failed.output.contains("INPUT="), "Failed build must not launch game", failed);
            try (var files = Files.list(scratch.resolve("manifests"))) {
                require(files.findAny().isEmpty(), "Temporary manifests must be removed", failed);
            }
            System.out.println("PASS: build failure and temporary-file cleanup");
            System.out.println("All launcher integration checks passed.");
        } finally {
            try (var paths = Files.walk(scratch)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyWindow(List<String> literal, boolean cancel) throws Exception {
        Path marker = scratch.resolve("window ready & ' héllo.txt");
        Path close = marker.resolveSibling("window-close");
        Files.deleteIfExists(marker);
        Files.deleteIfExists(close);
        List<String> windowArgs = new ArrayList<>(List.of("--window-fixture", marker.toString()));
        windowArgs.addAll(literal);
        List<String> options = new ArrayList<>(List.of("--window", "--"));
        options.addAll(windowArgs);
        CompletableFuture<Result> pending = CompletableFuture.supplyAsync(() -> {
            try { return launch("", options.toArray(String[]::new)); }
            catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        });
        Result result;
        long gamePid = -1;
        try {
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
            while (!Files.exists(marker) || !Files.readString(marker).endsWith("READY\n")) {
                if (pending.isDone()) throw new AssertionError("Window exited before readiness: " + pending.get());
                if (System.nanoTime() >= deadline) throw new AssertionError("Window never became ready");
                Thread.sleep(20);
            }
            String record = Files.readString(marker);
            require(record.contains("CONSOLE=true"), "Game needs a real console in its window", new Result(0, record));
            require(record.contains("FLAG=two words"), "Window must preserve JVM flags", new Result(0, record));
            String cwd = record.lines().filter(l -> l.startsWith("DIR=")).findFirst().orElseThrow().substring(4);
            require(Files.isSameFile(Path.of(cwd), project), "Window working directory changed", new Result(0, record));
            for (String arg : windowArgs) {
                String encoded = Base64.getEncoder().encodeToString(arg.getBytes(StandardCharsets.UTF_8));
                require(record.lines().anyMatch(l -> l.equals("ARG=" + encoded)), "Window argument changed", new Result(0, record));
            }
            gamePid = Long.parseLong(record.lines().filter(l -> l.startsWith("PID=")).findFirst().orElseThrow().substring(4));
            if (cancel) {
                // Windows Process.destroy forcibly kills a JVM without its shutdown hooks.
                // Exercise game termination there; POSIX also verifies graceful launcher cancellation.
                if (System.getProperty("os.name").startsWith("Windows")) {
                    ProcessHandle.of(gamePid).orElseThrow().destroy();
                } else activeLaunch.destroy();
            }
        } finally {
            Files.writeString(close, "close");
            result = pending.get(30, TimeUnit.SECONDS);
        }
        require(cancel ? result.code != 0 : result.code == 7, "Window exit status must propagate", result);
        require(ProcessHandle.of(gamePid).map(ProcessHandle::isAlive).orElse(false) == false, "Game process leaked", result);
        try (var files = Files.list(scratch.resolve("manifests"))) {
            require(files.findAny().isEmpty(), "Window launcher files must be removed", result);
        }
        System.out.println(cancel ? "PASS: terminated process leaves no game or window launcher files"
            : "PASS: new console window, JVM flags, literal arguments, exit status and cleanup");
    }

    private static void verifyMacScript() throws Exception {
        Path classes = Files.createDirectory(scratch.resolve("launcher-classes"));
        int compiled = javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-d", classes.toString(), launcher.toString());
        if (compiled != 0) throw new AssertionError("Launcher compilation failed");
        try (var loader = new java.net.URLClassLoader(new java.net.URL[] {classes.toUri().toURL()})) {
            var method = loader.loadClass("CanopyLaunch").getDeclaredMethod("windowCommand", List.class,
                Path.class, Path.class, Path.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            var command = (List<String>) method.invoke(null, List.of("/path with ' quote/java", "/runner.java", "/request"),
                scratch.resolve("result"), scratch.resolve("pid"), scratch.resolve("owned window ' id"));
            Process check = new ProcessBuilder("osacompile", "-o", scratch.resolve("window.scpt").toString(),
                "-e", command.get(command.size() - 1)).inheritIO().start();
            if (!check.waitFor(30, TimeUnit.SECONDS) || check.exitValue() != 0) {
                check.destroyForcibly();
                throw new AssertionError("macOS terminal script does not compile");
            }
        }
        System.out.println("PASS: macOS owned-window script compiles with quoted paths");
    }

    private static void verifyGame(Result result, List<String> arguments, Path cwd) throws IOException {
        require(result.code == 7, "Game exit code must propagate", result);
        for (String expected : List.of("FLAG=two words", "RUN=run option", "INPUT=keyboard")) {
            require(result.output.contains(expected), "Missing " + expected, result);
        }
        for (String arg : arguments) {
            String encoded = Base64.getEncoder().encodeToString(arg.getBytes(StandardCharsets.UTF_8));
            require(result.output.lines().anyMatch(line -> line.equals("ARG=" + encoded)), "Argument not preserved: " + arg, result);
        }
        String home = result.output.lines().filter(line -> line.startsWith("JAVA=")).findFirst().orElseThrow().substring(5);
        require(Files.isSameFile(Path.of(home), Path.of(System.getProperty("java.home"))), "Selected Java toolchain", result);
        String dir = result.output.lines().filter(line -> line.startsWith("DIR=")).findFirst().orElseThrow().substring(4);
        require(Files.isSameFile(Path.of(dir), cwd), "Game working directory", result);
    }

    private static Result launch(String input, String... options) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        List<String> command = new ArrayList<>(List.of(java.toString(),
            "-Djava.io.tmpdir=" + scratch.resolve("manifests"), launcher.toString(), "-p", project.toString(),
            "--gradle-arg", "--no-daemon", "--gradle-arg", "--max-workers=2"));
        if (Boolean.getBoolean("canopy.tests.offline")) command.addAll(List.of("--gradle-arg", "--offline"));
        command.addAll(List.of(options));
        Path log = scratch.resolve("process.log");
        Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        activeLaunch = child;
        try {
            child.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            child.getOutputStream().close();
            if (!child.waitFor(4, TimeUnit.MINUTES)) {
                throw new AssertionError("Launcher timed out: " + Files.readString(log));
            }
            return new Result(child.exitValue(), Files.readString(log));
        } finally {
            activeLaunch = null;
            if (child.isAlive()) {
                child.descendants().forEach(ProcessHandle::destroyForcibly);
                child.destroyForcibly();
                child.waitFor();
            }
        }
    }

    private static void require(boolean condition, String message, Result result) {
        if (!condition) throw new AssertionError(message + "\nExit: " + result.code + "\n" + result.output);
    }

    private record Result(int code, String output) { }
}
