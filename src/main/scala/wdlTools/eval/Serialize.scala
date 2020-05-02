package wdlTools.eval

import wdlTools.eval.WdlValues._
import spray.json._

// The mapping of JSON type to WDL type is:
// JSON Type 	WDL Type
// object 	Map[String, ?]
// array 	Array[?]
// number 	Int or Float
// string 	String
// boolean 	Boolean
// null 	null

object Serialize {
  def toJson(wv : WV) : JsValue = {
    wv match {
      case WV_Null => JsNull
      case WV_Boolean(value) => JsBoolean(value)
      case WV_Int(value) => JsNumber(value)
      case WV_Float(value) => JsNumber(value)
      case WV_String(value) => JsString(value)
      case WV_File(value) => JsString(value)

      // compound values
      case WV_Array(vec) =>
        JsArray(vec.map(toJson(_)))
      case WV_Object(members) =>
        JsObject(members.map{ case (k,v) => k -> toJson(v) })
      case WV_Struct(_, members) =>
        JsObject(members.map{ case (k,v) => k -> toJson(v) })

      case other => throw new JsonSerializationException(s"value ${other} not supported")
    }
  }

  def fromJson(jsv : JsValue) : WV = {
    jsv  match {
      case JsNull => WV_Null
      case JsBoolean(value) => WV_Boolean(value)
      case JsNumber(value) =>
        // Convert the big-decimal to int, if possible. Otherwise
        // return a float.
        val n = value.toInt
        val x = value.toDouble
        if (n == x.toInt) WV_Int(n)
        else WV_Float(x)
      case JsString(value) => WV_String(value)

      // compound values
      case JsArray(vec) =>
        WV_Array(vec.map(fromJson(_)))
      case JsObject(fields) =>
        WV_Object(fields.map{ case (k,v) => k -> fromJson(v) })
    }
  }
}
