package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Json
import io.circe.syntax.*
import sttp.tapir.Schema
import com.histmersion.values.Historicity

/** CORRECTIONS BY HAND that survive a re-import (V088, the data-source assessment's §B.4): a person changed one FIELD
  * of something an importer wrote, and the next import must neither lose the change nor keep it blindly. Each
  * correction records the person's value AND what upstream said when it was made; on every refresh
  * [[Corrections.reconcile]] compares that with what upstream says now:
  *
  * | upstream now says             | outcome                                           |
  * |:------------------------------|:--------------------------------------------------|
  * | what it said then (unchanged) | keep applying the correction (`active`)           |
  * | the person's value            | retire it (`agreed`): upstream adopted the change |
  * | anything else                 | keep applying it, flagged `conflict` for review   |
  *
  * Generic over the TARGET (`event` now; `place` is the next one — `place_overrides` is meant to become its
  * canonical-target form): a correction names its target kind, its id and the field, and values are JSON, compared as
  * JSON.
  */
object CorrectionTarget:
  val Event = "event"
  val Place = "place"
  val all: List[String] = List(Event, Place)

object CorrectionStatus:
  val Active = "active"
  val Agreed = "agreed"
  val Conflict = "conflict"
  val all: List[String] = List(Active, Agreed, Conflict)

  /** The statuses still applied over upstream. */
  val applied: Set[String] = Set(Active, Conflict)

/** One correction, as listed (`histmersion events corrections`, an event's card). Values are JSON text. `upstreamNow`
  * is what upstream said at the last refresh (`None`: not refreshed since).
  */
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
) derives KindlingsCodecAsObject
object Correction:
  given Schema[Correction] = KindlingsSchema.derived[Correction].schema

final case class CorrectionList(total: Int, corrections: List[Correction]) derives KindlingsCodecAsObject
object CorrectionList:
  given Schema[CorrectionList] = KindlingsSchema.derived[CorrectionList].schema

/** What a refresh did with the corrections it met: kept applying, retired, newly in conflict. */
final case class CorrectionCounts(kept: Int = 0, retired: Int = 0, conflicts: Int = 0) derives KindlingsCodecAsObject:
  def +(o: CorrectionCounts): CorrectionCounts =
    CorrectionCounts(kept + o.kept, retired + o.retired, conflicts + o.conflicts)
  def isEmpty: Boolean = kept == 0 && retired == 0 && conflicts == 0

  /** Pure: the counts in words, `None` when there were none. */
  def summary: Option[String] =
    Option.when(!isEmpty)(
      s"corrections by hand: $kept kept, $retired retired (upstream agrees), $conflicts in conflict"
    )
object CorrectionCounts:
  given Schema[CorrectionCounts] = KindlingsSchema.derived[CorrectionCounts].schema

object Corrections:

  enum Outcome:
    /** Upstream still says what it said: the correction keeps applying. */
    case Keep

    /** Upstream now says what the person said: the correction is retired. */
    case Retire

    /** Upstream changed to something else: the correction keeps applying and is flagged. */
    case Conflict

  /** Pure: the outcome of a refresh for one correction (the table above). */
  def reconcile(value: Json, upstreamThen: Json, upstreamNow: Json): Outcome =
    if upstreamNow == value then Outcome.Retire
    else if upstreamNow == upstreamThen then Outcome.Keep
    else Outcome.Conflict

  /** Pure: the status an outcome leaves. */
  def status(o: Outcome): String = o match
    case Outcome.Keep     => CorrectionStatus.Active
    case Outcome.Retire   => CorrectionStatus.Agreed
    case Outcome.Conflict => CorrectionStatus.Conflict

  def counts(os: Iterable[Outcome]): CorrectionCounts =
    CorrectionCounts(os.count(_ == Outcome.Keep), os.count(_ == Outcome.Retire), os.count(_ == Outcome.Conflict))

  /** Pure: a JSON value in a line of text (a string unquoted, null as "none"). */
  def show(json: String): String =
    io.circe.parser.parse(json).toOption match
      case Some(j) if j.isNull => "none"
      case Some(j)             => j.asString.getOrElse(j.noSpaces)
      case None                => json

  /** Pure: one correction in words — what was changed, what the catalogue said, and, in conflict, what it says now.
    */
  def describe(c: Correction): String =
    val base = s"${c.field}: “${Fmt.clip(show(c.value))}” (was “${Fmt.clip(show(c.upstreamValue))}”" +
      c.upstream.fold("")(u => s" in $u") + ")"
    c.status match
      case CorrectionStatus.Conflict =>
        s"$base — CONFLICT: the catalogue now says “${Fmt.clip(c.upstreamNow.map(show).getOrElse("?"))}”"
      case CorrectionStatus.Agreed => s"$base — retired: the catalogue now agrees"
      case _                       => base

  private object Fmt:
    def clip(s: String): String = if s.length > 80 then s.take(79) + "…" else s

  /** Pure: a list as Markdown (`histmersion events corrections`, the MCP tool). */
  def markdown(l: CorrectionList): String =
    if l.corrections.isEmpty then "_No corrections by hand._"
    else
      val lines =
        l.corrections.map(c => s"- **${c.targetId}**${c.label.fold("")(t => s" ($t)")} · ${c.status} · ${describe(c)}")
      (s"### ${l.total} correction(s) by hand" :: "" :: lines).mkString("\n")

/** The FIELDS of an event a correction can hold, and their JSON values — the ones an import of the catalogue that
  * created the event would otherwise rewrite. Pure.
  */
object EventFields:
  val Label = "label"
  val Kind = "kind"
  val Span = "timeSpan"
  val Historicity = "historicity"
  val Description = "description"
  val Geometry = "geometry"
  val all: List[String] = List(Label, Kind, Span, Historicity, Description, Geometry)

  /** Pure: a point as the GeoJSON an event's geometry is compared as. */
  def point(lat: Double, lon: Double): Json =
    Json.obj("type" -> "Point".asJson, "coordinates" -> Json.arr(lon.asJson, lat.asJson))

  /** Pure: GeoJSON text as the JSON compared (unparseable text compares as itself; none as null). */
  def geometry(g: Option[String]): Json =
    g.map(_.trim).filter(_.nonEmpty).fold(Json.Null)(t => io.circe.parser.parse(t).getOrElse(t.asJson))

  /** Pure: what an import of `r` writes to each field. */
  def upstream(r: CatalogRecord): Map[String, Json] =
    Map(
      Label -> r.label.asJson,
      Kind -> EventTaxonomy.canonical(r.kind).asJson,
      Span -> r.timeSpan.asJson,
      Historicity -> r.historicity.asJson,
      Description -> r.description.asJson,
      Geometry -> (for la <- r.lat; lo <- r.lon yield point(la, lo)).getOrElse(Json.Null)
    )

  /** Pure: the fields a patch sets, as compared. */
  def ofPatch(p: EventPatch): Map[String, Json] =
    List(
      p.label.map(Label -> _.asJson),
      p.kind.map(k => Kind -> EventTaxonomy.canonical(k).asJson),
      p.timeSpan.map(Span -> _.asJson),
      p.historicity.map(Historicity -> _.asJson),
      p.description.map(d => Description -> Option(d).filter(_.nonEmpty).asJson),
      p.geometry.map(g => Geometry -> geometry(Some(g)))
    ).flatten.toMap

  /** Pure: the fields a whole put sets. */
  def ofPut(p: EventPut): Map[String, Json] =
    Map(
      Label -> p.label.asJson,
      Kind -> EventTaxonomy.canonical(p.kind).asJson,
      Span -> p.timeSpan.asJson,
      Historicity -> p.historicity.asJson,
      Description -> p.description.filter(_.nonEmpty).asJson,
      Geometry -> geometry(p.geometry)
    )
