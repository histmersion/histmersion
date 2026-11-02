package com.histmersion.api

import com.histmersion.api.automation.AgentJobKinds
import com.histmersion.api.catalogue.EventsJobKinds
import com.histmersion.api.catalogue.EvidenceJobKinds
import com.histmersion.api.catalogue.PlacesJobKinds
import com.histmersion.api.library.AcquisitionJobKinds
import com.histmersion.api.library.IngestJobKinds
import com.histmersion.api.media.CoversJobKinds
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.api.basemap.BasemapJobKinds
import com.histmersion.api.exchange.ExchangeJobKinds
import com.histmersion.api.maintenance.MaintenanceJobKinds
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.catalogue.{EventImportReport, EventRederiveReport, SkippedPeriod}
import com.histmersion.api.exchange.InitPlan
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.maintenance.VacuumPlan
import com.histmersion.api.library.Acquire

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
      Acquire.Checks.RecheckJob.Detail
    )
  )
