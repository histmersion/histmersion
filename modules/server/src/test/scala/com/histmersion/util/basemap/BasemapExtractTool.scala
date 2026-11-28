package com.histmersion
package util.basemap
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.ByteCount
import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.config.*
import com.histmersion.domain.*

import com.histmersion.server.BasemapWiring
import com.histmersion.util.storage.Store

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import com.histmersion.api.basemap.Basemap
import com.histmersion.util.problem.Text.sentences
import com.histmersion.lint.{tolerated, LintRule}

/** Measuring tools for docs/research-terrain-basemap.md (and how the browser fixture's vector extract was made), with
  * the fetch's own code — never run by the tests:
  *
  *   - `extract <build|latest> <bbox> <maxZoom> <out.pmtiles> [drop|keep-all]` — a Protomaps extract, through the
  *     network, with the default drop list or unfiltered;
  *   - `sizes <keep-all.pmtiles>` — per zoom, what an unfiltered extract stores against the default drop list and the
  *     pre-2026-10-19 keep list (distinct contents, gzip as stored).
  *
  * `sbt "server/Test/runMain com.histmersion.util.basemap.basemapExtract extract latest 22.5,35,28.5,40.5 9 /tmp/v.pmtiles"`
  */
@main def basemapExtract(args: String*): Unit = BasemapExtractTool.run(args.toList)

object BasemapExtractTool:

  /** The keep list the extract used before the drop list (earth, water, natural land cover). */
  val OldKeep: Map[Mvt.LayerName, Mvt.Rule] = Map(
    Mvt.LayerName("earth") -> Mvt.Rule(),
    Mvt.LayerName("water") -> Mvt.Rule(drop =
      Set(
        Mvt.Rule.Drop("canal"),
        Mvt.Rule.Drop("ditch"),
        Mvt.Rule.Drop("drain"),
        Mvt.Rule.Drop("dock"),
        Mvt.Rule.Drop("swimming_pool"),
        Mvt.Rule.Drop("fountain"),
        Mvt.Rule.Drop("basin")
      )
    ),
    Mvt.LayerName("landcover") -> Mvt.Rule(keep =
      Set(
        Mvt.Rule.Keep("forest"),
        Mvt.Rule.Keep("grassland"),
        Mvt.Rule.Keep("scrub"),
        Mvt.Rule.Keep("barren"),
        Mvt.Rule.Keep("glacier")
      )
    )
  )

  /** The store of `p`'s directory and `p`'s name in it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def at(p: Path): (Store, Store.Key) =
    val a = p.toAbsolutePath
    (BasemapFixtures.store(a.getParent), Store.Key.applyUnsafe(a.getFileName.toString))

  /** The work store of an extract written to `out`: `<out>.work` beside it. */
  private def workOf(out: Path): (Store, Store.Key) = at(out.resolveSibling(out.getFileName.toString + ".work"))

  /** The failure of a step, as the tool's own error. */
  private def orDie[A](r: Either[Problem, A]): A = r.fold(p => sys.error(p.sentence), identity)

  /** The politeness the configuration asks for. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def paceOf(cfg: BasemapConfig): BasemapFetch.Pace = BasemapFetch.Pace(
    BasemapFetch.Pace.GapMs(cfg.requestGapMs.value.toLong),
    ByteCount.applyUnsafe(cfg.maxRequestMb.value.toLong << 20),
    BasemapFetch.Pace.Overfetch(cfg.overfetch.value)
  )

  /** The region an archive's header covers. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def boxOf(h: PmTiles.Header): BBox =
    BBox(Longitude(h.minLon.value), Latitude(h.minLat.value), Longitude(h.maxLon.value), Latitude(h.maxLat.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def run(args: List[String]): Unit = args match
    case "extract" :: build :: bbox :: z :: out :: rest =>
      extract(
        BasemapVectorConfig.Build(build),
        BBox.Text(bbox),
        BasemapFetch.Plan.MaxZoom(z.toInt),
        Path.of(out),
        rest.headOption.contains("keep-all")
      )
    case "sizes" :: file :: Nil                     => sizes(Path.of(file))
    case "kinds" :: file :: z :: Nil                => kinds(Path.of(file), Basemap.Zoom(z.toInt))
    case "relief" :: bbox :: z0 :: z1 :: out :: Nil =>
      relief(
        BBox.Text(bbox),
        BasemapFetch.Plan.MinZoom(z0.toInt),
        BasemapFetch.Plan.MaxZoom(z1.toInt),
        Some(Path.of(out))
      )
    case "relief-size" :: bbox :: z0 :: z1 :: Nil =>
      relief(BBox.Text(bbox), BasemapFetch.Plan.MinZoom(z0.toInt), BasemapFetch.Plan.MaxZoom(z1.toInt), None)
    case "inspect" :: file :: bbox :: z :: Nil =>
      inspect(Path.of(file), BBox.parse(BBox.Text(bbox)).get, Basemap.Zoom(z.toInt))
    case "subset" :: in :: bbox :: z :: out :: Nil =>
      subset(Path.of(in), BBox.parse(BBox.Text(bbox)).get, PmTiles.Zoom(z.toInt), Path.of(out))
    case _ => sys.error("extract <build|latest> <bbox> <maxZoom> <out> [keep-all] | sizes <file>")

  /** Every layer's `kind`s at zoom `z` of an archive, counted over its tiles (what a style can select). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def kinds(file: Path, z: Basemap.Zoom): Unit =
    val f = orDie(BasemapFixtures.open(file))
    val h = f.header
    val ((x0, x1), (y0, y1)) = Basemap.tileRange(boxOf(h), z)
    val counts = scala.collection.mutable.Map.empty[(String, String), Int].withDefaultValue(0)
    for
      x <- x0.value to x1.value
      y <- y0.value to y1.value
      b <- f.tile(PmTiles.Zoom(z.value), PmTiles.TileColumn(x), PmTiles.TileRow(y)).toOption.flatten
    do
      val raw = PmTiles.gunzip(PmTiles.CompressedBytes(b.value)).getOrElse(PmTiles.PlainBytes(b.value))
      Mvt
        .layerNames(raw)
        .getOrElse(Nil)
        .foreach(l => Mvt.kinds(raw, l).getOrElse(Nil).foreach(k => counts((l.value, k.value)) += 1))
    counts.toList.sortBy(_._1).foreach { case ((l, k), n) => println(s"$l\t$k\t$n") }

  /** The relief of a region from zoom `z0` to `z1` (a focus region, as `fetch --focus` stores it): its download
    * measured from the directories alone, or written to `out`.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def relief(
      bbox: BBox.Text,
      z0: BasemapFetch.Plan.MinZoom,
      z1: BasemapFetch.Plan.MaxZoom,
      out: Option[Path]
  ): Unit =
    val cfg = AppConfig.load().basemap
    val http = BasemapWiring.http(cfg)(using java.time.Clock.systemUTC())
    val src =
      BasemapFetch.Source(RangeHttp.Url(cfg.terrain.url.value), BasemapFetch.Source.Label(cfg.terrain.url.value))
    val pace = paceOf(cfg)
    val note = (n: BasemapFetch.Note) => scribe.info(n.value)
    val plan =
      orDie(BasemapFetch.plan(http, src, BBox.parse(bbox).get, z1, pace, note, minZoom = Some(z0)))
    println(
      s"RELIEF $bbox z$z0-$z1: ${plan.tiles.size} tiles, ${plan.requestBytes} bytes in ${plan.requests.size} requests, contents ${plan.contentBytes}"
    )
    out.foreach { o =>
      val (base, workKey) = workOf(o)
      val work = base.sub(workKey)
      BasemapFetch.prepare(work, BasemapFetch.stateOf(plan, BasemapFetch.Note("relief")))
      orDie(BasemapFetch.download(http, plan, work, pace, note, _ => Right(())))
      val meta = BasemapFetch.localMetadata(
        plan,
        Set.empty,
        BasemapFetch.Attribution(cfg.terrain.attribution.value),
        BasemapFetch.FetchedAt(java.time.Instant.now().toString),
        Basemap.Layer.Terrain
      )
      val (outStore, outKey) = at(o)
      val (bytes, tiles) = orDie(
        BasemapFetch
          .assemble(plan, work, outStore, outKey, b => Right(Some(b)), BasemapFetch.localHeader(plan), meta, note)
      )
      val _ = base.deleteTree(workKey)
      println(s"RELIEF $o: $tiles tiles, $bytes bytes")
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def extract(
      build: BasemapVectorConfig.Build,
      bbox: BBox.Text,
      z: BasemapFetch.Plan.MaxZoom,
      out: Path,
      keepAll: Boolean
  ): Unit =
    val cfg = AppConfig.load().basemap
    val http = BasemapWiring.http(cfg)(using java.time.Clock.systemUTC())
    val key: BasemapService.BuildName = if build == BasemapVectorConfig.Build("latest") then
      http
        .text(RangeHttp.Url(cfg.vector.buildsUrl.value))
        .flatMap(t => BasemapService.builds(BasemapService.JsonText(t.value)))
        .toOption
        .get
        .last
        ._1
    else BasemapService.BuildName(build.value)
    val src = BasemapFetch.Source(
      RangeHttp.Url(cfg.vector.url.value.replace("{build}", key.value)),
      BasemapFetch.Source.Label(key.value)
    )
    val b = BBox.parse(bbox).get
    val pace = paceOf(cfg)
    val note = (n: BasemapFetch.Note) => scribe.info(n.value)
    val plan = orDie(BasemapFetch.plan(http, src, b, z, pace, note))
    val (base, workKey) = workOf(out)
    val work = base.sub(workKey)
    BasemapFetch.prepare(
      work,
      BasemapFetch.stateOf(
        plan,
        if keepAll then BasemapFetch.Note("keep-all") else BasemapFetch.Note(cfg.vector.drop.value)
      )
    )
    orDie(BasemapFetch.download(http, plan, work, pace, note, _ => Right(())))
    val drop =
      Basemap
        .parseDrop(Basemap.DropRule.Spec(cfg.vector.drop.value))
        .fold(p => sys.error(p.sentences.mkString("; ")), identity)
    val rules = if keepAll then Map.empty[Mvt.LayerName, Mvt.Rule] else BasemapService.mvtRules(drop)
    val meta = BasemapFetch.localMetadata(
      plan,
      if keepAll then Set.empty else drop.filter(_.whole.whole).map(_.layer).toSet,
      BasemapFetch.Attribution(cfg.vector.attribution.value),
      BasemapFetch.FetchedAt(java.time.Instant.now().toString),
      Basemap.Layer.Vector,
      Some(
        (
          (if keepAll then BasemapFetch.Filtered.Filtered else BasemapFetch.Filtered.Whole),
          BasemapFetch.FilterSpec(cfg.vector.drop.value)
        )
      )
    )
    val (bytes, tiles) = orDie(
      BasemapFetch
        .assemble(
          plan,
          work,
          at(out)._1,
          at(out)._2,
          t => BasemapService.thin(t, plan.header.tileCompression, rules),
          BasemapFetch.localHeader(plan),
          meta,
          note
        )
    )
    val _ = base.deleteTree(workKey)
    scribe.info(s"$out: $tiles tiles, $bytes bytes, downloaded ${plan.requestBytes} bytes")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sizes(file: Path): Unit =
    val f = orDie(BasemapFixtures.open(file))
    val drop =
      BasemapService.mvtRules(
        Basemap
          .parseDrop(Basemap.DropRule.Spec(AppConfig.load().basemap.vector.drop.value))
          .fold(p => sys.error(p.sentences.mkString("; ")), identity)
      )
    val h = f.header
    val b = boxOf(h)
    // per zoom: (all, default drop, old keep) bytes of distinct contents, and the layers' share
    val seen = Array.fill(3)(collection.mutable.HashSet.empty[String])
    def digest(a: PmTiles.CompressedBytes) =
      MessageDigest.getInstance("SHA-1").digest(a.value).map(x => f"$x%02x").mkString
    val perLayer = collection.mutable.TreeMap.empty[String, Long]
    for z <- h.minZoom.value to h.maxZoom.value do
      val sums = Array(0L, 0L, 0L)
      val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, Basemap.Zoom(z))
      for x <- x0.value to x1.value; y <- y0.value to y1.value do
        f.tile(PmTiles.Zoom(z), PmTiles.TileColumn(x), PmTiles.TileRow(y)).toOption.flatten.foreach { stored =>
          val raw = orDie(PmTiles.gunzip(PmTiles.CompressedBytes(stored.value)))
          if z == h.maxZoom.value then
            Mvt.layerNames(raw).toOption.get.foreach { l =>
              val one = Mvt.filter(raw, Map(l -> Mvt.Rule())).toOption.get
              perLayer(l.value) = perLayer.getOrElse(l.value, 0L) + PmTiles.gzip(one).value.length
            }
          List(
            Some(PmTiles.CompressedBytes(stored.value)),
            Mvt.filter(raw, drop, others = Mvt.Others.Others).toOption.filter(_.value.nonEmpty).map(PmTiles.gzip),
            Mvt.filter(raw, OldKeep).toOption.filter(_.value.nonEmpty).map(PmTiles.gzip)
          ).zipWithIndex.foreach {
            case (Some(t), i) => if seen(i).add(digest(t)) then sums(i) += t.value.length
            case _            => ()
          }
        }
      scribe.info(
        f"z$z%2d  keep-all ${sums(0) / 1e6}%8.2f MB   default drop ${sums(1) / 1e6}%8.2f MB   old keep list ${sums(2) / 1e6}%8.2f MB"
      )
    scribe.info(
      s"z${h.maxZoom} by layer (gzip, each alone): " + perLayer.map((l, n) => f"$l ${n / 1e6}%.1f MB").mkString(", ")
    )
    f.close()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def inspect(file: Path, b: BBox, z: Basemap.Zoom): Unit =
    val f = orDie(BasemapFixtures.open(file))
    val kinds = collection.mutable.TreeMap.empty[String, Int]
    val keys = collection.mutable.TreeMap.empty[String, Int]
    val bytes = collection.mutable.TreeMap.empty[String, Long]
    val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, z)
    for x <- x0.value to x1.value; y <- y0.value to y1.value do
      f.tile(PmTiles.Zoom(z.value), PmTiles.TileColumn(x), PmTiles.TileRow(y)).toOption.flatten.foreach { stored =>
        val raw = orDie(PmTiles.gunzip(PmTiles.CompressedBytes(stored.value)))
        Mvt.layerNames(raw).toOption.get.foreach { l =>
          bytes(l.value) = bytes.getOrElse(l.value, 0L) +
            PmTiles.gzip(Mvt.filter(raw, Map(l -> Mvt.Rule())).toOption.get).value.length
        }
        orDie(Mvt.features(raw)).foreach { (l, g, p) =>
          val k =
            s"$l g$g ${p.get(Mvt.PropKey("kind")).fold("-")(_.value)}/${p.get(Mvt.PropKey("kind_detail")).fold("-")(_.value)}"
          kinds(k) = kinds.getOrElse(k, 0) + 1
          p.keys
            .map(_.value)
            .map(k => if k.startsWith("name:") || k.startsWith("pgf:name:") then k.takeWhile(_ != ':') + ":*" else k)
            .toSet
            .foreach(k => keys(s"$l.$k") = keys.getOrElse(s"$l.$k", 0) + 1)
        }
      }
    println(
      s"z${z.value} bytes per layer (gzip, alone): " + bytes.map((l, n) => f"$l ${n / 1e6}%.2f MB").mkString(", ")
    )
    kinds.foreach((k, n) => println(f"$n%8d  $k"))
    keys.foreach((k, n) => println(f"$n%8d  key $k"))
    f.close()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def subset(in: Path, b: BBox, maxZoom: PmTiles.Zoom, out: Path): Unit =
    val f = orDie(BasemapFixtures.open(in))
    val w = BasemapFixtures.writer(out.toAbsolutePath)
    val ids = (f.header.minZoom.value to maxZoom.value.min(f.header.maxZoom.value))
      .flatMap { z =>
        val ((x0, x1), (y0, y1)) = Basemap.tileRange(b, Basemap.Zoom(z))
        for x <- x0.value to x1.value; y <- y0.value to y1.value
        yield PmTiles.tileId(PmTiles.Zoom(z), PmTiles.TileColumn(x), PmTiles.TileRow(y))
      }
      .sortBy[Long](_.value)
    ids.foreach { id =>
      val (z, x, y) = PmTiles.zxy(id)
      f.tile(z, x, y).toOption.flatten.foreach(w.add(id, _))
    }
    val meta = f.metadata.deepMerge(
      Json.obj(
        "histmersion" -> f.metadata.cursor
          .downField("histmersion")
          .focus
          .getOrElse(Json.obj())
          .deepMerge(
            Json.obj(
              "bbox" -> Json.fromString(s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}"),
              "maxZoom" -> Json.fromInt(maxZoom.value)
            )
          )
      )
    )
    val size = w.finish(
      f.header
        .modify(_.maxZoom)
        .setTo(PmTiles.Zoom(maxZoom.value.min(f.header.maxZoom.value)))
        .modify(_.minLon)
        .setTo(PmTiles.Header.MinLon(b.minLon.value))
        .modify(_.minLat)
        .setTo(PmTiles.Header.MinLat(b.minLat.value))
        .modify(_.maxLon)
        .setTo(PmTiles.Header.MaxLon(b.maxLon.value))
        .modify(_.maxLat)
        .setTo(PmTiles.Header.MaxLat(b.maxLat.value))
        .modify(_.centerLon)
        .setTo(PmTiles.Header.CenterLon((b.minLon.value + b.maxLon.value) / 2))
        .modify(_.centerLat)
        .setTo(PmTiles.Header.CenterLat((b.minLat.value + b.maxLat.value) / 2))
        .modify(_.centerZoom)
        .setTo(PmTiles.Zoom(maxZoom.value.min(f.header.maxZoom.value) / 2)),
      meta
    )
    scribe.info(s"$out: ${w.tileCount} tiles, $size bytes")
    f.close()
