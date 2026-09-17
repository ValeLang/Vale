use std::hash::Hash;

pub fn repeat<T: Clone>(elem: T, n: i32) -> Vec<T> {
    let mut result: Vec<T> = Vec::new();
    for _ in 0..n {
        result.push(elem.clone());
    }
    result
}

pub fn union_maps_expect_no_conflict<K, V, F>(
    a: &indexmap::IndexMap<K, V>,
    b: &indexmap::IndexMap<K, V>,
    equator: F,
) -> indexmap::IndexMap<K, V>
where K: Copy + Eq + Hash, V: Copy, F: Fn(V, V) -> bool,
{
    let mut result: indexmap::IndexMap<K, V> = indexmap::IndexMap::new();
    for (k, v) in a {
        result.insert(*k, *v);
    }
    for (k, v) in b {
        match result.get(k) {
            None => {}
            Some(previous_value) => assert!(equator(*v, *previous_value)),
        }
        result.insert(*k, *v);
    }
    result
}

pub fn replace_all(original: &str, replacements: &indexmap::IndexMap<&str, &str>) -> String {
    let mut str_acc: String = original.to_string();
    for (from, to) in replacements {
        str_acc = str_acc.replace(from, to);
    }
    str_acc
}

// Get all possible versions of original_map where the keys are the same
// but the value for each is randomized.
pub fn scrambles<T, Y>(original_map: &indexmap::IndexMap<T, Y>) -> Vec<indexmap::IndexMap<T, Y>>
where T: Clone + Eq + Hash, Y: Clone,
{
    let original_keys: Vec<T> = original_map.keys().cloned().collect();
    let original_vals: Vec<Y> = original_map.values().cloned().collect();
    // (Rust adaptation of Scala's List.permutations — Rust stdlib has no permutations method.)
    // Heap's algorithm, iterative, generates all permutations of original_vals.
    let mut vals_permuted: Vec<Vec<Y>> = Vec::new();
    let mut arr = original_vals.clone();
    let n = arr.len();
    vals_permuted.push(arr.clone());
    let mut c: Vec<usize> = vec![0; n];
    let mut i = 0;
    while i < n {
        if c[i] < i {
            if i % 2 == 0 { arr.swap(0, i); } else { arr.swap(c[i], i); }
            vals_permuted.push(arr.clone());
            c[i] += 1;
            i = 0;
        } else {
            c[i] = 0;
            i += 1;
        }
    }
    let keys_repeated = repeat(original_keys, vals_permuted.len() as i32);
    keys_repeated.into_iter().zip(vals_permuted.into_iter())
        .map(|(keys, vals)| keys.into_iter().zip(vals.into_iter()).collect::<indexmap::IndexMap<T, Y>>())
        .collect()
}

