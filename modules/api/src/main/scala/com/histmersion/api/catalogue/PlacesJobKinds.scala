package com.histmersion.api.catalogue
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** The gazetteer's jobs, as the catalogue registers them: a gazetteer's download, the place hierarchy, the sites'
  * types.
  */
object PlacesJobKinds:
  val PlacesFetch: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("places-fetch"),
    "Download a gazetteer",
    "Downloads an external gazetteer, or Natural Earth land, and updates the places from it.",
    subject = com.histmersion.api.jobs.JobDetail.Purpose.of
  )

  val PlacesHierarchy: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("places-hierarchy"),
    "Work out which places lie inside which",
    "Works out which places lie inside which (spatial containment), for the place hierarchy.",
    subject = com.histmersion.api.jobs.JobDetail.Purpose.of
  )

  val SiteTypes: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("site-types"),
    "Look up site types",
    "Looks up on Wikidata what type of site each Wikidata site on the map is.",
    subject = com.histmersion.api.catalogue.EventsJobKinds.Catalogue.of
  )
