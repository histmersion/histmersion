package com.histmersion.util.database
import com.augustnagro.magnum.*
import io.circe.syntax.*

/** Magnum DbCodec instances for the domain vocabularies. Each reads/writes the lowercase wire string that Postgres
  * stores. Placed here (db module) because core has no Magnum dependency.
  *
  * ONE rule for a value the vocabulary does not know (#1798 item 11): it reads as the vocabulary's UNKNOWN — the value
  * that claims nothing — never as a confident member. Confidence has no such member, so an unknown confidence reads as
  * NOT STATED (`None`), never as `high`, which is what the store once did. Writes are checked where they enter
  * ([[Write]]: an unknown word is refused).
  */
object Vocab:
  /** A vocabulary column: `parse` the stored wire string, `unknown` for anything it does not know. */

  def codec[E](parse: com.histmersion.WireEnumValues.Wire => Option[E], wire: E => com.histmersion.WireEnumValues.Wire, unknown: E): DbCodec[E] =
    DbCodec[Option[String]].biMap(s => s.flatMap(v => parse(v.trim)).getOrElse(unknown), e => Some(wire(e)))
