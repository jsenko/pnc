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
package org.jboss.pnc.rest.provider;

import org.apache.commons.lang3.StringUtils;
import org.jboss.pnc.common.Configuration;
import org.jboss.pnc.common.json.ConfigurationParseException;
import org.jboss.pnc.common.json.moduleconfig.ScmModuleConfig;
import org.jboss.pnc.common.json.moduleprovider.PncConfigProvider;
import org.jboss.pnc.model.BuildConfiguration;
import org.jboss.pnc.model.BuildConfigurationAudited;
import org.jboss.pnc.model.IdRev;
import org.jboss.pnc.model.ProductVersion;
import org.jboss.pnc.rest.provider.collection.CollectionInfo;
import org.jboss.pnc.rest.provider.collection.CollectionInfoCollector;
import org.jboss.pnc.rest.restmodel.BuildConfigurationAuditedRest;
import org.jboss.pnc.rest.restmodel.BuildConfigurationRest;
import org.jboss.pnc.rest.validation.ConflictedEntryValidator;
import org.jboss.pnc.rest.validation.ValidationBuilder;
import org.jboss.pnc.rest.validation.exceptions.ConflictedEntryException;
import org.jboss.pnc.rest.validation.exceptions.InvalidEntityException;
import org.jboss.pnc.rest.validation.exceptions.ValidationException;
import org.jboss.pnc.rest.validation.groups.WhenCreatingNew;
import org.jboss.pnc.rest.validation.groups.WhenUpdating;
import org.jboss.pnc.spi.datastore.repositories.BuildConfigurationAuditedRepository;
import org.jboss.pnc.spi.datastore.repositories.BuildConfigurationRepository;
import org.jboss.pnc.spi.datastore.repositories.PageInfoProducer;
import org.jboss.pnc.spi.datastore.repositories.ProductVersionRepository;
import org.jboss.pnc.spi.datastore.repositories.SortInfoProducer;
import org.jboss.pnc.spi.datastore.repositories.api.RSQLPredicateProducer;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.jboss.pnc.rest.utils.StreamHelper.nullableStreamOf;
import static org.jboss.pnc.spi.datastore.predicates.BuildConfigurationPredicates.isNotArchived;
import static org.jboss.pnc.spi.datastore.predicates.BuildConfigurationPredicates.withBuildConfigurationSetId;
import static org.jboss.pnc.spi.datastore.predicates.BuildConfigurationPredicates.withDependantConfiguration;
import static org.jboss.pnc.spi.datastore.predicates.BuildConfigurationPredicates.withName;
import static org.jboss.pnc.spi.datastore.predicates.BuildConfigurationPredicates.withProductId;
import static org.jboss.pnc.spi.datastore.predicates.BuildConfigurationPredicates.withProductVersionId;
import static org.jboss.pnc.spi.datastore.predicates.BuildConfigurationPredicates.withProjectId;

@Stateless
public class BuildConfigurationProvider extends AbstractProvider<BuildConfiguration, BuildConfigurationRest> {

    private BuildConfigurationAuditedRepository buildConfigurationAuditedRepository;

    private ProductVersionRepository productVersionRepository;

    private ScmModuleConfig moduleConfig;

    @Inject
    public BuildConfigurationProvider(BuildConfigurationRepository buildConfigurationRepository,
                                      BuildConfigurationAuditedRepository buildConfigurationAuditedRepository,
                                      RSQLPredicateProducer rsqlPredicateProducer, SortInfoProducer sortInfoProducer, PageInfoProducer pageInfoProducer,
                                      ProductVersionRepository productVersionRepository,
                                      Configuration configuration) throws ConfigurationParseException {
        super(buildConfigurationRepository, rsqlPredicateProducer, sortInfoProducer, pageInfoProducer);
        this.buildConfigurationAuditedRepository = buildConfigurationAuditedRepository;
        this.productVersionRepository = productVersionRepository;
        this.moduleConfig = configuration.getModuleConfig(new PncConfigProvider<>(ScmModuleConfig.class));
    }

    // needed for EJB/CDI
    public BuildConfigurationProvider() {
    }

    public CollectionInfo<BuildConfigurationRest> getAllNonArchived(Integer pageIndex, Integer pageSize, String sortingRsql,
            String query) {
        return queryForCollection(pageIndex, pageSize, sortingRsql, query, isNotArchived());
    }

    public CollectionInfo<BuildConfigurationRest> getAllForProject(Integer pageIndex, Integer pageSize, String sortingRsql,
            String query, Integer projectId) {
        return queryForCollection(pageIndex, pageSize, sortingRsql, query, withProjectId(projectId), isNotArchived());
    }

    public CollectionInfo<BuildConfigurationRest> getAllForProduct(int pageIndex, int pageSize, String sortingRsql,
            String query, Integer productId) {
        return queryForCollection(pageIndex, pageSize, sortingRsql, query, withProductId(productId), isNotArchived());
    }

    public CollectionInfo<BuildConfigurationRest> getAllForProductAndProductVersion(int pageIndex, int pageSize,
            String sortingRsql, String query, Integer productId, Integer versionId) {
        return queryForCollection(pageIndex, pageSize, sortingRsql, query, withProductVersionId(versionId), isNotArchived());
    }

    public CollectionInfo<BuildConfigurationRest> getAllForBuildConfigurationSet(int pageIndex, int pageSize,
            String sortingRsql, String query, Integer buildConfigurationSetId) {
        return queryForCollection(pageIndex, pageSize, sortingRsql, query,
                withBuildConfigurationSetId(buildConfigurationSetId), isNotArchived());
    }

    @Override
    protected void validateBeforeSaving(BuildConfigurationRest buildConfigurationRest) throws ValidationException {
        super.validateBeforeSaving(buildConfigurationRest);
        validateIfItsNotConflicted(buildConfigurationRest);
        validateInternalRepository(buildConfigurationRest.getScmRepoURL());
    }

    @Override
    protected void validateBeforeUpdating(Integer id, BuildConfigurationRest buildConfigurationRest)
            throws ValidationException {
        super.validateBeforeUpdating(id, buildConfigurationRest);
        validateIfItsNotConflicted(buildConfigurationRest);
        validateDependencies(buildConfigurationRest.getId(), buildConfigurationRest.getDependencyIds());
        validateInternalRepository(buildConfigurationRest.getScmRepoURL());
    }

    public void validateInternalRepository(String internalRepoUrl) throws InvalidEntityException {
        String internalScmAuthority = moduleConfig.getInternalScmAuthority();

        if (StringUtils.isNotBlank(internalScmAuthority)) {
            String expectedPrefix = "git+ssh://" + internalScmAuthority;
            if (!internalRepoUrl.startsWith(expectedPrefix)) {
                throw new InvalidEntityException("Internal repository url has to start with: " + expectedPrefix);
            }
        }
    }

    private void validateDependencies(Integer buildConfigId, Set<Integer> dependenciesIds) throws InvalidEntityException {

        if (dependenciesIds == null || dependenciesIds.isEmpty()) {
            return;
        }

        BuildConfiguration buildConfig = repository.queryById(buildConfigId);
        for (Integer dependencyId : dependenciesIds) {

            ValidationBuilder.validateObject(buildConfig, WhenUpdating.class).validateCondition(
                    !buildConfig.getId().equals(dependencyId), "A build configuration cannot depend on itself");

            BuildConfiguration dependency = repository.queryById(dependencyId);
            ValidationBuilder.validateObject(buildConfig, WhenUpdating.class)
                    .validateCondition(!dependency.getAllDependencies().contains(buildConfig), "Cannot add dependency from : "
                            + buildConfig.getId() + " to: " + dependencyId + " because it would introduce a cyclic dependency");
        }
    }

    private void validateIfItsNotConflicted(BuildConfigurationRest buildConfigurationRest)
            throws ConflictedEntryException, InvalidEntityException {
        ValidationBuilder.validateObject(buildConfigurationRest, WhenUpdating.class).validateConflict(() -> {
            BuildConfiguration buildConfigurationFromDB = repository.queryByPredicates(withName(buildConfigurationRest.getName()));

            // don't validate against myself
            if (buildConfigurationFromDB != null && !buildConfigurationFromDB.getId().equals(buildConfigurationRest.getId())) {
                return new ConflictedEntryValidator.ConflictedEntryValidationError(buildConfigurationFromDB.getId(),
                        BuildConfiguration.class, "Build configuration with the same name already exists");
            }
            return null;
        });
    }

    @Override
    protected Function<? super BuildConfiguration, ? extends BuildConfigurationRest> toRESTModel() {
        return buildConfiguration -> new BuildConfigurationRest(buildConfiguration);
    }

    @Override
    protected Function<? super BuildConfigurationRest, ? extends BuildConfiguration> toDBModel() {
        return buildConfigRest -> {

            BuildConfiguration.Builder builder = buildConfigRest.toDBEntityBuilder();

            if (buildConfigRest.getId() == null) {
                return builder.build();
            }

            BuildConfiguration buildConfigDB = repository.queryById(buildConfigRest.getId());
            // If updating an existing record, need to replace several fields from the rest entity with values from DB
            if (buildConfigDB != null) {
                builder.lastModificationTime(buildConfigDB.getLastModificationTime()); // Handled by JPA @Version
                builder.creationTime(buildConfigDB.getCreationTime()); // Immutable after creation
                if (buildConfigRest.getDependencyIds() == null) {
                    // If the client request does not include a list of dependencies, just keep the current set
                    builder.dependencies(buildConfigDB.getDependencies());
                }
            }

            return builder.build();
        };
    }

    public void archive(Integer buildConfigurationId)  throws ValidationException {
        ValidationBuilder.validateObject(WhenUpdating.class).validateAgainstRepository(repository, buildConfigurationId,
                true);
        BuildConfiguration buildConfiguration = repository.queryById(buildConfigurationId);
        buildConfiguration.setArchived(true);
        repository.save(buildConfiguration);
    }

    public Integer clone(Integer buildConfigurationId) throws ValidationException {
        ValidationBuilder.validateObject(WhenCreatingNew.class).validateAgainstRepository(repository, buildConfigurationId,
                true);

        BuildConfiguration buildConfiguration = repository.queryById(buildConfigurationId);

        BuildConfiguration clonedBuildConfiguration = buildConfiguration.clone();

        clonedBuildConfiguration = repository.save(clonedBuildConfiguration);
        return clonedBuildConfiguration.getId();
    }

    public void addDependency(Integer configId, Integer dependencyId) throws ValidationException {
        BuildConfiguration buildConfig = repository.queryById(configId);
        BuildConfiguration dependency = repository.queryById(dependencyId);

        ValidationBuilder.validateObject(buildConfig, WhenUpdating.class)
                .validateCondition(buildConfig != null, "No build config exists with id: " + configId)
                .validateCondition(dependency != null, "No dependency build config exists with id: " + dependencyId)
                .validateCondition(!configId.equals(dependencyId), "A build configuration cannot depend on itself")
                .validateCondition(!dependency.getAllDependencies().contains(buildConfig), "Cannot add dependency from : "
                        + configId + " to: " + dependencyId + " because it would introduce a cyclic dependency");
        System.out.println("didn't throw any validation errors");
        buildConfig.addDependency(dependency);
        repository.save(buildConfig);
    }

    public void removeDependency(Integer configId, Integer dependencyId) throws ValidationException {
        BuildConfiguration buildConfig = repository.queryById(configId);
        BuildConfiguration dependency = repository.queryById(dependencyId);
        ValidationBuilder.validateObject(buildConfig, WhenUpdating.class)
                .validateCondition(buildConfig != null, "No build config exists with id: " + configId)
                .validateCondition(dependency != null, "No dependency build config exists with id: " + dependencyId);
        buildConfig.removeDependency(dependency);
        repository.save(buildConfig);
    }

    public void setProductVersion(Integer configId, Integer productVersionId) throws ValidationException {
        BuildConfiguration buildConfig = repository.queryById(configId);
        ValidationBuilder.validateObject(buildConfig, WhenUpdating.class)
                .validateCondition(buildConfig!=null, "No build config exists with id: " + configId);
        ProductVersion productVersion = null;
        if (productVersionId != null) {
            productVersion = productVersionRepository.queryById(productVersionId);
        }
        buildConfig.setProductVersion(productVersion);
        repository.save(buildConfig);
    }

    public CollectionInfo<BuildConfigurationRest> getDependencies(int pageIndex, int pageSize, String sortingRsql, String query,
            Integer configId) {
        return queryForCollection(pageIndex, pageSize, sortingRsql, query, withDependantConfiguration(configId));
    }

    public CollectionInfo<BuildConfigurationAuditedRest> getRevisions(int pageIndex, int pageSize, Integer id) {
        List<BuildConfigurationAudited> auditedBuildConfigs = buildConfigurationAuditedRepository.findAllByIdOrderByRevDesc(id);
        return nullableStreamOf(auditedBuildConfigs).map(buildConfigurationAuditedToRestModel()).skip(pageIndex * pageSize)
                .limit(pageSize).collect(new CollectionInfoCollector<>(pageIndex, pageSize, auditedBuildConfigs.size()));
    }

    public BuildConfigurationAuditedRest getRevision(Integer id, Integer rev) {
        IdRev idRev = new IdRev(id, rev);
        BuildConfigurationAudited auditedBuildConfig = buildConfigurationAuditedRepository.queryById(idRev);
        if (auditedBuildConfig == null) {
            return null;
        }
        return new BuildConfigurationAuditedRest(auditedBuildConfig);
    }

    private Function<BuildConfigurationAudited, BuildConfigurationAuditedRest> buildConfigurationAuditedToRestModel() {
        return BuildConfigurationAudited -> new BuildConfigurationAuditedRest(BuildConfigurationAudited);
    }
}
