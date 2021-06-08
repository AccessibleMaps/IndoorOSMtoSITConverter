package de.twoSIT.util

/*
Usage: extend the companion object

class MySingleton{
    companion object : SingletonHolder<MySingleton, String>(::MySingleton)
}
 */
open class SingletonHolder<out T, in A>(private val constructor: (A) -> T) {
    @Volatile
    private var instance: T? = null

    fun getInstance(arg: A): T {
        return when {
            instance != null -> instance!!
            else -> synchronized(this) {
                if (instance == null) instance = constructor(arg)
                instance!!
            }
        }
    }
}