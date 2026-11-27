package com.histmersion
package domain.catalogue.periods
import com.histmersion.util.mcp.ChimpJson.given

import com.histmersion.api.json.NewtypeCodecs.given
import chimp.server.*
import com.histmersion.api.catalogue.{HistoricalPeriod, PeriodsMarkdown}
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Named periods for agents — the same two modes as `places`: no `id` → search, `id` → show. */
object PeriodsTools:
  import com.histmersion.util.mcp.ToolInputs.given

  /** Input of the `periods` tool. */
  final case class PeriodsInput(
      query: Option[PeriodsInput.Query] = None,
      id: Option[PeriodsInput.Id] = None,
      region: Option[PeriodsInput.Region] = None,
      from: Option[PeriodsInput.From] = None,
      to: Option[PeriodsInput.To] = None
  ) derives JsonCodec
  object PeriodsInput:
    type Query = Query.T
    object Query extends Newtype[String, Pure]
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    export PeriodSearch.Region
    export PeriodSearch.From
    export PeriodSearch.To

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[PeriodsInput] = KindlingsSchema
      .derived[PeriodsInput]
      .schema
      .modify(_.query)(_.description("Label substring (case-insensitive), e.g. 'Minoan'"))
      .modify(_.id)(_.description("Period id (a PeriodO id such as p0ds9qjq7b2) to show"))
      .modify(_.region)(_.description("Exact region, case-insensitive (e.g. 'Crete', 'Levant')"))
      .modify(_.from)(_.description("Signed year (BCE negative): periods overlapping from..to"))
      .modify(_.to)(_.description("Signed year (BCE negative): periods overlapping from..to"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(periods: PeriodsControllers) =
    List(
      tool("periods")
        .description(
          "NAMED PERIODS: PeriodO definitions of historical/archaeological periods (Bronze Age, Late " +
            "Minoan IB, Old Babylonian, Yamnaya…), each with its span in signed years (BCE negative), " +
            "its region and the published authority that states the range. Without `id` it SEARCHES " +
            "by label (`query`), exact `region`, and/or overlap with `from`..`to`; with `id` it SHOWS " +
            "one period with what it is part of and how it is divided. Use it to turn a period name in " +
            "a question into the years to pass as `from`/`to` to `search`/`geo_when` — and cite the " +
            "authority, since two authorities date the same name differently."
        )
        .input[PeriodsInput]
        .handle { in =>
          in.id.map(_.value.trim).filter(_.nonEmpty) match
            case Some(id) =>
              periods.period(HistoricalPeriod.Id(id)) match
                case Right(d) => ToolResult.text(PeriodsMarkdown.detail(d))
                case Left(e)  => McpOut.error(e)
            case None =>
              ToolResult.text(
                PeriodsMarkdown
                  .list(
                    periods
                      .search(
                        in.query.map(_.value.trim).filter(_.nonEmpty).map(PeriodSearch.Text(_)),
                        in.region.map(g => g),
                        in.from.map(y => y),
                        in.to.map(y => y)
                      )
                      .toVector
                  )
              )
        }
    )
