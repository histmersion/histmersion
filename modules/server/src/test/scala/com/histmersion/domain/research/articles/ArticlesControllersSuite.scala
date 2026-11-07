package com.histmersion.domain.research.articles

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

/** The articles' controllers over the in-memory services, with no source stored: a slug that is not one and a strict
  * write refused by the formatting check, an old slug followed by every read and write, a coverage with no source, a
  * note imported (stale, under an old slug, fresh), the versions of no article, and every write refused while the
  * maintenance lock is held.
  */
class ArticlesControllersSuite extends munit.FunSuite:

  /** The library's reads as a database with no source answers them. */
  private val noSources = new CoverageService:
    def sourceSet(filter: Filter, scope: PlaceScope, cap: ArticleRepo.Limit): (com.histmersion.RowCount, List[Coverage.Source]) = (com.histmersion.RowCount(0), Nil)
    def members(filter: Filter, scope: PlaceScope, citekeys: List[Citekey]): (com.histmersion.RowCount, Set[Citekey]) = (com.histmersion.RowCount(0), Set.empty)
    def edgesTouching(citekeys: List[Citekey], kinds: List[com.histmersion.domain.library.Edge.Kind.Wire]): List[Coverage.Edge] = Nil

  /** The engine's records as a database with no manager item, ledger, run or goal answers them. */
  private val noReviews = new ReviewStateService:
    def facts(slugs: List[Slug]) =
      slugs.map(_ -> (com.histmersion.domain.ArticleReviewStatus.Facts(), None)).toMap
    def runStarts(runs: List[com.histmersion.domain.automation.Run.Id]): Map[com.histmersion.domain.automation.Run.Id, com.histmersion.domain.ArticleReviewStatus.RunStart] = Map.empty
    def goals(slug: Slug): List[ReviewGoal] = Nil

  /** Not reached by these tests: the list is a mechanism over the library's filter SQL. */
  private val noSearch = new ArticleSearch:
    def list(f: Filter, s: PlaceScope, k: Option[com.histmersion.domain.research.Article.Kind], t: Option[com.histmersion.domain.library.search.Search.Query], l: ArticleRepo.Limit, o: com.histmersion.Offset): Article.List =
      Article.List.of(com.histmersion.api.research.Article.List.Total(0), Nil, com.histmersion.api.personal.UnreadPage.Offset(0))

  private val formatting = new SourceFormatting:
    def issues(summary: Option[com.histmersion.domain.library.Source.Summary], notes: Option[com.histmersion.domain.library.sources.SourceNote.Text]): List[FormatIssue] = Nil
    def fix(text: com.histmersion.domain.library.Source.Text, field: FormatField): (com.histmersion.domain.library.Source.Text, List[com.histmersion.domain.library.Source.Text]) = (text, Nil)
    def refusal(issues: List[FormatIssue]): Option[SourceFormatting.Problem] = None

  private val originals = new SourceOriginals:
    def find(plan: com.histmersion.domain.library.sources.SourceDeletePlan): List[SourceOriginals.FindResult] = Nil
    def remove(files: List[SourceOriginals.RemoveFiles]): List[SourceOriginals.RemoveResult] = Nil

  private def controllers(gate: WriteGate = WriteGate()): ArticlesControllers =
    val sourceStore =
      SourceService.InMemory(
        AtomicReference(LibraryState.empty),
        LibraryStoreSuite.Ticking(),
        () => LibraryStoreSuite.Settings
      )
    val media = new MediaSources:
      def current(citekey: Citekey): Option[MediaSources.Current] = sourceStore.current(citekey).map(c => MediaSources.Current(c.value))
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
    )(using new com.histmersion.util.time.SystemTimer)
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
    title = com.histmersion.api.research.Article.Title("Walls"),
    kind = com.histmersion.api.research.Article.Kind.Wire("concept"),
    body = com.histmersion.api.research.Article.Body("## Overview\n\nThe walls of Zrtown.\n"),
    filter = Some(Article.Filter(tags = List(com.histmersion.api.library.Filter.Tags("zr-oracle"))))
  )

  test("a slug that is not one is refused; a strict write with a formatting error too, a lenient one carries it"):
    val c = controllers()
    assertEquals(c.put(com.histmersion.domain.research.Slug("Not A Slug"), put).left.map(_.code), Left(Some(Write.CodeInvalid)))
    val messy = put.modify(_.body).setTo(Article.Body("<div> never closed\n"))
    assertEquals(c.put(com.histmersion.domain.research.Slug("zr_walls"), messy, strict = com.histmersion.domain.research.Article.StrictMode(true)).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(c.store.exists(com.histmersion.domain.research.Slug("zr_walls")), false)
    val written = c.put(com.histmersion.domain.research.Slug("zr_walls"), messy).toOption.get
    assert(written.formatting.exists(_.severity == FormatSeverity.Error), written.formatting)

  test("an old slug is followed by every read and write; the coverage of a filter that returns no source"):
    val c = controllers()
    val _ = c.put(com.histmersion.domain.research.Slug("zr_walls"), put)
    val _ = c.put(com.histmersion.domain.research.Slug("zr_index"), put.modify(_.title).setTo(Article.Title("Index")).modify(_.kind).setTo(Article.Kind.Wire("index")).modify(_.body).setTo(Article.Body("- [[zr_walls]]\n")).modify(_.filter).setTo(None))
    assertEquals(c.rename(com.histmersion.domain.research.Slug("zr_walls"), LabelRename(com.histmersion.api.aliases.LabelRename.To("zr_city_walls"))).map(_.rewritten), Right(List("zr_index")))
    assertEquals(c.get(Slug.parse("ZR_WALLS").toOption.get).map(_.article.slug), Right("zr_city_walls"))
    assertEquals(
      c.patch(com.histmersion.domain.research.Slug("zr_walls"), Article.Patch(title = Some(com.histmersion.api.research.Article.Title("City walls")))).map(_.article.title),
      Right("City walls")
    )
    assertEquals(c.versions(com.histmersion.domain.research.Slug("zr_walls")).map(_.size), Right(1))
    val coverage = c.coverage(com.histmersion.domain.research.Slug("zr_walls")).toOption.get
    assertEquals((coverage.slug, coverage.total, coverage.cited), ("zr_city_walls", 0, Nil))
    assertEquals(c.coverage(com.histmersion.domain.research.Slug("zr_index")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(c.versions(com.histmersion.domain.research.Slug("zr_nothing")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(c.review(com.histmersion.domain.research.Slug("zr_walls")).map(_.slug), Right("zr_city_walls"))
    assert(c.exportNote(com.histmersion.domain.research.Slug("zr_walls")).toOption.get.value.contains("slug: zr_city_walls"))
    assertEquals(c.delete(com.histmersion.domain.research.Slug("zr_walls")).map(_.slug), Right("zr_city_walls"))
    assertEquals(c.get(com.histmersion.domain.research.Slug("zr_walls")).left.map(_.code), Left(Some(Write.CodeNotFound)))

  test("a note: stale when the row is newer, refused under an old slug, imported fresh"):
    val c = controllers()
    val _ = c.put(com.histmersion.domain.research.Slug("zr_walls"), put)
    val note = Article.Import.Text(c.exportNote(com.histmersion.domain.research.Slug("zr_walls")).toOption.get.value)
    assertEquals(c.importNote(Article.Import(note, mtime = Some(com.histmersion.api.research.Article.Import.Mtime(1000L)))).map(_.skipped), Right(List("zr_walls")))
    assertEquals(c.importNote(Article.Import(note, mtime = Some(com.histmersion.api.research.Article.Import.Mtime(1000L)), force = com.histmersion.api.research.Article.Import.Force(true))).map(_.imported), Right(1))
    val _ = c.rename(com.histmersion.domain.research.Slug("zr_walls"), LabelRename(com.histmersion.api.aliases.LabelRename.To("zr_city_walls")))
    assertEquals(c.importNote(Article.Import(note)).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(c.importNote(Article.Import(com.histmersion.api.research.Article.Import.Text("no frontmatter"))).isLeft, true)

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val c = controllers(gate)
    val _ = c.put(com.histmersion.domain.research.Slug("zr_walls"), put)
    val held = gate.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore"))(
      Right(
        List(
          c.put(com.histmersion.domain.research.Slug("zr_harbour"), put).isLeft,
          c.patch(com.histmersion.domain.research.Slug("zr_walls"), Article.Patch(title = Some(com.histmersion.api.research.Article.Title("x")))).isLeft,
          c.rename(com.histmersion.domain.research.Slug("zr_walls"), LabelRename(com.histmersion.api.aliases.LabelRename.To("zr_city_walls"))).isLeft,
          c.delete(com.histmersion.domain.research.Slug("zr_walls")).isLeft,
          c.fix(com.histmersion.domain.research.Slug("zr_walls")).isLeft,
          c.importNote(Article.Import(com.histmersion.api.research.Article.Import.Text("---\nslug: zr_n\ntitle: N\nkind: concept\n---\n"))).isLeft
        )
      )
    )
    assertEquals(held.fold(e => fail(e.message.value), identity), List.fill(6)(true))
    assertEquals(c.store.slugs, List("zr_walls"))
