package com.histmersion.db

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Real land, for drawing polities on it rather than over the sea (V025). `land` holds Natural Earth land + minor
  * islands cut into small pieces; `places.geom_clipped` is each polity ∩ land, which a trigger keeps current when a
  * polity changes and [[reclip]] recomputes when the land changes.
  */
object LandRepo:

  /** Vertices per stored piece: small pieces make the index narrow a clip to a handful of rows. */
  private val PieceVertices = 256

  /** Joins ids into one bind parameter (ids never contain it). */
  private val Sep = "\u001f"

  /** Replace the land with `geometries` (GeoJSON geometry objects). Returns how many pieces were stored. One
    * transaction: the map never sees half a coastline.
    */
  def replace(ds: DataSource, geometries: List[String]): Int =
    val _ = transact(ds):
      val _ = sql"TRUNCATE land".update.run()
      val _ = batchUpdate(geometries) { g =>
        sql"""INSERT INTO land (geom)
              SELECT ST_Subdivide(ST_CollectionExtract(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON($g), 4326)), 3),
                                  $PieceVertices)""".update
      }
      sql"SELECT count(*)::int FROM land".query[Int].run().head
    // a freshly loaded `land` has no statistics yet; clipping runs right after, so give the planner
    // what it needs to use the index rather than guess
    val _ = connect(ds)(sql"ANALYZE land".update.run())
    pieces(ds)

  /** Polities re-clipped per transaction by [[reclip]]. */
  private val ReclipBatch = 100

  /** Recompute every polity's land-clipped shape; returns how many are now clipped (the rest touch no land, or no land
    * is loaded, and are drawn as published).
    *
    * In small batches, in id order, each its own transaction: one statement over every polity held row locks on all ~13
    * 000 of them for the ~12 minutes a full re-clip takes, so an import running meanwhile stalled — or deadlocked
    * against it, taking rows in another order (seen as a flaky test when two suites ran together).
    */
  def reclip(ds: DataSource): Int =
    val ids =
      connect(ds)(sql"SELECT id FROM places WHERE kind = 'polity' AND geom IS NOT NULL ORDER BY id".query[String].run())
    ids.grouped(ReclipBatch).foreach { batch =>
      val list = batch.mkString(Sep)
      val _ = transact(ds)(sql"""UPDATE places SET geom_clipped = clip_to_land(geom)
              WHERE id = ANY(string_to_array($list, $Sep))""".update.run())
    }
    connect(ds)(sql"SELECT count(*)::int FROM places WHERE geom_clipped IS NOT NULL".query[Int].run().head)

  def pieces(ds: DataSource): Int =
    connect(ds)(sql"SELECT count(*)::int FROM land".query[Int].run().head)
