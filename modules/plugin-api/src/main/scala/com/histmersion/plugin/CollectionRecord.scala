package com.histmersion.plugin

/** A source of the host as an audit sees it: its key, the item it is linked to, and what the host says of it. */
final case class CollectionRecord(
    key: String,
    id: CollectionId,
    title: String,
    authors: List[String],
    year: Option[Year]
)
