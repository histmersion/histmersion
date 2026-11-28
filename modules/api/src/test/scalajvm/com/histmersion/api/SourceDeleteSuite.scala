package com.histmersion
package api
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.{CalibreEntryCheck, Citekey, DeleteBlocker, DeleteCount, DeletePart, Edge}
import com.histmersion.api.library.Source
import com.histmersion.lint.{tolerated, LintRule}

/** #969: the pure half of deleting a source — the refusal message, the log line, the rendering and the wire shape. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SourceDeleteSuite extends munit.FunSuite:

  private val Ck = Citekey("beckman2011_ahhiyawa")

  private val blocked = List(
    DeleteBlocker(DeleteBlocker.Kind.Article, DeleteBlocker.Id("ahhiyawa"), Some(DeleteBlocker.Title("Ahhiyawa"))),
    DeleteBlocker(DeleteBlocker.Kind.Article, DeleteBlocker.Id("troy"), None),
    DeleteBlocker(DeleteBlocker.Kind.Job, DeleteBlocker.Id("j1"), Some(DeleteBlocker.Title("ingest")))
  )

  test("the refusal counts the blockers of each kind"):
    val ck = Problem.TypeName.of(Citekey)
    val kind = Problem.TypeName.named[DeleteBlocker.Kind]
    def referred(k: DeleteBlocker.Kind, n: Long) =
      Problem.Conflict.Referenced(
        ck,
        Problem.Raw(s"$Ck"),
        Problem.TypeName(s"$kind.${k.productPrefix}"),
        Problem.Count(n)
      )
    assertEquals(
      Source.Delete.refusal(Ck, blocked).toVector,
      Vector(referred(DeleteBlocker.Kind.Article, 2L), referred(DeleteBlocker.Kind.Job, 1L))
    )

  private val done = Source.Deletion(
    Ck,
    Source.Deletion.Title("The Ahhiyawa Texts"),
    applied = Source.Deletion.Applied.Applied,
    counts =
      List(DeleteCount(DeletePart.Findings, DeleteCount.Rows(3)), DeleteCount(DeletePart.EdgesIn, DeleteCount.Rows(2))),
    files = List(Source.Deletion.Files("/spool/abc.pdf")),
    calibreId = Some(Source.CalibreId("1693")),
    logEntry = Some(Source.Deletion.LogEntry(42))
  )

  test("the log line says what went, in words"):
    assertEquals(
      Source.Delete.logText(done),
      s"deleted $Ck (The Ahhiyawa Texts): 3 findings, 2 edges in (parked), 1 stored original, Calibre entry 1693 kept"
    )
    assertEquals(
      Source.Delete.logText(Source.Deletion(Ck, Source.Deletion.Title("T"), applied = Source.Deletion.Applied.Applied)),
      s"deleted $Ck (T)"
    )

  test("markdown: dry run, refused, applied"):
    val dry = Source.Delete
      .markdown(done.modify(_.applied).setTo(Source.Deletion.Applied.Previewed).modify(_.logEntry).setTo(None))
      .value
    assert(dry.contains("Would delete") && dry.contains("--apply") && dry.contains("findings: 3"), dry)
    assert(dry.contains("links entry 1693 (not checked against the library)"), dry)
    val present = Source.Delete
      .markdown(
        done.modify(_.applied).setTo(Source.Deletion.Applied.Previewed).modify(_.logEntry).setTo(None),
        Some(CalibreEntryCheck.Present)
      )
      .value
    assert(present.contains("entry 1693 is linked and is NOT touched"), present)
    val refused = Source.Delete
      .markdown(done.modify(_.applied).setTo(Source.Deletion.Applied.Previewed).modify(_.blockers).setTo(blocked))
      .value
    assert(
      refused.contains("Cannot delete") && refused.contains("no override") && !refused.contains("--apply"),
      refused
    )
    val applied = Source.Delete.markdown(done).value
    assert(applied.contains("# Deleted") && applied.contains("item #42") && applied.contains("/spool/abc.pdf"), applied)

  test("the Calibre line says what the library holds, not only what the record links (#1776)"):
    assertEquals(
      Source.Delete.calibreLine(Some(Source.CalibreId("1885")), Some(CalibreEntryCheck.Missing)),
      "Calibre: entry 1885 no longer exists in the library — nothing to remove there."
    )
    assert(
      Source.Delete
        .calibreLine(Some(Source.CalibreId("1885")), Some(CalibreEntryCheck.Present))
        .value
        .contains("is linked and is NOT touched")
    )
    val noLibrary = Problem.Io.Wrong(Problem.Io.Path("/x"), Problem.Io.Kind.Directory)
    val un = Source.Delete
      .calibreLine(Some(Source.CalibreId("1885")), Some(CalibreEntryCheck.Unchecked(noLibrary)))
      .value
    assert(un.contains(s"could not be checked: ${noLibrary.sentence}") && !un.contains("is linked and"), un)
    assertEquals(Source.Delete.calibreLine(None, Some(CalibreEntryCheck.Missing)), "Calibre: no linked entry.")
    val gone = Source.Delete.markdown(done, Some(CalibreEntryCheck.Missing)).value
    assert(gone.contains("entry 1693 no longer exists in the library") && !gone.contains("NOT touched"), gone)

  test("the wire shape round-trips, enums as their wire names"):
    val d = done
      .modify(_.blockers)
      .setTo(blocked)
      .modify(_.edgesIn)
      .setTo(List(Edge(Citekey("cline2014_1177"), Ck, Edge.Kind.Challenges)))
    val json = d.asJson.noSpaces
    assert(json.contains("\"part\":\"edges-in\"") && json.contains("\"kind\":\"article\""), json)
    assertEquals(RawJson.decode[Source.Deletion](json), Right(d))

  test("the request is strict: an unknown key is refused"):
    assertEquals(
      RawJson.decode[Source.DeleteRequest]("""{"apply":true}"""),
      Right(Source.DeleteRequest(Source.DeleteRequest.Apply.Apply))
    )
    assertEquals(
      RawJson.decode[Source.DeleteRequest]("{}"),
      Right(Source.DeleteRequest(Source.DeleteRequest.Apply.DryRun))
    )
    assert(RawJson.decode[Source.DeleteRequest]("""{"apply":true,"force":true}""").isLeft)
