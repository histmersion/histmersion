package com.histmersion.domain.catalogue.periods

import com.histmersion.api.Dataset
import com.histmersion.api.catalogue.HistoricalPeriod
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.util.database.{LikePattern, RangeSql}

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given

/** Named historical periods (`periods`, V016) — the timeline's presets and the PeriodO definitions behind them: the
  * search, the single-period reads and the write path. The period is `int8range '[]'`, so the inclusive upper bound
  * read back is `upper − 1`. Every method runs on the connection it is given.
  */
private[periods] object PeriodRepo:
  type SearchText = SearchText.T
  object SearchText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedVersion = SeedVersion.T
  object SeedVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SeedVersionName = SeedVersionName.T
  object SeedVersionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSeedVersionName = SetSeedVersionName.T
  object SetSeedVersionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val columns = s"id, label, authority, ${RangeSql.cols("period")}, region, parent_id, preset"

  def get(id: com.histmersion.domain.catalogue.periods.PeriodRow.Id)(using DbCon): Option[HistoricalPeriod] =
    Sql
      .of(Sql.OfText(s"SELECT $columns FROM periods WHERE id = ?"), Param.S(Param.S.V(id.value)))
      .frag
      .query[PeriodRow]
      .run()
      .headOption
      .map(_.toPeriod)

  def children(id: com.histmersion.domain.catalogue.periods.PeriodRow.Id)(using DbCon): Vector[HistoricalPeriod] =
    Sql
      .of(
        Sql.OfText(s"SELECT $columns FROM periods WHERE parent_id = ? ORDER BY ${RangeSql.start("period")}, label"),
        Param.S(Param.S.V(id.value))
      )
      .frag
      .query[PeriodRow]
      .run()
      .map(_.toPeriod)

  /** The periods whose label holds `q` (a literal substring, any case), of the region `region` (any case), overlapping
    * `from..to` (either bound alone is a span too), by start then label.
    */
  def search(q: Option[com.histmersion.domain.catalogue.periods.PeriodRepo.SearchText], region: Option[com.histmersion.domain.catalogue.periods.PeriodRow.Region], from: Option[com.histmersion.domain.DeepTime.SignedYear], to: Option[com.histmersion.domain.DeepTime.SignedYear])(using
      DbCon
  ): Vector[HistoricalPeriod] =
    val text = q.map(_.value.trim).filter(_.nonEmpty)
    val cont = text.map(LikePattern.contains).getOrElse("%")
    val reg = region.map(_.value.trim).filter(_.nonEmpty)
    sql"""SELECT ${SqlLiteral(columns)}
          FROM periods
          WHERE ($text::text IS NULL OR label ILIKE $cont ESCAPE '\\')
            AND ($reg::text IS NULL OR lower(region) = lower($reg))
            AND (($from::bigint IS NULL AND $to::bigint IS NULL)
                 OR period && int8range($from::bigint, $to::bigint, '[]'))
          ORDER BY ${SqlLiteral(RangeSql.start("period"))}, label""".query[PeriodRow].run().map(_.toPeriod)

  def count(using DbCon): com.histmersion.RowCount =
    com.histmersion.RowCount(sql"SELECT count(*)::int FROM periods".query[Int].run().head)

  /** The periods the web timeline offers as presets (those filed under a region heading). */
  def presetCount(using DbCon): com.histmersion.RowCount =
    com.histmersion.RowCount(sql"SELECT count(*)::int FROM periods WHERE preset IS NOT NULL".query[Int].run().head)

  /** Where each of `ids` sits now: every stored id → its parent (`None` = top level). Ids with no row are absent. */
  def placement(ids: List[com.histmersion.domain.catalogue.periods.PeriodRow.Id])(using DbCon): Map[com.histmersion.domain.catalogue.periods.PeriodRow.Id, Option[com.histmersion.domain.catalogue.periods.PeriodRow.Label]] =
    if ids.isEmpty then Map.empty
    else
      sql"SELECT id, parent_id FROM periods WHERE id = ANY($ids::text[])"
        .query[(com.histmersion.domain.catalogue.periods.PeriodRow.Id, Option[com.histmersion.domain.catalogue.periods.PeriodRow.Label])]
        .run()
        .toMap

  /** Upsert in the order given — parents first, because `parent_id` is a foreign key checked per row. A row with no
    * `preset` leaves a stored one alone.
    */
  def upsert(ps: List[HistoricalPeriod], dataset: Dataset)(using DbCon): com.histmersion.RowCount =
    if ps.isEmpty then com.histmersion.RowCount(0)
    else
      val _ = batchUpdate(ps) { p =>
        val preset = p.preset.map(_.wire)
        sql"""INSERT INTO periods (id, label, authority, period, region, parent_id, dataset, preset)
              VALUES (${p.id}, ${p.label}, ${p.authority},
                      ${RangeSql.always(Some(p.period))}::int8range,
                      ${p.region}, ${p.parentId}, ${dataset.wire}, $preset)
              ON CONFLICT (id) DO UPDATE SET
                label = EXCLUDED.label, authority = EXCLUDED.authority, period = EXCLUDED.period,
                region = EXCLUDED.region, parent_id = EXCLUDED.parent_id, dataset = EXCLUDED.dataset,
                preset = coalesce(EXCLUDED.preset, periods.preset)""".update
      }
      com.histmersion.RowCount(ps.size)

  /** Take every preset that is NOT one of `ids` off the menu (the row itself stays). */
  def retainPresets(ids: List[com.histmersion.domain.catalogue.periods.PeriodRow.Id])(using DbCon): com.histmersion.RowCount =
    com.histmersion.RowCount(
      sql"""UPDATE periods SET preset = NULL
          WHERE preset IS NOT NULL AND NOT (id = ANY($ids::text[]))""".update.run()
    )

  /** Delete the SEED rows that are not among `ids`; a collection's own (`local`) periods are never touched. */
  def deleteSeedExcept(ids: List[com.histmersion.domain.catalogue.periods.PeriodRow.Id])(using DbCon): com.histmersion.RowCount =
    com.histmersion.RowCount(
      sql"""DELETE FROM periods WHERE dataset = 'seed' AND NOT (id = ANY($ids::text[]))""".update.run()
    )

  def seedVersion(name: com.histmersion.domain.catalogue.periods.PeriodRepo.SeedVersionName)(using DbCon): Option[com.histmersion.domain.catalogue.periods.PeriodRepo.SeedVersion] =
    sql"SELECT version FROM seed_versions WHERE name = $name".query[Int].run().headOption.map(SeedVersion(_))

  def setSeedVersion(name: com.histmersion.domain.catalogue.periods.PeriodRepo.SetSeedVersionName, version: com.histmersion.domain.catalogue.periods.PeriodRepo.SeedVersion)(using DbCon): Unit =
    val _ = sql"""INSERT INTO seed_versions (name, version) VALUES ($name, $version)
                  ON CONFLICT (name) DO UPDATE SET version = EXCLUDED.version, applied_at = now()""".update.run()
