package dev.vale.simplifying

import dev.vale.finalast._
import dev.vale.{vassert, vassertSome, vfail, vimpl, vregionmut, vwat, finalast => m}
import dev.vale.finalast._
import dev.vale.instantiating.ast._

object LetHammer {
  val BOX_MEMBER_INDEX: Int = 0
}

class LetHammer(
    typeHammer: TypeHammer,
    nameHammer: NameHammer,
    structHammer: StructHammer,
    expressionHammer: ExpressionHammer,
    loadHammer: LoadHammer) {

  def translateLet(
      hinputs: HinputsI,
      hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      let2: LetNormalIE):
  ExpressionH[KindHT] = {
    val LetNormalIE(localVariable, sourceExpr2, _) = let2

    val (sourceExprHE, deferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, sourceExpr2.result)

    sourceExprHE.resultType.kind match {
      // We'll never get to this let, so strip it out. See BRCOBS.
      case NeverHT(_) => return sourceExprHE
      case _ =>
    }

    val stackifyNode =
      localVariable match {
        case ReferenceLocalVariableI(varId, variability, type2) => {
          translateMundaneLet(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExprHE, sourceResultPointerTypeH, varId, variability)
        }
        case AddressibleLocalVariableI(varId, variability, reference) => {
          translateAddressibleLet(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExprHE, sourceResultPointerTypeH, varId, variability, reference)
        }
      }

    expressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, stackifyNode, deferreds)
  }

  def translateRestackify(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    let2: RestackifyIE):
  ExpressionH[KindHT] = {
    val RestackifyIE(localVariable, sourceExpr2, _) = let2

    val (sourceExprHE, deferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, sourceExpr2.result)

    sourceExprHE.resultType.kind match {
      // We'll never get to this let, so strip it out. See BRCOBS.
      case NeverHT(_) => return sourceExprHE
      case _ =>
    }

    val stackifyNode =
      localVariable match {
        case ReferenceLocalVariableI(varId, variability, type2) => {
          translateMundaneRestackify(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExprHE, varId)
        }
        case AddressibleLocalVariableI(varId, variability, reference) => {
          translateAddressibleRestackify(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExprHE, sourceResultPointerTypeH, varId, variability, reference)
        }
      }

    expressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, stackifyNode, deferreds)
  }

  def translateLetAndPoint(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    letIE: LetAndLendIE):
  (ExpressionH[KindHT]) = {
    val LetAndLendIE(localVariable, sourceExpr2, targetOwnership, _) = letIE

    val (sourceExprHE, deferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      typeHammer.translateCoord(hinputs, hamuts, sourceExpr2.result)

    val borrowAccess =
      localVariable match {
        case ReferenceLocalVariableI(varId, variability, type2) => {
          translateMundaneLetAndPoint(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2, sourceExprHE, sourceResultPointerTypeH, letIE, varId, variability)
        }
        case AddressibleLocalVariableI(varId, variability, reference) => {
          translateAddressibleLetAndPoint(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2, sourceExprHE, sourceResultPointerTypeH, letIE, varId, variability, reference)
        }
      }

    expressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, borrowAccess, deferreds)
  }

  private def translateAddressibleLet(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    sourceExprHE: ExpressionH[KindHT],
    sourceResultPointerTypeH: CoordH[KindHT],
    varId: IVarNameI[cI],
    variability: VariabilityI,
    reference: CoordI[cI]):
  ExpressionH[KindHT] = {
    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, reference, sourceResultPointerTypeH)
    val expectedLocalBoxType = CoordH(OwnH, YonderH, boxStructRefH)

    val varIdNameH = nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, varId))
    val local =
      locals.addTypingPassLocal(
        varId, varIdNameH, Conversions.evaluateVariability(variability), expectedLocalBoxType)

    StackifyH(
      NewStructH(
        Vector(sourceExprHE),
        hamuts.structDefs.find(_.getRef == boxStructRefH).get.members.map(_.name),
        expectedLocalBoxType),
      local,
      Some(nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, varId))))
  }

  private def translateAddressibleRestackify(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    sourceExprHE: ExpressionH[KindHT],
    sourceResultPointerTypeH: CoordH[KindHT],
    varId: IVarNameI[cI],
    variability: VariabilityI,
    reference: CoordI[cI]):
  ExpressionH[KindHT] = {
    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, reference, sourceResultPointerTypeH)
    val expectedLocalBoxType = CoordH(OwnH, YonderH, boxStructRefH)

    locals.markRestackified(varId)

    val local = vassertSome(locals.get(varId))

    RestackifyH(
      NewStructH(
        Vector(sourceExprHE),
        hamuts.structDefs.find(_.getRef == boxStructRefH).get.members.map(_.name),
        expectedLocalBoxType),
      local,
      Some(nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, varId))))
  }

  private def translateAddressibleLetAndPoint(
    hinputs: HinputsI,
    hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    sourceExpr2: ReferenceExpressionIE,
    sourceExprHE: ExpressionH[KindHT],
    sourceResultPointerTypeH: CoordH[KindHT],
    letIE: LetAndLendIE,
    varId: IVarNameI[cI],
    variability: VariabilityI,
    reference: CoordI[cI]):
  (ExpressionH[KindHT]) = {
    val stackifyH =
      translateAddressibleLet(
        hinputs, hamuts, currentFunctionHeader, locals, sourceExprHE, sourceResultPointerTypeH, varId, variability, reference)
    val (borrowAccess, Vector()) =
      loadHammer.translateAddressibleLocalLoad(
        hinputs,
        hamuts,
        currentFunctionHeader,
        locals,
        varId,
        variability,
        sourceExpr2.result,
        letIE.result.ownership)
    ConsecutorH(Vector(stackifyH, borrowAccess))
  }

  private def translateMundaneLet(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    sourceExprHE: ExpressionH[KindHT],
    sourceResultPointerTypeH: CoordH[KindHT],
    varId: IVarNameI[cI],
    variability: VariabilityI):
  StackifyH = {
    sourceExprHE.resultType.kind match {
      case NeverHT(_) => vwat()
      case _ =>
    }
    val varIdNameH = nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, varId))
    val localIndex =
      locals.addTypingPassLocal(varId, varIdNameH, Conversions.evaluateVariability(variability), sourceResultPointerTypeH)
    val stackNode =
      StackifyH(
        sourceExprHE,
        localIndex,
        Some(nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, varId))))
    stackNode
  }

  private def translateMundaneRestackify(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    sourceExprHE: ExpressionH[KindHT],
    varId: IVarNameI[cI]):
  RestackifyH = {
    locals.markRestackified(varId)

    sourceExprHE.resultType.kind match {
      case NeverHT(_) => vwat()
      case _ =>
    }
    val local = vassertSome(locals.get(varId))
    val stackNode =
      RestackifyH(
        sourceExprHE,
        local,
        Some(nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, varId))))
    stackNode
  }

    private def translateMundaneLetAndPoint(
      hinputs: HinputsI,
      hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      sourceExpr2: ReferenceExpressionIE,
      sourceExprHE: ExpressionH[KindHT],
      sourceResultPointerTypeH: CoordH[KindHT],
      letIE: LetAndLendIE,
      varId: IVarNameI[cI],
      variability: VariabilityI):
    ExpressionH[KindHT] = {
    val stackifyH =
      translateMundaneLet(
        hinputs,
        hamuts,
        currentFunctionHeader,
        locals,
        sourceExprHE,
        sourceResultPointerTypeH,
        varId,
        variability)

    val (borrowAccess, Vector()) =
      loadHammer.translateMundaneLocalLoad(
        hinputs,
        hamuts,
        currentFunctionHeader,
        locals,
        varId,
        sourceExpr2.result,
        letIE.result.ownership)

      ConsecutorH(Vector(stackifyH, borrowAccess))
  }

  def translateUnlet(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      unlet2: UnletIE):
  (ExpressionH[KindHT]) = {
    val local =
      locals.get(unlet2.variable.name) match {
        case None => {
          vfail("Unletting an unknown variable: " + unlet2.variable.name)
        }
        case Some(local) => local
      }

    unlet2.variable match {
      case ReferenceLocalVariableI(varId, _, localType2) => {
        val localTypeH = typeHammer.translateCoord(hinputs, hamuts, localType2)
        val unstackifyNode = UnstackifyH(local)
        locals.markUnstackified(varId)
        unstackifyNode
      }
      case AddressibleLocalVariableI(varId, variability, innerType2) => {
        val innerTypeH = typeHammer.translateCoord(hinputs, hamuts, innerType2)
        val structRefH =
          structHammer.makeBox(hinputs, hamuts, variability, innerType2, innerTypeH)

        val unstackifyBoxNode = UnstackifyH(local)
        locals.markUnstackified(varId)

        val innerLocal = locals.addHammerLocal(innerTypeH, Conversions.evaluateVariability(variability))

        val desH =
          DestroyH(
            unstackifyBoxNode.expectStructAccess(),
            Vector(innerTypeH),
            Vector(innerLocal))
        locals.markUnstackified(innerLocal.id)

        val unstackifyContentsNode = UnstackifyH(innerLocal)

        ConsecutorH(Vector(desH, unstackifyContentsNode))
      }
    }
  }

  def translateDestructureStaticSizedArray(
    hinputs: HinputsI,
    hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderI,
    locals: LocalsBox,
    des2: DestroyStaticSizedArrayIntoLocalsIE
  ): ExpressionH[KindHT] = {
    val DestroyStaticSizedArrayIntoLocalsIE(sourceExpr2, arrSeqI, destinationReferenceLocalVariables) = des2

    val (sourceExprHE, sourceExprDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);

    vassert(destinationReferenceLocalVariables.size == arrSeqI.size)

    // Destructure2 will immediately destroy any addressible references inside it
    // (see Destructure2 comments).
    // In the post-addressible world with all our boxes and stuff, an addressible
    // reference member is actually a borrow reference to a box.
    // Destructure2's destroying of addressible references translates to hammer
    // unborrowing the references to boxes.
    // However, the typingpass only supplied variables for the reference members,
    // so we need to introduce our own local variables here.

    val (localTypes, localIndices) =
      destinationReferenceLocalVariables
        .map(destinationReferenceLocalVariable => {
          val (memberRefTypeH) =
            typeHammer.translateCoord(hinputs, hamuts, arrSeqI.elementType.coord)
          val varIdNameH =
            nameHammer.translateFullName(
              hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, destinationReferenceLocalVariable.name))
          val localIndex =
            locals.addTypingPassLocal(
              destinationReferenceLocalVariable.name,
              varIdNameH,
              Conversions.evaluateVariability(destinationReferenceLocalVariable.variability),
              memberRefTypeH)
          (memberRefTypeH, localIndex)
        })
        .unzip

    val stackNode =
        DestroyStaticSizedArrayIntoLocalsH(
          sourceExprHE.expectStaticSizedArrayAccess(),
          localTypes,
          localIndices.toVector)

    expressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, stackNode, sourceExprDeferreds)
  }

  def translateDestroy(
      hinputs: HinputsI,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderI,
      locals: LocalsBox,
      des2: DestroyIE):
  ExpressionH[KindHT] = {
    val DestroyIE(sourceExpr2, structTI, destinationReferenceLocalVariables) = des2

    val (sourceExprHE, sourceExprDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);

//    val structDefT = hinputs.lookupStruct(TemplataCompiler.getStructTemplate(structTT.fullName))
    val structDefT = structHammer.lookupStruct(hinputs, hamuts, structTI)

    // Destructure2 will immediately destroy any addressible references inside it
    // (see Destructure2 comments).
    // In the post-addressible world with all our boxes and stuff, an addressible
    // reference member is actually a borrow reference to a box.
    // Destructure2's destroying of addressible references translates to hammer
    // unborrowing the references to boxes.
    // However, the typingpass only supplied variables for the reference members,
    // so we need to introduce our own local variables here.

    // We put Vector.empty here to make sure that we've consumed all the destination
    // reference local variables.
    val (Vector(), localTypes, localIndices) =
      structDefT.members.foldLeft((destinationReferenceLocalVariables, Vector[CoordH[KindHT]](), Vector[Local]()))({
        case ((remainingDestinationReferenceLocalVariables, previousLocalTypes, previousLocalIndices), member2) => {
          member2 match {
            case StructMemberI(name, variability, ReferenceMemberTypeI(memberRefType2)) => {
              val destinationReferenceLocalVariable = remainingDestinationReferenceLocalVariables.head

              val (memberRefTypeH) =
                typeHammer.translateCoord(hinputs, hamuts, memberRefType2)
              val varIdNameH = nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(currentFunctionHeader.id, destinationReferenceLocalVariable.name))
              val localIndex =
                locals.addTypingPassLocal(
                  destinationReferenceLocalVariable.name,
                  varIdNameH,
                  Conversions.evaluateVariability(destinationReferenceLocalVariable.variability),
                  memberRefTypeH)
              (remainingDestinationReferenceLocalVariables.tail, previousLocalTypes :+ memberRefTypeH, previousLocalIndices :+ localIndex)
            }
            // The struct might have addressibles in them, which translate to
            // borrow refs of boxes which contain things. We're moving that borrow
            // ref into a local variable. We'll then unlet the local variable, and
            // unborrow it.
            case StructMemberI(name, variability, AddressMemberTypeI(memberRefType2)) => {
              val (memberRefTypeH) =
                typeHammer.translateCoord(hinputs, hamuts, memberRefType2);
              // In the case of an addressible struct member, its variability refers to the
              // variability of the pointee variable, see structMember2
              val (boxStructRefH) =
                structHammer.makeBox(hinputs, hamuts, variability, memberRefType2, memberRefTypeH)
              // Structs only ever borrow boxes, boxes are only ever owned by the stack.
              val localBoxType = CoordH(vregionmut(MutableBorrowH), YonderH, boxStructRefH)
              val localIndex = locals.addHammerLocal(localBoxType, Final)

              (remainingDestinationReferenceLocalVariables, previousLocalTypes :+ localBoxType, previousLocalIndices :+ localIndex)
            }
          }
        }
      })

    val destructureH =
        DestroyH(
          sourceExprHE.expectStructAccess(),
          localTypes,
          localIndices.toVector)

    val unboxingsH =
      structDefT.members.zip(localTypes.zip(localIndices)).flatMap({
        case (StructMemberI(_, _, ReferenceMemberTypeI(_)), (localType, local)) => Vector.empty
        case (StructMemberI(_, _, AddressMemberTypeI(_)), (localType, local)) => {
          // localType is the box type.
          // First, unlet it, then discard the contents.
          // We discard instead of putting it into a local because address members
          // can't own, they only refer to a box owned elsewhere.
          val unstackifyNode = UnstackifyH(local)
          locals.markUnstackified(local.id)

          val discardNode = DiscardH(unstackifyNode)
          Vector(discardNode)
        }
      })

    val destructureAndUnboxings = ConsecutorH(Vector(destructureH) ++ unboxingsH)

    expressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, destructureAndUnboxings, sourceExprDeferreds)
  }
}
