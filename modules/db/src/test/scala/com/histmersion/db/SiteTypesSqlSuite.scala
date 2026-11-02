package com.histmersion.db

import com.histmersion.domain.catalogue.places.SiteTypesSql

import com.histmersion.config.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.api.catalogue.SiteTypes

/** `SiteTypesSql.expr` — the map tiles' site type in SQL — answers what [[SiteTypes.of]] answers, for every entry of
  * its tables and the cases between them (HISTMERSION_TEST_DB_URL; skipped otherwise).
  */
class SiteTypesSqlSuite extends com.histmersion.db.DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val cfg = AppConfig.load()
    testPool(cfg.db.copy(url = testDb.get))

  private def sqlType(
      kind: String,
      stored: Option[String],
      ft: Option[String],
      fcode: Option[String],
      cls: Option[String],
      p31: Option[io.circe.Json]
  ): Option[String] =
    val props = io.circe.Json
      .fromFields(
        ft.map("feature_types" -> io.circe.Json.fromString(_)).toList ++ fcode.map(
          "fcode" -> io.circe.Json.fromString(_)
        ) ++
          cls.map("class" -> io.circe.Json.fromString(_)) ++ p31.map("p31" -> _)
      )
      .noSpaces
    val q = Sql.of(
      s"SELECT ${SiteTypesSql.expr("a")} FROM (SELECT ?::text AS kind, nullif(?, '')::text AS site_type," +
        " ?::jsonb AS properties) a",
      Param.S(kind),
      Param.S(stored.getOrElse("")),
      Param.S(props)
    )
    connect(ds)(q.frag.query[Option[String]].run().head)

  private def same(
      kind: String,
      stored: Option[String] = None,
      ft: Option[String] = None,
      fcode: Option[String] = None,
      cls: Option[String] = None,
      p31: List[String] = Nil
  )(using munit.Location): Unit =
    val json = Option.when(p31.nonEmpty)(io.circe.Json.arr(p31.map(io.circe.Json.fromString)*))
    assertEquals(
      sqlType(kind, stored, ft, fcode, cls, json),
      SiteTypes.of("x", kind, stored, ft, fcode, cls, SiteTypes.p31Of(json.map(_.noSpaces))),
      s"$kind $stored $ft $fcode $cls $p31"
    )

  test("every Pleiades feature type, alone and with a variant suffix"):
    SiteTypes.pleiadesTypes.keys.foreach { k => same("site", ft = Some(k)); same("site", ft = Some(s"$k-2")) }

  test("several Pleiades types: the most specific wins, spaces and case do not matter"):
    same("site", ft = Some("settlement,temple-2"))
    same("site", ft = Some(" Fort , settlement , tomb"))
    same("site", ft = Some("unknown,province"))
    same("site", ft = Some("settlement-modern"))
    same("site", ft = Some(""))

  test("every GeoNames code, any populated place, and the Wikidata classes"):
    SiteTypes.geonamesCodes.keys.foreach(c => same("site", fcode = Some(c)))
    List("P.PPL", "P.PPLA", "s.ruin", "S.HTL").foreach(c => same("site", fcode = Some(c)))
    SiteTypes.wikidataClasses.keys.foreach(c => same("site", cls = Some(c)))
    same("site", cls = Some("Q1"))

  test("a Wikidata item's own P31 classes: each alone, several (the most specific wins), before the class"):
    SiteTypes.wikidataClasses.keys.foreach(c => same("site", p31 = List(c)))
    same("site", p31 = List("Q16970", "Q2031836"))
    same("site", p31 = List("Q12518", "Q839954"), cls = Some("Q839954"))
    same("site", p31 = List("Q1", "Q2"), cls = Some("Q486972"))
    same("site", p31 = List("Q23413"), fcode = Some("S.TMB"))
    same("polity", p31 = List("Q23413"))
    // a p31 that is no array counts as none
    assertEquals(
      sqlType("site", None, None, None, Some("Q486972"), Some(io.circe.Json.fromString("Q23413"))),
      Some("settlement")
    )

  test("the stored type first, then Pleiades, GeoNames, Wikidata; only a site or a feature has one"):
    same("site", stored = Some("palace"), ft = Some("settlement"))
    same("site", stored = Some("nonsense"), ft = Some("settlement"))
    same("site", ft = Some("tomb"), fcode = Some("S.CSTL"))
    same("site", fcode = Some("S.CSTL"), cls = Some("Q44539"))
    same("feature", ft = Some("cave"))
    same("polity", ft = Some("settlement"))
    same("site")
