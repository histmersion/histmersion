package com.histmersion
package config.edit

import org.ekrich.config.{
  ConfigException,
  ConfigFactory,
  ConfigList,
  ConfigRenderOptions,
  ConfigValue,
  ConfigValueFactory,
  ConfigValueType
}

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** One value to write into the config file. */
enum SettingValue:
  case Text(text: SettingValue.Text.Value)
  case Number(number: BigDecimal)
  case Flag(flag: SettingValue.Flag.Value)
  case Items(items: List[SettingValue])

  /** As HOCON holds it. */
  def toConfig: ConfigValue = this match
    case Text(t)   => ConfigValueFactory.fromAnyRef(t.value)
    case Number(n) =>
      ConfigValueFactory.fromAnyRef(if n.isWhole && n.isValidLong then java.lang.Long.valueOf(n.toLong)
      else java.lang.Double.valueOf(n.toDouble))
    case Flag(b)   => ConfigValueFactory.fromAnyRef(java.lang.Boolean.valueOf(b.value))
    case Items(xs) => ConfigValueFactory.fromIterable(xs.map(_.toConfig.unwrapped).asJava)

  /** The text written into the file: JSON, which HOCON reads as it is. */
  def hocon: SettingValue.Hocon = SettingValue.Hocon(toConfig.render(ConfigRenderOptions.concise))

  def kind: SettingKind = this match
    case Text(_)   => SettingKind.Text
    case Number(_) => SettingKind.Number
    case Flag(_)   => SettingKind.Flag
    case Items(_)  => SettingKind.Items

object SettingValue:
  type Hocon = Hocon.T
  object Hocon extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]

  object Text:
    type Value = Value.T
    object Value extends Newtype[String, Pure]

  object Flag:
    type Value = Value.T
    object Value extends Newtype[Boolean, Pure]

  type Typed = Typed.T
  object Typed extends Newtype[String, Pure]

  private val Truthy = Set("true", "yes", "on")
  private val Falsy = Set("false", "no", "off")

  /** What a person typed, read as a setting of `kind` — the CLI's argument, a field of the web form. Text is taken as
    * typed; a number, a flag and a list are read; `Any` (a plugin's own key) guesses the kind.
    */
  def parse(raw: Typed, kind: SettingKind): Either[Problem, SettingValue] =
    val t = Option(raw).map(_.value).getOrElse("")
    kind match
      case SettingKind.Text   => Right(Text(Text.Value(t)))
      case SettingKind.Number => number(t).toRight(Problem(s"'${t.trim}' is not a number"))
      case SettingKind.Flag   => flag(t).toRight(Problem(s"'${t.trim}' is not true or false"))
      case SettingKind.Items  => items(t)
      case SettingKind.Block  => Left(Problem("this is a block of settings: set one of the keys inside it"))
      case SettingKind.Any    =>
        Right(
          flag(t)
            .orElse(number(t))
            .orElse(Option.when(t.trim.startsWith("["))(items(t).toOption).flatten)
            .getOrElse(Text(Text.Value(t)))
        )

  private def number(t: String): Option[SettingValue] =
    Option(t.trim).filter(_.matches("-?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?")).map(s => Number(BigDecimal(s)))

  private def flag(t: String): Option[SettingValue] =
    t.trim.toLowerCase(java.util.Locale.ROOT) match
      case s if Truthy(s) => Some(Flag(Flag.Value(true)))
      case s if Falsy(s)  => Some(Flag(Flag.Value(false)))
      case _              => None

  /** `[a, "b c", 3]` (HOCON's list), or words between commas. */
  private def items(t: String): Either[Problem, SettingValue] =
    val s = t.trim
    if s.isEmpty then Right(Items(Nil))
    else if s.startsWith("[") then
      try
        ConfigFactory.parseString(s"v = $s").getValue("v") match
          case l: ConfigList => fromConfig(l).toRight(Problem(s"'$s' holds something other than plain values"))
          case _             => Left(Problem(s"'$s' is not a list"))
      catch case e: ConfigException => Left(Problem(s"'$s' is not a list: ${e.getMessage.replaceFirst("^String: \\d+: ", "")}"))
    else Right(Items(s.split(',').toList.map(_.trim).filter(_.nonEmpty).map(x => Text(Text.Value(x)))))

  /** A value as HOCON holds it — `None` for an object or null. */
  def fromConfig(v: ConfigValue): Option[SettingValue] = v.valueType match
    case ConfigValueType.STRING  => Some(Text(Text.Value(v.unwrapped.toString)))
    case ConfigValueType.NUMBER  => Some(Number(BigDecimal(v.unwrapped.toString)))
    case ConfigValueType.BOOLEAN => Some(Flag(Flag.Value(v.unwrapped.asInstanceOf[java.lang.Boolean].booleanValue)))
    case ConfigValueType.LIST    =>
      val xs = v.asInstanceOf[ConfigList].asScala.toList.map(fromConfig)
      Option.when(xs.forall(_.isDefined))(Items(xs.flatten))
    case _ => None

  /** Do two values say the same thing (`5` and `5.0` do; `"5"` and `5` do not)? */
  def same(a: SettingValue, b: SettingValue): Boolean = (a, b) match
    case (Number(x), Number(y)) => x == y
    case (Items(xs), Items(ys)) => xs.size == ys.size && xs.zip(ys).forall(same)
    case _                      => a == b
