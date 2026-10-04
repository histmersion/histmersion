package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

/** Research goals and agent state (plan §1.5): server as single source of truth. */

object GoalStatus:
  val Planned  = "planned"
  val Seeding  = "seeding"
  val Compiled = "compiled"
  val Reviewed = "reviewed"
  val Done     = "done"
  val all: List[String] = List(Planned, Seeding, Compiled, Reviewed, Done)

final case class Goal(
    slug: String,
    title: String,
    status: String,
    notes: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    parentSlug: Option[String] = None,
    updatedAt: Option[String] = None
) derives KindlingsCodecAsObject

object Goal:
  given Schema[Goal] = KindlingsSchema.derived[Goal].schema

final case class GoalPut(
    title: String,
    status: String = "planned",
    notes: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    parentSlug: Option[String] = None
)

object GoalPut:
  given Schema[GoalPut] = KindlingsSchema.derived[GoalPut].schema
  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[GoalPut] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPut])

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
)

object GoalPatch:
  given Schema[GoalPatch] = KindlingsSchema.derived[GoalPatch].schema
  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[GoalPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPatch])

final case class GoalList(
    total: Int,
    goals: List[Goal]
) derives KindlingsCodecAsObject

object GoalList:
  given Schema[GoalList] = KindlingsSchema.derived[GoalList].schema

final case class GoalDeleted(
    slug: String,
    deleted: Boolean
) derives KindlingsCodecAsObject

object GoalDeleted:
  given Schema[GoalDeleted] = KindlingsSchema.derived[GoalDeleted].schema

// ── agent state ────────────────────────────────────────────────────────────────────────────────

/** Value is stored as a JSON string — the server handles JSONB storage and round-trips it as a
  * string through the wire so Kindlings/tapir Schema derivation works without a custom Schema for
  * io.circe.Json. The agent parses/emits JSON at the CLI/MCP boundary. */
final case class AgentStateEntry(
    key: String,
    value: String,
    updatedAt: Option[String] = None
) derives KindlingsCodecAsObject

object AgentStateEntry:
  given Schema[AgentStateEntry] = KindlingsSchema.derived[AgentStateEntry].schema

final case class AgentStatePut(
    value: String
)

object AgentStatePut:
  given Schema[AgentStatePut] = KindlingsSchema.derived[AgentStatePut].schema
  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[AgentStatePut] = StrictJson.codec(KindlingsCodecAsObject.derived[AgentStatePut])

final case class AgentStateDeleted(
    key: String,
    deleted: Boolean
) derives KindlingsCodecAsObject

object AgentStateDeleted:
  given Schema[AgentStateDeleted] = KindlingsSchema.derived[AgentStateDeleted].schema
