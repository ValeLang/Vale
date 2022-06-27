package dev.vale.lexing

import dev.vale.{Accumulator, Err, Interner, Ok, Result, StrI, vassert, vfail, vimpl, vwat}

import scala.collection.mutable

class Lexer(interner: Interner) {
  val DeriveStructDrop = interner.intern(StrI("DeriveStructDrop"))

  def lexDenizen(iter: LexingIterator):
  Result[IDenizenL, IParseError] = {
    val denizenBegin = iter.getPos()

    val attributes = {
      val attributesAccum = new Accumulator[IAttributeL]()

      while ({
        // Optimize: can xor the next 8 chars into a u64 and then
        // use xor and bitwise and to do these string comparisons
        // Might be a good idea to switch to trait so it fits in 8 letters lol
        val attributeBegin = iter.getPos()
        if (iter.trySkipCompleteWord("#DeriveStructDrop")) {
          val end = iter.getPos()
          attributesAccum.add(
            MacroCallL(
              RangeL(attributeBegin, end),
              CallMacroL,
              WordLE(RangeL(attributeBegin, end), DeriveStructDrop)))
          true
        } else if (iter.trySkipCompleteWord("#!DeriveStructDrop")) {
          val end = iter.getPos()
          attributesAccum.add(
            MacroCallL(
              RangeL(attributeBegin, end),
              DontCallMacroL,
              WordLE(RangeL(attributeBegin, end), DeriveStructDrop)))
          true
        } else if (iter.trySkipCompleteWord("abstract")) {
          val end = iter.getPos()
          attributesAccum.add(AbstractAttributeL(RangeL(attributeBegin, end)))
          true
        } else if (iter.trySkipCompleteWord("pure")) {
          val end = iter.getPos()
          attributesAccum.add(PureAttributeL(RangeL(attributeBegin, end)))
          true
        } else if (iter.trySkipCompleteWord("extern")) {
          val end = iter.getPos()
          attributesAccum.add(ExternAttributeL(RangeL(attributeBegin, end)))
          true
        } else if (iter.trySkipCompleteWord("exported")) {
          val end = iter.getPos()
          attributesAccum.add(ExportAttributeL(RangeL(attributeBegin, end)))
          true
        } else if (iter.trySkipCompleteWord("weakable")) {
          val end = iter.getPos()
          attributesAccum.add(WeakableAttributeL(RangeL(attributeBegin, end)))
          true
        } else if (iter.trySkipCompleteWord("sealed")) {
          val end = iter.getPos()
          attributesAccum.add(SealedAttributeL(RangeL(attributeBegin, end)))
          true
        } else {
          false
        }
      }) {}
      attributesAccum.buildArray()
    }

    iter.consumeCommentsAndWhitespace()

    lexFunction(iter, denizenBegin, attributes) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(TopLevelFunctionL(x))
      case Ok(None) =>
    }

    lexStruct(iter, denizenBegin, attributes) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(TopLevelStructL(x))
      case Ok(None) =>
    }

    vfail()
  }

  def lexImpl(iter: LexingIterator, begin: Int, attributes: Array[IAttributeL]):
  Result[Option[ImplL], IParseError] = {
    vimpl()
  }

  def lexFunction(iter: LexingIterator, begin: Int, attributes: Array[IAttributeL]):
  Result[Option[FunctionL], IParseError] = {
    if (!iter.trySkipCompleteWord("func")) {
      return Ok(None)
    }

    iter.consumeCommentsAndWhitespace()

    val nameBegin = iter.getPos()
    val name = lexIdentifier(iter)

    val maybeGenericArgs =
      lexAngled(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    val params =
      lexParend(iter) match {
        case Err(e) => return Err(e)
        case Ok(None) => vfail()
        case Ok(Some(x)) => x
      }

    iter.consumeCommentsAndWhitespace()

    val returnBegin = iter.getPos()
    val maybeReturn =
      if (!iter.peekCompleteWord("where") && !iter.peekCompleteWord("region") && iter.peek() != '{' && !iter.trySkip(';')) {
        if (iter.trySkipCompleteWord("infer-return")) {
          val range = RangeL(returnBegin, iter.getPos())
          FunctionReturnL(range, Some(range), None)
        } else {
          val returnType =
            lexNode(iter, true, true) match {
              case Err(e) => return Err(e)
              case Ok(x) => x
            }
          val range = RangeL(returnBegin, iter.getPos())
          FunctionReturnL(range, None, Some(returnType))
        }
      } else {
        val range = RangeL(returnBegin, iter.getPos())
        FunctionReturnL(range, None, None)
      }

    iter.consumeCommentsAndWhitespace()

    val maybeRules =
      if (iter.trySkipCompleteWord("where")) {
        Some(
          lexScramble(iter, true, false) match {
            case Err(e) => return Err(e)
            case Ok(x) => x
          })
      } else {
        None
      }

    iter.consumeCommentsAndWhitespace()

    val headerEnd = iter.getPos()

    val maybeBody =
      if (iter.trySkip(';')) {
        None
      } else {
        val maybeDefaultRegion =
          if (iter.trySkipCompleteWord("region")) {
            Some(
              lexNode(iter, true, false) match {
                case Err(e) => return Err(e)
                case Ok(x) => x
              })
          } else {
            None
          }

        val body =
          lexCurlied(iter, false) match {
            case Err(e) => return Err(e)
            case Ok(Some(x)) => x
            case Ok(None) => vfail()
          }

        Some(FunctionBodyL(maybeDefaultRegion, body))
      }

    val end = iter.getPos()

    val header =
      FunctionHeaderL(
        RangeL(begin, headerEnd),
        name,
        attributes,
        maybeGenericArgs,
        maybeRules,
        params,
        maybeReturn)
    val func = FunctionL(RangeL(begin, end), header, maybeBody)
    Ok(Some(func))
  }

  def lexStruct(iter: LexingIterator, begin: Int, attributes: Array[IAttributeL]):
  Result[Option[StructL], IParseError] = {
    if (!iter.trySkipCompleteWord("struct")) {
      return Ok(None)
    }

    iter.consumeCommentsAndWhitespace()

    val nameBegin = iter.getPos()
    val name = lexIdentifier(iter)

    iter.consumeCommentsAndWhitespace()

    val maybeGenericArgs =
      lexAngled(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    val maybeMutability =
      if (!iter.peekCompleteWord("where") && iter.peek() != '{') {
        lexScramble(iter, true, true) match {
          case Err(e) => return Err(e)
          case Ok(x) => Some(x)
        }
      } else {
        None
      }

    iter.consumeCommentsAndWhitespace()

    val maybeRules =
      if (iter.trySkipCompleteWord("where")) {
        Some(
          lexScramble(iter, true, false) match {
            case Err(e) => return Err(e)
            case Ok(x) => x
          })
      } else {
        None
      }

    iter.consumeCommentsAndWhitespace()

    val headerEnd = iter.getPos()

    val maybeDefaultRegion =
      if (iter.trySkipCompleteWord("region")) {
        Some(
          lexNode(iter, true, false) match {
            case Err(e) => return Err(e)
            case Ok(x) => x
          })
      } else {
        None
      }

    iter.consumeCommentsAndWhitespace()

    if (!iter.trySkip('{')) {
      return Err(BadStructContentsBegin(iter.getPos()))
    }
    val membersBegin = iter.getPos()

    iter.consumeCommentsAndWhitespace()

    val membersAcc = new Accumulator[ScrambleLE]()

    val contents =
      lexScramble(iter, false, false) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    if (!iter.trySkip('}')) {
      return Err(BadStructContentsEnd(iter.getPos()))
    }

    iter.consumeCommentsAndWhitespace()

    val end = iter.getPos()

    val struct =
      StructL(
        RangeL(begin, headerEnd),
        name,
        attributes,
        maybeMutability,
        maybeGenericArgs,
        maybeRules,
        contents)
    Ok(Some(struct))
  }

  def lexParend(iter: LexingIterator): Result[Option[ParendLE], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip('(')) {
      return Ok(None)
    }

    iter.consumeCommentsAndWhitespace()

    val innards =
      lexScramble(iter, false, false) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    if (!iter.trySkip(')')) {
      vfail()
    }

    val end = iter.getPos()

    Ok(Some(ParendLE(RangeL(begin, end), innards)))
  }

  def lexCurlied(iter: LexingIterator, stopOnOpenBrace: Boolean): Result[Option[CurliedLE], IParseError] = {
    val begin = iter.getPos()

    if (iter.peek() == '{' && stopOnOpenBrace) {
      return Ok(None)
    }

    if (!iter.trySkip('{')) {
      return Ok(None)
    }

    iter.consumeCommentsAndWhitespace()

    val innards =
      lexScramble(iter, false, false) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    if (!iter.trySkip('}')) {
      vfail()
    }

    val end = iter.getPos()

    Ok(Some(CurliedLE(RangeL(begin, end), innards)))
  }

  def lexSquared(iter: LexingIterator): Result[Option[SquaredLE], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip('[')) {
      return Ok(None)
    }

    iter.consumeCommentsAndWhitespace()

    val innards =
      lexScramble(iter, false, false) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    if (!iter.trySkip(']')) {
      vfail()
    }

    val end = iter.getPos()

    Ok(Some(SquaredLE(RangeL(begin, end), innards)))
  }

  def lexAngled(iter: LexingIterator): Result[Option[AngledLE], IParseError] = {
    val begin = iter.getPos()

    if (!iter.trySkip('<')) {
      return Ok(None)
    }

    iter.consumeCommentsAndWhitespace()

    val innards =
      lexScramble(iter, false, false) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    if (!iter.trySkip('>')) {
      vfail()
    }

    val end = iter.getPos()

    Ok(Some(AngledLE(RangeL(begin, end), innards)))

    //    // We need one side of the chevron to not have spaces, to avoid ambiguity with
    //    // the binary < operator.
    //    if (iter.peek(() => "^\\s+<\\s+")) {
    //      // This is a binary < operator, because there's space on both sides.
    //      return Ok(None)
    //    } else if (iter.peek(() => "^\\s*<=")) {
    //      // This is a binary <= operator, bail.
    //      return Ok(None)
    //    } else if (iter.peek(() => "^\\s+<[\\S]")) {
    //      // This is a template call like:
    //      //   x = myFunc <int> ();
    //      val y = iter.trySkipWord("\\s+<")
    //      vassert(y)
    //      // continue
    //    } else if (iter.trySkipWord("<\\s*")) {
    //      // continue
    //    } else {
    //      // Nothing we recognize, bail out.
    //      return Ok(None)
    //    }
    //    iter.consumeWhitespace()
    //    val elements = new mutable.ArrayBuffer[ITemplexPT]()
    //    while (!iter.trySkipWord("\\>")) {
    //      val expr =
    //        new TemplexParser().parseTemplex(iter) match {
    //          case Err(e) => return Err(e)
    //          case Ok(expr) => expr
    //        }
    //      elements += expr
    //      iter.consumeWhitespace()
    //      iter.trySkipWord(",")
    //      iter.consumeWhitespace()
    //    }
    //
    //    Ok(Some(elements.toVector))
  }

  def isAtBinaryChevron(iter: LexingIterator): Boolean = {
    iter.peek() match {
      case '<' | '>' => // continue
      case _ => return false
    }
    val whitespaceBefore =
      iter.position - 1 >= 0 &&
        (iter.code.charAt(iter.position - 1) match {
          case ' ' | '\t' | '\n' | '\r' => true
          case _ => false
        })
    val whitespaceAfter =
      iter.position + 1 < iter.code.length &&
        (iter.code.charAt(iter.position + 1) match {
          case ' ' | '\t' | '\n' | '\r' => true
          case _ => false
        })
    val whitespaceOnBothSides = whitespaceBefore && whitespaceAfter
    val isBinaryOperator = whitespaceOnBothSides
    isBinaryOperator
  }

//  def lexSemicolonSeparatedList(iter: LexingIterator, stopOnOpenBrace: Boolean, stopOnWhere: Boolean): Result[SemicolonSeparatedListLE, IParseError] = {
//    val begin = iter.getPos()
//
//    // If this encounters a ; or or ) or } a non-binary > then it should stop.
//    iter.consumeCommentsAndWhitespace()
//
//    val elements = new Accumulator[ScrambleLE]()
//    var trailingSemicolon = false
//
//    while (!atEnd(iter, stopOnOpenBrace, stopOnWhere) && iter.trySkip(';')) {
//      iter.consumeCommentsAndWhitespace()
//
//      if (atEnd(iter, stopOnOpenBrace, stopOnWhere)) {
//        trailingSemicolon = true
//      } else {
//        val node =
//          lexScramble(iter, stopOnOpenBrace, stopOnWhere) match {
//            case Err(e) => return Err(e)
//            case Ok(x) => x
//          }
//        elements.add(node)
//      }
//
//      iter.consumeCommentsAndWhitespace()
//    }
//
//    val end = iter.getPos()
//
//    Ok(SemicolonSeparatedListLE(RangeL(begin, end), elements.buildArray(), trailingSemicolon))
//  }

//  def lexCommaSeparatedList(iter: LexingIterator, stopOnOpenBrace: Boolean, stopOnWhere: Boolean): Result[CommaSeparatedListLE, IParseError] = {
//    val begin = iter.getPos()
//
//    // If this encounters a ; or or ) or } a non-binary > then it should stop.
//    iter.consumeCommentsAndWhitespace()
//
//    val innards = new Accumulator[ScrambleLE]()
//    var trailingComma = false
//
//    while (!atEnd(iter, stopOnOpenBrace, stopOnWhere) && !iter.trySkip(',')) {
//      iter.consumeCommentsAndWhitespace()
//
//      if (atEnd(iter, stopOnOpenBrace, stopOnWhere)) {
//        trailingComma = true
//      } else {
//        val node =
//          lexScramble(iter, stopOnOpenBrace, stopOnWhere) match {
//            case Err(e) => return Err(e)
//            case Ok(x) => x
//          }
//        innards.add(node)
//      }
//
//      iter.consumeCommentsAndWhitespace()
//    }
//
//    val end = iter.getPos()
//
//    Ok(CommaSeparatedListLE(RangeL(begin, end), innards.buildArray(), trailingComma))
//  }

  def atEnd(iter: LexingIterator, stopOnOpenBrace: Boolean, stopOnWhere: Boolean): Boolean = {
    if (iter.atEnd()) {
      return true
    }
    if (stopOnWhere && iter.peekString("where")) {
      return true
    }
    iter.peek() match {
      case ')' | '}' | ']' => true
      case '{' => stopOnOpenBrace
      case '>' => !isAtBinaryChevron(iter)
      case _ => false
    }
  }

  def lexScramble(iter: LexingIterator, stopOnOpenBrace: Boolean, stopOnWhere: Boolean): Result[ScrambleLE, IParseError] = {
    val begin = iter.getPos()

    // If this encounters a ; or or ) or } a non-binary > then it should stop.
    iter.consumeCommentsAndWhitespace()

    val innards = new Accumulator[INodeLE]()

    while (!atEnd(iter, stopOnOpenBrace, stopOnWhere)) {
      val node =
        lexNode(iter, stopOnOpenBrace, stopOnWhere) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }

      innards.add(node)

      iter.consumeCommentsAndWhitespace()
    }

    val end = iter.getPos()

    Ok(ScrambleLE(RangeL(begin, end), innards.buildArray()))
  }


  def lexNode(iter: LexingIterator, stopOnOpenBrace: Boolean, stopOnWhere: Boolean): Result[INodeLE, IParseError] = {
    lexAngled(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }
    lexSquared(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }
    lexCurlied(iter, stopOnOpenBrace) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }
    lexParend(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(x)
      case Ok(None) =>
    }
    lexAtom(iter, stopOnWhere) match {
      case Err(e) => Err(e)
      case Ok(x) => Ok(x)
    }
  }

  // Optimize: we can make a perfect hash map beforehand based off of the u64s
  // of all the keywords. Then those can point at the pre-interned things?
  def lexAtom(iter: LexingIterator, stopOnWhere: Boolean): Result[INodeLE, IParseError] = {
    vassert(!(stopOnWhere && iter.trySkipCompleteWord("where")))

    lexNumber(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(n)) => return Ok(n)
      case Ok(None) =>
    }

    val begin = iter.getPos()
    if (iter.peek().isUnicodeIdentifierPart) {
      Ok(lexIdentifier(iter))
    } else {
      iter.advance()
      Ok(SymbolLE(RangeL(begin, iter.getPos()), iter.code.charAt(begin)))
    }
  }

  def lexIdentifier(iter: LexingIterator): WordLE = {
    val begin = iter.getPos()
    // If it was a word char, then keep eating until we reach the end of the word chars.
    while (!iter.atEnd() && iter.peek().isUnicodeIdentifierPart) {
      iter.advance()
    }
    val end = iter.getPos()
    val word = iter.code.slice(begin, end)
    WordLE(RangeL(begin, end), interner.intern(StrI(word)))
  }

//
//  def parseStringPart(iter: LexingIterator, stringBeginPos: Int): Result[Char, IParseError] = {
//    if (iter.trySkip(() => "^\\\\".r)) {
//      if (iter.trySkip(() => "^r".r) || iter.trySkip(() => "^\\r".r)) {
//        Ok('\r')
//      } else if (iter.trySkip(() => "^t".r)) {
//        Ok('\t')
//      } else if (iter.trySkip(() => "^n".r) || iter.trySkip(() => "^\\n".r)) {
//        Ok('\n')
//      } else if (iter.trySkip(() => "^\\\\".r)) {
//        Ok('\\')
//      } else if (iter.trySkip(() => "^\"".r)) {
//        Ok('\"')
//      } else if (iter.trySkip(() => "^/".r)) {
//        Ok('/')
//      } else if (iter.trySkip(() => "^\\{".r)) {
//        Ok('{')
//      } else if (iter.trySkip(() => "^\\}".r)) {
//        Ok('}')
//      } else if (iter.trySkip(() => "^u".r)) {
//        val num =
//          StringParser.parseFourDigitHexNum(iter) match {
//            case None => {
//              return Err(BadUnicodeChar(iter.getPos()))
//            }
//            case Some(x) => x
//          }
//        Ok(num.toChar)
//      } else {
//        Ok(iter.tryy(() => "^.".r).get.charAt(0))
//      }
//    } else {
//      val c =
//        iter.tryy(() => "^(.|\\n)".r) match {
//          case None => {
//            return Err(BadStringChar(stringBeginPos, iter.getPos()))
//          }
//          case Some(x) => x
//        }
//      Ok(c.charAt(0))
//    }
//  }
//
//  def parseString(iter: LexingIterator): Result[Option[StringPT], IParseError] = {
//    val begin = iter.getPos()
//    if (!iter.trySkip(() => "^\"".r)) {
//      return Ok(None)
//    }
//    val stringSoFar = new StringBuilder()
//    while (!(iter.atEnd() || iter.trySkip(() => "^\"".r))) {
//      val c = parseStringPart(iter, begin) match { case Err(e) => return Err(e) case Ok(c) => c }
//      stringSoFar += c
//    }
//    Ok(Some(StringPT(RangeL(begin, iter.getPos()), stringSoFar.toString())))
//  }

  def parseStringEnd(iter: LexingIterator, isLongString: Boolean): Boolean = {
    iter.atEnd() || iter.trySkip(if (isLongString) "^\"\"\"" else "^\"")
  }

  def parseString(iter: LexingIterator): Result[Option[INodeLE], IParseError] = {
    val begin = iter.getPos()
    val isLongString =
      if (iter.trySkip("\"\"\"")) {
        true
      } else if (iter.trySkip("\"")) {
        false
      } else {
        return Ok(None)
      }

    val parts = new Accumulator[StringPart]()
    var stringSoFarBegin = iter.getPos()
    var stringSoFar = new StringBuilder()

    while (!parseStringEnd(iter, isLongString)) {
      parseStringPart(iter, begin) match {
        case Err(e) => return Err(e)
        case Ok(Left(c)) => {
          stringSoFar += c
        }
        case Ok(Right(expr)) => {
          if (stringSoFar.nonEmpty) {
            parts.add(StringPartLiteral(RangeL(stringSoFarBegin, iter.getPos()), stringSoFar.toString()))
            stringSoFar.clear()
          }
          parts.add(StringPartExpr(expr))
          if (!iter.trySkip('}')) {
            return Err(BadStringInterpolationEnd(iter.getPos()))
          }
          stringSoFarBegin = iter.getPos()
        }
      }
    }
    if (stringSoFar.nonEmpty) {
      parts.add(StringPartLiteral(RangeL(stringSoFarBegin, iter.getPos()), stringSoFar.toString()))
      stringSoFar.clear()
    }
    Ok(Some(StringLE(RangeL(stringSoFarBegin, iter.getPos()), parts.buildArray())))
  }

  def parseStringPart(iter: LexingIterator, stringBeginPos: Int):
  Result[Either[Char, INodeLE], IParseError] = {
    // Normally, we interpret as an expression anything after a `{`. However, we handle
    // newlines in a special way.
    // We don't want to interpolate { if its followed by a newline, such as in
    //   """
    //     if true {
    //       3 + 4
    //     }
    //   """
    // but if they want to interpolate inside, they can use {\ like
    //   """
    //     if true {\
    //       3 + 4
    //     }
    //   """
    // which becomes
    //   """
    //     if true 7
    //   """
    // The newline is because we dont want to interpolate when its a { then a newline.
    // If they want that, then they should do {\
    if (iter.trySkip("{\\\n")) { // line ending in {\
      lexScramble(iter, false, false).map(Right(_))
    } else if (iter.trySkip("{\n")) { // line ending in {
      Ok(Left('{'))
    } else if (iter.trySkip('\n')) { // { with stuff after
      lexScramble(iter, false, false).map(Right(_))
    } else if (iter.trySkip('\\')) {
      if (iter.trySkip('r') || iter.trySkip('\r')) {
        Ok(Left('\r'))
      } else if (iter.trySkip('t')) {
        Ok(Left('\t'))
      } else if (iter.trySkip('n') || iter.trySkip('\n')) {
        Ok(Left('\n'))
      } else if (iter.trySkip('\\')) {
        Ok(Left('\\'))
      } else if (iter.trySkip('"')) {
        Ok(Left('\"'))
      } else if (iter.trySkip('/')) {
        Ok(Left('/'))
      } else if (iter.trySkip('{')) {
        Ok(Left('{'))
      } else if (iter.trySkip('}')) {
        Ok(Left('}'))
      } else if (iter.trySkip('u')) {
        val num =
          parseFourDigitHexNum(iter) match {
            case None => {
              return Err(BadUnicodeChar(iter.getPos()))
            }
            case Some(x) => x
          }
        Ok(Left(num.toChar))
      } else {
        Ok(Left(iter.advance()))
      }
    } else {
      val c = iter.peek()
      Ok(Left(c))
    }
  }

  def parseFourDigitHexNum(iter: LexingIterator): Option[Int] = {
    val str =
      iter.peek(4) match {
        case None => return None
        case Some(s) => s
      }
    var i = 0;
    while (i < 4) {
      val c = iter.advance()
      if (c >= '0' && c <= '9') {
      } else if (c >= 'a' && c <= 'f') {
      } else if (c >= 'A' && c <= 'F') {
      } else {
        return None
      }
      i = i + 1;
    }
    iter.skipTo(iter.position + 4)
    Some(Integer.parseInt(str, 16))
  }

  // This is here in the lexer because it's a little awkward to identify things with i like 7i32.
  // But it's only a minor reason, we can move it to the parser if we want.
  def lexNumber(originalIter: LexingIterator): Result[Option[IParsedNumberLE], IParseError] = {
    val begin = originalIter.getPos()

    val tentativeIter = originalIter.clone()

    val negative = tentativeIter.trySkip("-")

    val peeked = tentativeIter.peek()
    if (peeked < '0' || peeked > '9') {
      return Ok(None)
    }

    originalIter.skipTo(tentativeIter.position)
    val iter = originalIter

    var digitsConsumed = 0
    var integer = 0L

    while ({
      val c = iter.peek()
      if (c >= '0' && c <= '9') {
        integer = integer * 10L + (c.toInt - '0')
        digitsConsumed += 1
        iter.advance()
        true
      } else {
        false
      }
    }) {}
    vassert(digitsConsumed > 0)

    if (iter.peekString("..")) {
      // This is followed by the range operator, so just stop here.
      Ok(Some(ParsedIntegerLE(RangeL(begin, iter.getPos()), integer, None)))
    } else if (iter.trySkip('.')) {
      var mantissa = 0.0
      var digitMultiplier = 1.0

      while ({
        val c = iter.peek()
        if (c >= '0' && c <= '9') {
          digitMultiplier = digitMultiplier * 0.1
          mantissa = mantissa + (c.toInt - '0') * digitMultiplier
          iter.advance()
          true
        } else {
          false
        }
      }) {}

      if (iter.trySkip("f")) {
        vimpl()
      }

      val result = (integer + mantissa) * (if (negative) -1 else 1)
      Ok(Some(ParsedDoubleLE(RangeL(begin, iter.getPos()), result, None)))
    } else {
      val bits =
        if (iter.trySkip("i")) {
          var bits = 0
          while ({
            val c = iter.peek()
            if (c >= '0' && c <= '9') {
              bits = bits * 10 + (c.toInt - '0')
              iter.advance()
              true
            } else {
              false
            }
          }) {}
          vassert(bits > 0)
          Some(bits)
        } else {
          None
        }

      val result = integer * (if (negative) -1 else 1)

      Ok(Some(ParsedIntegerLE(RangeL(begin, iter.getPos()), result, bits)))
    }
  }
}

//return Err(BadFunctionAfterParam(iter.getPos()))
//return Err(BadFunctionBodyError(iter.position))
//return Err(BadStartOfStatementError(iter.getPos()))