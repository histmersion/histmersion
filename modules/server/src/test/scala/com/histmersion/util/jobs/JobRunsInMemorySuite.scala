package com.histmersion
package util.jobs
import com.histmersion.util.problem.Problem

import com.histmersion.domain.automation.Run

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class JobRunsInMemorySuite extends JobRunsContract("memory"):
  private val stores = collection.concurrent.TrieMap.empty[JobRuns, AtomicReference[Map[Run.Id, JobRunState]]]

  protected def fresh(test: String): Option[(JobRuns, Instant)] =
    val now = Instant.parse("2026-11-01T12:00:00Z")
    val state = AtomicReference(Map.empty[Run.Id, JobRunState])
    val r = JobRuns.InMemory(state, () => now)
    stores.update(r, state)
    Some((r, now))

  protected def row(
      runs: JobRuns,
      id: Run.Id
  ): (RunRow.Status, Option[Problem], Option[JobRunState.Progress], Option[RunRow.Origin]) =
    stores(runs).get()(id) match
      case s => (s.row.status, s.error, s.progress, Some(s.row.origin))
