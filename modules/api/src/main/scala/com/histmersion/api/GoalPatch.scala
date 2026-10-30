package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

/** Research goals and agent state (plan §1.5): server as single source of truth. */

final case class GoalPatch(
    title: Option[String] = None,
    status: Option[String] = None,
    notes: Option[String] = None,
    tags: Option[List[String]] = None,
    regions: Option[List[String]] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    parentSlug: Option[String] = None,
    /** The cost cap of the runs started for this goal (USD; 0 clears it — the server default applies). */
    budgetUsd: Option[Double] = None
) derives KindlingsSchema

object GoalPatch:
  given Schema[GoalPatch] = summon[KindlingsSchema[GoalPatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[GoalPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPatch])
