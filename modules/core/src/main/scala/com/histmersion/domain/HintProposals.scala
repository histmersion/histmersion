package com.histmersion
package domain

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.automation.Managers.{HintEffect, ManagerConfig}
import io.github.iltotore.iron.constraint.any.Pure

/** LLM-proposed hint effects (docs/research-process-managers.md §3.6, phase 3): a person writes a hint in words; a
  * model reads it and proposes the structured action it asks for — hold, prioritise or skip some of the manager's
  * items, or a change of its settings — and CODE decides whether the proposal is fit to show a person (an action it
  * knows, targets that exist, a date that parses, settings that parse). A proposal is never applied by itself: it
  * becomes a `hint-effect` approval, applied when a person approves it. Pure.
  */
object HintProposals:
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Item = Item.T
  object Item extends Newtype[String, Pure]
  type Last = Last.T
  object Last extends Newtype[Boolean, Pure]

  /** The `hint-effect` step's typed answer. `action` "none" = the hint asks for no structured action. */

  final case class HintAnswer(
      action: HintAnswer.Action,
      why: Problem = Problem(""),
      targets: List[HintAnswer.Targets] = Nil,
      tag: Option[HintAnswer.Tag] = None,
      until: Option[HintAnswer.Until] = None,
      untilSource: Option[HintAnswer.UntilSource] = None,
      priority: Option[HintAnswer.Priority] = None,
      settings: List[HintAnswer.Settings] = Nil
  ) derives KindlingsCodecAsObject
  object HintAnswer:
    type Action = Action.T
    object Action extends Newtype[String, Pure]
    type Targets = Targets.T
    object Targets extends Newtype[String, Pure]
    type Tag = Tag.T
    object Tag extends Newtype[String, Pure]
    type Until = Until.T
    object Until extends Newtype[String, Pure]
    type UntilSource = UntilSource.T
    object UntilSource extends Newtype[String, Pure]
    type Priority = Priority.T
    object Priority extends Newtype[Int, Pure]
    type Settings = Settings.T
    object Settings extends Newtype[String, Pure]

    given Schema[HintAnswer] = KindlingsSchema
      .derived[HintAnswer]
      .schema
      .modify(_.action)(
        _.description("none | prioritise | hold | skip | config — what the hint asks the manager to do")
      )
      .modify(_.why)(_.description("Which words of the hint ask for it, in one sentence"))
      .modify(_.targets)(
        _.description("prioritise, hold, skip: the items it names, exactly as listed (article slugs, citekeys, goals)")
      )
      .modify(_.tag)(_.description("prioritise, hold, skip: or every listed article carrying this tag; null for none"))
      .modify(_.until)(_.description("hold: until this date (2026-11-01); null for none"))
      .modify(_.untilSource)(
        _.description("hold: or until a source with this DOI or citekey is in the corpus; null for none")
      )
      .modify(_.priority)(_.description("prioritise: the priority (a person's enqueue is 10); null for the default"))
      .modify(_.settings)(_.description("config: the settings to change as key=value, from the listed keys"))

  /** The highest priority a proposal may set (a person's enqueue is 10). */
  val MaxPriority = 20

  /** An answer checked: Right(None) = no action asked for; Right(Some) = the effect to propose; Left = what is wrong
    * (sent back while attempts remain). `known` = whether a target is one of the manager's items (or names a record it
    * could drive); `item` = the target the hint is scoped to, if any. On the LAST attempt an unknown target is dropped
    * (warned) rather than failing the proposal, and an answer that stays wrong proposes nothing.
    */
  def check(
      a: HintAnswer,
      known: Item => Boolean,
      item: Option[Item],
      last: Last
  ): Either[List[Problem], (Option[HintEffect], List[Description])] =
    val action = Option(a.action).map(_.value.trim.toLowerCase).getOrElse("")
    val targets = a.targets.map(_.value.trim).filter(_.nonEmpty).distinct.map(Item(_))
    val (good, unknown) = targets.partition(known)
    val tag = a.tag.map(_.value.trim).filter(_.nonEmpty)
    def fail(ps: List[Problem]): Either[List[Problem], (Option[HintEffect], List[Description])] =
      if last.value then Right((None, ps.map(p => Description(s"nothing proposed: $p")))) else Left(ps)
    if action == "none" || action.isEmpty then Right((None, Nil))
    else if !HintEffect.Proposable.contains(action) then
      fail(List(Problem(s"'${a.action}' is not an action (one of: none, ${HintEffect.Proposable.mkString(", ")})")))
    else if action == HintEffect.Config then
      val kvs = a.settings.map(_.value.trim).filter(_.nonEmpty)
      if kvs.isEmpty then fail(List(Problem("config: give the settings to change as key=value")))
      else
        ManagerConfig.parse(kvs.map(ManagerConfig.Spec(_))) match
          case Left(e)  => fail(List(Problem(s"config: $e")))
          case Right(c) => Right((Some(HintEffect(HintEffect.Action(HintEffect.Config), config = Some(c))), Nil))
    else
      val problems = List(
        Option.when(unknown.nonEmpty && !last.value)(
          Problem(s"not among the manager's items: ${unknown.mkString(", ")} — name them exactly as listed")
        ),
        Option.when(good.isEmpty && unknown.isEmpty && tag.isEmpty && item.isEmpty)(
          Problem(s"$action: name the items (targets) or a tag")
        ),
        Option.when(
          action == HintEffect.HoldAction && a.until.forall(_.value.trim.isEmpty) && a.untilSource.forall(
            _.value.trim.isEmpty
          )
        )(Problem("hold: give `until` (a date) or `untilSource` (a DOI or citekey)")),
        a.until
          .map(_.value.trim)
          .filter(_.nonEmpty)
          .filter(u => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(u)).isEmpty)
          .map(u => Problem(s"hold: '$u' is not a date (2026-11-01)")),
        a.priority
          .map(_.value)
          .filter(p => p < -MaxPriority || p > MaxPriority)
          .map(p => Problem(s"prioritise: priority $p is outside ±$MaxPriority"))
      ).flatten
      if problems.nonEmpty then fail(problems)
      else if good.isEmpty && tag.isEmpty && item.isEmpty then
        fail(List(Problem(s"$action: none of the items it names exists")))
      else
        Right(
          (
            Some(
              HintEffect(
                HintEffect.Action(action),
                (good ++ item.filterNot(good.contains).toList).map(i => HintEffect.Targets(i.value)),
                tag.map(HintEffect.Tag(_)),
                until = a.until.map(_.value.trim).filter(_.nonEmpty).map(HintEffect.Until(_)),
                untilSource = a.untilSource.map(_.value.trim).filter(_.nonEmpty).map(HintEffect.UntilSource(_)),
                priority =
                  a.priority.filter(_ => action == HintEffect.Prioritise).map(p => HintEffect.Priority(p.value))
              )
            ),
            Option
              .when(unknown.nonEmpty)(Description(s"dropped, not among the manager's items: ${unknown.mkString(", ")}"))
              .toList
          )
        )

  /** What the step checkpoints: the proposal that passed (None: nothing asked for, or nothing passed), why, and the
    * warnings.
    */
  final case class Proposed(effect: Option[HintEffect], why: Problem, problems: List[Description] = Nil)
      derives KindlingsCodecAsObject

