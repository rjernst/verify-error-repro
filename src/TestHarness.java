import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

public class TestHarness {

    private static final Path javaHome = Path.of(System.getProperty("java.home"));
    private static final Path projectRoot = Path.of("");

    public static void main(String[] args) throws Exception{
        Path agentJar = compileJar(projectRoot.resolve("agent"));
        Path checkerJar = compileJar(projectRoot.resolve("checker"));
        Path mainJar = compileJar(projectRoot);

        var pb = new ProcessBuilder();
        pb.command(
                javaHome.resolve("bin/java").toString(),
                "-javaagent:" + agentJar,
                "--patch-module=java.base=" + checkerJar,
                "-Dagent.retransform=" + System.getProperty("agent.retransform"),
                "-cp", mainJar.toString(),
                "TestMain"
        );
        System.out.println(pb.command());
        pb.inheritIO();
        var process = pb.start();
        System.exit(process.waitFor());
    }

    private static Path compileJar(Path dir) throws IOException {
        Path buildDir = dir.resolve("build");
        Path compileDir = buildDir.resolve("compile");
        Path srcDir = dir.resolve("src");
        try (Stream<Path> srcFiles = Files.walk(srcDir)
                .filter(p -> p.toString().endsWith(".java"))) {
            compile(srcFiles.toList(), compileDir);
        }
        Path jarDir = buildDir.resolve("jar");
        Files.createDirectories(jarDir);
        Path jarFile = jarDir.resolve(dir.toAbsolutePath().getFileName() + ".jar");
        System.out.println("Creating " + jarFile);
        try (var os = Files.newOutputStream(jarFile);
             var jar = new JarOutputStream(os)) {
             try (Stream<Path> classFiles = Files.walk(compileDir).filter(p -> p.toString().endsWith(".class"))) {
                 for (var classFile : classFiles.toList()) {
                     String localPath = compileDir.relativize(classFile).toString();
                     jar.putNextEntry(new ZipEntry(localPath));
                     try (var is = Files.newInputStream(classFile)) {
                         is.transferTo(jar);
                     }
                 }
             }
             Path manifest = srcDir.resolve("META-INF/MANIFEST.MF");
             if (Files.exists(manifest)) {
                 jar.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                 try (var is = Files.newInputStream(manifest)) {
                     is.transferTo(jar);
                 }
             }
        }
        return jarFile;
    }

    private static void compile(List<Path> srcFiles, Path output) throws IOException{
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (var fileManager = compiler.getStandardFileManager(null, null, null)) {
            var compilationUnits = fileManager.getJavaFileObjects(srcFiles.toArray(new Path[0]));
            var options = List.of("-d", output.toString());
            var compileTask = compiler.getTask(null, fileManager, null, options, null, compilationUnits);

            if (compileTask.call() == false) {
                throw new AssertionError("Compilation failed: " + srcFiles);
            }
        }
    }
}
