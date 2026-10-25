package com.histmersion.values

/** The five `kind`s the store recognises. Free text in the column (an importer may carry its own
  * vocabulary), but these are what the CLI/UI offer and what api's `Lpf` maps foreign type labels onto.
  * `people` (2026-10-09) is a people or a civic tribe — the Ubii, the Athenian tribe Aiantis: a group
  * of persons that a gazetteer places at a representative point, never an area (Pleiades files them
  * as regions; `lint --rule place-kind` moves them). */
object PlaceKind:
  val Region  = "region"
  val Polity  = "polity"
  val Site    = "site"
  val Feature = "feature"
  val People  = "people"
  val all: List[String] = List(Region, Polity, Site, Feature, People)

  /** Map a foreign type label (LPF `types[].label`, an AAT id, a Natural Earth class) onto a kind;
    * `None` when nothing recognisable is there, so the caller's default wins. */
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

/** How exact a place's POINT is (V077). Pleiades gives a place whose only location is a square of the
  * Barrington Atlas grid the centre of that square as its representative point: Thessalia (the late
  * Roman province) at 37.5 N 22.5 E — in the Peloponnese — and Viennensis and Tarraconensis both at
  * 42.5 N 2.5 W. Such a point says "somewhere in this square", not "here" ([[GridCell]]).
  *
  * Told from the coordinates alone ([[LocationPrecision.of]]), because the seed keeps only the
  * representative point: a Pleiades point that is the centre of a 5°, 1° or ½° cell. On the live
  * corpus (2026-10-09) 2 426 Pleiades points are, and 2 363 of them are "rough" in Pleiades' own
  * locations dump; no precise point of any size sits exactly on a centre at five decimals. Only
  * Pleiades rows: a Wikidata coordinate in whole minutes (37°15′ = 37.25) is coarse, but it is not
  * an atlas square. `places.location_precision` is the SQL twin (a generated column), held equal by
  * `PlaceCleanupSuite`. */
enum LocationPrecision:
  case Stated, GridCell

  def wire: String = this match
    case Stated   => "stated"
    case GridCell => "grid-cell"

object LocationPrecision:
  /** The atlas grids, as cell sizes in micro-degrees: 5°, 1° and ½°. */
  val CellsMicro: List[Long] = List(5000000L, 1000000L, 500000L)

  /** Is (`lon`, `lat`) — rounded to a micro-degree — the centre of a cell of one of the grids, in
    * both coordinates? Pure. */
  def gridCentre(lon: Double, lat: Double): Boolean =
    val x = math.round(lon * 1e6)
    val y = math.round(lat * 1e6)
    CellsMicro.exists(c => Math.floorMod(x, c) == c / 2 && Math.floorMod(y, c) == c / 2)

  /** The precision of the point of place `placeId`. */
  def of(placeId: String, lon: Double, lat: Double): LocationPrecision =
    if placeId.startsWith("pleiades_") && gridCentre(lon, lat) then GridCell else Stated

  /** What a place says about a grid-cell point ("location: approximate (atlas grid square)" on a card). */
  val Approximate: String = "approximate (atlas grid square)"

  def parse(s: String): Option[LocationPrecision] = values.find(_.wire == Ascii.lower(s.trim))

  given WireEnumValues[LocationPrecision]:
    def all: Array[LocationPrecision] = LocationPrecision.values
    def wire(e: LocationPrecision): String = e.wire
    def fromWire(s: String): Option[LocationPrecision] = parse(s)
