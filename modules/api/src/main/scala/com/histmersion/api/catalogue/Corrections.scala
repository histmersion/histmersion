package com.histmersion.api.catalogue
import io.circe.Json
import io.circe.syntax.*
import com.histmersion.api.given

object Corrections:

  type StatusResult = StatusResult.T
  object StatusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowResult = ShowResult.T
  object ShowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowJson = ShowJson.T
  object ShowJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Outcome:
    case Keep

    case Retire

    case Conflict

  def reconcile(value: Json, upstreamThen: Json, upstreamNow: Json): Outcome =
    if upstreamNow == value then Outcome.Retire
    else if upstreamNow == upstreamThen then Outcome.Keep
    else Outcome.Conflict

  def status(o: Outcome): com.histmersion.api.catalogue.Corrections.StatusResult = o match
    case Outcome.Keep     => Correction.Status.Active
    case Outcome.Retire   => Correction.Status.Agreed
    case Outcome.Conflict => Correction.Status.Conflict

  def counts(os: Iterable[Outcome]): Correction.Counts =
    Correction.Counts(os.count(_ == Outcome.Keep), os.count(_ == Outcome.Retire), os.count(_ == Outcome.Conflict))

  def show(json: com.histmersion.api.catalogue.Corrections.ShowJson): com.histmersion.api.catalogue.Corrections.ShowResult =
    io.circe.parser.parse(json).toOption match
      case Some(j) if j.isNull => "none"
      case Some(j)             => j.asString.getOrElse(j.noSpaces)
      case None                => json

  def describe(c: Correction): com.histmersion.api.catalogue.Corrections.DescribeResult =
    val base = s"${c.field}: “${Fmt.clip(show(c.value))}” (was “${Fmt.clip(show(c.upstreamValue))}”" +
      c.upstream.fold("")(u => s" in $u") + ")"
    c.status match
      case Correction.Status.Conflict =>
        s"$base — CONFLICT: the catalogue now says “${Fmt.clip(c.upstreamNow.map(show).getOrElse("?"))}”"
      case Correction.Status.Agreed => s"$base — retired: the catalogue now agrees"
      case _                        => base

  private object Fmt:
    type ClipResult = ClipResult.T
    object ClipResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClipS = ClipS.T
    object ClipS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def clip(s: com.histmersion.api.catalogue.Corrections.Fmt.ClipS): com.histmersion.api.catalogue.Corrections.Fmt.ClipResult = if s.length > 80 then s.take(79) + "…" else s

  def markdown(l: Correction.List): com.histmersion.api.catalogue.Corrections.MarkdownResult =
    if l.corrections.isEmpty then "_No corrections by hand._"
    else
      val lines =
        l.corrections.map(c => s"- **${c.targetId}**${c.label.fold("")(t => s" ($t)")} · ${c.status} · ${describe(c)}")
      (s"### ${l.total} correction(s) by hand" :: "" :: lines).mkString("\n")
