/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote.query;

import com.alibaba.nacos.config.server.remote.query.handler.ConfigQueryHandler;

/**
 * ConfigQueryHandlerChainBuilder.
 * @author Nacos
 */
public interface ConfigQueryHandlerChainBuilder {
    
    /**
     * Builds the configuration query handler chain.
     * @return the configuration query handler chain
     */
    ConfigQueryHandlerChain build();
    
    /**
     * Adds a configuration query handler to the chain.
     * @param handler the handler to be added
     * @return the current builder instance
     */
    ConfigQueryHandlerChainBuilder addHandler(ConfigQueryHandler handler);
}