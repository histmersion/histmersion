package com.histmersion.domain.library.acquisition
final case class RefRow(
    title: Option[String] = None,
    authors: List[String] = Nil,
    year: Option[Int] = None,
    doi: Option[String] = None,
    raw: Option[String] = None
)
