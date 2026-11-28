package com.histmersion
package api.automation
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

enum AgentMode(val wire: AgentMode.Wire) derives CanEqual:
  case Working extends AgentMode(AgentMode.Wire("working"))
  case Finishing extends AgentMode(AgentMode.Wire("finish"))
  case Paused extends AgentMode(AgentMode.Wire("pause"))

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
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[AgentMode] =
    val t = (s).value.trim.toLowerCase
    values.find(m => m.wire == Wire(t) || m.toString.toLowerCase == t)

  def of(autopilotOn: Step.Autopilot.On, draining: Step.Drain.On): AgentMode =
    if !(autopilotOn).on then Paused else if (draining).on then Finishing else Working

  enum Step:
    case Autopilot(on: Autopilot.On)
    case Drain(on: Drain.On)

  object Step:
    object Autopilot:
      enum On derives CanEqual:
        case On, Off
      object On extends TwoStates[On]:
        extension (on: On) def on: Boolean = on == On
    object Drain:
      enum On derives CanEqual:
        case On, Off
      object On extends TwoStates[On]:
        extension (on: On) def on: Boolean = on == On

  def steps(autopilotOn: Step.Autopilot.On, draining: Step.Drain.On, to: AgentMode): List[Step] = to match
    case Working =>
      Option.when(((draining) == Step.Drain.On.On))(Step.Drain(Step.Drain.On.Off)).toList ++ Option.when(
        !((autopilotOn) == Step.Autopilot.On.On)
      )(Step.Autopilot(Step.Autopilot.On.On))
    case Finishing =>
      Option.when(!(draining).on)(Step.Drain(Step.Drain.On.On)).toList ++ Option.when(
        !((autopilotOn) == Step.Autopilot.On.On)
      )(Step.Autopilot(Step.Autopilot.On.On))
    case Paused =>
      Option.when(((draining) == Step.Drain.On.On))(Step.Drain(Step.Drain.On.Off)).toList ++ Option.when(
        ((autopilotOn) == Step.Autopilot.On.On)
      )(Step.Autopilot(Step.Autopilot.On.Off))

  final case class ModeSet(
      mode: ModeSet.Mode,
      by: Option[ModeSet.By] = None,
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

  object ModeSet:
    export AgentMode.Wire as Mode
    type By = By.T
    object By extends Newtype[String, Pure]
