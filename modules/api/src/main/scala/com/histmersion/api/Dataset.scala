package com.histmersion.api

/** Whose a place or a named period is (V052): `seed` — imported from the reference data every instance seeds itself
  * with (`histmersion init`, `places seed`, `places fetch`, `periods seed`, `periods fetch`), never carried in a
  * bundle; `local` — this collection's own (a file a person imported), carried in a bundle and merged like any other
  * record.
  */
enum Dataset:
  case Seed, Local
  def wire: String = this.toString.toLowerCase(java.util.Locale.ROOT)

object Dataset:
  def parse(s: String): Option[Dataset] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** A request's `dataset` parameter: absent is `local` (a file a person sends is theirs). */
  def ofParam(raw: Option[String]): Either[ApiError, Dataset] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(Local)
      case Some(s) => parse(s).toRight(ApiError(s"dataset '$s' is not seed or local", Some(Write.CodeInvalid)))
