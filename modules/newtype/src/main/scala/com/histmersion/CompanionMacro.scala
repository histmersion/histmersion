package com.histmersion

import scala.quoted.*

/** The compile-time half of [[Newtype.described]]: it reads the companion being defined from the place the given is
  * summoned (the companion's own `extends Newtype[...]`), so the names are the compiler's, never a written string.
  */
object CompanionMacro:

  /** Iron's packages, left out of a constraint's name: `Interval.Closed[0, 9]`, not its full path. */
  private val IronPackages = "\\bio\\.github\\.iltotore\\.iron\\.(?:constraint\\.\\w+\\.)?(?:\\w+\\$package\\.)?".r

  def described[A: Type, C: Type](using Quotes): Expr[Newtype.Described[A, C]] =
    import quotes.reflect.*
    val companion = enclosingObject(Symbol.spliceOwner)
    val name = Expr(companion.fold("")(fullName))
    // `C` reaches the given dealiased (`Pure` as `True`); the companion's own parent keeps it as written
    val written = companion
      .map(_.typeRef.baseType(TypeRepr.of[Newtype[?, ?]].typeSymbol))
      .collect { case AppliedType(_, List(_, c)) => c }
      .getOrElse(TypeRepr.of[C])
    val constraint = Expr(IronPackages.replaceAllIn(asIronWrites(written).show(using Printer.TypeReprCode), ""))
    '{ Newtype.Described[A, C](Newtype.TypeName($name), $constraint) }

  /** The innermost object around `owner`: the companion whose `extends` clause summons the description. */
  private def enclosingObject(using Quotes)(owner: quotes.reflect.Symbol): Option[quotes.reflect.Symbol] =
    import quotes.reflect.*
    Iterator
      .iterate(owner)(_.maybeOwner)
      .takeWhile(s => !s.isNoSymbol && !s.isPackageDef)
      .find(s => s.isClassDef && s.flags.is(Flags.Module))

  /** A symbol's name as source code writes it: its package, then the enclosing objects and itself, without the
    * compiler's `$` suffixes and file wrappers.
    */
  private def fullName(using Quotes)(symbol: quotes.reflect.Symbol): String =
    val chain = Iterator.iterate(symbol)(_.maybeOwner).takeWhile(s => !s.isNoSymbol).toList
    val (inside, packages) = chain.span(s => !s.isPackageDef)
    val names = inside.map(_.name.stripSuffix("$")).filterNot(_.endsWith("$package")).reverse
    (packages.headOption.map(_.fullName).filterNot(_ == "<root>").toList ++ names).mkString(".")

  /** A constraint as Iron writes it: the project's own aliases (`CitekeyRule`) are followed to the Iron type they stand
    * for (`Match["[a-z]+"]`); Iron's own (`Pure`, `Interval.Closed[0, 9]`) are kept as they are.
    */
  private def asIronWrites(using Quotes)(constraint: quotes.reflect.TypeRepr): quotes.reflect.TypeRepr =
    import quotes.reflect.*
    def iron(s: Symbol): Boolean = s.fullName.startsWith("io.github.iltotore.iron.")
    constraint match
      case AppliedType(head, _) if iron(head.typeSymbol)       => constraint
      case ref: TypeRef if iron(ref.typeSymbol)                => constraint
      case ref: TypeRef if ref.typeSymbol.isAliasType          => asIronWrites(ref.translucentSuperType)
      case AppliedType(head, _) if head.typeSymbol.isAliasType => asIronWrites(constraint.dealias)
      case _                                                   => constraint
