package com.histmersion.api

import com.histmersion.api.automation.agents.AgentJobKinds
import com.histmersion.api.catalogue.events.EventsJobKinds
import com.histmersion.api.catalogue.evidence.EvidenceJobKinds
import com.histmersion.api.catalogue.places.PlacesJobKinds
import com.histmersion.api.library.acquisition.AcquisitionJobKinds
import com.histmersion.api.library.ingest.IngestJobKinds
import com.histmersion.api.media.covers.CoversJobKinds
import com.histmersion.api.media.imports.ImportJobKinds
import com.histmersion.util.basemap.BasemapJobKinds
import com.histmersion.util.data.exchange.ExchangeJobKinds
import com.histmersion.util.data.maintenance.MaintenanceJobKinds
import com.histmersion.util.jobs.JobKinds

/** Every kind of background job the server runs, each registered by its owner, in the order the jobs window has always
  * listed them (as `Api.all` lists the features' endpoints): what the server, the web page and the CLI name a job by;
  * and the fields of a job's detail, each declared by its owner, in the order the detail has always shown them.
  */
object RegisteredJobKinds:
  val all: JobKinds = JobKinds(
    List(
      IngestJobKinds.Ingest,
      IngestJobKinds.Reocr,
      ExchangeJobKinds.Init,
      ExchangeJobKinds.MergeApply,
      MaintenanceJobKinds.Vacuum,
      AgentJobKinds.Agent,
      AcquisitionJobKinds.AcquireRecheck,
      BasemapJobKinds.BasemapFetch,
      PlacesJobKinds.PlacesFetch,
      PlacesJobKinds.PlacesHierarchy,
      EventsJobKinds.EventsImport,
      EvidenceJobKinds.EventsCandidates,
      EventsJobKinds.EventsRederive,
      PlacesJobKinds.SiteTypes,
      ImportJobKinds.MediaImport,
      CoversJobKinds.MediaCovers
    ),
    List(
      InitPlan.Detail,
      SkippedPeriod.Skipped,
      SkippedPeriod.SkippedCount,
      ExchangeJobKinds.PlanHash,
      VacuumPlan.Detail,
      ImportJobKinds.Media,
      EventsJobKinds.Catalogue,
      EventImportReport.Detail,
      EventRederiveReport.Detail,
      JobDetail.Purpose,
      AcquireChecks.RecheckJob.Detail
    )
  )
