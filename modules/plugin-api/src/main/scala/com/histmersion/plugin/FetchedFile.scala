package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

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
    bytes: FetchedFile.Bytes,
    extension: FetchedFile.Extension,
    licence: Option[Licence] = None
)

object FetchedFile:
  type Bytes = Bytes.T
  object Bytes extends Newtype[Array[Byte], Pure]

  type Source = Source.T
  object Source extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type Extension = Extension.T
  object Extension extends Newtype[String, Pure]
