package com.histmersion
package util.data.exchange

import io.github.iltotore.iron.constraint.any.Pure

/** A line-based three-way merge (diff3; Khanna, Kunal & Pierce 2007), for article bodies that changed on both sides
  * since a common base (docs/research-merge-import.md §5.4). Pure.
  *
  * Both sides are matched against the base by a longest common subsequence of lines. A base line matched on BOTH sides
  * is stable; between stable lines lie the chunks where something changed: a chunk changed on one side only takes that
  * side, a chunk changed the same way on both takes it once, and a chunk changed differently on each side is a
  * conflict. A merge with no conflict is clean and applied automatically; one with conflicts is a decision for the
  * user.
  */
object Diff3:

  type MergeBase = MergeBase.T
  object MergeBase extends Newtype[String, Pure]
  type MergeLeft = MergeLeft.T
  object MergeLeft extends Newtype[String, Pure]
  type MergeRight = MergeRight.T
  object MergeRight extends Newtype[String, Pure]

  /** One region of the merge: agreed text, or a conflict between the two sides over a base. */
  enum Hunk derives CanEqual:
    case Agreed(lines: Vector[Hunk.Agreed.Lines])
    case Conflict(base: Vector[Hunk.Conflict.Base], left: Vector[Hunk.Conflict.Left], right: Vector[Hunk.Conflict.Right])

  object Hunk:
    object Agreed:
      type Lines = Lines.T
      object Lines extends Newtype[String, Pure]
    object Conflict:
      type Base = Base.T
      object Base extends Newtype[String, Pure]
      type Left = Left.T
      object Left extends Newtype[String, Pure]
      type Right = Right.T
      object Right extends Newtype[String, Pure]

  final case class Result(hunks: List[Hunk]):
    def conflicts: Result.ConflictCount = Result.ConflictCount(hunks.count(_.isInstanceOf[Hunk.Conflict]))
    def clean: Boolean = conflicts.value == 0

    /** The merged text — only meaningful when [[clean]]. */
    def text: Result.MergedText = Result.MergedText(render(_ => Vector.empty))

    /** The merged text with git-style conflict markers around every conflict. */
    def marked(leftName: Result.MarkedLeftName, rightName: Result.MarkedRightName): Result.MarkedText =
      Result.MarkedText(render { case Hunk.Conflict(b, l, r) =>
        (s"<<<<<<< $leftName" +: l.map(_.value)) ++ ("||||||| base" +: b.map(_.value)) ++ ("=======" +: r.map(_.value)) :+ s">>>>>>> $rightName"
      })

    private def render(conflict: Hunk.Conflict => Vector[String]): String =
      hunks
        .flatMap {
          case Hunk.Agreed(ls)  => ls.map(_.value)
          case c: Hunk.Conflict => conflict(c)
        }
        .mkString("\n")

  object Result:
    type ConflictCount = ConflictCount.T
    object ConflictCount extends Newtype[Int, Pure]
    type MergedText = MergedText.T
    object MergedText extends Newtype[String, Pure]
    type MarkedText = MarkedText.T
    object MarkedText extends Newtype[String, Pure]
    type MarkedLeftName = MarkedLeftName.T
    object MarkedLeftName extends Newtype[String, Pure]
    type MarkedRightName = MarkedRightName.T
    object MarkedRightName extends Newtype[String, Pure]

  /** Lines of a text; a final newline is not an extra empty line. */
  private def lines(s: String): Vector[String] = s.split("\n", -1).toVector

  private def agreedOf(ls: Vector[String]): Hunk = Hunk.Agreed(ls.map(Hunk.Agreed.Lines(_)))

  def merge(base: MergeBase, left: MergeLeft, right: MergeRight): Result =
    if left.value == right.value then Result(List(agreedOf(lines(left.value))))
    else if left.value == base.value then Result(List(agreedOf(lines(right.value))))
    else if right.value == base.value then Result(List(agreedOf(lines(left.value))))
    else mergeLines(lines(base.value), lines(left.value), lines(right.value))

  private def mergeLines(o: Vector[String], a: Vector[String], b: Vector[String]): Result =
    val ma = matching(o, a)
    val mb = matching(o, b)
    val out = List.newBuilder[Hunk]
    val agreed = Vector.newBuilder[String]
    def flushAgreed(): Unit =
      val ls = agreed.result()
      agreed.clear()
      if ls.nonEmpty then out += agreedOf(ls)
    def chunk(co: Vector[String], ca: Vector[String], cb: Vector[String]): Unit =
      if ca == co then agreed ++= cb
      else if cb == co then agreed ++= ca
      else if ca == cb then agreed ++= ca
      else
        flushAgreed()
        out += Hunk.Conflict(co.map(Hunk.Conflict.Base(_)), ca.map(Hunk.Conflict.Left(_)), cb.map(Hunk.Conflict.Right(_)))
    // positions in base, left, right; each round consumes at least one line of one of them
    @annotation.tailrec
    def walk(io: Int, ia: Int, ib: Int): Unit =
      // the next base line matched on both sides
      val next = (io until o.length).find(j => ma(j) >= 0 && mb(j) >= 0)
      next match
        case None =>
          if io < o.length || ia < a.length || ib < b.length then chunk(o.drop(io), a.drop(ia), b.drop(ib))
        case Some(j) =>
          val (ja, jb) = (ma(j), mb(j))
          if j == io && ja == ia && jb == ib then
            agreed += o(j)
            walk(io + 1, ia + 1, ib + 1)
          else
            chunk(o.slice(io, j), a.slice(ia, ja), b.slice(ib, jb))
            walk(j, ja, jb)
    walk(0, 0, 0)
    flushAgreed()
    Result(out.result())

  /** For each line of `o`, the index of the line of `x` it is matched to by a longest common subsequence, or −1. The
    * common prefix and suffix are matched directly, so only the middle (where the edits are) costs the quadratic table.
    */
  private def matching(o: Vector[String], x: Vector[String]): Array[Int] =
    val m = Array.fill(o.length)(-1)
    val pre = o.iterator.zip(x.iterator).takeWhile((p, q) => p == q).length
    (0 until pre).foreach(i => m(i) = i)
    val maxSuf = math.min(o.length, x.length) - pre
    val suf = (1 to maxSuf).takeWhile(k => o(o.length - k) == x(x.length - k)).length
    (1 to suf).foreach(k => m(o.length - k) = x.length - k)
    val (o0, o1, x0, x1) = (pre, o.length - suf, pre, x.length - suf)
    val (n, k) = (o1 - o0, x1 - x0)
    if n > 0 && k > 0 then
      // lcs(i)(j): the LCS length of o[o0+i..o1) and x[x0+j..x1), filled from the end
      val lcs = Array.ofDim[Int](n + 1, k + 1)
      var i = n - 1 // index loops over the table: whiles keep the hot path allocation-free
      while i >= 0 do
        var j = k - 1
        while j >= 0 do
          lcs(i)(j) =
            if o(o0 + i) == x(x0 + j) then lcs(i + 1)(j + 1) + 1
            else math.max(lcs(i + 1)(j), lcs(i)(j + 1))
          j -= 1
        i -= 1
      @annotation.tailrec
      def trace(i: Int, j: Int): Unit =
        if i < n && j < k then
          if o(o0 + i) == x(x0 + j) then
            m(o0 + i) = x0 + j
            trace(i + 1, j + 1)
          else if lcs(i + 1)(j) >= lcs(i)(j + 1) then trace(i + 1, j)
          else trace(i, j + 1)
      trace(0, 0)
    m
