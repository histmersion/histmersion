package com.histmersion.upstreams

/** One work that cites another, as Semantic Scholar or OpenAlex lists it, with the citation's intents and contexts
  * where the index gives them.
  */
final case class FollowupRow(
    title: Option[String] = None,
    year: Option[Int] = None,
    doi: Option[String] = None,
    arxiv: Option[String] = None,
    citedBy: Option[Int] = None,
    isInfluential: Boolean = false,
    intents: List[String] = Nil,
    contexts: List[String] = Nil
)
