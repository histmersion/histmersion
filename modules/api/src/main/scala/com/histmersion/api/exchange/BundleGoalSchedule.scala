package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import com.histmersion.api.given

final case class BundleGoalSchedule(kind: String, cron: String, enabled: Boolean, budgetUsd: Option[Double])
    derives KindlingsCodecAsObject
