package com.histmersion.api.catalogue.places

import com.histmersion.util.jobs.JobKindEntry
import com.histmersion.values.JobKind

/** The gazetteer's jobs, as the catalogue registers them: a gazetteer's download, the place hierarchy, the sites' types.
  */
object PlacesJobKinds:
  val PlacesFetch: JobKindEntry = JobKindEntry.of(
    JobKind("places-fetch"),
    "Download a gazetteer",
    "Downloads an external gazetteer, or Natural Earth land, and updates the places from it.",
    subject = _.about
  )

  val PlacesHierarchy: JobKindEntry = JobKindEntry.of(
    JobKind("places-hierarchy"),
    "Work out which places lie inside which",
    "Works out which places lie inside which (spatial containment), for the place hierarchy.",
    subject = _.about
  )

  val SiteTypes: JobKindEntry = JobKindEntry.of(
    JobKind("site-types"),
    "Look up site types",
    "Looks up on Wikidata what type of site each Wikidata site on the map is.",
    subject = _.events
  )
