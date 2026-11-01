package com.histmersion.domain.automation

import com.histmersion.domain.automation.autopilot.AutopilotRowService
import com.histmersion.domain.automation.goals.GoalService
import com.augustnagro.magnum.DbCon

/** The engine features' services over one store — the database's scratch copy or the shared in-memory state — and how
  * a contract makes the rows of other domains a feature reads (an article, a source) and runs a service's `…In`
  * operation in a transaction of its own.
  */
trait EngineStores:
  def autopilot: AutopilotRowService
  def goals: GoalService
  def article(slug: String, title: String): Unit
  def source(citekey: String, title: String): Unit
  def within[A](work: DbCon ?=> A): A
