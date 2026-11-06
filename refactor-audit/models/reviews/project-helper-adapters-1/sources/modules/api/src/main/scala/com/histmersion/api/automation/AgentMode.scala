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
  def wording: com.histmersion.api.automation.AgentMode.WordingResult = this match
    case Working   => _root_.com.histmersion.api.automation.AgentMode.WordingResult("Working")
    case Finishing => _root_.com.histmersion.api.automation.AgentMode.WordingResult("Finish current work, then pause")
    case Paused    => _root_.com.histmersion.api.automation.AgentMode.WordingResult("Pause now")

  /** The heading of the research overview in this mode. */
  def heading: com.histmersion.api.automation.AgentMode.HeadingResult = this match
    case Working   => _root_.com.histmersion.api.automation.AgentMode.HeadingResult("Research agents are working")
    case Finishing => _root_.com.histmersion.api.automation.AgentMode.HeadingResult("Research agents are finishing current work, then pausing")
    case Paused    => _root_.com.histmersion.api.automation.AgentMode.HeadingResult("Research agents are paused")

object AgentMode:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type WordingResult = WordingResult.T
  object WordingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingResult = HeadingResult.T
  object HeadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfAutopilotOn = OfAutopilotOn.T
  object OfAutopilotOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfDraining = OfDraining.T
  object OfDraining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepsAutopilotOn = StepsAutopilotOn.T
  object StepsAutopilotOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepsDraining = StepsDraining.T
  object StepsDraining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.automation.AgentMode.ParseS): Option[AgentMode] =
    val t = (s).value.trim.toLowerCase
    values.find(m => m.wire == _root_.com.histmersion.api.automation.AgentMode.Wire(t) || m.toString.toLowerCase == t)

  def of(autopilotOn: com.histmersion.api.automation.AgentMode.OfAutopilotOn, draining: com.histmersion.api.automation.AgentMode.OfDraining): AgentMode =
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

  def steps(autopilotOn: com.histmersion.api.automation.AgentMode.StepsAutopilotOn, draining: com.histmersion.api.automation.AgentMode.StepsDraining, to: AgentMode): List[Step] = to match
    case Working   => Option.when(((draining) == _root_.com.histmersion.api.automation.AgentMode.StepsDraining(true)))(Step.Drain(_root_.com.histmersion.api.automation.AgentMode.Step.Drain.On(false))).toList ++ Option.when(!((autopilotOn) == _root_.com.histmersion.api.automation.AgentMode.StepsAutopilotOn(true)))(Step.Autopilot(_root_.com.histmersion.api.automation.AgentMode.Step.Autopilot.On(true)))
    case Finishing => Option.when(!(draining).value)(Step.Drain(_root_.com.histmersion.api.automation.AgentMode.Step.Drain.On(true))).toList ++ Option.when(!((autopilotOn) == _root_.com.histmersion.api.automation.AgentMode.StepsAutopilotOn(true)))(Step.Autopilot(_root_.com.histmersion.api.automation.AgentMode.Step.Autopilot.On(true)))
    case Paused    => Option.when(((draining) == _root_.com.histmersion.api.automation.AgentMode.StepsDraining(true)))(Step.Drain(_root_.com.histmersion.api.automation.AgentMode.Step.Drain.On(false))).toList ++ Option.when(((autopilotOn) == _root_.com.histmersion.api.automation.AgentMode.StepsAutopilotOn(true)))(Step.Autopilot(_root_.com.histmersion.api.automation.AgentMode.Step.Autopilot.On(false)))

  final case class ModeSet(mode: ModeSet.Mode, by: Option[ModeSet.By] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object ModeSet:
    type Mode = Mode.T
    object Mode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
