package com.histmersion
package api.automation.autopilot

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Autopilot.Rationale
import com.histmersion.api.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class FocusGoal(
    slug: FocusGoal.Slug,
    title: FocusGoal.Title = Goal.Title(""),
    status: FocusGoal.Status = FocusGoal.Status(""),
    stage: FocusGoal.Stage = FocusGoal.Stage(""),
    why: Rationale = Rationale(""),
    pinned: FocusGoal.Pinned = FocusGoal.Pinned.Unpinned,
    priority: FocusGoal.Priority = FocusGoal.Priority(0),
    since: Option[FocusGoal.Since] = None,
    doing: Option[FocusGoal.Doing] = None,
    open: FocusGoal.Open = FocusGoal.Open(0),
    doneLast7: FocusGoal.DoneLast7 = FocusGoal.DoneLast7(0),
    etaDays: Option[FocusGoal.EtaDays] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object FocusGoal:
  export api.research.ReviewGoal.Slug
  export Goal.Title
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Stage = Stage.T
  object Stage extends Newtype[String, Pure]
  enum Pinned derives CanEqual:
    case Pinned, Unpinned
  object Pinned extends TwoStates[Pinned]:
    extension (pinned: Pinned) def pinned: Boolean = pinned == Pinned
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]
  type Since = Since.T
  object Since extends Newtype[String, Pure]
  type Doing = Doing.T
  object Doing extends Newtype[String, Pure]
  type Open = Open.T
  object Open extends Newtype[Int, Pure]
  type DoneLast7 = DoneLast7.T
  object DoneLast7 extends Newtype[Int, Pure]
  type EtaDays = EtaDays.T
  object EtaDays extends Newtype[Int, Pure]
