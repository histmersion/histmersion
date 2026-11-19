package com.histmersion
package api.automation
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

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

  def parse(s: Wire): Option[AgentMode] =
    val t = (s).value.trim.toLowerCase
    values.find(m => m.wire == Wire(t) || m.toString.toLowerCase == t)

  def of(autopilotOn: Step.Autopilot.On, draining: Step.Drain.On): AgentMode =
    if !(autopilotOn).value then Paused else if (draining).value then Finishing else Working

  enum Step:
    case Autopilot(on: Autopilot.On)
    case Drain(on: Drain.On)

  object Step:
    object Autopilot:
      type On = On.T
      object On extends Newtype[Boolean, Pure]
    object Drain:
      type On = On.T
      object On extends Newtype[Boolean, Pure]

  def steps(autopilotOn: Step.Autopilot.On, draining: Step.Drain.On, to: AgentMode): List[Step] = to match
    case Working =>
      Option.when(((draining) == Step.Drain.On(true)))(Step.Drain(Step.Drain.On(false))).toList ++ Option.when(
        !((autopilotOn) == Step.Autopilot.On(true))
      )(Step.Autopilot(Step.Autopilot.On(true)))
    case Finishing =>
      Option.when(!(draining).value)(Step.Drain(Step.Drain.On(true))).toList ++ Option.when(
        !((autopilotOn) == Step.Autopilot.On(true))
      )(Step.Autopilot(Step.Autopilot.On(true)))
    case Paused =>
      Option.when(((draining) == Step.Drain.On(true)))(Step.Drain(Step.Drain.On(false))).toList ++ Option.when(
        ((autopilotOn) == Step.Autopilot.On(true))
      )(Step.Autopilot(Step.Autopilot.On(false)))

  final case class ModeSet(mode: ModeSet.Mode, by: Option[ModeSet.By] = None)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object ModeSet:
    export AgentMode.Wire as Mode
    type By = By.T
    object By extends Newtype[String, Pure]
