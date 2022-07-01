package dev.vale.lexing

import dev.vale.{Accumulator, Ok, Profiler, vassert, vcurious, vfail, vwat}

import scala.util.matching.Regex


case class LexingIterator(code: String, var position: Int = 0) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious();

  val comments = new Accumulator[RangeL]()

  def consumeCommentsAndWhitespace(): Unit = {
    // consumeComments will consume any whitespace that come before the comment
    consumeComments()
    consumeWhitespace()
  }

  def findWhitespaceEnd(): Int = {
    var tentativePosition = position
    // Skip whitespace
    while ({
      if (tentativePosition == code.length) {
        return tentativePosition
      }
      code.charAt(tentativePosition) match {
        case ' ' | '\n' | '\r' | '\t' => {
          tentativePosition = tentativePosition + 1
          true
        }
        case _ => false
      }
    }) {}
    tentativePosition
  }

  def consumeComments(): Unit = {
    consumeLineComments()
    consumeChevronComments()
    consumeEllipsesComments()
  }

  def getUntil(needle: Char): Option[String] = {
    val begin = position
    if (code.charAt(position) == needle) {
      return Some(code.slice(begin, position))
    }
    while (true) {
      if (position == code.length) {
        return None
      } else {
        position = position + 1
        vassert(position <= code.length)
        if (code.charAt(position) == needle) {
          return Some(code.slice(begin, position))
        }
      }
    }
    vwat()
  }

  def skipToPast(needle: Char): Unit = {
    while ({
      if (position == code.length) {
        vfail()
      } else {
        val isNeedle = code.charAt(position) == needle
        position = position + 1
        vassert(position <= code.length)
        !isNeedle
      }
    }) {}
  }

  def consumeChevronComments(): Unit = {
    val tentativePositionAfterWhitespace = findWhitespaceEnd()
    if (tentativePositionAfterWhitespace < code.length &&
      code.charAt(tentativePositionAfterWhitespace) == '«') {
      val begin = position
      position = tentativePositionAfterWhitespace + 1
      vassert(position <= code.length)
      skipToPast('»')
      comments.add(RangeL(begin, position))
      consumeComments()
    }
  }

  def consumeEllipsesComments(): Unit = {
    val tentativePositionAfterWhitespace = findWhitespaceEnd()
    if (tentativePositionAfterWhitespace < code.length &&
      code.charAt(tentativePositionAfterWhitespace) == '.' &&
      code.charAt(tentativePositionAfterWhitespace + 1) == '.' &&
      code.charAt(tentativePositionAfterWhitespace + 2) == '.') {
      val begin = position
      position = tentativePositionAfterWhitespace + 3
      vassert(position <= code.length)
      comments.add(RangeL(begin, position))
      consumeComments()
    }

    trySkip("...")
  }

  def consumeLineComments(): Unit = {
    val tentativePositionAfterWhitespace = findWhitespaceEnd()
    if (tentativePositionAfterWhitespace + 2 <= code.length &&
        code.charAt(tentativePositionAfterWhitespace) == '/' &&
        code.charAt(tentativePositionAfterWhitespace + 1) == '/') {
      val begin = tentativePositionAfterWhitespace
      position = tentativePositionAfterWhitespace + 2
      vassert(position <= code.length)
      skipToPast('\n')
      comments.add(RangeL(begin, position - 1))
      consumeComments()
    }
  }

  def advance(): Char = {
    val c = peek()
    position = position + 1
    vassert(position <= code.length)
    c
  }

  def trySkip(c: Char): Boolean = {
    if (position + 1 <= code.length) {
      // good, continue
    } else {
      return false
    }
    val wasAsExpected = (peek() == c)
    position = position + (if (wasAsExpected) 1 else 0)
    vassert(position <= code.length)
    wasAsExpected
  }

  // Optimize: replace with xor and bitwise and for small strings
  def trySkip(s: String): Boolean = {
    val wasAsExpected = peekString(s)
    position = position + (if (wasAsExpected) 1 else 0) * s.length
    vassert(position <= code.length)
    wasAsExpected
  }

  // Optimize: replace with xor and bitwise and for small strings
  // A complete word is one that doesnt have any more word characters after it
  def trySkipCompleteWord(s: String): Boolean = {
    if (position + s.length <= code.length) {
      // good, continue
    } else {
      return false
    }
    var i = 0
    var wasAsExpected = true
    while (i < s.length) {
      wasAsExpected = wasAsExpected && code.charAt(position + i) == s.charAt(i)
      i = i + 1
    }
    // Now check if we're ending the word, by peeking at the next thing
    wasAsExpected = wasAsExpected && (
      position + i == code.length ||
      !(
        code.charAt(position + 1) == '_' ||
        (code.charAt(position + i) >= 'a' && code.charAt(position + i) >= 'z') ||
        (code.charAt(position + i) >= 'A' && code.charAt(position + i) >= 'Z')))

    position = position + (if (wasAsExpected) 1 else 0) * s.length
    vassert(position <= code.length)
    wasAsExpected
  }

  override def clone(): LexingIterator = LexingIterator(code, position)

  def atEnd(): Boolean = { position >= code.length }

  def skipTo(newPosition: Int) = {
    vassert(newPosition >= position)
    position = newPosition
    vassert(position <= code.length)
  }

  def getPos(): Int = {
    position
  }

  def consumeWhitespace(): Boolean = {
    var foundAny = false
    while (!atEnd()) {
      peek() match {
        case ' ' | '\t' | '\n' | '\r' => foundAny = true
        case _ => return foundAny
      }
      advance()
    }
    return false
  }

//  private def at(regexF: () => Regex): Boolean = {
//    runRegexFrame(regexF, code).nonEmpty
//  }
//
//  def trySkip(regexF: () => Regex): Boolean = {
//    runRegexFrame(regexF, code) match {
//      case None => false
//      case Some(matchedStr) => {
//        skipTo(position + matchedStr.length)
//        true
//      }
//    }
//  }
//
//  def tryy(regexF: () => Regex): Option[String] = {
//    runRegexFrame(regexF, code) match {
//      case None => None
//      case Some(matchedStr) => {
//        skipTo(position + matchedStr.length)
//        Some(matchedStr)
//      }
//    }
//  }
//
//  def runRegexFrame(regexF: () => Regex, code: String): Option[String] = {
//    Profiler.frame(() => {
//      val regex = regexF()
//      vassert(regex.pattern.pattern().startsWith("^"))
//      regex.findFirstIn(code.slice(position, code.length))
//    })
//  }

//  def peek(): Char = {
//    if (position >= code.length)
//      return '\0'
//    else
//      return code.charAt(position)
//  }

  def peekString(s: String): Boolean = {
    var tentativePosition = position
    if (tentativePosition + s.length <= code.length) {
      // good, continue
    } else {
      return false
    }
    var i = 0
    var wasAsExpected = true
    while (i < s.length) {
      wasAsExpected = wasAsExpected && code.charAt(tentativePosition + i) == s.charAt(i)
      i = i + 1
    }
    tentativePosition = tentativePosition + (if (wasAsExpected) 1 else 0) * s.length

    wasAsExpected
  }

  def peekCompleteWord(s: String): Boolean = {
    var wasAsExpected = peekString(s)
    val posAfterWord = position + s.length

    // Now check if we're ending the word, by peeking at the next thing
    wasAsExpected =
      wasAsExpected &&
        (posAfterWord == code.length || !code.charAt(posAfterWord).isUnicodeIdentifierPart)

    wasAsExpected
  }

  def peek(): Char = {
    if (position >= code.length) '\0'
    else code.charAt(position)
  }

  def peek(n: Int): Option[String] = {
    val s = code.slice(position, position + n)
    if (s.length < n) { None } else { Some(s) }
  }

//  def trySkipIfPeekNext(
//    toConsumeF: () => Regex,
//    ifNextPeekF: () => Regex):
//  Boolean = {
//    val tentativeIter = this.clone()
//    if (!tentativeIter.trySkip(toConsumeF)) {
//      return false
//    }
//    val pos = tentativeIter.getPos()
//    if (!tentativeIter.peek(ifNextPeekF)) {
//      return false
//    }
//    this.skipTo(pos)
//    return true
//  }
}
