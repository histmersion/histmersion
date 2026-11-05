package com.histmersion.domain.catalogue.maptiles

import com.histmersion.util.database.Transactor

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.annotation.tailrec

/** The sites with no known date in a map tile: counted by the squares of its grid, or listed one by one. */
trait UndatedSites:
  /** The undated sites of tile `c` counted by `grid`'s squares, of `siteType` when one is asked for. */
  def counted(c: TileCoord, grid: TileGrid, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSites.CountedSiteType]): List[GridCell]

  /** Every undated site of tile `c`, each alone (marked undated), but those of `except`. */
  def items(c: TileCoord, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSites.ItemsSiteType], except: List[com.histmersion.domain.catalogue.maptiles.UndatedSites.ItemsExcept]): List[TileCell]

object UndatedSites:
  /** Over the database: a tile up to `keptZoom` reads the counts kept ahead of time while they answer for every write
    * to an undated site; when they do not, it counts from the sites and a rebuild of the kept counts is started
    * (`background`, one at a time).
    */
  type CountedSiteType = CountedSiteType.T
  object CountedSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemsSiteType = ItemsSiteType.T
  object ItemsSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemsExcept = ItemsExcept.T
  object ItemsExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyKeptZoomResult = ApplyKeptZoomResult.T
  object ApplyKeptZoomResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerSideOfResult = PerSideOfResult.T
  object PerSideOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor, keptZoom: () => com.histmersion.domain.catalogue.maptiles.UndatedSites.ApplyKeptZoomResult, background: (() => Unit) => Unit): UndatedSites =
    Implementation(db, keptZoom, background)

  final private class Implementation(db: Transactor, keptZoom: () => Int, background: (() => Unit) => Unit)
      extends UndatedSites:
    private val rebuilding = AtomicBoolean(false)

    def counted(c: TileCoord, grid: TileGrid, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSites.Implementation.CountedSiteType]): List[GridCell] =
      val perSide = perSideOf(grid)
      val wanted = keptZoom()
      val fresh = c.z <= wanted && keptAnswer(perSide, wanted)
      db.read:
        if fresh then UndatedSiteRepo.kept(c, perSide, siteType) else UndatedSiteRepo.counted(c, grid, siteType)

    def items(c: TileCoord, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSites.Implementation.ItemsSiteType], except: List[com.histmersion.domain.catalogue.maptiles.UndatedSites.Implementation.ItemsExcept]): List[TileCell] =
      db.read(UndatedSiteRepo.items(c, siteType, except))

    /** Do the kept counts answer for every write so far? When not, their rebuild is started. */
    private def keptAnswer(perSide: com.histmersion.domain.catalogue.maptiles.UndatedSites.Implementation.KeptAnswerPerSide, wanted: com.histmersion.domain.catalogue.maptiles.UndatedSites.Implementation.KeptAnswerWanted): com.histmersion.domain.catalogue.maptiles.UndatedSites.Implementation.KeptAnswerResult =
      val (now, ok) = db.read:
        val v = UndatedSiteRepo.version()
        (v, UndatedSiteRepo.built().exists(_.answers(v, perSide, wanted)))
      if !ok then rebuild(now, perSide, wanted)
      ok

    private def rebuild(version: com.histmersion.domain.catalogue.maptiles.UndatedSites.Implementation.RebuildVersion, perSide: com.histmersion.domain.catalogue.maptiles.UndatedSites.Implementation.RebuildPerSide, maxZoom: com.histmersion.domain.catalogue.maptiles.UndatedSites.Implementation.RebuildMaxZoom): Unit =
      if rebuilding.compareAndSet(false, true) then
        background { () =>
          try
            val _ = db.transact(UndatedSiteRepo.rebuild(version, perSide, maxZoom))
          catch case e: Exception => scribe.warn(s"counting the undated sites for the map failed: ${e.getMessage}")
          finally rebuilding.set(false)
        }

  object Implementation:
    type CountedSiteType = CountedSiteType.T
    object CountedSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemsSiteType = ItemsSiteType.T
    object ItemsSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemsExcept = ItemsExcept.T
    object ItemsExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeptAnswerResult = KeptAnswerResult.T
    object KeptAnswerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeptAnswerPerSide = KeptAnswerPerSide.T
    object KeptAnswerPerSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeptAnswerWanted = KeptAnswerWanted.T
    object KeptAnswerWanted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RebuildVersion = RebuildVersion.T
    object RebuildVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RebuildPerSide = RebuildPerSide.T
    object RebuildPerSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RebuildMaxZoom = RebuildMaxZoom.T
    object RebuildMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The squares a tile side of `grid`. */
  def perSideOf(grid: TileGrid): com.histmersion.domain.catalogue.maptiles.UndatedSites.PerSideOfResult = (TileCoord.Extent / grid.cellUnits.max(1)).max(1)

  /** The same answers over sites held in memory (tests). */
  final class InMemory(sites: AtomicReference[List[UndatedSite]]) extends UndatedSites:
    private def inTile(c: TileCoord, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSites.InMemory.InTileSiteType]): List[(UndatedSite, com.histmersion.domain.catalogue.maptiles.UndatedSites.InMemory.InTileResultPart2, com.histmersion.domain.catalogue.maptiles.UndatedSites.InMemory.InTileResultPart3)] =
      val wanted = siteType.map(_.trim.toLowerCase).filter(_.nonEmpty)
      sites.get.flatMap { s =>
        val (px, py) = c.unitsOf(s.lat, s.lon)
        Option.when(
          px >= 0 && px < TileCoord.Extent && py >= 0 && py < TileCoord.Extent &&
            wanted.forall(t => s.siteType.contains(t))
        )((s, px, py))
      }

    def counted(c: TileCoord, grid: TileGrid, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSites.InMemory.CountedSiteType]): List[GridCell] =
      val perSide = perSideOf(grid)
      val cu = grid.cellUnits.max(1)
      inTile(c, siteType)
        .groupBy((_, px, py) =>
          (c.x * perSide + math.floor(px / cu).toLong, c.y * perSide + math.floor(py / cu).toLong)
        )
        .toList
        .sortBy(_._1)
        .map { case ((gx, gy), in) => square(gx, gy, in) }

    private def square(gx: com.histmersion.domain.catalogue.maptiles.UndatedSites.InMemory.SquareGx, gy: com.histmersion.domain.catalogue.maptiles.UndatedSites.InMemory.SquareGy, in: List[(UndatedSite, com.histmersion.domain.catalogue.maptiles.UndatedSites.InMemory.SquareInPart2, com.histmersion.domain.catalogue.maptiles.UndatedSites.InMemory.SquareInPart3)]): GridCell =
      @tailrec def loop(
          rest: List[(UndatedSite, Double, Double)],
          sx: Double,
          sy: Double,
          w: Double,
          s: Double,
          e: Double,
          n: Double,
          one: String
      ): GridCell = rest match
        case (site, px, py) :: tail =>
          loop(
            tail,
            sx + px,
            sy + py,
            w.min(site.lon),
            s.min(site.lat),
            e.max(site.lon),
            n.max(site.lat),
            if one.isEmpty || site.id < one then site.id else one
          )
        case Nil => GridCell(gx, gy, in.size, 0, sx, sy, 0, 0, None, None, w, s, e, n, one)
      loop(in, 0, 0, Double.MaxValue, Double.MaxValue, -Double.MaxValue, -Double.MaxValue, "")

    def items(c: TileCoord, siteType: Option[com.histmersion.domain.catalogue.maptiles.UndatedSites.InMemory.ItemsSiteType], except: List[com.histmersion.domain.catalogue.maptiles.UndatedSites.InMemory.ItemsExcept]): List[TileCell] =
      inTile(c, siteType).filterNot((s, _, _) => except.contains(s.id)).map { (s, px, py) =>
        TileCell(
          s.id,
          math.floor(px).toInt,
          math.floor(py).toInt,
          1,
          None,
          None,
          s.lon,
          s.lat,
          s.lon,
          s.lat,
          undated = 1
        )
      }

  object InMemory:
    type InTileResultPart2 = InTileResultPart2.T
    object InTileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InTileResultPart3 = InTileResultPart3.T
    object InTileResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InTileSiteType = InTileSiteType.T
    object InTileSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountedSiteType = CountedSiteType.T
    object CountedSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SquareGx = SquareGx.T
    object SquareGx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SquareGy = SquareGy.T
    object SquareGy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SquareInPart2 = SquareInPart2.T
    object SquareInPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SquareInPart3 = SquareInPart3.T
    object SquareInPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemsSiteType = ItemsSiteType.T
    object ItemsSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemsExcept = ItemsExcept.T
    object ItemsExcept extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
