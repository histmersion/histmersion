package com.histmersion.cli

import com.histmersion.api.Paging

/** The "next page" line of a paged listing (`jobs`, `agent runs`, `agent approvals`): the same command with the same
  * options, plus `--before <the last row's id>`. Pure.
  */
object PageCmd:

  /** `histmersion jobs --status failed --limit 20 --before 4f1c…` — `opts` in the order given, the ones left unset
    * dropped; a value with a space or quote is single-quoted for the shell.
    */
  def next(command: String, opts: List[(String, Option[String])], cursor: String): String =
    val set = opts.collect { case (k, Some(v)) => s"--$k ${quote(v)}" }
    (List(s"histmersion $command") ++ set ++ List(s"--before ${quote(cursor)}")).mkString(" ")

  /** The line under a listing, when an older page exists. */
  def older(page: Paging.Page, command: String, opts: List[(String, Option[String])]): Option[String] =
    Paging.olderLine(page, c => s"`${next(command, opts, c)}`")

  private def quote(v: String): String =
    if v.nonEmpty && v.forall(c => c.isLetterOrDigit || "-_.:/@".contains(c)) then v
    else "'" + v.replace("'", "'\\''") + "'"
