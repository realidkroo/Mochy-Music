package com.mochyy.mochymusic.di

import com.mochyy.mochymusic.backup.CloudBackupManager
import com.mochyy.mochymusic.backup.CloudSignIn
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** for the cloud login callback and the scheduled backup worker, which hilt doesn't build */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudBackupEntryPoint {
    fun cloudSignIn(): CloudSignIn

    fun cloudBackupManager(): CloudBackupManager
}
