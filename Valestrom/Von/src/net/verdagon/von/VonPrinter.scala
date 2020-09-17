package net.verdagon.von

import net.verdagon.vale.{vfail, vimpl}
import org.apache.commons.lang.StringEscapeUtils

sealed trait ISyntax
case class VonSyntax(
  includeFieldNames: Boolean = true,
  squareBracesForArrays: Boolean = true,
  includeEmptyParams: Boolean = true,
  includeEmptyArrayMembersAtEnd: Boolean = true,
) extends ISyntax
case object JsonSyntax extends ISyntax

class VonPrinter(
    syntax: ISyntax,
    lineWidth: Int,
    typeNameMap: Map[String, String] = Map(),
    includeSpaceAfterComma: Boolean = true,
) {
  val memberSeparator = if (includeSpaceAfterComma) ", " else ","

  def print(data: IVonData): String = {
    printSingleLine(data, lineWidth) match {
      case Some(str) => str
      case None => printMultiline(data, 0)
    }
  }

  def printMultiline(
    data: IVonData,
    indentation: Int):
  // None if we failed to put it on the one line.
  String = {
    data match {
      case VonInt(value) => value.toString
      case VonBool(value) => value.toString
      case VonStr(value) => {
        syntax match {
          case VonSyntax(_, _, _, _) => "\"" + StringEscapeUtils.escapeJava(value) + "\""
          case JsonSyntax => "\"" + StringEscapeUtils.escapeJavaScript(value) + "\""
        }
      }
      case VonReference(id) => vimpl()
      case o @ VonObject(_, _, _) => printObjectMultiline(o, indentation)
      case a @ VonArray(_, _) => printArrayMultiline(a, indentation)
      case VonListMap(id, members) => vimpl()
      case VonMap(id, members) => vimpl()
    }
  }

  def repeatStr(str: String, n: Int): String = {
    var result = "";
    (0 until n).foreach(i => {
      result = result + str
    })
    result
  }

  def printObjectMultiline(obbject: VonObject, indentation: Int): String = {
    val VonObject(tyype, None, unfilteredMembers) = obbject

    val members = filterMembers(unfilteredMembers)

    printObjectStart(tyype, members.nonEmpty) + "\n" + members.zipWithIndex.map({ case (member, index) =>
      val memberStr =
        printMemberSingleLine(member, lineWidth) match {
          case None => printMemberMultiline(member, indentation + 1)
          case Some(s) => s
        }
      repeatStr("  ", indentation + 1) + memberStr + (if (index == members.size - 1) "" else ",")
    }).mkString("\n") + printObjectEnd(members.nonEmpty)
  }

  def printObjectStart(originalType: String, hasMembers: Boolean): String = {
    val mappedType = typeNameMap.getOrElse(originalType, originalType)
    syntax match {
      case VonSyntax(_, _, true, _) => mappedType + "("
      case VonSyntax(_, _, false, _) => mappedType + (if (hasMembers) "(" else "")
      case JsonSyntax => {
        "{\"__type\": " + "\"" + StringEscapeUtils.escapeJavaScript(mappedType) + "\"" + (if (hasMembers) memberSeparator else "")
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
      case VonSyntax(_, false, _, _) => "List("
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

  def printArrayMultiline(array: VonArray, indentation: Int): String = {
    val VonArray(None, members) = array

    printArrayStart() + "\n" + members.zipWithIndex.map({ case (member, index) =>
      val memberStr =
        printSingleLine(member, lineWidth) match {
          case None => printMultiline(member, indentation + 1)
          case Some(s) => s
        }
      repeatStr("  ", indentation + 1) + memberStr + (if (index == members.size - 1) "" else ",")
    }).mkString("\n") + printArrayEnd()
  }

  def printMemberMultiline(
    member: VonMember,
    indentation: Int):
  // None if we failed to put it on the one line.
  String = {
    getMemberPrefix(member) + printMultiline(member.value, indentation)
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
    data match {
      case VonInt(value) => Some(value.toString)
      case VonBool(value) => Some(value.toString)
      case VonFloat(value) => Some(value.toString)
      case VonStr(value) => {
        Some(
          syntax match {
            case VonSyntax(_, _, _, _) => "\"" + StringEscapeUtils.escapeJava(value) + "\""
            case JsonSyntax => "\"" + StringEscapeUtils.escapeJavaScript(value) + "\""
          })
      }
      case VonReference(id) => vimpl()
      case o @ VonObject(_, _, _) => printObjectSingleLine(o, lineWidthRemaining)
      case a @ VonArray(_, _) => printArraySingleLine(a, lineWidthRemaining)
      case VonListMap(id, members) => vimpl()
      case VonMap(id, members) => vimpl()
    }
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
