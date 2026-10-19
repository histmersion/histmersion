package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Json, JsonObject}
import sttp.tapir.Schema

import scala.collection.mutable.ListBuffer

/** One stop of a [[ZoomValue]]: at map zoom `zoom`, the value is `value`. */
final case class ZoomStop(zoom: Double, value: Double) derives KindlingsCodecAsObject
object ZoomStop:
  given Schema[ZoomStop] = KindlingsSchema.derived[ZoomStop].schema

/** A number that may change with the map's zoom: its stops, in zoom order, interpolated linearly
  * between and held constant beyond the first and the last. One stop is a constant. */
final case class ZoomValue(stops: List[ZoomStop]) derives KindlingsCodecAsObject:
  /** The value at zoom `z`. Pure. */
  def at(z: Double): Double = ZoomValue.interpolate(stops, z)
  /** Whether it is the same at every zoom. */
  def constant: Boolean = stops.map(_.value).distinct.size <= 1

object ZoomValue:
  given Schema[ZoomValue] = KindlingsSchema.derived[ZoomValue].schema

  def const(v: Double): ZoomValue = ZoomValue(List(ZoomStop(0, v)))

  /** Linear between neighbouring stops, the end values beyond them; 0 with no stop. Pure. */
  def interpolate(stops: List[ZoomStop], z: Double): Double = stops match
    case Nil                              => 0.0
    case first :: _ if z <= first.zoom    => first.value
    case _ =>
      stops.sliding(2).collectFirst {
        case List(a, b) if z <= b.zoom =>
          if b.zoom == a.zoom then b.value else a.value + (b.value - a.value) * (z - a.zoom) / (b.zoom - a.zoom)
      }.getOrElse(stops.last.value)

/** A label's font: CSS family, size in pixels (by zoom), weight and style. */
final case class StyleFont(family: String = "sans-serif", size: ZoomValue = ZoomValue.const(12), weight: String = "400",
    style: String = "normal") derives KindlingsCodecAsObject
object StyleFont:
  given Schema[StyleFont] = KindlingsSchema.derived[StyleFont].schema

/** One drawing rule of a terrain style, bottom to top: the features of the `dataLayers` of one `source`
  * (`vector`: the Protomaps extract; `labels`: the physical names) that `geometry`, `kinds` (the
  * feature's `kind`), `notKinds` and `details` (its `kind_detail`) select, between `minZoom` and
  * `maxZoom`, drawn as a `fill`, a `line` or a `label`. Labels are placed in rule order (earlier
  * rules win a collision); `ownMinZoom` also honours the feature's own `min_zoom`. */
final case class StyleLayer(
    id: String,
    draw: String,
    source: String = TerrainStyles.SourceVector,
    dataLayers: List[String],
    geometry: Option[String] = None,
    kinds: List[String] = Nil,
    notKinds: List[String] = Nil,
    details: List[String] = Nil,
    minZoom: Option[Double] = None,
    maxZoom: Option[Double] = None,
    fill: Option[String] = None,
    color: Option[String] = None,
    opacity: Option[ZoomValue] = None,
    width: Option[ZoomValue] = None,
    dash: List[Double] = Nil,
    placement: String = "point",
    halo: Option[String] = None,
    haloWidth: Option[ZoomValue] = None,
    font: Option[StyleFont] = None,
    transform: Option[String] = None,
    letterSpacing: Option[Double] = None,
    maxLineChars: Option[Int] = None,
    ownMinZoom: Boolean = true
) derives KindlingsCodecAsObject
object StyleLayer:
  given Schema[StyleLayer] = KindlingsSchema.derived[StyleLayer].schema

/** The relief shaded from the elevation tiles: the shadow and highlight colours and their strongest
  * opacity, the light's direction (azimuth clockwise from north) and height, and how much the slopes
  * are steepened at each zoom. */
final case class ReliefStyle(
    enabled: Boolean = true,
    shadow: String = "var(--terrain-shadow)",
    shadowOpacity: Double = 0.72,
    highlight: String = "var(--terrain-highlight)",
    highlightOpacity: Double = 0.2,
    azimuth: Double = 315,
    altitude: Double = 45,
    exaggeration: ZoomValue = ZoomValue(List(ZoomStop(0, 7.6), ZoomStop(11, 1)))
) derives KindlingsCodecAsObject
object ReliefStyle:
  given Schema[ReliefStyle] = KindlingsSchema.derived[ReliefStyle].schema

/** One named look of the map drawn from the server's own extracts — an entry of the base-map menu.
  * `tone`: whether it is a light or a dark map (the map's own names follow it). `labels`: whether its
  * label rules are drawn; `language` the names shown (else the document's, else
  * `basemap.labels.language`). `modern`: a map of today's world with its roads, towns and borders
  * (Standard, Muted, Dark) — no "no modern features" in the menu, no past-landscape notice. */
final case class TerrainStyle(
    id: String,
    name: String,
    description: String = "",
    tone: String = "dark",
    background: String,
    labels: Boolean = true,
    language: Option[String] = None,
    relief: ReliefStyle = ReliefStyle(),
    layers: List[StyleLayer] = Nil,
    modern: Boolean = false
) derives KindlingsCodecAsObject:
  def hasLabels: Boolean = layers.exists(_.draw == "label")
  /** The vector extract's data layers this style draws, sorted: what a download must have kept for
    * it ([[Basemap.missingFor]]). */
  def needs: List[String] =
    layers.filter(_.source == TerrainStyles.SourceVector).flatMap(_.dataLayers).distinct.sorted
object TerrainStyle:
  given Schema[TerrainStyle] = KindlingsSchema.derived[TerrainStyle].schema

/** `GET /api/basemap/styles`: the named terrain styles, resolved and checked, in menu order.
  *
  * @param source   where they came from: "built-in", or the user's file over the built-in ones
  * @param file     the style file looked for (`basemap.style`, else `basemap-style.conf` beside the config file)
  * @param version  changes whenever the served styles do — the page compares it to redraw
  * @param warnings what was refused or ignored (a broken file falls back to the built-in styles, said here)
  * @param checkSeconds how often the page asks again while the terrain map is shown */
final case class TerrainStyles(
    styles: List[TerrainStyle],
    default: String,
    language: String,
    source: String,
    file: Option[String] = None,
    version: String = "",
    warnings: List[String] = Nil,
    checkSeconds: Int = 0
) derives KindlingsCodecAsObject:
  def find(id: String): Option[TerrainStyle] = styles.find(_.id == id)
  /** The style `id` names, else the default, else the first. */
  def pick(id: Option[String]): Option[TerrainStyle] =
    id.flatMap(find).orElse(find(default)).orElse(styles.headOption)

object TerrainStyles:
  given Schema[TerrainStyles] = KindlingsSchema.derived[TerrainStyles].schema

  val SourceVector = "vector"
  val SourceLabels = "labels"
  val Sources: List[String] = List(SourceVector, SourceLabels)
  val Draws: List[String] = List("fill", "line", "label")
  val Geometries: List[String] = List("point", "line", "polygon")
  val Transforms: List[String] = List("none", "uppercase", "lowercase", "capitalize")
  val BuiltIn = "built-in"

  /** The feature properties a label reads, first present wins: `local` the name in its own
    * language, `en` the English name, any other code that language, then English, then the local
    * name. The Protomaps extract and the physical names both carry `name` and `name:<code>`. Pure. */
  def labelProps(language: String): List[String] = language.trim.toLowerCase(java.util.Locale.ROOT) match
    case "local" | "" => List("name")
    case "en"         => List("name:en", "name")
    case l            => List(s"name:$l", "name:en", "name")

  /** A language a label can be shown in: `local`, or a code like `en`, `de`, `zh-Hans`. Pure. */
  def validLanguage(s: String): Boolean = s == "local" || s.matches("[a-z]{2,3}(-[A-Za-z]{2,8})?")

/** `POST /api/basemap/styles/check`: what a style file would give, without using it — the styles it
  * would show, or its errors (each at its place in the file), and the keys it would ignore. */
final case class TerrainStyleCheck(ok: Boolean, styles: List[String] = Nil, default: Option[String] = None,
    errors: List[String] = Nil, warnings: List[String] = Nil) derives KindlingsCodecAsObject
object TerrainStyleCheck:
  given Schema[TerrainStyleCheck] = KindlingsSchema.derived[TerrainStyleCheck].schema

/** The terrain style document (docs/user-guide/how-to/terrain-styles.md): HOCON or JSON, its `styles`
  * merged over the built-in ones by `id`. Parsing and checking are pure: JSON in, styles or errors out —
  * every error names its place in the file (`styles[1].layers[3].width`), and an unknown key is a
  * warning (ignored), never silently dropped.
  *
  * Merging: a style with a built-in's `id` replaces it, or — with `extends` — is that style with the
  * keys it gives changed; its `layers` then patch the parent's by `id` (the keys given replace, `remove
  * = true` drops the layer, a new `id` is added at the end or `before` another). `hidden = true` takes a
  * style out of the menu. */
object TerrainStyleDoc:

  /** The parsed document: its styles (resolved), the default, the language, and the warnings. */
  final case class Parsed(styles: List[TerrainStyle], default: Option[String], language: Option[String], warnings: List[String])

  private val DocKeys   = Set("version", "default", "language", "styles")
  private val StyleKeys = Set("id", "name", "description", "extends", "hidden", "tone", "background", "labels", "language",
    "relief", "layers", "modern")
  private val ReliefKeys = Set("enabled", "shadow", "shadow-opacity", "highlight", "highlight-opacity", "azimuth", "altitude",
    "exaggeration")
  private val LayerKeys = Set("id", "type", "source", "layer", "geometry", "kinds", "not-kinds", "details", "min-zoom",
    "max-zoom", "fill", "color", "opacity", "width", "dash", "placement", "halo", "halo-width", "font", "transform",
    "letter-spacing", "max-line-chars", "own-min-zoom", "remove", "before")
  private val FontKeys = Set("family", "size", "weight", "style")

  // ── colours ─────────────────────────────────────────────────────────────────────────────────────

  private val Hex    = "#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})".r
  private val Func   = """(rgb|rgba|hsl|hsla)\(\s*[-+0-9.%\s,/a-z]+\)""".r
  private val Token  = """var\(--[a-zA-Z0-9-]+\)""".r
  /** CSS's named colours. */
  private val Named: Set[String] = ("transparent aliceblue antiquewhite aqua aquamarine azure beige bisque black " +
    "blanchedalmond blue blueviolet brown burlywood cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson " +
    "cyan darkblue darkcyan darkgoldenrod darkgray darkgreen darkgrey darkkhaki darkmagenta darkolivegreen darkorange " +
    "darkorchid darkred darksalmon darkseagreen darkslateblue darkslategray darkslategrey darkturquoise darkviolet deeppink " +
    "deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite forestgreen fuchsia gainsboro ghostwhite gold goldenrod " +
    "gray green greenyellow grey honeydew hotpink indianred indigo ivory khaki lavender lavenderblush lawngreen " +
    "lemonchiffon lightblue lightcoral lightcyan lightgoldenrodyellow lightgray lightgreen lightgrey lightpink lightsalmon " +
    "lightseagreen lightskyblue lightslategray lightslategrey lightsteelblue lightyellow lime limegreen linen magenta " +
    "maroon mediumaquamarine mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue mediumspringgreen " +
    "mediumturquoise mediumvioletred midnightblue mintcream mistyrose moccasin navajowhite navy oldlace olive olivedrab " +
    "orange orangered orchid palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum " +
    "powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna silver " +
    "skyblue slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato turquoise violet wheat white " +
    "whitesmoke yellow yellowgreen").split(" ").toSet

  /** A colour the page can draw: `#rgb[a]`, `#rrggbb[aa]`, `rgb()`/`rgba()`/`hsl()`/`hsla()`, a CSS
    * colour name, or `var(--token)` — a token of the web UI's stylesheet, so a theme still applies. Pure. */
  def validColour(s: String): Boolean =
    val v = s.trim
    Hex.matches(v) || Func.matches(v.toLowerCase(java.util.Locale.ROOT)) || Token.matches(v) || Named(v.toLowerCase(java.util.Locale.ROOT))

  // ── reading one object, with the errors at their path ──────────────────────────────────────────

  /** Collects a pass's errors and warnings; a local of [[parse]], never shared. */
  private final class Notes:
    val errors: ListBuffer[String]   = ListBuffer.empty
    val warnings: ListBuffer[String] = ListBuffer.empty

  private def describe(j: Json): String =
    j.fold("null", b => b.toString, n => n.toString, s => s"\"$s\"", _ => "a list", _ => "an object")

  private final class Obj(val path: String, val o: JsonObject, n: Notes):
    def has(k: String): Boolean = o.contains(k)
    private def at(k: String) = if path.isEmpty then k else s"$path.$k"
    def err(k: String, msg: String): Unit = n.errors += s"${at(k)}: $msg"

    def unknown(known: Set[String]): Unit =
      o.keys.filterNot(known).foreach(k => n.warnings += s"${at(k)}: unknown key, ignored (known: ${known.toList.sorted.mkString(", ")})")

    def str(k: String): Option[String] = o(k).flatMap { j =>
      j.asString.orElse(j.asNumber.map(_.toString)).orElse { err(k, s"expected text, got ${describe(j)}"); None }
    }
    def oneOf(k: String, allowed: List[String]): Option[String] = str(k).flatMap { s =>
      Option.when(allowed.contains(s))(s).orElse { err(k, s"\"$s\" is not one of ${allowed.mkString(", ")}"); None }
    }
    def colour(k: String): Option[String] = str(k).flatMap { s =>
      Option.when(validColour(s))(s.trim).orElse {
        err(k, s"\"$s\" is not a colour (#rrggbb, rgb(…), rgba(…), hsl(…), a CSS colour name or var(--token))"); None
      }
    }
    def bool(k: String): Option[Boolean] = o(k).flatMap { j =>
      j.asBoolean.orElse(j.asString.collect { case "true" => true; case "false" => false })
        .orElse { err(k, s"expected true or false, got ${describe(j)}"); None }
    }
    def num(k: String, min: Double = Double.NegativeInfinity, max: Double = Double.PositiveInfinity): Option[Double] =
      o(k).flatMap { j =>
        j.asNumber.map(_.toDouble).orElse(j.asString.flatMap(_.trim.toDoubleOption)) match
          case Some(d) if d >= min && d <= max => Some(d)
          case Some(d) => err(k, s"$d is outside $min…$max".replace(".0…", "…").replace("Infinity", "∞")); None
          case None    => err(k, s"expected a number, got ${describe(j)}"); None
      }
    def int(k: String, min: Int, max: Int): Option[Int] = num(k, min, max).flatMap { d =>
      Option.when(d == math.rint(d))(d.toInt).orElse { err(k, s"expected a whole number, got $d"); None }
    }
    def strings(k: String): Option[List[String]] = o(k).flatMap { j =>
      j.asArray.map(_.toList).orElse(j.asString.map(s => List(Json.fromString(s)))) match
        case Some(vs) =>
          val out = vs.flatMap(v => v.asString.orElse(v.asNumber.map(_.toString)))
          if out.size != vs.size then { err(k, "expected a list of text"); None } else Some(out)
        case None => err(k, s"expected a list of text, got ${describe(j)}"); None
    }
    def numbers(k: String): Option[List[Double]] = o(k).flatMap { j =>
      j.asArray.map(_.toList.flatMap(_.asNumber.map(_.toDouble))) match
        case Some(ds) if ds.size == j.asArray.map(_.size).getOrElse(-1) && ds.forall(_ >= 0) => Some(ds)
        case _ => err(k, s"expected a list of non-negative numbers, got ${describe(j)}"); None
    }
    /** A number, or `[[zoom, value], …]` (zooms 0–24, ascending). */
    def zoomValue(k: String, min: Double, max: Double): Option[ZoomValue] = o(k).flatMap { j =>
      val bad = s"expected a number or [[zoom, value], …], got ${describe(j)}"
      j.asNumber.map(_.toDouble) match
        case Some(d) => if d >= min && d <= max then Some(ZoomValue.const(d)) else { err(k, s"$d is outside $min…$max"); None }
        case None =>
          j.asArray.map(_.toList) match
            case None | Some(Nil) => err(k, bad); None
            case Some(pairs) =>
              val stops = pairs.map(_.asArray.map(_.toList.flatMap(_.asNumber.map(_.toDouble))))
              if stops.exists(s => !s.exists(_.size == 2)) then { err(k, bad); None }
              else
                val ss = stops.flatten.map { case List(z, v) => ZoomStop(z, v); case _ => ZoomStop(0, 0) }
                if ss.exists(s => s.zoom < 0 || s.zoom > 24) then { err(k, "a stop's zoom is outside 0–24"); None }
                else if ss.zip(ss.drop(1)).exists((a, b) => b.zoom < a.zoom) then { err(k, "the stops' zooms must ascend"); None }
                else if ss.exists(s => s.value < min || s.value > max) then { err(k, s"a stop's value is outside $min…$max"); None }
                else Some(ZoomValue(ss))
    }
    def obj(k: String): Option[Obj] = o(k).flatMap { j =>
      j.asObject.map(Obj(at(k), _, n)).orElse { err(k, s"expected an object { … }, got ${describe(j)}"); None }
    }
    def objs(k: String): Option[List[Obj]] = o(k).flatMap { j =>
      j.asArray match
        case None => err(k, s"expected a list [ … ], got ${describe(j)}"); None
        case Some(vs) =>
          Some(vs.toList.zipWithIndex.flatMap { (v, i) =>
            v.asObject.map(Obj(s"${at(k)}[$i]", _, n)).orElse { n.errors += s"${at(k)}[$i]: expected an object { … }, got ${describe(v)}"; None }
          })
    }

  // ── checking (types and values of the keys given; `complete`: also the keys a style needs) ──────

  private def checkFont(f: Obj): Option[StyleFont] =
    f.unknown(FontKeys)
    val d = StyleFont()
    Some(StyleFont(f.str("family").getOrElse(d.family), f.zoomValue("size", 4, 64).getOrElse(d.size),
      f.str("weight").getOrElse(d.weight), f.oneOf("style", List("normal", "italic", "oblique")).getOrElse(d.style)))

  private def checkLayer(l: Obj, complete: Boolean, styleId: String): Option[StyleLayer] =
    l.unknown(LayerKeys)
    val id = l.str("id")
    if id.isEmpty && !l.has("id") then l.err("id", "a layer needs an id")
    val draw = l.oneOf("type", TerrainStyles.Draws)
    val geometry = l.oneOf("geometry", TerrainStyles.Geometries)
    val layer = l.strings("layer").filter(_.nonEmpty)
    val source = l.oneOf("source", TerrainStyles.Sources)
    val fill = l.colour("fill")
    val color = l.colour("color")
    val halo = l.colour("halo")
    val font = l.obj("font").flatMap(checkFont)
    val layerOut = StyleLayer(
      id = id.getOrElse(""), draw = draw.getOrElse(""), source = source.getOrElse(TerrainStyles.SourceVector),
      dataLayers = layer.getOrElse(Nil), geometry = geometry,
      kinds = l.strings("kinds").getOrElse(Nil), notKinds = l.strings("not-kinds").getOrElse(Nil),
      details = l.strings("details").getOrElse(Nil),
      minZoom = l.num("min-zoom", 0, 24), maxZoom = l.num("max-zoom", 0, 24),
      fill = fill, color = color, opacity = l.zoomValue("opacity", 0, 1), width = l.zoomValue("width", 0, 64),
      dash = l.numbers("dash").getOrElse(Nil), placement = l.oneOf("placement", List("point", "line")).getOrElse("point"),
      halo = halo, haloWidth = l.zoomValue("halo-width", 0, 16), font = font,
      transform = l.oneOf("transform", TerrainStyles.Transforms), letterSpacing = l.num("letter-spacing", 0, 32),
      maxLineChars = l.int("max-line-chars", 1, 200), ownMinZoom = l.bool("own-min-zoom").getOrElse(true))
    l.bool("remove"); l.str("before")
    if complete then
      val where = s"layer '${layerOut.id}' of style '$styleId'"
      if draw.isEmpty && !l.has("type") then l.err("type", s"$where needs a type (${TerrainStyles.Draws.mkString(", ")})")
      if layer.isEmpty && !l.has("layer") then l.err("layer", s"$where needs the data layer it draws (earth, water, landcover, …)")
      draw match
        case Some("fill") if fill.isEmpty && !l.has("fill")    => l.err("fill", s"$where is a fill and needs a fill colour")
        case Some("line") if color.isEmpty && !l.has("color")  => l.err("color", s"$where is a line and needs a color")
        case Some("label") if color.isEmpty && !l.has("color") => l.err("color", s"$where is a label and needs a color")
        case _ => ()
      for a <- layerOut.minZoom; b <- layerOut.maxZoom if a > b do l.err("min-zoom", s"$where: min-zoom $a is above max-zoom $b")
    Some(layerOut)

  private def checkRelief(r: Obj): ReliefStyle =
    r.unknown(ReliefKeys)
    val d = ReliefStyle()
    ReliefStyle(r.bool("enabled").getOrElse(d.enabled), r.colour("shadow").getOrElse(d.shadow),
      r.num("shadow-opacity", 0, 1).getOrElse(d.shadowOpacity), r.colour("highlight").getOrElse(d.highlight),
      r.num("highlight-opacity", 0, 1).getOrElse(d.highlightOpacity), r.num("azimuth", 0, 360).getOrElse(d.azimuth),
      r.num("altitude", 1, 90).getOrElse(d.altitude), r.zoomValue("exaggeration", 0, 100).getOrElse(d.exaggeration))

  /** One style object: every key given checked (a raw style of the file), and when `complete` (a
    * style after merging) the keys it needs. */
  private def checkStyle(s: Obj, complete: Boolean): Option[TerrainStyle] =
    s.unknown(StyleKeys)
    val id = s.str("id").map(_.trim)
    id.filter(i => !i.matches("[a-zA-Z0-9][a-zA-Z0-9_-]*")).foreach(i => s.err("id", s"\"$i\": letters, digits, - and _ only"))
    s.str("extends"); s.bool("hidden")
    val name = s.str("name")
    val tone = s.oneOf("tone", List("light", "dark"))
    val background = s.colour("background")
    val language = s.str("language").flatMap { l =>
      Option.when(TerrainStyles.validLanguage(l))(l).orElse { s.err("language", s"\"$l\" is not local or a language code like en, de"); None }
    }
    val relief = s.obj("relief").map(checkRelief)
    val layers = s.objs("layers").map(_.flatMap(checkLayer(_, complete, id.getOrElse("?"))))
    val sid = id.getOrElse("")
    if complete then
      if name.forall(_.trim.isEmpty) then s.err("name", s"style '$sid' needs a name (the base-map menu shows it)")
      if background.isEmpty && !s.has("background") then s.err("background", s"style '$sid' needs a background colour (the sea)")
      layers.getOrElse(Nil).groupBy(_.id).collect { case (i, ls) if ls.size > 1 => i }.foreach(i =>
        s.err("layers", s"style '$sid' has two layers with the id '$i'"))
    Some(TerrainStyle(id = sid, name = name.getOrElse(sid), description = s.str("description").getOrElse(""),
      tone = tone.getOrElse("dark"), background = background.getOrElse(""), labels = s.bool("labels").getOrElse(true),
      language = language, relief = relief.getOrElse(ReliefStyle()), layers = layers.getOrElse(Nil),
      modern = s.bool("modern").getOrElse(false)))

  // ── merging ────────────────────────────────────────────────────────────────────────────────────

  /** `child`'s keys over `parent`'s: `relief` merged key by key, `layers` patched by id. Pure. */
  def mergeStyle(parent: JsonObject, child: JsonObject): JsonObject =
    child.toList.foldLeft(parent.remove("hidden")) { case (acc, (k, v)) =>
      k match
        case "extends" => acc
        case "relief" =>
          val merged = (acc("relief").flatMap(_.asObject), v.asObject) match
            case (Some(a), Some(b)) => Json.fromJsonObject(b.toList.foldLeft(a)((o, kv) => o.add(kv._1, kv._2)))
            case _                  => v
          acc.add("relief", merged)
        case "layers" =>
          (acc("layers").flatMap(_.asArray), v.asArray) match
            case (Some(base), Some(patches)) => acc.add("layers", Json.fromValues(mergeLayers(base.toList, patches.toList)))
            case _                           => acc.add(k, v)
        case _ => acc.add(k, v)
    }

  private def idOf(j: Json): Option[String] = j.hcursor.get[String]("id").toOption

  /** The parent's layers patched by the child's, by id. Pure. */
  def mergeLayers(base: List[Json], patches: List[Json]): List[Json] =
    patches.foldLeft(base) { (acc, p) =>
      val pid    = idOf(p)
      val remove = p.hcursor.get[Boolean]("remove").getOrElse(false)
      val clean  = p.asObject.map(o => Json.fromJsonObject(o.remove("remove").remove("before"))).getOrElse(p)
      acc.indexWhere(l => pid.isDefined && idOf(l) == pid) match
        case -1 if remove => acc
        case -1 =>
          p.hcursor.get[String]("before").toOption.map(b => acc.indexWhere(l => idOf(l).contains(b))).filter(_ >= 0) match
            case Some(i) => acc.patch(i, List(clean), 0)
            case None    => acc :+ clean
        case i if remove => acc.patch(i, Nil, 1)
        case i =>
          val merged = (acc(i).asObject, clean.asObject) match
            case (Some(a), Some(b)) => Json.fromJsonObject(b.toList.foldLeft(a)((o, kv) => o.add(kv._1, kv._2)))
            case _                  => clean
          acc.updated(i, merged)
    }

  // ── the document ───────────────────────────────────────────────────────────────────────────────

  /** Parse a style document over `base` (the built-in styles' raw objects, in order — empty when
    * parsing the built-in document itself). Any error: `Left(errors)`, and the caller keeps what it
    * had; else the resolved, checked styles in menu order. Pure. */
  def parse(doc: Json, base: List[JsonObject] = Nil): Either[List[String], (Parsed, List[JsonObject])] =
    val n = Notes()
    doc.asObject match
      case None => Left(List(s"the style file must be an object { styles = [ … ] }, got ${describe(doc)}"))
      case Some(root) =>
        val top = Obj("", root, n)
        top.unknown(DocKeys)
        top.int("version", 1, 1)
        val default  = top.str("default")
        val language = top.str("language").flatMap { l =>
          Option.when(TerrainStyles.validLanguage(l))(l).orElse { top.err("language", s"\"$l\" is not local or a language code"); None }
        }
        // no `styles`: the built-in ones as they are (a file may only pick the default or the language)
        val raw = top.objs("styles").getOrElse(Nil)
        raw.foreach(s => checkStyle(s, complete = false))
        raw.foreach(s => if !s.has("id") then s.err("id", "a style needs an id"))
        // resolve in order: the built-in ones first, each of the file's over what is there by then
        val resolved = raw.foldLeft(base.map(o => (idOf(Json.fromJsonObject(o)).getOrElse(""), o))) { (acc, s) =>
          val id = idOf(Json.fromJsonObject(s.o)).getOrElse("")
          val ext = s.o("extends").flatMap(_.asString)
          val obj = ext match
            case None => s.o.remove("extends")
            case Some(p) =>
              acc.find(_._1 == p).map(_._2) match
                case Some(parent) => mergeStyle(parent, s.o)
                case None =>
                  s.err("extends", s"no style '$p' to extend (${acc.map(_._1).mkString(", ")})")
                  s.o
          acc.indexWhere(_._1 == id) match
            case -1 => acc :+ (id -> obj)
            case i  => acc.updated(i, id -> obj)
        }
        val shown = resolved.filterNot(_._2("hidden").flatMap(_.asBoolean).getOrElse(false))
        if n.errors.nonEmpty then Left(n.errors.toList.distinct)
        else
          // the merged styles, checked whole: what each needs (its own keys were checked above, so
          // this pass's warnings would only repeat them)
          val whole = Notes()
          val styles = shown.flatMap { (id, o) =>
            val where = raw.indexWhere(r => idOf(Json.fromJsonObject(r.o)).contains(id))
            checkStyle(Obj(if where >= 0 then s"styles[$where]" else s"style '$id'", o, whole), complete = true)
          }
          val wt = Obj("", root, whole)
          if styles.isEmpty then wt.err("styles", "every style is hidden: the menu would have none")
          default.filter(d => !styles.exists(_.id == d)).foreach(d => wt.err("default", s"no style '$d' (${styles.map(_.id).mkString(", ")})"))
          if whole.errors.nonEmpty then Left(whole.errors.toList.distinct)
          else Right((Parsed(styles, default, language, n.warnings.toList.distinct), resolved.map(_._2)))
