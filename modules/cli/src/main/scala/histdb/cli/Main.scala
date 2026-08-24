package histdb.cli

import cats.effect.*
import com.monovore.decline.*
import com.monovore.decline.effect.*
import histdb.core.Version

/** hist-db CLI entrypoint. Phase-1 skeleton: a `version` command. Next: `serve`, `calibre`,
  * `ingest`, `search`/`geo-when`/`neighbors`, `enrich`/`tag`/`link`, `export`/`backup`/`restore` —
  * the terminal face of the same `core` service functions the MCP tools expose. */
object Main
    extends CommandIOApp(
      name = "histdb",
      header = "hist-db — CLI over the hist-db server + local Calibre/file ops"
    ):

  private val versionCmd: Opts[IO[ExitCode]] =
    Opts.subcommand("version", "Print the hist-db version") {
      Opts.unit.map(_ => IO.println(s"${Version.name} ${Version.version}").as(ExitCode.Success))
    }

  def main: Opts[IO[ExitCode]] = versionCmd
