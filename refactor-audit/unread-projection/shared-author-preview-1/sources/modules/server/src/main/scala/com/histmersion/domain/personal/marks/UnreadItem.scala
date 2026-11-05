package com.histmersion.domain.personal.marks

import com.histmersion.domain.ItemKey
import com.histmersion.domain.{AuthorName, Title}
import com.histmersion.Year

/** A record a person has not marked read, as "Not read yet" lists it. */
final case class UnreadItem(
    key: ItemKey,
    title: Title,
    authors: List[AuthorName] = Nil,
    year: Option[Year] = None,
    status: Option[UnreadItem.Status] = None,
    periodStart: Option[Year] = None,
    periodEnd: Option[Year] = None
)


object UnreadItem:
    
  type Status = Status.T
  object Status extends io.github.iltotore.iron.RefinedType[String, io.github.iltotore.iron.Pure]
