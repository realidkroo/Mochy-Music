// where the module feed's sign-in sends the browser back to: it keeps the session and brings mochy back to the front, on the module browser

package com.mochyy.mochymusic.modules.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.mochyy.mochymusic.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** for the sign-in's way back, a bare activity hilt doesn't build */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ModuleFeedEntryPoint {
    fun moduleFeed(): ModuleFeed
}

class ModuleBrowserLoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.data?.let { redirect ->
            EntryPointAccessors.fromApplication(applicationContext, ModuleFeedEntryPoint::class.java).moduleFeed().finishSignIn(redirect)
        }
        // mochy comes back as it was, still on the browser, which reloads once the session lands
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
