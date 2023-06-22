package dev.vale.lexing

import dev.vale.{Accumulator, Err, Interner, Keywords, Ok, Profiler, Result, StrI, vassert, vassertSome, vfail, vimpl, vwat}

import scala.collection.mutable

class Lexer(interner: Interner, keywords: Keywords) {
  def lexAttributes(iter: LexingIterator): Result[Vector[IAttributeL], IParseError] = {
    val attributesAccum = new Accumulator[IAttributeL]()
    while ({
      iter.consumeCommentsAndWhitespace()
      lexAttribute(iter) match {
        case Err(e) => return Err(e)
        case Ok(Some(attrL)) => attributesAccum.add(attrL); true
        case Ok(None) => false
      }
    }) {}
    Ok(attributesAccum.buildArray())
  }

  def lexAttribute(iter: LexingIterator): Result[Option[IAttributeL], IParseError] = {
    // Optimize: can xor the next 8 chars into a u64 and then
    // use xor and bitwise and to do these string comparisons
    // Might be a good idea to switch to trait so it fits in 8 letters lol
    val attributeBegin = iter.getPos()
    if (iter.trySkipCompleteWord("#DeriveStructDrop")) {
      val end = iter.getPos()
      Ok(
        Some(
          MacroCallL(
            RangeL(attributeBegin, end),
            CallMacroL,
            WordLE(RangeL(attributeBegin, end), keywords.DeriveStructDrop))))
    } else if (iter.trySkipCompleteWord("#!DeriveStructDrop")) {
      val end = iter.getPos()
      Ok(
        Some(
          MacroCallL(
            RangeL(attributeBegin, end),
            DontCallMacroL,
            WordLE(RangeL(attributeBegin, end), keywords.DeriveStructDrop))))
    } else if (iter.trySkipCompleteWord("#DeriveAnonymousSubstruct")) {
        val end = iter.getPos()
        Ok(
          Some(
            MacroCallL(
              RangeL(attributeBegin, end),
              CallMacroL,
              WordLE(RangeL(attributeBegin, end), keywords.DeriveAnonymousSubstruct))))
    } else if (iter.trySkipCompleteWord("#!DeriveAnonymousSubstruct")) {
      val end = iter.getPos()
      Ok(
        Some(
          MacroCallL(
            RangeL(attributeBegin, end),
            DontCallMacroL,
            WordLE(RangeL(attributeBegin, end), keywords.DeriveAnonymousSubstruct))))
    } else if (iter.trySkipCompleteWord("#DeriveInterfaceDrop")) {
      val end = iter.getPos()
      Ok(
        Some(
          MacroCallL(
            RangeL(attributeBegin, end),
            CallMacroL,
            WordLE(RangeL(attributeBegin, end), keywords.DeriveInterfaceDrop))))
    } else if (iter.trySkipCompleteWord("#!DeriveInterfaceDrop")) {
      val end = iter.getPos()
      Ok(
        Some(
          MacroCallL(
            RangeL(attributeBegin, end),
            DontCallMacroL,
            WordLE(RangeL(attributeBegin, end), keywords.DeriveInterfaceDrop))))
    } else if (iter.trySkipCompleteWord("abstract")) {
      val end = iter.getPos()
      Ok(Some(AbstractAttributeL(RangeL(attributeBegin, end))))
    } else if (iter.trySkipCompleteWord("pure")) {
      val end = iter.getPos()
      Ok(Some(PureAttributeL(RangeL(attributeBegin, end))))
    } else if (iter.trySkipCompleteWord("additive")) {
      val end = iter.getPos()
      Ok(Some(AdditiveAttributeL(RangeL(attributeBegin, end))))
    } else if (iter.trySkipCompleteWord("extern")) {
      val maybeCustomName =
        if (iter.peek() == '(') {
          lexParend(iter) match {
            case Err(e) => return Err(e)
            case Ok(None) => vwat()
            case Ok(Some(x)) => Some(x)
          }
        } else {
          None
        }
      val end = iter.getPos()
      Ok(Some(ExternAttributeL(RangeL(attributeBegin, end), maybeCustomName)))
    } else if (iter.trySkipCompleteWord("exported")) {
      val end = iter.getPos()
      Ok(Some(ExportAttributeL(RangeL(attributeBegin, end))))
    } else if (iter.trySkipCompleteWord("weakable")) {
      val end = iter.getPos()
      Ok(Some(WeakableAttributeL(RangeL(attributeBegin, end))))
    } else if (iter.trySkipCompleteWord("sealed")) {
      val end = iter.getPos()
      Ok(Some(SealedAttributeL(RangeL(attributeBegin, end))))
    } else {
      Ok(None)
    }
  }

  def lexDenizen(iter: LexingIterator):
  Result[IDenizenL, IParseError] = {
    val denizenBegin = iter.getPos()

    val attributes =
      lexAttributes(iter) match {
        case Err(e) => return Err(e)
        case Ok(a) => a
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

    lexInterface(iter, denizenBegin, attributes) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(TopLevelInterfaceL(x))
      case Ok(None) =>
    }

    lexImpl(iter, denizenBegin, attributes) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(TopLevelImplL(x))
      case Ok(None) =>
    }

    lexImport(iter, denizenBegin, attributes) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(TopLevelImportL(x))
      case Ok(None) =>
    }

    lexExport(iter, denizenBegin, attributes) match {
      case Err(e) => return Err(e)
      case Ok(Some(x)) => return Ok(TopLevelExportAsL(x))
      case Ok(None) =>
    }

    return Err(UnrecognizedDenizenError(iter.getPos()))
  }

  def lexImpl(iter: LexingIterator, begin: Int, attributes: Vector[IAttributeL]):
  Result[Option[ImplL], IParseError] = {
    if (!iter.trySkipCompleteWord("impl")) {
      return Ok(None)
    }

    iter.consumeCommentsAndWhitespace()

    val maybeIdentifyingRunes =
      lexAngled(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    val interfaceName =
      lexIdentifier(iter) match {
        case None => return Err(BadImplInterface(iter.getPos()))
        case Some(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    val maybeInterfaceGenericArgs =
      lexAngled(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    val interface =
      maybeInterfaceGenericArgs match {
        case None => ScrambleLE(interfaceName.range, Vector(interfaceName))
        case Some(interfaceGenericArgs) => {
          ScrambleLE(
            RangeL(interfaceName.range.begin, interfaceGenericArgs.range.end),
            Vector(interfaceName, interfaceGenericArgs))
        }
      }

    iter.consumeCommentsAndWhitespace()

    if (!iter.trySkipCompleteWord("for")) {
      return Err(BadImplFor(iter.getPos()))
    }

    iter.consumeCommentsAndWhitespace()

    val structName =
      lexIdentifier(iter) match {
        case None => return Err(BadImplStruct(iter.getPos()))
        case Some(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    val maybeStructGenericArgs =
      lexAngled(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    val struct =
      maybeStructGenericArgs match {
        case None => ScrambleLE(structName.range, Vector(structName))
        case Some(structGenericArgs) => {
          ScrambleLE(
            RangeL(structName.range.begin, structGenericArgs.range.end),
            Vector(structName, structGenericArgs))
        }
      }

    iter.consumeCommentsAndWhitespace()

    val maybeRules =
      if (iter.trySkipCompleteWord("where")) {
        Some(
          lexScramble(iter, true, false, true) match {
            case Err(e) => return Err(e)
            case Ok(x) => x
          })
      } else {
        None
      }

    iter.consumeCommentsAndWhitespace()

    if (!iter.trySkip(';')) {
      return Err(BadImplEnd(iter.getPos()))
    }

    iter.consumeCommentsAndWhitespace()

    val end = iter.getPos()

    val impl =
      ImplL(
        RangeL(begin, end),
        maybeIdentifyingRunes,
        maybeRules,
        Some(struct),
        interface,
        attributes)
    Ok(Some(impl))
  }

  def lexFunction(iter: LexingIterator, begin: Int, attributes: Vector[IAttributeL]):
  Result[Option[FunctionL], IParseError] = {
    if (!iter.trySkipCompleteWord("func") && !iter.trySkipCompleteWord("funky")) {
      return Ok(None)
    }

    iter.consumeCommentsAndWhitespace()

    val nameBegin = iter.getPos()
    val name =
      lexIdentifier(iter) match {
        case None => {
          val begin = iter.getPos()
          val nameStr =
            iter.peek() match {
              case '!' => {
                iter.peek(2) match {
                  case Some("!=") => keywords.notEquals
                  case _ => return Err(BadFunctionName(iter.getPos()))
                }
              }
              case '=' => {
                iter.peek(2) match {
                  case Some("==") => {
                    iter.peek(3) match {
                      case Some("===") => keywords.tripleEquals
                      case _ => keywords.doubleEquals
                    }
                  }
                  case _ => return Err(BadFunctionName(iter.getPos()))
                }
              }
              case '>' => {
                iter.peek(2) match {
                  case Some(">=") => keywords.greaterEquals
                  case _ => keywords.greater
                }
              }
              case '<' => {
                iter.peek(2) match {
                  case Some("<=") => {
                    iter.peek(3) match {
                      case Some("<=>") => keywords.spaceship
                      case _ => keywords.lessEquals
                    }
                  }
                  case _ => keywords.less
                }
              }
              case '+' => keywords.plus
              case '-' => keywords.minus
              case '*' => keywords.asterisk
              case '/' => keywords.slash
            }
          iter.skipTo(begin + nameStr.str.length)
          val end = iter.getPos()
          WordLE(RangeL(begin, iter.getPos()), nameStr)
        }
        case Some(x) => x
      }

    val maybeGenericArgs =
      lexAngled(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    val params =
      lexParend(iter) match {
        case Err(e) => return Err(e)
        case Ok(None) => vfail()
        case Ok(Some(x)) => x
      }

    iter.consumeCommentsAndWhitespace()

    val trailingDetails =
      lexScramble(iter, true, false, true) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    val headerEnd = iter.getPos()

    val maybeBody =
      if (iter.trySkip(';')) {
        None
      } else {
        val body =
          lexCurlied(iter, false) match {
            case Err(e) => return Err(e)
            case Ok(Some(x)) => x
            case Ok(None) => return Err(BadFunctionBodyError(iter.getPos()))
          }

        Some(FunctionBodyL(body))
      }

    val end = iter.getPos()

    val header =
      FunctionHeaderL(
        RangeL(begin, headerEnd),
        name,
        attributes,
        maybeGenericArgs,
        params,
        trailingDetails)
    val func = FunctionL(RangeL(begin, end), header, maybeBody)
    Ok(Some(func))
  }

  def lexStruct(iter: LexingIterator, begin: Int, attributes: Vector[IAttributeL]):
  Result[Option[StructL], IParseError] = {
    if (!iter.trySkipCompleteWord("struct")) {
      return Ok(None)
    }

    iter.consumeCommentsAndWhitespace()

    val nameBegin = iter.getPos()
    val name =
      lexIdentifier(iter) match {
        case None => return Err(BadStructName(iter.getPos()))
        case Some(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    val maybeGenericArgs =
      lexAngled(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    val maybeMutability =
      if (!iter.peekCompleteWord("where") && iter.peek() != '{') {
        lexScramble(iter, true, true, true) match {
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
          lexScramble(iter, true, false, true) match {
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
      lexScramble(iter, false, false, false) match {
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

  def lexInterface(iter: LexingIterator, begin: Int, attributes: Vector[IAttributeL]):
  Result[Option[InterfaceL], IParseError] = {
    if (!iter.trySkipCompleteWord("interface")) {
      return Ok(None)
    }

    iter.consumeCommentsAndWhitespace()

    val nameBegin = iter.getPos()
    val name =
      lexIdentifier(iter) match {
        case None => return Err(BadInterfaceName(iter.getPos()))
        case Some(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    val maybeGenericArgs =
      lexAngled(iter) match {
        case Err(e) => return Err(e)
        case Ok(x) => x
      }

    iter.consumeCommentsAndWhitespace()

    val maybeMutability =
      if (!iter.peekCompleteWord("where") && iter.peek() != '{') {
        lexScramble(iter, true, true, true) match {
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
          lexScramble(iter, true, false, true) match {
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
      return Err(BadInterfaceContentsBegin(iter.getPos()))
    }
    val membersBegin = iter.getPos()

    iter.consumeCommentsAndWhitespace()

    val membersAcc = new Accumulator[FunctionL]()
    while (!iter.atEnd() && !iter.trySkip('}')) {
      val memberBegin = iter.getPos()
      val attributes =
        lexAttributes(iter) match {
          case Err(e) => return Err(e)
          case Ok(a) => a
        }
      lexFunction(iter, memberBegin, attributes) match {
        case Err(e) => return Err(e)
        case Ok(Some(func)) => {
          membersAcc.add(func)
        }
        case Ok(None) => return Err(BadInterfaceMember(iter.getPos()))
      }
      iter.consumeCommentsAndWhitespace()
    }
    val members = membersAcc.buildArray()

    iter.consumeCommentsAndWhitespace()

    val end = iter.getPos()

    val interface =
      InterfaceL(
        RangeL(begin, headerEnd),
        name,
        attributes,
        maybeMutability,
        maybeGenericArgs,
        maybeRules,
        RangeL(membersBegin, end),
        members)
    Ok(Some(interface))
  }

  def lexImport(iter: LexingIterator, begin: Int, attributes: Vector[IAttributeL]):
  Result[Option[ImportL], IParseError] = {
    if (!iter.trySkipCompleteWord(keywords.impoort.str)) {
      return Ok(None)
    }

    if (attributes.nonEmpty) {
      return Err(UnexpectedAttributes(iter.getPos()))
    }

    val steps = mutable.ArrayBuffer[WordLE]()
    while ({
      iter.consumeCommentsAndWhitespace()

      val stepBegin = iter.getPos()
      val name =
        if (iter.trySkip('*')) {
          WordLE(RangeL(stepBegin, iter.getPos()), keywords.asterisk)
        } else {
          lexIdentifier(iter) match {
            case None => return Err(BadImportName(iter.getPos()))
            case Some(n) => n
          }
        }
      steps += name

      iter.consumeCommentsAndWhitespace()

      if (iter.trySkip('.')) {
        true
      } else if (iter.trySkip(';')) {
        false
      } else {
        return Err(BadImportEnd(iter.getPos()))
      }
    }) {}

    val moduleName = steps.head
    val importee = steps.last
    val packageSteps = steps.init.tail
    val imporrt = ImportL(RangeL(begin, iter.getPos()), moduleName, packageSteps.toVector, importee)
    Ok(Some(imporrt))
  }

  def lexExport(iter: LexingIterator, begin: Int, attributes: Vector[IAttributeL]):
  Result[Option[ExportAsL], IParseError] = {
    if (!iter.trySkipCompleteWord(keywords.export.str)) {
      return Ok(None)
    }

    if (attributes.nonEmpty) {
      return Err(UnexpectedAttributes(iter.getPos()))
    }

    val scramble =
      lexScramble(iter, false, false, true) match {
        case Err(e) => return Err(e)
        case Ok(s) => s
      }

    iter.consumeCommentsAndWhitespace()

    if (iter.trySkip('.')) {
      true
    } else if (iter.trySkip(';')) {
      false
    } else {
      return Err(BadImportEnd(iter.getPos()))
    }

    val export = ExportAsL(RangeL(begin, iter.getPos()), scramble)
    Ok(Some(export))
  }

  def lexParend(iter: LexingIterator): Result[Option[ParendLE], IParseError] = {
    Profiler.frame(() => {
      val begin = iter.getPos()

      if (!iter.trySkip('(')) {
        return Ok(None)
      }

      iter.consumeCommentsAndWhitespace()

      val innards =
        lexScramble(iter, false, false, false) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }

      iter.consumeCommentsAndWhitespace()

      if (!iter.trySkip(')')) {
        vfail()
      }

      val end = iter.getPos()

      Ok(Some(ParendLE(RangeL(begin, end), innards)))
    })
  }

  def lexCurlied(iter: LexingIterator, stopOnOpenBrace: Boolean): Result[Option[CurliedLE], IParseError] = {
    Profiler.frame(() => {
      val begin = iter.getPos()

      if (iter.peek() == '{' && stopOnOpenBrace) {
        return Ok(None)
      }

      if (!iter.trySkip('{')) {
        return Ok(None)
      }

      iter.consumeCommentsAndWhitespace()

      val innards =
        lexScramble(iter, false, false, false) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }

      iter.consumeCommentsAndWhitespace()

      if (iter.trySkip(')')) {
        return Err(BadStartOfStatementError(iter.getPos()))
      }

      if (iter.trySkip(']')) {
        return Err(BadStartOfStatementError(iter.getPos()))
      }

      if (!iter.trySkip('}')) {
        vfail()
      }

      val end = iter.getPos()

      Ok(Some(CurliedLE(RangeL(begin, end), innards)))
    })
  }

  def lexSquared(iter: LexingIterator): Result[Option[SquaredLE], IParseError] = {
    Profiler.frame(() => {
      val begin = iter.getPos()

      if (!iter.trySkip('[')) {
        return Ok(None)
      }

      iter.consumeCommentsAndWhitespace()

      val innards =
        lexScramble(iter, false, false, false) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }

      iter.consumeCommentsAndWhitespace()

      if (!iter.trySkip(']')) {
        vfail()
      }

      val end = iter.getPos()

      Ok(Some(SquaredLE(RangeL(begin, end), innards)))
    })
  }

  def lexAngled(iter: LexingIterator): Result[Option[AngledLE], IParseError] = {
    Profiler.frame(() => {
      val begin = iter.getPos()

      if (!(iter.peek() == '<' && angleIsOpenOrClose(iter))) {
        return Ok(None)
      }
      iter.advance()

      iter.consumeCommentsAndWhitespace()

      val innards =
        lexScramble(iter, false, false, false) match {
          case Err(e) => return Err(e)
          case Ok(x) => x
        }

      iter.consumeCommentsAndWhitespace()

      if (!iter.trySkip('>')) {
        vfail()
      }

      val end = iter.getPos()

      Ok(Some(AngledLE(RangeL(begin, end), innards)))
    })
  }

  def angleIsOpenOrClose(iter: LexingIterator): Boolean = {
    iter.peek() match {
      case '<' | '>' => // continue
      case _ => return false
    }
    iter.peek(2) match {
      case Some(">=") => return false
      case Some("<=") => return false
      case _ => // continue
    }
    // If we're encountering a > after a =, then its a => like in a lambda.
    // Not a closer.
    if (iter.position - 1 >= 0 &&
        iter.code.charAt(iter.position - 1) == '=' &&
        iter.code.charAt(iter.position) == '>') {
      return false
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
    val isOpenOrClose = !whitespaceOnBothSides
    isOpenOrClose
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

  def atEnd(iter: LexingIterator, stopOnOpenBrace: Boolean, stopOnWhere: Boolean, stopOnSemicolon: Boolean): Boolean = {
    if (iter.atEnd()) {
      return true
    }
    if (stopOnWhere && iter.peekString("where")) {
      return true
    }
    iter.peek() match {
      case ')' | '}' | ']' => true
      case '{' => stopOnOpenBrace
      case ';' => stopOnSemicolon
      case '>' => angleIsOpenOrClose(iter)
      case _ => false
    }
  }

  def lexScramble(iter: LexingIterator, stopOnOpenBrace: Boolean, stopOnWhere: Boolean, stopOnSemicolon: Boolean): Result[ScrambleLE, IParseError] = {
    Profiler.frame(() => {
      val begin = iter.getPos()

      iter.consumeCommentsAndWhitespace()

      val innards = new Accumulator[INodeLE]()

      // If this encounters a ; or or ) or } a non-binary > then it should stop.
      while (!atEnd(iter, stopOnOpenBrace, stopOnWhere, stopOnSemicolon)) {
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
    })
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

    lexString(iter) match {
      case Err(e) => return Err(e)
      case Ok(Some(n)) => return Ok(n)
      case Ok(None) =>
    }

    val begin = iter.getPos()
    if (iter.peek().isUnicodeIdentifierPart) {
      Ok(vassertSome(lexIdentifier(iter)))
    } else {
      iter.advance()
      Ok(SymbolLE(RangeL(begin, iter.getPos()), iter.code.charAt(begin)))
    }
  }

  def lexIdentifier(iter: LexingIterator): Option[WordLE] = {
    val begin = iter.getPos()
    // If it was a word char, then keep eating until we reach the end of the word chars.
    while (!iter.atEnd() && iter.peek().isUnicodeIdentifierPart) {
      iter.advance()
    }
    val end = iter.getPos()
    val word = iter.code.slice(begin, end)
    if (word.isEmpty) {
      None
    } else {
      Some(WordLE(RangeL(begin, end), interner.intern(StrI(word))))
    }
  }

  def lexRegion(originalIter: LexingIterator): Option[ScrambleLE] = {
    val begin = originalIter.getPos()

    val tentativeIter = originalIter.clone()

    val name =
      lexIdentifier(tentativeIter) match {
        case None => return None
        case Some(x) => x
      }

    val symbolBegin = tentativeIter.getPos()
    if (!tentativeIter.trySkip('\'')) {
      return None
    }
    val symbolEnd = tentativeIter.getPos()

    originalIter.skipTo(tentativeIter.getPos())
    val end = originalIter.getPos()

    val symbolL = SymbolLE(RangeL(symbolBegin, symbolEnd), '\'')
    val scramble = ScrambleLE(RangeL(begin, end), Vector(name, symbolL))
    return Some(scramble)
  }

//
//  def lexStringPart(iter: LexingIterator, stringBeginPos: Int): Result[Char, IParseError] = {
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
//  def lexString(iter: LexingIterator): Result[Option[StringPT], IParseError] = {
//    val begin = iter.getPos()
//    if (!iter.trySkip(() => "^\"".r)) {
//      return Ok(None)
//    }
//    val stringSoFar = new StringBuilder()
//    while (!(iter.atEnd() || iter.trySkip(() => "^\"".r))) {
//      val c = lexStringPart(iter, begin) match { case Err(e) => return Err(e) case Ok(c) => c }
//      stringSoFar += c
//    }
//    Ok(Some(StringPT(RangeL(begin, iter.getPos()), stringSoFar.toString())))
//  }

  def lexStringEnd(iter: LexingIterator, isLongString: Boolean): Boolean = {
    iter.atEnd() || iter.trySkip(if (isLongString) "\"\"\"" else "\"")
  }

  def lexString(iter: LexingIterator): Result[Option[INodeLE], IParseError] = {
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

    while (!lexStringEnd(iter, isLongString)) {
      val stringSoFarEndPos = iter.getPos()
      lexStringPart(iter, begin) match {
        case Err(e) => return Err(e)
        case Ok(Left(c)) => {
          stringSoFar += c
        }
        case Ok(Right(expr)) => {
          if (stringSoFar.nonEmpty) {
            parts.add(StringPartLiteral(RangeL(stringSoFarBegin, stringSoFarEndPos), stringSoFar.toString()))
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
    Ok(Some(StringLE(RangeL(begin, iter.getPos()), parts.buildArray())))
  }

  def lexStringPart(iter: LexingIterator, stringBeginPos: Int):
  Result[Either[Char, ScrambleLE], IParseError] = {
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
      lexScramble(iter, false, false, false).map(Right(_))
    } else if (iter.peekString("{\n")) { // line ending in {
      iter.advance()
      Ok(Left('{'))
    } else if (iter.trySkip('{')) { // { with stuff after
      lexScramble(iter, false, false, false).map(Right(_))
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
      val c = iter.advance()
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
    Some(Integer.parseInt(str, 16))
  }

  // This is here in the lexer because it's a little awkward to identify things with i like 7i32.
  // But it's only a minor reason, we can move it to the parser if we want.
  def lexNumber(originalIter: LexingIterator): Result[Option[IParsedNumberLE], IParseError] = {
    val begin = originalIter.getPos()

    // This is so if we have an expression like arr.2.1 to get an element in
    // a 2D array, we don't interpret that 2.1 as a float.
    val isName =
      originalIter.position >= 1 && originalIter.code(originalIter.position - 1) == '.'

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
    } else if (isName) {
      // This is a name for accessing in a tuple or array, so stop here.
      Ok(Some(ParsedIntegerLE(RangeL(begin, iter.getPos()), integer, None)))
    } else {
      if (iter.trySkip('.')) {
        var mantissa = 0.0
        var digitMultiplier = 1.0

        while ( {
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
            var bits = 0L
            while ( {
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
}

//return Err(BadFunctionAfterParam(iter.getPos()))
//return Err(BadFunctionBodyError(iter.position))
//return Err(BadStartOfStatementError(iter.getPos()))