package com.histmersion
package domain.catalogue.periods

import com.histmersion.api.Dataset
import com.histmersion.api.catalogue.HistoricalPeriod
import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.util.database.{LikePattern, RangeSql}

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.DeepTime
import com.histmersion.domain.{CreatedAt, Creator}
import com.histmersion.util.database.StoredCreator

/** Named historical periods (`periods`, V016) — the timeline's presets and the PeriodO definitions behind them: the
  * search, the single-period reads and the write path. The period is `int8range '[]'`, so the inclusive upper bound
  * read back is `upper − 1`. Every method runs on the connection it is given.
  */
private[periods] object PeriodRepo:
  type SearchText = SearchText.T
  object SearchText extends Newtype[String, Pure]
  type SeedVersion = SeedVersion.T
  object SeedVersion extends Newtype[Int, Pure]

  type SeedVersionName = SeedVersionName.T
  object SeedVersionName extends Newtype[String, Pure]
  type SetSeedVersionName = SetSeedVersionName.T
  object SetSeedVersionName extends Newtype[String, Pure]

  private val columns = sql"id, label, authority, ${RangeSql.cols(sql"period")}, region, parent_id, preset"

  def get(id: PeriodRow.Id)(using DbCon): Option[HistoricalPeriod] =
    sql"SELECT $columns FROM periods WHERE id = $id"
      .query[PeriodRow]
      .run()
      .headOption
      .map(_.toPeriod)

  def children(id: PeriodRow.Id)(using DbCon): Vector[HistoricalPeriod] =
    sql"SELECT $columns FROM periods WHERE parent_id = $id ORDER BY ${RangeSql.start(sql"period")}, label, id"
      .query[PeriodRow]
      .run()
      .map(_.toPeriod)

  /** The periods whose label holds `q` (a literal substring, any case), of the region `region` (any case), overlapping
    * `from..to` (either bound alone is a span too), by start, then label, then id.
    */
  def search(
      q: Option[SearchText],
      region: Option[PeriodRow.Region],
      from: Option[DeepTime.SignedYear],
      to: Option[DeepTime.SignedYear]
  )(using
      DbCon
  ): Vector[HistoricalPeriod] =
    val text = q.map(_.value.trim).filter(_.nonEmpty)
    val cont = text.map(LikePattern.contains).getOrElse("%")
    val reg = region.map(_.value.trim).filter(_.nonEmpty)
    sql"""SELECT $columns
          FROM periods
          WHERE ($text::text IS NULL OR label ILIKE $cont ESCAPE '\\')
            AND ($reg::text IS NULL OR lower(region) = lower($reg))
            AND (($from::bigint IS NULL AND $to::bigint IS NULL)
                 OR period && int8range($from::bigint, $to::bigint, '[]'))
          ORDER BY ${RangeSql.start(sql"period")}, label, id""".query[PeriodRow].run().map(_.toPeriod)

  def count(using DbCon): RowCount =
    RowCount(sql"SELECT count(*)::int FROM periods".query[Int].run().head)

  /** The periods the web timeline offers as presets (those filed under a region heading). */
  def presetCount(using DbCon): RowCount =
    RowCount(sql"SELECT count(*)::int FROM periods WHERE preset IS NOT NULL".query[Int].run().head)

  /** Where each of `ids` sits now: every stored id → its parent (`None` = top level). Ids with no row are absent. */
  def placement(ids: List[PeriodRow.Id])(using DbCon): Map[PeriodRow.Id, Option[PeriodRow.Label]] =
    if ids.isEmpty then Map.empty
    else
      sql"SELECT id, parent_id FROM periods WHERE id = ANY($ids::text[])"
        .query[(PeriodRow.Id, Option[PeriodRow.Label])]
        .run()
        .toMap

  /** Upsert in the order given — parents first, because `parent_id` is a foreign key checked per row. A row with no
    * `preset` leaves a stored one alone.
    */
  def upsert(ps: List[HistoricalPeriod], dataset: Dataset)(using
      DbCon
  )(using
      creator: Creator.New,
      at: CreatedAt
  ): RowCount =
    if ps.isEmpty then RowCount(0)
    else
      val _ = batchUpdate(ps) { p =>
        val preset = p.preset.map(_.wire)
        sql"""INSERT INTO periods (id, label, authority, period, region, parent_id, dataset, preset, creator, created_at)
              VALUES (${p.id}, ${p.label}, ${p.authority},
                      ${RangeSql.always(Some(p.period))}::int8range,
                      ${p.region}, ${p.parentId}, ${dataset.wire}, $preset, ${StoredCreator.column(creator)}::jsonb,
                      ${at.iso}::timestamptz)
              ON CONFLICT (id) DO UPDATE SET
                label = EXCLUDED.label, authority = EXCLUDED.authority, period = EXCLUDED.period,
                region = EXCLUDED.region, parent_id = EXCLUDED.parent_id, dataset = EXCLUDED.dataset,
                preset = coalesce(EXCLUDED.preset, periods.preset)""".update
      }
      RowCount(ps.size)

  /** Take every preset that is NOT one of `ids` off the menu (the row itself stays). */
  def retainPresets(ids: List[PeriodRow.Id])(using DbCon): RowCount =
    RowCount(
      sql"""UPDATE periods SET preset = NULL
          WHERE preset IS NOT NULL AND NOT (id = ANY($ids::text[]))""".update.run()
    )

  /** Delete the SEED rows that are not among `ids`; a collection's own (`local`) periods are never touched. */
  def deleteSeedExcept(ids: List[PeriodRow.Id])(using DbCon): RowCount =
    RowCount(
      sql"""DELETE FROM periods WHERE dataset = 'seed' AND NOT (id = ANY($ids::text[]))""".update.run()
    )

  def seedVersion(name: SeedVersionName)(using DbCon): Option[SeedVersion] =
    sql"SELECT version FROM seed_versions WHERE name = $name".query[Int].run().headOption.map(SeedVersion(_))

  def setSeedVersion(name: SetSeedVersionName, version: SeedVersion)(using DbCon): Unit =
    val _ = sql"""INSERT INTO seed_versions (name, version) VALUES ($name, $version)
                  ON CONFLICT (name) DO UPDATE SET version = EXCLUDED.version, applied_at = now()""".update.run()
