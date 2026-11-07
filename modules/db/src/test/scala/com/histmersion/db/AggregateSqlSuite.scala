package com.histmersion.db

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

/** `AggregateRepo.periodsQuery` is a pure `Filter → SQL` value like the rest of [[FilterSql]], so the histogram's
  * predicate is asserted here WITHOUT a database (the DB behaviour lives in `AggregateRepoSuite`).
  */
class AggregateSqlSuite extends munit.FunSuite:

  private val window = Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-2000), com.histmersion.api.catalogue.Year(-1000))))

  test("unit=findings: one row per finding, source-level filter joined in"):
    val s = SearchFixtures.periodsQuery("findings", window.modify(_.q).setTo(Some("collapse")).modify(_.places).setTo(List("Anatolia")))
    assert(s.text.startsWith("SELECT lower(f.period), upper(f.period) - 1"), s.text)
    assert(s.text.contains("FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE"), s.text)
    assert(s.text.contains("s.tsv @@ websearch_to_tsquery"), s.text) // source-level (FTS)
    assert(s.text.contains("f.period && int8range(?::bigint, ?::bigint, '[]')"), s.text)
    assert(s.text.contains("f.region_name ILIKE ?"), s.text) // finding-level
    assertEquals(s.params, List(S(com.histmersion.util.database.Param.S.V("collapse")), S(com.histmersion.util.database.Param.S.V("collapse")), L(com.histmersion.util.database.Param.L.V(-2000)), L(com.histmersion.util.database.Param.L.V(-1000)), S(com.histmersion.util.database.Param.S.V("%Anatolia%"))))

  test("unit=sources: one row per source, bucketed by its own period or its findings' envelope"):
    val s = SearchFixtures.periodsQuery("sources", window)
    assert(
      s.text.startsWith("SELECT COALESCE(lower(s.period), fp.min_start), COALESCE(upper(s.period) - 1, fp.max_end)"),
      s.text
    )
    assert(s.text.contains("LEFT JOIN LATERAL"), s.text)
    assert(s.text.contains("count(*) FILTER (WHERE f.period && int8range(?::bigint, ?::bigint, '[]'))"), s.text)
    // no place/tag/bbox criterion → a source with no findings at all is still eligible
    assert(!s.text.contains("fp.matched > 0"), s.text)
    assert(s.text.endsWith("AND (s.period && int8range(?::bigint, ?::bigint, '[]') OR fp.in_window > 0)"), s.text)
    assertEquals(s.params, List(L(com.histmersion.util.database.Param.L.V(-2000)), L(com.histmersion.util.database.Param.L.V(-1000)), L(com.histmersion.util.database.Param.L.V(-2000)), L(com.histmersion.util.database.Param.L.V(-1000))))

  test("unit=sources: a finding-level criterion requires at least one matching finding"):
    List(
      window.modify(_.places).setTo(List("Anatolia")),
      window.modify(_.tags).setTo(List("steppe")),
      window.modify(_.bbox).setTo(Some(BBox(com.histmersion.api.catalogue.Longitude(1), com.histmersion.api.catalogue.Latitude(2), com.histmersion.api.catalogue.Longitude(3), com.histmersion.api.catalogue.Latitude(4))))
    )
      .foreach(f => assert(SearchFixtures.periodsQuery("sources", f).text.contains("fp.matched > 0"), f.toString))

  test("unit=sources: the tag criterion is evaluated per finding, over findings.tags ∪ sources.tags"):
    val s = SearchFixtures.periodsQuery("sources", window.modify(_.tags).setTo(List("steppe")))
    assert(
      s.text.contains("FROM findings f WHERE f.citekey = s.citekey AND (EXISTS (SELECT 1 FROM unnest(f.tags)"),
      s.text
    )
    assert(s.text.contains("OR EXISTS (SELECT 1 FROM unnest(s.tags)"), s.text)
    assertEquals(s.params, List(L(com.histmersion.util.database.Param.L.V(-2000)), L(com.histmersion.util.database.Param.L.V(-1000)), S(com.histmersion.util.database.Param.S.V("steppe")), S(com.histmersion.util.database.Param.S.V("steppe")), L(com.histmersion.util.database.Param.L.V(-2000)), L(com.histmersion.util.database.Param.L.V(-1000))))

  test("an empty filter still yields a valid predicate"):
    assertEquals(
      SearchFixtures.periodsQuery("findings", Filter()),
      Sql(
        "SELECT lower(f.period), upper(f.period) - 1" +
          " FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE TRUE AND TRUE",
        Nil
      )
    )
    val s = SearchFixtures.periodsQuery("sources", Filter())
    assert(s.text.contains("count(*) FILTER (WHERE TRUE)"), s.text)
    assert(s.text.endsWith("WHERE TRUE AND (TRUE OR fp.in_window > 0)"), s.text)
    assertEquals(s.params, Nil)
