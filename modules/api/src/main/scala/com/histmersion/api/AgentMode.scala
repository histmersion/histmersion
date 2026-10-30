package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

enum AgentMode(val wire: String):
  case Working extends AgentMode("working")
  case Finishing extends AgentMode("finish")
  case Paused extends AgentMode("pause")

object AgentMode:
  def parse(s: String): Option[AgentMode] =
    val t = s.trim.toLowerCase
    values.find(m => m.wire == t || m.toString.toLowerCase == t)

  def of(autopilotOn: Boolean, draining: Boolean): AgentMode =
    if !autopilotOn then Paused else if draining then Finishing else Working

  enum Step:
    case Autopilot(on: Boolean)
    case Drain(on: Boolean)

  def steps(autopilotOn: Boolean, draining: Boolean, to: AgentMode): List[Step] = to match
    case Working   => Option.when(draining)(Step.Drain(false)).toList ++ Option.when(!autopilotOn)(Step.Autopilot(true))
    case Finishing => Option.when(!draining)(Step.Drain(true)).toList ++ Option.when(!autopilotOn)(Step.Autopilot(true))
    case Paused    => Option.when(draining)(Step.Drain(false)).toList ++ Option.when(autopilotOn)(Step.Autopilot(false))

  final case class ModeSet(mode: String, by: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
  object ModeSet:
    given Schema[ModeSet] = summon[KindlingsSchema[ModeSet]].schema
