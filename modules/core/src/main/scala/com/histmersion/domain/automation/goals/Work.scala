package com.histmersion
package domain.automation.goals

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.managers.ManagerItem
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.api.automation.Managers.*

final case class Work(
    itemId: Work.ItemId,
    kind: ManagerItem.Kind,
    status: ItemStatus.Wire,
    target: Work.Target = Work.Target(""),
    person: Work.Person = Work.Person(false),
    focusHeld: Work.FocusHeld = Work.FocusHeld(false),
    personHeld: Work.PersonHeld = Work.PersonHeld(false),
    approval: Option[Work.Approval] = None,
    approvalByPerson: Work.ApprovalByPerson = Work.ApprovalByPerson(false)
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def waitsForPerson: Boolean = approval.isDefined && approvalByPerson.value

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def runnable: Boolean = !waitsForPerson && !personHeld.value

object Work:

  type ItemId = ItemId.T
  object ItemId extends Newtype[Long, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Person = Person.T
  object Person extends Newtype[Boolean, Pure]
  type FocusHeld = FocusHeld.T
  object FocusHeld extends Newtype[Boolean, Pure]
  type PersonHeld = PersonHeld.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PersonHeld extends Newtype[Boolean, Pure]
  type Approval = Approval.T
  object Approval extends Newtype[Long, Pure]
  type ApprovalByPerson = ApprovalByPerson.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ApprovalByPerson extends Newtype[Boolean, Pure]

  extension (w: Work)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def blockers(all: List[Work]): List[Work] = w.kind.value match
      case ProposeArticles => all.filter(_.kind.value == PlanGoalItem)
      case ArticleReview   => all.filter(p => p.kind.value == InferFilter && p.target == w.target)
      case _               => Nil
