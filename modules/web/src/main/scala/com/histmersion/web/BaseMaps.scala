package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.api.BBox
import com.histmersion.web.map.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.basemap.{Basemap, BaseMapSource, BaseStyles}
import com.histmersion.api.instance.UiConfig

/** The base map (docs/user-guide/exploring/web-ui.md, "The base map"; docs/research-map-styles.md):
  *
  *   - **Readable** and **Physical**, the two vector styles drawn from this server's own extracts (`GET
  *     /api/basemap/{layer}/{z}/{x}/{y}`) — today's landscape, with a notice saying so;
  *   - the **online** raster maps (OpenStreetMap's tiles: Standard, Muted, Dark), unless the page is `local`
  *     (`ui.base-map-source`): then it asks no other host for anything.
  *
  * One menu chooses among them; a small options panel (names on or off, their language, contours in Physical, the sea's
  * depth) lies over the vector styles, per viewer ([[UiState.mapOptions]]). The map surface draws the choice
  * ([[BaseMapSpec]]); without a GPU ([[Capabilities.software]]) the vector styles are drawn without their relief — the
  * cheapest drawing — and a line of the notice says so.
  */
object BaseMaps:

  /** What the server reports (`GET /api/basemap`); `None` until asked. */
  type LocalOnly = LocalOnly.T
  object LocalOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Software = Software.T
  object Software extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViewPart1 = ViewPart1.T
  object ViewPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EffectiveLocal = EffectiveLocal.T
  object EffectiveLocal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChoicesLocal = ChoicesLocal.T
  object ChoicesLocal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionLabelResult = OptionLabelResult.T
  object OptionLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PastNotice = PastNotice.T
  object PastNotice extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SoftwareNotice = SoftwareNotice.T
  object SoftwareNotice extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DocsLinkResult = DocsLinkResult.T
  object DocsLinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DocsLinkDocsUrl = DocsLinkDocsUrl.T
  object DocsLinkDocsUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DocsLinkPage = DocsLinkPage.T
  object DocsLinkPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MissingNoticeResult = MissingNoticeResult.T
  object MissingNoticeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LackingNoticeResult = LackingNoticeResult.T
  object LackingNoticeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LackingNoticeMissing = LackingNoticeMissing.T
  object LackingNoticeMissing extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllCreditsLink = AllCreditsLink.T
  object AllCreditsLink extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditsHtmlResult = CreditsHtmlResult.T
  object CreditsHtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditsHtmlCredits = CreditsHtmlCredits.T
  object CreditsHtmlCredits extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticeLiftResult = NoticeLiftResult.T
  object NoticeLiftResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticeLiftNoticeLeft = NoticeLiftNoticeLeft.T
  object NoticeLiftNoticeLeft extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticeLiftNoticeRight = NoticeLiftNoticeRight.T
  object NoticeLiftNoticeRight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticeLiftCreditsLeft = NoticeLiftCreditsLeft.T
  object NoticeLiftCreditsLeft extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticeLiftCreditsRight = NoticeLiftCreditsRight.T
  object NoticeLiftCreditsRight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticeLiftCreditsHeight = NoticeLiftCreditsHeight.T
  object NoticeLiftCreditsHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticeLiftRoom = NoticeLiftRoom.T
  object NoticeLiftRoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LanguagesPart1 = LanguagesPart1.T
  object LanguagesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LanguagesPart2 = LanguagesPart2.T
  object LanguagesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanResultPart2Part1 = PlanResultPart2Part1.T
  object PlanResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanResultPart2Part2 = PlanResultPart2Part2.T
  object PlanResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanResultPart3 = PlanResultPart3.T
  object PlanResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanLocal = PlanLocal.T
  object PlanLocal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanSoft = PlanSoft.T
  object PlanSoft extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetNoticeNPart1 = SetNoticeNPart1.T
  object SetNoticeNPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetNoticeNPart2 = SetNoticeNPart2.T
  object SetNoticeNPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetNoticeLacking = SetNoticeLacking.T
  object SetNoticeLacking extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticePart1 = NoticePart1.T
  object NoticePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticePart2 = NoticePart2.T
  object NoticePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticePart3 = NoticePart3.T
  object NoticePart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrawnCredits = DrawnCredits.T
  object DrawnCredits extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FootOpen = FootOpen.T
  object FootOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FootOpenNowResult = FootOpenNowResult.T
  object FootOpenNowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FootOpenNowChosen = FootOpenNowChosen.T
  object FootOpenNowChosen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FootOpenNowMoved = FootOpenNowMoved.T
  object FootOpenNowMoved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FootOpenNowWarns = FootOpenNowWarns.T
  object FootOpenNowWarns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeepsOsmCreditResult = KeepsOsmCreditResult.T
  object KeepsOsmCreditResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Open = Open.T
  object Open extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnVector = OnVector.T
  object OnVector extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultLanguageResult = DefaultLanguageResult.T
  object DefaultLanguageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LanguageChoicesResultPart1 = LanguageChoicesResultPart1.T
  object LanguageChoicesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LanguageChoicesResultPart2 = LanguageChoicesResultPart2.T
  object LanguageChoicesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LanguageChoicesDefault = LanguageChoicesDefault.T
  object LanguageChoicesDefault extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeeperReliefResult = DeeperReliefResult.T
  object DeeperReliefResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContoursFromZoom = ContoursFromZoom.T
  object ContoursFromZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContoursWhyResult = ContoursWhyResult.T
  object ContoursWhyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContoursWhySoft = ContoursWhySoft.T
  object ContoursWhySoft extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MeetsResult = MeetsResult.T
  object MeetsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContoursNoteResult = ContoursNoteResult.T
  object ContoursNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContoursNoteZ = ContoursNoteZ.T
  object ContoursNoteZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeaDepthWhyResult = SeaDepthWhyResult.T
  object SeaDepthWhyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeaDepthWhySoft = SeaDepthWhySoft.T
  object SeaDepthWhySoft extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacementResultPart1 = PlacementResultPart1.T
  object PlacementResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacementResultPart2 = PlacementResultPart2.T
  object PlacementResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacementButtonLeft = PlacementButtonLeft.T
  object PlacementButtonLeft extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacementButtonRight = PlacementButtonRight.T
  object PlacementButtonRight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacementButtonBottom = PlacementButtonBottom.T
  object PlacementButtonBottom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacementWidth = PlacementWidth.T
  object PlacementWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacementWindowWidth = PlacementWindowWidth.T
  object PlacementWindowWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanelSideButtonTop = PanelSideButtonTop.T
  object PanelSideButtonTop extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanelSideButtonBottom = PanelSideButtonBottom.T
  object PanelSideButtonBottom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanelSideWindowHeight = PanelSideWindowHeight.T
  object PanelSideWindowHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanelSideMinBelow = PanelSideMinBelow.T
  object PanelSideMinBelow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val status: Var[Option[Basemap.Status]] = Var(None)

  /** Whether the page has only the local maps (`ui.base-map-source` resolved to `local`). */
  val localOnly: Var[com.histmersion.web.BaseMaps.LocalOnly] = Var(false)

  /** The base map drawn — the choice as [[effective]] maps it: what the names' tone follows. */
  val shown: Var[BaseMap] = Var(BaseMap.Standard)

  /** The browser draws its WebGL in software: the cheapest drawing of every map. */
  val software: Var[com.histmersion.web.BaseMaps.Software] = Var(false)

  /** The map's view, once it is still (its zoom and region): what the contours' line follows. */
  val view: Var[Option[(com.histmersion.web.BaseMaps.ViewPart1, GeoBounds)]] = Var(None)

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The local style an extract draws best: Readable when it can draw it whole, else Physical. Pure. */
  def defaultLocal(s: Option[Basemap.Status]): BaseMap =
    if s.exists(st => st.available && st.missing(BaseStyles.Readable.id).isEmpty) || s.isEmpty then BaseMap.Readable
    else BaseMap.Physical

  /** The base map drawn for the kept choice `b`: on a `local` page an online choice is the default local style, so a
    * browser's earlier choice never reaches another host. Pure.
    */
  def effective(b: BaseMap, local: com.histmersion.web.BaseMaps.EffectiveLocal, s: Option[Basemap.Status]): BaseMap =
    if local && b.online then defaultLocal(s) else b

  /** The choices the menu offers: the two styles, and the online maps unless the page is `local`. Pure. */
  def choices(local: com.histmersion.web.BaseMaps.ChoicesLocal): List[BaseMap] = if local then BaseMap.vectors else BaseMap.values.toList

  /** A choice's label: a vector style says when the data is missing, or when the download left out layers it draws.
    * Pure.
    */
  def optionLabel(b: BaseMap, s: Option[Basemap.Status]): com.histmersion.web.BaseMaps.OptionLabelResult =
    if b.online then b.label
    else if !s.forall(_.available) then s"${b.label} — not downloaded"
    else if s.exists(st => st.missing(b.wire).nonEmpty) then s"${b.label} — needs the full download"
    else b.label

  /** The notice over a vector style. */
  val PastNotice: com.histmersion.web.BaseMaps.PastNotice =
    "Today's terrain. Coastlines, rivers, lakes and vegetation were different in the past."

  /** The line added without a GPU. */
  val SoftwareNotice: com.histmersion.web.BaseMaps.SoftwareNotice =
    "This browser draws maps without a GPU: the relief is left out to keep the map quick."

  /** The docs section the notice links to, under `ui.docs-url`. */
  def docsLink(docsUrl: com.histmersion.web.BaseMaps.DocsLinkDocsUrl, page: com.histmersion.web.BaseMaps.DocsLinkPage = "exploring/web-ui.md#the-base-map"): com.histmersion.web.BaseMaps.DocsLinkResult =
    val base = if docsUrl.isEmpty || docsUrl.endsWith("/") then docsUrl else docsUrl + "/"
    s"$base$page"

  /** The docs section on downloading the full extract. */
  val OfflinePage = "how-to/offline.md#the-one-time-download"

  /** The notice when the extracts are not on the server: what to run. */
  def missingNotice(s: Option[Basemap.Status]): com.histmersion.web.BaseMaps.MissingNoticeResult =
    val cmd = s.map(_.fetchCommand).getOrElse(Basemap.FetchCommand)
    s"This map is not downloaded on this server. Run `$cmd` on the machine that runs it (a few hundred MB to a few GB), " +
      "then choose it again."

  /** The notice over a style drawn without some of its layers: which, and what to run. Pure. */
  def lackingNotice(missing: List[com.histmersion.web.BaseMaps.LackingNoticeMissing]): com.histmersion.web.BaseMaps.LackingNoticeResult =
    s"This map also draws ${missing.mkString(", ")}, which the download on this server left out. Run " +
      s"`${Basemap.KeepAllCommand}` on the machine that runs it, then choose the map again."

  /** The credit line's link to every dataset's credit (Settings › Credits): the places, boundaries and events drawn
    * over the base map are credited there.
    */
  val allCreditsLink: com.histmersion.web.BaseMaps.AllCreditsLink =
    s"""<a class="map-credits-all" href="${Route.Settings(SettingsSection.Credits).path}">Data sources</a>"""

  /** Pure: the credits line — each source's credit once, in order, separated by a middle dot. */
  def creditsHtml(credits: Seq[com.histmersion.web.BaseMaps.CreditsHtmlCredits]): com.histmersion.web.BaseMaps.CreditsHtmlResult = credits.map(_.trim).filter(_.nonEmpty).distinct.mkString(" · ")

  /** How far the notice's corner rises so it clears the right-hand corner's controls: their height and a gap when the
    * two would overlap side by side, else nothing — and nothing when that would take it past the `room` above it in the
    * map (an opened legend as tall as the map lies over it instead). Pure.
    */
  def noticeLift(
      noticeLeft: com.histmersion.web.BaseMaps.NoticeLiftNoticeLeft,
      noticeRight: com.histmersion.web.BaseMaps.NoticeLiftNoticeRight,
      creditsLeft: com.histmersion.web.BaseMaps.NoticeLiftCreditsLeft,
      creditsRight: com.histmersion.web.BaseMaps.NoticeLiftCreditsRight,
      creditsHeight: com.histmersion.web.BaseMaps.NoticeLiftCreditsHeight,
      room: com.histmersion.web.BaseMaps.NoticeLiftRoom = Double.MaxValue
  ): com.histmersion.web.BaseMaps.NoticeLiftResult =
    val lift = if noticeRight > creditsLeft && creditsRight > noticeLeft then creditsHeight + 4 else 0
    if lift > room then 0 else lift

  /** The languages offered for the names: English, each place's own, and those both the map's data and Natural Earth's
    * names carry.
    */
  val Languages: List[(com.histmersion.web.BaseMaps.LanguagesPart1, com.histmersion.web.BaseMaps.LanguagesPart2)] = List(
    "en" -> "English",
    "local" -> "Local (own script)",
    "de" -> "Deutsch",
    "fr" -> "Français",
    "it" -> "Italiano",
    "es" -> "Español",
    "el" -> "Ελληνικά",
    "tr" -> "Türkçe",
    "ru" -> "Русский",
    "ar" -> "العربية",
    "he" -> "עברית",
    "fa" -> "فارسی",
    "zh-Hans" -> "中文"
  )

  /** The online raster map of `b` (`ui.tiles` for Standard and Dark). Pure. */
  def raster(b: BaseMap, ui: UiConfig): BaseMapSpec.Raster =
    val osm = "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors (ODbL)"
    val url = b.url.getOrElse(ui.tiles)
    val credit =
      if url.contains("openstreetmap.fr/hot") then s"$osm · tiles © Humanitarian OSM Team, OSM France" else osm
    BaseMapSpec.Raster(url, ui.tilesMaxZoom, credit, dark = BaseMap.darkened(b, MapTone.parse(ui.tilesTone)))

  /** What is drawn for base map `b` (already [[effective]]), the server's status and the viewer's options: the spec,
    * the notice, and how deep the map zooms. Pure.
    */
  def plan(
      b: BaseMap,
      s: Option[Basemap.Status],
      prefs: MapPrefs,
      ui: UiConfig,
      local: com.histmersion.web.BaseMaps.PlanLocal,
      soft: com.histmersion.web.BaseMaps.PlanSoft
  ): (BaseMapSpec, Option[(com.histmersion.web.BaseMaps.PlanResultPart2Part1, com.histmersion.web.BaseMaps.PlanResultPart2Part2)], com.histmersion.web.BaseMaps.PlanResultPart3) =
    b.vector match
      case None    => (raster(b, ui), None, ui.tilesMaxZoom)
      case Some(v) =>
        s.filter(_.available) match
          case Some(st) =>
            val lang = prefs.language.getOrElse(ui.mapLanguage)
            val spec = BaseMapSpec.Vector(
              v,
              st,
              lang,
              prefs.names.getOrElse(true),
              prefs.contours.getOrElse(true),
              prefs.seaDepth(v),
              reduced = soft
            )
            val lacking = st.missing(b.wire)
            val notice = if lacking.nonEmpty then (lackingNotice(lacking), true) else (PastNotice, false)
            (spec, Some(notice), st.mapMaxZoom)
          case None =>
            // not downloaded: the online Standard stands in (none on a local page), and the notice says what to run
            (
              if local then BaseMapSpec.Blank else raster(BaseMap.Standard, ui),
              Some((missingNotice(s), true)),
              if local then ui.tilesMaxZoom else ui.tilesMaxZoom
            )

  // ── on the map ──────────────────────────────────────────────────────────────────────────────────

  /** The base map drawn now (a small map elsewhere on the page draws the same). */
  // `var`: set with every change of the base map
  var current: BaseMapSpec = BaseMapSpec.Blank

  // `var`s: the page's settings and its map, once installed
  private var ui: UiConfig = null
  private var surface: MapSurface = null
  private var noticeEl: Option[dom.HTMLElement] = None

  /** Before the map is made: the page's `ui.base-map-source`. */
  def configure(config: UiConfig): Unit =
    ui = config
    localOnly.set(config.baseMapSource == BaseMapSource.Local)

  /** Ask the server again (on start, and when a vector style is chosen). */
  def refresh(): Unit =
    ApiClient
      .basemap()
      .foreach(s => status.set(Some(s)))(using scala.scalajs.concurrent.JSExecutionContext.Implicits.queue)

  def install(m: MapSurface, config: UiConfig)(using Owner): Unit =
    surface = m
    ui = config
    software.set(m.capabilities.software)
    view.set(Some((m.zoom, m.bounds)))
    val _ = m.on(MapEventKind.MoveEnd)(_ => view.set(Some((m.zoom, m.bounds))))
    // the credits are shown at first; the reader's first pan or zoom folds them to their (i) button
    val _ = m.on(MapEventKind.MoveStart)(e => if e.native.isDefined && !moved then { moved = true; foldAfterMove() })
    // a base map drawn: its credits are known (they follow its sources and the layers drawn)
    val _ = m.on(MapEventKind.BaseMapReady)(_ => if m.credits != drawnCredits then drawFoot())
    refresh()
    // a page with only the local maps, never told otherwise by this browser: its best local style
    status.signal.foreach { s =>
      if localOnly.now() && !UiState.stored(UiState.baseMap) && UiState.baseMap.now().online then
        UiState.set(UiState.baseMap := defaultLocal(s))()
    }
    UiState.baseMap.signal.combineWith(status.signal, UiState.mapOptions.signal).foreach { (b, s, prefs) =>
      val eb = effective(b, localOnly.now(), s)
      shown.set(eb)
      val (spec, notice, maxZoom) = plan(eb, s, prefs, config, localOnly.now(), software.now())
      m.setMaxZoom(maxZoom.toDouble)
      m.setBaseMap(spec)
      current = spec
      setNotice(
        notice.map((t, warn) => (if software.now() && eb.vector.isDefined then s"$t $SoftwareNotice" else t, warn)),
        lacking = notice.exists(_._1.startsWith("This map also"))
      )
      m.container.classList.toggle("basemap-vector", eb.vector.isDefined && spec.isInstanceOf[BaseMapSpec.Vector])
    }

  /** The map's foot, bottom left (#3759): the notice over a vector style (or what to run), and under it the base map's
    * credits — one box, where MapLibre's ⓘ button and its folded credits used to sit in the right-hand corner under the
    * legend. Without a notice (an online map) the box holds the credits alone; without either it is gone.
    */
  private def setNotice(n: Option[(com.histmersion.web.BaseMaps.SetNoticeNPart1, com.histmersion.web.BaseMaps.SetNoticeNPart2)], lacking: com.histmersion.web.BaseMaps.SetNoticeLacking): Unit =
    notice = n.map((t, w) => (t, w, lacking))
    drawFoot()

  // `var`s: the notice drawn now (its text, whether it warns, whether it is the lacking-layers one), and the
  // credits drawn with it
  private var notice: Option[(com.histmersion.web.BaseMaps.NoticePart1, com.histmersion.web.BaseMaps.NoticePart2, com.histmersion.web.BaseMaps.NoticePart3)] = None
  private var drawnCredits: Seq[com.histmersion.web.BaseMaps.DrawnCredits] = Nil
  // whether the reader opened (or closed) the foot with its (i) button: kept when it is drawn again; whether the
  // reader has moved the map yet; how far the foot's corner is lifted now
  private var footOpen: Option[com.histmersion.web.BaseMaps.FootOpen] = None
  private var moved = false
  private var noticeLifted = 0.0

  /** Pure: is the foot open — the reader's choice, else open until the first pan or zoom, and open whenever it says the
    * map is not (wholly) downloaded.
    */
  def footOpenNow(chosen: Option[com.histmersion.web.BaseMaps.FootOpenNowChosen], moved: com.histmersion.web.BaseMaps.FootOpenNowMoved, warns: com.histmersion.web.BaseMaps.FootOpenNowWarns): com.histmersion.web.BaseMaps.FootOpenNowResult = chosen.getOrElse(warns || !moved)

  /** Pure: does the folded foot keep a credit in sight? The online OpenStreetMap tiles (and OSM France's) require
    * theirs to stay visible on the map.
    */
  def keepsOsmCredit(b: BaseMap): com.histmersion.web.BaseMaps.KeepsOsmCreditResult = b.online

  /** The first pan or zoom: the credits fold to the (i) button, unless the reader chose otherwise. */
  private def foldAfterMove(): Unit =
    noticeEl.foreach { box =>
      if footOpen.isEmpty && !notice.exists(_._2) then
        MapFold.set(box, false)
        dom.window.requestAnimationFrame((_: Double) => fitNotice())
    }

  private def drawFoot(): Unit =
    noticeEl.foreach(_.remove())
    noticeEl = None
    val credits = surface.credits
    drawnCredits = credits
    if notice.nonEmpty || credits.nonEmpty then
      val box = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
      box.className = if notice.exists(_._2) then "map-foot is-missing hd-map-control" else "map-foot hd-map-control"
      // open at first (the credits must be seen without any interaction), folded to its (i) button after the
      // reader's first pan or zoom; opened with the button it shows the notice about the past landscape too
      val open = footOpenNow(footOpen, moved, notice.exists(_._2))
      box.innerHTML = MapFold.toggleHtml(
        "btn--icon map-foot-toggle",
        "map-foot-body",
        if notice.nonEmpty then "about this map: its terrain and credits" else "map credits",
        Icon.html(Icon.Info),
        open
      )
      MapFold.set(box, open)
      box.classList.toggle("is-expanded", footOpen.contains(true))
      // the online OpenStreetMap tiles' credit stays in sight, folded or not
      if keepsOsmCredit(shown.now()) then
        val osm = dom.document.createElement("span").asInstanceOf[dom.HTMLElement]
        osm.className = "map-credits-keep"
        osm.innerHTML = "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>"
        box.appendChild(osm)
      MapFold.wire(box, o => { footOpen = Some(o); box.classList.toggle("is-expanded", o); fitNotice() })
      val body = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
      body.className = "map-foot-body"
      body.id = "map-foot-body"
      box.appendChild(body)
      notice.foreach { (text, warn, lacking) =>
        val el = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
        el.className = "terrain-notice"
        el.setAttribute("role", "note")
        val p = dom.document.createElement("span")
        p.textContent = text
        val a = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
        a.href = if lacking then docsLink(ui.docsUrl, OfflinePage)
        else if warn then docsLink(ui.docsUrl, "how-to/offline.md")
        else docsLink(ui.docsUrl)
        a.target = "_blank"
        a.rel = "noopener"
        a.textContent = if warn then "How to download it" else "Why?"
        el.appendChild(p)
        el.appendChild(dom.document.createTextNode(" "))
        el.appendChild(a)
        body.appendChild(el)
      }
      if credits.nonEmpty then
        val c = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
        c.className = "map-credits"
        // the sources' own credit lines (their links included), as the map library showed them
        c.innerHTML = creditsHtml(credits) + " · " + allCreditsLink
        Option(c.querySelector(".map-credits-all")).foreach(
          _.addEventListener(
            "click",
            (e: dom.MouseEvent) =>
              if Router.plain(e) then { e.preventDefault(); Router.go(Route.Settings(SettingsSection.Credits)) }
          )
        )
        body.appendChild(c)
      surface.corner(Corner.BottomLeft).appendChild(box)
      noticeEl = Some(box)
      dom.window.requestAnimationFrame((_: Double) => fitNotice())
    else
      noticeLifted = 0.0
      surface.container.style.setProperty("--terrain-notice-lift", "0px")

  // the notice's watcher of the right-hand corner (the credits, the legend), made once
  private lazy val cornerWatch: Unit =
    val ro = scala.scalajs.js.Dynamic.global.ResizeObserver
    if !scala.scalajs.js.isUndefined(ro) then
      val o = scala.scalajs.js.Dynamic.newInstance(ro)(
        ((_: scala.scalajs.js.Any) => fitNotice()): scala.scalajs.js.Function1[scala.scalajs.js.Any, Unit]
      )
      o.observe(surface.corner(Corner.BottomRight))
      o.observe(surface.container)

  /** The notice shares the map's foot with the right-hand corner (the credits, the legend and its key): where they
    * would meet, the notice's corner rises above that corner.
    */
  private def fitNotice(): Unit =
    cornerWatch
    noticeEl.foreach { n =>
      val boxes = surface
        .corner(Corner.BottomRight)
        .children
        .toList
        .map(_.getBoundingClientRect())
        .filter(r => r.width > 0 && r.height > 0)
      val nr = n.getBoundingClientRect()
      // the room above the notice as it would stand unlifted, to the map's top or the controls in its corner
      val above = surface
        .corner(Corner.TopLeft)
        .children
        .toList
        .map(_.getBoundingClientRect())
        .filter(r => r.width > 0 && r.height > 0)
        .map(_.bottom)
        .maxOption
        .getOrElse(surface.container.getBoundingClientRect().top)
      val room = nr.top + noticeLifted - above - 4
      val lift = if boxes.isEmpty then 0.0
      else
        noticeLift(
          nr.left,
          nr.right,
          boxes.map(_.left).min,
          boxes.map(_.right).max,
          boxes.map(_.bottom).max - boxes.map(_.top).min,
          room
        )
      noticeLifted = lift
      surface.container.style.setProperty("--terrain-notice-lift", s"${lift}px")
    }

  // ── the controls ────────────────────────────────────────────────────────────────────────────────

  private val open: Var[com.histmersion.web.BaseMaps.Open] = Var(false)

  private val onVector: Signal[com.histmersion.web.BaseMaps.OnVector] = shown.signal.map(_.vector.isDefined)

  private def baseSelect: HtmlElement =
    select(
      cls := "field map-base-select",
      aria.label := "base map",
      title := "Readable and Physical are drawn from this server's own data (today's landscape); the online maps are OpenStreetMap's tiles",
      children <-- localOnly.signal
        .combineWith(status.signal)
        .map((l, s) =>
          choices(l).map(b =>
            option(
              value := b.wire,
              optionLabel(b, s),
              title := (if b.online then "Tiles from the internet"
                        else s.map(_.missing(b.wire)).filter(_.nonEmpty).fold(PastNotice)(lackingNotice))
            )
          )
        ),
      controlled(
        value <-- shown.signal.map(_.wire).composeChanges(_.delay(0)),
        onChange.mapToValue --> { v =>
          BaseMap.fromWire(v).foreach { b =>
            if b.vector.isDefined then refresh()
            UiState.set(UiState.baseMap := b)()
          }
        }
      )
    )

  /** The button that opens the options: the map's motion on every map, and the vector styles' own options. */
  private def optionsButton: HtmlElement =
    button(
      typ := "button",
      idAttr := "map-options-button",
      cls := "btn btn--toggle btn--icon map-options-btn",
      aria.label := "map options",
      htmlAttr("aria-haspopup", com.raquo.laminar.codecs.StringAsIsCodec) := "dialog",
      aria.controls := "map-options",
      aria.expanded <-- open.signal,
      title <-- onVector.map(b =>
        if b then "Map options: names, their language, contours, the sea's depth; map motion"
        else "Map options: map motion"
      ),
      Icon(Icon.MapContours),
      onClick --> { _ => open.update(!_) }
    )

  /** The names' language unless the viewer chose one: `basemap.labels.language`, as the server reports it. */
  private def defaultLanguage: com.histmersion.web.BaseMaps.DefaultLanguageResult = if ui == null || ui.mapLanguage.isEmpty then "en" else ui.mapLanguage

  /** The languages the menu offers: [[Languages]], and the configured default when it is not among them. Pure. */
  def languageChoices(default: com.histmersion.web.BaseMaps.LanguageChoicesDefault): List[(com.histmersion.web.BaseMaps.LanguageChoicesResultPart1, com.histmersion.web.BaseMaps.LanguageChoicesResultPart2)] =
    if Languages.exists(_._1 == default) then Languages else Languages :+ (default -> default)

  private def label_(mods: Modifier[HtmlElement]*): HtmlElement = htmlTag("label")(mods*)

  private def setPrefs(f: MapPrefs => MapPrefs): Unit = UiState.set(UiState.mapOptions := f(UiState.mapOptions.now()))()

  /** The relief as the server has it, when it can be drawn. */
  private def relief(s: Option[Basemap.Status]): Option[Basemap.LayerInfo] =
    s.flatMap(_.layers.find(l => l.layer == Basemap.Layer.Terrain.wire && l.present && l.error.isEmpty))

  /** Relief deeper than the extract: a focus region downloaded, or the on-demand relief on. Pure. */
  def deeperRelief(i: Basemap.LayerInfo): com.histmersion.web.BaseMaps.DeeperReliefResult = i.focus.nonEmpty || i.onDemand.exists(_.enabled)

  /** The app zoom from which Physical draws its contours (its style's `contours` layer: MapLibre zoom 10). */
  val ContoursFromZoom: com.histmersion.web.BaseMaps.ContoursFromZoom = 11.0

  /** Why the contours cannot be drawn anywhere, if they cannot: only Physical draws them, with a GPU, from relief
    * deeper than the extract — a focus region (`basemap fetch --layer terrain --focus`) or the on-demand relief
    * (`basemap.terrain.on-demand`). Pure.
    */
  def contoursWhy(b: BaseMap, s: Option[Basemap.Status], soft: com.histmersion.web.BaseMaps.ContoursWhySoft): Option[com.histmersion.web.BaseMaps.ContoursWhyResult] =
    if b != BaseMap.Physical then Some("Physical draws contours")
    else if soft then Some("not drawn without a GPU")
    else
      relief(s) match
        case None                        => Some(s"no relief downloaded — `${Basemap.FetchCommand} --layer terrain`")
        case Some(i) if !deeperRelief(i) =>
          Some(
            s"no detailed relief: no focus region downloaded and on-demand relief is off — " +
              s"`${Basemap.FetchCommand} --layer terrain --focus <region>`"
          )
        case _ => None

  /** Do a region of the view and a focus region's box meet? Pure. */
  def meets(v: GeoBounds, b: BBox): com.histmersion.web.BaseMaps.MeetsResult =
    v.south <= b.maxLat && v.north >= b.minLat && v.west <= b.maxLon && v.east >= b.minLon

  /** Why the contours, possible on this map, are not drawn in this view (zoom `z`, region `v`): zoomed out too far, or
    * outside every focus region with the on-demand relief off (the contours there follow the extract's coarse relief).
    * `None`: they are drawn. Pure.
    */
  def contoursNote(s: Option[Basemap.Status], z: com.histmersion.web.BaseMaps.ContoursNoteZ, v: GeoBounds): Option[com.histmersion.web.BaseMaps.ContoursNoteResult] =
    relief(s).flatMap { i =>
      val inFocus = i.focus.exists(f => BBox.parse(f.bbox).exists(meets(v, _)))
      if z < ContoursFromZoom then Some(f"zoom in to see contours (from zoom ${ContoursFromZoom.toInt}%d)")
      else if !inFocus && !i.onDemand.exists(_.enabled) then
        Some(
          s"outside the detailed relief (${i.focus.map(_.name).mkString(", ")}) and on-demand relief is off: " +
            "contours here follow the coarse relief"
        )
      else None
    }

  /** Why the sea cannot be coloured by its depth, if it cannot: with a GPU, from the sea floor (`basemap fetch --layer
    * bathymetry`). Pure.
    */
  def seaDepthWhy(b: BaseMap, s: Option[Basemap.Status], soft: com.histmersion.web.BaseMaps.SeaDepthWhySoft): Option[com.histmersion.web.BaseMaps.SeaDepthWhyResult] =
    if b.vector.isEmpty then Some("Readable and Physical draw the sea's depth")
    else if soft then Some("not drawn without a GPU")
    else if !s.exists(_.layers.exists(l => l.layer == Basemap.Layer.Bathymetry.wire && l.present && l.error.isEmpty))
    then Some(s"no sea floor downloaded — `${Basemap.FetchCommand} --layer bathymetry`")
    else None

  private def panel: HtmlElement =
    val why = shown.signal.combineWith(status.signal, software.signal).map(contoursWhy.tupled)
    val note = why.combineWith(status.signal, view.signal).map { (w, s, v) =>
      if w.isDefined then None else v.flatMap((z, b) => contoursNote(s, z, b))
    }
    val depthWhy = shown.signal.combineWith(status.signal, software.signal).map(seaDepthWhy.tupled)
    div(
      idAttr := "map-options",
      cls := "map-options",
      role := "dialog",
      aria.label := "map options",
      cls("is-hidden") <-- open.signal.map(!_),
      // the vector styles' own options: on an online map, a line that says where they are
      div(
        cls := "map-options-vector",
        cls("is-hidden") <-- onVector.map(!_),
        div(
          cls := "map-option",
          label_(
            input(
              typ := "checkbox",
              cls := "map-option-names",
              checked <-- UiState.mapOptions.signal.map(_.names.getOrElse(true)),
              onInput.mapToChecked --> (on => setPrefs(_.modify(_.names).setTo(Option.when(!on)(false))))
            ),
            span("Names")
          )
        ),
        div(
          cls := "map-option map-option-row",
          label_(
            span("Language"),
            select(
              cls := "field map-language-select",
              aria.label := "names' language",
              disabled <-- UiState.mapOptions.signal.map(_.names.contains(false)),
              languageChoices(defaultLanguage).map((code, name) => option(value := code, name)),
              controlled(
                value <-- UiState.mapOptions.signal.map(_.language.getOrElse(defaultLanguage)),
                onChange.mapToValue --> (v =>
                  setPrefs(_.modify(_.language).setTo(Option.when(v != defaultLanguage)(v)))
                )
              )
            )
          )
        ),
        div(
          cls := "map-option",
          cls("is-disabled") <-- why.map(_.isDefined),
          label_(
            input(
              typ := "checkbox",
              cls := "map-option-contours",
              disabled <-- why.map(_.isDefined),
              checked <-- UiState.mapOptions.signal
                .combineWith(why)
                .map((p, w) => w.isEmpty && p.contours.getOrElse(true)),
              onInput.mapToChecked --> (on => setPrefs(_.modify(_.contours).setTo(Option.when(!on)(false))))
            ),
            span("Contour lines")
          ),
          child.maybe <-- why
            .combineWith(note)
            .map((w, n) => w.orElse(n).map(t => div(cls := "map-option-why map-option-contours-why", t)))
        ),
        div(
          cls := "map-option",
          cls("is-disabled") <-- depthWhy.map(_.isDefined),
          label_(
            input(
              typ := "checkbox",
              cls := "map-option-depth",
              disabled <-- depthWhy.map(_.isDefined),
              checked <-- UiState.mapOptions.signal
                .combineWith(shown.signal, depthWhy)
                .map((p, b, w) => w.isEmpty && b.vector.exists(p.seaDepth)),
              onInput.mapToChecked --> (on => shown.now().vector.foreach(v => setPrefs(_.withSeaDepth(v, on))))
            ),
            span("Sea depth")
          ),
          child.maybe <-- depthWhy.map(_.map(w => div(cls := "map-option-why map-option-depth-why", w)))
        )
      ),
      div(
        cls := "map-option map-options-online",
        cls("is-hidden") <-- onVector,
        div(cls := "map-option-why", "Names, contours and the sea's depth are options of Readable and Physical.")
      ),
      motionOption,
      div(
        cls := "map-option map-option-row",
        button(
          typ := "button",
          cls := "btn btn--ghost btn--sm map-options-reset",
          "Reset",
          disabled <-- UiState.mapOptions.signal.map(_ == MapPrefs()),
          onClick --> { _ => setPrefs(_ => MapPrefs()) }
        ),
        button(
          typ := "button",
          cls := "btn btn--ghost btn--sm map-options-close",
          "Close",
          onClick --> { _ => close() }
        )
      )
    )

  /** The map's motion (#4718), on every base map: follow the system's reduced-motion setting, always animate, or never
    * ([[MotionPref]]); and, when it follows the system and the system asks for reduced motion, a line that says why
    * nothing moves.
    */
  private def motionOption: HtmlElement =
    div(
      cls := "map-option map-option-row map-option-motion",
      label_(
        span("Map motion"),
        select(
          cls := "field map-motion-select",
          idAttr := "map-motion-select",
          aria.label := "map motion",
          aria.describedBy <-- MotionPref.heldBySystem.map(h => if h then "map-motion-note" else ""),
          MapMotion.values.toList.map(m => option(value := m.wire, m.label)),
          controlled(
            value <-- UiState.mapMotion.signal.map(_.wire),
            onChange.mapToValue --> (v => MapMotion.fromWire(v).foreach(m => UiState.set(UiState.mapMotion := m)()))
          )
        )
      ),
      child.maybe <-- MotionPref.heldBySystem.map(h =>
        Option.when(h)(
          div(idAttr := "map-motion-note", cls := "map-option-why map-motion-note", role := "note", MotionNote)
        )
      )
    )

  /** The line under "Map motion" while the system's setting keeps the map still. */
  val MotionNote = "Animations are off because your system asks for reduced motion."

  /** The panel's first control a reader can reach (an online map hides the vector styles' options). */
  private def firstControl(): Option[dom.HTMLElement] =
    dom.document
      .querySelectorAll("#map-options input, #map-options select")
      .toList
      .map(_.asInstanceOf[dom.HTMLElement])
      .find(e => e.offsetParent != null && !e.asInstanceOf[js.Dynamic].disabled.asInstanceOf[Boolean])

  /** Where the panel goes: under its button, its right edge at the button's — moved left or right to stay inside the
    * window. Pure.
    */
  def placement(
      buttonLeft: com.histmersion.web.BaseMaps.PlacementButtonLeft,
      buttonRight: com.histmersion.web.BaseMaps.PlacementButtonRight,
      buttonBottom: com.histmersion.web.BaseMaps.PlacementButtonBottom,
      width: com.histmersion.web.BaseMaps.PlacementWidth,
      windowWidth: com.histmersion.web.BaseMaps.PlacementWindowWidth
  ): (com.histmersion.web.BaseMaps.PlacementResultPart1, com.histmersion.web.BaseMaps.PlacementResultPart2) =
    val x = math.max(8.0, math.min(windowWidth - width - 8, buttonRight - width))
    (if buttonLeft < 0 then 8.0 else x, buttonBottom + 4)

  /** Which side of its button the panel opens on: under it, its top `top` px from the window's top; or over it, its
    * bottom `bottom` px from the window's bottom.
    */
  enum PanelSide:
    case Below(top: PanelSide.Below.Top)
    case Above(bottom: PanelSide.Above.Bottom)

  object PanelSide:
    object Below:
      type Top = Top.T
      object Top extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Above:
      type Bottom = Bottom.T
      object Bottom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: under the button, unless less than `minBelow` px is left under it and more is above — the button low in a
    * tablet's map & tools drawer, where the panel would be squeezed into a strip.
    */
  def panelSide(buttonTop: com.histmersion.web.BaseMaps.PanelSideButtonTop, buttonBottom: com.histmersion.web.BaseMaps.PanelSideButtonBottom, windowHeight: com.histmersion.web.BaseMaps.PanelSideWindowHeight, minBelow: com.histmersion.web.BaseMaps.PanelSideMinBelow): PanelSide =
    val below = windowHeight - buttonBottom - 4
    if below < minBelow && buttonTop - 4 > below then PanelSide.Above(windowHeight - buttonTop + 4)
    else PanelSide.Below(buttonBottom + 4)

  private def place(): Unit =
    for
      b <- Option(dom.document.getElementById("map-options-button"))
      p <- Option(dom.document.getElementById("map-options")).map(_.asInstanceOf[dom.HTMLElement])
    do
      val r = b.getBoundingClientRect()
      val (x, y) =
        placement(r.left, r.right, r.bottom, math.min(300.0, dom.window.innerWidth * 0.92), dom.window.innerWidth)
      p.style.setProperty("--map-options-x", s"${x}px")
      p.style.setProperty("--map-options-y", s"${y}px")
      val minBelow = if Viewport.tabletNow then Layout.mapOptionsMinBelow.value.toDouble else 0.0
      panelSide(r.top, r.bottom, dom.window.innerHeight, minBelow) match
        case PanelSide.Above(bottom) =>
          p.classList.add("is-above")
          p.style.setProperty("--map-options-b", s"${bottom}px")
        case PanelSide.Below(_) => p.classList.remove("is-above")

  private def close(): Unit =
    open.set(false)
    Option(dom.document.getElementById("map-options-button")).foreach(_.asInstanceOf[dom.HTMLElement].focus())

  /** The base-map controls of the map's toolbar: the menu and the options. */
  def controls: HtmlElement =
    div(
      cls := "btn-group map-base-group",
      role := "group",
      aria.label := "base map",
      baseSelect,
      div(cls := "map-options-anchor", optionsButton, panel),
      documentEvents(_.onKeyDown) --> { e => if e.key == "Escape" && open.now() then close() },
      documentEvents(_.onPointerDown) --> { e =>
        val t = e.target.asInstanceOf[dom.Element]
        if open.now() && t != null && t.closest(".map-options-anchor") == null then open.set(false)
      },
      open.signal.changes.filter(identity) --> { _ =>
        place()
        dom.window.setTimeout(() => firstControl().foreach(_.focus()), 0)
      },
      windowEvents(_.onResize) --> { _ => if open.now() then place() }
    )
