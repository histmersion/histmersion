package com.histmersion.server

import com.histmersion.domain.library.sources.{SourceMedia, SourceService}
import com.histmersion.domain.media.{MediaJobs, MediaSources}
import com.histmersion.domain.media.assets.{AssetsControllers, AssetService, IiifControllers, MediaMapLayer}
import com.histmersion.domain.media.covers.{CoversControllers, CoverService, SourcePictures}
import com.histmersion.domain.media.figures.{FiguresControllers, FigureService}
import com.histmersion.domain.media.imports.{ImportsControllers, ImportService, MediaStore}
import com.histmersion.domain.media.links.{AssetLinksControllers, AssetLinkService}
import com.histmersion.pluginhost.{HttpTransport, JavaHttpTransport}
import com.histmersion.util.ocr.ConverterClient

/** Media's services and controllers, built over one server's `deps`: `http` fetches the providers' pictures and Open
  * Library's covers, `converter` makes thumbnails; the sources its figures and covers belong to are the library's.
  */
final class MediaWiring(deps: Deps, http: HttpTransport, converter: ConverterClient):
  def this(deps: Deps, converter: ConverterClient) =
    this(deps, JavaHttpTransport(deps.cfg.media.timeoutSeconds), converter)
  def this(deps: Deps, http: HttpTransport) = this(deps, http, MediaWiring.NoThumbnails)
  def this(deps: Deps) = this(deps, MediaWiring.NoThumbnails)

  /** The pictures and where they are shown. */
  val assetStore: AssetService = AssetService(deps.transactor)

  /** What the imports ask about and what they recorded asking. */
  val importStore: ImportService = ImportService(deps.transactor)

  /** The sources the figures and covers belong to. */
  val sources: MediaSources =
    SourceMedia(SourceService(deps.transactor, Deps.sourceReferrers, () => LibraryWiring.settings(deps.cfg)))

  /** One media run at a time. */
  val jobs: MediaJobs = MediaJobs(deps.ds, deps.maintenance, deps.pins, deps.jobStops)

  val assets: AssetsControllers = AssetsControllers(assetStore, sources, () => deps.cfg, deps.maintenance)

  val iiif: IiifControllers = IiifControllers(assetStore, () => deps.cfg.server.publicUrl.stripSuffix("/"))

  val links: AssetLinksControllers =
    AssetLinksControllers(
      AssetLinkService(deps.transactor),
      (k, id) => Deps.aliasTables.current(deps.ds, k, id),
      deps.maintenance
    )

  val imports: ImportsControllers =
    ImportsControllers(
      importStore,
      MediaStore(assetStore, importStore),
      jobs,
      () =>
        val gen = deps.plugins
        (
          gen.registries.mediaCollections,
          gen.contributions.toList.flatMap((id, r) => r.mediaCollections.keys.map(_ -> id)).toMap
        )
      ,
      () => deps.cfg.media,
      http,
      converter,
      deps.clock
    )

  /** The sources' covers and illustrations. */
  val pictures: SourcePictures =
    SourcePictures(
      assetStore,
      CoverService(deps.transactor),
      importStore,
      sources,
      () => deps.cfg.media,
      http,
      converter
    )

  val covers: CoversControllers = CoversControllers(pictures, jobs)

  val figures: FiguresControllers = FiguresControllers(FigureService(deps.transactor), deps.maintenance)

  /** The pictures as a layer of the map. */
  val mapLayer: MediaMapLayer = MediaMapLayer(assetStore)

object MediaWiring:

  /** A converter that makes no thumbnails (tests; a server whose sidecar is down still serves cards). */
  object NoThumbnails extends ConverterClient:
    type Health = Health.T
    object Health extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ConvertFilename = ConvertFilename.T
    object ConvertFilename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ConvertQuality = ConvertQuality.T
    object ConvertQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def convert(
        file: com.histmersion.util.storage.Blob,
        filename: com.histmersion.server.MediaWiring.NoThumbnails.ConvertFilename,
        quality: com.histmersion.server.MediaWiring.NoThumbnails.ConvertQuality
    ): Either[com.histmersion.server.MediaWiring.NoThumbnails.Problem, com.histmersion.api.ocr.ConvertedDoc] =
      Left("no converter")
    def health: Either[com.histmersion.server.MediaWiring.NoThumbnails.Problem, com.histmersion.server.MediaWiring.NoThumbnails.Health] = Left("no converter")
