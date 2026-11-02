package com.histmersion.domain.library.acquisition

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The db suites' writes of the acquisition's records on a database of their own, each in a transaction of its own as
  * the service runs them.
  */
object AcquisitionFixtures:
  def putAssessment(ds: DataSource, a: AssessmentRecord): String = transact(ds)(AssessmentRepo.putAssessment(a))
