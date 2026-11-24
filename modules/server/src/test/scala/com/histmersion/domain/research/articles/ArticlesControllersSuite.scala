package com.histmersion
package domain.research.articles
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.domain.catalogue.places.{PlaceScope, PlaceService}
import com.histmersion.domain.library.{LibraryState, LibraryStoreSuite}
import com.histmersion.domain.library.search.SearchFilters
import com.histmersion.domain.library.sources.{SourceFormatting, SourceOriginals, SourcesControllers, SourceService}
import com.histmersion.domain.media.{MediaSources, MediaState, MediaStoreSuite}
import com.histmersion.domain.media.assets.{AssetsControllers, AssetService}
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.{ResearchState, Slug}
import com.histmersion.util.identity.AliasTable
import com.histmersion.domain.research.articles.review.ReviewStateService
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.aliases.LabelRename
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatSeverity}
import com.histmersion.api.library.Filter
import com.histmersion.api.research.ReviewGoal
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.domain.library.Edge
import com.histmersion.domain.ArticleReviewStatus
import com.histmersion.domain.automation.Run
import com.histmersion.domain.library.search.Search
import com.histmersion.api.personal.UnreadPage
import com.histmersion.domain.library.Source
import com.histmersion.domain.library.sources.SourceNote
import com.histmersion.domain.library.sources.SourceDeletePlan
import com.histmersion.util.time.SystemTimer
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** The articles' controllers over the in-memory services, with no source stored: a slug that is not one and a strict
  * write refused by the formatting check, an old slug followed by every read and write, a coverage with no source, a
  * note imported (stale, under an old slug, fresh), the versions of no article, and every write refused while the
  * maintenance lock is held.
  */
class ArticlesControllersSuite extends munit.FunSuite:

  /** The library's reads as a database with no source answers them. */
  private val noSources = new CoverageService:
    def sourceSet(filter: Filter, scope: PlaceScope, cap: ArticleRepo.Limit): (RowCount, List[Coverage.Source]) =
      (RowCount(0), Nil)
    def members(filter: Filter, scope: PlaceScope, citekeys: List[Citekey]): (RowCount, Set[Citekey]) =
      (RowCount(0), Set.empty)
    def edgesTouching(citekeys: List[Citekey], kinds: List[Edge.Kind.Wire]): List[Coverage.Edge] = Nil

  /** The engine's records as a database with no manager item, ledger, run or goal answers them. */
  private val noReviews = new ReviewStateService:
    def facts(slugs: List[Slug]) =
      slugs.map(_ -> (ArticleReviewStatus.Facts(), None)).toMap
    def runStarts(runs: List[Run.Id]): Map[Run.Id, ArticleReviewStatus.RunStart] = Map.empty
    def goals(slug: Slug): List[ReviewGoal] = Nil

  /** Not reached by these tests: the list is a mechanism over the library's filter SQL. */
  private val noSearch = new ArticleSearch:
    def list(
        f: Filter,
        s: PlaceScope,
        k: Option[domain.research.Article.Kind],
        t: Option[Search.Query],
        l: ArticleRepo.Limit,
        o: Offset
    ): Article.List =
      Article.List.of(Article.List.Total(0), Nil, UnreadPage.Offset(0))

  private val formatting = new SourceFormatting:
    def issues(summary: Option[Source.Summary], notes: Option[SourceNote.Text]): List[FormatIssue] = Nil
    def fix(text: Source.Text, field: FormatField): (Source.Text, List[Source.Text]) = (text, Nil)
    def refusal(issues: List[FormatIssue]): Option[Problem] = None

  private val originals = new SourceOriginals:
    def find(plan: SourceDeletePlan): List[SourceOriginals.FoundFile] = Nil
    def remove(files: List[SourceOriginals.RemoveFiles]): List[SourceOriginals.RemovedFile] = Nil

  private def controllers(gate: WriteGate = WriteGate()): ArticlesControllers =
    val sourceStore =
      SourceService.InMemory(
        AtomicReference(LibraryState.empty),
        LibraryStoreSuite.Ticking(),
        () => LibraryStoreSuite.Settings
      )
    val media = new MediaSources:
      def current(citekey: Citekey): Option[MediaSources.Current] =
        sourceStore.current(citekey).map(c => MediaSources.Current(c.value))
      def lookup(citekey: Citekey): Either[MediaSources.Error, MediaSources.Key] =
        if sourceStore.load(citekey).isDefined then Right(MediaSources.Key(citekey.value))
        else Left(MediaSources.Error.NotFound)
      def markdown(citekey: Citekey): MediaSources.Markdown = MediaSources.Markdown(sourceStore.markdown(citekey).value)
    val figures = AssetsControllers(
      AssetService.InMemory(AtomicReference(MediaState.empty), MediaStoreSuite.Ticking()),
      media,
      () => AppConfig.load(),
      gate
    )
    val sources = SourcesControllers(
      sourceStore,
      figures,
      formatting,
      originals,
      _ => SourcesControllers.Markdown(""),
      (_, id) => sourceStore.current(id).getOrElse(id),
      () => AppConfig.load(),
      gate,
      Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    )(using new SystemTimer)
    val places =
      PlaceService.InMemory(AtomicReference(CatalogueState.empty), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    val store = ArticleService.InMemory(AtomicReference(ResearchState.empty), LibraryStoreSuite.Ticking())
    val current: (AliasKind, AliasTable.Id) => AliasTable.Id = (_, id) =>
      Slug.parse(id.value).toOption.fold(id)(slug => AliasTable.Id(store.canonical(slug).value))
    lazy val c: ArticlesControllers = ArticlesControllers(
      store,
      noSearch,
      noSources,
      noReviews,
      sources,
      sourceStore,
      SearchFilters(places, () => AppConfig.load()),
      _ => Right(None),
      (_, _, _) => Nil,
      slug => c.articleNote(slug),
      current,
      () => AppConfig.load(),
      gate
    )
    c

  private val put = Article.Put(
    title = Article.Title("Walls"),
    kind = Article.Kind.Wire("concept"),
    body = Article.Body("## Overview\n\nThe walls of Zrtown.\n"),
    filter = Some(Article.Filter(tags = List(Filter.Tags("zr-oracle"))))
  )

  test("a slug that is not one is refused; a strict write with a formatting error too, a lenient one carries it"):
    val c = controllers()
    assertEquals(c.put(Slug.assume("Not A Slug"), put).left.map(_.code), Left(Some(Write.CodeInvalid)))
    val messy = put.modify(_.body).setTo(Article.Body("<div> never closed\n"))
    assertEquals(
      c.put(Slug("zr_walls"), messy, strict = domain.research.Article.StrictMode(true))
        .left
        .map(_.code),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(c.store.exists(Slug("zr_walls")), false)
    val written = c.put(Slug("zr_walls"), messy).toOption.get
    assert(written.formatting.exists(_.severity == FormatSeverity.Error), written.formatting)

  test("an old slug is followed by every read and write; the coverage of a filter that returns no source"):
    val c = controllers()
    val _ = c.put(Slug("zr_walls"), put)
    val _ = c.put(
      Slug("zr_index"),
      put
        .modify(_.title)
        .setTo(Article.Title("Index"))
        .modify(_.kind)
        .setTo(Article.Kind.Wire("index"))
        .modify(_.body)
        .setTo(Article.Body("- [[zr_walls]]\n"))
        .modify(_.filter)
        .setTo(None)
    )
    assertEquals(
      c.rename(Slug("zr_walls"), LabelRename(LabelRename.To("zr_city_walls"))).map(_.rewritten),
      Right(List("zr_index"))
    )
    assertEquals(c.get(Slug.parse("ZR_WALLS").toOption.get).map(_.article.slug), Right("zr_city_walls"))
    assertEquals(
      c.patch(Slug("zr_walls"), Article.Patch(title = Some(Article.Title("City walls")))).map(_.article.title),
      Right("City walls")
    )
    assertEquals(c.versions(Slug("zr_walls")).map(_.size), Right(1))
    val coverage = c.coverage(Slug("zr_walls")).toOption.get
    assertEquals((coverage.slug, coverage.total, coverage.cited), ("zr_city_walls", 0, Nil))
    assertEquals(c.coverage(Slug("zr_index")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(c.versions(Slug("zr_nothing")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(c.review(Slug("zr_walls")).map(_.slug), Right("zr_city_walls"))
    assert(c.exportNote(Slug("zr_walls")).toOption.get.value.contains("slug: zr_city_walls"))
    assertEquals(c.delete(Slug("zr_walls")).map(_.slug), Right("zr_city_walls"))
    assertEquals(c.get(Slug("zr_walls")).left.map(_.code), Left(Some(Write.CodeNotFound)))

  test("a note: stale when the row is newer, refused under an old slug, imported fresh"):
    val c = controllers()
    val _ = c.put(Slug("zr_walls"), put)
    val note = Article.Import.Text(c.exportNote(Slug("zr_walls")).toOption.get.value)
    assertEquals(
      c.importNote(Article.Import(note, mtime = Some(Article.Import.Mtime(1000L)))).map(_.skipped),
      Right(List("zr_walls"))
    )
    assertEquals(
      c.importNote(Article.Import(note, mtime = Some(Article.Import.Mtime(1000L)), force = Article.Import.Force(true)))
        .map(_.imported),
      Right(1)
    )
    val _ = c.rename(Slug("zr_walls"), LabelRename(LabelRename.To("zr_city_walls")))
    assertEquals(c.importNote(Article.Import(note)).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(c.importNote(Article.Import(Article.Import.Text("no frontmatter"))).isLeft, true)

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val c = controllers(gate)
    val _ = c.put(Slug("zr_walls"), put)
    val held = gate.exclusively(WriteGate.OperationName("restore"))(
      Right(
        List(
          c.put(Slug("zr_harbour"), put).isLeft,
          c.patch(Slug("zr_walls"), Article.Patch(title = Some(Article.Title("x")))).isLeft,
          c.rename(Slug("zr_walls"), LabelRename(LabelRename.To("zr_city_walls"))).isLeft,
          c.delete(Slug("zr_walls")).isLeft,
          c.fix(Slug("zr_walls")).isLeft,
          c.importNote(Article.Import(Article.Import.Text("---\nslug: zr_n\ntitle: N\nkind: concept\n---\n"))).isLeft
        )
      )
    )
    assertEquals(held.fold(e => fail(e.problems.sentences.mkString("; ")), identity), List.fill(6)(true))
    assertEquals(c.store.slugs, List("zr_walls"))
