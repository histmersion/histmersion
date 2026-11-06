package com.histmersion.cli

import com.histmersion.api.Paging

/** The "next page" line of a paged listing (`jobs`, `agent runs`, `agent approvals`): the same command with the same
  * options, plus `--before <the last row's id>`. Pure.
  */
object PageCmd:

  /** `histmersion jobs --status failed --limit 20 --before 4f1c…` — `opts` in the order given, the ones left unset
    * dropped; a value with a space or quote is single-quoted for the shell.
    */
  type NextResult = NextResult.T
  object NextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextCommand = NextCommand.T
  object NextCommand extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextOptsPart1 = NextOptsPart1.T
  object NextOptsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextOptsPart2 = NextOptsPart2.T
  object NextOptsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextCursor = NextCursor.T
  object NextCursor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderResult = OlderResult.T
  object OlderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderCommand = OlderCommand.T
  object OlderCommand extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderOptsPart1 = OlderOptsPart1.T
  object OlderOptsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderOptsPart2 = OlderOptsPart2.T
  object OlderOptsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuoteResult = QuoteResult.T
  object QuoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuoteV = QuoteV.T
  object QuoteV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def next(command: com.histmersion.cli.PageCmd.NextCommand, opts: List[(com.histmersion.cli.PageCmd.NextOptsPart1, Option[com.histmersion.cli.PageCmd.NextOptsPart2])], cursor: com.histmersion.cli.PageCmd.NextCursor): com.histmersion.cli.PageCmd.NextResult =
    val set = opts.collect { case (k, Some(v)) => s"--$k ${quote(v)}" }
    (List(s"histmersion $command") ++ set ++ List(s"--before ${quote(cursor)}")).mkString(" ")

  /** The line under a listing, when an older page exists. */
  def older(page: Paging.Page, command: com.histmersion.cli.PageCmd.OlderCommand, opts: List[(com.histmersion.cli.PageCmd.OlderOptsPart1, Option[com.histmersion.cli.PageCmd.OlderOptsPart2])]): Option[com.histmersion.cli.PageCmd.OlderResult] =
    Paging.olderLine(page, c => s"`${next(command, opts, c)}`")

  private def quote(v: com.histmersion.cli.PageCmd.QuoteV): com.histmersion.cli.PageCmd.QuoteResult =
    if v.nonEmpty && v.forall(c => c.isLetterOrDigit || "-_.:/@".contains(c)) then v
    else "'" + v.replace("'", "'\\''") + "'"
