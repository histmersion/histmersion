package com.histmersion
package api.log

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.time.TimeBound
import io.github.iltotore.iron.constraint.any.Pure

import java.time.Instant
import scala.util.Try

import LogPage.{Cursor, Origin}

final case class LogPage(events: Vector[LogEvent], next: Option[Cursor], origin: Origin)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:

  def markdown(older: Cursor => String): String =
    val from = origin match
      case Origin.Database => ""
      case Origin.File     => "_Read from the log file: the database could not answer._\n\n"
    val lines = if events.isEmpty then "_No events._" else events.map(_.markdown).mkString("\n")
    from + lines + next.fold("")(c => s"\n\n${older(c)}")

  def oldestFirst: LogPage = LogPage(events.reverse, next, origin)

object LogPage:
  type Cursor = Cursor.T
  object Cursor extends Newtype[String, Pure]:
    def of(at: LogEvent.At, id: LogEvent.Id): Cursor = Cursor(s"$at~$id")

    extension (c: Cursor)
      def present: Option[Cursor] = Option(c.value.trim).filter(_.nonEmpty).map(Cursor(_))

      def place: Option[(Instant, LogEvent.Id)] = c.value.split("~", 2) match
        case Array(at, id) => Try(Instant.parse(at)).toOption.zip(LogEvent.Id.parse(id))
        case _             => None

  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]:
    def clamped(asked: Option[Limit]): Limit = Limit(asked.fold(DefaultLimit)(_.value).max(1).min(MaxLimit))

    def max: Limit = Limit(MaxLimit)

    def one: Limit = Limit(1)

    extension (l: Limit)
      def cut[A](rows: Vector[A]): (Vector[A], Vector[A]) = rows.splitAt(l.value)

      def below(n: Int): Boolean = n > l.value

      def andOne: Limit = Limit(l.value + 1)

  val DefaultLimit: Int = 100
  val MaxLimit: Int = 1000

  enum Origin derives CanEqual:
    case Database, File

  type From = From.T
  object From extends Newtype[String, Pure]:
    val File: From = From("file")

    extension (f: From)
      def present: Option[From] = Option(f.value.trim).filter(_.nonEmpty).map(From(_))

      def names(store: String): Boolean = f.value.trim.equalsIgnoreCase(store)

  final case class Filter(
      trace: Option[LogEvent.Trace] = None,
      label: Option[LogEvent.Label] = None,
      job: Option[LogEvent.Key] = None,
      run: Option[LogEvent.Key] = None,
      source: Option[LogEvent.Key] = None,
      problem: Option[LogEvent.ProblemId] = None,
      kind: Option[LogEvent.Kind] = None,
      since: Option[TimeBound] = None,
      until: Option[TimeBound] = None,
      limit: Option[Limit] = None,
      before: Option[Cursor] = None,
      from: Option[From] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    def problemAlone: Option[LogEvent.ProblemId] =
      problem.filter(_ => List(trace, label, job, run, source, kind, since, until, before).forall(_.isEmpty))
