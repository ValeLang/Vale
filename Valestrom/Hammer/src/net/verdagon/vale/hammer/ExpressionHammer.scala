package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.{vassert, vassertSome, vcurious, vfail, vimpl, metal => m}
import net.verdagon.vale.metal.{ShareH, BorrowH => _, Immutable => _, Mutable => _, OwnH => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.AddressibleLocalVariable2
import net.verdagon.vale.templar.types._

object ExpressionHammer {

  // stackHeight is the number of locals that have been declared in ancestor
  // blocks and previously in this block. It's used to figure out the index of
  // a newly declared local.
  // Returns:
  // - result register id
  // - deferred expressions, to move to after the enclosing call. head is put first after call.
  def translate(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      locals: LocalsBox,
      expr2: Expression2
  ): (ExpressionH[ReferendH], List[Expression2]) = {
    expr2 match {
      case IntLiteral2(value) => {
        (ConstantI64H(value), List())
      }
      case VoidLiteral2() => {
        val constructH = NewStructH(List(), ProgramH.emptyTupleStructType)
        (constructH, List())
      }
      case StrLiteral2(value) => {
        (ConstantStrH(value), List())
      }
      case FloatLiteral2(value) => {
        (ConstantF64H(value), List())
      }
      case BoolLiteral2(value) => {
        (ConstantBoolH(value), List())
      }
      case let2 @ LetNormal2(_, _) => {
        val letH =
          LetHammer.translateLet(hinputs, hamuts, locals, let2)
        (letH, List())
      }
      case let2 @ LetAndLend2(_, _) => {
        val borrowAccess =
          LetHammer.translateLetAndLend(hinputs, hamuts, locals, let2)
        (borrowAccess, List())
      }
      case des2 @ Destructure2(_, _, _) => {
        val destructureH =
            LetHammer.translateDestructure(hinputs, hamuts, locals, des2)
        // Templar destructures put things in local variables (even though hammer itself
        // uses registers internally to make that happen).
        // Since all the members landed in locals, we still need something to return, so we
        // return a void.
        (destructureH, List())
      }
      case des2 @ DestructureArraySequence2(_, _, _) => {
        val destructureH =
            LetHammer.translateDestructureArraySequence(hinputs, hamuts, locals, des2)
        // Templar destructures put things in local variables (even though hammer itself
        // uses registers internally to make that happen).
        // Since all the members landed in locals, we still need something to return, so we
        // return a void.
        (destructureH, List())
      }
      case unlet2 @ Unlet2(_) => {
        val valueAccess =
          LetHammer.translateUnlet(
            hinputs, hamuts, locals, unlet2)
        (valueAccess, List())
      }
      case mutate2 @ Mutate2(_, _) => {
        val newEmptyPackStructNodeLine =
          MutateHammer.translateMutate(hinputs, hamuts, locals, mutate2)
        (newEmptyPackStructNodeLine, List())
      }
      case b @ Block2(_) => {
        val blockH =
          BlockHammer.translateBlock(hinputs, hamuts, locals.snapshot, b)
        (blockH, List())
      }
      case call2 @ FunctionCall2(callableExpr, args) => {
        val access =
          CallHammer.translateFunctionPointerCall(
            hinputs, hamuts, locals, callableExpr, args, call2.resultRegister.reference)
        (access, List())
      }

      case InterfaceFunctionCall2(superFunctionHeader, resultType2, argsExprs2) => {
        val access =
          CallHammer.translateInterfaceFunctionCall(
            hinputs, hamuts, locals, superFunctionHeader, resultType2, argsExprs2)
        (access, List())
      }

      case Consecutor2(exprs) => {
        val (resultLines, deferreds) =
          translateExpressions(hinputs, hamuts, locals, exprs);
        resultLines.init.foreach(nonLastResultLine => {
          vassert(nonLastResultLine.resultType == ProgramH.emptyTupleStructType)
        })
        vassert(deferreds.isEmpty) // curiosity, would we have any here?
        (flattenAndMakeBlock(resultLines), List())
      }

      case PackE2(exprs, resultType, resultPackType) => {
        val (resultLines, deferreds) =
          translateExpressions(hinputs, hamuts, locals, exprs)
        val (underlyingStructRefH) =
          StructHammer.translateStructRef(hinputs, hamuts, resultPackType.underlyingStruct)
        val (resultReference) =
          TypeHammer.translateReference(hinputs, hamuts, resultType)
        vassert(resultReference.kind == underlyingStructRefH)

        val structDefH = hamuts.structDefsByRef2(resultPackType.underlyingStruct)
        vassert(resultLines.size == structDefH.members.size)
        val newStructNode =
          NewStructH(

            resultLines,
            resultReference.expectStructReference())

        val newStructNodeAndDeferredsExprH =
            translateDeferreds(hinputs, hamuts, locals, newStructNode, deferreds)

        // Export locals from inside the pack
        (newStructNodeAndDeferredsExprH, List())
      }

      case ArrayLength2(arrayExpr2) => {
        val (resultLine, deferreds) =
          translate(hinputs, hamuts, locals, arrayExpr2);

        val lengthResultNode = ArrayLengthH(resultLine);

        val arrayLengthAndDeferredsExprH =
            translateDeferreds(hinputs, hamuts, locals, lengthResultNode, deferreds)

        (arrayLengthAndDeferredsExprH, List())
      }

      case TupleE2(exprs, resultType, resultPackType) => {
        val (resultLines, deferreds) =
          translateExpressions(hinputs, hamuts, locals, exprs);
        val (underlyingStructRefH) =
          StructHammer.translateStructRef(hinputs, hamuts, resultPackType.underlyingStruct);
        val (resultReference) =
          TypeHammer.translateReference(hinputs, hamuts, resultType)
        vassert(resultReference.kind == underlyingStructRefH)

        val structDefH = hamuts.structDefsByRef2(resultPackType.underlyingStruct)
        vassert(resultLines.size == structDefH.members.size)
        val newStructNode =
          NewStructH(
            resultLines,
            resultReference.expectStructReference())
        // Export locals from inside the pack

        val newStructAndDeferredsExprH =
            translateDeferreds(hinputs, hamuts, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, List())
      }

      case ArraySequenceE2(exprs, arrayReference2, arrayType2) => {
        val (resultLines, deferreds) =
          translateExpressions(hinputs, hamuts, locals, exprs);
        val (underlyingArrayH) =
          TypeHammer.translateKnownSizeArray(hinputs, hamuts, arrayType2);

        val (arrayReferenceH) =
          TypeHammer.translateReference(hinputs, hamuts, arrayReference2)
        vassert(arrayReferenceH.kind == underlyingArrayH)

        val newStructNode =
          NewArrayFromValuesH(
            arrayReferenceH.expectKnownSizeArrayReference(),
            resultLines)

        val newStructAndDeferredsExprH =
        translateDeferreds(hinputs, hamuts, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, List())
      }

      case Construct2(structRef2, resultType2, memberExprs) => {
        val (memberResultLines, deferreds) =
          translateExpressions(hinputs, hamuts, locals, memberExprs);

        val (resultTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, resultType2)

//        hinputs.lookupStruct(resultStructType2)
//        vassert(structDef2.getRef == resultTypeH.innerType)

        val structDefH = hamuts.structDefsByRef2(structRef2)
        vassert(memberResultLines.size == structDefH.members.size)
        val newStructNode =
          NewStructH(

            memberResultLines,
            resultTypeH.expectStructReference())

        val newStructAndDeferredsExprH =
            translateDeferreds(hinputs, hamuts, locals, newStructNode, deferreds)

        (newStructAndDeferredsExprH, List())
      }

      case load2 @ SoftLoad2(_, _) => {
        val (loadedAccessH, deferreds) =
          LoadHammer.translateLoad(hinputs, hamuts, locals, load2)
        (loadedAccessH, deferreds)
      }

      case lookup2 @ LocalLookup2(AddressibleLocalVariable2(_, _, _), _) => {
        val loadBoxAccess =
          LoadHammer.translateLocalAddress(hinputs, hamuts, locals, lookup2)
        (loadBoxAccess, List())
      }

      case lookup2 @ AddressMemberLookup2(_, _, _) => {
        val (loadBoxAccess, deferreds) =
          LoadHammer.translateMemberAddress(hinputs, hamuts, locals, lookup2)
        (loadBoxAccess, deferreds)
      }

      case if2 @ If2(_, _, _) => {
        val maybeAccess =
          CallHammer.translateIf(hinputs, hamuts, locals, if2)
        (maybeAccess, List())
      }

      case ca2 @ ConstructArray2(_, _, _) => {
        val access =
          CallHammer.translateConstructArray(
            hinputs, hamuts, locals, ca2)
        (access, List())
      }

      case TemplarReinterpret2(innerExpr, resultType2) => {
        // Check types; it's overkill because reinterprets are rather scary.
        val innerExprResultType2 = innerExpr.resultRegister.reference
        val (innerExprResultTypeH) = TypeHammer.translateReference(hinputs, hamuts, innerExprResultType2);
        val (resultTypeH) = TypeHammer.translateReference(hinputs, hamuts, resultType2);
        if (innerExprResultTypeH.kind != NeverH()) {
          if (innerExprResultTypeH != resultTypeH) {
            vfail(innerExprResultTypeH  + " doesnt match " + resultTypeH);
          }
        }

        val (innerExprResultLine, deferreds) =
          translate(hinputs, hamuts, locals, innerExpr);

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

        if (innerExprResultTypeH.kind == NeverH() || resultTypeH.kind == NeverH()) {
          (ReinterpretH(innerExprResultLine, resultTypeH), deferreds)
        } else {
          (innerExprResultLine, deferreds)
        }
      }

      case CheckRefCount2(refExpr2, category, numExpr2) => {
        val (refExprResultLine, refExprDeferreds) =
          translate(hinputs, hamuts, locals, refExpr2)
        val (refExprResultTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, refExpr2.resultRegister.reference);

        val (numExprResultLine, numExprDeferreds) =
          translate(hinputs, hamuts, locals, numExpr2)
        val (numExprResultTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, refExpr2.resultRegister.reference);

        val checkRefCountH =
            CheckRefCountH(
              refExprResultLine,
              Conversions.evaluateRefCountCategory(category),
              numExprResultLine.expectIntAccess())

        val checkRefCountAndDeferredsH =
            translateDeferreds(
              hinputs, hamuts, locals, checkRefCountH, numExprDeferreds ++ refExprDeferreds)

        (checkRefCountAndDeferredsH, List())
      }

      case up @ InterfaceToInterfaceUpcast2(innerExpr, targetInterfaceRef2) => {
        val targetPointerType2 = up.resultRegister.reference;
        val sourcePointerType2 = innerExpr.resultRegister.reference

        val (sourcePointerTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, sourcePointerType2);
        val (targetPointerTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, targetPointerType2);

        val sourceStructRefH = sourcePointerTypeH.kind.asInstanceOf[InterfaceRefH]
        val targetInterfaceRefH = targetPointerTypeH.kind.asInstanceOf[InterfaceRefH]

        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, locals, innerExpr);
        // Upcasting an interface is technically a no-op with our language, but the sculptor
        // will still want to do it to do some checking in debug mode.
        val upcastNode =
            InterfaceToInterfaceUpcastH(
              innerExprResultLine.expectInterfaceAccess(),
              targetInterfaceRefH);
        (upcastNode, innerDeferreds)
      }

      case up @ StructToInterfaceUpcast2(innerExpr, targetInterfaceRef2) => {
        val targetPointerType2 = up.resultRegister.reference;
        val sourcePointerType2 = innerExpr.resultRegister.reference

        val (sourcePointerTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, sourcePointerType2);
        val (targetPointerTypeH) =
          TypeHammer.translateReference(hinputs, hamuts, targetPointerType2);

        val sourceStructRefH = sourcePointerTypeH.kind.asInstanceOf[StructRefH]

        val targetInterfaceRefH = targetPointerTypeH.kind.asInstanceOf[InterfaceRefH]

        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, locals, innerExpr);
        // Upcasting an interface is technically a no-op with our language, but the sculptor
        // will still want to do it to do some checking in debug mode.
        val upcastNode =
            StructToInterfaceUpcastH(
              innerExprResultLine.expectStructAccess(),
              targetInterfaceRefH)
        (upcastNode, innerDeferreds)
      }

      case ExternFunctionCall2(prototype2, argsExprs2) => {
        val access =
          CallHammer.translateExternFunctionCall(hinputs, hamuts, locals, prototype2, argsExprs2)
        (access, List())
      }

      case while2 @ While2(_) => {
        val whileH =
            CallHammer.translateWhile(hinputs, hamuts, locals, while2)
        (whileH, List())
      }

      case Defer2(innerExpr, deferredExpr) => {
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, locals, innerExpr);
        (innerExprResultLine, deferredExpr :: innerDeferreds)
      }

      case Discard2(innerExpr) => {
        val (undiscardedInnerExprH, innerDeferreds) =
          translate(hinputs, hamuts, locals, innerExpr);

        vassert(innerDeferreds.isEmpty) // BMHD, probably need to translate them here.

        undiscardedInnerExprH.resultType.ownership match {
          case m.BorrowH | m.ShareH =>
          case m.OwnH => {
            vfail("Owns can only be discarded via destructuring!")
          }
        }

        // We don't discard Void, see VDND.
        val innerExprH =
          if (innerExpr.resultRegister.reference.referend != Void2()) {
            DiscardH(undiscardedInnerExprH)
          } else {
            undiscardedInnerExprH
          }

        val innerWithDeferredsExprH =
          translateDeferreds(hinputs, hamuts, locals, innerExprH, innerDeferreds)

        (innerWithDeferredsExprH, List())
      }
      case Return2(innerExpr) => {
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, locals, innerExpr);

        // Return is a special case where we execute the *inner* expression (not the whole return expression) and
        // then the deferreds and then the return. See MEDBR.
        val innerWithDeferreds =
            translateDeferreds(hinputs, hamuts, locals, innerExprResultLine, innerDeferreds)

        (ReturnH(innerWithDeferreds), List())
      }
      case ArgLookup2(paramIndex, type2) => {
        val typeH = TypeHammer.translateReference(hinputs, hamuts, type2)
        val argNode = ArgumentH(typeH, paramIndex)
        (argNode, List())
      }

      case das2 @ DestroyArraySequence2(_, _, _) => {
        val dasH =
            CallHammer.translateDestroyArraySequence(
              hinputs, hamuts, locals, das2)
        (dasH, List())
      }

      case das2 @ DestroyUnknownSizeArray2(_, _, _) => {
        val dusaH =
            CallHammer.translateDestroyUnknownSizeArray(
              hinputs, hamuts, locals, das2)
        (dusaH, List())
      }

      case UnreachableMootE2(innerExpr) => {
        val (innerExprResultLine, innerDeferreds) =
          translate(hinputs, hamuts, locals, innerExpr);
        val innerWithDeferredsH =
          translateDeferreds(hinputs, hamuts, locals, innerExprResultLine, innerDeferreds)
        (UnreachableMootH(innerWithDeferredsH), List())
      }

      case _ => {
        vfail("wat " + expr2)
      }
    }
  }

  def translateDeferreds(
                          hinputs: Hinputs,
                          hamuts: HamutsBox,
                          locals: LocalsBox,
                          originalExpr: ExpressionH[ReferendH],
                          deferreds: List[Expression2]):
  ExpressionH[ReferendH] = {
    if (deferreds.isEmpty) {
      return originalExpr
    }

    val (deferredExprs, deferredDeferreds) =
      translateExpressions(hinputs, hamuts, locals, deferreds)
    if (deferredExprs.map(_.resultType).toSet != Set(ProgramH.emptyTupleStructType)) {
      // curiosity, why would a deferred ever have a result
      vfail("ehwot?")
    }
    if (locals.locals.size != locals.locals.size) {
      // There shouldnt have been any locals introduced
      vfail("wat")
    }
    vassert(deferredDeferreds.isEmpty)
    // Don't need these, they should all be voids anyway

    vcurious(deferredExprs.nonEmpty)

    val temporaryResultLocal = locals.addHammerLocal(originalExpr.resultType)

    val newExprs =
      if (originalExpr.resultType == ProgramH.emptyTupleStructType) {
        val void = NewStructH(List(), ProgramH.emptyTupleStructType)
        originalExpr :: (deferredExprs :+ void)
      } else {
        val stackify = StackifyH(originalExpr, temporaryResultLocal, None)
        val unstackify = UnstackifyH(temporaryResultLocal)
        stackify :: (deferredExprs :+ unstackify)
      }

    val result = flattenAndMakeBlock(newExprs)
    vassert(originalExpr.resultType == result.resultType)
    result
  }

  def flattenAndMakeBlock(innerExprsH: List[ExpressionH[ReferendH]]): BlockH = {
    vassert(innerExprsH.nonEmpty) // If we trip this, maybe we could make this return a non-block expr?
    BlockH(
      innerExprsH.flatMap({
        case BlockH(innerExprs) => innerExprs
        case other => List(other)
      }))
  }

//  def translateExpressions(
//      hinputs: Hinputs, hamuts: HamutsBox,
//      locals: LocalsBox,
//      stackHeight: StackHeightBox,
//      exprs2: List[Expression2]):
//  (List[NodeH[ReferendH]], List[Expression2]) = {
//    exprs2 match {
//      case Nil => (List(), List())
//      case firstExpr :: restExprs => {
//        val (firstResultLine, firstDeferreds) =
//          translate(hinputs, hamuts, locals, firstExpr);
//        val (restResultLines, restDeferreds) =
//          translateExpressions(hinputs, hamuts, locals, restExprs);
//
//        val resultLines = firstResultLine :: restResultLines
//        (resultLines, restDeferreds ++ firstDeferreds)
//      }
//    }
//  }

  def translateExpressions(
      hinputs: Hinputs, hamuts: HamutsBox,
      locals: LocalsBox,
      exprs2: List[Expression2]):
  (List[ExpressionH[ReferendH]], List[Expression2]) = {
    exprs2 match {
      case Nil => (List(), List())
      case firstExpr :: restExprs => {
        val (firstResultLine, firstDeferreds) =
          translate(hinputs, hamuts, locals, firstExpr);
        val (restResultLines, restDeferreds) =
          translateExpressions(hinputs, hamuts, locals, restExprs);

        val resultLines = firstResultLine :: restResultLines
        (resultLines, restDeferreds ++ firstDeferreds)
      }
    }
  }
}
