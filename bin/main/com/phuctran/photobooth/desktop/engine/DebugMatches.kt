package com.phuctran.photobooth.desktop.engine

import com.phuctran.photobooth.desktop.remote.FirebaseManager
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    FirebaseManager.initialize()
    val layouts = FirebaseManager.fetchLayouts()
    println("==== LAYOUT IDS IN FIREBASE ====")
    layouts.forEach {
        println("- ID: ${it.id} | Title: ${it.title} | Print Size: ${it.printSizeLabel}")
    }
    println("================================")
}
