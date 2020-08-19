
#ifndef VALE_INSTRUCTIONS_H_
#define VALE_INSTRUCTIONS_H_

class Expression;
class IRegister;
class ReferenceRegister;
class AddressRegister;
class Local;
class VariableId;
class StackHeight;

enum class RefCountCategory {
    VARIABLE_REF_COUNT,
    MEMBER_REF_COUNT,
    REGISTER_REF_COUNT
};

class Expression {
public:
    virtual ~Expression() {}

//    virtual UnconvertedReference* getResultType() const = 0;
};

class ConstantI64 : public Expression {
public:
  int value;

  ConstantI64(
      int value_)
      : value(value_) {}
};

class ConstantBool : public Expression {
public:
  bool value;

  ConstantBool(
      bool value_)
      : value(value_) {}
};


class ConstantVoid : public Expression {
public:
};


class ConstantStr : public Expression {
public:
  std::string value;

  ConstantStr(
      const std::string &value_) :
      value(value_) {}
};


class ConstantF64 : public Expression {
public:
  float value;
};


class Argument : public Expression {
public:
  UnconvertedReference* resultType;
  int argumentIndex;
  Argument(
      UnconvertedReference* resultType_,
      int argumentIndex_) :
      resultType(resultType_),
    argumentIndex(argumentIndex_) {}
};


class Stackify : public Expression {
public:
  Expression* sourceExpr;
  Local* local;
  std::string maybeName;

  Stackify(
      Expression* sourceExpr_,
    Local* local_,
    std::string maybeName_) :
      sourceExpr(sourceExpr_),
    local(local_),
        maybeName(maybeName_){}
};


class Unstackify : public Expression {
public:
  Local* local;

  Unstackify(Local* local_) : local(local_){}
};


class Destroy : public Expression {
public:
  Expression* structExpr;
  UnconvertedReference* structType;
  std::vector<UnconvertedReference*> localTypes;
  std::vector<Local*> localIndices;

  Destroy(
      Expression* structExpr_,
      UnconvertedReference* structType_,
      std::vector<UnconvertedReference*> localTypes_,
      std::vector<Local*> localIndices_) :
      structExpr(structExpr_),
      structType(structType_),
      localTypes(localTypes_),
      localIndices(localIndices_) {}
};


class StructToInterfaceUpcast : public Expression {
public:
  Expression* sourceExpr;
  UnconvertedReference* sourceStructType;
  StructReferend* sourceStructReferend;
  UnconvertedReference* targetInterfaceType;
  InterfaceReferend* targetInterfaceReferend;

  StructToInterfaceUpcast(
      Expression* sourceExpr_,
      UnconvertedReference* sourceStructType_,
      StructReferend* sourceStructReferend_,
      UnconvertedReference* targetInterfaceType_,
      InterfaceReferend* targetInterfaceReferend_) :
      sourceExpr(sourceExpr_),
      sourceStructType(sourceStructType_),
      sourceStructReferend(sourceStructReferend_),
      targetInterfaceType(targetInterfaceType_),
      targetInterfaceReferend(targetInterfaceReferend_) {}
};

class InterfaceToInterfaceUpcast : public Expression {
public:
  Expression* sourceExpr;
  InterfaceReferend* targetInterfaceRef;
};

class LocalStore : public Expression {
public:
  Local* local;
  Expression* sourceExpr;
  std::string localName;

  LocalStore(
      Local* local_,
      Expression* sourceExpr_,
      std::string localName_) :
      local(local_),
      sourceExpr(sourceExpr_),
      localName(localName_) {}
};


class LocalLoad : public Expression {
public:
  Local* local;
  UnconvertedOwnership targetOwnership;
  std::string localName;

  LocalLoad(
      Local* local,
      UnconvertedOwnership targetOwnership,
      std::string localName) :
      local(local),
    targetOwnership(targetOwnership),
        localName(localName) {}
};


class WeakAlias : public Expression {
public:
  Expression* sourceExpr;
  UnconvertedReference* sourceType;
  Referend* sourceReferend;

  WeakAlias(
      Expression* sourceExpr_,
      UnconvertedReference* sourceType_,
      Referend* sourceReferend_) :
    sourceExpr(sourceExpr_),
    sourceType(sourceType_),
    sourceReferend(sourceReferend_) {}
};


class MemberStore : public Expression {
public:
  Expression* structExpr;
  UnconvertedReference* structType;
  int memberIndex;
  Expression* sourceExpr;
  UnconvertedReference* resultType;
  std::string memberName;

  MemberStore(
      Expression* structExpr_,
      UnconvertedReference* structType_,
      int memberIndex_,
      Expression* sourceExpr_,
      UnconvertedReference* resultType_,
      std::string memberName_) :
    structExpr(structExpr_),
    structType(structType_),
    memberIndex(memberIndex_),
    sourceExpr(sourceExpr_),
    resultType(resultType_),
    memberName(memberName_) {}
};


class MemberLoad : public Expression {
public:
  Expression* structExpr;
  StructReferend* structId;
  UnconvertedReference* structType;
  int memberIndex;
  UnconvertedOwnership targetOwnership;
  UnconvertedReference* expectedMemberType;
  UnconvertedReference* expectedResultType;
  std::string memberName;

  MemberLoad(
      Expression* structExpr_,
      StructReferend* structId_,
      UnconvertedReference* structType_,
      int memberIndex_,
      UnconvertedOwnership targetOwnership_,
      UnconvertedReference* expectedMemberType_,
      UnconvertedReference* expectedResultType_,
      std::string memberName_) :
    structExpr(structExpr_),
    structId(structId_),
    structType(structType_),
    memberIndex(memberIndex_),
    targetOwnership(targetOwnership_),
    expectedMemberType(expectedMemberType_),
    expectedResultType(expectedResultType_),
    memberName(memberName_) {}
};


class NewArrayFromValues : public Expression {
public:
  std::vector<Expression*> sourceExprs;
  UnconvertedReference* arrayRefType;
  KnownSizeArrayT* arrayReferend;

  NewArrayFromValues(
      std::vector<Expression*> sourceExprs_,
      UnconvertedReference* arrayRefType_,
      KnownSizeArrayT* arrayReferend_) :
      sourceExprs(sourceExprs_),
      arrayRefType(arrayRefType_),
      arrayReferend(arrayReferend_) {}
};


class KnownSizeArrayStore : public Expression {
public:
  Expression* arrayExpr;
  Expression* indexExpr;
  Expression* sourceExpr;
};


class UnknownSizeArrayStore : public Expression {
public:
  Expression* arrayExpr;
  UnconvertedReference* arrayType;
  UnknownSizeArrayT* arrayReferend;
  Expression* indexExpr;
  UnconvertedReference* indexType;
  Referend* indexReferend;
  Expression* sourceExpr;
  UnconvertedReference* sourceType;
  Referend* sourceReferend;

  UnknownSizeArrayStore(
      Expression* arrayExpr_,
      UnconvertedReference* arrayType_,
      UnknownSizeArrayT* arrayReferend_,
      Expression* indexExpr_,
      UnconvertedReference* indexType_,
      Referend* indexReferend_,
      Expression* sourceExpr_,
      UnconvertedReference* sourceType_,
      Referend* sourceReferend_) :
    arrayExpr(arrayExpr_),
    arrayType(arrayType_),
    arrayReferend(arrayReferend_),
    indexExpr(indexExpr_),
    indexType(indexType_),
    indexReferend(indexReferend_),
    sourceExpr(sourceExpr_),
    sourceType(sourceType_),
    sourceReferend(sourceReferend_) {}
};


class UnknownSizeArrayLoad : public Expression {
public:
  Expression* arrayExpr;
  UnconvertedReference* arrayType;
  UnknownSizeArrayT* arrayReferend;
  Expression* indexExpr;
  UnconvertedReference* indexType;
  Referend* indexReferend;
  UnconvertedReference* resultType;
  UnconvertedOwnership targetOwnership;

  UnknownSizeArrayLoad(
      Expression* arrayExpr_,
      UnconvertedReference* arrayType_,
      UnknownSizeArrayT* arrayReferend_,
      Expression* indexExpr_,
      UnconvertedReference* indexType_,
      Referend* indexReferend_,
      UnconvertedReference* resultType_,
      UnconvertedOwnership targetOwnership_) :
    arrayExpr(arrayExpr_),
    arrayType(arrayType_),
    arrayReferend(arrayReferend_),
    indexExpr(indexExpr_),
    indexType(indexType_),
    indexReferend(indexReferend_),
    resultType(resultType_),
    targetOwnership(targetOwnership_) {}
};


class KnownSizeArrayLoad : public Expression {
public:
  Expression* arrayExpr;
  UnconvertedReference* arrayType;
  KnownSizeArrayT* arrayReferend;
  Expression* indexExpr;
  UnconvertedReference* resultType;
  UnconvertedOwnership targetOwnership;

  KnownSizeArrayLoad(
      Expression* arrayExpr_,
      UnconvertedReference* arrayType_,
      KnownSizeArrayT* arrayReferend_,
      Expression* indexExpr_,
      UnconvertedReference* resultType_,
      UnconvertedOwnership targetOwnership_) :
    arrayExpr(arrayExpr_),
    arrayType(arrayType_),
    arrayReferend(arrayReferend_),
    indexExpr(indexExpr_),
    resultType(resultType_),
    targetOwnership(targetOwnership_) {}
};


class Call : public Expression {
public:
    Prototype *function;
    std::vector<Expression *> argExprs;

    Call(
        Prototype *function_,
        std::vector<Expression *> argExprs_)
        : function(function_),
          argExprs(argExprs_) {}
};

class ExternCall : public Expression {
public:
    Prototype *function;
    std::vector<Expression *> argExprs;
    std::vector<UnconvertedReference *> argTypes;

    ExternCall(
        Prototype *function_,
        std::vector<Expression *> argExprs_,
        std::vector<UnconvertedReference *> argTypes_)
        : function(function_),
        argExprs(argExprs_),
        argTypes(argTypes_) {}
};


class InterfaceCall : public Expression {
public:
  std::vector<Expression*> argExprs;
  int virtualParamIndex;
  InterfaceReferend* interfaceRef;
  int indexInEdge;
  Prototype* functionType;

  InterfaceCall(
      std::vector<Expression*> argExprs_,
      int virtualParamIndex_,
      InterfaceReferend* interfaceRef_,
      int indexInEdge_,
      Prototype* functionType_) :
    argExprs(argExprs_),
    virtualParamIndex(virtualParamIndex_),
    interfaceRef(interfaceRef_),
    indexInEdge(indexInEdge_),
    functionType(functionType_) {}
};


class If : public Expression {
public:
  Expression* conditionExpr;
  Expression* thenExpr;
  UnconvertedReference* thenResultType;
  Expression* elseExpr;
  UnconvertedReference* elseResultType;
  UnconvertedReference* commonSupertype;

  If(
      Expression * conditionExpr_,
      Expression * thenExpr_,
      UnconvertedReference* thenResultType_,
      Expression * elseExpr_,
      UnconvertedReference* elseResultType_,
      UnconvertedReference* commonSupertype_) :
    conditionExpr(conditionExpr_),
    thenExpr(thenExpr_),
    thenResultType(thenResultType_),
    elseExpr(elseExpr_),
    elseResultType(elseResultType_),
    commonSupertype(commonSupertype_) {}
};

class While : public Expression {
public:
  Expression* bodyExpr;

  While(Expression* bodyExpr_) : bodyExpr(bodyExpr_) {}
};

class Consecutor : public Expression {
public:
  std::vector<Expression *> exprs;

  Consecutor(
      std::vector<Expression *> exprs_) :
      exprs(exprs_) {}
};

class Block : public Expression {
public:
  Expression * inner;
  UnconvertedReference* innerType;

  Block(Expression * inner_, UnconvertedReference* innerType_) :
  inner(inner_),
  innerType(innerType_) {}
};

class Return : public Expression {
public:
  Expression *sourceExpr;
  UnconvertedReference* sourceType;

  Return(
    Expression *sourceExpr_,
    UnconvertedReference* sourceType_)
    : sourceExpr(sourceExpr_),
      sourceType(sourceType_) {}
};


class ConstructUnknownSizeArray : public Expression {
public:
  Expression* sizeExpr;
  UnconvertedReference* sizeType;
  Referend* sizeReferend;
  Expression* generatorExpr;
  UnconvertedReference* generatorType;
  InterfaceReferend* generatorReferend;
  UnconvertedReference* arrayRefType;

  ConstructUnknownSizeArray(
      Expression* sizeExpr_,
      UnconvertedReference* sizeType_,
      Referend* sizeReferend_,
      Expression* generatorExpr_,
      UnconvertedReference* generatorType_,
      InterfaceReferend* generatorReferend_,
      UnconvertedReference* arrayRefType_) :
    sizeExpr(sizeExpr_),
    sizeType(sizeType_),
    sizeReferend(sizeReferend_),
    generatorExpr(generatorExpr_),
    generatorType(generatorType_),
    generatorReferend(generatorReferend_),
    arrayRefType(arrayRefType_) {}
};

class DestroyKnownSizeArrayIntoFunction : public Expression {
public:
  Expression* arrayExpr;
  UnconvertedReference* arrayType;
  KnownSizeArrayT* arrayReferend;
  Expression* consumerExpr;
  UnconvertedReference* consumerType;

  DestroyKnownSizeArrayIntoFunction(
      Expression* arrayExpr_,
      UnconvertedReference* arrayType_,
      KnownSizeArrayT* arrayReferend_,
      Expression* consumerExpr_,
      UnconvertedReference* consumerType_) :
    arrayExpr(arrayExpr_),
    arrayType(arrayType_),
    arrayReferend(arrayReferend_),
    consumerExpr(consumerExpr_),
    consumerType(consumerType_) {}
};

class DestroyKnownSizeArrayIntoLocals : public Expression {
public:
  Expression* arrayExpr;
  Expression* consumerExpr;
};

class DestroyUnknownSizeArray : public Expression {
public:
  Expression* arrayExpr;
  UnconvertedReference* arrayType;
  UnknownSizeArrayT* arrayReferend;
  Expression* consumerExpr;
  UnconvertedReference* consumerType;
  InterfaceReferend* consumerReferend;

  DestroyUnknownSizeArray(
      Expression* arrayExpr_,
      UnconvertedReference* arrayType_,
      UnknownSizeArrayT* arrayReferend_,
      Expression* consumerExpr_,
      UnconvertedReference* consumerType_,
      InterfaceReferend* consumerReferend_) :
    arrayExpr(arrayExpr_),
    arrayType(arrayType_),
    arrayReferend(arrayReferend_),
    consumerExpr(consumerExpr_),
    consumerType(consumerType_),
    consumerReferend(consumerReferend_) {}
};

class NewStruct : public Expression {
public:
  std::vector<Expression*> sourceExprs;
  UnconvertedReference* resultType;

  NewStruct(
      std::vector<Expression*> sourceExprs_,
      UnconvertedReference* resultType_) :
      sourceExprs(sourceExprs_),
      resultType(resultType_) {}
};

class ArrayLength : public Expression {
public:
  Expression* sourceExpr;
  UnconvertedReference* sourceType;

  ArrayLength(
      Expression* sourceExpr_,
      UnconvertedReference* sourceType_) :
      sourceExpr(sourceExpr_),
      sourceType(sourceType_) {}
};


class CheckRefCount : public Expression {
public:
  Expression* refExpr;
  RefCountCategory category;
  Expression* numExpr;
};


class Discard : public Expression {
public:
  Expression* sourceExpr;
  UnconvertedReference* sourceResultType;

  Discard(Expression* sourceExpr_, UnconvertedReference* sourceResultType_) :
      sourceExpr(sourceExpr_), sourceResultType(sourceResultType_) {}
};


class LockWeak : public Expression {
public:
  Expression* sourceExpr;
  UnconvertedReference* sourceType;

  Prototype* someConstructor;
  UnconvertedReference* someType;
  StructReferend* someReferend;

  Prototype* noneConstructor;
  UnconvertedReference* noneType;
  StructReferend* noneReferend;

  UnconvertedReference* resultOptType;
  InterfaceReferend* resultOptReferend;

  LockWeak(
      Expression* sourceExpr_,
      UnconvertedReference* sourceType_,
      Prototype* someConstructor_,
      UnconvertedReference* someType_,
      StructReferend* someReferend_,
      Prototype* noneConstructor_,
      UnconvertedReference* noneType_,
      StructReferend* noneReferend_,
      UnconvertedReference* resultOptType_,
      InterfaceReferend* resultOptReferend_) :
    sourceExpr(sourceExpr_),
    sourceType(sourceType_),
    someConstructor(someConstructor_),
    someType(someType_),
    someReferend(someReferend_),
    noneConstructor(noneConstructor_),
    noneType(noneType_),
    noneReferend(noneReferend_),
    resultOptType(resultOptType_),
    resultOptReferend(resultOptReferend_) {}
};

// Interned
class Local {
public:
    VariableId* id;
    UnconvertedReference* type;

    Local(
        VariableId* id_,
        UnconvertedReference* type_) :
    id(id_),
    type(type_) {}
};

// Interned
class VariableId {
public:
    int number;
    std::string maybeName;

    VariableId(
        int number_,
    std::string maybeName_) :
    number(number_),
    maybeName(maybeName_) {}
};

#endif