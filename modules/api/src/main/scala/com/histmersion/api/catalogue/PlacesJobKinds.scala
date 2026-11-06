package com.histmersion.api.catalogue
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** The gazetteer's jobs, as the catalogue registers them: a gazetteer's download, the place hierarchy, the sites'
  * types.
  */
object PlacesJobKinds:
  val PlacesFetch: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("places-fetch"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Download a gazetteer"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("Downloads an external gazetteer, or Natural Earth land, and updates the places from it."),
    subject = d => com.histmersion.api.jobs.JobDetail.Purpose.of(d).map(p => _root_.com.histmersion.api.jobs.JobKinds.Subject(p.value))
  )

  val PlacesHierarchy: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("places-hierarchy"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Work out which places lie inside which"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("Works out which places lie inside which (spatial containment), for the place hierarchy."),
    subject = d => com.histmersion.api.jobs.JobDetail.Purpose.of(d).map(p => _root_.com.histmersion.api.jobs.JobKinds.Subject(p.value))
  )

  val SiteTypes: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("site-types"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Look up site types"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("Looks up on Wikidata what type of site each Wikidata site on the map is."),
    subject = d => com.histmersion.api.catalogue.EventsJobKinds.Catalogue.of(d).map(c => _root_.com.histmersion.api.jobs.JobKinds.Subject(c.value))
  )
