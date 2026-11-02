package com.histmersion.domain.research.articles

import com.histmersion.api.*
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.domain.catalogue.places.{PlaceScope, PlaceService}
import com.histmersion.domain.library.{LibraryState, LibraryStoreSuite}
import com.histmersion.domain.library.search.SearchFilters
import com.histmersion.domain.library.sources.{SourceFormatting, SourceOriginals, SourcesControllers, SourceService}
import com.histmersion.domain.media.{MediaSources, MediaState, MediaStoreSuite}
import com.histmersion.domain.media.assets.{AssetsControllers, AssetService}
import com.histmersion.domain.research.ResearchState
import com.histmersion.domain.research.articles.review.ReviewStateService
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.identity.AliasKind

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference

/** The articles' controllers over the in-memory services, with no source stored: a slug that is not one and a strict
  * write refused by the formatting check, an old slug followed by every read and write, a coverage with no source, a
  * note imported (stale, under an old slug, fresh), the versions of no article, and every write refused while the
  * maintenance lock is held.
  */
class ArticlesControllersSuite extends munit.FunSuite:

  /** The library's reads as a database with no source answers them. */
  private val noSources = new CoverageService:
    def sourceSet(filter: Filter, scope: PlaceScope, cap: Int): (Int, List[CoverageSource]) = (0, Nil)
    def members(filter: Filter, scope: PlaceScope, citekeys: List[String]): (Int, Set[String]) = (0, Set.empty)
    def edgesTouching(citekeys: List[String], kinds: List[String]): List[CoverageEdge] = Nil

  /** The engine's records as a database with no manager item, ledger, run or goal answers them. */
  private val noReviews = new ReviewStateService:
    def facts(slugs: List[String]): Map[String, (com.histmersion.domain.ArticleReviewStatus.Facts, Option[String])] =
      slugs.map(_ -> (com.histmersion.domain.ArticleReviewStatus.Facts(), None)).toMap
    def runStarts(runs: List[String]): Map[String, String] = Map.empty
    def goals(slug: String): List[ReviewGoal] = Nil

  /** Not reached by these tests: the list is a mechanism over the library's filter SQL. */
  private val noSearch = new ArticleSearch:
    def list(f: Filter, s: PlaceScope, k: Option[String], t: Option[String], l: Int, o: Int): ArticleList =
      ArticleList.of(0, Nil, 0)

  private val formatting = new SourceFormatting:
    def issues(summary: Option[String], notes: Option[String]): List[FormatIssue] = Nil
    def fix(text: String, field: FormatField): (String, List[String]) = (text, Nil)
    def refusal(issues: List[FormatIssue]): Option[String] = None

  private val originals = new SourceOriginals:
    def find(plan: com.histmersion.domain.library.sources.SourceDeletePlan): List[String] = Nil
    def remove(files: List[String]): List[String] = Nil

  private def controllers(gate: WriteGate = WriteGate()): ArticlesControllers =
    val sourceStore =
      SourceService.InMemory(
        AtomicReference(LibraryState.empty),
        LibraryStoreSuite.Ticking(),
        () => LibraryStoreSuite.Settings
      )
    val media = new MediaSources:
      def current(citekey: String): Option[String] = sourceStore.current(citekey)
      def exists(citekey: String): Boolean = sourceStore.load(citekey).isDefined
      def markdown(citekey: String): String = sourceStore.markdown(citekey)
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
      _ => "",
      (_, id) => sourceStore.current(id).getOrElse(id),
      () => AppConfig.load(),
      gate,
      Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    )
    val places =
      PlaceService.InMemory(AtomicReference(CatalogueState.empty), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    val store = ArticleService.InMemory(AtomicReference(ResearchState.empty), LibraryStoreSuite.Ticking())
    val current: (AliasKind, String) => String = (_, slug) => store.canonical(slug)
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

  private val put = ArticlePut(
    title = "Walls",
    kind = "concept",
    body = "## Overview\n\nThe walls of Zrtown.\n",
    filter = Some(ArticleFilter(tags = List("zr-oracle")))
  )

  test("a slug that is not one is refused; a strict write with a formatting error too, a lenient one carries it"):
    val c = controllers()
    assertEquals(c.put("Not A Slug", put).left.map(_.code), Left(Some(Write.CodeInvalid)))
    val messy = put.copy(body = "<div> never closed\n")
    assertEquals(c.put("zr_walls", messy, strict = true).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(c.store.exists("zr_walls"), false)
    val written = c.put("zr_walls", messy).toOption.get
    assert(written.formatting.exists(_.severity == FormatSeverity.Error), written.formatting)

  test("an old slug is followed by every read and write; the coverage of a filter that returns no source"):
    val c = controllers()
    val _ = c.put("zr_walls", put)
    val _ = c.put("zr_index", put.copy(title = "Index", kind = "index", body = "- [[zr_walls]]\n", filter = None))
    assertEquals(c.rename("zr_walls", LabelRename("zr_city_walls")).map(_.rewritten), Right(List("zr_index")))
    assertEquals(c.get("ZR_WALLS").map(_.article.slug), Right("zr_city_walls"))
    assertEquals(
      c.patch("zr_walls", ArticlePatch(title = Some("City walls"))).map(_.article.title),
      Right("City walls")
    )
    assertEquals(c.versions("zr_walls").map(_.size), Right(1))
    val coverage = c.coverage("zr_walls").toOption.get
    assertEquals((coverage.slug, coverage.total, coverage.cited), ("zr_city_walls", 0, Nil))
    assertEquals(c.coverage("zr_index").left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(c.versions("zr_nothing").left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(c.review("zr_walls").map(_.slug), Right("zr_city_walls"))
    assert(c.exportNote("zr_walls").toOption.get.contains("slug: zr_city_walls"))
    assertEquals(c.delete("zr_walls").map(_.slug), Right("zr_city_walls"))
    assertEquals(c.get("zr_walls").left.map(_.code), Left(Some(Write.CodeNotFound)))

  test("a note: stale when the row is newer, refused under an old slug, imported fresh"):
    val c = controllers()
    val _ = c.put("zr_walls", put)
    val note = c.exportNote("zr_walls").toOption.get
    assertEquals(c.importNote(ArticleImport(note, mtime = Some(1000L))).map(_.skipped), Right(List("zr_walls")))
    assertEquals(c.importNote(ArticleImport(note, mtime = Some(1000L), force = true)).map(_.imported), Right(1))
    val _ = c.rename("zr_walls", LabelRename("zr_city_walls"))
    assertEquals(c.importNote(ArticleImport(note)).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(c.importNote(ArticleImport("no frontmatter")).isLeft, true)

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val c = controllers(gate)
    val _ = c.put("zr_walls", put)
    val held = gate.exclusively("restore")(
      Right(
        List(
          c.put("zr_harbour", put).isLeft,
          c.patch("zr_walls", ArticlePatch(title = Some("x"))).isLeft,
          c.rename("zr_walls", LabelRename("zr_city_walls")).isLeft,
          c.delete("zr_walls").isLeft,
          c.fix("zr_walls").isLeft,
          c.importNote(ArticleImport("---\nslug: zr_n\ntitle: N\nkind: concept\n---\n")).isLeft
        )
      )
    )
    assertEquals(held.fold(e => fail(e.message), identity), List.fill(6)(true))
    assertEquals(c.store.slugs, List("zr_walls"))
