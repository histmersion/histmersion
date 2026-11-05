package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.syntax.*
import sttp.tapir.Schema
import com.histmersion.api.given

final case class Correction(
    id: Correction.Id,
    targetKind: Correction.TargetKind,
    targetId: Correction.TargetId,
    field: Correction.Field,
    value: Correction.Value,
    upstreamValue: Correction.UpstreamValue,
    status: Correction.StatusValue,
    upstream: Option[Correction.Upstream] = None,
    upstreamNow: Option[Correction.UpstreamNow] = None,
    label: Option[Correction.Label] = None,
    createdAt: Correction.CreatedAt = "",
    checkedAt: Option[Correction.CheckedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object Correction:


  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetKind = TargetKind.T
  object TargetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetId = TargetId.T
  object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Field = Field.T
  object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Value = Value.T
  object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpstreamValue = UpstreamValue.T
  object UpstreamValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusValue = StatusValue.T
  object StatusValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Upstream = Upstream.T
  object Upstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpstreamNow = UpstreamNow.T
  object UpstreamNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedAt = CheckedAt.T
  object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Counts(kept: Counts.Kept = 0, retired: Counts.Retired = 0, conflicts: Counts.Conflicts = 0)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def +(o: Counts): Counts =
      Counts(kept + o.kept, retired + o.retired, conflicts + o.conflicts)
    def isEmpty: Boolean = kept == 0 && retired == 0 && conflicts == 0

    def summary: Option[String] =
      Option.when(!isEmpty)(
        s"corrections by hand: $kept kept, $retired retired (upstream agrees), $conflicts in conflict"
      )

  object Counts:
    type Kept = Kept.T
    object Kept extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Retired = Retired.T
    object Retired extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Conflicts = Conflicts.T
    object Conflicts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class List(total: List.Total, corrections: scala.List[Correction]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object List:
    type Total = Total.T
    object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


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
