package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.automation.worklists.QueueService
import com.histmersion.domain.library.sources.SourceService

/** The acquisition's services one contract runs on, with the library's sources and the work lists they read. */
trait AcquisitionStores:
  def assessments: AssessmentService
  def candidates: CandidateService
  def quotas: UpstreamQuotaService
  def attempts: AcquireCheckService
  def sources: SourceService
  def queues: QueueService

  /** A research goal the candidates belong to: the automation's record on the database; nothing in memory, where the
    * candidates hold no goal to check.
    */
  def goal(slug: String): Unit
