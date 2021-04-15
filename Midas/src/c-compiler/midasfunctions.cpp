
#include "midasfunctions.h"
#include "region/iregion.h"
#include "region/linear/linear.h"

//void declareExtraInterfaceMethod(
//    GlobalState* globalState,
//    InterfaceReferend* referend,
//    InterfaceMethod* newMethod,
//    std::function<Prototype*(StructReferend*)> declarer,
//    std::function<void(StructReferend*, Prototype*)> bodyGenerator) {
//  auto program = globalState->program;
//
//  std::vector<Prototype*> substructPrototypes;
//
//  globalState->addInterfaceExtraMethod(referend, newMethod);
//
//  for (auto nameAndStruct : globalState->program->structs) {
//    for (auto edge : nameAndStruct.second->edges) {
//      if (edge->interfaceName == referend) {
//        auto substruct = edge->structName;
//
//        Reference *substructReference = nullptr;
//        assert(false); // TODO
//        Prototype *substructPrototype = nullptr;
//        assert(false); // TODO
//        LLVMTypeRef functionLT = nullptr;
//        assert(false); // TODO
//        // globalState->region->translateType(substructReference).c_str()
//
//        globalState->addEdgeExtraMethod(edge, newMethod, substructPrototype);
//
//        auto functionL = declarer(substructPrototype);
//        assert(globalState->extraFunctions.find(substructPrototype) != globalState->extraFunctions.end());
//
//        substructPrototypes.push_back(substructPrototype);
//      }
//    }
//  }
//
//  for (auto substructPrototype : substructPrototypes) {
//    auto substruct =
//        dynamic_cast<StructReferend*>(
//            substructPrototype->params[newMethod->virtualParamIndex]->referend);
//    assert(substruct);
//    bodyGenerator(substruct, substructPrototype);
//  }
//}
