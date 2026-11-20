package com.histmersion
package api.log

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** A page of the structured log, newest first: `next` is the cursor of the older page, `origin` where it was read (the
  * file when the database could not be).
  */
final case class LogPage(events: Vector[LogEvent], next: Option[LogPage.Cursor], origin: LogPage.Origin)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object LogPage:
  type Cursor = Cursor.T
  object Cursor extends Newtype[String, Pure]:
    /** The cursor of the page after the event at `at` with `id`. */
    def of(at: LogEvent.At, id: LogEvent.Id): Cursor = Cursor(s"$at~$id")

    extension (c: Cursor)
      /** As a filter gives it: trimmed, none when blank. */
      def present: Option[Cursor] = Option(c.value.trim).filter(_.nonEmpty).map(Cursor(_))

      /** Its two parts, `<instant>~<id>`, when it has both. */
      def parts: Option[(String, String)] = c.value.split("~", 2) match
        case Array(at, id) if id.nonEmpty => Some((at, id))
        case _                            => None

  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]:
    /** The page size asked for, within 1 … [[MaxLimit]]; [[DefaultLimit]] when none was. */
    def clamped(asked: Option[Limit]): Limit = Limit(asked.fold(DefaultLimit)(_.value).max(1).min(MaxLimit))

    /** The largest page. */
    def max: Limit = Limit(MaxLimit)

    extension (l: Limit)
      /** The first `l` of `rows`, and the rest. */
      def cut[A](rows: Vector[A]): (Vector[A], Vector[A]) = rows.splitAt(l.value)

      /** Whether `n` rows are more than a page. */
      def below(n: Int): Boolean = n > l.value

      /** One more than a page: what a query fetches to know whether an older page follows. */
      def andOne: Limit = Limit(l.value + 1)

  val DefaultLimit: Int = 100
  val MaxLimit: Int = 1000

  /** Where a page was read. */
  enum Origin derives CanEqual:
    case Database, File

  /** Which store to read: the table (falling back to the file when the database cannot answer), or the file alone. */
  type From = From.T
  object From extends Newtype[String, Pure]:
    /** Reading the file alone. */
    val File: From = From("file")

    extension (f: From)
      /** As a filter gives it: trimmed, none when blank. */
      def present: Option[From] = Option(f.value.trim).filter(_.nonEmpty).map(From(_))

      /** Whether it names `store`, whatever its case. */
      def names(store: String): Boolean = f.value.trim.equalsIgnoreCase(store)

  /** The filters of a log query, as the wire carries them (the server checks them). */
  final case class Filter(
      trace: Option[LogEvent.Trace] = None,
      label: Option[LogEvent.Label] = None,
      job: Option[LogEvent.Key] = None,
      run: Option[LogEvent.Key] = None,
      source: Option[LogEvent.Key] = None,
      problem: Option[LogEvent.ProblemId] = None,
      kind: Option[LogEvent.Kind] = None,
      since: Option[LogEvent.At] = None,
      until: Option[LogEvent.At] = None,
      limit: Option[Limit] = None,
      before: Option[Cursor] = None,
      from: Option[From] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    /** The problem asked for when no other filter is given: what happened around that problem alone. */
    def problemAlone: Option[LogEvent.ProblemId] =
      problem.filter(_ => List(trace, label, job, run, source, kind, since, until, before).forall(_.isEmpty))
