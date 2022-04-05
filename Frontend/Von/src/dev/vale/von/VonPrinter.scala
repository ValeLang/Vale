package dev.vale.von

import dev.vale.{Profiler, vcurious, vimpl}
import org.apache.commons.lang.StringEscapeUtils

sealed trait ISyntax
case class VonSyntax(
  includeFieldNames: Boolean = true,
  squareBracesForArrays: Boolean = true,
  includeEmptyParams: Boolean = true,
  includeEmptyArrayMembersAtEnd: Boolean = true,
) extends ISyntax { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }
case object JsonSyntax extends ISyntax

class VonPrinter(
    syntax: ISyntax,
    lineWidth: Int,
    typeNameMap: Map[String, String] = Map(),
    includeSpaceAfterComma: Boolean = true,
) {
  val memberSeparator = if (includeSpaceAfterComma) ", " else ","

  def print(data: IVonData): String = {
    val builder = new StringBuilder()
    printSingleLine(data, lineWidth) match {
      case Some(str) => builder ++= str
      case None => printMultiline(builder, data, 0)
    }
    builder.toString()
  }

  def escape(value: String): String = {
    syntax match {
      case VonSyntax(_, _, _, _) => StringEscapeUtils.escapeJava(value)
      case JsonSyntax => {
        // We couldn't use escapeJavaScript because it escapes single quotes, and lift-json
        // has a bug where \' turns into \
        StringEscapeUtils.escapeJava(value)
      }
    }
  }

  def printMultiline(
    builder: StringBuilder,
    data: IVonData,
    indentation: Int):
  Unit = {
    Profiler.frame(() => {
      data match {
        case VonInt(value) => builder ++= value.toString
        case VonBool(value) => builder ++= value.toString
        case VonStr(value) => {
          builder ++= "\""
          builder ++= escape(value)
          builder ++= "\""
        }
        case VonReference(id) => vimpl()
        case o@VonObject(_, _, _) => printObjectMultiline(builder, o, indentation)
        case a@VonArray(_, _) => printArrayMultiline(builder, a, indentation)
        case VonListMap(id, members) => vimpl()
        case VonMap(id, members) => vimpl()
      }
    })
  }

  def repeatStr(str: String, n: Int): String = {
    val resultBuilder = new StringBuilder()
    (0 until n).foreach(i => {
      resultBuilder ++= str
    })
    resultBuilder.toString()
  }

  def printObjectMultiline(builder: StringBuilder, obbject: VonObject, indentation: Int): Unit = {
    val VonObject(tyype, None, unfilteredMembers) = obbject

    val members = filterMembers(unfilteredMembers)

    builder ++= printObjectStart(tyype, members.nonEmpty)
    builder ++= "\n"

    members.zipWithIndex.foreach({ case (member, index) =>
      builder ++= repeatStr("  ", indentation + 1)
      printMemberSingleLine(member, lineWidth) match {
        case None => printMemberMultiline(builder, member, indentation + 1)
        case Some(s) => builder ++= s
      }
      builder ++= (if (index == members.size - 1) "" else ",")
      if (index > 0) {
        builder ++= "\n"
      }
    })
    builder ++= printObjectEnd(members.nonEmpty)
  }

  def printObjectStart(originalType: String, hasMembers: Boolean): String = {
    val mappedType = typeNameMap.getOrElse(originalType, originalType)
    syntax match {
      case VonSyntax(_, _, true, _) => mappedType + "("
      case VonSyntax(_, _, false, _) => mappedType + (if (hasMembers) "(" else "")
      case JsonSyntax => {
        "{\"__type\": " + "\"" + escape(mappedType) + "\"" + (if (hasMembers) memberSeparator else "")
      }
    }
  }
  def printObjectEnd(hasMembers: Boolean): String = {
    syntax match {
      case VonSyntax(_, _, true, _) => ")"
      case VonSyntax(_, _, false, _) => (if (hasMembers) ")" else "")
      case JsonSyntax => "}"
    }
  }

  def printArrayStart(): String = {
    syntax match {
      case VonSyntax(_, false, _, _) => "Vector("
      case VonSyntax(_, true, _, _) => "["
      case JsonSyntax => "["
    }
  }
  def printArrayEnd(): String = {
    syntax match {
      case VonSyntax(_, false, _, _) => ")"
      case VonSyntax(_, true, _, _) => "]"
      case JsonSyntax => "]"
    }
  }

  def printArrayMultiline(builder: StringBuilder, array: VonArray, indentation: Int): Unit = {
    val VonArray(None, members) = array

    builder ++= printArrayStart()
    builder ++= "\n"
    members.zipWithIndex.foreach({ case (member, index) =>
      builder ++= repeatStr("  ", indentation + 1)
      printSingleLine(member, lineWidth) match {
        case None => printMultiline(builder, member, indentation + 1)
        case Some(s) => builder ++= s
      }
      builder ++= (if (index == members.size - 1) "" else ",")
      if (index > 0) {
        builder ++= "\n"
      }
    })
    builder ++= printArrayEnd()
  }

  def printMemberMultiline(
    builder: StringBuilder,
    member: VonMember,
    indentation: Int):
  Unit = {
    builder ++= getMemberPrefix(member)
    printMultiline(builder, member.value, indentation)
  }

  def getMemberPrefix(member: VonMember):
  // None if we failed to put it on the one line.
  String = {
    val VonMember(fieldName, _) = member
    printMemberPrefix(fieldName)
  }


  def printSingleLine(
    data: IVonData,
    // None means it will get its own line.
    // Some(n) means we should only try to print to n characters then give up.
    lineWidthRemaining: Int):
  // None if we failed to put it on the one line.
  Option[String] = {
    Profiler.frame(() => {
      data match {
        case VonInt(value) => Some(value.toString)
        case VonBool(value) => Some(value.toString)
        case VonFloat(value) => Some(value.toString)
        case VonStr(value) => {
          val escaped = escape(value)
          Some("\"" + escaped + "\"")
        }
        case VonReference(id) => vimpl()
        case o@VonObject(_, _, _) => printObjectSingleLine(o, lineWidthRemaining)
        case a@VonArray(_, _) => printArraySingleLine(a, lineWidthRemaining)
        case VonListMap(id, members) => vimpl()
        case VonMap(id, members) => vimpl()
      }
    })
  }

  def objectIsEmpty(data: IVonData): Boolean = {
    data match {
      case VonInt(value) => value == 0
      case VonStr(value) => value == ""
      case VonBool(value) => value == false
      case VonFloat(value) => value == 0
      case VonArray(_, members) => members.forall(objectIsEmpty)
      case VonObject(_, _, members) => false
    }
  }

  def filterMembers(members: Vector[VonMember]): Vector[VonMember] = {
    syntax match {
      case JsonSyntax => members
      case VonSyntax(_, _, _, true) => members
      case VonSyntax(_, _, _, false) => {
        if (members.nonEmpty && objectIsEmpty(members.last.value)) {
          filterMembers(members.init)
        } else {
          members
        }
      }
    }
  }

  def printObjectSingleLine(
    obbject: VonObject,
    lineWidthRemainingForWholeObject: Int):
    // None if we failed to put it on the one line.
  Option[String] = {
    val VonObject(tyype, None, unfilteredMembers) = obbject

    val members = filterMembers(unfilteredMembers)

    val prefix = printObjectStart(tyype, members.nonEmpty)

    val lineWidthRemainingAfterPrefix = lineWidthRemainingForWholeObject - prefix.length
    if (lineWidthRemainingAfterPrefix <= 0) {
      // identifier took up too much space, bail!
      None
    } else {
      // None means we failed to put it within lineWidthRemaining
      val initialObjectStr: Option[String] = Some(prefix)
      val maybeMembersStr =
        members.zipWithIndex.foldLeft((initialObjectStr))({
          case (None, _) => None
          case (Some(objectStrSoFar), (member, index)) => {
            val lineWidthRemaining = lineWidthRemainingForWholeObject - objectStrSoFar.length
            // If we get here, we're trying to fit things on one line.
            printMemberSingleLine(member, lineWidthRemaining) match {
              case None => None
              case Some(memberStr) => {
                val memberStrMaybeWithComma = memberStr + (if (index == members.size - 1) "" else memberSeparator)
                if (memberStrMaybeWithComma.length > lineWidthRemaining) {
                  None
                } else {
                  Some(objectStrSoFar + memberStrMaybeWithComma)
                }
              }
            }
          }
        })
      maybeMembersStr match {
        case None => None
        case Some(membersStr) => {
          val wholeObjectStr = membersStr + printObjectEnd(members.nonEmpty)
          if (wholeObjectStr.length > lineWidthRemainingForWholeObject) {
            None
          } else {
            Some(wholeObjectStr)
          }
        }
      }
    }
  }

  def printArraySingleLine(
    array: VonArray,
    lineWidthRemainingForWholeObject: Int):
  // None if we failed to put it on the one line.
  Option[String] = {
    val VonArray(id, members) = array

    val prefix = printArrayStart()

    val lineWidthRemainingAfterPrefix = lineWidthRemainingForWholeObject - prefix.length
    if (lineWidthRemainingAfterPrefix <= 0) {
      // identifier took up too much space, bail!
      None
    } else {
      // None means we failed to put it within lineWidthRemaining
      val initialObjectStr: Option[String] = Some(prefix)
      val maybeMembersStr =
        members.zipWithIndex.foldLeft((initialObjectStr))({
          case (None, _) => None
          case (Some(objectStrSoFar), (member, index)) => {
            val lineWidthRemaining = lineWidthRemainingForWholeObject - objectStrSoFar.length
            // If we get here, we're trying to fit things on one line.
            printSingleLine(member, lineWidthRemaining) match {
              case None => None
              case Some(memberStr) => {
                val memberStrMaybeWithComma = memberStr + (if (index == members.size - 1) "" else memberSeparator)
                if (memberStrMaybeWithComma.length > lineWidthRemaining) {
                  None
                } else {
                  Some(objectStrSoFar + memberStrMaybeWithComma)
                }
              }
            }
          }
        })
      maybeMembersStr match {
        case None => None
        case Some(membersStr) => {
          val wholeObjectStr = membersStr + printArrayEnd()
          if (wholeObjectStr.length > lineWidthRemainingForWholeObject) {
            None
          } else {
            Some(wholeObjectStr)
          }
        }
      }
    }
  }

  def printMemberPrefix(name: String): String = {
    syntax match {
      case VonSyntax(true, _, _, _) => name + " = "
      case VonSyntax(false, _, _, _) => ""
      case JsonSyntax => "\"" + name + "\": "
    }
  }

  def printMemberSingleLine(
    member: VonMember,
    // None means it will get its own line.
    // Some(n) means we should only try to print to n characters then give up.
    lineWidthRemaining: Int):
  // None if we failed to put it on the one line.
  Option[String] = {
    val VonMember(fieldName, value) = member

    val identifier = printMemberPrefix(fieldName)

    val lineWidthRemainingForValue = lineWidthRemaining - identifier.length
    if (lineWidthRemainingForValue <= 0) {
      // identifier took up too much space, bail!
      None
    } else {
      printSingleLine(value, lineWidthRemainingForValue) match {
        case None => {
          // We failed to put it in the remaining space, which means the entire member string is too long.
          None
        }
        case Some(valueStr) => {
          val result = identifier + valueStr
          if (result.length >= lineWidthRemaining) {
            None
          } else {
            Some(result)
          }
        }
      }
    }
  }

}
