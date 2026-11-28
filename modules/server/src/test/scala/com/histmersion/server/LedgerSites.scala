package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

object LedgerSites:
  val Log: Path = Path.of("target/guidelines/ledger-sites.log")
  val SentenceSites: Path = Path.of("target/guidelines/sentence-sites.tsv")
  val ProblemSymbols: Path = Path.of("target/guidelines/problem-symbols.tsv")
  val KeyFile: Path = Path.of("target/guidelines/ledger-sites.key")
  val KeyResource: String = "ledger-sites-key.txt"

  lazy val expectedKey: String =
    val in = Option(getClass.getClassLoader.getResourceAsStream(KeyResource))
      .getOrElse(sys.error(s"$KeyResource is not on the test class path: the build writes it (server Test resources)"))
    try new String(in.readAllBytes(), UTF_8).trim
    finally in.close()

  def current(written: Option[String], expected: String): Boolean = written.exists(_.trim == expected)

  def ensure(): Unit =
    val written = if Files.isRegularFile(KeyFile) then Some(Files.readString(KeyFile, UTF_8)) else None
    val logged = if Files.isRegularFile(Log) then Files.readAllLines(Log, UTF_8).asScala.toList else Nil
    assert(
      current(written, expectedKey) && Files.isRegularFile(SentenceSites) && Files.isRegularFile(ProblemSymbols),
      s"the ledger sites in target/guidelines are missing or were written for other sources: the build writes them " +
        s"once before the server's tests, never a test JVM (sbt server/ledgerSites, docs/adr/0033); $Log, the last " +
        s"40 lines:\n${logged.takeRight(40).mkString("\n")}"
    )
