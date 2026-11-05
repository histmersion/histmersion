package com.histmersion.util.text

/** A MinHash signature (Broder 1997): the minimum of each of `k` hash functions over a text's word shingles. */
final case class MinHash(mins: Vector[MinHash.Mins]):

  /** The estimated Jaccard similarity of the two shingle sets. */
  def similarity(o: MinHash): com.histmersion.util.text.MinHash.SimilarityResult =
    if mins.isEmpty || mins.length != o.mins.length then 0.0
    else mins.zip(o.mins).count((a, b) => a == b).toDouble / mins.length

object MinHash:

  /** Signature of a text: folded word 5-shingles, `k` seeded MurmurHash3 functions. A text shorter than one shingle is
    * hashed as one. Pure and deterministic.
    */
  type OfShingles = OfShingles.T
  object OfShingles extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SimilarityResult = SimilarityResult.T
  object SimilarityResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfText = OfText.T
  object OfText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfK = OfK.T
  object OfK extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfShingle = OfShingle.T
  object OfShingle extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Mins = Mins.T
  object Mins extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(text: com.histmersion.util.text.MinHash.OfText, k: com.histmersion.util.text.MinHash.OfK = 64, shingle: com.histmersion.util.text.MinHash.OfShingle = 5): MinHash =
    val ws = Fingerprint.tokens(text)
    val shingles: Iterator[com.histmersion.util.text.MinHash.OfShingles] =
      if ws.length <= shingle then Iterator.single(ws.mkString(" ")) else ws.sliding(shingle).map(_.mkString(" "))
    val mins = Array.fill(k)(Int.MaxValue)
    shingles.foreach { sh =>
      val h = scala.util.hashing.MurmurHash3.stringHash(sh)
      var i = 0 // a hot loop over the k functions; a while keeps it allocation-free
      while i < k do
        val v = scala.util.hashing.MurmurHash3.mix(h, i * 0x9e3779b9)
        if v < mins(i) then mins(i) = v
        i += 1
    }
    MinHash(mins.toVector)
