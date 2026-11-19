package com.histmersion
package util.data.exchange

import com.histmersion.domain.DeepTime
import com.histmersion.util.database.RangeSql
import com.histmersion.api.catalogue.Year
import com.augustnagro.magnum.{sql, Frag}
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

/** The SQL every carried kind's lines are read with: a range as three columns, the rows a scope selects, and the uid of
  * whatever a label names (a clock as the wire carries it is [[Timestamps.iso]]).
  */
object BundleSql:

  /** A range as the three columns [[range]] reads back. */
  def rangeCols(col: Frag): Frag = sql"$col IS NULL, ${RangeSql.cols(col)}"

  def range(
      isNull: Bundle.RangeIsNull,
      lo: Option[Bundle.RangeLower],
      hi: Option[Bundle.RangeUpper]
  ): Option[com.histmersion.api.exchange.Bundle.Range] =
    Option.when(!isNull.value)(
      com.histmersion.api.exchange.Bundle.Range(
        lo.map(v => Year(v.value)),
        hi.map(v => Year(v.value))
      )
    )

  /** A bundle range as the `int8range` literal ([[RangeSql.bounds]]), NULL when the bundle has none. */
  def rangeLiteral(p: Option[com.histmersion.api.exchange.Bundle.Range]): Option[String] =
    p.map(r =>
      RangeSql.bounds(r.start.map(y => DeepTime.SignedYear(y.value)), r.end.map(y => DeepTime.SignedYear(y.value)))
    )

  /** A bundle range where no range means every year (`(,)`) — places, relations, types, periods. */
  def rangeOrAll(p: Option[com.histmersion.api.exchange.Bundle.Range]): String =
    RangeSql.bounds(
      p.flatMap(_.start).map(y => DeepTime.SignedYear(y.value)),
      p.flatMap(_.end).map(y => DeepTime.SignedYear(y.value))
    )

  /** `WHERE` for the scope, on a table's uid and change-clock columns. */
  def where(scope: BundleScope, uid: Frag, clock: Frag): Frag = scope match
    case BundleScope.All       => sql"TRUE"
    case BundleScope.Since(at) => sql"$clock > $at::timestamptz"
    case BundleScope.Uids(us)  =>
      sql"$uid = ANY(${us.map(u => u.toString)}::uuid[])"

  /** The uid of whatever a label names, for a target that HAS a uid — the label of an attestation's or an asset link's
    * target is kept, and its uid travels beside it for the matcher.
    */
  def targetUid(tt: Frag, tid: Frag): Frag =
    sql"""CASE $tt WHEN 'finding' THEN (SELECT uid FROM findings WHERE id = $tid)
                 WHEN 'source'  THEN (SELECT uid FROM sources WHERE citekey = $tid)
                 WHEN 'article' THEN (SELECT uid FROM articles WHERE slug = $tid)
                 WHEN 'event'   THEN (SELECT uid FROM events WHERE id = $tid) END"""
