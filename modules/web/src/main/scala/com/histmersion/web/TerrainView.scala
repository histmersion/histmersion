package com.histmersion.web

import com.histmersion.core.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** What a viewer changed of a Histmersion map preset in the map-options panel ([[MapOptions]]): each
  * `None` is the preset's own. The style document stays the source of the presets; these lie over the
  * chosen one, per viewer (remembered in this browser), until "reset to preset". */
final case class ViewOptions(
    names: Option[Boolean] = None,
    /** Shading strength, a multiple of the preset's (0–2). */
    relief: Option[Double] = None,
    /** The ground coloured by its height. */
    heights: Option[Boolean] = None,
    landCover: Option[Boolean] = None,
    /** The sea coloured and shaded by its depth. */
    seaFloor: Option[Boolean] = None,
    contours: Option[Boolean] = None,
    /** A fixed contour interval in metres (`None`: the preset's, by zoom). */
    interval: Option[Double] = None
) derives KindlingsCodecAsObject:
  def isEmpty: Boolean = this == ViewOptions()

/** A viewer's map options: the names' language (for every preset) and each preset's changes, by id. */
final case class ViewPrefs(language: Option[String] = None, styles: Map[String, ViewOptions] = Map.empty)
    derives KindlingsCodecAsObject:
  def of(id: String): ViewOptions = styles.getOrElse(id, ViewOptions())
  def set(id: String, v: ViewOptions): ViewPrefs = copy(styles = if v.isEmpty then styles - id else styles + (id -> v))

object ViewPrefs:
  /** Kept as one JSON value; anything unreadable is no options at all. */
  given FieldCodec[ViewPrefs]:
    def encode(a: ViewPrefs) = if a == ViewPrefs() then Nil else List(io.circe.syntax.EncoderOps(a).asJson.noSpaces)
    def decode(vs: List[String]) =
      Some(vs.headOption.flatMap(v => io.circe.parser.decode[ViewPrefs](v).toOption).getOrElse(ViewPrefs()))

/** A preset with a viewer's options over it — the style that is drawn. Pure. */
object TerrainView:

  /** The data layers of land cover. */
  val LandCoverLayers: Set[String] = Set("landcover", "landuse")

  def isLandCover(l: StyleLayer): Boolean = l.draw == "fill" && l.dataLayers.nonEmpty && l.dataLayers.forall(LandCoverLayers)

  /** What the preset itself shows — each option's value before a viewer changes it. */
  def defaults(s: TerrainStyle): ViewOptions = ViewOptions(
    names = Some(s.labels && s.hasLabels), relief = Some(1.0), heights = Some(s.relief.heights && s.relief.tint.nonEmpty),
    landCover = Some(s.layers.exists(isLandCover)), seaFloor = Some(s.relief.seaFloor && s.relief.seaTint.nonEmpty),
    contours = Some(s.hasContours), interval = None)

  /** Which options a preset can offer at all, given the data the server has (`terrain`, `bathymetry`,
    * names): each with why not when it cannot. */
  def unavailable(s: TerrainStyle, have: Set[String]): Map[String, String] =
    val relief = Option.when(!have(BasemapLayer.Terrain.wire))(s"no relief downloaded — `${Basemap.FetchCommand} --layer terrain`")
    List(
      "names" -> Option.when(!s.hasLabels)("this preset draws no names"),
      "relief" -> relief.orElse(Option.when(!s.relief.enabled)("this preset has no relief")),
      "heights" -> relief.orElse(Option.when(s.relief.tint.isEmpty)("this preset has no colours by height")),
      "landCover" -> Option.when(!s.layers.exists(isLandCover))("this preset draws no land cover"),
      "seaFloor" -> relief.orElse(Option.when(s.relief.seaTint.isEmpty)("this preset has no sea-floor colours"))
        .orElse(Option.when(!have(BasemapLayer.Bathymetry.wire))(s"no sea floor downloaded — `${Basemap.FetchCommand} --layer bathymetry`")),
      "contours" -> relief
    ).collect { case (k, Some(why)) => k -> why }.toMap

  /** The value of each option shown: the viewer's, else the preset's. */
  def shown(s: TerrainStyle, v: ViewOptions): ViewOptions =
    val d = defaults(s)
    ViewOptions(v.names.orElse(d.names), v.relief.orElse(d.relief), v.heights.orElse(d.heights), v.landCover.orElse(d.landCover),
      v.seaFloor.orElse(d.seaFloor), v.contours.orElse(d.contours), v.interval)

  /** An option set to the preset's own value is no change: dropped, so "reset" and a toggle back agree. */
  def normalise(s: TerrainStyle, v: ViewOptions): ViewOptions =
    val d = defaults(s)
    def keep[A](a: Option[A], b: Option[A]) = a.filter(x => !b.contains(x))
    ViewOptions(keep(v.names, d.names), keep(v.relief, d.relief), keep(v.heights, d.heights), keep(v.landCover, d.landCover),
      keep(v.seaFloor, d.seaFloor), keep(v.contours, d.contours), v.interval)

  /** `style` with the viewer's options and language applied — what is drawn, given the data the server
    * has (`have`: an option whose data is missing stays as if off). */
  def apply(style: TerrainStyle, v: ViewOptions, language: Option[String], have: Set[String]): TerrainStyle =
    val o = shown(style, v)
    val missing = unavailable(style, have)
    def on(k: String, x: Option[Boolean]) = x.contains(true) && !missing.contains(k)
    var s = style // the options applied one after another, each a plain copy
    language.foreach(l => s = s.copy(language = Some(l)))
    s = s.copy(labels = on("names", o.names))
    o.relief.filter(_ != 1.0).foreach { k =>
      s = s.copy(relief = s.relief.copy(shadowOpacity = math.min(1.0, s.relief.shadowOpacity * k),
        highlightOpacity = math.min(1.0, s.relief.highlightOpacity * k)))
    }
    // colours by height (drawn over the land's fill, which shows where there is no elevation)
    val heights = on("heights", o.heights)
    s = s.copy(relief = s.relief.copy(heights = heights))
    if o.landCover.contains(false) then s = s.copy(layers = s.layers.filterNot(isLandCover))
    // the sea floor: on — coloured by depth in the relief, over the open sea's own fill (the ground, below it)
    // and under the lakes and the shore's glow; off — the water as the preset paints it
    val sea = on("seaFloor", o.seaFloor)
    s = s.copy(relief = s.relief.copy(seaFloor = sea))
    if sea then
      val water = (l: StyleLayer) => l.draw == "fill" && l.dataLayers.contains("water") && !l.kinds.exists(_ != "ocean") &&
        !l.notKinds.contains("ocean")
      s = s.copy(layers = s.layers.flatMap { l =>
        if !water(l) then List(l)
        else
          val sea   = l.copy(id = s"${l.id}-sea", kinds = List("ocean"), notKinds = Nil, glow = None)
          val lakes = l.copy(notKinds = (l.notKinds :+ "ocean").distinct)
          val shore = l.glow.map(_ => l.copy(id = s"${l.id}-shore", kinds = List("ocean"), notKinds = Nil,
            opacity = Some(ZoomValue.const(0))))
          sea :: (if l.kinds == List("ocean") then Nil else List(lakes)) ++ shore.toList
      })
    // contours: on or off, and a fixed interval
    val contours = on("contours", o.contours)
    if contours != s.relief.contours.enabled then
      s = s.copy(relief = s.relief.copy(contours = s.relief.contours.copy(enabled = contours)))
    v.interval.foreach(i => s = s.copy(relief = s.relief.copy(contours = s.relief.contours.copy(interval = ZoomValue.const(i)))))
    s

  /** Whether two drawn styles differ only in their names (the language, names on or off): then only the
    * names' layer is drawn again. */
  def onlyNames(a: TerrainStyle, b: TerrainStyle): Boolean =
    a.copy(labels = b.labels, language = b.language) == b

  /** The languages offered for the names: English, each place's own, and those both the map's data and
    * Natural Earth's names carry. */
  val Languages: List[(String, String)] = List("en" -> "English", "local" -> "Local (own script)", "de" -> "Deutsch",
    "fr" -> "Français", "it" -> "Italiano", "es" -> "Español", "el" -> "Ελληνικά", "tr" -> "Türkçe", "ru" -> "Русский",
    "ar" -> "العربية", "he" -> "עברית", "fa" -> "فارسی", "zh-Hans" -> "中文")

  /** The contour intervals offered besides the preset's own (by zoom). */
  val Intervals: List[Double] = List(10, 20, 50, 100, 200, 500)
