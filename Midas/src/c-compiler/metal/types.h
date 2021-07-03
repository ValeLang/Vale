
#ifndef VALE_TYPES_H_
#define VALE_TYPES_H_

#include <string>
#include <vector>
#include <cassert>

#include "name.h"

// Defined elsewhere
class Name;
class PackageCoordinate;
class CodeLocation;

// Defined in this file
class Reference;
class Kind;
class Int;
class Bool;
class Str;
class Void;
class Float;
class Never;
class InterfaceKind;
class StructKind;
class RawArrayT;
class StaticSizedArrayT;
class RuntimeSizedArrayT;

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

struct RegionId {
  PackageCoordinate* packageCoord;
  std::string id;

  RegionId(PackageCoordinate* packageCoord_, std::string id_) :
      packageCoord(packageCoord_), id(id_) {}
};

// Interned
class Reference {
public:
  Ownership ownership;
  Location location;
  Kind* kind;
//  std::string debugStr;

  Reference(
      Ownership ownership_,
      Location location_,
      Kind* kind_
//      , const std::string& debugStr_
  ) :
      ownership(ownership_),
      location(location_),
      kind(kind_)
//    , debugStr(debugStr_)
  {

    if (ownership == Ownership::BORROW || ownership == Ownership::WEAK) {
      assert(location == Location::YONDER);
    }
  }

  // Someday, have a nice way to print out this Reference...
  std::string str() { return ""; }
};

class Kind {
public:
    virtual ~Kind() {}
    virtual PackageCoordinate* getPackageCoordinate() const = 0;
};

class Int : public Kind {
public:
  RegionId* regionId;
  int bits;

  Int(RegionId* regionId_, int bits_) :
      regionId(regionId_),
      bits(bits_) {}

  PackageCoordinate* getPackageCoordinate() const override { return regionId->packageCoord; }
};

class Bool : public Kind {
public:
  RegionId* regionId;

  Bool(RegionId* regionId_) :
      regionId(regionId_) {}

  PackageCoordinate* getPackageCoordinate() const override { return regionId->packageCoord; }
};

class Str : public Kind {
public:
  RegionId* regionId;

  Str(RegionId* regionId_) :
      regionId(regionId_) {}

  PackageCoordinate* getPackageCoordinate() const override { return regionId->packageCoord; }
};

class Float : public Kind {
public:
  RegionId* regionId;

  Float(RegionId* regionId_) :
      regionId(regionId_) {}

  PackageCoordinate* getPackageCoordinate() const override { return regionId->packageCoord; }
};

class Never : public Kind {
public:
  RegionId* regionId;

  Never(RegionId* regionId_) :
      regionId(regionId_) {}

  PackageCoordinate* getPackageCoordinate() const override { return regionId->packageCoord; }
};

class InterfaceKind : public Kind {
public:
    Name* fullName;

  InterfaceKind(Name* fullName_) :
      fullName(fullName_) {}

  PackageCoordinate* getPackageCoordinate() const override { return fullName->packageCoord; }

};

// Interned
class StructKind : public Kind {
public:
    Name* fullName;

    StructKind(Name* fullName_) :
        fullName(fullName_) {}

  PackageCoordinate* getPackageCoordinate() const override { return fullName->packageCoord; }
};

// Interned
class RawArrayT {
public:
  RegionId* regionId;
  Mutability mutability;
  Variability variability;
  Reference *elementType;

  RawArrayT(
      RegionId* regionId_,
      Mutability mutability_,
      Variability variability_,
      Reference* elementType_) :
      regionId(regionId_),
      mutability(mutability_),
      variability(variability_),
      elementType(elementType_) {}
};

// Interned
class StaticSizedArrayT : public Kind {
public:
  Name* name;

  StaticSizedArrayT(
      Name* name_) :
      name(name_) {}

  PackageCoordinate* getPackageCoordinate() const override { return name->packageCoord; }
};

class StaticSizedArrayDefinitionT {
public:
  Name* name;
  StaticSizedArrayT* kind;
  int size;
  RawArrayT* rawArray;

  StaticSizedArrayDefinitionT(
      Name* name_,
      StaticSizedArrayT* kind_,
      int size_,
      RawArrayT* rawArray_) :
      name(name_),
      kind(kind_),
      size(size_),
      rawArray(rawArray_) {}

};



// Interned
class RuntimeSizedArrayT : public Kind {
public:
  Name* name;

  RuntimeSizedArrayT(
      Name* name_) :
      name(name_) {}

  PackageCoordinate* getPackageCoordinate() const override { return name->packageCoord; }
};

class RuntimeSizedArrayDefinitionT {
public:
  Name* name;
  RuntimeSizedArrayT* kind;
  RawArrayT* rawArray;

  RuntimeSizedArrayDefinitionT(
      Name* name_,
      RuntimeSizedArrayT* kind_,
      RawArrayT* rawArray_) :
      name(name_),
      kind(kind_),
      rawArray(rawArray_) {}

};


class IContainer {
public:
    std::string humanName;
    CodeLocation* location;
};

#endif