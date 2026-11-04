package dev.levrn.attach;

import com.sun.tools.attach.VirtualMachine;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;

/** Out-of-process controller. Uses only the JVM's documented attach mechanism. */
public final class AttachMain {
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception e) {
            System.err.println("Levrn: " + e.getMessage());
            System.err.println("Use the same Windows user as Minecraft and a Java 21 JDK. The target must allow Java agent loading.");
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--list")) {
            for (var vm : VirtualMachine.list()) {
                // Game arguments may contain access tokens. Never print the full JVM command line.
                String main = vm.displayName().strip().split("\\s+", 2)[0];
                System.out.println(vm.id() + "\t" + main);
            }
            return;
        }
        if (args.length != 3 || !args[0].equals("--attach") || !args[1].matches("[1-9][0-9]*"))
            throw new IllegalArgumentException("Usage: --list OR --attach <PID> <Levrn client JAR>");
        Path payload = Path.of(args[2]).toRealPath();
        Path agent = Path.of(AttachMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        Path requestDir = Files.createTempDirectory("levrn-attach-");
        Path request = requestDir.resolve("request.properties"), result = requestDir.resolve("result.properties");
        Properties p = new Properties();
        p.setProperty("payload", payload.toString()); p.setProperty("result", result.toString());
        try {
            try (OutputStream out = Files.newOutputStream(request)) { p.store(out, "Levrn local attach request"); }
            VirtualMachine vm = VirtualMachine.attach(args[1]);
            try {
                var props = vm.getSystemProperties();
                if (!props.getProperty("java.specification.version", "").equals("21"))
                    throw new IllegalStateException("The target is not Java 21; nothing was loaded.");
                String options = Base64.getUrlEncoder().withoutPadding().encodeToString(request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                vm.loadAgent(agent.toString(), options);
            } finally { vm.detach(); }
            long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
            while (System.nanoTime() < deadline) {
                if (Files.exists(result)) {
                    Properties response = new Properties();
                    try (InputStream in = Files.newInputStream(result)) { response.load(in); }
                    String message = response.getProperty("message", "No result message");
                    if (!response.getProperty("status", "").equals("OK")) throw new IllegalStateException(message);
                    System.out.println(message); return;
                }
                Thread.sleep(100);
            }
            throw new IllegalStateException("No game-thread acknowledgement within 25 seconds. Attachment is unconfirmed; check Minecraft's latest.log before retrying.");
        } finally {
            Files.deleteIfExists(request); Files.deleteIfExists(result); Files.deleteIfExists(requestDir);
        }
    }
}
