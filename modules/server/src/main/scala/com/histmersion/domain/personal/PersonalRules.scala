package com.histmersion.domain.personal

import com.histmersion.api.personal.*
import com.histmersion.values.{Latitude, Longitude, Year}

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The rules bookmarks, notes and read marks follow wherever they are kept — in the database or in memory: the times as
  * the wire writes them, the names, the search, and a stored row read back as a target. Pure.
  */
object PersonalRules:

  private val Iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)

  /** The time a store keeps: to the microsecond, as Postgres does. */
  def kept(at: Instant): Instant = at.truncatedTo(ChronoUnit.MICROS)

  /** A time as the wire writes it (`2026-10-30T12:40:00.123456Z`), the same text the database answers. */
  def iso(at: Instant): String = Iso.format(kept(at))

  /** The most characters an item's name keeps. */
  val TitleChars = 300

  /** An item's name as the person saw it, tidied: none when empty. */
  def storedTitle(t: Option[String]): Option[String] =
    t.map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty).map(_.take(TitleChars))

  /** What a target is called: the item's name now, else the name it had when it was marked, else its key or what its
    * kind is.
    */
  def title(t: Target, live: Option[String], stored: Option[String]): String =
    storedTitle(live).orElse(storedTitle(stored)).getOrElse(t.itemKey.getOrElse(t.kind.label))

  def label(raw: Option[BookmarkLabel]): Either[String, Option[BookmarkLabel]] =
    raw.map(_.value.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty) match
      case Some(l) if l.length > BookmarkLabel.MaxChars =>
        Left(s"a bookmark's name is at most ${BookmarkLabel.MaxChars} characters")
      case other => Right(other.map(BookmarkLabel(_)))

  def text(raw: NoteText): Either[String, NoteText] =
    val t = raw.value.replace("\r\n", "\n").stripTrailing
    if t.trim.isEmpty then Left("a note needs some text")
    else if t.length > NoteText.MaxChars then
      Left(s"a note is at most ${NoteText.MaxChars} characters (this one: ${t.length})")
    else Right(NoteText(t))

  /** The words a search asks for, each to be found (case does not matter). */
  def words(q: Option[String]): List[String] =
    q.toList.flatMap(_.toLowerCase.split("\\s+").toList).map(_.trim).filter(_.nonEmpty)

  def matches(words: List[String], texts: String*): Boolean =
    val all = texts.mkString(" ").toLowerCase
    words.forall(all.contains)

  /** A stored row's target; none when the row does not make one (a kind this version does not know). */
  def target(
      kind: String,
      key: Option[String],
      lat: Option[Double],
      lon: Option[Double],
      zoom: Option[Double],
      from: Option[Long],
      to: Option[Long],
      view: Option[String]
  ): Option[Target] =
    ItemKind.values.find(_.wire == kind).map { k =>
      Target(
        k,
        key.map(ItemKey(_)),
        lat.flatMap(Latitude.option),
        lon.flatMap(Longitude.option),
        zoom.flatMap(MapZoom.option),
        from.map(v => Year(v.toInt)),
        to.map(v => Year(v.toInt)),
        view.map(ViewState(_))
      )
    }

  /** Newest first by `at`, then by id from the highest (so equal times keep one order everywhere). */
  def newestFirst[A](as: Seq[A])(at: A => Instant, id: A => String): List[A] =
    as.sortWith((a, b) => at(a).isAfter(at(b)) || (at(a) == at(b) && id(a) > id(b))).toList
