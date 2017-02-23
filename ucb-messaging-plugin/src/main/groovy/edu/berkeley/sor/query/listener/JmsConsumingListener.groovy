/*
 * Copyright (c) 2016, Regents of the University of California and
 * contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.berkeley.sor.query.listener

import org.apache.camel.AsyncCallback
import org.apache.camel.AsyncProcessor
import org.apache.camel.Endpoint
import org.apache.camel.Exchange
import org.apache.camel.component.jms.JmsConsumer

class JmsConsumingListener implements AsyncProcessor {
    Endpoint endpoint // injected by resources.groovy
    def service // injected by BootStrap.groovy
    JmsConsumer jmsConsumer

    /**
     * This has to be called from BootStrap.groovy after the service
     * has been injected by BootStrap.groovy.
     *
     * The service has to have an onMessage() method.
     */
    void start() {
        // register this consumer with the camelContext
        this.jmsConsumer = endpoint.createConsumer(this)
        // start it listening
        jmsConsumer.start()
        log.info("Consuming listener for endpoint " + endpoint + " has started")
    }

    /**
     * Called from BootStrap.groovy on shutdown.
     */
    void stop() {
        jmsConsumer.stop()
        log.info("Consuming listener for endpoint " + endpoint + " has stopped")
    }

    public void process(Exchange exchange) {
        service.onMessage(exchange.getIn())
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        process(exchange)
        callback.done(true)
        return true
    }
}
