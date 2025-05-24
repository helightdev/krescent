package dev.helight.krescent

import java.util.function.Consumer

interface HandlerChainParticipant {

    fun accept(visitor: Consumer<HandlerChainParticipant>) {
        visitor.accept(this)
    }
}