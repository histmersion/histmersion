package com.histmersion
package api.catalogue

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates

/** A place as the hierarchy shows it: its id, label, kind, period, and whether it is contested or withdrawn. */
final case class RegionNode(
    id: RegionNode.Id,
    label: RegionNode.Label,
    kind: RegionNode.Kind,
    period: Option[Period] = None,
    contested: RegionNode.Contested = RegionNode.Contested.Settled,
    /** Pleiades marks superseded records `withdrawn`; they must never head a level. */
    withdrawn: RegionNode.Withdrawn = RegionNode.Withdrawn.Current
)

object RegionNode:
  type RootKindsText = RootKindsText.T
  object RootKindsText extends Newtype[String, Pure]

  type RootKinds = RootKindsValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type RootKindsValue = RootKindsText
  val RootKindsValue: RootKindsText.type = RootKindsText

  type Id = Id.T
  object Id extends Newtype[String, Pure]
  export Place.Label
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  enum Contested derives CanEqual:
    case Contested, Settled
  object Contested extends TwoStates[Contested]:
    extension (contested: Contested) def contested: Boolean = contested == Contested
  enum Withdrawn derives CanEqual:
    case Withdrawn, Current
  object Withdrawn extends TwoStates[Withdrawn]:
    extension (withdrawn: Withdrawn) def withdrawn: Boolean = withdrawn == Withdrawn

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val RootKinds: Set[RootKinds] = Set(
    RootKindsText(Place.Kind.Region),
    RootKindsText(Place.Kind.Polity),
    RootKindsText("continent")
  )

  /** Pleiades writes an uncertain identification into the label: a trailing `?` on the name or a part of it ("Theren?
    * (river)", "Kisamon? Pr."), and a leading `*` for a reconstructed name ("*Granouas (river)").
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def uncertainLabel(label: Label): Boolean =
    val l = (label).value.trim
    (l.startsWith("*") || l.contains("?") || l.toLowerCase.startsWith("withdrawn"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rootEligible(n: RegionNode): Boolean =
    RootKinds.contains(RootKindsText((n.kind).value)) && !((n.withdrawn) == Withdrawn.Withdrawn) && !((uncertainLabel(
      Label((n.label).value)
    )) == (true))
