package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Named historical periods (`periods`, V016) — the timeline's presets and the PeriodO definitions
  * behind them (#719). The list query stays where it was ([[PlaceRepo.listPeriods]]); this is the
  * write path and the single-period reads. The period is `int8range '[]'`, so the inclusive upper
  * bound read back is `upper − 1` (AGENTS.md). */
object PeriodRepo:

  private[db] val columns = s"id, label, authority, ${PeriodSql.cols("period")}, region, parent_id, preset"

  def get(ds: DataSource, id: String): Option[HistoricalPeriod] =
    connect(ds):
      Sql.of(s"SELECT $columns FROM periods WHERE id = ?", Param.S(id)).frag
        .query[HistPeriodRow].run().headOption.map(_.toDto)

  def children(ds: DataSource, id: String): Vector[HistoricalPeriod] =
    connect(ds):
      Sql.of(s"SELECT $columns FROM periods WHERE parent_id = ? ORDER BY ${PeriodSql.start("period")}, label", Param.S(id))
        .frag.query[HistPeriodRow].run().map(_.toDto)

  def count(ds: DataSource): Int =
    connect(ds)(sql"SELECT count(*)::int FROM periods".query[Int].run().head)

  /** The periods the web timeline offers as presets (those filed under a region heading). */
  def presetCount(ds: DataSource): Int =
    connect(ds)(sql"SELECT count(*)::int FROM periods WHERE preset IS NOT NULL".query[Int].run().head)

  /** Where each of `ids` sits now: every stored id → its parent (`None` = top level). Ids with no
    * row are absent. */
  def placement(ds: DataSource, ids: List[String]): Map[String, Option[String]] =
    if ids.isEmpty then Map.empty
    else
      connect(ds):
        sql"SELECT id, parent_id FROM periods WHERE id = ANY(${ids}::text[])"
          .query[(String, Option[String])].run().toMap

  /** Upsert in ONE transaction, in the order given — parents first, which [[PeriodO.toPeriods]]
    * guarantees, because `parent_id` is a foreign key checked per row. Returns the rows written.
    * A row with no `preset` leaves a stored one alone: a `periods fetch` of an authority the seed
    * draws presets from must not take them off the menu ([[retainPresets]] is how the seed does). */
  def upsert(ds: DataSource, ps: List[HistoricalPeriod], dataset: Dataset = Dataset.Seed): Int =
    if ps.isEmpty then 0
    else
      transact(ds):
        val _ = batchUpdate(ps) { p =>
          val preset = p.preset.map(_.wire)
          sql"""INSERT INTO periods (id, label, authority, period, region, parent_id, dataset, preset)
                VALUES (${p.id}, ${p.label}, ${p.authority},
                        ${PeriodSql.always(Some(p.period))}::int8range,
                        ${p.region}, ${p.parentId}, ${dataset.wire}, $preset)
                ON CONFLICT (id) DO UPDATE SET
                  label = EXCLUDED.label, authority = EXCLUDED.authority, period = EXCLUDED.period,
                  region = EXCLUDED.region, parent_id = EXCLUDED.parent_id, dataset = EXCLUDED.dataset,
                  preset = coalesce(EXCLUDED.preset, periods.preset)""".update
        }
        ps.size

  /** The seed's half of "the presets are exactly the seed's": take every preset that is NOT one of
    * `ids` off the menu (the row itself stays). Returns how many lost their heading. */
  def retainPresets(ds: DataSource, ids: List[String]): Int =
    transact(ds):
      sql"""UPDATE periods SET preset = NULL
            WHERE preset IS NOT NULL AND NOT (id = ANY(${ids}::text[]))""".update.run()

  /** `periods seed --replace`: delete the SEED rows (`dataset = 'seed'`, which includes what
    * `periods fetch` brought in) that are not among `ids`. A collection's own (`local`) periods are
    * never touched; children of a deleted row lose their parent (`ON DELETE SET NULL`). */
  def deleteSeedExcept(ds: DataSource, ids: List[String]): Int =
    transact(ds):
      sql"""DELETE FROM periods WHERE dataset = 'seed' AND NOT (id = ANY(${ids}::text[]))""".update.run()

  /** The version of the bundled seed `name` last applied here, if any. */
  def seedVersion(ds: DataSource, name: String): Option[Int] =
    connect(ds)(sql"SELECT version FROM seed_versions WHERE name = $name".query[Int].run().headOption)

  def setSeedVersion(ds: DataSource, name: String, version: Int): Unit =
    transact(ds):
      val _ = sql"""INSERT INTO seed_versions (name, version) VALUES ($name, $version)
                    ON CONFLICT (name) DO UPDATE SET version = EXCLUDED.version, applied_at = now()""".update.run()
