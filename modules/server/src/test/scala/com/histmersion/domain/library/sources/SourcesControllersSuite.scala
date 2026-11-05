package com.histmersion.domain.library.sources

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{Api, Write}
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.{LibraryState, LibraryStoreSuite}
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

/** A source's controllers over the in-memory services: a citekey that is not one, a strict write refused by the
  * formatting check, an old citekey followed, a section that is not one, passages of a source not there, a delete's dry
  * run naming the stored originals and its apply removing them, and every write refused while the maintenance lock is
  * held.
  */
class SourcesControllersSuite extends munit.FunSuite:

  private val formatting = new SourceFormatting:
    def issues(summary: Option[String], notes: Option[String]): List[FormatIssue] =
      (summary.toList ++ notes.toList)
        .filter(_.contains("<div>"))
        .map(_ =>
          FormatIssue(FormatRule.HtmlTag, FormatSeverity.Error, FormatField.Notes, 1, 1, "<div>", "never closed")
        )
    def fix(text: String, field: FormatField): (String, List[String]) =
      if text.contains("  ") then (text.replace("  ", " "), List("double space")) else (text, Nil)
    def refusal(issues: List[FormatIssue]): Option[String] = Option.when(issues.nonEmpty)("refused (strict)")

  private val removed = AtomicReference(List.empty[String])
  private val originals = new SourceOriginals:
    def find(plan: SourceDeletePlan): List[String] = List(s"/spool/${plan.deletion.citekey}.pdf")
    def remove(files: List[String]): List[String] = { removed.set(files); Nil }

  private def controllers(gate: WriteGate = WriteGate()) =
    val state = AtomicReference(LibraryState.empty)
    val sources = SourceService.InMemory(state, LibraryStoreSuite.Ticking(), () => LibraryStoreSuite.Settings)
    val media = new MediaSources:
      def current(citekey: String): Option[String] = sources.current(citekey)
      def exists(citekey: String): Boolean = sources.load(citekey).isDefined
      def markdown(citekey: String): String = sources.markdown(citekey)
    val figures = AssetsControllers(
      AssetService.InMemory(AtomicReference(MediaState.empty), MediaStoreSuite.Ticking()),
      media,
      () => AppConfig.load(),
      gate
    )
    val current: (AliasKind, String) => String = (_, id) => sources.current(id).getOrElse(id)
    SourcesControllers(
      sources,
      figures,
      formatting,
      originals,
      ck => s"\n\n(mentioned: $ck)",
      current,
      () => AppConfig.load(),
      gate,
      Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC)
    )(using new com.histmersion.util.time.SystemTimer)

  private val put =
    Source.Put(title = "Walls", body = Some("# Walls\n\n## Gate\n\nThe gate of Zqtell.\n"), notes = Some("ok"))

  test("a put under a citekey that is not one is refused; a strict put with a formatting error too"):
    val c = controllers()
    assertEquals(c.put("Not A Key", put).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(
      c.put("zqa1990_walls", put.modify(_.notes).setTo(Some("<div>")), strict = true).left.map(_.message),
      Left("refused (strict)")
    )
    val written = c.put("zqa1990_walls", put.modify(_.notes).setTo(Some("<div>"))).toOption.get
    assertEquals(written.formatting.size, 1)

  test("an old citekey is followed by every read and write; a section that is not one, passages of no source"):
    val c = controllers()
    val _ = c.put("zqa1990_walls", put)
    val _ = c.rename("zqa1990_walls", Rename.Request("zqa1991_walls"))
    assertEquals(c.get("zqa1990_walls", body = false).map(_.source.citekey), Right("zqa1991_walls"))
    assertEquals(c.canonical("zqa1990_walls"), "zqa1991_walls")
    assertEquals(c.patch("zqa1990_walls", Source.Patch(summary = Some("s"))).map(_.source.summary), Right(Some("s")))
    assert(c.markdown("zqa1990_walls", fullText = false).toOption.get.endsWith("(mentioned: zqa1991_walls)"))
    assert(c.markdown("zqa1990_walls", false, Some("toc")).toOption.get.contains("Gate"))
    assert(c.markdown("zqa1990_walls", false, Some("nonsense")).left.exists(_.message.startsWith("unknown section")))
    assertEquals(c.passages(Api.PassagesQuery("zqa1990_walls", "gate")).map(_.matches), Right(1))
    assertEquals(c.passages(Api.PassagesQuery("zqnone2000_x", "gate")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(c.aliases, List(Source.Alias("zqa1990_walls", "zqa1991_walls")))

  test("formatting repaired as one guarded patch; a delete's dry run names the originals, its apply removes them"):
    val c = controllers()
    val _ = c.put("zqa1990_walls", put.modify(_.notes).setTo(Some("two  spaces")))
    assertEquals(c.fix("zqa1990_walls").map(_.fixed), Right(List("notes: double space")))
    assertEquals(c.get("zqa1990_walls").map(_.source.notes), Right(Some("two spaces")))
    assertEquals(
      c.delete("zqa1990_walls", Source.DeleteRequest()).map(_.files),
      Right(List("/spool/zqa1990_walls.pdf"))
    )
    assertEquals(c.get("zqa1990_walls").isRight, true)
    val gone = c.delete("zqa1990_walls", Source.DeleteRequest(apply = true)).toOption.get
    assertEquals((gone.applied, gone.filesFailed, removed.get), (true, Nil, List("/spool/zqa1990_walls.pdf")))
    assertEquals(c.get("zqa1990_walls").left.map(_.message), Left("No source 'zqa1990_walls'"))

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val c = controllers(gate)
    val _ = c.put("zqa1990_walls", put)
    val held = gate.exclusively("restore")(
      Right(
        List(
          c.put("zqb2001_cove", put).isLeft,
          c.patch("zqa1990_walls", Source.Patch(summary = Some("x"))).isLeft,
          c.rename("zqa1990_walls", Rename.Request("zqa1991_walls")).isLeft,
          c.delete("zqa1990_walls", Source.DeleteRequest(apply = true)).isLeft,
          c.fix("zqa1990_walls").isLeft,
          c.importNote(NoteImport("---\ncitekey: zqn2000_n\ntitle: N\n---\n")).isLeft
        )
      )
    )
    assertEquals(held.fold(e => fail(e.message), identity), List.fill(6)(true))
