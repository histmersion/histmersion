package com.histmersion.domain.library.acquisition
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
