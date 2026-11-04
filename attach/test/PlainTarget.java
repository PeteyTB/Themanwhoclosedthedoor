/** Owned JVM fixture for proving unsupported attachment fails without terminating the target. */
public final class PlainTarget {
    public static void main(String[] args) throws Exception {
        System.out.println("READY");
        System.out.flush();
        System.in.read();
        System.out.println("ALIVE");
    }
}
