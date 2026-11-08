package com.histmersion
package api.catalogue

import io.github.iltotore.iron.constraint.any.Pure

/** A place as the hierarchy shows it: its id, label, kind, period, and whether it is contested or withdrawn. */
final case class RegionNode(
    id: RegionNode.Id,
    label: RegionNode.Label,
    kind: RegionNode.Kind,
    period: Option[Period] = None,
    contested: RegionNode.Contested = RegionNode.Contested(false),
    /** Pleiades marks superseded records `withdrawn`; they must never head a level. */
    withdrawn: RegionNode.Withdrawn = RegionNode.Withdrawn(false)
)

object RegionNode:
  type RootKindsText = RootKindsText.T
  object RootKindsText extends Newtype[String, Pure]

  type RootKinds = RootKindsValue
  type RootKindsValue = RootKindsText
  val RootKindsValue: RootKindsText.type = RootKindsText

  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Contested = Contested.T
  object Contested extends Newtype[Boolean, Pure]
  type Withdrawn = Withdrawn.T
  object Withdrawn extends Newtype[Boolean, Pure]

  val RootKinds: Set[RootKinds] = Set(
    RootKindsText(Place.Kind.Region),
    RootKindsText(Place.Kind.Polity),
    RootKindsText("continent")
  )

  /** Pleiades writes an uncertain identification into the label: a trailing `?` on the name or a part of it ("Theren?
    * (river)", "Kisamon? Pr."), and a leading `*` for a reconstructed name ("*Granouas (river)").
    */
  def uncertainLabel(label: Label): Boolean =
    val l = (label).value.trim
    (l.startsWith("*") || l.contains("?") || l.toLowerCase.startsWith("withdrawn"))

  def rootEligible(n: RegionNode): Boolean =
    RootKinds.contains(RootKindsText((n.kind).value)) && !((n.withdrawn) == Withdrawn(true)) && !((uncertainLabel(
      Label((n.label).value)
    )) == (true))
