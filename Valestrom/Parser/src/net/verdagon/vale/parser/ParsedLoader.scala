package net.verdagon.vale.parser

import net.liftweb.json._
import net.verdagon.vale.vimpl

object ParsedLoader {
  def expectJValue(obj: Object): JValue = {
    if (!obj.isInstanceOf[JValue]) {
      throw BadVPSTException(BadVPSTError("Expected JSON object, got: " + obj.getClass.getSimpleName))
    }
    obj.asInstanceOf[JValue]
  }
  def expectJValue(obj: JValue, expectedType: String): JValue start here change to obj? = {
    val jobj = expectJValue(obj)
    val actualType = getStringField(jobj, "__type")
    if (!actualType.equals(expectedType)) {
      throw BadVPSTException(BadVPSTError("Expected " + expectedType + " but got a " + actualType))
    }
    jobj
  }
  def getField(jobj: JValue, fieldName: String): JValue = {
    (jobj \ fieldName) match {
      case JNothing => throw BadVPSTException(BadVPSTError("Object had no field named " + fieldName))
      case other => other
    }
  }
  def getField(containerJobj: JValue, fieldName: String, expectedType: String): JValue = {
    val obj = expectJValue(getField(containerJobj, fieldName))
    val jobj = obj.asInstanceOf[JValue]
    val actualType = getStringField(jobj, "__type")
    if (!actualType.equals(expectedType)) {
      throw BadVPSTException(BadVPSTError("Expected " + expectedType + " but got a " + actualType))
    }
    jobj
  }
  def getStringField(jobj: JValue, fieldName: String): String = {
    getField(jobj, fieldName) match {
      case JString(s) => s
      case _ => throw BadVPSTException(BadVPSTError("Field " + fieldName + " wasn't a string!"))
    }
  }
  def getArrayField(jobj: JValue, fieldName: String): List[JValue] = {
    getField(jobj, fieldName) match {
      case JArray(arr) => arr
      case _ => throw BadVPSTException(BadVPSTError("Field " + fieldName + " wasn't an array!"))
    }
  }
  def getType(obj: JValue): String = {
    val jobj = expectJValue(obj)
    getStringField(jobj, "__type")
  }
  def getRange(obj: JValue, fieldName: String): String = {
    val jobj = expectJValue(obj)
    getStringField(jobj, "__type")
  }

  def load(source: String): IParseResult[FileP] = {
    try {
      val jfile = expectJValue(parse(source), "File")
      ParseSuccess(
        FileP(
          getArrayField(jfile, "topLevelThings").map(topLevelThing => {
            getType(topLevelThing) match {
              case "Struct" => {
                StructP(
                  getRange(topLevelThing)
                )
              }
              case x => vimpl(x.toString)
            }
          })))
    } catch {
      case BadVPSTException(err) => ParseFailure(err)
    }
  }
}
