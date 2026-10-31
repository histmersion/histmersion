package com.histmersion.util.storage

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A name inside a store: `/`-separated segments, relative, never `.` or `..`, no empty segment, no backslash or NUL.
  * The empty key is the store itself.
  */
type StoreKeyRule = Match["|(?!\\.{1,2}(/|$))[^/\\\\\\x00]+(/(?!\\.{1,2}(/|$))[^/\\\\\\x00]+)*"]

type StoreKey = StoreKey.T

object StoreKey extends RefinedType[String, StoreKeyRule]:
  val Root: StoreKey = applyUnsafe("")

  /** A name made of one segment or several, each checked. */
  def of(segments: String*): Either[String, StoreKey] = either(segments.filter(_.nonEmpty).mkString("/"))

  extension (key: StoreKey)
    def isRoot: Boolean = key.value.isEmpty

    /** `key/child`; `child` may itself hold several segments. */
    def /(child: String): Either[String, StoreKey] = of(key.value, child)

    /** `key/child` for a child the caller built from checked parts. */
    def child(name: String): StoreKey = (key / name).fold(e => throw IllegalArgumentException(e), identity)

    def segments: List[String] = if key.isRoot then Nil else key.value.split('/').toList

    /** The last segment. */
    def name: String = segments.lastOption.getOrElse("")

    def parent: StoreKey = applyUnsafe(segments.dropRight(1).mkString("/"))

    /** The key `name` with `suffix` added to its last segment. */
    def withSuffix(suffix: String): StoreKey = parent.child(name + suffix)

    /** Is `key` this key or below it? */
    def within(prefix: StoreKey): Boolean =
      prefix.isRoot || key.value == prefix.value || key.value.startsWith(prefix.value + "/")

    /** This key relative to `prefix` (which it must be within). */
    def relativeTo(prefix: StoreKey): StoreKey =
      if prefix.isRoot then key else applyUnsafe(key.value.drop(prefix.value.length).stripPrefix("/"))
