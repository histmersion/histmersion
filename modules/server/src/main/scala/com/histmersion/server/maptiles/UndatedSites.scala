package com.histmersion.server.maptiles

import com.histmersion.db.UndatedSiteRepo
import com.histmersion.domain.*
import com.histmersion.domain.maptiles.{GridCell, UndatedBuild}

import com.augustnagro.magnum.{connect, transact}

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.sql.DataSource
import scala.annotation.tailrec

/** One site of no known date, as the map draws it: its id, point and type. */
final case class UndatedSite(id: String, lat: Double, lon: Double, siteType: Option[String])

/** The sites with no known date in a map tile: counted by the squares of its grid, or listed one by one. */
trait UndatedSites:
  /** The undated sites of tile `c` counted by `grid`'s squares, of `siteType` when one is asked for. */
  def counted(c: TileCoord, grid: TileGrid, siteType: Option[String]): List[GridCell]

  /** Every undated site of tile `c`, each alone (marked undated), but those of `except`. */
  def items(c: TileCoord, siteType: Option[String], except: List[String]): List[TileCell]

object UndatedSites:
  /** Over the database: a tile up to `keptZoom` reads the counts kept ahead of time while they answer for
    * every write to an undated site; when they do not, it counts from the sites and a rebuild of the kept
    * counts is started (`background`, one at a time). */
  def apply(ds: DataSource, keptZoom: () => Int, background: (() => Unit) => Unit): UndatedSites =
    Implementation(ds, keptZoom, background)

  private final class Implementation(ds: DataSource, keptZoom: () => Int, background: (() => Unit) => Unit) extends UndatedSites:
    private val rebuilding = AtomicBoolean(false)

    def counted(c: TileCoord, grid: TileGrid, siteType: Option[String]): List[GridCell] =
      val perSide = perSideOf(grid)
      val wanted = keptZoom()
      val fresh = c.z <= wanted && keptAnswer(perSide, wanted)
      connect(ds):
        if fresh then UndatedSiteRepo.kept(c, perSide, siteType) else UndatedSiteRepo.counted(c, grid, siteType)

    def items(c: TileCoord, siteType: Option[String], except: List[String]): List[TileCell] =
      connect(ds)(UndatedSiteRepo.items(c, siteType, except))

    /** Do the kept counts answer for every write so far? When not, their rebuild is started. */
    private def keptAnswer(perSide: Int, wanted: Int): Boolean =
      val (now, ok) = connect(ds):
        val v = UndatedSiteRepo.version()
        (v, UndatedSiteRepo.built().exists(_.answers(v, perSide, wanted)))
      if !ok then rebuild(now, perSide, wanted)
      ok

    private def rebuild(version: Long, perSide: Int, maxZoom: Int): Unit =
      if rebuilding.compareAndSet(false, true) then
        background { () =>
          try
            val _ = transact(ds)(UndatedSiteRepo.rebuild(version, perSide, maxZoom))
          catch case e: Exception => scribe.warn(s"counting the undated sites for the map failed: ${e.getMessage}")
          finally rebuilding.set(false)
        }

  /** The squares a tile side of `grid`. */
  def perSideOf(grid: TileGrid): Int = (TileCoord.Extent / grid.cellUnits.max(1)).max(1)

  /** The same answers over sites held in memory (tests). */
  final class InMemory(sites: AtomicReference[List[UndatedSite]]) extends UndatedSites:
    private def inTile(c: TileCoord, siteType: Option[String]): List[(UndatedSite, Double, Double)] =
      val wanted = siteType.map(_.trim.toLowerCase).filter(_.nonEmpty)
      sites.get.flatMap { s =>
        val (px, py) = c.unitsOf(s.lat, s.lon)
        Option.when(px >= 0 && px < TileCoord.Extent && py >= 0 && py < TileCoord.Extent &&
          wanted.forall(t => s.siteType.contains(t)))((s, px, py))
      }

    def counted(c: TileCoord, grid: TileGrid, siteType: Option[String]): List[GridCell] =
      val perSide = perSideOf(grid)
      val cu = grid.cellUnits.max(1)
      inTile(c, siteType).groupBy((_, px, py) => (c.x * perSide + math.floor(px / cu).toLong, c.y * perSide + math.floor(py / cu).toLong))
        .toList.sortBy(_._1).map { case ((gx, gy), in) => square(gx, gy, in) }

    private def square(gx: Long, gy: Long, in: List[(UndatedSite, Double, Double)]): GridCell =
      @tailrec def loop(rest: List[(UndatedSite, Double, Double)], sx: Double, sy: Double, w: Double, s: Double, e: Double,
          n: Double, one: String): GridCell = rest match
        case (site, px, py) :: tail =>
          loop(tail, sx + px, sy + py, w.min(site.lon), s.min(site.lat), e.max(site.lon), n.max(site.lat),
            if one.isEmpty || site.id < one then site.id else one)
        case Nil => GridCell(gx, gy, in.size, 0, sx, sy, 0, 0, None, None, w, s, e, n, one)
      loop(in, 0, 0, Double.MaxValue, Double.MaxValue, -Double.MaxValue, -Double.MaxValue, "")

    def items(c: TileCoord, siteType: Option[String], except: List[String]): List[TileCell] =
      inTile(c, siteType).filterNot((s, _, _) => except.contains(s.id)).map { (s, px, py) =>
        TileCell(s.id, math.floor(px).toInt, math.floor(py).toInt, 1, None, None, s.lon, s.lat, s.lon, s.lat, undated = 1)
      }
