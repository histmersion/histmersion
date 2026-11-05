package com.histmersion.config

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
    pointToleranceKm: Double,
    searchLimit: Int,
    childrenLimit: Int,
    treeLimit: Int,
    treeDepth: Int,
    seedDir: String,
    importBatch: Int,
    geocodePlaceholderMin: Int,
    geocodeRepairKm: Double,
    modernFrom: Long,
    sources: PlaceSourcesConfig,
    placeholderAreaShare: Double,
    geoMaxLimit: Int,
    fetchTimeoutSeconds: Int,
    fetchConnectTimeoutSeconds: Int
) derives ConfigReader:
  /** `/api/places/geo`'s `limit` into `[1, geoMaxLimit]`; absent or `<= 0` is `dflt` (itself capped). */
  def geoLimit(requested: Option[Int], dflt: Int): Int =
    val cap = math.max(1, geoMaxLimit)
    requested.filter(_ > 0).getOrElse(dflt).max(1).min(cap)
