package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What one import (or its dry run) did, per source. `created` places are new; `joined` records found their place by an
  * identifier; the name and link counts are rows inserted, removed and changed — a second run of the same version
  * reports zeros.
  */
final case class GazetteerLoadCounts(
    source: String,
    version: String,
    records: Long = 0,
    created: Long = 0,
    joined: Long = 0,
    placesChanged: Long = 0,
    namesAdded: Long = 0,
    namesRemoved: Long = 0,
    namesChanged: Long = 0,
    linksAdded: Long = 0,
    linksRemoved: Long = 0,
    placesRemoved: Long = 0,
    skipped: List[String] = Nil,
    descriptionsAdded: Long = 0,
    descriptionsRemoved: Long = 0,
    descriptionsChanged: Long = 0,
    /** Links whose relation or item facts (class, point, dates — V077) changed. */
    linksChanged: Long = 0,
    /** Records not kept as places of their own: a GeoNames/Wikidata region, polity or people known only by a point
      * ([[Gazetteer.dropsPointArea]]).
      */
    pointAreasSkipped: Long = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def +(o: GazetteerLoadCounts): GazetteerLoadCounts =
    copy(
      records = records + o.records,
      created = created + o.created,
      joined = joined + o.joined,
      placesChanged = placesChanged + o.placesChanged,
      namesAdded = namesAdded + o.namesAdded,
      namesRemoved = namesRemoved + o.namesRemoved,
      namesChanged = namesChanged + o.namesChanged,
      linksAdded = linksAdded + o.linksAdded,
      linksRemoved = linksRemoved + o.linksRemoved,
      placesRemoved = placesRemoved + o.placesRemoved,
      skipped = (skipped ++ o.skipped).take(20),
      descriptionsAdded = descriptionsAdded + o.descriptionsAdded,
      descriptionsRemoved = descriptionsRemoved + o.descriptionsRemoved,
      descriptionsChanged = descriptionsChanged + o.descriptionsChanged,
      linksChanged = linksChanged + o.linksChanged,
      pointAreasSkipped = pointAreasSkipped + o.pointAreasSkipped
    )

  /** Nothing would change: the store already holds exactly this version. */
  def unchanged: Boolean =
    created == 0 && placesChanged == 0 && namesAdded == 0 && namesRemoved == 0 && namesChanged == 0 &&
      linksAdded == 0 && linksRemoved == 0 && linksChanged == 0 && placesRemoved == 0 &&
      descriptionsAdded == 0 && descriptionsRemoved == 0 && descriptionsChanged == 0

  /** One line for a job summary. */
  def describe: String =
    s"$source $version: ${Plural.count(records, "record")}, $created new place(s), $joined joined an existing place, " +
      s"$placesChanged place(s) changed, names +$namesAdded −$namesRemoved ~$namesChanged, links +$linksAdded −$linksRemoved" +
      (if linksChanged > 0 then s" ~$linksChanged" else "") +
      (if descriptionsAdded + descriptionsRemoved + descriptionsChanged > 0 then
         s", descriptions +$descriptionsAdded −$descriptionsRemoved ~$descriptionsChanged"
       else "") +
      (if placesRemoved > 0 then s", $placesRemoved place(s) removed" else "") +
      (if pointAreasSkipped > 0 then s", $pointAreasSkipped point-only region(s)/polity(ies) not kept as places"
       else "") +
      (if skipped.nonEmpty then s", ${skipped.size} skipped" else "")
object GazetteerLoadCounts:
  given Schema[GazetteerLoadCounts] = summon[KindlingsSchema[GazetteerLoadCounts]].schema
