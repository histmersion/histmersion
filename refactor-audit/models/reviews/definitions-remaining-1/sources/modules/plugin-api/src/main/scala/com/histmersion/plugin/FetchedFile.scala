package com.histmersion.plugin

/** A downloaded copy of a work.
  *
  * @param source
  *   the channel it came from, recorded as the source's provenance (`arxiv`, `pmc`, …)
  * @param url
  *   where it was downloaded from
  * @param bytes
  *   the file
  * @param extension
  *   `pdf`, `epub`, `xml` (a JATS full text), … — the host refuses a `pdf` that is not one
  * @param licence
  *   the licence the copy is published under, when the fetcher knows it — an SPDX id (`CC-BY-4.0`) or `public-domain`,
  *   else the licence's URL; the host records it as the source's licence when that has none
  */
final case class FetchedFile(
    source: FetchedFile.Source,
    url: Option[FetchedFile.Url],
    bytes: Array[FetchedFile.Bytes],
    extension: FetchedFile.Extension,
    licence: Option[Licence] = None
)

object FetchedFile:
  type Source = Source.T
  object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Extension = Extension.T
  object Extension extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
