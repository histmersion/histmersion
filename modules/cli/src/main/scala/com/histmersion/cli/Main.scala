package com.histmersion.cli

/** The `histmersion` binary. */
@main def histmersionCli(args: String*): Unit = Cli.run(args.toArray)
