package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*

import java.nio.file.{Files, Path}

/** `histmersion periods …` — named historical periods, the web timeline's presets (#719) — and
  * `histmersion init`, the first-run import (#720). Kept in its own file so the shared `Main` only
  * gains one line.
  *
  * `periods seed` asks the SERVER to import the presets bundled with it: the CLI is a pure HTTP
  * client and a fresh install has no repo checkout to read them from. `--file` sends a PeriodO
  * dataset from this machine instead (the repo's `db/periods/periodo-seed.json`, or a dump). */
object PeriodsCmd:

  private val search = Opts.subcommand("search", "Find named periods by label, region or time span") {
    (Opts.argument[String]("query").orNone,
      Opts.option[String]("region", "Exact region (case-insensitive), e.g. Crete").orNone,
      Opts.option[Long]("from", "Signed year the period must reach (BCE negative)").orNone,
      Opts.option[Long]("to", "Signed year the period must start by").orNone)
      .mapN { (q, region, from, to) => (c: Client) =>
        c.periods(q.map(_.trim).filter(_.nonEmpty), region, from, to)
          .map(ps => println(PeriodsMarkdown.list(ps.toVector)))
      }
  }

  private val showCmd = Opts.subcommand("show", "One period: its span, authority, what it is part of and its divisions") {
    Opts.argument[String]("id").map { id => (c: Client) =>
      c.period(id).map(d => println(PeriodsMarkdown.detail(d)))
    }
  }

  private val seed = Opts.subcommand("seed",
      "Import the period presets bundled with the server (a curated PeriodO subset); idempotent") {
    (Opts.option[String]("file", "Send this PeriodO dataset instead (e.g. db/periods/periodo-seed.json)").orNone,
      Opts.flag("replace", "Also delete the seed periods the bundled seed no longer has (fetched ones too; your own are kept)").orFalse)
      .mapN { (file, replace) => (c: Client) =>
        val r = file.map(Path.of(_)) match
          case None => c.seedPeriods(replace)
          case Some(_) if replace => Left(CliError("--replace applies to the bundled seed, not to --file", exitCode = ExitCode.Usage))
          case Some(f) if !Files.isRegularFile(f) => Left(CliError(s"no such file: $f", exitCode = ExitCode.Usage))
          case Some(f) => c.importPeriods(Nil, Files.readString(f), Dataset.Seed)
        r.map(x => println(PeriodsMarkdown.imported(x)))
      }
  }

  private val importCmd = Opts.subcommand("import",
      "Upsert a PeriodO dataset of your own (a dump or a subset); its periods are this collection's and travel in a bundle") {
    (Opts.argument[String]("file.json"),
      Opts.options[String]("collection", "Keep only this PeriodO authority id (repeatable)").orEmpty,
      PlacesCmd.datasetOpt)
      .mapN { (file, cols, dataset) => (c: Client) =>
        val f = Path.of(file)
        if !Files.isRegularFile(f) then Left(CliError(s"no such file: $file", exitCode = ExitCode.Usage))
        else c.importPeriods(cols, Files.readString(f), dataset).map(x => println(PeriodsMarkdown.imported(x)))
      }
  }

  private val fetch = Opts.subcommand("fetch",
      "Download PeriodO and import collections, each under one node; re-runnable and idempotent") {
    (Opts.argument[String]("source"),
      Opts.options[String]("collection", "A PeriodO authority id, e.g. p0ds9qj (repeatable; default: config periods.periodo-collections)").orEmpty)
      .mapN { (source, cols) => (c: Client) =>
        c.fetchPeriods(source, cols).map(r => println(PeriodsMarkdown.imported(r)))
      }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("periods", "Named periods (the timeline presets): search, show, seed and fetch from PeriodO") {
      search orElse showCmd orElse seed orElse importCmd orElse fetch
    }

  /** `histmersion init` — what a server does by itself on its first start, on demand. */
  val init: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("init",
        "Import the bundled gazetteer and period presets (a server job); --fetch adds the network sources") {
      (Opts.flag("fetch", "Also download the network gazetteers and PeriodO (config init.fetch-sources)").orFalse,
        Opts.flag("no-wait", "Print the job id and return instead of following it").orFalse)
        .mapN { (fetch, noWait) => (c: Client) =>
          c.init(fetch).flatMap { j =>
            println(s"init job `${j.id}` started (`histmersion jobs` lists it)")
            if noWait then Right(()) else IngestCmd.pollJob(c, j.id, 0)
          }
        }
    }
