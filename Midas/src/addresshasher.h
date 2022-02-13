#ifndef ADDRESS_HASHER_H_
#define ADDRESS_HASHER_H_

#include <unordered_map>

template<typename T>
struct AddressHasher;

struct AddressNumberer {
public:
  std::unordered_map<void*, std::size_t> idByAddress;

  template<typename T>
  AddressHasher<T> makeHasher();
};

// Makes it so we can hash by addresses, but deterministically.
template<typename T>
struct AddressHasher {
public:
  AddressNumberer* numberer = nullptr;
  AddressHasher(AddressNumberer* numberer_) : numberer(numberer_) {}
  AddressHasher(const AddressHasher<T>& hasher_) : numberer(hasher_.numberer) {}
  AddressHasher(AddressHasher<T>&& hasher_) : numberer(hasher_.numberer) {}

  inline std::size_t operator()(T const& ptr) const {
    auto iter = numberer->idByAddress.find((void*)ptr);
    if (iter == numberer->idByAddress.end()) {
      iter = numberer->idByAddress.emplace(ptr, numberer->idByAddress.size()).first;
    }
    return iter->second;
  }
};


template<typename T>
AddressHasher<T> AddressNumberer::makeHasher() {
  return AddressHasher<T>(this);
}

template<typename T>
struct AddressEquator {
  inline bool operator()(T const& ptrA, T const& ptrB) const {
    return ptrA == ptrB;
  }
};

#endif
