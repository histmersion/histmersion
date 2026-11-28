package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Goal
import com.histmersion.api.WireEnumValues
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class HintEffect(
    action: HintEffect.Action,
    targets: List[HintEffect.Targets] = Nil,
    tag: Option[HintEffect.Tag] = None,
    topic: Option[HintEffect.Topic] = None,
    goal: Option[Goal.Slug] = None,
    until: Option[HintEffect.Until] = None,
    untilSource: Option[HintEffect.UntilSource] = None,
    priority: Option[HintEffect.Priority] = None,
    config: Option[ManagerConfig] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object HintEffect:

  /** The action word as a person or a model wrote it: one [[Verb]]'s word once checked, any text before. */
  type Action = Action.T
  object Action extends Newtype[String, Pure]:
    /** The verb a word of any newtype names (`Action.verb(answer.action)`), trimmed and lower-cased: unwrapped here,
      * like `Problem.Raw.of`. None = not an action word.
      */
    def verb[T](word: T)(using RefinedType.Mirror[T]): Option[Verb] =
      val written = Action(s"$word".trim.toLowerCase)
      Verb.values.find(_.word == written)

    /** Whether a word of any newtype asks for no action: blank, or [[Verb.NoAction]]. */
    def none[T](word: T)(using RefinedType.Mirror[T]): Boolean =
      val written = Action(Option(word).fold("")(w => s"$w").trim.toLowerCase)
      written == Verb.NoAction || written == Action("")

  /** What a hint asks a manager to do, by its wire word. */
  enum Verb(val word: Action) derives CanEqual:
    case Prioritise extends Verb(Action("prioritise"))
    case Hold extends Verb(Action("hold"))
    case Skip extends Verb(Action("skip"))

    /** A change of the manager's settings: a model may propose it; a person changes the config itself. */
    case Config extends Verb(Action("config"))

  object Verb:
    /** The word that asks for no action (the hint box's and a model's answer's `none`). */
    val NoAction: Action = Action("none")

    /** What a person's hint may ask for. */
    val Actions: Vector[Verb] = Vector(Prioritise, Hold, Skip)

    /** What a model may propose: a person's actions, or a settings change. */
    val Proposable: Vector[Verb] = Actions :+ Config

    given WireEnumValues[Verb]:
      def all: Array[Verb] = values
      def wire(v: Verb): WireEnumValues.Wire = WireEnumValues.Wire.of(v.word)
      def fromWire(s: WireEnumValues.Wire): Option[Verb] = Action.verb(s)

  type Targets = Targets.T
  object Targets extends Newtype[String, Pure]
  type Tag = Tag.T
  object Tag extends Newtype[String, Pure]
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
  type Until = Until.T
  object Until extends Newtype[String, Pure]
  type UntilSource = UntilSource.T
  object UntilSource extends Newtype[String, Pure]
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]
