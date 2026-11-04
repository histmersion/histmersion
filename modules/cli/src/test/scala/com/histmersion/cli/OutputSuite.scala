package com.histmersion.cli

import com.monovore.decline.Command
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.automation.Queue

class OutputSuite extends munit.FunSuite:

  test("render prints the DTO's own codec with --json, its Markdown without"):
    val item = Queue.QueueItem(7, "tasks", "Read Cline", None, "open", None, Some("2026-10-17T00:00:00Z"), None)
    assertEquals(Output.render(json = false, item)(i => s"#${i.id}"), "#7")
    val js = Output.render(json = true, item)(_ => "unused")
    assert(js.startsWith("{\n  \"id\" : 7,"), js)
    assert(js.contains("\"reason\" : null"), "an absent field is printed as null, as the server sends it: " + js)

  test("the one --json flag parses, and defaults to Markdown"):
    val cmd = Command("t", "t")(Output.jsonFlag)
    assertEquals(cmd.parse(List("--json")), Right(true))
    assertEquals(cmd.parse(Nil), Right(false))

  test("calibre search --json keeps the field names it always printed"):
    val b =
      Calibre.Book(
        3,
        "Title",
        List("A"),
        Some("1999-01-01"),
        None,
        List("t"),
        None,
        Map("doi" -> "10.1/x"),
        List("PDF")
      )
    val js = Output.json(List(Calibre.Json.Book.of(b)))
    assertEquals(
      io.circe.parser.parse(js).toOption.flatMap(_.asArray).flatMap(_.headOption).map(_.asObject.get.keys.toList),
      Some(List("id", "title", "authors", "year", "publisher", "tags", "identifiers", "formats"))
    )
    assert(js.contains("\"year\" : 1999"), js)

  test("calibre update --all --json names each outcome as before"):
    val ch = Calibre.Change(Calibre.Field.values.head, "a", "b")
    val r = Calibre.Json.UpdateRow.of(UpdateEntry(3, List("ck"), UpdateOutcome.Failed(List(ch), "locked")))
    assertEquals((r.state, r.reason, r.changes.map(_.from)), ("failed", Some("locked"), List("a")))
    assertEquals(Calibre.Json.UpdateRow.of(UpdateEntry(3, Nil, UpdateOutcome.UpToDate)).state, "up-to-date")

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
        Cli.write(value())
        Cli.writeLine()
        Cli.writeLine(null)
        List("a", "b").foreach(Cli.writeLine)
        Output.emit(json = false, "answer")(identity)
        Output.emit(json = true, "answer")(_ => fail("JSON must not render Markdown"))
      }
    }
    val nl = System.lineSeparator()
    assertEquals(out.toString("UTF-8"), s"雪${nl}null${nl}a${nl}b${nl}answer${nl}\"answer\"${nl}")
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
      Cli.errorWrite({ evaluated += 1; "雪" })
      Cli.errorLine("line")
      Cli.errorLine()
      Cli.errorFlush()
      JobFollow.Progress.line("working")
      JobFollow.Progress.clear()
      intercept[IllegalArgumentException] { Cli.errorLine(throw new IllegalArgumentException("failed")) }
    }
    val nl = System.lineSeparator()
    val blank = " " * JobFollow.Progress.Width
    assertEquals(bytes.toString("UTF-8"), s"雪line${nl}${nl}\r${blank}\rworking\r${blank}\r")
    assertEquals(flushes, 1)
    assertEquals(lines, 1)
    assertEquals(evaluated, 1)
  }
