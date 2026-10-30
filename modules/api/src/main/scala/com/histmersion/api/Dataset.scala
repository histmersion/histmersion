package com.histmersion.api

enum Dataset:
  case Seed, Local
  def wire: String = this.toString.toLowerCase(java.util.Locale.ROOT)

object Dataset:
  def parse(s: String): Option[Dataset] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  def ofParam(raw: Option[String]): Either[ApiError, Dataset] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(Local)
      case Some(s) => parse(s).toRight(ApiError(s"dataset '$s' is not seed or local", Some(Write.CodeInvalid)))
