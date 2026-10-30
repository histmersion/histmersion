package com.histmersion.config.edit

import org.ekrich.config.{Config, ConfigException, ConfigFactory, ConfigObject, ConfigOrigin, ConfigValue}
import org.ekrich.config.parser.ConfigDocumentFactory

import scala.annotation.tailrec
import scala.util.Try

/** The file after a set of changes: its new text, the keys that changed and the ones that already said so. */
final case class EditedText(text: String, changed: List[SettingKey], unchanged: List[SettingKey])

/** Where a value of the config file, as read with its includes, was written: in the file itself, or in a
  * file it includes. */
enum Written:
  case Nowhere
  case Here(value: ConfigValue)
  case Elsewhere(file: String)
  /** Behind a `${…}` substitution, `+=`, or an object merged with one. */
  case Substituted(at: SettingKey)

/** Changes to the config file's TEXT that keep everything else as it is: its comments, its order, its layout,
  * its includes and its unknown keys (sconfig's `ConfigDocument`, which edits the parsed text, not a
  * rendering of the tree). A setting written with a substitution or `+=`, or one that comes from an
  * included file, is refused — never rewritten. Setting a value the file already holds changes nothing.
  * Pure: `current` is the file as read with its includes, unresolved, and `here` tells its own origins. */
object ConfigTextEdit:

  def apply(text: String, current: Config, here: ConfigOrigin => Boolean, edits: List[SettingEdit]): Either[SettingError, EditedText] =
    @tailrec def loop(rest: List[SettingEdit], doc: org.ekrich.config.parser.ConfigDocument,
        changed: List[SettingKey], unchanged: List[SettingKey]): Either[SettingError, EditedText] =
      rest match
        case Nil => Right(EditedText(endsLikeAFile(text, doc.render), changed.reverse, unchanged.reverse))
        case edit :: tail =>
          (written(current, edit.key, here), edit) match
            case (Written.Substituted(_), _)     => Left(SettingError.Substitution(edit.key))
            case (Written.Elsewhere(f), _)       => Left(SettingError.FromInclude(edit.key, f))
            case (Written.Here(v), SettingEdit.Set(k, want)) if SettingValue.fromConfig(v).exists(SettingValue.same(_, want)) =>
              loop(tail, doc, changed, k :: unchanged)
            case (Written.Nowhere, SettingEdit.Unset(k)) =>
              loop(tail, doc, changed, k :: unchanged)
            case (_, SettingEdit.Set(k, want)) =>
              val next = ConfigDocumentFactory.parseString(inFileStyle(doc.render, doc.withValueText(k.hoconPath, want.hocon).render))
              if dropsVerbatim(doc.render, next.render) then Left(SettingError.Substitution(k))
              else loop(tail, next, k :: changed, unchanged)
            case (_, SettingEdit.Unset(k)) =>
              val next = withoutEmptyBlocks(doc.withoutPath(k.hoconPath), k)
              if dropsVerbatim(doc.render, next.render) then Left(SettingError.Substitution(k))
              else loop(tail, next, k :: changed, unchanged)
    try
      val doc = ConfigDocumentFactory.parseString(text)
      // a key changed twice in one write: the last change wins, the first is not written at all
      val last = edits.reverse.distinctBy(_.key.value).reverse
      loop(last, doc, Nil, Nil)
    catch case e: ConfigException => Left(SettingError.Unreadable(e.getMessage))

  private val AddedBlock = """^(\s*)("[^"]*"|[A-Za-z0-9_-]+) : \{\s*$""".r
  private val AddedValue = """^(\s*)("[^"]*"|[A-Za-z0-9_-]+) : (.*)$""".r

  /** The lines an edit ADDED, in the file's own style: `key {` and `key = value` where sconfig writes `key : {`
    * and `key : value`. Only lines not in `before` are touched, and only when the result reads the same. */
  private def inFileStyle(before: String, after: String): String =
    val old = before.linesIterator.toList.groupBy(identity).view.mapValues(_.size).toMap
    @tailrec def loop(rest: List[String], seen: Map[String, Int], acc: List[String]): List[String] =
      rest match
        case Nil => acc.reverse
        case line :: tail =>
          val n = seen.getOrElse(line, 0)
          if n < old.getOrElse(line, 0) then loop(tail, seen.updated(line, n + 1), line :: acc)
          else
            val styled = line match
              case AddedBlock(indent, key)        => s"$indent$key {"
              case AddedValue(indent, key, value) => s"$indent$key = $value"
              case other                          => other
            loop(tail, seen, styled :: acc)
    val styled = loop(after.split("\n", -1).toList, Map.empty, Nil).mkString("\n")
    val same = scala.util.Try(ConfigFactory.parseString(styled).root == ConfigFactory.parseString(after).root).getOrElse(false)
    if same then styled else after

  /** `doc` without the blocks taking `key` out left empty (`backups { }`), up to `histmersion` — unless a block
    * holds a comment, which stays where it was written. */
  private def withoutEmptyBlocks(doc: org.ekrich.config.parser.ConfigDocument, key: SettingKey): org.ekrich.config.parser.ConfigDocument =
    @tailrec def loop(d: org.ekrich.config.parser.ConfigDocument, at: Option[SettingKey]): org.ekrich.config.parser.ConfigDocument =
      at match
        case None => d
        case Some(block) =>
          val empty = scala.util.Try(ConfigFactory.parseString(d.render).getObject(block.hoconPath).isEmpty).getOrElse(false)
          val pruned = if empty then d.withoutPath(block.hoconPath) else d
          if !empty || comments(pruned.render) < comments(d.render) then d
          else loop(pruned, block.parent)
    val root = loop(doc, key.parent)
    // the `histmersion { }` block itself, when it is left empty and holds no comment
    val emptyRoot = scala.util.Try(ConfigFactory.parseString(root.render).getObject(SettingKey.Root).isEmpty).getOrElse(false)
    if emptyRoot then
      val pruned = root.withoutPath(SettingKey.Root)
      if comments(pruned.render) < comments(root.render) then root else pruned
    else root

  private def comments(text: String): Int = text.linesIterator.count(l => l.contains("#") || l.contains("//"))

  /** A file that ended with a line break (or had no text) still does. */
  private def endsLikeAFile(before: String, after: String): String =
    if after.nonEmpty && !after.endsWith("\n") && (before.isEmpty || before.endsWith("\n")) then after + "\n" else after

  /** What each key is to hold after `edits` (`None`: taken out); the last change of a key wins. */
  def intended(edits: List[SettingEdit]): Map[String, Option[SettingValue]] =
    edits.map {
      case SettingEdit.Set(k, v) => k.value -> Some(v)
      case SettingEdit.Unset(k)  => k.value -> None
    }.toMap

  /** The new file, read back (with its includes, unresolved; `here` its own origins), says what each change
    * wrote — an include after it, or a second line for the same key, would otherwise win over it. */
  def verify(parsed: Config, here: ConfigOrigin => Boolean, edited: EditedText,
      intended: Map[String, Option[SettingValue]]): Either[SettingError, Unit] =
    edited.changed.iterator.map { key =>
      (written(parsed, key, here), intended.getOrElse(key.value, None)) match
        case (Written.Substituted(_), _)          => Left(SettingError.Substitution(key))
        case (Written.Elsewhere(f), _)            => Left(SettingError.FromInclude(key, f))
        case (Written.Here(v), Some(w)) if SettingValue.fromConfig(v).exists(SettingValue.same(_, w)) => Right(())
        case (Written.Nowhere, None)              => Right(())
        case _ => Left(SettingError.Invalid(s"${key.relative} did not read back as it was written"))
    }.find(_.isLeft).getOrElse(Right(()))

  /** Did an edit take out a substitution or an include the file had (a key written twice, once with
    * `${…}`, its plain value being the one that counts)? Those are kept exactly, so such a change is refused. */
  private def dropsVerbatim(before: String, after: String): Boolean =
    count(after, "${") < count(before, "${") || count(after, "+=") < count(before, "+=") ||
      includes(after) < includes(before)

  private def count(text: String, what: String): Int =
    @tailrec def loop(from: Int, n: Int): Int = text.indexOf(what, from) match
      case -1 => n
      case i  => loop(i + what.length, n + 1)
    loop(0, 0)

  private def includes(text: String): Int = text.linesIterator.count(_.trim.startsWith("include"))

  /** Where the file (read with its includes, unresolved) writes `key`. */
  def written(current: Config, key: SettingKey, here: ConfigOrigin => Boolean): Written =
    @tailrec def down(obj: ConfigObject, path: List[String], seen: List[String]): Written =
      path match
        case Nil => Written.Nowhere
        case word :: rest =>
          val at = SettingKey.either((seen :+ word).mkString(".")).toOption
          Try(Option(obj.get(word))) match
            case scala.util.Failure(_)    => Written.Substituted(at.getOrElse(key))
            case scala.util.Success(None) => Written.Nowhere
            case scala.util.Success(Some(v)) =>
              if Try(v.valueType).isFailure then Written.Substituted(at.getOrElse(key))
              else if rest.isEmpty then (if here(v.origin) then Written.Here(v) else Written.Elsewhere(fileOf(v.origin)))
              else v match
                case o: ConfigObject if Try(o.keySet).isSuccess => down(o, rest, seen :+ word)
                case o: ConfigObject => Written.Substituted(at.getOrElse(key))
                // a single value where the block of `key` belongs: setting `key` replaces it
                case other => if here(other.origin) then Written.Nowhere else Written.Elsewhere(fileOf(other.origin))
    down(current.root, key.segments, Nil)

  private def fileOf(o: ConfigOrigin): String =
    Option(o.filename).orElse(Option(o.url).map(_.toString)).getOrElse(o.description)
