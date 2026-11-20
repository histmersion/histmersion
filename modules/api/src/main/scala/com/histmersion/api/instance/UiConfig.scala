package com.histmersion
package api.instance
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.MapTilesUiConfig
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class UiConfig(
    tiles: UiConfig.Tiles,
    tilesMaxZoom: UiConfig.TilesMaxZoom,
    markerRadius: UiConfig.MarkerRadius,
    viewportDebounceMs: UiConfig.ViewportDebounceMs,
    pageSize: UiConfig.PageSize,
    facetSuggestions: UiConfig.FacetSuggestions,
    treeDepth: UiConfig.TreeDepth,
    treeSamples: UiConfig.TreeSamples,
    maxBuckets: UiConfig.MaxBuckets,
    debounce: DebounceConfig,
    time: TimeConfig,
    narrowMaxWidthPx: UiConfig.NarrowMaxWidthPx,
    narrowMaxHeightPx: UiConfig.NarrowMaxHeightPx,
    tabletMaxWidthPx: UiConfig.TabletMaxWidthPx,
    events: EventsUiConfig,
    mapTiles: MapTilesUiConfig = MapTilesUiConfig(),
    countersPollSeconds: UiConfig.CountersPollSeconds = UiConfig.CountersPollSeconds(30),
    jobPollMs: UiConfig.JobPollMs = UiConfig.JobPollMs(1000),
    keyRequired: UiConfig.KeyRequired = UiConfig.KeyRequired(false),
    agentsEnabled: UiConfig.AgentsEnabled = UiConfig.AgentsEnabled(false),
    tilesTone: UiConfig.TilesTone = UiConfig.TilesTone("light"),
    plugins: PluginsReport = PluginsReport(),
    docsUrl: UiConfig.DocsUrl = UiConfig.DocsUrl(""),
    baseMapSource: UiConfig.BaseMapSource = api.basemap.BaseMapSource.Wire("auto"),
    mapLanguage: UiConfig.MapLanguage = UiConfig.MapLanguage("en"),
    mapStart: MapStartConfig = MapStartConfig(),
    setupDue: UiConfig.SetupDue = UiConfig.SetupDue(false),
    buildId: UiConfig.BuildId = UiConfig.BuildId("")
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object UiConfig:
  type Tiles = Tiles.T
  object Tiles extends Newtype[String, Pure]
  type TilesMaxZoom = TilesMaxZoom.T
  object TilesMaxZoom extends Newtype[Int, Pure]
  type MarkerRadius = MarkerRadius.T
  object MarkerRadius extends Newtype[Int, Pure]
  type ViewportDebounceMs = ViewportDebounceMs.T
  object ViewportDebounceMs extends Newtype[Int, Pure]
  type PageSize = PageSize.T
  object PageSize extends Newtype[Int, Pure]
  type FacetSuggestions = FacetSuggestions.T
  object FacetSuggestions extends Newtype[Int, Pure]
  type TreeDepth = TreeDepth.T
  object TreeDepth extends Newtype[Int, Pure]
  type TreeSamples = TreeSamples.T
  object TreeSamples extends Newtype[Int, Pure]
  type MaxBuckets = MaxBuckets.T
  object MaxBuckets extends Newtype[Int, Pure]
  type NarrowMaxWidthPx = NarrowMaxWidthPx.T
  object NarrowMaxWidthPx extends Newtype[Int, Pure]
  type NarrowMaxHeightPx = NarrowMaxHeightPx.T
  object NarrowMaxHeightPx extends Newtype[Int, Pure]
  type TabletMaxWidthPx = TabletMaxWidthPx.T
  object TabletMaxWidthPx extends Newtype[Int, Pure]
  type CountersPollSeconds = CountersPollSeconds.T
  object CountersPollSeconds extends Newtype[Int, Pure]
  type JobPollMs = JobPollMs.T
  object JobPollMs extends Newtype[Int, Pure]

  /** Whether the page must send the API key: it was let in with the key, not as a trusted network's. */
  type KeyRequired = KeyRequired.T
  object KeyRequired extends Newtype[Boolean, Pure]
  type AgentsEnabled = AgentsEnabled.T
  object AgentsEnabled extends Newtype[Boolean, Pure]
  type TilesTone = TilesTone.T
  object TilesTone extends Newtype[String, Pure]
  type DocsUrl = DocsUrl.T
  object DocsUrl extends Newtype[String, Pure]
  export api.basemap.BaseMapSource.Wire as BaseMapSource
  type MapLanguage = MapLanguage.T
  object MapLanguage extends Newtype[String, Pure]
  type SetupDue = SetupDue.T
  object SetupDue extends Newtype[Boolean, Pure]

  /** The build of the web bundle the server ships (G109): the first 32 hex digits of the linked bundle's SHA-256,
    * written by the build into the bundle and beside it. The server sends it here, in the bundle's URL and in the
    * [[BuildHeader]] of every answer; the page compares it with its own and asks for a reload when they differ. Empty
    * from a server without a built bundle (or an older one): nothing to compare.
    */
  type BuildId = BuildId.T
  object BuildId extends Newtype[String, Pure]:
    /** How long an id is: the first 32 hex digits of the bundle's SHA-256. */
    private val Length = 32

    /** How a page's build compares with the server's. */
    enum Verdict derives CanEqual:
      /** Both name the same build. */
      case Same

      /** One side names none (a development bundle, a server without a built bundle or an older one). */
      case Unknown

      /** The server runs another build than the page's. */
      case Updated

    extension (id: BuildId)
      /** An id as the build writes it: 32 lower-case hex digits. Empty, or the page's placeholder, is none. */
      def known: Boolean =
        id.value.length == Length && id.value.forall(c => c >= '0' && c <= '9' || c >= 'a' && c <= 'f')

      /** This page's build against the server's. */
      def against(server: BuildId): Verdict =
        if !id.known || !server.known then Verdict.Unknown
        else if id.value == server.value then Verdict.Same
        else Verdict.Updated

  /** The response header that carries the server's [[BuildId]] on every answer, so an open page notices an upgrade at
    * its next request, not only when it loads.
    */
  val BuildHeader: String = "X-Histmersion-Build"
