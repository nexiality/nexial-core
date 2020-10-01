package org.nexial.commons.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.spi.FilterReply.NEUTRAL
import org.nexial.core.spi.NexialExecutionEvent
import org.nexial.core.spi.NexialListenerFactory


class LogEventFilter : Filter<ILoggingEvent>() {

    override fun decide(event: ILoggingEvent): FilterReply {
        NexialListenerFactory.fireEvent(NexialExecutionEvent.newLogInvokedEvent(event))
        return NEUTRAL
    }
}