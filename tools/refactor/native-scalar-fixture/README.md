# Native scalar operation repairs

The expected output is fixed independently of the matcher. Thirteen operations cover
numeric receivers, right operands, comparisons, bit operations, conversion and
an effectful operand, property selection, unary negation and a predicate placeholder.
Runtime checks retain overflow, NaN, division by zero and
single-evaluation behavior. Both input and expected output use the actual Iron
library, Scala 3.10 future source, and fatal E165 diagnostics.

The excluded file must never appear in the plan: non-Pure constraints, a general
overloaded call, a callback call, an unrelated overloaded receiver, an infix
placeholder callback, an unproven inferred receiver and a user-defined `Int`.
Those cases remain unsupported; the last two deliberately distinguish native
receiver evidence from a compiler-rendered short class name.

Use the existing Scalafix CLI workflow with native-scalar-fixture-index.conf,
compile the input with scala-cli into the index's build.log (expected failure),
then run native-scalar-fixture.conf and native-scalar-disabled-fixture.conf.
Compare the rewritten positive file with expected/NativeOps.scala and run its
checkNativeScalar main. The disabled configuration must produce an empty plan.
Reports are immutable: choose fresh target report directories for a repeated run.

nativeScalarOperations requires ownerOperationsOnly and nativeBooleans. It adds
numeric E008 receiver operations across owners, plus numeric E134 infix arguments
with a literal receiver or an explicit newtype parameter's `.value` receiver.
The compiler must report the corresponding native class and a compatible numeric
overload. Placeholders and function arguments are rejected. It does not enable
general E134 overload adapters, arbitrary argument unwrapping, nominal conversion,
new constraints, or aliases between model concepts. Existing owner-local repairs
remain available in this mode. Imported Pure resolution is a separate option.

This is a transformation fixture, not an independent policy-detection holdout.
