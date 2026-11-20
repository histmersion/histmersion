import sbt.*
import sbt.Keys.*
import sbt.protocol.testing.TestResult

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}

/** CI's test shards (.github/workflows/ci.yml, scripts/ci/): every project, inert unless the environment asks.
  *
  *   - `ciSuites` writes `target/ci/suites/<project>.tsv`, one `<project> TAB <suite>` row per defined test suite: what
  *     the shards must cover between them (`sbt ciSuites` at the root writes every aggregated project's file).
  *   - `HISTMERSION_CI_SHARD_FILE=<file>` (rows `<project> TAB <suite>`, as scripts/ci/plan.py writes them) narrows
  *     every project's suites to the file's: `sbt 'testOnly *'` at the root then runs exactly one shard.
  *   - `HISTMERSION_CI_REPORT_DIR=<dir>` records each finished suite as `<project> TAB <suite> TAB <result> TAB passed
  *     TAB failed TAB errors TAB skipped TAB ignored` in `<dir>/<project>.tsv` (a suite that aborted is `error`), and
  *     every skipped test with its reason in `<dir>/<project>.skips.tsv`; scripts/ci/check.py holds the union of the
  *     shards' reports to the suites listed: each run exactly once.
  */
object CiShard extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  object autoImport {
    val ciSuites = taskKey[Unit]("Write target/ci/suites/<project>.tsv: this project's test suites, for CI's shards")
  }
  import autoImport.*

  val ShardFileEnv = "HISTMERSION_CI_SHARD_FILE"
  val ReportDirEnv = "HISTMERSION_CI_REPORT_DIR"

  private def env(name: String): Option[String] = sys.env.get(name).map(_.trim).filter(_.nonEmpty)

  /** The suites of `project` a shard file lists. */
  private def shardSuites(file: File, project: String): Set[String] =
    IO.readLines(file)
      .map(_.split("\t", -1).toList)
      .collect { case p :: suite :: Nil if p == project => suite }
      .toSet

  override def projectSettings: Seq[Setting[?]] = Seq(
    ciSuites := Def.uncached {
      val id = thisProject.value.id
      val names = (Test / definedTestNames).value.distinct.sorted
      val out = (ThisBuild / baseDirectory).value / "target" / "ci" / "suites" / s"$id.tsv"
      IO.writeLines(out, names.map(n => s"$id\t$n"))
      streams.value.log.info(s"ciSuites: ${names.size} suite(s) of $id → $out")
    },
    Test / testOptions := Def.uncached {
      val id = thisProject.value.id
      val before = (Test / testOptions).value
      env(ShardFileEnv) match {
        case Some(f) =>
          val file = new File(f)
          if !file.isFile then sys.error(s"$ShardFileEnv=$f: no such file")
          val mine = shardSuites(file, id)
          before :+ Tests.Filter(mine.contains)
        case None => before
      }
    },
    Test / testListeners := Def.uncached {
      val id = thisProject.value.id
      val before = (Test / testListeners).value
      env(ReportDirEnv) match {
        case Some(d) => before :+ new CiSuiteReport(id, Path.of(d))
        case None    => before
      }
    }
  )
}

/** Appends one row per finished suite of `project` to `<dir>/<project>.tsv`, and each skipped test with its reason to
  * `<dir>/<project>.skips.tsv` (CiShard). Thread-safe: forked groups report side by side.
  */
final class CiSuiteReport(project: String, dir: Path) extends TestsListener {
  import sbt.testing.{Event as TEvent, Status}

  final private class Counts {
    var passed, failed, errors, skipped, ignored = 0
  }
  private val counts = scala.collection.mutable.Map.empty[String, Counts]

  private def clean(s: String): String = s.replaceAll("[\t\r\n]+", " ").trim

  private def append(file: String, line: String): Unit = synchronized {
    Files.createDirectories(dir)
    val _ = Files.write(
      dir.resolve(file),
      (line + "\n").getBytes(UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    )
  }

  def doInit(): Unit = ()
  def doComplete(finalResult: TestResult): Unit = ()
  def startGroup(name: String): Unit = synchronized(counts.getOrElseUpdate(name, new Counts))

  def testEvent(event: TestEvent): Unit = synchronized {
    event.detail.foreach { (e: TEvent) =>
      val c = counts.getOrElseUpdate(e.fullyQualifiedName, new Counts)
      e.status match {
        case Status.Success                                    => c.passed += 1
        case Status.Failure                                    => c.failed += 1
        case Status.Error                                      => c.errors += 1
        case Status.Skipped | Status.Canceled | Status.Pending =>
          c.skipped += 1
          val test = e.selector match {
            case s: sbt.testing.TestSelector => s.testName
            case s                           => s.toString
          }
          val why = if e.throwable.isDefined then Option(e.throwable.get.getMessage).getOrElse("") else ""
          append(s"$project.skips.tsv", s"$project\t${e.fullyQualifiedName}\t${clean(test)}\t${clean(why)}")
        case Status.Ignored => c.ignored += 1
      }
    }
  }

  private def row(name: String, result: String): Unit = synchronized {
    val c = counts.getOrElse(name, new Counts)
    append(
      s"$project.tsv",
      s"$project\t$name\t$result\t${c.passed}\t${c.failed}\t${c.errors}\t${c.skipped}\t${c.ignored}"
    )
  }

  def endGroup(name: String, t: Throwable): Unit = row(name, "error")
  def endGroup(name: String, result: TestResult): Unit = row(name, result.toString.toLowerCase)
}
