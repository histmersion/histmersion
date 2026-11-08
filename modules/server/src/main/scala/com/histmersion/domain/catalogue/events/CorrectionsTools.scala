package com.histmersion
package domain.catalogue.events

import com.histmersion.api.Problem
import com.histmersion.api.json.NewtypeCodecs.given
import chimp.server.*
import com.histmersion.api.catalogue.Corrections
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.Correction

/** The corrections by hand of catalogue events, for agents. The import and the re-derivation are not tools, on purpose:
  * an import is an operator's decision that sends traffic to a third party (`histmersion events import`), a
  * re-derivation rewrites every catalogue event at once, an operator's step after a deploy.
  */
object CorrectionsTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class EventCorrectionsInput(
      status: Option[EventCorrectionsInput.Status] = None,
      id: Option[EventCorrectionsInput.Id] = None,
      limit: Option[EventCorrectionsInput.Limit] = None
  ) derives KindlingsCodecAsObject
  object EventCorrectionsInput:
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]

    given Schema[EventCorrectionsInput] = KindlingsSchema
      .derived[EventCorrectionsInput]
      .schema
      .modify(_.status)(
        _.description("active | agreed (retired: the catalogue now agrees) | conflict; default: all but agreed")
      )
      .modify(_.id)(_.description("Only this event's corrections"))

  def apply(imports: EventImportsControllers) =
    val corrections =
      tool("event_corrections")
        .description(
          "The fields of CATALOGUE events (GVP, NOAA, Wikidata imports) a person corrected by hand with " +
            "`patch_event`: each keeps the person's value over the catalogue's on every re-import. A " +
            "correction is retired automatically (`agreed`) once the catalogue says the same; when the " +
            "catalogue changes the field to something else it stays applied and is flagged `conflict` for " +
            "review (conflicts are listed first). Each line: the field, the person's value, what the " +
            "catalogue said then and, in conflict, what it says now."
        )
        .input[EventCorrectionsInput]
        .handle { in =>
          val event: Either[Api.Error, Option[Event.Id]] =
            in.id.map(_.value.trim).filter(_.nonEmpty) match
              case None    => Right(None)
              case Some(i) =>
                Event.Id
                  .either(i)
                  .left
                  .map(e => Api.Error(Problem(e)))
                  .map(Some(_))
          ToolResult.text(
            event
              .flatMap(e =>
                imports.corrections(
                  in.status.map(s => Correction.StatusValue(s.value)),
                  e,
                  in.limit.flatMap(l => PageSize.option(l.value)),
                  None
                )
              )
              .fold(McpOut.refusal, c => Corrections.markdown(c).value)
          )
        }
    List(corrections)
