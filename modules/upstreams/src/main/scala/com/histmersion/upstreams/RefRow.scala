package com.histmersion.upstreams

/** One entry of a work's deposited reference list. */
final case class RefRow(
    title: Option[String] = None,
    authors: List[String] = Nil,
    year: Option[Int] = None,
    doi: Option[String] = None,
    raw: Option[String] = None
)
