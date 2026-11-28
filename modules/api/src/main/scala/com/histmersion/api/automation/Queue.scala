package com.histmersion
package api.automation

import com.histmersion.api.automation.queue.Kind
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.queue.GoalRef
import com.histmersion.api.automation.queue.ItemGoals

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Field, Invalid, Raw, TypeName}

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.library.Acquire
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object Queue:

  /** Why an item is on a list, or how it was closed: a rationale, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

    extension (r: Rationale)
      /** Does it record that the item was ingested as this citekey (`ingested as [[citekey]]`)? */
      def recordsIngestOf(citekey: Citekey): Boolean = s"$r".contains(ingestTag(citekey))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def ingestTag(citekey: Citekey): String = s"ingested as [[$citekey]]"
  type Export = Export.T
  object Export extends Newtype[String, Pure]
  type FileText = FileText.T
  object FileText extends Newtype[String, Pure]
  type Id = Id.T
  object Id extends Newtype[Int, Pure]
  type InstantKey = InstantKey.T
  object InstantKey extends Newtype[Long, Pure]
  type Preamble = Preamble.T
  object Preamble extends Newtype[String, Pure]
  export Kind.Label as Wire

  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  val Open = "open"
  val Done = "done"
  val Note = "note"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val Statuses: List[QueueItem.Status] = List(QueueItem.Status(Open), QueueItem.Status(Done), QueueItem.Status(Note))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def statusOf(raw: Option[QueueItem.Status]): Either[Problem, Option[QueueItem.Status]] =
    raw.map((_).value.trim.toLowerCase).filter(_.nonEmpty).map(x => QueueItem.Status(x)) match
      case None                            => Right(None)
      case Some(s) if Statuses.contains(s) => Right(Some(s))
      case Some(s)                         =>
        Left(Invalid.Unknown.here(TypeName.of(QueueItem.Status), Raw(s"$s"), Statuses.toVector.map(x => Raw(s"$x"))))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def textOf(raw: Option[QueueItem.Text]): Either[Problem, Option[QueueItem.Text]] =
    raw match
      case Some(t) if (t).value.trim.isEmpty => Left(Invalid.Missing.here(Field("text")))
      case other                             => Right(other)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fulfilledReason(prev: Option[Rationale], citekey: Citekey): Rationale =
    val tag = Rationale.ingestTag(citekey)
    prev.map(r => s"$r".trim).filter(_.nonEmpty) match
      case Some(p) if p.contains(tag) => Rationale(s"$p")
      case Some(p)                    => Rationale(s"$p · $tag")
      case None                       => Rationale(s"$tag")

  def itemGoals(own: List[(QueueItem.Id, GoalRef)], linked: List[(QueueItem.Id, GoalRef)]): List[ItemGoals] =
    (own ++ linked).groupBy(_._1).toList.sortBy(_._1).map((id, gs) => ItemGoals(id, gs.map(_._2).distinctBy(_.slug)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def createdAtOf(raw: Option[QueueItem.CreatedAt]): Either[Problem, Option[QueueItem.CreatedAt]] =
    raw.map((_).value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(s) =>
        scala.util
          .Try(java.time.Instant.parse(s))
          .toEither
          .map(i => Some(i.toString))
          .left
          .map(_ =>
            Invalid.Unreadable(
              TypeName.of(QueueItem.CreatedAt),
              Raw(s"$s"),
              Vector(Raw("2026-08-25T00:00:00Z")),
              Vector(Field("createdAt"))
            )
          )
          .map(refactorValue2 => (refactorValue2).map(refactorValue3 => QueueItem.CreatedAt(refactorValue3)))

  val Sep = " > "

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def path(topic: Option[QueueItem.Topic]): Vector[String] =
    topic
      .map((_).value.split(java.util.regex.Pattern.quote(Sep), -1).toVector.filter(_.nonEmpty))
      .getOrElse(Vector.empty)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def topicOf(p: Vector[String]): Option[QueueItem.Topic] =
    if p.isEmpty then None else Some(QueueItem.Topic(p.mkString(Sep)))
