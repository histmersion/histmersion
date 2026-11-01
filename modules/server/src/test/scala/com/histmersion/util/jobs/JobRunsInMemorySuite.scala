package com.histmersion.util.jobs

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class JobRunsInMemorySuite extends JobRunsContract("memory"):
  private val stores = collection.concurrent.TrieMap.empty[JobRuns, AtomicReference[Map[String, JobRunState]]]

  protected def fresh(test: String): Option[(JobRuns, Instant)] =
    val now = Instant.parse("2026-11-01T12:00:00Z")
    val state = AtomicReference(Map.empty[String, JobRunState])
    val r = JobRuns.InMemory(state, () => now)
    stores.update(r, state)
    Some((r, now))

  protected def row(runs: JobRuns, id: String): (String, Option[String], Option[String], Option[String]) =
    stores(runs).get()(id) match
      case s => (s.row.status, s.error, s.progress, Some(s.row.origin))
