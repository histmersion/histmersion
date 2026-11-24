package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*

import com.histmersion.api.problem.Answer.status
import com.histmersion.util.problem.Problem

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

  test("every case has its row in the register, with what justifies it; the unjustified ones only fall"):
    val expected = Register.of(rows)
    if sys.env.get("HISTMERSION_UPDATE_ARCHITECTURE").contains("1") then
      val _ = Files.write(RegisterFile, Register.render(expected).asJava, UTF_8)
    val listed = Files.readAllLines(RegisterFile, UTF_8).asScala.toList
    assertEquals(listed, Register.render(expected), s"$RegisterFile is not what the code justifies: regenerate it")
    assertEquals(expected.count(_.justified == Justified.Not), Unjustified)

  test("a case is justified by a branch on it, by its own HTTP status, or by the shape of its data"):
    val branches = List(Row(Kind.Branch, "Io.Process", "modules/x.scala"), Row(Kind.Branch, "Io.Ending.TimedOut", "y"))
    assertEquals(Register.branchOn("Io.Process", branches), Some("modules/x.scala"))
    assertEquals(Register.branchOn("Io.Ending", branches), Some("y"))
    assertEquals(Register.branchOn("Io.File", branches), None)
    assertEquals(Register.shapeOf(Problem.Run.Draining(None)), List("optional"))
    assertEquals(Register.shapeOf(Problem.Refused.Misdirected(Problem.Raw("x"))), Nil)

  test("a row reads back from its cells"):
    assertEquals(
      Row.parse(List("kind\tname\tmodule\tpath\tline", "build\tRun.Interrupted\tserver\tmodules/x.scala\t3")),
      List(Row(Kind.Build, "Run.Interrupted", "modules/x.scala"))
    )

object ProblemCasesSuite:

  val Cases: Int = 131
  val Values: Int = 627
  val Unjustified: Int = 33

  val RegisterFile: Path = Path.of("architecture/problem-cases.tsv")

  val BuiltOutside: Map[String, String] = Map(
    "Plugin.Retrieval.NoEntry" -> "the vault plugin's template (cli/PluginTemplateKinds.scala) writes it into a new plugin"
  )

  enum Kind(val word: String) derives CanEqual:
    case Case extends Kind("case")
    case Value extends Kind("value")
    case Build extends Kind("build")
    case Branch extends Kind("branch")

  final case class Row(kind: Kind, name: String, path: String)

  object Row:
    def parse(lines: List[String]): List[Row] =
      lines.drop(1).filter(_.nonEmpty).flatMap { line =>
        val cells = line.split("\t", -1)
        Kind.values.find(_.word == cells(0)).map(Row(_, cells(1), cells(3)))
      }

  enum Justified(val word: String) derives CanEqual:
    case Branch extends Justified("branch")
    case Status extends Justified("status")
    case Shape extends Justified("shape")
    case Not extends Justified("unjustified")

  final case class Entry(name: String, justified: Justified, evidence: String)

  object Register:
    val Header: String =
      "# case\tjustified\tevidence — every Problem case and what justifies it (owner decision 8 of 2026-11-19, row 18 " +
        "decision 1): a branch on it outside the edges (the first file), an HTTP status its group does not answer, or " +
        "data with a shape (a list, an optional value, a nested choice, another problem); regenerate with " +
        "HISTMERSION_UPDATE_ARCHITECTURE=1 sbt 'server/testOnly *ProblemCasesSuite'"

    def branchOn(name: String, rows: List[Row]): Option[String] =
      rows
        .filter(r => r.kind == Kind.Branch && (r.name == name || r.name.startsWith(name + ".")))
        .map(_.path)
        .sorted
        .headOption

    def shapeOf(problem: Product): List[String] =
      problem.productIterator.toList
        .map { field =>
          if field.isInstanceOf[Option[?]] then "optional"
          else if field.isInstanceOf[Iterable[?]] then "list"
          else if field.isInstanceOf[Problem] then "problem"
          else if field.isInstanceOf[Product] then "choice"
          else ""
        }
        .filter(_.nonEmpty)
        .distinct

    def of(rows: List[Row]): List[Entry] =
      val byGroup = Problem.examples.groupBy(_.group)
      val usual = byGroup.view.mapValues(ps => ps.groupBy(_.status.code).maxBy((code, of) => (of.size, -code))._1).toMap
      Problem.examples.toList
        .map { p =>
          val name = SentenceLedgerSuite.Examples.caseOf(p)
          val code = p.status.code
          branchOn(name, rows)
            .map(Entry(name, Justified.Branch, _))
            .orElse(Option.when(code != usual(p.group))(Entry(name, Justified.Status, s"$code, not ${usual(p.group)}")))
            .orElse(Option.when(shapeOf(p).nonEmpty)(Entry(name, Justified.Shape, shapeOf(p).mkString(", "))))
            .getOrElse(Entry(name, Justified.Not, "-"))
        }
        .sortBy(_.name)

    def render(entries: List[Entry]): List[String] =
      Header :: entries.map(e => List(e.name, e.justified.word, e.evidence).mkString("\t"))
