package com.histmersion
package db

import com.histmersion.api.catalogue.SiteType
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.places.SiteTypesSql

import com.histmersion.config.*

import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.CatalogRecord.Wikidata
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.lint.{tolerated, LintRule}

/** `SiteTypesSql.expr` — the map tiles' site type in SQL — answers what [[SiteTypes.of]] answers, for every entry of
  * its tables and the cases between them (HISTMERSION_TEST_DB_URL; skipped otherwise).
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SiteTypesSqlSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val cfg = AppConfig.load()
    testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))

  private val site = Place.Kind.Wire("site")
  private val polity = Place.Kind.Wire("polity")
  private val feature = Place.Kind.Wire("feature")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def q(s: String) = Wikidata.Class(s)

  private def sqlType(
      kind: String,
      stored: Option[String],
      ft: Option[String],
      fcode: Option[String],
      cls: Option[String],
      p31: Option[Json]
  ): Option[String] =
    val props = Json
      .fromFields(
        ft.map("feature_types" -> Json.fromString(_)).toList ++ fcode.map(
          "fcode" -> Json.fromString(_)
        ) ++
          cls.map("class" -> Json.fromString(_)) ++ p31.map("p31" -> _)
      )
      .noSpaces
    val q =
      sql"SELECT ${SiteTypesSql.expr(sql"a")} FROM (SELECT $kind::text AS kind, nullif(${stored.getOrElse("")}, '')::text AS site_type, $props::jsonb AS properties) a"
    connect(ds)(q.query[Option[String]].run().head)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def same(
      kind: Place.Kind.Wire,
      stored: Option[SiteType.Id] = None,
      ft: Option[SiteType.Pleiades.FeatureType] = None,
      fcode: Option[SiteType.Geonames.Fcode] = None,
      cls: Option[Wikidata.Class] = None,
      p31: List[Wikidata.Class] = Nil
  )(using munit.Location): Unit =
    val json = Option.when(p31.nonEmpty)(Json.arr(p31.map(c => Json.fromString(c.value))*))
    assertEquals(
      sqlType(kind.value, stored.map(_.value), ft.map(_.value), fcode.map(_.value), cls.map(_.value), json),
      SiteType.of(
        Place.Id("x"),
        kind,
        stored,
        ft,
        fcode,
        cls,
        SiteType.p31Of(json.map(j => Wikidata.P31Json(j.noSpaces)))
      ),
      s"$kind $stored $ft $fcode $cls $p31"
    )

  test("every Pleiades feature type, alone and with a variant suffix"):
    SiteType.pleiadesTypes.keys.foreach { k =>
      same(site, ft = Some(k)); same(site, ft = Some(SiteType.Pleiades.FeatureType(s"$k-2")))
    }

  test("several Pleiades types: the most specific wins, spaces and case do not matter"):
    same(site, ft = Some(SiteType.Pleiades.FeatureType("settlement,temple-2")))
    same(site, ft = Some(SiteType.Pleiades.FeatureType(" Fort , settlement , tomb")))
    same(site, ft = Some(SiteType.Pleiades.FeatureType("unknown,province")))
    same(site, ft = Some(SiteType.Pleiades.FeatureType("settlement-modern")))
    same(site, ft = Some(SiteType.Pleiades.FeatureType("")))

  test("every GeoNames code, any populated place, and the Wikidata classes"):
    SiteType.geonamesCodes.keys.foreach(c => same(site, fcode = Some(c)))
    List("P.PPL", "P.PPLA", "s.ruin", "S.HTL").foreach(c => same(site, fcode = Some(SiteType.Geonames.Fcode(c))))
    SiteType.wikidataClasses.keys.foreach(c => same(site, cls = Some(c)))
    same(site, cls = Some(q("Q1")))

  test("a Wikidata item's own P31 classes: each alone, several (the most specific wins), before the class"):
    SiteType.wikidataClasses.keys.foreach(c => same(site, p31 = List(c)))
    same(site, p31 = List(q("Q16970"), q("Q2031836")))
    same(site, p31 = List(q("Q12518"), q("Q839954")), cls = Some(q("Q839954")))
    same(site, p31 = List(q("Q1"), q("Q2")), cls = Some(q("Q486972")))
    same(site, p31 = List(q("Q23413")), fcode = Some(SiteType.Geonames.Fcode("S.TMB")))
    same(polity, p31 = List(q("Q23413")))
    // a p31 that is no array counts as none
    assertEquals(
      sqlType("site", None, None, None, Some("Q486972"), Some(Json.fromString("Q23413"))),
      Some("settlement")
    )

  test("the stored type first, then Pleiades, GeoNames, Wikidata; only a site or a feature has one"):
    same(site, stored = Some(SiteType.Id("palace")), ft = Some(SiteType.Pleiades.FeatureType("settlement")))
    same(site, stored = Some(SiteType.Id("nonsense")), ft = Some(SiteType.Pleiades.FeatureType("settlement")))
    same(site, ft = Some(SiteType.Pleiades.FeatureType("tomb")), fcode = Some(SiteType.Geonames.Fcode("S.CSTL")))
    same(site, fcode = Some(SiteType.Geonames.Fcode("S.CSTL")), cls = Some(q("Q44539")))
    same(feature, ft = Some(SiteType.Pleiades.FeatureType("cave")))
    same(polity, ft = Some(SiteType.Pleiades.FeatureType("settlement")))
    same(site)
