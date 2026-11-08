package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One file of a collection's item.
  *
  * @param name
  *   what the collection calls it: an absolute path in a local library, a file name in a remote one
  * @param format
  *   the format the collection states (`Text PDF`, `EPUB`), when it states one
  * @param megabytes
  *   its size, when the collection says
  */
final case class CollectionFile(name: CollectionFile.Name, format: Option[CollectionFile.Format] = None, megabytes: Option[CollectionFile.Megabytes] = None)

object CollectionFile:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Format = Format.T
  object Format extends RefinedType[String, Pure]
  type Megabytes = Megabytes.T
  object Megabytes extends RefinedType[Double, Pure]
