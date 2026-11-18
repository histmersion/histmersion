package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

/** Every rule of the guideline inventory (`tools/refactor/GuidelineInventory.scala`: G3misplaced, G9prefix, G44…G49,
  * G58raw and the silent classes silentCompare, silentInterp, silentPure) as a ratchet: a count per rule, module and
  * scope (main or test) in `architecture/guidelines-ratchet.tsv` that may only go down.
  *
  * The inventory is a Scalafix rule over the SemanticDB the normal compile writes (`semanticdbEnabled`), run through
  * scala-cli over every module, main and test. A count above the file fails, naming the new findings (path:line) of the
  * files changed since the file was last committed; a count below it fails too, until the file is regenerated with
  * `HISTMERSION_UPDATE_ARCHITECTURE=1 sbt 'server/testOnly *GuidelineRatchetSuite'`, which writes the file when it is
  * missing and otherwise refuses to write a count that rises.
  */
class GuidelineRatchetSuite extends munit.FunSuite:
  import GuidelineRatchetSuite.*

  override val munitTimeout = scala.concurrent.duration.Duration(20, "min")

  test("no guideline count rises above architecture/guidelines-ratchet.tsv, and every lowered count is written down"):
    val findings = Inventory.findings
    val actual = Ratchet.counts(findings)
    val listed = if Files.isRegularFile(RatchetFile) then
      Ratchet.parse(Files.readAllLines(RatchetFile, UTF_8).asScala.toList)
    else Map.empty
    val rises = Ratchet.rises(listed, actual)
    val drops = Ratchet.drops(listed, actual)
    def risen: String =
      val changed = Changes.since(RatchetFile)
      rises
        .map { (key, was, now) =>
          val shown = Ratchet.newFindings(findings, key, changed)
          (s"${key.rule} ${key.module} ${key.scope}: $was -> $now" :: shown
            .map(f => s"  ${f.path}:${f.line} ${f.detail}"))
            .mkString("\n")
        }
        .mkString("\n")
    if sys.env.get("HISTMERSION_UPDATE_ARCHITECTURE").contains("1") then
      assert(
        rises.isEmpty || !Files.isRegularFile(RatchetFile),
        s"not regenerated: these counts rose (fix the new findings first)\n$risen"
      )
      val _ = Files.write(RatchetFile, Ratchet.render(actual).asJava, UTF_8)
    else
      assert(
        rises.isEmpty && drops.isEmpty,
        (Option.when(rises.nonEmpty)(s"new guideline violations:\n$risen") ++
          Option.when(drops.nonEmpty)(
            drops
              .map((key, was, now) =>
                s"lowered — regenerate architecture/guidelines-ratchet.tsv: ${key.rule} ${key.module} ${key.scope}: $was -> $now"
              )
              .mkString("\n")
          )).mkString("\n")
      )

  test("findings are counted per rule, module and scope"):
    val findings = List(
      Finding("G44", "core", "modules/core/src/main/scala/A.scala", 3, "value:Citekey"),
      Finding("G44", "core", "modules/core/src/main/scala/B.scala", 4, "value:Citekey"),
      Finding("G44", "core", "modules/core/src/test/scala/ASuite.scala", 5, "value:Citekey"),
      Finding("G58raw", "db", "modules/db/src/main/scala/Q.scala", 6, "operator:raw")
    )
    assertEquals(
      Ratchet.counts(findings),
      Map(Key("G44", "core", "main") -> 2, Key("G44", "core", "test") -> 1, Key("G58raw", "db", "main") -> 1)
    )

  test("a count above the file rises, one below it drops, and a key missing from the file starts at zero"):
    val listed =
      Map(Key("G44", "core", "main") -> 2, Key("G49", "web", "main") -> 5, Key("G47root", "api", "main") -> 1)
    val actual =
      Map(Key("G44", "core", "main") -> 3, Key("G49", "web", "main") -> 4, Key("silentPure", "cli", "test") -> 1)
    assertEquals(
      Ratchet.rises(listed, actual),
      List((Key("G44", "core", "main"), 2, 3), (Key("silentPure", "cli", "test"), 0, 1))
    )
    assertEquals(
      Ratchet.drops(listed, actual),
      List((Key("G47root", "api", "main"), 1, 0), (Key("G49", "web", "main"), 5, 4))
    )

  test("the ratchet file reads back what it was written from"):
    val counts = Map(Key("G44", "core", "main") -> 2, Key("G58raw", "db", "test") -> 7)
    assertEquals(Ratchet.parse(Ratchet.render(counts)), counts)

  test("the new findings shown are those of the risen key in the changed files, or all of the key's when none changed"):
    val a = Finding("G44", "core", "modules/core/src/main/scala/A.scala", 3, "value:Citekey")
    val b = Finding("G44", "core", "modules/core/src/main/scala/B.scala", 4, "value:Citekey")
    val other = Finding("G49", "core", "modules/core/src/main/scala/B.scala", 4, "lambda:map;Citekey")
    val key = Key("G44", "core", "main")
    assertEquals(Ratchet.newFindings(List(a, b, other), key, Set("modules/core/src/main/scala/B.scala")), List(b))
    assertEquals(Ratchet.newFindings(List(a, b, other), key, Set.empty), List(a, b))

  test("an inventory row is a finding; the header is not"):
    val rows =
      List("rule\tmodule\tpath\tline\tdetail", "G44\tcore\tmodules/core/src/test/scala/A.scala\t3\tvalue:Citekey")
    assertEquals(
      Finding.parse(rows),
      List(Finding("G44", "core", "modules/core/src/test/scala/A.scala", 3, "value:Citekey"))
    )

  test("the inventory reads the SemanticDB of meta and test-meta, never a leftover one beside the classes"):
    val out = Files.createTempDirectory("ratchet-out")
    val dirs =
      List("jvm/scala-3.10.0/m/meta", "jvm/scala-3.10.0/m/test-meta", "jvm/scala-3.10.0/m/classes/META-INF/semanticdb")
    dirs.foreach(d => Files.createDirectories(out.resolve(d)))
    assertEquals(
      Inventory.semanticdbTargetRoots(out).map(p => out.toAbsolutePath.normalize.relativize(p).toString),
      List("jvm/scala-3.10.0/m/meta", "jvm/scala-3.10.0/m/test-meta")
    )
    assertEquals(Inventory.semanticdbTargetRoots(out.resolve("missing")), Nil)

object GuidelineRatchetSuite:

  val RatchetFile: Path = Path.of("architecture/guidelines-ratchet.tsv")

  /** A count's place: the inventory's rule, the module (`modules/<module>/src/…`) and whether the file is a test. */
  final case class Key(rule: String, module: String, scope: String) derives CanEqual

  final case class Finding(rule: String, module: String, path: String, line: Int, detail: String):
    def key: Key = Key(rule, module, if path.contains("/src/test/") then "test" else "main")

  object Finding:
    /** The inventory's report: `rule, module, path, line, detail`, under a header. */
    def parse(rows: List[String]): List[Finding] =
      rows.filterNot(_.startsWith("rule\t")).filter(_.nonEmpty).map { row =>
        val cols = row.split("\t", 5)
        Finding(cols(0), cols(1), cols(2), cols(3).toInt, cols.lift(4).getOrElse(""))
      }

  object Ratchet:
    val Header: String =
      "# rule\tmodule\tscope\tcount — guideline findings (GuidelineInventory) that may only go down (G57); " +
        "regenerate with HISTMERSION_UPDATE_ARCHITECTURE=1 sbt 'server/testOnly *GuidelineRatchetSuite'"

    def counts(findings: List[Finding]): Map[Key, Int] = findings.groupMapReduce(_.key)(_ => 1)(_ + _)

    def render(counts: Map[Key, Int]): List[String] =
      Header :: counts.toList
        .sortBy((k, _) => (k.rule, k.module, k.scope))
        .map((k, n) => s"${k.rule}\t${k.module}\t${k.scope}\t$n")

    def parse(lines: List[String]): Map[Key, Int] =
      lines
        .filterNot(l => l.startsWith("#") || l.isBlank)
        .map { l =>
          val cols = l.split('\t')
          Key(cols(0), cols(1), cols(2)) -> cols(3).toInt
        }
        .toMap

    /** The keys whose count is above the listed one (a key not listed counts zero): key, listed, actual. */
    def rises(listed: Map[Key, Int], actual: Map[Key, Int]): List[(Key, Int, Int)] =
      actual.toList
        .collect { case (k, n) if n > listed.getOrElse(k, 0) => (k, listed.getOrElse(k, 0), n) }
        .sortBy(_._1.toString)

    /** The keys whose count is below the listed one (a key with no finding left counts zero): key, listed, actual. */
    def drops(listed: Map[Key, Int], actual: Map[Key, Int]): List[(Key, Int, Int)] =
      listed.toList
        .collect { case (k, n) if actual.getOrElse(k, 0) < n => (k, n, actual.getOrElse(k, 0)) }
        .sortBy(_._1.toString)

    /** The findings of a risen key that a change can have added: those in the changed files, or every one of the key
      * when no changed file has one (the counts do not say which finding is new).
      */
    def newFindings(findings: List[Finding], key: Key, changed: Set[String]): List[Finding] =
      val ofKey = findings.filter(_.key == key)
      val inChanged = ofKey.filter(f => changed(f.path))
      (if inChanged.nonEmpty then inChanged else ofKey).sortBy(f => (f.path, f.line))

  /** The inventory, run once per test run over the SemanticDB of the last compile. */
  object Inventory:
    val Config: String = "tools/refactor/guideline-ratchet.conf"
    val Report: Path = Path.of("target/guidelines/findings.tsv")
    val Log: Path = Path.of("target/guidelines/inventory.log")

    /** The places that build a problem (SentenceSites, run in the same pass), for SentenceLedgerSuite. */
    val SentenceSites: Path = Path.of("target/guidelines/sentence-sites.tsv")

    /** The findings of one run, shared by the suites that read its reports (this one, SentenceLedgerSuite). */
    lazy val findings: List[Finding] = run()

    /** The directories sbt writes the SemanticDB to (`meta`, `test-meta` beside each module's classes). Scalafix is
      * told them, because by default it reads the first `META-INF/semanticdb` on the classpath — a leftover one under
      * `classes` from an older build layout too, which describes an older text of the file.
      */
    def semanticdbTargetRoots(out: Path): List[Path] =
      if !Files.isDirectory(out) then Nil
      else
        val stream = Files.find(out, 4, (p, a) => a.isDirectory && Set("meta", "test-meta")(p.getFileName.toString))
        try stream.iterator.asScala.map(_.toAbsolutePath.normalize).toList.sortBy(_.toString)
        finally stream.close()

    def run(): List[Finding] =
      Files.createDirectories(Log.getParent)
      Files.deleteIfExists(Report)
      Files.deleteIfExists(SentenceSites)
      val targetRoots =
        semanticdbTargetRoots(Path.of("target/out")).flatMap(p => List("--semanticdb-targetroots", p.toString))
      val command = List(
        "scala-cli",
        "run",
        "--server=false",
        "-q",
        "-S",
        "2.13.18",
        "--java-opt",
        "-Xmx6g",
        "--dep",
        "ch.epfl.scala:scalafix-cli_2.13.18:0.14.9",
        "--main-class",
        "scalafix.cli.Cli",
        "--",
        "--config",
        Config,
        "--scala-version",
        "3.10.0",
        "--sourceroot",
        ".",
        "--auto-classpath",
        "--auto-classpath-roots",
        "target/out"
      ) ++ targetRoots ++ List("--files", "modules")
      val process = new ProcessBuilder(command.asJava).redirectErrorStream(true).redirectOutput(Log.toFile).start()
      val finished = process.waitFor(15, TimeUnit.MINUTES)
      if !finished then process.destroyForcibly()
      val tail =
        if Files.isRegularFile(Log) then Files.readAllLines(Log, UTF_8).asScala.takeRight(40).mkString("\n") else ""
      assert(
        finished && process.exitValue() == 0 && Files.isRegularFile(Report),
        s"the guideline inventory did not finish (log $Log; a stale or missing SemanticDB means a module was not compiled: " +
          s"sbt compile Test/compile):\n$tail"
      )
      Finding.parse(Files.readAllLines(Report, UTF_8).asScala.toList)

  /** The files changed since a file was last committed: committed since, staged, unstaged and untracked. */
  object Changes:
    def since(file: Path): Set[String] =
      val base = git("log", "-1", "--format=%H", "--", file.toString).headOption.filter(_.nonEmpty)
      (base.toList.flatMap(b => git("diff", "--name-only", b)) ++ git("diff", "--name-only", "HEAD") ++
        git("ls-files", "--others", "--exclude-standard", "modules")).toSet

    private def git(args: String*): List[String] =
      val process = new ProcessBuilder(("git" :: args.toList).asJava).redirectErrorStream(true).start()
      val out = scala.io.Source.fromInputStream(process.getInputStream, "UTF-8").getLines().toList
      if process.waitFor() == 0 then out else Nil
