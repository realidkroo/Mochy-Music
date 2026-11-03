// the only classes mochy and a zip module share at runtime: just text, bytes and these few shapes cross, so neither side's kotlin, r8 or library versions can trip the other

package com.mochyy.mochymusic.module.bridge.shared

/** the module's end, mochy loads it from the zip's code and calls it */
interface ModuleBridge {
    /** makes the module from its class name and says what it is, as json */
    fun open(entry: String): String

    /** hands over mochy's end, before anything else is asked */
    fun attach(host: HostBridge)

    /** a call that answers straight away, json in and json out */
    fun ask(method: String, args: String): String

    /** a call that takes a while: it answers through reply from any thread, and id is how mochy cancels it */
    fun start(id: Long, method: String, args: String, data: ByteArray?, reply: BridgeReply)

    fun cancel(id: Long)

    /** the module is being removed or replaced, so its work stops and what it holds is let go */
    fun close()
}

/** mochy's end, everything the module can reach */
interface HostBridge {
    fun get(key: String): String?

    fun set(key: String, value: String?)

    /** http through mochy's own client, blocking; head is the request without its body, as json */
    fun http(head: String, body: ByteArray?): BridgeResponse

    fun log(message: String)

    fun folder(): String
}

/** how a slow call answers */
interface BridgeReply {
    /** one step of a check while it runs, as json */
    fun step(json: String)

    fun done(json: String)

    fun failed(message: String)
}

/** an http answer on its way back to the module, its headers as json */
class BridgeResponse(val code: Int, val headers: String, val body: ByteArray)
