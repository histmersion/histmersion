package com.histmersion
package api.catalogue
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning
import com.histmersion.api.jobs.JobDetail
import com.histmersion.lint.{LintRule, tolerated}

/** The gazetteer's jobs, as the catalogue registers them: a gazetteer's download, the place hierarchy, the sites'
  * types.
  */
object PlacesJobKinds:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val PlacesFetch: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("places-fetch"),
    JobKinds.Wire("Download a gazetteer"),
    JobKindMeaning("Downloads an external gazetteer, or Natural Earth land, and updates the places from it."),
    subject = d => JobDetail.Purpose.of(d).map(p => JobKinds.Subject(p.value))
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val PlacesHierarchy: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("places-hierarchy"),
    JobKinds.Wire("Work out which places lie inside which"),
    JobKindMeaning("Works out which places lie inside which (spatial containment), for the place hierarchy."),
    subject = d => JobDetail.Purpose.of(d).map(p => JobKinds.Subject(p.value))
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val SiteTypes: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("site-types"),
    JobKinds.Wire("Look up site types"),
    JobKindMeaning("Looks up on Wikidata what type of site each Wikidata site on the map is."),
    subject = d => EventsJobKinds.Catalogue.of(d).map(c => JobKinds.Subject(c.value))
  )
