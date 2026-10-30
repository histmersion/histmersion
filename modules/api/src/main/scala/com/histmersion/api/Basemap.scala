package com.histmersion.api

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
