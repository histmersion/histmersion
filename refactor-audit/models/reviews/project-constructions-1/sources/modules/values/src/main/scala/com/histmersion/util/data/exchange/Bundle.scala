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

    def wire: com.histmersion.util.data.exchange.Bundle.Kind.WireResult = Kind.wireOf(this)

    /** The NDJSON file holding this kind's lines. */
    def file: com.histmersion.util.data.exchange.Bundle.Kind.FileResult = _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileResult(s"entities/${Kind.fileStem(this)}.ndjson")

    /** The letter a merge issue about this kind of record starts its id with (`P-1a2b3c4d`). */
    def issueLetter: com.histmersion.util.data.exchange.Bundle.Kind.IssueLetterResult = Kind.letterOf(this)

  object Kind:
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FileResult = FileResult.T
    object FileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IssueLetterResult = IssueLetterResult.T
    object IssueLetterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireOfResult = WireOfResult.T
    object WireOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FileStemResult = FileStemResult.T
    object FileStemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LetterOfResult = LetterOfResult.T
    object LetterOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def wireOf(k: Kind): com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult = k match
      case Source         => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("source")
      case Finding        => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("finding")
      case Article        => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("article")
      case ArticleVersion => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("article-version")
      case Asset          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("asset")
      case AssetLink      => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("asset-link")
      case Attestation    => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("attestation")
      case Event          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("event")
      case Goal           => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("goal")
      case QueueItem      => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("queue-item")
      case Place          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("place")
      case Period         => _root_.com.histmersion.util.data.exchange.Bundle.Kind.WireOfResult("period")

    private def fileStem(k: Kind): com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult = k match
      case Source         => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("sources")
      case Finding        => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("findings")
      case Article        => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("articles")
      case ArticleVersion => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("article-versions")
      case Asset          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("assets")
      case AssetLink      => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("asset-links")
      case Attestation    => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("attestations")
      case Event          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("events")
      case Goal           => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("goals")
      case QueueItem      => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("queue")
      case Place          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("places")
      case Period         => _root_.com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult("periods")

    private def letterOf(k: Kind): com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult = k match
      case Source         => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("S")
      case Finding        => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("F")
      case Article        => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("A")
      case ArticleVersion => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("V")
      case Asset          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("X")
      case AssetLink      => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("K")
      case Attestation    => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("T")
      case Event          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("E")
      case Goal           => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("G")
      case QueueItem      => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("Q")
      case Place          => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("P")
      case Period         => _root_.com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult("N")

    def fromWire(s: com.histmersion.util.data.exchange.Bundle.Kind.FromWireS): Option[Kind] = values.find(_.wire == s.trim.toLowerCase)

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): com.histmersion.util.data.exchange.Bundle.Kind.WireResult = wireOf(e)
      def fromWire(s: com.histmersion.util.data.exchange.Bundle.Kind.FromWireS): Option[Kind] = Kind.fromWire(s)
