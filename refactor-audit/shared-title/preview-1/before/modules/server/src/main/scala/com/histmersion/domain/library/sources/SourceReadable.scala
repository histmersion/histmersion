package com.histmersion.domain.library.sources

import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.database.given
import com.histmersion.domain.{ItemKey, ItemKind, ItemRef}
import com.histmersion.domain.personal.marks.{Readable, UnreadItem, UnreadItems}
import com.histmersion.util.database.Transactor
import com.histmersion.domain.library.{AuthorName, Title}
import com.histmersion.{Offset, PageSize, WireEnumValues, Year}

import com.augustnagro.magnum.*
import com.histmersion.domain.library.Source

/** The sources a person has not marked read, by title, as "Not read yet" lists them. */
final class SourceReadable(db: Transactor) extends Readable:
  val kind: ItemKind = SourceReferable.Kind

  private def notRead(read: Set[ItemRef]): Sql =
    Sql.of(
      " FROM sources i WHERE NOT (i.citekey = ANY(?::text[]) OR i.uid = ANY(?::uuid[]))",
      Param.A(read.toList.map(_.key.value)),
      Param.A(read.toList.flatMap(_.uid).map(_.value.toString))
    )

  def unread(read: Set[ItemRef], size: PageSize, from: Offset): UnreadItems =
    db.read:
      val rows =
        (Sql.raw("SELECT i.citekey, i.title, i.authors, i.year, i.status, lower(i.period), upper(i.period) - 1") ++
          notRead(read) ++
          Sql.of(" ORDER BY lower(i.title), i.citekey LIMIT ? OFFSET ?", Param.I(size.value), Param.I(from.value))).frag
          .query[(String, String, List[String], Option[Int], Option[String], Option[Long], Option[Long])]
          .run()
          .toList
      val total = (Sql.raw("SELECT count(*)") ++ notRead(read)).frag.query[Long].run().head.toInt
      UnreadItems(
        rows.map((ck, title, authors, year, status, start, end) =>
          UnreadItem(
            ItemKey(ck),
            Title(title),
            authors.map(AuthorName(_)),
            year.map(Year(_)),
            status.flatMap(summon[WireEnumValues[Source.Status]].fromWire),
            start.map(y => Year(y.toInt)),
            end.map(y => Year(y.toInt))
          )
        ),
        total
      )
