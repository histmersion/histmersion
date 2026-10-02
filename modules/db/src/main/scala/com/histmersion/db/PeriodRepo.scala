package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Named historical periods (`periods`, V016) — the timeline's presets and the PeriodO definitions
  * behind them (#719). The list query stays where it was ([[PlaceRepo.listPeriods]]); this is the
  * write path and the single-period reads. The period is `int8range '[]'`, so the inclusive upper
  * bound read back is `upper − 1` (AGENTS.md). */
object PeriodRepo:

  private val columns = "id, label, authority, lower(period), upper(period) - 1, region, parent_id"

  def get(ds: DataSource, id: String): Option[HistoricalPeriod] =
    connect(ds):
      Sql.of(s"SELECT $columns FROM periods WHERE id = ?", Param.S(id)).frag
        .query[HistPeriodRow].run().headOption.map(_.toDto)

  def children(ds: DataSource, id: String): Vector[HistoricalPeriod] =
    connect(ds):
      Sql.of(s"SELECT $columns FROM periods WHERE parent_id = ? ORDER BY lower(period), label", Param.S(id))
        .frag.query[HistPeriodRow].run().map(_.toDto)

  def count(ds: DataSource): Int =
    connect(ds)(sql"SELECT count(*)::int FROM periods".query[Int].run().head)

  /** The top-level periods — what the web timeline offers as presets. */
  def presetCount(ds: DataSource): Int =
    connect(ds)(sql"SELECT count(*)::int FROM periods WHERE parent_id IS NULL".query[Int].run().head)

  /** Where each of `ids` sits now: every stored id → its parent (`None` = top level). Ids with no
    * row are absent. */
  def placement(ds: DataSource, ids: List[String]): Map[String, Option[String]] =
    if ids.isEmpty then Map.empty
    else
      connect(ds):
        sql"SELECT id, parent_id FROM periods WHERE id = ANY(${ids}::text[])"
          .query[(String, Option[String])].run().toMap

  /** Upsert in ONE transaction, in the order given — parents first, which [[PeriodO.toPeriods]]
    * guarantees, because `parent_id` is a foreign key checked per row. Returns the rows written. */
  def upsert(ds: DataSource, ps: List[HistoricalPeriod]): Int =
    if ps.isEmpty then 0
    else
      transact(ds):
        val _ = batchUpdate(ps) { p =>
          sql"""INSERT INTO periods (id, label, authority, period, region, parent_id)
                VALUES (${p.id}, ${p.label}, ${p.authority},
                        int8range(${p.period.start}::bigint, ${p.period.end}::bigint, '[]'),
                        ${p.region}, ${p.parentId})
                ON CONFLICT (id) DO UPDATE SET
                  label = EXCLUDED.label, authority = EXCLUDED.authority, period = EXCLUDED.period,
                  region = EXCLUDED.region, parent_id = EXCLUDED.parent_id""".update
        }
        ps.size
