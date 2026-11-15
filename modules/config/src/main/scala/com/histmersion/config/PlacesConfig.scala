package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

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
  def geoLimit(requested: Option[PlacesConfig.GeoLimit], dflt: PlacesConfig.GeoLimit): PlacesConfig.GeoLimit =
    val cap = geoMaxLimit.atLeastOne
    requested.filter(_.isPositive).getOrElse(dflt).atLeastOne.atMost(cap)

object PlacesConfig:
  type GeoLimit = GeoLimit.T
  object GeoLimit extends Newtype[Int, Pure]:
    extension (l: T)
      def isPositive: Boolean = l.value > 0
      def atLeastOne: T = GeoLimit(l.value.max(1))
      def atMost(cap: T): T = GeoLimit(l.value.min(cap.value))

  type PointToleranceKm = PointToleranceKm.T
  object PointToleranceKm extends Newtype[Double, Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends Newtype[Int, Pure]
  type ChildrenLimit = ChildrenLimit.T
  object ChildrenLimit extends Newtype[Int, Pure]
  type TreeLimit = TreeLimit.T
  object TreeLimit extends Newtype[Int, Pure]
  type TreeDepth = TreeDepth.T
  object TreeDepth extends Newtype[Int, Pure]
  type SeedDir = SeedDir.T
  object SeedDir extends Newtype[String, Pure]
  type ImportBatch = ImportBatch.T
  object ImportBatch extends Newtype[Int, Pure]
  type GeocodePlaceholderMin = GeocodePlaceholderMin.T
  object GeocodePlaceholderMin extends Newtype[Int, Pure]
  type GeocodeRepairKm = GeocodeRepairKm.T
  object GeocodeRepairKm extends Newtype[Double, Pure]
  type ModernFrom = ModernFrom.T
  object ModernFrom extends Newtype[Long, Pure]
  type PlaceholderAreaShare = PlaceholderAreaShare.T
  object PlaceholderAreaShare extends Newtype[Double, Pure]
  type FetchTimeoutSeconds = FetchTimeoutSeconds.T
  object FetchTimeoutSeconds extends Newtype[Int, Pure]
  type FetchConnectTimeoutSeconds = FetchConnectTimeoutSeconds.T
  object FetchConnectTimeoutSeconds extends Newtype[Int, Pure]
