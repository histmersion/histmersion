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
  type CodecParseArg1 = CodecParseArg1.T
  object CodecParseArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CodecWireResult = CodecWireResult.T
  object CodecWireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def codec[E](parse: com.histmersion.util.database.Vocab.CodecParseArg1 => Option[E], wire: E => com.histmersion.util.database.Vocab.CodecWireResult, unknown: E): DbCodec[E] =
    DbCodec[Option[String]].biMap(s => s.flatMap(v => parse(v.trim)).getOrElse(unknown), e => Some(wire(e)))
