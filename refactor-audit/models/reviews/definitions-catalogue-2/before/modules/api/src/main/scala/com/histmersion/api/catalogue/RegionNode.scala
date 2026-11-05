package com.histmersion.api.catalogue

/** A place as the hierarchy shows it: its id, label, kind, period, and whether it is contested or withdrawn. */
final case class RegionNode(
    id: String,
    label: String,
    kind: String,
    period: Option[Period] = None,
    contested: Boolean = false,
    /** Pleiades marks superseded records `withdrawn`; they must never head a level. */
    withdrawn: Boolean = false
)

object RegionNode:

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
