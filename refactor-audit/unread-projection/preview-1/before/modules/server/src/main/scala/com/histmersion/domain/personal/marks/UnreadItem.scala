package com.histmersion.domain.personal.marks

import com.histmersion.domain.ItemKey
import com.histmersion.domain.library.AuthorName
import com.histmersion.domain.Title
import com.histmersion.Year
import com.histmersion.domain.library.Source

/** A record a person has not marked read, as "Not read yet" lists it. */
final case class UnreadItem(
    key: ItemKey,
    title: Title,
    authors: List[AuthorName] = Nil,
    year: Option[Year] = None,
    status: Option[Source.Status] = None,
    periodStart: Option[Year] = None,
    periodEnd: Option[Year] = None
)
