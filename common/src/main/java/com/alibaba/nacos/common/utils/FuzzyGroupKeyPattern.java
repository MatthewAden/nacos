/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.ANY_PATTERN;
import static com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_PATTERN_SPLITTER;

/**
 * Utility class for matching group keys against a given pattern.
 *
 * <p>This class provides methods to match group keys based on a pattern specified. It supports matching based on
 * dataId, group, and namespace components of the group key.
 *
 * @author stone-98
 * @date 2024/3/14
 */
public class FuzzyGroupKeyPattern {
    
    /**
     * Generates a fuzzy listen group key pattern based on the given dataId pattern, group, and optional tenant.
     * pattern result as: fixNamespace>>groupPattern>>dataIdPattern
     *
     * @param resourcePattern The pattern for matching dataIds or service names.
     * @param groupPattern  The groupPattern associated with the groups.
     * @param fixNamespace  (Optional) The tenant associated with the dataIds (can be null or empty).
     * @return A unique group key pattern for fuzzy listen.
     * @throws IllegalArgumentException If the dataId pattern or group is blank.
     */
    public static String generateFuzzyWatchGroupKeyPattern(final String resourcePattern, final String groupPattern,
             String fixNamespace) {
        if (StringUtils.isBlank(resourcePattern)) {
            throw new IllegalArgumentException("Param 'resourcePattern' is illegal, resourcePattern is blank");
        }
        if (StringUtils.isBlank(groupPattern)) {
            throw new IllegalArgumentException("Param 'groupPattern' is illegal, group is blank");
        }
        if (StringUtils.isBlank(fixNamespace)) {
            fixNamespace=DEFAULT_NAMESPACE_ID;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(fixNamespace);
        sb.append(FUZZY_WATCH_PATTERN_SPLITTER);
        sb.append(groupPattern);
        sb.append(FUZZY_WATCH_PATTERN_SPLITTER);
        sb.append(resourcePattern);
        return sb.toString().intern();
    }
    
    
    /**
     * Given a dataId, group, and a collection of completed group key patterns, returns the patterns that match.
     *
     * @param resourceName     The dataId or sservice name to match.
     * @param group            The group to match.
     * @param namespace        The group to match.
     * @param groupKeyPatterns The collection of completed group key patterns to match against.
     * @return A set of patterns that match the dataId and group.
     */
    public static Set<String> filterMatchedPatterns(Collection<String> groupKeyPatterns,String resourceName, String group, String namespace
            ) {
        if (CollectionUtils.isEmpty(groupKeyPatterns)) {
            return new HashSet<>(1);
        }
        Set<String> matchedPatternList = new HashSet<>();
        for (String keyPattern : groupKeyPatterns) {
            if (matchPattern(keyPattern,namespace, group,resourceName)) {
                matchedPatternList.add(keyPattern);
            }
        }
        return matchedPatternList;
    }
    
    public static boolean matchPattern(String groupKeyPattern,String namespace,String group,String resourceName){
        if(StringUtils.isBlank(namespace)){
            namespace=DEFAULT_NAMESPACE_ID;
        }
        String[] splitPatterns = groupKeyPattern.split(FUZZY_WATCH_PATTERN_SPLITTER);
        return splitPatterns[0].equals(namespace)&&itemMatched(splitPatterns[1],group)&&itemMatched(splitPatterns[2],resourceName);
    }
    
    public static String getNamespaceFromGroupKeyPattern(String groupKeyPattern){
        return groupKeyPattern.split(FUZZY_WATCH_PATTERN_SPLITTER)[0];
    }
    /**
     *
     * @param pattern
     * @param resource
     * @return
     */
    private static boolean itemMatched(String pattern,String resource){
        
        //accurate match without *
        if (!pattern.contains(ANY_PATTERN)){
            return pattern.equals(resource);
        }
    
        //match for '*' pattern
        if (pattern.equals(ANY_PATTERN)){
            return true;
        }
        
        //match for *{string}*
        if (pattern.startsWith(ANY_PATTERN)&&pattern.endsWith(ANY_PATTERN)){
            String pureString=pattern.replaceAll(ANY_PATTERN,"");
            return resource.contains(pureString);
        }
    
        //match for postfix match *{string}
        if (pattern.startsWith(ANY_PATTERN)&&pattern.endsWith(ANY_PATTERN)){
            String pureString=pattern.replaceAll(ANY_PATTERN,"");
            return resource.endsWith(pureString);
        }
    
        //match for prefix match {string}*
        if (pattern.startsWith(ANY_PATTERN)&&pattern.endsWith(ANY_PATTERN)){
            String pureString=pattern.replaceAll(ANY_PATTERN,"");
            return resource.startsWith(pureString);
        }
    
        return false;
    }
}
