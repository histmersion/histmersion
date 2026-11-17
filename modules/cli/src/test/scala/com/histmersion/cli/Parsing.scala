package com.histmersion
package cli

/** The command line as tests read it: the command [[CommandLine]] builds, or what it answered instead. */
object Parsing:
  def parse(args: String*): Either[CommandLine.Outcome, Cli.Command] =
    CommandLine.parse(args) match
      case CommandLine.Outcome.Run(_, c) => Right(c)
      case other                         => Left(other)

  def parses(args: String*): Boolean = parse(args*).isRight

  /** The help it printed: asked for, or after a refusal. */
  def help(args: String*): String =
    CommandLine.parse(args) match
      case CommandLine.Outcome.Help(t)      => t.printed
      case CommandLine.Outcome.Failed(_, t) => t.printed
      case _                                => ""
