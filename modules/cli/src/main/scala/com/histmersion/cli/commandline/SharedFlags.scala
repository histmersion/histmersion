package com.histmersion
package cli
package commandline

import com.histmersion.api.{Dataset, ItemKind}
import com.histmersion.api.exchange.MergeResolutions
import com.histmersion.api.automation.ListFilters
import com.histmersion.cli.CommandLine.{given, *}
import org.rogach.scallop.*
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The flags several command groups share, declared once: the merge resolutions, a bookmark's or a note's target, the
  * filters of the three listings. Each returns the value to read after the command line is verified.
  */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter of the flags several command groups share: declares its options, parses them into typed commands"
)
private[cli] trait SharedFlags extends CommandLine.Declares:

  /** `--resolutions`, `--resolve ID=decision`…, `--editions` (`merge plan|apply`, `bundle import`). */
  protected def resolutionFlags(): () => MergeCmd.ResolutionFlags =
    val file = value[String]("resolutions", "A resolution file (YAML; start one with merge plan --write-template)")
    val resolve = values[String](
      "resolve",
      "Decide one issue: ID=decision, e.g. S-3c1e7a2b=keep-left (repeatable; overrides the file)"
    )
    val editions = value[String]("editions", "Answer every edition (year-gap) question at once: merge or separate")
    () =>
      MergeCmd.ResolutionFlags(
        file.toOption.map(MergeCmd.ResolutionFlags.File(_)),
        resolve.getOrElse(Nil).map(MergeResolutions.Text.Flag(_)),
        editions.toOption.map(MergeCmd.ResolutionFlags.Editions(_))
      )

  /** `<kind> [<id>]` and the flags of a point on the map, a view (with `views`) or a period. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  protected def targetFlags(kinds: Set[ItemKind], views: TargetFlags.Views): () => TargetFlags.Raw =
    val kind = arg[String](s"kind (${ItemKind.values.filter(kinds).map(_.wire).mkString(" | ")})")
    val id = arg[String]("id", required = false)
    val lat = value[Double]("lat", "A point's latitude, degrees north")
    val lon = value[Double]("lon", "A point's longitude, degrees east")
    val zoom =
      Option.when(views.value)(value[Double]("zoom", "A view's map zoom (2 a continent, 5 a country, 9 a city)"))
    val from = value[Int]("from", "The first year (BCE negative)")
    val to = value[Int]("to", "The last year (BCE negative)")
    val view =
      Option.when(views.value)(value[String]("view", "A view's filters: what follows # in Explore's address"))
    () =>
      TargetFlags.Raw(
        kind(),
        id.toOption,
        lat.toOption,
        lon.toOption,
        zoom.flatMap(_.toOption),
        from.toOption,
        to.toOption,
        view.flatMap(_.toOption)
      )

  private def many(name: String, help: String) = values[String](name, s"$help (repeatable, or comma-separated)")
  private def one(name: String, help: String) = value[String](name, help)
  private def since() =
    one("since", "Only those started at or after: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago")
  private def until() = one("until", "Only those started before the end of: 2026-10-01, or an ISO instant")

  /** The filters of `agent approvals`. */
  protected def approvalFilter(): () => ListFilters.ApprovalFilter =
    val state = one("state", ListOpts.Help.approvalState)
    val kind = many("kind", ListOpts.Help.approvalKind)
    val subject =
      one("subject", "Only those about something matching this text: a goal or article slug, a manager, a citekey")
    val goal = one("goal", "Only one goal's: its plan, or proposed by a run for it")
    val decidedBy = many("decided-by", ListOpts.Help.decidedBy)
    () =>
      ListOpts.approvalFilter(
        state.toOption,
        kind.getOrElse(Nil),
        subject.toOption,
        goal.toOption,
        decidedBy.getOrElse(Nil)
      )

  /** The filters of `agent runs`. */
  protected def runFilter(): () => ListFilters.RunFilter =
    val status = many("status", ListOpts.Help.runStatus)
    val pipeline = many("pipeline", ListOpts.Help.pipeline)
    val manager = many("manager", "Only the runs these process managers dispatched (review, enrich, seed:<goal>, …)")
    val goal = one("goal", "Only the runs for this goal")
    val model = many("model", "Only runs a step of which used this provider:model")
    val provider = many("provider", "Only runs a step of which used this provider (codex, claude-code, …)")
    val origin = many("origin", ListOpts.Help.origin)
    val from = since()
    val to = until()
    val subject = one("subject", "Only runs whose target matches this text (a citekey, an article or goal slug)")
    () =>
      ListOpts.runFilter(
        status.getOrElse(Nil),
        pipeline.getOrElse(Nil),
        manager.getOrElse(Nil),
        goal.toOption,
        model.getOrElse(Nil),
        provider.getOrElse(Nil),
        origin.getOrElse(Nil),
        from.toOption,
        to.toOption,
        subject.toOption
      )

  /** The filters of `jobs`. */
  protected def jobFilter(): () => ListFilters.JobFilter =
    val status = many("status", ListOpts.Help.jobStatus)
    val kind =
      many("kind", "Only these job kinds: ingest, reocr, agent, media-import, events-import, init, basemap-fetch, …")
    val from = since()
    val to = until()
    val subject =
      one("subject", "Only jobs about something matching this text (citekey, target, detail), or whose id starts so")
    () => ListOpts.jobFilter(status.getOrElse(Nil), kind.getOrElse(Nil), from.toOption, to.toOption, subject.toOption)

  /** `--dataset local|seed` (`periods import`, `places import`): whose the imported rows are; local by default. */
  protected def datasetFlag(): () => Dataset =
    given ValueConverter[Dataset] =
      reading(s => Dataset.parse(Dataset.Wire(s)).toRight(s"--dataset $s: expected local or seed"))
    given Metavar[Dataset] = Metavar.of("string")
    val d = value[Dataset](
      "dataset",
      "Whose they are: local (this collection's own — the default; carried in a bundle) or seed (reference data every instance imports)"
    )
    () => d.getOrElse(Dataset.Local)

  /** `config get|set|unset --local`. */
  protected def localFlag(): () => Boolean =
    val f = flag(
      "local",
      "Read or change the settings file on this machine directly, without the server (the default when it does not answer)"
    )
    () => f.getOrElse(false)

  /** `config set|unset --no-reload`. */
  protected def noReloadFlag(): () => Boolean =
    val f = flag("no-reload", "Change the file only; do not make the server apply it")
    () => f.getOrElse(false)

  /** `config set --literal-secret`. */
  protected def literalSecretFlag(): () => Boolean =
    val f = flag(
      "literal-secret",
      "Allow a credential written as value:<the value itself> (anyone who can read the file can read it)"
    )
    () => f.getOrElse(false)

  /** `--model provider:model` of an agent call. */
  protected def modelFlag(): () => Option[String] =
    val m = value[String]("model", "provider:model for this call (default: the role's configured model)")
    () => m.toOption

  /** `--follow` of a started run. */
  protected def followFlag(): () => Boolean =
    val f = flag("follow", "Wait for the run to finish (or pause, or wait for an approval), printing its progress")
    () => f.getOrElse(false)

  /** `--before` of a listing's next page. */
  protected def beforeFlag(what: String): () => Option[String] =
    val b =
      value[String]("before", s"The next older page: the id of the last $what shown (the listing prints the command)")
    () => b.toOption
