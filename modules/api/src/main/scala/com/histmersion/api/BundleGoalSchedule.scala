package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** A goal's schedule (`goal_schedules`, V049): what it runs and when. The run-time state (next and last run, the last
  * note) is this machine's and is not carried.
  */
final case class BundleGoalSchedule(kind: String, cron: String, enabled: Boolean, budgetUsd: Option[Double])
    derives KindlingsCodecAsObject
