package com.histmersion
package cli

import com.histmersion.util.console.Console

/** The `histmersion` binary. */
@main def histmersionCli(args: String*): Unit = {
  val _ = scribe.Logger.root.clearHandlers().withHandler(writer = scribe.writer.SystemErrWriter).replace()
  given Console = Console.system
  Cli.run(args.toArray)
}
