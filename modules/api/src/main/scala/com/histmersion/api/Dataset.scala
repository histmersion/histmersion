package com.histmersion.api

enum Dataset:
  case Seed, Local
  def wire: com.histmersion.api.Dataset.Wire = _root_.com.histmersion.api.Dataset.Wire(this.toString.toLowerCase(java.util.Locale.ROOT))

object Dataset:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.Dataset.Wire): Option[Dataset] = values.find(_.wire == _root_.com.histmersion.api.Dataset.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  def ofParam(raw: Option[com.histmersion.api.Dataset.Wire]): Either[Api.Error, Dataset] =
    raw.map((_).value.trim).filter(_.nonEmpty) match
      case None    => Right(Local)
      case Some(s) => parse(_root_.com.histmersion.api.Dataset.Wire(s)).toRight(Api.Error(_root_.com.histmersion.api.Api.Error.Message(s"dataset '$s' is not seed or local"), Some(_root_.com.histmersion.api.Api.Error.Code(Write.CodeInvalid))))
