package com.histmersion
package domain.library.sources

import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.database.given
import com.histmersion.domain.{ItemKey, ItemKind, ItemRef}
import com.histmersion.domain.personal.marks.{Readable, UnreadItem, UnreadItems}
import com.histmersion.util.database.Transactor
import com.histmersion.domain.Title
import com.histmersion.{Offset, PageSize, WireEnumValues, Year}

import com.augustnagro.magnum.*
import com.histmersion.domain.library.Source
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.AuthorName

/** The sources a person has not marked read, by title, as "Not read yet" lists them. */
final class SourceReadable(db: Transactor) extends Readable:
  val kind: ItemKind = SourceReferable.Kind

  private def notRead(read: Set[ItemRef]): Sql =
    Sql.of(
      Sql.Text(" FROM sources i WHERE NOT (i.citekey = ANY(?::text[]) OR i.uid = ANY(?::uuid[]))"),
      Param.A(read.toList.map(r => Param.A.V(r.key.value))),
      Param.A(read.toList.flatMap(_.uid).map(u => Param.A.V(u.value.toString)))
    )

  def unread(read: Set[ItemRef], size: PageSize, from: Offset): UnreadItems =
    db.read:
      val rows =
        (Sql.raw(
          Sql.Text("SELECT i.citekey, i.title, i.authors, i.year, i.status, lower(i.period), upper(i.period) - 1")
        ) ++
          notRead(read) ++
          Sql.of(
            Sql.Text(" ORDER BY lower(i.title), i.citekey LIMIT ? OFFSET ?"),
            Param.I(Param.I.V(size.value)),
            Param.I(Param.I.V(from.value))
          )).frag
          .query[(String, String, List[String], Option[Int], Option[String], Option[Long], Option[Long])]
          .run()
          .toList
      val total = (Sql.raw(Sql.Text("SELECT count(*)")) ++ notRead(read)).frag.query[Long].run().head.toInt
      UnreadItems(
        rows.map((ck, title, authors, year, status, start, end) =>
          SourceReadable.row(
            Citekey.assume(ck),
            Title(title),
            authors.map(a => FullSourceRow.Authors(a)),
            year.map(Year(_)),
            status.map(w => Source.Status.Wire(w)),
            start.map(SourceReadable.RowStart(_)),
            end.map(SourceReadable.RowEnd(_))
          )
        ),
        UnreadItems.Total(total)
      )

object SourceReadable:
  type RowStart = RowStart.T
  object RowStart extends Newtype[Long, Pure]
  type RowEnd = RowEnd.T
  object RowEnd extends Newtype[Long, Pure]

  private[sources] def row(
      ck: Citekey,
      title: Title,
      authors: List[FullSourceRow.Authors],
      year: Option[Year],
      status: Option[Source.Status.Wire],
      start: Option[RowStart],
      end: Option[RowEnd]
  ): UnreadItem =
    UnreadItem(
      ItemKey(ck.value),
      title,
      authors.map(a => AuthorName(a.value)),
      year,
      status
        .flatMap(w => summon[WireEnumValues[Source.Status]].fromWire(WireEnumValues.Wire(w.value)))
        .map(s => UnreadItem.Status(s.wire.value)),
      start.map(y => Year(y.value.toInt)),
      end.map(y => Year(y.value.toInt))
    )
