package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The terrain base map (docs/research-terrain-basemap.md): MODERN terrain only — relief, coast, water, rivers, natural
  * land cover — drawn by the web page from two regional PMTiles extracts the server downloads once (`histmersion
  * basemap fetch`) and serves (`GET /api/basemap/{layer}/{z}/{x}/{y}`). The wire types and the pure tile arithmetic;
  * the server owns the files.
  */
enum BasemapLayer(val wire: String, val title: String):
  /** Coast, water, rivers, land cover and land use, natural points: vector tiles (MVT) without the modern layers
    * (`basemap.vector.drop`: roads, buildings, places, boundaries …).
    */
  case Vector extends BasemapLayer("vector", "coast, water and land cover")

  /** Elevation, terrarium-encoded raster tiles the page shades into relief. */
  case Terrain extends BasemapLayer("terrain", "relief")

  /** The physical names — ranges, deserts, peninsulas, lakes, major rivers, peaks — from Natural Earth, a small vector
    * archive built by the fetch (seas and gulfs come with the vector layer).
    */
  case Labels extends BasemapLayer("labels", "physical names")

  /** The sea floor: terrarium-encoded elevation tiles whose sea is its depth (ETOPO1's bathymetry, NOAA, read tile by
    * tile; or the GEBCO grid's, `basemap.bathymetry.source`) — the relief's own tiles have the sea flat at 0.
    */
  case Bathymetry extends BasemapLayer("bathymetry", "sea floor")

  def file: String = s"$wire.pmtiles"

object BasemapLayer:
  def parse(s: String): Option[BasemapLayer] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** `all` (or blank) is every layer. */
  def parseMany(s: String): Either[String, List[BasemapLayer]] =
    s.trim.toLowerCase(java.util.Locale.ROOT) match
      case "" | "all" => Right(values.toList)
      case other      =>
        parse(other).map(List(_)).toRight(s"unknown base-map layer '$s' (vector, terrain, labels, bathymetry or all)")

/** One downloaded layer, as the server finds it on disk. */
final case class BasemapLayerInfo(
    layer: String,
    present: Boolean,
    file: String,
    bytes: Long = 0,
    minZoom: Option[Int] = None,
    maxZoom: Option[Int] = None,
    bbox: Option[String] = None,
    tiles: Option[Long] = None,
    tileType: Option[String] = None,
    /** Where it came from (the build file or the URL) and when it was fetched (ISO instant). */
    source: Option[String] = None,
    fetchedAt: Option[String] = None,
    /** Changes with every fetch: the tile URLs carry it, so a browser never shows a stale cached tile. */
    version: Option[String] = None,
    /** The credit line (HTML) the page shows with the layer. */
    attribution: String = "",
    /** What the extract kept ([[Basemap.keptLine]]): the vector layer's filter. */
    kept: Option[String] = None,
    /** The vector layer's data layers the download left out whole ([[Basemap.droppedLayers]]): a style drawing one of
      * them lacks it.
      */
    dropped: List[String] = Nil,
    /** Why the file is there but cannot be read. */
    error: Option[String] = None,
    /** The relief only: the deeper zooms read from the provider as a page needs them, and their cache. */
    onDemand: Option[BasemapOnDemand] = None,
    /** The relief only: the focus regions kept to a deeper zoom (`fetch --layer terrain --focus <name>`). */
    focus: List[BasemapFocus] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** The deepest zoom the page can ask this layer for: the extract's, a focus region's, or the on-demand relief's.
    */
  def deepest: Option[Int] =
    (maxZoom.toList ++ focus.map(_.maxZoom) ++ onDemand.filter(_.enabled).map(_.maxZoom).toList).maxOption

object BasemapLayerInfo:
  given Schema[BasemapLayerInfo] = summon[KindlingsSchema[BasemapLayerInfo]].schema

/** The relief deeper than the extract, fetched tile by tile from the provider when a page shows an area
  * (`basemap.terrain.on-demand`): to `maxZoom`, cached in `dir` up to `maxBytes` (least recently used dropped first).
  * `offlineUntil`: after a failed request, none is made before then (ISO instant).
  */
final case class BasemapOnDemand(
    enabled: Boolean,
    maxZoom: Int,
    dir: String,
    tiles: Long,
    bytes: Long,
    maxBytes: Long,
    offlineUntil: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object BasemapOnDemand:
  given Schema[BasemapOnDemand] = summon[KindlingsSchema[BasemapOnDemand]].schema

/** A focus region's relief archive: its name, region, zooms and size. */
final case class BasemapFocus(
    name: String,
    file: String,
    bytes: Long,
    bbox: String,
    minZoom: Int,
    maxZoom: Int,
    tiles: Long,
    fetchedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object BasemapFocus:
  given Schema[BasemapFocus] = summon[KindlingsSchema[BasemapFocus]].schema

/** `GET /api/basemap`: is the terrain base map there, what of it, and how to get it. */
final case class BasemapStatus(
    /** The vector layer is there (the relief is optional: without it the map is flat). */
    available: Boolean,
    dir: String,
    /** The region the next fetch downloads (`basemap.bbox`). */
    bbox: String,
    layers: List[BasemapLayerInfo],
    /** The command that downloads it, for a page or a message to show. */
    fetchCommand: String,
    /** Free space where the files go, in bytes. */
    freeBytes: Long = 0,
    /** A fetch job running now. */
    running: Option[String] = None,
    /** What a fetch would add (the physical names to an extract made before they existed; the layers the street maps
      * need).
      */
    hints: List[String] = Nil,
    /** Each named style of the base-map menu and the vector data layers it draws that the stored extract lacks (none:
      * it can be drawn whole).
      */
    styles: List[BasemapStyleState] = Nil,
    /** How far the page zooms in on Readable and Physical (`basemap.map-max-zoom`). */
    mapMaxZoom: Int = 16,
    /** The named regions a fetch knows (`basemap.terrain.focus-regions`), for a page to offer. */
    regions: List[BasemapRegion] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** The style `id`'s missing layers: empty when it can be drawn whole or is not known. */
  def missing(id: String): List[String] = styles.find(_.id == id).fold(Nil)(_.missing)

object BasemapStatus:
  given Schema[BasemapStatus] = summon[KindlingsSchema[BasemapStatus]].schema

/** A named region: its name and `minLon,minLat,maxLon,maxLat`. */
final case class BasemapRegion(name: String, bbox: String) derives KindlingsCodecAsObject, KindlingsSchema

object BasemapRegion:
  given Schema[BasemapRegion] = summon[KindlingsSchema[BasemapRegion]].schema

/** One named style against the stored extract: the vector data layers it draws that the download left out
  * (`basemap.vector.drop`).
  */
final case class BasemapStyleState(id: String, name: String, missing: List[String] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object BasemapStyleState:
  given Schema[BasemapStyleState] = summon[KindlingsSchema[BasemapStyleState]].schema

/** `DELETE /api/basemap?layer=`: what was removed. */
final case class BasemapRemoved(removed: List[String], bytes: Long) derives KindlingsCodecAsObject, KindlingsSchema

object BasemapRemoved:
  given Schema[BasemapRemoved] = summon[KindlingsSchema[BasemapRemoved]].schema

/** The fetch's options: which layers, how deep, where; a dry run downloads only the directories and reports what the
  * extract would take. Empty values fall back to the configuration.
  */
final case class BasemapFetchQuery(
    layer: String = "all",
    maxZoom: Option[Int] = None,
    terrainMaxZoom: Option[Int] = None,
    bbox: Option[String] = None,
    dryRun: Boolean = false,
    keepAll: Boolean = false,
    focus: Option[String] = None
)

/** `DELETE /api/basemap`: the layers' files (`all`, a layer, or `none`), one focus region (`all`: every one) and the
  * on-demand relief cache.
  */
final case class BasemapRemoveQuery(layer: String = "all", focus: Option[String] = None, cache: Boolean = false)

object Basemap:

  /** The command a person runs to download the base map. */
  val FetchCommand = "histmersion basemap fetch"

  /** The deepest zoom a fetch accepts: deeper extracts are tens of gigabytes for this region. */
  val MaxZoomCap = 14

  /** Web Mercator's latitude limit. */
  val MaxLat = 85.0511287798066

  /** A focus region's name: lower-case letters, digits and `-`. Pure. */
  def validFocusName(s: String): Either[String, String] =
    val n = s.trim.toLowerCase(java.util.Locale.ROOT)
    Either.cond(n.matches("[a-z0-9][a-z0-9-]{0,40}"), n, s"'$s' is not a focus region name (letters, digits and -)")

  /** One of `basemap.terrain.focus-regions`: "name minLon,minLat,maxLon,maxLat". Pure. */
  def focusRegion(spec: String): Either[String, (String, BBox)] =
    spec.trim.split("\\s+", 2).toList match
      case n :: b :: Nil => for name <- validFocusName(n); box <- validBBox(b.replace(" ", "")) yield (name, box)
      case _             => Left(s"'$spec' is not \"name minLon,minLat,maxLon,maxLat\"")

  /** A name for a focus region given only by its box: `bbox-22.5_37.4_23.2_37.9`. Pure. */
  def focusNameOf(b: BBox): String =
    def f(d: Double) = java.math
      .BigDecimal(d)
      .setScale(2, java.math.RoundingMode.HALF_UP)
      .stripTrailingZeros
      .toPlainString
      .replace("-", "m")
      .replace(".", "p")
    s"bbox-${f(b.minLon)}-${f(b.minLat)}-${f(b.maxLon)}-${f(b.maxLat)}"

  /** The tile column of longitude `lon` at zoom `z`. Pure. */
  def tileX(lon: Double, z: Int): Long =
    val n = 1L << z
    math.floor((lon + 180.0) / 360.0 * n).toLong.max(0L).min(n - 1)

  /** The tile row of latitude `lat` at zoom `z` (row 0 at the north). Pure. */
  def tileY(lat: Double, z: Int): Long =
    val n = 1L << z
    val r = math.toRadians(lat.max(-MaxLat).min(MaxLat))
    val y = (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.Pi) / 2.0 * n
    math.floor(y).toLong.max(0L).min(n - 1)

  /** The tiles of zoom `z` that touch `b`: columns and rows, inclusive. Pure. */
  def tileRange(b: BBox, z: Int): ((Long, Long), (Long, Long)) =
    ((tileX(b.minLon, z), tileX(b.maxLon, z)), (tileY(b.maxLat, z), tileY(b.minLat, z)))

  /** How many tiles zooms `minZoom`…`maxZoom` hold over `b`. Pure. */
  def tileCount(b: BBox, minZoom: Int, maxZoom: Int): Long =
    (minZoom to maxZoom).map { z =>
      val ((x0, x1), (y0, y1)) = tileRange(b, z)
      (x1 - x0 + 1) * (y1 - y0 + 1)
    }.sum

  /** The whole world, as far as web maps go (Web Mercator's ±85.05°): `--bbox world` (#3760). */
  val World: BBox = BBox(-180, -85.05, 180, 85.05)

  /** A bbox a fetch accepts: four numbers, west < east, south < north, inside the world; or `world`. */
  def validBBox(s: String): Either[String, BBox] =
    if s.trim.equalsIgnoreCase("world") then Right(World)
    else
      BBox.parse(s).toRight(s"'$s' is not minLon,minLat,maxLon,maxLat").flatMap { b =>
        if b.minLon >= b.maxLon || b.minLat >= b.maxLat then Left(s"'$s': west must be < east and south < north")
        else if b.minLon < -180 || b.maxLon > 180 || b.minLat < -90 || b.maxLat > 90 then
          Left(s"'$s' is outside the world")
        else Right(b)
      }

  /** One entry of `basemap.vector.drop`: a vector layer dropped whole (`roads`), only some of its kinds (`water
    * swimming_pool fountain`), or all of it but some kinds (`pois +peak +volcano`).
    */
  final case class DropRule(
      layer: String,
      whole: Boolean,
      drop: Set[String] = Set.empty,
      spare: Set[String] = Set.empty
  )

  /** `basemap.vector.drop`: "roads; buildings; pois +peak" — `;` between layers; a layer alone is dropped whole, `kind`
    * drops that kind, `+kind` spares it and drops the rest. Everything not named is KEPT, so a style can draw it
    * without a new download. Empty: nothing dropped. Pure.
    */
  def parseDrop(s: String): Either[String, List[DropRule]] =
    val parts = s.split(";").toList.map(_.trim).filter(_.nonEmpty).map(_.split("\\s+").toList)
    val rules = parts.map { words =>
      val kinds = words.tail
      val spare = kinds.filter(_.startsWith("+")).map(_.drop(1)).filter(_.nonEmpty).toSet
      val drop = kinds.filterNot(_.startsWith("+")).toSet
      DropRule(words.head, kinds.isEmpty, drop, spare)
    }
    val dup = rules.groupBy(_.layer).collect { case (l, rs) if rs.size > 1 => l }
    val mixed = rules.filter(r => r.drop.nonEmpty && r.spare.nonEmpty).map(_.layer)
    if dup.nonEmpty then Left(s"basemap.vector.drop names ${dup.mkString(", ")} twice")
    else if mixed.nonEmpty then
      Left(s"basemap.vector.drop: ${mixed.mkString(", ")} both drops kinds and spares kinds (+kind) — one or the other")
    else Right(rules)

  /** What a page reads of one data layer: its geometries (`1` points, `2` lines, `3` areas; empty: all) and the
    * features' kinds (empty: all).
    */
  final case class Read(geoms: Set[Int] = Set.empty, kinds: Set[String] = Set.empty):
    /** Both reads at once: the union, where "all" wins. */
    def merge(o: Read): Read =
      Read(
        if geoms.isEmpty || o.geoms.isEmpty then Set.empty else geoms ++ o.geoms,
        if kinds.isEmpty || o.kinds.isEmpty then Set.empty else kinds ++ o.kinds
      )

  private val KindName = "[A-Za-z0-9_:.-]+".r

  /** A served tile's subset (`?layers=earth,water:pl,landuse:a~forest|wood`): the layers a page draws and, per layer,
    * the geometries (`p` points, `l` lines, `a` areas; all when none is given) and the kinds after `~` (all when none)
    * — so a page never decodes what its style does not draw. Pure.
    */
  def parseSubset(s: String): Either[String, Map[String, Read]] =
    val parts = s.split(",").toList.map(_.trim).filter(_.nonEmpty)
    val parsed = parts.map { p =>
      val (head, kindPart) = p.split("~", 2).toList match
        case h :: k :: Nil => (h, Some(k))
        case h :: _        => (h, None)
        case Nil           => ("", None)
      val kinds = kindPart.map(_.split("\\|").toList.map(_.trim).filter(_.nonEmpty))
      val kindsOk = kinds.forall(ks => ks.nonEmpty && ks.forall(KindName.matches))
      head.split(":", 2).toList match
        case _ if !kindsOk => Left(s"'$p': kinds are `~kind|kind`")
        case l :: g :: Nil =>
          val codes = g.toList.map { case 'p' => 1; case 'l' => 2; case 'a' => 3; case _ => 0 }
          Either.cond(
            l.nonEmpty && codes.nonEmpty && !codes.contains(0),
            l -> Read(codes.toSet, kinds.fold(Set.empty)(_.toSet)),
            s"'$p': geometries are p, l and a"
          )
        case l :: Nil if l.nonEmpty => Right(l -> Read(Set.empty, kinds.fold(Set.empty)(_.toSet)))
        case _                      => Left(s"'$p' is not layer[:geometries][~kind|kind]")
    }
    parsed
      .collectFirst { case Left(e) => e }
      .toLeft(
        parsed
          .collect { case Right(kv) => kv }
          .groupMapReduce(_._1)(_._2)(_.merge(_))
      )

  /** The query value for a subset ([[parseSubset]]'s inverse), sorted so a page asks one URL; a layer of `fromZoom` is
    * asked for only from that tile zoom on (`landuse:a~forest@8`, [[subsetAt]]). Pure.
    */
  def subsetParam(m: Map[String, Read], fromZoom: Map[String, Int] = Map.empty): String =
    m.toList
      .sortBy(_._1)
      .map { (l, r) =>
        val g = if r.geoms.isEmpty then ""
        else ":" + r.geoms.toList.sorted.map { case 1 => 'p'; case 2 => 'l'; case _ => 'a' }.mkString
        val k = if r.kinds.isEmpty then "" else "~" + r.kinds.toList.sorted.mkString("|")
        l + g + k + fromZoom.get(l).filter(_ > 0).fold("")(z => s"@$z")
      }
      .mkString(",")

  /** A subset as it applies to a tile of zoom `z`: the entries of a deeper zoom (`landuse@8` at 7) left out, the others
    * without their zoom — so a page never downloads or decodes a layer its style draws only closer in. Pure.
    */
  def subsetAt(s: String, z: Int): String =
    s.split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { e =>
        e.split("@", 2).toList match
          case l :: n :: Nil =>
            n.trim.toIntOption match
              case Some(from) => Option.when(z >= from)(l)
              case None       => Some(e) // left for parseSubset to refuse
          case _ => Some(e)
      }
      .mkString(",")

  /** The vector data layers an extract made before the drop list lacks: it kept only `earth`, `water` and `landcover`.
    */
  val LegacyLacks: List[String] = List("boundaries", "buildings", "landuse", "places", "pois", "roads", "transit")

  /** The data layers an extract left out whole, from its metadata: none when `keepAll`; the layers its drop list drops
    * whole; [[LegacyLacks]] for an extract without that record. Pure.
    */
  def droppedLayers(keepAll: Boolean, drop: Option[String]): List[String] =
    if keepAll then Nil
    else
      drop match
        case Some(d) => parseDrop(d).toOption.getOrElse(Nil).filter(_.whole).map(_.layer).distinct.sorted
        case None    => LegacyLacks

  /** What a style that draws `needs` lacks in an extract without `dropped`. Pure. */
  def missingFor(needs: List[String], dropped: List[String]): List[String] =
    needs.filter(dropped.contains).distinct.sorted

  /** The command that stores the vector layer whole — what a style lacking layers needs. */
  val KeepAllCommand = s"$FetchCommand --layer vector --keep-all"

  /** What an extract kept, said in a line (`basemap status`): from its metadata — `keepAll`, the drop list it was
    * thinned with, or neither (an extract made before the drop list, which kept only coast, water and land cover).
    * Pure.
    */
  def keptLine(keepAll: Boolean, drop: Option[String]): String =
    if keepAll then "everything (unfiltered)"
    else
      drop match
        case Some(d) if d.trim.isEmpty => "everything (nothing dropped)"
        case Some(d)                   => s"all but: $d"
        case None                      => "earth, water and land cover only (fetched before the drop list)"

/** The web map's two vector styles (docs/research-map-styles.md; the style documents are the web page's): what each
  * draws of the vector extract. `needs`: the data layers without which it is not itself — a style drawn from an extract
  * that left one out says so, and what to run. Physical's towns come from `places` when the extract has them; it is
  * whole without them.
  */
object BaseStyles:
  final case class Style(id: String, name: String, needs: List[String])

  val Readable: Style =
    Style("readable", "Readable", List("boundaries", "earth", "landcover", "landuse", "places", "roads", "water"))
  val Physical: Style = Style("physical", "Physical", List("earth", "landcover", "landuse", "water"))

  /** In the order the menu shows them; the first is the default. */
  val all: List[Style] = List(Readable, Physical)

  /** Each style against an extract without `dropped` (`None`: no extract — nothing is said to be missing, the whole map
    * is). Pure.
    */
  def states(dropped: Option[List[String]]): List[BasemapStyleState] =
    all.map(s => BasemapStyleState(s.id, s.name, dropped.fold(Nil)(d => Basemap.missingFor(s.needs, d))))

/** `ui.base-map-source`: which base maps the web page offers (docs/user-guide/how-to/offline.md).
  *
  *   - `local`: only the maps drawn from the server's own extracts (Readable and Physical) — the page then asks no
  *     other host for anything;
  *   - `online`: those AND the online raster maps (`ui.tiles` and its Muted and Dark variants), the online Standard the
  *     default;
  *   - `auto` (the default setting): `local` once the stored extract can draw one of the vector styles whole (Physical
  *     from any download; Readable from a `--keep-all` one), else `online`.
  *
  * `/api/config` answers the resolved value (`local` or `online`), never `auto`.
  */
object BaseMapSource:
  val Auto = "auto"
  val Local = "local"
  val Online = "online"
  val All: List[String] = List(Auto, Local, Online)

  /** The setting as the page uses it: `local` or `online` (`auto` decided from the extract; anything else is `auto`).
    * Pure.
    */
  def resolve(setting: String, status: BasemapStatus): String =
    setting.trim.toLowerCase(java.util.Locale.ROOT) match
      case Local  => Local
      case Online => Online
      case _      =>
        val whole = status.styles.exists(s => BaseStyles.all.exists(_.id == s.id) && s.missing.isEmpty)
        if status.available && whole then Local else Online
