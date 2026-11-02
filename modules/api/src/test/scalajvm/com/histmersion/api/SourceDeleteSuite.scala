package com.histmersion.api

import io.circe.parser.decode
import io.circe.syntax.*
import com.histmersion.api.library.{CalibreEntryCheck, DeleteBlocker, DeleteCount, DeletePart, Edge}
import com.histmersion.api.library.Source

/** #969: the pure half of deleting a source — the refusal message, the log line, the rendering and the wire shape. */
class SourceDeleteSuite extends munit.FunSuite:

  private val Ck = "beckman2011_ahhiyawa"

  private val blocked = List(
    DeleteBlocker(DeleteBlocker.Kind.Article, "ahhiyawa", Some("Ahhiyawa")),
    DeleteBlocker(DeleteBlocker.Kind.Article, "troy", None),
    DeleteBlocker(DeleteBlocker.Kind.Job, "j1", Some("ingest"))
  )

  test("the refusal names every blocker and offers no override"):
    val m = Source.Delete.refusal(Ck, blocked)
    assert(m.contains("cited by article ahhiyawa (Ahhiyawa), troy"), m)
    assert(m.contains("still being written by job j1 (ingest)"), m)
    assert(m.contains("no override"), m)

  private val done = Source.Deletion(
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
      Source.Delete.logText(done),
      s"deleted $Ck (The Ahhiyawa Texts): 3 findings, 2 edges in (parked), 1 stored original, Calibre entry 1693 kept"
    )
    assertEquals(Source.Delete.logText(Source.Deletion(Ck, "T", applied = true)), s"deleted $Ck (T)")

  test("markdown: dry run, refused, applied"):
    val dry = Source.Delete.markdown(done.copy(applied = false, logEntry = None))
    assert(dry.contains("Would delete") && dry.contains("--apply") && dry.contains("findings: 3"), dry)
    assert(dry.contains("links entry 1693 (not checked against the library)"), dry)
    val present = Source.Delete.markdown(done.copy(applied = false, logEntry = None), Some(CalibreEntryCheck.Present))
    assert(present.contains("entry 1693 is linked and is NOT touched"), present)
    val refused = Source.Delete.markdown(done.copy(applied = false, blockers = blocked))
    assert(
      refused.contains("Cannot delete") && refused.contains("no override") && !refused.contains("--apply"),
      refused
    )
    val applied = Source.Delete.markdown(done)
    assert(applied.contains("# Deleted") && applied.contains("item #42") && applied.contains("/spool/abc.pdf"), applied)

  test("the Calibre line says what the library holds, not only what the record links (#1776)"):
    assertEquals(
      Source.Delete.calibreLine(Some("1885"), Some(CalibreEntryCheck.Missing)),
      "Calibre: entry 1885 no longer exists in the library — nothing to remove there."
    )
    assert(
      Source.Delete.calibreLine(Some("1885"), Some(CalibreEntryCheck.Present)).contains("is linked and is NOT touched")
    )
    val un = Source.Delete.calibreLine(Some("1885"), Some(CalibreEntryCheck.Unchecked("no Calibre library at /x")))
    assert(un.contains("could not be checked: no Calibre library at /x") && !un.contains("is linked and"), un)
    assertEquals(Source.Delete.calibreLine(None, Some(CalibreEntryCheck.Missing)), "Calibre: no linked entry.")
    val gone = Source.Delete.markdown(done, Some(CalibreEntryCheck.Missing))
    assert(gone.contains("entry 1693 no longer exists in the library") && !gone.contains("NOT touched"), gone)

  test("the wire shape round-trips, enums as their wire names"):
    val d = done.copy(blockers = blocked, edgesIn = List(Edge("cline2014_1177", Ck, "challenges")))
    val json = d.asJson.noSpaces
    assert(json.contains("\"part\":\"edges-in\"") && json.contains("\"kind\":\"article\""), json)
    assertEquals(decode[Source.Deletion](json), Right(d))

  test("the request is strict: an unknown key is refused"):
    assertEquals(decode[Source.DeleteRequest]("""{"apply":true}"""), Right(Source.DeleteRequest(true)))
    assertEquals(decode[Source.DeleteRequest]("{}"), Right(Source.DeleteRequest(false)))
    assert(decode[Source.DeleteRequest]("""{"apply":true,"force":true}""").isLeft)
