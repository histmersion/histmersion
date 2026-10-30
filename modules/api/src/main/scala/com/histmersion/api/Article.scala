package com.histmersion.api

/** One compiled article. The domain model: `db` stores it, `ArticleNote` renders/parses it. */
final case class Article(
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    period: Option[Period] = None,
    /** Opaque last-write token (`updated_at`); `None` when the value did not come from the store. */
    updatedAt: Option[String] = None,
    /** The filter it is compiled from (#958): its Not used list and Method note are generated from it. */
    filter: Option[ArticleFilter] = None,
    /** Why each source of the filter that the body does not cite is left out, by citekey. */
    reasons: Map[String, String] = Map.empty
)
