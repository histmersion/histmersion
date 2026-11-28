package com.histmersion
package api.automation.activity

import com.histmersion.util.problem.Problem
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Run

final case class Entry(
    key: Entry.Key,
    kind: Entry.Kind,
    at: Entry.At,
    state: Entry.State = Entry.State(""),
    what: Entry.What = Entry.What(""),
    text: Entry.Text = Entry.Text(""),
    error: Option[Problem] = None,
    endedAt: Option[Entry.EndedAt] = None,
    goal: Option[Goal.Slug] = None,
    job: Option[Entry.Job] = None,
    by: Option[Entry.By] = None,
    citekey: Option[Citekey] = None,
    article: Option[Entry.Article] = None,
    queueItem: Option[Entry.QueueItem] = None,
    queueList: Option[Entry.QueueList] = None,
    approval: Option[Entry.Approval] = None,
    target: Option[Entry.Target] = None,
    targetKind: Option[Entry.TargetKind] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def entryKind: Option[EntryKind] = EntryKind.parse(kind)

object Entry:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  export EntryKind.Wire as Kind
  type At = At.T
  object At extends Newtype[String, Pure]
  export Run.State.Wire as State
  type What = What.T
  object What extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type EndedAt = EndedAt.T
  object EndedAt extends Newtype[String, Pure]
  export api.SpatialHierarchyResult.Job.Id as Job
  export Writer.Wire as By
  export api.research.ReviewGoal.Slug as Article
  export Goal.Plans.GoalLink.QueueNumber as QueueItem
  type QueueList = QueueList.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object QueueList extends Newtype[String, Pure]
  type Approval = Approval.T
  object Approval extends Newtype[Long, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type TargetKind = TargetKind.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object TargetKind extends Newtype[String, Pure]
