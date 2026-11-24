package com.histmersion
package cli
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor

import com.histmersion.api.Paging
import io.github.iltotore.iron.RefinedType
import com.histmersion.lint.{LintRule, tolerated}

/** The "next page" line of a paged listing (`jobs`, `agent runs`, `agent approvals`): the same command with the same
  * options, plus `--before <the last row's id>`. Pure.
  */
object PageCmd:

  def next[C](command: String, opts: List[(String, Option[String])], cursor: C)(using RefinedType.Mirror[C]): String =
    val set = opts.collect { case (k, Some(v)) => s"--$k ${quote(v)}" }
    (List(s"histmersion $command") ++ set ++ List(s"--before ${quote(s"$cursor")}")).mkString(" ")

  /** The line under a listing, when an older page exists. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def older(page: Paging.Page, command: String, opts: List[(String, Option[String])]): Option[Paging.OlderLine.Text] =
    Paging.olderLine(page, c => Paging.OlderLine.Text(s"`${next(command, opts, c)}`"))

  private def quote(v: String): String =
    if v.nonEmpty && v.forall(c => c.isLetterOrDigit || "-_.:/@".contains(c)) then v
    else "'" + v.replace("'", "'\\''") + "'"
