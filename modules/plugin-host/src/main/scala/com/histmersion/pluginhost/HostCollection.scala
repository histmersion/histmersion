package com.histmersion.pluginhost

import com.histmersion.plugin.*

import scala.util.control.NonFatal

/** A plugin's [[SourceCollection]] as the host calls it: every call guarded (an exception or a linkage error becomes
  * the operation's `Left`, naming the collection), and every write refused unless the collection declares the
  * capability it needs — whatever the plugin's own member would do.
  */
final class HostCollection(val name: HostCollection.Name, collection: SourceCollection):
  def title: com.histmersion.domain.Title = guardedValue(collection.title, name)
  def description: com.histmersion.pluginhost.HostCollection.Description = guardedValue(collection.description, com.histmersion.pluginhost.HostCollection.Description(""))
  def capabilities: Set[CollectionCapability] = guardedValue(collection.capabilities, Set(CollectionCapability.Read))

  def reach: Either[com.histmersion.pluginhost.HostCollection.Problem, Unit] = guarded(collection.reach)
  def search(query: CollectionQuery): Either[com.histmersion.pluginhost.HostCollection.Problem, List[CollectionItem]] = guarded(collection.search(query))
  def item(id: CollectionId): Either[com.histmersion.pluginhost.HostCollection.Problem, Option[CollectionItem]] = guarded(collection.item(id))
  def admits(item: CollectionItem): Either[com.histmersion.pluginhost.HostCollection.Problem, Unit] = guarded(collection.admits(item))
  def file(item: CollectionItem, format: Option[com.histmersion.pluginhost.HostCollection.FileFormat]): Either[com.histmersion.pluginhost.HostCollection.Problem, CollectionFile] =
    guarded(collection.file(item, format))
  def original(item: CollectionItem, file: CollectionFile): Either[com.histmersion.pluginhost.HostCollection.Problem, FetchedFile] =
    guarded(collection.original(item, file))
  def cover(item: CollectionItem): Either[com.histmersion.pluginhost.HostCollection.Problem, Option[FetchedFile]] = guarded(collection.cover(item))
  def audit(records: List[CollectionRecord]): Either[com.histmersion.pluginhost.HostCollection.Problem, List[CollectionFinding]] =
    guarded(collection.audit(records))

  def add(metadata: CollectionMetadata, file: FetchedFile): Either[com.histmersion.pluginhost.HostCollection.Problem, CollectionId] =
    writing(CollectionCapability.Add)(collection.add(metadata, file))
  def replace(id: CollectionId, key: com.histmersion.pluginhost.HostCollection.Key, file: FetchedFile): Either[com.histmersion.pluginhost.HostCollection.Problem, com.histmersion.pluginhost.HostCollection.Id] =
    writing(CollectionCapability.Add)(collection.replace(id, key, file))
  def update(id: CollectionId, metadata: CollectionMetadata): Either[com.histmersion.pluginhost.HostCollection.Problem, List[CollectionChange]] =
    writing(CollectionCapability.Update)(collection.update(id, metadata))
  def updateAll(links: List[CollectionLink], apply: com.histmersion.pluginhost.HostCollection.Apply): Either[com.histmersion.pluginhost.HostCollection.Problem, List[CollectionUpdateEntry]] =
    if apply then writing(CollectionCapability.Update)(collection.updateAll(links, apply))
    else guarded(collection.updateAll(links, apply))
  def set(id: CollectionId, fields: List[(com.histmersion.pluginhost.HostCollection.FieldName, com.histmersion.pluginhost.HostCollection.SetFieldsPart2)]): Either[com.histmersion.pluginhost.HostCollection.Problem, Unit] =
    writing(CollectionCapability.Update)(collection.set(id, fields))

  /** Whether the collection declares `capability`. */
  def can(capability: CollectionCapability): Boolean = capabilities.contains(capability)

  /** The refusal of a write the collection does not declare. */
  def readOnly: com.histmersion.pluginhost.HostCollection.Note = s"$title is read-only here: ${guardedValue(collection.readOnlyReason, "it is read-only")}"

  private def writing[A](capability: CollectionCapability)(call: => Either[String, A]): Either[com.histmersion.pluginhost.HostCollection.Problem, A] =
    if can(capability) then guarded(call) else Left(readOnly)

  private def guarded[A](call: => Either[String, A]): Either[com.histmersion.pluginhost.HostCollection.Problem, A] =
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

object HostCollection:
  type Apply = Apply.T
  object Apply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FieldName = FieldName.T
  object FieldName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FileFormat = FileFormat.T
  object FileFormat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFieldsPart2 = SetFieldsPart2.T
  object SetFieldsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
