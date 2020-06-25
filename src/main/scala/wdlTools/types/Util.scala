package wdlTools.types

import wdlTools.types.WdlTypes._
import wdlTools.types.{TypedAbstractSyntax => TAT}

object Util {
  // check if the right hand side of an assignment matches the left hand side
  //
  // Negative examples:
  //    Int i = "hello"
  //    Array[File] files = "8"
  //
  // Positive examples:
  //    Int k =  3 + 9
  //    Int j = k * 3
  //    String s = "Ford model T"
  //    String s2 = 5
  def isPrimitive(t: T): Boolean = {
    t match {
      case T_String | T_File | T_Boolean | T_Int | T_Float => true
      case _                                               => false
    }
  }

  def typeToString(t: T): String = {
    t match {
      case T_Boolean        => "Boolean"
      case T_Int            => "Int"
      case T_Float          => "Float"
      case T_String         => "String"
      case T_File           => "File"
      case T_Directory      => "Directory"
      case T_Any            => "Any"
      case T_Var(i)         => s"Var($i)"
      case T_Identifier(id) => s"Id(${id})"
      case T_Pair(l, r)     => s"Pair[${typeToString(l)}, ${typeToString(r)}]"
      case T_Array(t, _)    => s"Array[${typeToString(t)}]"
      case T_Map(k, v)      => s"Map[${typeToString(k)}, ${typeToString(v)}]"
      case T_Object         => "Object"
      case T_Optional(t)    => s"Optional[${typeToString(t)}]"

      // a user defined structure
      case T_Struct(name, _) => s"Struct($name)"

      case T_Task(name, input, output) =>
        val inputs = input
          .map {
            case (name, (t, _)) =>
              s"$name -> ${typeToString(t)}"
          }
          .mkString(", ")
        val outputs = output
          .map {
            case (name, t) =>
              s"$name -> ${typeToString(t)}"
          }
          .mkString(", ")
        s"TaskSig($name, input=$inputs, outputs=${outputs})"

      case T_Workflow(name, input, output) =>
        val inputs = input
          .map {
            case (name, (t, _)) =>
              s"$name -> ${typeToString(t)}"
          }
          .mkString(", ")
        val outputs = output
          .map {
            case (name, t) =>
              s"$name -> ${typeToString(t)}"
          }
          .mkString(", ")
        s"WorkflowSig($name, input={$inputs}, outputs={$outputs})"

      // The type of a call to a task or a workflow.
      case T_Call(name, output: Map[String, T]) =>
        val outputs = output
          .map {
            case (name, t) =>
              s"$name -> ${typeToString(t)}"
          }
          .mkString(", ")
        s"Call $name { $outputs }"

      // T representation for an stdlib function.
      // For example, stdout()
      case T_Function0(name, output) =>
        s"${name}() -> ${typeToString(output)}"

      // A function with one argument
      case T_Function1(name, input, output) =>
        s"${name}(${typeToString(input)}) -> ${typeToString(output)}"

      // A function with two arguments. For example:
      // Float size(File, [String])
      case T_Function2(name, arg1, arg2, output) =>
        s"${name}(${typeToString(arg1)}, ${typeToString(arg2)}) -> ${typeToString(output)}"

      // A function with three arguments. For example:
      // String sub(String, String, String)
      case T_Function3(name, arg1, arg2, arg3, output) =>
        s"${name}(${typeToString(arg1)}, ${typeToString(arg2)}, ${typeToString(arg3)}) -> ${typeToString(output)}"
    }
  }

  /**
    * Utility function for writing an expression in a human readable form
    * @param expr the expression to convert
    * @param callback a function that optionally converts expression to string - if it returns
    *                 Some(string), string is returned rather than the default formatting. This
    *                 can be used to provide custom formatting for some or all parts of a nested
    *                 expression.
    */
  def exprToString(expr: TAT.Expr, callback: Option[TAT.Expr => Option[String]] = None): String = {
    if (callback.isDefined) {
      val s = callback.get(expr)
      if (s.isDefined) {
        return s.get
      }
    }

    expr match {
      case TAT.ValueNull(_, _)                    => "null"
      case TAT.ValueNone(_, _)                    => "None"
      case TAT.ValueBoolean(value: Boolean, _, _) => value.toString
      case TAT.ValueInt(value, _, _)              => value.toString
      case TAT.ValueFloat(value, _, _)            => value.toString
      case TAT.ValueString(value, _, _)           => s""""$value"""" // add double quotes around value
      case TAT.ValueFile(value, _, _)             => s""""$value"""" // -"-
      case TAT.ValueDirectory(value, _, _)        => s""""$value"""" // -"-
      case TAT.ExprIdentifier(id: String, _, _)   => id

      case TAT.ExprCompoundString(value, _, _) =>
        val vec = value.map(x => exprToString(x, callback)).mkString(", ")
        s"ExprCompoundString(${vec})"
      case TAT.ExprPair(l, r, _, _) =>
        s"(${exprToString(l, callback)}, ${exprToString(r, callback)})"
      case TAT.ExprArray(value, _, _) =>
        "[" + value.map(x => exprToString(x, callback)).mkString(", ") + "]"
      case TAT.ExprMap(value, _, _) =>
        val m2 = value
          .map {
            case (k, v) => s"${exprToString(k, callback)} : ${exprToString(v, callback)}"
          }
          .mkString(", ")
        s"{$m2}"
      case TAT.ExprObject(value, _, _) =>
        val m2 = value
          .map {
            case (k, v) => s"${exprToString(k, callback)} : ${exprToString(v, callback)}"
          }
          .mkString(", ")
        s"object {$m2}"

      // ~{true="--yes" false="--no" boolean_value}
      case TAT.ExprPlaceholderEqual(t, f, value, _, _) =>
        s"{true=${exprToString(t, callback)} false=${exprToString(f, callback)} ${exprToString(value, callback)}"

      // ~{default="foo" optional_value}
      case TAT.ExprPlaceholderDefault(default, value, _, _) =>
        s"{default=${exprToString(default, callback)} ${exprToString(value, callback)}}"

      // ~{sep=", " array_value}
      case TAT.ExprPlaceholderSep(sep, value, _, _) =>
        s"{sep=${exprToString(sep, callback)} ${exprToString(value, callback)}"

      // operators on one argument
      case TAT.ExprUnaryPlus(value, _, _) =>
        s"+ ${exprToString(value, callback)}"
      case TAT.ExprUnaryMinus(value, _, _) =>
        s"- ${exprToString(value, callback)}"
      case TAT.ExprNegate(value, _, _) =>
        s"not(${exprToString(value, callback)})"

      // operators on two arguments
      case TAT.ExprLor(a, b, _, _) =>
        s"${exprToString(a, callback)} || ${exprToString(b, callback)}"
      case TAT.ExprLand(a, b, _, _) =>
        s"${exprToString(a, callback)} && ${exprToString(b, callback)}"
      case TAT.ExprEqeq(a, b, _, _) =>
        s"${exprToString(a, callback)} == ${exprToString(b, callback)}"
      case TAT.ExprLt(a, b, _, _) => s"${exprToString(a, callback)} < ${exprToString(b, callback)}"
      case TAT.ExprGte(a, b, _, _) =>
        s"${exprToString(a, callback)} >= ${exprToString(b, callback)}"
      case TAT.ExprNeq(a, b, _, _) =>
        s"${exprToString(a, callback)} != ${exprToString(b, callback)}"
      case TAT.ExprLte(a, b, _, _) =>
        s"${exprToString(a, callback)} <= ${exprToString(b, callback)}"
      case TAT.ExprGt(a, b, _, _)  => s"${exprToString(a, callback)} > ${exprToString(b, callback)}"
      case TAT.ExprAdd(a, b, _, _) => s"${exprToString(a, callback)} + ${exprToString(b, callback)}"
      case TAT.ExprSub(a, b, _, _) => s"${exprToString(a, callback)} - ${exprToString(b, callback)}"
      case TAT.ExprMod(a, b, _, _) => s"${exprToString(a, callback)} % ${exprToString(b, callback)}"
      case TAT.ExprMul(a, b, _, _) => s"${exprToString(a, callback)} * ${exprToString(b, callback)}"
      case TAT.ExprDivide(a, b, _, _) =>
        s"${exprToString(a, callback)} / ${exprToString(b, callback)}"

      // Access an array element at [index]
      case TAT.ExprAt(array, index, _, _) =>
        s"${exprToString(array, callback)}[${index}]"

      // conditional:
      // if (x == 1) then "Sunday" else "Weekday"
      case TAT.ExprIfThenElse(cond, tBranch, fBranch, _, _) =>
        s"if (${exprToString(cond, callback)}) then ${exprToString(tBranch, callback)} else ${exprToString(fBranch, callback)}"

      // Apply a standard library function to arguments. For example:
      //   read_int("4")
      case TAT.ExprApply(funcName, _, elements, _, _) =>
        val args = elements.map(x => exprToString(x, callback)).mkString(", ")
        s"${funcName}(${args})"

      case TAT.ExprGetName(e, id: String, _, _) =>
        s"${exprToString(e, callback)}.${id}"
    }
  }
}
