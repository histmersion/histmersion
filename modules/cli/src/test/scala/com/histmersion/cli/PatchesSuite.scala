package com.histmersion.cli

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.values.{EdgeKind, SourceStatus}

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
      summary: Option[String] = None,
      status: Option[SourceStatus] = None,
      title: Option[String] = None,
      period: Option[String] = None,
      addFindings: List[String] = Nil,
      removeFindings: List[String] = Nil,
      ifUpdatedAt: Option[String] = None,
      bodyFile: Option[String] = None,
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
    assertEquals(build(), Right(SourcePatch()))

  test("--tags-add only sets the tags part, and only its add list"):
    val p = build(tagsAdd = Some("sea-peoples, collapse")).toOption.get
    assertEquals(p.tags, Some(ListPatch(add = List("sea-peoples", "collapse"))))
    assertEquals(p.topics, None)
    assertEquals(p.findings, None)
    assertEquals(p.summary, None)

  test("--topics / --regions replace the whole list"):
    val p = build(topics = Some("a,b"), regions = Some("Anatolia")).toOption.get
    assertEquals(p.topics, Some(List("a", "b")))
    assertEquals(p.regions, Some(List("Anatolia")))

  test("--status and --title fold into one metadata part"):
    val p = build(status = Some(SourceStatus.Superseded), title = Some("New")).toOption.get
    assertEquals(p.metadata, Some(MetadataPatch(title = Some("New"), status = Some(SourceStatus.Superseded))))

  test("--add-finding parses JSON; --remove-finding collects ids"):
    val p = build(
      addFindings = List("""{"claim":"c","lon":1.0,"lat":2.0}"""),
      removeFindings = List("x#0", "x#1")
    ).toOption.get
    assertEquals(p.findings.get.add, List(FindingInput("c", lon = Some(1.0), lat = Some(2.0))))
    assertEquals(p.findings.get.remove, List("x#0", "x#1"))

  test("--add-finding with bad JSON is a usage error, not a crash"):
    val e = build(addFindings = List("{nope")).swap.toOption.get
    assertEquals(e.exitCode, 1)
    assert(e.message.contains("--add-finding"), e.message)

  test("--period takes signed years, BCE negative; a malformed one is a usage error"):
    assertEquals(build(period = Some("-3300,-2600")).toOption.get.period, Some(PeriodInput(-3300, -2600)))
    assertEquals(build(period = Some("3300 BCE")).swap.toOption.get.exitCode, 1)

  test("--json carries the parts that have no flag, e.g. edges"):
    val p = build(json = Some("""{"edges":{"add":[{"src":"a","dst":"b","kind":"refutes"}]}}""")).toOption.get
    assertEquals(p.edges, Some(EdgesPatch(add = List(EdgeInput("b", EdgeKind.Refutes, Some("a"))))))
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
            EdgeInput("a", EdgeKind.Refutes),
            EdgeInput("lord1968_oralpoet", EdgeKind.Challenges),
            EdgeInput("notopoulos1960_achaean", EdgeKind.Challenges)
          ),
          remove = List(EdgeInput("old2001_x", EdgeKind.SupersededBy))
        )
      )
    )

  test("--add-edge with an unknown kind or no target is a usage error"):
    val bad = build(addEdges = List("disagrees:x")).swap.toOption.get
    assertEquals(bad.exitCode, 1)
    assert(bad.message.contains("unknown edge kind 'disagrees'"), bad.message)
    assert(build(addEdges = List("refutes")).isLeft)
    assert(build(removeEdges = List("refutes:")).isLeft)

  test("a flag overrides the same part of --json, other --json parts survive"):
    val p = build(
      json = Some("""{"summary":"from json","topics":["t"],"tags":{"add":["from-json"]}}"""),
      tagsAdd = Some("from-flag"),
      summary = Some("from flag")
    ).toOption.get
    assertEquals(p.summary, Some("from flag"))
    assertEquals(p.tags, Some(ListPatch(add = List("from-flag"))))
    assertEquals(p.topics, Some(List("t")))

  test("--if-updated-at is carried through as the precondition"):
    assertEquals(
      build(ifUpdatedAt = Some("2026-08-25T10:00:00.000000Z")).toOption.get.ifUpdatedAt,
      Some("2026-08-25T10:00:00.000000Z")
    )

  test("--tags-replace with --tags-remove keeps both parts"):
    val p = build(tagsReplace = Some("a,b"), tagsRemove = Some("b")).toOption.get
    assertEquals(p.tags, Some(ListPatch(remove = List("b"), replace = Some(List("a", "b")))))

  // a book's OCR (> 1 MB) could not be moved between records: --json on the command line was the only way
  test("--body-file replaces the body with the file's text; a missing file is an error"):
    val f = java.nio.file.Files.createTempFile("body", ".md")
    try
      java.nio.file.Files.writeString(f, "# Chapter\n\ntext")
      assertEquals(build(bodyFile = Some(f.toString)).map(_.body), Right(Some("# Chapter\n\ntext")))
      assert(build(bodyFile = Some(f.toString + ".nope")).isLeft)
      assertEquals(build().map(_.body), Right(None), "absent flag → body untouched")
    finally java.nio.file.Files.deleteIfExists(f)
