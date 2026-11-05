package com.histmersion.domain.library.workflows

import com.histmersion.api.library.{Acquire, FollowupRow}
import com.histmersion.api.given

import com.histmersion.domain.AgentProposals.Proposal
import com.histmersion.api.automation.Approval
import com.histmersion.api.library.Edge

/** The pure rules of a currency check (docs/research-embedded-agents.md §14): what it makes of the papers citing a
  * source, and the acquisition item a disagreeing paper outside the corpus becomes.
  */
object CurrencyCheckRules:

  /** What a currency check makes of one paper citing `target`: a disagreement edge to propose (both ends in the
    * corpus), a paper to queue for acquisition (it disagrees but is not in the corpus), or nothing (it confirms or
    * merely cites). `supersedes` becomes `superseded_by` from the older source to the newer, the direction the corpus
    * records. Evidence is the citation context: code never invents a quote.
    */
  type CurrencyActionTarget = CurrencyActionTarget.T
  object CurrencyActionTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcquireTextResult = AcquireTextResult.T
  object AcquireTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcquireTextTarget = AcquireTextTarget.T
  object AcquireTextTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcquireTextGoal = AcquireTextGoal.T
  object AcquireTextGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum CurrencyAction:
    case Propose(p: Proposal)
    case Fetch(row: FollowupRow)
    case Ignore

  def currencyAction(target: com.histmersion.domain.library.workflows.CurrencyCheckRules.CurrencyActionTarget, row: FollowupRow): CurrencyAction =
    val kind = row.proposedKind.trim.toLowerCase
    val disagreement = kind == Acquire.EdgeKinds.Refutes || kind == Acquire.EdgeKinds.Challenges ||
      kind == Acquire.EdgeKinds.Supersedes
    if !disagreement || row.citekey.contains(target) then CurrencyAction.Ignore
    else
      row.citekey.filter(_ != target) match
        case None if row.doi.isDefined => CurrencyAction.Fetch(row)
        case None                      => CurrencyAction.Ignore
        case Some(citing)              =>
          val context = row.contexts.map(_.trim).filter(_.nonEmpty).headOption
          val evidence = s"followups: the citing paper reads as '$kind'" +
            (if row.intents.nonEmpty then s" (intents: ${row.intents.mkString(", ")})" else "") +
            context.fold("")(c => s"; context: ${c.take(400)}")
          val (from, edge, to) =
            if kind == Acquire.EdgeKinds.Supersedes then (target, Edge.Kind.SupersededBy, citing)
            else (citing, if kind == Acquire.EdgeKinds.Refutes then Edge.Kind.Refutes else Edge.Kind.Challenges, target)
          CurrencyAction.Propose(
            Proposal(Approval.Kind.Edge, from, edgeKind = Some(edge), target = Some(to), evidence = evidence)
          )

  /** The acquisition item a disagreeing paper outside the corpus becomes. */
  def acquireText(row: FollowupRow, target: com.histmersion.domain.library.workflows.CurrencyCheckRules.AcquireTextTarget, goal: com.histmersion.domain.library.workflows.CurrencyCheckRules.AcquireTextGoal): com.histmersion.domain.library.workflows.CurrencyCheckRules.AcquireTextResult =
    val year = row.year.fold("")(y => s" ($y)")
    s"${row.title.getOrElse("untitled")}$year. https://doi.org/${row.doi.getOrElse("")} — " +
      s"${row.proposedKind} [[$target]] (currency check, goal $goal)"
