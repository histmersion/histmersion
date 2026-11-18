package com.histmersion
package db

import com.histmersion.domain.library.search.SearchFixtures

import com.histmersion.domain.library.search.FilterSql
import com.histmersion.domain.library.search.Search

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.BBox
import com.histmersion.domain.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Filter, Sort}
import com.histmersion.api.library.Facet
import com.histmersion.util.database.given
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Year
import com.histmersion.api.research.ReviewGoal
import com.histmersion.domain.library.Citekey
import com.augustnagro.magnum.Frag

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
    Map(
      PlaceScope.Value("zq_land") -> List(
        PlaceScope.ByValueValue("zq_land"),
        PlaceScope.ByValueValue("zq_tell"),
        PlaceScope.ByValueValue("zq_cove")
      ),
      PlaceScope.Value("zq_tell") -> List(PlaceScope.ByValueValue("zq_tell"))
    ),
    Map(
      PlaceScope.Value("zq_land") -> PlaceScope.LabelsValue("Zqland"),
      PlaceScope.Value("zq_tell") -> PlaceScope.LabelsValue("Zqtell")
    )
  )

  private val Box = BBox(Longitude(30), Latitude(36), Longitude(40), Latitude(42))
  private val Antimeridian = BBox(Longitude(170), Latitude(-25), Longitude(-170), Latitude(-10))

  private val criteria: List[(String, Filter)] = List(
    "empty" -> Filter(),
    "q" -> Filter(q = Some(Filter.Q("collapse"))),
    "q phrase" -> Filter(q = Some(Filter.Q("\"sea peoples\""))),
    "q exclusion" -> Filter(q = Some(Filter.Q("tin -islands"))),
    "q or" -> Filter(q = Some(Filter.Q("tin or kings"))),
    "q blank" -> Filter(q = Some(Filter.Q("   "))),
    "q with a percent" -> Filter(q = Some(Filter.Q("50% of_it"))),
    "tag" -> Filter(tags = List(Filter.Tags("sea-peoples"))),
    "tags" -> Filter(tags = List(Filter.Tags("sea-peoples"), Filter.Tags("steppe"))),
    "author" -> Filter(authors = List(Filter.Authors("cline"))),
    "authors" -> Filter(authors = List(Filter.Authors("cline"), Filter.Authors("anthony"))),
    "author with a percent" -> Filter(authors = List(Filter.Authors("100%_x"))),
    "place name" -> Filter(places = List(Filter.Places("Anatolia"))),
    "place id" -> Filter(places = List(Filter.Places("zq_land"))),
    "places" -> Filter(places = List(Filter.Places("zq_land"), Filter.Places("Crete"))),
    "topic" -> Filter(topics = List(Filter.Topics("bronze age"))),
    "topics" -> Filter(topics = List(Filter.Topics("bronze age"), Filter.Topics("trade"))),
    "status" -> Filter(status = List(Filter.Status("contested"))),
    "statuses" -> Filter(status = List(Filter.Status("contested"), Filter.Status("superseded"))),
    "years" -> Filter(years = Some(Period(Year(1980), Year(2005)))),
    "years from" -> Filter(years = Some(Period(Year(1990), Filter.Latest))),
    "period" -> Filter(period = Some(Period(Year(-1450), Year(-1350)))),
    "period open start" -> Filter(period = Some(Period(Filter.Earliest, Year(-1500)))),
    "period open end" -> Filter(period = Some(Period(Year(-1190), Filter.Latest))),
    "bbox" -> Filter(bbox = Some(Box)),
    "bbox antimeridian" -> Filter(bbox = Some(Antimeridian)),
    "title" -> Filter(title = Some(Filter.Title("zqtell"))),
    "goal" -> Filter(goal = Some(ReviewGoal.Slug("zq-goal"))),
    "q and tag" -> Filter(q = Some(Filter.Q("tin")), tags = List(Filter.Tags("zq-trade"))),
    "period and place" -> Filter(
      period = Some(Period(Year(-1500), Year(-1200))),
      places = List(Filter.Places("zq_land"))
    ),
    "bbox and period" -> Filter(bbox = Some(Box), period = Some(Period(Year(-1600), Year(-1100)))),
    "tag and bbox" -> Filter(tags = List(Filter.Tags("zq-trade")), bbox = Some(Antimeridian)),
    "everything" -> Filter(
      q = Some(Filter.Q("walls")),
      tags = List(Filter.Tags("zq-walls"), Filter.Tags("zq-oracle")),
      authors = List(Filter.Authors("zqauthor")),
      places = List(Filter.Places("zq_tell"), Filter.Places("Zqcove")),
      topics = List(Filter.Topics("bronze age")),
      status = List(Filter.Status("current"), Filter.Status("contested")),
      years = Some(Period(Year(1950), Year(2020))),
      period = Some(Period(Year(-1600), Year(-1100))),
      bbox = Some(Box),
      title = Some(Filter.Title("walls")),
      goal = Some(ReviewGoal.Slug("zq-goal"))
    )
  )

  private val sorts: List[(String, Filter)] =
    Sort.values.toList.flatMap(s =>
      List(
        s"sort ${Sort.label(s)}" -> Filter(sort = s),
        s"sort ${Sort.label(s)} with q" -> Filter(q = Some(Filter.Q("walls")), sort = s)
      )
    )

  private val pages: List[(String, Filter)] = List(
    "limit" -> Filter(limit = Filter.Limit(2)),
    "limit offset" -> Filter(limit = Filter.Limit(2), offset = Filter.Offset(2)),
    "limit offset with q" -> Filter(q = Some(Filter.Q("tin")), limit = Filter.Limit(25), offset = Filter.Offset(50)),
    "limit offset with period" -> Filter(
      period = Some(Period(Year(-1700), Year(-1100))),
      limit = Filter.Limit(10),
      offset = Filter.Offset(10)
    )
  )

  private val scopes: List[(String, PlaceScope)] = List("no scope" -> PlaceScope.empty, "scoped" -> scoped)

  private def render(s: Frag): String = s"${s.sqlString}\n  params: ${s.params.map(param).mkString(", ")}"

  /** A bound value with the JDBC type it is bound as, in the recording's spelling (`S` text, `I` int, `L` bigint, `D`
    * double, `A` a text array): a value that changed type would change the recording.
    */
  private def param(p: Any): String = p.asInstanceOf[Matchable] match
    case s: String   => s"S($s)"
    case i: Int      => s"I($i)"
    case l: Long     => s"L($l)"
    case d: Double   => s"D($d)"
    case xs: List[?] => s"A($xs)"
    case other       => s"?($other)"

  private def statements(f: Filter, scope: PlaceScope): List[(String, Frag)] =
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
      "sourcesQuery" -> FilterSql.sourcesQuery(f, Search.SnippetWords(30), scope),
      "countQuery" -> FilterSql.countQuery(f, scope),
      "findingsFeedQuery" -> FilterSql.findingsFeedQuery(f, scope),
      "findingsCountQuery" -> FilterSql.findingsCountQuery(f, scope),
      "selectionCountsQuery" -> FilterSql.selectionCountsQuery(f, scope),
      "periodsQuery sources" -> SearchFixtures.periodsQuery(SearchFixtures.AggregateUnit("sources"), f, scope),
      "periodsQuery findings" -> SearchFixtures.periodsQuery(SearchFixtures.AggregateUnit("findings"), f, scope),
      "treeRowsQuery sources" -> SearchFixtures.treeRowsQuery(SearchFixtures.AggregateUnit("sources"), f, scope),
      "treeRowsQuery findings" -> SearchFixtures.treeRowsQuery(SearchFixtures.AggregateUnit("findings"), f, scope)
    ) ++ text.toList.map(q =>
      "snippetsQuery" -> FilterSql.snippetsQuery(
        q,
        Search.SnippetWords(30),
        List(Citekey("zqa1990_a"), Citekey("zqb2001_b"))
      )
    )

  private def facets(f: Filter, scope: PlaceScope): List[(String, Frag)] =
    Facet.Field.values.toList.flatMap { ff =>
      List(
        s"facetsQuery ${Facet.Field.label(ff)}" -> FilterSql.facetsQuery(ff, None, f, PageSize(50), scope),
        s"facetsQuery ${Facet.Field.label(ff)} prefix" -> FilterSql
          .facetsQuery(ff, Some(FilterSql.FacetPrefix("Zq-T")), f, PageSize(5), scope),
        s"facetsQuery ${Facet.Field.label(ff)} blank prefix" -> FilterSql.facetsQuery(
          ff,
          Some(FilterSql.FacetPrefix("  ")),
          f,
          PageSize(5),
          scope
        )
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
      "tsQuery" -> Search.tsQuery(Search.Query("sea peoples")),
      "ftsPredicate" -> Search.ftsPredicate(Search.FtsPredicateQ("sea peoples")),
      "ftsPredicate f" -> Search.ftsPredicate(Search.FtsPredicateQ("x"), "f"),
      "rankExpr" -> Search.rankExpr(Search.RankExprQ("sea peoples")),
      "rankExprCte" -> Search.rankExprCte(Search.RankExprCteQ("sea peoples")),
      "findingsQuery" -> FilterSql.findingsQuery(List(Citekey("zqa1990_a"), Citekey("zqb2001_b")))
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
