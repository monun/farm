package com.github.monun.farm.util

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import javax.annotation.Nonnull

class UpstreamReference<T> : WeakReference<T> {
    constructor(referent: T) : super(referent)
    constructor(
        referent: T, q: ReferenceQueue<in T>?
    ) : super(referent, q)

    @Nonnull
    override fun get(): T {
        return super.get()
            ?: throw IllegalStateException("Cannot get reference as it has already been Garbage Collected")
    }

    override fun hashCode(): Int {
        return get().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return get() == other
    }

    override fun toString(): String {
        return get().toString()
    }
}