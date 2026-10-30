package com.histmersion.api

/** One (entity, shape, validity) triple. The SAME name with DIFFERENT borders over time is several `Place` rows sharing
  * a [[PlaceName]]. `geometry` is GeoJSON text (`None` = hierarchy-only place, e.g. a continent whose extent is the
  * union of its members); `properties` is the importer's left-over JSON object, kept verbatim so an import round-trips.
  */
final case class Place(
    id: String,
    label: String,
    kind: String,
    geometry: Option[String] = None,
    period: Option[Period] = None,
    source: Option[String] = None,
    properties: Option[String] = None,
    /** The side of `period` its source left open ([[OpenSide]]). */
    open: Option[OpenSide] = None
):
  /** The inclusive bounds stored for it ([[OpenSide.bounds]]). */
  def bounds: (Option[Long], Option[Long]) = OpenSide.bounds(period, open)
