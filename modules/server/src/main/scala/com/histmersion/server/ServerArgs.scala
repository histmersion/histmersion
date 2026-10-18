package com.histmersion.server

/** The server binary's command line. It takes almost nothing — every setting is config — but what it
  * does not know is an ERROR that ends the process before anything is opened: once, `--help` started
  * a real server against the live database (docs/research-plugins.md §10.3). */
enum ServerMode:
  /** No arguments: run the server. */
  case Serve
  /** `--self-check`: load the config and the plugins, verify them, print a report and exit — no
    * database connection, no bundled runtime, no listener. */
  case SelfCheck
  /** `--help` / `-h`: print the usage and exit. */
  case Help

object ServerArgs:

  val Usage: String =
    """usage: histmersion-server [--self-check | --help]
      |
      |  (no arguments)  run the server: config from reference.conf < histmersion.conf < the environment
      |  --self-check    load the config and the plugins, resolve their secrets, print a report and exit
      |                  (0 = all good, 2 = a plugin failed or was refused, 3 = the config is broken);
      |                  opens no database connection and no port
      |  --help, -h      this text""".stripMargin

  /** Pure: the mode, or the usage error. */
  def parse(args: List[String]): Either[String, ServerMode] =
    args match
      case Nil                     => Right(ServerMode.Serve)
      case List("--self-check")    => Right(ServerMode.SelfCheck)
      case List("--help" | "-h")   => Right(ServerMode.Help)
      case a :: _ if !Known(a)     => Left(s"unknown argument '$a'")
      case _                       => Left(s"one argument at most: ${args.mkString(" ")}")

  private val Known = Set("--self-check", "--help", "-h")
