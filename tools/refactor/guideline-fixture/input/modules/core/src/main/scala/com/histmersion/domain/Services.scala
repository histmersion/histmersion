package com.histmersion.domain // assert: GuidelineInventory.G47package

import com.histmersion.db.SourceRows

final case class Source(citekey: Citekey, title: Title, status: Status)
final case class SourceStatus(value: Int)
final case class SourceRepo(rows: List[Source])

object Sources:
  def describe(s: Source): String = s"${s.citekey.value}: ${s.status}" // assert: GuidelineInventory
  def byKey(xs: List[Source], key: Citekey): Option[Source] = xs.find(_.citekey.value == key.value) // assert: GuidelineInventory
  def has(xs: List[Citekey]): Boolean = xs.contains("abc") // assert: GuidelineInventory.silentCompare
  def same(a: Citekey, b: Slug): Boolean = (a: Any) == b // assert: GuidelineInventory.G3misplaced
  def sameSeq(a: List[Citekey], b: Slug): Boolean = a.contains(b) // assert: GuidelineInventory.silentCompare
  def titled(t: Title): Boolean = List("x").contains(t) // assert: GuidelineInventory.G3misplaced
  def wireOf(s: Status): String = s"status=$s" // assert: GuidelineInventory
  def active: String = s"${Status.Active}" // assert: GuidelineInventory.silentInterp
  def label(n: Int): String = n.toString
  def plain(c: Citekey): String = s"${c.normalised}" // assert: GuidelineInventory.G3misplaced
  def check(raw: String): Option[Citekey] = Citekey.option(raw) // assert: GuidelineInventory
  def slug(raw: String): Either[String, Slug] = Slug.either(raw) // assert: GuidelineInventory.G44
  def keys(raws: List[String]): List[Citekey] = raws.flatMap(Citekey.option) // assert: GuidelineInventory
  def fixed: Citekey = Citekey("fixed") // assert: GuidelineInventory.G44
  def bridge(s: Slug): Citekey = Citekey(s.value) // assert: GuidelineInventory
  def loud(c: Citekey): String = Text.shout(c.value, 2) + Text.whisper("x") // assert: GuidelineInventory
  def bound(c: Citekey): String = SourceRows.bind(c.value) // assert: GuidelineInventory
  def same2(c: com.histmersion.domain.Citekey): Int = 1 // assert: GuidelineInventory

final class Ranking(keys: List[Citekey]):
  def sorted: List[Citekey] = keys.sorted
  def first(c: Citekey): Boolean = keys.headOption.contains(c)

object Names:
  def names(xs: List[Citekey]): List[String] = xs.map(_.value) // assert: GuidelineInventory

object Other:
  final case class Created(n: Int)
final case class CreatedAt(n: Int)
