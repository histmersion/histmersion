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
import com.histmersion.api.library.{FacetField, Filter, Sort}

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

  private val Box = BBox(30, 36, 40, 42)
  private val Antimeridian = BBox(170, -25, -170, -10)

  private val criteria: List[(String, Filter)] = List(
    "empty" -> Filter(),
    "q" -> Filter(q = Some("collapse")),
    "q phrase" -> Filter(q = Some("\"sea peoples\"")),
    "q exclusion" -> Filter(q = Some("tin -islands")),
    "q or" -> Filter(q = Some("tin or kings")),
    "q blank" -> Filter(q = Some("   ")),
    "q with a percent" -> Filter(q = Some("50% of_it")),
    "tag" -> Filter(tags = List("sea-peoples")),
    "tags" -> Filter(tags = List("sea-peoples", "steppe")),
    "author" -> Filter(authors = List("cline")),
    "authors" -> Filter(authors = List("cline", "anthony")),
    "author with a percent" -> Filter(authors = List("100%_x")),
    "place name" -> Filter(places = List("Anatolia")),
    "place id" -> Filter(places = List("zq_land")),
    "places" -> Filter(places = List("zq_land", "Crete")),
    "topic" -> Filter(topics = List("bronze age")),
    "topics" -> Filter(topics = List("bronze age", "trade")),
    "status" -> Filter(status = List("contested")),
    "statuses" -> Filter(status = List("contested", "superseded")),
    "years" -> Filter(years = Some(Period(1980, 2005))),
    "years from" -> Filter(years = Some(Period(1990, Filter.Latest))),
    "period" -> Filter(period = Some(Period(-1450, -1350))),
    "period open start" -> Filter(period = Some(Period(Filter.Earliest, -1500))),
    "period open end" -> Filter(period = Some(Period(-1190, Filter.Latest))),
    "bbox" -> Filter(bbox = Some(Box)),
    "bbox antimeridian" -> Filter(bbox = Some(Antimeridian)),
    "title" -> Filter(title = Some("zqtell")),
    "goal" -> Filter(goal = Some("zq-goal")),
    "q and tag" -> Filter(q = Some("tin"), tags = List("zq-trade")),
    "period and place" -> Filter(period = Some(Period(-1500, -1200)), places = List("zq_land")),
    "bbox and period" -> Filter(bbox = Some(Box), period = Some(Period(-1600, -1100))),
    "tag and bbox" -> Filter(tags = List("zq-trade"), bbox = Some(Antimeridian)),
    "everything" -> Filter(
      q = Some("walls"),
      tags = List("zq-walls", "zq-oracle"),
      authors = List("zqauthor"),
      places = List("zq_tell", "Zqcove"),
      topics = List("bronze age"),
      status = List("current", "contested"),
      years = Some(Period(1950, 2020)),
      period = Some(Period(-1600, -1100)),
      bbox = Some(Box),
      title = Some("walls"),
      goal = Some("zq-goal")
    )
  )

  private val sorts: List[(String, Filter)] =
    Sort.values.toList.flatMap(s =>
      List(
        s"sort ${Sort.label(s)}" -> Filter(sort = s),
        s"sort ${Sort.label(s)} with q" -> Filter(q = Some("walls"), sort = s)
      )
    )

  private val pages: List[(String, Filter)] = List(
    "limit" -> Filter(limit = 2),
    "limit offset" -> Filter(limit = 2, offset = 2),
    "limit offset with q" -> Filter(q = Some("tin"), limit = 25, offset = 50),
    "limit offset with period" -> Filter(period = Some(Period(-1700, -1100)), limit = 10, offset = 10)
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
      "sourcesQuery" -> FilterSql.sourcesQuery(f, 30, scope),
      "countQuery" -> FilterSql.countQuery(f, scope),
      "findingsFeedQuery" -> FilterSql.findingsFeedQuery(f, scope),
      "findingsCountQuery" -> FilterSql.findingsCountQuery(f, scope),
      "selectionCountsQuery" -> FilterSql.selectionCountsQuery(f, scope),
      "periodsQuery sources" -> SearchFixtures.periodsQuery("sources", f, scope),
      "periodsQuery findings" -> SearchFixtures.periodsQuery("findings", f, scope),
      "treeRowsQuery sources" -> SearchFixtures.treeRowsQuery("sources", f, scope),
      "treeRowsQuery findings" -> SearchFixtures.treeRowsQuery("findings", f, scope)
    ) ++ text.toList.map(q => "snippetsQuery" -> FilterSql.snippetsQuery(q, 30, List("zqa1990_a", "zqb2001_b")))

  private def facets(f: Filter, scope: PlaceScope): List[(String, Sql)] =
    FacetField.values.toList.flatMap { ff =>
      List(
        s"facetsQuery ${FacetField.label(ff)}" -> FilterSql.facetsQuery(ff, None, f, 50, scope),
        s"facetsQuery ${FacetField.label(ff)} prefix" -> FilterSql.facetsQuery(ff, Some("Zq-T"), f, 5, scope),
        s"facetsQuery ${FacetField.label(ff)} blank prefix" -> FilterSql.facetsQuery(ff, Some("  "), f, 5, scope)
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
      "tsQuery" -> Search.tsQuery("sea peoples"),
      "ftsPredicate" -> Search.ftsPredicate("sea peoples"),
      "ftsPredicate f" -> Search.ftsPredicate("x", "f"),
      "rankExpr" -> Search.rankExpr("sea peoples"),
      "rankExprCte" -> Search.rankExprCte("sea peoples"),
      "findingsQuery" -> FilterSql.findingsQuery(List("zqa1990_a", "zqb2001_b"))
    ).foreach((n, s) => out ++= s"## $n\n${render(s)}\n")
    out ++= s"## orderBy\n${Sort.values.toList.map(s => s"${Sort.label(s)}: ${FilterSql.orderBy(s)}").mkString("\n")}\n"
    out ++= s"## facetValues\n${FacetField.values.toList.map(f => s"${FacetField.label(f)}: ${FilterSql.facetValues(f)}").mkString("\n")}\n"
    out.result()

  test("the search's SQL, as recorded"):
    val actual = snapshot
    if update then
      Files.createDirectories(file.getParent)
      val _ = Files.writeString(file, actual, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertNoDiff(actual, Files.readString(file, UTF_8))
