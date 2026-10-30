package com.histmersion.api

/** The kinds of background job the server runs: the `kind` of a jobs row. Every one has a name for a person
  * ([[Wording.jobKind]]); the server's own constants are these wires, so a new kind starts here.
  */
enum JobKind(val wire: String):
  case Ingest extends JobKind("ingest")
  case Reocr extends JobKind("reocr")
  case Init extends JobKind("init")
  case MergeApply extends JobKind("merge-apply")
  case Vacuum extends JobKind("vacuum")
  case Agent extends JobKind("agent")
  case AcquireRecheck extends JobKind("acquire_recheck")
  case BasemapFetch extends JobKind("basemap-fetch")
  case PlacesFetch extends JobKind("places-fetch")
  case PlacesHierarchy extends JobKind("places-hierarchy")
  case EventsImport extends JobKind("events-import")
  case EventsCandidates extends JobKind("events-candidates")
  case EventsRederive extends JobKind("events-rederive")
  case SiteTypes extends JobKind("site-types")
  case MediaImport extends JobKind("media-import")
  case MediaCovers extends JobKind("media-covers")

object JobKind:
  def parse(s: String): Option[JobKind] = values.find(_.wire == s.trim.toLowerCase)
