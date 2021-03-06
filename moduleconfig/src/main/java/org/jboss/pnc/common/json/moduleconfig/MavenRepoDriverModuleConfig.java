/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.common.json.moduleconfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;
import org.jboss.pnc.common.json.AbstractModuleConfig;
import java.util.List;

@ToString
public class MavenRepoDriverModuleConfig extends AbstractModuleConfig{
    
    public static String MODULE_NAME = "maven-repo-driver";

    /**
     * Base url to maven repository manager (Indy)
     */
    private String baseUrl;

    /**
     * Comma-separated list of repository name patterns to use when considering whether a remote repository
     * represents an internal build (from a trusted build system, for instance).
     */
    @JsonProperty("internal-repo-patterns")
    private List<String> internalRepoPatterns;

    /**
     * Internal network (cloud) maven repository path
     */
    @JsonProperty
    private String internalRepositoryMvnPath;

    /**
     * External network maven repository path
     */
    @JsonProperty
    private String externalRepositoryMvnPath;

    public MavenRepoDriverModuleConfig(@JsonProperty("base-url") String baseUrl){
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<String> getInternalRepoPatterns() {
        return internalRepoPatterns;
    }

    public void setInternalRepoPatterns(List<String> internalRepoPatterns) {
        this.internalRepoPatterns = internalRepoPatterns;
    }

    public String getInternalRepositoryMvnPath() {
        return internalRepositoryMvnPath;
    }

    public void setInternalRepositoryMvnPath(String internalRepositoryMvnPath) {
        this.internalRepositoryMvnPath = internalRepositoryMvnPath;
    }

    public String getExternalRepositoryMvnPath() {
        return externalRepositoryMvnPath;
    }

    public void setExternalRepositoryMvnPath(String externalRepositoryMvnPath) {
        this.externalRepositoryMvnPath = externalRepositoryMvnPath;
    }
}
