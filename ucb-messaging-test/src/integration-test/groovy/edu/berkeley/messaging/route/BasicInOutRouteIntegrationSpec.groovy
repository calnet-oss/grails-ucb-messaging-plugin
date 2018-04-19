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

import edu.berkeley.sor.query.util.CamelContextUtil
import edu.berkeley.sql.SqlService
import grails.core.GrailsApplication
import grails.testing.mixin.integration.Integration
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.apache.camel.CamelContext
import org.apache.camel.Endpoint
import org.apache.camel.Exchange
import org.apache.camel.ProducerTemplate
import org.apache.camel.model.RouteDefinition
import org.grails.web.json.JSONObject
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.annotation.PostConstruct
import javax.sql.DataSource

@Slf4j
@Integration
class BasicInOutRouteIntegrationSpec extends Specification {

    static transactional = false

    // injected
    CamelContext camelContext
    SqlService sqlService
    DataSource dataSource
    ProducerTemplate producerTemplate
    GrailsApplication grailsApplication
    PlatformTransactionManager transactionManager

    @Shared
    int quantityToSend = 10

    TestService testService

    private static class LazyHolder {
        Closure<Object> closure
        private Object value

        def getValue() {
            if (value == null) {
                value = closure()
            }
        }
    }

    @Shared
    CamelContextUtil camelContextUtil = new CamelContextUtil()

    @Shared
    Closure<Endpoint> sedaRouteEndpointClosure = { camelContextUtil.getEndpoint("seda:routeEndpoint?multipleConsumers=true&blockWhenFull=true") }

    @Shared
    Closure<Endpoint> directRouteEndpointClosure = { camelContextUtil.getEndpoint("direct:routeEndpoint") }

    @PostConstruct
    void initSpec() {
        camelContextUtil.camelContext = camelContext
    }

    def setup() {
        // Set up the testService
        grailsApplication.mainContext.registerBeanDefinition("testService", BeanDefinitionBuilder.genericBeanDefinition(TestService).beanDefinition)
        testService = (TestService) grailsApplication.mainContext.getBean("testService")
        assert producerTemplate
        testService.producerTemplate = producerTemplate
        testService.sqlService = sqlService
        assert transactionManager instanceof PlatformTransactionManager
        testService.regTransactionManager = transactionManager

        // Launch the test routes
        Endpoint destinationEndpoint = camelContextUtil.getEndpoint("bean:testService?method=receiveMessage")
        // SEDA Route
        camelContext.addRoutes(new TestRoute(sedaRouteEndpointClosure(), destinationEndpoint))
        // Direct Route
        camelContext.addRoutes(new TestRoute(directRouteEndpointClosure(), destinationEndpoint))
        // start routes
        camelContext.startAllRoutes()


        Sql sql = new Sql(dataSource)
        sql.execute("CREATE TABLE Test (timeCreated TIMESTAMP)" as String)
    }

    def cleanup() {
        Sql sql = new Sql(dataSource)
        sql.execute("DROP TABLE IF EXISTS Test" as String)

        // stop the routes
        List<String> idsToRemove = []
        camelContext.routeDefinitions.each { RouteDefinition routeDef ->
            idsToRemove.add(routeDef.id)
        }
        idsToRemove.each { String id ->
            camelContext.stopRoute(id)
            assert camelContext.removeRoute(id)
        }

        // remove the testService
        grailsApplication.mainContext.removeBeanDefinition("testService")
    }

    @Unroll
    void "test Camel success, fault and exception behavior"() {
        given:
        new Sql(dataSource).execute("TRUNCATE TABLE Test" as String)
        assert !countTestRows()
        Object lock = new Object()
        int successCount = 0
        int successMessageCount = 0
        int failureExceptionCount = 0
        int failureFaultCount = 0
        Map<String, Integer> exceptionMessageCount = [:]
        testService.receivePreparationClosure = receivePreparationClosure
        testService.routeEndpoint = routeEndpointClosure()

        when:
        for (int i = 0; i < quantityToSend; i++) {
            testService.sendMessage(["test": "foobar"] as JSONObject, { def result ->
                synchronized (lock) {
                    if (result == ["result": "success"]) {
                        successMessageCount++
                    }
                    successCount++
                }
            }, { def input, boolean isFault, Throwable exception ->
                synchronized (lock) {
                    if (exception) {
                        failureExceptionCount++
                        if (!exceptionMessageCount[exception.message])
                            exceptionMessageCount[exception.message] = 1
                        else
                            exceptionMessageCount[exception.message] = exceptionMessageCount[exception.message] + 1
                    } else {
                        failureFaultCount++
                    }
                }
            })
        }
        // possibly asynchronous if configured that way, so wait a little bit for completion if necessary
        int i
        for (i = 0; i < 800; i++) {
            synchronized (lock) {
                if (successCount + failureExceptionCount + failureFaultCount >= quantityToSend) {
                    break
                }
            }
            sleep(100) // sleep 100ms each iteration
        }
        log.info("Received within ${i * 100}ms and successCount = $successCount")

        then:
        testName && exceptionMessageCount.keySet() != null && countTestRows() == expectedTestRows
        testName && exceptionMessageCount.keySet() != null && successCount == expectedSuccessCount
        testName && exceptionMessageCount.keySet() != null && successMessageCount == expectedSuccessCount
        testName && exceptionMessageCount.keySet() != null && failureExceptionCount == expectedExceptionCount
        testName && exceptionMessageCount.keySet() != null && (exceptionMessageCount[expectedExceptionMessage] ?: 0) == expectedExceptionCount
        testName && exceptionMessageCount.keySet() != null && failureFaultCount == expectedFaultCount

        where:
        //
        // Test that success messsages, ecceptions thrown and fault messages are all handled correctly.
        // Also test transaction rollbacks on database inserts when an exception or fault occurs.
        //
        testName          | routeEndpointClosure       | receivePreparationClosure | expectedSuccessCount | expectedTestRows | expectedExceptionCount | expectedExceptionMessage | expectedFaultCount
        "sedaSuccess"     | sedaRouteEndpointClosure   | successClosure            | quantityToSend       | quantityToSend   | 0                      | null                     | 0
        "sedaException"   | sedaRouteEndpointClosure   | exceptionClosure          | 0                    | 0                | quantityToSend         | "test exception"         | 0
        "sedaFault"       | sedaRouteEndpointClosure   | faultClosure              | 0                    | quantityToSend   | quantityToSend         | "{result=success}"       | 0
        "directSuccess"   | directRouteEndpointClosure | successClosure            | quantityToSend       | quantityToSend   | 0                      | null                     | 0
        "directException" | directRouteEndpointClosure | exceptionClosure          | 0                    | 0                | quantityToSend         | "test exception"         | 0
        "directFault"     | directRouteEndpointClosure | faultClosure              | 0                    | quantityToSend   | quantityToSend         | "{result=success}"       | 0
    }

    void performTestTransaction(Sql sql) {
        sql.execute("INSERT INTO Test (timeCreated) VALUES(?)" as String, [new Date()])
    }

    int countTestRows() {
        Sql sql = new Sql(dataSource)
        return sql.firstRow("SELECT count(*) AS count FROM Test" as String).count
    }

    Closure getSuccessClosure() {
        return { Exchange exchange, Sql sql ->
            log.info("PREP")
            performTestTransaction(sql)
        }
    }

    Closure getExceptionClosure() {
        return { Exchange exchange, Sql sql ->
            performTestTransaction(sql)
            throw new RuntimeException("test exception")
        }
    }

    Closure getFaultClosure() {
        return { Exchange exchange, Sql sql ->
            performTestTransaction(sql)
            exchange.out.fault = true
        }
    }
}
