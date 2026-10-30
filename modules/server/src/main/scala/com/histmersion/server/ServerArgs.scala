package com.histmersion.server

object ServerArgs:

  val Usage: String =
    """usage: histmersion-server [--self-check | --help]
      |
      |  (no arguments)  run the server: config from reference.conf < histmersion.conf < the environment
      |  --self-check    load the config and the plugins, resolve their secrets, check the time zones,
      |                  print a report and exit (0 = all good, 2 = a plugin failed or was refused or the
      |                  time zones are missing, 3 = the config is broken);
      |                  opens no database connection and no port
      |  --help, -h      this text""".stripMargin

  /** Pure: the mode, or the usage error. */
  def parse(args: List[String]): Either[String, ServerMode] =
    args match
      case Nil                   => Right(ServerMode.Serve)
      case List("--self-check")  => Right(ServerMode.SelfCheck)
      case List("--help" | "-h") => Right(ServerMode.Help)
      case a :: _ if !Known(a)   => Left(s"unknown argument '$a'")
      case _                     => Left(s"one argument at most: ${args.mkString(" ")}")

  private val Known = Set("--self-check", "--help", "-h")
