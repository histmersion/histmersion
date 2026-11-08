package com.histmersion
package plugin

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
  object Name extends Newtype[String, Pure]
  type Format = Format.T
  object Format extends Newtype[String, Pure]
  type Megabytes = Megabytes.T
  object Megabytes extends Newtype[Double, Pure]
