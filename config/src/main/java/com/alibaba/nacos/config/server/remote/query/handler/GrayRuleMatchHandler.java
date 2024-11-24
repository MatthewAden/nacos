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

package com.alibaba.nacos.config.server.remote.query.handler;

import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;

import java.io.IOException;

import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;

/**
 * GrayRuleMatchHandler.
 * This class represents a gray rule handler in the configuration query processing chain.
 * It checks if the request matches any gray rules and processes the request accordingly.
 *
 * @author Nacos
 */
public class GrayRuleMatchHandler extends AbstractConfigQueryHandler {
    
    private static final String GRAY_RULE_MATCH_HANDLER = "grayRuleMatchHandler";
    
    private ConfigCacheGray matchedGray;
    
    @Override
    public String getQueryHandlerName() {
        return GRAY_RULE_MATCH_HANDLER;
    }
    
    @Override
    public boolean canHandler(ConfigQueryChainRequest request) {
        // Check if the request matches any gray rules
        CacheItem cacheItem = ConfigChainEntryHandler.getThreadLocalCacheItem();
        if (cacheItem.getSortConfigGrays() != null && !cacheItem.getSortConfigGrays().isEmpty()) {
            for (ConfigCacheGray configCacheGray : cacheItem.getSortConfigGrays()) {
                if (configCacheGray.match(request.getAppLabels())) {
                    matchedGray = configCacheGray;
                }
            }
        }
        
        return matchedGray != null;
    }
    
    @Override
    public ConfigQueryChainResponse doHandle(ConfigQueryChainRequest request) throws IOException {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        
        long lastModified = matchedGray.getLastModifiedTs();
        String md5 = matchedGray.getMd5(ENCODE_UTF8);
        String encryptedDataKey = matchedGray.getEncryptedDataKey();
        String content = ConfigDiskServiceFactory.getInstance().getGrayContent(request.getDataId(),
                request.getGroup(), request.getTenant(), matchedGray.getGrayName());
        CacheItem cacheItem = ConfigChainEntryHandler.getThreadLocalCacheItem();
        
        response.setContent(content);
        response.setMd5(md5);
        response.setLastModified(lastModified);
        response.setEncryptedDataKey(encryptedDataKey);
        response.setMatchedGray(matchedGray);
        response.setContentType(cacheItem.getType());
        if (BetaGrayRule.TYPE_BETA.equals(matchedGray.getGrayRule().getType())) {
            response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.BETA);
        } else if (TagGrayRule.TYPE_TAG.equals(matchedGray.getGrayRule().getType())) {
            response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.TAG);
        }
        
        return response;
    }
}