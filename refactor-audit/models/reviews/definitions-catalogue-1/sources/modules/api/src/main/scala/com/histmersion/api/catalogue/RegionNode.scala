package com.histmersion.api.catalogue

/** A place as the hierarchy shows it: its id, label, kind, period, and whether it is contested or withdrawn. */
final case class RegionNode(
    id: RegionNode.Id,
    label: RegionNode.Label,
    kind: RegionNode.Kind,
    period: Option[Period] = None,
    contested: RegionNode.Contested = false,
    /** Pleiades marks superseded records `withdrawn`; they must never head a level. */
    withdrawn: RegionNode.Withdrawn = false
)

object RegionNode:

  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Contested = Contested.T
  object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Withdrawn = Withdrawn.T
  object Withdrawn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val RootKinds: Set[String] = Set(
    com.histmersion.api.catalogue.Place.Kind.Region,
    com.histmersion.api.catalogue.Place.Kind.Polity,
    "continent"
  )

  /** Pleiades writes an uncertain identification into the label: a trailing `?` on the name or a part of it ("Theren?
    * (river)", "Kisamon? Pr."), and a leading `*` for a reconstructed name ("*Granouas (river)").
    */
  def uncertainLabel(label: String): Boolean =
    val l = label.trim
    l.startsWith("*") || l.contains("?") || l.toLowerCase.startsWith("withdrawn")

  def rootEligible(n: RegionNode): Boolean =
    RootKinds.contains(n.kind) && !n.withdrawn && !uncertainLabel(n.label)
