package com.histmersion.domain.automation.goals
import com.histmersion.util.database.RangeSql

import com.histmersion.api.{ApiError, Write}
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.api.automation.{Goal, GoalList, GoalPatch, GoalPut}

object GoalRepo:

  private val cols =
    "g.slug, g.title, g.status, g.notes, g.tags, g.regions, " +
      s"${RangeSql.cols("g.period")}, g.parent_slug, g.updated_at::text"

  /** One page of the goals (of one status), by slug, on the caller's connection. */
  def listIn(status: Option[String], limit: Int, offset: Int)(using DbCon): GoalList =
    val st = status.map(_.trim.toLowerCase).filter(_.nonEmpty)
    locally:
      val total = sql"""SELECT count(*)::int FROM goals g
            WHERE ($st::text IS NULL OR g.status = $st)""".query[Int].run().head
      val rows = sql"""SELECT ${SqlLiteral(cols)} FROM goals g
            WHERE ($st::text IS NULL OR g.status = $st)
            ORDER BY g.slug
            LIMIT ${limit.max(1)} OFFSET ${offset.max(0)}
            """.query[GoalRow].run().toList.map(_.toDto)
      GoalList.of(total, rows, offset.max(0))

  def get(slug: String)(using DbCon): Option[Goal] =
    sql"""SELECT ${SqlLiteral(cols)} FROM goals g WHERE g.slug = $slug"""
      .query[GoalRow]
      .run()
      .headOption
      .map(_.toDto)

  /** Every goal, by slug — unpaged, for the `meta/GOALS.md` export. */
  def all()(using DbCon): List[Goal] =
    sql"""SELECT ${SqlLiteral(cols)} FROM goals g ORDER BY g.slug""".query[GoalRow].run().toList.map(_.toDto)

  /** A goal's direct sub-goals, by slug. */
  def children(slug: String)(using DbCon): List[Goal] =
    sql"""SELECT ${SqlLiteral(cols)} FROM goals g WHERE g.parent_slug = $slug ORDER BY g.slug"""
      .query[GoalRow]
      .run()
      .toList
      .map(_.toDto)

  def exists(slug: String)(using DbCon): Boolean =
    (sql"SELECT 1 FROM goals WHERE slug = $slug".query[Int].run().nonEmpty)

  /** Create or replace a goal. Either bound of its period may be given alone (an open period); one that starts after it
    * ends is refused.
    */
  def put(slug: String, in: GoalPut)(using DbCon): Either[ApiError, Unit] =
    RangeSql
      .merge((None, None), in.periodStart, in.periodEnd, s"goal '$slug'")
      .getOrElse(Right((None, None)))
      .map((s, e) => putIn(slug, in, RangeSql.literal(s, e)))

  private def putIn(slug: String, in: GoalPut, period: Option[String])(using DbCon): Unit =
    sql"""INSERT INTO goals (slug, title, status, notes, tags, regions, period, parent_slug, updated_at)
          VALUES ($slug, ${in.title}, ${in.status}, ${in.notes},
                  ${in.tags}::text[], ${in.regions}::text[],
                  $period::int8range, ${in.parentSlug}, now())
          ON CONFLICT (slug) DO UPDATE SET
            title = EXCLUDED.title, status = EXCLUDED.status, notes = EXCLUDED.notes,
            tags = EXCLUDED.tags, regions = EXCLUDED.regions, period = EXCLUDED.period,
            parent_slug = EXCLUDED.parent_slug, updated_at = now()""".update.run()

  /** A goal's fields changed as given, in the caller's transaction. */
  def patchIn(slug: String, p: GoalPatch)(using DbCon): Either[ApiError, Unit] =
    locally:
      val exists = sql"SELECT 1 FROM goals WHERE slug = $slug".query[Int].run().nonEmpty
      if !exists then Left(ApiError(s"No goal '$slug'", Some(Write.CodeNotFound)))
      else
        p.title.foreach(v => sql"UPDATE goals SET title = $v, updated_at = now() WHERE slug = $slug".update.run())
        p.status.foreach(v => sql"UPDATE goals SET status = $v, updated_at = now() WHERE slug = $slug".update.run())
        p.notes.foreach(v => sql"UPDATE goals SET notes = $v, updated_at = now() WHERE slug = $slug".update.run())
        p.tags.foreach { v =>
          sql"""UPDATE goals SET tags = $v::text[],
                updated_at = now() WHERE slug = $slug""".update.run()
        }
        p.regions.foreach { v =>
          sql"""UPDATE goals SET regions = $v::text[],
                updated_at = now() WHERE slug = $slug""".update.run()
        }
        p.parentSlug.foreach(v =>
          sql"UPDATE goals SET parent_slug = $v, updated_at = now() WHERE slug = $slug".update.run()
        )
        // a cap of 0 (or less) clears it: the runs fall back to the server default
        p.budgetUsd.foreach { v =>
          val usd = Option.when(v > 0)(BigDecimal(v))
          sql"UPDATE goals SET budget_usd = $usd, updated_at = now() WHERE slug = $slug".update.run()
        }
        // a bound given alone replaces that bound; the other stays as stored ([[RangeSql.merge]])
        val stored = sql"SELECT ${SqlLiteral(RangeSql.cols("period"))} FROM goals WHERE slug = $slug"
          .query[(Option[Long], Option[Long])]
          .run()
          .head
        RangeSql.merge(stored, p.periodStart, p.periodEnd, s"goal '$slug'").getOrElse(Right(stored)).map { (s, e) =>
          if (s, e) != stored then
            val range = RangeSql.literal(s, e)
            sql"UPDATE goals SET period = $range::int8range, updated_at = now() WHERE slug = $slug".update.run()
        }

  /** Rename a goal (V042), inside the caller's transaction: the slug moves with one UPDATE (its sub-goals' parent
    * cascades). What a bundle import does when a goal was renamed on the other side.
    */
  private[histmersion] def renameIn(oldSlug: String, newSlug: String)(using DbCon): Either[ApiError, Unit] =
    def exists(s: String) = sql"SELECT 1 FROM goals WHERE slug = $s".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT goal_slug FROM goal_aliases WHERE alias = $newSlug".query[String].run().headOption
    if !exists(oldSlug) then Left(ApiError(s"No goal '$oldSlug'", Some(Write.CodeNotFound)))
    else if exists(newSlug) then
      Left(ApiError(s"'$newSlug' is already the slug of another goal", Some(Write.CodeInvalid)))
    else if aliasOf.exists(_ != oldSlug) then
      Left(
        ApiError(
          s"'$newSlug' is an old slug of '${aliasOf.get}' and still resolves to it — pick another",
          Some(Write.CodeInvalid)
        )
      )
    else
      sql"DELETE FROM goal_aliases WHERE alias = $newSlug".update.run()
      sql"UPDATE goals SET slug = $newSlug WHERE slug = $oldSlug".update.run()
      sql"""INSERT INTO goal_aliases (alias, goal_slug) VALUES ($oldSlug, $newSlug)
            ON CONFLICT (alias) DO UPDATE SET goal_slug = EXCLUDED.goal_slug""".update.run()
      Right(())

  /** The slug an OLD slug was renamed to — `None` when it is a live slug, or nothing. */
  def renamedTo(slug: String)(using DbCon): Option[String] =
    com.histmersion.domain.automation.goals.GoalAliasTable.table.renamedToIn(slug)

  /** A goal removed in the caller's transaction; false when there was none. */
  def deleteIn(slug: String)(using DbCon): Boolean =
    sql"DELETE FROM goals WHERE slug = $slug".update.run() > 0
