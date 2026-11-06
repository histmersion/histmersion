package com.histmersion.api.catalogue
import io.circe.Json
import io.circe.syntax.*
import com.histmersion.api.given

object Corrections:
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type StatusResult = StatusResult.T
  object StatusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowJson = ShowJson.T
  object ShowJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = com.histmersion.api.catalogue.Corrections.Markdown
  val MarkdownResult: _root_.com.histmersion.api.catalogue.Corrections.Markdown.type = _root_.com.histmersion.api.catalogue.Corrections.Markdown

  enum Outcome:
    case Keep

    case Retire

    case Conflict

  def reconcile(value: Json, upstreamThen: Json, upstreamNow: Json): Outcome =
    if upstreamNow == value then Outcome.Retire
    else if upstreamNow == upstreamThen then Outcome.Keep
    else Outcome.Conflict

  def status(o: Outcome): com.histmersion.api.catalogue.Corrections.StatusResult = o match
    case Outcome.Keep     => _root_.com.histmersion.api.catalogue.Corrections.StatusResult(Correction.Status.Active)
    case Outcome.Retire   => _root_.com.histmersion.api.catalogue.Corrections.StatusResult(Correction.Status.Agreed)
    case Outcome.Conflict => _root_.com.histmersion.api.catalogue.Corrections.StatusResult(Correction.Status.Conflict)

  def counts(os: Iterable[Outcome]): Correction.Counts =
    Correction.Counts(_root_.com.histmersion.api.catalogue.Correction.Counts.Kept(os.count(_ == Outcome.Keep)), _root_.com.histmersion.api.catalogue.Correction.Counts.Retired(os.count(_ == Outcome.Retire)), _root_.com.histmersion.api.catalogue.Correction.Counts.Conflicts(os.count(_ == Outcome.Conflict)))

  def show(json: com.histmersion.api.catalogue.Corrections.ShowJson): String =
    io.circe.parser.parse((json).value).toOption match
      case Some(j) if j.isNull => ("none")
      case Some(j)             => (j.asString.getOrElse(j.noSpaces))
      case None                => ((json).value)

  def describe(c: Correction): String =
    val base = s"${c.field}: “${Fmt.clip(show(c.value))}” (was “${Fmt.clip(show(c.upstreamValue))}”" +
      c.upstream.fold("")(u => s" in $u") + ")"
    c.status match
      case Correction.Status.Conflict =>
        s"$base — CONFLICT: the catalogue now says “${Fmt.clip(c.upstreamNow.map(show).getOrElse("?"))}”"
      case Correction.Status.Agreed => (s"$base — retired: the catalogue now agrees")
      case _                        => base

  private object Fmt:

    def clip(s: String): String = if (s).length > 80 then ((s).take(79) + "…") else ((s))

  def markdown(l: Correction.List): com.histmersion.api.catalogue.Corrections.Markdown =
    if l.corrections.isEmpty then _root_.com.histmersion.api.catalogue.Corrections.Markdown("_No corrections by hand._")
    else
      val lines =
        l.corrections.map(c => s"- **${c.targetId}**${c.label.fold("")(t => s" ($t)")} · ${c.status} · ${describe(c)}")
      _root_.com.histmersion.api.catalogue.Corrections.Markdown((s"### ${l.total} correction(s) by hand" :: "" :: lines).mkString("\n"))
