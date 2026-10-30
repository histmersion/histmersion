package com.histmersion.api

import io.circe.Json
import io.circe.syntax.*

object Corrections:

  enum Outcome:
    case Keep

    case Retire

    case Conflict

  def reconcile(value: Json, upstreamThen: Json, upstreamNow: Json): Outcome =
    if upstreamNow == value then Outcome.Retire
    else if upstreamNow == upstreamThen then Outcome.Keep
    else Outcome.Conflict

  def status(o: Outcome): String = o match
    case Outcome.Keep     => CorrectionStatus.Active
    case Outcome.Retire   => CorrectionStatus.Agreed
    case Outcome.Conflict => CorrectionStatus.Conflict

  def counts(os: Iterable[Outcome]): CorrectionCounts =
    CorrectionCounts(os.count(_ == Outcome.Keep), os.count(_ == Outcome.Retire), os.count(_ == Outcome.Conflict))

  def show(json: String): String =
    io.circe.parser.parse(json).toOption match
      case Some(j) if j.isNull => "none"
      case Some(j)             => j.asString.getOrElse(j.noSpaces)
      case None                => json

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

  def markdown(l: CorrectionList): String =
    if l.corrections.isEmpty then "_No corrections by hand._"
    else
      val lines =
        l.corrections.map(c => s"- **${c.targetId}**${c.label.fold("")(t => s" ($t)")} · ${c.status} · ${describe(c)}")
      (s"### ${l.total} correction(s) by hand" :: "" :: lines).mkString("\n")
