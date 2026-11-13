package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.{Api, Dataset}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.instance.CorpusCounts.Periods as PeriodsApi
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.api.catalogue.PeriodsMarkdown
import com.histmersion.util.console.Console

/** `histmersion periods …` — named historical periods, the web timeline's presets (#719) — and `histmersion init`, the
  * first-run import (#720). Kept in its own file so the shared `Cli` only gains one line.
  *
  * `periods seed` asks the SERVER to import the presets bundled with it: the CLI is a pure HTTP client and a fresh
  * install has no repo checkout to read them from. `--file` sends a PeriodO dataset from this machine instead (the
  * repo's `db/periods/periodo-seed.json`, or a dump).
  */
object PeriodsCmd:

  /** `histmersion init` — what a server does by itself on its first start, on demand. */

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Search(q: Option[String], region: Option[String], from: Option[Long], to: Option[Long])
    case Show(id: String)
    case Seed(file: Option[String], replace: Boolean)
    case Import(file: String, cols: List[String], dataset: Dataset)
    case Fetch(source: String, cols: List[String])
    case Init(fetch: Boolean, noWait: Boolean)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = PeriodsCmd.run(c, this)

  /** Carry out one command against the server. */
  def run(c: Client, cmd: Command)(using console: Console): Either[Cli.Error, Unit] =
    cmd match
      case Command.Search(q, region, from, to) =>
        c.periods(
          q.map(_.trim).filter(_.nonEmpty).map(PeriodsApi.Source(_)),
          region.map(PeriodsApi.Collection(_)),
          from.map(Api.PlacesGeoQuery.From(_)),
          to.map(Api.PlacesGeoQuery.To(_))
        ).map(ps => Cli.out.println(PeriodsMarkdown.list(ps.toVector)))
      case Command.Show(id) =>
        c.period(Period.Id(id)).map(d => Cli.out.println(PeriodsMarkdown.detail(d)))
      case Command.Seed(file, replace) =>
        val r = file.map(Path.of(_)) match
          case None               => c.seedPeriods(Api.Overwrite(replace))
          case Some(_) if replace =>
            Left(
              Cli.Error(
                Problem.Unclassified(Legacy.Text("--replace applies to the bundled seed, not to --file")),
                exitCode = ExitCode.Usage
              )
            )
          case Some(f) if !Files.isRegularFile(f) =>
            Left(Cli.Error(Problem.Unclassified(Legacy.Text(s"no such file: $f")), exitCode = ExitCode.Usage))
          case Some(f) => c.importPeriods(Nil, PeriodsApi.Name(Files.readString(f)), Dataset.Seed)
        r.map(x => Cli.out.println(PeriodsMarkdown.imported(x)))
      case Command.Import(file, cols, dataset) =>
        val f = Path.of(file)
        if !Files.isRegularFile(f) then
          Left(Cli.Error(Problem.Unclassified(Legacy.Text(s"no such file: $file")), exitCode = ExitCode.Usage))
        else
          c.importPeriods(cols.map(PeriodsApi.Source(_)), PeriodsApi.Name(Files.readString(f)), dataset)
            .map(x => Cli.out.println(PeriodsMarkdown.imported(x)))
      case Command.Fetch(source, cols) =>
        c.fetchPeriods(PeriodsApi.Source(source), cols.map(PeriodsApi.Collection(_)))
          .map(r => Cli.out.println(PeriodsMarkdown.imported(r)))
      case Command.Init(fetch, noWait) =>
        c.init(Api.Overwrite(fetch)).flatMap { j =>
          Cli.out.println(s"init job `${j.id}` started (`histmersion jobs` lists it)")
          if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
        }
