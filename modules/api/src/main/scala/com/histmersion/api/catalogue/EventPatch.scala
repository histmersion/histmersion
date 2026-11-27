package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.{StrictJson, TimeSpan}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventPatch(
    label: Option[EventSummary.Label] = None,
    kind: Option[EventSummary.Kind] = None,
    timeSpan: Option[TimeSpan] = None,
    historicity: Option[EventSummary.Historicity] = None,
    geometry: Option[EventPatch.Geometry] = None,
    placeId: Option[Place.Id] = None,
    parentId: Option[Event.Id] = None,
    description: Option[EventSummary.Description] = None,
    citekey: Option[Citekey] = None,
    datings: Option[List[EventDating]] = None,
    edges: Option[List[EventEdge]] = None,
    participants: Option[List[EventParticipant]] = None,
    scale: Option[EventSummary.Scale] = None,
    wikidata: Option[EventPatch.Wikidata] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema, CanEqual
object EventPatch:

  type Geometry = Geometry.T
  object Geometry extends Newtype[String, Pure]
  type Wikidata = Wikidata.T
  object Wikidata extends Newtype[String, Pure]
