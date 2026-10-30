package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

/** Research goals and agent state (plan §1.5): server as single source of truth. */

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
) derives KindlingsCodecAsObject,
      KindlingsSchema

object Goal:
  given Schema[Goal] = summon[KindlingsSchema[Goal]].schema

final case class GoalPut(
    title: String,
    status: String = "planned",
    notes: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    parentSlug: Option[String] = None
) derives KindlingsSchema

object GoalPut:
  given Schema[GoalPut] = summon[KindlingsSchema[GoalPut]].schema

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
) derives KindlingsSchema

object GoalPatch:
  given Schema[GoalPatch] = summon[KindlingsSchema[GoalPatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[GoalPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPatch])

final case class GoalList(
    total: Int,
    goals: List[Goal],
    /** Where this page starts, and the offset of the next one (absent on the last) — [[OffsetPage]]. */
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def page: OffsetPage = OffsetPage(total, offset, goals.size)

object GoalList:
  given Schema[GoalList] = summon[KindlingsSchema[GoalList]].schema
  def of(total: Int, rows: List[Goal], offset: Int): GoalList =
    GoalList(total, rows, offset, OffsetPage(total, offset, rows.size).next)

final case class GoalDeleted(
    slug: String,
    deleted: Boolean
) derives KindlingsCodecAsObject,
      KindlingsSchema

object GoalDeleted:
  given Schema[GoalDeleted] = summon[KindlingsSchema[GoalDeleted]].schema

// ── agent state ────────────────────────────────────────────────────────────────────────────────

/** Value is stored as a JSON string — the server handles JSONB storage and round-trips it as a string through the wire
  * so Kindlings/tapir Schema derivation works without a custom Schema for io.circe.Json. The agent parses/emits JSON at
  * the CLI/MCP boundary.
  */
final case class AgentStateEntry(
    key: String,
    value: String,
    updatedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object AgentStateEntry:
  given Schema[AgentStateEntry] = summon[KindlingsSchema[AgentStateEntry]].schema

final case class AgentStatePut(
    value: String
) derives KindlingsSchema

object AgentStatePut:
  given Schema[AgentStatePut] = summon[KindlingsSchema[AgentStatePut]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[AgentStatePut] = StrictJson.codec(KindlingsCodecAsObject.derived[AgentStatePut])

final case class AgentStateDeleted(
    key: String,
    deleted: Boolean
) derives KindlingsCodecAsObject,
      KindlingsSchema

object AgentStateDeleted:
  given Schema[AgentStateDeleted] = summon[KindlingsSchema[AgentStateDeleted]].schema
