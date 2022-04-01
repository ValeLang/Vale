package dev.vale.monomorphizing

import dev.vale.finalast.{BorrowH, ConsecutorH, DestroyH, DestroyStaticSizedArrayIntoLocalsH, DiscardH, ExpressionH, Final, KindH, Local, NeverH, NewStructH, OwnH, ReferenceH, StackifyH, UnstackifyH, YonderH}
import dev.vale.typing.Hinputs
import dev.vale.typing.ast.{DestroyStaticSizedArrayIntoLocalsTE, DestroyTE, FunctionHeaderT, LetAndLendTE, LetNormalTE, ReferenceExpressionTE, UnletTE}
import dev.vale.typing.env.{AddressibleLocalVariableT, ReferenceLocalVariableT}
import dev.vale.typing.names.{FullNameT, IVarNameT}
import dev.vale.typing.types.{AddressMemberTypeT, CoordT, ReferenceMemberTypeT, VariabilityT}
import dev.vale.{finalast, vassert, vfail, vwat}
import dev.vale.{finalast => m}
import dev.vale.finalast._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.names.IVarNameT
import dev.vale.typing.types._

class LetHammer(
    typeHammer: TypeHammer,
    nameHammer: NameHammer,
    structHammer: StructHammer,
    expressionHammer: ExpressionHammer,
    loadHammer: LoadHammer) {

  def translateLet(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      let2: LetNormalTE):
  ExpressionH[KindH] = {
    val LetNormalTE(localVariable, sourceExpr2) = let2

    val (sourceExprHE, deferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      typeHammer.translateReference(hinputs, hamuts, sourceExpr2.result.reference)

    sourceExprHE.resultType.kind match {
      // We'll never get to this let, so strip it out. See BRCOBS.
      case NeverH(_) => return sourceExprHE
      case _ =>
    }

    val stackifyNode =
      localVariable match {
        case ReferenceLocalVariableT(varId, variability, type2) => {
          translateMundaneLet(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExprHE, sourceResultPointerTypeH, varId, variability)
        }
        case AddressibleLocalVariableT(varId, variability, reference) => {
          translateAddressibleLet(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExprHE, sourceResultPointerTypeH, varId, variability, reference)
        }
      }

    expressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, stackifyNode, deferreds)
  }

  def translateLetAndPoint(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    letTE: LetAndLendTE):
  (ExpressionH[KindH]) = {
    val LetAndLendTE(localVariable, sourceExpr2, targetOwnership) = letTE

    val (sourceExprHE, deferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);
    val (sourceResultPointerTypeH) =
      typeHammer.translateReference(hinputs, hamuts, sourceExpr2.result.reference)

    val borrowAccess =
      localVariable match {
        case ReferenceLocalVariableT(varId, variability, type2) => {
          translateMundaneLetAndPoint(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2, sourceExprHE, sourceResultPointerTypeH, letTE, varId, variability)
        }
        case AddressibleLocalVariableT(varId, variability, reference) => {
          translateAddressibleLetAndPoint(
            hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2, sourceExprHE, sourceResultPointerTypeH, letTE, varId, variability, reference)
        }
      }

    expressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, borrowAccess, deferreds)
  }

  private def translateAddressibleLet(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExprHE: ExpressionH[KindH],
    sourceResultPointerTypeH: ReferenceH[KindH],
    varId: FullNameT[IVarNameT],
    variability: VariabilityT,
    reference: CoordT):
  ExpressionH[KindH] = {
    val (boxStructRefH) =
      structHammer.makeBox(hinputs, hamuts, variability, reference, sourceResultPointerTypeH)
    val expectedLocalBoxType = ReferenceH(OwnH, YonderH, boxStructRefH)

    val varIdNameH = nameHammer.translateFullName(hinputs, hamuts, varId)
    val local =
      locals.addTypingPassLocal(
        varId, varIdNameH, Conversions.evaluateVariability(variability), expectedLocalBoxType)

    StackifyH(
      NewStructH(
        Vector(sourceExprHE),
        hamuts.structDefs.find(_.getRef == boxStructRefH).get.members.map(_.name),
        expectedLocalBoxType),
      local,
      Some(nameHammer.translateFullName(hinputs, hamuts, varId)))
  }

  private def translateAddressibleLetAndPoint(
    hinputs: Hinputs,
    hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExpr2: ReferenceExpressionTE,
    sourceExprHE: ExpressionH[KindH],
    sourceResultPointerTypeH: ReferenceH[KindH],
    letTE: LetAndLendTE,
    varId: FullNameT[IVarNameT],
    variability: VariabilityT,
    reference: CoordT):
  (ExpressionH[KindH]) = {
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
        sourceExpr2.result.reference,
        letTE.result.reference.ownership)
    ConsecutorH(Vector(stackifyH, borrowAccess))
  }

  private def translateMundaneLet(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    sourceExprHE: ExpressionH[KindH],
    sourceResultPointerTypeH: ReferenceH[KindH],
    varId: FullNameT[IVarNameT],
    variability: VariabilityT):
  StackifyH = {
    sourceExprHE.resultType.kind match {
      case NeverH(_) => vwat()
      case _ =>
    }
    val varIdNameH = nameHammer.translateFullName(hinputs, hamuts, varId)
    val localIndex =
      locals.addTypingPassLocal(varId, varIdNameH, Conversions.evaluateVariability(variability), sourceResultPointerTypeH)
    val stackNode =
      StackifyH(
        sourceExprHE,
        localIndex,
        Some(nameHammer.translateFullName(hinputs, hamuts, varId)))
    stackNode
  }

    private def translateMundaneLetAndPoint(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      sourceExpr2: ReferenceExpressionTE,
      sourceExprHE: ExpressionH[KindH],
      sourceResultPointerTypeH: ReferenceH[KindH],
      letTE: LetAndLendTE,
      varId: FullNameT[IVarNameT],
      variability: VariabilityT):
    ExpressionH[KindH] = {
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
        sourceExpr2.result.reference,
        letTE.result.reference.ownership)

      ConsecutorH(Vector(stackifyH, borrowAccess))
  }

  def translateUnlet(
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      unlet2: UnletTE):
  (ExpressionH[KindH]) = {
    val local =
      locals.get(unlet2.variable.id) match {
        case None => {
          vfail("Unletting an unknown variable: " + unlet2.variable.id)
        }
        case Some(local) => local
      }

    unlet2.variable match {
      case ReferenceLocalVariableT(varId, _, localType2) => {
        val localTypeH = typeHammer.translateReference(hinputs, hamuts, localType2)
        val unstackifyNode = UnstackifyH(local)
        locals.markUnstackified(varId)
        unstackifyNode
      }
      case AddressibleLocalVariableT(varId, variability, innerType2) => {
        val innerTypeH = typeHammer.translateReference(hinputs, hamuts, innerType2)
        val structRefH =
          structHammer.makeBox(hinputs, hamuts, variability, innerType2, innerTypeH)

        val unstackifyBoxNode = finalast.UnstackifyH(local)
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
    hinputs: Hinputs,
    hamuts: HamutsBox,
      currentFunctionHeader: FunctionHeaderT,
    locals: LocalsBox,
    des2: DestroyStaticSizedArrayIntoLocalsTE
  ): ExpressionH[KindH] = {
    val DestroyStaticSizedArrayIntoLocalsTE(sourceExpr2, arrSeqT, destinationReferenceLocalVariables) = des2

    val (sourceExprHE, sourceExprDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);

    vassert(destinationReferenceLocalVariables.size == arrSeqT.size)

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
            typeHammer.translateReference(hinputs, hamuts, arrSeqT.elementType)
          val varIdNameH = nameHammer.translateFullName(hinputs, hamuts, destinationReferenceLocalVariable.id)
          val localIndex =
            locals.addTypingPassLocal(
              destinationReferenceLocalVariable.id,
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
      hinputs: Hinputs,
      hamuts: HamutsBox,
    currentFunctionHeader: FunctionHeaderT,
      locals: LocalsBox,
      des2: DestroyTE):
  ExpressionH[KindH] = {
    val DestroyTE(sourceExpr2, structTT, destinationReferenceLocalVariables) = des2

    val (sourceExprHE, sourceExprDeferreds) =
      expressionHammer.translate(hinputs, hamuts, currentFunctionHeader, locals, sourceExpr2);

    val structDefT = hinputs.lookupStruct(structTT)

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
      structDefT.members.foldLeft((destinationReferenceLocalVariables, Vector[ReferenceH[KindH]](), Vector[Local]()))({
        case ((remainingDestinationReferenceLocalVariables, previousLocalTypes, previousLocalIndices), member2) => {
          member2.tyype match {
            case ReferenceMemberTypeT(memberRefType2) => {
              val destinationReferenceLocalVariable = remainingDestinationReferenceLocalVariables.head

              val (memberRefTypeH) =
                typeHammer.translateReference(hinputs, hamuts, memberRefType2)
              val varIdNameH = nameHammer.translateFullName(hinputs, hamuts, destinationReferenceLocalVariable.id)
              val localIndex =
                locals.addTypingPassLocal(
                  destinationReferenceLocalVariable.id,
                  varIdNameH,
                  Conversions.evaluateVariability(destinationReferenceLocalVariable.variability),
                  memberRefTypeH)
              (remainingDestinationReferenceLocalVariables.tail, previousLocalTypes :+ memberRefTypeH, previousLocalIndices :+ localIndex)
            }
            // The struct might have addressibles in them, which translate to
            // borrow refs of boxes which contain things. We're moving that borrow
            // ref into a local variable. We'll then unlet the local variable, and
            // unborrow it.
            case AddressMemberTypeT(memberRefType2) => {
              val (memberRefTypeH) =
                typeHammer.translateReference(hinputs, hamuts, memberRefType2);
              // In the case of an addressible struct member, its variability refers to the
              // variability of the pointee variable, see structMember2
              val (boxStructRefH) =
                structHammer.makeBox(hinputs, hamuts, member2.variability, memberRefType2, memberRefTypeH)
              // Structs only ever borrow boxes, boxes are only ever owned by the stack.
              val localBoxType = ReferenceH(BorrowH, YonderH, boxStructRefH)
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
        case (structMember2, (localType, local)) => {
          structMember2.tyype match {
            case ReferenceMemberTypeT(_) => Vector.empty
            case AddressMemberTypeT(_) => {
              // localType is the box type.
              // First, unlet it, then discard the contents.
              // We discard instead of putting it into a local because address members
              // can't own, they only refer to a box owned elsewhere.
              val unstackifyNode = UnstackifyH(local)
              locals.markUnstackified(local.id)

              val discardNode = DiscardH(unstackifyNode)
              Vector(discardNode)
            }
          }
        }
      })

    val destructureAndUnboxings = ConsecutorH(Vector(destructureH) ++ unboxingsH)

    expressionHammer.translateDeferreds(
      hinputs, hamuts, currentFunctionHeader, locals, destructureAndUnboxings, sourceExprDeferreds)
  }
}
