package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.syntax.*
import sttp.tapir.Schema
import com.histmersion.api.given

final case class Correction(
    id: Long,
    targetKind: String,
    targetId: String,
    field: String,
    value: String,
    upstreamValue: String,
    status: String,
    upstream: Option[String] = None,
    upstreamNow: Option[String] = None,
    label: Option[String] = None,
    createdAt: String = "",
    checkedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object Correction:
  given Schema[Correction] = summon[KindlingsSchema[Correction]].schema

  final case class Counts(kept: Int = 0, retired: Int = 0, conflicts: Int = 0)
      derives KindlingsCodecAsObject,
        KindlingsSchema:
    def +(o: Counts): Counts =
      Counts(kept + o.kept, retired + o.retired, conflicts + o.conflicts)
    def isEmpty: Boolean = kept == 0 && retired == 0 && conflicts == 0

    def summary: Option[String] =
      Option.when(!isEmpty)(
        s"corrections by hand: $kept kept, $retired retired (upstream agrees), $conflicts in conflict"
      )
  object Counts:
    given Schema[Counts] = summon[KindlingsSchema[Counts]].schema

  final case class List(total: Int, corrections: scala.List[Correction]) derives KindlingsCodecAsObject, KindlingsSchema
  object List:
    given Schema[List] = summon[KindlingsSchema[List]].schema

  object Status:
    val Active = "active"
    val Agreed = "agreed"
    val Conflict = "conflict"
    val all: scala.List[String] = scala.List(Active, Agreed, Conflict)

    val applied: Set[String] = Set(Active, Conflict)

  object Target:
    val Event = "event"
    val Place = "place"
    val all: scala.List[String] = scala.List(Event, Place)
