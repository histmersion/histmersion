package com.histmersion.api

enum Dataset:
  case Seed, Local
  def wire: com.histmersion.api.Dataset.WireResult = _root_.com.histmersion.api.Dataset.WireResult(this.toString.toLowerCase(java.util.Locale.ROOT))

object Dataset:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfParamRaw = OfParamRaw.T
  object OfParamRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.Dataset.ParseS): Option[Dataset] = values.find(_.wire == _root_.com.histmersion.api.Dataset.WireResult((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  def ofParam(raw: Option[com.histmersion.api.Dataset.OfParamRaw]): Either[Api.Error, Dataset] =
    raw.map((_).value.trim).filter(_.nonEmpty) match
      case None    => Right(Local)
      case Some(s) => parse(_root_.com.histmersion.api.Dataset.ParseS(s)).toRight(Api.Error(_root_.com.histmersion.api.Api.Error.Message(s"dataset '$s' is not seed or local"), Some(_root_.com.histmersion.api.Api.Error.Code(Write.CodeInvalid))))
