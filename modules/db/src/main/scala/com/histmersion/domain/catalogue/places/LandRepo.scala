package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given

import com.augustnagro.magnum.*

/** Real land, for drawing polities on it rather than over the sea (V025). `land` holds Natural Earth land + minor
  * islands cut into small pieces; `places.geom_clipped` is each polity ∩ land, which a trigger keeps current when a
  * polity changes and [[reclip]] recomputes when the land changes.
  */
private[places] object LandRepo:

  /** Vertices per stored piece: small pieces make the index narrow a clip to a handful of rows. */
  private val PieceVertices = 256

  /** Joins ids into one bind parameter (ids never contain it). */
  private val Sep = "\u001f"

  /** Replace the land with `geometries` (GeoJSON geometry objects). Returns how many pieces were stored. One
    * transaction: the map never sees half a coastline.
    */
  def replace(geometries: List[String])(using DbCon): Int =
    val _ = sql"TRUNCATE land".update.run()
    val _ = batchUpdate(geometries) { g =>
      sql"""INSERT INTO land (geom)
            SELECT ST_Subdivide(ST_CollectionExtract(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON($g), 4326)), 3),
                                $PieceVertices)""".update
    }
    sql"SELECT count(*)::int FROM land".query[Int].run().head

  /** Statistics for a freshly loaded `land`: clipping runs right after, so the planner uses the index rather than
    * guess.
    */
  def analyze()(using DbCon): Unit =
    val _ = sql"ANALYZE land".update.run()

  /** Polities re-clipped per transaction. */
  val ReclipBatch = 100

  /** The polities with a shape, in id order: every polity's land-clipped shape is recomputed from them in batches of
    * [[ReclipBatch]], each its own transaction (the rest touch no land, or no land is loaded, and are drawn as
    * published).
    *
    * In small batches, in id order, each its own transaction: one statement over every polity held row locks on all ~13
    * 000 of them for the ~12 minutes a full re-clip takes, so an import running meanwhile stalled — or deadlocked
    * against it, taking rows in another order (seen as a flaky test when two suites ran together).
    */
  def polities()(using DbCon): List[String] =
    sql"SELECT id FROM places WHERE kind = 'polity' AND geom IS NOT NULL ORDER BY id".query[String].run().toList

  /** One batch of [[polities]] clipped again. */
  def reclip(batch: List[String])(using DbCon): Unit =
    val list = batch.mkString(Sep)
    val _ = sql"""UPDATE places SET geom_clipped = clip_to_land(geom)
            WHERE id = ANY(string_to_array($list, $Sep))""".update.run()

  def clipped()(using DbCon): Int =
    sql"SELECT count(*)::int FROM places WHERE geom_clipped IS NOT NULL".query[Int].run().head

  def pieces()(using DbCon): Int =
    sql"SELECT count(*)::int FROM land".query[Int].run().head
