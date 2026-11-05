package com.histmersion.util.data.exchange

import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.database.RangeSql

/** The SQL every carried kind's lines are read with: a clock as the wire carries it, a range as three columns, the rows
  * a scope selects, and the uid of whatever a label names.
  */
object BundleSql:

  /** `timestamptz` as the wire carries it everywhere: UTC, microseconds, `Z`. */
  type TsResult = TsResult.T
  object TsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TsCol = TsCol.T
  object TsCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeColsResult = RangeColsResult.T
  object RangeColsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeColsCol = RangeColsCol.T
  object RangeColsCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeIsNull = RangeIsNull.T
  object RangeIsNull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeLo = RangeLo.T
  object RangeLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeHi = RangeHi.T
  object RangeHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeLiteralResult = RangeLiteralResult.T
  object RangeLiteralResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeOrAllResult = RangeOrAllResult.T
  object RangeOrAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WhereUid = WhereUid.T
  object WhereUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WhereClock = WhereClock.T
  object WhereClock extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetUidResult = TargetUidResult.T
  object TargetUidResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetUidTt = TargetUidTt.T
  object TargetUidTt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetUidTid = TargetUidTid.T
  object TargetUidTid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def ts(col: com.histmersion.util.data.exchange.BundleSql.TsCol): com.histmersion.util.data.exchange.BundleSql.TsResult =
    s"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')"""

  /** A range as the three columns [[range]] reads back. */
  def rangeCols(col: com.histmersion.util.data.exchange.BundleSql.RangeColsCol): com.histmersion.util.data.exchange.BundleSql.RangeColsResult = s"$col IS NULL, ${RangeSql.cols(col)}"

  def range(isNull: com.histmersion.util.data.exchange.BundleSql.RangeIsNull, lo: Option[com.histmersion.util.data.exchange.BundleSql.RangeLo], hi: Option[com.histmersion.util.data.exchange.BundleSql.RangeHi]): Option[com.histmersion.api.exchange.Bundle.Range] =
    Option.when(!isNull)(com.histmersion.api.exchange.Bundle.Range(lo, hi))

  /** A bundle range as the `int8range` literal ([[RangeSql.bounds]]), NULL when the bundle has none. */
  def rangeLiteral(p: Option[com.histmersion.api.exchange.Bundle.Range]): Option[com.histmersion.util.data.exchange.BundleSql.RangeLiteralResult] =
    p.map(r => RangeSql.bounds(r.start, r.end))

  /** A bundle range where no range means every year (`(,)`) — places, relations, types, periods. */
  def rangeOrAll(p: Option[com.histmersion.api.exchange.Bundle.Range]): com.histmersion.util.data.exchange.BundleSql.RangeOrAllResult =
    RangeSql.bounds(p.flatMap(_.start), p.flatMap(_.end))

  /** `WHERE` for the scope, on a table's uid and change-clock columns. */
  def where(scope: BundleScope, uid: com.histmersion.util.data.exchange.BundleSql.WhereUid, clock: com.histmersion.util.data.exchange.BundleSql.WhereClock): Sql = scope match
    case BundleScope.All       => Sql.raw("TRUE")
    case BundleScope.Since(at) => Sql.of(s"$clock > ?::timestamptz", Param.S(at))
    case BundleScope.Uids(us)  => Sql.of(s"$uid = ANY(?::uuid[])", Param.A(us.map(_.toString)))

  /** The uid of whatever a label names, for a target that HAS a uid — the label of an attestation's or an asset link's
    * target is kept, and its uid travels beside it for the matcher.
    */
  def targetUid(tt: com.histmersion.util.data.exchange.BundleSql.TargetUidTt, tid: com.histmersion.util.data.exchange.BundleSql.TargetUidTid): com.histmersion.util.data.exchange.BundleSql.TargetUidResult =
    s"""CASE $tt WHEN 'finding' THEN (SELECT uid FROM findings WHERE id = $tid)
                 WHEN 'source'  THEN (SELECT uid FROM sources WHERE citekey = $tid)
                 WHEN 'article' THEN (SELECT uid FROM articles WHERE slug = $tid)
                 WHEN 'event'   THEN (SELECT uid FROM events WHERE id = $tid) END"""
