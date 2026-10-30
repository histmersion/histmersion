package com.histmersion.values

/** The five `kind`s the store recognises. Free text in the column (an importer may carry its own vocabulary), but these
  * are what the CLI/UI offer and what api's `Lpf` maps foreign type labels onto. `people` (2026-10-09) is a people or a
  * civic tribe — the Ubii, the Athenian tribe Aiantis: a group of persons that a gazetteer places at a representative
  * point, never an area (Pleiades files them as regions; `lint --rule place-kind` moves them).
  */
object PlaceKind:
  val Region = "region"
  val Polity = "polity"
  val Site = "site"
  val Feature = "feature"
  val People = "people"
  val all: List[String] = List(Region, Polity, Site, Feature, People)

  /** Map a foreign type label (LPF `types[].label`, an AAT id, a Natural Earth class) onto a kind; `None` when nothing
    * recognisable is there, so the caller's default wins.
    */
  def parse(s: String): Option[String] =
    val t = s.trim.toLowerCase
    if t.isEmpty then None
    else if all.contains(t) then Some(t)
    else if t.contains("people") || t.contains("tribe") then Some(People)
    else if t.contains("settlement") || t.contains("site") || t.contains("city") || t.contains("town")
      || t.contains("village") || t.contains("fort") || t.contains("temple") || t.contains("sanctuary")
    then Some(Site)
    else if t.contains("kingdom") || t.contains("empire") || t.contains("polity") || t.contains("state")
      || t.contains("dynasty") || t.contains("country") || t.contains("nation")
    then Some(Polity)
    else if t.contains("region") || t.contains("continent") || t.contains("province")
      || t.contains("territory") || t.contains("area")
    then Some(Region)
    else if t.contains("river") || t.contains("mountain") || t.contains("island") || t.contains("lake")
      || t.contains("feature") || t.contains("pass") || t.contains("valley")
    then Some(Feature)
    else None
