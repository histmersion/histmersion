package com.histmersion.util.storage

import com.histmersion.{ByteCount, ByteOffset, Sha256}

import java.io.InputStream
import java.nio.ByteBuffer
import java.time.Instant

/** A file in a store, as it is when it is read. Big files stream ([[open]]) or are read by position ([[readAt]],
  * [[reader]]); a grid read many times over is mapped ([[mapped]]).
  */
trait Blob:
  def key: Store.Key

  /** Where the file is, for messages. */
  def where: com.histmersion.util.storage.Blob.WhereResult
  def size: ByteCount
  def modified: Instant
  def open(): InputStream
  def bytes(): com.histmersion.util.storage.Blob.BytesResult
  def text(): com.histmersion.util.storage.Blob.TextResult
  def sha256(): Sha256

  /** `length` bytes from `offset`. */
  def readAt(offset: ByteOffset, length: com.histmersion.util.storage.Blob.ReadAtLength): com.histmersion.util.storage.Blob.ReadAtResult

  /** A reader held open for many positional reads. */
  def reader(): RandomReader

  /** The whole file, read-only, mapped into memory where the store is on disk. */
  def mapped(): ByteBuffer

  /** The file as bytes to read from the start as often as needed. */
  final def asSource: ByteSource =
    val blob = this
    new ByteSource:
      def size: com.histmersion.util.storage.Blob.AsSourceSizeResult = blob.size.value
      def open(): InputStream = blob.open()

object Blob:
  type AsSourceSizeResult = AsSourceSizeResult.T
  object AsSourceSizeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type BytesResult = BytesResult.T
  object BytesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAtResult = ReadAtResult.T
  object ReadAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type WhereResult = WhereResult.T
  object WhereResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResult = TextResult.T
  object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadAtLength = ReadAtLength.T
  object ReadAtLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
