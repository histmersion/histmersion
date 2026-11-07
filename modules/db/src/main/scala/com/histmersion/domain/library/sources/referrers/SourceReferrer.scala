package com.histmersion.domain.library.sources.referrers

import com.augustnagro.magnum.DbCon

/** A domain whose records name a source — its citekey in a column, `[[citekey]]` in a text — and that keeps them right
  * when the library renames or deletes the source, inside the library's transaction. Each member's default is "nothing
  * of that kind here".
  */
trait SourceReferrer:

  /** The source is called `rename.to` from now on: what this domain stores of `rename.from` follows; the columns and
    * texts it rewrote.
    */
  def renamed(rename: SourceRename)(using DbCon): RenamedReferences = RenamedReferences()

  /** What of this domain a delete of the source would touch: what refuses it, what goes, what stays and is reported. */
  def report(source: SourceKeys)(using DbCon): DeleteReport = DeleteReport()

  /** The source is about to go: remove what goes with it. `report` is what every referrer reported. */
  def deleting(source: SourceKeys, report: DeleteReport)(using DbCon): Unit = ()

  /** The source went: the entry this domain keeps of it (the log's), with `line` as its text and `day` as its topic. */
  def recorded(source: SourceKeys, line: com.histmersion.domain.library.sources.referrers.SourceReferrers.Line, day: com.histmersion.domain.library.sources.referrers.SourceReferrers.Day)(using DbCon): Option[com.histmersion.domain.library.sources.referrers.SourceReferrers.Entry] = None

