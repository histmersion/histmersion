package com.histmersion.api

import io.circe.parser.decode
import io.circe.syntax.*

/** #969: the pure half of deleting a source — which attestations lose their target, the refusal message, the log line,
  * the rendering and the wire shape.
  */
class SourceDeleteSuite extends munit.FunSuite:

  private val Ck = "beckman2011_ahhiyawa"

  test("attestations of its findings, of edges FROM it and of its datings go; of edges TO it they stay"):
    assert(SourceDelete.attestationGone("finding", s"$Ck#3", Ck))
    assert(!SourceDelete.attestationGone("finding", s"${Ck}x#3", Ck))
    assert(SourceDelete.attestationGone("edge", s"$Ck|cline2014_1177|challenges", Ck))
    assert(
      !SourceDelete.attestationGone("edge", s"cline2014_1177|$Ck|challenges", Ck),
      "an incoming edge is parked, not deleted"
    )
    assert(SourceDelete.attestationGone("event_dating", s"fall_of_troy|$Ck", Ck))
    assert(!SourceDelete.attestationGone("event_dating", s"fall_of_troy|${Ck}_v2", Ck))
    assert(!SourceDelete.attestationGone("place", Ck, Ck))

  test("links: any [[ck]], with a label or an anchor, case-insensitively"):
    assert(SourceDelete.links(s"see [[$Ck|Beckman]]", Ck))
    assert(SourceDelete.links(s"ingested as [[$Ck]]", Ck))
    assert(SourceDelete.links(s"[[$Ck#ch-2]]", Ck))
    assert(!SourceDelete.links(s"plain $Ck", Ck))
    assert(!SourceDelete.links(s"[[${Ck}_v2]]", Ck))

  private val blocked = List(
    DeleteBlocker(DeleteBlockerKind.Article, "ahhiyawa", Some("Ahhiyawa")),
    DeleteBlocker(DeleteBlockerKind.Article, "troy", None),
    DeleteBlocker(DeleteBlockerKind.Job, "j1", Some("ingest"))
  )

  test("the refusal names every blocker and offers no override"):
    val m = SourceDelete.refusal(Ck, blocked)
    assert(m.contains("cited by article ahhiyawa (Ahhiyawa), troy"), m)
    assert(m.contains("still being written by job j1 (ingest)"), m)
    assert(m.contains("no override"), m)

  private val done = SourceDeletion(
    Ck,
    "The Ahhiyawa Texts",
    applied = true,
    counts = List(DeleteCount(DeletePart.Findings, 3), DeleteCount(DeletePart.EdgesIn, 2)),
    files = List("/spool/abc.pdf"),
    calibreId = Some("1693"),
    logEntry = Some(42)
  )

  test("the log line says what went, in words"):
    assertEquals(
      SourceDelete.logText(done),
      s"deleted $Ck (The Ahhiyawa Texts): 3 findings, 2 edges in (parked), 1 stored original, Calibre entry 1693 kept"
    )
    assertEquals(SourceDelete.logText(SourceDeletion(Ck, "T", applied = true)), s"deleted $Ck (T)")

  test("markdown: dry run, refused, applied"):
    val dry = SourceDelete.markdown(done.copy(applied = false, logEntry = None))
    assert(dry.contains("Would delete") && dry.contains("--apply") && dry.contains("findings: 3"), dry)
    assert(dry.contains("links entry 1693 (not checked against the library)"), dry)
    val present = SourceDelete.markdown(done.copy(applied = false, logEntry = None), Some(CalibreEntryCheck.Present))
    assert(present.contains("entry 1693 is linked and is NOT touched"), present)
    val refused = SourceDelete.markdown(done.copy(applied = false, blockers = blocked))
    assert(
      refused.contains("Cannot delete") && refused.contains("no override") && !refused.contains("--apply"),
      refused
    )
    val applied = SourceDelete.markdown(done)
    assert(applied.contains("# Deleted") && applied.contains("item #42") && applied.contains("/spool/abc.pdf"), applied)

  test("the Calibre line says what the library holds, not only what the record links (#1776)"):
    assertEquals(
      SourceDelete.calibreLine(Some("1885"), Some(CalibreEntryCheck.Missing)),
      "Calibre: entry 1885 no longer exists in the library — nothing to remove there."
    )
    assert(
      SourceDelete.calibreLine(Some("1885"), Some(CalibreEntryCheck.Present)).contains("is linked and is NOT touched")
    )
    val un = SourceDelete.calibreLine(Some("1885"), Some(CalibreEntryCheck.Unchecked("no Calibre library at /x")))
    assert(un.contains("could not be checked: no Calibre library at /x") && !un.contains("is linked and"), un)
    assertEquals(SourceDelete.calibreLine(None, Some(CalibreEntryCheck.Missing)), "Calibre: no linked entry.")
    val gone = SourceDelete.markdown(done, Some(CalibreEntryCheck.Missing))
    assert(gone.contains("entry 1693 no longer exists in the library") && !gone.contains("NOT touched"), gone)

  test("the wire shape round-trips, enums as their wire names"):
    val d = done.copy(blockers = blocked, edgesIn = List(Edge("cline2014_1177", Ck, "challenges")))
    val json = d.asJson.noSpaces
    assert(json.contains("\"part\":\"edges-in\"") && json.contains("\"kind\":\"article\""), json)
    assertEquals(decode[SourceDeletion](json), Right(d))

  test("the request is strict: an unknown key is refused"):
    assertEquals(decode[SourceDeleteRequest]("""{"apply":true}"""), Right(SourceDeleteRequest(true)))
    assertEquals(decode[SourceDeleteRequest]("{}"), Right(SourceDeleteRequest(false)))
    assert(decode[SourceDeleteRequest]("""{"apply":true,"force":true}""").isLeft)
