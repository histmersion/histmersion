package com.histmersion.api.instance
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CorpusCounts(
    sources: CorpusCounts.Sources,
    findings: CorpusCounts.Findings,
    articles: CorpusCounts.Articles,
    events: CorpusCounts.Events,
    places: CorpusCounts.Places,
    periods: CorpusCounts.Periods,
    presets: CorpusCounts.Presets = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CorpusCounts:
  type Sources = Sources.T
  object Sources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Findings = Findings.T
  object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Articles = Articles.T
  object Articles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Events = Events.T
  object Events extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Places = Places.T
  object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Periods = Periods.T
  object Periods extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Presets = Presets.T
  object Presets extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
