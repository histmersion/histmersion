package com.histmersion.config

import io.github.iltotore.iron.RefinedType
import hearth.kindlings.sconfigderivation.ConfigReader

/** The settings reader of every newtype, from its base type's — the lenient one for numbers and booleans, so `"30"`
  * from an environment variable still reads as an `Int` — with the constraint checked.
  */
object NewtypeReaders:
  given reader: [T] => (m: RefinedType.Mirror[T]) => (base: ConfigReader[m.BaseType]) => ConfigReader[T] =
    base.emap(m.ops.either).asInstanceOf[ConfigReader[T]]
