package com.github.monun.farm.internal


interface Indexable {
    val index: Short
}

fun Array<out Indexable?>.binaryIndexSearch(key: Short, fromIndex: Int, toIndex: Int): Int {
    var low = fromIndex
    var high = toIndex - 1
    while (low <= high) {
        val mid = low + high ushr 1
        val midVal = this[mid]
        val cmp = midVal!!.index.compareTo(key)
        if (cmp < 0) low = mid + 1 else if (cmp > 0) high = mid - 1 else return mid // key found
    }
    return -(low + 1) // key not found.
}