package com.histmersion.util.storage

import com.histmersion.values.{ByteCount, ByteOffset, Sha256}

import java.io.InputStream
import java.nio.ByteBuffer
import java.time.Instant

/** A file in a store, as it is when it is read. Big files stream ([[open]]) or are read by position ([[readAt]],
  * [[reader]]); a grid read many times over is mapped ([[mapped]]).
  */
trait Blob:
  def key: StoreKey

  /** Where the file is, for messages. */
  def where: String
  def size: ByteCount
  def modified: Instant
  def open(): InputStream
  def bytes(): Array[Byte]
  def text(): String
  def sha256(): Sha256

  /** `length` bytes from `offset`. */
  def readAt(offset: ByteOffset, length: Int): Array[Byte]

  /** A reader held open for many positional reads. */
  def reader(): RandomReader

  /** The whole file, read-only, mapped into memory where the store is on disk. */
  def mapped(): ByteBuffer
