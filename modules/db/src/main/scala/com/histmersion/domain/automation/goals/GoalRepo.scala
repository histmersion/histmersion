package com.histmersion
package domain.automation.goals

import com.histmersion.util.problem.Problem

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
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator

object GoalRepo:

  private val cols =
    sql"""g.slug, g.title, g.status, g.notes, g.tags, g.regions, ${RangeSql.cols(sql"g.period")}, g.parent_slug,
          g.updated_at::text, g.creator::text, ${StoredCreator.at(sql"g.created_at")}"""

  /** One page of the goals (of one status), by slug, on the caller's connection. */
  def listIn(status: Option[GoalRow.Status], limit: PageSize, offset: Offset)(using DbCon): Goal.List =
    val st = status.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
    locally:
      val total = sql"""SELECT count(*)::int FROM goals g
            WHERE ($st::text IS NULL OR g.status = $st)""".query[Int].run().head
      val rows = sql"""SELECT $cols FROM goals g
            WHERE ($st::text IS NULL OR g.status = $st)
            ORDER BY g.slug
            LIMIT ${limit.value.max(1)} OFFSET ${offset.value.max(0)}
            """.query[GoalRow].run().toList.map(_.toDto)
      Goal.List.of(
        RowCount(total),
        rows,
        UnreadPage.Offset(offset.value.max(0))
      )

  def get(slug: domain.automation.Goal.Slug)(using DbCon): Option[Goal] =
    sql"""SELECT $cols FROM goals g WHERE g.slug = $slug"""
      .query[GoalRow]
      .run()
      .headOption
      .map(_.toDto)

  /** Every goal, by slug — unpaged, for the `meta/GOALS.md` export. */
  def all()(using DbCon): List[Goal] =
    sql"""SELECT $cols FROM goals g ORDER BY g.slug""".query[GoalRow].run().toList.map(_.toDto)

  /** A goal's direct sub-goals, by slug. */
  def children(slug: domain.automation.Goal.Slug)(using DbCon): List[Goal] =
    sql"""SELECT $cols FROM goals g WHERE g.parent_slug = $slug ORDER BY g.slug"""
      .query[GoalRow]
      .run()
      .toList
      .map(_.toDto)

  def exists(slug: domain.automation.Goal.Slug)(using DbCon): Boolean =
    (sql"SELECT 1 FROM goals WHERE slug = $slug".query[Int].run().nonEmpty)

  /** Create or replace a goal. Either bound of its period may be given alone (an open period); one that starts after it
    * ends is refused.
    */
  def put(slug: domain.automation.Goal.Slug, in: Goal.Put)(using
      DbCon
  )(using
      Creator.New,
      CreatedAt
  ): Either[Api.Error, Unit] =
    periodOf(slug, in).map(putIn(slug, in, _))

  /** Create a goal, never replace one, in the caller's transaction: a slug in use, or an old slug of another goal
    * (V053), is a conflict, and so is a period that starts after it ends refused. Nothing is written then.
    */
  def createIn(slug: domain.automation.Goal.Slug, in: Goal.Put)(using
      DbCon
  )(using creator: Creator.New, at: CreatedAt): Either[Api.Error, Unit] =
    periodOf(slug, in).flatMap { period =>
      val conflict = Api.Error(Problem.Conflict.AlreadyExists(Problem.TypeName.of(Goal.Slug), Problem.Raw.of(slug)))
      renamedTo(slug) match
        case Some(to) =>
          Left(
            Api.Error(
              Problem.Conflict.Aliased(Problem.TypeName.of(Goal.Slug), Problem.Raw.of(slug), Problem.Raw.of(to))
            )
          )
        case None =>
          val made =
            sql"""INSERT INTO goals (slug, title, status, notes, tags, regions, period, parent_slug, updated_at, creator,
                                     created_at)
                  VALUES ($slug, ${in.title}, ${in.status}, ${in.notes},
                          ${in.tags}::text[], ${in.regions}::text[],
                          $period::int8range, ${in.parentSlug}, now(), ${StoredCreator.column(creator)}::jsonb,
                          $at::timestamptz)
                  ON CONFLICT (slug) DO NOTHING""".update.run()
          Either.cond(made > 0, (), conflict)
    }

  /** The goal's period as a range literal: either bound may be given alone (an open period); one that starts after it
    * ends is refused.
    */
  private def periodOf(slug: domain.automation.Goal.Slug, in: Goal.Put): Either[Api.Error, Option[String]] =
    RangeSql
      .merge(
        (None, None),
        in.periodStart.map(y => DeepTime.SignedYear(y.value)),
        in.periodEnd.map(y => DeepTime.SignedYear(y.value)),
        s"goal '${slug.value}'"
      )
      .getOrElse(Right((None, None)))
      .map((s, e) => RangeSql.literal(s, e))

  private def putIn(slug: domain.automation.Goal.Slug, in: Goal.Put, period: Option[String])(using
      DbCon
  )(using creator: Creator.New, at: CreatedAt): Unit =
    sql"""INSERT INTO goals (slug, title, status, notes, tags, regions, period, parent_slug, updated_at, creator,
                             created_at)
          VALUES ($slug, ${in.title}, ${in.status}, ${in.notes},
                  ${in.tags}::text[], ${in.regions}::text[],
                  $period::int8range, ${in.parentSlug}, now(), ${StoredCreator.column(creator)}::jsonb,
                  $at::timestamptz)
          ON CONFLICT (slug) DO UPDATE SET
            title = EXCLUDED.title, status = EXCLUDED.status, notes = EXCLUDED.notes,
            tags = EXCLUDED.tags, regions = EXCLUDED.regions, period = EXCLUDED.period,
            parent_slug = EXCLUDED.parent_slug, updated_at = now()""".update.run()

  /** A goal's fields changed as given, in the caller's transaction. */
  def patchIn(slug: domain.automation.Goal.Slug, p: Goal.Patch)(using DbCon): Either[Api.Error, Unit] =
    locally:
      val exists = sql"SELECT 1 FROM goals WHERE slug = $slug".query[Int].run().nonEmpty
      if !exists then
        Left(
          Api.Error(
            Problem.NotFound.Entity(Problem.TypeName.of(Goal.Slug), Problem.Raw(s"${slug.value}")),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
      else
        p.title.foreach(v =>
          Unused(sql"UPDATE goals SET title = $v, updated_at = now() WHERE slug = $slug".update.run(), Unused.Why.Held)
        )
        p.status.foreach(v =>
          Unused(sql"UPDATE goals SET status = $v, updated_at = now() WHERE slug = $slug".update.run(), Unused.Why.Held)
        )
        p.notes.foreach(v =>
          Unused(sql"UPDATE goals SET notes = $v, updated_at = now() WHERE slug = $slug".update.run(), Unused.Why.Held)
        )
        p.tags.foreach { v =>
          Unused(
            sql"""UPDATE goals SET tags = $v::text[],
                updated_at = now() WHERE slug = $slug""".update.run(),
            Unused.Why.Held
          )
        }
        p.regions.foreach { v =>
          Unused(
            sql"""UPDATE goals SET regions = $v::text[],
                updated_at = now() WHERE slug = $slug""".update.run(),
            Unused.Why.Held
          )
        }
        p.parentSlug.foreach(v =>
          Unused(
            sql"UPDATE goals SET parent_slug = $v, updated_at = now() WHERE slug = $slug".update.run(),
            Unused.Why.Held
          )
        )
        // a cap of 0 (or less) clears it: the runs fall back to the server default
        p.budgetUsd.foreach { v =>
          val usd = Option.when(v.value > 0)(BigDecimal(v.value))
          Unused(
            sql"UPDATE goals SET budget_usd = $usd, updated_at = now() WHERE slug = $slug".update.run(),
            Unused.Why.Held
          )
        }
        // a bound given alone replaces that bound; the other stays as stored ([[RangeSql.merge]])
        val storedRaw = sql"SELECT ${RangeSql.cols(sql"period")} FROM goals WHERE slug = $slug"
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
      oldSlug: domain.automation.Goal.Slug,
      newSlug: domain.automation.Goal.Slug
  )(using DbCon): Either[Api.Error, Unit] =
    def exists(s: domain.automation.Goal.Slug) =
      sql"SELECT 1 FROM goals WHERE slug = $s".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT goal_slug FROM goal_aliases WHERE alias = $newSlug"
      .query[String]
      .run()
      .headOption
      .map(domain.automation.Goal.Slug.assume(_))
    if !exists(oldSlug) then
      Left(
        Api.Error(
          Problem.NotFound.Entity(Problem.TypeName.of(Goal.Slug), Problem.Raw(s"${oldSlug.value}")),
          Some(Api.Error.Code(Write.CodeNotFound))
        )
      )
    else if exists(newSlug) then
      Left(
        Api.Error(
          Problem.Conflict.AlreadyExists(Problem.TypeName.of(Goal.Slug), Problem.Raw.of(newSlug)),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else if aliasOf.exists(_ != oldSlug) then
      Left(
        Api.Error(
          Problem.Conflict.Aliased(
            Problem.TypeName.of(Goal.Slug),
            Problem.Raw.of(newSlug),
            Problem.Raw.of(aliasOf.get)
          ),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else
      Unused(sql"DELETE FROM goal_aliases WHERE alias = $newSlug".update.run(), Unused.Why.Idempotent)
      Unused(sql"UPDATE goals SET slug = $newSlug WHERE slug = $oldSlug".update.run(), Unused.Why.Held)
      sql"""INSERT INTO goal_aliases (alias, goal_slug) VALUES ($oldSlug, $newSlug)
            ON CONFLICT (alias) DO UPDATE SET goal_slug = EXCLUDED.goal_slug""".update.run()
      Right(())

  /** The slug an OLD slug was renamed to — `None` when it is a live slug, or nothing. */
  def renamedTo(
      slug: domain.automation.Goal.Slug
  )(using DbCon): Option[domain.automation.Goal.Slug] =
    GoalAliasTable.table
      .renamedToIn(AliasTable.Id(slug.value))
      .map(id => domain.automation.Goal.Slug.assume(id.value))

  /** A goal removed in the caller's transaction; false when there was none. */
  def deleteIn(slug: domain.automation.Goal.Slug)(using DbCon): Boolean =
    sql"DELETE FROM goals WHERE slug = $slug".update.run() > 0
