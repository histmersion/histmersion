package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

private[db] final case class GoalRow(
    slug: String, title: String, status: String, notes: Option[String],
    tags: List[String], regions: List[String],
    periodStart: Option[Long], periodEnd: Option[Long],
    parentSlug: Option[String], updatedAt: Option[String]):
  def toDto: Goal = Goal(slug, title, status, notes,
    tags, regions,
    periodStart, periodEnd, parentSlug, updatedAt)

given DbCodec[GoalRow] = DbCodec.derived

object GoalRepo:

  private val cols =
    "g.slug, g.title, g.status, g.notes, g.tags, g.regions, " +
      "lower(g.period), upper(g.period) - 1, g.parent_slug, g.updated_at::text"

  def list(ds: DataSource, status: Option[String], limit: Int, offset: Int): GoalList =
    val st = status.map(_.trim.toLowerCase).filter(_.nonEmpty)
    connect(ds):
      val total = sql"""SELECT count(*)::int FROM goals g
            WHERE (${st}::text IS NULL OR g.status = ${st})""".query[Int].run().head
      val rows = sql"""SELECT ${SqlLiteral(cols)} FROM goals g
            WHERE (${st}::text IS NULL OR g.status = ${st})
            ORDER BY g.slug
            LIMIT ${limit.max(1)} OFFSET ${offset.max(0)}
            """.query[GoalRow].run().toList.map(_.toDto)
      GoalList(total, rows)

  def get(ds: DataSource, slug: String): Option[Goal] =
    connect(ds):
      sql"""SELECT ${SqlLiteral(cols)} FROM goals g WHERE g.slug = $slug"""
        .query[GoalRow].run().headOption.map(_.toDto)

  /** Every goal, by slug — unpaged, for the `meta/GOALS.md` export. */
  def all(ds: DataSource): List[Goal] =
    connect(ds):
      sql"""SELECT ${SqlLiteral(cols)} FROM goals g ORDER BY g.slug""".query[GoalRow].run().toList.map(_.toDto)

  /** A goal's direct sub-goals, by slug. */
  def children(ds: DataSource, slug: String): List[Goal] =
    connect(ds):
      sql"""SELECT ${SqlLiteral(cols)} FROM goals g WHERE g.parent_slug = $slug ORDER BY g.slug"""
        .query[GoalRow].run().toList.map(_.toDto)

  def exists(ds: DataSource, slug: String): Boolean =
    connect(ds)(sql"SELECT 1 FROM goals WHERE slug = $slug".query[Int].run().nonEmpty)

  def put(ds: DataSource, slug: String, in: GoalPut): Unit =
    transact(ds):
      val period = (in.periodStart, in.periodEnd) match
        case (Some(s), Some(e)) => Some(s"[$s,${e + 1})")
        case _ => None: Option[String]
      sql"""INSERT INTO goals (slug, title, status, notes, tags, regions, period, parent_slug, updated_at)
            VALUES ($slug, ${in.title}, ${in.status}, ${in.notes},
                    ${in.tags}::text[], ${in.regions}::text[],
                    ${period}::int8range, ${in.parentSlug}, now())
            ON CONFLICT (slug) DO UPDATE SET
              title = EXCLUDED.title, status = EXCLUDED.status, notes = EXCLUDED.notes,
              tags = EXCLUDED.tags, regions = EXCLUDED.regions, period = EXCLUDED.period,
              parent_slug = EXCLUDED.parent_slug, updated_at = now()""".update.run()

  def patch(ds: DataSource, slug: String, p: GoalPatch): Either[ApiError, Unit] =
    transact(ds):
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
        p.parentSlug.foreach(v => sql"UPDATE goals SET parent_slug = $v, updated_at = now() WHERE slug = $slug".update.run())
        // a cap of 0 (or less) clears it: the runs fall back to the server default
        p.budgetUsd.foreach { v =>
          val usd = Option.when(v > 0)(BigDecimal(v))
          sql"UPDATE goals SET budget_usd = $usd, updated_at = now() WHERE slug = $slug".update.run()
        }
        (p.periodStart, p.periodEnd) match
          case (Some(s), Some(e)) =>
            val range = s"[$s,${e + 1})"
            sql"UPDATE goals SET period = ${range}::int8range, updated_at = now() WHERE slug = $slug".update.run()
          case _ => ()
        Right(())

  /** Rename a goal (V042), inside the caller's transaction: the slug moves with one UPDATE (its
    * sub-goals' parent cascades). What a bundle import does when a goal was renamed on the other side. */
  private[db] def renameIn(oldSlug: String, newSlug: String)(using DbCon): Either[ApiError, Unit] =
    def exists(s: String) = sql"SELECT 1 FROM goals WHERE slug = $s".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT goal_slug FROM goal_aliases WHERE alias = $newSlug".query[String].run().headOption
    if !exists(oldSlug) then Left(ApiError(s"No goal '$oldSlug'", Some(Write.CodeNotFound)))
    else if exists(newSlug) then Left(ApiError(s"'$newSlug' is already the slug of another goal", Some(Write.CodeInvalid)))
    else if aliasOf.exists(_ != oldSlug) then
      Left(ApiError(s"'$newSlug' is an old slug of '${aliasOf.get}' and still resolves to it — pick another", Some(Write.CodeInvalid)))
    else
      sql"DELETE FROM goal_aliases WHERE alias = $newSlug".update.run()
      sql"UPDATE goals SET slug = $newSlug WHERE slug = $oldSlug".update.run()
      sql"""INSERT INTO goal_aliases (alias, goal_slug) VALUES ($oldSlug, $newSlug)
            ON CONFLICT (alias) DO UPDATE SET goal_slug = EXCLUDED.goal_slug""".update.run()
      Right(())

  /** `histmersion goals rename`: a new slug, the old one kept as an alias (V053). One transaction. */
  def rename(ds: DataSource, oldSlug: String, newSlug: String): Either[ApiError, Unit] =
    transact(ds)(renameIn(oldSlug.trim, newSlug.trim))

  /** The slug an OLD slug was renamed to — `None` when it is a live slug, or nothing. */
  def renamedTo(ds: DataSource, slug: String): Option[String] =
    connect(ds):
      sql"SELECT goal_slug FROM goal_aliases WHERE alias = $slug AND NOT EXISTS (SELECT 1 FROM goals WHERE slug = $slug)"
        .query[String].run().headOption

  def delete(ds: DataSource, slug: String): Boolean =
    transact(ds):
      sql"DELETE FROM goals WHERE slug = $slug".update.run() > 0

// ── agent state ────────────────────────────────────────────────────────────────────────────────

given DbCodec[AgentStateEntry] = DbCodec.derived

object AgentStateRepo:

  def list(ds: DataSource): List[AgentStateEntry] =
    connect(ds):
      sql"SELECT key, value::text, updated_at::text FROM agent_state ORDER BY key"
        .query[AgentStateEntry].run().toList

  def get(ds: DataSource, key: String): Option[AgentStateEntry] =
    connect(ds):
      sql"SELECT key, value::text, updated_at::text FROM agent_state WHERE key = $key"
        .query[AgentStateEntry].run().headOption

  def set(ds: DataSource, key: String, value: String): Unit =
    transact(ds):
      sql"""INSERT INTO agent_state (key, value, updated_at) VALUES ($key, ${value}::jsonb, now())
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()""".update.run()

  def delete(ds: DataSource, key: String): Boolean =
    transact(ds):
      sql"DELETE FROM agent_state WHERE key = $key".update.run() > 0
