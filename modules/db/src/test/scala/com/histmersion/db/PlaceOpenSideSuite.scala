package com.histmersion.db

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate

/** A one-ended LPF timespan is stored OPEN (#1403): `[start,)` / `(,end]`, never the one known year — for the place,
  * its names and its relations. Against a real PostGIS (HISTMERSION_TEST_DB_URL).
  */
class PlaceOpenSideSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d); d

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM place_relations WHERE child_id LIKE 'zzpo_%'".update.run()
    val _ = sql"DELETE FROM place_names WHERE place_id LIKE 'zzpo_%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zzpo_%'".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private val doc =
    """{"type":"FeatureCollection","features":[
      | {"@id":"zzpo_parent","type":"Feature","properties":{"title":"Zzpo Parent"},"geometry":null},
      | {"@id":"zzpo_town","type":"Feature","properties":{"title":"Zzpo Town"},
      |  "when":{"timespans":[{"start":{"in":"-300"}}]},
      |  "names":[{"toponym":"Zzpo Old","when":{"timespans":[{"end":{"in":"200"}}]}}],
      |  "relations":[{"relationType":"gvp:broaderPartitive","relationTo":"zzpo_parent",
      |                "when":{"timespans":[{"start":{"in":"-250"}}]}}],
      |  "geometry":{"type":"Point","coordinates":[30.0,40.0]}}]}""".stripMargin

  private type Bounds = (Option[Long], Option[Long], Boolean, Boolean)

  test("a place, a name and a relation with one side open keep it open in the store"):
    val in = Lpf.parse(doc).fold(e => fail(e), identity)
    val _ = PlaceFixtures.upsertPlaces(ds, in)
    def read(q: Frag): Bounds = connect(ds)(q.query[Bounds].run().head)
    assertEquals(
      read(sql"""SELECT lower(period), upper(period) - 1, lower_inf(period), upper_inf(period)
                            FROM places WHERE id = 'zzpo_town'"""),
      (Some(-300L), None, false, true),
      "from 300 BCE on: [-300,)"
    )
    assertEquals(
      read(sql"""SELECT lower(period), upper(period) - 1, lower_inf(period), upper_inf(period)
                            FROM place_names WHERE place_id = 'zzpo_town' AND name = 'Zzpo Old'"""),
      (None, Some(200L), true, false),
      "until 200 CE: (,200]"
    )
    assertEquals(
      read(sql"""SELECT lower(period), upper(period) - 1, lower_inf(period), upper_inf(period)
                            FROM place_relations WHERE child_id = 'zzpo_town'"""),
      (Some(-250L), None, false, true)
    )
    val alive = connect(ds)(
      sql"SELECT count(*)::int FROM places WHERE id = 'zzpo_town' AND period @> 1500::bigint"
        .query[Int]
        .run()
        .head
    )
    assertEquals(alive, 1, "an open end holds at 1500 CE — the old reading kept only the year 300 BCE")
