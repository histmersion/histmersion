package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.CollectionEntry
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class OutputSuite extends munit.FunSuite:

  private given console: util.console.Console = util.console.Console.standard

  test("render prints the DTO's own codec with --json, its Markdown without"):
    val item = Queue.QueueItem(
      Queue.QueueItem.Id(7),
      Queue.QueueItem.List("tasks"),
      Queue.QueueItem.Text("Read Cline"),
      None,
      Queue.QueueItem.Status("open"),
      None,
      Some(Queue.QueueItem.CreatedAt("2026-10-17T00:00:00Z")),
      None
    )
    assertEquals(Output.render(json = Cli.JsonOutput(false), item)(i => s"#${i.id}"), "#7")
    val js = Output.render(json = Cli.JsonOutput(true), item)(_ => "unused")
    assert(js.startsWith("{\n  \"id\" : 7,"), js)
    assert(js.contains("\"reason\" : null"), "an absent field is printed as null, as the server sends it: " + js)

  test("the one --json flag parses, and defaults to Markdown"):
    assertEquals(
      Parsing.parse("query", "select 1", "--json"),
      Right(Ops.Command.Query("select 1", Cli.JsonOutput(true)))
    )
    assertEquals(Parsing.parse("query", "select 1"), Right(Ops.Command.Query("select 1", Cli.JsonOutput(false))))

  test("calibre search --json keeps the field names it always printed"):
    val b =
      Calibre.Book(
        Calibre.Book.Id(3),
        Calibre.Book.Title("Title"),
        List(AuthorName("A")),
        Some(Calibre.Book.Pubdate("1999-01-01")),
        None,
        List(CollectionEntry.Tags("t")),
        None,
        Map(Calibre.Book.IdentifiersKey("doi") -> Calibre.Book.IdentifiersValue("10.1/x")),
        List(CollectionEntry.Formats("PDF"))
      )
    val js = Output.json(List(Calibre.Json.Book.of(b)))
    assertEquals(
      RawJson.parse(js).toOption.flatMap(_.asArray).flatMap(_.headOption).map(_.asObject.get.keys.toList),
      Some(List("id", "title", "authors", "year", "publisher", "tags", "identifiers", "formats"))
    )
    assert(js.contains("\"year\" : 1999"), js)

  test("calibre update --all --json names each outcome as before"):
    val ch = Calibre.Change(Calibre.Field.values.head, Calibre.Change.From("a"), Calibre.Change.To("b"))
    val r = Calibre.Json.UpdateRow.of(
      UpdateEntry(
        UpdateEntry.CalibreId(3),
        List(UpdateEntry.Citekeys("ck")),
        UpdateOutcome.Failed(List(ch), Problem.Unclassified(Legacy.Text("locked")))
      )
    )
    assertEquals(
      (r.state, r.reasons, r.changes.map(_.from)),
      (
        Calibre.Json.UpdateRow.State("failed"),
        List(Problem.Unclassified(Legacy.Text("locked"))),
        List(Calibre.Change.From("a"))
      )
    )
    assertEquals(
      Calibre.Json.UpdateRow.of(UpdateEntry(UpdateEntry.CalibreId(3), Nil, UpdateOutcome.UpToDate)).state,
      Calibre.Json.UpdateRow.State("up-to-date")
    )

  test("no command prints JSON or declares --json its own way"):
    val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", ".")).resolve("modules/cli/src/main/scala")
    val offenders = Files
      .walk(root)
      .iterator
      .asScala
      .filter(_.toString.endsWith(".scala"))
      .filterNot(_.getFileName.toString == "Output.scala")
      .flatMap { p =>
        Files.readAllLines(p).asScala.zipWithIndex.collect {
          case (l, i) if l.contains(".spaces2") || l.contains("Json.obj(") || l.contains("Opts.flag(\"json\"") =>
            s"${p.getFileName}:${i + 1}: ${l.trim}"
        }
      }
      .toList
    assertEquals(offenders, Nil, "print through Output.emit / Output.json and take Output.jsonFlag")

  test("CLI output boundary preserves bytes, channels, callbacks and evaluation") {
    val out = new java.io.ByteArrayOutputStream
    val err = new java.io.ByteArrayOutputStream
    val stdout = new java.io.PrintStream(out, true, "UTF-8")
    val stderr = new java.io.PrintStream(err, true, "UTF-8")
    var evaluated = 0
    var rendered = 0
    def value(): Any = {
      evaluated += 1
      new Object {
        override def toString: String = { rendered += 1; "雪" }
      }
    }
    Console.withOut(stdout) {
      Console.withErr(stderr) {
        summon[util.console.Console].write(value())
        summon[util.console.Console].writeLine()
        summon[util.console.Console].writeLine(null)
        List("a", "b").foreach(summon[util.console.Console].writeLine)
        Output.emit(json = Cli.JsonOutput(false), "answer")(identity)
        Output.emit(json = Cli.JsonOutput(true), "answer")(_ => fail("JSON must not render Markdown"))
      }
    }
    val nl = System.lineSeparator()
    assertEquals(out.toString("UTF-8"), s"雪${nl}null${nl}a${nl}b${nl}answer$nl\"answer\"$nl")
    assertEquals(err.toString("UTF-8"), "")
    assertEquals(evaluated, 1)
    assertEquals(rendered, 1)
  }

  test("CLI stderr boundary preserves progress, overloads, flush and single evaluation") {
    val bytes = new java.io.ByteArrayOutputStream
    var flushes = 0
    var lines = 0
    val stream = new java.io.PrintStream(bytes, false, "UTF-8") {
      override def flush(): Unit = { flushes += 1; super.flush() }
      override def println(value: String): Unit = { lines += 1; super.println(value) }
    }
    var evaluated = 0
    Console.withErr(stream) {
      summon[util.console.Console].errorWrite { evaluated += 1; "雪" }
      summon[util.console.Console].errorLine("line")
      summon[util.console.Console].errorLine()
      summon[util.console.Console].errorFlush()
      JobFollow.Progress.line("working")
      JobFollow.Progress.clear()
      intercept[IllegalArgumentException] {
        summon[util.console.Console].errorLine(throw new IllegalArgumentException("failed"))
      }
    }
    val nl = System.lineSeparator()
    val blank = " " * JobFollow.Progress.Width
    assertEquals(bytes.toString("UTF-8"), s"雪line$nl$nl\r$blank\rworking\r$blank\r")
    assertEquals(flushes, 1)
    assertEquals(lines, 1)
    assertEquals(evaluated, 1)
  }

  test("injected consoles isolate command output, progress, input and errors") {
    val a = new java.io.ByteArrayOutputStream
    val b = new java.io.ByteArrayOutputStream
    val errors = new java.io.ByteArrayOutputStream
    val first = util.console.Console.streams(
      new java.io.PrintStream(a, true, "UTF-8"),
      new java.io.PrintStream(errors, true, "UTF-8"),
      () => util.console.Console.Line("yes")
    )
    val second = util.console.Console.streams(
      new java.io.PrintStream(b, true, "UTF-8"),
      new java.io.PrintStream(errors, true, "UTF-8"),
      () => util.console.Console.Line("no")
    )
    def answer(text: String)(using util.console.Console): Unit = {
      Cli.out.println(text)
      Cli.out.flush()
    }
    answer("first")(using first)
    answer("second")(using second)
    answer("again")(using first)
    Cli.err(using first).println("problem")
    JobFollow.Progress.line("working")(using first)
    assertEquals(first.readLine(), util.console.Console.Line("yes"))
    assertEquals(second.readLine(), util.console.Console.Line("no"))
    val nl = System.lineSeparator()
    assertEquals(a.toString("UTF-8"), s"first${nl}again$nl")
    assertEquals(b.toString("UTF-8"), s"second$nl")
    assertEquals(
      errors.toString("UTF-8"),
      s"problem$nl\\r${" " * JobFollow.Progress.Width}\\rworking".replace("\\r", "\r")
    )
  }

  test("injected terminal prompts use supplied streams and input") {
    val out = new java.io.ByteArrayOutputStream
    val err = new java.io.ByteArrayOutputStream
    val console = util.console.Console.streams(
      new java.io.PrintStream(out),
      new java.io.PrintStream(err),
      () => util.console.Console.Line("yes")
    )
    assertEquals(
      console.readTerminalLine(util.console.Console.Prompt("Proceed? ")),
      Some(util.console.Console.Line("yes"))
    )
    assertEquals(out.toString("UTF-8"), "")
    assertEquals(err.toString("UTF-8"), "Proceed? ")
  }
