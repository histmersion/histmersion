package com.histmersion
package api.jobs

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

/** A kind of job as its owner registers it: its name and meaning for a person, whether a person may pause and cancel
  * its jobs, whether those go to the kind's own runner instead (an agent run's), and what one of its jobs is about,
  * read from the detail its jobs store.
  */
final case class JobKindEntry(
    kind: JobInfo.Kind,
    name: JobKindName,
    meaning: JobKindMeaning,
    stoppable: JobKindEntry.Stoppable = JobKindEntry.Stoppable.Unstoppable,
    subject: JobDetail => Option[JobKinds.Subject] = _ => None,
    ownControl: JobKindEntry.OwnControl = JobKindEntry.OwnControl.Shared
)

object JobKindEntry:
  export com.histmersion.api.jobs.JobKindMeaning as OfMeaning
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type OfStoppable = Stoppable
  val OfStoppable: Stoppable.type = Stoppable
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type OfOwnControl = OwnControl
  val OfOwnControl: OwnControl.type = OwnControl
  enum Stoppable derives CanEqual:
    case Stoppable, Unstoppable
  object Stoppable extends TwoStates[Stoppable]:
    extension (stoppable: Stoppable) def stoppable: Boolean = stoppable == Stoppable
  enum OwnControl derives CanEqual:
    case OwnControl, Shared
  object OwnControl extends TwoStates[OwnControl]:
    extension (ownControl: OwnControl) def ownControl: Boolean = ownControl == OwnControl

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(
      kind: JobInfo.Kind,
      name: JobKinds.Wire,
      meaning: JobKindMeaning,
      stoppable: OfStoppable = OfStoppable.Unstoppable,
      subject: JobDetail => Option[JobKinds.Subject] = _ => None,
      ownControl: OfOwnControl = OfOwnControl.Shared
  ): JobKindEntry = JobKindEntry(
    kind,
    JobKindName((name).value),
    meaning,
    stoppable, {
      val refactorFunction2 = subject;
      (refactorValue3) => (refactorFunction2(refactorValue3)).map(refactorValue4 => refactorValue4)
    },
    ownControl
  )
