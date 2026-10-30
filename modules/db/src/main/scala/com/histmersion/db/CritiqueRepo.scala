package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The critique checklist's stored reasons (V091): why one disagreement between two sources does not bear on an
  * article. The checklist itself is computed (`core.Critiques`); only the reasons are stored. Stateless.
  */
object CritiqueRepo:

  /** The article's reasons, by pair. */
  def list(ds: DataSource, slug: String): List[CritiqueReason] = connect(ds)(list(slug))

  private[db] def list(slug: String)(using DbCon): List[CritiqueReason] =
    sql"""SELECT src, dst, reason, set_by FROM article_critique_reasons WHERE slug = $slug ORDER BY src, dst"""
      .query[(String, String, String, String)]
      .run()
      .toList
      .map((s, d, r, b) => CritiqueReason(s, d, r, Some(b)))

  /** One checked change: set `reason` on the edge's direction, or remove the pair's reason. */
  private enum Change:
    case Set(src: String, dst: String, reason: String, by: String)
    case Remove(a: String, b: String)

  /** Check the reasons of a patch, inside the caller's transaction, writing nothing. Each pair's citekeys resolve as
    * links do (an old citekey names its source); a reason is kept under the direction of the disagreement edge between
    * them (critic first) and is refused when there is none — a reason is about a recorded disagreement. An empty reason
    * removes the pair's reason in either direction. `by` is the setter of a reason that names none. The first refusal
    * is the answer.
    */
  private def check(reasons: List[CritiqueReason], by: String)(using DbCon): Either[ApiError, List[Change]] =
    val tidy = reasons.map(CritiqueReason.tidy)
    tidy.collectFirst { case Left(m) => m } match
      case Some(m) => Left(ApiError(m, Some(Write.CodeInvalid)))
      case None    =>
        val rs = tidy.collect { case Right(r) => r }
        val known = SourceRepo.canonicalAll(rs.flatMap(r => List(r.src, r.dst)).distinct)
        val kinds = Critiques.Kinds
        rs.foldLeft[Either[ApiError, Vector[Change]]](Right(Vector.empty)) { (acc, r) =>
          acc.flatMap { done =>
            (known.get(r.src), known.get(r.dst)) match
              case (None, _) =>
                Left(ApiError(s"critique reason ${r.ref.wire}: '${r.src}' is not a citekey", Some(Write.CodeInvalid)))
              case (_, None) =>
                Left(ApiError(s"critique reason ${r.ref.wire}: '${r.dst}' is not a citekey", Some(Write.CodeInvalid)))
              case (Some(a), Some(b)) if r.reason.isEmpty => Right(done :+ Change.Remove(a, b))
              case (Some(a), Some(b))                     =>
                val dirs = sql"""SELECT src, dst FROM edges WHERE kind = ANY($kinds::text[])
                                 AND ((src = $a AND dst = $b) OR (src = $b AND dst = $a))"""
                  .query[(String, String)]
                  .run()
                  .toList
                  .distinct
                dirs.find(_ == (a, b)).orElse(dirs.headOption) match
                  case None =>
                    Left(
                      ApiError(
                        s"critique reason ${r.ref.wire}: no ${kinds.mkString("/")} edge joins [[$a]] and [[$b]] — " +
                          "a reason is for a recorded disagreement (add the edge first: histmersion patch <critic> --add-edge <kind>:<other>)",
                        Some(Write.CodeInvalid)
                      )
                    )
                  case Some((s, d)) =>
                    Right(
                      done :+ Change.Set(s, d, r.reason, r.by.map(_.trim).filter(_.nonEmpty).getOrElse(by).take(200))
                    )
          }
        }.map(_.toList)

  /** Check, then — when every reason passes — apply the reasons of a patch, inside the caller's transaction; `write`
    * (the article's own write, if any) runs between the two, so a refused reason leaves nothing written. Returns how
    * many pairs changed.
    */
  private[db] def applyIn(slug: String, reasons: List[CritiqueReason], by: String)(
      write: => Unit
  )(using DbCon): Either[ApiError, Int] =
    check(reasons, by).map { changes =>
      write
      changes.map {
        case Change.Remove(a, b) =>
          sql"""DELETE FROM article_critique_reasons
                WHERE slug = $slug AND ((src = $a AND dst = $b) OR (src = $b AND dst = $a))""".update.run()
        case Change.Set(s, d, reason, who) =>
          sql"DELETE FROM article_critique_reasons WHERE slug = $slug AND src = $d AND dst = $s".update.run()
          sql"""INSERT INTO article_critique_reasons (slug, src, dst, reason, set_by) VALUES ($slug, $s, $d, $reason, $who)
                ON CONFLICT (slug, src, dst) DO UPDATE SET reason = EXCLUDED.reason, set_by = EXCLUDED.set_by, set_at = now()""".update
            .run()
      }.sum
    }
