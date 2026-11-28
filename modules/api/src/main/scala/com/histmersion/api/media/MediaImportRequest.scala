package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.RepairAcquire
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Event
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class MediaImportRequest(
    provider: MediaItem.Provider = MediaItem.Provider("wikidata"),
    dryRun: MediaImportRequest.DryRun = RepairAcquire.RepairRequest.DryRun.DryRun,
    limit: Option[MediaImportRequest.Limit] = None,
    place: Option[Place.Id] = None,
    event: Option[Event.Id] = None,
    refresh: MediaImportRequest.Refresh = MediaImportRequest.Refresh.Reuse,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object MediaImportRequest:
  export RepairAcquire.RepairRequest.DryRun
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  enum Refresh derives CanEqual:
    case Refresh, Reuse
  object Refresh extends TwoStates[Refresh]:
    extension (refresh: Refresh) def refresh: Boolean = refresh == Refresh
