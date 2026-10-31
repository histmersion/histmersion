package com.histmersion.util.identity

/** A kind of record whose identifier a rename changes and whose old identifier then stays an alias: what the record and
  * its identifier are called, for the surfaces that say an old one was followed.
  */
final case class AliasKind(noun: RecordNoun, idWord: KeyWord)
