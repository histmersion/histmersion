package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** The gazetteer (plan §1.6a). `pointToleranceKm` is how close a finding must be to a POINT place (a Pleiades site has
  * no extent) to count as inside it; polygons use exact containment. The limits bound what `/api/places` and the
  * hierarchy walk return, and `seedDir` is where `histmersion places seed` looks for the committed Natural Earth /
  * Pleiades files on the HOST. The geocoder (#981, `db.Geocoder`) treats a point shared by `geocodePlaceholderMin` or
  * more places as a placeholder, and the `finding-geocode` repair moves only points that end up more than
  * `geocodeRepairKm` away. `modernFrom` is the year from which a time-filtered view shows present-day places
  * ([[PlaceTimeBasis.Modern]]: the Natural Earth cities, undated GeoNames/Wikidata places). `placeholderAreaShare`: a
  * finding whose point is a placeholder belongs to the areas covering at least this share of the place its name
  * resolves to (`db.PlaceRepo.insertAssignments`). `geoMaxLimit` caps the map's area and site feed (`/api/places/geo`)
  * apart from `limits.max-page`: over a whole range the area layer draws every state alive in it (1 744 for
  * -3000..2000), each a stored simplified outline (V090). `fetchTimeoutSeconds` and `fetchConnectTimeoutSeconds` bound
  * a network gazetteer's download (the answer, the connection).
  */
final case class PlacesConfig(
    pointToleranceKm: PlacesConfig.PointToleranceKm,
    searchLimit: PlacesConfig.SearchLimit,
    childrenLimit: PlacesConfig.ChildrenLimit,
    treeLimit: PlacesConfig.TreeLimit,
    treeDepth: PlacesConfig.TreeDepth,
    seedDir: PlacesConfig.SeedDir,
    importBatch: PlacesConfig.ImportBatch,
    geocodePlaceholderMin: PlacesConfig.GeocodePlaceholderMin,
    geocodeRepairKm: PlacesConfig.GeocodeRepairKm,
    modernFrom: PlacesConfig.ModernFrom,
    sources: PlaceSourcesConfig,
    placeholderAreaShare: PlacesConfig.PlaceholderAreaShare,
    geoMaxLimit: PlacesConfig.GeoLimit,
    fetchTimeoutSeconds: PlacesConfig.FetchTimeoutSeconds,
    fetchConnectTimeoutSeconds: PlacesConfig.FetchConnectTimeoutSeconds
) derives ConfigReader:
  /** `/api/places/geo`'s `limit` into `[1, geoMaxLimit]`; absent or `<= 0` is `dflt` (itself capped). */
  def geoLimit(requested: Option[com.histmersion.config.PlacesConfig.GeoLimit], dflt: com.histmersion.config.PlacesConfig.GeoLimit): com.histmersion.config.PlacesConfig.GeoLimit =
    val cap = geoMaxLimit.atLeastOne
    requested.filter(_.isPositive).getOrElse(dflt).atLeastOne.atMost(cap)

object PlacesConfig:
  type GeoLimit = GeoLimit.T
  object GeoLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    extension (l: T)
      def isPositive: Boolean = l.value > 0
      def atLeastOne: T = GeoLimit(l.value.max(1))
      def atMost(cap: T): T = GeoLimit(l.value.min(cap.value))

  type PointToleranceKm = PointToleranceKm.T
  object PointToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChildrenLimit = ChildrenLimit.T
  object ChildrenLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeLimit = TreeLimit.T
  object TreeLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeDepth = TreeDepth.T
  object TreeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedDir = SeedDir.T
  object SeedDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportBatch = ImportBatch.T
  object ImportBatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeocodePlaceholderMin = GeocodePlaceholderMin.T
  object GeocodePlaceholderMin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeocodeRepairKm = GeocodeRepairKm.T
  object GeocodeRepairKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModernFrom = ModernFrom.T
  object ModernFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceholderAreaShare = PlaceholderAreaShare.T
  object PlaceholderAreaShare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchTimeoutSeconds = FetchTimeoutSeconds.T
  object FetchTimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchConnectTimeoutSeconds = FetchConnectTimeoutSeconds.T
  object FetchConnectTimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
