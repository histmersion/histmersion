package com.histmersion
package domain.automation.goals
import com.histmersion.api.Problem
import com.histmersion.util.database.RangeSql

import com.histmersion.api.Write
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.automation.Goal
import com.histmersion.api.Api
import com.histmersion.api.RowCount
import com.histmersion.api.personal.UnreadPage
import com.histmersion.domain.DeepTime
import com.histmersion.util.identity.AliasTable

object GoalRepo:

  private val cols =
    "g.slug, g.title, g.status, g.notes, g.tags, g.regions, " +
      s"${RangeSql.cols("g.period")}, g.parent_slug, g.updated_at::text"

  /** One page of the goals (of one status), by slug, on the caller's connection. */
  def listIn(status: Option[GoalRow.Status], limit: PageSize, offset: Offset)(using DbCon): Goal.List =
    val st = status.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
    locally:
      val total = sql"""SELECT count(*)::int FROM goals g
            WHERE ($st::text IS NULL OR g.status = $st)""".query[Int].run().head
      val rows = sql"""SELECT ${SqlLiteral(cols)} FROM goals g
            WHERE ($st::text IS NULL OR g.status = $st)
            ORDER BY g.slug
            LIMIT ${limit.value.max(1)} OFFSET ${offset.value.max(0)}
            """.query[GoalRow].run().toList.map(_.toDto)
      Goal.List.of(
        RowCount(total),
        rows,
        UnreadPage.Offset(offset.value.max(0))
      )

  def get(slug: com.histmersion.domain.automation.Goal.Slug)(using DbCon): Option[Goal] =
    sql"""SELECT ${SqlLiteral(cols)} FROM goals g WHERE g.slug = $slug"""
      .query[GoalRow]
      .run()
      .headOption
      .map(_.toDto)

  /** Every goal, by slug — unpaged, for the `meta/GOALS.md` export. */
  def all()(using DbCon): List[Goal] =
    sql"""SELECT ${SqlLiteral(cols)} FROM goals g ORDER BY g.slug""".query[GoalRow].run().toList.map(_.toDto)

  /** A goal's direct sub-goals, by slug. */
  def children(slug: com.histmersion.domain.automation.Goal.Slug)(using DbCon): List[Goal] =
    sql"""SELECT ${SqlLiteral(cols)} FROM goals g WHERE g.parent_slug = $slug ORDER BY g.slug"""
      .query[GoalRow]
      .run()
      .toList
      .map(_.toDto)

  def exists(slug: com.histmersion.domain.automation.Goal.Slug)(using DbCon): Boolean =
    (sql"SELECT 1 FROM goals WHERE slug = $slug".query[Int].run().nonEmpty)

  /** Create or replace a goal. Either bound of its period may be given alone (an open period); one that starts after it
    * ends is refused.
    */
  def put(slug: com.histmersion.domain.automation.Goal.Slug, in: Goal.Put)(using DbCon): Either[Api.Error, Unit] =
    RangeSql
      .merge(
        (None, None),
        in.periodStart.map(y => DeepTime.SignedYear(y.value)),
        in.periodEnd.map(y => DeepTime.SignedYear(y.value)),
        s"goal '${slug.value}'"
      )
      .getOrElse(Right((None, None)))
      .map((s, e) => putIn(slug, in, RangeSql.literal(s, e)))

  private def putIn(slug: com.histmersion.domain.automation.Goal.Slug, in: Goal.Put, period: Option[String])(using
      DbCon
  ): Unit =
    sql"""INSERT INTO goals (slug, title, status, notes, tags, regions, period, parent_slug, updated_at)
          VALUES ($slug, ${in.title}, ${in.status}, ${in.notes},
                  ${in.tags}::text[], ${in.regions}::text[],
                  $period::int8range, ${in.parentSlug}, now())
          ON CONFLICT (slug) DO UPDATE SET
            title = EXCLUDED.title, status = EXCLUDED.status, notes = EXCLUDED.notes,
            tags = EXCLUDED.tags, regions = EXCLUDED.regions, period = EXCLUDED.period,
            parent_slug = EXCLUDED.parent_slug, updated_at = now()""".update.run()

  /** A goal's fields changed as given, in the caller's transaction. */
  def patchIn(slug: com.histmersion.domain.automation.Goal.Slug, p: Goal.Patch)(using DbCon): Either[Api.Error, Unit] =
    locally:
      val exists = sql"SELECT 1 FROM goals WHERE slug = $slug".query[Int].run().nonEmpty
      if !exists then Left(Api.Error(api.Problem(s"No goal '${slug.value}'"), Some(Api.Error.Code(Write.CodeNotFound))))
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
          val usd = Option.when(v.value > 0)(BigDecimal(v.value))
          sql"UPDATE goals SET budget_usd = $usd, updated_at = now() WHERE slug = $slug".update.run()
        }
        // a bound given alone replaces that bound; the other stays as stored ([[RangeSql.merge]])
        val storedRaw = sql"SELECT ${SqlLiteral(RangeSql.cols("period"))} FROM goals WHERE slug = $slug"
          .query[(Option[Long], Option[Long])]
          .run()
          .head
        val stored = (storedRaw._1.map(DeepTime.SignedYear(_)), storedRaw._2.map(DeepTime.SignedYear(_)))
        RangeSql
          .merge(
            stored,
            p.periodStart.map(y => DeepTime.SignedYear(y.value)),
            p.periodEnd.map(y => DeepTime.SignedYear(y.value)),
            s"goal '${slug.value}'"
          )
          .getOrElse(Right(stored))
          .map { (s, e) =>
            if (s, e) != stored then
              val range = RangeSql.literal(s, e)
              sql"UPDATE goals SET period = $range::int8range, updated_at = now() WHERE slug = $slug".update.run()
          }

  /** Rename a goal (V042), inside the caller's transaction: the slug moves with one UPDATE (its sub-goals' parent
    * cascades). What a bundle import does when a goal was renamed on the other side.
    */
  private[histmersion] def renameIn(
      oldSlug: com.histmersion.domain.automation.Goal.Slug,
      newSlug: com.histmersion.domain.automation.Goal.Slug
  )(using DbCon): Either[Api.Error, Unit] =
    def exists(s: com.histmersion.domain.automation.Goal.Slug) =
      sql"SELECT 1 FROM goals WHERE slug = $s".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT goal_slug FROM goal_aliases WHERE alias = $newSlug"
      .query[String]
      .run()
      .headOption
      .map(com.histmersion.domain.automation.Goal.Slug.assume(_))
    if !exists(oldSlug) then
      Left(Api.Error(api.Problem(s"No goal '${oldSlug.value}'"), Some(Api.Error.Code(Write.CodeNotFound))))
    else if exists(newSlug) then
      Left(
        Api.Error(
          api.Problem(s"'${newSlug.value}' is already the slug of another goal"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else if aliasOf.exists(_ != oldSlug) then
      Left(
        Api.Error(
          api.Problem(
            s"'${newSlug.value}' is an old slug of '${aliasOf.get.value}' and still resolves to it — pick another"
          ),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else
      sql"DELETE FROM goal_aliases WHERE alias = $newSlug".update.run()
      sql"UPDATE goals SET slug = $newSlug WHERE slug = $oldSlug".update.run()
      sql"""INSERT INTO goal_aliases (alias, goal_slug) VALUES ($oldSlug, $newSlug)
            ON CONFLICT (alias) DO UPDATE SET goal_slug = EXCLUDED.goal_slug""".update.run()
      Right(())

  /** The slug an OLD slug was renamed to — `None` when it is a live slug, or nothing. */
  def renamedTo(
      slug: com.histmersion.domain.automation.Goal.Slug
  )(using DbCon): Option[com.histmersion.domain.automation.Goal.Slug] =
    GoalAliasTable.table
      .renamedToIn(AliasTable.Id(slug.value))
      .map(id => com.histmersion.domain.automation.Goal.Slug.assume(id.value))

  /** A goal removed in the caller's transaction; false when there was none. */
  def deleteIn(slug: com.histmersion.domain.automation.Goal.Slug)(using DbCon): Boolean =
    sql"DELETE FROM goals WHERE slug = $slug".update.run() > 0
