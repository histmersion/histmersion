package com.histmersion
package domain.research.articles

import com.histmersion.domain.{ItemKey, ItemKind, ItemRef}
import com.histmersion.domain.personal.marks.{Readable, UnreadItem, UnreadItems}
import com.histmersion.util.database.Transactor
import com.histmersion.{Offset, PageSize, Year}
import com.histmersion.domain.Title

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

/** The articles a person has not marked read, by title, as "Not read yet" lists them. */
final class ArticleReadable(db: Transactor) extends Readable:
  val kind: ItemKind = ArticleReferable.Kind

  private def notRead(read: Set[ItemRef]): Frag =
    sql" FROM articles i WHERE NOT (i.slug = ANY(${read.toList.map(_.key)}::text[]) OR i.uid = ANY(${read.toList.flatMap(_.uid).map(u => u.value.toString)}::uuid[]))"

  def unread(read: Set[ItemRef], size: PageSize, from: Offset): UnreadItems =
    db.read:
      val rows =
        sql"SELECT i.slug, i.title, lower(i.period), upper(i.period) - 1${notRead(read)} ORDER BY lower(i.title), i.slug LIMIT $size OFFSET $from"
          .query[(String, String, Option[Long], Option[Long])]
          .run()
          .toList
      val total = sql"SELECT count(*)${notRead(read)}".query[Long].run().head.toInt
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
