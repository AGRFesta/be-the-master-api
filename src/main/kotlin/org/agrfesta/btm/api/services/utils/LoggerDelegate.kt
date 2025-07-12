package org.agrfesta.btm.api.services.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.companionObject

/**
 * A Kotlin property delegate for lazy SLF4J loggers.
 *
 * This delegate allows any class (including companion objects) to easily initialize
 * a logger without repeating the class reference.
 *
 * Usage:
 * ```kotlin
 * class MyService {
 *     private val logger by LoggerDelegate()
 * }
 * ```
 *
 * In case of a companion object, it ensures the enclosing class is used as the logger name:
 * ```kotlin
 * class MyClass {
 *     companion object {
 *         private val logger by LoggerDelegate()
 *     }
 * }
 * ```
 * Notes: https://www.baeldung.com/kotlin/logging
 *
 * @param R the type of the receiver where the property is declared.
 */
class LoggerDelegate<in R : Any>: ReadOnlyProperty<R, Logger> {

    /**
     * Provides the logger instance for the calling class, or its enclosing class
     * in case of companion objects.
     *
     * @param thisRef the receiver object where the delegate is used.
     * @param property the metadata of the delegated property (not used).
     * @return the SLF4J [Logger] associated with the class.
     */
    override fun getValue(thisRef: R, property: KProperty<*>): Logger =
        LoggerFactory.getLogger(getClassForLogging(thisRef.javaClass))

    /**
     * Determines the correct class to use for logging, especially handling
     * Kotlin companion objects to avoid logging under "Companion".
     *
     * @param javaClass the actual runtime class of the instance.
     * @return the enclosing class if the current class is a companion object,
     *         otherwise the class itself.
     */
    private fun <T : Any> getClassForLogging(javaClass: Class<T>): Class<*> {
        return javaClass.enclosingClass?.takeIf {
            it.kotlin.companionObject?.java == javaClass
        } ?: javaClass
    }

}
