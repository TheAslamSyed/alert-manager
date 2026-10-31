package com.democorp.warroom.common.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DemoCorp's service catalog: who owns each service, how critical it is and what it depends on.
 * Loaded from {@code warroom.catalog.services} in warroom-common.yml.
 */
@ConfigurationProperties(prefix = "warroom.catalog")
public record ServiceCatalog(List<ServiceInfo> services) {

    public ServiceCatalog {
        services = services == null ? List.of() : List.copyOf(services);
    }

    public Optional<ServiceInfo> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return services.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    /** True if one service calls the other, in either direction. */
    public boolean related(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return dependenciesOf(a).contains(b) || dependenciesOf(b).contains(a);
    }

    public Set<String> dependenciesOf(String name) {
        return find(name).map(s -> Set.copyOf(s.dependsOn())).orElse(Set.of());
    }

    public Set<String> names() {
        return services.stream().map(ServiceInfo::name).collect(Collectors.toUnmodifiableSet());
    }

    public record ServiceInfo(String name, String team, String owner, int tier, List<String> dependsOn) {
        public ServiceInfo {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
    }
}
