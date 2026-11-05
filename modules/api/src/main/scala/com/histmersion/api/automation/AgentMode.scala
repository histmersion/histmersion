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
  def wording: com.histmersion.api.automation.AgentMode.WordingResult = this match
    case Working   => "Working"
    case Finishing => "Finish current work, then pause"
    case Paused    => "Pause now"

  /** The heading of the research overview in this mode. */
  def heading: com.histmersion.api.automation.AgentMode.HeadingResult = this match
    case Working   => "Research agents are working"
    case Finishing => "Research agents are finishing current work, then pausing"
    case Paused    => "Research agents are paused"

object AgentMode:
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
    val t = s.trim.toLowerCase
    values.find(m => m.wire == t || m.toString.toLowerCase == t)

  def of(autopilotOn: com.histmersion.api.automation.AgentMode.OfAutopilotOn, draining: com.histmersion.api.automation.AgentMode.OfDraining): AgentMode =
    if !autopilotOn then Paused else if draining then Finishing else Working

  enum Step:
    case Autopilot(on: Boolean)
    case Drain(on: Boolean)

  def steps(autopilotOn: com.histmersion.api.automation.AgentMode.StepsAutopilotOn, draining: com.histmersion.api.automation.AgentMode.StepsDraining, to: AgentMode): List[Step] = to match
    case Working   => Option.when(draining)(Step.Drain(false)).toList ++ Option.when(!autopilotOn)(Step.Autopilot(true))
    case Finishing => Option.when(!draining)(Step.Drain(true)).toList ++ Option.when(!autopilotOn)(Step.Autopilot(true))
    case Paused    => Option.when(draining)(Step.Drain(false)).toList ++ Option.when(autopilotOn)(Step.Autopilot(false))

  final case class ModeSet(mode: ModeSet.Mode, by: Option[ModeSet.By] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object ModeSet:
    type Mode = Mode.T
    object Mode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
