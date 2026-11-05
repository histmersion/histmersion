package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type CommaList = CommaList.T
object CommaList extends RefinedType[String, Pure]:
  type EntriesResult = EntriesResult.T
  object EntriesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given ConfigReader[CommaList] = summon[ConfigReader[String]].emap(CommaList.either)

  extension (list: CommaList)
    def entries: List[com.histmersion.config.CommaList.EntriesResult] =
      list.value.split(",").toList.map(_.trim.toLowerCase).filter(_.nonEmpty)
