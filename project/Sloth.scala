import sbt.*
import sbt.given
import sbt.Keys.*
import sbt.librarymanagement.LibraryManagementCodec.given
import sbt.util.CacheImplicits.given

/** VirtusLab Sloth (https://github.com/VirtusLab/sloth) rewrites the lazy-val bytecode of jars that were compiled by
  * Scala < 3.8 (circe, cats, tapir, sttp, sconfig, magnolia…) from the `sun.misc.Unsafe` scheme to the `VarHandle`
  * scheme Scala 3.8 emits. Without it every native binary prints JDK 25's "terminally deprecated method in
  * sun.misc.Unsafe" warning on first lazy val, and JDK 26 removes the method. The Sloth CLI jar is not published; build
  * it with `sbt cli/assembly` in a Sloth checkout and point `SLOTH_JAR` at it (default: a checkout next to this repo,
  * as CI makes one, or next to the main worktree when building from a `git worktree`). Absent → the native image is
  * built from the unpatched jars and a log line says so; `scripts/bundle/smoke.sh` then fails on the warning.
  */
object Sloth extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val slothJar = settingKey[Option[File]]("Sloth CLI assembly used to patch pre-3.8 lazy vals before native-image")
  }
  import autoImport.*

  override def buildSettings: Seq[Setting[?]] = Seq(
    slothJar := {
      // SLOTH_JAR first; then a checkout next to this one, then one next to the MAIN worktree when this
      // is a linked `git worktree` (#874) — see project/SlothLookup.scala
      val repo = (ThisBuild / baseDirectory).value
      val checkouts = SlothLookup.checkouts(repo, SlothLookup.gitCommonDir(repo))
      val candidates = sys.env.get("SLOTH_JAR").map(file) ++
        checkouts.flatMap(c => (c / "cli" / "target" * "scala-*" / "sloth.jar").get())
      candidates.find(_.isFile)
    }
  )

  /** Copy every classpath jar to `target/sloth/` and run Sloth over it (in place, with the whole classpath as type
    * hierarchy so stack-map frames resolve). A copy is re-patched only when the original's CONTENT changed — its
    * SHA-256 is kept beside the copy. (Timestamps were the signal once, and a rebuilt jar of our own went unnoticed:
    * the native binaries shipped stale code.) Returns the classpath to hand to native-image.
    *
    * Speed (docs/research-build-speed.md §4): our own jars (`histmersion-*`, compiled by Scala 3.8 — no Unsafe lazy
    * vals) are copied, not patched; the Sloth runs go side by side (one JVM per jar, at most half the cores), reading
    * the ORIGINAL jars as hierarchy (patching changes method bodies, never a type) so no run reads a jar another is
    * rewriting; and a patched, verified jar is kept in `store` (the checkout's target/sloth-store, keyed by the
    * original's and Sloth's SHA-256) so the CLI reuses what the server's image patched instead of patching the same 30
    * jars again.
    */
  def patch(log: Logger, sloth: Option[File], entries: Seq[File], outDir: File, store: File): Seq[File] =
    sloth match {
      case None =>
        log.warn(
          "sloth: no Sloth jar found (SLOTH_JAR) — native image keeps pre-3.8 lazy vals (sun.misc.Unsafe warning)"
        )
        entries
      case Some(jar) =>
        IO.createDirectory(outDir)
        val jars = entries.filter(_.getName.endsWith(".jar"))
        val dirs = entries.filterNot(_.getName.endsWith(".jar"))
        val copies = jars.map(j => j -> outDir / j.getName)
        def sha(f: File): String = {
          val md = java.security.MessageDigest.getInstance("SHA-256")
          md.digest(IO.readBytes(f)).map(b => f"$b%02x").mkString
        }
        def marker(dst: File): File = new File(dst.getPath + ".src-sha256")
        val hashes = copies.map { case (src, _) => src -> sha(src) }.toMap
        val slothSha = sha(jar)
        val stale = copies.filter { case (src, dst) =>
          !dst.isFile || !marker(dst).isFile || IO.read(marker(dst)).trim != hashes(src)
        }
        def stored(src: File): File = store / s"${hashes(src).take(32)}-${slothSha.take(16)}" / src.getName
        val (own, others) = stale.partition { case (src, _) => src.getName.startsWith("histmersion-") }
        val (reused, toPatch) = others.partition { case (src, _) => stored(src).isFile }
        stale.foreach { case (src, dst) => IO.delete(marker(dst)) }
        own.foreach { case (src, dst) => IO.copyFile(src, dst) }
        reused.foreach { case (src, dst) => IO.copyFile(stored(src), dst) }
        toPatch.foreach { case (src, dst) => IO.copyFile(src, dst) }
        val hierarchy = (copies.map(_._2) ++ dirs).mkString(java.io.File.pathSeparator)
        val originals = (jars ++ dirs).mkString(java.io.File.pathSeparator)
        val javaBin = sys.env.get("JAVA_HOME").map(_ + "/bin/java").getOrElse("java")
        val par = math.max(1, math.min(8, java.lang.Runtime.getRuntime.availableProcessors / 2))
        val pool = java.util.concurrent.Executors.newFixedThreadPool(par)
        val runs = try {
          val fs = toPatch.map { case (_, dst) =>
            pool.submit(new java.util.concurrent.Callable[(File, Int)] {
              def call(): (File, Int) = dst -> scala.sys.process
                .Process(
                  Seq(javaBin, "-jar", jar.getAbsolutePath, dst.getAbsolutePath, "--hierarchy-classpath", originals)
                )
                .!(scala.sys.process.ProcessLogger(_ => (), e => if e.contains("Error") then log.error(s"sloth: $e")))
            })
          }
          fs.map(_.get())
        } finally pool.shutdown()
        runs.collectFirst { case (dst, rc) if rc != 0 => sys.error(s"sloth failed on ${dst.getName} (exit $rc)") }
        // Sloth has emitted classes the JVM rejects (sttp's SafeGZIPInputStream: every gzip-encoded
        // upstream response failed). The JVM's verifier checks every class it changed; a rejected one
        // gets its original bytes back. See scripts/bundle/SlothVerify.java.
        if toPatch.nonEmpty then {
          val verifier = (file("scripts") / "bundle" / "SlothVerify.java").getAbsolutePath
          val pairs = toPatch.flatMap { case (src, dst) => Seq(src.getAbsolutePath, dst.getAbsolutePath) }
          val out = new StringBuilder
          val rc = scala.sys.process
            .Process(Seq(javaBin, verifier, hierarchy) ++ pairs)
            .!(scala.sys.process.ProcessLogger(l => out.append(l).append('\n'), e => out.append(e).append('\n')))
          out.toString.linesIterator.filter(_.nonEmpty).foreach(l => log.warn(s"sloth verify: $l"))
          if rc != 0 then sys.error("sloth verify: a class stays unverifiable after restoring its original bytes")
        }
        // only a copy that was patched AND verified is marked current — and kept for the next module
        toPatch.foreach { case (src, dst) =>
          val keep = stored(src)
          val tmp = new File(keep.getPath + s".tmp-${java.util.UUID.randomUUID()}")
          IO.copyFile(dst, tmp)
          java.nio.file.Files.move(
            tmp.toPath,
            keep.toPath,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
          )
        }
        stale.foreach { case (src, dst) => IO.write(marker(dst), hashes(src)) }
        log.info(
          s"sloth: ${toPatch.size} jar(s) patched and verified ($par at a time), ${reused.size} reused, " +
            s"${own.size} of ours copied, ${copies.size - stale.size} up to date → $outDir"
        )
        copies.map(_._2) ++ dirs
    }

}
