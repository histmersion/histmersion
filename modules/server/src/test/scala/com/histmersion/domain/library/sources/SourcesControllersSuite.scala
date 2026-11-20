package com.histmersion
package domain.library.sources
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{Api, Write}
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.{Citekey, LibraryState, LibraryStoreSuite}
import com.histmersion.domain.media.{MediaSources, MediaState, MediaStoreSuite}
import com.histmersion.domain.media.assets.{AssetsControllers, AssetService}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatRule, FormatSeverity}
import com.histmersion.api.library.NoteImport
import com.histmersion.api.library.Rename
import com.histmersion.api.library.Source
import com.histmersion.util.time.SystemTimer
import com.histmersion.api.library.Title
import com.histmersion.util.database.TestPermits.given

/** A source's controllers over the in-memory services: a citekey that is not one, a strict write refused by the
  * formatting check, an old citekey followed, a section that is not one, passages of a source not there, a delete's dry
  * run naming the stored originals and its apply removing them, and every write refused while the maintenance lock is
  * held.
  */
class SourcesControllersSuite extends munit.FunSuite:

  private val formatting = new SourceFormatting:
    def issues(
        summary: Option[domain.library.Source.Summary],
        notes: Option[SourceNote.Text]
    ): List[FormatIssue] =
      (summary.map(_.value).toList ++ notes.map(_.value).toList)
        .filter(_.contains("<div>"))
        .map(_ =>
          FormatIssue(
            FormatRule.HtmlTag,
            FormatSeverity.Error,
            FormatField.Notes,
            FormatIssue.Line(1),
            FormatIssue.Column(1),
            FormatIssue.Excerpt("<div>"),
            FormatIssue.Message("never closed")
          )
        )
    def fix(
        text: domain.library.Source.Text,
        field: FormatField
    ): (domain.library.Source.Text, List[domain.library.Source.Text]) =
      if text.value.contains("  ") then
        (
          domain.library.Source.Text(text.value.replace("  ", " ")),
          List(domain.library.Source.Text("double space"))
        )
      else (text, Nil)
    def refusal(issues: List[FormatIssue]): Option[Problem] =
      Option.when(issues.nonEmpty)(Problem.Unclassified(Legacy.Text("refused (strict)")))

  private val removed = AtomicReference(List.empty[SourceOriginals.RemoveFiles])
  private val originals = new SourceOriginals:
    def find(plan: SourceDeletePlan): List[SourceOriginals.FoundFile] =
      List(SourceOriginals.FoundFile(s"/spool/${plan.deletion.citekey}.pdf"))
    def remove(files: List[SourceOriginals.RemoveFiles]): List[SourceOriginals.RemovedFile] = {
      removed.set(files); Nil
    }

  private def controllers(gate: WriteGate = WriteGate()) =
    val state = AtomicReference(LibraryState.empty)
    val sources = SourceService.InMemory(state, LibraryStoreSuite.Ticking(), () => LibraryStoreSuite.Settings)
    val media = new MediaSources:
      def current(citekey: Citekey): Option[MediaSources.Current] =
        sources.current(citekey).map(c => MediaSources.Current(c.value))
      def lookup(citekey: Citekey): Either[MediaSources.Error, MediaSources.Key] =
        if sources.load(citekey).isDefined then Right(MediaSources.Key(citekey.value))
        else Left(MediaSources.Error.NotFound)
      def markdown(citekey: Citekey): MediaSources.Markdown = MediaSources.Markdown(sources.markdown(citekey).value)
    val figures = AssetsControllers(
      AssetService.InMemory(AtomicReference(MediaState.empty), MediaStoreSuite.Ticking()),
      media,
      () => AppConfig.load(),
      gate
    )
    val current: (AliasKind, Citekey) => Citekey = (_, id) => sources.current(id).getOrElse(id)
    SourcesControllers(
      sources,
      figures,
      formatting,
      originals,
      ck => SourcesControllers.Markdown(s"\n\n(mentioned: $ck)"),
      current,
      () => AppConfig.load(),
      gate,
      Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC)
    )(using new SystemTimer)

  private val put =
    Source.Put(
      title = Title("Walls"),
      body = Some(Source.OcrMarkdown("# Walls\n\n## Gate\n\nThe gate of Zqtell.\n")),
      notes = Some(Source.Notes("ok"))
    )

  private val Walls90 = Citekey("zqa1990_walls")
  private val Walls91 = api.library.Citekey("zqa1991_walls")
  private val Cove = Citekey("zqb2001_cove")
  private val Div = Some(Source.Notes("<div>"))

  test("a put under a citekey that is not one is refused; a strict put with a formatting error too"):
    val c = controllers()
    assertEquals(
      c.parseCitekey(api.library.Citekey("Not A Key")).flatMap(c.put(_, put)).left.map(_.code),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      c.put(Walls90, put.modify(_.notes).setTo(Div), strict = SourcesControllers.PutStrict(true))
        .left
        .map(_.problems.head)
        .left
        .map(_.sentence),
      Left("refused (strict)")
    )
    val written = c.put(Walls90, put.modify(_.notes).setTo(Div)).toOption.get
    assertEquals(written.formatting.size, 1)

  test("an old citekey is followed by every read and write; a section that is not one, passages of no source"):
    val c = controllers()
    val _ = c.put(Walls90, put)
    val _ = c.rename(Walls90, Rename.Request(Walls91))
    assertEquals(
      c.get(Walls90, body = SourcesControllers.GetBody(false)).left.map(Api.Error(_)).map(_.source.citekey),
      Right("zqa1991_walls")
    )
    assertEquals(c.canonical(Walls90), "zqa1991_walls")
    assertEquals(
      c.patch(Walls90, Source.Patch(summary = Some(Source.Summary("s")))).map(_.source.summary),
      Right(Some("s"))
    )
    assert(
      c.markdown(Walls90, SourcesControllers.MarkdownFullText(false))
        .left
        .map(Api.Error(_))
        .toOption
        .get
        .value
        .endsWith("(mentioned: zqa1991_walls)")
    )
    assert(
      c.markdown(Walls90, SourcesControllers.MarkdownFullText(false), Some(Source.Section.Wire("toc")))
        .left
        .map(Api.Error(_))
        .toOption
        .get
        .value
        .contains("Gate")
    )
    assert(
      c.markdown(Walls90, SourcesControllers.MarkdownFullText(false), Some(Source.Section.Wire("nonsense")))
        .left
        .map(Api.Error(_))
        .left
        .exists(_.problems.sentences.mkString("; ").startsWith("unknown source section 'nonsense' (one of: summary"))
    )
    assertEquals(
      c.passages(Api.PassagesQuery(api.library.Citekey("zqa1990_walls"), Api.PassagesQuery.Q("gate")))
        .map(_.matches),
      Right(1)
    )
    assertEquals(
      c.passages(Api.PassagesQuery(api.library.Citekey("zqnone2000_x"), Api.PassagesQuery.Q("gate")))
        .left
        .map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(c.aliases, List(Source.Alias(Source.Alias.Value("zqa1990_walls"), Walls91)))

  test("formatting repaired as one guarded patch; a delete's dry run names the originals, its apply removes them"):
    val c = controllers()
    val _ = c.put(Walls90, put.modify(_.notes).setTo(Some(Source.Notes("two  spaces"))))
    assertEquals(c.fix(Walls90).map(_.fixed), Right(List("notes: double space")))
    assertEquals(c.get(Walls90).left.map(Api.Error(_)).map(_.source.notes), Right(Some("two spaces")))
    assertEquals(
      c.delete(Walls90, Source.DeleteRequest()).map(_.files),
      Right(List("/spool/zqa1990_walls.pdf"))
    )
    assertEquals(c.get(Walls90).left.map(Api.Error(_)).isRight, true)
    val gone = c.delete(Walls90, Source.DeleteRequest(apply = Source.DeleteRequest.Apply(true))).toOption.get
    assertEquals((gone.applied, gone.filesFailed, removed.get), (true, Nil, List("/spool/zqa1990_walls.pdf")))
    assertEquals(
      c.get(Walls90).left.map(Api.Error(_)).left.map(_.problems.head).left.map(_.sentence),
      Left("No source 'zqa1990_walls'")
    )

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val c = controllers(gate)
    val _ = c.put(Walls90, put)
    val held = gate.exclusively(WriteGate.OperationName("restore"))(
      Right(
        List(
          c.put(Cove, put).isLeft,
          c.patch(Walls90, Source.Patch(summary = Some(Source.Summary("x")))).isLeft,
          c.rename(Walls90, Rename.Request(Walls91)).isLeft,
          c.delete(Walls90, Source.DeleteRequest(apply = Source.DeleteRequest.Apply(true))).isLeft,
          c.fix(Walls90).isLeft,
          c.importNote(NoteImport(NoteImport.Text("---\ncitekey: zqn2000_n\ntitle: N\n---\n"))).isLeft
        )
      )
    )
    assertEquals(held.fold(e => fail(e.problems.sentences.mkString("; ")), identity), List.fill(6)(true))
