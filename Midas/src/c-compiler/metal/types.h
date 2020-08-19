
#ifndef VALE_TYPES_H_
#define VALE_TYPES_H_

#include <string>
#include <vector>
#include <cassert>

// Defined elsewhere
class Name;
class CodeLocation;

// Defined in this file
class Reference;
class Referend;
class Int;
class Bool;
class Str;
class Void;
class Float;
class Never;
class InterfaceReferend;
class StructReferend;
class RawArrayT;
class KnownSizeArrayT;
class UnknownSizeArrayT;

enum class UnconvertedOwnership {
  OWN,
  BORROW,
  WEAK,
  SHARE
};

enum class Ownership {
  OWN,
  BORROW,
  WEAK,
  SHARE
};

enum class Weakability {
  WEAKABLE,
  NON_WEAKABLE,
};

enum class UnconvertedWeakability {
  WEAKABLE,
  NON_WEAKABLE,
};

enum class Permission {
    READONLY,
    READWRITE,
    EXCLUSIVE_READWRITE
};

enum class Location {
    INLINE,
    YONDER
};

enum class Mutability {
    IMMUTABLE,
    MUTABLE
};

enum class Virtuality {
  NORMAL,
  ABSTRACT
};

enum class Variability {
    FINAL,
    VARYING
};

// Interned
class UnconvertedReference {
public:
  UnconvertedOwnership ownership;
  Location location;
  Referend* referend;
//  std::string debugStr;

  UnconvertedReference(
      UnconvertedOwnership ownership_,
      Location location_,
      Referend* referend_
//      , const std::string& debugStr_
  ) :
      ownership(ownership_),
      location(location_),
      referend(referend_)
//    , debugStr(debugStr_)
  {

    if (ownership == UnconvertedOwnership::BORROW || ownership == UnconvertedOwnership::WEAK) {
      assert(location == Location::YONDER);
    }
  }

  // Someday, have a nice way to print out this Reference...
  std::string str() { return ""; }
};

// Interned
class Reference {
public:
  Ownership ownership;
  Location location;
  Referend* referend;
//  std::string debugStr;

  Reference(
      Ownership ownership_,
      Location location_,
      Referend* referend_
//      , const std::string& debugStr_
      ) :
    ownership(ownership_),
    location(location_),
    referend(referend_)
//    , debugStr(debugStr_)
    {

    if (ownership == Ownership::BORROW || ownership == Ownership::WEAK) {
      assert(location == Location::YONDER);
    }
  }

  // Someday, have a nice way to print out this Reference...
  std::string str() { return ""; }
};

class Referend {
public:
    virtual ~Referend() {}
};

class Int : public Referend {
public:
};

class Bool : public Referend {
public:
};

class Str : public Referend {
public:
};

class Void : public Referend {
public:
};

class Float : public Referend {
public:
};

class Never : public Referend {
public:
};

class InterfaceReferend : public Referend {
public:
    Name* fullName;

  InterfaceReferend(Name* fullName_) :
      fullName(fullName_) {}

};

// Interned
class StructReferend : public Referend {
public:
    Name* fullName;

    StructReferend(Name* fullName_) :
        fullName(fullName_) {}

};

// Interned
class RawArrayT {
public:
  Mutability mutability;
  UnconvertedReference* elementType;

  RawArrayT(
      Mutability mutability_,
      UnconvertedReference* elementType_) :
      mutability(mutability_),
      elementType(elementType_) {}
};

// Interned
class KnownSizeArrayT : public Referend {
public:
  Name* name;
  int size;
  RawArrayT* rawArray;

  KnownSizeArrayT(
      Name* name_,
      int size_,
      RawArrayT* rawArray_) :
      name(name_),
      size(size_),
      rawArray(rawArray_) {}

};



// Interned
class UnknownSizeArrayT : public Referend {
public:
  Name* name;
  RawArrayT* rawArray;

  UnknownSizeArrayT(
      Name* name_,
      RawArrayT* rawArray_) :
      name(name_),
      rawArray(rawArray_) {}

};


class IContainer {
public:
    std::string humanName;
    CodeLocation* location;
};

#endif