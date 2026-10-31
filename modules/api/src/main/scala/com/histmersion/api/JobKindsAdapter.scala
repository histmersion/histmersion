package com.histmersion.api

import com.histmersion.util.jobs.{JobKindEntry, JobKindMeaning, JobKindName, JobKinds}
import com.histmersion.values.JobKind

/** Today's job kinds, registered in one table until each owner registers its own: every kind the server runs, its name
  * and meaning for a person, whether it can be paused and cancelled, and how its detail names what a job is about.
  */
object JobKindsAdapter:
  val Ingest: JobKind = JobKind("ingest")
  val Reocr: JobKind = JobKind("reocr")
  val Init: JobKind = JobKind("init")
  val MergeApply: JobKind = JobKind("merge-apply")
  val Vacuum: JobKind = JobKind("vacuum")
  val Agent: JobKind = JobKind("agent")
  val AcquireRecheck: JobKind = JobKind("acquire_recheck")
  val BasemapFetch: JobKind = JobKind("basemap-fetch")
  val PlacesFetch: JobKind = JobKind("places-fetch")
  val PlacesHierarchy: JobKind = JobKind("places-hierarchy")
  val EventsImport: JobKind = JobKind("events-import")
  val EventsCandidates: JobKind = JobKind("events-candidates")
  val EventsRederive: JobKind = JobKind("events-rederive")
  val SiteTypes: JobKind = JobKind("site-types")
  val MediaImport: JobKind = JobKind("media-import")
  val MediaCovers: JobKind = JobKind("media-covers")

  private def entry(
      kind: JobKind,
      name: String,
      meaning: String,
      stoppable: Boolean = false,
      subject: JobDetail => Option[String] = _ => None
  ): JobKindEntry = JobKindEntry(kind, JobKindName(name), JobKindMeaning(meaning), stoppable, subject)

  private val about: JobDetail => Option[String] = _.about
  private val events: JobDetail => Option[String] = _.events
  private val media: JobDetail => Option[String] = _.media

  val all: JobKinds = JobKinds(
    List(
      entry(
        Ingest,
        "Bring in a source",
        "Brings in a source: fetches its open-access file or takes your upload, converts it to text (OCR) and stores it."
      ),
      entry(Reocr, "Read a source's text again", "Converts a stored source's original file to text (OCR) again."),
      entry(
        Init,
        "Import reference data",
        "The first-run import of reference data: the bundled gazetteer and period presets, and the sources it fetches.",
        stoppable = true,
        subject = _.init.map(InitPlan.describe)
      ),
      entry(
        MergeApply,
        "Merge another collection",
        "Merges another collection's bundle into this one, after taking a backup.",
        subject = _.planHash.map(h => s"merge plan $h")
      ),
      entry(
        Vacuum,
        "Clean up the database",
        "Database clean-up (VACUUM): frees the space old row versions hold after a re-seed or a gazetteer load.",
        subject = _.vacuum.map(com.histmersion.api.Vacuum.describe)
      ),
      entry(Agent, "Agent run", "An agent run; the agent runs window shows its steps."),
      entry(
        AcquireRecheck,
        "Check what to acquire again",
        "Checks the to-acquire list again: which items have a legal open copy or are in the library now.",
        stoppable = true,
        subject = _.recheck.map(AcquireChecks.RecheckJob.describe)
      ),
      entry(
        BasemapFetch,
        "Download the background map",
        "Downloads and assembles base-map layers (vector map, terrain relief, sea floor) for an area.",
        stoppable = true,
        subject = about
      ),
      entry(
        PlacesFetch,
        "Download a gazetteer",
        "Downloads an external gazetteer, or Natural Earth land, and updates the places from it.",
        subject = about
      ),
      entry(
        PlacesHierarchy,
        "Work out which places lie inside which",
        "Works out which places lie inside which (spatial containment), for the place hierarchy.",
        subject = about
      ),
      entry(
        EventsImport,
        "Import events",
        "Imports the events of an external event catalogue.",
        stoppable = true,
        subject = events
      ),
      entry(
        EventsCandidates,
        "Find sources for events",
        "Looks for the sources that may attest each catalogue event: candidate links for you to confirm or reject.",
        subject = events
      ),
      entry(
        EventsRederive,
        "Re-date events",
        "Recomputes the dating of catalogue events from their stored records; nothing is downloaded.",
        subject = events
      ),
      entry(
        SiteTypes,
        "Look up site types",
        "Looks up on Wikidata what type of site each Wikidata site on the map is.",
        subject = events
      ),
      entry(
        MediaImport,
        "Fetch pictures",
        "Asks a picture provider (Wikidata, museums, the Library of Congress…) for pictures of places and events.",
        stoppable = true,
        subject = media
      ),
      entry(
        MediaCovers,
        "Fetch source covers",
        "Pictures of sources: Open Library covers by ISBN, and an illustration picked from a source's own figures.",
        stoppable = true,
        subject = media
      )
    )
  )
