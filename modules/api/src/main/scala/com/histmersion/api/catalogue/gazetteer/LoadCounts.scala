package com.histmersion
package api.catalogue.gazetteer

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Plural
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.gazetteer.SourceInfo
import com.histmersion.Newtype

final case class LoadCounts(
    source: LoadCounts.Source,
    version: LoadCounts.Version,
    records: LoadCounts.Records = LoadCounts.Records((0).toLong),
    created: LoadCounts.Created = LoadCounts.Created((0).toLong),
    joined: LoadCounts.Joined = LoadCounts.Joined((0).toLong),
    placesChanged: LoadCounts.PlacesChanged = LoadCounts.PlacesChanged((0).toLong),
    namesAdded: LoadCounts.NamesAdded = LoadCounts.NamesAdded((0).toLong),
    namesRemoved: LoadCounts.NamesRemoved = LoadCounts.NamesRemoved((0).toLong),
    namesChanged: LoadCounts.NamesChanged = LoadCounts.NamesChanged((0).toLong),
    linksAdded: LoadCounts.LinksAdded = LoadCounts.LinksAdded((0).toLong),
    linksRemoved: LoadCounts.LinksRemoved = LoadCounts.LinksRemoved((0).toLong),
    placesRemoved: LoadCounts.PlacesRemoved = LoadCounts.PlacesRemoved((0).toLong),
    skipped: List[LoadCounts.Skipped] = Nil,
    descriptionsAdded: LoadCounts.DescriptionsAdded = LoadCounts.DescriptionsAdded((0).toLong),
    descriptionsRemoved: LoadCounts.DescriptionsRemoved = LoadCounts.DescriptionsRemoved((0).toLong),
    descriptionsChanged: LoadCounts.DescriptionsChanged = LoadCounts.DescriptionsChanged((0).toLong),
    linksChanged: LoadCounts.LinksChanged = LoadCounts.LinksChanged((0).toLong),
    pointAreasSkipped: LoadCounts.PointAreasSkipped = LoadCounts.PointAreasSkipped((0).toLong)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def +(o: LoadCounts): LoadCounts =
    copy(
      records = LoadCounts.Records(records.value + o.records.value),
      created = LoadCounts.Created(created.value + o.created.value),
      joined = LoadCounts.Joined(joined.value + o.joined.value),
      placesChanged = LoadCounts.PlacesChanged(placesChanged.value + o.placesChanged.value),
      namesAdded = LoadCounts.NamesAdded(namesAdded.value + o.namesAdded.value),
      namesRemoved = LoadCounts.NamesRemoved(namesRemoved.value + o.namesRemoved.value),
      namesChanged = LoadCounts.NamesChanged(namesChanged.value + o.namesChanged.value),
      linksAdded = LoadCounts.LinksAdded(linksAdded.value + o.linksAdded.value),
      linksRemoved = LoadCounts.LinksRemoved(linksRemoved.value + o.linksRemoved.value),
      placesRemoved = LoadCounts.PlacesRemoved(placesRemoved.value + o.placesRemoved.value),
      skipped = (skipped ++ o.skipped).take(20),
      descriptionsAdded = LoadCounts.DescriptionsAdded(descriptionsAdded.value + o.descriptionsAdded.value),
      descriptionsRemoved = LoadCounts.DescriptionsRemoved(descriptionsRemoved.value + o.descriptionsRemoved.value),
      descriptionsChanged = LoadCounts.DescriptionsChanged(descriptionsChanged.value + o.descriptionsChanged.value),
      linksChanged = LoadCounts.LinksChanged(linksChanged.value + o.linksChanged.value),
      pointAreasSkipped = LoadCounts.PointAreasSkipped(pointAreasSkipped.value + o.pointAreasSkipped.value)
    )

  def unchanged: Boolean =
    created.value == 0L && placesChanged.value == 0L && namesAdded.value == 0L && namesRemoved.value == 0L && namesChanged.value == 0L &&
      linksAdded.value == 0L && linksRemoved.value == 0L && linksChanged.value == 0L && placesRemoved.value == 0L &&
      descriptionsAdded.value == 0L && descriptionsRemoved.value == 0L && descriptionsChanged.value == 0L

  def describe: String =
    s"$source $version: ${Plural.count(Plural.CountN(records.value), Plural.CountSingular("record"))}, $created new place(s), $joined joined an existing place, " +
      s"$placesChanged place(s) changed, names +$namesAdded −$namesRemoved ~$namesChanged, links +$linksAdded −$linksRemoved" +
      (if linksChanged.value > 0 then s" ~$linksChanged" else "") +
      (if descriptionsAdded.value + descriptionsRemoved.value + descriptionsChanged.value > 0 then
         s", descriptions +$descriptionsAdded −$descriptionsRemoved ~$descriptionsChanged"
       else "") +
      (if placesRemoved.value > 0 then s", $placesRemoved place(s) removed" else "") +
      (if pointAreasSkipped.value > 0 then s", $pointAreasSkipped point-only region(s)/polity(ies) not kept as places"
       else "") +
      (if skipped.nonEmpty then s", ${skipped.size} skipped" else "")

object LoadCounts:

  export SourceInfo.{ImportedVersion as Version, Key as Source}
  type Records = Records.T
  object Records extends Newtype[Long, Pure]
  type Created = Created.T
  object Created extends Newtype[Long, Pure]
  type Joined = Joined.T
  object Joined extends Newtype[Long, Pure]
  type PlacesChanged = PlacesChanged.T
  object PlacesChanged extends Newtype[Long, Pure]
  type NamesAdded = NamesAdded.T
  object NamesAdded extends Newtype[Long, Pure]
  type NamesRemoved = NamesRemoved.T
  object NamesRemoved extends Newtype[Long, Pure]
  type NamesChanged = NamesChanged.T
  object NamesChanged extends Newtype[Long, Pure]
  type LinksAdded = LinksAdded.T
  object LinksAdded extends Newtype[Long, Pure]
  type LinksRemoved = LinksRemoved.T
  object LinksRemoved extends Newtype[Long, Pure]
  type PlacesRemoved = PlacesRemoved.T
  object PlacesRemoved extends Newtype[Long, Pure]
  type Skipped = Skipped.T
  object Skipped extends Newtype[String, Pure]
  type DescriptionsAdded = DescriptionsAdded.T
  object DescriptionsAdded extends Newtype[Long, Pure]
  type DescriptionsRemoved = DescriptionsRemoved.T
  object DescriptionsRemoved extends Newtype[Long, Pure]
  type DescriptionsChanged = DescriptionsChanged.T
  object DescriptionsChanged extends Newtype[Long, Pure]
  type LinksChanged = LinksChanged.T
  object LinksChanged extends Newtype[Long, Pure]
  type PointAreasSkipped = PointAreasSkipped.T
  object PointAreasSkipped extends Newtype[Long, Pure]
