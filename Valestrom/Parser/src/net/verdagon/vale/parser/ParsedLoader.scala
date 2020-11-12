package net.verdagon.vale.parser

import net.liftweb.json._

object ParsedLoader {
  def expectJValue(obj: Object): JValue = {
    if (!obj.isInstanceOf[JValue]) {
      throw BadVPRException(BadVPRError("Expected JSON object, got: " + obj.getClass.getSimpleName))
    }
    obj.asInstanceOf[JValue]
  }
  def expectJValue(obj: JValue, expectedType: String): JValue = {
    val jobj = expectJValue(obj)
    val actualType = getStringField(jobj, "__type")
    if (!actualType.equals(expectedType)) {
      throw BadVPRException(BadVPRError("Expected " + expectedType + " but got a " + actualType))
    }
    jobj
  }
  def getField(jobj: JValue, fieldName: String): JValue = {
    (jobj \ fieldName) match {
      case JNothing => throw BadVPRException(BadVPRError("Object had no field named " + fieldName))
      case other => other
    }
  }
  def getField(containerJobj: JValue, fieldName: String, expectedType: String): JValue = {
    val obj = expectJValue(getField(containerJobj, fieldName))
    val jobj = obj.asInstanceOf[JValue]
    val actualType = getStringField(jobj, "__type")
    if (!actualType.equals(expectedType)) {
      throw BadVPRException(BadVPRError("Expected " + expectedType + " but got a " + actualType))
    }
    jobj
  }
  def getStringField(jobj: JValue, fieldName: String): String = {
    getField(jobj, fieldName) match {
      case JString(s) => s
      case _ => throw BadVPRException(BadVPRError("Field " + fieldName + " wasn't a string!"))
    }
  }
  def getArrayField(jobj: JValue, fieldName: String): List[JValue] = {
    getField(jobj, fieldName) match {
      case JArray(arr) => arr
      case _ => throw BadVPRException(BadVPRError("Field " + fieldName + " wasn't an array!"))
    }
  }

  def load(source: String): IParseResult[FileP] = {
    try {
      val jfile = expectJValue(parse(source), "File")
      val topLevelThings = getArrayField(jfile, "topLevelThings")

      val file = FileP(List())
      ParseSuccess(file)
    } catch {
      case BadVPRException(err) => ParseFailure(err)
    }
  }
}
