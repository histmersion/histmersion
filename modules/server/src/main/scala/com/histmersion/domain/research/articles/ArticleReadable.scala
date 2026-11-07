package com.histmersion.domain.research.articles

import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.{ItemKey, ItemKind, ItemRef}
import com.histmersion.domain.personal.marks.{Readable, UnreadItem, UnreadItems}
import com.histmersion.util.database.Transactor
import com.histmersion.{Offset, PageSize, Year}
import com.histmersion.domain.Title

import com.augustnagro.magnum.*

/** The articles a person has not marked read, by title, as "Not read yet" lists them. */
final class ArticleReadable(db: Transactor) extends Readable:
  val kind: ItemKind = ArticleReferable.Kind

  private def notRead(read: Set[ItemRef]): Sql =
    Sql.of(
      Sql.Text(" FROM articles i WHERE NOT (i.slug = ANY(?::text[]) OR i.uid = ANY(?::uuid[]))"),
      Param.A(read.toList.map(r => Param.A.V(r.key.value))),
      Param.A(read.toList.flatMap(_.uid).map(u => Param.A.V(u.value.toString)))
    )

  def unread(read: Set[ItemRef], size: PageSize, from: Offset): UnreadItems =
    db.read:
      val rows = (Sql.raw(com.histmersion.util.database.Sql.Text("SELECT i.slug, i.title, lower(i.period), upper(i.period) - 1")) ++ notRead(read) ++
        Sql.of(com.histmersion.util.database.Sql.Text(" ORDER BY lower(i.title), i.slug LIMIT ? OFFSET ?"), Param.I(Param.I.V(size.value)), Param.I(Param.I.V(from.value)))).frag
        .query[(String, String, Option[Long], Option[Long])]
        .run()
        .toList
      val total = (Sql.raw(com.histmersion.util.database.Sql.Text("SELECT count(*)")) ++ notRead(read)).frag.query[Long].run().head.toInt
      UnreadItems(
        rows.map((slug, title, start, end) =>
          UnreadItem(
            ItemKey(slug),
            Title(title),
            periodStart = start.map(y => Year(y.toInt)),
            periodEnd = end.map(y => Year(y.toInt))
          )
        ),
        UnreadItems.Total(total)
      )
