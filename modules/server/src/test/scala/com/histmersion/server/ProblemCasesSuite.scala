package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*

class ProblemCasesSuite extends munit.FunSuite:
  import ProblemCasesSuite.*

  override val munitTimeout = Duration(20, "min")

  private lazy val rows: List[Row] =
    val _ = GuidelineRatchetSuite.Inventory.findings
    Row.parse(Files.readAllLines(GuidelineRatchetSuite.Inventory.ProblemSymbols, UTF_8).asScala.toList)

  test("ProblemCaseBuilt: every case and every value of a problem's vocabulary is built in main code"):
    val built = rows.filter(_.kind == Kind.Build).map(_.name).toSet
    val unbuilt = rows
      .filter(r => (r.kind == Kind.Case || r.kind == Kind.Value) && !built(r.name) && !BuiltOutside.contains(r.name))
      .map(r => s"${r.kind.word} ${r.name}")
    assertEquals(unbuilt, Nil)

  test("a value built outside the scanned code is still defined and still unbuilt there"):
    val defined = rows.filter(_.kind == Kind.Value).map(_.name).toSet
    val built = rows.filter(_.kind == Kind.Build).map(_.name).toSet
    BuiltOutside.keys.foreach(name => assert(defined(name) && !built(name), name))

  test("the cases and the values of the problem vocabularies only fall"):
    assertEquals(rows.count(_.kind == Kind.Case), Cases)
    assertEquals(rows.count(_.kind == Kind.Value), Values)

  test("a row reads back from its cells"):
    assertEquals(
      Row.parse(List("kind\tname\tmodule\tpath\tline", "build\tRun.Interrupted\tserver\tmodules/x.scala\t3")),
      List(Row(Kind.Build, "Run.Interrupted"))
    )

object ProblemCasesSuite:

  val Cases: Int = 147
  val Values: Int = 608

  val BuiltOutside: Map[String, String] = Map(
    "Plugin.Retrieval.NoEntry" -> "the vault plugin's template (cli/PluginTemplateKinds.scala) writes it into a new plugin"
  )

  enum Kind(val word: String) derives CanEqual:
    case Case extends Kind("case")
    case Value extends Kind("value")
    case Build extends Kind("build")
    case Branch extends Kind("branch")

  final case class Row(kind: Kind, name: String)

  object Row:
    def parse(lines: List[String]): List[Row] =
      lines.drop(1).filter(_.nonEmpty).flatMap { line =>
        val cells = line.split("\t", -1)
        Kind.values.find(_.word == cells(0)).map(Row(_, cells(1)))
      }
