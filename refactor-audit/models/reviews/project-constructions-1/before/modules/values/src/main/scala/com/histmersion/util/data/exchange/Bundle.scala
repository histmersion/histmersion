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
    def file: com.histmersion.util.data.exchange.Bundle.Kind.FileResult = s"entities/${Kind.fileStem(this)}.ndjson"

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
      case Source         => "source"
      case Finding        => "finding"
      case Article        => "article"
      case ArticleVersion => "article-version"
      case Asset          => "asset"
      case AssetLink      => "asset-link"
      case Attestation    => "attestation"
      case Event          => "event"
      case Goal           => "goal"
      case QueueItem      => "queue-item"
      case Place          => "place"
      case Period         => "period"

    private def fileStem(k: Kind): com.histmersion.util.data.exchange.Bundle.Kind.FileStemResult = k match
      case Source         => "sources"
      case Finding        => "findings"
      case Article        => "articles"
      case ArticleVersion => "article-versions"
      case Asset          => "assets"
      case AssetLink      => "asset-links"
      case Attestation    => "attestations"
      case Event          => "events"
      case Goal           => "goals"
      case QueueItem      => "queue"
      case Place          => "places"
      case Period         => "periods"

    private def letterOf(k: Kind): com.histmersion.util.data.exchange.Bundle.Kind.LetterOfResult = k match
      case Source         => "S"
      case Finding        => "F"
      case Article        => "A"
      case ArticleVersion => "V"
      case Asset          => "X"
      case AssetLink      => "K"
      case Attestation    => "T"
      case Event          => "E"
      case Goal           => "G"
      case QueueItem      => "Q"
      case Place          => "P"
      case Period         => "N"

    def fromWire(s: com.histmersion.util.data.exchange.Bundle.Kind.FromWireS): Option[Kind] = values.find(_.wire == s.trim.toLowerCase)

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): com.histmersion.util.data.exchange.Bundle.Kind.WireResult = wireOf(e)
      def fromWire(s: com.histmersion.util.data.exchange.Bundle.Kind.FromWireS): Option[Kind] = Kind.fromWire(s)
