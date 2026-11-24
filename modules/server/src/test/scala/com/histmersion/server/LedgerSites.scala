package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

object LedgerSites:
  val Config: String = "tools/refactor/ledger-sites.conf"
  val Log: Path = Path.of("target/guidelines/ledger-sites.log")
  val SentenceSites: Path = Path.of("target/guidelines/sentence-sites.tsv")
  val ProblemSymbols: Path = Path.of("target/guidelines/problem-symbols.tsv")

  private lazy val written: Unit = run()

  def ensure(): Unit = written

  def semanticdbTargetRoots(out: Path, scalaVersion: String): List[Path] =
    if !Files.isDirectory(out) then Nil
    else
      val stream = Files.find(
        out,
        4,
        (p, a) =>
          a.isDirectory && Set("meta", "test-meta")(p.getFileName.toString) &&
            p.getParent.getParent.getFileName.toString == s"scala-$scalaVersion"
      )
      try stream.iterator.asScala.map(_.toAbsolutePath.normalize).toList.sortBy(_.toString)
      finally stream.close()

  def run(): Unit =
    Files.createDirectories(Log.getParent)
    Files.deleteIfExists(SentenceSites)
    Files.deleteIfExists(ProblemSymbols)
    val versions = BuildVersions.build
    val targetRoots = semanticdbTargetRoots(Path.of("target/out"), versions.scala)
      .flatMap(p => List("--semanticdb-targetroots", p.toString))
    val command = List(
      "scala-cli",
      "run",
      "--server=false",
      "-q",
      "-S",
      versions.scalafixScala,
      "--java-opt",
      "-Xmx6g",
      "--dep",
      s"ch.epfl.scala:scalafix-cli_${versions.scalafixScala}:${versions.scalafix}",
      "--main-class",
      "scalafix.cli.Cli",
      "--",
      "--config",
      Config,
      "--scala-version",
      versions.scala,
      "--sourceroot",
      ".",
      "--auto-classpath",
      "--auto-classpath-roots",
      "target/out"
    ) ++ targetRoots ++ List("--files", "modules")
    val builder = new ProcessBuilder(command.asJava)
      .redirectErrorStream(true)
      .redirectOutput(Log.toFile)
      .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
    builder.environment().remove("JAVA_OPTS")
    builder.environment().remove("JDK_JAVA_OPTIONS")
    val process = builder.start()
    val finished = process.waitFor(15, TimeUnit.MINUTES)
    if !finished then
      process.destroyForcibly()
      val _ = process.waitFor(10, TimeUnit.SECONDS)
    val logged = if Files.isRegularFile(Log) then Files.readAllLines(Log, UTF_8).asScala.toList else Nil
    assert(
      finished && process.exitValue() == 0 && Files.isRegularFile(SentenceSites) && Files.isRegularFile(ProblemSymbols),
      s"the ledger sites were not written (a stale or missing SemanticDB means a module was not compiled: sbt compile " +
        s"Test/compile); $Log, the last 40 lines:\n${logged.takeRight(40).mkString("\n")}\ncommand: ${command.mkString(" ")}"
    )
