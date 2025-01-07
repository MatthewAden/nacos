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

package com.alibaba.nacos.naming.controllers.v3;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckerFactory;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.NumberUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.ClusterOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Cluster controller.
 *
 * @author Nacos
 */
@RestController
@RequestMapping(UtilsAndCommons.CLUSTER_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
public class ClusterControllerV3 {
    
    private final ClusterOperatorV2Impl clusterOperatorV2;
    
    public ClusterControllerV3(ClusterOperatorV2Impl clusterOperatorV2) {
        this.clusterOperatorV2 = clusterOperatorV2;
    }
    
    /**
     * Update cluster.
     */
    @PutMapping
    @Secured(resource = UtilsAndCommons.CLUSTER_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> update(HttpServletRequest request) throws Exception {
        final String namespaceId = WebUtils
                .optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        final String clusterName = WebUtils.required(request, CommonParams.CLUSTER_NAME);
        final String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        clusterMetadata.setHealthyCheckPort(NumberUtils.toInt(WebUtils.required(request, "checkPort")));
        clusterMetadata.setUseInstancePortForCheck(
                ConvertUtils.toBoolean(WebUtils.required(request, "useInstancePort4Check")));
        AbstractHealthChecker healthChecker = HealthCheckerFactory
                .deserialize(WebUtils.required(request, "healthChecker"));
        clusterMetadata.setHealthChecker(healthChecker);
        clusterMetadata.setHealthyCheckType(healthChecker.getType());
        clusterMetadata.setExtendData(
                UtilsAndCommons.parseMetadata(WebUtils.optional(request, "metadata", StringUtils.EMPTY)));
        clusterOperatorV2.updateClusterMetadata(namespaceId, serviceName, clusterName, clusterMetadata);
        
        return Result.success("ok");
    }
}