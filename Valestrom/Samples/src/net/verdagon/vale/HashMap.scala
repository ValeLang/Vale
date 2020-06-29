package net.verdagon.vale

object HashMap {
  val code =
    """
      |fn panic(msg Str) {
      |  println(msg);
      |  = panic();
      |}
      |
      |fn abs(a Int) {
      |  = if (a < 0) { a * -1 } else { a }
      |}
      |
      |struct HashNode<K, V> {
      |  key K;
      |  value V;
      |}
      |
      |struct HashMap<K, V, H, E> {
      |  hasher H;
      |  equator E;
      |  table Array<mut, Opt<HashNode<K, V>>>;
      |  size Int;
      |}
      |
      |fn HashMap<K, V, H, E>(hasher H, equator E) HashMap<K, V, H, E> {
      |  HashMap<K, V, H, E>(hasher, equator, 0)
      |}
      |
      |fn HashMap<K, V, H, E>(hasher H, equator E, capacity Int) HashMap<K, V, H, E> {
      |  HashMap<K, V>(
      |      hasher,
      |      equator,
      |      Array<mut, Opt<HashNode<K, V>>>(
      |        capacity,
      |        &IFunction1<mut, Int, Opt<HashNode<K, V>>>((index){
      |          opt Opt<HashNode<K, V>> = None<HashNode<K, V>>();
      |          = opt;
      |        })),
      |      0)
      |}
      |
      |fn add<K, V, H, E>(map &HashMap<K, V, H, E>, key K, value V) Void {
      |  if (map.has(key)) {
      |    panic("Map already has given key!");
      |  }
      |  if ((map.size + 1) * 2 >= map.table.len()) {
      |    newSize =
      |        if (map.table.len() == 0) { 2 }
      |        else { map.table.len() * 2 };
      |    newTable =
      |        Array<mut, Opt<HashNode<K, V>>>(
      |            newSize,
      |            &IFunction1<mut, Int, Opt<HashNode<K, V>>>((index){
      |              opt Opt<HashNode<K, V>> = None<HashNode<K, V>>();
      |              = opt;
      |            }));
      |    i = 0;
      |    while (i < map.table.len()) {
      |      if (map.table[i].empty?()) {
      |        // do nothing
      |      } else {
      |        node? = (mut map.table[i] = None<HashNode<K, V>>());
      |        node = get(node?);
      |        addNodeToTable(&newTable, map.hasher, node);
      |      }
      |      mut i = i + 1;
      |    }
      |    mut map.table = newTable;
      |  }
      |
      |  addNodeToTable(map.table, map.hasher, HashNode<K, V>(key, value));
      |  mut map.size = map.size + 1;
      |}
      |
      |fn addNodeToTable<K, V, H>(table &Array<mut, Opt<HashNode<K, V>>>, hasher H, node HashNode<K, V>) {
      |  hash = (hasher)(node.key);
      |  startIndex = abs(hash mod table.len());
      |  index = findEmptyIndexForKey(table, startIndex, node.key);
      |
      |  opt Opt<HashNode<K, V>> = Some(node);
      |  mut table[index] = opt;
      |}
      |
      |fn findEmptyIndexForKey<K, V>(table &Array<mut, Opt<HashNode<K, V>>>, startIndex Int, key K) Int {
      |  i = 0;
      |  while (i < table.len()) {
      |    index = (startIndex + i) mod table.len();
      |    something = table[index];
      |    if (something.empty?()) {
      |      ret index;
      |    }
      |    // continue to next node
      |    mut i = i + 1;
      |  }
      |  = panic("findEmptyIndexForKey went past end of table!");
      |}
      |
      |fn findIndexOfKey<K, V, E>(table &Array<mut, Opt<HashNode<K, V>>>, equator E, startIndex Int, key K) Opt<Int> {
      |  i = 0;
      |  while (i < table.len()) {
      |    index = (startIndex + i) mod table.len();
      |    something = table[index];
      |    if (something.empty?()) {
      |      ret None<Int>();
      |    }
      |    node = something.get();
      |    if ((equator)(node.key, key)) {
      |      ret Some<Int>(index);
      |    }
      |    // continue to next node
      |    mut i = i + 1;
      |  }
      |  = panic("findIndexOfKey went past end of table! len " + Str(table.len()) + " and i " + Str(i));
      |}
      |
      |fn get<K, V, H, E>(self &HashMap<K, V, H, E>, key K) Opt<&V> {
      |  if (self.table.len() == 0) {
      |    ret None<&V>();
      |  }
      |  hash = (self.hasher)(key);
      |  startIndex = abs(hash mod self.table.len());
      |  index? = findIndexOfKey(self.table, self.equator, startIndex, key);
      |  if (index?.empty?()) {
      |    opt Opt<&V> = None<&V>();
      |    ret opt;
      |  }
      |  node = self.table[index?.get()].get();
      |  opt Opt<&V> = Some<&V>(node.value);
      |  ret opt;
      |}
      |
      |fn has<K, V, H, E>(self &HashMap<K, V, H, E>, key K) Bool {
      |  not(self.get(key).empty?())
      |}
      |
      |fn keys<K, V, H, E>(self &HashMap<K, V, H, E>) Array<imm, K> {
      |  list = List<K>();
      |  index = 0;
      |  while (index < self.table.len()) {
      |    node? = self.table[index];
      |    if (not(node?.empty?())) {
      |      list.add(node?.get().key);
      |    }
      |    mut index = index + 1;
      |  }
      |  = list.toArray<imm>();
      |}
      |
    """.stripMargin
}
