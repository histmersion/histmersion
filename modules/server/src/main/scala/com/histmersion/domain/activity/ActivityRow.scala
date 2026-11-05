package com.histmersion.domain.activity

import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.Activity.{Entry, Show}
import com.histmersion.util.database.{Param, Sql}

import com.augustnagro.magnum.*

import java.time.Instant

/** One row of the merged list, in the order of [[ActivityRepo.Merged]]'s columns. */
final private[activity] case class ActivityRow(
    key: ActivityRow.Key,
    kind: ActivityRow.Kind,
    at: ActivityRow.At,
    state: ActivityRow.State,
    what: ActivityRow.What,
    text: ActivityRow.Text,
    error: Option[ActivityRow.Error],
    endedAt: Option[ActivityRow.EndedAt],
    goal: Option[ActivityRow.Goal],
    job: Option[ActivityRow.Job],
    by: Option[ActivityRow.By],
    citekey: Option[com.histmersion.domain.library.Citekey],
    article: Option[ActivityRow.Article],
    queueItem: Option[ActivityRow.QueueItem],
    queueList: Option[ActivityRow.QueueList],
    approval: Option[ActivityRow.Approval],
    target: Option[ActivityRow.Target],
    targetKind: Option[ActivityRow.TargetKind]
) derives DbCodec:
  def entry: Entry = Entry(
    key,
    kind,
    at,
    state,
    what,
    text,
    error,
    endedAt,
    goal,
    job,
    by,
    citekey,
    article,
    queueItem,
    queueList,
    approval,
    target,
    targetKind
  )

object ActivityRow:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type At = At.T
  object At extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type State = State.T
  object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type What = What.T
  object What extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Error = Error.T
  object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndedAt = EndedAt.T
  object EndedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Job = Job.T
  object Job extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type By = By.T
  object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Article = Article.T
  object Article extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueItem = QueueItem.T
  object QueueItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueList = QueueList.T
  object QueueList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Approval = Approval.T
  object Approval extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Target = Target.T
  object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetKind = TargetKind.T
  object TargetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
