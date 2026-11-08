package com.histmersion
package util.text

import io.github.iltotore.iron.constraint.any.Pure

/** A MinHash signature (Broder 1997): the minimum of each of `k` hash functions over a text's word shingles. */
final case class MinHash(mins: Vector[MinHash.Mins]):

  /** The estimated Jaccard similarity of the two shingle sets. */
  def similarity(o: MinHash): MinHash.Similarity =
    if mins.isEmpty || mins.length != o.mins.length then MinHash.Similarity(0.0)
    else MinHash.Similarity(mins.zip(o.mins).count((a, b) => a == b).toDouble / mins.length)

object MinHash:

  type Similarity = Similarity.T
  object Similarity extends Newtype[Double, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Size = Size.T
  object Size extends Newtype[Int, Pure]
  type ShingleLength = ShingleLength.T
  object ShingleLength extends Newtype[Int, Pure]

  type Mins = Mins.T
  object Mins extends Newtype[Int, Pure]

  /** Signature of a text: folded word 5-shingles, `k` seeded MurmurHash3 functions. A text shorter than one shingle is
    * hashed as one. Pure and deterministic.
    */
  def of(text: Text, k: Size = Size(64), shingle: ShingleLength = ShingleLength(5)): MinHash =
    val ws = Fingerprint.tokens(Fingerprint.TokensS(text.value)).map(_.value)
    val shingles: Iterator[String] =
      if ws.length <= shingle.value then Iterator.single(ws.mkString(" "))
      else ws.sliding(shingle.value).map(_.mkString(" "))
    val mins = Array.fill(k.value)(Int.MaxValue)
    shingles.foreach { sh =>
      val h = scala.util.hashing.MurmurHash3.stringHash(sh)
      var i = 0 // a hot loop over the k functions; a while keeps it allocation-free
      while i < k.value do
        val v = scala.util.hashing.MurmurHash3.mix(h, i * 0x9e3779b9)
        if v < mins(i) then mins(i) = v
        i += 1
    }
    MinHash(mins.toVector.map(Mins(_)))
