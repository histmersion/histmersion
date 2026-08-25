package histdb.db

import histdb.core.*
import histdb.db.FilterSql.Param.*
import histdb.db.FilterSql.Sql

/** `AggregateRepo.periodsQuery` is a pure `Filter → SQL` value like the rest of [[FilterSql]], so the
  * histogram's predicate is asserted here WITHOUT a database (the DB behaviour lives in
  * `AggregateRepoSuite`). */
class AggregateSqlSuite extends munit.FunSuite:

  private val window = Filter(period = Some(Period(-2000, -1000)))

  test("unit=findings: one row per finding, source-level filter joined in"):
    val s = AggregateRepo.periodsQuery("findings", window.copy(q = Some("collapse"), places = List("Anatolia")))
    assert(s.text.startsWith("SELECT lower(f.period), upper(f.period) - 1"), s.text)
    assert(s.text.contains("FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE"), s.text)
    assert(s.text.contains("(s.title ILIKE ?"), s.text)                    // source-level
    assert(s.text.contains("f.period && int8range(?::bigint, ?::bigint, '[]')"), s.text)
    assert(s.text.contains("f.region_name ILIKE ?"), s.text)               // finding-level
    assertEquals(s.params,
      List(S("%collapse%"), S("%collapse%"), S("%collapse%"), L(-2000), L(-1000), S("%Anatolia%")))

  test("unit=sources: one row per source, bucketed by its own period or its findings' envelope"):
    val s = AggregateRepo.periodsQuery("sources", window)
    assert(s.text.startsWith(
      "SELECT COALESCE(lower(s.period), fp.min_start), COALESCE(upper(s.period) - 1, fp.max_end)"), s.text)
    assert(s.text.contains("LEFT JOIN LATERAL"), s.text)
    assert(s.text.contains("count(*) FILTER (WHERE f.period && int8range(?::bigint, ?::bigint, '[]'))"), s.text)
    // no place/tag/bbox criterion → a source with no findings at all is still eligible
    assert(!s.text.contains("fp.matched > 0"), s.text)
    assert(s.text.endsWith("AND (s.period && int8range(?::bigint, ?::bigint, '[]') OR fp.in_window > 0)"), s.text)
    assertEquals(s.params, List(L(-2000), L(-1000), L(-2000), L(-1000)))

  test("unit=sources: a finding-level criterion requires at least one matching finding"):
    List(window.copy(places = List("Anatolia")), window.copy(tags = List("steppe")),
      window.copy(bbox = Some(BBox(1, 2, 3, 4))))
      .foreach(f => assert(AggregateRepo.periodsQuery("sources", f).text.contains("fp.matched > 0"), f.toString))

  test("unit=sources: the tag criterion is evaluated per finding, over findings.tags ∪ sources.tags"):
    val s = AggregateRepo.periodsQuery("sources", window.copy(tags = List("steppe")))
    assert(s.text.contains("FROM findings f WHERE f.citekey = s.citekey AND (EXISTS (SELECT 1 FROM unnest(f.tags)"), s.text)
    assert(s.text.contains("OR EXISTS (SELECT 1 FROM unnest(s.tags)"), s.text)
    assertEquals(s.params, List(L(-2000), L(-1000), S("steppe"), S("steppe"), L(-2000), L(-1000)))

  test("an empty filter still yields a valid predicate"):
    assertEquals(AggregateRepo.periodsQuery("findings", Filter()),
      Sql("SELECT lower(f.period), upper(f.period) - 1" +
        " FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE TRUE AND TRUE", Nil))
    val s = AggregateRepo.periodsQuery("sources", Filter())
    assert(s.text.contains("count(*) FILTER (WHERE TRUE)"), s.text)
    assert(s.text.endsWith("WHERE TRUE AND (TRUE OR fp.in_window > 0)"), s.text)
    assertEquals(s.params, Nil)
