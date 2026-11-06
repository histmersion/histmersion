package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

enum AgentMode(val wire: AgentMode.Wire):
  case Working extends AgentMode(_root_.com.histmersion.api.automation.AgentMode.Wire("working"))
  case Finishing extends AgentMode(_root_.com.histmersion.api.automation.AgentMode.Wire("finish"))
  case Paused extends AgentMode(_root_.com.histmersion.api.automation.AgentMode.Wire("pause"))

  /** The mode as the agents' switch offers it. */
  def wording: String = this match
    case Working   => ("Working")
    case Finishing => ("Finish current work, then pause")
    case Paused    => ("Pause now")

  /** The heading of the research overview in this mode. */
  def heading: String = this match
    case Working   => ("Research agents are working")
    case Finishing => ("Research agents are finishing current work, then pausing")
    case Paused    => ("Research agents are paused")

object AgentMode:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def parse(s: com.histmersion.api.automation.AgentMode.Wire): Option[AgentMode] =
    val t = (s).value.trim.toLowerCase
    values.find(m => m.wire == _root_.com.histmersion.api.automation.AgentMode.Wire(t) || m.toString.toLowerCase == t)

  def of(autopilotOn: com.histmersion.api.automation.AgentMode.Step.Autopilot.On, draining: com.histmersion.api.automation.AgentMode.Step.Drain.On): AgentMode =
    if !(autopilotOn).value then Paused else if (draining).value then Finishing else Working

  enum Step:
    case Autopilot(on: Step.Autopilot.On)
    case Drain(on: Step.Drain.On)

  object Step:
    object Autopilot:
      type On = On.T
      object On extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Drain:
      type On = On.T
      object On extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def steps(autopilotOn: com.histmersion.api.automation.AgentMode.Step.Autopilot.On, draining: com.histmersion.api.automation.AgentMode.Step.Drain.On, to: AgentMode): List[Step] = to match
    case Working   => Option.when(((draining) == _root_.com.histmersion.api.automation.AgentMode.Step.Drain.On(true)))(Step.Drain(_root_.com.histmersion.api.automation.AgentMode.Step.Drain.On(false))).toList ++ Option.when(!((autopilotOn) == _root_.com.histmersion.api.automation.AgentMode.Step.Autopilot.On(true)))(Step.Autopilot(_root_.com.histmersion.api.automation.AgentMode.Step.Autopilot.On(true)))
    case Finishing => Option.when(!(draining).value)(Step.Drain(_root_.com.histmersion.api.automation.AgentMode.Step.Drain.On(true))).toList ++ Option.when(!((autopilotOn) == _root_.com.histmersion.api.automation.AgentMode.Step.Autopilot.On(true)))(Step.Autopilot(_root_.com.histmersion.api.automation.AgentMode.Step.Autopilot.On(true)))
    case Paused    => Option.when(((draining) == _root_.com.histmersion.api.automation.AgentMode.Step.Drain.On(true)))(Step.Drain(_root_.com.histmersion.api.automation.AgentMode.Step.Drain.On(false))).toList ++ Option.when(((autopilotOn) == _root_.com.histmersion.api.automation.AgentMode.Step.Autopilot.On(true)))(Step.Autopilot(_root_.com.histmersion.api.automation.AgentMode.Step.Autopilot.On(false)))

  final case class ModeSet(mode: ModeSet.Mode, by: Option[ModeSet.By] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object ModeSet:
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
