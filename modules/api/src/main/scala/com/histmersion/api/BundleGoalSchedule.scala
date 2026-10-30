package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

final case class BundleGoalSchedule(kind: String, cron: String, enabled: Boolean, budgetUsd: Option[Double])
    derives KindlingsCodecAsObject
