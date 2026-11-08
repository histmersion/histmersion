package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.syntax.*
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

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
    createdAt: Correction.CreatedAt = Correction.CreatedAt(""),
    checkedAt: Option[Correction.CheckedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object Correction:


  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type TargetKind = TargetKind.T
  object TargetKind extends Newtype[String, Pure]
  type TargetId = TargetId.T
  object TargetId extends Newtype[String, Pure]
  type Field = Field.T
  object Field extends Newtype[String, Pure]
  type Value = Value.T
  object Value extends Newtype[String, Pure]
  type UpstreamValue = UpstreamValue.T
  object UpstreamValue extends Newtype[String, Pure]
  type StatusValue = StatusValue.T
  object StatusValue extends Newtype[String, Pure]
  type Upstream = Upstream.T
  object Upstream extends Newtype[String, Pure]
  type UpstreamNow = UpstreamNow.T
  object UpstreamNow extends Newtype[String, Pure]
  export CatalogRecord.Label
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type CheckedAt = CheckedAt.T
  object CheckedAt extends Newtype[String, Pure]

  final case class Counts(kept: Counts.Kept = Counts.Kept(0), retired: Counts.Retired = Counts.Retired(0), conflicts: Counts.Conflicts = Counts.Conflicts(0))
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def +(o: Counts): Counts =
      Counts(Counts.Kept((kept).value + (o.kept).value), Counts.Retired((retired).value + (o.retired).value), Counts.Conflicts((conflicts).value + (o.conflicts).value))
    def isEmpty: Boolean = (kept == Counts.Kept(0) && retired == Counts.Retired(0) && conflicts == Counts.Conflicts(0))

    def summary: Option[String] =
      Option.when(!(isEmpty))(
        (s"corrections by hand: $kept kept, $retired retired (upstream agrees), $conflicts in conflict")
      )

  object Counts:

    type Kept = Kept.T
    object Kept extends Newtype[Int, Pure]
    type Retired = Retired.T
    object Retired extends Newtype[Int, Pure]
    type Conflicts = Conflicts.T
    object Conflicts extends Newtype[Int, Pure]


  final case class List(total: List.Total, corrections: scala.List[Correction]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object List:
    type Total = Total.T
    object Total extends Newtype[Int, Pure]


  object Status:
    type All = All.T
    object All extends Newtype[String, Pure]
    type Applied = Applied.T
    object Applied extends Newtype[String, Pure]

    val Active = "active"
    val Agreed = "agreed"
    val Conflict = "conflict"
    val all: scala.List[All] = scala.List(All(Active), All(Agreed), All(Conflict))

    val applied: Set[Applied] = Set(Applied(Active), Applied(Conflict))

  object Target:

    val Event = "event"
    val Place = "place"
    val all: scala.List[TargetKind] = scala.List(TargetKind(Event), TargetKind(Place))
