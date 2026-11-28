package com.histmersion
package api.catalogue

import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object Correction:

  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type TargetKind = TargetKind.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TargetKind extends Newtype[String, Pure]
  type TargetId = TargetId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TargetId extends Newtype[String, Pure]
  type Field = Field.T
  object Field extends Newtype[String, Pure]
  type Value = Value.T
  object Value extends Newtype[String, Pure]
  type UpstreamValue = UpstreamValue.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object UpstreamValue extends Newtype[String, Pure]
  type StatusValue = StatusValue.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object StatusValue extends Newtype[String, Pure]
  type Upstream = Upstream.T
  object Upstream extends Newtype[String, Pure]
  type UpstreamNow = UpstreamNow.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object UpstreamNow extends Newtype[String, Pure]
  export CatalogRecord.Label
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type CheckedAt = CheckedAt.T
  object CheckedAt extends Newtype[String, Pure]

  final case class Counts(
      kept: Counts.Kept = Counts.Kept(0),
      retired: Counts.Retired = Counts.Retired(0),
      conflicts: Counts.Conflicts = Counts.Conflicts(0)
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def +(o: Counts): Counts =
      Counts(
        Counts.Kept((kept).value + (o.kept).value),
        Counts.Retired((retired).value + (o.retired).value),
        Counts.Conflicts((conflicts).value + (o.conflicts).value)
      )
    def isEmpty: Boolean = (kept == Counts.Kept(0) && retired == Counts.Retired(0) && conflicts == Counts.Conflicts(0))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  final case class List(total: List.Total, corrections: scala.List[Correction])
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

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
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val all: scala.List[All] = scala.List(All(Active), All(Agreed), All(Conflict))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val applied: Set[Applied] = Set(Applied(Active), Applied(Conflict))

  object Target:

    val Event = "event"
    val Place = "place"
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val all: scala.List[TargetKind] = scala.List(TargetKind(Event), TargetKind(Place))

  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  enum Outcome derives CanEqual:
    case Keep

    case Retire

    case Conflict

  def reconcile(value: Json, upstreamThen: Json, upstreamNow: Json): Outcome =
    if upstreamNow == value then Outcome.Retire
    else if upstreamNow == upstreamThen then Outcome.Keep
    else Outcome.Conflict

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def statusOf(o: Outcome): StatusValue = o match
    case Outcome.Keep     => StatusValue(Status.Active)
    case Outcome.Retire   => StatusValue(Status.Agreed)
    case Outcome.Conflict => StatusValue(Status.Conflict)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def counts(os: Iterable[Outcome]): Counts =
    Counts(
      Counts.Kept(os.count(_ == Outcome.Keep)),
      Counts.Retired(os.count(_ == Outcome.Retire)),
      Counts.Conflicts(os.count(_ == Outcome.Conflict))
    )

  def show(json: String): String =
    RawJson.parse(json).toOption match
      case Some(j) if j.isNull => ("none")
      case Some(j)             => (j.asString.getOrElse(j.noSpaces))
      case None                => (json)

  extension (c: Correction)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    def describe: String =
      val base = s"${c.field}: “${Fmt.clip(show(c.value.value))}” (was “${Fmt.clip(show(c.upstreamValue.value))}”" +
        c.upstream.fold("")(u => s" in $u") + ")"
      (c.status.value: String) match
        case Status.Conflict =>
          s"$base — CONFLICT: the catalogue now says “${Fmt.clip(c.upstreamNow.map(u => show(u.value)).getOrElse("?"))}”"
        case Status.Agreed => (s"$base — retired: the catalogue now agrees")
        case _             => base

  private object Fmt:

    def clip(s: String): String = if (s).length > 80 then ((s).take(79) + "…") else ((s))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdown(l: List): Markdown =
    if l.corrections.isEmpty then Markdown("_No corrections by hand._")
    else
      val lines =
        l.corrections.map(c => s"- **${c.targetId}**${c.label.fold("")(t => s" ($t)")} · ${c.status} · ${c.describe}")
      Markdown((s"### ${l.total} correction(s) by hand" :: "" :: lines).mkString("\n"))
