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
final case class CollectionFile(name: String, format: Option[String] = None, megabytes: Option[Double] = None)
