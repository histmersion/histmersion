package com.histmersion
package util.jobs

import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable}
import com.histmersion.util.data.diagnostics.{DataCount, DataFigures}
import com.augustnagro.magnum.DbCon

/** What the jobs area says about the data it owns: the jobs table, counted by state for About's job queue. */
object JobsData extends DataContribution:
  val owner: DataOwner = DataOwner("util.jobs")

  val tables: List[OwnedTable] = OwnedTable.all("jobs")

  def figures(using DbCon): DataFigures = DataFigures(JobRepo.states.map((state, n) => DataCount(DataCount.Noun(state.value), DataCount.Count(n.value.toLong))))
