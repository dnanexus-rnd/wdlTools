package wdlTools.types

import WdlTypes.{T_Function2, T_Var, _}
import wdlTools.syntax.{Operator, WdlVersion}
import wdlTools.types.ExprState.ExprState
import wdlTools.types.TypeCheckingRegime.TypeCheckingRegime
import dx.util.{Logger, TraceLevel}

// TODO: for the prototypes with array inputs and outputs (transpose, zip, cross, flatten, prefix),
//  the non-empty value of the result arrays should match the values of the input arrays
case class Stdlib(regime: TypeCheckingRegime, version: WdlVersion, logger: Logger = Logger.get) {
  private val unify = Unification(regime)

  // Some functions are overloaded and can take several kinds of arguments.
  // A particulary problematic one is size.
  // There is WDL code that calls size with File, and File?. So what is the prototype of "size" ?
  //
  // We chould use:
  //       T_File -> T_Float
  // or
  //       T_File? -> T_Float
  // or both:
  //       T_File -> T_Float
  //       T_File? -> T_Float
  //
  // A constraint we wish to maintain is that looking up a function prototype will
  // return just one result. Therefore, we ended up with the prototype:
  //       T_File? -> T_Float
  // because with the current type system T_File? is more general than T_File.
  // TODO: make sure everything here is in sync with
  //  https://github.com/openwdl/wdl/issues/396

  private def unaryNumericPrototypes(funcName: String): Vector[T_Function] = {
    Vector(
        T_Function1(funcName, T_Int, T_Int),
        T_Function1(funcName, T_Float, T_Float)
    )
  }
  private def comparisonPrototypes(funcName: String): Vector[T_Function] = {
    Vector(
        T_Function2(funcName, T_Boolean, T_Boolean, T_Boolean),
        T_Function2(funcName, T_Int, T_Int, T_Boolean),
        T_Function2(funcName, T_Float, T_Float, T_Boolean),
        T_Function2(funcName, T_String, T_String, T_Boolean),
        T_Function2(funcName, T_Int, T_Float, T_Boolean),
        T_Function2(funcName, T_Float, T_Int, T_Boolean)
    )
  }
  private def binaryNumericPrototypes(funcName: String): Vector[T_Function] = {
    Vector(
        T_Function2(funcName, T_Int, T_Int, T_Int),
        T_Function2(funcName, T_Float, T_Float, T_Float),
        T_Function2(funcName, T_Int, T_Float, T_Float),
        T_Function2(funcName, T_Float, T_Int, T_Float)
    )
  }
  // The + operator is overloaded for string arguments. If both arguments are non-optional,
  // then the return type is non-optional. Within an interpolation, if either argument type
  // is optional, then the return type is optional
  private def optionalStringAdditionPrototypes(a: T, b: T, result: T): Vector[T_Function] = {
    Vector(
        T_Function2(Operator.Addition.name, T_Optional(a), b, T_Optional(result)),
        T_Function2(Operator.Addition.name, a, T_Optional(b), T_Optional(result)),
        T_Function2(Operator.Addition.name, T_Optional(a), T_Optional(b), T_Optional(result))
    )
  }
  private def stringAdditionPrototypes(a: T, b: T, result: T): Vector[T_Function] = {
    Vector(
        T_Function2(Operator.Addition.name, a, b, result)
    ) ++ optionalStringAdditionPrototypes(a, b, result)
  }

  private lazy val draft2Prototypes: Vector[T_Function] = Vector(
      // unary numeric operators
      unaryNumericPrototypes(Operator.UnaryPlus.name),
      unaryNumericPrototypes(Operator.UnaryMinus.name),
      // logical operators
      Vector(
          T_Function1(Operator.LogicalNot.name, T_Boolean, T_Boolean),
          T_Function2(Operator.LogicalOr.name, T_Boolean, T_Boolean, T_Boolean),
          T_Function2(Operator.LogicalAnd.name, T_Boolean, T_Boolean, T_Boolean)
      ),
      // comparison operators
      // equal/not-equal comparisons are allowed for all primitive types
      // prior to V2 Booleans and Strings could be compared by >/<; it is
      // not explicitly stated in the spec, we assume
      // * true > false
      // * Strings are ordered lexicographically using Unicode code point number
      //   for comparison of individual characters
      comparisonPrototypes(Operator.Equality.name),
      comparisonPrototypes(Operator.Inequality.name),
      comparisonPrototypes(Operator.LessThan.name),
      comparisonPrototypes(Operator.LessThanOrEqual.name),
      comparisonPrototypes(Operator.GreaterThan.name),
      comparisonPrototypes(Operator.GreaterThanOrEqual.name),
      // equal/not-equal is allowed for File-String
      // also, it is not explicitly stated in the spec, but we allow
      // comparisons for any operands of the same type
      Vector(
          T_Function2(Operator.Equality.name, T_File, T_File, T_Boolean),
          T_Function2(Operator.Inequality.name, T_File, T_File, T_Boolean),
          T_Function2(Operator.Equality.name, T_File, T_String, T_Boolean),
          T_Function2(Operator.Inequality.name, T_File, T_String, T_Boolean),
          T_Function2(Operator.Equality.name, T_Var(0), T_Var(0), T_Boolean),
          T_Function2(Operator.Inequality.name, T_Var(0), T_Var(0), T_Boolean)
      ),
      // the + function is overloaded for string expressions
      // it is omitted from the spec, but we allow String + Boolean as well
      stringAdditionPrototypes(T_File, T_File, T_File),
      stringAdditionPrototypes(T_File, T_String, T_File),
      stringAdditionPrototypes(T_String, T_File, T_File),
      stringAdditionPrototypes(T_String, T_String, T_String),
      stringAdditionPrototypes(T_Int, T_String, T_String),
      stringAdditionPrototypes(T_String, T_Int, T_String),
      stringAdditionPrototypes(T_Float, T_String, T_String),
      stringAdditionPrototypes(T_String, T_Float, T_String),
      stringAdditionPrototypes(T_String, T_Boolean, T_String),
      stringAdditionPrototypes(T_Boolean, T_String, T_String),
      // binary numeric operators
      binaryNumericPrototypes(Operator.Addition.name),
      binaryNumericPrototypes(Operator.Subtraction.name),
      binaryNumericPrototypes(Operator.Multiplication.name),
      binaryNumericPrototypes(Operator.Division.name),
      binaryNumericPrototypes(Operator.Remainder.name),
      // standard library functions
      Vector(
          T_Function0("stdout", T_File),
          T_Function0("stderr", T_File),
          T_Function1("read_lines", T_File, T_Array(T_String)),
          T_Function1("read_tsv", T_File, T_Array(T_Array(T_String))),
          T_Function1("read_map", T_File, T_Map(T_String, T_String)),
          T_Function1("read_object", T_File, T_Object),
          T_Function1("read_objects", T_File, T_Array(T_Object)),
          T_Function1("read_json", T_File, T_Any),
          T_Function1("read_int", T_File, T_Int),
          T_Function1("read_string", T_File, T_String),
          T_Function1("read_float", T_File, T_Float),
          T_Function1("read_boolean", T_File, T_Boolean),
          T_Function1("write_lines", T_Array(T_String), T_File),
          T_Function1("write_tsv", T_Array(T_Array(T_String)), T_File),
          T_Function1("write_map", T_Map(T_String, T_String), T_File),
          T_Function1("write_object", T_Object, T_File),
          T_Function1("write_objects", T_Array(T_Object), T_File),
          T_Function1("write_json", T_Any, T_File),
          T_Function1("size", T_Optional(T_File), T_Float),
          // Size takes an optional units parameter (KB, KiB, MB, GiB, ...)
          T_Function2("size", T_Optional(T_File), T_String, T_Float),
          T_Function3("sub", T_String, T_String, T_String, T_String),
          T_Function1("range", T_Int, T_Array(T_Int)),
          // Array[Array[X]] transpose(Array[Array[X]])
          T_Function1("transpose", T_Array(T_Array(T_Var(0))), T_Array(T_Array(T_Var(0)))),
          // Array[Pair(X,Y)] zip(Array[X], Array[Y])
          T_Function2("zip",
                      T_Array(T_Var(0)),
                      T_Array(T_Var(1)),
                      T_Array(T_Pair(T_Var(0), T_Var(1)))),
          // Array[Pair(X,Y)] cross(Array[X], Array[Y])
          T_Function2("cross",
                      T_Array(T_Var(0)),
                      T_Array(T_Var(1)),
                      T_Array(T_Pair(T_Var(0), T_Var(1)))),
          // Integer length(Array[X])
          T_Function1("length", T_Array(T_Var(0)), T_Int),
          // Array[X] flatten(Array[Array[X]])
          T_Function1("flatten", T_Array(T_Array(T_Var(0))), T_Array(T_Var(0))),
          T_Function2("prefix",
                      T_String,
                      T_Array(T_Var(0, TypeUtils.PrimitiveTypes)),
                      T_Array(T_String)),
          T_Function1("select_first", T_Array(T_Optional(T_Var(0))), T_Var(0)),
          T_Function1("select_all", T_Array(T_Optional(T_Var(0))), T_Array(T_Var(0))),
          T_Function1("defined", T_Optional(T_Var(0)), T_Boolean),
          T_Function1("basename", T_String, T_String),
          T_Function2("basename", T_String, T_String, T_String),
          T_Function1("floor", T_Float, T_Int),
          T_Function1("ceil", T_Float, T_Int),
          T_Function1("round", T_Float, T_Int),
          // not mentioned in the specification
          T_Function1("glob", T_String, T_Array(T_File))
      )
  ).flatten

  private lazy val v1Prototypes: Vector[T_Function] = Vector(
      // unary numeric operators
      unaryNumericPrototypes(Operator.UnaryPlus.name),
      unaryNumericPrototypes(Operator.UnaryMinus.name),
      // logical operators
      Vector(
          T_Function1(Operator.LogicalNot.name, T_Boolean, T_Boolean),
          T_Function2(Operator.LogicalOr.name, T_Boolean, T_Boolean, T_Boolean),
          T_Function2(Operator.LogicalAnd.name, T_Boolean, T_Boolean, T_Boolean)
      ),
      // comparison operators
      // equal/not-equal comparisons are allowed for all primitive types
      // prior to V2 Booleans and Strings could be compared by >/<; it is
      // not explicitly stated in the spec, we assume
      // * true > false
      // * Strings are ordered lexicographically using Unicode code point number
      //   for comparison of individual characters
      comparisonPrototypes(Operator.Equality.name),
      comparisonPrototypes(Operator.Inequality.name),
      comparisonPrototypes(Operator.LessThan.name),
      comparisonPrototypes(Operator.LessThanOrEqual.name),
      comparisonPrototypes(Operator.GreaterThan.name),
      comparisonPrototypes(Operator.GreaterThanOrEqual.name),
      // equal/not-equal is allowed for File-String
      // also, it is not explicitly stated in the spec, but we allow
      // comparisons for any operands of the same type
      Vector(
          T_Function2(Operator.Equality.name, T_File, T_File, T_Boolean),
          T_Function2(Operator.Inequality.name, T_File, T_File, T_Boolean),
          T_Function2(Operator.Equality.name, T_File, T_String, T_Boolean),
          T_Function2(Operator.Inequality.name, T_File, T_String, T_Boolean),
          T_Function2(Operator.Equality.name, T_Var(0), T_Var(0), T_Boolean),
          T_Function2(Operator.Inequality.name, T_Var(0), T_Var(0), T_Boolean)
      ),
      // the + function is overloaded for string expressions
      // it is omitted from the spec, but we allow String + Boolean as well
      stringAdditionPrototypes(T_File, T_File, T_File),
      stringAdditionPrototypes(T_File, T_String, T_File),
      stringAdditionPrototypes(T_String, T_File, T_File),
      stringAdditionPrototypes(T_String, T_String, T_String),
      stringAdditionPrototypes(T_Int, T_String, T_String),
      stringAdditionPrototypes(T_String, T_Int, T_String),
      stringAdditionPrototypes(T_Float, T_String, T_String),
      stringAdditionPrototypes(T_String, T_Float, T_String),
      stringAdditionPrototypes(T_String, T_Boolean, T_String),
      stringAdditionPrototypes(T_Boolean, T_String, T_String),
      // binary numeric operators
      binaryNumericPrototypes(Operator.Addition.name),
      binaryNumericPrototypes(Operator.Subtraction.name),
      binaryNumericPrototypes(Operator.Multiplication.name),
      binaryNumericPrototypes(Operator.Division.name),
      binaryNumericPrototypes(Operator.Remainder.name),
      // standard library functions
      Vector(
          T_Function0("stdout", T_File),
          T_Function0("stderr", T_File),
          T_Function1("read_lines", T_File, T_Array(T_String)),
          T_Function1("read_tsv", T_File, T_Array(T_Array(T_String))),
          T_Function1("read_map", T_File, T_Map(T_String, T_String)),
          T_Function1("read_object", T_File, T_Object),
          T_Function1("read_objects", T_File, T_Array(T_Object)),
          T_Function1("read_json", T_File, T_Any),
          T_Function1("read_int", T_File, T_Int),
          T_Function1("read_string", T_File, T_String),
          T_Function1("read_float", T_File, T_Float),
          T_Function1("read_boolean", T_File, T_Boolean),
          T_Function1("write_lines", T_Array(T_String), T_File),
          T_Function1("write_tsv", T_Array(T_Array(T_String)), T_File),
          T_Function1("write_map", T_Map(T_String, T_String), T_File),
          T_Function1("write_object", T_Object, T_File),
          T_Function1("write_objects", T_Array(T_Object), T_File),
          T_Function1("write_json", T_Any, T_File),
          T_Function1("size", T_Optional(T_File), T_Float),
          T_Function1("size", T_Array(T_File), T_Float),
          // Size takes an optional units parameter (KB, KiB, MB, GiB, ...)
          T_Function2("size", T_Optional(T_File), T_String, T_Float),
          T_Function2("size", T_Array(T_File), T_String, T_Float),
          T_Function3("sub", T_String, T_String, T_String, T_String),
          T_Function1("range", T_Int, T_Array(T_Int)),
          // Array[Array[X]] transpose(Array[Array[X]])
          T_Function1("transpose", T_Array(T_Array(T_Var(0))), T_Array(T_Array(T_Var(0)))),
          // Array[Pair(X,Y)] zip(Array[X], Array[Y])
          T_Function2("zip",
                      T_Array(T_Var(0)),
                      T_Array(T_Var(1)),
                      T_Array(T_Pair(T_Var(0), T_Var(1)))),
          // Array[Pair(X,Y)] cross(Array[X], Array[Y])
          T_Function2("cross",
                      T_Array(T_Var(0)),
                      T_Array(T_Var(1)),
                      T_Array(T_Pair(T_Var(0), T_Var(1)))),
          // Integer length(Array[X])
          T_Function1("length", T_Array(T_Var(0)), T_Int),
          // Array[X] flatten(Array[Array[X]])
          T_Function1("flatten", T_Array(T_Array(T_Var(0))), T_Array(T_Var(0))),
          T_Function2("prefix",
                      T_String,
                      T_Array(T_Var(0, TypeUtils.PrimitiveTypes)),
                      T_Array(T_String)),
          T_Function1("select_first", T_Array(T_Optional(T_Var(0))), T_Var(0)),
          T_Function1("select_all", T_Array(T_Optional(T_Var(0))), T_Array(T_Var(0))),
          T_Function1("defined", T_Optional(T_Var(0)), T_Boolean),
          T_Function1("basename", T_String, T_String),
          T_Function2("basename", T_String, T_String, T_String),
          T_Function1("floor", T_Float, T_Int),
          T_Function1("ceil", T_Float, T_Int),
          T_Function1("round", T_Float, T_Int),
          // not mentioned in the specification
          T_Function1("glob", T_String, T_Array(T_File))
      )
  ).flatten

  private def comparisonPrototypesV2(funcName: String): Vector[T_Function] = {
    Vector(
        T_Function2(funcName, T_Int, T_Int, T_Boolean),
        T_Function2(funcName, T_Int, T_Float, T_Boolean),
        T_Function2(funcName, T_Float, T_Float, T_Boolean),
        T_Function2(funcName, T_Float, T_Int, T_Boolean),
        T_Function2(funcName, T_String, T_String, T_Boolean)
    )
  }

  private lazy val v2Prototypes: Vector[T_Function] = Vector(
      // unary numeric operators
      unaryNumericPrototypes(Operator.UnaryMinus.name),
      // logical operators
      Vector(
          T_Function1(Operator.LogicalNot.name, T_Boolean, T_Boolean),
          T_Function2(Operator.LogicalOr.name, T_Boolean, T_Boolean, T_Boolean),
          T_Function2(Operator.LogicalAnd.name, T_Boolean, T_Boolean, T_Boolean)
      ),
      // comparison operators
      comparisonPrototypesV2(Operator.Equality.name),
      comparisonPrototypesV2(Operator.Inequality.name),
      comparisonPrototypesV2(Operator.LessThan.name),
      comparisonPrototypesV2(Operator.LessThanOrEqual.name),
      comparisonPrototypesV2(Operator.GreaterThan.name),
      comparisonPrototypesV2(Operator.GreaterThanOrEqual.name),
      // it is not explicitly stated in the spec, but we allow equal/not-equal
      // comparisons for any operands of the same type
      Vector(
          T_Function2(Operator.Equality.name, T_File, T_File, T_Boolean),
          T_Function2(Operator.Equality.name, T_Directory, T_Directory, T_Boolean),
          T_Function2(Operator.Inequality.name, T_File, T_File, T_Boolean),
          T_Function2(Operator.Inequality.name, T_Directory, T_Directory, T_Boolean),
          T_Function2(Operator.Equality.name, T_Var(0), T_Var(0), T_Boolean),
          T_Function2(Operator.Inequality.name, T_Var(0), T_Var(0), T_Boolean)
      ),
      // the + function is overloaded for string expressions
      // even worse, it has different semantics within an interpolation vs elsewhere
      // https://github.com/openwdl/wdl/blob/main/versions/development/SPEC.md#interpolating-and-concatenating-optional-strings
      Vector(
          // if both arguments are non-optional, then the return type is non-optional
          // v2 inverts the argument order of File/String concatenation - we allow
          // both in all versions
          T_Function2(Operator.Addition.name, T_File, T_String, T_File),
          T_Function2(Operator.Addition.name, T_String, T_File, T_File),
          T_Function2(Operator.Addition.name, T_String, T_String, T_String)
      ),
      // binary numeric operators
      binaryNumericPrototypes(Operator.Addition.name),
      binaryNumericPrototypes(Operator.Subtraction.name),
      binaryNumericPrototypes(Operator.Multiplication.name),
      binaryNumericPrototypes(Operator.Division.name),
      binaryNumericPrototypes(Operator.Remainder.name),
      // standard library functions
      Vector(
          T_Function0("stdout", T_File),
          T_Function0("stderr", T_File),
          T_Function1("read_lines", T_File, T_Array(T_String)),
          T_Function1("read_tsv", T_File, T_Array(T_Array(T_String))),
          T_Function1("read_map", T_File, T_Map(T_String, T_String)),
          T_Function1("read_json", T_File, T_Any),
          T_Function1("read_int", T_File, T_Int),
          T_Function1("read_string", T_File, T_String),
          T_Function1("read_float", T_File, T_Float),
          T_Function1("read_boolean", T_File, T_Boolean),
          T_Function1("write_lines", T_Array(T_String), T_File),
          T_Function1("write_tsv", T_Array(T_Array(T_String)), T_File),
          T_Function1("write_map", T_Map(T_String, T_String), T_File),
          T_Function1("write_json", T_Any, T_File),
          T_Function1("size", T_Optional(T_File), T_Float),
          T_Function1("size", T_Array(T_File), T_Float),
          // Size takes an optional units parameter (KB, KiB, MB, GiB, ...)
          T_Function2("size", T_Optional(T_File), T_String, T_Float),
          T_Function2("size", T_Array(T_File), T_String, T_Float),
          T_Function3("sub", T_String, T_String, T_String, T_String),
          T_Function1("range", T_Int, T_Array(T_Int)),
          // Array[Array[X]] transpose(Array[Array[X]])
          T_Function1("transpose", T_Array(T_Array(T_Var(0))), T_Array(T_Array(T_Var(0)))),
          // Array[Pair(X,Y)] zip(Array[X], Array[Y])
          T_Function2("zip",
                      T_Array(T_Var(0)),
                      T_Array(T_Var(1)),
                      T_Array(T_Pair(T_Var(0), T_Var(1)))),
          // Array[Pair(X,Y)] cross(Array[X], Array[Y])
          T_Function2("cross",
                      T_Array(T_Var(0)),
                      T_Array(T_Var(1)),
                      T_Array(T_Pair(T_Var(0), T_Var(1)))),
          // Array[Pair[X,Y]] as_pairs(Map[X,Y])
          T_Function1("as_pairs", T_Map(T_Var(0), T_Var(1)), T_Array(T_Pair(T_Var(0), T_Var(1)))),
          // Map[X,Y] as_map(Array[Pair[X,Y]])
          T_Function1("as_map", T_Array(T_Pair(T_Var(0), T_Var(1))), T_Map(T_Var(0), T_Var(1))),
          // // Array[X] keys(Map[X,Y])
          T_Function1("keys", T_Map(T_Var(0), T_Any), T_Array(T_Var(0))),
          // Map[X,Array[Y]] collect_by_key(Array[Pair[X,Y]])
          T_Function1("collect_by_keys",
                      T_Array(T_Pair(T_Var(0), T_Var(1))),
                      T_Map(T_Var(0), T_Array(T_Var(1)))),
          // Integer length(Array[X])
          T_Function1("length", T_Array(T_Var(0)), T_Int),
          // Array[X] flatten(Array[Array[X]])
          T_Function1("flatten", T_Array(T_Array(T_Var(0))), T_Array(T_Var(0))),
          T_Function2("prefix",
                      T_String,
                      T_Array(T_Var(0, TypeUtils.PrimitiveTypes)),
                      T_Array(T_String)),
          T_Function2("suffix",
                      T_String,
                      T_Array(T_Var(0, TypeUtils.PrimitiveTypes)),
                      T_Array(T_String)),
          T_Function1("quote", T_Array(T_Var(0, TypeUtils.PrimitiveTypes)), T_Array(T_String)),
          T_Function1("squote", T_Array(T_Var(0, TypeUtils.PrimitiveTypes)), T_Array(T_String)),
          T_Function1("select_first", T_Array(T_Optional(T_Var(0))), T_Var(0)),
          T_Function1("select_all", T_Array(T_Optional(T_Var(0))), T_Array(T_Var(0))),
          T_Function1("defined", T_Optional(T_Var(0)), T_Boolean),
          T_Function1("basename", T_String, T_String),
          T_Function2("basename", T_String, T_String, T_String),
          T_Function1("floor", T_Float, T_Int),
          T_Function1("ceil", T_Float, T_Int),
          T_Function1("round", T_Float, T_Int),
          T_Function2("sep", T_String, T_Array(T_String), T_String),
          // mentioned in the specification but not formally defined
          T_Function1("glob", T_String, T_Array(T_File)),
          // https://github.com/openwdl/wdl/pull/304
          T_Function2("min", T_Int, T_Int, T_Int),
          T_Function2("min", T_Int, T_Float, T_Float),
          T_Function2("min", T_Float, T_Int, T_Float),
          T_Function2("min", T_Float, T_Float, T_Float),
          T_Function2("max", T_Int, T_Int, T_Int),
          T_Function2("max", T_Int, T_Float, T_Float),
          T_Function2("max", T_Float, T_Int, T_Float),
          T_Function2("max", T_Float, T_Float, T_Float)
      )
  ).flatten

  private lazy val v2PlaceholderPrototypes: Vector[T_Function] = Vector(
      optionalStringAdditionPrototypes(T_File, T_String, T_File),
      optionalStringAdditionPrototypes(T_String, T_File, T_File),
      optionalStringAdditionPrototypes(T_String, T_String, T_String),
      // these concatenations are no longer allowed generally, but
      // I believe they should be allowed within placeholders - there
      // is an open discussion https://github.com/openwdl/wdl/issues/391
      stringAdditionPrototypes(T_Int, T_String, T_String),
      stringAdditionPrototypes(T_String, T_Int, T_String),
      stringAdditionPrototypes(T_Float, T_String, T_String),
      stringAdditionPrototypes(T_String, T_Float, T_String),
      stringAdditionPrototypes(T_String, T_Boolean, T_String),
      stringAdditionPrototypes(T_Boolean, T_String, T_String)
  ).flatten

  // choose the standard library prototypes according to the WDL version
  private def protoTable(exprState: ExprState): Vector[T_Function] = version match {
    case WdlVersion.Draft_2 =>
      draft2Prototypes
    case WdlVersion.V1 =>
      v1Prototypes
    case WdlVersion.V2
        if exprState >= ExprState.InPlaceholder || regime <= TypeCheckingRegime.Lenient =>
      v2Prototypes ++ v2PlaceholderPrototypes
    case WdlVersion.V2 =>
      v2Prototypes
    case other => throw new RuntimeException(s"Unsupported WDL version ${other}")
  }

  // build a mapping from a function name to all of its prototypes.
  // Some functions are overloaded, so they may have several.
  private def funcProtoMap(exprState: ExprState): Map[String, Vector[T_Function]] = {
    protoTable(exprState).foldLeft(Map.empty[String, Vector[T_Function]]) {
      case (accu, funcDesc: T_Function) =>
        accu.get(funcDesc.name) match {
          case None =>
            accu + (funcDesc.name -> Vector(funcDesc))
          case Some(protoVec: Vector[T_Function]) =>
            accu + (funcDesc.name -> (protoVec :+ funcDesc))
        }
    }
  }

  /**
    * Evaluate the output type of a function. This may require calculation because
    * some functions are polymorphic in their inputs.
    * @param funcProto The function prototype
    * @param inputTypes The actual input types
    * @return an optional (T, Boolean), where T is the actual output type, and the
    *         second argument specifies whether all types in the signature were
    *         matched exactly by the input types.
    */
  private def evalOnePrototype(funcProto: T_Function,
                               inputTypes: Vector[T],
                               ctx: UnificationContext): Option[(T, Priority.Priority)] = {
    val arity = inputTypes.size
    val (args, output) = (arity, funcProto) match {
      case (0, T_Function0(_, output))                   => (Vector.empty, output)
      case (1, T_Function1(_, arg1, output))             => (Vector(arg1), output)
      case (2, T_Function2(_, arg1, arg2, output))       => (Vector(arg1, arg2), output)
      case (3, T_Function3(_, arg1, arg2, arg3, output)) => (Vector(arg1, arg2, arg3), output)
      case (_, _)                                        => return None
    }
    try {
      Some(unify.apply(args, inputTypes, output, ctx))
    } catch {
      case _: TypeUnificationException =>
        None
    }
  }

  /**
    * Determines whether `funcName` is valid and whether there is a signature that
    * matches `inputTypes`.
    * @param funcName The function name
    * @param inputTypes The input types
    * @param exprState the state of the expression at which point this function is
    *                   being evaluated.
    * @return
    */
  def apply(funcName: String,
            inputTypes: Vector[T],
            exprState: ExprState,
            section: Section.Section = Section.Other): (T, T_Function) = {
    val candidates: Vector[T_Function] = funcProtoMap(exprState).get(funcName) match {
      case None =>
        throw new StdlibFunctionException(s"No function named ${funcName} in the standard library")
      case Some(protoVec) =>
        protoVec
    }
    val ctx = UnificationContext(section, inPlaceholder = exprState >= ExprState.InPlaceholder)
    // The function may be overloaded, taking several types of inputs. Try to match all
    // prototypes against the input, preferring the one with exactly matching inputs (if any)
    val (priority, viableCandidates): (Priority.Priority,
                                       Vector[(T, T_Function, Priority.Priority)]) =
      candidates
        .flatMap { funcProto =>
          try {
            evalOnePrototype(funcProto, inputTypes, ctx) match {
              case None                => None
              case Some((t, priority)) => Some((t, funcProto, priority))
            }
          } catch {
            case e: SubstitutionException =>
              throw new StdlibFunctionException(e.getMessage)
          }
        }
        .groupBy(_._3)
        .toVector
        .sortWith(_._1 < _._1)
        .headOption
        .getOrElse({
          // no matches
          val inputsStr = inputTypes.map(TypeUtils.prettyFormatType).mkString("\n")
          val candidatesStr = candidates.map(TypeUtils.prettyFormatType(_)).mkString("\n")
          val msg =
            s"""|Invoking stdlib function ${funcName} with badly typed arguments
                |${candidatesStr}

                |inputs: ${inputsStr}

                |""".stripMargin
          throw new StdlibFunctionException(msg)
        })
    if (viableCandidates.size > 1) {
      // Match more than one prototype.
      val prototypeDescriptions = viableCandidates
        .map {
          case (_, funcSig, _) => TypeUtils.prettyFormatType(funcSig)
        }
        .mkString("\n")
      val msg = s"""|Call to ${funcName} matches ${viableCandidates.size} prototypes
                    |inputTypes: ${inputTypes}
                    |prototypes:
                    |${prototypeDescriptions}
                    |""".stripMargin
      throw new StdlibFunctionException(msg)
    }
    val (resultType, resultSignature, _) = viableCandidates.head
    if (priority == Priority.Exact) {
      logger.trace(
          s"selected exact match prototype ${resultSignature}",
          minLevel = TraceLevel.VVerbose
      )
    } else {
      logger.trace(
          s"selected coerced prototype ${resultSignature}",
          minLevel = TraceLevel.VVerbose
      )
    }
    (resultType, resultSignature)
  }
}
