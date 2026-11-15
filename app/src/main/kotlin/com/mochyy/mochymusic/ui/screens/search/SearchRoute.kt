// SearchRoute.kt

package com.mochyy.mochymusic.ui.screens.search

import android.net.Uri

// navigation decodes the query once on its own, so it must not be decoded again; urlencoder's + for a space made a typed + turn into a space
fun searchRoute(query: String): String = "search/${Uri.encode(query)}"
