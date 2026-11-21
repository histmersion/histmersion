package com.histmersion
package util.storage

import com.histmersion.{ByteCount, ByteOffset, Sha256}

import java.io.InputStream
import java.nio.ByteBuffer
import java.time.Instant
import com.histmersion.util.basemap.PmTiles
import io.github.iltotore.iron.constraint.any.Pure

/** A file in a store, as it is when it is read. Big files stream ([[open]]) or are read by position ([[readAt]],
  * [[reader]]); a grid read many times over is mapped ([[mapped]]).
  */
trait Blob:
  def key: Store.Key

  /** Where the file is, for messages. */
  def where: Blob.Location
  def size: ByteCount
  def modified: Instant
  def open(): InputStream
  def bytes(): ByteSource.Content
  def text(): Blob.Text
  def sha256(): Sha256

  /** `length` bytes from `offset`. */
  def readAt(offset: ByteOffset, length: PmTiles.ByteLength): ByteSource.Content

  /** A reader held open for many positional reads. */
  def reader(): RandomReader

  /** The whole file, read-only, mapped into memory where the store is on disk. */
  def mapped(): ByteBuffer

  /** The file as bytes to read from the start as often as needed. */
  final def asSource: ByteSource =
    val blob = this
    new ByteSource:
      def size: ByteCount = blob.size
      def open(): InputStream = blob.open()

object Blob:
  type Location = Location.T
  object Location extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]:
    extension (text: Text)
      /** The text without the white space around it (a line a build wrote, its line break dropped). */
      def trimmed: String = text.value.trim
