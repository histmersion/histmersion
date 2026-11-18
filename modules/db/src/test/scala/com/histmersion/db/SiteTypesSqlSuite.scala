package com.histmersion
package db
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.places.SiteTypesSql

import com.histmersion.config.*

import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.{Place, SiteTypes}
import com.histmersion.api.catalogue.CatalogRecord.Wikidata
import com.histmersion.api.catalogue.SiteTypes.{Geonames, Pleiades, SiteType}
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig

/** `SiteTypesSql.expr` — the map tiles' site type in SQL — answers what [[SiteTypes.of]] answers, for every entry of
  * its tables and the cases between them (HISTMERSION_TEST_DB_URL; skipped otherwise).
  */
class SiteTypesSqlSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val cfg = AppConfig.load()
    testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))

  private val site = Place.Kind.Wire("site")
  private val polity = Place.Kind.Wire("polity")
  private val feature = Place.Kind.Wire("feature")

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
      sql"SELECT ${SqlLiteral(SiteTypesSql.expr("a"))} FROM (SELECT $kind::text AS kind, nullif(${stored.getOrElse("")}, '')::text AS site_type, $props::jsonb AS properties) a"
    connect(ds)(q.query[Option[String]].run().head)

  private def same(
      kind: Place.Kind.Wire,
      stored: Option[SiteType.Id] = None,
      ft: Option[Pleiades.FeatureType] = None,
      fcode: Option[Geonames.Fcode] = None,
      cls: Option[Wikidata.Class] = None,
      p31: List[Wikidata.Class] = Nil
  )(using munit.Location): Unit =
    val json = Option.when(p31.nonEmpty)(Json.arr(p31.map(c => Json.fromString(c.value))*))
    assertEquals(
      sqlType(kind.value, stored.map(_.value), ft.map(_.value), fcode.map(_.value), cls.map(_.value), json),
      SiteTypes.of(
        Place.Id("x"),
        kind,
        stored,
        ft,
        fcode,
        cls,
        SiteTypes.p31Of(json.map(j => Wikidata.P31Json(j.noSpaces)))
      ),
      s"$kind $stored $ft $fcode $cls $p31"
    )

  test("every Pleiades feature type, alone and with a variant suffix"):
    SiteTypes.pleiadesTypes.keys.foreach { k =>
      same(site, ft = Some(k)); same(site, ft = Some(Pleiades.FeatureType(s"$k-2")))
    }

  test("several Pleiades types: the most specific wins, spaces and case do not matter"):
    same(site, ft = Some(Pleiades.FeatureType("settlement,temple-2")))
    same(site, ft = Some(Pleiades.FeatureType(" Fort , settlement , tomb")))
    same(site, ft = Some(Pleiades.FeatureType("unknown,province")))
    same(site, ft = Some(Pleiades.FeatureType("settlement-modern")))
    same(site, ft = Some(Pleiades.FeatureType("")))

  test("every GeoNames code, any populated place, and the Wikidata classes"):
    SiteTypes.geonamesCodes.keys.foreach(c => same(site, fcode = Some(c)))
    List("P.PPL", "P.PPLA", "s.ruin", "S.HTL").foreach(c => same(site, fcode = Some(Geonames.Fcode(c))))
    SiteTypes.wikidataClasses.keys.foreach(c => same(site, cls = Some(c)))
    same(site, cls = Some(q("Q1")))

  test("a Wikidata item's own P31 classes: each alone, several (the most specific wins), before the class"):
    SiteTypes.wikidataClasses.keys.foreach(c => same(site, p31 = List(c)))
    same(site, p31 = List(q("Q16970"), q("Q2031836")))
    same(site, p31 = List(q("Q12518"), q("Q839954")), cls = Some(q("Q839954")))
    same(site, p31 = List(q("Q1"), q("Q2")), cls = Some(q("Q486972")))
    same(site, p31 = List(q("Q23413")), fcode = Some(Geonames.Fcode("S.TMB")))
    same(polity, p31 = List(q("Q23413")))
    // a p31 that is no array counts as none
    assertEquals(
      sqlType("site", None, None, None, Some("Q486972"), Some(Json.fromString("Q23413"))),
      Some("settlement")
    )

  test("the stored type first, then Pleiades, GeoNames, Wikidata; only a site or a feature has one"):
    same(site, stored = Some(SiteType.Id("palace")), ft = Some(Pleiades.FeatureType("settlement")))
    same(site, stored = Some(SiteType.Id("nonsense")), ft = Some(Pleiades.FeatureType("settlement")))
    same(site, ft = Some(Pleiades.FeatureType("tomb")), fcode = Some(Geonames.Fcode("S.CSTL")))
    same(site, fcode = Some(Geonames.Fcode("S.CSTL")), cls = Some(q("Q44539")))
    same(feature, ft = Some(Pleiades.FeatureType("cave")))
    same(polity, ft = Some(Pleiades.FeatureType("settlement")))
    same(site)
