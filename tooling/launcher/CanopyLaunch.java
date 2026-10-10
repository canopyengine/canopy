import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builds a Gradle application, then starts Java with the original console handles. */
public class CanopyLaunch {
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
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (option.equals("--")) {
                gameArgs = Arrays.asList(args).subList(i + 1, args.length);
                break;
            }
            if (option.equals("--help") || option.equals("-h")) {
                System.out.println("Usage: java CanopyLaunch.java [-p PROJECT] [--module :game]"
                    + " [--gradle-arg ARG] [-- GAME_ARGS...]");
                return 0;
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
        return run(game, Path.of(fields.get(0)), true);
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
        Thread cleanup = new Thread(child::destroy, "canopy-launcher-child-cleanup");
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
