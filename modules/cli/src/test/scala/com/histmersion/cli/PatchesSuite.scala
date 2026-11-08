package com.histmersion
package cli

import com.histmersion.api.ListPatch
import com.histmersion.config.*
import com.histmersion.api.library.{EdgesPatch, FindingsPatch, MetadataPatch}
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Title
import com.histmersion.api.Write
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey

/** `histmersion patch` flags must produce a patch whose ABSENT parts stay absent — a flag nobody passed must not turn
  * into an empty list that wipes the stored value.
  */
class PatchesSuite extends munit.FunSuite:

  private def build(
      json: Option[String] = None,
      tagsAdd: Option[String] = None,
      tagsRemove: Option[String] = None,
      tagsReplace: Option[String] = None,
      topics: Option[String] = None,
      regions: Option[String] = None,
      summary: Option[Source.Summary] = None,
      status: Option[Source.Status] = None,
      title: Option[Title] = None,
      period: Option[String] = None,
      addFindings: List[String] = Nil,
      removeFindings: List[String] = Nil,
      ifUpdatedAt: Option[Source.UpdatedAt] = None,
      bodyFile: Option[Patches.BodyFile] = None,
      addEdges: List[String] = Nil,
      removeEdges: List[String] = Nil
  ) = Patches.build(
    json,
    tagsAdd,
    tagsRemove,
    tagsReplace,
    topics,
    regions,
    summary,
    status,
    title,
    None,
    period,
    bodyFile,
    addFindings,
    removeFindings,
    ifUpdatedAt,
    addEdges,
    removeEdges
  )

  test("no flags at all → every part absent (the server changes nothing)"):
    assertEquals(build(), Right(Source.Patch()))

  test("--tags-add only sets the tags part, and only its add list"):
    val p = build(tagsAdd = Some("sea-peoples, collapse")).toOption.get
    assertEquals(p.tags, Some(ListPatch(add = List(Write.ListItem("sea-peoples"), Write.ListItem("collapse")))))
    assertEquals(p.topics, None)
    assertEquals(p.findings, None)
    assertEquals(p.summary, None)

  test("--topics / --regions replace the whole list"):
    val p = build(topics = Some("a,b"), regions = Some("Anatolia")).toOption.get
    assertEquals(p.topics, Some(List(Source.Topics("a"), Source.Topics("b"))))
    assertEquals(p.regions, Some(List(Source.Regions("Anatolia"))))

  test("--status and --title fold into one metadata part"):
    val p = build(status = Some(Source.Status.Superseded), title = Some(Title("New"))).toOption.get
    assertEquals(p.metadata, Some(MetadataPatch(title = Some(Title("New")), status = Some(Source.Status.Superseded))))

  test("--add-finding parses JSON; --remove-finding collects ids"):
    val p = build(
      addFindings = List("""{"claim":"c","lon":1.0,"lat":2.0}"""),
      removeFindings = List("x#0", "x#1")
    ).toOption.get
    assertEquals(
      p.findings.get.add,
      List(
        Finding.Input(
          Finding.Claim("c"),
          lon = Some(Longitude(1.0)),
          lat = Some(Latitude(2.0))
        )
      )
    )
    assertEquals(p.findings.get.remove, List(FindingsPatch.Remove("x#0"), FindingsPatch.Remove("x#1")))

  test("--add-finding with bad JSON is a usage error, not a crash"):
    val e = build(addFindings = List("{nope")).swap.toOption.get
    assertEquals(e.exitCode, ExitCode.Usage)
    assert(e.message.value.contains("--add-finding"), e.message)

  test("--period takes signed years, BCE negative; a malformed one is a usage error"):
    assertEquals(build(period = Some("-3300,-2600")).toOption.get.period, Some(Period.Input(Year(-3300), Year(-2600))))
    assertEquals(build(period = Some("3300 BCE")).swap.toOption.get.exitCode, ExitCode.Usage)

  test("--json carries the parts that have no flag, e.g. edges"):
    val p = build(json = Some("""{"edges":{"add":[{"src":"a","dst":"b","kind":"refutes"}]}}""")).toOption.get
    assertEquals(
      p.edges,
      Some(EdgesPatch(add = List(Edge.Input(Citekey("b"), Edge.Kind.Refutes, Some(Citekey("a"))))))
    )
    assertEquals(p.tags, None)

  // #603: typed edges could only be added through --json
  test("--add-edge / --remove-edge take <kind>:<citekey>, repeatable, and join --json edges"):
    val p = build(
      json = Some("""{"edges":{"add":[{"dst":"a","kind":"refutes"}]}}"""),
      addEdges = List("challenges:lord1968_oralpoet", "challenges: notopoulos1960_achaean"),
      removeEdges = List("superseded_by:old2001_x")
    ).toOption.get
    assertEquals(
      p.edges,
      Some(
        EdgesPatch(
          add = List(
            Edge.Input(Citekey("a"), Edge.Kind.Refutes),
            Edge.Input(Citekey("lord1968_oralpoet"), Edge.Kind.Challenges),
            Edge.Input(Citekey("notopoulos1960_achaean"), Edge.Kind.Challenges)
          ),
          remove = List(Edge.Input(Citekey("old2001_x"), Edge.Kind.SupersededBy))
        )
      )
    )

  test("--add-edge with an unknown kind or no target is a usage error"):
    val bad = build(addEdges = List("disagrees:x")).swap.toOption.get
    assertEquals(bad.exitCode, ExitCode.Usage)
    assert(bad.message.value.contains("unknown edge kind 'disagrees'"), bad.message)
    assert(build(addEdges = List("refutes")).isLeft)
    assert(build(removeEdges = List("refutes:")).isLeft)

  test("a flag overrides the same part of --json, other --json parts survive"):
    val p = build(
      json = Some("""{"summary":"from json","topics":["t"],"tags":{"add":["from-json"]}}"""),
      tagsAdd = Some("from-flag"),
      summary = Some(Source.Summary("from flag"))
    ).toOption.get
    assertEquals(p.summary, Some(Source.Summary("from flag")))
    assertEquals(p.tags, Some(ListPatch(add = List(Write.ListItem("from-flag")))))
    assertEquals(p.topics, Some(List(Source.Topics("t"))))

  test("--if-updated-at is carried through as the precondition"):
    assertEquals(
      build(ifUpdatedAt = Some(Source.UpdatedAt("2026-08-25T10:00:00.000000Z"))).toOption.get.ifUpdatedAt,
      Some(Source.UpdatedAt("2026-08-25T10:00:00.000000Z"))
    )

  test("--tags-replace with --tags-remove keeps both parts"):
    val p = build(tagsReplace = Some("a,b"), tagsRemove = Some("b")).toOption.get
    assertEquals(
      p.tags,
      Some(
        ListPatch(remove = List(Write.ListItem("b")), replace = Some(List(Write.ListItem("a"), Write.ListItem("b"))))
      )
    )

  // a book's OCR (> 1 MB) could not be moved between records: --json on the command line was the only way
  test("--body-file replaces the body with the file's text; a missing file is an error"):
    val f = java.nio.file.Files.createTempFile("body", ".md")
    try
      java.nio.file.Files.writeString(f, "# Chapter\n\ntext")
      assertEquals(
        build(bodyFile = Some(Patches.BodyFile(f.toString))).map(_.body),
        Right(Some(Source.OcrMarkdown("# Chapter\n\ntext")))
      )
      assert(build(bodyFile = Some(Patches.BodyFile(f.toString + ".nope"))).isLeft)
      assertEquals(build().map(_.body), Right(None), "absent flag → body untouched")
    finally java.nio.file.Files.deleteIfExists(f)
