package com.histmersion.pluginhost

import com.histmersion.plugin.*

import scala.util.control.NonFatal

/** A plugin's [[SourceCollection]] as the host calls it: every call guarded (an exception or a linkage error becomes
  * the operation's `Left`, naming the collection), and every write refused unless the collection declares the
  * capability it needs — whatever the plugin's own member would do.
  */
final class HostCollection(val name: String, collection: SourceCollection):
  def title: String = guardedValue(collection.title, name)
  def description: String = guardedValue(collection.description, "")
  def capabilities: Set[CollectionCapability] = guardedValue(collection.capabilities, Set(CollectionCapability.Read))

  def reach: Either[String, Unit] = guarded(collection.reach)
  def search(query: CollectionQuery): Either[String, List[CollectionItem]] = guarded(collection.search(query))
  def item(id: CollectionId): Either[String, Option[CollectionItem]] = guarded(collection.item(id))
  def admits(item: CollectionItem): Either[String, Unit] = guarded(collection.admits(item))
  def file(item: CollectionItem, format: Option[String]): Either[String, CollectionFile] =
    guarded(collection.file(item, format))
  def original(item: CollectionItem, file: CollectionFile): Either[String, FetchedFile] =
    guarded(collection.original(item, file))
  def cover(item: CollectionItem): Either[String, Option[FetchedFile]] = guarded(collection.cover(item))
  def audit(records: List[CollectionRecord]): Either[String, List[CollectionFinding]] =
    guarded(collection.audit(records))

  def add(metadata: CollectionMetadata, file: FetchedFile): Either[String, CollectionId] =
    writing(CollectionCapability.Add)(collection.add(metadata, file))
  def replace(id: CollectionId, key: String, file: FetchedFile): Either[String, String] =
    writing(CollectionCapability.Add)(collection.replace(id, key, file))
  def update(id: CollectionId, metadata: CollectionMetadata): Either[String, List[CollectionChange]] =
    writing(CollectionCapability.Update)(collection.update(id, metadata))
  def updateAll(links: List[CollectionLink], apply: Boolean): Either[String, List[CollectionUpdateEntry]] =
    if apply then writing(CollectionCapability.Update)(collection.updateAll(links, apply))
    else guarded(collection.updateAll(links, apply))
  def set(id: CollectionId, fields: List[(String, String)]): Either[String, Unit] =
    writing(CollectionCapability.Update)(collection.set(id, fields))

  /** Whether the collection declares `capability`. */
  def can(capability: CollectionCapability): Boolean = capabilities.contains(capability)

  /** The refusal of a write the collection does not declare. */
  def readOnly: String = s"$title is read-only here: ${guardedValue(collection.readOnlyReason, "it is read-only")}"

  private def writing[A](capability: CollectionCapability)(call: => Either[String, A]): Either[String, A] =
    if can(capability) then guarded(call) else Left(readOnly)

  private def guarded[A](call: => Either[String, A]): Either[String, A] =
    try
      call match
        case null => Left(s"source collection '$name' answered nothing")
        case r    => r
    catch
      case e: LinkageError => Left(s"source collection '$name' failed: ${e.getClass.getSimpleName}: ${e.getMessage}")
      case NonFatal(e)     => Left(s"source collection '$name' failed: ${e.getClass.getSimpleName}: ${e.getMessage}")

  private def guardedValue[A](value: => A, otherwise: A): A =
    try Option(value).getOrElse(otherwise)
    catch
      case _: LinkageError => otherwise
      case NonFatal(_)     => otherwise
