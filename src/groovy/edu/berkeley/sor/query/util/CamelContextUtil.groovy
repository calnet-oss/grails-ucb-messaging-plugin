package edu.berkeley.sor.query.util

import org.apache.camel.CamelContext
import org.apache.camel.Endpoint
import org.apache.camel.impl.SimpleRegistry
import org.apache.camel.impl.DefaultCamelContext

class CamelContextUtil {
    CamelContext camelContext // injected

    public Endpoint getEndpoint(CharSequence uri) {
        return camelContext.getEndpoint(uri.toString())
    }

    /**
     * Create and start a new CamelContext with an empty SimpleRegistry. 
     * Primarily useful for using in unit tests.
     *
     * If you want a route where you can use the Java DSL, use
     * camelContext.addRoutes(new RouteBuilder(camelContext){...}).  Add the
     * routes before producing any messages.
     *
     * Once you have the CamelContext, and any routes you want, the next
     * step is to call createProducerTemplate() on the context.  With that
     * you can use sendBody() to send to a first destination.
     *
     * Don't forget to stop the context when you're done with it.
     *
     * Example:
     * <code>
     *   CamelContext camelContext = CamelContextUtil.createNewCamelContext()
     *   camelContext.getRegistry().getRegistry().put("another", new MyBean())
     *   camelContext.addRoutes(
     *     new RouteBuilder(camelContext) {
     *       public void configure() {
     *         from("direct:oneplace").to("bean:another")
     *       }
     *     }
     *   )
     *   ProducerTemplate tmpl = camelContext.createProducerTemplate()
     *   tmpl.sendBody("direct:oneplace", "my message")
     *   camelContext.stop()
     * </code>
     */
    public static CamelContext createNewCamelContext() {
        SimpleRegistry registry = new SimpleRegistry()
        DefaultCamelContext camelContext = new DefaultCamelContext(registry)
        camelContext.start()
        // wait max of 5 seconds for it to start
        for (int i = 0; i < 5; i++) {
            while (!camelContext.isStarted()) {
                sleep(1000)
            }
            if (!camelContext.isStarted())
                throw new RuntimeException("Timed out waiting for camelContext to start")
        }
        return camelContext
    }
}
