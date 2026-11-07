package com.histmersion.db

import com.histmersion.domain.library.search.SearchFixtures

import com.histmersion.domain.library.search.FilterSql
import com.histmersion.domain.library.search.Search

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.BBox
import com.histmersion.domain.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import com.histmersion.util.database.Sql
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Filter, Sort}
import com.histmersion.api.library.Facet
import com.histmersion.util.database.given

/** The SQL the filter's builders generate, recorded for every combination the search exercises: each criterion alone
  * (text, tags, authors, places by name and by gazetteer id, topics, statuses, publication years, deep time, a box and
  * one across the antimeridian, a title, a goal), pairs and the whole filter, every order, pages, each facet field with
  * and without a prefix, the map feed, the counts, the snippets and the histogram's rows in both units — with an empty
  * place scope and one that names gazetteer ids. The text and the bound parameters of each statement, so a move of the
  * builders shows any change to the query the database is sent. `HISTMERSION_UPDATE_ORACLE=1` rewrites the file.
  */
class SearchSqlSnapshotSuite extends munit.FunSuite:

  private val file = Path.of("modules/db/src/test/resources/snapshot/search-sql.txt")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private val scoped = PlaceScope(
    Map("zq_land" -> List("zq_land", "zq_tell", "zq_cove"), "zq_tell" -> List("zq_tell")),
    Map("zq_land" -> "Zqland", "zq_tell" -> "Zqtell")
  )

  private val Box = BBox(com.histmersion.api.catalogue.Longitude(30), com.histmersion.api.catalogue.Latitude(36), com.histmersion.api.catalogue.Longitude(40), com.histmersion.api.catalogue.Latitude(42))
  private val Antimeridian = BBox(com.histmersion.api.catalogue.Longitude(170), com.histmersion.api.catalogue.Latitude(-25), com.histmersion.api.catalogue.Longitude(-170), com.histmersion.api.catalogue.Latitude(-10))

  private val criteria: List[(String, Filter)] = List(
    "empty" -> Filter(),
    "q" -> Filter(q = Some(com.histmersion.api.library.Filter.Q("collapse"))),
    "q phrase" -> Filter(q = Some(com.histmersion.api.library.Filter.Q("\"sea peoples\""))),
    "q exclusion" -> Filter(q = Some(com.histmersion.api.library.Filter.Q("tin -islands"))),
    "q or" -> Filter(q = Some(com.histmersion.api.library.Filter.Q("tin or kings"))),
    "q blank" -> Filter(q = Some(com.histmersion.api.library.Filter.Q("   "))),
    "q with a percent" -> Filter(q = Some(com.histmersion.api.library.Filter.Q("50% of_it"))),
    "tag" -> Filter(tags = List(com.histmersion.api.library.Filter.Tags("sea-peoples"))),
    "tags" -> Filter(tags = List(com.histmersion.api.library.Filter.Tags("sea-peoples"), com.histmersion.api.library.Filter.Tags("steppe"))),
    "author" -> Filter(authors = List(com.histmersion.api.library.Filter.Authors("cline"))),
    "authors" -> Filter(authors = List(com.histmersion.api.library.Filter.Authors("cline"), com.histmersion.api.library.Filter.Authors("anthony"))),
    "author with a percent" -> Filter(authors = List(com.histmersion.api.library.Filter.Authors("100%_x"))),
    "place name" -> Filter(places = List(com.histmersion.api.library.Filter.Places("Anatolia"))),
    "place id" -> Filter(places = List(com.histmersion.api.library.Filter.Places("zq_land"))),
    "places" -> Filter(places = List(com.histmersion.api.library.Filter.Places("zq_land"), com.histmersion.api.library.Filter.Places("Crete"))),
    "topic" -> Filter(topics = List(com.histmersion.api.library.Filter.Topics("bronze age"))),
    "topics" -> Filter(topics = List(com.histmersion.api.library.Filter.Topics("bronze age"), com.histmersion.api.library.Filter.Topics("trade"))),
    "status" -> Filter(status = List(com.histmersion.api.library.Filter.Status("contested"))),
    "statuses" -> Filter(status = List(com.histmersion.api.library.Filter.Status("contested"), com.histmersion.api.library.Filter.Status("superseded"))),
    "years" -> Filter(years = Some(Period(com.histmersion.api.catalogue.Year(1980), com.histmersion.api.catalogue.Year(2005)))),
    "years from" -> Filter(years = Some(Period(com.histmersion.api.catalogue.Year(1990), Filter.Latest))),
    "period" -> Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-1450), com.histmersion.api.catalogue.Year(-1350)))),
    "period open start" -> Filter(period = Some(Period(Filter.Earliest, com.histmersion.api.catalogue.Year(-1500)))),
    "period open end" -> Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-1190), Filter.Latest))),
    "bbox" -> Filter(bbox = Some(Box)),
    "bbox antimeridian" -> Filter(bbox = Some(Antimeridian)),
    "title" -> Filter(title = Some(com.histmersion.api.library.Filter.Title("zqtell"))),
    "goal" -> Filter(goal = Some(com.histmersion.api.research.ReviewGoal.Slug("zq-goal"))),
    "q and tag" -> Filter(q = Some(com.histmersion.api.library.Filter.Q("tin")), tags = List(com.histmersion.api.library.Filter.Tags("zq-trade"))),
    "period and place" -> Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-1500), com.histmersion.api.catalogue.Year(-1200))), places = List(com.histmersion.api.library.Filter.Places("zq_land"))),
    "bbox and period" -> Filter(bbox = Some(Box), period = Some(Period(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1100)))),
    "tag and bbox" -> Filter(tags = List(com.histmersion.api.library.Filter.Tags("zq-trade")), bbox = Some(Antimeridian)),
    "everything" -> Filter(
      q = Some(com.histmersion.api.library.Filter.Q("walls")),
      tags = List(com.histmersion.api.library.Filter.Tags("zq-walls"), com.histmersion.api.library.Filter.Tags("zq-oracle")),
      authors = List(com.histmersion.api.library.Filter.Authors("zqauthor")),
      places = List(com.histmersion.api.library.Filter.Places("zq_tell"), com.histmersion.api.library.Filter.Places("Zqcove")),
      topics = List(com.histmersion.api.library.Filter.Topics("bronze age")),
      status = List(com.histmersion.api.library.Filter.Status("current"), com.histmersion.api.library.Filter.Status("contested")),
      years = Some(Period(com.histmersion.api.catalogue.Year(1950), com.histmersion.api.catalogue.Year(2020))),
      period = Some(Period(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1100))),
      bbox = Some(Box),
      title = Some(com.histmersion.api.library.Filter.Title("walls")),
      goal = Some(com.histmersion.api.research.ReviewGoal.Slug("zq-goal"))
    )
  )

  private val sorts: List[(String, Filter)] =
    Sort.values.toList.flatMap(s =>
      List(
        s"sort ${Sort.label(s)}" -> Filter(sort = s),
        s"sort ${Sort.label(s)} with q" -> Filter(q = Some(com.histmersion.api.library.Filter.Q("walls")), sort = s)
      )
    )

  private val pages: List[(String, Filter)] = List(
    "limit" -> Filter(limit = com.histmersion.api.library.Filter.Limit(2)),
    "limit offset" -> Filter(limit = com.histmersion.api.library.Filter.Limit(2), offset = com.histmersion.api.library.Filter.Offset(2)),
    "limit offset with q" -> Filter(q = Some(com.histmersion.api.library.Filter.Q("tin")), limit = com.histmersion.api.library.Filter.Limit(25), offset = com.histmersion.api.library.Filter.Offset(50)),
    "limit offset with period" -> Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-1700), com.histmersion.api.catalogue.Year(-1100))), limit = com.histmersion.api.library.Filter.Limit(10), offset = com.histmersion.api.library.Filter.Offset(10))
  )

  private val scopes: List[(String, PlaceScope)] = List("no scope" -> PlaceScope.empty, "scoped" -> scoped)

  private def render(s: Sql): String = s"${s.text}\n  params: ${s.params.mkString(", ")}"

  private def statements(f: Filter, scope: PlaceScope): List[(String, Sql)] =
    val text = FilterSql.text(f)
    List(
      "where" -> FilterSql.where(f, scope),
      "sourceKeys" -> FilterSql.sourceKeys(f, scope),
      "articleWhere" -> FilterSql.articleWhere(f, scope),
      "eventWhere" -> FilterSql.eventWhere(f, scope),
      "sourceWhere" -> FilterSql.sourceWhere(f),
      "findingPlaceWhere" -> FilterSql.findingPlaceWhere(f, scope = scope),
      "findingPeriodWhere" -> FilterSql.findingPeriodWhere(f),
      "findingWhere" -> FilterSql.findingWhere(f, scope = scope),
      "orderBySql" -> FilterSql.orderBySql(f),
      "orderBySql cte" -> FilterSql.orderBySql(f, useCte = true),
      "sourcesQuery" -> FilterSql.sourcesQuery(f, com.histmersion.domain.library.search.Search.SnippetWords(30), scope),
      "countQuery" -> FilterSql.countQuery(f, scope),
      "findingsFeedQuery" -> FilterSql.findingsFeedQuery(f, scope),
      "findingsCountQuery" -> FilterSql.findingsCountQuery(f, scope),
      "selectionCountsQuery" -> FilterSql.selectionCountsQuery(f, scope),
      "periodsQuery sources" -> SearchFixtures.periodsQuery("sources", f, scope),
      "periodsQuery findings" -> SearchFixtures.periodsQuery("findings", f, scope),
      "treeRowsQuery sources" -> SearchFixtures.treeRowsQuery("sources", f, scope),
      "treeRowsQuery findings" -> SearchFixtures.treeRowsQuery("findings", f, scope)
    ) ++ text.toList.map(q => "snippetsQuery" -> FilterSql.snippetsQuery(q, com.histmersion.domain.library.search.Search.SnippetWords(30), List(com.histmersion.domain.library.Citekey("zqa1990_a"), com.histmersion.domain.library.Citekey("zqb2001_b"))))

  private def facets(f: Filter, scope: PlaceScope): List[(String, Sql)] =
    Facet.Field.values.toList.flatMap { ff =>
      List(
        s"facetsQuery ${Facet.Field.label(ff)}" -> FilterSql.facetsQuery(ff, None, f, com.histmersion.PageSize(50), scope),
        s"facetsQuery ${Facet.Field.label(ff)} prefix" -> FilterSql.facetsQuery(ff, Some(com.histmersion.domain.library.search.FilterSql.FacetPrefix("Zq-T")), f, com.histmersion.PageSize(5), scope),
        s"facetsQuery ${Facet.Field.label(ff)} blank prefix" -> FilterSql.facetsQuery(ff, Some(com.histmersion.domain.library.search.FilterSql.FacetPrefix("  ")), f, com.histmersion.PageSize(5), scope)
      )
    }

  private def snapshot: String =
    val out = StringBuilder()
    for (name, f) <- criteria ++ sorts ++ pages; (scopeName, scope) <- scopes do
      out ++= s"# $name · $scopeName\n"
      statements(f, scope).foreach((n, s) => out ++= s"## $n\n${render(s)}\n")
    for (name, f) <- criteria.filter((n, _) => Set("empty", "q", "place id", "everything")(n)); (sn, sc) <- scopes do
      out ++= s"# facets · $name · $sn\n"
      facets(f, sc).foreach((n, s) => out ++= s"## $n\n${render(s)}\n")
    out ++= "# fragments\n"
    List(
      "tsQuery" -> Search.tsQuery(com.histmersion.domain.library.search.Search.Query("sea peoples")),
      "ftsPredicate" -> Search.ftsPredicate(com.histmersion.domain.library.search.Search.FtsPredicateQ("sea peoples")),
      "ftsPredicate f" -> Search.ftsPredicate(com.histmersion.domain.library.search.Search.FtsPredicateQ("x"), "f"),
      "rankExpr" -> Search.rankExpr(com.histmersion.domain.library.search.Search.RankExprQ("sea peoples")),
      "rankExprCte" -> Search.rankExprCte(com.histmersion.domain.library.search.Search.RankExprCteQ("sea peoples")),
      "findingsQuery" -> FilterSql.findingsQuery(List(com.histmersion.domain.library.Citekey("zqa1990_a"), com.histmersion.domain.library.Citekey("zqb2001_b")))
    ).foreach((n, s) => out ++= s"## $n\n${render(s)}\n")
    out ++= s"## orderBy\n${Sort.values.toList.map(s => s"${Sort.label(s)}: ${FilterSql.orderBy(s)}").mkString("\n")}\n"
    out ++= s"## facetValues\n${Facet.Field.values.toList.map(f => s"${Facet.Field.label(f)}: ${FilterSql.facetValues(f)}").mkString("\n")}\n"
    out.result()

  test("the search's SQL, as recorded"):
    val actual = snapshot
    if update then
      Files.createDirectories(file.getParent)
      val _ = Files.writeString(file, actual, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertNoDiff(actual, Files.readString(file, UTF_8))
