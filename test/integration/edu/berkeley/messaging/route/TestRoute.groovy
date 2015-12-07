package edu.berkeley.messaging.route

import org.apache.camel.Endpoint

class TestRoute extends BasicInOutRoute {
    TestRoute(Endpoint fromEndpoint, Endpoint toEndpoint) {
        super()
        this.fromEndpoint = fromEndpoint
        this.toEndpoint = toEndpoint
    }
}
