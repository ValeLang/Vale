package net.verdagon.vale.templar.expression

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.UseP
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar._
import net.verdagon.vale.{IProfiler, vfail}

import scala.collection.immutable.List

// either want:
// 1. (nonchecking) thing that just trusts its good and extracts it into locals. (for lets)
// 2. (checking) thing that checks it matches, returns None if not, otherwise returns
//    a struct containing it all (for signatures)

class PatternTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    inferTemplar: InferTemplar,
    arrayTemplar: ArrayTemplar,
    convertHelper: ConvertHelper,
    destructorTemplar: DestructorTemplar,
    localHelper: LocalHelper) {
  // Note: This will unlet/drop the input expressions. Be warned.
  // patternInputExprs2 is a list of reference expression because they're coming in from
  // god knows where... arguments, the right side of a let, a variable, don't know!
  // If a pattern needs to send it to multiple places, the pattern is free to put it into
  // a local variable.
  // PatternTemplar must be sure to NOT USE IT TWICE! That would mean copying the entire
  // expression subtree that it contains!
  // Has "InferAnd" because we evaluate the template rules too.
  // Returns:
  // - Temputs
  // - Function state
  // - Exports, to toss into the environment
  // - Local variables
  def nonCheckingTranslateList(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
      patterns1: List[AtomAP],
      patternInputExprs2: List[ReferenceExpressionTE]):
  (List[ReferenceExpressionTE]) = {
    profiler.newProfile("nonCheckingTranslateList", fate.fullName.toString, () => {
      patterns1.zip(patternInputExprs2) match {
        case Nil => (Nil)
        case (pattern1, patternInputExpr2) :: _ => {
          val headLets =
            innerNonCheckingTranslate(
              temputs, fate, pattern1, patternInputExpr2);
          val tailLets =
            nonCheckingTranslateList(
              temputs, fate, patterns1.tail, patternInputExprs2.tail)
          (headLets ++ tailLets)
        }
        case _ => vfail("wat")
      }
    })
  }

  // Note: This will unlet/drop the input expression. Be warned.
  def nonCheckingInferAndTranslate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      rules: List[IRulexAR],
      typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
      pattern: AtomAP,
      inputExpr: ReferenceExpressionTE):
  (List[ReferenceExpressionTE]) = {
    profiler.newProfile("nonCheckingInferAndTranslate", fate.fullName.toString, () => {

      val templatasByRune =
        inferTemplar.inferFromArgCoords(fate.snapshot, temputs, List(), rules, typeByRune, localRunes, List(pattern), None, pattern.range, List(), List(ParamFilter(inputExpr.resultRegister.reference, None))) match {
          case (isf@InferSolveFailure(_, _, _, _, range, _, _)) => {
            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldn't figure out runes for pattern!\n" + isf))
          }
          case (InferSolveSuccess(tbr)) => (tbr.templatasByRune.mapValues(v => List(TemplataEnvEntry(v))))
        }

      fate.addEntries(opts.useOptimization, templatasByRune.map({ case (key, value) => (key, value) }).toMap)

      innerNonCheckingTranslate(
        temputs, fate, pattern, inputExpr)
    })
  }

  // Note: This will unlet/drop the input expression. Be warned.
  def nonCheckingTranslate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      pattern: AtomAP,
      inputExpr: ReferenceExpressionTE):
  (List[ReferenceExpressionTE]) = {
    innerNonCheckingTranslate(
      temputs, fate, pattern, inputExpr)
  }

  // the #1 case above
  // returns:
  // - the temputs
  // - the new seq num
  // - a bunch of lets.
  // - exports, to toss into the env
  // - function state
  private def innerNonCheckingTranslate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      pattern: AtomAP,
      unconvertedInputExpr: ReferenceExpressionTE):
  (List[ReferenceExpressionTE]) = {

    val AtomAP(range, lv @ LocalA(varName, _, _, _, _, _, _), maybeVirtuality, coordRuneA, maybeDestructure) = pattern

    if (maybeVirtuality.nonEmpty) {
      // This is actually to be expected for when we translate the patterns from the
      // function's parameters. Ignore them.
    }

    val expectedTemplata = fate.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(coordRuneA), Set(TemplataLookupContext))
    val expectedCoord =
      expectedTemplata match {
        case Some(CoordTemplata(coord)) => coord
        case Some(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, "not a coord!"))
        case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "not found!"))
      }

    // Now we convert m to a Marine. This also checks that it *can* be
    // converted to a Marine.
    val inputExpr =
      convertHelper.convert(fate.snapshot, temputs, range, unconvertedInputExpr, expectedCoord);

    // We make it here instead of down in the maybeDestructure clauses because whether we destructure it or not
    // is unrelated to wheter we destructure it.
    val export =
      localHelper.makeUserLocalVariable(
        temputs, fate, range, lv, expectedCoord)
    val let = LetNormalTE(export, inputExpr);
    fate.addVariable(export)
    val lets0 = List(let)

    maybeDestructure match {
      case None => {
        List(destructorTemplar.drop(fate, temputs, inputExpr))
      }
      case Some(listOfMaybeDestructureMemberPatterns) => {
        val CoordT(expectedContainerOwnership, expectedContainerPermission, expectedContainerKind) = expectedCoord
        expectedContainerOwnership match {
          case OwnT => {
            expectedContainerKind match {
              case StructRefT(_) => {
                // Example:
                //   struct Marine { bork: Bork; }
                //   Marine(b) = m;
                // In this case, expectedStructType1 = TypeName1("Marine") and
                // destructureMemberPatterns = List(CaptureSP("b", FinalP, None)).
                // Since we're receiving an owning reference, and we're *not* capturing
                // it in a variable, it will be destroyed and we will harvest its parts.
                nonCheckingTranslateDestroyStructInner(
                  temputs, fate, range, listOfMaybeDestructureMemberPatterns, expectedCoord, inputExpr)
              }
              case PackTT(_, underlyingStruct@StructRefT(_)) => {
                val structType2 = CoordT(expectedContainerOwnership, expectedContainerPermission, underlyingStruct)
                val reinterpretExpr2 = TemplarReinterpretTE(inputExpr, structType2)
                nonCheckingTranslateDestroyStructInner(
                  temputs, fate, range, listOfMaybeDestructureMemberPatterns, structType2, reinterpretExpr2)
              }
              case TupleTT(_, underlyingStruct@StructRefT(_)) => {
                val structType2 = CoordT(expectedCoord.ownership, expectedCoord.permission, underlyingStruct)
                val reinterpretExpr2 = TemplarReinterpretTE(inputExpr, structType2)
                nonCheckingTranslateDestroyStructInner(
                  temputs, fate, range, listOfMaybeDestructureMemberPatterns, structType2, reinterpretExpr2)
              }
              case staticSizedArrayT@StaticSizedArrayTT(size, RawArrayTT(elementType, _, _)) => {
                if (size != listOfMaybeDestructureMemberPatterns.size) {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, "Wrong num exprs!"))
                }

                val memberTypes = (0 until size).toList.map(_ => elementType)
                val memberLocalVariables = makeLocals(fate, memberTypes)
                val destroyTE = DestroyStaticSizedArrayIntoLocalsTE(inputExpr, staticSizedArrayT, memberLocalVariables)
                val lets = makeLetsForOwn(temputs, fate, listOfMaybeDestructureMemberPatterns, memberLocalVariables)
                (destroyTE :: lets)
              }
              case _ => vfail("impl!")
            }
          }
          case ConstraintT | ShareT => {
            // This can be used however many times we want, it's only aliasing, not moving.
            val containerAliasingExprTE =
              localHelper.softLoad(
                fate, range, LocalLookupTE(range, export, inputExpr.resultRegister.reference, FinalT), UseP)

            val CoordT(expectedContainerOwnership, expectedContainerPermission, expectedContainerKind) = expectedCoord

//            val arrSeqLocalVariable = localHelper.makeTemporaryLocal(fate, inputArraySeqExpr.resultRegister.reference)
//            val arrSeqLet = LetNormalTE(arrSeqLocalVariable, inputArraySeqExpr);

            val innerLets =
              listOfMaybeDestructureMemberPatterns.zipWithIndex
                .flatMap({
                  case (innerPattern, index) => {
                    val memberAddrExprTE =
                      expectedContainerKind match {
                        case structRefT@StructRefT(_) => {
                          // Example:
                          //   struct Marine { bork: Bork; }
                          //   Marine(b) = m;
                          // In this case, expectedStructType1 = TypeName1("Marine") and
                          // destructureMemberPatterns = List(CaptureSP("b", FinalP, None)).
                          // Since we're receiving an owning reference, and we're *not* capturing
                          // it in a variable, it will be destroyed and we will harvest its parts.

                          loadFromStruct(temputs, range, expectedContainerPermission, containerAliasingExprTE, structRefT, index)
                        }
                        case PackTT(_, underlyingStruct@StructRefT(_)) => {
                          val reinterpretedStructRefT = CoordT(expectedContainerOwnership, expectedContainerPermission, underlyingStruct)
                          val reinterpretedContainerAliasingExprTE = TemplarReinterpretTE(containerAliasingExprTE, reinterpretedStructRefT)
                          loadFromStruct(
                            temputs, range, expectedContainerPermission, reinterpretedContainerAliasingExprTE, underlyingStruct, index)
                        }
                        case TupleTT(_, underlyingStruct@StructRefT(_)) => {
                          val reinterpretedStructRefT = CoordT(expectedContainerOwnership, expectedContainerPermission, underlyingStruct)
                          val reinterpretedContainerAliasingExprTE = TemplarReinterpretTE(containerAliasingExprTE, reinterpretedStructRefT)
                          loadFromStruct(
                            temputs, range, expectedContainerPermission, reinterpretedContainerAliasingExprTE, underlyingStruct, index)
                        }
                        case staticSizedArrayT@StaticSizedArrayTT(size, RawArrayTT(elementType, _, _)) => {
                          if (size != listOfMaybeDestructureMemberPatterns.size) {
                            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Wrong num exprs!"))
                          }
                          loadFromStaticSizedArray(range, staticSizedArrayT, expectedCoord, expectedContainerOwnership, expectedContainerPermission, containerAliasingExprTE, index)
                        }
                        case _ => vfail("impl!")
                      }

                    val memberOwnershipInStruct = memberAddrExprTE.resultRegister.reference.ownership
                    val coerceToOwnership = loadResultOwnership(memberOwnershipInStruct)
                    val loadExpr = SoftLoadTE(memberAddrExprTE, coerceToOwnership, expectedContainerPermission)
                    innerNonCheckingTranslate(temputs, fate, innerPattern, loadExpr)
                  }
                })

            val packUnlet = localHelper.unletLocal(fate, `export`)
            val dropExpr = destructorTemplar.drop(fate, temputs, packUnlet)

            (lets0 ++ innerLets) :+ dropExpr
          }
        }
      }
    }
  }

  private def nonCheckingTranslateDestroyStructInner(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    innerPatternMaybes: List[AtomAP],
    structType2: CoordT,
    inputStructExpr: ReferenceExpressionTE):
  (List[ReferenceExpressionTE]) = {
    val CoordT(structOwnership, structPermission, structRefT @ StructRefT(_)) = structType2
    val structDefT = temputs.getStructDefForRef(structRefT)
    // We don't pattern match against closure structs.

    val memberLocalVariables =
      makeLocals(fate, structDefT.members.map(_.tyype.expectReferenceMember().reference))
    val destroy2 = DestroyTE(inputStructExpr, structRefT, memberLocalVariables)
    val lets = makeLetsForOwn(temputs, fate, innerPatternMaybes, memberLocalVariables)
    (destroy2 :: lets)
  }

  private def loadResultOwnership(memberOwnershipInStruct: OwnershipT): OwnershipT = {
    memberOwnershipInStruct match {
      case OwnT => ConstraintT
      case ConstraintT => ConstraintT
      case WeakT => WeakT
      case ShareT => ShareT
    }
  }

  private def loadFromStruct(
      temputs: Temputs,
      range: RangeS,
      structPermission: PermissionT,
      containerAlias: ReferenceExpressionTE,
      structRefT: StructRefT,
      index: Int) = {
    val structDefT = temputs.getStructDefForRef(structRefT)

    val member = structDefT.members(index)

    val memberCoord = member.tyype.expectReferenceMember().reference

    val memberPermissionInStruct = structDefT.members(index).tyype.reference.permission
    val resultPermission = Templar.intersectPermission(memberPermissionInStruct, structPermission)

    ReferenceMemberLookupTE(
      range,
      containerAlias,
      structDefT.fullName.addStep(structDefT.members(index).name),
      memberCoord,
      resultPermission,
      member.variability)
  }

  private def loadFromStaticSizedArray(
      range: RangeS,
      staticSizedArrayT: StaticSizedArrayTT,
      localCoord: CoordT,
      structOwnership: OwnershipT,
      structPermission: PermissionT,
      containerAlias: ReferenceExpressionTE,
      index: Int) = {
    arrayTemplar.lookupInStaticSizedArray(range, containerAlias, ConstantIntTE(index, 32), staticSizedArrayT)
  }

  private def makeLocals(
    fate: FunctionEnvironmentBox,
    memberTypes: List[CoordT]
  ): List[ReferenceLocalVariableT] = {
    memberTypes.map({
      case memberType => {
        localHelper.makeTemporaryLocal(fate, memberType)
      }
    })
  }

  private def makeLetsForOwn(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    innerPatternMaybes: List[AtomAP],
    memberLocalVariables: List[ReferenceLocalVariableT]
  ): List[ReferenceExpressionTE] = {
    innerPatternMaybes.zip(memberLocalVariables).flatMap({
      case ((innerPattern, localVariable)) => {
        val unletExpr = localHelper.unletLocal(fate, localVariable)
        innerNonCheckingTranslate(temputs, fate, innerPattern, unletExpr)
      }
    })
  }
}
