package com.histmersion.server.basemap

import com.histmersion.core.*

import java.nio.file.{Files, Path}
import java.security.MessageDigest

/** Measuring tools for docs/research-terrain-basemap.md (and how the browser fixture's vector extract
  * was made), with the fetch's own code — never run by the tests:
  *
  *  - `extract <build|latest> <bbox> <maxZoom> <out.pmtiles> [drop|keep-all]` — a Protomaps extract,
  *    through the network, with the default drop list or unfiltered;
  *  - `sizes <keep-all.pmtiles>` — per zoom, what an unfiltered extract stores against the default drop
  *    list and the pre-2026-10-19 keep list (distinct contents, gzip as stored).
  *
  * `sbt "server/Test/runMain com.histmersion.server.basemap.BasemapExtractTool extract latest 22.5,35,28.5,40.5 9 /tmp/v.pmtiles"` */
object BasemapExtractTool:

  /** The keep list the extract used before the drop list (earth, water, natural land cover). */
  val OldKeep: Map[String, Mvt.Rule] = Map(
    "earth" -> Mvt.Rule(),
    "water" -> Mvt.Rule(drop = Set("canal", "ditch", "drain", "dock", "swimming_pool", "fountain", "basin")),
    "landcover" -> Mvt.Rule(keep = Set("forest", "grassland", "scrub", "barren", "glacier")))

  def main(args: Array[String]): Unit = args.toList match
    case "extract" :: build :: bbox :: z :: out :: rest => extract(build, bbox, z.toInt, Path.of(out), rest.headOption.contains("keep-all"))
    case "sizes" :: file :: Nil => sizes(Path.of(file))
    case _ => sys.error("extract <build|latest> <bbox> <maxZoom> <out> [keep-all] | sizes <file>")

  private def extract(build: String, bbox: String, z: Int, out: Path, keepAll: Boolean): Unit =
    val cfg  = AppConfig.load().basemap
    val http = JavaRangeHttp(cfg.timeoutSeconds, cfg.retries)
    val key  = if build == "latest" then http.text(cfg.vector.buildsUrl).flatMap(BasemapService.builds).toOption.get.last._1 else build
    val src  = BasemapFetch.Source(cfg.vector.url.replace("{build}", key), key)
    val b    = BBox.parse(bbox).get
    val pace = BasemapFetch.Pace(cfg.requestGapMs.toLong, cfg.maxRequestMb.toLong << 20, cfg.overfetch)
    val note = (s: String) => scribe.info(s)
    val plan = BasemapFetch.plan(http, src, b, z, pace, note).fold(sys.error, identity)
    val work = out.resolveSibling(out.getFileName.toString + ".work")
    BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, if keepAll then "keep-all" else cfg.vector.drop))
    BasemapFetch.download(http, plan, work, pace, note, _ => Right(())).fold(sys.error, identity)
    val drop  = Basemap.parseDrop(cfg.vector.drop).fold(sys.error, identity)
    val rules = if keepAll then Map.empty[String, Mvt.Rule] else BasemapService.mvtRules(drop)
    val meta  = BasemapFetch.localMetadata(plan, if keepAll then Set.empty else drop.filter(_.whole).map(_.layer).toSet,
      cfg.vector.attribution, java.time.Instant.now().toString, BasemapLayer.Vector, Some((keepAll, cfg.vector.drop)))
    val (bytes, tiles) = BasemapFetch.assemble(plan, work, out, t => BasemapService.thin(t, plan.header.tileCompression, rules),
      BasemapFetch.localHeader(plan), meta, note).fold(sys.error, identity)
    BasemapService.deleteTree(work)
    scribe.info(s"$out: $tiles tiles, $bytes bytes, downloaded ${plan.requestBytes} bytes")

  private def sizes(file: Path): Unit =
    val f = PmTilesFile.open(file).fold(sys.error, identity)
    val drop = BasemapService.mvtRules(Basemap.parseDrop(AppConfig.load().basemap.vector.drop).fold(sys.error, identity))
    val h = f.header
    val b = BBox(h.minLon, h.minLat, h.maxLon, h.maxLat)
    // per zoom: (all, default drop, old keep) bytes of distinct contents, and the layers' share
    val seen = Array.fill(3)(collection.mutable.HashSet.empty[String])
    def digest(a: Array[Byte]) = MessageDigest.getInstance("SHA-1").digest(a).map(x => f"$x%02x").mkString
    val perLayer = collection.mutable.TreeMap.empty[String, Long]
    for z <- h.minZoom to h.maxZoom do
      val sums = Array(0L, 0L, 0L)
      val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, z)
      for x <- x0 to x1; y <- y0 to y1 do
        f.tile(z, x, y).toOption.flatten.foreach { stored =>
          val raw = PmTiles.gunzip(stored).fold(sys.error, identity)
          if z == h.maxZoom then
            Mvt.layerNames(raw).toOption.get.foreach { l =>
              val one = Mvt.filter(raw, Map(l -> Mvt.Rule())).toOption.get
              perLayer(l) = perLayer.getOrElse(l, 0L) + PmTiles.gzip(one).length
            }
          List(Some(stored), Mvt.filter(raw, drop, others = true).toOption.filter(_.nonEmpty).map(PmTiles.gzip),
              Mvt.filter(raw, OldKeep).toOption.filter(_.nonEmpty).map(PmTiles.gzip)).zipWithIndex.foreach {
            case (Some(t), i) => if seen(i).add(digest(t)) then sums(i) += t.length
            case _ => ()
          }
        }
      scribe.info(f"z$z%2d  keep-all ${sums(0) / 1e6}%8.2f MB   default drop ${sums(1) / 1e6}%8.2f MB   old keep list ${sums(2) / 1e6}%8.2f MB")
    scribe.info(s"z${h.maxZoom} by layer (gzip, each alone): " + perLayer.map((l, n) => f"$l ${n / 1e6}%.1f MB").mkString(", "))
    f.close()
