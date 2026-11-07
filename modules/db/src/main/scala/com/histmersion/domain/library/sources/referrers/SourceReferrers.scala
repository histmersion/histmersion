package com.histmersion.domain.library.sources.referrers

import com.augustnagro.magnum.DbCon

/** Every domain that names sources, in the order their rewrites are reported (a rename's columns follow it). */
final class SourceReferrers(val all: List[SourceReferrer]):

  /** Every referrer's rewrite of a rename, in order. */
  def renamed(rename: SourceRename)(using DbCon): RenamedReferences =
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
  def recorded(source: SourceKeys, line: com.histmersion.domain.library.sources.referrers.SourceReferrers.Line, day: com.histmersion.domain.library.sources.referrers.SourceReferrers.Day)(using DbCon): Option[com.histmersion.RowCount] =
    all.iterator.map(_.recorded(source, line, day)).collectFirst { case Some(id) => id }

object SourceReferrers:
  type Day = Day.T
  object Day extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Line = Line.T
  object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
