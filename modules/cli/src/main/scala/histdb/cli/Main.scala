package histdb.cli

import com.monovore.decline.*
import histdb.core.Version

/** hist-db CLI entrypoint — direct-style (decline core, no effect system). Phase-1 skeleton: a
  * `version` command. Next: `serve`, `calibre`, `ingest`, `search`/`geo-when`/`neighbors`, and an
  * sttp client derived from `core`'s Tapir endpoint definitions. */
object Main:

  private val command: Command[Unit] =
    Command(
      name = "histdb",
      header = "hist-db — CLI over the hist-db server + local Calibre/file ops"
    ) {
      Opts.subcommand("version", "Print the hist-db version") {
        Opts.unit.map(_ => println(s"${Version.name} ${Version.version}"))
      }
    }

  def main(args: Array[String]): Unit =
    command.parse(args.toList) match
      case Left(help) => System.err.println(help); sys.exit(1)
      case Right(_)   => ()
