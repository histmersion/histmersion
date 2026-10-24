package com.histmersion.server.basemap

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

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
    case "kinds" :: file :: z :: Nil => kinds(Path.of(file), z.toInt)
    case "relief" :: bbox :: z0 :: z1 :: out :: Nil => relief(bbox, z0.toInt, z1.toInt, Some(Path.of(out)))
    case "relief-size" :: bbox :: z0 :: z1 :: Nil => relief(bbox, z0.toInt, z1.toInt, None)
    case "inspect" :: file :: bbox :: z :: Nil => inspect(Path.of(file), BBox.parse(bbox).get, z.toInt)
    case "subset" :: in :: bbox :: z :: out :: Nil => subset(Path.of(in), BBox.parse(bbox).get, z.toInt, Path.of(out))
    case _ => sys.error("extract <build|latest> <bbox> <maxZoom> <out> [keep-all] | sizes <file>")

  /** Every layer's `kind`s at zoom `z` of an archive, counted over its tiles (what a style can select). */
  private def kinds(file: Path, z: Int): Unit =
    val f = PmTilesFile.open(file).fold(sys.error, identity)
    val h = f.header
    val ((x0, x1), (y0, y1)) = Basemap.tileRange(BBox(h.minLon, h.minLat, h.maxLon, h.maxLat), z)
    val counts = scala.collection.mutable.Map.empty[(String, String), Int].withDefaultValue(0)
    for x <- x0 to x1; y <- y0 to y1; b <- f.tile(z, x, y).toOption.flatten do
      val raw = PmTiles.gunzip(b).getOrElse(b)
      Mvt.layerNames(raw).getOrElse(Nil).foreach(l => Mvt.kinds(raw, l).getOrElse(Nil).foreach(k => counts((l, k)) += 1))
    counts.toList.sortBy(_._1).foreach { case ((l, k), n) => println(s"$l\t$k\t$n") }

  /** The relief of a region from zoom `z0` to `z1` (a focus region, as `fetch --focus` stores it): its
    * download measured from the directories alone, or written to `out`. */
  private def relief(bbox: String, z0: Int, z1: Int, out: Option[Path]): Unit =
    val cfg  = AppConfig.load().basemap
    val http = JavaRangeHttp(cfg.timeoutSeconds, cfg.retries)
    val src  = BasemapFetch.Source(cfg.terrain.url, cfg.terrain.url)
    val pace = BasemapFetch.Pace(cfg.requestGapMs.toLong, cfg.maxRequestMb.toLong << 20, cfg.overfetch)
    val note = (s: String) => scribe.info(s)
    val plan = BasemapFetch.plan(http, src, BBox.parse(bbox).get, z1, pace, note, minZoom = Some(z0)).fold(sys.error, identity)
    println(s"RELIEF $bbox z$z0-$z1: ${plan.tiles.size} tiles, ${plan.requestBytes} bytes in ${plan.requests.size} requests, contents ${plan.contentBytes}")
    out.foreach { o =>
      val work = o.resolveSibling(o.getFileName.toString + ".work")
      BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, "relief"))
      BasemapFetch.download(http, plan, work, pace, note, _ => Right(())).fold(sys.error, identity)
      val meta = BasemapFetch.localMetadata(plan, Set.empty, cfg.terrain.attribution, java.time.Instant.now().toString, BasemapLayer.Terrain)
      val (bytes, tiles) = BasemapFetch.assemble(plan, work, o, b => Right(Some(b)), BasemapFetch.localHeader(plan), meta, note)
        .fold(sys.error, identity)
      BasemapService.deleteTree(work)
      println(s"RELIEF $o: $tiles tiles, $bytes bytes")
    }

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

  private def inspect(file: Path, b: BBox, z: Int): Unit =
    val f = PmTilesFile.open(file).fold(sys.error, identity)
    val kinds = collection.mutable.TreeMap.empty[String, Int]
    val keys  = collection.mutable.TreeMap.empty[String, Int]
    val bytes = collection.mutable.TreeMap.empty[String, Long]
    val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, z)
    for x <- x0 to x1; y <- y0 to y1 do
      f.tile(z, x, y).toOption.flatten.foreach { stored =>
        val raw = PmTiles.gunzip(stored).fold(sys.error, identity)
        Mvt.layerNames(raw).toOption.get.foreach { l =>
          bytes(l) = bytes.getOrElse(l, 0L) + PmTiles.gzip(Mvt.filter(raw, Map(l -> Mvt.Rule())).toOption.get).length
        }
        Mvt.features(raw).fold(sys.error, identity).foreach { (l, g, p) =>
          val k = s"$l g$g ${p.getOrElse("kind", "-")}/${p.getOrElse("kind_detail", "-")}"
          kinds(k) = kinds.getOrElse(k, 0) + 1
          p.keys.map(k => if k.startsWith("name:") || k.startsWith("pgf:name:") then k.takeWhile(_ != ':') + ":*" else k)
            .toSet.foreach(k => keys(s"$l.$k") = keys.getOrElse(s"$l.$k", 0) + 1)
        }
      }
    println(s"z$z bytes per layer (gzip, alone): " + bytes.map((l, n) => f"$l ${n / 1e6}%.2f MB").mkString(", "))
    kinds.foreach((k, n) => println(f"$n%8d  $k"))
    keys.foreach((k, n) => println(f"$n%8d  key $k"))
    f.close()

  private def subset(in: Path, b: BBox, maxZoom: Int, out: Path): Unit =
    val f = PmTilesFile.open(in).fold(sys.error, identity)
    val w = PmTilesWriter(out)
    val ids = (f.header.minZoom to maxZoom.min(f.header.maxZoom)).flatMap { z =>
      val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, z)
      for x <- x0 to x1; y <- y0 to y1 yield PmTiles.tileId(z, x, y)
    }.sorted
    ids.foreach { id =>
      val (z, x, y) = PmTiles.zxy(id)
      f.tile(z, x, y).toOption.flatten.foreach(w.add(id, _))
    }
    val meta = f.metadata.deepMerge(io.circe.Json.obj("histmersion" -> f.metadata.hcursor.downField("histmersion").focus
      .getOrElse(io.circe.Json.obj()).deepMerge(io.circe.Json.obj(
        "bbox" -> io.circe.Json.fromString(s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}"),
        "maxZoom" -> io.circe.Json.fromInt(maxZoom)))))
    val size = w.finish(f.header.copy(maxZoom = maxZoom.min(f.header.maxZoom), minLon = b.minLon, minLat = b.minLat,
      maxLon = b.maxLon, maxLat = b.maxLat, centerLon = (b.minLon + b.maxLon) / 2, centerLat = (b.minLat + b.maxLat) / 2,
      centerZoom = maxZoom.min(f.header.maxZoom) / 2), meta)
    scribe.info(s"$out: ${w.tileCount} tiles, $size bytes")
    f.close()
