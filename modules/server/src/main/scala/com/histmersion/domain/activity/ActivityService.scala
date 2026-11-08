package com.histmersion
package domain.activity

import com.histmersion.api.automation.Activity

import com.augustnagro.magnum.connect

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** Research › Activity: what the agents, the server and the person did, one list, newest first. */
trait ActivityService:
  /** One page of `q`; the chips' counts are measured at `now`. */
  def page(q: ActivityQuery, now: Instant): Activity.Page

object ActivityService:
  /** How far back the problems the chip counts go. */
  val ProblemsWindow: java.time.Duration = java.time.Duration.ofDays(7)

  def apply(ds: DataSource): ActivityService = Implementation(ds)

  final private class Implementation(ds: DataSource) extends ActivityService:
    def page(q: ActivityQuery, now: Instant): Activity.Page =
      connect(ds):
        val (entries, next) = ActivityRepo.page(q)
        Activity.Page(entries, next.map(w => Activity.Page.Next(w.value)), ActivityRepo.counts(now.minus(ProblemsWindow)))

  /** The same list over entries held in memory (tests): written with [[add]], as the store's tables write them.
    */
  final class InMemory(entries: AtomicReference[List[Activity.Entry]] = AtomicReference(Nil)) extends ActivityService:
    def add(e: Activity.Entry): Unit = entries.updateAndGet(e :: _): Unit

    def page(q: ActivityQuery, now: Instant): Activity.Page =
      val all = entries.get
      ActivityRules.page(all, q, ActivityRules.counts(all, now.minus(ProblemsWindow)))
