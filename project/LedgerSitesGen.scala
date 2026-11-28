import sbt.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

object LedgerSitesGen {
  val Outputs: Seq[String] = Seq("sentence-sites.tsv", "problem-symbols.tsv")
  val KeyFile = "ledger-sites.key"
  val KeyResource = "ledger-sites-key.txt"
  val LogFile = "ledger-sites.log"

  def dir(root: File): File = root / "target" / "guidelines"

  def semanticdbTargetRoots(out: File, scalaVersion: String): Seq[File] =
    if !out.isDirectory then Nil
    else {
      val stream = Files.find(
        out.toPath,
        4,
        (p, a) =>
          a.isDirectory && Set("meta", "test-meta")(p.getFileName.toString) &&
            p.getParent.getParent.getFileName.toString == s"scala-$scalaVersion"
      )
      try stream.iterator.asScala.map(_.toAbsolutePath.normalize.toFile).toList.sortBy(_.getPath)
      finally stream.close()
    }

  def fresh(dir: File, key: String): Boolean =
    Outputs.forall(o => (dir / o).isFile) && (dir / KeyFile).isFile && IO.read(dir / KeyFile).trim == key

  def tail(log: File, n: Int): String =
    if log.isFile then Files.readAllLines(log.toPath, UTF_8).asScala.takeRight(n).mkString("\n")
    else s"($log was not written)"

  def ensure(root: File, key: String, cmd: Seq[String], timeout: Long, log: Logger): Unit = {
    val out = dir(root)
    if fresh(out, key) then log.info(s"ledger sites: current in $out")
    else {
      IO.createDirectory(out)
      (KeyFile +: Outputs).foreach(o => IO.delete(out / o))
      val logFile = out / LogFile
      log.info(s"ledger sites: Scalafix over every module's SemanticDB → $out (at most ${timeout}s)")
      val builder = new ProcessBuilder(cmd.asJava)
        .directory(root)
        .redirectErrorStream(true)
        .redirectOutput(logFile)
        .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
      builder.environment().remove("JAVA_OPTS")
      builder.environment().remove("JDK_JAVA_OPTIONS")
      val started = System.nanoTime()
      val process = builder.start()
      val finished = process.waitFor(timeout, TimeUnit.SECONDS)
      if !finished then {
        process.descendants().iterator().asScala.foreach(_.destroyForcibly())
        process.destroyForcibly()
        val _ = process.waitFor(10, TimeUnit.SECONDS)
      }
      val seconds = (System.nanoTime() - started) / 1000000000L
      val written = finished && process.exitValue() == 0 && Outputs.forall(o => (out / o).isFile)
      if !written then
        sys.error(
          (if finished then s"ledger sites: Scalafix exited ${process.exitValue()} after ${seconds}s"
           else
             s"ledger sites: Scalafix killed with its child processes after ${timeout}s (${LedgerSitesPlan.TimeoutEnv})"
          ) +
            s" without writing ${Outputs.mkString(" and ")} (a stale or missing SemanticDB means a module was not " +
            s"compiled: sbt compile Test/compile); $logFile, the last 40 lines:\n${tail(logFile, 40)}\n" +
            s"command: ${cmd.mkString(" ")}"
        )
      IO.write(out / KeyFile, key + "\n")
      log.info(s"ledger sites: written in ${seconds}s")
    }
  }
}
