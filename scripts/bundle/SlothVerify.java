import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Checks Sloth's output with the JVM's own bytecode verifier and undoes what it broke.
  *
  * Sloth rewrites whole classes, and it has emitted invalid stack-map frames for at least one
  * constructor that contains no lazy val at all (sttp's SafeGZIPInputStream: a VerifyError, so every
  * gzip-encoded upstream response killed its request). For each class whose bytes Sloth changed,
  * this links the class — `getDeclaredConstructors()` verifies without running static initialisers —
  * and on a VerifyError that names the class, puts the original bytes back. A class without lazy vals
  * loses nothing by that; one with them keeps the old scheme (and the warning) but works.
  *
  * Usage: java SlothVerify.java <hierarchy-classpath> <original.jar> <patched.jar> [<original> <patched>]…
  * Prints one "restored <jar> <class>" line per class put back; exits 1 if a restored class still
  * does not verify. Run in its own JVM so the classes it loads never touch the build's. */
public class SlothVerify {
  public static void main(String[] args) throws Exception {
    URL[] urls = Arrays.stream(args[0].split(File.pathSeparator)).filter(s -> !s.isEmpty())
        .map(SlothVerify::url).toArray(URL[]::new);
    boolean failed = false;
    for (int i = 1; i + 1 < args.length; i += 2) {
      Path original = Paths.get(args[i]), patched = Paths.get(args[i + 1]);
      Map<String, byte[]> changed = changedClasses(original, patched);
      if (changed.isEmpty()) continue;
      Set<String> broken = unverifiable(urls, changed.keySet());
      if (broken.isEmpty()) continue;
      restore(patched, original, broken);
      for (String c : broken) System.out.println("restored " + patched.getFileName() + " " + c);
      Set<String> still = unverifiable(urls, broken);
      for (String c : still) System.out.println("STILL BROKEN " + patched.getFileName() + " " + c);
      failed |= !still.isEmpty();
    }
    System.exit(failed ? 1 : 0);
  }

  private static URL url(String p) {
    try { return new File(p).toURI().toURL(); } catch (IOException e) { throw new UncheckedIOException(e); }
  }

  /** Entry name → original bytes, for every .class whose bytes differ between the two jars. */
  private static Map<String, byte[]> changedClasses(Path original, Path patched) throws IOException {
    Map<String, byte[]> before = new HashMap<>();
    try (ZipFile z = new ZipFile(original.toFile())) {
      for (ZipEntry e : Collections.list(z.entries()))
        if (e.getName().endsWith(".class")) before.put(e.getName(), z.getInputStream(e).readAllBytes());
    }
    Map<String, byte[]> changed = new TreeMap<>();
    try (ZipFile z = new ZipFile(patched.toFile())) {
      for (ZipEntry e : Collections.list(z.entries())) {
        byte[] was = before.get(e.getName());
        if (was != null && !Arrays.equals(was, z.getInputStream(e).readAllBytes())) changed.put(e.getName(), was);
      }
    }
    return changed;
  }

  /** The entries among `entries` whose class fails verification in a fresh loader over `urls`. A
    * class that cannot be LOADED (an optional dependency missing) is not Sloth's doing and is kept. */
  private static Set<String> unverifiable(URL[] urls, Set<String> entries) throws IOException {
    Set<String> broken = new TreeSet<>();
    try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
      for (String entry : entries) {
        String internal = entry.substring(0, entry.length() - ".class".length());
        if (internal.endsWith("module-info")) continue;
        try {
          Class.forName(internal.replace('/', '.'), false, loader).getDeclaredConstructors();
        } catch (VerifyError e) {
          if (String.valueOf(e.getMessage()).contains(internal)) broken.add(entry);
        } catch (LinkageError | ClassNotFoundException e) {
          // missing optional dependency or a broken superclass: judged in its own jar's pass
        }
      }
    }
    return broken;
  }

  /** Rewrite `patched` in place with the `entries` taken from `original`, keeping entry order. */
  private static void restore(Path patched, Path original, Set<String> entries) throws IOException {
    Map<String, byte[]> originals = new HashMap<>();
    try (ZipFile z = new ZipFile(original.toFile())) {
      for (String e : entries) originals.put(e, z.getInputStream(z.getEntry(e)).readAllBytes());
    }
    Path tmp = patched.resolveSibling(patched.getFileName() + ".verify-tmp");
    try (ZipFile in = new ZipFile(patched.toFile());
         ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
      for (ZipEntry e : Collections.list(in.entries())) {
        out.putNextEntry(new ZipEntry(e.getName()));
        byte[] body = originals.containsKey(e.getName()) ? originals.get(e.getName()) : in.getInputStream(e).readAllBytes();
        out.write(body);
        out.closeEntry();
      }
    }
    Files.move(tmp, patched, StandardCopyOption.REPLACE_EXISTING);
  }
}
