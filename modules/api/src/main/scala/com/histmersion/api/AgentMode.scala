package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The research agents' state as ONE three-way choice: working (the autopilot on, nothing held back), finishing what
  * they are doing and then pausing (the autopilot on and draining: nothing new starts, the runs in flight finish), or
  * paused now (the autopilot off). The two switches underneath stay what the CLI and the tools set (`agent autopilot
  * on|off`, `… drain on|off`). Pure.
  */
enum AgentMode(val wire: String):
  case Working extends AgentMode("working")
  case Finishing extends AgentMode("finish")
  case Paused extends AgentMode("pause")

object AgentMode:
  def parse(s: String): Option[AgentMode] =
    val t = s.trim.toLowerCase
    values.find(m => m.wire == t || m.toString.toLowerCase == t)

  /** The mode the two switches stand for. Off is paused, draining or not. */
  def of(autopilotOn: Boolean, draining: Boolean): AgentMode =
    if !autopilotOn then Paused else if draining then Finishing else Working

  /** One switch to move. */
  enum Step:
    case Autopilot(on: Boolean)
    case Drain(on: Boolean)

  /** The switches to move, in order, to go from the switches as they stand to `to`: nothing when it is there already. A
    * drain is lifted before the autopilot goes on, and off before the autopilot is paused, so no state between two
    * steps starts work the person did not choose.
    */
  def steps(autopilotOn: Boolean, draining: Boolean, to: AgentMode): List[Step] = to match
    case Working   => Option.when(draining)(Step.Drain(false)).toList ++ Option.when(!autopilotOn)(Step.Autopilot(true))
    case Finishing => Option.when(!draining)(Step.Drain(true)).toList ++ Option.when(!autopilotOn)(Step.Autopilot(true))
    case Paused    => Option.when(draining)(Step.Drain(false)).toList ++ Option.when(autopilotOn)(Step.Autopilot(false))

  /** `POST /api/agents/autopilot/mode {mode}`: `working`, `finish` or `pause`; `by` = who. */
  final case class ModeSet(mode: String, by: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
  object ModeSet:
    given Schema[ModeSet] = summon[KindlingsSchema[ModeSet]].schema
