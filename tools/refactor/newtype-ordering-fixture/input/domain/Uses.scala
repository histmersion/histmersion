package com.histmersion
package domain

final case class Entry(key: Citekey, rank: Rank, weight: Weight)

object Uses:
  def keys(ks: List[Citekey]): List[Citekey] = ks.sortBy(_.value)
  def named(ks: Vector[Citekey]): Vector[Citekey] = ks.sortBy(k => k.value)
  def first(ks: List[Citekey]): Citekey = ks.minBy(_.value)
  def last(ks: List[Citekey]): Citekey = ks.maxBy(k => k.value)
  def descending(ks: List[Citekey]): List[Citekey] = ks.sortBy(_.value)(using Ordering[String].reverse)
  def descendingNamed(ks: List[Citekey]): List[Citekey] = ks.sortBy(_.value)(using Ordering.String.reverse)
  def reversed(ks: List[Citekey]): List[Citekey] = ks.sortBy(_.value).reverse
  def before(a: Citekey, b: Citekey): Boolean = a.value < b.value
  def notAfter(a: Rank, b: Rank): Boolean = a.value <= b.value
  def same(a: Citekey, b: Citekey): Boolean = a.value == b.value
  def differ(e: Entry, f: Entry): Boolean = e.key.value != f.key.value
  def ranked(es: List[Entry], top: Rank): List[Entry] = es.filter(_.rank.value <= top.value)
  def local(ks: List[Citekey]): Boolean =
    val k = ks.head
    val l = ks.last
    k.value == l.value

  def wrapped(a: Rank, b: Rank): Boolean = (a).value < (b).value
  // left alone
  def lowered(ks: List[Citekey]): List[Citekey] = ks.sortBy(_.value.toLowerCase)
  def legacy(ls: List[Legacy]): List[Legacy] = ls.sortBy(_.value)
  def heavier(a: Weight, b: Weight): Boolean = a.value > b.value
  def sameWeight(a: Weight, b: Weight): Boolean = a.value == b.value
  def mixed(a: Citekey, b: Label): Boolean = a.value == b.value
  def byWeight(es: List[Entry]): List[Entry] = es.sortBy(e => e.weight.value: Double)
  def raw(xs: List[String]): List[String] = xs.sortBy(identity)
  def chained(es: List[Entry]): List[Citekey] = es.map(_.key).distinct.sortBy(_.value)
  def otherOrder(ks: List[Citekey]): List[Citekey] = ks.sortBy(_.value)(using Ordering[String])
  def firstOption(ks: List[Citekey]): Option[Citekey] = ks.minByOption(_.value)
  def checked(a: Citekey, b: Citekey): Unit = assert(a.value == b.value)
