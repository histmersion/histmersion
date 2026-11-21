# 0009. One newtype kernel gives every concept its instances, and conversions follow Iron

- Status: accepted
- Date: 2026-11-21

## Context

Every value in the model is an Iron newtype (AGENTS.md, *Coding practices* 1). Each needs the same instances: ordering,
equality under `-language:strictEquality`, its name for error messages, and a typed refusal. Written per companion, those
drift from the code. Layers keep their own copies of a type (a client's copy judges nothing; the server validates), and
Chimney converts between them.

## Decision

- **`Newtype[A, C]`** (module `newtype`) is the companion of every newtype: an Iron `RefinedType` that also provides, as
  members of the companion (so they are in `T`'s implicit scope with no import): `CanEqual[T, T]`; the base type's
  `Ordering` lifted to `T` and the comparison operators; `typeName`, `constraint`, `refused` and `checked`. Every
  instance is an `inline given` and costs nothing at run time: a value of `T` is a value of `A`, so the ordering of `T` is
  the ordering of `A` itself. A companion that defines an `Ordering[T]` of its own keeps it, because a given defined in
  the companion takes precedence over an inherited one.
- **Names are derived, never spelled**: the newtype's and the constraint's names are Hearth's `TypeName`s (the
  constraint's where the companion is defined, the newtype's own where it is asked for), and the constraint's
  description is Iron's. Iron's packages are left out of a constraint's name (`Match["[a-z]+"]`). A companion beside a
  top-level alias is named through the file's synthetic object (`a.b.File$package.Citekey.T`), which the name drops to
  `a.b.Citekey`.
- **`chimney-iron`** converts newtype to newtype. Into a client copy that judges nothing (`Pure`) it wraps the base
  value without a check; into a newtype with a constraint of its own it checks the base value and fails with the
  target's reason. When the argument is already refined with the target's constraint it calls Iron's `apply`, which emits
  no check. The module builds for Scala 3 only, where Hearth's `Type` and `Expr` are `scala.quoted`'s own.
- **`NonEmptyVector[A]`** is an opaque type over `Vector[A]`: at run time it is the vector itself, so a value that is one
  `A` or many (`A | NonEmptyVector[A]`) is told apart by whether it is a vector. An element type must therefore never be
  a vector itself. `nonempty-derivation` registers a Hearth `IsCollectionProvider` (through `META-INF/services`) so that
  Kindlings derives its JSON codecs and Tapir schemas as a list that refuses an empty input. It runs at compile time only,
  so nothing of it reaches a binary.
- **Tests compare expected literals by value** (`ComparedByValue`): two types compare by `==` when they have the same
  shape and every pair inside is one type, a subtype and its supertype, or a newtype and its own base. Anything else
  (two newtypes, a newtype and another base type, a `Vector` and a `List`) does not compile. An expected list that mixes
  `Some` and `None` infers the union of the alternatives, so the instances cover it.

## Consequences

- A new newtype is one line (`object Citekey extends Newtype[String, Not[Blank]]`) and gets every instance.
- Comparing unrelated types is a compile error, in code and in tests.

## Code

`modules/newtype/src/main/scala/com/histmersion/Newtype.scala`, `modules/chimney-iron/`, `modules/chimney-iron-tests/`,
`modules/nonempty/`, `modules/nonempty-derivation/`, `modules/values/src/test/scala/com/histmersion/values/testsupport/ComparedByValue.scala`.
