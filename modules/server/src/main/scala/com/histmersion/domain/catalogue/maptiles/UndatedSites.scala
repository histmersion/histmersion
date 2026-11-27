package com.histmersion
package domain.catalogue.maptiles

import com.histmersion.util.database.{Transactor, WritePermit}

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.annotation.tailrec
import com.histmersion.util.database.Unused
import com.histmersion.lint.{tolerated, LintRule}

/** The sites with no known date in a map tile: counted by the squares of its grid, or listed one by one. */
trait UndatedSites:
  /** The undated sites of tile `c` counted by `grid`'s squares, of `siteType` when one is asked for. */
  def counted(c: TileCoord, grid: TileGrid, siteType: Option[TileRepo.SiteType]): List[GridCell]

  /** Every undated site of tile `c`, each alone (marked undated), but those of `except`. */
  def items(c: TileCoord, siteType: Option[TileRepo.SiteType], except: List[TileRepo.SiteId]): List[TileCell]

object UndatedSites:
  /** Over the database: a tile up to `keptZoom` reads the counts kept ahead of time while they answer for every write
    * to an undated site; when they do not, it counts from the sites and a rebuild of the kept counts is started
    * (`background`, one at a time: a worker's unit, whose permit the rebuild writes under).
    */

  def apply(
      db: Transactor,
      keptZoom: () => UndatedBuild.MaxZoom,
      background: (() => WritePermit ?=> Unit) => Unit
  ): UndatedSites =
    Implementation(db, keptZoom, background)

  final private class Implementation(
      db: Transactor,
      keptZoom: () => UndatedBuild.MaxZoom,
      background: (() => WritePermit ?=> Unit) => Unit
  ) extends UndatedSites:
    private val rebuilding = AtomicBoolean(false)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def counted(c: TileCoord, grid: TileGrid, siteType: Option[TileRepo.SiteType]): List[GridCell] =
      val perSide = perSideOf(grid)
      val wanted = keptZoom()
      val fresh = c.z.value <= wanted.value && keptAnswer(perSide, wanted)
      db.readToCompletion:
        if fresh then UndatedSiteRepo.kept(c, perSide, siteType) else UndatedSiteRepo.counted(c, grid, siteType)

    def items(c: TileCoord, siteType: Option[TileRepo.SiteType], except: List[TileRepo.SiteId]): List[TileCell] =
      db.readToCompletion(UndatedSiteRepo.items(c, siteType, except))

    /** Do the kept counts answer for every write so far? When not, their rebuild is started. */
    private def keptAnswer(perSide: UndatedBuild.PerSide, wanted: UndatedBuild.MaxZoom): Boolean =
      val (now, ok) = db.read:
        val v = UndatedSiteRepo.version()
        (v, UndatedSiteRepo.built().exists(_.answers(v, perSide, wanted)))
      if !ok then rebuild(now, perSide, wanted)
      ok

    private def rebuild(
        version: UndatedBuild.Version,
        perSide: UndatedBuild.PerSide,
        maxZoom: UndatedBuild.MaxZoom
    ): Unit =
      if rebuilding.compareAndSet(false, true) then
        background { () =>
          try db.write(Unused(UndatedSiteRepo.rebuild(version, perSide, maxZoom), Unused.Why.Idempotent))
          catch case e: Exception => scribe.warn(s"counting the undated sites for the map failed: ${e.getMessage}")
          finally rebuilding.set(false)
        }

  /** The squares a tile side of `grid`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def perSideOf(grid: TileGrid): UndatedBuild.PerSide =
    UndatedBuild.PerSide((TileCoord.Extent.value / grid.cellUnits.value.max(1)).max(1))

  /** The same answers over sites held in memory (tests). */
  final class InMemory(sites: AtomicReference[List[UndatedSite]]) extends UndatedSites:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def inTile(c: TileCoord, siteType: Option[TileRepo.SiteType]): List[(UndatedSite, Double, Double)] =
      val wanted = siteType.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
      sites.get.flatMap { s =>
        val (px, py) = c.unitsOf(s.lat, s.lon)
        Option.when(
          px >= 0 && px < TileCoord.Extent.value && py >= 0 && py < TileCoord.Extent.value &&
            wanted.forall(t => s.siteType.exists(_.value == t))
        )((s, px, py))
      }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def counted(c: TileCoord, grid: TileGrid, siteType: Option[TileRepo.SiteType]): List[GridCell] =
      val perSide = perSideOf(grid)
      val cu = grid.cellUnits.value.max(1)
      inTile(c, siteType)
        .groupBy((_, px, py) =>
          (
            c.x.value * perSide.value + math.floor(px / cu).toLong,
            c.y.value * perSide.value + math.floor(py / cu).toLong
          )
        )
        .toList
        .sortBy(_._1)
        .map { case ((gx, gy), in) => square(gx, gy, in) }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def square(gx: Long, gy: Long, in: List[(UndatedSite, Double, Double)]): GridCell =
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
            w.min(site.lon.value),
            s.min(site.lat.value),
            e.max(site.lon.value),
            n.max(site.lat.value),
            if one.isEmpty || site.id.value < one then site.id.value else one
          )
        case Nil =>
          GridCell(
            GridCell.Gx(gx),
            GridCell.Gy(gy),
            GridCell.N(in.size),
            GridCell.Dup(0),
            GridCell.SumX(sx),
            GridCell.SumY(sy),
            GridCell.DupX(0),
            GridCell.DupY(0),
            None,
            None,
            GridCell.West(w),
            GridCell.South(s),
            GridCell.East(e),
            GridCell.North(n),
            GridCell.One(one)
          )
      loop(in, 0.0, 0.0, Double.MaxValue, Double.MaxValue, -Double.MaxValue, -Double.MaxValue, "")

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def items(c: TileCoord, siteType: Option[TileRepo.SiteType], except: List[TileRepo.SiteId]): List[TileCell] =
      inTile(c, siteType).filterNot((s, _, _) => except.exists(_.value == s.id.value)).map { (s, px, py) =>
        TileCell(
          TileCell.Key(s.id.value),
          TileCell.X(math.floor(px).toInt),
          TileCell.Y(math.floor(py).toInt),
          TileCell.N(1),
          None,
          None,
          TileCell.West(s.lon.value),
          TileCell.South(s.lat.value),
          TileCell.East(s.lon.value),
          TileCell.North(s.lat.value),
          undated = TileCell.Undated(1)
        )
      }
