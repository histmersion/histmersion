package com.histmersion
package util.data.exchange

import com.histmersion.{Period, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure

object Bundle:
  type RangeIsNull = RangeIsNull.T
  object RangeIsNull extends Newtype[Boolean, Pure]
  type RangeLower = RangeLower.T
  object RangeLower extends Newtype[Long, Pure]
  type RangeUpper = RangeUpper.T
  object RangeUpper extends Newtype[Long, Pure]

  /** The kinds of entity a bundle carries one line per uid of — and what a tombstone says was deleted. Wire names are
    * kebab-case (`article-version`), as the V031 trigger writes them.
    */
  enum Kind derives CanEqual:
    case Source, Finding, Article, ArticleVersion, Asset, AssetLink, Attestation, Event, Goal, QueueItem

    /** A place or a named period added HERE (`dataset = 'local'`); the bundled seeds every instance imports itself are
      * not carried (docs/research-merge-import.md §7.8).
      */
    case Place, Period

    def wire: Kind.Wire = Kind.Wire((Kind.wireOf(this)).value)

    /** The NDJSON file holding this kind's lines. */
    def file: Kind.EntryPath = Kind.EntryPath(s"entities/${Kind.fileStem(this)}.ndjson")

    /** The letter a merge issue about this kind of record starts its id with (`P-1a2b3c4d`). */
    def issueLetter: Kind.IssueLetter = Kind.letterOf(this)

  object Kind:
    type EntryPath = EntryPath.T
    object EntryPath extends Newtype[String, Pure]
    type IssueLetter = IssueLetter.T
    object IssueLetter extends Newtype[String, Pure]
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def wireOf(k: Kind): Wire = k match
      case Source         => Wire("source")
      case Finding        => Wire("finding")
      case Article        => Wire("article")
      case ArticleVersion => Wire("article-version")
      case Asset          => Wire("asset")
      case AssetLink      => Wire("asset-link")
      case Attestation    => Wire("attestation")
      case Event          => Wire("event")
      case Goal           => Wire("goal")
      case QueueItem      => Wire("queue-item")
      case Place          => Wire("place")
      case Period         => Wire("period")

    private def fileStem(k: Kind): String = k match
      case Source         => ("sources")
      case Finding        => ("findings")
      case Article        => ("articles")
      case ArticleVersion => ("article-versions")
      case Asset          => ("assets")
      case AssetLink      => ("asset-links")
      case Attestation    => ("attestations")
      case Event          => ("events")
      case Goal           => ("goals")
      case QueueItem      => ("queue")
      case Place          => ("places")
      case Period         => ("periods")

    private def letterOf(k: Kind): IssueLetter = k match
      case Source         => IssueLetter("S")
      case Finding        => IssueLetter("F")
      case Article        => IssueLetter("A")
      case ArticleVersion => IssueLetter("V")
      case Asset          => IssueLetter("X")
      case AssetLink      => IssueLetter("K")
      case Attestation    => IssueLetter("T")
      case Event          => IssueLetter("E")
      case Goal           => IssueLetter("G")
      case QueueItem      => IssueLetter("Q")
      case Place          => IssueLetter("P")
      case Period         => IssueLetter("N")

    def fromWire(s: Wire): Option[Kind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = Kind.fromWire(Wire((s).value))
