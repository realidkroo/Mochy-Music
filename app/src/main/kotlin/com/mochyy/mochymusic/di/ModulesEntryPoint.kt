package com.mochyy.mochymusic.di

import com.mochyy.mochymusic.modules.ModuleRegistry
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** for ui outside the modules' own view model, like a module page standing in for one of mochy's, which would otherwise start a whole modules screen's work */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ModulesEntryPoint {
    fun moduleRegistry(): ModuleRegistry
}
