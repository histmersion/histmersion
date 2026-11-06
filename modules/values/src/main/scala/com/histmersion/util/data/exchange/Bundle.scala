package com.histmersion.util.data.exchange

import com.histmersion.{Period, WireEnumValues}

object Bundle:

  /** The kinds of entity a bundle carries one line per uid of — and what a tombstone says was deleted. Wire names are
    * kebab-case (`article-version`), as the V031 trigger writes them.
    */
  enum Kind:
    case Source, Finding, Article, ArticleVersion, Asset, AssetLink, Attestation, Event, Goal, QueueItem

    /** A place or a named period added HERE (`dataset = 'local'`); the bundled seeds every instance imports itself are
      * not carried (docs/research-merge-import.md §7.8).
      */
    case Place, Period

    def wire: com.histmersion.util.data.exchange.Bundle.Kind.Wire = _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire((Kind.wireOf(this)).value)

    /** The NDJSON file holding this kind's lines. */
    def file: com.histmersion.util.data.exchange.Bundle.Kind.EntryPath = _root_.com.histmersion.util.data.exchange.Bundle.Kind.EntryPath(s"entities/${Kind.fileStem(this)}.ndjson")

    /** The letter a merge issue about this kind of record starts its id with (`P-1a2b3c4d`). */
    def issueLetter: com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter = Kind.letterOf(this)

  object Kind:
    type EntryPath = EntryPath.T
    object EntryPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IssueLetter = IssueLetter.T
    object IssueLetter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def wireOf(k: Kind): com.histmersion.util.data.exchange.Bundle.Kind.Wire = k match
      case Source         => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("source")
      case Finding        => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("finding")
      case Article        => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("article")
      case ArticleVersion => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("article-version")
      case Asset          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("asset")
      case AssetLink      => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("asset-link")
      case Attestation    => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("attestation")
      case Event          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("event")
      case Goal           => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("goal")
      case QueueItem      => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("queue-item")
      case Place          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("place")
      case Period         => _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire("period")

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

    private def letterOf(k: Kind): com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter = k match
      case Source         => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("S")
      case Finding        => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("F")
      case Article        => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("A")
      case ArticleVersion => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("V")
      case Asset          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("X")
      case AssetLink      => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("K")
      case Attestation    => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("T")
      case Event          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("E")
      case Goal           => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("G")
      case QueueItem      => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("Q")
      case Place          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("P")
      case Period         => _root_.com.histmersion.util.data.exchange.Bundle.Kind.IssueLetter("N")

    def fromWire(s: com.histmersion.util.data.exchange.Bundle.Kind.Wire): Option[Kind] = values.find(_.wire == _root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire((s).value.trim.toLowerCase))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): _root_.com.histmersion.WireEnumValues.Wire = _root_.com.histmersion.WireEnumValues.Wire((wireOf(e)).value)
      def fromWire(s: _root_.com.histmersion.WireEnumValues.Wire): _root_.scala.Option[Kind] = Kind.fromWire(_root_.com.histmersion.util.data.exchange.Bundle.Kind.Wire((s).value))
