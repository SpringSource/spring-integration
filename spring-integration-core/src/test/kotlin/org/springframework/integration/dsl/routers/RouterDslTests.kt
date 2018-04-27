/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.dsl.routers

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.config.EnableIntegration
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.PollableChannel
import org.springframework.messaging.support.GenericMessage
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

/**
 * @author Artem Bilan
 *
 * @since 5.0.5
 */
@SpringJUnitConfig
@DirtiesContext
class RouterDslTests {

    @Autowired
    @Qualifier("routerTwoSubFlows.input")
    private lateinit var routerTwoSubFlowsInput: MessageChannel

    @Autowired
    @Qualifier("routerTwoSubFlowsOutput")
    private lateinit var routerTwoSubFlowsOutput: PollableChannel

    @Test
    fun `route to two subflows using lambda`() {

        this.routerTwoSubFlowsInput.send(GenericMessage<Any>(listOf(1, 2, 3, 4, 5, 6)))
        val receive = this.routerTwoSubFlowsOutput.receive(10000)

        val payload = receive?.payload

        assert(payload).isNotNull {
            it.isInstanceOf(List::class.java)
        }

        assert(payload).isEqualTo(listOf(3, 4, 9, 8, 15, 12))
    }


    @Configuration
    @EnableIntegration
    open class Config {

        @Bean
        open fun routerTwoSubFlows() =
                IntegrationFlow { f ->
                    f.split()
                            .route<Int, Boolean>({ p -> p % 2 == 0 },
                                    { m ->
                                        m.subFlowMapping(true, { sf -> sf.handle<Int> { p, _ -> p * 2 } })
                                                .subFlowMapping(false, { sf -> sf.handle<Int> { p, _ -> p * 3 } })
                                    })
                            .aggregate()
                            .channel { c -> c.queue("routerTwoSubFlowsOutput") }
                }

    }

}
