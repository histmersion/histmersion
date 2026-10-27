package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*

import com.augustnagro.magnum.*

/** `SiteTypesSql.expr` — the map tiles' site type in SQL — answers what [[SiteTypes.of]] answers, for
  * every entry of its tables and the cases between them (HISTMERSION_TEST_DB_URL; skipped otherwise). */
class SiteTypesSqlSuite extends com.histmersion.db.DbSuite:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val cfg = AppConfig.load()
    testPool(cfg.db.copy(url = testDb.get))

  private def sqlType(kind: String, stored: Option[String], ft: Option[String], fcode: Option[String], cls: Option[String]): Option[String] =
    val props = io.circe.Json.fromFields(
      ft.map("feature_types" -> io.circe.Json.fromString(_)).toList ++ fcode.map("fcode" -> io.circe.Json.fromString(_)) ++
        cls.map("class" -> io.circe.Json.fromString(_))).noSpaces
    val q = Sql.of(s"SELECT ${SiteTypesSql.expr("a")} FROM (SELECT ?::text AS kind, nullif(?, '')::text AS site_type," +
      " ?::jsonb AS properties) a", Param.S(kind), Param.S(stored.getOrElse("")), Param.S(props))
    connect(ds)(q.frag.query[Option[String]].run().head)

  private def same(kind: String, stored: Option[String] = None, ft: Option[String] = None, fcode: Option[String] = None,
      cls: Option[String] = None)(using munit.Location): Unit =
    assertEquals(sqlType(kind, stored, ft, fcode, cls), SiteTypes.of("x", kind, stored, ft, fcode, cls),
      s"$kind $stored $ft $fcode $cls")

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

  test("the stored type first, then Pleiades, GeoNames, Wikidata; only a site or a feature has one"):
    same("site", stored = Some("palace"), ft = Some("settlement"))
    same("site", stored = Some("nonsense"), ft = Some("settlement"))
    same("site", ft = Some("tomb"), fcode = Some("S.CSTL"))
    same("site", fcode = Some("S.CSTL"), cls = Some("Q44539"))
    same("feature", ft = Some("cave"))
    same("polity", ft = Some("settlement"))
    same("site")
