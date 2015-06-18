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
