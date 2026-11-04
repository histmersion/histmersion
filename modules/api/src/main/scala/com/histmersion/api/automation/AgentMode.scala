package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

enum AgentMode(val wire: String):
  case Working extends AgentMode("working")
  case Finishing extends AgentMode("finish")
  case Paused extends AgentMode("pause")

  /** The mode as the agents' switch offers it. */
  def wording: String = this match
    case Working   => "Working"
    case Finishing => "Finish current work, then pause"
    case Paused    => "Pause now"

  /** The heading of the research overview in this mode. */
  def heading: String = this match
    case Working   => "Research agents are working"
    case Finishing => "Research agents are finishing current work, then pausing"
    case Paused    => "Research agents are paused"

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

  final case class ModeSet(mode: String, by: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
