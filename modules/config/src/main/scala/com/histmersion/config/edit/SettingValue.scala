package com.histmersion.config.edit

import org.ekrich.config.{ConfigException, ConfigFactory, ConfigList, ConfigRenderOptions, ConfigValue,
  ConfigValueFactory, ConfigValueType}

import scala.jdk.CollectionConverters.*

/** One value to write into the config file. */
enum SettingValue:
  case Text(text: String)
  case Number(number: BigDecimal)
  case Flag(flag: Boolean)
  case Items(items: List[SettingValue])

  /** As HOCON holds it. */
  def toConfig: ConfigValue = this match
    case Text(t)    => ConfigValueFactory.fromAnyRef(t)
    case Number(n)  => ConfigValueFactory.fromAnyRef(if n.isWhole && n.isValidLong then java.lang.Long.valueOf(n.toLong)
                                                    else java.lang.Double.valueOf(n.toDouble))
    case Flag(b)    => ConfigValueFactory.fromAnyRef(java.lang.Boolean.valueOf(b))
    case Items(xs)  => ConfigValueFactory.fromIterable(xs.map(_.toConfig.unwrapped).asJava)

  /** The text written into the file: JSON, which HOCON reads as it is. */
  def hocon: String = toConfig.render(ConfigRenderOptions.concise)

  def kind: SettingKind = this match
    case Text(_)   => SettingKind.Text
    case Number(_) => SettingKind.Number
    case Flag(_)   => SettingKind.Flag
    case Items(_)  => SettingKind.Items

object SettingValue:

  private val Truthy = Set("true", "yes", "on")
  private val Falsy  = Set("false", "no", "off")

  /** What a person typed, read as a setting of `kind` — the CLI's argument, a field of the web form. Text is
    * taken as typed; a number, a flag and a list are read; `Any` (a plugin's own key) guesses the kind. */
  def parse(raw: String, kind: SettingKind): Either[String, SettingValue] =
    val t = Option(raw).getOrElse("")
    kind match
      case SettingKind.Text   => Right(Text(t))
      case SettingKind.Number => number(t).toRight(s"'${t.trim}' is not a number")
      case SettingKind.Flag   => flag(t).toRight(s"'${t.trim}' is not true or false")
      case SettingKind.Items  => items(t)
      case SettingKind.Block  => Left("this is a block of settings: set one of the keys inside it")
      case SettingKind.Any    =>
        Right(flag(t).orElse(number(t)).orElse(Option.when(t.trim.startsWith("["))(items(t).toOption).flatten)
          .getOrElse(Text(t)))

  private def number(t: String): Option[SettingValue] =
    Option(t.trim).filter(_.matches("-?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?")).map(s => Number(BigDecimal(s)))

  private def flag(t: String): Option[SettingValue] =
    t.trim.toLowerCase(java.util.Locale.ROOT) match
      case s if Truthy(s) => Some(Flag(true))
      case s if Falsy(s)  => Some(Flag(false))
      case _              => None

  /** `[a, "b c", 3]` (HOCON's list), or words between commas. */
  private def items(t: String): Either[String, SettingValue] =
    val s = t.trim
    if s.isEmpty then Right(Items(Nil))
    else if s.startsWith("[") then
      try
        ConfigFactory.parseString(s"v = $s").getValue("v") match
          case l: ConfigList => fromConfig(l).toRight(s"'$s' holds something other than plain values")
          case _             => Left(s"'$s' is not a list")
      catch case e: ConfigException => Left(s"'$s' is not a list: ${e.getMessage.replaceFirst("^String: \\d+: ", "")}")
    else Right(Items(s.split(',').toList.map(_.trim).filter(_.nonEmpty).map(Text(_))))

  /** A value as HOCON holds it — `None` for an object or null. */
  def fromConfig(v: ConfigValue): Option[SettingValue] = v.valueType match
    case ConfigValueType.STRING  => Some(Text(v.unwrapped.toString))
    case ConfigValueType.NUMBER  => Some(Number(BigDecimal(v.unwrapped.toString)))
    case ConfigValueType.BOOLEAN => Some(Flag(v.unwrapped.asInstanceOf[java.lang.Boolean].booleanValue))
    case ConfigValueType.LIST    =>
      val xs = v.asInstanceOf[ConfigList].asScala.toList.map(fromConfig)
      Option.when(xs.forall(_.isDefined))(Items(xs.flatten))
    case _ => None

  /** Do two values say the same thing (`5` and `5.0` do; `"5"` and `5` do not)? */
  def same(a: SettingValue, b: SettingValue): Boolean = (a, b) match
    case (Number(x), Number(y)) => x == y
    case (Items(xs), Items(ys)) => xs.size == ys.size && xs.zip(ys).forall(same)
    case _                      => a == b
