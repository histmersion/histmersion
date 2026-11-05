package com.histmersion.plugin

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
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Format = Format.T
  object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Megabytes = Megabytes.T
  object Megabytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
