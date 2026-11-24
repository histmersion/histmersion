package com.histmersion
package lint

enum LintRule:
  case G3Misplaced, G9Prefix, G9PrefixRole, G44, G44Conversion, G44PlainDef, G45Boundary, G46, G47Root, G47Fqn,
    G47Self, G47Package, G48, G5Problem, G5ProblemText, G5TextBranch, G55Positional, G58Raw, G60FromString,
    G60External, G60Unrecognised, G89Unguarded, DiscardedOutcome, BooleanParam, UnnamedBoolean, SilentCompare,
    SilentInterp, SilentPure
  case Primitive, Layer, ClientRefined, Concept, Derives, Copy, OneType, Transaction, Global, Service, Helper, Prefix,
    Bucket, Circe, Scallop, NonEmptyPattern, ProblemField, Unrecognised, Relayed, FirstProblem, HtmlSink, HtmlNewtype,
    FailureRequest, RawRequest, SchemaConfig, Unclassified, CaseDefault, CanEqualHome
  case Placement, Direction, FileSystem, Process, Repository, TotalOrder, GetWrites
