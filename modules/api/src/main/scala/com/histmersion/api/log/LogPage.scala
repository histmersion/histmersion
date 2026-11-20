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
  object Cursor extends Newtype[String, Pure]

  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]

  val DefaultLimit: Int = 100
  val MaxLimit: Int = 1000

  /** Where a page was read. */
  enum Origin derives CanEqual:
    case Database, File

  /** Which store to read: the table (falling back to the file when the database cannot answer), or the file alone. */
  type From = From.T
  object From extends Newtype[String, Pure]

  /** The filters of a log query, as the wire carries them (the server checks them). */
  final case class Filter(
      trace: Option[LogEvent.Trace] = None,
      label: Option[LogEvent.Label] = None,
      job: Option[LogEvent.Key] = None,
      run: Option[LogEvent.Key] = None,
      source: Option[LogEvent.Key] = None,
      problem: Option[LogEvent.Key] = None,
      kind: Option[LogEvent.Kind] = None,
      since: Option[LogEvent.At] = None,
      until: Option[LogEvent.At] = None,
      limit: Option[Limit] = None,
      before: Option[Cursor] = None,
      from: Option[From] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema
