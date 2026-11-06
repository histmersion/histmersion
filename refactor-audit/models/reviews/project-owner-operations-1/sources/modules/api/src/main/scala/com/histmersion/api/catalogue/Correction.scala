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
    createdAt: Correction.CreatedAt = _root_.com.histmersion.api.catalogue.Correction.CreatedAt(""),
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

  final case class Counts(kept: Counts.Kept = _root_.com.histmersion.api.catalogue.Correction.Counts.Kept(0), retired: Counts.Retired = _root_.com.histmersion.api.catalogue.Correction.Counts.Retired(0), conflicts: Counts.Conflicts = _root_.com.histmersion.api.catalogue.Correction.Counts.Conflicts(0))
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def +(o: Counts): Counts =
      Counts((kept).value + o.kept, (retired).value + o.retired, (conflicts).value + o.conflicts)
    def isEmpty: com.histmersion.api.catalogue.Correction.Counts.IsEmptyResult = kept == 0 && retired == 0 && conflicts == 0

    def summary: Option[com.histmersion.api.catalogue.Correction.Counts.SummaryResult] =
      Option.when(!(isEmpty).value)(
        s"corrections by hand: $kept kept, $retired retired (upstream agrees), $conflicts in conflict"
      )

  object Counts:
    type IsEmptyResult = IsEmptyResult.T
    object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SummaryResult = SummaryResult.T
    object SummaryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Applied = Applied.T
    object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Active = "active"
    val Agreed = "agreed"
    val Conflict = "conflict"
    val all: scala.List[com.histmersion.api.catalogue.Correction.Status.All] = scala.List(_root_.com.histmersion.api.catalogue.Correction.Status.All(Active), _root_.com.histmersion.api.catalogue.Correction.Status.All(Agreed), _root_.com.histmersion.api.catalogue.Correction.Status.All(Conflict))

    val applied: Set[com.histmersion.api.catalogue.Correction.Status.Applied] = Set(_root_.com.histmersion.api.catalogue.Correction.Status.Applied(Active), _root_.com.histmersion.api.catalogue.Correction.Status.Applied(Conflict))

  object Target:
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Event = "event"
    val Place = "place"
    val all: scala.List[com.histmersion.api.catalogue.Correction.Target.All] = scala.List(_root_.com.histmersion.api.catalogue.Correction.Target.All(Event), _root_.com.histmersion.api.catalogue.Correction.Target.All(Place))
