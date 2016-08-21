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
package edu.berkeley.messaging.route

import groovy.util.logging.Log4j
import org.apache.camel.Endpoint
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder

abstract class BasicInOutRoute extends RouteBuilder {

    // injected
    def defaultTransactionPolicy

    Endpoint fromEndpoint
    Endpoint toEndpoint

    @Log4j
    static class ExceptionProcessor implements Processor {
        @Override
        void process(Exchange exchange) {
            Throwable caused = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable)
            log.error("route exception", caused)
            // This property may be used by the onCompletion processor to distinguish between
            // a fault or an exception when onCompletion runs.
            exchange.setProperty("exception", caused)
        }
    }

    static class SuccessProcessor implements Processor {
        @Override
        void process(Exchange exchange) {
            // This property tells the onCompletion processor that the route succeeded.
            exchange.setProperty("success", true)
        }
    }

    static class OnCompletionProcessor implements Processor {
        @Override
        void process(Exchange exchange) {
            if (exchange.getProperty("success")) {
                Closure successClosure = exchange.getProperty("successClosure", Closure)
                if (successClosure) {
                    // in.body is the return result of the ldapProvisioningService handler method
                    successClosure(exchange.in.body)
                }
            } else {
                Closure failureClosure = exchange.getProperty("failureClosure", Closure)
                if (failureClosure) {
                    Throwable exception = exchange.getProperty("exception", Throwable)
                    // If exception: in.body is the original input message sent to ldapProvisioningRouteEndpoint
                    // If fault: in.body is the exchange out.body at time of return from the ldapProvisioningService handler.  If ldapProvisioningService handler method returns a value normally (but with the fault flag set explicitly), then that will be the body passed to the failureClosure.
                    // If exception is not set, then it must be a fault where setFault(true) was called on the message.
                    // The second parameter to the closure is a isFault boolean.
                    failureClosure(exchange.in.body, exception == null, exception)
                }
            }
        }
    }

    @Override
    void configure() {
        assert fromEndpoint && toEndpoint
        from(fromEndpoint)
                .handleFault() // convert fault messages into exceptions
                .onException(Exception).process(new ExceptionProcessor()).markRollbackOnlyLast().end()
                .onCompletion().process(new OnCompletionProcessor()).end()
                .transacted(defaultTransactionPolicy)
                .to(toEndpoint)
                .process(new SuccessProcessor()).end()

    }
}
