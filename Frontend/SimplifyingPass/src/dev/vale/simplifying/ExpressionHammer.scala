package dev.vale.simplifying

import dev.vale.finalast._
import dev.vale._
import dev.vale.finalast._
import dev.vale.instantiating.ast._

import scala.collection.immutable.Map

class ExpressionHammer(
    keywords: Keywords,
    typeHammer: TypeHammer,
    nameHammer: NameHammer,
    structHammer: StructHammer,
    functionHammer: FunctionHammer) {
  val blockHammer = new BlockHammer(this, typeHammer)
  val loadHammer = new LoadHammer(keywords, typeHammer, nameHammer, structHammer, this)
  val letHammer = new LetHammer(typeHammer, nameHammer, structHammer, this, loadHammer)
  val mutateHammer = new MutateHammer(keywords, typeHammer, nameHammer, structHammer, this)

  // stackHeight is the number of locals that have been declared in ancestor
  // blocks and previously in this block. It's used to figure out the index of
  // a newly declared local.
  // Returns:
  // - result register id
  // - deferred expressions, to move to after the enclosing call. head is put first after call.
  def translate(
      hinputs: HinputsI,
      hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      expr2: ExpressionI
  ): (ExpressionH[KindHT], Vector[ExpressionI]) = {
    expr2 match {
      case ConstantIntIE(numTemplata, bits) => {
        val num = numTemplata
        (ConstantIntH(num, bits), Vector.empty)
      }
      case VoidLiteralIE() => {
        val constructH = ConstantVoidH()
        (constructH, Vector.empty)
      }
      case ConstantStrIE(value) => {
        (ConstantStrH(value), Vector.empty)
      }
      case ConstantFloatIE(value) => {
        (ConstantF64H(value), Vector.empty)
      }
      case ConstantBoolIE(value) => {
        (ConstantBoolH(value), Vector.empty)
      }
      case let2 @ LetNormalIE(_, _, _) => {
        val letH =
          letHammer.translateLet(hinputs, hamuts, currentFunctionHeader, locals, let2)
        (letH, Vector.empty)
      }
      case let2 @ RestackifyIE(_, _, _) => {
        val letH =
          letHammer.translateRestackify(hinputs, hamuts, currentFunctionHeader, locals, let2)
        (letH, Vector.empty)
      }
      case let2 @ LetAndLendIE(_, _, _, _) => {
        val borrowAccess =
          letHammer.translateLetAndPoint(hinputs, hamuts, currentFunctionHeader, locals, let2)
        (borrowAccess, Vector.empty)
      }
      case des2 @ DestroyIE(_, _, _) => {
        val destroyH =
            letHammer.translateDestroy(hinputs, hamuts, currentFunctionHeader, locals, des2)
        // Compiler destructures put things in local variables (even though hammer itself
        // uses registers internally to make that happen).
        // Since all the members landed in locals, we still need something to ret, so we
        // return a void.
        (destroyH, Vector.empty)
      }
      case des2 @ DestroyStaticSizedArrayIntoLocalsIE(_, _, _) => {
        val destructureH =
            letHammer.translateDestructureStaticSizedArray(hinputs, hamuts, currentFunctionHeader, locals, des2)
        // Compiler destructures put things in local variables (even though hammer itself
        // uses registers internally to make that happen).
        // Since all the members landed in locals, we still need something to ret, so we
        // return a void.
        (destructureH, Vector.empty)
      }
      case unlet2 @ UnletIE(_, _) => {
        val valueAccess =
          letHammer.translateUnlet(
            hinputs, hamuts, currentFunctionHeader, locals, unlet2)
        (valueAccess, Vector.empty)
      }
      case mutate2 @ MutateIE(_, _, _) => {
        val newEmptyPackStructNodeHE =
          mutateHammer.translateMutate(hinputs, hamuts, currentFunctionHeader, locals, mutate2)
        (newEmptyPackStructNodeHE, Vector.empty)
      }
      case b @ MutabilifyIE(_, _) => {
        val pureH =
          blockHammer.translateMutabilify(hinputs, hamuts, currentFunctionHeader, locals, b)
        (pureH, Vector.empty)
      }
      case b@ImmutabilifyIE(_, _) => {
        val pureH =
          blockHammer.translateImmutabilify(hinputs, hamuts, currentFunctionHeader, locals, b)
        (pureH, Vector.empty)
      }
      case b @ BlockIE(_, _) => {
        val blockH =
          blockHammer.translateBlock(hinputs, hamuts, currentFunctionHeader, locals, b)
        (blockH, Vector.empty)
      }
      case call2 @ FunctionCallIE(callableExpr, args, _) => {
        val access =
          translateFunctionPointerCall(
            hinputs, hamuts, currentFunctionHeader, locals, callableExpr, args, call2.result)
        (access, Vector.empty)
      }
      case PreCheckBorrowIE(inner) => {
        val (innerHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, inner)
        (PreCheckBorrowH(innerHE), deferreds)
      }
      case InterfaceFunctionCallIE(superFunctionPrototype, virtualParamIndex, argsExprs2, resultType2) => {
        val access =
          translateInterfaceFunctionCall(
            hinputs, hamuts, currentFunctionHeader, locals, superFunctionPrototype, virtualParamIndex, resultType2, argsExprs2)
        (access, Vector.empty)
      }

      case ConsecutorIE(exprsIE, _) => {
        // If there's an expression returning a Never, then remove all the expressions after that.
        // See BRCOBS.
        val exprsHE =
          exprsIE.foldLeft(Vector[ExpressionH[KindHT]]())({
            case (previousHE, nextIE) => {
              previousHE.lastOption.map(_.resultType.kind) match {
                case Some(NeverHT(_)) => previousHE
                case _ => {
                  val (nextHE, nextDeferreds) =
                    translate(hinputs, hamuts, currentFunctionHeader, locals, nextIE);
                  val nextExprWithDeferredsHE =
                    translateDeferreds(
                      hinputs, hamuts, currentFunctionHeader, locals, nextHE, nextDeferreds)
                  previousHE :+ nextExprWithDeferredsHE
                }
              }
            }
          })
        exprsHE.lastOption.map(_.resultType.kind) match {
          case Some(NeverHT(_)) => {
            return (Hammer.consecrash(locals, exprsHE), Vector.empty)
          }
          case _ =>
        }

        (Hammer.consecutive(exprsHE), Vector.empty)
      }

      case ArrayLengthIE(arrayExpr2) => {
        val (resultHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

        val lengthResultNode = ArrayLengthH(resultHE);

        val arrayLengthAndDeferredsExprH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, lengthResultNode, deferreds)

        (arrayLengthAndDeferredsExprH, Vector.empty)
      }

      case RuntimeSizedArrayCapacityIE(arrayExpr2) => {
        val (resultHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

        val lengthResultNode = ArrayCapacityH(resultHE);

        val arrayLengthAndDeferredsExprH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, lengthResultNode, deferreds)

        (arrayLengthAndDeferredsExprH, Vector.empty)
      }

      case LockWeakIE(innerExpr2, resultOptBorrowType2, someConstructor, noneConstructor, _, _, _) => {
        val (resultHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr2);
        val (resultOptBorrowTypeH) =
          typeHammer.translateCoord(hinputs, hamuts, resultOptBorrowType2)

        val someConstructorH =
          functionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, someConstructor);
        val noneConstructorH =
          functionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, noneConstructor);

        val resultNode =
          LockWeakH(
            resultHE,
            resultOptBorrowTypeH.expectInterfaceCoord(),
            someConstructorH.prototype,
            noneConstructorH.prototype);
        (resultNode, deferreds)
      }

      case TupleIE(exprs, resultType) => {
        val (resultsHE, deferreds) =
          translateExpressionsUntilNever(
            hinputs, hamuts, currentFunctionHeader, locals, exprs);
        // Don't evaluate anything that can't ever be run, see BRCOBS
        resultsHE.lastOption.map(_.resultType.kind) match {
          case Some(NeverHT(_)) => {
            return (Hammer.consecrash(locals, resultsHE), Vector())
          }
          case _ =>
        }

        val resultStructI = resultType.kind match { case s @ StructIT(_) => s }
        val (underlyingStructRefH) =
          structHammer.translateStructI(hinputs, hamuts, resultStructI);
        val (resultReference) =
          typeHammer.translateCoord(hinputs, hamuts, resultType)
        vassert(resultReference.kind == underlyingStructRefH)

        val structDefH = hamuts.structTToStructDefH(resultStructI)
        vassert(resultsHE.size == structDefH.members.size)
        val newStructNode =
          NewStructH(
            resultsHE,
            structDefH.members.map(_.name),
            resultReference.expectStructCoord())
        // Export locals from inside the pack

        val newStructAndDeferredsExprH =
            translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, Vector.empty)
      }

      case StaticArrayFromValuesIE(exprs, arrayReference2, arrayType2) => {
        val (resultsHE, deferreds) =
          translateExpressionsUntilNever(hinputs, hamuts, currentFunctionHeader, locals, exprs);
        // Don't evaluate anything that can't ever be run, see BRCOBS
        resultsHE.lastOption.map(_.resultType.kind) match {
          case Some(NeverHT(_)) => {
            return (Hammer.consecrash(locals, resultsHE), Vector())
          }
          case _ =>
        }
        val (underlyingArrayH) =
          typeHammer.translateStaticSizedArray(hinputs, hamuts, arrayType2);

        val (arrayReferenceH) =
          typeHammer.translateCoord(hinputs, hamuts, arrayReference2)
        vassert(arrayReferenceH.kind == underlyingArrayH)

        val newStructNode =
          NewArrayFromValuesH(
            arrayReferenceH.expectStaticSizedArrayCoord(),
            resultsHE)

        val newStructAndDeferredsExprH =
        translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, Vector.empty)
      }

      case ConstructIE(structIT, resultType2, memberExprs) => {
        val (membersHE, deferreds) =
          translateExpressionsUntilNever(hinputs, hamuts, currentFunctionHeader, locals, memberExprs);
        // Don't evaluate anything that can't ever be run, see BRCOBS
        membersHE.lastOption.map(_.resultType.kind) match {
          case Some(NeverHT(_)) => {
            return (Hammer.consecrash(locals, membersHE), Vector())
          }
          case _ =>
        }

        val (resultTypeH) =
          typeHammer.translateCoord(hinputs, hamuts, resultType2)


        val structDefH = hamuts.structTToStructDefH(structIT)
        vassert(membersHE.size == structDefH.members.size)
        membersHE.zip(structDefH.members).foreach({ case (memberHE, memberH ) =>
          vassert(memberHE.resultType == memberH.tyype)
        })
        val newStructNode =
          NewStructH(
            membersHE,
            structDefH.members.map(_.name),
            resultTypeH.expectStructCoord())

        val newStructAndDeferredsExprH =
            translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, Vector.empty)
      }

      case load2 @ SoftLoadIE(_, _, _) => {
        val (loadedAccessH, deferreds) =
          loadHammer.translateLoad(hinputs, hamuts, currentFunctionHeader, locals, load2)
        (loadedAccessH, deferreds)
      }

      case lookup2 @ LocalLookupIE(AddressibleLocalVariableI(_, _, _), _) => {
        vregionmut()
        val loadBoxAccess =
          loadHammer.translateLocalAddress(hinputs, hamuts, currentFunctionHeader, locals, lookup2)
        (loadBoxAccess, Vector.empty)
      }

      case lookup2 @ AddressMemberLookupIE(_, _, _, _) => {
        val (loadBoxAccess, deferreds) =
          loadHammer.translateMemberAddress(hinputs, hamuts, currentFunctionHeader, locals, lookup2)
        (loadBoxAccess, deferreds)
      }

      case if2 @ IfIE(_, _, _, _) => {
        val maybeAccess =
          translateIf(hinputs, hamuts, currentFunctionHeader, locals, if2)
        (maybeAccess, Vector.empty)
      }

      case prsaIE @ PushRuntimeSizedArrayIE(_, _) => {

        val PushRuntimeSizedArrayIE(arrayIE, newcomerIE) = prsaIE;

        val (arrayHE, arrayDeferreds) =
          translate(
            hinputs, hamuts, currentFunctionHeader, locals, arrayIE);
        val rsaHE = arrayHE.expectRuntimeSizedArrayAccess()
        val rsaDefH = hamuts.getRuntimeSizedArray(rsaHE.resultType.kind)

        val (newcomerHE, newcomerDeferreds) =
          translate(
            hinputs, hamuts, currentFunctionHeader, locals, newcomerIE);

        vassert(newcomerHE.resultType == rsaDefH.elementType)

        val constructArrayCallNode = PushRuntimeSizedArrayH(rsaHE, newcomerHE)

        val access =
          translateDeferreds(
            hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, arrayDeferreds ++ newcomerDeferreds)

        (access, Vector.empty)
      }

      case prsaIE @ PopRuntimeSizedArrayIE(_, _) => {
        val PopRuntimeSizedArrayIE(arrayIE, _) = prsaIE;

        val (arrayHE, arrayDeferreds) =
          translate(
            hinputs, hamuts, currentFunctionHeader, locals, arrayIE);
        val rsaHE = arrayHE.expectRuntimeSizedArrayAccess()
        val rsaDefH = hamuts.getRuntimeSizedArray(rsaHE.resultType.kind)

        val constructArrayCallNode = PopRuntimeSizedArrayH(rsaHE, rsaDefH.elementType)

        val access =
          translateDeferreds(
            hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, arrayDeferreds)

        (access, Vector.empty)
      }

      case nmrsaIE @ NewMutRuntimeSizedArrayIE(_, _, _) => {
        val access =
          translateNewMutRuntimeSizedArray(
            hinputs, hamuts, currentFunctionHeader, locals, nmrsaIE)
        (access, Vector.empty)
      }

      case nirsaIE @ NewImmRuntimeSizedArrayIE(_, _, _, _, _) => {
        val access =
          translateNewImmRuntimeSizedArray(
            hinputs, hamuts, currentFunctionHeader, locals, nirsaIE)
        (access, Vector.empty)
      }

      case ca2 @ StaticArrayFromCallableIE(_, _, _, _) => {
        val access =
          translateStaticArrayFromCallable(
            hinputs, hamuts, currentFunctionHeader, locals, ca2)
        (access, Vector.empty)
      }

      case ReinterpretIE(innerExpr, resultType2, _) => {
        // Check types; it's overkill because reinterprets are rather scary.
        val innerExprResultType2 = innerExpr.result
        val (innerExprResultTypeH) = typeHammer.translateCoord(hinputs, hamuts, innerExprResultType2);
        val (resultTypeH) = typeHammer.translateCoord(hinputs, hamuts, resultType2);
        innerExprResultTypeH.kind match {
          case NeverHT(_) =>
          case _ => {
            if (innerExprResultTypeH != resultTypeH) {
              vfail(innerExprResultTypeH + " doesnt match " + resultTypeH);
            }
          }
        }

        val (innerExprHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);

        // Not always the case:
        //   vcurious(innerExprResultTypeH.kind == NeverH() || resultTypeH.kind == NeverH())
        // for example when we're destructuring a TupleT2 or PackT2, we interpret from that
        // to its understruct.

        // These both trip:
        //   vcurious(innerExprResultTypeH == resultTypeH)
        //   vcurious(innerExprResultTypeH != resultTypeH)
        // Because sometimes theyre actually the same, because we might interpret a tuple to
        // its understruct, and sometimes theyre different, when we're making a Never into
        // an Int for example when one branch of an If panics or returns.

        innerExprResultTypeH.kind match {
          case NeverHT(_) => vfail()
          case _ =>
        }
        resultTypeH.kind match {
          case NeverHT(_) => vfail()
          case _ =>
        }
        (innerExprHE, deferreds)
      }

      case up @ InterfaceToInterfaceUpcastIE(innerExpr, targetInterfaceRef2, _) => {
        val targetPointerType2 = up.result;
        val sourcePointerType2 = innerExpr.result

        val (sourcePointerTypeH) =
          typeHammer.translateCoord(hinputs, hamuts, sourcePointerType2);
        val (targetPointerTypeH) =
          typeHammer.translateCoord(hinputs, hamuts, targetPointerType2);

        val sourceStructRefH = sourcePointerTypeH.kind.asInstanceOf[InterfaceHT]
        val targetInterfaceRefH = targetPointerTypeH.kind.asInstanceOf[InterfaceHT]

        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        // Upcasting an interface is technically a no-op with our language, but the sculptor
        // will still want to do it to do some checking in debug mode.
        val upcastNode =
            InterfaceToInterfaceUpcastH(
              innerExprHE.expectInterfaceAccess(),
              targetInterfaceRefH);
        (upcastNode, innerDeferreds)
      }

      case up @ UpcastIE(innerExpr, targetInterfaceRef2, _, _) => {
        val targetPointerType2 = up.result;
        val sourcePointerType2 = innerExpr.result

        val (sourcePointerTypeH) =
          typeHammer.translateCoord(hinputs, hamuts, sourcePointerType2);
        val (targetPointerTypeH) =
          typeHammer.translateCoord(hinputs, hamuts, targetPointerType2);

        val sourceStructRefH = sourcePointerTypeH.kind.asInstanceOf[StructHT]

        val targetInterfaceRefH = targetPointerTypeH.kind.asInstanceOf[InterfaceHT]

        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        // Upcasting an interface is technically a no-op with our language, but the sculptor
        // will still want to do it to do some checking in debug mode.
        val upcastNode =
            StructToInterfaceUpcastH(
              innerExprHE.expectStructAccess(),
              targetInterfaceRefH)
        (upcastNode, innerDeferreds)
      }

      case ExternFunctionCallIE(prototype2, argsExprs2, _) => {
        val access =
          translateExternFunctionCall(hinputs, hamuts, currentFunctionHeader, locals, prototype2, argsExprs2)
        (access, Vector.empty)
      }

      case while2 @ WhileIE(_, _) => {
        val whileH =
            translateWhile(hinputs, hamuts, currentFunctionHeader, locals, while2)
        (whileH, Vector.empty)
      }

      case DeferIE(innerExpr, deferredExpr, _) => {
        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (innerExprHE, Vector(deferredExpr) ++ innerDeferreds)
      }

      case DiscardIE(innerExpr) => {
        val (undiscardedInnerExprH, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        vassert(innerDeferreds.isEmpty) // BMHD, probably need to translate them here.
        val innerExprH = DiscardH(undiscardedInnerExprH)
        val innerWithDeferredsExprH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, innerExprH, innerDeferreds)
        (innerWithDeferredsExprH, Vector.empty)
      }
      case ReturnIE(innerExpr) => {
        vassert(
          innerExpr.result.kind == NeverIT[cI](false) ||
          innerExpr.result == currentFunctionHeader.returnType)

        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);

        // Return is a special case where we execute the *inner* expression (not the whole return expression) and
        // then the deferreds and then the return. See MEDBR.
        val innerWithDeferreds =
            translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, innerExprHE, innerDeferreds)

        // If we're returning a never, just strip this Return, because we'll
        // never get here.
        innerWithDeferreds.resultType.kind match {
          case NeverHT(_) => {
            return (innerWithDeferreds, Vector.empty)
          }
          case _ =>
        }

        vassert(innerExpr.result == currentFunctionHeader.returnType)
        (ReturnH(innerWithDeferreds), Vector.empty)
      }
      case ArgLookupIE(paramIndex, type2) => {
        val typeH = typeHammer.translateCoord(hinputs, hamuts, type2)
        vassert(currentFunctionHeader.paramTypes(paramIndex) == type2)
        vassert(typeHammer.translateCoord(hinputs, hamuts, currentFunctionHeader.paramTypes(paramIndex)) == typeH)
        val argNode = ArgumentH(typeH, paramIndex)
        (argNode, Vector.empty)
      }

      case das2 @ DestroyStaticSizedArrayIntoFunctionIE(_, _, _, _) => {
        val dasH =
            translateDestroyStaticSizedArray(
              hinputs, hamuts, currentFunctionHeader, locals, das2)
        (dasH, Vector.empty)
      }

      case das2 @ DestroyImmRuntimeSizedArrayIE(_, _, _, _) => {
        val drsaH =
            translateDestroyImmRuntimeSizedArray(
              hinputs, hamuts, currentFunctionHeader, locals, das2)
        (drsaH, Vector.empty)
      }

//      case UnreachableMootIE(innerExpr) => {
//        val (innerExprHE, innerDeferreds) =
//          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
//        val innerWithDeferredsH =
//          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, innerExprHE, innerDeferreds)
//
//        // Throw away the inner expression because we dont want it to be generated, because
//        // theyll never get run anyway.
//        // We only translated them above to mark unstackified things unstackified.
//
//        val void = ConstantVoidH()
//
//        (void, Vector.empty)
//      }

      case BorrowToWeakIE(innerExpr, _) => {
        val (innerExprHE, innerDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, innerExpr);
        (BorrowToWeakH(innerExprHE), innerDeferreds)
      }

      case IsSameInstanceIE(leftExprI, rightExprI) => {
        val (leftExprHE, leftDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, leftExprI);
        val (rightExprHE, rightDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, rightExprI);
        val resultHE = IsSameInstanceH(leftExprHE, rightExprHE)

        val expr = translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, resultHE, leftDeferreds ++ rightDeferreds)
        (expr, Vector.empty)
      }

      case AsSubtypeIE(leftExprI, targetSubtype, resultOptType, someConstructor, noneConstructor, _, _, _, _) => {
        val (resultHE, deferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, leftExprI);
        val (targetSubtypeH) =
          typeHammer.translateCoord(hinputs, hamuts, targetSubtype).kind
        val (resultOptTypeH) =
          typeHammer.translateCoord(hinputs, hamuts, resultOptType).expectInterfaceCoord()

        val someConstructorH =
          functionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, someConstructor);
        val noneConstructorH =
          functionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, noneConstructor);

        val resultNode =
          AsSubtypeH(
            resultHE,
            targetSubtypeH,
            resultOptTypeH,
            someConstructorH.prototype,
            noneConstructorH.prototype);
        (resultNode, deferreds)
      }

      case DestroyMutRuntimeSizedArrayIE(rsaIE) => {
        val (rsaHE, rsaDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, rsaIE);

        val destroyHE =
          DestroyMutRuntimeSizedArrayH(rsaHE.expectRuntimeSizedArrayAccess())

        val expr = translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, destroyHE, rsaDeferreds)
        (expr, Vector.empty)
      }

      case BreakIE() => {
        (BreakH(), Vector.empty)
      }

      case _ => {
        vfail("wat " + expr2)
      }
    }
  }

  def translateDeferreds(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    originalExpr: ExpressionH[KindHT],
    deferreds: Vector[ExpressionI]):
  ExpressionH[KindHT] = {
    if (deferreds.isEmpty) {
      return originalExpr
    }

    val (deferredExprs, deferredDeferreds) =
      translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, deferreds)
    // Don't evaluate anything that can't ever be run, see BRCOBS
    deferredExprs.lastOption.map(_.resultType.kind) match {
      case Some(NeverHT(_)) => {
        return Hammer.consecrash(locals, deferredExprs)
      }
      case _ =>
    }
    if (deferredExprs.map(_.resultType.kind).toSet != Set(VoidHT())) {
      // curiosity, why would a deferred ever have a result
      vcurious()
    }
    if (locals.locals.size != locals.locals.size) {
      // There shouldnt have been any locals introduced
      vfail("wat")
    }
    vassert(deferredDeferreds.isEmpty)
    // Don't need these, they should all be voids anyway

    vcurious(deferredExprs.nonEmpty)

    val newExprs =
      if (originalExpr.resultType.kind == VoidHT()) {
        val void = ConstantVoidH()
        Vector(originalExpr) ++ (deferredExprs :+ void)
      } else {
        val temporaryResultLocal = locals.addHammerLocal(originalExpr.resultType, Final)
        val stackify = StackifyH(originalExpr, temporaryResultLocal, None)
        val unstackify = UnstackifyH(temporaryResultLocal)
        locals.markUnstackified(temporaryResultLocal.id)
        Vector(stackify) ++ deferredExprs ++ Vector(unstackify)
      }

    val result = ConsecutorH(newExprs)
    vassert(originalExpr.resultType == result.resultType)
    result
  }

  def translateExpressionsUntilNever(
    hinputs: HinputsI, hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    exprsIE: Vector[ExpressionI]):
  (Vector[ExpressionH[KindHT]], Vector[ExpressionI]) = {
    val (exprsHE, deferreds) =
      exprsIE.foldLeft((Vector[ExpressionH[KindHT]](), Vector[ExpressionI]()))({
        // If we previously saw a Never, stop there, don't proceed, don't even waste
        // time compiling the rest.
        case ((prevExprsHE, _), _)
            if (prevExprsHE.lastOption.map(_.resultType.kind) match {
              case Some(NeverHT(_)) => true case _ => false
            }) => {
          (prevExprsHE, Vector())
        }
        case ((prevExprsHE, prevDeferreds), nextIE) => {
          val (nextHE, nextDeferreds) =
            translate(hinputs, hamuts, currentFunctionHeader, locals, nextIE);
          (prevExprsHE :+ nextHE, prevDeferreds ++ nextDeferreds)
        }
      })

    // We'll never get to the deferreds, so forget them.
    exprsHE.lastOption.map(_.resultType.kind) match {
      case Some(NeverHT(_)) => (exprsHE, Vector())
      case _ => (exprsHE, deferreds)
    }
  }

  def translateExpressionsAndDeferreds(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    exprs2: Vector[ExpressionI]):
  ExpressionH[KindHT] = {
    val exprs =
      exprs2.map({ case expr2 =>
        val (firstHE, firstDeferreds) =
          translate(hinputs, hamuts, currentFunctionHeader, locals, expr2);
        val firstExprWithDeferredsH =
          translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, firstHE, firstDeferreds)
        firstExprWithDeferredsH
      })
    Hammer.consecutive(exprs)
  }

  def translateExternFunctionCall(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    prototype2: PrototypeI[cI],
    argsExprs2: Vector[ReferenceExpressionIE]):
  (ExpressionH[KindHT]) = {
    val (argsHE, argsDeferreds) =
      translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, argsExprs2);
    // Don't evaluate anything that can't ever be run, see BRCOBS
    if (argsHE.nonEmpty && argsHE.last.resultType.kind == NeverHT(true)) {
      return Hammer.consecrash(locals, argsHE)
    }

    // Doublecheck the types
    val (paramTypes) =
      typeHammer.translateCoords(hinputs, hamuts, prototype2.paramTypes);
    vassert(argsHE.map(_.resultType) == paramTypes)

    val (functionRefH) =
      functionHammer.translateFunctionRef(hinputs, hamuts, currentFunctionHeader, prototype2);

    val callResultNode = ExternCallH(functionRefH.prototype, argsHE)

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, callResultNode, argsDeferreds)
  }

  def translateFunctionPointerCall(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    function: PrototypeI[cI],
    args: Vector[ExpressionI],
    resultType2: CoordI[cI]):
  ExpressionH[KindHT] = {
    val returnType2 = function.returnType
    val paramTypes = function.paramTypes
    val (argsHE, argsDeferreds) =
      translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, args);
    // Don't evaluate anything that can't ever be run, see BRCOBS
    argsHE.lastOption.map(_.resultType.kind) match {
      case Some(NeverHT(_)) => {
        return Hammer.consecrash(locals, argsHE)
      }
      case _ =>
    }

    val prototypeH =
      typeHammer.translatePrototype(hinputs, hamuts, function)

    // Doublecheck the types
    val (paramTypesH) =
      typeHammer.translateCoords(hinputs, hamuts, paramTypes)
    vassert(argsHE.map(_.resultType) == paramTypesH)

    // Doublecheck return
    val (returnTypeH) = typeHammer.translateCoord(hinputs, hamuts, returnType2)
    val (resultTypeH) = typeHammer.translateCoord(hinputs, hamuts, resultType2);
    vassert(returnTypeH == resultTypeH)

    val callResultNode = CallH(prototypeH, argsHE)

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, callResultNode, argsDeferreds)
  }

  def translateNewMutRuntimeSizedArray(
    hinputs: HinputsI, hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    constructArray2: NewMutRuntimeSizedArrayIE):
  (ExpressionH[KindHT]) = {
    val NewMutRuntimeSizedArrayIE(arrayType2, capacityExpr2, _) = constructArray2;

    val (capacityRegisterId, capacityDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, capacityExpr2);

    val (arrayRefTypeH) =
      typeHammer.translateCoord(
        hinputs, hamuts, constructArray2.result)

    val (arrayTypeH) =
      typeHammer.translateRuntimeSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayCoord().kind == arrayTypeH)

    val elementType = hamuts.getRuntimeSizedArray(arrayTypeH).elementType

    val constructArrayCallNode =
      NewMutRuntimeSizedArrayH(
        capacityRegisterId.expectIntAccess(),
        elementType,
        arrayRefTypeH.expectRuntimeSizedArrayCoord())

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, capacityDeferreds)
  }

  def translateNewImmRuntimeSizedArray(
    hinputs: HinputsI, hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    constructArray2: NewImmRuntimeSizedArrayIE):
  (ExpressionH[KindHT]) = {
    val NewImmRuntimeSizedArrayIE(arrayType2, sizeExpr2, generatorExpr2, generatorMethod, _) = constructArray2;

    val (sizeRegisterId, sizeDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, sizeExpr2);

    val (generatorRegisterId, generatorDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, generatorExpr2);

    val (arrayRefTypeH) =
      typeHammer.translateCoord(
        hinputs, hamuts, constructArray2.result)

    val (arrayTypeH) =
      typeHammer.translateRuntimeSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayCoord().kind == arrayTypeH)

    val elementType = hamuts.getRuntimeSizedArray(arrayTypeH).elementType

    val generatorMethodH =
      typeHammer.translatePrototype(hinputs, hamuts, generatorMethod)

    val constructArrayCallNode =
      NewImmRuntimeSizedArrayH(
        sizeRegisterId.expectIntAccess(),
        generatorRegisterId,
        generatorMethodH,
        elementType,
        arrayRefTypeH.expectRuntimeSizedArrayCoord())

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, generatorDeferreds ++ sizeDeferreds)
  }

  def translateStaticArrayFromCallable(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    exprIE: StaticArrayFromCallableIE):
  (ExpressionH[KindHT]) = {
    val StaticArrayFromCallableIE(arrayType2, generatorExpr2, generatorMethod, _) = exprIE;

    val (generatorRegisterId, generatorDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, generatorExpr2);

    val (arrayRefTypeH) =
      typeHammer.translateCoord(
        hinputs, hamuts, exprIE.result)

    val (arrayTypeH) =
      typeHammer.translateStaticSizedArray(hinputs, hamuts, arrayType2)
    vassert(arrayRefTypeH.expectStaticSizedArrayCoord().kind == arrayTypeH)

    val elementType = hamuts.getStaticSizedArray(arrayTypeH).elementType

    val generatorMethodH =
      typeHammer.translatePrototype(hinputs, hamuts, generatorMethod)

    val constructArrayCallNode =
      StaticArrayFromCallableH(
        generatorRegisterId,
        generatorMethodH,
        elementType,
        arrayRefTypeH.expectStaticSizedArrayCoord())

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, constructArrayCallNode, generatorDeferreds)
  }

  def translateDestroyStaticSizedArray(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    das2: DestroyStaticSizedArrayIntoFunctionIE):
  ExpressionH[KindHT] = {
    val DestroyStaticSizedArrayIntoFunctionIE(arrayExpr2, staticSizedArrayType, consumerExpr2, consumerMethod2) = das2;

    val (arrayTypeH) =
      typeHammer.translateStaticSizedArray(hinputs, hamuts, staticSizedArrayType)
    val (arrayRefTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, arrayExpr2.result)
    vassert(arrayRefTypeH.expectStaticSizedArrayCoord().kind == arrayTypeH)

    val (arrayExprResultHE, arrayExprDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

    val (consumerCallableResultHE, consumerCallableDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, consumerExpr2);

    val staticSizedArrayDef = hamuts.getStaticSizedArray(arrayTypeH)

    val consumerMethod =
      typeHammer.translatePrototype(hinputs, hamuts, consumerMethod2)

    val destroyStaticSizedArrayCallNode =
      DestroyStaticSizedArrayIntoFunctionH(
        arrayExprResultHE.expectStaticSizedArrayAccess(),
        consumerCallableResultHE,
        consumerMethod,
        staticSizedArrayDef.elementType,
        staticSizedArrayDef.size)

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, destroyStaticSizedArrayCallNode, consumerCallableDeferreds ++ arrayExprDeferreds)
  }

  def translateDestroyImmRuntimeSizedArray(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    das2: DestroyImmRuntimeSizedArrayIE):
  ExpressionH[KindHT] = {
    val DestroyImmRuntimeSizedArrayIE(arrayExpr2, runtimeSizedArrayType2, consumerExpr2, consumerMethod2) = das2;

    //    val RuntimeSizedArrayT2(RawArrayT2(memberType2, mutability)) = runtimeSizedArrayType2

    val (arrayTypeH) =
      typeHammer.translateRuntimeSizedArray(hinputs, hamuts, runtimeSizedArrayType2)
    val (arrayRefTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, arrayExpr2.result)
    vassert(arrayRefTypeH.expectRuntimeSizedArrayCoord().kind == arrayTypeH)

    val (arrayExprResultHE, arrayExprDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, arrayExpr2);

    val (consumerCallableResultHE, consumerCallableDeferreds) =
      translate(
        hinputs, hamuts, currentFunctionHeader, locals, consumerExpr2);

    val consumerMethod =
      typeHammer.translatePrototype(hinputs, hamuts, consumerMethod2)

    val elementType =
      hamuts.getRuntimeSizedArray(
        arrayExprResultHE.expectRuntimeSizedArrayAccess().resultType.kind)
        .elementType

    val destroyStaticSizedArrayCallNode =
      DestroyImmRuntimeSizedArrayH(
        arrayExprResultHE.expectRuntimeSizedArrayAccess(),
        consumerCallableResultHE,
        consumerMethod,
        elementType)

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, destroyStaticSizedArrayCallNode, consumerCallableDeferreds ++ arrayExprDeferreds)
  }

  def translateIf(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    parentLocals: LocalsBox,
    if2: IfIE):
  ExpressionH[KindHT] = {
    val IfIE(condition2, thenBlock2, elseBlock2, _) = if2

    val (conditionBlockH, Vector()) =
      translate(hinputs, hamuts, currentFunctionHeader, parentLocals, condition2);
    vassert(conditionBlockH.resultType == CoordH(MutableShareH, InlineH, BoolHT()))

    val thenLocals = LocalsBox(parentLocals.snapshot)
    val (thenBlockH, Vector()) =
      translate(hinputs, hamuts, currentFunctionHeader, thenLocals, thenBlock2);
    val thenResultCoord = thenBlockH.resultType
    parentLocals.setNextLocalIdNumber(thenLocals.nextLocalIdNumber)

    val elseLocals = LocalsBox(parentLocals.snapshot)
    val (elseBlockH, Vector()) =
      translate(hinputs, hamuts, currentFunctionHeader, elseLocals, elseBlock2);
    val elseResultCoord = elseBlockH.resultType
    parentLocals.setNextLocalIdNumber(elseLocals.nextLocalIdNumber)

    val commonSupertypeH =
      typeHammer.translateCoord(hinputs, hamuts, if2.result)

    val ifCallNode = IfH(conditionBlockH.expectBoolAccess(), thenBlockH, elseBlockH, commonSupertypeH)


    val thenContinues = thenResultCoord.kind match { case NeverHT(_) => false case _ => true }
    val elseContinues = elseResultCoord.kind match { case NeverHT(_) => false case _ => true }

    val unstackifiesOfParentLocals =
      if (thenContinues && elseContinues) { // Both continue
        val parentLocalsAfterThen = thenLocals.locals.keySet -- thenLocals.unstackifiedVars
        val parentLocalsAfterElse = elseLocals.locals.keySet -- elseLocals.unstackifiedVars
        // The same outside-if variables should still exist no matter which branch we went down.
        if (parentLocalsAfterThen != parentLocalsAfterElse) {
          vfail("Internal error:\nIn function " + currentFunctionHeader + "\nMismatch in if branches' parent-unstackifies:\nThen branch: " + parentLocalsAfterThen + "\nElse branch: " + parentLocalsAfterElse)
        }
        // Since theyre the same, just arbitrarily use the then.
        thenLocals.unstackifiedVars
      } else if (thenContinues) {
        // Then continues, else does not
        // Throw away any information from the else. But do consider those from the then.
        thenLocals.unstackifiedVars
      } else if (elseContinues) {
        // Else continues, then does not
        elseLocals.unstackifiedVars
      } else {
        // Neither continues, so neither unstackifies things.
        // It also kind of doesnt matter, no code after this will run.
        Set[VariableIdH]()
      }

    val parentLocalsToUnstackify =
    // All the parent locals...
      parentLocals.locals.keySet
        // ...minus the ones that were unstackified before...
        .diff(parentLocals.unstackifiedVars)
        // ...which were unstackified by the branch.
        .intersect(unstackifiesOfParentLocals)
    parentLocalsToUnstackify.foreach(parentLocals.markUnstackified)

    ifCallNode
  }

  def translateWhile(
    hinputs: HinputsI, hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    while2: WhileIE):
  WhileH = {

    val WhileIE(bodyExpr2, _) = while2

    val (exprWithoutDeferreds, deferreds) =
      translate(hinputs, hamuts, currentFunctionHeader, locals, bodyExpr2);
    val expr =
      translateDeferreds(hinputs, hamuts, currentFunctionHeader, locals, exprWithoutDeferreds, deferreds)

    val whileCallNode = WhileH(expr)
    whileCallNode
  }

  def translateInterfaceFunctionCall(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    superFunctionPrototype: PrototypeI[cI],
    virtualParamIndex: Int,
    resultType2: CoordI[cI],
    argsExprs2: Vector[ExpressionI]):
  ExpressionH[KindHT] = {
    val (argsHE, argsDeferreds) =
      translateExpressionsUntilNever(
        hinputs, hamuts, currentFunctionHeader, locals, argsExprs2);
    // Don't evaluate anything that can't ever be run, see BRCOBS
    if (argsHE.nonEmpty && argsHE.last.resultType.kind == NeverHT(false)) {
      return Hammer.consecrash(locals, argsHE)
    }

//    val virtualParamIndex = superFunctionHeader.getVirtualIndex.get
    val CoordI(_, interfaceIT @ InterfaceIT(_)) =
      superFunctionPrototype.paramTypes(virtualParamIndex)
    val (interfaceRefH) =
      structHammer.translateInterface(hinputs, hamuts, interfaceIT)
    val edge = hinputs.interfaceToEdgeBlueprints(interfaceIT.id)
    vassert(edge.interface == interfaceIT.id)
    val indexInEdge = edge.superFamilyRootHeaders.indexWhere(x => superFunctionPrototype.toSignature == x._1.toSignature)
    vassert(indexInEdge >= 0)

    val (prototypeH) = typeHammer.translatePrototype(hinputs, hamuts, superFunctionPrototype)

    val callNode =
      InterfaceCallH(
        argsHE,
        virtualParamIndex,
        interfaceRefH,
        indexInEdge,
        prototypeH)

    translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, callNode, argsDeferreds)
  }
}
