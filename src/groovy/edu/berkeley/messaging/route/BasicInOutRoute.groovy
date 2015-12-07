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
