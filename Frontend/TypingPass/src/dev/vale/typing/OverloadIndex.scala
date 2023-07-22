package dev.vale.typing

import dev.vale._
import dev.vale.postparsing.IImpreciseNameS
import dev.vale.typing.env.TemplatasStore
import dev.vale.typing.names._
import dev.vale.typing.ast.{FunctionCalleeCandidate, ICalleeCandidate, PrototypeTemplataCalleeCandidate}
import dev.vale.typing.types._

import scala.collection.mutable

// DO NOT SUBMIT document what merged is all about
// see "Test calling a generic function with a concept function"
class OwnershipToFunctionsMap(merged: Boolean) {
  val owns = mutable.HashSet[ICalleeCandidate]()
  val borrows = if (merged) owns else mutable.HashSet[ICalleeCandidate]()
  val shares = if (merged) owns else mutable.HashSet[ICalleeCandidate]()
  val weaks = if (merged) owns else mutable.HashSet[ICalleeCandidate]()

  def add(ownership: OwnershipT, function: ICalleeCandidate): Unit = {
    ownership match {
      case OwnT => {
        // DO NOT SUBMIT
        owns += function
        shares += function
      }
      case BorrowT => {
        // DO NOT SUBMIT
        borrows += function
        shares += function
      }
      case ShareT => shares += function
      case WeakT => weaks += function
    }
  }

  def get(ownership: OwnershipT): mutable.HashSet[ICalleeCandidate] = {
    ownership match {
      case OwnT => owns
      case BorrowT => borrows
      case ShareT => shares
      case WeakT => weaks
    }
  }
}

class KindToOwnershipToFunctionsMap {
  val structTemplates = mutable.HashMap[IdT[IStructTemplateNameT], OwnershipToFunctionsMap]()
  val interfaceTemplates = mutable.HashMap[IdT[IInterfaceTemplateNameT], OwnershipToFunctionsMap]() // DO NOT SUBMIT document an example
  val boundPlaceholderTemplates = mutable.HashMap[IdT[KindPlaceholderTemplateNameT], OwnershipToFunctionsMap]() // DO NOT SUBMIT document an example
  var rsas: Option[OwnershipToFunctionsMap] = None
  var ssas: Option[OwnershipToFunctionsMap] = None
  var i32Shortcut: Option[OwnershipToFunctionsMap] = None
  var i64Shortcut: Option[OwnershipToFunctionsMap] = None
  val intSizes = mutable.HashMap[Int, OwnershipToFunctionsMap]()
  var bool: Option[OwnershipToFunctionsMap] = None
  var void: Option[OwnershipToFunctionsMap] = None
  var str: Option[OwnershipToFunctionsMap] = None
  var float: Option[OwnershipToFunctionsMap] = None
  var frees: Option[OwnershipToFunctionsMap] = None // DO NOT SUBMIT document an example

  // if None, its a free param DO NOT SUBMIT
  def add(freeOrCoord: Option[CoordT], function: ICalleeCandidate): Unit = {
    freeOrCoord match {
      case None => {
        val ownershipToFunctionMap = getOrAdd(None)
        // DO NOT SUBMIT doc why all this
        ownershipToFunctionMap.add(OwnT, function)
        ownershipToFunctionMap.add(BorrowT, function)
        ownershipToFunctionMap.add(ShareT, function)
      }
      case Some(CoordT(ownership, _, kind)) => {
        val ownershipToFunctionMap = getOrAdd(Some(kind))
        ownershipToFunctionMap.add(ownership, function)
      }
    }
  }

  // If we're adding a function's parameter that's a placeholder for that own function, then it's a free
  // parameter. DO NOT SUBMIT doc better

  // If a function receives some other function's placeholder, then we're processing a bound function. DO NOT SUBMIT doc better
  def getOrAdd(freeOrKind: Option[KindT]): OwnershipToFunctionsMap = {
    freeOrKind match {
      case None => {
        frees match {
          case Some(x) => x
          case None => {
            val newMap = new OwnershipToFunctionsMap(true)
            frees = Some(newMap)
            newMap
          }
        }
      }
      case Some(kind) => {
        kind match {
          case IntT(32) => {
            i32Shortcut match {
              case Some(x) => x
              case None => {
                val newMap = new OwnershipToFunctionsMap(false)
                i32Shortcut = Some(newMap)
                newMap
              }
            }
          }
          case IntT(64) => {
            i64Shortcut match {
              case Some(x) => x
              case None => {
                val newMap = new OwnershipToFunctionsMap(false)
                i64Shortcut = Some(newMap)
                newMap
              }
            }
          }
          case IntT(otherSize) => intSizes.getOrElseUpdate(otherSize, new OwnershipToFunctionsMap(false))
          case BoolT() => {
            bool match {
              case Some(x) => x
              case None => {
                val newMap = new OwnershipToFunctionsMap(false)
                bool = Some(newMap)
                newMap
              }
            }
          }
          case VoidT() => {
            void match {
              case Some(x) => x
              case None => {
                val newMap = new OwnershipToFunctionsMap(false)
                void = Some(newMap)
                newMap
              }
            }
          }
          case StrT() => {
            str match {
              case Some(x) => x
              case None => {
                val newMap = new OwnershipToFunctionsMap(false)
                str = Some(newMap)
                newMap
              }
            }
          }
          case FloatT() => {
            float match {
              case Some(x) => x
              case None => {
                val newMap = new OwnershipToFunctionsMap(false)
                float = Some(newMap)
                newMap
              }
            }
          }
          case RuntimeSizedArrayTT(_) => {
            rsas match {
              case Some(x) => x
              case None => {
                val newMap = new OwnershipToFunctionsMap(false)
                rsas = Some(newMap)
                newMap
              }
            }
          }
          case StaticSizedArrayTT(_) => {
            ssas match {
              case Some(x) => x
              case None => {
                val newMap = new OwnershipToFunctionsMap(false)
                ssas = Some(newMap)
                newMap
              }
            }
          }
          case StructTT(structId) => {
            structTemplates.getOrElseUpdate(TemplataCompiler.getStructTemplate(structId), new OwnershipToFunctionsMap(false))
          }
          case InterfaceTT(interfaceId) => {
            interfaceTemplates.getOrElseUpdate(TemplataCompiler.getInterfaceTemplate(interfaceId), new OwnershipToFunctionsMap(false))
          }
          case KindPlaceholderT(placeholderId) => {
            // These arent like frees DO NOT SUBMIT doc
            boundPlaceholderTemplates.getOrElseUpdate(TemplataCompiler.getPlaceholderTemplate(placeholderId), new OwnershipToFunctionsMap(false))
          }
        }
      }
    }
  }

  def get(kind: KindT, kindToSuperKinds: ISubKindTT => Array[ISuperKindTT]): Vector[OwnershipToFunctionsMap] = {
    kind match {
      case IntT(32) => i32Shortcut.toVector ++ frees
      case IntT(64) => i64Shortcut.toVector ++ frees
      case IntT(otherSize) => intSizes.get(otherSize).toVector ++ frees
      case BoolT() => bool.toVector ++ frees
      case VoidT() => void.toVector ++ frees
      case StrT() => str.toVector ++ frees
      case FloatT() => float.toVector ++ frees
      case RuntimeSizedArrayTT(_) => rsas.toVector ++ frees
      case StaticSizedArrayTT(_) => ssas.toVector ++ frees
      case s @ StructTT(structId) => {
        structTemplates.get(TemplataCompiler.getStructTemplate(structId)).toVector ++
            kindToSuperKinds(s).flatMap(superKind => get(superKind, kindToSuperKinds)) ++
            frees
      }
      case i @ InterfaceTT(interfaceId) => {
        interfaceTemplates.get(TemplataCompiler.getInterfaceTemplate(interfaceId)).toVector ++
            kindToSuperKinds(i).flatMap(superKind => get(superKind, kindToSuperKinds)) ++
            frees
      }
      case k@KindPlaceholderT(placeholderId) => {
        boundPlaceholderTemplates.get(TemplataCompiler.getPlaceholderTemplate(placeholderId)).toVector ++
            kindToSuperKinds(k).flatMap(superKind => get(superKind, kindToSuperKinds)) ++
            frees
      }
      case other => vimpl(other)
    }
  }
}

class OverloadIndex() {
  // DO NOT SUBMIT maybe use an array instead of hash map for arity
  val nameToZeroArgFunction = mutable.HashMap[IImpreciseNameS, mutable.HashSet[ICalleeCandidate]]()
  val nameToArityToParamIndexToKindToOwnershipToFunctions =
    mutable.HashMap[IImpreciseNameS, mutable.HashMap[Int, Array[KindToOwnershipToFunctionsMap]]]()

  def add(
      candidateName: IImpreciseNameS,
      candidateParamMaybes: Vector[Option[CoordT]],
      calleeCandidate: ICalleeCandidate):
  Unit = {
    if (candidateParamMaybes.length == 0) {
      nameToZeroArgFunction
          .getOrElseUpdate(candidateName, new mutable.HashSet[ICalleeCandidate]())
          .add(calleeCandidate)
      return
    }

    var paramIndexToKindToOwnershipToFunctions =
      nameToArityToParamIndexToKindToOwnershipToFunctions
          .getOrElseUpdate(candidateName, mutable.HashMap[Int, Array[KindToOwnershipToFunctionsMap]]())
          .getOrElseUpdate(candidateParamMaybes.length, Array[KindToOwnershipToFunctionsMap]())
    // Expand it if it's not big enough
    if (paramIndexToKindToOwnershipToFunctions.length < candidateParamMaybes.length) {
      val newArr = new Array[KindToOwnershipToFunctionsMap](candidateParamMaybes.length)
      var i = 0
      while (i < paramIndexToKindToOwnershipToFunctions.length) {
        newArr(i) = paramIndexToKindToOwnershipToFunctions(i)
        i = i + 1
      }
      while (i < candidateParamMaybes.length) {
        newArr(i) = new KindToOwnershipToFunctionsMap()
        i = i + 1
      }
      // TODO(optimize) dont do this lookup twice. DO NOT SUBMIT
      vassertSome(nameToArityToParamIndexToKindToOwnershipToFunctions.get(candidateName))
          .put(candidateParamMaybes.length, newArr)
      paramIndexToKindToOwnershipToFunctions = newArr
    }
    var paramIndex = 0
    while (paramIndex < paramIndexToKindToOwnershipToFunctions.length) {
      val kindToOwnershipToFunctionsMap = paramIndexToKindToOwnershipToFunctions(paramIndex)
      val param = candidateParamMaybes(paramIndex)
      kindToOwnershipToFunctionsMap.add(param, calleeCandidate)
      paramIndex = paramIndex + 1
    }
  }

  def findOverloads(name: IImpreciseNameS, params: Vector[CoordT], kindToSuperKinds: ISubKindTT => Array[ISuperKindTT]): Array[ICalleeCandidate] = {
    if (params.isEmpty) {
      return nameToZeroArgFunction.get(name).toArray.flatten
    }

    nameToArityToParamIndexToKindToOwnershipToFunctions.get(name) match {
      case None => Array()
      case Some(arityToParamIndexToKindToOwnershipToFunctions) => {
        arityToParamIndexToKindToOwnershipToFunctions.get(params.length) match {
          case None => Array()
          case Some(paramIndexToKindToOwnershipToFunctions) => {
            val paramIndexToCandidateLists =
              U.map2Arr[CoordT, KindToOwnershipToFunctionsMap, Vector[mutable.HashSet[ICalleeCandidate]]](
                params.toArray, // TODO(optimize) DO NOT SUBMIT
                paramIndexToKindToOwnershipToFunctions,
                (param, kindToOwnershipToFunctions) => {
                  val resultA =
                    kindToOwnershipToFunctions.get(param.kind, kindToSuperKinds)
                        .map(_.get(param.ownership))
                  // Shortcut
                  if (resultA.isEmpty) {
                    return Array()
                  }
                  resultA
                })
            // Now we sort by the number of functions that match each particular parameter, so we can start with the
            // most unusual parameter type first.
            val sortedCandidateLists = paramIndexToCandidateLists.sortBy(_.size)

            // Let's union all the sets together
            val unusualestParamCandidateList = mutable.HashSet[ICalleeCandidate]()
            U.foreach[mutable.HashSet[ICalleeCandidate]](
              sortedCandidateLists(0),
              x => unusualestParamCandidateList ++= x)

            // Now we're basically going to get the intersections of all the above sets.
            val result =
              U.filterIterable[ICalleeCandidate](
                unusualestParamCandidateList, // Start with the parameter that has the least candidates
                (candidate) => {
                  var inAll = true
                  var i = 1 // Start at index 1 because we got the 0th element above for starting things out
                  while (i < sortedCandidateLists.length) {
                    val otherParamCandidates = sortedCandidateLists(i)
                    if (otherParamCandidates.exists(_.contains(candidate))) {
                      // Good, it's in this other parameter's candidates too, continue on
                      i = i + 1
                    } else {
                      // It's not in this other parameter's candidates, stop here.
                      i = sortedCandidateLists.length
                      inAll = false
                    }
                  }
                  inAll
                })
            result
          }
        }
      }
    }
  }
}
