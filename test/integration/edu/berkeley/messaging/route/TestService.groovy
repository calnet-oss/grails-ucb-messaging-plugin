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

import edu.berkeley.sql.SqlService
import grails.transaction.Transactional
import groovy.sql.Sql
import groovy.util.logging.Log4j
import org.apache.camel.Endpoint
import org.apache.camel.Exchange
import org.apache.camel.ExchangePattern
import org.apache.camel.ProducerTemplate
import org.apache.camel.impl.DefaultExchange
import org.codehaus.groovy.grails.web.json.JSONObject
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation

@Log4j
@Transactional(propagation = Propagation.NEVER, rollbackFor = Exception)
class TestService {
    Endpoint routeEndpoint
    ProducerTemplate producerTemplate
    SqlService sqlService
    PlatformTransactionManager regTransactionManager

    Closure receivePreparationClosure

    void sendMessage(JSONObject json, Closure successClosure, Closure failureClosure) {

        DefaultExchange _exchange = new DefaultExchange(routeEndpoint, ExchangePattern.InOut)
        _exchange.in.body = json
        _exchange.setProperty("successClosure", successClosure)
        _exchange.setProperty("failureClosure", failureClosure)
        producerTemplate.asyncCallback(routeEndpoint, _exchange, null)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception)
    Map receiveMessage(Exchange exchange) {
        Map inparam = (Map) exchange.in.body
        Sql sql = getRegSqlInstance()
        if (receivePreparationClosure) {
            receivePreparationClosure(exchange, sql)
        }
        return ["result": "success"]
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Sql getRegSqlInstance() {
        assert regTransactionManager
        return sqlService.getCurrentTransactionSql(regTransactionManager)
    }
}
