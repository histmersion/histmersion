package com.histmersion
package api.automation.autopilot

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.json.TwoStates

final case class FocusInfo(
    on: FocusInfo.On,
    max: FocusInfo.Max = FocusInfo.Max(0),
    maxFrom: FocusInfo.MaxFrom = FocusInfo.MaxFrom("config"),
    active: List[FocusGoal] = Nil,
    queue: List[FocusGoal] = Nil,
    queued: FocusInfo.Queued = FocusInfo.Queued(0),
    waiting: List[FocusWaiting] = Nil,
    held: FocusInfo.Held = FocusInfo.Held(0),
    note: Option[FocusInfo.Note] = None,
    changes: List[FocusInfo.Changes] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object FocusInfo:
  enum On derives CanEqual:
    case On, Off
  object On extends TwoStates[On]:
    extension (on: On) def on: Boolean = on == On
  type Max = Max.T
  object Max extends Newtype[Int, Pure]
  type MaxFrom = MaxFrom.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object MaxFrom extends Newtype[String, Pure]
  type Queued = Queued.T
  object Queued extends Newtype[Int, Pure]
  type Held = Held.T
  object Held extends Newtype[Int, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Changes = Changes.T
  object Changes extends Newtype[String, Pure]
