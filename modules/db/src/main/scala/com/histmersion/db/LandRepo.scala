package com.histmersion.db

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Real land, for drawing polities on it rather than over the sea (V025). `land` holds Natural Earth
  * land + minor islands cut into small pieces; `places.geom_clipped` is each polity ∩ land, which a
  * trigger keeps current when a polity changes and [[reclip]] recomputes when the land changes. */
object LandRepo:

  /** Vertices per stored piece: small pieces make the index narrow a clip to a handful of rows. */
  private val PieceVertices = 256

  /** Replace the land with `geometries` (GeoJSON geometry objects). Returns how many pieces were
    * stored. One transaction: the map never sees half a coastline. */
  def replace(ds: DataSource, geometries: List[String]): Int =
    val _ = transact(ds):
      val _ = sql"TRUNCATE land".update.run()
      val _ = batchUpdate(geometries) { g =>
        sql"""INSERT INTO land (geom)
              SELECT ST_Subdivide(ST_CollectionExtract(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON($g), 4326)), 3),
                                  ${PieceVertices})""".update
      }
      sql"SELECT count(*)::int FROM land".query[Int].run().head
    // a freshly loaded `land` has no statistics yet; clipping runs right after, so give the planner
    // what it needs to use the index rather than guess
    val _ = connect(ds)(sql"ANALYZE land".update.run())
    pieces(ds)

  /** Recompute every polity's land-clipped shape; returns how many were clipped (the rest touch no
    * land, or no land is loaded, and are drawn as published). */
  def reclip(ds: DataSource): Int =
    transact(ds):
      val _ = sql"""UPDATE places SET geom_clipped = clip_to_land(geom)
                    WHERE kind = 'polity' AND geom IS NOT NULL""".update.run()
      sql"SELECT count(*)::int FROM places WHERE geom_clipped IS NOT NULL".query[Int].run().head

  def pieces(ds: DataSource): Int =
    connect(ds)(sql"SELECT count(*)::int FROM land".query[Int].run().head)
