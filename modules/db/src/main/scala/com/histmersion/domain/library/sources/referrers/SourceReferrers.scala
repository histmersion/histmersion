package com.histmersion
package domain.library.sources.referrers

import com.augustnagro.magnum.DbCon
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Creator

/** Every domain that names sources, in the order their rewrites are reported (a rename's columns follow it). */
final class SourceReferrers(val all: List[SourceReferrer]):

  /** Every referrer's rewrite of a rename, in order. */
  def renamed(rename: SourceRename)(using DbCon)(using Creator.New): RenamedReferences =
    val each = all.map(_.renamed(rename))
    RenamedReferences(each.flatMap(_.columns), each.flatMap(_.texts))

  /** Every referrer's report, the library's own first: the blockers and counts in the order of their kinds. */
  def report(own: DeleteReport, source: SourceKeys)(using DbCon): DeleteReport =
    val each = own :: all.map(_.report(source))
    DeleteReport(
      each.flatMap(_.blockers).sortBy(_.kind.ordinal),
      each.flatMap(_.counts).sortBy(_.part.ordinal),
      each.flatMap(_.coverage),
      each.flatMap(_.mentions)
    )

  def deleting(source: SourceKeys, report: DeleteReport)(using DbCon): Unit = all.foreach(_.deleting(source, report))

  /** The first entry a referrer kept of the deletion. */
  def recorded(source: SourceKeys, line: SourceReferrers.Line, day: SourceReferrers.Day)(using
      DbCon
  ): Option[SourceReferrers.Entry] =
    all.iterator.map(_.recorded(source, line, day)).collectFirst { case Some(id) => id }

object SourceReferrers:
  /** The entry a referrer kept of a deletion: its number in the domain's own log. */
  type Entry = Entry.T
  object Entry extends Newtype[Int, Pure]
  type Day = Day.T
  object Day extends Newtype[String, Pure]
  type Line = Line.T
  object Line extends Newtype[String, Pure]
