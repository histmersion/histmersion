package com.histmersion.cli

import com.monovore.decline.Command
import com.histmersion.api.*
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

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
      CalibreBook(3, "Title", List("A"), Some("1999-01-01"), None, List("t"), None, Map("doi" -> "10.1/x"), List("PDF"))
    val js = Output.json(List(CalibreJson.Book.of(b)))
    assertEquals(
      io.circe.parser.parse(js).toOption.flatMap(_.asArray).flatMap(_.headOption).map(_.asObject.get.keys.toList),
      Some(List("id", "title", "authors", "year", "publisher", "tags", "identifiers", "formats"))
    )
    assert(js.contains("\"year\" : 1999"), js)

  test("calibre update --all --json names each outcome as before"):
    val ch = CalibreChange(CalibreField.values.head, "a", "b")
    val r = CalibreJson.UpdateRow.of(UpdateEntry(3, List("ck"), UpdateOutcome.Failed(List(ch), "locked")))
    assertEquals((r.state, r.reason, r.changes.map(_.from)), ("failed", Some("locked"), List("a")))
    assertEquals(CalibreJson.UpdateRow.of(UpdateEntry(3, Nil, UpdateOutcome.UpToDate)).state, "up-to-date")

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
