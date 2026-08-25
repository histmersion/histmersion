package histdb.cli

import cats.syntax.all.*
import com.monovore.decline.*
import histdb.core.*

/** hist-db CLI — a pure HTTP client of the server (no DB access on the host). Every command maps to
  * one `Api` endpoint and prints the shared Markdown rendering, so CLI and MCP output agree. */
object Main:

  private val serverOpt =
    Opts.option[String]("server", "hist-db server URL (default: config cli.serverUrl / $HISTDB_SERVER_URL)").orNone

  private val version = Opts.subcommand("version", "Print the CLI and server versions") {
    Opts.unit.map(_ => (c: Client) =>
      println(s"cli    ${Version.name} ${Version.version}")
      c.version.map(v => println(s"server ${v.name} ${v.version}")))
  }

  private val health = Opts.subcommand("health", "Server health (db, schema version)") {
    Opts.unit.map(_ => (c: Client) => c.health.map(h => println(s"${h.status} · db ${h.db} · schema ${h.schemaVersion.getOrElse("?")}")))
  }

  private val seed = Opts.subcommand("seed", "Import a wiki/sources directory (a path visible to the SERVER; empty = configured corpus dir)") {
    (Opts.argument[String]("dir").withDefault(""), Opts.flag("force", "Overwrite rows newer than the files").orFalse)
      .mapN((dir, force) => (c: Client) =>
        c.seed(SeedRequest(dir, force)).map { r =>
          println(s"imported ${r.imported}, failed ${r.failed.size}, edges unified ${r.edgesResolved}")
          r.failed.take(10).foreach(f => Console.err.println(s"  ! $f"))
        })
  }

  // ── the shared Filter flags (plan §1.0): identical set on `search` and `facets` ──────────────
  private val filterFlags: Opts[Filter] = (
    Opts.options[String]("tag", "Tag that must be present (repeatable; ALL are required)").orEmpty,
    Opts.options[String]("author", "Author substring (repeatable; ANY matches)").orEmpty,
    Opts.options[String]("place", "Place/region name (repeatable; ANY matches)").orEmpty,
    Opts.options[String]("topic", "Topic that must be present (repeatable; ALL are required)").orEmpty,
    Opts.options[String]("status", s"Source status (repeatable; ANY matches)").orEmpty,
    Opts.option[Long]("year-from", "Earliest publication year").orNone,
    Opts.option[Long]("year-to", "Latest publication year").orNone,
    Opts.option[Long]("from", "Deep-time start (signed year, BCE negative)").orNone,
    Opts.option[Long]("to", "Deep-time end (signed year)").orNone,
    Opts.option[String]("bbox", "minLon,minLat,maxLon,maxLat (minLon > maxLon crosses the antimeridian)").orNone,
    Opts.option[String]("sort", s"Ordering: ${Sort.all.mkString("|")}").orNone,
    Opts.option[Int]("limit", "Max results").orNone,
    Opts.option[Int]("offset", "Skip this many results").orNone
  ).mapN { (tags, authors, places, topics, status, yearFrom, yearTo, from, to, bbox, sort, limit, offset) =>
    val b = bbox.flatMap(BBox.parse)
    Filter.of(None, tags, authors, places, topics, status, yearFrom, yearTo, from, to,
      b.map(_.minLon), b.map(_.minLat), b.map(_.maxLon), b.map(_.maxLat), sort, limit, offset)
  }

  private val search = Opts.subcommand("search", "Search the corpus: sources with the findings that matched") {
    (Opts.argument[String]("query").orNone, filterFlags).mapN { (q, f) => (c: Client) =>
      c.sources(f.copy(q = q.map(_.trim).filter(_.nonEmpty)))
        .map(r => println(Markdown.searchResults(r)))
    }
  }

  private val facets = Opts.subcommand("facets", s"Vocabulary of a dimension (${FacetField.all.mkString("|")}) with distinct-source counts") {
    (
      Opts.argument[String]("field"),
      Opts.option[String]("prefix", "Case-insensitive prefix match").orNone,
      Opts.argument[String]("query").orNone,
      filterFlags
    ).mapN { (field, prefix, q, f) => (c: Client) =>
      c.facets(field, prefix, f.copy(q = q.map(_.trim).filter(_.nonEmpty)))
        .map(rows => println(Markdown.facets(field, rows.toVector)))
    }
  }

  private val get = Opts.subcommand("get", "Show one source as Markdown") {
    (Opts.argument[String]("citekey"), Opts.flag("full", "Include full text").orFalse)
      .mapN((ck, full) => (c: Client) => c.getSource(ck).map(sw => println(Markdown.source(sw.source, sw.findings.toVector, full))))
  }

  private val geoWhen = Opts.subcommand("geo-when", "Findings by region and/or deep-time range and/or bbox") {
    (
      Opts.option[String]("region", "Region-name substring").orNone,
      Opts.option[Long]("from", "Period start (signed year, BCE negative)").orNone,
      Opts.option[Long]("to", "Period end (signed year)").orNone,
      Opts.option[String]("bbox", "minLon,minLat,maxLon,maxLat").orNone,
      Opts.option[Int]("limit", "Max results").orNone
    ).mapN { (region, from, to, bbox, limit) => (c: Client) =>
      val b = bbox.map(_.split(",").map(_.trim.toDouble)).filter(_.length == 4)
      val q = Api.FindingsQuery(region, b.map(_(0)), b.map(_(1)), b.map(_(2)), b.map(_(3)), from, to, limit)
      c.findings(q).map(hits => println(Markdown.geoResults(hits.toVector)))
    }
  }

  private val neighbors = Opts.subcommand("neighbors", "Typed references to/from a source") {
    Opts.argument[String]("citekey").map(ck => (c: Client) => c.neighbors(ck).map(rows => println(Markdown.neighbors(ck, rows.toVector))))
  }

  private val command: Command[(Option[String], Client => Either[CliError, Unit])] =
    Command(name = "histdb", header = "hist-db — CLI over the hist-db server (MCP twin)") {
      (serverOpt,
        version orElse health orElse seed orElse search orElse facets orElse get orElse geoWhen orElse neighbors
      ).tupled
    }

  def main(args: Array[String]): Unit =
    command.parse(args.toList, sys.env) match
      case Left(help) => System.err.println(help); sys.exit(1)
      case Right((server, run)) =>
        val url = server.getOrElse(AppConfig.load().cli.serverUrl)
        run(Client(url)) match
          case Right(()) => ()
          case Left(e)   => System.err.println(s"error: ${e.message}"); sys.exit(e.exitCode)
