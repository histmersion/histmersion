package com.histmersion
package api.catalogue
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

object Corrections:
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  enum Outcome derives CanEqual:
    case Keep

    case Retire

    case Conflict

  private given CanEqual[Json, Json] = CanEqual.derived

  def reconcile(value: Json, upstreamThen: Json, upstreamNow: Json): Outcome =
    if upstreamNow == value then Outcome.Retire
    else if upstreamNow == upstreamThen then Outcome.Keep
    else Outcome.Conflict

  def status(o: Outcome): Correction.StatusValue = o match
    case Outcome.Keep     => Correction.StatusValue(Correction.Status.Active)
    case Outcome.Retire   => Correction.StatusValue(Correction.Status.Agreed)
    case Outcome.Conflict => Correction.StatusValue(Correction.Status.Conflict)

  def counts(os: Iterable[Outcome]): Correction.Counts =
    Correction.Counts(
      Correction.Counts.Kept(os.count(_ == Outcome.Keep)),
      Correction.Counts.Retired(os.count(_ == Outcome.Retire)),
      Correction.Counts.Conflicts(os.count(_ == Outcome.Conflict))
    )

  def show(json: String): String =
    RawJson.parse(json).toOption match
      case Some(j) if j.isNull => ("none")
      case Some(j)             => (j.asString.getOrElse(j.noSpaces))
      case None                => (json)

  def describe(c: Correction): String =
    val base = s"${c.field}: “${Fmt.clip(show(c.value.value))}” (was “${Fmt.clip(show(c.upstreamValue.value))}”" +
      c.upstream.fold("")(u => s" in $u") + ")"
    (c.status.value: String) match
      case Correction.Status.Conflict =>
        s"$base — CONFLICT: the catalogue now says “${Fmt.clip(c.upstreamNow.map(u => show(u.value)).getOrElse("?"))}”"
      case Correction.Status.Agreed => (s"$base — retired: the catalogue now agrees")
      case _                        => base

  private object Fmt:

    def clip(s: String): String = if (s).length > 80 then ((s).take(79) + "…") else ((s))

  def markdown(l: Correction.List): Markdown =
    if l.corrections.isEmpty then Markdown("_No corrections by hand._")
    else
      val lines =
        l.corrections.map(c => s"- **${c.targetId}**${c.label.fold("")(t => s" ($t)")} · ${c.status} · ${describe(c)}")
      Markdown((s"### ${l.total} correction(s) by hand" :: "" :: lines).mkString("\n"))
