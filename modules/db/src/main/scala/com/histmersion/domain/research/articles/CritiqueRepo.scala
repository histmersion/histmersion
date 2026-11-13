package com.histmersion
package domain.research.articles
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.domain.library.sources.CitekeyResolver

import com.histmersion.api.Write
import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.research.{CritiqueReason, Critiques}
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.research.Slug
import com.histmersion.api.library.Citekey

/** The critique checklist's stored reasons (V091): why one disagreement between two sources does not bear on an
  * article. The checklist itself is computed (`core.Critiques`); only the reasons are stored. Every method runs on the
  * connection it is given.
  */
private[articles] object CritiqueRepo:
  type By = By.T
  object By extends Newtype[String, Pure]

  /** The article's reasons, by pair. */

  def list(slug: Slug)(using DbCon): List[CritiqueReason] =
    sql"""SELECT src, dst, reason, set_by FROM article_critique_reasons WHERE slug = $slug ORDER BY src, dst"""
      .query[(String, String, String, String)]
      .run()
      .toList
      .map((s, d, r, b) =>
        CritiqueReason(
          Citekey(s),
          Citekey(d),
          Problem.Unclassified(Legacy.Text(r)),
          Some(CritiqueReason.By(b))
        )
      )

  /** One checked change: set `reason` on the edge's direction, or remove the pair's reason. */
  private enum Change derives CanEqual:
    case Set(
        src: com.histmersion.domain.library.Citekey,
        dst: com.histmersion.domain.library.Citekey,
        reason: Problem,
        by: By
    )
    case Remove(a: com.histmersion.domain.library.Citekey, b: com.histmersion.domain.library.Citekey)

  /** Check the reasons of a patch, inside the caller's transaction, writing nothing. Each pair's citekeys resolve as
    * links do (an old citekey names its source); a reason is kept under the direction of the disagreement edge between
    * them (critic first) and is refused when there is none — a reason is about a recorded disagreement. An empty reason
    * removes the pair's reason in either direction. `by` is the setter of a reason that names none. The first refusal
    * is the answer.
    */
  private def check(reasons: List[CritiqueReason], by: By)(using DbCon): Either[Api.Error, List[Change]] =
    val tidy = reasons.map(CritiqueReason.tidy)
    tidy.collectFirst { case Left(m) => m } match
      case Some(m) => Left(Api.Error(m, Some(Api.Error.Code(Write.CodeInvalid))))
      case None    =>
        val rs = tidy.collect { case Right(r) => r }
        def stored(c: Citekey): Option[com.histmersion.domain.library.Citekey] =
          com.histmersion.domain.library.Citekey.option(c.value)
        val known = CitekeyResolver.stored.canonical(rs.flatMap(r => List(r.src, r.dst)).flatMap(stored).distinct)
        val kinds = Critiques.Kinds
        rs.foldLeft[Either[Api.Error, Vector[Change]]](Right(Vector.empty)) { (acc, r) =>
          acc.flatMap { done =>
            (stored(r.src).flatMap(known.get), stored(r.dst).flatMap(known.get)) match
              case (None, _) =>
                Left(
                  Api.Error(
                    Problem.Unclassified(Legacy.Text(s"critique reason ${r.ref.wire}: '${r.src}' is not a citekey")),
                    Some(Api.Error.Code(Write.CodeInvalid))
                  )
                )
              case (_, None) =>
                Left(
                  Api.Error(
                    Problem.Unclassified(Legacy.Text(s"critique reason ${r.ref.wire}: '${r.dst}' is not a citekey")),
                    Some(Api.Error.Code(Write.CodeInvalid))
                  )
                )
              case (Some(a), Some(b)) if r.reason.sentence.isEmpty => Right(done :+ Change.Remove(a, b))
              case (Some(a), Some(b))                              =>
                val dirs = sql"""SELECT src, dst FROM edges WHERE kind = ANY($kinds::text[])
                                 AND ((src = $a AND dst = $b) OR (src = $b AND dst = $a))"""
                  .query[(com.histmersion.domain.library.Citekey, com.histmersion.domain.library.Citekey)]
                  .run()
                  .toList
                  .distinct
                dirs.find(_ == (a, b)).orElse(dirs.headOption) match
                  case None =>
                    Left(
                      Api.Error(
                        Problem.Unclassified(
                          Legacy.Text(
                            s"critique reason ${r.ref.wire}: no ${kinds.mkString("/")} edge joins [[$a]] and [[$b]] — " +
                              "a reason is for a recorded disagreement (add the edge first: histmersion patch <critic> --add-edge <kind>:<other>)"
                          )
                        ),
                        Some(Api.Error.Code(Write.CodeInvalid))
                      )
                    )
                  case Some((s, d)) =>
                    Right(
                      done :+ Change.Set(
                        s,
                        d,
                        r.reason,
                        By(r.by.map(_.value.trim).filter(_.nonEmpty).getOrElse(by.value).take(200))
                      )
                    )
          }
        }.map(_.toList)

  /** Check, then — when every reason passes — apply the reasons of a patch, inside the caller's transaction; `write`
    * (the article's own write, if any) runs between the two, so a refused reason leaves nothing written. Returns how
    * many pairs changed.
    */
  def applyIn(slug: Slug, reasons: List[CritiqueReason], by: By)(
      write: => Unit
  )(using DbCon): Either[Api.Error, RowCount] =
    check(reasons, by).map { changes =>
      write
      RowCount(changes.map {
        case Change.Remove(a, b) =>
          sql"""DELETE FROM article_critique_reasons
                WHERE slug = $slug AND ((src = $a AND dst = $b) OR (src = $b AND dst = $a))""".update.run()
        case Change.Set(s, d, reason, who) =>
          sql"DELETE FROM article_critique_reasons WHERE slug = $slug AND src = $d AND dst = $s".update.run()
          sql"""INSERT INTO article_critique_reasons (slug, src, dst, reason, set_by) VALUES ($slug, $s, $d, ${reason.sentence}, $who)
                ON CONFLICT (slug, src, dst) DO UPDATE SET reason = EXCLUDED.reason, set_by = EXCLUDED.set_by, set_at = now()""".update
            .run()
      }.sum)
    }
