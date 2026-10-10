import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Same dependency-free integration suite on Linux, Windows and macOS; requires JDK 17+. */
public class LauncherTests {
    private static Path launcher;
    private static Path project;
    private static Path scratch;
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
        scratch = Files.createTempDirectory("canopy launcher tests ");
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
        try {
            child.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            child.getOutputStream().close();
            if (!child.waitFor(4, TimeUnit.MINUTES)) {
                throw new AssertionError("Launcher timed out: " + Files.readString(log));
            }
            return new Result(child.exitValue(), Files.readString(log));
        } finally {
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
