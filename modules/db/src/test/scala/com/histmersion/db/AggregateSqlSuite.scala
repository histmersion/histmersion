package com.histmersion
package db

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.search.SearchFixtures

import com.histmersion.domain.library.search.FilterSql

import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Param.*
import com.histmersion.util.database.Sql
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.Filter
import com.histmersion.util.database.given
import com.histmersion.api.catalogue.Year
import com.histmersion.util.database.Param
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude

/** `AggregateRepo.periodsQuery` is a pure `Filter → SQL` value like the rest of [[FilterSql]], so the histogram's
  * predicate is asserted here WITHOUT a database (the DB behaviour lives in `AggregateRepoSuite`).
  */
class AggregateSqlSuite extends munit.FunSuite:

  private val window = Filter(period = Some(Period(Year(-2000), Year(-1000))))

  test("unit=findings: one row per finding, source-level filter joined in"):
    val s = SearchFixtures.periodsQuery(SearchFixtures.AggregateUnit("findings"), window.modify(_.q).setTo(Some(Filter.Q("collapse"))).modify(_.places).setTo(List(Filter.Places("Anatolia"))))
    assert(s.text.value.startsWith("SELECT lower(f.period), upper(f.period) - 1"), s.text)
    assert(s.text.value.contains("FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE"), s.text)
    assert(s.text.value.contains("s.tsv @@ websearch_to_tsquery"), s.text) // source-level (FTS)
    assert(s.text.value.contains("f.period && int8range(?::bigint, ?::bigint, '[]')"), s.text)
    assert(s.text.value.contains("f.region_name ILIKE ?"), s.text) // finding-level
    assertEquals(s.params, List(S(Param.S.V("collapse")), S(Param.S.V("collapse")), L(Param.L.V(-2000)), L(Param.L.V(-1000)), S(Param.S.V("%Anatolia%"))))

  test("unit=sources: one row per source, bucketed by its own period or its findings' envelope"):
    val s = SearchFixtures.periodsQuery(SearchFixtures.AggregateUnit("sources"), window)
    assert(
      s.text.value.startsWith("SELECT COALESCE(lower(s.period), fp.min_start), COALESCE(upper(s.period) - 1, fp.max_end)"),
      s.text
    )
    assert(s.text.value.contains("LEFT JOIN LATERAL"), s.text)
    assert(s.text.value.contains("count(*) FILTER (WHERE f.period && int8range(?::bigint, ?::bigint, '[]'))"), s.text)
    // no place/tag/bbox criterion → a source with no findings at all is still eligible
    assert(!s.text.value.contains("fp.matched > 0"), s.text)
    assert(s.text.value.endsWith("AND (s.period && int8range(?::bigint, ?::bigint, '[]') OR fp.in_window > 0)"), s.text)
    assertEquals(s.params, List(L(Param.L.V(-2000)), L(Param.L.V(-1000)), L(Param.L.V(-2000)), L(Param.L.V(-1000))))

  test("unit=sources: a finding-level criterion requires at least one matching finding"):
    List(
      window.modify(_.places).setTo(List(Filter.Places("Anatolia"))),
      window.modify(_.tags).setTo(List(Filter.Tags("steppe"))),
      window.modify(_.bbox).setTo(Some(BBox(Longitude(1), Latitude(2), Longitude(3), Latitude(4))))
    )
      .foreach(f => assert(SearchFixtures.periodsQuery(SearchFixtures.AggregateUnit("sources"), f).text.value.contains("fp.matched > 0"), f.toString))

  test("unit=sources: the tag criterion is evaluated per finding, over findings.tags ∪ sources.tags"):
    val s = SearchFixtures.periodsQuery(SearchFixtures.AggregateUnit("sources"), window.modify(_.tags).setTo(List(Filter.Tags("steppe"))))
    assert(
      s.text.value.contains("FROM findings f WHERE f.citekey = s.citekey AND (EXISTS (SELECT 1 FROM unnest(f.tags)"),
      s.text
    )
    assert(s.text.value.contains("OR EXISTS (SELECT 1 FROM unnest(s.tags)"), s.text)
    assertEquals(s.params, List(L(Param.L.V(-2000)), L(Param.L.V(-1000)), S(Param.S.V("steppe")), S(Param.S.V("steppe")), L(Param.L.V(-2000)), L(Param.L.V(-1000))))

  test("an empty filter still yields a valid predicate"):
    assertEquals(
      SearchFixtures.periodsQuery(SearchFixtures.AggregateUnit("findings"), Filter()),
      Sql(
        Sql.Text(
          "SELECT lower(f.period), upper(f.period) - 1" +
            " FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE TRUE AND TRUE"
        ),
        Nil
      )
    )
    val s = SearchFixtures.periodsQuery(SearchFixtures.AggregateUnit("sources"), Filter())
    assert(s.text.value.contains("count(*) FILTER (WHERE TRUE)"), s.text)
    assert(s.text.value.endsWith("WHERE TRUE AND (TRUE OR fp.in_window > 0)"), s.text)
    assertEquals(s.params, Nil)
