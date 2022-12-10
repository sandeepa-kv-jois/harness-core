/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterType;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.filters.MatchType;
import io.harness.cdng.environment.filters.TagsFilter;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentInfraFilterHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessTagsFilterYamlForEnvironmentsForMatchAll() {
    Set<Environment> listOfEnvironment = getEnvironmentListForAllTagMatch();

    final FilterYaml filterYaml = getTagFilterYamlMatchTypeAll();
    Set<Environment> filteredEnv =
        getEnvironmentInfraFilterHelper().processTagsFilterYamlForEnvironments(filterYaml, listOfEnvironment);
    assertThat(filteredEnv.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessTagsFilterYamlForEnvironmentsForMatchAny() {
    Set<Environment> listOfEnvironment = getEnvironmentListForAnyTagMatch();
    final FilterYaml filterYaml = getTagFilterYamlMatchTypeAny();

    Set<Environment> filteredEnv =
        getEnvironmentInfraFilterHelper().processTagsFilterYamlForEnvironments(filterYaml, listOfEnvironment);
    assertThat(listOfEnvironment.size()).isEqualTo(filteredEnv.size());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessAllFilterYamlForEnvironments() {
    Set<Environment> listOfEnvironment = getEnvironmentListForAnyTagMatch();
    final FilterYaml filterYaml = getAllFilterYaml();

    Set<Environment> filteredEnv =
        getEnvironmentInfraFilterHelper().processTagsFilterYamlForEnvironments(filterYaml, listOfEnvironment);
    assertThat(listOfEnvironment.size()).isEqualTo(filteredEnv.size());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessAllFilterYamlForClusters() {
    Map<String, io.harness.cdng.gitops.entity.Cluster> listOfClusters =
        Map.of("cl1", Cluster.builder().clusterRef("cl1").build(), "cl2", Cluster.builder().clusterRef("cl2").build());
    final FilterYaml filterYaml = getAllFilterYaml();

    List<Cluster> filteredCls = getEnvironmentInfraFilterHelper().processTagsFilterYamlForGitOpsClusters(
        filterYaml, Collections.emptySet(), listOfClusters);
    assertThat(listOfClusters.size()).isEqualTo(filteredCls.size());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessTagsFilterYamlForClustersForMatchAll() {
    Map<String, io.harness.cdng.gitops.entity.Cluster> listOfClusters =
        Map.of("cl1", Cluster.builder().clusterRef("cl1").build(), "cl2", Cluster.builder().clusterRef("cl2").build());
    final FilterYaml filterYaml = getTagFilterYamlMatchTypeAll();

    List<Cluster> filteredCls = getEnvironmentInfraFilterHelper().processTagsFilterYamlForGitOpsClusters(
        filterYaml, getClusterListForAllTagMatch(), listOfClusters);
    assertThat(filteredCls.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessTagsFilterYamlForClustersForMatchAny() {
    Map<String, io.harness.cdng.gitops.entity.Cluster> listOfClusters =
        Map.of("cl1", Cluster.builder().clusterRef("cl1").build(), "cl2", Cluster.builder().clusterRef("cl2").build());
    final FilterYaml filterYaml = getTagFilterYamlMatchTypeAny();

    List<Cluster> filteredCls = getEnvironmentInfraFilterHelper().processTagsFilterYamlForGitOpsClusters(
        filterYaml, getClusterListForAnyTagMatch(), listOfClusters);
    assertThat(listOfClusters.size()).isEqualTo(filteredCls.size());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testApplyFiltersOnEnvsMatchAnyTagFilter() {
    Set<Environment> listOfEnvironment = getEnvironmentListForAnyTagMatch();
    final FilterYaml filterYaml = getTagFilterYamlMatchTypeAny();

    Set<Environment> environments =
        getEnvironmentInfraFilterHelper().applyFiltersOnEnvs(listOfEnvironment, Arrays.asList(filterYaml));
    assertThat(environments.size()).isEqualTo(listOfEnvironment.size());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testApplyFiltersOnEnvsMatchAllTagFilter() {
    Set<Environment> listOfEnvironment = getEnvironmentListForAnyTagMatch();
    final FilterYaml filterYaml = getTagFilterYamlMatchTypeAll();

    Set<Environment> environments =
        getEnvironmentInfraFilterHelper().applyFiltersOnEnvs(listOfEnvironment, Arrays.asList(filterYaml));
    assertThat(environments.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testApplyFiltersOnClustersMatchAnyTagFilter() {
    Set<io.harness.gitops.models.Cluster> listOfClusters = getClusterListForAnyTagMatch();
    final FilterYaml filterYaml = getTagFilterYamlMatchTypeAny();

    Map<String, io.harness.cdng.gitops.entity.Cluster> clsToCluster =
        Map.of("cl1", Cluster.builder().clusterRef("cl1").build(), "cl2", Cluster.builder().clusterRef("cl2").build());

    Set<Cluster> filteredClusters = getEnvironmentInfraFilterHelper().applyFilteringOnClusters(
        Arrays.asList(filterYaml), clsToCluster, listOfClusters);
    assertThat(filteredClusters.size()).isEqualTo(listOfClusters.size());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testApplyFiltersOnClustersMatchAllTagFilter() {
    Set<io.harness.gitops.models.Cluster> listOfClusters = getClusterListForAllTagMatch();
    final FilterYaml filterYaml = getTagFilterYamlMatchTypeAll();

    Map<String, io.harness.cdng.gitops.entity.Cluster> clsToCluster =
        Map.of("cl1", Cluster.builder().clusterRef("cl1").build(), "cl2", Cluster.builder().clusterRef("cl2").build());

    Set<Cluster> filteredClusters = getEnvironmentInfraFilterHelper().applyFilteringOnClusters(
        Arrays.asList(filterYaml), clsToCluster, listOfClusters);
    assertThat(filteredClusters.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testApplyFiltersOnClustersNoFilterExists() {
    Set<io.harness.gitops.models.Cluster> listOfClusters = getClusterListForAllTagMatch();

    Map<String, io.harness.cdng.gitops.entity.Cluster> clsToCluster =
        Map.of("cl1", Cluster.builder().clusterRef("cl1").build(), "cl2", Cluster.builder().clusterRef("cl2").build());

    Set<Cluster> filteredClusters =
        getEnvironmentInfraFilterHelper().applyFilteringOnClusters(emptyList(), clsToCluster, listOfClusters);
    assertThat(filteredClusters.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessTagsFilterYamlForInfraForMatchAny() {
    Set<InfrastructureEntity> listOfInfra = getInfrastructureListForAnyTagMatch();
    final FilterYaml filterYaml = getTagFilterYamlMatchTypeAny();

    Set<InfrastructureEntity> filteredEnv =
        getEnvironmentInfraFilterHelper().processTagsFilterYamlForInfraStructures(filterYaml, listOfInfra);
    assertThat(listOfInfra.size()).isEqualTo(filteredEnv.size());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessTagsFilterYamlForInfraForMatchAll() {
    Set<InfrastructureEntity> listOfInfra = getInfrastructureListForAllTagMatch();

    final FilterYaml filterYaml = getTagFilterYamlMatchTypeAll();
    Set<InfrastructureEntity> filteredEnv =
        getEnvironmentInfraFilterHelper().processTagsFilterYamlForInfraStructures(filterYaml, listOfInfra);
    assertThat(filteredEnv.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testAreFiltersPresent() {
    assertThat(getEnvironmentInfraFilterHelper().areFiltersPresent(
                   EnvironmentsYaml.builder()
                       .uuid("envId")
                       .values(ParameterField.createValueField(anyList()))
                       .filters(ParameterField.createValueField(Arrays.asList(FilterYaml.builder().build())))
                       .build()))
        .isTrue();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testAreFiltersPresentReturnsFalseIfNotEnvironmentsExists() {
    assertThat(getEnvironmentInfraFilterHelper().areFiltersPresent(EnvironmentsYaml.builder().uuid("envId").build()))
        .isFalse();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testAreFiltersSetOnIndividualEnvironments() {
    assertThat(getEnvironmentInfraFilterHelper().areFiltersSetOnIndividualEnvironments(
                   EnvironmentsYaml.builder()
                       .uuid("envId")
                       .values(ParameterField.createValueField(Arrays.asList(
                           EnvironmentYamlV2.builder().filters(ParameterField.createValueField(anyList())).build())))
                       .filters(ParameterField.createValueField(anyList()))
                       .build()))
        .isTrue();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testgetEnvV2YamlsWithFilters() {
    EnvironmentsYaml envId =
        EnvironmentsYaml.builder()
            .uuid("envId")
            .values(ParameterField.createValueField(
                Arrays.asList(EnvironmentYamlV2.builder().filters(ParameterField.createValueField(anyList())).build())))
            .filters(ParameterField.createValueField(anyList()))
            .build();
    assertThat(envId).isNotNull();
  }

  @NotNull
  private static EnvironmentInfraFilterHelper getEnvironmentInfraFilterHelper() {
    return new EnvironmentInfraFilterHelper();
  }

  private static FilterYaml getTagFilterYamlMatchTypeAll() {
    Map<String, String> tagMap = new HashMap<>();
    tagMap.put("env", "dev");
    return FilterYaml.builder()
        .entities(Set.of(Entity.environments, Entity.gitOpsClusters, Entity.infrastructures))
        .type(FilterType.tags)
        .spec(TagsFilter.builder()
                  .matchType(ParameterField.createValueField(MatchType.all))
                  .tags(ParameterField.createValueField(tagMap))
                  .build())
        .build();
  }

  private static FilterYaml getTagFilterYamlMatchTypeAny() {
    Map<String, String> tagMap = new HashMap<>();
    tagMap.put("env", "dev");
    tagMap.put("env1", "dev1");
    tagMap.put("infra", "dev");
    tagMap.put("infra1", "dev1");

    return FilterYaml.builder()
        .entities(Set.of(Entity.environments, Entity.gitOpsClusters, Entity.infrastructures))
        .type(FilterType.tags)
        .spec(TagsFilter.builder()
                  .matchType(ParameterField.createValueField(MatchType.any))
                  .tags(ParameterField.createValueField(tagMap))
                  .build())
        .build();
  }

  private static FilterYaml getAllFilterYaml() {
    return FilterYaml.builder()
        .entities(Set.of(Entity.environments, Entity.gitOpsClusters))
        .type(FilterType.all)
        .build();
  }

  @NotNull
  private static Set<Environment> getEnvironmentListForAnyTagMatch() {
    List<NGTag> env1Tags = Arrays.asList(NGTag.builder().key("env").value("dev").build());
    List<NGTag> env2Tags = Arrays.asList(
        NGTag.builder().key("env").value("dev").build(), NGTag.builder().key("env1").value("dev2").build());
    final Set<Environment> listOfEnvironment = new HashSet<>(
        Arrays.asList(Environment.builder().tags(env1Tags).build(), Environment.builder().tags(env2Tags).build()));
    return listOfEnvironment;
  }

  @NotNull
  private static Set<Environment> getEnvironmentListForAllTagMatch() {
    List<NGTag> env1Tags = Arrays.asList(NGTag.builder().key("env").value("dev").build());
    List<NGTag> env2Tags = Arrays.asList(NGTag.builder().key("env1").value("dev2").build());
    final Set<Environment> listOfEnvironment = new HashSet<>(
        Arrays.asList(Environment.builder().tags(env1Tags).build(), Environment.builder().tags(env2Tags).build()));
    return listOfEnvironment;
  }

  @NotNull
  private static Set<io.harness.gitops.models.Cluster> getClusterListForAnyTagMatch() {
    io.harness.gitops.models.Cluster cl1 = new io.harness.gitops.models.Cluster();
    cl1.setTags(Map.of("env", "dev"));
    cl1.setIdentifier("cl1");

    io.harness.gitops.models.Cluster cl2 = new io.harness.gitops.models.Cluster();
    cl2.setTags(Map.of("env", "dev", "env1", "dev1"));
    cl2.setIdentifier("cl2");

    final Set<io.harness.gitops.models.Cluster> listOfClusters = new HashSet<>(Arrays.asList(cl1, cl2));
    return listOfClusters;
  }

  @NotNull
  private static Set<io.harness.gitops.models.Cluster> getClusterListForAllTagMatch() {
    io.harness.gitops.models.Cluster cl1 = new io.harness.gitops.models.Cluster();
    cl1.setTags(Map.of("env", "dev"));
    cl1.setIdentifier("cl1");

    io.harness.gitops.models.Cluster cl2 = new io.harness.gitops.models.Cluster();
    cl2.setTags(Map.of("env1", "dev1"));
    cl2.setIdentifier("cl2");

    final Set<io.harness.gitops.models.Cluster> listOfClusters = new HashSet<>(Arrays.asList(cl1, cl2));
    return listOfClusters;
  }

  @NotNull
  private static Set<InfrastructureEntity> getInfrastructureListForAnyTagMatch() {
    List<NGTag> infra1Tags = Arrays.asList(NGTag.builder().key("infra").value("dev").build());
    List<NGTag> infra2Tags = Arrays.asList(
        NGTag.builder().key("infra").value("dev").build(), NGTag.builder().key("infra1").value("dev2").build());
    final Set<InfrastructureEntity> listOfInfra =
        new HashSet<>(Arrays.asList(InfrastructureEntity.builder().tags(infra1Tags).build(),
            InfrastructureEntity.builder().tags(infra2Tags).build()));
    return listOfInfra;
  }

  @NotNull
  private static Set<InfrastructureEntity> getInfrastructureListForAllTagMatch() {
    List<NGTag> infra1Tags = Arrays.asList(NGTag.builder().key("env").value("dev").build());
    List<NGTag> infra2Tags = Arrays.asList(
        NGTag.builder().key("env").value("dev").build(), NGTag.builder().key("env1").value("dev2").build());
    final Set<InfrastructureEntity> listOfInfra =
        new HashSet<>(Arrays.asList(InfrastructureEntity.builder().tags(infra1Tags).build(),
            InfrastructureEntity.builder().tags(infra2Tags).build()));
    return listOfInfra;
  }
}